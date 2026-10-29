package com.neonrush.game.ui

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Code-drawn aircraft for Air Support (used when res/drawable-nodpi/air_heli.webp / air_jet.webp are not uploaded, or always for the effects around them).
 * Both face RIGHT; [dir] = -1 mirrors them to fly left. [size] is the body length in pixels. [t] is seconds (rotor spin, flicker). [bank] tilts in degrees.
 */
object AirCraftArt {
    private val STEEL = Color(0xFF1B2745); private val STEEL2 = Color(0xFF2C3E6B); private val CYAN = Color(0xFF3DE8FF)
    private val AMBER = Color(0xFFFFB300); private val RED = Color(0xFFFF3D3D)

    fun heli(d: DrawScope, cx: Float, cy: Float, size: Float, dir: Float, t: Float, bank: Float, art: ImageBitmap?, gunFlash: Boolean) {
        d.run {
            val s = size
            // searchlight cone below
            drawPath(Path().apply { moveTo(cx + dir * s * 0.12f, cy + s * 0.2f); lineTo(cx + dir * s * 0.55f, cy + s * 1.5f); lineTo(cx - dir * s * 0.15f, cy + s * 1.5f); close() },
                Brush.verticalGradient(listOf(Color(0x44BFEFFF), Color.Transparent), cy + s * 0.2f, cy + s * 1.5f))
            rotate(bank * dir, Offset(cx, cy)) {
                scale(dir, 1f, Offset(cx, cy)) {
                    if (art != null) {
                        val ratio = art.height.toFloat() / art.width
                        val dw = s * 1.15f; val dh = dw * ratio
                        drawImage(art, dstOffset = IntOffset((cx - dw / 2f).toInt(), (cy - dh / 2f).toInt()), dstSize = IntSize(dw.toInt(), dh.toInt()))
                    } else {
                        // glow
                        drawCircle(Brush.radialGradient(listOf(CYAN.copy(alpha = 0.28f), Color.Transparent), Offset(cx, cy), s * 0.7f), s * 0.7f, Offset(cx, cy))
                        // tail boom + fin + tail rotor
                        drawRoundRect(STEEL2, Offset(cx - s * 0.62f, cy - s * 0.045f), Size(s * 0.5f, s * 0.09f), CornerRadius(s * 0.04f))
                        drawPath(Path().apply { moveTo(cx - s * 0.62f, cy - s * 0.04f); lineTo(cx - s * 0.7f, cy - s * 0.26f); lineTo(cx - s * 0.54f, cy - s * 0.04f); close() }, STEEL2)
                        val trA = t * 40f
                        rotate(trA, Offset(cx - s * 0.68f, cy - s * 0.12f)) { drawLine(Color.White.copy(alpha = 0.55f), Offset(cx - s * 0.68f, cy - s * 0.2f), Offset(cx - s * 0.68f, cy - s * 0.04f), s * 0.018f) }
                        // fuselage
                        drawOval(Brush.verticalGradient(listOf(STEEL2, STEEL), cy - s * 0.2f, cy + s * 0.2f), Offset(cx - s * 0.3f, cy - s * 0.18f), Size(s * 0.66f, s * 0.36f))
                        drawOval(CYAN.copy(alpha = 0.7f), Offset(cx - s * 0.3f, cy - s * 0.18f), Size(s * 0.66f, s * 0.36f), style = Stroke(s * 0.012f))
                        // canopy
                        drawOval(Brush.linearGradient(listOf(Color(0xFFB3F4FF), Color(0xFF2E7DA8)), Offset(cx + s * 0.12f, cy - s * 0.15f), Offset(cx + s * 0.3f, cy + s * 0.05f)), Offset(cx + s * 0.12f, cy - s * 0.14f), Size(s * 0.22f, s * 0.2f))
                        // stub wing + missile pods
                        drawRoundRect(STEEL, Offset(cx - s * 0.1f, cy + s * 0.07f), Size(s * 0.3f, s * 0.04f), CornerRadius(s * 0.02f))
                        for (k in 0..2) drawRoundRect(Color(0xFFCFD8DC), Offset(cx - s * 0.06f + k * s * 0.09f, cy + s * 0.1f), Size(s * 0.07f, s * 0.026f), CornerRadius(s * 0.012f))
                        // skids
                        drawLine(STEEL2, Offset(cx - s * 0.22f, cy + s * 0.27f), Offset(cx + s * 0.3f, cy + s * 0.27f), s * 0.018f)
                        drawLine(STEEL2, Offset(cx - s * 0.1f, cy + s * 0.18f), Offset(cx - s * 0.12f, cy + s * 0.27f), s * 0.014f)
                        drawLine(STEEL2, Offset(cx + s * 0.2f, cy + s * 0.18f), Offset(cx + s * 0.22f, cy + s * 0.27f), s * 0.014f)
                        // nav lights
                        val blink = if (((t * 3f).toInt() % 2) == 0) 1f else 0.25f
                        drawCircle(RED.copy(alpha = blink), s * 0.016f, Offset(cx - s * 0.66f, cy - s * 0.26f))
                        drawCircle(AMBER.copy(alpha = 1f - blink * 0.6f), s * 0.016f, Offset(cx + s * 0.02f, cy + s * 0.18f))
                    }
                    // main rotor: spinning blur disc + blades
                    val rx = cx - s * 0.02f; val ry = cy - s * 0.23f
                    drawOval(Color.White.copy(alpha = 0.10f), Offset(rx - s * 0.62f, ry - s * 0.05f), Size(s * 1.24f, s * 0.1f))
                    drawOval(CYAN.copy(alpha = 0.22f), Offset(rx - s * 0.62f, ry - s * 0.05f), Size(s * 1.24f, s * 0.1f), style = Stroke(s * 0.008f))
                    val a = t * 18f
                    for (k in 0..1) { val c = cos(a + k * PI.toFloat()); drawLine(Color.White.copy(alpha = 0.75f), Offset(rx, ry), Offset(rx + c * s * 0.62f, ry + sin(a + k * PI.toFloat()) * s * 0.05f), s * 0.014f) }
                    drawCircle(STEEL, s * 0.03f, Offset(rx, ry))
                    if (gunFlash) {
                        val fx = cx + s * 0.4f; val fy = cy + s * 0.12f
                        drawCircle(Brush.radialGradient(listOf(Color.White, Color(0xFFFFE082), Color.Transparent), Offset(fx, fy), s * 0.11f), s * 0.11f, Offset(fx, fy))
                    }
                }
            }
        }
    }

    fun jet(d: DrawScope, cx: Float, cy: Float, size: Float, dir: Float, t: Float, bank: Float, art: ImageBitmap?, gunFlash: Boolean) {
        d.run {
            val s = size
            // contrail behind
            val tx = cx - dir * s * 0.5f
            drawLine(Brush.horizontalGradient(if (dir > 0) listOf(Color.Transparent, Color.White.copy(alpha = 0.4f)) else listOf(Color.White.copy(alpha = 0.4f), Color.Transparent), tx - s * 2.2f, tx + s * 2.2f), Offset(tx - dir * s * 2.2f, cy), Offset(tx, cy), s * 0.07f)
            rotate(bank * dir, Offset(cx, cy)) {
                scale(dir, 1f, Offset(cx, cy)) {
                    // afterburner
                    val fl = 0.85f + 0.3f * sin(t * 60f) + Random.nextFloat() * 0.12f
                    drawPath(Path().apply { moveTo(cx - s * 0.5f, cy - s * 0.045f); lineTo(cx - s * (0.9f + 0.5f * fl), cy); lineTo(cx - s * 0.5f, cy + s * 0.045f); close() },
                        Brush.horizontalGradient(listOf(Color.Transparent, Color(0xFFFF7A18), Color(0xFFFFF3B0)), cx - s * 1.4f, cx - s * 0.5f))
                    if (art != null) {
                        val ratio = art.height.toFloat() / art.width
                        val dw = s * 1.3f; val dh = dw * ratio
                        drawImage(art, dstOffset = IntOffset((cx - dw / 2f).toInt(), (cy - dh / 2f).toInt()), dstSize = IntSize(dw.toInt(), dh.toInt()))
                    } else {
                        drawCircle(Brush.radialGradient(listOf(CYAN.copy(alpha = 0.25f), Color.Transparent), Offset(cx, cy), s * 0.8f), s * 0.8f, Offset(cx, cy))
                        // twin tail fins
                        drawPath(Path().apply { moveTo(cx - s * 0.42f, cy); lineTo(cx - s * 0.56f, cy - s * 0.2f); lineTo(cx - s * 0.3f, cy - s * 0.02f); close() }, STEEL)
                        // delta wing
                        drawPath(Path().apply { moveTo(cx + s * 0.55f, cy); lineTo(cx - s * 0.32f, cy - s * 0.34f); lineTo(cx - s * 0.5f, cy - s * 0.3f); lineTo(cx - s * 0.2f, cy); lineTo(cx - s * 0.5f, cy + s * 0.3f); lineTo(cx - s * 0.32f, cy + s * 0.34f); close() },
                            Brush.horizontalGradient(listOf(STEEL, STEEL2), cx - s * 0.5f, cx + s * 0.55f))
                        drawPath(Path().apply { moveTo(cx + s * 0.55f, cy); lineTo(cx - s * 0.32f, cy - s * 0.34f); lineTo(cx - s * 0.5f, cy - s * 0.3f); lineTo(cx - s * 0.2f, cy); lineTo(cx - s * 0.5f, cy + s * 0.3f); lineTo(cx - s * 0.32f, cy + s * 0.34f); close() }, CYAN.copy(alpha = 0.75f), style = Stroke(s * 0.012f))
                        // fuselage + canopy
                        drawOval(Brush.verticalGradient(listOf(STEEL2, STEEL), cy - s * 0.07f, cy + s * 0.07f), Offset(cx - s * 0.5f, cy - s * 0.07f), Size(s * 1.0f, s * 0.14f))
                        drawOval(Brush.linearGradient(listOf(Color(0xFFB3F4FF), Color(0xFF2E7DA8)), Offset(cx + s * 0.05f, cy - s * 0.09f), Offset(cx + s * 0.3f, cy)), Offset(cx + s * 0.08f, cy - s * 0.085f), Size(s * 0.26f, s * 0.09f))
                        // missiles under the wings
                        for (k in 0..1) { val yy = cy + (if (k == 0) -1f else 1f) * s * 0.17f; drawRoundRect(Color(0xFFECEFF1), Offset(cx - s * 0.12f, yy - s * 0.012f), Size(s * 0.3f, s * 0.024f), CornerRadius(s * 0.012f)) }
                    }
                    if (gunFlash) { val fx = cx + s * 0.56f; drawCircle(Brush.radialGradient(listOf(Color.White, Color(0xFFFFE082), Color.Transparent), Offset(fx, cy), s * 0.1f), s * 0.1f, Offset(fx, cy)) }
                }
            }
        }
    }
}
