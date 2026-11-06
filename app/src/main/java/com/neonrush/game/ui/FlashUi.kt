package com.neonrush.game.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neonrush.game.Flash
import com.neonrush.game.MilShield
import com.neonrush.game.PurchaseGate
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

private val FL_MONO = FontFamily.Monospace
private const val FL_TAU = (2.0 * PI).toFloat()

/**
 * FLASH: the advisor card for the home screen. Collapsed it is a slim bolt ribbon; tapped it opens into a holo card with the evidence, the benefit and one button.
 * [onGo] receives the target: upgrades, wingman, magbay, air, bosstheme, hangar, crate or milshield (the caller opens that page or buys it).
 */
@Composable
fun FlashCard(gems: Int, bestZone: Int, fuelTiers: Int, testing: Boolean, onGo: (String) -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val tip = remember(Flash.version, MilShield.version, gems / 25, bestZone, fuelTiers, testing) { Flash.best(ctx, gems, bestZone, fuelTiers, testing) } ?: return
    val col = Color(tip.color)
    var open by remember(tip.id) { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val slide = remember(tip.id) { Animatable(1f) }
    val flash = remember(tip.id) { Animatable(1f) }
    LaunchedEffect(tip.id) {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        launch { flash.animateTo(0f, tween(700)) }
        slide.animateTo(0f, spring(dampingRatio = 0.55f, stiffness = 220f))
    }
    var shown by remember(tip.id) { mutableIntStateOf(0) }
    LaunchedEffect(tip.id, open) { if (open) { shown = 0; while (shown < tip.line.length) { delay(14); shown++ } } }
    val t by rememberInfiniteTransition(label = "flash").animateFloat(0f, 1f, infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Restart), label = "t")
    val locked = tip.lock != null
    val shape = RoundedCornerShape(16.dp)

    Box(modifier.fillMaxWidth()
        .graphicsLayer { translationX = slide.value * size.width * 0.7f; alpha = 1f - 0.7f * slide.value }
        .clip(shape)
        .background(Brush.horizontalGradient(listOf(Color(0xFF0B1030), col.copy(alpha = 0.22f), Color(0xFF0B1030))))
        .border(1.5.dp, col.copy(alpha = 0.55f + 0.35f * sin(t * FL_TAU)), shape)
        .clickable { open = !open }
        .animateContentSize()) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Canvas(Modifier.size(36.dp)) {
                    val w = size.width; val h = size.height
                    drawCircle(col.copy(alpha = 0.22f + 0.2f * sin(t * FL_TAU * 2f)), size.minDimension * 0.55f, Offset(w / 2f, h / 2f))
                    val p = Path().apply {
                        moveTo(w * 0.58f, h * 0.02f); lineTo(w * 0.16f, h * 0.56f); lineTo(w * 0.46f, h * 0.56f)
                        lineTo(w * 0.36f, h * 0.98f); lineTo(w * 0.86f, h * 0.38f); lineTo(w * 0.56f, h * 0.38f); close()
                    }
                    drawPath(p, Brush.verticalGradient(listOf(Color.White, col)))
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("FLASH", color = col, fontSize = 9.sp, fontWeight = FontWeight.Black, fontFamily = FL_MONO)
                    Text(tip.head, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Black, fontFamily = FL_MONO)
                }
                Text(if (open) "▲" else "▼", color = col.copy(alpha = 0.6f + 0.4f * sin(t * FL_TAU)), fontSize = 14.sp)
            }
            AnimatedVisibility(visible = open, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Column(Modifier.padding(top = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(66.dp), contentAlignment = Alignment.Center) {
                            Canvas(Modifier.matchParentSize()) {
                                val r = size.minDimension * 0.44f; val c = Offset(size.width / 2f, size.height / 2f)
                                drawCircle(Color.White.copy(alpha = 0.10f), r, c, style = Stroke(6f))
                                if (locked) drawArc(col, -90f, 360f * tip.lockPct.coerceIn(0f, 1f), false, Offset(c.x - r, c.y - r), Size(r * 2f, r * 2f), style = Stroke(6f, cap = StrokeCap.Round))
                                else drawCircle(col.copy(alpha = 0.6f * (1f - t)), r * (1f + 0.25f * t), c, style = Stroke(4f))
                            }
                            Text(tip.icon, fontSize = 28.sp, modifier = Modifier.offset(y = (sin(t * FL_TAU) * 3f).dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(tip.line.take(shown) + if (shown < tip.line.length) "▌" else "", color = Color.White, fontSize = 12.sp, lineHeight = 17.sp, fontFamily = FL_MONO, modifier = Modifier.weight(1f))
                    }
                    tip.evidence.forEachIndexed { i, ev ->
                        val v by animateFloatAsState(if (open) ev.value else 0f, tween(900, delayMillis = 150 * i), label = "ev")
                        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(ev.label, color = Color.White.copy(alpha = 0.75f), fontSize = 9.sp, fontFamily = FL_MONO, modifier = Modifier.width(112.dp))
                            Box(Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(Color.White.copy(alpha = 0.1f))) {
                                Box(Modifier.fillMaxWidth(v.coerceIn(0f, 1f)).fillMaxHeight().background(Brush.horizontalGradient(listOf(col.copy(alpha = 0.55f), col))))
                            }
                        }
                    }
                    if (tip.preview.isNotEmpty()) {
                        Text(tip.preview, color = col, fontSize = 12.sp, fontWeight = FontWeight.Black, fontFamily = FL_MONO, textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 10.dp).fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(col.copy(alpha = 0.12f + 0.06f * sin(t * FL_TAU))).padding(vertical = 7.dp))
                    }
                    if (tip.lock != null) Text("🔒 ${tip.lock}", color = Color.White.copy(alpha = 0.8f), fontSize = 10.sp, fontFamily = FL_MONO, modifier = Modifier.padding(top = 8.dp))
                    Box(Modifier.fillMaxWidth().padding(top = 12.dp).clip(RoundedCornerShape(12.dp))
                        .background(if (locked) SolidColor(Color.White.copy(alpha = 0.12f)) else Brush.horizontalGradient(listOf(col, Color.White.copy(alpha = 0.85f), col)))
                        .clickable {
                            if (tip.target == "none") Flash.dismiss(ctx, tip.id)
                            else if (locked) android.widget.Toast.makeText(ctx, tip.lock, android.widget.Toast.LENGTH_SHORT).show()
                            else { if (tip.id == "wish") PurchaseGate.clearWish(ctx); if (tip.id == "shw_hangar") Flash.dismiss(ctx, "shw_hangar"); onGo(tip.target); open = false }
                        }.padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                        if (!locked) Canvas(Modifier.matchParentSize()) {
                            val x = size.width * (t * 1.6f - 0.3f)
                            drawLine(Color.White.copy(alpha = 0.55f), Offset(x, 0f), Offset(x - size.height * 0.6f, size.height), size.height * 0.5f)
                        }
                        Text(if (locked) "🔒 ${tip.cta}" else tip.cta, color = if (locked) Color.White.copy(alpha = 0.8f) else Color.Black, fontSize = 13.sp, fontWeight = FontWeight.Black, fontFamily = FL_MONO)
                    }
                    Text("LATER", color = Color.White.copy(alpha = 0.55f), fontSize = 11.sp, fontFamily = FL_MONO, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().clickable { Flash.dismiss(ctx, tip.id) }.padding(top = 8.dp, bottom = 2.dp))
                }
            }
        }
        // HUD overlay: a scan line, corner brackets and the arrival flash
        Canvas(Modifier.matchParentSize()) {
            val y = size.height * t
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, col.copy(alpha = 0.16f), Color.Transparent), startY = y - 40f, endY = y + 40f), Offset(0f, y - 40f), Size(size.width, 80f))
            val b = 16f; val c = col.copy(alpha = 0.8f)
            drawLine(c, Offset(6f, 6f), Offset(6f + b, 6f), 3f); drawLine(c, Offset(6f, 6f), Offset(6f, 6f + b), 3f)
            drawLine(c, Offset(size.width - 6f, 6f), Offset(size.width - 6f - b, 6f), 3f); drawLine(c, Offset(size.width - 6f, 6f), Offset(size.width - 6f, 6f + b), 3f)
            drawLine(c, Offset(6f, size.height - 6f), Offset(6f + b, size.height - 6f), 3f); drawLine(c, Offset(6f, size.height - 6f), Offset(6f, size.height - 6f - b), 3f)
            drawLine(c, Offset(size.width - 6f, size.height - 6f), Offset(size.width - 6f - b, size.height - 6f), 3f); drawLine(c, Offset(size.width - 6f, size.height - 6f), Offset(size.width - 6f, size.height - 6f - b), 3f)
            if (flash.value > 0.01f) drawRect(Color.White.copy(alpha = flash.value * 0.55f))
        }
    }
}
