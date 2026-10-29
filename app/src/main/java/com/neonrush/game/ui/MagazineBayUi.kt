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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.neonrush.game.BroodBomb
import com.neonrush.game.MagazineBay
import com.neonrush.game.Shooting
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private val GOLD = Color(0xFFFFC21A)
private val CYAN = Color(0xFF3DE8FF)
private val PINK = Color(0xFFFF4DD2)
private val GREEN = Color(0xFF69F0AE)
private val INK = Color(0xFF0B0820)
private val MONO = FontFamily.Monospace

/**
 * MAGAZINE BAY (Stage 144). One armor = 20 shots, one magazine = 5 armors = 100 shots. Real money buys STORAGE (how many magazines the gun carries);
 * the tier you buy replaces the base. Tiers are bought in order, one per day. Gems buy the BOMB RACK (more Brood Bomb slots).
 * Animated gun gauge, five tier cards with every lock reason, month progress bars, purchase celebration.
 */
@Composable
fun MagazineBayDialog(
    isPro: Boolean, proMonths: Int, testing: Boolean,
    onClose: () -> Unit, onGoPro: () -> Unit,
    onBuyTier: (Int, (Boolean) -> Unit) -> Unit, onBuyBombRack: ((String?) -> Unit) -> Unit
) {
    val ctx = LocalContext.current
    MagazineBay.proNow = isPro
    val inf = rememberInfiniteTransition(label = "bay")
    val t by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(3200, easing = LinearEasing), RepeatMode.Restart), label = "t")
    val tau = 2f * PI.toFloat()
    val own = MagazineBay.tier(ctx)
    val usable = MagazineBay.usableTier(ctx, isPro)
    val cap = MagazineBay.capacity(ctx, isPro)
    val mags = MagazineBay.magazines(ctx, isPro)
    val ammo = Shooting.ammo(ctx)
    val rack = BroodBomb.rack(ctx)
    val artCyan = rememberArt("armor_mag_cyan"); val artGold = rememberArt("armor_mag_gold"); val artBomb = rememberArt("bomb_pink")
    var msg by remember { mutableStateOf("") }
    var msgGood by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var burst by remember { mutableStateOf(-1f) }
    var burstText by remember { mutableStateOf("") }

    LaunchedEffect(burst >= 0f) {
        if (burst < 0f) return@LaunchedEffect
        var last = 0L
        while (burst in 0f..1.6f) { val n = withFrameNanos { it }; if (last != 0L) burst += ((n - last) / 1_000_000_000f).coerceAtMost(0.05f); last = n }
        burst = -1f
    }
    fun celebrate(text: String) { burstText = text; burst = 0f; msgGood = true; msg = text }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)) {
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF120B2E), INK, Color(0xFF06040F))))) {
            Canvas(Modifier.fillMaxSize()) {
                for (i in 0 until 26) {
                    val sx = (i * 0.137f) % 1f; val sp = 0.4f + (i % 5) * 0.12f
                    val y = size.height * (1f - ((t * sp + i * 0.173f) % 1f)); val x = size.width * sx + sin((t + i) * tau) * 12.dp.toPx()
                    drawCircle((if (i % 3 == 0) GOLD else CYAN).copy(alpha = 0.25f), (1.5f + (i % 3)).dp.toPx(), Offset(x, y))
                }
            }
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 28.dp, bottom = 40.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("MAGAZINE BAY", color = GOLD, fontSize = 22.sp, fontWeight = FontWeight.Black, fontFamily = MONO)
                        Text("1 armor = 20 shots · 1 magazine = 5 armors = 100 shots", color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp, fontFamily = MONO)
                    }
                    Box(Modifier.size(40.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.1f)).border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape).clickable { onClose() }, contentAlignment = Alignment.Center) {
                        Text("✕", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Black)
                    }
                }
                Spacer(Modifier.height(12.dp))

                // ---------------- the gun gauge: magazines, each five armor cells ----------------
                val shape = RoundedCornerShape(18.dp)
                Column(Modifier.fillMaxWidth().clip(shape).background(Color(0xFF16103A)).border(2.dp, GOLD.copy(alpha = 0.55f), shape).padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("YOUR GUN", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Black, fontFamily = MONO, modifier = Modifier.weight(1f))
                        Text("$ammo / $cap shots", color = GOLD, fontSize = 14.sp, fontWeight = FontWeight.Black, fontFamily = MONO)
                    }
                    Text("$mags ${if (mags == 1) "magazine" else "magazines"} · ${cap / MagazineBay.PER_ARMOR} armors" + if (own > usable) " · tier $own sleeps until Pro returns" else "",
                        color = CYAN, fontSize = 10.sp, fontFamily = MONO, modifier = Modifier.padding(top = 2.dp))
                    Spacer(Modifier.height(8.dp))
                    val perRow = 12
                    val rows = (mags + perRow - 1) / perRow
                    val active = if (ammo <= 0) -1 else ((ammo - 1) / MagazineBay.MAG_SIZE).coerceAtMost(mags - 1)
                    Canvas(Modifier.fillMaxWidth().height((rows * 64).dp)) {
                        val gap = 4.dp.toPx(); val cols = minOf(mags, perRow).coerceAtLeast(1)
                        val mw = ((size.width - gap * (perRow - 1)) / perRow).coerceAtMost(46.dp.toPx())
                        val mh = 56.dp.toPx(); val cellGap = 2.dp.toPx(); val ch = (mh - cellGap * 4) / 5f
                        val total = cols * mw + (cols - 1) * gap; val x0 = (size.width - total) / 2f
                        for (m in 0 until mags) {
                            val r = m / perRow; val c = m % perRow
                            val rowCols = minOf(perRow, mags - r * perRow)
                            val rowTotal = rowCols * mw + (rowCols - 1) * gap
                            val x = (size.width - rowTotal) / 2f + c * (mw + gap); val y = r * 64.dp.toPx()
                            val isActive = m == active
                            drawRoundRect((if (isActive) GOLD else Color.White).copy(alpha = if (isActive) 0.5f + 0.4f * sin(t * tau * 2f) else 0.18f), Offset(x - 2f, y - 2f), Size(mw + 4f, mh + 4f), CornerRadius(6.dp.toPx()), style = Stroke(if (isActive) 2.dp.toPx() else 1.dp.toPx()))
                            for (cell in 0 until 5) {
                                val shots = ammo - m * MagazineBay.MAG_SIZE - cell * MagazineBay.PER_ARMOR
                                val k = (shots / MagazineBay.PER_ARMOR.toFloat()).coerceIn(0f, 1f)
                                val cy = y + mh - (cell + 1) * ch - cell * cellGap
                                drawRoundRect(Color.White.copy(alpha = 0.07f), Offset(x, cy), Size(mw, ch), CornerRadius(3.dp.toPx()))
                                if (k > 0f) {
                                    val wave = 0.78f + 0.22f * sin(t * tau * 2f - m * 0.6f - cell * 0.9f)
                                    drawRoundRect(Brush.horizontalGradient(listOf(GOLD, Color(0xFFFFF3B0), GOLD), x, x + mw), Offset(x, cy), Size(mw * k, ch), CornerRadius(3.dp.toPx()), alpha = wave)
                                }
                            }
                        }
                    }
                    Text("The gun fires one magazine at a time and swaps to the next by itself. Armor picked up while storage is full turns into score.",
                        color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp, fontFamily = MONO, modifier = Modifier.padding(top = 8.dp))
                }

                if (!isPro) {
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Brush.horizontalGradient(listOf(Color(0xFF3A1A6E), Color(0xFF7C4DFF)))).clickable { onGoPro() }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("👑", fontSize = 22.sp); Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text("FREE GUN HOLDS ${MagazineBay.FREE_BASE} SHOTS", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Black, fontFamily = MONO)
                            Text("Free players can buy tiers 1 and 2. Pro: a full ${MagazineBay.PRO_BASE}-shot magazine, tiers 3 to 5 and Legendary", color = Color.White.copy(alpha = 0.85f), fontSize = 10.sp, fontFamily = MONO)
                        }
                        Text("GO PRO ▸", color = GOLD, fontSize = 12.sp, fontWeight = FontWeight.Black, fontFamily = MONO)
                    }
                }

                // ---------------- storage tiers ----------------
                Spacer(Modifier.height(18.dp))
                Text("GUN STORAGE", color = CYAN, fontSize = 14.sp, fontWeight = FontWeight.Black, fontFamily = MONO)
                Text("One-time purchases · the tier you buy replaces your base · in order · one tier per day", color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp, fontFamily = MONO)
                Spacer(Modifier.height(8.dp))
                MagazineBay.TIERS.forEachIndexed { idx, tr ->
                    val n = idx + 1
                    val owned = n <= own
                    val sleeping = owned && n > usable
                    val isNext = n == own + 1
                    val reason = MagazineBay.blockReason(ctx, n, isPro, proMonths, testing)
                    val canBuy = reason == null && !busy
                    val best = n == MagazineBay.MAX_TIER
                    val accent = when { best -> GOLD; n >= 3 -> PINK; else -> CYAN }
                    val sh = RoundedCornerShape(16.dp)
                    val pulse = 0.55f + 0.45f * sin(t * tau * 2f)
                    Box(Modifier.fillMaxWidth().padding(bottom = 10.dp).clip(sh)
                        .background(Brush.horizontalGradient(listOf(Color(0xFF16103A), accent.copy(alpha = if (owned || isNext) 0.20f else 0.05f))))
                        .border(if (best) 2.5.dp else 1.5.dp, when { owned && !sleeping -> GOLD; isNext -> accent.copy(alpha = pulse); else -> accent.copy(alpha = 0.22f) }, sh)) {
                        if (owned || isNext) Canvas(Modifier.matchParentSize()) {
                            val sx = size.width * (((t * 1.2f + n * 0.2f) % 1.6f) - 0.3f)
                            drawLine(Color.White.copy(alpha = 0.09f), Offset(sx, 0f), Offset(sx - 50.dp.toPx(), size.height), 26.dp.toPx())
                        }
                        Column(Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.width(92.dp).height(60.dp)) {
                                    val show = tr.mags.coerceAtMost(5)
                                    for (k in 0 until show) {
                                        val a = if (n >= 4) artGold else artCyan
                                        if (a != null) androidx.compose.foundation.Image(androidx.compose.ui.graphics.painter.BitmapPainter(a), null,
                                            Modifier.align(Alignment.CenterStart).offset(x = (k * 11).dp, y = (sin(t * tau + k + n) * 2.5f).dp).height(50.dp).width(42.dp), alpha = if (owned || isNext) 1f else 0.35f)
                                    }
                                }
                                Column(Modifier.weight(1f).padding(start = 8.dp)) {
                                    Text("TIER $n · ${tr.label}", color = accent, fontSize = 10.sp, fontWeight = FontWeight.Black, fontFamily = MONO)
                                    Text("${tr.mags} MAGAZINES", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Black, fontFamily = MONO)
                                    Text("${tr.mags * MagazineBay.MAG_SIZE} shots · ${tr.mags * 5} armors", color = Color.White.copy(alpha = 0.65f), fontSize = 10.sp, fontFamily = MONO)
                                    val more = ((1f / MagazineBay.dropMulAt(n) - 1f) * 100f).roundToInt()
                                    if (more > 0) Text("armor drops $more% more often", color = GREEN, fontSize = 9.sp, fontFamily = MONO)
                                }
                                if (owned) Text(if (sleeping) "💤\nSLEEPS" else "✓\nOWNED", color = if (sleeping) Color.White.copy(alpha = 0.6f) else GOLD, fontSize = 11.sp, fontWeight = FontWeight.Black, fontFamily = MONO, textAlign = TextAlign.Center)
                                else Text(if (canBuy) tr.price else if (isNext) "🔒" else "🔒", color = if (canBuy) Color.Black else Color.White.copy(alpha = 0.8f), fontSize = if (canBuy) 15.sp else 14.sp, fontWeight = FontWeight.Black, fontFamily = MONO, textAlign = TextAlign.Center,
                                    modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(if (canBuy) Brush.horizontalGradient(listOf(accent, Color(0xFFFFF3B0))) else Brush.horizontalGradient(listOf(Color(0xFF3A3A4A), Color(0xFF2A2A38))))
                                        .clickable(enabled = canBuy) {
                                            busy = true
                                            onBuyTier(n) { ok -> busy = false; if (ok) celebrate("TIER $n UNLOCKED!  ${tr.mags} magazines · ${tr.mags * MagazineBay.MAG_SIZE} shots") else { msgGood = false; msg = "Purchase cancelled" } }
                                        }.padding(horizontal = 16.dp, vertical = 12.dp))
                            }
                            if (!owned && reason != null) {
                                Text(reason, color = if (isNext) Color(0xFFFFAB91) else Color.White.copy(alpha = 0.5f), fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = MONO, modifier = Modifier.padding(top = 6.dp))
                            }
                            if (!owned && tr.proMonths > 0) {
                                Box(Modifier.fillMaxWidth().padding(top = 5.dp).height(7.dp).clip(RoundedCornerShape(4.dp)).background(Color.White.copy(alpha = 0.1f))) {
                                    Box(Modifier.fillMaxWidth((proMonths.toFloat() / tr.proMonths).coerceIn(0f, 1f)).fillMaxHeight().background(Brush.horizontalGradient(listOf(CYAN, GOLD))))
                                }
                                Text("Pro time: $proMonths / ${tr.proMonths} months", color = Color.White.copy(alpha = 0.55f), fontSize = 9.sp, fontFamily = MONO, modifier = Modifier.padding(top = 2.dp))
                            }
                        }
                        if (best) Text("BEST VALUE", color = Color.Black, fontSize = 8.sp, fontWeight = FontWeight.Black, fontFamily = MONO, modifier = Modifier.align(Alignment.TopEnd).clip(RoundedCornerShape(bottomStart = 10.dp)).background(GOLD).padding(horizontal = 8.dp, vertical = 3.dp))
                    }
                }
                if (own > usable) Text("Your Pro has ended: tiers above 2 sleep (storage works up to tier 2) and wake up when Pro returns. Nothing is lost.", color = Color(0xFFFFAB91), fontSize = 10.sp, fontFamily = MONO, modifier = Modifier.padding(bottom = 6.dp))

                // ---------------- bomb rack (gems) ----------------
                Spacer(Modifier.height(14.dp))
                Text("BOMB RACK", color = PINK, fontSize = 14.sp, fontWeight = FontWeight.Black, fontFamily = MONO)
                Text("Carry more Brood Bombs · bought with gems", color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp, fontFamily = MONO)
                Spacer(Modifier.height(8.dp))
                val rsh = RoundedCornerShape(16.dp)
                Column(Modifier.fillMaxWidth().clip(rsh).background(Color(0xFF16103A)).border(1.5.dp, PINK.copy(alpha = 0.6f), rsh).padding(12.dp)) {
                    Canvas(Modifier.fillMaxWidth().height(46.dp)) {
                        val slots = BroodBomb.BASE + BroodBomb.RACK_MAX; val gap = 8.dp.toPx(); val sw = ((size.width - gap * (slots - 1)) / slots).coerceAtMost(54.dp.toPx())
                        val x0 = (size.width - (slots * sw + (slots - 1) * gap)) / 2f
                        for (i in 0 until slots) {
                            val open = i < BroodBomb.BASE + rack; val have = i < BroodBomb.count(ctx)
                            val x = x0 + i * (sw + gap)
                            drawRoundRect((if (open) PINK else Color.White).copy(alpha = if (open) 0.7f else 0.15f), Offset(x, 2f), Size(sw, size.height - 4f), CornerRadius(8.dp.toPx()), style = Stroke(if (open) 2.dp.toPx() else 1.dp.toPx()))
                            if (have) drawCircle(Brush.radialGradient(listOf(Color(0xFFFFF3B0), PINK, Color.Transparent), Offset(x + sw / 2f, size.height / 2f), sw * (0.45f + 0.06f * sin(t * tau * 2f + i))), sw * 0.5f, Offset(x + sw / 2f, size.height / 2f))
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text("Slots: ${BroodBomb.BASE + rack} of ${BroodBomb.BASE + BroodBomb.RACK_MAX}", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Black, fontFamily = MONO)
                    if (rack >= BroodBomb.RACK_MAX) Text("🏆 RACK MAXED", color = GOLD, fontSize = 13.sp, fontWeight = FontWeight.Black, fontFamily = MONO, modifier = Modifier.padding(top = 6.dp))
                    else {
                        val cost = BroodBomb.RACK_GEMS[rack]
                        Text("UPGRADE TO ${BroodBomb.BASE + rack + 1} SLOTS  ·  $cost 💎", color = Color.Black, fontSize = 13.sp, fontWeight = FontWeight.Black, fontFamily = MONO, textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 8.dp).fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(if (!busy) Brush.horizontalGradient(listOf(PINK, Color(0xFFFFE0F7))) else Brush.horizontalGradient(listOf(Color(0xFF555555), Color(0xFF444444))))
                                .clickable(enabled = !busy) { busy = true; onBuyBombRack { err -> busy = false; if (err == null) celebrate("BOMB RACK UPGRADED!  ${BroodBomb.BASE + BroodBomb.rack(ctx)} slots") else { msgGood = false; msg = err } } }.padding(vertical = 10.dp))
                    }
                    if (artBomb != null) Unit
                }
                if (msg.isNotEmpty()) Text(msg, color = if (msgGood) GREEN else Color(0xFFFF8A80), fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = MONO, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 10.dp))
            }
            if (burst >= 0f) {
                val k = (burst / 1.6f).coerceIn(0f, 1f)
                Canvas(Modifier.fillMaxSize()) {
                    val c = Offset(size.width / 2f, size.height * 0.45f)
                    for (i in 0 until 36) {
                        val a = i * (tau / 36f) + (i % 3) * 0.2f; val r = (40f + (140f + (i % 4) * 40f) * k).dp.toPx()
                        drawCircle((if (i % 2 == 0) GOLD else CYAN).copy(alpha = 1f - k), (4f * (1f - k) + 1f).dp.toPx(), Offset(c.x + cos(a) * r, c.y + sin(a) * r))
                    }
                    drawCircle(GOLD.copy(alpha = 0.7f * (1f - k)), (30f + 260f * k).dp.toPx(), c, style = Stroke((6f * (1f - k) + 1f).dp.toPx()))
                }
                Text(burstText, color = GOLD.copy(alpha = (1f - k * 0.6f)), fontSize = 16.sp, fontWeight = FontWeight.Black, fontFamily = MONO, textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center).offset(y = (-(k * 40f)).dp).clip(RoundedCornerShape(14.dp)).background(Color(0xE6080620)).border(2.dp, GOLD, RoundedCornerShape(14.dp)).padding(horizontal = 18.dp, vertical = 12.dp))
            }
        }
    }
}
