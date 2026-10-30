package com.neonrush.game.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neonrush.game.BossDamageFx
import com.neonrush.game.BossLunge
import com.neonrush.game.BossReturns
import com.neonrush.game.NeonSoundEngine
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

private class DPart(var x: Float, var y: Float, var vx: Float, var vy: Float, val life: Float, val col: Color, var size: Float, val grow: Float = 0f, var age: Float = 0f)
private class DFloat(val x: Float, var y: Float, val text: String, val col: Color, val sp: Float, var age: Float = 0f)
private class DBlast(val x: Float, val y: Float, val at: Float, val r: Float, var done: Boolean = false)
private class DRing(val x: Float, val y: Float, val col: Color, val maxR: Float, var age: Float = 0f)
private class DChunk(var x: Float, var y: Float, var vx: Float, var vy: Float, var rot: Float, val vr: Float, val size: Float, val col: Color, var age: Float = 0f)

/**
 * BOSS DAMAGE (Stage 146): makes every hit readable. The more the boss is hurt the worse it looks: hull cracks, sparks, smoke, flames, electric arcs, red alarm.
 * Each hit pops a damage number and a ring on the boss; a shield bounce shows BLOCKED. A bar overlay keeps a pale "chip" of the last damage and marks 25/50/75%.
 * When the boss is destroyed it blows up in a chain of blasts with flying debris, and the banner shows how many returns that cost it (8 to 6, 6 to 4, ...).
 * A boss that retreats alive is announced with its remaining hull, and comes back still wounded.
 * Coordinates match the boss drawing in NeonRushApp (x = 0.88 + lunge shift, height 0.38 of the screen, health bar at 10%).
 */
@Composable
fun BossDamageOverlay(
    active: Boolean, bossActive: Boolean, bossHealth: Float, bossY: Int, zoneNumber: Int, tick: Int, modifier: Modifier = Modifier
) {
    val dens = LocalDensity.current.density
    val tm = rememberTextMeasurer()
    val sfx = remember { NeonSoundEngine() }
    val bossRef = rememberUpdatedState(bossActive)
    val hpRef = rememberUpdatedState(bossHealth)
    val yRef = rememberUpdatedState(bossY)
    val tickRef = rememberUpdatedState(tick)
    var sz by remember { mutableStateOf(IntSize.Zero) }
    var frame by remember { mutableStateOf(0) }
    var clock by remember { mutableStateOf(0f) }
    var trail by remember { mutableStateOf(1f) }
    val parts = remember { ArrayList<DPart>() }
    val floats = remember { ArrayList<DFloat>() }
    val blasts = remember { ArrayList<DBlast>() }
    val rings = remember { ArrayList<DRing>() }
    val chunks = remember { ArrayList<DChunk>() }
    var banner by remember { mutableStateOf("") }
    var bannerSub by remember { mutableStateOf("") }
    var bannerAge by remember { mutableStateOf(-1f) }
    var bannerGold by remember { mutableStateOf(false) }
    var deathT by remember { mutableStateOf(-1f) }
    var flash by remember { mutableStateOf(0f) }
    var cutPop by remember { mutableStateOf(-1f) }

    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        var last = 0L; var wasBoss = false; var lastHp = 1f; var seenHit = BossDamageFx.hitSeq; var seenBlock = BossDamageFx.blockSeq; var seenCut = BossReturns.cutSeq; var killAt = -10f
        fun rnd(a: Float, b: Float) = a + Random.nextFloat() * (b - a)
        while (true) {
            val now = withFrameNanos { it }
            val dt = if (last == 0L) 0.016f else ((now - last) / 1_000_000_000f).coerceAtMost(0.05f)
            last = now; clock += dt
            val w = sz.width.toFloat(); val h = sz.height.toFloat()
            if (w <= 0f || h <= 0f) { frame++; continue }
            val boss = bossRef.value; val hp = hpRef.value.coerceIn(0f, 1f)
            val bx = (0.88f + BossLunge.shift) * w; val by = yRef.value / 100f * h; val R = h * 0.19f
            // chip damage bar follows slowly
            if (trail < hp) trail = hp else trail = (trail - dt * 0.35f).coerceAtLeast(hp)
            // ---- destroyed (the sim says so through BossReturns.cutSeq), appear, retreat ----
            if (BossReturns.cutSeq != seenCut) {
                seenCut = BossReturns.cutSeq; cutPop = 0f; killAt = clock
                banner = "BOSS DESTROYED"; bannerSub = if (BossReturns.lastAfter == 0) "NO MORE RETURNS IN THIS ZONE" else "RETURNS  ${BossReturns.lastBefore}  →  ${BossReturns.lastAfter}"; bannerAge = 0f; bannerGold = true
                deathT = 0f; flash = 0.6f; blasts.clear()
                for (i in 0 until 8) blasts.add(BossBlast(bx, by, R, i))
                for (i in 0 until 16) { val a = rnd(0f, 2f * PI.toFloat()); val sp = rnd(80f, 360f) * dens; chunks.add(DChunk(bx + rnd(-0.4f, 0.4f) * R, by + rnd(-0.3f, 0.3f) * R, cos(a) * sp, sin(a) * sp - 60f * dens, rnd(0f, 6f), rnd(-8f, 8f), rnd(6f, 16f) * dens, if (i % 3 == 0) Color(0xFFFFB74D) else Color(0xFF37474F))) }
            }
            if (boss && !wasBoss && clock - killAt > 1.5f && lastHp < 0.97f && lastHp > 0.02f) { banner = "BOSS RETURNS WOUNDED"; bannerSub = "HULL ${(hp * 100).roundToInt()}%"; bannerAge = 0f; bannerGold = false }
            if (!boss && wasBoss && clock - killAt > 1.5f) { banner = "BOSS RETREATS"; bannerSub = "HULL ${(lastHp * 100).roundToInt()}% · it will be back wounded"; bannerAge = 0f; bannerGold = false }
            if (boss) lastHp = hp
            wasBoss = boss
            // ---- hits ----
            if (BossDamageFx.hitSeq != seenHit) {
                seenHit = BossDamageFx.hitSeq
                val big = BossDamageFx.big; val pct = (BossDamageFx.amt * 100f).roundToInt().coerceAtLeast(1)
                val hx = bx - R * rnd(0.1f, 0.5f); val hy = by + rnd(-0.5f, 0.4f) * R
                floats.add(DFloat(hx, hy - R * 0.2f, "-$pct%", if (big) Color(0xFFFFD54F) else Color.White, if (big) 22f else 15f))
                rings.add(DRing(hx, hy, if (big) Color(0xFFFFB74D) else Color(0xFF80DEEA), (if (big) 80f else 34f) * dens))
                for (i in 0 until (if (big) 14 else 6)) { val a = rnd(0f, 2f * PI.toFloat()); val sp = rnd(40f, 220f) * dens; parts.add(DPart(hx, hy, cos(a) * sp, sin(a) * sp, 0.4f, if (i % 2 == 0) Color.White else Color(0xFFFFB74D), rnd(1.5f, 3.5f) * dens)) }
                if (big) flash = maxOf(flash, 0.25f)
            }
            if (BossDamageFx.blockSeq != seenBlock) {
                seenBlock = BossDamageFx.blockSeq
                val hx = bx - R * 0.45f; val hy = by + rnd(-0.4f, 0.4f) * R
                floats.add(DFloat(hx, hy, "BLOCKED", Color(0xFF80D8FF), 12f)); rings.add(DRing(hx, hy, Color(0xFF80D8FF), 40f * dens))
            }
            // ---- ongoing wounds ----
            if (boss && hp > 0.001f) {
                val bodyX = { bx + rnd(-0.75f, 0.75f) * R * 0.9f }; val bodyY = { by + rnd(-0.6f, 0.6f) * R }
                if (hp < 0.75f && Random.nextFloat() < (0.75f - hp) * 0.5f) parts.add(DPart(bodyX(), bodyY(), rnd(-60f, 60f) * dens, rnd(-90f, -10f) * dens, 0.35f, Color(0xFFFFF59D), rnd(1.2f, 2.6f) * dens))
                if (hp < 0.6f && Random.nextFloat() < (0.6f - hp) * 0.9f) parts.add(DPart(bodyX(), bodyY(), rnd(-70f, -20f) * dens, rnd(-50f, -15f) * dens, rnd(0.9f, 1.5f), Color(0xFF455A64), rnd(8f, 14f) * dens, 14f * dens))
                if (hp < 0.4f && Random.nextFloat() < (0.4f - hp) * 1.6f) parts.add(DPart(bodyX(), bodyY(), rnd(-30f, 10f) * dens, rnd(-110f, -50f) * dens, rnd(0.35f, 0.6f), if (Random.nextBoolean()) Color(0xFFFF7A18) else Color(0xFFFFD54F), rnd(5f, 10f) * dens, -6f * dens))
            }
            if (deathT >= 0f) {
                deathT += dt
                for (b in blasts) if (!b.done && deathT >= b.at) {
                    b.done = true; rings.add(DRing(b.x, b.y, Color(0xFFFFB74D), b.r * 0.9f))
                    for (i in 0 until 14) { val a = rnd(0f, 2f * PI.toFloat()); val sp = rnd(60f, 300f) * dens; parts.add(DPart(b.x, b.y, cos(a) * sp, sin(a) * sp, rnd(0.5f, 0.9f), if (i % 3 == 0) Color.White else if (i % 3 == 1) Color(0xFFFFB74D) else Color(0xFFFF5722), rnd(3f, 7f) * dens)) }
                    parts.add(DPart(b.x, b.y, 0f, -30f * dens, 1.2f, Color(0xFF263238), 22f * dens, 26f * dens))
                    flash = maxOf(flash, 0.3f); if (b.at > 1.0f) sfx.playAirBoom()
                }
                if (deathT > 1.9f) deathT = -1f
            }
            // ---- advance everything ----
            val ip = parts.iterator(); while (ip.hasNext()) { val p = ip.next(); p.age += dt; p.x += p.vx * dt; p.y += p.vy * dt; p.size += p.grow * dt; if (p.age > p.life) ip.remove() }
            val ifl = floats.iterator(); while (ifl.hasNext()) { val f = ifl.next(); f.age += dt; f.y -= 46f * dens * dt; if (f.age > 0.9f) ifl.remove() }
            val ir = rings.iterator(); while (ir.hasNext()) { val r = ir.next(); r.age += dt; if (r.age > 0.5f) ir.remove() }
            val ic = chunks.iterator(); while (ic.hasNext()) { val c = ic.next(); c.age += dt; c.x += c.vx * dt; c.y += c.vy * dt; c.vy += 420f * dens * dt; c.rot += c.vr * dt; if (c.age > 1.7f) ic.remove() }
            if (flash > 0f) flash = (flash - dt * 2.2f).coerceAtLeast(0f)
            if (bannerAge >= 0f) { bannerAge += dt; if (bannerAge > 3.0f) bannerAge = -1f }
            if (cutPop >= 0f) { cutPop += dt; if (cutPop > 2.4f) cutPop = -1f }
            frame++
        }
    }

    Box(modifier.fillMaxSize().onSizeChanged { sz = it }) {
        Canvas(Modifier.fillMaxSize()) {
            frame.let { }
            val w = size.width; val h = size.height
            val hp = bossHealth.coerceIn(0f, 1f)
            val bx = (0.88f + BossLunge.shift) * w; val by = bossY / 100f * h; val R = h * 0.19f
            if (bossActive && hp > 0.001f) {
                val sev = (1f - hp).coerceIn(0f, 1f)
                // red alarm when nearly dead
                if (hp < 0.25f) drawCircle(Color(0xFFFF1744).copy(alpha = (0.10f + 0.12f * (0.5f + 0.5f * sin(clock * 12f))) * (1f - hp * 3f)), R * 1.1f, Offset(bx, by))
                // hull cracks: more of them as it takes damage
                val n = ((0.85f - hp) / 0.85f * 8f).toInt().coerceIn(0, 8)
                for (i in 0 until n) {
                    val a0 = i * 2.4f + 0.7f; val p = Path(); var cx = bx + cos(a0) * R * 0.1f; var cy = by + sin(a0) * R * 0.1f; p.moveTo(cx, cy)
                    for (j in 1..5) { val a = a0 + sin(i * 7f + j * 3f) * 0.7f; cx += cos(a) * R * 0.16f; cy += sin(a) * R * 0.13f; p.lineTo(cx, cy) }
                    drawPath(p, Color.Black.copy(alpha = 0.55f), style = Stroke(2.6f * dens))
                    drawPath(p, Color(0xFFFF8A00).copy(alpha = (0.35f + 0.6f * sev) * (0.75f + 0.25f * sin(clock * 5f + i))), style = Stroke(1.1f * dens))
                }
                // electric arcs
                if (hp < 0.35f && Random.nextFloat() < 0.35f) {
                    val p = Path(); var cx = bx + (Random.nextFloat() - 0.5f) * R * 1.2f; var cy = by + (Random.nextFloat() - 0.5f) * R * 0.9f; p.moveTo(cx, cy)
                    for (j in 0 until 5) { cx += (Random.nextFloat() - 0.5f) * R * 0.35f; cy += (Random.nextFloat() - 0.5f) * R * 0.3f; p.lineTo(cx, cy) }
                    drawPath(p, Color(0xFFB3E5FC).copy(alpha = 0.9f), style = Stroke(1.8f * dens))
                }
                // sputter: the failing boss flickers dark
                if (hp < 0.3f && Random.nextFloat() < 0.18f) drawCircle(Color.Black.copy(alpha = 0.35f), R * (0.5f + Random.nextFloat() * 0.5f), Offset(bx + (Random.nextFloat() - 0.5f) * R * 0.6f, by))
            }
            for (p in parts) { val k = 1f - p.age / p.life; drawCircle(p.col.copy(alpha = (if (p.grow > 0f) 0.45f * k else k).coerceIn(0f, 1f)), p.size.coerceAtLeast(0.5f), Offset(p.x, p.y)) }
            for (r in rings) { val k = r.age / 0.5f; drawCircle(r.col.copy(alpha = 1f - k), r.maxR * (0.2f + 0.8f * k), Offset(r.x, r.y), style = Stroke((3f * (1f - k) + 1f) * dens)) }
            for (c in chunks) { val k = 1f - c.age / 1.7f; val p = Path(); for (v in 0..2) { val a = c.rot + v * 2.1f; val rr = c.size * (if (v == 0) 1f else 0.6f); if (v == 0) p.moveTo(c.x + cos(a) * rr, c.y + sin(a) * rr) else p.lineTo(c.x + cos(a) * rr, c.y + sin(a) * rr) }; p.close(); drawPath(p, c.col.copy(alpha = k.coerceIn(0f, 1f))) }
            for (f in floats) {
                val k = (f.age / 0.9f).coerceIn(0f, 1f)
                val txt = tm.measure(f.text, TextStyle(color = f.col.copy(alpha = 1f - k * k), fontSize = (f.sp * (1.4f - 0.4f * minOf(1f, f.age * 8f))).sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace))
                drawText(txt, topLeft = Offset(f.x - txt.size.width / 2f, f.y))
            }
            if (flash > 0f) drawRect(Color.White.copy(alpha = flash * 0.4f))
            // ---- health bar overlay (same place as the game's bar): chip damage, 25/50/75 marks, glowing edge ----
            if (bossActive) {
                val bw = w * 0.6f; val bh = h * 0.022f; val bl = (w - bw) / 2f; val bt = h * 0.1f
                if (trail > hp) drawRect(Color(0xFFFFCDD2).copy(alpha = 0.8f), Offset(bl + bw * hp, bt), Size(bw * (trail - hp), bh))
                for (q in 1..3) drawLine(Color.Black.copy(alpha = 0.55f), Offset(bl + bw * q / 4f, bt), Offset(bl + bw * q / 4f, bt + bh), 1.5f * dens)
                drawRect(Color.White.copy(alpha = 0.9f), Offset(bl + bw * hp - 1.5f * dens, bt), Size(3f * dens, bh))
                val pct = tm.measure("${(hp * 100).roundToInt()}%", TextStyle(color = Color.White.copy(alpha = 0.9f), fontSize = 9.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace))
                drawText(pct, topLeft = Offset(bl + bw + 6f * dens, bt + bh / 2f - pct.size.height / 2f))
            }
        }
        // ---- returns counter ----
        if (zoneNumber % 5 == 0 && (bossActive || BossReturns.zone == zoneNumber)) {
            val left = BossReturns.remaining()
            Row(Modifier.align(Alignment.TopCenter).padding(top = 76.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp)).background(Color(0x55080620)).padding(horizontal = 8.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("BOSS RETURNS ", color = Color.White.copy(alpha = 0.7f), fontSize = 8.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
                for (i in 0 until BossReturns.base()) Text("●", color = if (i < left) Color(0xFFFF5252).copy(alpha = 0.9f) else Color.White.copy(alpha = 0.18f), fontSize = 8.sp)
                if (cutPop >= 0f) Text("  -${BossReturns.CUT}", color = Color(0xFFFFD54F).copy(alpha = (1f - cutPop / 2.4f).coerceIn(0f, 1f)), fontSize = (11f + 5f * (1f - minOf(1f, cutPop * 3f))).sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
            }
        }
        // ---- banner (top, faint, quick) ----
        if (bannerAge >= 0f) {
            val a = (if (bannerAge < 0.2f) bannerAge / 0.2f else 1f - ((bannerAge - 1.2f) / 1.8f).coerceIn(0f, 1f)) * 0.9f
            androidx.compose.foundation.layout.Column(Modifier.align(Alignment.TopCenter).padding(top = 96.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(banner, color = (if (bannerGold) Color(0xFFFFD54F) else Color(0xFF80DEEA)).copy(alpha = a), fontSize = if (bannerGold) 17.sp else 12.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
                Text(bannerSub, color = Color.White.copy(alpha = a * 0.85f), fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

private fun BossBlast(bx: Float, by: Float, R: Float, i: Int): DBlast {
    val a = i * 2.4f
    return DBlast(bx + cos(a) * R * 0.55f * (0.3f + (i % 3) * 0.35f), by + sin(a) * R * 0.45f, i * 0.16f, R * (if (i == 7) 1.2f else 0.5f))
}
