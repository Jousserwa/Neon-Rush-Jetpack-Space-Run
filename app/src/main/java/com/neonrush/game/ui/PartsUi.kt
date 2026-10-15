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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import com.neonrush.game.DropSchedule
import com.neonrush.game.ServerClock
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neonrush.game.NeonRushViewModel
import com.neonrush.game.PartCatalog
import com.neonrush.game.PartSlot
import com.neonrush.game.PilotPart
import com.neonrush.game.Skins
import com.neonrush.game.db.GameProfile
import com.neonrush.game.ui.theme.CyberBackground
import com.neonrush.game.ui.theme.CyberOnSurface
import com.neonrush.game.ui.theme.CyberPrimary
import com.neonrush.game.ui.theme.CyberSecondary
import com.neonrush.game.ui.theme.CyberSurface

/** One pilot (suit + loadout) drawn live. Reads the shared clock inside the draw phase so it never recomposes. */
@Composable
private fun PilotStage(
    frames: List<ImageBitmap>, suitId: String, loadout: Map<String, String>, clock: State<Float>,
    modifier: Modifier, running: Boolean, tint: Color
) {
    Canvas(modifier.background(Brush.verticalGradient(listOf(Color(0xFF04071A), tint.copy(alpha = 0.25f))))) {
        val tick = (clock.value * 20f).toInt()
        val fi = if (running) (tick / 3) % 6 else 0
        val bmp = frames[fi]
        val h = size.height * 0.72f
        val w = h * bmp.width.toFloat() / bmp.height.toFloat()
        drawPilotLook(suitId, bmp, fi, size.width * 0.52f, size.height * 0.58f, w, h, tick, loadout)
    }
}

/**
 * Pilots tab > PARTS. Parts are bought with gems and worn on ANY suit. Tap a card to try the part on the big preview;
 * a part replaces the suit's own piece in that slot.
 */
@Composable
fun PartsPanel(viewModel: NeonRushViewModel, profile: GameProfile) {
    val st by viewModel.parts.state.collectAsState()
    val frames = rememberPilotFrames()
    val clock = rememberInfiniteTransition(label = "partsClock").animateFloat(
        0f, 600f, infiniteRepeatable(tween(600_000, easing = LinearEasing), RepeatMode.Restart), label = "pc"
    )
    var slotFilter by remember { mutableStateOf<PartSlot?>(null) }
    var tryId by remember { mutableStateOf<String?>(null) }
    var lockInfo by remember { mutableStateOf<LockInfo?>(null) }
    lockInfo?.let { LockInfoDialog(it) { lockInfo = null } }
    val nowMs = remember { ServerClock.bestNowMs() }
    val visible = PartCatalog.SHOP.filter { DropSchedule.partReleased(it.id, nowMs) }
    val nextDrop = DropSchedule.nextPartDropMs(nowMs)
    val suitId = profile.activePilotSkinId
    val suitName = Skins.ALL.firstOrNull { it.id == suitId }?.name ?: "Standard Issue"
    val tryPart = tryId?.let { PartCatalog.byId(it) }
    val loadout = if (tryPart != null) st.equipped + (tryPart.slot.name to tryPart.id) else st.equipped
    val builtIn = SuitLooks.of(suitId).parts.mapNotNull { PartCatalog.byId(it)?.name }

    Text("🧩 PILOT PARTS", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = CyberPrimary,
        fontFamily = FontFamily.Monospace, modifier = Modifier.padding(bottom = 4.dp))
    Text("Gear that fits any pilot and any suit. Same pilot, your style. A part replaces the suit's own piece in its slot.",
        color = CyberOnSurface.copy(alpha = 0.7f), fontSize = 12.sp, modifier = Modifier.padding(bottom = 10.dp))

    // ---- big preview
    Column(Modifier.fillMaxWidth().padding(bottom = 10.dp).clip(RoundedCornerShape(12.dp)).background(CyberSurface)
        .border(1.5.dp, CyberPrimary.copy(alpha = 0.5f), RoundedCornerShape(12.dp))) {
        PilotStage(frames, suitId, loadout, clock, Modifier.fillMaxWidth().height(190.dp), true, CyberPrimary)
        Column(Modifier.padding(12.dp)) {
            Text(if (tryPart != null) "TRYING ON: ${tryPart.name}" else "YOUR PILOT: $suitName",
                color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
            val worn = PartSlot.values().mapNotNull { s -> st.equipped[s.name]?.let { PartCatalog.byId(it)?.name } }
            Text(
                (if (worn.isEmpty()) "No extra parts worn." else "Wearing: " + worn.joinToString(", ")) +
                    (if (builtIn.isNotEmpty()) "  Suit includes: " + builtIn.joinToString(", ") + "." else ""),
                color = CyberOnSurface.copy(alpha = 0.7f), fontSize = 11.sp, modifier = Modifier.padding(top = 3.dp)
            )
            Text("💎 ${profile.gems}", color = Color(0xFF69F0AE), fontWeight = FontWeight.Bold, fontSize = 12.sp,
                fontFamily = FontFamily.Monospace, modifier = Modifier.padding(top = 4.dp))
        }
    }

    // ---- slot chips
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        val chips: List<Pair<String, PartSlot?>> = listOf<Pair<String, PartSlot?>>("ALL" to null) + PartSlot.values().map { "${it.emoji} ${it.label.uppercase()}" to it }
        chips.forEach { (label, slot) ->
            val sel = slotFilter == slot
            Text(
                label, color = if (sel) CyberBackground else CyberPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(end = 6.dp).clip(RoundedCornerShape(16.dp))
                    .background(if (sel) CyberPrimary else CyberSurface)
                    .border(1.dp, CyberPrimary.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                    .clickable { slotFilter = slot }.padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    }

    val ownedCount = visible.count { it.id in st.owned }
    Text("$ownedCount/${visible.size} parts collected" + (nextDrop?.let { "  ·  2 new parts drop ${DropSchedule.dateLabel(it)}" } ?: ""),
        color = CyberOnSurface.copy(alpha = 0.6f), fontSize = 11.sp, modifier = Modifier.padding(bottom = 6.dp))
    Text("Parts arrive 2 a month, and each needs a zone milestone. Gems and money follow the same calendar.",
        color = Color(0xFFFFD23F).copy(alpha = 0.8f), fontSize = 10.sp, modifier = Modifier.padding(bottom = 6.dp))

    visible.filter { slotFilter == null || it.slot == slotFilter }
        .sortedWith(compareBy<PilotPart>({ it.slot.ordinal }, { it.rarity.ordinal }, { it.gems }))
        .forEach { part -> PartCard(viewModel, profile, part, st.owned, st.equipped, frames, suitId, clock, tryId == part.id, { lockInfo = it }) { tryId = if (tryId == part.id) null else part.id } }
}

@Composable
private fun PartCard(
    viewModel: NeonRushViewModel, profile: GameProfile, part: PilotPart,
    owned: Set<String>, equipped: Map<String, String>, frames: List<ImageBitmap>, suitId: String,
    clock: State<Float>, trying: Boolean, onLock: (LockInfo) -> Unit, onTry: () -> Unit
) {
    val nowMs = remember { ServerClock.bestNowMs() }
    val status = DropSchedule.partStatus(part.id, profile.bestZoneReached, nowMs)
    val ctx = LocalContext.current
    val activity = remember(ctx) { var c: Context? = ctx; var a: Activity? = null; while (c is ContextWrapper) { if (c is Activity) { a = c; break }; c = c.baseContext }; a }
    val rc = Color(part.rarity.argb)
    val have = part.id in owned
    val worn = equipped[part.slot.name] == part.id
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(shape).background(CyberSurface)
            .border(if (worn || trying) 2.5.dp else 1.5.dp, rc.copy(alpha = if (worn || trying) 1f else 0.55f), shape)
            .clickable { onTry() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        PilotStage(frames, suitId, equipped + (part.slot.name to part.id), clock, Modifier.size(width = 104.dp, height = 112.dp), false, rc)
        Column(Modifier.weight(1f).padding(horizontal = 10.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.background(rc, RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 1.dp)) {
                    Text(part.rarity.label, color = Color(0xFF05060F), fontSize = 9.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
                }
                Text("  ${part.slot.emoji}", fontSize = 12.sp)
            }
            Text(part.name, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp, modifier = Modifier.padding(top = 3.dp))
            Text(part.blurb, fontSize = 11.sp, color = CyberPrimary.copy(alpha = 0.7f), modifier = Modifier.padding(top = 2.dp, bottom = 6.dp))
            when {
                worn -> Button(
                    onClick = { viewModel.parts.unequip(part.slot) },
                    colors = ButtonDefaults.buttonColors(containerColor = CyberPrimary), shape = RoundedCornerShape(4.dp)
                ) { Text("WORN · TAKE OFF", color = CyberBackground, fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                have -> Button(
                    onClick = { viewModel.parts.equip(part.id) },
                    colors = ButtonDefaults.buttonColors(containerColor = CyberSurface.copy(alpha = 0.8f)), shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.border(1.dp, CyberPrimary, RoundedCornerShape(4.dp))
                ) { Text("EQUIP", color = CyberPrimary, fontFamily = FontFamily.Monospace, fontSize = 11.sp) }
                status != DropSchedule.PartStatus.OPEN -> Button(
                    onClick = { onLock(partLockInfo(part, profile.bestZoneReached, nowMs, rc)) },
                    colors = ButtonDefaults.buttonColors(containerColor = CyberSurface.copy(alpha = 0.8f)), shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.border(1.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(4.dp))
                ) { Text("🔒 REACH ZONE ${DropSchedule.partZone(part.id)}", color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 11.sp) }
                part.usd != null -> Button(
                    onClick = { activity?.let { viewModel.parts.buyUsd(it, part.id) } },
                    colors = ButtonDefaults.buttonColors(containerColor = CyberSecondary), shape = RoundedCornerShape(4.dp)
                ) { Text("BUY ${part.usd}", color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                else -> Button(
                    onClick = { viewModel.parts.buy(part.id) },
                    colors = ButtonDefaults.buttonColors(containerColor = if (profile.gems >= part.gems) CyberSecondary else CyberSurface.copy(alpha = 0.8f)),
                    shape = RoundedCornerShape(4.dp)
                ) { Text("BUY 💎 ${part.gems}", color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 11.sp) }
            }
        }
    }
}

private fun partLockInfo(part: PilotPart, best: Int, nowMs: Long, accent: Color): LockInfo {
    val need = DropSchedule.partZone(part.id)
    val price = part.usd ?: "💎 ${part.gems}"
    return LockInfo("🧩", part.name, "Reach Zone $need to unlock this part.",
        listOf("Fly any run until you reach Zone $need. Your best so far is Zone $best.",
            "Then it opens for purchase ($price). Money cannot skip this step.",
            "New parts arrive 2 at a time, every month."),
        (best.toFloat() / need).coerceIn(0f, 1f), "Best zone $best / $need", accent)
}
