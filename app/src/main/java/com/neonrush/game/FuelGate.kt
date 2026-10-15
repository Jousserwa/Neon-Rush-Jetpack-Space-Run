package com.neonrush.game

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.TimeZone

/** Streak progress that decides whether Fuel Tank tiers 3 to 5 may be bought. */
data class FuelProgress(
    val playStreak: Int = 0,        // consecutive months with 15+ play days (current month counts once it reaches 15)
    val proStreak: Int = 0,         // consecutive months with an active Pro subscription
    val daysThisMonth: Int = 0
)

/**
 * Tier 3: 3 Pro months OR 4 free months. Tier 4: 6 Pro OR 7 free. Tier 5: 24 months either way.
 * "Free" months need 15+ different play days in the month. All months must be back to back: a missed month resets the count.
 */
class FuelGate(private val store: HangarStore, private val scope: CoroutineScope) {
    companion object {
        const val DAYS_PER_MONTH = 15
        private val FREE_NEED = intArrayOf(0, 0, 4, 7, 24)
        private val PRO_NEED = intArrayOf(0, 0, 3, 6, 24)
        private const val K_DAYS = "fg_play_days"
        private const val K_PRO = "fg_pro_months"
        fun freeNeed(tier: Int) = FREE_NEED[(tier - 1).coerceIn(0, 4)]
        fun proNeed(tier: Int) = PRO_NEED[(tier - 1).coerceIn(0, 4)]
        fun gated(tier: Int) = tier >= 3
    }

    private val _state = MutableStateFlow(FuelProgress())
    val state: StateFlow<FuelProgress> = _state.asStateFlow()
    private var days: List<String> = emptyList()      // yyyyMMdd
    private var proMonths: List<String> = emptyList() // yyyyMM

    suspend fun load() {
        try { days = store.getList(K_DAYS); proMonths = store.getList(K_PRO) } catch (_: Exception) {}
        recompute()
    }

    /** Call on app start, on run start and whenever Pro status changes. Records today and re-evaluates the streaks. */
    suspend fun touch(isPro: Boolean) {
        try {
            val c = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = ServerClock.bestNowMs() }
            val y = c.get(Calendar.YEAR); val m = c.get(Calendar.MONTH) + 1; val d = c.get(Calendar.DAY_OF_MONTH)
            val dayKey = (y * 10000 + m * 100 + d).toString()
            val monKey = (y * 100 + m).toString()
            var changed = false
            if (dayKey !in days) { days = days + dayKey; changed = true }
            if (isPro && monKey !in proMonths) { proMonths = proMonths + monKey; changed = true }
            if (changed) {
                val cutoff = monthIndex(y * 100 + m) - 27
                days = days.filter { monthIndex(it.toInt() / 100) >= cutoff }
                proMonths = proMonths.filter { monthIndex(it.toInt()) >= cutoff }
                store.putList(K_DAYS, days); store.putList(K_PRO, proMonths)
            }
        } catch (_: Exception) {}
        recompute()
    }

    fun touchAsync(isPro: Boolean) { scope.launch { touch(isPro) } }

    private fun monthIndex(yyyymm: Int) = (yyyymm / 100) * 12 + (yyyymm % 100 - 1)

    private fun recompute() {
        val c = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = ServerClock.bestNowMs() }
        val curKey = c.get(Calendar.YEAR) * 100 + c.get(Calendar.MONTH) + 1
        val cur = monthIndex(curKey)
        val perMonth = HashMap<Int, Int>()
        days.forEach { k -> val mi = monthIndex(k.toInt() / 100); perMonth[mi] = (perMonth[mi] ?: 0) + 1 }
        val proSet = proMonths.map { monthIndex(it.toInt()) }.toSet()
        fun streak(ok: (Int) -> Boolean): Int {
            var n = if (ok(cur)) 1 else 0
            var i = cur - 1
            while (n < 60 && ok(i)) { n++; i-- }
            return n
        }
        _state.value = FuelProgress(
            playStreak = streak { (perMonth[it] ?: 0) >= DAYS_PER_MONTH },
            proStreak = streak { it in proSet },
            daysThisMonth = perMonth[cur] ?: 0
        )
    }

    fun canBuy(tier: Int, p: FuelProgress = _state.value): Boolean =
        !gated(tier) || p.proStreak >= proNeed(tier) || p.playStreak >= freeNeed(tier)
}
