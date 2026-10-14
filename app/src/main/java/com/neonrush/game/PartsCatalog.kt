package com.neonrush.game

import com.neonrush.game.db.GameDao
import com.neonrush.game.ui.PilotWardrobe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class PartSlot(val label: String, val emoji: String) {
    HEAD("Head", "🪖"), NECK("Neck", "🧣"), SHOULDER("Shoulder", "🛡️"), BACK("Back", "🦇"), WRIST("Wrists", "🧤"), BOOTS("Boots", "👟")
}

/**
 * A piece of pilot gear. Shop parts can be bought with gems and mixed onto ANY suit; suit-signature pieces (shop = false)
 * come only with their suit.
 */
data class PilotPart(
    val id: String, val name: String, val slot: PartSlot, val rarity: Rarity, val gems: Int,
    val behind: Boolean, val shop: Boolean, val blurb: String
)

object PartCatalog {
    private fun p(id: String, name: String, slot: PartSlot, r: Rarity, gems: Int, blurb: String, behind: Boolean = false, shop: Boolean = true) =
        PilotPart(id, name, slot, r, gems, behind, shop, blurb)

    val ALL: List<PilotPart> = listOf(
        // HEAD
        p("antenna_array", "Antenna Array", PartSlot.HEAD, Rarity.COMMON, 300, "Twin signal whips that sway as you run."),
        p("crest_fin", "Neon Crest", PartSlot.HEAD, Rarity.RARE, 600, "A razor fin crest in hot pink."),
        p("halo_ring", "Halo Ring", PartSlot.HEAD, Rarity.EPIC, 1000, "A gold ring that floats above the helmet.", behind = true),
        p("oni_horns", "Oni Horns", PartSlot.HEAD, Rarity.EPIC, 1000, "Crimson horns with bone-gold tips."),
        // NECK
        p("flag_scarf", "Battle Scarf", PartSlot.NECK, Rarity.COMMON, 300, "A red scarf that streams behind you."),
        p("neon_scarf", "Neon Scarf", PartSlot.NECK, Rarity.RARE, 500, "Cyan light-cloth with a glowing core."),
        p("gold_chain", "Gold Chain", PartSlot.NECK, Rarity.RARE, 600, "Heavy chain with a ruby pendant."),
        // SHOULDER
        p("spike_pauldron", "Spike Pauldron", PartSlot.SHOULDER, Rarity.RARE, 700, "Steel shoulder plate with three spikes."),
        p("skull_guard", "Skull Guard", PartSlot.SHOULDER, Rarity.RARE, 700, "A bleached skull strapped to your shoulder."),
        p("gold_epaulette", "Gold Epaulette", PartSlot.SHOULDER, Rarity.EPIC, 900, "Officer's fringe in polished gold."),
        p("crystal_shard", "Crystal Cluster", PartSlot.SHOULDER, Rarity.EPIC, 1000, "Glassy cyan crystals grown from the shoulder."),
        // BACK
        p("banner_pole", "Banner Pole", PartSlot.BACK, Rarity.COMMON, 400, "Carry your own purple war banner.", behind = true),
        p("twin_pods", "Twin Pods", PartSlot.BACK, Rarity.RARE, 700, "Two extra thruster pods strapped to the pack."),
        p("royal_cape", "Royal Cape", PartSlot.BACK, Rarity.LEGENDARY, 1500, "A crimson cape with a golden hem.", behind = true),
        p("mech_wings", "Mech Wings", PartSlot.BACK, Rarity.LEGENDARY, 1800, "Blade wings with cyan light cores.", behind = true),
        // WRIST
        p("chain_shackle", "Chain Cuffs", PartSlot.WRIST, Rarity.COMMON, 300, "Broken shackles still dangling chain."),
        p("power_gauntlet", "Power Gauntlets", PartSlot.WRIST, Rarity.RARE, 600, "Armoured bracers with glowing cores."),
        p("blade_bracer", "Blade Bracers", PartSlot.WRIST, Rarity.EPIC, 1000, "Forearm guards with energy blades."),
        // BOOTS
        p("spur_boots", "Gold Spurs", PartSlot.BOOTS, Rarity.COMMON, 300, "Golden spurs on both heels."),
        p("magnet_cuffs", "Mag Cuffs", PartSlot.BOOTS, Rarity.RARE, 600, "Hovering magnet rings round each ankle."),
        p("wing_boots", "Wing Boots", PartSlot.BOOTS, Rarity.EPIC, 1000, "White feathered wings on both heels.", behind = true),
        // suit-signature pieces (come with their suit only)
        p("sun_crown", "Sun Crown", PartSlot.HEAD, Rarity.LEGENDARY, 0, "Solar Flare's blazing crown.", shop = false),
        p("reaper_hood", "Reaper Hood", PartSlot.HEAD, Rarity.LEGENDARY, 0, "Chrome Reaper's hood.", behind = true, shop = false),
        p("flower_crest", "Bloom Crest", PartSlot.HEAD, Rarity.LEGENDARY, 0, "Toxic Bloom's flowers.", shop = false),
        p("gold_crown", "Protocol Crown", PartSlot.HEAD, Rarity.LEGENDARY, 0, "Golden Protocol's crown.", shop = false),
        p("headband", "Racing Headband", PartSlot.HEAD, Rarity.LEGENDARY, 0, "Nova Sprint's headband.", shop = false),
        p("ember_horns", "Ember Horns", PartSlot.HEAD, Rarity.LEGENDARY, 0, "Obsidian horns with glowing tips.", shop = false),
        p("vine_scarf", "Vine Scarf", PartSlot.NECK, Rarity.LEGENDARY, 0, "Toxic Bloom's vines.", shop = false),
        p("void_cloak", "Void Cloak", PartSlot.BACK, Rarity.LEGENDARY, 0, "Void Walker's starry cloak.", behind = true, shop = false),
        p("claw_spikes", "Claw Spikes", PartSlot.SHOULDER, Rarity.LEGENDARY, 0, "Apex Predator's claws.", shop = false),
        p("shackle_chain", "Prison Cuffs", PartSlot.WRIST, Rarity.LEGENDARY, 0, "Convict Grey's cuffs.", shop = false)
    )
    private val BY_ID = ALL.associateBy { it.id }
    fun byId(id: String): PilotPart? = BY_ID[id]
    val SHOP: List<PilotPart> = ALL.filter { it.shop }
}

data class PartsState(
    val owned: Set<String> = emptySet(),
    val equipped: Map<String, String> = emptyMap()      // slot name -> part id
)

/** Which parts the player owns and wears. Parts are bought with gems and persist across suits. */
class PartsLocker(
    private val dao: GameDao,
    private val store: HangarStore,
    private val scope: CoroutineScope,
    private val announce: (String) -> Unit
) {
    private val _state = MutableStateFlow(PartsState())
    val state: StateFlow<PartsState> = _state.asStateFlow()
    private companion object { const val K_OWNED = "part_owned_csv"; const val K_EQ = "part_equipped_csv" }

    private fun publish(s: PartsState) { _state.value = s; PilotWardrobe.equipped = s.equipped }

    suspend fun load() {
        try {
            val owned = store.getList(K_OWNED).filter { PartCatalog.byId(it)?.shop == true }.toSet()
            val eq = HashMap<String, String>()
            store.getList(K_EQ).forEach { e ->
                val i = e.indexOf(':'); if (i <= 0) return@forEach
                val slot = e.substring(0, i); val id = e.substring(i + 1)
                if (id in owned && PartCatalog.byId(id)?.slot?.name == slot) eq[slot] = id
            }
            publish(PartsState(owned, eq))
        } catch (_: Exception) {}
    }

    private suspend fun save() {
        try {
            store.putList(K_OWNED, _state.value.owned.toList())
            store.putList(K_EQ, _state.value.equipped.map { "${it.key}:${it.value}" })
        } catch (_: Exception) {}
    }

    fun equip(id: String) {
        val part = PartCatalog.byId(id) ?: return
        if (id !in _state.value.owned) return
        publish(_state.value.copy(equipped = _state.value.equipped + (part.slot.name to id)))
        scope.launch { save() }
    }

    fun unequip(slot: PartSlot) {
        publish(_state.value.copy(equipped = _state.value.equipped - slot.name))
        scope.launch { save() }
    }

    fun buy(id: String) {
        val part = PartCatalog.byId(id) ?: return
        if (!part.shop || id in _state.value.owned) return
        scope.launch {
            var ok = false
            dao.updateProfile { p -> if (p.gems >= part.gems) { ok = true; p.copy(gems = p.gems - part.gems) } else p }
            if (ok) {
                publish(_state.value.copy(owned = _state.value.owned + id, equipped = _state.value.equipped + (part.slot.name to id)))
                save()
                announce("${part.name} bought and equipped.")
            } else announce("Not enough gems for ${part.name} (${part.gems} 💎).")
        }
    }
}
