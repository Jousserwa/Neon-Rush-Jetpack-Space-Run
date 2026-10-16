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
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
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
    val squeeze = lerp(1f, 0.8f, inF) - 0.07f * sin(inF * PI.toFloat())   // a little overshoot, then it settles
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

/**
 * Idle animation of every item on the track, each matching what it does: the shield turns a hexagon, the magnet pulls dots in,
 * time-slow ticks a clock hand, the ghost flickers and drifts wisps, x2 twinkles, x5 burns, invincibility spins stars, boom ticks
 * like a fuse, shrink squeezes chevrons, lane-warp scans, zone-skip races chevrons, legendary turns a rainbow, fuel bubbles.
 */
fun DrawScope.drawPickupHalo(id: String, x: Float, y: Float, size: Float, tick: Int) {
    val col = PickupFx.colorOf(id)
    val t = (PickupFx.nowMs() % 600000L) / 1000f
    val p = 0.5f + 0.5f * sin(t * 3.6f)
    val c = Offset(x, y)
    val r = size * 0.62f
    val pi = PI.toFloat()
    fxGlow(c, size * (0.85f + 0.12f * p), col, 0.30f + 0.12f * p)
    when (id) {
        "PU1" -> {
            val path = Path()
            for (k in 0..5) {
                val ang = t * 1.2f + k * (pi / 3f)
                if (k == 0) path.moveTo(x + cos(ang) * r, y + sin(ang) * r) else path.lineTo(x + cos(ang) * r, y + sin(ang) * r)
            }
            path.close()
            drawPath(path, col.copy(alpha = 0.85f), style = Stroke(width = size * 0.04f))
            val sa = t * 1.2f
            fxSpark(Offset(x + cos(sa) * r, y + sin(sa) * r), size * 0.07f, Color.White, 0.9f)
        }
        "PU2" -> {
            for (k in 0..1) {
                drawArc((if (k == 0) Color(0xFFFF5252) else Color(0xFF448AFF)).copy(alpha = 0.9f), startAngle = t * 140f + k * 180f, sweepAngle = 110f, useCenter = false,
                    topLeft = Offset(x - r, y - r), size = Size(r * 2f, r * 2f), style = Stroke(width = size * 0.06f, cap = StrokeCap.Round))
            }
            for (k in 0 until 4) {
                val f = (t * 0.9f + k / 4f) % 1f
                val d = r * 1.5f * (1f - f)
                val ang = k * 1.57f + t
                drawCircle(Color.White.copy(alpha = f * 0.9f), radius = size * 0.03f, center = Offset(x + cos(ang) * d, y + sin(ang) * d))
            }
        }
        "PU3" -> {
            drawCircle(col.copy(alpha = 0.7f), radius = r, center = c, style = Stroke(width = size * 0.03f))
            val ha = Math.floor((t * 2f).toDouble()).toFloat() * (pi / 6f) - pi / 2f      // ticks in steps like a clock
            drawLine(Color.White.copy(alpha = 0.9f), c, Offset(x + cos(ha) * r * 0.85f, y + sin(ha) * r * 0.85f), strokeWidth = size * 0.05f, cap = StrokeCap.Round)
            for (k in 0 until 4) {
                val a2 = k * (pi / 2f)
                drawLine(col.copy(alpha = 0.8f), Offset(x + cos(a2) * r * 0.86f, y + sin(a2) * r * 0.86f), Offset(x + cos(a2) * r, y + sin(a2) * r), strokeWidth = size * 0.03f)
            }
        }
        "PU4" -> {
            drawCircle(col.copy(alpha = 0.25f + 0.45f * Math.abs(sin(t * 2f))), radius = r, center = c, style = Stroke(width = size * 0.03f))
            for (k in 0 until 3) {
                val f = (t * 0.5f + k / 3f) % 1f
                drawCircle(col.copy(alpha = (1f - f) * 0.6f), radius = size * 0.05f, center = Offset(x + sin(t * 2f + k * 2f) * size * 0.3f, y + size * 0.5f - f * size * 1.1f))
            }
        }
        "PU5" -> for (k in 0 until 4) {
            val ph = (t * 1.3f + k * 0.25f) % 1f
            val ang = k * 1.7f
            fxSpark(Offset(x + cos(ang) * r, y + sin(ang) * r), size * 0.12f * sin(ph * pi), col, sin(ph * pi))
        }
        "PU6" -> {
            for (k in 0 until 5) {
                val ang = -pi / 2f + (k - 2) * 0.5f
                val base = Offset(x + cos(ang) * r * 0.7f, y + sin(ang) * r * 0.7f)
                val len = size * (0.25f + 0.15f * sin(t * 9f + k * 2f))
                val tip = Offset(x + cos(ang) * (r * 0.7f + len), y + sin(ang) * (r * 0.7f + len))
                drawLine(col.copy(alpha = 0.85f), base, tip, strokeWidth = size * 0.07f, cap = StrokeCap.Round)
                drawLine(Color(0xFFFFEB3B).copy(alpha = 0.9f), base, tip, strokeWidth = size * 0.03f, cap = StrokeCap.Round)
            }
        }
        "PU7" -> {
            drawCircle(col.copy(alpha = 0.5f + 0.3f * p), radius = r * (0.95f + 0.08f * p), center = c, style = Stroke(width = size * 0.03f))
            for (k in 0 until 4) {
                val ang = t * 2.2f + k * (pi / 2f)
                fxSpark(Offset(x + cos(ang) * r, y + sin(ang) * r), size * 0.1f, col, 0.95f)
            }
        }
        "PU8" -> {
            val rp = (t * 2.5f) % 1f
            drawCircle(col.copy(alpha = (1f - rp) * 0.8f), radius = r * (0.8f + 0.5f * rp), center = c, style = Stroke(width = size * 0.04f))
            fxSpark(Offset(x + size * 0.05f * sin(t * 30f), y - r * 1.05f), size * 0.08f * (0.6f + 0.4f * sin(t * 40f)), Color.White, 0.95f)
        }
        "PU9" -> for (k in 0 until 4) {
            val ang = k * (pi / 2f) + pi / 4f
            val d = r * (1.05f - 0.25f * p)
            drawLine(col.copy(alpha = 0.8f), Offset(x + cos(ang) * d, y + sin(ang) * d), Offset(x + cos(ang) * (d - size * 0.12f), y + sin(ang) * (d - size * 0.12f)), strokeWidth = size * 0.04f, cap = StrokeCap.Round)
        }
        "PU10" -> {
            val sx = x + sin(t * 3f) * r
            drawLine(col.copy(alpha = 0.85f), Offset(sx, y - r), Offset(sx, y + r), strokeWidth = size * 0.03f, cap = StrokeCap.Round)
            drawLine(col.copy(alpha = 0.4f), Offset(x - r, y), Offset(x + r, y), strokeWidth = size * 0.015f)
        }
        "PU11" -> for (k in 0 until 3) {
            val f = (t * 1.4f + k / 3f) % 1f
            val cx = x + r * (1f - 2f * f)
            val q = size * 0.14f
            val path = Path()
            path.moveTo(cx + q, y - q); path.lineTo(cx, y); path.lineTo(cx + q, y + q)
            drawPath(path, Color.White.copy(alpha = sin(f * pi)), style = Stroke(width = size * 0.04f, cap = StrokeCap.Round))
        }
        "PU12" -> rotate(degrees = t * 90f, pivot = c) {
            drawCircle(
                brush = Brush.sweepGradient(listOf(Color(0xFFFF5252), Color(0xFFFFEB3B), Color(0xFF69F0AE), Color(0xFF40C4FF), Color(0xFFE040FB), Color(0xFFFF5252)), center = c),
                radius = r, center = c, alpha = 0.9f, style = Stroke(width = size * 0.05f)
            )
        }
        "FUEL" -> for (k in 0 until 3) {
            val f = (t * 0.9f + k / 3f) % 1f
            drawCircle(col.copy(alpha = (1f - f) * 0.85f), radius = size * 0.045f * (1f - 0.5f * f), center = Offset(x + sin(k * 2.1f + t * 2f) * r * 0.5f, y + r - f * 2f * r))
            val ang = t * 2f + k * 2.0944f
            drawCircle(col.copy(alpha = 0.8f), radius = size * 0.035f, center = Offset(x + cos(ang) * r, y + sin(ang) * r))
        }
        else -> for (k in 0 until 3) {
            val a = tick * 0.12f + k * 2.0944f
            fxSpark(Offset(x + cos(a) * size * 0.62f, y + sin(a) * size * 0.62f), size * 0.07f, col, 0.75f)
        }
    }
}

/** The diamond: a glint sweeps across it every second or so and a star twinkles on its corner. */
fun DrawScope.drawGemShine(x: Float, y: Float, size: Float) {
    val t = (PickupFx.nowMs() % 600000L) / 1000f
    val col = Color(0xFF00E5FF)
    fxGlow(Offset(x, y), size * 0.9f, col, 0.18f + 0.08f * sin(t * 4f))
    val ph = (t % 1.6f) / 0.5f
    if (ph < 1f) {
        val sx = x - size * 0.5f + size * ph
        drawLine(Color.White.copy(alpha = sin(ph * PI.toFloat()) * 0.85f), Offset(sx - size * 0.18f, y + size * 0.35f), Offset(sx + size * 0.18f, y - size * 0.35f), strokeWidth = size * 0.08f, cap = StrokeCap.Round)
    }
    val tw = 0.5f + 0.5f * sin(t * 5f + 1f)
    fxSpark(Offset(x + size * 0.32f, y - size * 0.32f), size * 0.12f * tw, Color.White, tw)
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

/**
 * How the pilot body itself moves, from power-ups, hits and diamonds: a pop on every pick-up, recoil from Boom, a stretch for
 * Lane Warp and Zone Skip, a lean for the Magnet, slow breathing for Time Slow, a float for Ghost, pulses for the score boosts and
 * Invincibility. Obstacle hits are mixed in (knock-back, shake, spin) and the blink after a hit comes out as [PilotMotion.alpha].
 */
fun pilotMotionFor(dur: Map<String, Int>, h: Float, t: Float): PilotMotion {
    var dx = 0f; var dy = 0f; var rot = 0f; var sx = 1f; var sy = 1f
    fun left(id: String) = dur[id] ?: 0
    val age = PickupFx.ageMs()
    val id = PickupFx.lastId
    if (id.isNotEmpty() && age < 700L) {
        val f = age / 700f
        val d = (1f - f) * (1f - f)
        val pop = when (id) { "FUEL" -> 0.10f; "PU8" -> 0.06f; "PU9" -> 0f; else -> 0.14f }
        val k = 1f + pop * d * (0.6f + 0.4f * cos(age * 0.03f))
        sx *= k; sy *= k
        when (id) {
            "PU8" -> { dx -= h * 0.30f * d; rot -= 12f * d }
            "PU10" -> { sx *= 1f + 0.35f * d; sy *= 1f - 0.18f * d }
            "PU11" -> { sx *= 1f + 0.7f * d; dx += h * 0.12f * (1f - f) }
            "PU9" -> rot += 6f * sin(age * 0.05f) * d
            "PU2" -> rot += 8f * sin(age * 0.04f) * d
        }
    }
    if (left("PU2") > 0) { rot += 4f * sin(t * 2.4f); dx += h * 0.015f }
    if (left("PU3") > 0) { val b = sin(t * 1.3f) * 0.025f; sx *= 1f - b; sy *= 1f + b }
    if (left("PU4") > 0) dy += sin(t * 1.8f) * h * 0.03f
    if (left("PU5") > 0) { val b = sin(t * 8f) * 0.025f; sx *= 1f + b; sy *= 1f + b }
    if (left("PU6") > 0) { val b = sin(t * 11f) * 0.04f; sx *= 1f + b; sy *= 1f + b; rot += 2f * sin(t * 13f) }
    if (maxOf(left("PU7"), left("PU12")) > 0) { val b = sin(t * 6f) * 0.02f; sx *= 1f + b; sy *= 1f + b }
    val hm = ReactFx.hitMotion(h)
    val gAge = ReactFx.nowMs() - ReactFx.gemMs
    var gp = 1f
    if (gAge in 0L..260L) { val f = gAge / 260f; gp = 1f + 0.06f * (1f - f) * (1f - f) }
    return PilotMotion(dx + hm.dx, dy + hm.dy, rot + hm.rot, sx * hm.sx * gp, sy * hm.sy * gp, hm.alpha)
}

/**
 * In-front pilot effects that belong to each power-up's nature: x2 / x5 badge, Invincible shine and shockwaves, Magnet horseshoe and
 * pulled streaks, Time Slow hourglass, Ghost wisps, Boom embers, Shrink twinkles, Lane Warp beam-in, Zone Skip speed streaks,
 * Legendary crown, Fuel fill-up wave. Draw AFTER the pilot.
 */
fun DrawScope.drawPilotPowerFx(dur: Map<String, Int>, x: Float, y: Float, h: Float, t: Float, measurer: TextMeasurer?) {
    val c = Offset(x, y)
    val pi = PI.toFloat()
    fun left(id: String) = dur[id] ?: 0
    fun life(id: String) = (left(id) / 6f).coerceIn(0f, 1f)

    // SCORE x2 / x5: a floating badge above the head
    val x5 = left("PU6") > 0
    if ((left("PU5") > 0 || x5) && measurer != null) {
        val lf = life(if (x5) "PU6" else "PU5")
        val col = if (x5) Color(0xFFFF9100) else Color(0xFFFFD740)
        val bob = sin(t * 4f) * h * 0.03f
        val tl = measurer.measure(if (x5) "x5" else "x2", style = TextStyle(color = col.copy(alpha = lf), fontSize = (h * 0.17f).toSp(), fontWeight = FontWeight.Black))
        fxGlow(Offset(x, y - h * 0.62f + bob), h * 0.28f, col, 0.35f * lf)
        drawText(textLayoutResult = tl, topLeft = Offset(x - tl.size.width / 2f, y - h * 0.62f + bob - tl.size.height / 2f))
    }

    // INVINCIBLE / LEGENDARY: a shine sweeps across the pilot and a shockwave leaves it every second
    val inv = maxOf(left("PU7"), left("PU12"))
    if (inv > 0) {
        val lf = life(if (left("PU7") > 0) "PU7" else "PU12")
        val ph = (t % 1.4f) / 0.45f
        if (ph < 1f) {
            val sx = x - h * 0.4f + h * 0.8f * ph
            drawLine(Color.White.copy(alpha = sin(ph * pi) * 0.85f * lf), Offset(sx - h * 0.1f, y + h * 0.4f), Offset(sx + h * 0.1f, y - h * 0.4f), strokeWidth = h * 0.06f, cap = StrokeCap.Round, blendMode = BlendMode.Plus)
        }
        val rp = (t * 0.9f) % 1f
        drawCircle(Color(0xFFFFE57F).copy(alpha = (1f - rp) * 0.55f * lf), radius = h * (0.4f + 0.7f * rp), center = c, style = Stroke(width = h * 0.02f))
    }

    // MAGNET: a horseshoe at the nose and streaks being pulled in from ahead
    if (left("PU2") > 0) {
        val lf = life("PU2")
        val hx = x + h * 0.42f
        val rr = h * 0.1f
        drawArc(Color(0xFFFF5252).copy(alpha = 0.9f * lf), startAngle = 90f, sweepAngle = 90f, useCenter = false, topLeft = Offset(hx - rr, y - rr), size = Size(rr * 2f, rr * 2f), style = Stroke(width = h * 0.035f, cap = StrokeCap.Round))
        drawArc(Color(0xFF448AFF).copy(alpha = 0.9f * lf), startAngle = 270f, sweepAngle = 90f, useCenter = false, topLeft = Offset(hx - rr, y - rr), size = Size(rr * 2f, rr * 2f), style = Stroke(width = h * 0.035f, cap = StrokeCap.Round))
        for (k in 0 until 5) {
            val f = (t * 1.2f + k / 5f) % 1f
            val sx = x + h * (1.3f - 0.85f * f)
            val sy = y + (k - 2) * h * 0.18f * (1f - f)
            drawLine((if (k % 2 == 0) Color(0xFFFF5252) else Color(0xFF448AFF)).copy(alpha = f * 0.8f * lf), Offset(sx, sy), Offset(sx + h * 0.12f, sy), strokeWidth = h * 0.02f, cap = StrokeCap.Round)
        }
    }

    // TIME SLOW: a tiny hourglass above the head with a falling grain
    if (left("PU3") > 0) {
        val lf = life("PU3")
        val col = Color(0xFF4FC3F7).copy(alpha = 0.9f * lf)
        val hy = y - h * 0.62f
        val q = h * 0.07f
        val p = Path()
        p.moveTo(x - q, hy - q * 1.3f); p.lineTo(x + q, hy - q * 1.3f); p.lineTo(x, hy); p.lineTo(x + q, hy + q * 1.3f); p.lineTo(x - q, hy + q * 1.3f); p.lineTo(x, hy); p.close()
        drawPath(p, col, style = Stroke(width = h * 0.015f))
        val g = (t * 0.8f) % 1f
        drawCircle(Color.White.copy(alpha = lf), radius = h * 0.012f, center = Offset(x, hy - q * 0.2f + g * q * 1.4f))
    }

    // GHOST: wisps rising off the pilot
    if (left("PU4") > 0) {
        val lf = life("PU4")
        for (k in 0 until 4) {
            val f = (t * 0.6f + k / 4f) % 1f
            drawCircle(Color(0xFFD1C4FF).copy(alpha = (1f - f) * 0.55f * lf), radius = h * 0.035f * (1f - 0.4f * f), center = Offset(x + sin(t * 2f + k * 1.7f) * h * 0.25f, y + h * 0.3f - f * h * 0.9f))
        }
    }

    // SHRINK: tight twinkles around the smaller pilot
    if (left("PU9") > 0) {
        val lf = life("PU9")
        for (k in 0 until 3) {
            val ang = t * 3f + k * 2.0944f
            fxSpark(Offset(x + cos(ang) * h * 0.38f, y + sin(ang) * h * 0.38f), h * 0.035f, Color(0xFF69F0AE), 0.9f * lf)
        }
    }

    // LEGENDARY: a little crown floating above the head, rainbow motes rising
    if (left("PU12") > 0) {
        val lf = life("PU12")
        val cy = y - h * 0.66f + sin(t * 3f) * h * 0.02f
        val w = h * 0.2f
        val p = Path()
        p.moveTo(x - w, cy + h * 0.07f); p.lineTo(x - w, cy - h * 0.04f); p.lineTo(x - w * 0.5f, cy + h * 0.02f); p.lineTo(x, cy - h * 0.08f)
        p.lineTo(x + w * 0.5f, cy + h * 0.02f); p.lineTo(x + w, cy - h * 0.04f); p.lineTo(x + w, cy + h * 0.07f); p.close()
        drawPath(p, Color(0xFFFFD740).copy(alpha = lf))
        drawPath(p, Color.White.copy(alpha = 0.8f * lf), style = Stroke(width = h * 0.012f))
        val hues = listOf(Color(0xFFFF5252), Color(0xFFFFEB3B), Color(0xFF69F0AE), Color(0xFF40C4FF), Color(0xFFE040FB))
        for (k in 0 until 5) {
            val f = (t * 0.7f + k / 5f) % 1f
            drawCircle(hues[k].copy(alpha = (1f - f) * 0.8f * lf), radius = h * 0.025f, center = Offset(x + (k - 2) * h * 0.14f, y + h * 0.1f - f * h * 0.8f))
        }
    }

    // ---- one-shot reactions to the last pick-up
    val id = PickupFx.lastId
    val age = PickupFx.ageMs()
    when (id) {
        "PU8" -> if (age < 900L) {          // Boom: scorch on the nose, embers falling
            val f = age / 900f
            val a = 1f - f
            fxGlow(Offset(x + h * 0.3f, y), h * (0.3f + 0.5f * f), Color(0xFFFF8A1F), 0.6f * a)
            for (k in 0 until 8) {
                val px = x + (fxRnd(7, k) - 0.5f) * h * 0.9f
                val py = y - h * 0.4f + f * h * 1.0f * (0.6f + fxRnd(7, k + 9))
                drawCircle(Color(0xFFFFB347).copy(alpha = a), radius = h * 0.02f, center = Offset(px, py))
            }
        }
        "PU10" -> if (age < 520L) {         // Lane Warp: beamed in, scan lines on the pilot
            val f = age / 520f
            val a = 1f - f
            drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Color(0xFF00E5FF).copy(alpha = 0.55f * a), Color.Transparent), startX = x - h * 0.3f, endX = x + h * 0.3f),
                topLeft = Offset(x - h * 0.3f, y - h * 0.8f), size = Size(h * 0.6f, h * 1.6f), blendMode = BlendMode.Plus)
            var yy = y - h * 0.5f
            var i = 0
            while (yy < y + h * 0.5f) {
                if ((i + (age / 40L).toInt()) % 2 == 0) drawLine(Color(0xFF00E5FF).copy(alpha = 0.6f * a), Offset(x - h * 0.4f, yy), Offset(x + h * 0.4f, yy), strokeWidth = h * 0.012f)
                yy += h * 0.08f; i++
            }
        }
        "PU11" -> if (age < 700L) {         // Zone Skip: speed streaks trailing behind
            val f = age / 700f
            val a = 1f - f
            for (k in 0 until 9) {
                val ly = y + (k - 4) * h * 0.11f
                drawLine((if (k % 2 == 0) Color.White else Color(0xFFE040FB)).copy(alpha = a * 0.8f), Offset(x - h * 0.3f, ly), Offset(x - h * (0.8f + 0.9f * fxRnd(3, k)) * (0.5f + f), ly), strokeWidth = h * 0.015f, cap = StrokeCap.Round)
            }
        }
        "FUEL" -> if (age < 800L) {         // Fuel: a green wave fills the pilot from the bottom, plus rising crosses
            val f = age / 800f
            val a = 1f - f
            val top = y + h * 0.5f - f * h * 1.0f
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color(0xFF00FF88).copy(alpha = 0.40f * a)), startY = top - h * 0.15f, endY = top + h * 0.05f),
                topLeft = Offset(x - h * 0.4f, top - h * 0.15f), size = Size(h * 0.8f, h * 0.2f), blendMode = BlendMode.Plus)
            for (k in 0 until 3) {
                val px = x + (k - 1) * h * 0.22f
                val py = y + h * 0.4f - f * h * (0.8f + 0.2f * k)
                val q = h * 0.04f
                drawLine(Color(0xFF00FF88).copy(alpha = a), Offset(px - q, py), Offset(px + q, py), strokeWidth = h * 0.015f, cap = StrokeCap.Round)
                drawLine(Color(0xFF00FF88).copy(alpha = a), Offset(px, py - q), Offset(px, py + q), strokeWidth = h * 0.015f, cap = StrokeCap.Round)
            }
        }
    }
}
