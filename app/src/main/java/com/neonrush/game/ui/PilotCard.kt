package com.neonrush.game.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.neonrush.game.AuraRank
import com.neonrush.game.HullCatalog
import com.neonrush.game.R
import kotlinx.coroutines.delay

/** Stats the title/badge unlock rules read. Build from GameProfile + ProSnapshot + HangarUiState. */
data class PilotStats(
    val bestScore: Int = 0, val bestZone: Int = 1, val streak: Int = 0, val totalRuns: Int = 0,
    val masteryLevel: Int = 0, val hullsOwned: Int = 0, val proMonths: Int = 0,
    val isLegend: Boolean = false, val rank: AuraRank = AuraRank.NONE
)

data class PilotTitle(val id: String, val text: String, val hint: String, val unlocked: (PilotStats) -> Boolean)
data class PilotBadge(val id: String, val emoji: String, val name: String, val unlocked: (PilotStats) -> Boolean)

object PilotIdentity {
    val TITLES = listOf(
        PilotTitle("rookie", "Rookie Pilot", "Everyone starts here") { true },
        PilotTitle("streak", "Streak Runner", "7-day streak") { it.streak >= 7 },
        PilotTitle("veteran", "Neon Veteran", "100 runs") { it.totalRuns >= 100 },
        PilotTitle("breaker", "Zone Breaker", "Reach zone 20") { it.bestZone >= 20 },
        PilotTitle("master", "Mastermind", "Mastery level 10") { it.masteryLevel >= 10 },
        PilotTitle("collector", "Hull Collector", "Own 8 hulls") { it.hullsOwned >= 8 },
        PilotTitle("baron", "Hull Baron", "Own 15 hulls") { it.hullsOwned >= 15 },
        PilotTitle("aura", "Aura Bearer", "Reach Silver Pro") { it.rank.ordinal >= AuraRank.SILVER.ordinal },
        PilotTitle("legend", "LEGEND", "Annual Pro / Year Edition") { it.isLegend }
    )
    val BADGES = listOf(
        PilotBadge("flame", "🔥", "7-day streak") { it.streak >= 7 },
        PilotBadge("zone", "🌀", "Zone 10+") { it.bestZone >= 10 },
        PilotBadge("star", "⭐", "Mastery 5") { it.masteryLevel >= 5 },
        PilotBadge("gem", "💎", "Diamond Pro") { it.rank == AuraRank.DIAMOND },
        PilotBadge("crown", "👑", "Legend") { it.isLegend },
        PilotBadge("hulls", "🛸", "5 hulls") { it.hullsOwned >= 5 },
        PilotBadge("vet", "🏅", "100 runs") { it.totalRuns >= 100 },
        PilotBadge("pro", "⚡", "Pro member") { it.rank != AuraRank.NONE }
    )
    const val MAX_BADGES = 3
    fun title(id: String) = TITLES.firstOrNull { it.id == id } ?: TITLES.first()
}

/** World backdrops for the pilot card / avatar. Colors by world band (every 10 zones). */
object WorldBackdrops {
    private val PALETTE = listOf(
        listOf(Color(0xFF0A0420), Color(0xFF1B0B4A)), listOf(Color(0xFF021A24), Color(0xFF0B4D5C)),
        listOf(Color(0xFF1F0A0A), Color(0xFF5C1B0B)), listOf(Color(0xFF0A1F0A), Color(0xFF1B5C2A)),
        listOf(Color(0xFF1A0A24), Color(0xFF5C0B5A)), listOf(Color(0xFF05050F), Color(0xFF2A2A5C))
    )
    fun forZone(zone: Int): List<Color> = PALETTE[((zone - 1).coerceAtLeast(0) / 10) % PALETTE.size]
}

/** What a leaderboard row / ghost / profile needs to render the full pilot. */
data class PilotCardData(
    val name: String, val hullId: String, val bestScore: Int, val bestZone: Int,
    val rank: AuraRank = AuraRank.NONE, val isLegend: Boolean = false,
    val titleId: String = "rookie", val badgeIds: List<String> = emptyList(), val isPro: Boolean = false
)

/** Tiny hull thumbnail for leaderboard rows and ghost chips. */
@Composable
fun LeaderboardAvatar(hullId: String, modifier: Modifier = Modifier.size(width = 84.dp, height = 30.dp)) {
    HullPreview(hullId, modifier)
}

@Composable
fun PilotCardDialog(d: PilotCardData, onDismiss: () -> Unit) {
    val t = rememberInfiniteTransition(label = "pilotCard")
    val tick by t.animateFloat(0f, 600f, infiniteRepeatable(tween(75000, easing = LinearEasing), RepeatMode.Restart), label = "t")
    var preview by remember { mutableStateOf(false) }
    var left by remember { mutableStateOf(0) }
    LaunchedEffect(preview) { if (preview) { left = 8; while (left > 0) { delay(1000); left-- }; preview = false } }
    val hull = HullCatalog.byId(d.hullId)

    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
            .background(Brush.verticalGradient(WorldBackdrops.forZone(d.bestZone)))
            .border(1.5.dp, if (d.rank == AuraRank.NONE) Color(0xFF00E5FF) else Color(0xFFFFD23F), RoundedCornerShape(18.dp))
            .padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.fillMaxWidth().height(if (preview) 150.dp else 100.dp), contentAlignment = Alignment.Center) {
                HangarPreview(d.hullId, reactions = true)
                LivingAvatar(tick.toInt(), Modifier.align(Alignment.CenterEnd).padding(end = 28.dp)) {
                    Image(painterResource(R.drawable.pilot_run_1), null, Modifier.size(64.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
            if (d.isPro) ProName(d.name, d.isLegend, fontSize = 20.sp)
            else Text(d.name, color = Color.White, fontWeight = FontWeight.Black, fontSize = 20.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                AuraBadge(d.rank)
                if (d.isLegend) LegendTitle()
            }
            Text(PilotIdentity.title(d.titleId).text, color = Color(0xFFC9B8FF), fontSize = 12.sp,
                fontFamily = FontFamily.Monospace, modifier = Modifier.padding(top = 2.dp))
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(PilotIdentity.MAX_BADGES) { i ->
                    val b = d.badgeIds.getOrNull(i)?.let { id -> PilotIdentity.BADGES.firstOrNull { it.id == id } }
                    Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = 0.08f))
                        .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                        Text(b?.emoji ?: "·", fontSize = 18.sp)
                    }
                }
            }
            Text("Best ${d.bestScore}  ·  Zone ${d.bestZone}", color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp,
                modifier = Modifier.padding(top = 10.dp))
            Text("Flying ${hull?.let { it.emoji + " " + it.name } ?: d.hullId.replace('_', ' ')}", color = Color.White,
                fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
            if (hull != null && !hull.earnedOnly) {
                Button(onClick = { preview = true }, enabled = !preview,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF007F)),
                    shape = RoundedCornerShape(8.dp), modifier = Modifier.padding(top = 10.dp)) {
                    Text(if (preview) "PREVIEW ${left}s" else "▶ TRY THIS HULL 8s", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                }
            }
        }
    }
}


/** Until the player picks their own, show the best unlocked title and the first 3 unlocked badges. */
fun PilotIdentity.autoTitleId(s: PilotStats): String =
    if (s.isLegend) "legend" else TITLES.lastOrNull { it.unlocked(s) }?.id ?: "rookie"

fun PilotIdentity.autoBadgeIds(s: PilotStats): List<String> =
    BADGES.filter { it.unlocked(s) }.take(MAX_BADGES).map { it.id }
