package com.neonrush.game.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.neonrush.game.RealmRules
import com.neonrush.game.VisualTrackElement
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** Drawing for the zone 240+ realms and the Echo Rival. Names start with "rl" to stay unique in the package. */

private val RL_CYAN = Color(0xFF00E5FF)
private val RL_MAG = Color(0xFFFF2BD6)

fun rlSideColor(side: Int): Color = if (side == 0) RL_CYAN else RL_MAG

/**
 * MIRROR: the screen is washed in your side's colour; a flip throws a ripple across it. Obstacles get a ring in their side colour:
 * a bright pulsing ring when they are live for you, a faint dashed one when you phase through them.
 */
fun DrawScope.drawMirrorRealm(cw: Float, ch: Float, t: Float, tick: Int, side: Int, flipTick: Int, pilot: Offset, elems: List<VisualTrackElement>) {
    val my = rlSideColor(side)
    // side colour at the edges of the screen
    drawRect(Brush.horizontalGradient(listOf(my.copy(alpha = 0.16f), Color.Transparent, my.copy(alpha = 0.10f))), size = Size(cw, ch), blendMode = BlendMode.Plus)
    drawRect(Brush.verticalGradient(listOf(my.copy(alpha = 0.12f), Color.Transparent, Color.Transparent, my.copy(alpha = 0.12f))), size = Size(cw, ch), blendMode = BlendMode.Plus)
    // the mirror seam: a shimmering line on the opposite side's colour
    val other = rlSideColor(1 - side)
    for (k in 0 until 6) {
        val y = ch * ((k + ((t * 0.25f) % 1f)) / 6f)
        drawLine(other.copy(alpha = 0.10f), Offset(0f, y), Offset(cw, y), strokeWidth = ch * 0.002f)
    }
    // flip ripple
    val age = tick - flipTick
    if (age in 0..9) {
        val f = age / 9f
        drawCircle(my.copy(alpha = 0.7f * (1f - f)), radius = cw * (0.1f + 1.2f * f), center = pilot, style = Stroke(width = ch * 0.012f * (1f - f)))
        drawRect(Color.White.copy(alpha = 0.18f * (1f - f)), size = Size(cw, ch), blendMode = BlendMode.Plus)
    }
    // pilot halo in the side colour
    fxGlow(pilot, ch * 0.16f, my, 0.35f + 0.1f * sin(t * 5f))
    drawCircle(my.copy(alpha = 0.8f), radius = ch * 0.075f, center = pilot, style = Stroke(width = ch * 0.004f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(ch * 0.02f, ch * 0.014f), t * 40f)))
    // obstacle rings
    for (e in elems) {
        if (e.type != "obstacle" || e.xOffsetFraction > 1.15f || e.xOffsetFraction < -0.05f) continue
        val s = RealmRules.sideOf(e)
        val c = rlSideColor(s)
        val p = Offset(cw * e.xOffsetFraction, ch * (e.yMatchPos / 100f))
        val r = ch * 0.07f
        if (s == side) {
            val pulse = 0.6f + 0.4f * sin(t * 8f + e.id.hashCode())
            drawCircle(c.copy(alpha = 0.85f * pulse), radius = r, center = p, style = Stroke(width = ch * 0.006f))
            fxGlow(p, r * 1.4f, c, 0.28f)
        } else {
            drawCircle(c.copy(alpha = 0.35f), radius = r, center = p, style = Stroke(width = ch * 0.003f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(ch * 0.016f, ch * 0.016f), t * 30f)))
        }
    }
}

/** PULSE: every beat the screen breathes: rings from the pilot, edge flash, and a bar counter (4 beats, the last one is the drop). */
fun DrawScope.drawPulseRealm(cw: Float, ch: Float, t: Float, tick: Int, pilot: Offset) {
    val beatLen = RealmRules.PULSE_BEAT
    val phase = (tick % beatLen) / beatLen.toFloat()
    val beat = (tick / beatLen) % 4
    val hit = (1f - phase) * (1f - phase)
    val col = if (beat == 3) Color(0xFFFFD54F) else Color(0xFF7C4DFF)
    drawRect(Brush.radialGradient(listOf(Color.Transparent, col.copy(alpha = 0.22f * hit)), center = Offset(cw / 2f, ch / 2f), radius = ch * 0.9f), size = Size(cw, ch), blendMode = BlendMode.Plus)
    drawCircle(col.copy(alpha = 0.6f * (1f - phase)), radius = ch * (0.06f + 0.5f * phase), center = pilot, style = Stroke(width = ch * 0.006f * (1f - phase)))
    // horizontal beat lines streaming through
    for (k in 0 until 5) {
        val y = ch * (0.15f + 0.17f * k)
        val w = cw * (0.3f + 0.7f * hit)
        drawLine(col.copy(alpha = 0.18f * hit + 0.04f), Offset(cw / 2f - w / 2f, y), Offset(cw / 2f + w / 2f, y), strokeWidth = ch * 0.002f, cap = StrokeCap.Round, blendMode = BlendMode.Plus)
    }
    // bar counter: four dots, the fourth is gold (the drop)
    for (k in 0 until 4) {
        val on = k <= beat
        val c = if (k == 3) Color(0xFFFFD54F) else Color(0xFF7C4DFF)
        val p = Offset(cw / 2f + (k - 1.5f) * ch * 0.035f, ch * 0.075f)
        drawCircle(c.copy(alpha = if (on) 0.95f else 0.25f), radius = ch * (if (k == beat) 0.012f + 0.006f * hit else 0.008f), center = p)
    }
}

/**
 * ECHO RIVAL: the pilot's own sprite, turned to face you, in violet shadow. A trail of faint copies shows the lanes it has been in
 * (the lanes you were in), so the shots it fires are readable.
 */
fun DrawScope.drawEchoRival(img: ImageBitmap, bossX: Float, bossY: Float, h: Float, t: Float, tick: Int, hp: Float, ch: Float) {
    val w = h * (img.width.toFloat() / img.height.toFloat())
    val vio = Color(0xFF7C4DFF)
    fxGlow(Offset(bossX, bossY), h * 0.9f, vio, 0.28f + 0.1f * sin(t * 3f))
    // trail of earlier lanes (what you did)
    for (k in 1..6) {
        val ty = ch * (RealmRules.echoY(tick - k * 3) / 100f)
        drawSprite(img, Offset(bossX + k * h * 0.12f, ty), w * 0.8f, h * 0.8f, vio, 0.10f)
    }
    // the rival itself, flipped to face left, jittering when it is hurt
    val jit = if (hp < 0.3f) sin(t * 47f) * h * 0.01f else 0f
    withTransform({ scale(-1f, 1f, Offset(bossX, bossY)) }) {
        drawSprite(img, Offset(bossX + jit, bossY), w, h, vio, 0.9f)
    }
    // glowing eyes line and a crackle of glitch bars
    val step = (t * 9f).toInt()
    for (k in 0 until 4) if (fxRnd(step, k) > 0.5f) {
        val y = bossY - h * 0.45f + fxRnd(step, k + 5) * h * 0.9f
        drawRect(vio.copy(alpha = 0.5f), Offset(bossX - w * 0.4f + fxRnd(k, step) * w * 0.3f, y), Size(w * 0.5f, h * 0.012f), blendMode = BlendMode.Plus)
    }
    fxSpark(Offset(bossX - w * 0.05f, bossY - h * 0.33f), h * 0.07f, Color.White, 0.7f + 0.3f * sin(t * 6f))
}

private fun DrawScope.drawSprite(img: ImageBitmap, c: Offset, w: Float, h: Float, tint: Color, alpha: Float) {
    drawImage(
        image = img,
        dstOffset = IntOffset((c.x - w / 2f).roundToInt(), (c.y - h / 2f).roundToInt()),
        dstSize = IntSize(w.roundToInt(), h.roundToInt()),
        alpha = alpha,
        colorFilter = ColorFilter.tint(tint, BlendMode.SrcIn)
    )
}

private val RL_SWAP_RB = ColorFilter.colorMatrix(ColorMatrix(floatArrayOf(
    0f, 0f, 1f, 0f, 0f,
    0f, 1f, 0f, 0f, 0f,
    1f, 0f, 0f, 0f, 0f,
    0f, 0f, 0f, 1f, 0f)))

/**
 * GRAVITY: the uploaded vortex sprite, spinning. Blue wells pull (they spin faster the closer you get), red wells (the same sprite with
 * blue and red swapped) push and spin backwards. A bent tether links the pilot to any well that has hold of it.
 */
fun DrawScope.drawGravityRealm(cw: Float, ch: Float, t: Float, pilot: Offset, wells: List<RealmRules.Well>, skimming: Boolean, img: ImageBitmap) {
    drawRect(Brush.radialGradient(listOf(Color.Transparent, Color(0xFF1A237E).copy(alpha = 0.18f)), center = Offset(cw / 2f, ch / 2f), radius = ch * 0.95f), size = Size(cw, ch), blendMode = BlendMode.Plus)
    for (w in wells) {
        if (w.x > 1.4f || w.x < -0.3f) continue
        val c = Offset(cw * w.x, ch * (w.y / 100f))
        val col = if (w.push) Color(0xFFFF5A36) else Color(0xFF40C4FF)
        val d = kotlin.math.hypot((c.x - pilot.x).toDouble(), (c.y - pilot.y).toDouble()).toFloat()
        val near = (1f - d / (ch * 0.7f)).coerceIn(0f, 1f)
        val size = ch * (if (w.push) 0.46f else 0.54f)
        val deg = (if (w.push) -1f else 1f) * (t * (80f + 380f * near))
        fxGlow(c, size * 0.75f, col, 0.18f + 0.22f * near)
        withTransform({ rotate(deg, c) }) {
            drawImage(
                image = img,
                dstOffset = IntOffset((c.x - size / 2f).roundToInt(), (c.y - size / 2f).roundToInt()),
                dstSize = IntSize(size.roundToInt(), size.roundToInt()),
                alpha = 0.93f,
                colorFilter = if (w.push) RL_SWAP_RB else null
            )
        }
        // field edge: where it starts to take hold
        drawCircle(col.copy(alpha = 0.22f + 0.2f * near), radius = ch * 0.36f, center = c, style = Stroke(width = ch * 0.003f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(ch * 0.018f, ch * 0.02f), t * 50f * (if (w.push) -1f else 1f))))
        // tether bending toward / away from the well
        if (d < ch * 0.5f) {
            val mid = Offset((pilot.x + c.x) / 2f, (pilot.y + c.y) / 2f + ch * 0.05f * sin(t * 9f))
            val path = androidx.compose.ui.graphics.Path().apply { moveTo(pilot.x, pilot.y); quadraticBezierTo(mid.x, mid.y, c.x, c.y) }
            drawPath(path, col.copy(alpha = 0.55f * near + 0.15f), style = Stroke(width = ch * 0.004f, cap = StrokeCap.Round), blendMode = BlendMode.Plus)
        }
    }
    if (skimming) {
        fxGlow(pilot, ch * 0.2f, Color(0xFF40C4FF), 0.45f)
        fxSpark(pilot, ch * 0.09f, Color.White, 0.8f + 0.2f * sin(t * 20f))
    }
}

/**
 * BLACKOUT: a black veil with a lantern hole ahead of the pilot. Every PING_EVERY ticks a sonar ring sweeps out and the veil lifts, then
 * closes again. Obstacles and shots stay visible as faint blips so nothing is unfair. Deeper zones shrink the lantern.
 */
fun DrawScope.drawBlackoutRealm(cw: Float, ch: Float, t: Float, tick: Int, zone: Int, pilot: Offset, elems: List<VisualTrackElement>) {
    val phase = tick % RealmRules.PING_EVERY
    val reveal = if (phase < RealmRules.PING_LEN) 1f - phase / RealmRules.PING_LEN.toFloat() else 0f
    val deep = ((zone - 420) / 60f).coerceIn(0f, 1f)
    val lanternR = ch * (0.52f - 0.14f * deep)
    val lanternC = Offset(pilot.x + ch * 0.18f, pilot.y)
    val veil = 0.95f * (1f - reveal)
    if (veil > 0.01f) {
        drawRect(
            Brush.radialGradient(0f to Color.Transparent, 0.45f to Color.Transparent, 1f to Color.Black.copy(alpha = veil), center = lanternC, radius = lanternR),
            size = Size(cw, ch)
        )
    }
    // sonar ring
    if (phase < RealmRules.PING_LEN) {
        val f = phase / RealmRules.PING_LEN.toFloat()
        drawCircle(Color(0xFF69F0AE).copy(alpha = 0.7f * (1f - f)), radius = cw * 1.1f * f, center = pilot, style = Stroke(width = ch * 0.008f * (1f - f)))
    }
    // faint blips for everything dangerous, so the dark is scary but fair
    val blipA = 0.45f * (1f - reveal)
    if (blipA > 0.02f) {
        for (e in elems) {
            if (e.type != "obstacle" && e.type != "bullet" && e.type != "hazard") continue
            if (e.xOffsetFraction > 1.1f || e.xOffsetFraction < 0.05f) continue
            val p = Offset(cw * e.xOffsetFraction, ch * (e.yMatchPos / 100f))
            val c = if (e.type == "bullet") Color(0xFFFF5252) else Color(0xFFB0BEC5)
            drawCircle(c.copy(alpha = blipA * (0.7f + 0.3f * sin(t * 6f + e.id.hashCode()))), radius = ch * 0.011f, center = p)
        }
    }
    // lantern glow on the pilot
    fxGlow(pilot, ch * 0.12f, Color(0xFFFFF59D), 0.25f * (1f - reveal) + 0.05f)
}

/**
 * SHATTER: every obstacle tile of a glass wall is drawn as a pane of prism glass. The one gap glows with chevrons. Once a pane is
 * past the pilot it bursts into flying shards. Cracks spread across the screen the deeper you are in the realm.
 */
fun DrawScope.drawShatterRealm(cw: Float, ch: Float, t: Float, tick: Int, zone: Int, pilot: Offset, elems: List<VisualTrackElement>) {
    val cyan = Color(0xFF7DF9FF)
    val mag = Color(0xFFFF4FD8)
    drawRect(Brush.horizontalGradient(listOf(cyan.copy(alpha = 0.10f), Color.Transparent, mag.copy(alpha = 0.10f))), size = Size(cw, ch), blendMode = BlendMode.Plus)
    // cracks: more of them the deeper you are, flaring while a wall passes
    val depth = ((zone - 270) / 30f).coerceIn(0f, 1f)
    var flare = 0f
    for (e in elems) if (e.subType == "GLASS" && e.xOffsetFraction in 0.12f..0.3f) flare = 0.5f
    val n = 3 + (9 * depth).toInt()
    for (k in 0 until n) {
        var p = Offset(fxRnd(k, 1) * cw, if (fxRnd(k, 2) < 0.5f) 0f else ch)
        val a = 0.10f + 0.10f * depth + flare * 0.25f
        for (j in 0 until 5) {
            val q = Offset(p.x + (fxRnd(k, 10 + j) - 0.5f) * cw * 0.16f, p.y + (if (p.y < ch / 2f) 1f else -1f) * ch * (0.06f + 0.10f * fxRnd(k, 20 + j)))
            drawLine(Color.White.copy(alpha = a), p, q, strokeWidth = ch * 0.0016f, cap = StrokeCap.Round)
            p = q
        }
    }
    for (e in elems) {
        if (e.subType != "GLASS") continue
        val x = cw * e.xOffsetFraction
        if (x > cw * 1.1f || x < -cw * 0.1f) continue
        val y = ch * (e.yMatchPos / 100f)
        val parts = e.id.split("_")
        val gapC = parts.getOrNull(2)?.toIntOrNull() ?: 50
        val idx = parts.getOrNull(3)?.toIntOrNull() ?: 0
        val tw = cw * 0.05f
        val th = ch * 0.148f
        val gone = ((0.2f - e.xOffsetFraction) / 0.10f).coerceIn(0f, 1f)
        if (gone <= 0f) {
            val tl = Offset(x - tw / 2f, y - th / 2f)
            drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.30f), cyan.copy(alpha = 0.16f), mag.copy(alpha = 0.14f)), startY = tl.y, endY = tl.y + th), tl, Size(tw, th))
            drawRect(cyan.copy(alpha = 0.85f), tl, Size(tw, th), style = Stroke(width = ch * 0.003f))
            drawRect(mag.copy(alpha = 0.45f), Offset(tl.x + ch * 0.003f, tl.y + ch * 0.003f), Size(tw, th), style = Stroke(width = ch * 0.0015f))
            val gl = ((t * 0.7f + fxRnd(idx, gapC)) % 1f)
            drawLine(Color.White.copy(alpha = 0.7f * (1f - gl)), Offset(tl.x, tl.y + th * gl), Offset(tl.x + tw, tl.y + th * (gl - 0.4f).coerceAtLeast(0f)), strokeWidth = ch * 0.002f)
        } else if (gone < 1f) {
            for (k in 0 until 5) {
                val ang = (fxRnd(idx, k) * 6.283f)
                val d = gone * ch * (0.06f + 0.16f * fxRnd(k, idx))
                val c = Offset(x + kotlin.math.cos(ang) * d * 0.7f - gone * cw * 0.02f, y + sin(ang) * d)
                val r = ch * 0.03f * (1f - gone * 0.5f)
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(c.x, c.y - r); lineTo(c.x + r * 0.8f, c.y + r * 0.6f); lineTo(c.x - r * 0.7f, c.y + r * 0.5f); close()
                }
                drawPath(path, cyan.copy(alpha = 0.55f * (1f - gone)))
                drawPath(path, Color.White.copy(alpha = 0.8f * (1f - gone)), style = Stroke(width = ch * 0.002f))
            }
        }
        // the gap: shown once per wall, on its first tile
        if (idx == 0 && e.xOffsetFraction > 0.2f) {
            val gy = ch * (gapC / 100f)
            fxGlow(Offset(x, gy), ch * 0.2f, cyan, 0.28f + 0.1f * sin(t * 6f))
            for (c in 0 until 3) {
                val ox = x - cw * 0.035f * (c + 1) - ((t * 0.9f) % 1f) * cw * 0.02f
                val aa = 0.7f * (1f - c / 3f)
                drawLine(Color.White.copy(alpha = aa), Offset(ox + cw * 0.01f, gy - ch * 0.03f), Offset(ox, gy), strokeWidth = ch * 0.004f, cap = StrokeCap.Round)
                drawLine(Color.White.copy(alpha = aa), Offset(ox, gy), Offset(ox + cw * 0.01f, gy + ch * 0.03f), strokeWidth = ch * 0.004f, cap = StrokeCap.Round)
            }
        }
    }
    fxGlow(pilot, ch * 0.10f, cyan, 0.18f)
}

/**
 * STORM: rain, drifting thunderclouds and a violet veil. Each strike shows an amber warning band on its lane (it fills up over the
 * warning time, with a hazard chevron strip at the left edge), then the bolt crosses the whole screen. During the calm "eye" the
 * veil lifts and a golden glow settles on the pilot.
 */
fun DrawScope.drawStormRealm(cw: Float, ch: Float, t: Float, tick: Int, pilot: Offset) {
    val calm = RealmRules.calm
    val veil = if (calm) 0.04f else 0.16f
    drawRect(Brush.verticalGradient(listOf(Color(0xFF2A1B5E).copy(alpha = veil + 0.10f), Color.Transparent, Color(0xFF0B1230).copy(alpha = veil))), size = Size(cw, ch))
    // clouds along the top
    for (k in 0 until 5) {
        val cx = ((k * 0.27f - t * 0.02f * (1f + k * 0.2f)) % 1.4f + 1.4f) % 1.4f * cw - cw * 0.2f
        val r = ch * (0.20f + 0.05f * fxRnd(k, 3))
        fxGlow(Offset(cx, -ch * 0.02f), r, Color(0xFF6A4FD0), if (calm) 0.10f else 0.30f)
    }
    // rain
    if (!calm) {
        for (k in 0 until 46) {
            val sp = 0.9f + fxRnd(k, 5)
            val ry = ((t * sp * 1.6f + fxRnd(k, 6)) % 1f) * ch
            val rx = (fxRnd(k, 7) * cw * 1.2f - ry * 0.18f)
            drawLine(Color(0xFFB3C6FF).copy(alpha = 0.22f), Offset(rx, ry), Offset(rx - ch * 0.012f, ry + ch * 0.045f), strokeWidth = ch * 0.0016f)
        }
    }
    // strikes
    for (s in RealmRules.strikes) {
        val age = tick - s.t0
        if (age < 0) continue // stale strike from an earlier run (tick restarted): negative age made alpha < 0 and crashed
        val y = ch * (s.lane / 100f)
        val half = ch * 0.13f
        if (age < RealmRules.STORM_WARN) {
            val f = ((age + 1f) / RealmRules.STORM_WARN).coerceIn(0f, 1f)
            val pulse = 0.5f + 0.5f * sin(t * (10f + 14f * f))
            val amber = Color(0xFFFFC400)
            drawRect(amber.copy(alpha = (0.08f + 0.20f * f) * (0.6f + 0.4f * pulse)), Offset(0f, y - half), Size(cw, half * 2f), blendMode = BlendMode.Plus)
            drawLine(amber.copy(alpha = 0.55f + 0.4f * f), Offset(0f, y - half), Offset(cw, y - half), strokeWidth = ch * 0.003f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(ch * 0.03f, ch * 0.02f), t * 90f))
            drawLine(amber.copy(alpha = 0.55f + 0.4f * f), Offset(0f, y + half), Offset(cw, y + half), strokeWidth = ch * 0.003f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(ch * 0.03f, ch * 0.02f), -t * 90f))
            // charge building at the right edge: tiny sparks gathering
            for (k in 0 until 4) fxSpark(Offset(cw * (0.96f - 0.03f * k), y + (fxRnd(k, tick) - 0.5f) * half), ch * 0.02f * f, Color.White, 0.8f * f)
            // warning mark at the left edge
            fxSpark(Offset(cw * 0.04f, y), ch * (0.03f + 0.02f * pulse), Color(0xFFFF5252), 0.9f)
        } else if (age <= RealmRules.STORM_WARN + 2) {
            val k = age - RealmRules.STORM_WARN
            val a = if (k == 0) 1f else if (k == 1) 0.85f else 0.35f
            drawRect(Color.White.copy(alpha = 0.22f * a), size = Size(cw, ch), blendMode = BlendMode.Plus)
            drawRect(Color(0xFFB388FF).copy(alpha = 0.32f * a), Offset(0f, y - half), Size(cw, half * 2f), blendMode = BlendMode.Plus)
            val segs = 3
            for (g in 0 until segs) {
                val x0 = cw * (1f - g / segs.toFloat())
                val x1 = cw * (1f - (g + 1) / segs.toFloat())
                val yy0 = y + (fxRnd(tick, g) - 0.5f) * ch * 0.05f
                val yy1 = y + (fxRnd(tick, g + 1) - 0.5f) * ch * 0.05f
                fxBolt(Offset(x0, yy0), Offset(x1, yy1), tick * 7 + g, Color(0xFFB388FF), ch * 0.03f, 0.5f * a, 0.06f)
                fxBolt(Offset(x0, yy0), Offset(x1, yy1), tick * 7 + g, Color(0xFF80D8FF), ch * 0.012f, 0.9f * a, 0.06f)
                fxBolt(Offset(x0, yy0), Offset(x1, yy1), tick * 7 + g, Color.White, ch * 0.005f, a, 0.06f)
            }
            fxGlow(Offset(cw * 0.12f, y), ch * 0.3f, Color.White, 0.5f * a)
        }
    }
    if (calm) {
        fxGlow(pilot, ch * 0.22f, Color(0xFFFFD54F), 0.28f + 0.08f * sin(t * 3f))
        drawCircle(Color(0xFFFFD54F).copy(alpha = 0.35f), radius = ch * 0.14f, center = pilot, style = Stroke(width = ch * 0.003f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(ch * 0.02f, ch * 0.02f), t * 30f)))
    }
}

/** Small caption for the realm you are in. */
fun realmCaption(zone: Int): String? = when (RealmRules.realmFor(zone)) {
    com.neonrush.game.Realm.MIRROR -> "MIRROR REALM  ·  TAP TO SWAP SIDE"
    com.neonrush.game.Realm.GRAVITY -> "GRAVITY REALM  \u00b7  SKIM THE BLUE  \u00b7  FLEE THE RED"
    com.neonrush.game.Realm.BLACKOUT -> "BLACKOUT REALM  \u00b7  LIGHTS OUT  \u00b7  WATCH FOR THE PING"
    com.neonrush.game.Realm.SHATTER -> "SHATTER REALM  \u00b7  FLY THROUGH THE GAP"
    com.neonrush.game.Realm.STORM -> if (RealmRules.calm) "THE EYE  \u00b7  CALM BEFORE THE STORM" else "STORM REALM  \u00b7  LEAVE THE GLOWING LANE"
    com.neonrush.game.Realm.PULSE -> "PULSE REALM  ·  MOVE ON THE BEAT"
    else -> null
}
