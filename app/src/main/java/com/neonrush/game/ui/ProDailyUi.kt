package com.neonrush.game.ui

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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neonrush.game.ProDaily
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

private val PGOLD = Color(0xFFFFD23F)
private val PMAG = Color(0xFFFF4DD2)
private val PCYAN = Color(0xFF3DE8FF)

/**
 * Home card for the Pro daily gems.
 *  - Pro, not collected: big pulsing "TAP TO COLLECT +75" card (shimmer, rising sparkles, bobbing chest, glowing border).
 *  - Pro, collected: calm gold card "COLLECTED TODAY - next in Xh" with the running total.
 *  - Free: a locked, softly glowing teaser ("PRO MEMBERS GET +75 gems EVERY DAY") that opens the Pro offer.
 */
@Composable
fun ProDailyCard(isPro: Boolean, onCollect: () -> Unit, onUpgrade: () -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    @Suppress("UNUSED_VARIABLE") val v = ProDaily.version
    val can = isPro && ProDaily.canClaim(ctx)
    val inf = rememberInfiniteTransition(label = "proDaily")
    val t by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart), label = "t")
    val fast by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(800, easing = LinearEasing), RepeatMode.Restart), label = "f")
    val tau = 6.2831855f
    val pop = remember { Animatable(0.85f) }
    LaunchedEffect(Unit) { pop.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow)) }
    val shape = RoundedCornerShape(16.dp)
    val accent = if (isPro) PGOLD else PMAG
    val pulse = if (can) 0.5f + 0.5f * sin(fast * tau) else 0.35f + 0.25f * sin(t * tau)

    Column(
        modifier = modifier.fillMaxWidth().graphicsLayer { scaleX = pop.value * (1f + (if (can) 0.015f * pulse else 0f)); scaleY = scaleX; alpha = pop.value.coerceIn(0f, 1f) }
            .clip(shape)
            .background(Brush.horizontalGradient(listOf(accent.copy(alpha = 0.10f + 0.10f * pulse), PCYAN.copy(alpha = 0.06f), accent.copy(alpha = 0.10f + 0.10f * pulse))))
            .border(2.dp, Brush.linearGradient(listOf(accent.copy(alpha = 0.4f + 0.6f * pulse), Color(0xFFFFF59D), PCYAN.copy(alpha = 0.5f))), shape)
            .clickable { if (!isPro) onUpgrade() else if (can) onCollect() }
            .drawWithContent {
                drawContent()
                val w = size.width; val h = size.height
                val x = (t * 1.7f - 0.35f) * w
                drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha = if (can) 0.20f else 0.10f), Color.Transparent), startX = x - w * 0.15f, endX = x + w * 0.15f), blendMode = BlendMode.Plus)
                if (can) for (i in 0 until 12) {
                    val ph = (t * 1.6f + i * 0.083f) % 1f
                    val px = w * (0.06f + 0.88f * (((i * 41) % 100) / 100f)) + sin(ph * tau + i) * 6f
                    drawCircle(PGOLD.copy(alpha = ((1f - ph) * 0.9f).coerceIn(0f, 1f)), radius = 2f + 2.5f * ((i * 7) % 5) / 4f, center = Offset(px, h * (1f - ph)), blendMode = BlendMode.Plus)
                }
            }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            val icon = if (!isPro) "🔒" else if (can) "🎁" else "✅"
            Text(icon, fontSize = 30.sp,
                modifier = Modifier.graphicsLayer {
                    if (can) { translationY = -abs(sin(fast * 3.1415927f)) * 9f; rotationZ = sin(t * tau * 2f) * 8f }
                    else if (!isPro) { rotationZ = sin(t * tau) * 6f }
                })
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    if (!isPro) "PRO MEMBERS GET +${ProDaily.GEMS} 💎 EVERY DAY" else if (can) "PRO DAILY GEMS  •  TAP TO COLLECT" else "PRO DAILY GEMS  •  COLLECTED",
                    color = accent, fontWeight = FontWeight.Black, fontSize = 12.sp, fontFamily = FontFamily.Monospace
                )
                if (isPro) {
                    val big = if (can) 1f + 0.07f * sin(fast * tau) else 1f
                    Text(if (can) "+${ProDaily.GEMS} 💎" else "+${ProDaily.GEMS} 💎  next in ${ProDaily.hoursToNext()}h",
                        color = Color.White, fontWeight = FontWeight.Black, fontSize = if (can) 26.sp else 15.sp, fontFamily = FontFamily.Monospace,
                        modifier = Modifier.graphicsLayer { scaleX = big; scaleY = big; transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f) })
                } else {
                    Text("That is ${ProDaily.GEMS * 30} 💎 a month. Tap to see Pro.", color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                }
            }
        }
        if (isPro) {
            val total = ProDaily.collected(ctx)
            if (total > 0) Text("You have collected $total 💎 as Pro (${ProDaily.daysCollected(ctx)} days)", color = Color.White.copy(alpha = 0.65f), fontSize = 10.sp,
                textAlign = TextAlign.Center, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(top = 4.dp))
        } else {
            Text("Uncollected days are lost. Pro never misses one.", color = PMAG.copy(alpha = 0.8f), fontSize = 10.sp, textAlign = TextAlign.Center, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(top = 4.dp))
        }
    }
}
