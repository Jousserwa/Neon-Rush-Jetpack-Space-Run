package com.neonrush.game

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.neonrush.game.db.GameDao
import com.neonrush.game.db.GameProfile
import java.util.Calendar
import java.util.TimeZone

/** What the player is told when the starter wallet is filled: which day (1..3), how many gems were added, the wallet after. */
class StarterTopUp(val day: Int, val added: Int, val wallet: Int)

/** The dialog in the ui package reads this. */
object StarterWalletHost { var pending by mutableStateOf<StarterTopUp?>(null) }

/**
 * NEW-PLAYER SAFETY NET (first 3 days of play, never stacking):
 *  - STARTER WALLET: on each of the player's first 3 play days (within the first 7 calendar days) the permanent gem balance is topped
 *    UP TO [FLOOR] (not by [FLOOR]). Used nothing: nothing added. Used some: the difference. Used all: the full amount again.
 *    After the 3rd claimed day it stops for good and the wallet is whatever the player has.
 *  - ROOKIE RUNS: the first runs are gentler on fuel (see [rookie] and [refuelCost]).
 * Only for installs that had zero runs when this first ran: existing players are never touched.
 */
object StarterWallet {
    const val FLOOR = 500
    const val DAYS = 3
    const val WINDOW_DAYS = 7
    const val ROOKIE_RUNS = 5          // slower fuel drain, cheaper impacts
    private const val FREE_REFUEL_RUNS = 3     // runs 1-3: the first two refuels cost nothing
    private const val HALF_PRICE_RUNS = 10     // runs 4-10: refuels half price

    fun rookie(totalRuns: Int) = totalRuns < ROOKIE_RUNS

    fun refuelCost(totalRuns: Int, refillCount: Int, base: Int): Int = when {
        totalRuns < FREE_REFUEL_RUNS -> if (refillCount < 2) 0 else base / 2
        totalRuns < HALF_PRICE_RUNS -> base / 2
        else -> base
    }

    private fun dayNum(): Int {
        val c = Calendar.getInstance(TimeZone.getDefault())
        return (c.get(Calendar.YEAR) * 366 + c.get(Calendar.DAY_OF_YEAR))
    }

    /** Call once per app start. Safe to call repeatedly: it only acts once per calendar day. */
    suspend fun check(ctx: Context, dao: GameDao) {
        try {
            val sp = ctx.applicationContext.getSharedPreferences("neonrush_starter", Context.MODE_PRIVATE)
            val today = dayNum()
            var mode = sp.getInt("mode", 0)               // 0 = not decided, 1 = new player, 2 = existing player
            val prof = dao.getProfileDirect() ?: GameProfile()
            if (mode == 0) {
                mode = if (prof.totalRuns == 0) 1 else 2
                sp.edit().putInt("mode", mode).putInt("start", today).apply()
            }
            if (mode != 1) return
            val start = sp.getInt("start", today); val claimed = sp.getInt("claimed", 0); val last = sp.getInt("last", -1)
            if (claimed >= DAYS || today - start >= WINDOW_DAYS || last == today) return
            var added = 0
            val after = dao.updateProfile { p ->
                val permanent = (p.gems - p.bonusGems).coerceAtLeast(0)
                added = (FLOOR - permanent).coerceAtLeast(0)
                p.copy(gems = p.gems + added)
            }
            sp.edit().putInt("claimed", claimed + 1).putInt("last", today).apply()
            if (claimed == 0 || added > 0) StarterWalletHost.pending = StarterTopUp(claimed + 1, added, after.gems)
        } catch (_: Throwable) { }
    }
}
