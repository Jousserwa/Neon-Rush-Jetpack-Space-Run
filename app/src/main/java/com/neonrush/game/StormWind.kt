package com.neonrush.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import com.neonrush.game.ui.rememberBgClock
import kotlin.math.abs
import kotlin.math.sin

/** Daily Storm wind: one shared formula so the push the pilot feels and the streaks the player sees always agree. */
object StormWind {
    /** Smooth gust strength in about -1.5..+1.5 (positive pushes the pilot's Y up in value). */
    fun field(tick: Int): Float = (sin(tick * 0.035) + 0.5 * sin(tick * 0.21)).toFloat()
    /** The actual nudge applied to the pilot this tick. */
    fun push(tick: Int): Int {
        if (tick % 3 != 0) return 0
        val v = field(tick)
        return if (v > 0.35f) 1 else if (v < -0.35f) -1 else 0
    }
}

/** Full-screen, non-interactive wind streaks plus a gust chevron at the side the wind is pushing toward. */
@Composable
fun StormWindFx(tick: Int, modifier: Modifier = Modifier) {
    val clock = rememberBgClock()
    Canvas(modifier = modifier.fillMaxSize()) {
        val t = clock.value
        val v = StormWind.field(tick)
        val strength = ((abs(v) - 0.15f) / 0.9f).coerceIn(0f, 1f)
        val dir = if (v >= 0f) 1f else -1f      // +1: pushed toward larger Y (down the screen)
        val w = size.width; val h = size.height
        val n = 22
        for (i in 0 until n) {
            val fx = (i * 0.6180339f) % 1f
            val speed = 0.55f + (i % 5) * 0.13f
            val fy = (((i * 0.37f) + t * speed * (0.35f + strength) * dir) % 1f + 1f) % 1f
            val len = (28f + (i % 4) * 16f) * (0.5f + strength) * (w / 720f).coerceIn(0.6f, 1.6f)
            val x = fx * w; val y = fy * h
            val a = (0.10f + 0.30f * strength) * (0.6f + 0.4f * ((i % 3) / 2f))
            drawLine(
                color = Color(0xFFBFEFFF).copy(alpha = a),
                start = Offset(x, y), end = Offset(x - len * 0.45f, y - len * dir),
                strokeWidth = 2.2f + (i % 3), cap = StrokeCap.Round
            )
        }
        if (strength > 0.35f) {
            // gust chevrons on both edges, pulsing, pointing the way the wind is shoving
            val pulse = 0.5f + 0.5f * sin(t * 9f)
            val a = (0.25f + 0.45f * strength) * (0.6f + 0.4f * pulse)
            val cy = if (dir > 0) h * 0.82f else h * 0.18f
            for (cx in floatArrayOf(w * 0.05f, w * 0.95f)) {
                for (k in 0..1) {
                    val oy = cy + dir * k * 22f
                    val p = Path().apply {
                        moveTo(cx - 16f, oy - dir * 10f); lineTo(cx, oy + dir * 8f); lineTo(cx + 16f, oy - dir * 10f)
                    }
                    drawPath(p, Color(0xFFFFD166).copy(alpha = a * (1f - 0.35f * k)), style = Stroke(width = 5f, cap = StrokeCap.Round))
                }
            }
        }
    }
}
