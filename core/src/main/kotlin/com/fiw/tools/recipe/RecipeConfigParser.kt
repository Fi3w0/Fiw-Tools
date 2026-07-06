package com.fiw.tools.recipe

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * Parses recipe JSON files. One file may hold a single recipe object, a bare array of recipes,
 * or `{ "recipes": [ ... ] }` — so admins can group as many crafts as they like per file.
 */
object RecipeConfigParser {
    /**
     * Parse every recipe in [text]. [fallbackIdPrefix] (usually the file name) names recipes that
     * omit their own `id`, as `<prefix>#<index>`.
     */
    fun parseAll(text: String, fallbackIdPrefix: String): List<RecipeDefinition> {
        val root = JsonParser.parseString(text)
        val entries: JsonArray = when {
            root.isJsonArray -> root.asJsonArray
            root.isJsonObject && root.asJsonObject.has("recipes") ->
                root.asJsonObject.getAsJsonArray("recipes")
                    ?: throw IllegalArgumentException("'recipes' must be an array")
            root.isJsonObject -> JsonArray().apply { add(root) }
            else -> throw IllegalArgumentException("recipe file must be an object or array")
        }
        return entries.mapIndexed { index, element -> parseOne(element, "$fallbackIdPrefix#$index") }
    }

    private fun parseOne(element: JsonElement, fallbackId: String): RecipeDefinition {
        if (!element.isJsonObject) throw IllegalArgumentException("recipe entry must be an object")
        val o = element.asJsonObject
        val id = o.optString("id") ?: fallbackId
        val result = o.getAsJsonObject("result")
            ?: throw IllegalArgumentException("recipe '$id': missing 'result' object")
        val resultItem = result.optString("item")
            ?: throw IllegalArgumentException("recipe '$id': result needs an 'item'")
        val resultCount = result.optInt("count") ?: 1
        if (resultCount < 1) throw IllegalArgumentException("recipe '$id': result count must be >= 1")

        val declaredType = o.optString("type")?.lowercase()
        val hasPattern = o.has("pattern")
        val shaped = when (declaredType) {
            "shaped" -> true
            "shapeless" -> false
            null -> hasPattern
            else -> throw IllegalArgumentException("recipe '$id': unknown type '$declaredType'")
        }

        val enchantments = parseEnchantments(o, id, resultCount)

        return if (shaped) {
            val pattern = o.getAsJsonArray("pattern")?.map { it.asString }
                ?: throw IllegalArgumentException("recipe '$id': shaped recipe needs a 'pattern'")
            if (pattern.isEmpty() || pattern.size > 3 || pattern.any { it.isEmpty() || it.length > 3 })
                throw IllegalArgumentException("recipe '$id': pattern must be 1-3 rows of 1-3 symbols")
            val width = pattern.maxOf { it.length }
            if (pattern.any { it.length != width })
                throw IllegalArgumentException("recipe '$id': all pattern rows must be the same length")
            val key = parseKey(o.getAsJsonObject("key"), id)
            val symbols = pattern.flatMap { it.toList() }.filter { it != ' ' }.toSet()
            val missing = symbols.filter { it !in key }
            if (missing.isNotEmpty())
                throw IllegalArgumentException("recipe '$id': pattern symbol(s) $missing missing from 'key'")
            if (symbols.isEmpty())
                throw IllegalArgumentException("recipe '$id': pattern is entirely empty")
            RecipeDefinition(id, shaped = true, pattern = pattern, key = key,
                resultItem = resultItem, resultCount = resultCount, enchantments = enchantments)
        } else {
            val ingredients = o.getAsJsonArray("ingredients")?.map { it.asString }
                ?: throw IllegalArgumentException("recipe '$id': shapeless recipe needs 'ingredients'")
            if (ingredients.isEmpty() || ingredients.size > 9)
                throw IllegalArgumentException("recipe '$id': shapeless needs 1-9 ingredients")
            RecipeDefinition(id, shaped = false, ingredients = ingredients,
                resultItem = resultItem, resultCount = resultCount, enchantments = enchantments)
        }
    }

    /**
     * Parses the optional `"enchantments"` transfer block. Strict on purpose: an unknown mode or
     * conflict value fails the whole recipe at load time instead of silently doing something the
     * admin didn't intend with players' enchanted gear.
     */
    private fun parseEnchantments(o: JsonObject, id: String, resultCount: Int): RecipeEnchantmentsDef? {
        if (!o.has("enchantments") || o.get("enchantments").isJsonNull) return null
        val e = o.get("enchantments")
        // `"enchantments": true` is shorthand for the defaults (merge, highest wins).
        if (e.isJsonPrimitive && e.asJsonPrimitive.isBoolean) {
            return if (e.asBoolean) validated(RecipeEnchantmentsDef(), id, resultCount) else null
        }
        if (!e.isJsonObject) throw IllegalArgumentException(
            "recipe '$id': 'enchantments' must be true/false or an object")
        val obj = e.asJsonObject

        val mode = when (val raw = obj.optString("mode")?.lowercase()) {
            null, "merge", "merged", "both" -> RecipeEnchantmentsDef.MODE_MERGE
            "keep_old", "old", "ingredients", "transfer" -> RecipeEnchantmentsDef.MODE_KEEP_OLD
            "keep_new", "new", "none", "result" -> RecipeEnchantmentsDef.MODE_KEEP_NEW
            else -> throw IllegalArgumentException(
                "recipe '$id': unknown enchantments mode '$raw' (use merge, keep_old or keep_new)")
        }
        val conflict = when (val raw = obj.optString("conflict")?.lowercase()) {
            null, "highest", "max" -> RecipeEnchantmentsDef.CONFLICT_HIGHEST
            "lowest", "min" -> RecipeEnchantmentsDef.CONFLICT_LOWEST
            "old", "ingredient" -> RecipeEnchantmentsDef.CONFLICT_OLD
            "new", "result" -> RecipeEnchantmentsDef.CONFLICT_NEW
            "add", "sum" -> RecipeEnchantmentsDef.CONFLICT_ADD
            "combine", "anvil" -> RecipeEnchantmentsDef.CONFLICT_COMBINE
            else -> throw IllegalArgumentException(
                "recipe '$id': unknown enchantments conflict '$raw' (use highest, lowest, old, new, add or combine)")
        }
        val maxLevel = obj.optInt("maxLevel") ?: 255
        if (maxLevel < 1 || maxLevel > 255) throw IllegalArgumentException(
            "recipe '$id': enchantments maxLevel must be 1-255 (levels above 255 don't survive saving)")

        val def = RecipeEnchantmentsDef(
            mode = mode,
            conflict = conflict,
            from = obj.optStringList("from", id),
            only = obj.optStringList("only", id).map { normalizeEnchantId(it) },
            exclude = obj.optStringList("exclude", id).map { normalizeEnchantId(it) },
            transferCurses = obj.optBool("transferCurses") ?: true,
            respectMaxLevel = obj.optBool("respectMaxLevel") ?: false,
            maxLevel = maxLevel,
            onlyCompatible = obj.optBool("onlyCompatible") ?: false,
            checkConflicts = obj.optBool("checkConflicts") ?: false,
            allowMultiplied = obj.optBool("allowMultiplied") ?: false
        )
        return validated(def, id, resultCount)
    }

    private fun validated(def: RecipeEnchantmentsDef, id: String, resultCount: Int): RecipeEnchantmentsDef {
        // keep_new means "don't transfer" — harmless with any count, nothing to multiply.
        if (resultCount > 1 && def.mode != RecipeEnchantmentsDef.MODE_KEEP_NEW && !def.allowMultiplied) {
            throw IllegalArgumentException(
                "recipe '$id': enchantment transfer with result count $resultCount would copy the " +
                    "ingredients' enchantments onto every crafted item. Set " +
                    "\"allowMultiplied\": true in 'enchantments' if that is really what you want.")
        }
        return def
    }

    /** `sharpness` → `minecraft:sharpness`; already-namespaced ids pass through untouched. */
    private fun normalizeEnchantId(raw: String): String {
        val trimmed = raw.trim().lowercase()
        return if (':' in trimmed) trimmed else "minecraft:$trimmed"
    }

    private fun JsonObject.optStringList(key: String, id: String): List<String> {
        if (!has(key) || get(key).isJsonNull) return emptyList()
        val el = get(key)
        if (el.isJsonPrimitive) return listOf(el.asString)
        if (!el.isJsonArray) throw IllegalArgumentException("recipe '$id': '$key' must be a string or array of strings")
        return el.asJsonArray.map { it.asString }
    }

    private fun JsonObject.optBool(key: String): Boolean? =
        if (has(key) && !get(key).isJsonNull) get(key).asBoolean else null

    private fun parseKey(keyObj: JsonObject?, id: String): Map<Char, String> {
        if (keyObj == null) throw IllegalArgumentException("recipe '$id': shaped recipe needs a 'key'")
        val map = LinkedHashMap<Char, String>()
        for ((symbol, value) in keyObj.entrySet()) {
            if (symbol.length != 1 || symbol == " ")
                throw IllegalArgumentException("recipe '$id': key symbol '$symbol' must be a single non-space character")
            map[symbol[0]] = value.asString
        }
        return map
    }

    private fun JsonObject.optString(key: String): String? =
        if (has(key) && !get(key).isJsonNull) get(key).asString else null

    private fun JsonObject.optInt(key: String): Int? =
        if (has(key) && !get(key).isJsonNull) get(key).asInt else null
}
