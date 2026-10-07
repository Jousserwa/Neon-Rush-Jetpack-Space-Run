package com.neonrush.game.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.neonrush.game.R
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * Pilot suit looks, drawn in code on top of the shared running frames:
 * a colour grade per suit plus an aura / particle effect. No new art needed.
 * When real sprite sets exist, set Skin.pilotFrameOverrides and these still stack on top.
 */

// Colour grade: saturation, per-channel multiply, and a small brightness offset (0..255 scale).
private fun grade(sat: Float = 1f, r: Float = 1f, g: Float = 1f, b: Float = 1f, off: Float = 0f): ColorFilter {
    val lr = 0.299f; val lg = 0.587f; val lb = 0.114f
    val ir = 1f - sat
    return ColorFilter.colorMatrix(
        ColorMatrix(
            floatArrayOf(
                (lr * ir + sat) * r, lg * ir * r, lb * ir * r, 0f, off,
                lr * ir * g, (lg * ir + sat) * g, lb * ir * g, 0f, off,
                lr * ir * b, lg * ir * b, (lb * ir + sat) * b, 0f, off,
                0f, 0f, 0f, 1f, 0f
            )
        )
    )
}

private fun hueRotate(deg: Float): ColorFilter {
    val rad = deg * PI.toFloat() / 180f
    val c = cos(rad); val s = sin(rad)
    return ColorFilter.colorMatrix(
        ColorMatrix(
            floatArrayOf(
                0.213f + c * 0.787f - s * 0.213f, 0.715f - c * 0.715f - s * 0.715f, 0.072f - c * 0.072f + s * 0.928f, 0f, 0f,
                0.213f - c * 0.213f + s * 0.143f, 0.715f + c * 0.285f + s * 0.140f, 0.072f - c * 0.072f - s * 0.283f, 0f, 0f,
                0.213f - c * 0.213f - s * 0.787f, 0.715f - c * 0.715f + s * 0.715f, 0.072f + c * 0.928f + s * 0.072f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            )
        )
    )
}

private val RED_ONLY = ColorFilter.colorMatrix(ColorMatrix(floatArrayOf(
    1f, 0f, 0f, 0f, 60f,  0f, 0f, 0f, 0f, 0f,  0f, 0f, 0f, 0f, 0f,  0f, 0f, 0f, 1f, 0f)))
private val CYAN_ONLY = ColorFilter.colorMatrix(ColorMatrix(floatArrayOf(
    0f, 0f, 0f, 0f, 0f,  0f, 1f, 0f, 0f, 60f,  0f, 0f, 1f, 0f, 60f,  0f, 0f, 0f, 1f, 0f)))

private fun suitGrade(skinId: String, tick: Int): ColorFilter? = when (skinId) {
    "blackout_runner" -> grade(sat = 0.45f, r = 0.8f, g = 0.82f, b = 0.75f)
    "signal_ghost" -> grade(sat = 0.5f, r = 0.75f, g = 1.1f, b = 1.2f, off = 10f)
    "convict_grey" -> grade(sat = 0f, off = -6f)
    "apex_predator" -> grade(sat = 1.2f, r = 0.75f, g = 1.15f, b = 0.8f)
    "chrome_reaper" -> grade(sat = 0.15f, r = 1.25f, g = 1.25f, b = 1.3f, off = 14f)
    "solar_flare" -> grade(sat = 1.3f, r = 1.35f, g = 1.05f, b = 0.55f, off = 12f)
    "void_walker" -> grade(sat = 0.25f, r = 0.5f, g = 0.45f, b = 0.65f)
    "toxic_bloom" -> grade(sat = 1.3f, r = 0.75f, g = 1.3f, b = 0.6f)
    "circuit_breaker" -> grade(sat = 1.1f, r = 0.85f, g = 1.15f, b = 1.25f)
    "golden_protocol" -> grade(sat = 1.2f, r = 1.4f, g = 1.15f, b = 0.55f, off = 8f)
    "glitch_core" -> grade(sat = 1.2f, r = 1.0f, g = 1.1f, b = 1.15f)
    "prism_vanguard" -> hueRotate((tick * 6f) % 360f)
    "iron_wraith" -> grade(sat = 0.5f, r = 1.0f, g = 0.8f, b = 0.7f)
    "nova_sprint" -> grade(sat = 1.1f, r = 0.8f, g = 1.0f, b = 1.45f, off = 10f)
    "obsidian_pulse" -> grade(sat = 0.3f, r = 0.42f, g = 0.42f, b = 0.5f)
    "zenith_circuit" -> grade(sat = 1.2f, r = 0.8f, g = 1.25f, b = 1.15f, off = 8f)
    "eternal_flame" -> grade(sat = 1.4f, r = 1.4f, g = 0.8f, b = 0.5f)
    else -> null
}

/** Draws the pilot with the equipped suit's grade and effects. Replaces a plain drawImage. */
internal fun DrawScope.drawPilotWithSuit(
    skinId: String, bmp: ImageBitmap, cx: Float, cy: Float, w: Float, h: Float, tick: Int
) {
    fun frame(dx: Float, dy: Float, alpha: Float, cf: ColorFilter?) = drawImage(
        image = bmp,
        dstOffset = IntOffset((cx - w / 2f + dx).roundToInt(), (cy - h / 2f + dy).roundToInt()),
        dstSize = IntSize(w.roundToInt(), h.roundToInt()),
        alpha = alpha,
        colorFilter = cf
    )

    drawSuitBehind(skinId, cx, cy, h, tick)
    when (skinId) {
        "glitch_core" -> {
            // RGB split that jitters harder in short bursts
            val burst = (tick % 24) < 4
            val j = if (burst) h * 0.09f else h * 0.03f
            val dy = if (burst) ((tick * 7) % 5 - 2) * h * 0.012f else 0f
            frame(-j, dy, 0.55f, RED_ONLY)
            frame(j, -dy, 0.55f, CYAN_ONLY)
            frame(0f, 0f, 0.92f, suitGrade(skinId, tick))
        }
        "signal_ghost" -> {
            frame(-h * 0.14f, 0f, 0.22f, suitGrade(skinId, tick))
            frame(-h * 0.07f, 0f, 0.35f, suitGrade(skinId, tick))
            frame(0f, 0f, if ((tick / 5) % 7 == 0) 0.55f else 0.92f, suitGrade(skinId, tick))
        }
        else -> frame(0f, 0f, 1f, suitGrade(skinId, tick))
    }
    drawSuitFront(skinId, cx, cy, h, tick)
}

private fun DrawScope.glow(c: Offset, radius: Float, color: Color, alpha: Float) {
    drawCircle(
        brush = Brush.radialGradient(listOf(color.copy(alpha = alpha), Color.Transparent), center = c, radius = radius),
        radius = radius, center = c
    )
}

private fun beat(p: Float): Float = when {
    p < 0.10f -> sin(p / 0.10f * PI.toFloat())
    p in 0.18f..0.28f -> 0.7f * sin((p - 0.18f) / 0.10f * PI.toFloat())
    else -> 0f
}

private fun DrawScope.drawSuitBehind(skinId: String, cx: Float, cy: Float, h: Float, tick: Int) {
    val c = Offset(cx, cy)
    val u = h * 0.06f
    val t = tick.toFloat()
    when (skinId) {
        "blackout_runner" -> glow(c, h * 0.6f, Color(0xFFFF9800), 0.14f + 0.08f * sin(t * 0.12f))
        "apex_predator" -> drawCircle(Color(0xFF39FF14).copy(alpha = 0.35f), h * 0.55f + sin(t * 0.1f) * h * 0.03f, c, style = Stroke(2.5f))
        "solar_flare" -> {
            glow(c, h * 0.8f, Color(0xFFFF9800), 0.45f)
            for (k in 0 until 8) {
                val a = t * 0.04f + k * (PI.toFloat() / 4f)
                val d = Offset(cos(a), sin(a))
                drawLine(Color(0xFFFFD54F).copy(alpha = 0.35f), c + d * (h * 0.45f), c + d * (h * 0.75f), strokeWidth = 3f)
            }
        }
        "void_walker" -> {
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(Color.Black.copy(alpha = 0.75f), Color(0xFF7C4DFF).copy(alpha = 0.25f), Color.Transparent),
                    center = c, radius = h * 0.7f
                ), radius = h * 0.7f, center = c
            )
            for (k in 0 until 4) {
                val a = -t * 0.07f + k * (PI.toFloat() / 2f)
                drawCircle(Color(0xFFB388FF).copy(alpha = 0.7f), u * 0.5f, c + Offset(cos(a), sin(a)) * (h * 0.52f))
            }
        }
        "golden_protocol" -> glow(c, h * 0.65f, Color(0xFFFFD23F), 0.3f)
        "prism_vanguard" -> {
            val hue = (t * 6f) % 360f
            drawCircle(Color.hsv(hue, 0.8f, 1f).copy(alpha = 0.5f), h * 0.55f, c, style = Stroke(3f))
            drawCircle(Color.hsv((hue + 120f) % 360f, 0.8f, 1f).copy(alpha = 0.4f), h * 0.62f, c, style = Stroke(2.5f))
        }
        "nova_sprint" -> for (k in 0 until 6) {
            val off = (k - 2.5f) * h * 0.12f
            val len = h * (0.5f + 0.3f * (((tick + k * 5) % 10) / 10f))
            drawLine(Color(0xFF9BD5FF).copy(alpha = 0.35f), Offset(cx - h * 0.15f, cy + off), Offset(cx - h * 0.15f - len, cy + off), strokeWidth = 2.5f)
        }
        "obsidian_pulse" -> {
            val a = beat((tick % 36) / 36f)
            glow(c, h * (0.5f + 0.15f * a), Color(0xFF00E5FF), 0.1f + 0.3f * a)
        }
        "zenith_circuit" -> {
            drawCircle(Color(0xFF00FFC6).copy(alpha = 0.25f), h * 0.5f, c, style = Stroke(1.5f))
            for (k in 0 until 6) {
                val a = t * 0.05f + k * (PI.toFloat() / 3f)
                val p = c + Offset(cos(a), sin(a)) * (h * 0.5f)
                drawRect(Color(0xFF00FFC6).copy(alpha = 0.85f), p - Offset(u * 0.5f, u * 0.5f), androidx.compose.ui.geometry.Size(u, u))
            }
        }
        "eternal_flame" -> glow(c, h * 0.7f, Color(0xFFFF5722), 0.3f)
    }
}

private fun DrawScope.drawSuitFront(skinId: String, cx: Float, cy: Float, h: Float, tick: Int) {
    val u = h * 0.06f
    val t = tick.toFloat()
    when (skinId) {
        "chrome_reaper" -> {
            val p = (tick % 44) / 44f
            if (p < 0.5f) {
                val gx = cx - h * 0.3f + p * 2f * h * 0.6f
                drawLine(Color.White.copy(alpha = 0.65f), Offset(gx, cy - h * 0.4f), Offset(gx - h * 0.12f, cy + h * 0.4f), strokeWidth = 4f)
            }
        }
        "golden_protocol" -> for (k in 0 until 5) {
            val a = t * 0.06f + k * (2f * PI.toFloat() / 5f)
            val p = Offset(cx + cos(a) * h * 0.4f, cy + sin(a) * h * 0.4f)
            val tw = (sin(t * 0.3f + k) * 0.5f + 0.5f)
            val s = u * (0.5f + tw)
            val col = Color.White.copy(alpha = 0.4f + 0.5f * tw)
            drawLine(col, p - Offset(s, 0f), p + Offset(s, 0f), strokeWidth = 2f)
            drawLine(col, p - Offset(0f, s), p + Offset(0f, s), strokeWidth = 2f)
        }
        "toxic_bloom" -> for (k in 0 until 7) {
            val ph = (t * 0.03f + k / 7f) % 1f
            val x = cx + sin(k * 2.1f + t * 0.05f) * h * 0.3f
            val y = cy + h * 0.4f - ph * h * 0.9f
            drawCircle(Color(0xFF7CFF3A).copy(alpha = (1f - ph) * 0.7f), u * 0.7f * (1f - ph) + 1.5f, Offset(x, y))
        }
        "circuit_breaker" -> {
            val r = Random(tick / 2 * 31 + 7)
            for (arc in 0 until 3) {
                var p = Offset(cx + (r.nextFloat() - 0.5f) * h * 0.4f, cy + (r.nextFloat() - 0.5f) * h * 0.7f)
                for (seg in 0 until 4) {
                    val n = p + Offset((r.nextFloat() - 0.5f) * h * 0.28f, (r.nextFloat() - 0.5f) * h * 0.28f)
                    drawLine(Color(0xFF7DF9FF).copy(alpha = 0.9f), p, n, strokeWidth = 2.5f)
                    p = n
                }
            }
        }
        "iron_wraith" -> for (k in 0 until 5) {
            val ph = (t * 0.07f + k / 5f) % 1f
            val x = cx - h * 0.1f + sin(k * 3.3f) * h * 0.25f
            val y = cy + ph * h * 0.55f
            drawRect(Color(0xFFFFA040).copy(alpha = (1f - ph) * 0.9f), Offset(x, y), androidx.compose.ui.geometry.Size(u * 0.5f, u * 0.5f))
        }
        "eternal_flame" -> for (k in 0 until 9) {
            val ph = (t * 0.05f + k / 9f) % 1f
            val x = cx + sin(k * 1.7f + t * 0.1f) * h * 0.22f
            val y = cy + h * 0.35f - ph * h * 0.8f
            val col = androidx.compose.ui.graphics.lerp(Color(0xFFFFC107), Color(0xFFD50000), ph)
            drawCircle(col.copy(alpha = (1f - ph) * 0.8f), u * 1.1f * (1f - ph) + 1.5f, Offset(x, y))
        }
    }
}

/** Animated shop preview: shows exactly what the suit looks like in a run. */
@Composable
fun SuitPreview(skinId: String, modifier: Modifier = Modifier.size(64.dp)) {
    val bmp = ImageBitmap.imageResource(id = R.drawable.pilot_run_1)
    val t = rememberInfiniteTransition(label = "suitPreview")
    val tick by t.animateFloat(
        initialValue = 0f, targetValue = 480f,
        animationSpec = infiniteRepeatable(tween(60000, easing = LinearEasing), RepeatMode.Restart),
        label = "suitPreviewTick"
    )
    Canvas(modifier = modifier.clipToBounds()) {
        val h = size.height * 0.85f
        val w = h * bmp.width / bmp.height
        drawPilotWithSuit(skinId, bmp, size.width / 2f, size.height / 2f, w, h, tick.toInt())
    }
}
