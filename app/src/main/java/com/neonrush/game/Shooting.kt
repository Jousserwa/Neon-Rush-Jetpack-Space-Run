package com.neonrush.game

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.neonrush.game.db.GameDao

/**
 * SHOOTING.
 *  - LEGENDARY (Pro): unlimited bolts, limited only by a heat meter. Bolts destroy obstacles and chip the boss.
 *  - MEDIUM / HARD: a few free POWER SHOTS each run, plus shots from Ammo Packs (bought with gems, never charged per bullet).
 *    Power shots destroy obstacles but do no boss damage.
 *  - EASY: no shooting.
 * Walls, tunnels and pillars can never be shot (see canShoot). Bolt looks come from cosmetic Bolt Packs.
 */
object Shooting {
    class AmmoPack(val shots: Int, val gems: Int, val label: String)
    val AMMO = listOf(AmmoPack(10, 30, "10 POWER SHOTS"), AmmoPack(30, 75, "30 POWER SHOTS"))
    class BoltPack(val id: String, val name: String, val gems: Int, val argb: Long, val argb2: Long)
    val BOLTS = listOf(
        BoltPack("cyan", "NEON CYAN", 0, 0xFF3DE8FF, 0xFFFFFFFF),
        BoltPack("plasma", "PLASMA PINK", 150, 0xFFFF4DD2, 0xFFFFE0F7),
        BoltPack("ember", "EMBER", 150, 0xFFFF6D00, 0xFFFFF59D),
        BoltPack("frost", "FROST", 150, 0xFF80D8FF, 0xFFE1F5FE)
    )
    const val FREE_SHOTS = 3
    const val PRO_SHOTS = 5
    const val BOSS_DAMAGE = 0.01f
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
    fun mode(tierName: String, isPro: Boolean): Int = when (tierName) { "LEGENDARY" -> if (isPro) 2 else 1; "MEDIUM", "HARD" -> 1; else -> 0 }

    fun newRun(isPro: Boolean) { heat = 0f; overheated = false; runShots = if (isPro) PRO_SHOTS else FREE_SHOTS; version += 1 }

    /** Cooling, called every frame by the overlay. */
    fun cool(dt: Float) {
        if (heat > 0f) heat = (heat - COOL_PER_SEC * dt).coerceAtLeast(0f)
        if (overheated && heat <= RESUME_AT) overheated = false
    }

    /** Spends what one shot costs. Returns false when the pilot cannot shoot right now. */
    fun consume(ctx: Context, mode: Int): Boolean {
        when (mode) {
            2 -> { if (overheated) return false; heat += HEAT_PER_SHOT; if (heat >= 1f) { heat = 1f; overheated = true }; return true }
            1 -> {
                if (runShots > 0) { runShots -= 1; return true }
                val a = try { prefs(ctx).getInt("ammo", 0) } catch (_: Throwable) { 0 }
                if (a <= 0) return false
                try { prefs(ctx).edit().putInt("ammo", a - 1).apply() } catch (_: Throwable) { }
                version += 1
                return true
            }
        }
        return false
    }

    suspend fun buyAmmo(ctx: Context, dao: GameDao, i: Int): Boolean {
        val p = AMMO.getOrNull(i) ?: return false
        val prof = dao.getProfileDirect() ?: return false
        if (prof.gems < p.gems) return false
        dao.updateProfile { it.copy(gems = it.gems - p.gems) }
        try { val sp = prefs(ctx); sp.edit().putInt("ammo", sp.getInt("ammo", 0) + p.shots).apply() } catch (_: Throwable) { }
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
