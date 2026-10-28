package com.neonrush.game.ui

import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext

/**
 * Loads a drawable by NAME at run time. Returns null if the picture has not been uploaded to res/drawable-nodpi yet, so the screen draws its
 * code-made fallback instead of the whole build failing on a missing R.drawable reference.
 */
@Composable
fun rememberArt(name: String): ImageBitmap? {
    val ctx = LocalContext.current
    return remember(name) {
        try {
            val id = ctx.resources.getIdentifier(name, "drawable", ctx.packageName)
            if (id == 0) null else BitmapFactory.decodeResource(ctx.resources, id)?.asImageBitmap()
        } catch (_: Throwable) { null }
    }
}
