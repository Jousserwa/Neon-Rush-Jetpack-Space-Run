package com.neonrush.game

import com.neonrush.game.db.GameDao
import android.util.Log
import com.neonrush.game.ui.HullFx
import com.revenuecat.purchases.CustomerInfo
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesError
import com.revenuecat.purchases.interfaces.ReceiveCustomerInfoCallback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.TimeZone

enum class AuraRank(val label: String, val emoji: String, val minMonths: Int) {
    NONE("", "", 0), BRONZE("Bronze", "🥉", 1), SILVER("Silver", "🥈", 3),
    GOLD("Gold", "🥇", 6), DIAMOND("Diamond", "💎", 12);

    companion object {
        /** Cumulative active Pro months. Annual subscribers start at Gold. */
        fun of(months: Int, annual: Boolean): AuraRank {
            val byMonths = values().lastOrNull { it != NONE && months >= it.minMonths } ?: NONE
            return if (annual && byMonths.ordinal < GOLD.ordinal) GOLD else byMonths
        }
    }
}

data class ProSnapshot(
    val isPro: Boolean = false,
    val isAnnual: Boolean = false,
    val months: Int = 0,
    val rank: AuraRank = AuraRank.NONE,
    val loanerHullId: String? = null,
    val stipendClaimable: Boolean = false,
    val chroma: Map<String, Int> = emptyMap(),
    val hasYearEdition: Boolean = false
) {
    /** LEGEND title + animated gradient name belong to annual members and holders of the Year hull. */
    val isLegend: Boolean get() = isAnnual || hasYearEdition
}

/**
 * Pro-only layer on top of the entitlement:
 *  - Aura Rank from cumulative ACTIVE months (server time; gaps over 35 days are not counted)
 *  - Year Edition hull for annual members ~7 days after the annual plan starts
 *  - Monthly Loaner Hull, Chroma recolors, monthly gem stipend
 *  - Magenta Pulse + Gold Transcendence free after 3 months of Pro
 * Everything that depends on time requires [ServerClock.isSynced]; offline = no new grants (safe).
 */
class ProPerks(
    private val dao: GameDao,
    private val store: HangarStore,
    private val scope: CoroutineScope,
    private val hangar: HangarController
) {
    companion object {
        const val ENTITLEMENT = "Neon Rush Pro"
        const val STIPEND_GEMS = 30
        const val YEAR_EDITION_DELAY_DAYS = 7
        const val FREE_GEM_HULLS_AFTER_MONTHS = 3
        private const val DAY = 86_400_000L
        private const val MONTH = 30 * DAY
        private const val MAX_GAP = 35 * DAY
        private const val K_FREE_GRANTED = "free_gem_hulls_granted"
    }

    private val _snap = MutableStateFlow(ProSnapshot())
    val snapshot: StateFlow<ProSnapshot> = _snap.asStateFlow()

    /** Call on launch, after purchases/restore, and when the Hangar opens. */
    fun refresh() {
        try {
            Purchases.sharedInstance.getCustomerInfo(object : ReceiveCustomerInfoCallback {
                override fun onReceived(customerInfo: CustomerInfo) { scope.launch { onInfo(customerInfo) } }
                override fun onError(error: PurchasesError) { Log.e("ProPerks", error.message) }
            })
        } catch (e: Exception) { Log.e("ProPerks", "${e.message}") }
    }

    private fun monthKey(ms: Long): String {
        val c = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = ms }
        return "%04d-%02d".format(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1)
    }

    private suspend fun onInfo(info: CustomerInfo) {
        val ent = info.entitlements[ENTITLEMENT]
        val active = ent?.isActive == true
        val annualNow = active && (ent?.productIdentifier?.contains("annual") == true)
        val synced = ServerClock.isSynced
        val now = ServerClock.nowMs()

        // ---- accrual
        var accrued = store.getLong(HangarStore.K_PRO_MONTHS)           // stored in ms
        var last = store.getLong(HangarStore.K_PRO_LAST_ACCRUAL)
        if (active && synced) {
            if (last > 0L) accrued += (now - last).coerceIn(0L, MAX_GAP)
            last = now
        } else if (!active) last = 0L
        store.putLong(HangarStore.K_PRO_MONTHS, accrued); store.putLong(HangarStore.K_PRO_LAST_ACCRUAL, last)
        val months = (accrued / MONTH).toInt()

        // ---- annual-since (for Year Edition timing)
        var annualSince = store.getLong(HangarStore.K_PRO_ANNUAL)
        if (annualNow && annualSince == 0L && synced) { annualSince = now; store.putLong(HangarStore.K_PRO_ANNUAL, now) }
        val everAnnual = annualSince > 0L
        val rank = if (active || months > 0) AuraRank.of(months, annualNow) else AuraRank.NONE

        // ---- Year Edition hull (earned, never sold)
        var hasYear = store.get(HangarStore.K_YEAR_GRANTED) == "1"
        if (!hasYear && annualNow && synced && now >= annualSince + YEAR_EDITION_DELAY_DAYS * DAY) {
            hangar.grantEarned("eternal_crown_2026"); store.put(HangarStore.K_YEAR_GRANTED, "1"); hasYear = true
        }

        // ---- Magenta Pulse + Gold Transcendence free after ~3 months
        if (active && months >= FREE_GEM_HULLS_AFTER_MONTHS && store.get(K_FREE_GRANTED) != "1") {
            dao.updateProfile { p ->
                val set = p.unlockedSkinsCsv.split(",").filter { it.isNotBlank() }.toMutableList()
                listOf("magenta_pulse", "gold_transcendence").forEach { if (it !in set) set.add(it) }
                p.copy(unlockedSkinsCsv = set.joinToString(","))
            }
            store.put(K_FREE_GRANTED, "1")
        }

        // ---- loaner + stipend (need server month)
        var loaner: String? = null
        var stipend = false
        if (active && synced) {
            val mk = monthKey(now)
            val owned = hangar.ownedPremium()
            val saved = store.get(HangarStore.K_LOANER_MONTH)           // "yyyy-MM:hullId"
            loaner = saved.takeIf { it.startsWith("$mk:") }?.substringAfter(':')?.takeIf { it !in owned }
            if (loaner == null && !saved.startsWith("$mk:")) {
                val pool = HullCatalog.SOLD.map { it.id }.filter { it !in owned }
                if (pool.isNotEmpty()) {
                    loaner = pool[(mk.hashCode().toLong().let { if (it < 0) -it else it } % pool.size).toInt()]
                    store.put(HangarStore.K_LOANER_MONTH, "$mk:$loaner")
                }
            }
            stipend = store.get(HangarStore.K_STIPEND_MONTH) != mk
        }

        // ---- chroma (Pro only; saved choices return if they re-subscribe)
        val chroma = parseChroma(store.get(HangarStore.K_CHROMA))
        val freeChroma = store.getList(SeasonPass.K_CHROMA_FREE)
        HullFx.chroma = if (active) chroma else chroma.filterKeys { it in freeChroma }

        _snap.value = ProSnapshot(active, annualNow || (active && everAnnual && annualNow), months, rank,
            loaner, stipend, chroma, hasYear)
        hangar.setPro(_snap.value)
    }

    fun claimStipend() { scope.launch {
        val s = _snap.value
        if (!s.isPro || !s.stipendClaimable || !ServerClock.isSynced) return@launch
        store.put(HangarStore.K_STIPEND_MONTH, monthKey(ServerClock.nowMs()))
        dao.updateProfile { it.copy(gems = it.gems + STIPEND_GEMS) }
        _snap.value = s.copy(stipendClaimable = false); hangar.setPro(_snap.value)
    } }

    fun setChroma(hullId: String, variant: Int) { scope.launch {
        val freeChroma = store.getList(SeasonPass.K_CHROMA_FREE)
        if (!_snap.value.isPro && hullId !in freeChroma) return@launch
        val m = _snap.value.chroma.toMutableMap().apply { if (variant == 0) remove(hullId) else put(hullId, variant.coerceIn(1, 3)) }
        store.put(HangarStore.K_CHROMA, m.entries.joinToString(",") { "${it.key}:${it.value}" })
        HullFx.chroma = m; _snap.value = _snap.value.copy(chroma = m); hangar.setPro(_snap.value)
    } }

    private fun parseChroma(s: String): Map<String, Int> =
        s.split(",").mapNotNull { e -> e.split(":").takeIf { it.size == 2 }?.let { it[0] to (it[1].toIntOrNull() ?: 0) } }.toMap()
}
