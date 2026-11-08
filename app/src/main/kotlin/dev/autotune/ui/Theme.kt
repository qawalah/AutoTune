package dev.autotune.ui

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import dev.autotune.R
import dev.autotune.ThemeMode

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
    /** How the EQ sliders are drawn in this look. */
    val slider: SliderStyle = SliderStyle.ROUND,
    val shell: Color = panel,
    val onShell: Color = text,
    val onShellMuted: Color = muted,
    /** The wood cheeks down both edges of the screen (Braun SK 4); null draws none. */
    val teak: Color? = null,
    /** The aluminium of the Tune wheel in the key row. */
    val alu: Color = androidx.compose.ui.graphics.lerp(panelHigh, muted, 0.35f),
    /** The colours inside a deck, when they differ from the body's (Braun: a dark body with light decks). */
    val deck: AtColors? = null,
)

/** Slider looks: a round knob on a line, a mixing-desk fader cap, or a column of LEDs. */
enum class SliderStyle { ROUND, FADER, LED }

/** The app's looks, each with a dark and a light version (Light / Dark / System picks between them). */
enum class Look(val title: String, val detail: String) {
    DYNO("Dyno sheet", "Chart paper, shop green and marker orange, the default"),
    STUDIO("Studio", "Amber on graphite"),
    CONSOLE("Mixing console", "Charcoal desk, white fader caps, green signal LEDs"),
    HIFI("Hi-fi blue meters", "Black glass with blue meter glow and LED sliders"),
    VU("Analog VU", "Warm cream meter face with a red needle"),
    SYNTH("Synthwave", "Deep purple with magenta and cyan"),
    NAVY("Navy", "Deep navy with a teal accent, the DevStore look"),
    BRAUN("Braun", "Dark body, light outlined decks, teak cheeks and one red, after Dieter Rams. The default"),
    BRAUN_GRAPHITE("Braun graphite", "Greys throughout, with a dark grey accent"),
    BRAUN_BLACK("Braun black", "Black and aluminium with an orange accent, like the TP 1 and T 1000"),
    ;

    /** Wallpaper colours replace a look's own colours, so only the two plain looks offer them. */
    val allowsWallpaper: Boolean get() = this == DYNO || this == STUDIO

    companion object { fun of(name: String?) = entries.firstOrNull { it.name == name } ?: BRAUN }
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
    slider = SliderStyle.FADER,
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
    slider = SliderStyle.FADER,
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
    slider = SliderStyle.FADER,
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
    slider = SliderStyle.FADER,
    shell = Color(0xFF121211), onShell = Color(0xFFECEAE4), onShellMuted = Color(0xFFA3A19A),
    teak = Color(0xFF8E5A33), alu = Color(0xFF8F908E),
    deck = BraunDeckDark,
)

// Braun graphite: everything grey, the accent a dark grey (light) or a pale grey (dark); no wood.
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
    slider = SliderStyle.FADER,
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
    slider = SliderStyle.FADER,
    shell = Color(0xFF2B2B2B), onShell = Color(0xFFE8E8E6), onShellMuted = Color(0xFFA2A29E),
    alu = Color(0xFF7E7F7D),
)

// Braun black: black decks and aluminium, an orange accent; by day the aluminium body with black decks' greys.
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
    slider = SliderStyle.FADER,
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
    slider = SliderStyle.FADER,
    shell = Color(0xFF1E1E1E), onShell = Color(0xFFEDEDEA), onShellMuted = Color(0xFFA0A09C),
    alu = Color(0xFFA9AAA8),
)

// A dyno run sheet. By day, chart paper with a fine green grid; by night, graphite with the grid dimmed. Marker orange
// is the tune, plotter pens are the runs, the stock is grey ink (drawn dashed), shop green carries the navigation.
private val DynoLight = AtColors(
    dark = false,
    bg = Color(0xFFECEFE8), panel = Color(0xFFF9FAF6), panelHigh = Color(0xFFE3E8DF), line = Color(0xFFC6D2C3),
    text = Color(0xFF141B16), muted = Color(0xFF515E55),
    signal = Color(0xFFC23E0A), onSignal = Color(0xFFFFFFFF),
    good = Color(0xFF17784A), warn = Color(0xFF855600), bad = Color(0xFFB0123F),
    target = Color(0xFF141B16),
    runs = listOf(
        Color(0xFF4A524D), Color(0xFF1C5FC4), Color(0xFF7A3DC2), Color(0xFF0B8794),
        Color(0xFFB0198F), Color(0xFF8A5A2B), Color(0xFF2E3F9E), Color(0xFF657300),
    ),
    shell = Color(0xFF173A2B), onShell = Color(0xFFF1F6F0), onShellMuted = Color(0xFFA9C4B3),
)

private val DynoDark = AtColors(
    dark = true,
    bg = Color(0xFF0F1311), panel = Color(0xFF161C18), panelHigh = Color(0xFF1F2722), line = Color(0xFF2B3730),
    text = Color(0xFFE6ECE6), muted = Color(0xFF97A79C),
    signal = Color(0xFFFF8A4C), onSignal = Color(0xFF1C0B02),
    good = Color(0xFF4CD08E), warn = Color(0xFFE8C55A), bad = Color(0xFFFF6685),
    target = Color(0xFFE6ECE6),
    runs = listOf(
        Color(0xFFA3ADA6), Color(0xFF6EA8FF), Color(0xFFB892FF), Color(0xFF3FD0DC),
        Color(0xFFF07AD8), Color(0xFFD9A066), Color(0xFF9AA6FF), Color(0xFFC2D152),
    ),
    shell = Color(0xFF1B4532), onShell = Color(0xFFE9F3EC), onShellMuted = Color(0xFFA6C5B2),
)

private val DarkAt = AtColors(
    dark = true,
    bg = Color(0xFF0E1217), panel = Color(0xFF151B22), panelHigh = Color(0xFF1D252F), line = Color(0xFF2A3440),
    text = Color(0xFFE7EBF0), muted = Color(0xFF93A0B0),
    signal = Color(0xFFFFB020), onSignal = Color(0xFF1A1203),
    good = Color(0xFF3DDC97), warn = Color(0xFFE9D66B), bad = Color(0xFFFF6B6B),
    target = Color(0xFFE7EBF0),
    // far apart in hue: grey, cyan, fuchsia, coral, violet... none near amber (the EQ) or white (the prediction)
    runs = listOf(
        Color(0xFF9AA7B8), Color(0xFF22D3EE), Color(0xFFE879F9), Color(0xFFFF7A59),
        Color(0xFF8B8DFF), Color(0xFF5EEAD4), Color(0xFFFB7185), Color(0xFF7DD3FC),
    ),
)

private val LightAt = AtColors(
    dark = false,
    bg = Color(0xFFF1F3F6), panel = Color(0xFFFFFFFF), panelHigh = Color(0xFFE8ECF1), line = Color(0xFFD3DAE3),
    text = Color(0xFF10151B), muted = Color(0xFF556070),
    signal = Color(0xFFA85A00), onSignal = Color(0xFFFFFFFF),
    good = Color(0xFF0F8A5F), warn = Color(0xFF7A6200), bad = Color(0xFFC62828),
    target = Color(0xFF10151B),
    runs = listOf(
        Color(0xFF6B7A8C), Color(0xFF0A7EA4), Color(0xFFB02FC4), Color(0xFFD1431B),
        Color(0xFF4F51D9), Color(0xFF0F8F82), Color(0xFFC2255C), Color(0xFF2B6CB0),
    ),
)

private fun look(
    dark: Boolean, bg: Long, panel: Long, panelHigh: Long, line: Long, text: Long, muted: Long, signal: Long, onSignal: Long,
    runs: List<Long>, slider: SliderStyle,
): AtColors {
    val base = if (dark) DarkAt else LightAt
    return AtColors(
        dark, Color(bg), Color(panel), Color(panelHigh), Color(line), Color(text), Color(muted), Color(signal), Color(onSignal),
        base.good, base.warn, base.bad, Color(text), runs.map { Color(it) }, slider,
    )
}

/** The colours of [l] in its dark or light version. Run colours stay far from each look's accent. */
fun lookColors(l: Look, dark: Boolean): AtColors = when (l) {
    Look.BRAUN -> if (dark) BraunDark else BraunLight
    Look.BRAUN_GRAPHITE -> if (dark) GraphiteDark else GraphiteLight
    Look.BRAUN_BLACK -> if (dark) BlackDark else BlackLight
    Look.DYNO -> if (dark) DynoDark else DynoLight
    Look.STUDIO -> if (dark) DarkAt else LightAt
    Look.CONSOLE -> if (dark) look(
        true, 0xFF121214, 0xFF1B1B1F, 0xFF26262C, 0xFF35353D, 0xFFEDEDF0, 0xFF9C9CA6, 0xFF3EE07A, 0xFF04220F,
        listOf(0xFFA3A8B4, 0xFF4FC3F7, 0xFFF06292, 0xFFFFB74D, 0xFF9575CD, 0xFFE57373, 0xFFBA68C8, 0xFF90CAF9), SliderStyle.FADER,
    ) else look(
        false, 0xFFECEDEF, 0xFFFFFFFF, 0xFFE2E4E8, 0xFFCDD0D6, 0xFF141518, 0xFF5B5F68, 0xFF0B8A40, 0xFFFFFFFF,
        listOf(0xFF6E7380, 0xFF0277BD, 0xFFC2185B, 0xFFE65100, 0xFF5E35B1, 0xFFC62828, 0xFF8E24AA, 0xFF1565C0), SliderStyle.FADER,
    )
    Look.HIFI -> if (dark) look(
        true, 0xFF05080D, 0xFF0B111A, 0xFF121B28, 0xFF1D2B3C, 0xFFDDEBFF, 0xFF7F96B4, 0xFF3FA9FF, 0xFF021022,
        listOf(0xFF8B98AB, 0xFF5CF2C2, 0xFFFF7AB6, 0xFFFFC857, 0xFFB69CFF, 0xFFFF8A65, 0xFFE0E0E0, 0xFF81C784), SliderStyle.LED,
    ) else look(
        false, 0xFFEEF3F9, 0xFFFFFFFF, 0xFFE1E9F3, 0xFFC9D6E6, 0xFF0B1626, 0xFF4D6380, 0xFF0B63B5, 0xFFFFFFFF,
        listOf(0xFF66768C, 0xFF00897B, 0xFFC2185B, 0xFFB26A00, 0xFF6A4FC8, 0xFFD84315, 0xFF424242, 0xFF2E7D32), SliderStyle.LED,
    )
    Look.VU -> if (dark) look(
        true, 0xFF1A140D, 0xFF241B12, 0xFF30251A, 0xFF46372A, 0xFFF2E6CF, 0xFFB9A584, 0xFFFF7A45, 0xFF2A0E02,
        listOf(0xFFB0A28A, 0xFF7FD1C7, 0xFFD7A6FF, 0xFFE8D36A, 0xFF9DB8FF, 0xFF8BD17C, 0xFFFF9EB5, 0xFF6EC6FF), SliderStyle.FADER,
    ) else look(
        false, 0xFFF3EBD8, 0xFFFBF6EA, 0xFFEADFC6, 0xFFD6C7A6, 0xFF2A2118, 0xFF6E5E48, 0xFFC0391B, 0xFFFFF8EE,
        listOf(0xFF8A7B62, 0xFF1F7A73, 0xFF7B3FA0, 0xFF8A6D00, 0xFF3C5BA9, 0xFF3B7D2E, 0xFFAD1457, 0xFF0D6E9E), SliderStyle.FADER,
    )
    // the shared DevStore language: navy surfaces, one teal accent, the routes' blue and green among the runs
    Look.NAVY -> if (dark) look(
        true, 0xFF0B1220, 0xFF121A2B, 0xFF18233A, 0xFF25324D, 0xFFE8EEFC, 0xFF8A97B4, 0xFF2DD4BF, 0xFF04222A,
        listOf(0xFF8A97B4, 0xFF60A5FA, 0xFFA78BFA, 0xFFFBBF24, 0xFFFB7185, 0xFF34C759, 0xFFF0ABFC, 0xFF93C5FD), SliderStyle.ROUND,
    ) else look(
        false, 0xFFF3F5FA, 0xFFFFFFFF, 0xFFEEF1F7, 0xFFDBE1EC, 0xFF0F172A, 0xFF5B6578, 0xFF0D9488, 0xFFFFFFFF,
        listOf(0xFF5B6578, 0xFF2563EB, 0xFF7C3AED, 0xFFB45309, 0xFFE11D48, 0xFF15803D, 0xFFA21CAF, 0xFF1D4ED8), SliderStyle.ROUND,
    )
    Look.SYNTH -> if (dark) look(
        true, 0xFF120A1F, 0xFF1B1030, 0xFF26173F, 0xFF3A2660, 0xFFF3E8FF, 0xFFB49BD6, 0xFFFF3EA5, 0xFF22001A,
        listOf(0xFFA898C0, 0xFF2DE2E6, 0xFFFFD166, 0xFF7CFF8C, 0xFF9D8CFF, 0xFFFF9E64, 0xFF6EC1FF, 0xFFE0E0E0), SliderStyle.LED,
    ) else look(
        false, 0xFFF6F0FF, 0xFFFFFFFF, 0xFFECE3FA, 0xFFD8CBEE, 0xFF1D1030, 0xFF5E4C7A, 0xFFC2187A, 0xFFFFFFFF,
        listOf(0xFF76688C, 0xFF00838F, 0xFFB26A00, 0xFF2E7D32, 0xFF4F46C8, 0xFFD84315, 0xFF1565C0, 0xFF424242), SliderStyle.LED,
    )
}

val LocalAt = staticCompositionLocalOf { DynoDark }

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

/** The same colours with the accent ([signal] and the text on it) swapped for [a]. */
fun AtColors.withAccent(a: dev.autotune.Accent): AtColors {
    if (a == dev.autotune.Accent.LOOK) return this
    val sig = Color(if (dark) a.dark else a.light)
    val on = Color(if (dark) a.onDark else a.onLight)
    return AtColors(dark, bg, panel, panelHigh, line, text, muted, sig, on, good, warn, bad, target, runs, slider, shell, onShell, onShellMuted, teak, alu, deck?.withAccent(a))
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

@Composable
fun AutoTuneTheme(mode: ThemeMode, dynamic: Boolean, lookChoice: Look = Look.BRAUN, accent: dev.autotune.Accent = dev.autotune.Accent.LOOK, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val at = lookColors(lookChoice, dark).withAccent(accent)
    val ctx = LocalContext.current
    val scheme = if (dynamic && Build.VERSION.SDK_INT >= 31) {
        if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
    } else atScheme(at)
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
        MaterialTheme(colorScheme = scheme, typography = AtTypography, shapes = AtShapes, content = content)
    }
}
