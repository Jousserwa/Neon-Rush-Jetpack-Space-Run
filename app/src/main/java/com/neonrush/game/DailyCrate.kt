package com.neonrush.game

import com.neonrush.game.db.GameProfile
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.random.Random

/** What the player got from a crate, for the reveal screen. */
data class CrateResult(
    val gems: Int,
    val shardHullId: String?,   // which hull the shards belong to (null = no shards)
    val shardCount: Int,
    val unlockedHullId: String?, // set when these shards completed a hull
    val message: String?,        // quote / tip / streak text
    val streak: Int,
    val isStreakBonus: Boolean
)

/**
 * Daily crate rules.
 *  - Free pilots: 1 crate per day, opened by watching a rewarded ad.
 *  - Pro pilots (and players with ads removed): no ad; Pro also gets a 2nd crate.
 *  - Prizes: small gems (scarce currency!), hull shards, or a quote (+1 gem).
 *  - 7-day crate streak: guaranteed bonus.
 *  - Shards are per-hull; collecting enough unlocks that hull for free.
 */
object DailyCrate {
    /** Shards needed to unlock each paid hull (scaled to its gem price). */
    val HULL_SHARDS = linkedMapOf(
        "purple_square" to 10,
        "green_triangle" to 15,
        "solar_comet" to 18,
        "ice_shard" to 22,
        "magenta_pulse" to 25,
        "static_storm" to 35,
        "vaporwave_wave" to 45,
        "gold_transcendence" to 60,
        "phantom_echo" to 70,
        "matrix_grid" to 80
    )

    // Cheaper hulls drop more often.
    private val HULL_WEIGHT = mapOf(
        "purple_square" to 40, "green_triangle" to 30, "magenta_pulse" to 20,
        "solar_comet" to 28, "ice_shard" to 24, "static_storm" to 14,
        "vaporwave_wave" to 10, "phantom_echo" to 5,
        "gold_transcendence" to 7, "matrix_grid" to 3
    )

    val QUOTES = listOf(
        "Every great run starts with one thruster burn.",
        "Fall behind? The next sector doesn't know your last crash.",
        "Speed is nice. Staying alive is nicer.",
        "The pilots you admire also hit the barrier once.",
        "Small gems, big ambitions.",
        "Your best score is just waiting for a better attempt.",
        "Breathe. Dodge. Repeat.",
        "A calm hand beats a fast thumb.",
        "Today's crash is tomorrow's shortcut.",
        "Even the boss runs out of bullets.",
        "Fuel the dream, then outrun it.",
        "No Wi-Fi, no problem. The sky is offline too.",
        "You're one sector closer than you were yesterday.",
        "Neon never sleeps, but you should. Then run again.",
        "Progress is a series of near-misses.",
        "Aim for the gap, not the wall.",
        "Quit the run, never the habit.",
        "Great pilots are just persistent ones."
    )

    private fun fmt() = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    fun today(): String = fmt().format(Date())
    private fun yesterday(): String {
        val c = Calendar.getInstance(); c.add(Calendar.DAY_OF_YEAR, -1)
        return fmt().format(c.time)
    }

    fun cratesAllowed(isPro: Boolean) = if (isPro) 2 else 1
    fun cratesOpenedToday(prof: GameProfile) = if (prof.lastCrateDate == today()) prof.cratesToday else 0
    fun isReady(prof: GameProfile, isPro: Boolean) = cratesOpenedToday(prof) < cratesAllowed(isPro)

    fun millisUntilNextCrate(): Long {
        val c = Calendar.getInstance()
        c.add(Calendar.DAY_OF_YEAR, 1)
        c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
        return (c.timeInMillis - System.currentTimeMillis()).coerceAtLeast(0L)
    }

    fun countdownText(): String {
        val m = millisUntilNextCrate() / 60000
        return "${m / 60}h ${m % 60}m"
    }

    // ---- shard storage: "magenta_pulse:7,green_triangle:3" ----
    fun shardMap(csv: String): Map<String, Int> =
        csv.split(",").mapNotNull {
            val p = it.split(":")
            if (p.size == 2) p[1].toIntOrNull()?.let { n -> p[0] to n } else null
        }.toMap()

    fun shards(csv: String, hullId: String): Int = shardMap(csv)[hullId] ?: 0

    private fun withShards(csv: String, hullId: String, n: Int): String {
        val m = shardMap(csv).toMutableMap()
        if (n <= 0) m.remove(hullId) else m[hullId] = n
        return m.entries.joinToString(",") { "${it.key}:${it.value}" }
    }

    private fun pickLockedHull(prof: GameProfile, r: Random): String? {
        val owned = prof.unlockedSkinsCsv.split(",").toSet()
        val locked = HULL_SHARDS.keys.filter { it !in owned }
        if (locked.isEmpty()) return null
        var roll = r.nextInt(locked.sumOf { HULL_WEIGHT[it] ?: 1 })
        for (id in locked) {
            roll -= HULL_WEIGHT[id] ?: 1
            if (roll < 0) return id
        }
        return locked.first()
    }

    /**
     * Login-streak bonus: [count] shards for a random locked hull.
     * Returns (updated profile, hull that got the shards or null if every hull is owned,
     * hull id if these shards completed it). If everything is owned, shards become gems.
     */
    fun grantShards(prof: GameProfile, count: Int, r: Random = Random.Default): Triple<GameProfile, String?, String?> {
        val hullId = pickLockedHull(prof, r)
        if (hullId == null) {
            val g = count * 2
            return Triple(prof.copy(gems = prof.gems + g, totalGemsEarned = prof.totalGemsEarned + g), null, null)
        }
        val total = shards(prof.crateShardsCsv, hullId) + count
        val need = HULL_SHARDS.getValue(hullId)
        return if (total >= need) {
            val skins = (prof.unlockedSkinsCsv.split(",").filter { it.isNotEmpty() } + hullId).joinToString(",")
            Triple(prof.copy(crateShardsCsv = withShards(prof.crateShardsCsv, hullId, 0), unlockedSkinsCsv = skins), hullId, hullId)
        } else {
            Triple(prof.copy(crateShardsCsv = withShards(prof.crateShardsCsv, hullId, total)), hullId, null)
        }
    }

    /** Rolls the prize and returns the updated profile + what to show. Call inside updateProfile. */
    fun roll(prof: GameProfile, isPro: Boolean, r: Random = Random.Default): Pair<GameProfile, CrateResult> {
        val opened = cratesOpenedToday(prof)
        // Streak counts the first crate of each day; the Pro bonus crate doesn't change it.
        val streak = when {
            opened > 0 -> prof.crateStreak
            prof.lastCrateDate == yesterday() -> prof.crateStreak + 1
            else -> 1
        }
        val isBonus = opened == 0 && streak > 0 && streak % 7 == 0

        var gems = 0
        var shards = 0
        var message: String? = null
        when {
            isBonus -> { gems = 10; shards = 5; message = "🔥 $streak-DAY CRATE STREAK BONUS!" }
            else -> {
                val p = r.nextInt(100)
                when {
                    p < 40 -> gems = r.nextInt(2, 7)
                    p < 75 -> shards = r.nextInt(1, 4)
                    else -> { gems = 1; message = QUOTES[r.nextInt(QUOTES.size)] }
                }
            }
        }

        var hullId: String? = null
        var unlocked: String? = null
        var newCsv = prof.crateShardsCsv
        var newSkins = prof.unlockedSkinsCsv
        if (shards > 0) {
            hullId = pickLockedHull(prof, r)
            if (hullId == null) {
                // Every hull already owned: turn shards into gems instead.
                gems += shards; shards = 0
                message = (message ?: "") + " All hulls owned: shards became gems."
            } else {
                val total = shards(newCsv, hullId) + shards
                val need = HULL_SHARDS.getValue(hullId)
                if (total >= need) {
                    unlocked = hullId
                    newCsv = withShards(newCsv, hullId, 0)
                    newSkins = (newSkins.split(",").filter { it.isNotEmpty() } + hullId).joinToString(",")
                } else {
                    newCsv = withShards(newCsv, hullId, total)
                }
            }
        }

        val updated = prof.copy(
            gems = prof.gems + gems,
            totalGemsEarned = prof.totalGemsEarned + gems,
            crateShardsCsv = newCsv,
            unlockedSkinsCsv = newSkins,
            lastCrateDate = today(),
            cratesToday = opened + 1,
            crateStreak = streak
        )
        return updated to CrateResult(gems, hullId, shards, unlocked, message?.trim(), streak, isBonus)
    }
}
