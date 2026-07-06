package com.fiw.tools.recipe

/**
 * In-memory representation of one custom crafting recipe. Ingredient/result strings are kept raw
 * so the crafting handler can resolve them lazily on a live server:
 *
 * - `"fiw:<id>"` — a Fiw Tools custom item (matched by its persistent `fiw_tools_id` tag)
 * - `"#namespace:tag"` — any item in a vanilla item tag
 * - `"namespace:item"` — a plain vanilla (or other-mod) item; deliberately does NOT match Fiw items
 *   built on that base, so custom items can't be spent as their vanilla base material.
 */
data class RecipeDefinition(
    val id: String,
    val shaped: Boolean,
    /** Shaped only: 1–3 rows of 1–3 symbols. A space means "slot must be empty". */
    val pattern: List<String> = emptyList(),
    /** Shaped only: pattern symbol → ingredient spec. */
    val key: Map<Char, String> = emptyMap(),
    /** Shapeless only: 1–9 ingredient specs, each consuming one item. */
    val ingredients: List<String> = emptyList(),
    /** Result spec, same syntax as ingredients (`fiw:` or vanilla id). */
    val resultItem: String,
    val resultCount: Int = 1,
    /** When set, enchantments on consumed ingredients carry over to the result. */
    val enchantments: RecipeEnchantmentsDef? = null
) {
    val width: Int get() = pattern.maxOfOrNull { it.length } ?: 0
    val height: Int get() = pattern.size

    /** Ingredient spec at (col,row) of the trimmed shaped pattern, or null for an empty slot. */
    fun ingredientAt(col: Int, row: Int): String? {
        val line = pattern.getOrNull(row) ?: return null
        val symbol = line.getOrNull(col) ?: return null
        if (symbol == ' ') return null
        return key[symbol]
    }

    fun describeShort(): String = buildString {
        append(if (shaped) "shaped ${width}x$height" else "shapeless ${ingredients.size}")
        append(" -> ").append(resultItem)
        if (resultCount != 1) append(" x").append(resultCount)
        if (enchantments != null) append(" [enchants: ${enchantments.mode}]")
    }
}

/**
 * How enchantments move from consumed ingredients onto the crafted result. "Old" always means
 * enchantments already sitting on the grid items; "new" means the enchantments the freshly built
 * result carries by itself (a Fiw item's configured `enchantments`, or none for plain items).
 */
data class RecipeEnchantmentsDef(
    /** [MODE_MERGE] (old + new), [MODE_KEEP_OLD] (ingredients win wholesale), [MODE_KEEP_NEW] (no transfer). */
    val mode: String = MODE_MERGE,
    /** Level to keep when the same enchantment exists on both sides — see CONFLICT_* constants. */
    val conflict: String = CONFLICT_HIGHEST,
    /** Ingredient specs allowed to donate enchantments; empty = every consumed ingredient. */
    val from: List<String> = emptyList(),
    /** Enchantment id whitelist (normalized `namespace:path`); empty = all. */
    val only: List<String> = emptyList(),
    /** Enchantment id blacklist (normalized `namespace:path`). */
    val exclude: List<String> = emptyList(),
    /** Transfer curse enchantments (binding/vanishing) too. */
    val transferCurses: Boolean = true,
    /** Clamp each level to the enchantment's own vanilla maximum. */
    val respectMaxLevel: Boolean = false,
    /** Hard level cap after all merging; 1..255 (255 is the component format's limit). */
    val maxLevel: Int = 255,
    /** Drop enchantments the result item can't normally hold (e.g. Lure on a chestplate). */
    val onlyCompatible: Boolean = false,
    /** Drop incoming enchantments that vanilla marks mutually exclusive with one already kept. */
    val checkConflicts: Boolean = false,
    /** Must be set to true before a `count > 1` result may copy enchantments onto every copy. */
    val allowMultiplied: Boolean = false
) {
    companion object {
        const val MODE_MERGE = "merge"
        const val MODE_KEEP_OLD = "keep_old"
        const val MODE_KEEP_NEW = "keep_new"

        /** Higher level wins (default). */
        const val CONFLICT_HIGHEST = "highest"
        /** Lower level wins. */
        const val CONFLICT_LOWEST = "lowest"
        /** The ingredient's level wins; between two ingredients the earlier grid slot wins. */
        const val CONFLICT_OLD = "old"
        /** The result's own level wins; between two ingredients the earlier grid slot wins. */
        const val CONFLICT_NEW = "new"
        /** Levels are added together. */
        const val CONFLICT_ADD = "add"
        /** Anvil rule: equal levels combine into level + 1, otherwise the higher wins. */
        const val CONFLICT_COMBINE = "combine"
    }
}
