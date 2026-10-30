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
 * clears obstacles and the boss brood with its guns and fires missiles at the boss, then flies off. Stage 150: SEVEN call types (see CALLS) on an escalating ladder.
 *  - MEDIUM 20% strength (calls 1-2), HARD 40% (calls 1-5), LEGENDARY full (calls 1-7).  EASY: not available.  Free players only see a PRO preview.
 *  - The call opens after you have been hit once (or a shot of yours bounced off the boss shield).
 *    It also opens when you are taking a lot of hits in a row (intensive attack): then the guns clear the screen but no missiles are fired.
 *  - Price of a call = its base strikes + HEAT (+1 per call already made this round, max +3). Each type once per round, 20 s between calls. Strikes leave the single stock at once.
 *  - Pro gets 1 free strike every day; more strikes come in packs (consumable, ids below).
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
    const val COOLDOWN_MS = 20_000L
    const val HEAT_MAX = 3                      // extra strikes added to a call when you already called this round (capped)

    /** The seven call types. [chain]: aircraft that fly one after the other (true = jet). [missiles] in total, [dmgEach] = share of the boss hull per missile at full strength. */
    class CallType(val id: Int, val name: String, val tag: String, val base: Int, val chain: List<Boolean>, val missiles: Int, val dmgEach: Float, val wipe: Boolean, val blurb: String)
    val CALLS = listOf(
        CallType(1, "STRAFE RUN", "HELICOPTER", 1, listOf(false), 1, 0.12f, false, "Guns clear the lane. One missile."),
        CallType(2, "MISSILE RUN", "HELICOPTER", 1, listOf(false), 2, 0.12f, false, "Guns, the brood wiped, two missiles."),
        CallType(3, "JET STRIKE", "JET", 2, listOf(true), 3, 0.10f, false, "Fast pass, three missiles."),
        CallType(4, "JOINT STRIKE", "HELI + JET", 4, listOf(false, true), 4, 0.11f, false, "Two aircraft, four missiles."),
        CallType(5, "HEAVY BARRAGE", "HELI + JET", 12, listOf(false, true), 6, 0.10f, false, "Six missiles. Shreds escorts and shields."),
        CallType(6, "AIR ARMADA", "SPECIAL", 20, listOf(true, false, true), 6, 0.09f, true, "Wipes every escort, then hits the main boss."),
        CallType(7, "DOOMSDAY", "SPECIAL", 40, listOf(false, true, true, false), 8, 0.125f, true, "Wipes the escorts and tears the boss apart.")
    )
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
    fun newRun() { phase = 0; sorties = 0; fails = 0; intense = false; readyAtMs = 0L; teaserShown = false; heat = 0; usedMask = 0; usedThisRun = 0; lastRid = -1 }
    fun modeOk(tierName: String) = tierName != "EASY"
    fun strength(tierName: String) = when (tierName) { "LEGENDARY" -> 1f; "HARD" -> 0.4f; else -> 0.2f }
    /** Highest call a plan may use: MEDIUM 1-2, HARD 1-5, LEGENDARY 1-7. */
    fun maxCall(tierName: String): Int = when (tierName) { "LEGENDARY" -> 7; "HARD" -> 5; "MEDIUM" -> 2; else -> 0 }
    fun planNeeded(id: Int): String = if (id <= 2) "MEDIUM" else if (id <= 5) "HARD" else "LEGENDARY"

    // ---------- the escalating ladder (per boss round) ----------
    @Volatile var heat = 0                 // calls already made in THIS round
    @Volatile var usedMask = 0             // bit (id-1) set = that call type was used in this round
    @Volatile var usedThisRun = 0          // strikes spent in this run
    @Volatile private var lastRid = -1
    @Volatile var lastFail = ""            // why the last pay() said no
    @Volatile var bonusSeq = 0             // a bonus strike was granted (clean round, checkpoint)
    @Volatile var bonusText = ""
    private fun roundId(): Int = Siege.zone * 10 + Siege.round
    /** A new round (or zone) starts a fresh ladder. */
    fun syncRound() { val r = roundId(); if (r != lastRid) { lastRid = r; heat = 0; usedMask = 0; version += 1 } }
    fun surcharge(): Int { syncRound(); return minOf(heat, HEAT_MAX) }
    fun cost(id: Int): Int { syncRound(); return CALLS[id - 1].base + minOf(heat, HEAT_MAX) }
    fun usedThisRound(id: Int): Boolean { syncRound(); return (usedMask shr (id - 1)) and 1 == 1 }
    /** null = this call can be placed now; otherwise the short reason shown on the menu row. */
    fun lockedReason(id: Int, tierName: String, pro: Boolean): String? {
        if (!pro) return "PRO"
        if (id > maxCall(tierName)) return "NEEDS ${planNeeded(id)}"
        if (id == 6 && Fleet.total < 4) return "NEEDS 4 BOSSES"
        if (id == 7 && Fleet.total < 5) return "NEEDS 5 BOSSES"
        if (usedThisRound(id)) return "USED THIS ROUND"
        return null
    }
    /** The aircraft that fly a call. A jet leg is flown by the helicopter until the jet is unlocked. */
    fun chainFor(ctx: Context, id: Int): List<Boolean> { val jetOk = jetUnlocked(ctx); return CALLS[id - 1].chain.map { it && jetOk } }
    /** Called by the sim when a boss round is destroyed. A round closed without a single call refunds one strike. */
    fun onRoundDestroyed(ctx: Context, eligible: Boolean) {
        if (eligible && lastRid != -1 && heat == 0) grant(ctx, 1, "CLEAN ROUND · +1 STRIKE")
    }
    fun grant(ctx: Context, n: Int, text: String) {
        addCalls(ctx, n); bonusText = text; bonusSeq += 1
    }
    @Suppress("UNUSED_PARAMETER") private fun failsNeeded(tierName: String) = 1   // one hit on you (or one shot bounced off the shield) is enough
    /** The call button is offered: you were hit once while a boss is on screen, or a heavy attack is on. */
    fun offered(tierName: String, bossActive: Boolean): Boolean = modeOk(tierName) && ((bossActive && fails >= failsNeeded(tierName)) || intense)
    fun cooldownLeftMs(): Long = (readyAtMs - System.currentTimeMillis()).coerceAtLeast(0L)
    fun canCallNow(): Boolean = phase == 0 && cooldownLeftMs() == 0L

    /** Pays for call [id] and removes the strikes from the stock AT ONCE (written to storage right now). The free daily strike goes first. Returns false (and sets [lastFail]) when it can't be placed. */
    fun pay(ctx: Context, pro: Boolean, testing: Boolean, tierName: String, id: Int): Boolean {
        lastFail = ""
        if (phase != 0) { lastFail = "A call is already in progress."; return false }
        if (cooldownLeftMs() > 0L) { lastFail = "The aircraft are still rearming."; return false }
        val why = lockedReason(id, tierName, pro)
        if (why != null) { lastFail = "Not available: $why"; return false }
        val cost = cost(id)
        if (!testing) {
            val sp = prefs(ctx)
            val free = if (freeAvailable(ctx, pro)) 1 else 0
            val stock = calls(ctx)
            if (stock + free < cost) { lastFail = "Not enough strikes ($cost needed). Get more in the Air Support Bay on the home screen."; return false }
            val ed = sp.edit()
            var rest = cost
            if (free > 0) { ed.putLong("freeDay", today()); rest -= 1 }
            ed.putInt("calls", stock - rest)
            ed.putInt("total", sp.getInt("total", 0) + 1)
            try { ed.commit() } catch (_: Throwable) { ed.apply() }
        } else { try { val sp = prefs(ctx); sp.edit().putInt("total", sp.getInt("total", 0) + 1).commit() } catch (_: Throwable) { } }
        heat += 1; usedMask = usedMask or (1 shl (id - 1)); usedThisRun += cost
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
