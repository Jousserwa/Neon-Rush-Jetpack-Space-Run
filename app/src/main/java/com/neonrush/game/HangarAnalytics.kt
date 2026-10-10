package com.neonrush.game

import android.os.Bundle
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.ktx.Firebase

/** Hull-system funnel events. Kept separate so AnalyticsManager only needs no edits. */
object HangarAnalytics {
    private fun log(name: String, vararg kv: Pair<String, Any>) {
        val b = Bundle()
        kv.forEach { (k, v) -> when (v) { is Int -> b.putInt(k, v); is Long -> b.putLong(k, v); is Boolean -> b.putBoolean(k, v); else -> b.putString(k, v.toString()) } }
        try { Firebase.analytics.logEvent(name, b) } catch (_: Exception) {}
    }
    fun hangarOpened(owned: Int, isPro: Boolean) = log("hangar_opened", "owned" to owned, "is_pro" to isPro)
    fun hullViewed(id: String) = log("hull_viewed", "hull_id" to id)
    fun hullTryStarted(id: String) = log("hull_try_started", "hull_id" to id)
    fun hullBuyTapped(id: String) = log("hull_buy_tapped", "hull_id" to id)
    fun hullPurchased(id: String, tier: String) = log("hull_purchased", "hull_id" to id, "tier" to tier)
    fun hullPurchaseFailed(id: String) = log("hull_purchase_failed", "hull_id" to id)
    fun hullEquipped(id: String) = log("hull_equipped", "hull_id" to id)
    fun rotationToggled(id: String, on: Boolean) = log("hull_rotation_toggled", "hull_id" to id, "on" to on)
    fun lockChanged(id: String, on: Boolean) = log("hull_lock_changed", "hull_id" to id, "on" to on)
    fun rotationSwitched(id: String, sector: Int) = log("hull_rotation_switched", "hull_id" to id, "sector" to sector)
    fun earlyAccessBlocked(id: String, isPro: Boolean) = log("hull_early_access_blocked", "hull_id" to id, "is_pro" to isPro)
    fun hullsRestored(count: Int) = log("hulls_restored", "count" to count)
}
