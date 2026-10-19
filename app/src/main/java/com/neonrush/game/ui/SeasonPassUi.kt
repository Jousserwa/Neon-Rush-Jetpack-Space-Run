package com.neonrush.game.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
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
import com.neonrush.game.PassOffers
import com.neonrush.game.PassReward
import com.neonrush.game.RewardKind
import com.neonrush.game.SeasonPass
import com.neonrush.game.SeasonPassState
import com.neonrush.game.ui.theme.*

private val Gold = Color(0xFFFFD23F)

@Composable
fun SeasonPassScreen(
    s: SeasonPassState, onBuy: () -> Unit, onClaim: (tier: Int, track: String) -> Unit,
    onSkipPay: () -> Unit = {}, onBuyBundle: () -> Unit = {},
    /** True when the player already owns all three Apex hulls: the pass+hulls bundle is hidden (they'd pay twice). */
    hideBundle: Boolean = false
) {
    Column(Modifier.fillMaxWidth()) {
        val hull = s.config?.hullId?.let { HullCatalog.byId(it) }
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(Brush.horizontalGradient(listOf(CyberTertiary.copy(alpha = 0.55f), CyberSecondary.copy(alpha = 0.4f))))
            .padding(14.dp)) {
            Column {
                Text("🏁 ${s.seasonName.ifBlank { "SEASON" }} PASS", color = Color.White, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, fontSize = 14.sp)
                Text(hull?.let { "Finish it to earn ${it.emoji} ${it.name} + Chroma" } ?: "Finish it to earn the Season Champion title",
                    color = Color.White, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
                Text("Ends in ${formatCountdown(s.msLeft)}  ·  Tier ${s.tier}/${SeasonPass.TIERS}  ·  🎟 ${s.tokens} tokens", color = Color.White.copy(alpha = 0.85f),
                    fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        val frac = if (s.tier >= SeasonPass.TIERS) 1f else s.tokensIntoTier / SeasonPass.TOKENS_PER_TIER.toFloat()
        Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(CyberSurface)) {
            Box(Modifier.fillMaxWidth(safeFrac(frac)).fillMaxHeight().background(Brush.horizontalGradient(listOf(CyberPrimary, CyberSecondary))))
        }
        Text("${s.tokensIntoTier}/${SeasonPass.TOKENS_PER_TIER} tokens to the next tier. Collect 🎟 Tokens in every run: gems, close calls, new zones and bosses. Works offline. Falling behind? You earn bonus tokens to catch up.",
            color = CyberOnSurface.copy(alpha = 0.6f), fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp, bottom = 4.dp))
        if (s.lastRunTokens > 0) Text("Last run: +${s.lastRunTokens} 🎟", color = Gold, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        if (s.boostTokenRuns > 0 || s.boostGemRuns > 0) {
            Row(Modifier.padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (s.boostTokenRuns > 0) Text("⚡ Token x2 next ${s.boostTokenRuns} run(s)", color = Color.White, fontSize = 10.sp,
                    modifier = Modifier.background(CyberSecondary.copy(alpha = 0.5f), RoundedCornerShape(10.dp)).padding(horizontal = 8.dp, vertical = 3.dp))
                if (s.boostGemRuns > 0) Text("💎 +25% gems next ${s.boostGemRuns} run(s)", color = Color.White, fontSize = 10.sp,
                    modifier = Modifier.background(CyberTertiary.copy(alpha = 0.5f), RoundedCornerShape(10.dp)).padding(horizontal = 8.dp, vertical = 3.dp))
            }
        }
        Spacer(Modifier.height(6.dp))

        if (!s.premiumOwned) {
            Row(Modifier.fillMaxWidth().background(CyberSurface, RoundedCornerShape(10.dp)).border(1.dp, Gold, RoundedCornerShape(10.dp)).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Unlock the premium track", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text(if (s.isPro) "Pro price ${s.proPriceLabel} (regular ${s.priceLabel})" else "Pro members pay less", color = Gold, fontSize = 11.sp)
                }
                Button(onClick = onBuy, enabled = !s.inFlight, colors = ButtonDefaults.buttonColors(containerColor = CyberSecondary), shape = RoundedCornerShape(6.dp)) {
                    Text(if (s.inFlight) "…" else "BUY ${if (s.isPro) s.proPriceLabel else s.priceLabel}", color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(8.dp))
            // Best-value bundle: premium track + Apex Trio hulls (hidden if they already own the trio).
            if (!hideBundle) Row(Modifier.fillMaxWidth().background(CyberSurface, RoundedCornerShape(10.dp)).border(1.dp, CyberSecondary, RoundedCornerShape(10.dp)).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("🔥 Pass + Apex Trio hulls", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text("Premium track + Neon Wyrm, Event Horizon and Inferno Sovereign. Best value.", color = CyberOnSurface.copy(alpha = 0.7f), fontSize = 10.sp)
                }
                Button(onClick = onBuyBundle, enabled = !s.inFlight, colors = ButtonDefaults.buttonColors(containerColor = CyberSecondary), shape = RoundedCornerShape(6.dp)) {
                    Text("BUY ${s.bundlePriceLabel}", color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                }
            }
            if (!hideBundle) Spacer(Modifier.height(8.dp))
        }
        if (s.tier < SeasonPass.TIERS) {
            // Skip a tier: small real-money purchase.
            Row(Modifier.fillMaxWidth().background(CyberSurface, RoundedCornerShape(10.dp)).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("⏩ Skip a tier", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text("${s.skipsLeft} skip${if (s.skipsLeft == 1) "" else "s"} left this season", color = CyberOnSurface.copy(alpha = 0.6f), fontSize = 10.sp)
                }
                Button(onClick = onSkipPay, enabled = s.skipsLeft > 0,
                    colors = ButtonDefaults.buttonColors(containerColor = CyberSecondary), shape = RoundedCornerShape(6.dp), contentPadding = PaddingValues(horizontal = 10.dp)) {
                    Text("BUY ${s.skipPriceLabel}", color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(Modifier.width(54.dp)) {
                Spacer(Modifier.height(24.dp))
                Text("FREE", color = CyberPrimary, fontSize = 10.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.height(64.dp).padding(top = 22.dp))
                Text("PREMIUM", color = Gold, fontSize = 9.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.height(64.dp).padding(top = 22.dp))
                Text("PRO", color = CyberSecondary, fontSize = 10.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.height(64.dp).padding(top = 22.dp))
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(s.tiers) { t ->
                    val reached = s.tier >= t.tier
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${t.tier}", color = if (reached) CyberPrimary else CyberOnSurface.copy(alpha = 0.5f), fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace, modifier = Modifier.height(24.dp))
                        RewardCell(t.free, reached, t.tier in s.claimedFree, true) { onClaim(t.tier, "free") }
                        RewardCell(t.premium, reached, t.tier in s.claimedPremium, s.premiumOwned) { onClaim(t.tier, "premium") }
                        RewardCell(t.proBonus, reached, t.tier in s.claimedPro, s.isPro) { onClaim(t.tier, "pro") }
                    }
                }
            }
        }
    }
}

@Composable
private fun RewardCell(r: PassReward?, reached: Boolean, claimed: Boolean, unlockedTrack: Boolean, onClaim: () -> Unit) {
    Box(Modifier.size(width = 64.dp, height = 60.dp).padding(2.dp).clip(RoundedCornerShape(8.dp))
        .background(if (r == null) Color.Transparent else CyberSurface)
        .border(1.dp, if (r == null) Color.Transparent else if (claimed) CyberPrimary else Color.White.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
        .clickable(enabled = r != null && reached && unlockedTrack && !claimed) { onClaim() }, contentAlignment = Alignment.Center) {
        if (r != null) {
            val icon = when (r.kind) { RewardKind.GEMS -> "💎"; RewardKind.TITLE -> "🏷️"; RewardKind.BADGE -> "🎖️"
                RewardKind.BACKDROP -> "🌌"; RewardKind.CHROMA -> "🎨"; RewardKind.BOOST -> "⚡"; RewardKind.HULL -> HullCatalog.byId(r.value)?.emoji ?: "🛸" }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (claimed) "✔" else icon, fontSize = 18.sp)
                Text(when { claimed -> "got it"; !unlockedTrack -> "🔒"; reached -> "CLAIM"; else -> r.label.take(9) },
                    color = if (reached && unlockedTrack && !claimed) Gold else CyberOnSurface.copy(alpha = 0.6f), fontSize = 8.sp, maxLines = 1)
            }
        }
    }
}


/** Game-over nudge: shown only when [PassOffers.postRun] has something compelling to say. */
@Composable
fun PassOfferCard(s: SeasonPassState, onBuy: () -> Unit, onOpen: (() -> Unit)? = null) {
    val offer = PassOffers.postRun(s) ?: return
    androidx.compose.runtime.LaunchedEffect(offer.headline) { com.neonrush.game.HangarAnalytics.passOffer("post_run", true) }
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp).background(CyberSurface, RoundedCornerShape(10.dp)).border(1.dp, Gold, RoundedCornerShape(10.dp)).padding(12.dp)) {
        Text(offer.headline, color = Gold, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Text(offer.detail, color = Color.White.copy(alpha = 0.85f), fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { com.neonrush.game.HangarAnalytics.passOffer("post_run", false); onBuy() }, enabled = !s.inFlight,
                colors = ButtonDefaults.buttonColors(containerColor = CyberSecondary), shape = RoundedCornerShape(6.dp)) {
                Text("UNLOCK ${if (s.isPro) s.proPriceLabel else s.priceLabel}", color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
            }
            if (onOpen != null) Button(onClick = onOpen, colors = ButtonDefaults.buttonColors(containerColor = CyberSurface), shape = RoundedCornerShape(6.dp),
                modifier = Modifier.border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(6.dp))) {
                Text("SEE PASS", color = CyberPrimary, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
            }
        }
    }
}

/** Home-screen banner for the last 5 days of a season. Tap = open the Skins/Hangar tab where the pass lives. */
@Composable
fun SeasonReminderBanner(s: SeasonPassState, onOpen: () -> Unit) {
    val text = PassOffers.endOfSeason(s) ?: return
    androidx.compose.runtime.LaunchedEffect(text) { com.neonrush.game.HangarAnalytics.passOffer("end_of_season", true) }
    Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).clip(RoundedCornerShape(10.dp))
        .background(Brush.horizontalGradient(listOf(CyberTertiary.copy(alpha = 0.6f), CyberSecondary.copy(alpha = 0.5f))))
        .clickable { com.neonrush.game.HangarAnalytics.passOffer("end_of_season", false); onOpen() }.padding(10.dp)) {
        Text(text, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
    }
}
