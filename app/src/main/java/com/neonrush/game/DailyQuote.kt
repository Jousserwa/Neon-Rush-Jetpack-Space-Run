package com.neonrush.game

import android.content.Context
import java.util.Calendar

/**
 * DAILY ENCOURAGEMENT: one short line per day, picked by the calendar date (same line for everyone that day).
 * Lines live in res/raw/quotes.txt, ONE PER LINE. A line may start with [win] or [loss] to be used only after a good / bad run;
 * untagged lines are the general daily lines. Empty lines and lines starting with # are ignored. Add as many as you like, no code change.
 */
object DailyQuote {
    private var general: List<String> = emptyList()
    private var wins: List<String> = emptyList()
    private var losses: List<String> = emptyList()
    private var loaded = false

    private fun load(ctx: Context) {
        if (loaded) return
        loaded = true
        try {
            val lines = ctx.applicationContext.resources.openRawResource(R.raw.quotes).bufferedReader(Charsets.UTF_8).readLines()
                .map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
            general = lines.filter { !it.startsWith("[") }.distinct()
            wins = lines.filter { it.startsWith("[win]") }.map { it.removePrefix("[win]").trim() }.distinct()
            losses = lines.filter { it.startsWith("[loss]") }.map { it.removePrefix("[loss]").trim() }.distinct()
        } catch (_: Throwable) { }
    }

    private fun dayNum(): Int {
        val c = Calendar.getInstance()
        return c.get(Calendar.YEAR) * 366 + c.get(Calendar.DAY_OF_YEAR)
    }

    /** mode: "day" (home), "win" or "loss" (game over). Falls back to the general list, then to a safe default. */
    fun today(ctx: Context, mode: String = "day"): String {
        load(ctx)
        val pool = when (mode) { "win" -> wins.ifEmpty { general }; "loss" -> losses.ifEmpty { general }; else -> general }
        if (pool.isEmpty()) return "You can make it!"
        // after a run the pick also changes with the run count so two runs in a day do not show the same line
        val salt = if (mode == "day") 0 else (System.currentTimeMillis() / 60_000L).toInt()
        return pool[Math.floorMod(dayNum() * 31 + salt, pool.size)]
    }
}
