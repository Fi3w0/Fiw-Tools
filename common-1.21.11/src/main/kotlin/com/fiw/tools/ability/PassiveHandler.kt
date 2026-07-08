package com.fiw.tools.ability

import com.fiw.tools.build.FiwItems
import com.fiw.tools.config.ItemRegistry
import com.fiw.tools.imbue.ImbueMods
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.item.ItemStack
import org.slf4j.LoggerFactory

/**
 * Drives `while_held` and `while_worn` passive abilities. Every [SWEEP_TICKS] it walks each online
 * player's two hands and four armor slots; for each Fiw item, every passive whose trigger matches the
 * slot type (held vs worn) and whose slot filter matches the specific slot fires — honouring
 * per-player cooldowns (0 = run every sweep, for buffs), [PassiveConditions], and `chance`.
 *
 * Pure buffs re-apply a short effect each sweep, so they hold while equipped and fade shortly after
 * the item leaves your hand or armor slot. Reactive passives use `cooldownTicks` for their cadence.
 */
object PassiveHandler {
    private val logger = LoggerFactory.getLogger("fiw-tools/passive")

    private const val SWEEP_TICKS = 10
    private var counter = 0

    /** Armor slots we sweep, in head-down order. The string is the filter value players write in JSON. */
    private val WORN_SLOTS = listOf(
        EquipmentSlot.HEAD to "head",
        EquipmentSlot.CHEST to "chest",
        EquipmentSlot.LEGS to "legs",
        EquipmentSlot.FEET to "feet"
    )

    fun tick(server: MinecraftServer) {
        if (++counter >= SWEEP_TICKS) {
            counter = 0
            sweep(server)
        }
    }

    private fun sweep(server: MinecraftServer) {
        for (player in server.playerList.players) {
            val world = player.level()

            val main = player.mainHandItem
            runSlot(player, world, main, AbilityTrigger.WHILE_HELD, "hand", "either", "main")
            val off = player.offhandItem
            if (!off.isEmpty && off !== main) {
                runSlot(player, world, off, AbilityTrigger.WHILE_HELD, "hand", "either", "off")
            }

            for ((slot, slotName) in WORN_SLOTS) {
                val worn = player.getItemBySlot(slot)
                runSlot(player, world, worn, AbilityTrigger.WHILE_WORN, "slot", "any", slotName)
            }

            if (player.isCrouching) {
                runSlot(player, world, main, AbilityTrigger.WHILE_SNEAKING, "hand", "either", "main")
                if (!off.isEmpty && off !== main) {
                    runSlot(player, world, off, AbilityTrigger.WHILE_SNEAKING, "hand", "either", "off")
                }
                for ((slot, slotName) in WORN_SLOTS) {
                    val worn = player.getItemBySlot(slot)
                    runSlot(player, world, worn, AbilityTrigger.WHILE_SNEAKING, "slot", "any", slotName)
                }
            }

            sweepResonance(player, world)
        }
    }

    /**
     * Groups all equipped Fiw items by their resonanceId, then fires RESONANCE-trigger abilities
     * from the first matching stack, passing the equipped piece count so each ability's `minPieces`
     * param (default = the set's `resonanceRequires`) decides whether it activates. This lets one
     * set define several tiers — e.g. a 2-piece bonus and a stronger 4-piece bonus. Only one item in
     * a set needs to define resonance abilities (put set bonuses on a single piece).
     */
    private fun sweepResonance(player: net.minecraft.server.level.ServerPlayer, world: net.minecraft.server.level.ServerLevel) {
        val allStacks = buildList {
            add(player.mainHandItem)
            add(player.offhandItem)
            for ((slot, _) in WORN_SLOTS) add(player.getItemBySlot(slot))
        }
        // Map resonanceId -> list of (stack, def) pairs
        val groups = mutableMapOf<String, MutableList<Pair<ItemStack, com.fiw.tools.config.ItemDefinition>>>()
        for (stack in allStacks) {
            if (stack.isEmpty) continue
            val id = FiwItems.fiwId(stack) ?: continue
            val def = ItemRegistry.byId(id) ?: continue
            val rId = def.resonanceId ?: continue
            groups.getOrPut(rId) { mutableListOf() }.add(stack to def)
        }
        for ((_, entries) in groups) {
            val (firstStack, _) = entries.first()
            runSlot(player, world, firstStack, AbilityTrigger.RESONANCE, "slot", "any", "any", entries.size)
        }
    }

    /**
     * Generic single-slot runner. Walks the stack's effective abilities (def + imbued), keeps only the
     * ones whose [trigger] matches the slot type and whose `[filterKey]` param (a string like `"main"`,
     * `"head"`, `"any"`) matches [actualSlot]. The two callers — held + worn — only differ in those
     * parameters; sharing this body keeps cooldown/condition/chance logic in one place.
     */
    private fun runSlot(
        player: ServerPlayer,
        world: ServerLevel,
        stack: ItemStack,
        trigger: AbilityTrigger,
        filterKey: String,
        filterDefault: String,
        actualSlot: String,
        /** For RESONANCE: how many set pieces are equipped, so per-ability `minPieces` can tier. */
        resonancePieces: Int? = null
    ) {
        if (stack.isEmpty) return
        val id = FiwItems.fiwId(stack) ?: return
        val def = ItemRegistry.byId(id) ?: return
        if (com.fiw.tools.bind.BindingHandler.blocksUse(player, stack, def)) return
        val abilities = ImbueMods.effectiveAbilities(stack, def)
        // Pass 1: keep abilities clearing every non-random gate (trigger, resonance tier, stance,
        // slot filter, cooldown, conditions). Chance/proc groups are resolved together afterwards.
        val eligible = ArrayList<Int>()
        abilities.forEachIndexed { idx, ab ->
            if (AbilityTrigger.parse(ab.trigger) != trigger) return@forEachIndexed
            // Tiered resonance: an ability fires only once enough set pieces are worn. Its
            // `minPieces` defaults to the set's `resonanceRequires`, so untiered sets behave as before.
            if (trigger == AbilityTrigger.RESONANCE && resonancePieces != null &&
                resonancePieces < ab.params.optI("minPieces", def.resonanceRequires)) return@forEachIndexed
            val stanceGate = ab.stance
            if (stanceGate != null &&
                !stanceGate.equals(com.fiw.tools.stance.StanceHandler.activeStance(stack, def), ignoreCase = true)) return@forEachIndexed
            val filterValue = ab.params.get(filterKey)?.takeIf { !it.isJsonNull }?.asString?.lowercase() ?: filterDefault
            if (filterValue != filterDefault && filterValue != actualSlot) return@forEachIndexed
            val key = AbilityCooldownTracker.keyFor(def.id, idx, ab.cooldownGroup)
            if (!AbilityCooldownTracker.isReady(player, key)) return@forEachIndexed
            if (!PassiveConditions.met(player, world, ab.params)) return@forEachIndexed
            eligible.add(idx)
        }
        if (eligible.isEmpty()) return

        // Pass 2: resolve chance + proc groups, then fire the winners.
        val winners = ProcGroups.resolve(eligible.map {
            ProcGroups.Candidate(it, abilities[it].chance, abilities[it].procGroup)
        })
        for (idx in eligible) {
            if (idx !in winners) continue
            val ab = abilities[idx]
            val cost = ab.cost
            if (cost != null && !AbilityCosts.canPay(player, stack, def, cost)) continue
            val ability = AbilityRegistry.get(ab.type) ?: continue
            val key = AbilityCooldownTracker.keyFor(def.id, idx, ab.cooldownGroup)
            val params = AbilityScaling.resolve(player, stack, def, ab)
            try {
                if (ability.execute(AbilityContext(player, stack, world, null, null, params))) {
                    AbilityCooldownTracker.arm(player, key, ab.cooldownTicks)
                    if (cost != null) AbilityCosts.pay(player, stack, cost)
                }
            } catch (e: Exception) {
                logger.warn("Passive '${ab.type}' on '${def.id}' threw: ${e.message}")
            }
        }
    }
}
