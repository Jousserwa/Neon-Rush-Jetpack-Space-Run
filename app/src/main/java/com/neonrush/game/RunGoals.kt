package com.neonrush.game

import kotlin.random.Random

enum class GoalTier(val emoji: String, val points: Int) {
    BRONZE("🥉", 1), SILVER("🥈", 2), GOLD("🥇", 4)
}

enum class GoalMetric { GEMS, POWERUPS, ZONE, COMBO, BOSSES, SCORE }

data class RunGoal(
    val id: String,
    val tier: GoalTier,
    val metric: GoalMetric,
    val target: Int,
    val text: String,   // shown in HUD / Game Over
    val short: String   // compact version for the Arcade preview
)

/**
 * Per-run goals + permanent Mastery multiplier.
 *
 * Each run gets one Bronze, one Silver and one Gold goal (rotating every run).
 * Finishing goals earns Mastery points (1 / 2 / 4). Every POINTS_PER_LEVEL
 * points = +1% score, capped at +25% (level 25). Points beyond the cap earn
 * cosmetic Mastery Stars. Pro pilots earn points 50% faster.
 *
 * Deliberately NOT included: any goal about fuel (fuel is monetized) and any
 * reward in gems (gems are scarce). The reward only touches score.
 */
object RunGoals {
    const val POINTS_PER_LEVEL = 12
    const val MAX_LEVEL = 25
    const val STAR_POINTS = 30
    private const val CAP_POINTS = MAX_LEVEL * POINTS_PER_LEVEL // 300

    val POOL = listOf(
        // Bronze
        RunGoal("b_gems", GoalTier.BRONZE, GoalMetric.GEMS, 15, "Collect 15 gems", "15 gems"),
        RunGoal("b_pu", GoalTier.BRONZE, GoalMetric.POWERUPS, 3, "Grab 3 power-ups", "3 power-ups"),
        RunGoal("b_zone", GoalTier.BRONZE, GoalMetric.ZONE, 4, "Reach Zone 4", "Zone 4"),
        RunGoal("b_score", GoalTier.BRONZE, GoalMetric.SCORE, 1000, "Score 1,000", "1,000 pts"),
        // Silver
        RunGoal("s_combo", GoalTier.SILVER, GoalMetric.COMBO, 40, "Hit a 40 combo", "40 combo"),
        RunGoal("s_pu", GoalTier.SILVER, GoalMetric.POWERUPS, 6, "Grab 6 power-ups", "6 power-ups"),
        RunGoal("s_boss", GoalTier.SILVER, GoalMetric.BOSSES, 1, "Beat a boss", "1 boss"),
        RunGoal("s_gems", GoalTier.SILVER, GoalMetric.GEMS, 35, "Collect 35 gems", "35 gems"),
        RunGoal("s_zone", GoalTier.SILVER, GoalMetric.ZONE, 8, "Reach Zone 8", "Zone 8"),
        RunGoal("s_score", GoalTier.SILVER, GoalMetric.SCORE, 4000, "Score 4,000", "4,000 pts"),
        // Gold
        RunGoal("g_combo", GoalTier.GOLD, GoalMetric.COMBO, 120, "Hit a 120 combo", "120 combo"),
        RunGoal("g_boss", GoalTier.GOLD, GoalMetric.BOSSES, 2, "Beat 2 bosses", "2 bosses"),
        RunGoal("g_zone", GoalTier.GOLD, GoalMetric.ZONE, 15, "Reach Zone 15", "Zone 15"),
        RunGoal("g_pu", GoalTier.GOLD, GoalMetric.POWERUPS, 10, "Grab 10 power-ups", "10 power-ups"),
        RunGoal("g_gems", GoalTier.GOLD, GoalMetric.GEMS, 70, "Collect 70 gems", "70 gems"),
        RunGoal("g_score", GoalTier.GOLD, GoalMetric.SCORE, 10000, "Score 10,000", "10,000 pts")
    )

    /** One goal per tier, different every run (seeded by the lifetime run count). */
    fun forRun(runIndex: Int): List<RunGoal> {
        val r = Random(runIndex * 7919L + 13L)
        return GoalTier.values().map { tier ->
            val options = POOL.filter { it.tier == tier }
            options[r.nextInt(options.size)]
        }
    }

    fun idsCsv(goals: List<RunGoal>) = goals.joinToString(",") { it.id }
    fun parseIds(csv: String): List<RunGoal> =
        csv.split(",").mapNotNull { id -> POOL.find { it.id == id } }

    fun progress(goal: RunGoal, s: SimulationState): Int = when (goal.metric) {
        GoalMetric.GEMS -> s.collectedGemsCount.toInt()
        GoalMetric.POWERUPS -> s.powerupsCollected
        GoalMetric.ZONE -> s.currentZoneNumber
        GoalMetric.COMBO -> s.peakComboStreak
        GoalMetric.BOSSES -> s.bossZonesRewarded.split(",").count { it.isNotBlank() }
        GoalMetric.SCORE -> s.score.toInt()
    }

    fun isComplete(goal: RunGoal, s: SimulationState) = progress(goal, s) >= goal.target

    // ---- Mastery ----
    fun level(points: Int) = (points / POINTS_PER_LEVEL).coerceAtMost(MAX_LEVEL)
    fun multiplier(points: Int) = 1f + 0.01f * level(points)
    fun stars(points: Int) = if (points > CAP_POINTS) (points - CAP_POINTS) / STAR_POINTS else 0
    fun pointsIntoLevel(points: Int) = if (points >= CAP_POINTS) 0 else points % POINTS_PER_LEVEL
}
