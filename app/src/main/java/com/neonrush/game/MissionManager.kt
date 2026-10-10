package com.neonrush.game

import com.neonrush.game.db.GameProfile
import java.text.SimpleDateFormat
import java.util.*

enum class MissionTier {
    DAILY, WEEKLY, MONTHLY
}

enum class MissionMetric {
    ZONE_REACHED_RUN,       // best zone reached in a single run
    SCORE_RUN,              // best score in a single run
    GEMS_COLLECTED_RUN,     // gems collected in a single run
    RUNS_COMPLETED_PERIOD,  // number of runs completed within the period
    GEMS_EARNED_PERIOD,     // total gems earned within the period
    ZONES_TOTAL_PERIOD,     // sum of zones reached across runs within the period
    ADS_WATCHED_PERIOD,     // rewarded ads watched within the period
    STREAK_LENGTH,          // current login streak
    ZONE_REACHED_LIFETIME   // best zone ever reached, lifetime
}

data class MissionTemplate(
    val id: String,
    val tier: MissionTier,
    val metric: MissionMetric,
    val target: Int,
    val description: String,
    val rewardGems: Int
)

data class MissionProgress(
    val templateId: String,
    val progress: Int,
    val claimed: Boolean
)

object MissionManager {

    private val DAILY_POOL = listOf(
        MissionTemplate("d_zone5", MissionTier.DAILY, MissionMetric.ZONE_REACHED_RUN, 5, "Reach Zone 5 in a single run", 2),
        MissionTemplate("d_score1000", MissionTier.DAILY, MissionMetric.SCORE_RUN, 1000, "Score 1000+ in a single run", 2),
        MissionTemplate("d_gems30", MissionTier.DAILY, MissionMetric.GEMS_COLLECTED_RUN, 30, "Collect 30 gems in a single run", 2),
        MissionTemplate("d_runs3", MissionTier.DAILY, MissionMetric.RUNS_COMPLETED_PERIOD, 3, "Complete 3 runs today", 2),
        MissionTemplate("d_ads2", MissionTier.DAILY, MissionMetric.ADS_WATCHED_PERIOD, 2, "Watch 2 rewarded ads", 2)
    )

    private val WEEKLY_POOL = listOf(
        MissionTemplate("w_zones50", MissionTier.WEEKLY, MissionMetric.ZONES_TOTAL_PERIOD, 50, "Reach 50 total zones this week", 7),
        MissionTemplate("w_score5000", MissionTier.WEEKLY, MissionMetric.SCORE_RUN, 5000, "Score 5000+ in a single run", 7),
        MissionTemplate("w_runs15", MissionTier.WEEKLY, MissionMetric.RUNS_COMPLETED_PERIOD, 15, "Complete 15 runs this week", 7),
        MissionTemplate("w_gems300", MissionTier.WEEKLY, MissionMetric.GEMS_EARNED_PERIOD, 300, "Collect 300 gems this week", 7)
    )

    private val MONTHLY_POOL = listOf(
        MissionTemplate("m_zone300", MissionTier.MONTHLY, MissionMetric.ZONE_REACHED_LIFETIME, 300, "Reach Zone 300 (lifetime)", 10),
        MissionTemplate("m_gems1000", MissionTier.MONTHLY, MissionMetric.GEMS_EARNED_PERIOD, 1000, "Earn 1000 gems this month", 10),
        MissionTemplate("m_streak7", MissionTier.MONTHLY, MissionMetric.STREAK_LENGTH, 7, "Reach a 7-day login streak", 10),
        MissionTemplate("m_runs60", MissionTier.MONTHLY, MissionMetric.RUNS_COMPLETED_PERIOD, 60, "Complete 60 runs this month", 10)
    )

    private fun dateKey(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    private fun weekKey(): String {
        val cal = Calendar.getInstance()
        cal.firstDayOfWeek = Calendar.MONDAY
        return "${cal.get(Calendar.YEAR)}-W${cal.get(Calendar.WEEK_OF_YEAR)}"
    }

    private fun monthKey(): String = SimpleDateFormat("yyyy-MM", Locale.US).format(Date())

    // Deterministic selection: same 3 missions all day/week/month for everyone,
    // rotating based on a seed derived from the period key.
    private fun <T> selectThree(pool: List<T>, seed: String): List<T> {
        if (pool.size <= 3) return pool
        val rnd = Random(seed.hashCode().toLong())
        return pool.shuffled(rnd).take(3)
    }
fun currentDailyMissions(rerollCount: Int = 0): List<MissionTemplate> {
    val adsMission = DAILY_POOL.first { it.id == "d_ads2" }
    val rest = DAILY_POOL.filter { it.id != "d_ads2" }
    val others = selectThree(rest, dateKey() + "-r$rerollCount").take(2)
    return listOf(adsMission) + others
}
    
fun currentWeeklyMissions(rerollCount: Int = 0): List<MissionTemplate> = selectThree(WEEKLY_POOL, weekKey() + "-r$rerollCount")
fun currentMonthlyMissions(rerollCount: Int = 0): List<MissionTemplate> = selectThree(MONTHLY_POOL, monthKey() + "-r$rerollCount")

    fun currentDailyKey(): String = dateKey()
    fun currentWeekKey(): String = weekKey()
    fun currentMonthKey(): String = monthKey()

    // --- CSV helpers for storing progress on GameProfile ---

    fun parseProgressCsv(csv: String): MutableMap<String, Int> {
        if (csv.isBlank()) return mutableMapOf()
        return csv.split(",").mapNotNull {
            val parts = it.split(":")
            if (parts.size == 2) parts[0] to (parts[1].toIntOrNull() ?: 0) else null
        }.toMap().toMutableMap()
    }

    fun progressMapToCsv(map: Map<String, Int>): String =
        map.entries.joinToString(",") { "${it.key}:${it.value}" }

    fun parseClaimedCsv(csv: String): MutableSet<String> =
        if (csv.isBlank()) mutableSetOf() else csv.split(",").toMutableSet()

    fun claimedSetToCsv(set: Set<String>): String = set.joinToString(",")

    fun findTemplate(id: String): MissionTemplate? =
        (DAILY_POOL + WEEKLY_POOL + MONTHLY_POOL).find { it.id == id }
    // ---- Special worlds 4-9: earned by repeatedly finishing the mission sets. ----
    // "sets" = how many times the player has claimed all 3 missions of a period.
    // Counters live in GameProfile.specialProgressCsv as "d:5,w:2,m:0".
    fun parseSpecialCounters(csv: String): MutableMap<String, Int> {
        val m = mutableMapOf("d" to 0, "w" to 0, "m" to 0)
        csv.split(",").forEach { part ->
            val kv = part.split(":")
            if (kv.size == 2 && kv[0] in m) m[kv[0]] = kv[1].toIntOrNull() ?: 0
        }
        return m
    }
    private fun specialCountersToCsv(m: Map<String, Int>) = "d:${m["d"] ?: 0},w:${m["w"] ?: 0},m:${m["m"] ?: 0}"

    /** key = counter id, need = how many completed sets are required for that tier. */
    data class SpecialReq(val tier: Int, val key: String, val need: Int, val text: String)
    val SPECIAL_REQS = listOf(
        SpecialReq(4, "d", 5, "Complete all 3 Daily Missions on 5 different days"),
        SpecialReq(5, "w", 2, "Complete all 3 Weekly Missions in 2 different weeks"),
        SpecialReq(6, "d", 15, "Complete all 3 Daily Missions on 15 different days"),
        SpecialReq(7, "w", 5, "Complete all 3 Weekly Missions in 5 different weeks"),
        SpecialReq(8, "m", 2, "Complete all 3 Monthly Missions in 2 different months"),
        SpecialReq(9, "m", 3, "Complete all 3 Monthly Missions in 3 different months")
    )
    fun specialReq(tier: Int): SpecialReq? = SPECIAL_REQS.firstOrNull { it.tier == tier }
    fun specialProgress(profile: GameProfile, req: SpecialReq): Int =
        parseSpecialCounters(profile.specialProgressCsv)[req.key] ?: 0

    fun checkSpecialWorldQualification(profile: GameProfile): Int {
        var tier = profile.specialWorldTier
        val dailyClaimed = parseClaimedCsv(profile.dailyMissionsClaimedCsv)
        if (tier < 1 && dailyClaimed.size >= 3) tier = 1
        val weeklyClaimed = parseClaimedCsv(profile.weeklyMissionsClaimedCsv)
        if (tier < 2 && weeklyClaimed.size >= 3) tier = 2
        val monthlyClaimed = parseClaimedCsv(profile.monthlyMissionsClaimedCsv)
        if (tier < 3 && monthlyClaimed.size >= 3) tier = 3
        // Tiers 4-9 unlock in order, once their counters are met.
        while (tier in 3 until Worlds.SPECIAL_TIER_COUNT) {
            val req = specialReq(tier + 1) ?: break
            if (specialProgress(profile, req) >= req.need) tier++ else break
        }
        return tier
    }

    // Resets progress/claimed CSVs whenever the period key has changed since last recorded.
    private fun rolledOver(profile: GameProfile): GameProfile {
    var p = profile
    if (p.lastDailyMissionDate != dateKey()) {
        p = p.copy(dailyMissionProgressCsv = "", dailyMissionsClaimedCsv = "", lastDailyMissionDate = dateKey(), dailyRerollCount = 0)
    }
    if (p.lastWeeklyMissionDate != weekKey()) {
        p = p.copy(weeklyMissionProgressCsv = "", weeklyMissionsClaimedCsv = "", lastWeeklyMissionDate = weekKey(), weeklyRerollCount = 0)
    }
    if (p.lastMonthlyMissionDate != monthKey()) {
        p = p.copy(monthlyMissionProgressCsv = "", monthlyMissionsClaimedCsv = "", lastMonthlyMissionDate = monthKey(), monthlyRerollCount = 0)
    }
    return p
}

    private fun applyMetric(
        progress: MutableMap<String, Int>,
        templates: List<MissionTemplate>,
        metric: MissionMetric,
        value: Int,
        cumulative: Boolean
    ) {
        templates.filter { it.metric == metric }.forEach { t ->
            val existing = progress[t.id] ?: 0
            progress[t.id] = if (cumulative) existing + value else maxOf(existing, value)
        }
    }

    // Call once per completed run. bestZoneLifetime should be the player's all-time best zone
    // (i.e. max(profile.bestZoneReached, thisRunZone)) computed by the caller.
    fun recordRunResult(profile: GameProfile, zoneReached: Int, score: Int, gemsThisRun: Int, bestZoneLifetime: Int): GameProfile {
    val p = rolledOver(profile)
    val daily = currentDailyMissions(p.dailyRerollCount)
    val weekly = currentWeeklyMissions(p.weeklyRerollCount)
    val monthly = currentMonthlyMissions(p.monthlyRerollCount)
        val dProg = parseProgressCsv(p.dailyMissionProgressCsv)
        val wProg = parseProgressCsv(p.weeklyMissionProgressCsv)
        val mProg = parseProgressCsv(p.monthlyMissionProgressCsv)

        applyMetric(dProg, daily, MissionMetric.ZONE_REACHED_RUN, zoneReached, cumulative = false)
        applyMetric(dProg, daily, MissionMetric.SCORE_RUN, score, cumulative = false)
        applyMetric(dProg, daily, MissionMetric.GEMS_COLLECTED_RUN, gemsThisRun, cumulative = false)
        applyMetric(dProg, daily, MissionMetric.RUNS_COMPLETED_PERIOD, 1, cumulative = true)

        applyMetric(wProg, weekly, MissionMetric.SCORE_RUN, score, cumulative = false)
        applyMetric(wProg, weekly, MissionMetric.RUNS_COMPLETED_PERIOD, 1, cumulative = true)
        applyMetric(wProg, weekly, MissionMetric.ZONES_TOTAL_PERIOD, zoneReached, cumulative = true)
        applyMetric(wProg, weekly, MissionMetric.GEMS_EARNED_PERIOD, gemsThisRun, cumulative = true)

        applyMetric(mProg, monthly, MissionMetric.RUNS_COMPLETED_PERIOD, 1, cumulative = true)
        applyMetric(mProg, monthly, MissionMetric.GEMS_EARNED_PERIOD, gemsThisRun, cumulative = true)
        applyMetric(mProg, monthly, MissionMetric.ZONE_REACHED_LIFETIME, bestZoneLifetime, cumulative = false)
        applyMetric(mProg, monthly, MissionMetric.STREAK_LENGTH, p.currentStreak, cumulative = false)

        return p.copy(
            dailyMissionProgressCsv = progressMapToCsv(dProg),
            weeklyMissionProgressCsv = progressMapToCsv(wProg),
            monthlyMissionProgressCsv = progressMapToCsv(mProg)
        )
    }

    // Call whenever the player watches a rewarded ad.
    fun recordAdWatched(profile: GameProfile): GameProfile {
    val p = rolledOver(profile)
    val daily = currentDailyMissions(p.dailyRerollCount)
        val dProg = parseProgressCsv(p.dailyMissionProgressCsv)
        applyMetric(dProg, daily, MissionMetric.ADS_WATCHED_PERIOD, 1, cumulative = true)
        return p.copy(dailyMissionProgressCsv = progressMapToCsv(dProg))
    }

    // Attempts to claim a completed, unclaimed mission. Returns the updated profile and the
    // gem reward if successful, or null if not yet complete / already claimed / invalid id.
    fun claimMission(profile: GameProfile, tier: MissionTier, missionId: String): Pair<GameProfile, Int>? {
        val template = findTemplate(missionId) ?: return null
        val progressCsv: String
        val claimedCsv: String
        when (tier) {
            MissionTier.DAILY -> { progressCsv = profile.dailyMissionProgressCsv; claimedCsv = profile.dailyMissionsClaimedCsv }
            MissionTier.WEEKLY -> { progressCsv = profile.weeklyMissionProgressCsv; claimedCsv = profile.weeklyMissionsClaimedCsv }
            MissionTier.MONTHLY -> { progressCsv = profile.monthlyMissionProgressCsv; claimedCsv = profile.monthlyMissionsClaimedCsv }
        }
        val progress = parseProgressCsv(progressCsv)
        val claimed = parseClaimedCsv(claimedCsv)
        if (claimed.contains(missionId)) return null
        val current = progress[missionId] ?: 0
        if (current < template.target) return null
        claimed.add(missionId)
        val newClaimedCsv = claimedSetToCsv(claimed)
        var updated = when (tier) {
            MissionTier.DAILY -> profile.copy(dailyMissionsClaimedCsv = newClaimedCsv)
            MissionTier.WEEKLY -> profile.copy(weeklyMissionsClaimedCsv = newClaimedCsv)
            MissionTier.MONTHLY -> profile.copy(monthlyMissionsClaimedCsv = newClaimedCsv)
        }
        // The moment a period's set reaches all 3 claimed, count one completed set (once per period).
        if (claimed.size == 3) {
            val counters = parseSpecialCounters(profile.specialProgressCsv)
            val key = when (tier) { MissionTier.DAILY -> "d"; MissionTier.WEEKLY -> "w"; MissionTier.MONTHLY -> "m" }
            counters[key] = (counters[key] ?: 0) + 1
            updated = updated.copy(specialProgressCsv = specialCountersToCsv(counters))
        }
        return updated to template.rewardGems
    }
}
