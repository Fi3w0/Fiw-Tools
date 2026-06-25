package com.fiw.tools.build

import net.minecraft.core.component.DataComponents
import net.minecraft.world.item.ItemStack

/**
 * Small read-only helpers for recognising Fiw Tools items by their persistent `fiw_tools_id`
 * custom_data tag. Shared by Kotlin handlers and Java mixins (hence [JvmStatic]).
 */
object FiwItems {
    /** The item's Fiw Tools id, or null if this is not one of ours. */
    @JvmStatic
    fun fiwId(stack: ItemStack): String? {
        if (stack.isEmpty) return null
        val data = stack.get(DataComponents.CUSTOM_DATA) ?: return null
        return data.copyTag().getString(ItemBuilder.FIW_TOOLS_ID_KEY).orElse(null)
    }

    @JvmStatic
    fun isFiwItem(stack: ItemStack): Boolean = fiwId(stack) != null
}
