package com.neonrush.game

import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * HAZARD EMPLACEMENTS (Stage 163). No zone is ever just dodging: every zone gets FOUR hazard waves, one in each quarter of the zone (on every difficulty).
 * An emplacement slides in from the right behind a WARN marker, holds its post for about four seconds shooting at you, then leaves.
 *   HZ_FLAMER  flame spitter     : bursts of fireballs in a fan (zone 2+)
 *   HZ_SPEAR   spear thrower     : a glowing charge, then a fast spear down your lane (zone 2+)
 *   HZ_ACID    acid sprayer      : drops of poison that rain down at an angle (zone 6+)
 *   HZ_ROCK    rock catapult     : lobbed boulders that arc over and land on your lane (zone 10+)
 * Difficulty changes the pressure, not whether it happens: warning time (already tier based), shots per burst and the time between bursts.
 * Boss zones are left alone (the boss brings its own pressure) and so is the assist window after repeated deaths.
 * Everything is a normal "obstacle" / "bullet" element, so hits, shields, close calls and Boom clearing all work as they already do.
 * They are SHOOTABLE: each emplacement has hit points (6 + zone/8 bolts, max 12; Plasma Gun upgrades count). Below half hull it fires faster, below a quarter it retreats.
 * They aim ahead: shots lead your movement. Destroying one scores and charges the Legend meter (see shootDown in the view model).
 * Hooks: ThreatDirector.advance (first line) and ThreatDirector.reset call into this object; the view model calls [spawn] right after ThreatDirector.spawn.
 */
object Hazards {
    const val HOLD_X = 0.74f
    private const val ENTER_X = 1.2f
    private const val WARN_TICKS = 8
    private const val HOLD = 44                       // ticks from spawn until it leaves (the slide-in is part of this)
    private val THRESH = floatArrayOf(0.04f, 0.29f, 0.54f, 0.79f)   // one wave per quarter of the zone
    val KINDS = arrayOf("HZ_FLAMER", "HZ_SPEAR", "HZ_ACID", "HZ_ROCK")

    private val done = BooleanArray(4)
    private var lastZone = 0
    private var lastKind = -1
    private var counter = 0
    private var busyUntil = 0
    private var lastUserY = 50
    private var userVy = 0
    private val hp = HashMap<String, Float>()
    private val hpMax = HashMap<String, Float>()
    private val flashUntil = HashMap<String, Long>()
    private val chargeAt = HashMap<String, Int>()
    private val shotAt = HashMap<String, Int>()
    private var cycleBase = 18
    private class Pending(val atTick: Int, val elem: VisualTrackElement)
    private val pending = mutableListOf<Pending>()

    fun reset() { done.fill(false); lastZone = 0; lastKind = -1; counter = 0; busyUntil = 0; pending.clear(); hp.clear(); hpMax.clear(); flashUntil.clear(); chargeAt.clear(); shotAt.clear(); lastUserY = 50; userVy = 0 }

    private fun idTick(id: String): Int = id.split("_").getOrNull(1)?.toIntOrNull() ?: 0

    /** [frac] = how far through the current zone the run is (0..1). [warnDelta] = the tier's telegraph time (EASY +3 ... LEGENDARY -2). */
    fun spawn(tick: Int, zone: Int, frac: Float, userY: Int, rand: Random, suppress: Boolean, assist: Float, warnDelta: Int): List<VisualTrackElement> {
        tierWarn = warnDelta
        userVy = (userY - lastUserY).coerceIn(-6, 6); lastUserY = userY
        val out = mutableListOf<VisualTrackElement>()
        val it = pending.iterator()
        while (it.hasNext()) { val p = it.next(); if (p.atTick <= tick) { out += p.elem; it.remove(); val m = (6 + zone / 8).coerceAtMost(12).toFloat(); hpMax[p.elem.id] = m; hp[p.elem.id] = m } }
        if (zone != lastZone) { lastZone = zone; done.fill(false) }
        if (zone < 2 || suppress || assist > 0.2f || pending.isNotEmpty() || tick < busyUntil) return out
        for (q in 0..3) {
            if (done[q] || frac < THRESH[q]) continue
            done[q] = true
            if (frac < THRESH[q] + 0.2f) { schedule(tick, zone, userY, rand, warnDelta, out); break }
        }
        return out
    }

    private fun schedule(tick: Int, zone: Int, userY: Int, rand: Random, warnDelta: Int, out: MutableList<VisualTrackElement>) {
        val avail = if (zone >= 10) 4 else if (zone >= 6) 3 else 2
        var k = rand.nextInt(avail)
        if (k == lastKind && avail > 1) k = (k + 1 + rand.nextInt(avail - 1)) % avail
        lastKind = k
        val n = ++counter
        val delay = (WARN_TICKS + warnDelta).coerceIn(5, 12)
        val y = when (k) { 2 -> 24; 3 -> 78; else -> userY.coerceIn(26, 74) }
        pending += Pending(tick + delay, VisualTrackElement("obs_${tick + delay}_hz${n}_$k", ENTER_X, y, "obstacle", KINDS[k]))
        out += VisualTrackElement("wrn_${tick}_${n}_hz_$delay", 0.96f, y, "fx", "WARN")
        busyUntil = tick + delay + HOLD + 6
        BossSfx.warn()
    }

    // ---- shooting back at them ----
    fun isHazard(sub: String): Boolean = sub == "HZ_FLAMER" || sub == "HZ_SPEAR" || sub == "HZ_ACID" || sub == "HZ_ROCK"
    /** 1 = full hull ... 0 = nearly dead. Unknown ids count as full. */
    fun hpFrac(id: String): Float { val h = hp[id] ?: return 1f; return (h / (hpMax[id] ?: 1f)).coerceIn(0f, 1f) }
    fun flashing(id: String): Boolean = (flashUntil[id] ?: 0L) > System.nanoTime() / 1_000_000L
    /** 0 idle, 1 charging, 2 firing: lets the art swap frames. */
    fun state(id: String, tick: Int): Int = if ((shotAt[id] ?: -99) >= tick - 2) 2 else if ((chargeAt[id] ?: -99) >= tick - 1) 1 else 0
    /** A bolt hit [id]. Returns true when the emplacement is destroyed (the caller removes it and scores). */
    fun hit(id: String): Boolean {
        val h = hp[id] ?: return true
        val left = h - com.neonrush.game.NrUpgrades.gunMul()
        flashUntil[id] = System.nanoTime() / 1_000_000L + 140L
        if (left <= 0f) { hp.remove(id); hpMax.remove(id); flashUntil.remove(id); chargeAt.remove(id); shotAt.remove(id); return true }
        hp[id] = left
        return false
    }
    private fun forget(id: String) { hp.remove(id); hpMax.remove(id); flashUntil.remove(id); chargeAt.remove(id); shotAt.remove(id) }

    /** Burst size and gap by tier: EASY gentle ... LEGENDARY fierce. */
    private fun shots(warnDelta: Int): Int = if (warnDelta >= 2) 2 else if (warnDelta >= 0) 3 else if (warnDelta == -1) 4 else 5
    private fun cycle(base: Int, warnDelta: Int, hf: Float = 1f): Int = (((base + 2 * warnDelta.coerceIn(-2, 3)) * (if (hf < 0.5f) 0.7f else 1f)).toInt()).coerceAtLeast(6)

    /** The tier's warnDelta, remembered from the last [spawn] call so [advance] can scale burst sizes and gaps. */
    @Volatile private var tierWarn = 0

    /** Motion and shooting for hazard elements; null when [elem] is not a hazard (the caller then carries on as usual). */

    fun advance(elem: VisualTrackElement, nextX: Float, actualY: Int, userY: Int, tick: Int, out: MutableList<VisualTrackElement>): Pair<Float, Int>? {
        val sub = elem.subType
        if (!sub.startsWith("HZ_")) return null
        val parts = elem.id.split("_")
        val age = tick - idTick(elem.id)
        val w = tierWarn
        when (sub) {
            // ---------------- emplacements ----------------
            "HZ_FLAMER", "HZ_SPEAR", "HZ_ACID", "HZ_ROCK" -> {
                val hf = hpFrac(elem.id)
                if (age >= HOLD || hf < 0.25f) { val nx = elem.xOffsetFraction + (if (hf < 0.25f) 0.08f else 0.05f); if (nx >= ENTER_X) { forget(elem.id); return -0.5f to actualY }; return nx to actualY }   // retreats when badly hurt
                if (nextX > HOLD_X) return nextX to actualY          // still sliding in
                val n = (elem.id.hashCode() and 0xff)
                val pred = (userY + userVy * 6).coerceIn(8, 92)      // where the pilot is heading: shots lead you
                var y = actualY
                when (sub) {
                    "HZ_FLAMER" -> {
                        y = (actualY + ((pred - actualY) * 0.08f).toInt()).coerceIn(14, 86)
                        val cy = cycle(16, w, hf); val ph = age % cy
                        if (ph >= cy - 4) { chargeAt[elem.id] = tick; out += VisualTrackElement("chg_${tick}_${ph - (cy - 4) + 1}_0", HOLD_X, y, "fx", "HZ_CHARGE") }
                        if (ph == 0) {
                            shotAt[elem.id] = tick
                            val c = shots(w)
                            for (i in 0 until c) {
                                val lane = (pred + ((i - (c - 1) / 2f) * 10f).toInt()).coerceIn(5, 95)
                                out += VisualTrackElement("bullet_${tick}_hz${n}f$i", HOLD_X - 0.03f, lane, "bullet", "HZ_FIREBALL")
                            }
                            BossSfx.fire()
                        }
                    }
                    "HZ_SPEAR" -> {
                        y = (actualY + ((pred - actualY) * 0.14f).toInt()).coerceIn(14, 86)
                        val cy = cycle(20, w, hf); val ph = age % cy
                        if (ph >= cy - 5) { chargeAt[elem.id] = tick; out += VisualTrackElement("chg_${tick}_${ph - (cy - 5) + 1}_1", HOLD_X, y, "fx", "HZ_CHARGE") }
                        if (ph == 0) {
                            shotAt[elem.id] = tick
                            out += VisualTrackElement("bullet_${tick}_hz${n}s0", HOLD_X - 0.03f, pred, "bullet", "HZ_SPEARSHOT")
                            if (w <= -1) out += VisualTrackElement("bullet_${tick}_hz${n}s1", HOLD_X - 0.03f, (pred + (if (userY > pred) 12 else -12)).coerceIn(5, 95), "bullet", "HZ_SPEARSHOT")
                            BossSfx.zap()
                        }
                    }
                    "HZ_ACID" -> {
                        y = (26 + (sin(age * 0.22f) * 14f).toInt()).coerceIn(10, 60)
                        val cy = cycle(10, w, hf)
                        if (age % cy == 0) {
                            shotAt[elem.id] = tick
                            val c = if (w >= 2) 1 else if (w >= -1) 2 else 3
                            for (i in 0 until c) out += VisualTrackElement("bullet_${tick}_hz${n}a$i", HOLD_X - 0.03f - i * 0.02f, (y + 3 + i * 3).coerceIn(5, 95), "bullet", "HZ_ACIDDROP")
                            BossSfx.zap()
                        }
                    }
                    else -> {
                        val cy = cycle(18, w, hf); val ph = age % cy
                        if (ph >= cy - 4) { chargeAt[elem.id] = tick; out += VisualTrackElement("chg_${tick}_${ph - (cy - 4) + 1}_3", HOLD_X, y, "fx", "HZ_CHARGE") }
                        if (ph == 0) {
                            shotAt[elem.id] = tick
                            val land = pred.coerceIn(20, 84)
                            out += VisualTrackElement("bullet_${tick}_hz${n}r_$land", HOLD_X - 0.03f, land, "bullet", "HZ_BOULDER")
                            if (w <= -1) out += VisualTrackElement("bullet_${tick}_hz${n}q_${(land + (if (land > 50) -18 else 18))}", HOLD_X - 0.05f, (land + (if (land > 50) -18 else 18)), "bullet", "HZ_BOULDER")
                            BossSfx.fire()
                        }
                    }
                }
                return HOLD_X to y
            }
            // ---------------- charge-up marker ----------------
            "HZ_CHARGE" -> return (if (age >= 1) -0.5f else elem.xOffsetFraction) to elem.yMatchPos
            // ---------------- shots ----------------
            "HZ_FIREBALL" -> return nextX to actualY
            "HZ_SPEARSHOT" -> return (nextX - 0.02f) to actualY
            "HZ_ACIDDROP" -> {
                val y = actualY + 2
                return (if (y >= 94) -0.5f else nextX) to y.coerceAtMost(95)
            }
            "HZ_BOULDER" -> {
                // id: bullet_<tick>_hz<n>r_<landingLane>: arcs up and comes down on the lane it was thrown at
                val land = parts.lastOrNull()?.toIntOrNull() ?: actualY
                val t = (age / 14f).coerceIn(0f, 1f)
                val y = land - (sin(PI.toFloat() * t) * 24f).toInt() * (if (land > 50) 1 else -1)
                return nextX to y.coerceIn(4, 96)
            }
        }
        return null
    }
}
