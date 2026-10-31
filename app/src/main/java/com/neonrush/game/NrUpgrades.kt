package com.neonrush.game

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * UPGRADES (Stage 156). Real-money, one-time (non-consumable) weapon and aircraft upgrades, bought in order, one Mk at a time.
 *   GUN    : the pilot's bolts hit the boss and escorts harder (and wider at Mk III+ for obstacles).
 *   BOMB   : Brood Bomb blast damage.
 *   HELI / JET : missile and strike damage of the air-support aircraft (Mk V also adds one extra missile).
 *   LANCE  : the close-range weapon (Stage 157): the closer the boss, the bigger the percentage of its hull.
 *   STORE  : lance cell storage, +1 cell per level (product ids neonrush_lstore_1..3)
 *   WING / WARMS : combat wingman (Wingman.kt)
 *   RACK   : bomb storage, +1 slot per level (on top of the gem Bomb Rack).
 * Play Console: every product below is a ONE-TIME product with exactly this id. A level is the saved one or the highest the store account owns.
 * Fairness: free players can still win everything: the upgrades are a convenience and speed-up, the zones and bosses are tuned for the base kit.
 */
object NrUpgrades {
    const val GUN = 0; const val BOMB = 1; const val HELI = 2; const val JET = 3; const val LANCE = 4; const val RACK = 5; const val STORE = 6; const val WING = 7; const val WARMS = 8
    class Track(val key: String, val name: String, val blurb: String, val steps: Int, val prices: List<String>, val labels: List<String>)
    val TRACKS = listOf(
        Track("gun", "PLASMA GUN", "Bolts hit harder: more damage to bosses and escorts.", 4, listOf("$1.99", "$2.99", "$4.99", "$7.99"), listOf("Mk II", "Mk III", "Mk IV", "Mk V")),
        Track("bomb", "BROOD BOMB", "Bigger blast: more damage to the boss and its escorts.", 4, listOf("$1.99", "$2.99", "$4.99", "$7.99"), listOf("Mk II", "Mk III", "Mk IV", "Mk V")),
        Track("heli", "HELICOPTER", "Hawk Six hits harder. Mk V adds one more missile.", 4, listOf("$1.99", "$2.99", "$4.99", "$7.99"), listOf("Mk II", "Mk III", "Mk IV", "Mk V")),
        Track("jet", "JET", "Viper One hits harder. Mk V adds one more missile.", 4, listOf("$1.99", "$2.99", "$4.99", "$7.99"), listOf("Mk II", "Mk III", "Mk IV", "Mk V")),
        Track("lance", "PULSE LANCE", "Point-blank weapon: a bigger bite of the boss at close range.", 4, listOf("$1.99", "$2.99", "$4.99", "$7.99"), listOf("Mk II", "Mk III", "Mk IV", "Mk V")),
        Track("rack", "BOMB RACK", "+1 bomb slot per level.", 3, listOf("$0.99", "$1.99", "$2.99"), listOf("+1", "+2", "+3")),
        Track("lstore", "LANCE STORAGE", "Carry more pulse lance cells (+1 per level, up to 4).", 3, listOf("$0.99", "$1.99", "$2.99"), listOf("+1", "+2", "+3")),
        Track("wing", "COMBAT WINGMAN", "A second armed pilot fights beside you. Unlocks at zone 50. Mk 1 = 1 sortie per boss round, Mk 2 = 2 sorties.", 2, listOf("$4.99", "$9.99"), listOf("1 SORTIE", "2 SORTIES")),
        Track("warms", "WINGMAN ARMS", "Stronger cannons, longer sorties, faster fire for the wingman.", 4, listOf("$1.99", "$2.99", "$4.99", "$7.99"), listOf("Mk II", "Mk III", "Mk IV", "Mk V"))
    )
    fun productId(track: Int, step: Int) = "neonrush_${TRACKS[track].key}_$step"   // step 1..n, e.g. neonrush_gun_1 (= Mk II)

    private val GUN_MUL = floatArrayOf(1f, 1.12f, 1.25f, 1.4f, 1.6f)
    private val BOMB_MUL = floatArrayOf(1f, 1.2f, 1.4f, 1.7f, 2.0f)
    private val AIR_MUL = floatArrayOf(1f, 1.15f, 1.3f, 1.5f, 1.75f)

    var version by mutableStateOf(0)
    /** Cached levels so the sim and drawing code can read them without a context. Refreshed by [refresh] and every purchase. */
    private val cache = IntArray(9)
    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences("neonrush_upgrades", Context.MODE_PRIVATE)

    fun level(ctx: Context, track: Int): Int {
        @Suppress("UNUSED_VARIABLE") val v = version
        val t = TRACKS[track]
        val saved = try { prefs(ctx).getInt(t.key, 0) } catch (_: Throwable) { 0 }
        val owned = try { RevenueCatManager.ownedProducts.value } catch (_: Throwable) { emptySet() }
        var fromStore = 0
        for (s in 1..t.steps) if (productId(track, s) in owned) fromStore = s
        val l = maxOf(saved, fromStore).coerceIn(0, t.steps)
        cache[track] = l
        return l
    }
    fun refresh(ctx: Context) { for (i in TRACKS.indices) level(ctx, i) }

    /** Null when step [step] (1..n) of [track] can be bought now, otherwise the reason. */
    fun blockReason(ctx: Context, track: Int, step: Int): String? {
        val l = level(ctx, track)
        if (track == WING) return if (step <= l) "Already owned" else null   // the $9.99 tier can be bought directly and replaces the $4.99 one
        return when { step <= l -> "Already owned"; step > l + 1 -> "Buy ${TRACKS[track].labels[l]} first"; else -> null }
    }
    fun apply(ctx: Context, track: Int, step: Int) {
        try { prefs(ctx).edit().putInt(TRACKS[track].key, maxOf(level(ctx, track), step)).apply() } catch (_: Throwable) { }
        version += 1; refresh(ctx)
        if (track == RACK) BroodBomb.rack(ctx)
    }

    // ---- effects (read the cache) ----
    fun gunMul(): Float = GUN_MUL[cache[GUN].coerceIn(0, 4)]
    fun bombMul(): Float = BOMB_MUL[cache[BOMB].coerceIn(0, 4)]
    fun airMul(jet: Boolean): Float = AIR_MUL[cache[if (jet) JET else HELI].coerceIn(0, 4)]
    fun airExtraMissile(jet: Boolean): Int = if (cache[if (jet) JET else HELI] >= 4) 1 else 0
    fun wingArms(): Int = cache[WARMS].coerceIn(0, 4)
    fun lanceLevel(): Int = cache[LANCE].coerceIn(0, 4)
    fun rackBonus(ctx: Context): Int = level(ctx, RACK)
    /** Obstacle bolt width: Mk III and up widen the hit band a little. */
    fun boltBand(): Float = 1f + 0.12f * (cache[GUN] - 1).coerceIn(0, 3)
}
