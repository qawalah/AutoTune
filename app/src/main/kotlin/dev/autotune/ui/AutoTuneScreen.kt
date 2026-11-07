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
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight

/**
 * Where [go] can take the user. Five are the tabs of the navigation bar, in [dev.autotune.NAV_ORDER] (Tests, EQ, Tune
 * raised in the centre, Live, Settings); Export opens as a page over the tab you are on, so Back returns there.
 * New entries are appended, so ordinals saved across a configuration change stay valid.
 */
enum class Tab(val title: String, val icon: Ic) {
    TESTS("Tests", Ic.TESTS),
    MEASURE("Tune", Ic.TUNE),
    EQ("EQ", Ic.EQ),
    LIVE("Live", Ic.LIVE),
    EXPORT("Export", Ic.EXPORT),
    SETTINGS("Settings", Ic.GEAR),
    ;

    val isPage: Boolean get() = this == EXPORT

    companion object {
        /** The bar, start to end. */
        val bar: List<Tab> get() = dev.autotune.NAV_ORDER.map { valueOf(it) }
    }
}

/** Pages opened over a tab, newest last. Back closes the newest. LIVE is kept for saved state; Live is a tab now. */
private enum class Page { RESULTS, LIVE, EXPORT }

/** What fills the screen: a tab, or the newest page over it, and how deep the pages go. */
private data class Dest(val tab: Tab, val page: Page?, val depth: Int)

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
            if (t != null) { tabIdx = Tab.valueOf(dev.autotune.requestedTab(t)).ordinal; pages = emptyList(); overlay = OVERLAY_NONE; LaunchRequest.tab.value = null }
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
    // a tab replaces whatever was open over it; Export opens as a page over the current tab
    val go: (Tab) -> Unit = { t ->
        overlay = OVERLAY_NONE
        when (t) {
            Tab.EXPORT -> open(Page.EXPORT)
            else -> { tabIdx = t.ordinal; pages = emptyList() }
        }
    }
    // a new step of the Tune workflow opens at its top
    LaunchedEffect(s.tuneStep) { scrolls[Tab.MEASURE.name]?.let { if (it.value > 0) it.animateScrollTo(0) } }
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

    val viewMode = androidx.compose.runtime.remember(s.advanced) { ViewMode(s.advanced, vm::setAdvanced) }
    AutoTuneTheme(s.themeMode, s.dynamicColor, s.look, s.accent) {
      androidx.compose.runtime.CompositionLocalProvider(
          LocalMenu provides { MenuButton(go, vm::shareDiagnostics) }, LocalPin provides pin, LocalViewMode provides viewMode,
      ) {
        Box(Modifier.fillMaxSize().background(At.c.bg)) {
            Scaffold(
                containerColor = At.c.bg,
                contentColor = At.c.text,
                bottomBar = {
                    if (!keyboardOpen && overlay == OVERLAY_NONE) {
                        Column {
                            // the screen's main action, always in reach; it rises in and drops away
                            val shownPin = pin.action?.takeIf { !s.busy }
                            val lastPin = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<PinnedAction?>(null) }
                            if (shownPin != null) lastPin.value = shownPin
                            androidx.compose.animation.AnimatedVisibility(
                                visible = shownPin != null,
                                enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(motionMs(240), easing = EaseArrive)) +
                                    androidx.compose.animation.expandVertically(androidx.compose.animation.core.tween(motionMs(300), easing = EaseArrive), expandFrom = Alignment.Bottom),
                                exit = androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(motionMs(100), easing = EaseLeave)) +
                                    androidx.compose.animation.shrinkVertically(androidx.compose.animation.core.tween(motionMs(160), easing = EaseLeave), shrinkTowards = Alignment.Bottom),
                            ) { lastPin.value?.let { BigActionBar(it) } }
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
                            if (page == null) AtNavBar(tab, s.busy, s.systemEqOn) { t -> go(t) }
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
                // tabs slide in from the side of the tab you chose; pages rise in over the tab and drop back on Back
                val anim = animationsOn()
                Transit(
                    Dest(tab, page, pages.size), Modifier.padding(pad).fillMaxSize(),
                    spec = {
                        val from = initialState; val to = targetState
                        if (!anim) androidx.compose.animation.EnterTransition.None togetherWith androidx.compose.animation.ExitTransition.None
                        else if (to.depth > from.depth) riseIn()
                        else if (to.depth < from.depth) dropOut()
                        else slideSideways(dev.autotune.navSlide(from.tab.name, to.tab.name))
                    },
                ) { d ->
                    Column(
                        Modifier.fillMaxSize().imePadding().verticalScroll(scrollFor(d.page?.name ?: d.tab.name), enabled = true)
                            .padding(horizontal = 16.dp).padding(bottom = 96.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        if (d.page != null) {
                            androidx.compose.runtime.CompositionLocalProvider(LocalBack provides back) {
                                when (d.page) {
                                    Page.RESULTS -> ResultsPage(s, vm, back, go)
                                    Page.LIVE -> LiveTab(s, live, level, vm, pick, go)
                                    Page.EXPORT -> ExportTab(s, vm, go)
                                }
                            }
                        } else when (d.tab) {
                            Tab.TESTS -> TestsTab(s, vm, { id -> vm.openTest(id); openResults() })
                            Tab.MEASURE -> TuneTab(s, vm, sharer, go, openResults, pick)
                            Tab.EQ -> EqTab(s, vm, go, pick)
                            Tab.LIVE -> LiveTab(s, live, level, vm, pick, go)
                            Tab.SETTINGS -> SettingsTab(s, ai, vm, pick)
                            // Export always opens as a page
                            Tab.EXPORT -> Unit
                        }
                    }
                }
            }

            // full screens rise in from the bottom and drop away when closed
            androidx.compose.animation.AnimatedVisibility(ai.open, enter = sheetIn(), exit = sheetOut()) {
                androidx.compose.material3.Surface(Modifier.fillMaxSize(), color = At.c.bg, contentColor = At.c.text) {
                    Box(Modifier.statusBarsPadding().navigationBarsPadding().imePadding()) { AiScreen(ai, s, vm, go) }
                }
            }

            // measuring takes the whole screen: the instrument, what is happening, and the checks
            val lastMv = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(s.measureView) }
            if (s.measureView != null) lastMv.value = s.measureView
            androidx.compose.animation.AnimatedVisibility(s.measureView != null, enter = sheetIn(), exit = sheetOut()) {
                lastMv.value?.let { mv ->
                    androidx.compose.material3.Surface(Modifier.fillMaxSize(), color = At.c.bg, contentColor = At.c.text) {
                        MeasureOverlay(mv, s, vm)
                    }
                }
            }

            // the teak cheeks of a Braun SK 4, down both edges, over every screen
            At.c.teak?.let { teak ->
                Box(Modifier.align(Alignment.CenterStart).fillMaxHeight().width(6.dp).background(teak))
                Box(Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(6.dp).background(teak))
            }

        }
      }
    }
}

/**
 * The tabs as a row of push keys, like the SK 4 and audio 1: small rectangular keys with their line icon, the pressed
 * one dark, a lowercase label under each. Tune in the centre is the aluminium tuning wheel with a red index; it turns
 * to the tab's side when chosen and its index pulses while a measurement runs on another tab.
 */
@Composable
fun AtNavBar(tab: Tab, busy: Boolean, eqOn: Boolean = false, onSelect: (Tab) -> Unit) {
    val c = At.c
    val items = Tab.bar
    Column(Modifier.fillMaxWidth().background(c.shell)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
        Row(
            Modifier.align(Alignment.CenterHorizontally).widthIn(max = 520.dp).fillMaxWidth().padding(horizontal = 10.dp).padding(top = 12.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom,
        ) {
            items.forEach { t ->
                val here = t == tab
                val src = androidx.compose.runtime.remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                Column(
                    Modifier.widthIn(min = 64.dp)
                        .selectable(selected = here, interactionSource = src, indication = null, role = androidx.compose.ui.semantics.Role.Tab, onClick = { onSelect(t) })
                        .semantics { contentDescription = t.title },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (t == Tab.MEASURE) TuneButton(here, busy && !here, src)
                    else {
                        // the key travels down a little when pressed, like a real push key
                        val down by androidx.compose.animation.core.animateDpAsState(if (here) 1.dp else 0.dp, androidx.compose.animation.core.tween(motionMs(120)), label = "key")
                        Box(
                            Modifier.offset(y = down).size(62.dp, 42.dp).pressScale(src).clip(RoundedCornerShape(4.dp))
                                .background(if (here) c.text else c.panelHigh).border(1.dp, if (here) c.text else c.line, RoundedCornerShape(4.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            AtIcon(t.icon, if (here) c.bg else c.text, size = 24.dp)
                            // AutoTune EQ on: a green pilot light in the key's corner, like a lit power key
                            if (t == Tab.EQ && eqOn) Box(Modifier.align(Alignment.TopEnd).padding(5.dp).size(8.dp).clip(CircleShape).background(c.good))
                        }
                    }
                    Text(
                        if (t == Tab.EQ && eqOn) "eq on" else t.title.lowercase(), Modifier.padding(top = 6.dp), style = MaterialTheme.typography.labelMedium,
                        color = if (here) c.text else c.muted, fontWeight = if (here) FontWeight.SemiBold else FontWeight.Medium, maxLines = 1,
                    )
                }
            }
        }
        Box(Modifier.fillMaxWidth().navigationBarsPadding())
    }
}

/**
 * Tune is a round push button, Bauhaus plain: a circle in the accent with the Tune mark. Chosen, it is pressed in like
 * the other keys (graphite, the mark in the accent, a step lower); while a measurement runs on another tab a ring
 * pulses round it.
 */
@Composable
private fun TuneButton(selected: Boolean, pulse: Boolean, src: androidx.compose.foundation.interaction.MutableInteractionSource) {
    val c = At.c
    val face by androidx.compose.animation.animateColorAsState(if (selected) c.text else c.signal, androidx.compose.animation.core.tween(motionMs(160)), label = "face")
    val down by androidx.compose.animation.core.animateDpAsState(if (selected) 2.dp else 0.dp, androidx.compose.animation.core.tween(motionMs(120)), label = "down")
    val ring = if (pulse && animationsOn()) {
        val inf = androidx.compose.animation.core.rememberInfiniteTransition(label = "busy")
        inf.animateFloat(0f, 1f, androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(1400)), label = "r").value
    } else null
    Box(Modifier.size(62.dp, 52.dp), contentAlignment = Alignment.Center) {
        if (ring != null) androidx.compose.foundation.Canvas(Modifier.size(62.dp)) {
            drawCircle(c.signal.copy(alpha = 0.5f * (1f - ring)), size.minDimension / 2 * (0.8f + 0.2f * ring), style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
        }
        Box(
            Modifier.offset(y = down).size(if (selected) 48.dp else 50.dp).pressScale(src).clip(CircleShape).background(face)
                .border(if (selected) 2.dp else 0.dp, c.line, CircleShape),
            contentAlignment = Alignment.Center,
        ) { AtIcon(Ic.TUNE, if (selected) c.signal else c.onSignal, size = 26.dp) }
    }
}

/** The three-bar menu: tools that are not part of the tuning loop. Live and Settings are tabs now. */
@Composable
fun MenuButton(go: (Tab) -> Unit, sendDiagnostics: () -> Unit) {
    var open by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    Box {
        IconAction(Ic.MENU, "Menu") { open = true }
        androidx.compose.material3.DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            MenuHeader("Tools")
            androidx.compose.material3.DropdownMenuItem(text = { Text("Export EQ files") }, leadingIcon = { AtIcon(Ic.EXPORT, At.c.muted) }, onClick = { open = false; go(Tab.EXPORT) })
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
