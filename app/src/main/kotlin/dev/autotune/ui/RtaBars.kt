package dev.autotune.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import kotlin.math.abs

/**
 * RTA (real-time analyzer) bars: one bar per 1/3 octave, dB relative to the target level.
 * Teal = within 2 dB of the target, yellow = within 4 dB, red = further. White tick = target.
 */
@Composable
fun RtaBars(
    bars: DoubleArray, target: DoubleArray,
    /** Other runs drawn as coloured caps over the bars. */
    ghosts: List<Pair<DoubleArray, Color>> = emptyList(),
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 200.dp,
    caption: Boolean = true,
) {
    val c = At.c
    val tm = rememberTextMeasurer()
    val label = TextStyle(fontFamily = PlexMono, fontSize = 9.sp, color = c.muted)
    val labelled = setOf(31.5, 63.0, 125.0, 250.0, 500.0, 1000.0, 2000.0, 4000.0, 8000.0, 16000.0)
    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().height(height)) {
            val left = 26.dp.toPx()
            val bottom = size.height - 14.dp.toPx()
            val w = size.width - left
            val n = bars.size
            val slot = w / n
            val barW = slot * 0.72f
            val yMin = -15.0
            val yMax = 15.0
            fun y(db: Double) = (bottom * (1.0 - (db.coerceIn(yMin, yMax) - yMin) / (yMax - yMin))).toFloat()

            for (db in listOf(-10, 0, 10)) {
                drawLine(
                    if (db == 0) c.muted.copy(alpha = 0.7f) else c.line,
                    Offset(left, y(db.toDouble())), Offset(size.width, y(db.toDouble())), 1f,
                )
                drawText(tm, "$db", Offset(2.dp.toPx(), y(db.toDouble()) - 6.dp.toPx()), label)
            }
            for (i in 0 until n) {
                val x0 = left + i * slot + (slot - barW) / 2f
                val dev = abs(bars[i] - target[i])
                val color = when {
                    dev <= 2.0 -> c.good
                    dev <= 4.0 -> c.warn
                    else -> c.bad
                }
                val top = y(bars[i])
                drawRect(color, Offset(x0, top), Size(barW, bottom - top))
                ghosts.forEach { (g, col) -> drawRect(col, Offset(x0, y(g[i]) - 1.dp.toPx()), Size(barW, 3.dp.toPx())) }
                drawLine(c.target, Offset(x0 - 1f, y(target[i])), Offset(x0 + barW + 1f, y(target[i])), 2.dp.toPx())
                val f = Rta.CENTERS[i]
                if (f in labelled) {
                    val t = if (f >= 1000) "${(f / 1000).toInt()}k" else "${f.toInt()}"
                    drawText(tm, t, Offset(x0 - 3.dp.toPx(), bottom + 2.dp.toPx()), label)
                }
            }
        }
        if (caption) {
            Text(
                "Teal is within 2 dB of the target tick, yellow within 4 dB, red further away." +
                    if (ghosts.isNotEmpty()) " Coloured caps are the other ticked runs." else "",
                style = MaterialTheme.typography.bodySmall, color = c.muted,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}
