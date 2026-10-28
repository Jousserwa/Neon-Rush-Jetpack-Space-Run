package com.neonrush.game

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.neonrush.game.db.GameDao
import kotlin.random.Random

/**
 * PILOT FUSION: three kinds of cores drop from good runs (and from a finished Beacon Chain). In the Fusion Chamber on Home, three cores are
 * fused into a reward: three of one kind = PURE (best), three different = RAINBOW, otherwise MIXED. Every fusion also raises the pilot's Fusion
 * Level, which adds glowing rings to the pilot in the Fusion Chamber. Cores are permanent; nothing expires.
 */
object FusionCores {
    val NAMES = listOf("ARC", "AEGIS", "VOID")
    val ART = listOf("core_arc", "core_aegis", "core_void")
    val COLORS = listOf(0xFFFFC21A, 0xFF22E4FF, 0xFFFF4DD2)
    const val FUSE_COST = 3
    const val DROP_ZONE = 5
    const val DROP_CHANCE = 0.35f
    const val PURE_GEMS = 14
    const val RAINBOW_GEMS = 20
    const val MIXED_GEMS = 8

    var version by mutableStateOf(0)
    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences("neonrush_fusion", Context.MODE_PRIVATE)
    fun count(ctx: Context, kind: Int): Int { @Suppress("UNUSED_VARIABLE") val v = version; return try { prefs(ctx).getInt("c$kind", 0) } catch (_: Throwable) { 0 } }
    fun total(ctx: Context): Int = (0..2).sumOf { count(ctx, it) }
    fun level(ctx: Context): Int { @Suppress("UNUSED_VARIABLE") val v = version; return try { prefs(ctx).getInt("level", 0) } catch (_: Throwable) { 0 } }

    fun award(ctx: Context, kind: Int, n: Int = 1) {
        try { val sp = prefs(ctx); sp.edit().putInt("c$kind", sp.getInt("c$kind", 0) + n).apply() } catch (_: Throwable) { }
        version += 1
    }

    /** Called when a run ends: a good run may drop one core. Returns the kind dropped, or -1. */
    fun runEnded(ctx: Context, zone: Int): Int {
        if (zone < DROP_ZONE || Random.nextFloat() > DROP_CHANCE) return -1
        val k = Random.nextInt(3); award(ctx, k); return k
    }

    /** What fusing these three core kinds would pay (for the preview). */
    fun payout(kinds: List<Int>): Pair<String, Int> {
        val d = kinds.toSet().size
        return when (d) { 1 -> "PURE" to PURE_GEMS; 3 -> "RAINBOW" to RAINBOW_GEMS; else -> "MIXED" to MIXED_GEMS }
    }

    /** Fuses the chosen kinds (exactly FUSE_COST of them). Returns the result name and gems, or null if the pilot lacks the cores. */
    suspend fun fuse(ctx: Context, dao: GameDao, kinds: List<Int>): Pair<String, Int>? {
        if (kinds.size != FUSE_COST) return null
        val need = IntArray(3); kinds.forEach { need[it]++ }
        for (k in 0..2) if (count(ctx, k) < need[k]) return null
        val res = payout(kinds)
        try {
            val sp = prefs(ctx); val e = sp.edit()
            for (k in 0..2) e.putInt("c$k", sp.getInt("c$k", 0) - need[k])
            e.putInt("level", sp.getInt("level", 0) + 1).apply()
        } catch (_: Throwable) { return null }
        dao.updateProfile { p -> p.copy(gems = p.gems + res.second, totalGemsEarned = p.totalGemsEarned + res.second) }
        version += 1
        return res
    }
}
