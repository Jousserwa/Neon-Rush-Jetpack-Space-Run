package com.neonrush.game

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * MAGAZINE BAY (Stage 144, replaces the Stage 143 idea).
 *  - One armor pickup = [PER_ARMOR] 20 shots. One magazine = 5 armors = [MAG_SIZE] 100 shots.
 *  - What real money buys is STORAGE: how many magazines the gun can carry. The tier you buy REPLACES the base.
 *      Base:  Free 40 shots (2 armors)   Pro 100 shots (1 magazine)
 *      Tier 1 $0.99 = 2 magazines (200)   Tier 2 $2.99 = 5 (500)   Tier 3 $3.99 = 8 (800)   Tier 4 $4.99 = 12 (1200)   Tier 5 $9.99 = 24 (2400)
 *  - Tiers are bought in order, at most ONE tier per day (server-clock calendar day).
 *    Free players can buy tiers 1-2 only. Tiers 3-5 are Pro; tier 4 needs 6+ months of Pro, tier 5 needs 15+ months.
 *  - Tiers are permanent one-time purchases (restored with the store account). If Pro lapses, usable storage is capped at tier 2 until it returns.
 *  - The gun fires from one magazine at a time and swaps to the next by itself.
 *  - Bigger storage also makes armor magazines drop more often (see [dropMul]).
 */
object MagazineBay {
    const val PER_ARMOR = 20
    const val MAG_SIZE = 100
    const val FREE_BASE = 40
    const val PRO_BASE = 100
    const val MAX_TIER = 5
    const val FREE_MAX_TIER = 2

    class Tier(val id: String, val mags: Int, val price: String, val label: String, val proMonths: Int, val proOnly: Boolean)
    // Play Console: five ONE-TIME (non-consumable) products with exactly these ids.
    val TIERS = listOf(
        Tier("neonrush_mag_tier_1", 2, "$0.99", "DOUBLE CLIP", 0, false),
        Tier("neonrush_mag_tier_2", 5, "$2.99", "FIELD RACK", 0, false),
        Tier("neonrush_mag_tier_3", 8, "$3.99", "ARSENAL", 0, true),
        Tier("neonrush_mag_tier_4", 12, "$4.99", "WAR CHEST", 6, true),
        Tier("neonrush_mag_tier_5", 24, "$9.99", "ARMORY VAULT", 15, true)
    )
    /** How much more often armor magazines drop, per tier (1.0 = the base spacing). */
    private val DROP_MUL = floatArrayOf(1.0f, 0.92f, 0.84f, 0.76f, 0.68f, 0.60f)

    var version by mutableStateOf(0)
    /** Set at every run start and by the home screen so capacity follows the Pro state. */
    @Volatile var proNow = false

    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences("neonrush_magbay", Context.MODE_PRIVATE)
    private fun today(): Long = ServerClock.bestNowMs() / 86_400_000L

    /** Tier owned: the saved one, or the highest tier the store account owns (reinstall / new phone). */
    fun tier(ctx: Context): Int {
        @Suppress("UNUSED_VARIABLE") val v = version
        val saved = try { prefs(ctx).getInt("tier", 0) } catch (_: Throwable) { 0 }
        val owned = try { RevenueCatManager.ownedProducts.value } catch (_: Throwable) { emptySet() }
        val fromStore = TIERS.indices.lastOrNull { TIERS[it].id in owned }?.plus(1) ?: 0
        return maxOf(saved, fromStore).coerceIn(0, MAX_TIER)
    }
    /** Tier that actually works right now: if Pro lapsed, tiers above 2 sleep until it returns. */
    fun usableTier(ctx: Context, pro: Boolean = proNow): Int = if (pro) tier(ctx) else tier(ctx).coerceAtMost(FREE_MAX_TIER)

    fun capacity(ctx: Context, pro: Boolean = proNow): Int {
        val t = usableTier(ctx, pro)
        return if (t > 0) TIERS[t - 1].mags * MAG_SIZE else if (pro) PRO_BASE else FREE_BASE
    }
    fun magazines(ctx: Context, pro: Boolean = proNow): Int = (capacity(ctx, pro) / MAG_SIZE).coerceAtLeast(1)
    fun dropMul(ctx: Context): Float = DROP_MUL[usableTier(ctx)]
    /** Drop-spacing multiplier of tier [n] (0..5), for the Bay page. */
    fun dropMulAt(n: Int): Float = DROP_MUL[n.coerceIn(0, MAX_TIER)]

    /** Why tier [n] (1..5) cannot be bought right now, or null when it can. */
    fun blockReason(ctx: Context, n: Int, pro: Boolean, proMonths: Int, testing: Boolean): String? {
        val t = TIERS.getOrNull(n - 1) ?: return "Unknown tier"
        val own = tier(ctx)
        if (n <= own) return "Already owned"
        if (n != own + 1) return "Buy tier ${own + 1} first"
        if (!testing) {
            if (!pro && (t.proOnly || n > FREE_MAX_TIER)) return "Pro only"
            if (pro && proMonths < t.proMonths) return "Needs ${t.proMonths} months of Pro (you have $proMonths)"
            if (boughtToday(ctx)) return "One tier per day. Come back tomorrow"
        }
        return null
    }
    fun boughtToday(ctx: Context): Boolean { @Suppress("UNUSED_VARIABLE") val v = version; return try { prefs(ctx).getLong("day", -1L) == today() } catch (_: Throwable) { false } }

    /** The store confirmed payment for tier [n]. */
    fun applyTier(ctx: Context, n: Int) {
        try { prefs(ctx).edit().putInt("tier", maxOf(tier(ctx), n)).putLong("day", today()).apply() } catch (_: Throwable) { }
        version += 1
    }

    fun onRunStart(pro: Boolean) { proNow = pro }
}
