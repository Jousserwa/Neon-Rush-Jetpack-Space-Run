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
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Living bosses, boss shots and blink creatures. Everything is drawn with code on top of the existing art and runs on the smooth
 * seconds clock [t] (fxClock), so it animates between game ticks.
 *  - Each of the 8 bosses has its own aura, its own way of moving and its own overlay, and they get angrier below 30% health.
 *  - Each of the 8 worlds fires a different shot (dark orb, sonar pulse, shackle bolt, spore, glitch dart, shard trio, ice spear, comet).
 *  - Each of the 5 blink creatures (wolf, raptor, hornet, arachnid, wraith) moves its own way, leaves its own trail, materialises with a
 *    flash, dissolves into particles, and shows a gathering warning just before it blinks back in.
 * Names are prefixed "bx" so they cannot clash with other private helpers in this package.
 */

private const val BTAU = 6.2831855f

/** Extra shift / tilt / squash applied to a sprite while it is drawn. */
class BxPose(val dx: Float, val dy: Float, val rot: Float, val sx: Float, val sy: Float)

private fun bxCol(world: Int): Color = when (world) {
    1 -> Color(0xFF9B59FF)
    2 -> Color(0xFF3DFFB0)
    3 -> Color(0xFF29B6FF)
    4 -> Color(0xFF7CFF3A)
    5 -> Color(0xFFFF2B4D)
    6 -> Color(0xFFFF2BD6)
    7 -> Color(0xFF9FE8FF)
    else -> Color(0xFFFFD54F)
}

// ================================================================================================================ boss body

fun bossPose(world: Int, h: Float, t: Float, hp: Float): BxPose {
    var dx = if (hp < 0.3f) sin(t * 47f) * h * 0.008f else 0f
    var dy = sin(t * 1.6f) * h * 0.03f
    var rot = 0f
    var sx = 1f + 0.025f * sin(t * 2.2f)
    var sy = 1f - 0.025f * sin(t * 2.2f)
    when (world) {
        1 -> { dy = sin(t * 1.1f) * h * 0.04f; rot = sin(t * 0.9f) * 2f }
        2 -> { rot = sin(t * 0.7f) * 4f; dx += sin(t * 0.5f) * h * 0.02f }
        3 -> { dy = sin(t * 2.4f) * h * 0.012f; sx = 1f + 0.012f * sin(t * 2.4f); sy = 1f }
        4 -> { sx = 1f + 0.05f * sin(t * 2.6f); sy = 1f + 0.05f * sin(t * 2.6f + 1.5f); rot = sin(t * 1.3f) * 2.5f }
        5 -> { if ((t * 8f).toInt() % 11 == 0) dx += h * 0.02f; dy = sin(t * 3f) * h * 0.012f }
        6 -> { dx += sin(t * 13f) * h * 0.006f; dy = sin(t * 1.4f) * h * 0.03f; rot = sin(t * 9f) * 0.8f }
        7 -> { dy = sin(t * 0.8f) * h * 0.045f; rot = sin(t * 0.6f) * 2f }
        8 -> { dy = sin(t * 1.2f) * h * 0.035f; sx = 1f + 0.04f * sin(t * 1.9f); sy = sx }
    }
    return BxPose(dx, dy, rot, sx, sy)
}

/** Behind the boss sprite. [c] = boss centre, [h] = boss height. */
fun DrawScope.drawBossBack(world: Int, c: Offset, h: Float, t: Float, hp: Float) {
    val col = bxCol(world)
    if (hp < 0.3f) {
        val p = 0.5f + 0.5f * sin(t * 9f)
        fxGlow(c, h * 0.95f, Color(0xFFFF1744), 0.18f + 0.16f * p)
    }
    when (world) {
        1 -> { // BLACKOUT: a hole in the light, tendrils of dark, lights that die and flicker
            drawCircle(Brush.radialGradient(listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent), center = c, radius = h * 0.85f), h * 0.85f, c)
            for (k in 0 until 6) {
                val a0 = k * (BTAU / 6f) + t * 0.5f
                val path = Path().apply {
                    moveTo(c.x, c.y)
                    for (i in 1..8) {
                        val f = i / 8f
                        val r = h * (0.25f + 0.55f * f)
                        val a = a0 + sin(t * 1.6f + k + f * 4f) * 0.35f * f
                        lineTo(c.x + cos(a) * r, c.y + sin(a) * r * 0.9f)
                    }
                }
                drawPath(path, Color.Black.copy(alpha = 0.55f), style = Stroke(width = h * 0.05f, cap = StrokeCap.Round))
                drawPath(path, col.copy(alpha = 0.3f), style = Stroke(width = h * 0.012f, cap = StrokeCap.Round))
            }
            val step = (t * 9f).toInt()
            for (k in 0 until 7) if (fxRnd(step, k) > 0.45f) {
                val a = fxRnd(k, 3) * BTAU
                fxGlow(Offset(c.x + cos(a) * h * 0.5f, c.y + sin(a) * h * 0.42f), h * 0.07f, Color.White, 0.8f)
            }
        }
        2 -> { // DERELICT SIGNAL: sonar rings and a radar sweep
            for (k in 0 until 3) {
                val f = ((t / 2.4f) + k / 3f) % 1f
                drawCircle(col.copy(alpha = (1f - f) * 0.7f), radius = h * (0.3f + 0.75f * f), center = c, style = Stroke(width = h * 0.018f * (1f - f)))
            }
            val ang = t * 2.2f
            val tip = Offset(c.x + cos(ang) * h * 0.75f, c.y + sin(ang) * h * 0.75f)
            for (k in 1..6) {
                val a = ang - k * 0.12f
                drawLine(col.copy(alpha = 0.3f * (1f - k / 7f)), c, Offset(c.x + cos(a) * h * 0.75f, c.y + sin(a) * h * 0.75f), strokeWidth = h * 0.03f)
            }
            drawLine(col.copy(alpha = 0.9f), c, tip, strokeWidth = h * 0.012f, cap = StrokeCap.Round)
            for (k in 0 until 4) {
                val a = fxRnd(k, 8) * BTAU
                val blip = ((t * 0.7f + fxRnd(k, 9)) % 1f)
                fxGlow(Offset(c.x + cos(a) * h * 0.55f, c.y + sin(a) * h * 0.55f), h * 0.05f, col, (1f - blip) * 0.9f)
            }
        }
        3 -> { // CELL BLOCK ZERO: laser bars of a cage and a spinning lock ring with a travelling spark
            for (k in -3..3) {
                val bx = c.x + k * h * 0.15f
                val on = if (fxRnd((t * 6f).toInt(), k + 9) > 0.12f) 1f else 0.2f
                drawLine(col.copy(alpha = 0.5f * on), Offset(bx, c.y - h * 0.62f), Offset(bx, c.y + h * 0.62f), strokeWidth = h * 0.012f)
            }
            rotate(t * 25f, c) {
                drawCircle(col.copy(alpha = 0.7f), radius = h * 0.66f, center = c,
                    style = Stroke(width = h * 0.02f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(h * 0.12f, h * 0.07f))))
            }
            val sa = -t * 3.2f
            fxSpark(Offset(c.x + cos(sa) * h * 0.66f, c.y + sin(sa) * h * 0.66f), h * 0.06f, Color.White, 1f)
        }
        4 -> { // GREEN HELL: swaying vines and drifting spores
            for (k in 0 until 6) {
                val bx = c.x - h * 0.3f + k * h * 0.12f
                val path = Path().apply {
                    moveTo(bx, c.y + h * 0.4f)
                    for (i in 1..6) {
                        val f = i / 6f
                        lineTo(bx + sin(t * 1.7f + k + f * 3f) * h * 0.12f * f, c.y + h * 0.4f + f * h * 0.35f)
                    }
                }
                drawPath(path, Color(0xFF2E7D32).copy(alpha = 0.75f), style = Stroke(width = h * 0.035f, cap = StrokeCap.Round))
                drawPath(path, col.copy(alpha = 0.5f), style = Stroke(width = h * 0.01f, cap = StrokeCap.Round))
            }
            for (k in 0 until 14) {
                val f = (t * 0.35f + fxRnd(k, 1)) % 1f
                val px = c.x + (fxRnd(k, 2) - 0.5f) * h * 1.1f + sin(t * 1.5f + k) * h * 0.05f
                val py = c.y + h * 0.5f - f * h * 1.1f
                fxGlow(Offset(px, py), h * 0.045f, col, 0.7f * sin(f * PI.toFloat()))
            }
        }
        5 -> { // RED PROTOCOL: spinning warning hexagons and a scan bar
            for (k in 0 until 2) {
                val rr = h * (0.45f + 0.2f * k)
                val rot = t * (if (k == 0) 0.9f else -0.6f)
                val hex = Path().apply {
                    for (i in 0 until 6) {
                        val a = rot + i * (BTAU / 6f)
                        if (i == 0) moveTo(c.x + cos(a) * rr, c.y + sin(a) * rr) else lineTo(c.x + cos(a) * rr, c.y + sin(a) * rr)
                    }
                    close()
                }
                drawPath(hex, col.copy(alpha = 0.55f - 0.15f * k), style = Stroke(width = h * 0.015f))
            }
            val sy = c.y - h * 0.6f + ((t * 0.9f) % 1f) * h * 1.2f
            drawRect(col.copy(alpha = 0.22f), Offset(c.x - h * 0.7f, sy), Size(h * 1.4f, h * 0.05f), blendMode = BlendMode.Plus)
            if ((t * 4f).toInt() % 3 == 0) fxSpark(Offset(c.x - h * 0.55f, c.y - h * 0.5f), h * 0.05f, Color.White, 0.9f)
        }
        6 -> { // SIGNAL FRACTURE: the picture is cracking - RGB split, cracks and floating shards
            fxGlow(Offset(c.x - h * 0.05f, c.y), h * 0.6f, Color(0xFF00E5FF), 0.18f)
            fxGlow(Offset(c.x + h * 0.05f, c.y), h * 0.6f, Color(0xFFFF2BD6), 0.18f)
            val step = (t * 6f).toInt()
            for (k in 0 until 7) {
                val a = k * (BTAU / 7f) + fxRnd(step / 3, k) * 0.2f
                fxBolt(Offset(c.x + cos(a) * h * 0.3f, c.y + sin(a) * h * 0.3f), Offset(c.x + cos(a) * h * 0.82f, c.y + sin(a) * h * 0.82f), k * 31 + step / 3, Color.White, h * 0.008f, 0.7f, 0.08f)
            }
            for (k in 0 until 6) {
                val a = t * 0.7f + k * (BTAU / 6f)
                fxShard(Offset(c.x + cos(a) * h * 0.62f, c.y + sin(a) * h * 0.55f), h * 0.07f, a * 2f + t * 3f, if (k % 2 == 0) Color(0xFF00E5FF) else Color(0xFFFF2BD6), 0.85f)
            }
        }
        7 -> { // FROZEN VEIL: frost ring, orbiting snowflakes and falling snow
            fxGlow(c, h * 0.8f, Color(0xFF80D8FF), 0.22f)
            drawCircle(col.copy(alpha = 0.5f), radius = h * 0.62f, center = c, style = Stroke(width = h * 0.015f))
            for (k in 0 until 5) {
                val a = t * 0.5f + k * (BTAU / 5f)
                val p = Offset(c.x + cos(a) * h * 0.62f, c.y + sin(a) * h * 0.62f)
                for (arm in 0 until 3) {
                    val aa = a + arm * (PI.toFloat() / 3f) + t
                    drawLine(Color.White.copy(alpha = 0.85f), Offset(p.x - cos(aa) * h * 0.06f, p.y - sin(aa) * h * 0.06f), Offset(p.x + cos(aa) * h * 0.06f, p.y + sin(aa) * h * 0.06f), strokeWidth = h * 0.012f, cap = StrokeCap.Round)
                }
            }
            for (k in 0 until 16) {
                val f = (t * 0.3f + fxRnd(k, 4)) % 1f
                drawCircle(Color.White.copy(alpha = 0.7f), radius = h * 0.012f, center = Offset(c.x + (fxRnd(k, 5) - 0.5f) * h * 1.3f + sin(t + k) * h * 0.03f, c.y - h * 0.6f + f * h * 1.2f))
            }
        }
        else -> { // APEX SIGNAL: a golden crown of rays, two counter-turning rings and lightning
            fxGlow(c, h * 0.95f, Color(0xFFFFB300), 0.25f)
            for (k in 0 until 12) {
                val a = t * 0.35f + k * (BTAU / 12f)
                val l = h * (0.7f + 0.08f * sin(t * 3f + k))
                drawLine(col.copy(alpha = 0.5f), Offset(c.x + cos(a) * h * 0.5f, c.y + sin(a) * h * 0.5f), Offset(c.x + cos(a) * l, c.y + sin(a) * l), strokeWidth = h * 0.016f, cap = StrokeCap.Round)
            }
            for (k in 0 until 10) {
                val a = -t * 0.8f + k * (BTAU / 10f)
                fxGlow(Offset(c.x + cos(a) * h * 0.78f, c.y + sin(a) * h * 0.78f), h * 0.05f, Color.White, 0.8f)
            }
            val bolt = (t * 0.9f).toInt()
            val ph = (t * 0.9f) % 1f
            if (ph < 0.25f) {
                val a = fxRnd(bolt, 6) * BTAU
                fxBolt(c, Offset(c.x + cos(a) * h * 0.95f, c.y + sin(a) * h * 0.95f), bolt * 17 + (t * 30f).toInt(), Color.White, h * 0.012f, 1f - ph * 4f, 0.1f)
            }
        }
    }
}

/** On top of the boss sprite: a glint sweep, a world specific overlay, and damage when it is hurt. */
fun DrawScope.drawBossFront(world: Int, c: Offset, h: Float, t: Float, hp: Float) {
    val col = bxCol(world)
    val sw = (t / 3.2f) % 1f
    if (sw < 0.3f) {
        val f = sw / 0.3f
        val x = c.x - h * 0.5f + f * h * 1.0f
        drawLine(Color.White.copy(alpha = 0.55f * sin(f * PI.toFloat())), Offset(x - h * 0.12f, c.y + h * 0.4f), Offset(x + h * 0.12f, c.y - h * 0.4f), strokeWidth = h * 0.05f, blendMode = BlendMode.Plus)
    }
    when (world) {
        1 -> if (fxRnd((t * 9f).toInt(), 1) > 0.7f) drawRect(Color.Black.copy(alpha = 0.4f), Offset(c.x - h * 0.4f, c.y - h * 0.4f), Size(h * 0.8f, h * 0.8f))
        5, 6 -> {
            val step = (t * 12f).toInt()
            if (fxRnd(step, world) > 0.6f) {
                val sy = c.y - h * 0.4f + fxRnd(step, 2) * h * 0.8f
                drawRect((if (world == 5) col else Color(0xFF00E5FF)).copy(alpha = 0.55f), Offset(c.x - h * 0.45f + fxRnd(step, 3) * h * 0.1f, sy), Size(h * 0.9f, h * 0.035f), blendMode = BlendMode.Plus)
            }
        }
        7 -> for (k in 0 until 4) {
            val tw = (sin(t * 3f + k * 1.7f) * 0.5f + 0.5f)
            fxSpark(Offset(c.x + (fxRnd(k, 12) - 0.5f) * h * 0.7f, c.y + (fxRnd(k, 13) - 0.5f) * h * 0.7f), h * 0.05f * tw, Color.White, tw)
        }
        4 -> for (k in 0 until 3) {
            val f = (t * 0.8f + k / 3f) % 1f
            drawCircle(col.copy(alpha = 0.8f * (1f - f)), radius = h * 0.018f, center = Offset(c.x + (k - 1) * h * 0.2f, c.y + h * 0.3f + f * h * 0.25f))
        }
        8 -> fxSpark(Offset(c.x, c.y - h * 0.5f), h * 0.07f * (0.7f + 0.3f * sin(t * 4f)), Color(0xFFFFE082), 1f)
    }
    // damage: sparks below 50% health, flames below 25%
    if (hp < 0.5f) {
        val step = (t * 10f).toInt()
        for (k in 0 until 2) if (fxRnd(step, k + 40) > 0.4f) {
            val p = Offset(c.x + (fxRnd(step, k + 41) - 0.5f) * h * 0.6f, c.y + (fxRnd(step, k + 42) - 0.5f) * h * 0.6f)
            fxBolt(p, Offset(p.x + (fxRnd(step, k + 43) - 0.5f) * h * 0.2f, p.y + (fxRnd(step, k + 44) - 0.5f) * h * 0.2f), step + k, Color(0xFFFFE57F), h * 0.01f, 0.9f)
        }
    }
    if (hp < 0.25f) for (k in 0 until 5) {
        val f = (t * 1.4f + k / 5f) % 1f
        val px = c.x + (fxRnd(k, 50) - 0.5f) * h * 0.6f
        fxGlow(Offset(px + sin(t * 6f + k) * h * 0.02f, c.y + h * 0.1f - f * h * 0.5f), h * 0.1f * (1f - f), Color(0xFFFF6D00), 0.8f * (1f - f))
    }
}

// ================================================================================================================ boss shots

/**
 * One boss projectile, flying to the left. It is the real bullet asset ([img]: a crystal spear with a rune vortex at its tail),
 * made alive: glow aura, tinted after-images, a streaming energy tail with sparks, spinning rune rings round the vortex,
 * a glint on the tip and a tiny shake. [ch] = screen height, [seed] differs per bullet, [world] only tints the energy.
 */
fun DrawScope.drawBossShot(world: Int, x: Float, y: Float, ch: Float, t: Float, seed: Int, img: ImageBitmap) {
    val col = bxCol(world)
    val hot = Color(0xFFFF2BD6)
    val H = ch * 0.10f
    val W = H * (img.width.toFloat() / img.height.toFloat())
    val ph = (seed and 31) * 0.5f
    val jx = sin(t * 47f + ph) * H * 0.012f
    val jy = sin(t * 39f + ph) * H * 0.014f
    val pulse = 0.5f + 0.5f * sin(t * 13f + ph)
    val sc = 1f + 0.035f * sin(t * 17f + ph)
    val cx = x + jx
    val cy = y + jy
    // after rotating the art by 23 degrees its spear points straight left: tip at -1.03H, rune vortex at +0.47H, axis +0.07H below centre
    val axisY = cy + H * 0.07f
    val vx = cx + H * 0.47f
    val tipX = cx - H * 1.03f

    // 1. the streaming energy tail behind the vortex
    for (k in 0 until 3) {
        val len = H * (2.0f + 0.8f * k)
        val wob = sin(t * 11f + k * 2f + ph) * H * 0.05f
        drawLine(
            Brush.horizontalGradient(listOf(hot.copy(alpha = 0.5f - 0.12f * k), Color.Transparent), startX = vx, endX = vx + len),
            Offset(vx, axisY + (k - 1) * H * 0.12f), Offset(vx + len, axisY + (k - 1) * H * 0.12f + wob),
            strokeWidth = H * (0.11f - 0.025f * k), cap = StrokeCap.Round, blendMode = BlendMode.Plus
        )
    }
    // 2. sparks and shards shed from the vortex, flying back past the bullet
    for (k in 0 until 9) {
        val f = ((t * (1.6f + 0.25f * (k % 3)) + fxRnd(seed, k + 3)) % 1f)
        val px = vx + f * H * 2.6f
        val py = axisY + (fxRnd(seed, k + 11) - 0.5f) * H * 0.9f * (0.3f + f)
        val a = (1f - f) * 0.9f
        if (k % 3 == 0) fxShard(Offset(px, py), H * 0.045f, t * 8f + k, if (k % 2 == 0) hot else Color.White, a)
        else drawCircle((if (k % 2 == 0) hot else col).copy(alpha = a), H * 0.022f * (1f - f * 0.5f), Offset(px, py), blendMode = BlendMode.Plus)
    }
    // 3. tinted after-images, so it reads as fast
    for (k in 3 downTo 1) {
        withTransform({ translate(H * 0.34f * k, H * 0.02f * k); rotate(23f, Offset(cx, cy)); scale(sc, sc, Offset(cx, cy)) }) {
            drawImage(
                image = img,
                dstOffset = IntOffset((cx - W / 2f).roundToInt(), (cy - H / 2f).roundToInt()),
                dstSize = IntSize(W.roundToInt(), H.roundToInt()),
                alpha = 0.22f / k,
                colorFilter = ColorFilter.tint(hot, BlendMode.SrcAtop)
            )
        }
    }
    // 4. the glow around the whole bullet
    fxGlow(Offset(cx - H * 0.25f, axisY), H * (1.0f + 0.15f * pulse), hot, 0.38f + 0.2f * pulse)
    fxGlow(Offset(vx, axisY), H * 0.75f, col, 0.30f)
    // 5. the bullet itself
    withTransform({ rotate(23f, Offset(cx, cy)); scale(sc, sc, Offset(cx, cy)) }) {
        drawImage(
            image = img,
            dstOffset = IntOffset((cx - W / 2f).roundToInt(), (cy - H / 2f).roundToInt()),
            dstSize = IntSize(W.roundToInt(), H.roundToInt())
        )
    }
    // 6. spinning rune rings around the vortex
    val c = Offset(vx, axisY)
    val dash = PathEffect.dashPathEffect(floatArrayOf(H * 0.07f, H * 0.05f))
    rotate(t * 220f + ph * 20f, c) {
        drawCircle(Color.White.copy(alpha = 0.55f), H * 0.36f, c, style = Stroke(width = H * 0.018f, pathEffect = dash), blendMode = BlendMode.Plus)
    }
    rotate(-t * 150f + ph * 11f, c) {
        drawCircle(hot.copy(alpha = 0.7f), H * 0.5f, c, style = Stroke(width = H * 0.022f, pathEffect = dash), blendMode = BlendMode.Plus)
    }
    for (k in 0 until 3) {
        val a = t * 6f + k * 2.094f + ph
        fxSpark(Offset(c.x + cos(a) * H * 0.5f, c.y + sin(a) * H * 0.5f), H * 0.05f, Color.White, 0.8f)
    }
    // 7. a glint that flashes on the tip, and air streaks along the body
    val gl = (sin(t * 9f + ph) * 0.5f + 0.5f)
    fxSpark(Offset(tipX + H * 0.05f, axisY), H * (0.10f + 0.12f * gl), Color.White, 0.5f + 0.5f * gl)
    for (k in 0 until 3) {
        val off = (k - 1) * H * 0.2f
        val sx = tipX + ((t * 7f + k * 0.33f) % 1f) * H * 1.5f
        drawLine(Color.White.copy(alpha = 0.22f), Offset(sx, axisY + off), Offset(sx + H * 0.5f, axisY + off), strokeWidth = H * 0.012f, cap = StrokeCap.Round, blendMode = BlendMode.Plus)
    }
}

// ================================================================================================================ blink creatures

/** How each blink creature moves: 0 wolf, 1 raptor, 2 hornet, 3 arachnid, 4 wraith. */
fun blinkPose(kind: Int, size: Float, t: Float, seed: Int): BxPose {
    val p = seed * 0.37f
    return when (kind) {
        0 -> BxPose(-size * 0.08f * abs(sin(t * 3.2f + p)), sin(t * 6f + p) * size * 0.02f, sin(t * 3.2f + p) * 3f, 1f, 1f)
        1 -> BxPose(0f, sin(t * 6f + p) * size * 0.05f, sin(t * 6f + p) * 5f, 1f, 1f + 0.1f * sin(t * 12f + p))
        2 -> BxPose(sin(t * 40f + p) * size * 0.015f, sin(t * 33f + p) * size * 0.02f + sin(t * 2.5f) * size * 0.05f, sin(t * 20f + p) * 4f, 1f, 1f)
        3 -> BxPose(((t * 6f + seed).toInt() % 2) * size * 0.025f, sin(t * 9f + p) * size * 0.015f, 0f, 1f + 0.05f * sin(t * 9f + p), 1f - 0.04f * sin(t * 9f + p))
        else -> BxPose(0f, sin(t * 2f + p) * size * 0.06f, sin(t * 1.5f + p) * 6f, 1f + 0.04f * sin(t * 3f + p), 1f)
    }
}

/** Behind the creature while it is solid: a trail and signature effect for each kind. */
fun DrawScope.drawBlinkBack(kind: Int, c: Offset, size: Float, col: Color, t: Float, seed: Int) {
    val s = size
    val p = seed * 0.37f
    when (kind) {
        0 -> { // WOLF: claw slashes and speed lines behind it
            for (k in 0 until 3) {
                val f = ((t * 2.4f + k / 3f) % 1f)
                val ox = c.x + s * (0.4f + 0.9f * f)
                val oy = c.y + (k - 1) * s * 0.22f
                drawLine(Color(0xFFFF5252).copy(alpha = (1f - f) * 0.8f), Offset(ox, oy - s * 0.16f), Offset(ox - s * 0.28f, oy + s * 0.16f), strokeWidth = s * 0.035f, cap = StrokeCap.Round, blendMode = BlendMode.Plus)
            }
            for (k in 0 until 4) drawLine(col.copy(alpha = 0.35f), Offset(c.x + s * 0.4f, c.y + (k - 1.5f) * s * 0.16f), Offset(c.x + s * (0.9f + 0.2f * k), c.y + (k - 1.5f) * s * 0.16f), strokeWidth = s * 0.012f)
            fxGlow(Offset(c.x - s * 0.18f, c.y - s * 0.1f), s * 0.12f, Color(0xFFFF1744), 0.7f + 0.3f * sin(t * 8f))
        }
        1 -> { // RAPTOR: wing swooshes
            val flap = sin(t * 12f + p)
            for (side in intArrayOf(-1, 1)) {
                val path = Path().apply {
                    moveTo(c.x, c.y)
                    quadraticBezierTo(c.x + s * 0.3f, c.y + side * s * (0.45f + 0.3f * flap), c.x + s * 0.75f, c.y + side * s * (0.25f + 0.4f * flap))
                }
                drawPath(path, col.copy(alpha = 0.55f), style = Stroke(width = s * 0.05f, cap = StrokeCap.Round))
                drawPath(path, Color.White.copy(alpha = 0.5f), style = Stroke(width = s * 0.015f, cap = StrokeCap.Round))
            }
            for (k in 0 until 3) drawLine(col.copy(alpha = 0.3f), Offset(c.x + s * 0.4f, c.y + (k - 1) * s * 0.2f), Offset(c.x + s * 1.1f, c.y + (k - 1) * s * 0.2f + sin(t * 6f + k) * s * 0.05f), strokeWidth = s * 0.012f)
        }
        2 -> { // HORNET: blurred buzzing wings and a zigzag trail
            for (side in intArrayOf(-1, 1)) {
                val a = 0.35f + 0.3f * abs(sin(t * 55f + p))
                fxGlow(Offset(c.x + s * 0.05f, c.y + side * s * 0.3f), s * 0.35f, Color.White, a * 0.6f)
            }
            val z = Path().apply {
                moveTo(c.x + s * 0.3f, c.y)
                for (i in 1..7) lineTo(c.x + s * 0.3f + i * s * 0.2f, c.y + (if (i % 2 == 0) -1f else 1f) * s * 0.1f * (1f - i / 8f))
            }
            drawPath(z, Color(0xFFFFEA00).copy(alpha = 0.5f), style = Stroke(width = s * 0.02f, cap = StrokeCap.Round))
            fxGlow(Offset(c.x - s * 0.35f, c.y), s * 0.14f * (0.7f + 0.3f * sin(t * 10f)), Color(0xFFFFEA00), 0.9f)
        }
        3 -> { // ARACHNID: web threads to the edges and crawling nodes
            for (k in 0 until 6) {
                val a = k * (BTAU / 6f) + 0.3f
                val e = Offset(c.x + cos(a) * s * 0.85f, c.y + sin(a) * s * 0.85f)
                drawLine(Color.White.copy(alpha = 0.3f), c, e, strokeWidth = s * 0.01f)
                val f = ((t * 0.8f + k * 0.17f) % 1f)
                fxGlow(Offset(c.x + (e.x - c.x) * f, c.y + (e.y - c.y) * f), s * 0.05f, col, 0.9f)
            }
            drawCircle(Color.White.copy(alpha = 0.25f), s * 0.5f, c, style = Stroke(width = s * 0.01f))
            drawCircle(Color.White.copy(alpha = 0.2f), s * 0.75f, c, style = Stroke(width = s * 0.01f))
        }
        else -> { // WRAITH: fading afterimages, wisps and hollow eyes
            for (k in 1..4) {
                val f = k / 5f
                fxGlow(Offset(c.x + k * s * 0.3f, c.y + sin(t * 3f + k * 0.8f + p) * s * 0.12f), s * (0.5f - 0.06f * k), col, 0.3f * (1f - f))
            }
            for (k in 0 until 5) {
                val bx = c.x - s * 0.25f + k * s * 0.12f
                val path = Path().apply {
                    moveTo(bx, c.y + s * 0.3f)
                    for (i in 1..5) lineTo(bx + sin(t * 3f + k + i) * s * 0.06f, c.y + s * 0.3f + i * s * 0.08f)
                }
                drawPath(path, col.copy(alpha = 0.4f), style = Stroke(width = s * 0.02f, cap = StrokeCap.Round))
            }
            fxGlow(Offset(c.x - s * 0.12f, c.y - s * 0.1f), s * 0.07f, Color.White, 0.9f)
            fxGlow(Offset(c.x + s * 0.06f, c.y - s * 0.1f), s * 0.07f, Color.White, 0.9f)
        }
    }
}

/** Materialising (phase 0..2) and dissolving (phase 6..7) while visible. */
fun DrawScope.drawBlinkTransition(kind: Int, c: Offset, size: Float, col: Color, phase: Int, t: Float, seed: Int) {
    val appear = phase < 3
    val vanish = phase >= 6
    if (!appear && !vanish) return
    val f = if (appear) (phase + 0.5f) / 3f else (phase - 5.5f) / 2f
    val s = size
    if (appear) {
        drawCircle(col.copy(alpha = (1f - f) * 0.8f), s * (0.2f + 0.9f * f), c, style = Stroke(width = s * 0.05f * (1f - f)))
        fxGlow(c, s * 0.8f, Color.White, 0.45f * (1f - f))
        for (k in 0 until 8) {
            val a = k * (BTAU / 8f) + kind
            val d = s * (0.9f - 0.7f * f)
            fxShard(Offset(c.x + cos(a) * d, c.y + sin(a) * d), s * 0.07f, a + t * 5f, col, 1f - f)
        }
    } else {
        for (k in 0 until 12) {
            val a = fxRnd(seed + k, kind) * BTAU
            val d = s * (0.2f + 0.7f * f) * (0.5f + fxRnd(k, 7))
            fxGlow(Offset(c.x + cos(a) * d, c.y + sin(a) * d - s * 0.3f * f), s * 0.05f, col, 0.9f * (1f - f))
        }
        drawCircle(col.copy(alpha = 0.5f * (1f - f)), s * (0.3f + 0.5f * f), c, style = Stroke(width = s * 0.02f))
    }
}

/** While it is "invisible": a faint outline of its kind, and a gathering warning in the last ticks before it blinks in. */
fun DrawScope.drawBlinkTelltale(kind: Int, c: Offset, size: Float, col: Color, phase: Int, cycle: Int, t: Float) {
    val s = size
    val pulse = 0.5f + 0.5f * sin(t * 4f)
    val a = 0.1f + 0.08f * pulse
    when (kind) {
        0 -> drawLine(col.copy(alpha = a), Offset(c.x - s * 0.3f, c.y), Offset(c.x + s * 0.3f, c.y), strokeWidth = s * 0.02f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(s * 0.06f, s * 0.06f)))
        1 -> { drawLine(col.copy(alpha = a), Offset(c.x - s * 0.3f, c.y), Offset(c.x, c.y - s * 0.2f * pulse), strokeWidth = s * 0.02f); drawLine(col.copy(alpha = a), Offset(c.x - s * 0.3f, c.y), Offset(c.x, c.y + s * 0.2f * pulse), strokeWidth = s * 0.02f) }
        2 -> fxGlow(Offset(c.x + sin(t * 30f) * s * 0.04f, c.y), s * 0.1f, Color(0xFFFFEA00), a * 3f)
        3 -> for (k in 0 until 4) drawLine(col.copy(alpha = a), c, Offset(c.x + cos(k * 1.57f + 0.4f) * s * 0.35f, c.y + sin(k * 1.57f + 0.4f) * s * 0.35f), strokeWidth = s * 0.01f)
        else -> fxGlow(c, s * 0.3f * (0.8f + 0.2f * pulse), col, a * 2f)
    }
    // the last ticks before it blinks back in: the energy gathers into the spot
    val left = cycle - phase
    if (left <= 4) {
        val f = (4 - left) / 4f
        for (k in 0 until 8) {
            val ang = k * (BTAU / 8f) + t * 2f
            val d = s * (0.9f - 0.65f * f)
            fxGlow(Offset(c.x + cos(ang) * d, c.y + sin(ang) * d), s * 0.05f, col, 0.4f + 0.5f * f)
        }
        drawCircle(col.copy(alpha = 0.3f + 0.4f * f), s * (0.55f - 0.25f * f), c, style = Stroke(width = s * 0.015f))
    }
}
