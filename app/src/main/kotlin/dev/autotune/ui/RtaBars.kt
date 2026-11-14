package dev.autotune.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.autotune.dsp.Rta
import dev.autotune.rtaHalfRange
import kotlin.math.abs

/**
 * RTA (real-time analyzer) bars: one bar per 1/3 octave, dB relative to the target level.
 * Green = within 2 dB of the target, yellow = within 4 dB, red = further. White tick = target.
 *
 * The scale fits what is on screen: it is +-15 dB while the bars stay close to the target and widens (30, 45, 60) the
 * moment a bar, a held peak or the target would be cut off, then narrows again once they settle ([rtaHalfRange]).
 * With no [target] (the tone generator) the bars are in dB below the loudest one, on a fixed 0 to -60 dB scale.
 * [hold] draws the highest each bar has reached as a white cap; [marker] marks one bar (the tone's).
 */
@Composable
fun RtaBars(
    bars: DoubleArray, target: DoubleArray?,
    /** Other runs drawn as coloured caps over the bars. */
    ghosts: List<Pair<DoubleArray, Color>> = emptyList(),
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 200.dp,
    caption: Boolean = true,
    /** The highest value of each bar so far (Hold peaks). */
    hold: DoubleArray? = null,
    /** The bar to mark, by index into [Rta.CENTERS] (the tone's). */
    marker: Int? = null,
) {
    val c = At.c
    val tm = rememberTextMeasurer()
    val label = TextStyle(fontFamily = AtFigures, fontSize = 9.sp, color = c.muted, fontFeatureSettings = "tnum")
    val labelled = setOf(31.5, 63.0, 125.0, 250.0, 500.0, 1000.0, 2000.0, 4000.0, 8000.0, 16000.0)
    // the scale remembers where it was, so it narrows slowly instead of flickering with the music
    val scale = remember { doubleArrayOf(15.0) }
    if (target != null) {
        var reach = 0.0
        for (i in bars.indices) {
            if (bars[i].isFinite()) reach = maxOf(reach, abs(bars[i]))
            if (i < target.size) reach = maxOf(reach, abs(target[i]))
            if (hold != null && i < hold.size && hold[i].isFinite()) reach = maxOf(reach, abs(hold[i]))
        }
        scale[0] = rtaHalfRange(reach, scale[0])
    }
    val half = scale[0]
    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().height(height)) {
            val left = 26.dp.toPx()
            val bottom = size.height - 14.dp.toPx()
            val w = size.width - left
            val n = bars.size
            val slot = w / n
            val barW = slot * 0.72f
            val yMin = if (target != null) -half else -60.0
            val yMax = if (target != null) half else 0.0
            fun y(db: Double) = (bottom * (1.0 - (db.coerceIn(yMin, yMax) - yMin) / (yMax - yMin))).toFloat()

            val gridDb: List<Int> = if (target == null) listOf(0, -20, -40, -60)
            else {
                // rules at every 10 dB (every 20 on the wide scales), always including 0
                val step = if (half <= 30.0) 10 else 20
                val k = (half / step).toInt()
                (-k..k).map { it * step }
            }
            for (db in gridDb) {
                drawLine(
                    if (db == 0) c.muted.copy(alpha = 0.7f) else c.line,
                    Offset(left, y(db.toDouble())), Offset(size.width, y(db.toDouble())), 1f,
                )
                drawText(tm, "$db", Offset(2.dp.toPx(), y(db.toDouble()) - 6.dp.toPx()), label)
            }
            for (i in 0 until n) {
                val x0 = left + i * slot + (slot - barW) / 2f
                val v = if (bars[i].isFinite()) bars[i] else yMin
                val color = if (target == null) c.text.copy(alpha = 0.75f) else {
                    val dev = abs(v - target[i])
                    when {
                        dev <= 2.0 -> c.good
                        dev <= 4.0 -> c.warn
                        else -> c.bad
                    }
                }
                val top = y(v)
                drawRect(color, Offset(x0, top), Size(barW, bottom - top))
                ghosts.forEach { (g, col) -> drawRect(col, Offset(x0, y(g[i]) - 1.dp.toPx()), Size(barW, 3.dp.toPx())) }
                if (target != null) drawLine(c.target, Offset(x0 - 1f, y(target[i])), Offset(x0 + barW + 1f, y(target[i])), 2.dp.toPx())
                if (hold != null && i < hold.size && hold[i].isFinite()) {
                    drawRect(c.text, Offset(x0, y(hold[i]) - 1.dp.toPx()), Size(barW, 2.dp.toPx()))
                }
                if (marker == i) {
                    // a signal-coloured foot under the bar the tone sits in
                    drawRect(c.signal, Offset(x0 - 1.dp.toPx(), bottom), Size(barW + 2.dp.toPx(), 3.dp.toPx()))
                }
                val f = Rta.CENTERS[i]
                if (f in labelled) {
                    val t = if (f >= 1000) "${(f / 1000).toInt()}k" else "${f.toInt()}"
                    drawText(tm, t, Offset(x0 - 3.dp.toPx(), bottom + 2.dp.toPx()), label)
                }
            }
        }
        if (caption) {
            Text(
                "Green is within 2 dB of the target tick, yellow within 4 dB, red further away." +
                    if (ghosts.isNotEmpty()) " Coloured caps are the other ticked runs." else "",
                style = MaterialTheme.typography.bodySmall, color = c.muted,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}
