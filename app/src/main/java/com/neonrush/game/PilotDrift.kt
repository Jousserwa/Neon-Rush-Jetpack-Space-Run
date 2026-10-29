package com.neonrush.game

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Pilot forward / back drift: dragging sideways slides the pilot toward the obstacles (forward) or away from them (back), on top of the usual
 * up/down steering. Forward = less reaction time but a steady score bonus; back = more time, no bonus. Collision windows move with the pilot.
 */
object PilotDrift {
    const val BASE = 0.2f
    const val MIN = -0.08f
    const val MAX = 0.18f
    var dx by mutableStateOf(0f)
    val x: Float get() = BASE + dx

    fun nudge(d: Float) { dx = (dx + d).coerceIn(MIN, MAX) }
    fun reset() { dx = 0f }

    /** Score per second for flying forward (0 when not far enough forward). */
    fun bonusPerSec(): Int = if (dx > 0.05f) ((dx - 0.05f) * 300f).toInt() + 8 else 0
}
