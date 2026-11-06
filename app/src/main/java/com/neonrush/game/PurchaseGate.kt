package com.neonrush.game

import android.app.Activity
import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * COOLING-OFF LOCK (Stage 175): ONE real-money purchase per day, so nobody can empty the whole shop in one evening and kill their own game.
 * Every shop purchase goes through [buy]. NOT covered on purpose: the Pro subscription, restoring purchases, and anything paid with gems.
 * A blocked player sees the SYSTEMS COOLING screen (PurchaseGateUi.kt) with a live countdown and can save the item for tomorrow.
 * (Enforced inside the game only; purchases made straight in the Play Store cannot be stopped from here.)
 */
object PurchaseGate {
    /** Product id the cooling screen is currently shown for (null = hidden). */
    var coolingFor by mutableStateOf<String?>(null)

    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences("neonrush_gate", Context.MODE_PRIVATE)
    private fun nowMs(): Long = try { ServerClock.bestNowMs() } catch (_: Throwable) { System.currentTimeMillis() }
    private fun offset(t: Long): Long = java.util.TimeZone.getDefault().getOffset(t).toLong()
    private fun dayIndex(t: Long): Long = (t + offset(t)) / 86_400_000L

    fun boughtToday(ctx: Context): Boolean = try { prefs(ctx).getLong("day", -1L) == dayIndex(nowMs()) } catch (_: Throwable) { false }
    /** Milliseconds until the next local midnight, when the lock opens again. */
    fun msUntilOpen(): Long { val t = nowMs(); return ((dayIndex(t) + 1) * 86_400_000L - offset(t) - t).coerceIn(0L, 86_400_000L) }

    /** Start a real-money purchase. Same shape as RevenueCatManager.purchaseGemPack. */
    fun buy(act: Activity, id: String, cb: (Boolean) -> Unit) {
        if (!TESTING_DISABLE_PRO_GATE && boughtToday(act)) {
            coolingFor = id
            val m = msUntilOpen() / 60_000L
            Toast.makeText(act, "One purchase per day. Next one opens in ${m / 60}h ${m % 60}m", Toast.LENGTH_LONG).show()
            cb(false)
            return
        }
        RevenueCatManager.purchaseGemPack(act, id) { ok ->
            if (ok && !TESTING_DISABLE_PRO_GATE) { try { prefs(act).edit().putLong("day", dayIndex(nowMs())).apply() } catch (_: Throwable) {} }
            cb(ok)
        }
    }

    // ---- "save for tomorrow" ----
    fun saveForLater(ctx: Context, id: String) { try { prefs(ctx).edit().putString("wish", id).apply() } catch (_: Throwable) {} }
    fun wish(ctx: Context): String? = try { prefs(ctx).getString("wish", null) } catch (_: Throwable) { null }
    fun clearWish(ctx: Context) { try { prefs(ctx).edit().remove("wish").apply() } catch (_: Throwable) {} }

    /** A friendly name for a product id (used by the cooling screen and FLASH). */
    fun label(id: String): String = when {
        id.startsWith("neonrush_gun_") -> "Plasma Gun upgrade"
        id.startsWith("neonrush_bomb_") -> "Brood Bomb upgrade"
        id.startsWith("neonrush_heli_") -> "Helicopter upgrade"
        id.startsWith("neonrush_jet_") -> "Jet upgrade"
        id.startsWith("neonrush_lance_") -> "Pulse Lance upgrade"
        id.startsWith("neonrush_rack_") -> "Bomb Rack"
        id.startsWith("neonrush_lstore_") -> "Lance Storage"
        id.startsWith("neonrush_warms_") -> "Wingman Arms"
        id.startsWith("neonrush_wing") -> "Combat Wingman"
        id.startsWith("neonrush_wskin_") -> "Wingman skin"
        id.startsWith("neonrush_hskin_") || id.startsWith("neonrush_jskin_") -> "Aircraft skin"
        id.startsWith("neonrush_bskin_") -> "Boss theme"
        id.startsWith("neonrush_air_") -> "Air support calls"
        id.startsWith("neonrush_ammo_") -> "Ammo Depot shots"
        id.startsWith("neonrush_bombpack_") || id.startsWith("neonrush_bombvault_") -> "Bomb Depot"
        id.startsWith("neonrush_mag_") -> "Magazine Bay"
        id == MilShield.AUTO6_ID -> "AUTO 6"
        else -> "your saved item"
    }
}
