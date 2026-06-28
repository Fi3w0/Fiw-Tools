package com.fiw.tools.elemental

import net.minecraft.core.particles.ParticleTypes
import net.minecraft.server.MinecraftServer
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.LivingEntity
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Server-side elemental status tracker. Tracks FROZEN, SOAKED, and SHOCKED per entity UUID.
 *
 * - FROZEN: entity receives Slowness VI every sweep; ice particles. Cleared on fire damage (via ability code).
 * - SOAKED: marker only (water drip particles every 20t). Amplifies storm_chain damage.
 * - SHOCKED: deals 1.0 magic damage every 20 ticks + spark particles until duration expires.
 *
 * Call [processTick] from FiwToolsCommon.serverTick every server tick.
 * Call [reset] on serverStopped.
 */
object ElementalStatusTracker {

    private data class StatusEntry(var ticksLeft: Int, var effectTimer: Int = 0)

    private val statuses: MutableMap<UUID, MutableMap<ElementalStatus, StatusEntry>> =
        ConcurrentHashMap()

    private var tick = 0

    /** Apply or extend a status on an entity. Extending picks the longer duration. */
    fun apply(uuid: UUID, status: ElementalStatus, ticks: Int) {
        statuses.getOrPut(uuid) { ConcurrentHashMap() }
            .merge(status, StatusEntry(ticks)) { old, new ->
                StatusEntry(maxOf(old.ticksLeft, new.ticksLeft), old.effectTimer)
            }
    }

    fun has(uuid: UUID, status: ElementalStatus): Boolean =
        statuses[uuid]?.containsKey(status) == true

    fun clear(uuid: UUID, status: ElementalStatus) {
        statuses[uuid]?.remove(status)
        if (statuses[uuid]?.isEmpty() == true) statuses.remove(uuid)
    }

    fun clearAll(uuid: UUID) = statuses.remove(uuid)

    fun reset() = statuses.clear()

    fun processTick(server: MinecraftServer) {
        if (statuses.isEmpty()) return
        tick++
        val doFx = tick % 10 == 0
        val doShock = tick % 20 == 0

        val deadEntities = mutableListOf<UUID>()
        for ((uuid, entityStatuses) in statuses) {
            var entity: LivingEntity? = null
            if (doFx || doShock) {
                for (level in server.allLevels) {
                    entity = level.getEntity(uuid) as? LivingEntity
                    if (entity != null) break
                }
            }

            val toRemove = mutableListOf<ElementalStatus>()
            for ((status, entry) in entityStatuses) {
                if (--entry.ticksLeft <= 0) {
                    toRemove.add(status)
                    continue
                }
                if (entity == null || !entity.isAlive) continue

                when (status) {
                    ElementalStatus.FROZEN -> {
                        if (doFx) {
                            entity.addEffect(MobEffectInstance(MobEffects.SLOWNESS, 15, 5, false, false))
                            val level = entity.level()
                            if (level is net.minecraft.server.level.ServerLevel) {
                                level.sendParticles(ParticleTypes.SNOWFLAKE,
                                    entity.x, entity.y + 1.0, entity.z, 6, 0.3, 0.5, 0.3, 0.02)
                            }
                        }
                    }
                    ElementalStatus.SOAKED -> {
                        if (doShock) {
                            val level = entity.level()
                            if (level is net.minecraft.server.level.ServerLevel) {
                                level.sendParticles(ParticleTypes.DRIPPING_WATER,
                                    entity.x, entity.y + 1.8, entity.z, 8, 0.3, 0.1, 0.3, 0.0)
                            }
                        }
                    }
                    ElementalStatus.SHOCKED -> {
                        if (doShock) {
                            val level = entity.level() as? net.minecraft.server.level.ServerLevel ?: continue
                            entity.hurtServer(level, level.damageSources().magic(), 1.0f)
                            level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                                entity.x, entity.y + 1.0, entity.z, 10, 0.3, 0.5, 0.3, 0.05)
                        }
                    }
                }
            }
            for (s in toRemove) entityStatuses.remove(s)
            if (entityStatuses.isEmpty()) deadEntities.add(uuid)
        }
        for (uuid in deadEntities) statuses.remove(uuid)
    }
}
