package com.fiw.tools.imbue

import com.fiw.tools.build.ItemBuilder
import com.fiw.tools.config.ItemDefinition
import com.fiw.tools.util.TextParser
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.minecraft.core.Holder
import net.minecraft.core.HolderLookup
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
import net.minecraft.world.entity.EquipmentSlotGroup
import net.minecraft.world.entity.ai.attributes.Attribute
import net.minecraft.world.entity.ai.attributes.AttributeModifier
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.CustomData
import net.minecraft.world.item.component.ItemAttributeModifiers
import net.minecraft.world.item.component.ItemLore
import net.minecraft.world.item.enchantment.Enchantment
import net.minecraft.world.item.enchantment.ItemEnchantments
import org.slf4j.LoggerFactory

/**
 * Applies an imbuement outcome's `mods` block to an item stack — enchantments, attributes, abilities,
 * lore, display name. The same code path runs in two places:
 *
 *  - **Live** when the player triggers an imbue ability ([ImbueAbility]).
 *  - **Replay** when [com.fiw.tools.sync.ItemSyncHandler] rebuilds a stack from its definition and
 *    needs to re-stamp every imbuement that was ever applied to it (read from `fiw_imbue_log`).
 *
 * The log itself is a JSON array stored as a single NBT String for cheap round-trip. Each entry is a
 * resolved `mods` object — *already-rolled* values, so replay is deterministic and admin reload-safe.
 */
object ImbueMods {
    private val logger = LoggerFactory.getLogger("fiw-tools/imbue")

    /**
     * Apply a single mods block to [stack]. Mutates the stack in place. Safe to call multiple times
     * — each call merges enchantments at max(existing, new), appends attribute modifiers, appends
     * lore lines, and overwrites display name only when [mods] explicitly sets one.
     */
    fun applyMods(stack: ItemStack, mods: JsonObject, registries: HolderLookup.Provider, attrIdPrefix: String) {
        mods.getAsJsonObject("enchantments")?.let { applyEnchantments(stack, it, registries) }
        mods.getAsJsonArray("attributes")?.let { applyAttributes(stack, it, attrIdPrefix) }
        mods.getAsJsonArray("appendLore")?.let { appendLore(stack, it) }
        mods.get("setDisplayName")?.takeIf { it.isJsonPrimitive }?.asString?.let {
            stack.set(DataComponents.CUSTOM_NAME, TextParser.parse(it))
        }
    }

    /** Read the imbuement log (JSON array as NBT string). Empty list when missing. */
    fun readLog(stack: ItemStack): JsonArray {
        val data = stack.get(DataComponents.CUSTOM_DATA) ?: return JsonArray()
        val tag = data.copyTag()
        val raw = tag.getString(ItemBuilder.IMBUE_LOG_KEY).orElse(null) ?: return JsonArray()
        return runCatching { JsonParser.parseString(raw).asJsonArray }.getOrElse { JsonArray() }
    }

    /** Append [entry] to the log, increment `fiw_imbue_count`, write back. */
    fun appendLog(stack: ItemStack, entry: JsonObject) {
        val log = readLog(stack)
        log.add(entry)
        val data = stack.get(DataComponents.CUSTOM_DATA) ?: CustomData.EMPTY
        val tag = data.copyTag()
        tag.putString(ItemBuilder.IMBUE_LOG_KEY, log.toString())
        tag.putInt(ItemBuilder.IMBUE_COUNT_KEY, tag.getInt(ItemBuilder.IMBUE_COUNT_KEY).orElse(0) + 1)
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag))
    }

    /** Read the imbue count from the stack. */
    fun readCount(stack: ItemStack): Int {
        val data = stack.get(DataComponents.CUSTOM_DATA) ?: return 0
        return data.copyTag().getInt(ItemBuilder.IMBUE_COUNT_KEY).orElse(0)
    }

    /** Clear log + count entirely. The next replay will re-stamp nothing. */
    fun clearAll(stack: ItemStack) {
        val data = stack.get(DataComponents.CUSTOM_DATA) ?: return
        val tag = data.copyTag()
        tag.remove(ItemBuilder.IMBUE_LOG_KEY)
        tag.remove(ItemBuilder.IMBUE_COUNT_KEY)
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag))
    }

    /** Set count to zero without touching the log — keeps mods, opens the cap. */
    fun resetCount(stack: ItemStack) {
        val data = stack.get(DataComponents.CUSTOM_DATA) ?: return
        val tag = data.copyTag()
        tag.remove(ItemBuilder.IMBUE_COUNT_KEY)
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag))
    }

    /**
     * Effective ability list for a stack: the definition's abilities plus every `addAbilities` entry
     * in the imbue log. Returns [def] unchanged when no imbuements add abilities — the hot path is
     * usually a single JSON parse with empty result.
     */
    fun effectiveAbilities(stack: ItemStack, def: ItemDefinition): List<ItemDefinition.AbilityDef> {
        val log = readLog(stack)
        if (log.isEmpty) return def.abilities
        val extras = mutableListOf<ItemDefinition.AbilityDef>()
        for (entry in log) {
            val obj = entry as? JsonObject ?: continue
            val arr = obj.getAsJsonArray("addAbilities") ?: continue
            for (abilityEl in arr) {
                val abilityObj = abilityEl as? JsonObject ?: continue
                runCatching { extras.add(parseAbilityDef(abilityObj)) }
                    .onFailure { logger.warn("Bad imbued ability on stack: ${it.message}") }
            }
        }
        return if (extras.isEmpty()) def.abilities else def.abilities + extras
    }

    /** Replay every entry of the imbue log on a freshly-built [stack]. Called by the sync handler. */
    fun replayLog(stack: ItemStack, registries: HolderLookup.Provider) {
        val log = readLog(stack)
        if (log.isEmpty) return
        log.forEachIndexed { index, entry ->
            val obj = entry as? JsonObject ?: return@forEachIndexed
            applyMods(stack, obj, registries, "fiw_tools.imbue.$index")
        }
    }

    // === internals ===

    /**
     * Set the listed enchantments to exactly the listed levels (no max-merge). Bad outcomes genuinely
     * override good ones — that's the price of rolling. Enchants not mentioned in the outcome are
     * untouched.
     */
    private fun applyEnchantments(stack: ItemStack, enchants: JsonObject, registries: HolderLookup.Provider) {
        val lookup = registries.lookupOrThrow(Registries.ENCHANTMENT)
        val mutable = ItemEnchantments.Mutable(stack.get(DataComponents.ENCHANTMENTS) ?: ItemEnchantments.EMPTY)
        for ((rawId, levelEl) in enchants.entrySet()) {
            val id = Identifier.tryParse(rawId) ?: continue
            val key: ResourceKey<Enchantment> = ResourceKey.create(Registries.ENCHANTMENT, id)
            val holder: Holder<Enchantment> = lookup.get(key).orElse(null) ?: continue
            mutable.set(holder, levelEl.asInt)
        }
        stack.set(DataComponents.ENCHANTMENTS, mutable.toImmutable())
    }

    private fun applyAttributes(stack: ItemStack, attrs: JsonArray, idPrefix: String) {
        val existing = stack.get(DataComponents.ATTRIBUTE_MODIFIERS) ?: ItemAttributeModifiers.EMPTY
        val builder = ItemAttributeModifiers.builder()
        for (entry in existing.modifiers) builder.add(entry.attribute, entry.modifier, entry.slot)
        attrs.forEachIndexed { index, el ->
            val o = el as? JsonObject ?: return@forEachIndexed
            val typeId = Identifier.tryParse(o.get("type")?.asString ?: return@forEachIndexed) ?: return@forEachIndexed
            val holder: Holder<Attribute> = BuiltInRegistries.ATTRIBUTE.get(typeId).orElse(null) ?: return@forEachIndexed
            val amount = o.get("amount")?.asDouble ?: 0.0
            val operation = when ((o.get("operation")?.asString ?: "add_value").lowercase()) {
                "add_multiplied_base" -> AttributeModifier.Operation.ADD_MULTIPLIED_BASE
                "add_multiplied_total" -> AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
                else -> AttributeModifier.Operation.ADD_VALUE
            }
            val slot = when ((o.get("slot")?.asString ?: "any").lowercase()) {
                "mainhand" -> EquipmentSlotGroup.MAINHAND
                "offhand" -> EquipmentSlotGroup.OFFHAND
                "hand" -> EquipmentSlotGroup.HAND
                "head" -> EquipmentSlotGroup.HEAD
                "chest" -> EquipmentSlotGroup.CHEST
                "legs" -> EquipmentSlotGroup.LEGS
                "feet" -> EquipmentSlotGroup.FEET
                "armor" -> EquipmentSlotGroup.ARMOR
                else -> EquipmentSlotGroup.ANY
            }
            val modifierId = Identifier.fromNamespaceAndPath("fiw_tools", "${idPrefix.replace(Regex("[^a-z0-9._/-]"), "_")}.$index")
            builder.add(holder, AttributeModifier(modifierId, amount, operation), slot)
        }
        stack.set(DataComponents.ATTRIBUTE_MODIFIERS, builder.build())
    }

    // Abilities take effect via effectiveAbilities(), which reads the log directly — there's nothing
    // to write to the stack here beyond the log entry itself, which appendLog() already stored.

    private fun appendLore(stack: ItemStack, lines: JsonArray) {
        val existing = stack.get(DataComponents.LORE) ?: ItemLore.EMPTY
        val merged = existing.lines.toMutableList()
        for (line in lines) merged.add(TextParser.parse(line.asString))
        stack.set(DataComponents.LORE, ItemLore(merged))
    }

    private fun parseAbilityDef(obj: JsonObject): ItemDefinition.AbilityDef = ItemDefinition.AbilityDef(
        type = obj.get("type")?.asString ?: error("ability missing 'type'"),
        trigger = obj.get("trigger")?.asString ?: "on_right_click",
        cooldownTicks = obj.get("cooldownTicks")?.asInt ?: 0,
        chance = obj.get("chance")?.asFloat ?: 1f,
        params = obj.getAsJsonObject("params") ?: JsonObject()
    )
}
