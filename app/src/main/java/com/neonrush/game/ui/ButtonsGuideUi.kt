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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.sin

/** One stop of the on-screen button tour: where it sits on the mock screen (fractions), its colour, and what it does in plain words. */
private class HudStop(val tag: String, val x: Float, val y: Float, val r: Float, val wide: Boolean, val col: Color, val title: String, val body: String)

private val HUD_STOPS = listOf(
    HudStop("STEER", 0.30f, 0.50f, 0.13f, false, Color(0xFF00E5FF), "STEERING · no button",
        "Put a finger anywhere and drag up or down. Your pilot flies forward by itself. Stay close to the glowing guide line for more points."),
    HudStop("FUEL", 0.60f, 0.93f, 0.30f, true, Color(0xFF69F0AE), "FUEL BAR · bottom",
        "Your fuel drains as you fly. Fly through fuel canisters to fill it. If it runs low, tap the bar to refuel. The bar shows the gem cost (or FREE) and how many refuels you have left."),
    HudStop("FIRE", 0.85f, 0.80f, 0.115f, false, Color(0xFFFF4081), "FIRE · bottom right",
        "Press and hold to shoot obstacles, enemy bullets and bosses. The number is how many shots you have. EMPTY means none left: pick up armor to reload, or buy shots at the Ammo Depot (they load by themselves)."),
    HudStop("BOMB", 0.85f, 0.55f, 0.095f, false, Color(0xFFFFB300), "BOMB · right side",
        "Tap to detonate a bomb. It wipes out every small boss spawn at once. The x number is how many bombs you carry."),
    HudStop("DASH", 0.17f, 0.80f, 0.095f, false, Color(0xFF80F0FF), "DASH · SMASH (Pro runs)",
        "A burst of speed: about 0.7 seconds where nothing can hurt you. It recharges in 8 seconds, shown as a ring and a countdown."),
    HudStop("MAGNET", 0.37f, 0.80f, 0.095f, false, Color(0xFFFF4DD2), "MAGNET PULSE (Pro runs)",
        "Pulls every gem and fuel canister on the screen straight to you. Recharges in 12 seconds."),
    HudStop("OVERDRIVE", 0.57f, 0.80f, 0.10f, false, Color(0xFFFFC21A), "OVERDRIVE (Pro runs)",
        "Charges as you play, kill things and hit bosses. When it says READY, tap it for 6 seconds of super-fast shots that never overheat, plus bonus points every second."),
    HudStop("WINGMAN", 0.30f, 0.64f, 0.10f, false, Color(0xFFFFB74D), "WINGMAN · appears in danger",
        "Shows up when a boss attacks or 2 or more hazards hit at once. Tap it to call your wingman: 3 seconds of fighting, 2 seconds reloading, back again 3 times. A call is only used when you tap."),
    HudStop("AIR", 0.92f, 0.36f, 0.075f, false, Color(0xFF69F0AE), "AIR SUPPORT · small round button (Pro)",
        "Appears when a boss or heavy attacks overwhelm you and stays until you tap it. Choose a call: a helicopter or jet flies in, you take no damage while it is there, and missiles cut through boss shields."),
    HudStop("MIL", 0.14f, 0.20f, 0.10f, true, Color(0xFFFFC400), "MILITARY SHIELD · top left",
        "When active, a 10-part bar shows its hits left and damage %. After the 10th hit it turns red and blocks everything for 4 more seconds. The next shield from your stock switches on by itself when you are hit (3 per run, 6 with AUTO 6). Tap MIL to switch one on yourself."),
    HudStop("ALERT", 0.88f, 0.43f, 0.08f, false, Color(0xFFFF5252), "RED ! DANGER CHIP",
        "In real danger, if the weapon you need is out of stock, this chip flashes for half a second. Tap it to buy a shield, bombs or an airstrike with gems. Ignore it and it fades."),
    HudStop("PAUSE", 0.90f, 0.12f, 0.075f, false, Color(0xFF00E5FF), "PAUSE · top right",
        "Stops the run for a short time (the number shows pauses left). When you resume, a 3-2-1 countdown gives you time to get ready."),
    HudStop("QUIT", 0.14f, 0.93f, 0.12f, true, Color(0xFFB0BEC5), "QUIT · bottom left",
        "Ends the run and takes you to your results. Your gems and score so far are kept.")
)

/** Animated tour of every control on the race screen. Auto-plays; tap a coloured chip to jump to a button. */
@Composable
fun HudGuideCard(modifier: Modifier = Modifier) {
    var i by remember { mutableIntStateOf(0) }
    var hold by remember { mutableStateOf(0) }
    LaunchedEffect(i, hold) { delay(5200); i = (i + 1) % HUD_STOPS.size }
    val inf = rememberInfiniteTransition(label = "hudg")
    val t by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart), label = "t")
    val s = HUD_STOPS[i]
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFF0B0A22)).border(1.dp, s.col.copy(alpha = 0.6f), RoundedCornerShape(14.dp)).padding(12.dp)) {
        Text("🎯 THE BUTTONS ON YOUR SCREEN", color = s.col, fontSize = 13.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
        Text("Watch the highlight. It shows where each button is and what it does. Tap a chip to jump.", color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(top = 2.dp, bottom = 8.dp))
        Canvas(Modifier.fillMaxWidth().height(250.dp).background(Color(0xFF05041A), RoundedCornerShape(12.dp))) { drawMockScreen(i, t) }
        Text(s.title, color = s.col, fontSize = 13.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(top = 10.dp))
        Text(s.body, color = Color.White, fontSize = 12.sp, lineHeight = 17.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(top = 4.dp).heightIn(min = 90.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            HUD_STOPS.withIndex().chunked(5).forEach { rowItems ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    rowItems.forEach { (k, st) ->
                        Text(st.tag, color = if (k == i) Color.Black else st.col, fontSize = 9.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, maxLines = 1,
                            modifier = Modifier.clip(RoundedCornerShape(50)).background(if (k == i) st.col else st.col.copy(alpha = 0.14f)).border(1.dp, st.col.copy(alpha = 0.7f), RoundedCornerShape(50))
                                .clickable { i = k; hold++ }.padding(horizontal = 7.dp, vertical = 4.dp))
                    }
                }
            }
        }
    }
}

private fun DrawScope.drawMockScreen(sel: Int, t: Float) {
    val w = size.width; val h = size.height
    // faint track + stars so it reads as the game
    drawRect(Brush.verticalGradient(listOf(Color(0xFF14083A), Color(0xFF05041A))))
    for (k in 0 until 26) { val x = ((k * 0.137f + 0.03f) % 1f) * w; val y = ((k * 0.311f + 0.1f) % 1f) * h; drawCircle(Color.White.copy(alpha = 0.25f), 1.4f, Offset(x, y)) }
    drawLine(Color(0xFF00E5FF).copy(alpha = 0.25f), Offset(0f, h * 0.5f), Offset(w, h * 0.5f), strokeWidth = 2f)
    val pulse = 0.5f + 0.5f * sin(t * 2f * PI.toFloat())
    HUD_STOPS.forEachIndexed { k, st ->
        val c = Offset(st.x * w, st.y * h); val r = st.r * w
        val on = k == sel
        val a = if (on) 1f else 0.28f
        if (st.wide) {
            val ww = r * 2.2f; val hh = r * 0.55f
            drawRoundRect(Color(0xFF0E0C2C).copy(alpha = 0.9f), Offset(c.x - ww / 2f, c.y - hh / 2f), Size(ww, hh), CornerRadius(hh / 2f))
            drawRoundRect(st.col.copy(alpha = a), Offset(c.x - ww / 2f, c.y - hh / 2f), Size(ww, hh), CornerRadius(hh / 2f), style = Stroke(if (on) 3f else 1.5f))
            if (on) drawRoundRect(st.col.copy(alpha = 0.35f * pulse), Offset(c.x - ww / 2f - 6f, c.y - hh / 2f - 6f), Size(ww + 12f, hh + 12f), CornerRadius(hh / 2f + 6f), style = Stroke(5f))
        } else if (st.tag == "STEER") {
            // the pilot with up / down arrows
            drawCircle(st.col.copy(alpha = 0.18f * a), r, c)
            drawCircle(st.col.copy(alpha = a), r * 0.45f, c)
            val dy = sin(t * 2f * PI.toFloat()) * r * 0.5f
            if (on) { drawLine(st.col, Offset(c.x + r * 1.5f, c.y - r * 0.9f + dy), Offset(c.x + r * 1.5f, c.y + r * 0.9f + dy), strokeWidth = 4f)
                drawCircle(Color.White.copy(alpha = 0.8f), 9f, Offset(c.x + r * 1.5f, c.y + dy)) }
        } else {
            drawCircle(Color(0xFF0E0C2C), r, c)
            drawCircle(st.col.copy(alpha = a), r, c, style = Stroke(if (on) 3.5f else 1.8f))
            drawCircle(st.col.copy(alpha = 0.22f * a), r * 0.55f, c)
            if (on) { drawCircle(st.col.copy(alpha = 0.55f * (1f - pulse)), r * (1.1f + 0.7f * pulse), c, style = Stroke(4f)) }
        }
    }
}
