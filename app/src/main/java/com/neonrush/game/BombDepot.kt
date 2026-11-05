package com.neonrush.game

import android.content.Context

/**
 * BOMB DEPOT (Stage 169): the real-money side of bombs. It never touches the in-game pocket ([BroodBomb] pickups keep their normal cap).
 *  - Reserve: bombs you bought. The BOMB button uses the pocket first, then the reserve.
 *  - Unlimited storage ($1.99, one-time): lifts the pocket cap so bombs you find are never wasted.
 *  - Unlocked only after the game has been installed for [MIN_DAYS] days.
 * Play Console: two CONSUMABLE products (neonrush_bombpack_100, neonrush_bombpack_400) and one ONE-TIME product (neonrush_bombvault_unlimited).
 */
object BombDepot {
    const val MIN_DAYS = 30
    class Pack(val id: String, val bombs: Int, val price: String)
    val PACKS = listOf(Pack("neonrush_bombpack_100", 100, "$0.99"), Pack("neonrush_bombpack_400", 400, "$2.99"))
    const val UNLIMITED_ID = "neonrush_bombvault_unlimited"
    const val UNLIMITED_PRICE = "$1.99"

    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences("neonrush_bombdepot", Context.MODE_PRIVATE)

    fun reserve(ctx: Context): Int = try { prefs(ctx).getInt("reserve", 0) } catch (_: Throwable) { 0 }
    fun addReserve(ctx: Context, n: Int) {
        try { prefs(ctx).edit().putInt("reserve", (reserve(ctx) + n).coerceAtLeast(0)).apply() } catch (_: Throwable) {}
        BroodBomb.version += 1
    }
    /** Takes one bomb from the reserve. */
    fun takeReserve(ctx: Context): Boolean {
        val n = reserve(ctx); if (n <= 0) return false
        try { prefs(ctx).edit().putInt("reserve", n - 1).apply() } catch (_: Throwable) {}
        return true
    }

    fun unlimited(ctx: Context): Boolean {
        val saved = try { prefs(ctx).getBoolean("unlimited", false) } catch (_: Throwable) { false }
        val owned = try { UNLIMITED_ID in RevenueCatManager.ownedProducts.value } catch (_: Throwable) { false }
        return saved || owned
    }
    fun applyUnlimited(ctx: Context) {
        try { prefs(ctx).edit().putBoolean("unlimited", true).apply() } catch (_: Throwable) {}
        BroodBomb.version += 1
    }

    /** Days the game has been installed (0 if unknown). */
    fun daysInstalled(ctx: Context): Int = try {
        val first = ctx.packageManager.getPackageInfo(ctx.packageName, 0).firstInstallTime
        ((ServerClock.bestNowMs() - first) / 86_400_000L).toInt().coerceAtLeast(0)
    } catch (_: Throwable) { 0 }
    fun daysLeft(ctx: Context): Int = (MIN_DAYS - daysInstalled(ctx)).coerceAtLeast(0)
    fun unlocked(ctx: Context): Boolean = daysLeft(ctx) == 0

    /** Hands out what a product id stands for (after a real purchase, restore, or in testing). */
    fun deliver(ctx: Context, id: String) {
        val pack = PACKS.firstOrNull { it.id == id }
        if (pack != null) addReserve(ctx, pack.bombs) else if (id == UNLIMITED_ID) applyUnlimited(ctx)
    }
}
