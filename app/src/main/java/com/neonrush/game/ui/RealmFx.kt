package com.neonrush.game.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.neonrush.game.RealmRules
import com.neonrush.game.VisualTrackElement
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** Drawing for the zone 240+ realms and the Echo Rival. Names start with "rl" to stay unique in the package. */

private val RL_CYAN = Color(0xFF00E5FF)
private val RL_MAG = Color(0xFFFF2BD6)

fun rlSideColor(side: Int): Color = if (side == 0) RL_CYAN else RL_MAG

/**
 * MIRROR: the screen is washed in your side's colour; a flip throws a ripple across it. Obstacles get a ring in their side colour:
 * a bright pulsing ring when they are live for you, a faint dashed one when you phase through them.
 */
fun DrawScope.drawMirrorRealm(cw: Float, ch: Float, t: Float, tick: Int, side: Int, flipTick: Int, pilot: Offset, elems: List<VisualTrackElement>) {
    val my = rlSideColor(side)
    // side colour at the edges of the screen
    drawRect(Brush.horizontalGradient(listOf(my.copy(alpha = 0.16f), Color.Transparent, my.copy(alpha = 0.10f))), size = Size(cw, ch), blendMode = BlendMode.Plus)
    drawRect(Brush.verticalGradient(listOf(my.copy(alpha = 0.12f), Color.Transparent, Color.Transparent, my.copy(alpha = 0.12f))), size = Size(cw, ch), blendMode = BlendMode.Plus)
    // the mirror seam: a shimmering line on the opposite side's colour
    val other = rlSideColor(1 - side)
    for (k in 0 until 6) {
        val y = ch * ((k + ((t * 0.25f) % 1f)) / 6f)
        drawLine(other.copy(alpha = 0.10f), Offset(0f, y), Offset(cw, y), strokeWidth = ch * 0.002f)
    }
    // flip ripple
    val age = tick - flipTick
    if (age in 0..9) {
        val f = age / 9f
        drawCircle(my.copy(alpha = 0.7f * (1f - f)), radius = cw * (0.1f + 1.2f * f), center = pilot, style = Stroke(width = ch * 0.012f * (1f - f)))
        drawRect(Color.White.copy(alpha = 0.18f * (1f - f)), size = Size(cw, ch), blendMode = BlendMode.Plus)
    }
    // pilot halo in the side colour
    fxGlow(pilot, ch * 0.16f, my, 0.35f + 0.1f * sin(t * 5f))
    drawCircle(my.copy(alpha = 0.8f), radius = ch * 0.075f, center = pilot, style = Stroke(width = ch * 0.004f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(ch * 0.02f, ch * 0.014f), t * 40f)))
    // obstacle rings
    for (e in elems) {
        if (e.type != "obstacle" || e.xOffsetFraction > 1.15f || e.xOffsetFraction < -0.05f) continue
        val s = RealmRules.sideOf(e)
        val c = rlSideColor(s)
        val p = Offset(cw * e.xOffsetFraction, ch * (e.yMatchPos / 100f))
        val r = ch * 0.07f
        if (s == side) {
            val pulse = 0.6f + 0.4f * sin(t * 8f + e.id.hashCode())
            drawCircle(c.copy(alpha = 0.85f * pulse), radius = r, center = p, style = Stroke(width = ch * 0.006f))
            fxGlow(p, r * 1.4f, c, 0.28f)
        } else {
            drawCircle(c.copy(alpha = 0.35f), radius = r, center = p, style = Stroke(width = ch * 0.003f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(ch * 0.016f, ch * 0.016f), t * 30f)))
        }
    }
}

/** PULSE: every beat the screen breathes: rings from the pilot, edge flash, and a bar counter (4 beats, the last one is the drop). */
fun DrawScope.drawPulseRealm(cw: Float, ch: Float, t: Float, tick: Int, pilot: Offset) {
    val beatLen = RealmRules.PULSE_BEAT
    val phase = (tick % beatLen) / beatLen.toFloat()
    val beat = (tick / beatLen) % 4
    val hit = (1f - phase) * (1f - phase)
    val col = if (beat == 3) Color(0xFFFFD54F) else Color(0xFF7C4DFF)
    drawRect(Brush.radialGradient(listOf(Color.Transparent, col.copy(alpha = 0.22f * hit)), center = Offset(cw / 2f, ch / 2f), radius = ch * 0.9f), size = Size(cw, ch), blendMode = BlendMode.Plus)
    drawCircle(col.copy(alpha = 0.6f * (1f - phase)), radius = ch * (0.06f + 0.5f * phase), center = pilot, style = Stroke(width = ch * 0.006f * (1f - phase)))
    // horizontal beat lines streaming through
    for (k in 0 until 5) {
        val y = ch * (0.15f + 0.17f * k)
        val w = cw * (0.3f + 0.7f * hit)
        drawLine(col.copy(alpha = 0.18f * hit + 0.04f), Offset(cw / 2f - w / 2f, y), Offset(cw / 2f + w / 2f, y), strokeWidth = ch * 0.002f, cap = StrokeCap.Round, blendMode = BlendMode.Plus)
    }
    // bar counter: four dots, the fourth is gold (the drop)
    for (k in 0 until 4) {
        val on = k <= beat
        val c = if (k == 3) Color(0xFFFFD54F) else Color(0xFF7C4DFF)
        val p = Offset(cw / 2f + (k - 1.5f) * ch * 0.035f, ch * 0.075f)
        drawCircle(c.copy(alpha = if (on) 0.95f else 0.25f), radius = ch * (if (k == beat) 0.012f + 0.006f * hit else 0.008f), center = p)
    }
}

/**
 * ECHO RIVAL: the pilot's own sprite, turned to face you, in violet shadow. A trail of faint copies shows the lanes it has been in
 * (the lanes you were in), so the shots it fires are readable.
 */
fun DrawScope.drawEchoRival(img: ImageBitmap, bossX: Float, bossY: Float, h: Float, t: Float, tick: Int, hp: Float, ch: Float) {
    val w = h * (img.width.toFloat() / img.height.toFloat())
    val vio = Color(0xFF7C4DFF)
    fxGlow(Offset(bossX, bossY), h * 0.9f, vio, 0.28f + 0.1f * sin(t * 3f))
    // trail of earlier lanes (what you did)
    for (k in 1..6) {
        val ty = ch * (RealmRules.echoY(tick - k * 3) / 100f)
        drawSprite(img, Offset(bossX + k * h * 0.12f, ty), w * 0.8f, h * 0.8f, vio, 0.10f)
    }
    // the rival itself, flipped to face left, jittering when it is hurt
    val jit = if (hp < 0.3f) sin(t * 47f) * h * 0.01f else 0f
    withTransform({ scale(-1f, 1f, Offset(bossX, bossY)) }) {
        drawSprite(img, Offset(bossX + jit, bossY), w, h, vio, 0.9f)
    }
    // glowing eyes line and a crackle of glitch bars
    val step = (t * 9f).toInt()
    for (k in 0 until 4) if (fxRnd(step, k) > 0.5f) {
        val y = bossY - h * 0.45f + fxRnd(step, k + 5) * h * 0.9f
        drawRect(vio.copy(alpha = 0.5f), Offset(bossX - w * 0.4f + fxRnd(k, step) * w * 0.3f, y), Size(w * 0.5f, h * 0.012f), blendMode = BlendMode.Plus)
    }
    fxSpark(Offset(bossX - w * 0.05f, bossY - h * 0.33f), h * 0.07f, Color.White, 0.7f + 0.3f * sin(t * 6f))
}

private fun DrawScope.drawSprite(img: ImageBitmap, c: Offset, w: Float, h: Float, tint: Color, alpha: Float) {
    drawImage(
        image = img,
        dstOffset = IntOffset((c.x - w / 2f).roundToInt(), (c.y - h / 2f).roundToInt()),
        dstSize = IntSize(w.roundToInt(), h.roundToInt()),
        alpha = alpha,
        colorFilter = ColorFilter.tint(tint, BlendMode.SrcIn)
    )
}

/** Small caption for the realm you are in. */
fun realmCaption(zone: Int): String? = when (RealmRules.realmFor(zone)) {
    com.neonrush.game.Realm.MIRROR -> "MIRROR REALM  ·  TAP TO SWAP SIDE"
    com.neonrush.game.Realm.PULSE -> "PULSE REALM  ·  MOVE ON THE BEAT"
    else -> null
}
