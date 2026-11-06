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
import com.neonrush.game.AmmoDepot

private val AD_CYAN = Color(0xFF00E5FF)
private val AD_MONO = FontFamily.Monospace

/**
 * Home card for the Ammo Depot (bought shots). Locked until the game has been installed [AmmoDepot.MIN_DAYS] days.
 * [onBuy] = (productId, done) -> start the purchase, call done(true) when it worked (the card then delivers the shots).
 */
@Composable
fun AmmoDepotCard(testing: Boolean, onBuy: (String, (Boolean) -> Unit) -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    @Suppress("UNUSED_VARIABLE") val v = AmmoDepot.version
    val open = testing || AmmoDepot.unlocked(ctx)
    val left = AmmoDepot.daysLeft(ctx)
    val reserve = AmmoDepot.reserve(ctx)
    val shape = RoundedCornerShape(16.dp)
    Column(modifier.fillMaxWidth().clip(shape).background(Brush.horizontalGradient(listOf(Color(0xFF001F2A), Color(0xFF0B1536)))).border(2.dp, AD_CYAN.copy(alpha = if (open) 0.8f else 0.3f), shape).padding(12.dp)) {
        Text("AMMO DEPOT", color = AD_CYAN, fontSize = 13.sp, fontWeight = FontWeight.Black, fontFamily = AD_MONO)
        if (!open) {
            Text("🔒 Opens after $left more day${if (left == 1) "" else "s"} of playing", color = Color.White.copy(alpha = 0.8f), fontSize = 10.sp, fontFamily = AD_MONO)
        } else {
            Text("Bought shots: $reserve", color = Color.White.copy(alpha = 0.85f), fontSize = 10.sp, fontFamily = AD_MONO)
            Text("They fill your magazine at the start of each run. During a run an empty magazine stays empty until you collect armor. Each pack can be bought ${AmmoDepot.MAX_BUYS} times.", color = Color.White.copy(alpha = 0.6f), fontSize = 9.sp, fontFamily = AD_MONO)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (p in AmmoDepot.PACKS) {
                    val canBuy = AmmoDepot.canBuy(ctx, p.id, testing)
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(if (canBuy) AD_CYAN else Color.White.copy(alpha = 0.12f)).clickable(enabled = canBuy) { onBuy(p.id) { ok -> if (ok) AmmoDepot.deliver(ctx, p.id) } }.padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${p.shots} SHOTS", color = if (canBuy) Color.Black else Color.White.copy(alpha = 0.5f), fontSize = 10.sp, fontWeight = FontWeight.Black, fontFamily = AD_MONO)
                        Text(if (canBuy) p.price else "LIMIT ${AmmoDepot.MAX_BUYS}/${AmmoDepot.MAX_BUYS}", color = if (canBuy) Color.Black else Color.White.copy(alpha = 0.5f), fontSize = 12.sp, fontWeight = FontWeight.Black, fontFamily = AD_MONO)
                    }
                }
            }
        }
    }
}
