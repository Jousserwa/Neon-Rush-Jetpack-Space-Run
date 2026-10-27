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
