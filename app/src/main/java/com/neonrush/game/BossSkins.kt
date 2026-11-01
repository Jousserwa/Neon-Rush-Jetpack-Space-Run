package com.neonrush.game

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * BOSS THEMES (Stage 164). Opt-in challenge skins, bought once with real money (neonrush_bskin_1..3, $2.99 each).
 * You pick one (or none) on the Boss Themes page. While one is equipped:
 *   - every boss fight is tougher: bosses and escorts take 25% less damage (their hull is +25%),
 *   - boss kills pay +40% gems,
 *   - the boss gets the theme's look: an aura, orbiting pieces and a glow on its shots (ui/BossThemeUi.kt).
 * Nobody is forced into a harder boss: the default is NONE, and the harder fight is a choice made before the run.
 */
object BossSkins {
    class Theme(val id: String, val name: String, val price: String, val accent: Long, val blurb: String)
    val THEMES = listOf(
        Theme("neonrush_bskin_1", "IRON LEGION", "$2.99", 0xFFFF8A3D, "Armoured plates circle the boss and its shots glow forge-orange."),
        Theme("neonrush_bskin_2", "TOXIC BLOOM", "$2.99", 0xFF76FF03, "A cloud of green spores drifts around the boss and its shots drip poison-green."),
        Theme("neonrush_bskin_3", "VOID EMPEROR", "$2.99", 0xFFB388FF, "Violet crown-shards orbit the boss and its shots shine gold-violet.")
    )
    const val HULL_MUL = 1.25f
    const val GEM_MUL = 1.4f

    var version by mutableStateOf(0)
    @Volatile private var equippedCache = -1
    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences("neonrush_bossskins", Context.MODE_PRIVATE)

    fun owns(ctx: Context, i: Int): Boolean {
        @Suppress("UNUSED_VARIABLE") val v = version
        val t = THEMES.getOrNull(i) ?: return false
        val saved = try { prefs(ctx).getBoolean("own$i", false) } catch (_: Throwable) { false }
        val store = try { t.id in RevenueCatManager.ownedProducts.value } catch (_: Throwable) { false }
        return saved || store
    }
    /** Index of the equipped theme, or -1 for none. Also refreshes the cache the sim and drawing code read. */
    fun equipped(ctx: Context): Int {
        @Suppress("UNUSED_VARIABLE") val v = version
        val e = try { prefs(ctx).getInt("equipped", -1) } catch (_: Throwable) { -1 }
        val ok = if (e >= 0 && owns(ctx, e)) e else -1
        equippedCache = ok
        return ok
    }
    fun refresh(ctx: Context) { equipped(ctx) }
    /** Equip theme [i], or switch the theme off when [i] is already equipped. */
    fun toggle(ctx: Context, i: Int) {
        if (!owns(ctx, i)) return
        val now = equipped(ctx)
        try { prefs(ctx).edit().putInt("equipped", if (now == i) -1 else i).apply() } catch (_: Throwable) { }
        version += 1; refresh(ctx)
    }
    fun applyPurchase(ctx: Context, i: Int) {
        try { prefs(ctx).edit().putBoolean("own$i", true).putInt("equipped", i).apply() } catch (_: Throwable) { }
        version += 1; refresh(ctx)
    }

    // ---- effects (read the cache, no context needed) ----
    fun active(): Theme? = THEMES.getOrNull(equippedCache)
    fun hullMul(): Float = if (equippedCache >= 0) HULL_MUL else 1f
    fun gemMul(): Float = if (equippedCache >= 0) GEM_MUL else 1f
}
