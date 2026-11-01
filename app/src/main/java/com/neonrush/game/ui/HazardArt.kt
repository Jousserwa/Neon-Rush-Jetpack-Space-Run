package com.neonrush.game.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.neonrush.game.VisualTrackElement
import kotlin.math.cos
import kotlin.math.sin

/**
 * Code-drawn art for the hazard emplacements and their shots (Hazards.kt): flame spitter, spear thrower, acid sprayer, rock catapult.
 * Returns true when it drew [elem]; the caller then skips its normal drawing. [time] is the smooth seconds clock.
 */
internal fun DrawScope.drawHazard(elem: VisualTrackElement, x: Float, y: Float, cw: Float, ch: Float, tick: Int, time: Float): Boolean {
    val sub = elem.subType
    if (!sub.startsWith("HZ_")) return false
    val u = ch * 0.075f
    val pulse = 0.5f + 0.5f * sin(time * 9f)
    val steel = Color(0xFF546E7A); val dark = Color(0xFF1C2833); val wood = Color(0xFF8D6E63)
    when (sub) {
        "HZ_FLAMER" -> {
            // tank, body, nozzle pointing at the pilot, flickering pilot flame
            drawRect(Color(0xFFB71C1C), Offset(x + 0.1f * u, y - 1.5f * u), Size(1.5f * u, 0.9f * u))
            drawRect(dark, Offset(x - 1.0f * u, y - 0.7f * u), Size(2.4f * u, 1.4f * u))
            drawRect(steel, Offset(x - 1.0f * u, y - 0.7f * u), Size(2.4f * u, 1.4f * u), style = Stroke(2f))
            drawRect(steel, Offset(x - 2.1f * u, y - 0.25f * u), Size(1.2f * u, 0.5f * u))
            drawLine(steel, Offset(x - 0.6f * u, y + 0.7f * u), Offset(x - 0.9f * u, y + 1.5f * u), 4f, StrokeCap.Round)
            drawLine(steel, Offset(x + 1.0f * u, y + 0.7f * u), Offset(x + 1.3f * u, y + 1.5f * u), 4f, StrokeCap.Round)
            val r = u * (0.45f + 0.15f * pulse)
            drawCircle(Brush.radialGradient(listOf(Color(0xFFFFF59D), Color(0xFFFF6F00), Color.Transparent), Offset(x - 2.3f * u, y), r * 1.8f), r * 1.8f, Offset(x - 2.3f * u, y))
            return true
        }
        "HZ_SPEAR" -> {
            // ballista: two bow arms, a string, a long spear lying in the track
            val arm = Path().apply { moveTo(x + 0.9f * u, y - 1.6f * u); lineTo(x + 0.2f * u, y); lineTo(x + 0.9f * u, y + 1.6f * u) }
            drawPath(arm, wood, style = Stroke(u * 0.35f, cap = StrokeCap.Round))
            drawLine(Color(0xFFE0E0E0), Offset(x + 0.9f * u, y - 1.6f * u), Offset(x + 0.9f * u, y + 1.6f * u), 2f)
            drawRect(dark, Offset(x + 0.1f * u, y - 0.35f * u), Size(1.1f * u, 0.7f * u))
            drawLine(wood, Offset(x + 0.4f * u, y), Offset(x - 2.2f * u, y), u * 0.16f, StrokeCap.Round)
            val tip = Path().apply { moveTo(x - 2.2f * u, y); lineTo(x - 1.6f * u, y - 0.3f * u); lineTo(x - 1.6f * u, y + 0.3f * u); close() }
            drawPath(tip, Color(0xFFCFD8DC))
            return true
        }
        "HZ_ACID" -> {
            // glass tank of bubbling poison on a bracket, nozzle tipped toward the pilot, a drip forming
            drawRect(Color(0xFF263238), Offset(x - 0.9f * u, y - 1.1f * u), Size(1.8f * u, 2.0f * u))
            drawRect(Color(0xFF76FF03).copy(alpha = 0.55f), Offset(x - 0.75f * u, y - 0.5f * u), Size(1.5f * u, 1.25f * u))
            for (i in 0 until 3) drawCircle(Color(0xFFCCFF90).copy(alpha = 0.8f), u * 0.12f, Offset(x - 0.4f * u + i * 0.4f * u, y + 0.5f * u - ((time * 0.9f + i * 0.33f) % 1f) * 1.0f * u))
            drawRect(steel, Offset(x - 0.9f * u, y - 1.1f * u), Size(1.8f * u, 2.0f * u), style = Stroke(2f))
            drawLine(steel, Offset(x - 0.9f * u, y + 0.5f * u), Offset(x - 1.8f * u, y + 0.9f * u), u * 0.28f, StrokeCap.Round)
            val dp = ((time * 1.6f) % 1f)
            drawCircle(Color(0xFF76FF03).copy(alpha = 1f - dp), u * 0.16f, Offset(x - 1.8f * u, y + 0.9f * u + dp * u))
            return true
        }
        "HZ_ROCK" -> {
            // catapult: base block, wheels, a throwing arm with a boulder in its cup
            val sw = 0.5f + 0.5f * sin(time * 2f)
            drawRect(dark, Offset(x - 0.2f * u, y - 0.2f * u), Size(2.0f * u, 0.9f * u))
            drawCircle(steel, u * 0.4f, Offset(x + 0.1f * u, y + 0.8f * u)); drawCircle(steel, u * 0.4f, Offset(x + 1.5f * u, y + 0.8f * u))
            val ex = x - 1.6f * u - 0.3f * u * sw; val ey = y - 1.7f * u + 0.2f * u * sw
            drawLine(wood, Offset(x + 0.9f * u, y - 0.1f * u), Offset(ex, ey), u * 0.3f, StrokeCap.Round)
            drawCircle(Color(0xFF78909C), u * 0.55f, Offset(ex, ey - 0.3f * u))
            drawCircle(Color(0xFF546E7A), u * 0.14f, Offset(ex - 0.15f * u, ey - 0.4f * u))
            return true
        }
        "HZ_CHARGE" -> {
            // charge-up orb and aim line toward the pilot, brighter each tick
            val parts = elem.id.split("_")
            val k = ((parts.getOrNull(2)?.toIntOrNull() ?: 1).coerceIn(1, 5)) / 5f
            val kind = parts.getOrNull(3)?.toIntOrNull() ?: 0
            val c = when (kind) { 1 -> Color(0xFFE0E0E0); 3 -> Color(0xFFFFB74D); else -> Color(0xFFFF7043) }
            drawLine(c.copy(alpha = 0.10f + 0.30f * k), Offset(x - 2.2f * u, y), Offset(cw * 0.22f, y), 1.5f + 2.5f * k)
            val r = u * (0.25f + 0.55f * k)
            drawCircle(Brush.radialGradient(listOf(Color.White, c, Color.Transparent), Offset(x - 2.3f * u, y), r * 2.2f), r * 2.2f, Offset(x - 2.3f * u, y))
            return true
        }
        "HZ_FIREBALL" -> {
            val r = u * 0.42f
            for (i in 1..4) drawCircle(Color(0xFFFF5A1F).copy(alpha = 0.35f - i * 0.07f), r * (1f - i * 0.15f), Offset(x + i * r * 0.9f, y - sin(time * 9f + i) * r * 0.3f))
            drawCircle(Brush.radialGradient(listOf(Color(0xFFFFF2A0), Color(0xFFFF8A00), Color(0xFFB3200A), Color.Transparent), Offset(x, y), r * 1.6f), r * 1.6f, Offset(x, y))
            drawCircle(Color(0xFFFFE066), r * 0.45f, Offset(x - r * 0.15f, y - r * 0.15f))
            return true
        }
        "HZ_SPEARSHOT" -> {
            drawLine(Color(0xFFE0E0E0).copy(alpha = 0.25f), Offset(x + u * 3.2f, y), Offset(x + u * 0.8f, y), 3f, StrokeCap.Round)
            drawLine(wood, Offset(x + u * 1.6f, y), Offset(x - u * 0.6f, y), u * 0.2f, StrokeCap.Round)
            val tip = Path().apply { moveTo(x - u * 1.2f, y); lineTo(x - u * 0.5f, y - u * 0.3f); lineTo(x - u * 0.5f, y + u * 0.3f); close() }
            drawPath(tip, Color(0xFFECEFF1))
            return true
        }
        "HZ_ACIDDROP" -> {
            val r = u * 0.34f
            drawCircle(Color(0xFF76FF03).copy(alpha = 0.22f), r * 2.2f, Offset(x, y))
            val tear = Path().apply { moveTo(x + r * 1.8f, y - r * 1.2f); lineTo(x + r * 0.6f, y - r * 0.5f); lineTo(x + r * 0.9f, y + r * 0.2f); close() }
            drawPath(tear, Color(0xFF76FF03).copy(alpha = 0.6f))
            drawCircle(Color(0xFF76FF03), r, Offset(x, y))
            drawCircle(Color(0xFFE6FFB0), r * 0.35f, Offset(x - r * 0.3f, y - r * 0.3f))
            return true
        }
        "HZ_BOULDER" -> {
            val r = u * 0.62f
            val a = time * 5f
            for (i in 1..3) drawCircle(Color(0xFF90A4AE).copy(alpha = 0.22f - i * 0.05f), r * (0.8f - i * 0.12f), Offset(x + i * r * 0.9f, y))
            drawCircle(Color(0xFF607D8B), r, Offset(x, y))
            drawCircle(Color(0xFF455A64), r * 0.28f, Offset(x + cos(a) * r * 0.4f, y + sin(a) * r * 0.4f))
            drawCircle(Color(0xFF455A64), r * 0.2f, Offset(x - cos(a) * r * 0.45f, y - sin(a) * r * 0.2f))
            drawCircle(Color(0xFFB0BEC5), r, Offset(x, y), style = Stroke(2f))
            return true
        }
    }
    return false
}
