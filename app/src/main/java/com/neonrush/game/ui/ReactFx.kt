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
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Hit and pick-up reactions, for BOTH sides of every contact:
 *  - the pilot reacts differently to each kind of obstacle (knock-back and dizzy stars for a block, electric arcs for a laser,
 *    slashes for a blade, flames for lava, a glitch-bite for a blink creature...),
 *  - the obstacle reacts too (it shatters, splits, dissolves, explodes...) at the spot where it was hit,
 *  - every outcome looks different: a real hit, a shield deflect, an invincible smash, a ghost phase-through, a boom clear,
 *  - diamonds, fuel cells and power-ups also burst at the spot where they were collected.
 * The game loop only calls [ReactFx.obstacle] / [ReactFx.collect]; everything here animates on real time.
 */
object ReactFx {
    class Ev(val kind: String, val sub: String, val outcome: String, val xFrac: Float, val yPct: Float, val ms: Long, val seed: Int)

    private val evs = CopyOnWriteArrayList<Ev>()
    private var ctr = 0
    @Volatile var hitMs = -99999L
    @Volatile var hitFam = ""
    @Volatile var smashMs = -99999L
    @Volatile var phaseMs = -99999L
    @Volatile var gemMs = -99999L

    const val HIT_MS = 780L
    const val GEM_MS = 1000L
    /** Fuel a collected cell puts into the tank, in percent. */
    const val FUEL_PICKUP_PCT = 18

    fun nowMs(): Long = System.nanoTime() / 1_000_000L
    fun events(): List<Ev> = evs

    fun reset() {
        evs.clear()
        hitMs = -99999L; hitFam = ""; smashMs = -99999L; phaseMs = -99999L; gemMs = -99999L
    }

    /** Which reaction family an obstacle belongs to. */
    fun familyOf(sub: String): String = when (sub) {
        "PILLAR_TOP", "PILLAR_BOTTOM" -> "BLOCK"
        "BARRIER" -> "BARRIER"
        "TUNNEL_TOP", "TUNNEL_BOTTOM" -> "RUBBLE"
        "MOUNTAIN_TOP", "MOUNTAIN_BOTTOM" -> "ROCK"
        "STALACTITE", "STALAGMITE" -> "SPIKE"
        "LASER" -> "BEAM"
        "ZAP_FIELD", "FIRE_WALL" -> "ZAP"
        "BLADE" -> "BLADE"
        "SPLITTER" -> "SPLIT"
        "PHANTOM", "SHADOW_MIRROR", "SHADOW_SWARM", "SHADOW_STRAFER" -> "GHOST"
        "SHADOW_CHARGER" -> "RAM"
        "BLINK_HAZARD" -> "GLITCH"
        "LAVA_BALL" -> "LAVA"
        "", "DRONE_SHOT", "SHADOW_SHOT" -> "STING"
        else -> "BLAST"          // DRONE, STANDARD and anything new
    }

    /** outcome: "HIT" (hurts), "SHIELD" (deflected), "SMASH" (invincible), "PHASE" (ghost), "BOOM" (cleared by Boom). */
    fun obstacle(sub: String, outcome: String, xFrac: Float, yPct: Int) {
        val fam = familyOf(sub)
        val t = nowMs()
        add(Ev("obs", fam, outcome, xFrac, yPct.toFloat(), t, ++ctr))
        when (outcome) {
            "HIT" -> { hitMs = t; hitFam = fam }
            "SMASH" -> smashMs = t
            "PHASE" -> phaseMs = t
        }
    }

    /** kind: "gem", "fuel" or "pu" (sub = the power-up id). */
    fun collect(kind: String, sub: String, xFrac: Float, yPct: Int) {
        val t = nowMs()
        add(Ev(kind, if (kind == "pu") sub else "", if (kind == "pu") "" else sub, xFrac, yPct.toFloat(), t, ++ctr))
        if (kind == "gem") gemMs = t
    }

    private fun add(e: Ev) {
        val cut = e.ms - 1600L
        evs.removeAll { it.ms < cut }
        while (evs.size > 48) evs.removeAt(0)
        evs.add(e)
    }

    /** How the pilot body moves right now (knock-back, shake, spin...). Neutral when nothing is happening. */
    fun hitMotion(h: Float): PilotMotion {
        val age = nowMs() - hitMs
        if (age < 0L || age >= HIT_MS || hitFam.isEmpty()) return PilotMotion.NONE
        val a = age.toFloat()
        val f = a / HIT_MS.toFloat()
        val d = (1f - f) * (1f - f)
        var dx = 0f; var dy = 0f; var rot = 0f; var sx = 1f; var sy = 1f
        when (hitFam) {
            "BLOCK" -> { dx = -h * 0.28f * d; dy = sin(a * 0.06f) * h * 0.05f * d; rot = -7f * d }
            "ROCK" -> { dx = -h * 0.34f * d; dy = h * 0.10f * sin(f * PI.toFloat()); rot = -10f * d }
            "RUBBLE" -> { dx = -h * 0.12f * d; dy = sin(a * 0.11f) * h * 0.06f * d; rot = sin(a * 0.07f) * 6f * d }
            "BARRIER" -> { dx = -h * 0.30f * sin(f * PI.toFloat()); rot = 5f * sin(a * 0.05f) * d }
            "SPIKE" -> { val q = sin(a * 0.045f) * 0.14f * d; sx = 1f + q; sy = 1f - q; dx = -h * 0.08f * d }
            "BEAM" -> { dx = sin(a * 0.9f) * h * 0.04f * d; dy = sin(a * 1.1f) * h * 0.04f * d }
            "ZAP" -> { dx = sin(a * 1.3f) * h * 0.05f * d; dy = sin(a * 0.8f) * h * 0.05f * d; rot = sin(a * 1.0f) * 5f * d }
            "BLADE" -> { dx = -h * 0.18f * d; rot = sin(a * 0.035f) * 28f * d }
            "SPLIT" -> { rot = 360f * (1f - (1f - (a / 600f).coerceIn(0f, 1f)) * (1f - (a / 600f).coerceIn(0f, 1f))) }
            "GHOST" -> { dx = sin(a * 0.03f) * h * 0.05f * d; dy = cos(a * 0.025f) * h * 0.03f * d }
            "RAM" -> { dx = -h * 0.45f * d; rot = -14f * d }
            "GLITCH" -> { dx = (if ((age / 45L) % 2L == 0L) 0.08f else -0.05f) * h * d }
            "LAVA" -> { dy = -h * 0.14f * sin(f * PI.toFloat()); rot = sin(a * 0.05f) * 6f * d }
            "STING" -> { dx = -h * 0.12f * d; rot = -5f * d }
            else -> { dx = -h * 0.22f * d; rot = -12f * d }
        }
        val blink = if (age < 620L && (age / 75L) % 2L == 0L) 0.4f else 1f
        return PilotMotion(dx, dy, rot, sx, sy, blink)
    }
}

/** Movement applied to the pilot sprite: shift, extra tilt, stretch and a see-through blink. */
class PilotMotion(val dx: Float, val dy: Float, val rot: Float, val sx: Float, val sy: Float, val alpha: Float) {
    companion object { val NONE = PilotMotion(0f, 0f, 0f, 1f, 1f, 1f) }
}

// ---------------------------------------------------------------------------------------------------------- shared helpers

private const val TAU = 6.2831855f

internal fun fxRnd(a: Int, b: Int): Float {
    // murmur-style finalizer: neighbouring (a, b) pairs give unrelated values (the old mix put many stars on one row)
    var x = a * -1640531535 + b * 668265263 + 374761393
    x = x xor (x ushr 16)
    x *= 2146121005
    x = x xor (x ushr 15)
    x *= -2073254261
    x = x xor (x ushr 16)
    return (x and 0x7fffffff) / 2147483647f
}

internal fun DrawScope.fxGlow(c: Offset, r: Float, col: Color, a: Float) {
    if (r <= 0f) return
    drawCircle(Brush.radialGradient(listOf(col.copy(alpha = a.coerceIn(0f, 1f)), Color.Transparent), center = c, radius = r),
        radius = r, center = c, blendMode = BlendMode.Plus)
}

internal fun DrawScope.fxSpark(c: Offset, s: Float, col: Color, a: Float) {
    if (s <= 0f) return
    val w = (s * 0.28f).coerceAtLeast(1f)
    val al = a.coerceIn(0f, 1f)
    drawLine(col.copy(alpha = al), Offset(c.x - s, c.y), Offset(c.x + s, c.y), strokeWidth = w, cap = StrokeCap.Round)
    drawLine(col.copy(alpha = al), Offset(c.x, c.y - s), Offset(c.x, c.y + s), strokeWidth = w, cap = StrokeCap.Round)
    drawLine(Color.White.copy(alpha = al * 0.9f), Offset(c.x - s * 0.4f, c.y), Offset(c.x + s * 0.4f, c.y), strokeWidth = w * 0.6f, cap = StrokeCap.Round)
}

/** A jagged lightning line from [from] to [to]. [seed] changes the shape, so a new seed each few frames makes it crackle. */
internal fun DrawScope.fxBolt(from: Offset, to: Offset, seed: Int, col: Color, w: Float, a: Float, jag: Float = 0.12f) {
    val n = 6
    val dx = to.x - from.x
    val dy = to.y - from.y
    val len = sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
    val nx = -dy / len
    val ny = dx / len
    val cc = col.copy(alpha = a.coerceIn(0f, 1f))
    var px = from.x
    var py = from.y
    for (i in 1..n) {
        val f = i / n.toFloat()
        val off = if (i == n) 0f else (fxRnd(seed, i) - 0.5f) * 2f * len * jag
        val qx = from.x + dx * f + nx * off
        val qy = from.y + dy * f + ny * off
        drawLine(cc, Offset(px, py), Offset(qx, qy), strokeWidth = w, cap = StrokeCap.Round)
        px = qx; py = qy
    }
}

/** A small irregular triangle: a shard of glass, rock or metal. */
internal fun DrawScope.fxShard(c: Offset, s: Float, rot: Float, col: Color, a: Float) {
    if (s <= 0f) return
    val ca = cos(rot)
    val sa = sin(rot)
    val p = Path()
    val p0 = Offset(c.x + s * ca, c.y + s * sa)
    val p1 = Offset(c.x + (-s * 0.6f) * ca - (s * 0.7f) * sa, c.y + (-s * 0.6f) * sa + (s * 0.7f) * ca)
    val p2 = Offset(c.x + (-s * 0.45f) * ca - (-s * 0.55f) * sa, c.y + (-s * 0.45f) * sa + (-s * 0.55f) * ca)
    p.moveTo(p0.x, p0.y); p.lineTo(p1.x, p1.y); p.lineTo(p2.x, p2.y); p.close()
    drawPath(p, col.copy(alpha = a.coerceIn(0f, 1f)))
}

private fun famColor(fam: String): Color = when (fam) {
    "BLOCK" -> Color(0xFFFF0055)
    "BARRIER" -> Color(0xFF00E5FF)
    "RUBBLE" -> Color(0xFF8A8FA8)
    "ROCK" -> Color(0xFFB0A090)
    "SPIKE" -> Color(0xFFB388FF)
    "BEAM" -> Color(0xFF7CF3FF)
    "ZAP" -> Color(0xFFFFE040)
    "BLADE" -> Color(0xFFFFA040)
    "SPLIT" -> Color(0xFFFF2BD6)
    "GHOST" -> Color(0xFFB388FF)
    "RAM" -> Color(0xFFFF2B4D)
    "GLITCH" -> Color(0xFF00E5FF)
    "LAVA" -> Color(0xFFFF5A1F)
    "STING" -> Color(0xFFFF4433)
    else -> Color(0xFFFF7A1A)
}

// ---------------------------------------------------------------------------------------------------------- world reactions

/** Draw every live obstacle-break and pick-up burst at the spot where it happened. Call after the track elements, before the pilot. */
fun DrawScope.drawWorldReactions(cw: Float, ch: Float, measurer: TextMeasurer? = null) {
    val now = ReactFx.nowMs()
    for (e in ReactFx.events()) {
        val age = now - e.ms
        if (age < 0L) continue
        val c = Offset(cw * e.xFrac, ch * (e.yPct / 100f))
        if (e.kind == "obs") obstacleFx(e, c, age, ch) else collectFx(e, c, age, ch, measurer)
    }
}

private fun DrawScope.obstacleFx(e: ReactFx.Ev, c: Offset, age: Long, ch: Float) {
    val life = when (e.outcome) { "HIT" -> 900L; "SMASH" -> 800L; "BOOM" -> 800L; "PHASE" -> 700L; else -> 600L }
    if (age >= life) return
    val f = age / life.toFloat()
    val a = (1f - f).coerceIn(0f, 1f)
    val ease = 1f - (1f - f) * (1f - f)
    val s = ch * 0.11f
    when (e.outcome) {
        "SHIELD" -> {
            val col = Color(0xFF5CE1FF)
            fxGlow(c, s * (0.6f + 0.8f * ease), col, 0.5f * a)
            drawCircle(col.copy(alpha = a), radius = s * (0.3f + 1.1f * ease), center = c, style = Stroke(width = (s * 0.07f * (1f - f)).coerceAtLeast(1f)))
            for (k in 0 until 6) {
                val ang = k * (TAU / 6f) + e.seed * 0.7f
                val d = s * (0.4f + 1.0f * ease)
                fxSpark(Offset(c.x + cos(ang) * d, c.y + sin(ang) * d), s * 0.09f, Color.White, a)
            }
        }
        "SMASH" -> {
            val gold = Color(0xFFFFE57F)
            fxGlow(c, s * (0.7f + 1.0f * ease), gold, 0.55f * a)
            for (k in 0 until 6) {
                val ang = -0.9f + k * 0.36f
                val d = s * (0.3f + 2.2f * ease)
                fxSpark(Offset(c.x + cos(ang) * d, c.y + sin(ang) * d), s * 0.12f * (1f - 0.5f * f), gold, a)
            }
            val fc = famColor(e.sub)
            for (k in 0 until 7) {
                val px = c.x + s * (0.3f + 2.2f * ease) * (0.5f + fxRnd(e.seed, k))
                val py = c.y + (fxRnd(e.seed, k + 30) - 0.5f) * s * 1.8f * ease
                fxShard(Offset(px, py), s * 0.12f, k * 1.3f + f * 9f, fc, a)
            }
        }
        "PHASE" -> {
            val col = Color(0xFFB388FF)
            for (k in 0 until 2) {
                val ff = (f - k * 0.18f).coerceIn(0f, 1f)
                drawCircle(col.copy(alpha = (1f - ff) * 0.8f), radius = s * (0.2f + 1.0f * ff), center = c, style = Stroke(width = s * 0.04f))
            }
            for (k in 0 until 4) {
                val wx = c.x + (fxRnd(e.seed, k) - 0.5f) * s
                val wy = c.y - ease * s * (0.4f + 0.6f * fxRnd(e.seed, k + 9))
                drawCircle(col.copy(alpha = 0.5f * a), radius = s * 0.07f, center = Offset(wx, wy))
            }
        }
        "BOOM" -> {
            val col = Color(0xFFFF8A1F)
            fxGlow(c, s * (0.6f + 1.4f * ease), col, 0.8f * a)
            fxGlow(c, s * (0.3f + 0.7f * ease), Color(0xFFFFEB80), 0.7f * a)
            drawCircle(col.copy(alpha = a), radius = s * (0.3f + 1.3f * ease), center = c, style = Stroke(width = (s * 0.08f * (1f - f)).coerceAtLeast(1f)))
            for (k in 0 until 6) {
                val ang = fxRnd(e.seed, k) * TAU
                val d = s * (0.3f + 1.6f * ease)
                drawCircle(Color(0xFFFFB347).copy(alpha = a), radius = s * 0.05f, center = Offset(c.x + cos(ang) * d, c.y + sin(ang) * d))
            }
        }
        else -> breakFx(e.sub, c, age, f, s, e.seed)
    }
}

/** What each kind of obstacle does when it hits the pilot. */
private fun DrawScope.breakFx(fam: String, c: Offset, age: Long, f: Float, s: Float, seed: Int) {
    val a = (1f - f).coerceIn(0f, 1f)
    val ease = 1f - (1f - f) * (1f - f)
    val col = famColor(fam)
    val sec = age / 1000f
    val step = (age / 50L).toInt()
    when (fam) {
        "BLOCK", "ROCK", "RUBBLE" -> {
            val n = if (fam == "RUBBLE") 11 else 8
            for (k in 0 until n) {
                val ang = fxRnd(seed, k) * TAU
                val spd = s * (0.8f + 1.4f * fxRnd(seed, k + 20))
                val px = c.x + cos(ang) * spd * ease
                val py = c.y + sin(ang) * spd * ease + 3.0f * s * sec * sec
                val sz = s * (0.10f + 0.12f * fxRnd(seed, k + 40)) * (if (fam == "ROCK") 1.5f else 1f)
                if (fam == "RUBBLE") drawRect(col.copy(alpha = a), Offset(px - sz / 2f, py - sz / 2f), Size(sz, sz))
                else fxShard(Offset(px, py), sz, ang + sec * 7f, if (k % 3 == 0) Color.White else col, a)
            }
            for (k in 0 until 4) {
                val ang = fxRnd(seed, k + 60) * TAU
                drawCircle(Color(0xFFB8B0A0).copy(alpha = 0.30f * a), radius = s * (0.15f + 0.35f * ease),
                    center = Offset(c.x + cos(ang) * s * 0.4f * ease, c.y + sin(ang) * s * 0.4f * ease))
            }
            if (fam == "ROCK") for (k in 0 until 3) {
                drawCircle(Color.White.copy(alpha = 0.55f * a), radius = s * 0.06f,
                    center = Offset(c.x + (fxRnd(seed, k + 80) - 0.5f) * s * 1.4f, c.y - ease * s * (0.4f + 0.5f * fxRnd(seed, k + 90))))
            }
        }
        "BARRIER" -> {
            drawCircle(col.copy(alpha = a), radius = s * (0.3f + 1.1f * ease), center = c, style = Stroke(width = (s * 0.07f * (1f - f)).coerceAtLeast(1f)))
            for (k in 0 until 6) {
                val ang = k * (TAU / 6f) + 0.3f
                val d = s * (0.3f + 1.2f * ease)
                fxShard(Offset(c.x + cos(ang) * d, c.y + sin(ang) * d), s * 0.15f, ang + sec * 8f, col, a)
            }
            fxGlow(c, s * (0.5f + 0.8f * ease), col, 0.35f * a)
        }
        "SPIKE" -> {
            for (k in 0 until 9) {
                val ang = -1.2f + fxRnd(seed, k) * 2.4f + (if (k % 2 == 0) 0f else PI.toFloat())
                val d = s * (0.4f + 1.6f * ease) * (0.6f + 0.6f * fxRnd(seed, k + 20))
                val px = c.x + cos(ang) * d
                val py = c.y + sin(ang) * d + 2.0f * s * sec * sec
                fxShard(Offset(px, py), s * 0.2f, ang + sec * 5f, if (k % 3 == 0) Color.White else if (k % 2 == 0) col else Color(0xFF7CF3FF), a)
            }
            fxSpark(c, s * 0.3f * (1f - f), Color.White, a)
        }
        "BEAM" -> {
            val half = s * 1.6f
            val w = (s * 0.25f * (1f - f)).coerceAtLeast(1f)
            drawLine(col.copy(alpha = 0.55f * a), Offset(c.x - half, c.y), Offset(c.x + half, c.y), strokeWidth = w * 2.4f, cap = StrokeCap.Round)
            drawLine(Color.White.copy(alpha = a), Offset(c.x - half, c.y), Offset(c.x + half, c.y), strokeWidth = w, cap = StrokeCap.Round)
            for (k in 0 until 8) {
                fxSpark(Offset(c.x + (fxRnd(seed, k) - 0.5f) * 2f * half, c.y + (fxRnd(seed, k + 9) - 0.5f) * s * 0.5f), s * 0.06f, col, a)
            }
        }
        "ZAP" -> {
            fxGlow(c, s * (0.5f + 0.9f * ease), col, 0.5f * a)
            for (k in 0 until 6) {
                val ang = k * (TAU / 6f) + fxRnd(seed, k)
                val d = s * (0.7f + 0.8f * ease)
                fxBolt(c, Offset(c.x + cos(ang) * d, c.y + sin(ang) * d), step * 7 + k, col, s * 0.04f, a)
            }
        }
        "BLADE" -> {
            for (k in 0 until 12) {
                val ang = fxRnd(seed, k) * TAU
                val d0 = s * (0.3f + 0.5f * ease)
                val d1 = s * (0.5f + 1.1f * ease)
                drawLine((if (k % 2 == 0) Color.White else col).copy(alpha = a), Offset(c.x + cos(ang) * d0, c.y + sin(ang) * d0),
                    Offset(c.x + cos(ang) * d1, c.y + sin(ang) * d1), strokeWidth = s * 0.04f, cap = StrokeCap.Round)
            }
            drawCircle(col.copy(alpha = 0.7f * a), radius = s * (0.25f + 0.9f * ease), center = c, style = Stroke(width = s * 0.05f))
        }
        "SPLIT" -> {
            val off = s * (0.15f + 0.9f * ease)
            for (sgn in intArrayOf(-1, 1)) {
                drawArc(col.copy(alpha = a), startAngle = if (sgn < 0) 180f else 0f, sweepAngle = 180f, useCenter = true,
                    topLeft = Offset(c.x - s * 0.45f, c.y + sgn * off - s * 0.45f), size = Size(s * 0.9f, s * 0.9f))
            }
            for (k in 0 until 6) fxSpark(Offset(c.x + (fxRnd(seed, k) - 0.5f) * s * 1.6f * ease, c.y + (fxRnd(seed, k + 9) - 0.5f) * s * 1.6f * ease), s * 0.07f, Color.White, a)
        }
        "GHOST" -> {
            drawCircle(col.copy(alpha = 0.6f * a), radius = s * (0.3f + 0.9f * ease), center = c, style = Stroke(width = s * 0.04f))
            for (k in 0 until 6) {
                val wx = c.x + (fxRnd(seed, k) - 0.5f) * s * 1.2f
                val wy = c.y - ease * s * (0.6f + fxRnd(seed, k + 9))
                drawCircle(col.copy(alpha = 0.5f * a), radius = s * 0.12f * (1f - 0.5f * f), center = Offset(wx, wy))
            }
        }
        "RAM" -> {
            for (k in 0 until 14) {
                val px = c.x + (fxRnd(seed, k) - 0.5f) * s * (0.6f + 2f * ease)
                val py = c.y + (fxRnd(seed, k + 20) - 0.5f) * s * (0.6f + 2f * ease)
                drawRect((if (k % 2 == 0) col else Color(0xFFB388FF)).copy(alpha = a), Offset(px, py), Size(s * 0.07f, s * 0.07f))
            }
            drawCircle(col.copy(alpha = a), radius = s * (0.3f + 1.0f * ease), center = c, style = Stroke(width = s * 0.05f * (1f - f)))
        }
        "GLITCH" -> {
            for (k in 0 until 7) {
                val yy = c.y + (fxRnd(step + seed, k) - 0.5f) * s * 1.6f
                val xx = c.x + (fxRnd(step + seed, k + 11) - 0.5f) * s * 1.4f - s * 0.5f
                val w = s * (0.4f + 0.9f * fxRnd(step, k + 3))
                val cc = when (k % 3) { 0 -> Color(0xFF00E5FF); 1 -> Color(0xFFFF2BD6); else -> Color(0xFFFFFF55) }
                drawRect(cc.copy(alpha = 0.6f * a), Offset(xx, yy), Size(w, s * 0.07f))
            }
        }
        "LAVA" -> {
            drawCircle(col.copy(alpha = a), radius = s * (0.3f + 0.9f * ease), center = c, style = Stroke(width = s * 0.06f * (1f - f)))
            for (k in 0 until 9) {
                val ang = -PI.toFloat() * (0.1f + 0.8f * fxRnd(seed, k))
                val v = s * (1.2f + 1.2f * fxRnd(seed, k + 20))
                val px = c.x + cos(ang) * v * sec * 1.6f
                val py = c.y + sin(ang) * v * sec * 1.6f + 9f * s * sec * sec
                drawCircle(col.copy(alpha = a), radius = s * 0.08f, center = Offset(px, py))
                drawCircle(Color(0xFFFFE066).copy(alpha = a), radius = s * 0.04f, center = Offset(px, py))
            }
        }
        "STING" -> {
            drawCircle(col.copy(alpha = a), radius = s * (0.15f + 0.6f * ease), center = c, style = Stroke(width = s * 0.05f))
            for (k in 0 until 4) {
                val ang = k * (TAU / 4f) + 0.4f
                val d = s * (0.2f + 0.6f * ease)
                fxSpark(Offset(c.x + cos(ang) * d, c.y + sin(ang) * d), s * 0.07f, Color.White, a)
            }
        }
        else -> {   // BLAST: drones and standard obstacles burst into a fireball with debris and smoke
            fxGlow(c, s * (0.5f + 1.2f * ease), col, 0.8f * a)
            fxGlow(c, s * (0.3f + 0.6f * ease), Color(0xFFFFEB80), 0.6f * a)
            for (k in 0 until 6) {
                val ang = fxRnd(seed, k) * TAU
                val d = s * (0.4f + 1.2f * ease)
                fxShard(Offset(c.x + cos(ang) * d, c.y + sin(ang) * d + 2f * s * sec * sec), s * 0.1f, ang + sec * 6f, Color(0xFF3A3A4A), a)
            }
            for (k in 0 until 4) {
                drawCircle(Color(0xFF555566).copy(alpha = 0.30f * a), radius = s * (0.12f + 0.2f * ease),
                    center = Offset(c.x + (fxRnd(seed, k) - 0.5f) * s * 0.8f, c.y - ease * s * (0.5f + 0.5f * fxRnd(seed, k + 9))))
            }
        }
    }
}

/** What happens at the spot where a diamond, fuel cell or power-up was collected. */
private fun DrawScope.collectFx(e: ReactFx.Ev, c: Offset, age: Long, ch: Float, measurer: TextMeasurer?) {
    val life = when (e.kind) { "gem" -> 900L; "fuel" -> 1000L; else -> 900L }
    if (age >= life) return
    val f = age / life.toFloat()
    val a = (1f - f).coerceIn(0f, 1f)
    val ease = 1f - (1f - f) * (1f - f)
    val sec = age / 1000f
    val s = ch * 0.09f
    when (e.kind) {
        "gem" -> {
            val col = Color(0xFF00E5FF)
            fxGlow(c, s * (0.7f + 0.9f * ease), col, 0.4f * a)
            drawCircle(col.copy(alpha = a), radius = s * (0.25f + 1.1f * ease), center = c, style = Stroke(width = s * 0.06f * (1f - f)))
            drawCircle(Color(0xFFFF2BD6).copy(alpha = a * 0.7f), radius = s * (0.15f + 0.8f * ease), center = c, style = Stroke(width = s * 0.035f * (1f - f)))
            for (k in 0 until 8) {
                val ang = k * (TAU / 8f) + 0.3f + fxRnd(e.seed, k) * 0.3f
                val d = s * (0.2f + (0.8f + 0.5f * fxRnd(e.seed, k + 3)) * ease)
                fxRhombus(Offset(c.x + cos(ang) * d, c.y + sin(ang) * d - s * 0.3f * f), s * (0.13f + 0.05f * fxRnd(e.seed, k + 5)), sec * 6f + k, if (k % 2 == 0) Color.White else col, a)
            }
            fxSpark(c, s * 0.7f * (1f - f), Color.White, a)
            if (measurer != null) {
                val ta = (1f - f * f).coerceIn(0f, 1f)
                val tl = measurer.measure("+1", style = TextStyle(color = Color.White.copy(alpha = ta), fontSize = (s * 0.42f).toSp(), fontWeight = FontWeight.Black))
                drawText(textLayoutResult = tl, topLeft = Offset(c.x - tl.size.width / 2f, c.y - s * (0.5f + 0.7f * ease) - tl.size.height / 2f))
            }
        }
        "fuel" -> {
            val col = Color(0xFF00FF88)
            drawCircle(col.copy(alpha = a), radius = s * (0.25f + 0.9f * ease), center = c, style = Stroke(width = s * 0.05f * (1f - f)))
            for (k in 0 until 9) {
                val ang = -PI.toFloat() / 2f + (fxRnd(e.seed, k) - 0.5f) * 2.4f
                val v = s * (0.9f + fxRnd(e.seed, k + 7))
                val px = c.x + cos(ang) * v * sec * 1.8f
                val py = c.y + sin(ang) * v * sec * 1.8f + 5f * s * sec * sec
                drawCircle(col.copy(alpha = a), radius = s * 0.07f, center = Offset(px, py))
            }
            fxGlow(c, s * 0.9f, col, 0.3f * a)
            if (measurer != null && e.outcome.isNotEmpty()) {
                val tl = measurer.measure("⛽ " + e.outcome, style = TextStyle(color = col.copy(alpha = a), fontSize = (s * 0.3f).toSp(), fontWeight = FontWeight.Black))
                drawText(textLayoutResult = tl, topLeft = Offset(c.x - tl.size.width / 2f, c.y - s * (0.5f + 0.8f * ease) - tl.size.height / 2f))
            }
        }
        else -> puCollectFx(e.sub, c, f, s, e.seed)
    }
}

private fun DrawScope.puCollectFx(id: String, c: Offset, f: Float, s: Float, seed: Int) {
    val col = PickupFx.colorOf(id)
    val a = (1f - f).coerceIn(0f, 1f)
    val ease = 1f - (1f - f) * (1f - f)
    drawCircle(col.copy(alpha = a), radius = s * (0.25f + 1.1f * ease), center = c, style = Stroke(width = (s * 0.07f * (1f - f)).coerceAtLeast(1f)))
    fxGlow(c, s * (0.4f + 0.9f * ease), col, 0.5f * a)
    when (id) {
        "PU1" -> {
            val p = Path()
            val hr = s * (0.5f + 0.8f * ease)
            for (k in 0..5) {
                val ang = (PI / 3.0 * k + PI / 6.0).toFloat()
                if (k == 0) p.moveTo(c.x + cos(ang) * hr, c.y + sin(ang) * hr) else p.lineTo(c.x + cos(ang) * hr, c.y + sin(ang) * hr)
            }
            p.close()
            drawPath(p, Color.White.copy(alpha = a), style = Stroke(width = s * 0.05f))
        }
        "PU2" -> for (k in 0 until 8) {
            val ang = k * (TAU / 8f) + 0.2f
            val d = s * 1.4f * (1f - ease)
            drawCircle((if (k % 2 == 0) Color(0xFFFF5252) else Color(0xFF448AFF)).copy(alpha = 0.4f + 0.6f * a), radius = s * 0.06f, center = Offset(c.x + cos(ang) * d, c.y + sin(ang) * d))
        }
        "PU3" -> {
            val ang = f * TAU * 2f - PI.toFloat() / 2f
            drawLine(Color.White.copy(alpha = a), c, Offset(c.x + cos(ang) * s * 0.8f, c.y + sin(ang) * s * 0.8f), strokeWidth = s * 0.05f, cap = StrokeCap.Round)
        }
        "PU4" -> for (k in 0 until 5) {
            drawCircle(col.copy(alpha = 0.5f * a), radius = s * 0.1f, center = Offset(c.x + (fxRnd(seed, k) - 0.5f) * s * 1.2f, c.y - ease * s * (0.6f + fxRnd(seed, k + 9))))
        }
        "PU5", "PU6" -> for (k in 0 until (if (id == "PU6") 6 else 4)) {
            val px = c.x + (fxRnd(seed, k) - 0.5f) * s * 1.4f
            val py = c.y - ease * s * (0.7f + fxRnd(seed, k + 9))
            drawCircle(col.copy(alpha = a), radius = s * 0.09f, center = Offset(px, py))
            drawCircle(Color.White.copy(alpha = a * 0.8f), radius = s * 0.09f, center = Offset(px, py), style = Stroke(width = s * 0.02f))
        }
        "PU7" -> for (k in 0 until 4) {
            val ang = f * TAU * 1.2f + k * (TAU / 4f)
            val d = s * (0.4f + 0.7f * ease)
            fxSpark(Offset(c.x + cos(ang) * d, c.y + sin(ang) * d), s * 0.14f, col, a)
        }
        "PU8" -> {
            fxGlow(c, s * (0.6f + 1.6f * ease), Color(0xFFFF8A1F), 0.8f * a)
            drawCircle(Color(0xFFFFB347).copy(alpha = a), radius = s * (0.4f + 1.6f * ease), center = c, style = Stroke(width = (s * 0.1f * (1f - f)).coerceAtLeast(1f)))
        }
        "PU9" -> for (k in 0 until 4) {
            val ang = k * (TAU / 4f) + PI.toFloat() / 4f
            val d = s * 1.2f * (1f - ease) + s * 0.2f
            drawLine(col.copy(alpha = a), Offset(c.x + cos(ang) * d, c.y + sin(ang) * d), Offset(c.x + cos(ang) * (d - s * 0.18f), c.y + sin(ang) * (d - s * 0.18f)), strokeWidth = s * 0.05f, cap = StrokeCap.Round)
        }
        "PU10" -> drawLine(col.copy(alpha = a), Offset(c.x, c.y - s * 2f), Offset(c.x, c.y + s * 2f), strokeWidth = s * 0.05f)
        "PU11" -> for (k in 0 until 3) {
            val cx = c.x + s * (0.6f - 1.8f * ((f + k * 0.15f).coerceIn(0f, 1f)))
            val q = s * 0.25f
            val p = Path()
            p.moveTo(cx + q, c.y - q); p.lineTo(cx, c.y); p.lineTo(cx + q, c.y + q)
            drawPath(p, Color.White.copy(alpha = a), style = Stroke(width = s * 0.05f, cap = StrokeCap.Round))
        }
        "PU12" -> for (k in 0 until 3) {
            val hue = listOf(Color(0xFFFF5252), Color(0xFFFFEB3B), Color(0xFF40C4FF))[k]
            drawCircle(hue.copy(alpha = 0.6f * a), radius = s * (0.4f + (1.0f + 0.25f * k) * ease), center = c, style = Stroke(width = s * 0.05f))
        }
        else -> {}
    }
}

// ---------------------------------------------------------------------------------------------------------- pilot reactions

/** What the pilot looks like for a moment after a hit, a smash, a phase-through or a diamond. Draw AFTER the pilot. */
fun DrawScope.drawPilotHitFx(x: Float, y: Float, h: Float) {
    val now = ReactFx.nowMs()
    val c = Offset(x, y)

    val age = now - ReactFx.hitMs
    if (age in 0L until ReactFx.HIT_MS && ReactFx.hitFam.isNotEmpty()) pilotHurt(ReactFx.hitFam, c, h, age)

    val sAge = now - ReactFx.smashMs
    if (sAge in 0L until 700L) {
        // invincible: it bounces off and the pilot glints
        val f = sAge / 700f
        val a = 1f - f
        val gold = Color(0xFFFFE57F)
        fxGlow(c, h * 0.8f, gold, 0.35f * a)
        val sx = x - h * 0.4f + h * 0.8f * f
        drawLine(Color.White.copy(alpha = a * 0.9f), Offset(sx - h * 0.1f, y + h * 0.4f), Offset(sx + h * 0.1f, y - h * 0.4f), strokeWidth = h * 0.06f, cap = StrokeCap.Round, blendMode = BlendMode.Plus)
        fxSpark(Offset(x + h * 0.3f, y - h * 0.15f), h * 0.12f * a, gold, a)
    }

    val pAge = now - ReactFx.phaseMs
    if (pAge in 0L until 700L) {
        // ghost: it passes through, ripples spread over the pilot
        val f = pAge / 700f
        val col = Color(0xFFB388FF)
        for (k in 0 until 2) {
            val ff = (f - k * 0.2f).coerceIn(0f, 1f)
            drawCircle(col.copy(alpha = (1f - ff) * 0.7f), radius = h * (0.25f + 0.55f * ff), center = c, style = Stroke(width = h * 0.02f))
        }
    }

    val gAge = now - ReactFx.gemMs
    if (gAge in 0L until ReactFx.GEM_MS) {
        // diamond cascade: the pilot is crowned in cyan light, diamonds spiral up around it, a sparkle sweeps over the body
        val f = gAge / ReactFx.GEM_MS.toFloat()
        val a = (1f - f).coerceIn(0f, 1f)
        val cy = Color(0xFF00E5FF)
        val pk = Color(0xFFFF2BD6)
        val pop = if (f < 0.2f) f / 0.2f else 1f
        fxGlow(c, h * (0.55f + 0.5f * pop), cy, 0.38f * a)
        fxGlow(Offset(x, y - h * 0.1f), h * 0.4f, pk, 0.18f * a)
        for (r in 0 until 2) {
            val ff = (f * 1.3f - r * 0.18f).coerceIn(0f, 1f)
            drawCircle((if (r == 0) cy else pk).copy(alpha = (1f - ff) * 0.8f), radius = h * (0.3f + 0.7f * ff), center = c, style = Stroke(width = h * 0.03f * (1f - ff)))
        }
        for (k in 0 until 9) {
            val ang = k * (TAU / 9f) + f * 7f
            val rise = f * h * 0.5f
            val rad = h * (0.32f + 0.12f * sin(f * 9f + k))
            fxRhombus(Offset(x + cos(ang) * rad, y + sin(ang) * rad * 0.6f - rise), h * (0.075f - 0.03f * f), ang * 2f + f * 10f, if (k % 3 == 0) Color.White else if (k % 3 == 1) cy else pk, a)
        }
        // sparkle sweep top to bottom
        val sy = y - h * 0.5f + h * f * 1.0f
        drawLine(Color.White.copy(alpha = a * 0.8f), Offset(x - h * 0.3f, sy), Offset(x + h * 0.3f, sy), strokeWidth = h * 0.03f, cap = StrokeCap.Round, blendMode = BlendMode.Plus)
        fxSpark(Offset(x + h * 0.22f, y - h * 0.35f), h * 0.16f * a, Color.White, a)
        fxSpark(Offset(x - h * 0.2f, y + h * 0.1f), h * 0.11f * a, cy, a)
    }
}

private fun DrawScope.pilotHurt(fam: String, c: Offset, h: Float, age: Long) {
    val x = c.x
    val y = c.y
    val f = age / ReactFx.HIT_MS.toFloat()
    val a = (1f - f).coerceIn(0f, 1f)
    val front = Offset(x + h * 0.30f, y)
    val step = (age / 55L).toInt()
    val seed = (ReactFx.hitMs % 9973L).toInt()
    // every real hit: a short red hurt flash
    fxGlow(c, h * 0.75f, Color(0xFFFF3B3B), 0.32f * a)

    when (fam) {
        "BLOCK", "ROCK" -> {
            val sa = (1f - f * 2.2f).coerceIn(0f, 1f)
            if (sa > 0f) for (k in 0 until 8) {
                val ang = k * (TAU / 8f)
                drawLine(Color(0xFFFFE57F).copy(alpha = sa), Offset(front.x + cos(ang) * h * 0.06f, front.y + sin(ang) * h * 0.06f),
                    Offset(front.x + cos(ang) * h * (0.16f + 0.12f * f), front.y + sin(ang) * h * (0.16f + 0.12f * f)), strokeWidth = h * 0.025f, cap = StrokeCap.Round)
            }
            for (k in 0 until 3) {                       // dizzy stars circling the head
                val ang = age * 0.011f + k * 2.094f
                fxSpark(Offset(x + cos(ang) * h * 0.26f, y - h * 0.52f + sin(ang) * h * 0.06f), h * 0.05f, Color(0xFFFFE57F), a)
            }
            if (fam == "ROCK") for (k in 0 until 4) {
                drawCircle(Color(0xFFB8B0A0).copy(alpha = 0.35f * a), radius = h * (0.05f + 0.14f * f),
                    center = Offset(front.x + (fxRnd(seed, k) - 0.5f) * h * 0.3f, front.y + (fxRnd(seed, k + 9) - 0.5f) * h * 0.5f))
            }
        }
        "RUBBLE" -> for (k in 0 until 7) {               // scraping sparks along the top and bottom edge
            val sy = if (k % 2 == 0) y - h * 0.45f else y + h * 0.45f
            val sx = x + h * 0.2f - k * h * 0.07f - f * h * 0.45f
            drawLine(Color(0xFFFFB347).copy(alpha = a), Offset(sx, sy), Offset(sx - h * 0.06f, sy + (if (k % 2 == 0) -1f else 1f) * h * 0.05f), strokeWidth = h * 0.02f, cap = StrokeCap.Round)
        }
        "BARRIER" -> for (k in 0 until 2) {
            val ff = (f * 1.4f - k * 0.2f).coerceIn(0f, 1f)
            drawCircle(Color(0xFF00E5FF).copy(alpha = (1f - ff) * 0.8f), radius = h * (0.15f + 0.6f * ff), center = front, style = Stroke(width = h * 0.03f))
        }
        "SPIKE" -> {                                      // glass-crack lines from the impact point
            for (k in 0 until 5) {
                val ang = PI.toFloat() * (0.6f + 0.8f * k / 4f) + (fxRnd(seed, k) - 0.5f) * 0.4f
                fxBolt(front, Offset(front.x + cos(ang) * h * 0.34f, front.y + sin(ang) * h * 0.34f), seed + k, Color(0xFFE0F7FF), h * 0.012f, a, 0.10f)
            }
        }
        "BEAM" -> {                                       // electric arcs crawling over the pilot
            for (k in 0 until 4) {
                val p0 = Offset(x + (fxRnd(step, k) - 0.5f) * h * 0.6f, y + (fxRnd(step, k + 5) - 0.5f) * h * 0.9f)
                val p1 = Offset(x + (fxRnd(step, k + 10) - 0.5f) * h * 0.6f, y + (fxRnd(step, k + 15) - 0.5f) * h * 0.9f)
                fxBolt(p0, p1, step * 5 + k, Color(0xFF7CF3FF), h * 0.016f, a)
            }
            drawLine(Color(0xFF7CF3FF).copy(alpha = 0.5f * a), Offset(x - h * 0.5f, y), Offset(x + h * 0.5f, y), strokeWidth = h * 0.01f)
        }
        "ZAP" -> {
            fxGlow(c, h * 0.7f, Color(0xFFFFE040), 0.3f * a)
            for (k in 0 until 5) {
                val ang = k * (TAU / 5f) + fxRnd(step, k)
                fxBolt(c, Offset(x + cos(ang) * h * 0.6f, y + sin(ang) * h * 0.6f), step * 3 + k, Color(0xFFFFF176), h * 0.018f, a)
            }
        }
        "BLADE" -> {                                      // two quick slashes across the pilot
            val p = (f * 3f).coerceIn(0f, 1f)
            val s1 = Offset(x - h * 0.35f, y - h * 0.4f)
            val e1 = Offset(s1.x + (h * 0.7f) * p, s1.y + (h * 0.8f) * p)
            val s2 = Offset(x + h * 0.35f, y - h * 0.35f)
            val e2 = Offset(s2.x - (h * 0.7f) * p, s2.y + (h * 0.8f) * p)
            for (pr in listOf(s1 to e1, s2 to e2)) {
                drawLine(Color(0xFFFF3B3B).copy(alpha = a), pr.first, pr.second, strokeWidth = h * 0.05f, cap = StrokeCap.Round)
                drawLine(Color.White.copy(alpha = a), pr.first, pr.second, strokeWidth = h * 0.02f, cap = StrokeCap.Round)
            }
        }
        "SPLIT" -> for (k in 0 until 2) {
            drawArc(Color(0xFFFF2BD6).copy(alpha = 0.8f * a), startAngle = age * 0.9f + k * 180f, sweepAngle = 100f, useCenter = false,
                topLeft = Offset(x - h * 0.55f, y - h * 0.55f), size = Size(h * 1.1f, h * 1.1f), style = Stroke(width = h * 0.03f, cap = StrokeCap.Round))
        }
        "GHOST" -> {
            drawCircle(Color(0xFFB388FF).copy(alpha = 0.45f * a), radius = h * 0.5f, center = c, style = Stroke(width = h * 0.02f))
            for (k in 0 until 4) {
                drawCircle(Color(0xFFD1C4FF).copy(alpha = 0.5f * a), radius = h * 0.04f,
                    center = Offset(x + (fxRnd(seed, k) - 0.5f) * h * 0.6f, y + h * 0.2f - f * h * 0.7f - fxRnd(seed, k + 9) * h * 0.2f))
            }
        }
        "RAM" -> for (k in 0 until 6) {                   // knock lines streaking off the front
            val ly = y + (k - 2.5f) * h * 0.14f
            drawLine(Color(0xFFFF2B4D).copy(alpha = a), Offset(front.x, ly), Offset(front.x + h * (0.2f + 0.5f * f), ly), strokeWidth = h * 0.02f, cap = StrokeCap.Round)
        }
        "GLITCH" -> {
            for (k in 0 until 4) {
                val yy = y + (fxRnd(step, k) - 0.5f) * h * 0.9f
                val off = (fxRnd(step, k + 7) - 0.5f) * h * 0.3f
                drawRect((if (k % 2 == 0) Color(0xFF00E5FF) else Color(0xFFFF2BD6)).copy(alpha = 0.5f * a), Offset(x - h * 0.4f + off, yy), Size(h * 0.8f, h * 0.07f))
            }
            if (age < 280L) {                              // jagged bite closing on the pilot
                val g = 1f - age / 280f
                for (k in 0 until 5) {
                    val ty = front.y - h * 0.3f + k * h * 0.15f
                    val tx = front.x + h * 0.1f
                    val up = Path(); up.moveTo(tx, ty - h * 0.25f * g); up.lineTo(tx + h * 0.05f, ty); up.lineTo(tx - h * 0.05f, ty); up.close()
                    drawPath(up, Color.White.copy(alpha = 0.9f * a))
                }
            }
        }
        "LAVA" -> for (k in 0 until 5) {                   // flames licking up the pilot
            val bx = x - h * 0.2f + k * h * 0.1f
            val fh = h * (0.25f + 0.2f * fxRnd(step, k)) * a
            val p = Path()
            p.moveTo(bx - h * 0.04f, y + h * 0.15f); p.quadraticBezierTo(bx - h * 0.05f, y - fh * 0.5f, bx, y - fh)
            p.quadraticBezierTo(bx + h * 0.05f, y - fh * 0.5f, bx + h * 0.04f, y + h * 0.15f); p.close()
            drawPath(p, Color(0xFFFF5A1F).copy(alpha = 0.85f * a))
            drawCircle(Color(0xFFFFE066).copy(alpha = 0.8f * a), radius = h * 0.03f, center = Offset(bx, y + h * 0.05f))
        }
        "STING" -> {
            drawCircle(Color(0xFFFF4433).copy(alpha = a), radius = h * (0.08f + 0.2f * f), center = front, style = Stroke(width = h * 0.02f))
            fxSpark(front, h * 0.09f * a, Color.White, a)
        }
        else -> {                                           // BLAST: scorch, then smoke
            fxGlow(front, h * (0.2f + 0.45f * f), Color(0xFFFF7A1A), 0.7f * a)
            for (k in 0 until 5) {
                drawCircle(Color(0xFF555566).copy(alpha = 0.35f * a), radius = h * (0.05f + 0.08f * f),
                    center = Offset(x + (fxRnd(seed, k) - 0.5f) * h * 0.5f, y - h * 0.25f - f * h * 0.5f * (0.6f + fxRnd(seed, k + 9))))
            }
        }
    }
}

/** A small spinning diamond (rhombus) with a bright core, used for the diamond effects. */
internal fun DrawScope.fxRhombus(c: Offset, r: Float, spin: Float, color: Color, alpha: Float) {
    if (alpha <= 0f || r <= 0f) return
    val sq = kotlin.math.abs(cos(spin)).coerceAtLeast(0.25f)
    val path = Path().apply {
        moveTo(c.x, c.y - r * 1.25f)
        lineTo(c.x + r * sq, c.y)
        lineTo(c.x, c.y + r * 1.25f)
        lineTo(c.x - r * sq, c.y)
        close()
    }
    drawPath(path, color.copy(alpha = alpha * 0.9f), blendMode = BlendMode.Plus)
    drawPath(path, Color.White.copy(alpha = alpha), style = Stroke(width = r * 0.18f))
}
