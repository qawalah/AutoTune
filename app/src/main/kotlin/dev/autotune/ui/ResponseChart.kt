@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package dev.autotune.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.runtime.mutableStateOf
import dev.autotune.ChartData
import kotlin.math.ln

/** The dB range that shows every visible curve: at least ±15 dB, in 5 dB steps, never beyond ±45 dB. */
internal fun chartRange(series: List<DoubleArray>, freqs: DoubleArray): Pair<Double, Double> {
    var lo = 0.0; var hi = 0.0
    for (v in series) for (i in v.indices) {
        if (freqs[i] < 20.0 || freqs[i] > 20000.0 || !v[i].isFinite()) continue
        lo = minOf(lo, v[i]); hi = maxOf(hi, v[i])
    }
    val top = (Math.ceil((hi + 1.0) / 5.0) * 5.0).coerceIn(15.0, 45.0)
    val bottom = (Math.floor((lo - 1.0) / 5.0) * 5.0).coerceIn(-45.0, -15.0)
    return bottom to top
}

/**
 * Frequency response on chart paper, log axis, around the target. Plotter-pen lines = measured runs (the stock dashed
 * as the baseline, on every chart, alone too), thin faint line = target, solid in the accent colour =
 * the EQ, dotted text colour = predicted with the EQ. Tap a name in the legend to hide or show its curve; the corner
 * button opens the graph full screen. The range grows to fit the curves, so nothing is cut off at the edge.
 * The graph is drawn still (since 2.30.0). Touching it reads each measured curve, the target and the prediction at
 * that frequency; the EQ curve is never read out.
 */
@Composable
fun ResponseChart(
    data: ChartData, modifier: Modifier = Modifier, height: androidx.compose.ui.unit.Dp = 220.dp,
    showTarget: Boolean = true, onToggleTarget: (() -> Unit)? = null, targetLabel: String = "Target",
) {
    // hidden curves, by key; kept while the same curves are shown, and shared with the full-screen view
    val hiddenState = androidx.compose.runtime.saveable.rememberSaveable(data.curves.joinToString { it.label }) { androidx.compose.runtime.mutableStateOf(listOf<String>()) }
    val hidden = hiddenState.value
    val toggle: (String) -> Unit = { k -> hiddenState.value = if (k in hiddenState.value) hiddenState.value - k else hiddenState.value + k }
    // the full-screen view is drawn by the app shell over everything, in the same window ([ChartZoomLayer]);
    // outside the shell (the screenshot sheets) a dialog stands in
    val zoom = LocalChartZoom.current
    val me = androidx.compose.runtime.remember { Any() }
    var big by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    // the graph is drawn still: no movement when it comes into view (owner, 2026-11-13)
    Column(modifier.settleIn()) {
        Box {
            ChartCanvas(data, Modifier.fillMaxWidth().height(height), showTarget, hidden)
            Box(Modifier.align(Alignment.TopEnd)) {
                IconAction(Ic.EXPAND, "Show the graph full screen") {
                    if (zoom != null) zoom.show(me, data, showTarget, onToggleTarget, targetLabel, hiddenState) else big = true
                }
            }
        }
        Legend(data, showTarget, onToggleTarget, hidden, toggle, targetLabel)
    }
    if (zoom != null) {
        // while it is open, the full-screen view follows this graph (a curve hidden, the target shown, new data)
        if (zoom.owner === me) androidx.compose.runtime.SideEffect { zoom.show(me, data, showTarget, onToggleTarget, targetLabel, hiddenState) }
        // not closed when this chart leaves the composition: turning the phone can lay the screen out again (the EQ tab does)
        // and that must not drop the full-screen graph; the shell closes it when the tab or page changes
    }
    if (big && zoom == null) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { big = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        ) {
            androidx.compose.material3.Surface(Modifier.fillMaxSize(), color = At.c.bg, contentColor = At.c.text) {
                ZoomedChartView(data, showTarget, onToggleTarget, targetLabel, hiddenState) { big = false }
            }
        }
    }
}

/** The graph a chart opened full screen: what to draw, shared with the chart on the page while it is open. */
class ZoomedChart internal constructor(
    val data: ChartData, val showTarget: Boolean, val targetLabel: String,
    internal val hidden: androidx.compose.runtime.MutableState<List<String>>,
) {
    /** Not part of what is drawn, so a new lambda on each recomposition does not redraw the view. */
    internal var onToggleTarget: (() -> Unit)? = null
}

/** The one chart open full screen, drawn by the app shell over every screen ([ChartZoomLayer]); Back closes it. */
class ChartZoom {
    var shown by androidx.compose.runtime.mutableStateOf<ZoomedChart?>(null)
        private set
    internal var owner: Any? = null
        private set

    internal fun show(
        who: Any, data: ChartData, showTarget: Boolean, onToggleTarget: (() -> Unit)?, targetLabel: String,
        hidden: androidx.compose.runtime.MutableState<List<String>>,
    ) {
        owner = who
        val cur = shown
        // only a change in what is drawn replaces it, so following the page's chart never loops
        if (cur == null || cur.data !== data || cur.showTarget != showTarget || cur.targetLabel != targetLabel || cur.hidden !== hidden) {
            shown = ZoomedChart(data, showTarget, targetLabel, hidden).also { it.onToggleTarget = onToggleTarget }
        } else cur.onToggleTarget = onToggleTarget
    }

    /** Something other than a response graph shown full screen (the Live spectrum); it draws itself and is given Close. */
    var custom by androidx.compose.runtime.mutableStateOf<(@Composable (onClose: () -> Unit) -> Unit)?>(null)
        private set
    private var customOwner: Any? = null

    /** A full-screen view is open, of either kind: the screen may then turn sideways whatever the auto-rotate setting. */
    val open: Boolean get() = shown != null || custom != null

    fun showCustom(who: Any, content: @Composable (onClose: () -> Unit) -> Unit) {
        customOwner = who
        custom = content
    }

    /** Closes the custom view if [who] opened it (a screen going away takes its own view with it). */
    fun closeCustom(who: Any) { if (customOwner === who) { customOwner = null; custom = null } }

    fun close() { owner = null; shown = null; customOwner = null; custom = null }
}

val LocalChartZoom = androidx.compose.runtime.staticCompositionLocalOf<ChartZoom?> { null }

/** The full-screen chart over the whole app: it rises in as a sheet, in the deck's colours; Close or Back drops it. */
@Composable
fun ChartZoomLayer(zoom: ChartZoom) {
    val z = zoom.shown
    val last = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<ZoomedChart?>(null) }
    if (z != null) last.value = z
    val custom = zoom.custom
    val lastCustom = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<(@Composable (() -> Unit) -> Unit)?>(null) }
    if (custom != null) lastCustom.value = custom
    // which kind was shown last, so the sheet keeps its content while it drops away
    val wasCustom = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    if (custom != null) wasCustom.value = true else if (z != null) wasCustom.value = false
    // composed only while open, so it is the newest Back handler and closes the chart before anything else
    if (z != null || custom != null) androidx.activity.compose.BackHandler { zoom.close() }
    androidx.compose.animation.AnimatedVisibility(z != null || custom != null, enter = sheetIn(), exit = sheetOut()) {
        if (wasCustom.value) {
            lastCustom.value?.let { content ->
                DeckColors {
                    androidx.compose.material3.Surface(Modifier.fillMaxSize(), color = At.c.panel, contentColor = At.c.text) {
                        Box(Modifier.statusBarsPadding().navigationBarsPadding()) { content { zoom.close() } }
                    }
                }
            }
        } else last.value?.let { shown ->
            DeckColors {
                androidx.compose.material3.Surface(Modifier.fillMaxSize(), color = At.c.panel, contentColor = At.c.text) {
                    Box(Modifier.statusBarsPadding().navigationBarsPadding()) {
                        val toggleTarget: (() -> Unit)? = if (shown.onToggleTarget != null) ({ shown.onToggleTarget?.invoke(); Unit }) else null
                        ZoomedChartView(shown.data, shown.showTarget, toggleTarget, shown.targetLabel, shown.hidden) { zoom.close() }
                    }
                }
            }
        }
    }
}

/** The full-screen graph: its title and Close, the chart filling the room, the key and one line on reading it. */
@Composable
private fun ZoomedChartView(
    data: ChartData, showTarget: Boolean, onToggleTarget: (() -> Unit)?, targetLabel: String,
    hiddenState: androidx.compose.runtime.MutableState<List<String>>, onClose: () -> Unit,
) {
    val hidden = hiddenState.value
    val toggle: (String) -> Unit = { k -> hiddenState.value = if (k in hiddenState.value) hiddenState.value - k else hiddenState.value + k }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Response", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
            TextAction("Close", onClose, icon = Ic.CLOSE)
        }
        ChartCanvas(data, Modifier.fillMaxWidth().weight(1f), showTarget, hidden)
        Legend(data, showTarget, onToggleTarget, hidden, toggle, targetLabel)
        Muted("Touch the chart to read each curve. Tap a name to hide or show it.")
    }
}

/** The chart itself, drawn still. */
@Composable
private fun ChartCanvas(data: ChartData, modifier: Modifier, showTarget: Boolean, hidden: List<String>) {
    val tm = rememberTextMeasurer()
    val c = At.c
    val label = TextStyle(fontFamily = AtFigures, fontSize = 11.sp, color = c.muted, fontFeatureSettings = "tnum")
    val runCols = data.curves.map { runColor(it.colorIdx) }
    val visible = buildList {
        data.curves.forEach { if ("run:" + it.label !in hidden) add(it.values) }
        if (showTarget && "target" !in hidden) add(data.target)
        data.predicted?.let { if ("predicted" !in hidden) add(it) }
        data.eq?.let { if ("eq" !in hidden) add(it) }
    }
    val (yMin, yMax) = chartRange(visible, data.freqs)
    // the cursor: touch or drag across the chart to read each shown curve at that frequency; a tap on it again clears it
    var cursor by androidx.compose.runtime.remember(data.freqs.size, data.curves.size) { androidx.compose.runtime.mutableStateOf<Float?>(null) }
    val bubble = TextStyle(fontFamily = AtFigures, fontSize = 12.sp, color = c.text, fontFeatureSettings = "tnum")
    Canvas(
        modifier
            .pointerInput(data) { detectTapGestures { p -> cursor = if (cursor != null && kotlin.math.abs(cursor!! - p.x) < 24.dp.toPx()) null else p.x } }
            .pointerInput(data) { detectHorizontalDragGestures(onDragStart = { p -> cursor = p.x }) { change, _ -> cursor = change.position.x } },
    ) {
        val left = 30.dp.toPx()
        val bottom = size.height - 16.dp.toPx()
        val right = 16.dp.toPx()
        val w = size.width - left - right
        fun x(f: Double) = (left + w * (ln(f / 20.0) / ln(1000.0))).toFloat()
        fun y(db: Double) = (bottom * (1.0 - (db.coerceIn(yMin, yMax) - yMin) / (yMax - yMin))).toFloat()

        // chart paper: a fine grid at every dB step and every 1-2-3...9 of each decade, heavier rules at the decades,
        // every 10 dB and at the target (0 dB)
        val minor = c.line.copy(alpha = 0.55f)
        val major = c.line
        val hair = 1.dp.toPx() / 2f
        var db = yMin.toInt()
        while (db <= yMax.toInt()) {
            val yy = y(db.toDouble())
            drawLine(
                when { db == 0 -> c.muted; db % 10 == 0 -> major; else -> minor },
                Offset(left, yy), Offset(size.width - right, yy), if (db == 0) 1.5.dp.toPx() else if (db % 10 == 0) hair * 2 else hair,
            )
            if (db % 10 == 0) drawText(tm, "$db", Offset(2.dp.toPx(), yy - 7.dp.toPx()), label)
            db += 5
        }
        for (decade in listOf(10, 100, 1000, 10000)) for (kk in 1..9) {
            val f = decade * kk
            if (f < 20 || f > 20000) continue
            drawLine(if (kk == 1) major else minor, Offset(x(f.toDouble()), 0f), Offset(x(f.toDouble()), bottom), if (kk == 1) hair * 2 else hair)
        }
        drawLine(major, Offset(left, bottom), Offset(size.width - right, bottom), hair * 2)
        for (f in listOf(20, 50, 100, 200, 500, 1000, 2000, 5000, 10000, 20000)) {
            val t = if (f >= 1000) "${f / 1000}k" else "$f"
            drawText(tm, t, Offset(x(f.toDouble()) - 6.dp.toPx(), bottom + 2.dp.toPx()), label)
        }

        fun curve(values: DoubleArray, color: Color, width: Float, dashed: Boolean = false, dotted: Boolean = false) {
            val p = Path()
            for (i in values.indices) {
                val px = x(data.freqs[i]); val py = y(values[i])
                if (i == 0) p.moveTo(px, py) else p.lineTo(px, py)
            }
            drawPath(
                p, color,
                style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round, pathEffect = when {
                    dotted -> PathEffect.dashPathEffect(floatArrayOf(0.1f, 5.dp.toPx()))
                    dashed -> PathEffect.dashPathEffect(floatArrayOf(10f, 8f))
                    else -> null
                }),
            )
        }
        if (showTarget && "target" !in hidden) curve(data.target, c.target.copy(alpha = 0.4f), 1.dp.toPx())
        // the stock reading is the baseline, drawn dashed like the base run on a dyno sheet; the latest run is the heaviest
        data.curves.forEachIndexed { i, cv ->
            if ("run:" + cv.label !in hidden) {
                curve(cv.values, runCols[i], if (i == data.curves.lastIndex) 3.dp.toPx() else 2.dp.toPx(), dashed = cv.colorIdx == 0)
            }
        }
        data.predicted?.let { if ("predicted" !in hidden) curve(it, c.text, 2.5.dp.toPx(), dotted = true) }
        data.eq?.let { if ("eq" !in hidden) curve(it, c.signal, 2.5.dp.toPx()) }

        // the cursor's line, and a bubble with the frequency and each shown curve's level there, in its colour
        cursor?.let { cx0 ->
            val cx = cx0.coerceIn(left, size.width - right)
            val f = 20.0 * Math.pow(1000.0, ((cx - left) / w).toDouble())
            drawLine(c.text.copy(alpha = 0.7f), Offset(cx, 0f), Offset(cx, bottom), 1.5.dp.toPx())
            val rows = buildList {
                add(dev.autotune.cursorFreqText(f) to c.text)
                data.curves.forEachIndexed { i, cv -> if ("run:" + cv.label !in hidden) add("${cv.label}  %+.1f".format(dev.autotune.levelAt(data.freqs, cv.values, f)) to runCols[i]) }
                if (showTarget && "target" !in hidden) add("Target  %+.1f".format(dev.autotune.levelAt(data.freqs, data.target, f)) to c.muted)
                data.predicted?.let { if ("predicted" !in hidden) add("Predicted  %+.1f".format(dev.autotune.levelAt(data.freqs, it, f)) to c.text) }
                // the EQ curve (a suggestion on every reading) is never read out here (owner, 2026-11-13)
            }.map { (t, col) -> tm.measure(t, bubble.copy(color = col)) }
            val pad = 6.dp.toPx()
            val bw = rows.maxOf { it.size.width } + pad * 2
            val bh = rows.sumOf { it.size.height } + pad * 2
            // beside the line, on whichever side has room, and never past either edge of the chart
            val bx = dev.autotune.bubbleX(cx, bw, size.width - right, 8.dp.toPx())
            val by = 4.dp.toPx()
            drawRoundRect(c.panel.copy(alpha = 0.95f), Offset(bx, by), androidx.compose.ui.geometry.Size(bw, bh), androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()))
            drawRoundRect(c.line, Offset(bx, by), androidx.compose.ui.geometry.Size(bw, bh), androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()), style = Stroke(hair * 2))
            var yy = by + pad
            for (r in rows) { drawText(r, topLeft = Offset(bx + pad, yy)); yy += r.size.height }
        }
    }
}

@Composable
private fun Legend(data: ChartData, showTarget: Boolean, onToggleTarget: (() -> Unit)?, hidden: List<String>, toggle: (String) -> Unit, targetLabel: String) {
    val c = At.c
    FlowRow(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
        data.curves.forEach { cv ->
            Key(cv.label, runColor(cv.colorIdx), on = "run:" + cv.label !in hidden, dashed = cv.colorIdx == 0) { toggle("run:" + cv.label) }
        }
        // the target's visibility is a saved setting where the screen offers one
        val targetOn = showTarget && "target" !in hidden
        Key(targetLabel, c.target.copy(alpha = 0.55f), on = targetOn, thin = true) { if (onToggleTarget != null) onToggleTarget() else toggle("target") }
        if (data.predicted != null) Key("Predicted with EQ", c.text, on = "predicted" !in hidden, dotted = true) { toggle("predicted") }
        if (data.eq != null) Key(data.eqLabel, c.signal, on = "eq" !in hidden) { toggle("eq") }
    }
}

/** One entry of the key: a short stroke drawn the way its curve is drawn (solid or dashed), then the name. */
@Composable
private fun Key(text: String, color: Color, on: Boolean = true, dashed: Boolean = false, dotted: Boolean = false, thin: Boolean = false, onClick: (() -> Unit)? = null) {
    val muted = At.c.muted
    Row(
        (if (onClick != null) Modifier.heightIn(min = 40.dp).clickable(role = androidx.compose.ui.semantics.Role.Switch, onClickLabel = if (on) "Hide $text" else "Show $text", onClick = onClick) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.size(width = 20.dp, height = 10.dp)) {
            val yy = size.height / 2
            drawLine(
                if (on) color else muted.copy(alpha = 0.6f), Offset(0f, yy), Offset(size.width, yy), if (thin) 1.5.dp.toPx() else 3.dp.toPx(),
                cap = if (dotted) StrokeCap.Round else StrokeCap.Butt,
                pathEffect = when {
                    dotted -> PathEffect.dashPathEffect(floatArrayOf(0.1f, 5.dp.toPx()))
                    dashed -> PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))
                    else -> null
                },
            )
        }
        // a hidden curve's name is greyed with its stroke, never struck through (2.32)
        Text("  $text", style = MaterialTheme.typography.bodySmall, color = if (on) At.c.text else At.c.muted.copy(alpha = 0.7f))
    }
}
