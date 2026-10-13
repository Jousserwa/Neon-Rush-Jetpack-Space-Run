package com.neonrush.game.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.Dp
import com.neonrush.game.R

/**
 * The pilot as the player actually equipped it (colour grade, aura, particles), for every avatar spot
 * (header, in-run popup, game over, pilot card). Replaces the plain default pilot sprite.
 */
@Composable
fun SuitAvatar(suitId: String, box: Dp, tick: Int = 0, modifier: Modifier = Modifier) {
    val bmp = ImageBitmap.imageResource(id = R.drawable.pilot_run_1)
    Canvas(modifier.size(box)) {
        val h = this.size.height * 0.92f
        val w = h * bmp.width.toFloat() / bmp.height.toFloat()
        drawPilotWithSuit(suitId, bmp, this.size.width / 2f, this.size.height / 2f, w, h, tick)
    }
}
