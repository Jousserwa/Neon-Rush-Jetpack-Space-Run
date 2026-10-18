package com.neonrush.game

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The drip-feed calendar. Money never skips it: nothing here can be bought before its month, and a part also needs a
 * zone milestone, a suit also needs a boss win. Month 0 = live at launch, month k = k months after the launch month
 * (same calendar as the hull and trail drops, see HullCatalog.localReleaseMs).
 */
object DropSchedule {
    /** QA only: true = every drop is open so the shop can be tested. Keep false: the real locks are on. */
    const val TEST_OPEN = false

    // ---------- Pilot parts: 2 a month, always a cheaper piece paired with a dearer one, never two premiums together ----------
    // (id, drop month, best zone the pilot must have reached: cheap 5-7, mid 7-9, premium 10)
    private val PARTS = listOf(
        Triple("antenna_array", 0, 5), Triple("fire_inferno", 0, 8),
        Triple("flag_scarf", 1, 5), Triple("banner_pole", 1, 7),
        Triple("neon_scarf", 2, 6), Triple("phoenix_wings", 2, 10),
        Triple("crest_fin", 3, 6), Triple("spur_boots", 3, 7),
        Triple("gold_chain", 4, 5), Triple("royal_cape", 4, 8),
        Triple("twin_pods", 5, 6), Triple("orbital_drones", 5, 10),
        Triple("chain_shackle", 6, 5), Triple("magnet_cuffs", 6, 8),
        Triple("halo_ring", 7, 6), Triple("fire_azure", 7, 8),
        Triple("power_gauntlet", 8, 6), Triple("singularity_crown", 8, 10),
        Triple("spike_pauldron", 9, 7), Triple("gold_epaulette", 9, 9),
        Triple("skull_guard", 10, 7), Triple("crystal_shard", 10, 9),
        Triple("fire_violet", 11, 8), Triple("mech_wings", 11, 10),
        Triple("oni_horns", 12, 9), Triple("blade_bracer", 12, 9),
        Triple("wing_boots", 13, 9),
        // Showpiece helmets and boots: locked, one opens every 2 months (cheaper and dearer pieces alternate)
        Triple("helm_storm", 2, 8), Triple("boots_thunder", 4, 10), Triple("helm_samurai", 6, 10), Triple("boots_talon", 8, 8),
        Triple("helm_dragon", 10, 10), Triple("boots_prism", 12, 8), Triple("boots_jet", 14, 10), Triple("helm_glacier", 16, 9)
    )
    private val PART_BY_ID = PARTS.associateBy { it.first }

    fun partMonth(id: String): Int = PART_BY_ID[id]?.second ?: 0
    fun partZone(id: String): Int = PART_BY_ID[id]?.third ?: 0
    fun partReleaseMs(id: String): Long = HullCatalog.localReleaseMs(partMonth(id))
    fun partReleased(id: String, nowMs: Long): Boolean = TEST_OPEN || partMonth(id) <= 0 || partReleaseMs(id) <= nowMs

    enum class PartStatus { OPEN, NOT_RELEASED, NEED_ZONE }
    fun partStatus(id: String, bestZone: Int, nowMs: Long): PartStatus = when {
        TEST_OPEN -> PartStatus.OPEN
        !partReleased(id, nowMs) -> PartStatus.NOT_RELEASED
        bestZone < partZone(id) -> PartStatus.NEED_ZONE
        else -> PartStatus.OPEN
    }

    /** The next calendar month that still has parts to reveal (for the "next drop" line), or null when everything is out. */
    fun nextPartDropMs(nowMs: Long): Long? =
        PARTS.map { partReleaseMs(it.first) }.filter { it > nowMs }.minOrNull()

    fun dateLabel(ms: Long): String =
        java.text.SimpleDateFormat("d MMM yyyy", java.util.Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }.format(java.util.Date(ms))

    // ---------- Classic hulls: real money, one released every 2 months ----------
    data class ClassicHull(val id: String, val month: Int, val usd: String) {
        val productId: String get() = "neonrush_hull_$id"
    }
    private val CLASSIC_HULLS = listOf(
        ClassicHull("solar_comet", 0, "$1.99"),
        ClassicHull("ice_shard", 2, "$1.99"),
        ClassicHull("magenta_pulse", 4, "$2.99"),
        ClassicHull("static_storm", 6, "$0.99"),
        ClassicHull("vaporwave_wave", 8, "$0.99"),
        ClassicHull("gold_transcendence", 10, "$2.99"),
        ClassicHull("phantom_echo", 12, "$1.99"),
        ClassicHull("matrix_grid", 14, "$1.99")
    )
    private val CLASSIC_BY_ID = CLASSIC_HULLS.associateBy { it.id }
    fun classicHull(id: String): ClassicHull? = CLASSIC_BY_ID[id]
    fun classicReleaseMs(id: String): Long = HullCatalog.localReleaseMs(CLASSIC_BY_ID[id]?.month ?: 0)
    fun classicReleased(id: String, nowMs: Long): Boolean {
        val h = CLASSIC_BY_ID[id] ?: return true
        return TEST_OPEN || h.month <= 0 || classicReleaseMs(id) <= nowMs
    }

    // ---------- Pilot suits: one a month. When it is out, win a boss to earn the right to buy it. ----------
    private val SUITS = listOf(
        "chrome_reaper" to 0, "solar_flare" to 1, "void_walker" to 2, "toxic_bloom" to 3,
        "circuit_breaker" to 4, "golden_protocol" to 5, "glitch_core" to 6,
        // the four story suits join the same one-a-month calendar
        "blackout_runner" to 7, "signal_ghost" to 8, "convict_grey" to 9, "apex_predator" to 10,
        // the next four join the same calendar, one a month after that
        "prism_vanguard" to 11, "iron_wraith" to 12, "nova_sprint" to 13, "obsidian_pulse" to 14
    )
    private val SUIT_BY_ID = SUITS.toMap()
    fun isDropSuit(id: String) = id in SUIT_BY_ID
    fun suitMonth(id: String): Int = SUIT_BY_ID[id] ?: 0
    fun suitReleaseMs(id: String): Long = HullCatalog.localReleaseMs(suitMonth(id))
    fun suitReleased(id: String, nowMs: Long): Boolean = TEST_OPEN || suitMonth(id) <= 0 || suitReleaseMs(id) <= nowMs
    /** Suits in release order. */
    fun suitOrder(): List<String> = SUITS.sortedBy { it.second }.map { it.first }
}

/**
 * Suit right-to-buy. Every boss win opens exactly one released suit, oldest first, so the month's suit and a real boss
 * victory are both needed and a pile-up of drops still has to be earned one boss at a time. Opened suits are remembered forever.
 */
class SuitDropper(
    private val store: HangarStore,
    private val scope: CoroutineScope,
    private val onOpened: (String) -> Unit
) {
    private val _open = MutableStateFlow<Set<String>>(emptySet())
    val open: StateFlow<Set<String>> = _open.asStateFlow()
    private var lastWinMs = 0L
    private companion object { const val K = "suit_drop_open_csv" }

    suspend fun load(): Set<String> {
        try { _open.value = store.getList(K).toSet() } catch (_: Exception) {}
        return _open.value
    }

    /** Returns the suit id that was opened, or null. [owned] = suits the player already has. */
    fun onBossWin(owned: Set<String>) {
        val nowMs = ServerClock.bestNowMs()
        if (nowMs - lastWinMs < 4000L) return          // the sim can report one kill on several ticks
        lastWinMs = nowMs
        val next = DropSchedule.suitOrder().firstOrNull {
            it !in _open.value && it !in owned && DropSchedule.suitReleased(it, nowMs)
        } ?: return
        _open.value = _open.value + next
        scope.launch { try { store.putList(K, _open.value.toList()) } catch (_: Exception) {} }
        onOpened(next)
    }
}
