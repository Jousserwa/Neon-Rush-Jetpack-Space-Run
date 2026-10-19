package com.neonrush.game.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * LIVING WORLDS 1-5. Two parts, both free of new image files:
 *  1. Layer passes: the world's own art is drawn AGAIN with a colour filter that keeps only its bright pixels (bulbs, windows, stars, neon)
 *     or only its red pixels (beacons), added on top and animated. Because it is the same picture, every glow sits exactly on its
 *     light, and it moves with the parallax. Lights flicker, pulse, blink and fail like real bulbs.
 *  2. A code-drawn overlay per world (WorldLife): searchlights, embers, rain, fireflies, steam, sparks, police lights...
 * Names start with "bg" / "wl".
 */
object BgFx {
    const val STARS = 1      // twinkling points
    const val WARM = 2       // orange and yellow windows / lamps: flicker, each part of the screen on its own beat
    const val RED = 4        // red beacons: blink on and off like warning lights
    const val BULB = 8       // bright bulbs that stutter and drop out, like failing fluorescents
    const val BLOOM = 16     // every bright pixel breathes slowly

    /** Which passes a layer gets. The passes read the art itself, so a layer with no bright pixels simply shows nothing extra. */
    fun mask(world: Int, idx: Int): Int = when (world) {
        1 -> when (idx) { 0 -> STARS; 1 -> BLOOM; 2 -> WARM or BLOOM; 3 -> BLOOM or RED; else -> BLOOM }
        2 -> when (idx) { 0 -> STARS; 1 -> RED or BLOOM; else -> RED or WARM }
        3 -> when (idx) { 0 -> BLOOM; 1 -> BULB or WARM or RED; else -> BULB or BLOOM }
        4 -> when (idx) { 0 -> BLOOM; 1 -> BLOOM; 2 -> BLOOM or WARM; else -> BLOOM }
        5 -> when (idx) { 0 -> STARS; 1 -> WARM or BLOOM; 2 -> WARM or RED or BLOOM; 3 -> BLOOM or RED; else -> RED or WARM }
        else -> 0
    }

    /** Pixels per second a layer keeps drifting even when the pilot is not moving (clouds, mist). */
    fun drift(world: Int, idx: Int): Float = when (world) {
        1 -> if (idx == 1) 7f else if (idx == 0) 1.5f else 0f
        2 -> if (idx == 0) 1.5f else 0f
        4 -> if (idx == 0) 3f else 0f
        5 -> if (idx == 0) 4f else 0f
        else -> 0f
    }

    /** Foliage / hanging things sway a little. Degrees, applied around the bottom centre. */
    fun sway(world: Int, idx: Int, t: Float): Float =
        if (world == 4 && idx >= 3) 0.35f * sin(t * (0.9f + idx * 0.13f) + idx) else 0f

    private fun m(vararg v: Float) = ColorFilter.colorMatrix(ColorMatrix(floatArrayOf(*v)))
    // offsets are on the 0..255 scale. If a device read them otherwise the result is simply "no glow", never a wrong picture.
    private val F_BLOOM = m(2.2f, 0f, 0f, 0f, -255f,  0f, 2.2f, 0f, 0f, -255f,  0f, 0f, 2.2f, 0f, -255f,  0f, 0f, 0f, 1f, 0f)
    private val F_WARM = m(3f, 0f, -3f, 0f, -330f,  1.95f, 0f, -1.95f, 0f, -215f,  0.75f, 0f, -0.75f, 0f, -83f,  0f, 0f, 0f, 1f, 0f)
    private val F_RED = m(3.2f, -1.6f, -1.6f, 0f, -230f,  0.96f, -0.48f, -0.48f, 0f, -69f,  0.8f, -0.4f, -0.4f, 0f, -58f,  0f, 0f, 0f, 1f, 0f)

    private fun strips(cw: Float, ch: Float, n: Int, paint: (Int) -> Unit, scope: DrawScope) {
        for (k in 0 until n) with(scope) { clipRect(cw * k / n, 0f, cw * (k + 1) / n, ch) { paint(k) } }
    }

    /**
     * Draws the extra passes for one layer. [paint] draws the layer again with the given filter, blend mode and alpha
     * (it is the same code that drew the layer, so the passes line up pixel for pixel).
     */
    fun passes(scope: DrawScope, world: Int, idx: Int, t: Float, cw: Float, ch: Float, paint: (ColorFilter?, BlendMode, Float) -> Unit) {
        val mk = mask(world, idx)
        if (mk == 0) return
        if (mk and STARS != 0) strips(cw, ch, 7, { k ->
            paint(F_BLOOM, BlendMode.Plus, (0.25f + 0.75f * abs(sin(t * (1.1f + k * 0.37f) + k * 2.1f))) * 0.9f)
        }, scope)
        if (mk and BLOOM != 0) paint(F_BLOOM, BlendMode.Plus, 0.22f + 0.14f * sin(t * 0.9f + idx))
        if (mk and WARM != 0) strips(cw, ch, 6, { k ->
            val n = fxRnd((t * 11f).toInt(), k + 3)
            val base = 0.55f + 0.20f * sin(t * 1.3f + k * 1.9f)
            val a = if (n > 0.94f) base * 0.3f else base + 0.15f * n
            paint(F_WARM, BlendMode.Plus, a)
            paint(F_WARM, BlendMode.Plus, a * 0.35f)
        }, scope)
        if (mk and RED != 0) {
            val on = (t * 0.85f) % 1f < 0.45f
            val a = if (on) 1f else 0.08f
            paint(F_RED, BlendMode.Plus, a)
            if (on) paint(F_RED, BlendMode.Plus, 0.35f)
        }
        if (mk and BULB != 0) strips(cw, ch, 5, { k ->
            val drop = fxRnd((t * 8f).toInt(), k + 11) > 0.80f
            paint(F_BLOOM, BlendMode.Plus, if (drop) 0.05f else 0.55f)
        }, scope)
    }
}

/** A 30 fps clock for the backgrounds. Read it inside draw blocks only, so nothing recomposes. */
@Composable
fun rememberBgClock(): State<Float> {
    val clock = remember { mutableStateOf(0f) }
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) withFrameNanos { n ->
            if (n - last >= 33_000_000L) { last = n; clock.value = ((n / 1_000_000L) % 600_000L) / 1000f }
        }
    }
    return clock
}

// ------------------------------------------------------------------------------------------------------ overlay

/** Code-drawn life on top of the world art: world 1..5. Other worlds draw nothing. */
@Composable
fun WorldLife(worldId: Int) {
    if (worldId !in 1..5) return
    val clock = rememberBgClock()
    Canvas(modifier = Modifier.fillMaxSize()) {
    try {
        val cw = size.width
        val ch = size.height
        val t = clock.value
        when (worldId) {
            1 -> wl1(cw, ch, t)
            2 -> wl2(cw, ch, t)
            3 -> wl3(cw, ch, t)
            4 -> wl4(cw, ch, t)
            5 -> wl5(cw, ch, t)
        }
    
    } catch (_: Throwable) { /* never crash a frame */ }
}
}

private fun wrap(v: Float, span: Float): Float = ((v % span) + span) % span

private fun DrawScope.wlBeam(apex: Offset, angleDeg: Float, len: Float, spreadDeg: Float, col: Color, a: Float) {
    val r1 = Math.toRadians((angleDeg - spreadDeg).toDouble())
    val r2 = Math.toRadians((angleDeg + spreadDeg).toDouble())
    val p = Path().apply {
        moveTo(apex.x, apex.y)
        lineTo(apex.x + len * sin(r1).toFloat(), apex.y - len * cos(r1).toFloat())
        lineTo(apex.x + len * sin(r2).toFloat(), apex.y - len * cos(r2).toFloat())
        close()
    }
    drawPath(p, Brush.linearGradient(listOf(col.copy(alpha = a), Color.Transparent), apex, Offset(apex.x + len * sin(Math.toRadians(angleDeg.toDouble())).toFloat(), apex.y - len * cos(Math.toRadians(angleDeg.toDouble())).toFloat())), blendMode = BlendMode.Plus)
}

private fun DrawScope.wlRise(n: Int, seedBase: Int, t: Float, cw: Float, ch: Float, speed: Float, col: Color, size: Float, from: Float = 1f, sway: Float = 0.03f) {
    for (k in 0 until n) {
        val sp = speed * (0.6f + 0.8f * fxRnd(k, seedBase))
        val life = wrap(t * sp + fxRnd(k, seedBase + 1), 1f)
        val x = (fxRnd(k, seedBase + 2) + sin(t * 0.8f + k) * sway) * cw
        val y = ch * (from - life * 0.9f)
        val a = (1f - life) * (0.3f + 0.7f * fxRnd(k, seedBase + 3))
        fxGlow(Offset(x, y), size * (0.6f + 0.8f * fxRnd(k, seedBase + 4)), col, a * 0.9f)
    }
}

private fun DrawScope.wlPuff(c: Offset, t: Float, seed: Int, r0: Float, col: Color, a: Float) {
    for (j in 0 until 4) {
        val life = wrap(t * 0.45f + j * 0.25f + fxRnd(seed, j), 1f)
        val p = Offset(c.x + sin(life * 6f + j + seed) * r0 * 0.5f, c.y - life * r0 * 5f)
        fxGlow(p, r0 * (0.6f + life * 1.8f), col, a * (1f - life))
    }
}

// World 1: BLACKOUT FRONT, a night war zone
private fun DrawScope.wl1(cw: Float, ch: Float, t: Float) {
    wlBeam(Offset(cw * 0.22f, ch * 0.9f), sin(t * 0.35f) * 26f - 8f, ch * 1.1f, 3.2f, Color(0xFFBBDEFB), 0.20f)
    wlBeam(Offset(cw * 0.78f, ch * 0.9f), sin(t * 0.29f + 2f) * 26f + 8f, ch * 1.1f, 3.2f, Color(0xFFBBDEFB), 0.16f)
    // distant gun flashes lighting the horizon
    val ph = wrap(t * 0.17f, 1f)
    val fl = if (ph < 0.025f) 1f - ph / 0.025f else 0f
    if (fl > 0f) drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color(0xFFFF9800).copy(alpha = 0.22f * fl)), ch * 0.45f, ch), blendMode = BlendMode.Plus)
    wlRise(26, 100, t, cw, ch, 0.12f, Color(0xFFFF8F00), ch * 0.014f)
    wlRise(14, 120, t, cw, ch, 0.05f, Color(0xFF90A4AE), ch * 0.02f, from = 0.96f)
}

// World 2: DERELICT SIGNAL, a dying space wreck
private fun DrawScope.wl2(cw: Float, ch: Float, t: Float) {
    val sw = maxOf(0f, sin(t * 2.6f))
    drawRect(Brush.horizontalGradient(listOf(Color(0xFFFF1744).copy(alpha = 0.16f * sw), Color.Transparent), 0f, cw * 0.5f), blendMode = BlendMode.Plus)
    drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Color(0xFFFF1744).copy(alpha = 0.16f * (1f - sw))), cw * 0.5f, cw), blendMode = BlendMode.Plus)
    // electrical sparks from a broken panel
    val cycle = 2.3f
    val n = (t / cycle).toInt()
    val ph = (t % cycle) / cycle
    if (ph < 0.14f) {
        val o = Offset(cw * (0.15f + 0.7f * fxRnd(n, 1)), ch * (0.25f + 0.5f * fxRnd(n, 2)))
        for (k in 0 until 9) {
            val ang = fxRnd(n, 10 + k) * 6.283f
            val d = ch * (0.02f + 0.07f * fxRnd(n, 30 + k)) * (ph / 0.14f)
            drawLine(Color(0xFFFFF59D).copy(alpha = 1f - ph / 0.14f), o, Offset(o.x + cos(ang) * d, o.y + sin(ang) * d), strokeWidth = ch * 0.003f, cap = StrokeCap.Round, blendMode = BlendMode.Plus)
        }
        fxGlow(o, ch * 0.08f, Color(0xFF80D8FF), 0.6f * (1f - ph / 0.14f))
    }
    // nav lights blinking on the wreck, and drifting dust
    for (k in 0 until 3) {
        val on = ((t * (0.7f + 0.2f * k) + k * 0.3f) % 1f) < 0.3f
        if (on) fxGlow(Offset(cw * (0.2f + 0.3f * k), ch * (0.18f + 0.09f * k)), ch * 0.03f, if (k == 1) Color(0xFF00E5FF) else Color(0xFFFF5252), 0.9f)
    }
    wlRise(34, 200, t, cw, ch, 0.03f, Color(0xFFB3E5FC), ch * 0.006f, from = 1f, sway = 0.05f)
}

// World 3: CELL BLOCK ZERO, a prison under alarm
private fun DrawScope.wl3(cw: Float, ch: Float, t: Float) {
    wlBeam(Offset(cw * 0.05f, ch * 0.02f), 118f + sin(t * 0.5f) * 30f, ch * 1.4f, 4.5f, Color(0xFFFFF9C4), 0.24f)
    val ang = t * 2.4f
    fxGlow(Offset(cw * 0.5f + cos(ang) * cw * 0.45f, ch * 0.08f), ch * 0.5f, Color(0xFFFF1744), 0.20f * (0.5f + 0.5f * sin(ang * 2f)))
    wlPuff(Offset(cw * 0.30f, ch * 0.97f), t, 1, ch * 0.03f, Color(0xFFCFD8DC), 0.16f)
    wlPuff(Offset(cw * 0.72f, ch * 0.97f), t, 2, ch * 0.035f, Color(0xFFCFD8DC), 0.14f)
    wlRise(18, 300, t, cw, ch, 0.025f, Color(0xFFFFE0B2), ch * 0.005f, sway = 0.04f)
}

// World 4: GREEN HELL, the jungle
private fun DrawScope.wl4(cw: Float, ch: Float, t: Float) {
    // light shafts through the canopy
    for (k in 0 until 3) {
        val x0 = cw * (0.15f + 0.30f * k) + sin(t * 0.2f + k) * cw * 0.03f
        val p = Path().apply { moveTo(x0, 0f); lineTo(x0 + cw * 0.10f, 0f); lineTo(x0 + cw * 0.34f, ch); lineTo(x0 + cw * 0.18f, ch); close() }
        drawPath(p, Brush.verticalGradient(listOf(Color(0xFFE6FF9A).copy(alpha = 0.12f + 0.05f * sin(t * 0.7f + k)), Color.Transparent), 0f, ch * 0.9f), blendMode = BlendMode.Plus)
    }
    // mist banks
    for (k in 0 until 3) {
        val x = wrap(k * cw * 0.5f - t * cw * (0.01f + 0.004f * k), cw * 1.6f) - cw * 0.3f
        fxGlow(Offset(x, ch * (0.62f + 0.12f * k)), ch * 0.40f, Color(0xFFB2DFDB), 0.10f)
    }
    // fireflies
    for (k in 0 until 26) {
        val bx = fxRnd(k, 400) * cw
        val byy = ch * (0.25f + 0.65f * fxRnd(k, 401))
        val p = Offset(bx + sin(t * (0.4f + fxRnd(k, 402)) + k) * cw * 0.05f, byy + cos(t * (0.5f + fxRnd(k, 403)) + k * 2f) * ch * 0.04f)
        val pulse = 0.5f + 0.5f * sin(t * (1.5f + fxRnd(k, 404) * 2f) + k * 3f)
        fxGlow(p, ch * 0.022f, Color(0xFFCCFF66), 0.85f * pulse * pulse)
    }
    // falling leaves
    for (k in 0 until 10) {
        val life = wrap(t * 0.07f * (0.6f + fxRnd(k, 410)) + fxRnd(k, 411), 1f)
        val x = (fxRnd(k, 412) * 1.2f - life * 0.3f) * cw + sin(t * 1.3f + k) * cw * 0.03f
        val y = life * ch * 1.05f
        withTransform({ rotate(t * 60f + k * 40f, Offset(x, y)) }) {
            drawOval(Color(0xFF2E7D32).copy(alpha = 0.75f), Offset(x - ch * 0.012f, y - ch * 0.005f), Size(ch * 0.024f, ch * 0.010f))
        }
    }
}

// World 5: RED PROTOCOL, a city under lockdown in the rain
private fun DrawScope.wl5(cw: Float, ch: Float, t: Float) {
    // police lights washing the walls
    val flip = ((t * 2.2f).toInt() % 2 == 0)
    val pl = Color(if (flip) 0xFFFF1744 else 0xFF2979FF)
    drawRect(Brush.horizontalGradient(listOf(pl.copy(alpha = 0.14f), Color.Transparent), 0f, cw * 0.55f), blendMode = BlendMode.Plus)
    val pr = Color(if (flip) 0xFF2979FF else 0xFFFF1744)
    drawRect(Brush.horizontalGradient(listOf(Color.Transparent, pr.copy(alpha = 0.10f)), cw * 0.45f, cw), blendMode = BlendMode.Plus)
    // rain
    for (k in 0 until 70) {
        val sp = 1.2f + fxRnd(k, 500)
        val ry = wrap(t * sp * 1.7f + fxRnd(k, 501), 1f) * ch
        val rx = fxRnd(k, 502) * cw * 1.25f - ry * 0.16f
        drawLine(Color(0xFFB3C6FF).copy(alpha = 0.20f), Offset(rx, ry), Offset(rx - ch * 0.010f, ry + ch * 0.042f), strokeWidth = ch * 0.0015f)
    }
    // splashes and shimmer on the wet road
    for (k in 0 until 16) {
        val life = wrap(t * 2.2f + fxRnd(k, 510), 1f)
        val x = fxRnd(k, 511) * cw
        val y = ch * (0.80f + 0.17f * fxRnd(k, 512))
        drawOval(Color.White.copy(alpha = 0.35f * (1f - life)), Offset(x - ch * 0.02f * life, y - ch * 0.004f * life), Size(ch * 0.04f * life, ch * 0.008f * life), style = androidx.compose.ui.graphics.drawscope.Stroke(width = ch * 0.0012f))
    }
    for (k in 0 until 6) {
        val y = ch * (0.78f + 0.2f * ((k + wrap(t * 0.2f, 1f)) / 6f))
        val x = wrap(fxRnd(k, 520) * cw - t * cw * 0.05f, cw)
        drawLine(Color(0xFFFF8A80).copy(alpha = 0.10f), Offset(x, y), Offset(x + cw * 0.25f, y), strokeWidth = ch * 0.0018f, blendMode = BlendMode.Plus)
    }
    // a surveillance drone crossing high up, blinking
    val dx = wrap(t * cw * 0.06f, cw * 1.4f) - cw * 0.2f
    val dy = ch * 0.16f + sin(t * 0.8f) * ch * 0.01f
    drawLine(Color(0xFF90A4AE).copy(alpha = 0.7f), Offset(dx - ch * 0.02f, dy), Offset(dx + ch * 0.02f, dy), strokeWidth = ch * 0.004f, cap = StrokeCap.Round)
    if ((t * 1.8f) % 1f < 0.4f) fxGlow(Offset(dx, dy), ch * 0.03f, Color(0xFFFF1744), 0.95f)
    wlPuff(Offset(cw * 0.55f, ch * 0.97f), t, 5, ch * 0.03f, Color(0xFFCFD8DC), 0.12f)
}
