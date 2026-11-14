package dev.autotune.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.drawText
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.autotune.TUTORIAL_CARDS
import dev.autotune.TUTORIAL_FADERS
import dev.autotune.playPhase
import dev.autotune.tutorialBack
import dev.autotune.tutorialCurveDb
import dev.autotune.tutorialFaderDb
import dev.autotune.tutorialFaderX
import dev.autotune.tutorialKeyLabel
import dev.autotune.tutorialMotion
import dev.autotune.tutorialPlayMs
import dev.autotune.tutorialPos
import dev.autotune.tutorialReadingDb
import dev.autotune.tutorialShowsSkip
import dev.autotune.TUTORIAL_TV_CODE
import dev.autotune.TUTORIAL_LIVE_BARS
import dev.autotune.tutorialTvDigits
import dev.autotune.tutorialTvPlaying
import dev.autotune.tutorialLiveBar
import dev.autotune.tutorialLivePeak
import kotlinx.coroutines.launch

/**
 * The first-start tutorial (2.9.0), after the one in CrossPaths: five full-screen cards you swipe through, the opening
 * card and the four steps of a tune ([TUTORIAL_CARDS]). Each card's picture plays when the card comes up, and again
 * each time you come back to it; as you swipe, the picture travels further than the words ([tutorialMotion]), so the
 * card reads as layered. Skip is on every card but the last; the main key reads Next, then Get started. Back goes to
 * the previous card and closes from the first. Shown once; Settings › About replays it. With "Remove animations" each
 * picture shows its last frame. [start] is the card it opens on (the screenshot harness opens each).
 */
@Composable
fun Tutorial(onDone: () -> Unit, start: Int = 0) {
    val c = At.c
    val cards = TUTORIAL_CARDS
    val pager = rememberPagerState(initialPage = start) { cards.size }
    val scope = rememberCoroutineScope()
    val anim = animationsOn()
    val page = pager.currentPage
    val goTo: (Int) -> Unit = { i ->
        scope.launch { if (anim) pager.animateScrollToPage(i, animationSpec = tween(380, easing = EaseArrive)) else pager.scrollToPage(i) }
    }
    BackHandler { tutorialBack(pager.currentPage)?.let(goTo) ?: onDone() }

    Column(Modifier.fillMaxSize().background(c.bg).statusBarsPadding().navigationBarsPadding()) {
        // the top line: the wordmark, and Skip
        Row(Modifier.fillMaxWidth().height(56.dp).padding(start = 20.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Wordmark(Modifier.weight(1f), fixedSize = true)
            if (tutorialShowsSkip(page)) TextButton(onClick = onDone) {
                Text("Skip", style = MaterialTheme.typography.labelLarge, color = c.muted)
            }
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val pageW = constraints.maxWidth.toFloat()
            val room = maxHeight
            val artW = (maxWidth - 56.dp).coerceAtMost(320.dp)
            val artH = (room * 0.46f).coerceIn(150.dp, 220.dp)
            HorizontalPager(pager, Modifier.fillMaxSize()) { i ->
                val card = cards[i]
                val pos = { tutorialPos(i, pager.currentPage, pager.currentPageOffsetFraction) }
                // centred while it fits, scrolled when a large font makes it taller than the room
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    Column(
                        Modifier.fillMaxWidth().heightIn(min = room).padding(horizontal = 28.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            Modifier.graphicsLayer {
                                val m = tutorialMotion(pos())
                                translationX = m.artShift * pageW; alpha = m.alpha; scaleX = m.artScale; scaleY = m.artScale
                            },
                        ) {
                            CardArt(card.key, playing = pager.currentPage == i, away = pager.settledPage != i && pager.currentPage != i, width = artW, height = artH)
                        }
                        Spacer(Modifier.height(28.dp))
                        Column(
                            Modifier.fillMaxWidth().graphicsLayer {
                                val m = tutorialMotion(pos())
                                translationX = m.textShift * pageW; alpha = m.alpha
                            },
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            // one counter: the lights under the cards (2.32: "step 3 of 4" sat over card 4 of 5)
                            Text(card.title, style = MaterialTheme.typography.headlineMedium, color = c.text, textAlign = TextAlign.Center)
                            Spacer(Modifier.height(10.dp))
                            Text(card.text, style = MaterialTheme.typography.bodyLarge, color = c.muted, textAlign = TextAlign.Center)
                        }
                    }
                }
            }
        }
        PageLights(pager, goTo)
        Box(Modifier.padding(horizontal = 16.dp).padding(top = 12.dp, bottom = 16.dp)) {
            MainKey(tutorialKeyLabel(page), onClick = { if (page == cards.lastIndex) onDone() else goTo(page + 1) })
        }
    }
}

/** One pilot light per card: the current one lit in the accent and drawn long, the light moving with the swipe. */
@Composable
private fun PageLights(pager: PagerState, goTo: (Int) -> Unit) {
    val c = At.c
    val off = c.muted.copy(alpha = 0.4f)
    Row(
        Modifier.fillMaxWidth().semantics { contentDescription = dev.autotune.tutorialCounter(pager.currentPage) },
        horizontalArrangement = Arrangement.Center,
    ) {
        repeat(pager.pageCount) { i ->
            Box(
                Modifier.size(28.dp, 28.dp).clickable(onClickLabel = "Card ${i + 1}") { goTo(i) }.drawBehind {
                    // read in the draw phase, so a swipe only redraws the lights
                    val lit = tutorialMotion(tutorialPos(i, pager.currentPage, pager.currentPageOffsetFraction)).alpha
                    val h = 8.dp.toPx(); val w = h * (1f + 1.6f * lit)
                    drawRoundRect(lerp(off, c.signal, lit), Offset((size.width - w) / 2, (size.height - h) / 2), Size(w, h), CornerRadius(h / 2))
                },
            )
        }
    }
}

/**
 * A card's picture, played: its time runs from 0 to [tutorialPlayMs] each time the card becomes the current one, and
 * goes back to 0 once the card is off screen, so it plays from the start whenever you come back to it. With
 * "Remove animations", and in previews and screenshots, it shows its last frame.
 */
@Composable
private fun CardArt(key: String, playing: Boolean, away: Boolean, width: Dp, height: Dp) {
    val still = !animationsOn() || androidx.compose.ui.platform.LocalInspectionMode.current
    val total = tutorialPlayMs(key).toFloat()
    val t = remember { Animatable(if (still) total else 0f) }
    LaunchedEffect(playing) {
        if (playing) {
            if (still) t.snapTo(total) else { t.snapTo(0f); t.animateTo(total, tween(total.toInt(), easing = LinearEasing)) }
        }
    }
    LaunchedEffect(away) { if (away && !still) t.snapTo(0f) }
    TutorialArt(key, { t.value }, width, height)
}

/**
 * A card's picture at [ms] into its play: a light deck on the dark body, drawn in the deck's colours like the header
 * graphics. Read [ms] only while drawing, so playing redraws without recomposing (the Verify card's reading excepted).
 */
@Composable
internal fun TutorialArt(key: String, ms: () -> Float, width: Dp, height: Dp) {
    DeckColors {
        Box(Modifier.size(width, height).deckPlate().clearAndSetSemantics { }) {
            when (key) {
                "hello" -> HelloArt(ms)
                "measure" -> MeasureArt(ms)
                "eq" -> EqArt(ms)
                "verify" -> VerifyArt(ms)
                "tv" -> TvArt(ms)
                "live" -> LiveArt(ms)
                else -> KeepArt(ms)
            }
        }
    }
}

/** The part of a picture that runs from [delayMs] for [durationMs], eased in like every arrival in the app. */
private fun ease(ms: Float, delayMs: Int, durationMs: Int): Float = EaseArrive.transform(playPhase(ms, delayMs, durationMs))

/** The y of [db] on a plot from [top] to [bottom], ±[rangeDb] round its middle. */
private fun yOf(db: Float, top: Float, bottom: Float, rangeDb: Float = 10f): Float =
    (top + bottom) / 2 - (bottom - top) / 2 * (db / rangeDb).coerceIn(-1f, 1f)

/** The demo system's response from [x0] to [x1] of the width (20 Hz to 20 kHz), [tuned] of the way to the EQ'd one. */
private fun responsePath(left: Float, width: Float, top: Float, bottom: Float, tuned: Float, x1: Float = 1f): Path {
    val path = Path()
    val n = 80
    for (k in 0..n) {
        val x = x1 * k / n
        val px = left + width * x; val py = yOf(tutorialCurveDb(x, tuned), top, bottom)
        if (k == 0) path.moveTo(px, py) else path.lineTo(px, py)
    }
    return path
}

/** Chart paper: the decades and the 0 dB line, in hairlines. */
private fun DrawScope.chartPaper(left: Float, width: Float, top: Float, bottom: Float, rule: Color) {
    val hair = 1.dp.toPx()
    for (d in 0..3) { val x = left + width * d / 3f; drawLine(rule.copy(alpha = 0.6f), Offset(x, top), Offset(x, bottom), hair) }
    for (db in listOf(-5f, 5f)) { val y = yOf(db, top, bottom); drawLine(rule.copy(alpha = 0.35f), Offset(left, y), Offset(left + width, y), hair) }
}

/** The opening card: the AutoTune mark, a radio's tuning knob, turns until the needle on the scale finds its station. */
@Composable
private fun HelloArt(ms: () -> Float) {
    val c = At.c
    Canvas(Modifier.fillMaxSize()) {
        val t = ms()
        val turn = ease(t, 100, 1300)
        val lit = playPhase(t, 1250, 250)
        val w = size.width; val h = size.height
        val hair = 1.dp.toPx()
        // the tuning scale: a window of ticks, and the red needle that follows the knob
        val win = Offset(w * 0.08f, h * 0.1f); val ws = Size(w * 0.84f, h * 0.22f)
        drawRoundRect(c.bg, win, ws, CornerRadius(2.dp.toPx()))
        val n = 30
        for (k in 0..n) {
            val x = win.x + ws.width * (0.04f + 0.92f * k / n)
            val long = k % 5 == 0
            drawLine(c.muted.copy(alpha = if (long) 0.9f else 0.5f), Offset(x, win.y + ws.height * 0.22f), Offset(x, win.y + ws.height * if (long) 0.7f else 0.48f), hair)
        }
        val nx = win.x + ws.width * (0.12f + 0.54f * turn)
        drawLine(c.signal, Offset(nx, win.y - 4.dp.toPx()), Offset(nx, win.y + ws.height + 4.dp.toPx()), 2.5.dp.toPx())
        // the knob: ridged, graphite, its pointer in the accent; five ticks over its upper half
        val ctr = Offset(w * 0.5f, h * 0.67f); val r = h * 0.2f
        knobTicks().forEach { deg ->
            val a = Math.toRadians(deg.toDouble())
            val dx = kotlin.math.cos(a).toFloat(); val dy = kotlin.math.sin(a).toFloat()
            drawLine(c.muted, Offset(ctr.x + dx * r * 1.3f, ctr.y + dy * r * 1.3f), Offset(ctr.x + dx * r * 1.48f, ctr.y + dy * r * 1.48f), 1.5.dp.toPx())
        }
        val pointer = -200f + (KNOB_POINTER_DEG + 200f) * turn
        rotate(pointer - KNOB_POINTER_DEG, pivot = ctr) {
            val edge = Path()
            knobEdge(ctr.x, ctr.y, r, ridges = 28, depth = r * 0.055f).forEachIndexed { k, (x, y) -> if (k == 0) edge.moveTo(x, y) else edge.lineTo(x, y) }
            edge.close()
            drawPath(edge, c.text)
            drawCircle(lerp(c.text, c.panel, 0.18f), r * 0.78f, ctr)
            val a = Math.toRadians(KNOB_POINTER_DEG.toDouble())
            val dx = kotlin.math.cos(a).toFloat(); val dy = kotlin.math.sin(a).toFloat()
            drawLine(c.signal, Offset(ctr.x + dx * r * 0.18f, ctr.y + dy * r * 0.18f), Offset(ctr.x + dx * r * 0.7f, ctr.y + dy * r * 0.7f), r * 0.13f, StrokeCap.Butt)
        }
        // the pilot light comes on once it is tuned
        drawCircle(lerp(c.muted.copy(alpha = 0.4f), c.signal, lit), h * 0.035f, Offset(w * 0.9f, h * 0.88f))
    }
}

/** Step 1: the sweep runs across the dial from 20 Hz to 20 kHz, and the stock response draws in behind the needle. */
@Composable
private fun MeasureArt(ms: () -> Float) {
    val c = At.c
    Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Canvas(Modifier.fillMaxWidth().height(36.dp)) {
            val sweep = playPhase(ms(), 200, 1600)
            drawRoundRect(c.bg, Offset.Zero, size, CornerRadius(2.dp.toPx()))
            val pad = 10.dp.toPx(); val w = size.width - 2 * pad; val h = size.height
            for (dec in 0..2) for (k in 2..10) {
                val f = k * Math.pow(10.0, dec + 1.0)
                if (f < 20 || f > 20000) continue
                val x = pad + w * (Math.log10(f / 20.0) / 3.0).toFloat()
                val major = k == 10 || (dec == 0 && k == 2)
                drawLine(c.muted.copy(alpha = if (major) 0.9f else 0.45f), Offset(x, h * 0.2f), Offset(x, h * if (major) 0.6f else 0.4f), 1.dp.toPx())
            }
            // a log sweep moves evenly across a log dial
            if (sweep > 0f && sweep < 1f) drawLine(c.signal, Offset(pad + w * sweep, 0f), Offset(pad + w * sweep, h), 2.5.dp.toPx())
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("20", "200", "2k", "20k").forEach { Text(it, style = MaterialTheme.typography.labelSmall, color = c.muted) }
        }
        Spacer(Modifier.height(6.dp))
        Canvas(Modifier.fillMaxWidth().weight(1f)) {
            val sweep = playPhase(ms(), 200, 1600)
            val pad = 10.dp.toPx(); val w = size.width - 2 * pad
            val top = 4.dp.toPx(); val bottom = size.height - 4.dp.toPx()
            chartPaper(pad, w, top, bottom, c.line)
            drawLine(c.target, Offset(pad, yOf(0f, top, bottom)), Offset(pad + w, yOf(0f, top, bottom)), 1.5.dp.toPx())
            // the stock reading is drawn dashed, as in every chart
            if (sweep > 0f) drawPath(
                responsePath(pad, w, top, bottom, tuned = 0f, x1 = sweep), c.muted,
                style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))),
            )
        }
    }
}

/** Step 2: the faders of a graphic EQ slide from 0 dB to the suggested EQ, then its curve shows in the accent. */
@Composable
private fun EqArt(ms: () -> Float) {
    val c = At.c
    Canvas(Modifier.fillMaxSize()) {
        val t = ms()
        val w = size.width; val h = size.height
        val hair = 1.dp.toPx()
        val top = h * 0.14f; val bottom = h * 0.86f; val mid = (top + bottom) / 2; val half = (bottom - top) / 2
        val r2 = CornerRadius(2.dp.toPx())
        val slotInk = Color.Black.copy(alpha = if (c.dark) 0.6f else 0.45f)
        // 0 dB across the strip
        drawLine(c.line, Offset(w * 0.04f, mid), Offset(w * 0.96f, mid), hair)
        val caps = (0 until TUTORIAL_FADERS).map { k ->
            val x = w * (0.06f + 0.88f * tutorialFaderX(k))
            val y = mid - half * (tutorialFaderDb(k) / 8f) * ease(t, 100 + k * 90, 600)
            Offset(x, y)
        }
        caps.forEach { (x, y) ->
            for (s in 0..4) { val ty = top + (bottom - top) * s / 4f; drawLine(c.line, Offset(x - w * 0.045f, ty), Offset(x - w * 0.027f, ty), hair) }
            drawRoundRect(slotInk, Offset(x - w * 0.008f, top), Size(w * 0.016f, bottom - top), CornerRadius(w * 0.008f))
            drawRoundRect(c.text, Offset(x - w * 0.04f, y - h * 0.05f), Size(w * 0.08f, h * 0.1f), r2)
            drawLine(c.bg, Offset(x - w * 0.028f, y), Offset(x + w * 0.028f, y), hair * 1.5f)
        }
        // the EQ's curve through the caps, once they have settled
        val show = playPhase(t, 1050, 400)
        if (show > 0f) {
            val path = Path()
            path.moveTo(w * 0.03f, caps.first().y)
            path.lineTo(caps.first().x, caps.first().y)
            for (k in 1 until caps.size) {
                val a = caps[k - 1]; val b = caps[k]
                val mx = (a.x + b.x) / 2
                path.cubicTo(mx, a.y, mx, b.y, b.x, b.y)
            }
            path.lineTo(w * 0.97f, caps.last().y)
            drawPath(path, c.signal.copy(alpha = show), style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
        }
    }
}

/** Step 3: measured again with the EQ on, the response settles onto the target and the reading counts down. */
@Composable
private fun VerifyArt(ms: () -> Float) {
    val c = At.c
    val p = ease(ms(), 200, 1400)
    val run = runColor(1)
    Column(Modifier.fillMaxSize().padding(14.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text("%.1f".format(tutorialReadingDb(p)), style = MaterialTheme.typography.displayMedium, color = c.text, maxLines = 1)
            Text(" dB", Modifier.padding(bottom = 8.dp), style = MaterialTheme.typography.labelSmall, color = c.muted)
        }
        Text("from target", style = MaterialTheme.typography.labelSmall, color = c.muted)
        Spacer(Modifier.height(8.dp))
        Canvas(Modifier.fillMaxWidth().weight(1f)) {
            val w = size.width
            val top = 2.dp.toPx(); val bottom = size.height - 2.dp.toPx()
            chartPaper(0f, w, top, bottom, c.line)
            drawLine(c.target, Offset(0f, yOf(0f, top, bottom)), Offset(w, yOf(0f, top, bottom)), 1.5.dp.toPx())
            drawPath(
                responsePath(0f, w, top, bottom, tuned = 0f), c.muted.copy(alpha = 0.7f),
                style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))),
            )
            drawPath(responsePath(0f, w, top, bottom, tuned = p), run, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
        }
    }
}

/** Step 4: the car comes in over Bluetooth, and the EQ key latches down with its pilot lit: the EQ is on. */
@Composable
private fun KeepArt(ms: () -> Float) {
    val c = At.c
    val letters = androidx.compose.ui.text.rememberTextMeasurer()
    val label = MaterialTheme.typography.labelSmall
    Canvas(Modifier.fillMaxSize()) {
        val t = ms()
        val w = size.width; val h = size.height
        val hair = 1.dp.toPx()
        val arrive = ease(t, 0, 500)
        val link = ease(t, 400, 450)
        val latch = ease(t, 850, 140)
        val lit = playPhase(t, 900, 160)
        val icon = 40.dp.toPx()
        val cy = h * 0.5f
        // the car, sliding in from the start
        val carX = w * 0.08f - (1f - arrive) * 24.dp.toPx()
        translate(carX, cy - icon / 2) { drawIcAt(Ic.CAR, c.text.copy(alpha = arrive), icon) }
        // the key: a big latching push key, the EQ's faders on its face, lettered off and then on
        val kw = w * 0.42f; val kh = h * 0.36f
        val kx = w * 0.52f; val ky = cy - kh / 2 + 3.dp.toPx() * latch
        // the Bluetooth link drawing across to the key
        val from = carX + icon + 8.dp.toPx(); val to = kx - 8.dp.toPx()
        if (link > 0f) {
            drawLine(
                c.muted, Offset(from, cy), Offset(from + (to - from) * link, cy), 2.dp.toPx(), StrokeCap.Round,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 5.dp.toPx())),
            )
            val bt = 20.dp.toPx()
            translate((from + to) / 2 - bt / 2, cy - bt - 10.dp.toPx()) { drawIcAt(Ic.BLUETOOTH, c.text.copy(alpha = link), bt) }
        }
        val shape = CornerRadius(3.dp.toPx())
        drawRoundRect(lerp(c.panelHigh, lerp(c.panelHigh, c.line, 0.55f), latch), Offset(kx, ky), Size(kw, kh), shape)
        if (latch > 0f) drawRoundRect(
            Brush.verticalGradient(0f to Color.Black.copy(alpha = (if (c.dark) 0.45f else 0.2f) * latch), 0.45f to Color.Transparent, startY = ky, endY = ky + kh),
            Offset(kx, ky), Size(kw, kh), shape,
        )
        drawRoundRect(c.line, Offset(kx, ky), Size(kw, kh), shape, style = Stroke(hair))
        drawCircle(lerp(c.muted.copy(alpha = 0.4f), c.signal, lit), 5.dp.toPx(), Offset(kx + 16.dp.toPx(), ky + kh / 2))
        val eq = 26.dp.toPx()
        translate(kx + (kw - eq) / 2, ky + (kh - eq) / 2) { drawIcAt(Ic.EQ, c.text, eq) }
        val word = letters.measure(if (latch >= 0.5f) "on" else "off", label)
        drawText(word, color = if (latch >= 0.5f) c.text else c.muted, topLeft = Offset(kx + kw - word.size.width - 12.dp.toPx(), ky + (kh - word.size.height) / 2))
    }
}

/**
 * The companion: AutoTune on a TV (or PC) between two speakers. The TV shows its pairing code, the phone at the seat
 * links to it, then each speaker plays in turn (lit red, rings spreading) and the TV's EQ comes on.
 */
@Composable
private fun TvArt(ms: () -> Float) {
    val c = At.c
    val letters = androidx.compose.ui.text.rememberTextMeasurer()
    val codeStyle = MaterialTheme.typography.titleMedium.copy(letterSpacing = androidx.compose.ui.unit.TextUnit(4f, androidx.compose.ui.unit.TextUnitType.Sp))
    val label = MaterialTheme.typography.labelSmall
    Canvas(Modifier.fillMaxSize()) {
        val t = ms()
        val w = size.width; val h = size.height
        val hair = 1.dp.toPx()
        val screenInk = Color(0xFF161615)
        val arrive = ease(t, 0, 400)
        // the TV: a dark screen on a thin foot
        val tvL = w * 0.3f; val tvT = h * 0.1f + (1f - arrive) * 10.dp.toPx(); val tvW = w * 0.4f; val tvH = h * 0.36f
        drawRoundRect(screenInk.copy(alpha = arrive), Offset(tvL, tvT), Size(tvW, tvH), CornerRadius(3.dp.toPx()))
        drawLine(c.muted.copy(alpha = arrive), Offset(w * 0.5f, tvT + tvH), Offset(w * 0.5f, tvT + tvH + h * 0.05f), 2.dp.toPx())
        drawLine(c.muted.copy(alpha = arrive), Offset(w * 0.44f, tvT + tvH + h * 0.05f), Offset(w * 0.56f, tvT + tvH + h * 0.05f), 2.dp.toPx())
        // its pairing code, a digit at a time; once paired the screen shows the link's pilot
        val digits = tutorialTvDigits(t)
        if (digits > 0 && t < 1000f) {
            val code = letters.measure(TUTORIAL_TV_CODE.take(digits).padEnd(TUTORIAL_TV_CODE.length, ' '), codeStyle)
            drawText(code, color = Color(0xFFECEAE4), topLeft = Offset(tvL + (tvW - code.size.width) / 2, tvT + (tvH - code.size.height) / 2))
        }
        val eqOn = playPhase(t, 2000, 200)
        if (t >= 1000f) {
            val word = letters.measure("eq", label)
            val wx = tvL + (tvW - word.size.width) / 2 + 6.dp.toPx(); val wy = tvT + (tvH - word.size.height) / 2
            drawCircle(lerp(Color(0xFF5E5C57), c.signal, eqOn), 4.dp.toPx(), Offset(wx - 9.dp.toPx(), tvT + tvH / 2))
            drawText(word, color = Color(0xFFA3A19A), topLeft = Offset(wx, wy))
        }
        // two speaker cabinets, each with a dot grille and its pilot; the playing one is lit with rings
        val playing = tutorialTvPlaying(t)
        listOf(w * 0.12f, w * 0.88f).forEachIndexed { k, cx ->
            val sw = w * 0.13f; val sh = h * 0.5f; val top = h * 0.14f
            val left = cx - sw / 2
            if (playing == k) {
                val since = t - (1000f + 500f * k)
                for (ring in 0..1) {
                    val p = (((since - ring * 200f) / 500f)).coerceIn(0f, 1f)
                    if (p > 0f && p < 1f) drawCircle(c.signal.copy(alpha = 0.5f * (1f - p)), sw * (0.6f + 1.4f * p), Offset(cx, top + sh * 0.55f), style = Stroke(1.5.dp.toPx()))
                }
            }
            drawRoundRect(c.panelHigh.copy(alpha = arrive), Offset(left, top), Size(sw, sh), CornerRadius(4.dp.toPx()))
            drawRoundRect(c.line.copy(alpha = arrive), Offset(left, top), Size(sw, sh), CornerRadius(4.dp.toPx()), style = Stroke(hair))
            for (row in 0..5) for (col in 0..2) {
                drawCircle(c.text.copy(alpha = 0.35f * arrive), sw * 0.05f, Offset(left + sw * (0.28f + 0.22f * col), top + sh * (0.3f + 0.11f * row)))
            }
            drawCircle(if (playing == k) c.signal else c.muted.copy(alpha = 0.4f * arrive), sw * 0.07f, Offset(cx, top + sh * 0.13f))
        }
        // the phone at the seat, linking to the TV once the code is typed
        val link = ease(t, 800, 200)
        val pw = w * 0.07f; val ph = h * 0.2f; val px = w * 0.5f - pw / 2; val py = h * 0.74f
        drawRoundRect(c.text.copy(alpha = arrive), Offset(px, py), Size(pw, ph), CornerRadius(3.dp.toPx()))
        drawCircle(c.panel, pw * 0.12f, Offset(w * 0.5f, py + ph * 0.15f))
        if (link > 0f) {
            val from = py - 4.dp.toPx(); val to = tvT + tvH + h * 0.08f
            drawLine(
                c.muted, Offset(w * 0.5f, from), Offset(w * 0.5f, from + (to - from) * link), 2.dp.toPx(), StrokeCap.Round,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 5.dp.toPx())),
            )
        }
    }
}

/** Live: the spectrum's bars rise and move with the music, each leaving its held peak as a cap; the level beside them. */
@Composable
private fun LiveArt(ms: () -> Float) {
    val c = At.c
    Canvas(Modifier.fillMaxSize()) {
        val t = ms()
        val w = size.width; val h = size.height
        val pad = 14.dp.toPx()
        val top = h * 0.12f; val bottom = h * 0.86f
        val levelW = w * 0.06f
        val area = w - 2 * pad - levelW - 12.dp.toPx()
        val n = TUTORIAL_LIVE_BARS
        val step = area / n; val bw = step * 0.62f
        val r2 = CornerRadius(1.5.dp.toPx())
        for (y in listOf(0.25f, 0.5f, 0.75f)) drawLine(c.line.copy(alpha = 0.5f), Offset(pad, top + (bottom - top) * y), Offset(pad + area, top + (bottom - top) * y), 1.dp.toPx())
        var loud = 0f
        for (k in 0 until n) {
            val x = pad + step * k + (step - bw) / 2
            val v = tutorialLiveBar(k, t); val peak = tutorialLivePeak(k, t)
            loud = maxOf(loud, v)
            val bh = (bottom - top) * v
            drawRoundRect(c.text.copy(alpha = 0.8f), Offset(x, bottom - bh), Size(bw, bh), r2)
            if (peak > 0f) {
                val py = bottom - (bottom - top) * peak
                drawRect(c.signal, Offset(x, py - 2.dp.toPx()), Size(bw, 2.dp.toPx()))
            }
        }
        // the level: one bar at the end, the loudest band's reach
        val lx = w - pad - levelW
        drawRoundRect(c.line.copy(alpha = 0.6f), Offset(lx, top), Size(levelW, bottom - top), r2)
        val lh = (bottom - top) * loud
        drawRoundRect(c.signal, Offset(lx, bottom - lh), Size(levelW, lh), r2)
    }
}
