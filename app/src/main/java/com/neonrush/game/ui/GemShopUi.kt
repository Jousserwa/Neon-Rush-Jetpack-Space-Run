package com.neonrush.game.ui

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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.sin

/**
 * One gem pack in the shop. [tag] is "" (plain), "POPULAR" (magenta) or "BEST" (gold). [bonusPct] is how many percent more gems per dollar
 * the pack gives than the smallest one (0 = no ribbon text). Tagged rows glow, shimmer, and the buy button pulses.
 */
@Composable
fun GemPackRow(amount: Int, price: String, tag: String, bonusPct: Int, onBuy: () -> Unit) {
    val inf = rememberInfiniteTransition(label = "gemPack")
    val t by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Restart), label = "t")
    val breathe = 0.5f + 0.5f * sin(t * 6.2831855f)
    val col = when (tag) { "BEST" -> Color(0xFFFFD23F); "POPULAR" -> Color(0xFFFF4DD2); else -> Color(0xFF3DE8FF) }
    val hot = tag.isNotEmpty()
    val shape = RoundedCornerShape(10.dp)
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        if (hot) {
            Text(
                (if (tag == "BEST") "👑 BEST VALUE" else "🔥 MOST POPULAR") + (if (bonusPct > 0) "  •  +$bonusPct% MORE GEMS" else ""),
                color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(start = 10.dp).graphicsLayer { val k = 1f + 0.04f * breathe; scaleX = k; scaleY = k; transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 1f) }
                    .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                    .background(Brush.horizontalGradient(listOf(col, Color.White.copy(alpha = 0.9f), col))).padding(horizontal = 10.dp, vertical = 2.dp)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().clip(shape)
                .background(if (hot) Brush.horizontalGradient(listOf(col.copy(alpha = 0.14f + 0.08f * breathe), Color(0x22000000))) else Brush.horizontalGradient(listOf(Color(0x22FFFFFF), Color(0x11FFFFFF))))
                .border(if (hot) 1.5.dp else 1.dp, col.copy(alpha = if (hot) 0.5f + 0.4f * breathe else 0.25f), shape)
                .drawWithContent {
                    drawContent()
                    if (hot) {
                        val w = size.width
                        val x = (t * 1.6f - 0.3f) * w
                        drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.12f), Color.Transparent), startX = x - w * 0.12f, endX = x + w * 0.12f), blendMode = BlendMode.Plus)
                    }
                }
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("💎 $amount Gems", fontWeight = FontWeight.Black, color = Color.White, fontSize = if (hot) 17.sp else 15.sp)
                Text("$amount gems for $price", color = Color.White.copy(alpha = 0.55f), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
            }
            Text(
                "BUY $price", color = Color.Black, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black, fontSize = 12.sp,
                modifier = Modifier.graphicsLayer { val k = if (hot) 1f + 0.05f * breathe else 1f; scaleX = k; scaleY = k }
                    .clip(RoundedCornerShape(8.dp)).background(Brush.horizontalGradient(listOf(col, Color.White.copy(alpha = 0.85f)))).clickable { onBuy() }.padding(horizontal = 16.dp, vertical = 9.dp)
            )
        }
    }
}

/**
 * The one-time Starter Pack card: gold comet border, shimmer, rising gem sparkles, the pilot cheering, and a pulsing buy button.
 * The text column takes the remaining width (weight), so the buy button can never be squeezed out.
 */
@Composable
fun StarterPackCard(amount: Int, price: String, perDollar: Int, onBuy: () -> Unit) {
    val inf = rememberInfiniteTransition(label = "starterPack")
    val t by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Restart), label = "t")
    val lap by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(3800, easing = LinearEasing), RepeatMode.Restart), label = "lap")
    val breathe = 0.5f + 0.5f * sin(t * 6.2831855f)
    val gold = Color(0xFFFFD23F)
    val shape = RoundedCornerShape(16.dp)
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(
            "\u26A1 ONE-TIME OFFER  \u2022  GONE ONCE BOUGHT", color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(start = 12.dp).graphicsLayer { val k = 1f + 0.04f * breathe; scaleX = k; scaleY = k; transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 1f) }
                .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)).background(Brush.horizontalGradient(listOf(gold, Color.White.copy(alpha = 0.9f), gold))).padding(horizontal = 10.dp, vertical = 2.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth().clip(shape)
                .background(Brush.horizontalGradient(listOf(gold.copy(alpha = 0.16f + 0.08f * breathe), Color(0x22FF4DD2), gold.copy(alpha = 0.12f + 0.06f * breathe))))
                .border(1.5.dp, gold.copy(alpha = 0.45f + 0.35f * breathe), shape)
                .drawWithContent {
                    drawContent()
                    val w = size.width; val h = size.height
                    val x = (t * 1.7f - 0.3f) * w
                    drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.14f), Color.Transparent), startX = x - w * 0.12f, endX = x + w * 0.12f), blendMode = BlendMode.Plus)
                    for (i in 0 until 10) {
                        val ph = (t * 1.4f + i * 0.1f) % 1f
                        drawCircle(gold.copy(alpha = ((1f - ph) * 0.85f).coerceIn(0f, 1f)), radius = 2f + 2f * ((i * 5) % 3) / 2f,
                            center = androidx.compose.ui.geometry.Offset(w * (0.08f + 0.84f * (((i * 37) % 100) / 100f)) + sin(ph * 6.2831855f + i) * 5f, h * (1.02f - ph)), blendMode = BlendMode.Plus)
                    }
                    travelBorder(lap, gold, 16.dp.toPx(), 2.5.dp.toPx())
                }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PilotPose(Pose.CHEER, 62.dp)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text("\uD83D\uDC8E $amount GEMS", color = Color.White, fontWeight = FontWeight.Black, fontSize = 20.sp, fontFamily = FontFamily.Monospace, lineHeight = 24.sp,
                    modifier = Modifier.graphicsLayer { val k = 1f + 0.03f * breathe; scaleX = k; scaleY = k; transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f) })
                Text("New pilot deal: $perDollar gems per \$1. Buy it once, the gems are yours to keep.", color = Color.White.copy(alpha = 0.75f), fontSize = 11.sp, lineHeight = 14.sp)
            }
            Spacer(Modifier.width(8.dp))
            Text(
                "BUY\n$price", color = Color.Black, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black, fontSize = 13.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center, lineHeight = 16.sp,
                modifier = Modifier.graphicsLayer { val k = 1f + 0.07f * breathe; scaleX = k; scaleY = k }
                    .clip(RoundedCornerShape(12.dp)).background(Brush.horizontalGradient(listOf(gold, Color(0xFFFF4DD2)))).clickable { onBuy() }.padding(horizontal = 14.dp, vertical = 8.dp)
            )
        }
    }
}
