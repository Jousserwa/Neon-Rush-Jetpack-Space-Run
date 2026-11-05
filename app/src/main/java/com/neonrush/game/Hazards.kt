package com.neonrush.game

import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * HAZARD EMPLACEMENTS (Stage 163, ladder in Stage 168). Waves keep coming with a short calm between them (4 / 3 / 2 / 1.5 s on EASY / MEDIUM / HARD / LEGENDARY).
 * THE LADDER: each hazard has 10 LEVELS and the next hazard arrives only after the one before it has had them all. Levels 1-4 sharpen it (bigger bursts, shorter gaps),
 * levels 5-9 make REAL COPIES of it (2, then 3 at once, staggered), level 10 is its apex form (gold glow, +36% hull, one extra move). How many kinds are active at once: EASY 1, MEDIUM 2, HARD 3, LEGENDARY 4.
 * (old text) No zone is ever just dodging.
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
    val KINDS = arrayOf("HZ_FLAMER", "HZ_SPEAR", "HZ_ACID", "HZ_ROCK")

    private const val MAX_LV = 10
    private const val STAGGER = 5                     // ticks between the copies of one wave
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

    fun reset() { lastKind = -1; counter = 0; busyUntil = 0; pending.clear(); hp.clear(); hpMax.clear(); flashUntil.clear(); chargeAt.clear(); shotAt.clear(); lastUserY = 50; userVy = 0 }

    private fun idTick(id: String): Int = id.split("_").getOrNull(1)?.toIntOrNull() ?: 0

    /** [frac] = how far through the current zone the run is (0..1). [warnDelta] = the tier's telegraph time (EASY +3 ... LEGENDARY -2). */
    fun spawn(tick: Int, zone: Int, frac: Float, userY: Int, rand: Random, suppress: Boolean, assist: Float, warnDelta: Int): List<VisualTrackElement> {
        tierWarn = warnDelta
        userVy = (userY - lastUserY).coerceIn(-6, 6); lastUserY = userY
        val out = mutableListOf<VisualTrackElement>()
        val it = pending.iterator()
        while (it.hasNext()) { val p = it.next(); if (p.atTick <= tick) { out += p.elem; it.remove(); val lvl = levelOfId(p.elem.id); val m = (6 + zone / 8).coerceAtMost(12) * (1f + 0.04f * (lvl - 1)); hpMax[p.elem.id] = m; hp[p.elem.id] = m } }
        if (zone < 2 || suppress || assist > 0.2f || pending.isNotEmpty() || tick < busyUntil) return out
        schedule(tick, zone, userY, rand, warnDelta, out)
        return out
    }

    // ---- the ladder (Stage 168) ----
    private fun diff(w: Int): Int = if (w >= 2) 0 else if (w >= 0) 1 else if (w == -1) 2 else 3    // 0 EASY, 1 MEDIUM, 2 HARD, 3 LEGENDARY (from the tier's telegraph time)
    /** 0 EASY .. 3 LEGENDARY, from the last spawn call (other systems, such as the Roster, read it). */
    fun diffIdx(): Int = diff(tierWarn)
    private fun perLevel(d: Int): Int = if (d <= 1) 2 else 1                    // zones it takes a hazard to gain one level
    private fun introZone(k: Int, d: Int): Int = 2 + k * 10 * perLevel(d)       // a hazard arrives only after the one before it has had its ten levels
    fun levelFor(k: Int, zone: Int, d: Int): Int = ((zone - introZone(k, d)) / perLevel(d) + 1).coerceIn(1, MAX_LV)
    private fun eligible(zone: Int, d: Int): List<Int> = (0..3).filter { zone >= introZone(it, d) }.takeLast(d + 1)   // EASY 1 hazard at a time ... LEGENDARY all 4
    private fun restTicks(d: Int): Int = intArrayOf(33, 25, 17, 12)[d.coerceIn(0, 3)]            // calm between two waves: 4 s, 3 s, 2 s, 1.5 s (the classic threats fill it)
    /** Level 1..10 of an emplacement (stored at the end of its id). */
    fun levelOfId(id: String): Int = id.substringAfterLast('_').toIntOrNull()?.coerceIn(1, MAX_LV) ?: 1
    fun level(id: String): Int = levelOfId(id)

    private fun schedule(tick: Int, zone: Int, userY: Int, rand: Random, warnDelta: Int, out: MutableList<VisualTrackElement>) {
        val d = diff(warnDelta)
        val el = eligible(zone, d)
        if (el.isEmpty()) return
        val bag = ArrayList<Int>(el); bag.add(el.last())            // the newest hazard shows up a little more often
        var k = bag[rand.nextInt(bag.size)]
        if (k == lastKind && el.size > 1) { val others = el.filter { it != lastKind }; k = others[rand.nextInt(others.size)] }
        lastKind = k
        val lv = levelFor(k, zone, d)
        val copies = (if (lv >= 8) 3 else if (lv >= 5) 2 else 1).coerceAtMost(if (d == 0) 2 else 3)   // levels 5-9: it makes real copies of itself
        val n = ++counter
        val delay = (WARN_TICKS + warnDelta).coerceIn(5, 12)
        val base = when (k) { 2 -> 24; 3 -> 78; else -> userY.coerceIn(26, 74) }
        for (i in 0 until copies) {
            val y = when (k) {
                2 -> (24 + i * 4).coerceIn(10, 60)
                3 -> if (i == 0) 78 else if (i == 1) 24 else 51
                else -> { val off = if (i == 0) 0 else (if (i % 2 == 1) -26 else 26); val yy = base + off; (if (yy in 14..86) yy else base - off).coerceIn(14, 86) }
            }
            val at = tick + delay + i * STAGGER
            pending += Pending(at, VisualTrackElement("obs_${at}_hz${n}${'a' + i}_${k}_$lv", ENTER_X, y, "obstacle", KINDS[k]))
        }
        out += VisualTrackElement("wrn_${tick}_${n}_hz_$delay", 0.96f, base, "fx", "WARN")
        busyUntil = tick + delay + (copies - 1) * STAGGER + HOLD + 6 + restTicks(d)
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
    fun hit(id: String, extra: Float = 1f): Boolean {
        val h = hp[id] ?: return true
        val left = h - com.neonrush.game.NrUpgrades.gunMul() * extra
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
        val lv = levelOfId(elem.id)
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
                        val cy = cycle(16 - (lv - 1) / 2, w, hf); val ph = age % cy
                        if (ph >= cy - 4) { chargeAt[elem.id] = tick; out += VisualTrackElement("chg_${tick}_${ph - (cy - 4) + 1}_0", HOLD_X, y, "fx", "HZ_CHARGE") }
                        if (ph == 0) {
                            shotAt[elem.id] = tick
                            val c = (shots(w) + (lv - 1) / 3).coerceAtMost(8)
                            for (i in 0 until c) {
                                val lane = (pred + ((i - (c - 1) / 2f) * (if (lv >= 10) 14f else 10f)).toInt()).coerceIn(5, 95)
                                out += VisualTrackElement("bullet_${tick}_hz${n}f$i", HOLD_X - 0.03f, lane, "bullet", "HZ_FIREBALL")
                            }
                            BossSfx.fire()
                        }
                    }
                    "HZ_SPEAR" -> {
                        y = (actualY + ((pred - actualY) * 0.14f).toInt()).coerceIn(14, 86)
                        val cy = cycle(20 - (lv - 1) / 2, w, hf); val ph = age % cy
                        if (ph >= cy - 5) { chargeAt[elem.id] = tick; out += VisualTrackElement("chg_${tick}_${ph - (cy - 5) + 1}_1", HOLD_X, y, "fx", "HZ_CHARGE") }
                        if (ph == 0) {
                            shotAt[elem.id] = tick
                            out += VisualTrackElement("bullet_${tick}_hz${n}s0", HOLD_X - 0.03f, pred, "bullet", "HZ_SPEARSHOT")
                            if (w <= -1 || lv >= 6) out += VisualTrackElement("bullet_${tick}_hz${n}s1", HOLD_X - 0.03f, (pred + (if (userY > pred) 12 else -12)).coerceIn(5, 95), "bullet", "HZ_SPEARSHOT")
                            if (lv >= 10) out += VisualTrackElement("bullet_${tick}_hz${n}s2", HOLD_X - 0.03f, (pred + (if (userY > pred) -12 else 12)).coerceIn(5, 95), "bullet", "HZ_SPEARSHOT")
                            BossSfx.zap()
                        }
                    }
                    "HZ_ACID" -> {
                        y = (26 + (sin(age * 0.22f) * 14f).toInt()).coerceIn(10, 60)
                        val cy = cycle(10 - (lv - 1) / 3, w, hf)
                        if (age % cy == 0) {
                            shotAt[elem.id] = tick
                            val c = ((if (w >= 2) 1 else if (w >= -1) 2 else 3) + (lv - 1) / 3).coerceAtMost(6)
                            for (i in 0 until c) out += VisualTrackElement("bullet_${tick}_hz${n}a$i", HOLD_X - 0.03f - i * 0.02f, (y + 3 + i * 3).coerceIn(5, 95), "bullet", "HZ_ACIDDROP")
                            BossSfx.zap()
                        }
                    }
                    else -> {
                        val cy = cycle(18 - (lv - 1) / 2, w, hf); val ph = age % cy
                        if (ph >= cy - 4) { chargeAt[elem.id] = tick; out += VisualTrackElement("chg_${tick}_${ph - (cy - 4) + 1}_3", HOLD_X, y, "fx", "HZ_CHARGE") }
                        if (ph == 0) {
                            shotAt[elem.id] = tick
                            val land = pred.coerceIn(20, 84)
                            out += VisualTrackElement("bullet_${tick}_hz${n}r_$land", HOLD_X - 0.03f, land, "bullet", "HZ_BOULDER")
                            if (w <= -1 || lv >= 6) out += VisualTrackElement("bullet_${tick}_hz${n}q_${(land + (if (land > 50) -18 else 18))}", HOLD_X - 0.05f, (land + (if (land > 50) -18 else 18)), "bullet", "HZ_BOULDER")
                            if (lv >= 10) { val l3 = (land + (if (land > 50) -36 else 36)).coerceIn(4, 96); out += VisualTrackElement("bullet_${tick}_hz${n}p_$l3", HOLD_X - 0.07f, l3, "bullet", "HZ_BOULDER") }
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
