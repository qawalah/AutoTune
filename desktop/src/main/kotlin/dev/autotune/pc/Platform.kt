package dev.autotune.pc

import dev.autotune.dsp.EqChain
import dev.autotune.dsp.Speaker
import dev.autotune.link.Msg
import dev.autotune.link.NSD_TYPE
import java.io.File
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import java.util.concurrent.TimeUnit
import javax.jmdns.JmDNS
import javax.jmdns.ServiceInfo
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.SourceDataLine
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.ensureActive

val isWindows: Boolean = System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)

/** Equalizer APO as the endpoint uses it, so tests can stand in for Windows. */
interface ApoControl {
    fun status(output: String?): ApoStatus
    /** Writes AutoTune's file. Returns what went wrong, or null. */
    fun write(status: ApoStatus, text: String): String?
    /** The one-time setup, as administrator. Returns what went wrong, or null. */
    fun setup(status: ApoStatus): String?
}

/** The PC's sound outputs, so tests can stand in for the sound card. */
interface AudioOut {
    fun outputs(): List<String>
    /** Plays interleaved 16-bit [pcm] on [output] until it ends; [onTime] gets the seconds played. */
    suspend fun play(output: String?, pcm: ShortArray, channels: Int, fs: Int, onTime: (Double) -> Unit)
}

/** Runs a command and returns its output, or null when it fails or takes too long. */
internal fun run(vararg cmd: String, charset: Charset = Charsets.UTF_8, timeoutSec: Long = 20): String? = runCatching {
    val p = ProcessBuilder(*cmd).redirectErrorStream(true).start()
    val out = p.inputStream.bufferedReader(charset).readText()
    if (!p.waitFor(timeoutSec, TimeUnit.SECONDS)) { p.destroyForcibly(); return null }
    if (p.exitValue() != 0) null else out
}.getOrNull()

/** `reg query`, with the console switched to UTF-8 so device names keep their letters. */
internal fun regQuery(vararg args: String): String? =
    run("cmd.exe", "/d", "/c", "chcp 65001 >nul & reg query " + args.joinToString(" ") { if (it.contains(' ')) "\"$it\"" else it })

/** Equalizer APO on this Windows PC: read from the registry, written in its config folder. */
class WindowsApo : ApoControl {
    override fun status(output: String?): ApoStatus {
        if (!isWindows) return ApoStatus(ApoState.NOT_WINDOWS)
        val dir = ApoRegistry.configDir(regQuery(ApoRegistry.EQUALIZER_APO, "/reg:64"))
            ?: "C:\\Program Files\\EqualizerAPO\\config".takeIf { File(it, "config.txt").exists() }
            ?: return ApoStatus(ApoState.MISSING)
        val ids = ApoRegistry.apoIds(regQuery(ApoRegistry.APOS, "/s", "/reg:64"))
        val devices = regQuery(ApoRegistry.RENDER, "/s", "/reg:64")?.let { ApoRegistry.devices(it, ids) }.orEmpty()
        val device = output?.let { ApoRegistry.match(it, devices) }
        val config = File(dir, "config.txt")
        val ours = File(dir, ApoText.FILE)
        val included = runCatching { ApoText.hasInclude(readConfig(config)) }.getOrDefault(false)
        val state = when {
            !included || !ours.exists() || !writable(ours) -> ApoState.NEEDS_SETUP
            device?.apo == false -> ApoState.NOT_ON_OUTPUT
            device?.apo == true -> ApoState.READY
            else -> ApoState.UNKNOWN
        }
        return ApoStatus(state, device, dir)
    }

    override fun write(status: ApoStatus, text: String): String? {
        val dir = status.configDir ?: return status.problem(null)
        return runCatching {
            // written in place, so the file keeps the rights the setup gave it
            Files.write(File(dir, ApoText.FILE).toPath(), text.toByteArray(Charsets.UTF_8), StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)
            null
        }.getOrElse { "The PC could not write its EQ (${it.message ?: it.javaClass.simpleName})." }
    }

    override fun setup(status: ApoStatus): String? {
        val dir = status.configDir ?: return status.problem(null)
        val config = File(dir, "config.txt")
        val tmp = Files.createTempDirectory("autotune").toFile()
        val newConfig = File(tmp, "config.txt")
        val old = runCatching { config.readBytes() }.getOrDefault(ByteArray(0))
        newConfig.writeBytes(encodeLike(old, ApoText.withInclude(decode(old))))
        val sid = ApoRegistry.sid(run("whoami", "/user", "/fo", "csv", "/nh", charset = Charset.defaultCharset()))
        val script = File(tmp, "autotune-setup.cmd")
        script.writeText(ApoText.setupScript(dir, newConfig.path, sid), Charsets.ISO_8859_1)
        val ps = "\$p = Start-Process -FilePath '${script.path.replace("'", "''")}' -Verb RunAs -Wait -WindowStyle Hidden -PassThru; exit \$p.ExitCode"
        val done = run("powershell.exe", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-Command", ps, timeoutSec = 300) != null
        runCatching { tmp.deleteRecursively() }
        return if (done) null else "Windows did not allow it. Try again and choose Yes."
    }

    private fun writable(f: File): Boolean = runCatching { Files.newOutputStream(f.toPath(), StandardOpenOption.APPEND).close(); true }.getOrDefault(false)

    private fun readConfig(f: File): String = decode(f.readBytes())

    /** config.txt as text: UTF-16 when it starts with that mark, otherwise byte for byte (ANSI and UTF-8 survive). */
    private fun decode(b: ByteArray): String = when {
        b.size >= 2 && b[0] == 0xFF.toByte() && b[1] == 0xFE.toByte() -> String(b, 2, b.size - 2, Charsets.UTF_16LE)
        else -> String(b, Charsets.ISO_8859_1)
    }

    private fun encodeLike(old: ByteArray, text: String): ByteArray = when {
        old.size >= 2 && old[0] == 0xFF.toByte() && old[1] == 0xFE.toByte() -> byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + text.toByteArray(Charsets.UTF_16LE)
        else -> text.toByteArray(Charsets.ISO_8859_1)
    }
}

/** The PC's outputs through Java Sound (on Windows these are its DirectSound devices). */
class JavaSoundOut : AudioOut {
    private val skip = setOf("Primary Sound Driver", "Port")

    override fun outputs(): List<String> = runCatching {
        AudioSystem.getMixerInfo().filter { info ->
            info.name !in skip && !info.name.startsWith("Port ") &&
                runCatching { AudioSystem.getMixer(info).sourceLineInfo.any { it.lineClass == SourceDataLine::class.java } }.getOrDefault(false)
        }.map { it.name }.distinct()
    }.getOrDefault(emptyList())

    override suspend fun play(output: String?, pcm: ShortArray, channels: Int, fs: Int, onTime: (Double) -> Unit) {
        val fmt = AudioFormat(fs.toFloat(), 16, channels, true, false)
        val info = DataLine.Info(SourceDataLine::class.java, fmt)
        val mixer = AudioSystem.getMixerInfo().firstOrNull { it.name == output }?.let { AudioSystem.getMixer(it) }
        val line = (mixer?.getLine(info) ?: AudioSystem.getLine(info)) as SourceDataLine
        val frameBytes = 2 * channels
        line.open(fmt, fs / 5 * frameBytes)
        try {
            line.start()
            val chunk = 2048 * channels
            val buf = ByteArray(chunk * 2)
            var i = 0
            while (i < pcm.size) {
                coroutineContext.ensureActive()
                val n = minOf(chunk, pcm.size - i)
                for (k in 0 until n) {
                    val v = pcm[i + k].toInt()
                    buf[2 * k] = v.toByte()
                    buf[2 * k + 1] = (v shr 8).toByte()
                }
                line.write(buf, 0, n * 2)
                i += n
                onTime(line.longFramePosition.toDouble() / fs)
            }
            line.drain()
            onTime(pcm.size.toDouble() / channels / fs)
        } finally {
            runCatching { line.stop() }
            line.close()
        }
    }
}

/** Announces the PC on the Wi-Fi as the phone looks for it: "AutoTune on <name>" on [NSD_TYPE]. */
class Announcer {
    private val open = ArrayList<JmDNS>()

    fun start(name: String, port: Int, addresses: List<InetAddress>) {
        close()
        for (a in addresses.take(3)) runCatching {
            val j = JmDNS.create(a, name)
            j.registerService(ServiceInfo.create(NSD_TYPE + "local.", "AutoTune on $name", port, "kind=${Msg.KIND_PC}"))
            open += j
        }
    }

    fun close() {
        open.forEach { runCatching { it.unregisterAllServices(); it.close() } }
        open.clear()
    }
}

object Net {
    private val virtual = Regex("(?i)virtual|vmware|vbox|hyper-v|vethernet|wsl|docker|loopback|bluetooth|tap|tun|zerotier|tailscale")

    /** The PC's home-network addresses, the likeliest first (a wired or Wi-Fi card, not a virtual one). */
    fun addresses(): List<Inet4Address> = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback && !it.isPointToPoint }
            .sortedBy { if (virtual.containsMatchIn(it.displayName.orEmpty() + " " + it.name)) 1 else 0 }
            .flatMap { i -> i.inetAddresses.toList().filterIsInstance<Inet4Address>().filter { it.isSiteLocalAddress } }
    }.getOrDefault(emptyList())

    fun pcName(): String =
        System.getenv("COMPUTERNAME")?.takeIf { it.isNotBlank() }
            ?: runCatching { InetAddress.getLocalHost().hostName }.getOrNull()?.takeIf { it.isNotBlank() }
            ?: "PC"
}

/**
 * What the PC app remembers, in one text file (`%APPDATA%\AutoTune\pc.txt`): the code the phone pairs with, the
 * output, and the EQ last received with whether it is on. Unknown lines are skipped, so later versions can add some.
 */
class PcStore(private val file: File = defaultFile()) {
    data class Saved(
        val code: String? = null,
        val output: String? = null,
        val chains: Map<Speaker, EqChain> = emptyMap(),
        val eqOn: Boolean = false,
        val from: String? = null,
        val whenMillis: Long? = null,
    )

    fun load(): Saved = runCatching {
        val v = file.readLines().mapNotNull { l -> l.indexOf('=').takeIf { it > 0 }?.let { l.substring(0, it) to l.substring(it + 1) } }.toMap()
        Saved(
            code = v["code"]?.takeIf { it.length == 4 && it.all(Char::isDigit) },
            output = v["output"]?.takeIf { it.isNotBlank() },
            chains = Msg.chains(v["chains"]),
            eqOn = v["eqon"] == "1",
            from = v["from"]?.takeIf { it.isNotBlank() },
            whenMillis = v["when"]?.toLongOrNull(),
        )
    }.getOrDefault(Saved())

    fun save(s: Saved) {
        runCatching {
            file.parentFile?.mkdirs()
            file.writeText(
                buildString {
                    appendLine("v=1")
                    s.code?.let { appendLine("code=$it") }
                    s.output?.let { appendLine("output=${it.replace('\n', ' ')}") }
                    if (s.chains.isNotEmpty()) appendLine("chains=${Msg.chainsText(s.chains)}")
                    appendLine("eqon=${if (s.eqOn) 1 else 0}")
                    s.from?.let { appendLine("from=${it.replace('\n', ' ')}") }
                    s.whenMillis?.let { appendLine("when=$it") }
                },
            )
        }
    }

    companion object {
        fun defaultFile(): File {
            val base = System.getenv("APPDATA")?.let { File(it, "AutoTune") } ?: File(System.getProperty("user.home"), ".autotune-pc")
            return File(base, "pc.txt")
        }
    }
}
