package com.neonrush.game.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neonrush.game.NeonRushViewModel
import com.neonrush.game.R
import com.neonrush.game.RevenueCatManager
import com.neonrush.game.ThrusterCatalog
import com.neonrush.game.ThrusterKind
import com.neonrush.game.db.GameProfile
import kotlin.math.roundToInt

/** Shop > TRAILS: pick the thruster trail for your jetpack. Any trail can be previewed live before it is owned. */
@Composable
fun ThrustersPanel(viewModel: NeonRushViewModel, profile: GameProfile) {
    val st by viewModel.thrusters.state.collectAsState()
    val pro by RevenueCatManager.isPro.collectAsState()
    val tick = rememberMenuTick()
    val pilot = ImageBitmap.imageResource(id = R.drawable.pilot_run_1)
    LaunchedEffect(profile.bestZoneReached, profile.totalRuns, profile.bestScore, profile.currentStreak) {
        viewModel.thrusters.evaluate(profile)
    }
    val have = ThrusterCatalog.ALL.count { it.id in st.owned && (it.kind != ThrusterKind.PRO || pro) }

    Text("🚀 THRUSTER TRAILS", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = CyberPrimary,
        fontFamily = FontFamily.Monospace, modifier = Modifier.padding(bottom = 4.dp))
    Text("$have/${ThrusterCatalog.ALL.size} collected. A trail works with any hull and any suit. Tap a preview to see it fly.",
        color = CyberOnSurface.copy(alpha = 0.7f), fontSize = 12.sp, modifier = Modifier.padding(bottom = 12.dp))

    ThrusterCatalog.ALL.forEach { th ->
        val owned = th.id in st.owned && (th.kind != ThrusterKind.PRO || pro)
        val active = st.active == th.id && owned
        Row(
            Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(8.dp))
                .background(if (active) CyberPrimary.copy(alpha = 0.15f) else CyberSurface)
                .border(1.dp, if (active) CyberPrimary else Color.Transparent, RoundedCornerShape(8.dp))
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Canvas(Modifier.width(118.dp).height(56.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xFF070B1A))) {
                val ph = size.height * 0.8f
                val px = size.width * 0.74f
                val py = size.height / 2f
                drawThruster(th.id, px, py, ph, tick + th.id.hashCode().and(0xff), 1)
                val pw = ph * pilot.width / pilot.height
                drawImage(pilot, dstOffset = IntOffset((px - pw / 2f).roundToInt(), (py - ph / 2f).roundToInt()),
                    dstSize = IntSize(pw.roundToInt(), ph.roundToInt()))
            }
            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                Text("${th.emoji} ${th.name}", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
                Text(th.desc, fontSize = 11.sp, color = CyberPrimary.copy(alpha = 0.7f))
                if (!owned && th.kind != ThrusterKind.GEMS && th.kind != ThrusterKind.FREE) {
                    Text("🎯 ${th.requirement}" + (st.progress[th.id]?.let { "  ($it)" } ?: ""), fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace,
                        color = Color(0xFFFFD23F), modifier = Modifier.padding(top = 3.dp))
                }
            }
            when {
                active -> Box(Modifier.background(CyberPrimary, RoundedCornerShape(4.dp)).padding(horizontal = 8.dp, vertical = 4.dp)) {
                    Text("ACTIVE", color = CyberBackground, fontWeight = FontWeight.Bold, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                }
                owned -> Button(
                    onClick = { viewModel.thrusters.equip(th.id) },
                    colors = ButtonDefaults.buttonColors(containerColor = CyberSurface.copy(alpha = 0.8f)),
                    shape = RoundedCornerShape(4.dp), modifier = Modifier.border(1.dp, CyberPrimary, RoundedCornerShape(4.dp))
                ) { Text("EQUIP", color = CyberPrimary, fontFamily = FontFamily.Monospace, fontSize = 11.sp) }
                th.kind == ThrusterKind.GEMS -> Button(
                    onClick = { viewModel.thrusters.buy(th.id) },
                    colors = ButtonDefaults.buttonColors(containerColor = CyberSecondary), shape = RoundedCornerShape(4.dp)
                ) { Text("💎 ${th.gemCost}", color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 11.sp) }
                th.kind == ThrusterKind.PRO -> Text("👑 PRO", fontSize = 11.sp, color = Color(0xFFFFD23F), fontFamily = FontFamily.Monospace)
                else -> Text("🔒", fontSize = 16.sp)
            }
        }
    }
}
