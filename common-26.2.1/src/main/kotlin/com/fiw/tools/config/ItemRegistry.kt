package com.fiw.tools.config

import com.fiw.tools.FiwToolsPlatform
import com.fiw.tools.build.ItemBuilder
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicReference

object ItemRegistry {
    private val logger = LoggerFactory.getLogger("fiw-tools/registry")
    private val gson: Gson = GsonBuilder().create()
    private val current = AtomicReference<Map<String, ItemDefinition>>(emptyMap())

    val configRoot: Path
        get() = FiwToolsPlatform.configRoot()

    data class ReloadReport(val loaded: Int, val failed: List<Pair<String, String>>)

    fun loadAll(): ReloadReport {
        val root = configRoot
        if (!Files.exists(root)) {
            Files.createDirectories(root)
        }
        val map = LinkedHashMap<String, ItemDefinition>()
        val failures = mutableListOf<Pair<String, String>>()

        Files.walk(root).use { stream ->
            stream.filter { Files.isRegularFile(it) && it.toString().endsWith(".json") }.forEach { path ->
                try {
                    val text = Files.readString(path)
                    val def = ItemConfigParser.parse(text)
                    if (map.containsKey(def.id)) {
                        failures.add(path.fileName.toString() to "duplicate id '${def.id}'")
                    } else {
                        map[def.id] = def
                    }
                } catch (e: Exception) {
                    failures.add(path.fileName.toString() to (e.message ?: e.javaClass.simpleName))
                    logger.warn("Failed to load ${path.fileName}: ${e.message}")
                }
            }
        }

        current.set(map)
        ItemBuilder.invalidateRevisions()
        logger.info("Loaded ${map.size} item(s); ${failures.size} failed.")
        return ReloadReport(map.size, failures)
    }

    fun byId(id: String): ItemDefinition? = current.get()[id]

    fun all(): Collection<ItemDefinition> = current.get().values

    fun ids(): Set<String> = current.get().keys
}
