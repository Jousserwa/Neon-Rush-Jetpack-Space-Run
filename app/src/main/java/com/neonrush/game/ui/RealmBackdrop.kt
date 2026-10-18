package com.neonrush.game.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import com.neonrush.game.Realm
import com.neonrush.game.RealmRules
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/**
 * REALM BACKDROPS: whole scenes drawn in code (no image files), so a realm does not look like a re-coloured normal world.
 * They fade in over the normal parallax art when the realm starts and fade out when it ends. Names start with "rb".
 * Supported: SHATTER (fractured prism sky over a mirror lake), STORM (boiling clouds, flickering lightning, rough sea),
 * GRAVITY (star field falling toward a distant black hole).
 */
@Composable
fun RealmBackdrop(zone: Int, distanceMeters: Float) {
    val realm = RealmRules.realmFor(zone)
    val supported = realm == Realm.SHATTER || realm == Realm.STORM || realm == Realm.GRAVITY
    var shown by remember { mutableStateOf(Realm.NONE) }
    if (supported) shown = realm
    val a by animateFloatAsState(if (supported) 1f else 0f, tween(1400), label = "realmBackdrop")
    if (a <= 0.01f) return
    val clock = remember { mutableStateOf(0f) }
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) withFrameNanos { n ->
            if (n - last >= 33_000_000L) { last = n; clock.value = ((n / 1_000_000L) % 600_000L) / 1000f }
        }
    }
    Canvas(modifier = Modifier.fillMaxSize().graphicsLayer { alpha = a }) {
        val cw = size.width
        val ch = size.height
        val t = clock.value
        val s = distanceMeters * 3f
        when (shown) {
            Realm.SHATTER -> rbShatter(cw, ch, t, s)
            Realm.STORM -> rbStorm(cw, ch, t, s)
            Realm.GRAVITY -> rbGravity(cw, ch, t, s)
            else -> {}
        }
    }
}

private fun wrap(v: Float, span: Float): Float = ((v % span) + span) % span

/** A scrolling ridge line, filled down to the bottom of the screen. */
private fun DrawScope.rbRidge(cw: Float, ch: Float, baseY: Float, amp: Float, freq: Float, scroll: Float, seed: Float, top: Color, bottom: Color) {
    val p = Path()
    p.moveTo(0f, ch)
    val step = cw / 36f
    var x = 0f
    while (x <= cw + step) {
        val u = (x + scroll) * freq
        val y = baseY + amp * (0.55f * sin(u + seed) + 0.30f * sin(u * 2.3f + seed * 1.7f) + 0.15f * sin(u * 5.1f + seed * 3f))
        p.lineTo(x, y)
        x += step
    }
    p.lineTo(cw + step, ch)
    p.close()
    drawPath(p, Brush.verticalGradient(listOf(top, bottom), startY = baseY - amp, endY = ch))
}

private fun DrawScope.rbTri(a: Offset, b: Offset, c: Offset, fill: Color, edge: Color?, w: Float) {
    val p = Path().apply { moveTo(a.x, a.y); lineTo(b.x, b.y); lineTo(c.x, c.y); close() }
    drawPath(p, fill)
    if (edge != null) drawPath(p, edge, style = Stroke(width = w))
}

// ------------------------------------------------------------------------------------------------------------ SHATTER

private fun DrawScope.rbShatter(cw: Float, ch: Float, t: Float, s: Float) {
    val cyan = Color(0xFF7DF9FF)
    val mag = Color(0xFFFF4FD8)
    drawRect(Brush.verticalGradient(listOf(Color(0xFF05061A), Color(0xFF1A1450), Color(0xFF2B1B6B), Color(0xFF0E5A78)), 0f, ch), size = Size(cw, ch))
    // a huge cracked sun
    val sun = Offset(cw * 0.70f, ch * 0.30f)
    fxGlow(sun, ch * 0.55f, Color(0xFFFF7AD9), 0.30f)
    drawCircle(Brush.radialGradient(listOf(Color(0xFFFFE6FF), Color(0xFFFF7AD9), Color(0x00FF7AD9)), center = sun, radius = ch * 0.20f), radius = ch * 0.20f, center = sun)
    for (k in 0 until 7) {
        val ang = fxRnd(k, 40) * 6.283f
        val e = Offset(sun.x + cos(ang) * ch * 0.22f, sun.y + sin(ang) * ch * 0.22f)
        drawLine(Color(0xFF1A1450).copy(alpha = 0.55f), sun, e, strokeWidth = ch * 0.003f, cap = StrokeCap.Round)
    }
    // the prism sky: facets that drift and catch light
    val cols = 9
    val rows = 5
    val cellW = cw / (cols - 1)
    val cellH = ch * 0.62f / rows
    val shift = s * 0.05f
    val base = floor(shift / cellW).toInt()
    val ox = -wrap(shift, cellW)
    for (i in -1..cols) for (j in 0 until rows) {
        val idx = i + base
        val x0 = ox + i * cellW
        val x1 = x0 + cellW
        val y0 = j * cellH
        val y1 = y0 + cellH
        val c1 = when ((fxRnd(idx, j) * 3f).toInt()) { 0 -> cyan; 1 -> mag; else -> Color.White }
        val c2 = when ((fxRnd(idx, j + 50) * 3f).toInt()) { 0 -> cyan; 1 -> mag; else -> Color.White }
        val glint = 0.5f + 0.5f * sin(t * 0.6f + idx * 1.3f + j)
        rbTri(Offset(x0, y0), Offset(x1, y0), Offset(x0, y1), c1.copy(alpha = 0.02f + 0.11f * fxRnd(j, idx) * glint), Color.White.copy(alpha = 0.05f), ch * 0.0012f)
        rbTri(Offset(x1, y0), Offset(x1, y1), Offset(x0, y1), c2.copy(alpha = 0.02f + 0.11f * fxRnd(idx + 9, j) * (1f - glint * 0.5f)), Color.White.copy(alpha = 0.05f), ch * 0.0012f)
    }
    // floating shards at three depths
    for (k in 0 until 22) {
        val depth = 0.15f + 0.85f * fxRnd(k, 60)
        val span = cw * 1.4f
        val x = wrap(fxRnd(k, 61) * span - s * 0.25f * depth, span) - cw * 0.2f
        val y = ch * (0.08f + 0.60f * fxRnd(k, 62)) + sin(t * 0.6f + k) * ch * 0.012f
        val r = ch * (0.015f + 0.05f * depth)
        val rot = t * (0.3f + fxRnd(k, 63)) + k
        val p1 = Offset(x + cos(rot) * r, y + sin(rot) * r)
        val p2 = Offset(x + cos(rot + 2.2f) * r * 0.8f, y + sin(rot + 2.2f) * r * 0.8f)
        val p3 = Offset(x + cos(rot + 4.1f) * r * 1.1f, y + sin(rot + 4.1f) * r * 1.1f)
        rbTri(p1, p2, p3, (if (k % 2 == 0) cyan else mag).copy(alpha = 0.10f + 0.12f * depth), Color.White.copy(alpha = 0.35f * depth), ch * 0.002f)
    }
    // mirror lake: dark ridge line, then its reflection
    val horizon = ch * 0.72f
    rbRidge(cw, ch, horizon - ch * 0.03f, ch * 0.05f, 0.004f, s * 0.12f, 1.3f, Color(0xFF0A0F3A), Color(0xFF05061A))
    drawRect(Brush.verticalGradient(listOf(Color(0xFF16A0C8).copy(alpha = 0.55f), Color(0xFF0A1850)), horizon, ch), Offset(0f, horizon), Size(cw, ch - horizon))
    for (k in 0 until 9) {
        val y = horizon + (ch - horizon) * ((k + wrap(t * 0.15f, 1f)) / 9f)
        val w = cw * (0.2f + 0.5f * fxRnd(k, 70))
        val x = wrap(fxRnd(k, 71) * cw - s * 0.3f, cw)
        drawLine(Color.White.copy(alpha = 0.10f + 0.12f * (1f - (y - horizon) / (ch - horizon))), Offset(x, y), Offset(x + w, y), strokeWidth = ch * 0.002f)
    }
    drawRect(Color.Black.copy(alpha = 0.22f), size = Size(cw, ch)) // keep gameplay readable on top
}

// ------------------------------------------------------------------------------------------------------------ STORM

private fun DrawScope.rbStorm(cw: Float, ch: Float, t: Float, s: Float) {
    drawRect(Brush.verticalGradient(listOf(Color(0xFF05040F), Color(0xFF14123A), Color(0xFF2A2468), Color(0xFF1B2A58)), 0f, ch), size = Size(cw, ch))
    // distant lightning inside the clouds
    val ph = wrap(t * 0.55f, 1f)
    val flash = if (ph < 0.07f) 1f - ph / 0.07f else 0f
    val ph2 = wrap(t * 0.37f + 0.5f, 1f)
    val flash2 = if (ph2 < 0.05f) 1f - ph2 / 0.05f else 0f
    val fl = maxOf(flash, flash2 * 0.8f)
    val seed = floor(t * 0.55f).toInt()
    if (flash > 0f) {
        val bx = cw * (0.15f + 0.7f * fxRnd(seed, 1))
        fxBolt(Offset(bx, ch * 0.12f), Offset(bx + (fxRnd(seed, 2) - 0.5f) * cw * 0.2f, ch * 0.62f), seed, Color(0xFFB388FF), ch * 0.01f, 0.5f * flash, 0.10f)
        fxBolt(Offset(bx, ch * 0.12f), Offset(bx + (fxRnd(seed, 2) - 0.5f) * cw * 0.2f, ch * 0.62f), seed, Color.White, ch * 0.0035f, flash, 0.10f)
    }
    // three banks of cloud, each layer slower and darker the further back
    for (layer in 0 until 3) {
        val sp = 0.12f + layer * 0.22f
        val y0 = ch * (0.06f + layer * 0.10f)
        val n = 9
        val span = cw * 1.5f
        for (k in 0 until n) {
            val x = wrap(k * span / n + fxRnd(k, 80 + layer) * cw * 0.1f - s * sp - t * cw * 0.004f * (layer + 1), span) - cw * 0.25f
            val r = ch * (0.20f + 0.10f * fxRnd(k, 85 + layer)) * (1f + layer * 0.15f)
            val dark = Color(0xFF0B0A26).copy(alpha = 0.85f - layer * 0.08f)
            drawCircle(Brush.radialGradient(listOf(dark, Color.Transparent), center = Offset(x, y0), radius = r), radius = r, center = Offset(x, y0))
            fxGlow(Offset(x, y0 + r * 0.2f), r * 0.9f, Color(0xFF8E7BFF), (0.07f + 0.5f * fl * 0.5f) * (1f - layer * 0.2f))
        }
    }
    // rough sea
    val horizon = ch * 0.76f
    drawRect(Brush.verticalGradient(listOf(Color(0xFF1B2A58), Color(0xFF070B1E)), horizon, ch), Offset(0f, horizon), Size(cw, ch - horizon))
    for (k in 0 until 14) {
        val d = (k + 1) / 14f
        val y = horizon + (ch - horizon) * d * d
        val amp = ch * 0.01f * (0.3f + d)
        val p = Path()
        var x = 0f
        val step = cw / 28f
        p.moveTo(0f, y)
        while (x <= cw + step) {
            p.lineTo(x, y + sin((x + s * (0.5f + d)) * 0.012f * (1f + d) + t * (1.5f + d) + k) * amp)
            x += step
        }
        drawPath(p, Color(0xFFB3C6FF).copy(alpha = 0.08f + 0.20f * d + 0.25f * fl), style = Stroke(width = ch * (0.001f + 0.002f * d)))
    }
    drawRect(Color(0xFFB388FF).copy(alpha = 0.10f * fl), size = Size(cw, ch), blendMode = BlendMode.Plus)
    drawRect(Color.Black.copy(alpha = 0.18f), size = Size(cw, ch))
}

// ------------------------------------------------------------------------------------------------------------ GRAVITY

private fun DrawScope.rbGravity(cw: Float, ch: Float, t: Float, s: Float) {
    drawRect(Brush.verticalGradient(listOf(Color(0xFF02030C), Color(0xFF0B0B2E), Color(0xFF020309)), 0f, ch), size = Size(cw, ch))
    val bh = Offset(cw * 0.78f, ch * 0.46f)
    // a nebula glow, then stars that lean toward the black hole
    fxGlow(Offset(cw * 0.25f, ch * 0.30f), ch * 0.7f, Color(0xFF3949AB), 0.16f)
    fxGlow(bh, ch * 0.9f, Color(0xFF6A1B9A), 0.14f)
    for (k in 0 until 90) {
        val depth = 0.1f + 0.9f * fxRnd(k, 90)
        val span = cw * 1.2f
        var x = wrap(fxRnd(k, 91) * span - s * 0.15f * depth, span) - cw * 0.1f
        var y = ch * fxRnd(k, 92)
        val dx = bh.x - x
        val dy = bh.y - y
        val d = kotlin.math.sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
        val pull = (1f - d / (ch * 0.9f)).coerceIn(0f, 1f)
        x += dx / d * pull * pull * ch * 0.12f
        y += dy / d * pull * pull * ch * 0.12f
        val tw = 0.5f + 0.5f * sin(t * (1f + depth * 2f) + k)
        drawCircle(Color.White.copy(alpha = (0.25f + 0.6f * depth) * (0.5f + 0.5f * tw)), radius = ch * 0.0016f * (1f + depth * 1.5f), center = Offset(x, y))
    }
    // the black hole: dark core, bright thin ring, slowly turning lensing arcs
    val r = ch * 0.11f
    fxGlow(bh, r * 3.2f, Color(0xFFFF8F00), 0.18f)
    for (k in 0 until 3) {
        val rr = r * (1.25f + 0.28f * k)
        withTransform({ rotate(t * (14f - 5f * k), bh) }) {
            drawCircle(Color(0xFFFFB74D).copy(alpha = 0.30f - 0.07f * k), radius = rr, center = bh, style = Stroke(width = ch * (0.006f - 0.0015f * k)))
            drawCircle(Color(0xFFFFE0B2).copy(alpha = 0.55f - 0.15f * k), radius = rr, center = bh, style = Stroke(width = ch * 0.002f, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(ch * 0.05f, ch * 0.09f), 0f)))
        }
    }
    drawCircle(Color.Black, radius = r, center = bh)
    drawCircle(Color(0xFFFFE0B2).copy(alpha = 0.8f), radius = r, center = bh, style = Stroke(width = ch * 0.0025f))
    drawRect(Color.Black.copy(alpha = 0.20f), size = Size(cw, ch))
}
