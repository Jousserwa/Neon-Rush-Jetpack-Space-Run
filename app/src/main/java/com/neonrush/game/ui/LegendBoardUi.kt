package com.neonrush.game.ui

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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neonrush.game.LegendTrack
import kotlin.math.sin

private fun DrawScope.flame(cx: Float, bottom: Float, wid: Float, hgt: Float, sway: Float, col: Color) {
    val p = Path().apply {
        moveTo(cx + sway, bottom - hgt)
        cubicTo(cx + wid * 0.9f, bottom - hgt * 0.55f, cx + wid, bottom - hgt * 0.15f, cx, bottom)
        cubicTo(cx - wid, bottom - hgt * 0.15f, cx - wid * 0.9f, bottom - hgt * 0.55f, cx + sway, bottom - hgt)
        close()
    }
    drawPath(p, col)
}

/** A small living flame for the Legendary board: rank 1 burns white-gold and tall, ranks 2-3 burn orange, the rest glow red. */
@Composable
fun FlameBadge(rank: Int, modifier: Modifier = Modifier) {
    val inf = rememberInfiniteTransition(label = "flame")
    val t by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart), label = "t")
    val outer = when { rank == 1 -> Color(0xFFFFB300); rank <= 3 -> Color(0xFFFF6D00); else -> Color(0xFFE53935) }
    val mid = when { rank == 1 -> Color(0xFFFFE082); rank <= 3 -> Color(0xFFFFA726); else -> Color(0xFFFF7043) }
    val core = when { rank == 1 -> Color.White; rank <= 3 -> Color(0xFFFFF59D); else -> Color(0xFFFFCC80) }
    val scale = when { rank == 1 -> 1f; rank <= 3 -> 0.9f; else -> 0.78f }
    Canvas(modifier) {
        val w = size.width; val h = size.height
        val f1 = sin(t * 6.2832f); val f2 = sin(t * 12.566f + 1f)
        val hh = h * 0.92f * scale * (1f + 0.07f * f2)
        drawCircle(Brush.radialGradient(listOf(outer.copy(alpha = 0.35f), Color.Transparent)), w * 0.6f, Offset(w / 2f, h * 0.65f))
        flame(w / 2f, h * 0.98f, w * 0.34f * scale, hh, f1 * w * 0.05f, outer)
        flame(w / 2f, h * 0.98f, w * 0.23f * scale, hh * 0.74f, f1 * w * 0.04f, mid)
        flame(w / 2f, h * 0.98f, w * 0.12f * scale, hh * 0.46f, f1 * w * 0.03f, core)
    }
}

/** Banner on top of the Legendary board: living flames, the title, and the viewer's own best Legendary zone. */
@Composable
fun LegendBoardBanner(modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val inf = rememberInfiniteTransition(label = "legBoard")
    val t by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Restart), label = "t")
    val pulse = 0.5f + 0.5f * sin(t * 6.2832f)
    val shape = RoundedCornerShape(14.dp)
    val best = LegendTrack.best()
    Row(
        modifier.fillMaxWidth().clip(shape)
            .background(Brush.horizontalGradient(listOf(Color(0xFF2A0A05), Color(0xFF5A1A06), Color(0xFF2A0A05))))
            .border(2.dp, Color(0xFFFF9800).copy(alpha = 0.5f + 0.4f * pulse), shape).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FlameBadge(1, Modifier.size(width = 34.dp, height = 44.dp))
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("THE LEGENDARY FLAME BOARD", color = Color(0xFFFFD23F), fontSize = 13.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, letterSpacing = 1.sp)
            Text(if (best > 0) "Only Pro pilots burn here. Your deepest zone: $best" else "Only Pro pilots burn here. Fly Legendary to light your flame.", color = Color.White.copy(alpha = 0.85f), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
        }
        Spacer(Modifier.width(8.dp))
        FlameBadge(1, Modifier.size(width = 34.dp, height = 44.dp))
    }
}
