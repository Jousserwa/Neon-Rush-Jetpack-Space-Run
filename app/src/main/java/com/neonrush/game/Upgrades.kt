package com.neonrush.game

/**
 * Permanent gem-bought upgrades.
 *
 * Design rules:
 *  - Gems stay scarce: prices climb steeply every level (see COST_CURVE), effects are deliberately small.
 *  - Nothing here touches fuel size/consumption (that is a real-money purchase)
 *    and nothing changes distance travelled per tick (which would be fuel
 *    efficiency in disguise).
 *  - Free pilots can reach level FREE_MAX_LEVEL; Pro pilots reach MAX_LEVEL.
 *
 * Levels are stored in GameProfile.upgradesCsv as "shield:2,gem:1".
 */
object Upgrades {
    const val MAX_LEVEL = 5
    const val FREE_MAX_LEVEL = 3

    data class Def(
        val id: String,
        val name: String,
        val emoji: String,
        val desc: String,
        val baseCost: Int
    )

    val ALL = listOf(
        Def("shield", "Shield Core", "🛡️", "Your shield power-up lasts longer. Higher levels animate the barrier.", 120),
        Def("magnet", "Magnet Field", "🧲", "Your magnet pulls gems and power-ups in from farther away.", 120),
        Def("slowtime", "Time Warp", "⏳", "Your slow-time power-up lasts longer.", 120),
        Def("phase", "Phase Core", "👻", "Ghost mode and invincibility last longer.", 150),
        Def("afterburner", "Afterburner", "🔥", "Score builds faster. Your thruster trail grows with each level.", 200),
        Def("bullet", "Bullet Damper", "🎯", "Bosses fire bullets less often.", 250),
        Def("overload", "Boss Overload", "💥", "Boss fights end sooner (bosses attack for less time).", 250),
        Def("gem", "Gem Boost", "💎", "A small bonus to gems you collect during a run.", 300)
    )

    fun parse(csv: String): Map<String, Int> =
        csv.split(",").mapNotNull {
            val p = it.split(":")
            if (p.size == 2) p[1].toIntOrNull()?.let { lvl -> p[0] to lvl } else null
        }.toMap()

    fun level(csv: String, id: String): Int =
        (parse(csv)[id] ?: 0).coerceIn(0, MAX_LEVEL)

    fun setLevel(csv: String, id: String, level: Int): String {
        val m = parse(csv).toMutableMap()
        m[id] = level.coerceIn(0, MAX_LEVEL)
        return m.entries.joinToString(",") { "${it.key}:${it.value}" }
    }

    fun maxLevelFor(isPro: Boolean) = if (isPro) MAX_LEVEL else FREE_MAX_LEVEL

    /**
     * Price multipliers for going from level 0->1, 1->2, ... 4->5.
     * Roughly 3x the original doubling curve (1, 2, 4, 8, 16), but gentler on the
     * first two levels (2x and 2.5x) so a new player can still afford a first
     * upgrade within a few runs. Tune the whole economy by editing this one array.
     */
    private val COST_CURVE = intArrayOf(2, 5, 12, 24, 48)

    /** Price to go from [level] to level+1: baseCost x COST_CURVE[level]. */
    fun nextCost(def: Def, level: Int): Int = def.baseCost * COST_CURVE[level.coerceIn(0, MAX_LEVEL - 1)]

    // ---- Effects (all small on purpose) ----------------------------------

    /** Power-up duration multiplier for a picked-up power-up id ("PU1".."PU12"). */
    fun powerupDurationMultiplier(csv: String, puId: String): Float = when (puId) {
        "PU1" -> 1f + 0.10f * level(csv, "shield")
        "PU3" -> 1f + 0.10f * level(csv, "slowtime")
        "PU4", "PU7" -> 1f + 0.08f * level(csv, "phase")
        else -> 1f
    }

    /**
     * Magnet pull radius. The magnet pulls items that are between 0.15 and 0.65 of
     * the track width ahead of the pilot; each level pushes the far edge out by 0.05
     * (level 5 = 0.90, almost the whole screen). Fuel cells are NOT affected, so this
     * never adds extra fuel.
     */
    fun magnetReachBonus(csv: String): Float = 0.05f * level(csv, "magnet")

    /** +3% score per level (max +15%). Does not change speed, distance or fuel use. */
    fun scoreMultiplier(csv: String): Float = 1f + 0.03f * level(csv, "afterburner")

    /** +2% collected-gem bonus per level (max +10%). Boss/milestone gems are not boosted. */
    fun gemMultiplier(csv: String): Float = 1f + 0.02f * level(csv, "gem")

    /** Ticks between boss bullets: base + 1 per level (so 9 -> up to 14). */
    fun bossBulletInterval(csv: String, base: Int): Int = base + level(csv, "bullet")

    /** Boss health drains faster => shorter fight: +6% per level (max +30%). */
    fun bossDrainMultiplier(csv: String): Float = 1f + 0.06f * level(csv, "overload")

    /** Short text describing the effect at a given level, for the shop UI. */
    fun effectText(id: String, level: Int): String = when (id) {
        "shield" -> "+${10 * level}% shield time"
        "magnet" -> "+${10 * level}% pull radius"
        "slowtime" -> "+${10 * level}% slow-time"
        "phase" -> "+${8 * level}% ghost / invincible time"
        "afterburner" -> "+${3 * level}% score"
        "bullet" -> "boss shoots ${level} tick${if (level == 1) "" else "s"} slower"
        "overload" -> "boss fight -${(100 - 100 / (1f + 0.06f * level)).toInt()}% shorter"
        "gem" -> "+${2 * level}% collected gems"
        else -> ""
    }
}
