package com.neonrush.game

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Calendar

/**
 * LIVE EVENTS: no server needed. The calendar is worked out from the date on the phone:
 *  - a WEEKLY event every Monday-to-Monday, rotating through 8 themes (each with its own goal type and colour),
 *  - HOLIDAY seasons (Valentine, Halloween, Christmas, New Year) that take over the week while they last, with bigger goals and rewards.
 * Each event has 3 reward tiers (gems; the top tier also gives an event medal). Progress is counted from finished runs.
 * Reminders (new event, "ends tonight", crate ready, comeback after 3 and 7 days away) go through DropNotifier.
 * Remote Config can scale all goals (event_goal_pct) and show a banner line (event_banner_text).
 */
object LiveEvents {
    enum class Metric { METERS, SCORE, RUNS, ZONE }
    class Tier(val goal: Int, val gems: Int)
    class Event(
        val key: String, val title: String, val emoji: String, val blurb: String, val metric: Metric,
        val tiers: List<Tier>, val startMs: Long, val endMs: Long, val holiday: Boolean, val accent: Long
    ) {
        fun goalText(goal: Int): String = when (metric) {
            Metric.METERS -> "Fly $goal m"
            Metric.SCORE -> "Score $goal"
            Metric.RUNS -> "Play $goal runs"
            Metric.ZONE -> "Reach zone $goal"
        }
    }
    class State(val event: Event, val progress: Int, val claimed: Int, val medals: Int)

    private class Theme(val title: String, val emoji: String, val blurb: String, val metric: Metric, val accent: Long)
    private val THEMES = listOf(
        Theme("METEOR MARATHON", "☄️", "Fly farther than ever. Every metre counts all week.", Metric.METERS, 0xFFFF7043),
        Theme("SCORE STORM", "⚡", "Stack up points run after run.", Metric.SCORE, 0xFFFFD740),
        Theme("SQUADRON SPRINT", "🚀", "Launch again and again. Short runs count too.", Metric.RUNS, 0xFF40C4FF),
        Theme("DEEP DIVE", "🌌", "Push your deepest zone this week.", Metric.ZONE, 0xFFB388FF),
        Theme("NEON FRENZY", "💠", "Burn distance in the neon lanes.", Metric.METERS, 0xFF00E5FF),
        Theme("HIGH ROLLER", "💎", "Big scores earn big rewards.", Metric.SCORE, 0xFFFF4081),
        Theme("IRON PILOT", "🛡️", "Prove you never quit: play the runs.", Metric.RUNS, 0xFF69F0AE),
        Theme("ABYSS RUN", "🌀", "How deep can you go before the week ends?", Metric.ZONE, 0xFF7C4DFF)
    )

    private class Holiday(val title: String, val emoji: String, val blurb: String, val metric: Metric, val accent: Long,
                          val m0: Int, val d0: Int, val m1: Int, val d1: Int)   // ends at midnight starting m1/d1 (exclusive); m1 < m0 means next year
    private val HOLIDAYS = listOf(
        Holiday("NEON HEARTS", "💘", "Valentine season: score big for someone special.", Metric.SCORE, 0xFFFF4081, Calendar.FEBRUARY, 7, Calendar.FEBRUARY, 15),
        Holiday("HAUNTED RUN", "🎃", "Spooky season: survive as many runs as you dare.", Metric.RUNS, 0xFFFF9100, Calendar.OCTOBER, 24, Calendar.NOVEMBER, 3),
        Holiday("FROST FESTIVAL", "🎄", "Winter lights: fly far through the festive sky.", Metric.METERS, 0xFF80D8FF, Calendar.DECEMBER, 18, Calendar.JANUARY, 1),
        Holiday("FRESH START", "🎆", "New year, new records: reach deeper zones.", Metric.ZONE, 0xFFFFD740, Calendar.JANUARY, 1, Calendar.JANUARY, 8)
    )

    private fun baseGoals(m: Metric): IntArray = when (m) {
        Metric.METERS -> intArrayOf(1500, 5000, 12000)
        Metric.SCORE -> intArrayOf(2500, 8000, 20000)
        Metric.RUNS -> intArrayOf(5, 12, 25)
        Metric.ZONE -> intArrayOf(15, 35, 60)
    }

    private fun tiers(m: Metric, holiday: Boolean): List<Tier> {
        val pct = RemoteTuning.eventGoalPct()
        val mul = if (holiday) (if (m == Metric.ZONE) 1.3f else 2f) else 1f
        val gems = if (holiday) intArrayOf(40, 90, 200) else intArrayOf(25, 50, 100)
        val g = baseGoals(m)
        return List(3) { Tier(((g[it] * mul * pct / 100f).toInt()).coerceAtLeast(1), gems[it]) }
    }

    private fun midnight(c: Calendar) { c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0) }

    /** The event running at [nowMs] (a holiday if one is on, else this week's). */
    fun current(nowMs: Long): Event {
        val now = Calendar.getInstance().apply { timeInMillis = nowMs }
        val year = now.get(Calendar.YEAR)
        for (y in intArrayOf(year - 1, year)) for (h in HOLIDAYS) {
            val s = Calendar.getInstance().apply { set(y, h.m0, h.d0); midnight(this) }
            val e = Calendar.getInstance().apply { set(if (h.m1 < h.m0) y + 1 else y, h.m1, h.d1); midnight(this) }
            if (nowMs >= s.timeInMillis && nowMs < e.timeInMillis)
                return Event("h$y-${h.title}", h.title, h.emoji, h.blurb, h.metric, tiers(h.metric, true), s.timeInMillis, e.timeInMillis, true, h.accent)
        }
        val s = Calendar.getInstance().apply { timeInMillis = nowMs; firstDayOfWeek = Calendar.MONDAY; set(Calendar.DAY_OF_WEEK, Calendar.MONDAY); midnight(this) }
        val e = Calendar.getInstance().apply { timeInMillis = s.timeInMillis; add(Calendar.DAY_OF_YEAR, 7) }
        val idx = (s.get(Calendar.YEAR) * 53 + s.get(Calendar.DAY_OF_YEAR) / 7) % THEMES.size
        val th = THEMES[idx]
        val key = "w${s.get(Calendar.YEAR)}-${s.get(Calendar.DAY_OF_YEAR)}"
        return Event(key, th.title, th.emoji, th.blurb, th.metric, tiers(th.metric, false), s.timeInMillis, e.timeInMillis, false, th.accent)
    }

    // ------------------------------------------------------------------ progress (SharedPreferences)

    private const val PREFS = "neonrush_events"
    private var appCtx: Context? = null
    private val _state = MutableStateFlow<State?>(null)
    val state: StateFlow<State?> = _state.asStateFlow()

    private fun prefs() = appCtx?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @Synchronized fun refresh() {
        val p = prefs() ?: return
        val ev = current(System.currentTimeMillis())
        if (p.getString("key", "") != ev.key) p.edit().putString("key", ev.key).putInt("prog", 0).putInt("claimed", 0).apply()
        _state.value = State(ev, p.getInt("prog", 0), p.getInt("claimed", 0), p.getInt("medals", 0))
    }

    fun init(ctx: Context) {
        appCtx = ctx.applicationContext
        try { refresh(); scheduleReminders() } catch (_: Throwable) {}
    }

    /** Called when a run is over. */
    @Synchronized fun onRunEnd(zone: Int, score: Int, meters: Int) {
        val p = prefs() ?: return
        refresh()
        val ev = _state.value?.event ?: return
        val old = p.getInt("prog", 0)
        val now = when (ev.metric) {
            Metric.METERS -> old + meters.coerceAtLeast(0)
            Metric.SCORE -> old + score.coerceAtLeast(0)
            Metric.RUNS -> old + 1
            Metric.ZONE -> maxOf(old, zone)
        }
        p.edit().putInt("prog", now.coerceAtMost(2_000_000_000)).apply()
        refresh()
    }

    /** Returns the gems for the tier, or 0 when it is not reachable or already taken. The caller adds the gems to the profile. */
    @Synchronized fun claim(tier: Int): Int {
        val p = prefs() ?: return 0
        refresh()
        val st = _state.value ?: return 0
        val t = st.event.tiers.getOrNull(tier) ?: return 0
        if (st.progress < t.goal || ((st.claimed shr tier) and 1) == 1) return 0
        val edit = p.edit().putInt("claimed", st.claimed or (1 shl tier))
        if (tier == st.event.tiers.lastIndex) edit.putInt("medals", st.medals + 1)
        edit.apply()
        refresh()
        GameAnalytics.eventClaimed(st.event.key, tier)
        return t.gems
    }

    // ------------------------------------------------------------------ reminders

    private fun atHour(ms: Long, hour: Int): Long = Calendar.getInstance().apply {
        timeInMillis = ms; set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    /** Re-plans every reminder. Runs on each app open, so a player who stays away still hears from the game 3 and 7 days later. */
    fun scheduleReminders() {
        val ctx = appCtx ?: return
        val now = System.currentTimeMillis()
        val ev = current(now)
        val next = current(ev.endMs + 1000L)
        val evnew = listOf(DropEntry(next.key, next.title, next.emoji, atHour(next.startMs, 9), false)).filter { it.atMs > now }
        val evend = listOf(DropEntry(ev.key, ev.title, ev.emoji, ev.endMs - 6L * 3600_000L, false)).filter { it.atMs > now }
        val tomorrow = now + 24L * 3600_000L
        val crate = listOf(DropEntry("crate", "crate", "🎁", atHour(tomorrow, 10), false))
        val cb = listOf(
            DropEntry("cb3", ev.title, ev.emoji, atHour(now + 3L * 24 * 3600_000L, 18), false),
            DropEntry("cb7", ev.title, ev.emoji, atHour(now + 7L * 24 * 3600_000L, 18), false)
        )
        try {
            DropNotifier.sync(ctx, evnew, "evnew")
            DropNotifier.sync(ctx, evend, "evend")
            DropNotifier.sync(ctx, crate, "crate")
            DropNotifier.sync(ctx, cb, "comeback")
        } catch (_: Throwable) {}
    }
}
