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
    val resultCount: Int = 1
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
    }
}
