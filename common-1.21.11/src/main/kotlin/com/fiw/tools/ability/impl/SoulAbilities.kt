package com.fiw.tools.ability.impl

import com.fiw.tools.ability.Ability
import com.fiw.tools.ability.AbilityContext
import com.fiw.tools.ability.AffectScope
import com.fiw.tools.ability.collectTargets
import com.fiw.tools.ability.optD
import com.fiw.tools.ability.optF
import com.fiw.tools.ability.scope
import com.fiw.tools.build.FiwItems
import com.fiw.tools.config.ItemRegistry
import com.fiw.tools.soul.SoulHandler
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource

/**
 * Soul system: `soul_collector` harvests souls from kills into the item stack's custom_data.
 * `soul_surge` spends all stored souls for a burst of AoE magic damage. Together they create
 * a resource-management layer on top of the existing ability system.
 *
 * Requires `"soulCapacity": N` on the item definition. The collector passive uses `on_kill`
 * trigger; soul_surge uses `on_right_click`.
 */

/** Collect a soul on kill, stored in the item. Requires the item definition to set `soulCapacity`. */
object SoulCollectorAbility : Ability {
    override fun execute(ctx: AbilityContext): Boolean {
        val id = FiwItems.fiwId(ctx.stack) ?: return false
        val cap = ItemRegistry.byId(id)?.soulCapacity ?: return false
        if (cap <= 0) return false
        val added = SoulHandler.addSoul(ctx.stack, cap)
        if (!added) return false
        val souls = SoulHandler.getSouls(ctx.stack)
        val world = ctx.world
        world.sendParticles(ParticleTypes.SOUL, ctx.player.x, ctx.player.y + 1.2, ctx.player.z, 6, 0.3, 0.5, 0.3, 0.03)
        world.playSound(null, ctx.player.x, ctx.player.y, ctx.player.z,
            SoundEvents.SOUL_ESCAPE, SoundSource.PLAYERS, 0.5f, 1.4f)
        ctx.player.sendSystemMessage(
            net.minecraft.network.chat.Component.literal("§5Soul absorbed §8(${souls}/${cap})")
        )
        return true
    }
}

/**
 * Drain all stored souls for an AoE damage burst — damage scales with souls spent.
 * A full soul pool = a powerful nuke. An empty pool does nothing and costs no cooldown.
 */
object SoulSurgeAbility : Ability {
    override fun execute(ctx: AbilityContext): Boolean {
        val souls = SoulHandler.getSouls(ctx.stack)
        if (souls == 0) return false
        val drained = SoulHandler.drainAll(ctx.stack)
        val damagePerSoul = ctx.params.optF("damagePerSoul", 2f)
        val radius = ctx.params.optD("radius", 6.0)
        val scope = ctx.params.scope("affects", AffectScope.ALL)
        val world = ctx.world
        val player = ctx.player
        val totalDamage = drained * damagePerSoul
        val src = world.damageSources().magic()
        var hit = false
        for (e in collectTargets(world, player.position(), radius, player, scope)) {
            e.hurtServer(world, src, totalDamage)
            world.sendParticles(ParticleTypes.SOUL, e.x, e.y + 1.0, e.z, 8, 0.3, 0.4, 0.3, 0.03)
            hit = true
        }
        world.sendParticles(ParticleTypes.SOUL, player.x, player.y + 1.0, player.z, 30, 1.5, 1.0, 1.5, 0.05)
        world.playSound(null, player.x, player.y, player.z,
            SoundEvents.WITHER_SHOOT, SoundSource.PLAYERS, 0.9f, 0.8f)
        player.sendSystemMessage(
            net.minecraft.network.chat.Component.literal("§5Released §d${drained}§5 souls for §d${totalDamage.toInt()}§5 damage.")
        )
        return hit
    }
}
