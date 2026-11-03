package com.neonrush.game.ui

import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext

/**
 * Loads a drawable by NAME at run time. Returns null if the picture has not been uploaded yet, so the screen draws its
 * code-made fallback instead of the whole build failing on a missing R.drawable reference.
 * Stage 168: decoded WITHOUT density scaling (inScaled = false). A picture in res/drawable used to be enlarged 2-3x on a phone (9x the memory);
 * every caller draws the picture into a size it chooses itself, so only its pixel shape matters.
 */
@Composable
fun rememberArt(name: String): ImageBitmap? {
    val ctx = LocalContext.current
    return remember(name) {
        try {
            val id = ctx.resources.getIdentifier(name, "drawable", ctx.packageName)
            if (id == 0) null else {
                val o = BitmapFactory.Options().apply { inScaled = false }
                BitmapFactory.decodeResource(ctx.resources, id, o)?.asImageBitmap()
            }
        } catch (_: Throwable) { null }
    }
}
