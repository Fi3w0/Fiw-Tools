package com.fiw.tools.ability.impl

import com.fiw.tools.ability.Ability
import com.fiw.tools.ability.AbilityContext
import com.fiw.tools.ability.AffectScope
import com.fiw.tools.ability.ZoneEffects
import com.fiw.tools.ability.collectTargets
import com.fiw.tools.ability.optD
import com.fiw.tools.ability.optF
import com.fiw.tools.ability.optI
import com.fiw.tools.ability.scope
import net.minecraft.core.particles.ParticleOptions
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.core.registries.Registries
import net.minecraft.resources.Identifier
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.tags.TagKey
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ExperienceOrb
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import java.util.UUID
import kotlin.math.cos
import kotlin.math.sin

/**
 * PvE-leaning abilities: AoE clear, group damage and crowd control. By default these hit any non-player
 * mob (`affects` defaults to `mobs`) so they work on whatever you're fighting; set `affects` to
 * `hostiles` to limit them to hostile mobs, or `players` / `all` for PvP.
 */

/** Splashes flat damage to other nearby enemies around the entity you hit. */
object CleaveAbility : Ability {
    override fun execute(ctx: AbilityContext): Boolean {
        val center = ctx.target?.position() ?: ctx.targetPos ?: return false
        val radius = ctx.params.optD("radius", 3.0)
        val damage = ctx.params.optF("damage", 3f)
        val scope = ctx.params.scope("affects", AffectScope.MOBS)
        val world = ctx.world
        val src = world.damageSources().playerAttack(ctx.player)
        var hit = false
        for (e in collectTargets(world, center, radius, ctx.player, scope)) {
            if (e === ctx.target) continue
            e.hurtServer(world, src, damage)
            world.sendParticles(ParticleTypes.SWEEP_ATTACK, e.x, e.y + 0.8, e.z, 1, 0.0, 0.0, 0.0, 0.0)
            hit = true
        }
        return hit
    }
}

/** 360° sweep: damages + knocks back nearby enemies and tugs loose items/XP toward the caster. */
object WhirlwindAbility : Ability {
    override fun execute(ctx: AbilityContext): Boolean {
        val radius = ctx.params.optD("radius", 4.0)
        val damage = ctx.params.optF("damage", 3f)
        val knockback = ctx.params.optD("knockback", 0.6)
        val scope = ctx.params.scope("affects", AffectScope.MOBS)
        val player = ctx.player
        val world = ctx.world
        val center = player.position()
        val src = world.damageSources().playerAttack(player)

        for (e in collectTargets(world, center, radius, player, scope)) {
            e.hurtServer(world, src, damage)
            val delta = Vec3(e.x - center.x, 0.0, e.z - center.z)
            if (delta.length() > 0.05) {
                val dir = delta.normalize()
                e.push(dir.x * knockback, 0.35, dir.z * knockback)
                e.hurtMarked = true
            }
        }

        val pullBox = AABB.ofSize(center, radius * 2, radius * 2, radius * 2)
        val magnet = player.position().add(0.0, 0.5, 0.0)
        for (item in world.getEntitiesOfClass(ItemEntity::class.java, pullBox) { it.isAlive }) {
            item.setDeltaMovement(magnet.subtract(item.position()).normalize().scale(0.4))
        }
        for (orb in world.getEntitiesOfClass(ExperienceOrb::class.java, pullBox) { it.isAlive }) {
            orb.setDeltaMovement(magnet.subtract(orb.position()).normalize().scale(0.4))
        }

        for (i in 0 until 48) {
            val a = i / 48.0 * Math.PI * 2
            world.sendParticles(ParticleTypes.SWEEP_ATTACK, center.x + cos(a) * radius, center.y + 1.0, center.z + sin(a) * radius, 1, 0.0, 0.0, 0.0, 0.0)
        }
        world.playSound(null, player.x, player.y, player.z,
            SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.9f, 0.7f)
        return true
    }
}

/** Bonus damage against a configurable entity-type tag (`targetType`, e.g. `minecraft:undead`). */
object SlayingEdgeAbility : Ability {
    override fun execute(ctx: AbilityContext): Boolean {
        val target = ctx.target ?: return false
        val typeRaw = ctx.params.get("targetType")?.takeIf { !it.isJsonNull }?.asString ?: "minecraft:undead"
        val tagId = Identifier.tryParse(typeRaw) ?: return false
        val tag: TagKey<net.minecraft.world.entity.EntityType<*>> = TagKey.create(Registries.ENTITY_TYPE, tagId)
        if (!target.type.`is`(tag)) return false
        val bonus = ctx.params.optF("bonus", 4f)
        val world = ctx.world
        target.hurtServer(world, world.damageSources().playerAttack(ctx.player), bonus)
        world.sendParticles(ParticleTypes.ENCHANTED_HIT, target.x, target.y + 1.0, target.z, 12, 0.3, 0.4, 0.3, 0.1)
        world.playSound(null, target.x, target.y, target.z,
            SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 0.8f, 1.4f)
        return true
    }
}

/** On kill: heal and refresh a short Strength/Speed buff that decays once you stop killing. */
object SoulHarvestAbility : Ability {
    override fun execute(ctx: AbilityContext): Boolean {
        val player = ctx.player
        val heal = ctx.params.optF("heal", 2f)
        val duration = ctx.params.optI("buffDuration", 100)
        val strengthAmp = ctx.params.optI("strengthAmplifier", 0)
        val speedAmp = ctx.params.optI("speedAmplifier", 0)
        player.heal(heal)
        player.addEffect(MobEffectInstance(MobEffects.STRENGTH, duration, strengthAmp))
        player.addEffect(MobEffectInstance(MobEffects.SPEED, duration, speedAmp))
        ctx.world.sendParticles(ParticleTypes.SOUL, player.x, player.y + 1.0, player.z, 12, 0.3, 0.5, 0.3, 0.02)
        ctx.world.playSound(null, player.x, player.y, player.z,
            SoundEvents.SOUL_ESCAPE, SoundSource.PLAYERS, 0.6f, 1.2f)
        return true
    }
}

/** Lightning that arcs from the struck target to nearby enemies for falling-off damage each jump. */
object ChainLightningAbility : Ability {
    private val SPARK: ParticleOptions = ParticleTypes.ELECTRIC_SPARK

    override fun execute(ctx: AbilityContext): Boolean {
        val start = ctx.target ?: return false
        val jumps = ctx.params.optI("jumps", 3).coerceIn(1, 10)
        var damage = ctx.params.optF("damage", 4f)
        val falloff = ctx.params.optF("falloffPercent", 0.3f).coerceIn(0f, 0.9f)
        val range = ctx.params.optD("jumpRange", 5.0)
        val scope = ctx.params.scope("affects", AffectScope.MOBS)
        val player = ctx.player
        val world = ctx.world
        val src = world.damageSources().playerAttack(player)

        particleLine(world, player.position().add(0.0, 1.0, 0.0), start.position().add(0.0, 1.0, 0.0))
        val hit = HashSet<UUID>()
        hit.add(start.uuid)
        var current: LivingEntity = start
        var jumped = false
        var remaining = jumps
        while (remaining > 0) {
            val next = collectTargets(world, current.position(), range, player, scope)
                .filter { it.uuid !in hit }
                .minByOrNull { it.position().distanceTo(current.position()) } ?: break
            particleLine(world, current.position().add(0.0, 1.0, 0.0), next.position().add(0.0, 1.0, 0.0))
            next.hurtServer(world, src, damage)
            hit.add(next.uuid)
            current = next
            damage *= (1f - falloff)
            jumped = true
            remaining--
        }
        if (jumped) {
            world.playSound(null, start.x, start.y, start.z,
                SoundEvents.TRIDENT_THROW, SoundSource.PLAYERS, 0.7f, 1.6f)
        }
        return jumped
    }

    private fun particleLine(world: ServerLevel, a: Vec3, b: Vec3) {
        val steps = (a.distanceTo(b) * 4).toInt().coerceIn(2, 40)
        for (i in 0..steps) {
            val t = i.toDouble() / steps
            world.sendParticles(SPARK, a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t, a.z + (b.z - a.z) * t, 1, 0.0, 0.0, 0.0, 0.0)
        }
    }
}

/** Drops a pulsing zone ahead of you that drags enemies toward its center for a few seconds. */
object GravityWellAbility : Ability {
    override fun execute(ctx: AbilityContext): Boolean {
        val placeRange = ctx.params.optD("range", 4.0)
        val radius = ctx.params.optD("radius", 5.0)
        val pull = ctx.params.optD("pullStrength", 0.6)
        val duration = ctx.params.optI("duration", 60)
        val period = ctx.params.optI("period", 5)
        val scope = ctx.params.scope("affects", AffectScope.MOBS)
        val player = ctx.player
        val world = ctx.world
        val look = player.lookAngle
        val center = Vec3(player.x + look.x * placeRange, player.y, player.z + look.z * placeRange)

        ZoneEffects.repeating(duration, period) {
            if (!player.isAlive) return@repeating
            for (e in collectTargets(world, center, radius, player, scope)) {
                val delta = center.subtract(e.position())
                if (delta.length() > 0.4) {
                    val v = delta.normalize().scale(pull)
                    e.push(v.x, v.y * 0.2, v.z)
                    e.hurtMarked = true
                }
            }
            for (i in 0 until 20) {
                val a = i / 20.0 * Math.PI * 2
                world.sendParticles(ParticleTypes.PORTAL, center.x + cos(a) * radius, center.y + 0.3, center.z + sin(a) * radius, 1, 0.0, 0.0, 0.0, 0.0)
            }
        }
        world.playSound(null, center.x, center.y, center.z,
            SoundEvents.EVOKER_CAST_SPELL, SoundSource.PLAYERS, 0.8f, 0.8f)
        return true
    }
}

/** Leap, then a telegraphed delayed impact: AoE damage + outward knockback where you land. */
object GroundSlamAbility : Ability {
    override fun execute(ctx: AbilityContext): Boolean {
        val windup = ctx.params.optI("windup", 10)
        val radius = ctx.params.optD("radius", 4.0)
        val damage = ctx.params.optF("damage", 6f)
        val knockback = ctx.params.optD("knockback", 1.0)
        val hop = ctx.params.optD("hop", 0.45)
        val scope = ctx.params.scope("affects", AffectScope.MOBS)
        val player = ctx.player
        val world = ctx.world

        player.push(0.0, hop, 0.0)
        player.hurtMarked = true
        // Telegraph the landing zone.
        for (i in 0 until 24) {
            val a = i / 24.0 * Math.PI * 2
            world.sendParticles(ParticleTypes.FLAME, player.x + cos(a) * radius, player.y + 0.2, player.z + sin(a) * radius, 1, 0.0, 0.0, 0.0, 0.0)
        }

        ZoneEffects.schedule(windup) {
            if (!player.isAlive) return@schedule
            val c = player.position()
            val src = world.damageSources().playerAttack(player)
            for (e in collectTargets(world, c, radius, player, scope)) {
                e.hurtServer(world, src, damage)
                val delta = Vec3(e.x - c.x, 0.0, e.z - c.z)
                if (delta.length() > 0.05) {
                    val dir = delta.normalize()
                    e.push(dir.x * knockback, 0.5, dir.z * knockback)
                    e.hurtMarked = true
                }
            }
            for (i in 0 until 48) {
                val a = i / 48.0 * Math.PI * 2
                val r = radius * (0.4 + (i % 3) * 0.3)
                world.sendParticles(ParticleTypes.CRIT, c.x + cos(a) * r, c.y + 0.2, c.z + sin(a) * r, 1, 0.0, 0.0, 0.0, 0.0)
            }
            world.playSound(null, c.x, c.y, c.z, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.9f, 0.7f)
        }
        return true
    }
}
