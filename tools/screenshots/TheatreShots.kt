package dev.autotune.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import dev.autotune.ThemeMode
import dev.autotune.dsp.Layout
import dev.autotune.dsp.PlayMethod
import dev.autotune.dsp.Speaker
import dev.autotune.link.ShownRow
import dev.autotune.tv.OutputProbe
import dev.autotune.tv.TvScreen
import dev.autotune.tv.TvUi
import org.junit.Rule
import org.junit.Test

class TheatreShots {
    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig(screenWidth = 1920, screenHeight = 1080, density = Density.XHIGH, xdpi = 320, ydpi = 320, orientation = com.android.resources.ScreenOrientation.LANDSCAPE),
        theme = "android:Theme.Material.NoActionBar", showSystemUi = false, useDeviceResolution = true,
    )
    private val probe = OutputProbe(PlayMethod.DOLBY_51, "HDMI ARC", true, 2)

    @Test fun tvPairing() = paparazzi.snapshot { AutoTuneTheme(ThemeMode.DARK) { TvScreen(TvUi(code = "4719", tvName = "Living room", address = "192.168.1.20", probe = probe), {}, {}, {}) } }

    @Test fun tvPlaying() = paparazzi.snapshot {
        AutoTuneTheme(ThemeMode.DARK) { TvScreen(TvUi(code = "4719", tvName = "Living room", probe = probe, phone = "Galaxy S23 Ultra", playing = Speaker.SL, playT = 6.0), {}, {}, {}) }
    }

    @Test fun tvResult() = paparazzi.snapshot {
        val rows = listOf(
            ShownRow(Speaker.FL, "−1.0 dB", "in time", true), ShownRow(Speaker.C, "−3.0 dB", "−0.20 m", true), ShownRow(Speaker.FR, "level", "in time", true),
            ShownRow(Speaker.SR, "+2.0 dB", "−0.80 m", true), ShownRow(Speaker.SL, "—", "—", false), ShownRow(Speaker.SUB, "+2.5 dB", "+1.00 m", true),
        )
        AutoTuneTheme(ThemeMode.DARK) { TvScreen(TvUi(code = "4719", probe = probe, phone = "Galaxy S23 Ultra", rows = rows, headline = "No sound from surround left", eqOn = true), {}, {}, {}) }
    }

    @Test fun phoneMaps() {
        paparazzi.unsafeUpdateConfig(DeviceConfig.PIXEL_5.copy(screenHeight = 2400))
        paparazzi.snapshot {
            AutoTuneTheme(ThemeMode.LIGHT) {
                Column(Modifier.fillMaxSize().background(At.c.bg).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Panel(pad = 12.dp) { SpeakerMap(Layout.L51, Speaker.C, level = 0.8f); Text("centre · sweep") }
                    Panel(pad = 12.dp) {
                        SpeakerMap(Layout.L71, null, notes = mapOf(Speaker.FL to "−1.0 dB", Speaker.C to "−3.0 dB", Speaker.FR to "level", Speaker.SUB to "+2.5 dB", Speaker.SL to "+2.0 dB", Speaker.BL to "+1.5 dB"), silent = setOf(Speaker.BR))
                    }
                }
            }
        }
    }
}
