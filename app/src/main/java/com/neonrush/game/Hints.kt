package com.neonrush.game

import kotlin.math.abs

/**
 * Invisible onboarding: contextual, non-blocking prompts that fire the first
 * time a mechanic shows up. No manual, no forced screen.
 *
 * Rules (all timing is REAL time via SystemClock, because slow-mo stretches
 * ticks and tick counts would drift):
 *  - One prompt at a time, with a short cooldown between prompts.
 *  - A prompt is always readable for at least 3 s (even if the player is hit
 *    or the thing passes), and fades at 4 s if the player hasn't acted.
 *    The refuel prompt is the exception: 8 s minimum, fades at 9 s.
 *  - Acting ends it immediately with a quick "✓ Nice!" (or the power-up's
 *    effect) + a sound.
 *  - Slow-mo is half speed (tick delay x2), capped at 2 s, ends on success.
 *  - A lesson shows at most once per run and for at most 3 runs in total
 *    unless completed. Then it never shows again.
 *
 * Persistence: GameProfile.hintsCsv, e.g. "STEER=D,GEM=1,DODGE=2"
 * (D = done, number = times shown). "ALL" = everything done (existing players).
 */
enum class Lesson(
    val id: String,
    val prompt: String,
    val timeoutMs: Long,
    val slowMoMs: Long,
    // Prompt is always readable at least this long, even if the thing passes.
    val minShowMs: Long = 3000L
) {
    STEER("STEER", "Drag up / down to steer", 4000L, 0L),
    GEM("GEM", "Fly through gems 💎", 4000L, 0L),
    FUEL_CELL("FUELCELL", "⛽ Fuel cells refill your tank", 4000L, 0L),
    DODGE("DODGE", "Dodge it!", 4000L, 1500L),
    POWERUP("POWERUP", "Grab it!", 4000L, 1000L),
    // Refuel is the key lesson (and the revenue moment): 8 s minimum, fades at 9 s.
    LOW_FUEL("LOWFUEL", "Tap the fuel button to refuel", 9000L, 0L, 8000L),
    BOSS("BOSS", "Survive until the bar empties", 4000L, 0L);

    companion object {
        fun fromId(id: String?): Lesson? = values().firstOrNull { it.id == id }
    }
}

data class HintState(
    val lesson: String? = null,
    val promptText: String = "",
    val startedAtMs: Long = 0L,
    val timeoutMs: Long = 0L,
    val slowMoUntilMs: Long = 0L,
    val anchorY: Int = 50,
    val targetId: String = "",
    // DODGE only: -1 = move up, +1 = move down, 0 = no arrow
    val arrow: Int = 0,
    // DODGE only: the player got hit; keep the prompt up for its minimum time, no success possible.
    val lost: Boolean = false,
    // Thing the prompt is about (for the highlight ring). targetX < 0 = none.
    val targetX: Float = -1f,
    val targetY: Int = 0,
    val successText: String = "",
    val successUntilMs: Long = 0L,
    val cooldownUntilMs: Long = 0L
)

data class HintInput(
    val nowMs: Long,
    val tick: Int,
    val userY: Int,
    val fuelPercent: Int,
    val bossActive: Boolean,
    val hitThisTick: Boolean,
    val pickedGem: Boolean,
    val pickedFuel: Boolean,
    val pickedPowerupId: String?,
    val refuelTapped: Boolean,
    val elements: List<VisualTrackElement>
)

data class HintResult(
    val state: HintState,
    val shown: Lesson? = null,
    val completed: Lesson? = null,
    val quiet: Boolean = false
)

object HintEngine {
    const val DONE = 99
    private const val MAX_SHOWS = 3          // runs a lesson may be shown in, unless completed
    private const val SUCCESS_MS = 900L
    private const val EFFECT_MS = 2200L
    private const val COOLDOWN_MS = 1500L
    private const val SLOWMO_CAP_MS = 2000L
    private const val LOW_FUEL_PERCENT = 35

    private val GAP_TYPES = setOf(
        "PILLAR_TOP", "PILLAR_BOTTOM", "BARRIER",
        "TUNNEL_TOP", "TUNNEL_BOTTOM", "STALACTITE", "STALAGMITE"
    )

    // ---------- persistence helpers ----------

    fun parse(csv: String): MutableMap<String, Int> {
        val map = mutableMapOf<String, Int>()
        if (csv.trim().equals("ALL", ignoreCase = true)) {
            Lesson.values().forEach { map[it.id] = DONE }
            return map
        }
        csv.split(",").forEach { part ->
            val kv = part.split("=")
            if (kv.size == 2) {
                val v = if (kv[1] == "D") DONE else kv[1].toIntOrNull()
                if (v != null) map[kv[0]] = v
            }
        }
        return map
    }

    fun serialize(map: Map<String, Int>): String =
        map.entries.joinToString(",") { "${it.key}=${if (it.value >= DONE) "D" else it.value.toString()}" }

    private fun eligible(status: Map<String, Int>, l: Lesson, shownThisRun: Set<String>): Boolean {
        if (l.id in shownThisRun) return false
        return (status[l.id] ?: 0) < MAX_SHOWS
    }

    // ---------- per-tick state machine ----------

    private enum class Outcome { CONTINUE, SUCCESS, FAIL, LOST }

    fun step(
        prev: HintState,
        inp: HintInput,
        status: Map<String, Int>,
        shownThisRun: Set<String>
    ): HintResult {
        var s = prev
        val now = inp.nowMs
        if (s.successText.isNotEmpty() && now >= s.successUntilMs) {
            s = s.copy(successText = "", successUntilMs = 0L)
        }

        // 1) A lesson is active: did they do it, fail it, or time out?
        val active = Lesson.fromId(s.lesson)
        if (active != null) {
            return when (evaluate(active, s, inp)) {
                Outcome.SUCCESS -> {
                    val effect = if (active == Lesson.POWERUP) powerupEffect(inp.pickedPowerupId) else null
                    HintResult(
                        end(now, effect ?: "✓ Nice!", if (effect != null) EFFECT_MS else SUCCESS_MS),
                        completed = active
                    )
                }
                Outcome.FAIL -> HintResult(end(now, "", 0L))
                Outcome.LOST -> HintResult(s.copy(lost = true, arrow = 0, slowMoUntilMs = 0L, targetX = -1f))
                Outcome.CONTINUE -> HintResult(track(s, active, inp))
            }
        }

        // 2) Nothing active: wait out cooldown / success banner, then maybe start one.
        if (now < s.cooldownUntilMs || s.successText.isNotEmpty()) return HintResult(s)

        fun ok(l: Lesson) = eligible(status, l, shownThisRun)

        if (ok(Lesson.STEER) && inp.tick >= 2) {
            // Already steering before the prompt would show? Count it, silently.
            if (abs(inp.userY - 50) >= 8) return HintResult(s, completed = Lesson.STEER, quiet = true)
            return start(s, Lesson.STEER, inp)
        }
        if (ok(Lesson.LOW_FUEL) && inp.fuelPercent < LOW_FUEL_PERCENT) return start(s, Lesson.LOW_FUEL, inp)
        if (ok(Lesson.BOSS) && inp.bossActive) return start(s, Lesson.BOSS, inp)
        if (ok(Lesson.DODGE)) {
            val threat = threatenedBy(inp)
            if (threat != null) return start(s, Lesson.DODGE, inp, threat.id)
        }
        if (ok(Lesson.POWERUP) && inp.elements.any { it.type == "powerup" && it.xOffsetFraction in 0.35f..0.95f }) {
            return start(s, Lesson.POWERUP, inp)
        }
        if (ok(Lesson.FUEL_CELL) && inp.elements.any { it.type == "fuel" && it.xOffsetFraction in 0.35f..0.95f }) {
            return start(s, Lesson.FUEL_CELL, inp)
        }
        if (ok(Lesson.GEM) && inp.elements.any { it.type == "gem" && it.xOffsetFraction in 0.35f..0.95f }) {
            return start(s, Lesson.GEM, inp)
        }
        return HintResult(s)
    }

    private fun start(s: HintState, l: Lesson, inp: HintInput, targetId: String = ""): HintResult {
        val slow = l.slowMoMs.coerceAtMost(SLOWMO_CAP_MS)
        val base = s.copy(
            lesson = l.id,
            promptText = l.prompt,
            startedAtMs = inp.nowMs,
            timeoutMs = l.timeoutMs,
            slowMoUntilMs = if (slow > 0L) inp.nowMs + slow else 0L,
            anchorY = inp.userY,
            targetId = targetId,
            lost = false
        )
        return HintResult(track(base, l, inp), shown = l)
    }

    // Clears the active lesson (and slow-mo) and starts the cooldown.
    private fun end(now: Long, successText: String, bannerMs: Long) = HintState(
        successText = successText,
        successUntilMs = if (bannerMs > 0L) now + bannerMs else 0L,
        cooldownUntilMs = now + bannerMs + COOLDOWN_MS
    )

    private fun evaluate(l: Lesson, s: HintState, inp: HintInput): Outcome {
        val elapsed = inp.nowMs - s.startedAtMs

        // Hit during a dodge lesson: no success any more, but the prompt stays
        // up for its minimum read time so it never vanishes in a blink.
        if (s.lost) return if (elapsed >= l.minShowMs) Outcome.FAIL else Outcome.CONTINUE
        if (l == Lesson.DODGE && inp.hitThisTick) return Outcome.LOST

        val success = when (l) {
            Lesson.STEER -> abs(inp.userY - s.anchorY) >= 8
            Lesson.GEM -> inp.pickedGem
            Lesson.FUEL_CELL -> inp.pickedFuel
            Lesson.POWERUP -> inp.pickedPowerupId != null
            Lesson.LOW_FUEL -> inp.refuelTapped
            // The boss window ends on its own; surviving it is the lesson.
            // Passive, so it waits for the minimum read time.
            Lesson.BOSS -> !inp.bossActive && elapsed >= l.minShowMs
            Lesson.DODGE -> dodged(s, inp)
        }
        if (success) return Outcome.SUCCESS
        if (l == Lesson.LOW_FUEL && inp.fuelPercent > 50) return Outcome.FAIL // grabbed a fuel cell instead
        if (elapsed >= s.timeoutMs) return Outcome.FAIL
        return Outcome.CONTINUE
    }

    // ---------- highlight target ----------

    private fun track(s: HintState, l: Lesson, inp: HintInput): HintState {
        val t = targetOf(l, s, inp)
        return s.copy(
            arrow = if (l == Lesson.DODGE && !s.lost) dodgeArrow(s.targetId, inp) else 0,
            targetX = t?.xOffsetFraction ?: -1f,
            targetY = t?.yMatchPos ?: 0
        )
    }

    private fun targetOf(l: Lesson, s: HintState, inp: HintInput): VisualTrackElement? = when (l) {
        Lesson.GEM -> nearestAhead(inp, "gem")
        Lesson.FUEL_CELL -> nearestAhead(inp, "fuel")
        Lesson.POWERUP -> nearestAhead(inp, "powerup")
        Lesson.DODGE -> if (s.lost) null else inp.elements.firstOrNull { it.id == s.targetId }
        else -> null
    }

    private fun nearestAhead(inp: HintInput, type: String): VisualTrackElement? =
        inp.elements
            .filter { it.type == type && it.xOffsetFraction in 0.2f..1.0f }
            .minByOrNull { it.xOffsetFraction }

    // ---------- DODGE helpers ----------

    private fun groupOf(id: String, elements: List<VisualTrackElement>): List<VisualTrackElement> {
        val prefix = id.substring(0, id.lastIndexOf('_') + 1)
        return elements.filter { it.type == "obstacle" && it.id.startsWith(prefix) }
    }

    private fun isGapPair(group: List<VisualTrackElement>) =
        group.size == 2 && group.all { it.subType in GAP_TYPES }

    // First obstacle ahead whose lane overlaps the player's (collision radius is 15).
    private fun threatenedBy(inp: HintInput): VisualTrackElement? =
        inp.elements
            .filter {
                it.type == "obstacle" && it.subType != "BLINK_HAZARD" &&
                    it.xOffsetFraction in 0.5f..0.95f && abs(inp.userY - it.yMatchPos) < 20
            }
            .minByOrNull { it.xOffsetFraction }

    private fun dodged(s: HintState, inp: HintInput): Boolean {
        val target = inp.elements.firstOrNull { it.id == s.targetId } ?: return true // passed / gone, no hit
        if (target.xOffsetFraction < 0.14f) return true
        val group = groupOf(target.id, inp.elements)
        if (!isGapPair(group) && target.xOffsetFraction < 0.5f &&
            abs(inp.userY - target.yMatchPos) >= 24
        ) return true
        return false
    }

    // -1 = up, +1 = down, 0 = already fine / nothing to show.
    private fun dodgeArrow(targetId: String, inp: HintInput): Int {
        val target = inp.elements.firstOrNull { it.id == targetId } ?: return 0
        val group = groupOf(target.id, inp.elements)
        if (isGapPair(group)) {
            val center = group.map { it.yMatchPos }.average().toInt()
            val diff = center - inp.userY
            return if (abs(diff) < 6) 0 else if (diff < 0) -1 else 1
        }
        var dir = if (target.yMatchPos >= inp.userY) -1 else 1
        if (dir == -1 && inp.userY <= 30) dir = 1
        if (dir == 1 && inp.userY >= 70) dir = -1
        return dir
    }

    private fun powerupEffect(id: String?): String = "✓ " + when (id) {
        "PU1" -> "Shield blocks one hit"
        "PU2" -> "Magnet pulls pickups to you"
        "PU3" -> "Time Slow: more time to react"
        "PU4" -> "Ghost: fly through obstacles"
        "PU5" -> "Score x2"
        "PU6" -> "Score x5"
        "PU7" -> "Invincible (not vs. Blink Strike)"
        "PU8" -> "Boom: path ahead wiped"
        "PU9" -> "Shrink: slip through gaps"
        "PU10" -> "Lane Warp: snapped to safe line"
        "PU11" -> "Zone Skip: leaped ahead"
        "PU12" -> "Legendary Aura: bundle of power-ups"
        else -> "Power-up active"
    }
}
