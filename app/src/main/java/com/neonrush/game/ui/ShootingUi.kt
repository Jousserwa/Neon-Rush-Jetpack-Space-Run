package com.neonrush.game.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neonrush.game.BossGuard
import com.neonrush.game.NeonSoundEngine
import com.neonrush.game.Shooting
import com.neonrush.game.VisualTrackElement
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private class Bolt(var x: Float, val y: Float, val power: Boolean)
private class Boom(val x: Float, val y: Float, var age: Float = 0f)
private class Spark(var x: Float, var y: Float, var vx: Float, var vy: Float, var life: Float, val max: Float, val col: Color, val shard: Boolean = false, var rot: Float = 0f)
private class Ring(val x: Float, val y: Float, val col: Color, val big: Boolean, var age: Float = 0f)

/**
 * In-race shooting: a FIRE button with a heat ring (Legendary Pro) or a shot counter (Medium/Hard), the bolts themselves, and the hit tests.
 * Bolts destroy shootable obstacles through [onDestroy]; on Legendary they also chip the boss through [onBossHit].
 */
@Composable
fun ShootingOverlay(
    elements: List<VisualTrackElement>, pilotY: Int, bossActive: Boolean, bossY: Int, active: Boolean,
    tierName: String, isPro: Boolean, onDestroy: (String) -> Unit, onBossHit: () -> Unit, modifier: Modifier = Modifier
) {
    val ctx = LocalContext.current
    val mode = Shooting.mode(tierName, isPro)
    if (mode == 0) return
    val elRef = rememberUpdatedState(elements)
    val yRef = rememberUpdatedState(pilotY)
    val bossRef = rememberUpdatedState(bossActive)
    val bossYRef = rememberUpdatedState(bossY)
    val destroyRef = rememberUpdatedState(onDestroy)
    val hitRef = rememberUpdatedState(onBossHit)
    val bolts = remember { ArrayList<Bolt>() }
    var frame by remember { mutableStateOf(0) }
    var held by remember { mutableStateOf(false) }
    var cooldown by remember { mutableStateOf(0f) }
    val pack = Shooting.selectedBolt(ctx)
    val booms = remember { ArrayList<Boom>() }
    val sparks = remember { ArrayList<Spark>() }
    val rings = remember { ArrayList<Ring>() }
    var bossFlash by remember { mutableStateOf(0f) }
    var banner by remember { mutableStateOf("") }
    val sfx = remember { NeonSoundEngine() }
    val packRef = rememberUpdatedState(pack)
    // sparks + glass shards flying out of an impact (x 0..1 of the width, y 0..100 of the height)
    fun burst(x: Float, y: Float, col: Color, n: Int, speed: Float, shards: Int) {
        if (sparks.size > 260) return
        for (i in 0 until n) {
            val a = Random.nextFloat() * 6.2832f; val sp = speed * (0.5f + Random.nextFloat())
            sparks.add(Spark(x, y, cos(a) * sp, sin(a) * sp * 160f, 0.35f + Random.nextFloat() * 0.2f, 0.5f, col))
        }
        for (i in 0 until shards) {
            val a = Random.nextFloat() * 6.2832f; val sp = speed * (0.4f + Random.nextFloat() * 0.6f)
            sparks.add(Spark(x, y, cos(a) * sp, sin(a) * sp * 160f, 0.55f, 0.55f, Color.White, true, a))
        }
    }
    var muzzle by remember { mutableStateOf(0f) }
    val artM = rememberArt("shot_${pack.art}_m"); val artL = rememberArt("shot_${pack.art}_l")
    val artMuzzle = rememberArt("shot_muzzle")
    val artBoom = listOf(rememberArt("shot_boom1"), rememberArt("shot_boom2"), rememberArt("shot_boom3"), rememberArt("shot_boom4"))

    fun fire() {
        if (cooldown > 0f) return
        if (!Shooting.consume(ctx, mode)) { cooldown = 0.25f; sfx.playOverheat(); return }
        cooldown = (if (mode == 2) Shooting.FIRE_GAP_LEGEND else Shooting.FIRE_GAP_POWER) * (if (com.neonrush.game.LegendMoves.overdriveOn()) 0.5f else 1f)
        bolts.add(Bolt(0.25f, yRef.value.toFloat(), mode == 1))
        muzzle = 0.12f
        sfx.playShot(mode == 1)
        if (bossRef.value) BossGuard.noteShot()
        burst(0.24f, yRef.value.toFloat(), Color(packRef.value.argb2), 4, 0.25f, 0)
    }

    LaunchedEffect(active) {
        if (!active) { bolts.clear(); sparks.clear(); rings.clear(); BossGuard.incomingY = -1f; return@LaunchedEffect }
        var last = 0L
        var prevShield = false; var prevEn = 0
        while (true) {
            val now = withFrameNanos { it }
            val dt = if (last == 0L) 0.016f else ((now - last) / 1_000_000_000f).coerceAtMost(0.05f)
            last = now
            Shooting.cool(dt)
            if (cooldown > 0f) cooldown -= dt
            if (muzzle > 0f) muzzle -= dt
            if (bossFlash > 0f) bossFlash -= dt
            run {
                val si = sparks.iterator()
                while (si.hasNext()) { val sp = si.next(); sp.life -= dt; sp.x += sp.vx * dt; sp.y += sp.vy * dt; sp.vx *= 0.93f; sp.vy *= 0.93f; sp.rot += dt * 14f; if (sp.life <= 0f) si.remove() }
                val ri = rings.iterator()
                while (ri.hasNext()) { val rg = ri.next(); rg.age += dt; if (rg.age > 0.35f) ri.remove() }
            }
            // boss counter-measures feedback (shield / enrage): sounds + banner, only for pilots who can hurt the boss
            run {
                val on = mode == 2 && bossRef.value
                val su = on && BossGuard.shieldUp
                if (su && !prevShield) sfx.playShieldUp()
                prevShield = su
                val en = if (on) BossGuard.enrage else 0
                if (en > prevEn) sfx.playEnrage()
                prevEn = en
                val nb = if (!on) "" else if (su) "🛡 SHIELD UP - HOLD FIRE" else if (BossGuard.shieldWarn) "⚠ SHIELD CHARGING" else if (en == 2) "🔥 BOSS FURIOUS" else if (en == 1) "🔥 BOSS ENRAGED" else ""
                if (nb != banner) banner = nb
            }
            var incoming = -1f
            run { val bi = booms.iterator(); while (bi.hasNext()) { val bm = bi.next(); bm.age += dt; if (bm.age > 0.36f) bi.remove() } }
            if (held && mode == 2 && cooldown <= 0f) fire()
            val iter = bolts.iterator()
            while (iter.hasNext()) {
                val b = iter.next()
                val from = b.x
                b.x += dt * (if (b.power) 1.25f else 1.7f)
                if (sparks.size < 220) sparks.add(Spark(b.x - 0.025f, b.y + (Random.nextFloat() - 0.5f) * 3f, -0.12f, (Random.nextFloat() - 0.5f) * 16f, 0.22f, 0.22f, if (Random.nextBoolean()) Color(packRef.value.argb) else Color(packRef.value.argb2)))
                if (mode == 2 && bossRef.value && b.x > 0.28f && b.x < 0.85f) incoming = b.y
                var used = false
                for (e in elRef.value) {
                    if (!Shooting.canShoot(e.type, e.subType)) continue
                    if (e.xOffsetFraction >= from - 0.04f && e.xOffsetFraction <= b.x + 0.04f && abs(e.yMatchPos - b.y) <= 8f) { destroyRef.value(e.id); booms.add(Boom(e.xOffsetFraction, e.yMatchPos.toFloat())); used = true
                        sfx.playSmash(); burst(e.xOffsetFraction, e.yMatchPos.toFloat(), Color(packRef.value.argb), 12, 0.45f, 6); rings.add(Ring(e.xOffsetFraction, e.yMatchPos.toFloat(), Color(packRef.value.argb2), false)); break }
                }
                if (!used && mode == 2 && bossRef.value && b.x > 0.72f && abs(bossYRef.value - b.y) <= 14f) {
                    hitRef.value(); used = true
                    if (BossGuard.lastDeflect) {
                        sfx.playDeflect(); burst(0.83f, b.y, Color(0xFFB3E5FC), 9, 0.5f, 0); rings.add(Ring(0.87f, bossYRef.value.toFloat(), Color(0xFF80D8FF), false))
                    } else {
                        sfx.playBossHit(BossGuard.hitStreak / 8f); bossFlash = 0.2f; burst(0.85f, b.y, Color(0xFFFFB74D), 10, 0.5f, 3); rings.add(Ring(0.87f, bossYRef.value.toFloat(), Color(0xFFFF5252), false))
                    }
                }
                if (used || b.x > 1.1f) iter.remove()
            }
            BossGuard.incomingY = incoming
            frame++
        }
    }

    Box(modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            @Suppress("UNUSED_VARIABLE") val f = frame
            val c1 = Color(pack.argb); val c2 = Color(pack.argb2)
            fun sprite(img: ImageBitmap, rightX: Float, cy: Float, wPx: Float, alpha: Float = 1f) {
                val hPx = wPx * img.height / img.width
                drawImage(img, dstOffset = IntOffset((rightX - wPx).toInt(), (cy - hPx / 2f).toInt()), dstSize = IntSize(wPx.toInt(), hPx.toInt()), alpha = alpha)
            }
            for (b in bolts) {
                val x = b.x * size.width; val y = b.y / 100f * size.height
                val img = if (b.power) artL else artM
                if (img != null) sprite(img, x + 10.dp.toPx(), y, (if (b.power) 124.dp else 88.dp).toPx())
                else {
                    val len = if (b.power) 64f else 44f
                    drawLine(Brush.horizontalGradient(listOf(Color.Transparent, c1), startX = x - len, endX = x), Offset(x - len, y), Offset(x, y), strokeWidth = if (b.power) 11f else 7f, cap = StrokeCap.Round)
                    drawLine(c2, Offset(x - len * 0.4f, y), Offset(x, y), strokeWidth = if (b.power) 5f else 3f, cap = StrokeCap.Round)
                    drawCircle(c1.copy(alpha = 0.35f), if (b.power) 16f else 10f, Offset(x, y))
                }
            }
            // muzzle flash at the pilot
            if (muzzle > 0f) {
                val my = yRef.value / 100f * size.height; val mx = 0.2f * size.width
                if (artMuzzle != null) sprite(artMuzzle, mx + 56.dp.toPx(), my, 56.dp.toPx(), (muzzle / 0.12f).coerceIn(0f, 1f))
                else drawCircle(c2.copy(alpha = (muzzle / 0.12f).coerceIn(0f, 1f)), 14.dp.toPx(), Offset(mx + 24.dp.toPx(), my))
            }
            // boss shield bubble + damage flash
            if (mode == 2 && bossRef.value) {
                val bc = Offset(0.87f * size.width, bossYRef.value / 100f * size.height)
                val sh = BossGuard.shieldUp; val warn = BossGuard.shieldWarn
                if (sh || warn) {
                    val r = 58.dp.toPx(); val cy = Color(0xFF80D8FF)
                    val a = if (sh) 0.8f + 0.15f * sin(frame * 0.35f) else if ((frame / 4) % 2 == 0) 0.55f else 0.1f
                    drawCircle(Brush.radialGradient(listOf(cy.copy(alpha = 0.04f * a), cy.copy(alpha = 0.38f * a)), bc, r), r, bc)
                    drawCircle(cy.copy(alpha = a), r, bc, style = Stroke(3.dp.toPx()))
                    drawCircle(Color.White.copy(alpha = 0.5f * a), r - 6.dp.toPx(), bc, style = Stroke(1.dp.toPx()))
                }
                if (bossFlash > 0f) {
                    val k = (bossFlash / 0.2f).coerceIn(0f, 1f)
                    drawCircle(Color(0xFFFF5252).copy(alpha = 0.35f * k), 56.dp.toPx(), bc)
                    drawCircle(Color.White.copy(alpha = 0.7f * k), 34.dp.toPx(), bc)
                }
            }
            // shockwave rings
            for (rg in rings) {
                val k = (rg.age / 0.35f).coerceIn(0f, 1f)
                drawCircle(rg.col.copy(alpha = 0.9f * (1f - k)), (10f + (if (rg.big) 60f else 38f) * k).dp.toPx(), Offset(rg.x * size.width, rg.y / 100f * size.height), style = Stroke((4f * (1f - k) + 1f).dp.toPx()))
            }
            // sparks, trails and shards
            for (sp in sparks) {
                val a = (sp.life / sp.max).coerceIn(0f, 1f)
                val pos = Offset(sp.x * size.width, sp.y / 100f * size.height)
                if (sp.shard) {
                    val d = Offset(cos(sp.rot) * 6.dp.toPx(), sin(sp.rot) * 6.dp.toPx())
                    drawLine(sp.col.copy(alpha = a), pos - d, pos + d, 3.dp.toPx(), StrokeCap.Round)
                } else {
                    val tail = Offset(sp.vx * size.width * 0.05f, sp.vy / 100f * size.height * 0.05f)
                    drawLine(sp.col.copy(alpha = a), pos, pos - tail, 2.5f.dp.toPx(), StrokeCap.Round)
                    drawCircle(sp.col.copy(alpha = a * 0.6f), 2.dp.toPx(), pos)
                }
            }
            // impact explosions where a bolt destroyed something
            for (bm in booms) {
                val k = (bm.age / 0.36f).coerceIn(0f, 0.999f)
                val fi = (k * 4f).toInt()
                val ex = bm.x * size.width; val ey = bm.y / 100f * size.height
                val img = artBoom[fi]
                if (img != null) sprite(img, ex + 36.dp.toPx(), ey, 72.dp.toPx(), 1f - k * 0.5f)
                else drawCircle(c1.copy(alpha = 1f - k), (10f + 40f * k).dp.toPx(), Offset(ex, ey), style = Stroke(3.dp.toPx()))
            }
        }
        val hot = mode == 2 && Shooting.overheated
        val ring = if (hot) Color(0xFFFF5252) else Color(pack.argb)
        val left = if (mode == 2) null else Shooting.runShots + Shooting.ammo(ctx)
        if (banner.isNotEmpty()) {
            val shieldMsg = banner.startsWith("🛡")
            Text(banner, color = if (shieldMsg) Color(0xFF80D8FF) else if (banner.startsWith("⚠")) Color(0xFFFFD54F) else Color(0xFFFF5252),
                fontSize = 11.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 40.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xCC080620)).padding(horizontal = 10.dp, vertical = 4.dp))
        }
        Box(
            Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 16.dp).size(78.dp).clip(CircleShape)
                .background(Color(0xCC080620)).border(2.dp, ring.copy(alpha = if (left == 0) 0.3f else 0.9f), CircleShape)
                .pointerInput(mode) { detectTapGestures(onPress = { held = true; fire(); tryAwaitRelease(); held = false }) },
            contentAlignment = Alignment.Center
        ) {
            if (mode == 2) Canvas(Modifier.fillMaxSize().padding(4.dp)) { drawArc(ring, -90f, 360f * Shooting.heat, false, style = Stroke(7f, cap = StrokeCap.Round)) }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (hot) "🔥" else "⚡", fontSize = 22.sp)
                Text(if (hot) "COOL" else if (left != null) "$left" else "FIRE", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

/** Home: the Armory. Ammo packs (gems, never per bullet), bolt look packs, and what each difficulty allows. */
@Composable
fun ArmoryCard(isPro: Boolean, onBuyAmmo: (Int) -> Unit, onBolt: (String) -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    @Suppress("UNUSED_VARIABLE") val v = Shooting.version
    val ammo = Shooting.ammo(ctx)
    val sel = Shooting.selectedBolt(ctx)
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier.fillMaxWidth().clip(shape).background(Brush.verticalGradient(listOf(Color(0xFF14102E), Color(0xFF0B0820))))
            .border(2.dp, Color(sel.argb).copy(alpha = 0.6f), shape).padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val crate = rememberArt("ammo_crate")
            if (crate != null) androidx.compose.foundation.Image(androidx.compose.ui.graphics.painter.BitmapPainter(crate), contentDescription = null, modifier = Modifier.size(34.dp)) else Text("🔫", fontSize = 22.sp)
            Spacer(Modifier.width(8.dp))
            Text("ARMORY", color = Color(sel.argb), fontSize = 13.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
            Text("$ammo power shots", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        }
        Text(
            "Medium & Hard: ${if (isPro) Shooting.PRO_SHOTS else Shooting.FREE_SHOTS} free power shots every run, more from packs. " +
                (if (isPro) "Legendary: unlimited bolts with a heat meter that also hit the boss." else "👑 Pro on Legendary: unlimited bolts that also hit the boss."),
            color = Color.White.copy(alpha = 0.75f), fontSize = 10.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(vertical = 6.dp)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Shooting.AMMO.forEachIndexed { i, p ->
                Text("${p.shots} SHOTS\n${p.gems} 💎", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center, fontFamily = FontFamily.Monospace,
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(Brush.horizontalGradient(listOf(Color(0xFF3DE8FF), Color(0xFF69F0AE)))).clickable { onBuyAmmo(i) }.padding(vertical = 8.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Shooting.BOLTS.forEach { b ->
                val own = Shooting.ownsBolt(ctx, b.id); val on = sel.id == b.id
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = if (on) 0.14f else 0.06f))
                        .border(if (on) 2.dp else 1.dp, Color(b.argb).copy(alpha = if (on) 0.9f else 0.35f), RoundedCornerShape(10.dp))
                        .clickable { onBolt(b.id) }.padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Canvas(Modifier.size(width = 36.dp, height = 10.dp)) {
                        drawLine(Brush.horizontalGradient(listOf(Color.Transparent, Color(b.argb))), Offset(0f, size.height / 2), Offset(size.width, size.height / 2), strokeWidth = 7f, cap = StrokeCap.Round)
                        drawLine(Color(b.argb2), Offset(size.width * 0.5f, size.height / 2), Offset(size.width, size.height / 2), strokeWidth = 3f, cap = StrokeCap.Round)
                    }
                    Text(b.name.substringBefore(' '), color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    Text(if (on) "ON" else if (own) "OWNED" else "${b.gems}💎", color = Color(b.argb), fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}
