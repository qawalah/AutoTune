package dev.autotune.audio

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.SizeF
import android.widget.RemoteViews
import dev.autotune.MainActivity
import dev.autotune.ProfileStore
import dev.autotune.R

/** Home-screen widgets: a wide panel (Tune, EQ, Auto, and profile switcher when tall) plus single toggles. */
object AutoTuneWidgets {
    // the app's colours: white lettering on the accent while on, ink on the white keys while off
    private const val ON_TEXT = 0xFFFFFFFF.toInt()
    private const val OFF_TEXT = 0xFF2E2E2B.toInt()
    private val providers = listOf(PanelWidget::class.java, EqWidget::class.java, AutoWidget::class.java, ProfileWidget::class.java, CompanionWidget::class.java)

    fun update(ctx: Context) {
        val mgr = AppWidgetManager.getInstance(ctx)
        for (cls in providers) {
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, cls))
            if (ids.isNotEmpty()) for (id in ids) mgr.updateAppWidget(id, build(ctx, cls))
        }
    }

    // a broadcast, not an activity: tapping a toggle never brings the app to the front
    private fun act(ctx: Context, action: String, code: Int): PendingIntent = EqActionReceiver.pending(ctx, action, 100 + code)

    private fun openApp(ctx: Context, tab: String): PendingIntent =
        PendingIntent.getActivity(
            ctx, 10 + (tab.hashCode() and 0xff), Intent(ctx, MainActivity::class.java).putExtra("open", tab).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private class St(val eqOn: Boolean, val autoOn: Boolean, val profile: String?, val loudOn: Boolean)

    private fun state(ctx: Context): St {
        val p = ProfileController.prefs(ctx)
        val id = p.getString("activeProfile", null)
        val name = ProfileStore.load(ctx).firstOrNull { it.id == id }?.name
        return St(SystemEq.chain != null || p.getBoolean("eqWanted", false), p.getBoolean("automationOn", false), name, LoudEq.manual(ctx))
    }

    private fun chip(v: RemoteViews, root: Int, title: Int, sub: Int, on: Boolean, text: String) {
        v.setInt(root, "setBackgroundResource", if (on) R.drawable.widget_chip_on else R.drawable.widget_chip_off)
        val c = if (on) ON_TEXT else OFF_TEXT
        v.setTextColor(title, c)
        v.setTextColor(sub, c)
        v.setTextViewText(sub, text)
    }

    private fun panel(ctx: Context, tall: Boolean, st: St): RemoteViews {
        val v = RemoteViews(ctx.packageName, if (tall) R.layout.widget_panel_tall else R.layout.widget_panel)
        v.setOnClickPendingIntent(R.id.chip_tune, openApp(ctx, "measure"))
        v.setOnClickPendingIntent(R.id.chip_eq, act(ctx, ProfileController.ACTION_TOGGLE_EQ, 1))
        v.setOnClickPendingIntent(R.id.chip_auto, act(ctx, ProfileController.ACTION_TOGGLE_AUTO, 2))
        // the EQ key names the EQ in use while it is on
        chip(v, R.id.chip_eq, R.id.chip_eq_title, R.id.chip_eq_state, st.eqOn, if (st.eqOn) st.profile?.substringBefore(" · ") ?: "On" else "Off")
        v.setOnClickPendingIntent(R.id.chip_loud, act(ctx, ProfileController.ACTION_TOGGLE_LOUD, 5))
        chip(v, R.id.chip_loud, R.id.chip_loud_title, R.id.chip_loud_state, st.loudOn, if (st.loudOn) "On" else "Off")
        chip(v, R.id.chip_auto, R.id.chip_auto_title, R.id.chip_auto_state, st.autoOn, if (st.autoOn) "On" else "Off")
        if (tall) {
            v.setTextViewText(R.id.profile_name, st.profile ?: "No profile")
            v.setOnClickPendingIntent(R.id.prev, act(ctx, ProfileController.ACTION_PREV_PROFILE, 3))
            v.setOnClickPendingIntent(R.id.next, act(ctx, ProfileController.ACTION_NEXT_PROFILE, 4))
            v.setOnClickPendingIntent(R.id.profile_name, act(ctx, ProfileController.ACTION_NEXT_PROFILE, 4))
            v.setOnClickPendingIntent(R.id.companion, openApp(ctx, "theatre"))
        }
        return v
    }

    /**
     * The Companion widget (2.25): the TV or PC by name (opens the home-theatre page) and whether it is connected, its
     * EQ on or off, and the flavour (each tap the next). The keys work with the app closed ([dev.autotune.theatre.CompanionActions]).
     */
    private fun companion(ctx: Context): RemoteViews {
        val p = ProfileController.prefs(ctx)
        val v = RemoteViews(ctx.packageName, R.layout.widget_companion)
        val name = p.getString(dev.autotune.theatre.CompanionLink.PREF_NAME, null)
        val connected = dev.autotune.theatre.CompanionLink.open
        v.setTextViewText(R.id.chip_cmp_title, name ?: "TV or PC")
        // a TV answers only while AutoTune is open on it: say so where the state is
        val tv = dev.autotune.theatre.CompanionLink.rememberedKind(ctx) != dev.autotune.link.Msg.KIND_PC
        v.setTextViewText(R.id.chip_cmp_state, if (name == null) "Connect" else if (connected) "Connected" else if (tv) "Open AutoTune on the TV" else "Not connected")
        v.setOnClickPendingIntent(R.id.chip_cmp, openApp(ctx, "theatre"))
        val eqOn = p.getBoolean(dev.autotune.theatre.CompanionLink.PREF_EQ_ON, false)
        chip(v, R.id.chip_cmp_eq, R.id.chip_cmp_eq_title, R.id.chip_cmp_eq_state, eqOn, if (eqOn) "On" else "Off")
        v.setOnClickPendingIntent(R.id.chip_cmp_eq, act(ctx, dev.autotune.theatre.CompanionActions.ACTION_EQ, 20))
        val f = dev.autotune.Flavour.of(p.getString(dev.autotune.theatre.CompanionLink.PREF_FLAVOUR, null))
        chip(v, R.id.chip_cmp_flavour, R.id.chip_cmp_flavour_title, R.id.chip_cmp_flavour_state, f != dev.autotune.Flavour.NONE, f.title)
        v.setOnClickPendingIntent(R.id.chip_cmp_flavour, act(ctx, dev.autotune.theatre.CompanionActions.ACTION_FLAVOUR, 21))
        return v
    }

    private fun single(ctx: Context, title: String, sub: String, on: Boolean, tap: PendingIntent): RemoteViews {
        val v = RemoteViews(ctx.packageName, R.layout.widget_chip)
        v.setTextViewText(R.id.chip_title, title)
        chip(v, R.id.chip, R.id.chip_title, R.id.chip_state, on, sub)
        v.setOnClickPendingIntent(R.id.chip, tap)
        return v
    }

    fun build(ctx: Context, cls: Class<*>): RemoteViews {
        val st = state(ctx)
        return when (cls) {
            PanelWidget::class.java ->
                if (Build.VERSION.SDK_INT >= 31) RemoteViews(mapOf(SizeF(110f, 40f) to panel(ctx, false, st), SizeF(110f, 100f) to panel(ctx, true, st)))
                else panel(ctx, false, st)
            EqWidget::class.java -> single(ctx, "EQ", if (st.eqOn) "On" else "Off", st.eqOn, act(ctx, ProfileController.ACTION_TOGGLE_EQ, 1))
            CompanionWidget::class.java -> companion(ctx)
            AutoWidget::class.java -> single(ctx, "Auto", if (st.autoOn) "On" else "Off", st.autoOn, act(ctx, ProfileController.ACTION_TOGGLE_AUTO, 2))
            else -> single(ctx, "Profile", st.profile ?: "None", st.eqOn && st.profile != null, act(ctx, ProfileController.ACTION_NEXT_PROFILE, 4))
        }
    }
}

abstract class BaseWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (id in appWidgetIds) appWidgetManager.updateAppWidget(id, AutoTuneWidgets.build(context, this::class.java))
    }
}

class PanelWidget : BaseWidget()
class EqWidget : BaseWidget()
class AutoWidget : BaseWidget()
class ProfileWidget : BaseWidget()
class CompanionWidget : BaseWidget()
