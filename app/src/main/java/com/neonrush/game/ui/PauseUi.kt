package com.neonrush.game.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neonrush.game.Pause

private val PCYAN = Color(0xFF3DE8FF)

/**
 * The pause button (top right, under the speed readout), the pause screen with the time left in this pause, the 3-2-1 resume countdown,
 * and the automatic pause when the app is left (phone call, home button). See Pause for the rules.
 */
@Composable
fun PauseOverlay(active: Boolean, tierName: String, isPro: Boolean, bossActive: Boolean, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val bossRef = rememberUpdatedState(bossActive)
    var clock by remember { mutableStateOf(0) }
    @Suppress("UNUSED_VARIABLE") val v = Pause.version
    LaunchedEffect(active) {
        if (!active) { Pause.newRun(); return@LaunchedEffect }
        Pause.configure(tierName, isPro); Pause.newRun()
        while (true) {
            kotlinx.coroutines.delay(100)
            if (Pause.paused && !Pause.system && Pause.remainingMs() <= 0L) Pause.resume()   // the pause ran out
            clock += 1
        }
    }
    DisposableEffect(active) {
        val owner = ctx as? androidx.lifecycle.LifecycleOwner
        val obs = androidx.lifecycle.LifecycleEventObserver { _, ev -> if (ev == androidx.lifecycle.Lifecycle.Event.ON_STOP && active) Pause.systemPause() }
        owner?.lifecycle?.addObserver(obs)
        onDispose { owner?.lifecycle?.removeObserver(obs) }
    }
    if (!active) return
    @Suppress("UNUSED_VARIABLE") val tk = clock
    Box(modifier.fillMaxSize()) {
        // ---- button ----
        val why = Pause.blocked(bossRef.value)
        val ok = why == null
        if (!Pause.paused && Pause.countdown() == 0) {
            Box(
                Modifier.align(Alignment.TopEnd).padding(top = 92.dp, end = 10.dp).size(38.dp).clip(CircleShape).background(Color(0xCC080620))
                    .border(1.5.dp, PCYAN.copy(alpha = if (ok) 0.85f else 0.3f), CircleShape)
                    .clickable { if (ok) Pause.pause(bossRef.value) else android.widget.Toast.makeText(ctx, "Pause: $why", android.widget.Toast.LENGTH_SHORT).show() },
                contentAlignment = Alignment.Center
            ) {
                Canvas(Modifier.size(16.dp)) {
                    val c = PCYAN.copy(alpha = if (ok) 1f else 0.35f)
                    drawRect(c, Offset(size.width * 0.12f, 0f), Size(size.width * 0.28f, size.height))
                    drawRect(c, Offset(size.width * 0.60f, 0f), Size(size.width * 0.28f, size.height))
                }
                Text("${Pause.pausesLeft()}", color = Color.White.copy(alpha = if (ok) 0.9f else 0.4f), fontSize = 8.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, modifier = Modifier.align(Alignment.BottomEnd).padding(end = 4.dp, bottom = 1.dp))
            }
        }
        // ---- pause screen ----
        if (Pause.paused) {
            val left = Pause.remainingMs()
            Box(Modifier.fillMaxSize().background(Color(0xE605030F)).pointerInput(Unit) { detectTapGestures { } }, contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                    Text(if (Pause.system) "GAME PAUSED" else "⏸ PAUSED", color = PCYAN, fontSize = 26.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
                    if (Pause.system) Text("You left the game. It waited for you.", color = Color.White.copy(alpha = 0.75f), fontSize = 11.sp, fontFamily = FontFamily.Monospace, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
                    Spacer(Modifier.height(16.dp))
                    Box(Modifier.size(120.dp), contentAlignment = Alignment.Center) {
                        Canvas(Modifier.fillMaxSize()) {
                            val st = Stroke(8.dp.toPx(), cap = StrokeCap.Round)
                            drawArc(Color.White.copy(alpha = 0.12f), 0f, 360f, false, style = st)
                            val k = if (Pause.system) 1f else (left / Pause.perPauseMs.toFloat()).coerceIn(0f, 1f)
                            drawArc(if (k < 0.25f) Color(0xFFFF5252) else PCYAN, -90f, 360f * k, false, style = st)
                        }
                        Text(if (Pause.system) "∞" else "${(left + 999L) / 1000L}s", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(if (Pause.system) "This pause is free" else "Pauses left after this one: ${Pause.pausesLeft()}", color = Color.White.copy(alpha = 0.75f), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    Spacer(Modifier.height(18.dp))
                    Text("RESUME ▶", color = Color.Black, fontSize = 16.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace,
                        modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(PCYAN).clickable { Pause.resume() }.padding(horizontal = 30.dp, vertical = 12.dp))
                    Text("The run is frozen. A 3-2-1 countdown and a moment of cover follow.", color = Color.White.copy(alpha = 0.5f), fontSize = 9.sp, fontFamily = FontFamily.Monospace, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 12.dp))
                }
            }
        }
        // ---- 3-2-1 ----
        val cd = Pause.countdown()
        if (!Pause.paused && cd > 0) {
            Box(Modifier.fillMaxSize().background(Color(0x66000000)).pointerInput(Unit) { detectTapGestures { } }, contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("GET READY", color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
                    Text("$cd", color = PCYAN, fontSize = 84.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}
