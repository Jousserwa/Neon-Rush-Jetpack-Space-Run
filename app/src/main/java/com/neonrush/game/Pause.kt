package com.neonrush.game

import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * PAUSE (Stage 151). A limited pause per run, not an all-day one:
 *   Free: 2 pauses of up to 40 s · Pro: 3 pauses of up to 60 s · LEGENDARY (any plan, it is the competitive mode): 2 pauses of up to 30 s.
 *  - Not allowed while a boss is fighting (Siege FIGHT / GAP, or any boss in the special worlds) or while an air call is in progress.
 *  - Resuming gives a 3-2-1 countdown (the run stays frozen) and then about 2 s of cover (the pilot cannot be hurt).
 *  - A phone call or leaving the app pauses the run automatically ("system pause"): free, it does not use up a pause.
 * Times use the monotonic clock, so changing the phone's clock cannot stretch a pause.
 */
object Pause {
    const val COUNTDOWN_MS = 3000L
    const val COVER_MS = 2000L
    var paused by mutableStateOf(false)
    var system by mutableStateOf(false)
    var version by mutableStateOf(0)
    @Volatile var maxPauses = 2
    @Volatile var perPauseMs = 40_000L
    @Volatile var used = 0
    @Volatile var startMs = 0L
    @Volatile var resumeAtMs = 0L
    @Volatile var coverUntilMs = 0L

    private fun now() = SystemClock.elapsedRealtime()

    fun configure(tierName: String, isPro: Boolean) {
        if (tierName == "LEGENDARY") { maxPauses = 2; perPauseMs = 30_000L }
        else if (isPro) { maxPauses = 3; perPauseMs = 60_000L }
        else { maxPauses = 2; perPauseMs = 40_000L }
    }
    fun newRun() { paused = false; system = false; used = 0; startMs = 0L; resumeAtMs = 0L; coverUntilMs = 0L; version += 1 }

    fun pausesLeft(): Int = (maxPauses - used).coerceAtLeast(0)
    /** The sim, the overlays' hits and the shooting stand still. */
    fun frozen(): Boolean = paused || now() < resumeAtMs
    fun covering(): Boolean = now() < coverUntilMs
    fun countdown(): Int = if (now() < resumeAtMs) (((resumeAtMs - now()) + 999L) / 1000L).toInt() else 0
    /** null = a pause is allowed; otherwise why not. */
    fun blocked(bossActive: Boolean): String? {
        if (Siege.state == Siege.FIGHT || Siege.state == Siege.GAP || (Siege.special && bossActive)) return "NOT DURING A BOSS FIGHT"
        if (AirSupport.covering()) return "NOT DURING AN AIR CALL"
        if (frozen()) return "ALREADY PAUSED"
        if (pausesLeft() <= 0) return "NO PAUSES LEFT"
        return null
    }
    fun pause(bossActive: Boolean): Boolean {
        if (blocked(bossActive) != null) return false
        used += 1; paused = true; system = false; startMs = now(); version += 1
        return true
    }
    /** Phone call / app left: pauses without using one of the player's pauses. */
    fun systemPause() { if (paused) return; paused = true; system = true; startMs = now(); version += 1 }
    fun remainingMs(): Long = if (system) perPauseMs else (perPauseMs - (now() - startMs)).coerceAtLeast(0L)
    fun resume() {
        if (!paused) return
        paused = false; system = false
        resumeAtMs = now() + COUNTDOWN_MS; coverUntilMs = resumeAtMs + COVER_MS; version += 1
    }
}
