package com.fiw.tools.ability

import com.fiw.tools.build.ItemBuilder
import com.fiw.tools.config.ItemDefinition
import com.fiw.tools.config.ItemRegistry
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents
import net.fabricmc.fabric.api.event.player.AttackEntityCallback
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents
import net.fabricmc.fabric.api.event.player.UseItemCallback
import net.minecraft.core.component.DataComponents
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.Vec3
import org.slf4j.LoggerFactory
import java.util.concurrent.ThreadLocalRandom

object AbilityDispatcher {
    private val logger = LoggerFactory.getLogger("fiw-tools/abilities")

    fun init() {
        UseItemCallback.EVENT.register { player, world, hand ->
            if (world.isClientSide || player !is ServerPlayer) return@register InteractionResult.PASS
            val stack = player.getItemInHand(hand)
            if (fire(AbilityTrigger.ON_RIGHT_CLICK, player, stack, world as ServerLevel, null, null)) {
                InteractionResult.SUCCESS
            } else InteractionResult.PASS
        }

        AttackEntityCallback.EVENT.register { player, world, hand, target, _ ->
            if (world.isClientSide || player !is ServerPlayer || target !is LivingEntity) return@register InteractionResult.PASS
            val stack = player.getItemInHand(hand)
            fire(AbilityTrigger.ON_ATTACK, player, stack, world as ServerLevel, target, target.position())
            InteractionResult.PASS
        }

        ServerLivingEntityEvents.AFTER_DEATH.register { entity, source ->
            val attacker = source.entity
            if (attacker is ServerPlayer) {
                val stack = attacker.mainHandItem
                fire(AbilityTrigger.ON_KILL, attacker, stack, attacker.level() as ServerLevel, entity, entity.position())
            }
        }

        ServerLivingEntityEvents.ALLOW_DAMAGE.register { entity, _, _ ->
            if (entity is ServerPlayer) {
                val world = entity.level()
                if (world is ServerLevel) {
                    val stack = entity.mainHandItem
                    fire(AbilityTrigger.ON_HURT, entity, stack, world, null, null)
                    val off = entity.offhandItem
                    if (off !== stack) fire(AbilityTrigger.ON_HURT, entity, off, world, null, null)
                }
            }
            true
        }

        PlayerBlockBreakEvents.AFTER.register { world, player, pos, _, _ ->
            if (world is ServerLevel && player is ServerPlayer) {
                fire(AbilityTrigger.ON_BLOCK_BREAK, player, player.mainHandItem, world,
                    null, Vec3.atCenterOf(pos))
            }
        }
    }

    private fun fire(
        trigger: AbilityTrigger,
        player: ServerPlayer,
        stack: ItemStack,
        world: ServerLevel,
        target: LivingEntity?,
        targetPos: Vec3?
    ): Boolean {
        if (stack.isEmpty) return false
        val def = resolveDefinition(stack) ?: return false
        if (def.abilities.isEmpty()) return false

        var any = false
        def.abilities.forEachIndexed { idx, abilityDef ->
            if (AbilityTrigger.parse(abilityDef.trigger) != trigger) return@forEachIndexed
            val key = AbilityCooldownTracker.key(def.id, idx)
            if (!AbilityCooldownTracker.isReady(player, key)) return@forEachIndexed
            if (abilityDef.chance < 1f && ThreadLocalRandom.current().nextFloat() > abilityDef.chance) return@forEachIndexed
            val ability = AbilityRegistry.get(abilityDef.type) ?: run {
                logger.warn("Unknown ability type '${abilityDef.type}' on '${def.id}'")
                return@forEachIndexed
            }
            try {
                ability.execute(AbilityContext(player, stack, world, target, targetPos, abilityDef.params))
                AbilityCooldownTracker.arm(player, key, abilityDef.cooldownTicks)
                any = true
            } catch (e: Exception) {
                logger.warn("Ability '${abilityDef.type}' on '${def.id}' threw: ${e.message}")
            }
        }
        return any
    }

    private fun resolveDefinition(stack: ItemStack): ItemDefinition? {
        val data = stack.get(DataComponents.CUSTOM_DATA) ?: return null
        val tag = data.copyTag()
        val id = tag.getString(ItemBuilder.FIW_TOOLS_ID_KEY).orElse(null) ?: return null
        return ItemRegistry.byId(id)
    }
}
