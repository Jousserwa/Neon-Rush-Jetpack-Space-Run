package com.neonrush.game.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neonrush.game.RiskGates
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

/**
 * RISK GATES overlay. Every ~450m a gate slides in from the right with a wide SAFE corridor and a narrow moving RISKY corridor
 * on the opposite side. Where the pilot is when the gate reaches the ship decides the prize. Drawn in code, no art.
 */
@Composable
fun RiskGateOverlay(distanceMeters: Float, pilotY: Int, active: Boolean, isPro: Boolean, modifier: Modifier = Modifier) {
    val distRef = rememberUpdatedState(distanceMeters)
    val yRef = rememberUpdatedState(pilotY)
    var gx by remember { mutableStateOf(-1f) }       // gate position (fraction of width); -1 = no gate
    var safeC by remember { mutableStateOf(0.3f) }
    var riskC by remember { mutableStateOf(0.7f) }
    var phase by remember { mutableStateOf(0f) }
    var msg by remember { mutableStateOf("") }
    var msgGood by remember { mutableStateOf(true) }
    var msgT by remember { mutableStateOf(0f) }
    val safeHalf = 0.13f
    val riskHalf = 0.055f

    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        var last = 0L
        var nextAt = 350f
        var t = 0f
        while (true) {
            val now = androidx.compose.runtime.withFrameNanos { it }
            val dt = if (last == 0L) 0.016f else ((now - last) / 1_000_000_000f).coerceAtMost(0.05f)
            last = now
            val d = distRef.value
            if (d < 60f && nextAt > 400f) { nextAt = 350f; gx = -1f }            // a new run started
            t += dt; phase = t
            if (gx < 0f && d >= nextAt) {
                val safeTop = Random.nextBoolean()
                safeC = if (safeTop) 0.3f else 0.7f
                riskC = if (safeTop) 0.7f else 0.3f
                gx = 1.1f
            }
            if (gx >= 0f) {
                gx -= dt / 3.0f
                if (gx <= 0.2f) {
                    val y = yRef.value / 100f
                    val rc = riskC + sin(t * 2.2f) * 0.05f
                    when {
                        abs(y - rc) <= riskHalf + 0.02f -> { val g = RiskGates.add(if (isPro) RiskGates.RISK_GEMS_PRO else RiskGates.RISK_GEMS); msg = if (g > 0) "RISK PAID OFF  +$g 💎" else "RISK PAID OFF (run cap reached)"; msgGood = true }
                        abs(y - safeC) <= safeHalf -> { val g = RiskGates.add(RiskGates.SAFE_GEMS); msg = if (g > 0) "SAFE  +$g 💎" else "SAFE"; msgGood = true }
                        else -> { msg = "GATE MISSED"; msgGood = false }
                    }
                    msgT = 1.4f
                    gx = -1f
                    nextAt = d + 450f + Random.nextInt(200)
                }
            }
            if (msgT > 0f) msgT -= dt
        }
    }

    Box(modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            if (gx < 0f) return@Canvas
            val w = size.width; val h = size.height
            val x = gx * w; val thick = w * 0.025f
            val rc = riskC + sin(phase * 2.2f) * 0.05f
            // wall = everything except the two openings
            val bands = listOf(safeC - safeHalf to safeC + safeHalf, rc - riskHalf to rc + riskHalf).sortedBy { it.first }
            var from = 0f
            for ((a, b) in bands) {
                if (a > from) drawRect(Color(0xCC1B1035), Offset(x, from * h), Size(thick, (a - from) * h))
                from = b
            }
            if (from < 1f) drawRect(Color(0xCC1B1035), Offset(x, from * h), Size(thick, (1f - from) * h))
            val pulse = 0.6f + 0.4f * sin(phase * 8f)
            // SAFE corridor: calm cyan edges
            drawRect(Color(0x553DE8FF), Offset(x - thick * 0.5f, (safeC - safeHalf) * h), Size(thick * 2f, safeHalf * 2f * h))
            drawRect(Color(0xFF3DE8FF), Offset(x - thick * 0.5f, (safeC - safeHalf) * h - 3f), Size(thick * 2f, 4f))
            drawRect(Color(0xFF3DE8FF), Offset(x - thick * 0.5f, (safeC + safeHalf) * h - 1f), Size(thick * 2f, 4f))
            // RISKY corridor: hot gold, pulsing
            drawRect(Color(0x66FFD23F).copy(alpha = 0.3f + 0.3f * pulse), Offset(x - thick, (rc - riskHalf) * h), Size(thick * 3f, riskHalf * 2f * h))
            drawRect(Color(0xFFFF4D4D).copy(alpha = pulse), Offset(x - thick * 0.5f, (rc - riskHalf) * h - 3f), Size(thick * 2f, 5f))
            drawRect(Color(0xFFFF4D4D).copy(alpha = pulse), Offset(x - thick * 0.5f, (rc + riskHalf) * h - 2f), Size(thick * 2f, 5f))
        }
        if (gx >= 0f) {
            Text(
                "▶ SAFE +${RiskGates.SAFE_GEMS}   |   RISKY +${if (isPro) RiskGates.RISK_GEMS_PRO else RiskGates.RISK_GEMS} 💎",
                color = Color(0xFFFFD23F), fontSize = 11.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 64.dp)
            )
        }
        if (msgT > 0f) {
            Text(
                msg, color = if (msgGood) Color(0xFF69F0AE) else Color(0xFFFF8A80), fontSize = 16.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 90.dp)
            )
        }
    }
}
