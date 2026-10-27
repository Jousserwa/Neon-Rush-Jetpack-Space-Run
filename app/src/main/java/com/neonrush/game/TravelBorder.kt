package com.neonrush.game.ui

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.abs

/**
 * A bright comet of light that runs around the border of a card. [t] is 0..1 (one lap). Draw it AFTER drawContent().
 * Done with a sweep gradient whose bright part moves, so it costs one draw call per frame.
 */
fun DrawScope.travelBorder(t: Float, color: Color, cornerPx: Float, strokePx: Float, tail: Float = 0.22f) {
    val n = 24
    val stops = Array(n + 1) { i ->
        val pos = i / n.toFloat()
        var d = (t - pos)
        d -= Math.floor(d.toDouble()).toFloat()                  // 0..1, how far behind the comet head this point is
        val a = if (d < tail) (1f - d / tail) else 0f
        pos to color.copy(alpha = (a * a).coerceIn(0f, 1f))
    }
    drawRoundRect(Brush.sweepGradient(*stops, center = Offset(size.width / 2f, size.height / 2f)), cornerRadius = CornerRadius(cornerPx, cornerPx),
        style = Stroke(width = strokePx), blendMode = BlendMode.Plus)
}
