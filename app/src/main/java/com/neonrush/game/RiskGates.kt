package com.neonrush.game

/**
 * RISK GATES: every few hundred metres a gate crosses the track with two openings. The wide SAFE corridor pays a little;
 * the narrow, moving RISKY corridor pays more. Miss both and nothing happens (no punishment, just a missed chance).
 * Gems are counted during the run and paid once when the run ends. Capped per run so it never breaks the economy.
 */
object RiskGates {
    const val MAX_RUN = 15
    const val SAFE_GEMS = 1
    const val RISK_GEMS = 3
    const val RISK_GEMS_PRO = 4
    @Volatile private var runGems = 0

    fun reset() { runGems = 0 }
    fun runTotal(): Int = runGems
    /** Adds gems for a gate; returns the amount actually added (respects the per-run cap). */
    fun add(g: Int): Int { val a = g.coerceAtMost(MAX_RUN - runGems).coerceAtLeast(0); runGems += a; return a }
    /** Takes what was earned this run (called once at the end of the run). */
    fun take(): Int { val g = runGems; runGems = 0; return g }
}
