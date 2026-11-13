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
    fun liveAndResultSurviveTheLine() {
        val x = FloatArray(4800) { if (it < 2400) 0.5f else 0.001f }
        val w = Msg.waveText(x, 0, x.size)
        assertEquals(48, w.length)
        val m = LinkMsg.parse(Msg.live(-23.4f, w, "front left · sweep", 1.5).encode())!!
        val h = Msg.waveHeights(m["w"])
        assertTrue(h.first() > 0.8f && h.last() < 0.1f)
        assertEquals("1.5", m["ch"])
        val r = LinkMsg.parse(Msg.result("Close to the target", listOf(Triple("2.1", "dB", "from target")), listOf(1.0, -2.0), listOf(0.0, 0.0), listOf("a note"), "Test the EQ again").encode())!!
        assertEquals(listOf(Triple("2.1", "dB", "from target")), Msg.readings(r["readings"]))
        assertEquals(listOf(1.0, -2.0), Msg.numbers(r["resp"]))
        assertEquals("Test the EQ again", r["next"])
    }

    @Test
    fun aWelcomeWithoutAKindIsFromATv() {
        val old = LinkMsg.parse("welcome name=Living+room method=DOLBY_51 eq=0 out=HDMI+ARC v=1")!!
        assertEquals(Msg.KIND_TV, old["kind"] ?: Msg.KIND_TV)
        assertEquals(Msg.KIND_TV, Msg.welcome("TV", "STEREO", false, "TV")["kind"])
    }

    @Test
    fun aPingIsAnsweredAndTheWelcomeSaysSo() {
        val p = Phone(port)
        p.send(Msg.hello("4821", "new phone", pingSeconds = 10))
        val w = p.read()!!
        assertTrue(Msg.CAP_PONG in Msg.caps(w))
        // the tests it plays are still there
        assertTrue(Msg.caps(w).containsAll(listOf("noise", "distances", "eqtest")))
        p.send(Msg.ping())
        assertEquals(Msg.PONG, p.read()!!.type)
        // the ping is the link's own business, not the TV's or PC's
        assertNull(got.poll(200, TimeUnit.MILLISECONDS))
    }

    @Test
    fun aPhoneThatPingsAndFallsSilentIsDropped() {
        val p = Phone(port)
        p.send(Msg.hello("4821", "vanishing phone", pingSeconds = 1))
        assertNotNull(p.read())
        assertEquals("vanishing phone", phones.poll(2, TimeUnit.SECONDS))
        // nothing for three periods (3 s): it is gone
        assertEquals("-", phones.poll(6, TimeUnit.SECONDS))
        assertFalse(server.paired)
    }

    @Test
    fun aPhoneThatSaysNothingAboutPingsIsKept() {
        // a phone before 2.31: no ping= in its hello, so silence does not end it
        val p = Phone(port)
        p.send(Msg.hello("4821", "old phone"))
        assertNotNull(p.read())
        assertEquals("old phone", phones.poll(2, TimeUnit.SECONDS))
        assertNull(phones.poll(3500, TimeUnit.MILLISECONDS))
        assertTrue(server.paired)
    }

    @Test
    fun wrongCodesAreAnsweredOneAtATimeAndTheirPauseGrows() {
        assertEquals(100L, LinkServer.wrongCodePause(100, 1))
        assertEquals(100L, LinkServer.wrongCodePause(100, LinkServer.FREE_WRONG))
        assertEquals(200L, LinkServer.wrongCodePause(100, LinkServer.FREE_WRONG + 1))
        assertEquals(400L, LinkServer.wrongCodePause(100, LinkServer.FREE_WRONG + 2))
        assertEquals(LinkServer.MAX_PAUSE_MS, LinkServer.wrongCodePause(1500, 100))

        val slow = LinkServer(code = { "4821" }, welcome = { Msg.welcome("TV", "STEREO", false, "TV") }, onMessage = {}, wrongCodePauseMs = 150)
        val sp = slow.start(0)
        try {
            val start = System.currentTimeMillis()
            // three guesses at once, on three connections: answered one after another, not together
            val guesses = (1..3).map { k -> Phone(sp).also { it.send(Msg.hello("000$k", "guess")) } }
            guesses.forEach { assertEquals(Msg.DENIED, it.read()!!.type) }
            assertTrue(System.currentTimeMillis() - start >= 3 * 150 - 20, "wrong codes were answered together")
            // the right code is never held up
            val t0 = System.currentTimeMillis()
            val p = Phone(sp)
            p.send(Msg.hello("4821", "owner"))
            assertEquals(Msg.WELCOME, p.read()!!.type)
            assertTrue(System.currentTimeMillis() - t0 < 1000)
        } finally { slow.close() }
    }

    @Test
    fun waitingConnectionsAreCappedAndALongLineIsRefused() {
        val few = LinkServer(code = { "4821" }, welcome = { Msg.welcome("TV", "STEREO", false, "TV") }, onMessage = {}, maxWaiting = 2)
        val fp = few.start(0)
        try {
            val quiet = (1..2).map { Phone(fp) }
            Thread.sleep(200)
            // a third that waits unpaired is closed at once
            val third = Phone(fp)
            assertNull(runCatching { third.read() }.getOrNull())
            quiet.forEach { runCatching { it.s.close() } }
            Thread.sleep(200)
            // the slots are free again: a phone with the code pairs
            val p = Phone(fp)
            p.send(Msg.hello("4821", "owner"))
            assertEquals(Msg.WELCOME, p.read()!!.type)
        } finally { few.close() }

        // a first line longer than a hello can be ends the connection, unanswered
        val q = Phone(port)
        q.w.println("hello code=4821 name=" + "x".repeat(LinkServer.MAX_HELLO + 10))
        assertNull(runCatching { q.read() }.getOrNull())
        assertFalse(server.paired)
    }

    @Test
    fun anAddressIsHostOrHostAndPort() {
        assertEquals("192.168.1.20" to LINK_PORT, parseAddress(" 192.168.1.20 "))
        assertEquals("192.168.1.20" to 47811, parseAddress("192.168.1.20:47811"))
        assertEquals("desk-pc.local" to 5000, parseAddress("desk-pc.local:5000"))
        assertEquals("fe80::1" to LINK_PORT, parseAddress("fe80::1"))
        assertEquals("fe80::1" to 47812, parseAddress("[fe80::1]:47812"))
        assertNull(parseAddress(""))
        assertNull(parseAddress("192.168.1.20:"))
        assertNull(parseAddress("192.168.1.20:99999"))
        assertNull(parseAddress(":47810"))
        assertEquals("192.168.1.20", addressText("192.168.1.20", LINK_PORT))
        assertEquals("192.168.1.20:47811", addressText("192.168.1.20", 47811))
        assertEquals(30_000, silenceTimeoutMs(PING_SECONDS))
    }
}
