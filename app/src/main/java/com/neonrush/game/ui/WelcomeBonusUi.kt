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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.Canvas
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private val BGOLD = Color(0xFFFFD23F)

/** Animated daily welcome bonus (game-over screen): pop-in, bobbing gift, counting gems, shimmer sweep, rising sparkles, 10-day dots. */
@Composable
fun DailyBonusCard(label: String) {
    val lines = label.split("\n")
    val head = lines.firstOrNull().orEmpty()
    val dayM = Regex("DAY (\\d+)/(\\d+)").find(head)
    val gems = Regex("\\+(\\d+)").find(head)?.groupValues?.get(1)?.toIntOrNull() ?: 0
    val special = head.contains("SPECIAL")
    val day = if (special) 10 else dayM?.groupValues?.get(1)?.toIntOrNull() ?: 0
    val total = dayM?.groupValues?.get(2)?.toIntOrNull() ?: 10
    val rest = lines.drop(1).filter { it.isNotBlank() }

    val pop = remember(label) { Animatable(0.7f) }
    val count = remember(label) { Animatable(0f) }
    LaunchedEffect(label) {
        pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow))
    }
    LaunchedEffect(label) { count.animateTo(gems.toFloat(), tween(1200, delayMillis = 250, easing = FastOutSlowInEasing)) }

    val inf = rememberInfiniteTransition(label = "dailyBonus")
    val t by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Restart), label = "t")
    val fast by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart), label = "f")
    val tau = 6.2831855f
    val shape = RoundedCornerShape(16.dp)

    Column(
        modifier = Modifier.fillMaxWidth().graphicsLayer { scaleX = pop.value; scaleY = pop.value; alpha = pop.value.coerceIn(0f, 1f) }
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0x40FFD23F), Color(0x14FFD23F))))
            .border(2.dp, Brush.linearGradient(listOf(BGOLD.copy(alpha = 0.4f + 0.5f * sin(t * tau).coerceAtLeast(0f)), Color(0xFFFFF59D), BGOLD.copy(alpha = 0.5f))), shape)
            .drawWithContent {
                drawContent()
                val w = size.width; val h = size.height
                // shimmer sweep
                val x = (t * 1.6f - 0.3f) * w
                drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.16f), Color.Transparent), startX = x - w * 0.15f, endX = x + w * 0.15f), blendMode = BlendMode.Plus)
                // rising gem sparkles
                for (i in 0 until 9) {
                    val ph = (t * 1.5f + i * 0.111f) % 1f
                    val px = w * (0.08f + 0.84f * (((i * 37) % 100) / 100f)) + sin(ph * tau + i) * 6f
                    val py = h * (1f - ph)
                    val r = 2.2f + 2.2f * ((i * 13) % 5) / 4f
                    drawCircle(BGOLD.copy(alpha = (1f - ph) * 0.85f), radius = r, center = Offset(px, py), blendMode = BlendMode.Plus)
                }
            }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Text(if (special) "🌟" else "🎁", fontSize = 34.sp,
                modifier = Modifier.graphicsLayer { translationY = -kotlin.math.abs(sin(fast * 3.1415927f)) * 10f; rotationZ = sin(t * tau * 2f) * 8f })
            Spacer(Modifier.width(10.dp))
            Column {
                Text(if (special) "DAY $total SPECIAL BONUS RECEIVED" else "DAILY BONUS RECEIVED  •  DAY $day/$total", color = BGOLD, fontWeight = FontWeight.Black, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                val big = if (count.value >= gems.toFloat() - 0.5f) 1f + 0.06f * sin(t * tau * 2f) else 1f
                Text("+${count.value.roundToInt()} 💎", color = Color.White, fontWeight = FontWeight.Black, fontSize = 28.sp, fontFamily = FontFamily.Monospace,
                    modifier = Modifier.graphicsLayer { scaleX = big; scaleY = big; transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f) })
            }
        }
        // "received" stamp: slams down onto the card
        val stamp = remember(label) { Animatable(2.4f) }
        LaunchedEffect(label) { delay(450); stamp.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = Spring.StiffnessMedium)) }
        Text("\u2705 RECEIVED  +$gems \uD83D\uDC8E", color = Color.Black, fontWeight = FontWeight.Black, fontSize = 12.sp, fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(top = 8.dp)
                .graphicsLayer { val k = stamp.value; scaleX = k; scaleY = k; rotationZ = -4f; alpha = ((2.4f - k) / 1.4f).coerceIn(0f, 1f) }
                .clip(RoundedCornerShape(10.dp)).background(Brush.horizontalGradient(listOf(Color(0xFF69F0AE), BGOLD))).padding(horizontal = 12.dp, vertical = 4.dp))
        // the gems splash across the screen for 3 seconds, then fade
        var splash by remember(label) { mutableStateOf(gems > 0) }
        if (splash) GemSplash(gems) { splash = false }
        // 10 day dots
        Row(modifier = Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
            for (d in 1..total) {
                val now = d == day
                val sz = if (now) (9f + 3f * (0.5f + 0.5f * sin(fast * tau))).dp else 8.dp
                Box(Modifier.size(sz).clip(CircleShape).background(if (d <= day) BGOLD else Color.White.copy(alpha = 0.18f)))
            }
        }
        val next = head.substringAfter("(", "").substringBefore(")", "")
        if (next.isNotEmpty()) Text(next, color = Color.White.copy(alpha = 0.75f), fontSize = 11.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(top = 6.dp))
        rest.forEach { Text(it, color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp, textAlign = TextAlign.Center, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(top = 3.dp)) }
    }
}

/** Home screen: the expiring bonus gems chip. Calm when there is time, pulses red-gold when under 6 hours. */
@Composable
fun BonusExpiryChip(amount: Int, hoursLeft: Int) {
    val inf = rememberInfiniteTransition(label = "expiry")
    val p by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(if (hoursLeft <= 6) 700 else 1800), RepeatMode.Reverse), label = "p")
    val urgent = hoursLeft <= 6
    val col = if (urgent) Color(0xFFFF8A65) else BGOLD
    Text(
        text = "🎁 $amount bonus expire in ${hoursLeft}h",
        fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = col,
        modifier = Modifier.graphicsLayer { val k = 1f + (if (urgent) 0.06f else 0.02f) * p; scaleX = k; scaleY = k; alpha = 0.75f + 0.25f * p }
            .clip(RoundedCornerShape(8.dp)).background(col.copy(alpha = 0.10f + 0.10f * p)).padding(horizontal = 6.dp, vertical = 1.dp)
    )
}

/**
 * Full-screen gem splash for a won bonus: "+N gems" pops in big at the centre, gem shapes burst outward in a ring with trails, then everything
 * fades. Lasts 3 seconds, then calls [onDone]. It is a small non-focusable window in the middle, so it never blocks the buttons for long.
 */
@Composable
fun GemSplash(amount: Int, onDone: () -> Unit) {
    val prog = remember { Animatable(0f) }
    LaunchedEffect(Unit) { prog.animateTo(1f, tween(3000, easing = LinearEasing)); onDone() }
    val p = prog.value
    val inP = (p / 0.14f).coerceIn(0f, 1f)
    val pop = if (inP < 1f) 0.4f + 0.75f * inP else 1.15f - 0.15f * ((p - 0.14f) / 0.12f).coerceIn(0f, 1f)
    val fade = if (p < 0.72f) 1f else (1f - (p - 0.72f) / 0.28f).coerceIn(0f, 1f)
    Popup(alignment = Alignment.Center, properties = PopupProperties(focusable = false, dismissOnClickOutside = false, clippingEnabled = false)) {
        Box(Modifier.size(340.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val c = Offset(size.width / 2f, size.height / 2f)
                val burst = (p / 0.55f).coerceIn(0f, 1f)
                val ease = 1f - (1f - burst) * (1f - burst)
                // expanding ring
                drawCircle(BGOLD.copy(alpha = ((1f - burst) * 0.7f * fade).coerceIn(0f, 1f)), radius = size.width * (0.1f + 0.4f * ease), center = c,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 5f * (1f - burst) + 1f), blendMode = BlendMode.Plus)
                // 18 gems flying outward, each with a short trail, drifting down a little as they fade
                for (i in 0 until 18) {
                    val ang = i * (6.2831855f / 18f) + (i % 3) * 0.12f
                    val dist = size.width * (0.14f + (0.30f + 0.12f * ((i * 7) % 3)) * ease)
                    val gx = c.x + cos(ang) * dist
                    val gy = c.y + sin(ang) * dist + p * size.height * 0.06f
                    val r = size.width * (0.020f + 0.012f * ((i * 5) % 3))
                    val a = (fade * (1f - 0.4f * burst)).coerceIn(0f, 1f)
                    drawLine(BGOLD.copy(alpha = a * 0.5f), Offset(c.x + cos(ang) * dist * 0.7f, c.y + sin(ang) * dist * 0.7f), Offset(gx, gy), strokeWidth = r * 0.5f, cap = androidx.compose.ui.graphics.StrokeCap.Round, blendMode = BlendMode.Plus)
                    val gem = Path().apply { moveTo(gx, gy - r); lineTo(gx + r * 0.8f, gy); lineTo(gx, gy + r); lineTo(gx - r * 0.8f, gy); close() }
                    val col = if (i % 3 == 0) Color(0xFF3DE8FF) else if (i % 3 == 1) BGOLD else Color(0xFFFF4DD2)
                    drawPath(gem, col.copy(alpha = a))
                    drawPath(gem, Color.White.copy(alpha = a * 0.8f), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5f))
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.graphicsLayer { scaleX = pop; scaleY = pop; alpha = fade; translationY = -p * 40f }) {
                Text("DAILY BONUS", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, letterSpacing = 3.sp,
                    style = TextStyle(shadow = Shadow(Color.Black, Offset(0f, 2f), 6f)))
                Text("+$amount \uD83D\uDC8E", color = BGOLD, fontSize = 48.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace,
                    style = TextStyle(shadow = Shadow(Color(0xFFFF8F00), Offset.Zero, 24f)))
                Text("WON!", color = Color(0xFF69F0AE), fontSize = 16.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, letterSpacing = 4.sp,
                    style = TextStyle(shadow = Shadow(Color.Black, Offset(0f, 2f), 6f)))
            }
        }
    }
}
