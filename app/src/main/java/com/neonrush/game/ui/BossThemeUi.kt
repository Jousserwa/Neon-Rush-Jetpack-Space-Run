package com.neonrush.game.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.neonrush.game.BossSkins
import kotlin.math.cos
import kotlin.math.sin

private val BT_MONO = FontFamily.Monospace
private val BT_RED = Color(0xFFFF5252)

/**
 * Draws the equipped boss theme around the boss: an aura, a ring and orbiting pieces (different for each theme). Does nothing when no theme is equipped.
 * Called from the App canvas right after the boss itself is drawn. [h] = boss display height, [t] = smooth clock in seconds.
 */
fun DrawScope.drawBossTheme(themeIdx: Int, c: Offset, h: Float, t: Float, alpha: Float = 1f) {
    val th = BossSkins.THEMES.getOrNull(themeIdx) ?: return
    val col = Color(th.accent)
    val a = alpha.coerceIn(0f, 1f)
    val pulse = 0.5f + 0.5f * sin(t * 3f)
    drawCircle(Brush.radialGradient(listOf(col.copy(alpha = (0.20f + 0.12f * pulse) * a), Color.Transparent), c, h * 0.95f), h * 0.95f, c)
    drawCircle(col.copy(alpha = 0.55f * a), h * 0.62f, c, style = Stroke(h * 0.012f))
    val n = 6
    for (i in 0 until n) {
        val ang = t * (if (themeIdx == 1) 0.5f else 0.9f) + i * (6.2832f / n)
        val r = h * (0.62f + (if (themeIdx == 1) 0.08f * sin(t * 2f + i) else 0f))
        val p = Offset(c.x + cos(ang) * r * 0.55f, c.y + sin(ang) * r)
        val s = h * 0.045f
        when (themeIdx) {
            0 -> rotate(ang * 57.3f, p) {                       // armoured plates
                drawRect(Color(0xFF455A64), Offset(p.x - s, p.y - s * 0.6f), Size(s * 2f, s * 1.2f))
                drawRect(col.copy(alpha = a), Offset(p.x - s, p.y - s * 0.6f), Size(s * 2f, s * 1.2f), style = Stroke(2f))
            }
            1 -> {                                              // spores
                drawCircle(col.copy(alpha = 0.35f * a), s * 1.2f, p)
                drawCircle(col.copy(alpha = 0.9f * a), s * 0.55f, p)
                drawCircle(Color.White.copy(alpha = 0.7f * a), s * 0.18f, Offset(p.x - s * 0.15f, p.y - s * 0.15f))
            }
            else -> {                                           // crown shards
                val shard = Path().apply { moveTo(p.x, p.y - s * 1.3f); lineTo(p.x + s * 0.6f, p.y); lineTo(p.x, p.y + s * 1.3f); lineTo(p.x - s * 0.6f, p.y); close() }
                rotate(ang * 57.3f, p) { drawPath(shard, col.copy(alpha = 0.85f * a)); drawPath(shard, Color(0xFFFFD54F).copy(alpha = a), style = Stroke(2f)) }
            }
        }
    }
}

/** A glow behind a boss shot in the equipped theme's colour. No-op when none is equipped. */
fun DrawScope.drawBossThemeShot(themeIdx: Int, x: Float, y: Float, ch: Float, t: Float) {
    val th = BossSkins.THEMES.getOrNull(themeIdx) ?: return
    val col = Color(th.accent)
    val r = ch * (0.045f + 0.008f * sin(t * 12f))
    drawCircle(Brush.radialGradient(listOf(col.copy(alpha = 0.55f), Color.Transparent), Offset(x, y), r * 2f), r * 2f, Offset(x, y))
}

/** Home-screen card for the Boss Themes (challenge skins). */
@Composable
fun BossThemeCard(onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val eq = BossSkins.equipped(ctx)
    val shape = RoundedCornerShape(16.dp)
    Row(modifier.fillMaxWidth().clip(shape).background(Brush.horizontalGradient(listOf(Color(0xFF2A0B0B), Color(0xFF0B1536)))).border(2.dp, BT_RED.copy(alpha = 0.65f), shape).clickable { onOpen() }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("BOSS THEMES", color = BT_RED, fontSize = 13.sp, fontWeight = FontWeight.Black, fontFamily = BT_MONO)
            Text(if (eq >= 0) "${BossSkins.THEMES[eq].name} active · tougher bosses, +40% gems" else "Opt-in challenge: a tougher boss that pays +40% gems", color = Color.White.copy(alpha = 0.85f), fontSize = 10.sp, fontFamily = BT_MONO)
        }
        Text("OPEN ▸", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Black, fontFamily = BT_MONO, modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(BT_RED).padding(horizontal = 12.dp, vertical = 8.dp))
    }
}

/** Boss Themes page: three themes with an animated preview, the buy button and an equip switch. [onBuy] starts the store purchase for theme index and answers true/false. */
@Composable
fun BossThemeDialog(testing: Boolean, onClose: () -> Unit, onBuy: (Int, (Boolean) -> Unit) -> Unit) {
    val ctx = LocalContext.current
    var msg by remember { mutableStateOf("") }; var good by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    @Suppress("UNUSED_VARIABLE") val ver = BossSkins.version
    val eq = BossSkins.equipped(ctx)
    val inf = rememberInfiniteTransition(label = "bossTheme")
    val clock by inf.animateFloat(0f, 600f, infiniteRepeatable(tween(600_000, easing = LinearEasing)), label = "clock")

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)) {
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF260A0A), Color(0xFF0B0820), Color(0xFF06040F))))) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 28.dp, bottom = 40.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("BOSS THEMES", color = BT_RED, fontSize = 22.sp, fontWeight = FontWeight.Black, fontFamily = BT_MONO)
                        Text("A harder boss you choose · pays more", color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp, fontFamily = BT_MONO)
                    }
                    Box(Modifier.size(40.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.1f)).border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape).clickable { onClose() }, contentAlignment = Alignment.Center) { Text("✕", color = Color.White, fontSize = 16.sp) }
                }
                Spacer(Modifier.height(8.dp))
                for (l in listOf("Equip a theme before a run: every boss fight gets the theme's look.", "Bosses and their escorts take 25% less damage (+25% hull).", "Boss kills pay +40% gems.", "Tap the equipped theme again to switch it off: the normal boss is always one tap away.")) {
                    Text("• $l", color = Color.White.copy(alpha = 0.85f), fontSize = 11.sp, fontFamily = BT_MONO, modifier = Modifier.padding(vertical = 2.dp))
                }
                if (testing) Text("TESTING MODE: themes are free", color = Color(0xFFFFD54F), fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = BT_MONO, modifier = Modifier.padding(top = 6.dp))
                if (msg.isNotEmpty()) Text(msg, color = if (good) Color(0xFF69F0AE) else Color(0xFFFF8A80), fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = BT_MONO, modifier = Modifier.padding(top = 8.dp))
                for (i in BossSkins.THEMES.indices) {
                    val th = BossSkins.THEMES[i]; val own = BossSkins.owns(ctx, i); val on = eq == i
                    val col = Color(th.accent); val shape = RoundedCornerShape(14.dp)
                    Column(Modifier.fillMaxWidth().padding(top = 12.dp).clip(shape).background(Color.White.copy(alpha = 0.06f)).border(if (on) 2.dp else 1.dp, col.copy(alpha = if (on) 1f else 0.4f), shape).padding(10.dp)) {
                        Canvas(Modifier.fillMaxWidth().height(120.dp)) {
                            val c = Offset(size.width / 2f, size.height / 2f)
                            // stand-in boss body: a dark hull the theme is drawn around
                            drawCircle(Color(0xFF1B1830), size.height * 0.26f, c)
                            drawCircle(col.copy(alpha = 0.5f), size.height * 0.26f, c, style = Stroke(2f))
                            drawCircle(Color.White.copy(alpha = 0.85f), size.height * 0.05f, Offset(c.x - size.height * 0.08f, c.y))
                            drawBossTheme(i, c, size.height * 0.62f, clock)
                            for (k in 0 until 2) drawBossThemeShot(i, size.width * (0.2f - 0.15f * ((clock * 0.8f + k * 0.5f) % 1f)) + size.width * 0.12f, c.y + (k - 0.5f) * size.height * 0.3f, size.height, clock)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text(th.name, color = col, fontSize = 14.sp, fontWeight = FontWeight.Black, fontFamily = BT_MONO)
                                Text(th.blurb, color = Color.White.copy(alpha = 0.7f), fontSize = 9.sp, fontFamily = BT_MONO)
                            }
                            Text(if (on) "EQUIPPED ✓" else if (own) "EQUIP" else th.price, color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Black, fontFamily = BT_MONO,
                                modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(if (on) Color(0xFF69F0AE) else col).clickable(enabled = !busy) {
                                    if (own) { BossSkins.toggle(ctx, i); good = true; msg = if (BossSkins.equipped(ctx) == i) "${th.name} equipped" else "Boss theme off: normal bosses" }
                                    else { busy = true; onBuy(i) { ok -> busy = false; good = ok; msg = if (ok) "${th.name} unlocked and equipped" else "Purchase cancelled" } }
                                }.padding(horizontal = 12.dp, vertical = 8.dp))
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("Free players can still win everything. Boss themes are an optional challenge for more rewards.", color = Color.White.copy(alpha = 0.5f), fontSize = 9.sp, fontFamily = BT_MONO)
            }
        }
    }
}
