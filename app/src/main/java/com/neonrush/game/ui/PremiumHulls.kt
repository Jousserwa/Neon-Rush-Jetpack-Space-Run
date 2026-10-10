package com.neonrush.game.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Game events the premium hulls react to. The ViewModel sets these to the current tick
 * when the event happens (see PATCHES.md). Hulls read the age = tick - eventTick.
 * Defaults are far in the past so nothing fires until a real event.
 */
object HullFx {
    @Volatile var closeCallTick = -9999
    @Volatile var gemTick = -9999
    @Volatile var zoneTick = -9999
    @Volatile var reviveTick = -9999
    @Volatile var bossTick = -9999
    @Volatile var zone = 1
    /** Pro Chroma recolor: hullId -> variant 0..3 (0 = original). Hue-rotated 90 degrees per step. */
    @Volatile var chroma: Map<String, Int> = emptyMap()
    /** Hull Mastery level per hull (0..5), shown as small pips under the ship. */
    @Volatile var mastery: Map<String, Int> = emptyMap()
    fun reset() { closeCallTick = -9999; gemTick = -9999; zoneTick = -9999; reviveTick = -9999; bossTick = -9999; zone = 1 }
}

private fun DrawScope.pGlow(c: Offset, radius: Float, color: Color, alpha: Float) {
    drawCircle(
        brush = Brush.radialGradient(listOf(color.copy(alpha = alpha), Color.Transparent), center = c, radius = radius),
        radius = radius, center = c
    )
}

private fun age(tick: Int, eventTick: Int) = tick - eventTick

/**
 * Premium hulls (aura + trail + event reaction), all drawn in code.
 * Returns true when the hull was handled. Called first from drawExtraHull.
 */
private fun DrawScope.drawPremiumHullRaw(hullId: String, x: Float, y: Float, h: Float, tick: Int, speedLevel: Int): Boolean {
    val u = h * 0.06f
    val t = tick.toFloat()
    val n = 8 + speedLevel * 2
    val pi = PI.toFloat()
    val ship = Offset(x, y)
    when (hullId) {

        "aurora_serpent" -> {
            val m = n + 6
            for (side in 0..1) {
                var prev = Offset(x - h * 0.15f, y + h * 0.05f)
                for (i in 1..m) {
                    val f = i / (m + 1f)
                    val amp = u * (0.5f + f * 3f)
                    val next = Offset(x - h * 0.15f - i * u * 1.5f, y + h * 0.05f + sin(t * 0.2f + i * 0.4f + side * 2.2f) * amp)
                    val col = lerp(Color(0xFF39FFA0), Color(0xFFB266FF), f)
                    drawLine(col.copy(alpha = 0.8f * (1f - f)), prev, next, strokeWidth = (u * (1.2f - 0.8f * f)).coerceAtLeast(1.5f))
                    prev = next
                }
            }
            for (k in 0 until 6) {
                val ph = (t * 0.03f + k / 6f) % 1f
                drawCircle(Color.White.copy(alpha = (1f - ph) * 0.8f), u * 0.3f, Offset(x - h * 0.2f - ph * h * 1.1f, y + sin(k * 2.3f) * h * 0.35f))
            }
            val a = age(tick, HullFx.closeCallTick)
            if (a in 0..18) drawCircle(Color(0xFF7DFFD0).copy(alpha = 1f - a / 18f), h * 0.3f + a * u * 0.6f, ship, style = Stroke(3f))
        }

        "supernova_heart" -> {
            val beat = 0.5f + 0.5f * sin(t * (0.2f + speedLevel * 0.02f))
            pGlow(ship, h * (0.45f + 0.12f * beat), Color(0xFFFFB300), 0.25f + 0.2f * beat)
            for (k in 0 until 5) {
                val ph = (t * 0.04f + k / 5f) % 1f
                drawCircle(Color(0xFFFFE082).copy(alpha = (1f - ph) * 0.7f), h * 0.12f + ph * h * 0.5f,
                    Offset(x - h * 0.15f - ph * h * 1.2f, y + h * 0.05f), style = Stroke(2.5f))
            }
            val a = age(tick, HullFx.gemTick)
            if (a in 0..14) {
                val f = a / 14f
                for (k in 0 until 8) {
                    val ang = k * pi / 4f
                    drawLine(Color(0xFFFFD23F).copy(alpha = 1f - f), ship + Offset(cos(ang), sin(ang)) * (h * 0.3f * f),
                        ship + Offset(cos(ang), sin(ang)) * (h * (0.3f + 0.35f * f)), strokeWidth = 3f)
                }
            }
        }

        "neon_wyrm" -> {
            val segs = 9 + speedLevel
            for (i in segs downTo 1) {
                val f = i / (segs + 1f)
                val cx = x - h * 0.2f - i * u * 1.9f
                val cy = y + h * 0.05f + sin(t * 0.22f - i * 0.55f) * u * (0.8f + f * 2.4f)
                val col = lerp(Color(0xFF00FFC8), Color(0xFFFF2BD6), f)
                drawCircle(col.copy(alpha = 0.25f * (1f - f)), u * (2.4f - f * 1.4f), Offset(cx, cy))
                drawCircle(col.copy(alpha = 0.85f * (1f - f * 0.8f)), u * (1.5f - f * 0.9f).coerceAtLeast(0.5f), Offset(cx, cy))
            }
            val eye = if ((tick / 18) % 6 == 0) 0.2f else 1f // blink
            drawCircle(Color.White.copy(alpha = eye), u * 0.45f, Offset(x - h * 0.08f, y - h * 0.08f))
            drawCircle(Color(0xFFFF2BD6).copy(alpha = eye), u * 0.22f, Offset(x - h * 0.08f, y - h * 0.08f))
            for (k in 0 until 5) { // spark breath
                val ph = (t * 0.08f + k / 5f) % 1f
                drawCircle(Color(0xFFFFEE58).copy(alpha = 1f - ph), u * 0.4f,
                    Offset(x + h * 0.2f + ph * h * 0.5f, y - h * 0.02f + sin(k * 3.1f + t * 0.3f) * u * 1.2f * ph))
            }
        }

        "event_horizon" -> {
            val c = ship
            for (k in 0 until 14) {
                val a = t * 0.09f + k * (2f * pi / 14f)
                val p = c + Offset(cos(a) * h * 0.55f, sin(a) * h * 0.18f)
                val col = lerp(Color(0xFFFF7043), Color(0xFFFFEB3B), (k % 7) / 7f)
                drawCircle(col.copy(alpha = if (sin(a) > 0) 0.9f else 0.45f), u * 0.5f, p)
            }
            for (k in 0 until 6) { // debris pulled in
                val ph = (t * 0.035f + k / 6f) % 1f
                val ang = k * 1.05f
                val r = h * (0.9f - 0.75f * ph)
                drawCircle(Color.White.copy(alpha = 1f - ph), u * 0.3f, c + Offset(cos(ang + ph * 3f) * r, sin(ang + ph * 3f) * r * 0.5f))
            }
            for (i in 1..n) { // red-shifted trail
                val f = i / (n + 1f)
                drawCircle(lerp(Color(0xFFFF5252), Color(0xFF4A148C), f).copy(alpha = 0.6f * (1f - f)), u * (1.4f - f),
                    Offset(x - h * 0.2f - i * u * 1.6f, y + h * 0.05f))
            }
            drawCircle(Color.Black.copy(alpha = 0.55f), h * 0.12f, c)
            val a = age(tick, HullFx.zoneTick)
            if (a in 0..20) drawCircle(Color(0xFFFFAB91).copy(alpha = 1f - a / 20f), h * 0.3f + a * u, c, style = Stroke(3f))
        }

        "crystal_phoenix" -> {
            val flap = sin(t * 0.3f)
            for (side in intArrayOf(-1, 1)) {
                val tipY = y + side * h * (0.42f + 0.12f * flap)
                val wing = Path().apply {
                    moveTo(x - h * 0.05f, y)
                    lineTo(x - h * 0.45f, tipY)
                    lineTo(x - h * 0.2f, y + side * h * 0.1f)
                    lineTo(x - h * 0.55f, y + side * h * (0.2f + 0.05f * flap))
                    close()
                }
                drawPath(wing, Color(0xFF8FE9FF).copy(alpha = 0.45f))
                drawPath(wing, Color.White.copy(alpha = 0.8f), style = Stroke(2f))
            }
            for (k in 0 until 7) {
                val ph = (t * 0.045f + k / 7f) % 1f
                val col = lerp(Color(0xFFFFC107), Color(0xFFD50000), ph)
                drawCircle(col.copy(alpha = (1f - ph) * 0.8f), u * 0.5f,
                    Offset(x - h * 0.3f - ph * h * 1.0f, y + sin(k * 2.2f + t * 0.1f) * u * 2f))
            }
            val a = age(tick, HullFx.reviveTick)
            if (a in 0..22) pGlow(ship, h * (0.4f + a * 0.05f), Color(0xFFFFF59D), 0.7f * (1f - a / 22f))
        }

        "quantum_mirage" -> {
            val hue = (HullFx.zone * 47f) % 360f
            for (k in 1..3) {
                val a = 0.35f * (0.5f + 0.5f * sin(t * 0.18f + k * 2f))
                val ox = -h * 0.16f * k
                val oy = sin(t * 0.12f + k) * u * 1.5f
                val col = Color.hsv((hue + k * 70f) % 360f, 0.7f, 1f)
                drawRoundRect(col.copy(alpha = a), Offset(x - h * 0.17f + ox, y - h * 0.31f + oy), Size(h * 0.34f, h * 0.62f),
                    androidx.compose.ui.geometry.CornerRadius(h * 0.1f), style = Stroke(2.5f))
            }
            val scan = ((t * 0.03f) % 1f)
            val sy = y - h * 0.5f + scan * h
            drawLine(Color.White.copy(alpha = 0.55f), Offset(x - h * 0.5f, sy), Offset(x + h * 0.5f, sy), strokeWidth = 2f)
            for (i in 1..n) {
                val f = i / (n + 1f)
                drawCircle(Color.hsv((hue + f * 120f) % 360f, 0.8f, 1f).copy(alpha = 0.6f * (1f - f)), u * (1.3f - f),
                    Offset(x - h * 0.2f - i * u * 1.6f, y + h * 0.05f + sin(t * 0.3f + i) * u * 0.5f))
            }
        }

        "stormcaller" -> {
            for (k in 0 until 4) {
                val ang = t * 0.03f + k * pi / 2f
                drawCircle(Color(0xFF455A64).copy(alpha = 0.55f), h * 0.2f, ship + Offset(cos(ang) * h * 0.3f, sin(ang) * h * 0.2f - h * 0.1f))
            }
            val r = Random(tick / 2 * 13 + 5)
            for (k in 0 until 2) {
                var p = ship + Offset(h * 0.1f, -h * 0.05f)
                for (seg in 0 until 4) {
                    val nx = p + Offset(h * 0.12f + r.nextFloat() * u, (r.nextFloat() - 0.5f) * u * 3f)
                    drawLine(Color(0xFFFFF59D).copy(alpha = 0.9f), p, nx, strokeWidth = 2.2f)
                    p = nx
                }
            }
            for (i in 1..n) {
                val f = i / (n + 1f)
                drawLine(Color(0xFF90CAF9).copy(alpha = 0.5f * (1f - f)), Offset(x - h * 0.2f - i * u * 1.6f, y - u),
                    Offset(x - h * 0.22f - i * u * 1.6f, y + u * 2f), strokeWidth = 2f)
            }
            val a = age(tick, HullFx.closeCallTick)
            if (a in 0..5) drawRect(Color.White.copy(alpha = 0.4f * (1f - a / 5f)), Offset(x - size.width, y - size.height), Size(size.width * 2f, size.height * 2f))
        }

        "cosmic_koi" -> {
            for (k in 0 until 3) {
                val ang = t * 0.05f + k * (2f * pi / 3f)
                val p = ship + Offset(cos(ang) * h * 0.5f, sin(ang) * h * 0.22f)
                val col = if (k % 2 == 0) Color(0xFFFF8A65) else Color(0xFFFFFFFF)
                drawOval(col.copy(alpha = 0.9f), Offset(p.x - u * 1.4f, p.y - u * 0.7f), Size(u * 2.8f, u * 1.4f))
                val tail = Path().apply {
                    moveTo(p.x - u * 1.3f, p.y); lineTo(p.x - u * 2.3f, p.y - u * 0.8f); lineTo(p.x - u * 2.3f, p.y + u * 0.8f); close()
                }
                drawPath(tail, col.copy(alpha = 0.7f))
            }
            for (k in 0 until 4) {
                val ph = (t * 0.035f + k / 4f) % 1f
                drawCircle(Color(0xFF80DEEA).copy(alpha = (1f - ph) * 0.6f), h * 0.1f + ph * h * 0.45f,
                    Offset(x - h * 0.15f - ph * h * 1.1f, y + h * 0.05f), style = Stroke(2f))
            }
            for (k in 0 until 5) {
                val ph = (t * 0.03f + k / 5f) % 1f
                drawCircle(Color(0xFFF8BBD0).copy(alpha = 1f - ph), u * 0.45f,
                    Offset(x - h * 0.1f - ph * h * 0.9f, y - h * 0.4f + ph * h * 0.8f + sin(k * 2f + t * 0.1f) * u))
            }
        }

        "inferno_sovereign" -> {
            pGlow(ship, h * 0.6f, Color(0xFFFF6D00), 0.22f)
            for (k in -2..2) {
                val fl = 0.7f + 0.3f * sin(t * 0.4f + k)
                val tipH = h * (0.3f + (2 - kotlin.math.abs(k)) * 0.07f) * fl
                val base = Offset(x + k * u * 1.3f, y - h * 0.38f)
                val flame = Path().apply {
                    moveTo(base.x - u * 0.7f, base.y); lineTo(base.x, base.y - tipH); lineTo(base.x + u * 0.7f, base.y); close()
                }
                drawPath(flame, Color(0xFFFF9100).copy(alpha = 0.85f))
                drawPath(flame, Color(0xFFFFEB3B).copy(alpha = 0.5f), style = Stroke(1.5f))
            }
            for (k in 0 until 5) {
                val ph = (t * 0.05f + k / 5f) % 1f
                drawCircle(Color(0xFFFF3D00).copy(alpha = 1f - ph), u * 0.45f, Offset(x - h * 0.15f + k * u * 0.8f, y + h * 0.3f + ph * h * 0.5f))
            }
            val a = age(tick, HullFx.bossTick)
            if (a in 0..16) pGlow(ship, h * (0.5f + a * 0.08f), Color(0xFFFF3D00), 0.6f * (1f - a / 16f))
        }

        "eternal_crown_2026" -> {
            pGlow(ship, h * 0.7f, Color(0xFFFFD23F), 0.28f)
            for (k in 0 until 8) {
                val a = t * 0.05f + k * pi / 4f
                val p = ship + Offset(cos(a) * h * 0.5f, sin(a) * h * 0.16f - h * 0.32f)
                drawCircle(Color(0xFFFFE082).copy(alpha = if (sin(a) > 0) 0.95f else 0.5f), u * 0.45f, p)
            }
            for (k in 0 until 8) {
                val ph = (t * 0.04f + k / 8f) % 1f
                drawCircle(Color(0xFFFFD23F).copy(alpha = 1f - ph), u * 0.35f, Offset(x - h * 0.2f - ph * h * 1.0f, y + sin(k * 2.6f) * u * 1.8f))
            }
            if ((tick % 90) < 8) pGlow(ship + Offset(0f, -h * 0.32f), h * 0.35f, Color.White, 0.5f)
        }

        // Ranked Season hull: never sold, granted to the season's top 100.
        "apex_laurel" -> {
            pGlow(ship, h * 0.75f, Color(0xFF7DF9FF), 0.2f)
            for (side in -1..1 step 2) for (k in 0 until 7) {
                val a = (k - 3) * 0.28f
                val lp = ship + Offset(side * (h * 0.38f - kotlin.math.abs(k - 3) * u * 0.25f), -a * h * 0.55f * -1f + sin(t * 0.06f + k) * u * 0.2f)
                drawCircle(Color(0xFFFFD23F).copy(alpha = 0.9f), u * 0.5f, lp)
            }
            for (k in 0 until 10) {
                val ph = (t * 0.05f + k / 10f) % 1f
                drawCircle(Color(0xFF7DF9FF).copy(alpha = 1f - ph), u * 0.4f, Offset(x - h * 0.2f - ph * h * 1.2f, y + sin(k * 1.7f + t * 0.1f) * u * 2f))
            }
            if ((tick % 120) < 10) pGlow(ship, h * 0.5f, Color.White, 0.4f)
        }

        else -> return false
    }
    return true
}


private fun hueMatrix(deg: Float): ColorMatrix {
    val r = Math.toRadians(deg.toDouble()); val c = cos(r).toFloat(); val s = sin(r).toFloat()
    return ColorMatrix(floatArrayOf(
        0.213f + c * 0.787f - s * 0.213f, 0.715f - c * 0.715f - s * 0.715f, 0.072f - c * 0.072f + s * 0.928f, 0f, 0f,
        0.213f - c * 0.213f + s * 0.143f, 0.715f + c * 0.285f + s * 0.140f, 0.072f - c * 0.072f - s * 0.283f, 0f, 0f,
        0.213f - c * 0.213f - s * 0.787f, 0.715f - c * 0.715f + s * 0.715f, 0.072f + c * 0.928f + s * 0.072f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f))
}

/** Public entry used by drawExtraHull. Applies Pro Chroma (hue-rotate layer) and Mastery pips. */
internal fun DrawScope.drawPremiumHull(hullId: String, x: Float, y: Float, h: Float, tick: Int, speedLevel: Int): Boolean {
    val variant = HullFx.chroma[hullId] ?: 0
    var handled = false
    if (variant == 0) handled = drawPremiumHullRaw(hullId, x, y, h, tick, speedLevel)
    else drawIntoCanvas { canvas ->
        val paint = Paint().apply { colorFilter = ColorFilter.colorMatrix(hueMatrix(variant * 90f)) }
        canvas.saveLayer(androidx.compose.ui.geometry.Rect(0f, 0f, size.width, size.height), paint)
        handled = drawPremiumHullRaw(hullId, x, y, h, tick, speedLevel)
        canvas.restore()
    }
    if (handled) {
        val lv = HullFx.mastery[hullId] ?: 0
        for (i in 0 until lv) drawCircle(Color(0xFFFFD23F).copy(alpha = 0.9f), h * 0.025f, Offset(x + (i - (lv - 1) / 2f) * h * 0.09f, y + h * 0.5f))
    }
    return handled
}
