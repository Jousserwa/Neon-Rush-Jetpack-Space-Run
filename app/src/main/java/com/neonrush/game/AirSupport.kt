package com.neonrush.game

import android.content.Context
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

/**
 * AIR SUPPORT (Stage 145): a Pro-only backup call. A helicopter gunship (or, once unlocked, a jet) answers a radio call, sweeps the whole screen
 * for about 3 seconds, clears obstacles and the boss brood with its guns and fires two missiles at the boss, then flies off.
 *  - HARD: 40% strength.  LEGENDARY: full strength.  EASY / MEDIUM: not available.  Free players only see a PRO preview.
 *  - The call opens after you have been hit once (or a shot of yours bounced off the boss shield).
 *    It also opens when you are taking a lot of hits in a row (intensive attack): then the guns clear the screen but no missiles are fired.
 *  - Per run: at most 2 sorties, 20 s apart. Pro gets 1 free call every day; more calls come in packs (consumable, ids below).
 *  - Calls flown here never block the Legendary leaderboard: players get an AIR SUPPORT ribbon and badge instead.
 */
object AirSupport {
    class Pack(val id: String, val calls: Int, val price: String, val tag: String)
    // Play Console: five CONSUMABLE products with exactly these ids.
    val PACKS = listOf(
        Pack("neonrush_air_2", 2, "$0.99", "SCRAMBLE"),
        Pack("neonrush_air_5", 5, "$1.99", "SQUADRON"),
        Pack("neonrush_air_10", 10, "$3.99", "WING"),
        Pack("neonrush_air_15", 15, "$4.99", "TASK FORCE"),
        Pack("neonrush_air_50", 50, "$9.99", "AIR COMMAND")
    )
    const val SORTIES_PER_RUN = 2
    const val COOLDOWN_MS = 20_000L
    const val JET_UNLOCK_SORTIES = 20          // or buy a 15+ pack

    var version by mutableStateOf(0)
    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences("neonrush_air", Context.MODE_PRIVATE)
    private fun today(): Long = ServerClock.bestNowMs() / 86_400_000L

    // ---------- stock ----------
    fun calls(ctx: Context): Int { @Suppress("UNUSED_VARIABLE") val v = version; return try { prefs(ctx).getInt("calls", 0) } catch (_: Throwable) { 0 } }
    fun freeAvailable(ctx: Context, pro: Boolean): Boolean { @Suppress("UNUSED_VARIABLE") val v = version; return pro && (try { prefs(ctx).getLong("freeDay", -1L) != today() } catch (_: Throwable) { false }) }
    fun totalSorties(ctx: Context): Int { @Suppress("UNUSED_VARIABLE") val v = version; return try { prefs(ctx).getInt("total", 0) } catch (_: Throwable) { 0 } }
    fun addCalls(ctx: Context, n: Int) {
        try { val sp = prefs(ctx); val ed = sp.edit().putInt("calls", sp.getInt("calls", 0) + n); if (n >= 15) ed.putBoolean("jet", true); ed.apply() } catch (_: Throwable) { }
        version += 1
    }
    fun jetUnlocked(ctx: Context): Boolean {
        @Suppress("UNUSED_VARIABLE") val v = version
        val bought = try { prefs(ctx).getBoolean("jet", false) } catch (_: Throwable) { false }
        return bought || totalSorties(ctx) >= JET_UNLOCK_SORTIES
    }
    fun aircraft(ctx: Context): String { @Suppress("UNUSED_VARIABLE") val v = version; val a = try { prefs(ctx).getString("craft", "heli") ?: "heli" } catch (_: Throwable) { "heli" }; return if (a == "jet" && jetUnlocked(ctx)) "jet" else "heli" }
    fun selectAircraft(ctx: Context, id: String) { if (id == "heli" || jetUnlocked(ctx)) { try { prefs(ctx).edit().putString("craft", id).apply() } catch (_: Throwable) { }; version += 1 } }

    // ---------- ribbon / badge level from lifetime sorties ----------
    fun level(total: Int): Int = when { total >= 30 -> 3; total >= 10 -> 2; total >= 1 -> 1; else -> 0 }
    /** Badge ids for the leaderboard (read back by the ribbon): "air1".."air3" or empty. */
    fun badgeCsv(ctx: Context): String { val l = level(totalSorties(ctx)); return if (l > 0) "air$l" else "" }
    fun levelName(l: Int) = when (l) { 3 -> "AIR ACE"; 2 -> "STRIKE LEADER"; 1 -> "WINGMAN"; else -> "" }
    fun levelColor(l: Int): Long = when (l) { 3 -> 0xFFFFC21A; 2 -> 0xFFB0BEC5; 1 -> 0xFFCD7F32; else -> 0xFF607D8B }

    // ---------- run state ----------
    /** 0 idle, 1 radio call, 2 aircraft over the screen. The pilot is covered (invulnerable) in 1 and 2. */
    @Volatile var phase = 0
    @Volatile var sorties = 0
    @Volatile var fails = 0
    @Volatile var intense = false
    @Volatile var readyAtMs = 0L
    @Volatile var teaserShown = false
    fun covering(): Boolean = phase != 0
    fun newRun() { phase = 0; sorties = 0; fails = 0; intense = false; readyAtMs = 0L; teaserShown = false }
    fun modeOk(tierName: String) = tierName == "HARD" || tierName == "LEGENDARY"
    fun strength(tierName: String) = if (tierName == "LEGENDARY") 1f else 0.4f
    @Suppress("UNUSED_PARAMETER") private fun failsNeeded(tierName: String) = 1   // one hit on you (or one shot bounced off the shield) is enough
    /** The call button is offered: you were hit once while a boss is on screen, or a heavy attack is on. */
    fun offered(tierName: String, bossActive: Boolean): Boolean = modeOk(tierName) && ((bossActive && fails >= failsNeeded(tierName)) || intense)
    fun cooldownLeftMs(): Long = (readyAtMs - System.currentTimeMillis()).coerceAtLeast(0L)
    fun canCallNow(): Boolean = phase == 0 && sorties < SORTIES_PER_RUN && cooldownLeftMs() == 0L

    /** Pays for one call (the free daily one first). Returns false when none is left. [testing] flies for free. */
    fun pay(ctx: Context, pro: Boolean, testing: Boolean): Boolean {
        if (!canCallNow()) return false
        if (!testing) {
            val sp = prefs(ctx)
            if (freeAvailable(ctx, pro)) { try { sp.edit().putLong("freeDay", today()).apply() } catch (_: Throwable) { } }
            else { val c = calls(ctx); if (c <= 0) return false; try { sp.edit().putInt("calls", c - 1).apply() } catch (_: Throwable) { } }
        }
        try { val sp = prefs(ctx); sp.edit().putInt("total", sp.getInt("total", 0) + 1).apply() } catch (_: Throwable) { }
        sorties += 1; version += 1
        return true
    }
    fun finishSortie() { phase = 0; readyAtMs = System.currentTimeMillis() + COOLDOWN_MS; intense = false; version += 1 }

    // ---------- voices: real recordings if present (res/raw air_*.ogg), else the phone's own text-to-speech through a radio effect ----------
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var player: MediaPlayer? = null
    private fun ensureTts(ctx: Context) {
        if (tts != null) return
        try { tts = TextToSpeech(ctx.applicationContext) { st -> if (st == TextToSpeech.SUCCESS) { ttsReady = true; try { tts?.language = Locale.US } catch (_: Throwable) { } } } } catch (_: Throwable) { }
    }
    /** Speaks one radio line. [key] looks for res/raw/air_<key>; [role] 0 = pilot (NEON ONE), 1 = air control / aircraft. */
    fun say(ctx: Context, key: String, text: String, role: Int) {
        try {
            val id = ctx.resources.getIdentifier("air_$key", "raw", ctx.packageName)
            if (id != 0) { player?.release(); player = MediaPlayer.create(ctx.applicationContext, id)?.also { it.setOnCompletionListener { m -> m.release() }; it.start() }; return }
        } catch (_: Throwable) { }
        ensureTts(ctx)
        try {
            val t = tts ?: return
            if (!ttsReady) return
            t.setPitch(if (role == 0) 0.85f else 0.7f); t.setSpeechRate(if (role == 0) 1.12f else 1.0f)
            t.speak(text, TextToSpeech.QUEUE_FLUSH, null, "air_$key")
        } catch (_: Throwable) { }
    }
    fun stopVoice() { try { tts?.stop() } catch (_: Throwable) { } }
}
