package com.fiw.tools

import com.fiw.tools.ability.AbilityCooldownTracker
import com.fiw.tools.ability.AbilityRegistry
import com.fiw.tools.ability.AbilityState
import com.fiw.tools.ability.PassiveHandler
import com.fiw.tools.elemental.ElementalStatusTracker
import com.fiw.tools.ability.ZoneEffects
import com.fiw.tools.ability.impl.ArcSlashAbility
import com.fiw.tools.ability.impl.ProjectileBurstAbility
import com.fiw.tools.config.ItemRegistry
import com.fiw.tools.curse.CurseHandler
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
        logger.info("Fiw Tools loaded ${report.loaded} item(s); ${report.failed.size} failed.")
    }

    fun serverTick(server: MinecraftServer) {
        PassiveHandler.tick(server)
        CurseHandler.tick(server)
        ItemSyncHandler.tick(server)
        ZoneEffects.tick()
        ProjectileBurstAbility.tick()
        ArcSlashAbility.tick()
        AbilityState.processBleeds(server)
        ElementalStatusTracker.processTick(server)
    }

    fun playerJoin(player: ServerPlayer) {
        ItemSyncHandler.onJoin(player)
    }

    fun playerDisconnect(player: ServerPlayer) {
        AbilityCooldownTracker.clear(player)
        AbilityState.clear(player)
    }

    fun serverStopped() {
        ProjectileBurstAbility.clear()
        ArcSlashAbility.clear()
        ZoneEffects.clear()
        AbilityState.clear()
        ElementalStatusTracker.reset()
    }
}
