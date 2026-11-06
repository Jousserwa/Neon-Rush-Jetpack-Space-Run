package com.neonrush.game.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.neonrush.game.Showcase

private val SHW_BG = Color(0xFF07041A)
private val SHW_CYAN = Color(0xFF00E5FF)
private val SHW_PINK = Color(0xFFFF4DD2)
private val SHW_GOLD = Color(0xFFFFD23F)

/** One door: a title, a line that says what is behind it, and a ticker that cycles through EVERY item so nothing stays hidden. */
@Composable
private fun DoorCard(door: Int, title: String, sub: String, accent: Color, newCount: Int, offsetMs: Int, onClick: () -> Unit, modifier: Modifier) {
    val items = remember(door) { Showcase.entries(door) }
    var idx by remember { mutableStateOf(0) }
    LaunchedEffect(door) {
        kotlinx.coroutines.delay(offsetMs.toLong())
        while (true) { kotlinx.coroutines.delay(2600); idx = (idx + 1) % items.size }
    }
    val pulse by rememberInfiniteTransition(label = "door").animateFloat(0.35f, 1f, infiniteRepeatable(tween(1500, easing = LinearEasing), RepeatMode.Reverse), label = "p")
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Brush.verticalGradient(listOf(accent.copy(alpha = 0.22f), Color(0xFF0B0820))))
            .border(1.5.dp, accent.copy(alpha = 0.45f + 0.4f * pulse), RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = accent, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black, fontSize = 17.sp, maxLines = 1)
            Spacer(Modifier.weight(1f))
            if (newCount > 0) {
                Text("$newCount NEW", color = Color.Black, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 9.sp,
                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(SHW_GOLD).padding(horizontal = 5.dp, vertical = 2.dp))
            }
        }
        Text(sub, color = Color.White.copy(alpha = 0.6f), fontFamily = FontFamily.Monospace, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(8.dp))
        AnimatedContent(
            targetState = idx,
            transitionSpec = { (slideInVertically { it / 2 } + fadeIn(tween(350))) togetherWith (slideOutVertically { -it / 2 } + fadeOut(tween(250))) },
            label = "ticker"
        ) { i ->
            val e = items[i.coerceIn(0, items.size - 1)]
            Column(modifier = Modifier.fillMaxWidth().height(58.dp)) {
                Text(e.icon + " " + e.title, color = Color(e.color), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(e.line, color = Color.White.copy(alpha = 0.85f), fontFamily = FontFamily.Monospace, fontSize = 10.sp, lineHeight = 13.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }
        Text("TAP TO ENTER ›", color = accent.copy(alpha = 0.6f + 0.4f * pulse), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 10.sp)
    }
}

/** The two doors that sit under FLASH on the home screen. */
@Composable
fun DoorsRow(hangarNew: Int, armoryNew: Int, onHangar: () -> Unit, onArmory: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DoorCard(Showcase.HANGAR, "HANGAR", "HOW YOU LOOK & FLY", SHW_CYAN, hangarNew, 0, onHangar, Modifier.weight(1f))
        DoorCard(Showcase.ARMORY, "ARMORY", "WHAT SHOOTS & DEFENDS", SHW_PINK, armoryNew, 1300, onArmory, Modifier.weight(1f))
    }
}

/** Daily spotlight: one item per door per day, picked from whatever has been seen least. */
@Composable
fun SpotlightCard(door: Int, owned: Int, total: Int, onGo: (Showcase.Entry) -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val e = remember(door, Showcase.version) { Showcase.pick(ctx, door) }
    val accent = Color(e.color)
    val head = if (door == Showcase.HANGAR) "TODAY IN THE HANGAR" else "TODAY IN THE ARMORY"
    val pulse by rememberInfiniteTransition(label = "spot").animateFloat(0.4f, 1f, infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Reverse), label = "sp")
    Column(
        modifier = modifier.fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Brush.horizontalGradient(listOf(accent.copy(alpha = 0.20f), Color(0xFF0B0820))))
            .border(1.dp, accent.copy(alpha = 0.35f + 0.35f * pulse), RoundedCornerShape(14.dp))
            .clickable { Showcase.markOpened(ctx, e.id); onGo(e) }
            .padding(12.dp)
    ) {
        Text(head, color = SHW_GOLD, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 10.sp)
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(e.icon, fontSize = 28.sp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(e.title, color = accent, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black, fontSize = 14.sp, maxLines = 1)
                Text(e.line, color = Color.White.copy(alpha = 0.85f), fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 14.sp)
            }
        }
        if (total > 0) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (door == Showcase.HANGAR) "SUITS $owned/$total" else "ARMORY $owned/$total", color = Color.White.copy(alpha = 0.7f), fontFamily = FontFamily.Monospace, fontSize = 9.sp)
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f).height(5.dp).clip(RoundedCornerShape(3.dp)).background(Color.White.copy(alpha = 0.12f))) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth((owned.toFloat() / total).coerceIn(0f, 1f)).background(accent))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("SEE IT ›", color = Color.Black, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 11.sp,
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(accent).padding(horizontal = 14.dp, vertical = 6.dp))
    }
}

/** Full screen Armory: four tabs. The caller passes the cards of each tab, so every dialog and purchase keeps working exactly as before. */
@Composable
fun ArmoryDialog(startTab: Int, onClose: () -> Unit, onSpotlight: (Showcase.Entry) -> Unit, weapons: @Composable () -> Unit, defense: @Composable () -> Unit, allies: @Composable () -> Unit, depots: @Composable () -> Unit) {
    val ctx = LocalContext.current
    var tab by remember { mutableStateOf(startTab.coerceIn(0, 3)) }
    LaunchedEffect(tab) { Showcase.markTarget(ctx, "a:$tab") }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)) {
        Column(Modifier.fillMaxSize().background(SHW_BG).statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("ARMORY", color = SHW_PINK, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black, fontSize = 20.sp)
                Spacer(Modifier.weight(1f))
                Text("CLOSE ✕", color = Color.White, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 12.sp,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).border(1.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(8.dp)).clickable { onClose() }.padding(horizontal = 12.dp, vertical = 6.dp))
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF14102E))) {
                Showcase.ARMORY_TABS.forEachIndexed { i, name ->
                    val n = Showcase.unopenedTab(ctx, i)
                    Box(Modifier.weight(1f).background(if (tab == i) SHW_PINK else Color.Transparent).clickable { tab = i }.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                        Text(name, color = if (tab == i) Color.Black else SHW_PINK, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 10.sp, maxLines = 1, softWrap = false, textAlign = TextAlign.Center)
                        if (n > 0 && tab != i) Box(Modifier.align(Alignment.TopEnd).padding(top = 3.dp, end = 4.dp).size(7.dp).clip(RoundedCornerShape(4.dp)).background(SHW_GOLD))
                    }
                }
            }
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp)) {
                SpotlightCard(Showcase.ARMORY, 0, 0, onGo = onSpotlight)
                Spacer(Modifier.height(6.dp))
                when (tab) { 0 -> weapons(); 1 -> defense(); 2 -> allies(); else -> depots() }
                Spacer(Modifier.height(40.dp))
            }
        }
    }
}
