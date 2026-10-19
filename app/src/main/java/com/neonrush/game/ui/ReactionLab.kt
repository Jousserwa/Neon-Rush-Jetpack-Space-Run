package com.neonrush.game.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.neonrush.game.R
import kotlin.math.roundToInt

/**
 * TESTING ONLY. A stage with the pilot where every reaction can be fired by hand: each power-up, fuel, diamond, one obstacle of
 * every reaction family, and every outcome (hit / shield / invincible smash / ghost phase / boom). It uses exactly the same drawing
 * code as the game, so if something looks wrong here it is wrong in the game too - and you do not have to wait for rare items.
 * Opened from Settings while TESTING_DISABLE_PRO_GATE is true.
 */
@Composable
fun ReactionLabDialog(onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        ReactionLab(onClose)
    }
}

private val LAB_PU = listOf("PU1", "PU2", "PU3", "PU4", "PU5", "PU6", "PU7", "PU8", "PU9", "PU10", "PU11", "PU12")
private val LAB_PU_NAMES = mapOf(
    "PU1" to "Shield", "PU2" to "Magnet", "PU3" to "Slow", "PU4" to "Ghost", "PU5" to "x2", "PU6" to "x5",
    "PU7" to "Invinc.", "PU8" to "Boom", "PU9" to "Shrink", "PU10" to "Warp", "PU11" to "Skip", "PU12" to "Legend"
)
private val LAB_OBS = listOf(
    "Block" to "PILLAR_TOP", "Barrier" to "BARRIER", "Rubble" to "TUNNEL_TOP", "Rock" to "MOUNTAIN_BOTTOM", "Spike" to "STALACTITE",
    "Laser" to "LASER", "Zap" to "ZAP_FIELD", "Blade" to "BLADE", "Splitter" to "SPLITTER", "Phantom" to "PHANTOM",
    "Charger" to "SHADOW_CHARGER", "Blink" to "BLINK_HAZARD", "Lava" to "LAVA_BALL", "Drone" to "DRONE", "Bullet" to "DRONE_SHOT"
)
private val LAB_OUTCOMES = listOf("HIT", "SHIELD", "SMASH", "PHASE", "BOOM")

@Composable
fun ReactionLab(onClose: () -> Unit) {
    val f1 = ImageBitmap.imageResource(id = R.drawable.pilot_run_1)
    val f2 = ImageBitmap.imageResource(id = R.drawable.pilot_run_3)
    val f3 = ImageBitmap.imageResource(id = R.drawable.pilot_run_5)
    val frames = remember(f1, f2, f3) { listOf(f1, f2, f3) }
    val measurer = rememberTextMeasurer()
    val clock = remember { mutableStateOf(0f) }
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) withFrameNanos { n -> if (n - last >= 33_000_000L) { last = n; clock.value = ((n / 1_000_000L) % 3_600_000L) / 1000f } }
    }
    // power-ups stay "active" for 8 seconds, like in the game (ticks are 125 ms)
    val endAt = remember { mutableStateOf<Map<String, Long>>(emptyMap()) }
    var shieldLayers by remember { mutableStateOf(0) }
    var outcome by remember { mutableStateOf("HIT") }

    fun fire(id: String) {
        val now = ReactFx.nowMs()
        PickupFx.onPickup(id)
        ReactFx.collect("pu", id, 0.2f, 50)
        when (id) {
            "PU1" -> { shieldLayers = 2; PilotShieldFx.onGain(2) }
            "PU12" -> { endAt.value = endAt.value + (1..9).associate { "PU$it" to now + 5000L } + ("PU12" to now + 5000L); shieldLayers = 2; PilotShieldFx.onGain(2) }
            "PU8", "PU10", "PU11" -> {}
            else -> endAt.value = endAt.value + (id to now + 8000L)
        }
    }
    fun obstacle(sub: String) {
        if (outcome == "SHIELD") {
            if (shieldLayers > 0) { shieldLayers--; PilotShieldFx.onHit() } else { shieldLayers = 2; PilotShieldFx.onGain(2) }
        }
        ReactFx.obstacle(sub, outcome, 0.2f, 50)
    }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF05060D)).padding(8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("REACTION LAB (testing)", color = Color(0xFF00E5FF), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            LabChip("CLOSE", Color(0xFFFF5252), false) { onClose() }
        }

        Canvas(modifier = Modifier.fillMaxWidth().weight(1f)) {
            val cw = size.width
            val ch = size.height
            val t = clock.value
            val now = ReactFx.nowMs()
            val dur = endAt.value.mapNotNull { (k, v) -> val left = ((v - now) / 125L).toInt(); if (left > 0) k to left else null }.toMap()
            val userX = cw * 0.2f
            val userY = ch * 0.5f
            val dh = ch * 0.40f
            val img = frames[((t * 12f).toInt()) % frames.size]
            val dw = dh * (img.width.toFloat() / img.height.toFloat())

            // every item's idle animation along the top
            val ids = LAB_PU + listOf("FUEL")
            for ((i, id) in ids.withIndex()) {
                drawPickupHalo(id, cw * (0.06f + 0.0725f * i), ch * 0.10f, ch * 0.075f, (t * 8f).toInt())
            }
            drawGemShine(cw * 0.97f, ch * 0.10f, ch * 0.07f)

            // the same order the game uses
            drawPickupAura(dur, userX, userY, dh, t)
            val pm = pilotMotionFor(dur, dh, t)
            val alpha = (pilotAlphaFor(dur, t) * pm.alpha).coerceIn(0.15f, 1f)
            val sc = pilotScaleFor(dur)
            withTransform({
                translate(pm.dx, pm.dy)
                scale(sc * pm.sx, sc * pm.sy, pivot = Offset(userX, userY))
            }) {
                rotate(degrees = pm.rot, pivot = Offset(userX, userY)) {
                    drawIntoCanvasLayer(alpha, userX, userY, dh) {
                        drawImage(img, dstOffset = IntOffset((userX - dw / 2f).roundToInt(), (userY - dh / 2f).roundToInt()), dstSize = IntSize(dw.roundToInt(), dh.roundToInt()))
                    }
                    drawPilotShield(shieldLayers, userX, userY, dh, dw, t)
                }
            }
            drawWorldReactions(cw, ch, measurer)
            drawPickupBurst(userX, userY, dh)
            drawPilotPowerFx(dur, userX, userY, dh, t, measurer)
            drawPilotHitFx(userX, userY, dh)
        }

        Text("POWER-UPS, FUEL, DIAMOND", color = Color.White.copy(alpha = 0.6f), fontSize = 9.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(top = 4.dp))
        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (id in LAB_PU) LabChip(LAB_PU_NAMES[id] ?: id, PickupFx.colorOf(id), false) { fire(id) }
            LabChip("Fuel", PickupFx.colorOf("FUEL"), false) { PickupFx.onPickup("FUEL"); ReactFx.collect("fuel", "+${ReactFx.FUEL_PICKUP_PCT}%", 0.2f, 50) }
            LabChip("Diamond", Color(0xFF00E5FF), false) { ReactFx.collect("gem", "", 0.2f, 50) }
        }
        Text("OUTCOME", color = Color.White.copy(alpha = 0.6f), fontSize = 9.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(top = 4.dp))
        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (o in LAB_OUTCOMES) LabChip(o, Color(0xFFFFD54F), outcome == o) { outcome = o }
        }
        Text("OBSTACLES (uses the outcome above)", color = Color.White.copy(alpha = 0.6f), fontSize = 9.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(top = 4.dp))
        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for ((label, sub) in LAB_OBS) LabChip(label, Color(0xFFFF4081), false) { obstacle(sub) }
        }
        Box(modifier = Modifier.height(6.dp))
    }
}

@Composable
private fun LabChip(text: String, color: Color, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .background(if (selected) color.copy(alpha = 0.35f) else color.copy(alpha = 0.14f), RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = color, fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
    }
}

/** Draws [block] through a see-through layer, like the pilot in the game. */
private fun DrawScope.drawIntoCanvasLayer(alpha: Float, x: Float, y: Float, h: Float, block: DrawScope.() -> Unit) {
    if (alpha >= 0.999f) { block(); return }
    drawIntoCanvas { cv -> cv.saveLayer(Rect(x - h * 2.2f, y - h * 1.4f, x + h * 1.6f, y + h * 1.4f), Paint().apply { this.alpha = alpha }) }
    block()
    drawIntoCanvas { cv -> cv.restore() }
}
