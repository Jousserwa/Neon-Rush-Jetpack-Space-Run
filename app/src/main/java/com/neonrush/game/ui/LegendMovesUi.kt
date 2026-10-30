package com.neonrush.game.ui

import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neonrush.game.BossGuard
import com.neonrush.game.LegendMoves
import com.neonrush.game.NeonSoundEngine
import com.neonrush.game.WorldEchoBoss
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Legendary moves + boss banners, inside the gameplay box (after the touch layer so the buttons receive taps).
 *  - [legend]: DASH / PULSE / OVERDRIVE buttons with cooldown rings and the effects of each move.
 *  - Boss banners (any difficulty above Easy): the WORLD ECHO twist when a boss arrives, PHASE 2 when a Legendary boss transforms.
 */
@Composable
fun LegendMovesOverlay(
    active: Boolean, legend: Boolean, pilotY: Int, bossActive: Boolean,
    onDash: () -> Boolean, onPulse: () -> Boolean, onOverdrive: () -> Boolean, onBonus: (Int) -> Unit,
    onSmash: () -> Int = { 0 }, pickups: () -> List<Offset> = { emptyList() },
    modifier: Modifier = Modifier
) {
    val sfx = remember { NeonSoundEngine() }
    val dashScope = rememberCoroutineScope()
    var dashHeld by remember { mutableStateOf(false) }
    var dashJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }   // DASH needs a short deliberate hold (0.15 s): a finger sliding across it while steering does nothing
    val yRef = rememberUpdatedState(pilotY)
    val bossRef = rememberUpdatedState(bossActive)
    val bonusRef = rememberUpdatedState(onBonus)
    val smashRef = rememberUpdatedState(onSmash)
    val pickRef = rememberUpdatedState(pickups)
    var frame by remember { mutableStateOf(0) }
    var banner by remember { mutableStateOf("") }
    var bannerCol by remember { mutableStateOf(Color.White) }
    var bannerBig by remember { mutableStateOf(false) }
    var bannerLeft by remember { mutableStateOf(0f) }
    var pulseAge by remember { mutableStateOf(-1f) }
    var dashAge by remember { mutableStateOf(-1f) }
    var overBurst by remember { mutableStateOf(-1f) }
    var flash by remember { mutableStateOf(0f) }
    var pulled by remember { mutableStateOf(0) }
    val streams = remember { ArrayList<Offset>() }          // gems / fuel cells flying into the pilot after a pulse (x fraction, y percent)
    val pops = remember { ArrayList<Offset>() }             // smashed obstacles while dashing (x = pixel offset ahead of pilot in dp, y = percent offset)
    val popAges = remember { ArrayList<Float>() }
    val artBoost = rememberArt("boost_icon")

    fun say(s: String, c: Color, secs: Float, big: Boolean = false) { banner = s; bannerCol = c; bannerBig = big; bannerLeft = secs }
    fun tryDash() { if (onDash()) { sfx.playDash(); dashAge = 0f; say("💨 DASH · INVINCIBLE · SMASHES OBSTACLES", Color(0xFF80F0FF), 1.1f) } }
    fun tryPulse() {
        val pts = pickRef.value()
        if (onPulse()) {
            sfx.playPulse(); pulseAge = 0f; streams.clear(); streams.addAll(pts.take(24)); pulled = pts.size
            say(if (pts.isEmpty()) "🧲 PULSE · nothing in range" else "🧲 PULSE · ${pts.size} PICKUPS PULLED IN", Color(0xFFFF4DD2), 1.2f)
        }
    }
    fun tryOver() { if (onOverdrive()) { sfx.playOverdrive(); overBurst = 0f; flash = 0f; say("⚡ OVERDRIVE · FREE ARMOR SHOTS · 2× FIRE RATE · SCORE+", Color(0xFFFFC21A), 1.8f) } }

    LaunchedEffect(active) {
        if (!active) { banner = ""; return@LaunchedEffect }
        var last = 0L; var wasBoss = false; var seenPhase = BossGuard.phase2Ms; var bonusAcc = 0f; var smashT = 0f
        while (true) {
            val now = withFrameNanos { it }
            val dt = if (last == 0L) 0.016f else ((now - last) / 1_000_000_000f).coerceAtMost(0.05f)
            last = now
            if (legend) {
                if (!LegendMoves.overdriveOn()) LegendMoves.charge(dt * 0.02f)       // slow passive charge, kills and boss hits add more
                else { bonusAcc += dt; if (bonusAcc >= 0.25f) { bonusAcc = 0f; bonusRef.value(LegendMoves.OVER_SCORE_PER_SEC / 4) } }
                if (LegendMoves.dashing()) {   // the dash really smashes what is in front of the pilot
                    smashT -= dt
                    if (smashT <= 0f) {
                        smashT = 0.08f
                        val n = smashRef.value()
                        for (k in 0 until minOf(n, 3)) { pops.add(Offset(40f + Random.nextFloat() * 120f, (Random.nextFloat() - 0.5f) * 20f)); popAges.add(0f) }
                        if (n > 0) sfx.playSmash()
                    }
                }
            }
            if (pulseAge >= 0f) { pulseAge += dt; if (pulseAge > 0.8f) { pulseAge = -1f; streams.clear() } }
            if (dashAge >= 0f) { dashAge += dt; if (dashAge > 0.8f) dashAge = -1f }
            if (overBurst >= 0f) { overBurst += dt; if (overBurst > 0.6f) overBurst = -1f }
            if (flash > 0f) flash -= dt
            for (k in popAges.indices) popAges[k] = popAges[k] + dt
            while (popAges.isNotEmpty() && popAges[0] > 0.45f) { popAges.removeAt(0); pops.removeAt(0) }
            val boss = bossRef.value
            if (boss && !wasBoss && BossGuard.hard) say("🌍 WORLD ECHO · ${WorldEchoBoss.twistName()}\n${WorldEchoBoss.tip()}", Color(WorldEchoBoss.color()), 3.8f)
            wasBoss = boss
            if (BossGuard.phase2Ms != seenPhase) {
                seenPhase = BossGuard.phase2Ms
                if (boss && BossGuard.phase2) { say("⚡ PHASE 2 ⚡\nThe boss has transformed", Color(0xFFFF5252), 2.4f, true); flash = 0.5f; sfx.playPhase2() }
            }
            if (bannerLeft > 0f) { bannerLeft -= dt; if (bannerLeft <= 0f) banner = "" }
            frame++
        }
    }

    Box(modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            @Suppress("UNUSED_VARIABLE") val f = frame
            val w = size.width; val h = size.height; val d = density
            val py = yRef.value / 100f * h; val px = com.neonrush.game.PilotDrift.x * w
            val tau = 2f * kotlin.math.PI.toFloat()
            if (flash > 0f) drawRect(Color(0xFFFF1744).copy(alpha = 0.28f * (flash / 0.5f).coerceIn(0f, 1f)))
            // ---------------- DASH: speed lines across the whole screen, afterimages, cyan edges, invincibility bubble, smash bursts ----------------
            if (LegendMoves.dashing() || dashAge >= 0f) {
                val on = LegendMoves.dashing(); val k = (if (dashAge >= 0f) dashAge / 0.8f else 0f).coerceIn(0f, 1f); val fade = if (on) 1f else (1f - k)
                drawRect(Brush.radialGradient(listOf(Color.Transparent, Color(0xFF00E5FF).copy(alpha = 0.30f * fade)), Offset(w / 2f, h / 2f), h * 0.95f), size = size)
                for (i in 0 until 18) {
                    val yy = h * (0.03f + i * 0.054f); val len = (110 + 70 * (i % 3)) * d
                    val xx = w * (1.25f - ((frame * 0.045f * (1f + (i % 3) * 0.5f) + i * 0.37f) % 1.45f))
                    drawLine(Brush.horizontalGradient(listOf(Color.Transparent, (if (i % 2 == 0) Color.White else Color(0xFF80F0FF)).copy(alpha = 0.55f * fade)), xx, xx + len), Offset(xx, yy), Offset(xx + len, yy), (2f + (i % 3)) * d, StrokeCap.Round)
                }
                for (a in 1..5) {   // afterimages of the pilot trailing behind
                    val ox = px - a * 24 * d
                    drawRoundRect(Color(0xFF80F0FF).copy(alpha = 0.32f * fade / a), Offset(ox - 16 * d, py - 8 * d), androidx.compose.ui.geometry.Size(34 * d, 16 * d), androidx.compose.ui.geometry.CornerRadius(8 * d))
                    drawCircle(Color(0xFF80F0FF).copy(alpha = 0.28f * fade / a), 8 * d, Offset(ox + 10 * d, py - 8 * d))
                }
                drawCircle(Color(0xFF80F0FF).copy(alpha = 0.16f * fade), 40 * d, Offset(px, py)); drawCircle(Color(0xFF80F0FF).copy(alpha = 0.9f * fade), 40 * d, Offset(px, py), style = Stroke(2.5f * d))
                val v = Path().apply { moveTo(px + 70 * d, py); lineTo(px + 22 * d, py - 26 * d); lineTo(px + 22 * d, py + 26 * d); close() }
                drawPath(v, Color.White.copy(alpha = 0.22f * fade))
            }
            for (i in pops.indices) {   // obstacles smashed by the dash
                val a = popAges[i] / 0.45f; val c = Offset(px + pops[i].x * d, py + pops[i].y / 100f * h)
                drawCircle(Color(0xFFFFE082).copy(alpha = 1f - a), (6f + 26f * a) * d, c, style = Stroke(3f * d * (1f - a) + 1f))
                for (r in 0..5) { val an = r * tau / 6f; drawLine(Color.White.copy(alpha = 1f - a), Offset(c.x + cos(an) * 8 * d * (1f + a), c.y + sin(an) * 8 * d * (1f + a)), Offset(c.x + cos(an) * 22 * d * (0.5f + a), c.y + sin(an) * 22 * d * (0.5f + a)), 2f * d) }
            }
            // ---------------- PULSE: magnetic rings + every pickup visibly streams into the pilot ----------------
            if (pulseAge >= 0f) {
                val k = (pulseAge / 0.8f).coerceIn(0f, 1f)
                for (r in 0..2) {
                    val kk = (k - r * 0.1f).coerceIn(0f, 1f)
                    drawCircle(Color(0xFFFF4DD2).copy(alpha = 0.8f * (1f - kk)), (20f + 380f * kk) * d, Offset(px, py), style = Stroke((5f * (1f - kk) + 1f) * d))
                }
                for ((i, s) in streams.withIndex()) {
                    val kk = ((k * 1.6f) - i * 0.02f).coerceIn(0f, 1f); val e = kk * kk * (3f - 2f * kk)
                    val sx = s.x * w; val sy = s.y / 100f * h
                    for (tr in 0..4) { val te = (e - tr * 0.07f).coerceAtLeast(0f); drawCircle(Color(0xFFFF80AB).copy(alpha = (1f - tr * 0.2f) * (1f - kk * 0.6f)), (5f - tr * 0.7f) * d, Offset(sx + (px - sx) * te, sy + (py - sy) * te + sin(te * 3.14f) * -30f * d)) }
                    drawCircle(Color.White.copy(alpha = 1f - kk), 3f * d, Offset(sx + (px - sx) * e, sy + (py - sy) * e + sin(e * 3.14f) * -30f * d))
                }
                if (pulled > 0 && k < 0.8f) { /* the number is in the banner */ }
            }
            // ---------------- OVERDRIVE: flaming edges, rising embers, lightning around the pilot, gold speed ----------------
            if (LegendMoves.overdriveOn()) {
                val a = 0.5f + 0.3f * sin(frame * 0.25f)
                drawRect(Color(0xFFFFC21A).copy(alpha = a), topLeft = Offset(0f, 0f), size = androidx.compose.ui.geometry.Size(w, h), style = Stroke(6 * d))
                for (side in 0..1) {
                    val x0 = if (side == 0) 0f else w
                    drawRect(Brush.horizontalGradient(if (side == 0) listOf(Color(0x88FF6F00), Color.Transparent) else listOf(Color.Transparent, Color(0x88FF6F00)), if (side == 0) 0f else w - 46 * d, if (side == 0) 46 * d else w), Offset(if (side == 0) 0f else w - 46 * d, 0f), androidx.compose.ui.geometry.Size(46 * d, h), alpha = 0.6f + 0.4f * sin(frame * 0.4f + side))
                }
                for (i in 0 until 26) {
                    val ex = w * ((i * 0.137f + sin(frame * 0.02f + i) * 0.02f) % 1f); val ey = h * (1f - ((frame * 0.006f * (0.5f + (i % 4) * 0.25f) + i * 0.173f) % 1f))
                    drawCircle((if (i % 3 == 0) Color(0xFFFFF59D) else Color(0xFFFFB300)).copy(alpha = 0.7f), (1.5f + (i % 3)) * d, Offset(ex, ey))
                }
                drawCircle(Brush.radialGradient(listOf(Color(0x66FFC21A), Color.Transparent), Offset(px, py), 80 * d), 80 * d, Offset(px, py))
                for (q in 0..2) {
                    if (Random.nextFloat() < 0.5f) {
                        val p = Path(); var cx = px + (Random.nextFloat() - 0.5f) * 20 * d; var cy = py + (Random.nextFloat() - 0.5f) * 20 * d; p.moveTo(cx, cy)
                        for (j in 0 until 4) { cx += (Random.nextFloat() - 0.5f) * 36 * d; cy += (Random.nextFloat() - 0.5f) * 36 * d; p.lineTo(cx, cy) }
                        drawPath(p, Color(0xFFFFF59D).copy(alpha = 0.9f), style = Stroke(2f * d))
                    }
                }
            }
            if (overBurst >= 0f) {
                val k = (overBurst / 0.6f).coerceIn(0f, 1f)
                drawRect(Color.White.copy(alpha = 0.3f * (1f - k)))
                for (r in 0..1) { val kk = (k - r * 0.12f).coerceIn(0f, 1f); drawCircle(Color(0xFFFFE082).copy(alpha = 0.9f * (1f - kk)), (30f + 300f * kk) * d, Offset(px, py), style = Stroke(8 * d * (1f - kk) + 1f)) }
            }
        }
        if (banner.isNotEmpty()) {
            val a = if (bannerBig) 1f else (bannerLeft / 0.6f).coerceIn(0f, 1f) * 0.85f + 0.15f
            Text(banner, color = bannerCol.copy(alpha = a), fontSize = if (bannerBig) 15.sp else 10.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 76.dp).clip(RoundedCornerShape(12.dp)).background(Color(0x99080620)).border(1.dp, bannerCol.copy(alpha = 0.5f * a), RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 5.dp))
        }
        if (legend) {
            @Suppress("UNUSED_VARIABLE") val f = frame
            Row(Modifier.align(Alignment.BottomEnd).padding(end = 94.dp, bottom = 3.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
                val dc = LegendMoves.dashCd(); val pc = LegendMoves.pulseCd(); val on = LegendMoves.overdriveOn()
                GameButton(BtnKind.DASH, 52.dp, Color(0xFF80F0FF), "DASH", if (LegendMoves.dashing()) "GO!" else if (dc > 0f) "${kotlin.math.ceil(dc * LegendMoves.DASH_CD / 1000f).toInt()}" else "SMASH", 1f - dc, dc <= 0f, LegendMoves.dashing(), { dashHeld = true; dashJob?.cancel(); dashJob = dashScope.launch { kotlinx.coroutines.delay(150); if (dashHeld) tryDash() } }, { dashHeld = false; dashJob?.cancel() })
                GameButton(BtnKind.PULSE, 52.dp, Color(0xFFFF4DD2), "PULSE", if (pc > 0f) "${kotlin.math.ceil(pc * LegendMoves.PULSE_CD / 1000f).toInt()}" else "MAGNET", 1f - pc, pc <= 0f, false, { tryPulse() })
                GameButton(BtnKind.OVER, 54.dp, Color(0xFFFFC21A), "OVERDRIVE", if (on) "${kotlin.math.ceil(LegendMoves.overLeft() * LegendMoves.OVER_MS / 1000f).toInt()}s" else if (LegendMoves.meter >= 1f) "READY" else "${(LegendMoves.meter * 100).toInt()}%",
                    if (on) LegendMoves.overLeft() else LegendMoves.meter, LegendMoves.meter >= 1f && !on, on, { tryOver() })
            }
        }
    }
}

/** Home: this week's World Echo Boss, its twist and whether it has been cleared. */
@Composable
fun WorldEchoCard(isPro: Boolean, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    @Suppress("UNUSED_VARIABLE") val v = WorldEchoBoss.version
    val done = WorldEchoBoss.cleared(ctx)
    val col = Color(WorldEchoBoss.color())
    val shape = RoundedCornerShape(16.dp)
    val inf = androidx.compose.animation.core.rememberInfiniteTransition(label = "echo")
    val t by inf.animateFloat(0f, 1f, androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(2400, easing = androidx.compose.animation.core.LinearEasing)), label = "t")
    Row(
        modifier.fillMaxWidth().clip(shape).background(Brush.horizontalGradient(listOf(Color(0xFF14102E), col.copy(alpha = 0.22f), Color(0xFF0B0820))))
            .border(2.dp, col.copy(alpha = if (done) 0.35f else 0.8f), shape).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(54.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                for (r in 0..1) { val k = (t + r * 0.5f) % 1f; drawCircle(col.copy(alpha = 0.7f * (1f - k)), (10f + 20f * k).dp.toPx(), center, style = Stroke(2.dp.toPx())) }
                drawCircle(col.copy(alpha = 0.18f), 20.dp.toPx(), center)
            }
            Text(WorldEchoBoss.icon(), fontSize = 24.sp)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text("WORLD ECHO BOSS", color = col, fontSize = 11.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
            Text("${WorldEchoBoss.worldTitle()} · ${WorldEchoBoss.twistName()}", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
            Text(WorldEchoBoss.tip(), color = Color.White.copy(alpha = 0.75f), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
            Text(
                if (done) "✓ CLEARED this week · new echo in ${WorldEchoBoss.daysLeft()}d · streak ${WorldEchoBoss.streak(ctx)}"
                else "Defeat any boss (Medium+) · +${if (isPro) WorldEchoBoss.GEMS_PRO else WorldEchoBoss.GEMS} 💎 + a fusion core · ${WorldEchoBoss.daysLeft()}d left",
                color = if (done) Color(0xFF69F0AE) else Color(0xFFFFE082), fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(top = 3.dp)
            )
        }
    }
}
