package com.neonrush.game

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * STARTER PACK ($0.99, 700 gems) can be bought ONCE. It stays in the shop until it is bought, then it is gone for good.
 * The gems go straight into the normal balance (they never expire; they only go down when the player spends them).
 * "Bought" is remembered on the phone and also read from the store's owned products, so a restore does not bring the offer back.
 * [version] changes when it is bought so the shop redraws without the offer.
 */
object StarterPack {
    var version by mutableStateOf(0)
    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences("neonrush_starterpack", Context.MODE_PRIVATE)

    fun bought(ctx: Context): Boolean = try {
        @Suppress("UNUSED_VARIABLE") val v = version
        prefs(ctx).getBoolean("bought", false) ||
            RevenueCatManager.PRODUCT_ID_STARTER_PACK in PurchaseLedger.ownedProducts() ||
            RevenueCatManager.PRODUCT_ID_STARTER_PACK in RevenueCatManager.ownedProducts.value
    } catch (_: Throwable) { false }

    fun markBought(ctx: Context) {
        try { prefs(ctx).edit().putBoolean("bought", true).apply() } catch (_: Throwable) { }
        version += 1
    }
}
