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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neonrush.game.MilShield
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val MIL_GOLD = Color(0xFFFFC400)
private val MIL_GREEN = Color(0xFF69F0AE)
private val MIL_RED = Color(0xFFFF5252)
private val MIL_MONO = FontFamily.Monospace

private fun milColor(pct: Int): Color = when {
    pct > 60 -> MIL_GREEN
    pct > 30 -> MIL_GOLD
    else -> MIL_RED
}

/**
 * Armour-plated hexagon shield around the pilot. Colour goes green -> amber -> red as it takes damage,
 * cracks appear with every hit, plates flash white when hit, and it shudders. Call inside the pilot draw block.
 */
fun DrawScope.drawMilShield(x: Float, y: Float, h: Float, w: Float, t: Float) {
    val hp = MilShield.hp
    val hitAge = (MilShield.nowMs() - MilShield.hitMs).coerceAtLeast(0L)
    val onAge = (MilShield.nowMs() - MilShield.onMs).coerceAtLeast(0L)
    val ls = MilShield.inLastStand()
    if (hp <= 0 && !ls) return
    val pct = MilShield.healthPct()
    val col = if (ls) MIL_RED else milColor(pct)
    val center = Offset(x + w * 0.02f, y)
    val r = h * 0.62f
    val pop = if (onAge < 420L) 0.6f + 0.4f * (onAge / 420f) else 1f
    val flash = if (hitAge < 240L) 1f - hitAge / 240f else 0f
    val shake = if (hitAge < 240L) sin(hitAge * 0.12f) * 3f * flash else 0f
    val c = Offset(center.x + shake, center.y)
    val rr = r * pop
    // soft glow
    drawCircle(Brush.radialGradient(listOf(Color.Transparent, col.copy(alpha = 0.10f), col.copy(alpha = 0.30f)), center = c, radius = rr * 1.08f), radius = rr * 1.08f, center = c)
    // hexagon plates: one segment per hit left (lost plates go dark)
    val a0 = t * 0.35f
    for (i in 0 until 6) {
        val a1 = a0 + i * (PI.toFloat() / 3f)
        val a2 = a1 + PI.toFloat() / 3f
        val alive = ls || i < (hp * 6 + MilShield.MAX_HP - 1) / MilShield.MAX_HP
        val p = Path().apply {
            moveTo(c.x + cos(a1) * rr, c.y + sin(a1) * rr)
            lineTo(c.x + cos(a2) * rr, c.y + sin(a2) * rr)
        }
        drawPath(p, color = if (alive) col.copy(alpha = 0.55f + 0.45f * flash) else Color.White.copy(alpha = 0.08f), style = Stroke(width = if (alive) 4.5f else 1.5f))
        if (alive) drawPath(p, color = Color.White.copy(alpha = 0.25f + 0.6f * flash), style = Stroke(width = 1.4f))
    }
    // rivets
    for (i in 0 until 6) {
        val a = a0 + i * (PI.toFloat() / 3f)
        drawCircle(Color.White.copy(alpha = 0.7f), radius = 2.2f, center = Offset(c.x + cos(a) * rr, c.y + sin(a) * rr))
    }
    // cracks grow with damage
    val cracks = if (ls) MilShield.MAX_HP - 1 else MilShield.MAX_HP - hp
    for (k in 0 until cracks) {
        val a = k * 2.399f + 0.6f
        val p = Path().apply {
            moveTo(c.x + cos(a) * rr * 0.98f, c.y + sin(a) * rr * 0.98f)
            lineTo(c.x + cos(a + 0.12f) * rr * 0.80f, c.y + sin(a + 0.12f) * rr * 0.80f)
            lineTo(c.x + cos(a - 0.05f) * rr * 0.66f, c.y + sin(a - 0.05f) * rr * 0.66f)
        }
        drawPath(p, color = Color.White.copy(alpha = 0.65f), style = Stroke(width = 1.6f))
    }
    // last hit warning pulse
    if (hp <= 1) drawCircle(MIL_RED.copy(alpha = 0.25f + 0.25f * sin(t * 10f)), radius = rr, center = c, style = Stroke(width = 3f))
    // last stand: a ring that drains over the 4 seconds it has left
    if (ls) drawArc(MIL_RED, -90f, 360f * (MilShield.lastStandLeftMs() / MilShield.LAST_STAND_MS.toFloat()).coerceIn(0f, 1f), false, Offset(c.x - rr * 1.08f, c.y - rr * 1.08f), androidx.compose.ui.geometry.Size(rr * 2.16f, rr * 2.16f), style = Stroke(width = 5f))
}

/**
 * In-run MIL shield display: while one is on, a 10-segment damage bar with the percentage;
 * otherwise, if you hold one in stock, a MIL button that switches it on.
 */
@Composable
fun MilShieldOverlay(active: Boolean, modifier: Modifier = Modifier) {
    if (!active) return
    val ctx = LocalContext.current
    var clock by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(100); clock++ } }
    @Suppress("UNUSED_VARIABLE") val c = clock
    val hp = MilShield.hp
    val stock = MilShield.stock(ctx)
    val ls = MilShield.inLastStand()
    if (hp <= 0 && !ls && stock <= 0) return
    Box(modifier.fillMaxSize()) {
        Column(Modifier.align(Alignment.TopStart).padding(top = 96.dp, start = 8.dp)) {
            if (ls) {
                val left = MilShield.lastStandLeftMs()
                Row(Modifier.clip(RoundedCornerShape(10.dp)).background(Color(0xCC1A0508)).border(1.5.dp, MIL_RED.copy(alpha = 0.6f + 0.4f * kotlin.math.abs(sin(clock * 0.5f))), RoundedCornerShape(10.dp)).padding(horizontal = 8.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("MIL LAST STAND", color = MIL_RED, fontSize = 10.sp, fontWeight = FontWeight.Black, fontFamily = MIL_MONO)
                    Spacer(Modifier.width(6.dp))
                    Box(Modifier.size(width = 60.dp, height = 8.dp).clip(RoundedCornerShape(3.dp)).background(Color.White.copy(alpha = 0.12f))) {
                        Box(Modifier.fillMaxWidth((left / MilShield.LAST_STAND_MS.toFloat()).coerceIn(0f, 1f)).fillMaxHeight().background(MIL_RED))
                    }
                    Spacer(Modifier.width(6.dp))
                    Text("${left / 1000 + 1}s", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Black, fontFamily = MIL_MONO)
                }
            } else if (hp > 0) {
                val pct = MilShield.healthPct()
                val col = milColor(pct)
                Row(Modifier.clip(RoundedCornerShape(10.dp)).background(Color(0xCC0B1020)).border(1.5.dp, col, RoundedCornerShape(10.dp)).padding(horizontal = 8.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("MIL", color = col, fontSize = 10.sp, fontWeight = FontWeight.Black, fontFamily = MIL_MONO)
                    Spacer(Modifier.width(6.dp))
                    for (i in 0 until MilShield.MAX_HP) {
                        Box(Modifier.padding(horizontal = 1.dp).size(width = 8.dp, height = 10.dp).clip(RoundedCornerShape(2.dp)).background(if (i < hp) col else Color.White.copy(alpha = 0.12f)))
                    }
                    Spacer(Modifier.width(6.dp))
                    Text("$pct%", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Black, fontFamily = MIL_MONO)
                }
            } else {
                val glow = 0.65f + 0.35f * kotlin.math.abs(sin(clock * 0.3f))
                Row(Modifier.clip(RoundedCornerShape(12.dp)).background(Color(0xDD1B1400)).border(2.dp, MIL_GOLD.copy(alpha = glow), RoundedCornerShape(12.dp))
                    .clickable { MilShield.activateFromStock(ctx) }.padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("🛡 MIL ×$stock", color = MIL_GOLD, fontSize = 12.sp, fontWeight = FontWeight.Black, fontFamily = MIL_MONO)
                    val al = MilShield.autoLeft(ctx)
                    Text(if (al > 0) "  AUTO ×$al" else "  TAP", color = Color.White.copy(alpha = 0.75f), fontSize = 9.sp, fontFamily = MIL_MONO)
                }
            }
        }
    }
}

/** Home card: stock + buy 5 for 350 gems + the AUTO 6 switch / unlock. [spend] = viewModel::spendStormGems, [onBuyAuto] = (productId, done). */
@Composable
fun MilShieldCard(gems: Int, spend: (Int, (Boolean) -> Unit) -> Unit, onBuyAuto: (String, (Boolean) -> Unit) -> Unit = { _, cb -> cb(false) }, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    @Suppress("UNUSED_VARIABLE") val v = MilShield.version
    val stock = MilShield.stock(ctx)
    val can = gems >= MilShield.PACK_GEMS
    val unlocked = MilShield.auto6Unlocked(ctx)
    val on = MilShield.auto6On(ctx)
    val shape = RoundedCornerShape(16.dp)
    Column(modifier.fillMaxWidth().clip(shape).background(Brush.horizontalGradient(listOf(Color(0xFF2A2100), Color(0xFF0B1536)))).border(2.dp, MIL_GOLD.copy(alpha = if (can) 0.8f else 0.35f), shape).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("MILITARY SHIELD", color = MIL_GOLD, fontSize = 13.sp, fontWeight = FontWeight.Black, fontFamily = MIL_MONO)
                Text("Takes ${MilShield.MAX_HP} hits + 4 s last stand · in stock: $stock", color = Color.White.copy(alpha = 0.85f), fontSize = 10.sp, fontFamily = MIL_MONO)
            }
            Box(Modifier.clip(RoundedCornerShape(10.dp)).background(if (can) MIL_GOLD else Color.White.copy(alpha = 0.12f))
                .clickable(enabled = can) { spend(MilShield.PACK_GEMS) { ok -> if (ok) MilShield.addStock(ctx, MilShield.PACK) } }.padding(horizontal = 10.dp, vertical = 8.dp)) {
                Text("BUY ${MilShield.PACK} · ${MilShield.PACK_GEMS} 💎", color = if (can) Color.Black else Color.White.copy(alpha = 0.5f), fontSize = 11.sp, fontWeight = FontWeight.Black, fontFamily = MIL_MONO)
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if (on) "AUTO 6 · ON" else "AUTO ${MilShield.AUTO_BASE}", color = if (on) Color(0xFF69F0AE) else Color.White, fontSize = 11.sp, fontWeight = FontWeight.Black, fontFamily = MIL_MONO)
                Text(if (on) "Your next 6 shields each run switch on by themselves when you are hit."
                     else "The first ${MilShield.AUTO_BASE} shields each run switch on by themselves when you are hit. After that, tap MIL.", color = Color.White.copy(alpha = 0.7f), fontSize = 9.sp, fontFamily = MIL_MONO)
            }
            if (unlocked) {
                Text(if (on) "SWITCH TO 3" else "SWITCH TO 6", color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Black, fontFamily = MIL_MONO,
                    modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(Color(0xFF69F0AE)).clickable { MilShield.setAuto6On(ctx, !on) }.padding(horizontal = 10.dp, vertical = 8.dp))
            } else {
                Text("AUTO 6 · ${MilShield.AUTO6_PRICE}", color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Black, fontFamily = MIL_MONO,
                    modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(MIL_GOLD).clickable { onBuyAuto(MilShield.AUTO6_ID) { ok -> if (ok) MilShield.applyAuto6(ctx) } }.padding(horizontal = 10.dp, vertical = 8.dp))
            }
        }
        if (!unlocked) Text("Included free with PRO.", color = Color.White.copy(alpha = 0.55f), fontSize = 9.sp, fontFamily = MIL_MONO, modifier = Modifier.padding(top = 4.dp))
    }
}
