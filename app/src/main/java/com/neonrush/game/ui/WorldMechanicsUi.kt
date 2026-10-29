package com.neonrush.game.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neonrush.game.NeonSoundEngine
import com.neonrush.game.PilotDrift
import com.neonrush.game.RiskGates
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

private class Ent(val kind: Int, var x: Float, var y: Float, var vx: Float = 0f, var vy: Float = 0f, var t: Float = 0f, var a: Float = 0f, var b: Float = 0f)

// kinds: 0 supply crate, 1 searchlight, 2 data core, 3 gravity pocket, 4 keycard, 5 laser grid, 6 vine lane, 7 stampede beast, 8 patrol drone, 9 lock-on, 10 wind gust, 11 scarab

/**
 * Signature mechanic of each special world, drawn and judged in the gameplay box (x = 0..1 of the width, y = 0..100 of the height):
 *  1 BLACKOUT FRONT   supply crates to extract + a searchlight that hunts you
 *  2 DERELICT SIGNAL  data cores to sync + gravity pockets that bend your course
 *  3 CELL BLOCK ZERO  keycards to collect + laser grids with one gap
 *  4 GREEN HELL       vine lanes that tangle and drag you back + a stampede with one safe row
 *  5 RED PROTOCOL     a heat meter (stay still and you get locked on) + patrol drones
 *  6 DUNES (realm)    wind gusts that shove you + scarab swarms
 * [mech] 0 = none. Also owns the pilot-drift hint and the forward-drift score bonus (every world).
 * Hits go through [onHit] (shield layer first unless soft, then fuel). Gems pay through RiskGates (shared per-run cap).
 */
@Composable
fun WorldMechanicsOverlay(
    mech: Int, active: Boolean, bossActive: Boolean, pilotY: Int, distanceMeters: Float, intensity: Float,
    onNudgeY: (Int) -> Unit, onHit: (Int, Boolean) -> Unit, onScore: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val ctx = LocalContext.current
    val dens = LocalDensity.current.density
    val sfx = remember { NeonSoundEngine() }
    val yRef = rememberUpdatedState(pilotY)
    val bossRef = rememberUpdatedState(bossActive)
    val distRef = rememberUpdatedState(distanceMeters)
    val nudgeRef = rememberUpdatedState(onNudgeY)
    val hitRef = rememberUpdatedState(onHit)
    val scoreRef = rememberUpdatedState(onScore)
    val ents = remember { ArrayList<Ent>() }
    var sz by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    var frame by remember { mutableStateOf(0) }
    var toast by remember { mutableStateOf("") }
    var toastCol by remember { mutableStateOf(Color.White) }
    var chip by remember { mutableStateOf("") }
    var heat by remember { mutableStateOf(0f) }
    var flash by remember { mutableStateOf(0f) }
    var flashCol by remember { mutableStateOf(Color.Red) }

    LaunchedEffect(active, mech) {
        ents.clear(); heat = 0f; chip = ""; toast = ""
        if (!active) return@LaunchedEffect
        fun rnd(a: Float, b: Float) = a + Random.nextFloat() * (b - a)
        val tmr = FloatArray(4)
        when (mech) { 1 -> { tmr[0] = rnd(4f, 7f); tmr[1] = rnd(10f, 14f) }; 2 -> { tmr[0] = rnd(3f, 5f); tmr[1] = rnd(9f, 13f) }; 3 -> { tmr[0] = rnd(5f, 8f); tmr[1] = rnd(12f, 16f) }
            4 -> { tmr[0] = rnd(8f, 11f); tmr[1] = rnd(16f, 21f) }; 5 -> { tmr[0] = rnd(8f, 11f) }; 6 -> { tmr[0] = rnd(7f, 10f); tmr[1] = rnd(11f, 15f) } }
        var toastLeft = 0f; var hitCd = 0f; var runT = 0f; var driftAcc = 0f; var nudgeAcc = 0f
        var collected = 0; var vineT = 0f; var lastY = yRef.value.toFloat()
        var last = 0L
        // one-time hint about drifting (first 3 runs)
        try {
            val sp = ctx.getSharedPreferences("neonrush_drift", android.content.Context.MODE_PRIVATE)
            val n = sp.getInt("hints", 0)
            if (n < 3) { sp.edit().putInt("hints", n + 1).apply(); toast = "↔ DRAG SIDEWAYS TO DRIFT\nforward = bonus points · back = more time"; toastCol = Color(0xFF80F0FF); toastLeft = 5f }
        } catch (_: Throwable) { }
        fun say(s: String, c: Color, secs: Float = 2.2f) { toast = s; toastCol = c; toastLeft = secs }
        while (true) {
            val now = withFrameNanos { it }
            val dt = if (last == 0L) 0.016f else ((now - last) / 1_000_000_000f).coerceAtMost(0.05f)
            last = now
            runT += dt
            if (toastLeft > 0f) { toastLeft -= dt; if (toastLeft <= 0f) toast = "" }
            if (flash > 0f) flash -= dt
            if (hitCd > 0f) hitCd -= dt
            val w = sz.width.toFloat(); val h = sz.height.toFloat()
            if (w <= 0f || h <= 0f) { frame++; continue }
            val pxN = PilotDrift.x; val pyN = yRef.value.toFloat()
            val pxPx = pxN * w; val pyPx = pyN / 100f * h
            fun near(ex: Float, ey: Float, r: Float) = hypot(ex * w - pxPx, ey / 100f * h - pyPx) < r * dens
            fun hurt(loss: Int, why: String, soft: Boolean = false) {
                if (hitCd > 0f) return
                hitCd = 1.0f
                hitRef.value(((loss * intensity).toInt()).coerceAtLeast(1), soft)
                say(why, Color(0xFFFF5252), 1.6f); flash = 0.3f; flashCol = Color(0xFFFF1744)
            }
            // forward drift pays a steady score bonus (every world)
            val bps = PilotDrift.bonusPerSec()
            if (bps > 0 && !bossRef.value) { driftAcc += dt; if (driftAcc >= 0.5f) { driftAcc = 0f; scoreRef.value(bps / 2) } }
            val live = mech != 0 && runT > 8f && !bossRef.value && distRef.value > 120f
            if (bossRef.value) { ents.removeAll { true } }
            // ---------------- spawn ----------------
            if (live) {
                for (i in 0..1) tmr[i] -= dt
                val k = 1f / intensity
                when (mech) {
                    1 -> {
                        if (tmr[0] <= 0f) { tmr[0] = rnd(5f, 8f) * k; ents.add(Ent(0, 1.1f, rnd(20f, 80f), -0.30f)) }
                        if (tmr[1] <= 0f) { tmr[1] = rnd(13f, 18f) * k; ents.add(Ent(1, rnd(0.55f, 0.9f), rnd(25f, 75f), t = 4.5f)); say("🔦 SEARCHLIGHT - stay out of the beam", Color(0xFFFFF59D), 2f); sfx.playLaserWarning() }
                    }
                    2 -> {
                        if (tmr[0] <= 0f) { tmr[0] = rnd(3.5f, 6f); ents.add(Ent(2, 1.1f, rnd(18f, 82f), -0.28f)) }
                        if (tmr[1] <= 0f) { tmr[1] = rnd(10f, 14f) * k; ents.add(Ent(3, 1.15f, rnd(28f, 72f), -0.22f)); say("🌀 GRAVITY POCKET", Color(0xFFB388FF), 1.6f) }
                    }
                    3 -> {
                        if (tmr[0] <= 0f) { tmr[0] = rnd(6f, 9f); ents.add(Ent(4, 1.1f, rnd(20f, 80f), -0.30f)) }
                        if (tmr[1] <= 0f) { tmr[1] = rnd(13f, 18f) * k; ents.add(Ent(5, 1.1f, 0f, -0.32f, a = rnd(26f, 74f), b = 15f)); say("⚠ LASER GRID - find the gap", Color(0xFFFF5252), 1.8f); sfx.playLaserWarning() }
                    }
                    4 -> {
                        if (tmr[0] <= 0f) { tmr[0] = rnd(9f, 13f) * k; ents.add(Ent(6, 0f, rnd(22f, 78f), t = 5.5f)); say("🌿 VINE LANE - leave the green band", Color(0xFF69F0AE), 2f) }
                        if (tmr[1] <= 0f) {
                            tmr[1] = rnd(17f, 23f) * k
                            val gap = Random.nextInt(0, 7)
                            for (r in 0 until 7) if (r != gap) ents.add(Ent(7, 1.1f + Random.nextFloat() * 0.3f, 14f + r * 12f, -0.52f))
                            say("🐗 STAMPEDE! Find the safe row", Color(0xFFFFB74D), 2f); sfx.playThreatWarn()
                        }
                    }
                    5 -> { if (tmr[0] <= 0f) { tmr[0] = rnd(8f, 12f) * k; ents.add(Ent(8, 1.1f, rnd(20f, 80f), -0.34f, a = Random.nextFloat() * 6f)) } }
                    6 -> {
                        if (tmr[0] <= 0f) { tmr[0] = rnd(7f, 10f) * k; ents.add(Ent(10, 0f, 0f, t = 3f, a = if (Random.nextBoolean()) 1f else -1f)); say("💨 WIND GUST", Color(0xFFFFD54F), 1.6f) }
                        if (tmr[1] <= 0f) {
                            tmr[1] = rnd(11f, 15f) * k
                            val by = rnd(25f, 75f)
                            for (i in 0 until 14) ents.add(Ent(11, 1.1f + Random.nextFloat() * 0.12f, by, -0.30f, a = Random.nextFloat() * 6.28f, b = (Random.nextFloat() - 0.5f) * 20f))
                            say("🪲 SCARAB SWARM", Color(0xFFFFB300), 1.6f)
                        }
                    }
                }
            }
            // ---------------- update ----------------
            var inVine = false
            val it2 = ents.iterator()
            while (it2.hasNext()) {
                val e = it2.next()
                var dead = false
                when (e.kind) {
                    0 -> { e.x += e.vx * dt; if (near(e.x, e.y, 28f)) { dead = true; collected++; scoreRef.value(30); sfx.playGemCollect(); chip = "📦 $collected/3"; if (collected >= 3) { collected = 0; chip = "📦 0/3"; scoreRef.value(60); val g = RiskGates.add(1); say("📦 EXTRACTED! +60${if (g > 0) " +1💎" else ""}", Color(0xFFFFE082)) } } }
                    2 -> { e.x += e.vx * dt; if (near(e.x, e.y, 28f)) { dead = true; collected++; scoreRef.value(20); sfx.playGemCollect(); chip = "💾 $collected/5"; if (collected >= 5) { collected = 0; chip = "💾 0/5"; scoreRef.value(100); val g = RiskGates.add(1); say("💾 DATA SYNCED! +100${if (g > 0) " +1💎" else ""}", Color(0xFF80F0FF)) } } }
                    4 -> { e.x += e.vx * dt; if (near(e.x, e.y, 28f)) { dead = true; collected++; scoreRef.value(40); sfx.playGemCollect(); chip = "🔑 $collected/3"; if (collected >= 3) { collected = 0; chip = "🔑 0/3"; scoreRef.value(150); val g = RiskGates.add(1); say("🔓 CELL DOOR OPEN! +150${if (g > 0) " +1💎" else ""}", Color(0xFF69F0AE)) } } }
                    1 -> {
                        e.t -= dt; val age = 4.5f - e.t
                        if (age > 1f) { val tx = pxN; val ty = pyN; e.x += (tx - e.x).coerceIn(-0.11f * dt, 0.11f * dt); e.y += (ty - e.y).coerceIn(-15f * dt, 15f * dt) }
                        if (age > 1f && near(e.x, e.y, 62f)) { e.a += dt; if (e.a > 0.8f) { e.a = 0f; hurt(6, "SPOTTED!") } } else e.a = (e.a - dt).coerceAtLeast(0f)
                        if (e.t <= 0f) dead = true
                    }
                    3 -> {
                        e.x += e.vx * dt
                        val d = hypot(e.x * w - pxPx, e.y / 100f * h - pyPx)
                        if (d < 150f * dens) nudgeAcc += (if (e.y > pyN) 1f else -1f) * 30f * dt * (1f - d / (150f * dens))
                    }
                    5 -> { e.x += e.vx * dt; if (abs(e.x * w - pxPx) < 12f * dens && abs(pyN - e.a) > e.b) hurt(8, "LASER HIT!") }
                    6 -> {
                        e.t -= dt; val age = 5.5f - e.t
                        if (age > 1f && abs(pyN - e.y) < 9f) { inVine = true; PilotDrift.nudge(-0.12f * dt); vineT += dt; if (vineT > 0.8f) { vineT = 0f; hurt(2, "TANGLED!", true) } }
                        if (e.t <= 0f) dead = true
                    }
                    7 -> { e.x += e.vx * dt; if (near(e.x, e.y, 24f)) hurt(10, "TRAMPLED!") }
                    8 -> { e.x += e.vx * dt; e.y += sin(runT * 2f + e.a) * 18f * dt; if (near(e.x, e.y, 24f)) hurt(6, "DRONE HIT!") }
                    9 -> {
                        e.t -= dt
                        if (e.t <= 0f) { dead = true; flash = 0.25f; flashCol = Color(0xFFFF8A80); if (near(e.x, e.y, 46f)) hurt(10, "DIRECT STRIKE!") else { scoreRef.value(25); say("DODGED! +25", Color(0xFF69F0AE), 1.4f) } }
                    }
                    10 -> { e.t -= dt; if (3f - e.t > 1f) nudgeAcc += e.a * 26f * dt; if (e.t <= 0f) dead = true }
                    11 -> { e.x += e.vx * dt; if (near(e.x, e.b / 1f + e.y + sin(runT * 5f + e.a) * 3f, 14f)) hurt(6, "SWARMED!") }
                }
                if (dead || e.x < -0.3f) it2.remove()
            }
            if (!inVine) vineT = 0f
            // Red Protocol heat: stay still and you get locked on, keep moving and you cool down
            if (live && mech == 5) {
                val moved = abs(pyN - lastY) / dt
                heat = if (moved < 8f) (heat + 0.16f * dt * intensity).coerceAtMost(1f) else (heat - 0.25f * dt).coerceAtLeast(0f)
                if (heat >= 1f) { heat = 0.3f; ents.add(Ent(9, pxN, pyN, t = 0.9f)); say("🎯 LOCKED ON - MOVE!", Color(0xFFFF5252), 1.2f); sfx.playThreatWarn() }
            } else if (mech != 5) heat = 0f
            lastY = pyN
            // pockets and gusts push the pilot's height
            while (nudgeAcc >= 1f) { nudgeAcc -= 1f; nudgeRef.value(1) }
            while (nudgeAcc <= -1f) { nudgeAcc += 1f; nudgeRef.value(-1) }
            frame++
        }
    }

    Box(modifier.fillMaxSize().onSizeChanged { sz = it }) {
        Canvas(Modifier.fillMaxSize()) {
            @Suppress("UNUSED_VARIABLE") val f = frame
            val w = size.width; val h = size.height
            val t = frame * 0.016f
            fun X(x: Float) = x * w
            fun Y(y: Float) = y / 100f * h
            if (flash > 0f) drawRect(flashCol.copy(alpha = 0.3f * (flash / 0.3f).coerceIn(0f, 1f)))
            for (e in ents) when (e.kind) {
                0 -> { val c = Offset(X(e.x), Y(e.y)); val s = 12.dp.toPx(); drawRect(Color(0xFFB8860B), Offset(c.x - s, c.y - s), Size(2 * s, 2 * s)); drawRect(Color(0xFFFFE082), Offset(c.x - s, c.y - s), Size(2 * s, 2 * s), style = Stroke(2.dp.toPx())); drawLine(Color(0xFFFFE082), Offset(c.x - s, c.y), Offset(c.x + s, c.y), 2.dp.toPx()) }
                2 -> { val c = Offset(X(e.x), Y(e.y)); drawCircle(Color(0x5580F0FF), 20.dp.toPx() + 3.dp.toPx() * sin(t * 6f), c); drawCircle(Color(0xFF80F0FF), 10.dp.toPx(), c, style = Stroke(2.dp.toPx())); drawCircle(Color.White, 4.dp.toPx(), c) }
                4 -> { val c = Offset(X(e.x), Y(e.y)); drawRect(Color(0xFF1B5E20), Offset(c.x - 14.dp.toPx(), c.y - 9.dp.toPx()), Size(28.dp.toPx(), 18.dp.toPx())); drawRect(Color(0xFF69F0AE), Offset(c.x - 14.dp.toPx(), c.y - 9.dp.toPx()), Size(28.dp.toPx(), 18.dp.toPx()), style = Stroke(2.dp.toPx())); drawCircle(Color(0xFF69F0AE), 4.dp.toPx(), Offset(c.x - 6.dp.toPx(), c.y)) }
                1 -> {
                    val age = 4.5f - e.t; val sp = Offset(X(e.x), Y(e.y)); val r = 62.dp.toPx()
                    val warn = age < 1f
                    val a = if (warn) 0.35f else 0.8f
                    val cone = androidx.compose.ui.graphics.Path().apply { moveTo(sp.x - 6.dp.toPx(), 0f); lineTo(sp.x + 6.dp.toPx(), 0f); lineTo(sp.x + r, sp.y); lineTo(sp.x - r, sp.y); close() }
                    drawPath(cone, Brush.verticalGradient(listOf(Color(0x66FFF59D), Color(0x22FFF59D)), 0f, sp.y), alpha = a)
                    drawCircle(Brush.radialGradient(listOf(Color(0xAAFFFDE7).copy(alpha = a), Color(0x44FFF59D).copy(alpha = a * 0.6f)), sp, r), r, sp)
                    drawCircle(Color(0xFFFFF59D).copy(alpha = a), r, sp, style = Stroke(2.dp.toPx()))
                    if (e.a > 0f) drawArc(Color(0xFFFF5252), -90f, 360f * (e.a / 0.8f).coerceIn(0f, 1f), false, Offset(sp.x - r - 6.dp.toPx(), sp.y - r - 6.dp.toPx()), Size((r + 6.dp.toPx()) * 2, (r + 6.dp.toPx()) * 2), style = Stroke(4.dp.toPx()))
                }
                3 -> {
                    val c = Offset(X(e.x), Y(e.y)); val r = 150.dp.toPx()
                    drawCircle(Brush.radialGradient(listOf(Color(0x00B388FF), Color(0x44B388FF)), c, r), r, c)
                    for (k in 0..3) { val kk = ((t * 0.8f + k * 0.25f) % 1f); drawCircle(Color(0xFFB388FF).copy(alpha = 0.7f * kk), r * (1f - kk), c, style = Stroke(2.dp.toPx())) }
                    drawCircle(Color(0xFF1A0033), 14.dp.toPx(), c); drawCircle(Color(0xFFD1C4E9), 14.dp.toPx(), c, style = Stroke(2.dp.toPx()))
                }
                5 -> {
                    val x = X(e.x); val g0 = Y(e.a - e.b); val g1 = Y(e.a + e.b); val fl = 0.7f + 0.3f * sin(t * 40f)
                    drawLine(Color(0xFFFF1744).copy(alpha = fl), Offset(x, 0f), Offset(x, g0), 5.dp.toPx(), StrokeCap.Round)
                    drawLine(Color(0xFFFF1744).copy(alpha = fl), Offset(x, g1), Offset(x, h), 5.dp.toPx(), StrokeCap.Round)
                    drawLine(Color.White.copy(alpha = 0.7f), Offset(x, 0f), Offset(x, g0), 1.5f.dp.toPx()); drawLine(Color.White.copy(alpha = 0.7f), Offset(x, g1), Offset(x, h), 1.5f.dp.toPx())
                    drawCircle(Color(0xFF69F0AE), 6.dp.toPx(), Offset(x, g0)); drawCircle(Color(0xFF69F0AE), 6.dp.toPx(), Offset(x, g1))
                }
                6 -> {
                    val age = 5.5f - e.t; val a = if (age < 1f) 0.25f else 0.65f
                    val y0 = Y(e.y - 9f); val y1 = Y(e.y + 9f)
                    drawRect(Color(0xFF1B5E20).copy(alpha = a * 0.6f), Offset(0f, y0), Size(w, y1 - y0))
                    for (k in 0 until 14) { val xx = w * k / 13f; drawLine(Color(0xFF69F0AE).copy(alpha = a), Offset(xx, y0), Offset(xx + sin(t * 3f + k) * 14.dp.toPx(), y1), 3.dp.toPx(), StrokeCap.Round) }
                    drawLine(Color(0xFF69F0AE).copy(alpha = a), Offset(0f, y0), Offset(w, y0), 2.dp.toPx()); drawLine(Color(0xFF69F0AE).copy(alpha = a), Offset(0f, y1), Offset(w, y1), 2.dp.toPx())
                }
                7 -> {
                    val c = Offset(X(e.x), Y(e.y)); val bob = sin(t * 18f + e.y) * 2.dp.toPx()
                    drawOval(Color(0xFF2B1B14), Offset(c.x - 22.dp.toPx(), c.y - 11.dp.toPx() + bob), Size(44.dp.toPx(), 22.dp.toPx()))
                    drawCircle(Color(0xFF3E2723), 10.dp.toPx(), Offset(c.x - 20.dp.toPx(), c.y - 2.dp.toPx() + bob))
                    drawCircle(Color(0xFFFF5252), 2.5f.dp.toPx(), Offset(c.x - 24.dp.toPx(), c.y - 5.dp.toPx() + bob))
                    drawLine(Color(0xFFFFE0B2), Offset(c.x - 28.dp.toPx(), c.y - 8.dp.toPx() + bob), Offset(c.x - 34.dp.toPx(), c.y - 16.dp.toPx() + bob), 2.5f.dp.toPx(), StrokeCap.Round)
                    drawLine(Color(0x55FFB74D), Offset(c.x + 22.dp.toPx(), c.y), Offset(c.x + 70.dp.toPx(), c.y), 4.dp.toPx(), StrokeCap.Round)
                }
                8 -> {
                    val c = Offset(X(e.x), Y(e.y))
                    drawCircle(Color(0xFF263238), 13.dp.toPx(), c); drawCircle(Color(0xFFFF1744), 13.dp.toPx(), c, style = Stroke(2.dp.toPx()))
                    drawCircle(Color(0xFFFF1744).copy(alpha = 0.6f + 0.4f * sin(t * 12f)), 4.dp.toPx(), c)
                    drawLine(Color(0xFF90A4AE), Offset(c.x - 22.dp.toPx(), c.y - 8.dp.toPx()), Offset(c.x + 22.dp.toPx(), c.y - 8.dp.toPx()), 3.dp.toPx(), StrokeCap.Round)
                }
                9 -> {
                    val c = Offset(X(e.x), Y(e.y)); val k = (e.t / 0.9f).coerceIn(0f, 1f); val r = (22f + 40f * k).dp.toPx()
                    drawCircle(Color(0xFFFF1744), r, c, style = Stroke(3.dp.toPx())); drawCircle(Color(0x33FF1744), r, c)
                    drawLine(Color(0xFFFF1744), Offset(c.x - r - 8.dp.toPx(), c.y), Offset(c.x + r + 8.dp.toPx(), c.y), 2.dp.toPx()); drawLine(Color(0xFFFF1744), Offset(c.x, c.y - r - 8.dp.toPx()), Offset(c.x, c.y + r + 8.dp.toPx()), 2.dp.toPx())
                }
                10 -> {
                    val age = 3f - e.t; val a = if (age < 1f) 0.25f else 0.6f
                    for (k in 0 until 16) {
                        val yy = h * ((k * 0.137f + t * 0.05f * e.a) % 1f).let { if (it < 0f) it + 1f else it }
                        val xx = w * ((k * 0.211f + t * 0.9f) % 1f)
                        drawLine(Color(0xFFFFE082).copy(alpha = a), Offset(xx, yy), Offset(xx - 70.dp.toPx(), yy + e.a * 8.dp.toPx()), 2.dp.toPx(), StrokeCap.Round)
                    }
                }
                11 -> { val c = Offset(X(e.x), Y(e.y) + Y(0f) + e.b / 100f * h + sin(t * 5f + e.a) * 3f); drawOval(Color(0xFFFFB300), Offset(c.x - 6.dp.toPx(), c.y - 4.dp.toPx()), Size(12.dp.toPx(), 8.dp.toPx())); drawCircle(Color(0xFF3E2723), 2.dp.toPx(), c) }
            }
        }
        if (toast.isNotEmpty()) {
            Text(toast, color = toastCol, fontSize = 12.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 120.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xDD080620)).border(1.5.dp, toastCol.copy(alpha = 0.8f), RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 6.dp))
        }
        if (chip.isNotEmpty() && mech in 1..4) {
            Text(chip, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace,
                modifier = Modifier.align(Alignment.TopStart).padding(start = 12.dp, top = 120.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xCC080620)).padding(horizontal = 8.dp, vertical = 4.dp))
        }
        if (mech == 5) {
            Column(Modifier.align(Alignment.CenterStart).padding(start = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("HEAT", color = Color(0xFFFF8A80), fontSize = 8.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
                Box(Modifier.width(10.dp).height(90.dp).clip(RoundedCornerShape(5.dp)).background(Color(0xCC080620)).border(1.dp, Color(0xFFFF8A80).copy(alpha = 0.7f), RoundedCornerShape(5.dp)), contentAlignment = Alignment.BottomCenter) {
                    Box(Modifier.fillMaxWidth().fillMaxHeight(heat.coerceIn(0f, 1f)).background(Brush.verticalGradient(listOf(Color(0xFFFF1744), Color(0xFFFFEB3B)))))
                }
            }
        }
    }
}
