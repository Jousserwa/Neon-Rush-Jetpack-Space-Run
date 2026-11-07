package com.neonrush.game

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * PHASE GATE (Stage 177): once in a while, in the middle of a run, a crack tears open in the track, a portal pulls the ship in, and for 2.5 seconds the player
 * glimpses another realm (a postcard of a new world, a ghost of a Hangar item they could own, or a gift of gems) before snapping back. It never ends a run:
 * the pilot is invulnerable from the omen to the snap back and can still steer. One gate every 8 to 14 minutes of flying, at most 2 per run, never in the first
 * 3 runs, never in Legendary, never near a boss, and it can be turned off in Settings.
 */
object PhaseGate {
    const val MIN_SEC = 480f
    const val MAX_SEC = 840f
    const val MAX_PER_RUN = 2
    const val MIN_RUNS = 3
    const val T_OMEN = 0.6f
    const val T_PULL = 0.4f
    const val T_GLIMPSE = 2.5f
    const val T_BACK = 0.5f
    const val KINDS = 5                       // 0 crystal sea, 1 storm city, 2 hangar teaser, 3 gem gift, 4 zone preview
    const val GIFT_GEMS = 3                   // gems per gate, never more
    private val ORDER = intArrayOf(0, 4, 1, 3, 2)
    private val PREVIEW_ZONES = intArrayOf(30, 50, 75, 100, 150, 200, 300, 400, 500)
    /** How long the glimpse lasts, in seconds. The landscapes and the zone preview are a real 6 second flight; the teaser is a short postcard. */
    fun durOf(kind: Int): Float = when (kind) { 0, 1, 4 -> 6f; 3 -> 5f; else -> 4f }
    /** The first far zone ahead of the player's best zone (never changes progress, it is only a preview). */
    fun previewZone(best: Int): Int = PREVIEW_ZONES.firstOrNull { it > best + 5 } ?: PREVIEW_ZONES.last()

    /** 0 idle, 1 omen, 2 pull, 3 glimpse, 4 snap back. The pilot is covered (invulnerable) in 1..4. */
    @Volatile var phase = 0
    @Volatile var count = 0
    @Volatile var nextAtSec = 0f
    @Volatile var elapsedSec = 0f
    fun covering(): Boolean = phase != 0
    /** TESTING ONLY (Settings, testing menu): a gate every 6 seconds of flying, from the first run, so it can be seen at once. */
    @Volatile var testQuick = false
    private fun gap(): Float = if (testQuick) 6f else MIN_SEC + Random.nextFloat() * (MAX_SEC - MIN_SEC)
    fun newRun() { phase = 0; count = 0; elapsedSec = 0f; nextAtSec = gap() }
    fun scheduleNext() { elapsedSec = 0f; nextAtSec = gap() }

    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences("neonrush_phasegate", Context.MODE_PRIVATE)
    var version by mutableStateOf(0)
    fun enabled(ctx: Context): Boolean { @Suppress("UNUSED_VARIABLE") val v = version; return try { prefs(ctx).getBoolean("on", true) } catch (_: Throwable) { true } }
    fun setEnabled(ctx: Context, on: Boolean) { try { prefs(ctx).edit().putBoolean("on", on).apply() } catch (_: Throwable) {}; version += 1 }
    /** The next glimpse in the rotation, so a player sees every kind before any repeats. */
    fun nextKind(ctx: Context): Int {
        return try { val sp = prefs(ctx); val n = sp.getInt("seq", 0); sp.edit().putInt("seq", n + 1).putInt("seen", sp.getInt("seen", 0) + 1).apply(); ORDER[n % KINDS] } catch (_: Throwable) { Random.nextInt(KINDS) }
    }
    fun timesSeen(ctx: Context): Int = try { prefs(ctx).getInt("seen", 0) } catch (_: Throwable) { 0 }

    // ------------------------------------------------ the glimpse itself: shared by the driver and the layers drawn behind and in front of the pilot ------------------------------------------------
    /** type 0 obstacle, 1 gem. x runs from the right edge (1.12) to the left at [speed] screen widths per second; y and hh are fractions of the screen height. */
    class GObj(val type: Int, val t0: Float, val y: Float, val speed: Float, val hh: Float) { @Volatile var done = false }
    var tick by mutableStateOf(0)
    @Volatile var gt = 0f          // seconds inside the current phase
    @Volatile var gAt = 0f         // seconds since the glimpse began (keeps running during the snap back)
    @Volatile var gKind = 0
    @Volatile var gDur = 2.5f
    @Volatile var gZone = 0
    @Volatile var gTitle = ""
    @Volatile var gLine = ""
    @Volatile var gIcon = ""
    @Volatile var gemsGot = 0
    @Volatile var perfects = 0
    @Volatile var bumps = 0
    @Volatile var banner = ""
    @Volatile var bannerT = 0f
    @Volatile var flash = 0f
    @Volatile var objs: List<GObj> = emptyList()
    // ---- ZONE ECHO (kind 4): a sealed mini-run of a far zone. It uses the real zone data and the real hazard spawner; nothing in it counts and nothing in it can hurt. ----
    @Volatile var echoElems: List<VisualTrackElement> = emptyList()
    @Volatile var echoTick = 0
    @Volatile var echoAcc = 0f
    @Volatile var echoSpeed = 1f
    @Volatile var echoSpacing = 20f
    @Volatile var echoSet = 1
    @Volatile var echoRand: Random = Random(1)
    val echoHitIds = HashSet<String>()
    val ebul = ArrayList<FloatArray>()      // boss shots: x, y (fractions)
    @Volatile var hits = 0
    @Volatile var hitFx = 0f
    @Volatile var bossT = 0f                 // seconds the guardian has been on screen
    @Volatile var bossFire = 0f
    @Volatile var bossName = ""
    fun bossYPct(): Int = (50f + 26f * sin(bossT * 2.2f)).toInt()
    fun inGlimpse(): Boolean = phase == 3 || phase == 4
    /** 0..1: how far the realm is opened around the ship (opens in 0.4 s, closes during the snap back). */
    fun reveal(): Float = when (phase) { 3 -> (gt / 0.4f).coerceIn(0f, 1f); 4 -> 1f - (gt / T_BACK).coerceIn(0f, 1f); else -> 0f }
    fun ox(o: GObj): Float = 1.12f - (gAt - o.t0) * o.speed
    /** Gifts drift toward the pilot's height so the gift realm is easy; everything else stays in its lane. */
    fun oy(o: GObj, x: Float, py: Float): Float = if (gKind == 3 && o.type == 1) { val k = ((0.95f - x) / 0.6f).coerceIn(0f, 1f); o.y + (py - o.y) * k } else o.y
    fun begin(kind: Int, bestZone: Int) {
        gKind = kind; gDur = durOf(kind); gAt = 0f; gemsGot = 0; perfects = 0; bumps = 0; banner = ""; bannerT = 0f
        val r = Random(System.nanoTime()); val l = ArrayList<GObj>()
        if (kind != 2 && kind != 3 && kind != 4) {
            var tt = 0.9f
            val hh = when (kind) { 0 -> 0.10f; 1 -> 0.05f; else -> 0.055f }
            while (tt < gDur - 2.5f) { l.add(GObj(0, tt, 0.16f + r.nextFloat() * 0.68f, 0.50f + r.nextFloat() * 0.10f, hh)); tt += 0.9f + r.nextFloat() * 0.5f }
        }
        if (kind != 2) for (f in floatArrayOf(0.12f, 0.30f, 0.48f)) l.add(GObj(1, gDur * f, 0.2f + r.nextFloat() * 0.6f, 0.46f, 0.045f))
        objs = l
        gZone = if (kind == 4) previewZone(bestZone) else 0
        echoElems = emptyList(); echoTick = 0; echoAcc = 0f; echoHitIds.clear(); ebul.clear(); hits = 0; hitFx = 0f; bossT = 0f; bossFire = 0f
    }

    /** Is it safe to open a gate now? (Overlay passes what it knows.) */
    fun safe(tierName: String, bossActive: Boolean, fuel: Int, runs: Int): Boolean =
        tierName != "LEGENDARY" && !bossActive && fuel > 8 && (testQuick || runs >= MIN_RUNS) && (testQuick || count < MAX_PER_RUN) && !AirSupport.covering() && !Pause.paused && !Pause.covering()

    // ---------------------------------------------------------------- sound: four small synthesised clips, no files ----------------------------------------------------------------
    private const val SR = 22050
    private fun clip(sec: Float, gen: (Float, Float) -> Float): ShortArray {
        val n = (sec * SR).toInt()
        return ShortArray(n) { i -> val t = i / SR.toFloat(); (gen(t, t / sec).coerceIn(-1f, 1f) * 30000f).toInt().toShort() }
    }
    private val TAU = (2.0 * PI).toFloat()
    /** step 1 omen: a deep hum that climbs. 2 pull: a rising whoosh. 3 glimpse: a soft shimmering pad. 4 snap: a thump with a noise crash. */
    private fun render(step: Int): ShortArray = when (step) {
        1 -> clip(0.7f) { t, p -> val f = 55f + 150f * p * p; (sin(TAU * f * t) * 0.6f + sin(TAU * f * 2.01f * t) * 0.25f + (Random.nextFloat() - 0.5f) * 0.12f) * minOf(1f, p * 4f) }
        2 -> clip(0.45f) { t, p -> val f = 200f + 1800f * p; (sin(TAU * f * t) * 0.35f + (Random.nextFloat() - 0.5f) * 0.6f * p) * (1f - p * 0.3f) * minOf(1f, p * 6f) }
        3 -> clip(2.6f) { t, p -> val e = minOf(1f, p * 8f) * (1f - p).coerceAtLeast(0f)
            (sin(TAU * 220f * t) * 0.22f + sin(TAU * 330f * t + 0.5f * sin(TAU * 5f * t)) * 0.2f + sin(TAU * 440f * t) * 0.16f + sin(TAU * 880f * t) * 0.07f * (0.5f + 0.5f * sin(TAU * 3f * t))) * e }
        5 -> clip(0.25f) { t, p -> (sin(TAU * (880f + 900f * p) * t) * 0.5f + sin(TAU * 1760f * t) * 0.2f) * exp(-p * 3.5f) }
        6 -> clip(0.32f) { t, p -> val e = sin(PI.toFloat() * p); ((Random.nextFloat() - 0.5f) * 0.5f + sin(TAU * (400f + 900f * p) * t) * 0.25f) * e }
        7 -> clip(0.25f) { t, _ -> sin(TAU * (120f * exp(-t * 10f) + 50f) * t) * exp(-t * 12f) * 0.9f }
        else -> clip(0.6f) { t, _ -> (sin(TAU * (90f * exp(-t * 6f) + 40f) * t) * 0.9f + (Random.nextFloat() - 0.5f) * 0.9f * exp(-t * 9f)) * exp(-t * 3f) }
    }
    private val cache = HashMap<Int, ShortArray>()
    /** Plays one step of the gate sound (respects the Sound Effects setting). Safe to call from the UI thread: the work is done on a short thread. */
    fun play(step: Int) {
        if (!NeonSoundEngine.getSoundEffectsEnabled()) return
        Thread {
            try {
                val pcm = synchronized(cache) { cache.getOrPut(step) { render(step) } }
                val bytes = pcm.size * 2
                val track = AudioTrack.Builder()
                    .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                    .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(SR).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                    .setBufferSizeInBytes(bytes).setTransferMode(AudioTrack.MODE_STATIC).build()
                track.write(pcm, 0, pcm.size)
                track.setVolume(0.9f)
                track.play()
                Thread.sleep((pcm.size * 1000L / SR) + 250L)
                track.release()
            } catch (_: Throwable) { }
        }.start()
    }
}
