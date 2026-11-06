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
import com.neonrush.game.BombDepot
import com.neonrush.game.BroodBomb

private val BD_PINK = Color(0xFFFF4081)
private val BD_MONO = FontFamily.Monospace

/**
 * Home card for the Bomb Depot (real-money bombs). Locked until the game has been installed [BombDepot.MIN_DAYS] days.
 * [onBuy] = (productId, done) -> start the purchase, call done(true) when it worked (the card then delivers the goods).
 */
@Composable
fun BombDepotCard(testing: Boolean, onBuy: (String, (Boolean) -> Unit) -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    @Suppress("UNUSED_VARIABLE") val v = BroodBomb.version
    val open = testing || BombDepot.unlocked(ctx)
    val left = BombDepot.daysLeft(ctx)
    val unl = BombDepot.unlimited(ctx)
    val reserve = BombDepot.reserve(ctx)
    val shape = RoundedCornerShape(16.dp)
    Column(modifier.fillMaxWidth().clip(shape).background(Brush.horizontalGradient(listOf(Color(0xFF2A0B1C), Color(0xFF0B1536)))).border(2.dp, BD_PINK.copy(alpha = if (open) 0.8f else 0.3f), shape).padding(12.dp)) {
        Text("BOMB DEPOT", color = BD_PINK, fontSize = 13.sp, fontWeight = FontWeight.Black, fontFamily = BD_MONO)
        if (!open) {
            Text("🔒 Opens after $left more day${if (left == 1) "" else "s"} of playing", color = Color.White.copy(alpha = 0.8f), fontSize = 10.sp, fontFamily = BD_MONO)
        } else {
            Text("Bought bombs: $reserve · pocket storage: ${if (unl) "UNLIMITED" else "${BroodBomb.MAX}"}", color = Color.White.copy(alpha = 0.85f), fontSize = 10.sp, fontFamily = BD_MONO)
            Text("Bombs you find in the game are not affected.", color = Color.White.copy(alpha = 0.6f), fontSize = 9.sp, fontFamily = BD_MONO)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (p in BombDepot.PACKS) {
                    val canBuy = com.neonrush.game.AmmoDepot.canBuy(ctx, p.id, testing)
                    DepotBtn("${p.bombs} BOMBS", if (canBuy) p.price else "LIMIT ${com.neonrush.game.AmmoDepot.MAX_BUYS}/${com.neonrush.game.AmmoDepot.MAX_BUYS}", canBuy, Modifier.weight(1f)) { onBuy(p.id) { ok -> if (ok) BombDepot.deliver(ctx, p.id) } }
                }
                DepotBtn("UNLIMITED", if (unl) "OWNED" else BombDepot.UNLIMITED_PRICE, !unl, Modifier.weight(1f)) { onBuy(BombDepot.UNLIMITED_ID) { ok -> if (ok) BombDepot.deliver(ctx, BombDepot.UNLIMITED_ID) } }
            }
        }
    }
}

@Composable
private fun DepotBtn(title: String, price: String, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.clip(RoundedCornerShape(10.dp)).background(if (enabled) BD_PINK else Color.White.copy(alpha = 0.12f)).clickable(enabled = enabled) { onClick() }.padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, color = if (enabled) Color.Black else Color.White.copy(alpha = 0.6f), fontSize = 10.sp, fontWeight = FontWeight.Black, fontFamily = BD_MONO)
        Text(price, color = if (enabled) Color.Black else Color.White.copy(alpha = 0.6f), fontSize = 12.sp, fontWeight = FontWeight.Black, fontFamily = BD_MONO)
    }
}
