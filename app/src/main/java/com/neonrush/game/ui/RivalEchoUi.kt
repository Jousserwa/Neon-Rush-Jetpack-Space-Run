package com.neonrush.game.ui

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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import com.neonrush.game.RivalEcho
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * RIVAL ECHO overlay: a faint copy of your best run on this difficulty flies beside you. If it is ahead it appears further right (or an arrow
 * at the edge when it is far); if you are ahead it trails behind you. A small chip shows the gap in metres. Also records this run for next time.
 */
@Composable
fun RivalEchoOverlay(distanceMeters: Float, pilotY: Int, active: Boolean, tierKey: String, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val distRef = rememberUpdatedState(distanceMeters)
    val yRef = rememberUpdatedState(pilotY)
    var echoD by remember { mutableStateOf(-1f) }
    var echoY by remember { mutableStateOf(50f) }
    var gone by remember { mutableStateOf(false) }       // the echo's run ended before you did
    var shown by remember { mutableStateOf(false) }

    LaunchedEffect(active, tierKey) {
        if (!active) return@LaunchedEffect
        RivalEcho.begin(ctx, tierKey)
        shown = RivalEcho.hasEcho(); gone = false; echoD = -1f
        var last = 0L; var t = 0f; var acc = 0f; var seenMoving = false
        while (true) {
            val now = androidx.compose.runtime.withFrameNanos { it }
            val dt = if (last == 0L) 0.016f else ((now - last) / 1_000_000_000f).coerceAtMost(0.05f)
            last = now
            val d = distRef.value
            if (d < 30f && seenMoving && t > 3f) {            // a new run started inside the same composition
                RivalEcho.begin(ctx, tierKey); t = 0f; acc = 0f; seenMoving = false; gone = false; shown = RivalEcho.hasEcho()
            }
            if (d > 5f) seenMoving = true
            if (!seenMoving) continue                          // the clock starts when the pilot actually moves
            t += dt; acc += dt
            if (acc >= 0.5f) { acc = 0f; RivalEcho.sample(t, d, yRef.value) }
            val e = RivalEcho.at(t)
            if (e == null) { if (shown && t > RivalEcho.duration()) gone = true; echoD = -1f } else { echoD = e[0]; echoY = e[1] }
        }
    }

    if (!shown) return
    BoxWithConstraints(modifier.fillMaxSize()) {
        val wPx = with(LocalDensity.current) { maxWidth.toPx() }
        val hPx = with(LocalDensity.current) { maxHeight.toPx() }
        val sizePx = with(LocalDensity.current) { 46.dp.toPx() }
        if (echoD >= 0f) {
            val gap = echoD - distanceMeters
            val x = (wPx * 0.2f + gap * wPx * 0.0035f).coerceIn(-sizePx * 0.5f, wPx - sizePx * 0.6f)
            val y = hPx * (echoY / 100f)
            PilotPose(Pose.BOOST, 46.dp, animated = false,
                modifier = Modifier.offset { IntOffset((x - sizePx * 0.4f).roundToInt(), (y - sizePx * 0.5f).roundToInt()) }.graphicsLayer { alpha = 0.42f })
            Text(
                (if (gap >= 0) "ECHO +" else "ECHO ") + "${gap.roundToInt()}m", color = Color(0xFF7DF9FF), fontSize = 10.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace,
                modifier = Modifier.align(Alignment.TopStart).padding(start = 12.dp, top = 120.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xAA080620)).padding(horizontal = 8.dp, vertical = 3.dp)
            )
        } else if (gone) {
            Text("ECHO FINISHED • you are past your best!", color = Color(0xFF69F0AE), fontSize = 10.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace,
                modifier = Modifier.align(Alignment.TopStart).padding(start = 12.dp, top = 120.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xAA080620)).padding(horizontal = 8.dp, vertical = 3.dp))
        }
    }
}
