package com.neonrush.game

data class Skin(
    val id: String,
    val name: String,
    val description: String,
    val previewImageRes: Int,
    val priceUsd: String,
    val requiresPro: Boolean,
    val unlockWorldId: Int?,
    val pilotFrameOverrides: List<Int>? = null,
    val purchasable: Boolean = true // false = can only be earned (mission reward), never bought
)

object Skins {
    val ALL: List<Skin> = listOf(
        Skin(
            id = "default",
            name = "Standard Issue",
            description = "The pilot's original gear. Reliable, unremarkable, free.",
            previewImageRes = R.drawable.pilot_run_1,
            priceUsd = "Free",
            requiresPro = false,
            unlockWorldId = null
        ),
        Skin(
            id = "blackout_runner",
            name = "Blackout Runner",
            description = "Salvaged war-front plating. Clear Blackout Front to earn the right to buy it.",
            previewImageRes = R.drawable.pilot_run_1, // placeholder — swap when art exists
            priceUsd = "$2.99",
            requiresPro = false,
            unlockWorldId = 1
        ),
        Skin(
            id = "signal_ghost",
            name = "Signal Ghost",
            description = "Something from the station clings to this suit. Clear Derelict Signal without a revive to earn the right to buy it.",
            previewImageRes = R.drawable.pilot_run_1, // placeholder
            priceUsd = "$3.99",
            requiresPro = false,
            unlockWorldId = 2
        ),
        Skin(
            id = "convict_grey",
            name = "Convict Grey",
            description = "Prison-issue fatigues, worn since the break. Clear Cell Block Zero on Medium+ to earn the right to buy it.",
            previewImageRes = R.drawable.pilot_run_1, // placeholder
            priceUsd = "$2.99",
            requiresPro = false,
            unlockWorldId = 3
        ),
        Skin(
            id = "apex_predator",
            name = "Apex Predator",
            description = "You outran the thing that was hunting you. Clear Green Hell on Hard+ to earn the right to buy it.",
            previewImageRes = R.drawable.pilot_run_1, // placeholder
            priceUsd = "$3.99",
            requiresPro = false,
            unlockWorldId = 4
        ),
        Skin(
            id = "echo_drift",
            name = "Echo Drift",
            description = "Your reflection moves a half-second late. Earned by clearing Ghost Protocol (New Game+).",
            previewImageRes = R.drawable.pilot_run_1, // placeholder
            priceUsd = "Free (story reward)",
            requiresPro = true,
            unlockWorldId = 9,
            purchasable = false
        ),
        Skin(
            id = "relic_core",
            name = "Relic Core",
            description = "Salvaged from the vault's last clean backup. Earned by clearing Deep Archive (New Game+).",
            previewImageRes = R.drawable.pilot_run_1, // placeholder
            priceUsd = "Free (story reward)",
            requiresPro = true,
            unlockWorldId = 10,
            purchasable = false
        ),
        Skin(
            id = "signal_zero",
            name = "Signal Zero",
            description = "Worn by whoever answered the final transmission. Earned by clearing Last Transmission (New Game+).",
            previewImageRes = R.drawable.pilot_run_1, // placeholder
            priceUsd = "Free (story reward)",
            requiresPro = true,
            unlockWorldId = 11,
            purchasable = false
        ),
        Skin(
            id = "chrome_reaper",
            name = "Chrome Reaper",
            description = "No story behind this one. Just looks incredible.",
            previewImageRes = R.drawable.pilot_run_1, // placeholder
            priceUsd = "$1.99",
            requiresPro = false,
            unlockWorldId = null
        ),
        Skin(
            id = "solar_flare",
            name = "Solar Flare",
            description = "Blindingly bright. Pure vanity.",
            previewImageRes = R.drawable.pilot_run_1, // placeholder
            priceUsd = "$1.99",
            requiresPro = false,
            unlockWorldId = null
        ),
        Skin(
            id = "void_walker",
            name = "Void Walker",
            description = "Matte black plating that seems to swallow the neon around it.",
            previewImageRes = R.drawable.pilot_run_1, // placeholder
            priceUsd = "$1.99",
            requiresPro = false,
            unlockWorldId = null
        ),
        Skin(
            id = "toxic_bloom",
            name = "Toxic Bloom",
            description = "Acid-green venting, straight out of the reserve's worst nightmares.",
            previewImageRes = R.drawable.pilot_run_1, // placeholder
            priceUsd = "$2.99",
            requiresPro = false,
            unlockWorldId = null
        ),
        Skin(
            id = "circuit_breaker",
            name = "Circuit Breaker",
            description = "Live current running through every seam. Handle with care.",
            previewImageRes = R.drawable.pilot_run_1, // placeholder
            priceUsd = "$2.99",
            requiresPro = false,
            unlockWorldId = null
        ),
        Skin(
            id = "golden_protocol",
            name = "Golden Protocol",
            description = "Command-grade gold plating. Reserved for the pilots who never miss.",
            previewImageRes = R.drawable.pilot_run_1, // placeholder
            priceUsd = "$3.99",
            requiresPro = false,
            unlockWorldId = null
        ),
        Skin(
            id = "glitch_core",
            name = "Glitch Core",
            description = "Something's wrong with this suit's signal — and it looks incredible.",
            previewImageRes = R.drawable.pilot_run_1, // placeholder
            priceUsd = "$3.99",
            requiresPro = false,
            unlockWorldId = null
        ),
        Skin(
    id = "prism_vanguard",
    name = "Prism Vanguard",
    description = "Refracts every light source into a walking rainbow. Not subtle.",
    previewImageRes = R.drawable.pilot_run_1, // placeholder — swap when art exists
    priceUsd = "$2.99",
    requiresPro = false,
    unlockWorldId = null
),
Skin(
    id = "iron_wraith",
    name = "Iron Wraith",
    description = "Salvaged battle-plate, dented but unbroken.",
    previewImageRes = R.drawable.pilot_run_1, // placeholder — swap when art exists
    priceUsd = "$1.99",
    requiresPro = false,
    unlockWorldId = null
),
Skin(
    id = "nova_sprint",
    name = "Nova Sprint",
    description = "Built for speed. Leaves a trail even standing still.",
    previewImageRes = R.drawable.pilot_run_1, // placeholder — swap when art exists
    priceUsd = "$2.99",
    requiresPro = false,
    unlockWorldId = null
),
Skin(
    id = "obsidian_pulse",
    name = "Obsidian Pulse",
    description = "Black glass plating with a heartbeat of light beneath it.",
    previewImageRes = R.drawable.pilot_run_1, // placeholder — swap when art exists
    priceUsd = "$3.99",
    requiresPro = false,
    unlockWorldId = null
),
Skin(
    id = "zenith_circuit",
    name = "Zenith Circuit",
    description = "Worn only by pilots who cleared every objective this month. Not for sale.",
    previewImageRes = R.drawable.pilot_run_1, // placeholder — swap when art exists
    priceUsd = "🏆 Monthly Mission Reward",
    requiresPro = false,
    unlockWorldId = null,
    purchasable = false
),
        Skin(
            id = "eternal_flame",
            name = "Eternal Flame",
            description = "Kept burning by pilots who showed up 30 days in a row. Not for sale.",
            previewImageRes = R.drawable.pilot_run_1,
            priceUsd = "🔥 30-Day Streak Reward",
            requiresPro = false,
            unlockWorldId = null,
            purchasable = false
        )
    )

    fun isUnlocked(skin: Skin, isPro: Boolean, worldsCompleted: Set<Int>): Boolean {
        if (skin.unlockWorldId == null) return true
        if (skin.requiresPro && !isPro) return false
        return worldsCompleted.contains(skin.unlockWorldId)
    }
}
