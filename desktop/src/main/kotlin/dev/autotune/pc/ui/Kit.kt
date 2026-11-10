package dev.autotune.pc.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// The phone's Gradient edge look with its default palette (the owner's): body Warm white, decks White, ink Slate,
// accent Braun red, hairline Aluminium, each deck's edge shaded from ink grey to light. Red is only for what is live.
object Pc {
    val body = Color(0xFFF3F2EE)
    val deck = Color(0xFFFFFFFF)
    val key = Color(0xFFE4E2DC)
    val ink = Color(0xFF2E2E2B)
    val muted = Color(0xFF5E5C57)
    val line = Color(0xFFB9BAB8)
    val red = Color(0xFFBF3C18)
    val good = Color(0xFF3D7F3A)
    val warn = Color(0xFF805C00)
    val second = Color(0xFF2F4F7F)
    val edge = listOf(Color(0xFF5E5C57), Color(0xFFE4E2DC))
}

val Inter = FontFamily(
    Font("fonts/inter_light.ttf", FontWeight.Light),
    Font("fonts/inter_regular.ttf", FontWeight.Normal),
    Font("fonts/inter_medium.ttf", FontWeight.Medium),
    Font("fonts/inter_semibold.ttf", FontWeight.SemiBold),
)

// tabular figures everywhere, so readings line up and never jitter while they change
private fun t(size: Int, line: Int, weight: FontWeight, tracking: Double = 0.0) =
    TextStyle(fontFamily = Inter, fontSize = size.sp, lineHeight = line.sp, fontWeight = weight, letterSpacing = tracking.sp, fontFeatureSettings = "tnum")

private val Type = Typography(
    displayLarge = t(54, 60, FontWeight.Light, -1.2),
    displaySmall = t(32, 38, FontWeight.Light, -0.6),
    titleMedium = t(16, 22, FontWeight.Medium),
    titleSmall = t(14, 20, FontWeight.Medium),
    bodyLarge = t(16, 24, FontWeight.Normal),
    bodyMedium = t(14, 21, FontWeight.Normal),
    bodySmall = t(13, 19, FontWeight.Normal),
    labelLarge = t(14, 20, FontWeight.Medium),
    labelMedium = t(13, 18, FontWeight.Medium),
    labelSmall = t(11, 15, FontWeight.Medium, 0.6),
)

@Composable
fun PcTheme(content: @Composable () -> Unit) = MaterialTheme(
    colorScheme = lightColorScheme(
        primary = Pc.ink, onPrimary = Pc.deck, background = Pc.body, onBackground = Pc.ink, surface = Pc.deck, onSurface = Pc.ink,
        surfaceVariant = Pc.key, onSurfaceVariant = Pc.muted, outline = Pc.line, error = Pc.red,
    ),
    typography = Type,
    content = content,
)

private val DeckShape = RoundedCornerShape(8.dp)
private val KeyShape = RoundedCornerShape(3.dp)

/** A deck: a white plate with its shaded hairline edge, the label line at its top. Decks are never nested. */
@Composable
fun Deck(title: String, sub: String? = null, trailing: (@Composable () -> Unit)? = null, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(DeckShape).background(Pc.deck).border(1.dp, Brush.verticalGradient(Pc.edge), DeckShape)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 40.dp).padding(start = 16.dp, end = 8.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = Pc.ink, maxLines = 1)
                if (sub != null) Text(sub.lowercase(), style = MaterialTheme.typography.labelSmall, color = Pc.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            trailing?.invoke()
        }
        Column(Modifier.padding(horizontal = 16.dp).padding(top = 8.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { content() }
    }
}

/** A reading: the figure large and light, its name small and lowercase under it. */
@Composable
fun ReadingCell(value: String, name: String, big: Boolean = true, tint: Color = Pc.ink) {
    Column {
        Text(value, style = if (big) MaterialTheme.typography.displayLarge else MaterialTheme.typography.displaySmall, color = tint, maxLines = 1)
        Text(name.lowercase(), style = MaterialTheme.typography.labelSmall, color = Pc.muted, maxLines = 1)
    }
}

/** One line of fact or status under a control: small, lowercase, at most one line. */
@Composable
fun StatusLine(text: String, tint: Color = Pc.muted, dot: Color? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (dot != null) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
            Spacer(Modifier.width(8.dp))
        }
        Text(text.lowercase(), style = MaterialTheme.typography.labelSmall, color = tint, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** The (i): the explanation lives here, never on the page. */
@Composable
fun InfoButton(title: String, text: String) {
    var open by remember { mutableStateOf(false) }
    Box(Modifier.size(32.dp).clip(CircleShape).clickable(role = Role.Button, onClickLabel = "About $title") { open = true }, contentAlignment = Alignment.Center) {
        PcIcon(Ic.INFO, Pc.muted, 16.dp)
    }
    if (open) AlertDialog(
        onDismissRequest = { open = false },
        title = { Text(title, style = MaterialTheme.typography.titleMedium) },
        text = { Text(text, Modifier.verticalScroll(rememberScrollState()), style = MaterialTheme.typography.bodyMedium) },
        confirmButton = { TextButton(onClick = { open = false }) { Text("Got it") } },
        containerColor = Pc.deck,
    )
}

/** Warnings: one line that opens the full list. */
@Composable
fun Alerts(items: List<String>, title: String = "Notes") {
    if (items.isEmpty()) return
    var open by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 40.dp).clip(KeyShape).background(Pc.key).border(1.dp, Pc.line, KeyShape)
            .clickable(role = Role.Button) { open = true }.padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PcIcon(Ic.ALERT, Pc.warn, 18.dp)
        Spacer(Modifier.width(10.dp))
        Text(items.first(), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = Pc.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (items.size > 1) Text("+${items.size - 1}", style = MaterialTheme.typography.labelSmall, color = Pc.muted)
    }
    if (open) AlertDialog(
        onDismissRequest = { open = false },
        title = { Text(title, style = MaterialTheme.typography.titleMedium) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { items.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) } } },
        confirmButton = { TextButton(onClick = { open = false }) { Text("Got it") } },
        containerColor = Pc.deck,
    )
}

/** A choice: always a drop-down, its label small above the value. */
@Composable
fun DropSelect(options: List<String>, selected: Int, onSelect: (Int) -> Unit, label: String, icon: Ic, enabled: Boolean = true, info: String? = null) {
    var open by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(KeyShape).background(Pc.key).border(1.dp, Pc.line, KeyShape)
                    .clickable(enabled = enabled && options.isNotEmpty(), role = Role.DropdownList) { open = true }.padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PcIcon(icon, Pc.muted, 20.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(label.lowercase(), style = MaterialTheme.typography.labelSmall, color = Pc.muted)
                    Text(options.getOrNull(selected) ?: "none found", style = MaterialTheme.typography.bodyMedium, color = Pc.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                PcIcon(Ic.DOWN, Pc.muted, 16.dp)
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = Pc.deck) {
                options.forEachIndexed { i, o ->
                    DropdownMenuItem(text = { Text(o, style = MaterialTheme.typography.bodyMedium, color = if (i == selected) Pc.red else Pc.ink) }, onClick = { open = false; onSelect(i) })
                }
            }
        }
        if (info != null) InfoButton(label, info)
    }
}

/** The latching push key with its pilot light: red while on. */
@Composable
fun LatchKey(on: Boolean, onChange: (Boolean) -> Unit, enabled: Boolean = true) {
    Box(
        Modifier.size(64.dp, 48.dp).clickable(enabled = enabled, role = Role.Switch) { onChange(!on) },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.offset(y = if (on) 2.dp else 0.dp).size(56.dp, 34.dp).clip(KeyShape)
                .background(if (on) androidx.compose.ui.graphics.lerp(Pc.key, Pc.line, 0.55f) else Pc.key).border(1.dp, Pc.line, KeyShape),
        ) {
            if (on) Box(Modifier.matchParentSize().background(Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.2f), 0.45f to Color.Transparent)))
            Box(Modifier.align(Alignment.CenterStart).padding(start = 11.dp).size(8.dp).clip(CircleShape).background(if (on && enabled) Pc.red else Pc.muted.copy(alpha = 0.4f)))
            Text(if (on) "on" else "off", Modifier.align(Alignment.CenterEnd).padding(end = 10.dp), style = MaterialTheme.typography.labelSmall, color = if (on) Pc.ink else Pc.muted)
        }
    }
}

/** An action, one per row, the full width; [main] is the recommended one, drawn dark. */
@Composable
fun ActionKey(text: String, icon: Ic, onClick: () -> Unit, main: Boolean = false, enabled: Boolean = true) {
    val bg = if (main) Pc.ink else Pc.key
    val fg = if (main) Pc.deck else Pc.ink
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(KeyShape).background(if (enabled) bg else Pc.key)
            .border(1.dp, if (main) Pc.ink else Pc.line, KeyShape).clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PcIcon(icon, if (enabled) fg else Pc.muted, 20.dp)
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, color = if (enabled) fg else Pc.muted, maxLines = 1)
    }
}

/** The line icons, drawn as the phone's are: plain geometry on a 24-unit grid with a 1.6-unit stroke. */
enum class Ic { INFO, ALERT, DOWN, SPEAKER, EQ, PHONE, LINK, DOWNLOAD, AGAIN, SHIELD, OPEN }

@Composable
fun PcIcon(kind: Ic, tint: Color, size: Dp = 20.dp) {
    Canvas(Modifier.size(size)) { drawIc(kind, tint) }
}

fun DrawScope.drawIc(kind: Ic, tint: Color) {
    val u = this.size.minDimension / 24f
    val sw = 1.6f * u
    val flat = Stroke(sw, cap = StrokeCap.Butt)
    fun p(x: Float, y: Float) = Offset(x * u, y * u)
    fun seg(x1: Float, y1: Float, x2: Float, y2: Float) = drawLine(tint, p(x1, y1), p(x2, y2), sw, StrokeCap.Butt)
    fun ring(cx: Float, cy: Float, r: Float) = drawCircle(tint, r * u, p(cx, cy), style = Stroke(sw))
    fun dot(cx: Float, cy: Float, r: Float) = drawCircle(tint, r * u, p(cx, cy))
    fun box(x: Float, y: Float, w: Float, h: Float, r: Float = 1.5f) = drawRoundRect(tint, p(x, y), Size(w * u, h * u), CornerRadius(r * u), style = flat)
    fun poly(vararg xy: Float, closed: Boolean = false) {
        val path = Path()
        var i = 0
        while (i < xy.size) { if (i == 0) path.moveTo(xy[0] * u, xy[1] * u) else path.lineTo(xy[i] * u, xy[i + 1] * u); i += 2 }
        if (closed) path.close()
        drawPath(path, tint, style = flat)
    }
    when (kind) {
        Ic.INFO -> { ring(12f, 12f, 9f); seg(12f, 10.5f, 12f, 17f); dot(12f, 7.6f, 1.2f) }
        Ic.ALERT -> { poly(12f, 3.5f, 21.5f, 20f, 2.5f, 20f, closed = true); seg(12f, 9.5f, 12f, 14.5f); dot(12f, 17.2f, 1.1f) }
        Ic.DOWN -> poly(6f, 9.5f, 12f, 15.5f, 18f, 9.5f)
        Ic.SPEAKER -> { box(5f, 2.5f, 14f, 19f); ring(12f, 15f, 3.8f); ring(12f, 7.6f, 1.7f) }
        Ic.EQ -> { seg(6f, 3f, 6f, 21f); seg(12f, 3f, 12f, 21f); seg(18f, 3f, 18f, 21f); drawRect(tint, p(4f, 13f), Size(4f * u, 3f * u)); drawRect(tint, p(10f, 6f), Size(4f * u, 3f * u)); drawRect(tint, p(16f, 10f), Size(4f * u, 3f * u)) }
        Ic.PHONE -> { box(7f, 2.5f, 10f, 19f, 2f); seg(10.5f, 18.5f, 13.5f, 18.5f) }
        Ic.LINK -> { ring(12f, 17f, 1.6f); drawArc(tint, -135f, 90f, false, p(6.5f, 11.5f), Size(11f * u, 11f * u), style = flat); drawArc(tint, -135f, 90f, false, p(2.5f, 7.5f), Size(19f * u, 19f * u), style = flat) }
        Ic.DOWNLOAD -> { seg(12f, 3f, 12f, 15f); poly(7f, 10f, 12f, 15f, 17f, 10f); seg(4f, 20.5f, 20f, 20.5f) }
        Ic.AGAIN -> { drawArc(tint, -60f, 300f, false, p(4.5f, 4.5f), Size(15f * u, 15f * u), style = flat); poly(18.5f, 2.5f, 18f, 6.5f, 14f, 6f) }
        Ic.SHIELD -> { poly(12f, 2.5f, 20f, 5.5f, 20f, 11f, 12f, 21.5f, 4f, 11f, 4f, 5.5f, closed = true); poly(8.5f, 12f, 11f, 14.5f, 15.5f, 9.5f) }
        Ic.OPEN -> { box(3.5f, 6.5f, 14f, 14f); poly(13f, 3.5f, 20.5f, 3.5f, 20.5f, 11f); seg(20.5f, 3.5f, 11f, 13f) }
    }
}

/** The Tune knob, the app's mark: a light face, a dark rim with ticks, and the red index dot. */
fun DrawScope.drawKnob() {
    val s = size.minDimension
    val c = Offset(s / 2, s / 2)
    drawCircle(Pc.ink, s * 0.48f, c)
    drawCircle(Pc.body, s * 0.40f, c)
    for (k in 0 until 11) {
        val a = Math.toRadians(-225.0 + k * 27.0)
        val r0 = s * 0.30f
        val r1 = s * 0.36f
        drawLine(
            Pc.muted, Offset(c.x + r0 * kotlin.math.cos(a).toFloat(), c.y + r0 * kotlin.math.sin(a).toFloat()),
            Offset(c.x + r1 * kotlin.math.cos(a).toFloat(), c.y + r1 * kotlin.math.sin(a).toFloat()), s * 0.025f,
        )
    }
    val a = Math.toRadians(-60.0)
    drawCircle(Pc.red, s * 0.06f, Offset(c.x + s * 0.2f * kotlin.math.cos(a).toFloat(), c.y + s * 0.2f * kotlin.math.sin(a).toFloat()))
}

/** The knob as a painter, for the window and the tray. */
object KnobPainter : Painter() {
    override val intrinsicSize = Size(256f, 256f)
    override fun DrawScope.onDraw() = drawKnob()
}
