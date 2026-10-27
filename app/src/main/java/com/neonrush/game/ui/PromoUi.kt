package com.neonrush.game.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.sin

/**
 * Advertises the two side modes to players who have never opened them. It disappears for good the first time a mode is played,
 * and rotates between the two while both are still new. The Storm card shows a lock line if Storm Run is not open yet.
 */
@Composable
fun ModePromo(stormNew: Boolean, stormOpen: Boolean, duelNew: Boolean, onStorm: () -> Unit, onDuel: () -> Unit, modifier: Modifier = Modifier) {
    val kinds = buildList { if (stormNew) add(0); if (duelNew) add(1) }
    if (kinds.isEmpty()) return
    var i by remember { mutableStateOf(0) }
    LaunchedEffect(kinds.size) { while (kinds.size > 1) { delay(4500); i = (i + 1) % kinds.size } }
    val kind = kinds[i % kinds.size]
    val a = if (kind == 0) Color(0xFFFFD23F) else Color(0xFFFF4DD2)
    val b = if (kind == 0) Color(0xFF00E5FF) else Color(0xFF3DE8FF)
    val inf = rememberInfiniteTransition(label = "promo")
    val t by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart), label = "t")
    val p by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "p")
    val shape = RoundedCornerShape(14.dp)
    val locked = kind == 0 && !stormOpen
    Row(
        modifier.fillMaxWidth().height(70.dp)
            .clip(shape)
            .background(Brush.horizontalGradient(listOf(a.copy(alpha = 0.22f), Color(0xFF0B1022), b.copy(alpha = 0.22f))))
            .border(2.dp, Brush.linearGradient(listOf(a.copy(alpha = 0.5f + 0.45f * p), b.copy(alpha = 0.9f - 0.4f * p), a.copy(alpha = 0.5f))), shape)
            .clickable { if (kind == 0) onStorm() else onDuel() }
            .drawWithContent {
                drawContent()
                val w = size.width; val h = size.height
                val x = (t * 1.5f - 0.25f) * w
                drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.12f), Color.Transparent), startX = x - w * 0.12f, endX = x + w * 0.12f), blendMode = BlendMode.Plus)
                if (kind == 0 && sin(t * 40f) > 0.93f) {          // a quick lightning flicker across the Storm banner
                    val sx = w * (0.2f + 0.6f * ((t * 7f) % 1f))
                    drawLine(Color.White.copy(alpha = 0.8f), Offset(sx, 0f), Offset(sx + 14f, h * 0.45f), strokeWidth = 3f, cap = StrokeCap.Round, blendMode = BlendMode.Plus)
                    drawLine(Color.White.copy(alpha = 0.8f), Offset(sx + 14f, h * 0.45f), Offset(sx - 4f, h), strokeWidth = 3f, cap = StrokeCap.Round, blendMode = BlendMode.Plus)
                }
            }
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Crossfade(targetState = kind, animationSpec = tween(500), label = "promoKind") { k ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f, fill = false)) {
                PilotPose(if (k == 0) Pose.BOOST else Pose.RUN_RIBBON, 54.dp)
                Spacer(Modifier.width(8.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (k == 0) "⚡ STORM RUN" else "👻 GHOST DUEL", color = if (k == 0) Color(0xFFFFD23F) else Color(0xFFFF4DD2),
                            fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, fontSize = 14.sp)
                        Spacer(Modifier.width(6.dp))
                        Text("NEW", color = Color.Black, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, fontSize = 9.sp,
                            modifier = Modifier.graphicsLayer { val s = 1f + 0.08f * p; scaleX = s; scaleY = s }.clip(RoundedCornerShape(5.dp)).background(Color(0xFFFF5252)).padding(horizontal = 5.dp, vertical = 1.dp))
                    }
                    Text(
                        if (k == 0) (if (stormOpen) "90 seconds in the lightning. Free gems every day!" else "Opens tomorrow: dodge lightning, win gems daily")
                        else "Race a rival head to head. Beat them, they get faster!",
                        color = Color.White.copy(alpha = 0.8f), fontSize = 10.sp, maxLines = 2
                    )
                }
            }
        }
        Spacer(Modifier.weight(0.01f))
        if (!locked) Text("PLAY ›", color = Color.Black, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, fontSize = 12.sp,
            modifier = Modifier.padding(start = 6.dp).graphicsLayer { val s = 1f + 0.07f * p; scaleX = s; scaleY = s }
                .clip(RoundedCornerShape(16.dp)).background(a).padding(horizontal = 12.dp, vertical = 8.dp))
    }
}
