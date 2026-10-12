package com.neonrush.game.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Game events the premium hulls react to. The ViewModel sets these to the current tick
 * when the event happens (see PATCHES.md). Hulls read the age = tick - eventTick.
 * Defaults are far in the past so nothing fires until a real event.
 */
object HullFx {
    @Volatile var closeCallTick = -9999
    @Volatile var gemTick = -9999
    @Volatile var zoneTick = -9999
    @Volatile var reviveTick = -9999
    @Volatile var bossTick = -9999
    @Volatile var zone = 1
    /** Pro Chroma recolor: hullId -> variant 0..3 (0 = original). Hue-rotated 90 degrees per step. */
    @Volatile var chroma: Map<String, Int> = emptyMap()
    /** Hull Mastery level per hull (0..5), shown as small pips under the ship. */
    @Volatile var mastery: Map<String, Int> = emptyMap()
    fun reset() { closeCallTick = -9999; gemTick = -9999; zoneTick = -9999; reviveTick = -9999; bossTick = -9999; zone = 1 }
}

private fun DrawScope.pGlow(c: Offset, radius: Float, color: Color, alpha: Float) {
    drawCircle(
        brush = Brush.radialGradient(listOf(color.copy(alpha = alpha), Color.Transparent), center = c, radius = radius),
        radius = radius, center = c
    )
}

private fun age(tick: Int, eventTick: Int) = tick - eventTick

/** Layered flame tongue: outer / mid / hot core, pointing up from [base]. */
private fun DrawScope.flameSpire(base: Offset, w: Float, tip: Float, sway: Float, outer: Color, mid: Color, core: Color) {
    for (layer in 0..2) {
        val k = 1f - layer * 0.36f
        val c = when (layer) { 0 -> outer; 1 -> mid; else -> core }
        val p = Path().apply {
            moveTo(base.x - w * k, base.y)
            quadraticBezierTo(base.x - w * 0.9f * k, base.y - tip * k * 0.55f, base.x + sway * k, base.y - tip * k)
            quadraticBezierTo(base.x + w * 0.9f * k, base.y - tip * k * 0.55f, base.x + w * k, base.y)
            close()
        }
        drawPath(p, c)
    }
}

/** Soft cone of fire / energy pointing right from [from]. */
private fun DrawScope.breathCone(from: Offset, len: Float, spread: Float, flick: Float) {
    val cols = listOf(Color(0xFFFF2BD6), Color(0xFFFFEE58), Color(0xFFFFFFFF))
    for (layer in 0..2) {
        val k = 1f - layer * 0.3f
        val p = Path().apply {
            moveTo(from.x, from.y - spread * 0.25f * k)
            quadraticBezierTo(from.x + len * 0.5f * k, from.y - spread * k, from.x + len * k, from.y + flick * k)
            quadraticBezierTo(from.x + len * 0.5f * k, from.y + spread * k, from.x, from.y + spread * 0.25f * k)
            close()
        }
        drawPath(p, cols[layer].copy(alpha = 0.55f + layer * 0.15f))
    }
}

/**
 * Premium hulls (aura + trail + event reaction), all drawn in code.
 * Returns true when the hull was handled. Called first from drawExtraHull.
 */
private fun DrawScope.drawPremiumHullRaw(hullId: String, x: Float, y: Float, h: Float, tick: Int, speedLevel: Int): Boolean {
    val u = h * 0.06f
    val t = tick.toFloat()
    val n = 8 + speedLevel * 2
    val pi = PI.toFloat()
    val ship = Offset(x, y)
    when (hullId) {

        "aurora_serpent" -> {
            val m = n + 6
            for (side in 0..1) {
                var prev = Offset(x - h * 0.15f, y + h * 0.05f)
                for (i in 1..m) {
                    val f = i / (m + 1f)
                    val amp = u * (0.5f + f * 3f)
                    val next = Offset(x - h * 0.15f - i * u * 1.5f, y + h * 0.05f + sin(t * 0.2f + i * 0.4f + side * 2.2f) * amp)
                    val col = lerp(Color(0xFF39FFA0), Color(0xFFB266FF), f)
                    drawLine(col.copy(alpha = 0.8f * (1f - f)), prev, next, strokeWidth = (u * (1.2f - 0.8f * f)).coerceAtLeast(1.5f))
                    prev = next
                }
            }
            for (k in 0 until 6) {
                val ph = (t * 0.03f + k / 6f) % 1f
                drawCircle(Color.White.copy(alpha = (1f - ph) * 0.8f), u * 0.3f, Offset(x - h * 0.2f - ph * h * 1.1f, y + sin(k * 2.3f) * h * 0.35f))
            }
            val a = age(tick, HullFx.closeCallTick)
            if (a in 0..18) drawCircle(Color(0xFF7DFFD0).copy(alpha = 1f - a / 18f), h * 0.3f + a * u * 0.6f, ship, style = Stroke(3f))
        }

        "supernova_heart" -> {
            val beat = 0.5f + 0.5f * sin(t * (0.2f + speedLevel * 0.02f))
            pGlow(ship, h * (0.45f + 0.12f * beat), Color(0xFFFFB300), 0.25f + 0.2f * beat)
            for (k in 0 until 5) {
                val ph = (t * 0.04f + k / 5f) % 1f
                drawCircle(Color(0xFFFFE082).copy(alpha = (1f - ph) * 0.7f), h * 0.12f + ph * h * 0.5f,
                    Offset(x - h * 0.15f - ph * h * 1.2f, y + h * 0.05f), style = Stroke(2.5f))
            }
            val a = age(tick, HullFx.gemTick)
            if (a in 0..14) {
                val f = a / 14f
                for (k in 0 until 8) {
                    val ang = k * pi / 4f
                    drawLine(Color(0xFFFFD23F).copy(alpha = 1f - f), ship + Offset(cos(ang), sin(ang)) * (h * 0.3f * f),
                        ship + Offset(cos(ang), sin(ang)) * (h * (0.3f + 0.35f * f)), strokeWidth = 3f)
                }
            }
        }

        "neon_wyrm" -> {
            val segs = 10 + speedLevel
            val cc = age(tick, HullFx.closeCallTick)
            val boss = age(tick, HullFx.bossTick)
            val gem = age(tick, HullFx.gemTick)
            val flap = sin(t * 0.28f)
            pGlow(ship, h * 0.75f, Color(0xFF00FFC8), 0.16f)
            // membrane wings, flapping behind the body
            for (side in intArrayOf(-1, 1)) {
                val root = Offset(x - h * 0.22f, y + h * 0.02f)
                val mid = Offset(x - h * 0.34f, y + side * h * (0.42f + 0.10f * flap))
                val tip = Offset(x - h * 0.62f, y + side * h * (0.58f + 0.14f * flap))
                val low = Offset(x - h * 0.8f, y + side * h * (0.2f + 0.06f * flap))
                val wing = Path().apply {
                    moveTo(root.x, root.y); lineTo(mid.x, mid.y); lineTo(tip.x, tip.y)
                    quadraticBezierTo(x - h * 0.72f, y + side * h * 0.36f, low.x, low.y)
                    quadraticBezierTo(x - h * 0.5f, y + side * h * 0.1f, root.x, root.y)
                    close()
                }
                drawPath(wing, Brush.linearGradient(listOf(Color(0xFF00FFC8).copy(alpha = 0.55f), Color(0xFFFF2BD6).copy(alpha = 0.22f)), start = root, end = tip))
                drawPath(wing, Color(0xFF9CFFEA).copy(alpha = 0.9f), style = Stroke(2.2f))
                drawLine(Color(0xFF9CFFEA).copy(alpha = 0.7f), root, tip, 1.6f)
                drawLine(Color(0xFF9CFFEA).copy(alpha = 0.5f), root, low, 1.4f)
            }
            // armoured body: plate, rim, glow core and a dorsal spike on every segment
            val pulse = ((t * 0.45f) % (segs + 4f))
            for (i in segs downTo 1) {
                val f = i / (segs + 1f)
                val cx = x - h * 0.2f - i * u * 1.9f
                val cy = y + h * 0.05f + sin(t * 0.22f - i * 0.55f) * u * (0.8f + f * 2.4f)
                val r = u * (2.2f - f * 1.3f).coerceAtLeast(0.7f)
                val col = lerp(Color(0xFF00FFC8), Color(0xFFFF2BD6), f)
                val flash = cc in 0..18 && kotlin.math.abs(i - cc * 0.8f) < 1.7f
                drawCircle(col.copy(alpha = 0.22f * (1f - f)), r * 1.7f, Offset(cx, cy))
                val spike = Path().apply {
                    moveTo(cx - r * 0.45f, cy - r * 0.75f); lineTo(cx + r * 0.05f, cy - r * (1.9f - f * 0.5f)); lineTo(cx + r * 0.5f, cy - r * 0.75f); close()
                }
                drawPath(spike, col.copy(alpha = 0.95f * (1f - f * 0.6f)))
                drawCircle(Color(0xFF04302E).copy(alpha = 0.95f), r, Offset(cx, cy))
                drawCircle(if (flash) Color.White else col.copy(alpha = 0.95f * (1f - f * 0.5f)), r, Offset(cx, cy), style = Stroke(2.4f))
                drawArc(Color.White.copy(alpha = 0.5f * (1f - f)), 200f, 100f, false, Offset(cx - r * 0.7f, cy - r * 0.7f), Size(r * 1.4f, r * 1.4f), style = Stroke(1.6f))
                drawCircle((if (flash) Color.White else col).copy(alpha = if (kotlin.math.abs(i - pulse) < 1.2f) 1f else 0.55f), r * 0.38f, Offset(cx, cy))
            }
            // tail blade
            run {
                val i = segs + 1
                val cx = x - h * 0.2f - i * u * 1.9f
                val cy = y + h * 0.05f + sin(t * 0.22f - i * 0.55f) * u * 3.2f
                val blade = Path().apply { moveTo(cx + u, cy); lineTo(cx - u * 2.6f, cy - u * 1.4f); lineTo(cx - u * 4.2f, cy); lineTo(cx - u * 2.6f, cy + u * 1.4f); close() }
                drawPath(blade, Color(0xFFFF2BD6).copy(alpha = 0.85f))
                drawPath(blade, Color.White.copy(alpha = 0.7f), style = Stroke(1.6f))
            }
            // horned head: jaw, horns, slit eyes
            val head = Path().apply {
                moveTo(x - h * 0.18f, y - h * 0.17f); lineTo(x + h * 0.12f, y - h * 0.12f); lineTo(x + h * 0.22f, y - h * 0.05f)
                lineTo(x + h * 0.12f, y + h * 0.02f); lineTo(x - h * 0.16f, y + h * 0.05f); close()
            }
            drawPath(head, Color(0xFF04302E).copy(alpha = 0.9f))
            drawPath(head, Color(0xFF00FFC8), style = Stroke(2.4f))
            for (hs in intArrayOf(0, 1)) {
                val b = if (hs == 0) 0f else u * 1.1f
                val horn = Path().apply {
                    moveTo(x - h * 0.1f - b * 0.3f, y - h * 0.16f); lineTo(x - h * 0.3f - b, y - h * 0.36f - b * 0.4f); lineTo(x - h * 0.04f - b * 0.3f, y - h * 0.15f); close()
                }
                drawPath(horn, Color(0xFFFF2BD6).copy(alpha = 0.95f))
                drawPath(horn, Color.White.copy(alpha = 0.6f), style = Stroke(1.3f))
            }
            val eye = if ((tick / 18) % 6 == 0) 0.15f else 1f
            val ep = Offset(x - h * 0.05f, y - h * 0.09f)
            pGlow(ep, u * 2.4f, Color(0xFFFF2BD6), 0.55f * eye)
            drawCircle(Color.White.copy(alpha = eye), u * 0.6f, ep)
            drawRect(Color(0xFFFF2BD6).copy(alpha = eye), Offset(ep.x - u * 0.12f, ep.y - u * 0.5f), Size(u * 0.24f, u))
            // nostril smoke
            for (k in 0 until 3) {
                val ph = (t * 0.05f + k / 3f) % 1f
                drawCircle(Color(0xFF9CFFEA).copy(alpha = (1f - ph) * 0.4f), u * (0.5f + ph), Offset(x + h * 0.2f + ph * h * 0.2f, y - h * 0.07f - ph * h * 0.2f))
            }
            // breath: always a small flame, a huge blast when a boss appears
            val bossBlast = boss in 0..26
            val len = h * (0.35f + 0.1f * sin(t * 0.3f)) * (if (bossBlast) 2.8f else 1f)
            breathCone(Offset(x + h * 0.2f, y - h * 0.03f), len, u * (if (bossBlast) 3.4f else 1.5f), sin(t * 0.7f) * u)
            for (k in 0 until 8) {
                val ph = (t * 0.08f + k / 8f) % 1f
                drawCircle(lerp(Color(0xFFFFEE58), Color(0xFFFF2BD6), ph).copy(alpha = 1f - ph), u * 0.45f,
                    Offset(x + h * 0.22f + ph * len * 1.15f, y - h * 0.02f + sin(k * 3.1f + t * 0.3f) * u * 1.4f * ph))
            }
            if (bossBlast) drawCircle(Color(0xFFFF2BD6).copy(alpha = 1f - boss / 26f), h * 0.3f + boss * u * 0.9f, Offset(x + h * 0.2f, y), style = Stroke(4f))
            // gem pop: little diamonds around the head
            if (gem in 0..12) {
                val f = gem / 12f
                for (k in 0 until 6) {
                    val a = k * pi / 3f + t * 0.1f
                    val p = ep + Offset(cos(a), sin(a)) * (h * (0.12f + 0.3f * f))
                    drawCircle(Color(0xFFFFEE58).copy(alpha = 1f - f), u * 0.55f, p)
                }
            }
        }

        "event_horizon" -> {
            val c = ship
            for (k in 0 until 14) {
                val a = t * 0.09f + k * (2f * pi / 14f)
                val p = c + Offset(cos(a) * h * 0.55f, sin(a) * h * 0.18f)
                val col = lerp(Color(0xFFFF7043), Color(0xFFFFEB3B), (k % 7) / 7f)
                drawCircle(col.copy(alpha = if (sin(a) > 0) 0.9f else 0.45f), u * 0.5f, p)
            }
            for (k in 0 until 6) { // debris pulled in
                val ph = (t * 0.035f + k / 6f) % 1f
                val ang = k * 1.05f
                val r = h * (0.9f - 0.75f * ph)
                drawCircle(Color.White.copy(alpha = 1f - ph), u * 0.3f, c + Offset(cos(ang + ph * 3f) * r, sin(ang + ph * 3f) * r * 0.5f))
            }
            for (i in 1..n) { // red-shifted trail
                val f = i / (n + 1f)
                drawCircle(lerp(Color(0xFFFF5252), Color(0xFF4A148C), f).copy(alpha = 0.6f * (1f - f)), u * (1.4f - f),
                    Offset(x - h * 0.2f - i * u * 1.6f, y + h * 0.05f))
            }
            drawCircle(Color.Black.copy(alpha = 0.55f), h * 0.12f, c)
            val a = age(tick, HullFx.zoneTick)
            if (a in 0..20) drawCircle(Color(0xFFFFAB91).copy(alpha = 1f - a / 20f), h * 0.3f + a * u, c, style = Stroke(3f))
        }

        "crystal_phoenix" -> {
            val flap = sin(t * 0.3f)
            for (side in intArrayOf(-1, 1)) {
                val tipY = y + side * h * (0.42f + 0.12f * flap)
                val wing = Path().apply {
                    moveTo(x - h * 0.05f, y)
                    lineTo(x - h * 0.45f, tipY)
                    lineTo(x - h * 0.2f, y + side * h * 0.1f)
                    lineTo(x - h * 0.55f, y + side * h * (0.2f + 0.05f * flap))
                    close()
                }
                drawPath(wing, Color(0xFF8FE9FF).copy(alpha = 0.45f))
                drawPath(wing, Color.White.copy(alpha = 0.8f), style = Stroke(2f))
            }
            for (k in 0 until 7) {
                val ph = (t * 0.045f + k / 7f) % 1f
                val col = lerp(Color(0xFFFFC107), Color(0xFFD50000), ph)
                drawCircle(col.copy(alpha = (1f - ph) * 0.8f), u * 0.5f,
                    Offset(x - h * 0.3f - ph * h * 1.0f, y + sin(k * 2.2f + t * 0.1f) * u * 2f))
            }
            val a = age(tick, HullFx.reviveTick)
            if (a in 0..22) pGlow(ship, h * (0.4f + a * 0.05f), Color(0xFFFFF59D), 0.7f * (1f - a / 22f))
        }

        "quantum_mirage" -> {
            val hue = (HullFx.zone * 47f) % 360f
            val za = age(tick, HullFx.zoneTick)
            val cc = age(tick, HullFx.closeCallTick)
            pGlow(ship, h * 0.6f, Color.hsv(hue, 0.7f, 1f), 0.16f)
            // prism light: a spectrum fan refracting out behind the ship
            for (k in 0 until 7) {
                val col = Color.hsv((hue + k * 40f) % 360f, 0.85f, 1f)
                val spread = (k - 3) * 0.09f + sin(t * 0.1f + k) * 0.015f
                val len = h * (1.1f + 0.3f * sin(t * 0.2f + k))
                drawLine(col.copy(alpha = 0.42f), Offset(x - h * 0.2f, y + h * 0.03f), Offset(x - h * 0.2f - len, y + h * 0.03f + spread * len), strokeWidth = u * 0.55f)
            }
            // rotating wireframe cube around the ship
            run {
                val ca = t * 0.05f; val cb = t * 0.037f
                val sc = h * 0.34f
                val verts = Array(8) { idx ->
                    val vx = if ((idx and 1) == 0) -1f else 1f
                    val vy = if ((idx and 2) == 0) -1f else 1f
                    val vz = if ((idx and 4) == 0) -1f else 1f
                    val x1 = vx * cos(ca) + vz * sin(ca)
                    val z1 = -vx * sin(ca) + vz * cos(ca)
                    val y2 = vy * cos(cb) - z1 * sin(cb)
                    val z2 = vy * sin(cb) + z1 * cos(cb)
                    val pr = 1f / (1f - z2 * 0.18f)
                    Offset(ship.x + x1 * sc * pr, ship.y + y2 * sc * pr)
                }
                val edges = intArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 0, 2, 1, 3, 4, 6, 5, 7, 0, 4, 1, 5, 2, 6, 3, 7)
                for (e in 0 until 12) drawLine(Color.hsv((hue + e * 20f) % 360f, 0.5f, 1f).copy(alpha = 0.5f), verts[edges[e * 2]], verts[edges[e * 2 + 1]], 1.8f)
            }
            // phasing clones with red / cyan chromatic split
            for (k in 1..4) {
                val a = 0.42f * (0.5f + 0.5f * sin(t * 0.18f + k * 1.7f))
                val ox = -h * 0.15f * k
                val oy = sin(t * 0.12f + k) * u * 1.8f
                val glitch = if (((tick / 3) + k) % 7 == 0) u * 2.5f else 0f
                val tl = Offset(x - h * 0.17f + ox + glitch, y - h * 0.31f + oy)
                val sz = Size(h * 0.34f, h * 0.62f)
                val cr = androidx.compose.ui.geometry.CornerRadius(h * 0.1f)
                drawRoundRect(Color(0xFFFF2D6F).copy(alpha = a), tl + Offset(-u * 0.5f, 0f), sz, cr, style = Stroke(2.2f))
                drawRoundRect(Color(0xFF00E5FF).copy(alpha = a), tl + Offset(u * 0.5f, 0f), sz, cr, style = Stroke(2.2f))
                drawRoundRect(Color.White.copy(alpha = a * 0.6f), tl, sz, cr, style = Stroke(1.2f))
            }
            // orbiting prism shards
            for (k in 0 until 4) {
                val a = t * 0.07f + k * pi / 2f
                val c = ship + Offset(cos(a) * h * 0.55f, sin(a) * h * 0.3f)
                val rot = t * 0.12f + k
                val shard = Path().apply {
                    for (v in 0 until 3) {
                        val ang = rot + v * 2.094f
                        val px = c.x + cos(ang) * u * 1.6f; val py = c.y + sin(ang) * u * 1.6f
                        if (v == 0) moveTo(px, py) else lineTo(px, py)
                    }
                    close()
                }
                drawPath(shard, Color.hsv((hue + k * 70f) % 360f, 0.7f, 1f).copy(alpha = 0.6f))
                drawPath(shard, Color.White.copy(alpha = 0.85f), style = Stroke(1.5f))
            }
            // glitch slices + falling data bits
            if ((tick / 4) % 3 == 0) {
                val r = Random(tick / 2 * 7 + 3)
                for (k in 0 until 3) {
                    val gy = y - h * 0.5f + r.nextFloat() * h
                    drawRect(Color.hsv((hue + k * 100f) % 360f, 0.8f, 1f).copy(alpha = 0.35f),
                        Offset(x - h * 0.5f + r.nextFloat() * u * 3f, gy), Size(h * (0.4f + r.nextFloat() * 0.5f), u * 0.5f))
                }
            }
            for (k in 0 until 10) {
                val ph = (t * 0.03f + k / 10f) % 1f
                drawRect(Color.hsv((hue + k * 30f) % 360f, 0.6f, 1f).copy(alpha = (1f - ph) * 0.8f),
                    Offset(x - h * 0.2f - ph * h * 1.3f, y - h * 0.5f + ((k * 37) % 100) / 100f * h), Size(u * 0.5f, u * 0.5f))
            }
            // scan line + hue trail
            val scan = ((t * 0.03f) % 1f)
            val sy = y - h * 0.5f + scan * h
            drawLine(Color.White.copy(alpha = 0.55f), Offset(x - h * 0.5f, sy), Offset(x + h * 0.5f, sy), strokeWidth = 2f)
            for (i in 1..n) {
                val f = i / (n + 1f)
                drawCircle(Color.hsv((hue + f * 120f) % 360f, 0.8f, 1f).copy(alpha = 0.6f * (1f - f)), u * (1.3f - f),
                    Offset(x - h * 0.2f - i * u * 1.6f, y + h * 0.05f + sin(t * 0.3f + i) * u * 0.5f))
            }
            // teleport blink every so often
            if ((tick % 48) < 3) drawLine(Color(0xFF00E5FF).copy(alpha = 0.8f), Offset(x, y - h * 0.6f), Offset(x, y + h * 0.6f), strokeWidth = u * 0.9f)
            // zone pulse: a rainbow shockwave
            if (za in 0..22) {
                val f = za / 22f
                for (k in 0 until 3) drawCircle(Color.hsv((hue + k * 60f) % 360f, 0.8f, 1f).copy(alpha = 1f - f), h * 0.3f + za * u * (1f + k * 0.35f), ship, style = Stroke(3f))
            }
            // close call: the ship phases out into six fanned ghosts
            if (cc in 0..16) {
                val f = cc / 16f
                for (k in 0 until 6) {
                    val ang = (k - 2.5f) * 0.32f
                    val o = Offset(-cos(ang) * h * 0.9f * f, sin(ang) * h * 0.6f * f)
                    drawRoundRect(Color.hsv((hue + k * 55f) % 360f, 0.7f, 1f).copy(alpha = 0.7f * (1f - f)),
                        Offset(x - h * 0.17f + o.x, y - h * 0.31f + o.y), Size(h * 0.34f, h * 0.62f), androidx.compose.ui.geometry.CornerRadius(h * 0.1f), style = Stroke(2.4f))
                }
            }
        }

        "stormcaller" -> {
            for (k in 0 until 4) {
                val ang = t * 0.03f + k * pi / 2f
                drawCircle(Color(0xFF455A64).copy(alpha = 0.55f), h * 0.2f, ship + Offset(cos(ang) * h * 0.3f, sin(ang) * h * 0.2f - h * 0.1f))
            }
            val r = Random(tick / 2 * 13 + 5)
            for (k in 0 until 2) {
                var p = ship + Offset(h * 0.1f, -h * 0.05f)
                for (seg in 0 until 4) {
                    val nx = p + Offset(h * 0.12f + r.nextFloat() * u, (r.nextFloat() - 0.5f) * u * 3f)
                    drawLine(Color(0xFFFFF59D).copy(alpha = 0.9f), p, nx, strokeWidth = 2.2f)
                    p = nx
                }
            }
            for (i in 1..n) {
                val f = i / (n + 1f)
                drawLine(Color(0xFF90CAF9).copy(alpha = 0.5f * (1f - f)), Offset(x - h * 0.2f - i * u * 1.6f, y - u),
                    Offset(x - h * 0.22f - i * u * 1.6f, y + u * 2f), strokeWidth = 2f)
            }
            val a = age(tick, HullFx.closeCallTick)
            if (a in 0..5) drawRect(Color.White.copy(alpha = 0.4f * (1f - a / 5f)), Offset(x - size.width, y - size.height), Size(size.width * 2f, size.height * 2f))
        }

        "cosmic_koi" -> {
            for (k in 0 until 3) {
                val ang = t * 0.05f + k * (2f * pi / 3f)
                val p = ship + Offset(cos(ang) * h * 0.5f, sin(ang) * h * 0.22f)
                val col = if (k % 2 == 0) Color(0xFFFF8A65) else Color(0xFFFFFFFF)
                drawOval(col.copy(alpha = 0.9f), Offset(p.x - u * 1.4f, p.y - u * 0.7f), Size(u * 2.8f, u * 1.4f))
                val tail = Path().apply {
                    moveTo(p.x - u * 1.3f, p.y); lineTo(p.x - u * 2.3f, p.y - u * 0.8f); lineTo(p.x - u * 2.3f, p.y + u * 0.8f); close()
                }
                drawPath(tail, col.copy(alpha = 0.7f))
            }
            for (k in 0 until 4) {
                val ph = (t * 0.035f + k / 4f) % 1f
                drawCircle(Color(0xFF80DEEA).copy(alpha = (1f - ph) * 0.6f), h * 0.1f + ph * h * 0.45f,
                    Offset(x - h * 0.15f - ph * h * 1.1f, y + h * 0.05f), style = Stroke(2f))
            }
            for (k in 0 until 5) {
                val ph = (t * 0.03f + k / 5f) % 1f
                drawCircle(Color(0xFFF8BBD0).copy(alpha = 1f - ph), u * 0.45f,
                    Offset(x - h * 0.1f - ph * h * 0.9f, y - h * 0.4f + ph * h * 0.8f + sin(k * 2f + t * 0.1f) * u))
            }
        }

        "inferno_sovereign" -> {
            val boss = age(tick, HullFx.bossTick)
            val cc = age(tick, HullFx.closeCallTick)
            val gem = age(tick, HullFx.gemTick)
            pGlow(ship, h * 0.75f, Color(0xFFFF6D00), 0.22f + (if (boss in 0..16) 0.3f * (1f - boss / 16f) else 0f))
            for (k in 0 until 3) { // heat rings
                val ph = (t * 0.025f + k / 3f) % 1f
                drawCircle(Color(0xFFFF8A00).copy(alpha = (1f - ph) * 0.35f), h * (0.3f + ph * 0.5f), ship, style = Stroke(2f))
            }
            // molten mantle: three flowing ribbons
            for (rb in 0 until 3) {
                var prev = Offset(x - h * 0.12f, y + (rb - 1) * u * 1.4f)
                val m = n + 4
                for (i in 1..m) {
                    val f = i / (m + 1f)
                    val next = Offset(x - h * 0.12f - i * u * 1.7f, y + (rb - 1) * u * (1.4f + f * 2.2f) + sin(t * 0.25f - i * 0.5f + rb * 1.7f) * u * (0.6f + f * 2f))
                    drawLine(lerp(Color(0xFFFFEB3B), Color(0xFFD50000), f).copy(alpha = 0.85f * (1f - f)), prev, next, strokeWidth = (u * (2.0f - 1.6f * f)).coerceAtLeast(1.5f))
                    prev = next
                }
            }
            // three fire orbs circling the ship, each with a comet tail
            for (k in 0 until 3) {
                val a = t * 0.11f + k * (2f * pi / 3f)
                for (tr in 1..4) {
                    val a2 = a - tr * 0.18f
                    drawCircle(Color(0xFFFF6D00).copy(alpha = 0.5f * (1f - tr / 5f)), u * (0.9f - tr * 0.12f),
                        ship + Offset(cos(a2) * h * 0.5f, sin(a2) * h * 0.2f))
                }
                val p = ship + Offset(cos(a) * h * 0.5f, sin(a) * h * 0.2f)
                pGlow(p, u * 2.4f, Color(0xFFFF6D00), 0.6f)
                drawCircle(Color(0xFFFFEB3B), u * 0.7f, p)
                drawCircle(Color.White.copy(alpha = 0.9f), u * 0.3f, p)
            }
            // the crown: seven layered flame spires on a golden circlet with jewels
            val crownY = y - h * 0.40f
            val rise = if (boss in 0..16) 1f + 0.9f * (1f - boss / 16f) else 1f
            for (k in -3..3) {
                val fl = 0.65f + 0.35f * sin(t * 0.45f + k * 1.3f)
                val tip = h * (0.2f + (3 - kotlin.math.abs(k)) * 0.055f) * fl * rise
                flameSpire(Offset(x + k * u * 1.15f, crownY), u * 0.75f, tip, sin(t * 0.3f + k) * u * 0.5f,
                    Color(0xFFD50000).copy(alpha = 0.9f), Color(0xFFFF9100), Color(0xFFFFF59D))
            }
            drawLine(Color(0xFFFFD23F), Offset(x - u * 4.4f, crownY + u * 0.3f), Offset(x + u * 4.4f, crownY + u * 0.3f), strokeWidth = u * 0.9f)
            for (k in -2..2) {
                val jp = Offset(x + k * u * 1.9f, crownY + u * 0.3f)
                drawCircle(if (k == 0) Color(0xFFFF1744) else Color(0xFFFFF176), u * 0.42f, jp)
                if (((tick / 6) + k + 2) % 5 == 0 || gem in 0..10) drawCircle(Color.White, u * 0.2f, jp)
            }
            // lava drips falling from the circlet
            for (k in 0 until 6) {
                val ph = (t * 0.05f + k / 6f) % 1f
                val dx = x + (k - 2.5f) * u * 1.5f
                val dy = crownY + u * 0.6f + ph * h * 0.8f
                drawLine(Color(0xFFFF6D00).copy(alpha = 1f - ph), Offset(dx, dy), Offset(dx, dy - u * 1.2f * (1f - ph)), strokeWidth = u * 0.6f)
                drawCircle(Color(0xFFFFAB00).copy(alpha = 1f - ph), u * 0.4f, Offset(dx, dy))
            }
            // rising embers
            for (k in 0 until 8) {
                val ph = (t * 0.04f + k / 8f) % 1f
                drawCircle(Color(0xFFFFD54F).copy(alpha = 1f - ph), u * 0.3f,
                    Offset(x - h * 0.3f + ((k * 29) % 60) / 60f * h * 0.6f + sin(t * 0.2f + k) * u, y + h * 0.3f - ph * h * 0.9f))
            }
            // boss roar: shockwave rings + upward flame burst
            if (boss in 0..22) {
                val f = boss / 22f
                for (k in 0 until 2) drawCircle(Color(0xFFFF3D00).copy(alpha = 1f - f), h * 0.3f + boss * u * (1.1f + k * 0.5f), ship, style = Stroke(4f))
            }
            // close call: a ring of flame rays
            if (cc in 0..12) {
                val f = cc / 12f
                for (k in 0 until 12) {
                    val ang = k * pi / 6f
                    drawLine(Color(0xFFFFAB00).copy(alpha = 1f - f), ship + Offset(cos(ang), sin(ang)) * (h * 0.32f + h * 0.2f * f),
                        ship + Offset(cos(ang), sin(ang)) * (h * 0.5f + h * 0.4f * f), strokeWidth = 3.5f)
                }
            }
        }

        "eternal_crown_2026" -> {
            pGlow(ship, h * 0.7f, Color(0xFFFFD23F), 0.28f)
            for (k in 0 until 8) {
                val a = t * 0.05f + k * pi / 4f
                val p = ship + Offset(cos(a) * h * 0.5f, sin(a) * h * 0.16f - h * 0.32f)
                drawCircle(Color(0xFFFFE082).copy(alpha = if (sin(a) > 0) 0.95f else 0.5f), u * 0.45f, p)
            }
            for (k in 0 until 8) {
                val ph = (t * 0.04f + k / 8f) % 1f
                drawCircle(Color(0xFFFFD23F).copy(alpha = 1f - ph), u * 0.35f, Offset(x - h * 0.2f - ph * h * 1.0f, y + sin(k * 2.6f) * u * 1.8f))
            }
            if ((tick % 90) < 8) pGlow(ship + Offset(0f, -h * 0.32f), h * 0.35f, Color.White, 0.5f)
        }

        // Ranked Season hull: never sold, granted to the season's top 100.
        "apex_laurel" -> {
            pGlow(ship, h * 0.75f, Color(0xFF7DF9FF), 0.2f)
            for (side in -1..1 step 2) for (k in 0 until 7) {
                val a = (k - 3) * 0.28f
                val lp = ship + Offset(side * (h * 0.38f - kotlin.math.abs(k - 3) * u * 0.25f), -a * h * 0.55f * -1f + sin(t * 0.06f + k) * u * 0.2f)
                drawCircle(Color(0xFFFFD23F).copy(alpha = 0.9f), u * 0.5f, lp)
            }
            for (k in 0 until 10) {
                val ph = (t * 0.05f + k / 10f) % 1f
                drawCircle(Color(0xFF7DF9FF).copy(alpha = 1f - ph), u * 0.4f, Offset(x - h * 0.2f - ph * h * 1.2f, y + sin(k * 1.7f + t * 0.1f) * u * 2f))
            }
            if ((tick % 120) < 10) pGlow(ship, h * 0.5f, Color.White, 0.4f)
        }

        else -> return false
    }
    return true
}


private fun hueMatrix(deg: Float): ColorMatrix {
    val r = Math.toRadians(deg.toDouble()); val c = cos(r).toFloat(); val s = sin(r).toFloat()
    return ColorMatrix(floatArrayOf(
        0.213f + c * 0.787f - s * 0.213f, 0.715f - c * 0.715f - s * 0.715f, 0.072f - c * 0.072f + s * 0.928f, 0f, 0f,
        0.213f - c * 0.213f + s * 0.143f, 0.715f + c * 0.285f + s * 0.140f, 0.072f - c * 0.072f - s * 0.283f, 0f, 0f,
        0.213f - c * 0.213f - s * 0.787f, 0.715f - c * 0.715f + s * 0.715f, 0.072f + c * 0.928f + s * 0.072f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f))
}

/** Public entry used by drawExtraHull. Applies Pro Chroma (hue-rotate layer) and Mastery pips. */
internal fun DrawScope.drawPremiumHull(hullId: String, x: Float, y: Float, h: Float, tick: Int, speedLevel: Int): Boolean {
    val variant = HullFx.chroma[hullId] ?: 0
    var handled = false
    if (variant == 0) handled = drawPremiumHullRaw(hullId, x, y, h, tick, speedLevel)
    else drawIntoCanvas { canvas ->
        val paint = Paint().apply { colorFilter = ColorFilter.colorMatrix(hueMatrix(variant * 90f)) }
        canvas.saveLayer(androidx.compose.ui.geometry.Rect(0f, 0f, size.width, size.height), paint)
        handled = drawPremiumHullRaw(hullId, x, y, h, tick, speedLevel)
        canvas.restore()
    }
    if (handled) {
        val lv = HullFx.mastery[hullId] ?: 0
        for (i in 0 until lv) drawCircle(Color(0xFFFFD23F).copy(alpha = 0.9f), h * 0.025f, Offset(x + (i - (lv - 1) / 2f) * h * 0.09f, y + h * 0.5f))
    }
    return handled
}
