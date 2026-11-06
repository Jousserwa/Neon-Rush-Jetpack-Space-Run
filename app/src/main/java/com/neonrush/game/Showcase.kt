package com.neonrush.game

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * SHOWCASE (Stage 176): makes sure every part of the game gets seen. The home screen has two doors, HANGAR (how you look and fly: suits, hulls, trails, parts, fuel tank)
 * and ARMORY (everything that shoots or defends). Every door keeps a catalogue of its items; the doors cycle through ALL of them as a ticker, a daily spotlight picks the
 * item that has been seen least, and anything the player has never opened carries a NEW flag until they do.
 */
object Showcase {
    const val HANGAR = 0
    const val ARMORY = 1

    /** target: "h:pilots|ships|trails|upgrades" opens that Hangar page, "a:0..3" opens that Armory tab (0 WEAPONS, 1 DEFENSE, 2 ALLIES, 3 DEPOTS). */
    class Entry(val id: String, val door: Int, val icon: String, val title: String, val line: String, val color: Long, val target: String)

    val ARMORY_TABS = listOf("WEAPONS", "DEFENSE", "ALLIES", "DEPOTS")

    val ENTRIES: List<Entry> = listOf(
        Entry("h_pilot", HANGAR, "🧑‍🚀", "PILOT SUITS", "Change how your pilot looks. Some suits are earned, some are bought.", 0xFF00E5FF, "h:pilots"),
        Entry("h_ship", HANGAR, "🚀", "SHIP HULLS", "Swap your ship's hull. Same speed, brand new style.", 0xFFFF4DD2, "h:ships"),
        Entry("h_trail", HANGAR, "✨", "NEON TRAILS", "Light up the track behind you with a trail that is yours.", 0xFF7DF9FF, "h:trails"),
        Entry("h_parts", HANGAR, "🔧", "PARTS & THRUSTERS", "Upgrade the parts and thrusters of your ship.", 0xFFFFB300, "h:upgrades"),
        Entry("h_fuel", HANGAR, "⛽", "FUEL TANK", "Every tier makes you fly 20% longer before you need to refuel.", 0xFF69F0AE, "h:upgrades"),

        Entry("a_gun", ARMORY, "🔫", "PLASMA GUN", "Mk II to Mk V: bolts that hit hazards and bosses harder.", 0xFFFF4081, "a:0"),
        Entry("a_bomb", ARMORY, "💣", "BROOD BOMB", "One tap wipes out the small boss spawns.", 0xFFFFB300, "a:0"),
        Entry("a_mag", ARMORY, "📦", "MAGAZINE BAY", "Carry more shots into every fight.", 0xFF00E5FF, "a:0"),
        Entry("a_boss", ARMORY, "👹", "BOSS THEMES", "Harder bosses for bigger gem rewards.", 0xFFB388FF, "a:0"),
        Entry("a_mil", ARMORY, "🛡", "MILITARY SHIELD", "10 hits plus a 4 second last stand. Switches on by itself.", 0xFFFFC400, "a:1"),
        Entry("a_wing", ARMORY, "🤝", "COMBAT WINGMAN", "Your own fighter: it clears hazards and hits bosses.", 0xFFFFB74D, "a:2"),
        Entry("a_air", ARMORY, "🚁", "AIR SUPPORT", "Helicopter and jet calls that pierce boss shields.", 0xFF69F0AE, "a:2"),
        Entry("a_ammo", ARMORY, "🎯", "AMMO DEPOT", "Bought shots fill your magazine when a run starts.", 0xFF00E5FF, "a:3"),
        Entry("a_bombs", ARMORY, "💥", "BOMB DEPOT", "Store bombs without touching what you collect.", 0xFFFF7043, "a:3"),
        Entry("a_vouch", ARMORY, "🎟", "SCORE VOUCHERS", "Turn big scores into bombs, airstrikes and shields.", 0xFFFFD23F, "a:3")
    )

    /** Hangar sub page the Hangar tab should open on next (set by a door or the spotlight, read once by the tab). */
    var hangarSub: String? = null
    fun takeHangarSub(): String? { val s = hangarSub; hangarSub = null; return s }

    var version by mutableStateOf(0)
    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences("neonrush_showcase", Context.MODE_PRIVATE)
    private fun nowMs(): Long = try { ServerClock.bestNowMs() } catch (_: Throwable) { System.currentTimeMillis() }
    private fun day(): Long { val t = nowMs(); return (t + java.util.TimeZone.getDefault().getOffset(t)) / 86_400_000L }

    fun entries(door: Int): List<Entry> = ENTRIES.filter { it.door == door }
    fun opened(ctx: Context, id: String): Boolean { @Suppress("UNUSED_VARIABLE") val v = version; return try { prefs(ctx).getBoolean("open_$id", false) } catch (_: Throwable) { true } }
    fun markOpened(ctx: Context, id: String) {
        if (opened(ctx, id)) return
        try { prefs(ctx).edit().putBoolean("open_$id", true).apply() } catch (_: Throwable) {}
        version += 1
    }
    /** The player opened the page that holds these items (a Hangar page or an Armory tab). */
    fun markTarget(ctx: Context, target: String) { ENTRIES.filter { it.target == target }.forEach { markOpened(ctx, it.id) } }
    /** Items of this door the player has never opened: shown as the NEW badge on the door. */
    fun unopened(ctx: Context, door: Int): Int = entries(door).count { !opened(ctx, it.id) }
    fun unopenedTab(ctx: Context, tab: Int): Int = ENTRIES.count { it.target == "a:$tab" && !opened(ctx, it.id) }

    /** The daily spotlight for a door: the item that has waited longest (never opened first). Stays the same all day. */
    fun pick(ctx: Context, door: Int): Entry {
        @Suppress("UNUSED_VARIABLE") val v = version
        val list = entries(door)
        val today = day()
        try {
            val sp = prefs(ctx)
            val id = sp.getString("pick_$door", null); val d = sp.getLong("pickDay_$door", -1L)
            if (id != null && d == today) list.firstOrNull { it.id == id }?.let { return it }
            val best = list.maxByOrNull { e ->
                val shown = sp.getLong("shown_${e.id}", -1L)
                (if (!opened(ctx, e.id)) 1000L else 0L) + (if (shown < 0L) 500L else (today - shown) * 10L)
            } ?: list.first()
            sp.edit().putString("pick_$door", best.id).putLong("pickDay_$door", today).putLong("shown_${best.id}", today).apply()
            return best
        } catch (_: Throwable) { return list.first() }
    }
}
