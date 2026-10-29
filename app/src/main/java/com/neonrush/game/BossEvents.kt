package com.neonrush.game

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Boss surprises (Stage 140), shared between the boss-events overlay and the shooting overlay:
 *  - LUNGE (Legendary): the boss charges across the track toward the pilot. While it lunges its shield is down and bolts do double damage.
 *  - BROOD (Legendary 5 babies, Hard 3): the boss births small creatures that hunt the pilot. A basic bolt kills one baby; an ARMOR round
 *    sets off a chain blast that destroys every baby at once.
 */
object BossLunge {
    /** How far the boss body has moved left of its normal spot (0 .. about -0.5), in fractions of the width. */
    var shift by mutableStateOf(0f)
    @Volatile var vulnerable = false
    fun reset() { shift = 0f; vulnerable = false }
}

class Baby(var x: Float, var y: Float, var vx: Float, var vy: Float, var hp: Int = 1, var age: Float = 0f, val seed: Float = 0f)

object BossBrood {
    /** UI-thread only: the overlay's frame loop and the shooting overlay's frame loop both run on the main thread. */
    val babies = ArrayList<Baby>()
    @Volatile var novaRequested = false
    @Volatile var novaX = 0f
    @Volatile var novaY = 0f
    fun reset() { babies.clear(); novaRequested = false }
    fun hasBabies() = babies.isNotEmpty()
}
