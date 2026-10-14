package com.neonrush.game.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.neonrush.game.PartCatalog
import com.neonrush.game.PartSlot
import com.neonrush.game.PilotPart
import com.neonrush.game.R
import com.neonrush.game.Rarity
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * PILOT LOOKS
 *
 * The pilot is ALWAYS the same recognisable character (the six running frames). A "look" is made of
 *   1. a SUIT  : colour grade of the whole pilot + a painted pattern clipped to the silhouette + signature animation
 *                + a set of built-in parts, and
 *   2. PARTS   : shop pieces (head / neck / shoulder / back / wrist / boots) the player can mix onto ANY suit.
 * A part the player equips replaces the suit's built-in piece in the same slot.
 */

/** Parts the player has equipped, readable from any draw scope (header avatar, in-run pilot, cards). */
object PilotWardrobe {
    var equipped: Map<String, String> by mutableStateOf(emptyMap())   // slot name -> part id
    var masteryTier: Int by mutableStateOf(0)                          // Locker mastery 0..4 (orbiting stars)
}

// ---------------------------------------------------------------------------------------------- anchors

/** Per frame (normalised to the frame image): helmet cx,cy,radius(of height) | fist | jet nozzle | lowest foot | other foot. */
private val FRAME_ANCHORS = arrayOf(
    floatArrayOf(.726f, .137f, .104f, .90f, .34f, .415f, .402f, .83f, .914f, .10f, .63f),
    floatArrayOf(.728f, .142f, .106f, .89f, .333f, .416f, .407f, .111f, .919f, .62f, .79f),
    floatArrayOf(.687f, .139f, .105f, .892f, .332f, .346f, .398f, .138f, .921f, .48f, .78f),
    floatArrayOf(.701f, .14f, .105f, .9f, .348f, .395f, .4f, .628f, .917f, .07f, .84f),
    floatArrayOf(.698f, .139f, .106f, .897f, .34f, .366f, .388f, .133f, .92f, .60f, .85f),
    floatArrayOf(.689f, .139f, .104f, .897f, .338f, .37f, .398f, .155f, .919f, .62f, .84f)
)

private fun anchorsFor(slot: PartSlot, fi: Int, l: Float, tp: Float, w: Float, h: Float): List<Offset> {
    val a = FRAME_ANCHORS[((fi % 6) + 6) % 6]
    val r = a[2] * h
    val hx = l + a[0] * w; val hy = tp + a[1] * h
    return when (slot) {
        PartSlot.HEAD -> listOf(Offset(hx, hy))
        PartSlot.NECK -> listOf(Offset(hx - 0.55f * r, hy + 1.05f * r))
        PartSlot.SHOULDER -> listOf(Offset(hx - 1.35f * r, hy + 1.2f * r))
        PartSlot.BACK -> listOf(Offset(hx - 2.2f * r, hy + 1.15f * r))
        PartSlot.WRIST -> listOf(
            Offset(l + a[3] * w - 0.6f * r, tp + a[4] * h + 0.35f * r),
            Offset(l + a[5] * w - 1.2f * r, tp + a[6] * h - 0.2f * r)
        )
        PartSlot.BOOTS -> listOf(
            Offset(l + a[7] * w - 0.2f * r, tp + a[8] * h - 0.35f * r),
            Offset(l + a[9] * w, tp + a[10] * h)
        )
        PartSlot.AURA -> listOf(Offset(hx - 1.6f * r, hy + 3.2f * r))
        PartSlot.FLAME -> anchorsFor(PartSlot.WRIST, fi, l, tp, w, h) + anchorsFor(PartSlot.BOOTS, fi, l, tp, w, h)
    }
}

// ---------------------------------------------------------------------------------------------- colour grading

private fun grade(hue: Float, sat: Float, bright: Float, tr: Float, tg: Float, tb: Float, ar: Float, ag: Float, ab: Float): FloatArray {
    val lr = 0.213f; val lg = 0.715f; val lb = 0.072f
    val a = cos(Math.toRadians(hue.toDouble())).toFloat(); val b = sin(Math.toRadians(hue.toDouble())).toFloat()
    val h = arrayOf(
        floatArrayOf(lr + .787f * a - lr * b, lg - lg * a - lg * b, lb - lb * a + .928f * b),
        floatArrayOf(lr - lr * a + .143f * b, lg + .285f * a + .14f * b, lb - lb * a - .283f * b),
        floatArrayOf(lr - lr * a - .787f * b, lg - lg * a + lg * b, lb + .928f * a + lb * b)
    )
    val s = arrayOf(
        floatArrayOf(lr * (1 - sat) + sat, lg * (1 - sat), lb * (1 - sat)),
        floatArrayOf(lr * (1 - sat), lg * (1 - sat) + sat, lb * (1 - sat)),
        floatArrayOf(lr * (1 - sat), lg * (1 - sat), lb * (1 - sat) + sat)
    )
    val d = floatArrayOf(tr * bright, tg * bright, tb * bright)
    val m = FloatArray(20)
    for (row in 0..2) {
        for (col in 0..2) {
            var v = 0f
            for (k in 0..2) v += s[row][k] * h[k][col]
            m[row * 5 + col] = v * d[row]
        }
    }
    m[4] = ar; m[9] = ag; m[14] = ab
    m[18] = 1f   // alpha untouched
    return m
}

// ---------------------------------------------------------------------------------------------- pattern painter

private fun rnd(i: Int, k: Int): Float {
    var x = i * 374761393 + k * 668265263 + 12345
    x = (x xor (x ushr 13)) * 1274126177
    x = x xor (x ushr 16)
    return (x and 0xFFFF) / 65535f
}
private fun pulse(t: Float, f: Float, ph: Float = 0f): Float = 0.5f + 0.5f * sin(t * f + ph)
private fun col(argb: Long, a: Float = 1f): Color = Color(argb).copy(alpha = a.coerceIn(0f, 1f))

/** Draws only where the sprite already has pixels (the pilot's silhouette). Coordinates are fractions of the sprite box. */
class Pat(private val ds: DrawScope, private val l: Float, private val tp: Float, val w: Float, val h: Float) {
    fun rect(c: Color, x0: Float, y0: Float, x1: Float, y1: Float) =
        ds.drawRect(c, Offset(l + x0 * w, tp + y0 * h), Size((x1 - x0) * w, (y1 - y0) * h), blendMode = BlendMode.SrcAtop)
    fun oval(c: Color, cx: Float, cy: Float, rx: Float, ry: Float) =
        ds.drawOval(c, Offset(l + (cx - rx) * w, tp + (cy - ry) * h), Size(rx * 2f * w, ry * 2f * h), blendMode = BlendMode.SrcAtop)
    fun line(c: Color, x0: Float, y0: Float, x1: Float, y1: Float, wd: Float) =
        ds.drawLine(c, Offset(l + x0 * w, tp + y0 * h), Offset(l + x1 * w, tp + y1 * h), strokeWidth = wd * h, cap = StrokeCap.Round, blendMode = BlendMode.SrcAtop)
}

private fun camo(p: Pat, seed: Int, colors: List<Long>, n: Int, a: Float) {
    for (i in 0 until n) {
        val cx = rnd(i, seed); val cy = 0.05f + rnd(i, seed + 1) * 0.9
        p.oval(col(colors[i % colors.size], a), cx, cy.toFloat(), 0.04f + rnd(i, seed + 2) * 0.08f, 0.025f + rnd(i, seed + 3) * 0.05f)
    }
}
private fun spots(p: Pat, seed: Int, colors: List<Long>, n: Int, a: Float, rmax: Float) {
    for (i in 0 until n) {
        val rr = 0.012f + rnd(i, seed + 2) * rmax
        p.oval(col(colors[i % colors.size], a), rnd(i, seed), 0.04f + rnd(i, seed + 1) * 0.92f, rr, rr * 1.0f)
    }
}
private fun diagBands(p: Pat, t: Float, c: Long, a: Float, count: Int, wd: Float, speed: Float) {
    val off = (t * speed) % 1f
    for (i in -1..count) {
        val x = (i + off) / count
        p.line(col(c, a), x - 0.25f, 1.05f, x + 0.25f, -0.05f, wd)
    }
}
private fun scan(p: Pat, t: Float, c: Long, a: Float) {
    var y = 0.02f
    while (y < 1f) { p.rect(col(c, a), 0f, y, 1f, y + 0.012f); y += 0.035f }
    val by = ((t * 0.35f) % 1.3f) - 0.15f
    p.rect(col(0xFFFFFFFF, 0.28f), 0f, by, 1f, by + 0.05f)
}
private fun stars(p: Pat, t: Float, n: Int) {
    p.oval(col(0xFF7C4DFF, 0.28f), 0.3f, 0.35f, 0.2f, 0.12f)
    p.oval(col(0xFF18FFFF, 0.18f), 0.7f, 0.6f, 0.18f, 0.1f)
    for (i in 0 until n) {
        val tw = pulse(t, 2.5f + rnd(i, 9) * 3f, rnd(i, 8) * 6f)
        val r = 0.006f + 0.006f * tw
        p.oval(col(0xFFFFFFFF, 0.35f + 0.65f * tw), rnd(i, 1), rnd(i, 2), r, r)
    }
}
private val CIRCUIT = floatArrayOf(
    .10f, .55f, .30f, .55f, .30f, .55f, .30f, .70f, .30f, .70f, .52f, .70f, .52f, .70f, .52f, .85f,
    .20f, .20f, .45f, .20f, .45f, .20f, .45f, .35f, .45f, .35f, .62f, .35f, .62f, .35f, .62f, .48f,
    .62f, .48f, .82f, .48f, .70f, .08f, .70f, .22f, .70f, .22f, .90f, .22f, .55f, .55f, .75f, .62f,
    .75f, .62f, .75f, .78f, .15f, .85f, .15f, .92f, .35f, .30f, .35f, .45f
)
private fun circuit(p: Pat, t: Float, c: Long, glow: Long) {
    var i = 0; var k = 0
    while (i + 3 < CIRCUIT.size) {
        val pu = pulse(t, 3f, k * 0.7f)
        p.line(col(c, 0.55f + 0.4f * pu), CIRCUIT[i], CIRCUIT[i + 1], CIRCUIT[i + 2], CIRCUIT[i + 3], 0.012f)
        p.oval(col(glow, 0.5f + 0.5f * pu), CIRCUIT[i + 2], CIRCUIT[i + 3], 0.014f, 0.014f)
        i += 4; k++
    }
}
private fun glitchBars(p: Pat, t: Float, c1: Long, c2: Long) {
    val step = (t * 6f).toInt()
    for (i in 0 until 7) {
        val y = rnd(i, step) ; val hgt = 0.015f + rnd(i, step + 50) * 0.05f
        val on = rnd(i, step + 90) > 0.35f
        if (!on) continue
        val x0 = rnd(i, step + 7) * 0.4f
        p.rect(col(if (i % 2 == 0) c1 else c2, 0.5f), x0, y, x0 + 0.4f + rnd(i, step + 3) * 0.5f, y + hgt)
    }
}
private fun rainbow(p: Pat, t: Float) {
    val n = 9
    for (i in 0 until n) {
        val hue = ((i.toFloat() / n + t * 0.12f) % 1f) * 360f
        val x = (i - 1f) / (n - 2f)
        p.line(Color.hsv(hue, 0.85f, 1f, 0.5f), x - 0.2f, 1.05f, x + 0.2f, -0.05f, 0.18f)
    }
}
private fun veins(p: Pat, t: Float, c: Long) {
    val a = 0.35f + 0.6f * pulse(t, 3.2f)
    val pts = floatArrayOf(.55f, .22f, .5f, .32f, .56f, .4f, .48f, .5f, .55f, .62f, .46f, .72f, .3f, .55f, .22f, .48f, .12f, .6f, .75f, .3f, .85f, .38f, .8f, .5f, .6f, .85f, .66f, .95f)
    var i = 0
    while (i + 3 < pts.size) { p.line(col(c, a), pts[i], pts[i + 1], pts[i + 2], pts[i + 3], 0.014f); i += 2 }
}
private fun rivets(p: Pat) {
    for (r in 0 until 5) for (c in 0 until 6) {
        val x = 0.1f + c * 0.15f + (r % 2) * 0.05f; val y = 0.12f + r * 0.17f
        p.oval(col(0xFF263238, 0.7f), x, y, 0.013f, 0.013f)
        p.oval(col(0xFFCFD8DC, 0.6f), x - 0.004f, y - 0.004f, 0.005f, 0.005f)
    }
}
private fun sunburst(p: Pat, t: Float) {
    for (i in 0 until 12) {
        val a = Math.toRadians((i * 30.0) + t * 25.0)
        val x1 = 0.58f + cos(a).toFloat() * 0.7f; val y1 = 0.36f + sin(a).toFloat() * 0.6f
        p.line(col(0xFFFFF176, 0.18f + 0.15f * pulse(t, 2f, i.toFloat())), 0.58f, 0.36f, x1, y1, 0.035f)
    }
    p.oval(col(0xFFFFFFFF, 0.35f + 0.25f * pulse(t, 3f)), 0.58f, 0.36f, 0.06f, 0.04f)
}
private fun embers(p: Pat, t: Float, c: Long) {
    p.rect(col(c, 0.30f), 0f, 0.7f, 1f, 1f)
    for (i in 0 until 14) {
        val ph = (t * (0.25f + rnd(i, 3) * 0.3f) + rnd(i, 4)) % 1f
        p.oval(col(0xFFFFD180, 1f - ph), rnd(i, 1), 1f - ph * 0.9f, 0.01f, 0.01f)
    }
}
private fun scales(p: Pat) {
    for (r in 0 until 12) for (c in 0 until 10) {
        val x = 0.05f + c * 0.1f + (r % 2) * 0.05f; val y = 0.05f + r * 0.08f
        p.oval(col(0xFF1B0F00, 0.25f), x, y, 0.045f, 0.028f)
        p.oval(col(0xFFFFCC80, 0.18f), x - 0.006f, y - 0.008f, 0.02f, 0.01f)
    }
}
private fun runes(p: Pat, t: Float) {
    for (i in 0 until 9) {
        val a = 0.3f + 0.6f * pulse(t, 1.5f, i * 1.3f)
        val x = 0.12f + rnd(i, 1) * 0.76f; val y = 0.1f + rnd(i, 2) * 0.8f
        p.line(col(0xFF64FFDA, a), x, y, x + 0.04f, y, 0.012f)
        p.line(col(0xFF64FFDA, a), x + 0.02f, y, x + 0.02f, y + 0.045f, 0.012f)
    }
}

// ---------------------------------------------------------------------------------------------- suit looks

class SuitLook(
    val grade: FloatArray?,
    val parts: List<String> = emptyList(),   // built-in pieces (replaced by a part the player equips in the same slot)
    val aura: Long = 0L,                      // soft glow behind the pilot
    val ghost: Long = 0L,                     // trailing afterimage colour (0 = none)
    val glitchSplit: Boolean = false,         // RGB-split copies
    val flicker: Boolean = false,             // signal-ghost style dropouts
    val pattern: ((Pat, Float) -> Unit)? = null
)

object SuitLooks {
    private val NONE = SuitLook(null)
    fun of(id: String): SuitLook = MAP[id] ?: NONE

    private val MAP: Map<String, SuitLook> = mapOf(
        "default" to NONE,

        // ---- story suits (earned the right to buy)
        "blackout_runner" to SuitLook(
            grade(150f, 0.55f, 0.9f, 0.85f, 1.0f, 0.7f, 0f, 0f, 0f),
            parts = listOf("flag_scarf", "spike_pauldron"),
            pattern = { p, _ -> camo(p, 3, listOf(0xFF2E3B1F, 0xFF55622E, 0xFF1B1F14, 0xFF7A6F45), 30, 0.7f) }
        ),
        "signal_ghost" to SuitLook(
            grade(0f, 0.5f, 1.25f, 0.6f, 1.15f, 1.25f, 0f, 14f, 20f),
            parts = listOf("antenna_array"), aura = 0xFF18FFFF, flicker = true, ghost = 0xFF80F0FF,
            pattern = { p, t -> scan(p, t, 0xFF006064, 0.22f); glitchBars(p, t, 0xFF18FFFF, 0xFFFFFFFF) }
        ),
        "convict_grey" to SuitLook(
            grade(0f, 0.06f, 0.95f, 0.9f, 0.92f, 0.98f, 0f, 0f, 0f),
            parts = listOf("shackle_chain"),
            pattern = { p, _ ->
                var y = 0.04f
                while (y < 1f) { p.rect(col(0xFF111111, 0.38f), 0f, y, 1f, y + 0.05f); y += 0.1f }
                p.rect(col(0xFFFF6D00, 0.85f), 0f, 0.30f, 1f, 0.335f)
                p.rect(col(0xFFFF6D00, 0.85f), 0f, 0.62f, 1f, 0.645f)
            }
        ),
        "apex_predator" to SuitLook(
            grade(115f, 1.15f, 0.85f, 1.1f, 0.9f, 0.5f, 0f, 0f, 0f),
            parts = listOf("oni_horns", "claw_spikes"),
            pattern = { p, t ->
                scales(p)
                for (i in 0 until 7) p.line(col(0xFF120800, 0.55f), 0.08f + i * 0.15f, -0.05f, 0.22f + i * 0.15f, 1.05f, 0.03f)
                p.oval(col(0xFFFF1744, 0.5f + 0.4f * pulse(t, 3f)), 0.8f, 0.14f, 0.02f, 0.012f)
            }
        ),

        // ---- paid collection
        "chrome_reaper" to SuitLook(
            grade(0f, 0.04f, 1.45f, 0.95f, 1.0f, 1.08f, 8f, 8f, 12f),
            parts = listOf("reaper_hood", "blade_bracer"),
            pattern = { p, t ->
                diagBands(p, t, 0xFFFFFFFF, 0.5f, 2, 0.11f, 0.35f)
                p.rect(col(0xFF000000, 0.2f), 0f, 0.75f, 1f, 1f)
            }
        ),
        "solar_flare" to SuitLook(
            grade(95f, 1.3f, 1.25f, 1.45f, 1.2f, 0.3f, 14f, 8f, 0f),
            parts = listOf("sun_crown", "gold_epaulette"), aura = 0xFFFFB300,
            pattern = { p, t -> sunburst(p, t) }
        ),
        "void_walker" to SuitLook(
            grade(0f, 0.7f, 0.5f, 0.7f, 0.55f, 1.15f, 0f, 0f, 10f),
            parts = listOf("void_cloak"), aura = 0xFF7C4DFF, ghost = 0xFF4A148C,
            pattern = { p, t -> stars(p, t, 36) }
        ),
        "toxic_bloom" to SuitLook(
            grade(130f, 1.3f, 1.1f, 0.55f, 1.35f, 0.45f, 0f, 0f, 0f),
            parts = listOf("flower_crest", "vine_scarf"), aura = 0xFF76FF03,
            pattern = { p, t ->
                spots(p, 5, listOf(0xFFCCFF90, 0xFF1B5E20, 0xFFB2FF59), 34, 0.7f, 0.03f)
                p.rect(col(0xFFB2FF59, 0.12f * pulse(t, 2f)), 0f, 0f, 1f, 1f)
            }
        ),
        "circuit_breaker" to SuitLook(
            grade(45f, 1.0f, 1.05f, 0.45f, 1.1f, 0.95f, 0f, 0f, 0f),
            parts = listOf("antenna_array", "power_gauntlet"), aura = 0xFFFFEA00,
            pattern = { p, t -> circuit(p, t, 0xFFFFEA00, 0xFFFFFFFF) }
        ),
        "golden_protocol" to SuitLook(
            grade(100f, 0.55f, 1.3f, 1.25f, 1.12f, 0.75f, 18f, 12f, 0f),
            parts = listOf("gold_crown", "gold_chain", "royal_cape"), aura = 0xFFFFD23F,
            pattern = { p, t ->
                diagBands(p, t, 0xFFFFFFFF, 0.38f, 3, 0.05f, 0.25f)
                for (i in 0 until 10) { val tw = pulse(t, 4f, i * 1.7f); p.oval(col(0xFFFFFFFF, tw), rnd(i, 4), rnd(i, 5), 0.008f, 0.008f) }
            }
        ),
        "glitch_core" to SuitLook(
            grade(0f, 1.35f, 1.0f, 1f, 1f, 1f, 0f, 0f, 0f),
            parts = listOf("twin_pods"), glitchSplit = true,
            pattern = { p, t -> glitchBars(p, t, 0xFF00E5FF, 0xFFFF1744) }
        ),
        "prism_vanguard" to SuitLook(
            grade(0f, 0.6f, 1.35f, 1.0f, 1.0f, 1.05f, 10f, 10f, 10f),
            parts = listOf("crystal_shard", "halo_ring"), aura = 0xFFB388FF,
            pattern = { p, t -> rainbow(p, t) }
        ),
        "iron_wraith" to SuitLook(
            grade(0f, 0.3f, 0.85f, 0.85f, 0.95f, 1.08f, 0f, 0f, 0f),
            parts = listOf("spike_pauldron", "chain_shackle"),
            pattern = { p, _ ->
                spots(p, 11, listOf(0xFFBF5B2C, 0xFF8D4A1F, 0xFFD17A3A), 16, 0.5f, 0.05f)
                rivets(p)
            }
        ),
        "nova_sprint" to SuitLook(
            grade(0f, 0.03f, 1.6f, 1f, 0.97f, 0.97f, 12f, 8f, 8f),
            parts = listOf("headband", "wing_boots"),
            pattern = { p, t ->
                p.line(col(0xFFE53935, 0.92f), -0.1f, 0.62f, 1.1f, 0.22f, 0.09f)
                p.line(col(0xFFE53935, 0.92f), -0.1f, 0.78f, 1.1f, 0.38f, 0.035f)
                diagBands(p, t, 0xFFFFFFFF, 0.25f, 1, 0.03f, 0.9f)
            }
        ),
        "obsidian_pulse" to SuitLook(
            grade(0f, 0.3f, 0.38f, 1.0f, 0.85f, 0.9f, 0f, 0f, 0f),
            parts = listOf("ember_horns", "skull_guard"), aura = 0xFFFF1744,
            pattern = { p, t -> veins(p, t, 0xFFFF1744) }
        ),

        // ---- earned / reward suits
        "echo_drift" to SuitLook(
            grade(40f, 0.8f, 1.2f, 0.6f, 1.1f, 1.25f, 0f, 10f, 14f),
            aura = 0xFF40C4FF, ghost = 0xFF40C4FF,
            pattern = { p, t -> scan(p, t, 0xFF006064, 0.12f) }
        ),
        "relic_core" to SuitLook(
            grade(120f, 0.7f, 0.95f, 0.75f, 1.05f, 0.95f, 0f, 6f, 4f),
            parts = listOf("halo_ring"), aura = 0xFF64FFDA,
            pattern = { p, t -> runes(p, t) }
        ),
        "signal_zero" to SuitLook(
            grade(0f, 0.1f, 0.65f, 1.3f, 0.35f, 0.45f, 0f, 0f, 0f),
            parts = listOf("antenna_array"), flicker = true, glitchSplit = true,
            pattern = { p, t -> glitchBars(p, t, 0xFFFF1744, 0xFFFFFFFF) }
        ),
        "zenith_circuit" to SuitLook(
            grade(30f, 1.1f, 1.1f, 0.7f, 0.8f, 1.4f, 0f, 0f, 0f),
            parts = listOf("mech_wings"), aura = 0xFF448AFF,
            pattern = { p, t -> circuit(p, t, 0xFF80D8FF, 0xFFFFFFFF) }
        ),
        "eternal_flame" to SuitLook(
            grade(70f, 1.3f, 1.05f, 1.4f, 0.5f, 0.45f, 10f, 0f, 0f),
            parts = listOf("ember_horns"), aura = 0xFFFF5252,
            pattern = { p, t -> embers(p, t, 0xFFFF3D00) }
        )
    )
}

// ---------------------------------------------------------------------------------------------- drawing

/** The loadout actually drawn: each suit's built-in pieces, overridden slot by slot by what the player equipped. */
fun effectiveParts(suitId: String, equipped: Map<String, String>): List<PilotPart> {
    val bySlot = LinkedHashMap<PartSlot, PilotPart>()
    SuitLooks.of(suitId).parts.forEach { id -> PartCatalog.byId(id)?.let { bySlot[it.slot] = it } }
    equipped.forEach { (slot, id) ->
        val part = PartCatalog.byId(id) ?: return@forEach
        if (part.slot.name == slot) bySlot[part.slot] = part
    }
    return bySlot.values.toList()
}

private val DRAW_ORDER = listOf(PartSlot.BACK, PartSlot.NECK, PartSlot.BOOTS, PartSlot.SHOULDER, PartSlot.WRIST, PartSlot.FLAME, PartSlot.HEAD, PartSlot.AURA)

private fun DrawScope.paintParts(parts: List<PilotPart>, behind: Boolean, fi: Int, l: Float, tp: Float, w: Float, h: Float, t: Float) {
    val r = FRAME_ANCHORS[((fi % 6) + 6) % 6][2] * h
    for (slot in DRAW_ORDER) {
        val part = parts.firstOrNull { it.slot == slot && it.behind == behind } ?: continue
        val pts = anchorsFor(slot, fi, l, tp, w, h)
        for (o in pts) paintPartById(part.id, PartPainter(this, o.x, o.y, r, t))
        // rarity shine: Epic and Legendary parts glow softly and throw a twinkling glint, so they read as treasure
        if (!behind && part.rarity.ordinal >= Rarity.EPIC.ordinal && part.slot != PartSlot.FLAME && part.slot != PartSlot.AURA) {
            val o = pts[0]
            val rc = Color(part.rarity.argb)
            drawCircle(Brush.radialGradient(listOf(rc.copy(alpha = 0.22f), Color.Transparent), center = o, radius = r * 2.2f),
                radius = r * 2.2f, center = o, blendMode = BlendMode.Plus)
            val ph = t * 1.6f + part.id.length
            val g = (sin(ph).coerceAtLeast(0f))
            if (g > 0.05f) {
                val gx = o.x + cos(ph * 0.7f) * r * 0.9f; val gy = o.y - r * 0.5f + sin(ph * 0.9f) * r * 0.6f
                val sz = r * 0.38f * g
                val gc = Color.White.copy(alpha = g)
                drawLine(gc, Offset(gx - sz, gy), Offset(gx + sz, gy), strokeWidth = r * 0.07f, cap = StrokeCap.Round)
                drawLine(gc, Offset(gx, gy - sz), Offset(gx, gy + sz), strokeWidth = r * 0.07f, cap = StrokeCap.Round)
            }
        }
    }
}

/**
 * Draws the pilot with its suit and parts, centred on (cx, cy) inside a w x h box.
 * [frameIdx] is the running-frame index 0..5 (so parts follow the pose). [partsOverride] lets previews try a loadout on.
 */
fun DrawScope.drawPilotLook(
    suitId: String, img: ImageBitmap, frameIdx: Int,
    cx: Float, cy: Float, w: Float, h: Float, tick: Int,
    partsOverride: Map<String, String>? = null
) {
    val look = SuitLooks.of(suitId)
    val t = tick * 0.05f
    val l = cx - w / 2f; val tp = cy - h / 2f
    val parts = effectiveParts(suitId, partsOverride ?: PilotWardrobe.equipped)
    val dstO = IntOffset(l.roundToInt(), tp.roundToInt()); val dstS = IntSize(w.roundToInt(), h.roundToInt())
    val srcS = IntSize(img.width, img.height)

    // soft aura behind everything
    if (look.aura != 0L) {
        val a = 0.16f + 0.1f * pulse(t, 2.2f)
        drawCircle(
            Brush.radialGradient(listOf(col(look.aura, a), Color.Transparent), center = Offset(cx, cy), radius = h * 0.62f),
            radius = h * 0.62f, center = Offset(cx, cy), blendMode = BlendMode.Plus
        )
    }
    paintParts(parts, true, frameIdx, l, tp, w, h, t)

    // Locker mastery: Bronze..Mythic collectors get orbiting stars around the pilot
    val mt = PilotWardrobe.masteryTier
    if (mt > 0) {
        val mc = Color(longArrayOf(0xFF607D8B, 0xFFCD7F32, 0xFFCFD8DC, 0xFFFFD23F, 0xFFE040FB)[mt.coerceIn(0, 4)])
        if (mt >= 4) drawOval(mc.copy(alpha = 0.35f), Offset(cx - w * 0.62f, cy - h * 0.2f), Size(w * 1.24f, h * 0.4f), style = androidx.compose.ui.graphics.drawscope.Stroke(h * 0.012f))
        for (k in 0 until mt) {
            val th = t * 1.1f + k * (6.2832f / mt)
            val ox = cx + cos(th) * w * 0.62f; val oy = cy + sin(th) * h * 0.2f
            val sz = h * 0.035f * (0.8f + 0.2f * sin(t * 4f + k))
            drawLine(mc, Offset(ox - sz, oy), Offset(ox + sz, oy), strokeWidth = h * 0.012f, cap = StrokeCap.Round)
            drawLine(mc, Offset(ox, oy - sz), Offset(ox, oy + sz), strokeWidth = h * 0.012f, cap = StrokeCap.Round)
        }
    }

    // afterimages / RGB split
    if (look.ghost != 0L) {
        for (k in 1..2) drawImage(img, IntOffset.Zero, srcS, IntOffset((l - k * w * 0.07f).roundToInt(), tp.roundToInt()), dstS,
            alpha = 0.22f / k, colorFilter = ColorFilter.tint(Color(look.ghost), BlendMode.SrcIn))
    }
    if (look.glitchSplit) {
        val s = ((tick / 4) % 5) * w * 0.012f + w * 0.01f
        drawImage(img, IntOffset.Zero, srcS, IntOffset((l - s).roundToInt(), tp.roundToInt()), dstS, alpha = 0.5f, colorFilter = ColorFilter.tint(Color(0xFFFF1744), BlendMode.SrcIn))
        drawImage(img, IntOffset.Zero, srcS, IntOffset((l + s).roundToInt(), tp.roundToInt()), dstS, alpha = 0.5f, colorFilter = ColorFilter.tint(Color(0xFF00E5FF), BlendMode.SrcIn))
    }

    var layerAlpha = 1f
    if (look.flicker) {
        val k = tick / 3
        layerAlpha = if (rnd(k, 77) > 0.88f) 0.35f else 0.82f + 0.18f * sin(t * 5f)
    }
    val filter = look.grade?.let { ColorFilter.colorMatrix(ColorMatrix(it)) }
    val self = this
    drawIntoCanvas { c ->
        val paint = Paint().apply { alpha = layerAlpha }
        c.saveLayer(Rect(l - 2f, tp - 2f, l + w + 2f, tp + h + 2f), paint)
        self.drawImage(img, IntOffset.Zero, srcS, dstO, dstS, colorFilter = filter)
        look.pattern?.invoke(Pat(self, l, tp, w, h), t)
        c.restore()
    }

    paintParts(parts, false, frameIdx, l, tp, w, h, t)
}

// ---------------------------------------------------------------------------------------------- composables

@Composable
fun rememberPilotFrames(): List<ImageBitmap> {
    val f1 = ImageBitmap.imageResource(id = R.drawable.pilot_run_1)
    val f2 = ImageBitmap.imageResource(id = R.drawable.pilot_run_2)
    val f3 = ImageBitmap.imageResource(id = R.drawable.pilot_run_3)
    val f4 = ImageBitmap.imageResource(id = R.drawable.pilot_run_4)
    val f5 = ImageBitmap.imageResource(id = R.drawable.pilot_run_5)
    val f6 = ImageBitmap.imageResource(id = R.drawable.pilot_run_6)
    return remember(f1, f2, f3, f4, f5, f6) { listOf(f1, f2, f3, f4, f5, f6) }
}
