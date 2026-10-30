package dev.autotune.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import dev.autotune.TuneViewModel

/**
 * Where [go] can take the user. The first three are the tabs of the navigation bar; Live and Export open as pages over
 * the tab you are on, so Back returns there.
 */
enum class Tab(val title: String, val icon: Ic) {
    TESTS("Tests", Ic.TESTS),
    MEASURE("Tune", Ic.TUNE),
    EQ("EQ", Ic.EQ),
    LIVE("Live", Ic.LIVE),
    EXPORT("Export", Ic.EXPORT),
    ;

    val isPage: Boolean get() = this == LIVE || this == EXPORT
}

/** Pages opened over a tab, newest last. Back closes the newest. */
private enum class Page { RESULTS, LIVE, EXPORT }

/** The system pickers and permission prompts, launched from any tab. */
class Pickers(
    val permission: () -> Unit,
    val calibration: () -> Unit,
    val curve: () -> Unit,
    val clip: () -> Unit,
    val notifications: () -> Unit,
    val bluetooth: () -> Unit,
    /** The system folder picker, for where saved files go. */
    val exportFolder: () -> Unit = {},
)

/** Set by a home-screen widget to open a tab. */
object LaunchRequest { val tab = kotlinx.coroutines.flow.MutableStateFlow<String?>(null) }

private const val OVERLAY_NONE = 0
private const val OVERLAY_SETUP = 2

@Composable
fun AutoTuneScreen(vm: TuneViewModel, sharer: Sharer) {
    val s by vm.state.collectAsState()
    val live by vm.rtaBars.collectAsState()
    val level by vm.level.collectAsState()
    val ai by vm.ai.collectAsState()
    val pin = androidx.compose.runtime.remember { PinSlot() }
    var tabIdx by rememberSaveable { mutableIntStateOf(Tab.TESTS.ordinal) }
    var overlay by rememberSaveable { mutableIntStateOf(OVERLAY_NONE) }
    // pages over the tab, as ordinals (saveable), newest last
    var pages by rememberSaveable { mutableStateOf(listOf<Int>()) }
    val tab = Tab.entries[tabIdx]
    val page = pages.lastOrNull()?.let { Page.entries[it] }
    // each tab and page keeps its own place, so Back lands where you left it
    val scrolls = androidx.compose.runtime.remember { mutableMapOf<String, androidx.compose.foundation.ScrollState>() }
    val scrollFor: (String) -> androidx.compose.foundation.ScrollState = { k -> scrolls.getOrPut(k) { androidx.compose.foundation.ScrollState(0) } }
    // a page opens at its top; the tab under it keeps its place
    val open: (Page) -> Unit = { p -> scrolls.remove(p.name); pages = pages.filter { it != p.ordinal } + p.ordinal }
    val back: () -> Unit = { pages = pages.dropLast(1) }

    LaunchedEffect(Unit) {
        LaunchRequest.tab.collect { t ->
            if (t != null) { tabIdx = if (t == "eq") Tab.EQ.ordinal else Tab.MEASURE.ordinal; pages = emptyList(); overlay = OVERLAY_NONE; LaunchRequest.tab.value = null }
        }
    }
    // a finished measurement stays in the Tune workflow, which shows the result itself
    LaunchedEffect(Unit) { vm.fileToShare.collect { sharer.files(it.files, it.mime, it.title) } }
    // Leaving a tab or a page stops any sound it started and drops its messages.
    LaunchedEffect(tabIdx, page) { vm.onTabChanged() }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.setPermission(it) }
    val calPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::importCalibration) }
    val curvePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::importTargetCurve) }
    val notifPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val btPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.refreshBluetooth() }
    val clipPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::pickClip) }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri -> uri?.let(vm::setExportFolder) }
    val pick = Pickers(
        permission = { permLauncher.launch(Manifest.permission.RECORD_AUDIO) },
        calibration = { calPicker.launch(arrayOf("text/*", "application/octet-stream")) },
        curve = { curvePicker.launch(arrayOf("text/*", "application/octet-stream")) },
        clip = { clipPicker.launch(arrayOf("audio/*")) },
        notifications = { if (Build.VERSION.SDK_INT >= 33) notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS) },
        bluetooth = { if (Build.VERSION.SDK_INT >= 31) btPerm.launch(Manifest.permission.BLUETOOTH_CONNECT) else vm.refreshBluetooth() },
        exportFolder = { folderPicker.launch(null) },
    )
    // a tab replaces whatever was open over it; Live and Export open as pages over the current tab
    val go: (Tab) -> Unit = { t ->
        overlay = OVERLAY_NONE
        when (t) {
            Tab.LIVE -> open(Page.LIVE)
            Tab.EXPORT -> open(Page.EXPORT)
            else -> { tabIdx = t.ordinal; pages = emptyList() }
        }
    }
    // the results of the open test, over the tab you are on
    val openResults: () -> Unit = { overlay = OVERLAY_NONE; open(Page.RESULTS) }
    val keyboardOpen = WindowInsets.ime.getBottom(LocalDensity.current) > 0

    BackHandler(overlay != OVERLAY_NONE) { overlay = OVERLAY_NONE }
    BackHandler(overlay == OVERLAY_NONE && page == null && tab != Tab.TESTS) { tabIdx = Tab.TESTS.ordinal }
    // inside the Tune workflow, Back goes to the previous screen of the workflow, not to another tab
    BackHandler(overlay == OVERLAY_NONE && page == null && tab == Tab.MEASURE && s.tuneStep != dev.autotune.TuneStep.HOME && !s.busy) { vm.tuneBack() }
    // a page always goes back to where it was opened from
    BackHandler(overlay == OVERLAY_NONE && page != null) { back() }
    BackHandler(ai.open) { vm.aiClose() }
    // registered last so it wins while the measuring screen is up
    // Back on the measuring screen: stop a running measurement, otherwise close the screen
    BackHandler(s.measureView != null) { if (s.busy) vm.cancel() else vm.closeMeasureView() }

    AutoTuneTheme(s.themeMode, s.dynamicColor, s.look) {
      androidx.compose.runtime.CompositionLocalProvider(LocalMenu provides { MenuButton(go, vm::shareDiagnostics, vm::aiOpen) { overlay = OVERLAY_SETUP } }, LocalPin provides pin) {
        Box(Modifier.fillMaxSize().background(At.c.bg)) {
            Scaffold(
                containerColor = At.c.bg,
                contentColor = At.c.text,
                bottomBar = {
                    if (!keyboardOpen && overlay == OVERLAY_NONE) {
                        Column {
                            // the screen's main action, always in reach
                            pin.action?.let { a -> if (!s.busy) BigActionBar(a) }
                            // a measurement keeps running when you leave the Tune tab
                            if (s.busy && (tab != Tab.MEASURE || page != null)) {
                                Row(
                                    Modifier.fillMaxWidth().background(At.c.panelHigh).clickable { go(Tab.MEASURE) }.padding(horizontal = 16.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(s.status ?: "Measuring…", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, maxLines = 1)
                                    Text("Open", style = MaterialTheme.typography.labelMedium, color = At.c.signal)
                                }
                                val p = s.progress
                                if (p != null) LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth(), color = At.c.signal, trackColor = At.c.line)
                                else LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = At.c.signal, trackColor = At.c.line)
                            }
                            // a page is a step away from its tab: the tabs come back with Back
                            if (page == null) AtNavBar(tab) { t -> go(t) }
                            else Box(Modifier.fillMaxWidth().background(At.c.bg).navigationBarsPadding())
                        }
                    }
                },
                floatingActionButton = {
                    if (tab == Tab.TESTS && page == null && overlay == OVERLAY_NONE && !keyboardOpen) {
                        ExtendedFloatingActionButton(
                            onClick = { vm.tuneStart(); go(Tab.MEASURE) },
                            containerColor = At.c.signal, contentColor = At.c.onSignal,
                            icon = { AtIcon(Ic.PLUS, At.c.onSignal) },
                            text = { Text("New test", style = MaterialTheme.typography.labelLarge) },
                        )
                    }
                },
            ) { pad ->
                Column(
                    Modifier.padding(pad).fillMaxSize().imePadding().verticalScroll(scrollFor(page?.name ?: tab.name), enabled = true)
                        .padding(horizontal = 16.dp).padding(bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    if (page != null) {
                        androidx.compose.runtime.CompositionLocalProvider(LocalBack provides back) {
                            when (page) {
                                Page.RESULTS -> ResultsPage(s, vm, back, go)
                                Page.LIVE -> LiveTab(s, live, level, vm, pick, go)
                                Page.EXPORT -> ExportTab(s, vm, go)
                            }
                        }
                    } else when (tab) {
                        Tab.TESTS -> TestsTab(s, vm, { id -> vm.openTest(id); openResults() })
                        Tab.MEASURE -> TuneTab(s, vm, sharer, go, openResults, pick)
                        Tab.EQ -> EqTab(s, vm, go, pick)
                        // Live and Export always open as pages
                        Tab.LIVE, Tab.EXPORT -> Unit
                    }
                }
            }

            if (ai.open) {
                androidx.compose.material3.Surface(Modifier.fillMaxSize(), color = At.c.bg, contentColor = At.c.text) {
                    Box(Modifier.statusBarsPadding().navigationBarsPadding().imePadding()) { AiScreen(ai, s, vm, go) }
                }
            }

            // measuring takes the whole screen: the instrument, what is happening, and the checks
            s.measureView?.let { mv ->
                androidx.compose.material3.Surface(Modifier.fillMaxSize(), color = At.c.bg, contentColor = At.c.text) {
                    MeasureOverlay(mv, s, vm)
                }
            }

            if (overlay != OVERLAY_NONE) {
                androidx.compose.material3.Surface(Modifier.fillMaxSize(), color = At.c.bg, contentColor = At.c.text) {
                  Box(Modifier.statusBarsPadding().navigationBarsPadding().imePadding()) {
                    when (overlay) {
                        OVERLAY_SETUP -> SetupScreen(s, ai, vm, pick) { overlay = OVERLAY_NONE }
                    }
                  }
                }
            }
        }
      }
    }
}

@Composable
fun AtNavBar(tab: Tab, onSelect: (Tab) -> Unit) {
    // the shell band: three destinations, centred; Live, Export and Settings live in the menu
    val c = At.c
    Box(Modifier.fillMaxWidth().background(c.shell), contentAlignment = Alignment.Center) {
        NavigationBar(Modifier.widthIn(max = 360.dp), containerColor = androidx.compose.ui.graphics.Color.Transparent, contentColor = c.onShell) {
            Tab.entries.filter { !it.isPage }.forEach { t ->
                val here = t == tab
                NavigationBarItem(
                    selected = here,
                    onClick = { onSelect(t) },
                    icon = { AtIcon(t.icon, if (here) c.onShell else c.onShellMuted) },
                    label = { Text(t.title, style = MaterialTheme.typography.labelMedium) },
                    alwaysShowLabel = true,
                    colors = NavigationBarItemDefaults.colors(
                        selectedTextColor = c.onShell, unselectedTextColor = c.onShellMuted,
                        indicatorColor = androidx.compose.ui.graphics.lerp(c.shell, c.onShell, 0.16f),
                    ),
                )
            }
        }
    }
}

/** The three-bar menu: tools that are not part of the tuning loop. */
@Composable
fun MenuButton(go: (Tab) -> Unit, sendDiagnostics: () -> Unit, openAi: () -> Unit, openSettings: () -> Unit) {
    var open by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    Box {
        IconAction(Ic.MENU, "Menu") { open = true }
        androidx.compose.material3.DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            // grouped: tools for the sound, then the app itself; the AI review lives on the results it reviews
            MenuHeader("Tools")
            androidx.compose.material3.DropdownMenuItem(text = { Text("Live spectrum") }, leadingIcon = { AtIcon(Ic.LIVE, At.c.muted) }, onClick = { open = false; go(Tab.LIVE) })
            androidx.compose.material3.DropdownMenuItem(text = { Text("Export EQ files") }, leadingIcon = { AtIcon(Ic.EXPORT, At.c.muted) }, onClick = { open = false; go(Tab.EXPORT) })
            androidx.compose.material3.HorizontalDivider(color = At.c.line)
            MenuHeader("App")
            androidx.compose.material3.DropdownMenuItem(text = { Text("Settings") }, leadingIcon = { AtIcon(Ic.GEAR, At.c.muted) }, onClick = { open = false; openSettings() })
            androidx.compose.material3.DropdownMenuItem(text = { Text("Send diagnostics") }, leadingIcon = { AtIcon(Ic.DIAG, At.c.muted) }, onClick = { open = false; sendDiagnostics() })
        }
    }
}

@Composable
private fun MenuHeader(text: String) {
    Text(
        text.uppercase(), style = MaterialTheme.typography.labelSmall, color = At.c.muted,
        modifier = Modifier.padding(start = 16.dp, top = 10.dp, bottom = 4.dp),
    )
}
