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
private const val AIR = 0.46f               // seconds a jump lasts
private const val SLIDE = 0.5f
private const val CD = 0.22f                // cooldown after a jump / slide
private val DGOLD = Color(0xFFFFD166)
private val DCYAN = Color(0xFF3DE8FF)
private val DMAG = Color(0xFFFF4DD2)

class Rival(val name: String, val title: String, val color: Color, val skill: Float, val speedMul: Float,
            val pre: String, val win: String, val lose: String, val firstWinGems: Int, val density: Float, val portrait: Int, val sprite: Int = 0)

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
    fun beaten(i: Int) = gi("beat$i") == 1
    fun unlocked(i: Int) = i == 0 || beaten(i - 1)
    fun bestTime(i: Int) = gi("best$i", 0) / 100f
    fun wins() = RIVALS.indices.sumOf { gi("wins$it") }
    /** returns gems earned */
    fun recordWin(i: Int, time: Float, isPro: Boolean): Int {
        val first = !beaten(i)
        pi("beat$i", 1); pi("wins$i", gi("wins$i") + 1)
        val t100 = (time * 100).toInt()
        if (gi("best$i", 0) == 0 || t100 < gi("best$i", 0)) pi("best$i", t100)
        val base = if (first) RIVALS[i].firstWinGems else 10
        return (base * (if (isPro) 1.25f else 1f)).roundToInt()
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
            p += (14f - 3.5f * (rival.density - 1f) * 3f).coerceAtLeast(10.5f) + r.nextFloat() * 6f
        }
        val rr = Random(seed * 13 + 5)
        for (o in obs) o.rOk = o.type == 2 || rr.nextFloat() < rival.skill
    }

    fun jump() { if (t < 0f || me.airT > 0f || me.cd > 0f || done) return; me.airT = AIR; me.slideT = 0f }
    fun slide() { if (t < 0f || me.airT > 0f || me.slideT > 0f || me.cd > 0f || done) return; me.slideT = SLIDE }

    fun step(dt: Float, onHit: () -> Unit, onBoost: () -> Unit) {
        t += dt
        shake = max(0f, shake - dt * 3f); flashRed = max(0f, flashRed - dt * 3f)
        if (t < 0f || done) return
        // --- me
        run {
            val m = me
            if (m.airT > 0f) { m.airT -= dt; if (m.airT <= 0f) { m.airT = 0f; m.cd = CD } }
            if (m.slideT > 0f) { m.slideT -= dt; if (m.slideT <= 0f) { m.slideT = 0f; m.cd = CD } }
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
                    0 -> if (m.airT <= 0.02f) { m.stumble = 0.9f; m.hits++; shake = 1f; flashRed = 1f; onHit() }
                    1 -> if (m.slideT <= 0f) { m.stumble = 0.9f; m.hits++; shake = 1f; flashRed = 1f; onHit() }
                    else -> { m.boost = 1.6f; onBoost() }
                }
            }
            if (m.x >= TRACK && m.finishT < 0f) m.finishT = t
        }
        // --- rival: same course, pre-rolled mistakes, gentle rubber band so the race stays close
        run {
            val a = ai
            a.stumble = max(0f, a.stumble - dt); a.boost = max(0f, a.boost - dt)
            val gap = a.x - me.x
            val rb = if (gap > 40f) 0.96f else if (gap < -40f) 1.04f else 1f
            val target = (a.base + (if (a.boost > 0f) 6f else 0f)) * (if (a.stumble > 0f) 0.42f else 1f) * rb
            a.v += (target - a.v) * min(1f, dt * 4f)
            val prev = a.x; a.x += a.v * dt
            for (o in obs) {
                if (o.rDone || o.pos > a.x) continue
                if (o.pos < prev - 3f) { o.rDone = true; continue }
                o.rDone = true
                if (o.type == 2) a.boost = 1.6f else if (!o.rOk) { a.stumble = 0.9f; a.hits++ }
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
            Text("👻 GHOST DUEL", fontSize = 18.sp, fontWeight = FontWeight.Black, color = CyberPrimary, fontFamily = FontFamily.Monospace)
        }
        Text("A head-to-head sprint. Jump the barriers, slide under the beams, hit the boost pads, and cross the line before your rival.", color = CyberOnSurface.copy(alpha = 0.75f), fontSize = 12.sp, modifier = Modifier.padding(bottom = 4.dp))
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
                    .background(CyberSurface).border(1.5.dp, if (unlocked) r.color.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.12f), RoundedCornerShape(12.dp)).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.size(width = 60.dp, height = 80.dp).clip(RoundedCornerShape(10.dp)).border(1.5.dp, r.color.copy(alpha = if (unlocked) 0.9f else 0.2f), RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                    Image(painter = BitmapPainter(ImageBitmap.imageResource(id = r.portrait)), contentDescription = r.name, contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(), alpha = if (unlocked) 1f else 0.18f)
                    if (!unlocked) Text("\uD83D\uDD12", fontSize = 22.sp)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(r.name, color = if (unlocked) Color.White else Color.White.copy(alpha = 0.4f), fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, fontSize = 16.sp)
                    Text(r.title + (if (beaten) "  •  ✓ BEATEN  best ${"%.1f".format(DuelStore.bestTime(i))}s" else ""), color = if (beaten) Color(0xFF69F0AE) else CyberOnSurface.copy(alpha = 0.6f), fontSize = 11.sp)
                    Text(if (!unlocked) "Beat ${RIVALS[i - 1].name} to unlock" else if (beaten) "Win again: 10 💎" else "First win: ${r.firstWinGems} 💎", color = DGOLD, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
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
        Text("More rivals coming soon…", color = Color.White.copy(alpha = 0.35f), fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
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
    val rival = RIVALS[rivalIdx]
    val sim = remember(runKey) { DuelSim(rival, StormModes.dayKey() * 10 + rivalIdx + runKey) }
    val sfx = remember { NeonSoundEngine() }
    val activity = LocalContext.current.findActivity()
    val frames = listOf(R.drawable.pilot_run_1, R.drawable.pilot_run_2, R.drawable.pilot_run_3, R.drawable.pilot_run_4, R.drawable.pilot_run_5, R.drawable.pilot_run_6)
        .map { ImageBitmap.imageResource(id = it) }

    val duelBg = ImageBitmap.imageResource(id = R.drawable.duel_bg)
    val gateImg = ImageBitmap.imageResource(id = R.drawable.duel_gate)
    val vsImg = ImageBitmap.imageResource(id = R.drawable.duel_vs)
    val trophyImg = ImageBitmap.imageResource(id = R.drawable.duel_trophy)
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
                if (sim.playerWon) { gems = DuelStore.recordWin(rivalIdx, sim.myTime, isPro); firstWin = gems > 0 && gems >= (RIVALS[rivalIdx].firstWinGems); if (gems > 0) onCreditGems(gems); sfx.playTone(990f, 400, "sine") }
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
            drawDuel(sim, rival, frames, rivalSprite, duelBg, gateImg)
        }

        // HUD
        Column(modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(top = 28.dp, start = 14.dp, end = 14.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("YOU", color = DCYAN, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                Text("GHOST DUEL", color = Color.White, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, fontSize = 14.sp)
                Text(rival.name.uppercase(), color = rival.color, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
            }
            Box(modifier = Modifier.fillMaxWidth().padding(top = 6.dp).height(8.dp).clip(RoundedCornerShape(4.dp)).background(Color.White.copy(alpha = 0.14f))) {
                Box(modifier = Modifier.fillMaxWidth(hudRProg.coerceIn(0f, 1f)).fillMaxHeight().background(rival.color.copy(alpha = 0.7f)))
                Box(modifier = Modifier.fillMaxWidth(hudProg.coerceIn(0f, 1f)).fillMaxHeight(0.5f).align(Alignment.BottomStart).background(DCYAN))
            }
            val g = hudGap
            Text(if (sim.t < 0f) "" else if (g >= 0f) "AHEAD ${"%.0f".format(g)}m" else "BEHIND ${"%.0f".format(-g)}m", color = if (g >= 0f) Color(0xFF69F0AE) else Color(0xFFFF8A80), fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, fontSize = 12.sp, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 4.dp))
        }
        Text("QUIT", color = Color.White.copy(alpha = 0.8f), fontFamily = FontFamily.Monospace, fontSize = 12.sp,
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 14.dp, bottom = 22.dp).clip(RoundedCornerShape(8.dp)).background(Color.Black.copy(alpha = 0.45f)).clickable { confirmQuit = true }.padding(horizontal = 12.dp, vertical = 6.dp))
        if (sim.t > 0f && sim.t < 5f && phase == 0) Text("TAP = JUMP   •   SWIPE DOWN = SLIDE", color = DGOLD, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, fontSize = 12.sp, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 26.dp))

        // countdown + taunt
        if (hudCount > 0 && phase == 0) {
            Column(modifier = Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Image(painter = BitmapPainter(vsImg), contentDescription = null, modifier = Modifier.size(width = 120.dp, height = 108.dp))
                Image(painter = BitmapPainter(ImageBitmap.imageResource(id = rival.portrait)), contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.size(width = 120.dp, height = 160.dp).clip(RoundedCornerShape(14.dp)).border(2.dp, rival.color, RoundedCornerShape(14.dp)))
                Text("$hudCount", color = Color.White, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, fontSize = 80.sp)
                Text("\"${rival.pre}\"", color = rival.color, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("— ${rival.name}", color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
            }
        } else if (sim.t in 0f..0.7f && phase == 0) {
            Text("GO!", color = DGOLD, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, fontSize = 64.sp, modifier = Modifier.align(Alignment.Center))
        }

        // result
        if (phase == 1) {
            val won = sim.playerWon
            val close = !won && sim.margin < 8f
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.82f)), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(26.dp)) {
                    if (won) Image(painter = BitmapPainter(trophyImg), contentDescription = null, modifier = Modifier.size(width = 130.dp, height = 142.dp))
                    else Image(painter = BitmapPainter(ImageBitmap.imageResource(id = rival.portrait)), contentDescription = null, contentScale = ContentScale.Crop,
                        modifier = Modifier.size(width = 90.dp, height = 120.dp).clip(RoundedCornerShape(12.dp)).border(2.dp, rival.color, RoundedCornerShape(12.dp)))
                    Text(if (won) "VICTORY" else "DEFEAT", color = if (won) DGOLD else Color(0xFFFF5C7A), fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, fontSize = 40.sp)
                    Text("\"${if (won) rival.lose else rival.win}\"", color = rival.color, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.padding(top = 4.dp))
                    Text("— ${rival.name}", color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
                    Text(if (won) "Time ${"%.1f".format(sim.myTime)}s  •  won by ${"%.0f".format(sim.margin)}m" else if (close) "SO CLOSE! Lost by only ${"%.0f".format(sim.margin)}m" else "Lost by ${"%.0f".format(sim.margin)}m", color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp))
                    if (won) Text((if (firstWin) "FIRST WIN BONUS  " else "") + "+$gems 💎", color = DGOLD, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, fontSize = 22.sp, modifier = Modifier.padding(top = 8.dp))
                    if (won && rivalIdx + 1 < RIVALS.size && DuelStore.beaten(rivalIdx) && firstWin) Text("🔓 ${RIVALS[rivalIdx + 1].name} unlocked!", color = Color(0xFF69F0AE), fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                    if (won && gems > 0 && !doubled) {
                        Text(AdMobManager.rewardedLabel(RewardedSlot.STORM, "🎬 WATCH AD: DOUBLE TO +${gems * 2} 💎"), color = Color.Black, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, fontSize = 13.sp,
                            modifier = Modifier.padding(top = 14.dp).clip(RoundedCornerShape(24.dp)).background(Color(0xFF4CAF50))
                                .clickable { activity?.let { a -> AdMobManager.showRewardedIfReady(a, RewardedSlot.STORM) { doubled = true; onCreditGems(gems) } } }.padding(horizontal = 20.dp, vertical = 10.dp))
                    } else if (doubled) Text("DOUBLED! +$gems 💎 more", color = Color(0xFF69F0AE), fontFamily = FontFamily.Monospace, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
                    val left = remember(phase, tick) { DuelStore.left(isPro) }
                    val adLeft = remember(phase, tick) { DuelStore.adLeft() }
                    Row(modifier = Modifier.padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("DONE", color = Color.White, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, fontSize = 14.sp,
                            modifier = Modifier.clip(RoundedCornerShape(24.dp)).border(1.dp, Color.White.copy(alpha = 0.6f), RoundedCornerShape(24.dp)).clickable { onExit() }.padding(horizontal = 24.dp, vertical = 12.dp))
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
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.75f)).clickable { }, contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(30.dp)) {
                    Text("Forfeit the duel?", color = Color.White, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, fontSize = 20.sp)
                    Text("The duel is lost and used up.", color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                    Row(modifier = Modifier.padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("STAY", color = Color.Black, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, modifier = Modifier.clip(RoundedCornerShape(22.dp)).background(DCYAN).clickable { confirmQuit = false }.padding(horizontal = 28.dp, vertical = 11.dp))
                        Text("FORFEIT", color = Color.White, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, modifier = Modifier.clip(RoundedCornerShape(22.dp)).border(1.dp, Color.White.copy(alpha = 0.6f), RoundedCornerShape(22.dp)).clickable { onExit() }.padding(horizontal = 28.dp, vertical = 11.dp))
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------------------------------------------ drawing

private fun DrawScope.drawDuel(sim: DuelSim, rival: Rival, frames: List<ImageBitmap>, sprite: ImageBitmap?, arena: ImageBitmap, gate: ImageBitmap) {
    val w = size.width; val h = size.height
    val t = sim.t
    val shx = sin(t * 80f) * sim.shake * 8f
    withTransform({ translate(shx, 0f) }) {
        // arena: the stadium art (cover-fit), pulled dark so the lanes stay readable, with a slow light shimmer
        drawRect(Color(0xFF05061A))
        val asc = max(w / arena.width, h / arena.height)
        val adw = arena.width * asc; val adh = arena.height * asc
        drawImage(arena, srcSize = IntSize(arena.width, arena.height), dstOffset = IntOffset(((w - adw) / 2f).roundToInt(), ((h - adh) / 2f).roundToInt()), dstSize = IntSize(adw.roundToInt(), adh.roundToInt()), alpha = 0.85f)
        drawRect(Brush.verticalGradient(listOf(Color(0x66000010), Color(0x99000010), Color(0xCC05061A))))
        drawRect(Color(0xFF00E5FF).copy(alpha = 0.04f + 0.03f * sin(t * 1.7f)), blendMode = BlendMode.Plus)
        lane(sim, sim.ai, rival.color, true, h * 0.36f, frames, w, h, t, rival, sprite, gate)
        lane(sim, sim.me, DCYAN, false, h * 0.80f, frames, w, h, t, rival, null, gate)
        // divider
        drawRect(Brush.horizontalGradient(listOf(Color.Transparent, DMAG.copy(alpha = 0.8f), DCYAN.copy(alpha = 0.8f), Color.Transparent)), topLeft = Offset(0f, h * 0.575f), size = Size(w, 3f))
    }
    if (sim.flashRed > 0f) drawRect(Color(0xFFFF2040).copy(alpha = sim.flashRed * 0.22f))
}

private fun DrawScope.lane(sim: DuelSim, r: Runner, col: Color, isRival: Boolean, groundY: Float, frames: List<ImageBitmap>, w: Float, h: Float, t: Float, rival: Rival, sprite: ImageBitmap?, gate: ImageBitmap) {
    val unit = w * UNIT_W
    val px = w * PX
    // floor strip and moving dashes
    drawRect(Brush.verticalGradient(listOf(col.copy(alpha = 0.30f), Color.Transparent), startY = groundY, endY = groundY + h * 0.09f), topLeft = Offset(0f, groundY), size = Size(w, h * 0.09f))
    drawRect(col.copy(alpha = 0.9f), topLeft = Offset(0f, groundY), size = Size(w, 3f))
    for (i in -2..24) {
        val gx = px + ((i * 3f) - (r.x % 3f)) * unit
        drawRect(col.copy(alpha = 0.22f), topLeft = Offset(gx, groundY + h * 0.03f), size = Size(unit * 1.4f, 3f))
    }
    // finish gate: the arch you run through
    val fx = px + (TRACK - r.x) * unit
    if (fx < w * 1.3f && fx > -w * 0.5f) {
        val gh = h * 0.21f; val gw = gh * gate.width.toFloat() / gate.height.toFloat()
        drawImage(gate, srcSize = IntSize(gate.width, gate.height), dstOffset = IntOffset((fx - gw / 2f).roundToInt(), (groundY - gh + gh * 0.02f).roundToInt()), dstSize = IntSize(gw.roundToInt(), gh.roundToInt()))
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, DGOLD.copy(alpha = 0.55f)), startY = groundY - gh * 0.9f, endY = groundY), topLeft = Offset(fx - unit * 0.12f, groundY - gh * 0.9f), size = Size(unit * 0.24f, gh * 0.9f), blendMode = BlendMode.Plus)
    }
    // obstacles
    for (o in sim.obs) {
        val ox = px + (o.pos - r.x) * unit
        if (ox < -unit * 3f || ox > w + unit * 3f) continue
        when (o.type) {
            0 -> { // hurdle: jump it
                val bw = unit * 1.5f; val bh = h * 0.06f
                drawRoundRect(Brush.verticalGradient(listOf(Color(0xFFFFA000), Color(0xFFFF5C00))), topLeft = Offset(ox - bw / 2f, groundY - bh), size = Size(bw, bh), cornerRadius = CornerRadius(6f, 6f))
                drawRoundRect(Color.White.copy(alpha = 0.7f), topLeft = Offset(ox - bw / 2f, groundY - bh), size = Size(bw, bh), cornerRadius = CornerRadius(6f, 6f), style = Stroke(width = 2f))
                drawCircle(Brush.radialGradient(listOf(Color(0x66FF8A00), Color.Transparent), center = Offset(ox, groundY - bh / 2f), radius = bw), radius = bw, center = Offset(ox, groundY - bh / 2f), blendMode = BlendMode.Plus)
            }
            1 -> { // overhead beam: slide under it
                val bw = unit * 2.2f; val by = groundY - h * 0.115f
                drawRect(DMAG.copy(alpha = 0.18f), topLeft = Offset(ox - bw / 2f, by - h * 0.03f), size = Size(bw, h * 0.03f + 4f))
                drawLine(DMAG, Offset(ox - bw / 2f, by), Offset(ox + bw / 2f, by), strokeWidth = 7f, cap = StrokeCap.Round)
                drawLine(Color.White, Offset(ox - bw / 2f, by), Offset(ox + bw / 2f, by), strokeWidth = 2.5f, cap = StrokeCap.Round)
                drawCircle(DMAG, radius = 8f, center = Offset(ox - bw / 2f, by)); drawCircle(DMAG, radius = 8f, center = Offset(ox + bw / 2f, by))
                drawCircle(Brush.radialGradient(listOf(Color(0x66FF4DD2), Color.Transparent), center = Offset(ox, by), radius = bw), radius = bw, center = Offset(ox, by), blendMode = BlendMode.Plus)
            }
            else -> { // boost pad
                val bw = unit * 2.4f
                for (c in 0 until 3) {
                    val cx = ox - bw / 2f + c * bw / 3f
                    val p = Path2(cx, groundY - 2f, bw / 4f, h * 0.025f)
                    drawLine(Color(0xFF69F0AE).copy(alpha = 0.95f), Offset(p.x, p.y - p.h), Offset(p.x + p.w, p.y), strokeWidth = 6f, cap = StrokeCap.Round)
                    drawLine(Color(0xFF69F0AE).copy(alpha = 0.95f), Offset(p.x, p.y + p.h), Offset(p.x + p.w, p.y), strokeWidth = 6f, cap = StrokeCap.Round)
                }
                drawCircle(Brush.radialGradient(listOf(Color(0x5569F0AE), Color.Transparent), center = Offset(ox, groundY), radius = bw), radius = bw, center = Offset(ox, groundY), blendMode = BlendMode.Plus)
            }
        }
    }
    // runner
    val ph = h * 0.16f
    val useSprite = isRival && sprite != null
    val img = if (useSprite) sprite!! else frames[((t * (6f + r.v * 0.5f)).toInt().coerceAtLeast(0)) % frames.size]
    val pw = ph * img.width.toFloat() / img.height.toFloat()
    val air = if (r.airT > 0f) sin((1f - r.airT / AIR) * 3.14159f) else 0f
    val bob = if (useSprite && r.airT <= 0f && r.slideT <= 0f) abs(sin(t * (5f + r.v * 0.35f))) * ph * 0.05f else 0f   // a single running pose: bounce it like a stride
    val jumpY = air * h * 0.085f + bob
    val sliding = r.slideT > 0f
    val sy = if (sliding) 0.58f else 1f
    val cy = groundY - ph * 0.5f * sy - jumpY
    val st = r.stumble > 0f
    val rot = if (st) sin(t * 40f) * 5f else if (r.airT > 0f) -8f * (1f - 2f * (1f - r.airT / AIR)) else if (sliding) -14f else 0f
    if (r.boost > 0f) for (i in 0 until 6) {
        val ly = cy - ph * 0.35f + i * ph * 0.14f
        drawLine(Color(0xFF69F0AE).copy(alpha = 0.5f), Offset(px - pw * 0.5f, ly), Offset(px - pw * 0.5f - w * 0.16f * (0.5f + (i % 3) * 0.25f), ly), strokeWidth = 3f, cap = StrokeCap.Round)
    }
    drawCircle(Brush.radialGradient(listOf(col.copy(alpha = 0.35f), Color.Transparent), center = Offset(px, cy), radius = ph * 0.7f), radius = ph * 0.7f, center = Offset(px, cy), blendMode = BlendMode.Plus)
    withTransform({ rotate(rot, Offset(px, groundY - jumpY)); scale(1f, sy, Offset(px, groundY - jumpY)) }) {
        drawImage(img, srcSize = IntSize(img.width, img.height), dstOffset = IntOffset((px - pw / 2f).roundToInt(), (groundY - ph - jumpY).roundToInt()), dstSize = IntSize(pw.roundToInt(), ph.roundToInt()),
            alpha = if (isRival) 0.96f else 1f, colorFilter = if (isRival && !useSprite) ColorFilter.tint(col.copy(alpha = 0.40f), BlendMode.SrcAtop) else null)
        if (st) drawImage(img, srcSize = IntSize(img.width, img.height), dstOffset = IntOffset((px - pw / 2f).roundToInt(), (groundY - ph - jumpY).roundToInt()), dstSize = IntSize(pw.roundToInt(), ph.roundToInt()), alpha = 0.4f, colorFilter = ColorFilter.tint(Color.Red, BlendMode.SrcAtop))
    }
    // shadow
    drawOval(Color.Black.copy(alpha = 0.35f * (1f - air * 0.6f)), topLeft = Offset(px - pw * 0.3f, groundY - 4f), size = Size(pw * 0.6f, 9f))
    if (isRival) {
        val paint = android.graphics.Paint().apply { isAntiAlias = true; textAlign = android.graphics.Paint.Align.CENTER; textSize = w * 0.032f; color = col.toArgb(); typeface = android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD) }
        drawContext.canvas.nativeCanvas.drawText(rival.name, px, groundY - ph - jumpY - 10f, paint)
    }
    if (!isRival && sim.slip) drawCircle(DGOLD.copy(alpha = 0.5f), radius = ph * 0.58f, center = Offset(px, cy), style = Stroke(width = 3f))
}

private class Path2(val x: Float, val y: Float, val w: Float, val h: Float)
