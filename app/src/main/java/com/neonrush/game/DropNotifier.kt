package com.neonrush.game

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/**
 * Local "a new hull just dropped" notifications. No server needed: while the app runs it schedules one
 * inexact alarm per upcoming drop for THIS player (Pro: release time, everyone else: release + early-access window).
 * The list is kept in SharedPreferences so a reboot can re-arm the alarms (see [DropAlarmReceiver]).
 *
 * Manifest needs: POST_NOTIFICATIONS, RECEIVE_BOOT_COMPLETED and the <receiver> for DropAlarmReceiver.
 */
/** kind = "hull" or "trail": both share one notification channel and one alarm list. */
data class DropEntry(val hullId: String, val name: String, val emoji: String, val atMs: Long, val proEarly: Boolean, val kind: String = "hull")

object DropNotifier {
    private const val PREFS = "drop_notifier"
    private const val KEY_ENTRIES = "entries"
    const val CHANNEL_ID = "hull_drops"
    const val ACTION_DROP = "com.neonrush.game.ACTION_HULL_DROP"

    private fun enc(e: DropEntry) = listOf(e.hullId, e.name.replace("|", " "), e.emoji, e.atMs.toString(), if (e.proEarly) "1" else "0", e.kind).joinToString("|")
    private fun dec(s: String): DropEntry? {
        val p = s.split("|")
        if (p.size < 5) return null
        val at = p[3].toLongOrNull() ?: return null
        return DropEntry(p[0], p[1], p[2], at, p[4] == "1", p.getOrNull(5)?.takeIf { it.isNotBlank() } ?: "hull")
    }

    private fun loadEntries(ctx: Context): List<DropEntry> =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_ENTRIES, "").orEmpty()
            .split(";;").filter { it.isNotBlank() }.mapNotNull { dec(it) }

    private fun pending(ctx: Context, e: DropEntry, flags: Int): PendingIntent? {
        val i = Intent(ctx, DropAlarmReceiver::class.java).setAction(ACTION_DROP)
            .putExtra("id", e.hullId).putExtra("name", e.name).putExtra("emoji", e.emoji).putExtra("early", e.proEarly).putExtra("kind", e.kind)
        return PendingIntent.getBroadcast(ctx, (e.kind + ":" + e.hullId).hashCode(), i, flags or PendingIntent.FLAG_IMMUTABLE)
    }

    /** Replace the scheduled alarms of ONE [kind] ("hull" or "trail") with [entries]; the other kind is left alone. */
    fun sync(ctx: Context, entries: List<DropEntry>, kind: String = "hull") {
        val app = ctx.applicationContext
        val am = app.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val old = loadEntries(app)
        old.filter { it.kind == kind }.forEach { e -> pending(app, e, PendingIntent.FLAG_NO_CREATE)?.let { am.cancel(it); it.cancel() } }
        val now = System.currentTimeMillis()
        val keep = entries.filter { it.atMs > now }.map { it.copy(kind = kind) }.sortedBy { it.atMs }.take(8)
        val others = old.filter { it.kind != kind && it.atMs > now }
        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_ENTRIES, (others + keep).joinToString(";;") { enc(it) }).apply()
        keep.forEach { e ->
            val pi = pending(app, e, PendingIntent.FLAG_UPDATE_CURRENT) ?: return@forEach
            try { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, e.atMs, pi) } catch (_: Exception) {}
        }
    }

    fun clear(ctx: Context) { sync(ctx, emptyList(), "hull"); sync(ctx, emptyList(), "trail") }

    /** Called from the receiver after boot: the alarms are gone, the saved list is not. */
    internal fun rearm(ctx: Context) {
        val all = loadEntries(ctx)
        sync(ctx, all.filter { it.kind == "hull" }, "hull")
        sync(ctx, all.filter { it.kind == "trail" }, "trail")
    }

    internal fun show(ctx: Context, name: String, emoji: String, early: Boolean, kind: String = "hull") {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL_ID, "New drops", NotificationManager.IMPORTANCE_DEFAULT))
        }
        val open = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)
            ?.apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP }
        val tap = open?.let { PendingIntent.getActivity(ctx, (kind + name).hashCode(), it, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE) }
        val trail = kind == "trail"
        val title = if (early) (if (trail) "⭐ Pro early access: $emoji $name trail" else "⭐ Pro early access: $emoji $name")
                    else (if (trail) "✨ New trail: $emoji $name" else "🚀 New hull: $emoji $name")
        val text = if (early) "It's yours to buy right now. Everyone else gets it in ${HullCatalog.EARLY_ACCESS_HOURS / 24} days."
                   else if (trail) "Just dropped in Shop > Trails. Leave it behind you on your next run."
                   else "Just dropped in the Hangar. Take it for an 8-second test flight."
        val n = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(ctx.applicationInfo.icon)
            .setContentTitle(title).setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true).setContentIntent(tap).setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        try { NotificationManagerCompat.from(ctx).notify((kind + name).hashCode(), n) } catch (_: SecurityException) {}
    }
}

class DropAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> DropNotifier.rearm(context)
            DropNotifier.ACTION_DROP -> DropNotifier.show(
                context,
                intent.getStringExtra("name").orEmpty(),
                intent.getStringExtra("emoji").orEmpty(),
                intent.getBooleanExtra("early", false),
                intent.getStringExtra("kind") ?: "hull"
            )
        }
    }
}
