package com.neonrush.game

import com.neonrush.game.db.GameDao
import android.app.Activity
import android.content.Context
import com.google.firebase.firestore.FirebaseFirestore
import com.neonrush.game.ui.HangarActions
import com.neonrush.game.ui.HangarUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Glue between Hangar UI, store, purchases, rotation and the profile.
 * ViewModel owns one instance:  val hangar = HangarController(gameDao, HangarStore { dbHelper.writableDatabase }, viewModelScope)
 *
 * Rotation never changes profile.activeSkinId (that is the hull the player PICKED, the "last picked" one).
 * During a run the canvas draws [sectorHullId] ?: profile.activeSkinId.
 */
class HangarController(
    private val dao: GameDao,
    private val store: HangarStore,
    private val scope: CoroutineScope,
    private val totalGemHullCount: Int,
    private val gemHulls: List<Pair<String, String>> = emptyList()   // (id, name) of the gem-priced hulls
) {
    /** Set by the ViewModel: perks.setChroma / perks.claimStipend. */
    var onChroma: (String, Int) -> Unit = { _, _ -> }
    var onStipend: () -> Unit = {}

    private val _ui = MutableStateFlow<HangarUiState?>(null)
    val ui: StateFlow<HangarUiState?> = _ui.asStateFlow()

    /** Hull to draw right now during a run (null = use the equipped one). */
    private val _sectorHullId = MutableStateFlow<String?>(null)
    val sectorHullId: StateFlow<String?> = _sectorHullId.asStateFlow()

    /** One-shot toast text for the racing screen: "Switched to Cosmic Koi". */
    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast.asStateFlow()
    fun consumeToast() { _toast.value = null }

    private var owned: Set<String> = emptySet()
    private var rotationOut: Set<String> = emptySet()   // owned hulls excluded from Free Ride
    private var freeRide = false                          // default OFF: the equipped hull stays on permanently
    private var locked: String? = null
    private var lastPicked: String? = null
    private var nextRoll: String? = null
    private var releaseAt: Map<String, Long> = emptyMap()
    private var nextDropAt: Long = 0L
    private var inFlight: String? = null
    private var celebrate: String? = null
    private var currentSector = 0
    private var pro = ProSnapshot()
    private var pendingTrial: String? = null
    var progression: HullProgression? = null
    private var lastMarkM = 0
    private fun currentHull(equipped: String) = _sectorHullId.value ?: equipped

    fun onGem(equipped: String, n: Int = 1) { progression?.onGem(currentHull(equipped), n) }
    fun onCloseCall(equipped: String) { progression?.onCloseCall(currentHull(equipped)) }
    /** Run over (game over): credit the last stretch of distance, flush counters. */
    fun onRunEnd(distanceM: Int, equipped: String) {
        progression?.creditDistance(currentHull(equipped), distanceM - lastMarkM); lastMarkM = distanceM
        progression?.flush()
    }

    fun ownedPremium(): Set<String> = owned
    fun setPro(p: ProSnapshot) { pro = p; scope.launch { publish() } }
    /** Earned-only grants (Year Edition). Not equipped automatically. */
    fun grantEarned(id: String) { scope.launch { grant(listOf(id), equip = false); publish() } }

    fun start() = scope.launch {
        store.ensureTable()
        owned = store.getList(HangarStore.K_OWNED).toSet()
        rotationOut = store.getList(HangarStore.K_ROTATION_OUT).toSet()
        freeRide = store.get(HangarStore.K_FREE_RIDE) == "1"
        locked = store.get(HangarStore.K_LOCK).ifBlank { null }
        lastPicked = store.get(HangarStore.K_LAST_PICKED).ifBlank { null }
        reconcileFromLedger()       // offline-safe: paid hulls come back even with no internet
        reconcileWithStore()
        loadSchedule()
        loadAlertSeen()
        notifyOn = store.get("drop_notify") == "1"
        val dsRaw = store.get("dot_seen_csv")
        dotSeen = if (dsRaw.isBlank()) alertSeen else dsRaw.split(",").filter { it.isNotBlank() }.toSet()
        if (dsRaw.isBlank()) store.put("dot_seen_csv", (dotSeen + "init").joinToString(","))
        enforceEquipped()
        publish()
        while (true) { delay(30_000); publish() }      // keeps countdowns fresh
    }

    /** Grant hulls the secure local ledger says were bought (works with zero connectivity). Idempotent and silent. */
    fun reconcileFromLedger() {
        scope.launch {
            val ids = HullPurchases.ownedFromProductIds(PurchaseLedger.ownedProducts())
            if (ids.isNotEmpty()) { grant(ids, equip = false); publish() }
        }
    }

    /** Pull ownership from RevenueCat so reinstall / new device / refunds are handled. */
    fun reconcileWithStore() = HullPurchases.fetchOwned { ids -> if (ids != null) scope.launch { grant(ids, equip = false); publish() } }

    private suspend fun loadSchedule() {
        try {
            val doc = FirebaseFirestore.getInstance().collection("config").document("hull_drops").get().await()
            @Suppress("UNCHECKED_CAST")
            val m = (doc.get("releaseAt") as? Map<String, Number>)?.mapValues { it.value.toLong() } ?: emptyMap()
            releaseAt = m
            nextDropAt = doc.getLong("nextDropAt") ?: 0L
        } catch (_: Exception) { /* offline: launch set only */ }
    }

    /** Remote override + built-in monthly calendar (so future hulls stay locked even when offline). */
    private fun releaseMap(): Map<String, Long> = HullCatalog.releaseMap(releaseAt)

    /** Apex Trio bundle is only sellable once all three hulls are sellable to this player. */
    fun bundleBuyable(): Boolean =
        HullCatalog.canBuyNow(isPro(), HullCatalog.bundleReleaseMs(releaseMap()), ServerClock.bestNowMs())

    /** Set by the ViewModel so drop notifications can be scheduled. */
    var appContext: Context? = null
    private var notifyOn = false
    private var dotSeen: Set<String> = emptySet()

    private fun nowMs() = ServerClock.bestNowMs()

    fun founderBuyable(ownedAll: Set<String> = (dao.getProfileFlow().value?.unlockedSkinsCsv ?: "").split(",").filter { it.isNotBlank() }.toSet() + owned): Boolean =
        HullCatalog.founderOpen(nowMs(), ownedAll)

    /** Hulls on sale for this player that they have not looked at in the Hangar yet (drives the NEW dot). */
    private fun newHullIds(ownedAll: Set<String>): List<String> {
        val now = nowMs(); val rel = releaseMap()
        return HullCatalog.SOLD.filter { it.id !in dotSeen && it.id !in ownedAll && HullCatalog.canBuyNow(isPro(), rel[it.id] ?: 0L, now) }.map { it.id }
    }

    /** Call when the Hangar page opens: clears the NEW dot. */
    fun markHangarSeen() {
        val ownedAll = (dao.getProfileFlow().value?.unlockedSkinsCsv ?: "").split(",").filter { it.isNotBlank() }.toSet() + owned
        val fresh = newHullIds(ownedAll)
        if (fresh.isEmpty()) return
        dotSeen = dotSeen + fresh
        scope.launch { store.put("dot_seen_csv", (dotSeen + "init").joinToString(",")); publish() }
    }

    private fun scheduleNotifications(ownedAll: Set<String>) {
        val ctx = appContext ?: return
        if (!notifyOn) { try { DropNotifier.clear(ctx) } catch (_: Exception) {}; return }
        val now = nowMs(); val rel = releaseMap(); val pro = isPro()
        val nowWall = System.currentTimeMillis()
        val entries = HullCatalog.SOLD.filter { it.id !in ownedAll && (rel[it.id] ?: 0L) > 0L }.mapNotNull { h ->
            val at = HullCatalog.buyableAtMs(pro, rel[h.id] ?: 0L)
            if (at <= now) null else DropEntry(h.id, h.name, h.emoji, nowWall + (at - now), pro)
        }
        try { DropNotifier.sync(ctx, entries, "hull") } catch (_: Exception) {}
        // Monthly thruster-trail drops ride the same calendar, Pro window and notification toggle.
        val trailEntries = ThrusterCatalog.ALL.filter { it.dropMonth > 0 }.mapNotNull { t ->
            val at = ThrusterCatalog.buyableAtMs(t, pro)
            if (at <= now) null else DropEntry(t.id, t.name, t.emoji, nowWall + (at - now), pro, "trail")
        }
        try { DropNotifier.sync(ctx, trailEntries, "trail") } catch (_: Exception) {}
    }

    fun setDropNotify(on: Boolean) {
        notifyOn = on
        scope.launch { store.put("drop_notify", if (on) "1" else "0"); publish() }
    }

    // ---- "new hull is ready" alert -------------------------------------------------------------------------
    data class DropAlert(val hullIds: List<String>, val proEarly: Boolean)
    private val _dropAlert = MutableStateFlow<DropAlert?>(null)
    val dropAlert: StateFlow<DropAlert?> = _dropAlert.asStateFlow()
    private var alertSeen: Set<String> = emptySet()
    private var alertInit = false

    private suspend fun loadAlertSeen() {
        val raw = store.get("drop_seen_csv")
        if (raw.isBlank()) {
            // first run on this install: everything already on sale is "old news", only future drops alert
            val now = ServerClock.bestNowMs()
            alertSeen = HullCatalog.SOLD.filter { HullCatalog.canBuyNow(isPro(), releaseMap()[it.id] ?: 0L, now) }.map { it.id }.toSet()
            store.put("drop_seen_csv", (alertSeen + "init").joinToString(","))
        } else alertSeen = raw.split(",").filter { it.isNotBlank() }.toSet()
        alertInit = true
    }

    private fun checkDropAlert(ownedAll: Set<String>) {
        if (!alertInit) return
        val now = ServerClock.bestNowMs()
        val rel = releaseMap()
        val fresh = HullCatalog.SOLD.filter {
            it.id !in alertSeen && it.id !in ownedAll && HullCatalog.canBuyNow(isPro(), rel[it.id] ?: 0L, now)
        }
        if (fresh.isEmpty()) { if (_dropAlert.value != null && _dropAlert.value!!.hullIds.none { it !in alertSeen }) _dropAlert.value = null; return }
        val early = isPro() && fresh.any { (rel[it.id] ?: 0L) + HullCatalog.earlyAccessMillis() > now }
        val ids = fresh.map { it.id }
        if (_dropAlert.value?.hullIds != ids) _dropAlert.value = DropAlert(ids, early)
    }

    fun dismissDropAlert() {
        val a = _dropAlert.value ?: return
        _dropAlert.value = null
        alertSeen = alertSeen + a.hullIds
        scope.launch { store.put("drop_seen_csv", (alertSeen + "init").joinToString(",")) }
    }

    private suspend fun grant(ids: List<String>, equip: Boolean) {
        if (ids.isEmpty()) return
        owned = owned + ids
        store.putList(HangarStore.K_OWNED, owned.toList())
        // mirror into profile so the old equip/share/leaderboard code sees them
        dao.updateProfile { p ->
            val set = p.unlockedSkinsCsv.split(",").filter { it.isNotBlank() }.toMutableList()
            ids.forEach { if (it !in set) set.add(it) }
            p.copy(unlockedSkinsCsv = set.joinToString(","),
                activeSkinId = if (equip) ids.last() else p.activeSkinId)
        }
        if (equip) { lastPicked = ids.last(); store.put(HangarStore.K_LAST_PICKED, lastPicked!!) }
        progression?.refresh()
    }

    private fun isPro() = RevenueCatManager.isPro.value

    /** Hulls that take part in Free Ride: everything the player owns (bought or earned) except what they switched off. */
    private fun pool(): List<String> {
        val unlocked = (dao.getProfileFlow().value?.unlockedSkinsCsv ?: "").split(",").filter { it.isNotBlank() }
        val gemIds = gemHulls.map { it.first }.filter { it in unlocked }
        return (owned.toList() + gemIds).distinct().filter { it !in rotationOut }
    }

    private suspend fun publish() {
        val p = dao.getProfileFlow().value ?: return
        val activeId = p.activeSkinId
        val ownedAll = (p.unlockedSkinsCsv.split(",").filter { it.isNotBlank() } + owned).toSet()
        val withLoaner = if (pro.isPro && pro.loanerHullId != null) ownedAll + pro.loanerHullId!! else ownedAll
        if (nextRoll == null) nextRoll = HullRotation.preRoll(pool(), _sectorHullId.value ?: activeId, lastPicked, if (freeRide) null else activeId)
        checkDropAlert(ownedAll)
        scheduleNotifications(ownedAll)
        _ui.value = HangarUiState(
            ownedHullIds = withLoaner,
            activeHullId = activeId,
            lockedHullId = if (freeRide) null else activeId,
            hullLabels = gemHulls.associate { it.first to "🛸 ${it.second}" } + HullCatalog.ALL.associate { it.id to "${it.emoji} ${it.name}" },
            rotationIds = pool(),
            nextRotationId = if (freeRide && pool().size > 1) nextRoll else null,
            storePrices = HullPurchases.prices.value,
            isPro = isPro(),
            serverNowMs = ServerClock.bestNowMs(),
            bundleBuyable = bundleBuyable(),
            founderBuyable = founderBuyable(ownedAll), founderEndsMs = HullCatalog.founderEndsMs(),
            newHullCount = newHullIds(ownedAll).size, dropNotify = notifyOn,
            releaseAtMs = releaseMap(),
            nextDropAtMs = nextDropAt.takeIf { it > ServerClock.bestNowMs() } ?: (releaseMap().values.filter { it > ServerClock.bestNowMs() }.minOrNull() ?: 0L),
            totalHullCount = totalGemHullCount + HullCatalog.ALL.size,
            ownedHullCount = ownedAll.size.coerceAtMost(totalGemHullCount + HullCatalog.ALL.size),
            purchaseInFlightId = inFlight,
            justPurchasedId = celebrate,
            loanerHullId = pro.loanerHullId.takeIf { pro.isPro },
            auraRank = pro.rank, proMonths = pro.months, isAnnual = pro.isAnnual,
            chroma = pro.chroma, stipendClaimable = pro.stipendClaimable
        )
    }

    fun actions(activity: () -> Activity?): HangarActions = HangarActions(
        onBuy = { hull ->
            val act = activity() ?: return@HangarActions
            // Server-time gate. Non-Pro cannot buy during the Pro window even if the UI was bypassed.
            val rel = releaseMap()[hull.id] ?: 0L
            if (!HullCatalog.canBuyNow(isPro(), rel, ServerClock.bestNowMs())) {
                HangarAnalytics.earlyAccessBlocked(hull.id, isPro()); return@HangarActions
            }
            inFlight = hull.id; scope.launch { publish() }
            HullPurchases.buy(act, hull) { ok ->
                scope.launch {
                    inFlight = null
                    if (ok) { grant(listOf(hull.id), equip = true); celebrate = hull.id }
                    publish()
                }
            }
        },
        onEquip = { id -> scope.launch {
            dao.updateProfile { it.copy(activeSkinId = id) }
            lastPicked = id; store.put(HangarStore.K_LAST_PICKED, id)
            _sectorHullId.value = null; nextRoll = null
            HangarAnalytics.hullEquipped(id); publish()
        } },
        onToggleRotation = { id, on -> scope.launch {
            rotationOut = if (on) rotationOut - id else rotationOut + id
            store.putList(HangarStore.K_ROTATION_OUT, rotationOut.toList()); nextRoll = null
            HangarAnalytics.rotationToggled(id, on); publish()
        } },
        onSetLock = { id -> scope.launch {
            freeRide = (id == null); store.put(HangarStore.K_FREE_RIDE, if (freeRide) "1" else "0"); nextRoll = null
            _sectorHullId.value = null
            HangarAnalytics.lockChanged(id ?: "", id != null); publish()
        } },
        onSetChroma = { id, v -> onChroma(id, v) },
        onClaimStipend = { onStipend() },
        onBuyBundle = { act -> if (bundleBuyable()) HullPurchases.buyApexBundle(act) { ok -> if (ok) scope.launch { reconcileWithStore(); progression?.refresh() } } },
        onBuyFounder = { act -> if (founderBuyable()) HullPurchases.buyFounderBundle(act) { ok -> if (ok) scope.launch { reconcileWithStore(); progression?.refresh() } } },
        onSetDropNotify = { on -> setDropNotify(on) },
        onArmTrial = { id -> pendingTrial = id },
        onTryStart = { HangarAnalytics.hullViewed(it); HangarAnalytics.hullTryStarted(it) },
        onDismissCelebration = { celebrate = null; scope.launch { publish() } },
        onRestore = {
            HullPurchases.restoreAll { ok, ids -> scope.launch {
                if (ok) { grant(ids, equip = false); HangarAnalytics.hullsRestored(ids.size) }
                publish()
            } }
        }
    )

    // ------------------------------------------------------------ run hooks

    /** Record an explicit player choice (old shop, first-flight, transcendence) as THE hull to keep. */
    fun noteEquipped(id: String) {
        lastPicked = id; scope.launch { store.put(HangarStore.K_LAST_PICKED, id) }
    }

    /**
     * Lock-in guarantee: unless Free Ride is on, the hull the player last picked is the equipped hull.
     * If anything ever overwrote profile.activeSkinId, this puts it back before the run uses it.
     */
    private fun enforceEquipped() { scope.launch {
        val pick = lastPicked ?: return@launch
        if (freeRide) return@launch
        val p = dao.getProfileFlow().value ?: return@launch
        if (p.activeSkinId == pick) return@launch
        val unlocked = p.unlockedSkinsCsv.split(",").filter { it.isNotBlank() }.toSet()
        if (pick in unlocked || pick in owned) { dao.updateProfile { it.copy(activeSkinId = pick) }; publish() }
    } }

    /** Call at run start (and revive start). Resets rotation to the equipped hull. */
    fun onRunStart(equippedId: String) {
        enforceEquipped()
        lastMarkM = 0; currentSector = 0; _sectorHullId.value = null; nextRoll = null
        pendingTrial?.let { t ->
            pendingTrial = null
            _sectorHullId.value = t
            _toast.value = "🧪 Trying ${HullCatalog.byId(t)?.name ?: t} for 20s"
            scope.launch {
                delay(20000)
                if (_sectorHullId.value == t) { _sectorHullId.value = null; _toast.value = "Trial over. Buy it in the Hangar" }
            }
        }
        lastPicked?.let { } // lastPicked already persisted
    }

    /** Call when a new sector begins (same place the SECTOR banner is raised, in BOTH loops). */
    fun onSectorChange(sectorNumber: Int, equippedId: String, distanceM: Int = 0) {
        if (sectorNumber <= 1 || sectorNumber == currentSector) return
        currentSector = sectorNumber
        progression?.creditDistance(currentHull(equippedId), distanceM - lastMarkM); lastMarkM = distanceM
        val pool = pool()
        if (!freeRide || pool.size < 2) return
        val next = nextRoll ?: HullRotation.pickNext(pool, _sectorHullId.value ?: equippedId, lastPicked, null) ?: return
        _sectorHullId.value = next
        _toast.value = "🔄 ${HullCatalog.byId(next)?.name ?: next.replace('_', ' ')}"
        HangarAnalytics.rotationSwitched(next, sectorNumber)
        nextRoll = HullRotation.pickNext(pool, next, lastPicked, null)
    }
}
