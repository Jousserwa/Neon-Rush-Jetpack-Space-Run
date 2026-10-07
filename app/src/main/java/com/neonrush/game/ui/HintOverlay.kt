package com.neonrush.game.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neonrush.game.HintState

private val HintCyan = Color(0xFF00E5FF)
private val HintGreen = Color(0xFF69F0AE)
private val HintYellow = Color(0xFFFFEB3B)
private val HintOrange = Color(0xFFFF7043)

/**
 * Non-blocking onboarding overlay. It has no pointer handlers, so every touch
 * falls through to the steering drag underneath. Place it as a sibling of the
 * drag Box, inside the gameplay Box.
 *
 * Polish: prompt slides in, a thin countdown line shows the time left, a
 * pulsing ring marks the thing the prompt is about, success pops with a
 * spring + haptic tick, and a missed dodge turns the pill orange.
 */
@Composable
fun HintOverlay(
    hint: HintState,
    userYPos: Int,
    modifier: Modifier = Modifier
) {
    val lesson = hint.lesson
    val prompt = hint.promptText
    val success = hint.successText
    val haptic = LocalHapticFeedback.current

    // Keep the last text around so the fade-out doesn't flash an empty pill.
    var lastPrompt by remember { mutableStateOf("") }
    var lastSuccess by remember { mutableStateOf("") }
    LaunchedEffect(prompt) { if (prompt.isNotEmpty()) lastPrompt = prompt }
    LaunchedEffect(success) {
        if (success.isNotEmpty()) {
            lastSuccess = success
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    // Countdown line: 1 -> 0 over the lesson's timeout (real time).
    val remaining = remember { Animatable(1f) }
    LaunchedEffect(hint.startedAtMs, lesson) {
        if (lesson != null && hint.timeoutMs > 0L) {
            remaining.snapTo(1f)
            remaining.animateTo(0f, tween(hint.timeoutMs.toInt(), easing = LinearEasing))
        }
    }

    // Success pop.
    val pop = remember { Animatable(1f) }
    LaunchedEffect(success) {
        if (success.isNotEmpty()) {
            pop.snapTo(0.65f)
            pop.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMedium))
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val boxHeight = maxHeight
        val lowFuel = lesson == "LOWFUEL"
        val accent = when {
            hint.lost -> HintOrange
            lowFuel -> HintYellow
            else -> HintCyan
        }

        // Ring on the thing the prompt is about (gem, fuel cell, power-up, obstacle).
        if (lesson != null && hint.targetX in -0.05f..1.1f && hint.targetX >= 0f) {
            val t = rememberInfiniteTransition(label = "ring")
            val pulse by t.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(800, easing = LinearEasing), RepeatMode.Restart),
                label = "ringPulse"
            )
            val ringColor = if (lesson == "DODGE") HintOrange else HintGreen
            Canvas(modifier = Modifier.fillMaxSize()) {
                val c = Offset(size.width * hint.targetX, size.height * (hint.targetY / 100f))
                val base = 24.dp.toPx()
                drawCircle(
                    color = ringColor.copy(alpha = 0.9f),
                    radius = base,
                    center = c,
                    style = Stroke(width = 2.5.dp.toPx())
                )
                drawCircle(
                    color = ringColor.copy(alpha = (1f - pulse) * 0.7f),
                    radius = base + pulse * 22.dp.toPx(),
                    center = c,
                    style = Stroke(width = 2.dp.toPx())
                )
            }
        }

        // Prompt pill: top of the play area, or bottom (pointing at the fuel
        // bar) for the refuel lesson.
        AnimatedVisibility(
            visible = lesson != null && prompt.isNotEmpty(),
            enter = fadeIn(tween(220)) + slideInVertically(tween(260, easing = FastOutSlowInEasing)) { if (lowFuel) it / 2 else -it / 2 },
            exit = fadeOut(tween(260)) + slideOutVertically(tween(260)) { if (lowFuel) it / 3 else -it / 3 },
            modifier = Modifier
                .align(if (lowFuel) Alignment.BottomCenter else Alignment.TopCenter)
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            HintPill(
                text = prompt.ifEmpty { lastPrompt } + if (lowFuel) "  ▼" else "",
                accent = accent,
                progress = remaining.value
            )
        }

        // STEER: animated finger sliding up and down, with faint up/down chevrons.
        if (lesson == "STEER") {
            val t = rememberInfiniteTransition(label = "finger")
            val dy by t.animateFloat(
                initialValue = -44f,
                targetValue = 44f,
                animationSpec = infiniteRepeatable(
                    animation = tween(750, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "fingerY"
            )
            Text(
                text = "▲",
                color = HintCyan.copy(alpha = 0.5f),
                fontSize = 18.sp,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 62.dp)
                    .offset(y = (-64).dp)
            )
            Text(
                text = "▼",
                color = HintCyan.copy(alpha = 0.5f),
                fontSize = 18.sp,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 62.dp)
                    .offset(y = 64.dp)
            )
            Text(
                text = "👆",
                fontSize = 44.sp,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 40.dp)
                    .offset(y = dy.dp)
            )
        }

        // DODGE: arrow beside the ship pointing toward the safe direction.
        if (lesson == "DODGE" && hint.arrow != 0) {
            val t = rememberInfiniteTransition(label = "arrow")
            val a by t.animateFloat(
                initialValue = 0.4f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(350),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "arrowAlpha"
            )
            // Ship sits at 20% of the width; offset the arrow a ship-height up/down.
            val centerY = boxHeight * (userYPos / 100f) + boxHeight * 0.17f * hint.arrow
            Text(
                text = if (hint.arrow < 0) "▲" else "▼",
                color = HintCyan.copy(alpha = a),
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = maxWidth * 0.2f - 12.dp)
                    .offset(y = centerY - 20.dp)
            )
        }

        // Success banner: "✓ Nice!" or the power-up's effect.
        AnimatedVisibility(
            visible = success.isNotEmpty(),
            enter = fadeIn(tween(120)),
            exit = fadeOut(tween(300)),
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 16.dp)
                .graphicsLayer { scaleX = pop.value; scaleY = pop.value }
        ) {
            HintPill(text = success.ifEmpty { lastSuccess }, accent = HintGreen, progress = null)
        }
    }
}

@Composable
private fun HintPill(text: String, accent: Color, progress: Float?) {
    val t = rememberInfiniteTransition(label = "pill")
    val glow by t.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pillGlow"
    )
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF0B0B14).copy(alpha = 0.82f))
            .border(1.5.dp, accent.copy(alpha = glow), RoundedCornerShape(16.dp))
            .padding(horizontal = 18.dp, vertical = 11.dp)
    ) {
        Column(modifier = Modifier.width(IntrinsicSize.Max)) {
            Text(
                text = text,
                color = Color.White,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            if (progress != null) {
                Spacer(modifier = Modifier.height(7.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .clip(RoundedCornerShape(1.dp))
                        .background(Color.White.copy(alpha = 0.12f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress.coerceIn(0f, 1f))
                            .height(2.dp)
                            .background(accent.copy(alpha = 0.9f))
                    )
                }
            }
        }
    }
}
