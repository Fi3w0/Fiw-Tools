package com.fiw.tools.ability

import com.fiw.tools.FiwToolsPlatform
import net.minecraft.server.level.ServerPlayer
import org.slf4j.LoggerFactory
import java.util.concurrent.Executors
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.ceil

object AbilityCooldownTracker {
    private val cooldowns: MutableMap<UUID, MutableMap<String, Long>> = ConcurrentHashMap()

    private val logger = LoggerFactory.getLogger("fiw-tools/cooldowns")
    private val snapshots = mutableMapOf<UUID, Map<String, Long>>()
    private val players = mutableMapOf<UUID, ServerPlayer>()
    private var writer = Executors.newSingleThreadExecutor()
    private fun store() = CooldownStore(FiwToolsPlatform.configRoot().parent.resolve("data/cooldowns"))

    fun restore(player: ServerPlayer) {
        if (writer.isShutdown) writer = Executors.newSingleThreadExecutor()
        players[player.uuid] = player
        try {
            val restored = CooldownStore.restore(snapshots[player.uuid] ?: store().load(player.uuid), player.level().gameTime)
            cooldowns[player.uuid] = ConcurrentHashMap(restored)
        } catch (e: Exception) {
            logger.error("Could not restore cooldowns for ${player.uuid}", e)
        }
    }

    fun saveAndClear(player: ServerPlayer) {
        val remaining = CooldownStore.remaining(cooldowns[player.uuid].orEmpty(), player.level().gameTime)
        snapshots[player.uuid] = remaining
        val store = store()
        writer.submit {
            try {
                store.save(player.uuid, remaining)
            } catch (e: Exception) {
                logger.error("Could not save cooldowns for ${player.uuid}", e)
            }
        }
        players.remove(player.uuid)
        clear(player)
    }

    fun stop() {
        players.values.toList().forEach { saveAndClear(it) }
        writer.shutdown()
        writer.awaitTermination(30, java.util.concurrent.TimeUnit.SECONDS)
        cooldowns.clear()
        snapshots.clear()
    }

    fun clear(player: ServerPlayer) {
        cooldowns.remove(player.uuid)
    }

    fun key(itemId: String?, abilityIndex: Int): String = "${itemId ?: "_"}#$abilityIndex"

    /** Cooldown key for one ability slot — the shared group key when a `cooldownGroup` is set. */
    fun keyFor(itemId: String?, abilityIndex: Int, group: String?): String =
        if (group.isNullOrEmpty()) key(itemId, abilityIndex) else "group:${group.lowercase()}"

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

    fun clearKeys(player: ServerPlayer, keys: Iterable<String>): Int {
        val map = cooldowns[player.uuid] ?: return 0
        var changed = 0
        for (key in keys) {
            if (map.remove(key) != null) changed++
        }
        if (map.isEmpty()) cooldowns.remove(player.uuid)
        return changed
    }

    fun reduceKeys(player: ServerPlayer, keys: Iterable<String>, ticks: Int, percent: Float): Int {
        if (ticks <= 0 && percent <= 0f) return 0
        val map = cooldowns[player.uuid] ?: return 0
        val now = player.level().gameTime
        var changed = 0
        for (key in keys) {
            val until = map[key] ?: continue
            if (until <= now) continue
            val percentTicks = if (percent > 0f) ceil((until - now) * percent.toDouble()).toLong() else 0L
            val total = ticks.toLong() + percentTicks
            if (total <= 0) continue
            val reduced = until - total
            if (reduced <= now) map.remove(key) else map[key] = reduced
            changed++
        }
        if (map.isEmpty()) cooldowns.remove(player.uuid)
        return changed
    }
}
