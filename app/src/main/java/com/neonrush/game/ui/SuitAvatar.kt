package com.neonrush.game.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.Dp
import com.neonrush.game.R

/**
 * The pilot exactly as equipped (suit colours/pattern + equipped parts), for every avatar spot (header, in-run popup,
 * game over, pilot card, suit list). Pass [tick] >= 0 to drive the animation yourself; the default (-1) self-animates.
 * [partsOverride] lets the wardrobe preview a loadout before it is bought or equipped.
 */
@Composable
fun SuitAvatar(suitId: String, box: Dp, tick: Int = -1, modifier: Modifier = Modifier, partsOverride: Map<String, String>? = null) {
    val bmp = ImageBitmap.imageResource(id = R.drawable.pilot_run_1)
    val clock = rememberInfiniteTransition(label = "avatarClock").animateFloat(
        0f, 600f, infiniteRepeatable(tween(600_000, easing = LinearEasing), RepeatMode.Restart), label = "ac"
    )
    Canvas(modifier.size(box)) {
        val t = if (tick >= 0) tick else (clock.value * 20f).toInt()
        val h = this.size.height * 0.74f
        val w = h * bmp.width.toFloat() / bmp.height.toFloat()
        drawPilotLook(suitId, bmp, 0, this.size.width * 0.5f, this.size.height * 0.56f, w, h, t, partsOverride)
    }
}
