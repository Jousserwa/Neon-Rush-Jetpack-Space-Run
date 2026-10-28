package com.neonrush.game.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Home-screen screensaver scenes.
 *  tier 0 = everyone (the classic cyan run, now brighter)
 *  tier 1 = Pro MONTHLY: violet / magenta / electric blue. Two scenes that cross-fade every 16 s: PRISM NEBULA <-> NEON HORIZON (magenta / cyan / amber / blue / mint)
 *  tier 2 = Pro ANNUAL : gold / amber / crimson. Two scenes that cross-fade every 16 s: GOLDEN SUPERNOVA <-> ROYAL WARP
 * Everything is code-drawn from one smooth clock ([t] in seconds), so there are no assets and no layout impact.
 */
private const val TWO_PI = 6.2831855f

fun screensaverAccent(tier: Int): Color = when (tier) {
    1 -> Color(0xFFFF7AE0)
    2 -> Color(0xFFFFC94A)
    else -> Color(0xFF00E5FF)
}

fun screensaverBadge(tier: Int): String = when (tier) { 1 -> "PRO MONTHLY"; 2 -> "PRO ANNUAL"; else -> "" }

fun screensaverSceneName(tier: Int, scene: Int): String = when (tier) {
    1 -> if (scene == 0) "PRISM NEBULA" else "NEON HORIZON"
    2 -> if (scene == 0) "GOLDEN SUPERNOVA" else "ROYAL WARP"
    else -> ""
}

/** Fades the scene-name label in for ~3 s after each scene switch, then hides it. */
fun screensaverLabelAlpha(t: Float): Float {
    val u = t % 16f
    return when {
        u < 0.4f -> u / 0.4f
        u < 3.0f -> 1f
        u < 3.6f -> 1f - (u - 3.0f) / 0.6f
        else -> 0f
    }.coerceIn(0f, 1f)
}

/** [fullScreen] = the scene is the whole app background: no edge fade (nothing to melt into), slightly dimmed so the screens on top stay readable. */
fun DrawScope.drawScreensaverBackdrop(tier: Int, t: Float, fullScreen: Boolean = false) {
    if (tier == 0) { freeScene(t); return }
    if (fullScreen) {
        drawIntoCanvas { cv -> cv.saveLayer(Rect(0f, 0f, size.width, size.height), Paint().apply { alpha = 0.82f }) }
        when (tier) {
            1 -> twoScenes(t, { monthlyNebula(t) }, { monthlyAurora(t) })
            else -> twoScenes(t, { annualSupernova(t) }, { annualWarp(t) })
        }
        drawIntoCanvas { cv -> cv.restore() }
        return
    }
    // Draw the scene in its own layer, then fade all four edges to nothing, so it melts into the home screen instead of sitting in a box.
    drawIntoCanvas { cv -> cv.saveLayer(Rect(0f, 0f, size.width, size.height), Paint()) }
    when (tier) {
        1 -> twoScenes(t, { monthlyNebula(t) }, { monthlyAurora(t) })
        else -> twoScenes(t, { annualSupernova(t) }, { annualWarp(t) })
    }
    drawRect(Brush.verticalGradient(0f to Color.Transparent, 0.24f to Color.Black, 0.76f to Color.Black, 1f to Color.Transparent), size = size, blendMode = BlendMode.DstIn)
    drawRect(Brush.horizontalGradient(0f to Color.Transparent, 0.12f to Color.Black, 0.88f to Color.Black, 1f to Color.Transparent), size = size, blendMode = BlendMode.DstIn)
    drawIntoCanvas { cv -> cv.restore() }
}

/** Scene A for 16 s, a 2 s cross-fade, scene B for 16 s, a 2 s cross-fade back... */
private fun DrawScope.twoScenes(t: Float, a: DrawScope.() -> Unit, b: DrawScope.() -> Unit) {
    val cyc = (t % 32f) / 16f
    val wB = when {
        cyc < 0.875f -> 0f
        cyc < 1.0f -> (cyc - 0.875f) / 0.125f
        cyc < 1.875f -> 1f
        else -> 1f - (cyc - 1.875f) / 0.125f
    }.coerceIn(0f, 1f)
    if (wB < 0.999f) a()
    layered(wB) { b() }
}

private fun DrawScope.layered(alpha: Float, block: DrawScope.() -> Unit) {
    if (alpha <= 0.01f) return
    if (alpha >= 0.99f) { block(); return }
    drawIntoCanvas { cv -> cv.saveLayer(Rect(0f, 0f, size.width, size.height), Paint().apply { this.alpha = alpha }) }
    block()
    drawIntoCanvas { cv -> cv.restore() }
}

private fun DrawScope.starLayer(t: Float, n: Int, tints: List<Color>, speed: Float, bright: Float) {
    val cw = size.width
    val ch = size.height
    for (i in 0 until n) {
        val layer = i % 3
        val x = (fxRnd(i, 11) * cw - t * cw * 0.03f * (1 + layer) * speed).mod(cw)
        val y = fxRnd(i, 12) * ch
        val tw = 0.55f + 0.45f * sin(t * (1.5f + (i % 5) * 0.4f) + i)
        val a = ((0.40f + 0.15f * layer) * tw + 0.25f) * bright
        val r = (0.9f + layer * 0.9f).dp.toPx()
        val c = tints[i % tints.size]
        if (layer == 2) drawCircle(c.copy(alpha = (a * 0.25f).coerceIn(0f, 1f)), r * 3.4f, Offset(x, y))
        drawCircle(c.copy(alpha = a.coerceIn(0f, 1f)), r, Offset(x, y))
    }
}

// ------------------------------------------------------------------------------------------------ FREE (brighter than before)

private fun DrawScope.freeScene(t: Float) {
    val cw = size.width
    val ch = size.height
    // a soft cyan haze so the whole box feels lit, not black
    drawRect(Brush.radialGradient(listOf(Color(0xFF00E5FF).copy(alpha = 0.18f), Color(0xFF3D5AFE).copy(alpha = 0.07f), Color.Transparent),
        center = Offset(cw / 2f, ch / 2f), radius = maxOf(cw, ch) * 0.7f), size = size)
    starLayer(t, 56, listOf(Color.White, Color(0xFFB3F0FF), Color(0xFFFFC2F2)), 5.5f, 1.25f)
    // speed lines, clearer than before
    for (i in 0..9) {
        val y = ch * 0.12f + (i * 91 % 100) / 100f * ch * 0.76f
        val x = ((i * 137 % 100) / 100f * cw - t * 166f * 1.6f).mod(cw)
        drawLine(Color(0xFF00E5FF).copy(alpha = 0.60f), Offset(x, y), Offset(x + 40f, y), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
    }
}

// ------------------------------------------------------------------------------------------------ MONTHLY: violet / magenta / blue

private fun DrawScope.monthlyNebula(t: Float) {
    // PRISM NEBULA: a bright, many-coloured cosmos. Magenta, cyan, amber, electric blue and mint clouds, turning prism rings, rainbow crystals and light beams.
    val cw = size.width
    val ch = size.height
    val c = Offset(cw / 2f, ch * 0.55f)
    val big = maxOf(cw, ch)
    drawRect(Brush.verticalGradient(listOf(Color(0xFF160542), Color(0xFF5A1085), Color(0xFFA31B97))), size = size)
    val blobs = listOf(Color(0xFFFF2FB8), Color(0xFF22E4FF), Color(0xFFFF9A3D), Color(0xFF3D6BFF), Color(0xFF52FFB8))
    for (i in 0 until 5) {
        val bc = Offset(cw * (0.1f + 0.2f * i + 0.09f * sin(t * 0.25f + i * 1.3f)), ch * (0.45f + 0.25f * sin(t * 0.18f + i * 2.1f)))
        fxGlow(bc, ch * (0.85f + 0.12f * sin(t * 0.35f + i)), blobs[i], 0.40f)
    }
    // slow sweeping light beams from the bottom centre
    for (k in 0 until 4) {
        val a = -PI.toFloat() / 2f + sin(t * 0.3f + k * 1.6f) * 0.9f + (k - 1.5f) * 0.35f
        val p = Path()
        p.moveTo(cw / 2f, ch * 1.05f)
        p.lineTo(cw / 2f + cos(a - 0.07f) * big * 1.3f, ch * 1.05f + sin(a - 0.07f) * big * 1.3f)
        p.lineTo(cw / 2f + cos(a + 0.07f) * big * 1.3f, ch * 1.05f + sin(a + 0.07f) * big * 1.3f)
        p.close()
        drawPath(p, blobs[k % 5].copy(alpha = 0.12f))
    }
    starLayer(t, 80, listOf(Color.White, Color(0xFFBFF4FF), Color(0xFFFFC2F2), Color(0xFFFFE3A8)), 3.5f, 1.2f)
    // turning dashed prism rings in changing colours
    for (k in 0 until 3) {
        val dir = if (k % 2 == 0) 1f else -1f
        val hue = ((t * 18f + k * 120f) % 360f + 360f) % 360f
        drawCircle(Color.hsv(hue, 0.55f, 1f).copy(alpha = 0.55f), radius = ch * (0.22f + 0.17f * k), center = c,
            style = Stroke(width = 1.8.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 16f), dir * t * (22f + 9f * k))))
    }
    // turning crystals with rainbow edges
    for (k in 0 until 6) {
        val sp = 0.014f + 0.006f * (k % 3)
        val x = (((fxRnd(k, 1) - t * sp) % 1f) + 1f) % 1f * cw
        val y = ch * (0.12f + 0.74f * fxRnd(k, 2)) + sin(t * 0.5f + k) * ch * 0.03f
        val r = ch * (0.05f + 0.045f * fxRnd(k, 3))
        val rot = t * (0.4f + 0.2f * k)
        val p = Path()
        for (j in 0..5) {
            val a = rot + j * (TWO_PI / 6f)
            if (j == 0) p.moveTo(x + cos(a) * r, y + sin(a) * r) else p.lineTo(x + cos(a) * r, y + sin(a) * r)
        }
        p.close()
        val hue = ((t * 30f + k * 60f) % 360f + 360f) % 360f
        drawPath(p, Color.hsv(hue, 0.6f, 1f).copy(alpha = 0.22f))
        drawPath(p, Color.hsv((hue + 40f) % 360f, 0.45f, 1f).copy(alpha = 0.85f), style = Stroke(width = 1.6.dp.toPx()))
        fxGlow(Offset(x, y), r * 1.6f, Color.hsv(hue, 0.6f, 1f), 0.25f)
    }
    // multicoloured comets
    for (k in 0 until 4) {
        val p = (t * 0.24f + k / 4f) % 1f
        val vis = sin(p * PI.toFloat())
        val hx = cw * (1.15f - 1.3f * p)
        val hy = ch * (0.06f + 0.24f * k) + ch * 0.25f * p
        val tc = listOf(Color(0xFFFF7AE0), Color(0xFF7DF9FF), Color(0xFFFFC46B), Color(0xFFB9A2FF))[k]
        for (s in 0 until 12) {
            val q = s / 12f
            drawCircle(tc.copy(alpha = (1f - q) * 0.8f * vis), radius = (3.4f * (1f - q) + 0.5f).dp.toPx(), center = Offset(hx + q * cw * 0.15f, hy - q * ch * 0.08f))
        }
        fxGlow(Offset(hx, hy), ch * 0.09f, tc, 0.75f * vis)
    }
}

private fun DrawScope.monthlyAurora(t: Float) {
    // NEON HORIZON: a synthwave sunset. Striped sun, glowing mountains, a gliding cyan and magenta grid, falling stars.
    val cw = size.width
    val ch = size.height
    val hy = ch * 0.64f
    drawRect(Brush.verticalGradient(0f to Color(0xFF1A0540), 0.35f to Color(0xFF6A1B9A), 0.65f to Color(0xFFFF4081), 1f to Color(0xFFFF9E40), startY = 0f, endY = hy), size = Size(cw, hy))
    starLayer(t, 46, listOf(Color.White, Color(0xFFFFD1F5), Color(0xFFBFE9FF)), 1.6f, 1.0f)
    // the sun
    val sunR = ch * 0.24f
    val sc = Offset(cw / 2f, hy - sunR * 0.25f)
    fxGlow(sc, sunR * 2.2f, Color(0xFFFFB74D), 0.45f + 0.08f * sin(t * 1.5f))
    drawCircle(Brush.verticalGradient(listOf(Color(0xFFFFEE58), Color(0xFFFF9100), Color(0xFFFF2D95)), startY = sc.y - sunR, endY = sc.y + sunR), sunR, sc)
    for (i in 0 until 6) {
        val dy = sunR * (0.05f + 0.16f * i) + sin(t * 0.8f) * 1.5f
        val half = kotlin.math.sqrt((sunR * sunR - dy * dy).coerceAtLeast(0f))
        drawRect(Color(0xFFC2185B), Offset(sc.x - half, sc.y + dy), Size(half * 2f, (2f + i * 2.2f).dp.toPx()))
    }
    // distant mountains with a neon edge
    val m = Path()
    m.moveTo(0f, hy)
    val pts = 14
    for (i in 0..pts) {
        val x = cw * i / pts
        val peak = ch * (0.07f + 0.11f * fxRnd(i, 5)) * (if (i % 2 == 0) 1f else 0.55f)
        m.lineTo(x, hy - peak)
    }
    m.lineTo(cw, hy); m.close()
    drawPath(m, Color(0xFF2A0A4D))
    drawPath(m, Color(0xFFFF4DD2).copy(alpha = 0.6f), style = Stroke(width = 1.6.dp.toPx()))
    // the ground and its gliding grid
    drawRect(Brush.verticalGradient(listOf(Color(0xFF1B0540), Color(0xFF0B0220)), startY = hy, endY = ch), Offset(0f, hy), Size(cw, ch - hy))
    val cyan = Color(0xFF22E4FF); val mag = Color(0xFFFF4DD2)
    fxGlow(Offset(cw / 2f, hy), cw * 0.55f, mag, 0.22f)
    drawLine(cyan.copy(alpha = 0.9f), Offset(0f, hy), Offset(cw, hy), strokeWidth = 2.dp.toPx())
    for (i in -10..10) drawLine((if (i % 2 == 0) cyan else mag).copy(alpha = 0.45f), Offset(cw / 2f + i * cw * 0.02f, hy), Offset(cw / 2f + i * cw * 0.20f, ch), strokeWidth = 1.3f)
    for (k in 0 until 7) {
        val f = (t * 0.30f + k / 7f) % 1f
        val yy = hy + (ch - hy) * f * f
        drawLine((if (k % 2 == 0) cyan else mag).copy(alpha = 0.15f + 0.45f * f), Offset(0f, yy), Offset(cw, yy), strokeWidth = 1.4f)
    }
    // shooting stars and floating gems
    for (k in 0 until 3) {
        val p = (t * 0.30f + k / 3f) % 1f
        val vis = sin(p * PI.toFloat())
        val x0 = cw * (0.9f - 0.9f * p + 0.1f * k); val y0 = hy * (0.05f + 0.55f * p) * (0.6f + 0.2f * k)
        drawLine(Color.White.copy(alpha = 0.8f * vis), Offset(x0, y0), Offset(x0 + cw * 0.12f, y0 - ch * 0.05f), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
    }
    for (k in 0 until 5) {
        val x = cw * (0.08f + 0.2f * k) + sin(t * 0.6f + k) * 6f
        val y = hy * (0.18f + 0.5f * fxRnd(k, 8)) + sin(t * 0.9f + k * 2f) * ch * 0.02f
        val r = ch * 0.025f
        val col = listOf(cyan, mag, Color(0xFFFFE082), Color(0xFF69F0AE), Color(0xFFB388FF))[k]
        val d = Path().apply { moveTo(x, y - r); lineTo(x + r * 0.7f, y); lineTo(x, y + r); lineTo(x - r * 0.7f, y); close() }
        drawPath(d, col.copy(alpha = 0.35f)); drawPath(d, col, style = Stroke(width = 1.4.dp.toPx()))
    }
}

// ------------------------------------------------------------------------------------------------ ANNUAL: gold / amber / crimson

private fun DrawScope.annualSupernova(t: Float) {
    val cw = size.width
    val ch = size.height
    val c = Offset(cw / 2f, ch / 2f)
    val big = maxOf(cw, ch)
    drawRect(Brush.radialGradient(listOf(Color(0xFF6B3200), Color(0xFF2E0F00), Color(0xFF0E0300)), center = c, radius = big * 0.75f), size = size)
    // a slowly turning sunburst
    for (k in 0 until 18 step 2) {
        val a0 = t * 0.25f + k * (TWO_PI / 18f)
        val a1 = a0 + TWO_PI / 18f
        val p = Path()
        p.moveTo(c.x, c.y)
        p.lineTo(c.x + cos(a0) * big, c.y + sin(a0) * big)
        p.lineTo(c.x + cos(a1) * big, c.y + sin(a1) * big)
        p.close()
        drawPath(p, Color(0xFFFFD54F).copy(alpha = 0.11f))
    }
    // golden shockwaves
    for (k in 0 until 3) {
        val f = (t * 0.35f + k / 3f) % 1f
        drawCircle(Color(0xFFFFC94A).copy(alpha = (1f - f) * 0.55f), radius = f * big * 0.7f, center = c, style = Stroke(width = (1f + 3f * (1f - f)).dp.toPx()))
    }
    fxGlow(c, ch * 0.7f, Color(0xFFFFB300), 0.34f + 0.10f * sin(t * 2f))
    // embers rising
    for (i in 0 until 46) {
        val x = fxRnd(i, 1) * cw + sin(t * 0.8f + i) * cw * 0.02f
        val f = (t * (0.10f + 0.12f * fxRnd(i, 2)) + fxRnd(i, 3)) % 1f
        val y = ch * (1.05f - 1.1f * f)
        val col = when (i % 3) { 0 -> Color(0xFFFFD54F); 1 -> Color(0xFFFF9100); else -> Color.White }
        val a = sin(f * PI.toFloat()).coerceIn(0f, 1f)
        if (i % 4 == 0) fxGlow(Offset(x, y), 9.dp.toPx(), col, 0.45f * a)
        drawCircle(col.copy(alpha = a), (1f + i % 3).dp.toPx(), Offset(x, y))
    }
}

private fun DrawScope.annualWarp(t: Float) {
    val cw = size.width
    val ch = size.height
    val c = Offset(cw / 2f, ch / 2f)
    val r = sqrt(cw * cw + ch * ch) / 2f
    drawRect(Brush.verticalGradient(listOf(Color(0xFF12000A), Color(0xFF2B0010), Color(0xFF12000A))), size = size)
    fxGlow(c, ch * 0.6f, Color(0xFFFF1744), 0.28f)
    // warp streaks racing out of the centre
    for (i in 0 until 64) {
        val ang = fxRnd(i, 1) * TWO_PI
        val f = (t * (0.25f + 0.35f * fxRnd(i, 2)) + fxRnd(i, 3)) % 1f
        val r0 = r * f * f
        val r1 = r0 + r * 0.10f * (0.2f + f)
        val col = when (i % 3) { 0 -> Color(0xFFFFD54F); 1 -> Color.White; else -> Color(0xFFFF1744) }
        drawLine(col.copy(alpha = f.coerceIn(0f, 1f)), Offset(c.x + cos(ang) * r0, c.y + sin(ang) * r0), Offset(c.x + cos(ang) * r1, c.y + sin(ang) * r1),
            strokeWidth = (0.5f + 2.5f * f).dp.toPx(), cap = StrokeCap.Round)
    }
    // turning dashed golden rings
    for (k in 0 until 3) {
        val dir = if (k % 2 == 0) 1f else -1f
        drawCircle(Color(0xFFFFC94A).copy(alpha = 0.38f), radius = ch * (0.28f + 0.2f * k), center = c,
            style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 18f), dir * t * (24f + 10f * k))))
    }
    // a royal diamond emblem
    for (k in 0 until 2) {
        val s = ch * (0.13f + 0.09f * k) * (1f + 0.05f * sin(t * 2f + k))
        rotate(degrees = (if (k == 0) 1f else -1f) * t * 20f + 45f * k, pivot = c) {
            drawRect(Color(0xFFFFD54F).copy(alpha = 0.55f - 0.2f * k), topLeft = Offset(c.x - s, c.y - s), size = Size(s * 2f, s * 2f), style = Stroke(width = 1.5.dp.toPx()))
        }
    }
    // corner sparkles
    for (k in 0 until 4) {
        val tw = 0.5f + 0.5f * sin(t * 2.2f + k * 1.6f)
        fxSpark(Offset(if (k % 2 == 0) cw * 0.08f else cw * 0.92f, if (k < 2) ch * 0.14f else ch * 0.86f), 8.dp.toPx() * tw, Color(0xFFFFE082), tw)
    }
}
