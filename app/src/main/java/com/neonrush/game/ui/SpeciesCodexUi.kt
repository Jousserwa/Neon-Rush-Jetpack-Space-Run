package com.neonrush.game.ui

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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.neonrush.game.Roster

private val SC_MONO = FontFamily.Monospace
private val SC_GOLD = Color(0xFFFFD54F)

/** First zone where the species can appear: 4 are open from the start, then +1 per 15-zone tier (see Roster.unlocked). */
private fun scUnlockZone(id: Int): Int = 15 * maxOf(0, id - 3)

/** Home-screen card for the Species Codex (how many of the ten escort species you have met). */
@Composable
fun SpeciesCodexCard(onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val seen = Roster.seenCount(ctx)
    val shape = RoundedCornerShape(16.dp)
    Row(modifier.fillMaxWidth().clip(shape).background(Brush.horizontalGradient(listOf(Color(0xFF241B05), Color(0xFF0B1536)))).border(2.dp, SC_GOLD.copy(alpha = 0.6f), shape).clickable { onOpen() }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("SPECIES CODEX", color = SC_GOLD, fontSize = 13.sp, fontWeight = FontWeight.Black, fontFamily = SC_MONO)
            Text("Escort ships you have met in boss fights  ($seen / ${Roster.ALL.size} logged)", color = Color.White.copy(alpha = 0.85f), fontSize = 10.sp, fontFamily = SC_MONO)
        }
        Text("OPEN ▸", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Black, fontFamily = SC_MONO, modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(SC_GOLD).padding(horizontal = 12.dp, vertical = 8.dp))
    }
}

/** The Species Codex page: every escort species with its silhouette, quirk and stats once met; the others stay dark with a hint of where they appear. */
@Composable
fun SpeciesCodexDialog(onClose: () -> Unit) {
    val ctx = LocalContext.current
    val seen = Roster.seenCount(ctx)
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)) {
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF1B1507), Color(0xFF0B0820), Color(0xFF06040F))))) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 28.dp, bottom = 40.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("SPECIES CODEX", color = SC_GOLD, fontSize = 22.sp, fontWeight = FontWeight.Black, fontFamily = SC_MONO)
                        Text("$seen / ${Roster.ALL.size} logged · meet them in boss fights", color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp, fontFamily = SC_MONO)
                    }
                    Box(Modifier.size(40.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.1f)).border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape).clickable { onClose() }, contentAlignment = Alignment.Center) { Text("✕", color = Color.White, fontSize = 16.sp) }
                }
                Spacer(Modifier.height(10.dp))
                Text("Escorts guard the boss. Each species fights differently: learn its quirk and you know when to shoot.", color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp, fontFamily = SC_MONO)
                Spacer(Modifier.height(8.dp))
                for (sp in Roster.ALL) {
                    val met = Roster.seen(ctx, sp.id)
                    val col = Color(sp.col)
                    val shape = RoundedCornerShape(14.dp)
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(shape).background(Color.White.copy(alpha = 0.06f)).border(1.dp, if (met) col.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.15f), shape).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Canvas(Modifier.size(width = 84.dp, height = 56.dp)) {
                            val c = Offset(size.width * 0.5f, size.height * 0.5f)
                            val s = size.height * 0.3f
                            val hull = hullPath(sp.hull, c, s)
                            if (met) {
                                drawPath(hull, Brush.verticalGradient(listOf(col.copy(alpha = 0.85f), Color(0xFF14102E), col.copy(alpha = 0.7f)), c.y - s, c.y + s))
                                drawPath(hull, col, style = Stroke(2.dp.toPx()))
                                drawCircle(Color.White.copy(alpha = 0.9f), s * 0.22f, Offset(c.x - s * 0.35f, c.y))
                            } else {
                                drawPath(hull, Color.White.copy(alpha = 0.08f))
                                drawPath(hull, Color.White.copy(alpha = 0.25f), style = Stroke(1.5.dp.toPx()))
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            if (met) {
                                Text(sp.name, color = col, fontSize = 13.sp, fontWeight = FontWeight.Black, fontFamily = SC_MONO)
                                Text(sp.quirk, color = Color.White.copy(alpha = 0.85f), fontSize = 10.sp, fontFamily = SC_MONO)
                                Text("HULL ${scLabel(sp.tough, 0.9f, 1.3f)} · SPEED ${scLabel(sp.speed, 0.9f, 1.25f)} · FIRE ${scLabel(1f / sp.fire, 0.9f, 1.3f)}", color = Color.White.copy(alpha = 0.6f), fontSize = 9.sp, fontFamily = SC_MONO, modifier = Modifier.padding(top = 2.dp))
                            } else {
                                Text("? ? ?", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp, fontWeight = FontWeight.Black, fontFamily = SC_MONO)
                                val z = scUnlockZone(sp.id)
                                Text(if (z == 0) "Not met yet · can appear in any boss fight" else "Not met yet · can appear from zone $z", color = Color.White.copy(alpha = 0.55f), fontSize = 10.sp, fontFamily = SC_MONO)
                            }
                        }
                        Text(if (met) "LOGGED ✓" else "—", color = if (met) Color(0xFF69F0AE) else Color.White.copy(alpha = 0.3f), fontSize = 10.sp, fontWeight = FontWeight.Black, fontFamily = SC_MONO)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text("A new species is logged the first time it appears in a boss fight.", color = Color.White.copy(alpha = 0.5f), fontSize = 9.sp, fontFamily = SC_MONO)
            }
        }
    }
}

private fun scLabel(v: Float, low: Float, high: Float): String = if (v < low) "LOW" else if (v > high) "HIGH" else "MID"
