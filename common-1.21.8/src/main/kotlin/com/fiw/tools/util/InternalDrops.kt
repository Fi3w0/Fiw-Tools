package com.fiw.tools.util

import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack

/**
 * Overflow drops the mod performs itself — give-command spill, infinite-ammo returns, crafting
 * remainders, keep-on-death restores. ServerPlayerDropMixin checks [active] so these never fire
 * the `on_drop` trigger: that trigger means "the player deliberately tossed the item".
 */
object InternalDrops {
    private val depth = ThreadLocal.withInitial { 0 }

    @JvmStatic
    fun drop(player: ServerPlayer, stack: ItemStack) {
        depth.set(depth.get() + 1)
        try {
            player.drop(stack, false, false)
        } finally {
            depth.set(depth.get() - 1)
        }
    }

    @JvmStatic
    fun active(): Boolean = depth.get() > 0
}
