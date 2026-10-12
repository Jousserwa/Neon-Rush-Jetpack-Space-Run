// StoryBanner.kt
package com.neonrush.game

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.sin

/**
 * Comic-book caption bubble for story beats.
 *  - compact (max ~78% of the width), slightly see-through, never intercepts touches, so it does not block play
 *  - pops in with a spring, text types itself out, holds for a time that matches the length, then fades
 *  - speech-bubble tail, halftone dots, hard drop shadow, skewed world-name tag, tilt of -1.5 degrees
 *  - boss warnings shake and turn red; "to be continued" teasers get their own label
 * Same signature as before, so NeonRushApp.kt needs no change.
 */

private fun accentColorFor(worldId: Int): Color = when (worldId) {
    1 -> Color(0xFFFF3D9A) // Blackout Front: pink
    2 -> Color(0xFF00E5FF) // Derelict Signal: cyan
    3 -> Color(0xFFB388FF) // Cell Block Zero: violet
    4 -> Color(0xFF69F0AE) // Green Hell: acid green
    5 -> Color(0xFFFF9100)
    6 -> Color(0xFFFFD23F)
    7 -> Color(0xFFFF5252)
    8 -> Color(0xFFE0E0FF)
    9 -> Color(0xFF1DE9B6)
    10 -> Color(0xFFFFAB40)
    11 -> Color(0xFFFF4081)
    else -> Color(0xFF00E5FF)
}

private fun labelFor(type: StoryBeatType): String = when (type) {
    StoryBeatType.OPENING -> "SECTOR ENTERED"
    StoryBeatType.MID_RUN -> "SIGNAL INTERCEPTED"
    StoryBeatType.BOSS_INTRO -> "WARNING"
    StoryBeatType.ENDING -> "SECTOR CLEARED"
    StoryBeatType.PAYWALL_TEASER -> "TO BE CONTINUED..."
}

@Composable
fun StoryBannerHost(
    storyEvent: kotlinx.coroutines.flow.SharedFlow<StoryEvent>,
    modifier: Modifier = Modifier
) {
    var activeEvent by remember { mutableStateOf<StoryEvent?>(null) }
    var visible by remember { mutableStateOf(false) }
    var shownChars by remember { mutableStateOf(0) }
    var shakeT by remember { mutableStateOf(0f) }

    LaunchedEffect(Unit) {
        storyEvent.collect { event ->
            activeEvent = event
            shownChars = 0
            visible = true
            delay(260)                                   // let the pop-in land first
            val total = event.text.length
            val perChar = if (total > 140) 14L else 20L  // long lines type faster
            for (i in 1..total) { shownChars = i; delay(perChar) }
            if (event.type == StoryBeatType.BOSS_INTRO) {
                // short shake after the line is complete
                for (k in 0..14) { shakeT = k / 14f; delay(30) }
                shakeT = 0f
            }
            delay((1500L + total * 12L).coerceIn(1800L, 3800L))   // reading time after the line is complete
            visible = false
            delay(450)                                   // let the exit finish before clearing
            activeEvent = null
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 96.dp, start = 16.dp, end = 16.dp),
        contentAlignment = Alignment.BottomStart
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(160)) + scaleIn(
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                initialScale = 0.55f, transformOrigin = TransformOrigin(0.1f, 1f)
            ),
            exit = fadeOut(tween(380)) + scaleOut(tween(380), targetScale = 0.92f, transformOrigin = TransformOrigin(0.1f, 1f))
        ) {
            activeEvent?.let { event ->
                val boss = event.type == StoryBeatType.BOSS_INTRO
                val accent = if (boss) Color(0xFFFF3B3B) else accentColorFor(event.world.id)
                val shake = if (boss) sin(shakeT * 6.2832f * 3f) * (1f - shakeT) * 5f else 0f
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.78f)
                        .padding(top = 14.dp, bottom = 18.dp)
                        .graphicsLayer { rotationZ = -1.5f; translationX = shake * density }
                ) {
                    // ---- the bubble
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .drawBehind { drawBubble(accent) }
                            .padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 12.dp)
                    ) {
                        Text(
                            text = labelFor(event.type),
                            color = accent,
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black,
                                fontSize = 10.sp, letterSpacing = 2.sp
                            )
                        )
                        Text(
                            text = event.text.take(shownChars),
                            color = Color.White,
                            style = TextStyle(
                                fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.ExtraBold,
                                fontStyle = FontStyle.Italic, fontSize = 15.sp, lineHeight = 19.sp,
                                shadow = Shadow(Color.Black, Offset(1.5f, 1.5f), 2f)
                            ),
                            modifier = Modifier.padding(top = 3.dp)
                        )
                    }
                    // ---- skewed world-name tag overlapping the top-left corner
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .offset(x = (-4).dp, y = (-12).dp)
                            .drawBehind { drawTag(accent) }
                            .padding(horizontal = 14.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = event.world.title.uppercase(),
                            color = Color(0xFF0A0A12),
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black,
                                fontSize = 11.sp, letterSpacing = 1.5.sp
                            ),
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ drawing helpers

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawBubble(accent: Color) {
    val r = 14.dp.toPx()
    val tailH = 15.dp.toPx()
    val w = size.width
    val h = size.height
    val body = Path().apply { addRoundRect(RoundRect(0f, 0f, w, h, CornerRadius(r, r))) }
    val tail = Path().apply {
        moveTo(22.dp.toPx(), h - 2.dp.toPx())
        lineTo(52.dp.toPx(), h - 2.dp.toPx())
        lineTo(14.dp.toPx(), h + tailH)
        close()
    }
    val shape = Path.combine(PathOperation.Union, body, tail)

    // hard comic drop shadow
    translate(left = 5.dp.toPx(), top = 5.dp.toPx()) { drawPath(shape, accent.copy(alpha = 0.28f)) }
    // see-through dark fill, so the track stays visible behind it
    drawPath(shape, Color(0xD90A0A14))
    // halftone dots, top-right corner, clipped to the bubble
    clipPath(body) {
        val step = 9.dp.toPx()
        var row = 0
        var y = 4.dp.toPx()
        while (y < h * 0.7f) {
            var x = w - 4.dp.toPx() - (row % 2) * step / 2
            var col = 0
            while (x > w * 0.55f) {
                val fade = (1f - (w - x) / (w * 0.45f)) * (1f - y / (h * 0.7f))
                val rad = 2.6.dp.toPx() * fade.coerceIn(0f, 1f)
                if (rad > 0.4f) drawCircle(accent.copy(alpha = 0.22f), rad, Offset(x, y))
                x -= step; col++
            }
            y += step * 0.8f; row++
        }
    }
    // outline
    drawPath(shape, accent, style = Stroke(width = 2.5.dp.toPx()))
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawTag(accent: Color) {
    val skew = 8.dp.toPx()
    val tag = Path().apply {
        moveTo(skew, 0f); lineTo(size.width, 0f); lineTo(size.width - skew, size.height); lineTo(0f, size.height); close()
    }
    translate(left = 3.dp.toPx(), top = 3.dp.toPx()) { drawPath(tag, Color.Black.copy(alpha = 0.6f)) }
    drawPath(tag, accent)
}
