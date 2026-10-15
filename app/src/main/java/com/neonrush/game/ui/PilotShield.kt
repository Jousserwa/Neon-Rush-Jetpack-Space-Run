package com.neonrush.game.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Timestamps for the layered pilot shield (EASY mode). The game loop writes these, the draw code reads them,
 * so the animations run on real time (smooth) instead of the 120 ms game tick.
 */
object PilotShieldFx {
    @Volatile var gainMs = -99999L
    @Volatile var hitMs = -99999L
    @Volatile var maxLayers = 2

    private fun now(): Long = System.nanoTime() / 1_000_000L

    fun reset() { gainMs = -99999L; hitMs = -99999L }
    /** A fresh shield was put on the pilot (run start / refuel / revive). */
    fun onGain(layers: Int) { maxLayers = layers.coerceAtLeast(1); gainMs = now() }
    /** One layer was just used up. */
    fun onHit() { hitMs = now() }
    fun nowMs(): Long = now()
}

private val ICE = Color(0xFF5CE1FF)
private val ICE_DEEP = Color(0xFF2979FF)
private val WARN = Color(0xFFFFB74D)
private val WARN_DEEP = Color(0xFFFF6D00)

/**
 * The pilot's shield: an energy cocoon that hugs the pilot. One glowing ring per hit left, a honeycomb skin with a
 * light sweep, orbiting sparks, cracks as it weakens, an amber warning on the last layer, a pop-in when it is
 * put on and a shatter burst when a layer breaks. Drawn around the pilot (call it inside the pilot's rotate block).
 * [layers] = hits left, [t] = smooth clock in seconds.
 */
fun DrawScope.drawPilotShield(layers: Int, x: Float, y: Float, h: Float, w: Float, t: Float) {
    val nowMs = PilotShieldFx.nowMs()
    val hitAge = (nowMs - PilotShieldFx.hitMs).coerceAtLeast(0L)
    val gainAge = (nowMs - PilotShieldFx.gainMs).coerceAtLeast(0L)
    val breaking = hitAge < 650L
    if (layers <= 0 && !breaking) return

    val maxL = maxOf(PilotShieldFx.maxLayers, layers, 1)
    val center = Offset(x + w * 0.02f, y)
    val rx = maxOf(h * 0.5f, w * 0.46f)
    val ry = h * 0.6f
    val ky = ry / rx

    // pop-in on activation: grows past full size, settles back
    val popF = (gainAge / 480f).coerceIn(0f, 1f)
    val pop = if (popF >= 1f) 1f else (0.55f + 0.6f * (1f - (1f - popF) * (1f - popF)) - 0.1f * sin(popF * PI.toFloat())).coerceAtLeast(0.2f)
    val popAlpha = (popF * 2.2f).coerceIn(0f, 1f)

    val low = layers <= 1 && maxL >= 2
    val c1 = if (low) WARN else ICE
    val c2 = if (low) WARN_DEEP else ICE_DEEP
    val hitFlash = if (hitAge < 260L) 1f - hitAge / 260f else 0f
    val breath = 0.5f + 0.5f * sin(t * (if (low) 9f else 3.2f))   // faster pulse = hurry up

    withTransform({ scale(pop, pop, pivot = center) }) {
        for (i in 0 until layers) {
            val outer = i == layers - 1
            val s = 0.93f + 0.095f * i
            val rr = rx * s
            val layerAlpha = popAlpha * (if (outer) 1f else 0.75f)

            withTransform({ scale(1f, ky, pivot = center) }) {
                // glassy body: clear in the middle, glowing at the edge
                drawCircle(
                    brush = Brush.radialGradient(
                        0.0f to Color.Transparent,
                        0.62f to c2.copy(alpha = 0.05f * layerAlpha),
                        0.9f to c1.copy(alpha = (0.20f + 0.08f * breath + 0.35f * hitFlash) * layerAlpha),
                        1.0f to c1.copy(alpha = 0.55f * layerAlpha),
                        center = center, radius = rr
                    ),
                    radius = rr, center = center
                )
                // bright rim
                drawCircle(
                    color = c1.copy(alpha = (0.55f + 0.3f * breath) * layerAlpha),
                    radius = rr, center = center,
                    style = Stroke(width = h * (if (outer) 0.022f else 0.014f))
                )
                if (outer) {
                    drawCircle(Color.White.copy(alpha = (0.22f + 0.2f * hitFlash) * layerAlpha), radius = rr * 0.985f, center = center,
                        style = Stroke(width = h * 0.006f))
                }
            }

            if (outer) {
                honeycomb(center, rx * s, ry * s, h, t, c1, layerAlpha, low)
                sparks(center, rx * s, ry * s, h, t, c1, layers, layerAlpha)
                cracks(center, rx * s, ry * s, h, maxL - layers, layerAlpha, c1)
            }
        }

        // white flash across the whole bubble when something hits it
        if (hitFlash > 0f && layers > 0) {
            withTransform({ scale(1f, ky, pivot = center) }) {
                drawCircle(Color.White.copy(alpha = 0.35f * hitFlash), radius = rx * 1.0f, center = center, blendMode = BlendMode.Plus)
            }
        }
    }

    // the layer that just broke: ring flies outward and fades, with glass shards
    if (breaking) {
        val f = hitAge / 650f
        val ease = 1f - (1f - f) * (1f - f)
        val a = (1f - f).coerceIn(0f, 1f)
        val s = (0.93f + 0.095f * layers) + 0.55f * ease
        withTransform({ scale(1f, ky, pivot = center) }) {
            drawCircle(ICE.copy(alpha = 0.7f * a), radius = rx * s, center = center, style = Stroke(width = h * 0.03f * (1f - 0.7f * f)))
            drawCircle(Color.White.copy(alpha = 0.5f * a), radius = rx * s * 0.97f, center = center, style = Stroke(width = h * 0.008f))
        }
        for (k in 0 until 12) {
            val ang = k * (2f * PI.toFloat() / 12f) + 0.3f
            val d = 0.75f + 0.9f * ease + (k % 3) * 0.08f
            val px = center.x + cos(ang) * rx * d
            val py = center.y + sin(ang) * ry * d
            val len = h * (0.05f + 0.02f * (k % 3)) * (1f - 0.5f * f)
            drawLine(
                (if (k % 2 == 0) Color.White else ICE).copy(alpha = a),
                Offset(px, py), Offset(px + cos(ang + 0.6f) * len, py + sin(ang + 0.6f) * len),
                strokeWidth = h * 0.012f, cap = StrokeCap.Round
            )
        }
    }

    // expanding "put on" shockwave
    if (gainAge < 520L) {
        val f = gainAge / 520f
        withTransform({ scale(1f, ky, pivot = center) }) {
            drawCircle(Color.White.copy(alpha = 0.55f * (1f - f)), radius = rx * (0.9f + 0.9f * f), center = center,
                style = Stroke(width = h * 0.02f * (1f - f)))
        }
    }
}

/** Honeycomb skin clipped to the bubble, with a light band that sweeps across it. */
private fun DrawScope.honeycomb(c: Offset, rx: Float, ry: Float, h: Float, t: Float, col: Color, alpha: Float, low: Boolean) {
    val clip = Path().apply { addOval(androidx.compose.ui.geometry.Rect(c.x - rx, c.y - ry, c.x + rx, c.y + ry)) }
    val hex = h * 0.1f
    val dx = hex * 1.732f
    val dy = hex * 1.5f
    val grid = Path()
    var row = 0
    var yy = c.y - ry - hex
    while (yy < c.y + ry + hex) {
        val off = if (row % 2 == 0) 0f else dx / 2f
        var xx = c.x - rx - dx + off
        while (xx < c.x + rx + dx) {
            for (k in 0..5) {
                val a = (PI / 3.0 * k + PI / 6.0).toFloat()
                val px = xx + cos(a) * hex; val py = yy + sin(a) * hex
                if (k == 0) grid.moveTo(px, py) else grid.lineTo(px, py)
            }
            grid.close()
            xx += dx
        }
        yy += dy; row++
    }
    clipPath(clip) {
        drawPath(grid, col.copy(alpha = 0.16f * alpha), style = Stroke(width = h * 0.006f, join = StrokeJoin.Round))
        // sweeping highlight: a soft diagonal band rising through the bubble (faster when low)
        val ph = ((t * (if (low) 0.9f else 0.45f)) % 1f)
        val by = c.y + ry - ph * ry * 2.4f
        drawRect(
            brush = Brush.verticalGradient(
                listOf(Color.Transparent, Color.White.copy(alpha = 0.22f * alpha), Color.Transparent),
                startY = by - h * 0.16f, endY = by + h * 0.16f
            ),
            topLeft = Offset(c.x - rx, by - h * 0.16f), size = androidx.compose.ui.geometry.Size(rx * 2f, h * 0.32f),
            blendMode = BlendMode.Plus
        )
    }
}

/** Glints orbiting the bubble: more layers = more of them. */
private fun DrawScope.sparks(c: Offset, rx: Float, ry: Float, h: Float, t: Float, col: Color, layers: Int, alpha: Float) {
    val n = 2 + layers * 2
    for (k in 0 until n) {
        val a = t * (1.1f + 0.15f * (k % 2)) + k * (2f * PI.toFloat() / n)
        val px = c.x + cos(a) * rx
        val py = c.y + sin(a) * ry
        val tw = 0.55f + 0.45f * abs(sin(t * 3f + k))
        drawCircle(col.copy(alpha = 0.35f * alpha * tw), radius = h * 0.03f, center = Offset(px, py), blendMode = BlendMode.Plus)
        drawCircle(Color.White.copy(alpha = 0.9f * alpha * tw), radius = h * 0.011f, center = Offset(px, py))
    }
}

/** Hairline cracks on the rim: one more for every layer already lost, so damage is visible on the pilot itself. */
private fun DrawScope.cracks(c: Offset, rx: Float, ry: Float, h: Float, lost: Int, alpha: Float, col: Color) {
    if (lost <= 0) return
    val n = (lost * 2 + 1).coerceAtMost(8)
    for (k in 0 until n) {
        val a = (k * 2.399f + 0.7f)                       // spread around the rim
        val sx = c.x + cos(a) * rx; val sy = c.y + sin(a) * ry
        val dirx = -cos(a); val diry = -sin(a)
        val p = Path().apply {
            moveTo(sx, sy)
            var px = sx; var py = sy
            for (j in 1..3) {
                val jag = if (j % 2 == 0) 1f else -1f
                px += dirx * h * 0.045f - diry * jag * h * 0.03f
                py += diry * h * 0.045f + dirx * jag * h * 0.03f
                lineTo(px, py)
            }
        }
        drawPath(p, Color.White.copy(alpha = 0.8f * alpha), style = Stroke(width = h * 0.008f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(p, col.copy(alpha = 0.5f * alpha), style = Stroke(width = h * 0.016f, cap = StrokeCap.Round, join = StrokeJoin.Round), blendMode = BlendMode.Plus)
    }
}
