package com.neonrush.game.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.sin

/**
 * BAR POLISH (Stage 167). One effect for the home-screen bars (Upgrade Bay, Armory / Magazine, Boss Themes, Species Codex):
 * a breathing glow on the border, a slow light sweep across the bar, and a small squash of the content while it is pressed.
 * Use it right after the bar's own `.border(...)`:   .border(2.dp, col, shape).barFx(col)   (the bar keeps its own clip, background and click).
 * [phase] 0..3 offsets the timing so four bars on one screen do not all pulse together.
 */
fun Modifier.barFx(accent: Color, corner: Dp = 16.dp, phase: Int = 0): Modifier = composed {
    val inf = rememberInfiniteTransition(label = "barfx")
    val sweep by inf.animateFloat(-0.35f, 1.35f, infiniteRepeatable(tween(3600 + phase * 500, easing = LinearEasing), RepeatMode.Restart), label = "sweep")
    val pulse by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(1700 + phase * 300, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulse")
    var pressed by remember { mutableStateOf(false) }
    val sc by animateFloatAsState(if (pressed) 0.975f else 1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium), label = "press")
    this
        .pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                pressed = true
                waitForUpOrCancellation()
                pressed = false
            }
        }
        .graphicsLayer { scaleX = sc; scaleY = sc }
        .drawWithContent {
            drawContent()
            val w = size.width
            val cx = sweep * w
            drawRect(
                Brush.horizontalGradient(
                    listOf(Color.Transparent, accent.copy(alpha = 0.20f), Color.White.copy(alpha = 0.12f), accent.copy(alpha = 0.20f), Color.Transparent),
                    startX = cx - w * 0.22f, endX = cx + w * 0.22f
                )
            )
            val r = corner.toPx()
            drawRoundRect(accent.copy(alpha = 0.22f + 0.38f * pulse), cornerRadius = CornerRadius(r, r), style = Stroke(width = (1.5f + 2f * pulse).dp.toPx()))
        }
}

/** A small round badge with a moving symbol, for the left end of a bar: it bobs gently, or turns slowly when [spin] is true. */
@androidx.compose.runtime.Composable
fun BarGlyph(glyph: String, accent: Color, spin: Boolean = false) {
    val inf = rememberInfiniteTransition(label = "glyph")
    val t by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Restart), label = "gt")
    val bob = sin(t * 2f * PI.toFloat())
    Box(
        Modifier.padding(end = 10.dp).size(40.dp)
            .graphicsLayer { translationY = bob * 2.5.dp.toPx(); rotationZ = if (spin) t * 360f else bob * 8f }
            .clip(CircleShape)
            .background(Brush.radialGradient(listOf(accent.copy(alpha = 0.50f), accent.copy(alpha = 0.08f))))
            .border(1.5.dp, accent.copy(alpha = 0.85f), CircleShape),
        contentAlignment = Alignment.Center
    ) { Text(glyph, fontSize = 20.sp) }
}
