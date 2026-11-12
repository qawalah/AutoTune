package dev.autotune.theatre

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import dev.autotune.Flavour
import dev.autotune.audio.AutoTuneWidgets
import dev.autotune.companionChain
import dev.autotune.dsp.Speaker
import dev.autotune.filtersFrom
import dev.autotune.link.Msg
import dev.autotune.nextFlavour
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The Companion widget's keys (2.25; owner: "companion options should be in a widget for quick access"), run in the
 * background without opening the app: the EQ on the TV or PC on or off, and the next flavour. Each connects to the
 * remembered TV or PC first when needed ([CompanionLink.ensure]); the EQ sent is the home theatre's last one, kept by
 * the page, with the flavour on top.
 */
object CompanionActions {
    const val ACTION_EQ = "dev.autotune.action.COMPANION_EQ"
    const val ACTION_FLAVOUR = "dev.autotune.action.COMPANION_FLAVOUR"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun handles(action: String?) = action == ACTION_EQ || action == ACTION_FLAVOUR

    fun handle(ctx: Context, intent: Intent, done: BroadcastReceiver.PendingResult) {
        val app = ctx.applicationContext
        CompanionLink.init(app)
        val prefs = app.getSharedPreferences("autotune_prefs", Context.MODE_PRIVATE)
        scope.launch {
            val msg = try {
                when (intent.action) {
                    ACTION_FLAVOUR -> {
                        val f = nextFlavour(Flavour.of(prefs.getString(CompanionLink.PREF_FLAVOUR, null)))
                        prefs.edit().putString(CompanionLink.PREF_FLAVOUR, f.name).apply()
                        // while the EQ plays there, the new flavour goes at once
                        if (prefs.getBoolean(CompanionLink.PREF_EQ_ON, false)) send(app, prefs, f)
                        f.title
                    }
                    else -> if (prefs.getBoolean(CompanionLink.PREF_EQ_ON, false)) {
                        if (CompanionLink.ensure()) { CompanionLink.link.send(Msg.eqOff()); prefs.edit().putBoolean(CompanionLink.PREF_EQ_ON, false).apply(); "EQ off" }
                        else unreachable(app)
                    } else send(app, prefs, Flavour.of(prefs.getString(CompanionLink.PREF_FLAVOUR, null)))
                }
            } catch (e: Exception) {
                e.message ?: "Could not reach it"
            }
            AutoTuneWidgets.update(app)
            withContext(Dispatchers.Main) { Toast.makeText(app, msg, Toast.LENGTH_SHORT).show() }
            done.finish()
        }
    }

    /** Sends the kept EQ with [flavour] on top; the message for the toast. */
    private suspend fun send(app: Context, prefs: android.content.SharedPreferences, flavour: Flavour): String {
        val base = filtersFrom(prefs.getString(CompanionLink.PREF_BASE, null))
        if (base.isEmpty()) return "No EQ yet: test the EQ on the home-theatre page first"
        val speakers = prefs.getString(CompanionLink.PREF_SPEAKERS, null).orEmpty().split(',').mapNotNull { Speaker.of(it) }
            .ifEmpty { listOf(Speaker.FL, Speaker.FR) }
        if (!CompanionLink.ensure()) return unreachable(app)
        val chain = companionChain(base, flavour)
        CompanionLink.link.send(Msg.eq(speakers.associateWith { chain }))
        prefs.edit().putBoolean(CompanionLink.PREF_EQ_ON, true).apply()
        return "EQ on" + if (flavour != Flavour.NONE) " · ${flavour.title}" else ""
    }

    private fun unreachable(app: Context) =
        (CompanionLink.rememberedName(app)?.let { "$it did not answer: open AutoTune on it" } ?: "Connect to a TV or PC in AutoTune first")
}
