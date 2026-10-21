package com.neonrush.game.ui

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
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
import com.neonrush.game.RewardedSlot
import androidx.compose.ui.platform.LocalContext
import com.neonrush.game.R
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

// =====================================================================================================================
// STORM RUN: the daily 90-second mini-game. Portrait, the pilot runs along a rooftop and dodges lightning columns.
// Own world, own rules, one course per day (same for every pilot), a reward ladder and a streak.
// =====================================================================================================================

object StormRunHost { var open by mutableStateOf(false); var runKey by mutableStateOf(0) }

private const val RUN_SECONDS = 90f
private const val WARN = 1.15f          // seconds a column glows before it strikes
private const val STRIKE = 0.32f        // seconds the bolt is live
private val TIER_SCORES = intArrayOf(150, 300, 450, 650)
private val TIER_GEMS = intArrayOf(15, 35, 70, 120)
private val TIER_NAMES = arrayOf("BRONZE", "SILVER", "GOLD", "STORM LORD")
private const val STREAK_BONUS = 150
private val GOLD = Color(0xFFFFD166)
private val VOLT = Color(0xFF7DD3FF)

/** One weekday personality. */
class StormMode(val name: String, val blurb: String, val wind: Float, val strikeRate: Float, val slip: Boolean,
                val dark: Boolean, val cellMult: Int, val rewardMult: Float)

object StormModes {
    fun today(): StormMode = when (Calendar.getInstance().get(Calendar.DAY_OF_WEEK)) {
        Calendar.MONDAY -> StormMode("GALE", "Heavy wind shoves you sideways.", 0.17f, 1.0f, false, false, 1, 1f)
        Calendar.TUESDAY -> StormMode("SURGE", "Lightning everywhere.", 0.07f, 1.35f, false, false, 1, 1f)
        Calendar.WEDNESDAY -> StormMode("FROZEN", "Icy rooftops: your ship slides.", 0.07f, 1.0f, true, false, 1, 1f)
        Calendar.THURSDAY -> StormMode("BLACKOUT", "Dark. Only lightning shows the way.", 0.07f, 1.0f, false, true, 1, 1f)
        Calendar.FRIDAY -> StormMode("DOUBLE CELLS", "Every storm cell is worth double.", 0.07f, 1.0f, false, false, 2, 1f)
        Calendar.SATURDAY -> StormMode("TEMPEST", "The worst storm of the week. Bigger rewards.", 0.12f, 1.6f, false, false, 1, 1.25f)
        else -> StormMode("CALM EYE", "A gentler storm with +50% rewards.", 0.04f, 0.7f, false, false, 1, 1.5f)
    }
    fun dayKey(): Int { val c = Calendar.getInstance(); return c.get(Calendar.YEAR) * 10000 + (c.get(Calendar.MONTH) + 1) * 100 + c.get(Calendar.DAY_OF_MONTH) }
}

class StormResult(val score: Int, val tier: Int, val gems: Int, val streak: Int, val streakBonus: Int, val newBest: Boolean)

/** Local progress: today's best/claimed tier, the daily streak, all-time best. */
object StormStore {
    private var sp: SharedPreferences? = null
    fun init(c: Context) {
        if (sp == null) sp = c.applicationContext.getSharedPreferences("neonrush_storm", Context.MODE_PRIVATE)
        if (gi("firstSeen") == 0) pi("firstSeen", StormModes.dayKey())   // the day this player first opened the game with Storm Run in it
    }
    const val UNLOCK_RUNS = 3
    /** Early unlock: after 3 finished normal runs, or from the next calendar day. Players who already have 3+ runs get it at once. */
    fun unlocked(totalRuns: Int): Boolean = totalRuns >= UNLOCK_RUNS || StormModes.dayKey() > gi("firstSeen", StormModes.dayKey())
    private fun gi(k: String, d: Int = 0) = try { sp?.getInt(k, d) ?: d } catch (_: Throwable) { d }
    private fun pi(k: String, v: Int) { try { sp?.edit()?.putInt(k, v)?.apply() } catch (_: Throwable) { } }
    private fun prevDay(day: Int): Int { val c = Calendar.getInstance(); c.add(Calendar.DAY_OF_MONTH, -1); return c.get(Calendar.YEAR) * 10000 + (c.get(Calendar.MONTH) + 1) * 100 + c.get(Calendar.DAY_OF_MONTH) }

    fun streak(): Int { val d = StormModes.dayKey(); val last = gi("last"); return if (last == d || last == prevDay(d)) gi("streak") else 0 }
    fun playedToday(): Boolean = gi("last") == StormModes.dayKey()
    fun bestToday(): Int = if (gi("bestDay") == StormModes.dayKey()) gi("bestToday") else 0
    fun bestEver(): Int = gi("bestEver")
    private const val MAX_AD_ATTEMPTS = 2
    fun adAttemptsLeft(): Int = MAX_AD_ATTEMPTS - (if (gi("adDay") == StormModes.dayKey()) gi("adUsed") else 0)
    fun useAdAttempt() { val d = StormModes.dayKey(); val u = if (gi("adDay") == d) gi("adUsed") else 0; pi("adDay", d); pi("adUsed", u + 1) }
    fun claimedTier(): Int = if (gi("claimDay") == StormModes.dayKey()) gi("claimTier", -1) else -1

    /** Records a finished run. Pays only the tiers not yet claimed today (so replays can still improve the payout). */
    fun record(score: Int, mode: StormMode, isPro: Boolean): StormResult {
        val d = StormModes.dayKey()
        val tier = TIER_SCORES.indexOfLast { score >= it }
        val prevClaim = claimedTier()
        var gems = 0
        for (t in (prevClaim + 1)..tier) gems += TIER_GEMS[t]
        gems = (gems * mode.rewardMult * (if (isPro) 1.25f else 1f)).roundToInt()
        if (tier > prevClaim) { pi("claimDay", d); pi("claimTier", tier) }
        var streakBonus = 0
        var st = streak()
        if (!playedToday()) { st += 1; pi("streak", st); pi("last", d); if (st % 7 == 0) streakBonus = STREAK_BONUS }
        val newBest = score > bestEver()
        if (newBest) pi("bestEver", score)
        if (gi("bestDay") != d) { pi("bestDay", d); pi("bestToday", 0) }
        if (score > gi("bestToday")) pi("bestToday", score)
        return StormResult(score, tier, gems + streakBonus, st, streakBonus, newBest)
    }
}

/** Little status block for the daily card: today's weather, streak and best score. */
@Composable
fun StormCardInfo() {
    val m = remember { StormModes.today() }
    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        Text("TODAY: ${m.name}", color = GOLD, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
        Text(m.blurb, color = Color.White.copy(alpha = 0.75f), fontSize = 11.sp)
        Text("⚡ Streak ${StormStore.streak()}/7 (day 7: +$STREAK_BONUS gems)   •   Best today ${StormStore.bestToday()}", color = VOLT, fontFamily = FontFamily.Monospace, fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp))
    }
}

// ------------------------------------------------------------------------------------------------------ simulation

private class Strike(val t0: Float, val x: Float, val w: Float) { var done = false; var cancelled = false; var hit = false }
private class Cell(val t0: Float, val x: Float) { var got = false }

private class Sim(seed: Int, val mode: StormMode, val cellsPerSecondBoost: Float = 1f) {
    val strikes = ArrayList<Strike>()
    val cells = ArrayList<Cell>()
    var t = -1.6f                  // starts negative: a short READY phase
    var px = 0.5f; var target = 0.5f; var pvx = 0f
    var lives = 3; var invuln = 0f; var immune = 0f
    var score = 0; var combo = 1; var comboT = 0f
    var charge = 0f; var cellsGot = 0; var dodged = 0
    var flash = 0f; var shake = 0f; var wave = 0f
    var over = false; var eye = false; var finalBonus = 0
    val rng = Random(seed)

    init {
        // the whole course is generated up front from the day's seed: everyone flies the same storm
        var c = 2.2f
        val r = Random(seed * 31 + 7)
        while (c < RUN_SECONDS - 4f) {
            val diff = (c / RUN_SECONDS).coerceIn(0f, 1f)
            val gap = (2.0f - 1.15f * diff) / mode.strikeRate
            when (r.nextInt(100)) {
                in 0..44 -> strikes += Strike(c, 0.1f + r.nextFloat() * 0.8f, 0.10f)
                in 45..64 -> { val a = 0.12f + r.nextFloat() * 0.35f; strikes += Strike(c, a, 0.10f); strikes += Strike(c + 0.15f, a + 0.3f + r.nextFloat() * 0.3f, 0.10f) }
                in 65..84 -> { // a wall with one gap
                    val gapX = 0.18f + r.nextFloat() * 0.64f
                    var x = 0.07f
                    while (x < 0.95f) { if (abs(x - gapX) > 0.13f) strikes += Strike(c, x, 0.10f); x += 0.115f }
                }
                else -> { val n = 5; val dir = if (r.nextBoolean()) 1 else -1; val sx = if (dir > 0) 0.1f else 0.9f
                    for (i in 0 until n) strikes += Strike(c + i * 0.33f, sx + dir * i * 0.17f, 0.10f) }
            }
            c += gap + (if (r.nextInt(100) < 20) 0.8f else 0f)
        }
        // storm cells: most fall in open sky, some hang right under a coming bolt (risk for reward)
        var k = 1.5f
        while (k < RUN_SECONDS - 3f) {
            val risky = r.nextInt(100) < 35
            val near = if (risky) strikes.firstOrNull { it.t0 > k + 1.2f && it.t0 < k + 3.2f } else null
            val x = near?.let { (it.x + (r.nextFloat() - 0.5f) * 0.06f).coerceIn(0.07f, 0.93f) } ?: (0.08f + r.nextFloat() * 0.84f)
            cells += Cell(k, x)
            k += 1.05f + r.nextFloat() * 0.5f
        }
    }

    /** y position (0 top .. 1 bottom) of a falling cell at time t. */
    fun cellY(c: Cell) = (t - c.t0) / 3.2f

    fun step(dt: Float, onHit: () -> Unit, onCell: () -> Unit) {
        t += dt
        if (t < 0f) return
        flash = max(0f, flash - dt * 2.4f); shake = max(0f, shake - dt * 3f); wave = max(0f, wave - dt * 1.3f)
        invuln = max(0f, invuln - dt); immune = max(0f, immune - dt)
        comboT -= dt; if (comboT <= 0f) combo = 1
        // wind (sum of two slow sines, different every mode), pushing both the finger target and the ship
        val wnd = (sin(t * 0.55f) * 0.8f + sin(t * 1.9f) * 0.3f) * mode.wind
        target = (target + wnd * dt).coerceIn(0.06f, 0.94f)
        if (mode.slip) { pvx += (target - px) * 9f * dt; pvx *= (1f - 2.2f * dt).coerceAtLeast(0f); px += pvx * dt * 1.0f; px = px.coerceIn(0.06f, 0.94f) }
        else { px += (target - px) * min(1f, dt * 22f) }
        if (t >= RUN_SECONDS) { eye = true; over = true; finalBonus = 100 + lives * 50; score += finalBonus; return }
        // strikes
        for (s in strikes) {
            if (s.done || s.cancelled) continue
            val live0 = s.t0 + WARN; val live1 = live0 + STRIKE
            if (t >= live0 - 0.02f && immune > 0f) { s.cancelled = true; continue }
            if (t in live0..live1) {
                if (!s.hit && invuln <= 0f && abs(px - s.x) < s.w * 0.5f + 0.03f) {
                    s.hit = true; lives -= 1; invuln = 1.5f; flash = 1f; shake = 1f; combo = 1; onHit()
                    if (lives <= 0) { over = true; return }
                }
            }
            if (t > live1) {
                s.done = true
                if (!s.hit) { val d = abs(px - s.x) - s.w * 0.5f; val near = d < 0.085f
                    dodged += 1; score += if (near) 8 else 3 } }
        }
        // cells
        for (c in cells) {
            if (c.got) continue
            val y = cellY(c)
            if (y < 0f || y > 1.1f) continue
            if (y in 0.78f..0.92f && abs(c.x - px) < 0.085f) {
                c.got = true; cellsGot += 1; score += 10 * mode.cellMult * combo; combo = min(combo + 1, 4); comboT = 2.2f
                charge = min(1f, charge + 0.12f); onCell()
            }
        }
    }

    fun discharge() {
        if (charge < 1f) return
        charge = 0f; immune = 3.5f; wave = 1f; flash = 1f; score += 40
    }
}

// ------------------------------------------------------------------------------------------------------ screen

@Composable
fun StormRunScreen(
    gems: Int, attemptsLeft: Int, isPro: Boolean,
    onSpendGems: (Int, (Boolean) -> Unit) -> Unit,
    onCreditGems: (Int) -> Unit,
    onRetry: ((Boolean) -> Unit) -> Unit,
    onFreeAttempt: ((Boolean) -> Unit) -> Unit,
    onExit: () -> Unit
) {
    Dialog(
        onDismissRequest = { },
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        StormRunBody(gems, attemptsLeft, isPro, onSpendGems, onCreditGems, onRetry, onFreeAttempt, onExit)
    }
}

@Composable
private fun StormRunBody(
    gems: Int, attemptsLeft: Int, isPro: Boolean,
    onSpendGems: (Int, (Boolean) -> Unit) -> Unit, onCreditGems: (Int) -> Unit,
    onRetry: ((Boolean) -> Unit) -> Unit, onFreeAttempt: ((Boolean) -> Unit) -> Unit, onExit: () -> Unit
) {
    val activity = LocalContext.current.findActivity()
    var doubled by remember { mutableStateOf(false) }
    var adLeft by remember { mutableStateOf(StormStore.adAttemptsLeft()) }
    val mode = remember { StormModes.today() }
    val seed = remember { StormModes.dayKey() }
    val runKey = StormRunHost.runKey
    val sim = remember(runKey) { Sim(seed, mode) }
    val sfx = remember { NeonSoundEngine() }
    val bg = ImageBitmap.imageResource(id = R.drawable.storm_bg)
    val bolt = ImageBitmap.imageResource(id = R.drawable.storm_bolt)
    val warn = ImageBitmap.imageResource(id = R.drawable.storm_warn)
    val badge = ImageBitmap.imageResource(id = R.drawable.storm_badge)
    val runFrames = listOf(R.drawable.pilot_run_1, R.drawable.pilot_run_2, R.drawable.pilot_run_3, R.drawable.pilot_run_4, R.drawable.pilot_run_5, R.drawable.pilot_run_6)
        .map { ImageBitmap.imageResource(id = it) }

    var frame by remember(runKey) { mutableStateOf(0L) }
    var phase by remember(runKey) { mutableStateOf(0) }          // 0 playing, 1 dead (second wind offer), 2 results
    var result by remember(runKey) { mutableStateOf<StormResult?>(null) }
    var usedSecondWind by remember(runKey) { mutableStateOf(false) }
    var confirmQuit by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }
    LaunchedEffect(runKey) { doubled = false; adLeft = StormStore.adAttemptsLeft() }
    var hudScore by remember(runKey) { mutableStateOf(0) }
    var hudLives by remember(runKey) { mutableStateOf(3) }
    var hudCharge by remember(runKey) { mutableStateOf(0f) }
    var hudTime by remember(runKey) { mutableStateOf(0f) }
    var hudCombo by remember(runKey) { mutableStateOf(1) }

    LaunchedEffect(runKey, phase) {
        if (phase != 0) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            val dt = ((now - last) / 1_000_000_000f).coerceIn(0f, 0.05f); last = now
            sim.step(dt,
                onHit = { sfx.playTone(130f, 220, "sawtooth") },
                onCell = { sfx.playTone(900f + 120f * sim.combo, 70, "sine") })
            frame++
            if (hudScore != sim.score) hudScore = sim.score
            if (hudLives != sim.lives) hudLives = sim.lives
            if (abs(hudCharge - sim.charge) > 0.01f) hudCharge = sim.charge
            val tt = sim.t.coerceAtLeast(0f); if (abs(hudTime - tt) > 0.2f) hudTime = tt
            if (hudCombo != sim.combo) hudCombo = sim.combo
            if (sim.over) {
                if (sim.eye || usedSecondWind || gems < 20) { finish(sim, mode, isPro, onCreditGems) { result = it; phase = 2 } }
                else phase = 1
                break
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        Canvas(
            modifier = Modifier.fillMaxSize().pointerInput(runKey) {
                detectDragGestures(onDrag = { ch, d -> ch.consume(); sim.target = (sim.target + d.x / size.width * 1.35f).coerceIn(0.06f, 0.94f) })
            }
        ) {
            if (frame < 0L) return@Canvas
            drawStorm(sim, mode, bg, bolt, warn, runFrames)
        }

        // HUD
        Column(modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(top = 28.dp, start = 14.dp, end = 14.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("⚡ ${mode.name}", color = GOLD, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                Text("$hudScore", color = Color.White, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, fontSize = 26.sp)
                Text("♥".repeat(hudLives.coerceAtLeast(0)), color = Color(0xFFFF5C7A), fontSize = 20.sp)
            }
            Box(modifier = Modifier.fillMaxWidth().padding(top = 6.dp).height(5.dp).clip(RoundedCornerShape(3.dp)).background(Color.White.copy(alpha = 0.15f))) {
                Box(modifier = Modifier.fillMaxWidth((hudTime / RUN_SECONDS).coerceIn(0f, 1f)).fillMaxHeight().background(Brush.horizontalGradient(listOf(VOLT, GOLD))))
            }
            if (hudCombo > 1) Text("COMBO x$hudCombo", color = GOLD, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
        }
        Text("QUIT", color = Color.White.copy(alpha = 0.8f), fontFamily = FontFamily.Monospace, fontSize = 12.sp,
            modifier = Modifier.align(Alignment.TopStart).padding(start = 14.dp, top = 84.dp).clip(RoundedCornerShape(8.dp)).background(Color.Black.copy(alpha = 0.45f)).clickable { confirmQuit = true }.padding(horizontal = 12.dp, vertical = 6.dp))

        // charge bar + DISCHARGE
        Column(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 26.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (hudCharge >= 1f && phase == 0) {
                val pulse = 0.75f + 0.25f * sin(frame * 0.25f)
                Text("⚡ DISCHARGE", color = Color.Black, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, fontSize = 18.sp,
                    modifier = Modifier.clip(RoundedCornerShape(30.dp)).background(Brush.horizontalGradient(listOf(VOLT.copy(alpha = pulse), GOLD.copy(alpha = pulse))))
                        .clickable { sim.discharge(); sfx.playTone(300f, 380, "sawtooth") }.padding(horizontal = 34.dp, vertical = 14.dp))
            } else {
                Box(modifier = Modifier.width(170.dp).height(8.dp).clip(RoundedCornerShape(4.dp)).background(Color.White.copy(alpha = 0.15f))) {
                    Box(modifier = Modifier.fillMaxWidth(hudCharge.coerceIn(0f, 1f)).fillMaxHeight().background(VOLT))
                }
                Text("STORM CHARGE", color = Color.White.copy(alpha = 0.55f), fontFamily = FontFamily.Monospace, fontSize = 9.sp, modifier = Modifier.padding(top = 3.dp))
            }
        }

        // ready banner
        if (phase == 0 && sim.t < 0f) {
            Column(modifier = Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("TODAY: ${mode.name}", color = GOLD, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, fontSize = 24.sp)
                Text(mode.blurb, color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                Text("Drag left and right. Dodge the glowing columns.", color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp, modifier = Modifier.padding(top = 10.dp))
            }
        }

        // second wind offer
        if (phase == 1) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f)), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(28.dp)) {
                    Text("STRUCK DOWN", color = Color(0xFFFF5C7A), fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, fontSize = 24.sp)
                    Text("Score ${sim.score}. Get back up with 2 lives and keep your combo?", color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                    Text("⚡ SECOND WIND • 20 💎", color = Color.Black, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, fontSize = 15.sp,
                        modifier = Modifier.padding(top = 18.dp).clip(RoundedCornerShape(26.dp)).background(Brush.horizontalGradient(listOf(VOLT, GOLD)))
                            .clickable {
                                onSpendGems(20) { ok ->
                                    if (ok) { usedSecondWind = true; sim.lives = 2; sim.over = false; sim.invuln = 2.2f; sim.immune = 2.2f; phase = 0 }
                                    else msg = "Not enough gems"
                                }
                            }.padding(horizontal = 26.dp, vertical = 12.dp))
                    Text(AdMobManager.rewardedLabel(RewardedSlot.REVIVE, "\uD83C\uDFAC WATCH AD: SECOND WIND (FREE)"), color = Color.White, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, fontSize = 13.sp,
                        modifier = Modifier.padding(top = 12.dp).clip(RoundedCornerShape(26.dp)).border(1.5.dp, Color(0xFF4CAF50), RoundedCornerShape(26.dp))
                            .clickable {
                                activity?.let { a -> AdMobManager.showRewardedIfReady(a, RewardedSlot.REVIVE) { usedSecondWind = true; sim.lives = 2; sim.over = false; sim.invuln = 2.2f; sim.immune = 2.2f; phase = 0 } }
                            }.padding(horizontal = 22.dp, vertical = 10.dp))
                    Text("NO THANKS", color = Color.White.copy(alpha = 0.7f), fontFamily = FontFamily.Monospace, fontSize = 12.sp,
                        modifier = Modifier.padding(top = 16.dp).clickable { usedSecondWind = true; finish(sim, mode, isPro, onCreditGems) { result = it; phase = 2 } }.padding(10.dp))
                    if (msg.isNotEmpty()) Text(msg, color = GOLD, fontSize = 11.sp)
                }
            }
        }

        // results
        val res = result
        if (phase == 2 && res != null) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.82f)), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                    Canvas(modifier = Modifier.size(130.dp)) {
                        drawImage(badge, dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()), alpha = if (res.tier >= 0) 1f else 0.35f)
                    }
                    Text(if (sim.eye) "YOU REACHED THE EYE" else "STORM RUN OVER", color = GOLD, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, fontSize = 18.sp, modifier = Modifier.padding(top = 8.dp))
                    Text("${res.score}", color = Color.White, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, fontSize = 44.sp)
                    if (res.newBest) Text("NEW PERSONAL BEST", color = VOLT, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                    // the ladder
                    Column(modifier = Modifier.padding(top = 10.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.07f)).padding(12.dp)) {
                        for (i in TIER_SCORES.indices.reversed()) {
                            val reached = res.score >= TIER_SCORES[i]
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text((if (reached) "✓ " else "○ ") + TIER_NAMES[i], color = if (reached) GOLD else Color.White.copy(alpha = 0.45f), fontFamily = FontFamily.Monospace, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                Text("${TIER_SCORES[i]} pts  •  ${(TIER_GEMS[i] * mode.rewardMult).roundToInt()} 💎", color = Color.White.copy(alpha = if (reached) 0.95f else 0.45f), fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                            }
                        }
                    }
                    Text(if (res.gems > 0) "+${res.gems} 💎 EARNED" else "No new reward tier reached", color = if (res.gems > 0) GOLD else Color.White.copy(alpha = 0.6f), fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, fontSize = 18.sp, modifier = Modifier.padding(top = 12.dp))
                    Text("⚡ Storm streak ${res.streak}/7" + (if (res.streakBonus > 0) "  •  DAY 7 BONUS +${res.streakBonus}" else "  •  day 7 pays +$STREAK_BONUS gems"), color = VOLT, fontFamily = FontFamily.Monospace, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                    if (res.gems > 0 && !doubled) {
                        Text(AdMobManager.rewardedLabel(RewardedSlot.DOUBLE_GEMS, "\uD83C\uDFAC WATCH AD: DOUBLE TO +${res.gems * 2} \uD83D\uDC8E"), color = Color.Black, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, fontSize = 13.sp,
                            modifier = Modifier.padding(top = 14.dp).clip(RoundedCornerShape(24.dp)).background(Color(0xFF4CAF50))
                                .clickable { activity?.let { a -> AdMobManager.showRewardedIfReady(a, RewardedSlot.DOUBLE_GEMS) { doubled = true; onCreditGems(res.gems) } } }.padding(horizontal = 20.dp, vertical = 10.dp))
                    } else if (doubled) Text("DOUBLED! +${res.gems} \uD83D\uDC8E more", color = Color(0xFF69F0AE), fontFamily = FontFamily.Monospace, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
                    Row(modifier = Modifier.padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("DONE", color = Color.White, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, fontSize = 14.sp,
                            modifier = Modifier.clip(RoundedCornerShape(24.dp)).border(1.dp, Color.White.copy(alpha = 0.6f), RoundedCornerShape(24.dp)).clickable { onExit() }.padding(horizontal = 26.dp, vertical = 12.dp))
                        Text(if (attemptsLeft > 0) "PLAY AGAIN ($attemptsLeft left)" else "NO ATTEMPTS LEFT", color = Color.Black, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, fontSize = 13.sp,
                            modifier = Modifier.clip(RoundedCornerShape(24.dp)).background(if (attemptsLeft > 0) Brush.horizontalGradient(listOf(VOLT, GOLD)) else Brush.horizontalGradient(listOf(Color.Gray, Color.Gray)))
                                .clickable(enabled = attemptsLeft > 0) { onRetry { ok -> if (ok) StormRunHost.runKey += 1 else msg = "No attempts left" } }.padding(horizontal = 22.dp, vertical = 12.dp))
                    }
                    if (attemptsLeft <= 0 && adLeft > 0) {
                        Text(AdMobManager.rewardedLabel(RewardedSlot.DOUBLE_GEMS, "\uD83C\uDFAC WATCH AD: FREE ATTEMPT ($adLeft left today)"), color = Color.White, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, fontSize = 13.sp,
                            modifier = Modifier.padding(top = 12.dp).clip(RoundedCornerShape(24.dp)).border(1.5.dp, Color(0xFF4CAF50), RoundedCornerShape(24.dp))
                                .clickable {
                                    activity?.let { a -> AdMobManager.showRewardedIfReady(a, RewardedSlot.DOUBLE_GEMS) {
                                        onFreeAttempt { ok -> if (ok) { StormStore.useAdAttempt(); StormRunHost.runKey += 1 } else msg = "Could not start" }
                                    } }
                                }.padding(horizontal = 20.dp, vertical = 10.dp))
                    }
                    if (attemptsLeft <= 0) Text("Get more attempts on the Social tab (25 💎) or come back tomorrow.", color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp, modifier = Modifier.padding(top = 10.dp))
                }
            }
        }

        if (confirmQuit) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.75f)).clickable { }, contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(30.dp)) {
                    Text("Leave the storm?", color = Color.White, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, fontSize = 20.sp)
                    Text("This attempt is lost and no reward is paid.", color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                    Row(modifier = Modifier.padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("STAY", color = Color.Black, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, modifier = Modifier.clip(RoundedCornerShape(22.dp)).background(VOLT).clickable { confirmQuit = false }.padding(horizontal = 28.dp, vertical = 11.dp))
                        Text("LEAVE", color = Color.White, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, modifier = Modifier.clip(RoundedCornerShape(22.dp)).border(1.dp, Color.White.copy(alpha = 0.6f), RoundedCornerShape(22.dp)).clickable { onExit() }.padding(horizontal = 28.dp, vertical = 11.dp))
                    }
                }
            }
        }
    }
}

private fun finish(sim: Sim, mode: StormMode, isPro: Boolean, credit: (Int) -> Unit, done: (StormResult) -> Unit) {
    val r = StormStore.record(sim.score, mode, isPro)
    if (r.gems > 0) credit(r.gems)
    done(r)
}

// ------------------------------------------------------------------------------------------------------ drawing

private fun DrawScope.drawStorm(sim: Sim, mode: StormMode, bg: ImageBitmap, bolt: ImageBitmap, warn: ImageBitmap, run: List<ImageBitmap>) {
    val w = size.width; val h = size.height
    val t = sim.t
    val shx = sin(t * 90f) * sim.shake * 9f
    withTransform({ translate(shx, 0f) }) {
        // background, cover-fit with a slow drift
        val sc = max(w / bg.width, h / bg.height) * 1.08f
        val dw = bg.width * sc; val dh = bg.height * sc
        val drift = sin(t * 0.3f) * w * 0.02f
        drawImage(bg, srcSize = IntSize(bg.width, bg.height), dstOffset = IntOffset(((w - dw) / 2f + drift).roundToInt(), ((h - dh) / 2f).roundToInt()), dstSize = IntSize(dw.roundToInt(), dh.roundToInt()))
        // rain
        for (i in 0 until 46) {
            val fx = (i * 0.6180339f) % 1f
            val fy = ((i * 0.37f) + t * (1.3f + (i % 4) * 0.25f)) % 1f
            val x = fx * w - fy * h * 0.05f; val y = fy * h
            drawLine(Color(0xFFBFEFFF).copy(alpha = 0.18f), Offset(x, y), Offset(x - w * 0.012f, y + h * 0.03f), strokeWidth = 1.6f)
        }
        if (mode.dark) drawRect(Color.Black.copy(alpha = 0.72f))
        // columns: warning, then the bolt
        for (s in sim.strikes) {
            if (s.cancelled) continue
            val live0 = s.t0 + WARN; val live1 = live0 + STRIKE
            if (t < s.t0 || t > live1 + 0.15f) continue
            val cx = s.x * w
            if (t < live0) {
                val k = ((t - s.t0) / WARN).coerceIn(0f, 1f)
                val a = (0.25f + 0.65f * k) * (0.7f + 0.3f * sin(t * (10f + 14f * k)))
                val ww = w * (s.w + 0.06f) * 1.9f
                drawImage(warn, srcSize = IntSize(warn.width, warn.height), dstOffset = IntOffset((cx - ww / 2f).roundToInt(), 0), dstSize = IntSize(ww.roundToInt(), h.roundToInt()), alpha = a.coerceIn(0f, 1f), blendMode = BlendMode.Plus)
                // ground ring where it will land
                drawCircle(GOLD.copy(alpha = 0.35f * k), radius = w * s.w * (0.45f + 0.5f * k), center = Offset(cx, h * 0.9f), style = Stroke(width = 3f))
            } else if (t <= live1) {
                val ww = w * (s.w + 0.1f) * 2.6f
                val a = 1f
                drawImage(bolt, srcSize = IntSize(bolt.width, bolt.height), dstOffset = IntOffset((cx - ww / 2f).roundToInt(), 0), dstSize = IntSize(ww.roundToInt(), h.roundToInt()), alpha = a, blendMode = BlendMode.Plus)
                drawImage(bolt, srcSize = IntSize(bolt.width, bolt.height), dstOffset = IntOffset((cx - ww * 0.3f).roundToInt(), 0), dstSize = IntSize((ww * 0.6f).roundToInt(), h.roundToInt()), alpha = 0.8f, blendMode = BlendMode.Plus)
                drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.9f), VOLT.copy(alpha = 0.4f), Color.Transparent), center = Offset(cx, h * 0.9f), radius = w * 0.2f), radius = w * 0.2f, center = Offset(cx, h * 0.9f), blendMode = BlendMode.Plus)
            }
        }
        // storm cells
        for (c in sim.cells) {
            if (c.got) continue
            val y = sim.cellY(c)
            if (y < -0.05f || y > 1.1f) continue
            val cx = c.x * w; val cy = y * h
            val r = w * 0.035f * (1f + 0.12f * sin(t * 8f + c.x * 20f))
            drawCircle(Brush.radialGradient(listOf(GOLD.copy(alpha = 0.55f), Color.Transparent), center = Offset(cx, cy), radius = r * 3f), radius = r * 3f, center = Offset(cx, cy), blendMode = BlendMode.Plus)
            drawCircle(VOLT, radius = r, center = Offset(cx, cy))
            drawCircle(Color.White, radius = r * 0.45f, center = Offset(cx, cy))
            drawCircle(GOLD, radius = r * 1.35f, center = Offset(cx, cy), style = Stroke(width = 2.5f))
        }
        // the pilot, running on the rooftop
        val ph = h * 0.17f
        val frameIdx = ((t * 12f).toInt().coerceAtLeast(0)) % run.size
        val img = run[frameIdx]
        val pw = ph * img.width.toFloat() / img.height.toFloat()
        val pcx = sim.px * w; val pcy = h * 0.80f
        val moving = (sim.target - sim.px)
        val faceLeft = sim.pvx < -0.02f || moving < -0.01f
        val blink = sim.invuln > 0f && ((t * 14f).toInt() % 2 == 0)
        if (!blink) {
            withTransform({ if (faceLeft) scale(-1f, 1f, Offset(pcx, pcy)) }) {
                drawImage(img, srcSize = IntSize(img.width, img.height), dstOffset = IntOffset((pcx - pw / 2f).roundToInt(), (pcy - ph / 2f).roundToInt()), dstSize = IntSize(pw.roundToInt(), ph.roundToInt()))
            }
        }
        if (sim.immune > 0f) {
            drawCircle(VOLT.copy(alpha = 0.18f + 0.1f * sin(t * 18f)), radius = ph * 0.75f, center = Offset(pcx, pcy))
            drawCircle(VOLT.copy(alpha = 0.8f), radius = ph * 0.75f, center = Offset(pcx, pcy), style = Stroke(width = 3f))
        }
        // discharge shockwave
        if (sim.wave > 0f) {
            val rr = w * (1.3f - sim.wave * 1.2f)
            drawCircle(VOLT.copy(alpha = sim.wave * 0.7f), radius = rr, center = Offset(pcx, pcy), style = Stroke(width = 12f * sim.wave + 2f))
        }
        // wind streaks (sideways) and the gust chevrons
        val wnd = (sin(t * 0.55f) * 0.8f + sin(t * 1.9f) * 0.3f)
        val strength = (abs(wnd) * mode.wind / 0.17f).coerceIn(0f, 1f)
        if (strength > 0.12f) {
            val dir = if (wnd >= 0f) 1f else -1f
            for (i in 0 until 14) {
                val fy = (i * 0.173f) % 1f * h * 0.8f + h * 0.05f
                val fx = (((i * 0.37f) + t * (0.5f + (i % 3) * 0.2f) * dir * (0.4f + strength)) % 1f + 1f) % 1f
                drawLine(Color.White.copy(alpha = 0.10f + 0.25f * strength), Offset(fx * w, fy), Offset(fx * w - dir * w * 0.12f * (0.5f + strength), fy), strokeWidth = 2f, cap = StrokeCap.Round)
            }
        }
        if (mode.dark) {
            // a little light around the pilot so the player can still see their ship
            drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.22f), Color.Transparent), center = Offset(pcx, pcy), radius = w * 0.3f), radius = w * 0.3f, center = Offset(pcx, pcy), blendMode = BlendMode.Plus)
        }
    }
    if (sim.flash > 0f) drawRect(Color.White.copy(alpha = sim.flash * 0.45f))
    if (sim.eye) drawRect(Brush.verticalGradient(listOf(Color(0x55FFD166), Color.Transparent)))
}
