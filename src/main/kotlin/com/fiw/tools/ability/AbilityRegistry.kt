package com.fiw.tools.ability

import com.fiw.tools.ability.impl.ArcSlashAbility
import com.fiw.tools.ability.impl.BlinkAbility
import com.fiw.tools.ability.impl.FrostNovaAbility
import com.fiw.tools.ability.impl.HealOnHitAbility
import com.fiw.tools.ability.impl.LightningStrikeAbility
import com.fiw.tools.ability.impl.ProjectileBurstAbility
import com.fiw.tools.ability.impl.RiptideDashAbility
import com.fiw.tools.ability.impl.ShockwaveAbility

object AbilityRegistry {
    private val factories: MutableMap<String, () -> Ability> = HashMap()

    fun init() {
        register("riptide_dash") { RiptideDashAbility }
        register("arc_slash") { ArcSlashAbility }
        register("lightning_strike") { LightningStrikeAbility }
        register("shockwave") { ShockwaveAbility }
        register("heal_on_hit") { HealOnHitAbility }
        register("blink") { BlinkAbility }
        register("projectile_burst") { ProjectileBurstAbility }
        register("frost_nova") { FrostNovaAbility }
    }

    fun register(type: String, factory: () -> Ability) {
        factories[type.lowercase()] = factory
    }

    fun get(type: String): Ability? = factories[type.lowercase()]?.invoke()

    fun knownTypes(): Set<String> = factories.keys
}
