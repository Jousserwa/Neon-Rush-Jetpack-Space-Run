package com.neonrush.game

import com.neonrush.game.db.GameDao
import android.app.Activity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Calendar
import java.util.TimeZone

enum class RewardKind { GEMS, TITLE, BADGE, BACKDROP, CHROMA, HULL }
data class PassReward(val kind: RewardKind, val value: String, val amount: Int = 0, val label: String)
data class PassTier(val tier: Int, val free: PassReward?, val premium: PassReward?, val proBonus: PassReward? = null)
data class SeasonConfig(val id: String, val startMs: Long, val endMs: Long, val hullId: String?)

data class SeasonPassState(
    val config: SeasonConfig? = null,
    val xp: Int = 0, val tier: Int = 0, val xpIntoTier: Int = 0,
    val premiumOwned: Boolean = false,
    val claimedFree: Set<Int> = emptySet(), val claimedPremium: Set<Int> = emptySet(), val claimedPro: Set<Int> = emptySet(),
    val priceLabel: String = SeasonPass.FALLBACK_PRICE, val proPriceLabel: String = SeasonPass.FALLBACK_PRO_PRICE,
    val isPro: Boolean = false, val msLeft: Long = 0L,
    val tiers: List<PassTier> = emptyList(), val inFlight: Boolean = false
)

/**
 * 30-day Season Hull Pass. Free track for everyone, premium track $4.99 (Pro price $3.99, NEVER free for Pro).
 * Final premium reward = the season's hull + Chroma. The hull stays buyable in the Vault, so the pass is a
 * better-value path, not a FOMO wall. Rewards are cosmetic or small gems so it can't undercut gem-pack sales.
 *
 * Products (Play Console CONSUMABLE, so one product serves every season; no monthly product creation):
 *   neonrush_pass_season (4.99), neonrush_pass_season_pro (3.99).
 * Consumables are not restorable by the store, so purchase is also backed up to Firestore /passes/{uid}_{season}.
 */
class SeasonPass(
    private val dao: GameDao,
    private val store: HangarStore,
    private val scope: CoroutineScope,
    private val hangar: HangarController,
    private val isPro: () -> Boolean
) {
    companion object {
        const val PRODUCT = "neonrush_pass_season"
        const val PRODUCT_PRO = "neonrush_pass_season_pro"
        const val FALLBACK_PRICE = "$4.99"
        const val FALLBACK_PRO_PRICE = "$3.99"
        const val TIERS = 20
        const val PRO_BONUS_TIERS = 3          // tiers 21..23, Pro only, cosmetic
        const val XP_PER_TIER = 100
        const val K_COSMETICS = "pass_cosmetics_csv"
        const val K_CHROMA_FREE = "chroma_free_csv"

        fun xpForRun(distanceM: Int, zone: Int, newPb: Boolean): Int =
            (10 + zone * 4 + distanceM / 200 + if (newPb) 15 else 0).coerceAtMost(120)

        /** Catch-up: players behind the expected pace earn more XP so late joiners can still finish. */
        fun catchUp(tier: Int, startMs: Long, endMs: Long, nowMs: Long): Float {
            val span = (endMs - startMs).coerceAtLeast(1L)
            val expected = ((nowMs - startMs).toFloat() / span * TIERS).toInt()
            return when { expected - tier >= 6 -> 2f; expected - tier >= 3 -> 1.5f; else -> 1f }
        }

        fun buildTiers(seasonHull: String?): List<PassTier> = (1..TIERS + PRO_BONUS_TIERS).map { t ->
            if (t > TIERS) return@map PassTier(t, null, null,
                PassReward(if (t == TIERS + PRO_BONUS_TIERS) RewardKind.TITLE else RewardKind.BACKDROP,
                    if (t == TIERS + PRO_BONUS_TIERS) "Pro Elite" else "pro_bonus_${t}", label = if (t == TIERS + PRO_BONUS_TIERS) "Title: Pro Elite" else "Pro backdrop"))
            val free = when {
                t % 3 == 0 -> PassReward(RewardKind.GEMS, "gems", 15, "15 💎")
                t == 10 -> PassReward(RewardKind.BADGE, "pass_badge", label = "Season badge")
                else -> null
            }
            val premium = when {
                t == TIERS && seasonHull != null -> PassReward(RewardKind.HULL, seasonHull, label = "Season hull + Chroma")
                t == TIERS -> PassReward(RewardKind.TITLE, "Season Champion", label = "Title: Season Champion")
                t == 15 -> PassReward(RewardKind.TITLE, "Season Racer", label = "Title: Season Racer")
                t % 5 == 0 -> PassReward(RewardKind.BACKDROP, "season_bd_$t", label = "Backdrop")
                t % 2 == 0 -> PassReward(RewardKind.GEMS, "gems", 10, "10 💎")
                else -> PassReward(RewardKind.BADGE, "season_badge_$t", label = "Badge")
            }
            PassTier(t, free, premium)
        }
    }

    private val _state = MutableStateFlow(SeasonPassState())
    val state: StateFlow<SeasonPassState> = _state.asStateFlow()
    private var cfg: SeasonConfig? = null

    private fun k(name: String) = "${name}_${cfg?.id}"

    fun start() = scope.launch {
        cfg = loadConfig()
        restoreBackup()
        publish()
    }

    private suspend fun loadConfig(): SeasonConfig? {
        try {
            val d = FirebaseFirestore.getInstance().collection("config").document("season").get().await()
            val id = d.getString("id")
            if (id != null) return SeasonConfig(id, d.getLong("startMs") ?: 0L, d.getLong("endMs") ?: 0L, d.getString("hullId"))
        } catch (_: Exception) {}
        if (!ServerClock.isSynced) return null
        val now = ServerClock.nowMs()
        val c = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = now }
        val id = "%04d-%02d".format(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1)
        c.set(Calendar.DAY_OF_MONTH, 1); c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
        val start = c.timeInMillis; c.add(Calendar.MONTH, 1)
        return SeasonConfig(id, start, c.timeInMillis, null)
    }

    private suspend fun restoreBackup() {
        val c = cfg ?: return
        if (store.get(k("pass_owned")) == "1") return
        try {
            val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
            val d = FirebaseFirestore.getInstance().collection("passes").document("${uid}_${c.id}").get().await()
            if (d.exists()) store.put(k("pass_owned"), "1")
        } catch (_: Exception) {}
    }

    private suspend fun publish() {
        val c = cfg
        val xp = store.getLong(k("pass_xp")).toInt()
        val tier = (xp / XP_PER_TIER).coerceAtMost(TIERS + PRO_BONUS_TIERS)
        val prices = HullPurchases.prices.value
        _state.value = SeasonPassState(
            c, xp, tier, if (tier >= TIERS + PRO_BONUS_TIERS) XP_PER_TIER else xp % XP_PER_TIER,
            store.get(k("pass_owned")) == "1",
            store.getList(k("pass_cf")).mapNotNull { it.toIntOrNull() }.toSet(),
            store.getList(k("pass_cp")).mapNotNull { it.toIntOrNull() }.toSet(),
            store.getList(k("pass_cb")).mapNotNull { it.toIntOrNull() }.toSet(),
            prices[PRODUCT] ?: FALLBACK_PRICE, prices[PRODUCT_PRO] ?: FALLBACK_PRO_PRICE,
            isPro(), c?.let { (it.endMs - ServerClock.nowMs()).coerceAtLeast(0L) } ?: 0L,
            buildTiers(c?.hullId)
        )
    }

    fun refresh() { scope.launch { publish() } }

    /** Call at run end. */
    fun onRunEnd(distanceM: Int, zone: Int, newPb: Boolean) { scope.launch {
        val c = cfg ?: return@launch
        if (!ServerClock.isSynced) return@launch
        val xp = store.getLong(k("pass_xp")).toInt()
        val tier = xp / XP_PER_TIER
        val gain = (xpForRun(distanceM, zone, newPb) * catchUp(tier, c.startMs, c.endMs, ServerClock.nowMs())).toInt()
        store.putLong(k("pass_xp"), (xp + gain).coerceAtMost((TIERS + PRO_BONUS_TIERS) * XP_PER_TIER).toLong())
        publish()
    } }

    fun buy(activity: Activity) {
        val c = cfg ?: return
        val product = if (isPro()) PRODUCT_PRO else PRODUCT
        _state.value = _state.value.copy(inFlight = true)
        HangarAnalytics.hullBuyTapped("season_pass")
        RevenueCatManager.purchaseGemPack(activity, product) { ok ->
            scope.launch {
                if (ok) {
                    store.put(k("pass_owned"), "1")
                    try {
                        val uid = FirebaseAuth.getInstance().currentUser?.uid
                        if (uid != null) FirebaseFirestore.getInstance().collection("passes").document("${uid}_${c.id}")
                            .set(mapOf("product" to product, "t" to System.currentTimeMillis()))
                    } catch (_: Exception) {}
                    HangarAnalytics.hullPurchased("season_pass", product)
                }
                publish()
            }
        }
    }

    /** track: "free", "premium" or "pro". */
    fun claim(tier: Int, track: String) { scope.launch {
        val s = _state.value
        val t = s.tiers.firstOrNull { it.tier == tier } ?: return@launch
        if (s.tier < tier) return@launch
        val (reward, key, set) = when (track) {
            "free" -> Triple(t.free, "pass_cf", s.claimedFree)
            "premium" -> { if (!s.premiumOwned) return@launch; Triple(t.premium, "pass_cp", s.claimedPremium) }
            else -> { if (!s.isPro) return@launch; Triple(t.proBonus, "pass_cb", s.claimedPro) }
        }
        if (reward == null || tier in set) return@launch
        store.putList(k(key), (set + tier).map { it.toString() })
        when (reward.kind) {
            RewardKind.GEMS -> dao.updateProfile { it.copy(gems = it.gems + reward.amount) }
            RewardKind.HULL -> {
                hangar.grantEarned(reward.value)
                store.putList(K_CHROMA_FREE, store.getList(K_CHROMA_FREE) + reward.value)  // chroma for the season hull, even without Pro
            }
            else -> store.putList(K_COSMETICS, store.getList(K_COSMETICS) + reward.value)
        }
        publish()
    } }
}
