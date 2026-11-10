package dev.autotune.link

import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.Socket
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LinkServerTest {
    private val got = LinkedBlockingQueue<LinkMsg>()
    private val phones = LinkedBlockingQueue<String>()
    private val server = LinkServer(
        code = { "4821" },
        welcome = { phone -> Msg.welcome("Desk PC", "STEREO", false, "Speakers", Msg.KIND_PC).let { LinkMsg(it.type, it.args + ("to" to phone)) } },
        onMessage = { got.add(it) },
        onPhone = { phones.add(it ?: "-") },
        wrongCodePauseMs = 10,
    )
    private val port = server.start(0)

    @AfterTest
    fun stop() = server.close()

    private class Phone(port: Int) {
        val s = Socket("127.0.0.1", port).apply { soTimeout = 3000 }
        val w = PrintWriter(s.getOutputStream().bufferedWriter(), true)
        val r = BufferedReader(InputStreamReader(s.getInputStream()))
        fun send(m: LinkMsg) = w.println(m.encode())
        fun read(): LinkMsg? = r.readLine()?.let { LinkMsg.parse(it) }
    }

    @Test
    fun theRightCodeIsWelcomedAndMessagesFlowBothWays() {
        val p = Phone(port)
        p.send(Msg.hello("4821", "Galaxy S23 Ultra"))
        val w = p.read()!!
        assertEquals(Msg.WELCOME, w.type)
        assertEquals(Msg.KIND_PC, w["kind"])
        assertEquals("Galaxy S23 Ultra", w["to"])
        assertEquals("Galaxy S23 Ultra", phones.poll(2, TimeUnit.SECONDS))
        assertTrue(server.paired)

        p.send(Msg.eqOff())
        assertEquals(Msg.EQ_OFF, got.poll(2, TimeUnit.SECONDS)!!.type)
        assertTrue(server.send(Msg.eqState(true)))
        assertEquals("1", p.read()!!["on"])

        p.send(Msg.bye())
        assertEquals("-", phones.poll(2, TimeUnit.SECONDS))
        assertFalse(server.paired)
    }

    @Test
    fun aWrongCodeIsDeniedAndNothingElseIsHeard() {
        val p = Phone(port)
        p.send(Msg.hello("1234", "stranger"))
        assertEquals(Msg.DENIED, p.read()!!.type)
        assertNull(p.read())
        assertFalse(server.paired)
        // a message before the hello is a wrong code too
        val q = Phone(port)
        q.send(Msg.eqOff())
        assertEquals(Msg.DENIED, q.read()!!.type)
        assertNull(got.poll(200, TimeUnit.MILLISECONDS))
        assertFalse(server.send(Msg.eqState(false)))
    }

    @Test
    fun aPairedPhoneReplacesTheOneBeforeButAStrangerDoesNot() {
        val a = Phone(port)
        a.send(Msg.hello("4821", "first"))
        assertNotNull(a.read())
        val stranger = Phone(port)
        stranger.send(Msg.hello("0000", "stranger"))
        assertEquals(Msg.DENIED, stranger.read()!!.type)
        // the first phone is still the one listened to
        assertTrue(server.send(Msg.eqState(false)))
        assertEquals(Msg.EQ_STATE, a.read()!!.type)

        val b = Phone(port)
        b.send(Msg.hello("4821", "second"))
        assertEquals(Msg.WELCOME, b.read()!!.type)
        assertNull(runCatching { a.read() }.getOrNull())
        assertTrue(server.send(Msg.eqState(true)))
        assertEquals("1", b.read()!!["on"])
    }

    @Test
    fun sendingNeverWaitsOnTheNetwork() {
        // Android stops an app that touches the network from its main thread, so send() must only queue: here the
        // phone reads nothing, the socket's buffers fill, and every send still returns at once.
        val p = Phone(port)
        p.send(Msg.hello("4821", "slow phone"))
        assertNotNull(p.read())
        val big = Msg.show("L51", "x".repeat(60_000), emptyList())
        val sender = Thread { repeat(200) { server.send(big) } }.apply { isDaemon = true; start() }
        sender.join(2000)
        assertFalse(sender.isAlive, "send() waited on the network")
        // what was queued still arrives, in order
        repeat(3) { assertEquals(Msg.SHOW, p.read()!!.type) }
    }

    @Test
    fun aWelcomeWithoutAKindIsFromATv() {
        val old = LinkMsg.parse("welcome name=Living+room method=DOLBY_51 eq=0 out=HDMI+ARC v=1")!!
        assertEquals(Msg.KIND_TV, old["kind"] ?: Msg.KIND_TV)
        assertEquals(Msg.KIND_TV, Msg.welcome("TV", "STEREO", false, "TV")["kind"])
    }
}
