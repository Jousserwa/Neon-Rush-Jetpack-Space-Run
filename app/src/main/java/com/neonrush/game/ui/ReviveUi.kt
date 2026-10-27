package com.neonrush.game.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * The line on the revive screen: "I CAN'T GIVE UP". Letters pop in one by one, then ride a gentle wave while a gold-magenta-cyan
 * gradient runs through them; a glow breathes behind, sparks rise, and an energy bar sweeps under the words.
 */
@Composable
fun CantGiveUpTitle(modifier: Modifier = Modifier) {
    val text = "I CAN'T GIVE UP"
    val inf = rememberInfiniteTransition(label = "cgu")
    val t by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart), label = "t")
    val beat by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart), label = "beat")
    val entry = remember { Animatable(0f) }
    LaunchedEffect(Unit) { entry.animateTo(text.length.toFloat() + 6f, tween(text.length * 55 + 300, easing = LinearEasing)) }
    val tau = 6.2831855f
    val pulse = 0.5f + 0.5f * sin(beat * tau)
    Column(
        modifier = modifier.drawBehind {
            val c = Offset(size.width / 2f, size.height * 0.45f)
            drawCircle(Brush.radialGradient(listOf(Color(0xFFFF4DD2).copy(alpha = 0.20f + 0.14f * pulse), Color.Transparent), center = c, radius = size.width * 0.55f), radius = size.width * 0.55f, center = c, blendMode = BlendMode.Plus)
            for (i in 0 until 9) {
                val ph = (t * 1.3f + i * 0.111f) % 1f
                drawCircle(Color(0xFFFFD23F).copy(alpha = ((1f - ph) * 0.8f).coerceIn(0f, 1f)), radius = 2f + 2f * ((i * 7) % 3) / 2f,
                    center = Offset(size.width * (0.08f + 0.84f * (((i * 41) % 100) / 100f)) + sin(ph * tau + i) * 6f, size.height * (0.85f - 0.9f * ph)), blendMode = BlendMode.Plus)
            }
        }.graphicsLayer { val k = 1f + 0.025f * pulse; scaleX = k; scaleY = k },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            text.forEachIndexed { i, ch ->
                if (ch == ' ') { Spacer(Modifier.width(10.dp)); return@forEachIndexed }
                val a = (entry.value - i).coerceIn(0f, 1f)
                val hue = (t * 1.0f + i * 0.06f) % 1f
                val colA = lerpTri(hue)
                val colB = lerpTri((hue + 0.25f) % 1f)
                Text(
                    ch.toString(),
                    style = TextStyle(brush = Brush.verticalGradient(listOf(colA, colB)), fontSize = 28.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace),
                    modifier = Modifier.graphicsLayer {
                        alpha = a
                        translationY = (1f - a) * -26f + sin((t * tau) - i * 0.55f) * 5f * a
                        val s = 0.6f + 0.4f * a
                        scaleX = s; scaleY = s
                    }
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        // energy bar under the words
        Box(Modifier.width(200.dp).height(5.dp).drawBehind {
            drawRoundRect(Color.White.copy(alpha = 0.12f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height, size.height))
            val x = ((t * 1.4f) % 1f) * (size.width * 1.4f) - size.width * 0.2f
            drawRoundRect(Brush.horizontalGradient(listOf(Color.Transparent, Color(0xFFFFD23F), Color(0xFFFF4DD2), Color.Transparent), startX = x - size.width * 0.2f, endX = x + size.width * 0.2f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height, size.height))
        })
    }
}

/** gold -> magenta -> cyan -> gold ring, for the running gradient. */
private fun lerpTri(h: Float): Color {
    val gold = Color(0xFFFFD23F); val mag = Color(0xFFFF4DD2); val cy = Color(0xFF3DE8FF)
    val x = (h % 1f) * 3f
    return when {
        x < 1f -> androidx.compose.ui.graphics.lerp(gold, mag, x)
        x < 2f -> androidx.compose.ui.graphics.lerp(mag, cy, x - 1f)
        else -> androidx.compose.ui.graphics.lerp(cy, gold, x - 2f)
    }
}
