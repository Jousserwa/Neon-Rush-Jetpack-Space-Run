package com.neonrush.game.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neonrush.game.HintState

/**
 * Non-blocking onboarding overlay. Has no pointer handlers, so every touch
 * falls through to the steering drag underneath. Place it as a sibling of the
 * drag Box, inside the gameplay Box.
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

    // Keep the last text around so the fade-out doesn't flash an empty pill.
    var lastPrompt by remember { mutableStateOf("") }
    var lastSuccess by remember { mutableStateOf("") }
    LaunchedEffect(prompt) { if (prompt.isNotEmpty()) lastPrompt = prompt }
    LaunchedEffect(success) { if (success.isNotEmpty()) lastSuccess = success }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val boxHeight = maxHeight
        val lowFuel = lesson == "LOWFUEL"

        // Prompt pill: top of the play area, or bottom (pointing at the fuel
        // bar) for the refuel lesson.
        AnimatedVisibility(
            visible = lesson != null && prompt.isNotEmpty(),
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(if (lowFuel) Alignment.BottomCenter else Alignment.TopCenter)
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            HintPill(
                text = prompt.ifEmpty { lastPrompt } + if (lowFuel) "  ▼" else "",
                accent = if (lowFuel) Color(0xFFFFEB3B) else Color(0xFF00E5FF)
            )
        }

        // STEER: animated finger sliding up and down.
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
                color = Color(0xFF00E5FF).copy(alpha = a),
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
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 16.dp)
        ) {
            HintPill(text = success.ifEmpty { lastSuccess }, accent = Color(0xFF69F0AE))
        }
    }
}

@Composable
private fun HintPill(text: String, accent: Color) {
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
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF0B0B14).copy(alpha = 0.78f))
            .border(1.5.dp, accent.copy(alpha = glow), RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Text(
            text = text,
            color = Color.White,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp
        )
    }
}
