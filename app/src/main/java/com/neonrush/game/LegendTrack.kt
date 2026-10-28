package com.neonrush.game

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.neonrush.game.db.GameDao

/**
 * LEGENDARY REWARDS TRACK (Pro only, because Legendary is Pro only). Reaching deeper zones on LEGENDARY opens tiers with gems; the 3rd
 * tier also unlocks the exclusive Legend Ember trail. (Legendary runs already pay 1.6x gems through the difficulty multiplier.)
 * Progress = the best zone reached on a Legendary run. Rewards are collected with a tap; nothing expires.
 */
object LegendTrack {
    class Tier(val zone: Int, val gems: Int, val trail: Boolean = false)
    val TIERS = listOf(Tier(10, 20), Tier(25, 30), Tier(50, 50, trail = true), Tier(75, 80), Tier(100, 120), Tier(150, 180), Tier(200, 240), Tier(300, 300))
    const val TRAIL_ID = "legend_ember"

    var version by mutableStateOf(0)
    @Volatile private var bestCache = 0
    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences("neonrush_legendtrack", Context.MODE_PRIVATE)

    fun init(ctx: Context) { try { bestCache = prefs(ctx).getInt("best", 0) } catch (_: Throwable) { } }
    fun best(): Int { @Suppress("UNUSED_VARIABLE") val v = version; return bestCache }
    /** True once a Legendary run has reached the trail tier. Read by the trail catalog. */
    fun trailUnlocked(): Boolean = bestCache >= (TIERS.firstOrNull { it.trail }?.zone ?: Int.MAX_VALUE)
    fun claimed(ctx: Context): Int { @Suppress("UNUSED_VARIABLE") val v = version; return try { prefs(ctx).getInt("claimed", 0) } catch (_: Throwable) { 0 } }
    fun reached(): Int = TIERS.count { best() >= it.zone }
    fun pendingGems(ctx: Context): Int = TIERS.take(reached()).drop(claimed(ctx)).sumOf { it.gems }

    /** Call at the end of every run flown on LEGENDARY. */
    fun record(ctx: Context, zone: Int) {
        if (zone <= bestCache) return
        bestCache = zone
        try { prefs(ctx).edit().putInt("best", zone).apply() } catch (_: Throwable) { }
        version += 1
    }

    /** Pays every reached, uncollected tier. Returns the gems paid. */
    suspend fun claim(ctx: Context, dao: GameDao): Int {
        val gems = pendingGems(ctx)
        if (gems <= 0) return 0
        try { prefs(ctx).edit().putInt("claimed", reached()).apply() } catch (_: Throwable) { }
        dao.updateProfile { it.copy(gems = it.gems + gems, totalGemsEarned = it.totalGemsEarned + gems) }
        version += 1
        return gems
    }
}
