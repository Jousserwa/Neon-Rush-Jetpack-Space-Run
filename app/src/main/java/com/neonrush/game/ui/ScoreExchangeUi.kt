package com.neonrush.game.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.neonrush.game.ScoreVouchers

private val SE_CYAN = Color(0xFF00E5FF)
private val SE_MONO = FontFamily.Monospace

/** Home card: score vouchers earned by 30,000+ runs, exchanged for bombs, airstrikes or military shields. */
@Composable
fun ScoreExchangeCard(modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    @Suppress("UNUSED_VARIABLE") val v = ScoreVouchers.version
    val n = ScoreVouchers.count(ctx)
    var toast by remember { mutableStateOf("") }
    val shape = RoundedCornerShape(16.dp)
    Column(modifier.fillMaxWidth().clip(shape).background(Brush.horizontalGradient(listOf(Color(0xFF062A33), Color(0xFF0B1536)))).border(2.dp, SE_CYAN.copy(alpha = if (n > 0) 0.85f else 0.3f), shape).padding(12.dp)) {
        Text("SCORE EXCHANGE · 🎟 $n", color = SE_CYAN, fontSize = 13.sp, fontWeight = FontWeight.Black, fontFamily = SE_MONO)
        Text("Score 30,000+ in a run = 1 voucher (60k = 2, 100k = 3, max ${ScoreVouchers.DAILY_CAP} a day)", color = Color.White.copy(alpha = 0.8f), fontSize = 9.sp, fontFamily = SE_MONO)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (p in ScoreVouchers.Prize.values()) {
                val icon = when (p) { ScoreVouchers.Prize.BOMBS -> "💣"; ScoreVouchers.Prize.AIR -> "🚁"; else -> "🛡" }
                Column(Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(if (n > 0) SE_CYAN else Color.White.copy(alpha = 0.12f))
                    .clickable(enabled = n > 0) { if (ScoreVouchers.exchange(ctx, p)) toast = "Got ${p.label}!" }.padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(icon, fontSize = 16.sp)
                    Text(p.label, color = if (n > 0) Color.Black else Color.White.copy(alpha = 0.55f), fontSize = 9.sp, fontWeight = FontWeight.Black, fontFamily = SE_MONO)
                }
            }
        }
        if (toast.isNotEmpty()) Text(toast, color = Color(0xFF69F0AE), fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = SE_MONO, modifier = Modifier.padding(top = 6.dp))
    }
}
