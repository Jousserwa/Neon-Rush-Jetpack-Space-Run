package com.neonrush.game

/**
 * Legendary moves: DASH (0.7 s of invulnerability, 8 s cooldown), MAGNET PULSE (pulls every gem and fuel cell on screen to the pilot, 12 s cooldown)
 * and OVERDRIVE (charges from time, kills and boss hits; 6 s of double-speed bolts with no heat and a steady score bonus).
 * Wall-clock based so the sim loops only have to ask [dashing].
 */
object LegendMoves {
    const val DASH_MS = 700L
    const val DASH_CD = 8000L
    const val PULSE_CD = 12000L
    const val OVER_MS = 6000L
    const val OVER_SCORE_PER_SEC = 60

    @Volatile var dashUntil = 0L
    @Volatile var dashReady = 0L
    @Volatile var pulseReady = 0L
    @Volatile var overUntil = 0L
    @Volatile var meter = 0f

    private fun now() = System.currentTimeMillis()
    fun reset() { dashUntil = 0L; dashReady = 0L; pulseReady = 0L; overUntil = 0L; meter = 0f }

    fun dashing() = now() < dashUntil
    fun overdriveOn() = now() < overUntil
    fun dashCd(): Float = ((dashReady - now()).toFloat() / DASH_CD).coerceIn(0f, 1f)      // 1 = just used, 0 = ready
    fun pulseCd(): Float = ((pulseReady - now()).toFloat() / PULSE_CD).coerceIn(0f, 1f)
    fun overLeft(): Float = ((overUntil - now()).toFloat() / OVER_MS).coerceIn(0f, 1f)

    fun charge(x: Float) { if (!overdriveOn()) meter = (meter + x).coerceAtMost(1f) }
    fun dash(): Boolean { if (now() < dashReady) return false; dashUntil = now() + DASH_MS; dashReady = now() + DASH_CD; return true }
    fun pulse(): Boolean { if (now() < pulseReady) return false; pulseReady = now() + PULSE_CD; return true }
    fun overdrive(): Boolean { if (meter < 1f || overdriveOn()) return false; meter = 0f; overUntil = now() + OVER_MS; return true }
}
