package com.neonrush.game

import kotlin.math.sin
import kotlin.math.sqrt

/**
 * REALMS (zones 240+): each stretch of 60 zones changes what you DO, not only how fast it is.
 *   MIRROR (240-299): two sides, cyan and magenta. Tap to swap side. Obstacles only touch you on their own side.
 *   GRAVITY (300-359): spinning wells drift in. BLUE wells pull you in (skim the edge for bonus points, never sit in the middle); RED wells push you away.
 *   SHATTER (270-299): glass walls with one clear gap (always at least 24 lanes-units wide); they burst into shards as you pass.
 *   STORM  (330-359): lightning lanes. A warning band shows for 1.1 s, then the bolt strikes that lane. A calm 'eye' of ~5 s comes every ~25 s.
 *   PULSE  (360-389): the world runs on a beat. Obstacles arrive on the beat, and every 4th beat a "drop" of gems appears.
 *   ECHO RIVAL (boss, every 15th zone from 255): your own shadow hunts you, firing along the lanes you were in ~1.7 s ago.
 *   BLACKOUT (420-479): the lights are out. You only see inside your lantern; every 30 ticks (~3.6 s) a sonar ping lights the whole screen for a moment. Faint blips mark what is out there.
 * No new art: everything is code-drawn / tinted (see ui/RealmFx.kt). The finale boss at 480 is not built yet.
 */
enum class Realm { NONE, MIRROR, PULSE, GRAVITY, BLACKOUT, SHATTER, STORM }

object RealmRules {
    const val PULSE_BEAT = 6          // ticks per beat (~0.72 s)
    const val PING_EVERY = 30         // ticks between sonar pings in the Blackout realm
    const val PING_LEN = 7            // ticks a ping lights the screen
    const val ECHO_DELAY = 14         // ticks the rival lags behind you

    fun realmFor(zone: Int): Realm = when (zone) {
        in 240..269 -> Realm.MIRROR
        in 270..299 -> Realm.SHATTER
        in 300..329 -> Realm.GRAVITY
        in 330..359 -> Realm.STORM
        in 360..389 -> Realm.PULSE
        in 420..479 -> Realm.BLACKOUT
        else -> Realm.NONE
    }

    /** Obstacle lane side: 0 = cyan, 1 = magenta. Fixed per obstacle, so the player can read it. */
    fun sideOf(e: VisualTrackElement): Int = (e.id.hashCode() ushr 3) and 1

    /** In the Mirror realm an obstacle only hurts you while you stand on ITS side. Everything else is always live. */
    fun touchable(e: VisualTrackElement, mySide: Int, zone: Int): Boolean =
        realmFor(zone) != Realm.MIRROR || e.type != "obstacle" || sideOf(e) == mySide

    fun spacingMul(zone: Int): Float = when (realmFor(zone)) {
        Realm.MIRROR -> 0.7f
        Realm.SHATTER -> 1.9f   // each obstacle is a whole glass wall
        Realm.STORM -> 1.4f     // the lightning does the work, so fewer plain obstacles
        else -> 1f
    }

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
            val push = n % 3 == 2 || (zone >= 315 && n % 3 == 1 && h % 2 == 0)
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

    // ------------------------------------------------------------------ SHATTER: glass walls

    @Volatile private var lastGap = 50
    private fun hash(n: Int): Float {
        var x = n * 374761393 + 668265263
        x = (x xor (x ushr 13)) * 1274126177
        return ((x xor (x ushr 16)) and 0xFFFF) / 65535f
    }

    /**
     * One glass wall: obstacle tiles every 14 lane-units, except a gap 24 units wide. The gap never moves more than 32 units from the
     * previous one and always stays between lanes 28 and 72, so there is always an exit that can be reached in time.
     * Tile id: gp_<tick>_<gapCentre>_<index>; the draw reads the gap from the id.
     */
    fun glassPane(tick: Int, userY: Int, out: MutableList<VisualTrackElement>) {
        val h = hash(tick)
        val wish = if (h < 0.34f) userY else lastGap + ((hash(tick + 7) - 0.5f) * 64f).toInt()
        val c = wish.coerceIn(lastGap - 32, lastGap + 32).coerceIn(28, 72)
        lastGap = c
        val half = 12
        var i = 0
        var y = c - half - 15
        while (y >= -12) { out += VisualTrackElement("gp_${tick}_${c}_$i", 1.2f, y.coerceAtLeast(0), "obstacle", "GLASS"); i++; y -= 14 }
        y = c + half + 15
        while (y <= 112) { out += VisualTrackElement("gp_${tick}_${c}_$i", 1.2f, y.coerceAtMost(100), "obstacle", "GLASS"); i++; y += 14 }
    }

    // ------------------------------------------------------------------ STORM: lightning lanes

    class Strike(val id: Int, val lane: Int, val t0: Int)

    const val STORM_WARN = 9          // ticks of warning (~1.1 s) before the bolt lands
    @Volatile var strikes: List<Strike> = emptyList()
    @Volatile var calm: Boolean = false
    private var nextStrikeTick = 0
    private var strikeSeq = 0

    /**
     * Plans and fires lightning. The bolt is a real hit for 2 ticks, injected as a short-lived hazard exactly on the pilot's column.
     * Returns (bonus points for a close dodge, 1 when thunder should sound). Outside the Storm realm it clears everything.
     */
    fun stormStep(zone: Int, tick: Int, userY: Int, speedInPx: Float, out: MutableList<VisualTrackElement>): IntArray {
        if (realmFor(zone) != Realm.STORM) {
            if (strikes.isNotEmpty()) strikes = emptyList()
            calm = false; nextStrikeTick = 0
            return intArrayOf(0, 0)
        }
        val cyc = tick % 210
        calm = cyc >= 168                                  // the eye of the storm: ~5 s with no new strikes
        var live = strikes.filter { tick >= it.t0 && tick <= it.t0 + STORM_WARN + 3 }
        if (!calm && tick >= nextStrikeTick) {
            val gap = (20 - (zone - 330) / 4).coerceAtLeast(12)
            var lane = (userY + ((hash(tick * 3 + 1) - 0.5f) * 48f).toInt()).coerceIn(16, 84)
            var ok = true
            for (s in live) if (kotlin.math.abs(s.lane - lane) < 36) {
                lane = if (lane < 50) s.lane + 36 else s.lane - 36
                if (lane < 16 || lane > 84) ok = false
            }
            if (ok && live.size < 2) { live = live + Strike(strikeSeq++, lane, tick); nextStrikeTick = tick + gap }
            else nextStrikeTick = tick + 3
        }
        strikes = live
        var bonus = 0
        var thunder = 0
        val step = speedInPx * 0.018f
        for (s in live) {
            val age = tick - s.t0
            if (age == STORM_WARN || age == STORM_WARN + 1) {
                // x chosen so the hit window is crossed on this tick only
                out += VisualTrackElement("strike_${s.id}_$age", 0.16f - step + 0.004f, s.lane, "bullet", "STRIKE")
                if (age == STORM_WARN) thunder = 1
                if (age == STORM_WARN + 1) {
                    val d = kotlin.math.abs(userY - s.lane)
                    if (d in 16..30) bonus += 40       // a brave dodge
                }
            }
        }
        return intArrayOf(bonus, thunder)
    }
}
