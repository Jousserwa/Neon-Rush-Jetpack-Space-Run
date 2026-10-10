package com.neonrush.game.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neonrush.game.AuraRank

private fun rankColor(r: AuraRank) = when (r) {
    AuraRank.BRONZE -> Color(0xFFCD7F32)
    AuraRank.SILVER -> Color(0xFFC0C8D8)
    AuraRank.GOLD -> Color(0xFFFFD23F)
    AuraRank.DIAMOND -> Color(0xFF7DF9FF)
    AuraRank.NONE -> Color.Transparent
}

/** Small pill: "🥇 GOLD PRO". Diamond shimmers. Use next to names on profile, share card, leaderboard. */
@Composable
fun AuraBadge(rank: AuraRank, modifier: Modifier = Modifier, fontSize: TextUnit = 10.sp) {
    if (rank == AuraRank.NONE) return
    val c = rankColor(rank)
    val t = rememberInfiniteTransition(label = "aura")
    val shimmer by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2200, easing = LinearEasing), RepeatMode.Reverse), label = "s")
    val border = if (rank == AuraRank.DIAMOND)
        Brush.horizontalGradient(listOf(Color(0xFF7DF9FF), Color(0xFFFF7BF5).copy(alpha = 0.6f + 0.4f * shimmer), Color(0xFF7DF9FF)))
    else Brush.horizontalGradient(listOf(c, c))
    Box(modifier.background(c.copy(alpha = 0.14f), RoundedCornerShape(20.dp))
        .border(1.dp, border, RoundedCornerShape(20.dp)).padding(horizontal = 8.dp, vertical = 2.dp)) {
        Text("${rank.emoji} ${rank.label.uppercase()} PRO", color = c, fontSize = fontSize,
            fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black)
    }
}

/** Animated gradient player name for Pro (leaderboards, profile, pilot card). */
@Composable
fun ProName(name: String, legend: Boolean, modifier: Modifier = Modifier, fontSize: TextUnit = 14.sp) {
    val t = rememberInfiniteTransition(label = "proName")
    val shift by t.animateFloat(0f, 600f, infiniteRepeatable(tween(3500, easing = LinearEasing), RepeatMode.Restart), label = "g")
    val colors = if (legend) listOf(Color(0xFFFFD23F), Color(0xFFFF7BF5), Color(0xFF7DF9FF), Color(0xFFFFD23F))
                 else listOf(Color(0xFF00E5FF), Color(0xFFBC00DD), Color(0xFFFF007F), Color(0xFF00E5FF))
    Text(name, modifier = modifier, fontSize = fontSize, fontWeight = FontWeight.Black,
        style = TextStyle(brush = Brush.linearGradient(colors, start = androidx.compose.ui.geometry.Offset(shift, 0f),
            end = androidx.compose.ui.geometry.Offset(shift + 300f, 0f), tileMode = androidx.compose.ui.graphics.TileMode.Repeated)))
}

/** Annual / Year Edition title shown under the name. */
@Composable
fun LegendTitle(modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "legend")
    val a by t.animateFloat(0.65f, 1f, infiniteRepeatable(tween(1400), RepeatMode.Reverse), label = "a")
    Text("👑 LEGEND", modifier = modifier, color = Color(0xFFFFD23F).copy(alpha = a), fontSize = 10.sp,
        fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
}
