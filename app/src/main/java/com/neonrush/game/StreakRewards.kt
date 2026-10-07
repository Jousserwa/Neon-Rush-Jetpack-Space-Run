package com.neonrush.game

data class StreakReward(
    val day: Int,
    val gems: Int,
    val label: String,
    // Extra prize line (hull shards / exclusive suit), shown under the gem amount.
    val bonusText: String? = null
)

object StreakRewards {
    val ALL: List<StreakReward> = listOf(
        StreakReward(1, 2, "Day 1"),
        StreakReward(2, 4, "Day 2"),
        StreakReward(3, 6, "Day 3"),
        StreakReward(4, 8, "Day 4"),
        StreakReward(5, 10, "Day 5"),
        StreakReward(6, 12, "Day 6"),
        StreakReward(7, 14, "Day 7 — Full Streak!")
    )

    fun rewardForDay(day: Int): StreakReward {
        // Cycles back to day 1 after day 7 (day 8 = day 1's reward, day 9 = day 2's, etc.)
        val cyclePosition = ((day - 1) % 7) + 1
        return ALL.first { it.day == cyclePosition }
    }
}
