package dev.autotune.ui

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import dev.autotune.Palette
import dev.autotune.PaletteSlot
import dev.autotune.R
import dev.autotune.ThemeMode
import dev.autotune.mutedInk
import dev.autotune.onAccent
import dev.autotune.readableInk

/**
 * AutoTune's palette. [signal] is what the user acts on (their EQ, the primary action). [runs] are the measured runs,
 * index 0 the stock reference. [shell] is the navigation band and the header strip of a run sheet.
 */
@Immutable
class AtColors(
    val dark: Boolean,
    val bg: Color,
    val panel: Color,
    val panelHigh: Color,
    val line: Color,
    val text: Color,
    val muted: Color,
    val signal: Color,
    val onSignal: Color,
    val good: Color,
    val warn: Color,
    val bad: Color,
    val target: Color,
    val runs: List<Color>,
    val shell: Color = panel,
    val onShell: Color = text,
    val onShellMuted: Color = muted,
    /** The wood cheeks down both edges of the screen (Braun SK 4); null draws none. */
    val teak: Color? = null,
    /** The aluminium of the Tune wheel in the key row. */
    val alu: Color = androidx.compose.ui.graphics.lerp(panelHigh, muted, 0.35f),
    /** The colours inside a deck, when they differ from the body's (Braun: a dark body with light decks). */
    val deck: AtColors? = null,
    /** A deck's outline as a gradient, top to bottom (the Gradient edge look); null draws the plain [line]. */
    val edge: List<Color>? = null,
    /**
     * Soft plastic (the Concept look): plates and keys stand up with soft shading and large corners, fields are pressed
     * in, the round keys are domed. False draws the flat Braun decks with their hairline edge.
     */
    val soft: Boolean = false,
    /** The grey LCD the readings sit on ([LcdWindow]); null draws them on the deck. */
    val lcd: Color? = null,
    /** The figures on the [lcd]. */
    val lcdInk: Color = text,
) {
    fun copy(
        dark: Boolean = this.dark, bg: Color = this.bg, panel: Color = this.panel, panelHigh: Color = this.panelHigh,
        line: Color = this.line, text: Color = this.text, muted: Color = this.muted, signal: Color = this.signal,
        onSignal: Color = this.onSignal, target: Color = this.target, shell: Color = this.shell, onShell: Color = this.onShell,
        onShellMuted: Color = this.onShellMuted, deck: AtColors? = this.deck, edge: List<Color>? = this.edge,
    ) = AtColors(
        dark, bg, panel, panelHigh, line, text, muted, signal, onSignal, good, warn, bad, target, runs,
        shell, onShell, onShellMuted, teak, alu, deck, edge, soft, lcd, lcdInk,
    )
}

/**
 * The app's looks, all after Braun, each with a day and a night version (Light / Dark / System picks between them).
 * The enum names are what the setting stores, so they stay as they are; [title] is what the user sees. Append new
 * looks; a removed look's name is listed in [dev.autotune.RETIRED_LOOKS] and opens [DEFAULT] (Gradient edge), as does a
 * missing or unknown one. A look's [palette] is the palette it starts from (Gradient edge's own colours, none for the
 * others).
 */
enum class Look(val title: String, val detail: String) {
    BRAUN("Classic", "Dark body, light decks, teak cheeks and one red"),
    BRAUN_GRAPHITE("Graphite", "Greys throughout, with a dark grey accent"),
    BRAUN_BLACK("Black", "Black and aluminium with an orange accent"),
    WHITE("White", "A white body with pale grey decks, always light"),
    GRADIENT("Gradient edge", "Warm white, white decks, edges shaded dark to light. The default"),
    CONCEPT("Concept", "Off-white plastic, soft rounded plates, displays and orange keys"),
    ;

    /** The palette this look starts from; Reset palette returns to it ([dev.autotune.lookDefaultPalette]). */
    val palette: Palette get() = dev.autotune.lookDefaultPalette(name)

    companion object {
        /** The look the app opens with ([dev.autotune.DEFAULT_LOOK]). */
        val DEFAULT: Look get() = valueOf(dev.autotune.DEFAULT_LOOK)

        fun of(name: String?): Look = valueOf(dev.autotune.resolveLookName(name, entries.map { it.name }, dev.autotune.DEFAULT_LOOK))
    }
}

// Braun, after Dieter Rams: a dark graphite body with warm white decks, outlined, push keys, teak cheeks, and one red
// for the record label, the needle and the pilot light. Runs are inks and greys; the EQ is the red. The body and the
// decks have their own colours: everything inside a deck draws with [AtColors.deck].
private val BraunRunsLight = listOf(
    Color(0xFF85857F), Color(0xFF2F4F7F), Color(0xFF3D7F3A), Color(0xFF7A4A9C),
    Color(0xFF0E7C86), Color(0xFF8C5A2B), Color(0xFF4B5563), Color(0xFF9C2F6B),
)
private val BraunRunsDark = listOf(
    Color(0xFFA2A29C), Color(0xFF8EB4F0), Color(0xFF7CCB76), Color(0xFFC59BEB),
    Color(0xFF4FD1DB), Color(0xFFE0A86E), Color(0xFFB8C0CC), Color(0xFFF08CC0),
)

// the warm white deck of the day look
private val BraunDeckLight = AtColors(
    dark = false,
    bg = Color(0xFFE9E7E1), panel = Color(0xFFF3F2EE), panelHigh = Color(0xFFE4E2DC), line = Color(0xFFC9C7C0),
    text = Color(0xFF232321), muted = Color(0xFF5E5C57),
    signal = Color(0xFFBF3C18), onSignal = Color(0xFFFFFFFF),
    good = Color(0xFF3D7F3A), warn = Color(0xFF805C00), bad = Color(0xFFB3261E),
    target = Color(0xFF232321), runs = BraunRunsLight,
    shell = Color(0xFFF3F2EE), onShell = Color(0xFF232321), onShellMuted = Color(0xFF5E5C57),
    teak = Color(0xFFB07446), alu = Color(0xFFB9BAB8),
)

private val BraunLight = AtColors(
    dark = true,
    bg = Color(0xFF2B2B29), panel = Color(0xFF353533), panelHigh = Color(0xFF3A3A37), line = Color(0xFF4E4E49),
    text = Color(0xFFECEAE4), muted = Color(0xFFA3A19A),
    signal = Color(0xFFF0663A), onSignal = Color(0xFF1A0A03),
    good = Color(0xFF6CB866), warn = Color(0xFFE9C14A), bad = Color(0xFFFF6B5E),
    target = Color(0xFFECEAE4), runs = BraunRunsDark,
    shell = Color(0xFF2B2B29), onShell = Color(0xFFECEAE4), onShellMuted = Color(0xFFA3A19A),
    teak = Color(0xFFB07446), alu = Color(0xFFB9BAB8),
    deck = BraunDeckLight,
)

// at night the decks stay dark, so nothing glares in a car: a near-black body, graphite decks with a lighter edge
private val BraunDeckDark = AtColors(
    dark = true,
    bg = Color(0xFF1C1C1B), panel = Color(0xFF232321), panelHigh = Color(0xFF2E2E2B), line = Color(0xFF45453F),
    text = Color(0xFFECEAE4), muted = Color(0xFFA3A19A),
    signal = Color(0xFFF0663A), onSignal = Color(0xFF1A0A03),
    good = Color(0xFF6CB866), warn = Color(0xFFE9C14A), bad = Color(0xFFFF6B5E),
    target = Color(0xFFECEAE4), runs = BraunRunsDark,
    shell = Color(0xFF232321), onShell = Color(0xFFECEAE4), onShellMuted = Color(0xFFA3A19A),
    teak = Color(0xFF8E5A33), alu = Color(0xFF8F908E),
)

private val BraunDark = AtColors(
    dark = true,
    bg = Color(0xFF121211), panel = Color(0xFF1C1C1B), panelHigh = Color(0xFF262624), line = Color(0xFF3A3A36),
    text = Color(0xFFECEAE4), muted = Color(0xFFA3A19A),
    signal = Color(0xFFF0663A), onSignal = Color(0xFF1A0A03),
    good = Color(0xFF6CB866), warn = Color(0xFFE9C14A), bad = Color(0xFFFF6B5E),
    target = Color(0xFFECEAE4), runs = BraunRunsDark,
    shell = Color(0xFF121211), onShell = Color(0xFFECEAE4), onShellMuted = Color(0xFFA3A19A),
    teak = Color(0xFF8E5A33), alu = Color(0xFF8F908E),
    deck = BraunDeckDark,
)

// Graphite: everything grey, the accent a dark grey (light) or a pale grey (dark); no wood.
private val GraphiteLight = AtColors(
    dark = false,
    bg = Color(0xFFD5D5D2), panel = Color(0xFFC6C6C2), panelHigh = Color(0xFFE3E3E0), line = Color(0xFFA9A9A4),
    text = Color(0xFF1B1B1B), muted = Color(0xFF4D4D4A),
    signal = Color(0xFF353534), onSignal = Color(0xFFF2F2EF),
    good = Color(0xFF2F6F2C), warn = Color(0xFF6E4F00), bad = Color(0xFFA3221B),
    target = Color(0xFF1B1B1B),
    runs = listOf(
        Color(0xFF6E6E6A), Color(0xFF2A4A78), Color(0xFF2F6F2C), Color(0xFF6A3C8C),
        Color(0xFF0B6A73), Color(0xFF7A4C22), Color(0xFF3F4652), Color(0xFF8A2A5E),
    ),
    shell = Color(0xFFC6C6C2), onShell = Color(0xFF1B1B1B), onShellMuted = Color(0xFF4D4D4A),
    alu = Color(0xFFA9AAA8),
)

private val GraphiteDark = AtColors(
    dark = true,
    bg = Color(0xFF1F1F1F), panel = Color(0xFF2B2B2B), panelHigh = Color(0xFF363636), line = Color(0xFF454545),
    text = Color(0xFFE8E8E6), muted = Color(0xFFA2A29E),
    signal = Color(0xFFD2D2CF), onSignal = Color(0xFF1B1B1B),
    good = Color(0xFF7CC376), warn = Color(0xFFE9C14A), bad = Color(0xFFFF7A6E),
    target = Color(0xFFE8E8E6),
    runs = listOf(
        Color(0xFF8E8E8A), Color(0xFF8EB4F0), Color(0xFF7CCB76), Color(0xFFC59BEB),
        Color(0xFF4FD1DB), Color(0xFFE0A86E), Color(0xFFB8C0CC), Color(0xFFF08CC0),
    ),
    shell = Color(0xFF2B2B2B), onShell = Color(0xFFE8E8E6), onShellMuted = Color(0xFFA2A29E),
    alu = Color(0xFF7E7F7D),
)

// Black: black decks and aluminium, an orange accent; by day the aluminium body with black decks' greys.
private val BlackLight = AtColors(
    dark = false,
    bg = Color(0xFFDADAD7), panel = Color(0xFFCACAC6), panelHigh = Color(0xFFE8E8E5), line = Color(0xFFAEAEA9),
    text = Color(0xFF151515), muted = Color(0xFF4F4F4C),
    signal = Color(0xFFC2410C), onSignal = Color(0xFFFFFFFF),
    good = Color(0xFF2F6F2C), warn = Color(0xFF6E4F00), bad = Color(0xFFA3221B),
    target = Color(0xFF151515),
    runs = listOf(
        Color(0xFF6E6E6A), Color(0xFF2A4A78), Color(0xFF2F6F2C), Color(0xFF6A3C8C),
        Color(0xFF0B6A73), Color(0xFF7A4C22), Color(0xFF3F4652), Color(0xFF8A2A5E),
    ),
    shell = Color(0xFF2A2A29), onShell = Color(0xFFEDEDEA), onShellMuted = Color(0xFFA8A8A3),
    alu = Color(0xFFC4C5C3),
)

private val BlackDark = AtColors(
    dark = true,
    bg = Color(0xFF121212), panel = Color(0xFF1E1E1E), panelHigh = Color(0xFF2A2A2A), line = Color(0xFF383838),
    text = Color(0xFFEDEDEA), muted = Color(0xFFA0A09C),
    signal = Color(0xFFFF8A3D), onSignal = Color(0xFF1F0B00),
    good = Color(0xFF7CC376), warn = Color(0xFFE9C14A), bad = Color(0xFFFF7A6E),
    target = Color(0xFFEDEDEA),
    runs = listOf(
        Color(0xFF8E8E8A), Color(0xFF8EB4F0), Color(0xFF7CCB76), Color(0xFFC59BEB),
        Color(0xFF4FD1DB), Color(0xFFE0A86E), Color(0xFFB8C0CC), Color(0xFFF08CC0),
    ),
    shell = Color(0xFF1E1E1E), onShell = Color(0xFFEDEDEA), onShellMuted = Color(0xFFA0A09C),
    alu = Color(0xFFA9AAA8),
)

// White: the SK 4's white body. Pure white, pale grey decks that still read as plates by their hairline, the teak
// cheeks and the one red. The same by day and by night.
private val WhiteBody = AtColors(
    dark = false,
    bg = Color(0xFFFFFFFF), panel = Color(0xFFF4F4F2), panelHigh = Color(0xFFE8E8E5), line = Color(0xFFC4C4BF),
    text = Color(0xFF1B1B1A), muted = Color(0xFF575753),
    signal = Color(0xFFBF3C18), onSignal = Color(0xFFFFFFFF),
    good = Color(0xFF3D7F3A), warn = Color(0xFF805C00), bad = Color(0xFFB3261E),
    target = Color(0xFF1B1B1A), runs = BraunRunsLight,
    shell = Color(0xFFFFFFFF), onShell = Color(0xFF1B1B1A), onShellMuted = Color(0xFF575753),
    teak = Color(0xFFB07446), alu = Color(0xFFB9BAB8),
)

// Gradient edge: Classic, with each deck's outline shaded from ink grey at the top to a light tone at the bottom.
private val EdgeDay = listOf(Color(0xFF5E5C57), Color(0xFFE4E2DC))
private val EdgeNight = listOf(Color(0xFF2E2E2B), Color(0xFFA3A19A))
private val GradientLight = BraunLight.copy(edge = EdgeDay, deck = BraunDeckLight.copy(edge = EdgeDay))
private val GradientDark = BraunDark.copy(edge = EdgeNight, deck = BraunDeckDark.copy(edge = EdgeNight))

// Concept: a family of small off-white devices (Braun's later concept objects). Soft plastic plates and keys with large
// corners and soft shading, fields pressed in, round orange keys and the readings on a grey LCD; the graphs stay on
// the plate (the owner found a black graph no good, 2026-11-13). No decks of another colour: plates and body are the same plastic, the body a shade darker. At night the
// plastic is graphite and the LCD dimmed, so nothing glares in a car.
private val ConceptLight = AtColors(
    dark = false,
    bg = Color(0xFFE8E7E3), panel = Color(0xFFF2F1EE), panelHigh = Color(0xFFDEDDD8), line = Color(0xFFCFCEC8),
    text = Color(0xFF252523), muted = Color(0xFF57564F),
    signal = Color(0xFFD9501A), onSignal = Color(0xFFFFFFFF),
    good = Color(0xFF3D7F3A), warn = Color(0xFF805C00), bad = Color(0xFFB3261E),
    target = Color(0xFF252523), runs = BraunRunsLight,
    shell = Color(0xFFEEEDEA), onShell = Color(0xFF252523), onShellMuted = Color(0xFF57564F),
    alu = Color(0xFFC9C8C3),
    soft = true, lcd = Color(0xFFC6C8C0), lcdInk = Color(0xFF20221E),
)

private val ConceptDark = AtColors(
    dark = true,
    bg = Color(0xFF1D1D1C), panel = Color(0xFF292927), panelHigh = Color(0xFF20201F), line = Color(0xFF3C3C39),
    text = Color(0xFFECEAE4), muted = Color(0xFFADABA4),
    signal = Color(0xFFFF6A2B), onSignal = Color(0xFF1A0A03),
    good = Color(0xFF6CB866), warn = Color(0xFFE9C14A), bad = Color(0xFFFF6B5E),
    target = Color(0xFFECEAE4), runs = BraunRunsDark,
    shell = Color(0xFF252523), onShell = Color(0xFFECEAE4), onShellMuted = Color(0xFFADABA4),
    alu = Color(0xFF55554F),
    soft = true, lcd = Color(0xFF262823), lcdInk = Color(0xFFDADDD0),
)

/** The colours of [l] in its dark or light version. Run colours stay far from each look's accent. */
fun lookColors(l: Look, dark: Boolean): AtColors = when (l) {
    Look.BRAUN -> if (dark) BraunDark else BraunLight
    Look.BRAUN_GRAPHITE -> if (dark) GraphiteDark else GraphiteLight
    Look.BRAUN_BLACK -> if (dark) BlackDark else BlackLight
    Look.WHITE -> WhiteBody
    Look.GRADIENT -> if (dark) GradientDark else GradientLight
    Look.CONCEPT -> if (dark) ConceptDark else ConceptLight
}

val LocalAt = staticCompositionLocalOf { themeColors(Look.DEFAULT, false, dev.autotune.Accent.LOOK, Look.DEFAULT.palette) }

object At {
    val c: AtColors @Composable get() = LocalAt.current
}

/** Colour of run [idx]: the same in every chart, bar view and list. Index 0 is the stock reference. */
@Composable
fun runColor(idx: Int): Color = LocalAt.current.runs[idx.coerceAtLeast(0) % LocalAt.current.runs.size]

/** Interface text: Inter, a neutral grotesk in the spirit of the Akzidenz type on Braun devices. */
val AtSans = FontFamily(
    Font(R.font.inter_light, FontWeight.Light),
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)

/** Readings and headings: the same family; readings are set large and light, like a Braun display. */
val AtFigures = AtSans

// tabular figures everywhere, so readings line up in columns and never jitter while they update
private fun t(size: Int, line: Int, weight: FontWeight, family: FontFamily = AtSans, tracking: Double = 0.0) =
    TextStyle(fontFamily = family, fontSize = size.sp, lineHeight = line.sp, fontWeight = weight, letterSpacing = tracking.sp, fontFeatureSettings = "tnum")

private val AtTypography = Typography(
    displayLarge = t(54, 60, FontWeight.Light, AtFigures, -1.2),
    displayMedium = t(42, 48, FontWeight.Light, AtFigures, -0.9),
    displaySmall = t(32, 38, FontWeight.Light, AtFigures, -0.6),
    headlineLarge = t(30, 36, FontWeight.Medium, AtFigures, -0.5),
    headlineMedium = t(26, 32, FontWeight.Medium, AtFigures, -0.4),
    headlineSmall = t(21, 28, FontWeight.Medium, AtFigures, -0.2),
    titleLarge = t(18, 24, FontWeight.Medium, tracking = -0.1),
    titleMedium = t(16, 22, FontWeight.Medium),
    titleSmall = t(14, 20, FontWeight.Medium),
    bodyLarge = t(16, 24, FontWeight.Normal),
    bodyMedium = t(14, 21, FontWeight.Normal),
    bodySmall = t(13, 19, FontWeight.Normal),
    labelLarge = t(14, 20, FontWeight.Medium),
    labelMedium = t(13, 18, FontWeight.Medium),
    labelSmall = t(11, 15, FontWeight.Medium, tracking = 0.6),
)

// device corners: small and even, like the edges of a Braun panel and its keys
private val AtShapes = Shapes(
    extraSmall = RoundedCornerShape(2.dp),
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(4.dp),
    large = RoundedCornerShape(6.dp),
    extraLarge = RoundedCornerShape(12.dp),
)

// the Concept look's moulded plastic: large, soft corners on keys, fields and plates
private val SoftShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

/** The corners for a set of colours: soft plastic for the Concept look, small and even for the others. */
fun atShapes(at: AtColors): Shapes = if (at.soft) SoftShapes else AtShapes

/** The same colours with the accent ([signal] and the text on it) swapped for [a]. */
fun AtColors.withAccent(a: dev.autotune.Accent): AtColors {
    if (a == dev.autotune.Accent.LOOK) return this
    val sig = Color(if (dark) a.dark else a.light)
    val on = Color(if (dark) a.onDark else a.onLight)
    return copy(signal = sig, onSignal = on, deck = deck?.withAccent(a))
}

private fun Color.argb(): Long = toArgb().toLong() and 0xFFFFFFFFL
private fun mix(a: Color, b: Color, t: Float) = androidx.compose.ui.graphics.lerp(a, b, t)

/** Which surfaces text sits on in a set of colours: the body only, the deck only, or both (a look without decks). */
private enum class Ground { BODY, DECK, BOTH }

/**
 * One set of colours with the user's [body] (the page), [plate] (the deck), [ink], [rule] and [accent] put in. Text
 * and labels are checked against the surfaces they sit on and fall back to an ink that reads ([readableInk]).
 */
private fun AtColors.recolour(ground: Ground, body: Color?, plate: Color?, ink: Color?, rule: Color?, accent: Color?): AtColors {
    val bg2 = body ?: bg
    val panel2 = plate ?: panel
    val probe = when (ground) {
        Ground.BODY -> listOf(bg2)
        Ground.DECK -> listOf(panel2)
        Ground.BOTH -> listOf(panel2, bg2)
    }
    val text2 = Color(readableInk(ink?.argb(), text.argb(), probe.map { it.argb() }))
    // fields and keys follow a new surface, a shade towards the ink
    val high2 = when {
        ground != Ground.BODY && plate != null -> mix(panel2, text2, 0.07f)
        ground == Ground.BODY && body != null -> mix(bg2, text2, 0.08f)
        else -> panelHigh
    }
    val grounds = (if (ground == Ground.BODY) listOf(bg2, high2) else listOf(panel2, high2)).map { it.argb() }
    val muted2 = if (text2 == text && body == null && plate == null) muted else Color(mutedInk(text2.argb(), grounds))
    val main = if (ground == Ground.BODY) bg2 else panel2
    val line2 = rule ?: if (body != null || plate != null) mix(main, text2, 0.22f) else line
    val sig2 = accent ?: signal
    val newShell = shell == bg && body != null
    return copy(
        dark = if (body != null || plate != null) main.luminance() < 0.4f else dark,
        bg = bg2, panel = panel2, panelHigh = high2, line = line2, text = text2, muted = muted2,
        signal = sig2, onSignal = if (accent != null) Color(onAccent(accent.argb())) else onSignal,
        target = if (target == text) text2 else target,
        shell = if (newShell) bg2 else shell,
        onShell = if (newShell || onShell == text) text2 else onShell,
        onShellMuted = if (newShell || onShellMuted == muted) muted2 else onShellMuted,
    )
}

/** The colours with the user's [p]alette put in: body, deck, ink, accent and hairline, each where it is set. */
fun AtColors.withPalette(p: Palette): AtColors {
    if (p.isDefault) return this
    fun col(s: PaletteSlot) = p[s]?.let { Color(it) }
    val body = col(PaletteSlot.BODY); val plate = col(PaletteSlot.DECK); val ink = col(PaletteSlot.INK)
    val rule = col(PaletteSlot.RULE); val accent = col(PaletteSlot.ACCENT)
    val d = deck ?: return recolour(Ground.BOTH, body, plate, ink, rule, accent)
    return recolour(Ground.BODY, body, null, ink, rule, accent).copy(deck = d.recolour(Ground.DECK, null, plate, ink, rule, accent))
}

/** The Material colours for a palette, so drop-downs, dialogs and menus match the body or the deck they sit on. */
fun atScheme(at: AtColors): androidx.compose.material3.ColorScheme {
    val container = androidx.compose.ui.graphics.lerp(at.panel, at.signal, if (at.dark) 0.22f else 0.18f)
    return when {
        at.dark -> darkColorScheme(
            primary = at.signal, onPrimary = at.onSignal,
            primaryContainer = container, onPrimaryContainer = at.signal,
            secondary = at.muted, onSecondary = at.bg,
            secondaryContainer = container, onSecondaryContainer = at.signal,
            tertiary = at.runs[1], background = at.bg, onBackground = at.text,
            surface = at.bg, onSurface = at.text, surfaceVariant = at.panelHigh, onSurfaceVariant = at.muted,
            surfaceContainer = at.panel, surfaceContainerHigh = at.panelHigh, surfaceContainerHighest = at.panelHigh,
            surfaceContainerLow = at.panel, surfaceContainerLowest = at.bg,
            outline = at.muted, outlineVariant = at.line, error = at.bad, onError = at.bg,
            inverseSurface = at.text, inverseOnSurface = at.bg,
        )
        else -> lightColorScheme(
            primary = at.signal, onPrimary = at.onSignal,
            primaryContainer = container, onPrimaryContainer = at.text,
            secondary = at.muted, onSecondary = at.panel,
            secondaryContainer = container, onSecondaryContainer = at.text,
            tertiary = at.runs[1], background = at.bg, onBackground = at.text,
            surface = at.bg, onSurface = at.text, surfaceVariant = at.panelHigh, onSurfaceVariant = at.muted,
            surfaceContainer = at.panel, surfaceContainerHigh = at.panelHigh, surfaceContainerHighest = at.panelHigh,
            surfaceContainerLow = at.panel, surfaceContainerLowest = at.panel,
            outline = at.muted, outlineVariant = at.line, error = at.bad, onError = at.panel,
            inverseSurface = at.text, inverseOnSurface = at.bg,
        )
    }
}

/**
 * Where a palette's ink is drawn, surface by surface ([dev.autotune.inkSurface]): the look's own surfaces where the
 * ink reads, else their light versions for a dark ink (a dark body turns light, night decks take the day decks) or
 * their dark versions for a light ink (day decks take the night decks), by day and by night. A surface the user set
 * in the palette stays as set. [body] and [deck] are the changes for the body and the decks (for a look without decks
 * [body] is the whole look and [deck] is OWN).
 */
class InkMove(val body: dev.autotune.InkSurface, val deck: dev.autotune.InkSurface) {
    /** The change that happened, if any: LIGHT, DARK, or OWN when every surface stayed the look's own. */
    val any: dev.autotune.InkSurface get() = if (body != dev.autotune.InkSurface.OWN) body else deck
}

fun inkMove(look: Look, dark: Boolean, p: Palette): InkMove {
    val own = dev.autotune.InkSurface.OWN
    val ink = p[PaletteSlot.INK] ?: return InkMove(own, own)
    val c = lookColors(look, dark)
    val day = lookColors(look, false)
    val night = lookColors(look, true)
    val body = p[PaletteSlot.BODY]
    val plate = p[PaletteSlot.DECK]
    val d = c.deck
    if (d == null) {
        // no decks: text sits on the plates and on the body together, so the whole look moves to its day or night version
        fun grounds(x: AtColors) = listOf(plate ?: x.panel.argb(), body ?: x.bg.argb())
        val move = dev.autotune.inkSurface(ink, grounds(c), grounds(day), grounds(night), fixed = body != null && plate != null)
        return InkMove(move, own)
    }
    val lightDeck = day.deck ?: day
    val darkDeck = night.deck ?: night
    // the body's light version is the day deck's colours (Classic has a dark body by day and by night)
    val bodyMove = dev.autotune.inkSurface(ink, listOf(body ?: c.bg.argb()), listOf(lightDeck.bg.argb()), listOf(night.bg.argb()), fixed = body != null)
    val deckMove = dev.autotune.inkSurface(ink, listOf(plate ?: d.panel.argb()), listOf(lightDeck.panel.argb()), listOf(darkDeck.panel.argb()), fixed = plate != null)
    return InkMove(bodyMove, deckMove)
}

/** The look's surfaces for [dark], with the light or dark versions the palette's ink needs ([inkMove]). */
private fun surfaces(look: Look, dark: Boolean, p: Palette): AtColors {
    val c = lookColors(look, dark)
    val m = inkMove(look, dark, p)
    if (m.body == dev.autotune.InkSurface.OWN && m.deck == dev.autotune.InkSurface.OWN) return c
    val day = lookColors(look, false)
    val night = lookColors(look, true)
    val d = c.deck ?: return if (m.body == dev.autotune.InkSurface.LIGHT) day else night
    val lightDeck = day.deck ?: day
    val darkDeck = night.deck ?: night
    val deck = when (m.deck) {
        dev.autotune.InkSurface.LIGHT -> lightDeck
        dev.autotune.InkSurface.DARK -> darkDeck
        dev.autotune.InkSurface.OWN -> d
    }
    val body = when (m.body) {
        // a light body: the day deck's colours carry the page, the key row and the main key
        dev.autotune.InkSurface.LIGHT -> lightDeck
        dev.autotune.InkSurface.DARK -> night
        dev.autotune.InkSurface.OWN -> c
    }
    return body.copy(deck = deck)
}

/** The colours the app draws with: the look in its day or night version, then the accent, then the user's palette. */
fun themeColors(look: Look, dark: Boolean, accent: dev.autotune.Accent, palette: Palette): AtColors =
    surfaces(look, dark, palette).withAccent(accent).withPalette(palette)

/** The text colour on each surface text sits on: the body and the decks, or the plates of a look without decks. */
private fun textInks(c: AtColors): List<Long> = listOfNotNull(c.text.argb(), c.deck?.text?.argb())

/**
 * The status line under the palette's Ink field ([dev.autotune.inkNote]): whether the ink is used everywhere, only
 * where it reads or nowhere, whether it brought light or dark surfaces, and whether it looks the same as before.
 */
fun paletteInkNote(look: Look, dark: Boolean, p: Palette): String? {
    val ink = p[PaletteSlot.INK] ?: return null
    val shown = textInks(themeColors(look, dark, dev.autotune.Accent.LOOK, p))
    val before = textInks(themeColors(look, dark, dev.autotune.Accent.LOOK, p.with(PaletteSlot.INK, null)))
    return dev.autotune.inkNote(ink, shown, before, inkMove(look, dark, p).any)
}

/** Whether the app draws its night version for [mode]. */
@Composable
fun isNight(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

/**
 * The Tune dial's face and dots for a set of colours: always a light face (the deck's when it is light, else a light
 * grey) with dark dots, by day and by night, in every look and palette.
 */
fun dialColours(c: AtColors): Pair<Color, Color> {
    val face = dev.autotune.dialFace(listOfNotNull(c.deck?.panel, c.panel, c.bg).map { it.argb() }, c.dark && (c.deck ?: c).dark)
    val dots = dev.autotune.dialDots(face, listOfNotNull(c.deck?.text, c.text).map { it.argb() })
    return Color(face) to Color(dots)
}

@Composable
fun AutoTuneTheme(
    mode: ThemeMode, lookChoice: Look = Look.DEFAULT, accent: dev.autotune.Accent = dev.autotune.Accent.LOOK,
    palette: Palette = lookChoice.palette, content: @Composable () -> Unit,
) {
    val dark = isNight(mode)
    val at = themeColors(lookChoice, dark, accent, palette)
    val scheme = atScheme(at)
    val view = LocalView.current
    SideEffect {
        (view.context as? Activity)?.window?.let {
            val c = WindowCompat.getInsetsController(it, view)
            c.isAppearanceLightStatusBars = at.bg.luminance() > 0.5f
            // the navigation band sits behind the system bar, so its icons follow the band, not the page
            c.isAppearanceLightNavigationBars = at.shell.luminance() > 0.5f
        }
    }
    androidx.compose.runtime.CompositionLocalProvider(LocalAt provides at) {
        MaterialTheme(colorScheme = scheme, typography = AtTypography, shapes = atShapes(at), content = content)
    }
}
