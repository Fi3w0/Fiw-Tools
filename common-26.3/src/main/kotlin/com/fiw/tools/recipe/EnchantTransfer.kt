package com.fiw.tools.recipe

import net.minecraft.core.Holder
import net.minecraft.core.component.DataComponents
import net.minecraft.tags.EnchantmentTags
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.enchantment.Enchantment
import net.minecraft.world.item.enchantment.ItemEnchantments

/**
 * Carries enchantments from the consumed crafting-grid items onto the crafted result, per the
 * recipe's `enchantments` block. Runs on the exact stack that ends up in the result slot, in every
 * path that can produce it (tick handler and injected vanilla recipe), so the shown and the taken
 * result always agree.
 *
 * Every ingredient in the grid is consumed by the craft, so nothing is ever copied from an item
 * the player keeps — the enchantments move, they don't multiply. Final levels are clamped to
 * 1..255 (the component format's storable range) so no merge policy can write a level the save
 * format silently corrupts.
 */
object EnchantTransfer {

    fun apply(recipe: RecipeDefinition, result: ItemStack, grid: List<ItemStack>) {
        val cfg = recipe.enchantments ?: return
        if (result.isEmpty || cfg.mode == RecipeEnchantmentsDef.MODE_KEEP_NEW) return

        // Accepted enchantments in decision order: the result's own first (merge mode), then
        // donors slot by slot. LinkedHashMap keeps that order for the checkConflicts pass.
        val acc = LinkedHashMap<Holder<Enchantment>, Entry>()
        if (cfg.mode == RecipeEnchantmentsDef.MODE_MERGE) {
            for (entry in enchantsOn(result).entrySet()) {
                acc[entry.key] = Entry(entry.intValue, fromDonor = false)
            }
        }

        for (stack in grid) {
            if (stack.isEmpty || !donates(cfg, stack)) continue
            for (entry in enchantsOn(stack).entrySet()) {
                offer(cfg, acc, result, entry.key, entry.intValue)
            }
        }

        val final = ItemEnchantments.Mutable(ItemEnchantments.EMPTY)
        for ((holder, entry) in acc) {
            var level = entry.level
            if (cfg.respectMaxLevel) level = minOf(level, holder.value().maxLevel)
            level = level.coerceAtMost(cfg.maxLevel).coerceAtMost(255)
            if (level < 1) continue
            final.set(holder, level)
        }
        write(result, final.toImmutable())
    }

    private class Entry(var level: Int, var fromDonor: Boolean)

    /** Feed one donor enchantment into the accepted set, applying filters and conflict policy. */
    private fun offer(
        cfg: RecipeEnchantmentsDef,
        acc: LinkedHashMap<Holder<Enchantment>, Entry>,
        result: ItemStack,
        holder: Holder<Enchantment>,
        level: Int
    ) {
        if (level < 1) return
        val id = holder.unwrapKey().map { it.identifier().toString() }.orElse(null) ?: return
        if (cfg.only.isNotEmpty() && id !in cfg.only) return
        if (id in cfg.exclude) return
        if (!cfg.transferCurses && holder.`is`(EnchantmentTags.CURSE)) return
        // Books hold anything, so compatibility only makes sense against a real gear result.
        if (cfg.onlyCompatible && !result.`is`(Items.ENCHANTED_BOOK) && !holder.value().canEnchant(result)) return

        val existing = acc[holder]
        if (existing == null) {
            if (cfg.checkConflicts && acc.keys.any { !Enchantment.areCompatible(it, holder) }) return
            acc[holder] = Entry(level, fromDonor = true)
            return
        }
        existing.level = when (cfg.conflict) {
            RecipeEnchantmentsDef.CONFLICT_LOWEST -> minOf(existing.level, level)
            RecipeEnchantmentsDef.CONFLICT_ADD -> existing.level + level
            RecipeEnchantmentsDef.CONFLICT_COMBINE ->
                if (existing.level == level) level + 1 else maxOf(existing.level, level)
            // A donor already claimed this slot: the earlier grid slot keeps it.
            RecipeEnchantmentsDef.CONFLICT_OLD -> if (existing.fromDonor) existing.level else level
            RecipeEnchantmentsDef.CONFLICT_NEW -> existing.level
            else -> maxOf(existing.level, level) // CONFLICT_HIGHEST
        }
        existing.fromDonor = true
    }

    /** May this grid stack donate its enchantments? Empty `from` means every ingredient may. */
    private fun donates(cfg: RecipeEnchantmentsDef, stack: ItemStack): Boolean =
        cfg.from.isEmpty() || cfg.from.any { CraftingHandler.ingredientMatches(it, stack) }

    /** Live + stored enchantments — a book donor's payload lives in STORED_ENCHANTMENTS. */
    private fun enchantsOn(stack: ItemStack): ItemEnchantments {
        val live = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY)
        val stored = stack.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY)
        if (stored.isEmpty) return live
        if (live.isEmpty) return stored
        val merged = ItemEnchantments.Mutable(live)
        for (entry in stored.entrySet()) {
            merged.set(entry.key, maxOf(merged.getLevel(entry.key), entry.intValue))
        }
        return merged.toImmutable()
    }

    private fun write(result: ItemStack, enchants: ItemEnchantments) {
        val book = result.`is`(Items.ENCHANTED_BOOK)
        val component = if (book) DataComponents.STORED_ENCHANTMENTS else DataComponents.ENCHANTMENTS
        // A book result must hold ONE list. Its configured defaults may sit in the live
        // component (ItemBuilder writes there) — merge mode has folded them into [enchants] and
        // keep_old deliberately discards them — so clear the live copy either way, or the book
        // would carry two enchantment lists at once.
        if (book && !result.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).isEmpty) {
            result.set(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY)
        }
        val existing = result.getOrDefault(component, ItemEnchantments.EMPTY)
        // Nothing before, nothing after: don't touch the stack (avoids a pointless component patch).
        if (enchants.isEmpty && existing.isEmpty) return
        result.set(component, enchants)
    }
}
