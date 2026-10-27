package com.neonrush.game

import android.content.Context
import android.content.SharedPreferences
import com.neonrush.game.db.GameDao
import java.util.Calendar
import kotlin.math.min

/**
 * STORM GEM LIMITS: Storm Run may pay at most [DAILY_CAP] gems per calendar day (tiers, streak, rank-ups and the ad double all
 * count), and every gem it pays is "storm bonus gems": unspent ones are taken back after [LIFE_MS] (2 days), so they cannot be
 * hoarded. Spending always uses the oldest storm gems first.
 */
object StormBonus {
    const val DAILY_CAP = 100
    const val LIFE_MS = 48L * 60L * 60L * 1000L
    private var sp: SharedPreferences? = null
    fun init(c: Context) { if (sp == null) sp = c.applicationContext.getSharedPreferences("neonrush_stormbonus", Context.MODE_PRIVATE) }

    private fun day(): Int { val c = Calendar.getInstance(); return c.get(Calendar.YEAR) * 10000 + (c.get(Calendar.MONTH) + 1) * 100 + c.get(Calendar.DAY_OF_MONTH) }
    private fun gi(k: String) = try { sp?.getInt(k, 0) ?: 0 } catch (_: Throwable) { 0 }
    private fun pi(k: String, v: Int) { try { sp?.edit()?.putInt(k, v)?.apply() } catch (_: Throwable) { } }

    fun earnedToday(): Int = if (gi("capDay") == day()) gi("capEarned") else 0
    fun room(): Int = (DAILY_CAP - earnedToday()).coerceAtLeast(0)

    /** Takes up to [n] gems from today's allowance and logs them as expiring. Returns what was really granted (0..n). */
    fun take(n: Int): Int {
        val g = min(n, room())
        if (g <= 0) return 0
        pi("capDay", day()); pi("capEarned", earnedToday() + g)
        val list = load().toMutableList(); list.add(System.currentTimeMillis() to g); save(list)
        return g
    }

    private fun load(): List<Pair<Long, Int>> = try {
        (sp?.getString("grants", "") ?: "").split(";").filter { it.contains(":") }.map { val p = it.split(":"); p[0].toLong() to p[1].toInt() }
    } catch (_: Throwable) { emptyList() }
    private fun save(l: List<Pair<Long, Int>>) { try { sp?.edit()?.putString("grants", l.joinToString(";") { "${it.first}:${it.second}" })?.apply() } catch (_: Throwable) { } }

    /** Gems still counted as expiring storm gems, and the hours left on the oldest batch. */
    fun liveAmount(): Int = load().filter { it.first + LIFE_MS > System.currentTimeMillis() }.sumOf { it.second }
    fun hoursLeftOldest(): Int {
        val now = System.currentTimeMillis()
        val o = load().filter { it.first + LIFE_MS > now }.minByOrNull { it.first } ?: return 0
        return (((o.first + LIFE_MS - now) + 3_599_999L) / 3_600_000L).toInt()
    }

    /** Takes back expired storm gems and forgets batches that were already spent. Call on start and about once a minute. */
    suspend fun applyExpiry(dao: GameDao) {
        try {
            val all = load(); if (all.isEmpty()) return
            val now = System.currentTimeMillis()
            val expired = all.filter { it.first + LIFE_MS <= now }.sumOf { it.second }
            val kept = all.filter { it.first + LIFE_MS > now }.toMutableList()
            dao.updateProfile { p ->
                val g = p.gems - min(expired, p.gems).coerceAtLeast(0)
                var sum = kept.sumOf { it.second }
                while (sum > g && kept.isNotEmpty()) { val f = kept.removeAt(0); sum -= f.second }   // spent first = oldest first
                p.copy(gems = g)
            }
            save(kept)
        } catch (_: Throwable) { }
    }
}
