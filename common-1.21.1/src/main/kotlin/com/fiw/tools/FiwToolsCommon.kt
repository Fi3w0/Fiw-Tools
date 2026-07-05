package com.fiw.tools

import com.fiw.tools.ability.AbilityCooldownTracker
import com.fiw.tools.ability.AbilityRegistry
import com.fiw.tools.ability.AbilityState
import com.fiw.tools.ability.PassiveHandler
import com.fiw.tools.elemental.ElementalStatusTracker
import com.fiw.tools.ability.ZoneEffects
import com.fiw.tools.ability.impl.ArcSlashAbility
import com.fiw.tools.ability.impl.ProjectileBurstAbility
import com.fiw.tools.ability.impl.ArrowRainAbility
import com.fiw.tools.ability.impl.BeamAbility
import com.fiw.tools.ability.impl.GrappleAbility
import com.fiw.tools.ability.impl.SlashWaveAbility
import com.fiw.tools.ability.impl.SummonAbility
import com.fiw.tools.ability.impl.SonicBoomAbility
import com.fiw.tools.ability.impl.SlashBarrage
import com.fiw.tools.awaken.AwakeningHandler
import com.fiw.tools.bind.BindingHandler
import com.fiw.tools.config.ItemRegistry
import com.fiw.tools.curse.CurseHandler
import com.fiw.tools.infinite.InfiniteItems
import com.fiw.tools.recipe.CraftingHandler
import com.fiw.tools.recipe.RecipeRegistry
import com.fiw.tools.sync.ItemSyncHandler
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import org.slf4j.LoggerFactory
import java.nio.file.Path

object FiwToolsCommon {
    private val logger = LoggerFactory.getLogger("fiw-tools")

    fun init(configDir: Path) {
        FiwToolsPlatform.init(configDir)
        logger.info("Fiw Tools starting up.")
        AbilityRegistry.init()
    }

    fun serverStarting() {
        val report = ItemRegistry.loadAll()
        val recipes = RecipeRegistry.loadAll()
        logger.info("Fiw Tools loaded ${report.loaded} item(s); ${report.failed.size} failed. " +
            "${recipes.loaded} recipe(s); ${recipes.failed.size} failed.")
    }

    fun serverTick(server: MinecraftServer) {
        CraftingHandler.tick(server)
        InfiniteItems.tick(server)
        AwakeningHandler.tick(server)
        BindingHandler.tick(server)
        com.fiw.tools.charge.ChargeHandler.tick(server)
        com.fiw.tools.lore.DynamicLoreHandler.tick(server)
        PassiveHandler.tick(server)
        CurseHandler.tick(server)
        ItemSyncHandler.tick(server)
        ZoneEffects.tick()
        ProjectileBurstAbility.tick()
        ArcSlashAbility.tick()
        SlashBarrage.tick()
        BeamAbility.tick()
        GrappleAbility.tick()
        SummonAbility.tick()
        SlashWaveAbility.tick()
        ArrowRainAbility.tick()
        SonicBoomAbility.tick()
        AbilityState.processBleeds(server)
        ElementalStatusTracker.processTick(server)
    }

    fun playerJoin(player: ServerPlayer) {
        ItemSyncHandler.onJoin(player)
    }

    fun playerDisconnect(player: ServerPlayer) {
        AbilityCooldownTracker.clear(player)
        AbilityState.clear(player)
        CraftingHandler.clear(player)
    }

    fun serverStopped() {
        ProjectileBurstAbility.clear()
        ArcSlashAbility.clear()
        SlashBarrage.clear()
        BeamAbility.clear()
        GrappleAbility.clear()
        SummonAbility.clear()
        SlashWaveAbility.clear()
        ArrowRainAbility.clear()
        SonicBoomAbility.clear()
        ZoneEffects.clear()
        AbilityState.clear()
        ElementalStatusTracker.reset()
    }
}
