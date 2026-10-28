package com.neonrush.game

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.neonrush.game.db.GameDao
import java.util.Calendar
import java.util.Random

/**
 * MYSTERY REALM DOORS: once a day, after you finish a run, three doors appear. Pick one. Each hides a prize that was fixed for the day
 * (closing the app does not change it). Most prizes are small, a few are lucky. Pro pilots also get to peek behind the two doors they did not open.
 */
object MysteryDoors {
    val NAMES = listOf("DUNE DOOR", "NEON DOOR", "VOID DOOR")
    val COLORS = listOf(0xFFFFB74D, 0xFFFF4DD2, 0xFF7C4DFF)
    /** Possible prizes with weights; 0 = only dust. Average is about 3.8 gems. */
    private val PRIZES = listOf(0 to 20, 2 to 30, 4 to 25, 6 to 15, 10 to 8, 25 to 2)

    var version by mutableStateOf(0)
    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences("neonrush_doors", Context.MODE_PRIVATE)
    private fun dayNum(): Int { val c = Calendar.getInstance(); return c.get(Calendar.YEAR) * 366 + c.get(Calendar.DAY_OF_YEAR) }

    private fun roll(r: Random): Int {
        var x = r.nextInt(PRIZES.sumOf { it.second })
        for ((v, w) in PRIZES) { if (x < w) return v; x -= w }
        return 2
    }

    private fun ensure(ctx: Context) {
        val sp = prefs(ctx); val d = dayNum()
        if (sp.getInt("day", -1) == d) return
        val r = Random(d * 104729L + 31L)
        sp.edit().putInt("day", d).putBoolean("earned", false).putInt("pick", -1)
            .putInt("r0", roll(r)).putInt("r1", roll(r)).putInt("r2", roll(r)).apply()
    }

    /** Called when a run ends: today's doors are earned. */
    fun runEnded(ctx: Context) {
        try { ensure(ctx); val sp = prefs(ctx); if (!sp.getBoolean("earned", false)) { sp.edit().putBoolean("earned", true).apply(); version += 1 } } catch (_: Throwable) { }
    }
    fun earned(ctx: Context): Boolean { @Suppress("UNUSED_VARIABLE") val v = version; return try { ensure(ctx); prefs(ctx).getBoolean("earned", false) } catch (_: Throwable) { false } }
    /** -1 = nothing opened yet today. */
    fun picked(ctx: Context): Int { @Suppress("UNUSED_VARIABLE") val v = version; return try { ensure(ctx); prefs(ctx).getInt("pick", -1) } catch (_: Throwable) { 0 } }
    fun prize(ctx: Context, i: Int): Int = try { prefs(ctx).getInt("r$i", 0) } catch (_: Throwable) { 0 }
    fun canOpen(ctx: Context): Boolean = earned(ctx) && picked(ctx) < 0

    /** Opens door [i] and pays its prize. Returns the gems (0 = dust). */
    suspend fun open(ctx: Context, dao: GameDao, i: Int): Int {
        if (!canOpen(ctx) || i !in 0..2) return 0
        val g = prize(ctx, i)
        prefs(ctx).edit().putInt("pick", i).apply()
        if (g > 0) dao.updateProfile { p -> p.copy(gems = p.gems + g, totalGemsEarned = p.totalGemsEarned + g) }
        version += 1
        return g
    }
}
