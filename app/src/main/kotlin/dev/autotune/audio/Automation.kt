package dev.autotune.audio

import android.app.Activity
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import dev.autotune.EqProfile
import dev.autotune.ProfileStore
import dev.autotune.R
import dev.autotune.dsp.EqChain
import dev.autotune.dsp.EqFilter

/** Tells an open app screen that the EQ or automation changed from outside it (widget, notification, Bluetooth). */
object EqBus {
    val changed = kotlinx.coroutines.flow.MutableStateFlow(0L)
    fun bump() { changed.value = System.nanoTime() }
}

/** Applies saved profiles without the app's screen: from the Bluetooth watcher, from a shortcut, or from another app's intent. */
object ProfileController {
    const val ACTION_APPLY = "dev.autotune.action.APPLY_PROFILE"
    const val ACTION_OFF = "dev.autotune.action.EQ_OFF"
    const val ACTION_TOGGLE_EQ = "dev.autotune.action.TOGGLE_EQ"
    const val ACTION_TOGGLE_AUTO = "dev.autotune.action.TOGGLE_AUTO"
    const val ACTION_NEXT_PROFILE = "dev.autotune.action.NEXT_PROFILE"
    const val ACTION_PREV_PROFILE = "dev.autotune.action.PREV_PROFILE"
    const val ACTION_AUTO_OFF = "dev.autotune.action.AUTO_OFF"
    const val EXTRA_PROFILE = "profile"
    private const val PREFS = "autotune_prefs"

    fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * What actually plays for [filters] (the EQ in use): the user's flavour on top and the preamp mode they chose, and
     * the system EQ's band count from Settings. Widgets, the Bluetooth watcher and restarts all play through this.
     */
    fun shaped(ctx: Context, filters: List<EqFilter>): EqChain {
        val p = prefs(ctx)
        dev.autotune.SystemEqConfig.fine = p.getBoolean("eqFine", false)
        return dev.autotune.playChain(
            filters, dev.autotune.Flavour.of(p.getString("flavour", null)), dev.autotune.PreampMode.of(p.getString("preampMode", null)),
            p.getFloat("preampManual", -6f).toDouble(),
        )
    }

    /** Finds a profile by id or by name (ignoring case). */
    fun find(ctx: Context, key: String?): EqProfile? {
        if (key.isNullOrBlank()) return null
        val all = ProfileStore.load(ctx)
        return all.firstOrNull { it.id == key } ?: all.firstOrNull { it.name.equals(key.trim(), ignoreCase = true) }
    }

    /** Switches the phone's EQ to [p]. Returns an error text, or null on success. */
    fun apply(ctx: Context, p: EqProfile): String? {
        if (p.filters.isEmpty()) return "“${p.name}” has no bands."
        val err = SystemEq.apply(shaped(ctx, p.filters))
        if (err != null) return err
        prefs(ctx).edit().putString("activeProfile", p.id).apply()
        remember(ctx, EqChain.withSafePreamp(p.filters))
        runCatching { ContextCompat.startForegroundService(ctx, Intent(ctx, SystemEqService::class.java)) }
        AutoTuneWidgets.update(ctx)
        EqBus.bump()
        return null
    }

    /**
     * Remembers the EQ that is on, so the service can put it back if the system kills and restarts it. Null = off.
     * An EQ with no bands is a wanted flat EQ (switched on before an EQ was picked); switching off keeps the last bands,
     * so the widget switches the same EQ back on, not an older profile.
     */
    fun remember(ctx: Context, chain: EqChain?) {
        val e = prefs(ctx).edit()
        if (chain == null) e.putBoolean("eqWanted", false)
        else if (chain.filters.isEmpty()) e.putBoolean("eqWanted", true).putBoolean("eqFlat", true)
        else e.putBoolean("eqWanted", true).putBoolean("eqFlat", false).putString("eqChain", chain.filters.joinToString(";") { "${it.freqHz},${it.gainDb},${it.q}" })
        e.apply()
    }

    /** The bands that were on last, or null when none were ever remembered. */
    private fun lastChain(ctx: Context): List<EqFilter>? = runCatching {
        prefs(ctx).getString("eqChain", null)?.split(";")?.filter { it.isNotBlank() }
            ?.map { s -> s.split(",").map { it.toDouble() }.let { EqFilter(it[0], it[1], it[2]) } }
    }.getOrNull()?.takeIf { it.isNotEmpty() }

    /** After a service restart or a reboot: switch the wanted EQ back on (flat, the remembered bands, or the active profile). */
    fun restore(ctx: Context) {
        val p = prefs(ctx)
        if (!p.getBoolean("eqWanted", false)) return
        val filters = if (p.getBoolean("eqFlat", false)) emptyList()
        else lastChain(ctx) ?: find(ctx, p.getString("activeProfile", null))?.filters ?: emptyList()
        SystemEq.apply(shaped(ctx, filters))
        AutoTuneWidgets.update(ctx)
        EqBus.bump()
    }

    fun off(ctx: Context) {
        SystemEq.off()
        remember(ctx, null)
        runCatching { ctx.startService(Intent(ctx, SystemEqService::class.java).setAction(SystemEqService.ACTION_STOP)) }
        AutoTuneWidgets.update(ctx)
        EqBus.bump()
    }

    /** Widget / shortcut: EQ off if it is on, otherwise on with the profile used last (or the first one). Returns a message. */
    fun toggleEq(ctx: Context): String {
        if (SystemEq.chain != null || prefs(ctx).getBoolean("eqWanted", false)) { off(ctx); return "AutoTune EQ off" }
        // back on with the EQ that was on last (a tuning suggestion is not always a profile)
        lastChain(ctx)?.let { f ->
            val c = EqChain.withSafePreamp(f)
            SystemEq.apply(shaped(ctx, f))?.let { return "AutoTune: $it" }
            remember(ctx, c)
            runCatching { ContextCompat.startForegroundService(ctx, Intent(ctx, SystemEqService::class.java)) }
            AutoTuneWidgets.update(ctx)
            EqBus.bump()
            return "AutoTune EQ on"
        }
        val all = ProfileStore.load(ctx)
        val p = find(ctx, prefs(ctx).getString("activeProfile", null)) ?: all.firstOrNull()
        if (p != null) return apply(ctx, p)?.let { "AutoTune: $it" } ?: "AutoTune EQ: ${p.name}"
        // nothing to play yet: switch on flat, so an EQ picked in the app plays at once
        val flat = EqChain.EMPTY
        SystemEq.apply(shaped(ctx, emptyList()))?.let { return "AutoTune: $it" }
        remember(ctx, flat)
        runCatching { ContextCompat.startForegroundService(ctx, Intent(ctx, SystemEqService::class.java)) }
        AutoTuneWidgets.update(ctx)
        EqBus.bump()
        return "AutoTune EQ on, flat. Pick an EQ in the app."
    }

    /** Automatic switching off (the notification's action). */
    fun autoOff(ctx: Context): String {
        if (prefs(ctx).getBoolean("automationOn", false)) toggleAuto(ctx)
        return "Automatic switching off"
    }

    /** Switches automatic profile switching on or off. */
    fun toggleAuto(ctx: Context): String {
        val on = !prefs(ctx).getBoolean("automationOn", false)
        prefs(ctx).edit().putBoolean("automationOn", on).apply()
        runCatching {
            if (on) ContextCompat.startForegroundService(ctx, Intent(ctx, SystemEqService::class.java).setAction(SystemEqService.ACTION_WATCH))
            else ctx.startService(Intent(ctx, SystemEqService::class.java).setAction(SystemEqService.ACTION_REFRESH))
        }
        AutoTuneWidgets.update(ctx)
        EqBus.bump()
        return if (!on) "Automatic switching off" else if (ProfileStore.loadRules(ctx).isEmpty()) "Automatic switching on. Add a device in the EQ tab, Advanced." else "Automatic switching on"
    }

    /** One entry for every action (widgets, notification, shortcuts, other apps). Returns the message to show. */
    fun handle(ctx: Context, intent: Intent): String? = when (intent.action) {
        ACTION_OFF -> { off(ctx); "AutoTune EQ off" }
        ACTION_TOGGLE_EQ -> toggleEq(ctx)
        ACTION_TOGGLE_AUTO -> toggleAuto(ctx)
        ACTION_AUTO_OFF -> autoOff(ctx)
        ACTION_NEXT_PROFILE -> cycle(ctx, 1)
        ACTION_PREV_PROFILE -> cycle(ctx, -1)
        else -> {
            val key = intent.getStringExtra(EXTRA_PROFILE) ?: intent.data?.lastPathSegment
            val p = find(ctx, key)
            if (p == null) "AutoTune: no profile “${key.orEmpty()}”"
            else apply(ctx, p)?.let { "AutoTune: $it" } ?: "AutoTune EQ: ${p.name}"
        }
    }

    /** Next or previous profile, applied right away. */
    fun cycle(ctx: Context, step: Int): String {
        val all = ProfileStore.load(ctx)
        if (all.isEmpty()) return "Save an EQ profile first"
        val cur = all.indexOfFirst { it.id == prefs(ctx).getString("activeProfile", null) }
        val p = all[((if (cur < 0) (if (step > 0) -1 else 0) else cur) + step + all.size) % all.size]
        return apply(ctx, p)?.let { "AutoTune: $it" } ?: "AutoTune EQ: ${p.name}"
    }

    /** One launcher shortcut per profile (and one to switch off), so Routines, Tasker and the home screen can pick a profile. */
    fun publishShortcuts(ctx: Context, profiles: List<EqProfile>) {
        runCatching {
            val list = ArrayList<ShortcutInfoCompat>()
            val max = ShortcutManagerCompat.getMaxShortcutCountPerActivity(ctx).coerceAtMost(5)
            profiles.take(max - 1).forEach { p ->
                list += ShortcutInfoCompat.Builder(ctx, "profile_${p.id}")
                    .setShortLabel(p.name.take(25)).setLongLabel("EQ: ${p.name}")
                    .setIcon(IconCompat.createWithResource(ctx, R.mipmap.ic_launcher))
                    .setIntent(Intent(ctx, ApplyProfileActivity::class.java).setAction(ACTION_APPLY).putExtra(EXTRA_PROFILE, p.id))
                    .build()
            }
            list += ShortcutInfoCompat.Builder(ctx, "eq_off")
                .setShortLabel("EQ off").setLongLabel("AutoTune EQ off")
                .setIcon(IconCompat.createWithResource(ctx, R.mipmap.ic_launcher))
                .setIntent(Intent(ctx, ApplyProfileActivity::class.java).setAction(ACTION_OFF))
                .build()
            ShortcutManagerCompat.setDynamicShortcuts(ctx, list)
        }
    }
}

/** Widget taps and notification actions: done in the background, without opening the app. */
class EqActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val msg = ProfileController.handle(context, intent)
        if (msg != null && intent.getBooleanExtra(EXTRA_TOAST, true)) Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    }

    companion object {
        const val EXTRA_TOAST = "toast"

        fun pending(ctx: Context, action: String, code: Int, toast: Boolean = true): android.app.PendingIntent =
            android.app.PendingIntent.getBroadcast(
                ctx, code, Intent(ctx, EqActionReceiver::class.java).setAction(action).putExtra(EXTRA_TOAST, toast),
                android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT,
            )
    }
}

/**
 * Invisible screen that applies a profile and closes. Opened by an app shortcut, by a Samsung routine's "Open app", or by
 * another automation app sending [ProfileController.ACTION_APPLY] with a "profile" extra (id or name) or an
 * autotune://profile/NAME link. Being a visible activity for a moment is what lets Android allow the EQ service to start.
 */
class ApplyProfileActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        intent?.let { ProfileController.handle(this, it) }?.let { Toast.makeText(this, it, Toast.LENGTH_SHORT).show() }
        finish()
    }
}

/** Starts the Bluetooth watcher again after a restart, if the user turned automation on. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        // the EQ was on before the reboot: put it back (the service keeps it alive)
        if (ProfileController.prefs(context).getBoolean("eqWanted", false)) {
            ProfileController.restore(context)
            if (SystemEq.chain != null) runCatching { ContextCompat.startForegroundService(context, Intent(context, SystemEqService::class.java)) }
            return
        }
        if (ProfileController.prefs(context).getBoolean("automationOn", false) && ProfileStore.loadRules(context).isNotEmpty()) {
            runCatching {
                ContextCompat.startForegroundService(context, Intent(context, SystemEqService::class.java).setAction(SystemEqService.ACTION_WATCH))
            }
        }
    }
}

/** Reacts to Bluetooth devices connecting and disconnecting (registered by the running service). */
class BluetoothWatcher(private val ctx: Context, private val onChange: () -> Unit) : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val dev: BluetoothDevice = (if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        else @Suppress("DEPRECATION") intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)) ?: return
        if (intent.action == BluetoothDevice.ACTION_ACL_CONNECTED) ProfileController.prefs(ctx).edit().putLong("btSeen_" + dev.address, System.currentTimeMillis()).apply()
        val rule = ProfileStore.loadRules(ctx).firstOrNull { it.address.equals(dev.address, ignoreCase = true) } ?: return
        when (intent.action) {
            BluetoothDevice.ACTION_ACL_CONNECTED -> ProfileController.find(ctx, rule.profileId)?.let { ProfileController.apply(ctx, it) }
            BluetoothDevice.ACTION_ACL_DISCONNECTED ->
                if (ProfileController.prefs(ctx).getBoolean("offOnDisconnect", true)) { SystemEq.off(); ProfileController.prefs(ctx).edit().putBoolean("eqWanted", false).apply() }
        }
        AutoTuneWidgets.update(ctx)
        EqBus.bump()
        onChange()
    }
}
