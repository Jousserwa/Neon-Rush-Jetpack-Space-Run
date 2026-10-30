package com.neonrush.game

import kotlin.random.Random

/**
 * THE SIEGE (Stage 148). Replaces the old "boss returns" counter. A boss zone is a Siege of up to 4 ROUNDS; every round has 2 PHASES:
 *   Phase A = Pressure (shield rhythm, the round's own attack pattern), Phase B = Desperation (faster, angrier).
 *  - A round only closes when its boss is DESTROYED (in phase A or phase B, it counts the same). Then a short breather, then the next round.
 *  - Each round attacks differently: 1 PROBE (aimed shots) · 2 BARRAGE (fans) · 3 SIEGE (walls with a gap) · 4 WRATH (all of it, mixed).
 *  - The world HOLDS (no distance, no zone change) while a Siege is on, so it can never be cut short by the next zone.
 *  - Plan share: free 1 round, Pro MEDIUM 2, Pro HARD 3, Pro LEGENDARY 4 (see [configure]). Rounds past the plan are shown as a locked teaser.
 *  - Safety net: a boss that outlives its round window (about 40 s) overloads its own core, so nobody can get stuck.
 * Time unit: the sim tick (about 0.2 s).
 */
object Siege {
    const val ROUNDS = 4
    const val LEAD_IN = 24          // ticks before round 1 of a boss zone
    const val BREATHER = 30         // ticks between a kill and the next round (about 6 s)
    const val OVERLOAD_AT = 200     // ticks (about 40 s) after which the boss core overloads
    private val PHASE_A = intArrayOf(55, 65, 80, 95)          // phase A length per round (about 11 / 13 / 16 / 19 s)
    private val SCALE = floatArrayOf(1f, 1.25f, 1.5f, 1.8f)   // each later round takes more damage to bring down
    val NAMES = arrayOf("PROBE", "BARRAGE", "SIEGE", "WRATH")
    val TIPS = arrayOf("AIMED SHOTS · LEARN ITS RHYTHM", "FAN VOLLEYS · SLIP BETWEEN THE BOLTS", "WALLS OF FIRE · FIND THE GAP", "EVERYTHING AT ONCE · NO MERCY")

    const val IDLE = 0; const val WAIT = 1; const val FIGHT = 2; const val BREATH = 3; const val DONE = 4

    @Volatile var state = IDLE
    @Volatile var zone = -1
    @Volatile var round = 1
    @Volatile var roundStart = 0
    @Volatile var nextAt = 0
    @Volatile var nowTick = 0
    @Volatile var maxRounds = 1
    @Volatile var canShoot = false
    @Volatile var special = false            // special worlds keep the old single-boss rules
    @Volatile var locked = false             // the plan ended the Siege early: rounds above [rounds] are a teaser
    @Volatile var overload = false
    @Volatile var kills = 0
    @Volatile var lastKillPhase = 0
    @Volatile var closedRound = 0
    @Volatile var startSeq = 0               // a round began
    @Volatile var phaseSeq = 0               // phase B began
    @Volatile var closeSeq = 0               // a boss was destroyed (round closed)
    @Volatile var clearedSeq = 0             // round 4 closed: Siege cleared
    @Volatile var lockedSeq = 0              // the plan's last round closed with rounds still to go
    private var lastPhase = 0

    fun configure(difficultyName: String, isPro: Boolean) {
        maxRounds = if (!isPro) 1 else when (difficultyName) { "MEDIUM" -> 2; "HARD" -> 3; "LEGENDARY" -> 4; else -> 1 }
        canShoot = difficultyName != "EASY"
    }

    fun reset() {
        state = IDLE; zone = -1; round = 1; roundStart = 0; nextAt = 0; locked = false; overload = false; kills = 0; lastKillPhase = 0; lastPhase = 0
    }

    fun rounds(): Int = if (special) 1 else maxRounds
    fun active(): Boolean = state == WAIT || state == FIGHT || state == BREATH
    /** The world stands still while a Siege is on (normal worlds only). */
    fun holding(): Boolean = !special && (state == FIGHT || state == BREATH)
    fun phase(): Int = if (state != FIGHT) 0 else if (nowTick - roundStart < PHASE_A[round - 1]) 0 else 1
    fun roundName(): String = NAMES[(round - 1).coerceIn(0, 3)]
    fun secondsLeftToNext(): Int = if (state == BREATH || state == WAIT) ((nextAt - nowTick) / 5f).toInt().coerceAtLeast(0) else 0

    private fun begin(z: Int, tick: Int) {
        zone = z; round = 1; kills = 0; locked = false; overload = false; state = WAIT; nextAt = tick + LEAD_IN; lastPhase = 0
    }

    private fun startRound(tick: Int) {
        if (round > rounds()) { state = DONE; return }
        state = FIGHT; roundStart = tick; overload = false; lastPhase = 0; startSeq += 1
    }

    /** Called every tick of a boss zone by the sim: is the boss on the field right now? */
    fun allow(z: Int, tick: Int): Boolean {
        nowTick = tick
        if (z != zone) begin(z, tick)
        if ((state == WAIT || state == BREATH) && tick >= nextAt) startRound(tick)
        if (state == FIGHT) {
            val p = phase()
            if (p != lastPhase) { lastPhase = p; if (p == 1) phaseSeq += 1 }
        }
        return state == FIGHT
    }

    /** Full health at the start of every round (the boss never retreats and comes back wounded any more). */
    fun startHp(): Float = 1f

    /** The boss was destroyed: the round closes, whatever phase it was in. */
    fun onKill(tick: Int) {
        lastKillPhase = phase(); closedRound = round; kills += 1; closeSeq += 1
        if (round >= ROUNDS) { state = DONE; locked = false; clearedSeq += 1 }
        else if (round >= rounds()) { state = DONE; locked = !special && rounds() < ROUNDS; if (locked) lockedSeq += 1 }
        else { round += 1; state = BREATH; nextAt = tick + BREATHER }
    }

    /** Bigger bodies in later rounds: damage taken is divided by this. */
    fun scale(d: Float): Float = d / SCALE[(round - 1).coerceIn(0, 3)]

    /** Floor for the boss's anger: phase B is always angry, round 4 from the first second. */
    fun enrageFloor(): Int {
        if (state != FIGHT) return 0
        val b = phase() == 1
        return when {
            round >= 4 -> if (b) 2 else 1
            round == 3 -> if (b) 2 else 0
            else -> if (b) 1 else 0
        }
    }
    /** Shield rhythm per round: later rounds raise the shield more often and for longer. */
    fun periodAdj(): Int = if (state != FIGHT) 0 else intArrayOf(0, -2, -4, -6)[round - 1]
    fun upAdj(): Int = if (state != FIGHT) 0 else intArrayOf(-1, 1, 2, 3)[round - 1]

    /** How fast the boss's health runs down on its own (1 = the old rule). Shooters must earn the kill; non-shooters (EASY) and stalled fights drain. */
    fun timerDrain(): Float {
        if (special || state != FIGHT) return 1f
        val base = (if (canShoot) 0.08f else 0.5f) / SCALE[(round - 1).coerceIn(0, 3)]
        val over = nowTick - roundStart - OVERLOAD_AT
        if (over <= 0) return base
        overload = true
        return base + (1f - base) * (over / 40f).coerceIn(0f, 1f)
    }

    /** The round's own attack pattern, spawned on top of the boss's normal fire. */
    fun script(tick: Int, bossY: Int, pilotY: Int, easy: Boolean, rand: Random, out: MutableList<VisualTrackElement>) {
        if (state != FIGHT || special) return
        val b = phase() == 1
        var n = 0
        fun bullet(y: Int) { out += VisualTrackElement("sg_${tick}_${n++}", 0.95f, y.coerceIn(8, 92), "bullet") }
        fun aimed() { bullet(pilotY); BossSfx.fire() }
        fun fan(c: Int, gap: Int, count: Int) { val sh = if ((tick / 7) % 2 == 0) 0 else gap / 2; for (i in -(count / 2)..(count / 2)) bullet(c + i * gap + sh); BossSfx.fire() }
        fun wall() {
            if (out.count { it.type == "bullet" } > 22) return
            val gapY = (pilotY + rand.nextInt(-22, 23)).coerceIn(20, 80)
            var y = 10; while (y <= 90) { if (kotlin.math.abs(y - gapY) > 12) bullet(y); y += 10 }
            BossSfx.fire()
        }
        val r = if (easy) 1 else round
        when (r) {
            1 -> { if (tick % (if (b) 11 else 16) == 0) aimed(); if (b && tick % 33 == 0) { bullet(pilotY - 11); bullet(pilotY + 11) } }
            2 -> { if (tick % (if (b) 11 else 14) == 0) { if (b) fan(pilotY, 15, 5) else fan(bossY, 18, 3) } }
            3 -> { if (tick % (if (b) 18 else 24) == 0) wall(); if (b && tick % 9 == 0) aimed() }
            else -> {
                val cyc = (tick / (if (b) 16 else 20)) % 3
                when (cyc) {
                    0 -> if (tick % 6 == 0) aimed()
                    1 -> if (tick % (if (b) 10 else 13) == 0) fan(pilotY, 15, 5)
                    else -> if (tick % (if (b) 14 else 20) == 0) wall()
                }
            }
        }
    }
}
