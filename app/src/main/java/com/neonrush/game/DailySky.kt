package com.neonrush.game

import java.util.Calendar

/**
 * LIVING WORLD: every day has its own sky, the same for every player (worked out from the date, no server). The sky changes how
 * every world looks (code-drawn overlay in ui/DailySkyUi.kt) and pays a gem bonus on the gems earned in that day's runs;
 * the harder it is to see, the bigger the bonus. Pro pilots can also see tomorrow's sky.
 */
enum class Sky(val label: String, val emoji: String, val gemMul: Float, val blurb: String, val argb: Long) {
    CLEAR("CLEAR SKIES", "☀️", 1.00f, "A calm day. Fly clean.", 0xFF7DF9FF),
    SANDSTORM("SANDSTORM", "🏜️", 1.20f, "Blowing sand. Bigger gem rewards.", 0xFFFFB74D),
    AURORA("AURORA", "🌌", 1.15f, "Ribbons of light. Gems glow brighter.", 0xFF69F0AE),
    FOG("DEEP FOG", "🌫️", 1.20f, "Hard to see ahead. Bigger gem rewards.", 0xFFB0BEC5),
    METEORS("METEOR SHOWER", "☄️", 1.15f, "Falling stars. Bonus gems tonight.", 0xFFFF8A65),
    NEON_RAIN("NEON RAIN", "🌧️", 1.10f, "Glowing rain. A little extra gems.", 0xFFFF4DD2);

    val bonusPct: Int get() = ((gemMul - 1f) * 100f + 0.5f).toInt()

    companion object {
        private fun dayNum(offset: Int = 0): Int {
            val c = Calendar.getInstance().also { it.add(Calendar.DAY_OF_YEAR, offset) }
            return c.get(Calendar.YEAR) * 366 + c.get(Calendar.DAY_OF_YEAR)
        }
        private fun pick(d: Int): Sky {
            var x = d * 374761393 + 668265263
            x = (x xor (x ushr 13)) * 1274126177
            x = x xor (x ushr 16)
            return values()[Math.floorMod(x, values().size)]
        }
        fun today(): Sky = pick(dayNum(0))
        fun tomorrow(): Sky = pick(dayNum(1))
        /** Multiplier for the gems a run earns today. */
        fun gemMul(): Float = today().gemMul
    }
}
