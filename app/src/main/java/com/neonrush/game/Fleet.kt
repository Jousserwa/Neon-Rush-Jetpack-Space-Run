package com.neonrush.game

import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * THE FLEET (Stage 149). Real escort bosses that fight next to the main boss. Each one has its own hull, shield rhythm, movement and fire; killing one leaves the rest fighting.
 * The main boss (the sim's boss) is the LAST body: it stays shielded ("escort shield") until every escort is down. The round closes when the main boss dies.
 *
 * How many: the 15-zone TIER of the boss zone (zone / 15, capped by what the plan unlocks, see Siege.planTier):
 *   era = tier / 4, step = tier % 4.   era 0: 1, 2, 3, 4 bosses.   era 1+: it starts again at 2 and climbs 2, 3, 4, 5 (never more than 5), with a NEW movement mechanic.
 * Mechanic = era % 4: FORMATION (stacked lanes, swaying) · ORBIT (they circle a point) · HUNTERS (telegraphed dives at the pilot) · SWEEPERS (they patrol across the whole width).
 * Every body also moves in x, not just up and down; the main boss borrows [dx].
 */
class Body(val idx: Int, var x: Float, var y: Float) {
    @Volatile var hp = 1f
    @Volatile var alive = true
    @Volatile var age = 0f          // seconds since it stepped on the field (warp-in while small)
    @Volatile var flash = 0f
    @Volatile var shield = false
    @Volatile var tel = 0f          // hunters: 0..1 telegraph before a dive
    @Volatile var tx = 0f           // dive target
    @Volatile var ty = 50f
    @Volatile var diving = false
    @Volatile var hx = 0.86f        // hunters: where it hovers
    @Volatile var hy = 50f
    @Volatile var dived = false
    @Volatile var sp = 0            // species (see Roster)
}

object Fleet {
    const val MAX = 5
    const val ESCORT_DMG = 3f       // an escort's hull is smaller than the main boss's: bolts hit it this much harder
    val NAMES = arrayOf("FORMATION", "ORBIT", "HUNTERS", "SWEEPERS")
    val TIPS = arrayOf("STACKED LANES · PICK THEM OFF", "THEY CIRCLE YOU · TIME YOUR SHOTS", "WATCH THE RED LINE · DODGE THE DIVE", "THEY PATROL THE WHOLE TRACK")
    val bodies = CopyOnWriteArrayList<Body>()
    @Volatile var visible = false
    @Volatile var dx = 0f               // x offset of the main boss
    @Volatile var total = 1
    @Volatile var mech = 0
    @Volatile var deathSeq = 0
    @Volatile var lastDeadX = 0.8f
    @Volatile var lastDeadY = 50f
    @Volatile var hitSeq = 0
    @Volatile var hitX = 0f
    @Volatile var hitY = 0f
    @Volatile var hitAmt = 0f
    @Volatile var contactSeq = 0
    @Volatile var spawnSeq = 0
    private var t = 0f

    fun reset() { bodies.clear(); visible = false; dx = 0f; total = 1; mech = 0; t = 0f }
    fun alive(): Int = bodies.count { it.alive }
    fun tierOf(zone: Int): Int = minOf(maxOf(zone, 0) / 15, Siege.planTier)
    fun totalFor(zone: Int): Int { if (Siege.special) return 1; val tier = tierOf(zone); val era = tier / 4; val st = tier % 4; return if (era == 0) st + 1 else minOf(MAX, st + 2) }
    fun mechFor(zone: Int): Int = (tierOf(zone) / 4) % 4

    /** The boss steps onto the field (round start, after a phase break, after a withdrawal). */
    fun onEntry(fresh: Boolean, repair: Float, zone: Int) {
        total = totalFor(zone); mech = mechFor(zone)
        if (fresh) {
            bodies.clear()
            val crew = Roster.pick(zone, total - 1, Siege.round)
            for (i in 1 until total) { val b = Body(i, 1.15f, 50f); b.hx = 0.80f; b.hy = 50f; b.sp = crew.getOrElse(i - 1) { 0 }; bodies.add(b) }
            if (total > 1) spawnSeq += 1
        } else {
            for (b in bodies) if (b.alive) { b.hp = (b.hp + repair).coerceAtMost(1f); b.x = 1.15f; b.age = 0f; b.diving = false; b.tel = 0f }
        }
        dx = 0f
    }

    private fun lerp(a: Float, b: Float, k: Float) = a + (b - a) * k.coerceIn(0f, 1f)

    /** Once per frame from the overlay: moves every body (all directions) and the main boss's x offset. */
    fun step(dt: Float, pilotX: Float, pilotY: Float) {
        if (Pause.frozen()) return
        t += dt
        val n = total.coerceAtLeast(1)
        when (mech) {
            0 -> dx = sin(t * 0.5f) * 0.04f
            1 -> dx = -0.18f + 0.14f * cos(t * 0.8f)
            2 -> dx = sin(t * 0.7f) * 0.06f
            else -> dx = sin(t * 0.45f) * 0.2f - 0.05f
        }
        for (b in bodies) {
            if (!b.alive) continue
            b.age += dt; if (b.flash > 0f) b.flash = (b.flash - dt * 4f).coerceAtLeast(0f)
            val spc = Roster.of(b.sp)
            b.shield = ((t * 5f).toInt() + b.idx * 5) % spc.per < spc.duty
            val i = b.idx
            var gx: Float; var gy: Float
            when (mech) {
                0 -> { gx = 0.80f - 0.05f * (i % 2) + sin(t * 0.6f + i) * 0.05f; gy = 50f + (i - (n - 1) / 2f) * (66f / n) + sin(t * 0.9f + i * 1.7f) * 10f }
                1 -> { val a = t * 0.8f + 2f * PI.toFloat() * i / n; gx = 0.70f + 0.14f * cos(a); gy = 50f + 30f * sin(a) }
                2 -> {
                    b.hy = 50f + (i - (n - 1) / 2f) * (60f / n) + sin(t * 0.8f + i) * 6f; b.hx = 0.86f + sin(t * 0.5f + i) * 0.03f
                    val c = (t + i * 1.9f) % 8f
                    gx = b.hx; gy = b.hy
                    if (c < 5f) { b.diving = false; b.tel = 0f; b.dived = false }
                    else if (c < 5.9f) { b.tel = (c - 5f) / 0.9f; b.tx = pilotX + 0.08f; b.ty = pilotY }
                    else if (c < 6.5f) { b.tel = 0f; b.diving = true; val k = (c - 5.9f) / 0.6f; val e = k * k; gx = lerp(b.hx, b.tx, e); gy = lerp(b.hy, b.ty, e)
                        if (!b.dived && abs(gx - pilotX) < 0.07f && abs(gy - pilotY) < 9f) { b.dived = true; contactSeq += 1 } }
                    else { b.diving = false; val k = (c - 6.5f) / 1.5f; gx = lerp(b.tx, b.hx, k); gy = lerp(b.ty, b.hy, k) }
                }
                else -> { gx = 0.65f + 0.25f * sin(t * 0.5f + i * 1.3f); gy = 14f + 72f * (if (n > 1) i.toFloat() / (n - 1) else 0.5f) + sin(t * 0.7f + i) * 6f }
            }
            if (b.age < 1.0f) { gx = lerp(1.15f, gx, b.age); }   // warp in from the right edge
            val rate = if (mech == 2 && b.diving) 30f else 4.5f * spc.speed
            b.x = lerp(b.x, gx, dt * rate); b.y = lerp(b.y, gy, dt * rate)
        }
    }

    /** Escort fire, once per sim tick while the boss is on the field. Pattern follows the Siege round (1 aimed lane, 2 triples, 3 aimed, 4 aimed + lane). */
    fun fire(tick: Int, pilotY: Int, easy: Boolean, rand: Random, out: MutableList<VisualTrackElement>) {
        if (Siege.special || easy) return
        val iv = intArrayOf(22, 18, 15, 12)[(Siege.round - 1).coerceIn(0, 3)]
        var n = 0; var any = false
        for (b in bodies) {
            if (!b.alive || b.age < 1.2f || b.diving) continue
            if ((tick + b.idx * 3) % (iv * Roster.of(b.sp).fire).toInt().coerceAtLeast(6) != 0) continue
            val y0 = b.y.toInt().coerceIn(8, 92); val bx = (b.x + 0.02f).coerceIn(0.2f, 0.97f)
            fun bullet(y: Int) { out += VisualTrackElement("fl_${tick}_${b.idx}_${n++}", bx, y.coerceIn(8, 92), "bullet"); any = true }
            when (Siege.round) {
                1 -> bullet(y0)
                2 -> { bullet(y0); bullet(y0 - 11); bullet(y0 + 11) }
                3 -> bullet(pilotY + rand.nextInt(-3, 4))
                else -> { bullet(pilotY); bullet(y0) }
            }
        }
        if (any) BossSfx.fire()
    }

    /** A pilot bolt travelling from [fromX] to [toX] at height [y]: 0 = missed, 1 = hit, 2 = bounced off a shield. Applies the damage. */
    fun hitBolt(fromX: Float, toX: Float, y: Float, armor: Boolean): Int {
        if (!visible) return 0
        for (b in bodies) {
            if (!b.alive || b.age < 0.6f) continue
            if (toX < b.x - 0.05f || fromX > b.x + 0.04f || abs(b.y - y) > 10f) continue
            hitX = b.x; hitY = b.y
            if (b.shield) { b.flash = 0.3f; hitAmt = 0f; hitSeq += 1; return 2 }
            val d = Siege.scale(Shooting.BOSS_DAMAGE * Siege.dmgMul * NrUpgrades.gunMul() * (if (armor) 1.5f else 1f)) * ESCORT_DMG / Roster.of(b.sp).tough
            hurt(b, d)
            hitAmt = d * 0.35f; hitSeq += 1
            return 1
        }
        return 0
    }

    private fun hurt(b: Body, d: Float) {
        b.hp -= d; b.flash = 0.25f
        if (b.hp <= 0f && b.alive) { b.alive = false; lastDeadX = b.x; lastDeadY = b.y; deathSeq += 1 }
    }

    /** Air strikes and bombs: a fraction of the hull to every escort at once. Returns how many escorts were on the field. */
    fun damageAll(frac: Float): Int {
        var c = 0
        for (b in bodies) if (b.alive) { c += 1; hurt(b, Siege.scale(frac) * 1.2f / Roster.of(b.sp).tough) }
        return c
    }
}
