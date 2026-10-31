package com.neonrush.game

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Boss surprises (Stage 140), shared between the boss-events overlay and the shooting overlay:
 *  - LUNGE (Legendary): the boss charges across the track toward the pilot. While it lunges its shield is down and bolts do double damage.
 *  - BROOD (Legendary 5 babies, Hard 3): the boss births small creatures that hunt the pilot. A basic bolt kills one baby; an ARMOR round
 *    sets off a chain blast that destroys every baby at once.
 */
object BossLunge {
    /** How far the boss body has moved left of its normal spot (0 .. about -0.5), in fractions of the width. */
    var shift by mutableStateOf(0f)
    @Volatile var vulnerable = false
    fun reset() { shift = 0f; vulnerable = false }
}

class Baby(var x: Float, var y: Float, var vx: Float, var vy: Float, var hp: Int = 1, var age: Float = 0f, val seed: Float = 0f)

object BossBrood {
    /** UI-thread only: the overlay's frame loop and the shooting overlay's frame loop both run on the main thread. */
    val babies = ArrayList<Baby>()
    @Volatile var novaRequested = false
    @Volatile var novaX = 0f
    @Volatile var novaY = 0f
    fun reset() { babies.clear(); novaRequested = false }
    fun hasBabies() = babies.isNotEmpty()
}

/**
 * BROOD BOMB: a rare collectible (Legendary and Hard, mostly around boss fights). Stock is kept in the gun's pocket (max 3, saved between runs).
 * Tap BOMB while babies are alive (or on Legendary during a boss) and a shockwave wipes out every baby at once; on Legendary it also breaks the shield and hurts the boss.
 */
object BroodBomb {
    const val BASE = 3
    const val RACK_MAX = 2                       // Bomb Rack: +1 slot per level (3 -> 4 -> 5)
    val RACK_GEMS = intArrayOf(400, 800)         // gem price of level 1 and level 2
    /** Current storage (3, 4 or 5). Cached so drawing code can read it without a context. */
    @Volatile var MAX = BASE
    var version by mutableStateOf(0)
    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences("neonrush_shooting", Context.MODE_PRIVATE)
    fun rack(ctx: Context): Int { @Suppress("UNUSED_VARIABLE") val v = version; val r = try { prefs(ctx).getInt("rack", 0) } catch (_: Throwable) { 0 }.coerceIn(0, RACK_MAX); MAX = BASE + r + NrUpgrades.rackBonus(ctx); return r }
    /** Buys the next Bomb Rack level with gems. Returns an error text or null on success. */
    suspend fun upgradeRack(ctx: Context, dao: com.neonrush.game.db.GameDao): String? {
        val r = rack(ctx); if (r >= RACK_MAX) return "Bomb Rack is maxed"
        val price = RACK_GEMS[r]
        val prof = dao.getProfileDirect() ?: return "Profile not ready"
        if (prof.gems < price) return "Need $price gems"
        dao.updateProfile { it.copy(gems = it.gems - price) }
        try { prefs(ctx).edit().putInt("rack", r + 1).apply() } catch (_: Throwable) { }
        version += 1; rack(ctx); return null
    }
    fun count(ctx: Context): Int { @Suppress("UNUSED_VARIABLE") val v = version; return try { prefs(ctx).getInt("bombs", 0) } catch (_: Throwable) { 0 } }
    /** Adds one bomb; false when the pocket is full. */
    fun add(ctx: Context): Boolean {
        rack(ctx); val n = count(ctx); if (n >= MAX) return false
        try { prefs(ctx).edit().putInt("bombs", n + 1).apply() } catch (_: Throwable) { }
        version += 1; return true
    }
    fun use(ctx: Context): Boolean {
        val n = count(ctx); if (n <= 0) return false
        try { prefs(ctx).edit().putInt("bombs", n - 1).apply() } catch (_: Throwable) { }
        version += 1; return true
    }
}
