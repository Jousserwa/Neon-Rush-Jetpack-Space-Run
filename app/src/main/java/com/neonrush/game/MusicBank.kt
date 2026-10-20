package com.neonrush.game

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Log
import java.util.concurrent.ConcurrentHashMap

/**
 * Loop music: tiny .ogg files in res/raw are decoded once to PCM and mixed (with crossfades)
 * into the game's own audio stream by NeonSoundEngine. Keys: "menu", "w1".."w8", "boss".
 */
object MusicBank {
    private const val TAG = "MusicBank"
    private const val OUT_SR = 44100
    private var ctx: Context? = null
    @Volatile private var desired: String? = null
    @Volatile var suspended = false
    @Volatile var musicVolume = 0.6f
    private val cache = ConcurrentHashMap<String, ShortArray>()
    private val loading = ConcurrentHashMap.newKeySet<String>()

    private class Chan(val key: String, val pcm: ShortArray) { var pos = 0; var gain = 0f }
    private val chans = ArrayList<Chan>()          // render thread only
    @Volatile var audible = false

    private fun resId(key: String): Int = when (key) {
        "menu" -> R.raw.music_menu
        "w1" -> R.raw.music_w1
        "w2" -> R.raw.music_w2
        "w3" -> R.raw.music_w3
        "w4" -> R.raw.music_w4
        "w5" -> R.raw.music_w5
        "w6" -> R.raw.music_w6
        "w7" -> R.raw.music_w7
        "w8" -> R.raw.music_w8
        "boss" -> R.raw.music_boss
        else -> 0
    }

    fun init(context: Context) { ctx = context.applicationContext }

    /** null = silence. Safe to call often from any thread. */
    fun setTrack(key: String?) {
        desired = key
        if (key != null) request(key)
    }

    private fun request(key: String) {
        if (cache.containsKey(key) || !loading.add(key)) return
        Thread {
            try {
                val c = ctx
                val id = resId(key)
                if (c != null && id != 0) decode(c, id)?.let { cache[key] = it }
            } catch (e: Throwable) {
                Log.e(TAG, "decode $key failed: ${e.message}")
            } finally { loading.remove(key) }
        }.apply { name = "MusicDecode-$key"; isDaemon = true; start() }
    }

    private fun decode(c: Context, id: Int): ShortArray? {
        val afd = c.resources.openRawResourceFd(id)
        val ex = MediaExtractor()
        try { ex.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length) } finally { afd.close() }
        var track = -1
        for (i in 0 until ex.trackCount) {
            if (ex.getTrackFormat(i).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) { track = i; break }
        }
        if (track < 0) { ex.release(); return null }
        ex.selectTrack(track)
        val fmt = ex.getTrackFormat(track)
        val codec = MediaCodec.createDecoderByType(fmt.getString(MediaFormat.KEY_MIME)!!)
        codec.configure(fmt, null, null, 0)
        codec.start()
        var srcRate = fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        var chCount = fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        var raw = ShortArray(OUT_SR * 2 * 24)
        var n = 0
        val info = MediaCodec.BufferInfo()
        var inDone = false; var outDone = false
        while (!outDone) {
            if (!inDone) {
                val ii = codec.dequeueInputBuffer(10_000)
                if (ii >= 0) {
                    val buf = codec.getInputBuffer(ii)!!
                    val sz = ex.readSampleData(buf, 0)
                    if (sz < 0) { codec.queueInputBuffer(ii, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inDone = true }
                    else { codec.queueInputBuffer(ii, 0, sz, ex.sampleTime, 0); ex.advance() }
                }
            }
            val oi = codec.dequeueOutputBuffer(info, 10_000)
            if (oi >= 0) {
                val ob = codec.getOutputBuffer(oi)!!
                ob.position(info.offset); ob.limit(info.offset + info.size)
                val sb = ob.order(java.nio.ByteOrder.nativeOrder()).asShortBuffer()
                val cnt = sb.remaining()
                if (n + cnt > raw.size) raw = raw.copyOf(maxOf(raw.size * 2, n + cnt))
                sb.get(raw, n, cnt); n += cnt
                codec.releaseOutputBuffer(oi, false)
                if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outDone = true
            } else if (oi == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                val of = codec.outputFormat
                srcRate = of.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                chCount = of.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            }
        }
        codec.stop(); codec.release(); ex.release()
        if (n == 0) return null
        // to stereo 44.1k
        val frames = n / chCount
        val outFrames = (frames.toLong() * OUT_SR / srcRate).toInt()
        val out = ShortArray(outFrames * 2)
        for (i in 0 until outFrames) {
            val p = i.toDouble() * srcRate / OUT_SR
            val i0 = p.toInt().coerceAtMost(frames - 1); val i1 = (i0 + 1).coerceAtMost(frames - 1)
            val f = (p - i0).toFloat()
            for (ch in 0..1) {
                val cc = if (chCount == 1) 0 else ch
                val a = raw[i0 * chCount + cc].toInt(); val b = raw[i1 * chCount + cc].toInt()
                out[i * 2 + ch] = (a + (b - a) * f).toInt().toShort()
            }
        }
        return out
    }

    /** Called on the audio render thread once per block. Adds music into l/r (already scaled by [scale]). */
    fun mix(l: FloatArray, r: FloatArray, n: Int, enabled: Boolean, scale: Float) {
        val want = if (enabled && !suspended) desired else null
        if (want != null && chans.none { it.key == want }) {
            cache[want]?.let { chans.add(Chan(want, it)) }
        }
        if (chans.isEmpty()) { audible = false; return }
        val vol = scale * musicVolume
        val fadeSec = if (want == "boss") 0.5f else 1.6f
        val stepBlock = n / (OUT_SR * fadeSec)
        val it = chans.iterator()
        var any = false
        while (it.hasNext()) {
            val ch = it.next()
            val target = if (ch.key == want) 1f else 0f
            val g0 = ch.gain
            val g1 = if (g0 < target) minOf(target, g0 + stepBlock) else maxOf(target, g0 - stepBlock)
            val pcm = ch.pcm; val len = pcm.size / 2
            var pos = ch.pos
            for (i in 0 until n) {
                val g = (g0 + (g1 - g0) * i / n) * vol * (1f / 32768f)
                l[i] += pcm[pos * 2] * g
                r[i] += pcm[pos * 2 + 1] * g
                pos++; if (pos >= len) pos = 0
            }
            ch.pos = pos; ch.gain = g1
            if (g1 <= 0f && target == 0f) it.remove() else any = true
        }
        audible = any
        // free memory of tracks nobody wants
        if (cache.size > 3) for (k in cache.keys) if (k != desired && chans.none { c -> c.key == k }) { cache.remove(k); break }
    }
}
