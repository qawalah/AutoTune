package dev.autotune.pc

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.sp
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
            live = PcLive(-31f, FloatArray(96) { kotlin.math.sin(it * 0.55).toFloat() * (0.8f + 0.2f * kotlin.math.sin(it * 0.13).toFloat()) }, "front right · sweep", null, Long.MAX_VALUE / 2),
        ),
        "pc-5-notonoutput" to base.copy(
            phone = "samsung SM-S918B", output = "Headphones (Soundcore Life Q30)",
            apo = ApoStatus(ApoState.NOT_ON_OUTPUT, configDir = "C:\\Program Files\\EqualizerAPO\\config", withApo = listOf("Speakers (Realtek(R) Audio)")),
            method = dev.autotune.dsp.PlayMethod.PCM_51, layout = dev.autotune.dsp.Layout.L51, result = PcResult("Every speaker is in time", listOf(Triple("0.04", "m", "off by"), Triple("6/6", "", "heard")), emptyList(), "test the eq"),
        ),
        "pc-4-eq" to base.copy(
            phone = "samsung SM-S918B", apo = ApoStatus(ApoState.READY), chains = chains, eqOn = true, eqFrom = "samsung SM-S918B",
            eqWhen = 1794312000000L, headline = "Levels and distances match",
            rows = listOf(ShownRow(Speaker.FL, "level", "in time", true), ShownRow(Speaker.FR, "+1.0 dB", "in time", true)),
        ),
    )
    roomShots(out)
    for ((name, u) in shots) {
        ImageComposeScene(width = 2360, height = 1520, density = Density(2f)) { PcTheme { PcScreen(u, null) } }.use { scene ->
            val img = scene.render()
            File(out, "$name.png").writeBytes(img.encodeToData()!!.bytes)
        }
        println("wrote $name.png")
    }
}

/** The phone's and the TV's room drawing (Room3d), by day on a deck and at night, in 5.1 with surround left playing. */
private fun roomShots(out: File) {
    val notes = mapOf(
        Speaker.FL to "level", Speaker.FR to "+1.0 dB", Speaker.C to "−2.5 dB", Speaker.SUB to "+3.0 dB",
        Speaker.SL to "+1.5 dB", Speaker.SR to "in time",
    ).mapValues { (s, n) -> dev.autotune.ui.room.RoomSpeaker(s, n) }
    val day = dev.autotune.ui.room.RoomColors(
        floor = androidx.compose.ui.graphics.Color(0xFFE9E7E1), floorLine = androidx.compose.ui.graphics.Color(0xFFC9C7C0),
        ink = androidx.compose.ui.graphics.Color(0xFF232321), muted = androidx.compose.ui.graphics.Color(0xFF5E5C57),
        face = androidx.compose.ui.graphics.Color(0xFFF3F2EE), side = androidx.compose.ui.graphics.Color(0xFFE4E2DC),
        live = androidx.compose.ui.graphics.Color(0xFFBF3C18), onLive = androidx.compose.ui.graphics.Color.White,
        screen = androidx.compose.ui.graphics.Color(0xFF232321),
    )
    val night = dev.autotune.ui.room.RoomColors(
        floor = androidx.compose.ui.graphics.Color(0xFF1A1A19), floorLine = androidx.compose.ui.graphics.Color(0xFF45453F),
        ink = androidx.compose.ui.graphics.Color(0xFFECEAE4), muted = androidx.compose.ui.graphics.Color(0xFFA3A19A),
        face = androidx.compose.ui.graphics.Color(0xFF2E2E2B), side = androidx.compose.ui.graphics.Color(0xFF3A3A37),
        live = androidx.compose.ui.graphics.Color(0xFFF0663A), onLive = androidx.compose.ui.graphics.Color(0xFF1A0A03),
        screen = androidx.compose.ui.graphics.Color(0xFFD9D7D1),
    )
    for ((name, spec) in mapOf(
        "room-phone" to Triple(day, 720 to 412, false),
        "room-tv" to Triple(night, 1200 to 686, true),
    )) {
        val (colors, wh, big) = spec
        ImageComposeScene(width = wh.first, height = wh.second, density = Density(2f)) {
            val tm = androidx.compose.ui.text.rememberTextMeasurer()
            androidx.compose.foundation.Canvas(
                androidx.compose.ui.Modifier.fillMaxSize().background(if (big) androidx.compose.ui.graphics.Color(0xFF232321) else androidx.compose.ui.graphics.Color.White),
            ) {
                with(dev.autotune.ui.room.Room3d) {
                    drawRoom(
                        dev.autotune.dsp.Layout.L51, Speaker.SL, notes, colors, tm,
                        androidx.compose.ui.text.TextStyle(fontFamily = dev.autotune.pc.ui.Inter, fontSize = (if (big) 16 else 12).sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium),
                        androidx.compose.ui.text.TextStyle(fontFamily = dev.autotune.pc.ui.Inter, fontSize = (if (big) 14 else 11).sp),
                        phase = 0.35f, level = 0.7f,
                    )
                }
            }
        }.use { scene -> File(out, "$name.png").writeBytes(scene.render().encodeToData()!!.bytes) }
        println("wrote $name.png")
    }
}
