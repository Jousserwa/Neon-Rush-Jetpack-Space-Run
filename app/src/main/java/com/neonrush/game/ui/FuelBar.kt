
package com.neonrush.game.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
@Composable
fun FuelBar(
    fuelPercent: Int,
    refillCount: Int,
    isPro: Boolean,
    cost: Int,
    onRefuel: () -> Unit,
    onFuelTierChanged: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val capReached = !isPro && refillCount >= 90 /* TESTING ONLY: restore to 6 */
    val haptic = LocalHapticFeedback.current

    // Base color + tier name by fuel level
    val tier = when {
        fuelPercent > 50 -> "normal"
        fuelPercent > 30 -> "warning"
        else -> "critical"
    }
    val baseColor = when (tier) {
        "normal" -> Color(0xFF00E5FF)   // calm blue/cyan
        "warning" -> Color(0xFFFF9800)  // orange warning
        else -> Color(0xFFFF1744)       // urgent red
    }

    // Fire a callback exactly once whenever the tier changes (for sound/feedback hooks)
    var lastTier by remember { mutableStateOf(tier) }
    LaunchedEffect(tier) {
        if (tier != lastTier) {
            onFuelTierChanged(tier)
            lastTier = tier
        }
    }

    // Pulse speed/intensity scales with urgency
    val infiniteTransition = rememberInfiniteTransition(label = "fuelPulse")
    val pulseDurationMs = when (tier) {
        "normal" -> 100000 // effectively static
        "warning" -> 900
        else -> 450
    }
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = if (tier == "normal") 1f else 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(pulseDurationMs, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "fuelPulseAlpha"
    )

    val refuelsLeftLabel = if (!isPro) {
        val left = (90 - refillCount) /* TESTING ONLY: restore to 6 */.coerceAtLeast(0)
        "  •  $left LEFT"
    } else ""

    val warningIcon = if (tier == "critical") "⚠️ " else if (tier == "warning") "⛽ " else "⛽ "

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF14141C).copy(alpha = 0.15f))
            .border(1.dp, baseColor.copy(alpha = pulseAlpha * 0.5f), RoundedCornerShape(12.dp))
            .clickable(enabled = !capReached) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onRefuel()
            }
    ) {
        // Fill proportional to fuel level
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction = (fuelPercent / 100f).coerceIn(0f, 1f))
                .background(baseColor.copy(alpha = pulseAlpha * 0.25f))
        )

        Text(
            text = if (capReached) {
                "FUEL ${fuelPercent}%"
            } else {
                "$warningIcon${fuelPercent}%  •  TAP: $cost💎$refuelsLeftLabel"
            },
            color = Color.White,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            style = androidx.compose.ui.text.TextStyle(
                shadow = androidx.compose.ui.graphics.Shadow(
                    color = Color.Black,
                    offset = androidx.compose.ui.geometry.Offset(1f, 1f),
                    blurRadius = 6f
                )
            ),
            modifier = Modifier.align(Alignment.Center)
        )
    }
}
