package com.neonrush.game

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Calendar

/**
 * PRO GREETING: Pro members choose the name they want to be greeted with. It is shown only to the player themselves (Home greeting,
 * game-over line), never to other players, so it needs no moderation. Up to 14 characters.
 * The player is asked once, right after becoming Pro; after that the name can be changed from the greeting itself or from Settings.
 * [version] changes whenever the name changes so the screens redraw.
 */
object ProGreeting {
    const val MAX = 14
    var version by mutableStateOf(0)
    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences("neonrush_progreeting", Context.MODE_PRIVATE)

    fun name(ctx: Context): String { @Suppress("UNUSED_VARIABLE") val v = version; return try { prefs(ctx).getString("name", "") ?: "" } catch (_: Throwable) { "" } }
    fun needsPrompt(ctx: Context): Boolean = try { name(ctx).isEmpty() && !prefs(ctx).getBoolean("prompted", false) } catch (_: Throwable) { false }
    fun markPrompted(ctx: Context) { try { prefs(ctx).edit().putBoolean("prompted", true).apply() } catch (_: Throwable) { } }

    /** Keeps letters, digits, spaces and a few name marks; collapses spaces; cuts to [MAX]. */
    fun clean(raw: String): String {
        val kept = raw.filter { it.isLetterOrDigit() || it == ' ' || it == '\'' || it == '-' || it == '.' }
        return kept.replace(Regex(" +"), " ").trim().take(MAX).trim()
    }

    fun set(ctx: Context, raw: String): String {
        val n = clean(raw)
        try { prefs(ctx).edit().putString("name", n).putBoolean("prompted", true).apply() } catch (_: Throwable) { }
        version += 1
        return n
    }

    fun phrase(): String = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
        in 5..11 -> "GOOD MORNING"
        in 12..16 -> "GOOD AFTERNOON"
        in 17..21 -> "GOOD EVENING"
        else -> "WELCOME BACK"
    }

    /** 0 = Sunday ... 6 = Saturday: which of the seven looks is on today. */
    fun weekday(): Int = Calendar.getInstance().get(Calendar.DAY_OF_WEEK) - 1
}
