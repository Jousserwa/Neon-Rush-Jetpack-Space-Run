package com.neonrush.game

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import com.revenuecat.purchases.CustomerInfo
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Secure local record of what the player has paid for, so purchases keep working with no internet.
 *
 *  - Stored in the app's private files dir, encrypted with AES-256-GCM using a key that lives in the
 *    Android Keystore (the key never leaves the secure hardware/TEE and is not readable by the app or user).
 *    GCM is authenticated: if the file is edited or corrupted it fails to open and is treated as empty
 *    (the next online sync rebuilds it from the store), so it cannot be tampered with to fake a purchase.
 *  - It is a CACHE of the store's truth (RevenueCat/Google Play receipts stay with Google and RevenueCat; the raw
 *    receipt is never needed on the device). Online, every CustomerInfo refreshes it. Offline, it answers
 *    "is this player Pro / do they own X" from the last verified state.
 *  - Pro is honoured until its expiry date + [OFFLINE_GRACE_MS]. A "latest time ever seen" clock guard stops the
 *    expiry from being dodged by turning the device clock backwards.
 *  - Consumables (gem packs) are written to a pending list the moment the store confirms payment, BEFORE the gems
 *    are granted, and marked delivered afterwards. If the app is killed in between, the gems are delivered on the
 *    next launch ([undelivered]) instead of being lost.
 */
object PurchaseLedger {
    private const val TAG = "PurchaseLedger"
    private const val FILE = "purchase_ledger.dat"
    private const val ALIAS = "neonrush_purchase_ledger_v1"
    const val OFFLINE_GRACE_MS = 3L * 24 * 3600 * 1000
    private const val FOREVER = Long.MAX_VALUE / 4

    data class Pending(val tx: String, val product: String, val gems: Int, val at: Long, val delivered: Boolean)

    private val lock = Any()
    private var dir: File? = null
    private var proUntilMs = 0L
    private var adsRemoved = false
    private var proAnnual = false
    private var lastSeenMs = 0L
    private var lastSyncMs = 0L
    private val products = linkedSetOf<String>()
    private val pending = mutableListOf<Pending>()

    fun init(context: Context) {
        synchronized(lock) {
            if (dir != null) return
            dir = context.applicationContext.filesDir
            load()
        }
    }

    // ---------- reads (safe to call from anywhere, work fully offline) ----------
    fun isProCached(): Boolean = synchronized(lock) {
        proUntilMs > 0 && maxOf(System.currentTimeMillis(), lastSeenMs) <= saturatingAdd(proUntilMs, OFFLINE_GRACE_MS)
    }
    fun isAdsRemovedCached(): Boolean = synchronized(lock) { adsRemoved }
    /** True when the active Pro entitlement came from the annual plan (cached, so it works offline). */
    fun isProAnnualCached(): Boolean = synchronized(lock) { proAnnual }
    /** Store product ids (base id, no ":plan" suffix) the player has bought. */
    fun ownedProducts(): Set<String> = synchronized(lock) { products.toSet() }
    fun lastSyncMs(): Long = synchronized(lock) { lastSyncMs }
    fun undelivered(): List<Pending> = synchronized(lock) { pending.filter { !it.delivered } }

    // ---------- writes ----------
    /** Call with every CustomerInfo the store gives us (launch, purchase, restore, background update). */
    fun onCustomerInfo(info: CustomerInfo) {
        synchronized(lock) {
            val pro = info.entitlements.active["Neon Rush Pro"]
            proUntilMs = if (pro == null) 0L else (pro.expirationDate?.time ?: FOREVER)
            proAnnual = pro != null && pro.productIdentifier.let { it.contains("annual", ignoreCase = true) || it.contains("year", ignoreCase = true) }
            val ids = info.allPurchasedProductIds.map { it.substringBefore(':') }
            products.addAll(ids)
            adsRemoved = info.entitlements.active.containsKey("remove_ads") || RevenueCatManager.PRODUCT_ID_REMOVE_ADS in products
            lastSyncMs = System.currentTimeMillis()
            save()
        }
    }

    /** The store confirmed payment for a consumable. Persist it before granting anything. */
    fun recordPending(tx: String, product: String, gems: Int) {
        synchronized(lock) {
            if (pending.any { it.tx == tx }) return
            pending.add(Pending(tx, product, gems, System.currentTimeMillis(), false))
            while (pending.size > 50) pending.removeAt(0)   // keep the file small; only undelivered matter
            save()
        }
    }

    /** The normal purchase callback granted the gems; mark the newest matching pending entry as delivered. */
    fun markDeliveredByProduct(product: String) {
        synchronized(lock) {
            val i = pending.indexOfLast { it.product == product && !it.delivered }
            if (i >= 0) { pending[i] = pending[i].copy(delivered = true); save() }
        }
    }
    fun markDelivered(tx: String) {
        synchronized(lock) {
            val i = pending.indexOfFirst { it.tx == tx }
            if (i >= 0) { pending[i] = pending[i].copy(delivered = true); save() }
        }
    }

    // ---------- storage ----------
    private fun saturatingAdd(a: Long, b: Long) = if (a > Long.MAX_VALUE - b) Long.MAX_VALUE else a + b

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    private fun save() {
        val d = dir ?: return
        try {
            lastSeenMs = maxOf(lastSeenMs, System.currentTimeMillis())
            val j = JSONObject()
                .put("v", 1).put("proUntil", proUntilMs).put("ads", adsRemoved).put("annual", proAnnual)
                .put("seen", lastSeenMs).put("sync", lastSyncMs)
                .put("products", JSONArray(products.toList()))
                .put("pending", JSONArray(pending.map {
                    JSONObject().put("tx", it.tx).put("p", it.product).put("g", it.gems).put("at", it.at).put("d", it.delivered)
                }))
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.ENCRYPT_MODE, key())
            val out = c.iv + c.doFinal(j.toString().toByteArray(Charsets.UTF_8))
            val tmp = File(d, "$FILE.tmp")
            tmp.writeBytes(out)
            if (!tmp.renameTo(File(d, FILE))) { File(d, FILE).delete(); tmp.renameTo(File(d, FILE)) }
        } catch (e: Exception) { Log.e(TAG, "save failed: ${e.message}") }
    }

    private fun load() {
        val d = dir ?: return
        val f = File(d, FILE)
        if (!f.exists()) return
        try {
            val raw = f.readBytes()
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, raw, 0, 12))
            val j = JSONObject(String(c.doFinal(raw, 12, raw.size - 12), Charsets.UTF_8))
            proUntilMs = j.optLong("proUntil", 0L)
            adsRemoved = j.optBoolean("ads", false)
            proAnnual = j.optBoolean("annual", false)
            lastSeenMs = j.optLong("seen", 0L)
            lastSyncMs = j.optLong("sync", 0L)
            j.optJSONArray("products")?.let { a -> for (i in 0 until a.length()) products.add(a.getString(i)) }
            j.optJSONArray("pending")?.let { a ->
                for (i in 0 until a.length()) a.getJSONObject(i).let {
                    pending.add(Pending(it.getString("tx"), it.getString("p"), it.optInt("g"), it.optLong("at"), it.optBoolean("d")))
                }
            }
        } catch (e: Exception) {
            // Tampered, corrupted, or the keystore key is gone (reinstall / restored backup): start empty.
            // The next online CustomerInfo rebuilds everything from the store.
            Log.w(TAG, "ledger unreadable, starting empty: ${e.message}")
            f.delete()
        }
    }
}
