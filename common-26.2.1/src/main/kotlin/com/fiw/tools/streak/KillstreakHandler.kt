package com.fiw.tools.streak

import com.fiw.tools.ability.applyBuffs
import com.fiw.tools.ability.effectByName
import com.fiw.tools.build.FiwItems
import com.fiw.tools.config.ItemDefinition
import com.fiw.tools.config.ItemRegistry
import com.fiw.tools.util.TextParser
import net.minecraft.core.component.DataComponents
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.CustomData

/**
 * Killstreak momentum. Items with a `killstreak` block count consecutive kills per stack
 * (`fiw_streak` in custom data): every `killsPerTier` kills raises the tier, each kill applies
 * the configured buffs at amplifier tier-1 (refreshed per kill), and dying resets the count.
 * `{streak}` / `{streak_tier}` are available in `dynamicLore`.
 */
object KillstreakHandler {
    /** Per-stack custom-data key: current kill streak. */
    const val STREAK_KEY = "fiw_streak"

    fun streak(stack: ItemStack): Int {
        val data = stack.get(DataComponents.CUSTOM_DATA) ?: return 0
        return data.copyTag().getInt(STREAK_KEY).orElse(0)
    }

    fun tier(stack: ItemStack, def: ItemDefinition): Int {
        val ks = def.killstreak ?: return 0
        return (streak(stack) / ks.killsPerTier).coerceAtMost(ks.maxTier)
    }

    /** Called from the kill dispatch path with the weapon that scored the kill. */
    fun onKill(player: ServerPlayer, stack: ItemStack) {
        if (stack.isEmpty) return
        val def = ItemRegistry.byId(FiwItems.fiwId(stack) ?: return) ?: return
        val ks = def.killstreak ?: return

        val streak = streak(stack) + 1
        write(stack) { it.putInt(STREAK_KEY, streak) }

        val tier = (streak / ks.killsPerTier).coerceAtMost(ks.maxTier)
        if (tier >= 1) {
            val effects = ks.buffs.mapNotNull { effectByName(it) }
            if (effects.isNotEmpty()) player.applyBuffs(effects, ks.buffDuration, tier - 1)
        }
        val previousTier = ((streak - 1) / ks.killsPerTier).coerceAtMost(ks.maxTier)
        val tierMessage = ks.tierMessage
        if (tier > previousTier && !tierMessage.isNullOrEmpty()) {
            val text = tierMessage
                .replace("{streak}", streak.toString())
                .replace("{tier}", tier.toString())
            player.sendOverlayMessage(TextParser.parse(text)) // 26.x: displayClientMessage(c, true) renamed
        }
    }

    /** Death wipes the streak on every carried item that asked for it. */
    fun onDeath(player: ServerPlayer) {
        val inv = player.inventory
        for (slot in 0 until inv.containerSize) {
            val stack = inv.getItem(slot)
            if (stack.isEmpty) continue
            val def = ItemRegistry.byId(FiwItems.fiwId(stack) ?: continue) ?: continue
            val ks = def.killstreak ?: continue
            if (ks.resetOnDeath && streak(stack) > 0) {
                write(stack) { it.putInt(STREAK_KEY, 0) }
            }
        }
    }

    private inline fun write(stack: ItemStack, edit: (net.minecraft.nbt.CompoundTag) -> Unit) {
        val data = stack.get(DataComponents.CUSTOM_DATA) ?: return
        val tag = data.copyTag()
        edit(tag)
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag))
    }
}
