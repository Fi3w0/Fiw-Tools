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

    /** caster -> banked blood charges that backlash if not spent before [BloodDebtEntry.expiry], see `blood_debt`. */
    class BloodDebtEntry(var charges: Int, val expiry: Long, val backlashPerCharge: Float)
    val bloodDebt: MutableMap<UUID, BloodDebtEntry> = ConcurrentHashMap()

    /** caster -> the shade they can recall to (position + expiry), see `phase_shade`. */
    class ShadeEntry(val x: Double, val y: Double, val z: Double, val dimension: ResourceKey<Level>, val expiry: Long)
    val shades: MutableMap<UUID, ShadeEntry> = ConcurrentHashMap()

    /** Bank [charges] blood charges on [player]; a fresh debt stacks onto any unspent one. */
    fun addBloodDebt(player: UUID, charges: Int, expiry: Long, backlashPerCharge: Float) {
        bloodDebt.merge(player, BloodDebtEntry(charges, expiry, backlashPerCharge)) { old, new ->
            BloodDebtEntry(old.charges + new.charges, maxOf(old.expiry, new.expiry), maxOf(old.backlashPerCharge, new.backlashPerCharge))
        }
    }

    /** Spend one blood charge; returns false when the pool is empty. */
    fun spendBloodDebt(player: UUID): Boolean {
        val e = bloodDebt[player] ?: return false
        if (e.charges <= 0) return false
        e.charges--
        if (e.charges <= 0) bloodDebt.remove(player)
        return true
    }

    fun bloodDebtCharges(player: UUID): Int = bloodDebt[player]?.charges ?: 0

    /** The debt collector: once a debt's window closes, every unspent charge backlashes on the owner. */
    fun processBloodDebt(server: MinecraftServer) {
        if (bloodDebt.isEmpty()) return
        val now = server.overworld().gameTime
        val toRemove = mutableListOf<UUID>()
        for ((uuid, e) in bloodDebt) {
            if (now < e.expiry) continue
            val player = server.playerList.getPlayer(uuid)
            if (player != null && e.charges > 0 && player.isAlive) {
                player.invulnerableTime = 0
                player.hurt(player.level().damageSources().magic(), e.backlashPerCharge * e.charges)
                player.displayClientMessage(
                    com.fiw.tools.util.TextParser.parse("&4&lThe blood debt collects!"), true)
            }
            toRemove.add(uuid)
        }
        for (uuid in toRemove) bloodDebt.remove(uuid)
    }

    // ===== Riposte (see `riposte`) =====

    class ParryArm(val expiry: Long, val reflectFrac: Float, val riposteWindow: Int)
    val parryArms: MutableMap<UUID, ParryArm> = ConcurrentHashMap()
    val riposteReady: MutableMap<UUID, Long> = ConcurrentHashMap()

    fun armParry(player: UUID, expiry: Long, reflectFrac: Float, riposteWindow: Int) {
        parryArms[player] = ParryArm(expiry, reflectFrac, riposteWindow)
    }

    fun tryRiposte(player: ServerPlayer, source: net.minecraft.world.damagesource.DamageSource, amount: Float): Boolean {
        val arm = parryArms[player.uuid] ?: return false
        val level = player.serverLevel()
        val now = level.gameTime
        if (now > arm.expiry) { parryArms.remove(player.uuid); return false }
        if (source.`is`(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) return false
        parryArms.remove(player.uuid)
        riposteReady[player.uuid] = now + arm.riposteWindow
        if (arm.reflectFrac > 0f) {
            (source.entity as? LivingEntity)?.hurt(level.damageSources().thorns(player), amount * arm.reflectFrac)
        }
        level.playSound(null, player.x, player.y, player.z, net.minecraft.sounds.SoundEvents.SHIELD_BLOCK, net.minecraft.sounds.SoundSource.PLAYERS, 1.0f, 1.4f)
        level.sendParticles(ParticleTypes.CRIT, player.x, player.y + 1.0, player.z, 24, 0.4, 0.5, 0.4, 0.25)
        player.displayClientMessage(com.fiw.tools.util.TextParser.parse("&e&l⚔ PARRY!"), true)
        return true
    }

    fun consumeRiposte(player: UUID, now: Long): Boolean {
        val expiry = riposteReady[player] ?: return false
        riposteReady.remove(player)
        return now <= expiry
    }

    // ===== Sanguine tether (see `sanguine_tether`) =====

    class SanguineMark(val targetId: UUID, val expiry: Long)
    val sanguineMarks: MutableMap<UUID, SanguineMark> = ConcurrentHashMap()
    class SanguineBind(val targetId: UUID, val expiry: Long, val splitFrac: Float, val backlash: Float)
    val sanguineBinds: MutableMap<UUID, SanguineBind> = ConcurrentHashMap()

    fun markSanguine(caster: UUID, target: UUID, expiry: Long) {
        sanguineMarks[caster] = SanguineMark(target, expiry)
    }

    fun tryBindSanguine(caster: ServerPlayer, splitFrac: Float, backlash: Float, durationTicks: Int): Boolean {
        val now = caster.serverLevel().gameTime
        val mark = sanguineMarks[caster.uuid] ?: return false
        if (now > mark.expiry) { sanguineMarks.remove(caster.uuid); return false }
        sanguineMarks.remove(caster.uuid)
        sanguineBinds[caster.uuid] = SanguineBind(mark.targetId, now + durationTicks, splitFrac, backlash)
        return true
    }

    /** Guards against a mutual tether (A→B and B→A) recursing forever through the damage hook. */
    private val redirecting = ThreadLocal.withInitial { false }

    fun processSanguine(player: ServerPlayer, source: net.minecraft.world.damagesource.DamageSource, amount: Float) {
        if (redirecting.get()) return
        val bind = sanguineBinds[player.uuid] ?: return
        val level = player.serverLevel()
        val now = level.gameTime
        if (now > bind.expiry) { sanguineBinds.remove(player.uuid); return }
        if (amount <= 0f) return
        if (source.`is`(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY) ||
            source.`is`(com.fiw.tools.curse.CurseHandler.CURSE_DAMAGE_TYPE)) return
        val target = level.getEntity(bind.targetId) as? LivingEntity
        if (target == null || !target.isAlive) { sanguineBinds.remove(player.uuid); return }
        val redirect = amount * bind.splitFrac
        redirecting.set(true)
        try {
            target.hurt(level.damageSources().magic(), redirect)
        } finally {
            redirecting.set(false)
        }
        player.heal(redirect)
        level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, target.x, target.y + target.bbHeight * 0.5, target.z, 6, 0.2, 0.3, 0.2, 0.05)
        if (!target.isAlive) {
            sanguineBinds.remove(player.uuid)
            if (bind.backlash > 0f) {
                player.invulnerableTime = 0
                player.hurt(level.damageSources().magic(), bind.backlash)
            }
        }
    }

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

    /** Game-time of the player's last damage this session; `Long.MIN_VALUE` if never. Used by channels. */
    fun lastDamageTickOf(player: UUID): Long = lastDamageTick[player] ?: Long.MIN_VALUE

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
        bloodDebt.remove(id)
        shades.remove(id)
        parryArms.remove(id)
        riposteReady.remove(id)
        sanguineMarks.remove(id)
        sanguineBinds.remove(id)
    }

    fun clear() {
        recallAnchors.clear()
        tetherMarks.clear()
        lastDamageTick.clear()
        bleeds.clear()
        stuns.clear()
        aegisShields.clear()
        bloodDebt.clear()
        shades.clear()
        parryArms.clear()
        riposteReady.clear()
        sanguineMarks.clear()
        sanguineBinds.clear()
    }

    /** Records a fresh tether mark and returns true if [victim] was already marked and unexpired. */
    fun markTether(caster: UUID, victim: UUID, now: Long, durationTicks: Int): Boolean {
        val marks = tetherMarks.computeIfAbsent(caster) { ConcurrentHashMap() }
        val existing = marks[victim]
        marks[victim] = now + durationTicks
        return existing != null && existing >= now
    }
}
