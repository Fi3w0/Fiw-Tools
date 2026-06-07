package com.fiw.tools.ability

import com.google.gson.JsonObject
import net.minecraft.core.Holder
import net.minecraft.world.effect.MobEffect
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.LivingEntity

/** Maps a friendly effect name (as used in `buffs` params) to its vanilla holder, or null if unknown. */
internal fun effectByName(name: String): Holder<MobEffect>? = when (name.lowercase()) {
    "speed" -> MobEffects.SPEED
    "slowness" -> MobEffects.SLOWNESS
    "haste" -> MobEffects.HASTE
    "mining_fatigue" -> MobEffects.MINING_FATIGUE
    "strength" -> MobEffects.STRENGTH
    "jump_boost", "jump" -> MobEffects.JUMP_BOOST
    "nausea" -> MobEffects.NAUSEA
    "regeneration", "regen" -> MobEffects.REGENERATION
    "resistance" -> MobEffects.RESISTANCE
    "fire_resistance" -> MobEffects.FIRE_RESISTANCE
    "water_breathing" -> MobEffects.WATER_BREATHING
    "night_vision" -> MobEffects.NIGHT_VISION
    "blindness" -> MobEffects.BLINDNESS
    "darkness" -> MobEffects.DARKNESS
    "hunger" -> MobEffects.HUNGER
    "weakness" -> MobEffects.WEAKNESS
    "poison" -> MobEffects.POISON
    "wither" -> MobEffects.WITHER
    "health_boost" -> MobEffects.HEALTH_BOOST
    "absorption" -> MobEffects.ABSORPTION
    "saturation" -> MobEffects.SATURATION
    "glowing" -> MobEffects.GLOWING
    "levitation" -> MobEffects.LEVITATION
    "slow_falling" -> MobEffects.SLOW_FALLING
    "conduit_power" -> MobEffects.CONDUIT_POWER
    "dolphins_grace" -> MobEffects.DOLPHINS_GRACE
    "luck" -> MobEffects.LUCK
    else -> null
}

/** Reads a `buffs` string array param into effect holders, falling back to [default] when absent/empty. */
internal fun JsonObject.parseBuffs(default: List<Holder<MobEffect>>): List<Holder<MobEffect>> =
    getAsJsonArray("buffs")?.mapNotNull { effectByName(it.asString) }?.takeIf { it.isNotEmpty() } ?: default

/** Applies each effect to [entity] at the given duration/amplifier. */
internal fun LivingEntity.applyBuffs(buffs: List<Holder<MobEffect>>, duration: Int, amplifier: Int) {
    for (effect in buffs) addEffect(MobEffectInstance(effect, duration, amplifier))
}

// Nullable param readers — used by passive conditions to tell "absent" from a real value.
internal fun JsonObject?.optFOrNull(key: String): Float? =
    this?.get(key)?.takeIf { !it.isJsonNull }?.asFloat

internal fun JsonObject?.optDOrNull(key: String): Double? =
    this?.get(key)?.takeIf { !it.isJsonNull }?.asDouble

internal fun JsonObject?.optIOrNull(key: String): Int? =
    this?.get(key)?.takeIf { !it.isJsonNull }?.asInt

internal fun JsonObject?.optBOrNull(key: String): Boolean? =
    this?.get(key)?.takeIf { !it.isJsonNull }?.asBoolean
