package com.fiw.tools.recipe

import com.fiw.tools.FiwToolsPlatform
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicReference

/**
 * Holds every custom crafting recipe loaded from `config/fiw_tools/recipes/`. Mirrors
 * [com.fiw.tools.config.ItemRegistry]: hot-reloadable, atomic swap, duplicate ids rejected.
 */
object RecipeRegistry {
    private val logger = LoggerFactory.getLogger("fiw-tools/recipes")
    private val current = AtomicReference<List<RecipeDefinition>>(emptyList())

    val recipesRoot: Path
        get() = FiwToolsPlatform.recipesRoot()

    data class ReloadReport(val loaded: Int, val failed: List<Pair<String, String>>)

    fun loadAll(): ReloadReport {
        val root = recipesRoot
        if (!Files.exists(root)) {
            Files.createDirectories(root)
        }
        val list = mutableListOf<RecipeDefinition>()
        val seenIds = HashSet<String>()
        val failures = mutableListOf<Pair<String, String>>()

        Files.walk(root).use { stream ->
            stream.filter { Files.isRegularFile(it) && it.toString().endsWith(".json") }.forEach { path ->
                val fileName = path.fileName.toString()
                try {
                    val parsed = RecipeConfigParser.parseAll(Files.readString(path), fileName.removeSuffix(".json"))
                    for (recipe in parsed) {
                        if (!seenIds.add(recipe.id)) {
                            failures.add(fileName to "duplicate recipe id '${recipe.id}'")
                        } else {
                            list.add(recipe)
                        }
                    }
                } catch (e: Exception) {
                    failures.add(fileName to (e.message ?: e.javaClass.simpleName))
                    logger.warn("Failed to load $fileName: ${e.message}")
                }
            }
        }

        current.set(list)
        logger.info("Loaded ${list.size} recipe(s); ${failures.size} failed.")
        return ReloadReport(list.size, failures)
    }

    fun all(): List<RecipeDefinition> = current.get()

    fun ids(): List<String> = current.get().map { it.id }
}
