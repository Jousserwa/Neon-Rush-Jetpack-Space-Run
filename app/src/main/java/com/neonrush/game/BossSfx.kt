package com.neonrush.game

/**
 * Thin bridge so ThreatDirector (no Context, no ViewModel) can play combat sounds through the game's
 * own NeonSoundEngine. That way the FX on/off switch, master volume and haptics settings all apply.
 */
object BossSfx {
    private val engine by lazy { NeonSoundEngine() } // all state lives in NeonSoundEngine's companion

    fun init() { /* NeonSoundEngine.init(context) is already called by the app */ }
    fun charge() = engine.playBossCharge()
    fun fire() = engine.playBossFire()
    fun warn() = engine.playThreatWarn()
    fun whoosh() = engine.playShadowWhoosh()
    fun zap() = engine.playZap()
}
