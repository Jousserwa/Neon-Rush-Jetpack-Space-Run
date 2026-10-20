package com.neonrush.game

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * FRIENDS and CHALLENGES. Identity is the anonymous Firebase uid; the friend code is the same 6-letter code as the referral code
 * (referralCodes/{code} -> ownerId), so one code does both jobs.
 *
 * Firestore:
 *   players/{uid}                 { name, best, best_easy, best_hard, best_legendary, updated, referralCode }   readable by any signed-in player
 *   players/{uid}/friends/{fuid}  { name, since }                                                              private to the owner
 * Adding a friend is one-way (a follow): nothing is written to the other player, and scores are already public on the board.
 * Challenge: pick a friend's score as your target. Beat it in a run on that difficulty for gems (max 3 a day, once per friend score).
 */
object Social {
    class Friend(val uid: String, val name: String, val best: Int, val isMe: Boolean)
    class Challenge(val uid: String, val name: String, val score: Int, val tierKey: String, val tierLabel: String)
    enum class AddResult { OK, NOT_FOUND, SELF, ALREADY, FULL, OFFLINE }

    const val MAX_FRIENDS = 50
    const val WIN_GEMS = 15
    const val WIN_PER_DAY = 3
    private const val PREFS = "neonrush_social"
    private const val CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

    private var appCtx: Context? = null
    private val db by lazy { FirebaseFirestore.getInstance() }
    private val auth by lazy { FirebaseAuth.getInstance() }

    private val _myCode = MutableStateFlow("")
    val myCode: StateFlow<String> = _myCode.asStateFlow()
    private val _rows = MutableStateFlow<List<Friend>>(emptyList())
    val rows: StateFlow<List<Friend>> = _rows.asStateFlow()
    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()
    private val _challenge = MutableStateFlow<Challenge?>(null)
    val challenge: StateFlow<Challenge?> = _challenge.asStateFlow()

    private fun prefs() = appCtx?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun init(ctx: Context) {
        appCtx = ctx.applicationContext
        val p = prefs() ?: return
        _myCode.value = p.getString("code", "") ?: ""
        val u = p.getString("ch_uid", "") ?: ""
        if (u.isNotBlank()) _challenge.value = Challenge(u, p.getString("ch_name", "") ?: "", p.getInt("ch_score", 0), p.getString("ch_tier", "") ?: "", p.getString("ch_label", "") ?: "")
    }

    private suspend fun uid(): String? = try {
        auth.currentUser?.uid ?: withTimeoutOrNull(8_000L) { auth.signInAnonymously().await().user?.uid }
    } catch (_: Throwable) { null }

    /** This player's code: the existing referral code if there is one, otherwise a new one (same structure the referral feature uses). */
    suspend fun ensureCode(): String {
        _myCode.value.takeIf { it.isNotBlank() }?.let { return it }
        val me = uid() ?: return ""
        return try {
            val ref = db.collection("players").document(me)
            var code = ref.get().await().getString("referralCode")
            if (code == null) {
                var tries = 0
                do {
                    code = (1..6).map { CODE_CHARS.random() }.joinToString("")
                    tries++
                } while (tries < 6 && db.collection("referralCodes").document(code!!).get().await().exists())
                db.collection("referralCodes").document(code!!).set(mapOf("ownerId" to me, "createdAt" to System.currentTimeMillis())).await()
                ref.set(mapOf("referralCode" to code), SetOptions.merge()).await()
            }
            prefs()?.edit()?.putString("code", code)?.apply()
            _myCode.value = code ?: ""
            code ?: ""
        } catch (_: Throwable) { "" }
    }

    /** Publishes this pilot's best score for one difficulty (tierKey is the board suffix: "", "_easy", "_hard", "_legendary"). */
    suspend fun publishScore(name: String, tierKey: String, score: Int) {
        prefs()?.edit()?.putInt("my$tierKey", score)?.apply()
        val me = uid() ?: return
        try {
            withTimeoutOrNull(5_000L) {
                db.collection("players").document(me)
                    .set(mapOf("name" to name.take(24), "best$tierKey" to score, "updated" to System.currentTimeMillis()), SetOptions.merge()).await()
            }
        } catch (_: Throwable) { }
    }

    fun myBest(tierKey: String): Int = prefs()?.getInt("my$tierKey", 0) ?: 0

    suspend fun addFriend(rawCode: String): AddResult {
        val code = rawCode.trim().uppercase(Locale.US)
        if (code.length < 4) return AddResult.NOT_FOUND
        val me = uid() ?: return AddResult.OFFLINE
        return try {
            val owner = db.collection("referralCodes").document(code).get().await().getString("ownerId") ?: return AddResult.NOT_FOUND
            if (owner == me) return AddResult.SELF
            val fr = db.collection("players").document(me).collection("friends")
            if (fr.document(owner).get().await().exists()) return AddResult.ALREADY
            if (fr.get().await().size() >= MAX_FRIENDS) return AddResult.FULL
            val nm = db.collection("players").document(owner).get().await().getString("name") ?: "Pilot"
            fr.document(owner).set(mapOf("name" to nm, "since" to System.currentTimeMillis())).await()
            AddResult.OK
        } catch (_: Throwable) { AddResult.OFFLINE }
    }

    suspend fun removeFriend(fuid: String) {
        val me = uid() ?: return
        try { db.collection("players").document(me).collection("friends").document(fuid).delete().await() } catch (_: Throwable) { }
        if (_challenge.value?.uid == fuid) clearChallenge()
    }

    /** Loads the friends board for one difficulty: you and your friends, best first. */
    suspend fun refresh(tierKey: String, myName: String) {
        _loading.value = true
        try {
            val me = uid()
            val out = ArrayList<Friend>()
            out += Friend(me ?: "me", myName.ifBlank { "You" }, myBest(tierKey), true)
            if (me != null) {
                val list = db.collection("players").document(me).collection("friends").get().await()
                val names = HashMap<String, String>()
                list.documents.forEach { names[it.id] = it.getString("name") ?: "Pilot" }
                val ids = names.keys.toList()
                val seen = HashSet<String>()
                for (chunk in ids.chunked(10)) {
                    val snap = db.collection("players").whereIn(FieldPath.documentId(), chunk).get().await()
                    for (d in snap.documents) {
                        seen += d.id
                        out += Friend(d.id, d.getString("name") ?: names[d.id] ?: "Pilot", (d.getLong("best$tierKey") ?: 0L).toInt(), false)
                    }
                }
                for (id in ids) if (id !in seen) out += Friend(id, names[id] ?: "Pilot", 0, false)
            }
            _rows.value = out.sortedByDescending { it.best }
        } catch (_: Throwable) {
        } finally { _loading.value = false }
    }

    // ------------------------------------------------------------------ challenges

    fun setChallenge(f: Friend, tierKey: String, tierLabel: String) {
        val c = Challenge(f.uid, f.name, f.best, tierKey, tierLabel)
        _challenge.value = c
        prefs()?.edit()?.putString("ch_uid", c.uid)?.putString("ch_name", c.name)?.putInt("ch_score", c.score)?.putString("ch_tier", c.tierKey)?.putString("ch_label", c.tierLabel)?.apply()
    }

    fun clearChallenge() {
        _challenge.value = null
        prefs()?.edit()?.remove("ch_uid")?.apply()
    }

    private fun today() = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    /** After a run: gems if this score beat the challenge target on the same difficulty. Daily cap, and each friend score pays once. */
    @Synchronized fun judge(score: Int, tierKey: String): Int {
        val c = _challenge.value ?: return 0
        val p = prefs() ?: return 0
        if (c.tierKey != tierKey || c.score <= 0 || score <= c.score) return 0
        val day = today()
        val n = if (p.getString("win_day", "") == day) p.getInt("win_n", 0) else 0
        val tag = "${c.uid}:${c.score}"
        clearChallenge()
        if (n >= WIN_PER_DAY || p.getString("win_last_$tag", "") == "1") return 0
        p.edit().putString("win_day", day).putInt("win_n", n + 1).putString("win_last_$tag", "1").apply()
        return WIN_GEMS
    }

    fun inviteText(tierLabel: String, best: Int, code: String): String =
        "⚡ I scored $best on $tierLabel in Neon Rush! Add me with pilot code $code and beat my score.\n" +
        "https://play.google.com/store/apps/details?id=com.neonrushinfinite.game"
}
