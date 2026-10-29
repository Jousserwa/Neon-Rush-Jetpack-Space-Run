package com.neonrush.game

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Vibrator
import android.os.VibratorManager
import android.os.VibrationEffect
import android.util.Log
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.sin
import kotlin.math.cos
import kotlin.math.abs
import kotlin.math.pow
import kotlin.random.Random

class NeonSoundEngine {

    companion object {
        private const val TAG = "NeonSoundEngine"
        private const val SAMPLE_RATE = 44100
        private const val BLOCK_SIZE = 512 // Stereo frames (512 L + 512 R samples)
        
        @Volatile
        private var isInitialized = false
        private var appContext: Context? = null
        private var audioTrack: AudioTrack? = null
        private var audioThread: HandlerThread? = null
        private var audioHandler: Handler? = null
        private var vibrator: Vibrator? = null
        
        private var isPlaying = false
        private val activeVoices = CopyOnWriteArrayList<AudioVoice>()
        
        // Settings & Prefs
        private var sharedPrefs: SharedPreferences? = null
        private var masterVolumeSetting = 0.75f // maps 0..100 to 0..0.8f max volume knee
        private var soundEffectsEnabled = true
        private var ambientEnabled = true
        private var hapticsEnabled = true
        
        // Adaptive Audio multiplier
        @Volatile
        private var masterGainSetting = 0.55f // scaled dynamically by score
        
        // Game state variables
        private var currentScore = 0
        private var currentSpeed = 0f
        private var obstaclePassesCount = 0
        
        // Reverb properties
        private val reverbDelaySamples = (0.22f * SAMPLE_RATE).toInt()
        private val reverbBufferL = FloatArray(reverbDelaySamples)
        private val reverbBufferR = FloatArray(reverbDelaySamples)
        private var reverbIndex = 0
        private var reverbFeedback = 0.35f
        private var isReverbActive = false
        
        // Home Screen Ambient state
        @Volatile
        private var isHomeScreenActive = false
        private var dronePhase1 = 0.0f
        private var dronePhase2 = 0.0f
        private var dronePhase3 = 0.0f
        private var lfoPhase = 0.0f
        private var breathePhase = 0.0f

        fun init(context: Context) {
            if (isInitialized) return
            synchronized(this) {
                if (isInitialized) return
                appContext = context
                MusicBank.init(context)
                sharedPrefs = appContext?.getSharedPreferences("neon_rush_settings", Context.MODE_PRIVATE)
                
                // Read configurations
                soundEffectsEnabled = sharedPrefs?.getBoolean("sound_fx_enabled", true) ?: true
                ambientEnabled = sharedPrefs?.getBoolean("ambient_enabled", true) ?: true
                hapticsEnabled = sharedPrefs?.getBoolean("haptics_enabled", true) ?: true
                MusicBank.musicVolume = (sharedPrefs?.getInt("music_volume", 60) ?: 60) / 100f
                val volInt = sharedPrefs?.getInt("master_volume", 75) ?: 75
                masterVolumeSetting = (volInt / 100f) * 0.8f // Maps to masterGain 0 - 0.8f
                
                // Set initial home screen state
                isHomeScreenActive = true
                
                vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val manager = appContext?.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                    manager?.defaultVibrator
                } else {
                    @Suppress("DEPRECATION")
                    appContext?.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                }
                
                startAudioEngine()
                isInitialized = true
                Log.d(TAG, "NeonSoundEngine Service Initialized. Vol=$volInt FX=$soundEffectsEnabled Ambient=$ambientEnabled Haptics=$hapticsEnabled")
            }
        }
        
        private fun startAudioEngine() {
            try {
                val bufferSizeInBytes = AudioTrack.getMinBufferSize(
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_OUT_STEREO,
                    AudioFormat.ENCODING_PCM_FLOAT
                ).coerceAtLeast(BLOCK_SIZE * 2 * 4)
                
                audioTrack = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val builder = AudioTrack.Builder()
                        .setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_GAME)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                .build()
                        )
                        .setAudioFormat(
                            AudioFormat.Builder()
                                .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                                .setSampleRate(SAMPLE_RATE)
                                .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                                .build()
                        )
                        .setBufferSizeInBytes(bufferSizeInBytes)
                        .setTransferMode(AudioTrack.MODE_STREAM)
                    
                    // Avoid builder.setContext to bypass AppOps and prevent "attributionTag not declared in manifest" blockages on Android 12+
                    
                    builder.build()
                } else {
                    @Suppress("DEPRECATION")
                    AudioTrack(
                        android.media.AudioManager.STREAM_MUSIC,
                        SAMPLE_RATE,
                        AudioFormat.CHANNEL_OUT_STEREO,
                        AudioFormat.ENCODING_PCM_FLOAT,
                        bufferSizeInBytes,
                        AudioTrack.MODE_STREAM
                    )
                }
                
                audioThread = HandlerThread("NeonAudioRenderThread")
                audioThread?.start()
                audioHandler = Handler(audioThread!!.looper)
                
                isPlaying = true
                audioTrack?.play()
                
                audioHandler?.post(object : Runnable {
                    override fun run() {
                        if (!isPlaying) return
                        renderNextAudioBlock()
                        audioHandler?.post(this)
                    }
                })
            } catch (e: Exception) {
                Log.e(TAG, "AudioTrack setup error: ${e.message}")
            }
        }
        
        private fun renderNextAudioBlock() {
            val track = audioTrack ?: return
            val left = FloatArray(BLOCK_SIZE)
            val right = FloatArray(BLOCK_SIZE)
            
            // Render synth voices
            val finished = mutableListOf<AudioVoice>()
            for (voice in activeVoices) {
                if (voice.isActive()) {
                    if (soundEffectsEnabled) {
                        voice.render(left, right, BLOCK_SIZE)
                    }
                } else {
                    finished.add(voice)
                }
            }
            if (finished.isNotEmpty()) {
                activeVoices.removeAll(finished)
            }
            
            // Render drone if active on homepage
            if (isHomeScreenActive && ambientEnabled && !MusicBank.audible) {
                renderHomeAmbient(left, right)
            }
            
            // (hyper-speed wind noise removed on purpose: it was the constant 'shhh' hiss)
            
            // Dynamics Compressor soft knee limiter & Volume gains
            val stereoInterleaved = FloatArray(BLOCK_SIZE * 2)
            val compressor = DynamicsCompressor()
            
            val isReverb = isReverbActive && soundEffectsEnabled
            
            for (i in 0 until BLOCK_SIZE) {
                var l = left[i]
                var r = right[i]
                
                // Global adaptive reverb simulation for 500+ score runs
                if (isReverb) {
                    val revL = reverbBufferL[reverbIndex]
                    val revR = reverbBufferR[reverbIndex]
                    
                    reverbBufferL[reverbIndex] = l + revL * reverbFeedback
                    reverbBufferR[reverbIndex] = r + revR * reverbFeedback
                    
                    reverbIndex = (reverbIndex + 1) % reverbDelaySamples
                    
                    l += revL * 0.35f
                    r += revR * 0.35f
                }
                
                l = compressor.process(l)
                r = compressor.process(r)
                
                // Overall volume is combined master setting and adaptive intensity speed gain
                val outputMultiplier = masterVolumeSetting * masterGainSetting
                
                stereoInterleaved[2 * i] = l * outputMultiplier
                stereoInterleaved[2 * i + 1] = r * outputMultiplier
            }
            
            // Loop music (own volume; not affected by the score-driven adaptive gain)
            val mL = FloatArray(BLOCK_SIZE); val mR = FloatArray(BLOCK_SIZE)
            MusicBank.mix(mL, mR, BLOCK_SIZE, ambientEnabled, masterVolumeSetting / 0.8f)
            for (i in 0 until BLOCK_SIZE) {
                stereoInterleaved[2 * i] = (stereoInterleaved[2 * i] + mL[i] * 0.8f).coerceIn(-1f, 1f)
                stereoInterleaved[2 * i + 1] = (stereoInterleaved[2 * i + 1] + mR[i] * 0.8f).coerceIn(-1f, 1f)
            }
            
            try {
                track.write(stereoInterleaved, 0, stereoInterleaved.size, AudioTrack.WRITE_BLOCKING)
            } catch (e: Exception) {
                Log.e(TAG, "Track write exception: ${e.message}")
            }
        }
        
        private fun renderHomeAmbient(left: FloatArray, right: FloatArray) {
            val d1 = 55.0
            val d2 = 110.0
            val d3 = 165.0
            val dt = 1.0 / SAMPLE_RATE
            
            for (i in 0 until BLOCK_SIZE) {
                lfoPhase += (2.0 * Math.PI * (1.0 / 6.0) * dt).toFloat()
                if (lfoPhase > 2.0 * Math.PI) lfoPhase -= (2.0 * Math.PI).toFloat()
                
                val lfoMod = sin(lfoPhase) * 1.5
                
                dronePhase1 += (2.0 * Math.PI * (d1 + lfoMod) * dt).toFloat()
                dronePhase2 += (2.0 * Math.PI * (d2 + lfoMod) * dt).toFloat()
                dronePhase3 += (2.0 * Math.PI * (d3 + lfoMod) * dt).toFloat()
                
                val limitPhase = (2.0 * Math.PI).toFloat()
                if (dronePhase1 > limitPhase) dronePhase1 -= limitPhase
                if (dronePhase2 > limitPhase) dronePhase2 -= limitPhase
                if (dronePhase3 > limitPhase) dronePhase3 -= limitPhase
                
                breathePhase += (2.0 * Math.PI * 0.125 * dt).toFloat()
                if (breathePhase > limitPhase) breathePhase -= limitPhase
                
                val breatheVal = 0.9f + sin(breathePhase) * 0.1f
                
                val s1 = sin(dronePhase1) * 0.04f
                val s2 = sin(dronePhase2) * 0.03f
                val s3 = sin(dronePhase3) * 0.025f
                
                val mixedDrone = (s1 + s2 + s3) * breatheVal
                left[i] += mixedDrone
                right[i] += mixedDrone * 0.92f
            }
        }
        
        fun triggerHaptic(durationMs: Long) {
            if (!hapticsEnabled) return
            try {
                val v = vibrator ?: return
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    v.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    v.vibrate(durationMs)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Haptic trigger failed: ${e.message}")
            }
        }
        
        fun triggerHaptic(pattern: LongArray) {
            if (!hapticsEnabled) return
            try {
                val v = vibrator ?: return
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    v.vibrate(VibrationEffect.createWaveform(pattern, -1))
                } else {
                    @Suppress("DEPRECATION")
                    v.vibrate(pattern, -1)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Haptic pattern failed: ${e.message}")
            }
        }

        fun getSoundEffectsEnabled(): Boolean = soundEffectsEnabled
        fun setSoundEffectsEnabled(enabled: Boolean) {
            soundEffectsEnabled = enabled
            sharedPrefs?.edit()?.putBoolean("sound_fx_enabled", enabled)?.apply()
        }

        fun getAmbientEnabled(): Boolean = ambientEnabled
        fun setAmbientEnabled(enabled: Boolean) {
            ambientEnabled = enabled
            sharedPrefs?.edit()?.putBoolean("ambient_enabled", enabled)?.apply()
        }  
            fun setHomeActive(active: Boolean) {
            isHomeScreenActive = active
        }
         
        fun getHapticsEnabled(): Boolean = hapticsEnabled
        fun setHapticsEnabled(enabled: Boolean) {
            hapticsEnabled = enabled
            sharedPrefs?.edit()?.putBoolean("haptics_enabled", enabled)?.apply()
        }

        fun setMusicTrack(key: String?) = MusicBank.setTrack(key)
        fun setMusicSuspended(v: Boolean) { MusicBank.suspended = v }
        fun getMusicVolumePercent(): Int = (MusicBank.musicVolume * 100).toInt()
        fun setMusicVolumePercent(p: Int) {
            val c = p.coerceIn(0, 100); MusicBank.musicVolume = c / 100f
            sharedPrefs?.edit()?.putInt("music_volume", c)?.apply()
        }

        fun getMasterVolumePercent(): Int {
            return ((masterVolumeSetting / 0.8f) * 100).toInt().coerceIn(0, 100)
        }
        fun setMasterVolumePercent(percent: Int) {
            val clamped = percent.coerceIn(0, 100)
            masterVolumeSetting = (clamped / 100f) * 0.8f
            sharedPrefs?.edit()?.putInt("master_volume", clamped)?.apply()
        }
    }

    // Playback APIs called from ViewModels or Settings Screen
    fun playObstaclePass() {
        obstaclePassesCount++
        activeVoices.add(ObstaclePassVoice(660f, currentScore.toFloat()))
        triggerHaptic(8)
    }

    /** Near-miss with feedback that scales: tier 1 graze / 2 razor; the whoosh pitches up with the combo, razors get a double tap. */
    fun playNearMissTier(tier: Int, combo: Int) {
        val freq = 220f * (1f + 0.16f * (combo - 1).coerceIn(0, 4)) * (if (tier >= 2) 1.25f else 1f)
        activeVoices.add(NearMissVoice(true, freq))
        if (tier >= 2) triggerHaptic(longArrayOf(0, 22, 35, 40)) else triggerHaptic(18)
    }

    fun playNearMiss(isRight: Boolean = true) {
        activeVoices.add(NearMissVoice(isRight))
        triggerHaptic(50)
    }

    fun playSpeedMilestone(scoreMilestone: Int) {
        activeVoices.add(SpeedMilestoneVoice(scoreMilestone))
        val hapticPattern = when (scoreMilestone) {
            50 -> longArrayOf(0, 25, 10, 25)
            100 -> longArrayOf(0, 35, 15, 35, 15, 50)
            200 -> longArrayOf(0, 50, 20, 50, 20, 80, 20, 100)
            500 -> longArrayOf(0, 80, 30, 80, 30, 120, 30, 200)
            else -> longArrayOf(0, 100, 40, 100, 40, 150, 40, 300, 40, 500) // 1000 milestone
        }
        triggerHaptic(hapticPattern)
    }

    fun playCollision() {
        activeVoices.add(CollisionDeathVoice())
        triggerHaptic(longArrayOf(0, 150, 60, 250, 60, 150))
    }

    fun playPersonalBestBroken() {
        activeVoices.add(PersonalBestVoice())
        triggerHaptic(longArrayOf(0, 30, 15, 40, 15, 50, 15, 70, 15, 100, 15, 200))
    }

    fun playLaserWarning() {
        activeVoices.add(LaserBeamVoice())
        triggerHaptic(longArrayOf(0, 15, 15, 15, 15, 15))
    }

    fun playRotatingBladeSafe() {
        activeVoices.add(RotatingBladeVoice())
        triggerHaptic(12)
    }

    fun playGemCollect() {
        activeVoices.add(GemCollectVoice())
        triggerHaptic(12)
    }

    fun playShieldPowerup() {
        activeVoices.add(ShieldPowerupVoice())
        triggerHaptic(longArrayOf(0, 20, 10, 40))
    }

    fun playShieldBreak() {
        activeVoices.add(ShieldBreakVoice())
        triggerHaptic(longArrayOf(0, 100, 30, 60))
    }

    fun playGhostOvertake() {
        activeVoices.add(GhostOvertakeVoice())
        triggerHaptic(35)
    }

    fun playRevive() {
        activeVoices.add(ReviveActivationVoice())
        triggerHaptic(longArrayOf(0, 50, 25, 80, 25, 200))
    }

    // ---- Combat sounds (boss telegraph + new threats). Respect the same FX / volume / haptics settings. ----
    fun playBossCharge() {
        activeVoices.add(BossChargeVoice())
        triggerHaptic(longArrayOf(0, 15, 40, 20, 40, 30, 30, 45))
    }

    fun playBossFire() {
        activeVoices.add(BossFireVoice())
        triggerHaptic(40)
    }

    fun playThreatWarn() {
        activeVoices.add(ThreatWarnVoice())
        triggerHaptic(longArrayOf(0, 20, 60, 20))
    }

    fun playShadowWhoosh() {
        activeVoices.add(WhooshVoice())
    }

    fun playZap() {
        activeVoices.add(ZapVoice())
    }

    // ---- Shooting & smashing (Stage 136). Throttled so a held fire button never turns into a buzz. ----
    private var lastCombatNs = LongArray(16)
    private fun combatOk(kind: Int, minMs: Int): Boolean {
        val now = System.nanoTime()
        if (now - lastCombatNs[kind] < minMs * 1_000_000L) return false
        lastCombatNs[kind] = now
        return true
    }
    /** Bolt leaves the pilot. [power] = the heavy power shot. */
    fun playShot(power: Boolean) { if (combatOk(0, 45)) { activeVoices.add(CombatVoice(0, if (power) 1f else 0f)); triggerHaptic(if (power) 18L else 7L) } }
    /** A bolt shatters an obstacle. */
    fun playSmash() { if (combatOk(1, 70)) { activeVoices.add(CombatVoice(1)); triggerHaptic(28L) } }
    /** A bolt lands on the boss. [streak] 0..1 raises the pitch as hits chain. */
    fun playBossHit(streak: Float) { if (combatOk(2, 60)) { activeVoices.add(CombatVoice(2, streak.coerceIn(0f, 1f))); triggerHaptic(16L) } }
    /** Boss shield bounces a bolt away. */
    fun playDeflect() { if (combatOk(3, 60)) activeVoices.add(CombatVoice(3)) }
    /** Weapon overheated or out of shots. */
    fun playOverheat() { if (combatOk(4, 250)) { activeVoices.add(CombatVoice(4)); triggerHaptic(longArrayOf(0, 30, 30, 30)) } }
    /** Boss is destroyed. */
    fun playBossDown() { activeVoices.add(CombatVoice(5)); triggerHaptic(longArrayOf(0, 60, 40, 120, 40, 220)) }
    /** Legendary moves. */
    fun playDash() { if (combatOk(8, 200)) { activeVoices.add(CombatVoice(8)); triggerHaptic(25L) } }
    fun playPulse() { if (combatOk(9, 200)) { activeVoices.add(CombatVoice(9)); triggerHaptic(longArrayOf(0, 20, 30, 40)) } }
    fun playOverdrive() { activeVoices.add(CombatVoice(10)); triggerHaptic(longArrayOf(0, 30, 30, 60, 30, 90)) }
    fun playPhase2() { activeVoices.add(CombatVoice(11)); triggerHaptic(longArrayOf(0, 80, 40, 160)) }
    /** Armor magazine collected / baby born / chain blast. */
    fun playArmorPickup() { activeVoices.add(CombatVoice(12)); triggerHaptic(longArrayOf(0, 20, 30, 20, 30, 50)) }
    fun playBirth() { if (combatOk(13, 120)) activeVoices.add(CombatVoice(13)) }
    fun playNova() { activeVoices.add(CombatVoice(14)); triggerHaptic(longArrayOf(0, 40, 20, 90)) }
    /** Boss raises its shield. */
    fun playShieldUp() { if (combatOk(6, 400)) activeVoices.add(CombatVoice(6)) }
    /** Air Support (see AirVoice). */
    fun playAirRadio(open: Boolean) { activeVoices.add(AirVoice(if (open) 0 else 1)); triggerHaptic(15L) }
    fun playAirFlyby(jet: Boolean, seconds: Float) { activeVoices.add(AirVoice(if (jet) 3 else 2, seconds)) }
    fun playAirGun(seconds: Float) { activeVoices.add(AirVoice(4, seconds)); triggerHaptic(longArrayOf(0, 25, 15, 25, 15, 25, 15, 25, 15, 25, 15, 25)) }
    fun playAirMissile() { activeVoices.add(AirVoice(5)); triggerHaptic(longArrayOf(0, 40, 20, 40)) }
    fun playAirBoom() { activeVoices.add(AirVoice(6)); triggerHaptic(longArrayOf(0, 60, 30, 120, 40, 200)) }
    fun playAirLock() { activeVoices.add(AirVoice(7)) }
    /** Boss goes into a rage. */
    fun playEnrage() { if (combatOk(7, 1500)) { activeVoices.add(CombatVoice(7)); triggerHaptic(longArrayOf(0, 40, 30, 80)) } }

    // Interactive updates to track game adaptive metrics
    fun updateGameTelemetries(speed: Float, score: Int) {
        currentSpeed = speed
        currentScore = score
        
        // ADAPTIVE AUDIO SYSTEM: Scale master intensity dynamic gain by score levels
        masterGainSetting = when {
            score <= 50 -> 0.55f
            score <= 100 -> 0.60f
            score <= 200 -> 0.65f
            else -> 0.72f
        }
        
        // Reverb triggers at score 500+
        isReverbActive = score >= 500
    }

    fun setHomeScreenActiveState(active: Boolean) {
        isHomeScreenActive = active
    }

    // Deprecated compatibility methods to keep other ViewModel calls pristine without code friction
    fun playTone(frequency: Float, durationMs: Int, type: String = "sine") {
        activeVoices.add(SimpleToneVoice(frequency, durationMs, type))
    }

    fun playThrusterCharge() {
        playShieldPowerup()
    }

    fun playSpeedBoost() {
        playObstaclePass()
    }

    fun playCrash() {
        playCollision()
    }

    fun playUnlockSkin() {
        playPersonalBestBroken()
    }

    // Interfaces & Synth Inner Classes
    interface AudioVoice {
        fun isActive(): Boolean
        fun render(bufferLeft: FloatArray, bufferRight: FloatArray, sampleCount: Int)
    }

    class SimpleToneVoice(val freq: Float, val durationMs: Int, val type: String) : AudioVoice {
        private var sampleIdx = 0
        private val totalSamples = (durationMs * SAMPLE_RATE / 1000f).toInt()
        
        override fun isActive(): Boolean = sampleIdx < totalSamples
        
        override fun render(bufferLeft: FloatArray, bufferRight: FloatArray, sampleCount: Int) {
            val dt = 1.0f / SAMPLE_RATE
            for (i in 0 until sampleCount) {
                if (sampleIdx >= totalSamples) break
                val t = sampleIdx * dt
                val raw = when (type.lowercase()) {
                    "sawtooth" -> {
                        val period = 1.0f / freq
                        2.0f * ((t % period) / period) - 1.0f
                    }
                    "triangle" -> {
                        val period = 1.0f / freq
                        val x = (t % period) / period
                        if (x < 0.5f) 4.0f * x - 1.0f else 3.0f - 4.0f * x
                    }
                    else -> sin(2.0 * Math.PI * freq * t).toFloat()
                }
                
                val fadeOutDelta = (totalSamples * 0.8f).toInt()
                val fade = if (sampleIdx > fadeOutDelta) {
                    1.0f - (sampleIdx - fadeOutDelta).toFloat() / (totalSamples - fadeOutDelta)
                } else {
                    1.0f
                }
                
                val out = raw * 0.3f * fade
                bufferLeft[i] += out
                bufferRight[i] += out
                sampleIdx++
            }
        }
    }

    class ObstaclePassVoice(val baseFreq: Float, val score: Float) : AudioVoice {
        private var sampleIdx = 0
        private val totalSamples = (55 * SAMPLE_RATE / 1000f).toInt()
        private val attackSamples = (3 * SAMPLE_RATE / 1000f).toInt()
        private val decaySamples = totalSamples - attackSamples
        
        private val modifiedBase = baseFreq + (obstaclePassesCount / 50) * 5.0f + (score / 10f) * 0.5f
        private val finalFreq = modifiedBase + (Random.nextFloat() * 30f - 15f)
        
        override fun isActive() = sampleIdx < totalSamples
        
        override fun render(bufferLeft: FloatArray, bufferRight: FloatArray, sampleCount: Int) {
            val dt = 1.0f / SAMPLE_RATE
            for (i in 0 until sampleCount) {
                if (sampleIdx >= totalSamples) break
                val env = if (sampleIdx < attackSamples) {
                    sampleIdx.toFloat() / attackSamples
                } else {
                    1.0f - (sampleIdx - attackSamples).toFloat() / decaySamples
                }
                val t = sampleIdx * dt
                val mainSin = sin(2.0 * Math.PI * finalFreq * t).toFloat() * 0.22f
                val subSin = sin(2.0 * Math.PI * (finalFreq * 2.0) * t).toFloat() * 0.08f
                
                val signal = (mainSin + subSin) * env
                bufferLeft[i] += signal
                bufferRight[i] += signal
                sampleIdx++
            }
        }
    }

    class NearMissVoice(val isRight: Boolean, val freq: Float = 220f) : AudioVoice {
        private var sampleIdx = 0
        private val totalSamples = (80 * SAMPLE_RATE / 1000f).toInt()
        private val noiseSamples = (40 * SAMPLE_RATE / 1000f).toInt()
        private val biquad = BiquadFilter().apply { setBandpass(800f, SAMPLE_RATE.toFloat()) }
        
        private val pan = if (isRight) 0.5f else -0.5f
        private val leftG = cos((pan + 1.0) * Math.PI / 4.0).toFloat()
        private val rightG = sin((pan + 1.0) * Math.PI / 4.0).toFloat()
        
        override fun isActive() = sampleIdx < totalSamples
        
        override fun render(bufferLeft: FloatArray, bufferRight: FloatArray, sampleCount: Int) {
            val dt = 1.0f / SAMPLE_RATE
            for (i in 0 until sampleCount) {
                if (sampleIdx >= totalSamples) break
                var noise = 0f
                if (sampleIdx < noiseSamples) {
                    noise = (Random.nextFloat() * 2f - 1f) * 0.6f
                }
                
                val period = 1.0f / freq
                val t = sampleIdx * dt
                val saw = (2.0f * ((t % period) / period) - 1.0f) * 0.4f
                
                val envelope = 1.0f - (sampleIdx.toFloat() / totalSamples)
                val sum = (noise + saw) * envelope
                val output = biquad.process(sum)
                
                bufferLeft[i] += output * leftG
                bufferRight[i] += output * rightG
                sampleIdx++
            }
        }
    }

    class SpeedMilestoneVoice(val milestone: Int) : AudioVoice {
        private var sampleIdx = 0
        private val notes = when (milestone) {
            50 -> listOf(523f, 659f, 784f)
            100 -> listOf(659f, 784f, 1047f)
            200 -> listOf(784f, 1047f, 1319f)
            500 -> listOf(523f, 659f, 784f, 1047f, 1319f)
            else -> listOf(523f, 659f, 784f, 1047f, 1319f, 2093f)
        }
        private val durationMs = if (milestone == 50) 70 else if (milestone == 100) 80 else if (milestone == 200) 90 else 100
        private val gapMs = if (milestone == 50) 50 else if (milestone == 100) 60 else if (milestone == 200) 70 else 60
        private val noteSize = (durationMs * SAMPLE_RATE / 1000f).toInt()
        private val gapSize = (gapMs * SAMPLE_RATE / 1000f).toInt()
        private val stride = noteSize + gapSize
        private val peakAmp = if (milestone == 50) 0.4f else if (milestone == 100) 0.5f else if (milestone == 200) 0.6f else if (milestone == 500) 0.7f else 0.8f
        
        private val totalSamples = notes.size * stride + (if (milestone >= 1000) (300 * SAMPLE_RATE / 1000f).toInt() else 0)
        
        override fun isActive() = sampleIdx < totalSamples
        
        override fun render(bufferLeft: FloatArray, bufferRight: FloatArray, sampleCount: Int) {
            val dt = 1.0f / SAMPLE_RATE
            for (i in 0 until sampleCount) {
                if (sampleIdx >= totalSamples) break
                var ampVal = 0f
                val index = sampleIdx / stride
                val localOffset = sampleIdx % stride
                
                if (index < notes.size && localOffset < noteSize) {
                    val pitch = notes[index]
                    val t = localOffset * dt
                    val env = 1.0f - (localOffset.toFloat() / noteSize)
                    val raw = when (milestone) {
                        50 -> {
                            val period = 1.0f / pitch
                            val px = (t % period) / period
                            if (px < 0.5f) 4.0f * px - 1.0f else 3.0f - 4.0f * px
                        }
                        100 -> {
                            val period = 1.0f / pitch
                            2.0f * ((t % period) / period) - 1.0f
                        }
                        200 -> {
                            val period = 1.0f / pitch
                            val s1 = 2.0f * ((t % period) / period) - 1.0f
                            val s2 = sin(2.0 * Math.PI * pitch * t).toFloat()
                            (s1 + s2) * 0.5f
                        }
                        else -> sin(2.0 * Math.PI * pitch * t).toFloat()
                    }
                    ampVal = raw * peakAmp * env
                }
                
                if (milestone >= 1000 && sampleIdx >= stride) {
                    val delay = (300 * SAMPLE_RATE / 1000f).toInt()
                    if (sampleIdx >= delay) {
                        val reverbFreq = notes[notes.size - 1]
                        ampVal += sin(2.0 * Math.PI * reverbFreq * ((sampleIdx - delay) * dt)).toFloat() * peakAmp * 0.25f
                    }
                }
                bufferLeft[i] += ampVal
                bufferRight[i] += ampVal
                sampleIdx++
            }
        }
    }

    class CollisionDeathVoice : AudioVoice {
        private var sampleIdx = 0
        private val totalSamples = (1300 * SAMPLE_RATE / 1000f).toInt()
        private val layer1End = (30 * SAMPLE_RATE / 1000f).toInt()
        private val layer2End = (200 * SAMPLE_RATE / 1000f).toInt()
        private val layer3End = (700 * SAMPLE_RATE / 1000f).toInt()
        private val layer4End = (900 * SAMPLE_RATE / 1000f).toInt()
        private val layer5Start = layer4End
        private val layer5End = layer5Start + (40 * SAMPLE_RATE / 1000f).toInt()
        
        override fun isActive() = sampleIdx < totalSamples
        
        override fun render(bufferLeft: FloatArray, bufferRight: FloatArray, sampleCount: Int) {
            val dt = 1.0f / SAMPLE_RATE
            for (i in 0 until sampleCount) {
                if (sampleIdx >= totalSamples) break
                var value = 0f
                
                if (sampleIdx < layer1End) {
                    value += (Random.nextFloat() * 2f - 1f) * 0.9f
                }
                if (sampleIdx < layer2End) {
                    val fadeVal = kotlin.math.exp(-3.0f * (sampleIdx.toFloat() / layer2End))
                    value += sin(2.0 * Math.PI * 45.0 * (sampleIdx * dt)).toFloat() * 1.0f * fadeVal
                }
                if (sampleIdx < layer3End) {
                    val ratio = sampleIdx.toFloat() / layer3End
                    val freq = 900.0f * (80.0f / 900.0f).pow(ratio)
                    val period = 1.0f / freq
                    val t = sampleIdx * dt
                    val sawtoothWave = (2.0f * ((t % period) / period) - 1.0f) * 0.5f
                    value += sawtoothWave * (1.0f - ratio)
                }
                if (sampleIdx in layer5Start until layer5End) {
                    value += (Random.nextFloat() * 2f - 1f) * 0.15f
                }
                bufferLeft[i] += value
                bufferRight[i] += value
                sampleIdx++
            }
        }
    }

    class PersonalBestVoice : AudioVoice {
        private var sampleIdx = 0
        private val notes = listOf(523f, 659f, 784f, 1047f, 1319f, 2093f)
        private val noteSize = (100 * SAMPLE_RATE / 1000f).toInt()
        private val gapSize = (60 * SAMPLE_RATE / 1000f).toInt()
        private val stride = noteSize + gapSize
        private val chordStartIdx = notes.size * stride
        private val totalSamples = chordStartIdx + (600 * SAMPLE_RATE / 1000f).toInt()
        
        override fun isActive() = sampleIdx < totalSamples
        
        override fun render(bufferLeft: FloatArray, bufferRight: FloatArray, sampleCount: Int) {
            val dt = 1.0f / SAMPLE_RATE
            for (i in 0 until sampleCount) {
                if (sampleIdx >= totalSamples) break
                var value = 0f
                if (sampleIdx < chordStartIdx) {
                    val currentSection = sampleIdx / stride
                    for (nId in 0..currentSection.coerceAtMost(notes.size - 1)) {
                        val onsetTime = nId * stride
                        val age = sampleIdx - onsetTime
                        val pitch = notes[nId]
                        val t = age * dt
                        val volume = if (age < noteSize) 0.5f else 0.1f
                        value += sin(2.0 * Math.PI * pitch * t).toFloat() * volume
                    }
                } else {
                    val sustainAge = sampleIdx - chordStartIdx
                    val swellFade = 1.0f - (sustainAge.toFloat() / (totalSamples - chordStartIdx))
                    for (pitch in notes) {
                        value += sin(2.0 * Math.PI * pitch * (sampleIdx * dt)).toFloat() * 0.3f * swellFade
                    }
                    val reverbDelay = (500 * SAMPLE_RATE / 1000f).toInt()
                    if (sampleIdx >= reverbDelay) {
                        val reverbFreq = notes[sampleIdx % notes.size]
                        value += sin(2.0 * Math.PI * reverbFreq * ((sampleIdx - reverbDelay) * dt)).toFloat() * 0.15f * swellFade
                    }
                }
                bufferLeft[i] += value
                bufferRight[i] += value
                sampleIdx++
            }
        }
    }

    class LaserBeamVoice : AudioVoice {
        private var sampleIdx = 0
        private val totalA = (300 * SAMPLE_RATE / 1000f).toInt()
        private val totalB = totalA + (60 * SAMPLE_RATE / 1000f).toInt()
        private val totalC = totalB + (150 * SAMPLE_RATE / 1000f).toInt()
        
        private val sweepSizeA = (250 * SAMPLE_RATE / 1000f).toInt()
        private val pulseSizeB = (20 * SAMPLE_RATE / 1000f).toInt()
        private val sweepSizeC = (150 * SAMPLE_RATE / 1000f).toInt()
        
        override fun isActive() = sampleIdx < totalC
        
        override fun render(bufferLeft: FloatArray, bufferRight: FloatArray, sampleCount: Int) {
            val dt = 1.0f / SAMPLE_RATE
            for (i in 0 until sampleCount) {
                if (sampleIdx >= totalC) break
                var value = 0f
                if (sampleIdx < totalA) {
                    if (sampleIdx < sweepSizeA) {
                        val ratio = sampleIdx.toFloat() / sweepSizeA
                        val freq = 300f + (1400f - 300f) * ratio
                        value = sin(2.0 * Math.PI * freq * (sampleIdx * dt)).toFloat() * 0.35f
                    }
                } else if (sampleIdx < totalB) {
                    val relative = sampleIdx - totalA
                    val sectionId = relative / pulseSizeB
                    val sectionOffset = relative % pulseSizeB
                    if (sectionId < 3 && sectionOffset < (pulseSizeB * 0.6f)) {
                        value = sin(2.0 * Math.PI * 2000.0 * (relative * dt)).toFloat() * 0.45f
                    }
                } else {
                    val relative = sampleIdx - totalB
                    if (relative < sweepSizeC) {
                        val ratio = relative.toFloat() / sweepSizeC
                        val freq = 1400f - (1400f - 300f) * ratio
                        value = sin(2.0 * Math.PI * freq * (relative * dt)).toFloat() * 0.35f * (1.0f - ratio)
                    }
                }
                bufferLeft[i] += value
                bufferRight[i] += value
                sampleIdx++
            }
        }
    }

    class RotatingBladeVoice : AudioVoice {
        private var sampleIdx = 0
        private val totalSamples = (80 * SAMPLE_RATE / 1000f).toInt()
        
        override fun isActive() = sampleIdx < totalSamples
        
        override fun render(bufferLeft: FloatArray, bufferRight: FloatArray, sampleCount: Int) {
            val dt = 1.0f / SAMPLE_RATE
            for (i in 0 until sampleCount) {
                if (sampleIdx >= totalSamples) break
                val decayEnv = 1.0f - (sampleIdx.toFloat() / totalSamples)
                val s1 = sin(2.0 * Math.PI * 880.0 * (sampleIdx * dt)).toFloat()
                val s2 = sin(2.0 * Math.PI * 1100.0 * (sampleIdx * dt)).toFloat()
                val signal = (s1 + s2) * 0.25f * decayEnv
                bufferLeft[i] += signal
                bufferRight[i] += signal
                sampleIdx++
            }
        }
    }

    class GemCollectVoice : AudioVoice {
        private var sampleIdx = 0
        private val totalSamples = (90 * SAMPLE_RATE / 1000f).toInt()
        
        override fun isActive() = sampleIdx < totalSamples
        
        override fun render(bufferLeft: FloatArray, bufferRight: FloatArray, sampleCount: Int) {
            val dt = 1.0f / SAMPLE_RATE
            for (i in 0 until sampleCount) {
                if (sampleIdx >= totalSamples) break
                val ratio = sampleIdx.toFloat() / totalSamples
                val f1 = 1760f + (1900f - 1760f) * ratio
                val f2 = f1 * (2093f / 1760f)
                val env = 1.0f - ratio
                
                val s1 = sin(2.0 * Math.PI * f1 * (sampleIdx * dt)).toFloat() * 0.18f
                val s2 = sin(2.0 * Math.PI * f2 * (sampleIdx * dt)).toFloat() * 0.18f
                val signal = (s1 + s2) * env
                bufferLeft[i] += signal
                bufferRight[i] += signal
                sampleIdx++
            }
        }
    }

    class ShieldPowerupVoice : AudioVoice {
        private var sampleIdx = 0
        private val noteSamples = (120 * SAMPLE_RATE / 1000f).toInt()
        private val overlapSamples = (60 * SAMPLE_RATE / 1000f).toInt()
        private val totalSamples = noteSamples + overlapSamples
        private var lpState = 0f
        
        override fun isActive() = sampleIdx < totalSamples
        
        override fun render(bufferLeft: FloatArray, bufferRight: FloatArray, sampleCount: Int) {
            val dt = 1.0f / SAMPLE_RATE
            for (i in 0 until sampleCount) {
                if (sampleIdx >= totalSamples) break
                var rawValue = 0f
                if (sampleIdx < noteSamples) {
                    val period = 1.0f / 523f
                    rawValue += (2.0f * (((sampleIdx * dt) % period) / period) - 1.0f) * 0.25f
                }
                val secondOffset = noteSamples - overlapSamples
                if (sampleIdx >= secondOffset) {
                    val t2 = (sampleIdx - secondOffset) * dt
                    val period2 = 1.0f / 784f
                    rawValue += (2.0f * ((t2 % period2) / period2) - 1.0f) * 0.25f
                }
                
                lpState = lpState * 0.3f + rawValue * 0.7f
                bufferLeft[i] += lpState
                bufferRight[i] += lpState
                sampleIdx++
            }
        }
    }

    class ShieldBreakVoice : AudioVoice {
        private var sampleIdx = 0
        private val noteSamples = (150 * SAMPLE_RATE / 1000f).toInt()
        private val totalSamples = noteSamples * 2
        
        override fun isActive() = sampleIdx < totalSamples
        
        override fun render(bufferLeft: FloatArray, bufferRight: FloatArray, sampleCount: Int) {
            val dt = 1.0f / SAMPLE_RATE
            for (i in 0 until sampleCount) {
                if (sampleIdx >= totalSamples) break
                val raw = if (sampleIdx < noteSamples) {
                    val period = 1.0f / 784f
                    (2.0f * (((sampleIdx * dt) % period) / period) - 1.0f) * 0.5f
                } else {
                    val period = 1.0f / 554f
                    val age = (sampleIdx - noteSamples) * dt
                    (2.0f * ((age % period) / period) - 1.0f) * 0.5f
                }
                
                // distortion clipping at 0.8f
                val clamped = raw.coerceIn(-0.4f, 0.4f) * 2f
                bufferLeft[i] += clamped
                bufferRight[i] += clamped
                sampleIdx++
            }
        }
    }

    class GhostOvertakeVoice : AudioVoice {
        private var sampleIdx = 0
        private val totalSamples = (180 * SAMPLE_RATE / 1000f).toInt()
        private val biquad = BiquadFilter()
        
        override fun isActive() = sampleIdx < totalSamples
        
        override fun render(bufferLeft: FloatArray, bufferRight: FloatArray, sampleCount: Int) {
            for (i in 0 until sampleCount) {
                if (sampleIdx >= totalSamples) break
                val ratio = sampleIdx.toFloat() / totalSamples
                val centerHz = 200f + (4000f - 200f) * ratio
                biquad.setBandpass(centerHz, SAMPLE_RATE.toFloat())
                
                val noise = (Random.nextFloat() * 2f - 1f) * 0.6f
                val filtered = biquad.process(noise)
                
                val panVal = -1.0f + 2.0f * ratio
                val leftGain = cos((panVal + 1.0) * Math.PI / 4.0).toFloat()
                val rightGain = sin((panVal + 1.0) * Math.PI / 4.0).toFloat()
                
                bufferLeft[i] += filtered * leftGain
                bufferRight[i] += filtered * rightGain
                sampleIdx++
            }
        }
    }

    class ReviveActivationVoice : AudioVoice {
        private var sampleIdx = 0
        private val sweepSamples = (500 * SAMPLE_RATE / 1000f).toInt()
        private val totalSamples = sweepSamples + (400 * SAMPLE_RATE / 1000f).toInt()
        private val shimmerStart = (400 * SAMPLE_RATE / 1000f).toInt()
        
        override fun isActive() = sampleIdx < totalSamples
        
        override fun render(bufferLeft: FloatArray, bufferRight: FloatArray, sampleCount: Int) {
            val dt = 1.0f / SAMPLE_RATE
            for (i in 0 until sampleCount) {
                if (sampleIdx >= totalSamples) break
                var value = 0f
                if (sampleIdx < sweepSamples) {
                    val ratio = sampleIdx.toFloat() / sweepSamples
                    val freq = 150f + (900f - 150f) * ratio
                    val period = 1.0f / freq
                    value = (2.0f * (((sampleIdx * dt) % period) / period) - 1.0f) * 0.55f
                } else {
                    val t = (sampleIdx - sweepSamples) * dt
                    val chord = sin(2.0 * Math.PI * 523.0 * t).toFloat() + sin(2.0 * Math.PI * 698.0 * t).toFloat()
                    value = chord * 0.25f
                }
                
                if (sampleIdx >= shimmerStart) {
                    val tShim = (sampleIdx - shimmerStart) * dt
                    val shimmerWave = sin(2.0 * Math.PI * 4000.0 * tShim).toFloat() + sin(2.0 * Math.PI * 5000.0 * tShim).toFloat()
                    value += shimmerWave * 0.08f
                }
                
                bufferLeft[i] += value
                bufferRight[i] += value
                sampleIdx++
            }
        }
    }

    /** Boss powering up: rising whine with a fast tremolo, getting louder until it fires (~0.6 s). */
    class BossChargeVoice : AudioVoice {
        private var sampleIdx = 0
        private val totalSamples = (600 * SAMPLE_RATE / 1000f).toInt()
        private var phase = 0.0f
        override fun isActive() = sampleIdx < totalSamples
        override fun render(bufferLeft: FloatArray, bufferRight: FloatArray, sampleCount: Int) {
            for (i in 0 until sampleCount) {
                if (sampleIdx >= totalSamples) break
                val u = sampleIdx.toFloat() / totalSamples
                val t = sampleIdx.toFloat() / SAMPLE_RATE
                val freq = 200f + 900f * u * u
                phase += (2.0 * Math.PI * freq / SAMPLE_RATE).toFloat()
                if (phase > 2f * Math.PI.toFloat()) phase -= 2f * Math.PI.toFloat()
                val tail = if (u > 0.94f) (1f - u) / 0.06f else 1f
                val env = u.pow(1.2f) * tail
                val trem = 0.75f + 0.25f * sin(2.0 * Math.PI * 28.0 * t).toFloat()
                val v = (sin(phase) * 0.35f + sin(2f * phase) * 0.15f + (Random.nextFloat() - 0.5f) * 0.08f) * env * trem
                bufferLeft[i] += v
                bufferRight[i] += v
                sampleIdx++
            }
        }
    }

    /** Gunshot: noise crack, low thump that drops in pitch, and a short rumble body (~0.4 s). */
    class BossFireVoice : AudioVoice {
        private var sampleIdx = 0
        private val totalSamples = (400 * SAMPLE_RATE / 1000f).toInt()
        private var lp = 0f
        override fun isActive() = sampleIdx < totalSamples
        override fun render(bufferLeft: FloatArray, bufferRight: FloatArray, sampleCount: Int) {
            for (i in 0 until sampleCount) {
                if (sampleIdx >= totalSamples) break
                val t = sampleIdx.toFloat() / SAMPLE_RATE
                val noise = Random.nextFloat() * 2f - 1f
                lp += 0.33f * (noise - lp)
                val crack = noise * kotlin.math.exp(-t * 70f)
                val body = lp * kotlin.math.exp(-t * 13f)
                val thump = sin(2.0 * Math.PI * (95.0 - 55.0 * t) * t).toFloat() * kotlin.math.exp(-t * 11f)
                val raw = 0.7f * crack + 0.9f * body + 0.95f * thump
                val v = kotlin.math.tanh(1.7f * raw) * 0.8f
                bufferLeft[i] += v
                bufferRight[i] += v
                sampleIdx++
            }
        }
    }

    /** Two short alarm beeps: something is about to appear. */
    class ThreatWarnVoice : AudioVoice {
        private var sampleIdx = 0
        private val totalSamples = (320 * SAMPLE_RATE / 1000f).toInt()
        override fun isActive() = sampleIdx < totalSamples
        override fun render(bufferLeft: FloatArray, bufferRight: FloatArray, sampleCount: Int) {
            for (i in 0 until sampleCount) {
                if (sampleIdx >= totalSamples) break
                val t = sampleIdx.toFloat() / SAMPLE_RATE
                val on = t < 0.09f || (t >= 0.16f && t < 0.25f)
                if (on) {
                    val lt = if (t < 0.09f) t else t - 0.16f
                    val env = (1f - lt / 0.09f).coerceIn(0f, 1f)
                    val sn = sin(2.0 * Math.PI * 1000.0 * t).toFloat()
                    val v = (if (sn > 0f) 0.2f else -0.2f) + sn * 0.18f
                    bufferLeft[i] += v * env
                    bufferRight[i] += v * env
                }
                sampleIdx++
            }
        }
    }

    /** Shadow clone slipping in: filtered noise sweeping up, panning right to left. */
    class WhooshVoice : AudioVoice {
        private var sampleIdx = 0
        private val totalSamples = (380 * SAMPLE_RATE / 1000f).toInt()
        private val biquad = BiquadFilter()
        override fun isActive() = sampleIdx < totalSamples
        override fun render(bufferLeft: FloatArray, bufferRight: FloatArray, sampleCount: Int) {
            for (i in 0 until sampleCount) {
                if (sampleIdx >= totalSamples) break
                val u = sampleIdx.toFloat() / totalSamples
                biquad.setBandpass(300f + 2400f * u, SAMPLE_RATE.toFloat(), 1.4f)
                val env = sin(Math.PI * u).toFloat()
                val v = biquad.process((Random.nextFloat() * 2f - 1f) * 0.9f) * env
                val pan = 1.0 - 2.0 * u
                bufferLeft[i] += v * cos((pan + 1.0) * Math.PI / 4.0).toFloat()
                bufferRight[i] += v * sin((pan + 1.0) * Math.PI / 4.0).toFloat()
                sampleIdx++
            }
        }
    }

    /** Small energy bolt: fast downward ping. */
    class ZapVoice : AudioVoice {
        private var sampleIdx = 0
        private val totalSamples = (140 * SAMPLE_RATE / 1000f).toInt()
        private var phase = 0.0f
        override fun isActive() = sampleIdx < totalSamples
        override fun render(bufferLeft: FloatArray, bufferRight: FloatArray, sampleCount: Int) {
            for (i in 0 until sampleCount) {
                if (sampleIdx >= totalSamples) break
                val t = sampleIdx.toFloat() / SAMPLE_RATE
                phase += (2.0 * Math.PI * (1900.0 - 9000.0 * t).coerceAtLeast(300.0) / SAMPLE_RATE).toFloat()
                val v = sin(phase) * kotlin.math.exp(-t * 22f) * 0.45f
                bufferLeft[i] += v
                bufferRight[i] += v
                sampleIdx++
            }
        }
    }

    /** One synthesized combat sound. kind: 0 shot, 1 smash, 2 boss hit, 3 deflect, 4 overheat, 5 boss down, 6 shield up, 7 enrage. */
    class CombatVoice(val kind: Int, val p: Float = 0f) : AudioVoice {
        private var idx = 0
        private val total = (when (kind) { 0 -> if (p > 0.5f) 240 else 150; 1 -> 320; 2 -> 240; 3 -> 200; 4 -> 420; 5 -> 1000; 6 -> 280; 7 -> 450; 8 -> 450; 9 -> 520; 10 -> 850; 11 -> 1100; 12 -> 700; 13 -> 260; 14 -> 650; else -> 150 } * SAMPLE_RATE / 1000f).toInt()
        private var ph = 0.0
        private var lp = 0f
        private val pan = Random.nextFloat() * 0.5f - 0.25f
        private val gl = 1f - maxOf(0f, pan)
        private val gr = 1f + minOf(0f, pan)
        override fun isActive() = idx < total
        override fun render(bufferLeft: FloatArray, bufferRight: FloatArray, sampleCount: Int) {
            val tau = 2.0 * Math.PI
            for (i in 0 until sampleCount) {
                if (idx >= total) break
                val t = idx.toFloat() / SAMPLE_RATE
                val n = Random.nextFloat() * 2f - 1f
                val e = { d: Float -> kotlin.math.exp(-t * d) }
                var v = 0f
                when (kind) {
                    0 -> {
                        val power = p > 0.5f
                        val f = if (power) 900f * e(9f) + 140f else 1900f * e(16f) + 480f
                        ph += tau * f / SAMPLE_RATE
                        v = (sin(ph).toFloat() * 0.7f + sin(ph * 2).toFloat() * 0.2f) * e(if (power) 12f else 22f)
                        v += n * e(400f) * 0.35f
                        if (power) v += sin(tau * 70.0 * t).toFloat() * e(14f) * 0.5f
                        v *= 0.5f
                    }
                    1 -> {
                        lp += (0.55f * e(9f) + 0.04f) * (n - lp)
                        v = lp * e(11f) * 1.6f + n * e(300f) * 0.3f
                        ph += tau * (45.0 + 110.0 * e(20f)) / SAMPLE_RATE
                        v += sin(ph).toFloat() * e(16f) * 0.6f
                        val pings = floatArrayOf(2300f, 3100f, 4200f); val starts = floatArrayOf(0.015f, 0.04f, 0.07f)
                        for (k in 0..2) if (t > starts[k]) { val tt = t - starts[k]; v += sin(tau * pings[k] * tt).toFloat() * kotlin.math.exp(-tt * 38f) * 0.16f }
                        v *= 0.55f
                    }
                    2 -> {
                        val base = 410f * (1f + 0.18f * p)
                        val ratio = floatArrayOf(1f, 2.49f, 4.21f, 6.07f); val dec = floatArrayOf(14f, 20f, 28f, 40f); val amp = floatArrayOf(0.5f, 0.3f, 0.2f, 0.12f)
                        for (k in 0..3) v += sin(tau * base * ratio[k] * t).toFloat() * e(dec[k]) * amp[k]
                        v += sin(tau * 95.0 * t).toFloat() * e(28f) * 0.5f + n * e(500f) * 0.3f
                        v *= 0.6f
                    }
                    3 -> {
                        ph += tau * (3400f * e(9f) + 1100f) / SAMPLE_RATE
                        v = sin(ph).toFloat() * e(22f) * 0.5f + sin(tau * 5200.0 * t).toFloat() * e(45f) * 0.25f + sin(tau * 7300.0 * t).toFloat() * e(60f) * 0.12f
                    }
                    4 -> {
                        ph += tau * (240f * e(3.5f) + 50f) / SAMPLE_RATE
                        val saw = 2f * (((ph / tau) % 1.0).toFloat()) - 1f
                        val trem = 0.6f + 0.4f * sin(tau * 26.0 * t).toFloat()
                        lp += 0.3f * (n - lp)
                        v = saw * trem * e(4f) * 0.35f + lp * e(6f) * 0.25f
                    }
                    5 -> {
                        lp += (0.5f * e(3f) + 0.02f) * (n - lp)
                        v = lp * e(3.2f) * 2.4f
                        ph += tau * (30.0 + 130.0 * e(5f)) / SAMPLE_RATE
                        v += sin(ph).toFloat() * e(3f) * 0.7f
                        if (t > 0.12f) { val tt = t - 0.12f; v += sin(tau * (500.0 + 2000.0 * minOf(1f, tt / 0.5f)) * tt).toFloat() * kotlin.math.exp(-tt * 5f) * 0.12f }
                        v *= 0.6f
                    }
                    6 -> {
                        ph += tau * (260f + 1200f * minOf(1f, t / 0.2f)) / SAMPLE_RATE
                        val env = sin(Math.PI * t / 0.28).toFloat().coerceAtLeast(0f)
                        v = (sin(ph).toFloat() * 0.4f + sin(ph * 1.5).toFloat() * 0.15f) * env
                    }
                    7 -> {
                        ph += tau * (85f * e(1.2f) + 45f) / SAMPLE_RATE
                        val saw = 2f * (((ph / tau) % 1.0).toFloat()) - 1f
                        v = (saw * 1.8f).coerceIn(-1f, 1f) * e(3f) * 0.35f * (0.7f + 0.3f * sin(tau * 18.0 * t).toFloat())
                    }
                    8 -> {
                        val env = sin(Math.PI * t / 0.45).toFloat().coerceAtLeast(0f)
                        lp += (0.05f + 0.5f * minOf(1f, t / 0.2f)) * (n - lp)
                        ph += tau * (180f + 900f * minOf(1f, t / 0.25f)) / SAMPLE_RATE
                        v = (lp * 1.8f + sin(ph).toFloat() * 0.25f) * env
                    }
                    9 -> {
                        ph += tau * (220f + 1600f * minOf(1f, t / 0.35f)) / SAMPLE_RATE
                        v = sin(ph).toFloat() * e(5f) * 0.4f + sin(ph * 2).toFloat() * e(8f) * 0.2f
                    }
                    10 -> {
                        val notes = floatArrayOf(392f, 523f, 659f, 784f, 1046f)
                        for (k in 0..4) { val st = k * 0.1f; if (t > st) { val tt = t - st; v += sin(tau * notes[k] * tt).toFloat() * kotlin.math.exp(-tt * 9f) * 0.22f } }
                        ph += tau * (80f + 80f * minOf(1f, t / 0.6f)) / SAMPLE_RATE
                        v += sin(ph).toFloat() * e(3f) * 0.25f
                    }
                    11 -> {
                        ph += tau * (30f + 90f * e(4f)) / SAMPLE_RATE
                        lp += (0.4f * e(5f) + 0.02f) * (n - lp)
                        v = sin(ph).toFloat() * e(2.5f) * 0.8f + lp * e(4f) * 1.6f
                        val f = if (((t / 0.15f).toInt() % 2) == 0) 440.0 else 330.0
                        val saw = 2f * (((f * t) % 1.0).toFloat()) - 1f
                        v += saw * e(1.5f) * 0.12f
                        v *= 0.6f
                    }
                    12 -> {
                        // two metal clacks (magazine seating) + a rising charge chime
                        for (k in 0..1) { val st = k * 0.09f; if (t > st) { val tt = t - st; v += (n * kotlin.math.exp(-tt * 260f) * 0.4f) + sin(tau * (900.0 + 300.0 * k) * tt).toFloat() * kotlin.math.exp(-tt * 55f) * 0.3f } }
                        if (t > 0.22f) { val tt = t - 0.22f; val notes = floatArrayOf(523f, 659f, 784f, 1046f); for (q in 0..3) { val s2 = q * 0.07f; if (tt > s2) { val t3 = tt - s2; v += sin(tau * notes[q] * t3).toFloat() * kotlin.math.exp(-t3 * 8f) * 0.2f } } }
                    }
                    13 -> {
                        ph += tau * (140f + 420f * minOf(1f, t / 0.12f) - 200f * maxOf(0f, (t - 0.12f) / 0.14f)) / SAMPLE_RATE
                        lp += 0.2f * (n - lp)
                        v = (sin(ph).toFloat() * 0.5f + lp * 0.5f) * e(9f) * 0.8f
                    }
                    14 -> {
                        lp += (0.45f * e(7f) + 0.03f) * (n - lp)
                        ph += tau * (60.0 + 500.0 * e(9f)) / SAMPLE_RATE
                        v = lp * e(6f) * 1.4f + sin(ph).toFloat() * e(5f) * 0.6f
                        for (q in 0..3) { val s2 = q * 0.05f; if (t > s2) { val t3 = t - s2; v += sin(tau * (1200.0 + 400.0 * q) * t3).toFloat() * kotlin.math.exp(-t3 * 14f) * 0.15f } }
                        v *= 0.7f
                    }
                }
                bufferLeft[i] += v * gl
                bufferRight[i] += v * gr
                idx++
            }
        }
    }

    /** AIR SUPPORT sounds (Stage 145). kind: 0 radio open (squelch + static), 1 radio close, 2 helicopter fly-by (far, close, far), 3 jet fly-by, 4 minigun burst, 5 missile launch, 6 big explosion, 7 lock-on beeps. p = length in seconds for 2, 3 and 4. */
    class AirVoice(val kind: Int, val p: Float = 3f) : AudioVoice {
        private var idx = 0
        private val total = (when (kind) { 0 -> 0.30f; 1 -> 0.24f; 2, 3, 4 -> p; 5 -> 0.9f; 6 -> 1.2f; else -> 0.35f } * SAMPLE_RATE).toInt()
        private var ph = 0.0; private var bph = 0.0; private var lp = 0f; private var lp2 = 0f
        private val pan = Random.nextFloat() * 0.3f - 0.15f
        private val gl = 1f - maxOf(0f, pan); private val gr = 1f + minOf(0f, pan)
        override fun isActive() = idx < total
        override fun render(bufferLeft: FloatArray, bufferRight: FloatArray, sampleCount: Int) {
            val tau = 2.0 * Math.PI
            for (i in 0 until sampleCount) {
                if (idx >= total) break
                val t = idx.toFloat() / SAMPLE_RATE
                val n = Random.nextFloat() * 2f - 1f
                val e = { d: Float -> kotlin.math.exp(-t * d) }
                var v = 0f
                when (kind) {
                    0 -> {   // mic key: click, hiss burst, static tail
                        lp += 0.35f * (n - lp)
                        v = (n - lp) * 0.5f * e(9f) + (if (t < 0.045f) sin(tau * 1500.0 * t).toFloat() * 0.3f else 0f) + (n - lp) * 0.16f * e(5f)
                    }
                    1 -> {   // mic release: tiny chirp + hiss
                        lp += 0.35f * (n - lp)
                        v = (n - lp) * 0.4f * e(14f) + (if (t < 0.06f) sin(tau * 950.0 * t).toFloat() * 0.25f * (1f - t / 0.06f) else 0f)
                    }
                    2 -> {   // helicopter: blade thump + engine whine, grows as it comes, pitch drops once it passes
                        val x = t / p
                        val env = Math.pow(sin(Math.PI * x).toDouble().coerceAtLeast(0.0), 1.4).toFloat()
                        val dop = 1.14f - 0.28f * x
                        bph += tau * 14.0 * dop / SAMPLE_RATE
                        val th = Math.pow(sin(bph).coerceAtLeast(0.0), 5.0).toFloat()
                        lp += 0.07f * (n - lp)
                        v = (th * 0.9f + lp * 0.9f + sin(tau * 62.0 * dop * t).toFloat() * 0.22f + (n - lp) * th * 0.18f * env) * env * 0.8f
                    }
                    3 -> {   // jet: rising roar, scream and fall
                        val x = t / p
                        val env = Math.pow(sin(Math.PI * x).toDouble().coerceAtLeast(0.0), 2.2).toFloat()
                        val dop = 1.3f - 0.55f * x
                        lp += (0.12f + 0.5f * env) * (n - lp)
                        lp2 += 0.02f * (n - lp2)
                        ph += tau * 520.0 * dop / SAMPLE_RATE
                        v = (lp * 1.2f + (n - lp) * 0.3f * env + lp2 * 1.4f + sin(ph).toFloat() * 0.07f) * env * 0.8f
                    }
                    4 -> {   // minigun: spin-up, 70 rounds per second, spin-down
                        val gate = minOf(1f, t / 0.12f) * minOf(1f, (p - t) / 0.18f).coerceAtLeast(0f)
                        val loc = (t * 70f) % 1f / 70f
                        v = (n * kotlin.math.exp(-loc * 700f) * 0.7f + sin(tau * 170.0 * loc).toFloat() * kotlin.math.exp(-loc * 260f) * 0.7f) * gate * 0.55f
                    }
                    5 -> {   // missile launch: ignition thump + hissing climb
                        lp += (0.08f + 0.45f * minOf(1f, t / 0.45f)) * (n - lp)
                        ph += tau * (90.0 + 520.0 * minOf(1f, t / 0.6f)) / SAMPLE_RATE
                        v = lp * 1.3f * minOf(1f, t / 0.04f) * e(2.0f) + sin(ph).toFloat() * 0.12f * e(2.5f) + sin(tau * 60.0 * t).toFloat() * e(9f) * 0.6f
                    }
                    6 -> {   // explosion
                        lp += (0.5f * e(3f) + 0.02f) * (n - lp)
                        ph += tau * (30.0 + 120.0 * e(5f)) / SAMPLE_RATE
                        v = (lp * e(3f) * 2.6f + sin(ph).toFloat() * e(2.6f) * 0.9f) * 0.6f
                    }
                    else -> {   // radar lock-on: three short beeps
                        val b = (t < 0.05f) || (t in 0.11f..0.16f) || (t in 0.22f..0.27f)
                        if (b) v = sin(tau * 1800.0 * t).toFloat() * 0.3f
                    }
                }
                bufferLeft[i] += v * gl
                bufferRight[i] += v * gr
                idx++
            }
        }
    }

    // DSP Class Helpers
    class BiquadFilter {
        private var x1 = 0f; private var x2 = 0f; private var y1 = 0f; private var y2 = 0f
        private var b0 = 0f; private var b1 = 0f; private var b2 = 0f; private var a1 = 0f; private var a2 = 0f
        
        fun setBandpass(centerFreq: Float, sampleRate: Float, q: Float = 1.0f) {
            val w0 = (2.0 * Math.PI * centerFreq / sampleRate).toFloat()
            val alpha = (sin(w0.toDouble()) / (2.0 * q)).toFloat()
            val cosW = cos(w0)
            
            val a0 = 1f + alpha
            b0 = alpha / a0
            b1 = 0f
            b2 = -alpha / a0
            a1 = -2f * cosW / a0
            a2 = (1f - alpha) / a0
        }
        
        fun process(x: Float): Float {
            val y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
            x2 = x1
            x1 = x
            y2 = y1
            y1 = y
            return y
        }
    }

    class DynamicsCompressor(val threshold: Float = 0.7f, val slope: Float = 0.1f) {
        fun process(sample: Float): Float {
            val absVal = abs(sample)
            if (absVal <= threshold) return sample
            val compressed = threshold + (absVal - threshold) * slope
            return if (sample > 0) compressed else -compressed
        }
    }
}
