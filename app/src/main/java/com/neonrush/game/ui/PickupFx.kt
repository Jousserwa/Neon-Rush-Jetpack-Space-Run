package com.neonrush.game.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Power-up and fuel pick-up reactions. The game loop calls [onPickup] the instant something is collected; the draw code reads the
 * timestamps so everything animates on real time. Three layers of reaction:
 *  1. a burst at the pilot (rings, rays, sparks, screen-edge flash for the big ones),
 *  2. a living aura around the pilot for as long as the power-up lasts,
 *  3. the pilot itself changes (see-through for Invincibility and Ghost, squeezed for Shrink) and returns to normal at the end.
 * Ids are "PU1".."PU12" and "FUEL".
 */
object PickupFx {
    @Volatile var lastId = ""
    @Volatile var lastMs = -99999L
    private val starts = ConcurrentHashMap<String, Long>()

    private fun now(): Long = System.nanoTime() / 1_000_000L
    fun nowMs(): Long = now()
    fun reset() { lastId = ""; lastMs = -99999L; starts.clear() }
    fun onPickup(id: String) { lastId = id; lastMs = now(); starts[id] = lastMs }
    fun ageMs(): Long = (now() - lastMs).coerceAtLeast(0L)
    fun sinceStart(id: String): Long = (now() - (starts[id] ?: -99999L)).coerceAtLeast(0L)

    fun colorOf(id: String): Color = when (id) {
        "PU1" -> Color(0xFF5CE1FF)
        "PU2" -> Color(0xFFFF5252)
        "PU3" -> Color(0xFF4FC3F7)
        "PU4" -> Color(0xFFB388FF)
        "PU5" -> Color(0xFFFFD740)
        "PU6" -> Color(0xFFFF9100)
        "PU7" -> Color(0xFFFFE57F)
        "PU8" -> Color(0xFFFF6D00)
        "PU9" -> Color(0xFF69F0AE)
        "PU10" -> Color(0xFF00E5FF)
        "PU11" -> Color(0xFFE040FB)
        "PU12" -> Color(0xFFFFFFFF)
        "FUEL" -> Color(0xFF00FF88)
        else -> Color(0xFFFFFFFF)
    }
}

private fun lerp(a: Float, b: Float, f: Float) = a + (b - a) * f.coerceIn(0f, 1f)

/** How see-through the pilot is right now. Invincibility = a golden half-ghost, Ghost Mode = deeper. Always still clearly visible. */
fun pilotAlphaFor(dur: Map<String, Int>, t: Float): Float {
    val inv = maxOf(dur["PU7"] ?: 0, dur["PU12"] ?: 0)
    val gho = dur["PU4"] ?: 0
    if (inv <= 0 && gho <= 0) return 1f
    var target = 1f
    var rem = Int.MAX_VALUE
    var started = 0L
    if (inv > 0) { target = minOf(target, 0.62f + 0.08f * sin(t * 6f)); rem = minOf(rem, inv); started = maxOf(started, PickupFx.sinceStart(if ((dur["PU7"] ?: 0) > 0) "PU7" else "PU12")) }
    if (gho > 0) { target = minOf(target, 0.46f + 0.06f * sin(t * 5f)); rem = minOf(rem, gho); started = maxOf(started, PickupFx.sinceStart("PU4")) }
    // fade out over the last ~6 ticks, with a quick flicker as a warning, then it is solid again
    val endF = (rem / 6f).coerceIn(0f, 1f)
    val flick = if (rem <= 6) 0.82f + 0.18f * sin(t * 34f) else 1f
    val inF = (started / 320f).coerceIn(0f, 1f)           // fade into see-through right after the pick-up
    return (lerp(1f, lerp(1f, target, inF), endF) * flick).coerceIn(0.3f, 1f)
}

/** Shrink squeezes the pilot (matching its smaller hitbox) with a little pop at both ends. */
fun pilotScaleFor(dur: Map<String, Int>): Float {
    val rem = dur["PU9"] ?: 0
    if (rem <= 0) return 1f
    val inF = (PickupFx.sinceStart("PU9") / 260f).coerceIn(0f, 1f)
    val outF = (rem / 5f).coerceIn(0f, 1f)
    val squeeze = lerp(1f, 0.8f, inF)
    return lerp(1f, squeeze, outF)
}

private fun DrawScope.glow(c: Offset, r: Float, col: Color, a: Float) {
    if (r <= 0f) return
    drawCircle(Brush.radialGradient(listOf(col.copy(alpha = a.coerceIn(0f, 1f)), Color.Transparent), center = c, radius = r),
        radius = r, center = c, blendMode = BlendMode.Plus)
}

private fun DrawScope.spark(c: Offset, s: Float, col: Color, a: Float) {
    val w = (s * 0.28f).coerceAtLeast(1f)
    val cc = col.copy(alpha = a.coerceIn(0f, 1f))
    drawLine(cc, Offset(c.x - s, c.y), Offset(c.x + s, c.y), strokeWidth = w, cap = StrokeCap.Round)
    drawLine(cc, Offset(c.x, c.y - s), Offset(c.x, c.y + s), strokeWidth = w, cap = StrokeCap.Round)
    drawLine(Color.White.copy(alpha = a.coerceIn(0f, 1f) * 0.9f), Offset(c.x - s * 0.4f, c.y), Offset(c.x + s * 0.4f, c.y), strokeWidth = w * 0.6f, cap = StrokeCap.Round)
}

/** A soft glow and a few orbiting sparks behind each power-up and fuel cell on the track, so they pop. */
fun DrawScope.drawPickupHalo(id: String, x: Float, y: Float, size: Float, tick: Int) {
    val col = PickupFx.colorOf(id)
    val p = 0.5f + 0.5f * sin(tick * 0.3f)
    glow(Offset(x, y), size * (0.85f + 0.12f * p), col, 0.32f + 0.12f * p)
    for (k in 0 until 3) {
        val a = tick * 0.12f + k * 2.0944f
        spark(Offset(x + cos(a) * size * 0.62f, y + sin(a) * size * 0.62f), size * 0.07f, col, 0.75f)
    }
}

/** Living aura around the pilot for as long as each timed power-up lasts. Draw BEFORE the pilot so it sits behind. */
fun DrawScope.drawPickupAura(dur: Map<String, Int>, x: Float, y: Float, h: Float, t: Float) {
    if (dur.isEmpty()) return
    val c = Offset(x, y)
    fun left(id: String) = dur[id] ?: 0
    // the aura thins out over the last few ticks so the end of a power-up is felt, not sudden
    fun life(id: String) = (left(id) / 6f).coerceIn(0f, 1f)

    // INVINCIBILITY / LEGENDARY: golden glow and orbiting stars
    val inv = maxOf(left("PU7"), left("PU12"))
    if (inv > 0) {
        val lf = (inv / 6f).coerceIn(0f, 1f)
        val col = Color(0xFFFFE57F)
        val p = 0.5f + 0.5f * sin(t * 5f)
        glow(c, h * 0.95f, col, (0.20f + 0.14f * p) * lf)
        for (k in 0 until 6) {
            val a = t * 1.7f + k * (2f * PI.toFloat() / 6f)
            spark(Offset(x + cos(a) * h * 0.46f, y + sin(a) * h * 0.6f), h * 0.05f * (0.8f + 0.4f * p), col, 0.85f * lf)
        }
    }
    // GHOST: violet after-images trailing behind
    if (left("PU4") > 0) {
        val lf = life("PU4")
        val col = Color(0xFFB388FF)
        glow(c, h * 0.85f, col, 0.16f * lf)
        for (k in 1..3) {
            val dx = -h * 0.13f * k
            val wob = sin(t * 4f + k) * h * 0.02f
            drawOval(col.copy(alpha = (0.20f - 0.04f * k) * lf), topLeft = Offset(x + dx - h * 0.2f, y - h * 0.46f + wob), size = Size(h * 0.4f, h * 0.92f),
                style = Stroke(width = h * 0.012f))
        }
    }
    // MAGNET: field rings pulled in toward the pilot, red and blue like a horseshoe
    if (left("PU2") > 0) {
        val lf = life("PU2")
        for (k in 0 until 3) {
            val f = ((t * 0.9f + k / 3f) % 1f)
            val r = h * (1.15f - 0.75f * f)
            val a = f * 0.55f * lf
            val tl = Offset(x - r, y - r); val sz = Size(r * 2f, r * 2f)
            drawArc(Color(0xFFFF5252).copy(alpha = a), startAngle = 200f, sweepAngle = 140f, useCenter = false, topLeft = tl, size = sz, style = Stroke(width = h * 0.03f, cap = StrokeCap.Round))
            drawArc(Color(0xFF448AFF).copy(alpha = a), startAngle = 20f, sweepAngle = 140f, useCenter = false, topLeft = tl, size = sz, style = Stroke(width = h * 0.03f, cap = StrokeCap.Round))
        }
    }
    // TIME SLOW: a clock face around the pilot with a hand that sweeps backwards
    if (left("PU3") > 0) {
        val lf = life("PU3")
        val col = Color(0xFF4FC3F7)
        val r = h * 0.72f
        glow(c, h, col, 0.10f * lf)
        drawCircle(col.copy(alpha = 0.40f * lf), radius = r, center = c, style = Stroke(width = h * 0.012f))
        for (k in 0 until 12) {
            val a = k * (2f * PI.toFloat() / 12f)
            val r0 = r * (if (k % 3 == 0) 0.86f else 0.92f)
            drawLine(col.copy(alpha = 0.55f * lf), Offset(x + cos(a) * r0, y + sin(a) * r0), Offset(x + cos(a) * r, y + sin(a) * r), strokeWidth = h * 0.012f, cap = StrokeCap.Round)
        }
        val ha = -t * 1.6f
        drawLine(Color.White.copy(alpha = 0.8f * lf), c, Offset(x + cos(ha) * r * 0.8f, y + sin(ha) * r * 0.8f), strokeWidth = h * 0.014f, cap = StrokeCap.Round)
    }
    // SCORE x2 / x5: gold sparks rising off the pilot (x5 is hotter, thicker and orange)
    val x5 = left("PU6") > 0
    if (left("PU5") > 0 || x5) {
        val lf = life(if (x5) "PU6" else "PU5")
        val col = if (x5) Color(0xFFFF9100) else Color(0xFFFFD740)
        glow(c, h * 0.8f, col, (if (x5) 0.22f else 0.14f) * lf)
        val n = if (x5) 9 else 6
        for (k in 0 until n) {
            val f = ((t * 0.8f + k / n.toFloat()) % 1f)
            val sx = x + sin(k * 2.3f + t) * h * 0.38f
            val sy = y + h * 0.5f - f * h * 1.1f
            spark(Offset(sx, sy), h * 0.045f * (1f - 0.5f * f), col, (1f - f) * lf)
        }
    }
    // SHRINK: chevrons squeezing in on the pilot
    if (left("PU9") > 0) {
        val lf = life("PU9")
        val col = Color(0xFF69F0AE)
        val p = 0.5f + 0.5f * sin(t * 6f)
        val d = h * (0.62f - 0.1f * p)
        for (k in 0 until 4) {
            val a = k * (PI.toFloat() / 2f) + PI.toFloat() / 4f
            val px = x + cos(a) * d; val py = y + sin(a) * d
            val ix = x + cos(a) * (d - h * 0.1f); val iy = y + sin(a) * (d - h * 0.1f)
            drawLine(col.copy(alpha = 0.75f * lf), Offset(px, py), Offset(ix, iy), strokeWidth = h * 0.02f, cap = StrokeCap.Round)
        }
    }
    // LEGENDARY AURA: a slow rainbow ring
    if (left("PU12") > 0) {
        val lf = life("PU12")
        val rr = h * 0.78f
        rotate(degrees = t * 60f, pivot = c) {
            drawCircle(
                brush = Brush.sweepGradient(listOf(Color(0xFFFF5252), Color(0xFFFFEB3B), Color(0xFF69F0AE), Color(0xFF40C4FF), Color(0xFFE040FB), Color(0xFFFF5252)), center = c),
                radius = rr, center = c, alpha = 0.7f * lf, style = Stroke(width = h * 0.03f)
            )
        }
    }
}

/** The big one-off reaction at the moment of pick-up. Draw AFTER the pilot so it sits in front. */
fun DrawScope.drawPickupBurst(x: Float, y: Float, h: Float) {
    val id = PickupFx.lastId
    if (id.isEmpty()) return
    val age = PickupFx.ageMs()
    val dur = when (id) { "PU8", "PU11" -> 1100L; "PU12" -> 1000L; else -> 780L }
    if (age >= dur) return
    val f = age / dur.toFloat()
    val ease = 1f - (1f - f) * (1f - f)
    val a = (1f - f).coerceIn(0f, 1f)
    val c = Offset(x, y)
    val col = PickupFx.colorOf(id)

    // bright flash at the pilot
    glow(c, h * (0.7f + 1.5f * ease), col, 0.75f * a)
    // two rings, the second a beat behind
    drawCircle(col.copy(alpha = a), radius = h * (0.35f + 1.25f * ease), center = c, style = Stroke(width = h * 0.04f * (1f - 0.7f * f)))
    val f2 = ((age - 120L) / dur.toFloat()).coerceIn(0f, 1f)
    if (f2 > 0f) {
        drawCircle(Color.White.copy(alpha = (1f - f2) * 0.8f), radius = h * (0.3f + 1.0f * (1f - (1f - f2) * (1f - f2))), center = c, style = Stroke(width = h * 0.02f))
    }
    // rays
    val rays = if (id == "PU12") 18 else 12
    for (k in 0 until rays) {
        val ang = k * (2f * PI.toFloat() / rays) + 0.2f
        val r0 = h * (0.4f + 0.7f * ease)
        val r1 = r0 + h * 0.22f * (1f - f)
        drawLine(col.copy(alpha = a), Offset(x + cos(ang) * r0, y + sin(ang) * r0), Offset(x + cos(ang) * r1, y + sin(ang) * r1),
            strokeWidth = h * 0.02f, cap = StrokeCap.Round)
    }

    when (id) {
        "FUEL" -> {
            // droplets of fuel streaming into the pilot, then a green pulse over the whole pilot
            for (k in 0 until 10) {
                val ang = k * (2f * PI.toFloat() / 10f) + 0.4f
                val r = h * 1.1f * (1f - ease) + h * 0.12f
                drawCircle(col.copy(alpha = (0.4f + 0.6f * a)), radius = h * 0.028f, center = Offset(x + cos(ang) * r, y + sin(ang) * r))
            }
            glow(c, h * 0.7f, col, 0.22f * sin(f * PI.toFloat()))
        }
        "PU1" -> {
            // shield: a hexagon snaps around the pilot
            val hr = h * (0.55f + 0.2f * ease)
            val path = androidx.compose.ui.graphics.Path()
            for (k in 0..5) {
                val ang = (PI / 3.0 * k + PI / 6.0).toFloat()
                val px = x + cos(ang) * hr; val py = y + sin(ang) * hr
                if (k == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
            path.close()
            drawPath(path, Color.White.copy(alpha = a), style = Stroke(width = h * 0.03f))
        }
        "PU8" -> {
            // boom: a shockwave that races across the screen
            val rr = size.width * 0.95f * ease
            drawCircle(col.copy(alpha = 0.55f * a), radius = rr, center = c, style = Stroke(width = h * 0.09f * (1f - 0.8f * f)))
            edgeFlash(col, 0.38f * a)
        }
        "PU11" -> {
            // zone skip: speed lines racing past
            for (k in 0 until 14) {
                val yy = size.height * ((k * 0.0731f + 0.04f) % 1f)
                val len = size.width * (0.25f + 0.5f * ((k * 37 % 10) / 10f))
                val sx = size.width * (1.1f - 1.4f * ease) + (k % 4) * h * 0.2f
                drawLine(Color.White.copy(alpha = 0.55f * a), Offset(sx, yy), Offset(sx + len, yy), strokeWidth = h * 0.012f, cap = StrokeCap.Round)
            }
            edgeFlash(Color.White, 0.5f * a)
        }
        "PU10" -> {
            // lane warp: a vertical snap line and a cyan streak behind the pilot
            drawLine(col.copy(alpha = a), Offset(x, 0f), Offset(x, size.height), strokeWidth = h * 0.014f)
            drawLine(col.copy(alpha = 0.5f * a), Offset(x - h * 1.6f * ease, y), Offset(x, y), strokeWidth = h * 0.1f, cap = StrokeCap.Round)
        }
        "PU12" -> {
            for (k in 0 until 3) {
                val hue = listOf(Color(0xFFFF5252), Color(0xFFFFEB3B), Color(0xFF40C4FF))[k]
                drawCircle(hue.copy(alpha = 0.6f * a), radius = h * (0.5f + (1.2f + 0.25f * k) * ease), center = c, style = Stroke(width = h * 0.03f))
            }
            edgeFlash(Color(0xFFE040FB), 0.4f * a)
        }
        "PU7" -> edgeFlash(col, 0.22f * a)
        else -> {}
    }
}

private fun DrawScope.edgeFlash(col: Color, a: Float) {
    drawRect(
        Brush.radialGradient(listOf(Color.Transparent, Color.Transparent, col.copy(alpha = a.coerceIn(0f, 1f))),
            center = Offset(size.width / 2f, size.height / 2f), radius = maxOf(size.width, size.height) * 0.72f),
        size = size
    )
}
