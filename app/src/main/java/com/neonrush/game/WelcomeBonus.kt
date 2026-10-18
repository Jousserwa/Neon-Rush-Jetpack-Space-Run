package com.neonrush.game

import com.neonrush.game.db.GameProfile

/**
 * DAILY WELCOME BONUS: paid after the FIRST game over of each calendar day.
 * Day 1 = 50 gems, +10 every consecutive day, day 10 = 150 (special). Missing a whole day sends you back to day 1.
 * The bonus gems EXPIRE 24 hours after they are paid, used or not: whatever is left of them is taken back (see expire()).
 * After day 10 the cycle starts again at 50. (The streak itself, GameProfile.currentStreak, keeps counting without limit.)
 */
object WelcomeBonus {
    val GEMS = intArrayOf(50, 60, 70, 80, 90, 100, 110, 120, 130, 150)
    const val DAYS = 10

    fun dayOf(streak: Int): Int = ((streak.coerceAtLeast(1) - 1) % DAYS) + 1
    fun gemsForDay(day: Int): Int = GEMS[(day - 1).coerceIn(0, DAYS - 1)]

    fun line(day: Int, gems: Int): String {
        val next = if (day >= DAYS) "back to ${GEMS[0]} tomorrow" else "tomorrow +${GEMS[day]}"
        val head = if (day >= DAYS) "🌟 DAY 10 SPECIAL BONUS  +$gems 💎  ($next)"
        else "🎁 DAILY BONUS  DAY $day/$DAYS  +$gems 💎  ($next)"
        return head + "\n\u23F3 These bonus gems expire in 24 hours, spend them!"
    }

    const val LIFETIME_MS = 24L * 60L * 60L * 1000L

    /** The bonus gems live inside GameProfile.gems; this takes back what is left of them once 24 hours have passed. */
    fun expire(p: GameProfile, now: Long): GameProfile {
        if (p.bonusGems <= 0) return if (p.bonusGemsExpiry != 0L) p.copy(bonusGemsExpiry = 0L) else p
        if (now < p.bonusGemsExpiry) return p
        val take = minOf(p.bonusGems, p.gems).coerceAtLeast(0)
        return p.copy(gems = p.gems - take, bonusGems = 0, bonusGemsExpiry = 0L)
    }

    fun hoursLeft(p: GameProfile, now: Long): Int =
        if (p.bonusGems <= 0 || now >= p.bonusGemsExpiry) 0 else (((p.bonusGemsExpiry - now) + 3_599_999L) / 3_600_000L).toInt()
}
