package com.neonrush.game.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup

/**
 * Small floating message near the top of the screen. It has no touch handlers, so every tap goes straight through to
 * the buttons underneath (the old bottom snackbar covered the refuel / revive buttons). The caller clears it as soon as
 * the sentence has been readable for a moment (see toastMillis).
 */
@Composable
fun ToastPopup(text: String) {
    Popup(alignment = Alignment.TopCenter, offset = IntOffset(0, 150)) {
        Box(
            Modifier.padding(horizontal = 24.dp).widthIn(max = 340.dp)
                .background(Color(0xE60A0F24), RoundedCornerShape(12.dp))
                .border(1.dp, Color(0xFF00E5FF).copy(alpha = 0.7f), RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(text, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace, textAlign = TextAlign.Center)
        }
    }
}

/** Reading time: just long enough to finish the sentence, then gone. */
fun toastMillis(text: String): Long = (900L + text.length * 32L).coerceIn(1500L, 3800L)
