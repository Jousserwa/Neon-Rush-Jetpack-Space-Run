package com.neonrush.game

import com.neonrush.game.db.GameDao
import com.neonrush.game.ui.HullFx
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HullMissionDef(val hullId: String, val kind: String, val target: Int, val rewardGems: Int) {
    val key get() = "$hullId:$kind"
    fun text(name: String) = if (kind == "gems") "Collect $target gems with $name" else "Make $target close calls with $name"
}

data class SetBonusDef(val id: String, val name: String, val hullIds: List<String>, val rewardGems: Int, val blurb: String)

data class HullMissionState(val def: HullMissionDef, val progress: Int, val claimed: Boolean) {
    val done get() = progress >= def.target
}
data class SetBonusState(val def: SetBonusDef, val ownedCount: Int, val claimed: Boolean) {
    val complete get() = ownedCount >= def.hullIds.size
}

data class ProgressionState(
    val masteryMeters: Map<String, Int> = emptyMap(),
    val masteryLevels: Map<String, Int> = emptyMap(),
    val missions: List<HullMissionState> = emptyList(),
    val sets: List<SetBonusState> = emptyList()
)

/**
 * Hull Mastery (distance -> cosmetic level pips 0..5), per-hull missions (gems / close calls),
 * Set Bonuses (own a whole set -> one-time gem reward), and the Ranked Season hull grant.
 * All cosmetic or small one-time gems; nothing here is sold.
 */
class HullProgression(
    private val dao: GameDao,
    private val store: HangarStore,
    private val scope: CoroutineScope,
    private val hangar: HangarController
) {
    companion object {
        val MASTERY_STEPS = listOf(2_000, 8_000, 20_000, 50_000, 100_000)   // meters flown per level
        fun level(meters: Int) = MASTERY_STEPS.count { meters >= it }

        const val K_COUNTS = "hull_mission_counts"       // "hullId:kind=n,..."
        const val K_MCLAIMED = "hull_mission_claimed"
        const val K_SETCLAIMED = "set_bonus_claimed"
        const val K_RANKED = "ranked_hull_granted"

        val SETS = listOf(
            SetBonusDef("sky", "Sky Set", listOf("aurora_serpent", "crystal_phoenix", "stormcaller"), 120, "Aurora, wings and storm"),
            SetBonusDef("fire", "Ember Set", listOf("inferno_sovereign", "supernova_heart", "crystal_phoenix"), 120, "Everything that burns"),
            SetBonusDef("deep", "Deep Set", listOf("cosmic_koi", "event_horizon", "quantum_mirage"), 120, "Calm, void and mirage"),
            SetBonusDef("apex", "Apex Trio", HullCatalog.BUNDLE_APEX_IDS, 150, "Wyrm, Horizon and Sovereign"),
            SetBonusDef("all", "Full Hangar", HullCatalog.SOLD.map { it.id }, 400, "Every sold hull")
        )
    }

    private val _state = MutableStateFlow(ProgressionState())
    val state: StateFlow<ProgressionState> = _state.asStateFlow()

    private var meters = mutableMapOf<String, Int>()
    private var counts = mutableMapOf<String, Int>()       // "hullId:gems" / "hullId:cc"
    private var mClaimed = setOf<String>()
    private var setClaimed = setOf<String>()

    private fun parse(s: String) = s.split(",").mapNotNull { e -> e.split("=").takeIf { it.size == 2 }?.let { it[0] to (it[1].toIntOrNull() ?: 0) } }.toMap()
    private fun ser(m: Map<String, Int>) = m.entries.joinToString(",") { "${it.key}=${it.value}" }

    fun start() = scope.launch {
        meters = parse(store.get(HangarStore.K_MASTERY)).toMutableMap()
        counts = parse(store.get(K_COUNTS)).toMutableMap()
        mClaimed = store.getList(K_MCLAIMED).toSet()
        setClaimed = store.getList(K_SETCLAIMED).toSet()
        publish()
    }

    private fun isPremiumOwned(id: String) = id in hangar.ownedPremium() && HullCatalog.isPremium(id)

    /** Credit flown meters to the hull that was active (call at each sector change and at run end). */
    fun creditDistance(hullId: String, deltaMeters: Int) {
        if (deltaMeters <= 0 || !isPremiumOwned(hullId)) return
        meters[hullId] = (meters[hullId] ?: 0) + deltaMeters
        persistAndPublish { store.put(HangarStore.K_MASTERY, ser(meters)) }
    }

    fun onGem(hullId: String, n: Int = 1) = bump("$hullId:gems", n, hullId)
    fun onCloseCall(hullId: String) = bump("$hullId:cc", 1, hullId)
    private fun bump(key: String, n: Int, hullId: String) {
        if (!isPremiumOwned(hullId)) return
        counts[key] = (counts[key] ?: 0) + n
        persistAndPublish { store.put(K_COUNTS, ser(counts)) }
    }

    private var dirty = 0
    private fun persistAndPublish(write: suspend () -> Unit) {
        // Gems/close calls happen often; flush every 10 changes (and always on run end via flush()).
        dirty++
        if (dirty >= 10) { dirty = 0; scope.launch { write(); publish() } } else publishNow()
    }
    fun flush() { scope.launch {
        store.put(HangarStore.K_MASTERY, ser(meters)); store.put(K_COUNTS, ser(counts)); dirty = 0; publish() } }

    private fun publishNow() { _state.value = build(); HullFx.mastery = meters.mapValues { level(it.value) } }
    private fun publish() = publishNow()

    private fun build(): ProgressionState {
        val owned = hangar.ownedPremium()
        val missions = HullCatalog.SOLD.filter { it.id in owned }.flatMap { h ->
            listOf(HullMissionDef(h.id, "gems", 50, 25), HullMissionDef(h.id, "cc", 15, 25))
        }.map { HullMissionState(it, counts["${it.hullId}:${it.kind}"] ?: 0, it.key in mClaimed) }
        val sets = SETS.map { s -> SetBonusState(s, s.hullIds.count { it in owned }, s.id in setClaimed) }
        return ProgressionState(meters.toMap(), meters.mapValues { level(it.value) }, missions, sets)
    }

    fun claimMission(key: String) { scope.launch {
        val m = build().missions.firstOrNull { it.def.key == key } ?: return@launch
        if (!m.done || m.claimed) return@launch
        mClaimed = mClaimed + key; store.putList(K_MCLAIMED, mClaimed.toList())
        dao.updateProfile { it.copy(gems = it.gems + m.def.rewardGems) }
        publish()
    } }

    fun claimSet(id: String) { scope.launch {
        val s = build().sets.firstOrNull { it.def.id == id } ?: return@launch
        if (!s.complete || s.claimed) return@launch
        setClaimed = setClaimed + id; store.putList(K_SETCLAIMED, setClaimed.toList())
        dao.updateProfile { it.copy(gems = it.gems + s.def.rewardGems) }
        publish()
    } }

    /**
     * Ranked Season hull. Call when the player's season rank is known (top 100 of the ranked board).
     * IMPORTANT: leaderboard docs are client-written, so for a real award validate the rank server-side
     * (Cloud Function that writes /season_winners/{uid}) and call this only after reading that doc.
     */
    fun onSeasonRank(rank: Int) { scope.launch {
        if (rank in 1..100 && store.get(K_RANKED) != "1") {
            hangar.grantEarned("apex_laurel"); store.put(K_RANKED, "1")
        }
    } }

    /** Re-publish after ownership changes (purchase/restore) so missions/sets appear. */
    fun refresh() { scope.launch { publish() } }
}
