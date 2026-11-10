package dev.autotune.pc

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import dev.autotune.dsp.EqChain
import dev.autotune.dsp.EqFilter
import dev.autotune.dsp.Speaker
import dev.autotune.link.ShownRow
import dev.autotune.pc.ui.PcScreen
import dev.autotune.pc.ui.PcTheme
import java.io.File

/** Renders the PC window in a few states to PNG files in args[0], without a screen (`./gradlew :desktop:shots`). */
fun main(args: Array<String>) {
    val out = File(args.firstOrNull() ?: "build/shots").apply { mkdirs() }
    val outputs = listOf("Speakers (Realtek(R) Audio)", "LG TV SSCR2 (NVIDIA High Definition Audio)")
    val base = PcUi(code = "4821", pcName = "DESK-PC", address = "192.168.1.20", port = 47810, outputs = outputs, output = outputs[0])
    val chains = mapOf(
        Speaker.FL to EqChain(listOf(EqFilter(48.0, -6.5, 4.0), EqFilter(180.0, -3.0, 2.0), EqFilter(3200.0, 2.0, 1.2)), -2.4),
        Speaker.FR to EqChain(listOf(EqFilter(62.0, -4.0, 3.0), EqFilter(900.0, -2.0, 2.5), EqFilter(8000.0, 1.5, 1.0)), -2.4),
    )
    val shots = mapOf(
        "pc-1-waiting" to base.copy(apo = ApoStatus(ApoState.MISSING)),
        "pc-2-setup" to base.copy(apo = ApoStatus(ApoState.NEEDS_SETUP, configDir = "C:\\Program Files\\EqualizerAPO\\config")),
        "pc-3-playing" to base.copy(
            phone = "samsung SM-S918B", apo = ApoStatus(ApoState.READY), playing = Speaker.FR, playT = 6.2,
        ),
        "pc-4-eq" to base.copy(
            phone = "samsung SM-S918B", apo = ApoStatus(ApoState.READY), chains = chains, eqOn = true, eqFrom = "samsung SM-S918B",
            eqWhen = 1794312000000L, headline = "Levels and distances match",
            rows = listOf(ShownRow(Speaker.FL, "level", "in time", true), ShownRow(Speaker.FR, "+1.0 dB", "in time", true)),
        ),
    )
    for ((name, u) in shots) {
        ImageComposeScene(width = 840, height = 1600, density = Density(2f)) { PcTheme { PcScreen(u, null) } }.use { scene ->
            val img = scene.render()
            File(out, "$name.png").writeBytes(img.encodeToData()!!.bytes)
        }
        println("wrote $name.png")
    }
}
