package com.fiw.tools.soul

import net.minecraft.core.component.DataComponents
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.CustomData

/**
 * Reads and writes soul counts stored in item stack custom_data under `fiw_souls`.
 * Souls are transient in the sense that they live on the item stack — they survive disconnects
 * but reset when the item is rebuilt by `/fiwtools reload` (which is intentional: reload is a
 * hard reset, same as imbue count behaviour).
 */
object SoulHandler {
    const val SOUL_KEY = "fiw_souls"

    fun getSouls(stack: ItemStack): Int {
        val data = stack.get(DataComponents.CUSTOM_DATA) ?: return 0
        return data.copyTag().getInt(SOUL_KEY).orElse(0)
    }

    /** Add one soul up to [capacity]. Returns true if a soul was actually added. */
    fun addSoul(stack: ItemStack, capacity: Int): Boolean {
        val current = getSouls(stack)
        if (current >= capacity) return false
        writeSouls(stack, current + 1)
        return true
    }

    /** Spend [amount] souls. Returns true if there were enough. */
    fun spendSouls(stack: ItemStack, amount: Int): Boolean {
        val current = getSouls(stack)
        if (current < amount) return false
        writeSouls(stack, current - amount)
        return true
    }

    /** Drain all souls and return the count drained. */
    fun drainAll(stack: ItemStack): Int {
        val current = getSouls(stack)
        if (current == 0) return 0
        writeSouls(stack, 0)
        return current
    }

    private fun writeSouls(stack: ItemStack, amount: Int) {
        val data = stack.get(DataComponents.CUSTOM_DATA) ?: CustomData.EMPTY
        val tag = data.copyTag()
        tag.putInt(SOUL_KEY, amount)
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag))
    }
}
