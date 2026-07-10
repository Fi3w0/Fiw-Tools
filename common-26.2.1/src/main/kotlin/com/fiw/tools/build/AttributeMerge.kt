package com.fiw.tools.build

import net.minecraft.core.component.DataComponents
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.ItemAttributeModifiers

/**
 * Shared helper for keeping attribute modifiers that Fiw doesn't own. Any rebuild of a Fiw item's
 * `ATTRIBUTE_MODIFIERS` component ([ItemBuilder.applyAttributes] + imbue replay) re-derives only the
 * Fiw-owned modifiers (`fiw_tools:` namespace), so modifiers set by admin commands, datapacks or
 * other mods would silently vanish. Both rebuild sites — the resync/awaken/craft path
 * ([com.fiw.tools.state.StackStatePreserver]) and the anvil/smithing stations
 * ([EnchantingRules.restoreAttributes]) — call this to merge those foreign modifiers back on top.
 */
object AttributeMerge {
    /**
     * Merge foreign (non-`fiw_tools`) modifiers from [oldMods] into [stack]'s current attribute
     * component. Ids already present on the stack are skipped, so a config `attributes` entry that
     * declares its own custom id is never doubled.
     */
    fun mergeForeign(oldMods: ItemAttributeModifiers?, stack: ItemStack) {
        if (oldMods == null || oldMods.modifiers.isEmpty()) return
        val current = stack.get(DataComponents.ATTRIBUTE_MODIFIERS) ?: ItemAttributeModifiers.EMPTY
        val presentIds = current.modifiers.map { it.modifier.id() }.toHashSet()
        val builder = ItemAttributeModifiers.builder()
        for (entry in current.modifiers) builder.add(entry.attribute, entry.modifier, entry.slot)
        var changed = false
        for (entry in oldMods.modifiers) {
            val id = entry.modifier.id()
            if (id.namespace == "fiw_tools") continue
            if (id in presentIds) continue
            builder.add(entry.attribute, entry.modifier, entry.slot)
            changed = true
        }
        if (changed) stack.set(DataComponents.ATTRIBUTE_MODIFIERS, builder.build())
    }
}