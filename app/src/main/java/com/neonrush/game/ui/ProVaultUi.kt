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
            }
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { open = !open }) {
            Text(if (isPro) "🗝️" else "🔒", fontSize = 22.sp,
                modifier = Modifier.graphicsLayer { rotationZ = sin(t * tau) * (if (isPro) 5f else 8f) })
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(if (isPro) "YOUR PRO VAULT" else "PRO VAULT  •  LOCKED", color = accent, fontWeight = FontWeight.Black, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                Text(
                    if (isPro) (if (perks.rank.label.isNotEmpty()) "${perks.rank.emoji} ${perks.rank.label} Pro • ${perks.months} month${if (perks.months == 1) "" else "s"}" else "Everything Pro gives you, in one place")
                    else "See what you are missing every day and month",
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
                    Text(if (!isPro) "Plus +75 every single day" else if (perks.stipendClaimable) "Ready to claim" else "Claimed this month", color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                }
                if (isPro && perks.stipendClaimable) {
                    Text("CLAIM", color = Color.Black, fontWeight = FontWeight.Black, fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                        modifier = Modifier.graphicsLayer { val k = 1f + 0.06f * glow; scaleX = k; scaleY = k }
                            .clip(RoundedCornerShape(16.dp)).background(Brush.horizontalGradient(listOf(VGOLD, Color(0xFFFFF59D)))).clickable { onClaimStipend() }.padding(horizontal = 14.dp, vertical = 6.dp))
                }
            }
            VaultRow("⚡", "MORE RUNS EVERY DAY", "Storm Run 6 a day, Ghost Duel 6 plus a free bonus rematch", isPro, 6)
            VaultRow("🏁", "CLOSER CHECKPOINTS", "Saved every 35 zones, not 45", isPro, 6)
            VaultRow("🔥", "LEGENDARY DIFFICULTY", "Pro-only top difficulty, plus Pro trails and early access to new hulls", isPro, 6)
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
