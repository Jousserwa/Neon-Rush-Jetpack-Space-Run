package com.neonrush.game

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.neonrush.game.db.GameDao
import java.util.Calendar
import java.util.Random

/**
 * PILOT CONTRACTS: three small goals every day, picked from the date (so they change at midnight) and sized to the player's own bests.
 * Progress is counted when a run ends. Completed contracts are collected with a tap; collecting all three pays a bonus.
 * Pro pilots may reroll one unfinished contract per day. Rewards are small, permanent gems.
 */
object Contracts {
    const val COUNT = 3
    const val BONUS = 15
    val TYPES = listOf("ZONE", "SCORE", "RUNS", "GEMS", "COMBO")

    fun reward(type: String) = when (type) { "ZONE" -> 8; "SCORE" -> 10; "RUNS" -> 5; "GEMS" -> 8; else -> 10 }
    fun label(type: String, target: Int) = when (type) {
        "ZONE" -> "Reach zone $target in one run"
        "SCORE" -> "Score $target in one run"
        "RUNS" -> "Play $target runs today"
        "GEMS" -> "Collect $target gems today"
        else -> "Hit a $target combo in one run"
    }
    fun icon(type: String) = when (type) { "ZONE" -> "🗺️"; "SCORE" -> "⭐"; "RUNS" -> "🏃"; "GEMS" -> "💎"; else -> "🔥" }

    class Item(val type: String, val target: Int, val prog: Int, val claimed: Boolean) {
        val done: Boolean get() = prog >= target
        val frac: Float get() = (prog.toFloat() / target.coerceAtLeast(1)).coerceIn(0f, 1f)
    }

    var version by mutableStateOf(0)
    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences("neonrush_contracts", Context.MODE_PRIVATE)
    fun dayNum(): Int { val c = Calendar.getInstance(); return c.get(Calendar.YEAR) * 366 + c.get(Calendar.DAY_OF_YEAR) }
    fun hoursLeft(): Int = (24 - Calendar.getInstance().get(Calendar.HOUR_OF_DAY)).coerceIn(1, 24)

    private fun targetFor(type: String, r: Random, bestZone: Int, bestScore: Int, bestCombo: Int): Int = when (type) {
        "ZONE" -> (3 + (bestZone * 0.5f).toInt() + r.nextInt(3)).coerceAtMost(150)
        "SCORE" -> (maxOf(100, bestScore / 2) / 10) * 10 + r.nextInt(3) * 10
        "RUNS" -> 2 + r.nextInt(3)
        "GEMS" -> 15 + 5 * r.nextInt(4)
        else -> maxOf(6, (bestCombo * 0.6f).toInt()) + r.nextInt(3)
    }

    /** Makes today's three contracts if they do not exist yet (safe to call again and again). */
    fun ensure(ctx: Context, bestZone: Int, bestScore: Int, bestCombo: Int) {
        try {
            val sp = prefs(ctx); val d = dayNum()
            if (sp.getInt("day", -1) == d) return
            val r = Random(d * 7919L + 17L)
            val types = TYPES.toMutableList().also { java.util.Collections.shuffle(it, r) }.take(COUNT)
            val e = sp.edit().putInt("day", d).putInt("rerolled", 0).putBoolean("bonus", false)
            types.forEachIndexed { i, t -> e.putString("t$i", t).putInt("g$i", targetFor(t, r, bestZone, bestScore, bestCombo)).putInt("p$i", 0).putBoolean("c$i", false) }
            e.apply()
        } catch (_: Throwable) { }
    }

    fun items(ctx: Context): List<Item> {
        @Suppress("UNUSED_VARIABLE") val v = version
        return try {
            val sp = prefs(ctx)
            if (sp.getInt("day", -1) != dayNum()) emptyList()
            else (0 until COUNT).map { Item(sp.getString("t$it", "RUNS") ?: "RUNS", sp.getInt("g$it", 3), sp.getInt("p$it", 0), sp.getBoolean("c$it", false)) }
        } catch (_: Throwable) { emptyList() }
    }
    fun rerolled(ctx: Context): Boolean { @Suppress("UNUSED_VARIABLE") val v = version; return try { prefs(ctx).getInt("rerolled", 0) == 1 } catch (_: Throwable) { true } }
    fun allClaimed(ctx: Context): Boolean = items(ctx).let { it.size == COUNT && it.all { x -> x.claimed } }

    /** Called when a run ends. */
    fun runEnded(ctx: Context, zone: Int, score: Int, gems: Int, combo: Int) {
        try {
            val sp = prefs(ctx)
            if (sp.getInt("day", -1) != dayNum()) return
            val e = sp.edit()
            for (i in 0 until COUNT) {
                val p = sp.getInt("p$i", 0)
                val np = when (sp.getString("t$i", "")) {
                    "ZONE" -> maxOf(p, zone); "SCORE" -> maxOf(p, score); "COMBO" -> maxOf(p, combo)
                    "RUNS" -> p + 1; "GEMS" -> p + gems; else -> p
                }
                e.putInt("p$i", np)
            }
            e.apply()
        } catch (_: Throwable) { }
        version += 1
    }

    /** Pays one finished contract (and the all-three bonus when it is the last). Returns the gems paid. */
    suspend fun claim(ctx: Context, dao: GameDao, i: Int): Int {
        val list = items(ctx)
        val it = list.getOrNull(i) ?: return 0
        if (!it.done || it.claimed) return 0
        var pay = reward(it.type)
        val sp = prefs(ctx)
        sp.edit().putBoolean("c$i", true).apply()
        if (list.indices.all { j -> j == i || list[j].claimed } && !sp.getBoolean("bonus", false)) { pay += BONUS; sp.edit().putBoolean("bonus", true).apply() }
        dao.updateProfile { p -> p.copy(gems = p.gems + pay, totalGemsEarned = p.totalGemsEarned + pay) }
        version += 1
        return pay
    }

    /** Pro: swap one unfinished contract for a new one (once a day). */
    fun reroll(ctx: Context, i: Int, bestZone: Int, bestScore: Int, bestCombo: Int): Boolean {
        return try {
            val sp = prefs(ctx); val list = items(ctx)
            val cur = list.getOrNull(i) ?: return false
            if (sp.getInt("rerolled", 0) == 1 || cur.done || cur.claimed) return false
            val used = list.map { it.type }.toSet()
            val r = Random(System.currentTimeMillis())
            val t = TYPES.filter { it !in used }.ifEmpty { TYPES.filter { it != cur.type } }.random(kotlin.random.Random(r.nextLong()))
            sp.edit().putString("t$i", t).putInt("g$i", targetFor(t, r, bestZone, bestScore, bestCombo)).putInt("p$i", 0).putInt("rerolled", 1).apply()
            version += 1
            true
        } catch (_: Throwable) { false }
    }
}
