package com.fiw.tools.lore

import com.fiw.tools.awaken.AwakeningHandler
import com.fiw.tools.bind.BindingHandler
import com.fiw.tools.build.FiwItems
import com.fiw.tools.charge.ChargeHandler
import com.fiw.tools.config.ItemDefinition
import com.fiw.tools.config.ItemRegistry
import com.fiw.tools.soul.SoulHandler
import com.fiw.tools.util.TextParser
import net.minecraft.core.component.DataComponents
import net.minecraft.server.MinecraftServer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.ItemLore

/**
 * Live lore. Items with a `dynamicLore` block get those lines appended below the static lore,
 * refreshed once a second with the stack's real state:
 *
 * `{owner}` · `{charges}` `{max_charges}` · `{souls}` `{soul_capacity}` ·
 * `{awakening_stage}` `{awakening_progress}` `{awakening_count}` ·
 * `{durability}` `{max_durability}`
 *
 * The LORE component is only rewritten when the resolved text actually changed, so an idle
 * item costs one comparison per sweep and no resync.
 */
object DynamicLoreHandler {
    private const val SWEEP_TICKS = 20
    private var counter = 0

    fun tick(server: MinecraftServer) {
        if (++counter < SWEEP_TICKS) return
        counter = 0
        for (player in server.playerList.players) {
            val inv = player.inventory
            for (slot in 0 until inv.containerSize) {
                val stack = inv.getItem(slot)
                if (stack.isEmpty) continue
                val id = FiwItems.fiwId(stack) ?: continue
                val def = ItemRegistry.byId(id) ?: continue
                if (def.dynamicLore.isEmpty()) continue
                refresh(stack, def)
            }
        }
    }

    private fun refresh(stack: ItemStack, def: ItemDefinition) {
        val lines = buildList {
            def.lore.forEach { add(TextParser.parse(it)) }
            def.dynamicLore.forEach { add(TextParser.parse(resolve(it, stack, def))) }
        }
        val lore = ItemLore(lines)
        if (stack.get(DataComponents.LORE) == lore) return
        stack.set(DataComponents.LORE, lore)
    }

    private fun resolve(template: String, stack: ItemStack, def: ItemDefinition): String {
        var out = template
        if (out.contains("{owner}")) {
            out = out.replace("{owner}", BindingHandler.ownerNameOf(stack) ?: "Unbound")
        }
        if (out.contains("{charges}") || out.contains("{max_charges}")) {
            out = out
                .replace("{charges}", ChargeHandler.charges(stack).toString())
                .replace("{max_charges}", (def.charges?.max ?: 0).toString())
        }
        if (out.contains("{souls}") || out.contains("{soul_capacity}")) {
            out = out
                .replace("{souls}", SoulHandler.getSouls(stack).toString())
                .replace("{soul_capacity}", (def.soulCapacity ?: 0).toString())
        }
        if (out.contains("{awakening_")) {
            val aw = def.awakening
            val progress = awakeningProgress(stack)
            val stage = if (aw == null) "Awakened" else "${progress}/${aw.count.toLong()}"
            out = out
                .replace("{awakening_stage}", stage)
                .replace("{awakening_progress}", progress.toString())
                .replace("{awakening_count}", (aw?.count ?: 0.0).toLong().toString())
        }
        if (out.contains("{streak}") || out.contains("{streak_tier}")) {
            out = out
                .replace("{streak}", com.fiw.tools.streak.KillstreakHandler.streak(stack).toString())
                .replace("{streak_tier}", com.fiw.tools.streak.KillstreakHandler.tier(stack, def).toString())
        }
        if (out.contains("{durability}") || out.contains("{max_durability}")) {
            out = out
                .replace("{durability}", (stack.maxDamage - stack.damageValue).coerceAtLeast(0).toString())
                .replace("{max_durability}", stack.maxDamage.toString())
        }
        return out
    }

    private fun awakeningProgress(stack: ItemStack): Long {
        val data = stack.get(DataComponents.CUSTOM_DATA) ?: return 0L
        return data.copyTag().getDoubleOr(AwakeningHandler.PROGRESS_KEY, 0.0).toLong()
    }
}
