package com.neonrush.game.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neonrush.game.AirSupport
import com.neonrush.game.EliteBosses
import com.neonrush.game.VisualTrackElement
import com.neonrush.game.ThreatDirector
import com.neonrush.game.ZoneGenerator
import com.neonrush.game.NeonSoundEngine
import com.neonrush.game.NrUpgrades
import com.neonrush.game.Pause
import com.neonrush.game.PhaseGate
import com.neonrush.game.PilotDrift
import com.neonrush.game.Showcase
import com.neonrush.game.Worlds
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt

private fun pgFr(x: Float) = x - floor(x)
private val PG_MONO = FontFamily.Monospace
private val PG_CRACK = listOf(0.62f to 0.10f, 0.57f to 0.26f, 0.66f to 0.40f, 0.56f to 0.54f, 0.64f to 0.70f, 0.59f to 0.88f)

private fun DrawScope.pgDiamond(cx: Float, cy: Float, r: Float, c: Color, tall: Float = 1.5f) {
    val p = Path().apply { moveTo(cx, cy - r * tall); lineTo(cx + r, cy); lineTo(cx, cy + r * tall); lineTo(cx - r, cy); close() }
    drawPath(p, c)
    drawPath(p, Color.White.copy(alpha = c.alpha * 0.8f), style = Stroke(width = r * 0.12f))
}

private val pgEdgeCache = HashMap<ImageBitmap, Pair<Color, Color>>()
private fun pgEdge(img: ImageBitmap, top: Boolean): Color {
    val c = pgEdgeCache.getOrPut(img) { Pair(pgEdgeRaw(img, true), pgEdgeRaw(img, false)) }
    return if (top) c.first else c.second
}
private fun pgEdgeRaw(img: ImageBitmap, top: Boolean): Color = try {
    val w = img.width; val buf = IntArray(w * 2)
    img.readPixels(buf, startX = 0, startY = if (top) 0 else img.height - 2, width = w, height = 2)
    var r = 0L; var g = 0L; var b = 0L
    for (c in buf) { r += (c shr 16) and 255; g += (c shr 8) and 255; b += c and 255 }
    val n = buf.size * 255f
    Color(r / n, g / n, b / n)
} catch (_: Throwable) { Color(0xFF05030F) }

/** Zone Echo: runs the real zone's hazards (real spawner, real movement rules, 8 steps a second) and the guardian's shots. Hits only flash and shake: nothing counts and nothing hurts. */
private fun pgEchoStep(dt: Float, pilotYPct: Int, spawn: (Int, Int, kotlin.random.Random, Int, Int) -> List<VisualTrackElement>) {
    val px = PilotDrift.x; val py = pilotYPct / 100f
    fun hit(id: String) {
        if (!PhaseGate.echoHitIds.add(id)) return
        PhaseGate.hits += 1; PhaseGate.hitFx = 1f; PhaseGate.banner = "HIT"; PhaseGate.bannerT = 0.7f; PhaseGate.play(7); NeonSoundEngine.triggerHaptic(longArrayOf(0, 45))
    }
    // the guardian arrives for the last seconds and fires
    val bossFrom = PhaseGate.gDur - 2.3f
    if (PhaseGate.gAt >= bossFrom) {
        PhaseGate.bossT = PhaseGate.gAt - bossFrom
        PhaseGate.bossFire -= dt
        if (PhaseGate.bossT > 0.7f && PhaseGate.bossFire <= 0f && PhaseGate.phase == 3) { PhaseGate.bossFire = 0.7f; PhaseGate.ebul.add(floatArrayOf(0.84f, PhaseGate.bossYPct() / 100f)) }
    }
    val it = PhaseGate.ebul.iterator()
    while (it.hasNext()) {
        val b = it.next(); b[0] -= 0.62f * dt
        if (b[0] < -0.1f) { it.remove(); continue }
        if (abs(b[0] - px) < 0.05f && abs(b[1] - py) < 0.06f) { hit("b${System.identityHashCode(b)}"); it.remove() }
    }
    // the hazards, stepped like the real sim
    PhaseGate.echoAcc += dt
    while (PhaseGate.echoAcc >= 0.125f) {
        PhaseGate.echoAcc -= 0.125f
        val tick = ++PhaseGate.echoTick
        val out = ArrayList<VisualTrackElement>()
        for (e in PhaseGate.echoElems) {
            val nx = e.xOffsetFraction - PhaseGate.echoSpeed * 0.018f
            if (nx < -0.1f) continue
            var ay = e.yMatchPos
            if (e.subType == "DRONE" && nx > 0.05f && nx < 0.9f) ay += ((pilotYPct - ay) * 0.06f).toInt()
            val (fx, fy) = try { ThreatDirector.advance(e, nx, ay, pilotYPct, tick, out) } catch (_: Throwable) { Pair(nx, ay) }
            out.add(e.copy(xOffsetFraction = fx, yMatchPos = fy))
            if (e.type == "obstacle" || e.type == "bullet") if (abs(fx - px) < 0.05f && abs(fy / 100f - py) < 0.07f) hit(e.id)
        }
        if (tick % PhaseGate.echoSpacing.toInt().coerceAtLeast(4) == 0 && PhaseGate.gAt < PhaseGate.gDur - 2.4f) {
            try { out.addAll(spawn(PhaseGate.echoSet, tick, PhaseGate.echoRand, pilotYPct, PhaseGate.gZone)) } catch (_: Throwable) { }
        }
        PhaseGate.echoElems = out
    }
}

/** Where the realm opens: around the ship, a little ahead of it. */
private fun pgCenter(w: Float, h: Float, pilotY: Int, d: Float) = Offset(PilotDrift.x * w + 30f * d, pilotY / 100f * h)
private fun pgRadius(w: Float, h: Float, d: Float): Float { val e = PhaseGate.reveal(); val m = sqrt(w * w + h * h); return m * (1f - (1f - e) * (1f - e)) + 4f * d }

/**
 * PHASE GATE, the front layer and the brain. It runs the timers, opens the gate in the track ahead of the pilot (omen, pull), runs the flight inside the realm
 * (collecting gems and passing obstacles, nothing here can hurt the pilot) and draws what belongs IN FRONT of the ship: the gate itself, its rim, banners and text.
 * The realm itself is drawn BEHIND the HUD and the pilot by [PhaseGateWorld] and [PhaseGateObjects]. It never takes touches, so the pilot can still steer.
 */
@Composable
fun PhaseGateOverlay(active: Boolean, tierName: String, bossActive: Boolean, fuel: Int, runs: Int, pilotY: Int, onGems: (Int) -> Unit,
    echoParams: (Int) -> FloatArray, echoSpawn: (Int, Int, kotlin.random.Random, Int, Int) -> List<VisualTrackElement>, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val dens = LocalDensity.current.density
    val bossRef = rememberUpdatedState(bossActive)
    val fuelRef = rememberUpdatedState(fuel)
    val tierRef = rememberUpdatedState(tierName)
    val runsRef = rememberUpdatedState(runs)
    val gemsRef = rememberUpdatedState(onGems)
    val pyRef = rememberUpdatedState(pilotY)
    val epRef = rememberUpdatedState(echoParams)
    val esRef = rememberUpdatedState(echoSpawn)
    var ph by remember { mutableStateOf(0) }
    val artRing = rememberArt("phasegate_ring")

    LaunchedEffect(active) {
        if (!active) { PhaseGate.phase = 0; ph = 0; return@LaunchedEffect }
        PhaseGate.newRun(); ph = 0; PhaseGate.gt = 0f
        var last = 0L
        try {
            while (true) {
                val now = withFrameNanos { it }
                val dt = if (last == 0L) 0.016f else ((now - last) / 1_000_000_000f).coerceAtMost(0.05f)
                last = now
                if (PhaseGate.flash > 0f) PhaseGate.flash = (PhaseGate.flash - dt * 3f).coerceAtLeast(0f)
                if (PhaseGate.bannerT > 0f) PhaseGate.bannerT -= dt
                if (PhaseGate.hitFx > 0f) PhaseGate.hitFx = (PhaseGate.hitFx - dt * 2.2f).coerceAtLeast(0f)
                if (ph == 0) {
                    if (PhaseGate.enabled(ctx) && !Pause.paused && !Pause.covering() && !AirSupport.covering()) PhaseGate.elapsedSec += dt
                    if (PhaseGate.enabled(ctx) && PhaseGate.elapsedSec >= PhaseGate.nextAtSec && PhaseGate.safe(tierRef.value, bossRef.value, fuelRef.value, runsRef.value)) {
                        val kind = PhaseGate.nextKind(ctx)
                        PhaseGate.begin(kind, NrUpgrades.bestZone)
                        when (kind) {
                            0 -> { PhaseGate.gTitle = "CRYSTAL SEA"; PhaseGate.gLine = "Fly the glass sea. Catch the gems. Nothing here can hurt you."; PhaseGate.gIcon = "" }
                            1 -> { PhaseGate.gTitle = "STORM CITY"; PhaseGate.gLine = "Slip past the falling debris. Nothing here can hurt you."; PhaseGate.gIcon = "" }
                            2 -> { val e = Showcase.pick(ctx, Showcase.HANGAR); PhaseGate.gTitle = "IN THE HANGAR"; PhaseGate.gLine = e.title + ": " + e.line; PhaseGate.gIcon = e.icon }
                            3 -> { PhaseGate.gTitle = "A GIFT FROM THE GATE"; PhaseGate.gLine = "Three gems drift to you."; PhaseGate.gIcon = "" }
                            else -> {
                                val z = PhaseGate.gZone; val ahead = (z - NrUpgrades.bestZone).coerceAtLeast(1)
                                val wn = (Worlds.ALL.firstOrNull { z in it.startZone..it.endZone } ?: Worlds.ALL.last()).title
                                val dna = ZoneGenerator.generateZone(z, 42L)
                                val ep = try { epRef.value(z) } catch (_: Throwable) { floatArrayOf(1.6f, 20f, dna.obstacleSetId.toFloat()) }
                                PhaseGate.echoSpeed = ep[0]; PhaseGate.echoSpacing = ep[1]; PhaseGate.echoSet = ep[2].toInt(); PhaseGate.echoRand = kotlin.random.Random(z * 131L + System.nanoTime())
                                PhaseGate.bossName = EliteBosses.forZone(z)?.name ?: "ZONE GUARDIAN"
                                PhaseGate.gTitle = "ZONE $z ECHO"; PhaseGate.gLine = "$wn: ${dna.obstacleSetName}. The real zone, nothing counts. You are $ahead zones away."; PhaseGate.gIcon = ""
                            }
                        }
                        PhaseGate.count += 1; PhaseGate.phase = 1; ph = 1; PhaseGate.gt = 0f
                        NeonSoundEngine.setMusicSuspended(true)
                        PhaseGate.play(1); NeonSoundEngine.triggerHaptic(longArrayOf(0, 15, 60, 25, 60, 40, 60, 60))
                    }
                } else {
                    PhaseGate.gt += dt
                    val t = PhaseGate.gt
                    if (ph == 1 && t >= PhaseGate.T_OMEN) { ph = 2; PhaseGate.phase = 2; PhaseGate.gt = 0f; PhaseGate.play(2) }
                    else if (ph == 2 && t >= PhaseGate.T_PULL) { ph = 3; PhaseGate.phase = 3; PhaseGate.gt = 0f; PhaseGate.gAt = 0f; PhaseGate.flash = 0.45f; PhaseGate.play(3); NeonSoundEngine.triggerHaptic(longArrayOf(0, 90, 30, 40)) }
                    else if (ph == 3) {
                        PhaseGate.gAt = t
                        if (PhaseGate.gKind == 4) pgEchoStep(dt, pyRef.value, esRef.value)
                        // the flight: collect gems, pass obstacles. Nothing here ever hurts the pilot.
                        val px = PilotDrift.x; val py = pyRef.value / 100f
                        for (o in PhaseGate.objs) {
                            if (o.done) continue
                            val x = PhaseGate.ox(o); if (x > 1.2f) continue
                            val y = PhaseGate.oy(o, x, py)
                            if (o.type == 1) {
                                if (abs(x - px) < 0.07f && abs(y - py) < 0.09f) {
                                    o.done = true
                                    if (PhaseGate.gemsGot < PhaseGate.GIFT_GEMS) { PhaseGate.gemsGot += 1; gemsRef.value(1); PhaseGate.banner = "+1 💎"; PhaseGate.bannerT = 0.9f; PhaseGate.play(5); NeonSoundEngine.triggerHaptic(longArrayOf(0, 20)) }
                                } else if (x < px - 0.12f) o.done = true
                            } else {
                                if (abs(x - px) < 0.055f && abs(y - py) < o.hh + 0.05f) { o.done = true; PhaseGate.bumps += 1; PhaseGate.banner = "BUMP"; PhaseGate.bannerT = 0.7f; PhaseGate.play(7); NeonSoundEngine.triggerHaptic(longArrayOf(0, 35)) }
                                else if (x < px - 0.06f) { o.done = true; if (abs(y - py) < o.hh + 0.16f) { PhaseGate.perfects += 1; PhaseGate.banner = "PERFECT"; PhaseGate.bannerT = 0.8f; PhaseGate.play(6) } }
                            }
                        }
                        if (t >= PhaseGate.gDur) {
                            ph = 4; PhaseGate.phase = 4; PhaseGate.gt = 0f; PhaseGate.flash = 0.5f; PhaseGate.play(4); NeonSoundEngine.triggerHaptic(longArrayOf(0, 60, 25, 30))
                            if (PhaseGate.gKind == 4) PhaseGate.gLine = "💎 ${PhaseGate.gemsGot}/${PhaseGate.GIFT_GEMS}   hits taken: ${PhaseGate.hits} (free, nothing counted)" else if (PhaseGate.gKind != 2) PhaseGate.gLine = "💎 ${PhaseGate.gemsGot}/${PhaseGate.GIFT_GEMS}   ✦ ${PhaseGate.perfects} perfect"
                        }
                    }
                    else if (ph == 4) {
                        PhaseGate.gAt = PhaseGate.gDur + t
                        if (PhaseGate.gKind == 4) pgEchoStep(dt, pyRef.value, esRef.value)
                        if (t >= PhaseGate.T_BACK) { ph = 0; PhaseGate.phase = 0; PhaseGate.gt = 0f; NeonSoundEngine.setMusicSuspended(false); PhaseGate.scheduleNext() }
                    }
                }
                PhaseGate.tick += 1
            }
        } finally {
            PhaseGate.phase = 0
            if (ph != 0) NeonSoundEngine.setMusicSuspended(false)
        }
    }

    @Suppress("UNUSED_VARIABLE") val tk = PhaseGate.tick
    if (ph == 0 && PhaseGate.flash <= 0f) return
    val kind = PhaseGate.gKind
    Box(modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width; val h = size.height; val d = dens
            val px = PilotDrift.x * w; val py = pyRef.value / 100f * h
            val t = PhaseGate.gt
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
                    if (ph == 1 && open < 0.35f) {
                        val cp = Path(); var yy = cy - ry * 3f; var k = 0; cp.moveTo(cx, yy)
                        while (yy < cy + ry * 3f) { yy += h * 0.035f; k += 1; cp.lineTo(cx + (if (k % 2 == 0) 1f else -1f) * 7f * d, yy) }
                        drawPath(cp, Color.White, style = Stroke(width = 2.4f * d, cap = StrokeCap.Round))
                    }
                    if (open > 0.05f) {
                        val tl = Offset(cx - rx, cy - ry); val sz = Size(rx * 2f, ry * 2f)
                        val ringArt = artRing
                        if (ringArt != null) {
                            val ah = ry * 2.25f; val aw = ah * ringArt.width / ringArt.height.toFloat()
                            drawImage(ringArt, dstOffset = IntOffset((cx - aw / 2f).toInt(), (cy - ah / 2f).toInt()), dstSize = IntSize(aw.toInt().coerceAtLeast(1), ah.toInt().coerceAtLeast(1)), alpha = sqrt(open))
                        } else {
                            drawOval(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.95f), Color(0xFF00E5FF).copy(alpha = 0.85f), Color(0xFF3D1A8F).copy(alpha = 0.9f)), center = Offset(cx, cy), radius = ry), topLeft = tl, size = sz)
                            drawOval(Color(0xFF00E5FF), topLeft = tl, size = sz, style = Stroke(width = 5f * d))
                            drawOval(Color.White, topLeft = tl, size = sz, style = Stroke(width = 2f * d))
                        }
                        for (i in 0 until 14) {
                            val ly = cy + ((i / 13f) - 0.5f) * ry * 2.6f; val f = pgFr(i * 0.37f + tt * 2.2f); val sx = cx + (w * 0.5f) * f
                            drawLine(Color.White.copy(alpha = 0.45f * open * (1f - f)), Offset(sx, ly), Offset(sx - 26f * d, ly + (cy - ly) * 0.12f), strokeWidth = 1.6f * d)
                        }
                    }
                }
                3, 4 -> {
                    // the glowing rim of the gate travels with the opening circle
                    val c = pgCenter(w, h, pyRef.value, d); val rad = pgRadius(w, h, d)
                    val gp = (PhaseGate.gAt / PhaseGate.gDur).coerceIn(0f, 1f)
                    drawCircle(Color(0xFF00E5FF).copy(alpha = 0.55f * (1f - 0.6f * gp)), radius = rad, center = c, style = Stroke(width = 10f * d))
                    drawCircle(Color.White.copy(alpha = 0.9f), radius = rad, center = c, style = Stroke(width = 3f * d))
                }
            }
            if (PhaseGate.hitFx > 0f && (ph == 3 || ph == 4)) drawRect(Brush.radialGradient(listOf(Color.Transparent, Color(0xFFFF1744).copy(alpha = 0.45f * PhaseGate.hitFx)), center = Offset(w / 2f, h / 2f), radius = h * 0.7f))
            if (PhaseGate.flash > 0f) drawRect(Color.White.copy(alpha = (PhaseGate.flash * 0.9f).coerceIn(0f, 1f)))
        }
        if (ph == 3 || ph == 4) {
            val a = if (ph == 4) (1f - PhaseGate.gt / PhaseGate.T_BACK * 0.6f).coerceIn(0f, 1f) else minOf(1f, PhaseGate.gt * 3f)
            Text("◈ ${PhaseGate.gTitle} ◈", color = Color.White.copy(alpha = a), fontFamily = PG_MONO, fontWeight = FontWeight.Black, fontSize = 18.sp, textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 96.dp))
            Text(PhaseGate.gLine, color = Color.White.copy(alpha = 0.9f * a), fontFamily = PG_MONO, fontSize = 11.sp, textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 126.dp, start = 24.dp, end = 24.dp))
            if (kind != 2) Text("💎 ${PhaseGate.gemsGot}/${PhaseGate.GIFT_GEMS}", color = Color(0xFFFFD23F).copy(alpha = a), fontFamily = PG_MONO, fontWeight = FontWeight.Bold, fontSize = 14.sp,
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 150.dp, end = 20.dp))
            if (PhaseGate.bannerT > 0f && PhaseGate.banner.isNotEmpty()) {
                val bc = if (PhaseGate.banner == "PERFECT") Color(0xFF69F0AE) else if (PhaseGate.banner == "BUMP") Color(0xFFFF8A80) else Color(0xFF7DF9FF)
                Text(PhaseGate.banner, color = bc.copy(alpha = (PhaseGate.bannerT / 0.5f).coerceIn(0f, 1f)), fontFamily = PG_MONO, fontWeight = FontWeight.Black, fontSize = 22.sp,
                    modifier = Modifier.align(Alignment.Center).padding(bottom = 140.dp))
            }
            if (kind == 4 && PhaseGate.bossT > 0f && ph == 3) {
                val wa = (0.55f + 0.45f * abs(sin(PhaseGate.bossT * 6f)))
                Text("⚠ ${PhaseGate.bossName}", color = Color(0xFFFF5252).copy(alpha = wa), fontFamily = PG_MONO, fontWeight = FontWeight.Black, fontSize = 16.sp,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 176.dp))
            }
            if (kind == 2) {
                val gp = if (ph == 4) 1f else (PhaseGate.gAt / PhaseGate.gDur).coerceIn(0f, 1f)
                Text(PhaseGate.gIcon, fontSize = 64.sp, modifier = Modifier.align(Alignment.CenterStart).offset { IntOffset(((1.15f - gp * 1.5f) * 520f * dens).toInt(), 0) })
            }
        }
    }
}

private fun artFor(kind: Int, a0: ImageBitmap?, a1: ImageBitmap?, a2: ImageBitmap?, a3: ImageBitmap?): ImageBitmap? = when (kind) { 0 -> a0; 1 -> a1; 2 -> a2; 3 -> a3; else -> null }

/** The realm itself, drawn BEHIND the HUD and the pilot: a full-screen panorama that drifts, a foreground strip that rushes past, all shown through the opening gate. */
@Composable
fun PhaseGateWorld(pilotY: Int, modifier: Modifier = Modifier) {
    val dens = LocalDensity.current.density
    val p0 = rememberArt("phasegate_pan0"); val p1 = rememberArt("phasegate_pan1"); val p2 = rememberArt("phasegate_pan2"); val p3 = rememberArt("phasegate_pan3")
    val f0 = rememberArt("phasegate_fg0"); val f1 = rememberArt("phasegate_fg1"); val f2 = rememberArt("phasegate_fg2"); val f3 = rememberArt("phasegate_fg3")
    val startFrac = remember { floatArrayOf(0.15f, 0.45f, 0.25f, 0.55f) }
    @Suppress("UNUSED_VARIABLE") val tk = PhaseGate.tick
    if (!PhaseGate.inGlimpse() || PhaseGate.gKind == 4) return
    Canvas(modifier.fillMaxSize()) {
        val w = size.width; val h = size.height; val d = dens
        val kind = PhaseGate.gKind; val at = PhaseGate.gAt
        val circle = Path().apply { addOval(Rect(pgCenter(w, h, pilotY, d), pgRadius(w, h, d))) }
        clipPath(circle) {
            val pan = artFor(kind, p0, p1, p2, p3)
            val top = if (pan != null) pgEdge(pan, true) else Color(0xFF05030F)
            val bot = if (pan != null) pgEdge(pan, false) else Color(0xFF05030F)
            drawRect(Brush.verticalGradient(listOf(top, bot)))
            if (pan != null) {
                val dh = h * 0.66f; val y0 = h * 0.17f
                val srcH = pan.height; val srcW = ((w / (dh * pan.width / pan.height)) * pan.width).toInt().coerceIn(8, pan.width)
                val room = (pan.width - srcW).coerceAtLeast(0)
                val x0 = (startFrac[kind.coerceIn(0, 3)] * room + (at / PhaseGate.gDur) * 0.30f * room).toInt().coerceIn(0, room)
                val zoom = 1f + 0.07f * (at / PhaseGate.gDur)
                withTransform({ scale(zoom, zoom, pivot = Offset(w / 2f, h / 2f)) }) {
                    drawImage(pan, srcOffset = IntOffset(x0, 0), srcSize = IntSize(srcW, srcH), dstOffset = IntOffset(0, y0.toInt()), dstSize = IntSize(w.toInt().coerceAtLeast(1), dh.toInt().coerceAtLeast(1)))
                }
                drawRect(Brush.verticalGradient(0f to top, 1f to Color.Transparent, startY = y0 - h * 0.06f, endY = y0 + h * 0.07f), topLeft = Offset(0f, y0 - h * 0.06f), size = Size(w, h * 0.13f))
                drawRect(Brush.verticalGradient(0f to Color.Transparent, 1f to bot, startY = y0 + dh - h * 0.07f, endY = y0 + dh + h * 0.06f), topLeft = Offset(0f, y0 + dh - h * 0.07f), size = Size(w, h * 0.13f))
            }
            val fg = when (kind) { 0 -> f0; 1 -> f1; 2 -> f2; else -> f3 }
            if (fg != null) {
                val fh = h * 0.20f; val fw = fh * fg.width / fg.height
                val sc = (at * w * 0.55f) % fw
                var x = -sc
                while (x < w) { drawImage(fg, dstOffset = IntOffset(x.toInt(), (h - fh).toInt()), dstSize = IntSize(fw.toInt() + 2, fh.toInt())); x += fw }
            }
        }
    }
}

/** Obstacles, gems, weather and speed streaks of the realm, also behind the pilot (but above the realm), and clipped to the same opening gate. */
@Composable
fun PhaseGateObjects(pilotY: Int, modifier: Modifier = Modifier) {
    val dens = LocalDensity.current.density
    @Suppress("UNUSED_VARIABLE") val tk = PhaseGate.tick
    if (!PhaseGate.inGlimpse()) return
    Canvas(modifier.fillMaxSize()) {
        val w = size.width; val h = size.height; val d = dens
        val kind = PhaseGate.gKind; val at = PhaseGate.gAt
        val py = pilotY / 100f
        val circle = Path().apply { addOval(Rect(pgCenter(w, h, pilotY, d), pgRadius(w, h, d))) }
        clipPath(circle) {
            // speed streaks: the world rushing past
            for (i in 0 until 9) {
                val y = pgFr(i * 0.171f + 0.05f) * h; val f = pgFr(i * 0.37f + at * (0.9f + (i % 3) * 0.4f)); val x = w * (1.1f - f * 1.3f)
                drawLine(Color.White.copy(alpha = 0.16f), Offset(x, y), Offset(x + 90f * d, y), strokeWidth = 1.4f * d)
            }
            when (kind) {
                0 -> for (i in 0 until 18) {   // rising glints and glass
                    val x = pgFr(i * 0.137f + 0.03f) * w; val y = h * (1.05f - pgFr(i * 0.618f + at * (0.05f + (i % 5) * 0.02f)) * 1.1f)
                    drawCircle(Color(0xFF9FFFF2).copy(alpha = 0.55f), radius = (1.5f + (i % 3)) * d, center = Offset(x, y))
                }
                1 -> {   // rain and lightning
                    for (i in 0 until 38) {
                        val x = pgFr(i * 0.211f) * w; val y = pgFr(i * 0.47f + at * 1.9f) * h
                        drawLine(Color(0xFFB388FF).copy(alpha = 0.45f), Offset(x, y), Offset(x - 7f * d, y + 22f * d), strokeWidth = 1.4f * d)
                    }
                    var fl = 0f
                    for (te in floatArrayOf(0.9f, 2.6f, 4.3f, 5.5f)) { val a = at - te; if (a > 0f) fl = maxOf(fl, exp(-a * 7f)) }
                    if (fl > 0.05f) {
                        drawRect(Color.White.copy(alpha = 0.30f * fl))
                        val bx = (0.25f + 0.5f * pgFr(floor(at * 1.7f) * 0.37f)) * w; val bp = Path(); bp.moveTo(bx, 0f)
                        var yy = 0f; var xx = bx; var k = 0
                        while (yy < h * 0.55f) { yy += h * 0.07f; k += 1; xx += (if (k % 2 == 0) 1f else -1f) * 26f * d; bp.lineTo(xx, yy) }
                        drawPath(bp, Color.White.copy(alpha = fl), style = Stroke(width = 3.2f * d, cap = StrokeCap.Round))
                    }
                }
                2 -> {   // a slow light sweep and dust
                    val sx = w * pgFr(at * 0.18f)
                    drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Color(0xFF00E5FF).copy(alpha = 0.10f), Color.Transparent), startX = sx - 120f * d, endX = sx + 120f * d))
                    for (i in 0 until 24) drawCircle(Color.White.copy(alpha = 0.3f), radius = 1.4f * d, center = Offset(pgFr(i * 0.31f + at * 0.04f) * w, pgFr(i * 0.57f + at * 0.03f) * h))
                }
                3 -> for (i in 0 until 40) {   // drifting sparkles
                    val x = pgFr(i * 0.173f + at * 0.06f) * w; val y = pgFr(i * 0.41f - at * 0.04f) * h
                    drawCircle(Color(0xFFFFE9A8).copy(alpha = 0.35f + 0.4f * abs(sin(at * 3f + i))), radius = (1.5f + (i % 3)) * d, center = Offset(x, y))
                }
                else -> {}
            }
            // obstacles and gems
            for (o in PhaseGate.objs) {
                if (o.done && PhaseGate.ox(o) < -0.2f) continue
                val x = PhaseGate.ox(o); if (x > 1.2f || x < -0.2f) continue
                val y = PhaseGate.oy(o, x, py) * h; val cx = x * w
                if (o.type == 1) {
                    if (o.done) continue
                    val pulse = 0.85f + 0.15f * sin(at * 8f + o.t0)
                    drawCircle(Color(0xFFFFD23F).copy(alpha = 0.25f), radius = 22f * d * pulse, center = Offset(cx, y))
                    pgDiamond(cx, y, 9f * d * pulse, Color(0xFF7DF9FF).copy(alpha = 0.95f))
                } else {
                    val hh = o.hh * h
                    when (kind) {
                        0 -> { drawCircle(Color(0xFF00E5FF).copy(alpha = 0.18f), radius = hh * 0.9f, center = Offset(cx, y)); pgDiamond(cx, y, hh * 0.38f, Color(0xFF55EAFF).copy(alpha = 0.92f), tall = 2.6f) }
                        1 -> rotate(degrees = (at + o.t0) * 70f, pivot = Offset(cx, y)) {
                            drawRect(Color(0xFF0A0516), topLeft = Offset(cx - hh * 1.4f, y - hh * 0.5f), size = Size(hh * 2.8f, hh))
                            drawRect(Color(0xFFFF4DD2), topLeft = Offset(cx - hh * 1.4f, y - hh * 0.5f), size = Size(hh * 2.8f, hh), style = Stroke(width = 2.5f * d))
                        }
                        else -> { drawCircle(Color(0xFFFF5252).copy(alpha = 0.22f), radius = hh * 1.5f, center = Offset(cx, y)); pgDiamond(cx, y, hh * 0.9f, Color(0xFFFF8A65).copy(alpha = 0.95f), tall = 1.2f) }
                    }
                }
            }
        }
    }
}
