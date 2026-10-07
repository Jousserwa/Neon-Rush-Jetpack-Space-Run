package com.neonrush.game

import android.app.Activity
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.TimeZone

/**
 * One rewarded slot per AdMob ad unit, so each placement has its own preloaded ad
 * and its own reporting line in AdMob.
 *
 *  REVIVE       NeonRush_Rewarded_Revive      (game-over "watch ad to revive")
 *  DOUBLE_GEMS  NeonRush_Rewarded_DoubleGems  (game-over double gems + in-run sector bonus)
 *  DAILY_CRATE  Daily Crate Rewarded Ad       (daily crate)
 */
enum class RewardedSlot(val unitId: String, val analyticsName: String) {
    REVIVE("ca-app-pub-3841327492203214/4182218315", "revive"),
    DOUBLE_GEMS("ca-app-pub-3841327492203214/8797213140", "double_gems"),
    DAILY_CRATE("ca-app-pub-3841327492203214/6016158632", "daily_crate")
}

/**
 * Offline-safe ad manager.
 *
 *  - Preloads every ad unit, retries failed loads with a backoff, discards ads
 *    older than ~55 min (they expire at 1 h) and reloads when the network returns.
 *  - A rewarded action is NEVER granted for free just because no ad was ready.
 *    If no ad can be shown (offline or no fill) the player gets a small daily
 *    "offline bonus" allowance (GRACE_PER_DAY); after that, they must wait for an ad.
 *  - A missed game-over interstitial stays "owed" and shows at the next game over
 *    where an ad is ready, instead of being skipped until the next multiple of 3.
 *  - Pro players get no ads; players who bought Remove Ads get no banner or
 *    interstitial but can still opt in to rewarded ads.
 *
 * All state is touched on the main thread only.
 */
object AdMobManager {
    private const val TAG = "AdMobManager"

    // YOUR REAL ADMOB APP ID
    const val APP_ID = "ca-app-pub-3841327492203214~9145496921"

    // YOUR REAL AD UNIT IDs
    const val BANNER_AD_UNIT_ID = "ca-app-pub-3841327492203214/6533049489"
    const val INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-3841327492203214/3907006287"

    // TEST IDs (use these for testing, switch to real IDs for production)
    // const val BANNER_AD_UNIT_ID = "ca-app-pub-3940256099942544/6300978111"
    // const val INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-3940256099942544/1033173712"
    // Rewarded test ID: "ca-app-pub-3940256099942544/5224354917" (set unitId in RewardedSlot)

    private const val INTERSTITIAL_INTERVAL = 3 // Show interstitial every 3 game overs
    private const val PAYWALL_THRESHOLD = 5     // Show paywall after 5 game overs
    private const val AD_TTL_MS = 55 * 60 * 1000L
    private const val MAX_RETRIES = 5
    private const val GRACE_PER_DAY = 3

    private const val PREFS = "neon_ad_prefs"
    private const val KEY_GRACE_DAY = "grace_day"
    private const val KEY_GRACE_USED = "grace_used"

    private class Loaded<T>(val ad: T, val loadedAtMs: Long)

    private val mainHandler = Handler(Looper.getMainLooper())
    private var appContext: Context? = null

    private var interstitial: Loaded<InterstitialAd>? = null
    private var interstitialLoading = false
    private var interstitialRetry = 0
    private var interstitialOwed = false

    private val rewarded = mutableMapOf<RewardedSlot, Loaded<RewardedAd>>()
    private val rewardedLoading = mutableSetOf<RewardedSlot>()
    private val rewardedLastFailed = mutableSetOf<RewardedSlot>()
    private val rewardedRetry = mutableMapOf<RewardedSlot, Int>()

    private var online = true
    private var proUser = false
    private var adsRemoved = false
    private val interruptiveAdsOff get() = proUser || adsRemoved

    private var gameOverCount = 0

    // Bumped whenever ad readiness / connectivity / allowance changes, so Compose
    // buttons can recompute their labels.
    private val _adStateVersion = MutableStateFlow(0)
    val adStateVersion: StateFlow<Int> = _adStateVersion.asStateFlow()
    private fun bump() { _adStateVersion.update { it + 1 } }

    // ------------------------------------------------------------------ setup

    fun initialize(context: Context) {
        appContext = context.applicationContext
        try {
            MobileAds.initialize(context) { initializationStatus ->
                Log.d(TAG, "AdMob initialized: $initializationStatus")
            }
            registerNetworkCallback(context.applicationContext)
            loadAll()
        } catch (e: Exception) {
            Log.e(TAG, "AdMob initialization failed: ${e.message}")
        }
    }

    /** Call whenever Pro / Remove Ads status is known or changes. Main thread. */
    fun setAdState(isPro: Boolean, adsRemoved: Boolean) {
        proUser = isPro
        this.adsRemoved = adsRemoved
        loadAll()
        bump()
    }

    /** Cheap "make sure everything is loaded" hook (run start, game over, resume). */
    fun refresh() {
        rewardedRetry.clear()
        interstitialRetry = 0
        loadAll()
    }

    private fun loadAll() {
        ensureInterstitial()
        RewardedSlot.values().forEach { ensureRewarded(it) }
    }

    private fun now() = SystemClock.elapsedRealtime()
    private fun <T> isFresh(l: Loaded<T>?) = l != null && now() - l.loadedAtMs < AD_TTL_MS
    private fun retryDelayMs(attempt: Int) = (3_000L shl attempt.coerceAtMost(4)).coerceAtMost(60_000L)

    // ------------------------------------------------------------ connectivity

    private fun registerNetworkCallback(ctx: Context) {
        try {
            val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val caps = cm.getNetworkCapabilities(cm.activeNetwork)
            online = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
            cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    mainHandler.post { setOnline(true) }
                }
                override fun onLost(network: Network) {
                    mainHandler.post { setOnline(false) }
                }
                override fun onCapabilitiesChanged(network: Network, c: NetworkCapabilities) {
                    val ok = c.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                        c.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                    mainHandler.post { setOnline(ok) }
                }
            })
        } catch (e: Exception) {
            // Missing permission or unsupported: stay optimistic, load failures still drive the fallback.
            Log.w(TAG, "Network callback unavailable: ${e.message}")
        }
    }

    private fun setOnline(value: Boolean) {
        val wasOnline = online
        online = value
        if (value && !wasOnline) refresh() // connection is back: reload anything missing/expired
        bump()
    }

    // ------------------------------------------------------------ interstitial

    private fun ensureInterstitial() {
        val ctx = appContext ?: return
        if (interruptiveAdsOff || interstitialLoading || isFresh(interstitial)) return
        interstitial = null
        interstitialLoading = true
        InterstitialAd.load(ctx, INTERSTITIAL_AD_UNIT_ID, AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitial = Loaded(ad, now())
                    interstitialLoading = false
                    interstitialRetry = 0
                    Log.d(TAG, "Interstitial loaded")
                }
                override fun onAdFailedToLoad(error: LoadAdError) {
                    interstitial = null
                    interstitialLoading = false
                    Log.e(TAG, "Interstitial failed to load: ${error.message}")
                    AnalyticsManager.logAdEvent("ad_load_failed", "interstitial", error.code, online)
                    if (online && interstitialRetry < MAX_RETRIES) {
                        val d = retryDelayMs(interstitialRetry)
                        interstitialRetry++
                        mainHandler.postDelayed({ ensureInterstitial() }, d)
                    }
                }
            })
    }

    fun showInterstitialIfReady(activity: Activity, onComplete: () -> Unit) {
        if (interruptiveAdsOff) { onComplete(); return }
        val loaded = interstitial
        if (loaded != null && isFresh(loaded)) {
            interstitial = null
            loaded.ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdShowedFullScreenContent() {
                    interstitialOwed = false
                    AnalyticsManager.logAdViewed("interstitial", "game_over")
                }
                override fun onAdDismissedFullScreenContent() {
                    ensureInterstitial()
                    onComplete()
                }
                override fun onAdFailedToShowFullScreenContent(error: AdError) {
                    AnalyticsManager.logAdEvent("ad_show_failed", "interstitial", error.code, online)
                    interstitialOwed = true
                    ensureInterstitial()
                    onComplete()
                }
            }
            loaded.ad.show(activity)
        } else {
            // Due but nothing ready: keep it owed so it shows at the next game over
            // that has an ad, rather than being skipped for 3 more rounds.
            interstitialOwed = true
            interstitial = null
            interstitialRetry = 0
            ensureInterstitial()
            AnalyticsManager.logAdEvent("ad_not_ready", "interstitial", -1, online)
            onComplete()
        }
    }

    fun incrementGameOver() {
        if (interruptiveAdsOff) return
        gameOverCount++
    }

    fun isInterstitialDue(): Boolean {
        if (interruptiveAdsOff) return false
        return interstitialOwed || (gameOverCount % INTERSTITIAL_INTERVAL == 0 && gameOverCount > 0)
    }

    fun isPaywallDue(): Boolean {
        return gameOverCount >= PAYWALL_THRESHOLD && gameOverCount % PAYWALL_THRESHOLD == 0
    }

    fun resetCounters() {
        gameOverCount = 0
    }

    // ------------------------------------------------------------------ rewarded

    private fun ensureRewarded(slot: RewardedSlot) {
        val ctx = appContext ?: return
        if (proUser || slot in rewardedLoading || isFresh(rewarded[slot])) return
        rewarded.remove(slot)
        rewardedLoading.add(slot)
        RewardedAd.load(ctx, slot.unitId, AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    rewarded[slot] = Loaded(ad, now())
                    rewardedLoading.remove(slot)
                    rewardedLastFailed.remove(slot)
                    rewardedRetry.remove(slot)
                    Log.d(TAG, "Rewarded(${slot.analyticsName}) loaded")
                    bump()
                }
                override fun onAdFailedToLoad(error: LoadAdError) {
                    rewarded.remove(slot)
                    rewardedLoading.remove(slot)
                    rewardedLastFailed.add(slot)
                    Log.e(TAG, "Rewarded(${slot.analyticsName}) failed: ${error.message}")
                    AnalyticsManager.logAdEvent("ad_load_failed", slot.analyticsName, error.code, online)
                    val attempt = rewardedRetry[slot] ?: 0
                    if (online && attempt < MAX_RETRIES) {
                        rewardedRetry[slot] = attempt + 1
                        mainHandler.postDelayed({ ensureRewarded(slot) }, retryDelayMs(attempt))
                    }
                    bump()
                }
            })
    }

    fun isRewardedReady(slot: RewardedSlot): Boolean = isFresh(rewarded[slot])

    /** True when no ad can be shown right now (offline, or the last load found no ad). */
    private fun fallbackEligible(slot: RewardedSlot) = !online || slot in rewardedLastFailed

    fun graceRemaining(): Int {
        val ctx = appContext ?: return 0
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val used = if (p.getLong(KEY_GRACE_DAY, -1L) == localDay()) p.getInt(KEY_GRACE_USED, 0) else 0
        return (GRACE_PER_DAY - used).coerceAtLeast(0)
    }

    private fun consumeGrace(): Int {
        val ctx = appContext ?: return 0
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val today = localDay()
        val used = if (p.getLong(KEY_GRACE_DAY, -1L) == today) p.getInt(KEY_GRACE_USED, 0) else 0
        p.edit().putLong(KEY_GRACE_DAY, today).putInt(KEY_GRACE_USED, used + 1).apply()
        bump()
        return (GRACE_PER_DAY - used - 1).coerceAtLeast(0)
    }

    private fun localDay(): Long {
        val t = System.currentTimeMillis()
        return (t + TimeZone.getDefault().getOffset(t)) / 86_400_000L
    }

    /**
     * Button label that tells the truth about what a tap will do.
     * Ad ready or loading -> [readyLabel]; offline / no fill -> offline bonus or "unavailable".
     */
    fun rewardedLabel(slot: RewardedSlot, readyLabel: String, @Suppress("UNUSED_PARAMETER") version: Int = 0): String {
        if (proUser || isRewardedReady(slot) || !fallbackEligible(slot)) return readyLabel
        val left = graceRemaining()
        return if (left > 0) "🎁 OFFLINE BONUS · $left LEFT" else "📡 AD UNAVAILABLE"
    }

    fun showRewardedIfReady(activity: Activity, slot: RewardedSlot, onRewarded: () -> Unit) {
        if (proUser) { onRewarded(); return } // Pro: no ads at all

        val loaded = rewarded[slot]
        if (loaded != null && isFresh(loaded)) {
            rewarded.remove(slot) // an ad can only be shown once
            var earned = false
            loaded.ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    ensureRewarded(slot)
                }
                override fun onAdFailedToShowFullScreenContent(error: AdError) {
                    AnalyticsManager.logAdEvent("ad_show_failed", slot.analyticsName, error.code, online)
                    ensureRewarded(slot)
                    if (!earned) noAdAvailable(activity, slot, onRewarded)
                }
            }
            loaded.ad.show(activity) { rewardItem ->
                earned = true
                Log.d(TAG, "User earned reward: ${rewardItem.amount} ${rewardItem.type}")
                AnalyticsManager.logAdViewed("rewarded", slot.analyticsName)
                onRewarded()
            }
            bump()
            return
        }

        // Nothing ready (never loaded, expired, or failed).
        rewardedRetry.remove(slot)
        val canFallBack = fallbackEligible(slot)
        rewarded.remove(slot)
        ensureRewarded(slot)
        AnalyticsManager.logAdEvent("ad_not_ready", slot.analyticsName, -1, online)
        if (canFallBack) {
            noAdAvailable(activity, slot, onRewarded)
        } else {
            toast(activity, "Ad is loading… try again in a few seconds")
        }
    }

    // No ad can be shown: small daily allowance, then ask the player to reconnect.
    private fun noAdAvailable(activity: Activity, slot: RewardedSlot, onRewarded: () -> Unit) {
        if (graceRemaining() > 0) {
            val left = consumeGrace()
            AnalyticsManager.logAdEvent("offline_grace_used", slot.analyticsName, left, online)
            toast(activity, "Offline bonus used · $left left today")
            onRewarded()
        } else {
            AnalyticsManager.logAdEvent("ad_unavailable_no_grace", slot.analyticsName, -1, online)
            toast(activity, "No ad available right now. Connect to the internet to earn this reward.")
        }
    }

    private fun toast(activity: Activity, msg: String) {
        Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show()
    }

    // -------------------------------------------------------------------- banner

    fun createBannerAdView(context: Context): AdView {
        return AdView(context).apply {
            setAdSize(AdSize.BANNER)
            adUnitId = BANNER_AD_UNIT_ID
            loadAd(AdRequest.Builder().build())
        }
    }
}
