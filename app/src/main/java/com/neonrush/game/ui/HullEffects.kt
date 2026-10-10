package com.neonrush.game.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.neonrush.game.R
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * Extra ship hulls (trail + aura), all drawn in code. Called first from drawHullEffect;
 * returns true when it handled the hull so the classic trail is skipped.
 */
internal fun DrawScope.drawExtraHull(hullId: String, x: Float, y: Float, h: Float, tick: Int, speedLevel: Int): Boolean {
    if (drawPremiumHull(hullId, x, y, h, tick, speedLevel)) return true
    val unit = h * 0.06f
    val n = 8 + speedLevel * 2
    val t = tick.toFloat()
    when (hullId) {
        "solar_comet" -> {
            glow(Offset(x, y), h * 0.55f, Color(0xFFFF8A00), 0.3f)
            val m = n + 3
            for (i in 1..m) {
                val f = i / (m + 1f)
                val cx = x - h * 0.18f - i * unit * 1.5f
                val cy = y + h * 0.05f + sin((t + i * 2) * 0.35f) * unit * (0.3f + i * 0.06f)
                val col = lerp(Color(0xFFFFF3B0), Color(0xFFD84315), f)
                drawCircle(col.copy(alpha = 0.7f * (1f - f)), unit * (2.2f - i * 0.12f).coerceAtLeast(0.5f), Offset(cx, cy))
            }
            for (k in 0 until 4) { // embers
                val ph = (t * 0.06f + k / 4f) % 1f
                val ex = x - h * 0.25f - ph * h * 0.9f
                val ey = y + (if (k % 2 == 0) -1 else 1) * (unit * 1.5f + ph * unit * 3f)
                drawCircle(Color(0xFFFFB300).copy(alpha = 1f - ph), unit * 0.35f, Offset(ex, ey))
            }
        }
        "ice_shard" -> {
            val col = Color(0xFF8FE9FF)
            drawCircle(col.copy(alpha = 0.3f), h * 0.5f, Offset(x, y),
                style = Stroke(2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), t)))
            for (i in 1..n) {
                val a = 0.65f * (1f - i / (n + 1f))
                val s = unit * (1.5f - i * 0.08f).coerceAtLeast(0.4f)
                val cx = x - h * 0.18f - i * unit * 1.7f
                val cy = y + h * 0.05f + sin((t + i * 3) * 0.2f) * unit * 0.4f
                val p = Path().apply {
                    moveTo(cx - s * 1.6f, cy); lineTo(cx, cy - s); lineTo(cx + s * 1.6f, cy); lineTo(cx, cy + s); close()
                }
                drawPath(p, col.copy(alpha = a))
                drawPath(p, Color.White.copy(alpha = a), style = Stroke(1.5f))
                if (i % 3 == 0) {
                    val sp = (sin(t * 0.4f + i) * 0.5f + 0.5f) * unit
                    drawLine(Color.White.copy(alpha = a + 0.2f), Offset(cx, cy - sp * 1.6f), Offset(cx, cy + sp * 1.6f), 2f)
                    drawLine(Color.White.copy(alpha = a + 0.2f), Offset(cx - sp * 1.6f, cy), Offset(cx + sp * 1.6f, cy), 2f)
                }
            }
        }
        "static_storm" -> {
            glow(Offset(x, y), h * 0.5f, Color(0xFFFFEE33), 0.15f)
            val r = Random(tick / 2 * 17 + 3)
            var prev = Offset(x - h * 0.15f, y + h * 0.05f)
            for (i in 1..n) {
                val next = Offset(x - h * 0.15f - i * unit * 1.8f, y + h * 0.05f + (r.nextFloat() - 0.5f) * unit * 3.2f)
                val a = 1f - i / (n + 1f)
                drawLine(Color(0xFFFFEE33).copy(alpha = 0.25f * a), prev, next, strokeWidth = 7f)
                drawLine(Color.White.copy(alpha = 0.9f * a), prev, next, strokeWidth = 2.2f)
                prev = next
            }
            for (k in 0 until 2) { // arcs around the ship
                var p = Offset(x, y)
                val ang = r.nextFloat() * 6.283f
                for (seg in 0 until 3) {
                    val np = p + Offset(kotlin.math.cos(ang) * unit * 2f + (r.nextFloat() - 0.5f) * unit, kotlin.math.sin(ang) * unit * 2f + (r.nextFloat() - 0.5f) * unit)
                    drawLine(Color(0xFFFFF59D).copy(alpha = 0.85f), p, np, 2f)
                    p = np
                }
            }
        }
        "vaporwave_wave" -> {
            val pink = Color(0xFFFF71CE); val cyan = Color(0xFF01CDFE)
            for ((ribbon, col) in listOf(0f to pink, 3.1416f to cyan)) {
                var prev = Offset(x - h * 0.15f, y + h * 0.05f)
                for (i in 1..n + 4) {
                    val f = i / (n + 5f)
                    val amp = unit * (0.6f + f * 3.2f)
                    val next = Offset(
                        x - h * 0.15f - i * unit * 1.5f,
                        y + h * 0.05f + sin(t * 0.25f + i * 0.45f + ribbon) * amp
                    )
                    drawLine(col.copy(alpha = 0.85f * (1f - f)), prev, next, strokeWidth = (unit * (1.1f - 0.7f * f)).coerceAtLeast(1.5f))
                    prev = next
                }
            }
        }
        "phantom_echo" -> {
            val col = Color(0xFFB8C4FF)
            val flick = 0.75f + 0.25f * sin(t * 0.3f)
            drawCircle(col.copy(alpha = 0.18f * flick), h * 0.5f, Offset(x, y), style = Stroke(2f))
            for (i in 1..5) {
                val a = 0.4f * (1f - i / 6f) * flick
                val sc = 1f - i * 0.06f
                val ex = x - h * 0.14f * i * (1f + speedLevel * 0.08f)
                val ey = y + sin((t + i * 4) * 0.2f) * unit * 0.5f
                val tl = Offset(ex - h * 0.17f * sc, ey - h * 0.31f * sc)
                val sz = Size(h * 0.34f * sc, h * 0.62f * sc)
                drawRoundRect(col.copy(alpha = a * 0.35f), tl, sz, CornerRadius(h * 0.1f))
                drawRoundRect(col.copy(alpha = a), tl, sz, CornerRadius(h * 0.1f), style = Stroke(2f))
            }
            for (k in 0 until 4) { // wisps
                val ph = (t * 0.05f + k / 4f) % 1f
                drawCircle(col.copy(alpha = 0.6f * (1f - ph)), unit * 0.5f, Offset(x - h * 0.2f - k * h * 0.12f, y - ph * h * 0.45f))
            }
        }
        else -> return false
    }
    return true
}

private fun DrawScope.glow(c: Offset, radius: Float, color: Color, alpha: Float) {
    drawCircle(
        brush = Brush.radialGradient(listOf(color.copy(alpha = alpha), Color.Transparent), center = c, radius = radius),
        radius = radius, center = c
    )
}

/** Animated hull preview strip for the shop: ship at the right, trail streaming left. */
@Composable
fun HullPreview(hullId: String, modifier: Modifier = Modifier.width(200.dp).height(56.dp)) {
    val bmp: ImageBitmap = ImageBitmap.imageResource(id = R.drawable.pilot_run_1)
    val t = rememberInfiniteTransition(label = "hullPreview")
    val tick by t.animateFloat(
        initialValue = 0f, targetValue = 480f,
        animationSpec = infiniteRepeatable(tween(60000, easing = LinearEasing), RepeatMode.Restart),
        label = "hullPreviewTick"
    )
    Canvas(modifier = modifier.clipToBounds()) {
        val h = size.height * 1.0f
        val x = size.width * 0.82f
        val y = size.height / 2f
        drawHullEffect(hullId, x, y, h, tick.toInt(), 6)
        val w = h * bmp.width / bmp.height
        drawImage(
            image = bmp,
            dstOffset = IntOffset((x - w / 2f).roundToInt(), (y - h / 2f).roundToInt()),
            dstSize = IntSize(w.roundToInt(), h.roundToInt())
        )
    }
}
