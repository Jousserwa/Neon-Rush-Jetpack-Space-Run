package com.neonrush.game.db

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
data class GameProfile(
    val id: Int = 1,
    val username: String = "NeonPilot_99",
    val bestScore: Int = 0,
    val gems: Int = 240, // Starter gems
   val currentRunGemsCredited: Int = 0,
    val currentRunBossZonesRewarded: String = "",
    val currentRunMilestonesRewarded: String = "",
    val checkpointsReachedCsv: String = "",
    val checkpointsActivatedCsv: String = "",
    val fuelTiersOwned: Int = 0,
    val transcendenceCount: Int = 0,
    val activeSkinId: String = "cyan_diamond",
    val unlockedSkinsCsv: String = "cyan_diamond",
    val followedUsersCsv: String = "CyberRunner,ZeroGlitch,RetroWave",
    val subscriptionPro: Boolean = false,
    val dailyAttemptsToday: Int = 0,
    val lastDailyRushDate: String = "",
    val activePilotSkinId: String = "default",
    val unlockedPilotSkinsCsv: String = "default",
    // Tracks which worlds have actually been beaten (comma-separated world
    // ids), used to gate New Game+ worlds behind real completion rather than
    // just a Pro purchase.
    val completedWorldsCsv: String = "",
    // Lifetime best combo streak achieved — a skill-progression stat
    // independent of gems/score, the "mastery" track.
    val bestComboStreak: Int = 0,
    // Gates the mandatory first-run tutorial — false until the player has
    // seen it once, then permanently true.
    val hasSeenTutorial: Boolean = false,
    // Epoch millis of the last "check for updates" reminder shown, so it
    // can be throttled to roughly once every 2 weeks.
    val lastUpdateReminderShownAt: Long = 0L,
    val currentStreak: Int = 0,
    val lastStreakLoginDate: String = "",
    val totalRuns: Int = 0,
    val averageScore: Int = 0,
    val totalGemsEarned: Int = 0,
    val adsRemoved: Boolean = false,
    val dailyMissionProgressCsv: String = "",
    val dailyMissionsClaimedCsv: String = "",
    val lastDailyMissionDate: String = "",
    val weeklyMissionProgressCsv: String = "",
    val weeklyMissionsClaimedCsv: String = "",
    val lastWeeklyMissionDate: String = "",
    val monthlyMissionProgressCsv: String = "",
    val monthlyMissionsClaimedCsv: String = "",
    val lastMonthlyMissionDate: String = "",
    val bestZoneReached: Int = 0,
    val dailyRerollCount: Int = 0,
    val weeklyRerollCount: Int = 0,
    val monthlyRerollCount: Int = 0,
    val specialWorldTier: Int = 0,
    // Permanent gem upgrades, e.g. "shield:2,gem:1" (see Upgrades.kt)
    val upgradesCsv: String = "",
    // Daily crate (see DailyCrate.kt)
    val lastCrateDate: String = "",
    val cratesToday: Int = 0,
    val crateStreak: Int = 0,
    val crateShardsCsv: String = ""
)

data class GhostChallengeEntity(
    val challengeId: String,
    val playerName: String,
    val score: Int,
    val zoneReached: Int,
    val yPositionsCsv: String,
    val timestamp: Long = System.currentTimeMillis()
)

class GameDao(context: Context) {
    private val dbHelper = GameDbHelper(context)
    private val _profileFlow = MutableStateFlow<GameProfile?>(null)
    private val profileMutex = Mutex()
    
    init {
        refreshProfile()
    }

    fun getProfileFlow(): StateFlow<GameProfile?> {
        return _profileFlow.asStateFlow()
    }

    private fun refreshProfile() {
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery("SELECT * FROM game_profile WHERE id = 1", null)
        if (cursor.moveToFirst()) {
            val usernameIdx = cursor.getColumnIndex("username")
            val bestScoreIdx = cursor.getColumnIndex("bestScore")
            val gemsIdx = cursor.getColumnIndex("gems")
            val transIdx = cursor.getColumnIndex("transcendenceCount")
            val activeSkinIdx = cursor.getColumnIndex("activeSkinId")
            val unlockedSkinsIdx = cursor.getColumnIndex("unlockedSkinsCsv")
            val followedIdx = cursor.getColumnIndex("followedUsersCsv")
            val subIdx = cursor.getColumnIndex("subscriptionPro")
            val dailyIdx = cursor.getColumnIndex("dailyAttemptsToday")
            val dateIdx = cursor.getColumnIndex("lastDailyRushDate")
            val activePilotSkinIdx = cursor.getColumnIndex("activePilotSkinId")
            val unlockedPilotSkinsIdx = cursor.getColumnIndex("unlockedPilotSkinsCsv")
            val completedWorldsIdx = cursor.getColumnIndex("completedWorldsCsv")
            val bestComboStreakIdx = cursor.getColumnIndex("bestComboStreak")
            val hasSeenTutorialIdx = cursor.getColumnIndex("hasSeenTutorial")
            val lastUpdateReminderShownAtIdx = cursor.getColumnIndex("lastUpdateReminderShownAt")
            val currentStreakIdx = cursor.getColumnIndex("currentStreak")
            val lastStreakLoginIdx = cursor.getColumnIndex("lastStreakLoginDate")
            val totalRunsIdx = cursor.getColumnIndex("totalRuns")
            val averageScoreIdx = cursor.getColumnIndex("averageScore")
            val totalGemsEarnedIdx = cursor.getColumnIndex("totalGemsEarned")
            val adsRemovedIdx = cursor.getColumnIndex("adsRemoved")
            val dailyMissionProgressIdx = cursor.getColumnIndex("dailyMissionProgressCsv")
            val dailyMissionsClaimedIdx = cursor.getColumnIndex("dailyMissionsClaimedCsv")
            val lastDailyMissionDateIdx = cursor.getColumnIndex("lastDailyMissionDate")
            val weeklyMissionProgressIdx = cursor.getColumnIndex("weeklyMissionProgressCsv")
            val weeklyMissionsClaimedIdx = cursor.getColumnIndex("weeklyMissionsClaimedCsv")
            val lastWeeklyMissionDateIdx = cursor.getColumnIndex("lastWeeklyMissionDate")
            val monthlyMissionProgressIdx = cursor.getColumnIndex("monthlyMissionProgressCsv")
            val monthlyMissionsClaimedIdx = cursor.getColumnIndex("monthlyMissionsClaimedCsv")
            val lastMonthlyMissionDateIdx = cursor.getColumnIndex("lastMonthlyMissionDate")
            val bestZoneReachedIdx = cursor.getColumnIndex("bestZoneReached")
            val dailyRerollCountIdx = cursor.getColumnIndex("dailyRerollCount")
            val weeklyRerollCountIdx = cursor.getColumnIndex("weeklyRerollCount")
            val monthlyRerollCountIdx = cursor.getColumnIndex("monthlyRerollCount")
            val specialWorldTierIdx = cursor.getColumnIndex("specialWorldTier")
            val currentRunGemsCreditedIdx = cursor.getColumnIndex("currentRunGemsCredited")
            val currentRunBossZonesRewardedIdx = cursor.getColumnIndex("currentRunBossZonesRewarded")
            val currentRunMilestonesRewardedIdx = cursor.getColumnIndex("currentRunMilestonesRewarded")
            val checkpointsReachedCsvIdx = cursor.getColumnIndex("checkpointsReachedCsv")
            val checkpointsActivatedCsvIdx = cursor.getColumnIndex("checkpointsActivatedCsv")
            val fuelTiersOwnedIdx = cursor.getColumnIndex("fuelTiersOwned")
            val upgradesCsvIdx = cursor.getColumnIndex("upgradesCsv")
            val lastCrateDateIdx = cursor.getColumnIndex("lastCrateDate")
            val cratesTodayIdx = cursor.getColumnIndex("cratesToday")
            val crateStreakIdx = cursor.getColumnIndex("crateStreak")
            val crateShardsCsvIdx = cursor.getColumnIndex("crateShardsCsv")
            val profile = GameProfile(
                id = 1,
                username = if (usernameIdx != -1) cursor.getString(usernameIdx) else "NeonPilot_99",
                bestScore = if (bestScoreIdx != -1) cursor.getInt(bestScoreIdx) else 0,
                gems = if (gemsIdx != -1) cursor.getInt(gemsIdx) else 240,
                transcendenceCount = if (transIdx != -1) cursor.getInt(transIdx) else 0,
                activeSkinId = if (activeSkinIdx != -1) cursor.getString(activeSkinIdx) else "cyan_diamond",
                unlockedSkinsCsv = if (unlockedSkinsIdx != -1) cursor.getString(unlockedSkinsIdx) else "cyan_diamond",
                followedUsersCsv = if (followedIdx != -1) cursor.getString(followedIdx) else "CyberRunner,ZeroGlitch,RetroWave",
                subscriptionPro = if (subIdx != -1) cursor.getInt(subIdx) == 1 else false,
                dailyAttemptsToday = if (dailyIdx != -1) cursor.getInt(dailyIdx) else 0,
                lastDailyRushDate = if (dateIdx != -1) cursor.getString(dateIdx) else "",
                activePilotSkinId = if (activePilotSkinIdx != -1) cursor.getString(activePilotSkinIdx) else "default",
                unlockedPilotSkinsCsv = if (unlockedPilotSkinsIdx != -1) cursor.getString(unlockedPilotSkinsIdx) else "default",
                completedWorldsCsv = if (completedWorldsIdx != -1) cursor.getString(completedWorldsIdx) else "",
                bestComboStreak = if (bestComboStreakIdx != -1) cursor.getInt(bestComboStreakIdx) else 0,
                hasSeenTutorial = if (hasSeenTutorialIdx != -1) cursor.getInt(hasSeenTutorialIdx) != 0 else false,
                lastUpdateReminderShownAt = if (lastUpdateReminderShownAtIdx != -1) cursor.getLong(lastUpdateReminderShownAtIdx) else 0L,
                currentStreak = if (currentStreakIdx != -1) cursor.getInt(currentStreakIdx) else 0,
                lastStreakLoginDate = if (lastStreakLoginIdx != -1) cursor.getString(lastStreakLoginIdx) else "",
                totalRuns = if (totalRunsIdx != -1) cursor.getInt(totalRunsIdx) else 0,
                averageScore = if (averageScoreIdx != -1) cursor.getInt(averageScoreIdx) else 0,
                totalGemsEarned = if (totalGemsEarnedIdx != -1) cursor.getInt(totalGemsEarnedIdx) else 0,
                adsRemoved = if (adsRemovedIdx != -1)cursor.getInt(adsRemovedIdx) == 1 else false,
                dailyMissionProgressCsv = if (dailyMissionProgressIdx != -1) cursor.getString(dailyMissionProgressIdx) else "",
                dailyMissionsClaimedCsv = if (dailyMissionsClaimedIdx != -1) cursor.getString(dailyMissionsClaimedIdx) else "",
                lastDailyMissionDate = if (lastDailyMissionDateIdx != -1) cursor.getString(lastDailyMissionDateIdx) else "",
                weeklyMissionProgressCsv = if (weeklyMissionProgressIdx != -1) cursor.getString(weeklyMissionProgressIdx) else "",
                weeklyMissionsClaimedCsv = if (weeklyMissionsClaimedIdx != -1) cursor.getString(weeklyMissionsClaimedIdx) else "",
                lastWeeklyMissionDate = if (lastWeeklyMissionDateIdx != -1) cursor.getString(lastWeeklyMissionDateIdx) else "",
                monthlyMissionProgressCsv = if (monthlyMissionProgressIdx != -1) cursor.getString(monthlyMissionProgressIdx) else "",
                monthlyMissionsClaimedCsv = if (monthlyMissionsClaimedIdx != -1) cursor.getString(monthlyMissionsClaimedIdx) else "",
               lastMonthlyMissionDate = if (lastMonthlyMissionDateIdx != -1) cursor.getString(lastMonthlyMissionDateIdx) else "",
                bestZoneReached = if (bestZoneReachedIdx != -1) cursor.getInt(bestZoneReachedIdx) else 0,
                dailyRerollCount = if (dailyRerollCountIdx != -1) cursor.getInt(dailyRerollCountIdx) else 0,
                weeklyRerollCount = if (weeklyRerollCountIdx != -1) cursor.getInt(weeklyRerollCountIdx) else 0,
                monthlyRerollCount = if (monthlyRerollCountIdx != -1) cursor.getInt(monthlyRerollCountIdx) else 0,
                specialWorldTier = if (specialWorldTierIdx != -1) cursor.getInt(specialWorldTierIdx) else 0,
                currentRunGemsCredited = if (currentRunGemsCreditedIdx != -1) cursor.getInt(currentRunGemsCreditedIdx) else 0,
                currentRunBossZonesRewarded = if (currentRunBossZonesRewardedIdx != -1) cursor.getString(currentRunBossZonesRewardedIdx) else "",
                currentRunMilestonesRewarded = if (currentRunMilestonesRewardedIdx != -1) cursor.getString(currentRunMilestonesRewardedIdx) else "",
                checkpointsReachedCsv = if (checkpointsReachedCsvIdx != -1) cursor.getString(checkpointsReachedCsvIdx) else "",
                checkpointsActivatedCsv = if (checkpointsActivatedCsvIdx != -1) cursor.getString(checkpointsActivatedCsvIdx) else "",
                fuelTiersOwned = if (fuelTiersOwnedIdx != -1) cursor.getInt(fuelTiersOwnedIdx) else 0,
                upgradesCsv = if (upgradesCsvIdx != -1) (cursor.getString(upgradesCsvIdx) ?: "") else "",
                lastCrateDate = if (lastCrateDateIdx != -1) (cursor.getString(lastCrateDateIdx) ?: "") else "",
                cratesToday = if (cratesTodayIdx != -1) cursor.getInt(cratesTodayIdx) else 0,
                crateStreak = if (crateStreakIdx != -1) cursor.getInt(crateStreakIdx) else 0,
                crateShardsCsv = if (crateShardsCsvIdx != -1) (cursor.getString(crateShardsCsvIdx) ?: "") else ""
            )
            
            _profileFlow.value = profile
        } else {
            // Save initial profile
            val initial = GameProfile()
            saveProfileDirect(initial)
            _profileFlow.value = initial
        }
        cursor.close()
    }

    suspend fun getProfileDirect(): GameProfile? {
        refreshProfile()
        return _profileFlow.value
    }

    suspend fun saveProfile(profile: GameProfile) {
        saveProfileDirect(profile)
    }
    suspend fun updateProfile(block: (GameProfile) -> GameProfile): GameProfile {
        return profileMutex.withLock {
            refreshProfile()
            val current = _profileFlow.value ?: GameProfile()
            val updated = block(current)
            saveProfileDirect(updated)
            updated
        }
    }

    private fun saveProfileDirect(profile: GameProfile) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("id", 1)
            put("username", profile.username)
            put("bestScore", profile.bestScore)
            put("gems", profile.gems)
            put("transcendenceCount", profile.transcendenceCount)
            put("activeSkinId", profile.activeSkinId)
            put("unlockedSkinsCsv", profile.unlockedSkinsCsv)
            put("followedUsersCsv", profile.followedUsersCsv)
            put("subscriptionPro", if (profile.subscriptionPro) 1 else 0)
            put("dailyAttemptsToday", profile.dailyAttemptsToday)
            put("lastDailyRushDate", profile.lastDailyRushDate)
            put("activePilotSkinId", profile.activePilotSkinId)
            put("unlockedPilotSkinsCsv", profile.unlockedPilotSkinsCsv)
            put("completedWorldsCsv", profile.completedWorldsCsv)
            put("bestComboStreak", profile.bestComboStreak)
            put("hasSeenTutorial", if (profile.hasSeenTutorial) 1 else 0)
            put("lastUpdateReminderShownAt", profile.lastUpdateReminderShownAt)
            put("currentStreak", profile.currentStreak)
            put("lastStreakLoginDate", profile.lastStreakLoginDate)
            put("totalRuns", profile.totalRuns)
            put("averageScore", profile.averageScore)
            put("totalGemsEarned", profile.totalGemsEarned)
            put("adsRemoved", if (profile.adsRemoved) 1 else 0)
            put("dailyMissionProgressCsv", profile.dailyMissionProgressCsv)
            put("dailyMissionsClaimedCsv", profile.dailyMissionsClaimedCsv)
            put("lastDailyMissionDate", profile.lastDailyMissionDate)
            put("weeklyMissionProgressCsv", profile.weeklyMissionProgressCsv)
            put("weeklyMissionsClaimedCsv", profile.weeklyMissionsClaimedCsv)
            put("lastWeeklyMissionDate", profile.lastWeeklyMissionDate)
            put("monthlyMissionProgressCsv", profile.monthlyMissionProgressCsv)
            put("monthlyMissionsClaimedCsv", profile.monthlyMissionsClaimedCsv)
            put("lastMonthlyMissionDate", profile.lastMonthlyMissionDate)
            put("bestZoneReached", profile.bestZoneReached)
            put("dailyRerollCount", profile.dailyRerollCount)
            put("weeklyRerollCount", profile.weeklyRerollCount)
            put("monthlyRerollCount", profile.monthlyRerollCount)
            put("specialWorldTier", profile.specialWorldTier)
            put("currentRunGemsCredited", profile.currentRunGemsCredited)
            put("currentRunBossZonesRewarded", profile.currentRunBossZonesRewarded)
            put("currentRunMilestonesRewarded", profile.currentRunMilestonesRewarded)
            put("checkpointsReachedCsv", profile.checkpointsReachedCsv)
            put("checkpointsActivatedCsv", profile.checkpointsActivatedCsv)
            put("fuelTiersOwned", profile.fuelTiersOwned)
            put("upgradesCsv", profile.upgradesCsv)
            put("lastCrateDate", profile.lastCrateDate)
            put("cratesToday", profile.cratesToday)
            put("crateStreak", profile.crateStreak)
            put("crateShardsCsv", profile.crateShardsCsv)
        }
        db.insertWithOnConflict("game_profile", null, values, SQLiteDatabase.CONFLICT_REPLACE)
        _profileFlow.value = profile
    }

    suspend fun insertGhost(ghost: GhostChallengeEntity) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("challengeId", ghost.challengeId)
            put("playerName", ghost.playerName)
            put("score", ghost.score)
            put("zoneReached", ghost.zoneReached)
            put("yPositionsCsv", ghost.yPositionsCsv)
            put("timestamp", ghost.timestamp)
        }
        db.insertWithOnConflict("ghost_challenges", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    suspend fun getGhostById(id: String): GhostChallengeEntity? {
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery("SELECT * FROM ghost_challenges WHERE challengeId = ?", arrayOf(id))
        var ghost: GhostChallengeEntity? = null
        if (cursor.moveToFirst()) {
            val playerIdx = cursor.getColumnIndex("playerName")
            val scoreIdx = cursor.getColumnIndex("score")
            val zoneIdx = cursor.getColumnIndex("zoneReached")
            val yIdx = cursor.getColumnIndex("yPositionsCsv")
            val tsIdx = cursor.getColumnIndex("timestamp")

            ghost = GhostChallengeEntity(
                challengeId = id,
                playerName = if (playerIdx != -1) cursor.getString(playerIdx) else "",
                score = if (scoreIdx != -1) cursor.getInt(scoreIdx) else 0,
                zoneReached = if (zoneIdx != -1) cursor.getInt(zoneIdx) else 1,
                yPositionsCsv = if (yIdx != -1) cursor.getString(yIdx) else "",
                timestamp = if (tsIdx != -1) cursor.getLong(tsIdx) else System.currentTimeMillis()
            )
        }
        cursor.close()
        return ghost
    }
}

class GameDbHelper(context: Context) : SQLiteOpenHelper(context, "neon_rush_companion.db", null, 18) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE game_profile (
                id INTEGER PRIMARY KEY,
                username TEXT,
                bestScore INTEGER,
                gems INTEGER,
                transcendenceCount INTEGER,
                activeSkinId TEXT,
                unlockedSkinsCsv TEXT,
                followedUsersCsv TEXT,
                subscriptionPro INTEGER,
                dailyAttemptsToday INTEGER,
                lastDailyRushDate TEXT,
                activePilotSkinId TEXT,
                unlockedPilotSkinsCsv TEXT,
                currentStreak INTEGER,
                lastStreakLoginDate TEXT,
                totalRuns INTEGER,
                averageScore INTEGER,
                totalGemsEarned INTEGER,
                adsRemoved INTEGER,
                dailyMissionProgressCsv TEXT,
                dailyMissionsClaimedCsv TEXT,
                lastDailyMissionDate TEXT,
                weeklyMissionProgressCsv TEXT,
                weeklyMissionsClaimedCsv TEXT,
                lastWeeklyMissionDate TEXT,
                monthlyMissionProgressCsv TEXT,
                monthlyMissionsClaimedCsv TEXT,
                lastMonthlyMissionDate TEXT,
                bestZoneReached INTEGER,
                dailyRerollCount INTEGER,
                weeklyRerollCount INTEGER,
                monthlyRerollCount INTEGER,
                specialWorldTier INTEGER,
                currentRunGemsCredited INTEGER,
                currentRunBossZonesRewarded TEXT,
               currentRunMilestonesRewarded TEXT,
                checkpointsReachedCsv TEXT,
               checkpointsActivatedCsv TEXT,
                fuelTiersOwned INTEGER,
                completedWorldsCsv TEXT,
                bestComboStreak INTEGER,
                hasSeenTutorial INTEGER,
                lastUpdateReminderShownAt INTEGER,
                upgradesCsv TEXT,
                lastCrateDate TEXT,
                cratesToday INTEGER,
                crateStreak INTEGER,
                crateShardsCsv TEXT
            )
        """) 
        db.execSQL("""
            CREATE TABLE ghost_challenges (
                challengeId TEXT PRIMARY KEY,
                playerName TEXT,
                score INTEGER,
                zoneReached INTEGER,
                yPositionsCsv TEXT,
                timestamp INTEGER
            )
        """)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    if (oldVersion < 2) {
        db.execSQL("ALTER TABLE game_profile ADD COLUMN activePilotSkinId TEXT DEFAULT 'default'")
        db.execSQL("ALTER TABLE game_profile ADD COLUMN unlockedPilotSkinsCsv TEXT DEFAULT 'default'")
    }
    if (oldVersion < 3) {
        db.execSQL("ALTER TABLE game_profile ADD COLUMN currentStreak INTEGER DEFAULT 0")
        db.execSQL("ALTER TABLE game_profile ADD COLUMN lastStreakLoginDate TEXT DEFAULT ''")
    }
    if (oldVersion < 4) {
        db.execSQL("ALTER TABLE game_profile ADD COLUMN totalRuns INTEGER DEFAULT 0")
        db.execSQL("ALTER TABLE game_profile ADD COLUMN averageScore INTEGER DEFAULT 0")
        db.execSQL("ALTER TABLE game_profile ADD COLUMN totalGemsEarned INTEGER DEFAULT 0")
    }
    if (oldVersion < 5) {
        db.execSQL("ALTER TABLE game_profile ADD COLUMN adsRemoved INTEGER DEFAULT 0")
    }
    if (oldVersion < 6) {
        db.execSQL("ALTER TABLE game_profile ADD COLUMN dailyMissionProgressCsv TEXT DEFAULT ''")
        db.execSQL("ALTER TABLE game_profile ADD COLUMN dailyMissionsClaimedCsv TEXT DEFAULT ''")
        db.execSQL("ALTER TABLE game_profile ADD COLUMN lastDailyMissionDate TEXT DEFAULT ''")
        db.execSQL("ALTER TABLE game_profile ADD COLUMN weeklyMissionProgressCsv TEXT DEFAULT ''")
        db.execSQL("ALTER TABLE game_profile ADD COLUMN weeklyMissionsClaimedCsv TEXT DEFAULT ''")
        db.execSQL("ALTER TABLE game_profile ADD COLUMN lastWeeklyMissionDate TEXT DEFAULT ''")
        db.execSQL("ALTER TABLE game_profile ADD COLUMN monthlyMissionProgressCsv TEXT DEFAULT ''")
        db.execSQL("ALTER TABLE game_profile ADD COLUMN monthlyMissionsClaimedCsv TEXT DEFAULT ''")
        db.execSQL("ALTER TABLE game_profile ADD COLUMN lastMonthlyMissionDate TEXT DEFAULT ''")
    }
    if (oldVersion < 7) {
        db.execSQL("ALTER TABLE game_profile ADD COLUMN bestZoneReached INTEGER DEFAULT 0")
    }
    if (oldVersion < 8) {
        db.execSQL("ALTER TABLE game_profile ADD COLUMN dailyRerollCount INTEGER DEFAULT 0")
        db.execSQL("ALTER TABLE game_profile ADD COLUMN weeklyRerollCount INTEGER DEFAULT 0")
        db.execSQL("ALTER TABLE game_profile ADD COLUMN monthlyRerollCount INTEGER DEFAULT 0")
    }
    if (oldVersion < 9) {
        db.execSQL("ALTER TABLE game_profile ADD COLUMN specialWorldTier INTEGER DEFAULT 0")
    }
    if (oldVersion < 10) {
        db.execSQL("ALTER TABLE game_profile ADD COLUMN currentRunGemsCredited INTEGER DEFAULT 0")
        db.execSQL("ALTER TABLE game_profile ADD COLUMN currentRunBossZonesRewarded TEXT DEFAULT ''")
    }
    if (oldVersion < 11) {
        db.execSQL("ALTER TABLE game_profile ADD COLUMN currentRunMilestonesRewarded TEXT DEFAULT ''")
    }
    if (oldVersion < 12) {
        db.execSQL("ALTER TABLE game_profile ADD COLUMN checkpointsReachedCsv TEXT DEFAULT ''")
        db.execSQL("ALTER TABLE game_profile ADD COLUMN checkpointsActivatedCsv TEXT DEFAULT ''")
    }
    if (oldVersion < 13) {
        db.execSQL("ALTER TABLE game_profile ADD COLUMN fuelTiersOwned INTEGER DEFAULT 0")
    }
    if (oldVersion < 14) {
        db.execSQL("ALTER TABLE game_profile ADD COLUMN completedWorldsCsv TEXT DEFAULT ''")
    }
    if (oldVersion < 15) {
        db.execSQL("ALTER TABLE game_profile ADD COLUMN bestComboStreak INTEGER DEFAULT 0")
    }
    if (oldVersion < 18) {
        db.execSQL("ALTER TABLE game_profile ADD COLUMN lastCrateDate TEXT DEFAULT ''")
        db.execSQL("ALTER TABLE game_profile ADD COLUMN cratesToday INTEGER DEFAULT 0")
        db.execSQL("ALTER TABLE game_profile ADD COLUMN crateStreak INTEGER DEFAULT 0")
        db.execSQL("ALTER TABLE game_profile ADD COLUMN crateShardsCsv TEXT DEFAULT ''")
    }
    if (oldVersion < 17) {
        db.execSQL("ALTER TABLE game_profile ADD COLUMN upgradesCsv TEXT DEFAULT ''")
    }
    if (oldVersion < 16) {
        db.execSQL("ALTER TABLE game_profile ADD COLUMN hasSeenTutorial INTEGER DEFAULT 0")
        db.execSQL("ALTER TABLE game_profile ADD COLUMN lastUpdateReminderShownAt INTEGER DEFAULT 0")
    }
}
}
