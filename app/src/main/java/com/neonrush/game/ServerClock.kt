package com.neonrush.game

import android.os.SystemClock
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * Trusted time for early-access windows. Device clock can be changed by the player, so we ask
 * Firestore for its server timestamp once, store (serverMs - elapsedRealtime) and extrapolate with
 * the monotonic clock (elapsedRealtime cannot be changed by the user).
 *
 * Needs Firestore rule: allow create, update: if request.auth != null on /clock/{uid}.
 * If it never syncs (offline) we are conservative: Pro windows still work, non-Pro are NOT unlocked early
 * by a spoofed clock because [nowMs] falls back to last known server time + monotonic delta, and
 * to 0 (nothing public yet) if we never synced.
 */
object ServerClock {
    @Volatile private var offsetMs: Long? = null   // serverMs - elapsedRealtime at sync

    val isSynced get() = offsetMs != null

    fun nowMs(): Long = offsetMs?.let { it + SystemClock.elapsedRealtime() } ?: 0L

    suspend fun sync(uid: String): Boolean = try {
        val ref = FirebaseFirestore.getInstance().collection("clock").document(uid)
        ref.set(mapOf("t" to FieldValue.serverTimestamp())).await()
        val ts = ref.get().await().getTimestamp("t")
        if (ts != null) { offsetMs = ts.toDate().time - SystemClock.elapsedRealtime(); true } else false
    } catch (e: Exception) { false }
}
