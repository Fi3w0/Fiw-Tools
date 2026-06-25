package com.fiw.tools.ability

import net.minecraft.server.level.ServerPlayer
import net.minecraft.resources.ResourceKey
import net.minecraft.world.level.Level
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory per-player state for abilities that remember something between casts: ender_recall
 * anchors and tether marks. Like cooldowns, this is intentionally transient — it clears on
 * disconnect and server stop and never touches NBT.
 */
object AbilityState {
    data class Anchor(val x: Double, val y: Double, val z: Double, val dimension: ResourceKey<Level>, val setAtTick: Long)

    /** caster -> recall anchor. */
    val recallAnchors: MutableMap<UUID, Anchor> = ConcurrentHashMap()

    /** caster -> (victim -> game-time the mark expires). */
    val tetherMarks: MutableMap<UUID, MutableMap<UUID, Long>> = ConcurrentHashMap()

    /** player -> game-time they last took damage, for in/out-of-combat passive conditions. */
    private val lastDamageTick: MutableMap<UUID, Long> = ConcurrentHashMap()

    fun recordDamage(player: UUID, tick: Long) {
        lastDamageTick[player] = tick
    }

    /** Ticks since the player last took damage (a huge number if never, this session). */
    fun ticksSinceDamage(player: UUID, now: Long): Long = now - (lastDamageTick[player] ?: Long.MIN_VALUE / 2)

    fun clear(player: ServerPlayer) {
        val id = player.uuid
        recallAnchors.remove(id)
        tetherMarks.remove(id)
        lastDamageTick.remove(id)
    }

    fun clear() {
        recallAnchors.clear()
        tetherMarks.clear()
        lastDamageTick.clear()
    }

    /** Records a fresh tether mark and returns true if [victim] was already marked and unexpired. */
    fun markTether(caster: UUID, victim: UUID, now: Long, durationTicks: Int): Boolean {
        val marks = tetherMarks.computeIfAbsent(caster) { ConcurrentHashMap() }
        val existing = marks[victim]
        marks[victim] = now + durationTicks
        return existing != null && existing >= now
    }
}
