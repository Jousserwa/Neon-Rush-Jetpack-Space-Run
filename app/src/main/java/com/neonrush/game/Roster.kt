package com.neonrush.game

import android.content.Context
import kotlin.random.Random

/**
 * THE ROSTER (Stage 155). Ten escort species. Each has its own colour and hull, and a quirk that changes how it fights:
 *   tough = hull toughness (damage taken is divided by it) · speed = how fast it chases its slot · per/duty = shield rhythm (it is shielded `duty` of every `per` ticks)
 *   fire = fire-interval multiplier (lower = shoots more often).
 * A fight draws DISTINCT species (never the same one twice in a fight, and not the ones met in the last few fights), more species unlock as the tier climbs.
 * The first time a species is met it is logged in the codex (saved) and the banner says so. No snakes, serpents or wyrms in the roster.
 */
object Roster {
    class Species(val id: Int, val name: String, val quirk: String, val col: Long, val hull: Int, val tough: Float, val speed: Float, val per: Int, val duty: Int, val fire: Float)
    val ALL = listOf(
        Species(0, "WASP", "FAST AND FRAGILE", 0xFFFFC21A, 2, 0.8f, 1.5f, 22, 5, 1.0f),
        Species(1, "BULWARK", "SLOW · THICK HULL", 0xFF90A4AE, 1, 1.7f, 0.6f, 22, 4, 1.2f),
        Species(2, "SPECTER", "SHIELD FLICKERS OFTEN", 0xFFB388FF, 2, 1.0f, 1.0f, 14, 7, 1.0f),
        Species(3, "MANTIS", "SHOOTS TWICE AS OFTEN", 0xFF69F0AE, 3, 0.9f, 1.0f, 22, 5, 0.55f),
        Species(4, "TALON", "DARTS ABOUT QUICKLY", 0xFFFF7043, 0, 1.0f, 1.8f, 22, 5, 1.0f),
        Species(5, "HORNET", "STINGS AT RANGE", 0xFFFF4DD2, 3, 0.85f, 1.2f, 22, 5, 0.7f),
        Species(6, "GOLIATH", "HUGE · VERY TOUGH", 0xFF8D6E63, 1, 2.2f, 0.45f, 26, 3, 1.4f),
        Species(7, "COMET", "RARELY SHIELDED", 0xFF80D8FF, 0, 1.0f, 1.3f, 30, 3, 1.0f),
        Species(8, "PRISM", "SHIELD MOSTLY UP", 0xFF00E5FF, 4, 1.3f, 0.9f, 12, 6, 1.0f),
        Species(9, "KRAKEN", "TOUGH · FIRES FAST", 0xFFE040FB, 4, 1.6f, 0.8f, 22, 5, 0.6f)
    )
    private val recent = ArrayList<Int>()   // species met in the last few fights, avoided when the pool allows

    /** How many species the tier has unlocked (4 at tier 0, +1 per tier, all ten at tier 6). */
    fun unlocked(zone: Int): Int = (4 + Fleet.tierOf(zone)).coerceIn(4, ALL.size)

    /** [count] distinct species for this fight. Seeded by zone and round so a retry meets the same crew. */
    fun pick(zone: Int, count: Int, round: Int): IntArray {
        if (count <= 0) return IntArray(0)
        val rnd = Random(zone * 7919L + round * 104729L + 17L)
        val pool = (0 until unlocked(zone)).toMutableList()
        val fresh = pool.filter { it !in recent }.toMutableList()
        val out = ArrayList<Int>()
        while (out.size < count && fresh.isNotEmpty()) out.add(fresh.removeAt(rnd.nextInt(fresh.size)))
        val rest = pool.filter { it !in out }.toMutableList()
        while (out.size < count && rest.isNotEmpty()) out.add(rest.removeAt(rnd.nextInt(rest.size)))
        while (out.size < count) out.add(rnd.nextInt(pool.size))   // more bodies than species (cannot happen with 4+ unlocked and max 4 escorts)
        for (s in out) { recent.remove(s); recent.add(s) }
        while (recent.size > 6) recent.removeAt(0)
        return out.toIntArray()
    }

    fun of(i: Int): Species = ALL[i.coerceIn(0, ALL.size - 1)]

    // ---- codex (saved) ----
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("neonrush_roster", Context.MODE_PRIVATE)
    fun seen(ctx: Context, id: Int): Boolean = try { prefs(ctx).getBoolean("sp$id", false) } catch (_: Throwable) { true }
    fun seenCount(ctx: Context): Int = ALL.count { seen(ctx, it.id) }
    /** Logs the species; true when this is the first time it was met. */
    fun markSeen(ctx: Context, id: Int): Boolean {
        if (seen(ctx, id)) return false
        try { prefs(ctx).edit().putBoolean("sp$id", true).apply() } catch (_: Throwable) { }
        return true
    }
}
