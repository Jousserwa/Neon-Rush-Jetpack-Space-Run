package com.neonrush.game

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Key/value persistence for ALL new hull-system state (DB v21). One small table instead of ~25 new
 * GameProfile columns, so Stages 4-6 never need another migration and GameProfile's hand-written
 * read/write code stays untouched.
 *
 * Owned premium hulls are ALSO appended to GameProfile.unlockedSkinsCsv (see HullPurchases) so
 * existing equip code (purchaseSkin(id, 0)) and the share card/leaderboard keep working.
 */
class HangarStore(private val dbProvider: () -> SQLiteDatabase) {
    private val mutex = Mutex()

    companion object {
        const val CREATE_SQL = "CREATE TABLE IF NOT EXISTS hangar_kv (k TEXT PRIMARY KEY, v TEXT)"

        // keys
        const val K_OWNED = "owned_premium_csv"       // premium hull ids owned (source of truth, restored from store)
        const val K_ROTATION = "rotation_csv"         // ids included in rotation
        const val K_LOCK = "lock_hull_id"             // (legacy, unused)
        const val K_FREE_RIDE = "free_ride"           // "1" = rotate hulls each sector; default OFF = equipped hull stays on
        const val K_ROTATION_OUT = "rotation_out_csv" // owned hulls the player excluded from Free Ride
        const val K_LAST_PICKED = "last_picked_id"
        const val K_SECTOR_HULL = "current_sector_hull"
        const val K_FIRST_BUY_SEEN = "first_buy_offer_seen"
        // Stage 4+
        const val K_PRO_MONTHS = "pro_months_accrued"
        const val K_PRO_LAST_ACCRUAL = "pro_last_accrual_ms"
        const val K_PRO_SINCE = "pro_since_ms"
        const val K_PRO_ANNUAL = "pro_is_annual"
        const val K_YEAR_GRANTED = "year_edition_granted"
        const val K_LOANER_MONTH = "loaner_month_key"
        const val K_STIPEND_MONTH = "stipend_month_key"
        const val K_CHROMA = "chroma_by_hull_csv"     // hullId:variant
        const val K_MASTERY = "hull_mastery_csv"      // hullId:meters
        const val K_PASS_STATE = "season_pass_state"
        const val K_AURA_SEEN = "aura_rank_seen"
    }

    fun ensureTable() { dbProvider().execSQL(CREATE_SQL) }

    suspend fun get(key: String, default: String = ""): String = withContext(Dispatchers.IO) {
        dbProvider().rawQuery("SELECT v FROM hangar_kv WHERE k = ?", arrayOf(key)).use {
            if (it.moveToFirst()) it.getString(0) ?: default else default
        }
    }

    suspend fun put(key: String, value: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val cv = ContentValues().apply { put("k", key); put("v", value) }
            dbProvider().insertWithOnConflict("hangar_kv", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
        }
        Unit
    }

    suspend fun getLong(key: String, default: Long = 0L) = get(key).toLongOrNull() ?: default
    suspend fun putLong(key: String, v: Long) = put(key, v.toString())
    suspend fun getList(key: String): List<String> = get(key).split(",").filter { it.isNotBlank() }
    suspend fun putList(key: String, v: List<String>) = put(key, v.distinct().joinToString(","))
}
