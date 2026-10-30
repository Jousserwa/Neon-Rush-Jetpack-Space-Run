package com.neonrush.game

import kotlin.random.Random

/**
 * THE SIEGE (Stage 148, loop version). A boss zone is a Siege of up to 4 ROUNDS. Inside a round the boss FIGHTS IN PHASES, over and over, until it is destroyed:
 *   Phase A (pressure, 10-20 s) -> boss backs off 3-4 s -> Phase B (desperation, 10-20 s) -> boss backs off 3-4 s -> Phase A again ... (hull stays wounded: it repairs only a little between phases)
 *  - Destroyed in ANY phase = the round is closed. Then a long break of about 3-4 minutes (the world flies on), then the next round with a fresh, tougher boss.
 *  - Each round attacks differently: 1 PROBE (aimed shots) · 2 BARRAGE (fans) · 3 SIEGE (walls with a gap) · 4 WRATH (all of it, mixed).
 *  - While the boss is fighting or backing off the world HOLDS (no distance, no zone change). During the long breaks it flies on, but never past the end of the zone.
 *  - Plan share: free 1 round, Pro MEDIUM 2, Pro HARD 3, Pro LEGENDARY 4 (see [configure]). Rounds past the plan are shown as a locked teaser.
 *  - Safety net: after 4 phases (A,B,A,B, about 1.5-2 min) without a kill the boss WITHDRAWS for about 3 min, then returns, still wounded. Nobody gets stuck.
 * Time unit: the sim tick (about 0.2 s).
 */
object Siege {
    const val ROUNDS = 4
    const val LEAD_IN = 24          // ticks before round 1 of a boss zone
    const val MAX_PHASES = 4        // phases fought without a kill before the boss withdraws
    const val RETREAT_TICKS = 900   // ticks (about 3 min) the boss stays away after withdrawing
    const val REPAIR = 0.35f        // hull it repairs during a long withdrawal
    const val PHASE_REPAIR = 0.06f  // hull it repairs during the short break between phases
    private val LEN_A = intArrayOf(50, 60, 75, 85)            // phase A length per round (10 / 12 / 15 / 17 s)
    private val LEN_B = intArrayOf(60, 70, 85, 100)           // phase B length per round (12 / 14 / 17 / 20 s)
    private val SCALE = floatArrayOf(1f, 1.25f, 1.5f, 1.8f)   // each later round takes more damage to bring down
    val NAMES = arrayOf("PROBE", "BARRAGE", "SIEGE", "WRATH")
    val TIPS = arrayOf("AIMED SHOTS · LEARN ITS RHYTHM", "FAN VOLLEYS · SLIP BETWEEN THE BOLTS", "WALLS OF FIRE · FIND THE GAP", "EVERYTHING AT ONCE · NO MERCY")

    const val IDLE = 0; const val WAIT = 1; const val FIGHT = 2; const val BREATH = 3; const val DONE = 4; const val RETREAT = 5; const val GAP = 6

    @Volatile var state = IDLE
    @Volatile var zone = -1
    @Volatile var round = 1
    @Volatile var ph = 0                     // 0 = phase A, 1 = phase B (while in GAP: the phase that comes next)
    @Volatile var phaseStart = 0
    @Volatile var phasesFought = 0
    @Volatile var nextAt = 0
    @Volatile var nowTick = 0
    @Volatile var maxRounds = 1
    @Volatile var canShoot = false
    @Volatile var planTier = 0               // highest 15-zone boss tier the plan unlocks (free 0, MEDIUM 1, HARD 3, LEGENDARY all)
    @Volatile var dmgMul = 1f                // pilot bolt damage on bosses per difficulty
    @Volatile var drainBase = 0.5f           // passive hull drain per difficulty (1 = the old rule)
    @Volatile var entryFresh = true          // the boss that just stepped on the field is brand new
    @Volatile var lastRepair = 0f            // hull repaired for that entry
    @Volatile var special = false            // special worlds keep the old single-boss rules
    @Volatile var locked = false             // the plan ended the Siege early: rounds above [rounds] are a teaser
    @Volatile var returning = false          // this boss entry is a return after a withdrawal
    @Volatile var fresh = true               // next entry is a brand-new boss at full hull
    @Volatile var afterRetreat = false
    @Volatile var lastHp = 1f                // the sim reports the boss hull every tick
    @Volatile var carryHp = 1f
    @Volatile var kills = 0
    @Volatile var runKills = 0               // boss rounds destroyed in this whole run (Legendary checkpoint every 8)
    @Volatile var lastKillPhase = 0
    @Volatile var closedRound = 0
    @Volatile var startSeq = 0               // a round began (or the boss returned)
    @Volatile var phaseSeq = 0               // a new phase began (not the first of a round)
    @Volatile var gapSeq = 0                 // the boss backed off between phases
    @Volatile var retreatSeq = 0             // the boss withdrew for the long break
    @Volatile var closeSeq = 0               // a boss was destroyed (round closed)
    @Volatile var clearedSeq = 0             // round 4 closed: Siege cleared
    @Volatile var lockedSeq = 0              // the plan's last round closed with rounds still to go

    fun configure(difficultyName: String, isPro: Boolean) {
        maxRounds = if (!isPro) 1 else when (difficultyName) { "MEDIUM" -> 2; "HARD" -> 3; "LEGENDARY" -> 4; else -> 1 }
        canShoot = difficultyName != "EASY"
        planTier = if (!isPro) 0 else when (difficultyName) { "MEDIUM" -> 1; "HARD" -> 3; "LEGENDARY" -> 99; else -> 0 }
        dmgMul = when (difficultyName) { "EASY" -> 0f; "MEDIUM" -> 0.7f; "HARD" -> 0.85f; else -> 1f }
        drainBase = when (difficultyName) { "EASY" -> 0.5f; "MEDIUM" -> 0.2f; "HARD" -> 0.12f; else -> 0.08f }.let { if (!isPro) maxOf(it, 0.25f) else it }
    }

    fun reset() {
        state = IDLE; zone = -1; round = 1; ph = 0; phasesFought = 0; locked = false; returning = false; fresh = true; afterRetreat = false
        kills = 0; runKills = 0; lastKillPhase = 0; lastHp = 1f; carryHp = 1f
    }

    fun rounds(): Int = if (special) 1 else maxRounds
    fun active(): Boolean = state != IDLE && state != DONE
    /** The world stands still while the boss fights or backs off between phases (normal worlds only). */
    fun holding(): Boolean = !special && (state == FIGHT || state == GAP)
    /** During the long breaks the world flies on, but never past the end of this zone. */
    fun retreating(): Boolean = !special && (state == RETREAT || state == BREATH)
    fun phase(): Int = if (state == FIGHT || state == GAP) ph else 0
    fun roundName(): String = NAMES[(round - 1).coerceIn(0, 3)]
    fun secondsLeftToNext(): Int = if (state == BREATH || state == WAIT || state == RETREAT || state == GAP) ((nextAt - nowTick) / 5f).toInt().coerceAtLeast(0) else 0
    /** m:ss for the long countdowns. */
    fun clock(): String { val t = secondsLeftToNext(); return "${t / 60}:${(t % 60).toString().padStart(2, '0')}" }
    private fun phaseLen(): Int = (if (ph == 0) LEN_A else LEN_B)[(round - 1).coerceIn(0, 3)]
    private fun gapLen(): Int = 15 + 3 * ((round + ph) % 3)                 // 3.0 / 3.6 / 4.2 s
    private fun breakLen(): Int = 900 + 50 * round                          // 3.2 - 3.7 min after a kill

    private fun begin(z: Int, tick: Int) {
        zone = z; round = 1; kills = 0; locked = false; returning = false; fresh = true; afterRetreat = false; ph = 0; phasesFought = 0
        state = WAIT; nextAt = tick + LEAD_IN
    }

    private fun startRound(tick: Int, resume: Boolean) {
        if (round > rounds()) { state = DONE; return }
        if (!resume) fresh = true
        returning = resume; ph = 0; phasesFought = 1; phaseStart = tick; state = FIGHT; startSeq += 1
    }

    private fun endPhase(tick: Int) {
        carryHp = lastHp
        if (phasesFought >= MAX_PHASES) { state = RETREAT; afterRetreat = true; nextAt = tick + RETREAT_TICKS; retreatSeq += 1 }
        else { ph = 1 - ph; state = GAP; afterRetreat = false; nextAt = tick + gapLen(); gapSeq += 1 }
    }

    /** Called every tick of a boss zone by the sim: is the boss on the field right now? */
    fun allow(z: Int, tick: Int): Boolean {
        nowTick = tick
        if (z != zone) begin(z, tick)
        when (state) {
            WAIT, BREATH -> if (tick >= nextAt) startRound(tick, false)
            RETREAT -> if (tick >= nextAt) startRound(tick, true)
            GAP -> if (tick >= nextAt) { state = FIGHT; phaseStart = tick; phasesFought += 1; phaseSeq += 1 }
            FIGHT -> if (tick - phaseStart >= phaseLen()) endPhase(tick)
            else -> {}
        }
        return state == FIGHT
    }

    /** Hull when the boss steps onto the field: full for a new round, otherwise the wounded boss with a small (phase break) or bigger (withdrawal) repair. */
    fun startHp(): Float {
        entryFresh = fresh
        if (fresh) { fresh = false; afterRetreat = false; lastRepair = 0f; return 1f }
        val rep = if (afterRetreat) REPAIR else PHASE_REPAIR
        lastRepair = rep
        afterRetreat = false
        return (carryHp + rep).coerceIn(0.05f, 1f)
    }

    /** The boss was destroyed: the round closes, whatever phase it was in. Then the long break. */
    fun onKill(tick: Int) {
        lastKillPhase = phase(); closedRound = round; kills += 1; runKills += 1; closeSeq += 1
        if (round >= ROUNDS) { state = DONE; locked = false; clearedSeq += 1 }
        else if (round >= rounds()) { state = DONE; locked = !special && rounds() < ROUNDS; if (locked) lockedSeq += 1 }
        else { round += 1; state = BREATH; nextAt = tick + breakLen(); fresh = true; ph = 0 }
    }

    /** Bigger bodies in later rounds: damage taken is divided by this. */
    fun scale(d: Float): Float = d / SCALE[(round - 1).coerceIn(0, 3)]

    /** Floor for the boss's anger: phase B is always angry, round 4 from the first second. */
    fun enrageFloor(): Int {
        if (state != FIGHT) return 0
        val b = ph == 1
        return when {
            round >= 4 -> if (b) 2 else 1
            round == 3 -> if (b) 2 else 0
            else -> if (b) 1 else 0
        }
    }
    /** Shield rhythm per round: later rounds raise the shield more often and for longer. */
    fun periodAdj(): Int = if (state != FIGHT) 0 else intArrayOf(0, -2, -4, -6)[round - 1]
    fun upAdj(): Int = if (state != FIGHT) 0 else intArrayOf(-1, 1, 2, 3)[round - 1]

    /** How fast the boss's health runs down on its own (1 = the old rule). Shooters must earn the kill; non-shooters (EASY) get a pass-through boss that burns out. */
    fun timerDrain(): Float {
        if (special || state != FIGHT) return 1f
        if (Fleet.alive() > 0) return 0f                  // the wingmen shield the main boss: no drain until they are down
        return drainBase / SCALE[(round - 1).coerceIn(0, 3)]
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
