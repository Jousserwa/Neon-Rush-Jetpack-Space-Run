package com.neonrush.game

import com.neonrush.game.db.GameDao
import android.app.Activity
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
    private val totalGemHullCount: Int
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
    private var rotation: List<String> = emptyList()
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
        rotation = store.getList(HangarStore.K_ROTATION)
        locked = store.get(HangarStore.K_LOCK).ifBlank { null }
        lastPicked = store.get(HangarStore.K_LAST_PICKED).ifBlank { null }
        reconcileWithStore()
        loadSchedule()
        publish()
        while (true) { delay(30_000); publish() }      // keeps countdowns fresh
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
        ids.filter { it !in rotation }.forEach { rotation = rotation + it }
        store.putList(HangarStore.K_ROTATION, rotation)
        if (equip) { lastPicked = ids.last(); store.put(HangarStore.K_LAST_PICKED, lastPicked!!) }
        progression?.refresh()
    }

    private fun isPro() = RevenueCatManager.isPro.value

    private suspend fun publish() {
        val p = dao.getProfileFlow().value ?: return
        val activeId = p.activeSkinId
        val ownedAll = (p.unlockedSkinsCsv.split(",").filter { it.isNotBlank() } + owned).toSet()
        val withLoaner = if (pro.isPro && pro.loanerHullId != null) ownedAll + pro.loanerHullId!! else ownedAll
        if (nextRoll == null) nextRoll = HullRotation.preRoll(rotation.filter { it in owned }, _sectorHullId.value ?: activeId, lastPicked, locked)
        _ui.value = HangarUiState(
            ownedHullIds = withLoaner,
            activeHullId = activeId,
            lockedHullId = locked,
            rotationIds = rotation,
            nextRotationId = if (locked == null && rotation.count { it in owned } > 1) nextRoll else null,
            storePrices = HullPurchases.prices.value,
            isPro = isPro(),
            serverNowMs = ServerClock.nowMs(),
            releaseAtMs = releaseAt,
            nextDropAtMs = nextDropAt,
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
            val rel = releaseAt[hull.id] ?: 0L
            if (!HullCatalog.canBuyNow(isPro(), rel, ServerClock.nowMs())) {
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
            if (locked != null) { locked = id; store.put(HangarStore.K_LOCK, id) }   // lock follows the pick
            _sectorHullId.value = null; nextRoll = null
            HangarAnalytics.hullEquipped(id); publish()
        } },
        onToggleRotation = { id, on -> scope.launch {
            rotation = if (on) (rotation + id).distinct() else rotation - id
            store.putList(HangarStore.K_ROTATION, rotation); nextRoll = null
            HangarAnalytics.rotationToggled(id, on); publish()
        } },
        onSetLock = { id -> scope.launch {
            locked = id; store.put(HangarStore.K_LOCK, id ?: ""); nextRoll = null
            HangarAnalytics.lockChanged(id ?: "", id != null); publish()
        } },
        onSetChroma = { id, v -> onChroma(id, v) },
        onClaimStipend = { onStipend() },
        onBuyBundle = { act -> HullPurchases.buyApexBundle(act) { ok -> if (ok) scope.launch { reconcileWithStore(); progression?.refresh() } } },
        onArmTrial = { id -> pendingTrial = id },
        onTryStart = { HangarAnalytics.hullTryStarted(it) },
        onDismissCelebration = { celebrate = null; scope.launch { publish() } },
        onRestore = {
            HullPurchases.restoreAll { ok, ids -> scope.launch {
                if (ok) { grant(ids, equip = false); HangarAnalytics.hullsRestored(ids.size) }
                publish()
            } }
        }
    )

    // ------------------------------------------------------------ run hooks

    /** Call at run start (and revive start). Resets rotation to the equipped hull. */
    fun onRunStart(equippedId: String) {
        lastMarkM = 0; currentSector = 0; _sectorHullId.value = null; nextRoll = null
        pendingTrial?.let { t ->
            pendingTrial = null
            _sectorHullId.value = t
            _toast.value = "🧪 Trying ${HullCatalog.byId(t)?.name ?: t} for 8s"
            scope.launch {
                delay(8000)
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
        val pool = rotation.filter { it in owned }
        if (locked != null || pool.size < 2) return
        val next = nextRoll ?: HullRotation.pickNext(pool, _sectorHullId.value ?: equippedId, lastPicked, null) ?: return
        _sectorHullId.value = next
        _toast.value = "🔄 ${HullCatalog.byId(next)?.name ?: next.replace('_', ' ')}"
        HangarAnalytics.rotationSwitched(next, sectorNumber)
        nextRoll = HullRotation.pickNext(pool, next, lastPicked, null)
    }
}
