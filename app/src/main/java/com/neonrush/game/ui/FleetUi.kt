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
import com.neonrush.game.Roster
import com.neonrush.game.ShieldLayers
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private class FxDeath(val x: Float, val y: Float, var age: Float = 0f)
private class FxRing(val x: Float, val y: Float, val col: Color, var age: Float = 0f)
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
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val bossRef = rememberUpdatedState(bossActive)
    val yRef = rememberUpdatedState(pilotY)
    val bossYRef = rememberUpdatedState(bossY)
    val hitRef = rememberUpdatedState(onHit)
    val scoreRef = rememberUpdatedState(onScore)
    var sz by remember { mutableStateOf(IntSize.Zero) }
    var frame by remember { mutableStateOf(0) }
    val tm = rememberTextMeasurer()
    val deaths = remember { ArrayList<FxDeath>() }
    val floats = remember { ArrayList<FxFloat>() }
    val rings = remember { ArrayList<FxRing>() }
    var banner by remember { mutableStateOf("") }
    var bannerSub by remember { mutableStateOf("") }
    var bannerAge by remember { mutableStateOf(-1f) }
    var clock by remember { mutableStateOf(0f) }

    LaunchedEffect(active) {
        if (!active) { Fleet.visible = false; Fleet.dx = 0f; return@LaunchedEffect }
        var last = 0L
        val seenTypes = HashSet<Int>()
        var seenBuilt = ShieldLayers.builtSeq; var seenBreak = ShieldLayers.breakSeq; var seenBounce = ShieldLayers.bounceSeq
        var seenDeath = Fleet.deathSeq; var seenHit = Fleet.hitSeq; var seenContact = Fleet.contactSeq; var seenSpawn = Fleet.spawnSeq
        while (true) {
            val now = withFrameNanos { it }
            val dt = if (last == 0L) 0.016f else ((now - last) / 1_000_000_000f).coerceAtMost(0.05f)
            last = now; clock += dt
            val boss = bossRef.value
            Fleet.visible = boss
            if (boss) Fleet.step(dt, PilotDrift.x, yRef.value.toFloat()) else Fleet.dx = 0f
            if (!com.neonrush.game.Pause.frozen()) ShieldLayers.step(dt)
            val bxf = 0.88f + BossLunge.shift + Fleet.dx; val byp = bossYRef.value.toFloat()
            if (ShieldLayers.builtSeq != seenBuilt) {
                seenBuilt = ShieldLayers.builtSeq
                val t0 = ShieldLayers.main.firstOrNull()?.type
                if (t0 != null && seenTypes.add(t0)) { banner = "${ShieldLayers.NAMES[t0]} SHIELD"; bannerSub = ShieldLayers.TIPS[t0]; bannerAge = 0f; sfx.playAirLock() }
            }
            if (ShieldLayers.breakSeq != seenBreak) {
                seenBreak = ShieldLayers.breakSeq
                val bt = ShieldLayers.breakType
                rings.add(FxRing(bxf, byp, Color(ShieldLayers.COLS[bt]))); sfx.playSmash(); sfx.playAirBoom()
                banner = "${ShieldLayers.NAMES[bt]} LAYER DOWN"; bannerSub = if (ShieldLayers.up()) "${ShieldLayers.count()} LEFT · KEEP IT UP" else "THE HULL IS EXPOSED"; bannerAge = 0f
            }
            if (ShieldLayers.bounceSeq != seenBounce) { seenBounce = ShieldLayers.bounceSeq; floats.add(FxFloat(bxf - 0.04f, byp - 8f, "WEAK HIT", Color(ShieldLayers.COLS[ShieldLayers.hitType]))) }
            if (Fleet.spawnSeq != seenSpawn) {
                seenSpawn = Fleet.spawnSeq
                banner = "⚠ ${Fleet.total} BOSSES · ${Fleet.NAMES[Fleet.mech]}"; bannerSub = Fleet.TIPS[Fleet.mech]; bannerAge = 0f; sfx.playBossCharge()
                val fresh = Fleet.bodies.map { it.sp }.distinct().filter { Roster.markSeen(ctx, it) }
                if (fresh.isNotEmpty()) { val f = Roster.of(fresh[0]); banner = "NEW SPECIES LOGGED · ${f.name}"; bannerSub = f.quirk + "  (" + Roster.seenCount(ctx) + "/" + Roster.ALL.size + " IN CODEX)"; sfx.playAirLock() }
                else bannerSub = Fleet.bodies.joinToString(" · ") { Roster.of(it.sp).name } + "  —  " + Fleet.TIPS[Fleet.mech]
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
            val ri = rings.iterator(); while (ri.hasNext()) { val r = ri.next(); r.age += dt; if (r.age > 0.7f) ri.remove() }
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
            if (boss && ShieldLayers.up()) drawLayers(Offset((0.88f + BossLunge.shift + Fleet.dx) * w, bossY / 100f * h), h * 0.2f, clock)
            for (r in rings) { val k = (r.age / 0.7f).coerceIn(0f, 1f); val c = Offset(r.x * w, r.y / 100f * h)
                drawCircle(r.col.copy(alpha = 0.9f * (1f - k)), h * (0.22f + 0.25f * k), c, style = Stroke((7f * (1f - k) + 1f).dp.toPx()))
                for (q in 0 until 12) { val a = q * 0.5236f; val r0 = h * (0.22f + 0.2f * k); drawLine(r.col.copy(alpha = 1f - k), Offset(c.x + cos(a) * r0, c.y + sin(a) * r0), Offset(c.x + cos(a) * (r0 + h * 0.05f), c.y + sin(a) * (r0 + h * 0.05f)), 3.dp.toPx(), StrokeCap.Round) } }
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
            Row(Modifier.align(Alignment.TopEnd).padding(top = 134.dp, end = 8.dp).clip(RoundedCornerShape(8.dp)).background(Color(0x55080620)).padding(horizontal = 8.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("ESCORTS ", color = Color.White.copy(alpha = 0.7f), fontSize = 8.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
                for (b in Fleet.bodies) Text("●", color = if (b.alive) Color(Roster.of(b.sp).col) else Color.White.copy(alpha = 0.18f), fontSize = 9.sp)
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

/** Five hull silhouettes: 0 dart · 1 heavy wedge · 2 swept wings · 3 twin pods · 4 round saucer. */
private fun hullPath(kind: Int, c: Offset, s: Float): Path = Path().apply {
    when (kind) {
        1 -> { moveTo(c.x - s * 1.2f, c.y - s * 0.3f); lineTo(c.x + s * 0.2f, c.y - s * 0.95f); lineTo(c.x + s * 1.05f, c.y - s * 0.8f); lineTo(c.x + s * 1.05f, c.y + s * 0.8f); lineTo(c.x + s * 0.2f, c.y + s * 0.95f); lineTo(c.x - s * 1.2f, c.y + s * 0.3f); close() }
        2 -> { moveTo(c.x - s * 1.5f, c.y); lineTo(c.x + s * 0.1f, c.y - s * 0.25f); lineTo(c.x + s * 1.0f, c.y - s * 1.2f); lineTo(c.x + s * 0.7f, c.y); lineTo(c.x + s * 1.0f, c.y + s * 1.2f); lineTo(c.x + s * 0.1f, c.y + s * 0.25f); close() }
        3 -> { moveTo(c.x - s * 1.3f, c.y - s * 0.55f); lineTo(c.x + s * 0.9f, c.y - s * 0.75f); lineTo(c.x + s * 0.9f, c.y - s * 0.2f); lineTo(c.x + s * 0.3f, c.y); lineTo(c.x + s * 0.9f, c.y + s * 0.2f); lineTo(c.x + s * 0.9f, c.y + s * 0.75f); lineTo(c.x - s * 1.3f, c.y + s * 0.55f); lineTo(c.x - s * 0.7f, c.y); close() }
        4 -> { for (k in 0 until 10) { val a = k * 0.62832f; val rx = s * (if (k % 2 == 0) 1.15f else 0.95f); val x = c.x + cos(a) * rx; val y = c.y + sin(a) * rx * 0.85f; if (k == 0) moveTo(x, y) else lineTo(x, y) }; close() }
        else -> { moveTo(c.x - s * 1.35f, c.y); lineTo(c.x - s * 0.3f, c.y - s * 0.38f); lineTo(c.x + s * 0.5f, c.y - s * 1.0f); lineTo(c.x + s * 0.95f, c.y - s * 0.95f); lineTo(c.x + s * 0.65f, c.y - s * 0.2f); lineTo(c.x + s * 0.75f, c.y + s * 0.2f); lineTo(c.x + s * 0.95f, c.y + s * 0.95f); lineTo(c.x + s * 0.5f, c.y + s * 1.0f); lineTo(c.x - s * 0.3f, c.y + s * 0.38f); close() }
    }
}

private fun DrawScope.drawEscort(b: Body, w: Float, h: Float, t: Float) {
    val spc = Roster.of(b.sp)
    val col = Color(spc.col)
    val c = Offset(b.x * w, b.y / 100f * h)
    val warp = b.age.coerceIn(0.15f, 1f)
    val s = h * 0.085f * warp * (if (spc.tough >= 1.5f) 1.25f else if (spc.tough < 0.9f) 0.85f else 1f)
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
    val hull = hullPath(spc.hull, c, s)
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

/** The visible shield stack: one ring per layer, outermost largest. Each type looks different and thins out as it takes damage. */
private fun DrawScope.drawLayers(bc: Offset, R: Float, t: Float) {
    val live = ShieldLayers.main.filter { it.hp > 0f }
    val n = live.size
    val tau = 2f * PI.toFloat()
    for ((i, l) in live.withIndex()) {
        val r = R * (1.12f + 0.1f * (n - 1 - i))
        val col = Color(ShieldLayers.COLS[l.type])
        val f = (l.hp / l.max).coerceIn(0f, 1f)
        val fl = l.flash.coerceIn(0f, 1f)
        val tl = Offset(bc.x - r, bc.y - r); val sz = Size(r * 2f, r * 2f)
        when (l.type) {
            ShieldLayers.PLASMA -> {
                val fk = 0.75f + 0.25f * sin(t * 9f + i)
                drawCircle(col.copy(alpha = 0.05f + 0.07f * f), r, bc)
                drawCircle(col.copy(alpha = (0.3f + 0.55f * f) * fk), r, bc, style = Stroke((2f + 2f * f).dp.toPx()))
            }
            ShieldLayers.ARMOR -> {
                val plates = 10; val shown = kotlin.math.ceil(plates * f).toInt().coerceAtLeast(1)
                for (p in 0 until plates) {
                    if (p >= shown) continue
                    val a0 = p * 360f / plates + t * 8f
                    drawArc(col.copy(alpha = 0.85f), a0, 360f / plates - 6f, false, tl, sz, style = Stroke(8.dp.toPx(), cap = StrokeCap.Butt))
                    drawArc(Color.White.copy(alpha = 0.35f), a0 + 2f, 360f / plates - 10f, false, Offset(tl.x + 3.dp.toPx(), tl.y + 3.dp.toPx()), Size(sz.width - 6.dp.toPx(), sz.height - 6.dp.toPx()), style = Stroke(1.5.dp.toPx()))
                }
            }
            ShieldLayers.REFLECT -> {
                drawCircle(col.copy(alpha = 0.06f + 0.06f * f), r, bc)
                drawCircle(col.copy(alpha = 0.35f + 0.5f * f), r, bc, style = Stroke(3.dp.toPx()))
                for (g in 0 until 3) { val a = t * 1.6f + g * tau / 3f; val gx = bc.x + cos(a) * r; val gy = bc.y + sin(a) * r
                    drawLine(Color.White.copy(alpha = 0.9f), Offset(gx - 6.dp.toPx(), gy - 6.dp.toPx()), Offset(gx + 6.dp.toPx(), gy + 6.dp.toPx()), 2.dp.toPx(), StrokeCap.Round)
                    drawLine(Color.White.copy(alpha = 0.9f), Offset(gx - 6.dp.toPx(), gy + 6.dp.toPx()), Offset(gx + 6.dp.toPx(), gy - 6.dp.toPx()), 2.dp.toPx(), StrokeCap.Round) }
            }
            ShieldLayers.PHASE -> {
                val open = com.neonrush.game.BossLunge.vulnerable
                val fk = if (open) 0.25f else (0.4f + 0.5f * (0.5f + 0.5f * sin(t * 14f + i)))
                for (d in 0 until 16) { val a0 = d * 22.5f + t * 40f; drawArc(col.copy(alpha = fk * (0.5f + 0.5f * f)), a0, 11f, false, tl, sz, style = Stroke(4.dp.toPx(), cap = StrokeCap.Round)) }
            }
            else -> {
                val pk = 0.5f + 0.5f * sin(t * 5f)
                drawCircle(col.copy(alpha = 0.05f + 0.05f * pk), r, bc)
                drawCircle(col.copy(alpha = 0.3f + 0.5f * f), r, bc, style = Stroke(3.dp.toPx()))
                if (f < 1f) for (q in 0 until 4) { val a = t * 2f + q * tau / 4f; drawCircle(col, 2.5.dp.toPx(), Offset(bc.x + cos(a) * r, bc.y + sin(a) * r)) }
            }
        }
        if (fl > 0f) drawCircle(Color.White.copy(alpha = 0.7f * fl), r, bc, style = Stroke((6f * fl + 1f).dp.toPx()))
    }
}
