package com.fiw.tools.ability

import net.minecraft.core.particles.ParticleTypes
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.resources.ResourceKey
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.level.Level
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory per-player state for abilities that remember something between casts: ender_recall
 * anchors, tether marks, and scheduled bleed DoTs. Like cooldowns, this is intentionally
 * transient — it clears on disconnect and server stop and never touches NBT.
 */
object AbilityState {
    data class Anchor(val x: Double, val y: Double, val z: Double, val dimension: ResourceKey<Level>, val setAtTick: Long)

    /** caster -> recall anchor. */
    val recallAnchors: MutableMap<UUID, Anchor> = ConcurrentHashMap()

    /** caster -> (victim -> game-time the mark expires). */
    val tetherMarks: MutableMap<UUID, MutableMap<UUID, Long>> = ConcurrentHashMap()

    /** player -> game-time they last took damage, for in/out-of-combat passive conditions. */
    private val lastDamageTick: MutableMap<UUID, Long> = ConcurrentHashMap()

    /** Tracks active bleed DoTs keyed by victim UUID. */
    class BleedEntry(val dps: Float, val intervalTicks: Int, var pulsesLeft: Int) {
        var ticksUntilNext: Int = intervalTicks
    }
    val bleeds: MutableMap<UUID, BleedEntry> = ConcurrentHashMap()

    fun recordDamage(player: UUID, tick: Long) {
        lastDamageTick[player] = tick
    }

    /** Ticks since the player last took damage (a huge number if never, this session). */
    fun ticksSinceDamage(player: UUID, now: Long): Long = now - (lastDamageTick[player] ?: Long.MIN_VALUE / 2)

    /** Schedule a bleed DoT on [victim]. Replaces a weaker existing bleed; keeps the worse one. */
    fun addBleed(victim: UUID, dps: Float, pulses: Int, intervalTicks: Int) {
        bleeds.merge(victim, BleedEntry(dps, intervalTicks, pulses)) { old, new ->
            BleedEntry(maxOf(old.dps, new.dps), minOf(old.intervalTicks, new.intervalTicks), maxOf(old.pulsesLeft, new.pulsesLeft))
                .also { it.ticksUntilNext = minOf(old.ticksUntilNext, new.intervalTicks) }
        }
    }

    /** Called every server tick. Finds bleeding entities across all dimensions and applies damage. */
    fun processBleeds(server: MinecraftServer) {
        if (bleeds.isEmpty()) return
        val toRemove = mutableListOf<UUID>()
        for ((uuid, entry) in bleeds) {
            if (--entry.ticksUntilNext > 0) continue
            entry.ticksUntilNext = entry.intervalTicks
            var found = false
            for (level in server.allLevels) {
                val entity = level.getEntity(uuid) as? LivingEntity ?: continue
                entity.hurtServer(level, level.damageSources().magic(), entry.dps)
                level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, entity.x, entity.y + 1.0, entity.z, 4, 0.2, 0.3, 0.2, 0.0)
                found = true
                break
            }
            if (!found || --entry.pulsesLeft <= 0) toRemove.add(uuid)
        }
        for (uuid in toRemove) bleeds.remove(uuid)
    }

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
        bleeds.clear()
    }

    /** Records a fresh tether mark and returns true if [victim] was already marked and unexpired. */
    fun markTether(caster: UUID, victim: UUID, now: Long, durationTicks: Int): Boolean {
        val marks = tetherMarks.computeIfAbsent(caster) { ConcurrentHashMap() }
        val existing = marks[victim]
        marks[victim] = now + durationTicks
        return existing != null && existing >= now
    }
}
