package com.neonrush.game.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neonrush.game.HullCatalog
import com.neonrush.game.HullProgression
import com.neonrush.game.PremiumHull
import com.neonrush.game.ProgressionState
import com.neonrush.game.ui.theme.*
import kotlinx.coroutines.delay

private val Gold = Color(0xFFFFD23F)

/** GOALS tab: hull mastery, hull missions, set bonuses. */
@Composable
fun GoalsPanel(p: ProgressionState, onClaimMission: (String) -> Unit, onClaimSet: (String) -> Unit) {
    Column {
        Text("HULL MASTERY", color = CyberPrimary, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Text("Fly a hull to level it up. Cosmetic stars appear under the ship.", color = CyberOnSurface.copy(alpha = 0.7f), fontSize = 11.sp,
            modifier = Modifier.padding(bottom = 6.dp))
        if (p.masteryMeters.isEmpty()) Text("Fly a premium hull to start.", color = CyberOnSurface.copy(alpha = 0.5f), fontSize = 12.sp)
        p.masteryMeters.forEach { (id, m) ->
            val lv = HullProgression.level(m)
            val next = HullProgression.MASTERY_STEPS.getOrNull(lv)
            val prev = if (lv == 0) 0 else HullProgression.MASTERY_STEPS[lv - 1]
            Column(Modifier.fillMaxWidth().padding(vertical = 3.dp).background(CyberSurface, RoundedCornerShape(8.dp)).padding(10.dp)) {
                Text("${HullCatalog.byId(id)?.emoji ?: ""} ${HullCatalog.byId(id)?.name ?: id}  ·  Lv $lv ${"★".repeat(lv)}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Box(Modifier.fillMaxWidth().padding(top = 6.dp).height(5.dp).clip(RoundedCornerShape(3.dp)).background(Color.White.copy(alpha = 0.1f))) {
                    val f = if (next == null) 1f else (m - prev).toFloat() / (next - prev)
                    Box(Modifier.fillMaxWidth(f.coerceIn(0f, 1f)).fillMaxHeight().background(Gold))
                }
                Text(if (next == null) "MAX · ${m}m flown" else "${m}m / ${next}m", color = CyberOnSurface.copy(alpha = 0.6f), fontSize = 10.sp)
            }
        }
        Spacer(Modifier.height(14.dp))
        Text("HULL MISSIONS", color = CyberPrimary, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        if (p.missions.isEmpty()) Text("Own a premium hull to unlock its missions.", color = CyberOnSurface.copy(alpha = 0.5f), fontSize = 12.sp)
        p.missions.filter { !it.claimed }.forEach { m ->
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp).background(CyberSurface, RoundedCornerShape(8.dp)).padding(10.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(m.def.text(HullCatalog.byId(m.def.hullId)?.name ?: m.def.hullId), color = Color.White, fontSize = 12.sp)
                    Text("${m.progress.coerceAtMost(m.def.target)}/${m.def.target}  ·  +${m.def.rewardGems} 💎", color = CyberOnSurface.copy(alpha = 0.6f), fontSize = 10.sp)
                }
                Button(onClick = { onClaimMission(m.def.key) }, enabled = m.done, shape = RoundedCornerShape(6.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CyberPrimary)) {
                    Text("CLAIM", color = CyberBackground, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Text("SET BONUSES", color = CyberPrimary, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        p.sets.forEach { s ->
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp).background(CyberSurface, RoundedCornerShape(8.dp)).padding(10.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(s.def.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text("${s.def.blurb}  ·  ${s.ownedCount}/${s.def.hullIds.size}  ·  +${s.def.rewardGems} 💎", color = CyberOnSurface.copy(alpha = 0.6f), fontSize = 10.sp)
                }
                if (s.claimed) Text("✔", color = CyberPrimary)
                else Button(onClick = { onClaimSet(s.def.id) }, enabled = s.complete, shape = RoundedCornerShape(6.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CyberPrimary)) {
                    Text("CLAIM", color = CyberBackground, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}

/**
 * Ghost Hull Spotlight: show on the ghost/challenge screen and on game over after racing a ghost.
 * If the ghost flies a premium hull the player doesn't own, they see it live, can TRY IT 8s, and buy.
 */
@Composable
fun GhostHullSpotlight(ghostName: String, hull: PremiumHull, owned: Boolean, priceLabel: String, canBuy: Boolean,
                       onBuy: () -> Unit, onTry: () -> Unit = {}) {
    if (owned || hull.earnedOnly) return
    var left by remember { mutableStateOf(0) }
    var trig by remember { mutableStateOf(0) }
    LaunchedEffect(trig) { if (trig > 0) { left = 8; while (left > 0) { delay(1000); left-- } } }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
        .background(Brush.verticalGradient(listOf(CyberSurface, CyberBackground))).padding(12.dp)) {
        Text("👻 $ghostName flies ${hull.emoji} ${hull.name}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        HangarPreview(hull.id, reactions = true)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = { trig++; onTry() }, enabled = left == 0, shape = RoundedCornerShape(6.dp),
                colors = ButtonDefaults.buttonColors(containerColor = CyberSurface)) {
                Text(if (left > 0) "TRY ${left}s" else "▶ TRY IT 8s", color = CyberPrimary, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            }
            Spacer(Modifier.weight(1f))
            Button(onClick = onBuy, enabled = canBuy, shape = RoundedCornerShape(6.dp),
                colors = ButtonDefaults.buttonColors(containerColor = CyberSecondary)) {
                Text(if (canBuy) "BUY $priceLabel" else "PRO EARLY ACCESS", color = Color.White, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

/** First-purchase / "complete your set" banner for the Apex Trio bundle. Show only if owned <= 1 of the three. */
@Composable
fun ApexBundleBanner(ownedOfTrio: Int, priceLabel: String, onBuy: () -> Unit) {
    if (ownedOfTrio > 1) return
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
        .background(Brush.horizontalGradient(listOf(Gold.copy(alpha = 0.25f), CyberSecondary.copy(alpha = 0.25f)))).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("🐉🕳️👑 APEX TRIO", color = Color.White, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
            Text("Neon Wyrm + Event Horizon + Inferno Sovereign. Save 28%.", color = Color.White.copy(alpha = 0.85f), fontSize = 11.sp)
        }
        Button(onClick = onBuy, shape = RoundedCornerShape(6.dp), colors = ButtonDefaults.buttonColors(containerColor = Gold)) {
            Text(priceLabel, color = Color.Black, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
    }
}
