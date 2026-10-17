package com.neonrush.game.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The plan banner on the main screen. [tier]: 0 = free, 1 = Pro MONTHLY, 2 = Pro ANNUAL.
 *  - FREE: cool cyan, a pulsing "GO PRO" chip, tap opens the Pro offer.
 *  - MONTHLY: violet / magenta, a spinning crystal, flowing light around the border.
 *  - ANNUAL: gold / crimson, a floating crown, richer sparkle and a slow royal shine.
 * Everything is drawn in code from one 30 fps clock that is only read while drawing, so nothing recomposes.
 */
@Composable
fun PlanBanner(tier: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val clock = remember { mutableStateOf(0f) }
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) withFrameNanos { n -> if (n - last >= 33_000_000L) { last = n; clock.value = ((n / 1_000_000L) % 3_600_000L) / 1000f } }
    }
    val t = tier.coerceIn(0, 2)
    val c1 = when (t) { 1 -> Color(0xFFD28BFF); 2 -> Color(0xFFFFC94A); else -> Color(0xFF00E5FF) }
    val c2 = when (t) { 1 -> Color(0xFFFF2BD6); 2 -> Color(0xFFFF4D4D); else -> Color(0xFF3D7BFF) }
    val title = when (t) { 1 -> "PRO · MONTHLY"; 2 -> "PRO · ANNUAL"; else -> "FREE TIER" }
    val sub = when (t) { 1 -> "Thanks for flying Pro"; 2 -> "Annual pilot · thank you"; else -> "Remove ads · all worlds · Legendary" }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp)
            .clickable { if (t == 0) onClick() }
    ) {
        Canvas(modifier = Modifier.matchParentSize()) { drawPlanBanner(t, clock.value, c1, c2) }

        Row(
            modifier = Modifier.matchParentSize().padding(start = 62.dp, end = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.Center) {
                Text(
                    title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, letterSpacing = 1.sp,
                    style = TextStyle(shadow = Shadow(color = c1.copy(alpha = 0.9f), blurRadius = 16f))
                )
                Text(sub, color = c1.copy(alpha = 0.92f), fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            }
            if (t == 0) {
                Box(
                    modifier = Modifier
                        .graphicsLayer {
                            val p = 0.5f + 0.5f * sin(clock.value * 4f)
                            scaleX = 1f + 0.06f * p; scaleY = 1f + 0.06f * p
                        }
                        .background(Brush.horizontalGradient(listOf(c1, c2)), RoundedCornerShape(50))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("GO PRO ›", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .graphicsLayer { alpha = 0.55f + 0.45f * (0.5f + 0.5f * sin(clock.value * 3f)) }
                            .size(8.dp)
                            .background(c1, RoundedCornerShape(50))
                    )
                    Text("  ACTIVE", color = c1, fontSize = 11.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, letterSpacing = 1.sp)
                }
            }
        }
    }
}

private fun DrawScope.drawPlanBanner(tier: Int, t: Float, c1: Color, c2: Color) {
    val inset = 5.dp.toPx()
    val w = size.width - inset * 2f
    val h = size.height - inset * 2f
    if (w <= 0f || h <= 0f) return
    val r = 15.dp.toPx()
    val tl = Offset(inset, inset)
    val pulse = 0.5f + 0.5f * sin(t * 2.2f)
    val rr = RoundRect(inset, inset, inset + w, inset + h, CornerRadius(r))
    val outline = Path().apply { addRoundRect(rr) }

    // soft outer glow that breathes
    for (i in 1..4) {
        drawRoundRect(
            color = c1.copy(alpha = (0.05f + 0.05f * pulse) / i * (if (tier == 2) 1.4f else 1f)),
            topLeft = Offset(tl.x - i * 1.6.dp.toPx(), tl.y - i * 1.6.dp.toPx()),
            size = Size(w + i * 3.2.dp.toPx(), h + i * 3.2.dp.toPx()),
            cornerRadius = CornerRadius(r + i * 1.6.dp.toPx()),
            style = Stroke(width = 2.2.dp.toPx()),
            blendMode = BlendMode.Plus
        )
    }

    // dark tinted body
    drawRoundRect(
        brush = Brush.horizontalGradient(listOf(c2.copy(alpha = 0.30f), Color(0xFF0B0B18).copy(alpha = 0.92f), c1.copy(alpha = 0.26f))),
        topLeft = tl, size = Size(w, h), cornerRadius = CornerRadius(r)
    )

    // everything that must stay inside the rounded shape
    clipPath(outline) {
        // slow moving colour wash
        val wash = ((t * 0.12f) % 1f)
        drawRect(
            Brush.horizontalGradient(
                0f to Color.Transparent, (wash).coerceIn(0.001f, 0.998f) to c1.copy(alpha = 0.10f), 1f to Color.Transparent
            ),
            topLeft = tl, size = Size(w, h)
        )
        // diagonal shine sweeping across every few seconds (richer on annual)
        val period = if (tier == 2) 3.2f else 4.2f
        val sw = ((t / period) % 1.6f) - 0.3f
        if (sw > -0.25f && sw < 1.25f) {
            val x = tl.x + sw * w
            drawLine(Color.White.copy(alpha = if (tier == 2) 0.30f else 0.22f), Offset(x - h * 0.4f, tl.y + h), Offset(x + h * 0.4f, tl.y), strokeWidth = h * 0.28f, blendMode = BlendMode.Plus)
            drawLine(Color.White.copy(alpha = 0.35f), Offset(x - h * 0.4f + h * 0.22f, tl.y + h), Offset(x + h * 0.4f + h * 0.22f, tl.y), strokeWidth = h * 0.05f, blendMode = BlendMode.Plus)
        }
        // drifting sparkles
        val n = if (tier == 2) 9 else if (tier == 1) 7 else 4
        for (k in 0 until n) {
            val f = ((t * (0.07f + 0.02f * (k % 3)) + fxRnd(k, 5)) % 1f)
            val px = tl.x + f * w
            val py = tl.y + h * (0.2f + 0.6f * fxRnd(k, 9)) + sin(t * 1.6f + k) * h * 0.08f
            val tw = 0.5f + 0.5f * sin(t * 4f + k * 1.7f)
            fxSpark(Offset(px, py), 2.6.dp.toPx() * (0.5f + tw), if (k % 2 == 0) Color.White else c1, 0.35f + 0.5f * tw)
        }
    }

    // dim border + a bright comet of light that runs around the whole edge
    drawPath(outline, c1.copy(alpha = 0.35f), style = Stroke(width = 1.4.dp.toPx()))
    val pm = PathMeasure()
    pm.setPath(outline, false)
    val len = pm.length
    if (len > 0f) {
        val speed = if (tier == 0) 0.16f else 0.24f
        val head = ((t * speed) % 1f) * len
        val segs = 5
        for (i in 0 until segs) {
            val seglen = len * (0.22f - i * 0.035f)
            val a = (0.25f + i * 0.18f).coerceAtMost(1f)
            val col = if (i % 2 == 0) c1 else c2
            val start = head - seglen
            val p = Path()
            if (start >= 0f) pm.getSegment(start, head, p, true)
            else {
                pm.getSegment(len + start, len, p, true)
                pm.getSegment(0f, head, p, true)
            }
            drawPath(p, col.copy(alpha = a), style = Stroke(width = (1.6f + i * 0.5f).dp.toPx(), cap = StrokeCap.Round))
        }
        val hp = pm.getPosition(head)
        fxGlow(hp, 12.dp.toPx(), Color.White, 0.75f)
    }

    // the emblem on the left
    val ex = tl.x + 28.dp.toPx()
    val ey = tl.y + h / 2f
    val er = h * 0.34f
    fxGlow(Offset(ex, ey), er * 2.1f, c1, 0.40f + 0.25f * pulse)
    drawCircle(Color.Black.copy(alpha = 0.45f), er * 1.12f, Offset(ex, ey))
    drawCircle(c1.copy(alpha = 0.9f), er * 1.12f, Offset(ex, ey), style = Stroke(width = 1.4.dp.toPx()))
    when (tier) {
        0 -> { // a little ship: a cyan arrowhead with a flickering thruster
            val a = sin(t * 2f) * 0.08f
            val path = Path().apply {
                moveTo(ex + er * 0.85f, ey + a * er)
                lineTo(ex - er * 0.55f, ey - er * 0.62f)
                lineTo(ex - er * 0.25f, ey)
                lineTo(ex - er * 0.55f, ey + er * 0.62f)
                close()
            }
            drawPath(path, c1)
            drawLine(c2.copy(alpha = 0.6f + 0.4f * sin(t * 18f)), Offset(ex - er * 0.35f, ey), Offset(ex - er * (0.9f + 0.25f * sin(t * 14f)), ey), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
        }
        1 -> { // a spinning crystal
            fxRhombus(Offset(ex, ey), er * 0.7f, t * 2.4f, c1, 1f)
            fxRhombus(Offset(ex + er * 0.95f, ey - er * 0.7f), er * 0.22f, t * 3f, Color.White, 0.8f)
            fxRhombus(Offset(ex - er * 0.9f, ey + er * 0.75f), er * 0.18f, t * 3.6f, c2, 0.8f)
        }
        else -> { // a crown that floats a little
            val by = ey + sin(t * 2f) * er * 0.08f
            val crown = Path().apply {
                moveTo(ex - er * 0.8f, by + er * 0.5f)
                lineTo(ex - er * 0.85f, by - er * 0.35f)
                lineTo(ex - er * 0.4f, by + er * 0.05f)
                lineTo(ex, by - er * 0.6f)
                lineTo(ex + er * 0.4f, by + er * 0.05f)
                lineTo(ex + er * 0.85f, by - er * 0.35f)
                lineTo(ex + er * 0.8f, by + er * 0.5f)
                close()
            }
            drawPath(crown, Brush.verticalGradient(listOf(Color(0xFFFFF0A0), c1, c2), startY = by - er * 0.6f, endY = by + er * 0.5f))
            for (k in -1..1) drawCircle(Color.White.copy(alpha = 0.6f + 0.4f * sin(t * 3f + k)), er * 0.09f, Offset(ex + k * er * 0.4f, by + er * 0.3f))
            fxSpark(Offset(ex + er * 0.7f, by - er * 0.7f + sin(t * 3f) * er * 0.05f), er * 0.28f * (0.6f + 0.4f * sin(t * 5f)), Color.White, 0.9f)
        }
    }
}
