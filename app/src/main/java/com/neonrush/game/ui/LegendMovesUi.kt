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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import kotlin.math.sin

/**
 * Legendary moves + boss banners, inside the gameplay box (after the touch layer so the buttons receive taps).
 *  - [legend]: DASH / PULSE / OVERDRIVE buttons with cooldown rings and the effects of each move.
 *  - Boss banners (any difficulty above Easy): the WORLD ECHO twist when a boss arrives, PHASE 2 when a Legendary boss transforms.
 */
@Composable
fun LegendMovesOverlay(
    active: Boolean, legend: Boolean, pilotY: Int, bossActive: Boolean,
    onDash: () -> Boolean, onPulse: () -> Boolean, onOverdrive: () -> Boolean, onBonus: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val sfx = remember { NeonSoundEngine() }
    val yRef = rememberUpdatedState(pilotY)
    val bossRef = rememberUpdatedState(bossActive)
    val bonusRef = rememberUpdatedState(onBonus)
    var frame by remember { mutableStateOf(0) }
    var banner by remember { mutableStateOf("") }
    var bannerCol by remember { mutableStateOf(Color.White) }
    var bannerBig by remember { mutableStateOf(false) }
    var pulseAge by remember { mutableStateOf(-1f) }
    var dashAge by remember { mutableStateOf(-1f) }
    var overBurst by remember { mutableStateOf(-1f) }
    var flash by remember { mutableStateOf(0f) }

    fun tryDash() { if (onDash()) { sfx.playDash(); dashAge = 0f } }
    fun tryPulse() { if (onPulse()) { sfx.playPulse(); pulseAge = 0f } }
    fun tryOver() { if (onOverdrive()) { sfx.playOverdrive(); overBurst = 0f } }

    LaunchedEffect(active) {
        if (!active) { banner = ""; return@LaunchedEffect }
        var last = 0L; var bannerLeft = 0f; var wasBoss = false; var seenPhase = BossGuard.phase2Ms; var bonusAcc = 0f
        while (true) {
            val now = withFrameNanos { it }
            val dt = if (last == 0L) 0.016f else ((now - last) / 1_000_000_000f).coerceAtMost(0.05f)
            last = now
            if (legend) {
                if (!LegendMoves.overdriveOn()) LegendMoves.charge(dt * 0.02f)       // slow passive charge, kills and boss hits add more
                else { bonusAcc += dt; if (bonusAcc >= 0.25f) { bonusAcc = 0f; bonusRef.value(LegendMoves.OVER_SCORE_PER_SEC / 4) } }
            }
            if (pulseAge >= 0f) { pulseAge += dt; if (pulseAge > 0.7f) pulseAge = -1f }
            if (dashAge >= 0f) { dashAge += dt; if (dashAge > 0.8f) dashAge = -1f }
            if (overBurst >= 0f) { overBurst += dt; if (overBurst > 0.6f) overBurst = -1f }
            if (flash > 0f) flash -= dt
            val boss = bossRef.value
            if (boss && !wasBoss && BossGuard.hard) {
                banner = "🌍 WORLD ECHO · ${WorldEchoBoss.twistName()}\n${WorldEchoBoss.tip()}"; bannerCol = Color(WorldEchoBoss.color()); bannerBig = false; bannerLeft = 3.8f
            }
            wasBoss = boss
            if (BossGuard.phase2Ms != seenPhase) {
                seenPhase = BossGuard.phase2Ms
                if (boss && BossGuard.phase2) { banner = "⚡ PHASE 2 ⚡\nThe boss has transformed"; bannerCol = Color(0xFFFF5252); bannerBig = true; bannerLeft = 2.4f; flash = 0.5f; sfx.playPhase2() }
            }
            if (bannerLeft > 0f) { bannerLeft -= dt; if (bannerLeft <= 0f) banner = "" }
            frame++
        }
    }

    Box(modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            @Suppress("UNUSED_VARIABLE") val f = frame
            val w = size.width; val h = size.height
            val py = yRef.value / 100f * h; val px = com.neonrush.game.PilotDrift.x * w
            if (flash > 0f) drawRect(Color(0xFFFF1744).copy(alpha = 0.28f * (flash / 0.5f).coerceIn(0f, 1f)))
            if (LegendMoves.dashing() || dashAge >= 0f) {
                val k = (if (dashAge >= 0f) dashAge / 0.8f else 0f).coerceIn(0f, 1f)
                for (i in 0 until 9) {
                    val yy = py + (i - 4) * 14.dp.toPx() + sin(frame * 0.3f + i) * 4.dp.toPx()
                    val len = (90 + 60 * (i % 3)).dp.toPx()
                    drawLine(Brush.horizontalGradient(listOf(Color.Transparent, Color(0xFF80F0FF).copy(alpha = 0.8f * (1f - k))), px - len, px), Offset(px - len, yy), Offset(px + 20.dp.toPx(), yy), 3.dp.toPx(), StrokeCap.Round)
                }
                drawCircle(Color(0xFF80F0FF).copy(alpha = 0.25f * (1f - k)), 44.dp.toPx(), Offset(px, py))
            }
            if (pulseAge >= 0f) {
                val k = (pulseAge / 0.7f).coerceIn(0f, 1f)
                for (r in 0..2) {
                    val kk = (k - r * 0.12f).coerceIn(0f, 1f)
                    drawCircle(Color(0xFFFF4DD2).copy(alpha = 0.8f * (1f - kk)), (20f + 380f * kk).dp.toPx(), Offset(px, py), style = Stroke((5f * (1f - kk) + 1f).dp.toPx()))
                }
            }
            if (LegendMoves.overdriveOn()) {
                val a = 0.5f + 0.3f * sin(frame * 0.25f)
                val gold = Color(0xFFFFC21A).copy(alpha = a)
                drawRect(gold, topLeft = Offset(0f, 0f), size = androidx.compose.ui.geometry.Size(w, h), style = Stroke(6.dp.toPx()))
                drawCircle(Brush.radialGradient(listOf(Color(0x55FFC21A), Color.Transparent), Offset(px, py), 70.dp.toPx()), 70.dp.toPx(), Offset(px, py))
            }
            if (overBurst >= 0f) {
                val k = (overBurst / 0.6f).coerceIn(0f, 1f)
                drawCircle(Color(0xFFFFE082).copy(alpha = 0.9f * (1f - k)), (30f + 300f * k).dp.toPx(), Offset(px, py), style = Stroke(8.dp.toPx() * (1f - k) + 1f))
            }
        }
        if (banner.isNotEmpty()) {
            Text(banner, color = bannerCol, fontSize = if (bannerBig) 15.sp else 11.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 76.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xDD080620)).border(1.5.dp, bannerCol.copy(alpha = 0.8f), RoundedCornerShape(12.dp)).padding(horizontal = 14.dp, vertical = 8.dp))
        }
        if (legend) {
            @Suppress("UNUSED_VARIABLE") val f = frame
            Row(Modifier.align(Alignment.BottomEnd).padding(end = 104.dp, bottom = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MoveButton("💨", "DASH", Color(0xFF80F0FF), 1f - LegendMoves.dashCd(), LegendMoves.dashing()) { tryDash() }
                MoveButton("🧲", "PULSE", Color(0xFFFF4DD2), 1f - LegendMoves.pulseCd(), false) { tryPulse() }
                val on = LegendMoves.overdriveOn()
                MoveButton("⚡", if (on) "ON" else "OVER", Color(0xFFFFC21A), if (on) LegendMoves.overLeft() else LegendMoves.meter, on, LegendMoves.meter >= 1f && !on) { tryOver() }
            }
        }
    }
}

@Composable
private fun MoveButton(icon: String, label: String, col: Color, fill: Float, activeNow: Boolean, ready: Boolean = fill >= 0.999f, onTap: () -> Unit) {
    val glow = activeNow || ready
    Box(
        Modifier.size(54.dp).clip(CircleShape).background(Color(0xCC080620))
            .border(2.dp, col.copy(alpha = if (glow) 1f else 0.35f), CircleShape)
            .pointerInput(Unit) { detectTapGestures(onPress = { onTap() }) },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize().padding(3.dp)) { drawArc(col.copy(alpha = if (glow) 0.95f else 0.5f), -90f, 360f * fill.coerceIn(0f, 1f), false, style = Stroke(5f, cap = StrokeCap.Round)) }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(icon, fontSize = 17.sp)
            Text(label, color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
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
