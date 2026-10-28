package com.neonrush.game.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neonrush.game.RiskGates
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

/**
 * LEGENDARY GOLDEN GATES: on Legendary only, about every 800 m a tall golden gate crosses the track with one narrow opening. Slip through it
 * and the crown gate bursts open: +4 gems (shares the per-run gem cap with Risk Gates) and +40 score. Miss it and nothing is lost.
 * Lives inside the gameplay box so its heights match the pilot's. Drawn in code; the gate artwork appears as the celebration.
 */
@Composable
fun GoldenGateOverlay(distanceMeters: Float, pilotY: Int, active: Boolean, enabled: Boolean, onPass: (Int) -> Unit, modifier: Modifier = Modifier) {
    if (!enabled) return
    val distRef = rememberUpdatedState(distanceMeters)
    val yRef = rememberUpdatedState(pilotY)
    val passRef = rememberUpdatedState(onPass)
    val art = rememberArt("golden_gate")
    var gx by remember { mutableStateOf(-1f) }
    var center by remember { mutableStateOf(0.5f) }
    var phase by remember { mutableStateOf(0f) }
    var msg by remember { mutableStateOf("") }
    var msgGood by remember { mutableStateOf(true) }
    val splash = remember { Animatable(0f) }
    var splashing by remember { mutableStateOf(false) }
    val half = 0.10f

    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        var last = 0L; var nextAt = 600f; var t = 0f
        while (true) {
            val now = withFrameNanos { it }
            val dt = if (last == 0L) 0.016f else ((now - last) / 1_000_000_000f).coerceAtMost(0.05f)
            last = now
            val d = distRef.value
            if (d < 60f && nextAt > 700f) { nextAt = 600f; gx = -1f }
            t += dt; phase = t
            if (gx < 0f && d >= nextAt) { center = 0.28f + Random.nextFloat() * 0.44f; gx = 1.1f }
            if (gx >= 0f) {
                gx -= dt / 3.2f
                if (gx <= 0.2f) {
                    if (abs(yRef.value / 100f - center) <= half + 0.02f) {
                        val g = RiskGates.add(4)
                        passRef.value(40)
                        msg = if (g > 0) "CROWN GATE!  +$g 💎  +40" else "CROWN GATE!  +40"; msgGood = true
                        splashing = true
                    } else { msg = "GOLDEN GATE MISSED"; msgGood = false }
                    gx = -1f
                    nextAt = d + 750f + Random.nextInt(250)
                }
            }
        }
    }
    LaunchedEffect(msg) { if (msg.isNotEmpty()) { kotlinx.coroutines.delay(1500); msg = "" } }
    LaunchedEffect(splashing) {
        if (splashing) { splash.snapTo(0f); splash.animateTo(1f, tween(1500)); splashing = false }
    }

    Box(modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width; val h = size.height
            if (gx >= 0f) {
                val x = gx * w; val thick = w * 0.03f
                val pulse = 0.6f + 0.4f * sin(phase * 7f)
                val top = (center - half) * h; val bot = (center + half) * h
                val gold = Brush.horizontalGradient(listOf(Color(0xFF8D6200), Color(0xFFFFD54F), Color(0xFFB8860B)), startX = x, endX = x + thick)
                drawRect(gold, Offset(x, 0f), Size(thick, top))
                drawRect(gold, Offset(x, bot), Size(thick, h - bot))
                drawRect(Color(0xFFFFF59D).copy(alpha = 0.35f + 0.3f * pulse), Offset(x - thick * 0.6f, top), Size(thick * 2.2f, bot - top))
                drawRect(Color(0xFFFFEE58), Offset(x - thick * 0.5f, top - 3f), Size(thick * 2f, 5f))
                drawRect(Color(0xFFFFEE58), Offset(x - thick * 0.5f, bot - 2f), Size(thick * 2f, 5f))
            }
            if (splash.value > 0f && splash.value < 1f && art != null) {
                val k = splash.value
                val a = (if (k < 0.2f) k / 0.2f else 1f - (k - 0.2f) / 0.8f).coerceIn(0f, 1f)
                val dw = w * (0.55f + 0.25f * k); val dh = dw * art.height / art.width
                drawImage(art, dstOffset = IntOffset(((w - dw) / 2f).toInt(), (h * 0.42f - dh / 2f).toInt()), dstSize = IntSize(dw.toInt(), dh.toInt()), alpha = a)
            }
        }
        if (gx >= 0f) Text("▶ GOLDEN GATE  +4 💎", color = Color(0xFFFFD54F), fontSize = 11.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 30.dp))
        if (msg.isNotEmpty()) {
            Text(msg, color = if (msgGood) Color(0xFFFFE082) else Color(0xFFFF8A80), fontSize = 17.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace,
                style = TextStyle(shadow = Shadow(Color.Black, Offset.Zero, 12f)), modifier = Modifier.align(Alignment.TopCenter).padding(top = 56.dp))
        }
    }
}
