package com.neonrush.game.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Wraps any avatar/pilot image so it REACTS to the same events the hulls use (HullFx stamps).
 *  gem -> happy hop + 😄, close call -> flinch + 😮, zone -> wave + 🚀, boss -> shake + 🔥, revive -> spin-in + ✨
 * [tick] = simState.tickIndex during a run; in menus pass a free-running tick (HangarPreview style).
 * Reads HullFx inside graphicsLayer so it does not recompose the content.
 */
@Composable
fun LivingAvatar(tick: Int, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val gem = tick - HullFx.gemTick
    val cc = tick - HullFx.closeCallTick
    val zone = tick - HullFx.zoneTick
    val boss = tick - HullFx.bossTick
    val rev = tick - HullFx.reviveTick
    val emote = when {
        rev in 0..12 -> "✨"
        boss in 0..14 -> "🔥"
        cc in 0..8 -> "😮"
        gem in 0..8 -> "😄"
        zone in 0..10 -> "🚀"
        else -> null
    }
    fun decay(age: Int, len: Int) = if (age in 0..len) exp(-3f * age / len) else 0f
    Box(modifier.graphicsLayer {
        val hop = decay(gem, 8) * sin(gem * 0.9f)
        translationY = -hop * 14f
        val flinch = decay(cc, 8)
        scaleX = 1f - 0.08f * flinch + 0.05f * decay(gem, 8)
        scaleY = 1f + 0.10f * flinch + 0.05f * decay(gem, 8)
        rotationZ = decay(boss, 14) * sin(boss * 2.2f) * 7f + decay(zone, 10) * sin(zone * 1.1f) * 9f
        if (rev in 0..12) rotationZ += (1f - rev / 12f) * 360f
        // idle breathing so the avatar is never dead
        val breath = sin((tick % 40) / 40f * 2f * PI.toFloat())
        translationY += breath * 2.5f
    }, contentAlignment = Alignment.Center) {
        content()
        if (emote != null) Box(Modifier.align(Alignment.TopEnd)) { Text(emote, fontSize = 18.sp) }
    }
}


/** Free-running tick (~10/s) for menus, where there is no simulation tick. */
@Composable
fun rememberMenuTick(): Int {
    val t = rememberInfiniteTransition(label = "menuTick")
    val v by t.animateFloat(0f, 6000f, infiniteRepeatable(tween(600000, easing = LinearEasing), RepeatMode.Restart), label = "mt")
    return v.toInt()
}
