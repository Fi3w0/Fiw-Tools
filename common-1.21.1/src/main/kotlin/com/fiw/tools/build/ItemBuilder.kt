package com.fiw.tools.build

import com.fiw.tools.config.ItemDefinition
import com.fiw.tools.util.TextParser
import com.google.gson.Gson
import com.google.gson.JsonElement
import net.minecraft.core.Holder
import net.minecraft.core.HolderLookup
import net.minecraft.core.HolderSet
import net.minecraft.core.component.DataComponentType
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.nbt.ByteTag
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.DoubleTag
import net.minecraft.nbt.IntTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.StringTag
import net.minecraft.nbt.Tag
import net.minecraft.resources.ResourceLocation
import net.minecraft.resources.ResourceKey
import net.minecraft.tags.TagKey
import net.minecraft.util.Unit
import net.minecraft.world.entity.EquipmentSlotGroup
import net.minecraft.world.entity.ai.attributes.Attribute
import net.minecraft.world.entity.ai.attributes.AttributeModifier
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation
import net.minecraft.world.food.FoodProperties
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Rarity
import net.minecraft.world.item.component.CustomData
import net.minecraft.world.item.component.ItemAttributeModifiers
import net.minecraft.world.item.component.ItemLore
import net.minecraft.world.item.component.Tool
import net.minecraft.world.item.component.Unbreakable
import net.minecraft.world.item.enchantment.Enchantment
import net.minecraft.world.item.enchantment.ItemEnchantments
import net.minecraft.world.level.block.Block
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.security.MessageDigest
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap

/**
 * Turns an [ItemDefinition] into a live [ItemStack] using only vanilla data components,
 * so the result survives an uninstall and shows identically to vanilla clients.
 *
 * Registries are resolved lazily from the live server's [HolderLookup.Provider] — enchantments,
 * blocks and attributes are only valid once the server is running.
 */
object ItemBuilder {
    private val logger = LoggerFactory.getLogger("fiw-tools/builder")

    /** Reserved custom_data key holding the item's Fiw Tools id, used for ability/lookup resolution. */
    const val FIW_TOOLS_ID_KEY = "fiw_tools_id"

    /** Reserved custom_data key flagging an item to be kept through death. */
    const val KEEP_ON_DEATH_KEY = "fiw_keep_on_death"

    /** Reserved custom_data key holding the definition revision the item was last built from. */
    const val REVISION_KEY = "fiw_tools_rev"

    /**
     * Reserved custom_data key holding the raw config `displayName` last applied. Lets the resync tell
     * an untouched config name from a player anvil-rename, so renames are preserved across config edits.
     */
    const val CONFIG_NAME_KEY = "fiw_cfg_name"

    /**
     * Reserved custom_data key flagging this *specific stack* as exempt from the curse sweep. Set by
     * the uncurse ability or the admin `/fiwtools uncurse_held` command. Authoritative — overrides
     * `cursed: true` in the definition for this stack only, and survives reload/resync.
     */
    const val UNCURSED_KEY = "fiw_uncursed"

    /**
     * Reserved custom_data key set to `1b` on items whose definition currently has `cursed: true`. Lets
     * the curse sweep recognise cursed stacks without resolving the full definition each tick; updated
     * automatically on resync when the JSON toggle flips.
     */
    const val CURSED_KEY = "fiw_cursed"

    /** Reserved custom_data key counting how many times this *specific stack* has been imbued. */
    const val IMBUE_COUNT_KEY = "fiw_imbue_count"

    /**
     * Reserved custom_data key holding the JSON-encoded array of resolved `mods` blocks for every
     * imbuement ever applied to this stack. Replayed on every rebuild so imbuements survive resyncs.
     */
    const val IMBUE_LOG_KEY = "fiw_imbue_log"

    /** Reserved custom_data key counting how many uses a catalyst stack has already spent. */
    const val IMBUE_USES_KEY = "fiw_imbue_uses"

    /**
     * Bumped whenever this builder's output changes in a way that should re-stamp existing items
     * even if their JSON is untouched. Folded into [revision] so a mod update forces a one-time resync.
     */
    private const val BUILDER_VERSION = "1"

    private val revisionGson = Gson()

    // Revisions are computed once per definition and reused, so the periodic inventory sweep is just
    // a map lookup per item rather than a Gson serialize + SHA-256 every pass (no per-tick GC churn).
    // Keyed by item id; cleared by invalidateRevisions on every config reload so edits are picked up.
    private val revisionCache = ConcurrentHashMap<String, String>()

    /**
     * Stable content hash of a definition (plus [BUILDER_VERSION]). Identical across JVMs, server
     * restarts and world swaps for unchanged config; changes when the JSON or the builder changes,
     * which is what [com.fiw.tools.sync.ItemSyncHandler] uses to decide when to rebuild an item.
     */
    fun revision(def: ItemDefinition): String =
        revisionCache.computeIfAbsent(def.id) { computeRevision(def) }

    /** Drop cached revisions. Must be called whenever definitions are reloaded. */
    fun invalidateRevisions() {
        revisionCache.clear()
    }

    private fun computeRevision(def: ItemDefinition): String {
        val canonical = "$BUILDER_VERSION\u0000${revisionGson.toJson(def)}"
        val digest = MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8))
        val sb = StringBuilder(16)
        for (i in 0 until 8) sb.append(HEX[(digest[i].toInt() shr 4) and 0xF]).append(HEX[digest[i].toInt() and 0xF])
        return sb.toString()
    }

    private const val HEX = "0123456789abcdef"

    fun build(def: ItemDefinition, count: Int, registries: HolderLookup.Provider): ItemStack? {
        val baseId = ResourceLocation.tryParse(def.base) ?: run {
            logger.warn("Item '${def.id}': invalid base id '${def.base}'")
            return null
        }
        val item = BuiltInRegistries.ITEM.get(baseId) ?: run {
            logger.warn("Item '${def.id}': unknown base item '${def.base}'")
            return null
        }
        val stack = ItemStack(item, count.coerceIn(1, 99))

        applyIdentity(stack, def)
        applyDurability(stack, def)
        applyVisual(stack, def)
        applyEnchantments(stack, def, registries)
        applyAttributes(stack, def)
        applyFood(stack, def)
        applyChargeable(stack, def)
        applyTool(stack, def, registries)
        applyHideFlags(stack, def)
        applyCurseLocks(stack, def)
        applyCustomData(stack, def)
        // Last: the infinite-use remainder is a copy of the finished stack, so every other
        // component (including custom data) must already be in place.
        com.fiw.tools.infinite.InfiniteItems.applyUseRemainder(stack, def, registries)
        clampCountToMaxStackSize(stack)

        return stack
    }

    private fun clampCountToMaxStackSize(stack: ItemStack) {
        if (stack.count > stack.maxStackSize) stack.count = stack.maxStackSize
    }

    /** For cursed items: remove the ENCHANTABLE component so enchanting tables refuse them. */
    private fun applyCurseLocks(stack: ItemStack, def: ItemDefinition) {
        // DataComponents.ENCHANTABLE was added after 1.21.1.
    }

    private fun applyIdentity(stack: ItemStack, def: ItemDefinition) {
        def.displayName?.let { stack.set(DataComponents.CUSTOM_NAME, TextParser.parse(it)) }
        if (def.lore.isNotEmpty()) {
            stack.set(DataComponents.LORE, ItemLore(def.lore.map { TextParser.parse(it) }))
        }
        def.rarity?.let { raw ->
            val rarity = runCatching { Rarity.valueOf(raw.uppercase()) }.getOrNull()
            if (rarity != null) stack.set(DataComponents.RARITY, rarity)
            else logger.warn("Item '${def.id}': unknown rarity '$raw'")
        }
        def.stackSize?.let { stack.set(DataComponents.MAX_STACK_SIZE, it.coerceIn(1, 99)) }
        def.repairCost?.let { stack.set(DataComponents.REPAIR_COST, it) }
    }

    private fun applyDurability(stack: ItemStack, def: ItemDefinition) {
        if (def.unbreakable == true) stack.set(DataComponents.UNBREAKABLE, Unbreakable(true))
        def.maxDamage?.let { stack.set(DataComponents.MAX_DAMAGE, it) }
        def.damage?.let { stack.set(DataComponents.DAMAGE, it) }
    }

    private fun applyVisual(stack: ItemStack, def: ItemDefinition) {
        def.enchantmentGlint?.let { stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, it) }
        def.potionColor?.let { raw ->
            val color = parseColor(raw) ?: run {
                logger.warn("Item '${def.id}': invalid potionColor '$raw'"); return@let
            }
            // 1.21.1: PotionContents has no customName parameter.
            stack.set(DataComponents.POTION_CONTENTS,
                net.minecraft.world.item.alchemy.PotionContents(Optional.empty(), Optional.of(color), listOf()))
        }
        def.dyedColor?.let { raw ->
            val color = parseColor(raw) ?: run {
                logger.warn("Item '${def.id}': invalid dyedColor '$raw'"); return@let
            }
            stack.set(DataComponents.DYED_COLOR, net.minecraft.world.item.component.DyedItemColor(color, true)) // 1.21.1 ctor
        }
        if (def.itemModel != null) {
            // DataComponents.ITEM_MODEL was added after 1.21.1.
            logger.warn("Item '${def.id}': 'itemModel' is not supported on Minecraft 1.21.1 — ignored")
        }
    }

    /** Accepts `#RRGGBB` hex, a plain decimal int, or bare hex like `FF00FF` as a fallback. */
    private fun parseColor(raw: String): Int? {
        val trimmed = raw.trim()
        if (trimmed.startsWith("#")) return trimmed.substring(1).toLongOrNull(16)?.toInt()
        return trimmed.toLongOrNull()?.toInt() ?: trimmed.toLongOrNull(16)?.toInt()
    }

    private fun applyEnchantments(stack: ItemStack, def: ItemDefinition, registries: HolderLookup.Provider) {
        if (def.enchantments.isEmpty()) return
        val lookup = registries.lookupOrThrow(Registries.ENCHANTMENT)
        val mutable = ItemEnchantments.Mutable(ItemEnchantments.EMPTY)
        for ((rawId, level) in def.enchantments) {
            val id = ResourceLocation.tryParse(rawId) ?: run {
                logger.warn("Item '${def.id}': invalid enchantment id '$rawId'"); continue
            }
            val key: ResourceKey<Enchantment> = ResourceKey.create(Registries.ENCHANTMENT, id)
            val holder: Holder<Enchantment>? = lookup.get(key).orElse(null)
            if (holder == null) {
                logger.warn("Item '${def.id}': unknown enchantment '$rawId'"); continue
            }
            mutable.set(holder, level)
        }
        stack.set(DataComponents.ENCHANTMENTS, mutable.toImmutable())
    }

    private fun applyAttributes(stack: ItemStack, def: ItemDefinition) {
        if (def.attributes.isEmpty()) return
        val builder = ItemAttributeModifiers.builder()
        def.attributes.forEachIndexed { index, attr ->
            val typeId = ResourceLocation.tryParse(attr.type) ?: run {
                logger.warn("Item '${def.id}': invalid attribute id '${attr.type}'"); return@forEachIndexed
            }
            val holder: Holder<Attribute>? = BuiltInRegistries.ATTRIBUTE.getHolder(typeId).orElse(null)
            if (holder == null) {
                logger.warn("Item '${def.id}': unknown attribute '${attr.type}'"); return@forEachIndexed
            }
            val modifierId = attr.id?.let { ResourceLocation.tryParse(it) }
                ?: ResourceLocation.fromNamespaceAndPath("fiw_tools", modifierPath(def.id, attr.type, index))
            val modifier = AttributeModifier(modifierId, attr.amount, parseOperation(attr.operation))
            builder.add(holder, modifier, parseSlot(attr.slot))
        }
        stack.set(DataComponents.ATTRIBUTE_MODIFIERS, builder.build())
    }

    private fun applyChargeable(stack: ItemStack, def: ItemDefinition) {
        val cfg = def.chargeable ?: return
        // 1.21.1 has no consumable component; a never-finishing food gives the hold-to-use
        // behaviour (eat animation) that on_charge_release needs.
        stack.set(DataComponents.FOOD, FoodProperties(0, 0f, true, cfg.maxSeconds,
            java.util.Optional.empty(), emptyList()))
    }

    private fun applyFood(stack: ItemStack, def: ItemDefinition) {
        val food = def.food ?: return
        val builder = FoodProperties.Builder()
            .nutrition(food.nutrition)
            .saturationModifier(food.saturation)
        if (food.canAlwaysEat) builder.alwaysEdible()
        stack.set(DataComponents.FOOD, builder.build())
    }

    private fun applyTool(stack: ItemStack, def: ItemDefinition, registries: HolderLookup.Provider) {
        val tool = def.tool ?: return
        val blockLookup = registries.lookupOrThrow(Registries.BLOCK)
        val rules = tool.rules.mapNotNull { rule ->
            val blocks = resolveBlocks(def.id, rule.blocks, blockLookup) ?: return@mapNotNull null
            Tool.Rule(
                blocks,
                Optional.ofNullable(rule.speed),
                Optional.ofNullable(rule.correctForDrops)
            )
        }
        stack.set(DataComponents.TOOL, Tool(rules, tool.defaultMiningSpeed, tool.damagePerBlock))
    }

    private fun resolveBlocks(
        itemId: String,
        spec: String,
        blockLookup: HolderLookup.RegistryLookup<Block>
    ): HolderSet<Block>? {
        if (spec.startsWith("#")) {
            val tagId = ResourceLocation.tryParse(spec.substring(1)) ?: run {
                logger.warn("Item '$itemId': invalid block tag '$spec'"); return null
            }
            val tag: TagKey<Block> = TagKey.create(Registries.BLOCK, tagId)
            val set = blockLookup.get(tag).orElse(null)
            if (set == null) logger.warn("Item '$itemId': unknown block tag '$spec'")
            return set
        }
        val blockId = ResourceLocation.tryParse(spec) ?: run {
            logger.warn("Item '$itemId': invalid block id '$spec'"); return null
        }
        val holder: Holder<Block>? = BuiltInRegistries.BLOCK.getHolder(blockId).orElse(null)
        if (holder == null) {
            logger.warn("Item '$itemId': unknown block '$spec'"); return null
        }
        return HolderSet.direct(holder)
    }

    private fun applyHideFlags(stack: ItemStack, def: ItemDefinition) {
        if (def.hideFlags.isEmpty()) return
        if (def.hideFlags.any { it.equals("all", ignoreCase = true) }) {
            stack.set(DataComponents.HIDE_TOOLTIP, Unit.INSTANCE)
        } else {
            stack.set(DataComponents.HIDE_ADDITIONAL_TOOLTIP, Unit.INSTANCE)
        }
    }

    private fun applyCustomData(stack: ItemStack, def: ItemDefinition) {
        val tag = CompoundTag()
        def.customData?.let { tag.merge(jsonToTag(it) as CompoundTag) }
        tag.putString(FIW_TOOLS_ID_KEY, def.id)
        tag.putString(REVISION_KEY, revision(def))
        def.displayName?.let { tag.putString(CONFIG_NAME_KEY, it) }
        // Cursed items must always drop on death (lifting the curse via dying would be too cheap),
        // so we ignore keepOnDeath when cursed.
        if (def.keepOnDeath && !def.cursed) tag.putBoolean(KEEP_ON_DEATH_KEY, true)
        def.charges?.let { tag.putInt(com.fiw.tools.charge.ChargeHandler.CHARGES_KEY, it.effectiveStart) }
        if (def.cursed) tag.putBoolean(CURSED_KEY, true)
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag))
    }

    private fun modifierPath(itemId: String, type: String, index: Int): String =
        "${itemId}.${type.lowercase().replace(":", ".")}.$index".replace(Regex("[^a-z0-9._/-]"), "_")

    private fun parseOperation(raw: String): Operation = when (raw.lowercase()) {
        "add_value" -> Operation.ADD_VALUE
        "add_multiplied_base" -> Operation.ADD_MULTIPLIED_BASE
        "add_multiplied_total" -> Operation.ADD_MULTIPLIED_TOTAL
        else -> Operation.ADD_VALUE
    }

    private fun parseSlot(raw: String): EquipmentSlotGroup = when (raw.lowercase()) {
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

    private fun jsonToTag(element: JsonElement): Tag = when {
        element.isJsonObject -> CompoundTag().apply {
            for ((key, value) in element.asJsonObject.entrySet()) put(key, jsonToTag(value))
        }
        element.isJsonArray -> ListTag().apply {
            for (item in element.asJsonArray) add(size, jsonToTag(item))
        }
        element.isJsonPrimitive -> {
            val prim = element.asJsonPrimitive
            when {
                prim.isBoolean -> ByteTag.valueOf(prim.asBoolean)
                prim.isNumber -> {
                    val number = prim.asBigDecimal
                    if (number.scale() <= 0 && number.abs() < BigDecimal(Int.MAX_VALUE)) IntTag.valueOf(number.toInt())
                    else DoubleTag.valueOf(number.toDouble())
                }
                else -> StringTag.valueOf(prim.asString)
            }
        }
        else -> CompoundTag()
    }
}
