package com.neonrush.game

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * COMBAT WINGMAN (Stage 159, v2). A full copy of YOUR pilot in a military suit (the same run frames, graded and armed, with a rifle, a jet-pack and two gunship drones)
 * that mirrors your flying beside you, shoots on its own and every time you shoot, and collects its own ordnance crates while it is out.
 *   Locked below zone 30: the page advertises it (animated) but nothing can be bought. From zone 30 (best zone reached) it can be bought.
 *   Real money (one-time): $4.99 = 1 sortie per boss round, $9.99 = 2 sorties per boss round (Upgrades.WING, neonrush_wing_1 / _2; the $9.99 tier can be bought directly).
 *   WINGMAN ARMS Mk II-V (Upgrades.WARMS, neonrush_warms_1..4): cannon damage, sortie length, fire rate, and the second gunship drone from Mk III.
 *   SKINS (one-time, $1.99 each): DESERT OPS, ARCTIC, BLACKOUT (neonrush_wskin_1..3). OLIVE is free.
 *   ORDNANCE (picked up by the wingman during a sortie): ROCKET POD, MINIGUN, ION CANNON.
 * The call button shows only in a boss fight under severe attack (hull 60% or less, a lunge, a hunter dive). With TESTING_DISABLE_PRO_GATE on, everything is open and free.
 */
object Wingman {
    const val UNLOCK_ZONE = 30
    @Volatile var active = false
    @Volatile var left = 0f
    @Volatile var usedRound = 0
    @Volatile var roundKey = -1
    @Volatile var pilotShotSeq = 0          // the pilot's gun bumps this at every shot: the wingman answers with a shot of its own
    private val MUL = floatArrayOf(1.0f, 1.25f, 1.5f, 1.8f, 2.2f)
    private val LEN = floatArrayOf(14f, 15f, 16f, 18f, 20f)
    private val GAP = floatArrayOf(0.36f, 0.34f, 0.32f, 0.30f, 0.27f)
    fun sorties(ctx: Context, testing: Boolean): Int = if (testing) 2 else Upgrades.level(ctx, Upgrades.WING)
    fun arms(): Int = Upgrades.wingArms()
    fun damageMul(): Float = MUL[arms().coerceIn(0, 4)]
    fun length(): Float = LEN[arms().coerceIn(0, 4)]
    fun gap(): Float = GAP[arms().coerceIn(0, 4)]
    fun drones(): Int = if (arms() >= 2) 2 else 1
    /** In a boss fight: has the player reached the zone that gives access. */
    fun unlocked(zone: Int): Boolean = zone >= UNLOCK_ZONE
    /** Shop: can it be bought with the best zone reached so far. */
    fun canBuy(bestZone: Int, testing: Boolean): Boolean = testing || bestZone >= UNLOCK_ZONE
    fun syncRound(key: Int) { if (key != roundKey) { roundKey = key; usedRound = 0 } }
    fun reset() { active = false; left = 0f; usedRound = 0; roundKey = -1 }

    // ---- ordnance the wingman picks up ----
    val ORD_NAMES = arrayOf("ROCKET POD", "MINIGUN", "ION CANNON")
    val ORD_BLURB = arrayOf("6 rockets, 3x damage each", "8 seconds of very fast fire", "2 piercing blasts, 6x damage each")
    val ORD_COLS = longArrayOf(0xFFFF7043, 0xFFFFD54F, 0xFF40C4FF)

    // ---- skins ----
    class Skin(val id: String?, val name: String, val price: String, val accent: Long, val grade: FloatArray)
    val SKINS = listOf(
        Skin(null, "OLIVE", "FREE", 0xFFFFB74D, floatArrayOf(0.72f, 0.12f, 0f, 0f, 0f,  0.08f, 0.82f, 0f, 0f, 0f,  0f, 0.06f, 0.55f, 0f, 0f,  0f, 0f, 0f, 1f, 0f)),
        Skin("neonrush_wskin_1", "DESERT OPS", "$1.99", 0xFFFFD180, floatArrayOf(0.95f, 0.18f, 0f, 0f, 6f,  0.12f, 0.80f, 0f, 0f, 0f,  0f, 0.12f, 0.50f, 0f, 0f,  0f, 0f, 0f, 1f, 0f)),
        Skin("neonrush_wskin_2", "ARCTIC", "$1.99", 0xFF80D8FF, floatArrayOf(0.78f, 0.10f, 0.12f, 0f, 22f,  0.10f, 0.90f, 0.12f, 0f, 26f,  0.10f, 0.12f, 1.0f, 0f, 34f,  0f, 0f, 0f, 1f, 0f)),
        Skin("neonrush_wskin_3", "BLACKOUT", "$1.99", 0xFFFF5252, floatArrayOf(0.34f, 0.06f, 0.06f, 0f, 0f,  0.05f, 0.30f, 0.05f, 0f, 0f,  0.06f, 0.06f, 0.38f, 0f, 0f,  0f, 0f, 0f, 1f, 0f))
    )
    var skinVersion by mutableStateOf(0)
    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences("neonrush_wingman", Context.MODE_PRIVATE)
    fun ownsSkin(ctx: Context, i: Int): Boolean {
        @Suppress("UNUSED_VARIABLE") val v = skinVersion
        val s = SKINS.getOrNull(i) ?: return false
        if (s.id == null) return true
        val saved = try { prefs(ctx).getBoolean("own$i", false) } catch (_: Throwable) { false }
        val owned = try { RevenueCatManager.ownedProducts.value } catch (_: Throwable) { emptySet() }
        return saved || s.id in owned
    }
    fun equippedSkin(ctx: Context): Int { @Suppress("UNUSED_VARIABLE") val v = skinVersion; val i = try { prefs(ctx).getInt("skin", 0) } catch (_: Throwable) { 0 }; return if (ownsSkin(ctx, i)) i else 0 }
    fun equipSkin(ctx: Context, i: Int) { if (!ownsSkin(ctx, i)) return; try { prefs(ctx).edit().putInt("skin", i).apply() } catch (_: Throwable) { }; skinVersion += 1 }
    fun applySkinPurchase(ctx: Context, i: Int) { try { prefs(ctx).edit().putBoolean("own$i", true).putInt("skin", i).apply() } catch (_: Throwable) { }; skinVersion += 1 }
}
