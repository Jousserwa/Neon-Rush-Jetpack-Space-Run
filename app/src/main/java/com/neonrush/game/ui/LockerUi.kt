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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.neonrush.game.LockerCat
import com.neonrush.game.LockerCatalog
import com.neonrush.game.LockerStep
import com.neonrush.game.NeonRushViewModel
import com.neonrush.game.PartCatalog
import com.neonrush.game.RevenueCatManager
import com.neonrush.game.Skins
import com.neonrush.game.ThrusterCatalog
import com.neonrush.game.ThrusterKind
import com.neonrush.game.db.GameProfile
import com.neonrush.game.ui.theme.CyberBackground
import com.neonrush.game.ui.theme.CyberOnSurface
import com.neonrush.game.ui.theme.CyberPrimary
import com.neonrush.game.ui.theme.CyberSecondary
import com.neonrush.game.ui.theme.CyberSurface

private val GOLD = Color(0xFFFFD23F)

/** Every milestone (collector ranks, set steps, razor dodges) that is reached but not yet claimed. */
private fun readySteps(cats: List<LockerCat>, overall: Float, claimed: Set<String>, razors: Int): List<LockerStep> {
    val out = ArrayList<LockerStep>()
    LockerCatalog.OVERALL_STEPS.forEach { if (it.key !in claimed && overall >= it.needFrac) out.add(it) }
    cats.forEach { c -> LockerCatalog.stepsFor(c.id).forEach { if (it.key !in claimed && c.frac >= it.needFrac) out.add(it) } }
    LockerCatalog.RAZOR_STEPS.forEach { if (it.key !in claimed && razors >= it.needFrac) out.add(it) }
    return out
}

/** Shop entry card + the full-screen Locker. Drop it above the shop tabs. */
@Composable
fun LockerEntry(viewModel: NeonRushViewModel, profile: GameProfile) {
    var open by remember { mutableStateOf(false) }
    val hangar by viewModel.hangar.ui.collectAsState()
    val th by viewModel.thrusters.state.collectAsState()
    val pt by viewModel.parts.state.collectAsState()
    val pro by RevenueCatManager.isPro.collectAsState()

    val suitsOwned = (profile.unlockedPilotSkinsCsv.split(",").filter { it.isNotBlank() }.toSet() + "default").count { id -> Skins.ALL.any { it.id == id } }
    val cats = listOf(
        LockerCat("hulls", "Ship Hulls", "🚀", hangar?.ownedHullCount ?: 0, hangar?.totalHullCount ?: 0),
        LockerCat("suits", "Pilot Suits", "🧑‍🚀", suitsOwned, Skins.ALL.size),
        LockerCat("trails", "Thruster Trails", "☄️", ThrusterCatalog.ALL.count { it.id in th.owned && (it.kind != ThrusterKind.PRO || pro) }, ThrusterCatalog.ALL.count { ThrusterCatalog.released(it, com.neonrush.game.ServerClock.bestNowMs()) }),
        LockerCat("parts", "Pilot Parts", "🧩", PartCatalog.SHOP.count { it.id in pt.owned }, PartCatalog.SHOP.count { com.neonrush.game.DropSchedule.partReleased(it.id, com.neonrush.game.ServerClock.bestNowMs()) })
    )
    val totalAll = cats.sumOf { it.total }
    val overall = if (totalAll == 0) 0f else cats.sumOf { it.owned }.toFloat() / totalAll
    val tier = LockerCatalog.tierFor(overall)
    LaunchedEffect(tier, hangar != null) { if (hangar != null) viewModel.locker.setTier(tier) }
    val tc = Color(LockerCatalog.TIER_COLORS[tier])

    val claimedNow by viewModel.locker.claimed.collectAsState()
    val razorsNow by viewModel.locker.razorCount.collectAsState()
    val ready = readySteps(cats, overall, claimedNow, razorsNow).size
    val hot = if (ready > 0) GOLD else tc
    val tr = rememberInfiniteTransition(label = "lockerEntry")
    val pulse by tr.animateFloat(0f, 1f, infiniteRepeatable(tween(if (ready > 0) 700 else 1600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "lp")
    val sweep by tr.animateFloat(-0.3f, 1.3f, infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Restart), label = "ls")
    val nudge by tr.animateFloat(0f, 1f, infiniteRepeatable(tween(650, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "ln")
    val entryShape = RoundedCornerShape(14.dp)

    Box(Modifier.fillMaxWidth().padding(bottom = 14.dp)) {
        // soft halo behind the card
        Box(Modifier.matchParentSize().padding(2.dp).clip(entryShape).background(hot.copy(alpha = 0.10f + 0.16f * pulse)))
        Row(
            Modifier.fillMaxWidth().clip(entryShape)
                .background(Brush.horizontalGradient(listOf(hot.copy(alpha = 0.28f), CyberSurface, hot.copy(alpha = 0.12f))))
                .drawWithContent {
                    drawContent()
                    val x = size.width * sweep
                    drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.16f), Color.Transparent), startX = x - 90f, endX = x + 90f))
                }
                .border(androidx.compose.ui.unit.Dp(1.6f + 1.8f * pulse), lerp(hot.copy(alpha = 0.55f), hot, pulse), entryShape)
                .clickable { open = true }.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(46.dp).clip(RoundedCornerShape(12.dp)).background(hot.copy(alpha = 0.22f + 0.15f * pulse))
                .border(1.5.dp, hot, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                Text("🗄️", fontSize = 24.sp)
            }
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("LOCKER", color = Color.White, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black, fontSize = 15.sp)
                    Text("  ·  ${LockerCatalog.TIER_NAMES[tier]}", color = tc, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
                Text(if (ready > 0) "Rewards are waiting. Tap to claim them!" else "${(overall * 100).toInt()}% collected. Finish sets for gem rewards.",
                    color = if (ready > 0) GOLD else CyberOnSurface.copy(alpha = 0.75f), fontSize = 11.sp,
                    fontWeight = if (ready > 0) FontWeight.Bold else FontWeight.Normal)
                Bar(overall, hot, Modifier.padding(top = 6.dp))
            }
            if (ready > 0) {
                Text("🎁 $ready", color = Color(0xFF05060F), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black, fontSize = 13.sp,
                    modifier = Modifier.padding(start = 8.dp)
                        .graphicsLayer { val sc = 1f + 0.14f * pulse; scaleX = sc; scaleY = sc }
                        .background(GOLD, RoundedCornerShape(12.dp)).padding(horizontal = 9.dp, vertical = 5.dp))
            }
            Text("›", color = hot, fontSize = 30.sp, fontWeight = FontWeight.Black,
                modifier = Modifier.padding(start = 8.dp).graphicsLayer { translationX = 6f * nudge })
        }
    }
    if (open) LockerDialog(viewModel, profile, cats, overall, tier) { open = false }
}

@Composable
private fun Bar(frac: Float, color: Color, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(4.dp)).background(Color.White.copy(alpha = 0.1f))) {
        Box(Modifier.fillMaxWidth(frac.coerceIn(0f, 1f)).height(7.dp).background(color))
    }
}

@Composable
private fun LockerDialog(viewModel: NeonRushViewModel, profile: GameProfile, cats: List<LockerCat>, overall: Float, tier: Int, onBack: () -> Unit) {
    val claimed by viewModel.locker.claimed.collectAsState()
    val razors by viewModel.locker.razorCount.collectAsState()
    val readyList = readySteps(cats, overall, claimed, razors)
    val clock = rememberInfiniteTransition(label = "lockerClock").animateFloat(
        0f, 1f, infiniteRepeatable(tween(2200, easing = LinearEasing), RepeatMode.Reverse), label = "lc"
    )
    val tc = Color(LockerCatalog.TIER_COLORS[tier])
    Dialog(onDismissRequest = onBack, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Column(Modifier.fillMaxSize().background(CyberBackground).statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.clip(RoundedCornerShape(10.dp)).background(CyberSurface).clickable { onBack() }.padding(horizontal = 14.dp, vertical = 8.dp)) {
                    Text("‹ BACK", color = CyberPrimary, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                Spacer(Modifier.width(12.dp))
                Text("LOCKER", color = Color.White, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black, fontSize = 16.sp)
            }
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp)) {
                // ---- mastery header
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                        .background(Brush.verticalGradient(listOf(tc.copy(alpha = 0.28f), CyberSurface)))
                        .border(2.dp, lerp(tc.copy(alpha = 0.5f), tc, clock.value), RoundedCornerShape(14.dp)).padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    SuitAvatar(profile.activePilotSkinId, 120.dp)
                    Text(LockerCatalog.TIER_NAMES[tier].uppercase(), color = tc, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black, fontSize = 18.sp)
                    Text("${(overall * 100).toInt()}% complete", color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(bottom = 6.dp))
                    Bar(overall, tc)
                    val next = LockerCatalog.OVERALL_STEPS.firstOrNull { it.needFrac > overall }
                    Text(
                        if (next != null) "Next: ${next.title} at ${(next.needFrac * 100).toInt()}%  ·  orbiting stars around your pilot grow with each rank"
                        else "Every set complete. The Mythic ring circles your pilot forever.",
                        color = CyberOnSurface.copy(alpha = 0.75f), fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp)
                    )
                }
                if (readyList.isNotEmpty()) {
                    val total = readyList.sumOf { it.gems }
                    Text("🎁 CLAIM ALL  ·  ${readyList.size} ready  ·  +$total 💎", color = Color(0xFF05060F), fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Black, fontSize = 14.sp, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp).clip(RoundedCornerShape(12.dp))
                            .background(Brush.horizontalGradient(listOf(GOLD, Color(0xFFFFF59D), GOLD)))
                            .border(2.dp, lerp(GOLD.copy(alpha = 0.5f), Color.White, clock.value), RoundedCornerShape(12.dp))
                            .clickable { readyList.forEach { viewModel.locker.claim(it) } }.padding(vertical = 13.dp))
                }
                SectionTitle("COLLECTOR RANKS")
                Steps(LockerCatalog.OVERALL_STEPS, overall, claimed, GOLD) { viewModel.locker.claim(it) }

                SectionTitle("RAZOR DODGES")
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(CyberSurface)
                    .border(1.dp, Color(0xFF82B1FF).copy(alpha = 0.5f), RoundedCornerShape(12.dp)).padding(12.dp)) {
                    Text("⚡ $razors razor dodges", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    val nextR = LockerCatalog.RAZOR_STEPS.firstOrNull { it.needFrac > razors }
                    Text(if (nextR != null) "Slip past a hazard by a hair. Next reward at ${nextR.needFrac.toInt()}."
                         else "Razor Legend. The storm bows to you.",
                        color = CyberOnSurface.copy(alpha = 0.7f), fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp, bottom = 8.dp))
                    Steps(LockerCatalog.RAZOR_STEPS, razors.toFloat(), claimed, Color(0xFF82B1FF)) { viewModel.locker.claim(it) }
                }

                SectionTitle("SETS")
                cats.forEach { c ->
                    val done = c.complete
                    val border = if (done) lerp(GOLD.copy(alpha = 0.5f), GOLD, clock.value) else CyberPrimary.copy(alpha = 0.35f)
                    Column(
                        Modifier.fillMaxWidth().padding(vertical = 5.dp).clip(RoundedCornerShape(12.dp))
                            .background(if (done) GOLD.copy(alpha = 0.08f) else CyberSurface)
                            .border(if (done) 2.5.dp else 1.dp, border, RoundedCornerShape(12.dp)).padding(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(c.emoji, fontSize = 22.sp, modifier = Modifier.padding(end = 8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(c.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("${c.owned}/${c.total} collected", color = CyberOnSurface.copy(alpha = 0.7f), fontSize = 11.sp)
                            }
                            if (done) Text("★ SET COMPLETE", color = GOLD, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black, fontSize = 11.sp)
                            else Text("${(c.frac * 100).toInt()}%", color = CyberPrimary, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        Bar(c.frac, if (done) GOLD else CyberSecondary, Modifier.padding(vertical = 8.dp))
                        Steps(LockerCatalog.stepsFor(c.id), c.frac, claimed, if (done) GOLD else CyberPrimary) { viewModel.locker.claim(it) }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, color = CyberPrimary, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 13.sp,
        modifier = Modifier.padding(top = 16.dp, bottom = 6.dp))
}

@Composable
private fun Steps(steps: List<LockerStep>, frac: Float, claimed: Set<String>, accent: Color, onClaim: (LockerStep) -> Unit) {
    val sp by rememberInfiniteTransition(label = "stepPulse").animateFloat(0f, 1f, infiniteRepeatable(tween(800, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "sp")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)) {
        steps.forEach { st ->
            val got = st.key in claimed
            val ready = !got && frac >= st.needFrac
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                    .background(if (ready) accent.copy(alpha = 0.18f + 0.22f * sp) else Color.White.copy(alpha = 0.05f))
                    .border(if (ready) 2.dp else 1.dp, if (ready) lerp(accent.copy(alpha = 0.5f), accent, sp) else Color.White.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                    .then(if (ready) Modifier.clickable { onClaim(st) } else Modifier)
                    .padding(vertical = 8.dp, horizontal = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(st.title, color = if (got) accent else Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 2)
                Text(if (got) "✓" else "+${st.gems}💎", color = if (got) accent else if (ready) accent else Color.White.copy(alpha = 0.5f),
                    fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black, fontSize = 12.sp, modifier = Modifier.padding(top = 3.dp))
                Text(if (got) "CLAIMED" else if (ready) "TAP TO CLAIM" else "🔒", color = if (ready) accent else Color.White.copy(alpha = 0.4f),
                    fontSize = 9.sp, fontFamily = FontFamily.Monospace)
            }
        }
    }
}
