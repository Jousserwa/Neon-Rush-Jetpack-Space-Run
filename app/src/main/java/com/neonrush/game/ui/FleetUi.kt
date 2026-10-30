package com.neonrush.game.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neonrush.game.Body
import com.neonrush.game.BossLunge
import com.neonrush.game.Fleet
import com.neonrush.game.NeonSoundEngine
import com.neonrush.game.PilotDrift
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private class FxDeath(val x: Float, val y: Float, var age: Float = 0f)
private class FxFloat(val x: Float, val y: Float, val text: String, val col: Color, var age: Float = 0f)

private val ESCORT_COLS = longArrayOf(0xFF00E5FF, 0xFFFF4DD2, 0xFFFFC21A, 0xFF69F0AE, 0xFFFF7043)

/**
 * The escort bosses (Stage 149): drawn gunships that move in every direction (see Fleet), each with its own hull bar and shield flicker, hit sparks, a death blast,
 * a hunters' red dive lane, a ring around the main boss while its escorts live, and a little escort counter. Reads Fleet; hits come from the shooting overlay and the air strikes.
 */
@Composable
fun FleetOverlay(
    active: Boolean, bossActive: Boolean, bossY: Int, pilotY: Int,
    onHit: (Int, Boolean) -> Unit, onScore: (Int) -> Unit, modifier: Modifier = Modifier
) {
    val sfx = remember { NeonSoundEngine() }
    val bossRef = rememberUpdatedState(bossActive)
    val yRef = rememberUpdatedState(pilotY)
    val hitRef = rememberUpdatedState(onHit)
    val scoreRef = rememberUpdatedState(onScore)
    var sz by remember { mutableStateOf(IntSize.Zero) }
    var frame by remember { mutableStateOf(0) }
    val tm = rememberTextMeasurer()
    val deaths = remember { ArrayList<FxDeath>() }
    val floats = remember { ArrayList<FxFloat>() }
    var banner by remember { mutableStateOf("") }
    var bannerSub by remember { mutableStateOf("") }
    var bannerAge by remember { mutableStateOf(-1f) }
    var clock by remember { mutableStateOf(0f) }

    LaunchedEffect(active) {
        if (!active) { Fleet.visible = false; Fleet.dx = 0f; return@LaunchedEffect }
        var last = 0L
        var seenDeath = Fleet.deathSeq; var seenHit = Fleet.hitSeq; var seenContact = Fleet.contactSeq; var seenSpawn = Fleet.spawnSeq
        while (true) {
            val now = withFrameNanos { it }
            val dt = if (last == 0L) 0.016f else ((now - last) / 1_000_000_000f).coerceAtMost(0.05f)
            last = now; clock += dt
            val boss = bossRef.value
            Fleet.visible = boss
            if (boss) Fleet.step(dt, PilotDrift.x, yRef.value.toFloat()) else Fleet.dx = 0f
            if (Fleet.spawnSeq != seenSpawn) {
                seenSpawn = Fleet.spawnSeq
                banner = "⚠ ${Fleet.total} BOSSES · ${Fleet.NAMES[Fleet.mech]}"; bannerSub = Fleet.TIPS[Fleet.mech]; bannerAge = 0f; sfx.playBossCharge()
            }
            if (Fleet.deathSeq != seenDeath) {
                seenDeath = Fleet.deathSeq
                deaths.add(FxDeath(Fleet.lastDeadX, Fleet.lastDeadY)); sfx.playAirBoom(); scoreRef.value(150)
                val left = Fleet.alive()
                banner = if (left > 0) "ESCORT DESTROYED" else "ALL ESCORTS DOWN"; bannerSub = if (left > 0) "$left LEFT · THE MAIN BOSS IS STILL SHIELDED" else "THE MAIN BOSS IS EXPOSED · FINISH IT"; bannerAge = 0f
            }
            if (Fleet.hitSeq != seenHit) {
                seenHit = Fleet.hitSeq
                if (Fleet.hitAmt > 0f) { floats.add(FxFloat(Fleet.hitX, Fleet.hitY - 6f, "-${(Fleet.hitAmt * 100f).roundToInt().coerceAtLeast(1)}%", Color.White)); sfx.playSmash() }
                else { floats.add(FxFloat(Fleet.hitX, Fleet.hitY - 6f, "BLOCKED", Color(0xFF80D8FF))); sfx.playDeflect() }
            }
            if (Fleet.contactSeq != seenContact) {
                seenContact = Fleet.contactSeq
                hitRef.value(12, false); banner = "💥 RAMMED!"; bannerSub = "DODGE THE RED LINE"; bannerAge = 0f
            }
            val di = deaths.iterator(); while (di.hasNext()) { val d = di.next(); d.age += dt; if (d.age > 0.9f) di.remove() }
            val fi = floats.iterator(); while (fi.hasNext()) { val f = fi.next(); f.age += dt; if (f.age > 0.8f) fi.remove() }
            if (bannerAge >= 0f) { bannerAge += dt; if (bannerAge > 2.6f) bannerAge = -1f }
            frame++
        }
    }

    Box(modifier.fillMaxSize().onSizeChanged { sz = it }) {
        Canvas(Modifier.fillMaxSize()) {
            @Suppress("UNUSED_VARIABLE") val f = frame
            val w = size.width; val h = size.height
            if (w <= 0f || h <= 0f) return@Canvas
            val boss = bossRef.value
            // ring around the main boss while its escorts live
            if (boss && Fleet.alive() > 0) {
                val bc = Offset((0.88f + BossLunge.shift + Fleet.dx) * w, bossY / 100f * h); val r = h * 0.2f
                val pulse = 0.5f + 0.5f * sin(clock * 5f)
                drawCircle(Color(0xFF80D8FF).copy(alpha = 0.07f + 0.05f * pulse), r, bc)
                drawCircle(Color(0xFF80D8FF).copy(alpha = 0.5f + 0.3f * pulse), r, bc, style = Stroke(3.dp.toPx()))
                rotate(clock * 40f, bc) { for (k in 0 until 6) { val a = k * PI.toFloat() / 3f; drawLine(Color(0xFFB3E5FC).copy(alpha = 0.7f), Offset(bc.x + cos(a) * r * 0.92f, bc.y + sin(a) * r * 0.92f), Offset(bc.x + cos(a) * r * 1.06f, bc.y + sin(a) * r * 1.06f), 3.dp.toPx(), StrokeCap.Round) } }
            }
            if (boss) for (b in Fleet.bodies) if (b.alive) drawEscort(b, w, h, clock)
            for (d in deaths) {
                val k = (d.age / 0.9f).coerceIn(0f, 1f); val c = Offset(d.x * w, d.y / 100f * h)
                drawCircle(Color.White.copy(alpha = 0.8f * (1f - k)), h * 0.05f * (1f - k), c)
                for (r in 0..2) { val kk = (k * 1.2f - r * 0.15f).coerceIn(0f, 1f); drawCircle(Color(0xFFFFB74D).copy(alpha = 0.9f * (1f - kk)), h * (0.04f + 0.3f * kk), c, style = Stroke((6f * (1f - kk) + 1f).dp.toPx())) }
                for (r in 0 until 14) { val a = r * 0.449f; val s0 = h * 0.28f * k; drawLine(Color(0xFFFFE082).copy(alpha = 1f - k), Offset(c.x + cos(a) * s0, c.y + sin(a) * s0), Offset(c.x + cos(a) * (s0 + h * 0.05f), c.y + sin(a) * (s0 + h * 0.05f)), 2.5.dp.toPx(), StrokeCap.Round) }
            }
            for (fl in floats) {
                val k = (fl.age / 0.8f).coerceIn(0f, 1f)
                val tx = tm.measure(fl.text, TextStyle(color = fl.col.copy(alpha = 1f - k * k), fontSize = 13.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace))
                drawText(tx, topLeft = Offset(fl.x * w - tx.size.width / 2f, fl.y / 100f * h - k * 40.dp.toPx()))
            }
        }
        if (Fleet.visible && Fleet.total > 1 && bossRef.value) {
            Row(Modifier.align(Alignment.TopEnd).padding(top = 76.dp, end = 8.dp).clip(RoundedCornerShape(8.dp)).background(Color(0x55080620)).padding(horizontal = 8.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("ESCORTS ", color = Color.White.copy(alpha = 0.7f), fontSize = 8.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
                for (b in Fleet.bodies) Text("●", color = if (b.alive) Color(ESCORT_COLS[b.idx % ESCORT_COLS.size]) else Color.White.copy(alpha = 0.18f), fontSize = 9.sp)
            }
        }
        if (bannerAge >= 0f) {
            val a = (if (bannerAge < 0.2f) bannerAge / 0.2f else 1f - ((bannerAge - 1.4f) / 1.2f).coerceIn(0f, 1f)) * 0.9f
            Column(Modifier.align(Alignment.TopCenter).padding(top = 132.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(banner, color = Color(0xFFFFD54F).copy(alpha = a), fontSize = 14.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
                Text(bannerSub, color = Color.White.copy(alpha = a * 0.8f), fontSize = 9.sp, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

private fun DrawScope.drawEscort(b: Body, w: Float, h: Float, t: Float) {
    val col = Color(ESCORT_COLS[b.idx % ESCORT_COLS.size])
    val c = Offset(b.x * w, b.y / 100f * h)
    val warp = b.age.coerceIn(0.15f, 1f)
    val s = h * 0.085f * warp
    // dive lane (hunters)
    if (b.tel > 0f) {
        val tp = Offset(b.tx * w, b.ty / 100f * h); val fl = 0.5f + 0.5f * sin(t * 30f)
        drawLine(Color(0xFFFF1744).copy(alpha = (0.35f + 0.4f * fl) * b.tel), c, tp, 5.dp.toPx() * b.tel, StrokeCap.Round)
        drawCircle(Color(0xFFFF1744).copy(alpha = 0.8f * b.tel), 26.dp.toPx() * (1f - 0.6f * b.tel), tp, style = Stroke(2.dp.toPx()))
        drawCircle(Color(0xFFFF1744).copy(alpha = 0.25f * fl * b.tel), s * 1.8f, c)
    }
    // glow + engine flame
    drawCircle(Brush.radialGradient(listOf(col.copy(alpha = 0.35f), Color.Transparent), c, s * 2.4f), s * 2.4f, c)
    val fl = 0.7f + 0.3f * sin(t * 40f + b.idx)
    val fp = Path().apply { moveTo(c.x + s * 0.7f, c.y - s * 0.22f); lineTo(c.x + s * (1.5f + 0.6f * fl), c.y); lineTo(c.x + s * 0.7f, c.y + s * 0.22f); close() }
    drawPath(fp, Brush.horizontalGradient(listOf(Color(0xFFFFF59D), Color(0xFFFF6F00).copy(alpha = 0.0f)), c.x + s * 0.7f, c.x + s * 2.1f))
    // hull
    val hull = Path().apply {
        moveTo(c.x - s * 1.35f, c.y)
        lineTo(c.x - s * 0.3f, c.y - s * 0.38f); lineTo(c.x + s * 0.5f, c.y - s * 1.0f); lineTo(c.x + s * 0.95f, c.y - s * 0.95f)
        lineTo(c.x + s * 0.65f, c.y - s * 0.2f); lineTo(c.x + s * 0.75f, c.y + s * 0.2f)
        lineTo(c.x + s * 0.95f, c.y + s * 0.95f); lineTo(c.x + s * 0.5f, c.y + s * 1.0f); lineTo(c.x - s * 0.3f, c.y + s * 0.38f); close()
    }
    drawPath(hull, Brush.verticalGradient(listOf(col.copy(alpha = 0.85f), Color(0xFF14102E), col.copy(alpha = 0.7f)), c.y - s, c.y + s))
    drawPath(hull, col, style = Stroke(2.dp.toPx()))
    drawCircle(Color.White.copy(alpha = 0.9f), s * 0.22f, Offset(c.x - s * 0.35f, c.y))
    drawCircle(col.copy(alpha = 0.8f), s * 0.34f, Offset(c.x - s * 0.35f, c.y), style = Stroke(1.5.dp.toPx()))
    drawLine(Color(0xFFB0BEC5), Offset(c.x - s * 0.9f, c.y - s * 0.16f), Offset(c.x - s * 1.5f, c.y - s * 0.16f), 3.dp.toPx(), StrokeCap.Round)
    drawLine(Color(0xFFB0BEC5), Offset(c.x - s * 0.9f, c.y + s * 0.16f), Offset(c.x - s * 1.5f, c.y + s * 0.16f), 3.dp.toPx(), StrokeCap.Round)
    // damage: smoke and sparks
    if (b.hp < 0.55f) { for (k in 0 until 3) { val p = ((t * 1.3f + k * 0.33f) % 1f); drawCircle(Color(0xFF455A64).copy(alpha = 0.5f * (1f - p)), s * (0.15f + 0.3f * p), Offset(c.x + s * (0.3f + 0.9f * p), c.y - s * (0.2f + 0.8f * p))) } }
    if (b.hp < 0.3f && ((t * 12f).toInt() + b.idx) % 3 == 0) drawCircle(Color(0xFFFFD54F), s * 0.1f, Offset(c.x + sin(t * 20f) * s * 0.6f, c.y + cos(t * 17f) * s * 0.5f))
    // shield flicker
    if (b.shield) {
        drawCircle(Color(0xFF80D8FF).copy(alpha = 0.18f), s * 1.6f, c)
        drawCircle(Color(0xFF80D8FF).copy(alpha = 0.85f), s * 1.6f, c, style = Stroke(2.5.dp.toPx()))
    }
    if (b.flash > 0f) drawPath(hull, Color.White.copy(alpha = (b.flash * 2f).coerceAtMost(0.8f)))
    // hull bar
    val bw = s * 2.6f; val bx = c.x - bw / 2f + s * 0.1f; val by = c.y - s * 1.55f
    drawRect(Color.Black.copy(alpha = 0.6f), Offset(bx, by), Size(bw, 4.dp.toPx()))
    val hp = b.hp.coerceIn(0f, 1f)
    drawRect(if (hp > 0.5f) Color(0xFF69F0AE) else if (hp > 0.25f) Color(0xFFFFD54F) else Color(0xFFFF5252), Offset(bx, by), Size(bw * hp, 4.dp.toPx()))
}
