package dev.autotune.link

import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors
import kotlin.concurrent.thread

/**
 * The TV's or PC's end of the link. It listens on [LINK_PORT] (any free port when that one is taken) and talks to one
 * phone at a time. A phone must send this device's code in its first line. A wrong code is answered after a pause, so
 * the four digits cannot be tried quickly. A phone that pairs replaces the one before; one that does not, never does.
 */
class LinkServer(
    private val code: () -> String,
    /** The welcome for a phone that sent the right code; it gets the phone's name. */
    private val welcome: (phone: String) -> LinkMsg,
    /** Every later message from the paired phone, on the link's own thread. */
    private val onMessage: (LinkMsg) -> Unit,
    /** The paired phone's name once its welcome is sent, or null when it has gone. */
    private val onPhone: (String?) -> Unit = {},
    private val wrongCodePauseMs: Long = 1500,
    private val deniedText: String = "That is not the code on the screen.",
) {
    private class Conn(val socket: Socket, val out: PrintWriter)

    @Volatile private var server: ServerSocket? = null
    @Volatile private var current: Conn? = null

    // every line goes out on this one thread, in order: a caller never touches the network (Android stops an app that
    // does so from its main thread) and never waits on a phone that is slow to read
    private val writer = Executors.newSingleThreadExecutor { r -> Thread(r, "link-write").apply { isDaemon = true } }

    /** The port it listens on, or -1 before [start]. */
    val port: Int get() = server?.localPort ?: -1

    val paired: Boolean get() = current != null

    /** Opens the port and accepts phones on a background thread. Returns the port; throws when no port opens. */
    fun start(preferred: Int = LINK_PORT): Int {
        val ss = runCatching { ServerSocket(preferred) }.getOrElse { ServerSocket(0) }
        server = ss
        thread(name = "link-accept", isDaemon = true) {
            while (!ss.isClosed) {
                val s = runCatching { ss.accept() }.getOrNull() ?: break
                thread(name = "link-phone", isDaemon = true) { serve(s) }
            }
        }
        return ss.localPort
    }

    /**
     * Queues [m] for the paired phone and returns at once, from any thread. False when no phone is paired. A line that
     * cannot be written closes that phone's connection, which ends its pairing.
     */
    fun send(m: LinkMsg): Boolean {
        val c = current ?: return false
        val line = m.encode()
        return runCatching {
            writer.execute {
                if (current !== c) return@execute
                c.out.println(line)
                if (c.out.checkError()) runCatching { c.socket.close() }
            }
        }.isSuccess
    }

    fun close() {
        runCatching { server?.close() }
        current?.let { runCatching { it.socket.close() } }
        current = null
        writer.shutdown()
    }

    private fun serve(s: Socket) {
        val conn = Conn(s, PrintWriter(OutputStreamWriter(s.getOutputStream(), Charsets.UTF_8), true))
        var mine = false
        try {
            val r = BufferedReader(InputStreamReader(s.getInputStream(), Charsets.UTF_8))
            // the hello must come at once; a silent connection is not kept open
            s.soTimeout = 15_000
            val first = r.readLine()?.let { LinkMsg.parse(it) } ?: return
            if (first.type != Msg.HELLO || !Pairing.matches(first["code"], code())) {
                Thread.sleep(wrongCodePauseMs)
                conn.out.println(Msg.denied(deniedText).encode())
                return
            }
            s.soTimeout = 0
            val name = first["name"]?.takeIf { it.isNotBlank() } ?: "phone"
            val before = current
            current = conn
            mine = true
            before?.let { runCatching { it.socket.close() } }
            send(welcome(name))
            onPhone(name)
            while (true) {
                val m = LinkMsg.parse(r.readLine() ?: break) ?: continue
                if (m.type == Msg.BYE) break
                onMessage(m)
            }
        } catch (_: Exception) {
        } finally {
            runCatching { s.close() }
            if (mine && current === conn) {
                current = null
                onPhone(null)
            }
        }
    }
}
