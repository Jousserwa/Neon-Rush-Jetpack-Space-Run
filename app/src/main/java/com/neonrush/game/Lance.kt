package com.neonrush.game

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * PULSE LANCE (Stage 157). A collectible point-blank weapon: LANCE CELLS drift in during boss fights, you collect one by flying into it, and the LANCE button
 * appears ONLY while the boss (or a hunter escort) is charging at the pilot - from the start of the charge until it is back in its lane. Fire it and a beam of
 * pulse energy hits the boss for a percentage of its hull that depends on how close it is: POINT BLANK x1.5, CLOSE x1.0, LONG x0.5.
 * Strength by Mk (hull fraction at CLOSE): base 6%, Mk II 8%, Mk III 11%, Mk IV 15%, Mk V 20% (see Upgrades.LANCE).
 * Storage: you carry 1 cell; real money adds slots (Upgrades.STORE: +1, +2, +3 = up to 4 cells).
 */
object Lance {
    private val PCT = floatArrayOf(0.06f, 0.08f, 0.11f, 0.15f, 0.20f)
    var version by mutableStateOf(0)
    @Volatile var MAX = 1
    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences("neonrush_lance", Context.MODE_PRIVATE)
    fun max(ctx: Context): Int { MAX = 1 + Upgrades.level(ctx, Upgrades.STORE); return MAX }
    fun count(ctx: Context): Int { @Suppress("UNUSED_VARIABLE") val v = version; return try { prefs(ctx).getInt("cells", 0) } catch (_: Throwable) { 0 }.coerceAtMost(max(ctx)) }
    fun add(ctx: Context): Boolean {
        val n = count(ctx); if (n >= max(ctx)) return false
        try { prefs(ctx).edit().putInt("cells", n + 1).apply() } catch (_: Throwable) { }
        version += 1; return true
    }
    fun use(ctx: Context): Boolean {
        val n = count(ctx); if (n <= 0) return false
        try { prefs(ctx).edit().putInt("cells", n - 1).apply() } catch (_: Throwable) { }
        version += 1; return true
    }
    /** Range band from the horizontal gap between boss and pilot (fraction of the width): 0 point blank, 1 close, 2 long. */
    fun band(gap: Float): Int = if (gap < 0.10f) 0 else if (gap < 0.24f) 1 else 2
    val BAND_NAMES = arrayOf("POINT BLANK", "CLOSE", "LONG RANGE")
    private val BAND_MUL = floatArrayOf(1.5f, 1f, 0.5f)
    /** Hull fraction this shot takes, before round scaling. */
    fun fraction(level: Int, band: Int): Float = PCT[level.coerceIn(0, 4)] * BAND_MUL[band.coerceIn(0, 2)]
}
