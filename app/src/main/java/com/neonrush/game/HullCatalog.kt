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

    /**
     * Built-in drop calendar (works offline, so a future hull can never be bought early just because Firestore
     * could not be reached). Month index k goes live on the 1st of (LAUNCH month + k) at 00:00 UTC; index 0 = live
     * from the start. Firestore config/hull_drops.releaseAt (hullId -> ms) still overrides any single hull.
     * >>> Set these two to the month you actually publish the game. <<<
     */
    const val LAUNCH_YEAR = 2026
    // REMINDER (owner): confirm this month when the testing code is removed and the release AAB is built.
    const val LAUNCH_MONTH = 11          // 1..12

    fun localReleaseMs(monthIndex: Int): Long {
        if (monthIndex <= 0) return 0L
        val c = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
        c.clear()
        c.set(LAUNCH_YEAR, LAUNCH_MONTH - 1 + monthIndex, 1, 0, 0, 0)
        return c.timeInMillis
    }

    /** Release time for every sold hull: remote override first, else the built-in calendar (0 = already live). */
    fun releaseMap(remote: Map<String, Long>): Map<String, Long> =
        SOLD.associate { it.id to (remote[it.id] ?: localReleaseMs(it.releaseMonthIndex)) }

    /** The Apex Trio bundle only goes on sale once all three hulls are on sale. */
    fun bundleReleaseMs(releases: Map<String, Long>): Long =
        BUNDLE_APEX_IDS.maxOf { releases[it] ?: 0L }

    /** When THIS player can buy: Pro at the release time, everyone else after the early-access window. */
    fun buyableAtMs(isPro: Boolean, releaseAtMs: Long): Long =
        if (releaseAtMs <= 0L) 0L else releaseAtMs + (if (isPro) 0L else earlyAccessMillis())

    val ALL: List<PremiumHull> = listOf(
        PremiumHull("cosmic_koi", "Cosmic Koi", "🐟", "Calm. Koi drift around your ship.",
            listOf("Orbiting koi", "Ripple trail", "Drifting petals"), HullTier.STANDARD, 0),
        PremiumHull("aurora_serpent", "Aurora Serpent", "🌌", "A ribbon of living northern lights.",
            listOf("Aurora ribbons", "Drifting stars", "Close-call ripple"), HullTier.STANDARD, 1),
        PremiumHull("crystal_phoenix", "Crystal Phoenix", "🔥", "Crystal wings. Rises again on revive.",
            listOf("Flapping wings", "Feather embers", "Rebirth flash"), HullTier.STANDARD, 2),
        PremiumHull("supernova_heart", "Supernova Heart", "💥", "A star core that beats with your speed.",
            listOf("Pulsing core", "Shockwave rings", "Gold gem burst"), HullTier.RICH, 0),
        PremiumHull("quantum_mirage", "Quantum Mirage", "🔮", "Prism clones phase in and out.",
            listOf("Phasing clones", "Glitch scan line", "Zone hue shift"), HullTier.RICH, 2),
        PremiumHull("stormcaller", "Stormcaller", "⛈️", "Carry the storm. Thunder on near-miss.",
            listOf("Storm cloud", "Lightning forks", "Thunder flash"), HullTier.RICH, 1),
        PremiumHull("inferno_sovereign", "Inferno Sovereign", "👑", "A flame crown. Roars when a boss appears.",
            listOf("Flame crown", "Molten drips", "Boss roar flash"), HullTier.RICH, 3),
        PremiumHull("neon_wyrm", "Neon Wyrm", "🐉", "A segmented dragon that follows your every move.",
            listOf("Segmented body", "Glowing eyes", "Spark breath"), HullTier.FLAGSHIP, 3),
        PremiumHull("event_horizon", "Event Horizon", "🕳️", "A black hole in your wake.",
            listOf("Accretion ring", "Pulled debris", "Zone pulse"), HullTier.FLAGSHIP, 4),
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

    /**
     * FOUNDER DUO: the two launch hulls for one lower price, only during the launch window
     * (launch month + FOUNDER_GRACE_DAYS), and only while the player owns neither hull.
     * Needs its own non-consumable product in Play Console + RevenueCat.
     */
    const val FOUNDER_PRODUCT = "neonrush_bundle_founder"
    const val FOUNDER_FALLBACK_PRICE = "$4.99"
    const val FOUNDER_GRACE_DAYS = 14
    val FOUNDER_IDS = listOf("cosmic_koi", "supernova_heart")
    fun founderEndsMs(): Long = localReleaseMs(1) + FOUNDER_GRACE_DAYS * 24L * 60L * 60L * 1000L
    fun founderOpen(nowMs: Long, owned: Set<String>): Boolean =
        nowMs < founderEndsMs() && FOUNDER_IDS.none { it in owned }

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
    fun canBuyNow(isPro: Boolean, releaseAtMs: Long, serverNowMs: Long): Boolean {
        if (releaseAtMs <= 0L) return true            // launch set / no scheduled drop: everyone can buy now
        return serverNowMs >= releaseAtMs + (if (isPro) 0L else earlyAccessMillis())
    }

    fun millisUntilPublic(releaseAtMs: Long, serverNowMs: Long): Long =
        if (releaseAtMs <= 0L) 0L else (releaseAtMs + earlyAccessMillis() - serverNowMs).coerceAtLeast(0L)
}
