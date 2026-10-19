package com.neonrush.game

import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * ELITE BOSSES: zones 60 to 240 (every 5th zone). They reuse the art of the eight world bosses, but each one is a new creature:
 * its own colour grade, size, aura, way of moving and way of shooting. The roster of 12 repeats three more times, each lap with a new
 * colour, a hotter fire rate and bigger volleys (tier 0..3), so zone 240 never feels like zone 60 again.
 *
 * Logic lives here (movement and shots); the drawing is in ui/EliteBossFx.kt.
 */
enum class EliteMove { CHASE, SWEEP, ORBIT, STRAFE, DASH, HOP }
enum class EliteAtk { FAN, SALVO, SNIPER, CURTAIN, MIX }

class EliteBoss(
    val index: Int,
    val name: String,
    val base: Int,            // which world boss art (1..8)
    val hue: Float,           // colour rotation in degrees
    val bright: Float,
    val scale: Float,
    val move: EliteMove,
    val atk: EliteAtk,
    val rate: Float,          // > 1 = fires less often
    val twin: Boolean,        // two bodies, top and bottom, firing in turns
    val fx: Int,              // aura style, see EliteBossFx
    val accent: Long,
    val tier: Int             // 0..3, which lap of the roster
)

object EliteBosses {
    const val FIRST_ZONE = 60
    const val LAST_ZONE = 240

    private class Def(val name: String, val base: Int, val hue: Float, val bright: Float, val scale: Float, val move: EliteMove,
                       val atk: EliteAtk, val rate: Float, val twin: Boolean, val fx: Int, val accent: Long)

    private val ROSTER = listOf(
        Def("GLACIAL REVENANT", 7, 35f, 1.05f, 1.0f, EliteMove.SWEEP, EliteAtk.FAN, 1.0f, false, 0, 0xFF7EE7FF),
        Def("SOLAR PHANTOM", 8, -25f, 1.1f, 1.0f, EliteMove.DASH, EliteAtk.SNIPER, 1.1f, false, 1, 0xFFFFB300),
        Def("VENOM COLOSSUS", 4, 85f, 1.0f, 1.1f, EliteMove.ORBIT, EliteAtk.SALVO, 1.0f, false, 2, 0xFF00E5FF),
        Def("NULL SPECTER", 6, 110f, 1.05f, 1.0f, EliteMove.HOP, EliteAtk.CURTAIN, 1.25f, false, 3, 0xFF69F0AE),
        Def("WARDEN PRIME", 3, -40f, 1.0f, 1.2f, EliteMove.CHASE, EliteAtk.FAN, 1.15f, false, 4, 0xFFFF7043),
        Def("CRIMSON VANGUARD", 5, 175f, 1.05f, 1.0f, EliteMove.STRAFE, EliteAtk.SALVO, 1.0f, false, 5, 0xFF40C4FF),
        Def("IRON SKYHAWK", 2, 200f, 1.0f, 1.0f, EliteMove.SWEEP, EliteAtk.MIX, 1.1f, false, 5, 0xFFB388FF),
        Def("ECLIPSE WRAITH", 1, 90f, 0.95f, 1.0f, EliteMove.ORBIT, EliteAtk.SNIPER, 1.0f, false, 6, 0xFFB388FF),
        Def("TWIN FROST", 7, -45f, 1.0f, 0.85f, EliteMove.SWEEP, EliteAtk.FAN, 1.2f, true, 0, 0xFFFF80AB),
        Def("TWIN BLOOM", 4, -60f, 1.0f, 0.85f, EliteMove.ORBIT, EliteAtk.SALVO, 1.2f, true, 2, 0xFFFFEA00),
        Def("APEX OVERLORD", 8, 150f, 1.1f, 1.3f, EliteMove.CHASE, EliteAtk.MIX, 1.3f, false, 7, 0xFFFFD740),
        Def("OMEGA SIGNAL", 6, -80f, 1.05f, 1.1f, EliteMove.HOP, EliteAtk.MIX, 1.3f, false, 3, 0xFFFF4081)
    )

    /** The elite for this zone, or null when it is not an elite boss zone (the normal world bosses handle those). */
    fun forZone(zone: Int): EliteBoss? {
        if (zone < FIRST_ZONE || zone > LAST_ZONE || zone % 5 != 0) return null
        val n = (zone - FIRST_ZONE) / 5
        val d = ROSTER[n % ROSTER.size]
        val tier = (n / ROSTER.size).coerceIn(0, 3)
        return EliteBoss(n % ROSTER.size, d.name, d.base, d.hue + tier * 30f, d.bright, d.scale + 0.03f * tier, d.move, d.atk,
            d.rate, d.twin, d.fx, d.accent, tier)
    }

    // ------------------------------------------------------------------------------------- world bosses (zones 5-55, 245+)

    private val MOVES = arrayOf(EliteMove.CHASE, EliteMove.SWEEP, EliteMove.ORBIT, EliteMove.STRAFE, EliteMove.DASH, EliteMove.HOP)
    private val ATKS = arrayOf(EliteAtk.FAN, EliteAtk.SALVO, EliteAtk.SNIPER, EliteAtk.CURTAIN, EliteAtk.MIX)
    private val ACCENTS = longArrayOf(0xFF00E5FF, 0xFFFF7043, 0xFFB388FF, 0xFF69F0AE, 0xFFFFD740, 0xFFFF4081, 0xFF40C4FF, 0xFFFFB300)

    /**
     * Behaviour for the normal world bosses, so no two bosses in a row move or shoot the same way. The drawing stays the world's own
     * boss art (this only drives movement and shots). The move steps through 6 styles and the attack through 5, so boss k and boss k+1
     * always differ in BOTH; each world also starts at a different point of the cycle. The very first boss of World 1 stays the gentle original.
     */
    fun forWorld(family: Int, zone: Int): EliteBoss? {
        if (zone % 5 != 0 || zone <= 0) return null
        if (zone in FIRST_ZONE..LAST_ZONE) return null
        val f = ((family - 1).coerceAtLeast(0)) % 8 + 1
        val k = zone / 5 - 1
        if (zone < FIRST_ZONE && f == 1 && zone <= 10) return null
        val move = MOVES[(f * 2 + k) % MOVES.size]
        val atk = ATKS[(f + k * 2) % ATKS.size]
        val late = zone > LAST_ZONE
        val rate = if (late) 1.0f else if (zone < 20) 1.25f else if (zone < 40) 1.12f else 1.0f
        val twin = f >= 6 && (k % 3 == 2)
        return EliteBoss(k, "WORLD BOSS", f, 0f, 1f, 1f, move, atk, rate, twin, 0, ACCENTS[f - 1], if (late) 2 else 0)
    }

    // ---------------------------------------------------------------------------------------------- movement

    /** The boss's lane (0..100) for this tick. Same function drives the draw, so what you see is what shoots. */
    fun moveY(e: EliteBoss, tick: Int, cur: Int, userY: Int): Int {
        val y = when (e.move) {
            EliteMove.CHASE -> cur + ((userY - cur) * 0.10f).roundToInt().coerceIn(-3, 3)
            EliteMove.SWEEP -> cur + ((50f + 36f * sin(tick * 0.10f)).toInt() - cur).coerceIn(-6, 6)
            EliteMove.ORBIT -> cur + ((50f + 30f * sin(tick * 0.08f)).toInt() - cur).coerceIn(-5, 5)
            EliteMove.STRAFE -> cur + ((if ((tick / 14) % 2 == 0) 24 else 76) - cur).coerceIn(-7, 7)
            EliteMove.DASH -> {
                val ph = tick % 40
                if (ph < 24) cur + ((userY - cur) * 0.08f).roundToInt().coerceIn(-2, 2) else cur
            }
            EliteMove.HOP -> {
                if (tick % 30 == 0) 16 + (hash(tick / 30) * 68f).toInt() else cur
            }
        }
        return y.coerceIn(10, 90)
    }

    /** DASH pose 0..1: 0 = home, rising while it winds up, 1 = fully lunged. Used by the draw only. */
    fun dashLunge(tick: Int): Float {
        val ph = tick % 40
        return when {
            ph < 24 -> 0f
            ph < 30 -> -(ph - 23) / 6f * 0.35f          // wind-up: pulls back a little
            ph < 34 -> -0.35f + (ph - 29) / 4f * 1.35f  // lunge: 1 = fully forward
            else -> (1f - (ph - 33) / 6f).coerceIn(0f, 1f)
        }
    }

    private fun hash(n: Int): Float {
        var x = n * 374761393 + 668265263
        x = (x xor (x ushr 13)) * 1274126177
        return ((x xor (x ushr 16)) and 0xFFFF) / 65535f
    }

    // ---------------------------------------------------------------------------------------------- shooting

    /** One boss shot cycle. [baseInterval] already carries upgrades, difficulty and flavour. */
    fun fire(e: EliteBoss, tick: Int, baseInterval: Int, bossY: Int, userY: Int, rand: Random, out: MutableList<VisualTrackElement>) {
        val tier = e.tier
        val iv = (baseInterval * e.rate * (1f - 0.06f * tier)).toInt().coerceAtLeast(7)
        val phase = tick % iv
        val shot = tick / iv
        fun atkOf(n: Int): EliteAtk = if (e.atk == EliteAtk.MIX) listOf(EliteAtk.FAN, EliteAtk.SALVO, EliteAtk.SNIPER, EliteAtk.CURTAIN)[n % 4] else e.atk
        fun srcOf(n: Int): Int = if (e.twin && n % 2 == 1) 100 - bossY else bossY

        // charge glow before every volley (longer for the sniper)
        val nextShot = shot + 1
        val nextAtk = atkOf(nextShot)
        val chargeTicks = minOf(if (nextAtk == EliteAtk.SNIPER) 8 else 5, iv - 1)
        if (phase >= iv - chargeTicks) {
            val k = phase - (iv - chargeTicks) + 1
            out += VisualTrackElement("chg_${tick}_$k", 0.86f, srcOf(nextShot), "fx", "BOSS_CHARGE")
            if (k == 1) BossSfx.charge()
        }

        val atk = atkOf(shot)
        val src = srcOf(shot)
        when (atk) {
            EliteAtk.FAN -> if (phase == 0) {
                val n = 3 + tier.coerceAtMost(2)
                val aim = ((userY - src) / 12f).roundToInt().coerceIn(-2, 2)
                for (i in 0 until n) {
                    val slope = aim + (i - (n - 1) / 2)
                    out += VisualTrackElement("bullet_${tick}_f$i", 0.95f, src, "bullet", "FAN$slope")
                }
                BossSfx.fire()
            }
            EliteAtk.SALVO -> {
                val count = 2 + tier.coerceAtMost(2)
                if (phase in 0 until count) {
                    out += VisualTrackElement("bullet_${tick}_s", 0.95f, (src + rand.nextInt(-5, 6)).coerceIn(6, 94), "bullet")
                    if (phase == 0) BossSfx.fire()
                }
            }
            EliteAtk.SNIPER -> if (phase == 0) {
                out += VisualTrackElement("bullet_${tick}_n", 0.95f, src, "bullet", "SNIPE")
                if (tier >= 2) out += VisualTrackElement("bullet_${tick}_n2", 1.02f, (src + 8).coerceIn(6, 94), "bullet", "SNIPE")
                BossSfx.fire()
            }
            EliteAtk.CURTAIN -> if (phase == 0) {
                val lanes = 6
                var gap = ((userY - 12) / 14f).roundToInt().coerceIn(0, lanes - 1)
                gap = (gap + rand.nextInt(-1, 2)).coerceIn(0, lanes - 1)
                val gap2 = if (tier < 2) (if (gap + 1 < lanes) gap + 1 else gap - 1) else -1
                for (i in 0 until lanes) {
                    if (i == gap || i == gap2) continue
                    out += VisualTrackElement("bullet_${tick}_c$i", 0.95f, 12 + 14 * i, "bullet", "CURT")
                }
                BossSfx.fire()
            }
            EliteAtk.MIX -> {}
        }
    }

    /** Extra motion for the special shots. Called from ThreatDirector.advance. Returns the new lane, or null if [sub] is not one of ours. */
    fun shotLane(sub: String, nextX: Float, actualY: Int, userY: Int): Int? {
        if (sub.startsWith("FAN")) {
            val slope = sub.substring(3).toIntOrNull() ?: 0
            return (actualY + slope).coerceIn(4, 96)
        }
        if (sub == "SNIPE") {
            if (nextX <= 0.5f) return actualY
            val d = userY - actualY
            val step = (d * 0.5f).toInt().coerceIn(-7, 7).let { if (it == 0 && d != 0) (if (d > 0) 1 else -1) else it }
            return (actualY + step).coerceIn(4, 96)
        }
        if (sub == "CURT") return actualY
        return null
    }
}
