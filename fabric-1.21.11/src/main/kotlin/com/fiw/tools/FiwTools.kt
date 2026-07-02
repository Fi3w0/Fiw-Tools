package com.fiw.tools

import com.fiw.tools.ability.AbilityDispatcher
import com.fiw.tools.command.FiwToolsCommand
import com.fiw.tools.death.KeepOnDeathHandler
import com.fiw.tools.infinite.InfiniteItems
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.event.player.AttackEntityCallback
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents
import net.fabricmc.fabric.api.event.player.UseBlockCallback
import net.fabricmc.fabric.api.event.player.UseEntityCallback
import net.fabricmc.fabric.api.event.player.UseItemCallback
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.LivingEntity

object FiwTools : ModInitializer {
    override fun onInitialize() {
        FiwToolsCommon.init(FabricLoader.getInstance().configDir)

        UseItemCallback.EVENT.register { player, world, hand ->
            if (world.isClientSide || player !is ServerPlayer) return@register InteractionResult.PASS
            if (AbilityDispatcher.onRightClick(player, player.getItemInHand(hand), player.level(), null, null)) {
                InteractionResult.SUCCESS
            } else InteractionResult.PASS
        }

        UseBlockCallback.EVENT.register { player, world, hand, _ ->
            if (world.isClientSide || player !is ServerPlayer) return@register InteractionResult.PASS
            if (AbilityDispatcher.onRightClick(player, player.getItemInHand(hand), player.level(), null, null)) {
                InteractionResult.SUCCESS
            } else InteractionResult.PASS
        }

        UseEntityCallback.EVENT.register { player, world, hand, entity, _ ->
            if (world.isClientSide || player !is ServerPlayer) return@register InteractionResult.PASS
            val target = entity as? LivingEntity
            if (AbilityDispatcher.onRightClick(player, player.getItemInHand(hand), player.level(), target, entity.position())) {
                InteractionResult.SUCCESS
            } else InteractionResult.PASS
        }

        AttackEntityCallback.EVENT.register { player, world, hand, target, _ ->
            if (world.isClientSide || player !is ServerPlayer || target !is LivingEntity) return@register InteractionResult.PASS
            AbilityDispatcher.onAttack(player, player.getItemInHand(hand), player.level(), target)
            InteractionResult.PASS
        }

        ServerLivingEntityEvents.AFTER_DEATH.register(AbilityDispatcher::onKill)
        ServerLivingEntityEvents.ALLOW_DAMAGE.register { entity, source, amount ->
            if (entity is ServerPlayer) AbilityDispatcher.onHurt(entity, source, amount)
            true
        }
        PlayerBlockBreakEvents.AFTER.register { world, player, pos, _, _ ->
            if (player is ServerPlayer) AbilityDispatcher.onBlockBreak(player, player.level(), pos)
        }

        ServerPlayConnectionEvents.JOIN.register { handler, _, _ -> FiwToolsCommon.playerJoin(handler.player) }
        ServerPlayConnectionEvents.DISCONNECT.register { handler, _ -> FiwToolsCommon.playerDisconnect(handler.player) }
        ServerPlayerEvents.COPY_FROM.register { _, newPlayer, alive -> KeepOnDeathHandler.onClone(newPlayer, alive) }
        ServerEntityEvents.ENTITY_LOAD.register { entity, _ -> InfiniteItems.onEntityLoad(entity) }
        ServerTickEvents.END_SERVER_TICK.register(FiwToolsCommon::serverTick)
        ServerLifecycleEvents.SERVER_STARTING.register { FiwToolsCommon.serverStarting() }
        ServerLifecycleEvents.SERVER_STOPPED.register { FiwToolsCommon.serverStopped() }

        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            FiwToolsCommand.register(dispatcher)
        }
    }
}
