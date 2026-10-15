package com.neonrush.game.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.clickable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neonrush.game.NeonRushViewModel
import com.neonrush.game.R
import com.neonrush.game.RevenueCatManager
import com.neonrush.game.ServerClock
import com.neonrush.game.ThrusterCatalog
import com.neonrush.game.ThrusterKind
import com.neonrush.game.Rarity
import com.neonrush.game.db.GameProfile
import com.neonrush.game.ui.theme.CyberBackground
import com.neonrush.game.ui.theme.CyberOnSurface
import com.neonrush.game.ui.theme.CyberPrimary
import com.neonrush.game.ui.theme.CyberSecondary
import com.neonrush.game.ui.theme.CyberSurface
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Shop > TRAILS. Each trail is shown as a big "flight window": the pilot flies a wavy line over a scrolling starfield and
 * leaves the real trail behind. Rarity (Common / Rare / Epic / Legendary) sets the frame colour and badge, and Legendary
 * cards get a moving light sweep, so the collection reads as something to climb, not a list.
 */
@Composable
fun ThrustersPanel(viewModel: NeonRushViewModel, profile: GameProfile) {
    val st by viewModel.thrusters.state.collectAsState()
    val pro by RevenueCatManager.isPro.collectAsState()
    val clock = rememberInfiniteTransition(label = "trailClock").animateFloat(
        0f, 600f, infiniteRepeatable(tween(600_000, easing = LinearEasing), RepeatMode.Restart), label = "tc"
    )
    val pilot = ImageBitmap.imageResource(id = R.drawable.pilot_run_1)
    LaunchedEffect(profile.bestZoneReached, profile.totalRuns, profile.bestScore, profile.currentStreak) {
        viewModel.thrusters.evaluate(profile)
    }
    DisposableEffect(Unit) { onDispose { viewModel.thrusters.clearNew() } }
    val nowMs = remember { ServerClock.bestNowMs() }
    val have = ThrusterCatalog.ALL.count { it.id in st.owned && (it.kind != ThrusterKind.PRO || pro) }
    val released = ThrusterCatalog.ALL.count { ThrusterCatalog.released(it, nowMs) }
    val upcoming = ThrusterCatalog.ALL.filter { !ThrusterCatalog.released(it, nowMs) }.minByOrNull { it.dropMonth }

    Text("🚀 THRUSTER TRAILS", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = CyberPrimary,
        fontFamily = FontFamily.Monospace, modifier = Modifier.padding(bottom = 4.dp))
    Text("$have/$released collected. A trail is matter left on your flight path. It works with any hull and any suit." +
        (upcoming?.let { "  Next drop: ${it.name} on ${dropDate(it)} (Pro get it first)." } ?: ""),
        color = CyberOnSurface.copy(alpha = 0.7f), fontSize = 12.sp, modifier = Modifier.padding(bottom = 12.dp))

    var lockInfo by remember { mutableStateOf<LockInfo?>(null) }
    lockInfo?.let { LockInfoDialog(it) { lockInfo = null } }
    ThrusterCatalog.ALL.forEach { th ->
        val owned = th.id in st.owned && (th.kind != ThrusterKind.PRO || pro)
        val active = st.active == th.id && owned
        val rc = Color(th.rarity.argb)
        val shape = RoundedCornerShape(12.dp)
        Column(
            Modifier.fillMaxWidth().padding(vertical = 5.dp).clip(shape).background(CyberSurface)
                .border(if (active) 2.5.dp else 1.5.dp, rc.copy(alpha = if (active) 1f else 0.6f), shape)
                .then(
                    if (!owned && !(th.kind == ThrusterKind.GEMS && ThrusterCatalog.buyableAtMs(th, pro) <= nowMs))
                        Modifier.clickable { lockInfo = trailLockInfo(th, pro, nowMs, st.progress[th.id], rc) } else Modifier
                )
        ) {
            Canvas(Modifier.fillMaxWidth().height(92.dp).background(Brush.verticalGradient(listOf(Color(0xFF04071A), rc.copy(alpha = 0.22f))))) {
                val tm = clock.value + (th.id.hashCode() and 0xff) * 0.01f
                // scrolling starfield
                for (i in 0 until 26) {
                    val sp = 25f + 55f * ((i * 37 % 10) / 10f)
                    val sx = (((i * 61 % 100) / 100f) * size.width - tm * sp).let { v -> v - floor(v / size.width) * size.width }
                    val sy = ((i * 29 % 100) / 100f) * size.height
                    drawCircle(Color.White.copy(alpha = 0.2f + 0.4f * ((i % 4) / 4f)), 1.2f + (i % 3) * 0.5f, Offset(sx, sy))
                }
                val ph = size.height * 0.62f
                val px = size.width * 0.74f
                val cy = size.height / 2f
                val amp = size.height * 0.2f
                val yAt: (Float) -> Float = { age -> cy + sin((tm - age) * 1.9f) * amp }
                val py = yAt(0f)
                drawThruster(th.id, px, py, ph, tm, 1, yAt)
                val pw = ph * pilot.width / pilot.height
                drawImage(pilot, dstOffset = IntOffset((px - pw / 2f).roundToInt(), (py - ph / 2f).roundToInt()),
                    dstSize = IntSize(pw.roundToInt(), ph.roundToInt()))
                if (th.rarity == Rarity.LEGENDARY) {   // moving light sweep
                    val sweep = ((tm * 0.35f) % 1.6f - 0.3f) * size.width
                    drawRect(Brush.horizontalGradient(listOf(Color.Transparent, rc.copy(alpha = 0.22f), Color.Transparent),
                        startX = sweep - 60f, endX = sweep + 60f))
                }
            }
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(end = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.background(rc, RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 1.dp)) {
                            Text(th.rarity.label, color = Color(0xFF05060F), fontSize = 9.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
                        }
                        Text("  ${th.emoji} ${th.name}", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
                    }
                    Text(th.desc, fontSize = 11.sp, color = CyberPrimary.copy(alpha = 0.7f), modifier = Modifier.padding(top = 3.dp))
                    if (th.dropMonth > 0 && !owned) {
                        val at = ThrusterCatalog.buyableAtMs(th, pro)
                        val rel = ThrusterCatalog.releaseMs(th)
                        val line = when {
                            at <= nowMs -> "🆕 Monthly drop · ${dropDate(th)}"
                            rel > nowMs -> "🔒 Drops ${dropDate(th)} · Pro members get it first"
                            else -> "⏳ Pro early access is live · opens for you in ${countdown(at - nowMs)}"
                        }
                        Text(line, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = Color(0xFFFFD23F), modifier = Modifier.padding(top = 3.dp))
                    }
                    if (th.id in st.newDrops) Text("NEW", fontSize = 10.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, color = Color(0xFF69F0AE), modifier = Modifier.padding(top = 2.dp))
                    if (!owned && th.kind != ThrusterKind.GEMS && th.kind != ThrusterKind.FREE) {
                        Text("🎯 ${th.requirement}" + (st.progress[th.id]?.let { "  ($it)" } ?: ""), fontSize = 10.sp, fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace, color = Color(0xFFFFD23F), modifier = Modifier.padding(top = 3.dp))
                    }
                }
                when {
                    active -> Box(Modifier.background(CyberPrimary, RoundedCornerShape(4.dp)).padding(horizontal = 8.dp, vertical = 4.dp)) {
                        Text("ACTIVE", color = CyberBackground, fontWeight = FontWeight.Bold, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    }
                    owned -> Button(
                        onClick = { viewModel.thrusters.equip(th.id) },
                        colors = ButtonDefaults.buttonColors(containerColor = CyberSurface.copy(alpha = 0.8f)),
                        shape = RoundedCornerShape(4.dp), modifier = Modifier.border(1.dp, CyberPrimary, RoundedCornerShape(4.dp))
                    ) { Text("EQUIP", color = CyberPrimary, fontFamily = FontFamily.Monospace, fontSize = 11.sp) }
                    th.kind == ThrusterKind.GEMS && ThrusterCatalog.buyableAtMs(th, pro) > nowMs -> Text("🔒", fontSize = 16.sp)
                    th.kind == ThrusterKind.GEMS -> Button(
                        onClick = { viewModel.thrusters.buy(th.id) },
                        colors = ButtonDefaults.buttonColors(containerColor = CyberSecondary), shape = RoundedCornerShape(4.dp)
                    ) { Text("💎 ${th.gemCost}", color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 11.sp) }
                    th.kind == ThrusterKind.PRO -> Text("👑 PRO", fontSize = 11.sp, color = Color(0xFFFFD23F), fontFamily = FontFamily.Monospace)
                    else -> Text("🔒", fontSize = 16.sp)
                }
            }
        }
    }
}

private fun dropDate(t: com.neonrush.game.Thruster): String =
    java.text.SimpleDateFormat("d MMM yyyy", java.util.Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }.format(java.util.Date(ThrusterCatalog.releaseMs(t)))

private fun countdown(ms: Long): String {
    val m = (ms / 60000L).coerceAtLeast(0L)
    val d = m / 1440L; val hh = (m % 1440L) / 60L; val mm = m % 60L
    return if (d > 0) "${d}d ${hh}h" else "${hh}h ${mm}m"
}

private fun trailLockInfo(th: com.neonrush.game.Thruster, pro: Boolean, nowMs: Long, progress: String?, accent: Color): LockInfo {
    val lines = ArrayList<String>()
    val at = ThrusterCatalog.buyableAtMs(th, pro)
    val rel = ThrusterCatalog.releaseMs(th)
    if (th.kind == ThrusterKind.GEMS && at > nowMs) {
        if (rel > nowMs) {
            lines.add("Drops on ${dropDate(th)}.")
            lines.add("Pro members can buy it first, during early access.")
        } else {
            lines.add("Pro early access is live. It opens for you in ${countdown(at - nowMs)}.")
            lines.add("Subscribe to Pro to buy it right now.")
        }
        lines.add("After it opens, you buy it with 💎 ${th.gemCost}.")
        return LockInfo(th.emoji, th.name, "Not released to you yet.", lines, null, null, accent)
    }
    if (th.kind == ThrusterKind.PRO) {
        lines.add("This trail is for Pro members only.")
        lines.add("Subscribe to Neon Rush Pro and it is yours while you stay subscribed.")
        return LockInfo(th.emoji, th.name, "Pro trail.", lines, null, null, accent)
    }
    if (th.requirement.isNotBlank()) lines.add(th.requirement)
    if (!progress.isNullOrBlank()) lines.add("Your progress: $progress")
    lines.add("It unlocks by itself the moment you meet the goal.")
    return LockInfo(th.emoji, th.name, "Earn it by playing.", lines, null, null, accent)
}
