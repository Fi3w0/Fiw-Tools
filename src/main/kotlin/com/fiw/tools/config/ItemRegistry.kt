package com.fiw.tools.config

import com.fiw.tools.build.ItemBuilder
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.fabricmc.loader.api.FabricLoader
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicReference

object ItemRegistry {
    private val logger = LoggerFactory.getLogger("fiw-tools/registry")
    private val gson: Gson = GsonBuilder().create()
    private val current = AtomicReference<Map<String, ItemDefinition>>(emptyMap())

    val configRoot: Path
        get() = FabricLoader.getInstance().configDir.resolve("fiw_tools").resolve("items")

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
                    val def = parse(text)
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

    private fun parse(text: String): ItemDefinition {
        val root = JsonParser.parseString(text).asJsonObject
        val id = root.requireString("id")
        val base = root.requireString("base")

        val attributes = root.getAsJsonArray("attributes")?.map { e ->
            val o = e.asJsonObject
            ItemDefinition.AttrDef(
                type = o.requireString("type"),
                slot = o.optString("slot", "any"),
                amount = o.optDouble("amount", 0.0),
                operation = o.optString("operation", "add_value"),
                id = o.optStringOrNull("id")
            )
        } ?: emptyList()

        val enchants = root.getAsJsonObject("enchantments")?.entrySet()?.associate {
            it.key to it.value.asInt
        } ?: emptyMap()

        val foodObj = root.getAsJsonObject("food")
        val food = if (foodObj != null) {
            ItemDefinition.FoodDef(
                nutrition = foodObj.optInt("nutrition", 0),
                saturation = foodObj.optFloat("saturation", 0f),
                canAlwaysEat = foodObj.optBool("canAlwaysEat", false),
                effects = foodObj.getAsJsonArray("effects")?.map { e ->
                    val o = e.asJsonObject
                    ItemDefinition.EffectDef(
                        id = o.requireString("id"),
                        duration = o.optInt("duration", 100),
                        amplifier = o.optInt("amplifier", 0),
                        probability = o.optFloat("probability", 1f)
                    )
                } ?: emptyList()
            )
        } else null

        val toolObj = root.getAsJsonObject("tool")
        val tool = if (toolObj != null) {
            ItemDefinition.ToolDef(
                defaultMiningSpeed = toolObj.optFloat("defaultMiningSpeed", 1f),
                damagePerBlock = toolObj.optInt("damagePerBlock", 1),
                rules = toolObj.getAsJsonArray("rules")?.map { e ->
                    val o = e.asJsonObject
                    ItemDefinition.ToolRule(
                        blocks = o.requireString("blocks"),
                        speed = o.optFloatOrNull("speed"),
                        correctForDrops = o.optBoolOrNull("correctForDrops")
                    )
                } ?: emptyList()
            )
        } else null

        val abilities = root.getAsJsonArray("abilities")?.map { e ->
            val o = e.asJsonObject
            ItemDefinition.AbilityDef(
                type = o.requireString("type"),
                trigger = o.optString("trigger", "on_right_click"),
                cooldownTicks = o.optInt("cooldownTicks", 0),
                chance = o.optFloat("chance", 1f),
                params = o.getAsJsonObject("params") ?: JsonObject()
            )
        } ?: emptyList()

        val hideFlags = root.getAsJsonArray("hideFlags")?.map { it.asString } ?: emptyList()
        val lore = root.getAsJsonArray("lore")?.map { it.asString } ?: emptyList()

        val curseWhitelist = root.getAsJsonArray("curseWhitelist")?.map { it.asString } ?: emptyList()
        val curseObj = root.getAsJsonObject("curseSettings")
        val curseSettings = if (curseObj != null) {
            ItemDefinition.CurseSettings(
                perTick = curseObj.optFloat("perTick", 1.0f),
                ignoreArmor = curseObj.optBool("ignoreArmor", true),
                ignoreResistance = curseObj.optBool("ignoreResistance", true),
                checksEnderChest = curseObj.optBool("checksEnderChest", false),
                sound = curseObj.optString("sound", "minecraft:entity.wither.ambient"),
                particles = curseObj.optString("particles", "minecraft:sculk_soul")
            )
        } else ItemDefinition.CurseSettings()

        return ItemDefinition(
            id = id,
            base = base,
            displayName = root.optStringOrNull("displayName"),
            lore = lore,
            rarity = root.optStringOrNull("rarity"),
            stackSize = root.optIntOrNull("stackSize"),
            unbreakable = root.optBoolOrNull("unbreakable"),
            maxDamage = root.optIntOrNull("maxDamage"),
            damage = root.optIntOrNull("damage"),
            enchantmentGlint = root.optBoolOrNull("enchantmentGlint"),
            enchantments = enchants,
            attributes = attributes,
            keepOnDeath = root.optBool("keepOnDeath", false),
            hideFlags = hideFlags,
            food = food,
            tool = tool,
            repairCost = root.optIntOrNull("repairCost"),
            customData = root.getAsJsonObject("customData"),
            abilities = abilities,
            cursed = root.optBool("cursed", false),
            curseWhitelist = curseWhitelist,
            curseSettings = curseSettings,
            imbueLimit = root.optIntOrNull("imbueLimit")
        )
    }

    private fun JsonObject.requireString(key: String): String =
        get(key)?.asString ?: throw IllegalArgumentException("missing required field '$key'")

    private fun JsonObject.optString(key: String, default: String): String =
        if (has(key) && !get(key).isJsonNull) get(key).asString else default

    private fun JsonObject.optStringOrNull(key: String): String? =
        if (has(key) && !get(key).isJsonNull) get(key).asString else null

    private fun JsonObject.optInt(key: String, default: Int): Int =
        if (has(key) && !get(key).isJsonNull) get(key).asInt else default

    private fun JsonObject.optIntOrNull(key: String): Int? =
        if (has(key) && !get(key).isJsonNull) get(key).asInt else null

    private fun JsonObject.optFloat(key: String, default: Float): Float =
        if (has(key) && !get(key).isJsonNull) get(key).asFloat else default

    private fun JsonObject.optFloatOrNull(key: String): Float? =
        if (has(key) && !get(key).isJsonNull) get(key).asFloat else null

    private fun JsonObject.optDouble(key: String, default: Double): Double =
        if (has(key) && !get(key).isJsonNull) get(key).asDouble else default

    private fun JsonObject.optBool(key: String, default: Boolean): Boolean =
        if (has(key) && !get(key).isJsonNull) get(key).asBoolean else default

    private fun JsonObject.optBoolOrNull(key: String): Boolean? =
        if (has(key) && !get(key).isJsonNull) get(key).asBoolean else null
}
