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
    val infinite: InfiniteDef? = null,
    /**
     * Kill reward that recharges ability cooldowns and/or repairs the killing stack.
     * Target filters support any living entity, non-player mobs, players, or specific entity ids.
     */
    val rechargeOnKill: RechargeOnKillDef? = null,
    /**
     * Awakening — the artifact upgrades itself into [AwakeningDef.upgradeTo] when its condition is
     * met. Chain awakenings by giving the upgraded item its own `awakening` block. `null` = never.
     */
    val awakening: AwakeningDef? = null,
    /**
     * Binding — the artifact belongs to the first player who uses it (`first_use`) or picks it up
     * (`first_pickup`). Non-owners can't trigger its abilities, and with `curse: true` they take
     * damage every second just for carrying it. `null` = unbound, anyone can use it.
     */
    val binding: BindingDef? = null,
    /**
     * Universal charge pool stored per stack. Abilities spend charges via their `cost` block;
     * charges regenerate over time and can be granted by `rechargeOnKill`. `null` = no pool.
     */
    val charges: ChargesDef? = null,
    /**
     * Extra lore lines refreshed live with placeholders: `{owner}`, `{charges}`, `{max_charges}`,
     * `{souls}`, `{soul_capacity}`, `{awakening_stage}`, `{awakening_progress}`, `{awakening_count}`.
     */
    val dynamicLore: List<String> = emptyList()
) {
    data class ChargesDef(
        val max: Int,
        /** Charges a freshly built stack starts with. -1 (default) = full. */
        val start: Int = -1,
        /** Ticks between natural regens. 0 = no natural regen. */
        val regenTicks: Int = 0,
        /** Charges restored per natural regen. */
        val regenAmount: Int = 1
    ) {
        val effectiveStart: Int get() = if (start < 0) max else start.coerceAtMost(max)
    }

    data class CostDef(
        /** Charges consumed from the item's [ChargesDef] pool. */
        val charges: Int = 0,
        /** Durability consumed. Never breaks the item — the use is refused instead. */
        val durability: Int = 0,
        /** Health paid. Never lethal — the use is refused when it would drop the player to 0. */
        val health: Float = 0f,
        /** Hunger points consumed. */
        val hunger: Int = 0,
        /** XP levels consumed. */
        val xpLevels: Int = 0,
        /** Item consumed from the inventory: `fiw:<id>`, a vanilla id, or `#tag`. */
        val item: String? = null,
        val itemCount: Int = 1
    ) {
        val isFree: Boolean
            get() = charges == 0 && durability == 0 && health == 0f && hunger == 0 && xpLevels == 0 && item == null
    }

    data class RechargeOnKillDef(
        /** `main`, `off`, `both`, or `any`. Controls which carried copy can trigger from a player kill. */
        val hand: String = "main",
        /** `any`, `mobs`, `players`, or `entities`. */
        val target: String = "any",
        /** Specific entity type ids to match when target is `entities` or as an extra filter. */
        val entities: List<String> = emptyList(),
        /** Reduce active cooldowns on this item by this many ticks. */
        val cooldownTicks: Int = 0,
        /** Reduce active cooldowns by this fraction of their remaining time, e.g. 0.25 = 25%. */
        val cooldownPercent: Float = 0f,
        /** Optional ability type filter for cooldown recharge. Empty = every ability on this item. */
        val cooldownAbilityTypes: List<String> = emptyList(),
        /** Optional trigger filter for cooldown recharge. Empty = every trigger on this item. */
        val cooldownTriggers: List<String> = emptyList(),
        /** Clear all active cooldowns on this item. Applied before [cooldownTicks]. */
        val resetCooldowns: Boolean = false,
        /** Reduce the stack's current damage by this flat amount. */
        val repairDamage: Int = 0,
        /** Reduce current damage by this fraction of max durability, e.g. 0.10 = 10%. */
        val repairPercent: Float = 0f,
        /** Fully repair the stack on a matching kill. */
        val fullRepair: Boolean = false,
        /** Increase the stack's max durability by this flat amount on each matching kill. */
        val addMaxDamage: Int = 0,
        /** Optional cap for [addMaxDamage]. Null = no cap. */
        val maxDamageCap: Int? = null,
        /** Stamp the stack as unbreakable on a matching kill. */
        val unbreakable: Boolean = false,
        /** Random chance to apply after the target filter passes. */
        val chance: Float = 1f,
        /** Specific player names or UUIDs to match. Empty = any player when target allows players. */
        val players: List<String> = emptyList(),
        /** Charges granted to the item's [ChargesDef] pool on a matching kill. */
        val charges: Int = 0
    )

    data class BindingDef(
        /** `first_use` (right-click or attack) or `first_pickup` (first inventory it lands in). */
        val mode: String,
        /** Curse non-owners: carrying someone else's artifact hurts. */
        val curse: Boolean = false,
        /** Curse damage per second while a non-owner carries it. */
        val cursePerTick: Float = 1.0f,
        /** Message shown to the player the moment the artifact binds to them (text codes). */
        val message: String? = null,
        /** Block ability use by non-owners. On by default — that's the point of binding. */
        val blockUse: Boolean = true
    )

    data class AwakeningDef(
        /** `kill_entity`, `kill_player`, `deal_damage`, or `visit_dimension`. */
        val trigger: String,
        /** kill_entity: entity type id, e.g. `minecraft:wither` (bosses are just entity ids). */
        val entity: String? = null,
        /** kill_player: restrict to a specific victim (name or UUID). Null = any player counts. */
        val playerName: String? = null,
        /** visit_dimension: dimension id, e.g. `minecraft:the_end`. */
        val dimension: String? = null,
        /** Kills needed / total damage to deal. Ignored by visit_dimension. */
        val count: Double = 1.0,
        /** Fiw item id this artifact transforms into. */
        val upgradeTo: String,
        /** Message shown on awakening (text codes supported). */
        val message: String? = null,
        /** Broadcast [message] to the whole server instead of just the holder. */
        val broadcast: Boolean = false,
        val sound: String? = "minecraft:entity.wither.spawn",
        /** Action-bar progress (e.g. `Awakening 3/10`) each time progress is made. */
        val showProgress: Boolean = true
    )

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
        /**
         * Optional shared cooldown group. Abilities with the same group name share one per-player
         * cooldown — across items too — so stacking several burst items can't chain-cast them.
         */
        val cooldownGroup: String? = null,
        /** Resources consumed per successful fire. Checked before execution; unpaid = no fire. */
        val cost: CostDef? = null,
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
        rechargeOnKill?.let { append(", rechargeOnKill=").append(it.target).append('@').append(it.hand) }
    }
}
