package com.neonrush.game

import java.util.concurrent.CopyOnWriteArrayList
import kotlin.random.Random

/**
 * SHIELD LAYERS (Stage 152). The main boss wears a stack of visible shield layers, outermost first. Damage has to strip them one by one before the hull is touched.
 * Every shot has a DAMAGE CLASS: 1 = a normal bolt, 2 = an armor round, 3 = a missile (air support, bomb). A layer's type decides how much of that damage it takes:
 *   PLASMA  (cyan)    takes everything.
 *   ARMOR   (amber)   plates: class 1 does 15%, class 2+ does full. Armor rounds or missiles break it.
 *   REFLECT (magenta) a mirror: class 1 does 25%, class 2 does 50%, class 3 does full. Missiles go through.
 *   PHASE   (violet)  flickers out of reach: only 10% unless the boss is lunging (then 150%).
 *   REGEN   (green)   heals itself when you stop hitting it: keep the pressure on.
 * How many and which: by the boss zone's 15-zone tier and the Siege round (see [build]); new types appear gradually (tier 0 plasma/armor, tier 1 + reflect, tier 2 + phase, tier 3 + regen).
 * Passive drain (a player with no ammo) also wears layers down as a class 3 hit, so nobody is walled for good. EASY has no layers.
 */
class Layer(val type: Int, val max: Float) {
    @Volatile var hp = max
    @Volatile var flash = 0f
    @Volatile var lastHitMs = 0L
}

object ShieldLayers {
    const val PLASMA = 0; const val ARMOR = 1; const val REFLECT = 2; const val PHASE = 3; const val REGEN = 4
    val NAMES = arrayOf("PLASMA", "ARMOR", "REFLECT", "PHASE", "REGEN")
    val TIPS = arrayOf("ANY SHOT BREAKS IT", "ARMOR ROUNDS OR MISSILES BREAK IT", "MIRROR · MISSILES GO THROUGH", "ONLY HITTABLE WHILE IT LUNGES", "IT HEALS · KEEP FIRING")
    val COLS = longArrayOf(0xFF40E0FF, 0xFFFFB300, 0xFFFF4DD2, 0xFFB388FF, 0xFF69F0AE)
    private val HP = floatArrayOf(0.07f, 0.12f, 0.10f, 0.09f, 0.10f)

    val main = CopyOnWriteArrayList<Layer>()
    @Volatile var hitSeq = 0; @Volatile var hitType = 0
    @Volatile var breakSeq = 0; @Volatile var breakType = 0
    @Volatile var bounceSeq = 0
    @Volatile var builtSeq = 0
    @Volatile var tierSeen = 0

    fun reset() { main.clear(); hitSeq = 0; breakSeq = 0; bounceSeq = 0; builtSeq = 0 }
    fun up(): Boolean = main.any { it.hp > 0f }
    fun top(): Layer? = main.firstOrNull { it.hp > 0f }
    fun count(): Int = main.count { it.hp > 0f }

    /** Layers for a fresh boss: outermost first. */
    fun build(zone: Int, round: Int) {
        main.clear()
        if (Siege.special || Siege.dmgMul <= 0f) { builtSeq += 1; return }
        val tier = Fleet.tierOf(zone)
        val depth = (1 + (round - 1) / 2 + tier / 2).coerceAtMost(4)
        val pool = ArrayList<Int>()
        pool.add(PLASMA)
        if (round >= 2 || tier >= 1) pool.add(ARMOR)
        if (tier >= 1) pool.add(REFLECT)
        if (tier >= 2) pool.add(PHASE)
        if (tier >= 3) pool.add(REGEN)
        val rnd = Random(zone * 31 + round * 7 + tier * 131)
        var prev = -1
        for (i in 0 until depth) {
            var t = pool[rnd.nextInt(pool.size)]
            var guard = 0
            while (t == prev && pool.size > 1 && guard++ < 6) t = pool[rnd.nextInt(pool.size)]
            main.add(Layer(t, HP[t])); prev = t
        }
        builtSeq += 1
    }

    /** The boss steps back on the field: a new boss gets a new stack; a wounded one heals a little (a long withdrawal also restores broken layers weakly). */
    fun onEntry(fresh: Boolean, repair: Float, zone: Int) {
        if (fresh) { build(zone, Siege.round); return }
        for (l in main) {
            if (l.hp <= 0f && repair >= 0.3f) l.hp = l.max * 0.4f
            else if (l.hp > 0f) l.hp = (l.hp + l.max * repair * 2f).coerceAtMost(l.max)
        }
    }

    private fun mult(type: Int, cls: Int): Float = when (type) {
        PLASMA -> 1f
        ARMOR -> if (cls >= 2) 1f else 0.15f
        REFLECT -> if (cls >= 3) 1f else if (cls == 2) 0.5f else 0.25f
        PHASE -> if (BossLunge.vulnerable) 1.5f else 0.1f
        else -> 1f
    }

    /** [dmg] (hull fractions, already scaled by the round) of class [cls] hits the stack. Returns what is left for the hull (0 while a layer holds). */
    fun absorb(dmg: Float, cls: Int, fx: Boolean = true): Float {
        var left = dmg
        var guard = 0
        while (left > 0f && guard++ < 6) {
            val l = top() ?: return left
            val m = mult(l.type, cls)
            val eff = left * m
            if (fx) { l.flash = 0.35f; l.lastHitMs = System.currentTimeMillis(); hitType = l.type; hitSeq += 1; if (m < 0.2f && l.type != ARMOR) bounceSeq += 1 }
            if (eff >= l.hp) {
                val over = eff - l.hp
                l.hp = 0f; breakType = l.type; breakSeq += 1
                left = if (m > 0f) over / m else 0f
                if (over <= 0f) return 0f
            } else { l.hp -= eff; return 0f }
        }
        return if (top() == null) left else 0f
    }

    /** Called every frame by the overlay: flash decay and the REGEN layers healing when left alone. */
    fun step(dt: Float) {
        val now = System.currentTimeMillis()
        for (l in main) {
            if (l.flash > 0f) l.flash = (l.flash - dt * 3f).coerceAtLeast(0f)
            if (l.type == REGEN && l.hp > 0f && l.hp < l.max && now - l.lastHitMs > 2500L) l.hp = (l.hp + l.max * 0.35f * dt).coerceAtMost(l.max)
        }
    }
}
