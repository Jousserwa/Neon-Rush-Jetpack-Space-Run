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
 * the pilot is invulnerable from the omen to the snap back and can still steer. One gate every 5 to 9 minutes of flying, at most 2 per run, never in the first
 * 3 runs, never in Legendary, never near a boss, and it can be turned off in Settings.
 */
object PhaseGate {
    const val MIN_SEC = 300f
    const val MAX_SEC = 540f
    const val MAX_PER_RUN = 2
    const val MIN_RUNS = 3
    const val T_OMEN = 0.6f
    const val T_PULL = 0.4f
    const val T_GLIMPSE = 2.5f
    const val T_BACK = 0.5f
    const val KINDS = 4                       // 0 crystal sea, 1 storm city, 2 hangar teaser, 3 gem gift
    const val GIFT_GEMS = 3

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
        return try { val sp = prefs(ctx); val n = sp.getInt("seq", 0); sp.edit().putInt("seq", n + 1).putInt("seen", sp.getInt("seen", 0) + 1).apply(); n % KINDS } catch (_: Throwable) { Random.nextInt(KINDS) }
    }
    fun timesSeen(ctx: Context): Int = try { prefs(ctx).getInt("seen", 0) } catch (_: Throwable) { 0 }

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
