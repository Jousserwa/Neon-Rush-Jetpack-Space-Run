package com.neonrush.game.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neonrush.game.LegendMoves
import com.neonrush.game.NeonSoundEngine
import com.neonrush.game.PilotDrift
import com.neonrush.game.Shooting
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

private class Mag(var x: Float, var y: Float, var age: Float = 0f, var pulled: Boolean = false, val drop: Boolean = false)
private class Grab(var age: Float, val rounds: Int, val legend: Boolean)

/**
 * ARMOR MAGAZINES. They glide in from the right every so often (more often on Legendary, and a boss always leaves one). Fly through one to load
 * armor rounds into the gun: Medium/Hard 3 (Pro 5), Legendary 70. Rounds stay in the gun until fired, even between runs.
 * Pick-up animation: the magazine is pulled in, clacks into the gun, bullets orbit and dock, a ring of armor plates assembles around the pilot
 * (gold and longer on Legendary) and "+N ARMOR ROUNDS" rises. While rounds are loaded the pilot keeps a ring of little bullets orbiting.
 */
@Composable
fun ArmorOverlay(
    active: Boolean, tierName: String, isPro: Boolean, bossActive: Boolean, bossHealth: Float, pilotY: Int,
    onScore: (Int) -> Unit, modifier: Modifier = Modifier
) {
    val mode = Shooting.mode(tierName, isPro)
    if (mode == 0) return
    val ctx = LocalContext.current
    val dens = LocalDensity.current.density
    val sfx = remember { NeonSoundEngine() }
    val legend = mode == 2
    val yRef = rememberUpdatedState(pilotY)
    val bossRef = rememberUpdatedState(bossActive)
    val bossHpRef = rememberUpdatedState(bossHealth)
    val scoreRef = rememberUpdatedState(onScore)
    val mags = remember { ArrayList<Mag>() }
    val grabs = remember { ArrayList<Grab>() }
    var sz by remember { mutableStateOf(IntSize.Zero) }
    var frame by remember { mutableStateOf(0) }
    var armed by remember { mutableStateOf(0) }
    var popup by remember { mutableStateOf("") }
    var popupAge by remember { mutableStateOf(-1f) }
    var labelPos by remember { mutableStateOf(IntOffset(-999, -999)) }
    val art = rememberArt("ammo_crate")
    val gold = Color(0xFFFFC21A); val hot = Color(0xFFFFF3B0); val cyan = Color(0xFF3DE8FF)

    LaunchedEffect(active, legend) {
        mags.clear(); grabs.clear(); popup = ""; popupAge = -1f
        if (!active) return@LaunchedEffect
        fun rnd(a: Float, b: Float) = a + Random.nextFloat() * (b - a)
        var next = rnd(9f, 12f); var runT = 0f; var last = 0L; var wasBoss = false; var lastHp = 1f; var armT = 0f
        while (true) {
            val now = withFrameNanos { it }
            val dt = if (last == 0L) 0.016f else ((now - last) / 1_000_000_000f).coerceAtMost(0.05f)
            last = now; runT += dt; armT += dt
            val w = sz.width.toFloat(); val h = sz.height.toFloat()
            if (w <= 0f || h <= 0f) { frame++; continue }
            if (armT > 0.25f) { armT = 0f; armed = Shooting.ammo(ctx) }
            val boss = bossRef.value
            if (boss) lastHp = bossHpRef.value
            if (wasBoss && !boss && lastHp < 0.1f) mags.add(Mag(1.1f, rnd(25f, 75f), drop = true))   // a defeated boss leaves one behind
            wasBoss = boss
            if (!boss && mags.none { !it.drop }) { next -= dt; if (next <= 0f) { next = if (legend) rnd(15f, 21f) else rnd(26f, 36f); mags.add(Mag(1.1f, rnd(18f, 82f))) } }
            val pxPx = PilotDrift.x * w; val pyPx = yRef.value / 100f * h
            val it2 = mags.iterator()
            while (it2.hasNext()) {
                val m = it2.next(); m.age += dt
                val dx = pxPx - m.x * w; val dy = pyPx - m.y / 100f * h
                val d = hypot(dx, dy)
                if (d < 110f * dens) m.pulled = true
                if (m.pulled && d > 1f) {
                    // pulled toward the pilot, faster the closer it gets
                    val k = 2.5f + 4f * (1f - (d / (110f * dens)).coerceIn(0f, 1f))
                    m.x += (dx / w) * k * dt; m.y += (dy / h) * 100f * k * dt
                } else m.x -= 0.27f * dt
                if (d < 30f * dens) {
                    it2.remove()
                    val n = Shooting.armorFor(tierName, isPro)
                    val added = Shooting.addArmor(ctx, n)
                    armed = Shooting.ammo(ctx)
                    grabs.add(Grab(0f, n.coerceAtMost(if (legend) 9 else 5), legend))
                    sfx.playArmorPickup(); scoreRef.value(if (legend) 60 else 40)
                    popup = if (added > 0) "+$added ARMOR ROUNDS" + (if (legend) "\n+ OVERDRIVE CHARGE" else "") else "GUN FULL"; popupAge = 0f
                    if (legend) LegendMoves.charge(0.3f)
                } else if (m.x < -0.2f) it2.remove()
            }
            val gi = grabs.iterator()
            while (gi.hasNext()) { val g = gi.next(); g.age += dt; if (g.age > (if (g.legend) 3.2f else 2.0f)) gi.remove() }
            if (popupAge >= 0f) { popupAge += dt; if (popupAge > 2.2f) { popupAge = -1f; popup = "" } }
            if (popupAge >= 0f) labelPos = IntOffset((pxPx - 90f * dens).toInt(), (pyPx - 96f * dens - popupAge * 26f * dens).toInt())
            frame++
        }
    }

    fun DrawScope.hex(c: Offset, r: Float, rot: Float, col: Color, sw: Float) {
        val p = Path()
        for (i in 0..5) { val a = rot + i * (PI.toFloat() / 3f); val pt = Offset(c.x + cos(a) * r, c.y + sin(a) * r); if (i == 0) p.moveTo(pt.x, pt.y) else p.lineTo(pt.x, pt.y) }
        p.close(); drawPath(p, col, style = Stroke(sw))
    }

    Box(modifier.fillMaxSize().onSizeChanged { sz = it }) {
        Canvas(Modifier.fillMaxSize()) {
            @Suppress("UNUSED_VARIABLE") val f = frame
            val w = size.width; val h = size.height; val t = frame * 0.016f
            val pc = Offset(PilotDrift.x * w, yRef.value / 100f * h)
            // loaded gun: a ring of little bullets orbits the pilot while rounds are in the magazine
            if (armed > 0) {
                val n = if (armed <= 8) armed else 8
                for (i in 0 until n) {
                    val a = t * 1.6f + i * (2f * PI.toFloat() / n)
                    val c = Offset(pc.x + cos(a) * 34.dp.toPx(), pc.y + sin(a) * 34.dp.toPx())
                    rotate(a * 57.3f + 90f, c) { drawRoundRect(gold.copy(alpha = 0.85f), Offset(c.x - 1.5f.dp.toPx(), c.y - 4.dp.toPx()), Size(3.dp.toPx(), 8.dp.toPx()), CornerRadius(1.5f.dp.toPx())) }
                }
                if (legend) drawCircle(gold.copy(alpha = 0.10f + 0.05f * sin(t * 4f)), 42.dp.toPx(), pc)
            }
            // magazines on the track
            for (m in mags) {
                val c = Offset(m.x * w, m.y / 100f * h + sin(t * 3f + m.y) * 4.dp.toPx())
                val tint = if (legend) gold else cyan
                val pulse = 0.5f + 0.5f * sin(t * 5f)
                drawCircle(Brush.radialGradient(listOf(tint.copy(alpha = 0.45f + 0.2f * pulse), Color.Transparent), c, 46.dp.toPx()), 46.dp.toPx(), c)
                drawCircle(tint.copy(alpha = 0.5f * (1f - pulse)), (22f + 22f * pulse).dp.toPx(), c, style = Stroke(2.dp.toPx()))
                rotate(sin(t * 2f + m.y) * 12f, c) {
                    val bw = (if (legend) 30f else 24f).dp.toPx(); val bh = (if (legend) 44f else 36f).dp.toPx()
                    if (art != null) {
                        drawImage(art, dstOffset = IntOffset((c.x - bw * 0.9f).toInt(), (c.y - bh * 0.9f).toInt()), dstSize = IntSize((bw * 1.8f).toInt(), (bh * 1.8f).toInt()))
                    } else {
                        drawRoundRect(Color(0xFF1B1B2E), Offset(c.x - bw / 2, c.y - bh / 2), Size(bw, bh), CornerRadius(5.dp.toPx()))
                        drawRoundRect(tint, Offset(c.x - bw / 2, c.y - bh / 2), Size(bw, bh), CornerRadius(5.dp.toPx()), style = Stroke(2.dp.toPx()))
                        drawRect(tint.copy(alpha = 0.85f), Offset(c.x - bw / 2 + 3.dp.toPx(), c.y + bh * 0.1f), Size(bw - 6.dp.toPx(), 5.dp.toPx()))
                        val rounds = if (legend) 4 else 3
                        for (i in 0 until rounds) { val rx = c.x - bw / 2 + bw * (i + 1) / (rounds + 1); drawRoundRect(hot, Offset(rx - 2.5f.dp.toPx(), c.y - bh / 2 - 8.dp.toPx()), Size(5.dp.toPx(), 12.dp.toPx()), CornerRadius(2.5f.dp.toPx())) }
                    }
                    // light sweep across the magazine
                    val sweep = ((t * 0.8f + m.y * 0.01f) % 1f)
                    drawLine(Color.White.copy(alpha = 0.55f * (1f - kotlin.math.abs(sweep - 0.5f) * 2f)), Offset(c.x - bw / 2 + bw * sweep, c.y - bh / 2), Offset(c.x - bw / 2 + bw * sweep - 8.dp.toPx(), c.y + bh / 2), 2.dp.toPx(), StrokeCap.Round)
                }
                for (i in 0..2) { val a = t * 2.4f + i * 2.09f; drawCircle(hot.copy(alpha = 0.85f), 2.dp.toPx(), Offset(c.x + cos(a) * 30.dp.toPx(), c.y + sin(a) * 30.dp.toPx())) }
                if (m.pulled) drawLine(Brush.horizontalGradient(listOf(tint.copy(alpha = 0.7f), Color.Transparent), c.x, c.x + 90.dp.toPx()), c, Offset(c.x + 90.dp.toPx(), c.y), 4.dp.toPx(), StrokeCap.Round)
            }
            // pick-up animation on the pilot
            for (g in grabs) {
                val dur = if (g.legend) 3.2f else 2.0f
                val tint = if (g.legend) gold else cyan
                val a = g.age
                if (a < 0.6f) {   // burst ring + flash
                    val k = a / 0.6f
                    drawCircle(tint.copy(alpha = 0.9f * (1f - k)), (16f + (if (g.legend) 120f else 80f) * k).dp.toPx(), pc, style = Stroke((6f * (1f - k) + 1f).dp.toPx()))
                    drawCircle(Color.White.copy(alpha = 0.55f * (1f - k)), 30.dp.toPx(), pc)
                }
                // bullets orbit in and dock into the gun
                val gun = Offset(pc.x + 26.dp.toPx(), pc.y)
                for (i in 0 until g.rounds) {
                    val p = ((a - 0.1f - i * 0.06f) / 0.55f).coerceIn(0f, 1f)
                    if (p <= 0f || p >= 1f) continue
                    val ang = i * (2f * PI.toFloat() / g.rounds) + a * 4f
                    val start = Offset(pc.x + cos(ang) * 70.dp.toPx(), pc.y + sin(ang) * 70.dp.toPx())
                    val pos = Offset(start.x + (gun.x - start.x) * p, start.y + (gun.y - start.y) * p)
                    drawRoundRect(hot.copy(alpha = 1f - p * 0.4f), Offset(pos.x - 2.dp.toPx(), pos.y - 5.dp.toPx()), Size(4.dp.toPx(), 10.dp.toPx()), CornerRadius(2.dp.toPx()))
                }
                // armor plates assemble around the pilot, shimmer, then fade
                val appear = (a / 0.7f).coerceIn(0f, 1f)
                val fade = if (a > dur - 0.6f) ((dur - a) / 0.6f).coerceIn(0f, 1f) else 1f
                val r = (62f - 28f * appear).dp.toPx()
                for (i in 0..5) {
                    val ang = a * 1.2f + i * (PI.toFloat() / 3f)
                    val c = Offset(pc.x + cos(ang) * r, pc.y + sin(ang) * r)
                    hex(c, (if (g.legend) 11f else 9f).dp.toPx(), ang, tint.copy(alpha = 0.9f * fade * appear), 2.dp.toPx())
                    if (g.legend) drawCircle(hot.copy(alpha = 0.25f * fade * appear), 5.dp.toPx(), c)
                }
                drawCircle(tint.copy(alpha = 0.35f * fade * appear), r, pc, style = Stroke(1.5f.dp.toPx()))
                drawCircle(Brush.radialGradient(listOf(tint.copy(alpha = 0.22f * fade), Color.Transparent), pc, r), r, pc)
                // gun glow while it charges
                if (a < 1.2f) drawCircle(hot.copy(alpha = 0.5f * (1f - a / 1.2f) * (0.6f + 0.4f * sin(a * 30f))), 10.dp.toPx(), gun)
            }
        }
        if (popup.isNotEmpty() && popupAge >= 0f) {
            val al = (1f - (popupAge / 2.2f).coerceIn(0f, 1f) * 0.8f)
            Box(Modifier.offset { labelPos }.width(180.dp), contentAlignment = Alignment.Center) {
                Text(popup, color = (if (legend) gold else cyan).copy(alpha = al), fontSize = 13.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(Color(0xCC080620)).border(1.5.dp, (if (legend) gold else cyan).copy(alpha = al), RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 5.dp))
            }
        }
    }
}
