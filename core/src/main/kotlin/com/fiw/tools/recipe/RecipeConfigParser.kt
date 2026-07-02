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
                resultItem = resultItem, resultCount = resultCount)
        } else {
            val ingredients = o.getAsJsonArray("ingredients")?.map { it.asString }
                ?: throw IllegalArgumentException("recipe '$id': shapeless recipe needs 'ingredients'")
            if (ingredients.isEmpty() || ingredients.size > 9)
                throw IllegalArgumentException("recipe '$id': shapeless needs 1-9 ingredients")
            RecipeDefinition(id, shaped = false, ingredients = ingredients,
                resultItem = resultItem, resultCount = resultCount)
        }
    }

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
