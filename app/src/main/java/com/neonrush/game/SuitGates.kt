package com.neonrush.game

/**
 * The four story suits are paid ($2.99 / $3.99 / $2.99 / $3.99) but you have to EARN THE RIGHT TO BUY them.
 * Beating the condition fires a "SUIT UNLOCKED FOR PURCHASE" moment; until then the BUY button just tells the player
 * what is still missing. Once earned the right is kept forever (stored in the hangar key/value table).
 *
 * Evaluated at the end-of-world story beat (see NeonRushViewModel.checkWorldEndingBeat).
 */
data class SuitGate(
    val skinId: String,
    val worldId: Int,
    val minTier: NeonRushViewModel.DifficultyTier?,   // null = any difficulty
    val noRevive: Boolean,                            // true = the run must reach the end of the world without a revive
    val requirement: String,                          // shown on the locked card / in the message
    val teaser: String                                // shown when the right to buy is earned
)

object SuitGates {
    val ALL: List<SuitGate> = listOf(
        SuitGate("blackout_runner", 1, null, false,
            "Clear Blackout Front (reach the end of World 1).",
            "You survived the Blackout Front. The Blackout Runner plating is now available to you."),
        SuitGate("signal_ghost", 2, null, true,
            "Clear Derelict Signal WITHOUT using a revive.",
            "Flawless through the Derelict Signal. Something wants to wear you: Signal Ghost is now available."),
        SuitGate("convict_grey", 3, NeonRushViewModel.DifficultyTier.MEDIUM, false,
            "Clear Cell Block Zero on MEDIUM difficulty or higher.",
            "Cell Block Zero is behind you. The Convict Grey fatigues are now available."),
        SuitGate("apex_predator", 4, NeonRushViewModel.DifficultyTier.HARD, false,
            "Clear Green Hell on HARD or LEGENDARY difficulty.",
            "You outran the thing hunting you, on the hard setting. Apex Predator is now available.")
    )

    fun forSkin(skinId: String): SuitGate? = ALL.firstOrNull { it.skinId == skinId }
    fun forWorld(worldId: Int): SuitGate? = ALL.firstOrNull { it.worldId == worldId }
    fun key(skinId: String) = "suit_gate_$skinId"

    fun met(g: SuitGate, tier: NeonRushViewModel.DifficultyTier, revivesUsed: Int): Boolean =
        (g.minTier == null || tier.ordinal >= g.minTier.ordinal) && (!g.noRevive || revivesUsed == 0)

    /** Message for a BUY press while the condition is still open. */
    fun lockedMessage(skinId: String): String {
        val g = forSkin(skinId) ?: return "This suit is not available yet."
        return "🔒 Not available yet. To unlock the right to buy it: ${g.requirement}"
    }
}

/**
 * Pilot-suit Free Ride (default OFF). OFF = the equipped suit is the suit, everywhere, always.
 * ON = during a run the suit rotates every few zones among the suits the player OWNS; menus/share card still show the equipped one.
 */
object SuitRotation {
    const val ZONES_PER_SUIT = 5
    const val K_SUIT_FREE_RIDE = "suit_free_ride"

    fun pick(unlockedCsv: String, equipped: String, zone: Int, freeRide: Boolean): String {
        if (!freeRide) return equipped
        val have = unlockedCsv.split(",").filter { it.isNotBlank() }.toSet()
        val pool = Skins.ALL.map { it.id }.filter { it in have }
        if (pool.size < 2) return equipped
        return pool[((zone.coerceAtLeast(1) - 1) / ZONES_PER_SUIT) % pool.size]
    }
}
