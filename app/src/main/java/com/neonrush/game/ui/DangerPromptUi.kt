package com.neonrush.game.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neonrush.game.AirSupport
import com.neonrush.game.BossBrood
import com.neonrush.game.BombDepot
import com.neonrush.game.BroodBomb
import com.neonrush.game.MilShield
import kotlinx.coroutines.delay

/**
 * SMART DANGER PROMPT (Stage 169). The game senses real danger and, if the weapon that would help is OUT OF STOCK, flashes a buy chip
 * for [SHOW_MS] (half a second, fading out over its last part). Not tapped = it fades away. It only comes back with a NEW danger
 * (the danger must calm down for [CALM_MS] first), and never twice within [GAP_MS].
 * Prices are tunable here. Bought items are used at once (shield switches on, bombs/airstrikes go into stock).
 */
object DangerPrompt {
    const val SHOW_MS = 500L
    const val CALM_MS = 2500L
    const val GAP_MS = 4000L
    const val MIL_GEMS = 70          // 1 military shield (same per-shield price as the 5-pack)
    const val BOMB_GEMS = 60         // 3 bombs into the Bomb Depot reserve
    const val AIR_GEMS = 100         // 1 airstrike call
}

private enum class Need(val icon: String, val label: String, val gems: Int) {
    MIL("🛡", "SHIELD OUT! +1 MIL", DangerPrompt.MIL_GEMS),
    BOMB("💣", "OUT OF BOMBS! +3", DangerPrompt.BOMB_GEMS),
    AIR("🚁", "NEED BACKUP! +1 CALL", DangerPrompt.AIR_GEMS)
}

@Composable
fun DangerPromptOverlay(active: Boolean, fuel: Int, shields: Int, bossActive: Boolean, gems: Int, spend: (Int, (Boolean) -> Unit) -> Unit, modifier: Modifier = Modifier) {
    if (!active) return
    val ctx = LocalContext.current
    val fuelRef = rememberUpdatedState(fuel); val shRef = rememberUpdatedState(shields)
    val bossRef = rememberUpdatedState(bossActive); val gemRef = rememberUpdatedState(gems)
    var need by remember { mutableStateOf<Need?>(null) }
    var age by remember { mutableStateOf(0L) }

    LaunchedEffect(Unit) {
        val hits = ArrayList<Long>()
        var lastFuel = fuelRef.value; var lastSh = shRef.value
        var lastShown = -99999L; var calmSince = System.currentTimeMillis(); var armed = true
        while (true) {
            delay(100)
            val now = System.currentTimeMillis()
            val f = fuelRef.value; val s = shRef.value
            if (s < lastSh || f <= lastFuel - 5) hits.add(now)
            lastFuel = f; lastSh = s
            hits.removeAll { now - it > 5000L }
            val babies = BossBrood.hasBabies()
            val danger = hits.size >= 2 || babies || f <= 25
            if (!danger) { if (now - calmSince > DangerPrompt.CALM_MS) armed = true } else calmSince = now
            if (need != null) { age = now - lastShown; if (age >= DangerPrompt.SHOW_MS) need = null; continue }
            if (!danger || !armed || now - lastShown < DangerPrompt.GAP_MS) continue
            val g = gemRef.value
            val pick = when {
                babies && BroodBomb.count(ctx) == 0 && g >= DangerPrompt.BOMB_GEMS -> Need.BOMB
                bossRef.value && hits.size >= 3 && AirSupport.calls(ctx) == 0 && g >= DangerPrompt.AIR_GEMS -> Need.AIR
                hits.size >= 2 && !MilShield.active() && MilShield.stock(ctx) == 0 && g >= DangerPrompt.MIL_GEMS -> Need.MIL
                else -> null
            }
            if (pick != null) { need = pick; lastShown = now; age = 0; armed = false }
        }
    }

    val n = need
    if (n != null) {
        val a = (1f - ((age - 300L).coerceAtLeast(0L) / 200f)).coerceIn(0f, 1f)   // fades over the last 0.2 s
        Box(modifier.fillMaxSize()) {
            Row(Modifier.align(Alignment.TopCenter).padding(top = 130.dp).alpha(a).clip(RoundedCornerShape(14.dp)).background(Color(0xEE2A0A0A))
                .border(2.dp, Color(0xFFFF5252), RoundedCornerShape(14.dp))
                .clickable {
                    need = null
                    spend(n.gems) { ok ->
                        if (ok) when (n) {
                            Need.MIL -> { MilShield.addStock(ctx, 1); MilShield.activateFromStock(ctx) }
                            Need.BOMB -> BombDepot.addReserve(ctx, 3)
                            Need.AIR -> AirSupport.addCalls(ctx, 1)
                        }
                    }
                }.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(n.icon, fontSize = 20.sp)
                Spacer(Modifier.width(8.dp))
                Text("${n.label} · ${n.gems} 💎", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
            }
        }
    }
}
