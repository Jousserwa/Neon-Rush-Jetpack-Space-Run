package com.neonrush.game.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.neonrush.game.VisualTrackElement
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.pow

/**
 * Code-drawn art for the new threats (arc pylons, lava, mountains, boss charge-up, WARN markers) and the
 * SHADOW clones, which re-use the player's own pilot frames (mirrored, tinted, with afterimages).
 * Returns true when it drew [elem]; the caller then skips its normal drawing.
 */
internal fun DrawScope.drawThreat(
    elem: VisualTrackElement, x: Float, y: Float, cw: Float, ch: Float,
    tick: Int, pilot: ImageBitmap,
    /** Smooth seconds clock (30 fps) so flames, smoke and warnings animate between the 8-per-second game ticks. */
    time: Float = tick * 0.12f
): Boolean {
    val t = time / 0.12f           // same speeds as before, but continuous
    when (elem.subType) {
        "WARN" -> { drawWarn(elem, y, cw, ch, tick, time); return true }
        "BOSS_CHARGE" -> {
            val k = (elem.id.split("_").getOrNull(2)?.toIntOrNull() ?: 1).coerceIn(1, 5) / 5f
            val bx = cw * 0.88f - ch * 0.1f
            val c = Color(0xFFFF4433)
            // aim line, brighter as the shot gets closer
            drawLine(c.copy(alpha = 0.10f + 0.35f * k), Offset(bx, y), Offset(cw * 0.22f, y), strokeWidth = 1.5f + 3f * k)
            // charging orb + converging sparks
            val r = ch * (0.015f + 0.05f * k)
            drawCircle(Brush.radialGradient(listOf(Color.White, c, Color.Transparent), center = Offset(bx, y), radius = r * 2.4f), r * 2.4f, Offset(bx, y))
            for (i in 0 until 6) {
                val ang = (i / 6f) * 2f * PI.toFloat() + t * 0.6f
                val d = r * (3.2f - 2.2f * k)
                drawCircle(Color.White.copy(alpha = 0.8f), 2.2f, Offset(bx + cos(ang) * d, y + sin(ang) * d))
            }
            return true
        }
        "FIRE_WALL" -> { drawFlame(x, y, ch * 0.17f, time, elem.id.hashCode()); return true }
        "LAVA_BALL" -> {
            val r = ch * 0.045f
            val c = Color(0xFFFF5A1F)
            // ember trail
            for (i in 1..4) drawCircle(c.copy(alpha = 0.35f - i * 0.07f), r * (1f - i * 0.15f), Offset(x + i * r * 0.9f, y - sin(t * 0.45f + i) * r * 0.3f))
            drawCircle(Brush.radialGradient(listOf(Color(0xFFFFF2A0), Color(0xFFFF8A00), Color(0xFFB3200A), Color.Transparent), center = Offset(x, y), radius = r * 1.6f), r * 1.6f, Offset(x, y))
            drawCircle(Color(0xFFFFE066), r * 0.45f, Offset(x - r * 0.15f, y - r * 0.15f))
            return true
        }
        "MOUNTAIN_BOTTOM", "MOUNTAIN_TOP" -> {
            // A stack of 2-3 hit-boxes shares one mountain: draw it once (from the first piece), the rest only collide.
            val parts = elem.id.split("_")
            if ((parts.getOrNull(3)?.toIntOrNull() ?: 0) != 0) return true
            val seed = (parts.getOrNull(2) ?: elem.id).hashCode()
            val half = cw * 0.075f
            if (elem.subType == "MOUNTAIN_BOTTOM") drawMountain(x, y - ch * 0.10f, ch, half, seed, time, ch, false)
            else scale(1f, -1f, Offset(0f, ch / 2f)) { drawMountain(x, ch - (y + ch * 0.10f), ch, half, seed, time, ch, true) }
            return true
        }
        "SHADOW_MIRROR", "SHADOW_CHARGER", "SHADOW_SWARM", "SHADOW_STRAFER" -> {
            val tint = when (elem.subType) {
                "SHADOW_MIRROR" -> Color(0xFF8A2BE2)
                "SHADOW_CHARGER" -> Color(0xFFFF2B4D)
                "SHADOW_SWARM" -> Color(0xFF00E5FF)
                else -> Color(0xFFFF2BD6)
            }
            val h = ch * 0.18f
            val w = h * (pilot.width.toFloat() / pilot.height.toFloat())
            val cf = ColorFilter.tint(tint, BlendMode.SrcIn)
            // afterimages trail behind (to the right: it is coming at you)
            for (i in 3 downTo 1) {
                val ox = x + i * w * (if (elem.subType == "SHADOW_CHARGER") 0.55f else 0.28f)
                scale(-1f, 1f, Offset(ox, y)) {
                    drawImage(pilot, dstOffset = IntOffset((ox - w / 2f).roundToInt(), (y - h / 2f).roundToInt()),
                        dstSize = IntSize(w.roundToInt(), h.roundToInt()), alpha = 0.16f * (4 - i), colorFilter = cf)
                }
            }
            val flick = 0.78f + 0.22f * sin(t * 0.9f)
            drawCircle(tint.copy(alpha = 0.18f * flick), h * 0.7f, Offset(x, y))
            scale(-1f, 1f, Offset(x, y)) {
                drawImage(pilot, dstOffset = IntOffset((x - w / 2f).roundToInt(), (y - h / 2f).roundToInt()),
                    dstSize = IntSize(w.roundToInt(), h.roundToInt()), alpha = 0.92f * flick, colorFilter = cf)
            }
            if (elem.subType == "SHADOW_CHARGER") drawLine(tint.copy(alpha = 0.5f), Offset(x, y), Offset(x + w * 2.2f, y), strokeWidth = 3f)
            return true
        }
        "SHADOW_SHOT" -> {
            val c = Color(0xFFFF2BD6)
            drawLine(c.copy(alpha = 0.4f), Offset(x + ch * 0.07f, y), Offset(x, y), strokeWidth = 4f)
            drawCircle(c.copy(alpha = 0.9f), ch * 0.014f, Offset(x, y))
            drawCircle(Color.White, ch * 0.006f, Offset(x, y))
            return true
        }
    }
    return false
}


private class Rng(var s: Int) {
    fun next(): Float { s = s * 1103515245 + 12345; return ((s ushr 8) and 0xFFFF) / 65535f }
}

/**
 * ARC PYLON (replaces the old fire column): two glowing emitter nodes with a crackling plasma arc between them
 * and a faint energy field around it. Additive light in the game's own cyan / magenta, so it sits in the
 * scene like the crystals and neon instead of looking pasted on. Pure vector; driven by the smooth clock.
 */
private fun DrawScope.drawFlame(x: Float, y: Float, size: Float, time: Float, seed: Int) {
    val ph = (seed and 0xff) / 40f
    val pulse = 0.85f + 0.15f * sin(time * 9f + ph)
    val cyan = Color(0xFF00E5FF); val mag = Color(0xFFFF2D95); val violet = Color(0xFF7C4DFF)
    val half = size * 0.78f
    val top = Offset(x, y - half); val bot = Offset(x, y + half)

    // energy field: tall soft ellipse of light between the nodes
    drawOval(Brush.radialGradient(listOf(violet.copy(alpha = 0.34f * pulse), Color.Transparent), center = Offset(x, y), radius = half * 1.25f),
        topLeft = Offset(x - size * 0.55f, y - half * 1.15f), size = Size(size * 1.1f, half * 2.3f), blendMode = BlendMode.Plus)
    drawCircle(Brush.radialGradient(listOf(mag.copy(alpha = 0.30f * pulse), Color.Transparent), center = Offset(x, y), radius = size * 0.7f),
        size * 0.7f, Offset(x, y), blendMode = BlendMode.Plus)

    // lightning: re-rolled ~16 times a second so it crackles; three bolts of different thickness
    val step = (time * 16f).toInt()
    for (b in 0 until 3) {
        val r = Rng(seed * 31 + step * 7 + b * 101)
        val path = Path().apply {
            moveTo(top.x, top.y)
            val segs = 9
            for (k in 1 until segs) {
                val f = k / segs.toFloat()
                val amp = size * (0.10f + 0.16f * sin(f * PI.toFloat())) * (if (b == 0) 1f else 0.7f)
                lineTo(x + (r.next() - 0.5f) * 2f * amp, top.y + (bot.y - top.y) * f)
            }
            lineTo(bot.x, bot.y)
        }
        val a = if (b == 0) 1f else 0.6f
        drawPath(path, mag.copy(alpha = 0.20f * a), style = Stroke(width = size * 0.16f, cap = StrokeCap.Round), blendMode = BlendMode.Plus)
        drawPath(path, (if (b == 0) mag else cyan).copy(alpha = 0.85f * a), style = Stroke(width = size * 0.055f, cap = StrokeCap.Round), blendMode = BlendMode.Plus)
        drawPath(path, Color.White.copy(alpha = 0.95f * a), style = Stroke(width = size * 0.022f, cap = StrokeCap.Round), blendMode = BlendMode.Plus)
    }
    // short side crackles jumping off the arc
    for (k in 0 until 4) {
        val r = Rng(seed + step * 13 + k * 57)
        val sy = y + (r.next() - 0.5f) * half * 1.6f
        val dir = if (r.next() > 0.5f) 1f else -1f
        val len = size * (0.18f + 0.22f * r.next())
        val mid = Offset(x + dir * len * 0.5f, sy + (r.next() - 0.5f) * size * 0.12f)
        val end = Offset(x + dir * len, sy + (r.next() - 0.5f) * size * 0.2f)
        drawLine(cyan.copy(alpha = 0.8f), Offset(x, sy), mid, strokeWidth = size * 0.03f, blendMode = BlendMode.Plus)
        drawLine(cyan.copy(alpha = 0.6f), mid, end, strokeWidth = size * 0.022f, blendMode = BlendMode.Plus)
    }

    // emitter nodes: diamond core, spinning ring with shards, bright bloom
    for (n in 0..1) {
        val c = if (n == 0) top else bot
        drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.9f * pulse), cyan.copy(alpha = 0.5f), Color.Transparent), center = c, radius = size * 0.38f),
            size * 0.38f, c, blendMode = BlendMode.Plus)
        val rr = size * 0.15f
        val d = Path().apply { moveTo(c.x, c.y - rr); lineTo(c.x + rr * 0.7f, c.y); lineTo(c.x, c.y + rr); lineTo(c.x - rr * 0.7f, c.y); close() }
        drawPath(d, Color(0xFF0B1430)); drawPath(d, cyan, style = Stroke(width = size * 0.03f))
        val spin = time * (n * 2 - 1) * 3f + ph
        for (k in 0 until 4) {
            val ang = spin + k * PI.toFloat() / 2f
            drawCircle(mag.copy(alpha = 0.9f), size * 0.03f, Offset(c.x + cos(ang) * size * 0.26f, c.y + sin(ang) * size * 0.1f), blendMode = BlendMode.Plus)
        }
    }
    // drifting motes
    for (k in 0 until 5) {
        val p = (time * (0.6f + k * 0.1f) + k * 0.2f + ph) % 1f
        drawCircle(cyan.copy(alpha = (1f - p) * 0.8f), size * 0.028f * (1f - p * 0.6f),
            Offset(x + sin(k * 2.1f + p * 5f) * size * 0.3f + p * size * 0.25f, y + (k - 2) * size * 0.3f), blendMode = BlendMode.Plus)
    }
}

/**
 * Craggy mountain: jagged two-face silhouette (lit + shadow side), rim light, cracks. Two looks picked by seed:
 * a VOLCANO (glowing crater, flowing lava veins, smoke plume, flying embers) or a frosted PEAK (snow cap).
 * [flipped] = hanging from the ceiling: drawn upside down by the caller, smoke becomes falling lava drips.
 */
private fun DrawScope.drawMountain(x: Float, peakY: Float, baseY: Float, half: Float, seed: Int, time: Float, ch: Float, flipped: Boolean) {
    val rng = Rng(seed)
    val volcano = (seed and 0x7fffffff) % 3 != 0
    val n = 6
    val h = baseY - peakY
    // silhouette points: index 0 = peak side, n = base
    val lx = FloatArray(n + 1); val ly = FloatArray(n + 1); val rx = FloatArray(n + 1); val ry = FloatArray(n + 1)
    for (i in 0..n) {
        val f = i / n.toFloat()
        val w = half * (f.pow(0.82f)) * 1.12f
        val jx = if (i == 0 || i == n) 0f else half * 0.10f * (rng.next() - 0.5f) * 2f
        val jy = if (i == 0 || i == n) 0f else h * 0.035f * (rng.next() - 0.5f) * 2f
        lx[i] = x - w + jx; ly[i] = peakY + h * f + jy
        val jx2 = if (i == 0 || i == n) 0f else half * 0.10f * (rng.next() - 0.5f) * 2f
        val jy2 = if (i == 0 || i == n) 0f else h * 0.035f * (rng.next() - 0.5f) * 2f
        rx[i] = x + w + jx2; ry[i] = peakY + h * f + jy2
    }
    val peakX = x + half * 0.04f * (rng.next() - 0.5f)
    // ridge line down the middle (zig-zag) splits lit / shadow faces
    val gx = FloatArray(n + 1); val gy = FloatArray(n + 1)
    for (i in 0..n) { gx[i] = x + half * 0.10f * (rng.next() - 0.5f) * 2f * (i / n.toFloat()); gy[i] = peakY + h * (i / n.toFloat()) }

    val lit = Path().apply {
        moveTo(peakX, peakY)
        for (i in 1..n) lineTo(lx[i], ly[i])
        lineTo(gx[n], baseY)
        for (i in n - 1 downTo 1) lineTo(gx[i], gy[i])
        close()
    }
    val shade = Path().apply {
        moveTo(peakX, peakY)
        for (i in 1..n) lineTo(rx[i], ry[i])
        lineTo(gx[n], baseY)
        for (i in n - 1 downTo 1) lineTo(gx[i], gy[i])
        close()
    }
    val litTop = if (volcano) Color(0xFF7C5A66) else Color(0xFF8E83A8)
    val litBot = if (volcano) Color(0xFF34242E) else Color(0xFF3B3350)
    drawPath(lit, Brush.verticalGradient(listOf(litTop, litBot), startY = peakY, endY = baseY))
    drawPath(shade, Brush.verticalGradient(listOf(Color(0xFF3A2C36), Color(0xFF150F16)), startY = peakY, endY = baseY))

    // cracks / strata
    for (c in 0 until 4) {
        val i = 1 + (rng.next() * (n - 2)).toInt()
        val onLit = c % 2 == 0
        val sx = if (onLit) (lx[i] + gx[i]) / 2f else (rx[i] + gx[i]) / 2f
        val sy = (if (onLit) ly[i] else ry[i]) + h * 0.02f
        drawLine(Color.Black.copy(alpha = 0.35f), Offset(sx, sy), Offset(sx + (rng.next() - 0.3f) * half * 0.35f, sy + h * 0.10f), strokeWidth = 2f)
    }
    // warm rim light on the lit edge
    val rim = Path().apply { moveTo(peakX, peakY); for (i in 1..n) lineTo(lx[i], ly[i]) }
    drawPath(rim, Color(0xFFFFB27A).copy(alpha = if (volcano) 0.55f else 0.40f), style = Stroke(width = 2.2f, cap = StrokeCap.Round))

    if (volcano) {
        val pulse = 0.5f + 0.5f * sin(time * 5f + seed)
        // glowing crater
        drawCircle(Brush.radialGradient(listOf(Color(0xFFFFE08A).copy(alpha = 0.9f), Color(0xFFFF6A00).copy(alpha = 0.55f), Color(0x00FF3B00)),
            center = Offset(peakX, peakY + h * 0.03f), radius = half * (0.75f + 0.15f * pulse)), half * (0.75f + 0.15f * pulse), Offset(peakX, peakY + h * 0.03f), blendMode = BlendMode.Plus)
        // lava veins with a flowing highlight
        for (v in 0 until 2) {
            val side = if (v == 0) -1f else 1f
            val vein = Path().apply {
                moveTo(peakX + side * half * 0.04f, peakY + h * 0.04f)
                var px = peakX; var py = peakY
                for (i in 1..4) {
                    px = x + side * half * (0.10f + 0.16f * i) * (0.8f + 0.4f * rng.next())
                    py = peakY + h * (0.14f * i + 0.05f)
                    lineTo(px, py)
                }
            }
            drawPath(vein, Color(0xFF8A1E08).copy(alpha = 0.85f), style = Stroke(width = 3.2f, cap = StrokeCap.Round))
            drawPath(vein, Color(0xFFFFB347).copy(alpha = 0.55f + 0.4f * pulse), blendMode = BlendMode.Plus,
                style = Stroke(width = 2.0f, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 30f), -time * 70f)))
        }
        if (!flipped) {
            // smoke plume
            for (i in 0 until 5) {
                val p = (time * 0.30f + i * 0.2f) % 1f
                val sx = peakX + sin(p * 6f + i * 1.7f) * half * 0.18f + p * half * 0.55f
                val sy = peakY - p * ch * 0.20f
                drawCircle(Color(0xFF5A4A52).copy(alpha = (1f - p) * 0.38f), half * (0.16f + 0.40f * p), Offset(sx, sy))
            }
            // embers thrown from the crater
            for (i in 0 until 4) {
                val p = (time * 0.9f + i * 0.25f) % 1f
                val ex = peakX + (i - 1.5f) * half * 0.30f * p * 2f
                val ey = peakY - sin(p * PI.toFloat()) * ch * 0.14f + p * p * ch * 0.05f
                drawCircle(Color(0xFFFFB347).copy(alpha = (1f - p) * 0.95f), 2.6f * (1f - p * 0.5f), Offset(ex, ey), blendMode = BlendMode.Plus)
            }
        } else {
            // hanging rock: lava drips fall from the tip (drawn flipped, so "up" here is down on screen)
            for (i in 0 until 3) {
                val p = (time * 0.75f + i * 0.33f) % 1f
                val dy = p * p * ch * 0.28f
                val dx = peakX + (i - 1) * half * 0.10f
                drawLine(Color(0xFFFF8A00).copy(alpha = (1f - p) * 0.55f), Offset(dx, peakY - dy), Offset(dx, peakY - dy + ch * 0.03f * p), strokeWidth = 2.4f)
                drawCircle(Color(0xFFFFC04D).copy(alpha = 1f - p * 0.7f), 3.6f * (1f - p * 0.4f), Offset(dx, peakY - dy), blendMode = BlendMode.Plus)
            }
        }
    } else {
        // snow cap: jagged lower edge, lit side brighter
        val cap = Path().apply {
            moveTo(peakX, peakY)
            lineTo(rx[1], ry[1]); lineTo((rx[1] + peakX) / 2f + half * 0.04f, ry[1] - h * 0.04f)
            lineTo(peakX + half * 0.05f, ry[1] + h * 0.03f); lineTo(peakX - half * 0.10f, ry[1] - h * 0.02f)
            lineTo((lx[1] + peakX) / 2f, ly[1] + h * 0.03f); lineTo(lx[1], ly[1])
            close()
        }
        drawPath(cap, Brush.verticalGradient(listOf(Color.White, Color(0xFFCFD8F5)), startY = peakY, endY = ly[1]))
        drawCircle(Color.White.copy(alpha = 0.25f + 0.1f * sin(time * 3f)), half * 0.25f, Offset(peakX, peakY + h * 0.05f), blendMode = BlendMode.Plus)
        if (!flipped) for (i in 0 until 3) {   // wind-blown snow
            val p = (time * 0.5f + i * 0.33f) % 1f
            drawCircle(Color.White.copy(alpha = (1f - p) * 0.6f), 2f, Offset(peakX + p * half * 1.4f, peakY + h * 0.05f + sin(p * 9f + i) * h * 0.03f))
        }
    }
    // low mist / glow at the foot
    drawRect(Brush.verticalGradient(listOf(Color.Transparent, (if (volcano) Color(0xFFFF5A1F) else Color(0xFF8FB4FF)).copy(alpha = 0.30f)), startY = baseY - h * 0.16f, endY = baseY),
        Offset(x - half * 1.3f, baseY - h * 0.16f), Size(half * 2.6f, h * 0.16f))
}

/**
 * Incoming-threat warning, kept small so it never hides the track: a pulsing icon with a shrinking countdown ring and a ripple at the
 * edge where the threat will arrive, a thin edge marker (5 px) over the lanes it will fill, and a faint dashed hairline to the pilot.
 * id = wrn_<tick>_<n>_<kind>_<delayTicks>
 */
private fun DrawScope.drawWarn(elem: VisualTrackElement, y: Float, cw: Float, ch: Float, tick: Int, time: Float) {
    val parts = elem.id.split("_")
    val tag = parts.getOrNull(3) ?: "x"
    val delay = (parts.getOrNull(4)?.toIntOrNull() ?: 5).coerceAtLeast(1)
    val age = (tick - (parts.getOrNull(1)?.toIntOrNull() ?: tick)).coerceAtLeast(0)
    val prog = (age / delay.toFloat()).coerceIn(0f, 1f)
    val hot = tag == "fire" || tag == "lava" || tag == "mtnb" || tag == "mtnt" || tag == "ridges"
    val c = if (hot) Color(0xFFFF4A1C) else Color(0xFFFF2BD6)
    val pulse = 0.5f + 0.5f * sin(time * 11f)

    // 1) thin edge marker over the lanes that will be hit (no wide band, no gradient)
    fun marker(top: Float, bottom: Float) {
        if (bottom - top < 2f) return
        drawRect(c.copy(alpha = 0.30f + 0.35f * pulse), Offset(cw - 5f, top), Size(5f, bottom - top))
    }
    when (tag) {
        "fire" -> marker((y - ch * 0.58f).coerceAtLeast(0f), (y + ch * 0.58f).coerceAtMost(ch))
        "lava" -> marker((y - ch * 0.24f).coerceAtLeast(0f), (y + ch * 0.24f).coerceAtMost(ch))
        "mtnb" -> marker((y - ch * 0.12f).coerceAtLeast(0f), ch)
        "mtnt" -> marker(0f, (y + ch * 0.12f).coerceAtMost(ch))
        "ridges" -> { marker(0f, ch * 0.34f); marker(ch * 0.66f, ch) }
        else -> marker((y - ch * 0.07f).coerceAtLeast(0f), (y + ch * 0.07f).coerceAtMost(ch))
    }

    // 2) faint dashed lane hairline from the icon toward the pilot
    drawLine(c.copy(alpha = 0.10f + 0.10f * pulse), Offset(cw * 0.92f, y), Offset(cw * 0.30f, y), strokeWidth = 1.5f,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 16f), time * 40f))

    // two tiny chevrons right beside the icon
    val s = ch * 0.018f
    for (i in 0 until 2) {
        val p = (time * 1.6f + i / 2f) % 1f
        val cx = cw * 0.915f - p * cw * 0.05f
        drawPath(Path().apply { moveTo(cx + s, y - s * 1.3f); lineTo(cx, y); lineTo(cx + s, y + s * 1.3f) }, c.copy(alpha = (1f - p) * 0.8f),
            style = Stroke(width = 3f, cap = StrokeCap.Round))
    }

    // 3) icon, ripple and countdown ring
    val ix = cw * 0.945f
    val r = ch * 0.045f * (1f + 0.10f * pulse)
    val rp = (time * 1.3f) % 1f
    drawCircle(c.copy(alpha = (1f - rp) * 0.5f), r * (1.2f + 1.5f * rp), Offset(ix, y), style = Stroke(width = 2.5f))
    drawCircle(Color(0xE6120508), r * 1.18f, Offset(ix, y))
    drawArc(c, -90f, 360f * (1f - prog), false, Offset(ix - r * 1.18f, y - r * 1.18f), Size(r * 2.36f, r * 2.36f),
        style = Stroke(width = r * 0.24f, cap = StrokeCap.Round))
    val g = r * 0.62f
    when (tag) {
        "fire", "lava" -> {
            drawPath(Path().apply {
                moveTo(ix, y - g); cubicTo(ix + g * 0.95f, y - g * 0.2f, ix + g * 0.65f, y + g * 0.95f, ix, y + g * 0.95f)
                cubicTo(ix - g * 0.65f, y + g * 0.95f, ix - g * 0.95f, y - g * 0.2f, ix, y - g); close() }, Color(0xFFFF9A1F))
            drawCircle(Color(0xFFFFEC8A), g * 0.32f, Offset(ix, y + g * 0.35f))
        }
        "mtnb", "mtnt", "ridges" -> {
            drawPath(Path().apply { moveTo(ix - g, y + g * 0.8f); lineTo(ix - g * 0.25f, y - g * 0.7f); lineTo(ix + g * 0.25f, y + g * 0.1f)
                lineTo(ix + g * 0.6f, y - g * 0.3f); lineTo(ix + g, y + g * 0.8f); close() }, Color(0xFFFFB27A))
        }
        else -> {
            drawRect(Color.White, Offset(ix - g * 0.14f, y - g * 0.75f), Size(g * 0.28f, g * 1.0f))
            drawCircle(Color.White, g * 0.16f, Offset(ix, y + g * 0.6f))
        }
    }
}
