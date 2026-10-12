package com.neonrush.game

import com.neonrush.game.db.GameDao
import android.app.Activity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.tasks.await
import java.util.Calendar
import java.util.TimeZone

enum class RewardKind { GEMS, TITLE, BADGE, BACKDROP, CHROMA, HULL, BOOST }
data class PassReward(val kind: RewardKind, val value: String, val amount: Int = 0, val label: String)
data class PassTier(val tier: Int, val free: PassReward?, val premium: PassReward?, val proBonus: PassReward? = null)
data class SeasonConfig(val id: String, val startMs: Long, val endMs: Long, val hullId: String?)

data class SeasonPassState(
    val config: SeasonConfig? = null,
    val seasonName: String = "",
    /** Season Tokens collected so far (kept under the old name too, see [xp]). */
    val tokens: Int = 0, val tier: Int = 0, val tokensIntoTier: Int = 0,
    val premiumOwned: Boolean = false,
    val claimedFree: Set<Int> = emptySet(), val claimedPremium: Set<Int> = emptySet(), val claimedPro: Set<Int> = emptySet(),
    val priceLabel: String = SeasonPass.FALLBACK_PRICE, val proPriceLabel: String = SeasonPass.FALLBACK_PRO_PRICE,
    val isPro: Boolean = false, val msLeft: Long = 0L,
    val tiers: List<PassTier> = emptyList(), val inFlight: Boolean = false,
    /** Cosmetics earned from any season (title names, badge ids, backdrop ids). Permanent. */
    val cosmetics: Set<String> = emptySet(),
    val boostTokenRuns: Int = 0, val boostGemRuns: Int = 0,
    val lastRunTokens: Int = 0,
    val skipsLeft: Int = SeasonPass.MAX_SKIPS,
    val skipPriceLabel: String = SeasonPass.SKIP_FALLBACK_PRICE,
    val bundlePriceLabel: String = SeasonPass.BUNDLE_FALLBACK_PRICE
) {
    val xp get() = tokens
    val xpIntoTier get() = tokensIntoTier
}

/**
 * Trusted-as-possible time that ALSO works offline. Uses the Firestore server clock when it has synced, otherwise the
 * device clock, but never lets time go backwards (a persisted "latest time ever seen" floor), so turning the phone
 * clock back cannot extend a season. When the server clock is available it is authoritative and resets the floor.
 */
object SeasonClock {
    @Volatile var floorMs: Long = 0L
    fun now(): Long {
        if (ServerClock.isSynced) { floorMs = ServerClock.nowMs(); return floorMs }
        val t = maxOf(System.currentTimeMillis(), floorMs)
        floorMs = t
        return t
    }
}

/**
 * OFFLINE Season Pass (battle pass). Everything works with no internet:
 *  - Seasons are 30 days long. The active season is stored on the device; when it ends the next 30-day season starts
 *    automatically from a fixed calendar, so no server is needed. (If Firestore config/season is reachable at a
 *    season rollover it can supply the season id, dates and hull instead.)
 *  - Players collect Season Tokens during runs (gems, close calls, zones, bosses; shown live on the HUD) and they are
 *    banked when the run ends. 100 tokens = 1 tier, 20 tiers (+3 Pro-only tiers).
 *  - Rewards: cosmetics (titles, badges, backdrops, the season hull + Chroma), small gem amounts and BOOSTS
 *    (token x2 for the next run, +25% gems for the next runs). All claims are local.
 *  - Premium track $4.99 (Pro $3.99) is the only online step. Ownership is saved locally the instant the store confirms
 *    and a crash-proof copy is kept in [PurchaseLedger]; Firestore backup is best-effort.
 *
 * Products (Play Console CONSUMABLE so one product serves every season): neonrush_pass_season, neonrush_pass_season_pro.
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
        const val PRODUCT_SKIP = "neonrush_pass_skip"                 // consumable, +1 tier
        const val PRODUCT_BUNDLE = "neonrush_bundle_pass_apex"       // consumable: season premium track + Apex Trio hulls
        const val SKIP_FALLBACK_PRICE = "$0.99"
        const val BUNDLE_FALLBACK_PRICE = "$12.99"                   // pass 4.99 + trio 9.99 = 14.98
        const val MAX_SKIPS = 5                                      // per season (real-money purchase)
        const val TIERS = 20
        const val PRO_BONUS_TIERS = 3          // tiers 21..23, Pro only, cosmetic
        const val TOKENS_PER_TIER = 100
        const val XP_PER_TIER = TOKENS_PER_TIER
        const val SEASON_DAYS = 30
        const val MAX_RUN_TOKENS = 250
        const val K_COSMETICS = "pass_cosmetics_csv"
        const val K_CHROMA_FREE = "chroma_free_csv"
        private const val K_ACTIVE = "season_active_cfg"       // id|start|end|hull
        private const val K_FLOOR = "season_clock_floor"
        private const val K_BOOST_TOKEN = "boost_token_runs"
        private const val K_BOOST_GEM = "boost_gem_runs"
        private const val DAY_MS = 24L * 3600 * 1000
        private const val EPOCH_MS = 1767225600000L            // 2026-01-01 00:00 UTC, season #1 starts here

        // Token values per in-run event (see the onToken calls in the ViewModel).
        const val TOKENS_GEM = 1
        const val TOKENS_CLOSE_CALL = 2
        const val TOKENS_ZONE = 4
        const val TOKENS_BOSS = 25

        /** Tokens for a finished run: what was collected live + distance + personal-best bonus. */
        fun runTotal(collected: Int, distanceM: Int, newPb: Boolean): Int =
            (collected + distanceM / 400 + if (newPb) 15 else 0).coerceIn(5, MAX_RUN_TOKENS)

        /** Catch-up: players behind the expected pace earn more tokens so late joiners can still finish. */
        fun catchUp(tier: Int, startMs: Long, endMs: Long, nowMs: Long): Float {
            val span = (endMs - startMs).coerceAtLeast(1L)
            val expected = ((nowMs - startMs).toFloat() / span * TIERS).toInt()
            return when { expected - tier >= 6 -> 2f; expected - tier >= 3 -> 1.5f; else -> 1f }
        }

        /** Fixed 30-day calendar used whenever the server doesn't dictate the season. */
        fun localSeason(nowMs: Long): SeasonConfig {
            val len = SEASON_DAYS * DAY_MS
            val idx = ((nowMs - EPOCH_MS) / len).coerceAtLeast(0L)
            val start = EPOCH_MS + idx * len
            val sold = HullCatalog.SOLD
            val hull = if (sold.isEmpty()) null else sold[(idx % sold.size).toInt()].id
            return SeasonConfig("S${idx + 1}", start, start + len, hull)
        }

        fun buildTiers(seasonHull: String?): List<PassTier> = (1..TIERS + PRO_BONUS_TIERS).map { t ->
            if (t > TIERS) return@map PassTier(t, null, null,
                PassReward(if (t == TIERS + PRO_BONUS_TIERS) RewardKind.TITLE else RewardKind.BACKDROP,
                    if (t == TIERS + PRO_BONUS_TIERS) "Pro Elite" else "pro_bonus_${t}", label = if (t == TIERS + PRO_BONUS_TIERS) "Title: Pro Elite" else "Pro backdrop"))
            val free = when {
                t == 5 -> PassReward(RewardKind.BOOST, "token_x2", 1, "Token x2 boost")
                t == 14 -> PassReward(RewardKind.BOOST, "gem_boost", 2, "+25% gems boost")
                t % 3 == 0 -> PassReward(RewardKind.GEMS, "gems", 15, "15 💎")
                t == 10 -> PassReward(RewardKind.BADGE, "pass_badge", label = "Season badge")
                else -> null
            }
            val premium = when {
                t == TIERS && seasonHull != null -> PassReward(RewardKind.HULL, seasonHull, label = "Season hull + Chroma")
                t == TIERS -> PassReward(RewardKind.TITLE, "Season Champion", label = "Title: Season Champion")
                t == 15 -> PassReward(RewardKind.TITLE, "Season Racer", label = "Title: Season Racer")
                t == 8 || t == 18 -> PassReward(RewardKind.BOOST, "token_x2", 1, "Token x2 boost")
                t == 12 -> PassReward(RewardKind.BOOST, "gem_boost", 2, "+25% gems boost")
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
    private var lastFloorSave = 0L
    private var lastRunBanked = 0

    private val _runTokens = MutableStateFlow(0)
    /** Tokens collected in the CURRENT run (live HUD chip). Banked into the season when the run ends. */
    val runTokens: StateFlow<Int> = _runTokens.asStateFlow()
    @Volatile private var runTokenMult = 1
    /** Difficulty bonus (Hard 1.15, Legendary 1.3). Set by the ViewModel at run start. */
    @Volatile var tierTokenMult = 1f
    /** Gem multiplier for the current run (1.25 while a Gem boost is active). Read by the ViewModel at run end. */
    @Volatile var runGemMult = 1f
        private set

    private fun k(name: String) = "${name}_${cfg?.id}"

    fun start() = scope.launch {
        SeasonClock.floorMs = maxOf(SeasonClock.floorMs, store.getLong(K_FLOOR))
        cfg = activeConfig()
        recoverPendingPurchase()
        publish()                               // usable immediately, no network involved
        restoreBackup()                         // best-effort, never blocks the pass
        publish()
        while (true) {                          // keeps the countdown fresh and rolls into the next season by itself
            delay(60_000)
            val c = cfg
            if (c == null || SeasonClock.now() >= c.endMs) cfg = activeConfig()
            publish()
        }
    }

    // ---- season selection (offline first) --------------------------------------------------------------
    private fun parseCfg(raw: String): SeasonConfig? {
        val p = raw.split("|")
        if (p.size < 3) return null
        val st = p[1].toLongOrNull() ?: return null
        val en = p[2].toLongOrNull() ?: return null
        return SeasonConfig(p[0], st, en, p.getOrNull(3)?.ifBlank { null })
    }

    /** Keeps the saved season until it ends, so progress never jumps between server/local ids mid-season. */
    private suspend fun activeConfig(): SeasonConfig {
        val now = SeasonClock.now()
        store.get(K_ACTIVE).let { raw -> parseCfg(raw)?.let { if (now < it.endMs) return it } }
        val next = remoteConfig(now) ?: localSeason(now)
        store.put(K_ACTIVE, "${next.id}|${next.startMs}|${next.endMs}|${next.hullId ?: ""}")
        return next
    }

    private suspend fun remoteConfig(now: Long): SeasonConfig? = try {
        withTimeoutOrNull(3500) {
            val d = FirebaseFirestore.getInstance().collection("config").document("season").get().await()
            val id = d.getString("id")
            val st = d.getLong("startMs") ?: 0L
            val en = d.getLong("endMs") ?: 0L
            if (id != null && now in st until en) SeasonConfig(id, st, en, d.getString("hullId")) else null
        }
    } catch (_: Exception) { null }

    private suspend fun restoreBackup() {
        val c = cfg ?: return
        if (store.get(k("pass_owned")) == "1") return
        try {
            withTimeoutOrNull(4000) {
                val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return@withTimeoutOrNull
                val d = FirebaseFirestore.getInstance().collection("passes").document("${uid}_${c.id}").get().await()
                if (d.exists()) store.put(k("pass_owned"), "1")
            }
        } catch (_: Exception) {}
    }

    /** A pass purchase the store confirmed but the app never finished recording (killed mid-purchase). */
    private suspend fun recoverPendingPurchase() {
        if (cfg == null) return
        PurchaseLedger.undelivered().forEach { pend ->
            when (pend.product) {
                PRODUCT, PRODUCT_PRO -> { store.put(k("pass_owned"), "1"); PurchaseLedger.markDelivered(pend.tx) }
                PRODUCT_SKIP -> { addTierTokens(); PurchaseLedger.markDelivered(pend.tx) }
                PRODUCT_BUNDLE -> { grantBundle(); PurchaseLedger.markDelivered(pend.tx) }
            }
        }
    }

    private suspend fun publish() {
        val c = cfg
        val now = SeasonClock.now()
        if (now - lastFloorSave > 30_000) { lastFloorSave = now; store.putLong(K_FLOOR, SeasonClock.floorMs) }
        val tokens = store.getLong(k("pass_xp")).toInt()
        val tier = (tokens / TOKENS_PER_TIER).coerceAtMost(TIERS + PRO_BONUS_TIERS)
        val prices = HullPurchases.prices.value
        _state.value = SeasonPassState(
            config = c,
            seasonName = c?.id?.let { if (it.startsWith("S") && it.drop(1).all(Char::isDigit)) "SEASON ${it.drop(1)}" else it } ?: "",
            tokens = tokens, tier = tier,
            tokensIntoTier = if (tier >= TIERS + PRO_BONUS_TIERS) TOKENS_PER_TIER else tokens % TOKENS_PER_TIER,
            premiumOwned = store.get(k("pass_owned")) == "1",
            claimedFree = store.getList(k("pass_cf")).mapNotNull { it.toIntOrNull() }.toSet(),
            claimedPremium = store.getList(k("pass_cp")).mapNotNull { it.toIntOrNull() }.toSet(),
            claimedPro = store.getList(k("pass_cb")).mapNotNull { it.toIntOrNull() }.toSet(),
            priceLabel = prices[PRODUCT] ?: FALLBACK_PRICE, proPriceLabel = prices[PRODUCT_PRO] ?: FALLBACK_PRO_PRICE,
            isPro = isPro(), msLeft = c?.let { (it.endMs - now).coerceAtLeast(0L) } ?: 0L,
            tiers = buildTiers(c?.hullId),
            cosmetics = store.getList(K_COSMETICS).toSet(),
            boostTokenRuns = store.getLong(K_BOOST_TOKEN).toInt(), boostGemRuns = store.getLong(K_BOOST_GEM).toInt(),
            lastRunTokens = lastRunBanked,
            skipsLeft = (MAX_SKIPS - store.getLong(k("pass_skips")).toInt()).coerceAtLeast(0),
            skipPriceLabel = prices[PRODUCT_SKIP] ?: SKIP_FALLBACK_PRICE,
            bundlePriceLabel = prices[PRODUCT_BUNDLE] ?: BUNDLE_FALLBACK_PRICE
        )
    }

    fun refresh() { scope.launch { publish() } }

    // ---- tokens ------------------------------------------------------------------------------------------
    /** New run: clear the live counter and spend any active boosts (they apply to this run). */
    fun onRunStart() {
        _runTokens.value = 0
        runTokenMult = 1; runGemMult = 1f
        scope.launch {
            val t = store.getLong(K_BOOST_TOKEN).toInt()
            if (t > 0) { store.putLong(K_BOOST_TOKEN, (t - 1).toLong()); runTokenMult = 2 }
            val g = store.getLong(K_BOOST_GEM).toInt()
            if (g > 0) { store.putLong(K_BOOST_GEM, (g - 1).toLong()); runGemMult = 1.25f }
            publish()
        }
    }

    /** A token was earned mid-run (gem / close call / new zone / boss). Instant, no disk, no network. */
    fun onToken(n: Int) {
        if (n <= 0) return
        _runTokens.value = (_runTokens.value + n).coerceAtMost(MAX_RUN_TOKENS)
    }

    /** Call at game over: banks this run's tokens into the season (works offline). */
    fun onRunEnd(distanceM: Int, zone: Int, newPb: Boolean) {
        val collected = _runTokens.value
        _runTokens.value = 0
        scope.launch {
            val c = cfg ?: activeConfig().also { cfg = it }
            val have = store.getLong(k("pass_xp")).toInt()
            val tier = have / TOKENS_PER_TIER
            val base = (runTotal(collected * runTokenMult, distanceM, newPb) * tierTokenMult).toInt()
            val gain = (base * catchUp(tier, c.startMs, c.endMs, SeasonClock.now())).toInt()
            lastRunBanked = gain
            store.putLong(k("pass_xp"), (have + gain).coerceAtMost((TIERS + PRO_BONUS_TIERS) * TOKENS_PER_TIER).toLong())
            runTokenMult = 1
            publish()
        }
    }

    // ---- purchase ----------------------------------------------------------------------------------------
    fun buy(activity: Activity) {
        val c = cfg ?: return
        val product = if (isPro()) PRODUCT_PRO else PRODUCT
        _state.value = _state.value.copy(inFlight = true)
        HangarAnalytics.hullBuyTapped("season_pass")
        RevenueCatManager.purchaseGemPack(activity, product) { ok ->
            scope.launch {
                if (ok) {
                    store.put(k("pass_owned"), "1")
                    PurchaseLedger.markDeliveredByProduct(product)
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

    // ---- extra revenue paths -----------------------------------------------------------------------------
    /** Moves the player to the start of the next tier. Counts toward [MAX_SKIPS] for the season. */
    private suspend fun addTierTokens() {
        val have = store.getLong(k("pass_xp")).toInt()
        val cap = (TIERS + PRO_BONUS_TIERS) * TOKENS_PER_TIER
        val next = ((have / TOKENS_PER_TIER) + 1) * TOKENS_PER_TIER
        store.putLong(k("pass_xp"), next.coerceAtMost(cap).toLong())
        store.putLong(k("pass_skips"), store.getLong(k("pass_skips")) + 1)
        publish()
    }

    /** Real-money skip (consumable $0.99). */
    fun skipWithMoney(activity: Activity) {
        val s = _state.value
        if (s.skipsLeft <= 0 || s.tier >= TIERS) return
        HangarAnalytics.passSkip("money_tap")
        RevenueCatManager.purchaseGemPack(activity, PRODUCT_SKIP) { ok ->
            scope.launch { if (ok) { addTierTokens(); PurchaseLedger.markDeliveredByProduct(PRODUCT_SKIP); HangarAnalytics.passSkip("money") } }
        }
    }

    private suspend fun grantBundle() {
        store.put(k("pass_owned"), "1")
        HullCatalog.BUNDLE_APEX_IDS.forEach { hangar.grantEarned(it) }
        publish()
    }

    /** Season premium track + the Apex Trio hulls in one purchase (cheaper than buying both). */
    fun buyBundle(activity: Activity) {
        // Safety net: never sell the bundle to someone who already owns all three hulls (the UI hides it too).
        if (HullCatalog.BUNDLE_APEX_IDS.all { it in hangar.ownedPremium() }) return
        if (!hangar.bundleBuyable()) return   // trio not fully released yet
        _state.value = _state.value.copy(inFlight = true)
        HangarAnalytics.passSkip("bundle_tap")
        RevenueCatManager.purchaseGemPack(activity, PRODUCT_BUNDLE) { ok ->
            scope.launch {
                if (ok) { grantBundle(); PurchaseLedger.markDeliveredByProduct(PRODUCT_BUNDLE); HangarAnalytics.hullPurchased("pass_bundle", "BUNDLE") }
                publish()
            }
        }
    }

    // ---- claiming (all local) ----------------------------------------------------------------------------
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
            RewardKind.BOOST -> {
                val bk = if (reward.value == "gem_boost") K_BOOST_GEM else K_BOOST_TOKEN
                store.putLong(bk, store.getLong(bk) + reward.amount)
            }
            else -> store.putList(K_COSMETICS, (store.getList(K_COSMETICS) + reward.value).distinct())
        }
        publish()
    } }
}

/** Where and when to nudge the player toward the premium track. Pure functions, easy to tune. */
object PassOffers {
    data class Offer(val headline: String, val detail: String)

    /** Game-over screen. Null = no nudge (already premium, season over, or nothing compelling to say). */
    fun postRun(s: SeasonPassState): Offer? {
        if (s.config == null || s.premiumOwned || s.msLeft <= 0 || s.tier >= SeasonPass.TIERS) return null
        val waiting = s.tiers.count { it.premium != null && it.tier <= s.tier && it.tier !in s.claimedPremium }
        val toNext = SeasonPass.TOKENS_PER_TIER - s.tokensIntoTier
        val next = s.tiers.firstOrNull { it.tier == s.tier + 1 }?.premium
        return when {
            waiting >= 2 -> Offer("🔒 $waiting premium rewards are waiting for you", "You already earned them. Unlock the premium track to claim them all.")
            toNext <= 20 && next != null -> Offer("🎟 Only $toNext tokens to tier ${s.tier + 1}", "Premium reward there: ${next.label}. Unlock the premium track to claim it.")
            else -> null
        }
    }

    /** Home screen, last days of a season. */
    fun endOfSeason(s: SeasonPassState): String? {
        if (s.config == null || s.msLeft <= 0 || s.tier >= SeasonPass.TIERS) return null
        val days = ((s.msLeft + 86_399_999L) / 86_400_000L).toInt()
        if (days > 5) return null
        val left = SeasonPass.TIERS - s.tier
        val hull = s.config.hullId?.let { HullCatalog.byId(it) }
        val prize = if (hull != null) "${hull.emoji} ${hull.name}" else "the Season Champion title"
        return if (left <= 5) "⏳ $days day${if (days == 1) "" else "s"} left: only $left tier${if (left == 1) "" else "s"} from $prize"
        else "⏳ Season ends in $days day${if (days == 1) "" else "s"}: $left tiers to go for $prize"
    }
}
