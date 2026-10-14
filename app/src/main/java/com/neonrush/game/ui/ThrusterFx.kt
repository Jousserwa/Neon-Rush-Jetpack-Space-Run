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
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * THRUSTER TRAILS are WAKES, not auras.
 *  - Hull effects glow around the ship and move with it.
 *  - A trail is made of SOLID objects (dashes, pixels, digits, leaves, coins, circuit copper...) that are dropped on your actual
 *    flight path and then stay in the world while you fly on, so a climb or a dive bends the whole trail.
 * That difference (matter left behind vs light around you) is what makes them read as a separate collection.
 */

/** Remembers where the pilot was over the last few seconds so a trail can follow the real flight path. */
class TrailPath {
    private val cap = 180
    private val ts = FloatArray(cap)
    private val ys = FloatArray(cap)
    private var count = 0
    private var head = 0
    private var now = 0f

    fun push(time: Float, y: Float) {
        if (count > 0 && time < now - 0.5f) count = 0            // the effects clock restarted
        if (count > 0 && time - now < 0.02f) { ys[head] = y; now = time; return }
        head = (head + 1) % cap; ts[head] = time; ys[head] = y; now = time
        if (count < cap) count++
    }

    /** Pilot centre Y, [age] seconds ago (the oldest sample if older than we remember). */
    fun yAt(age: Float): Float {
        if (count == 0) return 0f
        val target = now - age
        var i = head
        var k = 0
        while (k < count - 1) {
            val j = (i - 1 + cap) % cap
            if (ts[j] <= target) {
                val f = ((target - ts[j]) / (ts[i] - ts[j]).coerceAtLeast(1e-4f)).coerceIn(0f, 1f)
                return ys[j] + (ys[i] - ys[j]) * f
            }
            i = j; k++
        }
        return ys[i]
    }
}

private fun rnd(i: Int): Float { val v = sin(i * 12.9898f) * 43758.547f; return abs(v - floor(v)) }
private val INK = Color(0xFF05060F)

/** Drops [count] objects over [life] seconds; each is called with (x, y, fade 0..1 young->old, stable seed). */
private fun DrawScope.stamps(
    ox: Float, oy: Float, y: Float, h: Float, time: Float, speed: Int, count: Int, life: Float,
    yAt: (Float) -> Float, draw: DrawScope.(Float, Float, Float, Int) -> Unit
) {
    val dt = life / count
    val v = h * 2.3f * (1f + 0.1f * speed) / life
    val base = floor(time / dt).toInt()
    val frac = time - base * dt
    for (j in 0 until count) {
        val age = frac + j * dt
        draw(ox - age * v, yAt(age) + (oy - y), age / life, base - j)
    }
}

private fun DrawScope.sparkle(c: Offset, r: Float, color: Color, alpha: Float) {
    val p = Path().apply {
        moveTo(c.x, c.y - r); quadraticBezierTo(c.x, c.y, c.x + r, c.y); quadraticBezierTo(c.x, c.y, c.x, c.y + r)
        quadraticBezierTo(c.x, c.y, c.x - r, c.y); quadraticBezierTo(c.x, c.y, c.x, c.y - r); close()
    }
    drawPath(p, color, alpha = alpha.coerceIn(0f, 1f))
}

private fun DrawScope.ecg(p: Float): Float = when {
    p < 0.10f -> sin(p / 0.10f * PI.toFloat()) * 0.12f
    p in 0.30f..0.36f -> -(p - 0.30f) / 0.06f * 0.2f
    p in 0.36f..0.42f -> -0.2f + (p - 0.36f) / 0.06f * 1.4f
    p in 0.42f..0.50f -> 1.2f - (p - 0.42f) / 0.08f * 1.6f
    p in 0.50f..0.56f -> -0.4f + (p - 0.50f) / 0.06f * 0.4f
    else -> 0f
}

/**
 * [x],[y] = pilot centre, [h] = pilot height, [time] = smooth seconds, [speedLevel] 0..5 (Afterburner) lengthens the wake.
 * [yAt] = pilot Y [age] seconds ago (see TrailPath).
 */
internal fun DrawScope.drawThruster(id: String, x: Float, y: Float, h: Float, time: Float, speedLevel: Int, yAt: (Float) -> Float) {
    val u = h * 0.06f
    val ox = x - h * 0.2f
    val oy = y + h * 0.06f
    val v = h * 2.3f * (1f + 0.1f * speedLevel)       // wake length in px (life * speed)
    when (id) {
        "pixel_trail" -> stamps(ox, oy, y, h, time, speedLevel, 12, 1.0f, yAt) { px, py, f, seed ->
            val cols = listOf(Color(0xFF00E5FF), Color(0xFFFF2D95), Color(0xFFFFEB3B))
            val cell = u * 0.9f * (if (f < 0.33f) 1f else if (f < 0.66f) 0.75f else 0.5f)
            val gx = floor(px / cell) * cell; val gy = floor(py / cell) * cell
            drawRect(INK, Offset(gx - 1.5f, gy - 1.5f), Size(cell + 3f, cell + 3f))
            drawRect(cols[((seed % 3) + 3) % 3], Offset(gx, gy), Size(cell, cell))
        }
        "code_stream" -> stamps(ox, oy, y, h, time, speedLevel, 16, 1.2f, yAt) { px, py, f, seed ->
            val row = ((rnd(seed * 3) * 5f).toInt() - 2) * u * 1.25f
            val g = u * 0.8f
            val c = if (f < 0.1f) Color(0xFFE8FFE8) else Color(0xFF39FF14)
            val a = 1f - f * 0.85f
            val cy = py + row
            if (rnd(seed * 7) > 0.5f) {
                drawCircle(INK.copy(alpha = a), g * 0.75f, Offset(px, cy), style = Stroke(4f))
                drawCircle(c.copy(alpha = a), g * 0.75f, Offset(px, cy), style = Stroke(2f))
            } else {
                drawLine(INK.copy(alpha = a), Offset(px, cy - g), Offset(px, cy + g), strokeWidth = 4.5f, cap = StrokeCap.Round)
                drawLine(c.copy(alpha = a), Offset(px, cy - g), Offset(px, cy + g), strokeWidth = 2.2f, cap = StrokeCap.Round)
                drawLine(c.copy(alpha = a), Offset(px, cy - g), Offset(px - g * 0.5f, cy - g * 0.55f), strokeWidth = 2.2f, cap = StrokeCap.Round)
            }
        }
        "ink_blots" -> stamps(ox, oy, y, h, time, speedLevel, 9, 1.2f, yAt) { px, py0, f, seed ->
            val ink = Color(0xFF1A0B3B)
            val py = py0 + f * f * h * 0.55f * (0.5f + rnd(seed))
            val r = u * (1.0f - 0.5f * f) * (0.7f + 0.6f * rnd(seed * 5))
            if (rnd(seed * 2) > 0.5f) drawLine(ink, Offset(px, py - r * 2.2f), Offset(px, py), strokeWidth = r * 0.7f, cap = StrokeCap.Round)
            drawCircle(ink, r, Offset(px, py))
            drawCircle(Color(0xFFFF2D95), r, Offset(px, py), style = Stroke(1.8f))
            drawCircle(Color.White.copy(alpha = 0.85f), r * 0.28f, Offset(px - r * 0.3f, py - r * 0.3f))
            drawCircle(ink, r * 0.3f, Offset(px + r * 1.5f, py - r * 0.8f)); drawCircle(ink, r * 0.22f, Offset(px - r * 1.2f, py + r * 1.2f))
        }
        "leaf_wake" -> stamps(ox, oy, y, h, time, speedLevel, 9, 1.4f, yAt) { px, py0, f, seed ->
            val py = py0 + sin(f * 9f + seed) * u * 1.4f
            val r = u * (1.6f - 0.5f * f)
            val col = when (((seed % 3) + 3) % 3) { 0 -> Color(0xFF4CAF50); 1 -> Color(0xFF9CCC65); else -> Color(0xFFFFB300) }
            val lp = Path().apply {
                moveTo(px - r, py); quadraticBezierTo(px, py - r * 0.75f, px + r, py); quadraticBezierTo(px, py + r * 0.75f, px - r, py); close()
            }
            rotate(seed * 40f + f * 260f, Offset(px, py)) {
                drawPath(lp, INK.copy(alpha = 0.9f), style = Stroke(3.5f)); drawPath(lp, col, alpha = 1f - f * 0.6f)
                drawLine(Color(0x99103010), Offset(px - r, py), Offset(px + r * 0.9f, py), strokeWidth = 1.6f)
            }
        }
        "constellation" -> {
            val pts = ArrayList<Triple<Offset, Float, Int>>()
            stamps(ox, oy, y, h, time, speedLevel, 9, 1.3f, yAt) { px, py, f, seed ->
                pts.add(Triple(Offset(px, py + (rnd(seed) - 0.5f) * u * 3.5f), f, seed))
            }
            for (i in 0 until pts.size - 1) drawLine(Color.White.copy(alpha = 0.5f * (1f - pts[i + 1].second)), pts[i].first, pts[i + 1].first, strokeWidth = 1.4f)
            for ((c, f, seed) in pts) {
                val big = rnd(seed * 4) > 0.6f
                sparkle(c, u * (if (big) 1.3f else 0.8f) * (1f - f * 0.4f), if (big) Color(0xFFFFE082) else Color.White, 1f - f * 0.7f)
            }
        }
        "prism_shards" -> stamps(ox, oy, y, h, time, speedLevel, 10, 1.3f, yAt) { px, py0, f, seed ->
            val py = py0 + (rnd(seed) - 0.5f) * u * 3f
            val r = u * (0.8f + 0.6f * rnd(seed * 3))
            val col = Color.hsv(((seed * 47) % 360 + 360 + time * 60f) % 360f, 0.55f, 1f)
            val tri = Path().apply { moveTo(px, py - r); lineTo(px + r * 0.9f, py + r * 0.7f); lineTo(px - r * 0.9f, py + r * 0.7f); close() }
            val inner = Path().apply { moveTo(px, py - r * 0.35f); lineTo(px + r * 0.35f, py + r * 0.3f); lineTo(px - r * 0.35f, py + r * 0.3f); close() }
            rotate(seed * 57f + f * 300f, Offset(px, py)) {
                drawPath(tri, col, alpha = 1f - f * 0.6f); drawPath(inner, Color.White.copy(alpha = 0.55f - f * 0.4f))
                drawPath(tri, Color.White.copy(alpha = 0.85f - f * 0.6f), style = Stroke(1.4f))
            }
        }
        "circuit_trace" -> {
            val step = u * 1.3f
            val n = 26
            val life = 1.2f
            val xs = FloatArray(n); val ysq = FloatArray(n)
            for (k in 0 until n) {
                val age = k * life / (n - 1)
                xs[k] = ox - age * (v / life)
                ysq[k] = ((yAt(age) + (oy - y)) / step).roundToInt() * step
            }
            val copper = Color(0xFFFFB300)
            for (k in 0 until n - 1) {
                val f = k / (n - 1f)
                val a = 1f - f * 0.85f
                // horizontal run, then the vertical hop at the corner
                drawLine(INK.copy(alpha = a), Offset(xs[k], ysq[k]), Offset(xs[k + 1], ysq[k]), strokeWidth = u * 0.95f)
                drawLine(copper.copy(alpha = a), Offset(xs[k], ysq[k]), Offset(xs[k + 1], ysq[k]), strokeWidth = u * 0.5f)
                if (ysq[k + 1] != ysq[k]) {
                    drawLine(INK.copy(alpha = a), Offset(xs[k + 1], ysq[k]), Offset(xs[k + 1], ysq[k + 1]), strokeWidth = u * 0.95f)
                    drawLine(copper.copy(alpha = a), Offset(xs[k + 1], ysq[k]), Offset(xs[k + 1], ysq[k + 1]), strokeWidth = u * 0.5f)
                    drawCircle(copper.copy(alpha = a), u * 0.6f, Offset(xs[k + 1], ysq[k + 1]))
                    drawCircle(INK.copy(alpha = a), u * 0.22f, Offset(xs[k + 1], ysq[k + 1]))
                }
            }
            val pk = (((time * 1.4f) % 1f) * (n - 1)).toInt().coerceIn(0, n - 1)   // data pulse running down the board
            drawCircle(Color.White, u * 0.5f, Offset(xs[pk], ysq[pk]))
            drawCircle(Color(0xFF00E5FF), u * 0.5f, Offset(xs[pk], ysq[pk]), style = Stroke(2f))
        }
        "sonic_rings" -> stamps(ox, oy, y, h, time, speedLevel, 7, 1.2f, yAt) { px, py, f, _ ->
            val r = u * (0.8f + 3.6f * f)
            val col = lerp(Color(0xFFFF2D95), Color(0xFF7C4DFF), f)
            for (k in 0..1) {
                val rr = r * (1f - k * 0.38f)
                val w = (u * 0.45f * (1f - f)).coerceAtLeast(1.4f)
                drawArc(INK.copy(alpha = 1f - f), 135f, 90f, false, Offset(px - rr * 0.6f, py - rr), Size(rr * 1.2f, rr * 2f), style = Stroke(w + 3f, cap = StrokeCap.Round))
                drawArc(col.copy(alpha = 1f - f), 135f, 90f, false, Offset(px - rr * 0.6f, py - rr), Size(rr * 1.2f, rr * 2f), style = Stroke(w, cap = StrokeCap.Round))
            }
        }
        "heartbeat_pulse" -> {
            val n = 40
            val life = 1.3f
            val green = Color(0xFF39FF14)
            var prev = Offset(ox, oy)
            for (k in 1..n) {
                val age = k * life / n
                val ph = ((time - age) * 1.8f).let { it - floor(it) }
                val p = Offset(ox - age * v / life, yAt(age) + (oy - y) - ecg(ph) * h * 0.34f)
                val a = 1f - k / n.toFloat()
                drawLine(INK.copy(alpha = a), prev, p, strokeWidth = u * 0.85f, cap = StrokeCap.Round)
                drawLine(green.copy(alpha = a), prev, p, strokeWidth = u * 0.4f, cap = StrokeCap.Round)
                prev = p
            }
            drawCircle(Color.White, u * 0.45f, Offset(ox, oy))
        }
        "gold_rush" -> stamps(ox, oy, y, h, time, speedLevel, 9, 1.3f, yAt) { px, py0, f, seed ->
            val py = py0 + sin(f * 7f + seed) * u * 1.3f
            val r = u * (1.3f - 0.35f * f)
            val wdt = (r * abs(cos(seed * 1.3f + f * 12f))).coerceAtLeast(r * 0.14f)
            val tl = Offset(px - wdt, py - r); val sz = Size(wdt * 2f, r * 2f)
            drawOval(Color(0xFF8A5A00), tl - Offset(1.5f, 1.5f), Size(sz.width + 3f, sz.height + 3f))
            drawOval(Color(0xFFFFC107), tl, sz, alpha = 1f - f * 0.5f)
            drawOval(Color(0xFFFFE57F), Offset(px - wdt * 0.6f, py - r * 0.6f), Size(wdt * 1.2f, r * 1.2f), alpha = 0.8f - f * 0.5f)
            if (rnd(seed * 6) > 0.7f) sparkle(Offset(px + r, py - r), u * 0.9f, Color.White, 1f - f)
        }
        "starburst_wake" -> stamps(ox, oy, y, h, time, speedLevel, 6, 1.4f, yAt) { px, py, f, seed ->
            val s = u * (0.9f + 3.0f * sin(f * PI.toFloat()).coerceAtLeast(0.1f))
            val star = Path().apply {
                for (k in 0 until 16) {
                    val ang = k * PI.toFloat() / 8f
                    val rad = if (k % 2 == 1) s * 0.28f else if (k % 4 == 0) s * 1.7f else s * 0.8f
                    val pt = Offset(px + cos(ang) * rad, py + sin(ang) * rad)
                    if (k == 0) moveTo(pt.x, pt.y) else lineTo(pt.x, pt.y)
                }
                close()
            }
            rotate(seed * 23f + f * 90f, Offset(px, py)) {
                drawPath(star, Color(0xFFFF2D95), style = Stroke(3f))
                drawPath(star, Color(0xFFFFD23F), alpha = 1f - f * 0.4f)
                drawCircle(Color.White, s * 0.3f, Offset(px, py))
            }
        }
        "veteran_comet" -> {
            val ice = Color(0xFF9BE7FF)
            val n = 16
            val life = 1.3f
            val tops = ArrayList<Offset>(); val bots = ArrayList<Offset>()
            for (k in 0..n) {
                val age = k * life / n
                val f = k / n.toFloat()
                val cx = ox - age * v / life; val cy = yAt(age) + (oy - y)
                val w = u * 1.5f * Math.pow((1f - f).toDouble(), 0.8).toFloat()
                tops.add(Offset(cx, cy - w)); bots.add(Offset(cx, cy + w))
            }
            val tail = Path().apply {
                moveTo(tops[0].x, tops[0].y); for (p in tops) lineTo(p.x, p.y); for (p in bots.reversed()) lineTo(p.x, p.y); close()
            }
            drawPath(tail, Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.95f), ice.copy(alpha = 0.55f), Color.Transparent), startX = ox, endX = ox - v))
            drawPath(tail, Color.White.copy(alpha = 0.5f), style = Stroke(1.2f))
            for (k in 0 until 4) {   // little stars riding the tail
                val f = ((time * 0.5f + k / 4f) % 1f)
                val age = f * life
                val c = Offset(ox - age * v / life, yAt(age) + (oy - y) + sin(f * 14f + k) * u * (1f + f * 1.5f))
                sparkle(c, u * 0.9f, Color.White, 1f - f)
            }
            drawCircle(Color.White, u * 0.8f, Offset(ox, oy)); drawCircle(ice, u * 1.3f, Offset(ox, oy), style = Stroke(2f))
        }
        else -> stamps(ox, oy, y, h, time, speedLevel, 12, 1.0f, yAt) { px, py, f, _ ->   // contrail
            val len = u * 2.4f * (1f - 0.5f * f)
            drawLine(INK.copy(alpha = 1f - f), Offset(px, py), Offset(px + len, py), strokeWidth = u * 0.95f, cap = StrokeCap.Round)
            drawLine(lerp(Color.White, Color(0xFF7FDBFF), f).copy(alpha = 1f - f * 0.8f), Offset(px, py), Offset(px + len, py), strokeWidth = u * 0.5f, cap = StrokeCap.Round)
        }
    }
}
