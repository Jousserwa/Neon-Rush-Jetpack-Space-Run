package com.neonrush.game.ui

import androidx.compose.animation.core.Animatable
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.neonrush.game.BeaconChain
import com.neonrush.game.MysteryDoors
import kotlin.math.sin

private val BGOLD = Color(0xFFFFD23F)
private val BDARK = Color(0xFF0E0A22)

/** BEACON CHAIN card: a tower of 7 lights. Lit = days flown in a row; the tower gains a glowing ring for every finished chain. */
@Composable
fun BeaconChainCard(isPro: Boolean, onClaim: () -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    @Suppress("UNUSED_VARIABLE") val v = BeaconChain.version
    val lit = BeaconChain.lit(ctx)
    val layers = BeaconChain.layers(ctx)
    val can = BeaconChain.canClaim(ctx)
    val played = BeaconChain.playedToday(ctx)
    val inf = rememberInfiniteTransition(label = "beacon")
    val t by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Restart), label = "t")
    val shape = RoundedCornerShape(16.dp)
    val pulse = 0.5f + 0.5f * sin(t * 6.2832f)
    Row(
        modifier.fillMaxWidth().clip(shape)
            .background(Brush.horizontalGradient(listOf(BDARK, Color(0xFF14204A))))
            .border(2.dp, (if (can) BGOLD else Color(0xFF3DE8FF)).copy(alpha = 0.35f + 0.5f * pulse), shape)
            .clickable { if (can) onClaim() }.padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val tower = rememberArt("beacon_${if (lit <= 0) 0 else if (lit < 4) 1 else if (lit < BeaconChain.LENGTH) 2 else 3}")
        Box(Modifier.size(width = 64.dp, height = 96.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val w = size.width; val h = size.height
                for (i in 0 until layers.coerceAtMost(4)) drawCircle(Color(0xFFFFD23F).copy(alpha = 0.18f + 0.1f * pulse), w * (0.5f + 0.12f * i), Offset(w * 0.5f, h * 0.3f), style = androidx.compose.ui.graphics.drawscope.Stroke(2f))
                if (tower != null) {
                    if (lit > 0) drawCircle(BGOLD.copy(alpha = 0.15f + 0.15f * pulse), w * 0.55f, Offset(w * 0.5f, h * 0.2f))
                    val tw = h * tower.width / tower.height
                    drawImage(tower, dstOffset = androidx.compose.ui.unit.IntOffset(((w - tw) / 2f).toInt(), 0), dstSize = androidx.compose.ui.unit.IntSize(tw.toInt(), h.toInt()))
                } else {
                    drawRect(Color(0xFF263159), Offset(w * 0.38f, h * 0.35f), Size(w * 0.24f, h * 0.65f))
                    val c = if (lit > 0) BGOLD else Color(0xFF455A64)
                    drawCircle(c.copy(alpha = if (lit > 0) 0.25f + 0.2f * pulse else 0.1f), w * 0.42f, Offset(w * 0.5f, h * 0.3f))
                    drawCircle(c, w * 0.18f, Offset(w * 0.5f, h * 0.3f))
                }
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text("BEACON CHAIN", color = Color(0xFF3DE8FF), fontSize = 13.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
            Text(
                if (can) "Beacon $lit is lit! Tap to collect +${BeaconChain.reward(lit, isPro)} 💎"
                else if (played) "Today's beacon collected. Come back tomorrow."
                else "Fly one run today to light beacon ${(lit + 1).coerceAtMost(BeaconChain.LENGTH)}. Miss a day and one beacon cools. Each finished chain adds a sky layer: stars, then a planet, then a nebula.",
                color = Color.White.copy(alpha = 0.85f), fontSize = 11.sp, fontFamily = FontFamily.Monospace
            )
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (i in 1..BeaconChain.LENGTH) {
                    val on = i <= lit
                    Box(Modifier.weight(1f).height(10.dp).clip(RoundedCornerShape(5.dp)).background(if (on) BGOLD.copy(alpha = if (i == lit && can) 0.6f + 0.4f * pulse else 1f) else Color.White.copy(alpha = 0.12f)))
                }
            }
            Text("Day 7 pays +${BeaconChain.reward(BeaconChain.LENGTH, isPro)} 💎" + if (!isPro) "  (Pro: +${BeaconChain.reward(BeaconChain.LENGTH, true)})" else "", color = BGOLD.copy(alpha = 0.85f), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
        }
    }
}

/** MYSTERY REALM DOORS card (Home). Locked until a run is finished today; then opens a dialog with three doors. */
@Composable
fun MysteryDoorsCard(isPro: Boolean, onOpenDoor: (Int, (Int) -> Unit) -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    @Suppress("UNUSED_VARIABLE") val v = MysteryDoors.version
    val earned = MysteryDoors.earned(ctx)
    val pick = MysteryDoors.picked(ctx)
    var show by remember { mutableStateOf(false) }
    val inf = rememberInfiniteTransition(label = "doors")
    val t by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(1500, easing = LinearEasing), RepeatMode.Restart), label = "t")
    val pulse = 0.5f + 0.5f * sin(t * 6.2832f)
    val ready = earned && pick < 0
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier.fillMaxWidth().clip(shape).background(Brush.horizontalGradient(listOf(BDARK, Color(0xFF2B1448))))
            .border(2.dp, (if (ready) Color(0xFFFF4DD2) else Color(0xFF7C4DFF)).copy(alpha = 0.35f + 0.5f * (if (ready) pulse else 0.3f)), shape)
            .clickable { if (earned) show = true }.padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(if (ready) "🚪" else if (earned) "✨" else "🔒", fontSize = 28.sp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text("MYSTERY REALM DOORS", color = Color(0xFFFF4DD2), fontSize = 13.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
            Text(
                if (ready) "Three doors are waiting. Pick one!" else if (earned) "You opened a door today. New doors tomorrow." else "Finish a run today to unlock three doors.",
                color = Color.White.copy(alpha = 0.85f), fontSize = 11.sp, fontFamily = FontFamily.Monospace
            )
        }
    }
    if (show) MysteryDoorsDialog(isPro = isPro, onOpenDoor = onOpenDoor, onDismiss = { show = false })
}

@Composable
private fun MysteryDoorsDialog(isPro: Boolean, onOpenDoor: (Int, (Int) -> Unit) -> Unit, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    @Suppress("UNUSED_VARIABLE") val v = MysteryDoors.version
    val pick = MysteryDoors.picked(ctx)
    val opens = remember { List(3) { Animatable(0f) } }
    LaunchedEffect(pick) { if (pick in 0..2) opens[pick].animateTo(1f, tween(900)) }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(BDARK).border(2.dp, Color(0xFFFF4DD2), RoundedCornerShape(20.dp)).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("MYSTERY REALM DOORS", color = Color(0xFFFF4DD2), fontSize = 16.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
            Text(if (pick < 0) "Pick one door. The prize behind it is yours." else "Come back tomorrow for three new doors.", color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp, textAlign = TextAlign.Center, fontFamily = FontFamily.Monospace)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (i in 0 until 3) {
                    val prize = MysteryDoors.prize(ctx, i)
                    val reveal = pick == i || (pick >= 0 && isPro)
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        DoorView(Color(MysteryDoors.COLORS[i]), opens[i].value.let { if (reveal && pick >= 0 && pick != i) 0.85f else it }, if (reveal) prize else null, listOf(0f, 150f, 250f)[i],
                            Modifier.fillMaxWidth().clickable(enabled = pick < 0) { onOpenDoor(i) { } })
                        Text(MysteryDoors.NAMES[i], color = Color(MysteryDoors.COLORS[i]), fontSize = 9.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    }
                }
            }
            if (pick >= 0) {
                Spacer(Modifier.height(8.dp))
                val g = MysteryDoors.prize(ctx, pick)
                Text(if (g > 0) "You found +$g 💎" else "Only dust this time. Try again tomorrow!", color = BGOLD, fontSize = 14.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
                if (!isPro) Text("👑 Pro pilots also peek behind the other two doors", color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
            }
            Spacer(Modifier.height(10.dp))
            Text("CLOSE", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace,
                modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = 0.12f)).clickable { onDismiss() }.padding(horizontal = 20.dp, vertical = 8.dp))
        }
    }
}

private fun hueMatrix(deg: Float, bright: Float): androidx.compose.ui.graphics.ColorFilter {
    val r = Math.toRadians(deg.toDouble()); val c = kotlin.math.cos(r).toFloat(); val s = kotlin.math.sin(r).toFloat()
    val m = floatArrayOf(
        (0.213f + c * 0.787f - s * 0.213f) * bright, (0.715f - c * 0.715f - s * 0.715f) * bright, (0.072f - c * 0.072f + s * 0.928f) * bright, 0f, 0f,
        (0.213f - c * 0.213f + s * 0.143f) * bright, (0.715f + c * 0.285f + s * 0.140f) * bright, (0.072f - c * 0.072f - s * 0.283f) * bright, 0f, 0f,
        (0.213f - c * 0.213f - s * 0.787f) * bright, (0.715f - c * 0.715f + s * 0.715f) * bright, (0.072f + c * 0.928f + s * 0.072f) * bright, 0f, 0f,
        0f, 0f, 0f, 1f, 0f
    )
    return androidx.compose.ui.graphics.ColorFilter.colorMatrix(androidx.compose.ui.graphics.ColorMatrix(m))
}

/** A realm portal. With the portal art: closed = dim and sealed with a "?", open = full glow and a little swell with the prize on top. Without art: the code-drawn arched door. */
@Composable
private fun DoorView(color: Color, open: Float, prize: Int?, hue: Float, modifier: Modifier = Modifier) {
    val art = rememberArt("realm_portal")
    Box(modifier.height(150.dp), contentAlignment = Alignment.Center) {
        if (art != null) {
            Canvas(Modifier.fillMaxSize()) {
                val sc = minOf(size.width / art.width, size.height / art.height) * (0.92f + 0.10f * open)
                val dw = art.width * sc; val dh = art.height * sc
                if (open > 0.05f) drawCircle(color.copy(alpha = 0.25f * open), minOf(size.width, size.height) * 0.55f, Offset(size.width / 2f, size.height / 2f))
                drawImage(art, dstOffset = androidx.compose.ui.unit.IntOffset(((size.width - dw) / 2f).toInt(), ((size.height - dh) / 2f).toInt()),
                    dstSize = androidx.compose.ui.unit.IntSize(dw.toInt(), dh.toInt()), colorFilter = hueMatrix(hue, 0.42f + 0.58f * open))
            }
            if (open < 0.3f) Text("?", color = Color.White.copy(alpha = 0.9f), fontSize = 34.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
            if (prize != null && open > 0.6f) {
                Text(if (prize > 0) "+$prize\n\uD83D\uDC8E" else "\u2728", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center, fontFamily = FontFamily.Monospace,
                    style = androidx.compose.ui.text.TextStyle(shadow = androidx.compose.ui.graphics.Shadow(Color.Black, Offset.Zero, 14f)))
            }
            return@Box
        }
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width; val h = size.height
            drawRoundRect(color.copy(alpha = 0.9f), Offset(0f, 0f), Size(w, h), CornerRadius(w * 0.45f, w * 0.45f))
            val iw = w * 0.82f; val ix = (w - iw) / 2f; val iy = h * 0.06f; val ih = h * 0.92f
            drawRoundRect(Brush.verticalGradient(listOf(Color.White, color.copy(alpha = 0.6f)), iy, iy + ih), Offset(ix, iy), Size(iw, ih), CornerRadius(iw * 0.42f, iw * 0.42f))
            val pw = iw * (1f - 0.92f * open)
            if (pw > 2f) {
                drawRoundRect(Color(0xFF1B1035), Offset(ix, iy), Size(pw, ih), CornerRadius(iw * 0.42f * (pw / iw), iw * 0.42f))
                drawCircle(color, w * 0.045f, Offset(ix + pw * 0.8f, h * 0.55f))
            }
        }
        if (prize != null && open > 0.6f) {
            Text(if (prize > 0) "+$prize\n\uD83D\uDC8E" else "\u2728", color = Color(0xFF1B1035), fontSize = 18.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center, fontFamily = FontFamily.Monospace)
        }
    }
}

/**
 * HOME SKY LAYERS (Beacon Chain reward): every finished chain adds a layer behind the whole app: 1 = a star cluster, 2 = a ringed planet, 3 = a glowing nebula.
 * They drift slowly. Pictures load by name (home_stars, home_planet, home_nebula); nothing is drawn if a picture is missing.
 */
@Composable
fun BeaconSkyLayers(modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    @Suppress("UNUSED_VARIABLE") val v = BeaconChain.version
    val layers = BeaconChain.layers(ctx)
    if (layers <= 0) return
    val stars = rememberArt("home_stars"); val planet = rememberArt("home_planet"); val neb = rememberArt("home_nebula")
    val inf = rememberInfiniteTransition(label = "skyLayers")
    val t by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(40000, easing = LinearEasing), RepeatMode.Restart), label = "t")
    Canvas(modifier.fillMaxSize()) {
        val w = size.width; val h = size.height; val tau = 6.2832f
        fun put(img: androidx.compose.ui.graphics.ImageBitmap?, cx: Float, cy: Float, wFrac: Float, a: Float) {
            if (img == null) return
            val dw = w * wFrac; val dh = dw * img.height / img.width
            drawImage(img, dstOffset = androidx.compose.ui.unit.IntOffset((cx - dw / 2f).toInt(), (cy - dh / 2f).toInt()), dstSize = androidx.compose.ui.unit.IntSize(dw.toInt(), dh.toInt()), alpha = a)
        }
        if (layers >= 1) put(stars, w * (0.5f + 0.05f * sin(t * tau)), h * 0.12f, 0.95f, 0.9f)
        if (layers >= 2) put(planet, w * (0.72f + 0.02f * sin(t * tau + 1f)), h * (0.30f + 0.012f * sin(t * tau * 2f)), 0.42f, 0.85f)
        if (layers >= 3) put(neb, w * (0.5f + 0.04f * sin(t * tau + 2f)), h * 0.86f, 1.15f, 0.55f + 0.15f * sin(t * tau * 2f))
    }
}
