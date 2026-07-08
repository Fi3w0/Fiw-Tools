package com.fiw.tools.ability.impl

import com.fiw.tools.ability.Ability
import com.fiw.tools.ability.AbilityContext
import com.fiw.tools.ability.optB
import com.fiw.tools.ability.optD
import com.fiw.tools.ability.optI
import com.fiw.tools.build.FiwItems
import com.fiw.tools.util.TextParser
import com.google.gson.JsonObject
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.resources.Identifier
import net.minecraft.server.level.ServerPlayer
import net.minecraft.tags.TagKey
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack

/**
 * Repair kit (`repair`) — hold the kit and right-click while a damaged item sits in your other hand
 * (or armor). The kit restores that item's durability by a flat `repairAmount` (+ optional
 * `repairPercent` of its max), or fully with `fullRepair`, then is spent: it shrinks by one, or —
 * with `useKitDurability` — burns its own durability so one kit lasts several repairs before it
 * breaks. An optional `targets` filter (items / bases / tags, like a catalyst) limits what it mends.
 * Right-clicks that find nothing to repair cost no cooldown and consume nothing.
 */
object RepairAbility : Ability {
    private val DEFAULT_MESSAGES = mapOf(
        "noTarget" to "&cHold a damaged item in your other hand.",
        "alreadyFull" to "&7That item isn't damaged."
    )

    override fun execute(ctx: AbilityContext): Boolean {
        val p = ctx.params
        val player = ctx.player
        val kit = ctx.stack

        val target = findTarget(player, p, kit) ?: run { tell(player, p, "noTarget"); return false }
        val cur = target.damageValue
        if (cur <= 0) { tell(player, p, "alreadyFull"); return false }

        val amount = if (p.optB("fullRepair", false)) cur
        else (p.optD("repairAmount", 0.0) + p.optD("repairPercent", 0.0) * target.maxDamage).toInt()
        if (amount <= 0) { tell(player, p, "alreadyFull"); return false }

        target.damageValue = (cur - amount).coerceAtLeast(0)
        spendKit(kit, p)
        return true
    }

    /** First damaged, filter-matching item in off hand → main hand → armor, never the kit itself. */
    private fun findTarget(player: ServerPlayer, params: JsonObject, kit: ItemStack): ItemStack? {
        val filter = params.getAsJsonObject("targets")
        val slots = buildList {
            add(player.offhandItem)
            add(player.mainHandItem)
            for (slot in EquipmentSlot.entries) if (slot.isArmor) add(player.getItemBySlot(slot))
        }
        for (s in slots) {
            if (s === kit || s.isEmpty || !s.isDamageableItem || s.damageValue <= 0) continue
            if (filter != null && !matchesFilter(s, filter)) continue
            return s
        }
        return null
    }

    /** Spends the kit on a successful repair: burn its durability if asked, else shrink it by one. */
    private fun spendKit(kit: ItemStack, params: JsonObject) {
        if (!params.optB("consumeSelf", true)) return
        if (params.optB("useKitDurability", false) && kit.isDamageableItem) {
            val perUse = params.optI("kitDurabilityPerUse", 1).coerceAtLeast(1)
            val next = kit.damageValue + perUse
            if (next >= kit.maxDamage) kit.shrink(1) else kit.damageValue = next
        } else {
            kit.shrink(1)
        }
    }

    /** Matches [stack] against a filter block of `items` (Fiw ids) / `bases` (vanilla ids) / `tags`. */
    private fun matchesFilter(stack: ItemStack, targets: JsonObject): Boolean {
        if (targets.entrySet().isEmpty()) return true
        targets.getAsJsonArray("items")?.let { arr ->
            val id = FiwItems.fiwId(stack)
            if (id != null && arr.any { it.asString == id }) return true
        }
        targets.getAsJsonArray("bases")?.let { arr ->
            val baseId = itemId(stack.item)
            if (baseId != null && arr.any { it.asString == baseId }) return true
        }
        targets.getAsJsonArray("tags")?.let { arr ->
            for (raw in arr) {
                val tagId = Identifier.tryParse(raw.asString.removePrefix("#")) ?: continue
                val key: TagKey<Item> = TagKey.create(Registries.ITEM, tagId)
                if (stack.`is`(key)) return true
            }
        }
        return false
    }

    private fun itemId(item: Item): String? =
        BuiltInRegistries.ITEM.getResourceKey(item).orElse(null)?.identifier()?.toString()

    private fun tell(player: ServerPlayer, params: JsonObject, key: String) {
        val messages = params.getAsJsonObject("messages")
        val text = messages?.get(key)?.takeIf { !it.isJsonNull }?.asString ?: DEFAULT_MESSAGES[key] ?: return
        if (text.isEmpty()) return
        player.displayClientMessage(TextParser.parse(text), true)
    }
}
