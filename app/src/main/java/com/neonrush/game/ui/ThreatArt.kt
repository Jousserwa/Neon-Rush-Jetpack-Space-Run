package com.neonrush.game.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
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

/**
 * Code-drawn art for the new threats (fire, lava, mountains, boss charge-up, WARN markers) and the
 * SHADOW clones, which re-use the player's own pilot frames (mirrored, tinted, with afterimages).
 * Returns true when it drew [elem]; the caller then skips its normal drawing.
 */
internal fun DrawScope.drawThreat(
    elem: VisualTrackElement, x: Float, y: Float, cw: Float, ch: Float,
    tick: Int, pilot: ImageBitmap
): Boolean {
    val t = tick.toFloat()
    when (elem.subType) {
        "WARN" -> {
            val a = 0.35f + 0.45f * (0.5f + 0.5f * sin(t * 1.4f))
            val c = Color(0xFFFF3B30)
            drawLine(c.copy(alpha = a * 0.35f), Offset(x, y), Offset(cw * 0.22f, y), strokeWidth = 2f)
            val s = ch * 0.035f
            drawPath(Path().apply { moveTo(x - s, y); lineTo(x + s * 0.6f, y - s); lineTo(x + s * 0.6f, y + s); close() }, c.copy(alpha = a))
            drawCircle(Color.White.copy(alpha = a), s * 0.18f, Offset(x + s * 0.1f, y))
            return true
        }
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
        "FIRE_WALL" -> { drawFlame(x, y, ch * 0.17f, t, elem.id.hashCode()); return true }
        "LAVA_BALL" -> {
            val r = ch * 0.045f
            val c = Color(0xFFFF5A1F)
            // ember trail
            for (i in 1..4) drawCircle(c.copy(alpha = 0.35f - i * 0.07f), r * (1f - i * 0.15f), Offset(x + i * r * 0.9f, y - sin(t * 0.45f + i) * r * 0.3f))
            drawCircle(Brush.radialGradient(listOf(Color(0xFFFFF2A0), Color(0xFFFF8A00), Color(0xFFB3200A), Color.Transparent), center = Offset(x, y), radius = r * 1.6f), r * 1.6f, Offset(x, y))
            drawCircle(Color(0xFFFFE066), r * 0.45f, Offset(x - r * 0.15f, y - r * 0.15f))
            return true
        }
        "MOUNTAIN_BOTTOM" -> {
            val peakY = y - ch * 0.10f
            val half = cw * 0.075f
            drawPath(Path().apply { moveTo(x - half, ch); lineTo(x, peakY); lineTo(x + half, ch); close() },
                Brush.verticalGradient(listOf(Color(0xFF6B5B73), Color(0xFF2A2230)), startY = peakY, endY = ch))
            drawPath(Path().apply { moveTo(x - half * 0.28f, peakY + (ch - peakY) * 0.28f); lineTo(x, peakY); lineTo(x + half * 0.28f, peakY + (ch - peakY) * 0.28f); close() }, Color.White.copy(alpha = 0.75f)) // snow cap
            // lava glow along the base
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color(0xFFFF5A1F).copy(alpha = 0.35f + 0.15f * sin(t * 0.4f))), startY = ch * 0.9f, endY = ch), Offset(x - half, ch * 0.9f), Size(half * 2, ch * 0.1f))
            return true
        }
        "MOUNTAIN_TOP" -> {
            val tipY = y + ch * 0.10f
            val half = cw * 0.075f
            drawPath(Path().apply { moveTo(x - half, 0f); lineTo(x, tipY); lineTo(x + half, 0f); close() },
                Brush.verticalGradient(listOf(Color(0xFF2A2230), Color(0xFF6B5B73)), startY = 0f, endY = tipY))
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

/** Three layered flame tongues, flickering. Pure vector, no assets. */
private fun DrawScope.drawFlame(x: Float, y: Float, size: Float, t: Float, seed: Int) {
    val ph = (seed and 0xff) / 40f
    fun tongue(scaleX: Float, scaleY: Float, color: Color) {
        val sway = sin(t * 0.55f + ph) * size * 0.08f
        val flick = 1f + 0.12f * sin(t * 0.9f + ph * 2f)
        val hw = size * 0.34f * scaleX
        val hh = size * 0.62f * scaleY * flick
        drawPath(Path().apply {
            moveTo(x - hw, y + hh)
            cubicTo(x - hw * 1.1f, y, x - hw * 0.2f + sway, y - hh * 0.4f, x + sway * 1.5f, y - hh)
            cubicTo(x + hw * 0.2f + sway, y - hh * 0.4f, x + hw * 1.1f, y, x + hw, y + hh)
            close()
        }, color)
    }
    drawCircle(Brush.radialGradient(listOf(Color(0xFFFF6A00).copy(alpha = 0.35f), Color.Transparent), center = Offset(x, y), radius = size * 0.9f), size * 0.9f, Offset(x, y))
    tongue(1f, 1f, Color(0xFFD62800))
    tongue(0.72f, 0.82f, Color(0xFFFF8A00))
    tongue(0.42f, 0.6f, Color(0xFFFFE680))
}
