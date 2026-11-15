package com.fiw.tools.ability.impl

import com.fiw.tools.ability.Ability
import com.fiw.tools.ability.AbilityContext
import com.fiw.tools.ability.AffectScope
import com.fiw.tools.ability.CommandAudit
import com.fiw.tools.ability.collectTargets
import com.fiw.tools.ability.effectByName
import com.fiw.tools.ability.optB
import com.fiw.tools.ability.optD
import com.fiw.tools.ability.optF
import com.fiw.tools.ability.optI
import com.fiw.tools.ability.optParticle
import com.fiw.tools.ability.scope
import com.fiw.tools.build.FiwItems
import com.fiw.tools.util.HolderAccess
import com.google.gson.JsonObject
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.Difficulty
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.monster.Enemy
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.slf4j.LoggerFactory
import java.util.UUID

/**
 * Decoy (`decoy`) — leaves a stand-in where the caster stands. The decoy is a building block: it
 * does nothing by itself except soak hits, and every moment of its short life can run a list of
 * actions, so one ability covers escape tools, traps, turrets and bait.
 *
 * Hooks (each an array of actions): `onSpawn`, `onHit`, `onDeath`, `onExpire`, and
 * `every: { "ticks": n, "actions": [...] }`.
 *
 * Actions (`"do"`): `explode`, `pull`, `push`, `damage`, `effect`, `command`, `particles`,
 * `sound`. Any action takes `delay` (ticks) and runs at the spot where the hook fired.
 *
 * The decoy never takes vanilla damage: hits drain its own `health` pool and are cancelled, so it
 * drops no loot or experience and the copies of the caster's gear it wears can never be obtained.
 * Decoys are tagged, so strays from a crash or restart are removed on load. A hostile body
 * (the default husk) cannot exist on Peaceful difficulty; the ability then refuses and says so.
 */
object DecoyAbility : Ability {
    private const val TAG = "fiw_decoy"
    private const val SECOND = 20
    private val logger = LoggerFactory.getLogger("fiw-tools/decoy")
    private val GEAR = listOf(
        EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET,
        EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND
    )
    private val UNSAFE = Regex("[^A-Za-z0-9_-]")

    private class Decoy(
        val world: ServerLevel,
        val mob: Mob,
        val owner: UUID,
        val params: JsonObject,
        val itemId: String,
        var ticksLeft: Int,
        var health: Float
    ) {
        var age = 0
        var hit = false
        var dying = false
    }

    private class Pending(val world: ServerLevel, val pos: Vec3, val owner: UUID, val itemId: String,
                          val action: JsonObject, var ticksLeft: Int)

    private val decoys: MutableList<Decoy> = ArrayList()
    private val pending: MutableList<Pending> = ArrayList()

    override fun execute(ctx: AbilityContext): Boolean {
        val p = ctx.params
        val player = ctx.player
        val world = ctx.world

        val typeId = ResourceLocation.tryParse(p.optS("entity", "minecraft:husk")) ?: return false
        val type = BuiltInRegistries.ENTITY_TYPE.getOptional(typeId).orElse(null) ?: return false
        val mob = type.create(world) as? Mob ?: return false

        // The game deletes hostile mobs every tick on Peaceful, so such a decoy would vanish at once.
        if (world.difficulty == Difficulty.PEACEFUL && mob is Enemy) {
            val notice = p.optS("peacefulMessage", "This decoy cannot exist on Peaceful difficulty.")
            if (notice.isNotEmpty()) player.sendSystemMessage(Component.literal(notice))
            return false
        }

        // Oldest first: a new decoy replaces the caster's earliest one once the cap is reached.
        val mine = decoys.filter { it.owner == player.uuid && !it.dying }
        val cap = p.optI("maxActive", 1).coerceIn(1, 4)
        for (old in mine.take((mine.size - cap + 1).coerceAtLeast(0))) remove(old, poof = true)

        mob.moveTo(player.x, player.y, player.z, player.yRot, player.xRot)
        mob.yHeadRot = player.yHeadRot
        mob.addTag(TAG)
        mob.setPersistenceRequired()
        mob.isSilent = p.optB("silent", true)
        if (p.optB("glowing", false)) mob.setGlowingTag(true)
        val attacks = p.optB("attacks", false)
        if (!attacks) mob.isNoAi = true
        val name = p.optS("name", "{player}").replace("{player}", player.gameProfile.name)
        if (name.isNotEmpty()) {
            mob.customName = Component.literal(name)
            mob.isCustomNameVisible = true
        }
        if (p.optB("copyEquipment", true)) {
            for (slot in GEAR) {
                mob.setItemSlot(slot, player.getItemBySlot(slot).copy())
                mob.setDropChance(slot, 0f)
            }
        }

        val decoy = Decoy(world, mob, player.uuid, p, FiwItems.fiwId(ctx.stack) ?: "",
            p.optI("duration", 200).coerceIn(SECOND, 30 * SECOND), p.optF("health", 20f).coerceAtLeast(1f))
        // Track before spawning: the entity-load hook fires inside addFreshEntity and removes
        // tagged mobs it does not know about.
        decoys.add(decoy)
        if (!world.addFreshEntity(mob)) {
            decoys.remove(decoy)
            return false
        }
        world.sendParticles(p.optParticle("particle", ParticleTypes.CLOUD), mob.x, mob.y + 1.0, mob.z, 16, 0.3, 0.5, 0.3, 0.02)
        fire(decoy, "onSpawn")
        return true
    }

    /**
     * Loader damage hook, before anything else looks at the hit. Returns true when vanilla damage
     * must be cancelled: the entity is a decoy (the hit drains its pool instead), or a decoy is
     * hitting its own owner. Effects run from [tick], never from inside the damage event.
     */
    fun absorb(entity: LivingEntity, source: DamageSource, amount: Float): Boolean {
        if (decoys.isEmpty()) return false
        // A fighting decoy keeps its mob AI, which may swing at its owner before [tick] re-aims it.
        val attacker = source.entity
        if (attacker != null && decoys.any { it.mob === attacker && it.owner == entity.uuid }) return true
        val decoy = decoys.firstOrNull { it.mob === entity } ?: return false
        if (decoy.dying) return true
        decoy.health -= amount
        if (decoy.health <= 0f) decoy.dying = true else decoy.hit = true
        return true
    }

    fun tick() {
        if (pending.isNotEmpty()) {
            val due = pending.filter { --it.ticksLeft <= 0 }
            pending.removeAll(due)
            for (p in due) run(p.world, p.pos, p.owner, p.itemId, p.action, null)
        }
        if (decoys.isEmpty()) return
        // Hooks may spawn or remove decoys, so walk a snapshot.
        for (decoy in decoys.toList()) {
            if (decoy !in decoys) continue
            val mob = decoy.mob
            if (mob.isRemoved) {
                decoys.remove(decoy)
                continue
            }
            if (decoy.dying) {
                fire(decoy, "onDeath")
                remove(decoy, poof = true)
                continue
            }
            decoy.age++
            if (--decoy.ticksLeft <= 0) {
                fire(decoy, "onExpire")
                remove(decoy, poof = true)
                continue
            }
            if (decoy.hit) {
                decoy.hit = false
                decoy.world.sendParticles(ParticleTypes.DAMAGE_INDICATOR, mob.x, mob.y + mob.bbHeight * 0.6, mob.z, 4, 0.2, 0.2, 0.2, 0.1)
                decoy.world.playSound(null, mob.x, mob.y, mob.z, SoundEvents.PLAYER_HURT, SoundSource.PLAYERS, 0.8f, 1.0f)
                fire(decoy, "onHit")
            }
            val every = decoy.params.get("every")?.takeIf { it.isJsonObject }?.asJsonObject
            if (every != null && decoy.age % every.optI("ticks", SECOND).coerceAtLeast(1) == 0) fire(decoy, "actions", every)
            if (decoy.age % SECOND == 0) taunt(decoy)
            if (!mob.isNoAi) aim(decoy)
        }
    }

    /** Server stop: forget everything; the entities are removed on next load via [onEntityLoad]. */
    fun clear() {
        decoys.clear()
        pending.clear()
    }

    /** Removes decoys saved through a crash or restart. Wire to the entity-load hook. */
    fun onEntityLoad(entity: Entity) {
        if (entity !is Mob || !entity.tags.contains(TAG)) return
        if (decoys.none { it.mob === entity }) entity.discard()
    }

    private fun remove(decoy: Decoy, poof: Boolean) {
        decoys.remove(decoy)
        val mob = decoy.mob
        if (mob.isRemoved) return
        if (poof) {
            decoy.world.sendParticles(ParticleTypes.POOF, mob.x, mob.y + mob.bbHeight / 2.0, mob.z, 16, 0.3, 0.4, 0.3, 0.02)
        }
        mob.discard()
    }

    /** Hostile mobs near the decoy turn on it instead of whoever they were chasing. */
    private fun taunt(decoy: Decoy) {
        val radius = decoy.params.optD("taunt", 12.0)
        if (radius <= 0.0) return
        val box = AABB.ofSize(decoy.mob.position(), radius * 2, radius, radius * 2)
        for (mob in decoy.world.getEntitiesOfClass(Mob::class.java, box)) {
            if (mob === decoy.mob || !mob.isAlive || mob.tags.contains(TAG)) continue
            val target = mob.target ?: continue
            if (target is Player) mob.target = decoy.mob
        }
    }

    /** A fighting decoy picks the nearest valid enemy and never its owner. */
    private fun aim(decoy: Decoy) {
        val mob = decoy.mob
        val owner = decoy.world.server.playerList.getPlayer(decoy.owner) ?: return
        val scope = decoy.params.scope("attackTargets", AffectScope.HOSTILES)
        val current = mob.target
        if (current != null && current.isAlive && current !== owner && scope.matches(owner, current)) return
        // Its own AI picked the owner or something off-limits: drop that now, search a few times a second.
        if (current != null) mob.target = null
        if (decoy.age % 5 != 0) return
        val range = decoy.params.optD("attackRange", 16.0)
        mob.target = collectTargets(decoy.world, mob.position(), range, owner, scope)
            .filter { it !== mob && !it.tags.contains(TAG) }
            .minByOrNull { it.distanceToSqr(mob) }
    }

    private fun fire(decoy: Decoy, hook: String, from: JsonObject = decoy.params) {
        val actions = from.get(hook)?.takeIf { it.isJsonArray }?.asJsonArray ?: return
        val pos = decoy.mob.position()
        for (element in actions) {
            val action = element.takeIf { it.isJsonObject }?.asJsonObject ?: continue
            val delay = action.optI("delay", 0)
            if (delay > 0) pending.add(Pending(decoy.world, pos, decoy.owner, decoy.itemId, action, delay.coerceAtMost(60 * SECOND)))
            else run(decoy.world, pos, decoy.owner, decoy.itemId, action, decoy.mob)
        }
    }

    private fun run(world: ServerLevel, pos: Vec3, ownerId: UUID, itemId: String, a: JsonObject, self: Mob?) {
        // Everything a decoy does is done in its owner's name; once they are gone it goes quiet.
        val owner = world.server.playerList.getPlayer(ownerId) ?: return
        if (owner.level() !== world) return
        val radius = a.optD("radius", 5.0)
        fun targets(default: AffectScope) = collectTargets(world, pos, radius, owner, a.scope("affects", default))
            .filter { it !== self && !it.tags.contains(TAG) }
        try {
            when (a.optS("do", "").lowercase()) {
                "explode" -> {
                    val damage = a.optF("damage", 6f)
                    val knockback = a.optD("knockback", 1.0)
                    val src = world.damageSources().playerAttack(owner)
                    for (e in targets(AffectScope.ALL)) {
                        e.hurt(src, damage)
                        val away = e.position().subtract(pos).let { if (it.lengthSqr() < 1.0E-4) Vec3(0.0, 1.0, 0.0) else it.normalize() }
                        e.push(away.x * knockback, 0.4, away.z * knockback)
                        e.hurtMarked = true
                    }
                    world.sendParticles(ParticleTypes.EXPLOSION_EMITTER, pos.x, pos.y + 1.0, pos.z, 1, 0.0, 0.0, 0.0, 0.0)
                    world.playSound(null, pos.x, pos.y, pos.z, HolderAccess.value(SoundEvents.GENERIC_EXPLODE), SoundSource.PLAYERS, 1.0f, 1.0f)
                }
                "pull", "push" -> {
                    val strength = a.optD("strength", 1.0) * if (a.optS("do", "") == "push") -1.0 else 1.0
                    for (e in targets(AffectScope.ALL)) {
                        val toward = pos.subtract(e.position())
                        if (toward.lengthSqr() < 1.0E-4) continue
                        val v = toward.normalize().scale(strength)
                        e.deltaMovement = Vec3(v.x, v.y * 0.5 + 0.2, v.z)
                        e.hurtMarked = true
                    }
                }
                "damage" -> {
                    val src = world.damageSources().playerAttack(owner)
                    for (e in targets(AffectScope.ALL)) e.hurt(src, a.optF("amount", 4f))
                }
                "effect" -> {
                    val effect = effectByName(a.optS("effect", "")) ?: return
                    val who = if (a.optS("to", "nearby").lowercase() == "owner") listOf<LivingEntity>(owner) else targets(AffectScope.ALL)
                    for (e in who) {
                        e.addEffect(MobEffectInstance(effect, a.optI("duration", 100), a.optI("amplifier", 0),
                            a.optB("ambient", false), a.optB("particles", true)))
                    }
                }
                "command" -> {
                    val commands = buildList {
                        a.get("command")?.takeIf { it.isJsonPrimitive }?.asString?.let { add(it) }
                        a.getAsJsonArray("commands")?.forEach { if (it.isJsonPrimitive) add(it.asString) }
                    }
                    val source = owner.createCommandSourceStack().withPosition(pos)
                        .withPermission(4).withSuppressedOutput()
                    val audit = AbilityContext(owner, ItemStack.EMPTY, world, params = a)
                    for (raw in commands) {
                        val cmd = raw.removePrefix("/")
                            .replace("{player}", clean(owner.gameProfile.name))
                            .replace("{uuid}", clean(owner.uuid.toString()))
                            .replace("{x}", clean(pos.x.toInt().toString()))
                            .replace("{y}", clean(pos.y.toInt().toString()))
                            .replace("{z}", clean(pos.z.toInt().toString()))
                        CommandAudit.during(itemId.ifEmpty { "decoy" }, -1) { CommandAudit.record(audit, cmd) }
                        world.server.commands.performPrefixedCommand(source, cmd)
                    }
                }
                "particles" -> world.sendParticles(a.optParticle("particle", ParticleTypes.CLOUD), pos.x, pos.y + 1.0, pos.z,
                    a.optI("count", 20).coerceIn(1, 200), a.optD("spread", 0.5), a.optD("spread", 0.5), a.optD("spread", 0.5), a.optD("speed", 0.02))
                "sound" -> soundById(a.optS("sound", ""))?.let {
                    world.playSound(null, pos.x, pos.y, pos.z, it, SoundSource.PLAYERS, a.optF("volume", 1.0f), a.optF("pitch", 1.0f))
                }
                else -> logger.warn("Unknown decoy action '{}' on item '{}'", a.optS("do", ""), itemId)
            }
        } catch (e: Exception) {
            logger.warn("Decoy action '{}' failed on item '{}': {}", a.optS("do", ""), itemId, e.message)
        }
    }

    private fun clean(raw: String): String = UNSAFE.replace(raw, "")

    private fun soundById(id: String): SoundEvent? {
        if (id.isEmpty()) return null
        val sid = ResourceLocation.tryParse(id) ?: return null
        return BuiltInRegistries.SOUND_EVENT.get(sid)
    }

    private fun JsonObject?.optS(key: String, default: String): String =
        this?.get(key)?.takeIf { it.isJsonPrimitive }?.asString ?: default
}
