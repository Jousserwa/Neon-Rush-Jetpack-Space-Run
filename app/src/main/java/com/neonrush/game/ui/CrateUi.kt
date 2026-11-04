package com.neonrush.game.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.window.Dialog
import com.neonrush.game.AirSupport
import com.neonrush.game.BombDepot
import com.neonrush.game.MilShield
import com.neonrush.game.ScoreVouchers
import com.neonrush.game.Wingman
import kotlinx.coroutines.delay
import com.neonrush.game.CrateResult
import com.neonrush.game.CrateWeek
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

private val CR_GOLD = Color(0xFFFFD23F)
private val CR_MONO = FontFamily.Monospace

/**
 * The animated crate. Idle: it floats, a halo of light rays turns behind it and the seam glows.
 * [charging]: it shakes harder and harder. [opened]: the lid flips back, light beams shoot out, sparks burst and fall.
 */
@Composable
fun CrateStage(charging: Boolean, opened: Boolean, size: Dp = 170.dp, modifier: Modifier = Modifier) {
    val inf = rememberInfiniteTransition(label = "crate")
    val clock by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(6000, easing = LinearEasing)), label = "clock")
    val bob by inf.animateFloat(-5f, 5f, infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "bob")
    val open by animateFloatAsState(if (opened) 1f else 0f, spring(dampingRatio = 0.45f, stiffness = 120f), label = "open")
    val burst by animateFloatAsState(if (opened) 1f else 0f, tween(1400, easing = LinearOutSlowInEasing), label = "burst")
    val shake by animateFloatAsState(if (charging && !opened) 1f else 0f, tween(650), label = "shake")
    val tw = clock * 2f * PI.toFloat()
    Canvas(modifier.size(size)) {
        val w = this.size.width; val h = this.size.height
        val c = Offset(w / 2f, h * 0.60f + (if (opened) 0f else bob))
        val sx = sin(tw * 40f) * shake * 7f
        val cx = c.x + sx
        // light rays
        val rayA = (0.16f + 0.10f * sin(tw * 6f)) * (if (opened) 2.3f else 1f + shake)
        rotate(clock * 360f, pivot = Offset(cx, c.y - h * 0.05f)) {
            for (i in 0 until 12) {
                val a = i * (2f * PI.toFloat() / 12f)
                val p = Path().apply {
                    moveTo(cx, c.y - h * 0.05f)
                    lineTo(cx + cos(a - 0.08f) * w * 0.8f, c.y - h * 0.05f + sin(a - 0.08f) * w * 0.8f)
                    lineTo(cx + cos(a + 0.08f) * w * 0.8f, c.y - h * 0.05f + sin(a + 0.08f) * w * 0.8f)
                    close()
                }
                drawPath(p, CR_GOLD.copy(alpha = rayA.coerceIn(0f, 0.6f)))
            }
        }
        // halo
        drawCircle(Brush.radialGradient(listOf(CR_GOLD.copy(alpha = 0.45f + 0.3f * open), Color.Transparent), center = Offset(cx, c.y), radius = w * 0.55f), radius = w * 0.55f, center = Offset(cx, c.y))
        val bw = w * 0.52f; val bh = h * 0.30f
        val left = cx - bw / 2f; val top = c.y - bh / 2f
        // inner glow when open
        if (open > 0.05f) drawRoundRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.95f * open), CR_GOLD.copy(alpha = 0.6f * open)), startY = top - bh * 0.4f, endY = top + bh * 0.2f), Offset(left + 6f, top - bh * 0.35f * open), Size(bw - 12f, bh * 0.5f), CornerRadius(8f))
        // body
        drawRoundRect(Brush.verticalGradient(listOf(Color(0xFF6B4A1B), Color(0xFF3A2610)), startY = top, endY = top + bh), Offset(left, top), Size(bw, bh), CornerRadius(10f))
        drawRoundRect(CR_GOLD, Offset(left, top), Size(bw, bh), CornerRadius(10f), style = Stroke(4f))
        for (k in 1..2) drawRect(CR_GOLD.copy(alpha = 0.85f), Offset(left + bw * (0.18f + 0.52f * (k - 1)), top), Size(bw * 0.12f, bh))
        // lock plate
        drawRoundRect(Color(0xFFFFE57F), Offset(cx - bw * 0.07f, top + bh * 0.08f), Size(bw * 0.14f, bh * 0.28f), CornerRadius(5f))
        // lid: hinged at the back (top-left), flips up as [open] -> 1
        val lidH = bh * 0.38f
        rotate(-open * 118f, pivot = Offset(left, top)) {
            drawRoundRect(Brush.verticalGradient(listOf(Color(0xFF8A6226), Color(0xFF5B3E17)), startY = top - lidH, endY = top), Offset(left - 3f, top - lidH), Size(bw + 6f, lidH), CornerRadius(12f))
            drawRoundRect(CR_GOLD, Offset(left - 3f, top - lidH), Size(bw + 6f, lidH), CornerRadius(12f), style = Stroke(4f))
        }
        // seam glow while idle / charging
        if (open < 0.05f) drawRect(Color.White.copy(alpha = (0.25f + 0.25f * sin(tw * 8f) + 0.4f * shake).coerceIn(0f, 0.9f)), Offset(left + 4f, top - 2f), Size(bw - 8f, 3f))
        // burst: sparks fly out and fall
        if (opened) {
            for (i in 0 until 30) {
                val a = (i * 2.399f) % (2f * PI.toFloat())
                val sp = 0.35f + (i % 5) * 0.12f
                val d = burst * w * sp * 1.1f
                val px = cx + cos(a) * d
                val py = top + sin(a) * d * 0.9f - burst * h * 0.1f + burst * burst * h * 0.35f
                val al = (1f - burst).coerceIn(0f, 1f)
                drawCircle(if (i % 3 == 0) Color.White.copy(alpha = al) else CR_GOLD.copy(alpha = al), radius = 3f + (i % 4), center = Offset(px, py))
            }
            // beams
            for (i in 0 until 5) {
                val a = -PI.toFloat() / 2f + (i - 2) * 0.28f
                drawLine(Color.White.copy(alpha = (0.55f * (1f - burst * 0.6f)).coerceIn(0f, 1f)), Offset(cx, top), Offset(cx + cos(a) * w * 0.8f, top + sin(a) * h * 0.9f), strokeWidth = 5f)
            }
        }
    }
}

/** Reveal of what the crate gave: big icon that pops in with a ring, then the text. */
@Composable
fun CrateReveal(res: CrateResult, hullName: (String) -> String, modifier: Modifier = Modifier) {
    val pop = remember { Animatable(0f) }
    LaunchedEffect(res) { pop.snapTo(0f); pop.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = 180f)) }
    val ring by rememberInfiniteTransition(label = "ring").animateFloat(0.9f, 1.15f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "r")
    val (icon, title) = when {
        res.milShields > 0 -> "🛡" to "+${res.milShields} MILITARY SHIELDS"
        res.airCalls > 0 -> "🚁" to "+${res.airCalls} AIRSTRIKE CALL"
        res.bombs > 0 -> "💣" to "+${res.bombs} BOMBS"
        res.shardHullId != null && res.shardCount > 0 -> "🧩" to "+${res.shardCount} ${hullName(res.shardHullId)} SHARDS"
        else -> "💎" to "+${res.gems} GEMS"
    }
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(130.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                drawCircle(Brush.radialGradient(listOf(CR_GOLD.copy(alpha = 0.55f), Color.Transparent)), radius = size.minDimension / 2f * ring * pop.value)
                drawCircle(CR_GOLD.copy(alpha = 0.8f), radius = size.minDimension * 0.36f * ring * pop.value, style = Stroke(3f))
            }
            Text(icon, fontSize = 60.sp, modifier = Modifier.scale(0.2f + 0.8f * pop.value))
        }
        Text(title, color = CR_GOLD, fontWeight = FontWeight.Black, fontSize = 20.sp, fontFamily = CR_MONO, textAlign = TextAlign.Center)
        if (res.gems > 0 && (res.milShields > 0 || res.airCalls > 0 || res.bombs > 0 || res.shardCount > 0)) {
            Text("+${res.gems} 💎", color = Color(0xFF00E5FF), fontWeight = FontWeight.Black, fontSize = 18.sp, fontFamily = CR_MONO)
        }
        if (res.isStreakBonus) Text(res.message ?: "", color = CR_GOLD, fontWeight = FontWeight.Black, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
        if (res.unlockedHullId != null) Text("🎊 ${hullName(res.unlockedHullId)} UNLOCKED!", color = Color.White, fontWeight = FontWeight.Black, fontSize = 16.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
        if (res.weekReward) Text("🎉 WEEK COMPLETE! SPIN THE WHEEL FOR ${CrateWeek.REFILLS} FREE REFILLS", color = Color(0xFF69F0AE), fontWeight = FontWeight.Black, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
        WeekPips(days = res.weekDays, modifier = Modifier.padding(top = 10.dp))
    }
}

/** Six pips: open the crate AND play, six days in a row, for 5 free refills (once a month). */
@Composable
fun WeekPips(days: Int, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val refills = CrateWeek.freeRefills(ctx)
    val done = CrateWeek.rewardedThisMonth(ctx)
    val pulse by rememberInfiniteTransition(label = "pip").animateFloat(0.4f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "p")
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            for (i in 1..CrateWeek.DAYS) {
                val on = i <= days
                val next = i == days + 1
                Box(Modifier.size(if (i == CrateWeek.DAYS) 26.dp else 20.dp).clip(CircleShape)
                    .background(if (on) CR_GOLD else Color.White.copy(alpha = if (next) 0.25f * pulse + 0.1f else 0.1f)), contentAlignment = Alignment.Center) {
                    Text(if (i == CrateWeek.DAYS) "⛽" else if (on) "✓" else "$i", color = if (on) Color.Black else Color.White.copy(alpha = 0.6f), fontSize = 10.sp, fontWeight = FontWeight.Black)
                }
            }
        }
        Text(
            if (done) "Monthly refill bonus collected · back next month" else "Open the crate AND play, $days/${CrateWeek.DAYS} days in a row = ${CrateWeek.REFILLS} free refills (once a month)",
            color = Color.White.copy(alpha = 0.7f), fontSize = 9.sp, fontFamily = CR_MONO, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp)
        )
        if (refills > 0) Text("⛽ Free refills banked: $refills (they fire by themselves at 20% fuel)", color = Color(0xFF69F0AE), fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = CR_MONO, textAlign = TextAlign.Center)
    }
}


// ---------------------------------------------------------------------------------------------------------------------------
// BONUS WHEEL (Stage 169): earned by the 7-day crate streak (random prize) and the 6-day week (always lands on the 5 refills).
// The prize is decided and paid the moment you press SPIN, so closing the screen can never lose it.
// ---------------------------------------------------------------------------------------------------------------------------
private class WheelSeg(val icon: String, val label: String, val color: Color)
private val WHEEL = listOf(
    WheelSeg("⛽", "5 REFILLS", Color(0xFF00C853)), WheelSeg("💎", "20 GEMS", Color(0xFF2979FF)), WheelSeg("💣", "10 BOMBS", Color(0xFFFF4081)), WheelSeg("🚁", "2 AIRSTRIKES", Color(0xFF00BFA5)),
    WheelSeg("🛡", "2 MIL SHIELDS", Color(0xFFFFB300)), WheelSeg("💊", "3 PILLS", Color(0xFF7C4DFF)), WheelSeg("🎟", "1 VOUCHER", Color(0xFF26C6DA)), WheelSeg("💎", "10 GEMS", Color(0xFFFF7043))
)

private fun payWheel(ctx: android.content.Context, i: Int, credit: (Int) -> Unit) {
    when (i) {
        0 -> CrateWeek.addRefills(ctx, CrateWeek.REFILLS)
        1 -> credit(20)
        2 -> BombDepot.addReserve(ctx, 10)
        3 -> AirSupport.addCalls(ctx, 2)
        4 -> MilShield.addStock(ctx, 2)
        5 -> Wingman.addPill(ctx, 3)
        6 -> ScoreVouchers.addVouchers(ctx, 1)
        7 -> credit(10)
    }
}

@Composable
fun BonusWheelDialog(kind: Int, credit: (Int) -> Unit, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val tm = rememberTextMeasurer()
    val rot = remember { Animatable(0f) }
    var state by remember { mutableStateOf(0) }       // 0 ready, 1 spinning, 2 landed
    var won by remember { mutableStateOf(0) }
    val clock by rememberInfiniteTransition(label = "wheelClock").animateFloat(0f, 1f, infiniteRepeatable(tween(1200, easing = LinearEasing)), label = "c")
    LaunchedEffect(state) {
        if (state == 1) {
            val seg = 360f / WHEEL.size
            val land = 360f - (won * seg + seg / 2f) + (kotlin.random.Random.nextFloat() - 0.5f) * seg * 0.6f
            rot.snapTo(rot.value % 360f)
            rot.animateTo(rot.value + 360f * 6f + land, tween(4600, easing = CubicBezierEasing(0.08f, 0.75f, 0.15f, 1f)))
            state = 2
        }
    }
    Dialog(onDismissRequest = { if (state != 1) onClose() }) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color(0xFF0B1020)).border(2.dp, CR_GOLD, RoundedCornerShape(18.dp)).padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (kind == 2) "WEEK COMPLETE!" else "STREAK BONUS!", color = CR_GOLD, fontSize = 20.sp, fontWeight = FontWeight.Black, fontFamily = CR_MONO)
            Text(if (kind == 2) "6 days in a row. Your wheel is ready." else "7-day crate streak. Spin for a bonus.", color = Color.White.copy(alpha = 0.75f), fontSize = 11.sp, fontFamily = CR_MONO)
            Spacer(Modifier.height(10.dp))
            Box(Modifier.size(280.dp), contentAlignment = Alignment.TopCenter) {
                Canvas(Modifier.fillMaxSize().padding(top = 14.dp)) {
                    val c = Offset(size.width / 2f, size.height / 2f); val r = size.minDimension / 2f - 10f
                    val seg = 360f / WHEEL.size
                    drawCircle(Brush.radialGradient(listOf(CR_GOLD.copy(alpha = 0.35f), Color.Transparent), c, r * 1.25f), r * 1.25f, c)
                    rotate(rot.value, c) {
                        for (i in WHEEL.indices) {
                            drawArc(WHEEL[i].color, -90f + i * seg, seg, true, Offset(c.x - r, c.y - r), Size(r * 2, r * 2))
                            drawArc(Color.White.copy(alpha = 0.35f), -90f + i * seg, seg, true, Offset(c.x - r, c.y - r), Size(r * 2, r * 2), style = Stroke(2f))
                            val mid = Math.toRadians((-90f + i * seg + seg / 2f).toDouble())
                            val tx = c.x + (r * 0.68f * cos(mid)).toFloat(); val ty = c.y + (r * 0.68f * sin(mid)).toFloat()
                            rotate(i * seg + seg / 2f, Offset(tx, ty)) {
                                val t1 = tm.measure(WHEEL[i].icon, TextStyle(fontSize = 22.sp)); drawText(t1, topLeft = Offset(tx - t1.size.width / 2f, ty - t1.size.height * 0.95f))
                                val t2 = tm.measure(WHEEL[i].label, TextStyle(fontSize = 8.sp, fontWeight = FontWeight.Black, color = Color.White, fontFamily = CR_MONO)); drawText(t2, topLeft = Offset(tx - t2.size.width / 2f, ty))
                            }
                        }
                    }
                    // rim lights
                    for (k in 0 until 24) {
                        val a = Math.toRadians((k * 15.0)); val on = ((k + (clock * 24f).toInt()) % 2 == 0) || state == 2
                        drawCircle(if (on) Color.White else CR_GOLD.copy(alpha = 0.4f), 4f, Offset(c.x + ((r + 4f) * cos(a)).toFloat(), c.y + ((r + 4f) * sin(a)).toFloat()))
                    }
                    drawCircle(Color(0xFF0B1020), r * 0.16f, c); drawCircle(CR_GOLD, r * 0.16f, c, style = Stroke(4f))
                }
                // pointer
                Canvas(Modifier.size(34.dp, 40.dp)) {
                    val p = Path().apply { moveTo(size.width / 2f, size.height); lineTo(0f, 0f); lineTo(size.width, 0f); close() }
                    drawPath(p, Color.White); drawPath(p, Color(0xFFFF5252), style = Stroke(5f))
                }
            }
            Spacer(Modifier.height(10.dp))
            when (state) {
                0 -> Text("🎡 SPIN", color = Color.Black, fontSize = 16.sp, fontWeight = FontWeight.Black, fontFamily = CR_MONO,
                    modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(CR_GOLD).clickable {
                        won = if (kind == 2) 0 else 1 + kotlin.random.Random.nextInt(WHEEL.size - 1)
                        payWheel(ctx, won, credit); CrateWeek.clearWheel(ctx); state = 1
                    }.padding(horizontal = 36.dp, vertical = 12.dp))
                1 -> Text("SPINNING…", color = CR_GOLD, fontSize = 14.sp, fontWeight = FontWeight.Black, fontFamily = CR_MONO)
                else -> {
                    Text("${WHEEL[won].icon} YOU WON ${WHEEL[won].label}!", color = Color(0xFF69F0AE), fontSize = 17.sp, fontWeight = FontWeight.Black, fontFamily = CR_MONO, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(8.dp))
                    Text("COLLECT", color = Color.Black, fontSize = 14.sp, fontWeight = FontWeight.Black, fontFamily = CR_MONO, modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(Color(0xFF69F0AE)).clickable { onClose() }.padding(horizontal = 30.dp, vertical = 10.dp))
                }
            }
        }
    }
}

/** Shows a pulsing SPIN button whenever a bonus wheel is waiting (7-day streak or full week). */
@Composable
fun BonusWheelButton(credit: (Int) -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val kind = CrateWeek.pendingWheel(ctx)
    var open by remember { mutableStateOf(false) }
    var openKind by remember { mutableStateOf(1) }
    if (kind > 0 || open) {
        val pulse by rememberInfiniteTransition(label = "wbtn").animateFloat(0.6f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "p")
        if (kind > 0) Text("🎡 SPIN YOUR BONUS WHEEL", color = Color.Black, fontSize = 13.sp, fontWeight = FontWeight.Black, fontFamily = CR_MONO, textAlign = TextAlign.Center,
            modifier = modifier.padding(top = 10.dp).scale(0.96f + 0.06f * pulse).clip(RoundedCornerShape(12.dp)).background(CR_GOLD.copy(alpha = pulse)).clickable { openKind = kind; open = true }.padding(horizontal = 18.dp, vertical = 10.dp))
        if (open) BonusWheelDialog(kind = openKind, credit = credit, onClose = { open = false })
    }
}
