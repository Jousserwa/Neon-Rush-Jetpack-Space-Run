package com.neonrush.game

import com.google.firebase.ktx.Firebase
import com.google.firebase.remoteconfig.ktx.remoteConfig
import com.google.firebase.remoteconfig.ktx.remoteConfigSettings

/**
 * Server-side tuning without shipping an update. Set the same keys in Firebase console > Remote Config.
 * Every getter falls back to a safe default if Firebase is unreachable.
 */
object RemoteTuning {
    private val defaults: Map<String, Any> = mapOf(
        "revive_cost_pct" to 100L,      // 100 = normal gem cost to revive
        "event_banner_text" to "",      // optional message for a future live-event banner
        "starter_pack_enabled" to true,
        "event_goal_pct" to 100L        // scales every live-event goal (100 = normal)
    )
    private var started = false

    fun start() {
        if (started) return
        started = true
        try {
            val rc = Firebase.remoteConfig
            rc.setConfigSettingsAsync(remoteConfigSettings { minimumFetchIntervalInSeconds = 3600 })
            rc.setDefaultsAsync(defaults)
            rc.fetchAndActivate()
        } catch (_: Throwable) {}
    }

    private fun long(key: String): Long =
        try { Firebase.remoteConfig.getLong(key) } catch (_: Throwable) { (defaults[key] as Long) }

    /** Revive price multiplier, clamped to 25..300 % so a bad value can never break the economy. */
    fun reviveCostPct(): Int = long("revive_cost_pct").toInt().coerceIn(25, 300)

    /** Live-event goal multiplier, clamped to 25..400 %. */
    fun eventGoalPct(): Int = long("event_goal_pct").toInt().coerceIn(25, 400)

    fun eventBanner(): String = try { Firebase.remoteConfig.getString("event_banner_text") } catch (_: Throwable) { "" }
    fun starterPackEnabled(): Boolean = try { Firebase.remoteConfig.getBoolean("starter_pack_enabled") } catch (_: Throwable) { true }
}
