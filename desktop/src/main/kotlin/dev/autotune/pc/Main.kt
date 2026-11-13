package dev.autotune.pc

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.isTraySupported
import androidx.compose.ui.window.rememberWindowState
import dev.autotune.pc.ui.KnobPainter
import dev.autotune.pc.ui.PcScreen
import dev.autotune.pc.ui.PcTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.io.RandomAccessFile
import kotlin.system.exitProcess

/**
 * AutoTune for Windows. One window and a tray icon: closing the window keeps the PC reachable from the phone (and the
 * EQ stays in Equalizer APO whether or not the app runs); Quit in the tray ends it.
 */
fun main() {
    if (!oneInstance()) exitProcess(0)
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val apo = WindowsApo()
    val pc = PcEndpoint(scope, PcStore(), apo, JavaSoundOut())
    val port = pc.start()
    apo.port = port
    val announcer = Announcer()
    // announced again, with the address shown, whenever the PC's network comes up or changes (2.31): a PC that started
    // before its Wi-Fi, or got a new address, is found again
    scope.launch(Dispatchers.IO) {
        var last: List<String>? = null
        while (true) {
            val now = Net.addresses()
            val ips = now.map { it.hostAddress }
            if (ips != last) {
                last = ips
                pc.setAddress(ips.firstOrNull())
                runCatching { announcer.start(pc.ui.value.pcName, port, now) }
            }
            delay(NETWORK_CHECK_MS)
        }
    }

    application {
        val u by pc.ui.collectAsState()
        var shown by remember { mutableStateOf(true) }
        val quit = {
            announcer.close()
            pc.close()
            exitApplication()
        }
        if (isTraySupported) Tray(
            icon = KnobPainter,
            tooltip = if (u.eqOn) "AutoTune · EQ on" else "AutoTune",
            onAction = { shown = true },
            menu = {
                Item("Open AutoTune", onClick = { shown = true })
                CheckboxItem("EQ on", checked = u.eqOn, enabled = u.hasEq, onCheckedChange = { pc.setEqOn(it) })
                Separator()
                Item("Quit", onClick = quit)
            },
        )
        Window(
            onCloseRequest = { if (isTraySupported) shown = false else quit() },
            visible = shown,
            title = "AutoTune",
            icon = KnobPainter,
            state = rememberWindowState(size = DpSize(1180.dp, 760.dp)),
        ) {
            window.minimumSize = java.awt.Dimension(900, 560)
            PcTheme { PcScreen(u, pc) }
        }
    }
}

/** How often the PC looks whether its addresses changed. */
private const val NETWORK_CHECK_MS = 15_000L

/** True for the first AutoTune on this PC; a second one would fight the first for the port. */
private fun oneInstance(): Boolean = runCatching {
    val f = File(PcStore.defaultFile().parentFile, "running.lock").apply { parentFile.mkdirs() }
    val lock = RandomAccessFile(f, "rw").channel.tryLock() ?: return false
    Runtime.getRuntime().addShutdownHook(Thread { runCatching { lock.release() } })
    true
}.getOrDefault(true)
