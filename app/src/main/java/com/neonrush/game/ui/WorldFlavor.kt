package com.neonrush.game

/**
 * Gameplay personality for a stretch of track. No assets: it only scales numbers the sim already has.
 *  spacingMul  > 1 = obstacles come denser (spawn spacing is divided by it)
 *  powerupMul  > 1 = more power-ups
 *  bossAggro   > 1 = boss tracks you faster and fires more often
 */
data class ZoneFlavor(val title: String, val spacingMul: Float, val powerupMul: Float, val bossAggro: Float)

object WorldFlavors {
    const val FIRST_FLAVOR_ZONE = 41
    const val BLOCK = 8 // zones per mood, same block size as WorldVariants.RIFT_BLOCK

    // Mood cycle used after zone 40 in every world (main worlds, rifts and Special worlds).
    private val moods = listOf(
        ZoneFlavor("AMBUSH",     1.25f, 0.80f, 1.30f),
        ZoneFlavor("CALM EYE",   0.85f, 1.40f, 0.90f),
        ZoneFlavor("BLITZ",      1.40f, 1.00f, 1.20f),
        ZoneFlavor("SCAVENGER",  1.00f, 1.80f, 1.00f),
        ZoneFlavor("WARDEN",     1.10f, 1.10f, 1.60f),
        ZoneFlavor("SIEGE",      1.30f, 0.70f, 1.50f),
        ZoneFlavor("DRIFT",      0.90f, 1.20f, 0.80f),
        ZoneFlavor("OVERDRIVE",  1.50f, 1.50f, 1.70f)
    )

    // Built-in personality of the six newer Special worlds (ids 27-32), easiest to hardest.
    private val worldBase = mapOf(
        27 to ZoneFlavor("SPORE BLOOM",   1.00f, 1.70f, 1.00f),
        28 to ZoneFlavor("THUNDER LANES", 1.15f, 1.00f, 1.15f),
        29 to ZoneFlavor("FOUNDRY HEAT",  1.25f, 0.90f, 1.30f),
        30 to ZoneFlavor("PRESSURE",      1.10f, 0.80f, 1.45f),
        31 to ZoneFlavor("RADIANT SIEGE", 1.20f, 1.20f, 1.70f),
        32 to ZoneFlavor("NULL STORM",    1.50f, 1.40f, 1.80f)
    )

    fun block(zone: Int): Int = if (zone < FIRST_FLAVOR_ZONE) -1 else (zone - FIRST_FLAVOR_ZONE) / BLOCK

    /** null = the world's normal tuning (zones 1-40 of the original worlds). */
    fun forZone(worldId: Int, family: Int, zone: Int): ZoneFlavor? {
        val base = worldBase[worldId]
        val b = block(zone)
        if (b < 0) return base
        // Consecutive blocks always differ (3 is coprime with 8), and each family walks the cycle differently.
        val mood = moods[(b * 3 + family) % moods.size]
        // The longer you survive, the meaner each mood gets (capped so it never becomes unfair).
        val ramp = 1f + (b * 0.015f).coerceAtMost(0.35f)
        val spacing = mood.spacingMul * (base?.spacingMul ?: 1f) * ramp
        val aggro = mood.bossAggro * (base?.bossAggro ?: 1f) * ramp
        val pu = mood.powerupMul * (base?.powerupMul ?: 1f)
        return ZoneFlavor(mood.title, spacing.coerceAtMost(2.2f), pu.coerceIn(0.5f, 2.5f), aggro.coerceAtMost(2.4f))
    }
}
