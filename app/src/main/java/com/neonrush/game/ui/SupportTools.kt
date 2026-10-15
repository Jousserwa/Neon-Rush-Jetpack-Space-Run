package com.neonrush.game.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import com.google.android.play.core.review.ReviewManagerFactory
import com.neonrush.game.SupportSnapshot
import java.util.Locale

/**
 * In-game "Report a Bug / Send Feedback": opens the player's email app with a ready-made message to
 * support, already filled with the diagnostic info (app version, Android, device, sector, score).
 * No permissions needed. If the phone has no email app we show a short message instead of crashing.
 */
object SupportMail {
    const val ADDRESS = "joudinksserwanja@gmail.com"

    fun send(context: Context, snap: SupportSnapshot): Boolean {
        val (versionName, versionCode) = appVersion(context)
        val body = buildString {
            append("Describe the problem here (what happened, what you expected):\n\n\n\n")
            append("------ Diagnostic info (please keep) ------\n")
            append("App: Neon Rush $versionName (build $versionCode)\n")
            append("Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})\n")
            append("Device: ${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}\n")
            append("Sector: ${snap.sector}\n")
            append("Score: ${snap.score}\n")
            append("Difficulty: ${snap.difficulty}\n")
            append("Language: ${Locale.getDefault().toLanguageTag()}\n")
        }
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:")
            putExtra(Intent.EXTRA_EMAIL, arrayOf(ADDRESS))
            putExtra(Intent.EXTRA_SUBJECT, "Neon Rush bug report / feedback ($versionName)")
            putExtra(Intent.EXTRA_TEXT, body)
            if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "No email app found. Write to $ADDRESS", Toast.LENGTH_LONG).show()
            false
        } catch (e: Exception) {
            Toast.makeText(context, "Couldn't open email. Write to $ADDRESS", Toast.LENGTH_LONG).show()
            false
        }
    }

    private fun appVersion(context: Context): Pair<String, Long> = try {
        val pi = context.packageManager.getPackageInfo(context.packageName, 0)
        val code = if (Build.VERSION.SDK_INT >= 28) pi.longVersionCode else @Suppress("DEPRECATION") pi.versionCode.toLong()
        (pi.versionName ?: "?") to code
    } catch (e: PackageManager.NameNotFoundException) {
        "?" to 0L
    } catch (e: Exception) {
        "?" to 0L
    }
}

/**
 * Google Play In-App Review. Opens Google's own review sheet over the game. Google decides whether it
 * is actually shown (it has its own quota), so every failure is swallowed and the game carries on.
 * Never offer a reward for rating - Google forbids it.
 */
object ReviewPrompter {
    fun request(activity: Activity) {
        try {
            if (activity.isFinishing) return
            val manager = ReviewManagerFactory.create(activity)
            manager.requestReviewFlow().addOnCompleteListener { task ->
                try {
                    if (task.isSuccessful && !activity.isFinishing) {
                        manager.launchReviewFlow(activity, task.result)
                    }
                } catch (e: Throwable) { /* silent: never break the game over a review prompt */ }
            }
        } catch (e: Throwable) { /* silent */ }
    }
}
