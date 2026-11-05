package com.neonrush.game

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * SCORE VOUCHERS (Stage 169): a big score now buys something.
 *  - A run that ends with 30,000+ points earns 1 voucher, 60,000+ earns 2, 100,000+ earns 3. At most [DAILY_CAP] vouchers a day.
 *  - 1 voucher = 10 bombs  OR  2 airstrike calls  OR  2 military shields.
 */
object ScoreVouchers {
    const val MIN_SCORE = 30_000L
    const val DAILY_CAP = 3
    var version by mutableStateOf(0)

    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences("neonrush_vouchers", Context.MODE_PRIVATE)
    private fun today(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    fun count(ctx: Context): Int { @Suppress("UNUSED_VARIABLE") val v = version; return try { prefs(ctx).getInt("n", 0) } catch (_: Throwable) { 0 } }
    fun forScore(score: Long): Int = when { score >= 100_000L -> 3; score >= 60_000L -> 2; score >= MIN_SCORE -> 1; else -> 0 }
    fun earnedToday(ctx: Context): Int = try { val p = prefs(ctx); if (p.getString("day", "") == today()) p.getInt("today", 0) else 0 } catch (_: Throwable) { 0 }

    /** Call once when a run ends. Returns the vouchers added. */
    fun award(ctx: Context, score: Long): Int {
        val want = forScore(score); if (want <= 0) return 0
        val add = minOf(want, DAILY_CAP - earnedToday(ctx)); if (add <= 0) return 0
        try { val p = prefs(ctx); p.edit().putInt("n", p.getInt("n", 0) + add).putString("day", today()).putInt("today", earnedToday(ctx) + add).apply() } catch (_: Throwable) {}
        version++
        return add
    }

    fun addVouchers(ctx: Context, n: Int) {
        try { val p = prefs(ctx); p.edit().putInt("n", p.getInt("n", 0) + n).apply() } catch (_: Throwable) {}
        version++
    }

    enum class Prize(val label: String) { BOMBS("10 BOMBS"), AIR("2 AIRSTRIKES"), MIL("2 MIL SHIELDS") }

    /** Spends one voucher on a prize. */
    fun exchange(ctx: Context, prize: Prize): Boolean {
        val n = count(ctx); if (n <= 0) return false
        try { prefs(ctx).edit().putInt("n", n - 1).apply() } catch (_: Throwable) { return false }
        when (prize) {
            Prize.BOMBS -> BombDepot.addReserve(ctx, 10)
            Prize.AIR -> AirSupport.addCalls(ctx, 2)
            Prize.MIL -> MilShield.addStock(ctx, 2)
        }
        version++
        return true
    }
}
