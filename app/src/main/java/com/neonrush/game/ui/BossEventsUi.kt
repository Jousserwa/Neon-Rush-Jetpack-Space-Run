package com.neonrush.game.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neonrush.game.Baby
import com.neonrush.game.BossGuard
import com.neonrush.game.BroodBomb
import com.neonrush.game.Shooting
import com.neonrush.game.Upgrades
import com.neonrush.game.BossBrood
import com.neonrush.game.BossLunge
import com.neonrush.game.Fleet
import com.neonrush.game.NeonSoundEngine
import com.neonrush.game.PilotDrift
import com.neonrush.game.RiskGates
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

private class Puff(val x: Float, val y: Float, var age: Float = 0f)
private class Arc(val x0: Float, val y0: Float, val x1: Float, val y1: Float, var age: Float = 0f)

/**
 * BOSS SURPRISES, once in a while:
 *  - LUNGE (Legendary, ~70% of bosses): the boss telegraphs, then charges across the track at the pilot. Dodge it; while it lunges its shield is down and bolts do x2.
 *  - BROOD (Legendary ~80% of bosses with 5 babies, Hard ~50% with 3): the boss births little hunters that chase the pilot.
 *    A basic bolt kills one; an ARMOR round starts a chain blast that destroys them all (ShootingOverlay sets BossBrood.novaRequested).
 *    Wipe the whole brood yourself for a bonus (+1 gem through the shared per-run cap).
 * Hits go through [onHit] (shield layer first, then fuel).
 */
@Composable
fun BossEventsOverlay(
    active: Boolean, tierName: String, bossActive: Boolean, bossHealth: Float, bossY: Int, pilotY: Int, accent: Color,
    onHit: (Int, Boolean) -> Unit, onScore: (Int) -> Unit, onBossDamage: () -> Unit = {},
    onBombSweep: () -> List<Offset> = { emptyList() }, onBombBoss: (Float) -> Unit = {}, modifier: Modifier = Modifier
) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val legend = tierName == "LEGENDARY"; val hard = tierName == "HARD"
    if (!legend && !hard) return
    val dens = LocalDensity.current.density
    val sfx = remember { NeonSoundEngine() }
    val bossRef = rememberUpdatedState(bossActive)
    val hpRef = rememberUpdatedState(bossHealth)
    val bossYRef = rememberUpdatedState(bossY)
    val yRef = rememberUpdatedState(pilotY)
    val hitRef = rememberUpdatedState(onHit)
    val scoreRef = rememberUpdatedState(onScore)
    val bossDmgRef = rememberUpdatedState(onBossDamage)
    val sweepRef = rememberUpdatedState(onBombSweep)
    val bombBossRef = rememberUpdatedState(onBombBoss)
    var bombPending by remember { mutableStateOf(false) }
    var bombAge by remember { mutableStateOf(-1f) }
    val artBomb = if (legend) rememberArt("bomb_gold") else rememberArt("bomb_pink")
    val bombCount = BroodBomb.count(ctx)
    var hasBabies by remember { mutableStateOf(false) }
    val showBomb = bombCount > 0 && active
    var sz by remember { mutableStateOf(IntSize.Zero) }
    var frame by remember { mutableStateOf(0) }
    var banner by remember { mutableStateOf("") }
    var bannerCol by remember { mutableStateOf(Color.White) }
    var telegraph by remember { mutableStateOf(0f) }     // 0..1 while the boss winds up
    val puffs = remember { ArrayList<Puff>() }
    val arcs = remember { ArrayList<Arc>() }
    var novaAge by remember { mutableStateOf(-1f) }
    var novaPos by remember { mutableStateOf(Offset.Zero) }
    val body = accent
    fun detonate() {
        if (bombAge >= 0f || !BroodBomb.use(ctx)) return
        bombAge = 0f; bombPending = true      // the shell flies for 0.38 s, then the blast does its work (see the frame loop)
        sfx.playAirMissile()
        if (BroodBomb.count(ctx) > 0) sfx.playBombReload() else sfx.playBombEmpty()   // the next bomb loads in (or the rack runs empty)
    }

    LaunchedEffect(active, tierName) {
        Upgrades.refresh(ctx)
        BossBrood.reset(); BossLunge.reset(); puffs.clear(); arcs.clear(); banner = ""; telegraph = 0f; novaAge = -1f
        if (!active) return@LaunchedEffect
        fun rnd(a: Float, b: Float) = a + Random.nextFloat() * (b - a)
        var wasBoss = false; var rollLunge = false; var broodN = 0; var lungeDone = false; var broodDone = false
        var phase = 0; var pt = 0f; var target = 0f; var queue = 0; var spawnT = 0f
        var spawned = 0; var kills = 0; var bannerLeft = 0f; var hitCd = 0f; var last = 0L
        fun say(s: String, c: Color, secs: Float = 2.2f) { banner = s; bannerCol = c; bannerLeft = secs }
        while (true) {
            val now = withFrameNanos { it }
            val dt = if (last == 0L) 0.016f else ((now - last) / 1_000_000_000f).coerceAtMost(0.05f)
            last = now
            val w = sz.width.toFloat(); val h = sz.height.toFloat()
            if (w <= 0f || h <= 0f) { frame++; continue }
            if (bannerLeft > 0f) { bannerLeft -= dt; if (bannerLeft <= 0f) banner = "" }
            if (hitCd > 0f) hitCd -= dt
            val boss = bossRef.value; val hp = hpRef.value
            val pxN = PilotDrift.x; val pyN = yRef.value.toFloat()
            fun near(ex: Float, ey: Float, r: Float) = hypot(ex * w - pxN * w, ey / 100f * h - pyN / 100f * h) < r * dens
            fun hurt(loss: Int, why: String) { if (hitCd > 0f) return; hitCd = 1.2f; hitRef.value(loss, false); say(why, Color(0xFFFF5252), 1.6f) }
            if (boss && !wasBoss) {   // a new boss: decide which surprises it has
                rollLunge = (legend && Random.nextFloat() < 0.7f) || (hard && com.neonrush.game.Siege.zone >= 50 && Random.nextFloat() < (if (com.neonrush.game.Siege.zone >= 100) 0.8f else 0.5f))
                broodN = if (legend) (if (Random.nextFloat() < 0.8f) 5 else 0) else (if (Random.nextFloat() < 0.5f) 3 else 0)
                lungeDone = false; broodDone = false; phase = 0
            }
            if (!boss && wasBoss && phase != 0) { phase = 0; BossLunge.reset(); telegraph = 0f }
            wasBoss = boss
            // ---------------- lunge ----------------
            if (boss && rollLunge && !lungeDone && phase == 0 && queue == 0 && hp < 0.88f && hp > 0.3f) {
                phase = 1; pt = 0f; BossLunge.vulnerable = true; say("⚠ BOSS CHARGING - DODGE!", Color(0xFFFF5252), 1.4f); sfx.playBossCharge()
            }
            if (phase != 0) {
                pt += dt
                when (phase) {
                    1 -> { telegraph = (pt / 0.9f).coerceIn(0f, 1f); if (pt >= 0.9f) { phase = 2; pt = 0f; telegraph = 0f; target = -(0.88f - (pxN + 0.14f)).coerceAtLeast(0.1f) } }
                    2 -> { val k = (pt / 0.45f).coerceIn(0f, 1f); BossLunge.shift = target * (1f - (1f - k) * (1f - k)); if (pt >= 0.45f) { phase = 3; pt = 0f } }
                    3 -> { BossLunge.shift = target; if (pt >= 0.55f) { phase = 4; pt = 0f; BossLunge.vulnerable = false } }
                    4 -> { BossLunge.shift = target * (1f - (pt / 0.9f).coerceIn(0f, 1f)); if (pt >= 0.9f) { phase = 0; BossLunge.shift = 0f; lungeDone = true } }
                }
                if ((phase == 2 || phase == 3) && abs(bossYRef.value - pyN) < 15f && (0.88f + BossLunge.shift + Fleet.dx) - pxN < 0.20f) hurt(14, "BOSS SLAM!")
            }
            // ---------------- brood ----------------
            if (boss && broodN > 0 && !broodDone && phase == 0 && queue == 0 && hp < 0.6f) {
                broodDone = true; queue = broodN; spawnT = 0f; spawned = 0; kills = 0
                say("🥚 THE BOSS IS BIRTHING A BROOD!", Color(0xFFFF8A80), 2.4f)
            }
            if (queue > 0) {
                spawnT -= dt
                if (spawnT <= 0f) {
                    spawnT = 0.28f; queue--; spawned++
                    BossBrood.babies.add(Baby(0.88f + BossLunge.shift + Fleet.dx - 0.05f, bossYRef.value.toFloat(), -0.05f, rnd(-18f, 18f), 1, 0f, Random.nextFloat() * 6.28f))
                    sfx.playBirth()
                    if (queue == 0) say("🐣 ${spawned} BABIES! Armor rounds wipe them all", Color(0xFFFFC21A), 2.6f)
                }
            }
            // chain blast
            if (BossBrood.novaRequested) {
                BossBrood.novaRequested = false
                val alive = BossBrood.babies.filter { it.hp > 0 }
                for (b in alive) { arcs.add(Arc(BossBrood.novaX, BossBrood.novaY, b.x, b.y)); b.hp = 0 }
                novaAge = 0f; novaPos = Offset(BossBrood.novaX, BossBrood.novaY)
                sfx.playNova(); if (alive.size > 1) { scoreRef.value(10 * (alive.size - 1)); say("💥 CHAIN BLAST x${alive.size + 1}", Color(0xFFFFC21A), 1.6f) }
            }
            // babies hunt the pilot
            val bi = BossBrood.babies.iterator()
            while (bi.hasNext()) {
                val b = bi.next(); b.age += dt
                if (b.hp > 0) {
                    if (b.age < 0.4f) { b.x += b.vx * dt; b.y += b.vy * dt * 0.5f }
                    else {
                        val sp = minOf(0.16f + 0.07f * (b.age - 0.4f), 0.34f)
                        b.x -= sp * dt
                        b.y += (pyN - b.y).coerceIn(-17f * dt, 17f * dt) + sin(b.age * 5f + b.seed) * 8f * dt
                        if (b.age > 0.5f && near(b.x, b.y, 22f)) { hurt(8, "BABY BITE!"); b.hp = -1 }
                    }
                    if (b.age > 11f || b.x < -0.15f) b.hp = -1
                }
                if (b.hp <= 0) {
                    puffs.add(Puff(b.x, b.y))
                    if (b.hp == 0) { kills++; scoreRef.value(25) }
                    bi.remove()
                }
            }
            if (spawned > 0 && queue == 0 && BossBrood.babies.isEmpty()) {
                if (kills >= spawned) { scoreRef.value(60); val g = RiskGates.add(1); say("🏆 BROOD CLEARED! +60${if (g > 0) " +1💎" else ""}", Color(0xFF69F0AE), 2.4f) }
                spawned = 0; kills = 0
            }
            val pi = puffs.iterator(); while (pi.hasNext()) { val p = pi.next(); p.age += dt; if (p.age > 0.4f) pi.remove() }
            val ai = arcs.iterator(); while (ai.hasNext()) { val a = ai.next(); a.age += dt; if (a.age > 0.45f) ai.remove() }
            if (novaAge >= 0f) { novaAge += dt; if (novaAge > 0.6f) novaAge = -1f }
            if (bombAge >= 0f) {
                bombAge += dt
                if (bombPending && bombAge >= 0.38f) {   // BLAST: clears the screen, wipes the brood, breaks the shield and wounds the boss
                    bombPending = false
                    sfx.playNova(); sfx.playAirBoom()
                    val hadBabies = BossBrood.hasBabies()
                    BossBrood.novaRequested = true; BossBrood.novaX = PilotDrift.x + 0.05f; BossBrood.novaY = yRef.value.toFloat()
                    val gone = sweepRef.value()
                    for (o in gone.take(14)) puffs.add(Puff(o.x, o.y))
                    var txt = "💥 BROOD BOMB · ${gone.size} CLEARED" + (if (hadBabies) " · BROOD WIPED" else "")
                    if (bossRef.value) { val dmg = if (legend) 0.18f else 0.10f; bombBossRef.value(dmg); txt += " · BOSS -${(dmg * 100).toInt()}% · SHIELD DOWN"; Shooting.lastArmor = true }
                    say(txt, if (legend) Color(0xFFFFC21A) else Color(0xFFFF4DD2), 2.2f)
                    scoreRef.value(40 + 6 * gone.size)
                }
                if (bombAge > 1.5f) bombAge = -1f
            }
            BossBrood.babies.isNotEmpty().let { if (it != hasBabies) hasBabies = it }
            frame++
        }
    }

    Box(modifier.fillMaxSize().onSizeChanged { sz = it }) {
        Canvas(Modifier.fillMaxSize()) {
            @Suppress("UNUSED_VARIABLE") val f = frame
            val w = size.width; val h = size.height; val t = frame * 0.016f
            fun X(x: Float) = x * w
            fun Y(y: Float) = y / 100f * h
            // lunge wind-up: boss glows red, a danger lane flashes toward the pilot
            if (telegraph > 0f) {
                val bc = Offset(X(0.88f), Y(bossYRef.value.toFloat()))
                val fl = 0.5f + 0.5f * sin(t * 30f)
                drawCircle(Color(0xFFFF1744).copy(alpha = 0.15f + 0.3f * telegraph * fl), 80.dp.toPx() * (1f + 0.2f * telegraph), bc)
                val py = Y(yRef.value.toFloat()); val px = X(PilotDrift.x)
                drawLine(Brush.horizontalGradient(listOf(Color(0xFFFF1744).copy(alpha = 0.5f * fl), Color.Transparent), bc.x, px), Offset(px, py), bc, 6.dp.toPx() * telegraph, StrokeCap.Round)
            }
            if (BossLunge.vulnerable && BossLunge.shift < -0.02f) {
                val bc = Offset(X(0.88f + BossLunge.shift + Fleet.dx), Y(bossYRef.value.toFloat()))
                for (k in 1..4) drawCircle(Color(0xFFFF8A80).copy(alpha = 0.14f / k), 70.dp.toPx(), Offset(bc.x + k * 22.dp.toPx(), bc.y))
            }
            // BOMB: a glowing shell flies from the pilot to the middle of the track, then a screen-wide blast
            if (bombAge >= 0f) {
                val bc = if (legend) Color(0xFFFFC21A) else Color(0xFFFF4DD2)
                val o = Offset(X(PilotDrift.x), Y(yRef.value.toFloat())); val mid = Offset(size.width * 0.55f, size.height * 0.5f)
                if (bombAge < 0.38f) {
                    val k = bombAge / 0.38f
                    for (tr in 0..6) { val kk = (k - tr * 0.06f).coerceAtLeast(0f); val pp = Offset(o.x + (mid.x - o.x) * kk, o.y + (mid.y - o.y) * kk - sin(kk * 3.14f) * 90.dp.toPx()); drawCircle(bc.copy(alpha = 0.6f * (1f - tr / 7f)), (9f - tr).dp.toPx().coerceAtLeast(1f), pp) }
                    val hp = Offset(o.x + (mid.x - o.x) * k, o.y + (mid.y - o.y) * k - sin(k * 3.14f) * 90.dp.toPx())
                    drawCircle(Color.White, 8.dp.toPx(), hp); drawCircle(bc.copy(alpha = 0.5f), 18.dp.toPx(), hp)
                    drawCircle(bc.copy(alpha = 0.8f), 26.dp.toPx() * (1f - k) + 6.dp.toPx(), mid, style = Stroke(2.dp.toPx()))   // target marker
                } else {
                    val k = ((bombAge - 0.38f) / 1.1f).coerceIn(0f, 1f)
                    drawRect(Color.White.copy(alpha = 0.75f * (1f - k) * (1f - k)))
                    drawRect(Brush.radialGradient(listOf(bc.copy(alpha = 0.45f * (1f - k)), Color.Transparent), mid, size.maxDimension * (0.3f + 0.9f * k)), size = size)
                    for (r in 0..3) { val kk = (k * 1.2f - r * 0.1f).coerceIn(0f, 1f); drawCircle(bc.copy(alpha = 0.9f * (1f - kk)), (30f + 700f * kk).dp.toPx(), mid, style = Stroke((14f * (1f - kk) + 1f).dp.toPx())) }
                    for (r in 0 until 28) { val a = r * 0.2244f; val s0 = (40f + 520f * k).dp.toPx(); drawLine(Color.White.copy(alpha = 0.8f * (1f - k)), Offset(mid.x + cos(a) * s0, mid.y + sin(a) * s0), Offset(mid.x + cos(a) * (s0 + 60.dp.toPx() * (1f - k) + 6.dp.toPx()), mid.y + sin(a) * (s0 + 60.dp.toPx() * (1f - k) + 6.dp.toPx())), 3.dp.toPx(), StrokeCap.Round) }
                    if (bossRef.value) { val bc2 = Offset(X(0.88f + BossLunge.shift + Fleet.dx), Y(bossYRef.value.toFloat())); drawCircle(bc.copy(alpha = 0.9f * (1f - k)), (20f + 220f * k).dp.toPx(), bc2, style = Stroke((8f * (1f - k) + 1f).dp.toPx())) }
                }
            }
            // chain blast
            if (novaAge >= 0f) {
                val k = (novaAge / 0.6f).coerceIn(0f, 1f)
                drawCircle(Color(0xFFFFC21A).copy(alpha = 0.8f * (1f - k)), (20f + 260f * k).dp.toPx(), Offset(X(novaPos.x), Y(novaPos.y)), style = Stroke((8f * (1f - k) + 1f).dp.toPx()))
                drawCircle(Color.White.copy(alpha = 0.5f * (1f - k)), (30f * (1f - k)).dp.toPx(), Offset(X(novaPos.x), Y(novaPos.y)))
            }
            for (a in arcs) {
                val k = (a.age / 0.45f).coerceIn(0f, 1f)
                val p = Path(); p.moveTo(X(a.x0), Y(a.y0))
                val mx = (X(a.x0) + X(a.x1)) / 2f + sin(a.age * 60f) * 16.dp.toPx(); val my = (Y(a.y0) + Y(a.y1)) / 2f + cos(a.age * 55f) * 16.dp.toPx()
                p.lineTo(mx, my); p.lineTo(X(a.x1), Y(a.y1))
                drawPath(p, Color(0xFFFFF3B0).copy(alpha = 1f - k), style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
                drawPath(p, Color(0xFFFFC21A).copy(alpha = 0.6f * (1f - k)), style = Stroke(8.dp.toPx(), cap = StrokeCap.Round))
            }
            // babies
            for (b in BossBrood.babies) {
                val c = Offset(X(b.x), Y(b.y))
                val birth = (b.age / 0.4f).coerceIn(0f, 1f)
                val r = 13.dp.toPx() * (0.3f + 0.7f * birth)
                val bob = sin(b.age * 14f + b.seed) * 2.dp.toPx()
                drawCircle(Brush.radialGradient(listOf(body.copy(alpha = 0.5f), Color.Transparent), c, r * 2.6f), r * 2.6f, c)
                if (birth < 1f) { // egg shell cracks open
                    for (i in 0..5) { val a = i * 1.047f + b.seed; drawLine(Color.White.copy(alpha = 1f - birth), Offset(c.x + cos(a) * r, c.y + sin(a) * r), Offset(c.x + cos(a) * (r + 10.dp.toPx() * birth), c.y + sin(a) * (r + 10.dp.toPx() * birth)), 2.dp.toPx(), StrokeCap.Round) }
                }
                val cy = c.y + bob
                // stubby legs, body, horns, eyes
                val leg = sin(b.age * 18f + b.seed) * 3.dp.toPx()
                drawLine(Color(0xFF2B1B3D), Offset(c.x - 5.dp.toPx(), cy + r * 0.8f), Offset(c.x - 5.dp.toPx() + leg, cy + r * 1.5f), 3.dp.toPx(), StrokeCap.Round)
                drawLine(Color(0xFF2B1B3D), Offset(c.x + 5.dp.toPx(), cy + r * 0.8f), Offset(c.x + 5.dp.toPx() - leg, cy + r * 1.5f), 3.dp.toPx(), StrokeCap.Round)
                drawCircle(Color(0xFF2B1B3D), r, Offset(c.x, cy)); drawCircle(body, r, Offset(c.x, cy), style = Stroke(2.5f.dp.toPx()))
                val horn = Path().apply { moveTo(c.x - r * 0.7f, cy - r * 0.6f); lineTo(c.x - r * 0.9f, cy - r * 1.5f); lineTo(c.x - r * 0.2f, cy - r * 0.9f); close(); moveTo(c.x + r * 0.7f, cy - r * 0.6f); lineTo(c.x + r * 0.9f, cy - r * 1.5f); lineTo(c.x + r * 0.2f, cy - r * 0.9f); close() }
                drawPath(horn, body)
                drawCircle(Color.White, r * 0.28f, Offset(c.x - r * 0.38f, cy - r * 0.1f)); drawCircle(Color.White, r * 0.28f, Offset(c.x + r * 0.38f, cy - r * 0.1f))
                drawCircle(Color(0xFFFF1744), r * 0.13f, Offset(c.x - r * 0.45f, cy - r * 0.1f)); drawCircle(Color(0xFFFF1744), r * 0.13f, Offset(c.x + r * 0.3f, cy - r * 0.1f))
            }
            for (p in puffs) {
                val k = (p.age / 0.4f).coerceIn(0f, 1f); val c = Offset(X(p.x), Y(p.y))
                drawCircle(body.copy(alpha = 0.8f * (1f - k)), (8f + 26f * k).dp.toPx(), c, style = Stroke(3.dp.toPx()))
                for (i in 0..5) { val a = i * 1.047f; drawLine(Color.White.copy(alpha = 1f - k), Offset(c.x + cos(a) * 8.dp.toPx() * (1f + k), c.y + sin(a) * 8.dp.toPx() * (1f + k)), Offset(c.x + cos(a) * 22.dp.toPx() * (0.5f + k), c.y + sin(a) * 22.dp.toPx() * (0.5f + k)), 2.dp.toPx(), StrokeCap.Round) }
            }
        }
        if (showBomb) {
            val bt = if (legend) Color(0xFFFFC21A) else Color(0xFFFF4DD2)
            @Suppress("UNUSED_VARIABLE") val f = frame
            GameButton(BtnKind.BOMB, 52.dp, bt, "BOMB", "x$bombCount", 1f, bombAge < 0f, bombAge >= 0f, { detonate() }, art = artBomb,
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 25.dp, bottom = 92.dp))
        }
        if (banner.isNotEmpty()) {
            Text(banner, color = bannerCol, fontSize = 12.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 96.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xDD080620)).border(1.5.dp, bannerCol.copy(alpha = 0.8f), RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 6.dp))
        }
    }
}
