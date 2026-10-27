package com.neonrush.game.ui

import androidx.compose.animation.Crossfade
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.neonrush.game.HullCatalog
import com.neonrush.game.ProDaily
import com.neonrush.game.ProSnapshot
import java.util.Calendar
import java.util.TimeZone
import kotlin.math.sin

private val VGOLD = Color(0xFFFFD23F)
private val VMAG = Color(0xFFFF4DD2)
private val VCYAN = Color(0xFF3DE8FF)

private fun daysLeftInMonth(): Int {
    val c = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
    return (c.getActualMaximum(Calendar.DAY_OF_MONTH) - c.get(Calendar.DAY_OF_MONTH) + 1).coerceAtLeast(1)
}

/**
 * PRO VAULT: one glowing card on Home that always shows what Pro gives. Pro members see their live perks (this month's loaner hull
 * and how long it stays, the 30-gem monthly stipend to claim, aura rank). Free players see the same list locked, so they know exactly
 * what they are missing. Tap the header to fold it open or closed; the free card opens the Pro offer from its button.
 */
@Composable
fun ProVaultCard(perks: ProSnapshot, isPro: Boolean, onClaimStipend: () -> Unit, onUpgrade: () -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(!isPro) }
    val inf = rememberInfiniteTransition(label = "vault")
    val t by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(3000, easing = LinearEasing), RepeatMode.Restart), label = "t")
    val tau = 6.2831855f
    val glow = 0.5f + 0.5f * sin(t * tau)
    val lap by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(4200, easing = LinearEasing), RepeatMode.Restart), label = "lap")
    val tick by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(12000, easing = LinearEasing), RepeatMode.Restart), label = "tick")
    val flame = 0.5f + 0.5f * sin(t * tau * 3f)
    val accent = if (isPro) VGOLD else VMAG
    val shape = RoundedCornerShape(16.dp)
    val loanerName = perks.loanerHullId?.let { HullCatalog.byId(it)?.name ?: it.replace('_', ' ') }

    Column(
        modifier = modifier.fillMaxWidth().clip(shape)
            .background(Brush.verticalGradient(listOf(accent.copy(alpha = 0.10f + 0.06f * glow), Color(0x14000000))))
            .border(1.5.dp, Brush.linearGradient(listOf(accent.copy(alpha = 0.5f + 0.4f * glow), VCYAN.copy(alpha = 0.4f), accent.copy(alpha = 0.5f))), shape)
            .drawWithContent {
                drawContent()
                val w = size.width
                val x = (t * 1.6f - 0.3f) * w
                drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.08f), Color.Transparent), startX = x - w * 0.12f, endX = x + w * 0.12f), blendMode = BlendMode.Plus)
                travelBorder(lap, accent, 16.dp.toPx(), 2.5.dp.toPx())
            }
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { open = !open }) {
            PilotPose(if (isPro) Pose.THUMB else Pose.BOOST, 52.dp)
            Spacer(Modifier.width(6.dp))
            Column(Modifier.weight(1f)) {
                Text(if (isPro) "YOUR PRO VAULT" else "PRO VAULT  •  LOCKED", color = accent, fontWeight = FontWeight.Black, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                val perksTicker = listOf("\uD83D\uDC8E +50 gems every day", "\uD83D\uDE80 a new hull every month", "\u26A1 more Storm and Duel runs", "\uD83C\uDFC1 checkpoints every 35 zones", "\uD83D\uDD25 LEGENDARY: a whole other Neon Rush")
                if (!isPro) {
                    val idx = ((tick * perksTicker.size * 5f).toInt() / 5).coerceIn(0, perksTicker.size - 1)   // each line holds ~2.4 s
                    Crossfade(targetState = idx, animationSpec = tween(500), label = "ticker") { i ->
                        Text(perksTicker[i], color = Color.White.copy(alpha = 0.85f), fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    }
                } else Text(
                    if (isPro) (if (perks.rank.label.isNotEmpty()) "${perks.rank.emoji} ${perks.rank.label} Pro • ${perks.months} month${if (perks.months == 1) "" else "s"}" else "Everything Pro gives you, in one place")
                    else "",
                    color = Color.White.copy(alpha = 0.75f), fontSize = 10.sp, fontFamily = FontFamily.Monospace
                )
            }
            Text(if (open) "▲" else "▼", color = accent, fontSize = 12.sp)
        }
        if (open) {
            Spacer(Modifier.height(8.dp))
            // monthly loaner hull
            VaultRow("🚀", "MONTHLY LOANER HULL",
                if (isPro) (if (loanerName != null) "$loanerName • swaps in ${daysLeftInMonth()}d" else "A new hull to fly every month") else "A different premium hull to fly every month",
                isPro)
            // stipend
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("💎 MONTHLY GEMS +30", color = if (isPro) Color.White else Color.White.copy(alpha = 0.5f), fontWeight = FontWeight.Bold, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    Text(if (!isPro) "Plus +${ProDaily.GEMS} every single day" else if (perks.stipendClaimable) "Ready to claim" else "Claimed this month", color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                }
                if (isPro && perks.stipendClaimable) {
                    Text("CLAIM", color = Color.Black, fontWeight = FontWeight.Black, fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                        modifier = Modifier.graphicsLayer { val k = 1f + 0.06f * glow; scaleX = k; scaleY = k }
                            .clip(RoundedCornerShape(16.dp)).background(Brush.horizontalGradient(listOf(VGOLD, Color(0xFFFFF59D)))).clickable { onClaimStipend() }.padding(horizontal = 14.dp, vertical = 6.dp))
                }
            }
            VaultRow("⚡", "MORE RUNS EVERY DAY", "Storm Run 6 a day, Ghost Duel 6 plus a free bonus rematch", isPro, 6)
            VaultRow("🏁", "CLOSER CHECKPOINTS", "Saved every 35 zones, not 45", isPro, 6)
            // LEGENDARY: more than a harder level, a different Neon Rush. Gets its own burning banner.
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(12.dp))
                    .background(Brush.horizontalGradient(listOf(Color(0xFFFF6D00).copy(alpha = 0.18f + 0.12f * flame), VGOLD.copy(alpha = 0.14f + 0.10f * flame), Color(0xFFFF6D00).copy(alpha = 0.18f + 0.12f * flame))))
                    .border(1.dp, VGOLD.copy(alpha = 0.4f + 0.4f * flame), RoundedCornerShape(12.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("\uD83D\uDD25", fontSize = 24.sp, modifier = Modifier.graphicsLayer { val k = 1f + 0.14f * flame; scaleX = k; scaleY = k; rotationZ = sin(t * tau * 3f) * 5f })
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (isPro) "LEGENDARY  \u2022  YOURS" else "LEGENDARY  \u2022  PRO ONLY", color = VGOLD, fontWeight = FontWeight.Black, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                    Text("More than a harder level: a whole other Neon Rush, with its own rewards, aura and powers.", color = Color.White.copy(alpha = 0.85f), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                }
            }
            VaultRow("🎨", "PRO TRAILS & EARLY HULLS", "Pro-only trails and 4 days early access to every new hull", isPro, 6)
            if (!isPro) {
                Text("UNLOCK PRO", color = Color.Black, fontWeight = FontWeight.Black, fontSize = 13.sp, fontFamily = FontFamily.Monospace, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(top = 10.dp).fillMaxWidth().graphicsLayer { val k = 1f + 0.02f * glow; scaleX = k; scaleY = k }
                        .clip(RoundedCornerShape(20.dp)).background(Brush.horizontalGradient(listOf(VMAG, VGOLD))).clickable { onUpgrade() }.padding(vertical = 10.dp))
            }
        }
    }
}

@Composable
private fun VaultRow(icon: String, title: String, sub: String, unlocked: Boolean, topPad: Int = 6) {
    Row(Modifier.fillMaxWidth().padding(top = topPad.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(if (unlocked) icon else "🔒", fontSize = 16.sp)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = if (unlocked) Color.White else Color.White.copy(alpha = 0.5f), fontWeight = FontWeight.Bold, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            Text(sub, color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
        }
        if (unlocked) Text("✓", color = VGOLD, fontWeight = FontWeight.Black, fontSize = 14.sp)
    }
}
