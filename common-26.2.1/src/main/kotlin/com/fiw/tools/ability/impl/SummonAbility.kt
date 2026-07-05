package com.fiw.tools.ability.impl

import com.fiw.tools.ability.Ability
import com.fiw.tools.ability.AbilityContext
import com.fiw.tools.ability.optD
import com.fiw.tools.ability.optF
import com.fiw.tools.ability.optI
import com.fiw.tools.ability.optParticle
import com.fiw.tools.util.HolderAccess
import com.google.gson.JsonObject
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntitySpawnReason
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.TamableAnimal
import net.minecraft.world.entity.monster.Enemy
import net.minecraft.world.phys.AABB
import kotlin.math.cos
import kotlin.math.sin

/**
 * Summon temporary allies. Spawns `count` mobs of `entity` around the caster; tamables are tamed
 * to them, everything gets pointed at whoever last hurt the caster and re-aimed at nearby
 * hostiles while alive. Summons vanish in a poof after `duration` ticks. Entities are tagged so
 * strays from a crash or restart are culled on load instead of roaming forever.
 *
 * Works best with tamable or neutral mobs (wolf, iron_golem, snow_golem, vex...) — a summoned
 * zombie is still a zombie and may turn on you between retargets.
 */
object SummonAbility : Ability {
    private const val TAG = "fiw_summon"
    private const val RETARGET_TICKS = 20

    private class Summon(val world: ServerLevel, val mob: Mob, val ownerUuid: java.util.UUID, var ticksLeft: Int)

    private val summons: MutableList<Summon> = ArrayList()
    private var retargetCounter = 0

    fun tick() {
        synchronized(summons) {
            retargetCounter++
            val retarget = retargetCounter >= RETARGET_TICKS
            if (retarget) retargetCounter = 0
            val it = summons.iterator()
            while (it.hasNext()) {
                val s = it.next()
                if (s.mob.isRemoved || !s.mob.isAlive) {
                    it.remove()
                    continue
                }
                s.ticksLeft--
                if (s.ticksLeft <= 0) {
                    poof(s)
                    s.mob.discard()
                    it.remove()
                    continue
                }
                if (retarget) retarget(s)
            }
        }
    }

    /** Server stop: forget summons (their entities are culled on next load via [onEntityLoad]). */
    fun clear() {
        synchronized(summons) { summons.clear() }
    }

    /** Culls stray summons persisted through a crash/restart. Wire to the entity-load hook. */
    fun onEntityLoad(entity: Entity) {
        if (entity !is Mob || !entity.entityTags().contains(TAG)) return
        synchronized(summons) {
            if (summons.none { it.mob === entity }) entity.discard()
        }
    }

    override fun execute(ctx: AbilityContext): Boolean {
        val p = ctx.params
        val player = ctx.player
        val world = ctx.world

        val id = p.optS("entity", "minecraft:wolf")
        val typeId = Identifier.tryParse(id) ?: return false
        val holder = BuiltInRegistries.ENTITY_TYPE.get(typeId).orElse(null) ?: return false
        val type = HolderAccess.value(holder)

        val count = p.optI("count", 2).coerceIn(1, 8)
        val duration = p.optI("duration", 600).coerceIn(20, 72000)
        val radius = p.optD("spawnRadius", 1.8)
        val particle = p.optParticle("particle", ParticleTypes.CLOUD)
        val initialTarget = player.lastHurtByMob?.takeIf { it.isAlive && it.level() === world }

        var spawned = 0
        for (i in 0 until count) {
            val mob = type.create(world, EntitySpawnReason.TRIGGERED) as? Mob ?: break
            val angle = Math.PI * 2 * i / count
            val x = player.x + cos(angle) * radius
            val z = player.z + sin(angle) * radius
            mob.snapTo(x, player.y, z, player.yRot, 0f)
            mob.addTag(TAG)
            (mob as? TamableAnimal)?.let {
                it.setTame(true, true)
                it.setOwner(player)
            }
            if (initialTarget != null) mob.setTarget(initialTarget)
            world.addFreshEntity(mob)
            world.sendParticles(particle, x, player.y + 0.8, z, 12, 0.3, 0.5, 0.3, 0.02)
            synchronized(summons) { summons.add(Summon(world, mob, player.uuid, duration)) }
            spawned++
        }
        if (spawned == 0) return false

        val sound = soundById(p.optS("sound", "minecraft:entity.evoker.cast_spell"))
        if (sound != null) {
            world.playSound(null, player.x, player.y, player.z, sound, SoundSource.PLAYERS,
                p.optF("soundVolume", 1.0f), p.optF("soundPitch", 1.0f))
        }
        return true
    }

    /** Keep the summon fighting: aim it at whoever hurts its owner, else the nearest hostile. */
    private fun retarget(s: Summon) {
        val current = s.mob.target
        if (current != null && current.isAlive) return
        val owner = s.world.getPlayerByUUID(s.ownerUuid)
        val hurtBy = (owner as? ServerPlayer)?.lastHurtByMob?.takeIf { it.isAlive && it !== s.mob }
        if (hurtBy != null) {
            s.mob.target = hurtBy
            return
        }
        val box = AABB.ofSize(s.mob.position(), 20.0, 10.0, 20.0)
        val hostile = s.world.getEntitiesOfClass(LivingEntity::class.java, box) {
            it is Enemy && it.isAlive && !it.entityTags().contains(TAG)
        }.minByOrNull { it.distanceToSqr(s.mob) }
        if (hostile != null) s.mob.target = hostile
    }

    private fun poof(s: Summon) {
        s.world.sendParticles(ParticleTypes.CLOUD, s.mob.x, s.mob.y + s.mob.bbHeight / 2.0, s.mob.z,
            16, 0.3, 0.4, 0.3, 0.02)
        s.world.playSound(null, s.mob.x, s.mob.y, s.mob.z,
            net.minecraft.sounds.SoundEvents.ENDERMAN_TELEPORT, SoundSource.NEUTRAL, 0.6f, 1.4f)
    }

    private fun soundById(id: String): SoundEvent? {
        if (id.isEmpty()) return null
        val sid = Identifier.tryParse(id) ?: return null
        val holder = BuiltInRegistries.SOUND_EVENT.get(sid).orElse(null) ?: return null
        return HolderAccess.value(holder)
    }

    private fun JsonObject?.optS(key: String, default: String): String =
        this?.get(key)?.takeIf { it.isJsonPrimitive }?.asString ?: default
}
