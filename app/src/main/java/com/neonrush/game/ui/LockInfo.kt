package com.neonrush.game.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.neonrush.game.ui.theme.CyberOnSurface
import com.neonrush.game.ui.theme.CyberSurface

/** A progress fraction that can never be NaN or out of range (a NaN width crashes Compose layout). */
fun safeFrac(f: Float): Float = if (f.isNaN()) 0f else f.coerceIn(0f, 1f)

/** What a locked item needs. Shown when the player taps the locked card. */
data class LockInfo(
    val emoji: String,
    val title: String,
    val headline: String,                     // one short line: why it is locked
    val lines: List<String> = emptyList(),    // the steps / requirements
    val progress: Float? = null,              // optional 0..1 bar
    val progressLabel: String? = null,
    val accent: Color = Color(0xFFFFD23F)
)

@Composable
fun LockInfoDialog(info: LockInfo, onDismiss: () -> Unit) {
    val pulse by rememberInfiniteTransition(label = "lockPulse").animateFloat(
        0f, 1f, infiniteRepeatable(tween(1100), RepeatMode.Reverse), label = "lp"
    )
    val shape = RoundedCornerShape(18.dp)
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().clip(shape)
                .background(Brush.verticalGradient(listOf(info.accent.copy(alpha = 0.22f), Color(0xFF0B1022))))
                .border(2.dp, lerp(info.accent.copy(alpha = 0.4f), info.accent, pulse), shape).padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            PilotPose(Pose.SAD, 84.dp)
            Text("🔒", fontSize = 22.sp)
            Text("${info.emoji} ${info.title}", color = Color.White, fontWeight = FontWeight.Black, fontSize = 17.sp,
                fontFamily = FontFamily.Monospace, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
            Text(info.headline, color = info.accent, fontWeight = FontWeight.Bold, fontSize = 12.sp, textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp, bottom = 10.dp))
            info.lines.forEach { l ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.Top) {
                    Text("▸", color = info.accent, fontSize = 13.sp, modifier = Modifier.padding(end = 8.dp))
                    Text(l, color = CyberOnSurface, fontSize = 13.sp)
                }
            }
            if (info.progress != null) {
                Box(Modifier.padding(top = 12.dp).fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(Color.White.copy(alpha = 0.12f))) {
                    Box(Modifier.fillMaxWidth(safeFrac(info.progress)).height(8.dp).background(info.accent))
                }
                if (info.progressLabel != null) Text(info.progressLabel, color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace, modifier = Modifier.padding(top = 4.dp))
            }
            Text("GOT IT", color = Color(0xFF05060F), fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, fontSize = 13.sp,
                modifier = Modifier.padding(top = 16.dp).clip(RoundedCornerShape(10.dp)).background(info.accent)
                    .clickable { onDismiss() }.padding(horizontal = 28.dp, vertical = 10.dp))
        }
    }
}
