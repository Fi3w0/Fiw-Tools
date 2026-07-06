package com.fiw.tools.build

import com.fiw.tools.config.ItemDefinition
import com.fiw.tools.config.ItemRegistry
import net.minecraft.core.component.DataComponents
import net.minecraft.world.item.ItemStack

/**
 * Resolves what a Fiw item may do at each enchanting station, per its `enchanting` config block.
 * Null config fields fall back to the long-standing defaults: cursed items refuse the enchanting
 * table and anvil, every Fiw item refuses the grindstone (config enchantments are permanent),
 * smithing and anvil renaming are allowed. Non-Fiw stacks are always vanilla-free.
 *
 * [JvmStatic] because the station mixins are Java.
 */
object EnchantingRules {
    @JvmStatic
    fun allowsTable(stack: ItemStack): Boolean =
        resolve(stack, { it.table }, { def, s -> !cursed(def, s) })

    @JvmStatic
    fun allowsAnvil(stack: ItemStack): Boolean =
        resolve(stack, { it.anvil }, { def, s -> !cursed(def, s) })

    @JvmStatic
    fun allowsGrindstone(stack: ItemStack): Boolean =
        resolve(stack, { it.grindstone }, { _, _ -> false })

    @JvmStatic
    fun allowsSmithing(stack: ItemStack): Boolean =
        resolve(stack, { it.smithing }, { _, _ -> true })

    @JvmStatic
    fun allowsRename(stack: ItemStack): Boolean =
        resolve(stack, { it.allowRename }, { _, _ -> true })

    /** Build-time table permission for [def]; drives whether ENCHANTABLE is stripped. */
    @JvmStatic
    fun tableAllowed(def: ItemDefinition): Boolean = def.enchanting?.table ?: !def.cursed

    /**
     * Forces the configured name back onto an anvil [result] whose input forbids renaming: the
     * result's custom name becomes exactly the [input]'s again. Returns true if it changed.
     */
    @JvmStatic
    fun restoreName(input: ItemStack, result: ItemStack): Boolean {
        val original = input.get(DataComponents.CUSTOM_NAME)
        if (original == result.get(DataComponents.CUSTOM_NAME)) return false
        if (original == null) result.remove(DataComponents.CUSTOM_NAME)
        else result.set(DataComponents.CUSTOM_NAME, original)
        return true
    }

    private inline fun resolve(
        stack: ItemStack,
        explicit: (ItemDefinition.EnchantingDef) -> Boolean?,
        fallback: (ItemDefinition?, ItemStack) -> Boolean
    ): Boolean {
        if (stack.isEmpty) return true
        val fiwId = FiwItems.fiwId(stack) ?: return true
        val def = ItemRegistry.byId(fiwId)
        def?.enchanting?.let { cfg -> explicit(cfg)?.let { return it } }
        return fallback(def, stack)
    }

    /** Def flag when known, else the stack's stamped tag (stale items keep their curse rules). */
    private fun cursed(def: ItemDefinition?, stack: ItemStack): Boolean {
        if (def != null) return def.cursed
        val data = stack.get(DataComponents.CUSTOM_DATA) ?: return false
        return data.copyTag().getBoolean(ItemBuilder.CURSED_KEY)
    }
}
