package com.neonrush.game.ui

import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neonrush.game.AirSkins

private val AK_GOLD = Color(0xFFFFD23F)
private val AK_MONO = FontFamily.Monospace

/**
 * AIR SKINS shop section: four helicopter looks and four jet looks. Tap a skin: owned = equip it, not owned = buy ($1.99, one-time).
 * [onBuy] = (productId, done): start the purchase and call done(true) when it worked (testing mode: free).
 */
@Composable
fun AirSkinsSection(testing: Boolean, onBuy: (String, (Boolean) -> Unit) -> Unit, clock: Float) {
    val ctx = LocalContext.current
    val dens = LocalDensity.current.density
    @Suppress("UNUSED_VARIABLE") val v = AirSkins.version
    var msg by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    Spacer(Modifier.height(16.dp))
    Text("AIRCRAFT SKINS", color = AK_GOLD, fontSize = 14.sp, fontWeight = FontWeight.Black, fontFamily = AK_MONO)
    Text("Looks only, one-time $1.99 each. Same power, new style.", color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp, fontFamily = AK_MONO)
    for (kind in listOf("heli", "jet")) {
        Spacer(Modifier.height(10.dp))
        Text(if (kind == "heli") "HAWK SIX · helicopter" else "VIPER ONE · jet", color = Color(0xFF00E5FF), fontSize = 11.sp, fontWeight = FontWeight.Black, fontFamily = AK_MONO)
        val eq = AirSkins.equipped(ctx, kind)
        val base = rememberArt("air_$kind")
        val arts = AirSkins.list(kind).map { rememberArt(it.art) }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            // slot 0 = the base look
            for (slot in 0..4) {
                val skin = if (slot == 0) null else AirSkins.list(kind)[slot - 1]
                val own = skin == null || AirSkins.owns(ctx, skin, testing)
                val sel = eq == slot
                val shape = RoundedCornerShape(10.dp)
                Column(Modifier.weight(1f).clip(shape).background(Color(0xFF16103A)).border(if (sel) 2.dp else 1.dp, if (sel) AK_GOLD else Color.White.copy(alpha = 0.2f), shape)
                    .clickable(enabled = !busy) {
                        if (own) { AirSkins.equip(ctx, kind, slot); msg = "" }
                        else if (skin != null) { busy = true; onBuy(skin.id) { ok -> busy = false; if (ok) AirSkins.applyPurchase(ctx, skin) else msg = "Purchase cancelled" } }
                    }.padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Canvas(Modifier.fillMaxWidth().height(34.dp)) {
                        val art = if (slot == 0) base else arts[slot - 1]
                        if (kind == "heli") AirCraftArt.heli(this, size.width / 2f, size.height / 2f, 44f * dens, 1f, clock, 3f, art, false) else AirCraftArt.jet(this, size.width / 2f, size.height / 2f, 46f * dens, 1f, clock, 0f, art, false)
                        if (!own) drawRect(Color(0x99100C28))
                    }
                    Text(skin?.name ?: "BASE", color = if (sel) AK_GOLD else Color.White, fontSize = 7.sp, fontWeight = FontWeight.Black, fontFamily = AK_MONO, maxLines = 1)
                    Text(if (sel) "ON" else if (own) "TAP" else skin!!.price, color = Color.White.copy(alpha = 0.8f), fontSize = 8.sp, fontFamily = AK_MONO)
                }
            }
        }
    }
    if (msg.isNotEmpty()) Text(msg, color = Color(0xFFFF8A80), fontSize = 10.sp, fontFamily = AK_MONO, modifier = Modifier.padding(top = 4.dp))
}
