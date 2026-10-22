package com.neonrush.game.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.neonrush.game.StarterWallet
import com.neonrush.game.StarterWalletHost

/** Shown once per starter day. Mount it once near the top of the app. */
@Composable
fun StarterWalletDialog() {
    val t = StarterWalletHost.pending ?: return
    val gold = Color(0xFFFFD23F)
    Dialog(onDismissRequest = { StarterWalletHost.pending = null }) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color(0xF0080C1E))
                .border(2.dp, gold.copy(alpha = 0.7f), RoundedCornerShape(18.dp)).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            PilotPose(Pose.CHEER, 120.dp)
            Text(if (t.day == 1) "WELCOME, PILOT!" else "WALLET REFILLED", color = gold, fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace, fontSize = 20.sp, modifier = Modifier.padding(top = 6.dp))
            Text(
                if (t.day == 1) "Your starter wallet: ${t.wallet} 💎\nEnough to refuel again and again while you learn the ropes."
                else "+${t.added} 💎  •  your wallet is back to ${t.wallet} 💎",
                color = Color.White, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp)
            )
            Text("Starter day ${t.day} of ${StarterWallet.DAYS}", color = Color(0xFF69F0AE), fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
            Text(
                if (t.day >= StarterWallet.DAYS) "This was the last free top-up. From now on your gems are yours: earn more from runs, crates, Storm Run and Ghost Duel."
                else "Spent some? Come back tomorrow and it fills up again, up to ${StarterWallet.FLOOR}. It never stacks.",
                color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp)
            )
            Text("LET'S GO", color = Color.Black, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, fontSize = 15.sp,
                modifier = Modifier.padding(top = 16.dp).clip(RoundedCornerShape(24.dp)).background(gold)
                    .clickable { StarterWalletHost.pending = null }.padding(horizontal = 34.dp, vertical = 11.dp))
        }
    }
}
