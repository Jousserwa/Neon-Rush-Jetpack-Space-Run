package com.neonrush.game.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.neonrush.game.NrUpgrades
import com.neonrush.game.Wingman
import com.neonrush.game.WingmanContracts
import com.neonrush.game.WingmanRank
import com.neonrush.game.WingmanPerks
import kotlin.math.sin

private val MONOW = FontFamily.Monospace
private val AMB = Color(0xFFFFB74D)

/** Home-screen card for the Combat Wingman: animated, and advertised (locked) until zone 30. */
@Composable
fun WingmanCard(bestZone: Int, testing: Boolean, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val (frames, custom) = rememberWingFrames()
    val tm = rememberTextMeasurer()
    val inf = rememberInfiniteTransition(label = "wingCard")
    val clock by inf.animateFloat(0f, 600f, infiniteRepeatable(tween(600_000, easing = LinearEasing)), label = "clock")
    val open = Wingman.canBuy(bestZone, testing)
    val owned = NrUpgrades.level(ctx, NrUpgrades.WING)
    val skin = Wingman.SKINS[Wingman.equippedSkin(ctx)]
    val shape = RoundedCornerShape(16.dp)
    Box(modifier.fillMaxWidth().clip(shape).background(Brush.horizontalGradient(listOf(Color(0xFF1B2410), Color(0xFF0B1536)))).border(2.dp, AMB.copy(alpha = if (open) 0.8f else 0.35f), shape).clickable { onOpen() }) {
        Canvas(Modifier.matchParentSize()) {
            val p = (clock / 6f) % 1f
            val c = Offset(size.width * (0.55f + 0.3f * p), size.height * 0.5f)
            drawWingmanPilot(c, size.height * 0.95f, frames[(clock * 10f).toInt() % frames.size], custom, clock, skin, 2, 1, 0f, 1f, "", tm)
            for (i in 0 until 14) drawCircle(Color.White.copy(alpha = 0.2f), 1.5f, Offset(size.width * ((i * 0.173f + clock * 0.01f) % 1f), size.height * ((i * 0.37f) % 1f)))
            if (!open) drawRect(Color.Black.copy(alpha = 0.35f))
        }
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("COMBAT WINGMAN", color = AMB, fontSize = 13.sp, fontWeight = FontWeight.Black, fontFamily = MONOW)
                Text(if (!open) "🔒 Unlocks at zone 30 · you are at zone $bestZone" else if (owned > 0 || testing) "$owned sortie(s) per boss round · tap for upgrades" else "A military copy of you, fighting at your side",
                    color = Color.White.copy(alpha = 0.85f), fontSize = 10.sp, fontFamily = MONOW)
            }
            Text(if (open) "OPEN ▸" else "PREVIEW ▸", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Black, fontFamily = MONOW, modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(AMB).padding(horizontal = 12.dp, vertical = 8.dp))
        }
    }
}

/**
 * COMBAT WINGMAN page: an animated preview (the wingman flying with its gunships, collecting ordnance and firing), the zone-30 lock with progress when locked,
 * how it works, the two sortie tiers ($4.99 / $9.99), Wingman Arms Mk II-V, the ordnance list and the skins. Everything is visible when locked; buying needs zone 30 (testing mode: free).
 * [onBuy] (track, step, callback) and [onBuySkin] (index, callback) start the store purchase (the caller applies it and answers true/false).
 */
@Composable
fun WingmanPageDialog(bestZone: Int, testing: Boolean, onClose: () -> Unit, onBuy: (Int, Int, (Boolean) -> Unit) -> Unit, onBuySkin: (Int, (Boolean) -> Unit) -> Unit,
    credit: (Int) -> Unit = {}, spend: (Int, (Boolean) -> Unit) -> Unit = { _, cb -> cb(false) }, onBuyDmg: (String, (Boolean) -> Unit) -> Unit = { _, cb -> cb(false) }) {
    val ctx = LocalContext.current
    val dens = LocalDensity.current.density
    val tm = rememberTextMeasurer()
    val (frames, custom) = rememberWingFrames()
    val inf = rememberInfiniteTransition(label = "wingPage")
    val clock by inf.animateFloat(0f, 600f, infiniteRepeatable(tween(600_000, easing = LinearEasing)), label = "clock")
    val open = Wingman.canBuy(bestZone, testing)
    var msg by remember { mutableStateOf("") }; var good by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    val equipped = Wingman.equippedSkin(ctx)
    val skin = Wingman.SKINS[equipped]
    val wing = NrUpgrades.level(ctx, NrUpgrades.WING); val arms = NrUpgrades.level(ctx, NrUpgrades.WARMS)

    fun buy(track: Int, step: Int) {
        if (busy) return
        if (!open) { good = false; msg = "Locked: reach zone ${Wingman.UNLOCK_ZONE} to buy (you are at zone $bestZone)"; return }
        val why = NrUpgrades.blockReason(ctx, track, step)
        if (why != null) { good = false; msg = why; return }
        busy = true
        onBuy(track, step) { ok -> busy = false; good = ok; msg = if (ok) "Done: ${NrUpgrades.TRACKS[track].name} ${NrUpgrades.TRACKS[track].labels[step - 1]}" else "Purchase cancelled" }
    }
    fun buySkin(i: Int) {
        if (busy) return
        if (Wingman.ownsSkin(ctx, i)) { Wingman.equipSkin(ctx, i); good = true; msg = "${Wingman.SKINS[i].name} equipped"; return }
        if (!open) { good = false; msg = "Locked: reach zone ${Wingman.UNLOCK_ZONE} to buy skins"; return }
        val skinWhy = if (testing) null else Wingman.skinLock(ctx)
        if (skinWhy != null) { good = false; msg = skinWhy; return }
        busy = true
        onBuySkin(i) { ok -> busy = false; good = ok; msg = if (ok) "${Wingman.SKINS[i].name} unlocked" else "Purchase cancelled" }
    }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)) {
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF141C0C), Color(0xFF0B0820), Color(0xFF06040F))))) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 28.dp, bottom = 40.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("COMBAT WINGMAN", color = AMB, fontSize = 22.sp, fontWeight = FontWeight.Black, fontFamily = MONOW)
                        Text("A military copy of you · fights at your side", color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp, fontFamily = MONOW)
                    }
                    Box(Modifier.size(40.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.1f)).border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape).clickable { onClose() }, contentAlignment = Alignment.Center) { Text("✕", color = Color.White, fontSize = 16.sp) }
                }
                Spacer(Modifier.height(12.dp))
                // ---- animated preview ----
                val hs = RoundedCornerShape(18.dp)
                Box(Modifier.fillMaxWidth().height(210.dp).clip(hs).border(2.dp, AMB.copy(alpha = 0.55f), hs)) {
                    Canvas(Modifier.matchParentSize()) {
                        drawRect(Brush.verticalGradient(listOf(Color(0xFF1A2A12), Color(0xFF0E1A2E), Color(0xFF1B1030))))
                        for (i in 0 until 26) drawCircle(Color.White.copy(alpha = (0.35f + 0.3f * sin(clock * 2f + i)).coerceIn(0f, 1f)), (0.8f + (i % 3) * 0.6f) * dens, Offset(size.width * ((i * 0.137f) % 1f), size.height * ((i * 0.291f) % 1f)))
                        val c = Offset(size.width * 0.4f, size.height * (0.52f + 0.03f * sin(clock * 1.4f)))
                        // a crate drifts in and a volley of tracers leaves the guns
                        val cp = (clock / 4f) % 1f
                        drawCircle(Color(Wingman.ORD_COLS[((clock / 4f).toInt()) % 3]).copy(alpha = 0.85f), 7f * dens, Offset(size.width * (1.05f - 0.75f * cp), size.height * 0.3f))
                        for (k in 0 until 3) { val tp = ((clock * 3f + k * 0.33f) % 1f); drawLine(Color(skin.accent).copy(alpha = 1f - tp), Offset(c.x + size.height * 0.4f + size.width * 0.5f * tp, c.y - 4f), Offset(c.x + size.height * 0.4f + size.width * 0.5f * tp + 30f * dens, c.y - 4f), 3f * dens, StrokeCap.Round) }
                        drawWingmanPilot(c, size.height * 0.62f, frames[(clock * 10f).toInt() % frames.size], custom, clock, skin, arms.coerceAtLeast(1), if (arms >= 2) 2 else 1, 0f, 1f, "WINGMAN", tm)
                        if (!open) drawRect(Color.Black.copy(alpha = 0.3f))
                    }
                    if (!open) Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color(0xCC000000)).padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🔒 LOCKED · REACH ZONE ${Wingman.UNLOCK_ZONE}", color = AMB, fontSize = 12.sp, fontWeight = FontWeight.Black, fontFamily = MONOW)
                        Box(Modifier.padding(top = 4.dp).fillMaxWidth(0.7f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(Color.White.copy(alpha = 0.15f))) {
                            Box(Modifier.fillMaxWidth((bestZone / Wingman.UNLOCK_ZONE.toFloat()).coerceIn(0f, 1f)).fillMaxHeight().background(AMB))
                        }
                        Text("zone $bestZone / ${Wingman.UNLOCK_ZONE}", color = Color.White.copy(alpha = 0.8f), fontSize = 9.sp, fontFamily = MONOW)
                    }
                }
                if (msg.isNotEmpty()) Text(msg, color = if (good) Color(0xFF69F0AE) else Color(0xFFFF8A80), fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = MONOW, modifier = Modifier.padding(top = 8.dp))
                Spacer(Modifier.height(10.dp))
                Section("HOW IT WORKS")
                for (l in listOf("A real copy of your pilot in a military suit, with a rifle, jet-pack and gunship drones.", "It flies beside you and moves when you move, shoots by itself and every time you shoot.",
                    "It collects its own ordnance crates: rocket pod, minigun, ion cannon.", "Its button appears whenever a boss attacks, or 2 or more hazards attack at once. Each call = 3 sorties of 3 seconds, with a 2 second reload between them.", "Its shots grow stronger during a call (focus fire). Boss shields still stop its bullets, and it cannot stop blink strikes: that is a job for the airstrike.", "\$4.99: 3 calls per boss round and 15 hazard calls per run. \$9.99 ELITE: 5 boss calls and UNLIMITED hazard calls.")) Bullet(l)
                Section("CALLS (one-time)")
                for (s in 1..2) {
                    val tr = NrUpgrades.TRACKS[NrUpgrades.WING]
                    UpRow(tr.labels[s - 1], if (s == 1) "3 calls per boss round, 15 hazard calls per run." else "5 boss calls per round, UNLIMITED hazard calls, more perks. Replaces the \$4.99 tier" + (if (wing == 1) " (upgrade price)." else "."), if (s == 2 && wing == 1) "\$4.99" else tr.prices[s - 1], wing >= s, open, busy) { buy(NrUpgrades.WING, s) }
                }
                Section("PERKS")
                for (l in listOf("COLLECTOR (both tiers): it grabs gems and fuel in its lane while it is out.", "DAILY PILL (both tiers): 1 free healing pill every day.", "ELITE: BULLET SCREEN takes 1 enemy shot per second for you.", "ELITE: TOW, once per run at 8% fuel it gives you +${WingmanPerks.TOW_GIVES}% fuel.", "ELITE: RANK 1-10 with rewards, and a DAILY CONTRACT that pays gems and a score voucher.")) Bullet(l)
                Section("HEALING PILLS · you have ${Wingman.pills(ctx)}")
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.06f)).border(1.dp, AMB.copy(alpha = 0.5f), RoundedCornerShape(12.dp)).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${WingmanPerks.PILL_PACK} pills · +${Wingman.PILL_HEAL.toInt()}% hull each", color = Color.White, fontSize = 11.sp, fontFamily = MONOW, modifier = Modifier.weight(1f))
                    Text("BUY ${WingmanPerks.PILL_PACK_GEMS} 💎", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Black, fontFamily = MONOW,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(AMB).clickable { spend(WingmanPerks.PILL_PACK_GEMS) { ok -> if (ok) { Wingman.addPill(ctx, WingmanPerks.PILL_PACK); good = true; msg = "+${WingmanPerks.PILL_PACK} pills" } else { good = false; msg = "Not enough gems" } } }.padding(horizontal = 10.dp, vertical = 6.dp))
                }
                Section("RANK & CONTRACT (Elite)")
                if (wing >= 2 || testing) {
                    val lvl = WingmanRank.level(ctx); val (xi, xn) = WingmanRank.xpIntoLevel(ctx)
                    Text("RANK $lvl / ${WingmanRank.MAX}  ·  $xi / $xn XP", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = MONOW)
                    Box(Modifier.fillMaxWidth().padding(vertical = 4.dp).height(6.dp).clip(RoundedCornerShape(3.dp)).background(Color.White.copy(alpha = 0.12f))) { Box(Modifier.fillMaxWidth((xi.toFloat() / xn).coerceIn(0f, 1f)).height(6.dp).background(AMB)) }
                    val pend = WingmanRank.pending(ctx)
                    if (pend > 0) Text("CLAIM RANK $pend REWARD · ${WingmanRank.rewardText(pend)}", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Black, fontFamily = MONOW,
                        modifier = Modifier.padding(vertical = 4.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF69F0AE)).clickable { val g = WingmanRank.claim(ctx); if (g > 0) credit(g); good = true; msg = "Rank reward claimed" }.padding(horizontal = 10.dp, vertical = 7.dp))
                    else if (lvl < WingmanRank.MAX) Text("Next reward at rank ${lvl + 1}: ${WingmanRank.rewardText(lvl + 1)}", color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp, fontFamily = MONOW)
                    Spacer(Modifier.height(6.dp))
                    val prog = WingmanContracts.progress(ctx)
                    Text("TODAY: ${WingmanContracts.name()}  ($prog / ${WingmanContracts.goal()})", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = MONOW)
                    if (WingmanContracts.ready(ctx)) Text("CLAIM ${WingmanContracts.GEMS} 💎 + 1 🎟", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Black, fontFamily = MONOW,
                        modifier = Modifier.padding(vertical = 4.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF69F0AE)).clickable { val g = WingmanContracts.claim(ctx); if (g > 0) credit(g); good = true; msg = "Contract paid" }.padding(horizontal = 10.dp, vertical = 7.dp))
                    else if (WingmanContracts.claimed(ctx)) Text("Done today. A new contract tomorrow.", color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp, fontFamily = MONOW)
                } else Text("Unlocked with the \$9.99 ELITE wingman.", color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp, fontFamily = MONOW)
                Section("WINGMAN DAMAGE (in order)")
                val dl = Wingman.dmgLevel(ctx)
                Text("Its shots chip hazards exactly like your own bolts. These levels make them hit harder, on hazards and bosses.", color = Color.White.copy(alpha = 0.65f), fontSize = 9.sp, fontFamily = MONOW, modifier = Modifier.padding(bottom = 4.dp))
                fun buyDmg(lv: Int) {
                    if (busy) return
                    if (!open) { good = false; msg = "Locked: reach zone ${Wingman.UNLOCK_ZONE} to buy (you are at zone $bestZone)"; return }
                    if (lv != dl + 1) { good = false; msg = "Buy the levels in order"; return }
                    if (lv <= 2) { busy = true; spend(Wingman.DMG_GEMS[lv - 1]) { ok -> busy = false; good = ok; if (ok) { Wingman.setDmgLevel(ctx, lv); msg = "Wingman damage level $lv" } else msg = "Not enough gems" } }
                    else { busy = true; onBuyDmg(Wingman.DMG_ID3) { ok -> busy = false; good = ok; if (ok) { Wingman.setDmgLevel(ctx, 3); msg = "Wingman damage level 3" } else msg = "Purchase cancelled" } }
                }
                UpRow("DAMAGE I", "x1.25 on hazards and bosses", "${Wingman.DMG_GEMS[0]} 💎", dl >= 1, open, busy) { buyDmg(1) }
                UpRow("DAMAGE II", "x1.5 on hazards and bosses", "${Wingman.DMG_GEMS[1]} 💎", dl >= 2, open, busy) { buyDmg(2) }
                UpRow("DAMAGE III", "x2 on hazards and bosses", Wingman.DMG_PRICE3, dl >= 3, open, busy) { buyDmg(3) }
                Section("WINGMAN ARMS (one-time, in order)")
                val ar = NrUpgrades.TRACKS[NrUpgrades.WARMS]
                val armBlurb = listOf("Cannon x1.25 · 15 s sortie", "Cannon x1.5 · SECOND GUNSHIP DRONE", "Cannon x1.8 · 18 s sortie", "Cannon x2.2 · 20 s sortie · fastest fire")
                for (s in 1..4) UpRow(ar.labels[s - 1], armBlurb[s - 1], ar.prices[s - 1], arms >= s, open, busy) { buy(NrUpgrades.WARMS, s) }
                Section("ORDNANCE (picked up in the field)")
                for (k in 0..2) Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(12.dp).clip(CircleShape).background(Color(Wingman.ORD_COLS[k]))); Spacer(Modifier.width(8.dp))
                    Text(Wingman.ORD_NAMES[k] + " · " + Wingman.ORD_BLURB[k], color = Color.White.copy(alpha = 0.85f), fontSize = 11.sp, fontFamily = MONOW)
                }
                Section("SKINS")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (i in Wingman.SKINS.indices) {
                        val sk = Wingman.SKINS[i]; val own = Wingman.ownsSkin(ctx, i); val sel = equipped == i
                        Column(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.06f)).border(if (sel) 2.dp else 1.dp, Color(sk.accent).copy(alpha = if (sel) 1f else 0.4f), RoundedCornerShape(12.dp)).clickable { buySkin(i) }.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Canvas(Modifier.fillMaxWidth().height(70.dp)) {
                                drawWingmanPilot(Offset(size.width / 2f, size.height * 0.55f), size.height * 0.9f, frames[(clock * 10f).toInt() % frames.size], custom, clock, sk, 0, 0, 0f, 1f, "", tm)
                            }
                            Text(sk.name, color = Color(sk.accent), fontSize = 8.sp, fontWeight = FontWeight.Black, fontFamily = MONOW, maxLines = 1)
                            Text(if (sel) "EQUIPPED" else if (own) "OWNED · TAP" else sk.price, color = Color.White.copy(alpha = 0.85f), fontSize = 8.sp, fontFamily = MONOW)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("Free players can still win everything. The wingman is a power and style choice, not a requirement.", color = Color.White.copy(alpha = 0.5f), fontSize = 9.sp, fontFamily = MONOW)
            }
        }
    }
}

@Composable private fun Section(t: String) { Text(t, color = AMB, fontSize = 12.sp, fontWeight = FontWeight.Black, fontFamily = MONOW, modifier = Modifier.padding(top = 14.dp, bottom = 6.dp)) }
@Composable private fun Bullet(t: String) { Text("• $t", color = Color.White.copy(alpha = 0.85f), fontSize = 11.sp, fontFamily = MONOW, modifier = Modifier.padding(vertical = 2.dp)) }

@Composable
private fun UpRow(title: String, blurb: String, price: String, owned: Boolean, open: Boolean, busy: Boolean, onBuy: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(shape).background(Color.White.copy(alpha = 0.06f)).border(1.dp, AMB.copy(alpha = if (owned) 0.9f else 0.3f), shape).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = if (owned) AMB else Color.White, fontSize = 12.sp, fontWeight = FontWeight.Black, fontFamily = MONOW)
            Text(blurb, color = Color.White.copy(alpha = 0.7f), fontSize = 9.sp, fontFamily = MONOW)
        }
        Text(if (owned) "OWNED ✓" else if (!open) "🔒 $price" else price, color = if (owned) Color(0xFF69F0AE) else Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Black, fontFamily = MONOW,
            modifier = Modifier.clip(RoundedCornerShape(10.dp)).then(if (owned) Modifier else Modifier.background(if (open) AMB else Color.White.copy(alpha = 0.25f))).clickable(enabled = !owned && !busy) { onBuy() }.padding(horizontal = 12.dp, vertical = 8.dp))
    }
}
