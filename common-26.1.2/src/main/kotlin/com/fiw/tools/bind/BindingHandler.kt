package com.fiw.tools.bind

import com.fiw.tools.build.FiwItems
import com.fiw.tools.config.ItemDefinition
import com.fiw.tools.config.ItemRegistry
import com.fiw.tools.curse.CurseHandler
import com.fiw.tools.util.TextParser
import net.minecraft.core.component.DataComponents
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.CustomData

/**
 * Bound artifacts. A stack with a `binding` definition belongs to exactly one player once bound:
 *
 * - `first_use` — binds to the first player who right-clicks or attacks with it
 * - `first_pickup` — binds to the first player whose inventory it lands in (the once-a-second
 *   sweep is the pickup detector, so binding happens within a second of picking it up)
 *
 * Non-owners can't fire the item's abilities (`blockUse`, default on), and with `curse: true`
 * they take `cursePerTick` damage per second while carrying it — the artifact itself punishes
 * the thief. Ownership lives in the stack's custom data and survives rebuilds and reloads.
 */
object BindingHandler {
    /** Per-stack custom-data key: owner UUID string. */
    const val OWNER_KEY = "fiw_bound_owner"

    /** Per-stack custom-data key: owner display name (for "Bound to X" feedback). */
    const val OWNER_NAME_KEY = "fiw_bound_name"

    private const val SWEEP_TICKS = 20
    private var counter = 0

    /**
     * Gate for every active ability dispatch. Returns false when the dispatch must be blocked
     * (someone else's artifact). Also the `first_use` bind point.
     */
    fun onUse(player: ServerPlayer, stack: ItemStack, def: ItemDefinition): Boolean {
        val binding = def.binding ?: return true
        val owner = ownerOf(stack)
        if (owner == null) {
            if (binding.mode == "first_use") bind(player, stack, binding)
            return true
        }
        if (owner == player.uuid.toString()) return true
        if (binding.blockUse) {
            // 26.x: displayClientMessage(c, true) renamed to sendOverlayMessage
            player.sendOverlayMessage(
                TextParser.parse("&5Bound to &d${ownerNameOf(stack) ?: "another player"}&5 — it will not answer you."))
            return false
        }
        return true
    }

    /** Is this stack bound to someone other than [player] (with ability blocking on)? */
    fun blocksUse(player: ServerPlayer, stack: ItemStack, def: ItemDefinition): Boolean {
        val binding = def.binding ?: return false
        if (!binding.blockUse) return false
        val owner = ownerOf(stack) ?: return false
        return owner != player.uuid.toString()
    }

    /** First-pickup binds + thief curse, once a second. */
    fun tick(server: MinecraftServer) {
        if (++counter < SWEEP_TICKS) return
        counter = 0
        for (player in server.playerList.players) {
            var curseDamage = 0f
            val inv = player.inventory
            for (slot in 0 until inv.containerSize) {
                val stack = inv.getItem(slot)
                if (stack.isEmpty) continue
                val id = FiwItems.fiwId(stack) ?: continue
                val binding = ItemRegistry.byId(id)?.binding ?: continue
                val owner = ownerOf(stack)
                if (owner == null) {
                    if (binding.mode == "first_pickup") bind(player, stack, binding)
                } else if (owner != player.uuid.toString() && binding.curse) {
                    curseDamage += binding.cursePerTick
                }
            }
            if (curseDamage > 0f) {
                val world = player.level()
                val src = world.damageSources().source(CurseHandler.CURSE_DAMAGE_TYPE)
                player.invulnerableTime = 0
                player.hurtServer(world, src, curseDamage)
            }
        }
    }

    private fun bind(player: ServerPlayer, stack: ItemStack, binding: ItemDefinition.BindingDef) {
        val data = stack.get(DataComponents.CUSTOM_DATA) ?: return
        val tag = data.copyTag()
        tag.putString(OWNER_KEY, player.uuid.toString())
        tag.putString(OWNER_NAME_KEY, player.gameProfile.name)
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag))
        val message = binding.message ?: "&5This artifact is now bound to you."
        player.sendSystemMessage(TextParser.parse(message))
    }

    fun ownerOf(stack: ItemStack): String? {
        val data = stack.get(DataComponents.CUSTOM_DATA) ?: return null
        return data.copyTag().getString(OWNER_KEY).orElse(null)?.takeIf { it.isNotEmpty() }
    }

    fun ownerNameOf(stack: ItemStack): String? {
        val data = stack.get(DataComponents.CUSTOM_DATA) ?: return null
        return data.copyTag().getString(OWNER_NAME_KEY).orElse(null)?.takeIf { it.isNotEmpty() }
    }
}
