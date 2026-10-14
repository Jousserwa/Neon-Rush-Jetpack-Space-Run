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
        LockerCat("parts", "Pilot Parts", "🧩", PartCatalog.SHOP.count { it.id in pt.owned }, PartCatalog.SHOP.size)
    )
    val totalAll = cats.sumOf { it.total }
    val overall = if (totalAll == 0) 0f else cats.sumOf { it.owned }.toFloat() / totalAll
    val tier = LockerCatalog.tierFor(overall)
    LaunchedEffect(tier, hangar != null) { if (hangar != null) viewModel.locker.setTier(tier) }
    val tc = Color(LockerCatalog.TIER_COLORS[tier])

    Row(
        Modifier.fillMaxWidth().padding(bottom = 12.dp).clip(RoundedCornerShape(10.dp)).background(CyberSurface)
            .border(1.5.dp, tc.copy(alpha = 0.8f), RoundedCornerShape(10.dp)).clickable { open = true }.padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("🗄️", fontSize = 26.sp, modifier = Modifier.padding(end = 10.dp))
        Column(Modifier.weight(1f)) {
            Text("LOCKER · ${LockerCatalog.TIER_NAMES[tier]}", color = tc, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black, fontSize = 13.sp)
            Text("${(overall * 100).toInt()}% of everything collected. Finish sets for gem rewards.", color = CyberOnSurface.copy(alpha = 0.7f), fontSize = 11.sp)
            Bar(overall, tc, Modifier.padding(top = 6.dp))
        }
        Text("›", color = tc, fontSize = 24.sp, modifier = Modifier.padding(start = 8.dp))
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
                SectionTitle("COLLECTOR RANKS")
                Steps(LockerCatalog.OVERALL_STEPS, overall, claimed, GOLD) { viewModel.locker.claim(it) }

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
    Row(Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)) {
        steps.forEach { st ->
            val got = st.key in claimed
            val ready = !got && frac >= st.needFrac
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                    .background(if (ready) accent.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.05f))
                    .border(1.dp, if (ready) accent else Color.White.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                    .then(if (ready) Modifier.clickable { onClaim(st) } else Modifier)
                    .padding(vertical = 8.dp, horizontal = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(st.title, color = if (got) accent else Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 2)
                Text(if (got) "✓" else "+${st.gems}💎", color = if (got) accent else if (ready) accent else Color.White.copy(alpha = 0.5f),
                    fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black, fontSize = 12.sp, modifier = Modifier.padding(top = 3.dp))
                Text(if (got) "CLAIMED" else if (ready) "TAP" else "🔒", color = if (ready) accent else Color.White.copy(alpha = 0.4f),
                    fontSize = 9.sp, fontFamily = FontFamily.Monospace)
            }
        }
    }
}
