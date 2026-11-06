package dev.autotune.audio

import android.app.UiModeManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.database.ContentObserver
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.core.content.ContextCompat
import dev.autotune.LoudMode
import dev.autotune.dsp.EqFilter
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * The loud EQ: a set of cuts that comes in at high volume, so the speakers stop distorting (see `loudDelta` in
 * TuningLogic). It follows the phone's media volume when the car's volume knob reaches the phone (checked once per
 * output with the volume check), and can always be switched by hand: notification, quick tile, volume keys
 * (down, up, down, up) or an intent from Routines. It only plays: measurements never use it.
 */
object LoudEq {
    /** What the loud EQ is doing now, for the screen. */
    data class Live(
        val amount: Double = 0.0, val manual: Boolean = false, val volumePct: Int? = null,
        val route: String = "", val synced: Boolean? = null, val carMode: Boolean = false,
    )

    val live = MutableStateFlow(Live())

    /** The amount the playing chain was built with, so a volume step only re-applies when it changes. */
    @Volatile
    var lastAmount = 0.0
        private set

    /** True while the volume check runs: the car knob turning up and down must not count as the volume-key switch. */
    @Volatile
    var checking = false

    private fun prefs(ctx: Context) = ProfileController.prefs(ctx)

    fun mode(ctx: Context): LoudMode = LoudMode.of(prefs(ctx).getString("loudMode", null))
    fun manual(ctx: Context): Boolean = prefs(ctx).getBoolean("loudOn", false)
    fun startFrac(ctx: Context): Double = prefs(ctx).getInt("loudStart", 75) / 100.0
    fun keysOn(ctx: Context): Boolean = prefs(ctx).getBoolean("loudKeys", true)

    /** The cuts from a loud reading, or null for the safe default. */
    fun cuts(ctx: Context): List<EqFilter>? = dev.autotune.filtersFromText(prefs(ctx).getString("loudCuts", null))

    /** The phone's media volume, 0..1. */
    fun volume(ctx: Context): Double? = runCatching {
        val am = ctx.getSystemService(AudioManager::class.java)
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        if (max <= 0) null else am.getStreamVolume(AudioManager.STREAM_MUSIC).toDouble() / max
    }.getOrNull()

    fun level(ctx: Context): Int = runCatching { ctx.getSystemService(AudioManager::class.java).getStreamVolume(AudioManager.STREAM_MUSIC) }.getOrDefault(-1)

    fun carMode(ctx: Context): Boolean = runCatching {
        ctx.getSystemService(UiModeManager::class.java)?.currentModeType == Configuration.UI_MODE_TYPE_CAR
    }.getOrDefault(false)

    private val ORDER = listOf(
        AudioDeviceInfo.TYPE_BUS, AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET,
        AudioDeviceInfo.TYPE_USB_ACCESSORY, AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET,
        AudioDeviceInfo.TYPE_LINE_ANALOG, AudioDeviceInfo.TYPE_HDMI,
    )

    /** Where media plays now (the output the volume check is remembered for). Android Auto is named when it is on. */
    fun route(ctx: Context): String {
        val outs = runCatching { ctx.getSystemService(AudioManager::class.java).getDevices(AudioManager.GET_DEVICES_OUTPUTS).toList() }.getOrDefault(emptyList())
        // Bluetooth LE ranks with classic Bluetooth
        val order = ORDER.toMutableList().apply { if (Build.VERSION.SDK_INT >= 31) addAll(2, listOf(AudioDeviceInfo.TYPE_BLE_SPEAKER, AudioDeviceInfo.TYPE_BLE_HEADSET)) }
        val d = outs.filter { it.type in order }.minByOrNull { order.indexOf(it.type) }
        val name = d?.let { Outputs.nameOf(it) } ?: "Phone speaker"
        return if (carMode(ctx) && "Android Auto" !in name) "Android Auto · $name" else name
    }

    private fun key(route: String) = "volSync_" + route.lowercase().replace(Regex("[^a-z0-9]+"), "_")

    /** Whether the volume check saw this output's volume move with the car's knob; null when never checked. */
    fun synced(ctx: Context, route: String = route(ctx)): Boolean? =
        if (prefs(ctx).contains(key(route))) prefs(ctx).getBoolean(key(route), false) else null

    fun setSynced(ctx: Context, route: String, ok: Boolean) = prefs(ctx).edit().putBoolean(key(route), ok).apply()

    /**
     * How much of the loud EQ plays now, in steps of 10%: all of it when switched on by hand; otherwise, in "Follow the
     * volume", by the media volume, but only on an output whose volume the check saw move with the car's knob
     * (elsewhere the phone's volume says nothing about how loud the car plays).
     */
    fun amount(ctx: Context): Double {
        val mode = mode(ctx)
        if (mode == LoudMode.OFF) return 0.0
        if (manual(ctx)) return 1.0
        if (mode != LoudMode.VOLUME || synced(ctx) != true) return 0.0
        val v = volume(ctx) ?: return 0.0
        return Math.round(dev.autotune.loudAmount(v, startFrac(ctx)) * 10.0) / 10.0
    }

    /** Called by [ProfileController.shaped] with the amount it built the chain with. */
    fun built(ctx: Context, amount: Double) {
        lastAmount = amount
        publish(ctx, amount)
    }

    fun publish(ctx: Context, amount: Double = lastAmount) {
        val r = route(ctx)
        live.value = Live(amount, manual(ctx) && mode(ctx) != LoudMode.OFF, volume(ctx)?.let { Math.round(it * 100).toInt() }, r, synced(ctx, r), carMode(ctx))
    }

    /** The volume moved: re-apply the EQ when the loud amount changed. */
    fun onVolume(ctx: Context) {
        val a = amount(ctx)
        if (a != lastAmount) ProfileController.reshape(ctx) else publish(ctx)
    }

    /** Switches the loud EQ on or off by hand. Switching on while it is Off sets "Button only". Returns the message. */
    fun toggleManual(ctx: Context): String = setManual(ctx, !(manual(ctx) && mode(ctx) != LoudMode.OFF))

    fun setManual(ctx: Context, on: Boolean): String {
        val e = prefs(ctx).edit().putBoolean("loudOn", on)
        if (on && mode(ctx) == LoudMode.OFF) e.putString("loudMode", LoudMode.BUTTON.name)
        e.apply()
        dev.autotune.Diag.log(ctx, if (on) "loud_on" else "loud_off", mapOf("route" to route(ctx)))
        changed(ctx)
        return when {
            on -> if (SystemEq.chain == null) "Loud EQ on. It plays once AutoTune EQ is on." else "Loud EQ on"
            else -> "Loud EQ off"
        }
    }

    /** After any loud setting changed: rebuild what plays, the notification and the tile. */
    fun changed(ctx: Context) {
        ProfileController.reshape(ctx)
        // with the EQ off nothing plays; with it on, reshape has published already
        if (SystemEq.chain == null) publish(ctx, 0.0)
        runCatching { ctx.startService(Intent(ctx, SystemEqService::class.java).setAction(SystemEqService.ACTION_REFRESH)) }
        runCatching { TileService.requestListeningState(ctx, ComponentName(ctx, LoudTile::class.java)) }
        EqBus.bump()
    }
}

/**
 * Sees every change of the media volume: the system's volume broadcast (at once) and the volume settings (a backup,
 * written about half a second later). Only real changes reach [onStep], with the direction (+1 up, -1 down).
 */
class VolumeWatcher(private val ctx: Context, private val onStep: (level: Int, dir: Int) -> Unit) {
    private val main = Handler(Looper.getMainLooper())
    private var last = -1
    private val observer = object : ContentObserver(main) {
        override fun onChange(selfChange: Boolean) = check()
    }
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val type = intent.getIntExtra("android.media.EXTRA_VOLUME_STREAM_TYPE", AudioManager.STREAM_MUSIC)
            if (type == AudioManager.STREAM_MUSIC) check()
        }
    }
    private var on = false

    private fun check() {
        val v = LoudEq.level(ctx)
        if (v < 0 || v == last) return
        val dir = if (last < 0) 0 else if (v > last) 1 else -1
        last = v
        if (dir != 0) onStep(v, dir)
    }

    fun start() {
        if (on) return
        last = LoudEq.level(ctx)
        runCatching { ctx.contentResolver.registerContentObserver(Settings.System.CONTENT_URI, true, observer) }
        runCatching { ContextCompat.registerReceiver(ctx, receiver, IntentFilter("android.media.VOLUME_CHANGED_ACTION"), ContextCompat.RECEIVER_NOT_EXPORTED) }
        on = true
    }

    fun stop() {
        if (!on) return
        runCatching { ctx.contentResolver.unregisterContentObserver(observer) }
        runCatching { ctx.unregisterReceiver(receiver) }
        on = false
    }
}

/** Quick Settings tile: the loud EQ on or off with one tap, from anywhere. */
class LoudTile : TileService() {
    override fun onStartListening() = paint()

    override fun onClick() {
        LoudEq.toggleManual(this)
        paint()
    }

    private fun paint() {
        val t = qsTile ?: return
        val on = LoudEq.manual(this) && LoudEq.mode(this) != LoudMode.OFF
        t.state = if (on) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        t.label = "Loud EQ"
        if (Build.VERSION.SDK_INT >= 29) t.subtitle = when {
            SystemEq.chain == null -> if (on) "Waits for the EQ" else "EQ is off"
            on -> "On"
            else -> "Off"
        }
        t.updateTile()
    }
}
