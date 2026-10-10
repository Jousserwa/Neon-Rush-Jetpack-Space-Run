package com.neonrush.game

/**
 * Premium ship hulls: real-money only (never gems), one-time non-consumable products.
 * Store product id = "neonrush_hull_<id>". Prices shown in the UI must come from the
 * store (RevenueCat), never from priceTier; priceTier is only a fallback label.
 */
enum class HullTier(val fallbackPrice: String) {
    STANDARD("$2.99"), RICH("$3.99"), FLAGSHIP("$4.99")
}

data class PremiumHull(
    val id: String,
    val name: String,
    val emoji: String,
    val tagline: String,
    val layers: List<String>,      // the 2-3 animation layers, shown as tags on the card
    val tier: HullTier?,           // null = never sold (earned only)
    val releaseMonthIndex: Int,    // 0 = launch set; later numbers = monthly drops
    val earnedOnly: Boolean = false
) {
    val productId: String get() = "neonrush_hull_$id"
}

object HullCatalog {
    /** Pro members get this long head start on each new monthly drop. Single constant on purpose. */
    const val EARLY_ACCESS_HOURS = 96

    val ALL: List<PremiumHull> = listOf(
        PremiumHull("cosmic_koi", "Cosmic Koi", "🐟", "Calm. Koi drift around your ship.",
            listOf("Orbiting koi", "Ripple trail", "Drifting petals"), HullTier.STANDARD, 0),
        PremiumHull("aurora_serpent", "Aurora Serpent", "🌌", "A ribbon of living northern lights.",
            listOf("Aurora ribbons", "Drifting stars", "Close-call ripple"), HullTier.STANDARD, 0),
        PremiumHull("crystal_phoenix", "Crystal Phoenix", "🔥", "Crystal wings. Rises again on revive.",
            listOf("Flapping wings", "Feather embers", "Rebirth flash"), HullTier.STANDARD, 0),
        PremiumHull("supernova_heart", "Supernova Heart", "💥", "A star core that beats with your speed.",
            listOf("Pulsing core", "Shockwave rings", "Gold gem burst"), HullTier.RICH, 0),
        PremiumHull("quantum_mirage", "Quantum Mirage", "🔮", "Prism clones phase in and out.",
            listOf("Phasing clones", "Glitch scan line", "Zone hue shift"), HullTier.RICH, 0),
        PremiumHull("stormcaller", "Stormcaller", "⛈️", "Carry the storm. Thunder on near-miss.",
            listOf("Storm cloud", "Lightning forks", "Thunder flash"), HullTier.RICH, 0),
        PremiumHull("inferno_sovereign", "Inferno Sovereign", "👑", "A flame crown. Roars when a boss appears.",
            listOf("Flame crown", "Molten drips", "Boss roar flash"), HullTier.RICH, 0),
        PremiumHull("neon_wyrm", "Neon Wyrm", "🐉", "A segmented dragon that follows your every move.",
            listOf("Segmented body", "Glowing eyes", "Spark breath"), HullTier.FLAGSHIP, 0),
        PremiumHull("event_horizon", "Event Horizon", "🕳️", "A black hole in your wake.",
            listOf("Accretion ring", "Pulled debris", "Zone pulse"), HullTier.FLAGSHIP, 0),
        // Never sold. Granted to annual Pro ~7 days after the subscription starts (see Stage 4).
        PremiumHull("eternal_crown_2026", "Eternal Crown 2026", "🏆", "Year Edition. Annual Pro only. Never sold.",
            listOf("Rotating halo", "Gold dust", "Crown flare"), null, 0, earnedOnly = true),
        // Never sold. Granted to the top 100 of a ranked season (see HullProgression.RankedSeason).
        PremiumHull("apex_laurel", "Apex Laurel", "🌿", "Ranked Season hull. Top 100 only. Never sold.",
            listOf("Gold laurels", "Comet sparks", "Pulse flash"), null, 0, earnedOnly = true)
    )

    /** 3-hull bundle: Apex Trio. Price $9.99 (vs $13.97 separately). Product id below. */
    const val BUNDLE_APEX_PRODUCT = "neonrush_bundle_apex"
    const val BUNDLE_APEX_FALLBACK_PRICE = "$9.99"
    val BUNDLE_APEX_IDS = listOf("neon_wyrm", "event_horizon", "inferno_sovereign")

    val SOLD: List<PremiumHull> get() = ALL.filter { !it.earnedOnly }
    fun byId(id: String): PremiumHull? = ALL.firstOrNull { it.id == id }
    fun isPremium(id: String) = ALL.any { it.id == id }

    /** Milliseconds from the monthly release until a non-Pro player can buy it. */
    fun earlyAccessMillis() = EARLY_ACCESS_HOURS * 60L * 60L * 1000L

    /**
     * Pro can buy at releaseAtMs; everyone else at releaseAtMs + early access.
     * releaseAtMs must come from a trusted clock (RevenueCat / Firestore server time),
     * not the device clock, or the window can be bypassed by changing the phone date.
     */
    fun canBuyNow(isPro: Boolean, releaseAtMs: Long, serverNowMs: Long): Boolean =
        serverNowMs >= releaseAtMs + (if (isPro) 0L else earlyAccessMillis())

    fun millisUntilPublic(releaseAtMs: Long, serverNowMs: Long): Long =
        (releaseAtMs + earlyAccessMillis() - serverNowMs).coerceAtLeast(0L)
}
