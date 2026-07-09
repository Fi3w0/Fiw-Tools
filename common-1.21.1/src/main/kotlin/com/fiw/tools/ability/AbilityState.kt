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

    /** victim -> their active hard stun (expiry + slowness strength), see `stun_strike`. */
    class StunEntry(val until: Long, val slownessAmplifier: Int)
    val stuns: MutableMap<UUID, StunEntry> = ConcurrentHashMap()

    /** player -> their active aegis barrier (remaining pool + expiry), see `aegis_shield`. */
    class AegisEntry(var pool: Float, val expiry: Long)
    val aegisShields: MutableMap<UUID, AegisEntry> = ConcurrentHashMap()

    /** Hard-stun [victim] until at least [untilTick]; extends an existing stun, never shortens it. */
    fun addStun(victim: UUID, untilTick: Long, slownessAmplifier: Int) {
        stuns.merge(victim, StunEntry(untilTick, slownessAmplifier)) { a, b ->
            StunEntry(maxOf(a.until, b.until), maxOf(a.slownessAmplifier, b.slownessAmplifier))
        }
    }

    /** Raise an aegis barrier on [player]: [pool] damage absorbed, expiring at [expiry]. */
    fun addAegis(player: UUID, pool: Float, expiry: Long) {
        aegisShields[player] = AegisEntry(pool, expiry)
    }

    /**
     * Called when [player] is about to take [amount] damage from [source]. Returns true if an active
     * aegis barrier swallowed the hit (the caller must then cancel the damage). Each hit is fully
     * absorbed while the barrier holds; the hit that drains the pool shatters it (and is still
     * absorbed). Damage that bypasses invulnerability (`/kill`, the void) and the mod's own curse
     * drain are never absorbed — a barrier is not an escape from either.
     */
    fun aegisAbsorbs(player: ServerPlayer, source: net.minecraft.world.damagesource.DamageSource, amount: Float): Boolean {
        val entry = aegisShields[player.uuid] ?: return false
        if (player.level().gameTime >= entry.expiry) { aegisShields.remove(player.uuid); return false }
        if (source.`is`(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) return false
        if (source.`is`(com.fiw.tools.curse.CurseHandler.CURSE_DAMAGE_TYPE)) return false
        entry.pool -= amount
        player.serverLevel().sendParticles(ParticleTypes.END_ROD, player.x, player.y + 1.0, player.z, 12, 0.4, 0.6, 0.4, 0.02)
        if (entry.pool <= 0f) aegisShields.remove(player.uuid)
        return true
    }

    /** Each tick: hold every stunned entity in place and keep its disabling effects topped up. */
    fun processStuns(server: MinecraftServer) {
        if (stuns.isEmpty()) return
        val now = server.overworld().gameTime
        val toRemove = mutableListOf<UUID>()
        for ((uuid, entry) in stuns) {
            if (now >= entry.until) { toRemove.add(uuid); continue }
            var found = false
            for (level in server.allLevels) {
                val e = level.getEntity(uuid) as? LivingEntity ?: continue
                e.deltaMovement = net.minecraft.world.phys.Vec3.ZERO
                e.hurtMarked = true
                e.addEffect(net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 10, entry.slownessAmplifier, false, false))
                e.addEffect(net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.WEAKNESS, 10, 250, false, false))
                e.addEffect(net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.DIG_SLOWDOWN, 10, 250, false, false))
                found = true
                break
            }
            if (!found) toRemove.add(uuid)
        }
        for (uuid in toRemove) stuns.remove(uuid)
    }

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
                entity.hurt(level.damageSources().magic(), entry.dps)
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
        stuns.remove(id)
        aegisShields.remove(id)
    }

    fun clear() {
        recallAnchors.clear()
        tetherMarks.clear()
        lastDamageTick.clear()
        bleeds.clear()
        stuns.clear()
        aegisShields.clear()
    }

    /** Records a fresh tether mark and returns true if [victim] was already marked and unexpired. */
    fun markTether(caster: UUID, victim: UUID, now: Long, durationTicks: Int): Boolean {
        val marks = tetherMarks.computeIfAbsent(caster) { ConcurrentHashMap() }
        val existing = marks[victim]
        marks[victim] = now + durationTicks
        return existing != null && existing >= now
    }
}
