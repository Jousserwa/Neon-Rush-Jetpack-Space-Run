package com.neonrush.game.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.neonrush.game.EliteBoss
import com.neonrush.game.EliteBosses
import com.neonrush.game.EliteMove
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Drawing for the zone 60-240 elite bosses. Same eight sprites as the world bosses, made new with a colour grade, a size, an aura of
 * their own, and the way they move (sweeping, orbiting, lunging at you, teleporting). Names are prefixed "eb" to stay unique.
 */
private const val EB_TAU = 6.2831855f
private val ebFilters = HashMap<Int, ColorFilter>()

private fun ebFilter(hueDeg: Float, bright: Float): ColorFilter {
    val key = (hueDeg * 2f).roundToInt() * 1000 + (bright * 100f).roundToInt()
    return ebFilters.getOrPut(key) {
        val r = Math.toRadians(hueDeg.toDouble())
        val c = cos(r).toFloat(); val s = sin(r).toFloat()
        val lr = 0.213f; val lg = 0.715f; val lb = 0.072f
        val b = bright
        ColorFilter.colorMatrix(ColorMatrix(floatArrayOf(
            (lr + c * (1 - lr) - s * lr) * b, (lg - c * lg - s * lg) * b, (lb - c * lb + s * (1 - lb)) * b, 0f, 0f,
            (lr - c * lr + s * 0.143f) * b, (lg + c * (1 - lg) + s * 0.140f) * b, (lb - c * lb - s * 0.283f) * b, 0f, 0f,
            (lr - c * lr - s * (1 - lr)) * b, (lg - c * lg + s * lg) * b, (lb + c * (1 - lb) + s * lb) * b, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )))
    }
}

private fun ebCol(e: EliteBoss): Color = Color(e.accent)

/**
 * Draws the whole elite boss: aura behind, the graded body (and a twin when it has one), moves like a lunge or a teleport flash, then the
 * front effects. [bossX]/[bossYPx] = where the boss sits, [bossYPct] = its lane 0..100 (the twin takes the opposite lane).
 */
fun DrawScope.drawEliteBoss(
    e: EliteBoss, img: ImageBitmap, bossX: Float, bossYPx: Float, bossYPct: Int,
    h: Float, t: Float, tick: Int, hp: Float, cw: Float, ch: Float
) {
    val bodies = if (e.twin) 2 else 1
    for (b in 0 until bodies) {
        val py = if (b == 0) bossYPx else ch * (100 - bossYPct) / 100f
        var px = bossX
        // how this boss moves on screen
        when (e.move) {
            EliteMove.DASH -> px -= cw * 0.34f * EliteBosses.dashLunge(tick)
            EliteMove.ORBIT -> px += cos(tick * 0.08f + b * 3.14f) * cw * 0.05f
            EliteMove.STRAFE -> px += sin(t * 5f) * cw * 0.012f
            else -> {}
        }
        val hh = h * e.scale * (if (e.twin) 1f else 1f)
        val hueB = e.hue + b * 70f
        drawEliteOne(e, img, Offset(px, py), hh, t, tick, hp, hueB, cw, b)
    }
}

private fun DrawScope.drawEliteOne(e: EliteBoss, img: ImageBitmap, c: Offset, h: Float, t: Float, tick: Int, hp: Float, hue: Float, cw: Float, bodyNo: Int) {
    val col = ebCol(e)
    val w = h * (img.width.toFloat() / img.height.toFloat())
    val pose = bossPose(e.base, h, t, hp)
    val filter = ebFilter(hue, e.bright)

    // ---- aura behind
    fxGlow(c, h * 0.95f, col, 0.20f + 0.08f * sin(t * 2.4f))
    ebBack(e, c, h, t, tick, col)

    // ---- afterimages of a lunge or a teleport
    if (e.move == EliteMove.DASH) {
        val l = EliteBosses.dashLunge(tick)
        if (l > 0.15f) for (k in 1..3) ebSprite(img, Offset(c.x + cw * 0.09f * k * l, c.y), w, h, pose, filter, 0.22f / k, 1f)
    }
    if (e.move == EliteMove.HOP) {
        val ph = tick % 30
        if (ph < 7) {
            val f = ph / 7f
            fxGlow(c, h * (0.4f + 0.8f * f), Color.White, 0.6f * (1f - f))
            drawCircle(col.copy(alpha = 0.8f * (1f - f)), radius = h * (0.2f + 0.7f * f), center = c, style = Stroke(width = h * 0.025f))
        }
    }
    // glitch / RGB split for the glitch auras
    if (e.fx == 3) {
        val sp = (if ((t * 12f).toInt() % 5 == 0) 0.03f else 0.008f) * h
        ebSprite(img, Offset(c.x - sp, c.y), w, h, pose, ColorFilter.tint(Color(0xFFFF1744), BlendMode.SrcAtop), 0.35f, 1f)
        ebSprite(img, Offset(c.x + sp, c.y), w, h, pose, ColorFilter.tint(Color(0xFF00E5FF), BlendMode.SrcAtop), 0.35f, 1f)
    }

    // ---- the body
    val flick = 0.92f + 0.08f * sin(tick * 0.5f)
    ebSprite(img, c, w, h, pose, filter, flick, 1f)

    // ---- front effects
    ebFront(e, c, h, t, tick, col, bodyNo)

    // enraged: red pulse, shaking
    if (hp < 0.4f) {
        val p = 0.5f + 0.5f * sin(t * 10f)
        fxGlow(c, h * 0.9f, Color(0xFFFF1744), 0.12f + 0.16f * p)
    }
}

private fun DrawScope.ebSprite(img: ImageBitmap, c: Offset, w: Float, h: Float, pose: BxPose, filter: ColorFilter, alpha: Float, mul: Float) {
    withTransform({
        translate(pose.dx, pose.dy)
        rotate(pose.rot, c)
        scale(pose.sx * mul, pose.sy * mul, c)
    }) {
        drawImage(
            image = img,
            dstOffset = IntOffset((c.x - w / 2f).roundToInt(), (c.y - h / 2f).roundToInt()),
            dstSize = IntSize(w.roundToInt(), h.roundToInt()),
            alpha = alpha.coerceIn(0f, 1f),
            colorFilter = filter
        )
    }
}

// ------------------------------------------------------------------------------------------------ auras (behind)

private fun DrawScope.ebBack(e: EliteBoss, c: Offset, h: Float, t: Float, tick: Int, col: Color) {
    when (e.fx) {
        1 -> { // SOLAR: rotating rays
            for (k in 0 until 10) {
                val a = t * 0.4f + k * (EB_TAU / 10f)
                val len = h * (0.85f + 0.12f * sin(t * 3f + k))
                drawLine(col.copy(alpha = 0.16f), c, Offset(c.x + cos(a) * len, c.y + sin(a) * len), strokeWidth = h * 0.045f, cap = StrokeCap.Round, blendMode = BlendMode.Plus)
            }
            for (k in 0 until 2) {
                val f = ((t / 1.8f) + k * 0.5f) % 1f
                drawCircle(col.copy(alpha = (1f - f) * 0.5f), radius = h * (0.3f + 0.7f * f), center = c, style = Stroke(width = h * 0.02f))
            }
        }
        4 -> { // WARDEN: spinning barrier arcs
            for (k in 0 until 3) {
                val r = h * 0.64f
                drawArc(col.copy(alpha = 0.7f), startAngle = t * 60f + k * 120f, sweepAngle = 70f, useCenter = false,
                    topLeft = Offset(c.x - r, c.y - r), size = Size(r * 2f, r * 2f), style = Stroke(width = h * 0.022f, cap = StrokeCap.Round))
            }
            for (k in 0 until 2) {
                val r = h * 0.52f
                drawArc(Color.White.copy(alpha = 0.4f), startAngle = -t * 90f + k * 180f, sweepAngle = 40f, useCenter = false,
                    topLeft = Offset(c.x - r, c.y - r), size = Size(r * 2f, r * 2f), style = Stroke(width = h * 0.014f, cap = StrokeCap.Round))
            }
        }
        6 -> { // ECLIPSE: a hole that drinks light
            drawCircle(Brush.radialGradient(listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent), center = c, radius = h * 0.8f), h * 0.8f, c)
            for (k in 0 until 12) {
                val ph = (t * 0.45f + k * 0.083f) % 1f
                val a = k * 0.5236f + t * 0.8f
                val r = h * 0.85f * (1f - ph)
                fxGlow(Offset(c.x + cos(a) * r, c.y + sin(a) * r * 0.9f), h * 0.05f, col, ph)
            }
        }
        7 -> { // APEX: a golden halo and long god-rays
            for (k in 0 until 7) {
                val a = -1.57f + (k - 3) * 0.28f + sin(t * 0.6f) * 0.08f
                drawLine(col.copy(alpha = 0.14f), Offset(c.x, c.y - h * 0.1f), Offset(c.x + cos(a) * h * 1.2f, c.y + sin(a) * h * 1.2f), strokeWidth = h * 0.06f, blendMode = BlendMode.Plus)
            }
        }
        else -> {}
    }
}

// ------------------------------------------------------------------------------------------------ effects (front)

private fun DrawScope.ebFront(e: EliteBoss, c: Offset, h: Float, t: Float, tick: Int, col: Color, bodyNo: Int) {
    when (e.fx) {
        0 -> { // FROST: shards orbit, snow falls
            for (k in 0 until 7) {
                val a = t * 0.8f + k * (EB_TAU / 7f) + bodyNo
                val p = Offset(c.x + cos(a) * h * 0.36f, c.y + sin(a) * h * 0.5f)
                fxShard(p, h * 0.045f, t * 2f + k, Color(0xFFE1F5FE), 0.9f)
            }
            for (k in 0 until 8) {
                val ph = (t * 0.3f + k * 0.125f) % 1f
                fxGlow(Offset(c.x - h * 0.4f + fxRnd(k, 4 + bodyNo) * h * 0.8f, c.y - h * 0.5f + ph * h), h * 0.03f, Color.White, 1f - ph)
            }
        }
        1 -> { // SOLAR: flares off the shoulders
            for (k in 0 until 3) {
                val ph = (t * 0.7f + k * 0.33f) % 1f
                fxSpark(Offset(c.x - h * 0.2f + sin(t + k) * h * 0.2f, c.y - h * 0.45f - ph * h * 0.4f), h * 0.07f * (1f - ph), col, 1f - ph)
            }
        }
        2 -> { // TOXIC: bubbles rising off the body
            for (k in 0 until 9) {
                val ph = (t * 0.35f + k * 0.111f) % 1f
                val p = Offset(c.x - h * 0.3f + fxRnd(k, 2 + bodyNo) * h * 0.6f + sin(t * 2f + k) * h * 0.03f, c.y + h * 0.45f - ph * h * 0.95f)
                drawCircle(col.copy(alpha = 0.7f * (1f - ph)), radius = h * 0.03f * (0.6f + fxRnd(k, 9)), center = p, style = Stroke(width = h * 0.008f))
                fxGlow(p, h * 0.025f, col, 0.4f * (1f - ph))
            }
        }
        3 -> { // GLITCH: torn slices and error blocks
            val step = (t * 10f).toInt()
            for (k in 0 until 5) if (fxRnd(step, k) > 0.45f) {
                val y = c.y - h * 0.45f + fxRnd(step, k + 7) * h * 0.9f
                val x = c.x - h * 0.35f + fxRnd(step + 3, k) * h * 0.3f
                drawRect(col.copy(alpha = 0.55f), Offset(x, y), Size(h * (0.15f + 0.3f * fxRnd(k, step)), h * 0.014f), blendMode = BlendMode.Plus)
            }
            for (k in 0 until 3) if (fxRnd(step + 9, k) > 0.5f)
                drawRect(Color.White.copy(alpha = 0.45f), Offset(c.x - h * 0.3f + fxRnd(k, step) * h * 0.6f, c.y - h * 0.4f + fxRnd(step, k + 2) * h * 0.8f), Size(h * 0.04f, h * 0.025f))
        }
        4 -> { // WARDEN: chain sparks along the shield
            for (k in 0 until 3) {
                val ph = (t * 1.3f + k * 0.33f) % 1f
                if (ph < 0.3f) fxSpark(Offset(c.x + h * 0.28f, c.y - h * 0.3f + k * h * 0.3f), h * 0.05f, Color(0xFFFFCDD2), 1f - ph * 3f)
            }
        }
        5 -> { // AFTERBURN: speed lines and muzzle flashes
            for (k in 0 until 7) {
                val ph = (t * 1.8f + k * 0.14f) % 1f
                val y = c.y - h * 0.4f + fxRnd(k, 12) * h * 0.8f
                val x = c.x + h * 0.4f - ph * h * 1.2f
                drawLine(col.copy(alpha = 0.5f * (1f - ph)), Offset(x, y), Offset(x + h * 0.25f, y), strokeWidth = h * 0.01f, cap = StrokeCap.Round, blendMode = BlendMode.Plus)
            }
            val fl = (sin(t * 9f) - 0.6f).coerceAtLeast(0f) * 2.5f
            if (fl > 0f) fxGlow(Offset(c.x - h * 0.34f, c.y + h * 0.05f), h * 0.18f * fl, Color(0xFFFFE082), 0.8f)
        }
        6 -> { // ECLIPSE: dark wisps shedding off
            for (k in 0 until 6) {
                val ph = (t * 0.5f + k * 0.17f) % 1f
                drawCircle(Color.Black.copy(alpha = 0.5f * (1f - ph)), radius = h * 0.06f * (1f + ph), center = Offset(c.x + h * 0.35f * ph, c.y - h * 0.4f + fxRnd(k, 1) * h * 0.8f))
            }
        }
        7 -> { // APEX: halo ring and orbiting blades
            val hy = c.y - h * 0.58f
            drawOval(col.copy(alpha = 0.85f), Offset(c.x - h * 0.3f, hy - h * 0.06f), Size(h * 0.6f, h * 0.12f), style = Stroke(width = h * 0.018f))
            for (k in 0 until 4) {
                val a = t * 1.2f + k * 1.5708f
                val p = Offset(c.x + cos(a) * h * 0.5f, c.y + sin(a) * h * 0.55f)
                fxShard(p, h * 0.07f, a + 1.5708f, col, 0.95f)
            }
        }
        else -> {}
    }
}
