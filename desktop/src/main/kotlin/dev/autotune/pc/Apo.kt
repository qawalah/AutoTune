package dev.autotune.pc

import dev.autotune.dsp.EqChain
import dev.autotune.dsp.PlayMethod
import dev.autotune.dsp.Speaker
import java.util.Locale

/** An output of the PC as Windows lists it in the registry. [apo] is null when it cannot be told. */
data class RenderDevice(val guid: String, val name: String, val active: Boolean, val apo: Boolean?)

/** Where Equalizer APO stands for the chosen output; each has its one status line for the PC screen. */
enum class ApoState(val line: String) {
    NOT_WINDOWS("equalizer apo · windows only"),
    MISSING("equalizer apo is not installed"),
    NOT_ON_OUTPUT("equalizer apo is not on this output"),
    NEEDS_SETUP("autotune may not write its eq yet"),
    /** Installed and writable, but whether it is on this output cannot be told. */
    UNKNOWN("equalizer apo · installed"),
    READY("equalizer apo · on this output"),
}

/** Equalizer APO for one output: its state, the output's device (for the `Device:` line) and the config folder. */
data class ApoStatus(
    val state: ApoState, val device: RenderDevice? = null, val configDir: String? = null,
    /** Where Equalizer APO is installed (its Configurator is there). */
    val installDir: String? = null,
    /** The outputs Equalizer APO is on now (connected ones), as Windows names them. */
    val withApo: List<String> = emptyList(),
) {
    /** What to tell the phone when an EQ cannot reach the speakers, or null when it can (or might). */
    fun problem(output: String?): String? = when (state) {
        ApoState.NOT_WINDOWS -> "The PC's EQ works on Windows only."
        ApoState.MISSING -> "Equalizer APO is not installed on the PC. Install it there, then restart the PC."
        ApoState.NOT_ON_OUTPUT -> "Equalizer APO is not on ${output ?: "this output"}. Add it in Equalizer APO's Configurator, then restart the PC."
        ApoState.NEEDS_SETUP -> "On the PC, press \"Allow AutoTune\" once, so it can write its EQ."
        ApoState.UNKNOWN, ApoState.READY -> null
    }
}

/** The text AutoTune keeps in Equalizer APO's config folder, and the line that brings it into config.txt. */
object ApoText {
    const val FILE = "autotune.txt"
    const val INCLUDE = "Include: $FILE"
    const val BACKUP = "config-before-autotune.txt"

    /**
     * Equalizer APO's name for [s]'s channel with [method]'s channel order: L and R in stereo, otherwise the channel's
     * number (counted from 1), which is the order AutoTune plays its multichannel clips in. Null when [method] has no
     * channel for [s].
     */
    fun channelOf(s: Speaker, method: PlayMethod): String? = when (method) {
        PlayMethod.STEREO -> when (s) {
            Speaker.FL -> "L"
            Speaker.FR -> "R"
            else -> null
        }
        else -> method.slotOf(s)?.let { "${it + 1}" }
    }

    /**
     * The whole of AutoTune's file: one section per speaker (its channel, its preamp, its bands), limited to [deviceGuid]
     * when the output's device is known. No chains is the EQ switched off: the file stays, with nothing in it.
     */
    fun config(chains: Map<Speaker, EqChain>, method: PlayMethod, deviceGuid: String?, note: String): String = buildString {
        appendLine("# AutoTune: written by AutoTune on this PC ($note). It is rewritten each time, so edits here are lost.")
        val sections = chains.entries.mapNotNull { (s, c) -> channelOf(s, method)?.let { Triple(s, it, c) } }
            .sortedBy { method.slotOf(it.first) ?: Int.MAX_VALUE }
        if (sections.isEmpty()) {
            appendLine("# EQ off")
            return@buildString
        }
        if (deviceGuid != null) appendLine("Device: $deviceGuid")
        for ((s, ch, c) in sections) {
            appendLine("# ${s.title}")
            appendLine("Channel: $ch")
            appendLine(String.format(Locale.US, "Preamp: %.1f dB", c.preampDb))
            c.filters.sortedBy { it.freqHz }.forEachIndexed { i, f ->
                appendLine(String.format(Locale.US, "Filter %d: ON PK Fc %.0f Hz Gain %.1f dB Q %.2f", i + 1, f.freqHz, f.gainDb, f.q))
            }
        }
        appendLine("Channel: all")
        if (deviceGuid != null) appendLine("Device: all")
    }

    /** True when config.txt already brings in AutoTune's file. */
    fun hasInclude(configTxt: String): Boolean =
        configTxt.lineSequence().any { it.trim().replace(Regex("\\s+"), " ").equals(INCLUDE, ignoreCase = true) }

    /** config.txt with AutoTune's file brought in at its end (so AutoTune's EQ comes after the user's own lines). */
    fun withInclude(configTxt: String): String {
        if (hasInclude(configTxt)) return configTxt
        val nl = if (configTxt.contains("\r\n")) "\r\n" else "\n"
        val base = if (configTxt.isEmpty() || configTxt.endsWith("\n")) configTxt else configTxt + nl
        return base + "# AutoTune's EQ (the AutoTune app on this PC writes it)$nl$INCLUDE$nl"
    }

    /**
     * The one-time setup, run as administrator: keep a copy of config.txt, put [newConfig] in its place, make
     * AutoTune's file and let [sid] (the user) change it, so later EQs need no administrator.
     */
    fun setupScript(configDir: String, newConfig: String, sid: String?, firewall: List<String> = emptyList()): String {
        val d = configDir.trimEnd('\\', '/')
        val who = sid?.let { "*$it" } ?: "*S-1-5-32-545"
        return (listOf(
            "@echo off",
            "if exist \"$d\\config.txt\" if not exist \"$d\\$BACKUP\" copy /y \"$d\\config.txt\" \"$d\\$BACKUP\" >nul",
            "copy /y \"$newConfig\" \"$d\\config.txt\" >nul || exit /b 2",
            "if not exist \"$d\\$FILE\" (echo # AutoTune) > \"$d\\$FILE\"",
            "icacls \"$d\\$FILE\" /grant $who:(M) >nul || exit /b 3",
        ) + firewall + "exit /b 0").joinToString("\r\n", postfix = "\r\n")
    }

    /** The firewall rule's name; set again (not added twice) each time the setup runs. */
    const val FIREWALL_RULE = "AutoTune"

    /**
     * Lines for the setup (2.31) that let the phone reach AutoTune on private networks: an inbound rule for [program]
     * (AutoTune.exe), or, when it is not known (or is a bare java.exe), for its TCP [port]. Never fails the setup:
     * Windows' own prompt is still there if the rule does not take.
     */
    fun firewallLines(program: String?, port: Int): List<String> {
        val exe = program?.takeIf { p ->
            val n = p.substringAfterLast('\\').substringAfterLast('/').lowercase()
            n.endsWith(".exe") && n != "java.exe" && n != "javaw.exe" && !p.contains('"')
        }
        val what = if (exe != null) "program=\"$exe\"" else "protocol=TCP localport=$port"
        return listOf(
            "netsh advfirewall firewall delete rule name=\"$FIREWALL_RULE\" >nul 2>&1",
            "netsh advfirewall firewall add rule name=\"$FIREWALL_RULE\" dir=in action=allow $what enable=yes profile=private >nul 2>&1",
        )
    }
}

/** Reads the parts of `reg query` output AutoTune needs. Windows only runs it; the parsing is tested anywhere. */
object ApoRegistry {
    const val RENDER = "HKLM\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\MMDevices\\Audio\\Render"
    const val APOS = "HKLM\\SOFTWARE\\Classes\\AudioEngine\\AudioProcessingObjects"
    const val EQUALIZER_APO = "HKLM\\SOFTWARE\\EqualizerAPO"

    // the device's description ("Speakers") and its adapter's name ("Realtek(R) Audio")
    private const val DESC = "{a45c254e-df1c-4efd-8020-67d146a850e0},2"
    private const val ADAPTER = "{b3f8fa53-0004-438e-9003-51a46e139bfc},6"

    /** Equalizer APO's class id as remembered from its source, kept in case its registration has another name. */
    const val KNOWN_CLSID = "{EACD2258-FCAC-4FF4-B36D-419E924A6D79}"

    private val valueLine = Regex("^\\s+(\\S.*?)\\s{4}(REG_\\w+)\\s{4}(.*)$")
    private val guid = Regex("\\{[0-9A-Fa-f-]{36}}")

    /** Every key in [out] with its values (name to data). */
    fun keys(out: String): Map<String, Map<String, String>> {
        val keys = LinkedHashMap<String, MutableMap<String, String>>()
        var key: String? = null
        for (raw in out.lineSequence()) {
            val line = raw.trimEnd('\r')
            if (line.startsWith("HKEY_")) { key = line.trim(); keys.getOrPut(key) { LinkedHashMap() }; continue }
            val m = valueLine.find(line) ?: continue
            val k = key ?: continue
            keys.getValue(k)[m.groupValues[1].trim()] = m.groupValues[3].trim()
        }
        return keys
    }

    /** The config folder from `reg query HKLM\SOFTWARE\EqualizerAPO`, or null when it is not installed. */
    fun configDir(out: String?): String? {
        val v = out?.let { keys(it).values.firstOrNull() } ?: return null
        return v["ConfigPath"]?.takeIf { it.isNotBlank() } ?: v["InstallPath"]?.takeIf { it.isNotBlank() }?.trimEnd('\\')?.plus("\\config")
    }

    /** The install folder from `reg query HKLM\SOFTWARE\EqualizerAPO`, or the config folder's parent. */
    fun installDir(out: String?, configDir: String?): String? {
        val v = out?.let { keys(it).values.firstOrNull() }
        return v?.get("InstallPath")?.takeIf { it.isNotBlank() }?.trimEnd('\\')
            ?: configDir?.trimEnd('\\')?.let { c -> c.substring(0, c.lastIndexOf('\\').coerceAtLeast(0)).ifEmpty { null } }
    }

    /** The class ids of every audio effect registered with a name like "Equalizer APO", plus [KNOWN_CLSID]. */
    fun apoIds(out: String?): Set<String> {
        val found = out?.let { o ->
            keys(o).filter { (_, v) -> v["FriendlyName"].orEmpty().lowercase().replace(" ", "").contains("equalizerapo") }
                .keys.mapNotNull { k -> guid.findAll(k).lastOrNull()?.value?.uppercase() }
        }.orEmpty()
        return (found + KNOWN_CLSID).toSet()
    }

    /** The outputs from `reg query <RENDER> /s`. [apoIds] empty means it cannot be told which have Equalizer APO. */
    fun devices(out: String, apoIds: Set<String>): List<RenderDevice> {
        val all = keys(out)
        val byGuid = LinkedHashMap<String, MutableMap<String, Map<String, String>>>()
        for ((k, v) in all) {
            val after = k.substringAfter("\\Render\\", "")
            if (after.isEmpty()) continue
            val g = guid.find(after)?.value ?: continue
            val part = after.substringAfter(g, "").trimStart('\\')
            byGuid.getOrPut(g) { LinkedHashMap() }[part] = v
        }
        return byGuid.mapNotNull { (g, parts) ->
            val props = parts["Properties"].orEmpty()
            val desc = props[DESC] ?: return@mapNotNull null
            val adapter = props[ADAPTER]
            val state = parts[""]?.get("DeviceState")?.removePrefix("0x")?.toLongOrNull(16)
            val fx = parts["FxProperties"].orEmpty().values.joinToString(" ").uppercase()
            RenderDevice(
                guid = g,
                name = if (adapter.isNullOrBlank()) desc else "$desc ($adapter)",
                active = state == null || state == 1L,
                apo = if (apoIds.isEmpty()) null else apoIds.any { fx.contains(it.uppercase()) },
            )
        }
    }

    /**
     * The device behind a sound output's name. Windows' sound names are "Speakers (Realtek(R) Audio)"; some APIs cut
     * them at 31 characters, so a cut name matches the device it starts.
     */
    fun match(output: String, devices: List<RenderDevice>): RenderDevice? {
        val o = output.trim()
        if (o.isEmpty()) return null
        val live = devices.sortedByDescending { it.active }
        return live.firstOrNull { it.name.equals(o, ignoreCase = true) }
            ?: live.firstOrNull { o.length >= 12 && it.name.startsWith(o, ignoreCase = true) }
            ?: live.firstOrNull { it.name.contains(o, ignoreCase = true) && o.length >= 8 }
    }

    /** The user's SID from `whoami /user /fo csv /nh` ("desk\\me","S-1-5-21-…"). */
    fun sid(out: String?): String? = out?.let { Regex("S-1-5-[0-9-]+").find(it)?.value }
}
