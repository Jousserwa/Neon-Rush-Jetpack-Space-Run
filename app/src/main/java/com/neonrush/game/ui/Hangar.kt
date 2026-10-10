package com.neonrush.game.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Switch
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
import com.neonrush.game.PremiumHull
import com.neonrush.game.ui.theme.CyberBackground
import com.neonrush.game.ui.theme.CyberOnSurface
import com.neonrush.game.ui.theme.CyberPrimary
import com.neonrush.game.ui.theme.CyberSecondary
import com.neonrush.game.ui.theme.CyberSurface
import com.neonrush.game.ui.theme.CyberTertiary
import kotlinx.coroutines.delay

/**
 * HANGAR (Stage 2). Pure UI: it takes a state snapshot + callbacks, so it does not depend on
 * how Stage 3 stores things. Stage 3 builds [HangarUiState] from GameProfile / RevenueCat.
 *
 * Drop it in where the "ships" tab content lives:
 *   if (selectedTab == "ships") HangarScreen(hangarState, hangarActions)
 * (keep the gem hull list below it via [legacyHullList] if you still want gem hulls on the same page)
 */

data class HangarUiState(
    val ownedHullIds: Set<String>,            // premium + gem hulls the player owns
    val activeHullId: String,
    val lockedHullId: String?,                // "Lock" switch: only this hull is used
    val rotationIds: List<String>,            // premium hulls included in rotation (owned subset)
    val nextRotationId: String?,              // for the "Next:" chip
    val storePrices: Map<String, String>,     // productId -> store-formatted price (from RevenueCat)
    val isPro: Boolean,
    val serverNowMs: Long,                    // trusted time
    val releaseAtMs: Map<String, Long>,       // hullId -> release time (drop calendar)
    val nextDropAtMs: Long,                   // next monthly drop (for countdown)
    val totalHullCount: Int,                  // gem hulls + premium hulls
    val ownedHullCount: Int,
    val purchaseInFlightId: String? = null,
    val justPurchasedId: String? = null,      // triggers the celebration overlay
    val loanerHullId: String? = null,         // Pro monthly loaner (temporary)
    val auraRank: com.neonrush.game.AuraRank = com.neonrush.game.AuraRank.NONE,
    val proMonths: Int = 0,
    val isAnnual: Boolean = false,
    val chroma: Map<String, Int> = emptyMap(),
    val stipendClaimable: Boolean = false
)

data class HangarActions(
    val onBuy: (PremiumHull) -> Unit,
    val onEquip: (String) -> Unit,
    val onToggleRotation: (String, Boolean) -> Unit,
    val onSetLock: (String?) -> Unit,
    val onTryStart: (String) -> Unit = {},    // analytics hook
    val onDismissCelebration: () -> Unit = {},
    val onRestore: () -> Unit = {},
    val onSetChroma: (String, Int) -> Unit = { _, _ -> },
    val onClaimStipend: () -> Unit = {},
    val onBuyBundle: (android.app.Activity) -> Unit = {}
)

private enum class HangarTab(val label: String) { HULLS("HULLS"), LOADOUT("LOADOUT"), VAULT("VAULT"), PASS("PASS"), GOALS("GOALS") }

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HangarScreen(
    state: HangarUiState, actions: HangarActions, modifier: Modifier = Modifier,
    passContent: (@Composable () -> Unit)? = null,     // SeasonPassScreen(...)
    goalsContent: (@Composable () -> Unit)? = null,    // GoalsPanel(...)
    bundleBanner: (@Composable () -> Unit)? = null     // ApexBundleBanner(...)
) {
    var tab by remember { mutableStateOf(HangarTab.HULLS) }
    Box(modifier) {
        Column(Modifier.fillMaxWidth()) {
            HangarHeader(state)
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                HangarTab.values().filter { (it != HangarTab.PASS || passContent != null) && (it != HangarTab.GOALS || goalsContent != null) }.forEach { t ->
                    val sel = t == tab
                    Box(
                        Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                            .background(if (sel) CyberPrimary else CyberSurface)
                            .clickable { tab = t }.padding(vertical = 9.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(t.label, color = if (sel) CyberBackground else CyberPrimary,
                            fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            when (tab) {
                HangarTab.HULLS -> { bundleBanner?.let { it(); Spacer(Modifier.height(10.dp)) }; HullCarousel(state, actions, HullCatalog.ALL) }
                HangarTab.LOADOUT -> LoadoutPanel(state, actions)
                HangarTab.VAULT -> VaultPanel(state, actions)
                HangarTab.PASS -> passContent?.invoke()
                HangarTab.GOALS -> goalsContent?.invoke()
            }
            Spacer(Modifier.height(8.dp))
            Text("Restore purchases", color = CyberPrimary.copy(alpha = 0.7f), fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.clickable { actions.onRestore() }.padding(8.dp))
        }
        state.justPurchasedId?.let { id ->
            HullCelebration(id, actions.onDismissCelebration)
        }
    }
}

@Composable
private fun HangarHeader(state: HangarUiState) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("🛸 HANGAR", color = CyberPrimary, fontWeight = FontWeight.Black, fontSize = 18.sp,
                    fontFamily = FontFamily.Monospace)
                AuraBadge(state.auraRank)
            }
            Box(Modifier.background(CyberSurface, RoundedCornerShape(20.dp))
                .border(1.dp, CyberTertiary, RoundedCornerShape(20.dp))
                .padding(horizontal = 10.dp, vertical = 4.dp)) {
                Text("${state.ownedHullCount}/${state.totalHullCount} hulls", color = Color.White,
                    fontFamily = FontFamily.Monospace, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(6.dp))
        val frac = if (state.totalHullCount == 0) 0f else state.ownedHullCount.toFloat() / state.totalHullCount
        Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(CyberSurface)) {
            Box(Modifier.fillMaxWidth(frac.coerceIn(0f, 1f)).fillMaxHeight()
                .background(Brush.horizontalGradient(listOf(CyberPrimary, CyberSecondary))))
        }
        if (state.lockedHullId != null || state.nextRotationId != null) {
            Spacer(Modifier.height(8.dp))
            RotationChip(state)
        }
    }
}

@Composable
private fun RotationChip(state: HangarUiState) {
    val text = when {
        state.lockedHullId != null ->
            "🔒 Locked: ${HullCatalog.byId(state.lockedHullId)?.name ?: state.lockedHullId.replace('_', ' ')}"
        state.nextRotationId != null ->
            "🔄 Next: ${HullCatalog.byId(state.nextRotationId)?.name ?: state.nextRotationId.replace('_', ' ')}"
        else -> return
    }
    Box(Modifier.background(CyberSurface, RoundedCornerShape(6.dp))
        .border(1.dp, CyberPrimary.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
        .padding(horizontal = 10.dp, vertical = 4.dp)) {
        Text(text, color = CyberOnSurface, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
    }
}

// ---------------------------------------------------------------- carousel

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HullCarousel(state: HangarUiState, actions: HangarActions, hulls: List<PremiumHull>) {
    val pager = rememberPagerState(pageCount = { hulls.size })
    HorizontalPager(
        state = pager,
        contentPadding = PaddingValues(horizontal = 28.dp),
        pageSpacing = 12.dp,
        modifier = Modifier.fillMaxWidth()
    ) { page ->
        HullCard(hulls[page], state, actions)
    }
    Spacer(Modifier.height(8.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        hulls.indices.forEach { i ->
            Box(Modifier.padding(2.dp).size(if (i == pager.currentPage) 8.dp else 5.dp)
                .clip(RoundedCornerShape(50))
                .background(if (i == pager.currentPage) CyberPrimary else CyberOnSurface.copy(alpha = 0.3f)))
        }
    }
}

private fun priceLabel(h: PremiumHull, state: HangarUiState): String =
    state.storePrices[h.productId] ?: h.tier?.fallbackPrice ?: ""

@Composable
private fun HullCard(h: PremiumHull, state: HangarUiState, actions: HangarActions) {
    val owned = h.id in state.ownedHullIds
    val active = state.activeHullId == h.id
    val release = state.releaseAtMs[h.id] ?: 0L
    val canBuy = !h.earnedOnly && HullCatalog.canBuyNow(state.isPro, release, state.serverNowMs)
    val msLeft = HullCatalog.millisUntilPublic(release, state.serverNowMs)
    val proEarly = !h.earnedOnly && state.isPro && msLeft > 0

    var tryUntilMs by remember(h.id) { mutableStateOf(0L) }
    var tryLeft by remember(h.id) { mutableStateOf(0) }
    val trying = tryLeft > 0
    LaunchedEffect(tryUntilMs) {
        if (tryUntilMs == 0L) return@LaunchedEffect
        tryLeft = 8
        while (tryLeft > 0) { delay(1000); tryLeft-- }
    }

    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(Brush.verticalGradient(listOf(CyberSurface, CyberBackground)))
            .border(1.5.dp, if (active) CyberPrimary else CyberTertiary.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
            .padding(14.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${h.emoji} ${h.name}", color = Color.White, fontWeight = FontWeight.Black, fontSize = 16.sp)
            if (h.earnedOnly) Badge("EARNED ONLY", Color(0xFFFFD23F))
            else if (h.id == state.loanerHullId) Badge("PRO LOANER", Color(0xFFFFD23F))
            else if (proEarly) Badge("PRO EARLY ACCESS", CyberSecondary)
        }
        Text(h.tagline, color = CyberOnSurface.copy(alpha = 0.75f), fontSize = 12.sp,
            modifier = Modifier.padding(top = 2.dp, bottom = 8.dp))

        // Big live preview. While "Try it" runs we also fire the reaction events so the buyer sees them.
        HangarPreview(h.id, reactions = trying || owned)

        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            h.layers.forEach { Badge(it, CyberPrimary) }
        }
        if (owned && state.isPro) {
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("CHROMA", color = Color(0xFFFFD23F), fontSize = 9.sp, fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold)
                val cur = state.chroma[h.id] ?: 0
                listOf(0, 1, 2, 3).forEach { v ->
                    Box(Modifier.size(22.dp).clip(RoundedCornerShape(50))
                        .background(androidx.compose.ui.graphics.Color.hsv((v * 90f + 190f) % 360f, 0.8f, 1f))
                        .border(if (cur == v) 2.dp else 0.dp, Color.White, RoundedCornerShape(50))
                        .clickable { actions.onSetChroma(h.id, v) })
                }
            }
        } else if (owned && !state.isPro && !h.earnedOnly) {
            Text("🎨 Pro unlocks Chroma recolors", color = CyberOnSurface.copy(alpha = 0.5f), fontSize = 10.sp,
                modifier = Modifier.padding(top = 6.dp))
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            if (!owned) {
                Button(
                    onClick = { if (!trying) { actions.onTryStart(h.id); tryUntilMs = System.nanoTime() } },
                    colors = ButtonDefaults.buttonColors(containerColor = CyberSurface),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.border(1.dp, CyberPrimary, RoundedCornerShape(6.dp))
                ) { Text(if (trying) "TRY ${tryLeft}s" else "▶ TRY IT 8s", color = CyberPrimary,
                    fontFamily = FontFamily.Monospace, fontSize = 11.sp) }
            }
            Spacer(Modifier.weight(1f))
            when {
                active -> Badge("EQUIPPED", CyberPrimary)
                owned -> Button(onClick = { actions.onEquip(h.id) },
                    colors = ButtonDefaults.buttonColors(containerColor = CyberPrimary),
                    shape = RoundedCornerShape(6.dp)) {
                    Text("EQUIP", color = CyberBackground, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                }
                h.earnedOnly -> Text("Annual Pro reward", color = Color(0xFFFFD23F), fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace)
                canBuy -> Button(
                    onClick = { actions.onBuy(h) },
                    enabled = state.purchaseInFlightId == null,
                    colors = ButtonDefaults.buttonColors(containerColor = CyberSecondary),
                    shape = RoundedCornerShape(6.dp)
                ) { Text(if (state.purchaseInFlightId == h.id) "…" else "BUY ${priceLabel(h, state)}",
                    color = Color.White, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold) }
                else -> Column(horizontalAlignment = Alignment.End) {
                    Text("🔓 Public in ${formatCountdown(msLeft)}", color = CyberOnSurface.copy(alpha = 0.8f),
                        fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    Text("Go Pro to buy now", color = CyberSecondary, fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}

@Composable
private fun Badge(text: String, color: Color) {
    Box(Modifier.background(color.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
        .border(1.dp, color.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
        .padding(horizontal = 6.dp, vertical = 2.dp)) {
        Text(text, color = color, fontSize = 9.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
    }
}

internal fun formatCountdown(ms: Long): String {
    val totalMin = ms / 60_000
    val d = totalMin / (60 * 24)
    val h = (totalMin / 60) % 24
    val m = totalMin % 60
    return when {
        d > 0 -> "${d}d ${h}h"
        h > 0 -> "${h}h ${m}m"
        else -> "${m}m"
    }
}

/**
 * Large animated preview. Fires the same HullFx events a run would, on a loop, so reaction layers
 * (close-call ripple, gem burst, zone pulse, boss roar, revive) are visible in the shop.
 */
@Composable
internal fun HangarPreview(hullId: String, reactions: Boolean) {
    val t = rememberInfiniteTransition(label = "hangarPreview")
    val tick by t.animateFloat(
        initialValue = 0f, targetValue = 600f,
        animationSpec = infiniteRepeatable(tween(75000, easing = LinearEasing), RepeatMode.Restart),
        label = "hangarTick"
    )
    val ti = tick.toInt()
    if (reactions) {
        // staggered fake events, ~every 4 seconds of tick time (tick runs ~8/s)
        when (ti % 40) {
            4 -> HullFx.closeCallTick = ti
            12 -> HullFx.gemTick = ti
            20 -> { HullFx.zoneTick = ti; HullFx.zone = (HullFx.zone + 1) % 12 }
            28 -> HullFx.bossTick = ti
            36 -> HullFx.reviveTick = ti
        }
    }
    HullPreview(hullId, Modifier.fillMaxWidth().height(110.dp))
}

// ---------------------------------------------------------------- loadout

@Composable
private fun LoadoutPanel(state: HangarUiState, actions: HangarActions) {
    val ownedPremium = HullCatalog.ALL.filter { it.id in state.ownedHullIds }
    Column {
        Text("LOADOUT & ROTATION", color = CyberPrimary, fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Text("Owned premium hulls swap each sector. The hull you picked last shows up about twice as often. " +
            "Lock one to fly it every sector.", color = CyberOnSurface.copy(alpha = 0.7f), fontSize = 12.sp,
            modifier = Modifier.padding(vertical = 6.dp))
        if (ownedPremium.isEmpty()) {
            Text("No premium hulls yet. Pick one in HULLS and it joins your rotation automatically.",
                color = CyberOnSurface.copy(alpha = 0.6f), fontSize = 12.sp)
            return
        }
        // Lock switch
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp).background(CyberSurface, RoundedCornerShape(8.dp))
            .padding(10.dp), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("🔒 Lock to equipped hull", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text("Stops rotation. Flies only ${HullCatalog.byId(state.activeHullId)?.name ?: "the equipped hull"}.",
                    color = CyberOnSurface.copy(alpha = 0.6f), fontSize = 11.sp)
            }
            Switch(checked = state.lockedHullId != null,
                onCheckedChange = { on -> actions.onSetLock(if (on) state.activeHullId else null) })
        }
        ownedPremium.forEach { h ->
            val inRot = h.id in state.rotationIds
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp).background(CyberSurface, RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text(h.emoji, fontSize = 20.sp, modifier = Modifier.padding(end = 8.dp))
                Column(Modifier.weight(1f)) {
                    Text(h.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    if (state.nextRotationId == h.id && state.lockedHullId == null)
                        Text("Next up", color = CyberPrimary, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                }
                Text("Rotate", color = CyberOnSurface.copy(alpha = 0.6f), fontSize = 10.sp,
                    modifier = Modifier.padding(end = 6.dp))
                Switch(checked = inRot, onCheckedChange = { actions.onToggleRotation(h.id, it) })
            }
        }
    }
}

// ---------------------------------------------------------------- vault + drop

@Composable
private fun VaultPanel(state: HangarUiState, actions: HangarActions) {
    val now = state.serverNowMs
    val upcomingMs = (state.nextDropAtMs - now).coerceAtLeast(0L)
    Column {
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(Brush.horizontalGradient(listOf(CyberTertiary.copy(alpha = 0.5f), CyberSecondary.copy(alpha = 0.4f))))
            .padding(14.dp)) {
            Column {
                Text("🚀 NEXT HULL DROP", color = Color.White, fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                Text(formatCountdown(upcomingMs), color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Black)
                Text(if (state.isPro) "Pro: you get it ${HullCatalog.EARLY_ACCESS_HOURS / 24} days early."
                     else "Pro members get it ${HullCatalog.EARLY_ACCESS_HOURS / 24} days early.",
                    color = Color.White.copy(alpha = 0.85f), fontSize = 11.sp)
            }
        }
        if (state.isPro) {
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth().background(CyberSurface, RoundedCornerShape(10.dp)).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("💎 Monthly Pro stipend", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text("${state.proMonths} months of Pro", color = CyberOnSurface.copy(alpha = 0.6f), fontSize = 10.sp)
                }
                Button(onClick = { actions.onClaimStipend() }, enabled = state.stipendClaimable,
                    colors = ButtonDefaults.buttonColors(containerColor = CyberPrimary), shape = RoundedCornerShape(6.dp)) {
                    Text(if (state.stipendClaimable) "CLAIM 30" else "CLAIMED", color = CyberBackground,
                        fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text("THE VAULT", color = CyberPrimary, fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Text("Every hull from past drops stays available here. Nothing leaves forever.",
            color = CyberOnSurface.copy(alpha = 0.7f), fontSize = 12.sp, modifier = Modifier.padding(vertical = 6.dp))
        val past = HullCatalog.SOLD.filter {
            it.releaseMonthIndex == 0 || (state.releaseAtMs[it.id] ?: 0L) <= now
        }
        past.forEach { h ->
            val owned = h.id in state.ownedHullIds
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp).background(CyberSurface, RoundedCornerShape(8.dp))
                .padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(h.emoji, fontSize = 22.sp, modifier = Modifier.padding(end = 10.dp))
                Column(Modifier.weight(1f)) {
                    Text(h.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text(h.layers.joinToString(" · "), color = CyberOnSurface.copy(alpha = 0.6f), fontSize = 10.sp)
                }
                if (owned) Badge("OWNED", CyberPrimary)
                else Button(onClick = { actions.onBuy(h) },
                    enabled = state.purchaseInFlightId == null &&
                        HullCatalog.canBuyNow(state.isPro, state.releaseAtMs[h.id] ?: 0L, now),
                    colors = ButtonDefaults.buttonColors(containerColor = CyberSecondary),
                    shape = RoundedCornerShape(6.dp)) {
                    Text(priceLabel(h, state), color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- celebration

@Composable
private fun HullCelebration(hullId: String, onDismiss: () -> Unit) {
    val h = HullCatalog.byId(hullId) ?: return
    LaunchedEffect(hullId) { delay(6000); onDismiss() }
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color(0xEE000000)).clickable { onDismiss() }.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Text("✨ NEW HULL UNLOCKED ✨", color = Color(0xFFFFD23F), fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace, fontSize = 14.sp)
            Spacer(Modifier.height(10.dp))
            HangarPreview(h.id, reactions = true)
            Text("${h.emoji} ${h.name}", color = Color.White, fontWeight = FontWeight.Black, fontSize = 22.sp)
            Text("Equipped and added to your rotation.", color = CyberOnSurface, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
            Spacer(Modifier.height(14.dp))
            Text("tap to continue", color = CyberOnSurface.copy(alpha = 0.5f), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
        }
    }
}
