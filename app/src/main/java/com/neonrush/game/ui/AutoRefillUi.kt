package com.neonrush.game.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neonrush.game.AutoRefill

private val AR_MONO = FontFamily.Monospace
private val AR_GREEN = Color(0xFF69F0AE)

/** Home-screen card for the Pro Auto-Refill switch. Free players see it locked (tap goes to the Pro page). */
@Composable
fun AutoRefillCard(isPro: Boolean, gems: Int, onUpgrade: () -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val on = AutoRefill.enabled(ctx)
    val enough = gems >= AutoRefill.MIN_GEMS
    val shape = RoundedCornerShape(16.dp)
    val status = when {
        !isPro -> "🔒 PRO ONLY · refuels for you, no tapping"
        !enough -> "Needs ${AutoRefill.MIN_GEMS}+ gems (you have $gems)" + if (on) " · paused" else ""
        on -> "ON · refuels at ${AutoRefill.TRIGGER}% fuel · revive stays manual"
        else -> "OFF · tap the switch to refuel automatically"
    }
    Row(modifier.fillMaxWidth().clip(shape).background(Brush.horizontalGradient(listOf(Color(0xFF0B2A1C), Color(0xFF0B1536)))).border(2.dp, AR_GREEN.copy(alpha = if (isPro && enough) 0.7f else 0.3f), shape)
        .then(if (!isPro) Modifier.clickable { onUpgrade() } else Modifier).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("AUTO-REFILL", color = AR_GREEN, fontSize = 13.sp, fontWeight = FontWeight.Black, fontFamily = AR_MONO)
            Text(status, color = Color.White.copy(alpha = 0.85f), fontSize = 10.sp, fontFamily = AR_MONO)
        }
        Switch(checked = on && isPro, enabled = isPro && (enough || on), onCheckedChange = { want ->
            if (want) { if (AutoRefill.canEnable(isPro, gems)) AutoRefill.set(ctx, true) } else AutoRefill.set(ctx, false)
        })
    }
}
