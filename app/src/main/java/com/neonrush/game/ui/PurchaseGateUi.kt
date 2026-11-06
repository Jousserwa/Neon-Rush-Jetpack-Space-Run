package com.neonrush.game.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.neonrush.game.PurchaseGate
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val CL_ICE = Color(0xFF7DF9FF)
private val CL_MONO = FontFamily.Monospace

/** The SYSTEMS COOLING screen: shown when a second real-money purchase is tried on the same day. Put one call anywhere in a screen that can start purchases. */
@Composable
fun PurchaseGateOverlay() {
    val id = PurchaseGate.coolingFor ?: return
    val ctx = LocalContext.current
    var left by remember { mutableStateOf(PurchaseGate.msUntilOpen()) }
    LaunchedEffect(id) { while (true) { left = PurchaseGate.msUntilOpen(); delay(1000) } }
    val inf = rememberInfiniteTransition(label = "cool")
    val t by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(3600, easing = LinearEasing), RepeatMode.Restart), label = "t")
    val s = (left / 1000L).toInt()
    val clock = "%02d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60)
    Dialog(onDismissRequest = { PurchaseGate.coolingFor = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.padding(horizontal = 22.dp).fillMaxWidth().clip(RoundedCornerShape(22.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF0A1B33), Color(0xFF050818))))
            .border(2.dp, CL_ICE.copy(alpha = 0.55f + 0.25f * sin(t * 2f * PI.toFloat())), RoundedCornerShape(22.dp)).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Canvas(Modifier.size(170.dp)) {
                val c = Offset(size.width / 2f, size.height / 2f); val r = size.minDimension * 0.40f
                val done = (1f - left / 86_400_000f).coerceIn(0f, 1f)
                drawCircle(CL_ICE.copy(alpha = 0.10f), r * 1.25f, c)
                drawCircle(Color.White.copy(alpha = 0.08f), r, c, style = Stroke(10f))
                drawArc(CL_ICE, -90f, 360f * done, false, Offset(c.x - r, c.y - r), Size(r * 2f, r * 2f), style = Stroke(10f, cap = androidx.compose.ui.graphics.StrokeCap.Round))
                // a spinning frost sweep
                val a = t * 2f * PI.toFloat()
                drawLine(CL_ICE.copy(alpha = 0.5f), c, Offset(c.x + cos(a) * r * 0.9f, c.y + sin(a) * r * 0.9f), 3f)
                // drifting ice crystals
                for (k in 0 until 14) {
                    val ph = (t + k * 0.071f) % 1f; val ang = k * 0.9f + t * 1.5f
                    val rr = r * (0.35f + 0.9f * ph)
                    drawCircle(Color.White.copy(alpha = (1f - ph) * 0.8f), 2.5f + (k % 3), Offset(c.x + cos(ang) * rr, c.y + sin(ang) * rr))
                }
                // snowflake in the middle
                for (k in 0 until 3) { val ang = k * PI.toFloat() / 3f + t * 0.6f; drawLine(CL_ICE, Offset(c.x - cos(ang) * r * 0.45f, c.y - sin(ang) * r * 0.45f), Offset(c.x + cos(ang) * r * 0.45f, c.y + sin(ang) * r * 0.45f), 5f) }
            }
            Text("SYSTEMS COOLING", color = CL_ICE, fontSize = 20.sp, fontWeight = FontWeight.Black, fontFamily = CL_MONO, modifier = Modifier.padding(top = 4.dp))
            Text(clock, color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Black, fontFamily = CL_MONO)
            Text("One purchase a day keeps the game fun. ${PurchaseGate.label(id)} will be ready when the timer hits zero. Nothing is lost.", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp, lineHeight = 17.sp, textAlign = TextAlign.Center, fontFamily = CL_MONO, modifier = Modifier.padding(vertical = 10.dp))
            Text("SAVE IT FOR TOMORROW", color = Color.Black, fontSize = 13.sp, fontWeight = FontWeight.Black, fontFamily = CL_MONO,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(CL_ICE)
                    .clickable { PurchaseGate.saveForLater(ctx, id); PurchaseGate.coolingFor = null; android.widget.Toast.makeText(ctx, "Saved. FLASH will remind you tomorrow", android.widget.Toast.LENGTH_SHORT).show() }
                    .padding(vertical = 12.dp), textAlign = TextAlign.Center)
            Text("OK", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp, fontFamily = CL_MONO, modifier = Modifier.padding(top = 10.dp).clickable { PurchaseGate.coolingFor = null }.padding(8.dp))
        }
    }
}
