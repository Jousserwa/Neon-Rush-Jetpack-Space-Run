package com.neonrush.game

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * CRATE WEEK (Stage 169). A day COUNTS when you both opened the Daily Crate AND played a run that day.
 * Six counted days in a row = a full week. A full week pays [REFILLS] free automatic refuels, at most ONCE PER CALENDAR MONTH.
 * Miss a day and the week starts again from 1. Free refuels fire by themselves when fuel falls to 20% (no Pro, no switch, no gems needed).
 */
object CrateWeek {
    const val DAYS = 6
    const val REFILLS = 5
    var version by mutableStateOf(0)
    @Volatile private var cachedRefills = 0

    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences("neonrush_crateweek", Context.MODE_PRIVATE)
    private fun fmt() = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private fun today(): String = fmt().format(Date())
    private fun yesterday(): String { val c = Calendar.getInstance(); c.add(Calendar.DAY_OF_YEAR, -1); return fmt().format(c.time) }
    private fun month(): String = today().substring(0, 7)

    /** Consecutive counted days (0..6). Shows as 0 if you missed yesterday and have not counted today. */
    fun days(ctx: Context): Int {
        @Suppress("UNUSED_VARIABLE") val v = version
        return try {
            val p = prefs(ctx); val last = p.getString("last", "") ?: ""
            if (last == today() || last == yesterday()) p.getInt("streak", 0) else 0
        } catch (_: Throwable) { 0 }
    }
    fun countedToday(ctx: Context): Boolean = try { prefs(ctx).getString("last", "") == today() } catch (_: Throwable) { false }
    fun rewardedThisMonth(ctx: Context): Boolean = try { prefs(ctx).getString("rewardMonth", "") == month() } catch (_: Throwable) { false }

    fun freeRefills(ctx: Context): Int {
        @Suppress("UNUSED_VARIABLE") val v = version
        val n = try { prefs(ctx).getInt("refills", 0) } catch (_: Throwable) { 0 }
        cachedRefills = n; return n
    }
    /** For the sim loop. */
    fun refillsCached(): Int = cachedRefills
    /** Uses one free refuel (sim loop calls this when fuel is at 20% or less). */
    fun takeRefill(ctx: Context): Int {
        val n = freeRefills(ctx); if (n <= 0) return 0
        try { prefs(ctx).edit().putInt("refills", n - 1).apply() } catch (_: Throwable) {}
        cachedRefills = n - 1; version++
        return n - 1
    }

    /** Bonus wheel waiting to be spun: 0 none, 1 = 7-day crate streak (random prize), 2 = full week (always the 5 refills). */
    fun pendingWheel(ctx: Context): Int { @Suppress("UNUSED_VARIABLE") val v = version; return try { prefs(ctx).getInt("wheel", 0) } catch (_: Throwable) { 0 } }
    fun setWheel(ctx: Context, kind: Int) { try { val p = prefs(ctx); if (p.getInt("wheel", 0) < kind) p.edit().putInt("wheel", kind).apply() } catch (_: Throwable) {}; version++ }
    fun clearWheel(ctx: Context) { try { prefs(ctx).edit().putInt("wheel", 0).apply() } catch (_: Throwable) {}; version++ }
    fun addRefills(ctx: Context, n: Int) {
        try { val p = prefs(ctx); p.edit().putInt("refills", p.getInt("refills", 0) + n).apply() } catch (_: Throwable) {}
        cachedRefills += n; version++
    }

    fun markOpened(ctx: Context): Boolean = mark(ctx, "openDay")
    fun markPlayed(ctx: Context): Boolean = mark(ctx, "playDay")

    /** Records half of today's pair; when both halves are in, the day counts. Returns true if this call paid the monthly 5 refuels. */
    private fun mark(ctx: Context, key: String): Boolean {
        try {
            val p = prefs(ctx); val t = today()
            p.edit().putString(key, t).apply()
            if (p.getString("openDay", "") != t || p.getString("playDay", "") != t) return false
            val last = p.getString("last", "") ?: ""
            if (last == t) return false
            val streak = if (last == yesterday()) p.getInt("streak", 0) + 1 else 1
            val ed = p.edit().putString("last", t).putInt("streak", streak.coerceAtMost(DAYS))
            var paid = false
            if (streak >= DAYS && p.getString("rewardMonth", "") != month()) {
                ed.putString("rewardMonth", month()).putInt("wheel", 2).putInt("streak", 0)   // the 5 refills are paid by the bonus wheel (it always lands on them)
                paid = true
            }
            ed.apply(); version++
            return paid
        } catch (_: Throwable) { return false }
    }
}
