package dev.autotune.link

import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

/**
 * The TV's or PC's end of the link. It listens on [LINK_PORT] (any free port when that one is taken) and talks to one
 * phone at a time. A phone must send this device's code in its first line. A phone that pairs replaces the one before;
 * one that does not, never does.
 *
 * Guessing the four digits is slow (2.31): wrong codes are answered one at a time across every connection, each after
 * a pause that grows after [FREE_WRONG] wrong codes in a row ([wrongCodePause]); at most [maxWaiting] connections may wait
 * unpaired; an over-long line ends a connection. A phone that says it pings (2.31) and then stays silent for
 * [SILENT_PINGS] periods is dropped, so a phone that vanished no longer shows as connected.
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
    private val maxWaiting: Int = 8,
) {
    private class Conn(val socket: Socket, val out: PrintWriter)

    @Volatile private var server: ServerSocket? = null
    @Volatile private var current: Conn? = null

    // every line goes out on this one thread, in order: a caller never touches the network (Android stops an app that
    // does so from its main thread) and never waits on a phone that is slow to read
    private val writer = Executors.newSingleThreadExecutor { r -> Thread(r, "link-write").apply { isDaemon = true } }

    /** Connections not yet paired; wrong codes in a row (a pairing resets it); one wrong code answered at a time. */
    private val waiting = AtomicInteger(0)
    @Volatile private var wrongInARow = 0
    private val wrongGate = Any()

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
                // too many waiting to pair: this one is not even read
                if (waiting.incrementAndGet() > maxWaiting) {
                    waiting.decrementAndGet()
                    runCatching { s.close() }
                    continue
                }
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
        var unpaired = true
        try {
            val r = BufferedReader(InputStreamReader(s.getInputStream(), Charsets.UTF_8))
            // the hello must come at once; a silent connection is not kept open
            s.soTimeout = 15_000
            val first = readLine(r, MAX_HELLO)?.let { LinkMsg.parse(it) } ?: return
            if (first.type != Msg.HELLO || !Pairing.matches(first["code"], code())) {
                // one wrong code at a time, across every connection, each after a pause that grows
                synchronized(wrongGate) {
                    wrongInARow++
                    Thread.sleep(wrongCodePause(wrongCodePauseMs, wrongInARow))
                }
                conn.out.println(Msg.denied(deniedText).encode())
                return
            }
            wrongInARow = 0
            waiting.decrementAndGet()
            unpaired = false
            // a phone that pings (2.31) is dropped after a few silent periods; an older one is kept as before
            s.soTimeout = first["ping"]?.toIntOrNull()?.takeIf { it > 0 }?.let { silenceTimeoutMs(it) } ?: 0
            val name = first["name"]?.takeIf { it.isNotBlank() } ?: "phone"
            val before = current
            current = conn
            mine = true
            before?.let { runCatching { it.socket.close() } }
            send(withPong(welcome(name)))
            onPhone(name)
            while (true) {
                val m = LinkMsg.parse(readLine(r, MAX_LINE) ?: break) ?: continue
                if (m.type == Msg.BYE) break
                // answered here, so every TV and PC answers it the same way
                if (m.type == Msg.PING) { send(Msg.pong()); continue }
                onMessage(m)
            }
        } catch (_: Exception) {
        } finally {
            if (unpaired) waiting.decrementAndGet()
            runCatching { s.close() }
            if (mine && current === conn) {
                current = null
                onPhone(null)
            }
        }
    }

    /** The welcome with [Msg.CAP_PONG] added to its caps: a phone from 2.31 then takes silence as a dropped link. */
    private fun withPong(w: LinkMsg): LinkMsg {
        if (w.type != Msg.WELCOME) return w
        val caps = Msg.caps(w)
        if (Msg.CAP_PONG in caps) return w
        return LinkMsg(w.type, w.args + ("caps" to (caps.toList() + Msg.CAP_PONG).joinToString(",")))
    }

    companion object {
        /** The longest hello, and the longest later line, a phone may send. */
        const val MAX_HELLO = 1024
        const val MAX_LINE = 256 * 1024
        /** Wrong codes in a row before the pause starts to grow. */
        const val FREE_WRONG = 3
        const val MAX_PAUSE_MS = 60_000L

        /** The pause before the [wrongInARow]th wrong code in a row is answered: [base], doubling after [FREE_WRONG], at most a minute. */
        fun wrongCodePause(base: Long, wrongInARow: Int): Long {
            val extra = (wrongInARow - FREE_WRONG).coerceIn(0, 16)
            return (base shl extra).coerceAtMost(maxOf(base, MAX_PAUSE_MS))
        }

        /** One line of at most [max] characters, without its end; null at the end of the stream. Longer: an IOException. */
        fun readLine(r: BufferedReader, max: Int): String? {
            val b = StringBuilder()
            while (true) {
                val c = r.read()
                if (c < 0) return if (b.isEmpty()) null else b.toString()
                if (c == '\n'.code) return b.toString().removeSuffix("\r")
                if (b.length >= max) throw IOException("line too long")
                b.append(c.toChar())
            }
        }
    }
}
