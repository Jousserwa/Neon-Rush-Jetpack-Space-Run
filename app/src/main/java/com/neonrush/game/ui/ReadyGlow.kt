package com.neonrush.game.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.sin

private val READY_GREEN = Color(0xFF69F0AE)

/**
 * "This is ready for you to buy": a breathing glow around the card, a light sheen that sweeps across it and twinkling corner stars.
 * Put it BEFORE .clip(...) in the modifier chain so the outer glow is not cut off. Does nothing when [enabled] is false.
 */
fun Modifier.readyGlow(enabled: Boolean, color: Color = READY_GREEN, corner: Dp = 14.dp): Modifier =
    if (!enabled) this else composed {
        val inf = rememberInfiniteTransition(label = "readyGlow")
        val t by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Restart), label = "t")
        val p by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "p")
        this.drawWithContent {
            val w = size.width; val h = size.height; val cr = CornerRadius(corner.toPx())
            // soft outer halo (drawn first so it sits behind the card content)
            drawRoundRect(color.copy(alpha = 0.10f + 0.16f * p), topLeft = Offset(-6f, -6f), size = Size(w + 12f, h + 12f), cornerRadius = CornerRadius(cr.x + 6f), style = Stroke(width = 10f + 8f * p), blendMode = BlendMode.Plus)
            drawContent()
            // crisp pulsing rim
            drawRoundRect(color.copy(alpha = 0.55f + 0.4f * p), cornerRadius = cr, style = Stroke(width = 2.5f + 1.5f * p))
            // sheen sweeping left to right
            val x = (t * 1.5f - 0.25f) * w
            drawRoundRect(Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.13f), Color.Transparent), startX = x - w * 0.12f, endX = x + w * 0.12f), cornerRadius = cr, blendMode = BlendMode.Plus)
            // twinkling stars on two corners
            for (i in 0 until 2) {
                val ph = (t * 2f + i * 0.5f) % 1f
                val r = 7f * sin(ph * 3.1415927f)
                val c = if (i == 0) Offset(w - 14f, 12f) else Offset(16f, h - 12f)
                drawLine(Color.White, Offset(c.x - r, c.y), Offset(c.x + r, c.y), strokeWidth = 2.2f, cap = StrokeCap.Round, blendMode = BlendMode.Plus)
                drawLine(Color.White, Offset(c.x, c.y - r), Offset(c.x, c.y + r), strokeWidth = 2.2f, cap = StrokeCap.Round, blendMode = BlendMode.Plus)
            }
        }
    }

/** Makes a buy button breathe so it draws the eye. */
fun Modifier.readyPulse(enabled: Boolean): Modifier =
    if (!enabled) this else composed {
        val inf = rememberInfiniteTransition(label = "readyPulse")
        val p by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "p")
        this.graphicsLayer { val k = 1f + 0.06f * p; scaleX = k; scaleY = k }
    }

/** Small glowing "READY TO BUY" pill. */
@Composable
fun ReadyTag(text: String = "READY TO BUY", color: Color = READY_GREEN) {
    val inf = rememberInfiniteTransition(label = "readyTag")
    val p by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(800, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "p")
    Text(
        "✨ $text", color = Color(0xFF05060F), fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, fontSize = 9.sp,
        modifier = Modifier.graphicsLayer { val k = 1f + 0.05f * p; scaleX = k; scaleY = k }
            .clip(RoundedCornerShape(6.dp)).background(color.copy(alpha = 0.8f + 0.2f * p)).padding(horizontal = 7.dp, vertical = 2.dp)
    )
}
