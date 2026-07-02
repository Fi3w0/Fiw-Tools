package com.fiw.tools.config

import com.google.gson.JsonObject
import com.google.gson.JsonParser

object ItemConfigParser {
    fun parse(text: String): ItemDefinition {
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

        val infiniteEl = root.get("infinite")?.takeIf { !it.isJsonNull }
        val infinite = when {
            infiniteEl == null -> null
            infiniteEl.isJsonPrimitive -> ItemDefinition.InfiniteDef(mode = normalizeInfiniteMode(infiniteEl.asString))
            infiniteEl.isJsonObject -> {
                val o = infiniteEl.asJsonObject
                ItemDefinition.InfiniteDef(
                    mode = normalizeInfiniteMode(o.optString("mode", "keep")),
                    damagePerUse = o.optInt("damagePerUse", 1),
                    replaceWith = o.optStringOrNull("replaceWith"),
                    replaceCount = o.optInt("replaceCount", 1)
                )
            }
            else -> throw IllegalArgumentException("'infinite' must be a mode string or an object")
        }
        if (infinite != null && infinite.mode == "replace" && infinite.replaceWith == null)
            throw IllegalArgumentException("infinite mode 'replace' needs 'replaceWith'")

        val awakeningObj = root.getAsJsonObject("awakening")
        val awakening = if (awakeningObj != null) {
            val trigger = awakeningObj.requireString("trigger").lowercase()
            if (trigger !in setOf("kill_entity", "kill_player", "deal_damage", "visit_dimension"))
                throw IllegalArgumentException("unknown awakening trigger '$trigger'")
            val def = ItemDefinition.AwakeningDef(
                trigger = trigger,
                entity = awakeningObj.optStringOrNull("entity"),
                playerName = awakeningObj.optStringOrNull("playerName"),
                dimension = awakeningObj.optStringOrNull("dimension"),
                count = awakeningObj.optDouble("count", 1.0),
                upgradeTo = awakeningObj.requireString("upgradeTo"),
                message = awakeningObj.optStringOrNull("message"),
                broadcast = awakeningObj.optBool("broadcast", false),
                sound = awakeningObj.optStringOrNull("sound") ?: "minecraft:entity.wither.spawn",
                showProgress = awakeningObj.optBool("showProgress", true)
            )
            if (trigger == "kill_entity" && def.entity == null)
                throw IllegalArgumentException("awakening trigger 'kill_entity' needs 'entity'")
            if (trigger == "visit_dimension" && def.dimension == null)
                throw IllegalArgumentException("awakening trigger 'visit_dimension' needs 'dimension'")
            if (def.count <= 0) throw IllegalArgumentException("awakening 'count' must be > 0")
            def
        } else null

        val bindingObj = root.getAsJsonObject("binding")
        val binding = if (bindingObj != null) {
            val mode = bindingObj.requireString("mode").lowercase()
            if (mode !in setOf("first_use", "first_pickup"))
                throw IllegalArgumentException("unknown binding mode '$mode' (first_use, first_pickup)")
            ItemDefinition.BindingDef(
                mode = mode,
                curse = bindingObj.optBool("curse", false),
                cursePerTick = bindingObj.optFloat("cursePerTick", 1.0f),
                message = bindingObj.optStringOrNull("message"),
                blockUse = bindingObj.optBool("blockUse", true)
            )
        } else null

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
            imbueLimit = root.optIntOrNull("imbueLimit"),
            soulCapacity = root.optIntOrNull("soulCapacity"),
            resonanceId = root.get("resonanceId")?.takeIf { !it.isJsonNull }?.asString,
            resonanceRequires = root.optInt("resonanceRequires", 2),
            infinite = infinite,
            awakening = awakening,
            binding = binding
        )
    }

    /** `normal` is the everyday alias players expect; `keep` is the canonical name. */
    private fun normalizeInfiniteMode(raw: String): String = when (raw.lowercase()) {
        "normal", "keep" -> "keep"
        "damage" -> "damage"
        "replace" -> "replace"
        else -> throw IllegalArgumentException("unknown infinite mode '$raw' (keep/normal, damage, replace)")
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
