package com.neonrush.game

import android.content.Context

/**
 * RIVAL ECHOES: your own best run on each difficulty is kept as an "echo" and flown again beside you in every new run.
 * While you fly, the race samples your distance and height twice a second (RivalEcho.sample). When a run ends with a new best score
 * on that difficulty, the path is saved (commit). The echo is a replay by TIME, so you can see whether you are ahead of or behind your best self.
 */
object RivalEcho {
    private const val MAX = 1500                        // 12 minutes of samples at 2 per second
    private val cur = ArrayList<FloatArray>()           // each = [t seconds, distance m, y 0..100]
    private var best: List<FloatArray> = emptyList()
    @Volatile var bestScore: Int = 0
        private set

    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences("neonrush_echo", Context.MODE_PRIVATE)

    /** Starts a fresh recording and loads the echo saved for [key] (the difficulty name). */
    fun begin(ctx: Context, key: String) {
        cur.clear()
        try {
            val sp = prefs(ctx)
            bestScore = sp.getInt("score_$key", 0)
            best = (sp.getString("path_$key", "") ?: "").split(";").mapNotNull { row ->
                val p = row.split(","); if (p.size != 3) null else try { floatArrayOf(p[0].toFloat(), p[1].toFloat(), p[2].toFloat()) } catch (_: Throwable) { null }
            }
        } catch (_: Throwable) { best = emptyList(); bestScore = 0 }
    }

    fun hasEcho(): Boolean = best.size >= 4
    fun duration(): Float = best.lastOrNull()?.get(0) ?: 0f
    fun sample(t: Float, dist: Float, y: Int) { if (cur.size < MAX) cur.add(floatArrayOf(t, dist, y.toFloat())) }

    /** The echo's [distance, y] at time [t], or null once the echo's run has ended (or if there is none). */
    fun at(t: Float): FloatArray? {
        val b = best
        if (b.size < 4 || t > b.last()[0]) return null
        var lo = 0; var hi = b.size - 1
        while (hi - lo > 1) { val m = (lo + hi) / 2; if (b[m][0] <= t) lo = m else hi = m }
        val a = b[lo]; val c = b[hi]
        val k = if (c[0] > a[0]) ((t - a[0]) / (c[0] - a[0])).coerceIn(0f, 1f) else 0f
        return floatArrayOf(a[1] + (c[1] - a[1]) * k, a[2] + (c[2] - a[2]) * k)
    }

    /** Called once when a run ends. Saves this run as the new echo if its score beats the old one. Returns true when it did. */
    fun commit(ctx: Context, key: String, score: Int): Boolean {
        if (score <= 0 || cur.size < 4) { cur.clear(); return false }
        return try {
            val sp = prefs(ctx)
            if (score <= sp.getInt("score_$key", 0)) { false } else {
                val sb = StringBuilder()
                cur.forEachIndexed { i, r -> if (i > 0) sb.append(';'); sb.append("%.1f,%.0f,%.0f".format(java.util.Locale.US, r[0], r[1], r[2])) }
                sp.edit().putInt("score_$key", score).putString("path_$key", sb.toString()).apply()
                true
            }
        } catch (_: Throwable) { false } finally { cur.clear() }
    }
}
