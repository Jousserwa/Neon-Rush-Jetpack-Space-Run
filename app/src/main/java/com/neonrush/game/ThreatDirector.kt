package com.neonrush.game

import kotlin.math.sin
import kotlin.random.Random

/**
 * Keeps the run tense and varied, with no new art:
 *  - Pacing: if no threat has appeared for ~2.5 s, one is scheduled (tighter deeper in), and each new zone
 *    gets a bigger "set piece" a moment after it begins.
 *  - Every new threat is telegraphed: a WARN marker + beep first, then it arrives (about 0.6 s later).
 *  - New threat kinds, all drawn in code: FIRE_WALL, LAVA_BALL, MOUNTAIN_*, and SHADOW_* clones of the pilot.
 *  - Boss shots now charge up (glow + whine + aim line) before they fire.
 * Everything uses the existing "obstacle" / "bullet" collision, so hits, shields, close calls and boom-clear all work.
 */
object ThreatDirector {
    private const val WARN_TICKS = 5
    private const val LONG_WARN_TICKS = 8          // fire / lava / mountains get a longer, clearer warning (~1 s)
    private const val BASE_X = 1.2f
    private const val STRAFER_X = 0.72f
    private const val STRAFER_HOLD = 26

    private var lastThreatTick = 0
    private var lastZone = 0
    private var setPieceAt = Int.MAX_VALUE
    private var setPieceDone = true
    private var counter = 0
    private class Pending(val atTick: Int, val elems: List<VisualTrackElement>)
    private val pending = mutableListOf<Pending>()

    fun reset() { lastThreatTick = 0; lastZone = 0; setPieceAt = Int.MAX_VALUE; setPieceDone = true; counter = 0; pending.clear(); BossSfx.init() }

    private fun spawnedAt(id: String, tick: Int): Boolean {
        val parts = id.split("_")
        return parts.size >= 2 && parts[0] in setOf("obs", "bullet", "blink") && parts[1].toIntOrNull() == tick
    }
    private fun idTick(id: String): Int = id.split("_").getOrNull(1)?.toIntOrNull() ?: 0

    // ------------------------------------------------------------------ spawning

    /**
     * Call once per tick after the normal spawn code. [current] = elements already added this tick.
     * [suppress] = a boss fight is running (it brings its own pressure). [intensity] = flavor spacing multiplier.
     */
    fun spawn(
        tick: Int, zone: Int, userY: Int, rand: Random,
        current: List<VisualTrackElement>, suppress: Boolean, intensity: Float, assist: Float, warnDelta: Int = 0
    ): List<VisualTrackElement> {
        val out = mutableListOf<VisualTrackElement>()
        if (current.any { spawnedAt(it.id, tick) }) lastThreatTick = tick

        // threats whose warning time is up
        val iter = pending.iterator()
        while (iter.hasNext()) {
            val p = iter.next()
            if (p.atTick <= tick) { out.addAll(p.elems); lastThreatTick = tick; iter.remove() }
        }
        if (assist > 0.2f || zone < 2 || suppress) { if (suppress) lastThreatTick = tick; return out }

        if (zone != lastZone) { lastZone = zone; setPieceDone = false; setPieceAt = tick + 10 }

        val busy = pending.isNotEmpty()
        if (!busy && !setPieceDone && tick >= setPieceAt) {
            setPieceDone = true
            schedule(tick, zone, userY, rand, out, big = true, warnDelta = warnDelta)
        } else if (!busy) {
            // 2.6 s at the start, shrinking to ~1.4 s by zone 40, tighter again with intensity
            val maxGap = (((22 - (zone / 4).coerceAtMost(8)) * 1.5f) / intensity.coerceAtLeast(0.5f)).toInt().coerceIn(14, 56)
            if (tick - lastThreatTick >= maxGap) schedule(tick, zone, userY, rand, out, big = false, warnDelta = warnDelta)
        }
        return out
    }

    private fun schedule(tick: Int, zone: Int, userY: Int, rand: Random, out: MutableList<VisualTrackElement>, big: Boolean, warnDelta: Int = 0) {
        val kinds = mutableListOf("fire", "lava", "mountain")
        if (zone >= 4) kinds += "mirror"
        if (zone >= 6) kinds += "charger"
        if (big && zone >= 10) kinds += "swarm"
        if (zone >= 14) kinds += listOf("strafer")
        if (big && zone >= 22) kinds += "ridges"
        val kind = kinds[rand.nextInt(kinds.size)]
        val n = ++counter
        val id = "thr_${tick}_$n"
        val elems = mutableListOf<VisualTrackElement>()
        var warnY = userY
        var delay = WARN_TICKS + warnDelta
        var tag = kind
        when (kind) {
            "fire" -> {
                // Arc pylon wall. Each pylon is about 26 units tall, so the usable opening is (halfGap - 13) either side
                // of the centre. 38 gives a clear ~25 unit lane on MEDIUM (about the pilot's height twice over), wider on
                // EASY, and the lane is centred close to where the pilot is. Longer warning than the other threats.
                delay = LONG_WARN_TICKS + 3 + warnDelta
                val halfGap = 38 + 3 * warnDelta.coerceIn(-2, 3)
                val gc = (userY + rand.nextInt(-8, 9)).coerceIn(32, 68); warnY = gc
                listOf(-(halfGap + 14), -halfGap, halfGap, halfGap + 14).map { gc + it }.filter { it in 6..94 }.forEachIndexed { i, y ->
                    elems += VisualTrackElement("obs_${tick + delay}_f${n}_$i", BASE_X, y, "obstacle", "FIRE_WALL")
                }
            }
            "lava" -> {
                delay = LONG_WARN_TICKS + warnDelta
                val base = (userY + rand.nextInt(-12, 13)).coerceIn(28, 72); warnY = base
                val count = if (zone >= 12) 3 else 2
                for (i in 0 until count) {
                    val phase = (i * 2.1f * 100).toInt()
                    elems += VisualTrackElement("obs_${tick + delay}_lav${n}_${i}_${base}_$phase", BASE_X + i * 0.2f, base, "obstacle", "LAVA_BALL")
                }
            }
            "mountain" -> {
                delay = LONG_WARN_TICKS + warnDelta
                val bottom = rand.nextBoolean()
                tag = if (bottom) "mtnb" else "mtnt"
                if (bottom) { val p = rand.nextInt(62, 75); warnY = p
                    listOf(p, p + 14, p + 28).filter { it <= 96 }.forEachIndexed { i, y -> elems += VisualTrackElement("obs_${tick + delay}_mb${n}_$i", BASE_X, y, "obstacle", "MOUNTAIN_BOTTOM") }
                } else { val q = rand.nextInt(26, 39); warnY = q
                    listOf(q, q - 14, q - 28).filter { it >= 4 }.forEachIndexed { i, y -> elems += VisualTrackElement("obs_${tick + delay}_mt${n}_$i", BASE_X, y, "obstacle", "MOUNTAIN_TOP") }
                }
            }
            "ridges" -> {
                delay = LONG_WARN_TICKS + warnDelta
                warnY = 50
                listOf(72, 86).forEachIndexed { i, y -> elems += VisualTrackElement("obs_${tick + delay}_rb${n}_$i", BASE_X, y, "obstacle", "MOUNTAIN_BOTTOM") }
                listOf(28, 14).forEachIndexed { i, y -> elems += VisualTrackElement("obs_${tick + delay}_rt${n}_$i", BASE_X, y, "obstacle", "MOUNTAIN_TOP") }
            }
            "mirror" -> {
                warnY = userY
                elems += VisualTrackElement("obs_${tick + delay}_mir$n", BASE_X, userY, "obstacle", "SHADOW_MIRROR")
            }
            "charger" -> {
                warnY = userY; delay = WARN_TICKS + 1 + warnDelta
                elems += VisualTrackElement("obs_${tick + delay}_chg$n", 0.88f, userY, "obstacle", "SHADOW_CHARGER")
            }
            "swarm" -> {
                warnY = 50
                for (i in 0 until 3) {
                    val phase = (i * 2.1f * 100).toInt()
                    elems += VisualTrackElement("obs_${tick + delay}_swm${n}_${i}_50_$phase", BASE_X + i * 0.14f, 50, "obstacle", "SHADOW_SWARM")
                }
            }
            "strafer" -> {
                warnY = userY
                elems += VisualTrackElement("obs_${tick + delay}_str$n", BASE_X, userY, "obstacle", "SHADOW_STRAFER")
            }
        }
        out += VisualTrackElement("wrn_${tick}_${n}_${tag}_$delay", 0.96f, warnY, "fx", "WARN")
        BossSfx.warn()
        if (kind == "mirror" || kind == "charger" || kind == "swarm" || kind == "strafer") BossSfx.whoosh()
        pending += Pending(tick + delay, elems)
    }

    // ------------------------------------------------------------------ per-tick motion

    /**
     * Special motion for the new kinds. [nextX]/[actualY] are the normal scroll result for this tick.
     * Returns the final (x, y). May append bullets to [out].
     */
    fun advance(elem: VisualTrackElement, nextX: Float, actualY: Int, userY: Int, tick: Int, out: MutableList<VisualTrackElement>): Pair<Float, Int> {
        when (elem.type) {
            "fx" -> {
                val age = tick - idTick(elem.id)
                val dead = when (elem.subType) { "WARN" -> age > (elem.id.split("_").getOrNull(4)?.toIntOrNull() ?: WARN_TICKS); "BOSS_CHARGE" -> age >= 1; else -> false }
                return (if (dead) -0.5f else elem.xOffsetFraction) to elem.yMatchPos
            }
        }
        if (elem.type == "bullet") {
            val eliteLane = EliteBosses.shotLane(elem.subType, nextX, actualY, userY)
            if (eliteLane != null) return nextX to eliteLane
        }
        // Boss shots fly TOWARD the pilot: they steer in the first part of the flight, then lock their lane so they can still be dodged.
        if (elem.type == "bullet" && elem.subType == "") {
            if (nextX > 0.42f) {
                val d = userY - actualY
                val step = if (d == 0) 0 else (d * 0.28f).toInt().coerceIn(-4, 4).let { if (it == 0) (if (d > 0) 1 else -1) else it }
                return nextX to (actualY + step).coerceIn(4, 96)
            }
            return nextX to actualY
        }
        val parts = elem.id.split("_")
        when (elem.subType) {
            "LAVA_BALL", "SHADOW_SWARM" -> {
                // id: obs_<spawn>_<tag>_<i>_<baseY>_<phase100>
                val base = parts.getOrNull(parts.size - 2)?.toIntOrNull() ?: actualY
                val phase = (parts.lastOrNull()?.toIntOrNull() ?: 0) / 100f
                val spawnT = idTick(elem.id)
                val amp = if (elem.subType == "LAVA_BALL") 20f else 24f
                val spd = if (elem.subType == "LAVA_BALL") 0.45f else 0.35f
                return nextX to (base + (sin((tick - spawnT) * spd + phase) * amp).toInt()).coerceIn(8, 92)
            }
            "SHADOW_MIRROR" -> {
                // homes on your lane until it is close, then locks so you can dodge at the last moment
                val y = if (nextX > 0.42f) actualY + ((userY - actualY) * 0.16f).toInt() else actualY
                return nextX to y
            }
            "SHADOW_STRAFER" -> {
                val age = tick - idTick(elem.id)
                val holding = age < STRAFER_HOLD
                if (holding && nextX <= STRAFER_X) {
                    val y = actualY + ((userY - actualY) * 0.10f).toInt()
                    if (age % 8 == 6) {
                        out += VisualTrackElement("bullet_${tick}_sh${elem.id.hashCode() and 0xff}", STRAFER_X - 0.03f, y, "bullet", "SHADOW_SHOT")
                        BossSfx.zap()
                    }
                    return STRAFER_X to y
                }
                return (if (holding) maxOf(nextX, STRAFER_X) else nextX) to actualY
            }
        }
        return nextX to actualY
    }

    // ------------------------------------------------------------------ boss shots

    /**
     * Replaces "fire every N ticks": the last few ticks before each shot show a charge glow (+ whine),
     * then the bullet leaves the boss. [interval] already includes upgrades and flavor aggression.
     */
    fun bossFire(tick: Int, interval: Int, bossY: Int, rand: Random, out: MutableList<VisualTrackElement>) {
        val iv = interval.coerceAtLeast(2)
        val chargeTicks = minOf(5, iv - 1)
        val phase = tick % iv
        if (phase >= iv - chargeTicks) {
            val k = phase - (iv - chargeTicks) + 1
            out += VisualTrackElement("chg_${tick}_$k", 0.86f, bossY, "fx", "BOSS_CHARGE")
            if (k == 1) BossSfx.charge()
        }
        if (phase == 0) {
            out += VisualTrackElement("bullet_${tick}", 0.95f, bossY + rand.nextInt(-6, 7), "bullet")
            BossSfx.fire()
        }
    }
}
