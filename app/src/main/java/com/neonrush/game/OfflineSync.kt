package com.neonrush.game

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * Re-syncs purchases the moment the device gets internet again.
 *  - Watches connectivity; when a network with internet appears (and again after being offline) it calls
 *    [RevenueCatManager.syncNow], which re-posts any unsynced store receipts to RevenueCat and refreshes
 *    [PurchaseLedger]. Restored items flow to the game through [RevenueCatManager.onInfoApplied].
 *  - Throttled so a flapping connection doesn't spam the store.
 * Needs android.permission.ACCESS_NETWORK_STATE (normal permission; already in your manifest).
 */
object OfflineSync {
    private const val TAG = "OfflineSync"
    private const val MIN_GAP_MS = 20_000L
    private var started = false
    private var lastRun = 0L
    private val main = Handler(Looper.getMainLooper())

    fun start(context: Context) {
        if (started) return
        started = true
        try {
            val cm = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val req = NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build()
            cm.registerNetworkCallback(req, object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) { trigger("network available") }
            })
        } catch (e: Exception) { Log.e(TAG, "connectivity watch failed: ${e.message}") }
    }

    fun trigger(reason: String) {
        val now = System.currentTimeMillis()
        if (now - lastRun < MIN_GAP_MS) return
        lastRun = now
        // give the connection a moment to become usable
        main.postDelayed({ Log.d(TAG, "sync: $reason"); RevenueCatManager.syncNow() }, 2500)
    }
}
