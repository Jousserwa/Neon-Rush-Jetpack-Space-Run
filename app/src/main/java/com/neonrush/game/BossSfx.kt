package com.neonrush.game

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tanh
import kotlin.random.Random

/**
 * Synthesised combat sounds (no audio files, so no size added to the app):
 *  charge()  rising whine while a boss powers up a shot
 *  fire()    sharp crack + low thump when it fires
 *  warn()    double beep when a new threat is about to appear
 *  whoosh()  a shadow clone slipping in
 *  zap()     small energy bolt
 * Built once on a background thread into static AudioTracks, then replayed instantly.
 */
object BossSfx {
    @Volatile var enabled = true
    private const val SR = 22050
    private val clips = ConcurrentHashMap<String, AudioTrack>()
    @Volatile private var building = false

    fun init() {
        if (building || clips.isNotEmpty()) return
        building = true
        Thread {
            try {
                clips["charge"] = make(chargeData())
                clips["fire"] = make(fireData())
                clips["warn"] = make(warnData())
                clips["whoosh"] = make(whooshData())
                clips["zap"] = make(zapData())
            } catch (_: Throwable) {
            } finally { building = false }
        }.start()
    }

    fun charge() = play("charge", 0.7f)
    fun fire() = play("fire", 0.9f)
    fun warn() = play("warn", 0.55f)
    fun whoosh() = play("whoosh", 0.6f)
    fun zap() = play("zap", 0.5f)

    private fun play(name: String, vol: Float) {
        if (!enabled) return
        val t = clips[name]
        if (t == null) { init(); return }
        try { t.stop(); t.reloadStaticData(); t.setVolume(vol); t.play() } catch (_: Throwable) {}
    }

    private fun make(data: ShortArray): AudioTrack {
        val t = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
            )
            .setAudioFormat(
                AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SR).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build()
            )
            .setBufferSizeInBytes(data.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        t.write(data, 0, data.size)
        return t
    }

    private fun s(x: Float): Short = (x.coerceIn(-1f, 1f) * 32000f).toInt().toShort()

    private fun chargeData(): ShortArray {
        val dur = 0.6f; val n = (SR * dur).toInt(); val out = ShortArray(n)
        var ph = 0f; val rnd = Random(7)
        for (i in 0 until n) {
            val t = i / SR.toFloat(); val u = t / dur
            val f = 200f + 900f * u * u
            ph += (2 * PI * f / SR).toFloat()
            val env = u.pow(1.2f) * 0.85f * (if (u > 0.94f) (1f - u) / 0.06f else 1f)
            val trem = 0.75f + 0.25f * sin(2 * PI.toFloat() * 28f * t)
            val v = (sin(ph) * 0.6f + sin(2 * ph) * 0.25f + (rnd.nextFloat() - 0.5f) * 0.16f) * env * trem
            out[i] = s(v)
        }
        return out
    }

    private fun fireData(): ShortArray {
        val dur = 0.38f; val n = (SR * dur).toInt(); val out = ShortArray(n)
        val rnd = Random(11); var lp = 0f
        for (i in 0 until n) {
            val t = i / SR.toFloat()
            val noise = rnd.nextFloat() * 2f - 1f
            lp += 0.33f * (noise - lp)
            val crack = noise * exp(-t * 70f)
            val body = lp * exp(-t * 13f)
            val thump = sin(2 * PI.toFloat() * (95f - 55f * t) * t) * exp(-t * 11f)
            out[i] = s(tanh(1.7f * (0.7f * crack + 0.9f * body + 0.95f * thump)) * 0.9f)
        }
        return out
    }

    private fun warnData(): ShortArray {
        val dur = 0.32f; val n = (SR * dur).toInt(); val out = ShortArray(n)
        for (i in 0 until n) {
            val t = i / SR.toFloat()
            val on = (t < 0.09f) || (t in 0.16f..0.25f)
            if (on) {
                val lt = if (t < 0.09f) t else t - 0.16f
                val env = (1f - lt / 0.09f).coerceIn(0f, 1f)
                val sq = if (sin(2 * PI.toFloat() * 1000f * t) > 0f) 1f else -1f
                out[i] = s((sq * 0.35f + sin(2 * PI.toFloat() * 1000f * t) * 0.3f) * env)
            }
        }
        return out
    }

    private fun whooshData(): ShortArray {
        val dur = 0.38f; val n = (SR * dur).toInt(); val out = ShortArray(n)
        val rnd = Random(3); var lp = 0f
        for (i in 0 until n) {
            val t = i / SR.toFloat(); val u = t / dur
            val a = 0.05f + 0.5f * sin(PI.toFloat() * u)
            lp += a * ((rnd.nextFloat() * 2f - 1f) - lp)
            out[i] = s(lp * sin(PI.toFloat() * u) * 1.4f)
        }
        return out
    }

    private fun zapData(): ShortArray {
        val dur = 0.14f; val n = (SR * dur).toInt(); val out = ShortArray(n)
        var ph = 0f
        for (i in 0 until n) {
            val t = i / SR.toFloat()
            ph += (2 * PI * (1900f - 9000f * t) / SR).toFloat()
            out[i] = s(sin(ph) * exp(-t * 22f) * 0.8f)
        }
        return out
    }
}
