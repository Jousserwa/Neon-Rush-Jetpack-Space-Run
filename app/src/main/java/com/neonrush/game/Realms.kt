package com.neonrush.game

import kotlin.math.sin
import kotlin.math.sqrt

/**
 * REALMS (zones 240+): each stretch of 60 zones changes what you DO, not only how fast it is.
 *   MIRROR (240-299): two sides, cyan and magenta. Tap to swap side. Obstacles only touch you on their own side.
 *   GRAVITY (300-359): spinning wells drift in. BLUE wells pull you in (skim the edge for bonus points, never sit in the middle); RED wells push you away.
 *   PULSE  (360-419): the world runs on a beat. Obstacles arrive on the beat, and every 4th beat a "drop" of gems appears.
 *   ECHO RIVAL (boss, every 15th zone from 255): your own shadow hunts you, firing along the lanes you were in ~1.7 s ago.
 * No new art: everything is code-drawn / tinted (see ui/RealmFx.kt). Blackout (420-479) is not built yet.
 */
enum class Realm { NONE, MIRROR, PULSE, GRAVITY }

object RealmRules {
    const val PULSE_BEAT = 6          // ticks per beat (~0.72 s)
    const val ECHO_DELAY = 14         // ticks the rival lags behind you

    fun realmFor(zone: Int): Realm = when (zone) {
        in 240..299 -> Realm.MIRROR
        in 300..359 -> Realm.GRAVITY
        in 360..419 -> Realm.PULSE
        else -> Realm.NONE
    }

    /** Obstacle lane side: 0 = cyan, 1 = magenta. Fixed per obstacle, so the player can read it. */
    fun sideOf(e: VisualTrackElement): Int = (e.id.hashCode() ushr 3) and 1

    /** In the Mirror realm an obstacle only hurts you while you stand on ITS side. Everything else is always live. */
    fun touchable(e: VisualTrackElement, mySide: Int, zone: Int): Boolean =
        realmFor(zone) != Realm.MIRROR || e.type != "obstacle" || sideOf(e) == mySide

    fun spacingMul(zone: Int): Float = if (realmFor(zone) == Realm.MIRROR) 0.7f else 1f

    /** When to spawn obstacles. Pulse snaps them to the beat (beats 1 and 3 of every bar, and 2 as well deeper in). */
    fun spawnNow(zone: Int, tick: Int, spacing: Int): Boolean {
        if (realmFor(zone) != Realm.PULSE) return tick % spacing == 0
        if (tick % PULSE_BEAT != 0) return false
        val beat = (tick / PULSE_BEAT) % 4
        return beat == 0 || beat == 2 || (zone >= 390 && beat == 1)
    }

    /** Every 4th beat of a bar: a short wave of gems placed near where the pilot is, a reward for staying in the rhythm. */
    fun pulseDrop(zone: Int, tick: Int, userY: Int, out: MutableList<VisualTrackElement>) {
        if (realmFor(zone) != Realm.PULSE) return
        if (tick % PULSE_BEAT != 0 || (tick / PULSE_BEAT) % 4 != 3) return
        for (i in 0 until 5) {
            val y = (userY + sin(i * 1.1f) * 14f).toInt().coerceIn(12, 88)
            out += VisualTrackElement("pg_${tick}_$i", 1.2f + i * 0.07f, y, "gem")
        }
    }

    // ------------------------------------------------------------------ the Echo Rival

    private val hist = IntArray(64) { 50 }
    fun record(tick: Int, userY: Int) { hist[tick and 63] = userY }
    fun echoY(tick: Int): Int = hist[(tick - ECHO_DELAY) and 63].coerceIn(10, 90)

    fun isRivalZone(zone: Int): Boolean = zone in 255..480 && zone % 15 == 0

    fun rivalFire(tick: Int, baseInterval: Int, bossY: Int, out: MutableList<VisualTrackElement>) {
        val iv = (baseInterval * 1.1f).toInt().coerceAtLeast(7)
        val phase = tick % iv
        val chargeTicks = minOf(5, iv - 1)
        if (phase >= iv - chargeTicks) {
            out += VisualTrackElement("chg_${tick}_${phase - (iv - chargeTicks) + 1}", 0.86f, bossY, "fx", "BOSS_CHARGE")
        }
        if (phase == 0) {
            // straight along the lane you WERE in: keep moving and it misses
            out += VisualTrackElement("bullet_${tick}_e", 0.95f, bossY, "bullet", "ECHO")
            BossSfx.fire()
        }
    }

    // ------------------------------------------------------------------ GRAVITY wells

    class Well(val id: Int, val x: Float, val y: Int, val push: Boolean)

    /** Wells currently on screen. Replaced as a whole every tick, so the draw can read it safely from the UI thread. */
    @Volatile var wells: List<Well> = emptyList()
    @Volatile var skimming: Boolean = false
    private const val WELL_R = 36f        // field radius in "lane units"
    private const val PILOT_X = 0.2f

    /**
     * Moves the wells and returns (lane shift for the pilot this tick, bonus points). Outside the Gravity realm it clears everything.
     * Pull wells drag the pilot toward their centre; flying along the rim earns points. Push wells shove the pilot away.
     */
    fun gravityStep(zone: Int, tick: Int, speedInPx: Float, userY: Int): IntArray {
        if (realmFor(zone) != Realm.GRAVITY) {
            if (wells.isNotEmpty()) wells = emptyList()
            skimming = false
            return intArrayOf(0, 0)
        }
        val dx = speedInPx * 0.018f * 0.8f
        val moved = ArrayList<Well>(wells.size + 1)
        for (w in wells) if (w.x - dx > -0.25f) moved += Well(w.id, w.x - dx, w.y, w.push)
        val interval = (26 - (zone - 300) / 8).coerceAtLeast(16)
        if (tick % interval == 0) {
            val n = tick / interval
            val h = (n * 2654435761L ushr 7).toInt() and 0xFFFF
            val y = 22 + (h % 57)
            val push = n % 3 == 2 || (zone >= 330 && n % 3 == 1 && h % 2 == 0)
            moved += Well(n, 1.3f, y, push)
        }
        wells = moved
        var shift = 0f
        var bonus = 0
        var skim = false
        for (w in moved) {
            val ddx = (w.x - PILOT_X) * 55f
            if (ddx > WELL_R || ddx < -WELL_R) continue
            val ddy = (w.y - userY).toFloat()
            val dist = sqrt(ddx * ddx + ddy * ddy)
            if (dist >= WELL_R) continue
            val f = 1f - dist / WELL_R
            val dir = if (ddy > 0f) 1f else if (ddy < 0f) -1f else (if (w.id % 2 == 0) 1f else -1f)
            if (w.push) {
                shift -= dir * 4.5f * f
            } else {
                val pullStep = minOf(4.8f * f, kotlin.math.abs(ddy))
                shift += dir * pullStep
                if (dist in 8f..22f) { bonus += 14; skim = true }
            }
        }
        skimming = skim
        return intArrayOf(shift.coerceIn(-6f, 6f).toInt(), bonus)
    }
}
