package com.neonrush.game

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

/**
 * AMMO DEPOT (Stage 173, reworked 174): bought shots for players who cannot collect enough armor. They wait in a RESERVE that never touches the armor you
 * collect. The reserve fills the gun's magazine at the start of a run AND again by itself during a run whenever the gun runs completely dry (Stage 176):
 * it loads a fresh magazine up to the Magazine Bay capacity, with a loud reload sound. Opens after [MIN_DAYS] days of playing.
 * Each pack can be bought at most [MAX_BUYS] times (also used for the bomb packs). Play Console: two CONSUMABLE products.
 */
object AmmoDepot {
    const val MIN_DAYS = 3
    const val MAX_BUYS = 2
    class Pack(val id: String, val shots: Int, val price: String)
    val PACKS = listOf(Pack("neonrush_ammo_1000", 1000, "$0.99"), Pack("neonrush_ammo_2400", 2400, "$1.99"))

    var version by androidx.compose.runtime.mutableStateOf(0)
    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences("neonrush_ammodepot", Context.MODE_PRIVATE)

    fun reserve(ctx: Context): Int { @Suppress("UNUSED_VARIABLE") val v = version; return try { prefs(ctx).getInt("reserve", 0) } catch (_: Throwable) { 0 } }
    fun addReserve(ctx: Context, n: Int) {
        try { prefs(ctx).edit().putInt("reserve", (reserve(ctx) + n).coerceAtLeast(0)).apply() } catch (_: Throwable) {}
        version += 1
    }
    /** Set when a reload from the reserve happens during a run (the shooting layer watches this for the sound and banner), and how many shots it loaded. */
    @Volatile var reloadMs = 0L
    @Volatile var reloadN = 0
    /** Mid-run: the gun is completely empty, so load a fresh magazine from the reserve (up to the Magazine Bay capacity). Returns the shots loaded. */
    fun autoReload(ctx: Context): Int {
        val r = reserve(ctx)
        if (r <= 0) return 0
        if (Shooting.ammo(ctx) > 0) return 0
        val added = Shooting.addArmor(ctx, minOf(MagazineBay.capacity(ctx), r))
        if (added > 0) { addReserve(ctx, -added); reloadN = added; reloadMs = System.currentTimeMillis() }
        return added
    }
    /** Run start: move shots from the reserve into the magazine, as many as fit. */
    fun topUp(ctx: Context) {
        val r = reserve(ctx)
        if (r <= 0) return
        val room = (MagazineBay.capacity(ctx) - Shooting.ammo(ctx)).coerceAtLeast(0)
        val added = Shooting.addArmor(ctx, minOf(room, r))
        if (added > 0) addReserve(ctx, -added)
    }

    // ---- purchase limit: any pack (ammo or bomb) can be bought at most MAX_BUYS times ----
    fun buys(ctx: Context, id: String): Int { @Suppress("UNUSED_VARIABLE") val v = version; return try { prefs(ctx).getInt("buys_$id", 0) } catch (_: Throwable) { 0 } }
    fun canBuy(ctx: Context, id: String, testing: Boolean): Boolean = testing || buys(ctx, id) < MAX_BUYS
    fun noteBuy(ctx: Context, id: String) { try { prefs(ctx).edit().putInt("buys_$id", buys(ctx, id) + 1).apply() } catch (_: Throwable) {}; version += 1 }

    fun daysInstalled(ctx: Context): Int = BombDepot.daysInstalled(ctx)
    fun daysLeft(ctx: Context): Int = (MIN_DAYS - daysInstalled(ctx)).coerceAtLeast(0)
    fun unlocked(ctx: Context): Boolean = daysLeft(ctx) == 0

    /** Hands out what a product id stands for (after a real purchase, or in testing). */
    fun deliver(ctx: Context, id: String) { PACKS.firstOrNull { it.id == id }?.let { addReserve(ctx, it.shots); noteBuy(ctx, id) } }
}
