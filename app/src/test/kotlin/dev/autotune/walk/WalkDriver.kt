@file:OptIn(androidx.compose.ui.InternalComposeUiApi::class)

package dev.autotune.walk

import android.Manifest
import android.app.Activity
import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.unit.IntSize
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import dev.autotune.MainActivity
import dev.autotune.audio.MeasurementRunner
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Runs the real AutoTune screens on the JVM with a pretend car, and takes commands from a folder: this is how a
 * first-time-user agent "holds the phone". Start it with `tools/walk/start.sh`; talk to it with `tools/walk/walk.sh`.
 * Coordinates are in the pixels of the pictures it saves (540 wide); it doubles them for the real 1080-wide screen.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class WalkDriver {
    @get:Rule val rule = createEmptyComposeRule()

    private val root = File(System.getProperty("walk.dir") ?: "/tmp/walk")
    private var scenario: ActivityScenario<MainActivity>? = null
    private var activity: Activity? = null
    private var n = 0
    private var lastTexts: List<String> = emptyList()
    private var lastBitmap: Bitmap? = null
    private var virtualSec = 0.0
    private var lastPermReq: Any? = null

    private fun launch() {
        scenario?.close()
        scenario = ActivityScenario.launch(MainActivity::class.java)
        scenario!!.onActivity { activity = it }
        pump(1500)
    }

    /** Lets the app run for [ms] of phone time. Background work gets real time too, so a 10 s sweep takes a moment. */
    private fun pump(ms: Int, realPerStepMs: Long = 4) {
        var t = 0
        while (t < ms) {
            rule.mainClock.advanceTimeBy(50)
            ShadowLooper.idleMainLooper(50, TimeUnit.MILLISECONDS)
            Thread.sleep(realPerStepMs)
            t += 50
        }
        virtualSec += ms / 1000.0
    }

    /** Every window on screen (the app, drop-downs, dialogs), bottom to top, with its Compose view. */
    private class Win(val root: android.view.View, val compose: androidx.compose.ui.node.RootForTest)

    private fun windows(): List<Win> {
        val out = ArrayList<Win>()
        val wmg = Class.forName("android.view.WindowManagerGlobal")
        val inst = wmg.getMethod("getInstance").invoke(null)
        @Suppress("UNCHECKED_CAST")
        val names = (wmg.getMethod("getViewRootNames").invoke(inst) as Array<String>)
        for (nm in names) {
            val v = wmg.getMethod("getRootView", String::class.java).invoke(inst, nm) as? android.view.View ?: continue
            if (!v.isAttachedToWindow || v.visibility != android.view.View.VISIBLE) continue
            findCompose(v)?.let { out += Win(v, it) }
        }
        return out
    }

    private fun findCompose(v: android.view.View): androidx.compose.ui.node.RootForTest? {
        if (v is androidx.compose.ui.node.RootForTest) return v
        if (v is android.view.ViewGroup) for (i in 0 until v.childCount) findCompose(v.getChildAt(i))?.let { return it }
        return null
    }

    private fun collect(n: SemanticsNode, out: MutableList<String>) {
        n.config.getOrNull(SemanticsProperties.Text)?.forEach { out += it.text }
        n.config.getOrNull(SemanticsProperties.ContentDescription)?.forEach { out += "[icon: $it]" }
        n.children.forEach { collect(it, out) }
    }

    private fun findFocusedEditable(n: SemanticsNode): SemanticsNode? {
        if (n.config.getOrNull(SemanticsProperties.Focused) == true && n.config.contains(androidx.compose.ui.semantics.SemanticsActions.SetText)) return n
        for (c in n.children) findFocusedEditable(c)?.let { return it }
        return null
    }

    private fun texts(): List<String> {
        val out = ArrayList<String>()
        for (w in windows()) collect(w.compose.semanticsOwner.rootSemanticsNode, out)
        return out
    }

    private fun drawView(v: android.view.View): Bitmap {
        val bm = Bitmap.createBitmap(maxOf(1, v.width), maxOf(1, v.height), Bitmap.Config.ARGB_8888)
        v.draw(Canvas(bm))
        return bm
    }

    /** One picture of everything on screen: the app, plus drop-downs and dialogs on top of it. */
    private fun screenshot(): Bitmap {
        val ws = windows()
        val main = drawView(activity!!.window.decorView).copy(Bitmap.Config.ARGB_8888, true)
        val c = Canvas(main)
        for (w in ws) {
            if (w.root === activity!!.window.decorView) continue
            val p = w.compose.semanticsOwner.rootSemanticsNode.positionOnScreen
            val loc = IntArray(2); w.root.getLocationOnScreen(loc)
            c.drawBitmap(drawView(w.root), (if (loc[0] != 0 || loc[1] != 0) loc[0] else p.x.toInt()).toFloat(), (if (loc[0] != 0 || loc[1] != 0) loc[1] else p.y.toInt()).toFloat(), null)
        }
        return main
    }

    private fun save(bm: Bitmap, f: File) {
        f.parentFile!!.mkdirs()
        val small = Bitmap.createScaledBitmap(bm, bm.width / 2, bm.height / 2, true)
        f.outputStream().use { small.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun diff(a: Bitmap?, b: Bitmap): Double {
        if (a == null || a.width != b.width || a.height != b.height) return 1.0
        var d = 0; var tot = 0
        for (y in 0 until a.height step 6) for (x in 0 until a.width step 6) { tot++; if (a.getPixel(x, y) != b.getPixel(x, y)) d++ }
        return d.toDouble() / tot
    }

    private fun target(px: Float, py: Float): Pair<android.view.View, FloatArray> {
        val ws = windows()
        for (w in ws.reversed()) {
            val v = w.compose as android.view.View
            val loc = IntArray(2); v.getLocationOnScreen(loc)
            if (px >= loc[0] && px < loc[0] + v.width && py >= loc[1] && py < loc[1] + v.height) return v to floatArrayOf(px - loc[0], py - loc[1])
        }
        val v = (ws.firstOrNull() ?: error("The app is not on screen. Say `open`.")).compose as android.view.View
        return v to floatArrayOf(px, py)
    }

    private fun touch(v: android.view.View, pts: List<FloatArray>, holdMs: Int) {
        val down = android.os.SystemClock.uptimeMillis()
        fun ev(action: Int, p: FloatArray, t: Long) = android.view.MotionEvent.obtain(down, t, action, p[0], p[1], 0)
        v.dispatchTouchEvent(ev(android.view.MotionEvent.ACTION_DOWN, pts.first(), down))
        val steps = pts.size - 1
        val per = maxOf(16, holdMs / maxOf(1, steps))
        for (k in 1..steps) { pump(per); v.dispatchTouchEvent(ev(android.view.MotionEvent.ACTION_MOVE, pts[k], android.os.SystemClock.uptimeMillis())) }
        if (steps == 0) pump(holdMs)
        v.dispatchTouchEvent(ev(android.view.MotionEvent.ACTION_UP, pts.last(), android.os.SystemClock.uptimeMillis()))
    }

    /** Things the phone does outside the app: permission questions, other apps opening. Reported in words. */
    private fun systemNotes(): List<String> {
        val a = activity ?: return emptyList()
        val notes = ArrayList<String>()
        val sh = shadowOf(a)
        sh.lastRequestedPermission?.takeIf { it !== lastPermReq }?.let { req ->
            lastPermReq = req
            notes += "The phone showed a pop-up: \"Allow AutoTune to record audio?\" You (the helper) tapped Allow."
            shadowOf(ApplicationProvider.getApplicationContext<Application>()).grantPermissions(*req.requestedPermissions)
            scenario?.onActivity { it.onRequestPermissionsResult(req.requestCode, req.requestedPermissions, IntArray(req.requestedPermissions.size)) }
            pump(400)
        }
        while (true) {
            val i: Intent = sh.nextStartedActivityForResult?.intent ?: break
            if (i.action == "android.content.pm.action.REQUEST_PERMISSIONS") continue
            notes += "The phone opened its file chooser (${i.action}). It is empty on this phone, so it closed again with nothing picked."
        }
        while (true) {
            val i: Intent = sh.nextStartedActivity ?: break
            if (i.action == "android.content.pm.action.REQUEST_PERMISSIONS") continue
            notes += "The phone opened another app or sheet: ${i.action ?: i.component} ${i.dataString ?: ""} ${i.getStringExtra(Intent.EXTRA_TEXT)?.take(80) ?: ""}".trim()
        }
        if (a.isFinishing || a.isDestroyed) notes += "You left the app. The screen is the phone's home screen. Say `open` to open AutoTune again."
        return notes
    }

    private fun log(cmd: String, extra: Map<String, Any?>) {
        val f = File(root, "hidden/log.jsonl"); f.parentFile!!.mkdirs()
        f.appendText("{\"n\":$n,\"cmd\":${q(cmd)},${extra.entries.joinToString(",") { "\"${it.key}\":${q(it.value)}" }}}\n")
    }
    private fun q(v: Any?): String = when (v) {
        null -> "null"; is Number, is Boolean -> v.toString()
        is List<*> -> v.joinToString(",", "[", "]") { q(it) }
        else -> "\"" + v.toString().replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ") + "\""
    }

    private fun run(line: String): String {
        val parts = line.trim().split(" ").filter { it.isNotEmpty() }
        val cmd = parts.firstOrNull() ?: return "empty"
        val before = lastBitmap
        var msg = "ok"
        when (cmd) {
            "shot" -> {}
            "open" -> launch()
            "tap", "long" -> {
                val px = parts[1].toFloat() * 2; val py = parts[2].toFloat() * 2
                val (v, p) = target(px, py)
                touch(v, listOf(p), if (cmd == "tap") 60 else 800)
                pump(700)
            }
            "swipe" -> {
                val v = parts.drop(1).map { it.toFloat() * 2 }
                val (view, a) = target(v[0], v[1])
                val dx = v[2] - v[0]; val dy = v[3] - v[1]
                val pts = (0..8).map { k -> floatArrayOf(a[0] + dx * k / 8f, a[1] + dy * k / 8f) }
                touch(view, pts, 400)
                pump(700)
            }
            "type" -> {
                val text = line.substringAfter("type ")
                var done = false
                for (w in windows().reversed()) {
                    val node = findFocusedEditable(w.compose.semanticsOwner.rootSemanticsNode) ?: continue
                    val old = node.config.getOrNull(SemanticsProperties.EditableText)?.text ?: ""
                    node.config[androidx.compose.ui.semantics.SemanticsActions.SetText].action?.invoke(androidx.compose.ui.text.AnnotatedString(old + text))
                    done = true; break
                }
                if (!done) msg = "Nothing happened: no box is waiting for typing. Tap a box first."
                pump(300)
            }
            "back" -> {
                scenario?.onActivity { it.onBackPressedDispatcher.onBackPressed() }
                pump(700)
            }
            "wait" -> {
                val s = parts.getOrNull(1)?.toDoubleOrNull() ?: 1.0
                pump((s * 1000).toInt(), realPerStepMs = 12)
            }
            else -> msg = "Unknown command $cmd"
        }
        val notes = systemNotes()
        val bm = screenshot()
        val png = File(root, "shots/%03d.png".format(n))
        save(bm, png)
        val now = texts()
        val changed = diff(before, bm)
        log(line, mapOf("changedFrac" to Math.round(changed * 1000) / 1000.0, "sameTexts" to (now == lastTexts), "notes" to notes, "texts" to now.take(80), "virtualSec" to virtualSec))
        lastBitmap = bm; lastTexts = now
        val sb = StringBuilder()
        sb.append(msg).append('\n').append("screenshot: ").append(png.path).append('\n')
        if (changed < 0.002) sb.append("(the screen looks exactly the same as before)\n")
        notes.forEach { sb.append("PHONE: ").append(it).append('\n') }
        return sb.toString()
    }

    @Test
    fun drive() {
        root.mkdirs()
        File(root, "in").mkdirs(); File(root, "out").mkdirs()
        val sim = CarSim()
        MeasurementRunner.simulator = sim
        shadowOf(ApplicationProvider.getApplicationContext<Application>()).denyPermissions(Manifest.permission.RECORD_AUDIO)
        launch()
        File(root, "ready").writeText("ready")
        while (true) {
            val cmdFile = File(root, "in/%03d.cmd".format(n))
            if (!cmdFile.exists()) { Thread.sleep(40); if (File(root, "quit").exists()) break; continue }
            Thread.sleep(30)
            val line = cmdFile.readText()
            if (line.trim() == "quit") { File(root, "out/%03d.txt".format(n)).writeText("bye"); break }
            val res = try { run(line) } catch (e: Throwable) { "DRIVER ERROR: $e\n" + e.stackTrace.take(8).joinToString("\n") }
            File(root, "out/%03d.txt".format(n)).writeText(res)
            n++
        }
        MeasurementRunner.simulator = null
    }
}
