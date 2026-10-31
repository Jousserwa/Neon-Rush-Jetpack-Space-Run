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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neonrush.game.BossLunge
import com.neonrush.game.Fleet
import com.neonrush.game.NeonSoundEngine
import com.neonrush.game.PilotDrift
import com.neonrush.game.R
import com.neonrush.game.Siege
import com.neonrush.game.Wingman
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

private class Tracer(var x0: Float, var y0: Float, var x1: Float, var y1: Float, var col: Color, var age: Float = 0f)
private class Crate(var x: Float, var y: Float, val kind: Int, var age: Float = 0f)

/** The six run frames of the pilot (the wingman is a copy of them). If wingman_run_1..6 are uploaded to res/drawable-nodpi they are used instead (custom = true, no colour grade). */
@Composable
internal fun rememberWingFrames(): Pair<List<ImageBitmap>, Boolean> {
    val mine = (1..6).map { rememberArt("wingman_run_$it") }
    val custom = mine.all { it != null }
    val base = listOf(
        ImageBitmap.imageResource(id = R.drawable.pilot_run_1), ImageBitmap.imageResource(id = R.drawable.pilot_run_2), ImageBitmap.imageResource(id = R.drawable.pilot_run_3),
        ImageBitmap.imageResource(id = R.drawable.pilot_run_4), ImageBitmap.imageResource(id = R.drawable.pilot_run_5), ImageBitmap.imageResource(id = R.drawable.pilot_run_6)
    )
    return if (custom) Pair(mine.map { it!! }, true) else Pair(base, false)
}

/** Where gunship drone [k] hovers relative to the wingman's centre [c] (shared by the drawing and the tracers). */
internal fun droneAt(c: Offset, hgt: Float, t: Float, k: Int): Offset =
    Offset(c.x + hgt * 0.35f + sin(t * 2f + k * 3f) * hgt * 0.08f, c.y + (if (k == 0) -1f else 1f) * hgt * 0.78f + cos(t * 2.4f + k) * hgt * 0.05f)

/**
 * COMBAT WINGMAN overlay (Stage 159 v2): the call button (boss fight + severe attack + sorties left; in testing mode any boss fight), the wingman itself (a military copy of your
 * pilot with rifle, jet-pack and gunship drones, flying beside you), its tracers, and the ordnance crates it collects on its own. It shoots by itself AND every time you shoot.
 * [onShot] gets the damage multiplier for a shot at the MAIN boss, [onClear] shoots down obstacles / enemy bullets in front of it at height y (returns how many went).
 */
@Composable
fun WingmanOverlay(
    active: Boolean, bossActive: Boolean, bossY: Int, pilotY: Int, hullPct: Int, testing: Boolean, bossZoneOk: Boolean,
    onShot: (Float) -> Unit, onClear: (Int) -> Int, modifier: Modifier = Modifier
) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val sfx = remember { NeonSoundEngine() }
    val tm = rememberTextMeasurer()
    val (frames, custom) = rememberWingFrames()
    val bossRef = rememberUpdatedState(bossActive)
    val bossYRef = rememberUpdatedState(bossY)
    val yRef = rememberUpdatedState(pilotY)
    val hullRef = rememberUpdatedState(hullPct)
    val shotRef = rememberUpdatedState(onShot)
    val clearRef = rememberUpdatedState(onClear)
    val tracers = remember { ArrayList<Tracer>() }
    val crates = remember { ArrayList<Crate>() }
    var sz by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    var frame by remember { mutableStateOf(0) }
    var clock by remember { mutableStateOf(0f) }
    var wy by remember { mutableStateOf(50f) }
    var wx by remember { mutableStateOf(-0.1f) }
    var tilt by remember { mutableStateOf(0f) }
    var entry by remember { mutableStateOf(0f) }
    var banner by remember { mutableStateOf("") }
    var bannerAge by remember { mutableStateOf(-1f) }
    var severe by remember { mutableStateOf(false) }
    var weapon by remember { mutableStateOf(-1) }
    var weaponLeft by remember { mutableStateOf(0f) }
    val sorties = Wingman.sorties(ctx, testing)
    val unlocked = testing || bossZoneOk
    val left = sorties - Wingman.usedRound
    val skin = Wingman.SKINS[Wingman.equippedSkin(ctx)]

    fun launch() {
        if (Wingman.active || left <= 0) return
        Wingman.usedRound += 1; Wingman.active = true; Wingman.left = Wingman.length(); entry = 0f
        wy = yRef.value.toFloat(); wx = -0.1f; weapon = -1; crates.clear()
        banner = "WINGMAN IN · ${Wingman.length().toInt()}s"; bannerAge = 0f; sfx.playAirRadio(true); sfx.playBossCharge()
    }

    LaunchedEffect(active) {
        Wingman.reset(); tracers.clear(); crates.clear(); weapon = -1
        if (!active) return@LaunchedEffect
        com.neonrush.game.Upgrades.refresh(ctx)
        var last = 0L; var fireCd = 0f; var clearCd = 0f; var crateIn = 5f; var seenPilotShot = Wingman.pilotShotSeq
        while (true) {
            val now = withFrameNanos { it }
            val dt = if (last == 0L) 0.016f else ((now - last) / 1_000_000_000f).coerceAtMost(0.05f)
            last = now; clock += dt
            if (com.neonrush.game.Pause.frozen()) { frame++; continue }
            val boss = bossRef.value
            Wingman.syncRound(Siege.zone * 100 + Siege.round)
            severe = boss && (hullRef.value <= 60 || BossLunge.vulnerable || Fleet.bodies.any { it.alive && (it.tel > 0f || it.diving) })
            if (!boss && Wingman.active) { Wingman.active = false; crates.clear(); banner = "WINGMAN OUT"; bannerAge = 0f }
            val w = sz.width.toFloat(); val h = sz.height.toFloat()
            if (Wingman.active && w > 0f) {
                Wingman.left -= dt; entry = (entry + dt * 1.8f).coerceAtMost(1f)
                if (Wingman.left <= 0f) { Wingman.active = false; crates.clear(); banner = "WINGMAN RETURNS TO BASE"; bannerAge = 0f; sfx.playAirRadio(false) }
                // ordnance crates drift in; the wingman steers to the nearest one by itself
                crateIn -= dt
                if (crateIn <= 0f && crates.size < 2) { crateIn = Random.nextFloat() * 3f + 5f; crates.add(Crate(1.05f, Random.nextFloat() * 70f + 15f, Random.nextInt(3))) }
                val ci = crates.iterator()
                var chase: Crate? = null
                while (ci.hasNext()) {
                    val c = ci.next(); c.age += dt; c.x -= 0.26f * dt
                    if (abs(c.x - wx) < 0.06f && abs(c.y - wy) < 9f) {
                        ci.remove(); weapon = c.kind; weaponLeft = if (c.kind == 0) 6f else if (c.kind == 1) 8f else 2f
                        banner = "WINGMAN ARMED · ${Wingman.ORD_NAMES[c.kind]}"; bannerAge = 0f; sfx.playArmorPickup()
                    } else if (c.x < -0.05f) ci.remove()
                    else if (c.x > wx + 0.03f && (chase == null || c.x < chase.x)) chase = c
                }
                // mirror the pilot: same drift, the open side above/below, a short lag; or steer to a crate
                val py = yRef.value.toFloat(); val off = if (py > 50f) -26f else 26f
                val goalY = chase?.y ?: (py + off)
                val oldWy = wy
                wy += (goalY.coerceIn(10f, 90f) - wy) * (dt * 6f).coerceAtMost(1f)
                tilt += (((wy - oldWy) / dt.coerceAtLeast(0.001f)).coerceIn(-60f, 60f) * 0.2f - tilt) * (dt * 8f).coerceAtMost(1f)
                val goalX = if (chase != null) (chase.x - 0.1f).coerceIn(0.1f, 0.6f) else (PilotDrift.x - 0.02f).coerceIn(0.05f, 0.9f)
                wx += (goalX - wx) * (dt * 5f).coerceAtMost(1f) * entry
                if (entry < 1f) wx = -0.12f + (PilotDrift.x + 0.1f) * entry
                // shooting: by itself, and whenever the pilot fires
                fireCd -= dt
                val pilotFired = Wingman.pilotShotSeq != seenPilotShot
                if (pilotFired) seenPilotShot = Wingman.pilotShotSeq
                val gapNow = Wingman.gap() * (if (weapon == 1) 0.4f else 1f)
                if (entry >= 1f && (fireCd <= 0f || (pilotFired && fireCd < gapNow * 0.6f))) {
                    fireCd = gapNow
                    val hgt = h * 0.2f; val cpx = Offset(wx * w, wy / 100f * h)
                    val mulW = Wingman.damageMul() * (if (weapon == 0) 3f else if (weapon == 2) 6f else 1f)
                    val col = if (weapon >= 0) Color(Wingman.ORD_COLS[weapon]) else Color(skin.accent)
                    val tgt = Fleet.bodies.filter { it.alive }.minByOrNull { abs(it.y - wy) }
                    val bx = 0.88f + BossLunge.shift + Fleet.dx; val by = bossYRef.value.toFloat()
                    fun shoot(ox: Float, oy: Float, mul: Float) {
                        if (tgt != null) { repeat(mul.roundToInt().coerceIn(1, 6)) { Fleet.hitBolt(ox, tgt.x + 0.02f, tgt.y, true) }; tracers.add(Tracer(ox, oy, tgt.x, tgt.y, col)) }
                        else { shotRef.value(mul); tracers.add(Tracer(ox, oy, bx, by, col)) }
                    }
                    shoot(wx + 0.07f, wy, mulW)
                    for (k in 0 until Wingman.drones()) { val d = droneAt(cpx, hgt, clock, k); shoot(d.x / w + 0.03f, d.y / h * 100f, Wingman.damageMul() * 0.5f) }
                    if (weapon == 0) sfx.playAirMissile() else if (weapon == 2) sfx.playNova() else sfx.playShot(true)
                    if (weapon == 0 || weapon == 2) { weaponLeft -= 1f; if (weaponLeft <= 0f) weapon = -1 }
                }
                if (weapon == 1) { weaponLeft -= dt; if (weaponLeft <= 0f) weapon = -1 }
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
                drawLine(t.col.copy(alpha = 1f - k), Offset(t.x0 * w, t.y0 / 100f * h), Offset(t.x1 * w, t.y1 / 100f * h), 3.dp.toPx(), StrokeCap.Round)
                drawLine(Color.White.copy(alpha = 0.9f * (1f - k)), Offset(t.x0 * w, t.y0 / 100f * h), Offset(t.x1 * w, t.y1 / 100f * h), 1.dp.toPx(), StrokeCap.Round)
            }
            for (c in crates) drawCrate(Offset(c.x * w, c.y / 100f * h), h * 0.03f, c.kind, c.age)
            if (Wingman.active) {
                val label = if (weapon >= 0) Wingman.ORD_NAMES[weapon] else "WINGMAN"
                drawWingmanPilot(Offset(wx * w, wy / 100f * h), h * 0.2f, frames[(clock * 10f).toInt() % frames.size], custom, clock, skin, Wingman.arms(), Wingman.drones(), tilt, Wingman.left / Wingman.length(), label, tm)
            }
        }
        if (unlocked && (severe || testing) && bossActive && !Wingman.active && left > 0) {
            GameButton(BtnKind.OVER, 58.dp, Color(skin.accent), "WINGMAN", "x$left", 1f, true, false, { launch() },
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 164.dp, bottom = 96.dp))
        }
        if (bannerAge >= 0f) {
            androidx.compose.material3.Text(banner, color = Color(skin.accent), fontSize = 12.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 142.dp))
        }
    }
}

private fun DrawScope.drawCrate(c: Offset, s: Float, kind: Int, age: Float) {
    val col = Color(Wingman.ORD_COLS[kind]); val p = 0.5f + 0.5f * sin(age * 6f)
    drawCircle(col.copy(alpha = 0.18f + 0.15f * p), s * 2.2f, c)
    val d = Path().apply { moveTo(c.x - s, c.y - s * 0.8f); lineTo(c.x + s, c.y - s * 0.8f); lineTo(c.x + s, c.y + s * 0.8f); lineTo(c.x - s, c.y + s * 0.8f); close() }
    drawPath(d, Brush.verticalGradient(listOf(col, Color(0xFF263238)), c.y - s, c.y + s))
    drawPath(d, Color.White.copy(alpha = 0.8f), style = Stroke(1.5.dp.toPx()))
    drawLine(Color.White.copy(alpha = 0.9f), Offset(c.x - s * 0.5f, c.y), Offset(c.x + s * 0.5f, c.y), 2.dp.toPx(), StrokeCap.Round)
    drawLine(Color.White.copy(alpha = 0.9f), Offset(c.x, c.y - s * 0.4f), Offset(c.x, c.y + s * 0.4f), 2.dp.toPx(), StrokeCap.Round)
}

/**
 * The wingman: a full copy of the pilot's run frames in a military grade (or your own wingman_run_1..6 art), a rifle with a muzzle flash, a jet-pack flame, rank chevrons, two gunship
 * drones that hover and fire, a halo in the skin's accent colour, a timer bar and the current weapon name. Used by the game and by the shop page preview.
 */
internal fun DrawScope.drawWingmanPilot(
    c: Offset, hgt: Float, img: ImageBitmap, custom: Boolean, t: Float, skin: Wingman.Skin, arms: Int, drones: Int, tilt: Float, timeFrac: Float, label: String, tm: TextMeasurer
) {
    val acc = Color(skin.accent)
    val dw = hgt * img.width / img.height.toFloat()
    val halo = 0.5f + 0.5f * sin(t * 6f)
    drawCircle(Brush.radialGradient(listOf(acc.copy(alpha = 0.30f), Color.Transparent), c, hgt * 0.95f), hgt * 0.95f, c)
    // jet-pack flame behind the shoulders
    val fl = 0.6f + 0.4f * sin(t * 45f)
    val bp = Offset(c.x - dw * 0.22f, c.y + hgt * 0.02f)
    val flame = Path().apply { moveTo(bp.x, bp.y - hgt * 0.05f); lineTo(bp.x - hgt * (0.28f + 0.14f * fl), bp.y); lineTo(bp.x, bp.y + hgt * 0.05f); close() }
    drawPath(flame, Brush.horizontalGradient(listOf(Color(0xFFFF6F00).copy(alpha = 0f), Color(0xFFFFF59D)), bp.x - hgt * 0.45f, bp.x))
    rotate(tilt, c) {
        val filter = if (custom) null else ColorFilter.colorMatrix(ColorMatrix(skin.grade))
        drawImage(image = img, srcOffset = IntOffset.Zero, srcSize = IntSize(img.width, img.height),
            dstOffset = IntOffset((c.x - dw / 2f).roundToInt(), (c.y - hgt / 2f).roundToInt()), dstSize = IntSize(dw.roundToInt(), hgt.roundToInt()), colorFilter = filter)
        // rifle: stock, barrel, scope, muzzle flash
        val rs = Offset(c.x + dw * 0.02f, c.y + hgt * 0.03f); val re = Offset(c.x + dw * 0.62f, c.y - hgt * 0.01f)
        drawLine(Color(0xFF263238), rs, re, hgt * 0.05f, StrokeCap.Round)
        drawLine(Color(0xFF90A4AE), Offset(rs.x + dw * 0.2f, rs.y - hgt * 0.04f), Offset(re.x - dw * 0.1f, re.y - hgt * 0.05f), hgt * 0.025f, StrokeCap.Round)
        drawCircle(acc, hgt * 0.022f, Offset(rs.x + dw * 0.3f, rs.y - hgt * 0.06f))
        if (((t * 14f).toInt()) % 2 == 0) drawCircle(Brush.radialGradient(listOf(Color.White, acc, Color.Transparent), re, hgt * 0.13f), hgt * 0.13f, re)
        // belt of ammunition + shoulder stripe in the accent colour
        drawLine(acc.copy(alpha = 0.85f), Offset(c.x - dw * 0.18f, c.y - hgt * 0.1f), Offset(c.x + dw * 0.12f, c.y + hgt * 0.16f), hgt * 0.025f, StrokeCap.Round)
    }
    // rank chevrons above the head (one per arms level, at least one)
    for (q in 0..arms.coerceIn(0, 4)) {
        val y = c.y - hgt * 0.56f - q * hgt * 0.045f
        drawLine(acc, Offset(c.x - hgt * 0.07f, y + hgt * 0.025f), Offset(c.x, y), hgt * 0.014f, StrokeCap.Round)
        drawLine(acc, Offset(c.x + hgt * 0.07f, y + hgt * 0.025f), Offset(c.x, y), hgt * 0.014f, StrokeCap.Round)
    }
    // gunship drones
    for (k in 0 until drones.coerceIn(1, 2)) {
        val d = droneAt(c, hgt, t, k); val s = hgt * 0.11f
        drawCircle(acc.copy(alpha = 0.2f), s * 1.8f, d)
        val body = Path().apply { moveTo(d.x + s * 1.2f, d.y); lineTo(d.x + s * 0.2f, d.y - s * 0.55f); lineTo(d.x - s * 1.0f, d.y - s * 0.35f); lineTo(d.x - s * 1.0f, d.y + s * 0.35f); lineTo(d.x + s * 0.2f, d.y + s * 0.55f); close() }
        drawPath(body, Brush.verticalGradient(listOf(Color(0xFF6D7B3A), Color(0xFF2E3318)), d.y - s, d.y + s)); drawPath(body, acc, style = Stroke(1.5.dp.toPx()))
        drawLine(Color(0xFFB0BEC5), Offset(d.x + s * 0.4f, d.y - s * 0.25f), Offset(d.x + s * 1.6f, d.y - s * 0.25f), 2.dp.toPx(), StrokeCap.Round)
        drawLine(Color(0xFFB0BEC5), Offset(d.x + s * 0.4f, d.y + s * 0.25f), Offset(d.x + s * 1.6f, d.y + s * 0.25f), 2.dp.toPx(), StrokeCap.Round)
        val rl = s * (0.8f + 0.5f * abs(cos(t * 40f + k)))
        drawLine(Color.White.copy(alpha = 0.8f), Offset(d.x - rl, d.y - s * 0.62f), Offset(d.x + rl, d.y - s * 0.62f), 2.dp.toPx(), StrokeCap.Round)
        if (((t * 14f).toInt() + k) % 2 == 0) drawCircle(Color(0xFFFFF59D), s * 0.2f, Offset(d.x + s * 1.7f, d.y - s * 0.25f))
    }
    drawCircle(acc.copy(alpha = 0.35f + 0.3f * halo), hgt * 0.62f, c, style = Stroke(1.5.dp.toPx()))
    val bw = hgt * 0.9f; val bx = c.x - bw / 2f; val by = c.y - hgt * 0.78f
    drawRect(Color.Black.copy(alpha = 0.6f), Offset(bx, by), Size(bw, 4.dp.toPx()))
    drawRect(acc, Offset(bx, by), Size(bw * timeFrac.coerceIn(0f, 1f), 4.dp.toPx()))
    val tx = tm.measure(label, TextStyle(color = acc, fontSize = 9.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace))
    drawText(tx, topLeft = Offset(c.x - tx.size.width / 2f, by - tx.size.height - 2.dp.toPx()))
}
