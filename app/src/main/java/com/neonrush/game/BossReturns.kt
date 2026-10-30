package com.neonrush.game

/** What the damage overlay reads: the last hit on the boss (size, big = missile) and the last bounce off its shield. (The old "returns" counter is gone: see Siege.kt.) */
object BossDamageFx {
    @Volatile var hitSeq = 0
    @Volatile var amt = 0f
    @Volatile var big = false
    @Volatile var blockSeq = 0
    fun hit(a: Float, isBig: Boolean) { amt = a; big = isBig; hitSeq += 1 }
    fun block() { blockSeq += 1 }
}
