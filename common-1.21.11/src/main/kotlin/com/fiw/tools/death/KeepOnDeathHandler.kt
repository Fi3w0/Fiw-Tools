package com.fiw.tools.death

import com.fiw.tools.build.ItemBuilder
import net.minecraft.core.component.DataComponents
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object KeepOnDeathHandler {
    private val stashed: MutableMap<UUID, MutableList<Pair<Int, ItemStack>>> = ConcurrentHashMap()

    fun onClone(newPlayer: ServerPlayer, alive: Boolean) {
        if (!alive) restore(newPlayer)
    }

    @JvmStatic
    fun shouldKeep(stack: ItemStack): Boolean {
        if (stack.isEmpty) return false
        val data = stack.get(DataComponents.CUSTOM_DATA) ?: return false
        val tag = data.copyTag()
        return tag.getBoolean(ItemBuilder.KEEP_ON_DEATH_KEY).orElse(false)
    }

    @JvmStatic
    fun stash(playerId: UUID, slot: Int, stack: ItemStack) {
        if (stack.isEmpty) return
        stashed.computeIfAbsent(playerId) { mutableListOf() }.add(slot to stack.copy())
    }

    fun restore(newPlayer: ServerPlayer) {
        val list = stashed.remove(newPlayer.uuid) ?: return
        val inv = newPlayer.inventory
        for ((slot, stack) in list) {
            if (slot in 0 until inv.containerSize && inv.getItem(slot).isEmpty) {
                inv.setItem(slot, stack)
            } else if (!inv.add(stack)) {
                newPlayer.drop(stack, false, false)
            }
        }
    }
}
