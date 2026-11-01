package com.neonrush.game.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.neonrush.game.NrUpgrades

private val UB_MONO = FontFamily.Monospace
private val UB_CYAN = Color(0xFF4DD0E1)
private val UB_GREEN = Color(0xFF69F0AE)

/** What each step of each track does, in plain words (the numbers match NrUpgrades / Lance). Index = track id from NrUpgrades. */
private val UB_EFFECTS: Map<Int, List<String>> = mapOf(
    NrUpgrades.GUN to listOf("Bolts +12% damage", "Bolts +25% · wider hit band on obstacles", "Bolts +40% · wider hit band", "Bolts +60% · widest hit band"),
    NrUpgrades.BOMB to listOf("Blast +20% damage", "Blast +40% damage", "Blast +70% damage", "Blast +100% damage (double)"),
    NrUpgrades.HELI to listOf("Missiles +15% damage", "Missiles +30% damage", "Missiles +50% damage", "Missiles +75% damage · ONE EXTRA MISSILE"),
    NrUpgrades.JET to listOf("Strikes +15% damage", "Strikes +30% damage", "Strikes +50% damage", "Strikes +75% damage · ONE EXTRA MISSILE"),
    NrUpgrades.LANCE to listOf("8% of the boss hull at close range (base 6%)", "11% of the boss hull at close range", "15% of the boss hull at close range", "20% of the boss hull at close range · point blank x1.5"),
    NrUpgrades.RACK to listOf("Carry 1 more Brood Bomb", "Carry 2 more Brood Bombs", "Carry 3 more Brood Bombs"),
    NrUpgrades.STORE to listOf("Carry 2 lance cells", "Carry 3 lance cells", "Carry 4 lance cells")
)
private val UB_ORDER = listOf(NrUpgrades.GUN, NrUpgrades.BOMB, NrUpgrades.HELI, NrUpgrades.JET, NrUpgrades.LANCE, NrUpgrades.RACK, NrUpgrades.STORE)

/** Home-screen card for the Upgrade Bay (weapon and aircraft upgrades). */
@Composable
fun UpgradeBayCard(onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    var owned = 0; var total = 0
    for (t in UB_ORDER) { owned += NrUpgrades.level(ctx, t); total += NrUpgrades.TRACKS[t].steps }
    val shape = RoundedCornerShape(16.dp)
    Row(modifier.fillMaxWidth().clip(shape).background(Brush.horizontalGradient(listOf(Color(0xFF0B2A33), Color(0xFF0B1536)))).border(2.dp, UB_CYAN.copy(alpha = 0.7f), shape).clickable { onOpen() }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("UPGRADE BAY", color = UB_CYAN, fontSize = 13.sp, fontWeight = FontWeight.Black, fontFamily = UB_MONO)
            Text("Gun · Bomb · Helicopter · Jet · Lance · Storage   ($owned / $total owned)", color = Color.White.copy(alpha = 0.85f), fontSize = 10.sp, fontFamily = UB_MONO)
        }
        Text("OPEN ▸", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Black, fontFamily = UB_MONO, modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(UB_CYAN).padding(horizontal = 12.dp, vertical = 8.dp))
    }
}

/**
 * UPGRADE BAY (Stage 158): the shop page for every weapon and aircraft upgrade that is not part of the Combat Wingman (that has its own page).
 * One-time purchases, bought in order, one Mk at a time. [onBuy] (track, step, callback) starts the store purchase; the caller applies it and answers true/false.
 */
@Composable
fun UpgradeBayDialog(testing: Boolean, onClose: () -> Unit, onBuy: (Int, Int, (Boolean) -> Unit) -> Unit) {
    val ctx = LocalContext.current
    var msg by remember { mutableStateOf("") }; var good by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    @Suppress("UNUSED_VARIABLE") val ver = NrUpgrades.version   // recompose after a purchase

    fun buy(track: Int, step: Int) {
        if (busy) return
        val why = NrUpgrades.blockReason(ctx, track, step)
        if (why != null) { good = false; msg = why; return }
        busy = true
        onBuy(track, step) { ok ->
            busy = false; good = ok
            msg = if (ok) "Done: ${NrUpgrades.TRACKS[track].name} ${NrUpgrades.TRACKS[track].labels[step - 1]}" else "Purchase cancelled"
        }
    }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)) {
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF0A1E26), Color(0xFF0B0820), Color(0xFF06040F))))) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 28.dp, bottom = 40.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("UPGRADE BAY", color = UB_CYAN, fontSize = 22.sp, fontWeight = FontWeight.Black, fontFamily = UB_MONO)
                        Text("One-time purchases · buy in order · yours forever", color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp, fontFamily = UB_MONO)
                    }
                    Box(Modifier.size(40.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.1f)).border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape).clickable { onClose() }, contentAlignment = Alignment.Center) { Text("✕", color = Color.White, fontSize = 16.sp) }
                }
                if (testing) Text("TESTING MODE: every upgrade is free", color = Color(0xFFFFD54F), fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = UB_MONO, modifier = Modifier.padding(top = 6.dp))
                if (msg.isNotEmpty()) Text(msg, color = if (good) UB_GREEN else Color(0xFFFF8A80), fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = UB_MONO, modifier = Modifier.padding(top = 8.dp))
                for (track in UB_ORDER) {
                    val tr = NrUpgrades.TRACKS[track]
                    val lvl = NrUpgrades.level(ctx, track)
                    Text("${tr.name}  ·  $lvl / ${tr.steps}", color = UB_CYAN, fontSize = 12.sp, fontWeight = FontWeight.Black, fontFamily = UB_MONO, modifier = Modifier.padding(top = 16.dp))
                    Text(tr.blurb, color = Color.White.copy(alpha = 0.65f), fontSize = 9.sp, fontFamily = UB_MONO, modifier = Modifier.padding(bottom = 6.dp))
                    val eff = UB_EFFECTS[track] ?: emptyList()
                    for (s in 1..tr.steps) {
                        UbRow(tr.labels[s - 1], eff.getOrElse(s - 1) { "" }, tr.prices[s - 1], owned = lvl >= s, next = s == lvl + 1, busy = busy) { buy(track, s) }
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text("The Combat Wingman and its arms and skins have their own page (unlocks at zone 30).", color = Color.White.copy(alpha = 0.55f), fontSize = 9.sp, fontFamily = UB_MONO)
                Spacer(Modifier.height(6.dp))
                Text("Free players can still win everything. Upgrades are a power and speed-up choice, not a requirement. Purchases are restored automatically on a new phone.", color = Color.White.copy(alpha = 0.5f), fontSize = 9.sp, fontFamily = UB_MONO)
            }
        }
    }
}

@Composable
private fun UbRow(title: String, blurb: String, price: String, owned: Boolean, next: Boolean, busy: Boolean, onBuy: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(shape).background(Color.White.copy(alpha = 0.06f)).border(1.dp, UB_CYAN.copy(alpha = if (owned) 0.9f else if (next) 0.5f else 0.2f), shape).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = if (owned) UB_CYAN else Color.White.copy(alpha = if (next) 1f else 0.6f), fontSize = 12.sp, fontWeight = FontWeight.Black, fontFamily = UB_MONO)
            Text(blurb, color = Color.White.copy(alpha = if (next || owned) 0.75f else 0.45f), fontSize = 9.sp, fontFamily = UB_MONO)
        }
        Text(if (owned) "OWNED ✓" else price, color = if (owned) UB_GREEN else Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Black, fontFamily = UB_MONO,
            modifier = Modifier.clip(RoundedCornerShape(10.dp)).then(if (owned) Modifier else Modifier.background(if (next) UB_CYAN else Color.White.copy(alpha = 0.25f))).clickable(enabled = !owned && !busy) { onBuy() }.padding(horizontal = 12.dp, vertical = 8.dp))
    }
}
