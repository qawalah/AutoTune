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
 * as the baseline), thin faint line = target, solid in the accent colour =
 * the EQ, dotted text colour = predicted with the EQ. Tap a name in the legend to hide or show its curve; the corner
 * button opens the graph full screen. The range grows to fit the curves, so nothing is cut off at the edge.
 */
@Composable
fun ResponseChart(
    data: ChartData, modifier: Modifier = Modifier, height: androidx.compose.ui.unit.Dp = 220.dp,
    showTarget: Boolean = true, onToggleTarget: (() -> Unit)? = null, targetLabel: String = "Target",
) {
    // hidden curves, by key; kept while the same curves are shown
    var hidden by androidx.compose.runtime.saveable.rememberSaveable(data.curves.joinToString { it.label }) { androidx.compose.runtime.mutableStateOf(listOf<String>()) }
    var big by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val toggle: (String) -> Unit = { k -> hidden = if (k in hidden) hidden - k else hidden + k }
    Column(modifier) {
        Box {
            ChartCanvas(data, Modifier.fillMaxWidth().height(height), showTarget, hidden)
            Box(Modifier.align(Alignment.TopEnd)) { IconAction(Ic.EXPAND, "Show the graph full screen") { big = true } }
        }
        Legend(data, showTarget, onToggleTarget, hidden, toggle, targetLabel)
    }
    if (big) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { big = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        ) {
            androidx.compose.material3.Surface(Modifier.fillMaxSize(), color = At.c.bg, contentColor = At.c.text) {
                Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Response", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                        TextAction("Close", { big = false }, icon = Ic.CLOSE)
                    }
                    ChartCanvas(data, Modifier.fillMaxWidth().weight(1f), showTarget, hidden)
                    Legend(data, showTarget, onToggleTarget, hidden, toggle, targetLabel)
                    Muted("Tap a name to hide or show its curve. dB from the target, level-matched over 250 Hz-2.5 kHz.")
                }
            }
        }
    }
}

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
    Canvas(modifier) {
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
        for (decade in listOf(10, 100, 1000, 10000)) for (k in 1..9) {
            val f = decade * k
            if (f < 20 || f > 20000) continue
            drawLine(if (k == 1) major else minor, Offset(x(f.toDouble()), 0f), Offset(x(f.toDouble()), bottom), if (k == 1) hair * 2 else hair)
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
                curve(cv.values, runCols[i], if (i == data.curves.lastIndex) 3.dp.toPx() else 2.dp.toPx(), dashed = cv.colorIdx == 0 && data.curves.size > 1)
            }
        }
        data.predicted?.let { if ("predicted" !in hidden) curve(it, c.text, 2.5.dp.toPx(), dotted = true) }
        data.eq?.let { if ("eq" !in hidden) curve(it, c.signal, 2.5.dp.toPx()) }
    }
}

@Composable
private fun Legend(data: ChartData, showTarget: Boolean, onToggleTarget: (() -> Unit)?, hidden: List<String>, toggle: (String) -> Unit, targetLabel: String) {
    val c = At.c
    FlowRow(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
        data.curves.forEach { cv ->
            Key(cv.label, runColor(cv.colorIdx), on = "run:" + cv.label !in hidden, dashed = cv.colorIdx == 0 && data.curves.size > 1) { toggle("run:" + cv.label) }
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
        Text(
            "  $text", style = MaterialTheme.typography.bodySmall, color = if (on) At.c.text else At.c.muted,
            textDecoration = if (on) null else androidx.compose.ui.text.style.TextDecoration.LineThrough,
        )
    }
}
