package com.neonrush.game

/**
 * BOSS RETURNS (Stage 146). A boss zone lets the boss come back [BASE] times (once per ~40-tick cycle), plus [STEP] more every 40 zones. Every time you destroy it
 * the rest of the zone loses [CUT] returns: 14 -> 12 -> 10 ... -> none. A destroyed boss does not pop back in the same window.
 * A boss that retreats alive comes back STILL WOUNDED (it only repairs [REGEN] of its hull), so every hit you land counts.
 */
object BossReturns {
    const val BASE = 14          // returns in a boss zone ... 
    const val STEP = 2           // ... plus this many more every 40 zones
    const val CUT = 2
    const val REGEN = 0.2f
    @Volatile var zone = -1
    @Volatile var startTick = 0
    @Volatile var kills = 0
    @Volatile var deadUntil = -1
    @Volatile var lastBefore = BASE
    @Volatile var lastAfter = BASE
    @Volatile var cutSeq = 0
    @Volatile var nowTick = 0

    fun reset() { zone = -1; startTick = 0; kills = 0; deadUntil = -1; lastBefore = base(); lastAfter = base(); cutSeq = 0; nowTick = 0 }
    /** Returns a boss zone starts with: 14, +2 every 40 zones. */
    fun base(): Int = BASE + STEP * (maxOf(zone, 0) / 40)
    fun left(): Int = (base() - CUT * kills).coerceAtLeast(0)
    /** Returns still to come in this zone, counting the current cycle. */
    fun remaining(): Int = if (zone < 0) BASE else (left() - (nowTick - startTick) / 40).coerceAtLeast(0)

    /** May the boss show up at this tick? Called every tick of a boss zone by the sim loops. */
    fun allow(z: Int, tick: Int): Boolean {
        nowTick = tick
        if (z != zone) { zone = z; startTick = tick; kills = 0; deadUntil = -1; lastBefore = base(); lastAfter = base() }
        if (tick < deadUntil) return false
        return (tick - startTick) / 40 < left()
    }
    /** The boss was destroyed: the rest of this cycle is quiet and the zone loses [CUT] returns. */
    fun onKill(tick: Int) { lastBefore = left(); kills += 1; lastAfter = left(); cutSeq += 1; deadUntil = (tick / 40 + 1) * 40 }
    /** Health when the boss shows up: a fresh boss after a kill, otherwise the wounded one with a small repair. */
    fun comeBack(prev: Float): Float = if (prev <= 0.001f) 1f else (prev + REGEN).coerceAtMost(1f)
}

/** What the damage overlay reads: the last hit on the boss (size, big = missile) and the last bounce off its shield. */
object BossDamageFx {
    @Volatile var hitSeq = 0
    @Volatile var amt = 0f
    @Volatile var big = false
    @Volatile var blockSeq = 0
    fun hit(a: Float, isBig: Boolean) { amt = a; big = isBig; hitSeq += 1 }
    fun block() { blockSeq += 1 }
}
