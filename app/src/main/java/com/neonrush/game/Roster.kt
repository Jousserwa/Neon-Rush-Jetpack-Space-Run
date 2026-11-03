package com.neonrush.game

import android.content.Context
import kotlin.random.Random

/**
 * THE ROSTER (Stage 155, reworked in Stage 168). Ten escort species. Each has its own colour and hull, and a quirk that changes how it fights:
 *   tough = hull toughness (damage taken is divided by it) · speed = how fast it chases its slot · per/duty = shield rhythm (it is shielded `duty` of every `per` ticks)
 *   fire = fire-interval multiplier (lower = shoots more often).
 * DRIP (Stage 168): every boss tier adds exactly ONE new species, in the order WASP, BULWARK, SPECTER, MANTIS, TALON, HORNET, GOLIATH, COMET, PRISM, KRAKEN.
 *   You start with 2 (EASY / MEDIUM), 3 (HARD) or 4 (LEGENDARY), and the NEWEST species is always in the fight (the "headliner"), the others are drawn from those already met.
 * The first time a species is met it is logged in the codex (saved) and the banner says so. No snakes, serpents or wyrms in the roster.
 * MASTERY (Stage 168): every escort you destroy counts for its species (saved): SCOUT 0+, VETERAN 8+, ACE 25+ kills. Each rank pays a small one-time gem reward (1 / 2 / 3)
 *   that the player CLAIMS on the Codex page, plus a daily hunt (1 gem a day) and three milestones (2 / 3 / 3). Gems stay scarce: nothing here pays more than 3 at once.
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
    @Volatile private var appCtx: Context? = null

    /** Name of the picture in res/drawable for a species (esc_0_wasp ... esc_9_kraken). */
    fun artName(id: Int): String = "esc_${id}_${of(id).name.lowercase()}"

    /** How many species this boss zone has unlocked: 2 at the start (3 on HARD, 4 on LEGENDARY), +1 per 15-zone tier, all ten at the end. */
    fun unlocked(zone: Int): Int {
        val bonus = when (Hazards.diffIdx()) { 2 -> 1; 3 -> 2; else -> 0 }
        return (2 + bonus + Fleet.tierOf(zone)).coerceIn(2, ALL.size)
    }

    /** [count] distinct species for this fight. Seeded by zone and round so a retry meets the same crew. The newest unlocked species always takes part. */
    fun pick(zone: Int, count: Int, round: Int): IntArray {
        if (count <= 0) return IntArray(0)
        val rnd = Random(zone * 7919L + round * 104729L + 17L)
        val n = unlocked(zone)
        val pool = (0 until n).toMutableList()
        val out = ArrayList<Int>()
        out.add(n - 1)                                   // the headliner: the newest species
        val fresh = pool.filter { it !in recent && it !in out }.toMutableList()
        while (out.size < count && fresh.isNotEmpty()) out.add(fresh.removeAt(rnd.nextInt(fresh.size)))
        val rest = pool.filter { it !in out }.toMutableList()
        while (out.size < count && rest.isNotEmpty()) out.add(rest.removeAt(rnd.nextInt(rest.size)))
        while (out.size < count) out.add(rnd.nextInt(pool.size))
        while (out.size > count) out.removeAt(out.size - 1)
        for (s in out) { recent.remove(s); recent.add(s) }
        while (recent.size > 6) recent.removeAt(0)
        return out.toIntArray()
    }

    fun of(i: Int): Species = ALL[i.coerceIn(0, ALL.size - 1)]

    // ---- codex (saved) ----
    private fun prefs(ctx: Context): android.content.SharedPreferences { val app = ctx.applicationContext; appCtx = app; return app.getSharedPreferences("neonrush_roster", Context.MODE_PRIVATE) }
    fun seen(ctx: Context, id: Int): Boolean = try { prefs(ctx).getBoolean("sp$id", false) } catch (_: Throwable) { true }
    fun seenCount(ctx: Context): Int = ALL.count { seen(ctx, it.id) }
    /** Logs the species; true when this is the first time it was met. */
    fun markSeen(ctx: Context, id: Int): Boolean {
        if (seen(ctx, id)) return false
        try { prefs(ctx).edit().putBoolean("sp$id", true).apply() } catch (_: Throwable) { }
        return true
    }

    // ---- mastery (Stage 168) ----
    val RANK_NAMES = arrayOf("SCOUT", "VETERAN", "ACE")
    val RANK_KILLS = intArrayOf(0, 8, 25)
    fun kills(ctx: Context, id: Int): Int = try { prefs(ctx).getInt("k$id", 0) } catch (_: Throwable) { 0 }
    fun rankOf(kills: Int): Int = if (kills >= RANK_KILLS[2]) 2 else if (kills >= RANK_KILLS[1]) 1 else 0
    fun rank(ctx: Context, id: Int): Int = rankOf(kills(ctx, id))
    /** Gems for reaching rank [r] (once per species): 1, 2, 3. */
    fun rankReward(r: Int): Int = (r + 1).coerceIn(1, 3)
    fun rankClaimed(ctx: Context, id: Int, r: Int): Boolean = try { prefs(ctx).getBoolean("c${r}_$id", false) } catch (_: Throwable) { true }
    /** The lowest rank reward that is earned but not claimed yet for this species, or -1. */
    fun pendingRank(ctx: Context, id: Int): Int {
        if (!seen(ctx, id)) return -1
        val rk = rank(ctx, id)
        for (r in 0..rk) if (!rankClaimed(ctx, id, r)) return r
        return -1
    }
    /** Marks the reward as claimed and returns the gems to credit (0 when it was not available). */
    fun claimRank(ctx: Context, id: Int, r: Int): Int {
        if (pendingRank(ctx, id) != r) return 0
        try { prefs(ctx).edit().putBoolean("c${r}_$id", true).apply() } catch (_: Throwable) { return 0 }
        return rankReward(r)
    }
    /** Called by Fleet when an escort is destroyed. */
    fun recordKill(id: Int) {
        val c = appCtx ?: return
        try {
            val p = prefs(c); val e = p.edit()
            e.putInt("k$id", p.getInt("k$id", 0) + 1)
            val target = dailyTarget(c)
            if (target == id) { val day = today(); val n = if (p.getLong("dh_day", -1L) == day) p.getInt("dh_n", 0) else 0; e.putLong("dh_day", day).putInt("dh_n", n + 1) }
            e.apply()
        } catch (_: Throwable) { }
    }

    // ---- daily hunt: 3 kills of today's species = 1 gem ----
    const val DAILY_GOAL = 3
    const val DAILY_GEMS = 1
    private fun today(): Long = System.currentTimeMillis() / 86_400_000L
    /** Today's target: one of the species you have met (none until you meet the first). */
    fun dailyTarget(ctx: Context): Int {
        val met = ALL.filter { seen(ctx, it.id) }
        if (met.isEmpty()) return -1
        return met[(today() % met.size).toInt()].id
    }
    fun dailyKills(ctx: Context): Int = try { val p = prefs(ctx); if (p.getLong("dh_day", -1L) == today()) p.getInt("dh_n", 0) else 0 } catch (_: Throwable) { 0 }
    fun dailyClaimed(ctx: Context): Boolean = try { prefs(ctx).getLong("dh_claim", -1L) == today() } catch (_: Throwable) { true }
    fun claimDaily(ctx: Context): Int {
        if (dailyClaimed(ctx) || dailyTarget(ctx) < 0 || dailyKills(ctx) < DAILY_GOAL) return 0
        try { prefs(ctx).edit().putLong("dh_claim", today()).apply() } catch (_: Throwable) { return 0 }
        return DAILY_GEMS
    }

    // ---- milestones: log 5 species (2), log all 10 (3), every species at ACE (3) ----
    val MILESTONES = listOf("m5", "m10", "mapex")
    val MILESTONE_NAMES = listOf("LOG 5 SPECIES", "LOG ALL 10 SPECIES", "APEX HUNTER · ALL ACE")
    val MILESTONE_GEMS = listOf(2, 3, 3)
    fun milestoneDone(ctx: Context, i: Int): Boolean = when (i) {
        0 -> seenCount(ctx) >= 5
        1 -> seenCount(ctx) >= ALL.size
        else -> ALL.all { rank(ctx, it.id) >= 2 && kills(ctx, it.id) >= RANK_KILLS[2] }
    }
    fun milestoneClaimed(ctx: Context, i: Int): Boolean = try { prefs(ctx).getBoolean("ms_${MILESTONES[i]}", false) } catch (_: Throwable) { true }
    fun claimMilestone(ctx: Context, i: Int): Int {
        if (!milestoneDone(ctx, i) || milestoneClaimed(ctx, i)) return 0
        try { prefs(ctx).edit().putBoolean("ms_${MILESTONES[i]}", true).apply() } catch (_: Throwable) { return 0 }
        return MILESTONE_GEMS[i]
    }

    /** The hunter title shown on the Codex page. */
    fun title(ctx: Context): String {
        val s = seenCount(ctx)
        return when {
            milestoneDone(ctx, 2) -> "APEX HUNTER"
            s >= ALL.size -> "FLEET MASTER"
            s >= 6 -> "HUNTER"
            s >= 3 -> "TRACKER"
            else -> "RECRUIT"
        }
    }
}
