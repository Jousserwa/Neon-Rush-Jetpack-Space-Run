package com.neonrush.game

/**
 * Anti-camping. Stay in the top or bottom strip of the screen for too long and the track starts sending
 * obstacles down that strip, so there is no permanent hiding place. They are ordinary obstacles that spawn at
 * the far edge of the screen, so there is always plenty of time to see one coming and move out.
 */
object EdgeGuard {
    private const val TOP_MAX = 24
    private const val BOTTOM_MIN = 76
    private const val CAMP_TICKS = 36      // about 4 s at 120 ms per tick
    private const val REPEAT_TICKS = 28

    private var lastTick = -1
    private var campTicks = 0
    private var sinceSpawn = 0

    fun step(tick: Int, userY: Int, out: MutableList<VisualTrackElement>) {
        if (tick < lastTick) { campTicks = 0; sinceSpawn = 0 }   // a new run started
        lastTick = tick
        val inEdge = userY <= TOP_MAX || userY >= BOTTOM_MIN
        if (!inEdge) { campTicks = 0; sinceSpawn = 0; return }
        campTicks++
        sinceSpawn++
        if (campTicks >= CAMP_TICKS && sinceSpawn >= REPEAT_TICKS) {
            sinceSpawn = 0
            val y = userY.coerceIn(6, 94)
            out.add(VisualTrackElement("obs_${tick}_edge", 1.2f, y, "obstacle", "STANDARD"))
        }
    }
}
