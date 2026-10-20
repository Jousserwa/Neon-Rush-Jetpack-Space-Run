package com.neonrush.game.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.neonrush.game.Social
import kotlinx.coroutines.launch

/**
 * FRIENDS dialog: your pilot code (copy / invite), add a friend by code, the friends board for the chosen difficulty,
 * and a CHALLENGE button on every friend. A challenged friend's score shows in the run HUD (see ChallengeHud).
 */
@Composable
fun FriendsDialog(myName: String, tierKey: String, tierLabel: String, onSync: () -> Unit, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val clip = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val code by Social.myCode.collectAsState()
    val rows by Social.rows.collectAsState()
    val loading by Social.loading.collectAsState()
    val challenge by Social.challenge.collectAsState()
    var input by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf("") }
    val gold = Color(0xFFFFD23F)
    val cyan = Color(0xFF00F0FF)

    LaunchedEffect(tierKey) {
        onSync()
        Social.ensureCode()
        Social.refresh(tierKey, myName)
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0xFF0B0720))
                .border(1.dp, cyan.copy(alpha = 0.6f), RoundedCornerShape(16.dp)).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("👥 FRIENDS  •  $tierLabel", color = cyan, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, fontFamily = FontFamily.Monospace)
            // my code
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (code.isBlank()) "YOUR CODE: ..." else "YOUR CODE: $code", color = gold, fontWeight = FontWeight.Bold, fontSize = 13.sp, fontFamily = FontFamily.Monospace,
                    modifier = Modifier.weight(1f))
                Text("COPY", color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(gold)
                        .clickable { if (code.isNotBlank()) { clip.setText(AnnotatedString(code)); msg = "Code copied" } }.padding(horizontal = 10.dp, vertical = 6.dp))
                Text("INVITE", color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(cyan)
                        .clickable {
                            if (code.isNotBlank()) {
                                val i = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, Social.inviteText(tierLabel, Social.myBest(tierKey), code))
                                ctx.startActivity(Intent.createChooser(i, "Invite a friend").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            }
                        }.padding(horizontal = 10.dp, vertical = 6.dp))
            }
            // add a friend
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = input, onValueChange = { input = it.uppercase().take(8) }, singleLine = true,
                    placeholder = { Text("Friend's code", fontSize = 12.sp) }, modifier = Modifier.weight(1f)
                )
                Text("ADD", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, fontFamily = FontFamily.Monospace,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Color(0xFF69F0AE))
                        .clickable {
                            val c = input
                            scope.launch {
                                msg = when (Social.addFriend(c)) {
                                    Social.AddResult.OK -> { input = ""; Social.refresh(tierKey, myName); "Friend added!" }
                                    Social.AddResult.NOT_FOUND -> "No pilot with that code"
                                    Social.AddResult.SELF -> "That's your own code"
                                    Social.AddResult.ALREADY -> "Already your friend"
                                    Social.AddResult.FULL -> "Friend list is full (${Social.MAX_FRIENDS})"
                                    Social.AddResult.OFFLINE -> "Can't reach the server. Check your connection."
                                }
                            }
                        }.padding(horizontal = 14.dp, vertical = 12.dp))
            }
            if (msg.isNotBlank()) Text(msg, color = Color(0xFFFFD54F), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            challenge?.let { c ->
                Text("⚔ TARGET: beat ${c.name} (${c.score}) on ${c.tierLabel}  •  +${Social.WIN_GEMS}💎   [CANCEL]",
                    color = Color(0xFFFF8A80), fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace,
                    modifier = Modifier.clickable { Social.clearChallenge() })
            }
            // board
            if (loading) Text("Loading...", color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            if (rows.size <= 1 && !loading) Text("No friends yet. Share your code, or enter theirs above.", color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            LazyColumn(modifier = Modifier.heightIn(max = 300.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(rows.size) { idx ->
                    val f = rows[idx]
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                            .background(if (f.isMe) cyan.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.06f)).padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("${idx + 1}", color = if (idx == 0) gold else Color.White.copy(alpha = 0.7f), fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                        Text(if (f.isMe) "${f.name} (you)" else f.name, color = Color.White, fontSize = 12.sp, fontFamily = FontFamily.Monospace, maxLines = 1, modifier = Modifier.weight(1f))
                        Text("${f.best}", color = cyan, fontWeight = FontWeight.Bold, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                        if (!f.isMe) {
                            Text("⚔", color = Color.Black, fontSize = 12.sp,
                                modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Color(0xFFFF8A65))
                                    .clickable { if (f.best > 0) { Social.setChallenge(f, tierKey, tierLabel); msg = "Challenge set: beat ${f.best}" } else msg = "${f.name} has no score on this difficulty yet" }
                                    .padding(horizontal = 8.dp, vertical = 3.dp))
                            Text("✕", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp,
                                modifier = Modifier.clickable { scope.launch { Social.removeFriend(f.uid); Social.refresh(tierKey, myName) } }.padding(4.dp))
                        }
                    }
                }
            }
            Text("CLOSE", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace,
                modifier = Modifier.align(Alignment.End).clickable { onDismiss() }.padding(6.dp))
        }
    }
}

/** One line for the run HUD while a friend's score is your target. */
@Composable
fun ChallengeHud(currentScore: Int, modifier: Modifier = Modifier) {
    val c by Social.challenge.collectAsState()
    val ch = c ?: return
    val ahead = currentScore > ch.score
    Text(
        (if (ahead) "⚔ AHEAD OF " else "⚔ BEAT ") + ch.name.take(12) + "  " + ch.score,
        color = if (ahead) Color(0xFF69F0AE) else Color(0xFFFF8A65),
        fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, modifier = modifier
    )
}
