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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.only
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
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
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.sp
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
    THEATRE("Home theatre", Ic.HOME),
    ;

    val isPage: Boolean get() = this == EXPORT || this == THEATRE

    companion object {
        /** The bar, start to end. */
        val bar: List<Tab> get() = dev.autotune.NAV_ORDER.map { valueOf(it) }
    }
}

/** Pages opened over a tab, newest last. Back closes the newest. LIVE is kept for saved state; Live is a tab now. */
private enum class Page { RESULTS, LIVE, EXPORT, THEATRE }

/** What fills the screen: a tab, or the newest page over it, and how deep the pages go. */
private data class Dest(val tab: Tab, val page: Page?, val depth: Int)

/** The system pickers and permission prompts, launched from any tab. */
class Pickers(
    val permission: () -> Unit,
    val calibration: () -> Unit,
    val curve: () -> Unit,
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

    // The tall header shows on a fresh open only: a cold start, or back after 15 minutes or more in the background
    // (freshOpen in TuningLogic). Then every tab opens tall once; leaving a tab uses it up, so jumping back to it or
    // going Back to it opens it compact. Pages over a tab always open compact.
    val allTabs = Tab.bar.map { it.name }.toSet()
    var tallKeys by rememberSaveable { mutableStateOf(allTabs.toList()) }
    var backgroundedAt by rememberSaveable { mutableStateOf<Long?>(null) }
    var freshEpoch by rememberSaveable { mutableIntStateOf(0) }
    val titleSlots = androidx.compose.runtime.remember { mutableMapOf<String, TitleSlot>() }
    // a pull past the top of a screen plays its graphics again: one count per screen ([LocalReplay])
    val replays = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateMapOf<String, Int>() }
    val pullPx = with(LocalDensity.current) { 64.dp.toPx() }
    // the one chart open full screen, drawn over everything in this window
    val zoom = androidx.compose.runtime.remember { ChartZoom() }
    // how far the content scrolls while the header folds into the slim bar
    val rangePx = headerRangePx()
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycle) {
        val watch = androidx.lifecycle.LifecycleEventObserver { _, e ->
            when (e) {
                androidx.lifecycle.Lifecycle.Event.ON_STOP -> backgroundedAt = android.os.SystemClock.elapsedRealtime()
                androidx.lifecycle.Lifecycle.Event.ON_START -> {
                    val now = android.os.SystemClock.elapsedRealtime()
                    if (dev.autotune.freshOpen(backgroundedAt, now) && backgroundedAt != null) {
                        // back after a long while: every tab tall again, each from its top
                        tallKeys = dev.autotune.tallOnStart(tallKeys.toSet(), allTabs, backgroundedAt, now).toList()
                        allTabs.forEach { k -> scrolls[k]?.let { st -> st.dispatchRawDelta(-st.value.toFloat()) } }
                        freshEpoch++
                    }
                    backgroundedAt = null
                }
                else -> Unit
            }
        }
        lifecycle.lifecycle.addObserver(watch)
        onDispose { lifecycle.lifecycle.removeObserver(watch) }
    }
    val screenKey = page?.name ?: tab.name
    val lastKey = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
    LaunchedEffect(screenKey) {
        val left = lastKey.value
        if (left != null && left != screenKey && left in tallKeys) tallKeys = dev.autotune.tallAfterLeaving(tallKeys.toSet(), left).toList()
        lastKey.value = screenKey
    }

    LaunchedEffect(Unit) {
        LaunchRequest.tab.collect { t ->
            if (t != null) {
                overlay = OVERLAY_NONE
                // the widget's companion key opens the TV/PC page over the Tune tab
                if (t.equals("theatre", ignoreCase = true)) { tabIdx = Tab.MEASURE.ordinal; pages = emptyList(); open(Page.THEATRE) }
                else { tabIdx = Tab.valueOf(dev.autotune.requestedTab(t)).ordinal; pages = emptyList() }
                LaunchRequest.tab.value = null
            }
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
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri -> uri?.let(vm::setExportFolder) }
    val pick = Pickers(
        permission = { permLauncher.launch(Manifest.permission.RECORD_AUDIO) },
        calibration = { calPicker.launch(arrayOf("text/*", "application/octet-stream")) },
        curve = { curvePicker.launch(arrayOf("text/*", "application/octet-stream")) },
        notifications = { if (Build.VERSION.SDK_INT >= 33) notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS) },
        bluetooth = { if (Build.VERSION.SDK_INT >= 31) btPerm.launch(Manifest.permission.BLUETOOTH_CONNECT) else vm.refreshBluetooth() },
        exportFolder = { folderPicker.launch(null) },
    )
    // the home theatre the Tests tab asked the Home theatre page to open
    var theatreOpen by rememberSaveable { mutableStateOf<String?>(null) }
    // a new home theatre to start once connected, named on Tune's Set up ("" = after the TV or PC) (2.24)
    var theatreNew by rememberSaveable { mutableStateOf<String?>(null) }
    // what Set up was connecting (a home theatre or PC speakers), for the Connect page's title (2.32)
    var theatreKind by rememberSaveable { mutableStateOf<String?>(null) }
    // a tab replaces whatever was open over it; Export opens as a page over the current tab
    val go: (Tab) -> Unit = { t ->
        // pressing the tab you are on plays its graphics again
        if (t.ordinal == tabIdx && pages.isEmpty() && overlay == OVERLAY_NONE) replays[t.name] = (replays[t.name] ?: 0) + 1
        overlay = OVERLAY_NONE
        when (t) {
            Tab.EXPORT -> open(Page.EXPORT)
            Tab.THEATRE -> open(Page.THEATRE)
            else -> { tabIdx = t.ordinal; pages = emptyList() }
        }
    }
    // a measuring key pressed without microphone access asks for it
    LaunchedEffect(Unit) { vm.askMic.collect { pick.permission() } }
    // a new step of the Tune workflow opens at its top
    LaunchedEffect(s.tuneStep) { scrolls[Tab.MEASURE.name]?.let { if (it.value > 0) it.animateScrollTo(0) } }
    // the results of the open test, over the tab you are on
    val openResults: () -> Unit = { overlay = OVERLAY_NONE; open(Page.RESULTS) }
    val keyboardOpen = WindowInsets.ime.getBottom(LocalDensity.current) > 0

    // Back, app-wide (2.33, CLAUDE.md): the top thing closes first (a dialog, menu or sheet itself, the full-screen
    // graph, the measuring screen, the AI sheet, a page); inside Tune one step back; a tab's top screen goes to Tests;
    // Tests leaves the app. The measuring screen's own Back (MeasureOverlay) asks before measured spots are dropped.
    val covered = s.measureView != null || ai.open || zoom.shown != null
    BackHandler(overlay != OVERLAY_NONE) { overlay = OVERLAY_NONE }
    BackHandler(!covered && overlay == OVERLAY_NONE && page == null && tab != Tab.TESTS) { tabIdx = Tab.TESTS.ordinal }
    // inside the Tune workflow, Back goes to the previous screen of the workflow, not to another tab
    BackHandler(!covered && overlay == OVERLAY_NONE && page == null && tab == Tab.MEASURE && s.tuneStep != dev.autotune.TuneStep.HOME && !s.busy) { vm.tuneBack() }
    // a page always goes back to where it was opened from
    BackHandler(!covered && overlay == OVERLAY_NONE && page != null) { back() }
    BackHandler(ai.open && zoom.shown == null) { vm.aiClose() }

    // Only the EQ tab turns sideways (its graph and faders fill the width); every other screen stays upright, either
    // way up, so the phone's mic can point up while measuring (owner, 2026-11-11: landscape was broken elsewhere)
    val activity = androidx.compose.ui.platform.LocalContext.current as? android.app.Activity
    // the full-screen chart turns too, wherever it was opened from
    LaunchedEffect(tab, page, s.measureView != null, zoom.shown != null) {
        activity?.requestedOrientation =
            if (zoom.shown != null || dev.autotune.turnsSideways(tab.name, page != null, s.measureView != null)) android.content.pm.ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
            else android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
    }
    val viewMode = androidx.compose.runtime.remember(s.advanced) { ViewMode(s.advanced, vm::setAdvanced) }
    AutoTuneTheme(s.themeMode, s.look, s.accent, s.palette) {
      androidx.compose.runtime.CompositionLocalProvider(
          LocalPin provides pin, LocalViewMode provides viewMode, LocalChartZoom provides zoom, LocalCovered provides covered,
          LocalStagger provides androidx.compose.runtime.remember { Stagger() },
      ) {
        Box(
            Modifier.fillMaxSize().background(At.c.bg)
                // sideways, the system's buttons and the camera cut-out sit at a side: keep everything clear of them
                .windowInsetsPadding(
                    androidx.compose.foundation.layout.WindowInsets.navigationBars.union(androidx.compose.foundation.layout.WindowInsets.displayCutout)
                        .only(androidx.compose.foundation.layout.WindowInsetsSides.Horizontal),
                ),
        ) {
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
                            // the five tabs on every screen, pages included (owner, 2026-11-11); a tab closes the pages over it
                            AtNavBar(tab, s.busy, s.systemEqOn) { t -> go(t) }
                        }
                    }
                },
                floatingActionButton = {
                    if (tab == Tab.TESTS && page == null && overlay == OVERLAY_NONE && !keyboardOpen) {
                        // a flat graphite key like the main key: device corners, a hairline edge, no shadow at rest or pressed
                        ExtendedFloatingActionButton(
                            onClick = { vm.tuneStart(); go(Tab.MEASURE) },
                            containerColor = At.c.text, contentColor = At.c.bg,
                            shape = MaterialTheme.shapes.medium,
                            elevation = androidx.compose.material3.FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp),
                            modifier = Modifier.border(1.dp, At.c.line, MaterialTheme.shapes.medium),
                            icon = { AtIcon(Ic.PLUS, At.c.signal) },
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
                    // each screen has its own header slot; the shell draws the header over the room the screen keeps for it
                    val key = d.page?.name ?: d.tab.name
                    val titleSlot = titleSlots.getOrPut(key) { TitleSlot() }
                    val sc = scrollFor(key)
                    // the content's own height and the viewport's, for "more below"
                    val sizes = androidx.compose.runtime.remember(key) { intArrayOf(-1, -1) }
                    // A screen that does not open tall opens with its header folded into the slim bar (content high),
                    // or where it was left if that is further down. Its header's room stays at its top, so scrolling
                    // all the way up brings the full header back. Decided as the screen arrives.
                    // the Tune tab has no header on any step: its home starts with the dial, its steps with StepHeader
                    val hasHeader = !(d.page == null && d.tab == Tab.MEASURE)
                    androidx.compose.runtime.remember(d, freshEpoch) {
                        // a test's results open tall, so their graphic is seen at its size and plays there
                        val tall = (d.page == null && key in tallKeys) || d.page == Page.RESULTS
                        if (!tall) {
                            val want = dev.autotune.compactOpenScroll(sc.value, rangePx, hasHeader)
                            if (want != sc.value) sc.dispatchRawDelta((want - sc.value).toFloat())
                        }
                    }
                    // pulled down past its top (the scroll is at 0 and the drag goes on), the screen plays its graphics again
                    val pull = androidx.compose.runtime.remember(key) { dev.autotune.PullReplay(pullPx) }
                    val pullWatch = androidx.compose.runtime.remember(key, sc) {
                        object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
                            override fun onPostScroll(
                                consumed: androidx.compose.ui.geometry.Offset, available: androidx.compose.ui.geometry.Offset,
                                source: androidx.compose.ui.input.nestedscroll.NestedScrollSource,
                            ): androidx.compose.ui.geometry.Offset {
                                if (source == androidx.compose.ui.input.nestedscroll.NestedScrollSource.UserInput && pull.drag(available.y, sc.value == 0)) {
                                    replays[key] = (replays[key] ?: 0) + 1
                                }
                                return androidx.compose.ui.geometry.Offset.Zero
                            }

                            override suspend fun onPreFling(available: androidx.compose.ui.unit.Velocity): androidx.compose.ui.unit.Velocity {
                                pull.end()
                                return androidx.compose.ui.unit.Velocity.Zero
                            }
                        }
                    }
                    Box(Modifier.fillMaxSize()) {
                    androidx.compose.runtime.CompositionLocalProvider(
                        LocalTitleSlot provides titleSlot, LocalBack provides (if (d.page != null) back else null), LocalReplay provides (replays[key] ?: 0),
                    ) {
                    Column(
                        Modifier.fillMaxSize().imePadding().nestedScroll(pullWatch).scrollWithHeaderRoom(sc, if (hasHeader) rangePx else 0, sizes)
                            .padding(horizontal = 16.dp).padding(bottom = 96.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        if (d.page != null) {
                            androidx.compose.runtime.CompositionLocalProvider(LocalBack provides back) {
                                when (d.page) {
                                    Page.RESULTS -> ResultsPage(s, vm, back, go)
                                    Page.LIVE -> LiveTab(s, live, level, vm, pick, go)
                                    Page.EXPORT -> ExportTab(s, vm, go)
                                    Page.THEATRE -> TheatrePage(pick, theatreOpen, opened = { theatreOpen = null; theatreNew = null }, newName = theatreNew, sharer = sharer, picked = dev.autotune.Equipment.orNull(theatreKind))
                                }
                            }
                        } else when (d.tab) {
                            Tab.TESTS -> TestsTab(
                                s, vm, { id -> vm.openTest(id); vm.selectBestRun(); openResults() }, continueTest = { id -> vm.openTest(id); vm.tuneContinue(); go(Tab.MEASURE) },
                                openTheatre = { id -> theatreOpen = id; theatreKind = null; go(Tab.THEATRE) },
                                openCompanion = { theatreKind = null; go(Tab.THEATRE) },
                            )
                            Tab.MEASURE -> TuneTab(s, vm, sharer, go, openResults, pick, openCompanion = { n, e -> theatreNew = n; theatreKind = e.name; go(Tab.THEATRE) })
                            Tab.EQ -> EqTab(s, vm, go, pick)
                            Tab.LIVE -> LiveTab(s, live, level, vm, pick, go)
                            Tab.SETTINGS -> SettingsTab(s, ai, vm, pick, go)
                            // Export and the home theatre always open as pages
                            Tab.EXPORT, Tab.THEATRE -> Unit
                        }
                    }
                    // more of the screen below the edge: a fade and a key that scrolls on
                    MoreBelow(sc, Modifier.align(Alignment.BottomCenter), sizes)
                    ScreenHeader(titleSlot, sc, Modifier.align(Alignment.TopCenter))
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
                        // its Back is its own, composed after the tabs', so it wins while it is up (2.33)
                        MeasureOverlay(mv, s, vm, backEnabled = s.measureView != null && !ai.open && zoom.shown == null)
                    }
                }
            }

            // a chart opened full screen rises over everything; Close or Back drops it
            ChartZoomLayer(zoom)

            // the first-start tutorial, over everything until it is finished or skipped; it fades away into the app
            androidx.compose.animation.AnimatedVisibility(
                s.showTutorial,
                enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(motionMs(300), easing = EaseArrive)),
                exit = androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(motionMs(260), easing = EaseLeave)) +
                    androidx.compose.animation.scaleOut(androidx.compose.animation.core.tween(motionMs(260), easing = EaseLeave), targetScale = 1.04f),
            ) {
                androidx.compose.material3.Surface(Modifier.fillMaxSize(), color = At.c.bg, contentColor = At.c.text) {
                    // it ends on the Tune tab, its key under the thumb (2.32: it ended on an empty Tests list)
                    Tutorial({ vm.tutorialDone(); tabIdx = Tab.MEASURE.ordinal; pages = emptyList() })
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
    // the soft look: a band of plastic, a shade lighter at its top edge, instead of a ruled strip
    val band = if (c.soft) Modifier.background(
        androidx.compose.ui.graphics.Brush.verticalGradient(listOf(androidx.compose.ui.graphics.lerp(c.shell, androidx.compose.ui.graphics.Color.White, if (c.dark) 0.03f else 0.35f), c.shell)),
    ) else Modifier.background(c.shell)
    Column(Modifier.fillMaxWidth().then(band)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(if (c.soft) (if (c.dark) androidx.compose.ui.graphics.Color.White.copy(alpha = 0.06f) else androidx.compose.ui.graphics.Color.White) else c.line))
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
                        // a latched key (AutoTune EQ on) stays down, its face in shadow from the rim, as a pressed Braun key
                        val latched = t == Tab.EQ && eqOn
                        val down by androidx.compose.animation.core.animateDpAsState((if (here) 1.dp else 0.dp) + (if (latched) 2.dp else 0.dp), androidx.compose.animation.core.tween(motionMs(120)), label = "key")
                        val face = when {
                            here -> c.text
                            latched -> androidx.compose.ui.graphics.lerp(c.panelHigh, c.line, 0.55f)
                            else -> c.panelHigh
                        }
                        // the soft look: raised plastic keys; the chosen one is pressed in, and EQ on lights a pilot on its key
                        val keyShape = RoundedCornerShape(if (c.soft) 12.dp else 4.dp)
                        Box(
                            Modifier.offset(y = down).size(62.dp, 42.dp).pressScale(src)
                                .then(
                                    if (c.soft) Modifier.keyFace(keyShape, down = here)
                                    else Modifier.clip(keyShape).background(face).border(1.dp, if (here) c.text else c.line, keyShape),
                                )
                                .semantics { if (latched) stateDescription = "AutoTune EQ on" },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (latched && c.soft) Pilot(true, Modifier.align(Alignment.TopEnd).padding(top = 7.dp, end = 8.dp), 6.dp)
                            else
                            if (latched) Box(
                                Modifier.matchParentSize().background(
                                    androidx.compose.ui.graphics.Brush.verticalGradient(
                                        0f to androidx.compose.ui.graphics.Color.Black.copy(alpha = if (c.dark) 0.45f else 0.22f),
                                        0.45f to androidx.compose.ui.graphics.Color.Transparent,
                                    ),
                                ),
                            )
                            AtIcon(t.icon, if (here && !c.soft) c.bg else c.text, size = 24.dp)
                        }
                    }
                    Text(
                        t.title, Modifier.padding(top = 6.dp), style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 0.2.sp),
                        color = if (here) c.onShell else c.onShellMuted, fontWeight = if (here) FontWeight.SemiBold else FontWeight.Medium, maxLines = 1,
                    )
                }
            }
        }
        Box(Modifier.fillMaxWidth().navigationBarsPadding())
    }
}

private val KnobRing = androidx.compose.ui.graphics.Color(0xFF2B2B29)
private val KnobTicks = androidx.compose.ui.graphics.Color(0xFF4A4945)

/**
 * Tune is the app's mark as a round key, drawn as the Windows app's icon (since 2.30.0): a graphite ring, a light face
 * with its scale and the index dot in the accent. Pressed, the knob turns up its scale to where it rests. Chosen, it
 * is pressed in (a step lower, a little smaller); while a measurement runs on another tab a ring pulses round it.
 */
@Composable
private fun TuneButton(selected: Boolean, pulse: Boolean, src: androidx.compose.foundation.interaction.MutableInteractionSource) {
    val c = At.c
    // the knob turns up its scale, from the low end to where it rests, each time it is pressed
    val turn = androidx.compose.runtime.remember { androidx.compose.animation.core.Animatable(KNOB_POINTER_DEG) }
    val turnMs = motionMs(650)
    androidx.compose.runtime.LaunchedEffect(src, turnMs) {
        src.interactions.collect { i ->
            if (i is androidx.compose.foundation.interaction.PressInteraction.Press && turnMs > 0) launch {
                turn.snapTo(KNOB_SCALE_START_DEG)
                turn.animateTo(KNOB_POINTER_DEG, androidx.compose.animation.core.tween(turnMs, easing = EaseArrive))
            }
        }
    }
    val down by androidx.compose.animation.core.animateDpAsState(if (selected) 2.dp else 0.dp, androidx.compose.animation.core.tween(motionMs(120)), label = "down")
    val ring = if (pulse && animationsOn()) {
        val inf = androidx.compose.animation.core.rememberInfiniteTransition(label = "busy")
        inf.animateFloat(0f, 1f, androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(1400)), label = "r").value
    } else null
    Box(Modifier.size(62.dp, 52.dp), contentAlignment = Alignment.Center) {
        if (ring != null) androidx.compose.foundation.Canvas(Modifier.size(62.dp)) {
            drawCircle(c.signal.copy(alpha = 0.5f * (1f - ring)), size.minDimension / 2 * (0.8f + 0.2f * ring), style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
        }
        // the app's mark, as on Windows: a graphite ring, a light face with its scale and the index dot in the accent;
        // pressed in (a step lower, a little smaller) while Tune is the tab shown, standing on a soft shadow in the Concept look
        val (face, _) = dialColours(c)
        val size = if (selected) 50.dp else 54.dp
        androidx.compose.foundation.Canvas(
            Modifier.offset(y = down).size(size).pressScale(src)
                .then(if (c.soft && !selected) Modifier.shadow(6.dp, CircleShape, clip = false) else Modifier),
        ) {
            drawKnobMark(KnobRing, face, KnobTicks, c.signal, turn.value)
            if (selected) drawCircle(
                androidx.compose.ui.graphics.Brush.verticalGradient(listOf(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.22f), androidx.compose.ui.graphics.Color.Transparent), endY = this.size.height * 0.5f),
                this.size.minDimension * 0.48f,
            )
        }
    }
}
