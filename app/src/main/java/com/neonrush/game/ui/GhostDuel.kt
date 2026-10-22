package com.neonrush.game.ui

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.neonrush.game.AdMobManager
import com.neonrush.game.NeonSoundEngine
import com.neonrush.game.R
import com.neonrush.game.RevenueCatManager
import com.neonrush.game.RewardedSlot
import com.neonrush.game.ui.theme.CyberOnSurface
import com.neonrush.game.ui.theme.CyberPrimary
import com.neonrush.game.ui.theme.CyberSurface
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

// =====================================================================================================================
// GHOST DUEL: a head-to-head sprint against a named rival. Different game from the endless run:
// side-view, two lanes (rival on top, you below), fixed finish line, jump / slide timing, boost pads and a slipstream.
// =====================================================================================================================

object GhostDuelHost { var open by mutableStateOf(false); var runKey by mutableStateOf(0); var rival by mutableStateOf(0) }

private const val TRACK = 900f              // units to the finish line
private const val BASE_SPEED = 15f          // units per second
private const val UNIT_W = 0.05f            // screen widths per unit
private const val PX = 0.28f                // the runner's fixed screen x (fraction)
private const val AIR = 0.54f               // seconds a jump lasts
private const val SLIDE = 0.68f
private const val CD = 0.14f                // cooldown after a jump / slide
private val DGOLD = Color(0xFFFFD166)
private val DCYAN = Color(0xFF3DE8FF)
private val DMAG = Color(0xFFFF4DD2)

class Rival(val name: String, val title: String, val color: Color, val skill: Float, val speedMul: Float,
            val pre: String, val win: String, val lose: String, val firstWinGems: Int, val density: Float, val portrait: Int, val sprite: Int = 0)

/** The same rival, stronger the higher the level (caps after level 10 so the race stays winnable, but the rewards keep coming). */
fun Rival.scaled(level: Int): Rival {
    if (level <= 0) return this
    val k = min(level, 10)
    return Rival(name, title, color, min(0.985f, skill + 0.008f * k), speedMul + 0.008f * k, pre, win, lose, firstWinGems, density + 0.02f * k, portrait, sprite)
}

val RIVALS = listOf(
    Rival("RetroWave", "The Showoff", Color(0xFFFF4DD2), 0.78f, 1.00f, "Let's see you keep up!", "Too slow, rookie.", "Lucky run... I want a rematch.", 40, 1.0f, R.drawable.rival_retro, R.drawable.rival_retro_run),
    Rival("ZeroGlitch", "The Machine", Color(0xFFB388FF), 0.88f, 1.03f, "Calculating your defeat.", "Predicted. Try again.", "Error. Impossible.", 80, 1.15f, R.drawable.rival_zero, R.drawable.rival_zero_run),
    Rival("CyberRunner", "The Champion", Color(0xFFFFD166), 0.94f, 1.06f, "Nobody beats me twice.", "Another one for the wall.", "You... earned this one.", 150, 1.3f, R.drawable.rival_cyber, R.drawable.rival_cyber_run)
)

/** Local progress: duels per day, rivals beaten, best times. */
object DuelStore {
    private var sp: SharedPreferences? = null
    fun init(c: Context) { if (sp == null) sp = c.applicationContext.getSharedPreferences("neonrush_duel", Context.MODE_PRIVATE) }
    private fun gi(k: String, d: Int = 0) = try { sp?.getInt(k, d) ?: d } catch (_: Throwable) { d }
    private fun pi(k: String, v: Int) { try { sp?.edit()?.putInt(k, v)?.apply() } catch (_: Throwable) { } }
    private fun day() = StormModes.dayKey()
    fun maxPerDay(isPro: Boolean) = if (isPro) 5 else 3
    fun used(): Int = if (gi("useDay") == day()) gi("used") else 0
    fun left(isPro: Boolean) = (maxPerDay(isPro) - used()).coerceAtLeast(0)
    fun useAttempt() { val d = day(); pi("useDay", d); pi("used", used() + 1) }
    fun adLeft(): Int = 2 - (if (gi("adDay") == day()) gi("adUsed") else 0)
    /** A rewarded ad earned one extra duel: give the used one back. */
    fun grantExtra() { pi("used", (used() - 1).coerceAtLeast(0)); useAdAttempt() }
    fun useAdAttempt() { val d = day(); val u = if (gi("adDay") == d) gi("adUsed") else 0; pi("adDay", d); pi("adUsed", u + 1) }
    /** Levels cleared against rival i (0 = never beaten). Every win raises the rival one level, with no end: the league is endless. */
    fun lvl(i: Int): Int = max(gi("lvl$i"), if (gi("beat$i") == 1) 1 else 0)
    fun beaten(i: Int) = lvl(i) >= 1
    fun allBeaten() = RIVALS.indices.all { beaten(it) }
    fun league(): Int = RIVALS.indices.sumOf { lvl(it) }
    fun winGems(nextLevel: Int): Int = 10 + 4 * min(nextLevel, 10)
    fun unlocked(i: Int) = i == 0 || beaten(i - 1)
    fun bestTime(i: Int) = gi("best$i", 0) / 100f
    fun wins() = RIVALS.indices.sumOf { gi("wins$it") }
    /** returns gems earned */
    fun recordWin(i: Int, time: Float, isPro: Boolean): Int {
        val old = lvl(i); val nl = old + 1
        pi("lvl$i", nl); pi("beat$i", 1); pi("wins$i", gi("wins$i") + 1)
        val t100 = (time * 100).toInt()
        if (gi("best$i", 0) == 0 || t100 < gi("best$i", 0)) pi("best$i", t100)
        val base = if (old == 0) RIVALS[i].firstWinGems else winGems(nl)
        return (base * (if (isPro) 1.25f else 1f)).roundToInt() + (if (old > 0 && nl % 5 == 0) 100 else 0)   // every 5th level: milestone bonus
    }
}

// ------------------------------------------------------------------------------------------------------ simulation

private class Obs(val pos: Float, val type: Int) { var pDone = false; var rDone = false; var rOk = true }   // 0 hurdle, 1 low bar, 2 boost pad

private class Runner(val base: Float) {
    var x = 0f; var v = 0f
    var airT = 0f; var slideT = 0f; var cd = 0f
    var stumble = 0f; var boost = 0f
    var finishT = -1f
    var hits = 0
    var sJump = 9f; var sLand = 9f; var sHit = 9f; var sSlide = 9f   // seconds since each event (drives animation)
}

private class DuelSim(val rival: Rival, seed: Int) {
    val obs = ArrayList<Obs>()
    val me = Runner(BASE_SPEED)
    val ai = Runner(BASE_SPEED * rival.speedMul)
    var t = -3f
    var done = false; var playerWon = false; var margin = 0f; var myTime = 0f
    var shake = 0f; var flashRed = 0f; var slip = false
    val rng = Random(seed * 17 + 3)

    init {
        val r = Random(seed * 7 + 1)
        var p = 26f
        while (p < TRACK - 20f) {
            val tp = r.nextInt(100)
            val type = if (tp < 40) 0 else if (tp < 78) 1 else 2
            obs += Obs(p, type)
            p += (14f - 3.5f * (rival.density - 1f) * 3f).coerceAtLeast(11f) + r.nextFloat() * 6f
        }
        val rr = Random(seed * 13 + 5)
        for (o in obs) o.rOk = o.type == 2 || rr.nextFloat() < rival.skill
    }

    fun jump() { if (t < 0f || me.airT > 0f || me.cd > 0f || done) return; me.airT = AIR; me.slideT = 0f; me.sJump = 0f }
    fun slide() { if (t < 0f || me.airT > 0f || me.slideT > 0f || me.cd > 0f || done) return; me.slideT = SLIDE; me.sSlide = 0f }

    fun step(dt: Float, onHit: () -> Unit, onBoost: () -> Unit) {
        t += dt
        shake = max(0f, shake - dt * 3f); flashRed = max(0f, flashRed - dt * 3f)
        if (t < 0f || done) return
        for (q in arrayOf(me, ai)) { q.sJump += dt; q.sLand += dt; q.sHit += dt; q.sSlide += dt }
        // --- me
        run {
            val m = me
            if (m.airT > 0f) { m.airT -= dt; if (m.airT <= 0f) { m.airT = 0f; m.cd = CD; m.sLand = 0f } }
            if (m.slideT > 0f) { m.slideT -= dt; if (m.slideT <= 0f) { m.slideT = 0f; m.cd = 0.04f } }
            m.cd = max(0f, m.cd - dt); m.stumble = max(0f, m.stumble - dt); m.boost = max(0f, m.boost - dt)
            val slipstream = if (ai.x > m.x && ai.x - m.x < 22f) 2.2f else 0f
            slip = slipstream > 0f
            val target = (m.base + (if (m.boost > 0f) 6f else 0f) + slipstream) * (if (m.stumble > 0f) 0.42f else 1f)
            m.v += (target - m.v) * min(1f, dt * 4f)
            val prev = m.x; m.x += m.v * dt
            for (o in obs) {
                if (o.pDone || o.pos > m.x) continue
                if (o.pos < prev - 3f) { o.pDone = true; continue }
                o.pDone = true
                when (o.type) {
                    0 -> if (m.airT <= 0.02f) { m.stumble = 0.9f; m.sHit = 0f; m.hits++; shake = 1f; flashRed = 1f; onHit() }
                    1 -> if (m.slideT <= 0f) { m.stumble = 0.9f; m.sHit = 0f; m.hits++; shake = 1f; flashRed = 1f; onHit() }
                    else -> { m.boost = 1.6f; onBoost() }
                }
            }
            if (m.x >= TRACK && m.finishT < 0f) m.finishT = t
        }
        // --- rival: same course, pre-rolled mistakes, gentle rubber band so the race stays close
        run {
            val a = ai
            a.stumble = max(0f, a.stumble - dt); a.boost = max(0f, a.boost - dt)
            if (a.airT > 0f) { a.airT -= dt; if (a.airT <= 0f) { a.airT = 0f; a.sLand = 0f } }
            if (a.slideT > 0f) { a.slideT -= dt; if (a.slideT < 0f) a.slideT = 0f }
            if (a.airT <= 0f && a.slideT <= 0f && a.stumble <= 0f) for (o in obs) {
                if (o.rDone || o.type == 2 || !o.rOk) continue
                val d = o.pos - a.x
                if (d in 0f..(a.v * 0.22f)) { if (o.type == 0) { a.airT = AIR; a.sJump = 0f } else { a.slideT = SLIDE; a.sSlide = 0f }; break }
            }
            val gap = a.x - me.x
            val rb = if (gap > 40f) 0.96f else if (gap < -40f) 1.04f else 1f
            val target = (a.base + (if (a.boost > 0f) 6f else 0f)) * (if (a.stumble > 0f) 0.42f else 1f) * rb
            a.v += (target - a.v) * min(1f, dt * 4f)
            val prev = a.x; a.x += a.v * dt
            for (o in obs) {
                if (o.rDone || o.pos > a.x) continue
                if (o.pos < prev - 3f) { o.rDone = true; continue }
                o.rDone = true
                if (o.type == 2) a.boost = 1.6f else if (!o.rOk) { a.stumble = 0.9f; a.sHit = 0f; a.hits++ }
            }
            if (a.x >= TRACK && a.finishT < 0f) a.finishT = t
        }
        if (me.finishT >= 0f || ai.finishT >= 0f || t > 120f) {
            done = true
            playerWon = me.finishT >= 0f && (ai.finishT < 0f || me.finishT <= ai.finishT)
            myTime = if (me.finishT >= 0f) me.finishT else t
            margin = abs(me.x - ai.x)
        }
    }
}

// ------------------------------------------------------------------------------------------------------ lobby (replaces the old ghost picker)

@Composable
fun GhostDuelLobby(onBack: () -> Unit) {
    val isPro by RevenueCatManager.isPro.collectAsState()
    var tick by remember { mutableStateOf(0) }       // bumps to refresh after a duel
    var msg by remember { mutableStateOf("") }
    val activity = LocalContext.current.findActivity()
    LaunchedEffect(GhostDuelHost.open) { tick++ }
    val left = remember(tick, isPro) { DuelStore.left(isPro) }
    val adLeft = remember(tick) { DuelStore.adLeft() }
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 6.dp)) {
            androidx.compose.material3.IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = CyberPrimary, modifier = Modifier.size(24.dp))
            }
            Spacer(modifier = Modifier.width(8.dp))
            PilotPose(Pose.BOOST, 44.dp)
            Text("👻 GHOST DUEL", fontSize = 18.sp, fontWeight = FontWeight.Black, color = CyberPrimary, fontFamily = FontFamily.Monospace)
        }
        Text("A head-to-head sprint. Jump the barriers, slide under the beams, hit the boost pads, and cross the line before your rival.", color = CyberOnSurface.ca(0.75f), fontSize = 12.sp, modifier = Modifier.padding(bottom = 4.dp))
        Text("TAP = JUMP   •   SWIPE DOWN = SLIDE", color = DGOLD, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(bottom = 10.dp))
        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Duels left today: $left/${DuelStore.maxPerDay(isPro)}", color = Color.White, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(painter = BitmapPainter(ImageBitmap.imageResource(id = R.drawable.duel_trophy)), contentDescription = null, modifier = Modifier.size(22.dp))
                Text(" Wins: ${DuelStore.wins()}", color = DGOLD, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
            }
        }
        RIVALS.forEachIndexed { i, r ->
            val unlocked = DuelStore.unlocked(i)
            val beaten = DuelStore.beaten(i)
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp).clip(RoundedCornerShape(12.dp))
                    .background(CyberSurface).border(1.5.dp, if (unlocked) r.color.ca(0.8f) else Color.White.ca(0.12f), RoundedCornerShape(12.dp)).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.size(width = 60.dp, height = 80.dp).clip(RoundedCornerShape(10.dp)).border(1.5.dp, r.color.ca(if (unlocked) 0.9f else 0.2f), RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                    Image(painter = BitmapPainter(ImageBitmap.imageResource(id = r.portrait)), contentDescription = r.name, contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(), alpha = if (unlocked) 1f else 0.18f)
                    if (!unlocked) Text("\uD83D\uDD12", fontSize = 22.sp)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(r.name, color = if (unlocked) Color.White else Color.White.ca(0.4f), fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, fontSize = 16.sp)
                    Text(r.title + (if (beaten) "  •  LEVEL ${DuelStore.lvl(i) + 1}  •  best ${"%.1f".format(DuelStore.bestTime(i))}s" else ""), color = if (beaten) Color(0xFF69F0AE) else CyberOnSurface.ca(0.6f), fontSize = 11.sp)
                    Text(if (!unlocked) "Beat ${RIVALS[i - 1].name} to unlock" else if (beaten) "Win: ${DuelStore.winGems(DuelStore.lvl(i) + 1)} 💎" + (if ((DuelStore.lvl(i) + 1) % 5 == 0) " + 100 MILESTONE" else "") else "First win: ${r.firstWinGems} 💎", color = DGOLD, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                }
                Text(
                    if (unlocked) "DUEL" else "LOCKED", color = Color.Black, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, fontSize = 13.sp,
                    modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(if (unlocked) r.color else Color.Gray)
                        .clickable(enabled = unlocked) {
                            if (DuelStore.left(isPro) > 0) { DuelStore.useAttempt(); GhostDuelHost.rival = i; GhostDuelHost.runKey += 1; GhostDuelHost.open = true; msg = "" }
                            else msg = "No duels left today. Watch an ad below or come back tomorrow."
                        }.padding(horizontal = 18.dp, vertical = 10.dp)
                )
            }
        }
        Text(if (DuelStore.allBeaten()) "🏆 CHAMPION LEAGUE  •  League level ${DuelStore.league()}\nEvery win makes that rival faster and smarter. It never ends: new levels, bigger milestones (+100 💎 every 5th level)." else "Beat all three rivals to enter the Champion League.", color = DGOLD.ca(0.85f), fontSize = 11.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(top = 10.dp))
        if (left <= 0 && adLeft > 0) {
            Text(AdMobManager.rewardedLabel(RewardedSlot.STORM, "🎬 WATCH AD: FREE DUEL ($adLeft left today)"), color = Color.White, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, fontSize = 13.sp,
                modifier = Modifier.padding(top = 14.dp).clip(RoundedCornerShape(24.dp)).border(1.5.dp, Color(0xFF4CAF50), RoundedCornerShape(24.dp))
                    .clickable { activity?.let { a -> AdMobManager.showRewardedIfReady(a, RewardedSlot.STORM) { DuelStore.grantExtra(); tick++; msg = "Free duel earned! Press DUEL." } } }
                    .padding(horizontal = 20.dp, vertical = 10.dp))
        }
        if (msg.isNotEmpty()) Text(msg, color = DGOLD, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
        Spacer(modifier = Modifier.height(24.dp))
    }
}

// ------------------------------------------------------------------------------------------------------ the duel screen

@Composable
fun GhostDuelScreen(isPro: Boolean, onCreditGems: (Int) -> Unit, onExit: () -> Unit) {
    Dialog(
        onDismissRequest = { },
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = false, dismissOnClickOutside = false)
    ) { DuelBody(isPro, onCreditGems, onExit) }
}

@Composable
private fun DuelBody(isPro: Boolean, onCreditGems: (Int) -> Unit, onExit: () -> Unit) {
    val runKey = GhostDuelHost.runKey
    val rivalIdx = GhostDuelHost.rival.coerceIn(0, RIVALS.size - 1)
    val rival = remember(runKey) { RIVALS[rivalIdx].scaled(DuelStore.lvl(rivalIdx)) }
    val sim = remember(runKey) { DuelSim(rival, StormModes.dayKey() * 10 + rivalIdx + runKey) }
    val sfx = remember { NeonSoundEngine() }
    val activity = LocalContext.current.findActivity()
    val frames = listOf(R.drawable.pilot_run_1, R.drawable.pilot_run_2, R.drawable.pilot_run_3, R.drawable.pilot_run_4, R.drawable.pilot_run_5, R.drawable.pilot_run_6)
        .map { ImageBitmap.imageResource(id = it) }

    val duelBg = ImageBitmap.imageResource(id = R.drawable.duel_bg)
    val gateImg = ImageBitmap.imageResource(id = R.drawable.duel_gate)
    val vsImg = ImageBitmap.imageResource(id = R.drawable.duel_vs)
    val trophyImg = ImageBitmap.imageResource(id = R.drawable.duel_trophy)
    val poseBoost = ImageBitmap.imageResource(id = R.drawable.pilot_boost)
    val poseThumb = ImageBitmap.imageResource(id = R.drawable.pilot_thumb)
    val poseSad = ImageBitmap.imageResource(id = R.drawable.pilot_sad)
    val rivalSprite: ImageBitmap? = if (rival.sprite != 0) ImageBitmap.imageResource(id = rival.sprite) else null   // full-body art; null = tinted pilot placeholder
    var frame by remember(runKey) { mutableStateOf(0L) }
    var phase by remember(runKey) { mutableStateOf(0) }       // 0 racing, 1 result
    var gems by remember(runKey) { mutableStateOf(0) }
    var doubled by remember(runKey) { mutableStateOf(false) }
    var firstWin by remember(runKey) { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }
    var confirmQuit by remember { mutableStateOf(false) }
    var hudCount by remember(runKey) { mutableStateOf(3) }
    var hudProg by remember(runKey) { mutableStateOf(0f) }
    var hudRProg by remember(runKey) { mutableStateOf(0f) }
    var hudGap by remember(runKey) { mutableStateOf(0f) }
    var tick by remember { mutableStateOf(0) }

    LaunchedEffect(runKey) {
        var last = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            val dt = ((now - last) / 1_000_000_000f).coerceIn(0f, 0.05f); last = now
            sim.step(dt, onHit = { sfx.playTone(140f, 200, "sawtooth") }, onBoost = { sfx.playTone(900f, 110, "sine") })
            frame++
            val c = if (sim.t < 0f) kotlin.math.ceil(-sim.t).toInt() else 0
            if (hudCount != c) hudCount = c
            hudProg = sim.me.x / TRACK; hudRProg = sim.ai.x / TRACK; hudGap = sim.me.x - sim.ai.x
            if (sim.done) {
                if (sim.playerWon) { gems = DuelStore.recordWin(rivalIdx, sim.myTime, isPro); firstWin = DuelStore.lvl(rivalIdx) == 1; if (gems > 0) onCreditGems(gems); sfx.playTone(990f, 400, "sine") }
                phase = 1
                break
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        Canvas(
            modifier = Modifier.fillMaxSize()
                .pointerInput(runKey) { detectTapGestures(onTap = { sim.jump(); if (sim.me.airT > 0f) sfx.playTone(620f, 70, "sine") }) }
                .pointerInput(runKey) { var acc = 0f; detectVerticalDragGestures(onDragStart = { acc = 0f }, onVerticalDrag = { _, d -> acc += d; if (acc > 26f) { sim.slide(); acc = -9999f } }) }
        ) {
            if (frame < 0L) return@Canvas
            drawDuel(sim, rival, frames, rivalSprite, duelBg, gateImg, poseBoost)
        }

        // HUD
        Column(modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(top = 28.dp, start = 14.dp, end = 14.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("YOU", color = DCYAN, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                Text("GHOST DUEL", color = Color.White, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, fontSize = 14.sp)
                Text(rival.name.uppercase(), color = rival.color, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
            }
            Box(modifier = Modifier.fillMaxWidth().padding(top = 6.dp).height(8.dp).clip(RoundedCornerShape(4.dp)).background(Color.White.ca(0.14f))) {
                Box(modifier = Modifier.fillMaxWidth(hudRProg.coerceIn(0f, 1f)).fillMaxHeight().background(rival.color.ca(0.7f)))
                Box(modifier = Modifier.fillMaxWidth(hudProg.coerceIn(0f, 1f)).fillMaxHeight(0.5f).align(Alignment.BottomStart).background(DCYAN))
            }
            val g = hudGap
            Text(if (sim.t < 0f) "" else if (g >= 0f) "AHEAD ${"%.0f".format(g)}m" else "BEHIND ${"%.0f".format(-g)}m", color = if (g >= 0f) Color(0xFF69F0AE) else Color(0xFFFF8A80), fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, fontSize = 12.sp, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 4.dp))
        }
        Text("QUIT", color = Color.White.ca(0.8f), fontFamily = FontFamily.Monospace, fontSize = 12.sp,
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 14.dp, bottom = 22.dp).clip(RoundedCornerShape(8.dp)).background(Color.Black.ca(0.45f)).clickable { confirmQuit = true }.padding(horizontal = 12.dp, vertical = 6.dp))
        if (sim.t > 0f && sim.t < 5f && phase == 0) Text("TAP = JUMP   •   SWIPE DOWN = SLIDE", color = DGOLD, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, fontSize = 12.sp, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 26.dp))

        // countdown + taunt
        if (hudCount > 0 && phase == 0) {
            Column(modifier = Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Image(painter = BitmapPainter(vsImg), contentDescription = null, modifier = Modifier.size(width = 120.dp, height = 108.dp))
                Image(painter = BitmapPainter(ImageBitmap.imageResource(id = rival.portrait)), contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.size(width = 120.dp, height = 160.dp).clip(RoundedCornerShape(14.dp)).border(2.dp, rival.color, RoundedCornerShape(14.dp)))
                Text("$hudCount", color = Color.White, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, fontSize = 80.sp)
                Text("\"${rival.pre}\"", color = rival.color, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("— ${rival.name}", color = Color.White.ca(0.6f), fontSize = 11.sp)
            }
        } else if (sim.t in 0f..0.7f && phase == 0) {
            Text("GO!", color = DGOLD, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, fontSize = 64.sp, modifier = Modifier.align(Alignment.Center))
        }

        // result
        if (phase == 1) {
            val won = sim.playerWon
            val close = !won && sim.margin < 8f
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.ca(0.82f)), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(26.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                        if (won) {
                            Image(painter = BitmapPainter(poseThumb), contentDescription = null, modifier = Modifier.size(width = 100.dp, height = 150.dp))
                            Image(painter = BitmapPainter(trophyImg), contentDescription = null, modifier = Modifier.size(width = 100.dp, height = 110.dp))
                        } else {
                            Image(painter = BitmapPainter(poseSad), contentDescription = null, modifier = Modifier.size(width = 100.dp, height = 150.dp))
                            Image(painter = BitmapPainter(ImageBitmap.imageResource(id = rival.portrait)), contentDescription = null, contentScale = ContentScale.Crop,
                                modifier = Modifier.size(width = 72.dp, height = 96.dp).clip(RoundedCornerShape(12.dp)).border(2.dp, rival.color, RoundedCornerShape(12.dp)))
                        }
                    }
                    Text(if (won) "VICTORY" else "DEFEAT", color = if (won) DGOLD else Color(0xFFFF5C7A), fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, fontSize = 40.sp)
                    Text("\"${if (won) rival.lose else rival.win}\"", color = rival.color, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.padding(top = 4.dp))
                    Text("— ${rival.name}", color = Color.White.ca(0.6f), fontSize = 11.sp)
                    Text(if (won) "Time ${"%.1f".format(sim.myTime)}s  •  won by ${"%.0f".format(sim.margin)}m" else if (close) "SO CLOSE! Lost by only ${"%.0f".format(sim.margin)}m" else "Lost by ${"%.0f".format(sim.margin)}m", color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp))
                    if (won) Text((if (firstWin) "FIRST WIN BONUS  " else "") + "+$gems 💎", color = DGOLD, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, fontSize = 22.sp, modifier = Modifier.padding(top = 8.dp))
                    if (won && rivalIdx + 1 < RIVALS.size && DuelStore.beaten(rivalIdx) && firstWin) Text("🔓 ${RIVALS[rivalIdx + 1].name} unlocked!", color = Color(0xFF69F0AE), fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                    if (won && DuelStore.lvl(rivalIdx) >= 2) Text("⬆ ${rival.name} is now LEVEL ${DuelStore.lvl(rivalIdx) + 1}" + (if (DuelStore.lvl(rivalIdx) % 5 == 0) "  •  MILESTONE +100 💎" else ""), color = Color(0xFF69F0AE), fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                    if (won && gems > 0 && !doubled) {
                        Text(AdMobManager.rewardedLabel(RewardedSlot.STORM, "🎬 WATCH AD: DOUBLE TO +${gems * 2} 💎"), color = Color.Black, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, fontSize = 13.sp,
                            modifier = Modifier.padding(top = 14.dp).clip(RoundedCornerShape(24.dp)).background(Color(0xFF4CAF50))
                                .clickable { activity?.let { a -> AdMobManager.showRewardedIfReady(a, RewardedSlot.STORM) { doubled = true; onCreditGems(gems) } } }.padding(horizontal = 20.dp, vertical = 10.dp))
                    } else if (doubled) Text("DOUBLED! +$gems 💎 more", color = Color(0xFF69F0AE), fontFamily = FontFamily.Monospace, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
                    val left = remember(phase, tick) { DuelStore.left(isPro) }
                    val adLeft = remember(phase, tick) { DuelStore.adLeft() }
                    Row(modifier = Modifier.padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("DONE", color = Color.White, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, fontSize = 14.sp,
                            modifier = Modifier.clip(RoundedCornerShape(24.dp)).border(1.dp, Color.White.ca(0.6f), RoundedCornerShape(24.dp)).clickable { onExit() }.padding(horizontal = 24.dp, vertical = 12.dp))
                        Text(if (left > 0) (if (close) "REMATCH!" else "REMATCH ($left left)") else "NO DUELS LEFT", color = Color.Black, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, fontSize = 13.sp,
                            modifier = Modifier.clip(RoundedCornerShape(24.dp)).background(if (left > 0) Brush.horizontalGradient(listOf(rival.color, DGOLD)) else Brush.horizontalGradient(listOf(Color.Gray, Color.Gray)))
                                .clickable(enabled = left > 0) { DuelStore.useAttempt(); GhostDuelHost.runKey += 1 }.padding(horizontal = 22.dp, vertical = 12.dp))
                    }
                    if (left <= 0 && adLeft > 0) {
                        Text(AdMobManager.rewardedLabel(RewardedSlot.STORM, "🎬 WATCH AD: FREE REMATCH ($adLeft left today)"), color = Color.White, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, fontSize = 13.sp,
                            modifier = Modifier.padding(top = 12.dp).clip(RoundedCornerShape(24.dp)).border(1.5.dp, Color(0xFF4CAF50), RoundedCornerShape(24.dp))
                                .clickable { activity?.let { a -> AdMobManager.showRewardedIfReady(a, RewardedSlot.STORM) { DuelStore.grantExtra(); DuelStore.useAttempt(); GhostDuelHost.runKey += 1 } } }.padding(horizontal = 20.dp, vertical = 10.dp))
                    }
                    if (msg.isNotEmpty()) Text(msg, color = DGOLD, fontSize = 11.sp)
                }
            }
        }
        if (confirmQuit) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.ca(0.75f)).clickable { }, contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(30.dp)) {
                    PilotPose(Pose.SAD, 90.dp)
                    Text("Forfeit the duel?", color = Color.White, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, fontSize = 20.sp)
                    Text("The duel is lost and used up.", color = Color.White.ca(0.75f), fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                    Row(modifier = Modifier.padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("STAY", color = Color.Black, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, modifier = Modifier.clip(RoundedCornerShape(22.dp)).background(DCYAN).clickable { confirmQuit = false }.padding(horizontal = 28.dp, vertical = 11.dp))
                        Text("FORFEIT", color = Color.White, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, modifier = Modifier.clip(RoundedCornerShape(22.dp)).border(1.dp, Color.White.ca(0.6f), RoundedCornerShape(22.dp)).clickable { onExit() }.padding(horizontal = 28.dp, vertical = 11.dp))
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------------------------------------------ drawing

private fun hz(i: Int): Float { val x = sin(i * 12.9898f) * 43758.547f; return x - kotlin.math.floor(x) }   // cheap deterministic 0..1

private fun DrawScope.drawDuel(sim: DuelSim, rival: Rival, frames: List<ImageBitmap>, sprite: ImageBitmap?, arena: ImageBitmap, gate: ImageBitmap, boostPose: ImageBitmap) {
    val w = size.width; val h = size.height
    val t = sim.t
    val shx = sin(t * 80f) * sim.shake * 8f
    val shy = kotlin.math.cos(t * 67f) * sim.shake * 5f
    val boostK = (sim.me.boost / 0.4f).coerceIn(0f, 1f)
    val zoom = 1f + 0.035f * boostK + 0.02f * sim.shake
    withTransform({ translate(shx, shy); scale(zoom, zoom, Offset(w * 0.3f, h * 0.6f)) }) {
        // arena: the stadium art (cover-fit, slightly oversized) pans slowly as the race advances
        drawRect(Color(0xFF05061A))
        val asc = max(w / arena.width, h / arena.height) * 1.10f
        val adw = arena.width * asc; val adh = arena.height * asc
        val pan = -(sim.me.x / TRACK).coerceIn(0f, 1f) * w * 0.045f
        drawImage(arena, srcSize = IntSize(arena.width, arena.height), dstOffset = IntOffset(((w - adw) / 2f + pan).roundToInt(), ((h - adh) / 2f).roundToInt()), dstSize = IntSize(adw.roundToInt(), adh.roundToInt()), alpha = 0.85f)
        drawRect(Brush.verticalGradient(listOf(Color(0x66000010), Color(0x99000010), Color(0xCC05061A))))
        drawRect(Color(0xFF00E5FF).ca(0.04f + 0.03f * sin(t * 1.7f)), blendMode = BlendMode.Plus)
        // sweeping floodlight beams
        for (i in 0 until 3) {
            val sway = sin(t * 0.5f + i * 2.1f) * w * 0.12f
            val bx = w * (0.18f + 0.32f * i) + sway
            val p = androidx.compose.ui.graphics.Path().apply { moveTo(bx - 6f, 0f); lineTo(bx + 6f, 0f); lineTo(bx + w * 0.16f + sway * 0.3f, h * 0.58f); lineTo(bx - w * 0.16f + sway * 0.3f, h * 0.58f); close() }
            drawPath(p, Brush.verticalGradient(listOf((if (i % 2 == 0) Color(0xFF9FE9FF) else Color(0xFFFFB0F0)).ca(0.10f), Color.Transparent), endY = h * 0.58f), blendMode = BlendMode.Plus)
        }
        // camera flashes in the stands
        for (i in 0 until 16) {
            val ph = (t * 0.8f + i * 0.37f) % 1f
            if (ph < 0.07f) drawCircle(Color.White.ca(1f - ph / 0.07f), radius = 3f + 5f * (1f - ph / 0.07f), center = Offset(hz(i * 3 + 1) * w, h * (0.06f + 0.18f * hz(i * 5 + 2))), blendMode = BlendMode.Plus)
        }
        lane(sim, sim.ai, rival.color, true, h * 0.38f, frames, w, h, t, rival, sprite, gate, boostPose)
        lane(sim, sim.me, DCYAN, false, h * 0.80f, frames, w, h, t, rival, null, gate, boostPose)
        // divider
        drawRect(Brush.horizontalGradient(listOf(Color.Transparent, DMAG.ca(0.8f), DCYAN.ca(0.8f), Color.Transparent)), topLeft = Offset(0f, h * 0.575f), size = Size(w, 3f))
        // speed streaks (stronger with speed / boost) and drifting motes
        val spd = (sim.me.v / BASE_SPEED).coerceIn(0f, 1.8f)
        for (i in 0 until 14) {
            val y = h * (0.10f + 0.80f * hz(i * 7 + 3))
            val len = w * (0.06f + 0.10f * hz(i + 40)) * spd
            val x = w - ((t * (0.9f + hz(i + 9)) * w * 0.9f * spd + hz(i + 20) * w) % (w * 1.3f)) + w * 0.1f
            drawLine(Color.White.ca(0.05f + 0.12f * boostK + 0.03f * spd), Offset(x, y), Offset(x + len, y), strokeWidth = 1.5f, cap = StrokeCap.Round)
        }
        for (i in 0 until 22) {
            val x = (hz(i * 11) * w - t * w * (0.05f + 0.08f * hz(i + 3)) * spd * 3f).let { ((it % w) + w) % w }
            val y = h * hz(i * 13 + 5) + sin(t * 0.9f + i) * 10f
            drawCircle(Color.White.ca(0.10f + 0.10f * hz(i + 60)), radius = 1.2f + 1.6f * hz(i + 70), center = Offset(x, y), blendMode = BlendMode.Plus)
        }
    }
    if (sim.flashRed > 0f) {
        drawRect(Color(0xFFFF2040).ca(sim.flashRed * 0.14f))
        drawRect(Brush.radialGradient(listOf(Color.Transparent, Color(0xFFFF1030).ca(sim.flashRed * 0.55f)), center = Offset(w / 2f, h / 2f), radius = max(w, h) * 0.75f))
    }
}

private fun DrawScope.lane(sim: DuelSim, r: Runner, col: Color, isRival: Boolean, groundY: Float, frames: List<ImageBitmap>, w: Float, h: Float, t: Float, rival: Rival, sprite: ImageBitmap?, gate: ImageBitmap, boostPose: ImageBitmap) {
    val unit = w * UNIT_W
    val px = w * PX
    val stripH = h * 0.09f
    val paint = android.graphics.Paint().apply { isAntiAlias = true; textAlign = android.graphics.Paint.Align.CENTER; typeface = android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD) }
    // floor strip: glossy track, scrolling light strips, moving dashes
    drawRect(Brush.verticalGradient(listOf(col.ca(0.34f), col.ca(0.08f)), startY = groundY, endY = groundY + stripH), topLeft = Offset(0f, groundY), size = Size(w, stripH))
    for (i in -2..14) {
        val gx = px + ((i * 6f) - (r.x % 6f)) * unit
        drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Color.White.ca(0.07f), Color.Transparent), startX = gx, endX = gx + unit * 3f), topLeft = Offset(gx, groundY), size = Size(unit * 3f, stripH))
    }
    drawRect(col.ca(0.9f), topLeft = Offset(0f, groundY), size = Size(w, 3f))
    for (i in -2..24) {
        val gx = px + ((i * 3f) - (r.x % 3f)) * unit
        drawRect(col.ca(0.22f), topLeft = Offset(gx, groundY + h * 0.03f), size = Size(unit * 1.4f, 3f))
    }
    // distance posts
    for (m in 1..5) {
        val dx = px + (m * 150f - r.x) * unit
        if (dx < -unit * 2f || dx > w + unit * 2f) continue
        drawLine(col.ca(0.8f), Offset(dx, groundY), Offset(dx, groundY - h * 0.05f), strokeWidth = 3f, cap = StrokeCap.Round)
        drawCircle(col.ca(0.5f), radius = 7f, center = Offset(dx, groundY - h * 0.05f), blendMode = BlendMode.Plus)
        paint.color = Color.White.ca(0.75f).toArgb(); paint.textSize = w * 0.026f
        drawContext.canvas.nativeCanvas.drawText("${(TRACK - m * 150f).toInt()}m", dx, groundY - h * 0.058f, paint)
    }
    // finish gate: the arch you run through (perspective: it grows as it nears), checkered line, light shafts
    val fx = px + (TRACK - r.x) * unit
    if (fx < w * 1.3f && fx > -w * 0.5f) {
        val near = 1f - (((fx - px) / w).coerceIn(0f, 1.2f)) / 1.2f
        val gs = 0.72f + 0.28f * near
        val gh = h * 0.21f * gs; val gw = gh * gate.width.toFloat() / gate.height.toFloat()
        // checkered finish line across the floor
        val sq = unit * 0.36f; val rows = 3; val rh = stripH / rows
        for (rw in 0 until rows) for (cc in 0 until 4) {
            if ((rw + cc) % 2 == 0) drawRect(Color.White.ca(0.85f), topLeft = Offset(fx - sq * 2f + cc * sq, groundY + rw * rh), size = Size(sq, rh))
            else drawRect(Color.Black.ca(0.6f), topLeft = Offset(fx - sq * 2f + cc * sq, groundY + rw * rh), size = Size(sq, rh))
        }
        // glow pool on the floor + beams to the sky
        drawOval(Brush.radialGradient(listOf(DGOLD.ca(0.45f), Color.Transparent), center = Offset(fx, groundY + stripH * 0.4f), radius = gw * 0.6f), topLeft = Offset(fx - gw * 0.6f, groundY - 6f), size = Size(gw * 1.2f, stripH * 1.1f), blendMode = BlendMode.Plus)
        for (sd in intArrayOf(-1, 1)) {
            val bx = fx + sd * gw * 0.42f
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, DGOLD.ca(0.30f + 0.08f * sin(t * 6f + sd))), startY = groundY - gh * 1.5f, endY = groundY), topLeft = Offset(bx - unit * 0.14f, groundY - gh * 1.5f), size = Size(unit * 0.28f, gh * 1.5f), blendMode = BlendMode.Plus)
        }
        drawImage(gate, srcSize = IntSize(gate.width, gate.height), dstOffset = IntOffset((fx - gw / 2f).roundToInt(), (groundY - gh + gh * 0.02f).roundToInt()), dstSize = IntSize(gw.roundToInt(), gh.roundToInt()))
    }
    // obstacles
    for (o in sim.obs) {
        val ox = px + (o.pos - r.x) * unit
        if (ox < -unit * 3f || ox > w + unit * 3f) continue
        when (o.type) {
            0 -> { // hurdle: jump it. two posts, a striped bar, a blinking lamp, a floor shadow
                val bw = unit * 1.6f; val bh = h * 0.062f; val th = bh * 0.42f
                drawOval(Color.Black.ca(0.35f), topLeft = Offset(ox - bw * 0.6f, groundY - 3f), size = Size(bw * 1.2f, 9f))
                for (sd in intArrayOf(-1, 1)) drawRect(Brush.verticalGradient(listOf(Color(0xFF8A94A8), Color(0xFF39404F))), topLeft = Offset(ox + sd * bw * 0.42f - unit * 0.07f, groundY - bh), size = Size(unit * 0.14f, bh))
                val by0 = groundY - bh
                drawRoundRect(Color(0xFFFFFFFF), topLeft = Offset(ox - bw / 2f, by0 - th * 0.2f), size = Size(bw, th), cornerRadius = CornerRadius(5f, 5f))
                for (k in 0 until 8 step 2) drawRect(Color(0xFFFF5C00), topLeft = Offset(ox - bw / 2f + bw * k / 8f + 2f, by0 - th * 0.2f), size = Size(bw / 8f, th))
                drawRoundRect(Color.White.ca(0.7f), topLeft = Offset(ox - bw / 2f, by0 - th * 0.2f), size = Size(bw, th), cornerRadius = CornerRadius(5f, 5f), style = Stroke(width = 1.6f))
                val blink = if (((t * 4f).toInt() + (o.pos.toInt())) % 2 == 0) 1f else 0.35f
                drawCircle(Color(0xFFFF3B30).ca(blink), radius = 5f, center = Offset(ox, by0 - th * 0.2f - 6f))
                drawCircle(Brush.radialGradient(listOf(Color(0x88FF3B30), Color.Transparent), center = Offset(ox, by0 - th * 0.2f - 6f), radius = 26f), radius = 26f, center = Offset(ox, by0 - th * 0.2f - 6f), blendMode = BlendMode.Plus)
                drawCircle(Brush.radialGradient(listOf(Color(0x44FF8A00), Color.Transparent), center = Offset(ox, groundY - bh / 2f), radius = bw), radius = bw, center = Offset(ox, groundY - bh / 2f), blendMode = BlendMode.Plus)
            }
            1 -> { // overhead laser beam on two pylons: slide under it
                val bw = unit * 2.2f; val by = groundY - h * 0.14f
                val jit = 6.5f + 2f * sin(t * 31f + o.pos)
                for (sd in intArrayOf(-1, 1)) {
                    val pxx = ox + sd * bw / 2f
                    drawRect(Brush.verticalGradient(listOf(Color(0xFF6C7488), Color(0xFF2A3040))), topLeft = Offset(pxx - unit * 0.08f, by - 8f), size = Size(unit * 0.16f, groundY - by + 8f))
                    drawCircle(DMAG, radius = 8f, center = Offset(pxx, by)); drawCircle(Color.White, radius = 3.5f, center = Offset(pxx, by))
                    drawOval(Brush.radialGradient(listOf(DMAG.ca(0.45f), Color.Transparent), center = Offset(pxx, groundY + 3f), radius = unit * 0.9f), topLeft = Offset(pxx - unit * 0.9f, groundY - 4f), size = Size(unit * 1.8f, 11f), blendMode = BlendMode.Plus)
                }
                drawRect(DMAG.ca(0.14f), topLeft = Offset(ox - bw / 2f, by - h * 0.03f), size = Size(bw, h * 0.03f + 4f))
                drawLine(DMAG, Offset(ox - bw / 2f, by), Offset(ox + bw / 2f, by), strokeWidth = jit, cap = StrokeCap.Round)
                drawLine(Color.White, Offset(ox - bw / 2f, by), Offset(ox + bw / 2f, by), strokeWidth = 2.5f, cap = StrokeCap.Round)
                for (k in 0 until 3) { val sp = ((t * 2.2f + k * 0.33f + o.pos) % 1f); drawCircle(Color.White.ca(1f - sp), radius = 2.6f, center = Offset(ox - bw / 2f + bw * sp, by), blendMode = BlendMode.Plus) }
                drawCircle(Brush.radialGradient(listOf(Color(0x66FF4DD2), Color.Transparent), center = Offset(ox, by), radius = bw), radius = bw, center = Offset(ox, by), blendMode = BlendMode.Plus)
            }
            else -> { // boost pad: glowing panel with chevrons that run forward
                val bw = unit * 2.4f; val pulse = 0.6f + 0.4f * sin(t * 8f + o.pos)
                drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Color(0xFF69F0AE).ca(0.35f * pulse), Color.Transparent), startX = ox - bw * 0.7f, endX = ox + bw * 0.7f), topLeft = Offset(ox - bw * 0.7f, groundY), size = Size(bw * 1.4f, stripH * 0.8f), blendMode = BlendMode.Plus)
                for (c in 0 until 3) {
                    val ph = ((t * 1.6f + c / 3f) % 1f)
                    val cx = ox - bw / 2f + ph * bw
                    val a = sin(ph * 3.14159f)
                    val p = Path2(cx, groundY + stripH * 0.35f, bw / 8f, h * 0.022f)
                    drawLine(Color(0xFF69F0AE).ca(a), Offset(p.x, p.y - p.h), Offset(p.x + p.w, p.y), strokeWidth = 6f, cap = StrokeCap.Round)
                    drawLine(Color(0xFF69F0AE).ca(a), Offset(p.x, p.y + p.h), Offset(p.x + p.w, p.y), strokeWidth = 6f, cap = StrokeCap.Round)
                }
                drawCircle(Brush.radialGradient(listOf(Color(0x5569F0AE), Color.Transparent), center = Offset(ox, groundY), radius = bw), radius = bw, center = Offset(ox, groundY), blendMode = BlendMode.Plus)
            }
        }
    }
    // ------------------------------------------------ runner
    val ph = h * 0.16f
    val useSprite = isRival && sprite != null
    val img = if (useSprite) sprite!! else if (r.boost > 0f && !isRival) boostPose else frames[((t * (6f + r.v * 0.5f)).toInt().coerceAtLeast(0)) % frames.size]
    val pw = ph * img.width.toFloat() / img.height.toFloat()
    val jf = if (r.airT > 0f) 1f - r.airT / AIR else 0f
    val air = if (r.airT > 0f) sin(jf * 3.14159f) else 0f
    val sliding = r.slideT > 0f
    val grounded = r.airT <= 0f && !sliding
    val bob = if (useSprite && grounded) abs(sin(t * (5f + r.v * 0.35f))) * ph * 0.05f else 0f   // a single running pose: bounce it like a stride
    val jumpY = air * h * 0.14f + bob
    val st = r.stumble > 0f
    // squash and stretch
    var sx = 1f; var sy = 1f
    if (sliding) { val e = (1f - r.slideT / SLIDE); val dip = min(1f, e / 0.15f); sy = 1f - 0.42f * dip; sx = 1f + 0.12f * dip }
    else if (r.airT > 0f) { sx = 1f - 0.08f * air; sy = 1f + 0.12f * air }
    if (r.sJump < 0.08f) { val k = 1f - r.sJump / 0.08f; sx *= 1f + 0.14f * k; sy *= 1f - 0.14f * k }
    if (r.sLand < 0.18f && r.airT <= 0f) { val k = sin((1f - r.sLand / 0.18f) * 3.14159f); sx *= 1f + 0.22f * k; sy *= 1f - 0.22f * k }
    // lean
    var rot = if (sliding) -22f else if (r.airT > 0f) -12f + 26f * jf else if (grounded) 3f else 0f
    var recoilX = 0f
    if (st) { val k = (r.stumble / 0.9f).coerceIn(0f, 1f); rot = sin(r.sHit * 30f) * 11f * k - 6f * k; recoilX = -w * 0.035f * (if (r.sHit < 0.3f) (1f - r.sHit / 0.3f) else 0f) }
    val cx0 = px + recoilX
    val cy = groundY - ph * 0.5f * sy - jumpY
    // floor shadow
    drawOval(Color.Black.ca(0.38f * (1f - air * 0.65f)), topLeft = Offset(cx0 - pw * 0.32f * (1f - air * 0.3f), groundY - 4f), size = Size(pw * 0.64f * (1f - air * 0.3f), 9f))
    // boost speed lines
    if (r.boost > 0f) for (i in 0 until 6) {
        val ly = cy - ph * 0.35f + i * ph * 0.14f
        drawLine(Color(0xFF69F0AE).ca(0.5f), Offset(cx0 - pw * 0.5f, ly), Offset(cx0 - pw * 0.5f - w * 0.16f * (0.5f + (i % 3) * 0.25f), ly), strokeWidth = 3f, cap = StrokeCap.Round)
    }
    // running dust behind the heels
    if (grounded && !st) for (i in 0 until 4) {
        val ph2 = (t * 3.2f + i * 0.25f) % 1f
        drawCircle(Color.White.ca(0.20f * (1f - ph2)), radius = 3f + 8f * ph2, center = Offset(cx0 - pw * 0.25f - ph2 * pw * 0.9f, groundY - 4f - ph2 * ph * 0.10f), blendMode = BlendMode.Plus)
    }
    // slide: sparks and a long dust skid
    if (sliding) {
        for (i in 0 until 7) {
            val ph2 = (r.sSlide * 3.5f + i * 0.14f) % 1f
            drawCircle(Color(0xFFFFB74D).ca(1f - ph2), radius = 2.4f * (1f - ph2) + 0.8f, center = Offset(cx0 - pw * 0.15f - ph2 * pw * 0.9f, groundY - 3f - hz(i) * 12f * ph2), blendMode = BlendMode.Plus)
            drawCircle(Color.White.ca(0.22f * (1f - ph2)), radius = 5f + 11f * ph2, center = Offset(cx0 - pw * 0.2f - ph2 * pw * 1.3f, groundY - 5f), blendMode = BlendMode.Plus)
        }
        drawLine(Color.White.ca(0.35f), Offset(cx0 - pw * 0.2f, groundY - 2f), Offset(cx0 - pw * 1.4f, groundY - 2f), strokeWidth = 3f, cap = StrokeCap.Round)
    }
    // takeoff puff and landing shock ring
    if (r.sJump < 0.4f) for (i in 0 until 5) {
        val u = r.sJump / 0.4f; val dir = (i - 2) * 0.5f
        drawCircle(Color.White.ca(0.35f * (1f - u)), radius = 4f + 9f * u, center = Offset(cx0 + dir * pw * 0.5f * (0.4f + u), groundY - 4f - u * 8f), blendMode = BlendMode.Plus)
    }
    if (r.sLand < 0.45f) {
        val u = r.sLand / 0.45f
        drawOval(col.ca(0.55f * (1f - u)), topLeft = Offset(cx0 - pw * (0.3f + 0.9f * u), groundY - 4f - 4f * u), size = Size(pw * (0.6f + 1.8f * u), 8f + 10f * u), style = Stroke(width = 2.5f * (1f - u) + 0.8f), blendMode = BlendMode.Plus)
        for (i in 0 until 6) { val dir = if (i % 2 == 0) 1f else -1f; drawCircle(Color.White.ca(0.30f * (1f - u)), radius = 3f + 5f * u, center = Offset(cx0 + dir * pw * (0.2f + 0.7f * u) * (0.5f + 0.2f * i), groundY - 5f - u * 10f), blendMode = BlendMode.Plus) }
    }
    drawCircle(Brush.radialGradient(listOf(col.ca(0.35f), Color.Transparent), center = Offset(cx0, cy), radius = ph * 0.7f), radius = ph * 0.7f, center = Offset(cx0, cy), blendMode = BlendMode.Plus)

    fun body(ox: Float, jy: Float, a: Float, rt: Float, ghost: Boolean) {
        val piv = Offset(cx0 + ox, groundY - jy)
        withTransform({ rotate(rt, piv); scale(sx, sy, piv) }) {
            val flick = if (st && !ghost) (if ((r.sHit * 16f).toInt() % 2 == 0) 0.5f else 1f) else 1f
            drawImage(img, srcSize = IntSize(img.width, img.height), dstOffset = IntOffset((cx0 + ox - pw / 2f).roundToInt(), (groundY - ph - jy).roundToInt()), dstSize = IntSize(pw.roundToInt(), ph.roundToInt()),
                alpha = a * flick * (if (isRival) 0.96f else 1f),
                colorFilter = if (ghost) ColorFilter.tint(col.ca(0.8f), BlendMode.SrcAtop) else if (isRival && !useSprite) ColorFilter.tint(col.ca(0.40f), BlendMode.SrcAtop) else null,
                blendMode = if (ghost) BlendMode.Plus else BlendMode.SrcOver)
            if (st && !ghost) drawImage(img, srcSize = IntSize(img.width, img.height), dstOffset = IntOffset((cx0 + ox - pw / 2f).roundToInt(), (groundY - ph - jy).roundToInt()), dstSize = IntSize(pw.roundToInt(), ph.roundToInt()), alpha = 0.4f * (r.stumble / 0.9f).coerceIn(0f, 1f), colorFilter = ColorFilter.tint(Color.Red, BlendMode.SrcAtop))
        }
    }
    // afterimages while airborne
    if (r.airT > 0f) for (i in 2 downTo 1) {
        val jf2 = (jf - 0.07f * i).coerceAtLeast(0f)
        body(-pw * 0.28f * i, sin(jf2 * 3.14159f) * h * 0.14f, 0.22f / i, rot - 4f * i, true)
    }
    body(0f, jumpY, 1f, rot, false)
    // hit: impact burst, ring, dizzy stars
    if (st && r.sHit < 0.9f) {
        val u = (r.sHit / 0.35f).coerceIn(0f, 1f)
        val hx = cx0 + pw * 0.38f; val hy = cy
        if (r.sHit < 0.35f) {
            drawCircle(Color.White.ca(0.9f * (1f - u)), radius = ph * (0.12f + 0.40f * u), center = Offset(hx, hy), style = Stroke(width = 5f * (1f - u) + 1f), blendMode = BlendMode.Plus)
            for (i in 0 until 9) {
                val ang = i * 0.698f; val r0 = ph * (0.10f + 0.34f * u); val r1 = r0 + ph * 0.18f * (1f - u)
                drawLine(Color(0xFFFFD54F).ca(1f - u), Offset(hx + kotlin.math.cos(ang) * r0, hy + sin(ang) * r0), Offset(hx + kotlin.math.cos(ang) * r1, hy + sin(ang) * r1), strokeWidth = 4f, cap = StrokeCap.Round, blendMode = BlendMode.Plus)
            }
        }
        if (r.sHit > 0.12f) for (i in 0 until 3) {
            val ang = r.sHit * 9f + i * 2.094f
            drawCircle(Color(0xFFFFE082).ca((1f - r.sHit / 0.9f) * 0.95f), radius = 5f, center = Offset(cx0 + kotlin.math.cos(ang) * pw * 0.3f, groundY - ph - jumpY + ph * 0.08f + sin(ang) * 7f), blendMode = BlendMode.Plus)
        }
    }
    // finish celebration: confetti and a flash
    if (r.finishT >= 0f) {
        val since = t - r.finishT
        if (since in 0f..2.6f) {
            val u = since / 2.6f
            for (i in 0 until 46) {
                val cxp = cx0 + (hz(i * 3) - 0.35f) * w * 0.55f
                val cyp = groundY - h * 0.30f + (since * (0.12f + 0.22f * hz(i + 90)) * h) - 30f * (1f - u) + sin(since * 6f + i) * 6f
                val cc = when (i % 4) { 0 -> DGOLD; 1 -> DCYAN; 2 -> DMAG; else -> Color.White }
                drawRect(cc.ca(1f - u * u), topLeft = Offset(cxp, cyp), size = Size(5f, 8f), blendMode = BlendMode.Plus)
            }
            if (since < 0.25f) drawRect(Color.White.ca(0.25f * (1f - since / 0.25f)), topLeft = Offset(0f, groundY - h * 0.28f), size = Size(w, h * 0.34f), blendMode = BlendMode.Plus)
        }
    }
    if (isRival) {
        paint.color = col.toArgb(); paint.textSize = w * 0.032f
        drawContext.canvas.nativeCanvas.drawText(rival.name, cx0, groundY - ph - jumpY - 10f, paint)
    }
    if (!isRival && sim.slip) drawCircle(DGOLD.ca(0.5f), radius = ph * 0.58f, center = Offset(cx0, cy), style = Stroke(width = 3f))
}

private class Path2(val x: Float, val y: Float, val w: Float, val h: Float)

private fun Color.ca(a: Float): Color = copy(alpha = if (a.isNaN()) 0f else a.coerceIn(0f, 1f))
