package com.neonrush.game

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

/**
 * AMMO DEPOT (Stage 173): bought shots for players who cannot collect enough armor. They sit in a RESERVE that never touches the armor you
 * collect or the Magazine Bay capacity. When the gun runs dry in a run, the reserve loads automatically (at most [RUN_CAP] shots per run, so a pack
 * lasts several runs and boss fights stay fair). Opens after the game has been installed [MIN_DAYS] days.
 * Play Console: two CONSUMABLE products (neonrush_ammo_1000 $0.99, neonrush_ammo_2400 $1.99).
 */
object AmmoDepot {
    const val MIN_DAYS = 3
    const val RUN_CAP = 400
    class Pack(val id: String, val shots: Int, val price: String)
    val PACKS = listOf(Pack("neonrush_ammo_1000", 1000, "$0.99"), Pack("neonrush_ammo_2400", 2400, "$1.99"))

    @Volatile var runUsed = 0
    var version by androidx.compose.runtime.mutableStateOf(0)
    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences("neonrush_ammodepot", Context.MODE_PRIVATE)

    fun newRun() { runUsed = 0 }
    fun reserve(ctx: Context): Int { @Suppress("UNUSED_VARIABLE") val v = version; return try { prefs(ctx).getInt("reserve", 0) } catch (_: Throwable) { 0 } }
    fun addReserve(ctx: Context, n: Int) {
        try { prefs(ctx).edit().putInt("reserve", (reserve(ctx) + n).coerceAtLeast(0)).apply() } catch (_: Throwable) {}
        version += 1
    }
    /** Shots the reserve can still give in this run. */
    fun runLeft(ctx: Context): Int = minOf(reserve(ctx), (RUN_CAP - runUsed).coerceAtLeast(0))
    /** Takes one shot from the reserve (respecting the per-run cap). */
    fun takeShot(ctx: Context): Boolean {
        if (runUsed >= RUN_CAP) return false
        val n = try { prefs(ctx).getInt("reserve", 0) } catch (_: Throwable) { 0 }
        if (n <= 0) return false
        try { prefs(ctx).edit().putInt("reserve", n - 1).apply() } catch (_: Throwable) { return false }
        runUsed += 1
        return true
    }

    fun daysInstalled(ctx: Context): Int = BombDepot.daysInstalled(ctx)
    fun daysLeft(ctx: Context): Int = (MIN_DAYS - daysInstalled(ctx)).coerceAtLeast(0)
    fun unlocked(ctx: Context): Boolean = daysLeft(ctx) == 0

    /** Hands out what a product id stands for (after a real purchase, or in testing). */
    fun deliver(ctx: Context, id: String) { PACKS.firstOrNull { it.id == id }?.let { addReserve(ctx, it.shots) } }
}
