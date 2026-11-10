package dev.autotune.theatre

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import dev.autotune.link.LINK_PORT
import dev.autotune.link.LinkMsg
import dev.autotune.link.Msg
import dev.autotune.link.NSD_TYPE
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.coroutines.resume

/** A TV or PC found on the Wi-Fi. */
class FoundTv(val name: String, internal val info: NsdServiceInfo)

/** The phone's end of the link: finds TVs and PCs running AutoTune, connects with the code on their screen, sends and receives lines. */
class LinkClient(private val ctx: Context, private val scope: CoroutineScope) {
    private val nsd: NsdManager? = ctx.getSystemService(NsdManager::class.java)
    private val _found = MutableStateFlow<List<FoundTv>>(emptyList())
    val found: StateFlow<List<FoundTv>> = _found
    private val _incoming = MutableSharedFlow<LinkMsg>(extraBufferCapacity = 64)
    val incoming: SharedFlow<LinkMsg> = _incoming
    private val _open = MutableStateFlow(false)
    /** True while connected and paired. */
    val open: StateFlow<Boolean> = _open

    private var socket: Socket? = null
    private var out: PrintWriter? = null
    private var discovery: NsdManager.DiscoveryListener? = null

    fun discover() {
        if (discovery != null || nsd == null) return
        val l = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(t: String) {}
            override fun onDiscoveryStopped(t: String) {}
            override fun onStartDiscoveryFailed(t: String, code: Int) { discovery = null }
            override fun onStopDiscoveryFailed(t: String, code: Int) {}
            override fun onServiceFound(i: NsdServiceInfo) {
                val name = i.serviceName.removePrefix("AutoTune on ")
                _found.update { list -> list.filter { it.info.serviceName != i.serviceName } + FoundTv(name, i) }
            }
            override fun onServiceLost(i: NsdServiceInfo) {
                _found.update { list -> list.filter { it.info.serviceName != i.serviceName } }
            }
        }
        runCatching { nsd!!.discoverServices(NSD_TYPE, NsdManager.PROTOCOL_DNS_SD, l); discovery = l }
    }

    fun stopDiscovery() {
        discovery?.let { runCatching { nsd?.stopServiceDiscovery(it) } }
        discovery = null
    }

    @Suppress("DEPRECATION")
    private suspend fun resolve(tv: FoundTv): Pair<String, Int> = withTimeout(8000) {
        suspendCancellableCoroutine { cont ->
            nsd?.resolveService(tv.info, object : NsdManager.ResolveListener {
                override fun onResolveFailed(i: NsdServiceInfo, code: Int) { if (cont.isActive) cont.resume("" to 0) }
                override fun onServiceResolved(i: NsdServiceInfo) {
                    if (cont.isActive) cont.resume((i.host?.hostAddress ?: "") to i.port)
                }
            }) ?: cont.resume("" to 0)
        }
    }

    /** Connects to [tv] and pairs with [code]. Returns its welcome; throws with the reason in words. */
    suspend fun connect(tv: FoundTv, code: String, phone: String): LinkMsg {
        val (host, port) = resolve(tv)
        if (host.isEmpty()) error("It did not answer. Check it is on the same Wi-Fi.")
        return connect(host, port, code, phone)
    }

    suspend fun connect(host: String, port: Int = LINK_PORT, code: String, phone: String): LinkMsg = withContext(Dispatchers.IO) {
        close()
        val s = Socket()
        try {
            s.connect(InetSocketAddress(host, port), 5000)
            s.soTimeout = 0
            val w = PrintWriter(s.getOutputStream().bufferedWriter(), true)
            val r = BufferedReader(InputStreamReader(s.getInputStream()))
            w.println(Msg.hello(code, phone).encode())
            s.soTimeout = 6000
            val first = LinkMsg.parse(r.readLine() ?: error("It closed the connection.")) ?: error("It sent something unexpected.")
            s.soTimeout = 0
            if (first.type == Msg.DENIED) error(first["reason"] ?: "It refused the code.")
            if (first.type != Msg.WELCOME) error("It sent something unexpected.")
            socket = s; out = w
            _open.value = true
            scope.launch(Dispatchers.IO) {
                try {
                    while (true) {
                        val line = r.readLine() ?: break
                        LinkMsg.parse(line)?.let { _incoming.emit(it) }
                    }
                } catch (_: Exception) {
                } finally {
                    if (socket === s) { _open.value = false; socket = null; out = null }
                    runCatching { s.close() }
                }
            }
            first
        } catch (e: Exception) {
            runCatching { s.close() }
            if (e is java.net.SocketTimeoutException) error("It did not answer.")
            if (e is java.io.IOException) error("Could not reach it (${e.message ?: "no answer"}).")
            throw e
        }
    }

    /** One background writer, so messages go out in order and never from the main thread. */
    private val writer = java.util.concurrent.Executors.newSingleThreadExecutor()

    fun send(m: LinkMsg) {
        val w = out ?: return
        writer.execute { runCatching { w.println(m.encode()) } }
    }

    fun close() {
        val s = socket; val w = out
        socket = null; out = null
        _open.value = false
        if (s != null) writer.execute { runCatching { w?.println(Msg.bye().encode()) }; runCatching { s.close() } }
    }
}
