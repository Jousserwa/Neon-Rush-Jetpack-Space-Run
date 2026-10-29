package com.neonrush.game

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.neonrush.game.db.GameDao

/**
 * SHOOTING (armor system, Stage 140).
 *  - ARMOR MAGAZINES drop on the track while you race. Collecting one loads ARMOR ROUNDS into the gun, and they stay there until fired (saved between runs):
 *      MEDIUM / HARD: 3 rounds per magazine (Pro: 5).   LEGENDARY: 70 rounds per magazine.   Ammo Packs from the Armory add rounds too.
 *  - Armor rounds are fired first. They hit harder (x1.5 on a boss) and one that touches a boss-brood baby chain-destroys ALL the babies at once.
 *  - Without armor you still have a few basic bolts each run (Medium/Hard 3, Pro 5, Legendary 12). Basic bolts kill one target at a time.
 *  - Legendary bolts chip the boss; Medium/Hard bolts clear obstacles and babies but do no boss damage. EASY: no shooting.
 * Walls, tunnels and pillars can never be shot (see canShoot). Bolt looks come from cosmetic Bolt Packs.
 */
object Shooting {
    class AmmoPack(val shots: Int, val gems: Int, val label: String)
    val AMMO = listOf(AmmoPack(20, 35, "1 ARMOR"), AmmoPack(100, 150, "5 ARMORS"))   // gem refills; capped by free room and by GEM_ARMOR_PER_DAY
    const val GEM_ARMOR_PER_DAY = 10
    class BoltPack(val id: String, val name: String, val gems: Int, val argb: Long, val argb2: Long, val art: String)
    val BOLTS = listOf(
        BoltPack("cyan", "NEON CYAN", 0, 0xFF3DE8FF, 0xFFFFFFFF, "cyan"),
        BoltPack("plasma", "PLASMA PINK", 150, 0xFFFF4DD2, 0xFFFFE0F7, "mag"),
        BoltPack("solar", "SOLAR GOLD", 150, 0xFFFFC21A, 0xFFFFF59D, "gold"),
        BoltPack("frost", "FROST", 150, 0xFF80D8FF, 0xFFE1F5FE, "ice")
    )
    const val FREE_SHOTS = 3
    const val PRO_SHOTS = 5
    const val BOSS_DAMAGE = 0.012f
    const val LEGEND_BASIC = 12
    /** Rounds one armor magazine holds in this mode. */
    @Suppress("UNUSED_PARAMETER") fun armorFor(tierName: String, isPro: Boolean): Int = MagazineBay.PER_ARMOR
    /** True when the last bolt that reached the boss was an armor round (BossGuard reads it for the x1.5). */
    @Volatile var lastArmor = false
    /** Set when the gun swaps to its next magazine (the shooting overlay shows MAGAZINE SWAP). */
    @Volatile var reloadMs = 0L
    fun setAmmo(ctx: Context, n: Int) { try { prefs(ctx).edit().putInt("ammo", n).apply() } catch (_: Throwable) { }; version += 1 }
    const val HEAT_PER_SHOT = 0.14f
    const val COOL_PER_SEC = 0.32f
    const val RESUME_AT = 0.35f
    const val FIRE_GAP_LEGEND = 0.14f
    const val FIRE_GAP_POWER = 0.45f

    /** Obstacle kinds a bolt may destroy. Everything else (glass walls, tunnels, pillars, blink hazards) is solid. */
    private val SHOOTABLE = setOf("STANDARD", "LASER", "BLADE", "STALACTITE", "STALAGMITE", "BARRIER", "ZAP_FIELD", "SPLITTER", "DRONE", "PHANTOM", "")
    fun canShoot(type: String, sub: String): Boolean = (type == "obstacle" && sub in SHOOTABLE) || (type == "bullet" && sub != "ECHO" && sub != "STRIKE")

    var version by mutableStateOf(0)
    var heat by mutableStateOf(0f)
    var overheated by mutableStateOf(false)
    var runShots by mutableStateOf(0)
    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences("neonrush_shooting", Context.MODE_PRIVATE)

    fun ammo(ctx: Context): Int { @Suppress("UNUSED_VARIABLE") val v = version; return try { prefs(ctx).getInt("ammo", 0) } catch (_: Throwable) { 0 } }
    fun ownsBolt(ctx: Context, id: String): Boolean = BOLTS.firstOrNull { it.id == id }?.let { it.gems == 0 || (try { prefs(ctx).getBoolean("own_$id", false) } catch (_: Throwable) { false }) } ?: false
    fun selectedBolt(ctx: Context): BoltPack { @Suppress("UNUSED_VARIABLE") val v = version; val id = try { prefs(ctx).getString("bolt", "cyan") ?: "cyan" } catch (_: Throwable) { "cyan" }; return BOLTS.firstOrNull { it.id == id } ?: BOLTS[0] }
    fun selectBolt(ctx: Context, id: String) { if (ownsBolt(ctx, id)) { try { prefs(ctx).edit().putString("bolt", id).apply() } catch (_: Throwable) { }; version += 1 } }

    /** Mode for a difficulty: 0 none, 1 power shots, 2 legendary unlimited + heat. */
    fun mode(tierName: String, isPro: Boolean): Int = when (tierName) { "LEGENDARY" -> 2; "MEDIUM", "HARD" -> 1; else -> 0 }

    fun newRun(isPro: Boolean, legend: Boolean = false) { heat = 0f; overheated = false; lastArmor = false; runShots = if (legend) LEGEND_BASIC else if (isPro) PRO_SHOTS else FREE_SHOTS; version += 1 }

    /** Cooling, called every frame by the overlay. */
    fun cool(dt: Float) {
        if (heat > 0f) heat = (heat - COOL_PER_SEC * dt).coerceAtLeast(0f)
        if (overheated && heat <= RESUME_AT) overheated = false
    }

    /** Loads armor rounds into the gun (kept until fired, capped). Returns how many were really added. */
    fun addArmor(ctx: Context, n: Int): Int {
        return try {
            val sp = prefs(ctx); val a = sp.getInt("ammo", 0); val b = maxOf(a, (a + n).coerceAtMost(MagazineBay.capacity(ctx)))
            sp.edit().putInt("ammo", b).apply(); version += 1; b - a
        } catch (_: Throwable) { 0 }
    }

    /** Spends one shot. Returns 0 = nothing to fire, 1 = basic bolt, 2 = armor round (armor is used first; Overdrive fires free armor rounds). */
    fun consume(ctx: Context, mode: Int): Int {
        if (mode == 0) return 0
        if (mode == 2 && LegendMoves.overdriveOn()) return 2
        val a = try { prefs(ctx).getInt("ammo", 0) } catch (_: Throwable) { 0 }
        if (a > 0) {
            val left = a - 1
            try { prefs(ctx).edit().putInt("ammo", left).apply() } catch (_: Throwable) { }
            if (left > 0 && left % MagazineBay.MAG_SIZE == 0) reloadMs = System.currentTimeMillis()   // one magazine emptied: the next clicks in
            version += 1; return 2
        }
        if (runShots > 0) { runShots -= 1; version += 1; return 1 }
        return 0
    }

    private fun gemDay(): Long = ServerClock.bestNowMs() / 86_400_000L
    /** Armors (20 shots each) already bought with gems today. */
    fun gemArmorToday(ctx: Context): Int { @Suppress("UNUSED_VARIABLE") val v = version; return try { val sp = prefs(ctx); if (sp.getLong("gday", -1L) == gemDay()) sp.getInt("gcount", 0) else 0 } catch (_: Throwable) { 0 } }

    /** Gem armor refill. Capped by free room (price is proportional when the room is smaller than the pack) and by GEM_ARMOR_PER_DAY. */
    suspend fun buyAmmo(ctx: Context, dao: GameDao, i: Int): Boolean {
        val p = AMMO.getOrNull(i) ?: return false
        val have = try { prefs(ctx).getInt("ammo", 0) } catch (_: Throwable) { 0 }
        val room = MagazineBay.capacity(ctx) - have
        val armorsRoom = room / MagazineBay.PER_ARMOR
        val dayLeft = GEM_ARMOR_PER_DAY - gemArmorToday(ctx)
        val armors = minOf(p.shots / MagazineBay.PER_ARMOR, armorsRoom, dayLeft)
        if (armors <= 0) return false
        val cost = (p.gems.toLong() * armors / (p.shots / MagazineBay.PER_ARMOR)).toInt().coerceAtLeast(1)
        val prof = dao.getProfileDirect() ?: return false
        if (prof.gems < cost) return false
        dao.updateProfile { it.copy(gems = it.gems - cost) }
        try { prefs(ctx).edit().putInt("ammo", have + armors * MagazineBay.PER_ARMOR).putLong("gday", gemDay()).putInt("gcount", gemArmorToday(ctx) + armors).apply() } catch (_: Throwable) { }
        version += 1
        return true
    }

    suspend fun buyBolt(ctx: Context, dao: GameDao, id: String): Boolean {
        val b = BOLTS.firstOrNull { it.id == id } ?: return false
        if (ownsBolt(ctx, id)) { selectBolt(ctx, id); return true }
        val prof = dao.getProfileDirect() ?: return false
        if (prof.gems < b.gems) return false
        dao.updateProfile { it.copy(gems = it.gems - b.gems) }
        try { prefs(ctx).edit().putBoolean("own_$id", true).putString("bolt", id).apply() } catch (_: Throwable) { }
        version += 1
        return true
    }
}
