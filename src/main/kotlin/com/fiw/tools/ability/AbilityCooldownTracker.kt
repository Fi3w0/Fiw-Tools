package com.fiw.tools.ability

import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.minecraft.server.level.ServerPlayer
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object AbilityCooldownTracker {
    private val cooldowns: MutableMap<UUID, MutableMap<String, Long>> = ConcurrentHashMap()

    fun init() {
        ServerPlayConnectionEvents.DISCONNECT.register { handler, _ ->
            cooldowns.remove(handler.player.uuid)
        }
    }

    fun key(itemId: String?, abilityIndex: Int): String = "${itemId ?: "_"}#$abilityIndex"

    fun isReady(player: ServerPlayer, key: String): Boolean {
        val map = cooldowns[player.uuid] ?: return true
        val until = map[key] ?: return true
        return player.level().gameTime >= until
    }

    fun arm(player: ServerPlayer, key: String, cooldownTicks: Int) {
        if (cooldownTicks <= 0) return
        val map = cooldowns.computeIfAbsent(player.uuid) { ConcurrentHashMap() }
        map[key] = player.level().gameTime + cooldownTicks
    }
}
