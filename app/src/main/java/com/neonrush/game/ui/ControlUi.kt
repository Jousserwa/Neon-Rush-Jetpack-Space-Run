package com.neonrush.game.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

enum class BtnKind { DASH, PULSE, OVER, FIRE, BOMB }

/**
 * One drawn, animated action button for the race (DASH, PULSE, OVERDRIVE, FIRE, BOMB). Metal bezel, a drawn icon that says what it does,
 * a breathing glow + rotating ring when READY, a dark cooldown sweep with the seconds in the middle, an energised look while ACTIVE,
 * a pop ring and a squash when pressed, and a muzzle flash on FIRE.
 *  fill: 0..1 (cooldown progress, time left while active, or magazine fraction for FIRE) · extra: FIRE = fraction of the current magazine · hot: overheated
 */
@Composable
fun GameButton(
    kind: BtnKind, size: Dp, col: Color, label: String, sub: String, fill: Float, ready: Boolean, active: Boolean,
    onPressStart: () -> Unit, onRelease: () -> Unit = {}, extra: Float = 0f, hot: Boolean = false, art: ImageBitmap? = null, modifier: Modifier = Modifier
) {
    val inf = rememberInfiniteTransition(label = "gbtn")
    val t by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Restart), label = "t")
    val pop = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    var pressed by remember { mutableStateOf(false) }
    val sc by animateFloatAsState(if (pressed) 0.9f else 1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium), label = "sc")
    val cooling = !ready && !active
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(size).graphicsLayer { scaleX = sc; scaleY = sc }
                .pointerInput(Unit) {
                    detectTapGestures(onPress = {
                        pressed = true; scope.launch { pop.snapTo(0f); pop.animateTo(1f, tween(420)) }
                        onPressStart(); tryAwaitRelease(); pressed = false; onRelease()
                    })
                },
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.fillMaxSize()) { drawGameButton(kind, col, fill, ready, active, t, pop.value, extra, hot, pressed, art) }
            if (cooling && sub.isNotEmpty()) Text(sub, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
        }
        Text(label, color = if (ready || active) col else Color.White.copy(alpha = 0.55f), fontSize = 9.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(top = 2.dp))
        if (!cooling && sub.isNotEmpty()) Text(sub, color = Color.White.copy(alpha = 0.8f), fontSize = 8.sp, fontFamily = FontFamily.Monospace)
    }
}

private fun DrawScope.drawGameButton(kind: BtnKind, col: Color, fill: Float, ready: Boolean, active: Boolean, t: Float, pop: Float, extra: Float, hot: Boolean, pressed: Boolean, art: ImageBitmap?) {
    val c = Offset(size.width / 2f, size.height / 2f); val r = size.minDimension / 2f
    val tau = 2f * PI.toFloat()
    val cooling = !ready && !active
    val breath = 0.5f + 0.5f * sin(t * tau)
    // glow
    if (ready || active) drawCircle(Brush.radialGradient(listOf(col.copy(alpha = (if (active) 0.55f else 0.25f + 0.25f * breath)), Color.Transparent), c, r * 1.35f), r * 1.35f, c)
    // body + bezel
    drawCircle(Brush.radialGradient(listOf(Color(0xFF2A2458), Color(0xFF120E30), Color(0xFF07041A)), Offset(c.x - r * 0.25f, c.y - r * 0.3f), r * 1.2f), r * 0.94f, c)
    drawCircle(Brush.sweepGradient(listOf(col.copy(alpha = 0.9f), Color.White.copy(alpha = 0.7f), col.copy(alpha = 0.35f), col.copy(alpha = 0.9f)), c), r * 0.94f, c, style = Stroke(r * 0.09f))
    drawCircle(Color.Black.copy(alpha = 0.45f), r * 0.78f, c, style = Stroke(r * 0.04f))
    // progress ring
    val ringCol = if (hot) Color(0xFFFF3D3D) else col
    drawArc(ringCol.copy(alpha = if (cooling) 0.55f else 0.95f), -90f, 360f * fill.coerceIn(0f, 1f), false, Offset(c.x - r * 0.86f, c.y - r * 0.86f), Size(r * 1.72f, r * 1.72f), style = Stroke(r * 0.07f, cap = StrokeCap.Round))
    // rotating ticks when ready
    if (ready && !active) rotate(t * 360f, c) { for (i in 0 until 12) { val a = i * tau / 12f; drawLine(col.copy(alpha = 0.5f), Offset(c.x + cos(a) * r * 1.0f, c.y + sin(a) * r * 1.0f), Offset(c.x + cos(a) * r * 1.1f, c.y + sin(a) * r * 1.1f), r * 0.04f) } }
    // icon
    val ia = if (cooling) 0.35f else 1f
    when (kind) {
        BtnKind.DASH -> {
            for (i in 0..2) { val ox = (i - 1) * r * 0.27f + (if (ready || active) sin(t * tau + i) * r * 0.03f else 0f)
                val p = Path().apply { moveTo(c.x + ox - r * 0.13f, c.y - r * 0.3f); lineTo(c.x + ox + r * 0.13f, c.y); lineTo(c.x + ox - r * 0.13f, c.y + r * 0.3f) }
                drawPath(p, col.copy(alpha = ia * (0.5f + 0.25f * i)), style = Stroke(r * 0.12f, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round)) }
            for (k in -1..1) { val sh = ((t * 2f + k * 0.3f) % 1f); drawLine(Color.White.copy(alpha = ia * 0.7f * (1f - sh)), Offset(c.x - r * (0.5f + 0.2f * sh), c.y + k * r * 0.3f), Offset(c.x - r * (0.72f + 0.2f * sh), c.y + k * r * 0.3f), r * 0.04f, StrokeCap.Round) }
        }
        BtnKind.PULSE -> {
            val mc = Color(0xFFE0E0E0).copy(alpha = ia)
            drawArc(mc, 0f, 180f, false, Offset(c.x - r * 0.3f, c.y - r * 0.2f), Size(r * 0.6f, r * 0.6f), style = Stroke(r * 0.2f))
            for (sgn in intArrayOf(-1, 1)) {
                val x = c.x + sgn * r * 0.3f
                drawRect(mc, Offset(x - r * 0.1f, c.y - r * 0.4f), Size(r * 0.2f, r * 0.5f))
                drawRect((if (sgn < 0) Color(0xFFFF5252) else Color(0xFF448AFF)).copy(alpha = ia), Offset(x - r * 0.1f, c.y - r * 0.4f), Size(r * 0.2f, r * 0.17f))
            }
            for (k in 0..2) { val kk = (t + k / 3f) % 1f; if (ready || active) drawArc(col.copy(alpha = (1f - kk) * 0.8f), 200f, 140f, false, Offset(c.x - r * (0.2f + 0.45f * kk), c.y - r * (0.45f + 0.4f * kk)), Size(r * (0.4f + 0.9f * kk), r * (0.5f + 0.7f * kk)), style = Stroke(r * 0.04f, cap = StrokeCap.Round)) }
        }
        BtnKind.OVER -> {
            val fl = if (active) 0.8f + 0.4f * sin(t * tau * 6f) else 1f
            drawCircle(col.copy(alpha = ia * 0.25f * fl), r * 0.55f, c)
            val pts = listOf(0.1f to -0.52f, -0.28f to 0.06f, -0.03f to 0.06f, -0.13f to 0.52f, 0.3f to -0.1f, 0.05f to -0.1f)
            val p = Path().apply { pts.forEachIndexed { i, (x, y) -> if (i == 0) moveTo(c.x + x * r, c.y + y * r) else lineTo(c.x + x * r, c.y + y * r) }; close() }
            drawPath(p, Brush.verticalGradient(listOf(Color(0xFFFFF59D), col, Color(0xFFFF6F00)), c.y - r * 0.5f, c.y + r * 0.5f), alpha = ia)
            drawPath(p, Color.White.copy(alpha = ia * 0.8f), style = Stroke(r * 0.03f))
            if (active) for (i in 0 until 6) { val a = t * tau + i * tau / 6f; drawCircle(Color(0xFFFFF59D), r * 0.04f, Offset(c.x + cos(a) * r * 0.62f, c.y + sin(a) * r * 0.62f)) }
        }
        BtnKind.FIRE -> {
            // crosshair
            drawCircle(col.copy(alpha = 0.35f), r * 0.62f, c, style = Stroke(r * 0.025f))
            for (k in 0..3) { val a = k * tau / 4f; drawLine(col.copy(alpha = 0.55f), Offset(c.x + cos(a) * r * 0.5f, c.y + sin(a) * r * 0.5f), Offset(c.x + cos(a) * r * 0.72f, c.y + sin(a) * r * 0.72f), r * 0.03f) }
            val recoil = if (pressed) r * 0.05f else 0f
            rotate(-10f, Offset(c.x, c.y + recoil)) {
                val mw = r * 0.34f; val mh = r * 0.62f; val mx = c.x - mw / 2f; val my = c.y - r * 0.02f + recoil
                // bullets (lit by how full the magazine is)
                for (b in 0..1) {
                    val bx = mx + mw * (0.05f + b * 0.5f); val bw = mw * 0.4f; val lit = extra > (b * 0.02f) && !(hot && b == 1)
                    drawRoundRect(if (lit) Color(0xFFFFB36B) else Color(0xFF555555), Offset(bx, my - r * 0.3f), Size(bw, r * 0.34f), CornerRadius(bw * 0.3f))
                    val tip = Path().apply { moveTo(bx, my - r * 0.3f); lineTo(bx + bw / 2f, my - r * 0.46f); lineTo(bx + bw, my - r * 0.3f); close() }
                    drawPath(tip, if (lit) Color(0xFFECEFF1) else Color(0xFF777777))
                }
                drawRoundRect(Brush.horizontalGradient(listOf(Color(0xFF3A4A6B), Color(0xFF8FA3C8), Color(0xFF2B3856)), mx, mx + mw), Offset(mx, my), Size(mw, mh), CornerRadius(r * 0.05f), alpha = ia.coerceAtLeast(0.6f))
                for (k in 1..4) drawLine(Color.Black.copy(alpha = 0.35f), Offset(mx + mw * 0.1f, my + mh * k / 5f), Offset(mx + mw * 0.9f, my + mh * k / 5f), r * 0.025f)
                // rounds left shown as a fill bar on the magazine side
                drawRect(col.copy(alpha = 0.9f), Offset(mx + mw + r * 0.04f, my + mh * (1f - extra.coerceIn(0f, 1f))), Size(r * 0.05f, mh * extra.coerceIn(0f, 1f)))
            }
            // trigger guard
            drawArc(Color(0xFFB0BEC5).copy(alpha = 0.7f), 20f, 140f, false, Offset(c.x + r * 0.08f, c.y + r * 0.28f), Size(r * 0.5f, r * 0.42f), style = Stroke(r * 0.05f, cap = StrokeCap.Round))
            drawLine(Color(0xFFFF8A65).copy(alpha = if (pressed) 1f else 0.8f), Offset(c.x + r * 0.28f, c.y + r * 0.42f), Offset(c.x + r * 0.22f + (if (pressed) -r * 0.06f else 0f), c.y + r * 0.58f), r * 0.07f, StrokeCap.Round)
            if (pressed && !hot) {   // muzzle flash
                val s = r * (0.35f + 0.25f * Random.nextFloat()); val o = Offset(c.x - r * 0.06f, c.y - r * 0.62f)
                for (k in 0 until 8) { val a = k * tau / 8f + t; drawLine(Color(0xFFFFF59D).copy(alpha = 0.9f), o, Offset(o.x + cos(a) * s, o.y + sin(a) * s), r * 0.05f, StrokeCap.Round) }
                drawCircle(Color.White, s * 0.35f, o)
            }
            if (hot) for (k in 0..3) drawLine(Color(0xFFFF3D3D).copy(alpha = 0.5f * (0.5f + 0.5f * sin(t * tau * 4f + k))), Offset(c.x - r * 0.6f, c.y - r * 0.5f + k * r * 0.3f), Offset(c.x + r * 0.6f, c.y - r * 0.2f + k * r * 0.3f), r * 0.05f)
        }
        BtnKind.BOMB -> {
            if (art != null) {
                val s = r * 1.3f; drawImage(art, dstOffset = IntOffset((c.x - s / 2f).toInt(), (c.y - s / 2f).toInt()), dstSize = IntSize(s.toInt(), s.toInt()), alpha = ia)
            } else {
                drawCircle(Brush.radialGradient(listOf(Color(0xFF8A8AAA), Color(0xFF22223A), Color.Black), Offset(c.x - r * 0.12f, c.y), r * 0.5f), r * 0.4f, Offset(c.x, c.y + r * 0.1f), alpha = ia)
                drawCircle(col.copy(alpha = ia), r * 0.4f, Offset(c.x, c.y + r * 0.1f), style = Stroke(r * 0.04f))
                drawRect(Color(0xFF616161), Offset(c.x - r * 0.1f, c.y - r * 0.38f), Size(r * 0.2f, r * 0.12f))
                val fuse = Path().apply { moveTo(c.x, c.y - r * 0.38f); quadraticBezierTo(c.x + r * 0.12f, c.y - r * 0.55f, c.x + r * 0.28f, c.y - r * 0.5f) }
                drawPath(fuse, Color(0xFFFFCC80), style = Stroke(r * 0.04f, cap = StrokeCap.Round))
                val sp = r * (0.1f + 0.06f * sin(t * tau * 7f))
                for (k in 0 until 6) { val a = k * tau / 6f + t * 4f; drawLine(Color(0xFFFFF59D), Offset(c.x + r * 0.28f, c.y - r * 0.5f), Offset(c.x + r * 0.28f + cos(a) * sp, c.y - r * 0.5f + sin(a) * sp), r * 0.03f) }
                drawCircle(Color.White, r * 0.04f, Offset(c.x + r * 0.28f, c.y - r * 0.5f))
            }
        }
    }
    // cooldown shade
    if (cooling) drawArc(Color.Black.copy(alpha = 0.5f), -90f + 360f * fill.coerceIn(0f, 1f), 360f * (1f - fill.coerceIn(0f, 1f)), true, Offset(c.x - r * 0.78f, c.y - r * 0.78f), Size(r * 1.56f, r * 1.56f))
    // active sparks around the bezel
    if (active) for (i in 0 until 8) { val a = t * tau * 1.5f + i * tau / 8f; drawCircle(col, r * 0.045f, Offset(c.x + cos(a) * r * 1.02f, c.y + sin(a) * r * 1.02f)) }
    // pop ring
    if (pop < 1f) drawCircle(col.copy(alpha = 0.9f * (1f - pop)), r * (1f + 0.8f * pop), c, style = Stroke(r * 0.09f * (1f - pop) + 1f))
}
