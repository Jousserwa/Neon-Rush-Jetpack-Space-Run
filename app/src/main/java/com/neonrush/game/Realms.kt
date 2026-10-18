package com.neonrush.game

import kotlin.math.sin

/**
 * REALMS (zones 240+): each stretch of 60 zones changes what you DO, not only how fast it is.
 *   MIRROR (240-299): two sides, cyan and magenta. Tap to swap side. Obstacles only touch you on their own side.
 *   PULSE  (360-419): the world runs on a beat. Obstacles arrive on the beat, and every 4th beat a "drop" of gems appears.
 *   ECHO RIVAL (boss, every 15th zone from 255): your own shadow hunts you, firing along the lanes you were in ~1.7 s ago.
 * No new art: everything is code-drawn / tinted (see ui/RealmFx.kt). Gravity (300-359) and Blackout (420-479) are not built yet.
 */
enum class Realm { NONE, MIRROR, PULSE }

object RealmRules {
    const val PULSE_BEAT = 6          // ticks per beat (~0.72 s)
    const val ECHO_DELAY = 14         // ticks the rival lags behind you

    fun realmFor(zone: Int): Realm = when (zone) {
        in 240..299 -> Realm.MIRROR
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
}
