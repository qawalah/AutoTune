package dev.autotune.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.audiofx.DynamicsProcessing
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import dev.autotune.dsp.DpBins
import dev.autotune.dsp.EqChain
import dev.autotune.dsp.EqRamp
import dev.autotune.dsp.Rta

/**
 * AutoTune as an equaliser for this phone's own audio (which also feeds a car or home theatre over Bluetooth / USB).
 *
 * Uses Android's DynamicsProcessing audio effect on the global output mix. Whether the mix honours it depends on the
 * phone and Android version. It cannot control Samsung's Adapt Sound / Dolby / SoundAlive: those are separate and should
 * be off. It is NOT also put on the sessions players announce: on a phone whose output mix honours it (the only kind the
 * measurements work on) music from those players got the EQ twice, while the sweep got it once.
 *
 * Every change is faded in over about 150 ms ([EqRamp]): switching on, off, to another EQ, or rebuilding. A jump of a
 * few dB on playing music is heard as a click.
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

    /** True while a measurement runs: nothing from outside (the loud EQ following the volume) may change the EQ then. */
    @Volatile
    var measuring = false

    /** Set when the output changed; the first player starting after that rebuilds the effect (SystemEqService). */
    @Volatile
    var routeChanged = false

    /** One effect and the band gains (dB) it holds right now; a fade moves [gains] to [goal]. */
    private class Fx(val dp: DynamicsProcessing, val edges: FloatArray, val gains: FloatArray, var goal: FloatArray)

    private var global: Fx? = null
    /** Effects fading to flat before they are released (the EQ switched off, or replaced by a rebuild). */
    private val leaving = ArrayList<Fx>()
    private val fader = java.util.concurrent.Executors.newSingleThreadExecutor { r -> Thread(r, "autotune-eq-fade").apply { isDaemon = true } }
    private var fadeId = 0

    // 31 bands at 1/3 octave, or 61 at 1/6 octave (finer cuts, more work for the phone)
    private val coarse = Rta.CENTERS
    private val fineCentres = DoubleArray(61) { 20.0 * Math.pow(2.0, it / 6.0) }.filter { it <= 20000.0 }.toDoubleArray()
    private val centers: DoubleArray get() = if (dev.autotune.SystemEqConfig.fine) fineCentres else coarse
    private val edgeFactor: Double get() = if (dev.autotune.SystemEqConfig.fine) Math.pow(2.0, 1.0 / 12.0) else Rta.EDGE
    /** Q of one band, for predicting what the bands realise (1/3 or 1/6 octave wide). */
    private val bandQ: Double get() = if (dev.autotune.SystemEqConfig.fine) 8.65 else 4.32

    /** Switches between 31 and 61 bands; the EQ that is on is rebuilt at once (the old bands fade out as the new fade in). */
    @Synchronized
    fun setFine(on: Boolean) {
        if (dev.autotune.SystemEqConfig.fine == on) return
        val c = chain
        off()
        dev.autotune.SystemEqConfig.fine = on
        c?.let { apply(it) }
    }

    /** The output's sample rate, which sets where the effect's bins fall (SystemEqService and the app read it). */
    @Volatile
    var sampleRate = 48000

    /** Each band's cutoff: the effect gives a band everything from the cutoff before it up to its own. */
    private fun edges(): DoubleArray = DoubleArray(centers.size) { minOf(centers[it] * edgeFactor, 21000.0) }
    private val block: Int get() = DpBins.block(sampleRate)

    /** The block size the effect is asked for at [sampleRate] (for the diagnostics). */
    val blockSize: Int get() = block

    /** The frame duration (ms) the running effect reports, or null when off: shows whether the phone took [DpBins.FRAME_MS]. */
    @Synchronized
    fun frameMsInEffect(): Float? = global?.let { runCatching { it.dp.config.preferredFrameDuration }.getOrNull() }

    /** Band gains that put [responseDb] on the effect's bins (with long blocks, about 12 Hz apart). */
    private fun bandGains(responseDb: (Double) -> Double): DoubleArray =
        DpBins.bandGains(edges(), centers, block, sampleRate, responseDb).map { it.coerceIn(-30.0, 15.0) }.toDoubleArray()

    private fun goals(c: EqChain): FloatArray = bandGains { c.responseDb(it) }.map { it.toFloat() }.toFloatArray()

    /** What the bands really do with [c] (preamp left out), as peaking filters at the band centres. */
    private fun realised(c: EqChain): List<dev.autotune.dsp.EqFilter> {
        val e = edges(); val b = block
        val bins = DpBins.binGains(e, bandGains { f -> c.filters.sumOf { it.magnitudeDb(f) } }, b, sampleRate)
        return EqChain.peakingAt(centers.toList(), DoubleArray(centers.size) { DpBins.realisedDb(bins, b, sampleRate, centers[it]) }, bandQ)
    }

    /** A new effect, flat: switching it on changes nothing, the fade then brings the EQ in. */
    private fun create(session: Int): Fx {
        val centers = centers
        val cfg = DynamicsProcessing.Config.Builder(
            DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION, 2, true, centers.size, false, 0, false, 0, true,
        )
            // long blocks: Android's default 10 ms gave bins 94 Hz apart, so the whole bass was two bins (smeared, and
            // the 45 Hz band's gain ran to 140 Hz). 80 ms gives 4096 samples, bins about 12 Hz apart, about 85 ms later.
            .setPreferredFrameDuration(DpBins.FRAME_MS)
            .build()
        val dp = DynamicsProcessing(0, session, cfg)
        val edges = edges().map { it.toFloat() }.toFloatArray()
        try {
            for (i in edges.indices) dp.setPreEqBandAllChannelsTo(i, DynamicsProcessing.EqBand(true, edges[i], 0f))
            // a safety limiter so boosts can never clip hard
            dp.setLimiterAllChannelsTo(DynamicsProcessing.Limiter(true, true, 0, 1f, 60f, 10f, -2f, 0f))
            dp.enabled = true
        } catch (e: Exception) {
            runCatching { dp.release() }
            throw e
        }
        return Fx(dp, edges, FloatArray(edges.size), FloatArray(edges.size))
    }

    /** Puts the effect out of use: it fades to flat, then it is released. */
    private fun retire(fx: Fx) {
        fx.goal = FloatArray(fx.edges.size)
        leaving += fx
    }

    /** Applies [c] to the global mix. Returns an error message, or null on success. */
    @Synchronized
    fun apply(c: EqChain): String? {
        if (!supported) return "The system equaliser needs Android 9 or newer."
        return try {
            global?.takeIf { it.edges.size != centers.size }?.let { retire(it); global = null }
            val g = global ?: create(0).also { global = it }
            g.goal = goals(c)
            chain = c
            applied = realised(c)
            fade()
            null
        } catch (e: Exception) {
            off()
            "This phone does not allow an app to equalise all audio (${e.message ?: e.javaClass.simpleName})."
        }
    }

    /**
     * Opens the effect again on the same EQ. The audio system drops or ignores an effect on the output mix when the
     * output route changes (Android Auto, a car, a dock) or a player starts after the route moved; pausing and playing
     * used to be the only way to get it back. The new effect fades in as the old one fades out, so when the old one was
     * still working the sound does not change at all.
     */
    @Synchronized
    fun rebuild() {
        val c = chain ?: return
        global?.let { retire(it) }
        global = null
        apply(c)
    }

    /** True while the effect on the output is on and in this app's control. */
    @Synchronized
    fun running(): Boolean = global?.let { runCatching { it.dp.enabled && it.dp.hasControl() }.getOrDefault(false) } ?: false

    @Synchronized
    fun off() {
        global?.let { retire(it) }
        global = null
        chain = null
        applied = emptyList()
        fade()
    }

    /** Moves every effect's bands to their goals in [EqRamp.STEPS] steps; a newer fade takes over from where this one got to. */
    private fun fade() {
        val id = ++fadeId
        val moves = (listOfNotNull(global) + leaving).map { it to EqRamp.frames(it.gains, it.goal) }
        fader.execute {
            for (k in 0 until EqRamp.STEPS) {
                synchronized(SystemEq) {
                    if (id != fadeId) return@execute
                    for ((fx, frames) in moves) set(fx, frames[k])
                }
                Thread.sleep(EqRamp.STEP_MS)
            }
            synchronized(SystemEq) {
                if (id != fadeId) return@execute
                leaving.forEach { runCatching { it.dp.release() } }
                leaving.clear()
            }
        }
    }

    private fun set(fx: Fx, g: FloatArray) {
        for (i in g.indices) {
            if (g[i] == fx.gains[i]) continue
            // an effect the audio system already dropped may refuse: it is being replaced or released anyway
            runCatching { fx.dp.setPreEqBandAllChannelsTo(i, DynamicsProcessing.EqBand(true, fx.edges[i], g[i])) }
            fx.gains[i] = g[i]
        }
    }
}

/** Keeps the equaliser alive while AutoTune is in the background, and brings it back when the output changes. */
class SystemEqService : Service() {
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    /** Why the pending rebuild was asked for, for the diagnostics log. */
    private var rebuildReason = ""
    private val rebuildNow = Runnable {
        val skipped = SystemEq.measuring
        val wasRunning = SystemEq.running()
        if (!skipped) SystemEq.rebuild()
        dev.autotune.Diag.log(this, "system_eq_rebuild", mapOf("reason" to rebuildReason, "wasRunning" to wasRunning, "skipped" to skipped, "route" to route.sorted()))
    }
    private var activePlayers = 0

    /** Waits a moment, so a burst of route and player changes rebuilds the effect once, after the new output is open. */
    private fun scheduleRebuild(reason: String) {
        if (SystemEq.chain == null) return
        rebuildReason = reason
        handler.removeCallbacks(rebuildNow)
        handler.postDelayed(rebuildNow, 600)
    }

    /** The outputs media plays on (Android 13+; before that, every output). The effect is rebuilt when this moves. */
    private fun mediaRoute(): Set<String> {
        val am = getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
        return runCatching {
            if (Build.VERSION.SDK_INT >= 33) {
                val media = android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_MEDIA).setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC).build()
                am.getAudioDevicesForAttributes(media).map { Outputs.nameOf(it) }
            } else am.getDevices(android.media.AudioManager.GET_DEVICES_OUTPUTS).map { Outputs.nameOf(it) }
        }.getOrDefault(emptyList()).toSet()
    }

    /** The media route the effect was last built on. */
    private var route: Set<String> = emptySet()
    /** False until Android has sent the list it sends on registering: every device, which is not a change. */
    private var devicesListed = false

    /** Rebuilds when media has moved to other outputs; a device coming or going that leaves the music where it was does nothing. */
    private val checkRoute = Runnable {
        val now = mediaRoute()
        if (dev.autotune.routeMoved(route, now)) {
            dev.autotune.Diag.log(this, "media_route", mapOf("was" to route.sorted(), "now" to now.sorted()))
            route = now
            SystemEq.routeChanged = true
            scheduleRebuild("output changed")
        }
    }

    private fun label(d: android.media.AudioDeviceInfo): String =
        if (d.isSink) "out " + Outputs.nameOf(d) else "in " + d.productName?.toString().orEmpty().ifBlank { "?" } + " #" + d.type

    private fun devicesChanged(what: String, devices: Array<out android.media.AudioDeviceInfo>) {
        // named in the log: a USB mic that drops out mid-measurement shows here
        if (devicesListed) dev.autotune.Diag.log(this, "audio_devices", mapOf(what to devices.map { label(it) }))
        devicesListed = true
        // the route settles a moment after the list changes
        handler.removeCallbacks(checkRoute)
        handler.postDelayed(checkRoute, 400)
    }

    private val deviceCallback = object : android.media.AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out android.media.AudioDeviceInfo>) = devicesChanged("added", addedDevices)
        override fun onAudioDevicesRemoved(removedDevices: Array<out android.media.AudioDeviceInfo>) = devicesChanged("removed", removedDevices)
    }

    private val playbackCallback = object : android.media.AudioManager.AudioPlaybackCallback() {
        override fun onPlaybackConfigChanged(configs: MutableList<android.media.AudioPlaybackConfiguration>) {
            val started = dev.autotune.playbackStarted(activePlayers, configs.size)
            activePlayers = configs.size
            // (a measurement's own sweep starting leaves everything for the next player)
            if (!started || SystemEq.measuring) return
            // the route can also move without a device coming or going: look again when a player starts
            handler.removeCallbacks(checkRoute)
            checkRoute.run()
            // not on every song: that rebuild clicked. Only the first player on a new output, or an effect that stopped.
            if (dev.autotune.rebuildOnPlayback(true, SystemEq.routeChanged, SystemEq.running())) {
                val reason = if (SystemEq.routeChanged) "first player after an output change" else "effect stopped"
                SystemEq.routeChanged = false
                scheduleRebuild(reason)
            }
        }
    }
    private var routeWatched = false

    private fun watchRoute() {
        if (routeWatched) return
        val am = getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
        route = mediaRoute() // before registering: Android answers a registration with every device it has
        am.registerAudioDeviceCallback(deviceCallback, handler)
        am.registerAudioPlaybackCallback(playbackCallback, handler)
        routeWatched = true
    }

    private var watcher: BluetoothWatcher? = null
    private var volume: VolumeWatcher? = null
    private val gesture = dev.autotune.VolumeGesture(3000)

    /** Watches the media volume while the EQ is on and the loud EQ is not Off: it follows it, and the volume keys switch it. */
    private fun updateVolumeWatch() {
        val want = SystemEq.chain != null && LoudEq.mode(this) != dev.autotune.LoudMode.OFF
        if (want && volume == null) volume = VolumeWatcher(this) { _, dir -> onVolumeStep(dir) }.also { it.start() }
        else if (!want) { volume?.stop(); volume = null }
    }

    private fun onVolumeStep(dir: Int) {
        if (!LoudEq.checking && LoudEq.keysOn(this) && gesture.step(android.os.SystemClock.elapsedRealtime(), dir)) {
            android.widget.Toast.makeText(this, LoudEq.toggleManual(this), android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        LoudEq.onVolume(this)
        if (LoudEq.lastAmount != shownLoud) refreshNotification()
    }

    /** The loud amount the notification shows. */
    private var shownLoud = -1.0

    private fun automationOn() = ProfileController.prefs(this).getBoolean("automationOn", false)

    private fun notify(text: String, title: String, force: Boolean = false) {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel(CHANNEL, "AutoTune EQ", NotificationManager.IMPORTANCE_LOW))
        val b = (if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL) else Notification.Builder(this))
            .setContentTitle(title).setContentText(text)
            // the Bauhaus mark in the status bar, tinted with the Braun red in the shade
            .setSmallIcon(dev.autotune.R.drawable.ic_stat_autotune).setColor(0xFFC8401A.toInt()).setOngoing(true)
            .setShowWhen(false).setCategory(Notification.CATEGORY_SERVICE)
            .setContentIntent(openApp())
        // switch things off right from the notification, without opening the app
        val prefs = ProfileController.prefs(this)
        if (SystemEq.chain != null || prefs.getBoolean("eqWanted", false)) b.addAction(action(android.R.drawable.ic_lock_power_off, "EQ off", ProfileController.ACTION_OFF, 201))
        if (automationOn()) b.addAction(action(android.R.drawable.ic_menu_close_clear_cancel, "Auto off", ProfileController.ACTION_AUTO_OFF, 202))
        if (SystemEq.chain != null && LoudEq.mode(this) != dev.autotune.LoudMode.OFF) {
            val loudOn = LoudEq.manual(this)
            b.addAction(action(android.R.drawable.ic_lock_silent_mode_off, if (loudOn) "Dynamic off" else "Dynamic on", ProfileController.ACTION_TOGGLE_LOUD, 203))
        }
        val n = b.build()
        // the sound level mode needs the microphone type; Android allows it only while the app is on screen
        val base = if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        val mic = if (Build.VERSION.SDK_INT >= 30) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0
        val want = if (wantsMeter()) base or mic else base
        if (want == fgType && !force) { getSystemService(NotificationManager::class.java).notify(42, n); return }
        try {
            ServiceCompat.startForeground(this, 42, n, want)
            fgType = want
            micAllowed = want != base || mic == 0
        } catch (e: Exception) {
            // not allowed from the background: keep the EQ running without the mic until the app is opened
            ServiceCompat.startForeground(this, 42, n, base)
            fgType = base
            micAllowed = false
        }
    }

    /** The foreground type in force (-1 before the first start), and whether the mic may be used. */
    private var fgType = -1
    private var micAllowed = false
    private var meter: LevelMeter? = null

    private fun wantsMeter(): Boolean =
        SystemEq.chain != null && LoudEq.mode(this) == dev.autotune.LoudMode.LEVEL &&
            ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED

    private fun updateMeter() {
        val want = wantsMeter()
        if (want && micAllowed) { if (meter == null) meter = LevelMeter(this).also { it.start() } }
        else {
            meter?.stop(); meter = null
            LoudEq.meterOff(this, if (want) "Open AutoTune once to start listening" else null)
        }
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
        updateVolumeWatch()
        try { refreshInner() } finally { updateMeter() }
    }

    private fun refreshInner() {
        val on = SystemEq.chain != null
        shownLoud = LoudEq.lastAmount
        val loud = if (on && LoudEq.lastAmount > 0.0) " · dynamic %.0f%%".format(LoudEq.lastAmount * 100) else ""
        // the user wants the EQ on but it could not start (yet): stay up and say so, as the widget does
        val wanted = ProfileController.prefs(this).getBoolean("eqWanted", false)
        when {
            // short, like the lettering on a device: what is on, then the detail
            on -> notify(
                (if (SystemEq.chain?.filters.isNullOrEmpty()) "Flat" else "Equalising") + loud + if (automationOn()) " · auto" else "",
                "EQ on",
            )
            wanted -> notify("Open AutoTune to switch it on", "EQ waiting")
            automationOn() -> notify("For your Bluetooth devices", "Watching")
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
        SystemEq.sampleRate = outputRate(this)
        // the foreground notice must go up first, whatever happens next
        notify(if (SystemEq.chain != null) "Equalising" else "For your Bluetooth devices",
            if (SystemEq.chain != null) "EQ on" else "Watching", force = true)
        watchRoute()
        if (automationOn()) startWatching()
        refreshNotification()
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(rebuildNow)
        handler.removeCallbacks(checkRoute)
        if (routeWatched) {
            val am = getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
            runCatching { am.unregisterAudioDeviceCallback(deviceCallback) }
            runCatching { am.unregisterAudioPlaybackCallback(playbackCallback) }
            routeWatched = false
        }
        watcher?.let { runCatching { unregisterReceiver(it) } }
        volume?.stop()
        volume = null
        meter?.stop()
        meter = null
        LoudEq.meterOff(this)
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

/** The phone's output mix rate (where the EQ's bins fall), 48 kHz when the phone does not say. */
fun outputRate(ctx: Context): Int =
    (ctx.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager)
        .getProperty(android.media.AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)?.toIntOrNull()?.takeIf { it in 8000..192000 } ?: 48000
