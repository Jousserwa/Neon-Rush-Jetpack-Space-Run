package com.neonrush.game.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neonrush.game.DailyQuote
import kotlin.math.floor
import kotlin.math.sin

/**
 * The daily encouragement, alive: the pilot waves beside it, the words appear one by one, a light runs around the border, a shimmer
 * sweeps over the text and sparkles drift up. mode = "day" (Home) | "win" | "loss" (game over).
 * FREE players get only the first part of the home quote on some days, with a lock chip that opens the Pro offer; Pro always reads it all.
 */
@Composable
fun QuoteLine(mode: String = "day", modifier: Modifier = Modifier, isPro: Boolean = true, onUpgrade: () -> Unit = {}) {
    val ctx = LocalContext.current
    val full = remember(mode) { DailyQuote.today(ctx, mode) }
    val cut = mode == "day" && !isPro && DailyQuote.isTeaserDay()
    val text = if (cut) DailyQuote.teaser(full) else full
    val words = remember(text) { text.split(" ") }
    val typed = remember(text) { Animatable(0f) }   // typewriter: how many letters are on the page
    LaunchedEffect(text) { typed.snapTo(0f); typed.animateTo(text.length.toFloat(), tween((text.length * 48).coerceAtLeast(700), easing = LinearEasing)) }
    val inf = rememberInfiniteTransition(label = "quote")
    val t by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(3600, easing = LinearEasing), RepeatMode.Restart), label = "t")
    val lap by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(5200, easing = LinearEasing), RepeatMode.Restart), label = "lap")
    val col = when (mode) { "win" -> Color(0xFFFFD23F); "loss" -> Color(0xFFFF8AD8); else -> Color(0xFF7DF9FF) }
    val pose = when (mode) { "win" -> Pose.THUMB; "loss" -> Pose.BOOST; else -> Pose.CHEER }
    val tau = 6.2831855f
    val breathe = 0.5f + 0.5f * sin(t * tau)
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = modifier.fillMaxWidth().clip(shape)
            .background(Color(0xFF080620))   // solid dark paper so the words never melt into the background
            .background(Brush.horizontalGradient(listOf(col.copy(alpha = 0.10f + 0.06f * breathe), col.copy(alpha = 0.24f + 0.10f * breathe), col.copy(alpha = 0.10f + 0.06f * breathe))))
            .border(2.dp, col.copy(alpha = 0.55f + 0.4f * breathe), shape)
            .then(if (cut) Modifier.clickable { onUpgrade() } else Modifier)
            .drawWithContent {
                drawContent()
                val w = size.width; val h = size.height
                val x = (t * 1.5f - 0.25f) * w
                drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.10f), Color.Transparent), startX = x - w * 0.12f, endX = x + w * 0.12f), blendMode = BlendMode.Plus)
                for (i in 0 until 7) {                                   // sparkles drifting up
                    val ph = (t * 1.2f + i * 0.143f) % 1f
                    drawCircle(col.copy(alpha = ((1f - ph) * 0.7f).coerceIn(0f, 1f)), radius = 1.8f + 1.8f * ((i * 5) % 3) / 2f,
                        center = Offset(w * (0.18f + 0.78f * (((i * 29) % 100) / 100f)) + sin(ph * tau + i) * 5f, h * (1.02f - ph)), blendMode = BlendMode.Plus)
                }
                travelBorder(lap, col, 16.dp.toPx(), 2.dp.toPx())
            }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PilotPose(pose, 64.dp)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (mode == "day") "✨ WORD OF THE DAY ✨" else if (mode == "win") "⭐ WELL DONE ⭐" else "💪 GET BACK UP 💪",
                color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, letterSpacing = 2.sp,
                modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(Brush.horizontalGradient(listOf(col, Color.White, col))).padding(horizontal = 12.dp, vertical = 3.dp))
            Spacer(Modifier.height(6.dp))
            val n = typed.value.toInt()
            val pal = listOf(Color.White, Color(0xFF7DF9FF), Color(0xFFFF8AD8), Color(0xFFFFD23F), Color(0xFF69F0AE))
            val sh = t * pal.size * 2f
            val base = floor(sh).toInt(); val fr = sh - base
            val keyIdx = words.indices.maxByOrNull { words[it].length } ?: 0      // the longest word is the star of the line
            val cursorOn = ((t * 10f).toInt() % 2) == 0
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = col, fontSize = 22.sp, fontWeight = FontWeight.Black)) { append("“") }
                    var used = 0
                    words.forEachIndexed { i, wd ->
                        val shown = (n - used).coerceIn(0, wd.length)
                        val c = if (i == keyIdx) lerp(Color(0xFFFFD23F), Color.White, 0.5f + 0.5f * sin(t * tau * 2f)) else lerp(pal[(i + base) % pal.size], pal[(i + base + 1) % pal.size], fr)
                        if (shown > 0) withStyle(SpanStyle(color = c, fontWeight = if (i == keyIdx) FontWeight.Black else FontWeight.Bold, fontSize = if (i == keyIdx) 18.sp else 16.sp)) { append(wd.substring(0, shown)) }
                        if (shown < wd.length) withStyle(SpanStyle(color = Color.Transparent)) { append(wd.substring(shown)) }
                        used += wd.length + 1
                        if (i < words.size - 1) append(" ")
                    }
                    if (cut) withStyle(SpanStyle(color = col)) { append("…") } else withStyle(SpanStyle(color = col, fontSize = 22.sp, fontWeight = FontWeight.Black)) { append("”") }
                    withStyle(SpanStyle(color = if (cursorOn) col else Color.Transparent, fontWeight = FontWeight.Black)) { append("▌") }   // typewriter cursor
                },
                style = TextStyle(fontSize = 16.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, lineHeight = 23.sp,
                    shadow = Shadow(col.copy(alpha = 0.8f), Offset.Zero, 10f))
            )
            if (cut) {
                Text("🔒 PRO READS THE WHOLE QUOTE", color = Color.Black, fontSize = 9.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(top = 6.dp).graphicsLayer { val k = 1f + 0.05f * breathe; scaleX = k; scaleY = k }
                        .clip(RoundedCornerShape(10.dp)).background(Brush.horizontalGradient(listOf(Color(0xFFFF4DD2), Color(0xFFFFD23F)))).padding(horizontal = 10.dp, vertical = 3.dp))
            }
        }
    }
}
