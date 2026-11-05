package com.neonrush.game.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.neonrush.game.Roster
import kotlin.math.roundToInt
import kotlin.math.sin

private val SC_MONO = FontFamily.Monospace
private val SC_GOLD = Color(0xFFFFD54F)
private val SC_GREEN = Color(0xFF69F0AE)

/** First zone where the species can appear on EASY / MEDIUM: 2 are open from the start, then +1 per 15-zone tier (see Roster.unlocked). */
private fun scUnlockZone(id: Int): Int = 15 * maxOf(0, id - 1)

/** Home-screen card for the Species Codex (how many of the ten escort species you have met, and your hunter title). */
@Composable
fun SpeciesCodexCard(onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val seen = Roster.seenCount(ctx)
    val shape = RoundedCornerShape(16.dp)
    Row(modifier.fillMaxWidth().clip(shape).background(Brush.horizontalGradient(listOf(Color(0xFF241B05), Color(0xFF0B1536)))).border(2.dp, SC_GOLD.copy(alpha = 0.6f), shape).barFx(SC_GOLD, phase = 2).clickable { onOpen() }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        BarGlyph("🛰", SC_GOLD)
        Column(Modifier.weight(1f)) {
            Text("SPECIES CODEX", color = SC_GOLD, fontSize = 13.sp, fontWeight = FontWeight.Black, fontFamily = SC_MONO)
            Text("$seen / ${Roster.ALL.size} logged · ${Roster.title(ctx)} · earn ranks and gems", color = Color.White.copy(alpha = 0.85f), fontSize = 10.sp, fontFamily = SC_MONO)
        }
        Text("OPEN ▸", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Black, fontFamily = SC_MONO, modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(SC_GOLD).padding(horizontal = 12.dp, vertical = 8.dp))
    }
}

/**
 * The Species Codex page (v2): a banner, your hunter title, a Daily Hunt, three milestones, and a card for every escort species (portrait, quirk, stat bars, kill count and rank;
 * the frame turns bronze -> silver -> gold as the rank rises). Rewards are CLAIMED by tapping: 1 / 2 / 3 gems per rank, 1 gem a day, milestones 2 / 3 / 3. [onClaim] credits the gems.
 */
@Composable
fun SpeciesCodexDialog(onClose: () -> Unit, onClaim: (Int) -> Unit = {}) {
    val ctx = LocalContext.current
    var rev by remember { mutableStateOf(0) }
    @Suppress("UNUSED_VARIABLE") val r0 = rev
    val seen = Roster.seenCount(ctx)
    val aces = Roster.ALL.count { Roster.rank(ctx, it.id) >= 2 }
    val inf = rememberInfiniteTransition(label = "codex")
    val clock by inf.animateFloat(0f, 6.2832f, infiniteRepeatable(tween(3200, easing = LinearEasing)), label = "cc")
    val banner = rememberArt("cdx_banner")
    fun claimed(g: Int) { if (g > 0) { onClaim(g); rev += 1 } }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)) {
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF1B1507), Color(0xFF0B0820), Color(0xFF06040F))))) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 40.dp)) {
                // ---- banner ----
                Box(Modifier.fillMaxWidth().aspectRatio(if (banner != null) banner.width.toFloat() / banner.height else 2.4f)) {
                    if (banner != null) Image(banner, null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
                    else Box(Modifier.matchParentSize().background(Brush.horizontalGradient(listOf(Color(0xFF2A1B4A), Color(0xFF0B0820)))))
                    Box(Modifier.matchParentSize().background(Brush.horizontalGradient(listOf(Color(0xE606040F), Color.Transparent))))
                    Column(Modifier.align(Alignment.CenterStart).padding(start = 16.dp, top = 20.dp)) {
                        Text("SPECIES CODEX", color = SC_GOLD, fontSize = 22.sp, fontWeight = FontWeight.Black, fontFamily = SC_MONO)
                        Text("$seen / ${Roster.ALL.size} LOGGED · $aces ACE", color = Color.White.copy(alpha = 0.85f), fontSize = 10.sp, fontFamily = SC_MONO)
                        Text(Roster.title(ctx), color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Black, fontFamily = SC_MONO, modifier = Modifier.padding(top = 6.dp).clip(RoundedCornerShape(8.dp)).background(SC_GOLD).padding(horizontal = 8.dp, vertical = 3.dp))
                    }
                    Box(Modifier.align(Alignment.TopEnd).padding(top = 28.dp, end = 16.dp).size(40.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f)).border(1.dp, Color.White.copy(alpha = 0.4f), CircleShape).clickable { onClose() }, contentAlignment = Alignment.Center) { Text("✕", color = Color.White, fontSize = 16.sp) }
                }
                Column(Modifier.padding(horizontal = 12.dp)) {
                    Spacer(Modifier.height(10.dp))
                    Text("Escorts guard the boss. Destroy them to rank up each species: SCOUT, then VETERAN (${Roster.RANK_KILLS[1]} kills), then ACE (${Roster.RANK_KILLS[2]} kills). Every new rank pays a few gems that you claim here.", color = Color.White.copy(alpha = 0.65f), fontSize = 10.sp, fontFamily = SC_MONO)
                    Spacer(Modifier.height(10.dp))

                    // ---- daily hunt ----
                    val tgt = Roster.dailyTarget(ctx)
                    if (tgt >= 0) {
                        val sp = Roster.of(tgt); val goal = Roster.DAILY_GOAL
                        val k = Roster.dailyKills(ctx).coerceAtMost(goal); val done = k >= goal; val got = Roster.dailyClaimed(ctx)
                        val shape = RoundedCornerShape(14.dp); val col = Color(sp.col)
                        val pic = rememberArt(Roster.artName(sp.id))
                        Row(Modifier.fillMaxWidth().clip(shape).background(Brush.horizontalGradient(listOf(col.copy(alpha = 0.22f), Color(0xFF0B0820)))).border(1.5.dp, col.copy(alpha = 0.7f), shape).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(width = 78.dp, height = 46.dp), contentAlignment = Alignment.Center) {
                                if (pic != null) Image(pic, null, Modifier.fillMaxSize().graphicsLayer { translationY = sin(clock) * 3f }, contentScale = ContentScale.Fit)
                                else Text("🎯", fontSize = 24.sp)
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text("DAILY HUNT", color = SC_GOLD, fontSize = 11.sp, fontWeight = FontWeight.Black, fontFamily = SC_MONO)
                                Text("Destroy $goal ${sp.name} today  ($k/$goal)", color = Color.White.copy(alpha = 0.9f), fontSize = 10.sp, fontFamily = SC_MONO)
                                Box(Modifier.padding(top = 4.dp).fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)).background(Color.White.copy(alpha = 0.12f))) { Box(Modifier.fillMaxWidth(k / goal.toFloat()).fillMaxHeight().background(col)) }
                            }
                            Spacer(Modifier.width(8.dp))
                            ClaimPill(if (got) "DONE ✓" else if (done) "CLAIM +${Roster.DAILY_GEMS} 💎" else "+${Roster.DAILY_GEMS} 💎", enabled = done && !got, good = got) { claimed(Roster.claimDaily(ctx)) }
                        }
                        Spacer(Modifier.height(8.dp))
                    }

                    // ---- milestones ----
                    for (i in Roster.MILESTONES.indices) {
                        val done = Roster.milestoneDone(ctx, i); val got = Roster.milestoneClaimed(ctx, i); val g = Roster.MILESTONE_GEMS[i]
                        val prog = when (i) { 0 -> "$seen/5"; 1 -> "$seen/${Roster.ALL.size}"; else -> "$aces/${Roster.ALL.size} ACE" }
                        val shape = RoundedCornerShape(12.dp)
                        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(shape).background(Color.White.copy(alpha = 0.05f)).border(1.dp, if (done && !got) SC_GOLD else Color.White.copy(alpha = 0.15f), shape).padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(Roster.MILESTONE_NAMES[i], color = if (done) SC_GOLD else Color.White.copy(alpha = 0.8f), fontSize = 11.sp, fontWeight = FontWeight.Black, fontFamily = SC_MONO)
                                Text(if (done) "Complete" else "Progress $prog", color = Color.White.copy(alpha = 0.55f), fontSize = 9.sp, fontFamily = SC_MONO)
                            }
                            ClaimPill(if (got) "CLAIMED ✓" else if (done) "CLAIM +$g 💎" else "+$g 💎", enabled = done && !got, good = got) { claimed(Roster.claimMilestone(ctx, i)) }
                        }
                    }
                    Spacer(Modifier.height(10.dp))

                    // ---- species cards, two to a row ----
                    for (row in Roster.ALL.chunked(2)) {
                        Row(Modifier.fillMaxWidth()) {
                            for (sp in row) Box(Modifier.weight(1f).padding(4.dp)) { SpeciesCard(sp, clock, rev) { g -> claimed(g) } }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text("A new species is logged the first time it appears in a boss fight. One new species joins the fights with every boss tier.", color = Color.White.copy(alpha = 0.5f), fontSize = 9.sp, fontFamily = SC_MONO)
                }
            }
        }
    }
}

@Composable
private fun ClaimPill(text: String, enabled: Boolean, good: Boolean, onClick: () -> Unit) {
    Text(text, color = if (enabled) Color.Black else if (good) SC_GREEN else Color.White.copy(alpha = 0.45f), fontSize = 10.sp, fontWeight = FontWeight.Black, fontFamily = SC_MONO, maxLines = 1,
        modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(if (enabled) SC_GOLD else Color.White.copy(alpha = 0.08f)).then(if (enabled) Modifier.clickable { onClick() } else Modifier).padding(horizontal = 10.dp, vertical = 7.dp))
}

/** One species card. The picture frame is drawn on top of the content: bronze = SCOUT, silver = VETERAN, gold = ACE (dim bronze while the species is still unknown). */
@Composable
private fun SpeciesCard(sp: Roster.Species, clock: Float, rev: Int, onClaim: (Int) -> Unit) {
    @Suppress("UNUSED_VARIABLE") val r0 = rev
    val ctx = LocalContext.current
    val met = Roster.seen(ctx, sp.id)
    val kills = Roster.kills(ctx, sp.id); val rank = Roster.rankOf(kills)
    val col = Color(sp.col)
    val portrait = rememberArt(Roster.artName(sp.id))
    val frame = rememberArt(if (!met) "frame_bronze" else when (rank) { 2 -> "frame_gold"; 1 -> "frame_silver"; else -> "frame_bronze" })
    val pending = if (met) Roster.pendingRank(ctx, sp.id) else -1
    // The card is laid out in fractions of its own size, so its text must not grow with the phone's font-size setting (that pushed the text out of its slots).
    val dens0 = androidx.compose.ui.platform.LocalDensity.current
    androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(dens0.density, 1f)) {
    BoxWithConstraints(Modifier.fillMaxWidth().aspectRatio(0.655f).clip(RoundedCornerShape(10.dp)).background(Brush.verticalGradient(listOf(col.copy(alpha = if (met) 0.25f else 0.08f), Color(0xFF0B0820), Color(0xFF06040F))))) {
        val w = maxWidth; val h = maxHeight
        Box(Modifier.offset(w * 0.10f, h * 0.085f).size(w * 0.80f, h * 0.065f), contentAlignment = Alignment.Center) {
            Text(if (met) sp.name else "? ? ?", color = if (met) col else Color.White.copy(alpha = 0.55f), fontSize = 11.sp, fontWeight = FontWeight.Black, fontFamily = SC_MONO, maxLines = 1)
        }
        Box(Modifier.offset(w * 0.10f, h * 0.165f).size(w * 0.80f, h * 0.385f), contentAlignment = Alignment.Center) {
            if (portrait != null) {
                Image(portrait, null, Modifier.fillMaxSize().padding(4.dp).graphicsLayer { translationY = if (met) sin(clock + sp.id) * 4f else 0f }, contentScale = ContentScale.Fit,
                    colorFilter = if (met) null else ColorFilter.tint(Color.White.copy(alpha = 0.16f), BlendMode.SrcIn))
            } else {
                Canvas(Modifier.fillMaxSize()) {
                    val c = Offset(size.width * 0.5f, size.height * 0.5f); val s = size.height * 0.22f
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
            }
            if (pending >= 0) {
                Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 0.dp).graphicsLayer { scaleX = 0.72f; scaleY = 0.72f }) {
                    ClaimPill("${Roster.RANK_NAMES[pending]} +${Roster.rankReward(pending)} 💎", enabled = true, good = false) { onClaim(Roster.claimRank(ctx, sp.id, pending)) }
                }
            }
        }
        Box(Modifier.offset(w * 0.10f, h * 0.585f).size(w * 0.80f, h * 0.055f), contentAlignment = Alignment.Center) {
            val line = if (!met) "NOT MET" else if (rank >= 2) "ACE · $kills KILLS" else "${Roster.RANK_NAMES[rank]} · $kills/${Roster.RANK_KILLS[rank + 1]}"
            Text(line, color = if (met) Color.White.copy(alpha = 0.9f) else Color.White.copy(alpha = 0.45f), fontSize = 8.sp, fontWeight = FontWeight.Bold, fontFamily = SC_MONO, maxLines = 1)
        }
        Column(Modifier.offset(w * 0.10f, h * 0.655f).size(w * 0.80f, h * 0.26f), verticalArrangement = Arrangement.Top) {
            if (met) {
                Text(sp.quirk, color = Color.White.copy(alpha = 0.85f), fontSize = 8.sp, fontFamily = SC_MONO, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(3.dp))
                StatBar("HULL", scLevel(sp.tough, 0.8f, 2.2f), col)
                StatBar("SPEED", scLevel(sp.speed, 0.45f, 1.8f), col)
                StatBar("FIRE", scLevel(1f / sp.fire, 0.7f, 1.82f), col)
            } else {
                val z = scUnlockZone(sp.id)
                Text(if (z == 0) "Can appear in any boss fight" else "Appears from zone $z (earlier on HARD and LEGENDARY)", color = Color.White.copy(alpha = 0.55f), fontSize = 8.sp, fontFamily = SC_MONO, maxLines = 3, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
        }
        if (frame != null) Image(frame, null, Modifier.matchParentSize(), contentScale = ContentScale.FillBounds, alpha = if (met) 1f else 0.4f)
    }
    }
}

@Composable
private fun StatBar(label: String, level: Int, col: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
        Text(label, color = Color.White.copy(alpha = 0.6f), fontSize = 7.sp, fontFamily = SC_MONO, modifier = Modifier.width(30.dp), maxLines = 1)
        for (i in 1..5) Box(Modifier.weight(1f).padding(horizontal = 1.dp).height(4.dp).background(if (i <= level) col else Color.White.copy(alpha = 0.12f)))
    }
}

private fun scLevel(v: Float, low: Float, high: Float): Int = (1 + ((v - low) / (high - low) * 4f).roundToInt()).coerceIn(1, 5)
