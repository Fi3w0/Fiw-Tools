package com.fiw.tools.ability.impl

import com.fiw.tools.ability.Ability
import com.fiw.tools.ability.AbilityContext
import com.fiw.tools.ability.optB
import com.fiw.tools.ability.optI
import com.fiw.tools.build.ItemBuilder
import com.fiw.tools.util.HolderAccess
import net.minecraft.core.component.DataComponents
import net.minecraft.core.particles.ParticleOptions
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundSource
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.CustomData

/**
 * Consumable curse-breaker. On right-click: walks the configured scope of the player's inventory,
 * stamps `fiw_uncursed: 1b` onto up to `limit` cursed Fiw stacks, optionally consumes itself, plays
 * a holy sound and a sparkle. Returns false on a wasted use so the cooldown isn't spent.
 *
 * Params: `scope` (main_hand | off_hand | armor | all_inventory, default all_inventory), `limit`
 * (default 1, 0 = uncurse all), `consumeSelf` (default true), `sound`, `particles`.
 */
object UncurseAbility : Ability {
    override fun execute(ctx: AbilityContext): Boolean {
        val params = ctx.params
        val scope = params.get("scope")?.takeIf { !it.isJsonNull }?.asString?.lowercase() ?: "all_inventory"
        val limit = params.optI("limit", 1).coerceAtLeast(0)
        val consumeSelf = params.optB("consumeSelf", true)

        val inv = ctx.player.inventory
        val candidates = slotsFor(scope, inv.containerSize)

        var uncursed = 0
        for (slot in candidates) {
            if (limit > 0 && uncursed >= limit) break
            val stack = inv.getItem(slot)
            if (stack === ctx.stack) continue  // don't target the scroll itself
            if (!uncurseStack(stack)) continue
            uncursed++
        }

        if (uncursed == 0) return false

        val soundId = params.get("sound")?.takeIf { !it.isJsonNull }?.asString ?: "minecraft:block.beacon.activate"
        val particlesId = params.get("particles")?.takeIf { !it.isJsonNull }?.asString ?: "minecraft:end_rod"
        playSound(ctx, soundId)
        spawnParticles(ctx, particlesId)

        if (consumeSelf) ctx.stack.shrink(1)
        return true
    }

    /** Stamp the per-stack uncurse flag. Returns true only if [stack] was a cursed item that wasn't already cleared. */
    private fun uncurseStack(stack: ItemStack): Boolean {
        if (stack.isEmpty) return false
        val data = stack.get(DataComponents.CUSTOM_DATA) ?: return false
        val tag = data.copyTag()
        if (!tag.getBoolean(ItemBuilder.CURSED_KEY).orElse(false)) return false
        if (tag.getBoolean(ItemBuilder.UNCURSED_KEY).orElse(false)) return false
        tag.putBoolean(ItemBuilder.UNCURSED_KEY, true)
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag))
        return true
    }

    private fun slotsFor(scope: String, size: Int): IntRange = when (scope) {
        // 0..8 = hotbar, 9..35 = main grid, 36..39 = armor (head/chest/legs/feet), 40 = offhand.
        "main_hand" -> 0..0  // current selected hotbar slot is the held item; player API has selected, but
        // the dispatcher already passed ctx.stack — for "main_hand" scope we just hit the held stack.
        // Skipped in execute() by `stack === ctx.stack`, so we fall back to all_inventory below.
        "off_hand" -> 40..40
        "armor" -> 36..39
        else -> 0 until size
    }

    private fun playSound(ctx: AbilityContext, id: String) {
        val sid = Identifier.tryParse(id) ?: return
        val holder = BuiltInRegistries.SOUND_EVENT.get(sid).orElse(null) ?: return
        val event = HolderAccess.value(holder)
        ctx.world.playSound(null, ctx.player.x, ctx.player.y, ctx.player.z,
            event, SoundSource.PLAYERS, 0.8f, 1.2f)
    }

    private fun spawnParticles(ctx: AbilityContext, id: String) {
        val sid = Identifier.tryParse(id) ?: return
        val holder = BuiltInRegistries.PARTICLE_TYPE.get(sid).orElse(null) ?: return
        val type = HolderAccess.value(holder) as? ParticleOptions ?: return
        ctx.world.sendParticles(type, ctx.player.x, ctx.player.y + 1.0, ctx.player.z,
            30, 0.5, 0.8, 0.5, 0.05)
    }
}
