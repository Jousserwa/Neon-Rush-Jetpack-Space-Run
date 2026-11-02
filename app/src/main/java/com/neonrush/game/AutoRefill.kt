package com.neonrush.game

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * AUTO-REFILL (Stage 165), Pro only. When it is on and the fuel falls to [TRIGGER]% the game presses the refuel button for you:
 * the normal refuel (gems taken from your balance, the same refill sound, EASY shield bonus) plus a "AUTO-REFILL -N gems" banner.
 * Rules: Pro only (free players keep the manual button and their 5 refuels per run) · it needs [MIN_GEMS] gems or more to work (below that it pauses and the
 * manual button still works) · it never revives: a revive after a boss or a crash is always your own tap.
 */
object AutoRefill {
    const val MIN_GEMS = 300
    const val TRIGGER = 20          // refuel when fuel is at or below this percent
    var version by mutableStateOf(0)
    @Volatile private var cached = false
    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences("neonrush_autorefill", Context.MODE_PRIVATE)

    /** The saved switch. Also refreshes the value the sim reads. */
    fun enabled(ctx: Context): Boolean {
        @Suppress("UNUSED_VARIABLE") val v = version
        val on = try { prefs(ctx).getBoolean("on", false) } catch (_: Throwable) { false }
        cached = on
        return on
    }
    fun set(ctx: Context, on: Boolean) {
        try { prefs(ctx).edit().putBoolean("on", on).apply() } catch (_: Throwable) { }
        cached = on; version += 1
    }
    /** For the sim loop (no context needed). */
    fun isOn(): Boolean = cached
    /** The switch may be turned on only by Pro players holding at least [MIN_GEMS] gems. */
    fun canEnable(isPro: Boolean, gems: Int): Boolean = isPro && gems >= MIN_GEMS
}
