package com.neonrush.game.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neonrush.game.FusionCores
import kotlin.math.sin

private fun DrawScope.core(col: Color, cx: Float, cy: Float, r: Float, t: Float, glow: Float) {
    drawCircle(Brush.radialGradient(listOf(col.copy(alpha = 0.55f * glow), Color.Transparent), center = Offset(cx, cy), radius = r * 2.1f), r * 2.1f, Offset(cx, cy))
    drawCircle(Brush.radialGradient(listOf(Color.White, col, col.copy(alpha = 0.6f)), center = Offset(cx - r * 0.3f, cy - r * 0.3f), radius = r * 1.3f), r, Offset(cx, cy))
    drawCircle(col, r, Offset(cx, cy), style = Stroke(2.5f))
    // orbiting spark
    val a = t * 6.2832f
    drawCircle(Color.White, r * 0.12f, Offset(cx + kotlin.math.cos(a) * r * 1.25f, cy + sin(a) * r * 1.25f))
}

@Composable
private fun CoreOrb(kind: Int, size: Dp, t: Float, glow: Float = 1f) {
    Canvas(Modifier.size(size)) { core(Color(FusionCores.COLORS[kind]), this.size.width / 2f, this.size.height / 2f, this.size.minDimension * 0.28f, t + kind * 0.33f, glow) }
}

/** The Fusion Chamber: tap cores to put three in the slots, see what they will make, press FUSE. */
@Composable
fun FusionCard(onFuse: (List<Int>, (String, Int) -> Unit) -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    @Suppress("UNUSED_VARIABLE") val v = FusionCores.version
    val inf = rememberInfiniteTransition(label = "fusion")
    val t by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Restart), label = "t")
    val chosen = remember { mutableStateListOf<Int>() }
    var result by remember { mutableStateOf<Pair<String, Int>?>(null) }
    val pop = remember { Animatable(0f) }
    LaunchedEffect(result) { if (result != null) { pop.snapTo(0f); pop.animateTo(1f, tween(500)); kotlinx.coroutines.delay(3500); result = null } }
    val have = IntArray(3) { FusionCores.count(ctx, it) }
    val level = FusionCores.level(ctx)
    val ready = chosen.size == FusionCores.FUSE_COST
    val preview = if (ready) FusionCores.payout(chosen.toList()) else null
    val pulse = 0.5f + 0.5f * sin(t * 6.2832f)
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier.fillMaxWidth().clip(shape).background(Brush.verticalGradient(listOf(Color(0xFF1B1038), Color(0xFF0A0720))))
            .border(2.dp, Brush.horizontalGradient(listOf(Color(0xFF3DE8FF), Color(0xFFFF8A3D), Color(0xFFB388FF))), shape).padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("🧬", fontSize = 22.sp); Spacer(Modifier.width(8.dp))
            Text("PILOT FUSION", color = Color(0xFFFFD23F), fontSize = 13.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
            Text("FUSION LV $level", color = Color.White.copy(alpha = 0.8f), fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        }
        Text("Good runs (zone ${FusionCores.DROP_ZONE}+) can drop cores. Fuse ${FusionCores.FUSE_COST}: three of one kind = PURE +${FusionCores.PURE_GEMS}, three different = RAINBOW +${FusionCores.RAINBOW_GEMS}, otherwise MIXED +${FusionCores.MIXED_GEMS} 💎.",
            color = Color.White.copy(alpha = 0.75f), fontSize = 10.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(vertical = 6.dp))
        // inventory
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            for (k in 0..2) {
                val free = have[k] - chosen.count { it == k }
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(enabled = free > 0 && chosen.size < FusionCores.FUSE_COST) { chosen.add(k) }) {
                    CoreOrb(k, 54.dp, t, if (free > 0) 1f else 0.2f)
                    Text("${FusionCores.NAMES[k]} x$free", color = Color(FusionCores.COLORS[k]).copy(alpha = if (free > 0) 1f else 0.4f), fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        // chamber: three slots and the Fusion Level rings
        Box(Modifier.fillMaxWidth().height(70.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                for (i in 0 until level.coerceAtMost(5)) drawCircle(Color(0xFFFFD23F).copy(alpha = 0.10f + 0.12f * pulse), size.height * (0.30f + 0.07f * i), Offset(size.width / 2f, size.height / 2f), style = Stroke(1.5f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                for (i in 0 until FusionCores.FUSE_COST) {
                    val k = chosen.getOrNull(i)
                    Box(Modifier.size(52.dp).clip(RoundedCornerShape(26.dp)).background(Color.White.copy(alpha = 0.06f))
                        .border(1.5.dp, if (k != null) Color(FusionCores.COLORS[k]) else Color.White.copy(alpha = 0.2f), RoundedCornerShape(26.dp))
                        .clickable(enabled = k != null) { chosen.removeAt(i) }, contentAlignment = Alignment.Center) {
                        if (k != null) CoreOrb(k, 52.dp, t) else Text("+", color = Color.White.copy(alpha = 0.3f), fontSize = 20.sp)
                    }
                }
            }
        }
        val r = result
        if (r != null) {
            Text("${r.first} FUSION!  +${r.second} 💎", color = Color(0xFFFFD23F), fontSize = 16.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(top = 4.dp).graphicsLayer { val k = 0.6f + 0.4f * pop.value; scaleX = k; scaleY = k; alpha = pop.value })
        } else {
            Text(if (ready) "FUSE  →  ${preview!!.first} +${preview.second} 💎" else if (FusionCores.total(ctx) >= FusionCores.FUSE_COST) "Tap three cores to fill the slots" else "Collect ${FusionCores.FUSE_COST} cores to fuse",
                color = if (ready) Color.Black else Color.White.copy(alpha = 0.6f), fontSize = 12.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp).then(if (ready) Modifier.graphicsLayer { val k = 1f + 0.05f * pulse; scaleX = k; scaleY = k }.clip(RoundedCornerShape(12.dp)).background(Brush.horizontalGradient(listOf(Color(0xFF3DE8FF), Color(0xFFFFD23F), Color(0xFFFF4DD2)))).clickable {
                    val picks = chosen.toList(); chosen.clear(); onFuse(picks) { name, g -> result = name to g }
                }.padding(horizontal = 18.dp, vertical = 8.dp) else Modifier))
        }
    }
}
