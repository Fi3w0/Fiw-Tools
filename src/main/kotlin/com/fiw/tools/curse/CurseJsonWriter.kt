package com.fiw.tools.curse

import com.fiw.tools.config.ItemRegistry
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import java.nio.file.Files
import java.nio.file.Path

/**
 * Patches curse-related fields inside an item's JSON in place, preserving every other key verbatim
 * (including user comments-as-fields and hand-edits not yet known to the loader). The whole file is
 * loaded as a [JsonObject], only the touched keys change, then it's written back pretty-printed.
 */
object CurseJsonWriter {
    private val gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()

    /** Find the JSON file backing the given item id by scanning the items dir. Returns null if absent. */
    fun findFile(id: String): Path? {
        val root = ItemRegistry.configRoot
        if (!Files.exists(root)) return null
        Files.walk(root).use { stream ->
            for (path in stream) {
                if (!Files.isRegularFile(path) || !path.toString().endsWith(".json")) continue
                runCatching {
                    val root = JsonParser.parseString(Files.readString(path)).asJsonObject
                    if (root.get("id")?.asString == id) return path
                }
            }
        }
        return null
    }

    fun setCursed(path: Path, cursed: Boolean) = mutate(path) { root ->
        if (cursed) root.addProperty("cursed", true) else root.remove("cursed")
    }

    fun addToWhitelist(path: Path, entry: String) = mutate(path) { root ->
        val list = root.getAsJsonArray("curseWhitelist") ?: JsonArray().also { root.add("curseWhitelist", it) }
        if (list.none { it.asString.equals(entry, ignoreCase = true) }) list.add(JsonPrimitive(entry))
    }

    fun removeFromWhitelist(path: Path, query: String): Boolean {
        var removed = false
        mutate(path) { root ->
            val list = root.getAsJsonArray("curseWhitelist") ?: return@mutate
            val iter = list.iterator()
            while (iter.hasNext()) {
                val v = iter.next().asString
                val pipe = v.indexOf('|')
                val name = if (pipe >= 0) v.substring(0, pipe) else v
                val uuid = if (pipe >= 0) v.substring(pipe + 1) else v
                if (v.equals(query, ignoreCase = true)
                    || name.equals(query, ignoreCase = true)
                    || uuid.equals(query, ignoreCase = true)) {
                    iter.remove()
                    removed = true
                }
            }
            if (list.isEmpty) root.remove("curseWhitelist")
        }
        return removed
    }

    fun listWhitelist(path: Path): List<String> {
        val root = JsonParser.parseString(Files.readString(path)).asJsonObject
        return root.getAsJsonArray("curseWhitelist")?.map { it.asString } ?: emptyList()
    }

    private inline fun mutate(path: Path, patch: (JsonObject) -> Unit) {
        val root = JsonParser.parseString(Files.readString(path)).asJsonObject
        patch(root)
        Files.writeString(path, gson.toJson(root) + "\n")
    }
}
