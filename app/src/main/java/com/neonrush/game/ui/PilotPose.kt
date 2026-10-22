package com.neonrush.game.ui

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.height
import com.neonrush.game.R

/**
 * The iconic pilot. ONE place that knows every pose, so a new pose is a single line here and then it can be used anywhere.
 * (When the run-with-ribbon pose arrives: add RUN_RIBBON(R.drawable.pilot_run_ribbon) below.)
 */
enum class Pose(val res: Int) {
    CHEER(R.drawable.pilot_cheer),   // good news, something to claim, welcome
    DIZZY(R.drawable.pilot_dizzy),   // crashed / hit / system failure
    SAD(R.drawable.pilot_sad),       // lost, locked, denied, nothing earned
    BOOST(R.drawable.pilot_boost),   // speed, power, energy
    THUMB(R.drawable.pilot_thumb)    // success, claimed, new best, victory
}

@Composable
fun PilotPose(pose: Pose, height: Dp, modifier: Modifier = Modifier) {
    val img: ImageBitmap = ImageBitmap.imageResource(id = pose.res)
    Image(painter = BitmapPainter(img), contentDescription = null, contentScale = ContentScale.Fit, modifier = modifier.height(height))
}

/** Picks a pose for a short message (used by the toast). null = no pilot. */
fun poseForMessage(text: String): Pose? {
    val t = text.lowercase()
    val bad = listOf("not enough", "failed", "can't", "cannot", "no ad", "unavailable", "error", "locked", "need ", "try again", "not ready", "no internet", "offline", "too late", "expired")
    val good = listOf("claimed", "reward", "earned", "unlocked", "welcome", "thank", "success", "purchased", "restored", "equipped", "complete", "collected", "+")
    return when {
        bad.any { t.contains(it) } -> Pose.SAD
        good.any { t.contains(it) } -> Pose.THUMB
        else -> null
    }
}
