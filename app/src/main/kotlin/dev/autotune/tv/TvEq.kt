package dev.autotune.tv

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.audiofx.DynamicsProcessing
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import dev.autotune.dsp.EqChain
import dev.autotune.dsp.PlayMethod
import dev.autotune.dsp.Rta
import dev.autotune.dsp.Speaker
import dev.autotune.link.Msg

/**
 * The TV's own EQ: one EQ per output channel on the TV's whole sound (DynamicsProcessing on the output mix). It only
 * reaches sound the TV itself turns into PCM; Dolby or DTS passed straight to the home theatre skips it, and so do the
 * Dolby test clips (so every test reads the home theatre as it is). Saved in prefs and kept up by [TvEqService].
 */
object TvEq {
    private const val PREFS = "autotune_tv"
    private var dp: DynamicsProcessing? = null
    private val centres = Rta.CENTERS

    @Volatile
    var chains: Map<Speaker, EqChain> = emptyMap()
        private set
    val on: Boolean get() = chains.isNotEmpty()

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Sets the EQ per speaker (empty = off) and remembers it. Returns an error in words, or null. */
    @Synchronized
    fun set(ctx: Context, c: Map<Speaker, EqChain>, method: PlayMethod): String? {
        prefs(ctx).edit().putString("chains", if (c.isEmpty()) null else Msg.chainsText(c)).putString("method", method.name).apply()
        chains = c
        val err = apply(method)
        if (c.isEmpty()) ctx.stopService(Intent(ctx, TvEqService::class.java))
        else runCatching { androidx.core.content.ContextCompat.startForegroundService(ctx, Intent(ctx, TvEqService::class.java)) }
        return err
    }

    /** Brings back the saved EQ (after the service restarts). */
    @Synchronized
    fun restore(ctx: Context) {
        val p = prefs(ctx)
        chains = Msg.chains(p.getString("chains", null))
        apply(PlayMethod.of(p.getString("method", null)) ?: PlayMethod.STEREO)
    }

    /** Switches the effect off for a moment (a raw test clip must read the system as it is) and back on. */
    @Synchronized
    fun hold(held: Boolean) { runCatching { dp?.enabled = !held && on } }

    @Synchronized
    private fun apply(method: PlayMethod): String? {
        runCatching { dp?.release() }
        dp = null
        if (chains.isEmpty()) return null
        if (Build.VERSION.SDK_INT < 28) return "This TV is too old for an app EQ (Android 9 or newer)."
        return try {
            val slots = method.slots
            val cfg = DynamicsProcessing.Config.Builder(
                DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION, slots.size, true, centres.size, false, 0, false, 0, true,
            ).build()
            val d = DynamicsProcessing(0, 0, cfg)
            // stereo output: the fronts' EQ; a channel with no EQ of its own stays flat
            for ((ch, s) in slots.withIndex()) {
                val c = s?.let { chains[it] }
                for (i in centres.indices) {
                    val edge = minOf(centres[i] * Rta.EDGE, 21000.0)
                    val g = c?.let { it.responseDb(centres[i]).coerceIn(-30.0, 15.0) } ?: 0.0
                    d.setPreEqBandByChannelIndex(ch, i, DynamicsProcessing.EqBand(true, edge.toFloat(), g.toFloat()))
                }
            }
            d.setLimiterAllChannelsTo(DynamicsProcessing.Limiter(true, true, 0, 1f, 60f, 10f, -2f, 0f))
            d.enabled = true
            dp = d
            null
        } catch (e: Exception) {
            "This TV does not let an app EQ its sound (${e.message ?: e.javaClass.simpleName})."
        }
    }
}

/** Keeps [TvEq] alive while it is on. */
class TvEqService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel("tv_eq", "AutoTune TV EQ", NotificationManager.IMPORTANCE_LOW))
        val n: Notification = androidx.core.app.NotificationCompat.Builder(this, "tv_eq")
            .setSmallIcon(dev.autotune.R.drawable.ic_stat_autotune).setContentTitle("AutoTune EQ on").setOngoing(true).build()
        val type = if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        runCatching { ServiceCompat.startForeground(this, 43, n, type) }
        if (!TvEq.on) TvEq.restore(this)
        if (!TvEq.on) stopSelf()
        return START_STICKY
    }
}
