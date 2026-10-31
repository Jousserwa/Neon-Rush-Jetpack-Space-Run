package com.neonrush.game

import android.content.Context

/**
 * COMBAT WINGMAN (Stage 159). A second, fully visible pilot in military kit that mirrors your moves and fights beside you for a short sortie.
 *   Unlocked from zone 50. Real money (one-time): $4.99 = 1 sortie per boss round, $9.99 = 2 sorties per boss round (Upgrades.WING, product ids neonrush_wing_1 / neonrush_wing_2;
 *   the $9.99 tier can be bought directly, it replaces the $4.99 one).
 *   Weapons: WINGMAN ARMS Mk I-V (Upgrades.WARMS, neonrush_warms_1..4): stronger cannon damage, a longer sortie and faster fire.
 *   The button appears only in a boss fight under severe attack: hull 60% or less, or the boss lunging / a hunter diving.
 * The wingman auto-targets the nearest escort (then the main boss) and shoots down shots and obstacles in front of it.
 */
object Wingman {
    const val UNLOCK_ZONE = 50
    @Volatile var active = false
    @Volatile var left = 0f
    @Volatile var usedRound = 0
    @Volatile var roundKey = -1
    private val MUL = floatArrayOf(1.0f, 1.25f, 1.5f, 1.8f, 2.2f)
    private val LEN = floatArrayOf(14f, 15f, 16f, 18f, 20f)
    private val GAP = floatArrayOf(0.36f, 0.34f, 0.32f, 0.30f, 0.27f)
    fun sorties(ctx: Context, testing: Boolean): Int = if (testing) 2 else Upgrades.level(ctx, Upgrades.WING)
    fun arms(): Int = Upgrades.wingArms()
    fun damageMul(): Float = MUL[arms().coerceIn(0, 4)]
    fun length(): Float = LEN[arms().coerceIn(0, 4)]
    fun gap(): Float = GAP[arms().coerceIn(0, 4)]
    fun unlocked(zone: Int): Boolean = zone >= UNLOCK_ZONE
    /** New boss round: the sorties are back. */
    fun syncRound(key: Int) { if (key != roundKey) { roundKey = key; usedRound = 0 } }
    fun reset() { active = false; left = 0f; usedRound = 0; roundKey = -1 }
}
