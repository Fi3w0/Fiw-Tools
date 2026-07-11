package com.fiw.tools.ability

import com.google.gson.JsonObject
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.monster.Enemy
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

/**
 * Who an ability's effect is allowed to touch. This is the single biggest balance dial — the same
 * effect tuned `hostiles` is a PvE clear tool, tuned `players` is a PvP tool. Configured per-ability
 * via an `affects` param.
 */
enum class AffectScope {
    /** Other players only (never the caster). */
    PLAYERS,
    /** Any non-player living entity. */
    MOBS,
    /** Hostile mobs only. */
    HOSTILES,
    /** Other players, treated as friendly (support buffs). */
    ALLIES,
    /** Anything alive except the caster. */
    ALL;

    fun matches(caster: ServerPlayer, entity: LivingEntity): Boolean = when (this) {
        PLAYERS -> entity is Player && entity !== caster
        MOBS -> entity !is Player
        HOSTILES -> entity is Enemy
        ALLIES -> entity is Player && entity !== caster
        ALL -> entity !== caster
    }

    companion object {
        fun parse(raw: String): AffectScope? = when (raw.lowercase()) {
            "players", "player" -> PLAYERS
            "mobs", "mob" -> MOBS
            "hostiles", "hostile", "enemies" -> HOSTILES
            "allies", "ally", "friendly" -> ALLIES
            "all", "any" -> ALL
            else -> null
        }
    }
}

/** Reads an `affects` scope param, falling back to [default] when missing or unrecognised. */
internal fun JsonObject?.scope(key: String, default: AffectScope): AffectScope {
    val raw = this?.get(key)?.takeIf { !it.isJsonNull }?.asString ?: return default
    return AffectScope.parse(raw) ?: default
}

/** The nearest living entity the player's crosshair ray touches within [range], or null. */
internal fun pickLookedAtLiving(world: ServerLevel, player: ServerPlayer, range: Double): LivingEntity? {
    val eye = player.eyePosition
    val dir = player.lookAngle.normalize()
    val end = eye.add(dir.scale(range))
    val box = player.boundingBox.expandTowards(dir.scale(range)).inflate(1.0)
    var best: LivingEntity? = null
    var bestDist = Double.MAX_VALUE
    for (e in world.getEntitiesOfClass(LivingEntity::class.java, box) { it !== player && it.isAlive }) {
        val clip = e.boundingBox.inflate(0.3).clip(eye, end)
        if (clip.isPresent) {
            val d = clip.get().distanceTo(eye)
            if (d < bestDist) { bestDist = d; best = e }
        }
    }
    return best
}

/** Living entities within [radius] of [center] that the [scope] permits, caster excluded by default. */
internal fun collectTargets(
    world: ServerLevel,
    center: Vec3,
    radius: Double,
    caster: ServerPlayer,
    scope: AffectScope,
    includeCaster: Boolean = false
): List<LivingEntity> {
    val box = AABB.ofSize(center, radius * 2, radius * 2, radius * 2)
    return world.getEntitiesOfClass(LivingEntity::class.java, box) { e ->
        e.isAlive &&
            (includeCaster || e !== caster) &&
            e.position().distanceTo(center) <= radius &&
            scope.matches(caster, e)
    }
}
