package com.neonrush.game.ui

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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.neonrush.game.AirSupport
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val SKY1 = Color(0xFF0A1330); private val SKY2 = Color(0xFF1B2F6B)
private val CYAN = Color(0xFF3DE8FF); private val GOLD = Color(0xFFFFC21A); private val GREEN = Color(0xFF69F0AE); private val AMBER = Color(0xFFFFB300)
private val MONO = FontFamily.Monospace

/** AIR SUPPORT ribbon (with a wings badge) shown on the pilot card and next to the name on the boards. level 1..3 = WINGMAN / STRIKE LEADER / AIR ACE. */
@Composable
fun AirRibbon(level: Int, modifier: Modifier = Modifier, compact: Boolean = false) {
    if (level <= 0) return
    val col = Color(AirSupport.levelColor(level)); val name = AirSupport.levelName(level)
    val inf = rememberInfiniteTransition(label = "ribbon")
    val sh by inf.animateFloat(-0.3f, 1.3f, infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Restart), label = "sh")
    val h = if (compact) 18.dp else 28.dp
    Box(modifier.height(h), contentAlignment = Alignment.CenterStart) {
        Canvas(Modifier.matchParentSize()) {
            val w = size.width; val hh = size.height; val n = hh * 0.3f
            val p = Path().apply { moveTo(0f, 0f); lineTo(w, 0f); lineTo(w - n, hh / 2f); lineTo(w, hh); lineTo(0f, hh); lineTo(n, hh / 2f); close() }
            drawPath(p, Brush.horizontalGradient(listOf(col.copy(alpha = 0.95f), Color(0xFF1B2745), col.copy(alpha = 0.95f))))
            drawPath(p, col, style = Stroke(1.5f))
            val sx = w * sh; drawLine(Color.White.copy(alpha = 0.25f), Offset(sx, 0f), Offset(sx - hh, hh), hh * 0.35f)
        }
        Row(Modifier.padding(start = (if (compact) 12 else 18).dp, end = (if (compact) 12 else 18).dp), verticalAlignment = Alignment.CenterVertically) {
            AirWings(level, if (compact) 14.dp else 22.dp)
            Spacer(Modifier.width(4.dp))
            Text(if (compact) name else "AIR SUPPORT · $name", color = Color.White, fontSize = if (compact) 8.sp else 11.sp, fontWeight = FontWeight.Black, fontFamily = MONO, maxLines = 1)
        }
    }
}

/** Little winged badge. */
@Composable
fun AirWings(level: Int, size: androidx.compose.ui.unit.Dp) {
    val col = Color(AirSupport.levelColor(level))
    Canvas(Modifier.size(width = size * 1.5f, height = size)) {
        val w = size.toPx() * 1.5f; val h = size.toPx(); val cx = w / 2f; val cy = h / 2f
        for (side in intArrayOf(-1, 1)) {
            val p = Path().apply { moveTo(cx + side * h * 0.2f, cy); lineTo(cx + side * w * 0.5f, cy - h * 0.38f); lineTo(cx + side * w * 0.42f, cy - h * 0.02f); lineTo(cx + side * w * 0.5f, cy + h * 0.12f); lineTo(cx + side * w * 0.3f, cy + h * 0.14f); lineTo(cx + side * h * 0.2f, cy + h * 0.3f); close() }
            drawPath(p, col)
        }
        drawCircle(Brush.radialGradient(listOf(Color.White, col), Offset(cx, cy), h * 0.35f), h * 0.3f, Offset(cx, cy))
        drawCircle(Color(0xFF1B2745), h * 0.3f, Offset(cx, cy), style = Stroke(1.5f))
        for (i in 0 until level) drawCircle(Color(0xFF1B2745), h * 0.045f, Offset(cx + (i - (level - 1) / 2f) * h * 0.13f, cy))
    }
}

/** Reads the ribbon level out of a badge-id list ("air1".."air3"), 0 when none. */
fun airLevelOf(badgeIds: List<String>): Int = badgeIds.firstOrNull { it.startsWith("air") && it.length == 4 }?.last()?.digitToIntOrNull() ?: 0

/** Home card: a small animated entry to the Air Support Bay. */
@Composable
fun AirSupportCard(isPro: Boolean, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val inf = rememberInfiniteTransition(label = "airCard")
    val clock by inf.animateFloat(0f, 600f, infiniteRepeatable(tween(600_000, easing = LinearEasing)), label = "clock")
    val shape = RoundedCornerShape(16.dp)
    val calls = AirSupport.calls(ctx); val free = AirSupport.freeAvailable(ctx, isPro)
    val total = AirSupport.totalSorties(ctx); val lv = AirSupport.level(total)
    val artHeli = rememberArt("air_heli")
    Box(modifier.fillMaxWidth().clip(shape).background(Brush.verticalGradient(listOf(SKY2, SKY1))).border(2.dp, CYAN.copy(alpha = 0.55f), shape).clickable { onOpen() }) {
        Canvas(Modifier.matchParentSize()) {
            val p = (clock / 7f) % 1f
            AirCraftArt.heli(this, size.width * (-0.15f + 1.3f * p), size.height * 0.42f, size.height * 0.62f, 1f, clock, 4f, artHeli, false)
            for (i in 0 until 14) drawCircle(Color.White.copy(alpha = 0.25f), 1.5f, Offset(size.width * ((i * 0.173f + clock * 0.01f) % 1f), size.height * ((i * 0.37f) % 1f)))
        }
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("AIR SUPPORT BAY", color = CYAN, fontSize = 13.sp, fontWeight = FontWeight.Black, fontFamily = MONO)
                Text(if (!isPro) "👑 Pro: helicopter + jet backup on Hard and Legendary" else if (free) "FREE CALL READY · $calls calls in stock" else "$calls calls in stock · free call used today", color = Color.White.copy(alpha = 0.85f), fontSize = 10.sp, fontFamily = MONO)
                if (lv > 0) AirRibbon(lv, Modifier.padding(top = 6.dp), compact = true)
            }
            Text("OPEN ▸", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Black, fontFamily = MONO, modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(Brush.horizontalGradient(listOf(CYAN, GREEN))).padding(horizontal = 12.dp, vertical = 8.dp))
        }
    }
}

/**
 * AIR SUPPORT BAY: aircraft flying across a living sky, your stock and free call, ribbon progress, aircraft choice (jet unlock), the rules and five call packs (consumable).
 * Free players see everything (to see what they miss) but cannot buy.
 */
@Composable
fun AirSupportBayDialog(isPro: Boolean, testing: Boolean, onClose: () -> Unit, onGoPro: () -> Unit, onBuyPack: (Int, (Boolean) -> Unit) -> Unit) {
    val ctx = LocalContext.current
    val dens = LocalDensity.current.density
    val inf = rememberInfiniteTransition(label = "airBay")
    val clock by inf.animateFloat(0f, 600f, infiniteRepeatable(tween(600_000, easing = LinearEasing)), label = "clock")
    val tau = 2f * PI.toFloat()
    val calls = AirSupport.calls(ctx); val free = AirSupport.freeAvailable(ctx, isPro)
    val total = AirSupport.totalSorties(ctx); val lv = AirSupport.level(total)
    val jetOk = AirSupport.jetUnlocked(ctx); val craft = AirSupport.aircraft(ctx)
    val artHeli = rememberArt("air_heli"); val artJet = rememberArt("air_jet")
    var msg by remember { mutableStateOf("") }; var msgGood by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var burst by remember { mutableStateOf(-1f) }; var burstText by remember { mutableStateOf("") }
    LaunchedEffect(burst >= 0f) {
        if (burst < 0f) return@LaunchedEffect
        var last = 0L
        while (burst in 0f..1.6f) { val n = withFrameNanos { it }; if (last != 0L) burst += ((n - last) / 1_000_000_000f).coerceAtMost(0.05f); last = n }
        burst = -1f
    }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)) {
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF0B1536), Color(0xFF0B0820), Color(0xFF06040F))))) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 28.dp, bottom = 40.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("AIR SUPPORT BAY", color = CYAN, fontSize = 22.sp, fontWeight = FontWeight.Black, fontFamily = MONO)
                        Text("Call in the cavalry · Hard 40% power · Legendary full power", color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp, fontFamily = MONO)
                    }
                    Box(Modifier.size(40.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.1f)).border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape).clickable { onClose() }, contentAlignment = Alignment.Center) { Text("✕", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Black) }
                }
                Spacer(Modifier.height(12.dp))
                // ---- living sky: both aircraft fly end to end ----
                val hs = RoundedCornerShape(18.dp)
                Box(Modifier.fillMaxWidth().height(170.dp).clip(hs).border(2.dp, CYAN.copy(alpha = 0.5f), hs)) {
                    Canvas(Modifier.matchParentSize()) {
                        drawRect(Brush.verticalGradient(listOf(SKY1, SKY2, Color(0xFF3A2A6A))))
                        for (i in 0 until 30) drawCircle(Color.White.copy(alpha = 0.3f + 0.3f * sin(clock * 2f + i)), (0.8f + (i % 3) * 0.6f) * dens, Offset(size.width * ((i * 0.137f) % 1f), size.height * 0.7f * ((i * 0.291f) % 1f)))
                        for (i in 0 until 4) { val cx = size.width * (((clock * (0.02f + i * 0.006f)) + i * 0.27f) % 1.3f - 0.15f); drawOval(Color.White.copy(alpha = 0.07f), Offset(cx, size.height * (0.25f + i * 0.17f)), Size(110f * dens, 18f * dens)) }
                        val p1 = (clock / 8f) % 1f
                        AirCraftArt.heli(this, size.width * (-0.15f + 1.3f * p1), size.height * (0.34f + 0.05f * sin(clock)), 78f * dens, 1f, clock, 5f, artHeli, false)
                        val p2 = (clock / 5.2f + 0.4f) % 1f
                        AirCraftArt.jet(this, size.width * (1.15f - 1.3f * p2), size.height * (0.7f + 0.06f * sin(clock * 1.3f)), 90f * dens, -1f, clock, -4f, artJet, false)
                    }
                    Text("HAWK SIX  ·  VIPER ONE", color = Color.White.copy(alpha = 0.85f), fontSize = 10.sp, fontWeight = FontWeight.Black, fontFamily = MONO, modifier = Modifier.align(Alignment.BottomStart).padding(10.dp))
                }
                if (!isPro) {
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Brush.horizontalGradient(listOf(Color(0xFF3A1A6E), Color(0xFF7C4DFF)))).clickable { onGoPro() }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("👑", fontSize = 22.sp); Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text("AIR SUPPORT IS A PRO FEATURE", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Black, fontFamily = MONO)
                            Text("Pro pilots get 1 free call every day, the jet, and the AIR SUPPORT ribbon on their card and name", color = Color.White.copy(alpha = 0.85f), fontSize = 10.sp, fontFamily = MONO)
                        }
                        Text("GO PRO ▸", color = GOLD, fontSize = 12.sp, fontWeight = FontWeight.Black, fontFamily = MONO)
                    }
                }
                // ---- stock + ribbon ----
                Spacer(Modifier.height(12.dp))
                val ss = RoundedCornerShape(16.dp)
                Column(Modifier.fillMaxWidth().clip(ss).background(Color(0xFF16103A)).border(1.5.dp, GOLD.copy(alpha = 0.5f), ss).padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("CALLS IN STOCK", color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp, fontFamily = MONO)
                            Text("$calls", color = GOLD, fontSize = 30.sp, fontWeight = FontWeight.Black, fontFamily = MONO)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("DAILY FREE CALL", color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp, fontFamily = MONO)
                            Text(if (!isPro) "PRO ONLY" else if (free) "✓ READY" else "USED · back tomorrow", color = if (free) GREEN else Color.White.copy(alpha = 0.6f), fontSize = 12.sp, fontWeight = FontWeight.Black, fontFamily = MONO)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text("SORTIES FLOWN: $total", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = MONO)
                    if (lv > 0) AirRibbon(lv, Modifier.padding(top = 6.dp)) else Text("Fly your first sortie to earn the WINGMAN ribbon", color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp, fontFamily = MONO)
                    val next = when (lv) { 0 -> 1; 1 -> 10; 2 -> 30; else -> 0 }
                    if (next > 0) {
                        Box(Modifier.fillMaxWidth().padding(top = 8.dp).height(7.dp).clip(RoundedCornerShape(4.dp)).background(Color.White.copy(alpha = 0.1f))) { Box(Modifier.fillMaxWidth((total.toFloat() / next).coerceIn(0f, 1f)).fillMaxHeight().background(Brush.horizontalGradient(listOf(CYAN, GOLD)))) }
                        Text("Next ribbon at $next sorties: ${AirSupport.levelName(lv + 1)}", color = Color.White.copy(alpha = 0.6f), fontSize = 9.sp, fontFamily = MONO, modifier = Modifier.padding(top = 3.dp))
                    }
                    Text("Your ribbon and badge show on your pilot card and next to your name on the leaderboards, Legendary board included.", color = Color.White.copy(alpha = 0.55f), fontSize = 9.sp, fontFamily = MONO, modifier = Modifier.padding(top = 6.dp))
                }
                // ---- aircraft ----
                Spacer(Modifier.height(16.dp))
                Text("YOUR AIRCRAFT", color = CYAN, fontSize = 14.sp, fontWeight = FontWeight.Black, fontFamily = MONO)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (id in listOf("heli", "jet")) {
                        val sel = craft == id; val locked = id == "jet" && !jetOk
                        val cs = RoundedCornerShape(14.dp)
                        Column(Modifier.weight(1f).clip(cs).background(Color(0xFF16103A)).border(if (sel) 2.dp else 1.dp, if (sel) GOLD else Color.White.copy(alpha = 0.2f), cs).clickable(enabled = !locked) { AirSupport.selectAircraft(ctx, id) }.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Canvas(Modifier.fillMaxWidth().height(56.dp)) {
                                val a = if (locked) 0.35f else 1f
                                if (id == "heli") AirCraftArt.heli(this, size.width / 2f, size.height / 2f, 70f * dens, 1f, clock, 4f, artHeli, false) else AirCraftArt.jet(this, size.width / 2f, size.height / 2f, 78f * dens, 1f, clock, 0f, artJet, false)
                                if (locked) drawRect(Color(0xCC16103A), Offset.Zero, size)
                            }
                            Text(if (id == "heli") "HAWK SIX" else "VIPER ONE", color = if (sel) GOLD else Color.White, fontSize = 12.sp, fontWeight = FontWeight.Black, fontFamily = MONO)
                            Text(if (id == "heli") "Gunship: hovers, strafes both ways" else if (locked) "🔒 Fly $total/${AirSupport.JET_UNLOCK_SORTIES} sorties or buy a 15+ pack" else "Jet: fast pass, afterburner, long lock", color = Color.White.copy(alpha = 0.65f), fontSize = 9.sp, fontFamily = MONO, textAlign = TextAlign.Center)
                            if (sel) Text("✓ SELECTED", color = GOLD, fontSize = 9.sp, fontWeight = FontWeight.Black, fontFamily = MONO)
                        }
                    }
                }
                // ---- rules ----
                Spacer(Modifier.height(16.dp))
                val rs = RoundedCornerShape(14.dp)
                Column(Modifier.fillMaxWidth().clip(rs).background(Color(0xFF110E2B)).border(1.dp, Color.White.copy(alpha = 0.15f), rs).padding(12.dp)) {
                    Text("HOW IT WORKS", color = GOLD, fontSize = 12.sp, fontWeight = FontWeight.Black, fontFamily = MONO)
                    for (r in listOf("Hard and Legendary only · Pro only", "The call button appears after the boss beats you (Legendary: once, Hard: twice) or when heavy attacks pile up", "Radio call, then about 3 seconds of gunship: the guns clear the lane and the brood, 2 missiles hit the boss", "You are covered (safe) from the call until the aircraft leaves", "Max 2 sorties per run, 20 seconds apart · each sortie uses 1 call", "Hard: 40% power · Legendary: full power", "Packs never expire")) {
                        Text("•  $r", color = Color.White.copy(alpha = 0.78f), fontSize = 10.sp, fontFamily = MONO, modifier = Modifier.padding(top = 3.dp))
                    }
                }
                // ---- packs ----
                Spacer(Modifier.height(18.dp))
                Text("CALL PACKS", color = GOLD, fontSize = 14.sp, fontWeight = FontWeight.Black, fontFamily = MONO)
                Text("Consumable · buy as many as you like · one call = one sortie", color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp, fontFamily = MONO)
                Spacer(Modifier.height(8.dp))
                AirSupport.PACKS.forEachIndexed { i, p ->
                    val best = i == AirSupport.PACKS.lastIndex
                    val accent = if (best) GOLD else if (i >= 2) Color(0xFFFF4DD2) else CYAN
                    val sh = RoundedCornerShape(16.dp)
                    val can = (isPro || testing) && !busy
                    Box(Modifier.fillMaxWidth().padding(bottom = 10.dp).clip(sh).background(Brush.horizontalGradient(listOf(Color(0xFF16103A), accent.copy(alpha = 0.2f)))).border(if (best) 2.5.dp else 1.5.dp, accent.copy(alpha = 0.8f), sh)) {
                        Canvas(Modifier.matchParentSize()) { val sx = size.width * (((clock * 0.6f + i * 0.2f) % 1.6f) - 0.3f); drawLine(Color.White.copy(alpha = 0.08f), Offset(sx, 0f), Offset(sx - 50f * dens, size.height), 26f * dens) }
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(if (p.calls >= 15) "✈" else "🚁", fontSize = 30.sp, modifier = Modifier.width(48.dp), textAlign = TextAlign.Center)
                            Column(Modifier.weight(1f)) {
                                Text(p.tag, color = accent, fontSize = 10.sp, fontWeight = FontWeight.Black, fontFamily = MONO)
                                Text("${p.calls} CALLS", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Black, fontFamily = MONO)
                                Text("${p.calls} sorties · ${p.calls * 2} missiles" + if (p.calls >= 15) " · unlocks the JET" else "", color = Color.White.copy(alpha = 0.65f), fontSize = 9.sp, fontFamily = MONO)
                            }
                            Text(if (can) p.price else if (!isPro) "👑 PRO" else "...", color = if (can) Color.Black else Color.White.copy(alpha = 0.8f), fontSize = 15.sp, fontWeight = FontWeight.Black, fontFamily = MONO, textAlign = TextAlign.Center,
                                modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(if (can) Brush.horizontalGradient(listOf(accent, Color(0xFFFFF3B0))) else Brush.horizontalGradient(listOf(Color(0xFF3A3A4A), Color(0xFF2A2A38))))
                                    .clickable(enabled = can || !isPro) {
                                        if (!isPro && !testing) { onGoPro(); return@clickable }
                                        busy = true
                                        onBuyPack(i) { ok -> busy = false; if (ok) { burstText = "+${p.calls} AIR SUPPORT CALLS"; burst = 0f; msgGood = true; msg = burstText } else { msgGood = false; msg = "Purchase cancelled" } }
                                    }.padding(horizontal = 16.dp, vertical = 12.dp))
                        }
                        if (best) Text("BEST VALUE", color = Color.Black, fontSize = 8.sp, fontWeight = FontWeight.Black, fontFamily = MONO, modifier = Modifier.align(Alignment.TopEnd).clip(RoundedCornerShape(bottomStart = 10.dp)).background(GOLD).padding(horizontal = 8.dp, vertical = 3.dp))
                    }
                }
                if (msg.isNotEmpty()) Text(msg, color = if (msgGood) GREEN else Color(0xFFFF8A80), fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = MONO, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
            }
            if (burst >= 0f) {
                val k = (burst / 1.6f).coerceIn(0f, 1f)
                Canvas(Modifier.fillMaxSize()) {
                    val c = Offset(size.width / 2f, size.height * 0.45f)
                    for (i in 0 until 36) { val a = i * (tau / 36f) + (i % 3) * 0.2f; val r = (40f + (140f + (i % 4) * 40f) * k) * dens; drawCircle((if (i % 2 == 0) GOLD else CYAN).copy(alpha = 1f - k), (4f * (1f - k) + 1f) * dens, Offset(c.x + cos(a) * r, c.y + sin(a) * r)) }
                    drawCircle(GOLD.copy(alpha = 0.7f * (1f - k)), (30f + 260f * k) * dens, c, style = Stroke((6f * (1f - k) + 1f) * dens))
                }
                Text(burstText, color = GOLD.copy(alpha = 1f - k * 0.6f), fontSize = 16.sp, fontWeight = FontWeight.Black, fontFamily = MONO, textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center).offset(y = (-(k * 40f)).dp).clip(RoundedCornerShape(14.dp)).background(Color(0xE6080620)).border(2.dp, GOLD, RoundedCornerShape(14.dp)).padding(horizontal = 18.dp, vertical = 12.dp))
            }
        }
    }
}
