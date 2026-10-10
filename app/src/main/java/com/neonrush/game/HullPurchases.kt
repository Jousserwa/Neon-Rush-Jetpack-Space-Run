package com.neonrush.game

import android.app.Activity
import android.util.Log
import com.revenuecat.purchases.CustomerInfo
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesError
import com.revenuecat.purchases.interfaces.ReceiveCustomerInfoCallback
import com.revenuecat.purchases.interfaces.ReceiveOfferingsCallback
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Premium hull purchases. Non-consumable products "neonrush_hull_<id>" must sit in a RevenueCat
 * offering (any offering, e.g. "hulls") so getOfferings() can find them and give store-localized prices.
 *
 * Ownership truth = CustomerInfo.allPurchasedProductIds (survives reinstall + restorePurchases).
 * [onOwnedChanged] lets the ViewModel mirror ownership into HangarStore + GameProfile.unlockedSkinsCsv.
 */
object HullPurchases {
    private const val TAG = "HullPurchases"

    private val _prices = MutableStateFlow<Map<String, String>>(emptyMap())
    val prices: StateFlow<Map<String, String>> = _prices.asStateFlow()

    /** productId -> price string, from the store. Call once at startup / when Hangar opens. */
    fun loadPrices() {
        try {
            Purchases.sharedInstance.getOfferings(object : ReceiveOfferingsCallback {
                override fun onReceived(offerings: com.revenuecat.purchases.Offerings) {
                    val map = offerings.all.values.flatMap { it.availablePackages }
                        .filter { it.product.id.startsWith("neonrush_hull_") || it.product.id.startsWith("neonrush_pass_") ||
                                  it.product.id.startsWith("neonrush_bundle_") }
                        .associate { it.product.id.substringBefore(':') to it.product.price.formatted }
                    _prices.value = map
                }
                override fun onError(error: PurchasesError) { Log.e(TAG, "offerings: ${error.message}") }
            })
        } catch (e: Exception) { Log.e(TAG, "loadPrices: ${e.message}") }
    }

    /** Buy one hull. Reuses the generic flow so purchase_attempted/completed analytics fire. */
    fun buy(activity: Activity, hull: PremiumHull, onResult: (Boolean) -> Unit) {
        HangarAnalytics.hullBuyTapped(hull.id)
        RevenueCatManager.purchaseGemPack(activity, hull.productId) { ok ->
            if (ok) HangarAnalytics.hullPurchased(hull.id, hull.tier?.name ?: "")
            else HangarAnalytics.hullPurchaseFailed(hull.id)
            onResult(ok)
        }
    }

    /** Hull ids owned according to the store (call after any purchase/restore/launch). */
    fun ownedFrom(info: CustomerInfo): List<String> {
        val ids = info.allPurchasedProductIds.map { it.substringBefore(':') }
        val single = HullCatalog.SOLD.filter { it.productId in ids }.map { it.id }
        val bundle = if (HullCatalog.BUNDLE_APEX_PRODUCT in ids) HullCatalog.BUNDLE_APEX_IDS else emptyList()
        return (single + bundle).distinct()
    }

    fun buyApexBundle(activity: Activity, onResult: (Boolean) -> Unit) {
        HangarAnalytics.hullBuyTapped("bundle_apex")
        RevenueCatManager.purchaseGemPack(activity, HullCatalog.BUNDLE_APEX_PRODUCT) { ok ->
            if (ok) HangarAnalytics.hullPurchased("bundle_apex", "BUNDLE") else HangarAnalytics.hullPurchaseFailed("bundle_apex")
            onResult(ok)
        }
    }

    fun fetchOwned(onResult: (List<String>?) -> Unit) {
        try {
            Purchases.sharedInstance.getCustomerInfo(object : ReceiveCustomerInfoCallback {
                override fun onReceived(customerInfo: CustomerInfo) = onResult(ownedFrom(customerInfo))
                override fun onError(error: PurchasesError) = onResult(null)
            })
        } catch (e: Exception) { onResult(null) }
    }

    /** Restore flow that ALSO restores hulls (the old restorePurchases only handled Pro/ads). */
    fun restoreAll(onResult: (ok: Boolean, owned: List<String>) -> Unit) {
        try {
            Purchases.sharedInstance.restorePurchases(object : ReceiveCustomerInfoCallback {
                override fun onReceived(customerInfo: CustomerInfo) = onResult(true, ownedFrom(customerInfo))
                override fun onError(error: PurchasesError) = onResult(false, emptyList())
            })
        } catch (e: Exception) { onResult(false, emptyList()) }
    }
}
