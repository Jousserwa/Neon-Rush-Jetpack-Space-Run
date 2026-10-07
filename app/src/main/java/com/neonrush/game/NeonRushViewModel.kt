package com.neonrush.game
import android.app.Activity
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neonrush.game.db.GameDao
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
const val TESTING_DISABLE_PRO_GATE = true

data class LeaderboardPilot(
    val rank: Int,
    val name: String,
    val bestScore: Int,
    val activeZone: String,
    val activeSkinId: String,
    val isFollowed: Boolean,
    val isBot: Boolean = true,
    val challengeId: String
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
    val fuelRefillCount: Int = 0,
    val specialWorldId: Int? = null,
    // Combo: consecutive tight-precision ticks (rawError < 8), resets to 0 on
    // any obstacle/bullet hit. Rewards sustained skill, not just survival —
    // a second, independent progression axis alongside gems.
    val comboStreak: Int = 0,
    val peakComboStreak: Int = 0,
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
    val hint: HintState = HintState()
)
class NeonRushViewModel(
    private val gameDao: GameDao,
    context: Context
) : ViewModel() {

    private val soundEngine = NeonSoundEngine()

    val profile = gameDao.getProfileFlow()

    // Latest upgrade levels, read by the simulation loops every tick.
    @Volatile private var activeUpgradesCsv: String = ""
    @Volatile private var activeMasteryPoints: Int = 0
    @Volatile private var activeTotalRuns: Int = 0

    val shopSkins = listOf(
        Triple("cyan_diamond", "Cyan Diamond", 0),
        Triple("purple_square", "Purple Square", 150),
        Triple("green_triangle", "Green Triangle", 350),
        Triple("magenta_pulse", "Magenta Pulse Racer", 750),
        Triple("gold_transcendence", "Gold Transcendence Vessel", 1800),
        Triple("matrix_grid", "Hex Grid Cyber-Fighter", 3000)
    )

    val leaderboard: StateFlow<List<LeaderboardPilot>> = FirebaseLeaderboardManager.globalRankings

    private val _simState = MutableStateFlow(SimulationState())
    val simState: StateFlow<SimulationState> = _simState.asStateFlow()

    private val _socialComments = MutableStateFlow<List<SocialComment>>(emptyList())
    val socialComments: StateFlow<List<SocialComment>> = _socialComments.asStateFlow()

    val dailyChallengeTitle = "Methane Glitch Rush"
    val dailyChallengeDesc = "Maximum wind resistance in Zone 3 with critical fuel cells! Finish above 200 points to score bonus gems."
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
            val rewardSkinId = when (world.id) {
                1 -> "blackout_runner"
                2 -> "signal_ghost"
                3 -> "convict_grey"
                4 -> "apex_predator"
                else -> null
            }
            rewardSkinId?.let { unlockPilotSkinFromStory(it) }
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
                soundEngine.playUnlockSkin()
            }
        } else {
            _purchaseErrorEvent.tryEmit("Purchase failed. Please try again.")
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
        val targetDistanceRaw = ((202.5 * zoneMTarget + 776.25).let { it * it } - 602564.0625) / 405.0
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
    fun getSoundEffectsEnabled(): Boolean = NeonSoundEngine.getSoundEffectsEnabled()
    fun setSoundEffectsEnabled(enabled: Boolean) = NeonSoundEngine.setSoundEffectsEnabled(enabled)
    fun getAmbientEnabled(): Boolean = NeonSoundEngine.getAmbientEnabled()
    fun setAmbientEnabled(enabled: Boolean) = NeonSoundEngine.setAmbientEnabled(enabled)
    fun purchaseStarterPack(activity: Activity) {
    RevenueCatManager.purchaseStarterPack(activity) { success ->
        if (success) {
            viewModelScope.launch {
                gameDao.updateProfile { prof -> prof.copy(gems = prof.gems + RevenueCatManager.STARTER_PACK_GEMS_AMOUNT) }
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
    return when (_simState.value.reviveCount) {
        0 -> 20
        1 -> 25
        2 -> 30
        3 -> 40
        4 -> 50
        else -> 50 + 20 * (_simState.value.reviveCount - 4)
    }
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
            reviveSimulation(isPro)
        }
    }
}
fun fuelRefillCostForCurrentRun(): Int {
    return when (_simState.value.fuelRefillCount) {
        0 -> 20
        1 -> 25
        else -> 30
    }
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
        // TESTING ONLY — raised from 6 to 90 for QA. Revert to 6 when told.
        if (!isPro && current.fuelRefillCount >= 90) return@launch
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
            _simState.value = current.copy(
                fuelLevelPercent = 100,
                fuelRefillCount = current.fuelRefillCount + 1
            )
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

    fun checkDailyStreak() {
    viewModelScope.launch {
        var rewardResult: StreakReward? = null
        gameDao.updateProfile { prof ->
            val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val today = fmt.format(Date())
            if (prof.lastStreakLoginDate == today) {
                prof
            } else {
                val newStreak = if (prof.lastStreakLoginDate.isEmpty()) {
                    1
                } else {
                    val lastDate = fmt.parse(prof.lastStreakLoginDate)
                    val todayDate = fmt.parse(today)
                    val diffDays = ((todayDate.time - lastDate.time) / (1000 * 60 * 60 * 24)).toInt()
                    if (diffDays == 1) prof.currentStreak + 1 else 1
                }
                val reward = StreakRewards.rewardForDay(newStreak)
                rewardResult = reward
                prof.copy(
                    currentStreak = newStreak,
                    lastStreakLoginDate = today,
                    gems = prof.gems + reward.gems
                )
            }
        }
        rewardResult?.let { reward ->
            soundEngine.playUnlockSkin()
            _streakRewardEvent.tryEmit(reward)
        }
    }
}

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
    return streakDaysMissed(prof) == 2 && prof.currentStreak > 0
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
        loadSocialComments()
        prepopulateSampleGhostChallenges()
        viewModelScope.launch {
            FirebaseLeaderboardManager.fetchTopScores()
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
            FirebaseLeaderboardManager.fetchTopScores()
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

    fun purchaseSkin(skinId: String, cost: Int) {
        viewModelScope.launch {
            var outcome = "none" // "equipped", "bought", or "none"
            gameDao.updateProfile { prof ->
                val unlockedSkins = prof.unlockedSkinsCsv.split(",").toMutableList()
                if (unlockedSkins.contains(skinId)) {
                    outcome = "equipped"
                    prof.copy(activeSkinId = skinId)
                } else if (prof.gems >= cost) {
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

    enum class DifficultyTier(val speedMultiplier: Float, val spacingMultiplier: Float, val label: String) {
        EASY(0.75f, 1.35f, "EASY"),
        MEDIUM(1.0f, 1.0f, "MEDIUM"),
        HARD(1.2f, 0.8f, "HARD"),
        LEGENDARY(1.4f, 0.65f, "LEGENDARY")
    }

    var selectedDifficulty: DifficultyTier = DifficultyTier.MEDIUM

    fun setDifficulty(tier: DifficultyTier) {
        selectedDifficulty = tier
    }

    var deathTimes: MutableList<Long> = mutableListOf()
    val deathHeatmap: MutableMap<String, Int> = mutableMapOf()
    var hitObstaclesHistory: MutableList<String> = mutableListOf()
    var totalPlayedSeconds: Float = 0f
    var lastPlayTime: Long = System.currentTimeMillis()

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
                elements.add(VisualTrackElement("${baseId}l1", 1.2f, rand.nextInt(20, 80), "obstacle", "LASER"))
                if (rand.nextBoolean()) {
                    elements.add(VisualTrackElement("${baseId}l2", 1.25f, rand.nextInt(20, 80), "obstacle", "LASER"))
                }
            }
            3 -> elements.add(VisualTrackElement("${baseId}b1", 1.3f, ghostY, "obstacle", "BLADE"))
            4 -> elements.add(VisualTrackElement("${baseId}st1", 1.2f, rand.nextInt(15, 45), "obstacle", "STALACTITE"))
            5 -> elements.add(VisualTrackElement("${baseId}sm1", 1.2f, rand.nextInt(55, 85), "obstacle", "STALAGMITE"))
            6 -> {
                elements.add(VisualTrackElement("${baseId}sa1", 1.2f, 20, "obstacle", "STALACTITE"))
                elements.add(VisualTrackElement("${baseId}sa2", 1.2f, 80, "obstacle", "STALAGMITE"))
            }
            7, 11 -> {
                elements.add(VisualTrackElement("${baseId}m1", 1.2f, ghostY - 12, "obstacle", "BARRIER"))
                elements.add(VisualTrackElement("${baseId}m2", 1.2f, ghostY + 12, "obstacle", "BARRIER"))
            }
            15 -> elements.add(VisualTrackElement("${baseId}z1", 1.2f, rand.nextInt(10, 90), "obstacle", "ZAP_FIELD"))
            17 -> elements.add(VisualTrackElement("${baseId}f1", 1.2f, ghostY + rand.nextInt(-10, 10).coerceIn(15, 85), "obstacle", "PHANTOM"))
            18 -> {
                elements.add(VisualTrackElement("${baseId}s1", 1.2f, ghostY - 6, "obstacle", "SPLITTER"))
                elements.add(VisualTrackElement("${baseId}s2", 1.2f, ghostY + 6, "obstacle", "SPLITTER"))
            }
            22 -> {
                elements.add(VisualTrackElement("${baseId}tu1", 1.2f, ghostY - 20, "obstacle", "TUNNEL_TOP"))
                elements.add(VisualTrackElement("${baseId}tu2", 1.2f, ghostY + 20, "obstacle", "TUNNEL_BOTTOM"))
            }
            23 -> {
                val droneY = ghostY + rand.nextInt(-15, 15).coerceIn(15, 85)
                elements.add(VisualTrackElement("${baseId}dr", 1.2f, droneY, "obstacle", "DRONE"))
                // 30% chance the drone also fires a companion projectile —
                // reuses the existing "bullet" collision handling (no new
                // damage logic needed), spawned slightly ahead so dodging
                // the drone's body and dodging its shot are two separate
                // reads, not the same dodge twice.
                if (rand.nextInt(100) < 30) {
                    elements.add(VisualTrackElement("${baseId}drb", 1.35f, droneY + rand.nextInt(-10, 10), "bullet", "DRONE_SHOT"))
                }
            }
            else -> elements.add(VisualTrackElement("${baseId}st", 1.2f, ghostY + rand.nextInt(-12, 12).coerceIn(15, 85), "obstacle", "STANDARD"))
        }
        // BLINK STRIKE: a new, per-world "creature" hazard that flickers
        // visible (~1s) then near-invisible (~2s), like lightning. It cannot
        // be neutralized by shields/invincibility/ghost mode while visible —
        // dodging it is the only way past. Gated to zone 4+ so tutorial zones
        // stay calm. kindIdx (0-4) gives 5 silhouette/color variants per world.
        if (zoneNumber >= 4 && rand.nextInt(100) < 20) {
            val kindIdx = rand.nextInt(0, 5)
            val hazardY = (ghostY + rand.nextInt(-25, 25)).coerceIn(15, 85)
            elements.add(VisualTrackElement("blink_${tick}_${kindIdx}", 1.2f, hazardY, "obstacle", "BLINK_HAZARD"))
        }
        return elements
    }

    fun triggerTranscendence() {
        viewModelScope.launch {
            var didTranscend = false
            gameDao.updateProfile { prof ->
                if (prof.bestScore >= 5000 || prof.transcendenceCount > 0) {
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
                        gems = prof.gems + 500,
                        unlockedSkinsCsv = currentUnlocked.joinToString(","),
                        activeSkinId = exclusiveSkin
                    )
                } else {
                    prof
                }
            }
            if (didTranscend) {
                soundEngine.playTone(880f, 500, "sawtooth")
                loadDefaultLeaderboard()
            }
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
            startRacingSimulation(hardGhost)
            onComplete(true, newAttempts)
        }
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

private fun hintTickDelayMs(): Long =
    if (android.os.SystemClock.elapsedRealtime() < _simState.value.hint.slowMoUntilMs) 240L else 120L

fun startSpecialModeRun(ghost: GhostChallengeEntity) {
    viewModelScope.launch {
        val prof = gameDao.getProfileDirect() ?: GameProfile()
        val world = Worlds.specialWorldForTier(prof.specialWorldTier)
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

fun startRacingSimulation(ghost: GhostChallengeEntity, specialWorldId: Int? = null, startFromZone: Int = 1) {
    simJob?.cancel()
    var startupDna = ZoneGenerator.generateZone(startFromZone, 42)
    if (specialWorldId != null) startupDna = overrideEnvironment(startupDna, specialWorldId)
    val todayMutation = DailyMutations.getActiveMutation()
    val isFirstLucky = (System.currentTimeMillis() - lastPlayTime) > 3 * 24 * 3600 * 1000L
    val startingDistance = if (startFromZone > 1) {
        val m = (startFromZone - 1).toDouble()
        (75.0 * m * m + 575.0 * m).toFloat()
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
        feedbackMessage = if (isFirstLucky) "LUCKY DRIFT ENGAGED (SLOWER SPEED, MORE POWERUPS)!" else "Synchronizing procedural light grid pathways...",
        currentZoneNumber = startFromZone,
        zoneDNA = startupDna,
        activeTrackElements = emptyList(),
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
            gameDao.updateProfile { p -> p.copy(currentRunGemsCredited = 0, currentRunBossZonesRewarded = "", currentRunMilestonesRewarded = "") }
            var tick = 0
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
            val metaDifficultyMultiplier = 1f + 0.4f * (1f - kotlin.math.exp(-prof.totalRuns / 250f))
            val spacingBias = (if (currentFrustration > 3.0f) 0.85f else if (avgZoneReached > 15f) 1.10f else 1.0f) * metaDifficultyMultiplier
            val isLuckyActive = isFirstLucky
            var liveDifficultyMultiplier = 1.0f
            var ticksSinceLastHit = 0
            while (_simState.value.isStarted && !_simState.value.isCompleted) {
                delay(hintTickDelayMs())
                val state = _simState.value
                val userY = state.userYPos
                val activeMutation = DailyMutations.getActiveMutation()
                val isMondayGems = activeMutation == MutationDay.MONDAY
                val isTuesdaySpeed = activeMutation == MutationDay.TUESDAY
                val isWednesdayPower = activeMutation == MutationDay.WEDNESDAY
                val isThursdayMirror = activeMutation == MutationDay.THURSDAY
                val isFridayGolden = activeMutation == MutationDay.FRIDAY
                val isSaturdayBoss = activeMutation == MutationDay.SATURDAY
                val isSundayLegendary = activeMutation == MutationDay.SUNDAY
                val baseSpeedVal = ZoneGenerator.calculateSpeed(state.currentZoneNumber)
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
                speedInPx *= liveDifficultyMultiplier * selectedDifficulty.speedMultiplier * metaDifficultyMultiplier
                val tickDistanceOffset = speedInPx * 3.6f
                var nextDistance = state.distanceMeters + tickDistanceOffset
                var zoneM = (-776.25 + kotlin.math.sqrt(602564.0625 + 405.0 * nextDistance)) / 202.5
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
                var sectorBannerText = state.sectorBannerText
                var sectorBannerUntilTick = state.sectorBannerUntilTick
                var sectorBonusPending = state.sectorBonusPending
                var sectorBonusExpiresAtTick = state.sectorBonusExpiresAtTick
                var nextSectorNumber = state.currentSectorNumber
                if (nextZoneNumber != state.currentZoneNumber) {
                    soundEngine.playTone(660f, 300, "sawtooth")
                    updatedMsg = "ENTERING: ${activeDna.environmentName} ${activeDna.environmentEmoji}"

                    // Sector transition: every 2 zones is a "sector" with a
                    // guaranteed-different obstacle set/mechanics/environment
                    // (see ZoneGenerator). Announce it clearly with a banner
                    // + sound + small gem bonus, and a brief window to watch
                    // an ad and double that bonus.
                    val computedSectorNumber = (nextZoneNumber - 1) / 2
                    if (computedSectorNumber != state.currentSectorNumber) {
                        nextSectorNumber = computedSectorNumber
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

                    val isCheckpointZone = nextZoneNumber % 50 == 0
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
                    updatedElements.add(elem.copy(xOffsetFraction = nextX, yMatchPos = actualY))
                }
                if (tick % (10 / spacingBias).coerceIn(4f, 25f).toInt() == 0 && !activeDna.mechanicIds.contains(19)) {
                    val gridX = 1.2f
                    val routeTargetY = state.ghostYPath.getOrNull(tick % state.ghostYPath.size.coerceAtLeast(1)) ?: 50
                    if (random.nextInt(100) < 9) {
                        updatedElements.add(VisualTrackElement("gem_${tick}", gridX, routeTargetY + random.nextInt(-8, 8), "gem"))
                    } else if (random.nextInt(100) < 15) {
                        updatedElements.add(VisualTrackElement("fuel_${tick}", gridX, routeTargetY + random.nextInt(-5, 5), "fuel"))
                    }
                }
                val spacingVal = (activeDna.obstacleSpacingAndDensity / (12f * spacingBias * liveDifficultyMultiplier * selectedDifficulty.spacingMultiplier)).coerceAtLeast(4f).toInt()
                if (tick % spacingVal == 0) {
                    val targetGhostY = state.ghostYPath.getOrNull(tick % state.ghostYPath.size.coerceAtLeast(1)) ?: 50
                    val obstacles = spawnObstacleForSet(activeDna.obstacleSetId, tick, random, targetGhostY, nextZoneNumber)
                    updatedElements.addAll(obstacles)
                    if (random.nextFloat() < activeDna.powerupDensity) {
                        val puType = chooseProceduralPowerupType(random, isSundayLegendary)
                        updatedElements.add(VisualTrackElement("pu_${tick}", 1.22f, targetGhostY + random.nextInt(-10, 10), "powerup", puType))
                    }
                }
                val hasBossZone = nextZoneNumber % 5 == 0 && ((tick % 40) >= 24 || isSaturdayBoss)
                var bossHealthState = state.bossHealth
                var bossYState = state.bossY
                if (hasBossZone) {
                    if (!state.bossActive) {
                        soundEngine.playTone(220f, 600, "sawtooth")
                        updatedMsg = "CRITICAL WARNING: ZONE BOSS INCOMING!"
                        bossHealthState = 1.0f
                    }
                    val trackingYBias = userY - bossYState
                    bossYState += (trackingYBias * 0.05f).toInt()
                    if (tick % Upgrades.bossBulletInterval(activeUpgradesCsv, 9) == 0) {
                        updatedElements.add(VisualTrackElement("bullet_${tick}", 1.15f, bossYState + random.nextInt(-18, 18), "bullet"))
                    }
                    bossHealthState -= 0.04f * Upgrades.bossDrainMultiplier(activeUpgradesCsv)
                    if (bossHealthState <= 0f) {
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
                            updatedMsg = "BOSS DEFEATED! Prestige Reward Cells gathered!"
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
                val hasInvincibility = nextDurationsMap.containsKey("PU7") || nextDurationsMap.containsKey("PU12")
                val hasGhostMode = nextDurationsMap.containsKey("PU4") || activeDna.mechanicIds.contains(3)
                var hasShield = nextDurationsMap.containsKey("PU1")
                var hitThisTick = false
                var boomClearTriggered = false
                var puPickedThisTick = 0
                for (elem in updatedElements) {
                    if (!isBlinkHazardVisible(elem, tick)) continue
                    val prevX = elem.xOffsetFraction + (speedInPx * 0.018f)
                    val isAligned = prevX >= 0.16f && elem.xOffsetFraction <= 0.26f
                    if (isAligned) {
                        val verticalDist = Math.abs(userY - elem.yMatchPos)
                        val collisionRadius = if (state.activePowerupDurations.containsKey("PU9")) 8 else 15
                        if (verticalDist < collisionRadius) {
                            when (elem.type) {
                                "gem" -> {
                                    soundEngine.playGemCollect()
                                    gemsGathered += 1
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
                                    fuelLevelState = (fuelLevelState + 18).coerceAtMost(100)
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
                                        updatedMsg = "LEGENDARY PRESTIGE MATRIX SYNC ACTIVE!"
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
                                    } else if ((hasInvincibility || hasGhostMode) && !isUnstoppableHazard) {
                                    } else if (hasShield && !isUnstoppableHazard) {
                                        soundEngine.playShieldBreak()
                                        nextDurationsMap.remove("PU1")
                                        hasShield = false
                                        updatedMsg = "DRIFT DEFLECTED: SHIELD BARRIER OVERLOADED"
                                    } else {
                                        soundEngine.playCollision()
                                        fuelLevelState = (fuelLevelState - 20).coerceAtLeast(0)
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
                    }
                    finalElements.add(elem)
                }
                if (boomClearTriggered) {
                    // Clear obstacles/bullets in the path immediately ahead
                    // of the player — a real "boom clear" effect, not just
                    // a themed name with nothing behind it.
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
                val finalTickPoints = (tickScore * multi).toInt()
                val nextScore = state.score + finalTickPoints
                soundEngine.updateGameTelemetries(speedInPx * 40f, nextScore)
                for (ms in listOf(150, 500, 1500, 4000, 10000)) {
                    if (nextScore >= ms && !milestonesTriggered.contains(ms)) {
                        milestonesTriggered.add(ms)
                        soundEngine.playSpeedMilestone(ms)
                    }
                }
                if ((tick * 10) % (10 + prof.fuelTiersOwned * 2) < 10) {
                    fuelLevelState = (fuelLevelState - 1)
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
                        elements = finalElements
                    )
                )
                hintRefuelTapped = false
                tick++
                _simState.value = state.copy(
                    hint = nextHint,
                    tickIndex = tick,
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
        var bonusGems = 0
        if (finalState.activeGhost?.challengeId == "daily_hard_ghost" && finalState.score >= dailyChallengeGoal) {
            bonusGems = 55
        }
        val GEM_ECONOMY_RATE = (1f / 3f)
        val gemsEarnedTotalSoFar = (((finalState.collectedGemsCount + bonusGems + FridayBonus) * valMultiplier) * GEM_ECONOMY_RATE * Upgrades.gemMultiplier(activeUpgradesCsv)).toInt()
        var gemsToCreditNow = 0

        gameDao.updateProfile { prof ->
            gemsToCreditNow = (gemsEarnedTotalSoFar - prof.currentRunGemsCredited).coerceAtLeast(0)
            prof.copy(
                gems = prof.gems + gemsToCreditNow,
                totalGemsEarned = prof.totalGemsEarned + gemsToCreditNow,
                currentRunGemsCredited = gemsEarnedTotalSoFar
            )
        }

        _simState.value = _simState.value.copy(gemsEarnedLastRun = gemsToCreditNow, dailyBonusLabel = dailyBonusLabel)
        if (bonusGems == 55) {
            soundEngine.playUnlockSkin()
        }
        soundEngine.playCollision()
    }

    fun finalizeRunStats() {
        viewModelScope.launch {
            val finalState = _simState.value
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
                adsRemovedResult = updated.adsRemoved
                MissionManager.recordRunResult(
                    updated,
                    zoneReached = finalState.currentZoneNumber,
                    score = finalState.score,
                    gemsThisRun = gemsThisSession,
                    bestZoneLifetime = bestZoneLifetime
                )
            }

            _simState.value = _simState.value.copy(
                masteryEarnedLastRun = _simState.value.masteryEarnedLastRun + masteryEarned,
                goalsAwardedCsv = goalsAwardedAfter
            )

            AnalyticsManager.logGameOver(
                score = finalState.score,
                isNewPB = isNewPB,
                zoneReached = finalState.currentZoneNumber
            )
            if (!adsRemovedResult) {
                AdMobManager.incrementGameOver()
            }
            if (isNewPB) {
                soundEngine.playPersonalBestBroken()
                FirebaseLeaderboardManager.submitScore(usernameForLeaderboard, finalState.score, activeSkinForLeaderboard)
            }
        }
    }
    fun reviveSimulation(isPro: Boolean = false) {
    val currentState = _simState.value
    if (!isPro && currentState.reviveCount >= 3) return
    val currentTick = currentState.tickIndex
    _simState.value = currentState.copy(
        isCompleted = false,
        fuelLevelPercent = 100,
        feedbackMessage = "Revive code accepted! Launching drone boosters...",
        reviveCount = currentState.reviveCount + 1,
        shieldUntilTick = currentTick + 25, // ~3 seconds of invulnerability at 120ms/tick
        fuelRefillCount = 0, // fresh life = fresh set of 6 refuels
        hint = HintState(cooldownUntilMs = android.os.SystemClock.elapsedRealtime() + 2000L)
    )
    simJob?.cancel()
    soundEngine.playRevive()
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
            val metaDifficultyMultiplier = 1f + 0.4f * (1f - kotlin.math.exp(-prof.totalRuns / 250f))
            var tick = currentTick
            while (_simState.value.isStarted && !_simState.value.isCompleted) {
                delay(hintTickDelayMs())
                val state = _simState.value
                val userY = state.userYPos
                val activeMutation = DailyMutations.getActiveMutation()
                val isMondayGems = activeMutation == MutationDay.MONDAY
                val isTuesdaySpeed = activeMutation == MutationDay.TUESDAY
                val isWednesdayPower = activeMutation == MutationDay.WEDNESDAY
                val isThursdayMirror = activeMutation == MutationDay.THURSDAY
                val isFridayGolden = activeMutation == MutationDay.FRIDAY
                val isSaturdayBoss = activeMutation == MutationDay.SATURDAY
                val isSundayLegendary = activeMutation == MutationDay.SUNDAY
                val baseSpeedVal = ZoneGenerator.calculateSpeed(state.currentZoneNumber)
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
                var zoneM = (-776.25 + kotlin.math.sqrt(602564.0625 + 405.0 * nextDistance)) / 202.5
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
                var sectorBannerText = state.sectorBannerText
                var sectorBannerUntilTick = state.sectorBannerUntilTick
                var sectorBonusPending = state.sectorBonusPending
                var sectorBonusExpiresAtTick = state.sectorBonusExpiresAtTick
                var nextSectorNumber = state.currentSectorNumber
                if (nextZoneNumber != state.currentZoneNumber) {
                    soundEngine.playTone(660f, 300, "sawtooth")
                    updatedMsg = "ENTERING: ${activeDna.environmentName} ${activeDna.environmentEmoji}"

                    // Sector transition: same as loop 1 — every 2 zones is a
                    // "sector" with a guaranteed-different obstacle
                    // set/mechanics/environment. Announce it with a banner +
                    // sound + small gem bonus, plus a brief ad-double window.
                    val computedSectorNumber = (nextZoneNumber - 1) / 2
                    if (computedSectorNumber != state.currentSectorNumber) {
                        nextSectorNumber = computedSectorNumber
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

                    val isCheckpointZone = nextZoneNumber % 50 == 0
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
                    updatedElements.add(elem.copy(xOffsetFraction = nextX, yMatchPos = actualY))
                }
                if (tick % 10 == 0 && !activeDna.mechanicIds.contains(19)) {
                    val gridX = 1.2f
                    val routeTargetY = state.ghostYPath.getOrNull(tick % state.ghostYPath.size.coerceAtLeast(1)) ?: 50
                    if (random.nextInt(100) < 40) {
                        updatedElements.add(VisualTrackElement("gem_${tick}", gridX, routeTargetY + random.nextInt(-8, 8), "gem"))
                    } else if (random.nextInt(100) < 15) {
                        updatedElements.add(VisualTrackElement("fuel_${tick}", gridX, routeTargetY + random.nextInt(-5, 5), "fuel"))
                    }
                }
                val spacingVal = (activeDna.obstacleSpacingAndDensity / (12f * metaDifficultyMultiplier)).coerceAtLeast(4f).toInt()
                if (tick % spacingVal == 0) {
                    val targetGhostY = state.ghostYPath.getOrNull(tick % state.ghostYPath.size.coerceAtLeast(1)) ?: 50
                    val obstacles = spawnObstacleForSet(activeDna.obstacleSetId, tick, random, targetGhostY, nextZoneNumber)
                    updatedElements.addAll(obstacles)
                    if (random.nextFloat() < activeDna.powerupDensity) {
                        val puType = chooseProceduralPowerupType(random, isSundayLegendary)
                        updatedElements.add(VisualTrackElement("pu_${tick}", 1.22f, targetGhostY + random.nextInt(-10, 10), "powerup", puType))
                    }
                }
                val hasBossZone = nextZoneNumber % 5 == 0 && ((tick % 40) >= 24 || isSaturdayBoss)
                var bossHealthState = state.bossHealth
                var bossYState = state.bossY
                if (hasBossZone) {
                    if (!state.bossActive) {
                        soundEngine.playTone(220f, 600, "sawtooth")
                        updatedMsg = "CRITICAL WARNING: ZONE BOSS INCOMING!"
                        bossHealthState = 1.0f
                    }
                    val trackingYBias = userY - bossYState
                    bossYState += (trackingYBias * 0.12f).toInt()
                    if (tick % Upgrades.bossBulletInterval(activeUpgradesCsv, 6) == 0) {
                        updatedElements.add(VisualTrackElement("bullet_${tick}", 1.15f, bossYState + random.nextInt(-5, 5), "bullet"))
                    }
                    bossHealthState -= 0.04f * Upgrades.bossDrainMultiplier(activeUpgradesCsv)
                    if (bossHealthState <= 0f) {
                        soundEngine.playTone(990f, 400, "sine")
                        updatedMsg = "BOSS DEFEATED!"
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
                val hasInvincibility = nextDurationsMap.containsKey("PU7") || nextDurationsMap.containsKey("PU12")
                val hasGhostMode = nextDurationsMap.containsKey("PU4") || activeDna.mechanicIds.contains(3)
                var hasShield = nextDurationsMap.containsKey("PU1")
                var hitThisTick = false
                var boomClearTriggered = false
                var puPickedThisTick = 0
                for (elem in updatedElements) {
                    if (!isBlinkHazardVisible(elem, tick)) continue
                    val prevX = elem.xOffsetFraction + (speedInPx * 0.018f)
                    val isAligned = prevX >= 0.16f && elem.xOffsetFraction <= 0.26f
                    if (isAligned) {
                        val verticalDist = Math.abs(userY - elem.yMatchPos)
                        val collisionRadius = if (state.activePowerupDurations.containsKey("PU9")) 8 else 15
                        if (verticalDist < collisionRadius) {
                            when (elem.type) {
                                "gem" -> {
                                    soundEngine.playGemCollect()
                                    gemsGathered += 1
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
                                    fuelLevelState = (fuelLevelState + 18).coerceAtMost(100)
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
                                    } else {
                                        nextDurationsMap[puId] = (80 * Upgrades.powerupDurationMultiplier(activeUpgradesCsv, puId)).toInt()
                                    }
                                }
                                "obstacle", "bullet" -> {
                                    val isUnstoppableHazard = elem.subType == "BLINK_HAZARD"
                                    if ((hasInvincibility || hasGhostMode) && !isUnstoppableHazard) {
                                    } else if (hasShield && !isUnstoppableHazard) {
                                        soundEngine.playShieldBreak()
                                        nextDurationsMap.remove("PU1")
                                        hasShield = false
                                        updatedMsg = "SHIELD BROKEN!"
                                    } else {
                                        soundEngine.playCollision()
                                        fuelLevelState = (fuelLevelState - 20).coerceAtLeast(0)
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
                    }
                    finalElements.add(elem)
                }
                if (boomClearTriggered) {
                    // Clear obstacles/bullets in the path immediately ahead
                    // of the player — a real "boom clear" effect, not just
                    // a themed name with nothing behind it.
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
                val finalTickPoints = (tickScore * multi).toInt()
                val nextScore = state.score + finalTickPoints
                soundEngine.updateGameTelemetries(speedInPx * 40f, nextScore)
                for (ms in listOf(150, 500, 1500, 4000, 10000)) {
                    if (nextScore >= ms && !milestonesTriggered.contains(ms)) {
                        milestonesTriggered.add(ms)
                        soundEngine.playSpeedMilestone(ms)
                        AnalyticsManager.logScoreMilestone(ms)
                    }
                }
                if ((tick * 10) % (10 + prof.fuelTiersOwned * 2) < 10) {
                    fuelLevelState = (fuelLevelState - 1)
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
                        elements = finalElements
                    )
                )
                hintRefuelTapped = false
                tick++
                _simState.value = state.copy(
                    hint = nextHint,
                    tickIndex = tick,
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
                    isTranscendenceUnlocked = nextZoneNumber >= 50 || prof.transcendenceCount > 0
                )
            }
            completeSimulationRun()
        }
    }

    fun resetSimulation() {
        simJob?.cancel()
        soundEngine.setHomeScreenActiveState(true)
        _simState.value = SimulationState()
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
