package com.neonrush.game

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Player-facing switches for the near-miss feel (vibration lives in NeonSoundEngine.setHapticsEnabled). */
object NearMissFx {
    var shakeOn: Boolean by mutableStateOf(true)
    var flashOn: Boolean by mutableStateOf(true)
}

/**
 * The near-miss loop. A GRAZE (slipped past with a hair of margin) and a RAZOR (a sliver of margin) feed a combo
 * chain: dodges within about 3 seconds of each other raise the multiplier from x1 to x5; a hit or 3 quiet seconds
 * resets it. Reaching x5 opens a short OVERDRIVE that trickles bonus score. Bonuses are flat score, never speed.
 * Anti-farm: one bonus per obstacle (the caller's grazed set) and no more than one bonus per [MIN_GAP] ticks.
 */
class NearMissDirector {
    class Result(val tier: Int, val combo: Int, val points: Int, val label: String)

    companion object {
        const val GRAZE_PTS = 10
        const val RAZOR_PTS = 25
        const val WINDOW = 25            // ticks (~3 s) to keep the chain alive
        const val MIN_GAP = 3            // ticks between two paid near-misses
        const val MAX_COMBO = 5
        const val OVERDRIVE_TICKS = 30
        const val OVERDRIVE_TICK_PTS = 3
        const val OVERDRIVE_NEAR_MULT = 2
    }

    var combo = 0; private set
    var overdriveUntil = -1; private set
    private var lastTick = -999
    private var lastPaidTick = -999

    fun reset() { combo = 0; overdriveUntil = -1; lastTick = -999; lastPaidTick = -999 }

    /** The chain as it stands at [tick] (decays when nothing has happened for [WINDOW] ticks). */
    fun comboNow(tick: Int): Int = if (combo > 0 && tick - lastTick > WINDOW) 0 else combo

    fun overdriveActive(tick: Int) = tick < overdriveUntil
    fun overdriveTickPoints(tick: Int) = if (overdriveActive(tick)) OVERDRIVE_TICK_PTS else 0

    fun onHit() { combo = 0; overdriveUntil = -1 }

    /** Returns null when the near-miss is not paid (anti-farm gap): the caller then plays the old plain feedback. */
    fun register(tick: Int, razor: Boolean): Result? {
        if (tick - lastPaidTick < MIN_GAP) return null
        if (comboNow(tick) == 0) combo = 0
        combo = (combo + 1).coerceAtMost(MAX_COMBO + 20)   // keeps counting for the HUD; the multiplier is capped
        lastTick = tick; lastPaidTick = tick
        val mult = combo.coerceAtMost(MAX_COMBO)
        if (combo == MAX_COMBO && !overdriveActive(tick)) overdriveUntil = tick + OVERDRIVE_TICKS
        var pts = (if (razor) RAZOR_PTS else GRAZE_PTS) * mult
        if (overdriveActive(tick)) pts *= OVERDRIVE_NEAR_MULT
        val tier = if (razor) 2 else 1
        val label = (if (razor) "RAZOR!" else "CLOSE CALL!") + (if (mult >= 2) " x$mult" else "")
        return Result(tier, mult, pts, label)
    }
}
