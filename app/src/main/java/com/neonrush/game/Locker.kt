package com.neonrush.game

import com.neonrush.game.db.GameDao
import com.neonrush.game.ui.PilotWardrobe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** One collection (hulls, suits, trails, parts) as shown in the Locker. */
data class LockerCat(val id: String, val name: String, val emoji: String, val owned: Int, val total: Int) {
    val frac: Float get() = if (total <= 0) 0f else (owned.toFloat() / total).coerceIn(0f, 1f)
    val complete: Boolean get() = total > 0 && owned >= total
}

/** A milestone reward. key is stable and stored once claimed. */
data class LockerStep(val key: String, val needFrac: Float, val gems: Int, val title: String)

object LockerCatalog {
    /** Per-collection milestones: 25 / 50 / 75 / 100 percent complete. */
    val CAT_STEPS = listOf(0.25f to 50, 0.5f to 100, 0.75f to 200, 1.0f to 500)
    fun stepsFor(catId: String): List<LockerStep> = CAT_STEPS.map { (f, g) ->
        LockerStep("$catId:${(f * 100).toInt()}", f, g, if (f >= 1f) "SET COMPLETE" else "${(f * 100).toInt()}%")
    }
    /** Whole-locker milestones. */
    val OVERALL_STEPS = listOf(
        LockerStep("all:25", 0.25f, 100, "Bronze Collector"),
        LockerStep("all:50", 0.5f, 300, "Silver Collector"),
        LockerStep("all:75", 0.75f, 700, "Gold Collector"),
        LockerStep("all:100", 1.0f, 2000, "Mythic Curator")
    )
    val TIER_NAMES = listOf("Rookie", "Bronze Collector", "Silver Collector", "Gold Collector", "Mythic Curator")
    val TIER_COLORS = longArrayOf(0xFF607D8B, 0xFFCD7F32, 0xFFCFD8DC, 0xFFFFD23F, 0xFFE040FB)
    fun tierFor(frac: Float): Int = when {
        frac >= 1f -> 4
        frac >= 0.75f -> 3
        frac >= 0.5f -> 2
        frac >= 0.25f -> 1
        else -> 0
    }
}

/**
 * Completion rewards and mastery tier. Claimed milestones are remembered; the mastery tier is stored too, so the
 * orbiting mastery stars show on the pilot everywhere from the moment the game starts.
 */
class LockerRewards(
    private val dao: GameDao,
    private val store: HangarStore,
    private val scope: CoroutineScope,
    private val announce: (String) -> Unit
) {
    private val _claimed = MutableStateFlow<Set<String>>(emptySet())
    val claimed: StateFlow<Set<String>> = _claimed.asStateFlow()
    private companion object { const val K_CLAIMED = "locker_claimed_csv"; const val K_TIER = "locker_tier" }

    suspend fun load() {
        try {
            _claimed.value = store.getList(K_CLAIMED).toSet()
            PilotWardrobe.masteryTier = store.get(K_TIER).toIntOrNull()?.coerceIn(0, 4) ?: 0
        } catch (_: Exception) {}
    }

    /** Called by the Locker page whenever it knows the current overall completion. */
    fun setTier(tier: Int) {
        if (tier == PilotWardrobe.masteryTier) return
        PilotWardrobe.masteryTier = tier
        scope.launch { try { store.put(K_TIER, tier.toString()) } catch (_: Exception) {} }
    }

    fun claim(step: LockerStep) {
        if (step.key in _claimed.value) return
        scope.launch {
            _claimed.value = _claimed.value + step.key
            try { store.putList(K_CLAIMED, _claimed.value.toList()) } catch (_: Exception) {}
            dao.updateProfile { p -> p.copy(gems = p.gems + step.gems) }
            announce("${step.title} reward claimed: +${step.gems} 💎")
        }
    }
}
