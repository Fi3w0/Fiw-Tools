package com.fiw.tools.recharge

import com.fiw.tools.ability.AbilityCooldownTracker
import com.fiw.tools.build.FiwItems
import com.fiw.tools.config.ItemDefinition
import com.fiw.tools.config.ItemRegistry
import com.fiw.tools.imbue.ImbueMods
import com.fiw.tools.infinite.InfiniteItems
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.Unbreakable
import java.util.Collections
import java.util.IdentityHashMap
import java.util.concurrent.ThreadLocalRandom
import kotlin.math.ceil

object RechargeOnKillHandler {
    fun onKill(player: ServerPlayer, victim: LivingEntity) {
        val seen = Collections.newSetFromMap(IdentityHashMap<ItemStack, Boolean>())
        visit(player, player.mainHandItem, victim, "main", seen)
        visit(player, player.offhandItem, victim, "off", seen)
        val inv = player.inventory
        for (slot in 0 until inv.containerSize) {
            visit(player, inv.getItem(slot), victim, "inventory", seen)
        }
    }

    private fun visit(
        player: ServerPlayer,
        stack: ItemStack,
        victim: LivingEntity,
        slot: String,
        seen: MutableSet<ItemStack>
    ) {
        if (!seen.add(stack)) return
        if (stack.isEmpty) return
        val id = FiwItems.fiwId(stack) ?: return
        val def = ItemRegistry.byId(id) ?: return
        val cfg = def.rechargeOnKill ?: return
        if (!matchesSlot(cfg.hand, slot)) return
        if (def.binding != null && com.fiw.tools.bind.BindingHandler.blocksUse(player, stack, def)) return
        if (!matchesTarget(cfg, victim)) return
        if (cfg.chance < 1f && ThreadLocalRandom.current().nextFloat() > cfg.chance) return

        val abilities = ImbueMods.effectiveAbilities(stack, def)
        val indices = selectedAbilityIndices(abilities, cfg)
        if (cfg.resetCooldowns) AbilityCooldownTracker.clearItem(player, id, indices)
        if (cfg.cooldownTicks > 0 || cfg.cooldownPercent > 0f) {
            AbilityCooldownTracker.reduceItem(player, id, indices, cfg.cooldownTicks, cfg.cooldownPercent)
        }

        if (applyDurabilityRewards(stack, cfg) && def.infinite != null) {
            InfiniteItems.applyUseRemainder(stack, def, player.level().registryAccess())
        }
    }

    private fun selectedAbilityIndices(
        abilities: List<ItemDefinition.AbilityDef>,
        cfg: ItemDefinition.RechargeOnKillDef
    ): List<Int> {
        val types = cfg.cooldownAbilityTypes.map { it.lowercase() }.toSet()
        val triggers = cfg.cooldownTriggers.map { it.lowercase() }.toSet()
        if (types.isEmpty() && triggers.isEmpty()) return abilities.indices.toList()
        return abilities.mapIndexedNotNull { index, ability ->
            val typeOk = types.isEmpty() || ability.type.lowercase() in types
            val triggerOk = triggers.isEmpty() || ability.trigger.lowercase() in triggers
            if (typeOk && triggerOk) index else null
        }
    }

    private fun matchesSlot(configured: String, actual: String): Boolean = when (configured) {
        "main" -> actual == "main"
        "off" -> actual == "off"
        "both" -> actual == "main" || actual == "off"
        "any" -> true
        else -> false
    }

    private fun matchesTarget(cfg: ItemDefinition.RechargeOnKillDef, victim: LivingEntity): Boolean {
        val entityId = BuiltInRegistries.ENTITY_TYPE.getKey(victim.type).toString()
        val baseMatch = when (cfg.target) {
            "any" -> true
            "mobs" -> victim !is Player
            "players" -> victim is Player
            "entities" -> cfg.entities.contains(entityId)
            else -> false
        }
        if (!baseMatch) return false
        if (cfg.players.isNotEmpty()) {
            val player = victim as? Player ?: return false
            val names = cfg.players.map { it.lowercase() }.toSet()
            if (player.uuid.toString().lowercase() !in names && player.name.string.lowercase() !in names) return false
        }
        return cfg.target == "entities" || cfg.entities.isEmpty() || cfg.entities.contains(entityId)
    }

    private fun applyDurabilityRewards(stack: ItemStack, cfg: ItemDefinition.RechargeOnKillDef): Boolean {
        var changed = false
        if (cfg.addMaxDamage > 0) {
            val current = stack.maxDamage.coerceAtLeast(0)
            val cap = cfg.maxDamageCap ?: Int.MAX_VALUE
            val next = (current + cfg.addMaxDamage).coerceAtMost(cap)
            if (next > current && next > 0) {
                stack.set(DataComponents.MAX_DAMAGE, next)
                changed = true
            }
        }
        if (cfg.unbreakable) {
            stack.set(DataComponents.UNBREAKABLE, Unbreakable(true))
            changed = true
        }
        if (repair(stack, cfg) > 0) changed = true
        return changed
    }

    private fun repair(stack: ItemStack, cfg: ItemDefinition.RechargeOnKillDef): Int {
        if (stack.damageValue <= 0) return 0
        if (cfg.fullRepair) {
            val old = stack.damageValue
            stack.damageValue = 0
            return old
        }
        val percentRepair = if (cfg.repairPercent > 0f && stack.maxDamage > 0) {
            ceil(stack.maxDamage * cfg.repairPercent.toDouble()).toInt()
        } else 0
        val amount = cfg.repairDamage + percentRepair
        if (amount <= 0) return 0
        val old = stack.damageValue
        stack.damageValue = (old - amount).coerceAtLeast(0)
        return old - stack.damageValue
    }
}
