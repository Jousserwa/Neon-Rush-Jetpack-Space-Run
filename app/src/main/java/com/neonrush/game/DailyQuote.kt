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
    private val BUILT_IN = listOf("You can make it!", "Today is your day!", "Don't give up!", "You are not alone!", "It's hard, but it's possible!", "[loss]Crashing is part of flying. Get back up!", "[win]Look at you go! That's your best yet!")

    private fun load(ctx: Context) {
        if (loaded) return
        loaded = true
        try {
            // looked up by name, so the build never fails if res/raw/quotes.txt has not been added yet
            val res = ctx.applicationContext.resources
            val id = res.getIdentifier("quotes", "raw", ctx.applicationContext.packageName)
            val lines = (if (id != 0) res.openRawResource(id).bufferedReader(Charsets.UTF_8).readLines() else BUILT_IN)
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

    /** FREE players only see part of the home quote on every 3rd day of the month; Pro always reads the whole line. */
    fun isTeaserDay(): Boolean = Calendar.getInstance().get(Calendar.DAY_OF_MONTH) % 3 == 0

    /** The first ~55% of the words, then an ellipsis. */
    fun teaser(text: String): String {
        val words = text.split(" ")
        if (words.size < 4) return text
        val keep = Math.ceil(words.size * 0.55).toInt().coerceIn(2, words.size - 1)
        return words.take(keep).joinToString(" ") + " \u2026"
    }

    private const val BLOCK = 15          // quotes used per month
    private const val BASE_MONTH = 2026 * 12 + 9   // October 2026 = first block

    /** mode: "day" (home) or "win" / "loss" (game over). Home: this month's block of 15 quotes, one a day in a month-seeded random order. */
    fun today(ctx: Context, mode: String = "day"): String {
        load(ctx)
        if (mode == "win" || mode == "loss") {
            val pool = (if (mode == "win") wins else losses).ifEmpty { general }
            if (pool.isEmpty()) return "You can make it!"
            return pool[Math.floorMod((System.currentTimeMillis() / 60_000L).toInt() + dayNum(), pool.size)]
        }
        if (general.isEmpty()) return "You can make it!"
        val c = Calendar.getInstance()
        val monthIdx = (c.get(Calendar.YEAR) * 12 + c.get(Calendar.MONTH)) - BASE_MONTH
        val blocks = ((general.size + BLOCK - 1) / BLOCK).coerceAtLeast(1)
        val start = Math.floorMod(monthIdx, blocks) * BLOCK
        val block = general.subList(start, minOf(start + BLOCK, general.size))
        // shuffle the block with a seed from the month, so every player sees the same order and each quote shows once before any repeats
        val order = block.indices.toMutableList().also { java.util.Collections.shuffle(it, java.util.Random(monthIdx * 7919L + 13L)) }
        return block[order[(c.get(Calendar.DAY_OF_MONTH) - 1) % block.size]]
    }}
