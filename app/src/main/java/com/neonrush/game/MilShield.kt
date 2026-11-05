package com.neonrush.game

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * MILITARY GRADE SHIELD (Stage 169, reworked in 173).
 *  - Takes [MAX_HP] (10) hits. It sits in front of everything else.
 *  - LAST STAND: when the 10th hit lands it does not vanish. It keeps absorbing EVERY hit for [LAST_STAND_MS] (4 s), then disappears.
 *  - Found on the track as pickup "PU13" about 40% scarcer than the normal shield. A pickup switches it on at once.
 *  - AUTO-DEPLOY: when a hit arrives and no military shield is on, the next one from stock switches on by itself and takes the hit.
 *    The first [AUTO_BASE] (3) stock shields of a run are automatic; after that the player taps MIL. AUTO 6 raises the limit to [AUTO_PLUS] (6):
 *    included with Pro, or a one-time purchase ([AUTO6_ID], $1.99).
 *  - Store: [PACK] shields for [PACK_GEMS] gems. Stock is saved in SharedPreferences "neonrush_milshield".
 */
object MilShield {
    const val MAX_HP = 10
    const val LAST_STAND_MS = 4000L
    const val PACK = 5
    const val PACK_GEMS = 350
    const val AUTO_BASE = 3
    const val AUTO_PLUS = 6
    const val AUTO6_ID = "neonrush_auto6"
    const val AUTO6_PRICE = "$1.99"

    @Volatile var hp = 0
    @Volatile var onMs = -99999L
    @Volatile var hitMs = -99999L
    @Volatile var lastStandUntil = -99999L
    /** Stock shields switched on this run (automatic or by tapping MIL). */
    @Volatile var autoUsed = 0
    /** Bumped whenever stock changes so Compose cards refresh. */
    var version by mutableStateOf(0)

    private fun now(): Long = System.nanoTime() / 1_000_000L
    fun nowMs(): Long = now()
    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences("neonrush_milshield", Context.MODE_PRIVATE)

    fun stock(ctx: Context): Int {
        @Suppress("UNUSED_VARIABLE") val v = version
        return try { prefs(ctx).getInt("stock", 0) } catch (_: Throwable) { 0 }
    }
    fun addStock(ctx: Context, n: Int) {
        try { prefs(ctx).edit().putInt("stock", (stock(ctx) + n).coerceAtLeast(0)).apply() } catch (_: Throwable) {}
        version++
    }

    // ---- AUTO 6 ----
    fun auto6Unlocked(ctx: Context): Boolean {
        if (TESTING_DISABLE_PRO_GATE || MagazineBay.proNow) return true
        val saved = try { prefs(ctx).getBoolean("auto6", false) } catch (_: Throwable) { false }
        val owned = try { AUTO6_ID in RevenueCatManager.ownedProducts.value } catch (_: Throwable) { false }
        return saved || owned
    }
    /** Player switch (only works when unlocked). Default on. */
    fun auto6On(ctx: Context): Boolean { @Suppress("UNUSED_VARIABLE") val v = version; return auto6Unlocked(ctx) && (try { prefs(ctx).getBoolean("auto6on", true) } catch (_: Throwable) { true }) }
    fun setAuto6On(ctx: Context, on: Boolean) { try { prefs(ctx).edit().putBoolean("auto6on", on).apply() } catch (_: Throwable) {}; version++ }
    fun applyAuto6(ctx: Context) { try { prefs(ctx).edit().putBoolean("auto6", true).apply() } catch (_: Throwable) {}; version++ }
    fun autoLimit(ctx: Context): Int = if (auto6On(ctx)) AUTO_PLUS else AUTO_BASE
    fun autoLeft(ctx: Context): Int = (autoLimit(ctx) - autoUsed).coerceAtLeast(0)

    /** Use one shield from stock and switch it on (the MIL button). False if there is none or one is already on. */
    fun activateFromStock(ctx: Context): Boolean {
        if (active() || stock(ctx) <= 0) return false
        addStock(ctx, -1)
        autoUsed++
        activate()
        return true
    }
    /** Called when a hit arrives and no shield is on: switches the next stock shield on by itself (within the auto limit). */
    fun autoDeploy(ctx: Context): Boolean {
        if (active() || autoUsed >= autoLimit(ctx) || stock(ctx) <= 0) return false
        return activateFromStock(ctx)
    }

    fun reset() { hp = 0; hitMs = -99999L; lastStandUntil = -99999L; autoUsed = 0 }
    /** Switch on a fresh shield (pickup or stock). */
    fun activate() { hp = MAX_HP; onMs = now(); hitMs = -99999L; lastStandUntil = -99999L }

    /** One hit lands. True = the military shield took it. */
    fun absorb(): Boolean {
        val t = now()
        if (hp > 0) {
            hp -= 1; hitMs = t
            if (hp == 0) lastStandUntil = t + LAST_STAND_MS
            return true
        }
        if (t < lastStandUntil) { hitMs = t; return true }   // last stand: takes everything, loses nothing
        return false
    }

    fun inLastStand(): Boolean = hp <= 0 && now() < lastStandUntil
    fun lastStandLeftMs(): Long = if (inLastStand()) lastStandUntil - now() else 0L
    /** 100 = new, 0 = gone (or in the last stand). */
    fun healthPct(): Int = (hp.coerceIn(0, MAX_HP) * 100) / MAX_HP
    fun damagePct(): Int = 100 - healthPct()
    fun active(): Boolean = hp > 0 || now() < lastStandUntil
}
