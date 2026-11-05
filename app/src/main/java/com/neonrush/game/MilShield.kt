package com.neonrush.game

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * MILITARY GRADE SHIELD (Stage 169).
 *  - Takes [MAX_HP] (7) hits. It is NOT a layer of the normal shield: it sits in front of everything else.
 *  - PROTECT WINDOW: for [PROTECT_MS] after it is switched on it can never fall below 1 hit left, even if hit 7 times
 *    (so it always shows its damage instead of vanishing at once).
 *  - Found on the track as pickup "PU13" about 40% scarcer than the normal shield (see chooseProceduralPowerupType).
 *    A pickup switches it on at once. Shields kept in stock are switched on with the on-screen MIL button.
 *  - Store: [PACK] shields for [PACK_GEMS] gems. Stock is saved in SharedPreferences "neonrush_milshield".
 */
object MilShield {
    const val MAX_HP = 7
    const val PROTECT_MS = 2000L
    const val PACK = 5
    const val PACK_GEMS = 350

    @Volatile var hp = 0
    @Volatile var onMs = -99999L
    @Volatile var hitMs = -99999L
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
    /** Use one shield from stock and switch it on. False if there is none or one is already on. */
    fun activateFromStock(ctx: Context): Boolean {
        if (hp > 0 || stock(ctx) <= 0) return false
        addStock(ctx, -1)
        activate()
        return true
    }

    fun reset() { hp = 0; hitMs = -99999L }
    /** Switch on a fresh shield (pickup or stock). */
    fun activate() { hp = MAX_HP; onMs = now(); hitMs = -99999L }

    /** One hit lands. True = the military shield took it. */
    fun absorb(): Boolean {
        if (hp <= 0) return false
        hp -= 1
        if (hp < 1 && now() - onMs < PROTECT_MS) hp = 1   // protect window: never gone this early
        hitMs = now()
        return true
    }

    /** 100 = new, 0 = gone. */
    fun healthPct(): Int = (hp.coerceIn(0, MAX_HP) * 100) / MAX_HP
    fun damagePct(): Int = 100 - healthPct()
    fun active(): Boolean = hp > 0
}
