package dev.autotune.ui.room

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import dev.autotune.dsp.Layout
import dev.autotune.dsp.Speaker
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

// Plain Compose drawing with no Android parts, so the Windows module's screenshot tool can render it too.

/** The colours the room is drawn in. */
class RoomColors(
    val floor: Color, val floorLine: Color, val ink: Color, val muted: Color,
    val face: Color, val side: Color, val live: Color, val onLive: Color, val screen: Color,
)

/** What one speaker shows: drawn faint when [silent], its [note] under its name. */
class RoomSpeaker(val speaker: Speaker, val note: String? = null, val silent: Boolean = false)

/** The width of the room's drawing over its height: the room is wide and seen low. */
const val ROOM_ASPECT = 1.75f

/**
 * The room seen from just behind the seat, low and straight on (the owner's pick, "C, from the seat", 2026-11-10):
 * the floor runs away to the screen wall, each speaker stands on it as a small cabinet with rounded edges turned to
 * the seat, and the one [playing] lights in the accent with rings spreading over the floor, wider with [level]
 * (0..1; null pulses evenly). [phase] (0..1) moves the rings.
 */
object Room3d {
    /** The seat, in [Speaker.x] / [Speaker.y] units. */
    const val SEAT_X = 0f
    const val SEAT_Y = 0.62f

    private const val DEPTH = 1.25 // the room is a little deeper than it is wide
    private const val PITCH_DEG = 31.0
    private const val DIST = 2.8
    private const val FOCAL = 2.2

    private class P3(val x: Float, val y: Float, val d: Double)

    /** Projects a point on the room's plan (x -1..1, y 0..1, height z) to the unit view, with its depth. */
    private fun project(x: Double, y: Double, z: Double): P3 {
        val th = PITCH_DEG * PI / 180
        val ty = DEPTH * 0.5
        val camY = ty + DIST * cos(th)
        val camZ = DIST * sin(th)
        val vx = x
        val vy = y * DEPTH - camY
        val vz = z - camZ
        val yc = vy * -sin(th) + vz * cos(th)
        val zc = vy * -cos(th) + vz * -sin(th)
        return P3((FOCAL * vx / zc).toFloat(), (-FOCAL * yc / zc).toFloat(), zc)
    }

    private class Box(val s: Speaker, val w: Double, val d: Double, val h: Double, val angle: Double)

    private fun boxOf(s: Speaker): Box {
        val centre = s == Speaker.C
        val w = if (s.sub) 0.17 else if (centre) 0.24 else 0.13
        val d = if (s.sub) 0.14 else 0.08
        val h = if (s.sub) 0.17 else if (centre) 0.09 else 0.24
        // every cabinet turns to face the seat
        val a = atan2((SEAT_X - s.x).toDouble(), (SEAT_Y - s.y) * DEPTH)
        return Box(s, w, d, h, a)
    }

    /** [all]: every speaker plays at once (the last sweep of the system test): all lit, the rings from the seat. */
    fun DrawScope.drawRoom(
        layout: Layout, playing: Speaker?, speakers: Map<Speaker, RoomSpeaker>, colors: RoomColors,
        text: TextMeasurer, nameStyle: TextStyle, noteStyle: TextStyle, phase: Float, level: Float?, all: Boolean = false,
    ) {
        val w = size.width
        val h = size.height
        // fit the floor and the screen into the canvas: the names of the near speakers fall on the floor itself
        val corners = listOf(project(-1.0, 0.0, 0.0), project(1.0, 0.0, 0.0), project(-1.0, 1.0, 0.0), project(1.0, 1.0, 0.0), project(0.0, 0.0, 0.42))
        val minX = corners.minOf { it.x }; val maxX = corners.maxOf { it.x }
        val minY = corners.minOf { it.y }; val maxY = corners.maxOf { it.y }
        val s = min(w * 0.94f / (maxX - minX), h * 0.88f / (maxY - minY))
        val ox = w / 2 - (minX + maxX) / 2 * s
        val oy = h * 0.5f - (minY + maxY) / 2 * s
        fun at(x: Double, y: Double, z: Double): Offset { val p = project(x, y, z); return Offset(ox + p.x * s, oy + p.y * s) }
        val hair = max(1f, min(w, h) / 300f)

        // the floor, with three lines across it for depth
        val floor = listOf(at(-1.0, 0.0, 0.0), at(1.0, 0.0, 0.0), at(1.0, 1.0, 0.0), at(-1.0, 1.0, 0.0))
        drawPath(rounded(floor, hair * 6), colors.floor)
        drawPath(rounded(floor, hair * 6), colors.floorLine, style = Stroke(hair))
        for (k in 1..3) drawLine(colors.floorLine.copy(alpha = 0.5f), at(-1.0, k / 4.0, 0.0), at(1.0, k / 4.0, 0.0), hair * 0.6f)
        // the screen on the front wall
        drawPath(rounded(listOf(at(-0.3, 0.0, 0.2), at(0.3, 0.0, 0.2), at(0.3, 0.0, 0.4), at(-0.3, 0.0, 0.4)), hair * 3), colors.screen.copy(alpha = 0.88f))
        // sound spreading over the floor from the speaker that plays
        val source = if (all) SEAT_X to SEAT_Y else playing?.takeIf { it in layout.speakers }?.let { it.x to it.y }
        if (source != null) {
            val reach = 0.14 + 0.26 * (level ?: 0.7f).coerceIn(0f, 1f)
            for (k in 0 until 3) {
                val t = (phase + k / 3f) % 1f
                val r = 0.08 + t * reach
                val ring = (0..48).map { i -> val a = i / 48.0 * 2 * PI; at(source.first + r * cos(a), source.second + r * sin(a) / DEPTH, 0.0) }
                drawPath(poly(ring), colors.live.copy(alpha = (1f - t) * 0.75f), style = Stroke(hair * 1.8f))
            }
        }

        // the speakers and the sofa, far ones first
        class Item(val depth: Double, val draw: () -> Unit)
        val items = ArrayList<Item>()
        for (b in layout.speakers.map(::boxOf)) items += Item(project(b.s.x.toDouble(), b.s.y.toDouble(), 0.0).d) {
            drawCabinet(b, all || b.s == playing, speakers[b.s]?.silent == true, colors, hair, ::at)
        }
        // the sofa at the seat, its back to us: a seat, a back and two arms, plain and rounded
        val sofa = listOf(
            Block(SEAT_X.toDouble(), SEAT_Y + 0.01, 0.0, 0.40, 0.13, 0.055, 0.0),
            Block(SEAT_X.toDouble(), SEAT_Y + 0.085, 0.0, 0.40, 0.05, 0.12, 0.0),
            Block(SEAT_X - 0.215, SEAT_Y + 0.03, 0.0, 0.05, 0.16, 0.085, 0.0),
            Block(SEAT_X + 0.215, SEAT_Y + 0.03, 0.0, 0.05, 0.16, 0.085, 0.0),
        )
        items += Item(project(SEAT_X.toDouble(), SEAT_Y.toDouble(), 0.0).d) {
            for (part in sofa.sortedByDescending { project(it.x, it.y, 0.0).d }) {
                drawBlock(part, colors.side, colors.face, colors.muted, hair, ::at) { _, _ -> }
            }
        }
        items.sortedByDescending { it.depth }.forEach { it.draw() }

        // each speaker's name on the floor in front of it, and its note under the name (over everything)
        for (b in layout.speakers.map(::boxOf)) {
            val live = all || b.s == playing
            val faint = speakers[b.s]?.silent == true
            val ca = cos(b.angle); val sa = sin(b.angle)
            val hd = b.d / 2
            val foot = at(b.s.x + (hd + 0.05) * sa, b.s.y + ((hd + 0.05) * ca) / DEPTH, 0.0)
            val name = text.measure(b.s.short, nameStyle.copy(color = if (live) colors.live else if (faint) colors.muted else colors.ink))
            var y = foot.y + hair * 2
            drawText(name, topLeft = Offset(foot.x - name.size.width / 2f, y))
            y += name.size.height
            speakers[b.s]?.note?.split('\n')?.forEach { line ->
                val t = text.measure(line, noteStyle.copy(color = if (live) colors.live else colors.muted))
                drawText(t, topLeft = Offset((foot.x - t.size.width / 2f).coerceIn(hair * 4, w - t.size.width - hair * 4), y))
                y += t.size.height
            }
        }
    }

    /** A box on the floor plan: centre [x], [y], bottom [z], size [w] across, [d] deep, [h] high, turned by [angle]. */
    private class Block(val x: Double, val y: Double, val z: Double, val w: Double, val d: Double, val h: Double, val angle: Double)

    /**
     * Draws [b] with soft rounded edges: sides in [side], the top in [top], a hairline [edge]; far sides first.
     * [onFront] draws on the side turned toward +v (the seat for a speaker) with a point on it: (u across, z up).
     */
    private fun DrawScope.drawBlock(
        b: Block, side: Color, top: Color, edge: Color, hair: Float, at: (Double, Double, Double) -> Offset,
        front: Color = side, onFront: (((Double, Double) -> Offset), Float) -> Unit,
    ) {
        val ca = cos(b.angle); val sa = sin(b.angle)
        fun corner(u: Double, v: Double, z: Double) = at(b.x + u * ca + v * sa, b.y + (-u * sa + v * ca) / DEPTH, z)
        val hw = b.w / 2; val hd = b.d / 2
        val us = listOf(-hw, hw, hw, -hw); val vs = listOf(-hd, -hd, hd, hd)
        val bot = (0 until 4).map { corner(us[it], vs[it], b.z) }
        val tp = (0 until 4).map { corner(us[it], vs[it], b.z + b.h) }
        val round = min(
            hypot((bot[1].x - bot[0].x).toDouble(), (bot[1].y - bot[0].y).toDouble()).toFloat(),
            hypot((tp[0].x - bot[0].x).toDouble(), (tp[0].y - bot[0].y).toDouble()).toFloat(),
        ) * 0.28f
        fun depth(i: Int): Double {
            val j = (i + 1) % 4
            return (project(b.x + (us[i] + us[j]) / 2 * ca + (vs[i] + vs[j]) / 2 * sa, b.y + (-(us[i] + us[j]) / 2 * sa + (vs[i] + vs[j]) / 2 * ca) / DEPTH, b.z + b.h / 2)).d
        }
        for (i in (0 until 4).sortedByDescending(::depth)) {
            val j = (i + 1) % 4
            val pts = listOf(bot[i], bot[j], tp[j], tp[i])
            val isFront = i == 2
            drawPath(rounded(pts, round), if (isFront) front else side)
            drawPath(rounded(pts, round), edge, style = Stroke(hair))
            if (isFront) onFront({ u, z -> corner(u, hd, b.z + z) }, round)
        }
        drawPath(rounded(tp, round), top)
        drawPath(rounded(tp, round), edge, style = Stroke(hair))
    }

    /**
     * A speaker after Braun's small devices: a light cabinet with soft edges, a grille of dots on the face turned to the
     * seat (a round field on the sub) and a small pilot dot that lights in the accent, with the grille, while it plays.
     */
    private fun DrawScope.drawCabinet(b: Box, live: Boolean, faint: Boolean, colors: RoomColors, hair: Float, at: (Double, Double, Double) -> Offset) {
        val edge = if (faint) colors.floorLine else colors.muted
        val dots = when { live -> colors.live; faint -> colors.floorLine; else -> colors.ink.copy(alpha = 0.55f) }
        drawBlock(Block(b.s.x.toDouble(), b.s.y.toDouble(), 0.0, b.w, b.d, b.h, b.angle), colors.side, colors.face, edge, hair, at, front = colors.face) { face, _ ->
            val r = (hair * 0.9f).coerceAtLeast(0.8f)
            val gap = 0.016
            fun dot(u: Double, z: Double) = drawCircle(dots, r, face(u, z))
            when {
                b.s.sub -> {
                    // a round field of dots, as on a Braun fan or heater
                    val cz = b.h * 0.5; val rad = min(b.w, b.h) * 0.36
                    var u = -rad
                    while (u <= rad) { var z = -rad; while (z <= rad) { if (u * u + z * z <= rad * rad) dot(u, cz + z); z += gap }; u += gap }
                }
                else -> {
                    // a rectangle of dots over most of the face, the pilot dot under it
                    val uMax = b.w / 2 - 0.022
                    val zLo = b.h * (if (b.s == Speaker.C) 0.3 else 0.22); val zHi = b.h - 0.02
                    var u = -uMax
                    while (u <= uMax + 1e-9) { var z = zLo; while (z <= zHi + 1e-9) { dot(u, z); z += gap }; u += gap }
                }
            }
            // the pilot: the one accent, lit only while this speaker plays
            val pilotZ = if (b.s.sub) b.h * 0.12 else b.h * 0.1
            drawCircle(if (live) colors.live else colors.floorLine, r * 2.6f, face(b.w / 2 - 0.03, pilotZ))
        }
    }

    private fun poly(pts: List<Offset>): Path = Path().apply {
        pts.forEachIndexed { i, p -> if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) }
    }

    /** A closed shape through [pts] with each corner rounded off by up to [r]: no sharp corners anywhere. */
    fun rounded(pts: List<Offset>, r: Float): Path {
        val n = pts.size
        val path = Path()
        for (i in 0 until n) {
            val prev = pts[(i - 1 + n) % n]; val p = pts[i]; val next = pts[(i + 1) % n]
            val a = toward(p, prev, r)
            val b = toward(p, next, r)
            if (i == 0) path.moveTo(a.x, a.y) else path.lineTo(a.x, a.y)
            path.cubicTo(p.x, p.y, p.x, p.y, b.x, b.y)
        }
        path.close()
        return path
    }

    /** The point [r] along from [p] toward [q], never past half way. */
    private fun toward(p: Offset, q: Offset, r: Float): Offset {
        val dx = q.x - p.x; val dy = q.y - p.y
        val len = hypot(dx.toDouble(), dy.toDouble()).toFloat()
        if (len < 1e-3f) return p
        val k = min(r, len / 2) / len
        return Offset(p.x + dx * k, p.y + dy * k)
    }
}
