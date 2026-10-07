package com.neonrush.game

import android.content.Context
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.ktx.Firebase

object AnalyticsManager {
    private var firebaseAnalytics: FirebaseAnalytics? = null

    fun initialize(context: Context) {
        firebaseAnalytics = Firebase.analytics
    }

    fun logGameStart() {
        firebaseAnalytics?.logEvent("game_start", null)
    }

    fun logGameOver(score: Int, isNewPB: Boolean, zoneReached: Int) {
        val params = Bundle().apply {
            putInt("score", score)
            putBoolean("is_new_pb", isNewPB)
            putInt("zone_reached", zoneReached)
        }
        firebaseAnalytics?.logEvent("game_over", params)
    }

    fun logAdViewed(adType: String, slot: String = "") {
        val params = Bundle().apply {
            putString("ad_type", adType) // "rewarded" or "interstitial"
            if (slot.isNotEmpty()) putString("ad_slot", slot)
        }
        firebaseAnalytics?.logEvent("ad_viewed", params)
    }

    fun logFirstFlightReward() {
        firebaseAnalytics?.logEvent("first_flight_reward", Bundle())
    }

    // Ad health events: ad_load_failed, ad_not_ready, ad_show_failed,
    // offline_grace_used, ad_unavailable_no_grace. They show how much ad
    // inventory is being missed and why (offline vs. no fill).
    fun logAdEvent(event: String, slot: String, code: Int = -1, online: Boolean? = null) {
        val params = Bundle().apply {
            putString("ad_slot", slot)
            if (code >= 0) putInt("code", code)
            if (online != null) putBoolean("online", online)
        }
        firebaseAnalytics?.logEvent(event, params)
    }

    fun logPurchaseAttempted(productId: String) {
        val params = Bundle().apply {
            putString(FirebaseAnalytics.Param.ITEM_ID, productId)
        }
        firebaseAnalytics?.logEvent("purchase_attempted", params)
    }

    fun logPurchaseCompleted(productId: String) {
        val params = Bundle().apply {
            putString(FirebaseAnalytics.Param.ITEM_ID, productId)
        }
        firebaseAnalytics?.logEvent(FirebaseAnalytics.Event.PURCHASE, params)
    }

    fun logScoreMilestone(milestone: Int) {
        val params = Bundle().apply {
            putInt("milestone", milestone)
        }
        firebaseAnalytics?.logEvent("score_milestone", params)
    }
}
