package com.neonrush.game.ui

import androidx.compose.animation.core.Animatable
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neonrush.game.ProGreeting
import com.neonrush.game.RevenueCatManager
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sin

/** The seven looks of the greeting name, one per weekday (0 = Sunday). */
private class GreetLook(val title: String, val a: Color, val b: Color, val family: FontFamily, val weight: FontWeight, val italic: Boolean, val effect: Int, val crown: Boolean = false)

private val LOOKS = listOf(
    GreetLook("LEGEND SUNDAY", Color(0xFFFFF3B0), Color(0xFFFFB300), FontFamily.Serif, FontWeight.Black, false, 2, crown = true),    // 0 Sunday: gold shimmer + crown
    GreetLook("NEON MONDAY", Color(0xFF3DE8FF), Color(0xFF2979FF), FontFamily.SansSerif, FontWeight.Black, false, 0),              // 1 wave
    GreetLook("GLITCH TUESDAY", Color(0xFFFF4DD2), Color(0xFF7C4DFF), FontFamily.Monospace, FontWeight.Black, false, 1),           // 2 glitch
    GreetLook("GOLDEN WEDNESDAY", Color(0xFFFFD23F), Color(0xFFFF8F00), FontFamily.Serif, FontWeight.Bold, true, 2),               // 3 shimmer
    GreetLook("VIOLET THURSDAY", Color(0xFFB388FF), Color(0xFF536DFE), FontFamily.Cursive, FontWeight.Bold, false, 3),             // 4 glow pulse
    GreetLook("FIRE FRIDAY", Color(0xFFFFEB3B), Color(0xFFFF3D00), FontFamily.SansSerif, FontWeight.Black, true, 4),               // 5 flicker
    GreetLook("RAINBOW SATURDAY", Color(0xFFFF4DD2), Color(0xFF3DE8FF), FontFamily.Monospace, FontWeight.Bold, false, 5)           // 6 rainbow
)

private fun rainbow(h: Float): Color {
    val x = ((h % 1f) + 1f) % 1f * 6f
    val stops = listOf(Color(0xFFFF5252), Color(0xFFFFD23F), Color(0xFF69F0AE), Color(0xFF3DE8FF), Color(0xFF7C4DFF), Color(0xFFFF4DD2), Color(0xFFFF5252))
    val i = floor(x).toInt().coerceIn(0, 5)
    return lerp(stops[i], stops[i + 1], x - i)
}

/** The animated name: every letter has its own colour and motion, depending on today's look. */
@Composable
fun GreetName(name: String, sizeSp: Int, modifier: Modifier = Modifier) {
    val look = LOOKS[ProGreeting.weekday().coerceIn(0, 6)]
    val inf = rememberInfiniteTransition(label = "greet")
    val t by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart), label = "t")
    val fast by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(600, easing = LinearEasing), RepeatMode.Restart), label = "f")
    val entry = remember(name) { Animatable(0f) }
    LaunchedEffect(name) { entry.snapTo(0f); entry.animateTo(name.length.toFloat() + 5f, tween(name.length * 70 + 400, easing = LinearEasing)) }
    val tau = 6.2831855f
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        if (look.crown) Text("👑", fontSize = (sizeSp * 0.8f).sp, modifier = Modifier.padding(end = 4.dp).graphicsLayer { translationY = sin(t * tau) * 3f; rotationZ = sin(t * tau) * 6f })
        name.forEachIndexed { i, ch ->
            if (ch == ' ') { Spacer(Modifier.width((sizeSp * 0.4f).dp)); return@forEachIndexed }
            val a = (entry.value - i).coerceIn(0f, 1f)
            var c1 = look.a; var c2 = look.b
            var dy = 0f; var dx = 0f; var sy = 1f; var glow = 0f
            when (look.effect) {
                0 -> dy = sin((t * tau) - i * 0.6f) * sizeSp * 0.14f
                1 -> {   // glitch: now and then a letter jumps sideways and flashes a different colour
                    val frame = floor(t * 16f).toInt()
                    val hit = ((frame * 7 + i * 13) % 11) == 0
                    if (hit) { dx = ((((frame + i) % 5) - 2) * sizeSp * 0.12f); c1 = Color.White; c2 = Color(0xFF3DE8FF) }
                }
                2 -> { val w = (sin((t * tau) - i * 0.5f) * 0.5f + 0.5f); c1 = lerp(look.a, Color.White, w * 0.7f); c2 = lerp(look.b, look.a, w) }
                3 -> glow = 8f + 10f * (0.5f + 0.5f * sin(t * tau))
                4 -> { val f = abs(sin(fast * tau + i * 1.9f)); sy = 1f + 0.12f * f; dy = -f * sizeSp * 0.05f; c1 = lerp(look.a, Color.White, 0.3f * f) }
                5 -> { val h = t + i * 0.09f; c1 = rainbow(h); c2 = rainbow(h + 0.18f) }
            }
            Text(
                ch.toString(),
                style = TextStyle(
                    brush = Brush.verticalGradient(listOf(c1, c2)), fontSize = sizeSp.sp, fontFamily = look.family, fontWeight = look.weight,
                    fontStyle = if (look.italic) FontStyle.Italic else FontStyle.Normal,
                    shadow = if (glow > 0f) Shadow(look.a.copy(alpha = 0.9f), Offset.Zero, glow) else Shadow(look.b.copy(alpha = 0.45f), Offset(0f, 2f), 6f)
                ),
                modifier = Modifier.graphicsLayer { alpha = a; translationY = dy + (1f - a) * -sizeSp * 0.9f; translationX = dx; scaleY = sy * (0.6f + 0.4f * a); scaleX = 0.6f + 0.4f * a }
            )
        }
    }
}

/**
 * Home greeting. Pro with a name: "GOOD EVENING" over the animated name (tap to change it). Pro without a name: a pulsing invitation to pick one.
 * Free: a plain "WELCOME BACK, PILOT" with a small gold hint that Pro pilots are greeted by name (tap opens the Pro offer).
 */
@Composable
fun ProGreetingBanner(isPro: Boolean, onUpgrade: () -> Unit, modifier: Modifier = Modifier, phrase: String? = null, compact: Boolean = false) {
    val ctx = LocalContext.current
    val name = ProGreeting.name(ctx)
    var edit by remember { mutableStateOf(false) }
    LaunchedEffect(isPro) { if (isPro && ProGreeting.needsPrompt(ctx)) { ProGreeting.markPrompted(ctx); edit = true } }
    if (edit) ProNameDialog(onDismiss = { edit = false })
    val inf = rememberInfiniteTransition(label = "greetBar")
    val t by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(3000, easing = LinearEasing), RepeatMode.Restart), label = "t")
    val breathe = 0.5f + 0.5f * sin(t * 6.2831855f)
    val look = LOOKS[ProGreeting.weekday().coerceIn(0, 6)]
    val shape = RoundedCornerShape(14.dp)
    val tapped = when { !isPro -> onUpgrade; else -> ({ edit = true }) }
    Column(
        modifier = modifier.fillMaxWidth().clip(shape)
            .background(Color(0xFF080620))   // solid dark backing so the greeting stands out from every world behind it
            .background(Brush.horizontalGradient(listOf(look.a.copy(alpha = 0.12f + 0.06f * breathe), look.b.copy(alpha = 0.28f + 0.10f * breathe), look.a.copy(alpha = 0.12f + 0.06f * breathe))))
            .border(2.dp, look.a.copy(alpha = if (isPro) 0.55f + 0.4f * breathe else 0.4f + 0.2f * breathe), shape)
            .clickable { tapped() }
            .drawWithContent {
                drawContent()
                if (isPro) {
                    val w = size.width
                    val x = (t * 1.5f - 0.25f) * w
                    drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.10f), Color.Transparent), startX = x - w * 0.1f, endX = x + w * 0.1f), blendMode = BlendMode.Plus)
                }
            }
            .padding(horizontal = 12.dp, vertical = if (compact) 6.dp else 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (isPro && name.isNotEmpty()) {
            Text((phrase ?: ProGreeting.phrase()) + ",", color = Color.White, fontSize = if (compact) 13.sp else 17.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, letterSpacing = 3.sp,
                style = TextStyle(shadow = Shadow(look.a.copy(alpha = 0.9f), Offset.Zero, 12f)))
            GreetName(name, if (compact) 30 else 42)
            if (!compact) Text(look.title, color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, letterSpacing = 2.sp,
                modifier = Modifier.padding(top = 4.dp).clip(RoundedCornerShape(8.dp)).background(Brush.horizontalGradient(listOf(look.a, look.b))).padding(horizontal = 10.dp, vertical = 2.dp))
        } else if (isPro) {
            Text("✨ TAP TO CHOOSE THE NAME WE GREET YOU WITH ✨", color = look.a.copy(alpha = 0.6f + 0.4f * breathe), fontSize = 11.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, textAlign = TextAlign.Center)
        } else if (!compact) {
            Text(ProGreeting.phrase() + ", PILOT", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, letterSpacing = 3.sp,
                style = TextStyle(shadow = Shadow(Color(0xFF7DF9FF).copy(alpha = 0.9f), Offset.Zero, 12f)))
            Text("🔒 PRO PILOTS ARE GREETED BY NAME, IN A NEW LOOK EVERY DAY", color = Color(0xFFFFD23F).copy(alpha = 0.55f + 0.4f * breathe), fontSize = 10.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, textAlign = TextAlign.Center)
        }
    }
}

/** Pick or change the greeting name, with a live preview in today's look. */
@Composable
fun ProNameDialog(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var text by remember { mutableStateOf(ProGreeting.name(ctx)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0E1230),
        title = { Row(verticalAlignment = Alignment.CenterVertically) { PilotPose(Pose.CHEER, 64.dp); Spacer(Modifier.width(8.dp)); Text("WHAT SHALL WE CALL YOU?", color = Color(0xFFFFD23F), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black, fontSize = 15.sp) } },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Pro pilots are greeted by name every time they open Neon Rush. The look changes with the day of the week. Only you see it.", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(value = text, onValueChange = { v -> text = v.filter { c -> c.isLetterOrDigit() || c == ' ' || c == '\'' || c == '-' || c == '.' }.take(ProGreeting.MAX) }, singleLine = true,
                    label = { Text("Your name (max ${ProGreeting.MAX})") })
                Spacer(Modifier.height(12.dp))
                Box(Modifier.heightIn(min = 54.dp), contentAlignment = Alignment.Center) { if (text.isNotBlank()) GreetName(text.trim(), 26) else Text("preview", color = Color.White.copy(alpha = 0.3f), fontSize = 12.sp) }
            }
        },
        confirmButton = { TextButton(onClick = { ProGreeting.set(ctx, text); onDismiss() }) { Text("SAVE", color = Color(0xFFFFD23F), fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("LATER", color = Color.White.copy(alpha = 0.6f), fontFamily = FontFamily.Monospace) } }
    )
}

/** Settings row (Pro only): shows the current name and opens the dialog. */
@Composable
fun GreetingNameSetting() {
    val isPro by RevenueCatManager.isPro.collectAsState()
    if (!isPro) return
    val ctx = LocalContext.current
    var edit by remember { mutableStateOf(false) }
    if (edit) ProNameDialog(onDismiss = { edit = false })
    val n = ProGreeting.name(ctx)
    Text("✏️ GREETING NAME: ${if (n.isEmpty()) "not set" else n}", color = Color(0xFFFFD23F), fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).border(1.dp, Color(0xFFFFD23F).copy(alpha = 0.4f), RoundedCornerShape(8.dp)).clickable { edit = true }.padding(12.dp))
}
