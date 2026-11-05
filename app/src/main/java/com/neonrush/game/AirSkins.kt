package com.neonrush.game

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * AIR SKINS (Stage 169): looks for the Air Support helicopter (HAWK SIX) and jet (VIPER ONE). Cosmetic only, no change to damage.
 * Art: res/drawable/air_heli_1..4 and air_jet_1..4 (the base air_heli / air_jet stay the free default).
 * Play Console: eight ONE-TIME products, $1.99 each: neonrush_hskin_1..4 (helicopters) and neonrush_jskin_1..4 (jets).
 */
object AirSkins {
    class Skin(val kind: String, val n: Int, val name: String) { val id get() = if (kind == "heli") "neonrush_hskin_$n" else "neonrush_jskin_$n"; val art get() = "air_${kind}_$n"; val price = "$1.99" }
    val HELI = listOf(Skin("heli", 1, "RAPTOR"), Skin("heli", 2, "HAMMER"), Skin("heli", 3, "STORMCALLER"), Skin("heli", 4, "WASP HUNTER"))
    val JET = listOf(Skin("jet", 1, "STRIKER"), Skin("jet", 2, "SWARM"), Skin("jet", 3, "BUNKER BUSTER"), Skin("jet", 4, "NIGHTFANG"))
    fun list(kind: String) = if (kind == "heli") HELI else JET

    var version by mutableStateOf(0)
    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences("neonrush_airskins", Context.MODE_PRIVATE)

    fun owns(ctx: Context, s: Skin, testing: Boolean = false): Boolean {
        @Suppress("UNUSED_VARIABLE") val v = version
        if (testing) return true
        val saved = try { prefs(ctx).getBoolean("own_${s.id}", false) } catch (_: Throwable) { false }
        val owned = try { RevenueCatManager.ownedProducts.value } catch (_: Throwable) { emptySet() }
        return saved || s.id in owned
    }
    /** 0 = the base look, else 1..4. */
    fun equipped(ctx: Context, kind: String): Int { @Suppress("UNUSED_VARIABLE") val v = version; return try { prefs(ctx).getInt("eq_$kind", 0) } catch (_: Throwable) { 0 } }
    fun equip(ctx: Context, kind: String, n: Int) { try { prefs(ctx).edit().putInt("eq_$kind", n).apply() } catch (_: Throwable) {}; version++ }
    /** After a real purchase (or in testing): own it and put it on. */
    fun applyPurchase(ctx: Context, s: Skin) { try { prefs(ctx).edit().putBoolean("own_${s.id}", true).putInt("eq_${s.kind}", s.n).apply() } catch (_: Throwable) {}; version++ }
    fun applyPurchaseById(ctx: Context, productId: String) { (HELI + JET).firstOrNull { it.id == productId }?.let { applyPurchase(ctx, it) } }
    /** The drawable name to draw for this aircraft right now. */
    fun artName(ctx: Context, kind: String): String { val n = equipped(ctx, kind); return if (n in 1..4) "air_${kind}_$n" else "air_$kind" }
}
