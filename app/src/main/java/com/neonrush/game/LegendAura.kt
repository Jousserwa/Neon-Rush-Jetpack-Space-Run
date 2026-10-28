package com.neonrush.game.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

private val LGOLD = Color(0xFFFFD23F)
private val LFIRE = Color(0xFFFF6D00)

/**
 * LEGEND AURA: the look and sound of a LEGENDARY run (Pro only). It sits behind the game elements, so it never hides an obstacle:
 *  - a pulsing gold-and-fire glow around the screen edges that grows with speed,
 *  - embers rising and streaming past, thin gold speed rays,
 *  - a shock ring from the pilot every 10 zones (with a chime),
 *  - a "LEGENDARY" banner that blazes in at the start of the run and fades,
 *  - a rising three-note sting when the run starts.
 * [onSting] is called with 1 (run start) or 2 (every 10th zone); the ViewModel plays the sound.
 */
@Composable
fun LegendAura(zone: Int, speedKmh: Int, pilotY: Int, onSting: (Int) -> Unit) {
    val inf = rememberInfiniteTransition(label = "legend")
    val t by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(3000, easing = LinearEasing), RepeatMode.Restart), label = "t")
    val ring = remember { Animatable(1f) }
    val banner = remember { Animatable(0f) }
    LaunchedEffect(Unit) { onSting(1); banner.snapTo(0f); banner.animateTo(1f, tween(500)); delay(2200); banner.animateTo(2f, tween(900)) }
    val startBucket = remember { zone / 10 }
    LaunchedEffect(zone / 10) { if (zone / 10 != startBucket) { onSting(2); ring.snapTo(0f); ring.animateTo(1f, tween(1400)) } }
    val speed = ((speedKmh - 100) / 260f).coerceIn(0f, 1f)
    val tau = 6.2831855f
    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width; val h = size.height
            val pulse = 0.5f + 0.5f * sin(t * tau)
            // edge glow: transparent in the middle, gold-fire at the rim
            val rim = 0.16f + 0.10f * speed + 0.05f * pulse
            drawRect(Brush.radialGradient(listOf(Color.Transparent, Color.Transparent, LFIRE.copy(alpha = rim * 0.6f), LGOLD.copy(alpha = rim)), center = Offset(w / 2f, h / 2f), radius = w * 0.75f), blendMode = BlendMode.Plus)
            // speed rays from the right edge
            for (i in 0 until 9) {
                val ph = (t * (1.4f + 0.8f * speed) + i * 0.111f) % 1f
                val y = h * (0.1f + 0.8f * (((i * 53) % 100) / 100f))
                val x = w * (1.05f - 1.1f * ph)
                drawLine(LGOLD.copy(alpha = (0.35f * (1f - ph) * (0.4f + speed)).coerceIn(0f, 1f)), Offset(x, y), Offset(x + w * (0.10f + 0.12f * speed), y), strokeWidth = 1.6f, cap = StrokeCap.Round, blendMode = BlendMode.Plus)
            }
            // embers
            for (i in 0 until 26) {
                val ph = (t * (0.6f + 0.5f * (i % 3)) + i * 0.0385f) % 1f
                val x = w * (((i * 37) % 100) / 100f) - ph * w * 0.25f
                val xx = ((x % w) + w) % w
                val y = h * (1.02f - ph * 1.04f)
                drawCircle(Color(0xFFFFB74D).copy(alpha = ((1f - ph) * 0.8f).coerceIn(0f, 1f)), radius = 1.5f + 2.2f * ((i * 7) % 4) / 3f, center = Offset(xx + sin(ph * tau * 2f + i) * 8f, y), blendMode = BlendMode.Plus)
            }
            // shock ring from the pilot at each 10th zone
            val r = ring.value
            if (r < 1f) {
                val c = Offset(w * 0.2f, h * (pilotY / 100f))
                drawCircle(LGOLD.copy(alpha = (1f - r) * 0.8f), radius = w * (0.05f + 0.7f * r), center = c, style = Stroke(width = 6f * (1f - r) + 1.5f), blendMode = BlendMode.Plus)
                drawCircle(LFIRE.copy(alpha = (1f - r) * 0.5f), radius = w * (0.03f + 0.55f * r), center = c, style = Stroke(width = 3f), blendMode = BlendMode.Plus)
            }
        }
        // start-of-run banner
        val b = banner.value
        if (b in 0.01f..1.99f) {
            val inA = b.coerceAtMost(1f)
            val outA = if (b > 1f) 2f - b else 1f
            Text(
                "🔥 LEGENDARY 🔥", color = LGOLD, fontSize = 24.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, letterSpacing = 3.sp,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 120.dp)
                    .graphicsLayer { alpha = (inA * outA); val k = 1.35f - 0.35f * inA + 0.04f * sin(t * tau * 3f); scaleX = k; scaleY = k }
            )
        }
    }
}

/** HUD tag used instead of the plain "LEG" label while flying LEGENDARY: a small flame badge that breathes. */
@Composable
fun LegendHudTag() {
    val inf = rememberInfiniteTransition(label = "legendTag")
    val t by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart), label = "t")
    val f = 0.5f + 0.5f * sin(t * 6.2831855f)
    Row(
        Modifier.clip(RoundedCornerShape(8.dp)).background(Brush.horizontalGradient(listOf(LFIRE.copy(alpha = 0.35f + 0.2f * f), LGOLD.copy(alpha = 0.25f + 0.15f * f))))
            .border(1.dp, LGOLD.copy(alpha = 0.5f + 0.4f * f), RoundedCornerShape(8.dp)).padding(horizontal = 6.dp, vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("🔥", fontSize = 11.sp, modifier = Modifier.graphicsLayer { val k = 1f + 0.18f * f; scaleX = k; scaleY = k })
        Text("LEGEND", color = LGOLD, fontSize = 11.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
    }
}
