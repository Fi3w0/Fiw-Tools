package com.fiw.tools.ability.impl

import com.fiw.tools.ability.Ability
import com.fiw.tools.ability.AbilityContext
import com.fiw.tools.build.FiwItems
import com.fiw.tools.build.ItemBuilder
import com.fiw.tools.config.ItemRegistry
import com.fiw.tools.imbue.ImbueMods
import com.fiw.tools.util.TextParser
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.Registries
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.server.level.ServerPlayer
import net.minecraft.tags.TagKey
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.CustomData
import java.util.concurrent.ThreadLocalRandom

/**
 * The catalyst engine. Right-click while holding the catalyst in main hand:
 *
 *  1. Scans the target (off hand → equipped armor → fails).
 *  2. Verifies the catalyst's `targets` filter matches the candidate stack.
 *  3. Verifies the candidate isn't cursed.
 *  4. Verifies neither cap is hit: the candidate's target-side `imbueLimit` (if defined) **or** the
 *     catalyst's own `maxImbuements` for vanilla bases.
 *  5. Rolls one outcome (weighted random or first-only depending on `rng`).
 *  6. Applies the outcome's `mods` block via [ImbueMods.applyMods] and appends a copy to the target's
 *     `fiw_imbue_log` so the imbuement survives `/fiwtools reload`.
 *  7. Decrements the catalyst's remaining uses; consumes it when exhausted.
 *
 * Every refusal path returns false so the cooldown isn't spent and the catalyst isn't decremented —
 * wasted right-clicks cost nothing. Refusal messages are admin-overridable through `params.messages`.
 */
object ImbueAbility : Ability {
    private val DEFAULT_MESSAGES = mapOf(
        "noTarget" to "&cHold a target item in your off hand, or wear it.",
        "wrongType" to "&cThis catalyst doesn't work on that item.",
        "capReached" to "&6This item refuses further imbuement.",
        "cursed" to "&cCursed items cannot be imbued.",
        "outOfCharges" to "&7This catalyst is spent.",
        "noOutcomes" to "&cThis catalyst has no outcomes configured."
    )

    override fun execute(ctx: AbilityContext): Boolean {
        val params = ctx.params
        val player = ctx.player
        val catalyst = ctx.stack

        // --- 1. Find target stack ---
        val (target, source) = findTarget(player, params) ?: run {
            tell(player, params, "noTarget"); return false
        }

        // --- 2. Curse refuses imbue ---
        if (isCursedStack(target)) { tell(player, params, "cursed"); return false }

        // --- 3. Cap check ---
        val effectiveCap = effectiveCap(target, params)
        val count = ImbueMods.readCount(target)
        if (effectiveCap >= 0 && count >= effectiveCap) {
            tell(player, params, "capReached"); return false
        }

        // --- 4. Roll outcome ---
        val outcomes = params.getAsJsonArray("outcomes") ?: JsonArray()
        if (outcomes.isEmpty()) { tell(player, params, "noOutcomes"); return false }
        val outcome = pickOutcome(outcomes, params.get("rng")?.takeIf { !it.isJsonNull }?.asBoolean ?: false)
            ?: run { tell(player, params, "noOutcomes"); return false }

        // --- 5. Apply mods to target + log ---
        val mods = outcome.getAsJsonObject("mods") ?: JsonObject()
        val attrPrefix = "stack/${ImbueMods.readCount(target)}"
        ImbueMods.applyMods(target, mods, ctx.world.registryAccess(), attrPrefix)
        ImbueMods.appendLog(target, mods)

        // --- 6. Outcome flavor ---
        outcome.get("name")?.takeIf { !it.isJsonNull }?.asString?.let {
            player.displayClientMessage(TextParser.parse(it), false)
        }
        outcome.get("chat")?.takeIf { !it.isJsonNull }?.asString?.let { broadcast ->
            ctx.world.server.playerList.players.forEach { it.sendSystemMessage(TextParser.parse(broadcast)) }
        }

        // --- 7. Charge bookkeeping ---
        consumeCharge(catalyst, params)
        // If the target lived in an armor slot we already mutated it in place. For off-hand, the
        // mutation also went to the actual stack reference (ItemStack is a mutable component holder),
        // so nothing else to write back.
        return true
    }

    // ===== Target scanning =====

    /** Returns the (stack, sourceLabel) of the first slot matching the catalyst's filter. */
    private fun findTarget(player: ServerPlayer, params: JsonObject): Pair<ItemStack, String>? {
        val targetsObj = params.getAsJsonObject("targets") ?: JsonObject()
        val off = player.offhandItem
        if (matchesFilter(off, targetsObj)) return off to "off_hand"
        for (slot in EquipmentSlot.entries) {
            if (!slot.isArmor) continue
            val worn = player.getItemBySlot(slot)
            if (matchesFilter(worn, targetsObj)) return worn to "armor/${slot.name.lowercase()}"
        }
        return null
    }

    /**
     * Matches a stack against the catalyst's `targets` block. Empty filter matches anything (a wide-open
     * catalyst). Each of `items` / `bases` / `tags` independently allows a match; any single hit wins.
     */
    private fun matchesFilter(stack: ItemStack, targets: JsonObject): Boolean {
        if (stack.isEmpty) return false
        if (targets.entrySet().isEmpty()) return true

        targets.getAsJsonArray("items")?.let { arr ->
            val id = FiwItems.fiwId(stack)
            if (id != null && arr.any { it.asString == id }) return true
        }
        targets.getAsJsonArray("bases")?.let { arr ->
            val baseId = BuiltinKey.itemId(stack.item)
            if (baseId != null && arr.any { it.asString == baseId }) return true
        }
        targets.getAsJsonArray("tags")?.let { arr ->
            for (raw in arr) {
                val tag = raw.asString.removePrefix("#")
                val tagId = Identifier.tryParse(tag) ?: continue
                val key: TagKey<Item> = TagKey.create(Registries.ITEM, tagId)
                if (stack.`is`(key)) return true
            }
        }
        return false
    }

    // ===== Cap resolution =====

    /**
     * Effective imbue cap. Returns -1 = unlimited, 0 = locked, N>0 = max imbuements allowed. Target's
     * own `imbueLimit` wins when defined; otherwise the catalyst's `maxImbuements` (default 3) applies.
     */
    private fun effectiveCap(target: ItemStack, params: JsonObject): Int {
        val fiwId = FiwItems.fiwId(target)
        val def = fiwId?.let { ItemRegistry.byId(it) }
        def?.imbueLimit?.let { return it }
        return params.get("maxImbuements")?.takeIf { !it.isJsonNull }?.asInt ?: 3
    }

    private fun isCursedStack(stack: ItemStack): Boolean {
        val data = stack.get(DataComponents.CUSTOM_DATA) ?: return false
        val tag = data.copyTag()
        if (tag.getBoolean(ItemBuilder.UNCURSED_KEY).orElse(false)) return false
        return tag.getBoolean(ItemBuilder.CURSED_KEY).orElse(false)
    }

    // ===== Outcome roll =====

    private fun pickOutcome(outcomes: JsonArray, rng: Boolean): JsonObject? {
        if (!rng) return outcomes.firstOrNull() as? JsonObject
        val weights = outcomes.map { (it as? JsonObject)?.get("weight")?.takeIf { e -> !e.isJsonNull }?.asInt ?: 1 }
        val total = weights.sum()
        if (total <= 0) return outcomes.firstOrNull() as? JsonObject
        var roll = ThreadLocalRandom.current().nextInt(total)
        for ((i, weight) in weights.withIndex()) {
            if (roll < weight) return outcomes[i] as? JsonObject
            roll -= weight
        }
        return outcomes.last() as? JsonObject
    }

    // ===== Charges =====

    /**
     * Catalyst use bookkeeping. `maxUses` from params caps the total charges; `fiw_imbue_uses` on the
     * stack tracks spent ones. When uses hit the cap the catalyst stack shrinks by 1 and the counter
     * resets (so a stack of 3 catalysts each lasts `maxUses` imbues before disappearing).
     */
    private fun consumeCharge(catalyst: ItemStack, params: JsonObject) {
        val maxUses = params.get("maxUses")?.takeIf { !it.isJsonNull }?.asInt ?: 1
        if (maxUses <= 0) return  // 0/negative = unlimited (debug)

        val data = catalyst.get(DataComponents.CUSTOM_DATA) ?: CustomData.EMPTY
        val tag = data.copyTag()
        val used = tag.getInt(ItemBuilder.IMBUE_USES_KEY).orElse(0) + 1
        if (used >= maxUses) {
            tag.remove(ItemBuilder.IMBUE_USES_KEY)
            catalyst.set(DataComponents.CUSTOM_DATA, CustomData.of(tag))
            catalyst.shrink(1)
        } else {
            tag.putInt(ItemBuilder.IMBUE_USES_KEY, used)
            catalyst.set(DataComponents.CUSTOM_DATA, CustomData.of(tag))
        }
    }

    // ===== Messaging =====

    private fun tell(player: ServerPlayer, params: JsonObject, key: String) {
        val messages = params.getAsJsonObject("messages")
        val text = messages?.get(key)?.takeIf { !it.isJsonNull }?.asString ?: DEFAULT_MESSAGES[key] ?: return
        if (text.isEmpty()) return  // admin set it to "" → silent refusal
        player.displayClientMessage(TextParser.parse(text), true)  // actionbar; less spammy than chat
    }

    /** Small helper so we don't import BuiltInRegistries everywhere just to get an item id. */
    private object BuiltinKey {
        fun itemId(item: Item): String? {
            val key = net.minecraft.core.registries.BuiltInRegistries.ITEM.getResourceKey(item).orElse(null) ?: return null
            return key.location().toString()
        }

        // 'location()' renamed to identifier() in 1.21.11 — keep both paths so we don't fight mappings.
        private fun net.minecraft.resources.ResourceKey<*>.location(): Identifier = this.identifier()
    }
}

/** Wired in [com.fiw.tools.ability.AbilityRegistry] under the type id `imbue`. */
