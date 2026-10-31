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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neonrush.game.BossLunge
import com.neonrush.game.Fleet
import com.neonrush.game.Lance
import com.neonrush.game.NeonSoundEngine
import com.neonrush.game.PilotDrift
import com.neonrush.game.NrUpgrades
import kotlin.math.PI
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

private class LanceCell(var x: Float, var y: Float, var age: Float = 0f, var pulled: Boolean = false)

/**
 * PULSE LANCE overlay (Stage 157): lance cells drift in during boss fights and are collected by flying into them. The LANCE button shows ONLY while the boss
 * (or a hunter escort) is charging at the pilot, and goes away when the charge ends or the lance is fired. [onLance] gets the hull fraction to apply.
 */
@Composable
fun LanceOverlay(active: Boolean, bossActive: Boolean, bossY: Int, pilotY: Int, onLance: (Float) -> Unit, modifier: Modifier = Modifier) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val sfx = remember { NeonSoundEngine() }
    val bossRef = rememberUpdatedState(bossActive)
    val bossYRef = rememberUpdatedState(bossY)
    val yRef = rememberUpdatedState(pilotY)
    val lanceRef = rememberUpdatedState(onLance)
    val cells = remember { ArrayList<LanceCell>() }
    var sz by remember { mutableStateOf(IntSize.Zero) }
    var frame by remember { mutableStateOf(0) }
    var window by remember { mutableStateOf(false) }
    var used by remember { mutableStateOf(false) }
    var beamAge by remember { mutableStateOf(-1f) }
    var beamTo by remember { mutableStateOf(Offset.Zero) }
    var banner by remember { mutableStateOf("") }
    var bannerAge by remember { mutableStateOf(-1f) }
    val count = Lance.count(ctx)

    fun fire() {
        if (!window || used || beamAge >= 0f) return
        if (!Lance.use(ctx)) return
        used = true; beamAge = 0f
        val bx = 0.88f + BossLunge.shift + Fleet.dx
        val gap = (bx - PilotDrift.x).coerceAtLeast(0f)
        val band = Lance.band(gap)
        val frac = Lance.fraction(NrUpgrades.lanceLevel(), band)
        beamTo = Offset(bx, bossYRef.value.toFloat())
        banner = "PULSE LANCE · ${Lance.BAND_NAMES[band]} · -${(frac * 100f).toInt()}%"; bannerAge = 0f
        sfx.playNova(); sfx.playAirBoom()
        lanceRef.value(frac)
    }

    LaunchedEffect(active) {
        cells.clear(); window = false; used = false; beamAge = -1f; bannerAge = -1f
        if (!active) return@LaunchedEffect
        NrUpgrades.refresh(ctx)
        var last = 0L; var wasBoss = false; var spawnIn = 14f
        while (true) {
            val now = withFrameNanos { it }
            val dt = if (last == 0L) 0.016f else ((now - last) / 1_000_000_000f).coerceAtMost(0.05f)
            last = now
            if (com.neonrush.game.Pause.frozen()) { frame++; continue }
            val boss = bossRef.value
            if (boss && !wasBoss) { spawnIn = Random.nextFloat() * 10f + 12f }
            if (!boss && wasBoss) cells.clear()
            wasBoss = boss
            // the window: the main boss lunging at the pilot, or a hunter escort telegraphing / diving
            val w2 = boss && (BossLunge.vulnerable || Fleet.bodies.any { it.alive && (it.tel > 0f || it.diving) })
            if (!w2) used = false
            window = w2
            val w = sz.width.toFloat(); val h = sz.height.toFloat()
            if (boss && w > 0f) {
                if (cells.isEmpty() && Lance.count(ctx) < Lance.max(ctx)) { spawnIn -= dt; if (spawnIn <= 0f) { spawnIn = Random.nextFloat() * 15f + 35f; cells.add(LanceCell(1.1f, Random.nextFloat() * 50f + 25f)); sfx.playAirLock() } }
                val px = PilotDrift.x * w; val py = yRef.value / 100f * h
                val it = cells.iterator()
                while (it.hasNext()) {
                    val c = it.next(); c.age += dt
                    val dx = px - c.x * w; val dy = py - c.y / 100f * h; val d = hypot(dx, dy)
                    val dens = h / 400f
                    if (d < 110f * dens) c.pulled = true
                    if (c.pulled && d > 1f) { val k = 2.5f + 4f * (1f - (d / (110f * dens)).coerceIn(0f, 1f)); c.x += (dx / w) * k * dt; c.y += (dy / h) * 100f * k * dt } else c.x -= 0.22f * dt
                    if (d < 30f * dens) {
                        it.remove(); val ok = Lance.add(ctx)
                        banner = if (ok) "+1 PULSE LANCE CELL (${Lance.count(ctx)}/${Lance.MAX})" else "LANCE STORAGE FULL"; bannerAge = 0f; sfx.playArmorPickup()
                    } else if (c.x < -0.1f) it.remove()
                }
            }
            if (beamAge >= 0f) { beamAge += dt; if (beamAge > 0.5f) beamAge = -1f }
            if (bannerAge >= 0f) { bannerAge += dt; if (bannerAge > 2.2f) bannerAge = -1f }
            frame++
        }
    }

    Box(modifier.fillMaxSize().onSizeChanged { sz = it }) {
        Canvas(Modifier.fillMaxSize()) {
            @Suppress("UNUSED_VARIABLE") val f = frame
            val w = size.width; val h = size.height
            if (w <= 0f) return@Canvas
            for (c in cells) {
                val cx = c.x * w; val cy = c.y / 100f * h; val p = 0.5f + 0.5f * sin(c.age * 6f); val r = h * 0.03f
                drawCircle(Color(0xFF00E5FF).copy(alpha = 0.15f + 0.15f * p), r * 2.2f, Offset(cx, cy))
                drawCircle(Color(0xFF00E5FF).copy(alpha = 0.8f), r * (1.3f + 0.15f * p), Offset(cx, cy), style = Stroke(2.dp.toPx()))
                val d = Path().apply { moveTo(cx, cy - r); lineTo(cx + r * 0.7f, cy); lineTo(cx, cy + r); lineTo(cx - r * 0.7f, cy); close() }
                drawPath(d, Brush.verticalGradient(listOf(Color.White, Color(0xFF00B8D4)), cy - r, cy + r))
            }
            if (beamAge >= 0f) {
                val k = (beamAge / 0.5f).coerceIn(0f, 1f)
                val from = Offset(PilotDrift.x * w + 0.04f * w, yRef.value / 100f * h); val to = Offset(beamTo.x * w, beamTo.y / 100f * h)
                drawLine(Color(0xFF00E5FF).copy(alpha = 0.85f * (1f - k)), from, to, (16f * (1f - k) + 2f).dp.toPx(), StrokeCap.Round)
                drawLine(Color.White.copy(alpha = 1f - k), from, to, (6f * (1f - k) + 1f).dp.toPx(), StrokeCap.Round)
                drawCircle(Color.White.copy(alpha = 0.8f * (1f - k)), h * (0.04f + 0.25f * k), to, style = Stroke((6f * (1f - k) + 1f).dp.toPx()))
            }
        }
        if (window && !used && count > 0 && beamAge < 0f) {
            GameButton(BtnKind.PULSE, 58.dp, Color(0xFF00E5FF), "LANCE", "x$count", 1f, true, false, { fire() },
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 96.dp, bottom = 96.dp))
        }
        if (bannerAge >= 0f) {
            Text(banner, color = Color(0xFF00E5FF), fontSize = 12.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 118.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xDD080620)).border(1.5.dp, Color(0xFF00E5FF).copy(alpha = 0.8f), RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 6.dp))
        }
    }
}
