package com.fiw.tools.ability

import com.fiw.tools.util.HolderAccess
import com.google.gson.JsonObject
import net.minecraft.core.particles.ParticleOptions
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.Vec3

enum class AbilityTrigger {
    ON_RIGHT_CLICK, ON_ATTACK, ON_KILL, ON_HURT, ON_BLOCK_BREAK, WHILE_HELD, WHILE_WORN, WHILE_SNEAKING;

    companion object {
        fun parse(s: String): AbilityTrigger? = when (s.lowercase()) {
            "on_right_click", "right_click" -> ON_RIGHT_CLICK
            "on_attack", "attack" -> ON_ATTACK
            "on_kill", "kill" -> ON_KILL
            "on_hurt", "hurt" -> ON_HURT
            "on_block_break", "block_break" -> ON_BLOCK_BREAK
            "while_held", "passive", "held" -> WHILE_HELD
            "while_worn", "worn", "armor" -> WHILE_WORN
            "while_sneaking", "sneaking", "sneak" -> WHILE_SNEAKING
            else -> null
        }
    }
}

data class AbilityContext(
    val player: ServerPlayer,
    val stack: ItemStack,
    val world: ServerLevel,
    val target: LivingEntity? = null,
    val targetPos: Vec3? = null,
    val params: JsonObject,
    /** For on_hurt: the damage the player is taking. Null for other triggers. */
    val damageSource: DamageSource? = null,
    /** For on_hurt: the incoming damage amount. 0 for other triggers. */
    val damageAmount: Float = 0f
)

interface Ability {
    /** Run the effect. Return true if it actually acted (arms the cooldown), false to skip the cooldown. */
    fun execute(ctx: AbilityContext): Boolean
}

object ParticleSpec {
    fun parse(json: Any?, default: ParticleOptions = ParticleTypes.CRIT): ParticleOptions {
        val id: String = when (json) {
            is String -> json
            is JsonObject -> json.get("id")?.asString ?: return default
            null -> return default
            else -> return default
        }
        val parsed = Identifier.tryParse(id) ?: return default
        val type = BuiltInRegistries.PARTICLE_TYPE.get(parsed).orElse(null) ?: return default
        return HolderAccess.value(type) as? ParticleOptions ?: default
    }
}

internal fun JsonObject?.optD(key: String, default: Double): Double =
    this?.get(key)?.takeIf { !it.isJsonNull }?.asDouble ?: default

internal fun JsonObject?.optF(key: String, default: Float): Float =
    this?.get(key)?.takeIf { !it.isJsonNull }?.asFloat ?: default

internal fun JsonObject?.optI(key: String, default: Int): Int =
    this?.get(key)?.takeIf { !it.isJsonNull }?.asInt ?: default

internal fun JsonObject?.optB(key: String, default: Boolean): Boolean =
    this?.get(key)?.takeIf { !it.isJsonNull }?.asBoolean ?: default

internal fun JsonObject?.optParticle(key: String, default: ParticleOptions): ParticleOptions {
    val el = this?.get(key) ?: return default
    return when {
        el.isJsonObject -> ParticleSpec.parse(el.asJsonObject, default)
        el.isJsonPrimitive -> ParticleSpec.parse(el.asString, default)
        else -> default
    }
}
