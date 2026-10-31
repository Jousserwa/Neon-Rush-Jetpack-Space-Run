package com.neonrush.game.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize as PxSize
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neonrush.game.BossLunge
import com.neonrush.game.Fleet
import com.neonrush.game.NeonSoundEngine
import com.neonrush.game.PilotDrift
import com.neonrush.game.Siege
import com.neonrush.game.Wingman
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

private class Tracer(var x0: Float, var y0: Float, var x1: Float, var y1: Float, var age: Float = 0f)

/**
 * COMBAT WINGMAN overlay (Stage 159): the button (boss fight + severe attack + sorties left), the wingman itself (a military gunship that mirrors your flying, with a
 * rotor-less jet body, twin cannons, a flame and a timer bar), its tracers, and its hits. [onShot] gets the damage multiplier for a shot at the MAIN boss,
 * [onClear] shoots down the obstacles and enemy bullets in front of it at height y (returns how many went).
 */
@Composable
fun WingmanOverlay(
    active: Boolean, bossActive: Boolean, bossY: Int, pilotY: Int, hullPct: Int, testing: Boolean, bossZoneOk: Boolean,
    onShot: (Float) -> Unit, onClear: (Int) -> Int, modifier: Modifier = Modifier
) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val sfx = remember { NeonSoundEngine() }
    val tm = rememberTextMeasurer()
    val bossRef = rememberUpdatedState(bossActive)
    val bossYRef = rememberUpdatedState(bossY)
    val yRef = rememberUpdatedState(pilotY)
    val hullRef = rememberUpdatedState(hullPct)
    val okRef = rememberUpdatedState(bossZoneOk)
    val shotRef = rememberUpdatedState(onShot)
    val clearRef = rememberUpdatedState(onClear)
    val tracers = remember { ArrayList<Tracer>() }
    val art = rememberArt("wingman_mil")   // res/drawable-nodpi/wingman_mil.webp: when it is uploaded it replaces the code-drawn gunship
    var tilt by remember { mutableStateOf(0f) }
    var sz by remember { mutableStateOf(IntSize.Zero) }
    var frame by remember { mutableStateOf(0) }
    var clock by remember { mutableStateOf(0f) }
    var wy by remember { mutableStateOf(50f) }
    var wx by remember { mutableStateOf(-0.1f) }
    var entry by remember { mutableStateOf(0f) }
    var banner by remember { mutableStateOf("") }
    var bannerAge by remember { mutableStateOf(-1f) }
    var severe by remember { mutableStateOf(false) }
    val sorties = Wingman.sorties(ctx, testing)
    val unlocked = testing || okRef.value
    val left = sorties - Wingman.usedRound

    fun launch() {
        if (Wingman.active || left <= 0) return
        Wingman.usedRound += 1; Wingman.active = true; Wingman.left = Wingman.length(); entry = 0f
        wy = yRef.value.toFloat(); wx = -0.1f
        banner = "WINGMAN IN · ${Wingman.length().toInt()}s"; bannerAge = 0f; sfx.playAirRadio(true); sfx.playBossCharge()
    }

    LaunchedEffect(active) {
        Wingman.reset(); tracers.clear()
        if (!active) return@LaunchedEffect
        com.neonrush.game.Upgrades.refresh(ctx)
        var last = 0L; var fireCd = 0f; var clearCd = 0f
        while (true) {
            val now = withFrameNanos { it }
            val dt = if (last == 0L) 0.016f else ((now - last) / 1_000_000_000f).coerceAtMost(0.05f)
            last = now; clock += dt
            if (com.neonrush.game.Pause.frozen()) { frame++; continue }
            val boss = bossRef.value
            Wingman.syncRound(Siege.zone * 100 + Siege.round)
            severe = boss && (hullRef.value <= 60 || BossLunge.vulnerable || Fleet.bodies.any { it.alive && (it.tel > 0f || it.diving) })
            if (!boss && Wingman.active) { Wingman.active = false; banner = "WINGMAN OUT"; bannerAge = 0f }
            if (Wingman.active) {
                Wingman.left -= dt; entry = (entry + dt * 1.8f).coerceAtMost(1f)
                if (Wingman.left <= 0f) { Wingman.active = false; banner = "WINGMAN RETURNS TO BASE"; bannerAge = 0f; sfx.playAirRadio(false) }
                // mirror the pilot: same height shifted to the open side, same drift, a short lag
                val py = yRef.value.toFloat(); val off = if (py > 50f) -17f else 17f
                val oldWy = wy
                wy += ((py + off).coerceIn(8f, 92f) - wy) * (dt * 7f).coerceAtMost(1f)
                tilt += (((wy - oldWy) / dt.coerceAtLeast(0.001f)).coerceIn(-60f, 60f) * 0.25f - tilt) * (dt * 8f).coerceAtMost(1f)   // banks with the climb
                wx += ((PilotDrift.x + 0.05f).coerceIn(0.05f, 0.9f) - wx) * (dt * 5f).coerceAtMost(1f) * entry
                if (entry < 1f) wx = -0.1f + (PilotDrift.x + 0.05f + 0.1f) * entry
                // fire: nearest escort first, then the main boss
                fireCd -= dt
                if (fireCd <= 0f && entry >= 1f) {
                    fireCd = Wingman.gap()
                    val tgt = Fleet.bodies.filter { it.alive }.minByOrNull { abs(it.y - wy) }
                    val bx = 0.88f + com.neonrush.game.BossLunge.shift + Fleet.dx
                    if (tgt != null) { Fleet.hitBolt(wx, tgt.x + 0.02f, tgt.y, true); tracers.add(Tracer(wx + 0.06f, wy, tgt.x, tgt.y)) }
                    else { shotRef.value(Wingman.damageMul()); tracers.add(Tracer(wx + 0.06f, wy, bx, bossYRef.value.toFloat())) }
                    sfx.playShot(true)
                }
                // clear the lane in front of it
                clearCd -= dt
                if (clearCd <= 0f) { clearCd = 0.9f; if (clearRef.value(wy.toInt()) > 0) sfx.playSmash() }
            }
            val ti = tracers.iterator(); while (ti.hasNext()) { val t = ti.next(); t.age += dt; if (t.age > 0.14f) ti.remove() }
            if (bannerAge >= 0f) { bannerAge += dt; if (bannerAge > 2.2f) bannerAge = -1f }
            frame++
        }
    }

    Box(modifier.fillMaxSize().onSizeChanged { sz = it }) {
        Canvas(Modifier.fillMaxSize()) {
            @Suppress("UNUSED_VARIABLE") val f = frame
            val w = size.width; val h = size.height
            if (w <= 0f) return@Canvas
            for (t in tracers) {
                val k = (t.age / 0.14f).coerceIn(0f, 1f)
                drawLine(Color(0xFFFFB74D).copy(alpha = 1f - k), Offset(t.x0 * w, t.y0 / 100f * h), Offset(t.x1 * w, t.y1 / 100f * h), 3.dp.toPx(), StrokeCap.Round)
                drawLine(Color.White.copy(alpha = 0.9f * (1f - k)), Offset(t.x0 * w, t.y0 / 100f * h), Offset(t.x1 * w, t.y1 / 100f * h), 1.dp.toPx(), StrokeCap.Round)
            }
            if (Wingman.active) drawWingman(Offset(wx * w, wy / 100f * h), h * 0.075f, clock, Wingman.left / Wingman.length(), Wingman.arms(), tm, art, tilt)
        }
        if (unlocked && severe && !Wingman.active && left > 0) {
            GameButton(BtnKind.OVER, 58.dp, Color(0xFFFFB74D), "WINGMAN", "x$left", 1f, true, false, { launch() },
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 164.dp, bottom = 96.dp))
        }
        if (bannerAge >= 0f) {
            androidx.compose.material3.Text(banner, color = Color(0xFFFFB74D), fontSize = 12.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 142.dp))
        }
    }
}

/** The wingman: an olive-and-orange military gunship (a copy of the pilot's craft, armed), twin cannons with muzzle flash, a flame, a halo, a timer bar. Arms Mk adds bars on the wings. */
private fun DrawScope.drawWingman(c: Offset, s: Float, t: Float, timeFrac: Float, arms: Int, tm: androidx.compose.ui.text.TextMeasurer, art: ImageBitmap?, tilt: Float) {
    val halo = 0.5f + 0.5f * sin(t * 6f)
    drawCircle(Brush.radialGradient(listOf(Color(0xFFFFB74D).copy(alpha = 0.35f), Color.Transparent), c, s * 2.6f), s * 2.6f, c)
    drawCircle(Color(0xFFFFB74D).copy(alpha = 0.45f + 0.3f * halo), s * 1.5f, c, style = Stroke(2.dp.toPx()))
    val fl = 0.6f + 0.4f * sin(t * 45f)
    val flame = Path().apply { moveTo(c.x - s * 0.9f, c.y - s * 0.2f); lineTo(c.x - s * (1.7f + 0.7f * fl), c.y); lineTo(c.x - s * 0.9f, c.y + s * 0.2f); close() }
    drawPath(flame, Brush.horizontalGradient(listOf(Color(0xFFFF6F00).copy(alpha = 0f), Color(0xFFFFF59D)), c.x - s * 2.4f, c.x - s * 0.9f))
    if (art != null) {
        // real art: bobs, banks with the climb, engine glow behind, muzzle flashes at the nose cannons
        val dw = s * 3.9f; val dh = dw * art.height / art.width.toFloat()
        val bob = sin(t * 3.2f) * s * 0.07f
        rotate(tilt, Offset(c.x, c.y + bob)) {
            drawImage(art, dstOffset = IntOffset((c.x - dw / 2f).toInt(), (c.y + bob - dh / 2f).toInt()), dstSize = PxSize(dw.toInt(), dh.toInt()))
            for (side in intArrayOf(-1, 1)) if (((t * 14f).toInt() + side) % 2 == 0) {
                val m = Offset(c.x + dw * 0.5f, c.y + bob + side * dh * 0.1f)
                drawCircle(Brush.radialGradient(listOf(Color.White, Color(0xFFFFB74D), Color.Transparent), m, s * 0.4f), s * 0.4f, m)
            }
        }
    } else {
        val hull = Path().apply {
            moveTo(c.x + s * 1.35f, c.y)
            lineTo(c.x + s * 0.3f, c.y - s * 0.38f); lineTo(c.x - s * 0.5f, c.y - s * 1.0f); lineTo(c.x - s * 0.95f, c.y - s * 0.95f)
            lineTo(c.x - s * 0.65f, c.y - s * 0.2f); lineTo(c.x - s * 0.75f, c.y + s * 0.2f)
            lineTo(c.x - s * 0.95f, c.y + s * 0.95f); lineTo(c.x - s * 0.5f, c.y + s * 1.0f); lineTo(c.x + s * 0.3f, c.y + s * 0.38f); close()
        }
        drawPath(hull, Brush.verticalGradient(listOf(Color(0xFF6D7B3A), Color(0xFF2E3318), Color(0xFF6D7B3A)), c.y - s, c.y + s))
        drawPath(hull, Color(0xFFFFB74D), style = Stroke(2.dp.toPx()))
        drawCircle(Color(0xFF80D8FF).copy(alpha = 0.9f), s * 0.2f, Offset(c.x + s * 0.35f, c.y))
        for (q in 0..arms.coerceAtMost(4)) drawLine(Color(0xFFFFB74D), Offset(c.x - s * (0.2f + 0.14f * q), c.y - s * 0.78f), Offset(c.x - s * (0.2f + 0.14f * q), c.y - s * 0.6f), 2.dp.toPx())
        for (side in intArrayOf(-1, 1)) {
            val gy = c.y + side * s * 0.62f
            drawLine(Color(0xFFB0BEC5), Offset(c.x + s * 0.1f, gy), Offset(c.x + s * 1.7f, gy), 4.dp.toPx(), StrokeCap.Round)
            if (((t * 14f).toInt() + side) % 2 == 0) drawCircle(Color(0xFFFFF59D), s * 0.17f, Offset(c.x + s * 1.8f, gy))
        }
    }
    val bw = s * 2.4f; val bx = c.x - bw / 2f; val by = c.y - s * 1.45f
    drawRect(Color.Black.copy(alpha = 0.6f), Offset(bx, by), Size(bw, 4.dp.toPx()))
    drawRect(Color(0xFFFFB74D), Offset(bx, by), Size(bw * timeFrac.coerceIn(0f, 1f), 4.dp.toPx()))
    val tx = tm.measure("WINGMAN", TextStyle(color = Color(0xFFFFB74D), fontSize = 9.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace))
    drawText(tx, topLeft = Offset(c.x - tx.size.width / 2f, by - tx.size.height - 2.dp.toPx()))
}
