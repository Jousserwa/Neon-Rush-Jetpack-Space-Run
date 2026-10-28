package com.neonrush.game

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.neonrush.game.db.GameDao
import java.util.Calendar

/**
 * BEACON CHAIN: fly at least one run a day to light the next beacon of a 7-beacon chain. Missing days does not wipe the chain,
 * it COOLS it (one beacon goes dark per missed day). Each lit beacon pays a few gems; the 7th pays a big bonus (bigger for Pro).
 * Every finished chain adds a layer to the tower on the Home screen.
 */
object BeaconChain {
    const val LENGTH = 7
    fun reward(beacon: Int, isPro: Boolean): Int = when {
        beacon >= LENGTH -> if (isPro) 20 else 10
        else -> 2
    }

    var version by mutableStateOf(0)
    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences("neonrush_beacon", Context.MODE_PRIVATE)
    private fun dayNum(): Int { val c = Calendar.getInstance(); return c.get(Calendar.YEAR) * 366 + c.get(Calendar.DAY_OF_YEAR) }

    fun lit(ctx: Context): Int { @Suppress("UNUSED_VARIABLE") val v = version; return try { prefs(ctx).getInt("lit", 0) } catch (_: Throwable) { 0 } }
    fun layers(ctx: Context): Int { @Suppress("UNUSED_VARIABLE") val v = version; return try { prefs(ctx).getInt("layers", 0) } catch (_: Throwable) { 0 } }
    fun playedToday(ctx: Context): Boolean { @Suppress("UNUSED_VARIABLE") val v = version; return try { prefs(ctx).getInt("last", -1) == dayNum() } catch (_: Throwable) { false } }
    fun claimedToday(ctx: Context): Boolean { @Suppress("UNUSED_VARIABLE") val v = version; return try { prefs(ctx).getInt("claimedDay", -1) == dayNum() } catch (_: Throwable) { true } }
    fun canClaim(ctx: Context): Boolean = playedToday(ctx) && !claimedToday(ctx) && lit(ctx) > 0

    /** Called when a run ends: lights today's beacon (once a day) and cools the chain for any missed days. */
    fun runEnded(ctx: Context) {
        try {
            val sp = prefs(ctx); val d = dayNum(); val last = sp.getInt("last", -1)
            if (last == d) return
            var lit = sp.getInt("lit", 0)
            if (last >= 0) { val missed = (d - last - 1).coerceAtLeast(0); if (missed > 0) lit = (lit - missed).coerceAtLeast(0) }
            lit = (lit + 1).coerceAtMost(LENGTH)
            sp.edit().putInt("last", d).putInt("lit", lit).apply()
        } catch (_: Throwable) { }
        version += 1
    }

    /** Pays today's beacon. The 7th beacon completes the chain: the tower gains a layer and the chain starts again. */
    suspend fun claim(ctx: Context, dao: GameDao, isPro: Boolean): Int {
        if (!canClaim(ctx)) return 0
        val sp = prefs(ctx); val lit = lit(ctx)
        val pay = reward(lit, isPro)
        val e = sp.edit().putInt("claimedDay", dayNum())
        if (lit >= LENGTH) { e.putInt("lit", 0).putInt("layers", sp.getInt("layers", 0) + 1); FusionCores.award(ctx, kotlin.random.Random.nextInt(3), 2) }   // a finished chain also drops 2 fusion cores
        e.apply()
        dao.updateProfile { p -> p.copy(gems = p.gems + pay, totalGemsEarned = p.totalGemsEarned + pay) }
        version += 1
        return pay
    }
}
