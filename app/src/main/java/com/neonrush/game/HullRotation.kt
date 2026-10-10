package com.neonrush.game

import kotlin.random.Random

/**
 * Sector rotation. Pure logic (no Android) so it is easy to unit test.
 *  - lockedId != null  -> always that hull
 *  - otherwise weighted random over rotation ids: the last-picked hull weighs LAST_PICKED_WEIGHT (2x),
 *    never the same hull twice in a row when there is more than one candidate.
 */
object HullRotation {
    const val LAST_PICKED_WEIGHT = 2.0

    fun pickNext(
        rotation: List<String>,
        current: String?,
        lastPicked: String?,
        lockedId: String?,
        rng: Random = Random.Default
    ): String? {
        if (lockedId != null) return lockedId
        val pool = rotation.distinct()
        if (pool.isEmpty()) return null
        if (pool.size == 1) return pool[0]
        val candidates = pool.filter { it != current }.ifEmpty { pool }
        val weights = candidates.map { if (it == lastPicked) LAST_PICKED_WEIGHT else 1.0 }
        var roll = rng.nextDouble() * weights.sum()
        for (i in candidates.indices) {
            roll -= weights[i]
            if (roll <= 0) return candidates[i]
        }
        return candidates.last()
    }

    /** For the "Next:" chip we pre-roll the next hull so what the chip shows is what happens. */
    fun preRoll(rotation: List<String>, current: String?, lastPicked: String?, lockedId: String?) =
        pickNext(rotation, current, lastPicked, lockedId)
}
