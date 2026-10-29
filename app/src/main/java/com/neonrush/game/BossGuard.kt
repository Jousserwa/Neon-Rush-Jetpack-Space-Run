package com.neonrush.game

/**
 * Boss counter-measures (Stage 136, extended Stage 137). Active on Medium/Hard/Legendary ([hard]); EASY bosses stay as they were.
 *  - SHIELD: the boss flickers, then raises a shield that bounces bolts away.
 *  - DODGE: it sidesteps a bolt about to hit it.
 *  - ARMOR: chained hits do less damage, recovering when the pilot stops firing.
 *  - COUNTER-FIRE: while the pilot shoots, the boss answers with extra aimed bullets.
 *  - ENRAGE: below 50% / 25% health it fires faster.
 *  - PHASE 2 (Legendary only): at 50% health the boss transforms: shorter shield gaps and a spread volley above and below it.
 *  - WORLD ECHO twist of the week (see WorldEchoBoss) bends the numbers.
 */
object BossGuard {
    @Volatile var hard = true           // Medium and up
    @Volatile var legend = false        // Legendary only
    @Volatile var shieldUp = false
    @Volatile var shieldWarn = false
    @Volatile var enrage = 0            // 0 calm, 1 angry (<50%), 2 furious (<25%)
    @Volatile var phase2 = false
    @Volatile var phase2Ms = -99999L
    @Volatile var incomingY = -1f       // nearest bolt flying at the boss (set by the shooting overlay), -1 = none
    @Volatile var lastDeflect = false
    @Volatile var hitStreak = 0
    @Volatile var lastShotMs = -99999L
    @Volatile var lastHitMs = -99999L
    @Volatile var dodgeMs = -99999L
    @Volatile private var lastBossY = 50

    private fun now() = System.nanoTime() / 1_000_000L

    fun configure(hardMode: Boolean, legendMode: Boolean) { hard = hardMode; legend = legendMode }

    fun reset() {
        shieldUp = false; shieldWarn = false; enrage = 0; phase2 = false; incomingY = -1f; lastDeflect = false
        hitStreak = 0; lastShotMs = -99999L; lastHitMs = -99999L; dodgeMs = -99999L
    }

    fun noteShot() { lastShotMs = now() }
    private fun shooting() = now() - lastShotMs < 1800L

    /** Fire interval after enrage + weekly twist (ticks, never below 3). */
    fun iv(base: Int, health: Float): Int {
        if (!hard) return base
        val k = if (health < 0.25f) 0.55f else if (health < 0.5f) 0.75f else 1f
        return (base * k * WorldEchoBoss.ivMul()).toInt().coerceAtLeast(3)
    }

    /** How fast the boss's health runs down (GLASS echo = fragile). */
    fun drain(): Float = if (hard) WorldEchoBoss.drainMul() else 1f

    /** Once per sim tick while a boss is on screen; returns the boss y after any sidestep. */
    fun step(tick: Int, health: Float, bossY: Int, minY: Int = 12, maxY: Int = 88): Int {
        if (!hard) { shieldUp = false; shieldWarn = false; enrage = 0; lastBossY = bossY; return bossY }
        if (health > 0.95f) phase2 = false
        enrage = if (health < 0.25f) 2 else if (health < 0.5f) 1 else 0
        if (legend && enrage >= 1 && !phase2) { phase2 = true; phase2Ms = now() }
        val period = if (phase2) 14 else if (enrage >= 1) 16 else 20
        val up = (if (enrage == 2) 7 else if (enrage == 1) 6 else 5) + WorldEchoBoss.shieldBonus()
        val ph = tick % period
        shieldUp = ph < up && health > 0.1f && !BossLunge.vulnerable   // a lunging boss drops its shield
        shieldWarn = !shieldUp && ph >= period - 2 && health > 0.1f
        var y = bossY
        val inc = incomingY
        if (inc >= 0f && !shieldUp && kotlin.math.abs(inc - y) <= 14f && tick % 4 != 0) {
            val stp = if (enrage == 2) 16 else if (enrage == 1) 13 else 10
            y += if (inc >= y) -stp else stp
            y = y.coerceIn(minY, maxY)
            dodgeMs = now()
        }
        lastBossY = y
        return y
    }

    /** Extra bullets: aimed answers while the pilot fires (or always on the SOLAR echo), plus the phase 2 spread. */
    fun counter(tick: Int, pilotY: Int, out: MutableList<VisualTrackElement>) {
        if (!hard) return
        if (shooting() || WorldEchoBoss.alwaysCounter()) {
            val every = if (enrage == 2) 4 else if (enrage == 1) 6 else if (WorldEchoBoss.alwaysCounter()) 8 else 9
            if (tick % every == 0) { out += VisualTrackElement("cbullet_$tick", 0.95f, pilotY, "bullet"); BossSfx.fire() }
        }
        if (legend && phase2 && tick % 7 == 3) {
            out += VisualTrackElement("sp2a_$tick", 0.95f, (lastBossY - 20).coerceAtLeast(8), "bullet")
            out += VisualTrackElement("sp2b_$tick", 0.95f, (lastBossY + 20).coerceAtMost(92), "bullet")
        }
    }

    /** A bolt reached the boss: health to remove (0 when the shield bounced it). Sets [lastDeflect]. */
    fun hit(base: Float): Float {
        if (shieldUp) { lastDeflect = true; return 0f }
        lastDeflect = false
        val lungeMul = if (BossLunge.vulnerable) 2f else 1f      // open mouth = double damage
        val armorMul = if (Shooting.lastArmor) 1.5f else 1f      // armor rounds hit harder
        val t = now()
        hitStreak = if (t - lastHitMs > 1200L) 1 else hitStreak + 1
        lastHitMs = t
        val armor = 1f / (1f + 0.22f * (hitStreak - 1).coerceAtMost(8))
        val rage = if (enrage == 2) 0.7f else if (enrage == 1) 0.85f else 1f
        return base * armor * rage * (if (phase2) 0.9f else 1f) * lungeMul * armorMul
    }
}
