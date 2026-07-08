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
                cooldownGroup = o.optStringOrNull("cooldownGroup"),
                procGroup = o.optStringOrNull("procGroup"),
                cost = parseCost(o.getAsJsonObject("cost")),
                stance = o.optStringOrNull("stance"),
                scaling = parseScaling(o.getAsJsonArray("scaling")),
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
        if (infinite != null && infinite.mode == "damage") {
            if (infinite.damagePerUse < 1) throw IllegalArgumentException("infinite damagePerUse must be >= 1")
        }
        if (infinite != null && infinite.mode == "replace") {
            if (infinite.replaceCount < 1) throw IllegalArgumentException("infinite replaceCount must be >= 1")
        }

        val rechargeObj = root.getAsJsonObject("rechargeOnKill")
        val rechargeOnKill = if (rechargeObj != null) {
            val rawEntity = rechargeObj.optStringOrNull("entity")
            val entityList = buildList {
                rawEntity?.takeUnless { isRechargeTargetAlias(it) }?.let { add(it) }
                rechargeObj.getAsJsonArray("entities")?.forEach { add(it.asString) }
            }
            val target = rechargeObj.optStringOrNull("target")
                ?: rawEntity?.takeIf { isRechargeTargetAlias(it) }
                ?: if (entityList.isEmpty()) "any" else "entities"
            val targetKey = target.lowercase()
            val hand = normalizeRechargeHand(rechargeObj.optStringOrNull("slot") ?: rechargeObj.optString("hand", "main"))
            if (hand !in setOf("main", "off", "both", "any"))
                throw IllegalArgumentException("unknown rechargeOnKill hand/slot '$hand'")
            if (targetKey !in setOf("any", "all", "mobs", "mob", "players", "player", "entities", "entity", "specific"))
                throw IllegalArgumentException("unknown rechargeOnKill target '$target'")
            if (targetKey in setOf("entities", "entity", "specific") && entityList.isEmpty())
                throw IllegalArgumentException("rechargeOnKill target '$target' needs 'entity' or 'entities'")
            val def = ItemDefinition.RechargeOnKillDef(
                hand = hand,
                target = normalizeRechargeTarget(targetKey),
                entities = entityList,
                cooldownTicks = rechargeObj.optInt("cooldownTicks", rechargeObj.optInt("cooldownReductionTicks", 0)),
                cooldownPercent = rechargeObj.optFloat("cooldownPercent", 0f),
                cooldownAbilityTypes = rechargeObj.stringList("cooldownAbilities") + rechargeObj.stringList("abilityTypes"),
                cooldownTriggers = rechargeObj.stringList("cooldownTriggers") + rechargeObj.stringList("triggers"),
                resetCooldowns = rechargeObj.optBool("resetCooldowns", false),
                repairDamage = rechargeObj.optInt("repairDamage", rechargeObj.optInt("repairAmount", rechargeObj.optInt("repair", 0))),
                repairPercent = rechargeObj.optFloat("repairPercent", 0f),
                fullRepair = rechargeObj.optBool("fullRepair", rechargeObj.optBool("repairToFull", false)),
                addMaxDamage = rechargeObj.optInt("addMaxDamage", rechargeObj.optInt("addDurability", 0)),
                maxDamageCap = rechargeObj.optIntOrNull("maxDamageCap"),
                unbreakable = rechargeObj.optBool("unbreakable", rechargeObj.optBool("makeUnbreakable", false)),
                chance = rechargeObj.optFloat("chance", 1f),
                charges = rechargeObj.optInt("charges", rechargeObj.optInt("addCharges", 0)),
                players = rechargeObj.stringList("players") + rechargeObj.stringList("playerNames") + rechargeObj.optStringOrNull("player").let { if (it == null) emptyList() else listOf(it) }
            )
            if (def.cooldownTicks < 0) throw IllegalArgumentException("rechargeOnKill cooldownTicks must be >= 0")
            if (def.cooldownPercent !in 0f..1f) throw IllegalArgumentException("rechargeOnKill cooldownPercent must be between 0 and 1")
            if (def.repairDamage < 0) throw IllegalArgumentException("rechargeOnKill repairDamage must be >= 0")
            if (def.repairPercent < 0f) throw IllegalArgumentException("rechargeOnKill repairPercent must be >= 0")
            if (def.addMaxDamage < 0) throw IllegalArgumentException("rechargeOnKill addMaxDamage must be >= 0")
            if (def.maxDamageCap != null && def.maxDamageCap < 1) throw IllegalArgumentException("rechargeOnKill maxDamageCap must be >= 1")
            if (def.chance !in 0f..1f) throw IllegalArgumentException("rechargeOnKill chance must be between 0 and 1")
            if (def.charges < 0) throw IllegalArgumentException("rechargeOnKill charges must be >= 0")
            // A players filter forces the victim to be a player, so it can never match target "mobs".
            if (def.target == "mobs" && def.players.isNotEmpty())
                throw IllegalArgumentException("rechargeOnKill target 'mobs' cannot be combined with a 'players' filter")
            def
        } else null

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

        val chargesEl = root.get("charges")?.takeIf { !it.isJsonNull }
        val charges = when {
            chargesEl == null -> null
            chargesEl.isJsonPrimitive -> ItemDefinition.ChargesDef(max = chargesEl.asInt)
            chargesEl.isJsonObject -> {
                val o = chargesEl.asJsonObject
                ItemDefinition.ChargesDef(
                    max = o.optInt("max", 1),
                    start = o.optInt("start", -1),
                    regenTicks = o.optInt("regenTicks", 0),
                    regenAmount = o.optInt("regenAmount", 1)
                )
            }
            else -> throw IllegalArgumentException("'charges' must be a max number or an object")
        }
        if (charges != null) {
            if (charges.max < 1) throw IllegalArgumentException("charges max must be >= 1")
            if (charges.regenTicks < 0) throw IllegalArgumentException("charges regenTicks must be >= 0")
            if (charges.regenAmount < 1) throw IllegalArgumentException("charges regenAmount must be >= 1")
            if (charges.start > charges.max) throw IllegalArgumentException("charges start cannot exceed max")
        }

        val dynamicLore = root.getAsJsonArray("dynamicLore")?.map { it.asString } ?: emptyList()

        val killstreakObj = root.get("killstreak")?.takeIf { !it.isJsonNull }?.let {
            if (!it.isJsonObject) throw IllegalArgumentException("'killstreak' must be an object")
            it.asJsonObject
        }
        val killstreak = killstreakObj?.let { o ->
            val def = ItemDefinition.KillstreakDef(
                killsPerTier = o.optInt("killsPerTier", 3),
                maxTier = o.optInt("maxTier", 2),
                buffs = o.getAsJsonArray("buffs")?.map { it.asString } ?: listOf("strength"),
                buffDuration = o.optInt("buffDuration", 200),
                resetOnDeath = o.optBool("resetOnDeath", true),
                tierMessage = o.optStringOrNull("tierMessage")
            )
            if (def.killsPerTier < 1) throw IllegalArgumentException("killstreak killsPerTier must be >= 1")
            if (def.maxTier < 1) throw IllegalArgumentException("killstreak maxTier must be >= 1")
            def
        }

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

        val stackSize = root.optIntOrNull("stackSize")
        if (stackSize != null && stackSize !in 1..99) throw IllegalArgumentException("stackSize must be between 1 and 99")
        val imbueLimit = root.optIntOrNull("imbueLimit")
        if (imbueLimit != null && imbueLimit < -1) throw IllegalArgumentException("imbueLimit must be -1 or greater")
        val soulCapacity = root.optIntOrNull("soulCapacity")
        if (soulCapacity != null && soulCapacity < 1) throw IllegalArgumentException("soulCapacity must be >= 1")
        val resonanceRequires = root.optInt("resonanceRequires", 2)
        if (resonanceRequires < 1) throw IllegalArgumentException("resonanceRequires must be >= 1")

        return ItemDefinition(
            id = id,
            base = base,
            displayName = root.optStringOrNull("displayName"),
            lore = lore,
            rarity = root.optStringOrNull("rarity"),
            stackSize = stackSize,
            unbreakable = root.optBoolOrNull("unbreakable"),
            maxDamage = root.optIntOrNull("maxDamage"),
            damage = root.optIntOrNull("damage"),
            enchantmentGlint = root.optBoolOrNull("enchantmentGlint"),
            potionColor = root.optStringOrNull("potionColor"),
            dyedColor = root.optStringOrNull("dyedColor"),
            itemModel = root.optStringOrNull("itemModel"),
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
            imbueLimit = imbueLimit,
            soulCapacity = soulCapacity,
            resonanceId = root.get("resonanceId")?.takeIf { !it.isJsonNull }?.asString,
            resonanceRequires = resonanceRequires,
            infinite = infinite,
            rechargeOnKill = rechargeOnKill,
            awakening = awakening,
            binding = binding,
            charges = charges,
            dynamicLore = dynamicLore,
            killstreak = killstreak,
            stances = root.getAsJsonArray("stances")?.map { it.asString } ?: emptyList(),
            chargeable = root.get("chargeable")?.takeIf { !it.isJsonNull }?.let { el ->
                when {
                    el.isJsonPrimitive && el.asJsonPrimitive.isBoolean ->
                        if (el.asBoolean) ItemDefinition.ChargeableDef() else null
                    el.isJsonObject -> {
                        val o = el.asJsonObject
                        ItemDefinition.ChargeableDef(
                            animation = o.optString("animation", "bow"),
                            maxSeconds = o.optFloat("maxSeconds", 3600f)
                        )
                    }
                    else -> throw IllegalArgumentException("'chargeable' must be true or an object")
                }
            },
            enchanting = root.get("enchanting")?.takeIf { !it.isJsonNull }?.let { el ->
                if (!el.isJsonObject) throw IllegalArgumentException("'enchanting' must be an object")
                val o = el.asJsonObject
                ItemDefinition.EnchantingDef(
                    table = o.optBoolOrNull("table") ?: o.optBoolOrNull("enchantingTable"),
                    anvil = o.optBoolOrNull("anvil"),
                    grindstone = o.optBoolOrNull("grindstone"),
                    smithing = o.optBoolOrNull("smithing"),
                    allowRename = o.optBoolOrNull("allowRename") ?: o.optBoolOrNull("rename"),
                    allowEnchant = o.optBoolOrNull("allowEnchant") ?: o.optBoolOrNull("enchant")
                )
            }
        )
    }

    /** Public so imbued `addAbilities` blocks parse costs identically to normal abilities. */
    fun parseCost(o: JsonObject?): ItemDefinition.CostDef? {
        if (o == null) return null
        val cost = ItemDefinition.CostDef(
            charges = o.optInt("charges", 0),
            durability = o.optInt("durability", 0),
            health = o.optFloat("health", 0f),
            maxHealth = o.optFloat("maxHealth", 0f),
            hunger = o.optInt("hunger", 0),
            xpLevels = o.optInt("xpLevels", o.optInt("levels", 0)),
            item = o.optStringOrNull("item"),
            itemCount = o.optInt("itemCount", o.optInt("count", 1))
        )
        if (cost.charges < 0 || cost.durability < 0 || cost.health < 0f || cost.maxHealth < 0f || cost.hunger < 0 || cost.xpLevels < 0 || cost.itemCount < 1)
            throw IllegalArgumentException("ability cost values must be >= 0 (itemCount >= 1)")
        return if (cost.isFree) null else cost
    }

    /** Recognised scaling stats — validated at parse time so typos fail loud, not silently. */
    private val SCALING_STATS = setOf("streak_tier", "imbue_count", "charges", "max_health")

    /** Public so imbued `addAbilities` blocks parse scaling identically to normal abilities. */
    fun parseScaling(arr: com.google.gson.JsonArray?): List<ItemDefinition.ScalingDef> {
        if (arr == null) return emptyList()
        return arr.map { el ->
            val o = el.asJsonObject
            val stat = o.requireString("stat").lowercase()
            if (stat !in SCALING_STATS)
                throw IllegalArgumentException("unknown scaling stat '$stat' (${SCALING_STATS.joinToString("/")})")
            ItemDefinition.ScalingDef(
                stat = stat,
                param = o.requireString("param"),
                perPoint = o.optDouble("perPoint", 0.0),
                cap = o.optDoubleOrNull("cap")
            )
        }
    }

    /** `normal` is the everyday alias players expect; `keep` is the canonical name. */
    private fun normalizeInfiniteMode(raw: String): String = when (raw.lowercase()) {
        "normal", "keep" -> "keep"
        "damage" -> "damage"
        "replace" -> "replace"
        else -> throw IllegalArgumentException("unknown infinite mode '$raw' (keep/normal, damage, replace)")
    }

    private fun normalizeRechargeTarget(raw: String): String = when (raw.lowercase()) {
        "all", "any" -> "any"
        "mob", "mobs" -> "mobs"
        "player", "players" -> "players"
        "entity", "entities", "specific" -> "entities"
        else -> raw.lowercase()
    }

    private fun isRechargeTargetAlias(raw: String): Boolean =
        raw.lowercase() in setOf("all", "any", "mob", "mobs", "player", "players")

    private fun normalizeRechargeHand(raw: String): String = when (raw.lowercase()) {
        "main", "mainhand", "main_hand" -> "main"
        "off", "offhand", "off_hand" -> "off"
        "both", "hands", "either" -> "both"
        "any", "all", "inventory", "inv", "carried" -> "any"
        else -> raw.lowercase()
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

    private fun JsonObject.optDoubleOrNull(key: String): Double? =
        if (has(key) && !get(key).isJsonNull) get(key).asDouble else null

    private fun JsonObject.optBool(key: String, default: Boolean): Boolean =
        if (has(key) && !get(key).isJsonNull) get(key).asBoolean else default

    private fun JsonObject.optBoolOrNull(key: String): Boolean? =
        if (has(key) && !get(key).isJsonNull) get(key).asBoolean else null

    private fun JsonObject.stringList(key: String): List<String> {
        val el = get(key)?.takeIf { !it.isJsonNull } ?: return emptyList()
        return when {
            el.isJsonArray -> el.asJsonArray.map { it.asString }
            el.isJsonPrimitive -> listOf(el.asString)
            else -> emptyList()
        }
    }
}
