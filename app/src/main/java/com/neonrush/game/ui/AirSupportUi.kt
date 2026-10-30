package com.neonrush.game.ui

import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neonrush.game.AirSupport
import com.neonrush.game.BossBrood
import com.neonrush.game.BossGuard
import com.neonrush.game.BossLunge
import com.neonrush.game.Fleet
import com.neonrush.game.NeonSoundEngine
import com.neonrush.game.PilotDrift
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

private val CYAN = Color(0xFF3DE8FF)
private val AMBER = Color(0xFFFFB300)
private val MONO = FontFamily.Monospace

private class AirPart(var x: Float, var y: Float, var vx: Float, var vy: Float, val life: Float, val col: Color, val size: Float, var age: Float = 0f)
private class AirTracer(val x0: Float, val y0: Float, val x1: Float, val y1: Float, var age: Float = 0f)
private class AirMissile(val sx: Float, val sy: Float, val tx: Float, val ty: Float, val dur: Float, val dmg: Float, val boss: Boolean, var age: Float = 0f, val trail: ArrayList<Offset> = ArrayList())
private class AirBoom(val x: Float, val y: Float, val big: Boolean, var age: Float = 0f)

private fun smooth(k: Float): Float { val c = k.coerceIn(0f, 1f); return c * c * (3f - 2f * c) }

/**
 * AIR SUPPORT overlay (Stage 145). Pro only on Hard (40% strength) and Legendary (full); free players get a one-time PRO preview.
 * Flow: the call button appears after the boss beats you (see AirSupport.offered) -> a movie-style intercepted radio call (voices, static, radar,
 * aircraft sound growing from far to close) -> the aircraft sweeps the WHOLE screen for about 3 seconds, guns clear the lane and the brood, two missiles hit the boss -> it leaves.
 * The pilot is covered (invulnerable) from the call until the aircraft leaves. All game effects go through the callbacks, the rest is drawing.
 */
@Composable
fun AirSupportOverlay(
    active: Boolean, tierName: String, isPro: Boolean, testing: Boolean,
    bossActive: Boolean, bossY: Int, pilotY: Int, fuel: Int, shields: Int,
    onGoPro: () -> Unit, onBossStrike: (Float) -> Unit, onSweep: (Float) -> Int, onScore: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (!AirSupport.modeOk(tierName)) return
    val ctx = LocalContext.current
    val dens = LocalDensity.current.density
    val sfx = remember { NeonSoundEngine() }
    val legend = tierName == "LEGENDARY"
    val bossRef = rememberUpdatedState(bossActive)
    val bossYRef = rememberUpdatedState(bossY)
    val pilotYRef = rememberUpdatedState(pilotY)
    val fuelRef = rememberUpdatedState(fuel)
    val shRef = rememberUpdatedState(shields)
    val strikeRef = rememberUpdatedState(onBossStrike)
    val sweepRef = rememberUpdatedState(onSweep)
    val scoreRef = rememberUpdatedState(onScore)
    val artHeli = rememberArt("air_heli"); val artJet = rememberArt("air_jet")

    var sz by remember { mutableStateOf(IntSize.Zero) }
    var stage by remember { mutableStateOf(0) }          // 0 idle, 1 radio, 2 flight, 3 outro / preview end card
    var demo by remember { mutableStateOf(false) }
    var jet by remember { mutableStateOf(false) }
    var st by remember { mutableStateOf(0f) }
    var frame by remember { mutableStateOf(0) }
    var tick by remember { mutableStateOf(0) }
    var speaker by remember { mutableStateOf("") }
    var lineText by remember { mutableStateOf("") }
    var lineStart by remember { mutableStateOf(0f) }
    var banner by remember { mutableStateOf("") }
    var bannerAge by remember { mutableStateOf(-1f) }
    var flash by remember { mutableStateOf(0f) }
    var struck by remember { mutableStateOf(0f) }
    val parts = remember { ArrayList<AirPart>() }
    val tracers = remember { ArrayList<AirTracer>() }
    val missiles = remember { ArrayList<AirMissile>() }
    val booms = remember { ArrayList<AirBoom>() }
    var acX by remember { mutableStateOf(-1f) }; var acY by remember { mutableStateOf(0f) }; var acDir by remember { mutableStateOf(1f) }; var acScale by remember { mutableStateOf(1f) }; var acBank by remember { mutableStateOf(0f) }
    var gun by remember { mutableStateOf(false) }
    var callId by remember { mutableStateOf(2) }
    var flightIdx by remember { mutableStateOf(0) }
    var chain by remember { mutableStateOf(listOf(false)) }
    var menuOpen by remember { mutableStateOf(false) }
    var seenBonus by remember { mutableStateOf(AirSupport.bonusSeq) }

    val radioLen = 3.7f
    val flyLen = (if (jet) 7.0f else 9.0f) * (if (flightIdx > 0) 0.75f else 1f)

    // ---------- watcher: counts failed attempts and heavy attacks, offers the call ----------
    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        AirSupport.newRun(); stage = 0
        var lastDef = false; var lastFuel = fuelRef.value; var lastSh = shRef.value; var lastFail = 0L; var wasOffered = false
        val hits = ArrayList<Long>()
        while (true) {
            kotlinx.coroutines.delay(100)
            val now = System.currentTimeMillis()
            if (!AirSupport.covering()) {
                val d = BossGuard.lastDeflect
                if (bossRef.value && d && !lastDef && now - lastFail > 1500) { AirSupport.fails += 1; lastFail = now }
                lastDef = d
                val f = fuelRef.value; val s = shRef.value
                if (s < lastSh || f <= lastFuel - 5) { hits.add(now); if (now - lastFail > 1500) { AirSupport.fails += 1; lastFail = now } }
                lastFuel = f; lastSh = s
                hits.removeAll { now - it > 15000L }
                AirSupport.intense = hits.size >= 3
            } else { lastFuel = fuelRef.value; lastSh = shRef.value }
            AirSupport.syncRound()
            if (AirSupport.bonusSeq != seenBonus) { seenBonus = AirSupport.bonusSeq; banner = AirSupport.bonusText; bannerAge = 0f; sfx.playAirLock() }
            val off = AirSupport.offered(tierName, bossRef.value)
            if (off && !wasOffered && stage == 0) { sfx.playAirLock(); banner = if (isPro) "BACKUP AVAILABLE" else "AIR SUPPORT (PRO) AVAILABLE"; bannerAge = 0f }
            wasOffered = off
            tick += 1
        }
    }

    fun start(asDemo: Boolean, id: Int = 2) {
        demo = asDemo; callId = id; flightIdx = 0
        chain = if (asDemo) listOf(Random.nextBoolean()) else AirSupport.chainFor(ctx, id)
        jet = chain[0]
        parts.clear(); tracers.clear(); missiles.clear(); booms.clear()
        st = 0f; stage = 1; speaker = ""; lineText = ""; acX = -1f; flash = 0f; struck = 0f
        AirSupport.phase = if (asDemo) 0 else 1
    }

    // ---------- the sequence ----------
    LaunchedEffect(stage, flightIdx) {
        if (stage == 0) return@LaunchedEffect
        var last = 0L
        val isJet = jet; val len = (if (isJet) 7.0f else 9.0f) * (if (flightIdx > 0) 0.75f else 1f)
        val ct = AirSupport.CALLS[(callId - 1).coerceIn(0, 6)]
        val nFl = chain.size.coerceAtLeast(1)
        var wiped = false
        var e0 = false; var e1 = false; var e2 = false; var e3 = false; var e4 = false; var e5 = false
        var g1 = false; var g2 = false; var m1 = false; var m2 = false; var babies = false; var pend = if (demo) 2 else ct.missiles / nFl + (if (flightIdx < ct.missiles % nFl) 1 else 0); var mCool = 0f; var lastBurst = -1; var flyby2 = false; var sweepT = 0f; var lockPlayed = false; var outro = false
        val strength = AirSupport.strength(tierName)
        val per = if (demo) 0.5f * strength else ct.dmgEach * strength
                val line1 = "Control, Neon One. Taking fire. Requesting air support!"
        val reply = if (isJet) "Neon One, Viper One. Inbound hot. Fox three!" else "Copy Neon One. Hawk Six inbound. Heads down!"
        while (true) {
            val now = withFrameNanos { it }
            val dt = if (last == 0L) 0.016f else ((now - last) / 1_000_000_000f).coerceAtMost(0.05f)
            last = now
            val w = sz.width.toFloat(); val h = sz.height.toFloat()
            if (w <= 0f || h <= 0f) { frame++; continue }
            st += dt
            if (flash > 0f) flash = (flash - dt * 2.4f).coerceAtLeast(0f)
            if (struck > 0f) struck = (struck - dt).coerceAtLeast(0f)
            if (bannerAge >= 0f) { bannerAge += dt; if (bannerAge > 1.5f) bannerAge = -1f }
            val dens2 = dens
            // particles / tracers / booms
            run {
                val it1 = parts.iterator(); while (it1.hasNext()) { val p = it1.next(); p.age += dt; p.x += p.vx * dt; p.y += p.vy * dt; p.vy += 120f * dt * dens2 * 0.3f; if (p.age > p.life) it1.remove() }
                val it2 = tracers.iterator(); while (it2.hasNext()) { val q = it2.next(); q.age += dt; if (q.age > 0.14f) it2.remove() }
                val it3 = booms.iterator(); while (it3.hasNext()) { val b = it3.next(); b.age += dt; if (b.age > 0.9f) it3.remove() }
            }
            if (stage == 1) {
                val t = st
                fun type(txt: String, from: Float) { lineText = txt.take(((t - from) * 42f).toInt().coerceAtLeast(0)) }
                if (!e0) { e0 = true; speaker = "NEON ONE"; lineStart = t; sfx.playAirRadio(true); AirSupport.say(ctx, "call1", line1, 0) }
                if (t < 1.4f) type(line1, lineStart)
                if (t >= 1.4f && !e1) { e1 = true; lineText = line1; sfx.playAirRadio(false) }
                if (t >= 1.6f && !e2) { e2 = true; speaker = if (isJet) "VIPER ONE" else "HAWK SIX"; lineStart = t; sfx.playAirRadio(true); AirSupport.say(ctx, if (isJet) "reply_jet" else "reply_heli", reply, 1) }
                if (t >= 1.6f && t < 3.3f) type(reply, lineStart)
                if (t >= 2.0f && !e3) { e3 = true; sfx.playAirFlyby(isJet, 6.5f) }
                if (t >= 3.3f && !e5) { e5 = true; sfx.playAirRadio(false) }
                if (t >= radioLen) { stage = 2; st = 0f; if (!demo) AirSupport.phase = 2; return@LaunchedEffect }   // restarts this effect for the flight
            } else if (stage == 2) {
                val u = (st / len).coerceIn(0f, 1f)
                // ---- flight path: two passes across the whole screen. Helicopter: slow cinematic pass out, quick pass back. Jet: the other way round ----
                var x: Float; var y: Float; var dir: Float; var sc: Float; var bank: Float
                val pi = PI.toFloat()
                if (!isJet) {
                    if (u < 0.4f) { val k = u / 0.4f; x = -0.18f + 1.08f * smooth(k); y = 0.22f + 0.07f * sin(k * pi * 1.5f); dir = 1f; sc = 0.7f + 0.35f * k; bank = 6f + 5f * sin(k * 9f) }
                    else if (u < 0.6f) { val k = (u - 0.4f) / 0.2f; x = 0.9f + 0.03f * sin(k * pi); y = 0.28f + 0.03f * sin(k * pi * 2f); dir = 1f - 2f * smooth((k - 0.35f) / 0.3f); sc = 1.08f; bank = 0f }
                    else { val k = (u - 0.6f) / 0.4f; val kk = k * k * (0.4f + 0.6f * k); x = 0.9f - 1.12f * kk; y = 0.28f + 0.3f * sin(k * pi); dir = -1f; sc = 1.08f - 0.4f * k; bank = 8f * sin(k * 7f) }
                } else {
                    if (u < 0.4f) { val k = u / 0.4f; x = -0.22f + 1.57f * k; y = 0.2f + 0.26f * k - 0.1f * sin(k * pi); dir = 1f; sc = 0.8f + 0.4f * k; bank = -10f + 20f * k }
                    else if (u < 0.55f) { x = 1.4f; y = 0.5f; dir = 1f; sc = 1.2f; bank = 0f }
                    else { val k = (u - 0.55f) / 0.45f; x = 1.3f - 1.55f * smooth(k); y = 0.64f - 0.16f * sin(k * pi); dir = -1f; sc = 1.25f - 0.35f * k; bank = -6f * sin(k * 5f) }
                }
                acX = x; acY = y; acDir = dir; acScale = sc; acBank = bank
                val ax = x * w; val ay = y * h
                val body = (if (isJet) 150f else 130f) * dens * sc
                if (u >= 0.58f && !flyby2) { flyby2 = true; sfx.playAirFlyby(isJet, 4.5f) }
                // ---- guns: bursts of one second with short pauses ----
                val win1 = if (isJet) u in 0.08f..0.4f else u in 0.05f..0.4f
                val win2 = if (isJet) u in 0.58f..0.95f else u in 0.62f..0.96f
                val burstOn = (st % 1.4f) < 1.0f
                gun = (win1 || win2) && burstOn
                val bi = (st / 1.4f).toInt()
                if (gun && bi != lastBurst) { lastBurst = bi; sfx.playAirGun(1.0f) }
                if (gun) {
                    val dirSign = if (dir >= 0f) 1f else -1f
                    val mx = ax + dirSign * body * 0.4f; val my = ay + body * 0.12f
                    for (q in 0..1) {
                        val tx = (ax + dirSign * (0.08f + Random.nextFloat() * 0.3f) * w).coerceIn(0f, w); val ty = h * (0.25f + Random.nextFloat() * 0.6f)
                        tracers.add(AirTracer(mx, my, tx, ty))
                        if (Random.nextFloat() < 0.5f) for (k in 0..2) parts.add(AirPart(tx, ty, (Random.nextFloat() - 0.5f) * 220f * dens, (Random.nextFloat() - 0.8f) * 200f * dens, 0.35f, if (k == 0) Color.White else Color(0xFFFFB74D), (2f + Random.nextFloat() * 3f) * dens))
                    }
                    if (!demo) {
                        sweepT -= dt
                        if (sweepT <= 0f) {
                            sweepT = 0.15f
                            val cut = if (win1) (x + 0.12f).coerceIn(0f, 1f) else 1f
                            val n = sweepRef.value(cut)
                            for (k in 0 until minOf(n, 3)) booms.add(AirBoom(w * (0.1f + Random.nextFloat() * 0.8f * cut), h * (0.25f + Random.nextFloat() * 0.6f), false))
                        }
                    }
                }
                // ---- missiles: wait for the boss to be on screen, fire two (the second 0.5 s after the first); if it never shows, the volley hits the lane ----
                val bossNow = bossRef.value
                val bx = (0.88f + BossLunge.shift + Fleet.dx) * w; val by = bossYRef.value / 100f * h
                mCool -= dt
                if (bossNow && u >= (if (isJet) 0.2f else 0.32f) - 0.08f && !lockPlayed) { lockPlayed = true; sfx.playAirLock() }
                fun launch(atBoss: Boolean) {
                    sfx.playAirMissile()
                    val tgtX = if (atBoss) bx else w * (0.55f + Random.nextFloat() * 0.3f)
                    val tgtY = if (atBoss) by + (Random.nextFloat() - 0.5f) * 30f * dens else h * (0.3f + Random.nextFloat() * 0.45f)
                    val dmgBase = per
                    val dist = kotlin.math.hypot(tgtX - ax, tgtY - ay) / w
                    missiles.add(AirMissile(ax, ay + body * 0.1f, tgtX, tgtY, (0.35f + 0.5f * dist).coerceIn(0.4f, 0.9f), dmgBase, atBoss))
                }
                if (pend > 0 && mCool <= 0f) {
                    if (bossNow && u >= (if (isJet) 0.2f else 0.32f) && u <= 0.92f) { launch(true); pend -= 1; mCool = 0.5f }
                    else if (u >= 0.86f) { launch(false); pend -= 1; mCool = 0.4f }
                }
                // ---- special calls: every escort is wiped out ----
                if (!demo && ct.wipe && !wiped && flightIdx == 0 && u >= 0.35f) { wiped = true; if (Fleet.alive() > 0) { Fleet.damageAll(6f); banner = "ESCORTS WIPED OUT"; bannerAge = 0f; flash = 1f; sfx.playAirBoom() } }
                // ---- brood wipe ----
                if (u >= 0.45f && !babies) { babies = true; if (!demo && BossBrood.hasBabies()) { BossBrood.novaX = 0.5f; BossBrood.novaY = 50f; BossBrood.novaRequested = true } }
                val mi = missiles.iterator()
                while (mi.hasNext()) {
                    val m = mi.next(); m.age += dt
                    val k = (m.age / m.dur).coerceIn(0f, 1f)
                    val px = m.sx + (m.tx - m.sx) * k; val py = m.sy + (m.ty - m.sy) * k + sin(k * PI.toFloat()) * -30f * dens
                    m.trail.add(Offset(px, py)); if (m.trail.size > 14) m.trail.removeAt(0)
                    if (k >= 1f) {
                        mi.remove()
                        booms.add(AirBoom(m.tx, m.ty, true)); flash = 0.9f; sfx.playAirBoom()
                        for (c in 0..22) { val a = Random.nextFloat() * 2f * PI.toFloat(); val sp = (60f + Random.nextFloat() * 260f) * dens; parts.add(AirPart(m.tx, m.ty, cos(a) * sp, sin(a) * sp, 0.6f + Random.nextFloat() * 0.4f, if (c % 3 == 0) Color.White else if (c % 3 == 1) Color(0xFFFFB74D) else Color(0xFFFF5722), (3f + Random.nextFloat() * 5f) * dens)) }
                        if (!demo) { if (m.boss) { strikeRef.value(m.dmg); banner = "AIR STRIKE  -${(m.dmg * 100).roundToInt()}% BOSS"; bannerAge = 0f; struck = 0.5f } else { val n = sweepRef.value(1f); if (n > 0) { banner = "AIR STRIKE  LANE CLEARED"; bannerAge = 0f } } }
                        else { banner = "AIR STRIKE"; bannerAge = 0f }
                    }
                }
                if (st >= len) {
                    if (!demo && flightIdx + 1 < nFl) { jet = chain[flightIdx + 1]; st = 0f; flightIdx += 1; return@LaunchedEffect }   // the next aircraft of the call
                    stage = 3; st = 0f; AirSupport.stopVoice()
                    if (!demo) { AirSupport.finishSortie(); scoreRef.value(30 + 20 * callId) }
                    return@LaunchedEffect
                }
            } else if (stage == 3) {
                if (!demo && !outro) { outro = true; speaker = if (isJet) "VIPER ONE" else "HAWK SIX"; lineStart = st; lineText = ""; sfx.playAirRadio(true)
                    AirSupport.say(ctx, if (isJet) "out_jet" else "out_heli", if (isJet) "Neon One, Viper One. Target down. Returning to base." else "Neon One, Hawk Six. Lane is clear. Good hunting!", 1) }
                if (!demo) {
                    val o = if (isJet) "Neon One, Viper One. Target down. Returning to base." else "Neon One, Hawk Six. Lane is clear. Good hunting!"
                    lineText = o.take(((st - lineStart) * 42f).toInt().coerceAtLeast(0))
                    if (st > 2.2f) { sfx.playAirRadio(false); stage = 0; return@LaunchedEffect }
                } else if (acX > -5f) { acX = -5f }   // preview: the end card waits for a tap
            }
            frame++
        }
    }

    val offered = AirSupport.offered(tierName, bossActive)
    @Suppress("UNUSED_VARIABLE") val tk = tick
    Box(modifier.fillMaxSize().onSizeChanged { sz = it }) {
        // -------- call button + the seven-call menu --------
        if (stage == 0 && offered) {
            val cd = AirSupport.cooldownLeftMs()
            val stock = com.neonrush.game.AirSupport.calls(ctx); val free = AirSupport.freeAvailable(ctx, isPro)
            val ready = cd == 0L
            val inf = androidx.compose.animation.core.rememberInfiniteTransition(label = "airBtn")
            val pulse by inf.animateFloat(0.55f, 1f, androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(700), androidx.compose.animation.core.RepeatMode.Reverse), label = "p")
            val accent = if (!isPro) Color(0xFFB388FF) else if (ready) Color(0xFF69F0AE) else Color(0xFF8A8A9A)
            val have = stock + (if (free) 1 else 0)
            Column(Modifier.align(Alignment.BottomStart).padding(start = 14.dp, bottom = 20.dp), horizontalAlignment = Alignment.Start) {
                if (menuOpen && isPro) {
                    val sur = AirSupport.surcharge()
                    Column(
                        Modifier.padding(bottom = 6.dp).widthIn(max = 300.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xF0080620)).border(1.5.dp, accent.copy(alpha = 0.7f), RoundedCornerShape(14.dp)).padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("AIR SUPPORT · PICK A CALL", color = accent, fontSize = 11.sp, fontWeight = FontWeight.Black, fontFamily = MONO, modifier = Modifier.weight(1f))
                            Text("✕", color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp, modifier = Modifier.clickable { menuOpen = false }.padding(horizontal = 4.dp))
                        }
                        Text("VAULT $have ⚡ · USED THIS RUN ${AirSupport.usedThisRun}" + (if (sur > 0) " · HEAT +$sur" else "") + (if (free) " · 1 FREE TODAY" else ""), color = Color.White.copy(alpha = 0.75f), fontSize = 9.sp, fontFamily = MONO)
                        for (c in AirSupport.CALLS) {
                            val why = AirSupport.lockedReason(c.id, tierName, isPro)
                            val cost = AirSupport.cost(c.id)
                            val ok = why == null && ready && (testing || have >= cost)
                            val special = c.id >= 6
                            val col = if (special) Color(0xFFFFC21A) else Color(0xFF69F0AE)
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(if (ok) col.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.05f))
                                    .border(1.dp, col.copy(alpha = if (ok) 0.8f else 0.2f), RoundedCornerShape(8.dp))
                                    .clickable {
                                        if (!ready) android.widget.Toast.makeText(ctx, "The aircraft are still rearming.", android.widget.Toast.LENGTH_SHORT).show()
                                        else if (AirSupport.pay(ctx, isPro, testing, tierName, c.id)) { menuOpen = false; start(false, c.id) }
                                        else android.widget.Toast.makeText(ctx, AirSupport.lastFail, android.widget.Toast.LENGTH_LONG).show()
                                    }.padding(horizontal = 8.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text("${c.id}  ${c.name}", color = if (ok) col else Color.White.copy(alpha = 0.5f), fontSize = 11.sp, fontWeight = FontWeight.Black, fontFamily = MONO)
                                    Text(if (why != null) "🔒 $why" else c.blurb, color = Color.White.copy(alpha = if (why != null) 0.55f else 0.75f), fontSize = 8.sp, fontFamily = MONO)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(if (testing) "FREE" else "$cost ⚡", color = if (ok) Color.White else Color.White.copy(alpha = 0.45f), fontSize = 12.sp, fontWeight = FontWeight.Black, fontFamily = MONO)
                                    Text(c.tag, color = col.copy(alpha = 0.7f), fontSize = 7.sp, fontFamily = MONO)
                                }
                            }
                        }
                    }
                }
                Column(
                    Modifier.clip(RoundedCornerShape(14.dp)).background(Color(0xE6080620))
                        .border(2.dp, accent.copy(alpha = if (ready) pulse else 0.5f), RoundedCornerShape(14.dp))
                        .clickable {
                            if (!isPro) { if (AirSupport.teaserShown) onGoPro() else { AirSupport.teaserShown = true; start(true) } }
                            else menuOpen = !menuOpen
                        }.padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(if (!isPro) "📞 AIR SUPPORT 👑" else if (!ready) "📞 READY IN ${(cd / 1000L) + 1}s" else "📞 AIR SUPPORT", color = accent, fontSize = 13.sp, fontWeight = FontWeight.Black, fontFamily = MONO)
                    Text(if (!isPro) "PRO · TAP TO PREVIEW" else if (testing) "TEST: FREE · TAP FOR CALLS" else "$have ⚡ IN VAULT · TAP FOR CALLS", color = Color.White.copy(alpha = 0.8f), fontSize = 9.sp, fontFamily = MONO)
                }
            }
        }
        // -------- cover ring + red alert edges --------
        if (stage in 1..2 || (stage == 3 && !demo)) Canvas(Modifier.fillMaxSize()) {
            frame.let { }
            val pulse = 0.5f + 0.5f * sin(st * 9f)
            if (stage == 1) drawRect(Brush.radialGradient(listOf(Color.Transparent, Color(0x55FF1744).copy(alpha = 0.25f + 0.25f * pulse)), Offset(size.width / 2f, size.height / 2f), size.maxDimension * 0.75f))
            if (!demo) {
                val px = PilotDrift.x * size.width; val py = pilotYRef.value / 100f * size.height
                drawCircle(CYAN.copy(alpha = 0.25f + 0.2f * pulse), 34f * dens, Offset(px, py), style = Stroke(2.5f * dens))
                drawCircle(CYAN.copy(alpha = 0.08f), 34f * dens, Offset(px, py))
            }
            if (stage == 1 && st > 2.6f) {   // incoming chevrons at the left edge
                val yy = size.height * 0.24f
                for (k in 0..2) { val a = (0.3f + 0.7f * (((st * 3f + k * 0.33f) % 1f))); drawLine(AMBER.copy(alpha = a), Offset(10f * dens + k * 14f * dens, yy - 16f * dens), Offset(22f * dens + k * 14f * dens, yy), 3f * dens); drawLine(AMBER.copy(alpha = a), Offset(22f * dens + k * 14f * dens, yy), Offset(10f * dens + k * 14f * dens, yy + 16f * dens), 3f * dens) }
            }
        }
        // -------- the aircraft, tracers, missiles, explosions --------
        if (stage == 2 || (stage == 3 && !demo)) Canvas(Modifier.fillMaxSize()) {
            frame.let { }
            val w = size.width; val h = size.height
            // letterbox
            val bar = h * 0.06f * minOf(1f, st / 0.3f, if (stage == 2) (flyLen - st) / 0.3f else 0f).coerceIn(0f, 1f)
            if (bar > 0f) { drawRect(Color.Black.copy(alpha = 0.55f), Offset.Zero, Size(w, bar)); drawRect(Color.Black.copy(alpha = 0.55f), Offset(0f, h - bar), Size(w, bar)) }
            for (q in tracers) { val k = q.age / 0.14f; val ex = q.x0 + (q.x1 - q.x0) * k.coerceAtMost(1f); val ey = q.y0 + (q.y1 - q.y0) * k.coerceAtMost(1f); val sx = q.x0 + (q.x1 - q.x0) * (k - 0.25f).coerceIn(0f, 1f); val sy = q.y0 + (q.y1 - q.y0) * (k - 0.25f).coerceIn(0f, 1f)
                drawLine(Color(0xFFFFF3B0).copy(alpha = 1f - k * 0.5f), Offset(sx, sy), Offset(ex, ey), 2.5f * dens) }
            for (m in missiles) {
                for ((i, p) in m.trail.withIndex()) drawCircle(Color(0xFFFFD180).copy(alpha = (i / m.trail.size.toFloat()) * 0.7f), (2f + i * 0.5f) * dens, p)
                val head = m.trail.lastOrNull(); if (head != null) { drawCircle(Color.White, 4f * dens, head); drawCircle(Color(0xFFFF7A18).copy(alpha = 0.5f), 9f * dens, head) }
            }
            for (b in booms) { val k = (b.age / 0.9f).coerceIn(0f, 1f); val r = (if (b.big) 160f else 50f) * dens
                drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 1f - k), Color(0xFFFFB74D).copy(alpha = 0.8f * (1f - k)), Color.Transparent), Offset(b.x, b.y), r * (0.25f + 0.75f * k)), r * (0.25f + 0.75f * k), Offset(b.x, b.y))
                drawCircle(Color.White.copy(alpha = 0.7f * (1f - k)), r * k * 1.2f, Offset(b.x, b.y), style = Stroke((4f * (1f - k) + 1f) * dens)) }
            for (p in parts) drawCircle(p.col.copy(alpha = (1f - p.age / p.life).coerceIn(0f, 1f)), p.size, Offset(p.x, p.y))
            if (stage == 2 && acX > -4f) {
                val body = (if (jet) 150f else 130f) * dens * acScale
                // reticle on the boss before the missiles go
                if (!demo && bossRef.value && st / flyLen in 0.18f..0.92f) {
                    val bx = (0.88f + BossLunge.shift + Fleet.dx) * w; val by = bossYRef.value / 100f * h; val rr = (56f + 14f * sin(st * 6f)) * dens
                    val c = Color(0xFFFF3D3D).copy(alpha = 0.6f + 0.4f * sin(st * 20f))
                    drawCircle(c, rr, Offset(bx, by), style = Stroke(2.5f * dens)); drawCircle(c, rr * 0.45f, Offset(bx, by), style = Stroke(1.5f * dens))
                    drawLine(c, Offset(bx - rr * 1.2f, by), Offset(bx + rr * 1.2f, by), 1.5f * dens); drawLine(c, Offset(bx, by - rr * 1.2f), Offset(bx, by + rr * 1.2f), 1.5f * dens)
                }
                if (jet) AirCraftArt.jet(this, acX * w, acY * h, body, acDir, st, acBank, artJet, gun && ((frame / 2) % 2 == 0))
                else AirCraftArt.heli(this, acX * w, acY * h, body, acDir, st, acBank, artHeli, gun && ((frame / 2) % 2 == 0))
            }
            if (flash > 0f) drawRect(Color.White.copy(alpha = flash * 0.35f))
        }
        // -------- the radio panel (movie intercept) --------
        if (stage == 1 || (stage == 3 && !demo)) {
            val shape = RoundedCornerShape(12.dp)
            Row(
                Modifier.align(Alignment.TopCenter).padding(top = 48.dp, start = 10.dp, end = 10.dp).widthIn(max = 440.dp).fillMaxWidth()
                    .graphicsLayer { alpha = if (stage == 1) minOf(1f, st / 0.25f) * (1f - smooth((st - 3.0f) / 0.6f)) * 0.8f else (1f - smooth((st - 1.3f) / 0.7f)) * 0.8f }   // faint, and gone before the action
                    .clip(shape).background(Color(0x66061018)).border(1.dp, Color(0xFF69F0AE).copy(alpha = 0.35f), shape).padding(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Canvas(Modifier.size(60.dp)) {
                    frame.let { }
                    val c = Offset(size.width / 2f, size.height / 2f); val r = size.minDimension / 2f
                    drawCircle(Color(0xFF69F0AE).copy(alpha = 0.12f), r, c); drawCircle(Color(0xFF69F0AE).copy(alpha = 0.6f), r, c, style = Stroke(1.5f * dens)); drawCircle(Color(0xFF69F0AE).copy(alpha = 0.3f), r * 0.55f, c, style = Stroke(1f * dens))
                    val a = st * 4.5f; drawLine(Color(0xFF69F0AE).copy(alpha = 0.9f), c, Offset(c.x + cos(a) * r, c.y + sin(a) * r), 2f * dens)
                    val k = (st / radioLen).coerceIn(0f, 1f); val bxp = c.x - r * 0.9f + r * 0.9f * k * 1.2f; val byp = c.y - r * 0.5f + r * 0.4f * k
                    drawCircle(Color(0xFFFFB300), 3.5f * dens, Offset(bxp.coerceIn(c.x - r, c.x + r), byp)); drawCircle(Color(0xFFFFB300).copy(alpha = 0.3f), (6f + 3f * sin(st * 8f)) * dens, Offset(bxp.coerceIn(c.x - r, c.x + r), byp))
                }
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("● REC  INTERCEPTED TRANSMISSION", color = Color(0xFFFF5252), fontSize = 8.sp, fontWeight = FontWeight.Black, fontFamily = MONO, modifier = Modifier.weight(1f))
                        Text("243.8 MHz", color = Color(0xFF69F0AE).copy(alpha = 0.8f), fontSize = 8.sp, fontFamily = MONO)
                    }
                    Text(speaker, color = if (speaker == "NEON ONE") CYAN else AMBER, fontSize = 11.sp, fontWeight = FontWeight.Black, fontFamily = MONO)
                    Text(lineText + if (((frame / 12) % 2) == 0) "▌" else "", color = Color.White, fontSize = 12.sp, fontFamily = MONO, lineHeight = 15.sp, modifier = Modifier.heightIn(min = 32.dp))
                    Canvas(Modifier.fillMaxWidth().height(14.dp)) {
                        frame.let { }
                        val bars = 36; val gw = size.width / bars
                        for (i in 0 until bars) { val hh = size.height * (0.15f + 0.85f * Random.nextFloat() * (if (lineText.isNotEmpty()) 1f else 0.2f)); drawRect(Color(0xFF69F0AE).copy(alpha = 0.75f), Offset(i * gw + 1f, (size.height - hh) / 2f), Size(gw - 2f, hh)) }
                        for (i in 0 until 3) { val yy = Random.nextFloat() * size.height; drawLine(Color.White.copy(alpha = 0.25f), Offset(0f, yy), Offset(size.width * Random.nextFloat(), yy), 1f) }   // static
                    }
                }
            }
        }
        // -------- banner --------
        if (bannerAge >= 0f) {
            val a = (1f - (bannerAge / 1.5f)).coerceIn(0f, 1f) * 0.8f; val sc = 0.8f + 0.2f * minOf(1f, bannerAge * 6f)
            Text(banner, color = (if (banner.contains("STRIKE")) Color(0xFFFFB74D) else Color(0xFF69F0AE)).copy(alpha = a), fontSize = (14f * sc).sp, fontWeight = FontWeight.Black, fontFamily = MONO, textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 108.dp).clip(RoundedCornerShape(10.dp)).background(Color(0x99080620).copy(alpha = 0.5f * a)).padding(horizontal = 12.dp, vertical = 5.dp))
        }
        if (demo && stage in 1..2) Text("👑 PRO PREVIEW · this is what Pro pilots get", color = Color(0xFFFFD23F), fontSize = 10.sp, fontWeight = FontWeight.Black, fontFamily = MONO,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 40.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xCC2A0A5A)).padding(horizontal = 10.dp, vertical = 4.dp))
        // -------- preview end card --------
        if (demo && stage == 3) {
            val shape = RoundedCornerShape(18.dp)
            Column(Modifier.align(Alignment.Center).padding(24.dp).clip(shape).background(Brush.verticalGradient(listOf(Color(0xFF2A1458), Color(0xFF120B2E)))).border(2.dp, Color(0xFFFFD23F), shape).padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("🚁  AIR SUPPORT  ✈", color = Color(0xFFFFD23F), fontSize = 18.sp, fontWeight = FontWeight.Black, fontFamily = MONO)
                Text("Backup on the radio, a gunship sweeping the screen, two missiles at the boss.", color = Color.White, fontSize = 12.sp, fontFamily = MONO, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
                Text("Pro pilots: 1 free call every day, a jet to unlock, and an AIR SUPPORT ribbon on their pilot card.", color = Color.White.copy(alpha = 0.75f), fontSize = 10.sp, fontFamily = MONO, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
                Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("GO PRO ▸", color = Color.Black, fontSize = 13.sp, fontWeight = FontWeight.Black, fontFamily = MONO, modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(Brush.horizontalGradient(listOf(Color(0xFFFFD23F), Color(0xFFFF8F00)))).clickable { stage = 0; onGoPro() }.padding(horizontal = 18.dp, vertical = 10.dp))
                    Text("CLOSE", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Black, fontFamily = MONO, modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = 0.12f)).clickable { stage = 0 }.padding(horizontal = 18.dp, vertical = 10.dp))
                }
            }
        }
    }
}
