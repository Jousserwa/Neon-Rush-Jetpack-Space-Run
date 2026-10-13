package com.neonrush.game.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/**
 * Thruster trails, drawn in code. Each one starts at the jetpack (just behind the pilot) and streams to the left.
 * [h] = pilot height in px, [tick] = game tick (or any free-running tick in menus), [speedLevel] 0..5 lengthens the trail.
 * All use additive light so they blend with the neon scene. Cheap: a few dozen primitives each.
 */
private fun tRand(i: Int): Float { val v = sin(i * 12.9898f) * 43758.547f; return abs(v - floor(v)) }

private fun DrawScope.tGlow(c: Offset, r: Float, color: Color, a: Float) {
    drawCircle(Brush.radialGradient(listOf(color.copy(alpha = a.coerceIn(0f, 1f)), Color.Transparent), center = c, radius = r),
        r, c, blendMode = BlendMode.Plus)
}

private fun DrawScope.seg(a: Offset, b: Offset, color: Color, alpha: Float, w: Float) {
    drawLine(color, a, b, strokeWidth = w, cap = StrokeCap.Round, alpha = alpha.coerceIn(0f, 1f), blendMode = BlendMode.Plus)
}

internal fun DrawScope.drawThruster(id: String, x: Float, y: Float, h: Float, tick: Int, speedLevel: Int = 0) {
    val u = h * 0.06f
    val t = tick.toFloat()
    val n = 10 + speedLevel * 2
    val ox = x - h * 0.2f
    val oy = y + h * 0.06f
    val o = Offset(ox, oy)
    val cyan = Color(0xFF00E5FF); val mag = Color(0xFFFF2D95); val violet = Color(0xFF7C4DFF)
    when (id) {
        "plasma_ribbon" -> {
            tGlow(o, u * 3f, violet, 0.5f)
            for (rb in 0..1) {
                var prev = o
                for (i in 1..n + 4) {
                    val f = i / (n + 5f)
                    val nx = Offset(ox - i * u * 1.7f, oy + sin(t * 0.26f - i * 0.5f + rb * PI.toFloat()) * u * (0.5f + f * 2.2f))
                    val col = lerp(if (rb == 0) mag else cyan, violet, f)
                    seg(prev, nx, col, 0.2f * (1f - f), u * (1.8f - 1.3f * f) * 2.6f)
                    seg(prev, nx, col, 0.9f * (1f - f), (u * (1.8f - 1.3f * f)).coerceAtLeast(1.2f))
                    prev = nx
                }
            }
        }
        "pixel_stream" -> {
            val cols = listOf(cyan, mag, Color(0xFFFFEB3B))
            val cell = u * 0.9f
            for (i in 0 until n + 6) {
                val f = i / (n + 6f)
                val row = ((tRand(i * 7 + (tick / 3)) - 0.5f) * 5f).toInt()
                val px = ox - i * cell * 1.15f
                val py = oy + row * cell
                val a = (1f - f) * 0.95f
                drawRect(cols[(i + tick / 4) % 3].copy(alpha = a), Offset(px, py), Size(cell, cell), blendMode = BlendMode.Plus)
            }
            tGlow(o, u * 2.2f, cyan, 0.4f)
        }
        "star_dust" -> {
            tGlow(o, u * 3f, Color(0xFFFFE082), 0.35f)
            for (k in 0 until n + 6) {
                val ph = (t * 0.03f + k / (n + 6f)) % 1f
                val sx = ox - ph * h * 1.25f
                val sy = oy + (tRand(k * 5) - 0.5f) * h * 0.5f + sin(t * 0.1f + k) * u * 0.4f
                val tw = 0.5f + 0.5f * sin(t * 0.3f + k * 2f)
                val r = u * (0.25f + 0.45f * tRand(k * 3)) * (1f - ph * 0.5f)
                val c = Offset(sx, sy)
                val col = if (k % 3 == 0) Color(0xFFFFE082) else Color.White
                val a = (1f - ph) * (0.4f + 0.6f * tw)
                seg(Offset(c.x - r * 1.6f, c.y), Offset(c.x + r * 1.6f, c.y), col, a, 1.6f)
                seg(Offset(c.x, c.y - r * 1.6f), Offset(c.x, c.y + r * 1.6f), col, a, 1.6f)
                drawCircle(col.copy(alpha = a), r * 0.5f, c, blendMode = BlendMode.Plus)
            }
        }
        "glitch_static" -> {
            val s = tick / 2
            tGlow(o, u * 2f, cyan, 0.35f)
            for (k in 0 until n) {
                val f = k / n.toFloat()
                val len = u * (3f + 8f * tRand(s * 11 + k))
                val dy = (tRand(s * 7 + k * 3) - 0.5f) * u * 5f
                val px = ox - f * h * 1.2f
                val hgt = u * (0.25f + 0.5f * tRand(s + k * 9))
                val a = (1f - f) * (if (tRand(s * 3 + k) > 0.2f) 0.9f else 0.2f)
                drawRect(Color(0xFFFF1744).copy(alpha = a * 0.8f), Offset(px - len + u * 0.5f, oy + dy), Size(len, hgt), blendMode = BlendMode.Plus)
                drawRect(cyan.copy(alpha = a), Offset(px - len, oy + dy + hgt * 0.4f), Size(len, hgt), blendMode = BlendMode.Plus)
            }
        }
        "prism_beam" -> {
            val bands = 6
            val len = h * (1.0f + 0.15f * speedLevel)
            for (b in 0 until bands) {
                val hue = ((t * 2.2f + b * 55f) % 360f)
                val col = Color.hsv(hue, 0.85f, 1f)
                val off = (b - (bands - 1) / 2f) * u * 0.55f
                val wob = sin(t * 0.18f + b) * u * 0.25f
                val steps = 8
                var prev = Offset(ox, oy + off)
                for (i in 1..steps) {
                    val f = i / steps.toFloat()
                    val nx = Offset(ox - len * f, oy + off * (1f + f * 1.8f) + wob * f)
                    seg(prev, nx, col, 0.8f * (1f - f), u * 0.55f * (1f - 0.5f * f))
                    prev = nx
                }
            }
            tGlow(o, u * 2.8f, Color.White, 0.4f)
        }
        "ink_drip" -> {
            val ink = Color(0xFF6A1BFF)
            for (k in 0 until n) {
                val ph = (t * 0.03f + k / n.toFloat()) % 1f
                val px = ox - ph * h * 1.0f
                val py = oy + ph * ph * h * 0.55f * (0.4f + tRand(k)) + sin(t * 0.15f + k) * u * 0.3f
                val r = u * (0.9f - 0.6f * ph) * (0.7f + 0.5f * tRand(k * 4))
                val a = (1f - ph)
                drawCircle(ink.copy(alpha = 0.55f * a), r * 1.9f, Offset(px, py), blendMode = BlendMode.Plus)
                drawCircle(Color(0xFF14002E).copy(alpha = 0.9f * a), r, Offset(px, py))
                drawCircle(mag.copy(alpha = 0.8f * a), r, Offset(px, py), style = Stroke(1.6f), blendMode = BlendMode.Plus)
            }
            tGlow(o, u * 2.4f, ink, 0.5f)
        }
        "circuit_sparks" -> {
            val green = Color(0xFF39FFA0)
            for (tr in 0..2) {
                var p = Offset(ox, oy + (tr - 1) * u * 1.1f)
                val total = n
                for (i in 1..total) {
                    val f = i / total.toFloat()
                    val horiz = tRand(tr * 31 + i * 17) > 0.35f
                    val np = if (horiz) Offset(p.x - u * (1.4f + 1.6f * tRand(i + tr)), p.y)
                             else Offset(p.x, p.y + (if (tRand(i * 3 + tr) > 0.5f) 1f else -1f) * u * 1.1f)
                    seg(p, np, green, (1f - f) * 0.9f, u * 0.32f)
                    if (!horiz) drawCircle(cyan.copy(alpha = 1f - f), u * 0.34f, np, blendMode = BlendMode.Plus)
                    p = np
                }
            }
            for (k in 0 until 4) {
                val ph = (t * 0.07f + k * 0.25f) % 1f
                val sp = Offset(ox - ph * h * 0.9f, oy + sin(k * 3.1f) * u * 2f)
                tGlow(sp, u * 1.4f, Color.White, (1f - ph) * 0.7f)
            }
            tGlow(o, u * 2.2f, green, 0.4f)
        }
        "aurora_veil" -> {
            val green = Color(0xFF39FFA0)
            for (band in 0..2) {
                val col = when (band) { 0 -> green; 1 -> cyan; else -> violet }
                var prev = Offset(ox, oy + (band - 1) * u * 1.2f)
                val steps = n + 4
                for (i in 1..steps) {
                    val f = i / steps.toFloat()
                    val nx = Offset(ox - i * u * 1.8f, oy + (band - 1) * u * (1.2f + f * 1.6f) + sin(t * 0.12f - i * 0.35f + band * 1.9f) * u * (0.6f + f * 1.8f))
                    seg(prev, nx, col, 0.14f * (1f - f), u * 3.4f)
                    seg(prev, nx, col, 0.55f * (1f - f), u * 1.1f)
                    // shimmer rays hanging off the curtain
                    if (i % 3 == 0) seg(nx, Offset(nx.x, nx.y + u * (1.5f + 2f * tRand(i + band * 9))), Color.White, 0.35f * (1f - f), 1.4f)
                    prev = nx
                }
            }
            tGlow(o, u * 2.6f, green, 0.4f)
        }
        "royal_plasma" -> {
            val gold = Color(0xFFFFD23F)
            val steps = n + 6
            var pa = o; var pb = o
            for (i in 1..steps) {
                val f = i / steps.toFloat()
                val px = ox - i * u * 1.5f
                val amp = u * (0.8f + f * 1.6f)
                val a = sin(t * 0.25f - i * 0.5f) * amp
                val na = Offset(px, oy + a); val nb = Offset(px, oy - a)
                seg(pa, na, gold, 0.9f * (1f - f), u * 0.7f)
                seg(pb, nb, mag, 0.9f * (1f - f), u * 0.7f)
                if (i % 2 == 0) seg(na, nb, Color.White, 0.25f * (1f - f), 1.2f)   // rungs
                if (abs(a) < amp * 0.25f) drawCircle(Color.White.copy(alpha = 0.9f * (1f - f)), u * 0.35f, Offset(px, oy), blendMode = BlendMode.Plus)
                pa = na; pb = nb
            }
            tGlow(o, u * 3f, gold, 0.45f)
        }
        "supernova_wake" -> {
            val gold = Color(0xFFFFE082)
            for (k in 0 until 4) {
                val ph = (t * 0.045f + k / 4f) % 1f
                val c = Offset(ox - ph * h * 1.15f, oy)
                val r = u * (0.6f + 3.2f * ph)
                val col = lerp(Color.White, mag, ph)
                drawCircle(col.copy(alpha = (1f - ph) * 0.85f), r, c, style = Stroke(u * 0.35f * (1f - ph * 0.6f)), blendMode = BlendMode.Plus)
                tGlow(c, r * 1.3f, gold, (1f - ph) * 0.25f)
            }
            for (k in 0 until 8) {  // flare spikes at the jet
                val ang = k * PI.toFloat() / 4f + t * 0.03f
                seg(o, Offset(ox + cos(ang) * u * (1.6f + 1.4f * (k % 2)), oy + sin(ang) * u * (1.6f + 1.4f * (k % 2))), Color.White, 0.8f, 1.8f)
            }
            seg(o, Offset(ox - h * 0.9f, oy), gold, 0.7f, u * 0.5f)
            tGlow(o, u * 3.4f, Color.White, 0.5f)
        }
        "heartbeat_pulse" -> {
            val green = Color(0xFF39FFA0)
            val steps = n + 8
            var prev = o
            for (i in 1..steps) {
                val f = i / steps.toFloat()
                val ph = ((i * 0.5f + t * 0.35f) % 8f)
                val amp = when {            // one ECG beat every 8 samples: small bump, spike up, spike down
                    ph < 1f -> sin(ph * PI.toFloat()) * 0.5f
                    ph in 3f..3.5f -> (ph - 3f) * 2f * 3.2f
                    ph in 3.5f..4f -> 3.2f - (ph - 3.5f) * 2f * 5.4f
                    ph in 4f..4.6f -> -2.2f + (ph - 4f) / 0.6f * 2.2f
                    else -> 0f
                }
                val nx = Offset(ox - i * u * 0.95f, oy - amp * u)
                seg(prev, nx, green, 0.2f * (1f - f), u * 1.6f)
                seg(prev, nx, green, 0.95f * (1f - f), u * 0.4f)
                prev = nx
            }
            val beat = 0.6f + 0.4f * sin(t * 0.35f * PI.toFloat() / 4f)
            tGlow(o, u * 2.6f * beat + u, green, 0.5f)
            drawCircle(Color.White.copy(alpha = 0.9f), u * 0.4f, o, blendMode = BlendMode.Plus)
        }
        "veteran_comet" -> {
            val ice = Color(0xFF9BE7FF)
            val len = h * (1.1f + 0.12f * speedLevel)
            val tail = Path().apply {
                moveTo(ox, oy - u * 1.3f); lineTo(ox - len, oy + sin(t * 0.1f) * u * 0.8f); lineTo(ox, oy + u * 1.3f); close()
            }
            drawPath(tail, Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.85f), ice.copy(alpha = 0.35f), Color.Transparent), startX = ox, endX = ox - len),
                blendMode = BlendMode.Plus)
            for (k in 0 until 3) {   // little stars orbiting the tail axis
                val ph = (t * 0.03f + k / 3f) % 1f
                val sx = ox - ph * len * 0.9f
                val sy = oy + sin(ph * 12f + k * 2.1f) * u * (1f + ph * 1.6f)
                drawCircle(Color.White.copy(alpha = 1f - ph), u * 0.4f, Offset(sx, sy), blendMode = BlendMode.Plus)
                seg(Offset(sx - u * 0.8f, sy), Offset(sx + u * 0.8f, sy), ice, (1f - ph) * 0.7f, 1.4f)
                seg(Offset(sx, sy - u * 0.8f), Offset(sx, sy + u * 0.8f), ice, (1f - ph) * 0.7f, 1.4f)
            }
            for (k in 0 until 8) {   // ice dust
                val ph = (t * 0.04f + k / 8f) % 1f
                drawCircle(ice.copy(alpha = (1f - ph) * 0.8f), u * 0.18f, Offset(ox - ph * len, oy + (tRand(k * 6) - 0.5f) * u * 3.2f), blendMode = BlendMode.Plus)
            }
            tGlow(o, u * 3f, Color.White, 0.55f)
        }
        else -> { // ion_stream (default): three tapering cyan streaks + core glow
            val len = h * (0.75f + 0.12f * speedLevel) * (0.92f + 0.08f * sin(t * 0.5f))
            tGlow(o, u * 3f, cyan, 0.5f)
            for (k in -1..1) {
                val tip = Offset(ox - len * (1f - 0.18f * abs(k)), oy + k * u * 1.1f)
                val p = Path().apply {
                    moveTo(ox, oy + k * u * 0.35f - u * 0.5f); lineTo(tip.x, tip.y); lineTo(ox, oy + k * u * 0.35f + u * 0.5f); close()
                }
                drawPath(p, cyan.copy(alpha = if (k == 0) 0.85f else 0.45f), blendMode = BlendMode.Plus)
            }
            drawCircle(Color.White.copy(alpha = 0.8f), u * 0.5f, o, blendMode = BlendMode.Plus)
        }
    }
}
