package com.neonrush.game.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.Dp
import com.neonrush.game.R
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * The iconic pilot. ONE place that knows every pose, so a new pose is a single line here and then it can be used anywhere.
 * All six poses are in. A new one is a single line here.
 * Every pose is ALIVE wherever it shows up: it pops in with a spring, then idles in a way that matches the mood
 * (cheer hops, dizzy sways with circling stars, sad breathes and droops, boost hovers with a trail glow, thumb pulses and sparkles).
 */
enum class Pose(val res: Int, val periodMs: Int, val glow: Color) {
    CHEER(R.drawable.pilot_cheer, 900, Color(0xFFFF4DD2)),    // good news, something to claim, welcome
    DIZZY(R.drawable.pilot_dizzy, 1700, Color(0xFFFFD23F)),   // crashed / hit / system failure
    SAD(R.drawable.pilot_sad, 3400, Color(0xFF5C7CFF)),       // lost, locked, denied, nothing earned
    BOOST(R.drawable.pilot_boost, 1100, Color(0xFF00E5FF)),   // speed, power, energy
    THUMB(R.drawable.pilot_thumb, 1900, Color(0xFFFFD23F)),   // success, claimed, new best, victory
    RUN_RIBBON(R.drawable.pilot_run_ribbon, 520, Color(0xFF3DE8FF))   // go! ready, race, launch, journey
}

@Composable
fun PilotPose(pose: Pose, height: Dp, modifier: Modifier = Modifier, animated: Boolean = true) {
    val img: ImageBitmap = ImageBitmap.imageResource(id = pose.res)
    if (!animated) {
        Image(painter = BitmapPainter(img), contentDescription = null, contentScale = ContentScale.Fit, modifier = modifier.height(height))
        return
    }
    val inf = rememberInfiniteTransition(label = "pilotIdle")
    val t by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(pose.periodMs, easing = LinearEasing), RepeatMode.Restart), label = "t")
    val shineT by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(3600, easing = LinearEasing), RepeatMode.Restart), label = "shine")
    val orbit by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(6000, easing = LinearEasing), RepeatMode.Restart), label = "orbit")
    val pop = remember(pose) { Animatable(0.55f) }
    val burst = remember(pose) { Animatable(0f) }
    LaunchedEffect(pose) { pop.snapTo(0.55f); pop.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMedium)) }
    LaunchedEffect(pose) { burst.snapTo(0f); burst.animateTo(1f, tween(750)) }
    val tau = 6.2831855f
    Image(
        painter = BitmapPainter(img), contentDescription = null, contentScale = ContentScale.Fit,
        modifier = modifier.height(height)
            .drawBehind {
                // steady stage under the pilot: mood glow, ground shadow, orbiting lights, and a ring that bursts out on arrival
                val w = size.width; val h = size.height
                val c = Offset(w / 2f, h * 0.5f)
                val pulse = 0.5f + 0.5f * sin(t * tau)
                drawCircle(Brush.radialGradient(listOf(pose.glow.copy(alpha = 0.18f + 0.12f * pulse), Color.Transparent), center = c, radius = h * 0.66f), radius = h * 0.66f, center = c, blendMode = BlendMode.Plus)
                val lift = when (pose) { Pose.CHEER -> abs(sin(t * 3.1415927f)); Pose.RUN_RIBBON -> abs(sin(t * tau)); Pose.BOOST -> 0.5f + 0.5f * sin(t * tau); else -> 0f }
                val sw = w * (0.46f - 0.12f * lift)
                drawOval(Color.Black.copy(alpha = 0.34f - 0.14f * lift), topLeft = Offset(w / 2f - sw / 2f, h * 0.965f), size = androidx.compose.ui.geometry.Size(sw, h * 0.05f))
                for (k in 0 until 5) {
                    val a = (orbit + k * 0.2f) * tau
                    val o = Offset(w * 0.5f + cos(a) * w * 0.52f, h * 0.52f + sin(a) * h * 0.20f * (if (sin(a) > 0f) 1f else 0.6f))
                    val tw = 0.5f + 0.5f * sin(a * 2f + t * tau)
                    drawCircle(pose.glow.copy(alpha = 0.30f + 0.5f * tw), radius = h * 0.011f * (0.6f + tw), center = o, blendMode = BlendMode.Plus)
                }
                val b = burst.value
                if (b < 1f) drawCircle(pose.glow.copy(alpha = (1f - b) * 0.65f), radius = h * (0.25f + 0.55f * b), center = c, style = androidx.compose.ui.graphics.drawscope.Stroke(width = h * 0.03f * (1f - b) + 1f))
            }
            .graphicsLayer {
                compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen
                val s = sin(t * tau); val h = size.height
                var dx = 0f; var dy = 0f; var rot = 0f; var sx = 1f; var sy = 1f
                when (pose) {
                    Pose.CHEER -> { val hop = abs(sin(t * 3.1415927f)); dy = -hop * h * 0.07f; rot = sin(t * tau * 2f) * 3.5f; sy = 1f + 0.03f * (1f - hop); sx = 1f - 0.02f * (1f - hop) }
                    Pose.DIZZY -> { rot = s * 6f; dx = s * h * 0.025f; dy = abs(cos(t * tau)) * h * 0.008f }
                    Pose.SAD -> { sy = 1f + 0.014f * s; sx = 1f - 0.006f * s; rot = sin(t * tau * 0.5f) * 1.4f; dy = h * 0.006f * s }
                    Pose.BOOST -> { dy = s * h * 0.035f; dx = sin(t * tau * 7f) * h * 0.004f; rot = s * 2.2f }
                    Pose.THUMB -> { val k = 1f + 0.035f * (0.5f + 0.5f * s); sx = k; sy = k; rot = sin(t * tau) * 1.8f }
                    Pose.RUN_RIBBON -> { dy = -abs(sin(t * tau)) * h * 0.035f; dx = s * h * 0.012f; rot = 3f + s * 2.2f; sy = 1f + 0.015f * s }
                }
                val p = pop.value
                transformOrigin = TransformOrigin(0.5f, 1f)
                translationX = dx; translationY = dy; rotationZ = rot; scaleX = sx * p; scaleY = sy * p
                alpha = (p * 1.4f - 0.2f).coerceIn(0f, 1f)
            }
            .drawWithContent {
                val w = size.width; val h = size.height
                drawContent()
                // a glint sweeps across the pilot every few seconds (only lands on the pilot's own pixels)
                if (shineT < 0.30f) {
                    val x = (shineT / 0.30f) * w * 1.7f - w * 0.35f
                    drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.55f), Color.Transparent), startX = x - w * 0.12f, endX = x + w * 0.12f), blendMode = BlendMode.SrcAtop)
                }
                when (pose) {
                    Pose.DIZZY -> for (i in 0 until 3) {            // stars circling the head
                        val a = t * tau + i * 2.0944f
                        val sc = Offset(w * 0.5f + cos(a) * w * 0.30f, h * 0.16f + sin(a) * h * 0.05f)
                        val r = h * 0.035f
                        drawLine(Color(0xFFFFF59D), Offset(sc.x - r, sc.y), Offset(sc.x + r, sc.y), strokeWidth = r * 0.5f, cap = StrokeCap.Round)
                        drawLine(Color(0xFFFFF59D), Offset(sc.x, sc.y - r), Offset(sc.x, sc.y + r), strokeWidth = r * 0.5f, cap = StrokeCap.Round)
                    }
                    Pose.CHEER, Pose.THUMB, Pose.BOOST -> for (i in 0 until 6) {   // twinkling sparkles
                        val ph = (t + i * 0.1667f) % 1f
                        val a = i * 1.7f + 0.6f
                        val sc = Offset(w * (0.5f + 0.44f * cos(a)), h * (0.45f + 0.40f * sin(a * 1.3f)))
                        val r = h * 0.05f * sin(ph * 3.1415927f)
                        val col = if (pose == Pose.BOOST) Color(0xFFB3F5FF) else Color(0xFFFFF3B0)
                        drawLine(col, Offset(sc.x - r, sc.y), Offset(sc.x + r, sc.y), strokeWidth = 2.5f, cap = StrokeCap.Round, blendMode = BlendMode.Plus)
                        drawLine(col, Offset(sc.x, sc.y - r), Offset(sc.x, sc.y + r), strokeWidth = 2.5f, cap = StrokeCap.Round, blendMode = BlendMode.Plus)
                        drawLine(col.copy(alpha = 0.6f), Offset(sc.x - r * 0.6f, sc.y - r * 0.6f), Offset(sc.x + r * 0.6f, sc.y + r * 0.6f), strokeWidth = 1.5f, cap = StrokeCap.Round, blendMode = BlendMode.Plus)
                    }
                    Pose.RUN_RIBBON -> for (i in 0 until 6) {         // speed lines streaming behind the runner
                        val ph = (t * 2f + i * 0.1667f) % 1f
                        val y = h * (0.35f + 0.5f * (((i * 37) % 100) / 100f))
                        val x0 = w * (0.30f - 0.28f * ph)
                        drawLine(Color(0xFFB3F5FF).copy(alpha = (1f - ph) * 0.8f), Offset(x0, y), Offset(x0 - w * 0.16f, y), strokeWidth = 2.5f, cap = StrokeCap.Round, blendMode = BlendMode.Plus)
                    }
                    Pose.SAD -> {                                    // a slow falling drop
                        val ph = t
                        drawCircle(Color(0xFF9FD8FF).copy(alpha = (1f - ph) * 0.8f), radius = h * 0.012f, center = Offset(w * 0.56f, h * (0.17f + 0.2f * ph)))
                    }
                }
            }
    )
}

/** Picks a pose for a short message (used by the toast). null = no pilot. */
fun poseForMessage(text: String): Pose? {
    val t = text.lowercase()
    val bad = listOf("not enough", "failed", "can't", "cannot", "no ad", "unavailable", "error", "locked", "need ", "try again", "not ready", "no internet", "offline", "too late", "expired")
    val go = listOf("let's go", "get ready", "launch", "go!", "race", "starting")
    val good = listOf("claimed", "reward", "earned", "unlocked", "welcome", "thank", "success", "purchased", "restored", "equipped", "complete", "collected", "+")
    return when {
        bad.any { t.contains(it) } -> Pose.SAD
        go.any { t.contains(it) } -> Pose.RUN_RIBBON
        good.any { t.contains(it) } -> Pose.THUMB
        else -> null
    }
}
