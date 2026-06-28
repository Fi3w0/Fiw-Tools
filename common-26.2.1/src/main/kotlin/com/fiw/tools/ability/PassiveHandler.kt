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
import java.util.concurrent.ThreadLocalRandom

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
     * Groups all equipped Fiw items by their resonanceId. For each set where the equipped count
     * meets the item's `resonanceRequires` threshold, fires RESONANCE-trigger abilities from the
     * first matching stack. Only one item in a set needs to define resonance abilities — if
     * multiple do, each fires independently (so put set bonuses on only one piece).
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
            val (firstStack, firstDef) = entries.first()
            if (entries.size < firstDef.resonanceRequires) continue
            runSlot(player, world, firstStack, AbilityTrigger.RESONANCE, "slot", "any", "any")
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
        actualSlot: String
    ) {
        if (stack.isEmpty) return
        val id = FiwItems.fiwId(stack) ?: return
        val def = ItemRegistry.byId(id) ?: return
        ImbueMods.effectiveAbilities(stack, def).forEachIndexed { idx, ab ->
            if (AbilityTrigger.parse(ab.trigger) != trigger) return@forEachIndexed
            val filterValue = ab.params.get(filterKey)?.takeIf { !it.isJsonNull }?.asString?.lowercase() ?: filterDefault
            if (filterValue != filterDefault && filterValue != actualSlot) return@forEachIndexed
            val key = AbilityCooldownTracker.key(def.id, idx)
            if (!AbilityCooldownTracker.isReady(player, key)) return@forEachIndexed
            if (!PassiveConditions.met(player, world, ab.params)) return@forEachIndexed
            if (ab.chance < 1f && ThreadLocalRandom.current().nextFloat() > ab.chance) return@forEachIndexed
            val ability = AbilityRegistry.get(ab.type) ?: return@forEachIndexed
            try {
                if (ability.execute(AbilityContext(player, stack, world, null, null, ab.params))) {
                    AbilityCooldownTracker.arm(player, key, ab.cooldownTicks)
                }
            } catch (e: Exception) {
                logger.warn("Passive '${ab.type}' on '${def.id}' threw: ${e.message}")
            }
        }
    }
}
