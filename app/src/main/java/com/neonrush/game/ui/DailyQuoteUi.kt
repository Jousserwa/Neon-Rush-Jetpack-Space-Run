package com.neonrush.game.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neonrush.game.DailyQuote
import kotlin.math.sin

/** One encouraging line: fades and rises in, soft shimmer sweeps across it, gentle breathing glow. mode = "day" | "win" | "loss". */
@Composable
fun QuoteLine(mode: String = "day", modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val text = remember(mode) { DailyQuote.today(ctx, mode) }
    val inA = remember(text) { Animatable(0f) }
    LaunchedEffect(text) { inA.animateTo(1f, tween(900)) }
    val inf = rememberInfiniteTransition(label = "quote")
    val t by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(3200, easing = LinearEasing), RepeatMode.Restart), label = "t")
    val col = when (mode) { "win" -> Color(0xFFFFD23F); "loss" -> Color(0xFFFF8AD8); else -> Color(0xFF7DF9FF) }
    val breathe = 0.5f + 0.5f * sin(t * 6.2831855f)
    Box(
        modifier = modifier.fillMaxWidth().graphicsLayer { alpha = inA.value; translationY = (1f - inA.value) * 14f }
            .clip(RoundedCornerShape(12.dp))
            .background(Brush.horizontalGradient(listOf(col.copy(alpha = 0.04f + 0.05f * breathe), col.copy(alpha = 0.10f + 0.05f * breathe), col.copy(alpha = 0.04f + 0.05f * breathe))))
            .drawWithContent {
                drawContent()
                val w = size.width
                val x = (t * 1.5f - 0.25f) * w
                drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.10f), Color.Transparent), startX = x - w * 0.12f, endX = x + w * 0.12f), blendMode = BlendMode.Plus)
            }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text("“$text”", color = Color.White.copy(alpha = 0.92f), fontSize = 13.sp, fontStyle = FontStyle.Italic, fontWeight = FontWeight.Medium,
            fontFamily = FontFamily.Serif, textAlign = TextAlign.Center)
    }
}
