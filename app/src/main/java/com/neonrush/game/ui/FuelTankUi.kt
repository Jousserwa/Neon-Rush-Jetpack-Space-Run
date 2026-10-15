package com.neonrush.game.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neonrush.game.FuelGate
import com.neonrush.game.NeonRushViewModel
import com.neonrush.game.db.GameProfile
import com.neonrush.game.ui.theme.CyberBackground
import com.neonrush.game.ui.theme.CyberOnSurface
import com.neonrush.game.ui.theme.CyberPrimary
import com.neonrush.game.ui.theme.CyberSecondary
import com.neonrush.game.ui.theme.CyberSurface
import kotlin.math.sin

private val TIER_NAMES = listOf("RESERVE", "EXTENDED", "LONG HAUL", "DEEP SPACE", "INFINITY")
private val TIER_COLORS = listOf(Color(0xFF69F0AE), Color(0xFF00E5FF), Color(0xFFFFC107), Color(0xFFFF6D00), Color(0xFFE040FB))
private val TIER_PRICES = listOf("$2.99", "$4.99", "$7.99", "$11.99", "$15.99")
private val TIER_IDS = listOf("fuel_tier_1", "fuel_tier_2", "fuel_tier_3", "fuel_tier_4", "fuel_tier_5")

/** A glass fuel tank: fills with tier colour, bubbles rise, cap and rim shine. Locked tanks are dark and empty. */
@Composable
private fun TankIcon(tier: Int, color: Color, owned: Boolean, locked: Boolean, phase: Float, modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width; val h = size.height
        val bodyW = w * 0.56f; val bodyH = h * 0.74f
        val left = (w - bodyW) / 2f; val top = h * 0.2f
        val corner = CornerRadius(bodyW * 0.22f)
        val col = if (locked) Color(0xFF5B6275) else color
        // glow
        if (owned) drawCircle(Brush.radialGradient(listOf(col.copy(alpha = 0.45f), Color.Transparent)), w * 0.62f, Offset(w / 2f, h * 0.55f))
        // cap and neck
        drawRoundRect(col.copy(alpha = 0.9f), Offset(w * 0.4f, h * 0.06f), Size(w * 0.2f, h * 0.16f), CornerRadius(4f))
        drawRoundRect(Color.White.copy(alpha = 0.35f), Offset(w * 0.42f, h * 0.08f), Size(w * 0.05f, h * 0.1f), CornerRadius(2f))
        // glass body
        drawRoundRect(Color(0xFF0A1022), Offset(left, top), Size(bodyW, bodyH), corner)
        // liquid
        val frac = if (locked) 0.12f else (0.28f + 0.14f * tier).coerceAtMost(0.95f)
        val liquidTop = top + bodyH * (1f - frac)
        val wob = sin(phase * 6.2832f) * 3f
        drawRoundRect(
            Brush.verticalGradient(listOf(col.copy(alpha = 0.95f), col.copy(alpha = 0.45f)), liquidTop, top + bodyH),
            Offset(left + 3f, liquidTop + wob * 0.3f), Size(bodyW - 6f, top + bodyH - liquidTop - 3f - wob * 0.3f), CornerRadius(corner.x * 0.8f)
        )
        // bubbles
        if (!locked) for (b in 0 until 4) {
            val ph = (phase * (0.8f + b * 0.17f) + b * 0.27f) % 1f
            val bx = left + bodyW * (0.22f + 0.18f * b) + sin((ph + b) * 6.2832f) * 3f
            val by = top + bodyH - 6f - (top + bodyH - liquidTop - 12f).coerceAtLeast(0f) * ph
            drawCircle(Color.White.copy(alpha = (0.6f * (1f - ph)).coerceIn(0f, 1f)), 2.4f + b * 0.4f, Offset(bx, by))
        }
        // tier ribs on the glass
        for (r in 1..tier) {
            val ry = top + bodyH * (0.12f + 0.1f * r)
            drawLine(Color.White.copy(alpha = 0.14f), Offset(left + 6f, ry), Offset(left + bodyW - 6f, ry), strokeWidth = 1.5f)
        }
        // rim and highlight
        drawRoundRect(col.copy(alpha = if (locked) 0.5f else 1f), Offset(left, top), Size(bodyW, bodyH), corner, style = Stroke(3.2f))
        drawRoundRect(Color.White.copy(alpha = 0.3f), Offset(left + bodyW * 0.14f, top + bodyH * 0.1f), Size(bodyW * 0.1f, bodyH * 0.6f), CornerRadius(6f))
        // base plate
        drawRoundRect(col.copy(alpha = 0.8f), Offset(left - 4f, top + bodyH - 2f), Size(bodyW + 8f, h * 0.06f), CornerRadius(5f))
    }
}

@Composable
private fun MiniBar(frac: Float, color: Color) {
    Box(Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)).background(Color.White.copy(alpha = 0.1f))) {
        Box(Modifier.fillMaxWidth(frac.coerceIn(0f, 1f)).height(5.dp).background(color))
    }
}

@Composable
fun FuelTankPanel(viewModel: NeonRushViewModel, profile: GameProfile) {
    val ctx = LocalContext.current
    val activity = remember(ctx) { var c: Context? = ctx; var a: Activity? = null; while (c is ContextWrapper) { if (c is Activity) { a = c; break }; c = c.baseContext }; a }
    val prog by viewModel.fuelGate.state.collectAsState()
    val clock = rememberInfiniteTransition(label = "tankClock").animateFloat(
        0f, 1f, infiniteRepeatable(tween(3200, easing = LinearEasing), RepeatMode.Restart), label = "tc"
    )
    val owned = profile.fuelTiersOwned
    var lockInfo by remember { mutableStateOf<LockInfo?>(null) }

    Text("⛽ FUEL TANK UPGRADES", color = CyberSecondary, fontWeight = FontWeight.Bold, fontSize = 14.sp,
        fontFamily = FontFamily.Monospace, modifier = Modifier.padding(bottom = 4.dp))
    // overall longevity meter, five segments
    Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        for (i in 1..5) {
            Box(Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp))
                .background(if (owned >= i) TIER_COLORS[i - 1] else Color.White.copy(alpha = 0.1f)))
        }
    }
    Text(if (owned > 0) "Current longevity: +${owned * 20}%" else "No upgrades yet. Every tier makes every tank last longer, forever.",
        color = CyberPrimary, fontSize = 12.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(bottom = 8.dp))

    lockInfo?.let { LockInfoDialog(it) { lockInfo = null } }
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        for (i in 1..5) {
            val col = TIER_COLORS[i - 1]
            val isOwned = owned >= i
            val prevOwned = owned >= i - 1
            val streakOk = viewModel.fuelGate.canBuy(i, prog)
            val buyable = !isOwned && prevOwned && streakOk
            val locked = !isOwned && !buyable
            val shape = RoundedCornerShape(14.dp)
            Column(
                Modifier.width(if (FuelGate.gated(i) && !isOwned) 176.dp else 124.dp).clip(shape)
                    .background(Brush.verticalGradient(listOf(col.copy(alpha = if (locked) 0.06f else 0.22f), CyberSurface)))
                    .border(if (buyable || isOwned) 2.dp else 1.dp, if (locked) Color.White.copy(alpha = 0.15f) else col.copy(alpha = if (isOwned) 1f else 0.8f), shape)
                    .then(if (buyable && activity != null) Modifier.clickable { viewModel.purchaseFuelTier(activity, i, TIER_IDS[i - 1]) }
                          else if (locked) Modifier.clickable { lockInfo = fuelLockInfo(i, prog, prevOwned, col) } else Modifier)
                    .padding(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("TIER $i", color = if (locked) Color.White.copy(alpha = 0.5f) else col, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black, fontSize = 12.sp)
                Box(contentAlignment = Alignment.Center) {
                    TankIcon(i, col, isOwned, locked, clock.value, Modifier.size(width = 84.dp, height = 96.dp))
                    if (locked) Text("🔒", fontSize = 22.sp)
                }
                Text(TIER_NAMES[i - 1], color = Color.White, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                Text("+${i * 20}% fuel life", color = if (locked) Color.White.copy(alpha = 0.45f) else col, fontFamily = FontFamily.Monospace, fontSize = 11.sp,
                    modifier = Modifier.padding(top = 2.dp, bottom = 6.dp))

                when {
                    isOwned -> Text("✓ INSTALLED", color = Color(0xFF05060F), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black, fontSize = 11.sp,
                        modifier = Modifier.background(col, RoundedCornerShape(6.dp)).padding(horizontal = 10.dp, vertical = 5.dp))
                    buyable -> Text("BUY ${TIER_PRICES[i - 1]}", color = Color(0xFF05060F), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black, fontSize = 12.sp,
                        modifier = Modifier.background(col, RoundedCornerShape(6.dp)).padding(horizontal = 12.dp, vertical = 6.dp))
                    !prevOwned -> Text("Install Tier ${i - 1} first", color = Color.White.copy(alpha = 0.5f), fontSize = 10.sp, textAlign = TextAlign.Center)
                    else -> {
                        val pNeed = FuelGate.proNeed(i); val fNeed = FuelGate.freeNeed(i)
                        Text("UNLOCK TO BUY", color = col, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                        if (pNeed != fNeed) {
                            Text("PRO  ${prog.proStreak.coerceAtMost(pNeed)}/$pNeed months in a row", color = Color.White, fontSize = 10.sp, modifier = Modifier.padding(top = 5.dp, bottom = 2.dp))
                            MiniBar(prog.proStreak.toFloat() / pNeed, Color(0xFFFFD23F))
                            Text("FREE  ${prog.playStreak.coerceAtMost(fNeed)}/$fNeed months in a row", color = Color.White, fontSize = 10.sp, modifier = Modifier.padding(top = 5.dp, bottom = 2.dp))
                            MiniBar(prog.playStreak.toFloat() / fNeed, col)
                        } else {
                            Text("${prog.playStreak.coerceAtMost(fNeed)}/$fNeed months in a row", color = Color.White, fontSize = 10.sp, modifier = Modifier.padding(top = 5.dp, bottom = 2.dp))
                            MiniBar(prog.playStreak.toFloat() / fNeed, col)
                        }
                        Text("TAP FOR DETAILS", color = Color.White.copy(alpha = 0.55f), fontSize = 9.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(top = 6.dp))
                        Text("Free months need ${FuelGate.DAYS_PER_MONTH}+ play days (this month ${prog.daysThisMonth}/${FuelGate.DAYS_PER_MONTH}). Skip a month and it restarts.",
                            color = CyberOnSurface.copy(alpha = 0.6f), fontSize = 9.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
                    }
                }
            }
        }
    }
}

private fun fuelLockInfo(tier: Int, prog: com.neonrush.game.FuelProgress, prevOwned: Boolean, accent: Color): LockInfo {
    val pNeed = FuelGate.proNeed(tier); val fNeed = FuelGate.freeNeed(tier)
    val lines = ArrayList<String>()
    if (!prevOwned) lines.add("Install Tier ${tier - 1} first. Tiers are installed in order.")
    if (pNeed == fNeed) {
        lines.add("Play $fNeed months in a row, whether you are Free or Pro. Free months need ${FuelGate.DAYS_PER_MONTH}+ different play days.")
    } else {
        lines.add("PRO route: stay subscribed to Pro for $pNeed months in a row.")
        lines.add("FREE route: play ${FuelGate.DAYS_PER_MONTH}+ different days in each of $fNeed months in a row.")
        lines.add("Whichever route finishes first unlocks the purchase.")
    }
    lines.add("Miss a month and the streak starts again from zero.")
    val best = maxOf(prog.proStreak.toFloat() / pNeed, prog.playStreak.toFloat() / fNeed)
    val label = if (pNeed == fNeed) "${prog.playStreak.coerceAtMost(fNeed)}/$fNeed months  ·  this month ${prog.daysThisMonth}/${FuelGate.DAYS_PER_MONTH} play days"
        else "Pro ${prog.proStreak.coerceAtMost(pNeed)}/$pNeed  ·  Free ${prog.playStreak.coerceAtMost(fNeed)}/$fNeed  ·  this month ${prog.daysThisMonth}/${FuelGate.DAYS_PER_MONTH} days"
    return LockInfo("⛽", "Tier $tier · ${TIER_NAMES[tier - 1]}", "Locked. Earn the right to buy it.", lines, best, label, accent)
}
