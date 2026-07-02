package com.fiw.tools.ability

import com.fiw.tools.charge.ChargeHandler
import com.fiw.tools.config.ItemDefinition
import com.fiw.tools.recipe.CraftingHandler
import com.fiw.tools.util.TextParser
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack

/**
 * Use costs for abilities. Every resource in an ability's `cost` block must be affordable
 * *before* the ability runs (all-or-nothing), and is only spent after the ability reports it
 * actually fired — a miss costs nothing, same as cooldowns. Costs never break the item and
 * never kill the payer; the use is refused instead.
 */
object AbilityCosts {
    fun canPay(player: ServerPlayer, stack: ItemStack, def: ItemDefinition, cost: ItemDefinition.CostDef): Boolean {
        if (cost.charges > 0) {
            if (def.charges == null) return false
            if (ChargeHandler.charges(stack) < cost.charges) return false
        }
        if (cost.durability > 0) {
            if (!stack.isDamageableItem) return false
            if (stack.maxDamage - stack.damageValue <= cost.durability) return false
        }
        if (cost.health > 0f && player.health <= cost.health) return false
        if (cost.hunger > 0 && player.foodData.foodLevel < cost.hunger) return false
        if (cost.xpLevels > 0 && player.experienceLevel < cost.xpLevels) return false
        cost.item?.let { spec ->
            if (countMatching(player, spec) < cost.itemCount) return false
        }
        return true
    }

    fun pay(player: ServerPlayer, stack: ItemStack, cost: ItemDefinition.CostDef) {
        if (cost.charges > 0) ChargeHandler.consume(stack, cost.charges)
        if (cost.durability > 0) stack.damageValue = stack.damageValue + cost.durability
        if (cost.health > 0f) player.health = (player.health - cost.health).coerceAtLeast(0.5f)
        if (cost.hunger > 0) player.foodData.foodLevel = (player.foodData.foodLevel - cost.hunger).coerceAtLeast(0)
        if (cost.xpLevels > 0) player.giveExperienceLevels(-cost.xpLevels)
        cost.item?.let { spec -> removeMatching(player, spec, cost.itemCount) }
    }

    /** Action-bar nudge for deliberate uses that failed the cost check. */
    fun notifyInsufficient(player: ServerPlayer) {
        player.sendOverlayMessage(TextParser.parse("&cNot enough resources.")) // 26.x rename
    }

    private fun countMatching(player: ServerPlayer, spec: String): Int {
        var count = 0
        val inv = player.inventory
        for (slot in 0 until inv.containerSize) {
            val s = inv.getItem(slot)
            if (!s.isEmpty && CraftingHandler.ingredientMatches(spec, s)) count += s.count
        }
        return count
    }

    private fun removeMatching(player: ServerPlayer, spec: String, amount: Int) {
        var remaining = amount
        val inv = player.inventory
        for (slot in 0 until inv.containerSize) {
            if (remaining <= 0) return
            val s = inv.getItem(slot)
            if (s.isEmpty || !CraftingHandler.ingredientMatches(spec, s)) continue
            val take = minOf(remaining, s.count)
            s.shrink(take)
            remaining -= take
        }
    }
}
