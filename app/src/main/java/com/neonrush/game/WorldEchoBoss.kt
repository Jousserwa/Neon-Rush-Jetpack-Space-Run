package com.neonrush.game

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * WORLD ECHO BOSS: every week one world's boss "echoes" back with a twist that changes how every boss fight plays (Medium and up).
 * Defeat any boss that week to clear it once: +8 gems (Pro +12) and one Fusion core. Resets with the new week.
 * Twists: 0 IRON (shield stays up longer), 1 STORM (fires faster), 2 SOLAR (always shoots back), 3 GLASS (fragile but furious).
 */
object WorldEchoBoss {
    const val GEMS = 8
    const val GEMS_PRO = 12
    private const val WEEK_MS = 604_800_000L
    private val NAMES = listOf("IRON ECHO", "STORM ECHO", "SOLAR ECHO", "GLASS ECHO")
    private val TIPS = listOf(
        "Its shield stays up longer. Wait for the flicker, then fire.",
        "It fires much faster. Stay in motion.",
        "It shoots back constantly. Weave while you fire.",
        "It melts fast but fires like mad. Survive the storm."
    )
    private val ICONS = listOf("🛡", "⛈", "☀", "💎")
    private val COLORS = listOf(0xFF90A4AEL, 0xFF7C4DFFL, 0xFFFFB300L, 0xFF40C4FFL)

    var justCleared by mutableStateOf(0)   // gems of the last clear, for the banner (0 = none)
    var version by mutableStateOf(0)

    fun week(): Int = (System.currentTimeMillis() / WEEK_MS).toInt()
    fun twist(): Int = week() % 4
    fun twistName(): String = NAMES[twist()]
    fun tip(): String = TIPS[twist()]
    fun icon(): String = ICONS[twist()]
    fun color(): Long = COLORS[twist()]
    fun worldTitle(): String = try { Worlds.ALL[week() % Worlds.ALL.size].title } catch (_: Throwable) { "Neon" }
    fun daysLeft(): Int = (((week() + 1) * WEEK_MS - System.currentTimeMillis()) / 86_400_000L).toInt().coerceAtLeast(0) + 1

    fun ivMul(): Float = when (twist()) { 1 -> 0.77f; 3 -> 0.7f; else -> 1f }
    fun drainMul(): Float = if (twist() == 3) 1.35f else 1f
    fun shieldBonus(): Int = if (twist() == 0) 3 else 0
    fun alwaysCounter(): Boolean = twist() == 2

    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences("neonrush_worldecho", Context.MODE_PRIVATE)
    fun cleared(ctx: Context): Boolean { @Suppress("UNUSED_VARIABLE") val v = version; return try { prefs(ctx).getInt("cleared", -1) == week() } catch (_: Throwable) { false } }
    fun streak(ctx: Context): Int = try { prefs(ctx).getInt("streak", 0) } catch (_: Throwable) { 0 }

    /** A boss was defeated. Pays once per week; returns the gems paid (0 when already cleared). */
    suspend fun onDefeat(ctx: Context, dao: GameDao, isPro: Boolean): Int {
        if (cleared(ctx)) return 0
        val w = week()
        try { val sp = prefs(ctx); val last = sp.getInt("cleared", -1); sp.edit().putInt("cleared", w).putInt("streak", if (last == w - 1) sp.getInt("streak", 0) + 1 else 1).apply() } catch (_: Throwable) { }
        val g = if (isPro) GEMS_PRO else GEMS
        dao.updateProfile { it.copy(gems = it.gems + g) }
        FusionCores.award(ctx, w % 3, 1)
        justCleared = g
        version += 1
        return g
    }
}
