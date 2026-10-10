package com.neonrush.game

data class World(
    val id: Int,
    val title: String,
    val subtitle: String,
    val startZone: Int,
    val endZone: Int,
    val openingText: String,
    val midRunText: String,
    val bossIntroText: String,
    val endingText: String,
    val requiresPro: Boolean,
    val environmentIds: List<Int>, // indices into ZoneGenerator.ENVIRONMENTS matching this world's theme
    val worldFamily: Int = id, // groups phases of the same world together (e.g. Blackout Front phases 1-4 all share worldFamily=1)
    val phase: Int = 1
)
object Worlds {

    val ALL: List<World> = listOf(
        World(
            id = 1,
            title = "BLACKOUT FRONT",
            subtitle = "Military Extraction",
            startZone = 1,
            endZone = 8,
            openingText = "Extraction job, sector Blackout Front. Enemy lines just collapsed — you're the last one still moving. Get to the rally point.",
            midRunText = "Radio crackles: \"Runner, they've got armor inbound. Don't stop for anything.\"",
            bossIntroText = "A war-drone locks onto your heat signature — built to hunt stragglers.",
            endingText = "You clear the front line as the sky lights up behind you. Command's already got your next job queued.",
            requiresPro = false,
            environmentIds = listOf(0, 5, 7) // Neon City, Electric Storm, Ancient Ruins
        ),
        
        World(
            id = 2,
            title = "DERELICT SIGNAL",
            subtitle = "Sci-Fi Retrieval",
            startZone = 9,
            endZone = 16,
            openingText = "Job's simple: retrieve the core from a research station. One problem — something's still awake in there.",
            midRunText = "The station's AI whispers through the comms: \"You should not have come back.\"",
            bossIntroText = "Whatever's guarding the core isn't human anymore — and it's fast.",
            endingText = "The station goes dark behind you for good. What you're carrying... you're not sure you want to know.",
            requiresPro = false,
            environmentIds = listOf(1, 4, 14) // Deep Space, Void Realm, Dimensional Rift
        
        ),
        World(
            id = 3,
            title = "CELL BLOCK ZERO",
            subtitle = "Prison Break",
            startZone = 17,
            endZone = 24,
            openingText = "Wrongfully locked up, or maybe not — doesn't matter now. Riot's started. This is your only window out.",
            midRunText = "Guards seal the east wing. \"Runner's loose! Lock it down!\"",
            bossIntroText = "The warden himself blocks the final gate — and he's not going down easy.",
            endingText = "You clear the wall as sirens fade behind you. Free — for now.",
            requiresPro = false,
            environmentIds = listOf(10, 2, 12) // Shadow World, Crystal Cave, Frozen Tundra
        
        ),
        World(
            id = 4,
            title = "GREEN HELL",
            subtitle = "Wild Escape",
            startZone = 25,
            endZone = 32,
            openingText = "Chopper went down deep in the reserve. Something big found the wreckage before rescue did.",
            midRunText = "Something's pacing you through the trees — matching your speed, staying just out of sight.",
            bossIntroText = "It finally shows itself — and it's been hunting you since you landed.",
            endingText = "You break the treeline as the jungle goes quiet behind you. You made it. Barely.",
            requiresPro = true,
            environmentIds = listOf(8, 6, 3) // Cyber Garden, Ocean Deep, Lava Forge
        ),
        World(
            id = 5,
            title = "RED PROTOCOL",
            subtitle = "Citywide Manhunt",
            startZone = 33,
            endZone = 40,
            openingText = "Your face just hit every screen in the city. RED PROTOCOL is active — every drone, every camera, every door is against you now.",
            midRunText = "Command channel, encrypted: \"They've sealed the outer district. You have one route left — through the transit spine.\"",
            bossIntroText = "A hunter-class enforcer drops from the skyline, already locked onto your signal.",
            endingText = "You clear the city limits as the alert finally goes dark behind you. For now, Red Protocol is over. For you, it never really ends.",
            requiresPro = true,
            environmentIds = listOf(0, 13, 9), // Neon City, Plasma Field, Prismatic
            worldFamily = 5,
            phase = 1
        ),

        // ============================================================
        // PHASE 2 — all 5 worlds return, harder, zones 100-179
        // ============================================================
        World(
            id = 12, title = "BLACKOUT FRONT: SECOND WAVE", subtitle = "Military Extraction — Phase 2",
            startZone = 100, endZone = 115,
            openingText = "Command's sending you back in. The first extraction worked too well — now they want the whole convoy, and the warzone knows you're coming this time.",
            midRunText = "Radio chatter, tense: \"They've doubled patrols since your last run. Someone talked.\"",
            bossIntroText = "The same war-drone class finds you again — rebuilt, faster, and it remembers losing.",
            endingText = "You make it out with the convoy intact, but command's tone has changed. They're not calling this extraction anymore. They're calling it a pattern.",
            requiresPro = true, environmentIds = listOf(0, 5, 7), worldFamily = 1, phase = 2
        ),
        World(
            id = 13, title = "BLACKOUT FRONT: SCORCHED LINE", subtitle = "Military Extraction — Phase 3",
            startZone = 300, endZone = 331,
            openingText = "The front line has moved. What was a warzone is now scorched earth, and command wants to know why nothing's transmitting from it anymore.",
            midRunText = "Every heat signature you pass is already cold. Whatever happened here, it happened fast.",
            bossIntroText = "Three war-drones this time, hunting in formation — the survivors of every run before this one.",
            endingText = "You clear the line, but the silence behind you is worse than the drones ever were.",
            requiresPro = true, environmentIds = listOf(0, 5, 7), worldFamily = 1, phase = 3
        ),
        World(
            id = 14, title = "BLACKOUT FRONT: LAST EXTRACTION", subtitle = "Military Extraction — Phase 4",
            startZone = 700, endZone = 747,
            openingText = "This is the last extraction order command will ever send from this front. After this, there's no one left to send for.",
            midRunText = "The comm channel that used to be full of chatter has been dead air for six runs straight.",
            bossIntroText = "The drone that finds you now isn't hunting orders anymore. It's just hunting.",
            endingText = "You get out. Command doesn't answer when you check in. You're not sure there's a command left to answer.",
            requiresPro = true, environmentIds = listOf(0, 5, 7), worldFamily = 1, phase = 4
        ),

        World(
            id = 15, title = "DERELICT SIGNAL: DEEPER STATIC", subtitle = "Sci-Fi Retrieval — Phase 2",
            startZone = 116, endZone = 131,
            openingText = "The signal never actually stopped. It just moved deeper into the station, into sections that shouldn't still have power.",
            midRunText = "Every door you open was already open. Something's been walking these halls ahead of you.",
            bossIntroText = "The guardian from before is back — and this time it brought pieces of what used to be the crew.",
            endingText = "You silence the signal again. It doesn't feel like the last time.",
            requiresPro = true, environmentIds = listOf(1, 4, 14), worldFamily = 2, phase = 2
        ),
        World(
            id = 16, title = "DERELICT SIGNAL: THE CORE AWAKENS", subtitle = "Sci-Fi Retrieval — Phase 3",
            startZone = 332, endZone = 363,
            openingText = "The core isn't dormant anymore. Whatever you disturbed the first two times, it's fully awake now, and it knows your signature.",
            midRunText = "The station's schematics don't match what's actually down here anymore. It's been building something.",
            bossIntroText = "What guards the core now barely resembles what it started as — and it's not guarding anymore. It's hunting.",
            endingText = "The core goes dark for good this time. You hope.",
            requiresPro = true, environmentIds = listOf(1, 4, 14), worldFamily = 2, phase = 3
        ),
        World(
            id = 17, title = "DERELICT SIGNAL: LAST BROADCAST", subtitle = "Sci-Fi Retrieval — Phase 4",
            startZone = 748, endZone = 795,
            openingText = "The station is finally coming apart. This is the last run anyone will ever make out here — one final signal, then it's gone for good.",
            midRunText = "The hull groans around you like it knows this is the end of its story too.",
            bossIntroText = "Everything the station ever was, all at once, one last time.",
            endingText = "You clear the wreck as it finally goes dark behind you, forever. The signal is silent. Truly, this time.",
            requiresPro = true, environmentIds = listOf(1, 4, 14), worldFamily = 2, phase = 4
        ),

        World(
            id = 18, title = "CELL BLOCK ZERO: LOCKDOWN", subtitle = "Prison Break — Phase 2",
            startZone = 132, endZone = 147,
            openingText = "Word got out about the first break. The whole facility's gone into full lockdown, and they've brought in wardens who don't miss.",
            midRunText = "Every gate you clear seals itself behind you a half-second later. They're learning your route.",
            bossIntroText = "The warden's replacement is waiting at the gate — younger, faster, and personally offended you got out once already.",
            endingText = "You clear the lockdown. Somewhere behind you, alarms are still going.",
            requiresPro = true, environmentIds = listOf(10, 2, 12), worldFamily = 3, phase = 2
        ),
        World(
            id = 19, title = "CELL BLOCK ZERO: DEEP WING", subtitle = "Prison Break — Phase 3",
            startZone = 364, endZone = 395,
            openingText = "There's a wing of this place that was sealed before you ever got here. After the lockdown, they moved everyone dangerous into it. Including you, once.",
            midRunText = "The cells down here don't have numbers. Just warnings.",
            bossIntroText = "Whoever runs the deep wing doesn't wear a warden's uniform anymore — they stopped needing the title.",
            endingText = "You clear the deep wing. You don't look back at what was in the cells you passed.",
            requiresPro = true, environmentIds = listOf(10, 2, 12), worldFamily = 3, phase = 3
        ),
        World(
            id = 20, title = "CELL BLOCK ZERO: THE LAST WARDEN", subtitle = "Prison Break — Phase 4",
            startZone = 796, endZone = 843,
            openingText = "Every warden who ever chased you is gone now, except one. The original. He's been waiting this whole time for you to come back.",
            midRunText = "The facility is nearly empty. It's just the two of you left in here now.",
            bossIntroText = "The warden himself, one final time — no shield, no backup, just the gate and him.",
            endingText = "The gate finally stays open behind you. Cell Block Zero is yours to leave, for good.",
            requiresPro = true, environmentIds = listOf(10, 2, 12), worldFamily = 3, phase = 4
        ),

        World(
            id = 21, title = "GREEN HELL: THE HUNT CONTINUES", subtitle = "Wild Escape — Phase 2",
            startZone = 148, endZone = 163,
            openingText = "It survived. Whatever you outran the first time didn't die out here — it adapted, and now it's hunting with a grudge.",
            midRunText = "The jungle's gone quiet in a way that means something bigger than you is close.",
            bossIntroText = "It's changed since last time — faster, angrier, and it remembers exactly how you got away.",
            endingText = "You outrun it again. Barely. It won't forget this one either.",
            requiresPro = true, environmentIds = listOf(8, 6, 3), worldFamily = 4, phase = 2
        ),
        World(
            id = 22, title = "GREEN HELL: DEEP BIOME", subtitle = "Wild Escape — Phase 3",
            startZone = 396, endZone = 427,
            openingText = "You're pushed past the part of the biome anyone's mapped. Down here, everything that hunts you has never seen a human before.",
            midRunText = "Nothing down here runs from you. That's new, and it's not a good sign.",
            bossIntroText = "The thing that rules this deep layer doesn't hunt like the others. It doesn't have to.",
            endingText = "You claw your way back to mapped territory. Whatever's down there, it's staying down there — for now.",
            requiresPro = true, environmentIds = listOf(8, 6, 3), worldFamily = 4, phase = 3
        ),
        World(
            id = 23, title = "GREEN HELL: APEX AWAKENED", subtitle = "Wild Escape — Phase 4",
            startZone = 844, endZone = 891,
            openingText = "Everything you've outrun in this jungle was leading up to this. The true apex of the biome has finally noticed you.",
            midRunText = "Every creature that used to hunt you is fleeing in the same direction you are.",
            bossIntroText = "The apex predator, unranked, unmatched — the thing every other predator here was afraid of too.",
            endingText = "You outrun the unrunnable. Green Hell doesn't have anything left to throw at you.",
            requiresPro = true, environmentIds = listOf(8, 6, 3), worldFamily = 4, phase = 4
        ),

        World(
            id = 24, title = "RED PROTOCOL: CODE ESCALATION", subtitle = "Citywide Manhunt — Phase 2",
            startZone = 164, endZone = 179,
            openingText = "Red Protocol never actually stood down. It just went quiet, waiting for you to think it was over.",
            midRunText = "Every camera in the district turns to track you now, in sync, like the whole city is one machine.",
            bossIntroText = "A second hunter-class enforcer, deployed the moment the first one failed.",
            endingText = "You slip the net again. The city remembers your face a little better each time.",
            requiresPro = true, environmentIds = listOf(0, 13, 9), worldFamily = 5, phase = 2
        ),
        World(
            id = 25, title = "RED PROTOCOL: FULL MOBILIZATION", subtitle = "Citywide Manhunt — Phase 3",
            startZone = 428, endZone = 459,
            openingText = "The city stops pretending this is routine. Every enforcement unit in the district is mobilized, for you alone.",
            midRunText = "Command channel, no longer encrypted, broadcasting openly now: \"Whatever it costs.\"",
            bossIntroText = "Not one enforcer this time. A squadron, dropping in formation, all locked onto the same signal.",
            endingText = "You clear the city as sirens finally fade behind you. For the first time, you wonder if they'll ever stop looking.",
            requiresPro = true, environmentIds = listOf(0, 13, 9), worldFamily = 5, phase = 3
        ),
        World(
            id = 26, title = "RED PROTOCOL: ZERO HOUR", subtitle = "Citywide Manhunt — Phase 4",
            startZone = 892, endZone = 939,
            openingText = "This is the order they never wanted to give. Every drone, every unit, every door in the city — zero hour, all at once.",
            midRunText = "There's nowhere left in the city that isn't watching. You run anyway.",
            bossIntroText = "Command itself comes down from the skyline this time — not a drone, not an enforcer. Whoever's been running this the whole time.",
            endingText = "The city goes quiet behind you, all at once, like it finally exhaled. Red Protocol, for the first time since it started, actually ends.",
            requiresPro = true, environmentIds = listOf(0, 13, 9), worldFamily = 5, phase = 4
        )
    )

    // Special Mode worlds — not part of normal endless progression.
    // Unlocked permanently via mission-tier qualification (see MissionManager/ViewModel).
    val SPECIAL_WORLDS: List<World> = listOf(
        World(
            id = 6,
            title = "SIGNAL FRACTURE",
            subtitle = "Data Heist",
            startZone = 1,
            endZone = 999,
            openingText = "Red Protocol went dark, but someone's been watching the whole time. They want what's in your head — the run data. Time to disappear into the wire.",
            midRunText = "A voice bleeds through every speaker you pass: \"We know where you're going.\"",
            bossIntroText = "A rogue trace-daemon breaches the tunnel — it's been hunting your signal for weeks.",
            endingText = "You slip the last firewall as the city's grid flickers back to normal. For now, you're a ghost again.",
            requiresPro = false,
            environmentIds = listOf(15) // Signal Fracture (dedicated new environment)
        ),
        World(
            id = 7,
            title = "FROZEN VEIL",
            subtitle = "Arctic Infiltration",
            startZone = 1,
            endZone = 999,
            openingText = "Coordinates lead north, to a research site buried under a century of ice. Something down there was never meant to surface.",
            midRunText = "The facility's old intercom crackles: \"Site containment has failed. Do not proceed.\"",
            bossIntroText = "The ice cracks behind you — something enormous was sleeping just beneath it.",
            endingText = "You break the surface into blinding white, the facility collapsing into the dark below.",
            requiresPro = false,
            environmentIds = listOf(16) // Frozen Veil (dedicated new environment)
        ),
        World(
            id = 8,
            title = "APEX SIGNAL",
            subtitle = "Final Ascent",
            startZone = 1,
            endZone = 999,
            openingText = "Every job, every job you've ever run — it all led here. The Apex Signal. Whatever's broadcasting it built everything you've survived so far.",
            midRunText = "The signal speaks directly into your comms now: \"You were always going to come.\"",
            bossIntroText = "The source reveals itself — not human, not machine, something in between.",
            endingText = "The signal goes silent. For the first time since this all began, so does your mind.",
            requiresPro = false,
            environmentIds = listOf(17) // Apex Signal (dedicated new environment)
        ),

        // ---- Six more mission-unlocked Special worlds (tiers 4-9). No new art: each remixes
        // ---- existing layers with a hue shift, donor sky/foreground, code-drawn weather and its
        // ---- own difficulty personality (see WorldVariants + WorldFlavors).
        World(
            id = 27, title = "SPORE GARDEN", subtitle = "Overgrown Relay",
            startZone = 1, endZone = 999,
            openingText = "A relay tower was swallowed by something that blooms. Every spore carries a piece of the signal.",
            midRunText = "The air glows green. The plants are listening.",
            bossIntroText = "The garden's heart unfolds, and it has been feeding on the signal for years.",
            endingText = "You burn a path out as the spores settle. The relay is quiet.",
            requiresPro = false, environmentIds = listOf(22)
        ),
        World(
            id = 28, title = "STORM CORRIDOR", subtitle = "Lightning Lanes",
            startZone = 1, endZone = 999,
            openingText = "A weather grid gone rogue. Every lane is a conductor and every cloud holds a charge.",
            midRunText = "Thunder rolls through the comms. Somebody is steering the storm.",
            bossIntroText = "The storm condenses into a single hunting shape, and it strikes where you were.",
            endingText = "You break out of the corridor, hair standing on end. The sky exhales.",
            requiresPro = false, environmentIds = listOf(23)
        ),
        World(
            id = 29, title = "CRIMSON FOUNDRY", subtitle = "Molten Assembly",
            startZone = 1, endZone = 999,
            openingText = "A foundry that never shut down keeps building hunters. Tonight you are the order.",
            midRunText = "Molten rivers cross the track. The presses keep time with your pulse.",
            bossIntroText = "The foreman rises from the slag, built from every machine that failed to stop you.",
            endingText = "The furnaces cool behind you. For once, nothing new rolls off the line.",
            requiresPro = false, environmentIds = listOf(24)
        ),
        World(
            id = 30, title = "ABYSS TIDE", subtitle = "Sunken Signal",
            startZone = 1, endZone = 999,
            openingText = "The signal sank with the old city. Down here the pressure bends the data and the dark has teeth.",
            midRunText = "Sonar pings answer from below. Something large is following the beat.",
            bossIntroText = "The deep opens its jaw. The tide itself is the boss.",
            endingText = "You surface into daylight, gasping. The tide pulls back, patient.",
            requiresPro = false, environmentIds = listOf(25)
        ),
        World(
            id = 31, title = "SOLAR CATHEDRAL", subtitle = "Radiant Siege",
            startZone = 1, endZone = 999,
            openingText = "A cathedral of mirrors focuses the sun into a weapon. Pilgrims do not return from its nave.",
            midRunText = "Light bends into blades. The choir is a firing solution.",
            bossIntroText = "The Archon steps out of the glare, and it never blinks.",
            endingText = "You leave the nave as the glass shatters behind you. The sun is just the sun again.",
            requiresPro = false, environmentIds = listOf(26)
        ),
        World(
            id = 32, title = "NULL HORIZON", subtitle = "Edge of the Map",
            startZone = 1, endZone = 999,
            openingText = "Past the last mapped zone the track forgets itself. This is the hardest run the signal can build.",
            midRunText = "Geometry stutters. The horizon is no longer where it was.",
            bossIntroText = "Nothing steps forward, and then everything does.",
            endingText = "You cross the horizon that was never there. The map has a new edge, and it is you.",
            requiresPro = false, environmentIds = listOf(27)
        )
    )

    // Post-ending "New Game+" tier, unlocked only after actually beating
    // World 8 (Apex Signal) — see isWorldUnlocked. Picks up the unresolved
    // "not human, not machine" ending: the fusion didn't end clean.
    val NEW_GAME_PLUS: List<World> = listOf(
        World(
            id = 9,
            title = "GHOST PROTOCOL",
            subtitle = "Identity Collapse",
            startZone = 1,
            endZone = 999,
            openingText = "The signal went silent, but you didn't come back all the way. Something wearing your face is running the same streets you are.",
            midRunText = "A reflection in a shattered window moves half a second after you do.",
            bossIntroText = "It steps out of a mirrored alley wearing your exact face — and it's not slowing down.",
            endingText = "You lose it in the static, but you're not sure anymore which of you got away.",
            requiresPro = true,
            environmentIds = listOf(19) // Ghost Protocol (dedicated new environment)
        ),
        World(
            id = 10,
            title = "DEEP ARCHIVE",
            subtitle = "Memory Retrieval",
            startZone = 1,
            endZone = 999,
            openingText = "If you're going to un-become whatever you're turning into, it's buried in an ancient server vault, miles under everything.",
            midRunText = "Fragments of your own voice, from runs you don't remember making, bleed through the archive's dead channels.",
            bossIntroText = "Something built from every discarded backup of you rises to guard the last clean copy.",
            endingText = "You surface holding a memory that might actually be yours. It's a start.",
            requiresPro = true,
            environmentIds = listOf(20) // Deep Archive (dedicated new environment)
        ),
        World(
            id = 11,
            title = "LAST TRANSMISSION",
            subtitle = "Final Signal",
            startZone = 1,
            endZone = 999,
            openingText = "This is the one that decides it. Break the signal for good, or become it permanently. No more runs after this.",
            midRunText = "There's no ground left, no city, no track — just the signal, and you, running through it.",
            bossIntroText = "The Apex Signal itself meets you at the source, one last time, for real this time.",
            endingText = "Whatever answer you found, it's yours now. The signal doesn't speak again.",
            requiresPro = true,
            environmentIds = listOf(21) // Last Transmission (dedicated new environment)
        )
    )

    fun worldForZone(zone: Int): World {
        return ALL.find { zone in it.startZone..it.endZone } ?: ALL.last()
    }

    fun specialWorldForTier(tier: Int): World? {
        return when (tier) {
            1 -> SPECIAL_WORLDS.getOrNull(0)
            2 -> SPECIAL_WORLDS.getOrNull(1)
            3 -> SPECIAL_WORLDS.getOrNull(2)
            in 4..SPECIAL_TIER_COUNT -> SPECIAL_WORLDS.getOrNull(tier - 1)
            else -> null
        }
    }

    /** Tiers 1-3 are the original Special worlds; 4-9 are the six newer ones. */
    const val SPECIAL_TIER_COUNT = 9

    fun newGamePlusWorldForTier(tier: Int): World? {
        return when (tier) {
            1 -> NEW_GAME_PLUS.getOrNull(0)
            2 -> NEW_GAME_PLUS.getOrNull(1)
            3 -> NEW_GAME_PLUS.getOrNull(2)
            else -> null
        }
    }

    // completedWorldIds: world ids the player has actually finished (see
    // GameProfile.completedWorldsCsv). Required in addition to requiresPro
    // for New Game+ worlds, so that tier is an earned unlock, not just a
    // purchase — Pro alone would let a paying player skip straight to the
    // ending content and recreate the "explored everything in 3 months"
    // problem we're trying to avoid.
    fun isWorldUnlocked(world: World, isPro: Boolean, completedWorldIds: Set<Int> = emptySet()): Boolean {
        val proOk = !world.requiresPro || isPro
        val prestigeOk = if (NEW_GAME_PLUS.any { it.id == world.id }) 8 in completedWorldIds else true
        return proOk && prestigeOk
    }
}
