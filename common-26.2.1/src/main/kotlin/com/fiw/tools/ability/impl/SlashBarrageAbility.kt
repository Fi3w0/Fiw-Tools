package com.fiw.tools.ability.impl

import com.fiw.tools.ability.Ability
import com.fiw.tools.ability.AbilityContext
import com.fiw.tools.ability.AffectScope
import com.fiw.tools.ability.ParticleSpec
import com.fiw.tools.ability.optB
import com.fiw.tools.ability.optD
import com.fiw.tools.ability.optF
import com.fiw.tools.ability.optI
import com.fiw.tools.ability.scope
import com.fiw.tools.util.HolderAccess
import com.fiw.tools.util.TextParser
import com.google.gson.JsonObject
import net.minecraft.core.particles.ParticleOptions
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.core.particles.PowerParticleOption
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import java.util.UUID
import kotlin.math.cos
import kotlin.math.sin

/**
 * Arc-slash barrage engine — a cinematic combo: an opening line + charge-up sound, then a
 * volley of rolled arc slashes with particle bursts between
 * them, optionally capped by a teleport behind the victim and one heavy finisher hit.
 *
 * Two front-ends share the engine:
 *  - [ArcSlashBarrageAbility] (`arc_slash_barrage`) — single target (hit target or the entity
 *    under the crosshair), defaults mirroring the original: 10 slashes rolled
 *    0/45/90/-45/30/-30/15/-15/60/-60, then "Behind you." teleport + 25 armor-ignoring damage.
 *  - [ArcSlashStormAbility] (`arc_slash_storm`) — every matching entity in `radius` (players
 *    and mobs alike) eats each slash; 15 rolls fanning 0..±126, no finisher.
 *
 * Everything is a param: rolls, delays, damage, arc shape, messages, sounds, every particle.
 */
internal object SlashBarrage {
    data class Burst(val particle: ParticleOptions, val amount: Int, val hSpread: Double, val vSpread: Double, val speed: Double)

    /** How `slashParticles` bursts map onto slashes: every burst each slash, or one per slash in rotation. */
    enum class BurstMode { ALL, CYCLE }

    class Defaults(
        val rolls: List<Double>,
        val startDelay: Int,
        val interval: Int,
        val startMessage: String,
        val startSound: String,
        val startVolume: Float,
        val startPitch: Float,
        val bursts: List<Burst>,
        val burstMode: BurstMode,
        val affects: AffectScope,
        val teleportBehind: Boolean,
        val finalDamage: Float
    )

    private class Slash(
        val world: ServerLevel,
        val owner: ServerPlayer,
        val origin: Vec3,
        val forward: Vec3,
        val right: Vec3,
        val arc: Double,
        val radius: Double,
        val damage: Float,
        val knockback: Double,
        val duration: Int,
        val points: Int,
        val yOffset: Double,
        val height: Double,
        val rollRad: Double,
        val hitRadius: Double,
        val arcParticle: ParticleOptions,
        val affects: AffectScope,
        val armorPierce: Double,
        var tick: Int = 0,
        val alreadyHit: MutableSet<UUID> = HashSet()
    )

    private class Sequence(
        val world: ServerLevel,
        val owner: ServerPlayer,
        /** Locked victim for targeted mode; null in radius mode. */
        val target: LivingEntity?,
        /** Radius mode re-collects victims each slash; otherwise a null target slashes in front. */
        val radiusMode: Boolean,
        val frontDistance: Double,
        val radius: Double,
        val affects: AffectScope,
        val rolls: List<Double>,
        val interval: Int,
        // Slash shape
        val width: Double,
        val arc: Double,
        val height: Double,
        val yOffset: Double,
        val damage: Float,
        val knockback: Double,
        val points: Int,
        val hitRadius: Double,
        val slashDuration: Int,
        val arcParticle: ParticleOptions,
        val slashSound: SoundEvent?,
        val slashVolume: Float,
        val slashPitch: Float,
        val bursts: List<Burst>,
        val burstMode: BurstMode,
        val burstAtSelf: Boolean,
        // Finisher
        val teleportBehind: Boolean,
        val behindDistance: Double,
        val finalDelay: Int,
        val finalDamage: Float,
        val armorPierce: Double,
        val slashArmorPierce: Double,
        val finalMessage: String,
        val finalMessageRadius: Double,
        val teleportSound: SoundEvent?,
        val teleportVolume: Float,
        val teleportPitch: Float,
        val teleportBurst: Burst?,
        var wait: Int,
        var slashIndex: Int = 0,
        /** 0 = slashing (after the opening wait), 1 = teleport+announce, 2 = delayed final hit. */
        var phase: Int = 0
    )

    private val sequences: MutableList<Sequence> = ArrayList()
    private val slashes: MutableList<Slash> = ArrayList()

    fun tick() {
        synchronized(this) {
            val si = sequences.iterator()
            while (si.hasNext()) {
                val s = si.next()
                if (!stepSequence(s)) si.remove()
            }
            val fi = slashes.iterator()
            while (fi.hasNext()) {
                val f = fi.next()
                f.tick++
                stepSlash(f)
                if (f.tick >= f.duration) fi.remove()
            }
        }
    }

    /** Drop everything in flight (server stop) so nothing ticks into a freshly loaded world. */
    fun clear() {
        synchronized(this) {
            sequences.clear()
            slashes.clear()
        }
    }

    /**
     * Reads params over [d] and starts a sequence. Always fires: with no victim (or nobody in
     * radius) the slashes carve the air in front of the caster instead, still hitting whatever
     * wanders into them — only the teleport finisher needs a locked target.
     */
    fun begin(ctx: AbilityContext, d: Defaults, radiusMode: Boolean): Boolean {
        val p = ctx.params
        val player = ctx.player
        val world = ctx.world

        val radius = p.optD("radius", 40.0)
        val affects = p.scope("affects", d.affects)
        val target: LivingEntity? = if (radiusMode) null
        else ctx.target ?: findLookTarget(player, world, p.optD("targetRange", 16.0))

        val startMessage = p.optS("startMessage", d.startMessage)
        val messageRadius = p.optD("startMessageRadius", 40.0)
        if (startMessage.isNotEmpty()) speak(world, player.position(), messageRadius, startMessage)
        val startSound = soundById(p.optS("startSound", d.startSound))
        if (startSound != null) {
            world.playSound(null, player.x, player.y, player.z, startSound, SoundSource.PLAYERS,
                p.optF("startSoundVolume", d.startVolume), p.optF("startSoundPitch", d.startPitch))
        }

        val seq = Sequence(
            world = world, owner = player, target = target, radiusMode = radiusMode,
            frontDistance = p.optD("frontDistance", 3.0), radius = radius, affects = affects,
            rolls = parseRolls(p, d.rolls),
            interval = p.optI("slashInterval", d.interval).coerceAtLeast(1),
            width = p.optD("width", 6.0),
            arc = p.optD("arc", 120.0),
            height = p.optD("height", 1.0),
            yOffset = p.optD("yOffset", 1.5),
            damage = p.optF("damage", 4f),
            knockback = p.optD("knockback", 0.3),
            points = p.optI("points", 24).coerceIn(6, 64),
            hitRadius = p.optD("hitRadius", 1.0),
            slashDuration = p.optI("slashDuration", 2).coerceIn(1, 30),
            arcParticle = ParticleSpec.parse(p.get("arcParticle")?.takeIf { it.isJsonPrimitive }?.asString, ParticleTypes.SWEEP_ATTACK),
            slashSound = soundById(p.optS("slashSound", "minecraft:entity.player.attack.sweep")),
            slashVolume = p.optF("slashSoundVolume", 1.0f),
            slashPitch = p.optF("slashSoundPitch", 1.2f),
            bursts = parseBursts(p, "slashParticles", d.bursts),
            burstMode = if (p.optS("particleMode", if (d.burstMode == BurstMode.ALL) "all" else "cycle") == "cycle") BurstMode.CYCLE else BurstMode.ALL,
            burstAtSelf = p.optS("burstAt", "self") != "target",
            teleportBehind = p.optB("teleportBehind", d.teleportBehind),
            behindDistance = p.optD("behindDistance", 2.0),
            finalDelay = p.optI("finalDelay", 5).coerceAtLeast(0),
            finalDamage = p.optF("finalDamage", d.finalDamage),
            armorPierce = pierceFraction(p, "ignoreArmor", 1.0),
            slashArmorPierce = pierceFraction(p, "slashIgnoreArmor", 0.0),
            finalMessage = p.optS("finalMessage", "&c&lBehind you."),
            finalMessageRadius = p.optD("finalMessageRadius", 20.0),
            teleportSound = soundById(p.optS("teleportSound", "minecraft:entity.enderman.teleport")),
            teleportVolume = p.optF("teleportSoundVolume", 1.0f),
            teleportPitch = p.optF("teleportSoundPitch", 0.5f),
            teleportBurst = parseBursts(p, "teleportParticles",
                listOf(Burst(PowerParticleOption.create(ParticleTypes.DRAGON_BREATH, 1.0f), 50, 1.0, 2.0, 0.0))).firstOrNull(),
            wait = p.optI("startDelay", d.startDelay).coerceAtLeast(0)
        )
        synchronized(this) { sequences.add(seq) }
        return true
    }

    // ---------------------------------------------------------------- sequence stepping

    /** Advances [s] one tick; false removes it. */
    private fun stepSequence(s: Sequence): Boolean {
        val owner = s.owner
        if (owner.isRemoved || !owner.isAlive || owner.level() !== s.world) return false
        if (s.wait > 0) {
            s.wait--
            return true
        }
        return when (s.phase) {
            0 -> stepSlashing(s)
            1 -> {
                if (!teleportAndAnnounce(s)) return false
                s.phase = 2
                s.wait = s.finalDelay
                true
            }
            2 -> {
                finalHit(s)
                false
            }
            else -> false
        }
    }

    private fun stepSlashing(s: Sequence): Boolean {
        val victims: List<LivingEntity> = when {
            s.target != null -> {
                if (s.target.isRemoved || !s.target.isAlive || s.target.level() !== s.world) return false
                listOf(s.target)
            }
            s.radiusMode -> victimsInRadius(s.world, s.owner, s.radius, s.affects)
            else -> emptyList()
        }

        val roll = s.rolls[s.slashIndex]
        if (victims.isEmpty()) spawnFrontSlash(s, roll) else for (victim in victims) spawnSlash(s, victim, roll)
        if (s.slashSound != null) {
            val at = if (s.target != null) s.target.position() else s.owner.position()
            s.world.playSound(null, at.x, at.y, at.z, s.slashSound, SoundSource.PLAYERS, s.slashVolume, s.slashPitch)
        }
        fireBursts(s)

        s.slashIndex++
        s.wait = s.interval
        if (s.slashIndex >= s.rolls.size) {
            if (s.target != null && (s.teleportBehind || s.finalDamage > 0f)) {
                s.phase = 1
            } else {
                return false
            }
        }
        return true
    }

    private fun spawnSlash(s: Sequence, victim: LivingEntity, rollDeg: Double) {
        val delta = victim.position().subtract(s.owner.position())
        val flat = Vec3(delta.x, 0.0, delta.z)
        val rawFwd = if (flat.length() > 0.05) flat else Vec3(s.owner.lookAngle.x, 0.0, s.owner.lookAngle.z)
        if (rawFwd.length() < 0.01) return
        val forward = rawFwd.normalize()
        val right = Vec3(-forward.z, 0.0, forward.x)
        // Put the arc's crest on the victim: points sit `width` blocks out from the origin.
        val origin = victim.position().subtract(forward.scale(s.width))
        slashes.add(Slash(s.world, s.owner, origin, forward, right, s.arc, s.width, s.damage,
            s.knockback, s.slashDuration, s.points, s.yOffset, s.height,
            Math.toRadians(rollDeg), s.hitRadius, s.arcParticle, s.affects, s.slashArmorPierce))
    }

    /** No victim to aim at: carve the air [Sequence.frontDistance] blocks ahead of the caster's live look. */
    private fun spawnFrontSlash(s: Sequence, rollDeg: Double) {
        val look = s.owner.lookAngle
        val flat = Vec3(look.x, 0.0, look.z)
        if (flat.length() < 0.01) return
        val forward = flat.normalize()
        val right = Vec3(-forward.z, 0.0, forward.x)
        val origin = s.owner.position().add(forward.scale(s.frontDistance - s.width))
        slashes.add(Slash(s.world, s.owner, origin, forward, right, s.arc, s.width, s.damage,
            s.knockback, s.slashDuration, s.points, s.yOffset, s.height,
            Math.toRadians(rollDeg), s.hitRadius, s.arcParticle, s.affects, s.slashArmorPierce))
    }

    private fun fireBursts(s: Sequence) {
        val at = (if (s.burstAtSelf || s.target == null) s.owner.position() else s.target.position()).add(0.0, s.yOffset, 0.0)
        when (s.burstMode) {
            BurstMode.ALL -> for (b in s.bursts) emitBurst(s.world, at, b)
            BurstMode.CYCLE -> if (s.bursts.isNotEmpty()) emitBurst(s.world, at, s.bursts[s.slashIndex % s.bursts.size])
        }
    }

    /** Teleport behind the victim (if enabled) and announce. False when the victim is gone. */
    private fun teleportAndAnnounce(s: Sequence): Boolean {
        val target = s.target ?: return false
        if (target.isRemoved || !target.isAlive || target.level() !== s.world) return false

        if (s.teleportBehind) {
            val back = target.lookAngle
            val flatRaw = Vec3(back.x, 0.0, back.z)
            val flat = if (flatRaw.length() > 0.05) flatRaw.normalize()
            else target.position().subtract(s.owner.position()).let { Vec3(it.x, 0.0, it.z) }.normalize()
            var dest = target.position().subtract(flat.scale(s.behindDistance))
            if (!isOpen(s.world, dest)) dest = target.position()
            s.owner.teleportTo(dest.x, dest.y, dest.z)
            if (s.teleportSound != null) {
                s.world.playSound(null, dest.x, dest.y, dest.z, s.teleportSound, SoundSource.PLAYERS,
                    s.teleportVolume, s.teleportPitch)
            }
            s.teleportBurst?.let { emitBurst(s.world, s.owner.position().add(0.0, 1.0, 0.0), it) }
        }
        if (s.finalMessage.isNotEmpty()) speak(s.world, s.owner.position(), s.finalMessageRadius, s.finalMessage)
        return true
    }

    private fun finalHit(s: Sequence) {
        val target = s.target ?: return
        if (s.finalDamage <= 0f || target.isRemoved || !target.isAlive || target.level() !== s.world) return
        dealDamage(s.world, s.owner, target, s.finalDamage, s.armorPierce)
        s.world.sendParticles(ParticleTypes.CRIT, target.x, target.y + target.bbHeight / 2.0, target.z,
            20, 0.4, 0.5, 0.4, 0.25)
    }

    // ---------------------------------------------------------------- slash animation

    private fun stepSlash(f: Slash) {
        val prevT = (f.tick - 1).toDouble() / f.duration
        val currT = f.tick.toDouble() / f.duration
        val iStart = (prevT * f.points).toInt()
        val iEnd = minOf(f.points, (currT * f.points).toInt() + 1)

        for (pi in iStart..iEnd) {
            val t = pi.toDouble() / f.points
            val pos = arcPoint(f, -f.arc / 2.0 + t * f.arc, t)
            f.world.sendParticles(f.arcParticle, pos.x, pos.y, pos.z, 1, 0.0, 0.0, 0.0, 0.0)

            val box = AABB(
                pos.x - f.hitRadius, pos.y - f.hitRadius - 0.5, pos.z - f.hitRadius,
                pos.x + f.hitRadius, pos.y + f.hitRadius + 0.5, pos.z + f.hitRadius
            )
            val victims = f.world.getEntitiesOfClass(LivingEntity::class.java, box) {
                it !== f.owner && it.isAlive && !f.alreadyHit.contains(it.uuid) && f.affects.matches(f.owner, it)
            }
            for (victim in victims) {
                dealDamage(f.world, f.owner, victim, f.damage, f.armorPierce)
                f.alreadyHit.add(victim.uuid)
                if (f.knockback > 0.0) {
                    val knock = victim.position().subtract(f.owner.position()).normalize()
                    victim.push(knock.x * f.knockback, 0.25, knock.z * f.knockback)
                    victim.hurtMarked = true
                }
                f.world.sendParticles(ParticleTypes.CRIT, victim.x, victim.y + victim.bbHeight / 2.0, victim.z,
                    8, 0.3, 0.3, 0.3, 0.15)
            }
        }
    }

    /**
     * Point on the slash: a `points`-step fan of radius [Slash.radius] spanning [Slash.arc] degrees
     * in front of the origin, with the lateral/vertical components rotated `roll` degrees around
     * the forward axis — roll 0 sweeps flat, roll 90 carves vertically.
     */
    private fun arcPoint(f: Slash, thetaDeg: Double, t: Double): Vec3 {
        val theta = Math.toRadians(thetaDeg)
        val lateral = f.radius * sin(theta)
        val forwardDist = f.radius * cos(theta)
        val bulge = f.height * sin(Math.PI * t)
        val xr = lateral * cos(f.rollRad) - bulge * sin(f.rollRad)
        val yr = lateral * sin(f.rollRad) + bulge * cos(f.rollRad)
        return Vec3(
            f.origin.x + f.forward.x * forwardDist + f.right.x * xr,
            f.origin.y + f.yOffset + yr,
            f.origin.z + f.forward.z * forwardDist + f.right.z * xr
        )
    }

    // ---------------------------------------------------------------- helpers

    /** Deals [damage], routing [pierce] (0..1) of it through an armor-bypassing magic source. */
    private fun dealDamage(world: ServerLevel, owner: ServerPlayer, target: LivingEntity, damage: Float, pierce: Double) {
        if (damage <= 0f) return
        val normalSrc = world.damageSources().playerAttack(owner)
        val magicSrc = world.damageSources().indirectMagic(owner, owner)
        when {
            pierce >= 1.0 -> target.hurtServer(world, magicSrc, damage)
            pierce <= 0.0 -> target.hurtServer(world, normalSrc, damage)
            else -> {
                val pierced = (damage * pierce).toFloat()
                target.hurtServer(world, normalSrc, damage - pierced)
                // Second source lands inside the invulnerability window; reopen it.
                target.invulnerableTime = 0
                target.hurtServer(world, magicSrc, pierced)
            }
        }
    }

    /** Bool-or-number param: true = 1.0, false = 0.0, 0..1 = fraction, >1 = percent. */
    private fun pierceFraction(p: JsonObject?, key: String, default: Double): Double {
        val prim = p?.get(key)?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive ?: return default
        return when {
            prim.isBoolean -> if (prim.asBoolean) 1.0 else 0.0
            prim.isNumber -> prim.asDouble.let { if (it > 1.0) it / 100.0 else it }.coerceIn(0.0, 1.0)
            else -> default
        }
    }

    private fun victimsInRadius(world: ServerLevel, owner: ServerPlayer, radius: Double, affects: AffectScope): List<LivingEntity> {
        val box = AABB.ofSize(owner.position(), radius * 2, radius * 2, radius * 2)
        return world.getEntitiesOfClass(LivingEntity::class.java, box) {
            it !== owner && it.isAlive && it.position().distanceTo(owner.position()) <= radius && affects.matches(owner, it)
        }
    }

    private fun findLookTarget(player: ServerPlayer, world: ServerLevel, range: Double): LivingEntity? {
        val eye = player.eyePosition
        val end = eye.add(player.lookAngle.normalize().scale(range))
        val sweep = player.boundingBox.expandTowards(end.subtract(eye)).inflate(1.0)
        var best: LivingEntity? = null
        var bestDist = Double.MAX_VALUE
        for (e in world.getEntitiesOfClass(LivingEntity::class.java, sweep) { it !== player && it.isAlive }) {
            val hit = e.boundingBox.inflate(0.3).clip(eye, end).orElse(null) ?: continue
            val d = eye.distanceToSqr(hit)
            if (d < bestDist) {
                bestDist = d
                best = e
            }
        }
        return best
    }

    private fun speak(world: ServerLevel, at: Vec3, radius: Double, message: String) {
        val component = TextParser.parse(message)
        for (p in world.players()) {
            if (p.position().distanceTo(at) <= radius) p.sendSystemMessage(component)
        }
    }

    private fun soundById(id: String): SoundEvent? {
        if (id.isEmpty()) return null
        val sid = Identifier.tryParse(id) ?: return null
        val holder = BuiltInRegistries.SOUND_EVENT.get(sid).orElse(null) ?: return null
        return HolderAccess.value(holder)
    }

    private fun emitBurst(world: ServerLevel, at: Vec3, b: Burst) {
        world.sendParticles(b.particle, at.x, at.y, at.z, b.amount, b.hSpread, b.vSpread, b.hSpread, b.speed)
    }

    private fun isOpen(world: ServerLevel, pos: Vec3): Boolean {
        val feet = net.minecraft.core.BlockPos.containing(pos)
        return world.getBlockState(feet).getCollisionShape(world, feet).isEmpty &&
            world.getBlockState(feet.above()).getCollisionShape(world, feet.above()).isEmpty
    }

    private fun parseRolls(p: JsonObject, default: List<Double>): List<Double> =
        p.getAsJsonArray("rolls")?.map { it.asDouble }?.takeIf { it.isNotEmpty() } ?: default

    /** `[{particle, amount, hSpread, vSpread, speed}, ...]` or bare particle-id strings. */
    private fun parseBursts(p: JsonObject, key: String, default: List<Burst>): List<Burst> {
        val arr = p.getAsJsonArray(key) ?: return default
        val out = ArrayList<Burst>()
        for (el in arr) {
            if (el.isJsonPrimitive) {
                out.add(Burst(ParticleSpec.parse(el.asString), 5, 0.1, 0.1, 0.0))
            } else if (el.isJsonObject) {
                val o = el.asJsonObject
                out.add(Burst(
                    ParticleSpec.parse(o.get("particle")?.takeIf { it.isJsonPrimitive }?.asString),
                    o.optI("amount", 5), o.optD("hSpread", 0.1), o.optD("vSpread", 0.1), o.optD("speed", 0.0)
                ))
            }
        }
        return if (out.isEmpty()) default else out
    }

    private fun JsonObject?.optS(key: String, default: String): String =
        this?.get(key)?.takeIf { it.isJsonPrimitive }?.asString ?: default
}

/**
 * Targeted arc-slash barrage (`arc_slash_barrage`): opening line + charge sound, ten rolled
 * slashes carving the victim, then a teleport behind them and one heavy armor-ignoring hit.
 * Works from `on_attack` (uses the hit target) or right-click (entity under the crosshair,
 * `targetRange`); with no target it slashes the air in front instead (`frontDistance`), still
 * hitting whatever steps in, and skips the finisher. Suggested `cooldownTicks`: 400.
 */
object ArcSlashBarrageAbility : Ability {
    private val DEFAULTS = SlashBarrage.Defaults(
        rolls = listOf(0.0, 45.0, 90.0, -45.0, 30.0, -30.0, 15.0, -15.0, 60.0, -60.0),
        startDelay = 8,
        interval = 3,
        startMessage = "&d&lYou're in my way...",
        startSound = "minecraft:block.respawn_anchor.charge",
        startVolume = 2.0f,
        startPitch = 0.5f,
        bursts = listOf(
            SlashBarrage.Burst(ParticleTypes.WITCH, 10, 0.1, 0.1, 0.0),
            SlashBarrage.Burst(PowerParticleOption.create(ParticleTypes.DRAGON_BREATH, 1.0f), 5, 0.05, 0.05, 0.01),
            SlashBarrage.Burst(ParticleTypes.ELECTRIC_SPARK, 2, 0.1, 0.1, 0.0)
        ),
        burstMode = SlashBarrage.BurstMode.ALL,
        affects = AffectScope.ALL,
        teleportBehind = true,
        finalDamage = 25f
    )

    override fun execute(ctx: AbilityContext): Boolean = SlashBarrage.begin(ctx, DEFAULTS, radiusMode = false)
}

/**
 * Radius arc-slash storm (`arc_slash_storm`): opening line + charge sound, then fifteen rolled
 * slashes each hitting every matching entity (`affects`, default all — players and mobs) within
 * `radius` of the caster, or the air in front when nobody is around. No finisher by default.
 * Suggested `cooldownTicks`: 2400.
 */
object ArcSlashStormAbility : Ability {
    private val DEFAULTS = SlashBarrage.Defaults(
        rolls = listOf(0.0, 18.0, -18.0, 36.0, -36.0, 54.0, -54.0, 72.0, -72.0, 90.0, -90.0, 108.0, -108.0, 126.0, -126.0),
        startDelay = 20,
        interval = 2,
        startMessage = "&d&lIm tired of you",
        startSound = "minecraft:block.respawn_anchor.charge",
        startVolume = 1.8f,
        startPitch = 0.6f,
        bursts = listOf(
            SlashBarrage.Burst(ParticleTypes.WITCH, 8, 0.1, 0.1, 0.0),
            SlashBarrage.Burst(PowerParticleOption.create(ParticleTypes.DRAGON_BREATH, 1.0f), 5, 0.05, 0.05, 0.01),
            SlashBarrage.Burst(ParticleTypes.ELECTRIC_SPARK, 3, 0.08, 0.08, 0.0)
        ),
        burstMode = SlashBarrage.BurstMode.CYCLE,
        affects = AffectScope.ALL,
        teleportBehind = false,
        finalDamage = 0f
    )

    override fun execute(ctx: AbilityContext): Boolean = SlashBarrage.begin(ctx, DEFAULTS, radiusMode = true)
}
