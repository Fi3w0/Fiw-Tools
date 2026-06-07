package com.fiw.tools

import com.fiw.tools.ability.AbilityCooldownTracker
import com.fiw.tools.ability.AbilityDispatcher
import com.fiw.tools.ability.AbilityRegistry
import com.fiw.tools.ability.AbilityState
import com.fiw.tools.ability.PassiveHandler
import com.fiw.tools.ability.ZoneEffects
import com.fiw.tools.ability.impl.ArcSlashAbility
import com.fiw.tools.ability.impl.ProjectileBurstAbility
import com.fiw.tools.command.FiwToolsCommand
import com.fiw.tools.config.ItemRegistry
import com.fiw.tools.curse.CurseHandler
import com.fiw.tools.death.KeepOnDeathHandler
import com.fiw.tools.sync.ItemSyncHandler
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import org.slf4j.LoggerFactory

object FiwTools : ModInitializer {
    private val logger = LoggerFactory.getLogger("fiw-tools")

    override fun onInitialize() {
        logger.info("Fiw Tools starting up.")

        AbilityRegistry.init()
        AbilityCooldownTracker.init()
        AbilityDispatcher.init()
        ProjectileBurstAbility.init()
        ArcSlashAbility.init()
        ZoneEffects.init()
        AbilityState.init()
        PassiveHandler.init()
        CurseHandler.init()
        KeepOnDeathHandler.init()
        ItemSyncHandler.init()

        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            FiwToolsCommand.register(dispatcher)
        }

        ServerLifecycleEvents.SERVER_STARTING.register { _ ->
            val report = ItemRegistry.loadAll()
            logger.info("Fiw Tools loaded ${report.loaded} item(s); ${report.failed.size} failed.")
        }

        // Drop any in-flight visual effects so they never tick into the next world (integrated-server swaps).
        ServerLifecycleEvents.SERVER_STOPPED.register { _ ->
            ProjectileBurstAbility.clear()
            ArcSlashAbility.clear()
            ZoneEffects.clear()
            AbilityState.clear()
        }
    }
}
