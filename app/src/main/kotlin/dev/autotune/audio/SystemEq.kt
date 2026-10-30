package dev.autotune.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.audiofx.AudioEffect
import android.media.audiofx.DynamicsProcessing
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import dev.autotune.dsp.EqChain
import dev.autotune.dsp.Rta

/**
 * AutoTune as an equaliser for this phone's own audio (which also feeds a car or home theatre over Bluetooth / USB).
 *
 * Uses Android's DynamicsProcessing audio effect, attached to the global output mix and to the sessions of players
 * that announce themselves. Whether the global mix honours it depends on the phone and Android version. It cannot
 * control Samsung's Adapt Sound / Dolby / SoundAlive: those are separate and should be off.
 */
object SystemEq {
    /** The EQ currently applied (preamp included), or null when off. */
    @Volatile
    var chain: EqChain? = null
        private set

    /**
     * What the 31 fixed bands really do with [chain] (narrow cuts lose depth between band centres, preamp excluded).
     * A run measured with the system EQ on records this as its applied EQ, so the next fit sees what the mic heard.
     */
    @Volatile
    var applied: List<dev.autotune.dsp.EqFilter> = emptyList()
        private set

    val supported: Boolean get() = Build.VERSION.SDK_INT >= 28

    private var global: DynamicsProcessing? = null
    private val sessions = HashMap<Int, DynamicsProcessing>()
    // 31 bands at 1/3 octave, or 61 at 1/6 octave (finer cuts, more work for the phone)
    private val coarse = Rta.CENTERS
    private val fineCentres = DoubleArray(61) { 20.0 * Math.pow(2.0, it / 6.0) }.filter { it <= 20000.0 }.toDoubleArray()
    private val centers: DoubleArray get() = if (dev.autotune.SystemEqConfig.fine) fineCentres else coarse
    private val edgeFactor: Double get() = if (dev.autotune.SystemEqConfig.fine) Math.pow(2.0, 1.0 / 12.0) else Rta.EDGE
    /** Q of one band, for predicting what the bands realise (1/3 or 1/6 octave wide). */
    private val bandQ: Double get() = if (dev.autotune.SystemEqConfig.fine) 8.65 else 4.32

    /** Switches between 31 and 61 bands; the EQ that is on is rebuilt at once. */
    @Synchronized
    fun setFine(on: Boolean) {
        if (dev.autotune.SystemEqConfig.fine == on) return
        val c = chain
        off()
        dev.autotune.SystemEqConfig.fine = on
        c?.let { apply(it) }
    }

    private fun fill(dp: DynamicsProcessing, c: EqChain) {
        val centers = centers
        for (i in centers.indices) {
            val edge = minOf(centers[i] * edgeFactor, 21000.0)
            val gain = c.responseDb(centers[i]).coerceIn(-30.0, 15.0)
            dp.setPreEqBandAllChannelsTo(i, DynamicsProcessing.EqBand(true, edge.toFloat(), gain.toFloat()))
        }
        // a safety limiter so boosts can never clip hard
        dp.setLimiterAllChannelsTo(DynamicsProcessing.Limiter(true, true, 0, 1f, 60f, 10f, -2f, 0f))
    }

    private fun create(session: Int, c: EqChain): DynamicsProcessing {
        val cfg = DynamicsProcessing.Config.Builder(
            DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION, 2, true, centers.size, false, 0, false, 0, true,
        ).build()
        val dp = DynamicsProcessing(0, session, cfg)
        fill(dp, c)
        dp.enabled = true
        return dp
    }

    /** Applies [c] to the global mix and to every attached player session. Returns an error message, or null on success. */
    @Synchronized
    fun apply(c: EqChain): String? {
        if (!supported) return "The system equaliser needs Android 9 or newer."
        return try {
            val g = global
            if (g == null) global = create(0, c) else fill(g, c)
            sessions.values.forEach { fill(it, c) }
            chain = c
            applied = c.sampledTo(centers.toList(), bandQ)
            null
        } catch (e: Exception) {
            off()
            "This phone does not allow an app to equalise all audio (${e.message ?: e.javaClass.simpleName})."
        }
    }

    @Synchronized
    fun attachSession(id: Int) {
        val c = chain ?: return
        if (id <= 0 || sessions.containsKey(id)) return
        runCatching { sessions[id] = create(id, c) }
    }

    @Synchronized
    fun detachSession(id: Int) {
        sessions.remove(id)?.let { runCatching { it.release() } }
    }

    @Synchronized
    fun off() {
        runCatching { global?.release() }
        global = null
        sessions.values.forEach { runCatching { it.release() } }
        sessions.clear()
        chain = null
        applied = emptyList()
    }
}

/** Keeps the equaliser alive while AutoTune is in the background, and attaches it to players that announce an audio session. */
class SystemEqService : Service() {
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val id = intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, -1)
            when (intent.action) {
                AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION -> SystemEq.attachSession(id)
                AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION -> SystemEq.detachSession(id)
            }
        }
    }
    private var registered = false
    private var watcher: BluetoothWatcher? = null

    private fun automationOn() = ProfileController.prefs(this).getBoolean("automationOn", false)

    private fun notify(text: String, title: String) {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel(CHANNEL, "AutoTune EQ", NotificationManager.IMPORTANCE_LOW))
        val b = (if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL) else Notification.Builder(this))
            .setContentTitle(title).setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_headset).setOngoing(true)
            .setContentIntent(openApp())
        // switch things off right from the notification, without opening the app
        val prefs = ProfileController.prefs(this)
        if (SystemEq.chain != null || prefs.getBoolean("eqWanted", false)) b.addAction(action(android.R.drawable.ic_lock_power_off, "Switch EQ off", ProfileController.ACTION_OFF, 201))
        if (automationOn()) b.addAction(action(android.R.drawable.ic_menu_close_clear_cancel, "Stop auto-detect", ProfileController.ACTION_AUTO_OFF, 202))
        val n = b.build()
        ServiceCompat.startForeground(
            this, 42, n,
            if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0,
        )
    }

    private fun action(icon: Int, title: String, act: String, code: Int): Notification.Action =
        Notification.Action.Builder(android.graphics.drawable.Icon.createWithResource(this, icon), title, EqActionReceiver.pending(this, act, code, toast = false)).build()

    private fun openApp(): android.app.PendingIntent = android.app.PendingIntent.getActivity(
        this, 200, Intent(this, dev.autotune.MainActivity::class.java).putExtra("open", "eq").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun startWatching() {
        if (watcher != null) return
        val w = BluetoothWatcher(this) { refreshNotification() }
        ContextCompat.registerReceiver(
            this, w,
            IntentFilter().apply {
                addAction(android.bluetooth.BluetoothDevice.ACTION_ACL_CONNECTED)
                addAction(android.bluetooth.BluetoothDevice.ACTION_ACL_DISCONNECTED)
            },
            ContextCompat.RECEIVER_EXPORTED,
        )
        watcher = w
    }

    private fun refreshNotification() {
        val on = SystemEq.chain != null
        // the user wants the EQ on but it could not start (yet): stay up and say so, as the widget does
        val wanted = ProfileController.prefs(this).getBoolean("eqWanted", false)
        when {
            on -> notify(
                (if (SystemEq.chain?.filters.isNullOrEmpty()) "Flat for now: pick an EQ in AutoTune" else "Equalising this phone's audio") +
                    if (automationOn()) " · watching for your Bluetooth devices" else "",
                "AutoTune EQ is on",
            )
            wanted -> notify("The EQ could not be switched on. Open AutoTune to try again.", "AutoTune EQ is waiting")
            automationOn() -> notify("Waiting for a Bluetooth device to connect", "AutoTune is watching")
            else -> stopSelf()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_REFRESH) {
            if (!automationOn()) { watcher?.let { runCatching { unregisterReceiver(it) } }; watcher = null }
            refreshNotification()
            return START_STICKY
        }
        if (intent?.action == ACTION_STOP) {
            SystemEq.off()
            if (automationOn()) { refreshNotification(); return START_STICKY }
            stopSelf()
            return START_NOT_STICKY
        }
        // restarted by the system after being killed (intent == null), or started after a reboot or an update while
        // the EQ was wanted on: bring it back (restore does nothing when the user switched it off)
        if (SystemEq.chain == null) ProfileController.restore(this)
        // the foreground notice must go up first, whatever happens next
        notify(if (SystemEq.chain != null) "Equalising this phone's audio" else "Waiting for a Bluetooth device to connect",
            if (SystemEq.chain != null) "AutoTune EQ is on" else "AutoTune is watching")
        if (!registered) {
            ContextCompat.registerReceiver(
                this, receiver,
                IntentFilter().apply {
                    addAction(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION)
                    addAction(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION)
                },
                ContextCompat.RECEIVER_EXPORTED,
            )
            registered = true
        }
        if (automationOn()) startWatching()
        refreshNotification()
        return START_STICKY
    }

    override fun onDestroy() {
        if (registered) runCatching { unregisterReceiver(receiver) }
        watcher?.let { runCatching { unregisterReceiver(it) } }
        SystemEq.off()
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "dev.autotune.STOP_EQ"
        const val ACTION_WATCH = "dev.autotune.WATCH"
        const val ACTION_REFRESH = "dev.autotune.REFRESH"
        private const val CHANNEL = "autotune_eq"
    }
}
