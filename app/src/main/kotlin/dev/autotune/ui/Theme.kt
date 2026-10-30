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
 * AutoTune's palette. Amber is what the user acts on (their EQ, the primary action). Cool colours are measurements,
 * grey-blue is the stock reference.
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
)

/** Slider looks: a round knob on a line, a mixing-desk fader cap, or a column of LEDs. */
enum class SliderStyle { ROUND, FADER, LED }

/** The app's looks, each with a dark and a light version (Light / Dark / System picks between them). */
enum class Look(val title: String, val detail: String) {
    STUDIO("Studio", "Amber on graphite, the original"),
    CONSOLE("Mixing console", "Charcoal desk, white fader caps, green signal LEDs"),
    HIFI("Hi-fi blue meters", "Black glass with blue meter glow and LED sliders"),
    VU("Analog VU", "Warm cream meter face with a red needle"),
    SYNTH("Synthwave", "Deep purple with magenta and cyan"),
    ;

    companion object { fun of(name: String?) = entries.firstOrNull { it.name == name } ?: STUDIO }
}

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
    Look.SYNTH -> if (dark) look(
        true, 0xFF120A1F, 0xFF1B1030, 0xFF26173F, 0xFF3A2660, 0xFFF3E8FF, 0xFFB49BD6, 0xFFFF3EA5, 0xFF22001A,
        listOf(0xFFA898C0, 0xFF2DE2E6, 0xFFFFD166, 0xFF7CFF8C, 0xFF9D8CFF, 0xFFFF9E64, 0xFF6EC1FF, 0xFFE0E0E0), SliderStyle.LED,
    ) else look(
        false, 0xFFF6F0FF, 0xFFFFFFFF, 0xFFECE3FA, 0xFFD8CBEE, 0xFF1D1030, 0xFF5E4C7A, 0xFFC2187A, 0xFFFFFFFF,
        listOf(0xFF76688C, 0xFF00838F, 0xFFB26A00, 0xFF2E7D32, 0xFF4F46C8, 0xFFD84315, 0xFF1565C0, 0xFF424242), SliderStyle.LED,
    )
}

val LocalAt = staticCompositionLocalOf { DarkAt }

object At {
    val c: AtColors @Composable get() = LocalAt.current
}

/** Colour of run [idx]: the same in every chart, bar view and list. Index 0 is the stock reference. */
@Composable
fun runColor(idx: Int): Color = LocalAt.current.runs[idx.coerceAtLeast(0) % LocalAt.current.runs.size]

val PlexSans = FontFamily(
    Font(R.font.plex_sans_regular, FontWeight.Normal),
    Font(R.font.plex_sans_medium, FontWeight.Medium),
    Font(R.font.plex_sans_semibold, FontWeight.SemiBold),
    Font(R.font.plex_sans_semibold, FontWeight.Bold),
)

/** Only for readings: frequencies, levels, scores. */
val PlexMono = FontFamily(
    Font(R.font.plex_mono_regular, FontWeight.Normal),
    Font(R.font.plex_mono_medium, FontWeight.Medium),
    Font(R.font.plex_mono_medium, FontWeight.SemiBold),
    Font(R.font.plex_mono_medium, FontWeight.Bold),
)

private fun t(size: Int, line: Int, weight: FontWeight, family: FontFamily = PlexSans, tracking: Double = 0.0) =
    TextStyle(fontFamily = family, fontSize = size.sp, lineHeight = line.sp, fontWeight = weight, letterSpacing = tracking.sp)

private val AtTypography = Typography(
    displayLarge = t(48, 52, FontWeight.Medium, PlexMono, -1.0),
    displayMedium = t(36, 40, FontWeight.Medium, PlexMono, -0.5),
    displaySmall = t(28, 34, FontWeight.Medium, PlexMono),
    headlineLarge = t(30, 36, FontWeight.SemiBold, tracking = -0.3),
    headlineMedium = t(24, 30, FontWeight.SemiBold, tracking = -0.2),
    headlineSmall = t(21, 28, FontWeight.SemiBold, tracking = -0.1),
    titleLarge = t(19, 26, FontWeight.SemiBold),
    titleMedium = t(16, 22, FontWeight.SemiBold),
    titleSmall = t(14, 20, FontWeight.Medium),
    bodyLarge = t(16, 24, FontWeight.Normal),
    bodyMedium = t(14, 21, FontWeight.Normal),
    bodySmall = t(13, 19, FontWeight.Normal),
    labelLarge = t(14, 20, FontWeight.Medium),
    labelMedium = t(13, 18, FontWeight.Medium),
    labelSmall = t(12, 16, FontWeight.Medium),
)

private val AtShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun AutoTuneTheme(mode: ThemeMode, dynamic: Boolean, lookChoice: Look = Look.STUDIO, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val at = lookColors(lookChoice, dark)
    val container = androidx.compose.ui.graphics.lerp(at.panel, at.signal, if (dark) 0.22f else 0.18f)
    val ctx = LocalContext.current
    val scheme = when {
        dynamic && Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> darkColorScheme(
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
    val view = LocalView.current
    SideEffect {
        (view.context as? Activity)?.window?.let {
            val c = WindowCompat.getInsetsController(it, view)
            c.isAppearanceLightStatusBars = !dark
            c.isAppearanceLightNavigationBars = !dark
        }
    }
    androidx.compose.runtime.CompositionLocalProvider(LocalAt provides at) {
        MaterialTheme(colorScheme = scheme, typography = AtTypography, shapes = AtShapes, content = content)
    }
}
