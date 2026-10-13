package com.neonrush.game

import com.neonrush.game.db.GameDao
import com.neonrush.game.db.GameProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * THRUSTER TRAILS: a loadout slot that is independent of the hull and the pilot suit.
 * Free ones are earned by milestones, some cost gems, some are Pro-only. Drawn in code (ui/ThrusterFx.kt).
 */
enum class ThrusterKind { FREE, MILESTONE, GEMS, PRO, LOYALTY }

data class Thruster(
    val id: String,
    val name: String,
    val emoji: String,
    val desc: String,
    val kind: ThrusterKind,
    val gemCost: Int = 0,
    val requirement: String = "",
    val loyalty: String = "",   // PRO_3M, PRO_12M, FREE_4M, VETERAN_2Y (see ThrusterLocker.evaluate)
    val earned: (GameProfile) -> Boolean = { false }
)

object ThrusterCatalog {
    const val DEFAULT = "ion_stream"
    val ALL: List<Thruster> = listOf(
        Thruster("ion_stream", "Ion Stream", "💠", "The standard cyan jet. Clean and sharp.", ThrusterKind.FREE),
        Thruster("plasma_ribbon", "Plasma Ribbon", "🎀", "Two glowing ribbons that dance behind you.", ThrusterKind.MILESTONE,
            requirement = "Reach Zone 33 (clear Green Hell)", earned = { it.bestZoneReached >= 33 }),
        Thruster("pixel_stream", "Pixel Stream", "🟦", "Retro blocks, straight out of an arcade.", ThrusterKind.MILESTONE,
            requirement = "Finish 100 runs", earned = { it.totalRuns >= 100 }),
        Thruster("glitch_static", "Glitch Static", "📺", "Your jet tears and splits like a broken signal.", ThrusterKind.MILESTONE,
            requirement = "Score 3,000 in one run", earned = { it.bestScore >= 3000 }),
        Thruster("ink_drip", "Ink Drip", "🖋️", "Heavy neon ink that falls as you fly.", ThrusterKind.MILESTONE,
            requirement = "Keep a 14-day play streak", earned = { it.currentStreak >= 14 }),
        Thruster("star_dust", "Star Dust", "✨", "A twinkling trail of tiny stars.", ThrusterKind.GEMS, gemCost = 900),
        Thruster("prism_beam", "Prism Beam", "🌈", "A full-spectrum beam that slowly changes colour.", ThrusterKind.GEMS, gemCost = 1500),
        Thruster("circuit_sparks", "Circuit Sparks", "🔌", "Live circuit traces that crackle behind you. A Pro reward.", ThrusterKind.PRO,
            requirement = "Pro member + reach Zone 20", earned = { it.bestZoneReached >= 20 }),
        Thruster("aurora_veil", "Aurora Veil", "🌌", "Shimmering northern-lights curtains that follow your jet. A Pro reward.", ThrusterKind.PRO,
            requirement = "Pro member + score 1,500 in one run", earned = { it.bestScore >= 1500 }),
        // ---- loyalty rewards: given for time, not for spending
        Thruster("heartbeat_pulse", "Heartbeat Pulse", "💚", "A living pulse line. For pilots who stayed 4 months in a row on the free tier.", ThrusterKind.LOYALTY,
            requirement = "Play 4 months in a row on the free tier", loyalty = "FREE_4M"),
        Thruster("royal_plasma", "Royal Plasma", "👑", "A golden double helix. For 3 months of unbroken Pro.", ThrusterKind.LOYALTY,
            requirement = "3 months in a row as Pro", loyalty = "PRO_3M"),
        Thruster("supernova_wake", "Supernova Wake", "💥", "Your jet leaves shockwave rings like an exploding star. For a full year of Pro.", ThrusterKind.LOYALTY,
            requirement = "12 months in a row as Pro", loyalty = "PRO_12M"),
        Thruster("veteran_comet", "Veteran Comet", "☄️", "A long icy comet tail with orbiting stars. For pilots who have flown 2 years.", ThrusterKind.LOYALTY,
            requirement = "Fly with Neon Rush for 2 years", loyalty = "VETERAN_2Y")
    )
    fun byId(id: String) = ALL.firstOrNull { it.id == id } ?: ALL.first()
}

data class ThrusterState(
    val owned: Set<String> = setOf(ThrusterCatalog.DEFAULT),
    val active: String = ThrusterCatalog.DEFAULT,
    val progress: Map<String, String> = emptyMap()   // loyalty id -> "1.5 / 3 months"
)

class ThrusterLocker(
    private val dao: GameDao,
    private val store: HangarStore,
    private val scope: CoroutineScope,
    private val isPro: () -> Boolean,
    private val announce: (String) -> Unit
) {
    private val _state = MutableStateFlow(ThrusterState())
    val state: StateFlow<ThrusterState> = _state.asStateFlow()

    private companion object {
        const val K_OWNED = "thr_owned_csv"; const val K_ACTIVE = "thr_active"
        const val K_FIRST = "thr_first_seen"; const val K_PRO_SINCE = "thr_pro_since"; const val K_PRO_SEEN = "thr_pro_seen"
        const val K_FREE_SINCE = "thr_free_since"; const val K_FREE_SEEN = "thr_free_seen"
    }

    fun usable(id: String): Boolean {
        val t = ThrusterCatalog.byId(id)
        return id in _state.value.owned && (t.kind != ThrusterKind.PRO || isPro())
    }

    /** The trail to draw right now (falls back to the free one if a Pro trail's membership ended). */
    fun activeNow(): String = _state.value.active.let { if (usable(it)) it else ThrusterCatalog.DEFAULT }

    suspend fun load() {
        try {
            val owned = store.getList(K_OWNED).toSet() + ThrusterCatalog.DEFAULT
            val active = store.get(K_ACTIVE).ifBlank { ThrusterCatalog.DEFAULT }
            _state.value = ThrusterState(owned, active)
        } catch (_: Exception) {}
    }

    private suspend fun save() {
        try { store.putList(K_OWNED, _state.value.owned.toList()); store.put(K_ACTIVE, _state.value.active) } catch (_: Exception) {}
    }

    /**
     * Grants milestone, Pro and loyalty trails the player now qualifies for and announces each one.
     * Loyalty uses a 7-day grace so a late renewal or a missed store sync never resets a streak.
     */
    suspend fun evaluate(p: GameProfile) {
        val DAY = 86_400_000L
        val now = ServerClock.bestNowMs()
        val pro = isPro()
        var firstSeen = store.getLong(K_FIRST)
        if (firstSeen == 0L) { firstSeen = now; store.putLong(K_FIRST, now) }
        var proSince = store.getLong(K_PRO_SINCE); var proSeen = store.getLong(K_PRO_SEEN)
        var freeSince = store.getLong(K_FREE_SINCE); var freeSeen = store.getLong(K_FREE_SEEN)
        if (pro) {
            if (proSince == 0L || now - proSeen > 7 * DAY) proSince = now
            proSeen = now
            freeSince = 0L
            store.putLong(K_PRO_SINCE, proSince); store.putLong(K_PRO_SEEN, proSeen); store.putLong(K_FREE_SINCE, 0L)
        } else {
            if (freeSince == 0L || now - freeSeen > 7 * DAY) freeSince = now
            freeSeen = now
            store.putLong(K_FREE_SINCE, freeSince); store.putLong(K_FREE_SEEN, freeSeen)
        }
        val proDays = if (pro && proSince > 0L) (now - proSince) / DAY else 0L
        val freeDays = if (!pro && freeSince > 0L) (now - freeSince) / DAY else 0L
        val vetDays = (now - firstSeen) / DAY
        fun days(rule: String) = when (rule) { "PRO_3M", "PRO_12M" -> proDays; "FREE_4M" -> freeDays; else -> vetDays }
        fun need(rule: String) = when (rule) { "PRO_3M" -> 90L; "PRO_12M" -> 365L; "FREE_4M" -> 120L; else -> 730L }

        val cur = _state.value.owned
        val fresh = ThrusterCatalog.ALL.filter {
            it.id !in cur && when (it.kind) {
                ThrusterKind.MILESTONE -> it.earned(p)
                ThrusterKind.PRO -> pro && it.earned(p)
                ThrusterKind.LOYALTY -> days(it.loyalty) >= need(it.loyalty)
                else -> false
            }
        }
        val progress = ThrusterCatalog.ALL.filter { it.kind == ThrusterKind.LOYALTY && it.id !in cur }.associate {
            val d = days(it.loyalty); val n = need(it.loyalty)
            it.id to "%.1f / %.1f months".format(d / 30f, n / 30f)
        }
        _state.value = _state.value.copy(owned = cur + fresh.map { it.id }, progress = progress)
        if (fresh.isEmpty()) return
        save()
        fresh.forEach { announce("${it.emoji} NEW THRUSTER TRAIL UNLOCKED: ${it.name}! Equip it in Shop > Trails.") }
    }

    fun equip(id: String) {
        if (!usable(id)) return
        _state.value = _state.value.copy(active = id)
        scope.launch { save() }
    }

    /** Returns a message for the UI, or null on success. */
    fun buy(id: String, onDone: () -> Unit = {}) {
        val t = ThrusterCatalog.byId(id)
        if (t.kind != ThrusterKind.GEMS || id in _state.value.owned) return
        scope.launch {
            var ok = false
            dao.updateProfile { p -> if (p.gems >= t.gemCost) { ok = true; p.copy(gems = p.gems - t.gemCost) } else p }
            if (ok) {
                _state.value = _state.value.copy(owned = _state.value.owned + id, active = id)
                save()
                onDone()
            } else announce("Not enough gems for ${t.name} (${t.gemCost} 💎).")
        }
    }
}
