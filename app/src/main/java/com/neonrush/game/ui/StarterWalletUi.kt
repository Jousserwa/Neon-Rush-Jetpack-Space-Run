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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.neonrush.game.StarterWallet
import com.neonrush.game.StarterWalletHost
import kotlin.math.roundToInt
import kotlin.math.sin

/** Shown once per starter day. Mount it once near the top of the app. Animated: spring pop, counting gems, sparkles, glow, pulsing button. */
@Composable
fun StarterWalletDialog() {
    val t = StarterWalletHost.pending ?: return
    val gold = Color(0xFFFFD23F)
    val pop = remember(t) { Animatable(0.6f) }
    val count = remember(t) { Animatable(0f) }
    LaunchedEffect(t) { pop.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow)) }
    LaunchedEffect(t) { count.animateTo((if (t.day == 1) t.wallet else t.added).toFloat(), tween(1300, delayMillis = 300, easing = FastOutSlowInEasing)) }
    val inf = rememberInfiniteTransition(label = "starter")
    val p by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart), label = "p")
    val tau = 6.2831855f
    val shape = RoundedCornerShape(18.dp)
    Dialog(onDismissRequest = { StarterWalletHost.pending = null }) {
        Column(
            Modifier.fillMaxWidth().graphicsLayer { scaleX = pop.value; scaleY = pop.value; alpha = pop.value.coerceIn(0f, 1f) }
                .clip(shape).background(Color(0xF0080C1E))
                .border(2.dp, gold.copy(alpha = 0.45f + 0.4f * (0.5f + 0.5f * sin(p * tau))), shape)
                .drawWithContent {
                    drawContent()
                    val w = size.width; val h = size.height
                    drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.10f), Color.Transparent), startX = (p * 1.6f - 0.3f) * w - w * 0.15f, endX = (p * 1.6f - 0.3f) * w + w * 0.15f), blendMode = BlendMode.Plus)
                    for (i in 0 until 12) {
                        val ph = (p * 1.3f + i * 0.083f) % 1f
                        drawCircle(gold.copy(alpha = (1f - ph) * 0.8f), radius = 2f + 2.5f * ((i * 7) % 4) / 3f,
                            center = Offset(w * (0.06f + 0.88f * (((i * 41) % 100) / 100f)) + sin(ph * tau + i) * 6f, h * (1f - ph)), blendMode = BlendMode.Plus)
                    }
                }
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            PilotPose(Pose.CHEER, 120.dp)
            Text(if (t.day == 1) "WELCOME, PILOT!" else "WALLET REFILLED", color = gold, fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace, fontSize = 20.sp, modifier = Modifier.padding(top = 6.dp))
            Text(
                (if (t.day == 1) "${count.value.roundToInt()} 💎" else "+${count.value.roundToInt()} 💎"),
                color = Color.White, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, fontSize = 34.sp,
                modifier = Modifier.padding(top = 6.dp).graphicsLayer { val k = if (count.value >= (if (t.day == 1) t.wallet else t.added).toFloat() - 0.5f) 1f + 0.05f * sin(p * tau * 2f) else 1f; scaleX = k; scaleY = k }
            )
            Text(
                if (t.day == 1) "Your starter wallet. Enough to refuel again and again while you learn the ropes."
                else "Your wallet is back to ${t.wallet} 💎",
                color = Color.White, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp)
            )
            Row(modifier = Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                for (d in 1..StarterWallet.DAYS) {
                    val now = d == t.day
                    Box(Modifier.size((if (now) 14f + 3f * (0.5f + 0.5f * sin(p * tau * 2f)) else 12f).dp).clip(CircleShape)
                        .background(if (d <= t.day) Color(0xFF69F0AE) else Color.White.copy(alpha = 0.18f)))
                }
            }
            Text("Starter day ${t.day} of ${StarterWallet.DAYS}", color = Color(0xFF69F0AE), fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
            Text(
                if (t.day >= StarterWallet.DAYS) "This was the last free top-up. From now on your gems are yours: earn more from runs, crates, Storm Run and Ghost Duel."
                else "Spent some? Come back tomorrow and it fills up again, up to ${StarterWallet.FLOOR}. It never stacks.",
                color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp)
            )
            Text("LET'S GO", color = Color.Black, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, fontSize = 15.sp,
                modifier = Modifier.padding(top = 16.dp).graphicsLayer { val k = 1f + 0.04f * (0.5f + 0.5f * sin(p * tau * 2f)); scaleX = k; scaleY = k }
                    .clip(RoundedCornerShape(24.dp)).background(gold)
                    .clickable { StarterWalletHost.pending = null }.padding(horizontal = 34.dp, vertical = 11.dp))
        }
    }
}
