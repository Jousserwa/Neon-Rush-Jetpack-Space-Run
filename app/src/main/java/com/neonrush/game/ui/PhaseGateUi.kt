package com.neonrush.game.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neonrush.game.AirSupport
import com.neonrush.game.NeonSoundEngine
import com.neonrush.game.Pause
import com.neonrush.game.PhaseGate
import com.neonrush.game.PilotDrift
import com.neonrush.game.Showcase
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

private fun pgFr(x: Float) = x - floor(x)
private val PG_MONO = FontFamily.Monospace
private val PG_CRACK = listOf(0.62f to 0.10f, 0.57f to 0.26f, 0.66f to 0.40f, 0.56f to 0.54f, 0.64f to 0.70f, 0.59f to 0.88f)

private fun DrawScope.pgDiamond(cx: Float, cy: Float, r: Float, c: Color) {
    val p = Path().apply { moveTo(cx, cy - r * 1.5f); lineTo(cx + r, cy); lineTo(cx, cy + r * 1.5f); lineTo(cx - r, cy); close() }
    drawPath(p, c)
    drawPath(p, Color.White.copy(alpha = c.alpha * 0.8f), style = Stroke(width = r * 0.12f))
}

/**
 * PHASE GATE overlay (see PhaseGate.kt): omen -> pull -> 2.5 s realm glimpse -> snap back. Draws only; it never takes touches, so the pilot can still steer.
 * Hazards are harmless while it runs because the pilot is covered (invulnerable) from the omen to the snap back.
 */
@Composable
fun PhaseGateOverlay(active: Boolean, tierName: String, bossActive: Boolean, fuel: Int, runs: Int, pilotY: Int, onGems: (Int) -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val dens = LocalDensity.current.density
    val bossRef = rememberUpdatedState(bossActive)
    val fuelRef = rememberUpdatedState(fuel)
    val tierRef = rememberUpdatedState(tierName)
    val runsRef = rememberUpdatedState(runs)
    val gemsRef = rememberUpdatedState(onGems)
    var ph by remember { mutableStateOf(0) }
    var t by remember { mutableStateOf(0f) }
    var kind by remember { mutableStateOf(0) }
    var flash by remember { mutableStateOf(0f) }
    var tick by remember { mutableStateOf(0) }
    var title by remember { mutableStateOf("") }
    var line by remember { mutableStateOf("") }
    var icon by remember { mutableStateOf("") }
    var teaser by remember { mutableStateOf<Showcase.Entry?>(null) }
    // optional paintings (drop webp files in res/drawable with these names; without them the gate is drawn in code)
    val artRing = rememberArt("phasegate_ring")
    val artR0 = rememberArt("phasegate_realm0"); val artR1 = rememberArt("phasegate_realm1"); val artR2 = rememberArt("phasegate_realm2"); val artR3 = rememberArt("phasegate_realm3")

    LaunchedEffect(active) {
        if (!active) { PhaseGate.phase = 0; ph = 0; return@LaunchedEffect }
        PhaseGate.newRun(); ph = 0; t = 0f
        var last = 0L
        try {
            while (true) {
                val now = withFrameNanos { it }
                val dt = if (last == 0L) 0.016f else ((now - last) / 1_000_000_000f).coerceAtMost(0.05f)
                last = now
                if (flash > 0f) flash = (flash - dt * 3f).coerceAtLeast(0f)
                if (ph == 0) {
                    if (PhaseGate.enabled(ctx) && !Pause.paused && !Pause.covering() && !AirSupport.covering()) PhaseGate.elapsedSec += dt
                    if (PhaseGate.enabled(ctx) && PhaseGate.elapsedSec >= PhaseGate.nextAtSec && PhaseGate.safe(tierRef.value, bossRef.value, fuelRef.value, runsRef.value)) {
                        kind = PhaseGate.nextKind(ctx)
                        teaser = if (kind == 2) Showcase.pick(ctx, Showcase.HANGAR) else null
                        when (kind) {
                            0 -> { title = "CRYSTAL SEA"; line = "A realm of floating glass. Nothing here can hurt you."; icon = "" }
                            1 -> { title = "STORM CITY"; line = "A city that never stops flashing. Nothing here can hurt you."; icon = "" }
                            2 -> { title = "IN THE HANGAR"; line = (teaser?.title ?: "") + ": " + (teaser?.line ?: ""); icon = teaser?.icon ?: "🚀" }
                            else -> { title = "A GIFT FROM THE GATE"; line = "+${PhaseGate.GIFT_GEMS} gems are yours."; icon = "💎" }
                        }
                        PhaseGate.count += 1; PhaseGate.phase = 1; ph = 1; t = 0f
                        NeonSoundEngine.setMusicSuspended(true)
                        PhaseGate.play(1); NeonSoundEngine.triggerHaptic(longArrayOf(0, 15, 60, 25, 60, 40, 60, 60))
                    }
                } else {
                    t += dt
                    if (ph == 1 && t >= PhaseGate.T_OMEN) { ph = 2; PhaseGate.phase = 2; t = 0f; PhaseGate.play(2) }
                    else if (ph == 2 && t >= PhaseGate.T_PULL) { ph = 3; PhaseGate.phase = 3; t = 0f; flash = 0.45f; PhaseGate.play(3); NeonSoundEngine.triggerHaptic(longArrayOf(0, 90, 30, 40)) }
                    else if (ph == 3 && t >= PhaseGate.T_GLIMPSE) { ph = 4; PhaseGate.phase = 4; t = 0f; flash = 0.5f; PhaseGate.play(4); NeonSoundEngine.triggerHaptic(longArrayOf(0, 60, 25, 30)) }
                    else if (ph == 4 && t >= PhaseGate.T_BACK) {
                        ph = 0; PhaseGate.phase = 0; t = 0f
                        NeonSoundEngine.setMusicSuspended(false)
                        PhaseGate.scheduleNext()
                        if (kind == 3) gemsRef.value(PhaseGate.GIFT_GEMS)
                    }
                }
                tick += 1
            }
        } finally {
            PhaseGate.phase = 0
            if (ph != 0) NeonSoundEngine.setMusicSuspended(false)
        }
    }

    @Suppress("UNUSED_VARIABLE") val tk = tick
    if (ph == 0 && flash <= 0f) return
    Box(modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width; val h = size.height
            val px = PilotDrift.x * w; val py = pilotY / 100f * h
            val d = dens
            when (ph) {
                1, 2 -> {
                    // The gate stands IN THE TRACK ahead of the pilot, side on like a doorway, and drifts toward the ship as the world scrolls.
                    val tt = if (ph == 1) t else PhaseGate.T_OMEN + t
                    val u = (tt / (PhaseGate.T_OMEN + PhaseGate.T_PULL)).coerceIn(0f, 1f)
                    val open = (if (ph == 1) t / PhaseGate.T_OMEN else 1f).coerceIn(0f, 1f)
                    val cx = (w * 0.97f) + ((px + 40f * d) - w * 0.97f) * (u * u)
                    val cy = py
                    val ry = h * 0.30f * open * (0.45f + 0.55f * u); val rx = ry * 0.30f + 3f * d
                    drawRect(Brush.radialGradient(listOf(Color.Transparent, Color(0xFF7C4DFF).copy(alpha = 0.30f * u)), center = Offset(cx, cy), radius = h * 0.8f))
                    if (ph == 1 && open < 0.35f) {   // the crack that splits first: a jagged vertical line through the spark
                        val cp = Path(); var yy = cy - ry * 3f; var k = 0; cp.moveTo(cx, yy)
                        while (yy < cy + ry * 3f) { yy += h * 0.035f; k += 1; cp.lineTo(cx + (if (k % 2 == 0) 1f else -1f) * 7f * d, yy) }
                        drawPath(cp, Color.White, style = Stroke(width = 2.4f * d, cap = StrokeCap.Round))
                    }
                    if (open > 0.05f) {
                        val tl = Offset(cx - rx, cy - ry); val sz = androidx.compose.ui.geometry.Size(rx * 2f, ry * 2f)
                        val ringArt = artRing
                        if (ringArt != null) {
                            // the painted gate (art): standing side-on in the track, fading in as it opens
                            val ah = ry * 2.25f; val aw = ah * ringArt.width / ringArt.height.toFloat()
                            drawImage(ringArt, dstOffset = androidx.compose.ui.unit.IntOffset((cx - aw / 2f).toInt(), (cy - ah / 2f).toInt()), dstSize = androidx.compose.ui.unit.IntSize(aw.toInt().coerceAtLeast(1), ah.toInt().coerceAtLeast(1)), alpha = kotlin.math.sqrt(open))
                        } else {
                            drawOval(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.95f), Color(0xFF00E5FF).copy(alpha = 0.85f), Color(0xFF3D1A8F).copy(alpha = 0.9f)), center = Offset(cx, cy), radius = ry), topLeft = tl, size = sz)
                            for (i in 0 until 5) {   // the swirl inside: arcs turning around the centre
                                val a0 = (tick * 9f + i * 72f); val shrink = 0.25f + 0.15f * i
                                drawArc(Color.White.copy(alpha = 0.55f), a0, 70f, false, topLeft = Offset(cx - rx * (1f - shrink), cy - ry * (1f - shrink)), size = androidx.compose.ui.geometry.Size(rx * 2f * (1f - shrink), ry * 2f * (1f - shrink)), style = Stroke(width = 2f * d, cap = StrokeCap.Round))
                            }
                            drawOval(Color(0xFF7C4DFF).copy(alpha = 0.35f), topLeft = Offset(cx - rx * 1.25f, cy - ry * 1.06f), size = androidx.compose.ui.geometry.Size(rx * 2.5f, ry * 2.12f), style = Stroke(width = 12f * d))
                            drawOval(Color(0xFF00E5FF), topLeft = tl, size = sz, style = Stroke(width = 5f * d))
                            drawOval(Color.White, topLeft = tl, size = sz, style = Stroke(width = 2f * d))
                        }
                        // things get sucked in: streaks pulled sideways into the gate
                        for (i in 0 until 14) {
                            val ly = cy + ((i / 13f) - 0.5f) * ry * 2.6f; val f = pgFr(i * 0.37f + tt * 2.2f); val sx = cx + (w * 0.5f) * f
                            drawLine(Color.White.copy(alpha = 0.45f * open * (1f - f)), Offset(sx, ly), Offset(sx - 26f * d, ly + (cy - ly) * 0.12f), strokeWidth = 1.6f * d)
                        }
                    }
                }
                3, 4 -> {
                    val fade = 1f
                    val gt = if (ph == 4) PhaseGate.T_GLIMPSE else t
                    // the realm is revealed THROUGH the gate: a circle opening around the ship, and closing back into it at the end
                    val maxR = kotlin.math.sqrt(w * w + h * h)
                    val opening = if (ph == 3) (t / 0.35f).coerceIn(0f, 1f) else 1f - (t / PhaseGate.T_BACK).coerceIn(0f, 1f)
                    val rad = maxR * (1f - (1f - opening) * (1f - opening)) + 4f * d
                    val circle = Path().apply { addOval(androidx.compose.ui.geometry.Rect(Offset(px + 30f * d, py), rad)) }
                    val gp = (gt / PhaseGate.T_GLIMPSE).coerceIn(0f, 1f)
                    clipPath(circle) {
                    val realmArt = when (kind) { 0 -> artR0; 1 -> artR1; 2 -> artR2; else -> artR3 }
                    if (realmArt != null) {
                        drawImage(realmArt, dstSize = androidx.compose.ui.unit.IntSize(w.toInt().coerceAtLeast(1), h.toInt().coerceAtLeast(1)))
                        drawRect(Color.Black.copy(alpha = 0.10f * (1f - gp)))
                    } else
                    when (kind) {
                        0 -> {
                            drawRect(Brush.verticalGradient(listOf(Color(0xFF021B2E), Color(0xFF0A5C73), Color(0xFF021B2E))), alpha = fade)
                            for (i in 0 until 5) {
                                val base = h * (0.62f + i * 0.07f); val path = Path()
                                var x = 0f; path.moveTo(0f, base)
                                while (x <= w) { path.lineTo(x, base + sin(x / (60f * d) + gt * (2f + i)) * 8f * d); x += 14f * d }
                                drawPath(path, Color(0xFF7DF9FF).copy(alpha = 0.30f * fade), style = Stroke(width = 2f * d))
                            }
                            for (i in 0 until 22) {
                                val x = pgFr(i * 0.137f + 0.03f) * w; val y = h * (1.1f - pgFr(i * 0.618f + gt * (0.08f + (i % 5) * 0.03f)) * 1.3f)
                                pgDiamond(x, y, (7f + (i % 4) * 5f) * d, Color(0xFF00E5FF).copy(alpha = 0.55f * fade))
                            }
                        }
                        1 -> {
                            drawRect(Brush.verticalGradient(listOf(Color(0xFF14002B), Color(0xFF4A0A66), Color(0xFF0B0014))), alpha = fade)
                            val bolt = sin(gt * 11f) > 0.9f
                            if (bolt) {
                                val bx = (0.2f + 0.6f * pgFr(gt * 3.7f)) * w; val bp = Path(); bp.moveTo(bx, 0f)
                                var yy = 0f; var xx = bx
                                while (yy < h * 0.7f) { yy += h * 0.09f; xx += (if (((yy / h * 100f).toInt()) % 2 == 0) 1f else -1f) * 22f * d; bp.lineTo(xx, yy) }
                                drawPath(bp, Color.White.copy(alpha = fade), style = Stroke(width = 3f * d, cap = StrokeCap.Round))
                                drawRect(Color.White.copy(alpha = 0.18f * fade))
                            }
                            for (i in 0 until 14) {
                                val bw = w / 14f; val bh = h * (0.12f + 0.25f * pgFr(i * 0.37f + 0.2f))
                                drawRect(Color(0xFF05000D).copy(alpha = fade), topLeft = Offset(i * bw, h - bh), size = androidx.compose.ui.geometry.Size(bw - 2f * d, bh))
                                for (r in 0 until 4) drawRect(Color(0xFFFF4DD2).copy(alpha = 0.5f * fade * (0.5f + 0.5f * sin(gt * 4f + i + r))), topLeft = Offset(i * bw + bw * 0.3f, h - bh + (r * 0.22f + 0.1f) * bh), size = androidx.compose.ui.geometry.Size(bw * 0.18f, 5f * d))
                            }
                            for (i in 0 until 30) {
                                val x = pgFr(i * 0.211f) * w; val y = pgFr(i * 0.47f + gt * 1.8f) * h
                                drawLine(Color(0xFFB388FF).copy(alpha = 0.5f * fade), Offset(x, y), Offset(x - 6f * d, y + 18f * d), strokeWidth = 1.5f * d)
                            }
                        }
                        2 -> {
                            drawRect(Brush.verticalGradient(listOf(Color(0xFF050515), Color(0xFF1B0B3A), Color(0xFF050515))), alpha = fade)
                            for (i in 0 until 9) {
                                val yy = h * (0.5f + 0.5f * pgFr(i / 9f + gt * 0.3f)); val k = (yy - h * 0.5f) / (h * 0.5f)
                                drawLine(Color(0xFF00E5FF).copy(alpha = 0.35f * fade * k), Offset(0f, yy), Offset(w, yy), strokeWidth = 1.5f * d)
                            }
                            for (i in -6..6) drawLine(Color(0xFFFF4DD2).copy(alpha = 0.3f * fade), Offset(w / 2f, h * 0.5f), Offset(w / 2f + i * w * 0.18f, h), strokeWidth = 1.5f * d)
                        }
                        else -> {
                            drawRect(Brush.verticalGradient(listOf(Color(0xFF1A1000), Color(0xFF6B4A00), Color(0xFF1A1000))), alpha = fade)
                            for (i in 0 until PhaseGate.GIFT_GEMS) {
                                val sx = pgFr(i * 0.31f + 0.1f) * w; val sy = pgFr(i * 0.53f + 0.2f) * h * 0.8f
                                val m = (gp * 1.25f - i * 0.12f).coerceIn(0f, 1f)
                                val gx = sx + (px - sx) * m; val gy = sy + (py - sy) * m
                                if (m < 0.98f) pgDiamond(gx, gy, 9f * d, (if (i % 2 == 0) Color(0xFF7DF9FF) else Color(0xFFFFD23F)).copy(alpha = 0.9f * fade))
                            }
                        }
                    }
                    // the ghost of your ship: the pilot is still here, still steerable
                    val ship = Path().apply { moveTo(px + 24f * d, py); lineTo(px - 14f * d, py - 13f * d); lineTo(px - 6f * d, py); lineTo(px - 14f * d, py + 13f * d); close() }
                    drawCircle(Color.White.copy(alpha = 0.18f * fade), radius = 34f * d, center = Offset(px, py))
                    drawPath(ship, Color.White.copy(alpha = 0.95f * fade))
                    drawPath(ship, Color(0xFF00E5FF).copy(alpha = fade), style = Stroke(width = 2f * d))
                    }
                    // the glowing rim of the gate travels with the opening circle
                    drawCircle(Color(0xFF00E5FF).copy(alpha = 0.55f * (1f - 0.6f * gp)), radius = rad, center = Offset(px + 30f * d, py), style = Stroke(width = 10f * d))
                    drawCircle(Color.White.copy(alpha = 0.9f), radius = rad, center = Offset(px + 30f * d, py), style = Stroke(width = 3f * d))
                }
            }
            if (flash > 0f) drawRect(Color.White.copy(alpha = (flash * 0.9f).coerceIn(0f, 1f)))
        }
        if (ph == 3 || ph == 4) {
            val a = if (ph == 4) (1f - t / PhaseGate.T_BACK).coerceIn(0f, 1f) else minOf(1f, t * 4f)
            Text("◈ $title ◈", color = Color.White.copy(alpha = a), fontFamily = PG_MONO, fontWeight = FontWeight.Black, fontSize = 17.sp, textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 110.dp))
            Text(line, color = Color.White.copy(alpha = 0.85f * a), fontFamily = PG_MONO, fontSize = 11.sp, textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 140.dp, start = 24.dp, end = 24.dp))
            if (kind == 2 || kind == 3) {
                val gp = if (ph == 4) 1f else (t / PhaseGate.T_GLIMPSE).coerceIn(0f, 1f)
                Text(icon, fontSize = 64.sp, modifier = Modifier.align(Alignment.CenterStart).offset { IntOffset(((1.15f - gp * 1.5f) * 520f * dens).toInt(), 0) })
            }
        }
    }
}
