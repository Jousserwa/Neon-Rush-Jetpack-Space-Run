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
import com.neonrush.game.BossBrood
import com.neonrush.game.BossGuard
import com.neonrush.game.BossLunge
import com.neonrush.game.Fleet
import com.neonrush.game.MagazineBay
import com.neonrush.game.NeonSoundEngine
import com.neonrush.game.Shooting
import com.neonrush.game.Upgrades
import com.neonrush.game.VisualTrackElement
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private class Bolt(var x: Float, val y: Float, val armor: Boolean)
private class Boom(val x: Float, val y: Float, var age: Float = 0f)
private class Spark(var x: Float, var y: Float, var vx: Float, var vy: Float, var life: Float, val max: Float, val col: Color, val shard: Boolean = false, var rot: Float = 0f)
private class Ring(val x: Float, val y: Float, val col: Color, val big: Boolean, var age: Float = 0f)

/**
 * In-race shooting. The FIRE button fires ARMOR ROUNDS first (collected from armor magazines, kept in the gun) and then the run's basic bolts.
 * Basic bolts kill one target; an armor round that touches a boss-brood baby chain-destroys every baby (BossBrood.novaRequested).
 * Legendary bolts chip the boss through [onBossHit] (armor rounds x1.5, see BossGuard); Medium/Hard bolts clear obstacles and babies only.
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
    var reloadFx by remember { mutableStateOf(0f) }
    val sfx = remember { NeonSoundEngine() }
    val packRef = rememberUpdatedState(pack)
    var muzzle by remember { mutableStateOf(0f) }
    val artM = rememberArt("shot_${pack.art}_m"); val artL = rememberArt("shot_${pack.art}_l")
    val artMuzzle = rememberArt("shot_muzzle")
    val artBoom = listOf(rememberArt("shot_boom1"), rememberArt("shot_boom2"), rememberArt("shot_boom3"), rememberArt("shot_boom4"))
    val gold = Color(0xFFFFC21A); val goldHot = Color(0xFFFFF3B0)
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

    fun fire() {
        if (cooldown > 0f) return
        val kind = Shooting.consume(ctx, mode)
        if (kind == 0) { cooldown = 0.3f; sfx.playDryClick(); sfx.playOverheat(); return }
        val armor = kind == 2
        val gap = if (mode == 2) (if (armor) Shooting.FIRE_GAP_LEGEND else 0.28f) else Shooting.FIRE_GAP_POWER
        cooldown = gap * (if (com.neonrush.game.LegendMoves.overdriveOn()) 0.5f else 1f)
        bolts.add(Bolt(com.neonrush.game.PilotDrift.x + 0.05f, yRef.value.toFloat(), armor))
        muzzle = 0.12f
        sfx.playShot(armor)
        if (bossRef.value) BossGuard.noteShot()
        burst(com.neonrush.game.PilotDrift.x + 0.04f, yRef.value.toFloat(), if (armor) goldHot else Color(packRef.value.argb2), if (armor) 6 else 4, 0.25f, 0)
    }

    LaunchedEffect(active) {
        Upgrades.refresh(ctx)
        if (!active) { bolts.clear(); sparks.clear(); rings.clear(); BossGuard.incomingY = -1f; return@LaunchedEffect }
        var last = 0L
        var seenReload = Shooting.reloadMs
        var prevShield = false; var prevEn = 0
        while (true) {
            val now = withFrameNanos { it }
            val dt = if (last == 0L) 0.016f else ((now - last) / 1_000_000_000f).coerceAtMost(0.05f)
            last = now
            if (com.neonrush.game.Pause.frozen()) { frame++; continue }   // paused: no firing, bolts hang
            if (cooldown > 0f) cooldown -= dt
            if (muzzle > 0f) muzzle -= dt
            if (bossFlash > 0f) bossFlash -= dt
            if (reloadFx > 0f) reloadFx -= dt
            if (Shooting.reloadMs != seenReload) { seenReload = Shooting.reloadMs; reloadFx = 1.4f; sfx.playMagSwap() }
            run {
                val bi = booms.iterator(); while (bi.hasNext()) { val bm = bi.next(); bm.age += dt; if (bm.age > 0.36f) bi.remove() }
                val si = sparks.iterator()
                while (si.hasNext()) { val sp = si.next(); sp.life -= dt; sp.x += sp.vx * dt; sp.y += sp.vy * dt; sp.vx *= 0.93f; sp.vy *= 0.93f; sp.rot += dt * 14f; if (sp.life <= 0f) si.remove() }
                val ri = rings.iterator()
                while (ri.hasNext()) { val rg = ri.next(); rg.age += dt; if (rg.age > 0.35f) ri.remove() }
            }
            // boss counter-measures feedback (shield / enrage): sounds + banner, only for pilots who can hurt the boss
            run {
                val on = mode >= 1 && bossRef.value
                val su = on && BossGuard.shieldUp
                if (su && !prevShield) sfx.playShieldUp()
                prevShield = su
                val en = if (on) BossGuard.enrage else 0
                if (en > prevEn) sfx.playEnrage()
                prevEn = en
                val nb = if (!on) "" else if (Fleet.alive() > 0) "🛡 ESCORT SHIELD - DESTROY THE WINGMEN" else if (BossLunge.vulnerable) "💥 BOSS EXPOSED - FIRE! x2" else if (su) "🛡 SHIELD UP - HOLD FIRE" else if (BossGuard.shieldWarn) "⚠ SHIELD CHARGING" else if (en == 2) "🔥 BOSS FURIOUS" else if (en == 1) "🔥 BOSS ENRAGED" else ""
                if (nb != banner) banner = nb
            }
            var incoming = -1f
            val bossHitX = 0.72f + BossLunge.shift.coerceAtMost(0f) + Fleet.dx
            val iter = bolts.iterator()
            while (iter.hasNext()) {
                val b = iter.next()
                val from = b.x
                b.x += dt * (if (b.armor) 1.9f else 1.6f)
                val c1 = if (b.armor) gold else Color(packRef.value.argb); val c2 = if (b.armor) goldHot else Color(packRef.value.argb2)
                if (sparks.size < 220) sparks.add(Spark(b.x - 0.025f, b.y + (Random.nextFloat() - 0.5f) * 3f, -0.12f, (Random.nextFloat() - 0.5f) * 16f, if (b.armor) 0.3f else 0.22f, if (b.armor) 0.3f else 0.22f, if (Random.nextBoolean()) c1 else c2))
                if (mode >= 1 && bossRef.value && b.x > bossHitX - 0.44f && b.x < bossHitX + 0.13f) incoming = b.y
                var used = false
                // boss-brood babies: a basic bolt kills one, an armor round sets off the chain blast
                for (bb in BossBrood.babies) {
                    if (bb.hp <= 0 || bb.age < 0.4f) continue
                    if (bb.x >= from - 0.04f && bb.x <= b.x + 0.04f && abs(bb.y - b.y) <= 9f) {
                        if (b.armor) { BossBrood.novaRequested = true; BossBrood.novaX = bb.x; BossBrood.novaY = bb.y } else bb.hp = 0
                        burst(bb.x, bb.y, c1, 10, 0.45f, 4); rings.add(Ring(bb.x, bb.y, c2, false)); sfx.playSmash(); used = true; break
                    }
                }
                if (!used) for (e in elRef.value) {
                    if (!Shooting.canShoot(e.type, e.subType)) continue
                    if (e.xOffsetFraction >= from - 0.04f && e.xOffsetFraction <= b.x + 0.04f && abs(e.yMatchPos - b.y) <= 8f) {
                        destroyRef.value(e.id); booms.add(Boom(e.xOffsetFraction, e.yMatchPos.toFloat())); used = true
                        sfx.playSmash(); burst(e.xOffsetFraction, e.yMatchPos.toFloat(), c1, if (b.armor) 16 else 12, 0.45f, 6); rings.add(Ring(e.xOffsetFraction, e.yMatchPos.toFloat(), c2, b.armor)); break
                    }
                }
                if (!used && mode >= 1 && bossRef.value) { when (Fleet.hitBolt(from, b.x, b.y, b.armor)) { 1 -> { used = true; sfx.playBossHit(0.3f); burst(b.x, b.y, if (b.armor) goldHot else Color(0xFFFFB74D), 8, 0.45f, 3); rings.add(Ring(b.x, b.y, c2, false)) }; 2 -> { used = true; sfx.playDeflect(); burst(b.x, b.y, Color(0xFFB3E5FC), 8, 0.5f, 0) } } }
                if (!used && mode >= 1 && bossRef.value && b.x > bossHitX && abs(bossYRef.value - b.y) <= 14f) {
                    Shooting.lastArmor = b.armor
                    hitRef.value(); used = true
                    val bx = 0.88f + BossLunge.shift + Fleet.dx
                    if (BossGuard.lastDeflect) {
                        sfx.playDeflect(); burst(bx - 0.04f, b.y, Color(0xFFB3E5FC), 9, 0.5f, 0); rings.add(Ring(bx, bossYRef.value.toFloat(), Color(0xFF80D8FF), false))
                    } else {
                        sfx.playBossHit(BossGuard.hitStreak / 8f); bossFlash = 0.2f; burst(bx - 0.02f, b.y, if (b.armor) goldHot else Color(0xFFFFB74D), if (b.armor) 14 else 10, 0.5f, 3); rings.add(Ring(bx, bossYRef.value.toFloat(), if (b.armor) gold else Color(0xFFFF5252), b.armor))
                    }
                }
                if (used || b.x > 1.1f) iter.remove()
            }
            if (held && cooldown <= 0f) fire()
            BossGuard.incomingY = incoming
            frame++
        }
    }

    Box(modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            @Suppress("UNUSED_VARIABLE") val f = frame
            fun sprite(img: ImageBitmap, rightX: Float, cy: Float, wPx: Float, alpha: Float = 1f) {
                val hPx = wPx * img.height / img.width
                drawImage(img, dstOffset = IntOffset((rightX - wPx).toInt(), (cy - hPx / 2f).toInt()), dstSize = IntSize(wPx.toInt(), hPx.toInt()), alpha = alpha)
            }
            for (b in bolts) {
                val x = b.x * size.width; val y = b.y / 100f * size.height
                val c1 = if (b.armor) gold else Color(pack.argb); val c2 = if (b.armor) goldHot else Color(pack.argb2)
                if (b.armor) {
                    // armor round: long golden comet with a hot core and a halo
                    val len = 120.dp.toPx()
                    drawLine(Brush.horizontalGradient(listOf(Color.Transparent, gold.copy(alpha = 0.7f)), startX = x - len, endX = x), Offset(x - len, y), Offset(x, y), 10.dp.toPx(), StrokeCap.Round)
                    drawCircle(gold.copy(alpha = 0.28f), 16.dp.toPx(), Offset(x, y))
                }
                val img = if (b.armor) artL else artM
                if (img != null) sprite(img, x + 10.dp.toPx(), y, (if (b.armor) 124.dp else 88.dp).toPx())
                else {
                    val len = if (b.armor) 64f else 44f
                    drawLine(Brush.horizontalGradient(listOf(Color.Transparent, c1), startX = x - len, endX = x), Offset(x - len, y), Offset(x, y), strokeWidth = if (b.armor) 11f else 7f, cap = StrokeCap.Round)
                    drawLine(c2, Offset(x - len * 0.4f, y), Offset(x, y), strokeWidth = if (b.armor) 5f else 3f, cap = StrokeCap.Round)
                    drawCircle(c1.copy(alpha = 0.35f), if (b.armor) 16f else 10f, Offset(x, y))
                }
            }
            // muzzle flash at the pilot
            if (muzzle > 0f) {
                val my = yRef.value / 100f * size.height; val mx = com.neonrush.game.PilotDrift.x * size.width
                if (artMuzzle != null) sprite(artMuzzle, mx + 56.dp.toPx(), my, 56.dp.toPx(), (muzzle / 0.12f).coerceIn(0f, 1f))
                else drawCircle(Color(pack.argb2).copy(alpha = (muzzle / 0.12f).coerceIn(0f, 1f)), 14.dp.toPx(), Offset(mx + 24.dp.toPx(), my))
            }
            // boss shield bubble + damage flash
            if (mode >= 1 && bossRef.value) {
                val bc = Offset((0.88f + BossLunge.shift + Fleet.dx) * size.width, bossYRef.value / 100f * size.height)
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
            for (rg in rings) {
                val k = (rg.age / 0.35f).coerceIn(0f, 1f)
                drawCircle(rg.col.copy(alpha = 0.9f * (1f - k)), (10f + (if (rg.big) 60f else 38f) * k).dp.toPx(), Offset(rg.x * size.width, rg.y / 100f * size.height), style = Stroke((4f * (1f - k) + 1f).dp.toPx()))
            }
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
                else drawCircle(Color(pack.argb).copy(alpha = 1f - k), (10f + 40f * k).dp.toPx(), Offset(ex, ey), style = Stroke(3.dp.toPx()))
            }
        }
        if (banner.isNotEmpty()) {
            val expose = banner.startsWith("💥")
            Text(banner, color = if (expose) Color(0xFFFFD54F) else if (banner.startsWith("🛡")) Color(0xFF80D8FF) else if (banner.startsWith("⚠")) Color(0xFFFFD54F) else Color(0xFFFF5252),
                fontSize = 11.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 40.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xCC080620)).padding(horizontal = 10.dp, vertical = 4.dp))
        }
        if (reloadFx > 0f) {
            val a = (reloadFx / 1.4f).coerceIn(0f, 1f)
            Text("🔄 MAGAZINE SWAP  (${Shooting.ammo(ctx) / MagazineBay.MAG_SIZE + 1} mags left)", color = Color(0xFFFFE082).copy(alpha = a), fontSize = 11.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace,
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 100.dp, bottom = 40.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xCC080620)).padding(horizontal = 8.dp, vertical = 4.dp))
        }
        val armorN = Shooting.ammo(ctx)
        val total = armorN + Shooting.runShots
        val ring = if (armorN > 0) gold else Color(pack.argb)
        val magFill = if (armorN <= 0) (if (Shooting.runShots > 0) 1f else 0f) else ((armorN - 1) % MagazineBay.MAG_SIZE + 1) / MagazineBay.MAG_SIZE.toFloat()   // the magazine being fired
        GameButton(
            BtnKind.FIRE, 70.dp, ring, if (total == 0) "EMPTY" else "FIRE", if (total == 0) "" else "$total", magFill.coerceIn(0f, 1f), total > 0, held,
            { held = true; fire() }, { held = false }, extra = magFill.coerceIn(0f, 1f),
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 14.dp, bottom = 3.dp)
        )
    }
}

/** Home: the Armory. Ammo packs (gems, never per bullet), bolt look packs, and what each difficulty allows. */
@Composable
fun ArmoryCard(isPro: Boolean, onBuyAmmo: (Int) -> Unit, onBolt: (String) -> Unit, onOpenBay: () -> Unit = {}, modifier: Modifier = Modifier) {
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
            Text("$ammo / ${MagazineBay.capacity(ctx, isPro)} shots", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        }
        Text(
            "Collect ARMOR magazines in the race: each armor gives ${MagazineBay.PER_ARMOR} shots (5 armors = 1 magazine of ${MagazineBay.MAG_SIZE}) that stay in your gun until fired. Armor shots hit harder and one blast wipes out a whole boss brood. The gun fires one magazine at a time and swaps to the next by itself. Buy more storage in the Magazine Bay. Gem refills: up to ${Shooting.GEM_ARMOR_PER_DAY} armors a day (${Shooting.gemArmorToday(ctx)} used).",
            color = Color.White.copy(alpha = 0.75f), fontSize = 10.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(vertical = 6.dp)
        )
        Row(
            Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(RoundedCornerShape(12.dp)).background(Brush.horizontalGradient(listOf(Color(0xFFFFC21A), Color(0xFFFF8F00)))).clickable { onOpenBay() }.padding(vertical = 10.dp, horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("🔫", fontSize = 18.sp)
            Spacer(Modifier.width(8.dp))
            Text("MAGAZINE BAY", color = Color.Black, fontSize = 13.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
            Text("${MagazineBay.magazines(ctx, isPro)} mags ▸", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Shooting.AMMO.forEachIndexed { i, p ->
                Text("${p.label}\n${p.gems} 💎", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center, fontFamily = FontFamily.Monospace,
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
