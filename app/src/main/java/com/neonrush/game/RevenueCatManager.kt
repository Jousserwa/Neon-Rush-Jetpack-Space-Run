package com.neonrush.game

import android.app.Activity
import android.content.Context
import android.util.Log
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesConfiguration
import com.revenuecat.purchases.PurchasesError
import com.revenuecat.purchases.interfaces.ReceiveCustomerInfoCallback
import com.revenuecat.purchases.interfaces.ReceiveOfferingsCallback
import com.revenuecat.purchases.interfaces.UpdatedCustomerInfoListener
import com.revenuecat.purchases.interfaces.PurchaseCallback
import com.revenuecat.purchases.models.StoreTransaction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object RevenueCatManager {
    private const val TAG = "RevenueCatManager"
    
    // YOUR REAL REVENUECAT API KEY
    private const val REVENUECAT_API_KEY = "goog_sveqtpBHLaPtuWlJfUvRySdYocO"
    
    // Product IDs
const val PRODUCT_ID_PRO_MONTHLY = "neon_rush_pro:monthly"
const val PRODUCT_ID_PRO_ANNUAL = "neon_rush_pro:annual"
const val PRODUCT_ID_GEMS_SMALL = "neonrush_gems_small"
const val PRODUCT_ID_GEMS_MEDIUM = "neonrush_gems_medium"
const val PRODUCT_ID_GEMS_LARGE = "neonrush_gems_large"
const val PRODUCT_ID_REMOVE_ADS = "remove_ads"
const val PRODUCT_ID_STARTER_PACK = "starter_pack_24h"

// Prices (defaults shown before RevenueCat fetches real store prices)
const val SUBSCRIPTION_PRICE_MONTHLY_USD = "$2.99"
const val SUBSCRIPTION_PRICE_ANNUAL_USD = "$24.00"
const val GEMS_SMALL_PRICE_USD = "$0.99"
const val GEMS_MEDIUM_PRICE_USD = "$4.99"
const val GEMS_LARGE_PRICE_USD = "$9.99"
const val REMOVE_ADS_PRICE_USD = "$2.99"
const val STARTER_PACK_PRICE_USD = "$0.99"

const val GEMS_SMALL_AMOUNT = 100
const val GEMS_MEDIUM_AMOUNT = 550
const val GEMS_LARGE_AMOUNT = 1200
const val STARTER_PACK_GEMS_AMOUNT = 250

    private val _isPro = MutableStateFlow(false)
    val isPro: StateFlow<Boolean> = _isPro.asStateFlow()
    private val _proAnnual = MutableStateFlow(false)
    /** True while the active Pro plan is the ANNUAL one (drives the home-screen screensaver). */
    val proAnnual: StateFlow<Boolean> = _proAnnual.asStateFlow()
    private val _isAdsRemoved = MutableStateFlow(false)
val isAdsRemoved: StateFlow<Boolean> = _isAdsRemoved.asStateFlow()

    private val _ownedProducts = MutableStateFlow<Set<String>>(emptySet())
    /** Base store product ids the player owns (survives offline: mirrored in the encrypted [PurchaseLedger]). */
    val ownedProducts: StateFlow<Set<String>> = _ownedProducts.asStateFlow()

    /** Set by the ViewModel: called after every fresh/cached entitlement update so hulls, suits, Pro and ads state can be mirrored. */
    @Volatile var onInfoApplied: (() -> Unit)? = null

    /** Gems granted by each consumable product (used to recover a paid-but-undelivered purchase after a crash). */
    val GEMS_BY_PRODUCT: Map<String, Int> = mapOf(
        PRODUCT_ID_GEMS_SMALL to GEMS_SMALL_AMOUNT,
        PRODUCT_ID_GEMS_MEDIUM to GEMS_MEDIUM_AMOUNT,
        PRODUCT_ID_GEMS_LARGE to GEMS_LARGE_AMOUNT,
        PRODUCT_ID_STARTER_PACK to STARTER_PACK_GEMS_AMOUNT
    )

    private var isInitialized = false
    private var appCtx: Context? = null

    /** Single place where store state enters the game: updates flows AND the secure offline ledger. */
    private fun applyInfo(info: com.revenuecat.purchases.CustomerInfo) {
        PurchaseLedger.onCustomerInfo(info)
        _isPro.value = info.entitlements.active.containsKey("Neon Rush Pro")
        _proAnnual.value = _isPro.value && PurchaseLedger.isProAnnualCached()
        _isAdsRemoved.value = PurchaseLedger.isAdsRemovedCached()
        _ownedProducts.value = PurchaseLedger.ownedProducts()
        Log.d(TAG, "Pro=${_isPro.value} AdsRemoved=${_isAdsRemoved.value} owned=${_ownedProducts.value.size}")
        onInfoApplied?.invoke()
    }

    /** Re-post unsynced receipts and refresh entitlements. Called on launch-online and whenever internet returns. */
    fun syncNow(onResult: ((Boolean) -> Unit)? = null) {
        if (!isInitialized) { onResult?.invoke(false); return }
        try {
            Purchases.sharedInstance.restorePurchases(object : ReceiveCustomerInfoCallback {
                override fun onReceived(customerInfo: com.revenuecat.purchases.CustomerInfo) { applyInfo(customerInfo); onResult?.invoke(true) }
                override fun onError(error: PurchasesError) { Log.w(TAG, "sync deferred (offline?): ${error.message}"); onResult?.invoke(false) }
            })
        } catch (e: Exception) { onResult?.invoke(false) }
    }

    fun initialize(context: Context) {
        appCtx = context.applicationContext
        // Offline-first: load the encrypted ledger BEFORE touching the network, so Pro / ads-removed / owned items
        // are correct from the very first frame even with no internet.
        PurchaseLedger.init(context)
        _isPro.value = PurchaseLedger.isProCached()
        _proAnnual.value = _isPro.value && PurchaseLedger.isProAnnualCached()
        _isAdsRemoved.value = PurchaseLedger.isAdsRemovedCached()
        _ownedProducts.value = PurchaseLedger.ownedProducts()
        if (isInitialized) return
        try {
            val configuration = PurchasesConfiguration.Builder(context, REVENUECAT_API_KEY).build()
            Purchases.configure(configuration)
            isInitialized = true
            Log.d(TAG, "RevenueCat initialized successfully")
            // Background entitlement updates (renewals, refunds, other device) land here automatically.
            Purchases.sharedInstance.updatedCustomerInfoListener = UpdatedCustomerInfoListener { info -> applyInfo(info) }
            OfflineSync.start(context)
            
            // Check subscription status
            checkSubscriptionStatus()
        } catch (e: Exception) {
            Log.e(TAG, "RevenueCat initialization failed: ${e.message}")
        }
    }

    private fun checkSubscriptionStatus() {
    try {
        Purchases.sharedInstance.getCustomerInfo(
            object : ReceiveCustomerInfoCallback {
                override fun onReceived(customerInfo: com.revenuecat.purchases.CustomerInfo) { applyInfo(customerInfo) }

                override fun onError(error: PurchasesError) {
                    // Offline / store unreachable: keep the cached state from the secure ledger. Never downgrade here.
                    Log.w(TAG, "customer info unavailable, using offline ledger: ${error.message}")
                }
            }
        )
    } catch (e: Exception) {
        Log.e(TAG, "Error checking subscription: ${e.message}")
    }
}

    /** Human-readable reason for the last failed purchase ("" when the user just cancelled). UI shows it in a toast. */
    @Volatile var lastError: String = ""

    private fun finishOk(onResult: (Boolean) -> Unit) { lastError = ""; onResult(true) }
    private fun finishFail(msg: String, onResult: (Boolean) -> Unit) {
        lastError = msg; Log.e(TAG, msg)
        // Always tell the player why a purchase did not start (previously every failure was silent).
        appCtx?.let { c -> android.os.Handler(android.os.Looper.getMainLooper()).post {
            android.widget.Toast.makeText(c, msg, android.widget.Toast.LENGTH_LONG).show() } }
        onResult(false)
    }

    private fun runPurchase(
        activity: Activity,
        params: com.revenuecat.purchases.PurchaseParams,
        onOk: (com.revenuecat.purchases.CustomerInfo) -> Unit,
        onResult: (Boolean) -> Unit
    ) {
        Purchases.sharedInstance.purchase(params, object : PurchaseCallback {
            override fun onCompleted(storeTransaction: StoreTransaction, customerInfo: com.revenuecat.purchases.CustomerInfo) {
                // Save the receipt result securely FIRST. Consumables go into the pending list before any gem is granted,
                // so a crash / kill between "paid" and "granted" is recovered on next launch.
                try {
                    val pid = storeTransaction.productIds.firstOrNull()?.substringBefore(':')
                    val gems = pid?.let { GEMS_BY_PRODUCT[it] } ?: 0
                    if (pid != null && (gems > 0 || pid.startsWith("neonrush_pass_") || pid.startsWith("neonrush_bundle_pass"))) PurchaseLedger.recordPending(storeTransaction.orderId ?: storeTransaction.purchaseToken, pid, gems)
                } catch (e: Exception) { Log.w(TAG, "ledger pending: ${e.message}") }
                applyInfo(customerInfo)
                onOk(customerInfo); finishOk(onResult)
            }
            override fun onError(error: PurchasesError, userCancelled: Boolean) {
                if (userCancelled) { lastError = ""; onResult(false) }
                else finishFail("Purchase failed: ${error.message}", onResult)
            }
        })
    }

    private fun buySubscription(activity: Activity, annual: Boolean, onResult: (Boolean) -> Unit) {
        if (!isInitialized) { finishFail("Store is not ready yet. Please try again in a moment.", onResult); return }
        try {
            Purchases.sharedInstance.getOfferings(object : ReceiveOfferingsCallback {
                override fun onReceived(offerings: com.revenuecat.purchases.Offerings) {
                    val wantedId = if (annual) PRODUCT_ID_PRO_ANNUAL else PRODUCT_ID_PRO_MONTHLY
                    val keyword = if (annual) "annual" else "month"
                    val type = if (annual) com.revenuecat.purchases.PackageType.ANNUAL else com.revenuecat.purchases.PackageType.MONTHLY
                    // Search the current offering first, then every offering. Match by standard package
                    // id, package type, exact product id, or a "monthly"/"annual" word in the id.
                    val pools = listOfNotNull(offerings.current) + offerings.all.values
                    val pkg = pools.asSequence().flatMap { it.availablePackages.asSequence() }.firstOrNull { p ->
                        p.packageType == type || p.identifier == (if (annual) "\$rc_annual" else "\$rc_monthly") ||
                            p.product.id == wantedId || p.product.id.contains(keyword, ignoreCase = true)
                    }
                    if (pkg == null) {
                        finishFail("Pro ${if (annual) "annual" else "monthly"} plan isn't available from the store yet (no matching package in the RevenueCat offering).", onResult)
                        return
                    }
                    try {
                        val params = com.revenuecat.purchases.PurchaseParams.Builder(activity, pkg).build()
                        runPurchase(activity, params, { _isPro.value = true; _proAnnual.value = annual }, onResult)
                    } catch (e: Exception) { finishFail("Could not start purchase: ${e.message}", onResult) }
                }
                override fun onError(error: PurchasesError) {
                    finishFail("Could not reach the store: ${error.message}", onResult)
                }
            })
        } catch (e: Exception) {
            finishFail("Store error: ${e.message}", onResult)
        }
    }

    fun purchaseProSubscription(activity: Activity, onResult: (Boolean) -> Unit) = buySubscription(activity, false, onResult)

    fun purchaseProSubscriptionAnnual(activity: Activity, onResult: (Boolean) -> Unit) = buySubscription(activity, true, onResult)

    /**
     * Generic one-time purchase by store product id (gem packs, remove ads, starter pack, hulls, bundle, season pass).
     * 1) uses the product from any RevenueCat offering; 2) if it is not in an offering, asks the store directly
     * (getProducts) so the product does NOT have to be placed in an offering to be purchasable.
     */
    fun purchaseGemPack(activity: Activity, productId: String, onResult: (Boolean) -> Unit) {
        if (!isInitialized) { finishFail("Store is not ready yet. Please try again in a moment.", onResult); return }
        fun start(params: com.revenuecat.purchases.PurchaseParams) {
            AnalyticsManager.logPurchaseAttempted(productId)
            runPurchase(activity, params, { info ->
                AnalyticsManager.logPurchaseCompleted(productId)
                _isAdsRemoved.value = info.entitlements.active.containsKey("remove_ads") || _isAdsRemoved.value
            }, onResult)
        }
        fun viaStore() {
            try {
                Purchases.sharedInstance.getProducts(listOf(productId), object : com.revenuecat.purchases.interfaces.GetStoreProductsCallback {
                    override fun onReceived(storeProducts: List<com.revenuecat.purchases.models.StoreProduct>) {
                        val p = storeProducts.firstOrNull { it.id.substringBefore(':') == productId } ?: storeProducts.firstOrNull()
                        if (p == null) finishFail("\"$productId\" isn't available in the store yet. Create it in Play Console (and activate it).", onResult)
                        else try { start(com.revenuecat.purchases.PurchaseParams.Builder(activity, p).build()) }
                        catch (e: Exception) { finishFail("Could not start purchase: ${e.message}", onResult) }
                    }
                    override fun onError(error: PurchasesError) { finishFail("Store error: ${error.message}", onResult) }
                })
            } catch (e: Exception) { finishFail("Store error: ${e.message}", onResult) }
        }
        try {
            Purchases.sharedInstance.getOfferings(object : ReceiveOfferingsCallback {
                override fun onReceived(offerings: com.revenuecat.purchases.Offerings) {
                    val pkg = offerings.all.values.flatMap { it.availablePackages }
                        .find { it.product.id == productId || it.product.id.substringBefore(':') == productId }
                    if (pkg != null) {
                        try { start(com.revenuecat.purchases.PurchaseParams.Builder(activity, pkg).build()) }
                        catch (e: Exception) { finishFail("Could not start purchase: ${e.message}", onResult) }
                    } else viaStore()
                }
                override fun onError(error: PurchasesError) { viaStore() }
            })
        } catch (e: Exception) { viaStore() }
    }

    fun purchasePilotSuit(activity: Activity, productId: String, onResult: (Boolean) -> Unit) {
        // Pilot suits use the same purchase flow as gem packs
        purchaseGemPack(activity, productId, onResult)
    }
    fun purchaseRemoveAds(activity: Activity, onResult: (Boolean) -> Unit) {
    // Remove Ads uses the same generic purchase flow
    purchaseGemPack(activity, PRODUCT_ID_REMOVE_ADS, onResult)
}

fun purchaseStarterPack(activity: Activity, onResult: (Boolean) -> Unit) {
    // Starter Pack uses the same generic purchase flow
    purchaseGemPack(activity, PRODUCT_ID_STARTER_PACK, onResult)
}

    fun restorePurchases(onResult: (Boolean) -> Unit) {
    try {
        Purchases.sharedInstance.restorePurchases(
            object : ReceiveCustomerInfoCallback {
                override fun onReceived(customerInfo: com.revenuecat.purchases.CustomerInfo) {
                    applyInfo(customerInfo)
                    onResult(true)
                }

                override fun onError(error: PurchasesError) {
                    onResult(false)
                }
            }
        )
    } catch (e: Exception) {
        onResult(false)
    }
}
}

