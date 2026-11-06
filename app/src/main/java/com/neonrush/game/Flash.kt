package com.neonrush.game

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * FLASH (Stage 175): the game's advisor. It remembers how your last runs ended and, at most ONE tip at a time, points at the single thing that would
 * help most: a free or gem fix first, a real-money item only when it clearly fixes what keeps happening, and when the best item is still locked it shows
 * how close the unlock is. It only speaks on the home screen, waits 24 hours on a tip you dismiss, and stays off money tips on a day you already bought something.
 */
object Flash {
    class Ev(val label: String, val value: Float)
    class Tip(
        val id: String, val icon: String, val head: String, val line: String,
        val evidence: List<Ev> = emptyList(), val preview: String = "",
        val target: String, val cta: String, val color: Long,
        val lock: String? = null, val lockPct: Float = 0f
    )

    var version by mutableStateOf(0)
    private const val KEEP = 5
    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences("neonrush_flash", Context.MODE_PRIVATE)
    private fun nowMs(): Long = try { ServerClock.bestNowMs() } catch (_: Throwable) { System.currentTimeMillis() }

    /** One finished run: zone, ran out of fuel, ended in a boss fight, magazine empty during that boss fight. Same seed twice = ignored. */
    fun record(ctx: Context, seed: Long, zone: Int, fuelOut: Boolean, boss: Boolean, ammoEmpty: Boolean) {
        try {
            val sp = prefs(ctx)
            if (sp.getLong("seed", -1L) == seed && seed != 0L) return
            val old = sp.getString("runs", "") ?: ""
            val add = "$zone,${if (fuelOut) 1 else 0},${if (boss) 1 else 0},${if (ammoEmpty) 1 else 0}"
            val all = (old.split(";").filter { it.isNotBlank() } + add).takeLast(KEEP)
            sp.edit().putString("runs", all.joinToString(";")).putLong("seed", seed).apply()
        } catch (_: Throwable) {}
        version += 1
    }
    private class R(val zone: Int, val fuel: Boolean, val boss: Boolean, val ammo: Boolean)
    private fun runs(ctx: Context): List<R> = try {
        (prefs(ctx).getString("runs", "") ?: "").split(";").filter { it.isNotBlank() }.mapNotNull {
            val p = it.split(","); if (p.size < 4) null else R(p[0].toIntOrNull() ?: 0, p[1] == "1", p[2] == "1", p[3] == "1")
        }
    } catch (_: Throwable) { emptyList() }

    fun dismiss(ctx: Context, id: String) { try { prefs(ctx).edit().putLong("d_$id", nowMs()).apply() } catch (_: Throwable) {}; version += 1 }
    private fun muted(ctx: Context, id: String): Boolean = try { nowMs() - prefs(ctx).getLong("d_$id", 0L) < 24L * 3600_000L } catch (_: Throwable) { false }

    private fun targetFor(id: String): String = when {
        id.startsWith("neonrush_wing") || id.startsWith("neonrush_warms_") || id.startsWith("neonrush_wskin_") -> "wingman"
        id.startsWith("neonrush_air_") || id.startsWith("neonrush_hskin_") || id.startsWith("neonrush_jskin_") -> "air"
        id.startsWith("neonrush_mag_") -> "magbay"
        id.startsWith("neonrush_bskin_") -> "bosstheme"
        else -> "upgrades"
    }

    /** The tip to show now, or null when there is nothing worth saying. */
    fun best(ctx: Context, gems: Int, bestZone: Int, fuelTiers: Int, testing: Boolean): Tip? {
        val raw = bestRaw(ctx, gems, bestZone, fuelTiers, testing)
        // BALANCE (Stage 176): urgent tips (wish, fuel, boss, mil) always win. The soft shooting tips (ammo, next unlock) and the quiet days give way to a
        // HANGAR tip every other day, so suits, hulls, trails and parts get airtime too.
        val soft = raw == null || raw.id == "ammo" || raw.id.startsWith("next_") || raw.id == "earn"
        if (!soft) return raw
        val day = (nowMs() + java.util.TimeZone.getDefault().getOffset(nowMs())) / 86_400_000L
        if (raw != null && day % 2L == 0L) return raw
        val e = Showcase.pick(ctx, Showcase.HANGAR)
        if (muted(ctx, "shw_hangar")) return raw
        return Tip("shw_hangar", e.icon, "FROM THE HANGAR", e.title + ": " + e.line, preview = "LOOKS & FLIGHT", target = "hangar:" + e.target.substring(2), cta = "OPEN HANGAR", color = e.color)
    }

    private fun bestRaw(ctx: Context, gems: Int, bestZone: Int, fuelTiers: Int, testing: Boolean): Tip? {
        @Suppress("UNUSED_VARIABLE") val v = version
        val rs = runs(ctx)
        val last3 = rs.takeLast(3); val last4 = rs.takeLast(4)
        val moneyOk = testing || !PurchaseGate.boughtToday(ctx)
        fun ok(id: String) = !muted(ctx, id)

        // 0. something the player saved when the cooling-off screen stopped them
        PurchaseGate.wish(ctx)?.let { w ->
            if (moneyOk && ok("wish")) return Tip("wish", "⏰", "READY WHEN YOU ARE", "You saved ${PurchaseGate.label(w)} yesterday. The purchase lock is open today.",
                target = targetFor(w), cta = "TAKE ME THERE", color = 0xFF7DF9FF)
        }
        // 1. fuel keeps running out
        val fuelOut = last3.count { it.fuel }
        if (moneyOk && fuelTiers < 5 && last3.size >= 2 && fuelOut >= 2 && ok("fuel")) {
            val now = fuelTiers * 20
            return Tip("fuel", "⛽", "YOU KEEP RUNNING DRY", "Your tank ran out in $fuelOut of your last ${last3.size} runs. One more FUEL TANK tier lets you fly longer before you have to refuel.",
                evidence = listOf(Ev("OUT OF FUEL", fuelOut / last3.size.toFloat()), Ev("TANK", fuelTiers / 5f)),
                preview = "RUN LONGER  +$now% → +${now + 20}%", target = "hangar", cta = "OPEN FUEL TANK", color = 0xFF69F0AE)
        }
        // 2. bosses keep winning
        val bossDeaths = last3.count { it.boss }
        if (last3.size >= 2 && bossDeaths >= 2) {
            // weapon upgrade first, if there is a next Mk
            for (track in intArrayOf(NrUpgrades.GUN, NrUpgrades.BOMB)) {
                val lvl = NrUpgrades.level(ctx, track); val step = lvl + 1
                if (step > NrUpgrades.TRACKS[track].steps || !ok("boss_$track")) continue
                val info = NrUpgrades.lockInfo(ctx, track, step)
                if (info == null && !moneyOk) continue
                val nm = NrUpgrades.TRACKS[track].name; val lab = NrUpgrades.TRACKS[track].labels[step - 1]
                return Tip("boss_$track", if (track == NrUpgrades.GUN) "🔫" else "💣", "BOSSES ARE BEATING YOU", "A boss ended $bossDeaths of your last ${last3.size} runs. $nm $lab hits bosses harder.",
                    evidence = listOf(Ev("BOSS DEATHS", bossDeaths / last3.size.toFloat()), Ev(nm, lvl / 4f)),
                    preview = "DAMAGE  ×${"%.2f".format(if (track == NrUpgrades.GUN) listOf(1f, 1.12f, 1.25f, 1.4f, 1.6f)[lvl] else listOf(1f, 1.2f, 1.4f, 1.7f, 2f)[lvl])} → ×${"%.2f".format(if (track == NrUpgrades.GUN) listOf(1f, 1.12f, 1.25f, 1.4f, 1.6f)[lvl + 1] else listOf(1f, 1.2f, 1.4f, 1.7f, 2f)[lvl + 1])}",
                    target = "upgrades", cta = "OPEN UPGRADES", color = 0xFFFF4081, lock = info?.first, lockPct = info?.second ?: 0f)
            }
            if (MilShield.stock(ctx) == 0 && ok("boss_mil")) {
                return Tip("boss_mil", "🛡", "SOFTEN THE HITS", "A boss ended $bossDeaths of your last ${last3.size} runs and your Military Shield stock is empty. 5 shields cost ${MilShield.PACK_GEMS} gems and each takes ${MilShield.MAX_HP} hits plus a 4 second last stand.",
                    evidence = listOf(Ev("BOSS DEATHS", bossDeaths / last3.size.toFloat()), Ev("SHIELD STOCK", 0f)),
                    preview = "${MilShield.PACK} SHIELDS  ${MilShield.PACK_GEMS} 💎", target = "milshield", cta = if (gems >= MilShield.PACK_GEMS) "BUY ${MilShield.PACK} · ${MilShield.PACK_GEMS} 💎" else "NEED ${MilShield.PACK_GEMS - gems} MORE 💎",
                    color = 0xFFFFC400, lock = if (gems >= MilShield.PACK_GEMS) null else "Earn gems in Ghost Duel, Storm Run and the Daily Crate", lockPct = (gems / MilShield.PACK_GEMS.toFloat()).coerceIn(0f, 1f))
            }
        }
        // 3. the magazine runs empty
        val dry = last4.count { it.ammo }
        if (moneyOk && last4.size >= 3 && dry >= 3 && ok("ammo")) {
            return Tip("ammo", "🔫", "YOUR GUN KEEPS RUNNING EMPTY", "Your magazine was empty during a boss fight in $dry of your last ${last4.size} runs. A bigger Magazine Bay carries more shots into every fight.",
                evidence = listOf(Ev("EMPTY AT A BOSS", dry / last4.size.toFloat())), preview = "MORE SHOTS PER RUN", target = "magbay", cta = "OPEN MAGAZINE BAY", color = 0xFF00E5FF)
        }
        // 4. short on gems: point at the free ways to earn
        if (gems < 150 && rs.size >= 2 && ok("earn")) {
            return Tip("earn", "💎", "NEED GEMS? EARN THEM", "Your gem pocket is low. Open today's Daily Crate, then try Ghost Duel and Storm Run: both pay gems for free every day.",
                evidence = listOf(Ev("GEMS", (gems / 150f).coerceIn(0f, 1f))), preview = "FREE GEMS TODAY", target = "crate", cta = "OPEN DAILY CRATE", color = 0xFF7C4DFF)
        }
        // 5. otherwise: the next unlock, with its progress ring
        var nextTip: Tip? = null
        for (track in intArrayOf(NrUpgrades.GUN, NrUpgrades.BOMB, NrUpgrades.HELI, NrUpgrades.JET, NrUpgrades.WARMS, NrUpgrades.LANCE)) {
            val step = NrUpgrades.level(ctx, track) + 1
            if (step > NrUpgrades.TRACKS[track].steps) continue
            val info = NrUpgrades.lockInfo(ctx, track, step) ?: continue
            if (!ok("next_$track")) continue
            if (nextTip == null || info.second > nextTip.lockPct) nextTip = Tip("next_$track", "🔓", "NEXT UNLOCK", "${NrUpgrades.TRACKS[track].name} ${NrUpgrades.TRACKS[track].labels[step - 1]} is on its way. Keep flying.",
                preview = NrUpgrades.TRACKS[track].name, target = "none", cta = "KEEP FLYING", color = 0xFFFFD23F, lock = info.first, lockPct = info.second)
        }
        return nextTip
    }
}
