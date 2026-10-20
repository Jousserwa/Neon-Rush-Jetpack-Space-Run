package com.neonrush.game.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neonrush.game.R
import com.neonrush.game.RealmRules
import kotlinx.coroutines.delay
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * DARK AGES KNIGHT: the boss of the Dark Ages realm (zones 395, 400, 410, 415). Three pictures only (wing, body, sword), all in res/drawable-nodpi.
 * The two wings beat on their own pivots, the body bobs, the cape art stays with the body, the burning sword hovers at the hand.
 * His shots are the same sword flying along the lane (see drawKnightSword).
 */
private const val BODY_ASPECT = 422f / 420f
private val EMBER = Color(0xFFFF6A00)

fun DrawScope.drawKnightBoss(
    body: ImageBitmap, wing: ImageBitmap, sword: ImageBitmap,
    cx: Float, cy: Float, h: Float, t: Float, tick: Int, hp: Float, thr: Float = 1f
) {
    try {
        val rage = if (hp < 0.35f) 1f else 0f                       // the last third of his life: faster wings, hotter glow
        val bob = sin(t * 2.2f) * h * 0.03f
        val bh = h * 1.05f
        val bw = bh * BODY_ASPECT
        val swing = (1f - thr).coerceIn(0f, 1f)                     // 1 at the instant a sword is thrown, fades to 0
        val left = cx - bw * 0.5f - bw * 0.07f * swing
        val top = cy - bh * 0.5f + bob
        val glow = 0.30f + 0.10f * sin(t * 3f) + 0.15f * rage
        drawCircle(Brush.radialGradient(listOf(EMBER.copy(alpha = glow), Color.Transparent), center = Offset(cx, cy + bob), radius = h * 0.95f),
            radius = h * 0.95f, center = Offset(cx, cy + bob), blendMode = BlendMode.Plus)

        val beat = t * (5f + 3f * rage)
        val wh = bh * 0.714f
        val ww = wh * (wing.width.toFloat() / wing.height.toFloat())
        val ax = left + bw * 0.42f
        val ay = top + bh * 0.25f
        // far wing: behind everything, smaller and darker
        withTransform({ translate(ax - bw * 0.02f, ay); rotate(20f - 18f * sin(beat + 0.7f), Offset.Zero); scale(0.85f, 0.85f, Offset.Zero) }) {
            drawImage(wing, dstOffset = IntOffset((-0.9f * ww).roundToInt(), (-0.45f * wh).roundToInt()), dstSize = IntSize(ww.roundToInt(), wh.roundToInt()), alpha = 0.72f)
        }
        // near wing: mirrored so it spreads up and back behind the shoulder
        withTransform({ translate(ax, ay); rotate(-(28f + 24f * sin(beat)), Offset.Zero); scale(-1f, 1f, Offset.Zero) }) {
            drawImage(wing, dstOffset = IntOffset((-0.9f * ww).roundToInt(), (-0.45f * wh).roundToInt()), dstSize = IntSize(ww.roundToInt(), wh.roundToInt()))
        }
        drawImage(body, dstOffset = IntOffset(left.roundToInt(), top.roundToInt()), dstSize = IntSize(bw.roundToInt(), bh.roundToInt()))

        // the sword hovers just ahead of the open hand, tip toward the pilot. On a throw it whips forward and vanishes, then a new one ignites.
        val sw = h * 0.62f
        val sh = sw * (sword.height.toFloat() / sword.width.toFloat())
        val hx = left + bw * 0.10f
        val hy = top + bh * 0.42f
        val vis = if (thr < 0.25f) 0f else ((thr - 0.25f) / 0.75f).coerceIn(0f, 1f)
        val whip = if (thr < 0.25f) 1f - thr / 0.25f else 0f
        if (whip > 0f) {
            drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.8f * whip), EMBER.copy(alpha = 0.5f * whip), Color.Transparent), center = Offset(hx - sw * 0.5f, hy), radius = h * 0.5f),
                radius = h * 0.5f, center = Offset(hx - sw * 0.5f, hy), blendMode = BlendMode.Plus)
        }
        withTransform({ rotate(-8f + 5f * sin(t * 2.6f) - 48f * whip, Offset(hx, hy)); translate(-sw * 0.45f * whip, 0f) }) {
            drawImage(sword, dstOffset = IntOffset((hx - sw).roundToInt(), (hy - sh * 0.5f).roundToInt()), dstSize = IntSize(sw.roundToInt(), sh.roundToInt()),
                alpha = if (whip > 0f) 1f else vis)
        }
    } catch (_: Throwable) { }
}

/** The knight's shot: the flaming sword, tip first, flying left. [sizeMul] follows the normal boss-shot size rules. */
fun DrawScope.drawKnightSword(sword: ImageBitmap, x: Float, y: Float, ch: Float, t: Float, sizeMul: Float) {
    try {
        val w = ch * 0.17f * sizeMul
        val h = w * (sword.height.toFloat() / sword.width.toFloat())
        val flick = 0.85f + 0.15f * sin(t * 24f)
        // ember trail streaming back behind the blade
        for (k in 1..6) {
            val a = 0.32f * (1f - k / 7f) * flick
            val r = w * (0.20f - 0.02f * k)
            drawCircle(EMBER.copy(alpha = a), radius = r, center = Offset(x + w * (0.25f + 0.17f * k), y + sin(t * 14f + k) * h * 0.18f), blendMode = BlendMode.Plus)
        }
        drawCircle(Brush.radialGradient(listOf(EMBER.copy(alpha = 0.40f * flick), Color.Transparent), center = Offset(x, y), radius = w * 0.6f),
            radius = w * 0.6f, center = Offset(x, y), blendMode = BlendMode.Plus)
        // the blade tumbles slightly as it flies
        withTransform({ rotate(7f * sin(t * 16f + y * 0.01f), Offset(x, y)) }) {
            drawImage(sword, dstOffset = IntOffset((x - w * 0.5f).roundToInt(), (y - h * 0.5f).roundToInt()), dstSize = IntSize(w.roundToInt(), h.roundToInt()))
        }
    } catch (_: Throwable) { }
}

/**
 * BOSS INTRO CARD for the Dark Ages knight: when a knight boss appears, the full picture (darkages_knight.webp) slides in from the right
 * over a burning vignette with a warning title, holds for about 2.6 s, then fades. It never blocks touches (drawn over the play field only).
 */
private var knightIntroPlayed = false

@Composable
fun KnightIntroCard(zone: Int, bossActive: Boolean, modifier: Modifier = Modifier) {
    val active = bossActive && RealmRules.isKnightZone(zone)
    val slide = remember { Animatable(1f) }
    val fade = remember { Animatable(0f) }
    val pic = ImageBitmap.imageResource(id = R.drawable.darkages_knight)
    // keyed ONLY on "a knight boss is on screen": the card plays once when that turns true and is wiped the moment it turns false.
    // (Keying it on the zone as well let a mid-animation restart leave the card stuck on screen.)
    LaunchedEffect(active) {
        if (active && knightIntroPlayed) return@LaunchedEffect   // only the first knight of the session gets the big entrance
        if (active) {
            knightIntroPlayed = true
            slide.snapTo(1f); fade.snapTo(0f)
            fade.animateTo(1f, tween(200))
            slide.animateTo(0f, tween(480, easing = FastOutSlowInEasing))
            delay(1500)
            fade.animateTo(0f, tween(350))
        } else {
            fade.snapTo(0f)
        }
    }
    if (fade.value <= 0.01f) return
    Box(modifier = modifier.fillMaxSize().alpha(fade.value)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Color(0xFF3A0800).copy(alpha = 0.78f), Color(0xFF3A0800).copy(alpha = 0.78f), Color.Transparent)),
                topLeft = Offset(0f, size.height * 0.22f), size = androidx.compose.ui.geometry.Size(size.width, size.height * 0.46f))
        }
        Image(
            painter = BitmapPainter(pic),
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .size(210.dp)
                .graphicsLayer { translationX = slide.value * size.width * 1.4f }
        )
        Column(modifier = Modifier.align(Alignment.CenterStart).padding(start = 22.dp)) {
            Text("WARNING", color = Color(0xFFFF5252), style = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black, fontSize = 12.sp, letterSpacing = 3.sp))
            Text("THE EMBER KNIGHT", color = Color(0xFFFFB74D), style = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black, fontSize = 22.sp, letterSpacing = 1.sp))
            Text("He throws his burning sword. Slip the lane.", color = Color.White.copy(alpha = 0.85f), style = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 13.sp), modifier = Modifier.padding(top = 4.dp))
        }
    }
}
