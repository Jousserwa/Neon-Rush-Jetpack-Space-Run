package com.neonrush.game
import android.app.Activity
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neonrush.game.db.GameDao
import com.neonrush.game.ui.HullFx
import com.neonrush.game.ui.PilotShieldFx
import com.neonrush.game.ui.PickupFx
import com.neonrush.game.ui.ReactFx
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await
import com.neonrush.game.db.GameProfile
import com.neonrush.game.db.GhostChallengeEntity
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

// ============================================================
// TESTING ONLY — set back to false (or delete this line and its
// two usages below) when told. While true, Worlds 4-5 and every
// Pro-flagged phase are freely reachable without the paywall gate,
// so all 5 main worlds can be QA'd end-to-end in one run.
// ============================================================
const val TESTING_DISABLE_PRO_GATE = true   // TESTING: everything Pro, Legendary open, unlimited gems, no fuel drain. MUST be false for release

/** EASY (kids) mode: hits the pilot's shield soaks up at run start and after every refuel / revive. */
/** Power-ups were too rare: drop-rate multiplier, and a guaranteed one if none showed up for this many ticks (8 ticks = 1 s). */
const val PU_BOOST = 2.2f
const val PU_PITY_TICKS = 100
const val EASY_SHIELD_LAYERS = 2

/** Shield the pilot wears after every revive: EASY 2 (it also gets one at run start and after each refuel), MEDIUM 2, HARD / LEGENDARY 1. */
fun reviveShieldLayers(tier: NeonRushViewModel.DifficultyTier): Int = when (tier) {
    NeonRushViewModel.DifficultyTier.EASY -> EASY_SHIELD_LAYERS
    NeonRushViewModel.DifficultyTier.MEDIUM -> 2
    else -> 1
}

/** Info attached automatically to a bug-report email. */
data class SupportSnapshot(val score: Int, val sector: Int, val difficulty: String)

/** All four tiers feel 30% gentler (belt speed, obstacle density, threat and boss-fire rate). The zone-by-zone growth is unchanged. */
const val DIFFICULTY_EASE = 0.70f

/** Every zone (so every world) takes this much more distance to cross. 1.5 = half again as long. Keep the three zone formulas in sync. */
const val ZONE_LENGTH_MULT = 4.5

data class LeaderboardPilot(
    val rank: Int,
    val name: String,
    val bestScore: Int,
    val activeZone: String,
    val activeSkinId: String,
    val isFollowed: Boolean,
    val isBot: Boolean = true,
    val challengeId: String,
    val auraRank: String = "NONE",
    val isLegend: Boolean = false,
    val titleId: String = "rookie",
    val badgeIds: List<String> = emptyList()
)

data class SocialComment(
    val username: String,
    val comment: String,
    val timeAgo: String,
    val associatedZone: String
)

data class VisualTrackElement(
    val id: String,
    val xOffsetFraction: Float,
    val yMatchPos: Int,
    val type: String,
    val subType: String = "",
    val isCollected: Boolean = false
)

data class Particle(
    val id: String,
    val x: Float,
    val y: Float,
    val vx: Float,
    val vy: Float,
    val age: Int = 0,
    val maxAge: Int = 15,
    val colorArgb: Long,
    val kind: String
)

data class SimulationState(
    val activeGhost: GhostChallengeEntity? = null,
    val isCompleted: Boolean = false,
    val isStarted: Boolean = false,
    val tickIndex: Int = 0,
    val mirrorSide: Int = 0,          // Mirror realm: 0 cyan, 1 magenta
    val mirrorFlipTick: Int = -100,
    val userYPath: List<Int> = emptyList(),
    val ghostYPath: List<Int> = emptyList(),
    val userYPos: Int = 50,
    val ghostYPos: Int = 50,
    val score: Int = 0,
    val currentZoneName: String = "Cyber Alley",
    val speedKmh: Int = 120,
    val fuelLevelPercent: Int = 100,
    val distanceMeters: Float = 0f,
    val feedbackMessage: String = "Ready to Sync Rushes",
    val currentZoneNumber: Int = 1,
    val zoneDNA: ZoneDNA = ZoneGenerator.generateZone(1, 42),
    val activeTrackElements: List<VisualTrackElement> = emptyList(),
    val activePowerupDurations: Map<String, Int> = emptyMap(),
    val screenShakeX: Float = 0f,
    val screenShakeY: Float = 0f,
    val bossActive: Boolean = false,
    val bossHealth: Float = 1.0f,
    val bossY: Int = 50,
    val collectedGemsCount: Int = 0,
    val gemsEarnedLastRun: Int = 0,
    val doubleGemsClaimed: Boolean = false,
    val gemsAtRunStart: Int = -1,
    val completeRunCallCount: Int = 0,
    val gemsAlreadyCreditedThisRun: Int = 0,
    val dailyBonusLabel: String = "",
    val bossZonesRewarded: String = "",
    val bossGemsThisRun: Int = 0,
    val ghostTierMode: Int = 1,
    val isTranscendenceUnlocked: Boolean = false,
    val currentMutationName: String = "",
    val frustrationLevelIndex: Float = 0.5f,
    val obstacleDensityMod: Float = 1.0f,
    val lastZoneTransitionTick: Int = -999,
    val particles: List<Particle> = emptyList(),
    val reviveCount: Int = 0,
    val shieldUntilTick: Int = -1,
    val shieldLayers: Int = 0,
    val fuelRefillCount: Int = 0,
    val specialWorldId: Int? = null,
    // Combo: consecutive tight-precision ticks (rawError < 8), resets to 0 on
    // any obstacle/bullet hit. Rewards sustained skill, not just survival —
    // a second, independent progression axis alongside gems.
    val comboStreak: Int = 0,
    val peakComboStreak: Int = 0,
    // Near-miss loop (graze/razor combo). Tier 0 none, 1 graze, 2 razor.
    val nearMissTier: Int = 0,
    val nearMissTick: Int = -999,
    val nearCombo: Int = 0,
    val overdriveUntilTick: Int = -1,
    // Sector system: every 2 zones is a "sector" with guaranteed-different
    // obstacle set/mechanics/environment from the previous one (see
    // ZoneGenerator). These fields drive the on-screen banner + gem bonus
    // + optional rewarded-ad double that fires on every sector transition.
    val currentSectorNumber: Int = 0,
    val sectorBannerText: String = "",
    val sectorBannerUntilTick: Int = 0,
    val sectorBonusPending: Int = 0,
    val sectorBonusExpiresAtTick: Int = 0,
    // Stable per-run seed for zone DNA generation (obstacle set,
    // environment, mechanics) — must persist across revives so the
    // sequence doesn't shift/reshuffle at the exact moment a player revives.
    val runSeed: Long = 0L,
    // Pro-content gate: fires when endless progression drifts into a
    // requiresPro world/phase range and the player isn't Pro. Gives a
    // 4-second grace/preview, then forces a choice — subscribe, or get
    // redirected back into free-tier zones. Can't just play through it.
    val proGateActive: Boolean = false,
    val proGateGraceUntilTick: Int = 0,
    val proGateTriggered: Boolean = false,
    // Run goals / Mastery (see RunGoals.kt)
    val runGoalIds: String = "",
    val goalsAwardedCsv: String = "",
    val powerupsCollected: Int = 0,
    val masteryEarnedLastRun: Int = 0,
    // Invisible onboarding (see Hints.kt)
    val hint: HintState = HintState(),
    // True on the game-over screen of the very first run (shows the First Flight reward).
    val firstFlightReward: Boolean = false
)
class NeonRushViewModel(
    private val gameDao: GameDao,
    context: Context
) : ViewModel() {

    private val appCtx: Context = context.applicationContext
    private val soundEngine = NeonSoundEngine()

    val profile = gameDao.getProfileFlow()

    // Latest upgrade levels, read by the simulation loops every tick.
    @Volatile private var activeUpgradesCsv: String = ""
    @Volatile private var activeMasteryPoints: Int = 0
    @Volatile private var activeTotalRuns: Int = 0

    val shopSkins = listOf(
        Triple("cyan_diamond", "Cyan Diamond", 0),
        Triple("purple_square", "Purple Square", 250),
        Triple("green_triangle", "Green Triangle", 450),
        Triple("solar_comet", "Solar Comet", 550),
        Triple("ice_shard", "Ice Shard", 700),
        Triple("magenta_pulse", "Magenta Pulse Racer", 850),
        Triple("static_storm", "Static Storm", 1200),
        Triple("vaporwave_wave", "Vaporwave Wave", 1500),
        Triple("gold_transcendence", "Gold Transcendence Vessel", 1900),
        Triple("phantom_echo", "Phantom Echo", 2500),
        Triple("matrix_grid", "Hex Grid Cyber-Fighter", 3100)
    )

    // Difficulty state lives ABOVE every init block: init launches coroutines that read it immediately.
    var selectedDifficulty: DifficultyTier = DifficultyTier.MEDIUM
    private val _difficulty = MutableStateFlow(DifficultyTier.MEDIUM)
    val difficultyFlow: StateFlow<DifficultyTier> = _difficulty
    private val _boardTier = MutableStateFlow(DifficultyTier.MEDIUM)
    val boardTier: StateFlow<DifficultyTier> = _boardTier

    // ---- Hull system (Hangar, Pro perks, progression, season pass)
    private val hangarStore = HangarStore { gameDao.db() }
    val hangar = HangarController(gameDao, hangarStore, viewModelScope, shopSkins.size, shopSkins.map { it.first to it.second })
    val proPerks = ProPerks(gameDao, hangarStore, viewModelScope, hangar)
    val progression = HullProgression(gameDao, hangarStore, viewModelScope, hangar)
    val seasonPass = SeasonPass(gameDao, hangarStore, viewModelScope, hangar) { RevenueCatManager.isPro.value }

    // Story suits: the RIGHT TO BUY is earned (see SuitGates). Declared above every init block that may read it.
    private val _suitGatesOpen = MutableStateFlow<Set<String>>(emptySet())
    val suitGatesOpen: StateFlow<Set<String>> = _suitGatesOpen.asStateFlow()
    private val _suitGateCelebration = MutableSharedFlow<String>(extraBufferCapacity = 2)
    val suitGateCelebration: SharedFlow<String> = _suitGateCelebration.asSharedFlow()
    private val _suitFreeRide = MutableStateFlow(false)
    val suitFreeRide: StateFlow<Boolean> = _suitFreeRide.asStateFlow()
    fun setSuitFreeRide(on: Boolean) {
        _suitFreeRide.value = on
        viewModelScope.launch { try { hangarStore.put(SuitRotation.K_SUIT_FREE_RIDE, if (on) "1" else "0") } catch (_: Exception) {} }
    }
    // Thruster trail slot (independent of hull and suit). Announcements reuse the suit celebration snackbar.
    val thrusters = ThrusterLocker(gameDao, hangarStore, viewModelScope, { RevenueCatManager.isPro.value }, { msg -> _suitGateCelebration.tryEmit(msg) })
    // Pilot parts (head/neck/shoulder/back/wrist/boots gear bought with gems, worn on any suit).
    val parts = PartsLocker(gameDao, hangarStore, viewModelScope) { msg -> _suitGateCelebration.tryEmit(msg) }
    val fuelGate = FuelGate(hangarStore, viewModelScope)
    // One pilot suit per month: when it is released, defeating a boss earns the right to buy it.
    val suitDrops = SuitDropper(hangarStore, viewModelScope) { skinId ->
        _suitGatesOpen.value = _suitGatesOpen.value + skinId
        val skin = Skins.ALL.firstOrNull { it.id == skinId }
        soundEngine.playUnlockSkin()
        _suitGateCelebration.tryEmit("🔓 BOSS DOWN. SUIT UNLOCKED FOR PURCHASE: ${skin?.name ?: skinId} (${skin?.priceUsd ?: ""}). Find it in Shop > Pilot Suits.")
    }
    /** A happy moment (boss beaten, world cleared): the home screen may show Google's in-app review once the player is back there. */
    private fun flagReviewMoment() {
        viewModelScope.launch { try { if (hangarStore.getLong("review_runs") >= 3) hangarStore.put("review_pending", "1") } catch (_: Exception) { } }
    }

    fun onBossDefeatedForDrops() {
        flagReviewMoment()
        viewModelScope.launch {
            val owned = try { gameDao.getProfileDirect()?.unlockedPilotSkinsCsv?.split(",")?.filter { it.isNotBlank() }?.toSet() ?: emptySet() } catch (_: Exception) { emptySet() }
            suitDrops.onBossWin(owned)
        }
    }
    val locker = LockerRewards(gameDao, hangarStore, viewModelScope) { msg -> _suitGateCelebration.tryEmit(msg) }
    private suspend fun loadSuitGates() {
        parts.load()
        locker.load()
        fuelGate.load(); fuelGate.touch(RevenueCatManager.isPro.value)
        try { NearMissFx.shakeOn = hangarStore.get("nm_shake") != "0"; NearMissFx.flashOn = hangarStore.get("nm_flash") != "0" } catch (_: Exception) {}
        try { _suitFreeRide.value = hangarStore.get(SuitRotation.K_SUIT_FREE_RIDE) == "1" } catch (_: Exception) {}
        thrusters.load()
        try { gameDao.getProfileDirect()?.let { thrusters.evaluate(it) } } catch (_: Exception) {}
        val nowMs = ServerClock.bestNowMs()
        _suitGatesOpen.value = SuitGates.ALL.filter { try { hangarStore.get(SuitGates.key(it.skinId)) == "1" } catch (_: Exception) { false } }
            .filter { DropSchedule.suitReleased(it.skinId, nowMs) }
            .map { it.skinId }.toSet() + suitDrops.load().filter { DropSchedule.suitReleased(it, nowMs) }
    }
    private suspend fun openSuitGate(skinId: String, announce: Boolean) {
        if (skinId in _suitGatesOpen.value) return
        try { hangarStore.put(SuitGates.key(skinId), "1") } catch (_: Exception) {}
        _suitGatesOpen.value = _suitGatesOpen.value + skinId
        if (announce) {
            val g = SuitGates.forSkin(skinId)
            val skin = Skins.ALL.firstOrNull { it.id == skinId }
            soundEngine.playUnlockSkin()
            _suitGateCelebration.tryEmit("🔓 SUIT UNLOCKED FOR PURCHASE: ${skin?.name ?: skinId} (${skin?.priceUsd ?: ""}). ${g?.teaser ?: ""} Find it in Hangar > Pilot Suits.")
        }
    }
    init {
        hangar.progression = progression
        hangar.appContext = context.applicationContext
        hangar.onChroma = { id, v -> proPerks.setChroma(id, v) }
        hangar.onStipend = { proPerks.claimStipend() }
    }

    val leaderboard: StateFlow<List<LeaderboardPilot>> = FirebaseLeaderboardManager.globalRankings

    private val _simState = MutableStateFlow(SimulationState())
    val simState: StateFlow<SimulationState> = _simState.asStateFlow()

    private val _socialComments = MutableStateFlow<List<SocialComment>>(emptyList())
    val socialComments: StateFlow<List<SocialComment>> = _socialComments.asStateFlow()

    val dailyChallengeTitle = "Storm Run"
    val dailyChallengeDesc = "90 seconds in the heart of the storm. Dodge the lightning columns, grab storm cells, fight the wind. One course for every pilot today; climb the reward ladder and keep your streak alive."
    val dailyChallengeGoal = 200

    private var simJob: Job? = null

    private val _storyEvent = MutableSharedFlow<StoryEvent>(extraBufferCapacity = 4)
    val storyEvent: SharedFlow<StoryEvent> = _storyEvent.asSharedFlow()

    val currentWorld: StateFlow<World> = simState
        .map { st ->
            // Special-mission worlds (6-8, NG+) run on zone numbers starting at 1, so
            // worldForZone() would wrongly return World 1. Resolve them by environment.
            val special = st.specialWorldId?.let { envId ->
                (Worlds.SPECIAL_WORLDS + Worlds.NEW_GAME_PLUS).find { it.environmentIds.contains(envId) }
            }
            special ?: Worlds.worldForZone(st.currentZoneNumber)
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, Worlds.ALL.first())

    private val firedStoryBeats = mutableSetOf<Pair<Int, StoryBeatType>>()
    private var lastWorldIdForStory = -1

    private fun checkWorldEndingBeat(previousZoneNumber: Int, nextZoneNumber: Int) {
        val world = Worlds.worldForZone(previousZoneNumber)
        if (nextZoneNumber > world.endZone && firedStoryBeats.add(world.id to StoryBeatType.ENDING)) {
            _storyEvent.tryEmit(StoryEvent(world, StoryBeatType.ENDING, world.endingText))
            flagReviewMoment()
            // The suits are no longer handed out: clearing the world (under the suit's condition) earns the RIGHT TO BUY it.
            SuitGates.forWorld(world.id)?.let { g ->
                if (!DropSchedule.suitReleased(g.skinId, ServerClock.bestNowMs())) {
                    _suitGateCelebration.tryEmit("Sector cleared. The ${Skins.ALL.firstOrNull { it.id == g.skinId }?.name ?: "suit"} drops on ${DropSchedule.dateLabel(DropSchedule.suitReleaseMs(g.skinId))}. Defeat a boss after that to unlock it for purchase.")
                } else if (SuitGates.met(g, selectedDifficulty, _simState.value.reviveCount)) {
                    viewModelScope.launch { openSuitGate(g.skinId, announce = true) }
                } else {
                    _suitGateCelebration.tryEmit("Sector cleared, but the ${Skins.ALL.firstOrNull { it.id == g.skinId }?.name ?: "suit"} is still locked. ${g.requirement}")
                }
            }
        }
    }

    private fun checkStoryBeats(zoneNumber: Int, bossRisingEdge: Boolean) {
        val world = Worlds.worldForZone(zoneNumber)
        if (world.id != lastWorldIdForStory) {
            lastWorldIdForStory = world.id
            if (firedStoryBeats.add(world.id to StoryBeatType.OPENING)) {
                _storyEvent.tryEmit(StoryEvent(world, StoryBeatType.OPENING, world.openingText))
            }
        }
        val midZone = world.startZone + (world.endZone - world.startZone) / 2
        if (zoneNumber >= midZone && firedStoryBeats.add(world.id to StoryBeatType.MID_RUN)) {
            _storyEvent.tryEmit(StoryEvent(world, StoryBeatType.MID_RUN, world.midRunText))
        }
        if (bossRisingEdge && firedStoryBeats.add(world.id to StoryBeatType.BOSS_INTRO)) {
            _storyEvent.tryEmit(StoryEvent(world, StoryBeatType.BOSS_INTRO, world.bossIntroText))
        }
    }

    private fun resetStoryProgress() {
        firedStoryBeats.clear()
        lastWorldIdForStory = -1
    }

    fun triggerPaywallTeaser(world: World) {
        _storyEvent.tryEmit(
            StoryEvent(
                world,
                StoryBeatType.PAYWALL_TEASER,
                "Command's voice cuts through static: \"Copter's down in the reserve. Something's already found the wreckage. You in?\""
            )
        )
    }

    fun purchaseGemPack(activity: Activity, productId: String, gemAmount: Int) {
    RevenueCatManager.purchaseGemPack(activity, productId) { success ->
        if (success) {
            viewModelScope.launch {
                gameDao.updateProfile { prof -> prof.copy(gems = prof.gems + gemAmount) }
                PurchaseLedger.markDeliveredByProduct(productId)
                soundEngine.playUnlockSkin()
            }
        } else {
            _purchaseErrorEvent.tryEmit("Purchase failed. Please try again.")
        }
    }
}
    /**
     * Mirrors purchases into the game. Runs on launch (from the offline ledger, no network needed), after every purchase,
     * restore and background store update, and when internet returns (OfflineSync). Everything here is idempotent.
     */
    private suspend fun reconcileEntitlements(fromStore: Boolean = false) {
        val owned = RevenueCatManager.ownedProducts.value
        // One time: every classic hull is real money. Anything opened by testing or crates is locked again; a real store purchase,
        // the Pro 3-month gift and Transcendence ranks keep theirs (those rewards are untouched). Cyan Diamond, Purple Square and Green Triangle are never locked.
        val relockDone = try { hangarStore.get("relock_classics_v2") == "1" } catch (_: Exception) { true }
        if (!relockDone) {
            val boughtIds = owned.map { it.substringBefore(':') }.filter { it.startsWith("neonrush_hull_") }.map { it.removePrefix("neonrush_hull_") }.toSet()
            val giftOk = try { hangarStore.get("free_gem_hulls_granted") == "1" } catch (_: Exception) { false }
            gameDao.updateProfile { p ->
                val have = p.unlockedSkinsCsv.split(",").filter { it.isNotBlank() }
                val keep = have.filter { id ->
                    DropSchedule.classicHull(id) == null || id in boughtIds ||
                        (giftOk && (id == "magenta_pulse" || id == "gold_transcendence")) ||
                        (id == "gold_transcendence" && p.transcendenceCount >= 1) ||
                        (id == "matrix_grid" && p.transcendenceCount >= 2)
                }
                if (keep.size == have.size) p
                else p.copy(
                    unlockedSkinsCsv = keep.joinToString(","),
                    activeSkinId = if (p.activeSkinId in have && p.activeSkinId !in keep) "cyan_diamond" else p.activeSkinId
                )
            }
            try { hangarStore.put("relock_classics_v2", "1") } catch (_: Exception) {}
        }
        // Pilot suits (non-consumable "neonrush_skin_<id>"): were previously only unlocked in the purchase callback,
        // so a reinstall / new phone / killed app lost them.
        owned.map { it.substringBefore(':') }.filter { it.startsWith("neonrush_skin_") }.map { it.removePrefix("neonrush_skin_") }
            .forEach { unlockPilotSkinFromStory(it) }
        // Only Standard Issue is free. A bought suit stays unlocked only while the store (or the secure ledger) says it is owned.
        // Runs after a real store sync, so a slow start or no signal can never remove a legitimate purchase.
        if (fromStore) {
            val paidSuitIds = Skins.ALL.filter { it.purchasable && it.priceUsd.startsWith("$") }.map { it.id }.toSet()
            val ownedSuitIds = owned.map { it.substringBefore(':') }.filter { it.startsWith("neonrush_skin_") }.map { it.removePrefix("neonrush_skin_") }.toSet()
            // Players who already earned a story suit before it became paid keep the right to buy it.
            gameDao.getProfileDirect()?.unlockedPilotSkinsCsv?.split(",")?.filter { it in paidSuitIds && SuitGates.forSkin(it) != null }
                ?.forEach { openSuitGate(it, announce = false) }
            gameDao.updateProfile { p ->
                val unlocked = p.unlockedPilotSkinsCsv.split(",").filter { it.isNotBlank() }
                val keep = unlocked.filter { it !in paidSuitIds || it in ownedSuitIds }
                if (keep.size == unlocked.size) p
                else p.copy(
                    unlockedPilotSkinsCsv = keep.joinToString(","),
                    activePilotSkinId = if (p.activePilotSkinId in unlocked && p.activePilotSkinId !in keep) "default" else p.activePilotSkinId
                )
            }
        }
        // Classic hulls bought with real money (neonrush_hull_<id>) come back after a reinstall.
        val classicOwned = owned.map { it.substringBefore(':') }.filter { it.startsWith("neonrush_hull_") }
            .map { it.removePrefix("neonrush_hull_") }.filter { DropSchedule.classicHull(it) != null }
        if (classicOwned.isNotEmpty()) {
            gameDao.updateProfile { p ->
                val set = p.unlockedSkinsCsv.split(",").filter { it.isNotBlank() }.toMutableList()
                var changed = false
                classicOwned.forEach { if (it !in set) { set.add(it); changed = true } }
                if (changed) p.copy(unlockedSkinsCsv = set.joinToString(",")) else p
            }
        }
        // Classic hulls are purchase-only. After a real store sync, one only stays owned if the store says so, or it came
        // from a deliberate reward: the Pro 3-month gift (Magenta Pulse + Gold Transcendence) or Transcendence ranks.
        if (fromStore) {
            val giftGranted = try { hangarStore.get("free_gem_hulls_granted") == "1" } catch (_: Exception) { false }
            val storeSet = classicOwned.toSet()
            gameDao.updateProfile { p ->
                val have = p.unlockedSkinsCsv.split(",").filter { it.isNotBlank() }
                val keep = have.filter { id ->
                    DropSchedule.classicHull(id) == null || id in storeSet ||
                        (giftGranted && (id == "magenta_pulse" || id == "gold_transcendence")) ||
                        (id == "gold_transcendence" && p.transcendenceCount >= 1) ||
                        (id == "matrix_grid" && p.transcendenceCount >= 2)
                }
                if (keep.size == have.size) p
                else p.copy(
                    unlockedSkinsCsv = keep.joinToString(","),
                    activeSkinId = if (p.activeSkinId in have && p.activeSkinId !in keep) "cyan_diamond" else p.activeSkinId
                )
            }
        }
        // Real-money pilot parts (non-consumable neonrush_part_<id>) restore on every store sync.
        parts.grantFromStore(owned.map { it.substringBefore(':') }.filter { it.startsWith("neonrush_part_") }.map { it.removePrefix("neonrush_part_") })
        // Real-money trails (non-consumable neonrush_trail_<id>) restore on every store sync, and unbacked ones are taken off.
        val trailIds = owned.map { it.substringBefore(':') }.filter { it.startsWith("neonrush_trail_") }.map { it.removePrefix("neonrush_trail_") }
        thrusters.grantFromStore(trailIds)
        if (fromStore) thrusters.syncRealMoney(trailIds)
        if (fromStore) parts.syncRealMoney(owned.map { it.substringBefore(':') }.filter { it.startsWith("neonrush_part_") }.map { it.removePrefix("neonrush_part_") })
        // Pro + ads-removed: profile.subscriptionPro / adsRemoved were never written from the store before.
        val pro = RevenueCatManager.isPro.value
        val ads = RevenueCatManager.isAdsRemoved.value
        gameDao.updateProfile { p ->
            if (p.subscriptionPro != pro || (ads && !p.adsRemoved)) p.copy(subscriptionPro = pro, adsRemoved = p.adsRemoved || ads) else p
        }
        hangar.reconcileFromLedger()
    }

    /** Gem packs the store charged for but the game never granted (app killed mid-purchase). Delivered exactly once. */
    private suspend fun deliverPendingPurchases() {
        PurchaseLedger.undelivered().forEach { p ->
            if (p.gems > 0) {
                gameDao.updateProfile { prof -> prof.copy(gems = prof.gems + p.gems) }
                PurchaseLedger.markDelivered(p.tx)
                _purchaseErrorEvent.tryEmit("Restored your purchase: +${p.gems} 💎")
            }
        }
    }

    fun recordAdWatched() {
    viewModelScope.launch {
        gameDao.updateProfile { prof -> MissionManager.recordAdWatched(prof) }
    }
}
    // Marks the mandatory first-run tutorial as seen — permanent, one-time.
    fun markTutorialSeen() {
        viewModelScope.launch {
            gameDao.updateProfile { prof -> prof.copy(hasSeenTutorial = true) }
        }
    }
    // Timestamps the "check for updates" reminder so it's throttled to
    // roughly once every 2 weeks rather than showing on every launch.
    fun recordUpdateReminderShown() {
        viewModelScope.launch {
            gameDao.updateProfile { prof -> prof.copy(lastUpdateReminderShownAt = System.currentTimeMillis()) }
        }
    }
    // Called when a free player declines to subscribe at a Pro-content gate.
    // Jumps them past the blocked world's zone range into whatever comes
    // next (either the next free world, or open endless territory) rather
    // than ending the run — the gate blocks the Pro content, not the game.
    fun redirectToFreeZone() {
        val current = _simState.value
        // Walk forward past every consecutive requiresPro range — Worlds 4
        // and 5 (and every world's later phases) are all Pro, so skipping
        // just the one immediately blocking world could land the player
        // directly inside the next one and re-trigger the gate right away.
        var targetZone = (Worlds.worldForZone(current.currentZoneNumber).endZone + 1).coerceAtLeast(1)
        var guard = 0
        while (guard < 20) {
            val w = Worlds.worldForZone(targetZone)
            val stillBlocked = w.requiresPro && targetZone in w.startZone..w.endZone
            if (!stillBlocked) break
            targetZone = w.endZone + 1
            guard++
        }
        // Inverse of the zone<->distance formula used in the tick loop
        // (zoneM = (-776.25 + sqrt(602564.0625 + 405*distance)) / 202.5),
        // solved for the distance at the start of targetZone.
        val zoneMTarget = (targetZone - 1).toDouble()
        val targetDistanceRaw = ((202.5 * zoneMTarget + 776.25).let { it * it } - 602564.0625) / 405.0 * ZONE_LENGTH_MULT
        val targetDistance = targetDistanceRaw.toFloat().coerceAtLeast(current.distanceMeters)
        _simState.value = current.copy(
            distanceMeters = targetDistance,
            currentZoneNumber = targetZone,
            proGateActive = false,
            proGateTriggered = false,
            proGateGraceUntilTick = 0
        )
    }
    // Doubles the sector gem bonus after a rewarded ad. The base amount was
    // already credited the instant the sector transition fired; this adds
    // the same amount again, then clears the offer so the button disappears.
    fun claimSectorAdBonus() {
        val pending = _simState.value.sectorBonusPending
        if (pending <= 0) return
        viewModelScope.launch {
            gameDao.updateProfile { prof ->
                val afterMissionUpdate = MissionManager.recordAdWatched(prof)
                afterMissionUpdate.copy(gems = afterMissionUpdate.gems + pending)
            }
        }
        _simState.value = _simState.value.copy(sectorBonusPending = 0, sectorBonusExpiresAtTick = 0)
    }
    // Near-miss feel switches (vibration is NeonSoundEngine's haptics switch)
    fun setNearMissShake(on: Boolean) { NearMissFx.shakeOn = on; viewModelScope.launch { try { hangarStore.put("nm_shake", if (on) "1" else "0") } catch (_: Exception) {} } }
    fun setNearMissFlash(on: Boolean) { NearMissFx.flashOn = on; viewModelScope.launch { try { hangarStore.put("nm_flash", if (on) "1" else "0") } catch (_: Exception) {} } }
    fun getHapticsEnabled(): Boolean = NeonSoundEngine.getHapticsEnabled()
    fun setHapticsEnabled(on: Boolean) = NeonSoundEngine.setHapticsEnabled(on)
    fun getSoundEffectsEnabled(): Boolean = NeonSoundEngine.getSoundEffectsEnabled()
    fun setSoundEffectsEnabled(enabled: Boolean) = NeonSoundEngine.setSoundEffectsEnabled(enabled)
    fun getAmbientEnabled(): Boolean = NeonSoundEngine.getAmbientEnabled()
    fun setAmbientEnabled(enabled: Boolean) = NeonSoundEngine.setAmbientEnabled(enabled)
    fun purchaseStarterPack(activity: Activity) {
    if (StarterPack.bought(appCtx)) { _purchaseErrorEvent.tryEmit("The Starter Pack can only be bought once, and you already have it."); return }
    RevenueCatManager.purchaseStarterPack(activity) { success ->
        if (success) {
            StarterPack.markBought(appCtx)
            viewModelScope.launch {
                gameDao.updateProfile { prof -> prof.copy(gems = prof.gems + RevenueCatManager.STARTER_PACK_GEMS_AMOUNT, totalGemsEarned = prof.totalGemsEarned) }
                PurchaseLedger.markDeliveredByProduct(RevenueCatManager.PRODUCT_ID_STARTER_PACK)
                soundEngine.playUnlockSkin()
            }
        } else {
            _purchaseErrorEvent.tryEmit("Purchase failed. Please try again.")
        }
    }
}

fun purchaseFuelTier(activity: Activity, tier: Int, productId: String) {
    viewModelScope.launch {
        val prof = gameDao.getProfileDirect() ?: GameProfile()
        if (prof.fuelTiersOwned != tier - 1) {
            _purchaseErrorEvent.tryEmit("Purchase your tiers in order.")
            return@launch
        }
        fuelGate.touch(RevenueCatManager.isPro.value)
        if (!fuelGate.canBuy(tier)) {
            _purchaseErrorEvent.tryEmit("Tier $tier is still locked. Keep your monthly streak going.")
            return@launch
        }
        RevenueCatManager.purchaseGemPack(activity, productId) { success ->
            if (success) {
                viewModelScope.launch {
                    gameDao.updateProfile { p ->
                        if (p.fuelTiersOwned == tier - 1) {
                            p.copy(fuelTiersOwned = tier)
                        } else {
                            p
                        }
                    }
                    soundEngine.playUnlockSkin()
                }
            } else {
                _purchaseErrorEvent.tryEmit("Purchase failed. Please try again.")
            }
        }
    }
}
fun purchaseRemoveAds(activity: Activity) {
    RevenueCatManager.purchaseRemoveAds(activity) { success ->
        if (success) {
            viewModelScope.launch {
                gameDao.updateProfile { prof -> prof.copy(adsRemoved = true) }
                soundEngine.playUnlockSkin()
            }
        } else {
            _purchaseErrorEvent.tryEmit("Purchase failed. Please try again.")
        }
    }
}
fun claimMission(tier: MissionTier, missionId: String) {
    viewModelScope.launch {
        var rewardGemsResult = 0
        var oldSpecialWorldTier = 0
        var claimed = false
        val finalProfile = gameDao.updateProfile { prof ->
            oldSpecialWorldTier = prof.specialWorldTier
            val result = MissionManager.claimMission(prof, tier, missionId)
            if (result != null) {
                val (updatedProfile, rewardGems) = result
                rewardGemsResult = rewardGems
                claimed = true
                val newTier = MissionManager.checkSpecialWorldQualification(updatedProfile)
                updatedProfile.copy(
                    gems = updatedProfile.gems + rewardGems,
                    specialWorldTier = newTier
                )
            } else {
                prof
            }
        }
        if (claimed) {
            soundEngine.playUnlockSkin()
            if (finalProfile.specialWorldTier > oldSpecialWorldTier) {
                soundEngine.playPersonalBestBroken() // reuse as a celebratory "world unlocked" sound
            }
            AnalyticsManager.logScoreMilestone(rewardGemsResult)
        }
    }
}
fun rerollMissions(tier: MissionTier) {
    viewModelScope.launch {
        val cost = when (tier) {
            MissionTier.DAILY -> 10
            MissionTier.WEEKLY -> 20
            MissionTier.MONTHLY -> 30
        }
        var didReroll = false
        gameDao.updateProfile { prof ->
            if (prof.gems >= cost) {
                didReroll = true
                when (tier) {
                    MissionTier.DAILY -> prof.copy(
                        gems = prof.gems - cost,
                        dailyRerollCount = prof.dailyRerollCount + 1,
                        dailyMissionProgressCsv = "",
                        dailyMissionsClaimedCsv = ""
                    )
                    MissionTier.WEEKLY -> prof.copy(
                        gems = prof.gems - cost,
                        weeklyRerollCount = prof.weeklyRerollCount + 1,
                        weeklyMissionProgressCsv = "",
                        weeklyMissionsClaimedCsv = ""
                    )
                    MissionTier.MONTHLY -> prof.copy(
                        gems = prof.gems - cost,
                        monthlyRerollCount = prof.monthlyRerollCount + 1,
                        monthlyMissionProgressCsv = "",
                        monthlyMissionsClaimedCsv = ""
                    )
                }
            } else {
                prof
            }
        }
        if (didReroll) {
            soundEngine.playUnlockSkin()
        }
    }
}
    fun equipPilotSkin(skinId: String) {
        viewModelScope.launch {
            var didEquip = false
            gameDao.updateProfile { prof ->
                val unlocked = prof.unlockedPilotSkinsCsv.split(",").toSet()
                if (unlocked.contains(skinId)) {
                    didEquip = true
                    prof.copy(activePilotSkinId = skinId)
                } else {
                    prof
                }
            }
            if (didEquip) {
                soundEngine.playTone(400f, 100, "sine")
            }
        }
    }

    fun unlockPilotSkinFromStory(skinId: String) {
        viewModelScope.launch {
            var didUnlock = false
            gameDao.updateProfile { prof ->
                val unlocked = prof.unlockedPilotSkinsCsv.split(",").toMutableList()
                if (!unlocked.contains(skinId)) {
                    unlocked.add(skinId)
                    didUnlock = true
                    prof.copy(unlockedPilotSkinsCsv = unlocked.joinToString(","))
                } else {
                    prof
                }
            }
            if (didUnlock) {
                soundEngine.playUnlockSkin()
            }
        }
    }
fun doubleGemsForRun() {
    if (_simState.value.doubleGemsClaimed) return
    _simState.value = _simState.value.copy(doubleGemsClaimed = true)
    viewModelScope.launch {
        gameDao.updateProfile { prof ->
            prof.copy(gems = prof.gems + prof.currentRunGemsCredited)
        }
        soundEngine.playUnlockSkin()
    }
}
fun purchasePilotSkin(activity: Activity, skinId: String, productId: String) {
    if (DropSchedule.isDropSuit(skinId) && !DropSchedule.TEST_OPEN && skinId !in _suitGatesOpen.value) {
        _purchaseErrorEvent.tryEmit(
            if (!DropSchedule.suitReleased(skinId, ServerClock.bestNowMs())) "🔒 This suit drops on ${DropSchedule.dateLabel(DropSchedule.suitReleaseMs(skinId))}."
            else "🔒 Defeat a boss to earn the right to buy this suit."
        )
        return
    }
    if (!DropSchedule.isDropSuit(skinId) && SuitGates.forSkin(skinId) != null && skinId !in _suitGatesOpen.value) {
        _purchaseErrorEvent.tryEmit(SuitGates.lockedMessage(skinId))
        return
    }
    RevenueCatManager.purchasePilotSuit(activity, productId) { success ->
        if (success) {
            unlockPilotSkinFromStory(skinId)
            equipPilotSkin(skinId)
        } else {
            _purchaseErrorEvent.tryEmit("Purchase failed. Please try again.")
        }
    }
}
fun reviveCostForCurrentRun(): Int {
    val base = when (_simState.value.reviveCount) {
        0 -> 20
        1 -> 25
        2 -> 30
        3 -> 40
        4 -> 50
        else -> 50 + 20 * (_simState.value.reviveCount - 4)
    }
    return (base * RemoteTuning.reviveCostPct() / 100).coerceAtLeast(1)
}
fun reviveWithGems(isPro: Boolean) {
    viewModelScope.launch {
        val cost = reviveCostForCurrentRun()
        var didRevive = false
        gameDao.updateProfile { prof ->
            if ((isPro || _simState.value.reviveCount < 3) && prof.gems >= cost) {
                didRevive = true
                prof.copy(gems = prof.gems - cost)
            } else {
                prof
            }
        }
        if (didRevive) {
            GameAnalytics.reviveUsed("gems", _simState.value.reviveCount + 1, cost)
            reviveSimulation(isPro)
        }
    }
}
fun fuelRefillCostForCurrentRun(): Int {
    val n = _simState.value.fuelRefillCount
    val base = when (n) {
        0 -> 20
        1 -> 25
        else -> 30
    }
    return StarterWallet.refuelCost(activeTotalRuns, n, base)   // gentler for the first runs, normal later
}
fun onFuelTierChanged(tier: String) {
    when (tier) {
        "warning" -> soundEngine.playTone(440f, 150, "sine")
        "critical" -> soundEngine.playTone(880f, 200, "sawtooth")
        else -> {}
    }
}

fun refuelWithGems(isPro: Boolean) {
    hintRefuelTapped = true
    viewModelScope.launch {
        val current = _simState.value
        // Free players: 5 refuels per run (Pro: unlimited).
        if (!isPro && current.fuelRefillCount >= 5) return@launch
        val cost = fuelRefillCostForCurrentRun()
        var didRefuel = false
        gameDao.updateProfile { prof ->
            if (prof.gems >= cost) {
                didRefuel = true
                prof.copy(gems = prof.gems - cost)
            } else {
                prof
            }
        }
        if (didRefuel) {
            val easyKid = selectedDifficulty == DifficultyTier.EASY
            _simState.value = _simState.value.copy(
                fuelLevelPercent = 100,
                fuelRefillCount = current.fuelRefillCount + 1,
                // EASY (kids): every refuel puts a fresh shield back on the pilot
                shieldLayers = if (easyKid) EASY_SHIELD_LAYERS else _simState.value.shieldLayers
            )
            if (easyKid) PilotShieldFx.onGain(EASY_SHIELD_LAYERS)
            soundEngine.playUnlockSkin()
        }
    }
}
fun buyExtraAttempt() {
    viewModelScope.launch {
        val cost = 25
        var didBuy = false
        gameDao.updateProfile { prof ->
            if (prof.gems >= cost) {
                didBuy = true
                prof.copy(
                    gems = prof.gems - cost,
                    dailyAttemptsToday = (prof.dailyAttemptsToday - 1).coerceAtLeast(0)
                )
            } else {
                prof
            }
        }
        if (didBuy) {
            soundEngine.playUnlockSkin()
        }
    }
}
    private val _streakRewardEvent = MutableSharedFlow<StreakReward>(extraBufferCapacity = 2)
    val streakRewardEvent: SharedFlow<StreakReward> = _streakRewardEvent.asSharedFlow()
    private val _purchaseErrorEvent = MutableSharedFlow<String>(extraBufferCapacity = 2)
    val purchaseErrorEvent: SharedFlow<String> = _purchaseErrorEvent.asSharedFlow()

    /** The daily bonus is now paid after the first game over (see completeSimulationRun / WelcomeBonus), not when the app opens. */
    fun checkDailyStreak() { viewModelScope.launch { gameDao.updateProfile { it } } } // also takes back expired bonus gems

fun streakDaysMissed(prof: GameProfile): Int {
    if (prof.lastStreakLoginDate.isEmpty()) return 0
    val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    val today = fmt.format(Date())
    if (prof.lastStreakLoginDate == today) return 0
    val lastDate = fmt.parse(prof.lastStreakLoginDate)
    val todayDate = fmt.parse(today)
    return ((todayDate.time - lastDate.time) / (1000 * 60 * 60 * 24)).toInt()
}
suspend fun isStreakFreezeEligible(): Boolean {
    val prof = gameDao.getProfileDirect() ?: GameProfile()
    return false // missing a day resets the welcome bonus to day 1; no streak freeze
}
fun freezeStreak() {
    viewModelScope.launch {
        val cost = 25
        var didFreeze = false
        gameDao.updateProfile { prof ->
            val missed = streakDaysMissed(prof)
            if (missed == 2 && prof.gems >= cost) {
                didFreeze = true
                val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                val cal = Calendar.getInstance()
                cal.add(Calendar.DAY_OF_YEAR, -1)
                val yesterday = fmt.format(cal.time)
                prof.copy(gems = prof.gems - cost, lastStreakLoginDate = yesterday)
            } else {
                prof
            }
        }
        if (didFreeze) {
            soundEngine.playUnlockSkin()
            checkDailyStreak()
        }
    }
}

    init {
        viewModelScope.launch {
            profile.collect { p ->
                activeUpgradesCsv = p?.upgradesCsv ?: ""
                activeMasteryPoints = p?.masteryPoints ?: 0
                activeTotalRuns = p?.totalRuns ?: 0
            }
        }
        hangar.start(); progression.start()
        HullPurchases.loadPrices()
        seasonPass.start()   // offline-first: must NOT wait for the network sign-in below
        viewModelScope.launch {
            try {
                val auth = FirebaseAuth.getInstance()
                val uid = auth.currentUser?.uid ?: auth.signInAnonymously().await().user?.uid
                if (uid != null) ServerClock.sync(uid)
            } catch (_: Exception) {}
            proPerks.refresh(); hangar.reconcileWithStore(); seasonPass.refresh()
        }
        viewModelScope.launch {
            RevenueCatManager.isPro.collect { fuelGate.touchAsync(it); proPerks.refresh(); seasonPass.refresh(); thrusters.recheckDrops(); try { gameDao.getProfileDirect()?.let { thrusters.evaluate(it) } } catch (_: Exception) {} }
        }
        LegendTrack.init(appCtx)
        restoreDifficulty()
        viewModelScope.launch { while (true) { StormBonus.applyExpiry(gameDao); delay(60_000) } }   // storm gems expire after 2 days
        viewModelScope.launch { StarterWallet.check(context.applicationContext, gameDao) }   // new players: starter wallet, first 3 days
        viewModelScope.launch { loadSuitGates() }
        // ── Offline purchase sync ──────────────────────────────────────────────────
        // Whenever the store (or the encrypted local ledger) reports entitlements, mirror them into the game.
        RevenueCatManager.onInfoApplied = { viewModelScope.launch { reconcileEntitlements(fromStore = true) } }
        viewModelScope.launch {
            deliverPendingPurchases()      // paid-but-not-granted gem packs from a previous session
            reconcileEntitlements()        // works from the ledger even with no internet
            RevenueCatManager.syncNow()    // and refresh from the store if we are online right now
        }
        loadSocialComments()
        prepopulateSampleGhostChallenges()
        viewModelScope.launch {
            FirebaseLeaderboardManager.fetchTopScores(_boardTier.value.boardKey)
        }
    }

    private fun prepopulateSampleGhostChallenges() {
        viewModelScope.launch {
            gameDao.insertGhost(
                GhostChallengeEntity(
                    challengeId = "ghost_retro",
                    playerName = "RetroWave",
                    score = 240,
                    zoneReached = 2,
                    yPositionsCsv = ZoneGenerator.generateTelemetryCsv(240, 42)
                )
            )
            gameDao.insertGhost(
                GhostChallengeEntity(
                    challengeId = "ghost_zeroglitch",
                    playerName = "ZeroGlitch",
                    score = 480,
                    zoneReached = 3,
                    yPositionsCsv = ZoneGenerator.generateTelemetryCsv(480, 84)
                )
            )
            gameDao.insertGhost(
                GhostChallengeEntity(
                    challengeId = "ghost_cyberrunner",
                    playerName = "CyberRunner",
                    score = 850,
                    zoneReached = 4,
                    yPositionsCsv = ZoneGenerator.generateTelemetryCsv(850, 111)
                )
            )
        }
    }

    private fun loadDefaultLeaderboard() {
        viewModelScope.launch {
            FirebaseLeaderboardManager.fetchTopScores(_boardTier.value.boardKey)
        }
    }

    private fun loadSocialComments() {
        _socialComments.value = listOf(
            SocialComment("CyberRunner", "Just secured a 850 run using the Matrix Grid fighter! The drafting is key in Warp Voids.", "10m ago", "Singularity Terminus"),
            SocialComment("ZeroGlitch", "That freeze hazard in Zone 3 completely ruined my sliding angles. Watch your thrusters!", "1h ago", "Methane Basin"),
            SocialComment("RetroWave", "Anyone else thinks the Cyan Diamond skin handles smoother on Chromium light rings?", "3h ago", "Chromium Grid"),
            SocialComment("DriftKing_X", "Is the Daily Rush active? Heard it gives 50 free gems today.", "4h ago", "Cyber Alley"),
            SocialComment("NeonVolt", "Unbelievable speed. I made the top 10 today but dropped immediately.", "6h ago", "Chromium Grid")
        )
    }

    fun toggleFollowUser(pilotName: String) {
        viewModelScope.launch {
            gameDao.updateProfile { prof ->
                val followedList = prof.followedUsersCsv.split(",").filter { it.isNotEmpty() }.toMutableList()
                if (followedList.contains(pilotName)) {
                    followedList.remove(pilotName)
                } else {
                    followedList.add(pilotName)
                }
                prof.copy(followedUsersCsv = followedList.joinToString(","))
            }
            loadDefaultLeaderboard()
        }
    }

    private val _crateResult = MutableStateFlow<CrateResult?>(null)
    val crateResult: StateFlow<CrateResult?> = _crateResult.asStateFlow()

    /** Opens today's crate (the UI shows the ad first for free players). */
    fun openDailyCrate(isPro: Boolean) {
        viewModelScope.launch {
            var result: CrateResult? = null
            gameDao.updateProfile { prof ->
                if (!DailyCrate.isReady(prof, isPro)) prof
                else {
                    val (updated, res) = DailyCrate.roll(prof, isPro)
                    result = res
                    updated
                }
            }
            result?.let {
                _crateResult.value = it
                soundEngine.playUnlockSkin()
            }
        }
    }

    fun dismissCrateResult() { _crateResult.value = null }

    /** Friends: publish this pilot's saved bests (all difficulties) so friends can see them. Called when the Friends dialog opens. */
    fun syncSocial(name: String) {
        viewModelScope.launch {
            for (t in DifficultyTier.values()) {
                val best = hangarStore.getLong("best_" + t.name).toInt()
                if (best > 0) Social.publishScore(name, t.boardKey, best)
            }
        }
    }

    /** Live events: claim one reward tier. The tier is marked taken first (so a double tap pays once), then the gems are added. */
    fun claimEventReward(tier: Int) {
        val gems = LiveEvents.claim(tier)
        if (gems <= 0) return
        viewModelScope.launch {
            gameDao.updateProfile { prof -> prof.copy(gems = prof.gems + gems) }
            soundEngine.playUnlockSkin()
        }
    }

    fun purchaseUpgrade(upgradeId: String, isPro: Boolean) {
        viewModelScope.launch {
            val def = Upgrades.ALL.find { it.id == upgradeId } ?: return@launch
            var bought = false
            gameDao.updateProfile { prof ->
                val lvl = Upgrades.level(prof.upgradesCsv, upgradeId)
                val cost = Upgrades.nextCost(def, lvl)
                if (lvl < Upgrades.maxLevelFor(isPro) && prof.gems >= cost) {
                    bought = true
                    prof.copy(
                        gems = prof.gems - cost,
                        upgradesCsv = Upgrades.setLevel(prof.upgradesCsv, upgradeId, lvl + 1)
                    )
                } else prof
            }
            if (bought) soundEngine.playUnlockSkin()
        }
    }

    /** Classic hulls now cost real money and follow the 2-month calendar. */
    fun purchaseClassicHull(activity: Activity, hullId: String) {
        val h = DropSchedule.classicHull(hullId) ?: return
        if (!DropSchedule.classicReleased(hullId, ServerClock.bestNowMs())) {
            _purchaseErrorEvent.tryEmit("🔒 This hull drops on ${DropSchedule.dateLabel(DropSchedule.classicReleaseMs(hullId))}.")
            return
        }
        RevenueCatManager.purchasePilotSuit(activity, h.productId) { success ->
            if (success) {
                viewModelScope.launch {
                    gameDao.updateProfile { prof ->
                        val set = prof.unlockedSkinsCsv.split(",").filter { it.isNotBlank() }.toMutableList()
                        if (hullId !in set) set.add(hullId)
                        prof.copy(unlockedSkinsCsv = set.joinToString(","), activeSkinId = hullId)
                    }
                    hangar.noteEquipped(hullId)
                    soundEngine.playUnlockSkin()
                    loadDefaultLeaderboard()
                }
            } else {
                _purchaseErrorEvent.tryEmit("Purchase failed. Please try again.")
            }
        }
    }

    /** Buys a real-money thruster trail (one-time, via the store) once its monthly drop has opened for this player. */
    fun purchaseTrail(activity: Activity, trailId: String) {
        val t = ThrusterCatalog.ALL.firstOrNull { it.id == trailId } ?: return
        if (!t.realMoney || trailId in thrusters.state.value.owned) return
        val at = ThrusterCatalog.buyableAtMs(t, RevenueCatManager.isPro.value)
        if (at > ServerClock.bestNowMs()) {
            _purchaseErrorEvent.tryEmit("🔒 ${t.name} drops on ${java.text.SimpleDateFormat("d MMM yyyy", java.util.Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }.format(java.util.Date(ThrusterCatalog.releaseMs(t)))}.")
            return
        }
        RevenueCatManager.purchasePilotSuit(activity, t.productId) { success ->
            if (success) { thrusters.grantPurchased(trailId); soundEngine.playUnlockSkin() }
            else _purchaseErrorEvent.tryEmit("Purchase failed. Please try again.")
        }
    }

    fun purchaseSkin(skinId: String, cost: Int) {
        viewModelScope.launch {
            var outcome = "none" // "equipped", "bought", or "none"
            gameDao.updateProfile { prof ->
                val unlockedSkins = prof.unlockedSkinsCsv.split(",").toMutableList()
                if (unlockedSkins.contains(skinId)) {
                    outcome = "equipped"
                    prof.copy(activeSkinId = skinId)
                } else if (DropSchedule.classicHull(skinId) == null && prof.gems >= cost) {
                    unlockedSkins.add(skinId)
                    outcome = "bought"
                    prof.copy(
                        gems = prof.gems - cost,
                        unlockedSkinsCsv = unlockedSkins.joinToString(","),
                        activeSkinId = skinId
                    )
                } else {
                    prof
                }
            }
            if (outcome != "none") hangar.noteEquipped(skinId)
            when (outcome) {
                "equipped" -> {
                    soundEngine.playTone(400f, 100, "sine")
                    loadDefaultLeaderboard()
                }
                "bought" -> {
                    soundEngine.playUnlockSkin()
                    loadDefaultLeaderboard()
                }
            }
        }
    }

    var avgZoneReached: Float = 10f
    var frustrationIndex: Float = 0.5f

    /**
     * Pressure grows with the zone: pressure(zone) = startMult * (1 + growthPer10 * (zone - 1) / 10).
     * It drives belt speed, obstacle density, threat frequency and boss fire rate together.
     * EASY +3% / 10 zones, MEDIUM +6%, HARD +11%, LEGENDARY +13%.
     * startMult makes zone 1 already feel different per tier (tweak freely).
     */
    enum class DifficultyTier(
        val growthPer10: Float, val startMult: Float, val label: String,
        val warnDelta: Int,       // telegraph time vs normal, in 120 ms ticks
        val gemMult: Float,       // gem reward multiplier
        val powerupMul: Float,    // power-up drop-rate multiplier
        val tokenMult: Float,     // Season Pass token multiplier
        val colorArgb: Long,      // tier colour for HUD / share card
        val boardKey: String      // Firestore collection suffix ("" = legacy global board)
    ) {
        EASY(0.02f, 0.80f, "EASY", +3, 0.80f, 1.25f, 1.00f, 0xFF69F0AE, "_easy"),
        MEDIUM(0.06f, 1.00f, "MEDIUM", 0, 1.00f, 1.00f, 1.00f, 0xFF00F0FF, ""),
        HARD(0.11f, 1.16f, "HARD", -1, 1.25f, 0.80f, 1.15f, 0xFFFF9100, "_hard"),
        LEGENDARY(0.13f, 1.27f, "LEGENDARY", -2, 1.60f, 0.65f, 1.30f, 0xFFFFD23F, "_legendary");

        /**
         * The "pressure gauge". Speed and aggression follow this zone, not the real one: every 60 zones the gauge drops back
         * (to zone 10's level, and to zone 15's level from zone 300 on) and climbs again, so very deep runs never get unfair
         * and the player gets a breather. Zones 1-59 are the real zone numbers.
         */
        fun gaugeZone(zone: Int): Int {
            if (zone < 60) return zone.coerceAtLeast(1)
            val base = if (zone >= 300) 15 else 10
            return base + zone % 60
        }

        /** Every tier steps up EVERY zone: EASY gently (0.8%), MEDIUM faster (1.2%), HARD fastest. EASY / MEDIUM never go past HARD. */
        private fun paced(zone: Int): Float {
            val z = (gaugeZone(zone) - 1).coerceAtLeast(0)
            val raw = when (this) {
                EASY -> 0.76f * (1f + 0.008f * z)
                else -> 0.94f * (1f + 0.012f * z)
            }
            return minOf(raw, HARD.hardCurve(zone))
        }

        private fun hardCurve(zone: Int): Float = startMult * (1f + growthPer10 * (gaugeZone(zone) - 1).coerceAtLeast(0) / 10f)

        /** Speed. Stage 70: +7% EASY, +10% MEDIUM, +14% HARD (each +2% over Stage 67) on top of the gauge curve (the 60-zone gauge still caps how far it can climb). */
        fun pressure(zone: Int): Float = when (this) {
            EASY -> paced(zone) * 1.0914f
            MEDIUM -> paced(zone) * 1.122f
            HARD -> hardCurve(zone) * 1.1628f
            else -> hardCurve(zone)
        }

        /**
         * Aggression (obstacle density, threat rate, boss fire) uses the same gauge. EASY grows at half the rate of its speed,
         * MEDIUM follows its speed, HARD / LEGENDARY climb at 40% of their speed growth.
         */
        fun aggression(zone: Int): Float {
            if (this == EASY || this == MEDIUM) {
                val ratio = paced(zone) / paced(1)
                val cap = HARD.hardAggro(zone) * 1.02f
                return if (this == EASY) minOf(0.45f * 1.0608f * (1f + 0.5f * (ratio - 1f)), cap)
                else minOf(0.84f * 1.02f * ratio, cap)
            }
            return if (this == HARD) hardAggro(zone) * 1.02f else hardAggro(zone)
        }

        private fun hardAggro(zone: Int): Float = startMult * (1f + growthPer10 * 0.4f * (gaugeZone(zone) - 1).coerceAtLeast(0) / 10f)
    }

    /** Observe this in the UI so the buttons recompose when the tier changes. */

    /** Player choice sticks (also across app restarts) until they tap another tier. */
    fun setDifficulty(tier: DifficultyTier) {
        if (tier == DifficultyTier.LEGENDARY && !(RevenueCatManager.isPro.value || TESTING_DISABLE_PRO_GATE)) return
        selectedDifficulty = tier
        _difficulty.value = tier
        _boardTier.value = tier
        viewModelScope.launch { FirebaseLeaderboardManager.fetchTopScores(tier.boardKey) }
        viewModelScope.launch { try { hangarStore.put("difficulty_tier", tier.name) } catch (_: Exception) {} }
    }

    /** Which tier's leaderboard the Leaderboards tab is showing. */
    fun setBoardTier(tier: DifficultyTier) {
        _boardTier.value = tier
        viewModelScope.launch { FirebaseLeaderboardManager.fetchTopScores(tier.boardKey) }
    }

    private fun restoreDifficulty() {
        viewModelScope.launch {
            try {
                val saved = DifficultyTier.values().firstOrNull { it.name == hangarStore.get("difficulty_tier") } ?: DifficultyTier.MEDIUM
                val tier = if (saved == DifficultyTier.LEGENDARY && !(RevenueCatManager.isPro.value || TESTING_DISABLE_PRO_GATE)) DifficultyTier.MEDIUM else saved
                selectedDifficulty = tier; _difficulty.value = tier; _boardTier.value = tier
                FirebaseLeaderboardManager.fetchTopScores(tier.boardKey)
            } catch (_: Exception) {}
        }
    }

    var deathTimes: MutableList<Long> = mutableListOf()
    val deathHeatmap: MutableMap<String, Int> = mutableMapOf()
    var hitObstaclesHistory: MutableList<String> = mutableListOf()
    var totalPlayedSeconds: Float = 0f
    var lastPlayTime: Long = System.currentTimeMillis()

    private var lastPuSpawnTick = 0
    private var fuelFreezeUntilTick = 0

    /** One tick of boss tracking: always moves toward the pilot (at least 1 unit, at most 7), so the boss really stays on target. */
    private fun bossFollowStep(diff: Int, rate: Float): Int {
        if (diff == 0) return 0
        val raw = diff * rate
        val mag = kotlin.math.abs(raw).coerceIn(1f, 7f).toInt().coerceAtLeast(1)
        return if (diff > 0) minOf(mag, diff) else -minOf(mag, -diff)
    }

    private fun chooseProceduralPowerupType(rand: kotlin.random.Random, isSunday: Boolean): String {
        val roll = rand.nextInt(100)
        val legendaryThreshold = if (isSunday) 25 else 5
        val rareThreshold = if (isSunday) 55 else 25
        return when {
            roll < legendaryThreshold -> if (rand.nextBoolean()) "PU11" else "PU12"
            roll < legendaryThreshold + rareThreshold -> "PU" + rand.nextInt(6, 11)
            else -> "PU" + rand.nextInt(1, 6)
        }
    }

    private fun isBlinkHazardVisible(elem: VisualTrackElement, tick: Int): Boolean {
        if (elem.subType != "BLINK_HAZARD") return true
        val spawnTick = elem.id.removePrefix("blink_").substringBefore("_").toIntOrNull() ?: return true
        val cycleLength = 25 // ~1s visible (8 ticks) + ~2s invisible (17 ticks) at 120ms/tick
        val phase = ((tick - spawnTick) % cycleLength + cycleLength) % cycleLength
        return phase < 8
    }

    /** A lane that is off the ghost line (so the ghost line stays safe) but can be anywhere on screen, top and bottom included. */
    private fun offLine(rand: kotlin.random.Random, ghostY: Int, minD: Int, maxD: Int): Int {
        val d = rand.nextInt(minD, maxD + 1)
        val up = rand.nextBoolean()
        var y = if (up) ghostY - d else ghostY + d
        if (y < 6 || y > 94) y = if (up) ghostY + d else ghostY - d
        return y.coerceIn(4, 96)
    }

    private fun spawnObstacleForSet(obstacleSetId: Int, tick: Int, rand: kotlin.random.Random, ghostY: Int, zoneNumber: Int = 1): List<VisualTrackElement> {
        val elements = mutableListOf<VisualTrackElement>()
        val baseId = "obs_${tick}_"
        when (obstacleSetId) {
            1 -> {
                val gapSize = rand.nextInt(20, 30)
                val gapCenter = (ghostY + rand.nextInt(-10, 10)).coerceIn(35, 65)
                elements.add(VisualTrackElement("${baseId}p1", 1.2f, gapCenter + gapSize/2, "obstacle", "PILLAR_BOTTOM"))
                elements.add(VisualTrackElement("${baseId}p2", 1.2f, gapCenter - gapSize/2, "obstacle", "PILLAR_TOP"))
            }
            2 -> {
                elements.add(VisualTrackElement("${baseId}l1", 1.2f, rand.nextInt(8, 93), "obstacle", "LASER"))
                if (rand.nextBoolean()) {
                    elements.add(VisualTrackElement("${baseId}l2", 1.25f, rand.nextInt(8, 93), "obstacle", "LASER"))
                }
            }
            3 -> elements.add(VisualTrackElement("${baseId}b1", 1.3f, ghostY, "obstacle", "BLADE"))
            4 -> elements.add(VisualTrackElement("${baseId}st1", 1.2f, rand.nextInt(6, 45), "obstacle", "STALACTITE"))
            5 -> elements.add(VisualTrackElement("${baseId}sm1", 1.2f, rand.nextInt(55, 95), "obstacle", "STALAGMITE"))
            6 -> {
                elements.add(VisualTrackElement("${baseId}sa1", 1.2f, 12, "obstacle", "STALACTITE"))
                elements.add(VisualTrackElement("${baseId}sa2", 1.2f, 88, "obstacle", "STALAGMITE"))
            }
            7, 11 -> {
                elements.add(VisualTrackElement("${baseId}m1", 1.2f, (ghostY - 12).coerceIn(4, 96), "obstacle", "BARRIER"))
                elements.add(VisualTrackElement("${baseId}m2", 1.2f, (ghostY + 12).coerceIn(4, 96), "obstacle", "BARRIER"))
            }
            15 -> elements.add(VisualTrackElement("${baseId}z1", 1.2f, rand.nextInt(6, 95), "obstacle", "ZAP_FIELD"))
            17 -> elements.add(VisualTrackElement("${baseId}f1", 1.2f, offLine(rand, ghostY, 14, 30), "obstacle", "PHANTOM"))
            18 -> {
                elements.add(VisualTrackElement("${baseId}s1", 1.2f, ghostY - 6, "obstacle", "SPLITTER"))
                elements.add(VisualTrackElement("${baseId}s2", 1.2f, ghostY + 6, "obstacle", "SPLITTER"))
            }
            22 -> {
                elements.add(VisualTrackElement("${baseId}tu1", 1.2f, (ghostY - 20).coerceIn(4, 96), "obstacle", "TUNNEL_TOP"))
                elements.add(VisualTrackElement("${baseId}tu2", 1.2f, (ghostY + 20).coerceIn(4, 96), "obstacle", "TUNNEL_BOTTOM"))
            }
            23 -> {
                val droneY = offLine(rand, ghostY, 14, 36)
                elements.add(VisualTrackElement("${baseId}dr", 1.2f, droneY, "obstacle", "DRONE"))
                // 30% chance the drone also fires a companion projectile —
                // reuses the existing "bullet" collision handling (no new
                // damage logic needed), spawned slightly ahead so dodging
                // the drone's body and dodging its shot are two separate
                // reads, not the same dodge twice.
                if (rand.nextInt(100) < 30) {
                    elements.add(VisualTrackElement("${baseId}drb", 1.35f, (droneY + rand.nextInt(-10, 10)).coerceIn(4, 96), "bullet", "DRONE_SHOT"))
                }
            }
            else -> elements.add(VisualTrackElement("${baseId}st", 1.2f, offLine(rand, ghostY, 16, 40), "obstacle", "STANDARD"))
        }
        // BLINK STRIKE: a new, per-world "creature" hazard that flickers
        // visible (~1s) then near-invisible (~2s), like lightning. It cannot
        // be neutralized by shields/invincibility/ghost mode while visible —
        // dodging it is the only way past. Gated to zone 4+ so tutorial zones
        // stay calm. kindIdx (0-4) gives 5 silhouette/color variants per world.
        if (zoneNumber >= 4 && rand.nextInt(100) < 20) {
            val kindIdx = rand.nextInt(0, 5)
            val hazardY = (ghostY + rand.nextInt(-25, 25)).coerceIn(8, 92)
            elements.add(VisualTrackElement("blink_${tick}_${kindIdx}", 1.2f, hazardY, "obstacle", "BLINK_HAZARD"))
        }
        return elements
    }

    fun triggerTranscendence() {
        viewModelScope.launch {
            var didTranscend = false
            gameDao.updateProfile { prof ->
                if (prof.bestScore >= 50000) {
                    didTranscend = true
                    val nextPrestige = prof.transcendenceCount + 1
                    val currentUnlocked = prof.unlockedSkinsCsv.split(",").filter { it.isNotEmpty() }.toMutableList()
                    val exclusiveSkin = when (nextPrestige) {
                        1 -> "gold_transcendence"
                        2 -> "matrix_grid"
                        else -> "elite_nebula"
                    }
                    if (!currentUnlocked.contains(exclusiveSkin)) {
                        currentUnlocked.add(exclusiveSkin)
                    }
                    prof.copy(
                        transcendenceCount = nextPrestige,
                        bestScore = 0,
                        gems = prof.gems + 35,
                        unlockedSkinsCsv = currentUnlocked.joinToString(","),
                        activeSkinId = exclusiveSkin
                    )
                } else {
                    prof
                }
            }
            if (didTranscend) {
                gameDao.getProfileFlow().value?.activeSkinId?.let { hangar.noteEquipped(it) }
                soundEngine.playTone(880f, 500, "sawtooth")
                loadDefaultLeaderboard()
            }
        }
    }

    /** Storm Run: how many attempts a day this player gets (Pro gets 2 extra). */
    fun stormDailyMax(): Int = if (RevenueCatManager.isPro.value) 6 else 3

    /** Uses one Storm Run attempt (no sim is started; the Storm Run screen is its own mini-game). */
    fun consumeStormAttempt(onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            var ok = false
            val max = stormDailyMax()
            gameDao.updateProfile { prof ->
                val todayDate = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
                val attempts = if (prof.lastDailyRushDate == todayDate) prof.dailyAttemptsToday else 0
                if (attempts >= max) prof else { ok = true; prof.copy(dailyAttemptsToday = attempts + 1, lastDailyRushDate = todayDate) }
            }
            onResult(ok)
        }
    }

    /** A rewarded ad earned one more Storm Run attempt: give one back, then spend it immediately on the new run. */
    fun grantAndConsumeStormAttempt(onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            gameDao.updateProfile { prof ->
                val todayDate = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
                if (prof.lastDailyRushDate == todayDate) prof.copy(dailyAttemptsToday = (prof.dailyAttemptsToday - 1).coerceAtLeast(0)) else prof
            }
            consumeStormAttempt(onResult)
        }
    }

    fun creditStormGems(n: Int) {
        if (n <= 0) return
        viewModelScope.launch { gameDao.updateProfile { p -> p.copy(gems = p.gems + n, totalGemsEarned = p.totalGemsEarned + n) } }
    }

    fun spendStormGems(n: Int, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            var ok = false
            gameDao.updateProfile { p -> if (p.gems >= n) { ok = true; p.copy(gems = p.gems - n) } else p }
            onResult(ok)
        }
    }

    fun runDailyRushChallenge(onComplete: (Boolean, Int) -> Unit) {
        viewModelScope.launch {
            var newAttempts = -1
            gameDao.updateProfile { prof ->
                val todayDate = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
                val attempts = if (prof.lastDailyRushDate == todayDate) prof.dailyAttemptsToday else 0
                if (attempts >= 3) {
                    prof
                } else {
                    newAttempts = attempts + 1
                    prof.copy(
                        dailyAttemptsToday = attempts + 1,
                        lastDailyRushDate = todayDate
                    )
                }
            }
            if (newAttempts < 0) {
                onComplete(false, 0)
                return@launch
            }
            val hardGhost = GhostChallengeEntity(
                challengeId = "daily_hard_ghost",
                playerName = "GlitchViper [VIRTUAL]",
                score = 350,
                zoneReached = 3,
                yPositionsCsv = ZoneGenerator.generateTelemetryCsv(350, 99)
            )
            startRacingSimulation(hardGhost, null, 3)
            onComplete(true, newAttempts)
        }
    }

    /** Daily Storm only: gusts that shove the pilot sideways; the player has to keep correcting. */
    private fun stormWind(st: SimulationState, tick: Int): Int =
        if (st.activeGhost?.challengeId == "daily_hard_ghost") StormWind.push(tick) else 0

    /** Which of the 8 world boss families this zone belongs to (special / New Game+ worlds map onto the same 8). */
    private fun worldFamilyFor(st: SimulationState, zone: Int): Int {
        val w = st.specialWorldId?.let { envId ->
            (Worlds.SPECIAL_WORLDS + Worlds.NEW_GAME_PLUS).find { it.environmentIds.contains(envId) }
        } ?: Worlds.worldForZone(zone)
        return w.worldFamily
    }

    /** Gameplay personality for this tick's zone (null = normal tuning). */
    private fun flavorFor(st: SimulationState, zone: Int): ZoneFlavor? {
        val w = st.specialWorldId?.let { envId ->
            (Worlds.SPECIAL_WORLDS + Worlds.NEW_GAME_PLUS).find { it.environmentIds.contains(envId) }
        } ?: Worlds.worldForZone(zone)
        return WorldFlavors.forZone(w.id, w.worldFamily, zone)
    }

    fun overrideEnvironment(dna: ZoneDNA, envId: Int): ZoneDNA {
    val (name, emoji, color) = ZoneGenerator.ENVIRONMENTS[envId]
    return dna.copy(environmentId = envId, environmentName = name, environmentEmoji = emoji, environmentColor = color)
}

// ============================================================
// Invisible onboarding (see Hints.kt): contextual prompts, no manual.
// ============================================================
private var hintStatus: MutableMap<String, Int> = mutableMapOf()
private val hintShownThisRun = mutableSetOf<String>()
@Volatile private var hintRefuelTapped = false

private fun beginHintsForNewRun(csv: String) {
    hintStatus = HintEngine.parse(csv)
    hintShownThisRun.clear()
    hintRefuelTapped = false
}

private fun persistHints() {
    val snapshot = HintEngine.serialize(hintStatus)
    viewModelScope.launch { gameDao.updateProfile { p -> p.copy(hintsCsv = snapshot) } }
}

private fun stepHints(prev: HintState, input: HintInput): HintState {
    val r = HintEngine.step(prev, input, hintStatus, hintShownThisRun)
    r.shown?.let {
        hintShownThisRun.add(it.id)
        hintStatus[it.id] = (hintStatus[it.id] ?: 0) + 1
        persistHints()
    }
    r.completed?.let {
        hintStatus[it.id] = HintEngine.DONE
        persistHints()
        if (!r.quiet) soundEngine.playTone(880f, 110, "triangle")
    }
    return r.state
}

// ============================================================
// First-run "guaranteed high": a gentle first run + close-call feedback.
// ============================================================
private var firstRunAssist = false
private val grazedIds = mutableSetOf<String>()
private val nearMiss = NearMissDirector()
private var assistGemsGiven = 0
private val FIRST_FLIGHT_GEMS = 60

// 1.0 for the first ~18 s of the very first run, fading to 0 by ~54 s so the
// difficulty ramps up naturally instead of falling off a cliff.
private fun assistStrength(tick: Int): Float =
    if (!firstRunAssist) 0f else (1f - (tick - 150) / 300f).coerceIn(0f, 1f)

private fun hintTickDelayMs(): Long =
    if (android.os.SystemClock.elapsedRealtime() < _simState.value.hint.slowMoUntilMs) 240L else 120L

fun startSpecialModeRun(ghost: GhostChallengeEntity, tier: Int? = null) {
    viewModelScope.launch {
        val prof = gameDao.getProfileDirect() ?: GameProfile()
        // New Game+ worlds (tiers 101..103): Pro + World 8 completed (Worlds.isWorldUnlocked).
        if (tier != null && tier in 101..103) {
            val ngWorld = Worlds.newGamePlusWorldForTier(tier - 100)
            val done = prof.completedWorldsCsv.split(",").mapNotNull { it.toIntOrNull() }.toSet()
            val isProNow = prof.subscriptionPro || TESTING_DISABLE_PRO_GATE
            val env = ngWorld?.environmentIds?.firstOrNull()
            if (ngWorld != null && env != null && Worlds.isWorldUnlocked(ngWorld, isProNow, done)) {
                startRacingSimulation(ghost, specialWorldId = env)
            }
            return@launch
        }
        // Any unlocked world can be chosen; never allow a tier above what is unlocked.
        val chosenTier = (tier ?: prof.specialWorldTier).coerceIn(1, maxOf(1, prof.specialWorldTier))
        val world = Worlds.specialWorldForTier(chosenTier)
        val envId = world?.environmentIds?.firstOrNull()
        if (envId != null) {
            startRacingSimulation(ghost, specialWorldId = envId)
        }
    }
}

fun startFromCheckpoint(checkpointZone: Int) {
    viewModelScope.launch {
        var canStart = false
        gameDao.updateProfile { prof ->
            val activated = prof.checkpointsActivatedCsv.split(",").filter { it.isNotEmpty() }
            if (checkpointZone.toString() in activated) {
                canStart = true
                prof
            } else {
                val cost = checkpointZone * 3
                if (prof.gems >= cost) {
                    canStart = true
                    prof.copy(
                        gems = prof.gems - cost,
                        checkpointsActivatedCsv = (activated + checkpointZone.toString()).joinToString(",")
                    )
                } else {
                    prof
                }
            }
        }
        if (canStart) {
            val checkpointGhost = com.neonrush.game.db.GhostChallengeEntity(
                "ghost_cyberrunner",
                "CyberRunner",
                850,
                4,
                ZoneGenerator.generateTelemetryCsv(850, 111)
            )
            startRacingSimulation(checkpointGhost, null, checkpointZone)
        }
    }
}

/** Legendary run sounds: 1 = three rising notes at the start, 2 = a two-note chime every 10 zones. */
fun legendSting(kind: Int) {
    viewModelScope.launch {
        try {
            if (kind == 1) { soundEngine.playTone(392f, 160, "triangle"); delay(150); soundEngine.playTone(523f, 160, "triangle"); delay(150); soundEngine.playTone(784f, 420, "sine") }
            else { soundEngine.playTone(880f, 120, "sine"); delay(110); soundEngine.playTone(1175f, 260, "sine") }
        } catch (_: Throwable) { }
    }
}

/** Checkpoint spacing: every 35 zones for Pro, every 45 for free players. */
fun checkpointEvery(): Int = if (RevenueCatManager.isPro.value || TESTING_DISABLE_PRO_GATE) 35 else 45

/** Legendary Rewards Track: collect every reached tier. */
fun claimLegendTrack(onDone: (Int) -> Unit = {}) {
    viewModelScope.launch { val g = LegendTrack.claim(appCtx, gameDao); if (g > 0) onDone(g) }
}
/** Pilot Contracts: make today's set, collect, reroll. */
fun ensureContracts() {
    viewModelScope.launch { val p = gameDao.getProfileDirect() ?: return@launch; Contracts.ensure(appCtx, p.bestZoneReached, p.bestScore.toInt(), p.bestComboStreak); Contracts.version += 1 }
}
fun claimContract(i: Int, onDone: (Int) -> Unit = {}) {
    viewModelScope.launch { val g = Contracts.claim(appCtx, gameDao, i); if (g > 0) onDone(g) }
}
fun rerollContract(i: Int) {
    viewModelScope.launch { val p = gameDao.getProfileDirect() ?: return@launch; Contracts.reroll(appCtx, i, p.bestZoneReached, p.bestScore.toInt(), p.bestComboStreak) }
}

/** Beacon Chain: collect today's beacon. */
fun claimBeacon(isPro: Boolean, onDone: (Int) -> Unit = {}) {
    viewModelScope.launch { val g = BeaconChain.claim(appCtx, gameDao, isPro); if (g > 0) onDone(g) }
}
/** Mystery Realm Doors: open one of today's three doors. */
fun openDoor(i: Int, onDone: (Int) -> Unit = {}) {
    viewModelScope.launch { val g = MysteryDoors.open(appCtx, gameDao, i); onDone(g) }
}

/** Golden Gate pass etc.: a flat score bonus on the live state. */
fun addBonusScore(n: Int) { val st = _simState.value; _simState.value = st.copy(score = st.score + n) }
/** Shooting: a bolt hit an obstacle / the boss. Works on the live state, so both race loops pick the change up on their next tick. */
fun shootDown(id: String) {
    val st = _simState.value
    val e = st.activeTrackElements.firstOrNull { it.id == id } ?: return
    ReactFx.obstacle(e.subType, "BOOM", e.xOffsetFraction, e.yMatchPos)
    LegendMoves.charge(0.08f)
    _simState.value = st.copy(activeTrackElements = st.activeTrackElements.filter { it.id != id }, score = st.score + 3)
}
/** Special-world hazards (WorldMechanicsUi): a hit costs a shield layer first (unless [soft]), then fuel. */
fun worldHit(fuelLoss: Int, soft: Boolean) {
    val st = _simState.value
    if (!soft && st.shieldLayers > 0) { PilotShieldFx.onHit(); soundEngine.playShieldBreak(); _simState.value = st.copy(shieldLayers = st.shieldLayers - 1); return }
    soundEngine.playCollision()
    if (TESTING_DISABLE_PRO_GATE) return
    _simState.value = st.copy(fuelLevelPercent = (st.fuelLevelPercent - fuelLoss).coerceAtLeast(0))
}
/** Raw height nudge for gravity pockets and wind gusts (ignores inverted-control worlds on purpose). */
fun worldNudgeY(d: Int) { val st = _simState.value; _simState.value = st.copy(userYPos = (st.userYPos + d).coerceIn(10, 90)) }
/** Legendary moves (buttons in LegendMovesUi). Each returns true when it actually fired. */
fun legendDash(): Boolean = selectedDifficulty == DifficultyTier.LEGENDARY && LegendMoves.dash()
fun legendOverdrive(): Boolean = selectedDifficulty == DifficultyTier.LEGENDARY && LegendMoves.overdrive()
/** Magnet pulse: every gem and fuel cell on screen jumps onto the pilot and is collected on the next tick. */
fun legendPulse(): Boolean {
    if (selectedDifficulty != DifficultyTier.LEGENDARY || !LegendMoves.pulse()) return false
    val st = _simState.value
    val uy = st.userYPos
    _simState.value = st.copy(activeTrackElements = st.activeTrackElements.map { if (it.type == "gem" || it.type == "fuel") it.copy(xOffsetFraction = 0.22f, yMatchPos = uy) else it })
    return true
}
/** Air Support (AirSupportUi): a missile hits the boss. Breaks its shield, ignores the shield bounce, can finish it. */
fun airBossStrike(dmg: Float) {
    val st = _simState.value
    if (!st.bossActive) return
    BossGuard.shieldUp = false
    BossDamageFx.hit(dmg, true)
    _simState.value = st.copy(bossHealth = (st.bossHealth - dmg).coerceAtLeast(0f), score = st.score + 25)
}
/** Air Support guns: removes obstacles and enemy bullets up to [maxX] (fraction of the width). Returns how many went. */
fun airSweep(maxX: Float): Int {
    val st = _simState.value
    val (gone, keep) = st.activeTrackElements.partition { (it.type == "obstacle" || it.type == "bullet") && it.xOffsetFraction <= maxX }
    if (gone.isEmpty()) return 0
    gone.take(5).forEach { ReactFx.obstacle(it.subType, "BOOM", it.xOffsetFraction, it.yMatchPos) }
    _simState.value = st.copy(activeTrackElements = keep, score = st.score + 4 * gone.size)
    return gone.size
}
fun damageBoss(amount: Float) {
    val st = _simState.value
    if (!st.bossActive) return
    val dmg = BossGuard.hit(amount) // shield bounce / armor / rage (BossGuard.kt)
    if (dmg <= 0f) { BossDamageFx.block(); return }
    BossDamageFx.hit(dmg, false)
    LegendMoves.charge(0.04f)
    _simState.value = st.copy(bossHealth = (st.bossHealth - dmg).coerceAtLeast(0.001f))
}
/** Magazine Bay: Bomb Rack upgrade (gems). onDone gets null on success or the reason. */
fun upgradeBombRack(onDone: (String?) -> Unit = {}) {
    viewModelScope.launch { onDone(BroodBomb.upgradeRack(appCtx, gameDao)) }
}
fun buyAmmo(i: Int, onDone: (Boolean) -> Unit = {}) { viewModelScope.launch { onDone(Shooting.buyAmmo(appCtx, gameDao, i)) } }
fun buyBolt(id: String, onDone: (Boolean) -> Unit = {}) { viewModelScope.launch { onDone(Shooting.buyBolt(appCtx, gameDao, id)) } }
/** Pilot Fusion: fuse three cores. */
fun fuseCores(kinds: List<Int>, onDone: (String, Int) -> Unit = { _, _ -> }) {
    viewModelScope.launch { FusionCores.fuse(appCtx, gameDao, kinds)?.let { onDone(it.first, it.second) } }
}

/** Pro daily gems: pays +50 once per day to Pro subscribers (see ProDaily). */
fun claimProDaily(onDone: (Int) -> Unit = {}) {
    if (!(RevenueCatManager.isPro.value || TESTING_DISABLE_PRO_GATE)) return
    viewModelScope.launch { val g = ProDaily.claim(appCtx, gameDao); if (g > 0) onDone(g) }
}

/** TESTING ONLY (shown in Settings while TESTING_DISABLE_PRO_GATE is true): start a run straight at [zone], free. */
fun testJumpToZone(zone: Int) {
    if (!TESTING_DISABLE_PRO_GATE) return
    startRacingSimulation(
        com.neonrush.game.db.GhostChallengeEntity("ghost_cyberrunner", "CyberRunner", 850, 4, ZoneGenerator.generateTelemetryCsv(850, 111)),
        null, zone.coerceAtLeast(1)
    )
}

fun startRacingSimulation(ghost: GhostChallengeEntity, specialWorldId: Int? = null, startFromZone: Int = 1) {
    simJob?.cancel()
    HullFx.reset(); PilotShieldFx.reset(); PickupFx.reset(); ReactFx.reset(); lastPuSpawnTick = 0; fuelFreezeUntilTick = 0; if (selectedDifficulty == DifficultyTier.EASY) PilotShieldFx.onGain(EASY_SHIELD_LAYERS); hangar.onRunStart(profile.value?.activeSkinId ?: "cyan_diamond"); ThreatDirector.reset(); resetStoryProgress(); seasonPass.tierTokenMult = selectedDifficulty.tokenMult; seasonPass.onRunStart()
    var startupDna = ZoneGenerator.generateZone(startFromZone, 42)
    if (specialWorldId != null) startupDna = overrideEnvironment(startupDna, specialWorldId)
    val todayMutation = DailyMutations.getActiveMutation()
    val isFirstLucky = (System.currentTimeMillis() - lastPlayTime) > 3 * 24 * 3600 * 1000L
    val startingDistance = if (startFromZone > 1) {
        val m = (startFromZone - 1).toDouble()
        (((202.5 * m + 776.25).let { it * it } - 602564.0625) / 405.0 * ZONE_LENGTH_MULT).toFloat()
    } else 0f
    _simState.value = SimulationState(
        activeGhost = ghost,
        isStarted = true,
        runGoalIds = RunGoals.idsCsv(RunGoals.forRun(activeTotalRuns)),
        isCompleted = false,
        tickIndex = 0,
        distanceMeters = startingDistance,
        ghostYPath = ZoneGenerator.parseTelemetry(ghost.yPositionsCsv),
        userYPath = parseTelemetryFromSimParameters(),
        feedbackMessage = if (ghost.challengeId == "daily_hard_ghost") "STORM WINDS + LOW FUEL: grab fuel cells!" else if (isFirstLucky) "LUCKY DRIFT ENGAGED (SLOWER SPEED, MORE POWERUPS)!" else "Synchronizing procedural light grid pathways...",
        fuelLevelPercent = if (ghost.challengeId == "daily_hard_ghost") 35 else 100,
        currentZoneNumber = startFromZone,
        zoneDNA = startupDna,
        activeTrackElements = emptyList(),
        // EASY (kids): every run starts wrapped in a layered shield that soaks up the first hits
        shieldLayers = if (selectedDifficulty == DifficultyTier.EASY) EASY_SHIELD_LAYERS else 0,
        activePowerupDurations = emptyMap(),
        currentMutationName = todayMutation.title,
        specialWorldId = specialWorldId
    )
    
        soundEngine.setHomeScreenActiveState(false)
        soundEngine.playThrusterCharge()
        AnalyticsManager.logGameStart()
        simJob = viewModelScope.launch {
            val milestonesTriggered = mutableSetOf<Int>()
            val prof = gameDao.getProfileDirect() ?: GameProfile()
            _simState.value = _simState.value.copy(gemsAtRunStart = prof.gems)
            beginHintsForNewRun(prof.hintsCsv)
            firstRunAssist = prof.totalRuns == 0
            grazedIds.clear()
            nearMiss.reset()
            fuelGate.touchAsync(RevenueCatManager.isPro.value)
            assistGemsGiven = 0
            RiskGates.reset(); BossReturns.reset(); AirSupport.newRun(); BossGuard.reset(); BossGuard.configure(selectedDifficulty != DifficultyTier.EASY, selectedDifficulty == DifficultyTier.LEGENDARY); LegendMoves.reset(); PilotDrift.reset()
            Shooting.newRun(RevenueCatManager.isPro.value || TESTING_DISABLE_PRO_GATE, selectedDifficulty == DifficultyTier.LEGENDARY); MagazineBay.onRunStart(RevenueCatManager.isPro.value || TESTING_DISABLE_PRO_GATE)
            gameDao.updateProfile { p -> p.copy(currentRunGemsCredited = 0, currentRunBossZonesRewarded = "", currentRunMilestonesRewarded = "") }
            var tick = 0
            mirrorSideVar = 0; mirrorFlipTickVar = -100
            val random = kotlin.random.Random(System.currentTimeMillis())
            // Stable per-run seed for zone DNA (obstacle set, environment,
            // mechanics). Previously each tick called random.nextLong() fresh
            // and fed that into generateZone(), so the "seed" changed every
            // single tick — meaning obstacleSetId/environment could flicker
            // tick-to-tick within the same zone instead of staying stable.
            // Capturing one seed here and reusing it for the whole run fixes
            // that, and is what makes the sector system's no-repeat
            // guarantees actually hold (they rely on a stable seed).
            val runSeed = random.nextLong()
            var runStartTime = System.currentTimeMillis()
            val currentFrustration = frustrationIndex
            // Meta-progression difficulty: each fresh run should feel
            // meaningfully more challenging than the last as the player
            // racks up experience — not just within one run, but run over
            // run. Asymptotic like the in-run curves (speed/spacing/
            // mechanics), so it keeps nudging upward for a very long time
            // without ever hard-capping, and totalRuns=0 gives exactly 1.0x
            // (brand-new players see zero change from this).
            val metaDifficultyMultiplier = 1f + 0.15f * (1f - kotlin.math.exp(-prof.totalRuns / 250f))
            val spacingBias = (if (currentFrustration > 3.0f) 0.85f else if (avgZoneReached > 15f) 1.10f else 1.0f) * metaDifficultyMultiplier
            val isLuckyActive = isFirstLucky
            var liveDifficultyMultiplier = 1.0f
            var ticksSinceLastHit = 0
            while (_simState.value.isStarted && !_simState.value.isCompleted) {
                delay(hintTickDelayMs())
                val state = _simState.value
                val assist = assistStrength(tick)
                val userY = state.userYPos
                val activeMutation = DailyMutations.getActiveMutation()
                val isMondayGems = activeMutation == MutationDay.MONDAY
                val isTuesdaySpeed = activeMutation == MutationDay.TUESDAY
                val isWednesdayPower = activeMutation == MutationDay.WEDNESDAY
                val isThursdayMirror = activeMutation == MutationDay.THURSDAY
                val isFridayGolden = activeMutation == MutationDay.FRIDAY
                val isSaturdayBoss = activeMutation == MutationDay.SATURDAY
                val isSundayLegendary = activeMutation == MutationDay.SUNDAY
                val tierPressure = selectedDifficulty.pressure(state.currentZoneNumber) * DIFFICULTY_EASE
                val aggroPressure = selectedDifficulty.aggression(state.currentZoneNumber) * DIFFICULTY_EASE
                val baseSpeedVal = ZoneGenerator.calculateSpeed(1) * tierPressure
                val prestigeSpeedBoost = prof.transcendenceCount * 0.2f
                var speedInPx = baseSpeedVal + prestigeSpeedBoost
                if (isTuesdaySpeed) {
                    speedInPx *= 1.20f
                }
                val hasSlowTime = state.activePowerupDurations.containsKey("PU3") || state.zoneDNA.mechanicIds.contains(6)
                if (hasSlowTime || isLuckyActive) {
                    speedInPx *= 0.50f
                }
                val hasHyperdrive = state.zoneDNA.mechanicIds.contains(1)
                if (hasHyperdrive) {
                    speedInPx *= 2.0f
                }
                speedInPx *= liveDifficultyMultiplier * metaDifficultyMultiplier
                // Take-off: every run starts just under its full pace (EASY -3%, MEDIUM -2%, HARD -1%) and is at full pace after 10 seconds (~84 ticks)
                val takeOff = (tick / 33f).coerceIn(0f, 1f)   // 33 ticks x 0.12 s = about 4 s to full pace
                when (selectedDifficulty) {
                    DifficultyTier.EASY -> speedInPx *= 0.97f + 0.03f * takeOff
                    DifficultyTier.MEDIUM -> speedInPx *= 0.98f + 0.02f * takeOff
                    DifficultyTier.HARD -> speedInPx *= 0.99f + 0.01f * takeOff
                    else -> {}
                }
                val tickDistanceOffset = speedInPx * 3.6f
                var nextDistance = state.distanceMeters + tickDistanceOffset
                var zoneM = (-776.25 + kotlin.math.sqrt(602564.0625 + 405.0 * (nextDistance / ZONE_LENGTH_MULT))) / 202.5
                var nextZoneNumber = (kotlin.math.floor(zoneM).toInt() + 1).coerceAtLeast(1)

                // Pro-content gate: only the normal endless path (special
                // mode has its own tier/mission gating). Entering a
                // requiresPro world/phase range without Pro starts a
                // 4-second grace period; once it expires the player must
                // subscribe or be redirected back to free-tier zones — they
                // cannot simply keep flying through it.
                var proGateActive = state.proGateActive
                var proGateGraceUntilTick = state.proGateGraceUntilTick
                var proGateTriggered = state.proGateTriggered
                if (state.specialWorldId == null) {
                    val zoneWorld = Worlds.worldForZone(nextZoneNumber)
                    val inProZone = zoneWorld.requiresPro && nextZoneNumber in zoneWorld.startZone..zoneWorld.endZone
                    val isProNow = RevenueCatManager.isPro.value
                    if (inProZone && !isProNow && !TESTING_DISABLE_PRO_GATE) {
                        if (!proGateActive) {
                            proGateActive = true
                            proGateGraceUntilTick = tick + 33
                            proGateTriggered = false
                        } else if (tick >= proGateGraceUntilTick) {
                            proGateTriggered = true
                        }
                        if (proGateTriggered) {
                            nextDistance = state.distanceMeters
                            nextZoneNumber = state.currentZoneNumber
                        }
                    } else {
                        proGateActive = false
                        proGateTriggered = false
                    }
                }
                var activeDna = ZoneGenerator.generateZone(nextZoneNumber, runSeed)
                if (state.specialWorldId != null) activeDna = overrideEnvironment(activeDna, state.specialWorldId)
                var updatedMsg = state.feedbackMessage
                var nmTierThis = -1
                var closeCallThisTick = false
                var shieldHitThisTick = false
                var nmBonusThisTick = 0
                var sectorBannerText = state.sectorBannerText
                var sectorBannerUntilTick = state.sectorBannerUntilTick
                var sectorBonusPending = state.sectorBonusPending
                var sectorBonusExpiresAtTick = state.sectorBonusExpiresAtTick
                var nextSectorNumber = state.currentSectorNumber
                if (nextZoneNumber != state.currentZoneNumber) {
                    soundEngine.playTone(660f, 300, "sawtooth")
                    updatedMsg = "ENTERING: ${activeDna.environmentName} ${activeDna.environmentEmoji}"
                    HullFx.zoneTick = tick; HullFx.zone = nextZoneNumber; seasonPass.onToken(SeasonPass.TOKENS_ZONE)
                    if (state.specialWorldId == null) { checkWorldEndingBeat(state.currentZoneNumber, nextZoneNumber); checkStoryBeats(nextZoneNumber, false) }

                    // Sector transition: every 2 zones is a "sector" with a
                    // guaranteed-different obstacle set/mechanics/environment
                    // (see ZoneGenerator). Announce it clearly with a banner
                    // + sound + small gem bonus, and a brief window to watch
                    // an ad and double that bonus.
                    val computedSectorNumber = (nextZoneNumber - 1) / 2
                    if (computedSectorNumber != state.currentSectorNumber) {
                        nextSectorNumber = computedSectorNumber
                        hangar.onSectorChange(computedSectorNumber + 1, prof.activeSkinId, state.distanceMeters.toInt())
                        soundEngine.playTone(880f, 200, "triangle")
                        sectorBannerText = "SECTOR ${computedSectorNumber + 1}: ${activeDna.environmentName} ${activeDna.environmentEmoji}"
                        sectorBannerUntilTick = tick + 30
                        // Gem bonus only every OTHER sector (every 4 zones,
                        // not every 2) — the banner still announces every
                        // sector for the "always something changing" feel,
                        // but the reward itself is a periodic bonus, not a
                        // constant drip.
                        if (computedSectorNumber % 2 == 1) {
                            val sectorBonusGems = 2
                            gameDao.updateProfile { current -> current.copy(gems = current.gems + sectorBonusGems) }
                            sectorBannerText = "SECTOR ${computedSectorNumber + 1}: ${activeDna.environmentName} ${activeDna.environmentEmoji}  +$sectorBonusGems💎"
                            sectorBonusPending = sectorBonusGems
                            sectorBonusExpiresAtTick = tick + 45
                        }
                    }

                    val isCheckpointZone = nextZoneNumber % checkpointEvery() == 0
                    if (isCheckpointZone) {
                        gameDao.updateProfile { current ->
                            val reached = current.checkpointsReachedCsv.split(",").filter { it.isNotEmpty() }
                            if (nextZoneNumber.toString() !in reached) {
                                updatedMsg = "🏁 CHECKPOINT SAVED: Zone $nextZoneNumber!"
                                current.copy(checkpointsReachedCsv = (reached + nextZoneNumber.toString()).joinToString(","))
                            } else {
                                current
                            }
                        }
                    }

                    val isMilestone25 = nextZoneNumber % 25 == 0
                    val isMilestone10 = nextZoneNumber % 10 == 0
                    if (isMilestone25 || isMilestone10) {
                        var milestoneWasNew = false
                        var milestoneGemsAwarded = 0
                        gameDao.updateProfile { current ->
                            val rewarded = current.currentRunMilestonesRewarded.split(",").filter { it.isNotEmpty() }
                            if (nextZoneNumber.toString() !in rewarded) {
                                milestoneWasNew = true
                                milestoneGemsAwarded = if (isMilestone25) 5 else 2
                                current.copy(
                                    gems = current.gems + milestoneGemsAwarded,
                                    currentRunMilestonesRewarded = (rewarded + nextZoneNumber.toString()).joinToString(",")
                                )
                            } else {
                                current
                            }
                        }
                        if (milestoneWasNew) {
                            updatedMsg = "🏆 MILESTONE! Zone $nextZoneNumber reached (+$milestoneGemsAwarded 💎)"
                            soundEngine.playUnlockSkin()
                        }
                    }
                } else if (state.feedbackMessage.startsWith("ENTERING") && tick - state.lastZoneTransitionTick > 25) {
                    updatedMsg = "SYNCHRONIZED WITH ${activeDna.environmentName}"
                }
                val updatedElements = mutableListOf<VisualTrackElement>()
                val pullActive = state.activePowerupDurations.containsKey("PU2") || activeDna.mechanicIds.contains(4)
                RealmRules.record(tick, userY)
                val gravRes = RealmRules.gravityStep(nextZoneNumber, tick, speedInPx, userY)
                val gravUserY = (userY + gravRes[0] + stormWind(state, tick)).coerceIn(10, 90)
                val stormRes = RealmRules.stormStep(nextZoneNumber, tick, userY, speedInPx, updatedElements)
                EdgeGuard.step(tick, userY, updatedElements)
                if (stormRes[1] == 1) soundEngine.playTone(70f, 260, "sawtooth")
                for (elem in state.activeTrackElements) {
                    val nextX = elem.xOffsetFraction - (speedInPx * 0.018f)
                    if (nextX < -0.1f) continue
                    var actualY = elem.yMatchPos
                    if (pullActive && elem.type != "obstacle" && elem.type != "bullet" && nextX > 0.15f && nextX < 0.65f) {
                        val diffY = userY - actualY
                        actualY += (diffY * 0.25f).toInt()
                    }
                    if (elem.subType == "DRONE" && nextX > 0.05f && nextX < 0.9f) {
                        // Homing: the drone gradually drifts toward the
                        // player's current lane instead of holding a fixed
                        // Y, giving it a genuine sense of pursuit. Gentle
                        // rate (6%/tick) keeps it fair/dodgeable rather than
                        // an inescapable lock-on.
                        val diffY = userY - actualY
                        actualY += (diffY * 0.06f).toInt()
                    }
                    val (fx, fy) = ThreatDirector.advance(elem, nextX, actualY, userY, tick, updatedElements)
                    updatedElements.add(elem.copy(xOffsetFraction = fx, yMatchPos = fy))
                }
                if (tick % (10 / spacingBias).coerceIn(4f, 25f).toInt() == 0 && !activeDna.mechanicIds.contains(19)) {
                    val gridX = 1.2f
                    val routeTargetY = state.ghostYPath.getOrNull(tick % state.ghostYPath.size.coerceAtLeast(1)) ?: 50
                    if (random.nextInt(100) < (11 + (16 * assist).toInt())) {
                        updatedElements.add(VisualTrackElement("gem_${tick}", gridX, routeTargetY + random.nextInt(-8, 8), "gem"))
                    } else if (random.nextInt(100) < (15 + (21 * assist).toInt())) {
                        updatedElements.add(VisualTrackElement("fuel_${tick}", gridX, routeTargetY + random.nextInt(-5, 5), "fuel"))
                    }
                }
                val flavor = flavorFor(state, nextZoneNumber)
                val spacingVal = ((activeDna.obstacleSpacingAndDensity / (12f * spacingBias * liveDifficultyMultiplier * aggroPressure * (flavor?.spacingMul ?: 1f))) * (1f + 0.4f * assist)).times(RealmRules.spacingMul(nextZoneNumber)).coerceAtLeast(4f).toInt()
                if (RealmRules.spawnNow(nextZoneNumber, tick, spacingVal)) {
                    val targetGhostY = state.ghostYPath.getOrNull(tick % state.ghostYPath.size.coerceAtLeast(1)) ?: 50
                    val obstacles = spawnObstacleForSet(activeDna.obstacleSetId, tick, random, targetGhostY, nextZoneNumber)
                    if (RealmRules.realmFor(nextZoneNumber) == Realm.SHATTER) RealmRules.glassPane(tick, userY, updatedElements) else if (RealmRules.realmFor(nextZoneNumber) == Realm.DUNES) RealmRules.glassPane(tick, userY, updatedElements, "SCARAB") else updatedElements.addAll(obstacles)
                    if (random.nextFloat() < activeDna.powerupDensity * PU_BOOST * (flavor?.powerupMul ?: 1f) * selectedDifficulty.powerupMul || tick - lastPuSpawnTick >= PU_PITY_TICKS) {
                        lastPuSpawnTick = tick
                        val puType = chooseProceduralPowerupType(random, isSundayLegendary)
                        updatedElements.add(VisualTrackElement("pu_${tick}", 1.22f, targetGhostY + random.nextInt(-10, 10), "powerup", puType))
                    }
                }
                RealmRules.pulseDrop(nextZoneNumber, tick, userY, updatedElements)
                val hasBossZone = nextZoneNumber % 5 == 0 && BossReturns.allow(nextZoneNumber, tick) && ((tick % 40) >= (if (EliteBosses.forZone(nextZoneNumber) != null || RealmRules.isRivalZone(nextZoneNumber)) 8 else 24) || isSaturdayBoss)
                if (state.specialWorldId == null && hasBossZone && !state.bossActive) checkStoryBeats(nextZoneNumber, true)
                updatedElements.addAll(ThreatDirector.spawn(tick, nextZoneNumber, userY, random, updatedElements, hasBossZone, (flavor?.spacingMul ?: 1f) * aggroPressure, assistStrength(tick), selectedDifficulty.warnDelta))
                var bossHealthState = state.bossHealth
                var bossYState = state.bossY
                if (hasBossZone) {
                    if (!state.bossActive) {
                        soundEngine.playTone(220f, 600, "sawtooth")
                        updatedMsg = "CRITICAL WARNING: ZONE BOSS INCOMING!"
                        HullFx.bossTick = tick
                        bossHealthState = BossReturns.comeBack(state.bossHealth)
                    }
                    val bossIvTicks = (Upgrades.bossBulletInterval(activeUpgradesCsv, 9) / ((flavor?.bossAggro ?: 1f) * aggroPressure) * (if (selectedDifficulty == DifficultyTier.EASY) 2.4f else 1f)).toInt().coerceAtLeast(4).let { BossGuard.iv(it, state.bossHealth) }
                    val eliteNow = EliteBosses.forZone(nextZoneNumber) ?: (if (RealmRules.isRivalZone(nextZoneNumber)) null else EliteBosses.forWorld(worldFamilyFor(state, nextZoneNumber), nextZoneNumber))
                    if (eliteNow == null && !RealmRules.isRivalZone(nextZoneNumber)) {
                        val trackingYBias = userY - bossYState
                        bossYState += bossFollowStep(trackingYBias, 0.22f * (flavor?.bossAggro ?: 1f))
                        ThreatDirector.bossFire(tick, bossIvTicks, bossYState, random, updatedElements)
                    } else if (eliteNow == null) {
                        bossYState = RealmRules.echoY(tick)
                        RealmRules.rivalFire(tick, bossIvTicks, bossYState, updatedElements)
                    } else {
                        bossYState = EliteBosses.moveY(eliteNow, tick, bossYState, userY)
                        EliteBosses.fire(eliteNow, tick, bossIvTicks, bossYState, userY, random, updatedElements)
                    }
                                        bossYState = BossGuard.step(tick, bossHealthState, bossYState)
                    BossGuard.counter(tick, userY, updatedElements)
                    bossHealthState -= 0.028f * Upgrades.bossDrainMultiplier(activeUpgradesCsv) * BossGuard.drain()
                    if (bossHealthState <= 0f) {
                        soundEngine.playBossDown(); BossGuard.reset(); BossReturns.onKill(tick)
                        if (BossGuard.hard) viewModelScope.launch { WorldEchoBoss.onDefeat(appCtx, gameDao, RevenueCatManager.isPro.value || TESTING_DISABLE_PRO_GATE) }
                        var wasNewlyRewarded = false
                        var bossReward = 0
                        gameDao.updateProfile { current ->
                            val rewardedZones = current.currentRunBossZonesRewarded.split(",").filter { it.isNotEmpty() }
                            if (nextZoneNumber.toString() !in rewardedZones) {
                                wasNewlyRewarded = true
                                bossReward = nextZoneNumber * 12
                                current.copy(
                                    gems = current.gems + bossReward,
                                    currentRunBossZonesRewarded = (rewardedZones + nextZoneNumber.toString()).joinToString(",")
                                )
                            } else {
                                current
                            }
                        }
                        if (wasNewlyRewarded) {
                            soundEngine.playTone(990f, 400, "sine")
                            updatedMsg = "BOSS DEFEATED! Prestige Reward Cells gathered!"; seasonPass.onToken(SeasonPass.TOKENS_BOSS); onBossDefeatedForDrops()
                            _simState.value = _simState.value.copy(bossGemsThisRun = _simState.value.bossGemsThisRun + bossReward)
                        }
                        // Special/prestige worlds (6-11) don't have a real
                        // zone-range ending (endZone=999, never naturally
                        // reached), so defeating their boss is what actually
                        // marks the world "completed" — this is what gates
                        // New Game+ worlds 9-11 behind having beaten World 8.
                        if (state.specialWorldId != null) {
                            val specialWorld = (Worlds.SPECIAL_WORLDS + Worlds.NEW_GAME_PLUS)
                                .find { it.environmentIds.contains(state.specialWorldId) }
                            if (specialWorld != null) {
                                var wasNewCompletion = false
                                gameDao.updateProfile { current ->
                                    val completed = current.completedWorldsCsv.split(",").filter { it.isNotEmpty() }.toMutableSet()
                                    if (completed.add(specialWorld.id.toString())) {
                                        wasNewCompletion = true
                                        current.copy(completedWorldsCsv = completed.joinToString(","))
                                    } else {
                                        current
                                    }
                                }
                                if (wasNewCompletion) {
                                    val rewardSkinId = when (specialWorld.id) {
                                        9 -> "echo_drift"
                                        10 -> "relic_core"
                                        11 -> "signal_zero"
                                        else -> null
                                    }
                                    rewardSkinId?.let { unlockPilotSkinFromStory(it) }
                                }
                            }
                        }
                    }
                }
                var fuelLevelState = state.fuelLevelPercent
                var shieldLayersState = state.shieldLayers
                val kidMode = selectedDifficulty == DifficultyTier.EASY
                var gemsGathered = state.collectedGemsCount
                val nextDurationsMap = state.activePowerupDurations.toMutableMap()
                val finalElements = mutableListOf<VisualTrackElement>()
                val activeParticles = state.particles.toMutableList()
                for (key in nextDurationsMap.keys.toList()) {
                    val remaining = nextDurationsMap[key] ?: 0
                    if (remaining <= 1) {
                        nextDurationsMap.remove(key)
                    } else {
                        nextDurationsMap[key] = remaining - 1
                    }
                }
                val hasInvincibility = nextDurationsMap.containsKey("PU7") || nextDurationsMap.containsKey("PU12") || LegendMoves.dashing() || AirSupport.covering()
                val hasGhostMode = nextDurationsMap.containsKey("PU4") || activeDna.mechanicIds.contains(3)
                var hasShield = nextDurationsMap.containsKey("PU1")
                var hitThisTick = false
                var boomClearTriggered = false
                var puPickedThisTick = 0
                for (elem in updatedElements) {
                    if (!isBlinkHazardVisible(elem, tick) || !RealmRules.touchable(elem, mirrorSideVar, nextZoneNumber)) continue
                    val prevX = elem.xOffsetFraction + (speedInPx * 0.018f)
                    val isPickup = elem.type == "gem" || elem.type == "fuel" || elem.type == "powerup"
                    // pickups use a wider, swept window: at high speed an item may spend only one tick near the pilot
                    val isAligned = if (isPickup) (prevX >= 0.12f + PilotDrift.dx && elem.xOffsetFraction <= 0.30f + PilotDrift.dx) else (prevX >= 0.16f + PilotDrift.dx && elem.xOffsetFraction <= 0.26f + PilotDrift.dx)
                    if (isAligned) {
                        val verticalDist = Math.abs(userY - elem.yMatchPos)
                        val collisionRadius = (if (state.activePowerupDurations.containsKey("PU9")) 8 else if (kidMode) 11 else 15 - (4 * assist).toInt()).let { if (isPickup) maxOf(it, 22) else it }
                        if (verticalDist < collisionRadius) {
                            when (elem.type) {
                                "gem" -> {
                                    soundEngine.playGemCollect()
                                    ReactFx.collect("gem", "", elem.xOffsetFraction, elem.yMatchPos)
                                    gemsGathered += 1
                                    HullFx.gemTick = tick; hangar.onGem(prof.activeSkinId); seasonPass.onToken(SeasonPass.TOKENS_GEM)
                                      repeat(6) { i ->
                                        activeParticles.add(
                                            Particle(
                                                id = "sp_${tick}_$i",
                                                x = elem.xOffsetFraction,
                                                y = elem.yMatchPos.toFloat(),
                                                vx = random.nextFloat() * 0.02f - 0.01f,
                                                vy = random.nextFloat() * 6f - 3f,
                                                maxAge = 12,
                                                colorArgb = 0xFF00E5FFL,
                                                kind = "sparkle"
                                            )
                                        )
                                    }
                                }
                                "fuel" -> {
                                    soundEngine.playTone(523f, 80, "sine")
                                    val fuelBefore = fuelLevelState
                                    fuelLevelState = (fuelLevelState + ReactFx.FUEL_PICKUP_PCT).coerceAtMost(100)
                                    // a full tank must not waste the cell: the overflow pauses fuel use for that many ticks
                                    val fuelSurplus = fuelBefore + ReactFx.FUEL_PICKUP_PCT - fuelLevelState
                                    if (fuelSurplus > 0) fuelFreezeUntilTick = maxOf(fuelFreezeUntilTick, tick) + fuelSurplus
                                    PickupFx.onPickup("FUEL"); ReactFx.collect("fuel", if (fuelSurplus > 0) "TANK FULL · SAVER" else "+${ReactFx.FUEL_PICKUP_PCT}%", elem.xOffsetFraction, elem.yMatchPos)
                                    repeat(6) { i ->
                                        activeParticles.add(
                                            Particle(
                                                id = "sp_${tick}_$i",
                                                x = elem.xOffsetFraction,
                                                y = elem.yMatchPos.toFloat(),
                                                vx = random.nextFloat() * 0.02f - 0.01f,
                                                vy = random.nextFloat() * 6f - 3f,
                                                maxAge = 12,
                                                colorArgb = 0xFF00FF88L,
                                                kind = "sparkle"
                                            )
                                        )
                                    }
                                }
                                "powerup" -> {
                                    soundEngine.playShieldPowerup()
                                    puPickedThisTick++
                                    val puId = elem.subType
                                    PickupFx.onPickup(puId); ReactFx.collect("pu", puId, elem.xOffsetFraction, elem.yMatchPos)
                                    if (puId == "PU8") {
                                        boomClearTriggered = true
                                        updatedMsg = "BOOM CLEAR! PATH AHEAD WIPED!"
                                        repeat(14) { i ->
                                            activeParticles.add(
                                                Particle(
                                                    id = "boom_${tick}_$i",
                                                    x = elem.xOffsetFraction,
                                                    y = elem.yMatchPos.toFloat(),
                                                    vx = random.nextFloat() * 0.05f - 0.025f,
                                                    vy = random.nextFloat() * 14f - 7f,
                                                    maxAge = 18,
                                                    colorArgb = 0xFFFF8800L,
                                                    kind = "explosion"
                                                )
                                            )
                                        }
                                    } else if (puId == "PU10") {
                                        val perfectY = state.ghostYPath.getOrNull(tick % state.ghostYPath.size.coerceAtLeast(1)) ?: 50
                                        _simState.value = _simState.value.copy(userYPos = perfectY)
                                        updatedMsg = "LANE WARP COMPLETE! DRIFT SAFE LINE SECURED!"
                                    } else if (puId == "PU11") {
                                        val mToSkip = 500f - (nextDistance % 500f) + 10f
                                        _simState.value = state.copy(distanceMeters = nextDistance + mToSkip, currentZoneNumber = nextZoneNumber + 1)
                                        updatedMsg = "QUANTUM ZONE LEAP ENGAGED!"
                                    } else if (puId == "PU12") {
                                        for (i in 1..9) {
                                            nextDurationsMap["PU$i"] = 40
                                        }
                                        shieldLayersState = maxOf(shieldLayersState, 2); PilotShieldFx.onGain(shieldLayersState)
                                        updatedMsg = "LEGENDARY PRESTIGE MATRIX SYNC ACTIVE!"
                                    } else if (puId == "PU1") {
                                        // The shield power-up puts (or puts back) the layered shield on the pilot
                                        val puLayers = 2 + (if (Upgrades.level(activeUpgradesCsv, "shield") >= 3) 1 else 0)
                                        shieldLayersState = maxOf(shieldLayersState, puLayers)
                                        PilotShieldFx.onGain(puLayers)
                                        updatedMsg = "SHIELD ONLINE! $puLayers HITS"
                                    } else {
                                        nextDurationsMap[puId] = (80 * Upgrades.powerupDurationMultiplier(activeUpgradesCsv, puId)).toInt()
                                    }
                                    repeat(8) { i ->
                                        activeParticles.add(
                                            Particle(
                                                id = "sp_${tick}_$i",
                                                x = elem.xOffsetFraction,
                                                y = elem.yMatchPos.toFloat(),
                                                vx = random.nextFloat() * 0.02f - 0.01f,
                                                vy = random.nextFloat() * 6f - 3f,
                                                maxAge = 14,
                                                colorArgb = 0xFFFF00FFL,
                                                kind = "sparkle"
                                            )
                                        )
                                    }
                                }
                                "obstacle", "bullet" -> {
    val hasReviveShield = tick < state.shieldUntilTick
    val isUnstoppableHazard = elem.subType == "BLINK_HAZARD"
    if (hasReviveShield) {
                                        ReactFx.obstacle(elem.subType, "SHIELD", elem.xOffsetFraction, elem.yMatchPos)
                                    } else if ((hasInvincibility || hasGhostMode) && !isUnstoppableHazard) {
                                        ReactFx.obstacle(elem.subType, if (hasInvincibility) "SMASH" else "PHASE", elem.xOffsetFraction, elem.yMatchPos)
                                    } else if (hasShield && !isUnstoppableHazard) {
                                        ReactFx.obstacle(elem.subType, "SHIELD", elem.xOffsetFraction, elem.yMatchPos)
                                        soundEngine.playShieldBreak()
                                        nextDurationsMap.remove("PU1")
                                        hasShield = false
                                        updatedMsg = "DRIFT DEFLECTED: SHIELD BARRIER OVERLOADED"
                                    } else if (shieldLayersState > 0 && (kidMode || !isUnstoppableHazard)) {
                                        shieldLayersState--
                                        shieldHitThisTick = true
                                        ReactFx.obstacle(elem.subType, "SHIELD", elem.xOffsetFraction, elem.yMatchPos)
                                        PilotShieldFx.onHit()
                                        soundEngine.playShieldBreak()
                                        updatedMsg = if (shieldLayersState > 0) "SHIELD HELD! $shieldLayersState LEFT" else "SHIELD BROKEN! FLY CAREFULLY"
                                        repeat(8) { i ->
                                            activeParticles.add(Particle(id = "sh_${tick}_${elem.id}_$i", x = 0.2f, y = userY.toFloat(), vx = random.nextFloat() * 0.04f - 0.02f, vy = random.nextFloat() * 8f - 4f, maxAge = 14, colorArgb = 0xFF5CE1FFL, kind = "sparkle"))
                                        }
                                    } else {
                                        soundEngine.playCollision()
                                        ReactFx.obstacle(elem.subType, "HIT", elem.xOffsetFraction, elem.yMatchPos)
                                        if (!TESTING_DISABLE_PRO_GATE) fuelLevelState = (fuelLevelState - (if (kidMode || StarterWallet.rookie(activeTotalRuns)) 12 else 20 - (10 * assist).toInt())).coerceAtLeast(0)
                                        updatedMsg = if (isUnstoppableHazard) "BLINK STRIKE HIT: NO SHIELD CAN STOP IT" else "WARNING: IMPACT DETECTED! HULL INTEGRITY LOST"
                                        hitObstaclesHistory.add(elem.subType)
                                        hitThisTick = true
                                        repeat(10) { i ->
                                            activeParticles.add(
                                                Particle(
                                                    id = "ex_${tick}_$i",
                                                    x = elem.xOffsetFraction,
                                                    y = elem.yMatchPos.toFloat(),
                                                    vx = random.nextFloat() * 0.04f - 0.02f,
                                                    vy = random.nextFloat() * 10f - 5f,
                                                    maxAge = 16,
                                                    colorArgb = 0xFFFF5500L,
                                                    kind = "explosion"
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                            continue
                        }
                        // CLOSE CALL: an obstacle slipped past within ~10 units of the hull.
                        if (elem.type == "obstacle" && elem.subType != "BLINK_HAZARD" &&
                            verticalDist < collisionRadius + 10 && grazedIds.add(elem.id)) {
                            val nmRes = nearMiss.register(tick, verticalDist < collisionRadius + 4)
                            if (nmRes != null) {
                                soundEngine.playNearMissTier(nmRes.tier, nmRes.combo)
                                updatedMsg = nmRes.label
                                nmTierThis = maxOf(nmTierThis, nmRes.tier)
                                if (nmRes.tier == 2) { HullFx.razorTick = tick; locker.addRazor() }
                                nmBonusThisTick += nmRes.points
                            } else {
                                soundEngine.playNearMiss()
                                updatedMsg = "CLOSE CALL!"
                            }
                            closeCallThisTick = true; HullFx.closeCallTick = tick; hangar.onCloseCall(prof.activeSkinId); seasonPass.onToken(SeasonPass.TOKENS_CLOSE_CALL)
                            if (assist > 0f && assistGemsGiven < 8) { // first run only: small gem reward
                                assistGemsGiven++
                                gemsGathered += 1
                            }
                            repeat(5) { i ->
                                activeParticles.add(
                                    Particle(
                                        id = "nm_${tick}_${elem.id}_$i",
                                        x = 0.2f,
                                        y = userY.toFloat(),
                                        vx = random.nextFloat() * 0.03f - 0.015f,
                                        vy = random.nextFloat() * 8f - 4f,
                                        maxAge = 12,
                                        colorArgb = 0xFF00E5FFL,
                                        kind = "sparkle"
                                    )
                                )
                            }
                        }
                    }
                    finalElements.add(elem)
                }
                if (hitThisTick) nearMiss.onHit()
                if (boomClearTriggered) {
                    // Clear obstacles/bullets in the path immediately ahead
                    // of the player — a real "boom clear" effect, not just
                    // a themed name with nothing behind it.
                    finalElements.filter { (it.type == "obstacle" || it.type == "bullet") && it.xOffsetFraction in 0.05f..0.6f }
                        .forEach { ReactFx.obstacle(it.subType, "BOOM", it.xOffsetFraction, it.yMatchPos) }
                    finalElements.removeAll {
                        (it.type == "obstacle" || it.type == "bullet") && it.xOffsetFraction in 0.05f..0.6f
                    }
                }
                val agedParticles = activeParticles.mapNotNull { p ->
                    val newAge = p.age + 1
                    if (newAge >= p.maxAge) null
                    else p.copy(age = newAge, x = p.x + p.vx, y = (p.y + p.vy).coerceIn(0f, 100f))
                }
                if (hitThisTick) {
                    ticksSinceLastHit = 0
                    liveDifficultyMultiplier = (liveDifficultyMultiplier - 0.15f).coerceAtLeast(0.6f)
                } else {
                    ticksSinceLastHit++
                    if (ticksSinceLastHit > 40) {
                        liveDifficultyMultiplier = (liveDifficultyMultiplier + 0.01f).coerceAtMost(1.4f)
                    }
                }
                val targetGhostY = state.ghostYPath.getOrNull(tick % state.ghostYPath.size.coerceAtLeast(1)) ?: 50
                val processedGhostY = if (activeDna.mechanicIds.contains(2)) 100 - targetGhostY else targetGhostY
                val rawError = Math.abs(userY - processedGhostY)
                // Combo streak: any hit resets it to 0; sustained tight
                // tracking (rawError < 8) keeps building it. This is a skill
                // axis independent of gems/powerups — it rewards getting
                // better at the game, not just playing it longer.
                val nextComboStreak = if (hitThisTick) 0 else if (rawError < 8) state.comboStreak + 1 else state.comboStreak
                val nextPeakComboStreak = maxOf(state.peakComboStreak, nextComboStreak)
                var tickScore = when {
                    rawError < 8 -> if (isWednesdayPower) 30 else 18
                    rawError < 18 -> if (isWednesdayPower) 15 else 10
                    else -> 2
                }
                tickScore += gravRes[1] + stormRes[0]
                var multi = 1.0f
                if (isFridayGolden) multi *= 3.0f
                multi += (prof.transcendenceCount * 0.05f)
                // Combo bonus: +0.5% per streak tick, capped at +100% (streak
                // 200, roughly 24 seconds of sustained precision at 120ms/tick).
                multi *= (1f + (nextComboStreak.coerceAtMost(200) * 0.005f))
                multi *= Upgrades.scoreMultiplier(activeUpgradesCsv)
                multi *= RunGoals.multiplier(activeMasteryPoints)
                if (nextDurationsMap.containsKey("PU6")) {
                    multi *= 5.0f
                } else if (nextDurationsMap.containsKey("PU5")) {
                    multi *= 2.0f
                }
                val finalTickPoints = (tickScore * multi).toInt() + nmBonusThisTick + nearMiss.overdriveTickPoints(tick)
                val nextScore = state.score + finalTickPoints
                soundEngine.updateGameTelemetries(speedInPx * 40f, nextScore)
                for (ms in listOf(150, 500, 1500, 4000, 10000)) {
                    if (nextScore >= ms && !milestonesTriggered.contains(ms)) {
                        milestonesTriggered.add(ms)
                        soundEngine.playSpeedMilestone(ms)
                    }
                }
                if ((tick * 10) % (10 + prof.fuelTiersOwned * 2) < 10 &&
                    !((assist > 0.5f && tick % 2 == 1) || (assist > 0f && assist <= 0.5f && tick % 4 == 1)) &&
                    !(StarterWallet.rookie(activeTotalRuns) && tick % 3 == 0)) {
                    if (tick >= fuelFreezeUntilTick && !TESTING_DISABLE_PRO_GATE) fuelLevelState = (fuelLevelState - 1)
                }
                if (fuelLevelState <= 0) {
                    _simState.value = state.copy(isCompleted = true, score = nextScore)
                    break
                }
                var shakeX = 0f
                var shakeY = 0f
                if (activeDna.mechanicIds.contains(9) || (hasBossZone && bossHealthState > 0)) {
                    shakeX = (random.nextFloat() * 8f - 4f)
                    shakeY = (random.nextFloat() * 8f - 4f)
                }
                run {
                    // Near-miss kick: a short, small shake that fades (kept under the big-hit white flash threshold).
                    val nmT = if (nmTierThis >= 0) tick else state.nearMissTick
                    val nmTier = if (nmTierThis >= 0) nmTierThis else state.nearMissTier
                    val nmA = tick - nmT
                    if (NearMissFx.shakeOn && nmTier > 0 && nmA in 0..5 && shakeX == 0f && shakeY == 0f) {
                        val amp = (if (nmTier == 2) 2.6f else 1.6f) * (1f - nmA / 6f)
                        var sx = (random.nextFloat() * 2f - 1f) * amp
                        var sy = (random.nextFloat() * 2f - 1f) * amp * 0.6f
                        val sum = kotlin.math.abs(sx) + kotlin.math.abs(sy)
                        if (sum > 2.9f) { val k = 2.9f / sum; sx *= k; sy *= k }
                        shakeX = sx; shakeY = sy
                    }
                }
                val hintPicked = updatedElements.filter {
                    (it.type == "gem" || it.type == "fuel" || it.type == "powerup") &&
                        finalElements.none { f -> f.id == it.id }
                }
                val nextHint = stepHints(
                    state.hint,
                    HintInput(
                        nowMs = android.os.SystemClock.elapsedRealtime(),
                        tick = tick,
                        userY = userY,
                        fuelPercent = fuelLevelState,
                        bossActive = hasBossZone && bossHealthState > 0f,
                        hitThisTick = hitThisTick,
                        pickedGem = hintPicked.any { it.type == "gem" },
                        pickedFuel = hintPicked.any { it.type == "fuel" },
                        pickedPowerupId = hintPicked.firstOrNull { it.type == "powerup" }?.subType,
                        refuelTapped = hintRefuelTapped,
                        elements = finalElements,
                        shieldLayers = shieldLayersState,
                        shieldHit = shieldHitThisTick,
                        closeCall = closeCallThisTick
                    )
                )
                hintRefuelTapped = false
                tick++
                _simState.value = state.copy(
                    hint = nextHint,
                    tickIndex = tick,
                    mirrorSide = mirrorSideVar,
                    userYPos = gravUserY,
                    mirrorFlipTick = mirrorFlipTickVar,
                    ghostYPos = processedGhostY,
                    score = nextScore,
                    comboStreak = nextComboStreak,
                    peakComboStreak = nextPeakComboStreak,
                    powerupsCollected = state.powerupsCollected + puPickedThisTick,
                    runSeed = runSeed,
                    currentSectorNumber = nextSectorNumber,
                    sectorBannerText = sectorBannerText,
                    sectorBannerUntilTick = sectorBannerUntilTick,
                    sectorBonusPending = sectorBonusPending,
                    sectorBonusExpiresAtTick = sectorBonusExpiresAtTick,
                    proGateActive = proGateActive,
                    proGateGraceUntilTick = proGateGraceUntilTick,
                    proGateTriggered = proGateTriggered,
                    currentZoneName = activeDna.name,
                    speedKmh = (speedInPx * 40).toInt(),
                    distanceMeters = nextDistance,
                    fuelLevelPercent = fuelLevelState,
                    shieldLayers = shieldLayersState,
                    feedbackMessage = updatedMsg,
                    currentZoneNumber = nextZoneNumber,
                    zoneDNA = activeDna,
                    activeTrackElements = finalElements,
                    activePowerupDurations = nextDurationsMap,
                    screenShakeX = shakeX,
                    screenShakeY = shakeY,
                    bossActive = hasBossZone && bossHealthState > 0f,
                    bossHealth = bossHealthState.coerceIn(0f, 1f),
                    bossY = bossYState,
                    collectedGemsCount = gemsGathered,
                    nearMissTier = if (nmTierThis >= 0) nmTierThis else state.nearMissTier,
                    nearMissTick = if (nmTierThis >= 0) tick else state.nearMissTick,
                    nearCombo = nearMiss.comboNow(tick),
                    overdriveUntilTick = nearMiss.overdriveUntil,
                    particles = agedParticles,
                    isTranscendenceUnlocked = nextZoneNumber >= 50 || prof.transcendenceCount > 0
                )
            }
            avgZoneReached = (avgZoneReached * 0.8f) + (_simState.value.currentZoneNumber * 0.2f)
            deathTimes.add(System.currentTimeMillis())
            totalPlayedSeconds += (tick * 0.12f)
            val durationMin = totalPlayedSeconds / 60f
            frustrationIndex = (deathTimes.size / durationMin.coerceAtLeast(1f))
            lastPlayTime = System.currentTimeMillis()
            completeSimulationRun()
        }
    }

   private suspend fun completeSimulationRun() {
        val finalState = _simState.value
        GameAnalytics.runEnd(finalState.currentZoneNumber, finalState.score, finalState.distanceMeters.toInt(), finalState.reviveCount)
        LiveEvents.onRunEnd(finalState.currentZoneNumber, finalState.score, finalState.distanceMeters.toInt())
        _simState.value = finalState.copy(
            isCompleted = true,
            feedbackMessage = "Sync terminal drift halt. Run finalized!",
            completeRunCallCount = finalState.completeRunCallCount + 1
        )

        val todayMutation = DailyMutations.getActiveMutation()
        val isMonday = todayMutation == MutationDay.MONDAY
        val isFriday = todayMutation == MutationDay.FRIDAY
        val valMultiplier = if (isMonday) 2 else 1
        val FridayBonus = if (isFriday) 5 else 0
        val dailyBonusLabel = when {
            isMonday -> "⚡ DOUBLE GEMS DAY bonus active today!"
            isFriday -> "🌅 GOLDEN HOUR bonus active today!"
            else -> ""
        }
        var welcomeLine = ""
        gameDao.updateProfile { prof ->
            val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val today = fmt.format(Date())
            if (prof.lastStreakLoginDate == today) {
                prof
            } else {
                val streak = if (prof.lastStreakLoginDate.isEmpty()) 1 else {
                    val diffDays = Math.round((fmt.parse(today)!!.time - fmt.parse(prof.lastStreakLoginDate)!!.time) / 86400000.0).toInt()
                    if (diffDays == 1) prof.currentStreak + 1 else 1
                }
                val day = WelcomeBonus.dayOf(streak)
                val welcomeGems = WelcomeBonus.gemsForDay(day)
                var updated = prof.copy(currentStreak = streak, lastStreakLoginDate = today, gems = prof.gems + welcomeGems,
                    bonusGems = minOf(prof.bonusGems, prof.gems) + welcomeGems, bonusGemsExpiry = System.currentTimeMillis() + WelcomeBonus.LIFETIME_MS)
                val bonuses = mutableListOf<String>()
                // Every 7th login day: hull shards toward a locked hull.
                if (streak % 7 == 0) {
                    val (afterShards, hullId, unlockedId) = DailyCrate.grantShards(updated, 5)
                    updated = afterShards
                    fun hullName(id: String) = shopSkins.firstOrNull { it.first == id }?.second ?: id
                    when {
                        unlockedId != null -> bonuses += "🔓 ${hullName(unlockedId)} hull unlocked!"
                        hullId != null -> bonuses += "+5 🧩 ${hullName(hullId)} shards"
                        else -> bonuses += "+10 💎 (all hulls owned)"
                    }
                }
                // Every 30th login day: the exclusive Eternal Flame suit (gems if already owned).
                if (streak % 30 == 0) {
                    val suits = updated.unlockedPilotSkinsCsv.split(",").filter { it.isNotEmpty() }
                    if ("eternal_flame" !in suits) {
                        updated = updated.copy(unlockedPilotSkinsCsv = (suits + "eternal_flame").joinToString(","))
                        bonuses += "🔥 ETERNAL FLAME suit unlocked!"
                    } else {
                        updated = updated.copy(gems = updated.gems + 50)
                        bonuses += "+50 💎 (Eternal Flame owned)"
                    }
                }
                welcomeLine = WelcomeBonus.line(day, welcomeGems) + (if (bonuses.isEmpty()) "" else "\n" + bonuses.joinToString("  \u00b7  "))
                updated
            }
        }
        var bonusGems = 0
        if (finalState.activeGhost?.challengeId == "daily_hard_ghost" && finalState.score >= dailyChallengeGoal) {
            bonusGems = 55
        }
        val GEM_ECONOMY_RATE = (1f / 3f)
        val gemsEarnedTotalSoFar = (((finalState.collectedGemsCount + bonusGems + FridayBonus) * valMultiplier) * GEM_ECONOMY_RATE * Upgrades.gemMultiplier(activeUpgradesCsv) * seasonPass.runGemMult * selectedDifficulty.gemMult * Sky.gemMul()).toInt()
        var gemsToCreditNow = 0

        gameDao.updateProfile { prof ->
            gemsToCreditNow = (gemsEarnedTotalSoFar - prof.currentRunGemsCredited).coerceAtLeast(0)
            prof.copy(
                gems = prof.gems + gemsToCreditNow,
                totalGemsEarned = prof.totalGemsEarned + gemsToCreditNow,
                currentRunGemsCredited = gemsEarnedTotalSoFar
            )
        }

        val shownLabel = if (welcomeLine.isEmpty()) dailyBonusLabel else if (dailyBonusLabel.isEmpty()) welcomeLine else welcomeLine + "\n" + dailyBonusLabel
        if (welcomeLine.isNotEmpty()) soundEngine.playUnlockSkin()
        _simState.value = _simState.value.copy(gemsEarnedLastRun = gemsToCreditNow, dailyBonusLabel = shownLabel)
        if (bonusGems == 55) {
            soundEngine.playUnlockSkin()
        }
        soundEngine.playCollision()
    }

    // ---- Support email + Play In-App Review helpers ----
    private suspend fun noteRunForSupport(score: Int, zone: Int, isNewPB: Boolean) {
        try {
            hangarStore.put("support_last_score", score.toString())
            hangarStore.put("support_last_zone", zone.toString())
            hangarStore.put("support_last_diff", selectedDifficulty.name)
            val runs = hangarStore.getLong("review_runs") + 1
            hangarStore.putLong("review_runs", runs)
            val bestZone = hangarStore.getLong("review_best_zone")
            val deeper = zone > bestZone
            if (deeper) hangarStore.putLong("review_best_zone", zone.toLong())
            // Happy moments only: a new personal best, or reaching a new deepest sector (Sector 6+).
            if ((isNewPB && runs >= 5) || (deeper && zone >= 6 && runs >= 3)) hangarStore.put("review_pending", "1")
        } catch (e: Exception) { }
    }

    suspend fun supportSnapshot(): SupportSnapshot {
        val live = _simState.value
        return try {
            val lastScore = hangarStore.getLong("support_last_score").toInt()
            val lastZone = hangarStore.getLong("support_last_zone").toInt()
            val diff = hangarStore.get("support_last_diff", selectedDifficulty.name)
            if (live.score > 0 && !live.isCompleted) SupportSnapshot(live.score, live.currentZoneNumber, selectedDifficulty.name)
            else SupportSnapshot(lastScore, lastZone.coerceAtLeast(1), diff)
        } catch (e: Exception) { SupportSnapshot(live.score, live.currentZoneNumber, selectedDifficulty.name) }
    }

    /** True only on the home screen after a happy milestone, and at most once every 45 days (Google also caps it). */
    suspend fun shouldAskReview(): Boolean = try {
        if (hangarStore.get("review_pending") != "1") false
        else {
            val last = hangarStore.getLong("review_last_ms")
            System.currentTimeMillis() - last >= 45L * 24 * 60 * 60 * 1000
        }
    } catch (e: Exception) { false }

    suspend fun markReviewAsked() {
        try {
            hangarStore.put("review_pending", "0")
            hangarStore.putLong("review_last_ms", System.currentTimeMillis())
        } catch (e: Exception) { }
    }

    fun finalizeRunStats() {
        viewModelScope.launch {
            val finalState = _simState.value
            val wasAssisted = firstRunAssist
            var firstFlight = false
            var isNewPB = false
            var usernameForLeaderboard = ""
            var activeSkinForLeaderboard = ""
            var adsRemovedResult = false
            var gemsThisSession = 0
            var masteryEarned = 0
            var goalsAwardedAfter = finalState.goalsAwardedCsv

            gameDao.updateProfile { prof ->
                isNewPB = finalState.score > prof.bestScore
                usernameForLeaderboard = prof.username
                activeSkinForLeaderboard = prof.activeSkinId
                gemsThisSession = prof.currentRunGemsCredited
                firstFlight = prof.totalRuns == 0
                val newTotalRuns = prof.totalRuns + 1
                val newAverageScore = ((prof.averageScore * prof.totalRuns) + finalState.score) / newTotalRuns
                val bestZoneLifetime = maxOf(prof.bestZoneReached, finalState.currentZoneNumber)
                val bestComboLifetime = maxOf(prof.bestComboStreak, finalState.peakComboStreak)
                // Run goals -> Mastery points (once per goal per run; Pro earns 50% faster)
                val goals = RunGoals.parseIds(finalState.runGoalIds)
                val alreadyAwarded = finalState.goalsAwardedCsv.split(",").filter { it.isNotBlank() }.toSet()
                val newlyDone = goals.filter { it.id !in alreadyAwarded && RunGoals.isComplete(it, finalState) }
                val basePoints = newlyDone.sumOf { it.tier.points }
                masteryEarned = if (RevenueCatManager.isPro.value) (basePoints * 1.5f + 0.5f).toInt() else basePoints
                goalsAwardedAfter = (alreadyAwarded + newlyDone.map { it.id }).joinToString(",")
                val updated = prof.copy(
                    masteryPoints = prof.masteryPoints + masteryEarned,
                    bestScore = if (isNewPB) finalState.score else prof.bestScore,
                    totalRuns = newTotalRuns,
                    averageScore = newAverageScore,
                    bestZoneReached = bestZoneLifetime,
                    bestComboStreak = bestComboLifetime
                )
                // First Flight reward: bonus gems + the Purple Square hull, unlocked and equipped.
                val withReward = if (firstFlight) {
                    val owned = updated.unlockedSkinsCsv.split(",").filter { it.isNotEmpty() }
                    updated.copy(
                        gems = updated.gems + FIRST_FLIGHT_GEMS,
                        totalGemsEarned = updated.totalGemsEarned + FIRST_FLIGHT_GEMS,
                        unlockedSkinsCsv = (if ("purple_square" in owned) owned else owned + "purple_square").joinToString(","),
                        activeSkinId = "purple_square"
                    )
                } else updated
                adsRemovedResult = withReward.adsRemoved
                MissionManager.recordRunResult(
                    withReward,
                    zoneReached = finalState.currentZoneNumber,
                    score = finalState.score,
                    gemsThisRun = gemsThisSession,
                    bestZoneLifetime = bestZoneLifetime
                )
            }

            _simState.value = _simState.value.copy(
                masteryEarnedLastRun = _simState.value.masteryEarnedLastRun + masteryEarned,
                goalsAwardedCsv = goalsAwardedAfter,
                firstFlightReward = firstFlight || _simState.value.firstFlightReward
            )
            if (firstFlight) {
                hangar.noteEquipped("purple_square")
                soundEngine.playUnlockSkin()
                AnalyticsManager.logFirstFlightReward()
            }

            AnalyticsManager.logGameOver(
                score = finalState.score,
                isNewPB = isNewPB,
                zoneReached = finalState.currentZoneNumber
            )
            hangar.onRunEnd(finalState.distanceMeters.toInt(), activeSkinForLeaderboard)
            seasonPass.onRunEnd(finalState.distanceMeters.toInt(), finalState.currentZoneNumber, isNewPB)
            RivalEcho.commit(appCtx, selectedDifficulty.name, finalState.score)   // best run on this difficulty becomes the next Rival Echo
            FusionCores.runEnded(appCtx, finalState.currentZoneNumber)
            BeaconChain.runEnded(appCtx)
            MysteryDoors.runEnded(appCtx)
            run { val rg = RiskGates.take(); if (rg > 0) gameDao.updateProfile { p -> p.copy(gems = p.gems + rg, totalGemsEarned = p.totalGemsEarned + rg) } }
            if (selectedDifficulty == DifficultyTier.LEGENDARY) LegendTrack.record(appCtx, finalState.currentZoneNumber)
            Contracts.runEnded(appCtx, finalState.currentZoneNumber, finalState.score.toInt(), gemsThisSession, finalState.peakComboStreak)
            gameDao.getProfileDirect()?.let { thrusters.evaluate(it) }
            if (!adsRemovedResult) {
                AdMobManager.incrementGameOver()
            }
            if (isNewPB) soundEngine.playPersonalBestBroken()
            noteRunForSupport(finalState.score, finalState.currentZoneNumber, isNewPB)
            // Each tier has its own board and its own personal best. The assisted first run stays off the boards.
            if (!wasAssisted) run {
                val tier = selectedDifficulty
                val key = "best_" + tier.name
                val tierBest = hangarStore.getLong(key)
                if (finalState.score > tierBest) {
                    hangarStore.putLong(key, finalState.score.toLong())
                    val ps = proPerks.snapshot.value
                    FirebaseLeaderboardManager.submitScore(usernameForLeaderboard, finalState.score, activeSkinForLeaderboard,
                        ps.rank.name, ps.isLegend, badges = AirSupport.badgeCsv(appCtx), tierKey = tier.boardKey)
                    Social.publishScore(usernameForLeaderboard, tier.boardKey, finalState.score)
                }
            }
            // friend challenge: did this run beat the friend's score the pilot was chasing?
            if (!wasAssisted) {
                val won = Social.judge(finalState.score, selectedDifficulty.boardKey)
                if (won > 0) {
                    gameDao.updateProfile { prof -> prof.copy(gems = prof.gems + won) }
                    _simState.value = _simState.value.copy(feedbackMessage = "\u2694 Challenge won! +$won gems")
                }
            }
        }
    }
    fun reviveSimulation(isPro: Boolean = false) {
    val currentState = _simState.value
    if (!isPro && currentState.reviveCount >= 3) return
    val currentTick = currentState.tickIndex
    HullFx.reviveTick = currentTick
    _simState.value = currentState.copy(
        isCompleted = false,
        particles = emptyList(),
        fuelLevelPercent = 100,
        feedbackMessage = "Revive code accepted! Launching drone boosters...",
        reviveCount = currentState.reviveCount + 1,
        shieldUntilTick = currentTick + 25, // ~3 seconds of invulnerability at 120ms/tick
        shieldLayers = reviveShieldLayers(selectedDifficulty),
        fuelRefillCount = 0, // fresh life = fresh set of 6 refuels
        hint = HintState(cooldownUntilMs = android.os.SystemClock.elapsedRealtime() + 2000L)
    )
    simJob?.cancel()
    soundEngine.playRevive()
    PilotShieldFx.onGain(reviveShieldLayers(selectedDifficulty))
        simJob = viewModelScope.launch {
            val milestonesTriggered = mutableSetOf<Int>()
            for (ms in listOf(150, 500, 1500, 4000, 10000)) {
                if (currentState.score >= ms) milestonesTriggered.add(ms)
            }
            val random = kotlin.random.Random(System.currentTimeMillis())
            // Reuse the ORIGINAL run's seed (persisted in state) rather than
            // generating a new one — this is a revive, not a fresh run, so
            // the zone DNA sequence must stay continuous, not reshuffle.
            val runSeed = currentState.runSeed
            val prof = gameDao.getProfileDirect() ?: GameProfile()
            // Same meta-difficulty scalar as loop 1 — a revive continues the
            // same run, so its calibration shouldn't reset or diverge.
            val metaDifficultyMultiplier = 1f + 0.15f * (1f - kotlin.math.exp(-prof.totalRuns / 250f))
            var tick = currentTick
            while (_simState.value.isStarted && !_simState.value.isCompleted) {
                delay(hintTickDelayMs())
                val state = _simState.value
                val assist = assistStrength(tick)
                val userY = state.userYPos
                val activeMutation = DailyMutations.getActiveMutation()
                val isMondayGems = activeMutation == MutationDay.MONDAY
                val isTuesdaySpeed = activeMutation == MutationDay.TUESDAY
                val isWednesdayPower = activeMutation == MutationDay.WEDNESDAY
                val isThursdayMirror = activeMutation == MutationDay.THURSDAY
                val isFridayGolden = activeMutation == MutationDay.FRIDAY
                val isSaturdayBoss = activeMutation == MutationDay.SATURDAY
                val isSundayLegendary = activeMutation == MutationDay.SUNDAY
                val tierPressure = selectedDifficulty.pressure(state.currentZoneNumber) * DIFFICULTY_EASE
                val aggroPressure = selectedDifficulty.aggression(state.currentZoneNumber) * DIFFICULTY_EASE
                val baseSpeedVal = ZoneGenerator.calculateSpeed(1) * tierPressure
                val prestigeSpeedBoost = prof.transcendenceCount * 0.2f
                var speedInPx = baseSpeedVal + prestigeSpeedBoost
                if (isTuesdaySpeed) {
                    speedInPx *= 1.20f
                }
                val hasSlowTime = state.activePowerupDurations.containsKey("PU3") || state.zoneDNA.mechanicIds.contains(6)
                if (hasSlowTime) {
                    speedInPx *= 0.50f
                }
                val hasHyperdrive = state.zoneDNA.mechanicIds.contains(1)
                if (hasHyperdrive) {
                    speedInPx *= 2.0f
                }
                speedInPx *= metaDifficultyMultiplier
                val tickDistanceOffset = speedInPx * 3.6f
                var nextDistance = state.distanceMeters + tickDistanceOffset
                var zoneM = (-776.25 + kotlin.math.sqrt(602564.0625 + 405.0 * (nextDistance / ZONE_LENGTH_MULT))) / 202.5
                var nextZoneNumber = (kotlin.math.floor(zoneM).toInt() + 1).coerceAtLeast(1)

                // Same Pro-zone gate as loop 1 — a revive continues the same
                // run, so the gate must carry over rather than reset.
                var proGateActive = state.proGateActive
                var proGateGraceUntilTick = state.proGateGraceUntilTick
                var proGateTriggered = state.proGateTriggered
                if (state.specialWorldId == null) {
                    val zoneWorld = Worlds.worldForZone(nextZoneNumber)
                    val inProZone = zoneWorld.requiresPro && nextZoneNumber in zoneWorld.startZone..zoneWorld.endZone
                    val isProNow = RevenueCatManager.isPro.value
                    if (inProZone && !isProNow && !TESTING_DISABLE_PRO_GATE) {
                        if (!proGateActive) {
                            proGateActive = true
                            proGateGraceUntilTick = tick + 33
                            proGateTriggered = false
                        } else if (tick >= proGateGraceUntilTick) {
                            proGateTriggered = true
                        }
                        if (proGateTriggered) {
                            nextDistance = state.distanceMeters
                            nextZoneNumber = state.currentZoneNumber
                        }
                    } else {
                        proGateActive = false
                        proGateTriggered = false
                    }
                }
                var activeDna = ZoneGenerator.generateZone(nextZoneNumber, runSeed)
                if (state.specialWorldId != null) activeDna = overrideEnvironment(activeDna, state.specialWorldId)
                var updatedMsg = state.feedbackMessage
                var nmTierThis = -1
                var closeCallThisTick = false
                var shieldHitThisTick = false
                var nmBonusThisTick = 0
                var sectorBannerText = state.sectorBannerText
                var sectorBannerUntilTick = state.sectorBannerUntilTick
                var sectorBonusPending = state.sectorBonusPending
                var sectorBonusExpiresAtTick = state.sectorBonusExpiresAtTick
                var nextSectorNumber = state.currentSectorNumber
                if (nextZoneNumber != state.currentZoneNumber) {
                    soundEngine.playTone(660f, 300, "sawtooth")
                    updatedMsg = "ENTERING: ${activeDna.environmentName} ${activeDna.environmentEmoji}"
                    HullFx.zoneTick = tick; HullFx.zone = nextZoneNumber; seasonPass.onToken(SeasonPass.TOKENS_ZONE)
                    if (state.specialWorldId == null) { checkWorldEndingBeat(state.currentZoneNumber, nextZoneNumber); checkStoryBeats(nextZoneNumber, false) }

                    // Sector transition: same as loop 1 — every 2 zones is a
                    // "sector" with a guaranteed-different obstacle
                    // set/mechanics/environment. Announce it with a banner +
                    // sound + small gem bonus, plus a brief ad-double window.
                    val computedSectorNumber = (nextZoneNumber - 1) / 2
                    if (computedSectorNumber != state.currentSectorNumber) {
                        nextSectorNumber = computedSectorNumber
                        hangar.onSectorChange(computedSectorNumber + 1, prof.activeSkinId, state.distanceMeters.toInt())
                        soundEngine.playTone(880f, 200, "triangle")
                        sectorBannerText = "SECTOR ${computedSectorNumber + 1}: ${activeDna.environmentName} ${activeDna.environmentEmoji}"
                        sectorBannerUntilTick = tick + 30
                        // Gem bonus only every OTHER sector (every 4 zones,
                        // not every 2) — the banner still announces every
                        // sector for the "always something changing" feel,
                        // but the reward itself is a periodic bonus, not a
                        // constant drip.
                        if (computedSectorNumber % 2 == 1) {
                            val sectorBonusGems = 2
                            gameDao.updateProfile { current -> current.copy(gems = current.gems + sectorBonusGems) }
                            sectorBannerText = "SECTOR ${computedSectorNumber + 1}: ${activeDna.environmentName} ${activeDna.environmentEmoji}  +$sectorBonusGems💎"
                            sectorBonusPending = sectorBonusGems
                            sectorBonusExpiresAtTick = tick + 45
                        }
                    }

                    val isCheckpointZone = nextZoneNumber % checkpointEvery() == 0
                    if (isCheckpointZone) {
                        gameDao.updateProfile { current ->
                            val reached = current.checkpointsReachedCsv.split(",").filter { it.isNotEmpty() }
                            if (nextZoneNumber.toString() !in reached) {
                                updatedMsg = "🏁 CHECKPOINT SAVED: Zone $nextZoneNumber!"
                                current.copy(checkpointsReachedCsv = (reached + nextZoneNumber.toString()).joinToString(","))
                            } else {
                                current
                            }
                        }
                    }

                    val isMilestone25 = nextZoneNumber % 25 == 0
                    val isMilestone10 = nextZoneNumber % 10 == 0
                    if (isMilestone25 || isMilestone10) {

                        var milestoneWasNew = false
                        var milestoneGemsAwarded = 0
                        gameDao.updateProfile { current ->
                            val rewarded = current.currentRunMilestonesRewarded.split(",").filter { it.isNotEmpty() }
                            if (nextZoneNumber.toString() !in rewarded) {
                                milestoneWasNew = true
                                milestoneGemsAwarded = if (isMilestone25) 5 else 2
                                current.copy(
                                    gems = current.gems + milestoneGemsAwarded,
                                    currentRunMilestonesRewarded = (rewarded + nextZoneNumber.toString()).joinToString(",")
                                )
                            } else {
                                current
                            }
                        }
                        if (milestoneWasNew) {
                            updatedMsg = "🏆 MILESTONE! Zone $nextZoneNumber reached (+$milestoneGemsAwarded 💎)"
                            soundEngine.playUnlockSkin()
                        }
                    }
                } else if (state.feedbackMessage.startsWith("ENTERING") && tick - state.lastZoneTransitionTick > 25) {
                    updatedMsg = "SYNCHRONIZED WITH ${activeDna.environmentName}"
                }
                
                val updatedElements = mutableListOf<VisualTrackElement>()
                val pullActive = state.activePowerupDurations.containsKey("PU2") || activeDna.mechanicIds.contains(4)
                RealmRules.record(tick, userY)
                val gravRes = RealmRules.gravityStep(nextZoneNumber, tick, speedInPx, userY)
                val gravUserY = (userY + gravRes[0] + stormWind(state, tick)).coerceIn(10, 90)
                val stormRes = RealmRules.stormStep(nextZoneNumber, tick, userY, speedInPx, updatedElements)
                EdgeGuard.step(tick, userY, updatedElements)
                if (stormRes[1] == 1) soundEngine.playTone(70f, 260, "sawtooth")
                for (elem in state.activeTrackElements) {
                    val nextX = elem.xOffsetFraction - (speedInPx * 0.018f)
                    if (nextX < -0.1f) continue
                    var actualY = elem.yMatchPos
                    if (pullActive && elem.type != "obstacle" && elem.type != "bullet" && nextX > 0.15f && nextX < 0.65f) {
                        val diffY = userY - actualY
                        actualY += (diffY * 0.25f).toInt()
                    }
                    if (elem.subType == "DRONE" && nextX > 0.05f && nextX < 0.9f) {
                        // Homing: the drone gradually drifts toward the
                        // player's current lane instead of holding a fixed
                        // Y, giving it a genuine sense of pursuit. Gentle
                        // rate (6%/tick) keeps it fair/dodgeable rather than
                        // an inescapable lock-on.
                        val diffY = userY - actualY
                        actualY += (diffY * 0.06f).toInt()
                    }
                    val (fx, fy) = ThreatDirector.advance(elem, nextX, actualY, userY, tick, updatedElements)
                    updatedElements.add(elem.copy(xOffsetFraction = fx, yMatchPos = fy))
                }
                if (tick % 10 == 0 && !activeDna.mechanicIds.contains(19)) {
                    val gridX = 1.2f
                    val routeTargetY = state.ghostYPath.getOrNull(tick % state.ghostYPath.size.coerceAtLeast(1)) ?: 50
                    if (random.nextInt(100) < 11) {
                        updatedElements.add(VisualTrackElement("gem_${tick}", gridX, routeTargetY + random.nextInt(-8, 8), "gem"))
                    } else if (random.nextInt(100) < (10 + (21 * assist).toInt())) {
                        updatedElements.add(VisualTrackElement("fuel_${tick}", gridX, routeTargetY + random.nextInt(-5, 5), "fuel"))
                    }
                }
                val flavor = flavorFor(state, nextZoneNumber)
                val spacingVal = (activeDna.obstacleSpacingAndDensity / (12f * metaDifficultyMultiplier * aggroPressure * (flavor?.spacingMul ?: 1f))).times(RealmRules.spacingMul(nextZoneNumber)).coerceAtLeast(4f).toInt()
                if (RealmRules.spawnNow(nextZoneNumber, tick, spacingVal)) {
                    val targetGhostY = state.ghostYPath.getOrNull(tick % state.ghostYPath.size.coerceAtLeast(1)) ?: 50
                    val obstacles = spawnObstacleForSet(activeDna.obstacleSetId, tick, random, targetGhostY, nextZoneNumber)
                    if (RealmRules.realmFor(nextZoneNumber) == Realm.SHATTER) RealmRules.glassPane(tick, userY, updatedElements) else if (RealmRules.realmFor(nextZoneNumber) == Realm.DUNES) RealmRules.glassPane(tick, userY, updatedElements, "SCARAB") else updatedElements.addAll(obstacles)
                    if (random.nextFloat() < activeDna.powerupDensity * PU_BOOST * (flavor?.powerupMul ?: 1f) * selectedDifficulty.powerupMul || tick - lastPuSpawnTick >= PU_PITY_TICKS) {
                        lastPuSpawnTick = tick
                        val puType = chooseProceduralPowerupType(random, isSundayLegendary)
                        updatedElements.add(VisualTrackElement("pu_${tick}", 1.22f, targetGhostY + random.nextInt(-10, 10), "powerup", puType))
                    }
                }
                RealmRules.pulseDrop(nextZoneNumber, tick, userY, updatedElements)
                val hasBossZone = nextZoneNumber % 5 == 0 && BossReturns.allow(nextZoneNumber, tick) && ((tick % 40) >= (if (EliteBosses.forZone(nextZoneNumber) != null || RealmRules.isRivalZone(nextZoneNumber)) 8 else 24) || isSaturdayBoss)
                if (state.specialWorldId == null && hasBossZone && !state.bossActive) checkStoryBeats(nextZoneNumber, true)
                updatedElements.addAll(ThreatDirector.spawn(tick, nextZoneNumber, userY, random, updatedElements, hasBossZone, (flavor?.spacingMul ?: 1f) * aggroPressure, assistStrength(tick), selectedDifficulty.warnDelta))
                var bossHealthState = state.bossHealth
                var bossYState = state.bossY
                if (hasBossZone) {
                    if (!state.bossActive) {
                        soundEngine.playTone(220f, 600, "sawtooth")
                        updatedMsg = "CRITICAL WARNING: ZONE BOSS INCOMING!"
                        HullFx.bossTick = tick
                        bossHealthState = BossReturns.comeBack(state.bossHealth)
                    }
                    val bossIvTicks = (Upgrades.bossBulletInterval(activeUpgradesCsv, 6) / ((flavor?.bossAggro ?: 1f) * aggroPressure) * (if (selectedDifficulty == DifficultyTier.EASY) 2.4f else 1f)).toInt().coerceAtLeast(4).let { BossGuard.iv(it, state.bossHealth) }
                    val eliteNow = EliteBosses.forZone(nextZoneNumber) ?: (if (RealmRules.isRivalZone(nextZoneNumber)) null else EliteBosses.forWorld(worldFamilyFor(state, nextZoneNumber), nextZoneNumber))
                    if (eliteNow == null && !RealmRules.isRivalZone(nextZoneNumber)) {
                        val trackingYBias = userY - bossYState
                        bossYState += bossFollowStep(trackingYBias, 0.28f * (flavor?.bossAggro ?: 1f))
                        ThreatDirector.bossFire(tick, bossIvTicks, bossYState, random, updatedElements)
                    } else if (eliteNow == null) {
                        bossYState = RealmRules.echoY(tick)
                        RealmRules.rivalFire(tick, bossIvTicks, bossYState, updatedElements)
                    } else {
                        bossYState = EliteBosses.moveY(eliteNow, tick, bossYState, userY)
                        EliteBosses.fire(eliteNow, tick, bossIvTicks, bossYState, userY, random, updatedElements)
                    }
                                        bossYState = BossGuard.step(tick, bossHealthState, bossYState)
                    BossGuard.counter(tick, userY, updatedElements)
                    bossHealthState -= 0.028f * Upgrades.bossDrainMultiplier(activeUpgradesCsv) * BossGuard.drain()
                    if (bossHealthState <= 0f) {
                        soundEngine.playBossDown(); BossGuard.reset(); BossReturns.onKill(tick)
                        if (BossGuard.hard) viewModelScope.launch { WorldEchoBoss.onDefeat(appCtx, gameDao, RevenueCatManager.isPro.value || TESTING_DISABLE_PRO_GATE) }
                        soundEngine.playTone(990f, 400, "sine")
                        updatedMsg = "BOSS DEFEATED!"; seasonPass.onToken(SeasonPass.TOKENS_BOSS); onBossDefeatedForDrops()
                        // Same completion tracking as the main loop: this is
                        // what marks a special/prestige world "beaten" since
                        // its zone range never naturally ends.
                        if (state.specialWorldId != null) {
                            val specialWorld = (Worlds.SPECIAL_WORLDS + Worlds.NEW_GAME_PLUS)
                                .find { it.environmentIds.contains(state.specialWorldId) }
                            if (specialWorld != null) {
                                var wasNewCompletion = false
                                gameDao.updateProfile { current ->
                                    val completed = current.completedWorldsCsv.split(",").filter { it.isNotEmpty() }.toMutableSet()
                                    if (completed.add(specialWorld.id.toString())) {
                                        wasNewCompletion = true
                                        current.copy(completedWorldsCsv = completed.joinToString(","))
                                    } else {
                                        current
                                    }
                                }
                                if (wasNewCompletion) {
                                    val rewardSkinId = when (specialWorld.id) {
                                        9 -> "echo_drift"
                                        10 -> "relic_core"
                                        11 -> "signal_zero"
                                        else -> null
                                    }
                                    rewardSkinId?.let { unlockPilotSkinFromStory(it) }
                                }
                            }
                        }
                    }
                }
                var fuelLevelState = state.fuelLevelPercent
                var shieldLayersState = state.shieldLayers
                val kidMode = selectedDifficulty == DifficultyTier.EASY
                var gemsGathered = state.collectedGemsCount
                val nextDurationsMap = state.activePowerupDurations.toMutableMap()
                val finalElements = mutableListOf<VisualTrackElement>()
                val activeParticles = state.particles.toMutableList()
                for (key in nextDurationsMap.keys.toList()) {
                    val remaining = nextDurationsMap[key] ?: 0
                    if (remaining <= 1) {
                        nextDurationsMap.remove(key)
                    } else {
                        nextDurationsMap[key] = remaining - 1
                    }
                }
                val hasInvincibility = nextDurationsMap.containsKey("PU7") || nextDurationsMap.containsKey("PU12") || LegendMoves.dashing() || AirSupport.covering()
                val hasGhostMode = nextDurationsMap.containsKey("PU4") || activeDna.mechanicIds.contains(3)
                var hasShield = nextDurationsMap.containsKey("PU1")
                var hitThisTick = false
                var boomClearTriggered = false
                var puPickedThisTick = 0
                for (elem in updatedElements) {
                    if (!isBlinkHazardVisible(elem, tick) || !RealmRules.touchable(elem, mirrorSideVar, nextZoneNumber)) continue
                    val prevX = elem.xOffsetFraction + (speedInPx * 0.018f)
                    val isPickup = elem.type == "gem" || elem.type == "fuel" || elem.type == "powerup"
                    // pickups use a wider, swept window: at high speed an item may spend only one tick near the pilot
                    val isAligned = if (isPickup) (prevX >= 0.12f + PilotDrift.dx && elem.xOffsetFraction <= 0.30f + PilotDrift.dx) else (prevX >= 0.16f + PilotDrift.dx && elem.xOffsetFraction <= 0.26f + PilotDrift.dx)
                    if (isAligned) {
                        val verticalDist = Math.abs(userY - elem.yMatchPos)
                        val collisionRadius = (if (state.activePowerupDurations.containsKey("PU9")) 8 else if (kidMode) 11 else 15 - (4 * assist).toInt()).let { if (isPickup) maxOf(it, 22) else it }
                        if (verticalDist < collisionRadius) {
                            when (elem.type) {
                                "gem" -> {
                                    soundEngine.playGemCollect()
                                    ReactFx.collect("gem", "", elem.xOffsetFraction, elem.yMatchPos)
                                    gemsGathered += 1
                                    HullFx.gemTick = tick; hangar.onGem(prof.activeSkinId); seasonPass.onToken(SeasonPass.TOKENS_GEM)
                                    repeat(6) { i ->
                                        activeParticles.add(
                                            Particle(
                                                id = "sp_${tick}_$i",
                                                x = elem.xOffsetFraction,
                                                y = elem.yMatchPos.toFloat(),
                                                vx = random.nextFloat() * 0.02f - 0.01f,
                                                vy = random.nextFloat() * 6f - 3f,
                                                maxAge = 12,
                                                colorArgb = 0xFF00E5FFL,
                                                kind = "sparkle"
                                            )
                                        )
                                    }
                                }
                                "fuel" -> {
                                    soundEngine.playTone(523f, 80, "sine")
                                    val fuelBefore = fuelLevelState
                                    fuelLevelState = (fuelLevelState + ReactFx.FUEL_PICKUP_PCT).coerceAtMost(100)
                                    // a full tank must not waste the cell: the overflow pauses fuel use for that many ticks
                                    val fuelSurplus = fuelBefore + ReactFx.FUEL_PICKUP_PCT - fuelLevelState
                                    if (fuelSurplus > 0) fuelFreezeUntilTick = maxOf(fuelFreezeUntilTick, tick) + fuelSurplus
                                    PickupFx.onPickup("FUEL"); ReactFx.collect("fuel", if (fuelSurplus > 0) "TANK FULL · SAVER" else "+${ReactFx.FUEL_PICKUP_PCT}%", elem.xOffsetFraction, elem.yMatchPos)
                                    repeat(6) { i ->
                                        activeParticles.add(
                                            Particle(
                                                id = "sp_${tick}_$i",
                                                x = elem.xOffsetFraction,
                                                y = elem.yMatchPos.toFloat(),
                                                vx = random.nextFloat() * 0.02f - 0.01f,
                                                vy = random.nextFloat() * 6f - 3f,
                                                maxAge = 12,
                                                colorArgb = 0xFF00FF88L,
                                                kind = "sparkle"
                                            )
                                        )
                                    }
                                }
                                "powerup" -> {
                                    soundEngine.playShieldPowerup()
                                    puPickedThisTick++
                                    val puId = elem.subType
                                    PickupFx.onPickup(puId); ReactFx.collect("pu", puId, elem.xOffsetFraction, elem.yMatchPos)
                                    repeat(8) { i ->
                                        activeParticles.add(
                                            Particle(
                                                id = "sp_${tick}_$i",
                                                x = elem.xOffsetFraction,
                                                y = elem.yMatchPos.toFloat(),
                                                vx = random.nextFloat() * 0.02f - 0.01f,
                                                vy = random.nextFloat() * 6f - 3f,
                                                maxAge = 14,
                                                colorArgb = 0xFFFF00FFL,
                                                kind = "sparkle"
                                            )
                                        )
                                    }
                                    if (puId == "PU8") {
                                        boomClearTriggered = true
                                        updatedMsg = "BOOM CLEAR! PATH AHEAD WIPED!"
                                        repeat(14) { i ->
                                            activeParticles.add(
                                                Particle(
                                                    id = "boom_${tick}_$i",
                                                    x = elem.xOffsetFraction,
                                                    y = elem.yMatchPos.toFloat(),
                                                    vx = random.nextFloat() * 0.05f - 0.025f,
                                                    vy = random.nextFloat() * 14f - 7f,
                                                    maxAge = 18,
                                                    colorArgb = 0xFFFF8800L,
                                                    kind = "explosion"
                                                )
                                            )
                                        }
                                    } else if (puId == "PU10") {
                                        val perfectY = state.ghostYPath.getOrNull(tick % state.ghostYPath.size.coerceAtLeast(1)) ?: 50
                                        _simState.value = _simState.value.copy(userYPos = perfectY)
                                    } else if (puId == "PU11") {
                                        val mToSkip = 500f - (nextDistance % 500f) + 10f
                                        _simState.value = state.copy(distanceMeters = nextDistance + mToSkip, currentZoneNumber = nextZoneNumber + 1)
                                    } else if (puId == "PU12") {
                                        for (i in 1..9) {
                                            nextDurationsMap["PU$i"] = 40
                                        }
                                        shieldLayersState = maxOf(shieldLayersState, 2); PilotShieldFx.onGain(shieldLayersState)
                                    } else if (puId == "PU1") {
                                        // The shield power-up puts (or puts back) the layered shield on the pilot
                                        val puLayers = 2 + (if (Upgrades.level(activeUpgradesCsv, "shield") >= 3) 1 else 0)
                                        shieldLayersState = maxOf(shieldLayersState, puLayers)
                                        PilotShieldFx.onGain(puLayers)
                                        updatedMsg = "SHIELD ONLINE! $puLayers HITS"
                                    } else {
                                        nextDurationsMap[puId] = (80 * Upgrades.powerupDurationMultiplier(activeUpgradesCsv, puId)).toInt()
                                    }
                                }
                                "obstacle", "bullet" -> {
                                    val isUnstoppableHazard = elem.subType == "BLINK_HAZARD"
                                    if ((hasInvincibility || hasGhostMode) && !isUnstoppableHazard) {
                                        ReactFx.obstacle(elem.subType, if (hasInvincibility) "SMASH" else "PHASE", elem.xOffsetFraction, elem.yMatchPos)
                                    } else if (hasShield && !isUnstoppableHazard) {
                                        ReactFx.obstacle(elem.subType, "SHIELD", elem.xOffsetFraction, elem.yMatchPos)
                                        soundEngine.playShieldBreak()
                                        nextDurationsMap.remove("PU1")
                                        hasShield = false
                                        updatedMsg = "SHIELD BROKEN!"
                                    } else if (shieldLayersState > 0 && (kidMode || !isUnstoppableHazard)) {
                                        shieldLayersState--
                                        shieldHitThisTick = true
                                        ReactFx.obstacle(elem.subType, "SHIELD", elem.xOffsetFraction, elem.yMatchPos)
                                        PilotShieldFx.onHit()
                                        soundEngine.playShieldBreak()
                                        updatedMsg = if (shieldLayersState > 0) "SHIELD HELD! $shieldLayersState LEFT" else "SHIELD BROKEN! FLY CAREFULLY"
                                        repeat(8) { i ->
                                            activeParticles.add(Particle(id = "sh_${tick}_${elem.id}_$i", x = 0.2f, y = userY.toFloat(), vx = random.nextFloat() * 0.04f - 0.02f, vy = random.nextFloat() * 8f - 4f, maxAge = 14, colorArgb = 0xFF5CE1FFL, kind = "sparkle"))
                                        }
                                    } else {
                                        soundEngine.playCollision()
                                        ReactFx.obstacle(elem.subType, "HIT", elem.xOffsetFraction, elem.yMatchPos)
                                        if (!TESTING_DISABLE_PRO_GATE) fuelLevelState = (fuelLevelState - (if (kidMode || StarterWallet.rookie(activeTotalRuns)) 12 else 20 - (10 * assist).toInt())).coerceAtLeast(0)
                                        updatedMsg = if (isUnstoppableHazard) "BLINK STRIKE HIT: NO SHIELD CAN STOP IT" else "IMPACT IMPACT!"
                                        hitThisTick = true
                                        repeat(10) { i ->
                                            activeParticles.add(
                                                Particle(
                                                    id = "ex_${tick}_$i",
                                                    x = elem.xOffsetFraction,
                                                    y = elem.yMatchPos.toFloat(),
                                                    vx = random.nextFloat() * 0.04f - 0.02f,
                                                    vy = random.nextFloat() * 10f - 5f,
                                                    maxAge = 16,
                                                    colorArgb = 0xFFFF5500L,
                                                    kind = "explosion"
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                            continue
                        }
                        // CLOSE CALL: an obstacle slipped past within ~10 units of the hull.
                        if (elem.type == "obstacle" && elem.subType != "BLINK_HAZARD" &&
                            verticalDist < collisionRadius + 10 && grazedIds.add(elem.id)) {
                            val nmRes = nearMiss.register(tick, verticalDist < collisionRadius + 4)
                            if (nmRes != null) {
                                soundEngine.playNearMissTier(nmRes.tier, nmRes.combo)
                                updatedMsg = nmRes.label
                                nmTierThis = maxOf(nmTierThis, nmRes.tier)
                                if (nmRes.tier == 2) { HullFx.razorTick = tick; locker.addRazor() }
                                nmBonusThisTick += nmRes.points
                            } else {
                                soundEngine.playNearMiss()
                                updatedMsg = "CLOSE CALL!"
                            }
                            closeCallThisTick = true; HullFx.closeCallTick = tick; hangar.onCloseCall(prof.activeSkinId); seasonPass.onToken(SeasonPass.TOKENS_CLOSE_CALL)
                            if (assist > 0f && assistGemsGiven < 8) { // first run only: small gem reward
                                assistGemsGiven++
                                gemsGathered += 1
                            }
                            repeat(5) { i ->
                                activeParticles.add(
                                    Particle(
                                        id = "nm_${tick}_${elem.id}_$i",
                                        x = 0.2f,
                                        y = userY.toFloat(),
                                        vx = random.nextFloat() * 0.03f - 0.015f,
                                        vy = random.nextFloat() * 8f - 4f,
                                        maxAge = 12,
                                        colorArgb = 0xFF00E5FFL,
                                        kind = "sparkle"
                                    )
                                )
                            }
                        }
                    }
                    finalElements.add(elem)
                }
                if (hitThisTick) nearMiss.onHit()
                if (boomClearTriggered) {
                    // Clear obstacles/bullets in the path immediately ahead
                    // of the player — a real "boom clear" effect, not just
                    // a themed name with nothing behind it.
                    finalElements.filter { (it.type == "obstacle" || it.type == "bullet") && it.xOffsetFraction in 0.05f..0.6f }
                        .forEach { ReactFx.obstacle(it.subType, "BOOM", it.xOffsetFraction, it.yMatchPos) }
                    finalElements.removeAll {
                        (it.type == "obstacle" || it.type == "bullet") && it.xOffsetFraction in 0.05f..0.6f
                    }
                }
                val agedParticles = activeParticles.mapNotNull { p ->
                    val newAge = p.age + 1
                    if (newAge >= p.maxAge) null
                    else p.copy(age = newAge, x = p.x + p.vx, y = (p.y + p.vy).coerceIn(0f, 100f))
                }
                val targetGhostY = state.ghostYPath.getOrNull(tick % state.ghostYPath.size.coerceAtLeast(1)) ?: 50
                val processedGhostY = if (activeDna.mechanicIds.contains(2)) 100 - targetGhostY else targetGhostY
                val rawError = Math.abs(userY - processedGhostY)
                val nextComboStreak = if (hitThisTick) 0 else if (rawError < 8) state.comboStreak + 1 else state.comboStreak
                val nextPeakComboStreak = maxOf(state.peakComboStreak, nextComboStreak)
                var tickScore = when {
                    rawError < 8 -> if (isWednesdayPower) 30 else 18
                    rawError < 18 -> if (isWednesdayPower) 15 else 10
                    else -> 2
                }
                tickScore += gravRes[1] + stormRes[0]
                var multi = 1.0f
                if (isFridayGolden) multi *= 3.0f
                multi += (prof.transcendenceCount * 0.05f)
                multi *= (1f + (nextComboStreak.coerceAtMost(200) * 0.005f))
                multi *= Upgrades.scoreMultiplier(activeUpgradesCsv)
                multi *= RunGoals.multiplier(activeMasteryPoints)
                if (nextDurationsMap.containsKey("PU6")) {
                    multi *= 5.0f
                } else if (nextDurationsMap.containsKey("PU5")) {
                    multi *= 2.0f
                }
                val finalTickPoints = (tickScore * multi).toInt() + nmBonusThisTick + nearMiss.overdriveTickPoints(tick)
                val nextScore = state.score + finalTickPoints
                soundEngine.updateGameTelemetries(speedInPx * 40f, nextScore)
                for (ms in listOf(150, 500, 1500, 4000, 10000)) {
                    if (nextScore >= ms && !milestonesTriggered.contains(ms)) {
                        milestonesTriggered.add(ms)
                        soundEngine.playSpeedMilestone(ms)
                        AnalyticsManager.logScoreMilestone(ms)
                    }
                }
                if ((tick * 10) % (10 + prof.fuelTiersOwned * 2) < 10 &&
                    !((assist > 0.5f && tick % 2 == 1) || (assist > 0f && assist <= 0.5f && tick % 4 == 1)) &&
                    !(StarterWallet.rookie(activeTotalRuns) && tick % 3 == 0)) {
                    if (tick >= fuelFreezeUntilTick && !TESTING_DISABLE_PRO_GATE) fuelLevelState = (fuelLevelState - 1)
                }
                if (fuelLevelState <= 0) {
                    _simState.value = state.copy(isCompleted = true, score = nextScore)
                    break
                }
                var shakeX = 0f
                var shakeY = 0f
                if (activeDna.mechanicIds.contains(9) || (hasBossZone && bossHealthState > 0)) {
                    shakeX = (random.nextFloat() * 8f - 4f)
                    shakeY = (random.nextFloat() * 8f - 4f)
                }
                run {
                    // Near-miss kick: a short, small shake that fades (kept under the big-hit white flash threshold).
                    val nmT = if (nmTierThis >= 0) tick else state.nearMissTick
                    val nmTier = if (nmTierThis >= 0) nmTierThis else state.nearMissTier
                    val nmA = tick - nmT
                    if (NearMissFx.shakeOn && nmTier > 0 && nmA in 0..5 && shakeX == 0f && shakeY == 0f) {
                        val amp = (if (nmTier == 2) 2.6f else 1.6f) * (1f - nmA / 6f)
                        var sx = (random.nextFloat() * 2f - 1f) * amp
                        var sy = (random.nextFloat() * 2f - 1f) * amp * 0.6f
                        val sum = kotlin.math.abs(sx) + kotlin.math.abs(sy)
                        if (sum > 2.9f) { val k = 2.9f / sum; sx *= k; sy *= k }
                        shakeX = sx; shakeY = sy
                    }
                }
                val hintPicked = updatedElements.filter {
                    (it.type == "gem" || it.type == "fuel" || it.type == "powerup") &&
                        finalElements.none { f -> f.id == it.id }
                }
                val nextHint = stepHints(
                    state.hint,
                    HintInput(
                        nowMs = android.os.SystemClock.elapsedRealtime(),
                        tick = tick,
                        userY = userY,
                        fuelPercent = fuelLevelState,
                        bossActive = hasBossZone && bossHealthState > 0f,
                        hitThisTick = hitThisTick,
                        pickedGem = hintPicked.any { it.type == "gem" },
                        pickedFuel = hintPicked.any { it.type == "fuel" },
                        pickedPowerupId = hintPicked.firstOrNull { it.type == "powerup" }?.subType,
                        refuelTapped = hintRefuelTapped,
                        elements = finalElements,
                        shieldLayers = shieldLayersState,
                        shieldHit = shieldHitThisTick,
                        closeCall = closeCallThisTick
                    )
                )
                hintRefuelTapped = false
                tick++
                _simState.value = state.copy(
                    hint = nextHint,
                    tickIndex = tick,
                    mirrorSide = mirrorSideVar,
                    userYPos = gravUserY,
                    mirrorFlipTick = mirrorFlipTickVar,
                    particles = agedParticles,
                    ghostYPos = processedGhostY,
                    score = nextScore,
                    comboStreak = nextComboStreak,
                    peakComboStreak = nextPeakComboStreak,
                    powerupsCollected = state.powerupsCollected + puPickedThisTick,
                    runSeed = runSeed,
                    currentSectorNumber = nextSectorNumber,
                    sectorBannerText = sectorBannerText,
                    sectorBannerUntilTick = sectorBannerUntilTick,
                    sectorBonusPending = sectorBonusPending,
                    sectorBonusExpiresAtTick = sectorBonusExpiresAtTick,
                    proGateActive = proGateActive,
                    proGateGraceUntilTick = proGateGraceUntilTick,
                    proGateTriggered = proGateTriggered,
                    currentZoneName = activeDna.name,
                    speedKmh = (speedInPx * 40).toInt(),
                    distanceMeters = nextDistance,
                    fuelLevelPercent = fuelLevelState,
                    shieldLayers = shieldLayersState,
                    feedbackMessage = "REVIVED SYNCHRONIZING...",
                    currentZoneNumber = nextZoneNumber,
                    zoneDNA = activeDna,
                    activeTrackElements = finalElements,
                    activePowerupDurations = nextDurationsMap,
                    screenShakeX = shakeX,
                    screenShakeY = shakeY,
                    bossActive = hasBossZone && bossHealthState > 0f,
                    bossHealth = bossHealthState.coerceIn(0f, 1f),
                    bossY = bossYState,
                    collectedGemsCount = gemsGathered,
                    nearMissTier = if (nmTierThis >= 0) nmTierThis else state.nearMissTier,
                    nearMissTick = if (nmTierThis >= 0) tick else state.nearMissTick,
                    nearCombo = nearMiss.comboNow(tick),
                    overdriveUntilTick = nearMiss.overdriveUntil,
                    isTranscendenceUnlocked = nextZoneNumber >= 50 || prof.transcendenceCount > 0
                )
            }
            completeSimulationRun()
        }
    }

    fun resetSimulation() {
        simJob?.cancel()
        mirrorSideVar = 0; mirrorFlipTickVar = -100
        soundEngine.setHomeScreenActiveState(true)
        _simState.value = SimulationState()
    }

    @Volatile private var mirrorSideVar = 0
    @Volatile private var mirrorFlipTickVar = -100

    /** Mirror realm: tap swaps between the cyan and magenta side. Outside the realm a tap does nothing. */
    fun toggleMirror() {
        val s = _simState.value
        if (s.isCompleted || RealmRules.realmFor(s.currentZoneNumber) != Realm.MIRROR) return
        mirrorSideVar = 1 - mirrorSideVar
        mirrorFlipTickVar = s.tickIndex
        soundEngine.playTone(if (mirrorSideVar == 0) 520f else 780f, 90, "sine")
        _simState.value = s.copy(mirrorSide = mirrorSideVar, mirrorFlipTick = mirrorFlipTickVar)
    }

    fun adjustUserY(delta: Int) {
        val activeMechs = _simState.value.zoneDNA.mechanicIds
        val relativeDelta = if (activeMechs.contains(2)) -delta else delta
        val nextY = (_simState.value.userYPos + relativeDelta).coerceIn(10, 90)
        _simState.value = _simState.value.copy(userYPos = nextY)
    }

    fun setGhostModeTier(mode: Int) {
        viewModelScope.launch {
            val prof = gameDao.getProfileDirect() ?: GameProfile()
            val simulatedScore = when (mode) {
                1 -> prof.bestScore.coerceAtLeast(100)
                2 -> 400
                3 -> 1200
                4 -> 2200
                else -> 5000
            }
            val simulatedTelemetry = ZoneGenerator.generateTelemetryCsv(simulatedScore, mode * 42L)
            _simState.value = _simState.value.copy(
                ghostTierMode = mode,
                ghostYPath = ZoneGenerator.parseTelemetry(simulatedTelemetry)
            )
            soundEngine.playTone(440f, 100, "sine")
        }
    }

    private fun parseTelemetryFromSimParameters(): List<Int> {
        return ZoneGenerator.parseTelemetry(ZoneGenerator.generateTelemetryCsv(200, 77))
    }

    override fun onCleared() {
        super.onCleared()
        simJob?.cancel()
    }
}
