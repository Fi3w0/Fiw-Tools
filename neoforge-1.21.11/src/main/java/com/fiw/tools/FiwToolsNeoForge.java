package com.fiw.tools;

import com.fiw.tools.ability.AbilityDispatcher;
import com.fiw.tools.command.FiwToolsCommand;
import com.fiw.tools.death.KeepOnDeathHandler;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@Mod("fiw_tools")
public final class FiwToolsNeoForge {
    public FiwToolsNeoForge(IEventBus modBus, ModContainer container) {
        FiwToolsCommon.INSTANCE.init(FMLPaths.CONFIGDIR.get());
        NeoForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        FiwToolsCommon.INSTANCE.serverStarting();
    }

    @SubscribeEvent
    public void onServerStopped(ServerStoppedEvent event) {
        FiwToolsCommon.INSTANCE.serverStopped();
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        FiwToolsCommon.INSTANCE.serverTick(event.getServer());
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        FiwToolsCommand.INSTANCE.register(event.getDispatcher());
    }

    @SubscribeEvent
    public void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            FiwToolsCommon.INSTANCE.playerJoin(player);
        }
    }

    @SubscribeEvent
    public void onPlayerLeave(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            FiwToolsCommon.INSTANCE.playerDisconnect(player);
        }
    }

    @SubscribeEvent
    public void onPlayerClone(PlayerEvent.Clone event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            KeepOnDeathHandler.INSTANCE.onClone(player, !event.isWasDeath());
        }
    }

    @SubscribeEvent
    public void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (event.getEntity() instanceof ServerPlayer player
                && event.getLevel() instanceof ServerLevel level
                && AbilityDispatcher.INSTANCE.onRightClick(player, event.getItemStack(), level, null, null)) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }

    @SubscribeEvent
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getEntity() instanceof ServerPlayer player
                && event.getLevel() instanceof ServerLevel level
                && AbilityDispatcher.INSTANCE.onRightClick(player, event.getItemStack(), level, null, null)) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }

    @SubscribeEvent
    public void onRightClickEntity(PlayerInteractEvent.EntityInteract event) {
        if (event.getEntity() instanceof ServerPlayer player
                && event.getLevel() instanceof ServerLevel level) {
            LivingEntity target = event.getTarget() instanceof LivingEntity living ? living : null;
            if (AbilityDispatcher.INSTANCE.onRightClick(player, event.getItemStack(), level, target, event.getTarget().position())) {
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.SUCCESS);
            }
        }
    }

    @SubscribeEvent
    public void onAttackEntity(AttackEntityEvent event) {
        if (event.getEntity() instanceof ServerPlayer player
                && event.getTarget() instanceof LivingEntity target
                && player.level() instanceof ServerLevel level) {
            AbilityDispatcher.INSTANCE.onAttack(player, player.getMainHandItem(), level, target);
        }
    }

    @SubscribeEvent
    public void onLivingDeath(LivingDeathEvent event) {
        AbilityDispatcher.INSTANCE.onKill(event.getEntity(), event.getSource());
    }

    @SubscribeEvent
    public void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            AbilityDispatcher.INSTANCE.onHurt(player, event.getSource(), event.getAmount());
        }
    }

    @SubscribeEvent
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player
                && event.getLevel() instanceof ServerLevel level) {
            AbilityDispatcher.INSTANCE.onBlockBreak(player, level, event.getPos());
        }
    }
}
