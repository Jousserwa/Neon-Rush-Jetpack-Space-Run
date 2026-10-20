package com.neonrush.game.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.imageResource
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neonrush.game.LiveEvents
import com.neonrush.game.RemoteTuning
import kotlinx.coroutines.delay

/**
 * The live-event card for the home screen: event name and countdown, one progress bar with three reward markers,
 * and a CLAIM button for every tier that has been reached. Weekly events and holiday seasons use the same card.
 */
@Composable
fun LiveEventCard(onClaim: (Int) -> Unit, modifier: Modifier = Modifier) {
    val st by LiveEvents.state.collectAsState()
    var nowMs by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) { delay(30_000); nowMs = System.currentTimeMillis(); LiveEvents.refresh() }
    }
    val ctx = LocalContext.current
    var notifOk by remember {
        mutableStateOf(Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
    }
    val askNotif = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> notifOk = ok }
    val s = st ?: return
    val ev = s.event
    val accent = Color(ev.accent)
    val left = (ev.endMs - nowMs).coerceAtLeast(0L)
    val days = left / 86_400_000L
    val hours = (left / 3_600_000L) % 24
    val mins = (left / 60_000L) % 60
    val countdown = if (days > 0) "${days}d ${hours}h" else "${hours}h ${mins}m"
    val last = ev.tiers.last().goal.coerceAtLeast(1)
    val frac = (s.progress.toFloat() / last).coerceIn(0f, 1f)
    val banner = remember(nowMs / 60_000L) { try { RemoteTuning.eventBanner() } catch (_: Throwable) { "" } }

    Column(
        modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(accent.copy(alpha = 0.10f))
            .border(1.dp, accent.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                // the pilot cheers while a reward is waiting to be claimed
                val rewardWaiting = ev.tiers.indices.any { s.progress >= ev.tiers[it].goal && ((s.claimed shr it) and 1) == 0 }
                if (rewardWaiting) androidx.compose.foundation.Image(
                    painter = androidx.compose.ui.graphics.painter.BitmapPainter(androidx.compose.ui.graphics.ImageBitmap.imageResource(id = com.neonrush.game.R.drawable.pilot_cheer)),
                    contentDescription = null, modifier = Modifier.size(width = 32.dp, height = 48.dp)
                )
                Text(
                    "${ev.emoji} ${ev.title}" + if (ev.holiday) "  • SEASON" else "  • WEEKLY",
                    color = accent, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp, fontFamily = FontFamily.Monospace, maxLines = 1
                )
            }
            Text("ends in $countdown", color = Color.White.copy(alpha = 0.8f), fontSize = 10.sp, fontFamily = FontFamily.Monospace, maxLines = 1)
        }
        Text(ev.blurb, color = Color.White.copy(alpha = 0.8f), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
        if (banner.isNotBlank()) Text(banner, color = Color(0xFFFFD54F), fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        // progress bar with a marker for every tier
        Box(modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(Color.White.copy(alpha = 0.12f))) {
            Box(modifier = Modifier.fillMaxWidth(frac).height(10.dp).background(accent))
        }
        Text(
            "${s.progress} / $last   •   🏅 ${s.medals}",
            color = Color.White, fontSize = 10.sp, fontFamily = FontFamily.Monospace
        )
        if (!notifOk) {
            Text(
                "\uD83D\uDD14 Tap for event & crate reminders",
                color = Color(0xFFFFD54F), fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace,
                modifier = Modifier.clickable { askNotif.launch(Manifest.permission.POST_NOTIFICATIONS) }
            )
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ev.tiers.forEachIndexed { i, t ->
                val reached = s.progress >= t.goal
                val taken = ((s.claimed shr i) and 1) == 1
                Column(
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                        .background(if (reached && !taken) accent else Color.White.copy(alpha = 0.07f))
                        .then(if (reached && !taken) Modifier.clickable { onClaim(i) } else Modifier)
                        .padding(horizontal = 4.dp, vertical = 5.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        if (taken) "✓ DONE" else if (reached) "CLAIM" else ev.goalText(t.goal),
                        color = if (reached && !taken) Color.Black else Color.White.copy(alpha = 0.85f),
                        fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, maxLines = 1
                    )
                    Text(
                        "💎 ${t.gems}" + if (i == ev.tiers.lastIndex) " +🏅" else "",
                        color = if (reached && !taken) Color.Black else Color(0xFF8CF3FF),
                        fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}
