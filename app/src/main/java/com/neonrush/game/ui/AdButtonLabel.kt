package com.neonrush.game.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.neonrush.game.AdMobManager
import com.neonrush.game.RewardedSlot

/**
 * Label for a "watch ad" button that updates live: normal text while an ad is
 * ready or loading, "OFFLINE BONUS · n LEFT" or "AD UNAVAILABLE" when it isn't.
 */
@Composable
fun adButtonLabel(slot: RewardedSlot, readyLabel: String): String {
    val version by AdMobManager.adStateVersion.collectAsState()
    return AdMobManager.rewardedLabel(slot, readyLabel, version)
}
