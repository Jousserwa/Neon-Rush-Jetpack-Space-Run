package com.neonrush.game.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.abs
import kotlin.math.sin

/**
 * REMIXED WORLDS: a re-coloured world is not enough to feel new, so every variant also gets its own SHAPE.
 * [variantGeo] picks, from the variant's name, whether the art is mirrored left-right, turned upside down, zoomed, how fast it
 * scrolls, and which code-drawn foreground scene stands in front of it (spires, hanging teeth, moon over ridges, pylons, waves, floating rocks).
 * No image files. Names start with "vs".
 */
class VGeo(val flipX: Boolean, val flipY: Boolean, val zoom: Float, val speed: Float, val scene: Int)

fun variantGeo(v: WorldVariant): VGeo {
    var h = 17
    for (c in v.key + v.suffix) h = h * 31 + c.code
    h = abs(h xor (h ushr 7))
    return VGeo(
        flipX = h % 2 == 0,
        flipY = (h / 2) % 5 == 0,
        zoom = 1f + ((h / 10) % 4) * 0.04f,
        speed = 0.8f + ((h / 40) % 5) * 0.1f,
        scene = (h / 200) % 6
    )
}

@Composable
fun VariantScene(variant: WorldVariant, distanceMeters: Float) {
    val clock = rememberBgClock()
    val geo = remember(variant.key, variant.suffix) { variantGeo(variant) }
    Canvas(modifier = Modifier.fillMaxSize()) {
        try {
            val cw = size.width
            val ch = size.height
            if (cw <= 1f || ch <= 1f) return@Canvas
            val t = clock.value
            val s = distanceMeters * 3.2f * geo.speed
            val ac = variant.accent
            val seed = abs(variant.key.hashCode() % 997) * 0.37f
            when (geo.scene) {
                0 -> vsSpires(cw, ch, t, s, ac, seed, false)
                1 -> vsSpires(cw, ch, t, s, ac, seed, true)
                2 -> vsMoonRidge(cw, ch, t, s, ac, seed)
                3 -> vsPylons(cw, ch, t, s, ac, seed)
                4 -> vsWaves(cw, ch, t, s, ac)
                else -> vsIslands(cw, ch, t, s, ac, seed)
            }
        } catch (_: Throwable) { }
    }
}

private val vsDark = Color(0xFF04050B)

private fun vsH(i: Int, salt: Int): Float {
    var x = i * 374761393 + salt * 668265263
    x = (x xor (x ushr 13)) * 1274126177.toInt()
    return ((x xor (x ushr 16)) and 0xFFFF) / 65535f
}

/** Crystal spires from the floor, or the same teeth hanging from the ceiling. */
private fun DrawScope.vsSpires(cw: Float, ch: Float, t: Float, s: Float, ac: Color, seed: Float, hanging: Boolean) {
    val spacing = cw * 0.16f
    val first = (s / spacing).toInt() - 1
    val n = (cw / spacing).toInt() + 4
    for (k in 0 until n) {
        val i = first + k
        val x = i * spacing - s
        val hgt = ch * (0.14f + 0.20f * vsH(i, 3))
        val w = spacing * (0.5f + 0.5f * vsH(i, 5))
        val baseY = if (hanging) 0f else ch
        val tipY = if (hanging) hgt else ch - hgt
        val p = Path().apply {
            moveTo(x - w / 2f, baseY); lineTo(x + w * 0.1f * (vsH(i, 7) - 0.5f), tipY); lineTo(x + w / 2f, baseY); close()
        }
        drawPath(p, Brush.verticalGradient(if (hanging) listOf(vsDark, ac.copy(alpha = 0.35f)) else listOf(ac.copy(alpha = 0.35f), vsDark),
            startY = minOf(baseY, tipY), endY = maxOf(baseY, tipY)))
        drawPath(p, ac.copy(alpha = 0.5f), style = Stroke(width = 1.5f))
        val pulse = 0.5f + 0.5f * sin(t * 1.6f + i)
        drawCircle(Brush.radialGradient(listOf(ac.copy(alpha = 0.35f * pulse), Color.Transparent), center = Offset(x, tipY), radius = w * 0.9f),
            radius = w * 0.9f, center = Offset(x, tipY), blendMode = BlendMode.Plus)
    }
}

/** A huge ringed moon over two layers of ridge silhouettes. */
private fun DrawScope.vsMoonRidge(cw: Float, ch: Float, t: Float, s: Float, ac: Color, seed: Float) {
    val c = Offset(cw * 0.72f, ch * 0.28f)
    val r = ch * 0.16f
    drawCircle(Brush.radialGradient(listOf(ac.copy(alpha = 0.55f), ac.copy(alpha = 0.10f), Color.Transparent), center = c, radius = r * 2.4f),
        radius = r * 2.4f, center = c, blendMode = BlendMode.Plus)
    drawCircle(Color.White.copy(alpha = 0.16f), radius = r, center = c)
    drawCircle(ac.copy(alpha = 0.5f), radius = r * 1.5f, center = c, style = Stroke(width = 2f))
    for (layer in 0 until 2) {
        val par = if (layer == 0) 0.35f else 0.8f
        val base = ch * (if (layer == 0) 0.80f else 0.90f)
        val amp = ch * (if (layer == 0) 0.12f else 0.08f)
        val path = Path().apply {
            moveTo(0f, ch)
            var x = 0f
            while (x <= cw + 12f) {
                val u = (x + s * par) * 0.011f + seed + layer * 2.1f
                val h = sin(u) * 0.5f + sin(u * 2.3f + 1.7f) * 0.3f + sin(u * 5.1f) * 0.2f
                lineTo(x, base - amp * (0.5f + 0.5f * h))
                x += 12f
            }
            lineTo(cw, ch); close()
        }
        drawPath(path, if (layer == 0) vsDark.copy(alpha = 0.7f) else vsDark.copy(alpha = 0.95f))
    }
}

/** Tall pylons with blinking tips and a beam of light going up. */
private fun DrawScope.vsPylons(cw: Float, ch: Float, t: Float, s: Float, ac: Color, seed: Float) {
    val spacing = cw * 0.24f
    val first = (s / spacing).toInt() - 1
    val n = (cw / spacing).toInt() + 3
    for (k in 0 until n) {
        val i = first + k
        val x = i * spacing - s
        val hgt = ch * (0.30f + 0.28f * vsH(i, 11))
        val w = cw * 0.03f
        drawRect(vsDark, topLeft = Offset(x - w / 2f, ch - hgt), size = androidx.compose.ui.geometry.Size(w, hgt))
        drawLine(ac.copy(alpha = 0.45f), Offset(x - w / 2f, ch - hgt), Offset(x - w / 2f, ch), strokeWidth = 1.5f)
        val on = ((t * 1.2f + vsH(i, 13) * 4f) % 2f) < 1.1f
        val tip = Offset(x, ch - hgt)
        if (on) drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.9f), ac.copy(alpha = 0.5f), Color.Transparent), center = tip, radius = w * 2.2f),
            radius = w * 2.2f, center = tip, blendMode = BlendMode.Plus)
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, ac.copy(alpha = if (on) 0.22f else 0.07f)), startY = 0f, endY = ch - hgt),
            topLeft = Offset(x - w * 0.6f, 0f), size = androidx.compose.ui.geometry.Size(w * 1.2f, ch - hgt), blendMode = BlendMode.Plus)
    }
}

/** Three bands of rolling swell (water, dunes or fog banks, depending on the colour). */
private fun DrawScope.vsWaves(cw: Float, ch: Float, t: Float, s: Float, ac: Color) {
    for (b in 0 until 3) {
        val base = ch * (0.78f + 0.07f * b)
        val amp = ch * (0.018f + 0.012f * b)
        val par = 0.4f + 0.4f * b
        val path = Path().apply {
            moveTo(0f, ch)
            var x = 0f
            while (x <= cw + 10f) {
                lineTo(x, base + sin((x + s * par) * 0.012f + t * (0.6f + 0.3f * b)) * amp + sin((x + s * par) * 0.031f - t) * amp * 0.5f)
                x += 10f
            }
            lineTo(cw, ch); close()
        }
        drawPath(path, Brush.verticalGradient(listOf(ac.copy(alpha = 0.22f + 0.05f * b), vsDark.copy(alpha = 0.9f)), startY = base - amp * 2f, endY = ch))
    }
}

/** Floating rocks drifting and bobbing. */
private fun DrawScope.vsIslands(cw: Float, ch: Float, t: Float, s: Float, ac: Color, seed: Float) {
    val spacing = cw * 0.30f
    val first = (s * 0.6f / spacing).toInt() - 1
    val n = (cw / spacing).toInt() + 3
    for (k in 0 until n) {
        val i = first + k
        val x = i * spacing - s * 0.6f
        val y = ch * (0.55f + 0.30f * vsH(i, 17)) + sin(t * 0.8f + i * 1.7f) * ch * 0.012f
        val w = spacing * (0.45f + 0.35f * vsH(i, 19))
        val h = w * 0.55f
        val rock = Path().apply {
            moveTo(x - w / 2f, y); lineTo(x + w / 2f, y); lineTo(x + w * 0.12f, y + h); lineTo(x - w * 0.18f, y + h * 0.7f); close()
        }
        drawPath(rock, vsDark.copy(alpha = 0.92f))
        drawLine(ac.copy(alpha = 0.6f), Offset(x - w / 2f, y), Offset(x + w / 2f, y), strokeWidth = 2f)
        drawCircle(Brush.radialGradient(listOf(ac.copy(alpha = 0.25f), Color.Transparent), center = Offset(x, y + h * 0.4f), radius = w * 0.8f),
            radius = w * 0.8f, center = Offset(x, y + h * 0.4f), blendMode = BlendMode.Plus)
    }
}
