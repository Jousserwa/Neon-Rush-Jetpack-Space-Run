package com.neonrush.game

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.neonrush.game.db.GameDao
import java.util.Calendar
import java.util.TimeZone

/**
 * PRO DAILY GEMS: every Pro subscriber can collect [GEMS] permanent gems once per calendar day (tap the card on Home).
 * Not stacking: a day that is not collected is gone. The gems never expire (unlike the welcome bonus), so Pro always feels generous.
 * [version] changes after a claim so the Home card redraws.
 */
object ProDaily {
    const val GEMS = 75
    var version by mutableStateOf(0)

    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences("neonrush_prodaily", Context.MODE_PRIVATE)
    private fun dayNum(): Int {
        val c = Calendar.getInstance(TimeZone.getDefault())
        return c.get(Calendar.YEAR) * 366 + c.get(Calendar.DAY_OF_YEAR)
    }

    fun canClaim(ctx: Context): Boolean = prefs(ctx).getInt("last", -1) != dayNum()
    /** Total gems this player has collected as Pro (shown on the card). */
    fun collected(ctx: Context): Int = prefs(ctx).getInt("total", 0)
    fun daysCollected(ctx: Context): Int = prefs(ctx).getInt("days", 0)

    /** Hours until the next claim is possible (1..24). */
    fun hoursToNext(): Int {
        val c = Calendar.getInstance(TimeZone.getDefault())
        val left = 24 - c.get(Calendar.HOUR_OF_DAY)
        return left.coerceIn(1, 24)
    }

    /** Pays the gems. Returns the amount paid, or 0 when already collected today. */
    suspend fun claim(ctx: Context, dao: GameDao): Int {
        if (!canClaim(ctx)) return 0
        val sp = prefs(ctx)
        sp.edit().putInt("last", dayNum()).putInt("total", sp.getInt("total", 0) + GEMS).putInt("days", sp.getInt("days", 0) + 1).apply()
        dao.updateProfile { it.copy(gems = it.gems + GEMS, totalGemsEarned = it.totalGemsEarned + GEMS) }
        version += 1
        return GEMS
    }
}
