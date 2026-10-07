package com.neonrush.game

import android.content.Context
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

// NOTE: LeaderboardPilot and SocialComment are defined in NeonRushViewModel.kt
// Do NOT redefine them here to avoid duplicate class errors

class FirebaseLeaderboardManager(context: Context) {
    // Auto-initialized from google-services.json — no manual config needed
    private val db = FirebaseFirestore.getInstance()
    private val _leaderboard = MutableStateFlow<List<LeaderboardPilot>>(emptyList())
    val leaderboard: StateFlow<List<LeaderboardPilot>> = _leaderboard.asStateFlow()

    private val _socialComments = MutableStateFlow<List<SocialComment>>(emptyList())
    val socialComments: StateFlow<List<SocialComment>> = _socialComments.asStateFlow()

    init {
        loadMockLeaderboard()
        loadMockSocialComments()
    }

    private fun loadMockLeaderboard() {
        _leaderboard.value = listOf(
            LeaderboardPilot(1, "CyberRunner", 2450, "Golden Age", "cyan_diamond", false, true, "ghost_cyberrunner"),
            LeaderboardPilot(2, "ZeroGlitch", 1980, "Neon Front", "purple_square", true, true, "ghost_zeroglitch"),
            LeaderboardPilot(3, "RetroWave", 1540, "Blackout", "green_triangle", true, true, "ghost_retro"),
            LeaderboardPilot(4, "NeonPilot_99", 1200, "Storm Zone 3", "cyan_diamond", false, false, "ghost_user"),
            LeaderboardPilot(5, "GlitchHunter", 980, "Derelict Signal", "magenta_pulse", false, true, "ghost_glitch"),
            LeaderboardPilot(6, "VoidWalker", 850, "Cell Block Zero", "green_triangle", false, true, "ghost_void"),
            LeaderboardPilot(7, "ChromeReaper", 720, "Green Hell", "purple_square", false, true, "ghost_chrome"),
            LeaderboardPilot(8, "SignalGhost", 640, "Neon Front", "cyan_diamond", false, true, "ghost_signal"),
            LeaderboardPilot(9, "ToxicBloom", 510, "Blackout", "magenta_pulse", false, true, "ghost_toxic"),
            LeaderboardPilot(10, "CircuitBreaker", 480, "Golden Age", "green_triangle", false, true, "ghost_circuit")
        )
    }

    private fun loadMockSocialComments() {
        _socialComments.value = listOf(
            SocialComment("CyberRunner", "Just hit 2450 on Golden Age! The new update is insane.", "2m ago", "Golden Age"),
            SocialComment("ZeroGlitch", "Anyone else notice the ghost trails are faster now?", "15m ago", "Neon Front"),
            SocialComment("RetroWave", "Finally unlocked Chrome Reaper skin. Worth every gem.", "1h ago", "Blackout"),
            SocialComment("NeonPilot_99", "Daily challenge is brutal today. Only 2 attempts left!", "2h ago", "Storm Zone 3"),
            SocialComment("GlitchHunter", "Pro tip: save your shield for Zone 5. Trust me.", "3h ago", "Derelict Signal"),
            SocialComment("VoidWalker", "Just bought the monthly pass. No more ads!", "5h ago", "Cell Block Zero"),
            SocialComment("SignalGhost", "The audio engine in this game is unreal. Haptic feedback on point.", "8h ago", "Neon Front"),
            SocialComment("ToxicBloom", "Anyone want to race ghost telemetry? I'm online now.", "12h ago", "Blackout")
        )
    }

    suspend fun submitScore(username: String, score: Int, activeSkinId: String) {
        try {
            val data = hashMapOf(
                "username" to username,
                "bestScore" to score,
                "activeSkinId" to activeSkinId,
                "timestamp" to System.currentTimeMillis()
            )
            // Firestore queues this write on the device and sends it when the connection
            // returns, but await() would hang offline until the server confirms. Don't wait.
            withTimeoutOrNull(5_000L) {
                db.collection("leaderboard").document(username).set(data).await()
            }
        } catch (e: Exception) {
            // Silently fail — leaderboard is non-critical
        }
    }

    suspend fun fetchTopScores() {
        try {
            val snapshot = db.collection("leaderboard")
                .orderBy("bestScore", Query.Direction.DESCENDING)
                .limit(50)
                .get()
                .await()

            val pilots = snapshot.documents.mapIndexed { index, doc ->
                LeaderboardPilot(
                    rank = index + 1,
                    name = doc.getString("username") ?: "",
                    bestScore = doc.getLong("bestScore")?.toInt() ?: 0,
                    activeZone = "Unknown",
                    activeSkinId = doc.getString("activeSkinId") ?: "cyan_diamond",
                    isFollowed = false,
                    isBot = false,
                    challengeId = ""
                )
            }
            if (pilots.isNotEmpty()) {
                _leaderboard.value = pilots
            }
        } catch (e: Exception) {
            // Keep mock data if Firebase fails
        }
    }

    companion object {
        // Singleton instance for ViewModel access
        private lateinit var instance: FirebaseLeaderboardManager
        
        val globalRankings: StateFlow<List<LeaderboardPilot>>
            get() = instance.leaderboard
        
        fun initialize(context: Context) {
            instance = FirebaseLeaderboardManager(context)
        }
        
        suspend fun submitScore(username: String, score: Int, activeSkinId: String) {
            instance.submitScore(username, score, activeSkinId)
        }
        
        suspend fun fetchTopScores() {
            instance.fetchTopScores()
        }
    }
}

