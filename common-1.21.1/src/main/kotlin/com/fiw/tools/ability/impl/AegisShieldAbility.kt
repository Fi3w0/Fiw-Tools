package com.fiw.tools.ability.impl

import com.fiw.tools.ability.Ability
import com.fiw.tools.ability.AbilityContext
import com.fiw.tools.ability.AbilityState
import com.fiw.tools.ability.optF
import com.fiw.tools.ability.optI
import com.fiw.tools.ability.optParticle
import com.google.gson.JsonObject
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundSource

/**
 * Aegis shield (`aegis_shield`) — raise an on-demand barrier that swallows incoming hits. For
 * `duration` ticks the caster has a `pool` of damage the barrier absorbs; each hit is eaten whole
 * while the pool lasts, and the hit that drains it shatters the barrier. Unlike Absorption this is a
 * real damage gate, not extra hearts — a chip hit and a huge hit both cost one "hold" of the shield.
 *
 * | Param | Default | Meaning |
 * |-------|---------|---------|
 * | `pool` | 20 | Total damage the barrier absorbs before it breaks. |
 * | `duration` | 100 | Ticks the barrier lasts if unbroken. |
 * | `particle` | `end_rod` | Dome burst on cast. |
 * | `sound` | `block.beacon.activate` | Played on cast. |
 */
object AegisShieldAbility : Ability {
    override fun execute(ctx: AbilityContext): Boolean {
        val p = ctx.params
        val player = ctx.player
        val world = ctx.world

        val pool = p.optF("pool", 20f).coerceAtLeast(0.5f)
        val duration = p.optI("duration", 100).coerceAtLeast(1)
        AbilityState.addAegis(player.uuid, pool, world.gameTime + duration)

        world.sendParticles(p.optParticle("particle", ParticleTypes.END_ROD),
            player.x, player.y + 1.0, player.z, 60, 0.6, 1.0, 0.6, 0.02)
        val sound = soundById(p.optS("sound", "minecraft:block.beacon.activate"))
        if (sound != null) {
            world.playSound(null, player.x, player.y, player.z, sound, SoundSource.PLAYERS,
                p.optF("soundVolume", 1.0f), p.optF("soundPitch", 1.2f))
        }
        return true
    }

    private fun soundById(id: String): SoundEvent? {
        if (id.isEmpty()) return null
        val sid = ResourceLocation.tryParse(id) ?: return null
        return BuiltInRegistries.SOUND_EVENT.get(sid)
    }

    private fun JsonObject?.optS(key: String, default: String): String =
        this?.get(key)?.takeIf { it.isJsonPrimitive }?.asString ?: default
}
