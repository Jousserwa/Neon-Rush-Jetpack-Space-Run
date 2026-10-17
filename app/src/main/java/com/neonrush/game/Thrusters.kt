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

/** Collection rarity: drives the card frame, the badge and how rare the item feels. */
enum class Rarity(val label: String, val argb: Long) {
    COMMON("COMMON", 0xFF9FB3C8), RARE("RARE", 0xFF4FC3F7), EPIC("EPIC", 0xFFB36BFF), LEGENDARY("LEGENDARY", 0xFFFFC107)
}

data class Thruster(
    val id: String,
    val name: String,
    val emoji: String,
    val desc: String,
    val kind: ThrusterKind,
    val rarity: Rarity = Rarity.COMMON,
    val gemCost: Int = 0,
    val requirement: String = "",
    val loyalty: String = "",   // PRO_3M, PRO_12M, FREE_4M, VETERAN_2Y (see ThrusterLocker.evaluate)
    val dropMonth: Int = 0,      // 0 = always available; k>0 = monthly drop k (same calendar as the hull drops)
    val usd: String = "",        // real-money price ("$1.99"); empty = bought with gems / earned. Product id: neonrush_trail_<id>
    val earned: (GameProfile) -> Boolean = { false }
) {
    val productId: String get() = "neonrush_trail_$id"
    val realMoney: Boolean get() = usd.isNotEmpty()
}

object ThrusterCatalog {
    const val DEFAULT = "contrail"
    fun releaseMs(t: Thruster): Long = HullCatalog.localReleaseMs(t.dropMonth)
    /** When THIS player can buy a drop: Pro at release, everyone else after the early-access window. */
    fun buyableAtMs(t: Thruster, pro: Boolean): Long = if (t.dropMonth <= 0) 0L else HullCatalog.buyableAtMs(pro, releaseMs(t))
    /** Counts toward the collection once it has been released to anyone (so Locker % never includes the far future). */
    fun released(t: Thruster, nowMs: Long): Boolean = t.dropMonth <= 0 || releaseMs(t) <= nowMs
    /**
     * Trails are WAKES, not auras: they are made of solid objects (dashes, pixels, digits, leaves, coins...) that are left
     * on your actual flight path and stay in the world while you fly on. Hull effects glow around the ship; trails are matter behind it.
     */
    val ALL: List<Thruster> = listOf(
        Thruster("contrail", "Contrail", "✈️", "Clean white dashes laid along your exact flight path.", ThrusterKind.FREE, Rarity.COMMON),
        Thruster("pixel_trail", "Pixel Trail", "🟦", "Chunky 8-bit blocks that stay where you flew.", ThrusterKind.GEMS, Rarity.COMMON,
            usd = "$1.99", requirement = "Available at launch", dropMonth = 0),
        Thruster("code_stream", "Code Stream", "💻", "Binary digits drop out of your jetpack like a hacked terminal.", ThrusterKind.GEMS, Rarity.RARE,
            usd = "$1.99", requirement = "Monthly drop", dropMonth = 1),
        Thruster("ink_blots", "Ink Blots", "🖋️", "Heavy ink that splats, drips and falls behind you.", ThrusterKind.MILESTONE, Rarity.RARE,
            requirement = "Keep a 14-day play streak", earned = { it.currentStreak >= 14 }),
        Thruster("leaf_wake", "Leaf Wake", "🍃", "Tumbling leaves, a trophy from Green Hell.", ThrusterKind.GEMS, Rarity.RARE,
            usd = "$1.99", requirement = "Monthly drop", dropMonth = 2),
        Thruster("constellation", "Constellation", "✨", "Stars joined by thin lines, drawing your route across the sky.", ThrusterKind.GEMS, Rarity.EPIC,
            usd = "$1.99", requirement = "Monthly drop", dropMonth = 3),
        Thruster("prism_shards", "Prism Shards", "💎", "Tumbling crystal facets in every colour.", ThrusterKind.GEMS, Rarity.EPIC, gemCost = 1500, requirement = "Monthly drop", dropMonth = 10),
        Thruster("circuit_trace", "Circuit Trace", "🔌", "A copper circuit board prints itself behind you, with a pulse running down it. A Pro reward.", ThrusterKind.PRO, Rarity.EPIC,
            requirement = "Pro member + reach Zone 20", earned = { it.bestZoneReached >= 20 }),
        Thruster("sonic_rings", "Sonic Rings", "🔊", "Sonar arcs ripple out behind every move. A Pro reward.", ThrusterKind.PRO, Rarity.EPIC,
            requirement = "Pro member + score 1,500 in one run", earned = { it.bestScore >= 1500 }),
        // ---- loyalty rewards: given for time, not for spending
        Thruster("heartbeat_pulse", "Heartbeat Pulse", "💚", "Your flight path turns into a heart-monitor line. For 4 months in a row on the free tier.", ThrusterKind.LOYALTY, Rarity.EPIC,
            requirement = "Play 4 months in a row on the free tier", loyalty = "FREE_4M"),
        Thruster("gold_rush", "Gold Rush", "🪙", "Spinning gold coins pour out behind you. For 3 months of unbroken Pro.", ThrusterKind.LOYALTY, Rarity.LEGENDARY,
            requirement = "3 months in a row as Pro", loyalty = "PRO_3M"),
        Thruster("starburst_wake", "Starburst Wake", "🌟", "Huge starbursts pop along your route. For a full year of Pro.", ThrusterKind.LOYALTY, Rarity.LEGENDARY,
            requirement = "12 months in a row as Pro", loyalty = "PRO_12M"),
        Thruster("veteran_comet", "Veteran Comet", "☄️", "A long icy comet tail that bends with your flight. For pilots who have flown 2 years.", ThrusterKind.LOYALTY, Rarity.LEGENDARY,
            requirement = "Fly with Neon Rush for 2 years", loyalty = "VETERAN_2Y"),
        // ---- monthly drops: one new trail a month on the SAME calendar as the hulls (Pro get it first, everyone else after the early-access window)
        Thruster("paper_planes", "Paper Planes", "🛩️", "Folded paper planes glide off your pack and tumble in your wake.", ThrusterKind.GEMS, Rarity.EPIC, usd = "$1.99", requirement = "Monthly drop", dropMonth = 4),
        Thruster("snowflakes", "Snowflakes", "❄️", "Six-armed crystal flakes drift down out of your flight path.", ThrusterKind.GEMS, Rarity.EPIC, usd = "$1.99", requirement = "Monthly drop", dropMonth = 5),
        Thruster("sakura_petals", "Sakura Petals", "🌸", "Pink petals spin loose behind you like a spring storm.", ThrusterKind.GEMS, Rarity.EPIC, usd = "$1.99", requirement = "Monthly drop", dropMonth = 6),
        Thruster("dice_roll", "Dice Roll", "🎲", "Tumbling dice, every one a different roll. Feeling lucky?", ThrusterKind.GEMS, Rarity.EPIC, usd = "$1.99", requirement = "Monthly drop", dropMonth = 7),
        Thruster("sheet_music", "Sheet Music", "🎵", "Neon notes pop off a bobbing staff as you fly.", ThrusterKind.GEMS, Rarity.EPIC, usd = "$1.99", requirement = "Monthly drop", dropMonth = 8),
        Thruster("soap_bubbles", "Soap Bubbles", "🫧", "Shimmering bubbles float up from your wake and drift.", ThrusterKind.GEMS, Rarity.EPIC, usd = "$1.99", requirement = "Monthly drop", dropMonth = 9)
    )
    fun byId(id: String) = ALL.firstOrNull { it.id == id } ?: ALL.first()
}

data class ThrusterState(
    val owned: Set<String> = setOf(ThrusterCatalog.DEFAULT),
    val active: String = ThrusterCatalog.DEFAULT,
    val progress: Map<String, String> = emptyMap(),   // loyalty id -> "1.5 / 3 months"
    val newDrops: Set<String> = emptySet()            // drops that went live for this player and have not been looked at yet
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
        const val K_FREE_SINCE = "thr_free_since"; const val K_FREE_SEEN = "thr_free_seen"; const val K_DROPS_SEEN = "thr_drops_seen"
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
            checkDrops()
        } catch (_: Exception) {}
    }

    /** Announces monthly drops that just went live for this player (once each). */
    private suspend fun checkDrops() {
        val now = ServerClock.bestNowMs()
        val seen = store.getList(K_DROPS_SEEN).toSet()
        val live = ThrusterCatalog.ALL.filter { it.dropMonth > 0 && ThrusterCatalog.buyableAtMs(it, isPro()) <= now && it.id !in owned() }
        val fresh = live.filter { it.id !in seen }
        if (fresh.isNotEmpty()) {
            store.putList(K_DROPS_SEEN, (seen + fresh.map { it.id }).toList())
            _state.value = _state.value.copy(newDrops = fresh.map { it.id }.toSet())
            fresh.forEach { announce("${it.emoji} NEW TRAIL DROP: ${it.name}! Shop > Trails.") }
        }
    }
    private fun owned(): Set<String> = _state.value.owned
    /** Re-check after Pro status or the clock changes (a Pro member sees a drop days before everyone else). */
    fun recheckDrops() { scope.launch { try { checkDrops() } catch (_: Exception) {} } }
    fun clearNew() { if (_state.value.newDrops.isNotEmpty()) _state.value = _state.value.copy(newDrops = emptySet()) }

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

    /** A real-money trail was just bought through the store. */
    fun grantPurchased(id: String) {
        val t = ThrusterCatalog.ALL.firstOrNull { it.id == id } ?: return
        if (!t.realMoney) return
        _state.value = _state.value.copy(owned = _state.value.owned + id, active = id)
        scope.launch { save() }
    }

    /** Real-money trails the store says the player owns (restore after reinstall / new phone). */
    fun grantFromStore(ids: Collection<String>) {
        val have = ids.filter { ThrusterCatalog.ALL.firstOrNull { t -> t.id == it }?.realMoney == true }.toSet()
        if (have.isEmpty() || _state.value.owned.containsAll(have)) return
        _state.value = _state.value.copy(owned = _state.value.owned + have)
        scope.launch { save() }
    }

    /**
     * After a real store sync: a real-money trail that is not backed by a store purchase (old test builds, or a trail that
     * used to be earned or bought with gems) goes back to the shop and is taken off.
     */
    fun syncRealMoney(ids: Collection<String>) {
        val have = ids.toSet()
        val keep = _state.value.owned.filter { id -> ThrusterCatalog.ALL.firstOrNull { it.id == id }?.realMoney != true || id in have }.toSet() + ThrusterCatalog.DEFAULT
        if (keep != _state.value.owned) {
            val active = if (_state.value.active in keep) _state.value.active else ThrusterCatalog.DEFAULT
            _state.value = _state.value.copy(owned = keep, active = active)
            scope.launch { save() }
        }
    }

    fun equip(id: String) {
        if (!usable(id)) return
        _state.value = _state.value.copy(active = id)
        scope.launch { save() }
    }

    /** Returns a message for the UI, or null on success. */
    fun buy(id: String, onDone: () -> Unit = {}) {
        val t = ThrusterCatalog.byId(id)
        if (t.kind != ThrusterKind.GEMS || t.realMoney || id in _state.value.owned) return
        if (ThrusterCatalog.buyableAtMs(t, isPro()) > ServerClock.bestNowMs()) { announce("${t.name} is not available to you yet."); return }
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
