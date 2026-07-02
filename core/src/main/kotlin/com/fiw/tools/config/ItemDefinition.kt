package com.fiw.tools.config

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
    val abilities: List<AbilityDef> = emptyList(),
    val cursed: Boolean = false,
    val curseWhitelist: List<String> = emptyList(),
    val curseSettings: CurseSettings = CurseSettings(),
    /**
     * Max number of times this item can be imbued. `null` = the catalyst's own `maxImbuements`
     * applies (used for vanilla bases or Fiw items that don't care). `0` = item is permanently
     * locked from imbuing. `-1` = unlimited (debug). When non-null, the catalyst's cap is ignored.
     */
    val imbueLimit: Int? = null,
    /**
     * Max souls this item can hold. `null` = item doesn't participate in the soul system.
     * Souls are collected on kill via the `soul_collector` passive and spent by `soul_surge`.
     */
    val soulCapacity: Int? = null,
    /**
     * Tag that groups this item into a named resonance set. When [resonanceRequires] or more items
     * with the same resonanceId are equipped simultaneously, abilities with trigger `"resonance"`
     * on those items are fired by the passive sweep. Null = no set membership.
     */
    val resonanceId: String? = null,
    /** How many items from the set must be equipped to activate resonance. Default 2. */
    val resonanceRequires: Int = 2,
    /**
     * Infinite-use behaviour. `null` = vanilla consumption. Covers anything the game consumes on
     * use: food, potions, throwables, and (via the projectile hook) arrows fired from a bow.
     */
    val infinite: InfiniteDef? = null
) {
    data class InfiniteDef(
        /** `keep`/`normal` = never consumed; `damage` = costs durability per use; `replace` = turns into another item. */
        val mode: String = "keep",
        /** Damage mode: durability lost per use. The item finally breaks when it would exceed maxDamage. */
        val damagePerUse: Int = 1,
        /** Replace mode: item spec the used item turns into — `fiw:<id>` or a vanilla id. */
        val replaceWith: String? = null,
        /** Replace mode: how many of [replaceWith] you get back. */
        val replaceCount: Int = 1
    )

    data class CurseSettings(
        val perTick: Float = 1.0f,
        val ignoreArmor: Boolean = true,
        val ignoreResistance: Boolean = true,
        val checksEnderChest: Boolean = false,
        val sound: String = "minecraft:entity.wither.ambient",
        val particles: String = "minecraft:sculk_soul"
    )

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
        if (cursed) append(", CURSED(wl=").append(curseWhitelist.size).append(')')
        imbueLimit?.let { append(", imbueLimit=").append(it) }
        infinite?.let { append(", infinite=").append(it.mode) }
    }
}
