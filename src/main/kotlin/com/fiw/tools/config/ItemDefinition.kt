package com.fiw.tools.config

import com.google.gson.JsonElement
import com.google.gson.JsonObject

/**
 * In-memory representation of one item JSON file.
 * Strings are kept raw so the ItemBuilder can resolve registries lazily on a live server.
 */
data class ItemDefinition(
    val id: String,
    val base: String,
    val displayName: String? = null,
    val lore: List<String> = emptyList(),
    val rarity: String? = null,
    val stackSize: Int? = null,
    val unbreakable: Boolean? = null,
    val maxDamage: Int? = null,
    val damage: Int? = null,
    val enchantmentGlint: Boolean? = null,
    val enchantments: Map<String, Int> = emptyMap(),
    val attributes: List<AttrDef> = emptyList(),
    val keepOnDeath: Boolean = false,
    val hideFlags: List<String> = emptyList(),
    val food: FoodDef? = null,
    val tool: ToolDef? = null,
    val repairCost: Int? = null,
    val customData: JsonObject? = null,
    val abilities: List<AbilityDef> = emptyList()
) {
    data class AttrDef(
        val type: String,
        val slot: String,
        val amount: Double,
        val operation: String,
        val id: String? = null
    )

    data class FoodDef(
        val nutrition: Int = 0,
        val saturation: Float = 0f,
        val canAlwaysEat: Boolean = false,
        val effects: List<EffectDef> = emptyList()
    )

    data class EffectDef(
        val id: String,
        val duration: Int,
        val amplifier: Int = 0,
        val probability: Float = 1f
    )

    data class ToolDef(
        val defaultMiningSpeed: Float = 1f,
        val damagePerBlock: Int = 1,
        val rules: List<ToolRule> = emptyList()
    )

    data class ToolRule(
        val blocks: String,
        val speed: Float? = null,
        val correctForDrops: Boolean? = null
    )

    data class AbilityDef(
        val type: String,
        val trigger: String,
        val cooldownTicks: Int = 0,
        val chance: Float = 1f,
        val params: JsonObject = JsonObject()
    )

    fun describeShort(): String = buildString {
        append("base=").append(base)
        if (displayName != null) append(", name=\"").append(displayName).append('"')
        if (enchantments.isNotEmpty()) append(", enchants=").append(enchantments.size)
        if (attributes.isNotEmpty()) append(", attrs=").append(attributes.size)
        if (abilities.isNotEmpty()) append(", abilities=").append(abilities.size)
        if (keepOnDeath) append(", keepOnDeath")
    }
}
