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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neonrush.game.Contracts
import com.neonrush.game.LegendTrack
import com.neonrush.game.Sky
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

private val LGOLD = Color(0xFFFFD23F)
private val LMAG = Color(0xFFFF4DD2)
private val LCYAN = Color(0xFF3DE8FF)
private val LDARK = Color(0xFF0E0A22)

@Composable
private fun pulse(ms: Int = 1400): Float {
    val inf = rememberInfiniteTransition(label = "pulse")
    val p by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(ms, easing = LinearEasing), RepeatMode.Reverse), label = "p")
    return p
}

/** LEGENDARY REWARDS TRACK card: tier dots, progress to the next zone goal, tap to collect. Free pilots see a locked teaser. */
@Composable
fun LegendTrackCard(isPro: Boolean, onClaim: () -> Unit, onUpgrade: () -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    @Suppress("UNUSED_VARIABLE") val v = LegendTrack.version
    val best = LegendTrack.best()
    val reached = LegendTrack.reached()
    val pending = LegendTrack.pendingGems(ctx)
    val next = LegendTrack.TIERS.getOrNull(reached)
    val p = pulse(if (pending > 0) 700 else 1800)
    val shape = RoundedCornerShape(16.dp)
    val glow = if (pending > 0) LGOLD else LMAG
    Column(
        modifier = modifier.fillMaxWidth().clip16(shape)
            .background(Brush.horizontalGradient(listOf(LDARK, Color(0xFF2A0F3D), LDARK)))
            .border(2.dp, glow.copy(alpha = 0.45f + 0.5f * p), shape)
            .clickable { if (!isPro) onUpgrade() else if (pending > 0) onClaim() }
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val crown = rememberArt("legend_crown")
            if (crown != null) androidx.compose.foundation.Image(androidx.compose.ui.graphics.painter.BitmapPainter(crown), contentDescription = null, modifier = Modifier.size(42.dp))
            else Text("🔥", fontSize = 26.sp)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text("LEGENDARY REWARDS TRACK", color = LGOLD, fontSize = 13.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
                Text(
                    if (!isPro) "Pro pilots earn gems and the Legend Ember trail by flying deep on Legendary." else if (next != null) "Next: zone ${next.zone} on Legendary  (best $best)" else "Every tier complete. True Legend.",
                    color = Color.White.copy(alpha = 0.85f), fontSize = 11.sp, fontFamily = FontFamily.Monospace
                )
            }
            if (!isPro) Text("🔒", fontSize = 22.sp)
            else if (pending > 0) Box(Modifier.clip16(RoundedCornerShape(10.dp)).background(LGOLD.copy(alpha = 0.75f + 0.25f * p)).padding(horizontal = 10.dp, vertical = 6.dp)) {
                Text("CLAIM +$pending 💎", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            LegendTrack.TIERS.forEachIndexed { i, t ->
                val done = isPro && i < reached
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.fillMaxWidth().height(8.dp).clip16(RoundedCornerShape(4.dp)).background(if (done) LGOLD else Color.White.copy(alpha = 0.12f)))
                    Text(if (t.trail) "🔥${t.zone}" else "${t.zone}", color = if (done) LGOLD else Color.White.copy(alpha = 0.5f), fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}

/** PILOT CONTRACTS: three daily goals with progress bars; tap a finished one to collect. Pro can reroll one a day. */
@Composable
fun ContractsCard(isPro: Boolean, onClaim: (Int) -> Unit, onReroll: (Int) -> Unit, onUpgrade: () -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    @Suppress("UNUSED_VARIABLE") val v = Contracts.version
    val list = Contracts.items(ctx)
    if (list.isEmpty()) return
    val canReroll = isPro && !Contracts.rerolled(ctx)
    val p = pulse(900)
    val shape = RoundedCornerShape(16.dp)
    val all = list.all { it.claimed }
    Column(
        modifier = modifier.fillMaxWidth().clip16(shape)
            .background(Brush.verticalGradient(listOf(Color(0xFF10163A), LDARK)))
            .border(2.dp, (if (all) LGOLD else LCYAN).copy(alpha = 0.5f), shape)
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("📋", fontSize = 22.sp)
            Spacer(Modifier.width(8.dp))
            Text("PILOT CONTRACTS", color = LCYAN, fontSize = 13.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
            Text(if (all) "ALL DONE ✔" else "new in ${Contracts.hoursLeft()}h", color = if (all) LGOLD else Color.White.copy(alpha = 0.6f), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
        }
        Spacer(Modifier.height(6.dp))
        list.forEachIndexed { i, c ->
            val ready = c.done && !c.claimed
            Row(
                Modifier.fillMaxWidth().padding(vertical = 3.dp).clip16(RoundedCornerShape(10.dp))
                    .background(if (ready) LGOLD.copy(alpha = 0.10f + 0.12f * p) else Color.White.copy(alpha = 0.05f))
                    .clickable { if (ready) onClaim(i) }.padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(Contracts.icon(c.type), fontSize = 18.sp)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(Contracts.label(c.type, c.target), color = Color.White, fontSize = 11.sp, fontFamily = FontFamily.Monospace, maxLines = 2)
                    Spacer(Modifier.height(3.dp))
                    Box(Modifier.fillMaxWidth().height(6.dp).clip16(RoundedCornerShape(3.dp)).background(Color.White.copy(alpha = 0.12f))) {
                        Box(Modifier.fillMaxWidth(c.frac).fillMaxHeight().background(if (c.done) LGOLD else LCYAN))
                    }
                }
                Spacer(Modifier.width(8.dp))
                when {
                    c.claimed -> Text("✔", color = Color(0xFF69F0AE), fontSize = 16.sp)
                    ready -> Text("+${Contracts.reward(c.type)}💎\nTAP", color = LGOLD, fontSize = 10.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center, fontFamily = FontFamily.Monospace)
                    else -> {
                        Text("${c.prog.coerceAtMost(c.target)}/${c.target}", color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                        if (canReroll) {
                            Spacer(Modifier.width(6.dp))
                            Text("🔄", fontSize = 16.sp, modifier = Modifier.clickable { onReroll(i) })
                        }
                    }
                }
            }
        }
        if (!isPro) Text("👑 Pro pilots can reroll one contract a day", color = LGOLD.copy(alpha = 0.8f), fontSize = 10.sp, fontFamily = FontFamily.Monospace,
            modifier = Modifier.clickable { onUpgrade() }.padding(top = 4.dp))
    }
}

/** LIVING WORLD banner: today's sky and its gem bonus. Pro pilots also see tomorrow's sky. */
@Composable
fun SkyBanner(isPro: Boolean, onUpgrade: () -> Unit, modifier: Modifier = Modifier) {
    val sky = Sky.today()
    val col = Color(sky.argb)
    val p = pulse(2000)
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = modifier.fillMaxWidth().clip16(shape)
            .background(Brush.horizontalGradient(listOf(col.copy(alpha = 0.22f), LDARK)))
            .border(1.5.dp, col.copy(alpha = 0.35f + 0.35f * p), shape)
            .clickable { if (!isPro) onUpgrade() }.padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val skyArt = rememberArt(when (sky) { Sky.SANDSTORM -> "sky_sandstorm"; Sky.AURORA -> "sky_aurora"; Sky.FOG -> "sky_fog"; Sky.METEORS -> "sky_meteors"; Sky.NEON_RAIN -> "sky_rain"; else -> "sky_clear" })
        if (skyArt != null) androidx.compose.foundation.Image(androidx.compose.ui.graphics.painter.BitmapPainter(skyArt), contentDescription = null, modifier = Modifier.size(40.dp).clip(RoundedCornerShape(6.dp)))
        else Text(sky.emoji, fontSize = 26.sp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text("TODAY: ${sky.label}" + if (sky.bonusPct > 0) "  +${sky.bonusPct}% GEMS" else "", color = col, fontSize = 12.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
            Text(sky.blurb, color = Color.White.copy(alpha = 0.8f), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
        }
        val tm = Sky.tomorrow()
        Text(if (isPro) "Tomorrow\n${tm.emoji} ${tm.label.lowercase()}" else "Tomorrow\n🔒 Pro", color = Color.White.copy(alpha = 0.65f), fontSize = 9.sp, textAlign = TextAlign.Center, fontFamily = FontFamily.Monospace)
    }
}

/** In-race sky effects for today's weather, drawn in code over every world. Light enough to keep the game readable. */
@Composable
fun SkyOverlay(modifier: Modifier = Modifier) {
    val sky = Sky.today()
    if (sky == Sky.CLEAR) return
    val inf = rememberInfiniteTransition(label = "sky")
    val t by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(6000, easing = LinearEasing), RepeatMode.Restart), label = "t")
    Canvas(modifier.fillMaxSize()) {
        val w = size.width; val h = size.height
        fun r(i: Int): Float { val x = sin(i * 12.9898f) * 43758.547f; return x - kotlin.math.floor(x) }
        when (sky) {
            Sky.SANDSTORM -> {
                drawRect(Color(0x33C98A3A))
                for (i in 0 until 40) {
                    val y = r(i) * h; val x = ((r(i + 50) + t * (1.2f + r(i + 9))) % 1f) * (w + 200f) - 100f
                    drawLine(Color(0x66FFD28A), Offset(x, y), Offset(x - 90f - 60f * r(i + 3), y + 6f), strokeWidth = 2f, cap = StrokeCap.Round)
                }
            }
            Sky.FOG -> {
                for (k in 0 until 3) {
                    val sh = ((t + k / 3f) % 1f)
                    drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Color(0x30CFD8DC), Color.Transparent), startX = sh * w * 1.6f - w * 0.6f, endX = sh * w * 1.6f + w * 0.2f))
                }
                drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Color(0x40B0BEC5)), startX = w * 0.45f, endX = w))
            }
            Sky.AURORA -> {
                for (k in 0 until 3) {
                    val col = listOf(Color(0x3369F0AE), Color(0x3340C4FF), Color(0x33B388FF))[k]
                    val base = h * (0.10f + 0.07f * k)
                    var prev = Offset(0f, base)
                    for (s in 1..16) {
                        val x = w * s / 16f
                        val y = base + sin((s / 16f * 2f + t * 2f + k) * PI.toFloat()) * h * 0.05f
                        drawLine(col, prev, Offset(x, y), strokeWidth = h * 0.06f, cap = StrokeCap.Round)
                        prev = Offset(x, y)
                    }
                }
            }
            Sky.METEORS -> {
                for (i in 0 until 6) {
                    val f = ((t * 2f + r(i) * 3f) % 1.6f)
                    if (f < 1f) {
                        val x = w * (0.2f + r(i + 5) * 0.9f) - f * w * 0.5f; val y = f * h * 0.55f
                        drawLine(Brush.linearGradient(listOf(Color(0xFFFFE0B2), Color.Transparent), Offset(x, y), Offset(x + 120f, y - 60f)), Offset(x, y), Offset(x + 120f, y - 60f), strokeWidth = 3f, cap = StrokeCap.Round)
                        drawCircle(Color(0xFFFFF3E0), 4f, Offset(x, y))
                    }
                }
            }
            Sky.NEON_RAIN -> {
                for (i in 0 until 46) {
                    val x = r(i) * w; val y = ((r(i + 30) + t * (2f + r(i + 2))) % 1f) * (h + 80f) - 40f
                    drawLine(if (i % 2 == 0) Color(0x88FF4DD2) else Color(0x883DE8FF), Offset(x, y), Offset(x - 8f, y + 34f), strokeWidth = 2.2f, cap = StrokeCap.Round)
                }
            }
            else -> {}
        }
    }
}

private fun Modifier.clip16(shape: RoundedCornerShape): Modifier = this.then(Modifier.clip(shape))
