package com.neonrush.game

import android.os.Bundle
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.ktx.Firebase

/** Retention / funnel events that complement AnalyticsManager and HangarAnalytics. Every call is crash-safe. */
object GameAnalytics {
    private fun log(name: String, vararg kv: Pair<String, Any>) {
        val b = Bundle()
        kv.forEach { (k, v) ->
            when (v) {
                is Int -> b.putLong(k, v.toLong()); is Long -> b.putLong(k, v)
                is Boolean -> b.putString(k, v.toString()); else -> b.putString(k, v.toString())
            }
        }
        try { Firebase.analytics.logEvent(name, b) } catch (_: Throwable) {}
    }

    /** Where players quit: zone, score, distance and how many revives they used. */
    fun runEnd(zone: Int, score: Int, meters: Int, revives: Int) =
        log("nr_run_end", "zone" to zone, "score" to score, "meters" to meters, "revives" to revives)

    fun reviveUsed(kind: String, revivesSoFar: Int, cost: Int) =
        log("nr_revive", "kind" to kind, "n" to revivesSoFar, "cost" to cost)

    fun eventClaimed(key: String, tier: Int) = log("nr_event_claim", "event" to key, "tier" to tier)
    fun languageSet(code: String) = log("nr_language_set", "code" to code)
    fun offerShown(id: String) = log("nr_offer_shown", "offer" to id)
    fun offerTapped(id: String) = log("nr_offer_tapped", "offer" to id)
    fun tutorialOpened() = log("nr_tutorial_opened")
    fun fuelRefuel(zone: Int, cost: Int) = log("nr_refuel", "zone" to zone, "cost" to cost)
    fun dailyBonusClaimed(day: Int, gems: Int) = log("nr_daily_bonus", "day" to day, "gems" to gems)
}
