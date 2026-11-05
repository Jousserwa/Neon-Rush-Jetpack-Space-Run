package com.neonrush.game

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * WINGMAN PERKS (Stage 169).
 *  - Collector (both tiers): while out, the wingman grabs gems and fuel in its lane.
 *  - Daily pill (both tiers): one free healing pill per day, on the first run of the day.
 *  - Free preview: one free call the first time you reach zone 30, so you can see what you would be buying.
 *  - ELITE ($9.99): Bullet screen (takes 1 enemy shot per second for you), Tow (once per run, at fuel 8% or less: +30% fuel), Rank 1-10, daily Contracts.
 */
object WingmanPerks {
    const val TOW_FUEL_AT = 8
    const val TOW_GIVES = 30
    var version by mutableStateOf(0)
    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences("neonrush_wingperks", Context.MODE_PRIVATE)
    private fun today(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    private fun dayIndex(): Int = (System.currentTimeMillis() / 86_400_000L).toInt()

    // ---- free preview ----
    fun previewUsed(ctx: Context): Boolean = try { prefs(ctx).getBoolean("previewUsed", false) } catch (_: Throwable) { true }
    fun usePreview(ctx: Context) { try { prefs(ctx).edit().putBoolean("previewUsed", true).apply() } catch (_: Throwable) {}; version++ }

    // ---- daily pill: true if a pill was just given ----
    fun dailyPill(ctx: Context): Boolean {
        return try {
            val p = prefs(ctx); if (p.getString("pillDay", "") == today()) false
            else { p.edit().putString("pillDay", today()).apply(); Wingman.addPill(ctx, 1); true }
        } catch (_: Throwable) { false }
    }

    // ---- pill pack (gems) ----
    const val PILL_PACK = 5
    const val PILL_PACK_GEMS = 100
}

/** RANK: 10 levels from sorties and hazards cleared. Level rewards are claimed on the Wingman page. */
object WingmanRank {
    private val XP_AT = intArrayOf(0, 40, 100, 180, 280, 400, 550, 730, 940, 1200)   // xp needed for level 1..10
    const val MAX = 10
    var version by mutableStateOf(0)
    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences("neonrush_wingrank", Context.MODE_PRIVATE)
    fun xp(ctx: Context): Int { @Suppress("UNUSED_VARIABLE") val v = version; return try { prefs(ctx).getInt("xp", 0) } catch (_: Throwable) { 0 } }
    fun addXp(ctx: Context, n: Int) { try { prefs(ctx).edit().putInt("xp", xp(ctx) + n).apply() } catch (_: Throwable) {}; version++ }
    fun level(ctx: Context): Int { val x = xp(ctx); var l = 1; for (i in 0 until MAX) if (x >= XP_AT[i]) l = i + 1; return l }
    fun xpIntoLevel(ctx: Context): Pair<Int, Int> { val l = level(ctx); if (l >= MAX) return 1 to 1; val x = xp(ctx); return (x - XP_AT[l - 1]) to (XP_AT[l] - XP_AT[l - 1]) }
    private fun claimed(ctx: Context): Int = try { prefs(ctx).getInt("claimed", 1) } catch (_: Throwable) { 1 }   // level 1 has no reward
    fun rewardText(l: Int): String = when (l) {
        2 -> "2 💊"; 3 -> "10 💎"; 4 -> "2 💊"; 5 -> "20 💎"; 6 -> "3 💊"; 7 -> "30 💎"; 8 -> "3 💊"; 9 -> "40 💎 + 1 🎟"; 10 -> "60 💎 + 5 💊 + 1 🎟"; else -> ""
    }
    /** Next level whose reward is waiting, or 0. */
    fun pending(ctx: Context): Int { @Suppress("UNUSED_VARIABLE") val v = version; val c = claimed(ctx); return if (level(ctx) > c) c + 1 else 0 }
    /** Claims the next waiting reward. Returns the GEMS the caller must credit (pills and vouchers are given here), or -1 if none waiting. */
    fun claim(ctx: Context): Int {
        val l = pending(ctx); if (l == 0) return -1
        try { prefs(ctx).edit().putInt("claimed", l).apply() } catch (_: Throwable) { return -1 }
        var gems = 0
        when (l) {
            2, 4 -> Wingman.addPill(ctx, 2); 6, 8 -> Wingman.addPill(ctx, 3)
            3 -> gems = 10; 5 -> gems = 20; 7 -> gems = 30
            9 -> { gems = 40; ScoreVouchers.addVouchers(ctx, 1) }
            10 -> { gems = 60; Wingman.addPill(ctx, 5); ScoreVouchers.addVouchers(ctx, 1) }
        }
        version++; return gems
    }
}

/** CONTRACTS (Elite): one daily task, pays 8 gems + 1 score voucher. */
object WingmanContracts {
    const val CLEAR = 0; const val CALLS = 1; const val COLLECT = 2
    private val NAMES = arrayOf("Clear 12 hazards", "Complete 2 wingman calls", "Collect 6 gems or fuel")
    private val GOALS = intArrayOf(12, 2, 6)
    const val GEMS = 8
    var version by mutableStateOf(0)
    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences("neonrush_wingcontract", Context.MODE_PRIVATE)
    private fun today(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    private fun kindToday(): Int = ((System.currentTimeMillis() / 86_400_000L).toInt()).mod(3)
    private fun roll(ctx: Context) { val p = prefs(ctx); if (p.getString("day", "") != today()) p.edit().putString("day", today()).putInt("prog", 0).putBoolean("claimed", false).apply() }
    fun name(): String = NAMES[kindToday()]
    fun goal(): Int = GOALS[kindToday()]
    fun progress(ctx: Context): Int { @Suppress("UNUSED_VARIABLE") val v = version; return try { roll(ctx); prefs(ctx).getInt("prog", 0) } catch (_: Throwable) { 0 } }
    fun claimed(ctx: Context): Boolean = try { roll(ctx); prefs(ctx).getBoolean("claimed", false) } catch (_: Throwable) { true }
    fun add(ctx: Context, kind: Int, n: Int) {
        if (kind != kindToday() || n <= 0) return
        try { roll(ctx); val p = prefs(ctx); p.edit().putInt("prog", (p.getInt("prog", 0) + n).coerceAtMost(goal())).apply() } catch (_: Throwable) {}
        version++
    }
    fun ready(ctx: Context): Boolean = progress(ctx) >= goal() && !claimed(ctx)
    /** Returns gems to credit (and gives the voucher), or -1. */
    fun claim(ctx: Context): Int {
        if (!ready(ctx)) return -1
        try { prefs(ctx).edit().putBoolean("claimed", true).apply() } catch (_: Throwable) { return -1 }
        ScoreVouchers.addVouchers(ctx, 1); version++; return GEMS
    }
}
