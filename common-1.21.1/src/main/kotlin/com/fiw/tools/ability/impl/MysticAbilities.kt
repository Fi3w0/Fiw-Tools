package com.fiw.tools.ability.impl

import com.fiw.tools.ability.Ability
import com.fiw.tools.ability.AbilityContext
import com.fiw.tools.ability.AbilityState
import com.fiw.tools.ability.AffectScope
import com.fiw.tools.ability.ZoneEffects
import com.fiw.tools.ability.collectTargets
import com.fiw.tools.ability.optD
import com.fiw.tools.ability.optF
import com.fiw.tools.ability.optI
import com.fiw.tools.ability.optParticle
import com.fiw.tools.ability.scope
import com.fiw.tools.util.TextParser
import net.minecraft.core.BlockPos
import net.minecraft.core.particles.ParticleOptions
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.Vec3
import kotlin.math.cos
import kotlin.math.sin

/**
 * `void_rift` — a proactive, telegraphed area denial. Right-click plants a rift at the block you're
 * looking at; for `delay` ticks it hums and drags nearby entities inward, then detonates for
 * `damage` in `radius`. You cast it *before* the enemy arrives — the skill is prediction and herding
 * targets into it (great paired with a pull), not point-and-delete.
 */
object VoidRiftAbility : Ability {
    override fun execute(ctx: AbilityContext): Boolean {
        val p = ctx.params
        val player = ctx.player
        val world = ctx.world
        val range = p.optD("range", 20.0).coerceIn(2.0, 64.0)
        val delay = p.optI("delay", 40).coerceIn(5, 200)
        val radius = p.optD("radius", 4.0).coerceIn(1.0, 16.0)
        val damage = p.optF("damage", 10f)
        val pull = p.optD("pull", 0.18)
        val scope = p.scope("affects", AffectScope.ALL)
        val ring = p.optParticle("particle", ParticleTypes.REVERSE_PORTAL)

        val eye = player.eyePosition
        val end = eye.add(player.lookAngle.normalize().scale(range))
        val center = ctx.targetPos
            ?: world.clip(ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player)).location

        // Telegraph: a shrinking ring plus an inward tug so the rift visibly "wants" everything nearby.
        ZoneEffects.repeating(delay, 4) {
            for (i in 0 until 20) {
                val a = i / 20.0 * Math.PI * 2
                world.sendParticles(ring, center.x + cos(a) * radius, center.y + 0.4, center.z + sin(a) * radius, 1, 0.0, 0.0, 0.0, 0.0)
            }
            if (pull > 0.0) for (e in collectTargets(world, center, radius * 1.4, player, scope)) {
                val d = center.subtract(e.position())
                if (d.length() > 0.5) {
                    val v = d.normalize().scale(pull)
                    e.push(v.x, v.y * 0.1, v.z)
                    e.hurtMarked = true
                }
            }
        }
        // Collapse.
        ZoneEffects.schedule(delay) {
            world.sendParticles(ParticleTypes.EXPLOSION_EMITTER, center.x, center.y + 0.5, center.z, 1, 0.0, 0.0, 0.0, 0.0)
            world.sendParticles(ParticleTypes.SONIC_BOOM, center.x, center.y + 0.5, center.z, 1, 0.0, 0.0, 0.0, 0.0)
            world.playSound(null, center.x, center.y, center.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 1.0f, 0.8f)
            if (damage > 0f) {
                val src = world.damageSources().indirectMagic(player, player)
                for (e in collectTargets(world, center, radius, player, scope)) e.hurt(src, damage)
            }
        }
        world.playSound(null, center.x, center.y, center.z, SoundEvents.PORTAL_TRIGGER, SoundSource.PLAYERS, 0.7f, 0.6f)
        return true
    }
}

/**
 * `phase_shade` — right-click blinks you forward and leaves a shade at the spot you left; right-click
 * again while the shade lasts (`windowTicks`) snaps you back to it. A commit-then-decide escape: dive
 * in, then bail to safety if you timed it — or lose the shade and be stuck where you blinked.
 */
object PhaseShadeAbility : Ability {
    override fun execute(ctx: AbilityContext): Boolean {
        val p = ctx.params
        val player = ctx.player
        val world = ctx.world
        val particle = p.optParticle("particle", ParticleTypes.SOUL)
        val now = world.gameTime

        val shade = AbilityState.shades[player.uuid]
        if (shade != null && now < shade.expiry && shade.dimension == world.dimension()) {
            // Recall to the shade.
            AbilityState.shades.remove(player.uuid)
            burst(world, player.position().add(0.0, 1.0, 0.0), particle)
            player.teleportTo(shade.x, shade.y, shade.z)
            burst(world, Vec3(shade.x, shade.y + 1.0, shade.z), particle)
            val safety = p.optI("recallResistance", 30)
            if (safety > 0) player.addEffect(MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, safety, 4, false, false))
            world.playSound(null, shade.x, shade.y, shade.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8f, 1.4f)
            return true
        }

        // Blink forward and drop a shade at the origin.
        val origin = player.position()
        val dest = safeBlink(world, player, p.optD("distance", 6.0))
        val window = p.optI("windowTicks", 100).coerceIn(20, 600)
        AbilityState.shades[player.uuid] = AbilityState.ShadeEntry(origin.x, origin.y, origin.z, world.dimension(), now + window)
        burst(world, origin.add(0.0, 1.0, 0.0), particle)
        player.teleportTo(dest.x, dest.y, dest.z)
        burst(world, dest.add(0.0, 1.0, 0.0), particle)
        world.playSound(null, dest.x, dest.y, dest.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8f, 0.9f)
        // A lingering wisp marks the recall point so the player can see where they'd snap back to.
        val uuid = player.uuid
        ZoneEffects.repeating(window, 5) {
            val s = AbilityState.shades[uuid] ?: return@repeating
            world.sendParticles(particle, s.x, s.y + 1.0, s.z, 3, 0.15, 0.5, 0.15, 0.0)
        }
        return true
    }

    private fun burst(world: ServerLevel, at: Vec3, particle: ParticleOptions) {
        for (i in 0 until 20) {
            world.sendParticles(particle, at.x + (Math.random() - 0.5), at.y + Math.random() * 1.2, at.z + (Math.random() - 0.5), 1, 0.0, 0.0, 0.0, 0.0)
        }
    }

    /** Walk the look direction to the furthest standing spot with head+feet clearance, like `blink`. */
    private fun safeBlink(world: ServerLevel, player: ServerPlayer, maxDistance: Double): Vec3 {
        val look = player.lookAngle.normalize()
        var travelled = 0.0
        var safe = player.position()
        while (travelled < maxDistance) {
            travelled += 0.5
            val candidate = player.position().add(look.x * travelled, 0.0, look.z * travelled)
            val pos = BlockPos.containing(candidate)
            val feet = world.getBlockState(pos).getCollisionShape(world, pos).isEmpty
            val head = world.getBlockState(pos.above()).getCollisionShape(world, pos.above()).isEmpty
            if (!feet || !head) break
            val below = pos.below()
            if (!world.getBlockState(below).getCollisionShape(world, below).isEmpty) {
                safe = Vec3(pos.x + 0.5, pos.y.toDouble(), pos.z + 0.5)
            }
        }
        return safe
    }
}

/**
 * `gravity_singularity` — a hold-to-charge pull (best on a `chargeable` item via `on_charge_release`).
 * The longer you hold, the wider and stronger the well and the bigger its final implosion — but the
 * charge animation commits you, and an optional `selfDamage` scales with the charge too. Skill is
 * charging to the right size under pressure, not mashing it.
 */
object GravitySingularityAbility : Ability {
    override fun execute(ctx: AbilityContext): Boolean {
        val p = ctx.params
        val player = ctx.player
        val world = ctx.world
        val maxCharge = p.optI("maxChargeTicks", 40).coerceAtLeast(1)
        val t = (ctx.damageAmount.toDouble() / maxCharge).coerceIn(0.0, 1.0)  // charge fraction (0..1)

        val radius = p.optD("minRadius", 3.0) + (p.optD("maxRadius", 8.0) - p.optD("minRadius", 3.0)) * t
        val pull = p.optD("pullStrength", 0.55) * (0.4 + 0.6 * t)
        val duration = p.optI("duration", 40).coerceAtLeast(4)
        val period = p.optI("period", 4).coerceAtLeast(1)
        val damage = p.optF("damage", 8f) * t.toFloat()
        val selfDamage = p.optF("selfDamage", 0f) * t.toFloat()
        val scope = p.scope("affects", AffectScope.MOBS)
        val range = p.optD("range", 4.0)
        val ring = p.optParticle("particle", ParticleTypes.PORTAL)

        val look = player.lookAngle
        val center = Vec3(player.x + look.x * range, player.y + look.y * range, player.z + look.z * range)

        ZoneEffects.repeating(duration, period) {
            if (!player.isAlive) return@repeating
            for (e in collectTargets(world, center, radius, player, scope)) {
                val d = center.subtract(e.position())
                if (d.length() > 0.4) {
                    val v = d.normalize().scale(pull)
                    e.push(v.x, v.y * 0.2, v.z)
                    e.hurtMarked = true
                }
            }
            for (i in 0 until 24) {
                val a = i / 24.0 * Math.PI * 2
                world.sendParticles(ring, center.x + cos(a) * radius, center.y + 0.3, center.z + sin(a) * radius, 1, 0.0, 0.0, 0.0, 0.0)
            }
        }
        ZoneEffects.schedule(duration) {
            world.sendParticles(ParticleTypes.EXPLOSION, center.x, center.y + 0.4, center.z, 1, 0.0, 0.0, 0.0, 0.0)
            if (damage > 0f) {
                val src = world.damageSources().indirectMagic(player, player)
                for (e in collectTargets(world, center, radius * 0.6, player, scope)) e.hurt(src, damage)
            }
        }
        if (selfDamage > 0f) {
            player.invulnerableTime = 0
            player.hurt(world.damageSources().magic(), selfDamage)
        }
        world.playSound(null, center.x, center.y, center.z, SoundEvents.EVOKER_CAST_SPELL, SoundSource.PLAYERS, 0.9f, (0.6 + 0.4 * t).toFloat())
        return true
    }
}