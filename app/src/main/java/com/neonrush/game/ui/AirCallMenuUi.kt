package com.neonrush.game.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neonrush.game.AirSupport
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.delay

private val MONO2 = FontFamily.Monospace
private val GRN = Color(0xFF69F0AE); private val GLD = Color(0xFFFFC21A); private val RED2 = Color(0xFFFF5252); private val CY = Color(0xFF3DE8FF)

/**
 * The seven-call board (Stage 150b): animated tiles that slide in one after another, each with a small animated aircraft icon, its price in strikes (with the heat surcharge),
 * and a clear state: ready, locked (and why), used this round, or "need N more". The two special calls are gold and shimmer. Tapping a ready tile places the call at once.
 */
@Composable
fun AirCallMenu(
    tierName: String, isPro: Boolean, testing: Boolean, have: Int, free: Boolean, ready: Boolean, cdMs: Long,
    onPick: (Int) -> Unit, onBlocked: (String) -> Unit, onClose: () -> Unit, modifier: Modifier = Modifier
) {
    val inf = rememberInfiniteTransition(label = "callBoard")
    val t by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart), label = "t")
    val sur = AirSupport.surcharge()
    val shape = RoundedCornerShape(18.dp)
    val vault by animateIntAsState(have, tween(500), label = "vault")
    Column(
        modifier.fillMaxWidth().widthIn(max = 340.dp).clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0xF2101A3A), Color(0xF2080620))))
            .border(1.5.dp, Brush.horizontalGradient(listOf(GRN.copy(alpha = 0.8f), CY.copy(alpha = 0.5f), GLD.copy(alpha = 0.8f))), shape)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // ---- header: title, vault, heat ----
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("AIR SUPPORT", color = GRN, fontSize = 14.sp, fontWeight = FontWeight.Black, fontFamily = MONO2)
                Text(if (!ready) "REARMING ${(cdMs / 1000L) + 1}s" else "PICK A CALL", color = if (ready) Color.White.copy(alpha = 0.7f) else Color(0xFFFFB300), fontSize = 9.sp, fontFamily = MONO2)
            }
            Row(Modifier.clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = 0.08f)).padding(horizontal = 8.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("⚡", fontSize = 13.sp); Text(" $vault", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Black, fontFamily = MONO2)
                if (free) Text(" +1 FREE", color = GLD, fontSize = 8.sp, fontWeight = FontWeight.Black, fontFamily = MONO2)
            }
            Spacer(Modifier.width(6.dp))
            Text("✕", color = Color.White.copy(alpha = 0.75f), fontSize = 16.sp, modifier = Modifier.clickable { onClose() }.padding(4.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("HEAT ", color = Color.White.copy(alpha = 0.6f), fontSize = 8.sp, fontWeight = FontWeight.Black, fontFamily = MONO2)
            for (i in 1..AirSupport.HEAT_MAX) Box(Modifier.padding(end = 3.dp).size(width = 22.dp, height = 5.dp).clip(RoundedCornerShape(3.dp)).background(if (i <= sur) Color(0xFFFF7043) else Color.White.copy(alpha = 0.12f)))
            Text(if (sur > 0) "  +$sur ON EVERY CALL THIS ROUND" else "  COOL · FIRST CALL OF THE ROUND", color = if (sur > 0) Color(0xFFFF7043) else Color.White.copy(alpha = 0.5f), fontSize = 8.sp, fontFamily = MONO2)
        }
        // ---- tiles: 1-4 in two columns, 5 wide, 6-7 gold ----
        val calls = AirSupport.CALLS
        for (row in listOf(listOf(1, 2), listOf(3, 4), listOf(5), listOf(6, 7))) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (id in row) {
                    Box(Modifier.weight(1f)) {
                        CallTile(calls[id - 1], tierName, isPro, testing, have, free, ready, t, sur, onPick, onBlocked)
                    }
                }
            }
        }
    }
}

@Composable
private fun CallTile(
    c: AirSupport.CallType, tierName: String, isPro: Boolean, testing: Boolean, have: Int, free: Boolean, ready: Boolean, t: Float, sur: Int,
    onPick: (Int) -> Unit, onBlocked: (String) -> Unit
) {
    val special = c.id >= 6
    val base = if (special) GLD else GRN
    val why = AirSupport.lockedReason(c.id, tierName, isPro)
    val cost = AirSupport.cost(c.id)
    val afford = testing || have >= cost
    val ok = why == null && ready && afford
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) { delay(c.id * 45L); appear.animateTo(1f, tween(280)) }
    var pressed by remember { mutableStateOf(false) }
    val press by animateFloatAsState(if (pressed) 0.93f else 1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium), label = "press")
    val pulse = 0.5f + 0.5f * sin(t * 2f * PI.toFloat())
    val shape = RoundedCornerShape(12.dp)
    val borderCol = if (why != null) Color.White.copy(alpha = 0.12f) else if (ok) base.copy(alpha = 0.55f + 0.4f * pulse) else RED2.copy(alpha = 0.4f)
    Box(
        Modifier.fillMaxWidth().graphicsLayer { val s = press * (0.85f + 0.15f * appear.value); scaleX = s; scaleY = s; alpha = appear.value; translationY = (1f - appear.value) * 24f }
            .clip(shape)
            .background(Brush.verticalGradient(listOf(if (ok) base.copy(alpha = 0.20f) else Color.White.copy(alpha = 0.06f), Color(0x66080620))))
            .border(1.5.dp, borderCol, shape)
            .pointerInput(c.id, ok, why, ready, afford) {
                detectTapGestures(onPress = { pressed = true; tryAwaitRelease(); pressed = false }, onTap = {
                    if (ok) onPick(c.id)
                    else onBlocked(when { why != null -> "Not available: $why"; !ready -> "The aircraft are still rearming."; else -> "Not enough strikes: ${cost} needed, you have $have." })
                })
            }
    ) {
        if (special && ok) Canvas(Modifier.matchParentSize()) {   // gold shimmer sweep
            val x = size.width * (-0.3f + 1.6f * t)
            drawRect(Brush.horizontalGradient(listOf(Color.Transparent, GLD.copy(alpha = 0.25f), Color.Transparent), x - size.width * 0.2f, x + size.width * 0.2f))
        }
        Row(Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Canvas(Modifier.size(40.dp)) { callIcon(c.id, t, if (why != null) Color.White.copy(alpha = 0.3f) else base, ok) }
            Spacer(Modifier.width(6.dp))
            Column(Modifier.weight(1f)) {
                Text("${c.id}  ${c.name}", color = if (why != null) Color.White.copy(alpha = 0.45f) else if (ok) base else Color.White.copy(alpha = 0.7f), fontSize = 10.sp, fontWeight = FontWeight.Black, fontFamily = MONO2, maxLines = 1)
                Text(
                    if (why != null) "🔒 $why" else if (!afford) "NEED ${cost - have} MORE ⚡" else c.tag,
                    color = if (why != null) Color.White.copy(alpha = 0.5f) else if (!afford) RED2 else Color.White.copy(alpha = 0.65f), fontSize = 8.sp, fontFamily = MONO2, maxLines = 1
                )
                Text(c.blurb, color = Color.White.copy(alpha = if (why != null) 0.3f else 0.55f), fontSize = 7.sp, fontFamily = MONO2, maxLines = 2, lineHeight = 9.sp)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(if (testing) "FREE" else "$cost", color = if (ok) Color.White else Color.White.copy(alpha = 0.45f), fontSize = 17.sp, fontWeight = FontWeight.Black, fontFamily = MONO2)
                Text(if (testing) "TEST" else "⚡ STRIKES", color = base.copy(alpha = if (ok) 0.9f else 0.4f), fontSize = 6.sp, fontWeight = FontWeight.Black, fontFamily = MONO2)
                if (sur > 0 && why == null && !testing) Text("+$sur heat", color = Color(0xFFFF7043), fontSize = 7.sp, fontFamily = MONO2)
            }
        }
    }
}

/** A small animated icon per call: spinning helicopter rotor, jet with a flickering exhaust, formations, a missile fan, a blast. */
private fun DrawScope.callIcon(id: Int, t: Float, col: Color, live: Boolean) {
    val c = Offset(size.width / 2f, size.height / 2f); val r = size.minDimension / 2f
    val tau = 2f * PI.toFloat()
    fun heli(cx: Float, cy: Float, s: Float) {
        drawOval(col.copy(alpha = 0.9f), Offset(cx - s * 0.5f, cy - s * 0.22f), Size(s, s * 0.44f))
        drawLine(col, Offset(cx + s * 0.4f, cy), Offset(cx + s * 0.95f, cy - s * 0.12f), s * 0.07f, StrokeCap.Round)
        drawLine(col.copy(alpha = 0.7f), Offset(cx - s * 0.35f, cy + s * 0.32f), Offset(cx + s * 0.3f, cy + s * 0.32f), s * 0.05f, StrokeCap.Round)
        val rl = s * (0.55f + 0.45f * (if (live) abs(cos(t * tau * 6f)) else 1f))
        drawLine(Color.White.copy(alpha = 0.85f), Offset(cx - rl, cy - s * 0.3f), Offset(cx + rl, cy - s * 0.3f), s * 0.05f, StrokeCap.Round)
        drawLine(col, Offset(cx, cy - s * 0.22f), Offset(cx, cy - s * 0.3f), s * 0.05f)
    }
    fun jet(cx: Float, cy: Float, s: Float) {
        val p = Path().apply { moveTo(cx + s * 0.6f, cy); lineTo(cx - s * 0.5f, cy - s * 0.42f); lineTo(cx - s * 0.25f, cy); lineTo(cx - s * 0.5f, cy + s * 0.42f); close() }
        drawPath(p, col.copy(alpha = 0.9f))
        val fl = 0.5f + 0.5f * (if (live) sin(t * tau * 8f) else 0.3f)
        drawLine(Color(0xFFFFB74D).copy(alpha = 0.8f), Offset(cx - s * 0.3f, cy), Offset(cx - s * (0.5f + 0.4f * fl), cy), s * 0.08f, StrokeCap.Round)
    }
    when (id) {
        1 -> heli(c.x, c.y, r * 1.2f)
        2 -> { heli(c.x, c.y - r * 0.1f, r * 1.1f); drawLine(Color(0xFFFFB74D), Offset(c.x - r * 0.3f, c.y + r * 0.5f), Offset(c.x - r * 0.3f + r * 0.5f * ((t * 2f) % 1f), c.y + r * 0.5f), 2f); drawCircle(Color.White, 2f, Offset(c.x - r * 0.3f + r * 0.5f * ((t * 2f) % 1f), c.y + r * 0.5f)) }
        3 -> jet(c.x, c.y, r * 1.3f)
        4 -> { heli(c.x - r * 0.1f, c.y - r * 0.3f, r * 0.9f); jet(c.x + r * 0.05f, c.y + r * 0.35f, r * 1.0f) }
        5 -> for (k in -2..2) { val a = k * 0.28f; val l = r * (0.5f + 0.5f * ((t * 1.5f + abs(k) * 0.15f) % 1f))
            drawLine(col, Offset(c.x - r * 0.4f, c.y), Offset(c.x - r * 0.4f + cos(a) * l * 1.6f, c.y + sin(a) * l * 1.6f), 2.5f, StrokeCap.Round); drawCircle(Color(0xFFFFB74D), 2.5f, Offset(c.x - r * 0.4f + cos(a) * l * 1.6f, c.y + sin(a) * l * 1.6f)) }
        6 -> { jet(c.x, c.y - r * 0.4f, r * 0.8f); jet(c.x - r * 0.35f, c.y + r * 0.1f, r * 0.8f); jet(c.x + r * 0.05f, c.y + r * 0.55f, r * 0.8f) }
        else -> {
            val k = (t * 1.2f) % 1f
            drawCircle(col.copy(alpha = 0.25f + 0.2f * (1f - k)), r * (0.35f + 0.6f * k), c)
            drawCircle(Color.White.copy(alpha = 0.9f * (1f - k)), r * (0.2f + 0.5f * k), c, style = Stroke(2.5f))
            for (q in 0 until 10) { val a = q * tau / 10f + t * 0.8f; drawLine(col, Offset(c.x + cos(a) * r * 0.3f, c.y + sin(a) * r * 0.3f), Offset(c.x + cos(a) * r * (0.6f + 0.35f * k), c.y + sin(a) * r * (0.6f + 0.35f * k)), 2f, StrokeCap.Round) }
            drawCircle(Color.White, r * 0.14f, c)
        }
    }
}
