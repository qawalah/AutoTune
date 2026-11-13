package dev.autotune.pc

import dev.autotune.dsp.EqChain
import dev.autotune.dsp.EqFilter
import dev.autotune.dsp.PlayMethod
import dev.autotune.dsp.Speaker
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ApoTest {
    private val chains = mapOf(
        Speaker.FR to EqChain(listOf(EqFilter(2500.0, 2.0, 1.4), EqFilter(63.0, -4.5, 2.0)), -3.0),
        Speaker.FL to EqChain(listOf(EqFilter(120.0, -6.0, 4.0)), -3.0),
    )

    @Test
    fun eachSpeakerGetsItsOwnChannelPreampAndBands() {
        val t = ApoText.config(chains, PlayMethod.STEREO, "{0d1b6a65-0000-4000-8000-123456789abc}", "from Galaxy")
        val lines = t.lines().filter { it.isNotBlank() && !it.startsWith("#") }
        assertEquals(
            listOf(
                "Device: {0d1b6a65-0000-4000-8000-123456789abc}",
                "Channel: L",
                "Preamp: -3.0 dB",
                "Filter 1: ON PK Fc 120 Hz Gain -6.0 dB Q 4.00",
                "Channel: R",
                "Preamp: -3.0 dB",
                "Filter 1: ON PK Fc 63 Hz Gain -4.5 dB Q 2.00",
                "Filter 2: ON PK Fc 2500 Hz Gain 2.0 dB Q 1.40",
                "Channel: all",
                "Device: all",
            ),
            lines,
        )
    }

    @Test
    fun offIsAFileWithNoFilters() {
        val t = ApoText.config(emptyMap(), PlayMethod.STEREO, "{x}", "off")
        assertTrue(t.lines().all { it.isBlank() || it.startsWith("#") })
        // without the output's device nothing limits the EQ to it
        assertFalse(ApoText.config(chains, PlayMethod.STEREO, null, "n").contains("Device:"))
        // a speaker stereo has no channel for is left out
        val c = ApoText.config(mapOf(Speaker.C to EqChain(listOf(EqFilter(100.0, -3.0, 1.0)))), PlayMethod.STEREO, null, "n")
        assertFalse(c.contains("Channel: "))
    }

    @Test
    fun multichannelUsesTheClipsChannelNumbers() {
        assertEquals("1", ApoText.channelOf(Speaker.FL, PlayMethod.PCM_71))
        assertEquals("4", ApoText.channelOf(Speaker.SUB, PlayMethod.PCM_71))
        assertEquals("8", ApoText.channelOf(Speaker.SR, PlayMethod.PCM_71))
        assertEquals("5", ApoText.channelOf(Speaker.SL, PlayMethod.PCM_51))
        assertNull(ApoText.channelOf(Speaker.BL, PlayMethod.PCM_51))
    }

    @Test
    fun includeIsAddedOnceKeepingLineEnds() {
        val win = "Preamp: -6 dB\r\nInclude: example.txt\r\n"
        val once = ApoText.withInclude(win)
        assertTrue(once.endsWith("Include: autotune.txt\r\n"))
        assertTrue(once.startsWith(win))
        assertEquals(once, ApoText.withInclude(once))
        assertTrue(ApoText.hasInclude("x\n  include:   AUTOTUNE.TXT  \n"))
        assertEquals("# AutoTune's EQ (the AutoTune app on this PC writes it)\nInclude: autotune.txt\n", ApoText.withInclude(""))
        assertTrue(ApoText.withInclude("Preamp: -6 dB").startsWith("Preamp: -6 dB\n"))
    }

    @Test
    fun setupScriptBacksUpCopiesAndGrants() {
        val s = ApoText.setupScript("C:\\Program Files\\EqualizerAPO\\config\\", "C:\\Users\\Me Too\\AppData\\Local\\Temp\\a\\config.txt", "S-1-5-21-1-2-3-1001")
        assertTrue(s.contains("copy /y \"C:\\Program Files\\EqualizerAPO\\config\\config.txt\" \"C:\\Program Files\\EqualizerAPO\\config\\config-before-autotune.txt\""))
        assertTrue(s.contains("copy /y \"C:\\Users\\Me Too\\AppData\\Local\\Temp\\a\\config.txt\" \"C:\\Program Files\\EqualizerAPO\\config\\config.txt\""))
        assertTrue(s.contains("icacls \"C:\\Program Files\\EqualizerAPO\\config\\autotune.txt\" /grant *S-1-5-21-1-2-3-1001:(M)"))
        assertTrue(s.contains("\r\n"))
        // without the user's SID, the Users group
        assertTrue(ApoText.setupScript("C:\\c", "C:\\t\\config.txt", null).contains("*S-1-5-32-545:(M)"))
    }

    @Test
    fun theSetupLetsThePhoneThroughTheFirewall() {
        val app = ApoText.firewallLines("C:\\Users\\Me\\AppData\\Local\\AutoTune\\AutoTune.exe", 47810)
        assertTrue(app.last().contains("program=\"C:\\Users\\Me\\AppData\\Local\\AutoTune\\AutoTune.exe\""))
        assertTrue(app.last().contains("dir=in action=allow"))
        assertTrue(app.last().contains("profile=private"))
        // set again, never added twice; never stops the setup
        assertTrue(app.first().contains("delete rule name=\"AutoTune\""))
        assertTrue(app.none { it.contains("exit") })
        // a bare java.exe (run from a build) is not let in wholesale: only the port
        val dev = ApoText.firewallLines("C:\\jdk\\bin\\java.exe", 47811)
        assertTrue(dev.last().contains("protocol=TCP localport=47811"))
        assertTrue(ApoText.firewallLines(null, 47810).last().contains("localport=47810"))
        // the rule comes before the script's end
        val s = ApoText.setupScript("C:\\c", "C:\\t\\config.txt", null, app)
        assertTrue(s.indexOf("netsh") in 1 until s.indexOf("exit /b 0"))
    }

    private val render = """

HKEY_LOCAL_MACHINE\SOFTWARE\Microsoft\Windows\CurrentVersion\MMDevices\Audio\Render\{0d1b6a65-0000-4000-8000-123456789abc}
    DeviceState    REG_DWORD    0x1

HKEY_LOCAL_MACHINE\SOFTWARE\Microsoft\Windows\CurrentVersion\MMDevices\Audio\Render\{0d1b6a65-0000-4000-8000-123456789abc}\FxProperties
    {d04e05a6-594b-4fb6-a80d-01af5eed7d1d},5    REG_SZ    {eacd2258-fcac-4ff4-b36d-419e924a6d79}

HKEY_LOCAL_MACHINE\SOFTWARE\Microsoft\Windows\CurrentVersion\MMDevices\Audio\Render\{0d1b6a65-0000-4000-8000-123456789abc}\Properties
    {a45c254e-df1c-4efd-8020-67d146a850e0},2    REG_SZ    Speakers
    {b3f8fa53-0004-438e-9003-51a46e139bfc},6    REG_SZ    Realtek(R) Audio

HKEY_LOCAL_MACHINE\SOFTWARE\Microsoft\Windows\CurrentVersion\MMDevices\Audio\Render\{11111111-2222-4333-8444-555555555555}
    DeviceState    REG_DWORD    0x4

HKEY_LOCAL_MACHINE\SOFTWARE\Microsoft\Windows\CurrentVersion\MMDevices\Audio\Render\{11111111-2222-4333-8444-555555555555}\Properties
    {a45c254e-df1c-4efd-8020-67d146a850e0},2    REG_SZ    Headphones
    {b3f8fa53-0004-438e-9003-51a46e139bfc},6    REG_SZ    WH-1000XM4 Hands-Free AG Audio

HKEY_LOCAL_MACHINE\SOFTWARE\Microsoft\Windows\CurrentVersion\MMDevices\Audio\Render\{99999999-2222-4333-8444-555555555555}
    DeviceState    REG_DWORD    0x1

HKEY_LOCAL_MACHINE\SOFTWARE\Microsoft\Windows\CurrentVersion\MMDevices\Audio\Render\{99999999-2222-4333-8444-555555555555}\FxProperties
    {d04e05a6-594b-4fb6-a80d-01af5eed7d1d},5    REG_SZ    {62dc1a93-ae24-464c-a43e-452f824c4250}

HKEY_LOCAL_MACHINE\SOFTWARE\Microsoft\Windows\CurrentVersion\MMDevices\Audio\Render\{99999999-2222-4333-8444-555555555555}\Properties
    {a45c254e-df1c-4efd-8020-67d146a850e0},2    REG_SZ    LG TV SSCR2
    {b3f8fa53-0004-438e-9003-51a46e139bfc},6    REG_SZ    NVIDIA High Definition Audio
""".trimIndent().replace("\n", "\r\n")

    @Test
    fun outputsAndWhichHaveEqualizerApoComeFromTheRegistry() {
        val apos = """
HKEY_LOCAL_MACHINE\SOFTWARE\Classes\AudioEngine\AudioProcessingObjects\{62DC1A93-AE24-464C-A43E-452F824C4250}
    FriendlyName    REG_SZ    Microsoft Speaker Protection

HKEY_LOCAL_MACHINE\SOFTWARE\Classes\AudioEngine\AudioProcessingObjects\{EC1CC9CE-FAED-4822-828A-82A81A6F018F}
    FriendlyName    REG_SZ    Equalizer APO
""".trimIndent()
        val ids = ApoRegistry.apoIds(apos)
        assertTrue("{EC1CC9CE-FAED-4822-828A-82A81A6F018F}" in ids)
        assertTrue(ApoRegistry.KNOWN_CLSID in ids)
        assertFalse("{62DC1A93-AE24-464C-A43E-452F824C4250}" in ids)

        val d = ApoRegistry.devices(render, ids)
        assertEquals(listOf("Speakers (Realtek(R) Audio)", "Headphones (WH-1000XM4 Hands-Free AG Audio)", "LG TV SSCR2 (NVIDIA High Definition Audio)"), d.map { it.name })
        assertEquals(listOf(true, false, true), d.map { it.active })
        assertEquals(listOf(true, false, false), d.map { it.apo })
        // without the effects' names nothing is claimed
        assertEquals(listOf(null, null, null), ApoRegistry.devices(render, emptySet()).map { it.apo })
    }

    @Test
    fun soundNamesFindTheirDeviceEvenWhenCut() {
        val d = ApoRegistry.devices(render, ApoRegistry.apoIds(null))
        assertEquals("{0d1b6a65-0000-4000-8000-123456789abc}", ApoRegistry.match("Speakers (Realtek(R) Audio)", d)?.guid)
        assertEquals("{99999999-2222-4333-8444-555555555555}", ApoRegistry.match("LG TV SSCR2 (NVIDIA High Defin", d)?.guid)
        assertNull(ApoRegistry.match("Primary Sound Driver", d))
        assertNull(ApoRegistry.match("", d))
    }

    @Test
    fun configFolderAndUser() {
        val eq = """
HKEY_LOCAL_MACHINE\SOFTWARE\EqualizerAPO
    InstallPath    REG_SZ    C:\Program Files\EqualizerAPO
    ConfigPath    REG_SZ    D:\Audio\EqualizerAPO\config
"""
        assertEquals("D:\\Audio\\EqualizerAPO\\config", ApoRegistry.configDir(eq))
        assertEquals("C:\\Program Files\\EqualizerAPO\\config", ApoRegistry.configDir("HKEY_LOCAL_MACHINE\\SOFTWARE\\EqualizerAPO\n    InstallPath    REG_SZ    C:\\Program Files\\EqualizerAPO\\\n"))
        assertNull(ApoRegistry.configDir(null))
        assertEquals("S-1-5-21-123-456-789-1001", ApoRegistry.sid("\"desk\\me\",\"S-1-5-21-123-456-789-1001\"\r\n"))
    }

    @Test
    fun eachStateSaysWhatToDo() {
        assertNull(ApoStatus(ApoState.READY).problem("Speakers"))
        assertNull(ApoStatus(ApoState.UNKNOWN).problem("Speakers"))
        assertTrue(ApoStatus(ApoState.NOT_ON_OUTPUT).problem("Speakers")!!.contains("Speakers"))
        assertTrue(ApoStatus(ApoState.MISSING).problem(null)!!.contains("not installed"))
    }
}
