package com.fiw.tools.sync

import com.fiw.tools.build.ItemBuilder
import com.fiw.tools.config.ItemRegistry
import net.minecraft.core.HolderLookup
import net.minecraft.core.component.DataComponents
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import org.slf4j.LoggerFactory

/**
 * Keeps Fiw Tools items already sitting in player inventories in sync with their current
 * definitions. An item is recognised purely by its persistent `fiw_tools_id` custom_data tag,
 * so detection survives server restarts, world swaps and mod updates with no extra bookkeeping.
 *
 * When a definition changes (config edit, or a builder bump via [ItemBuilder.BUILDER_VERSION]),
 * the item is rebuilt from the live config, preserving the player's durability and stack count.
 * Items whose id is no longer in the registry are left untouched, so re-adding the config later
 * makes them work again.
 */
object ItemSyncHandler {
    private val logger = LoggerFactory.getLogger("fiw-tools/sync")

    // Most resyncs are event-driven (player join, /fiwtools reload) and cost nothing between events.
    // This sweep is only a safety net — e.g. an out-of-date item picked up from a chest after a reload.
    // At 200 ticks (10s) with cached revisions, a pass is just slot iteration + O(1) map lookups.
    private const val SWEEP_INTERVAL_TICKS = 200
    private var tickCounter = 0

    fun onJoin(player: ServerPlayer) {
        sync(player)
    }

    fun tick(server: MinecraftServer) {
        if (++tickCounter >= SWEEP_INTERVAL_TICKS) {
            tickCounter = 0
            syncAll(server)
        }
    }

    /** Re-sync every online player's inventory. Called after `/fiwtools reload` and periodically. */
    fun syncAll(server: MinecraftServer) {
        for (player in server.playerList.players) sync(player)
    }

    fun sync(player: ServerPlayer) {
        val registries = player.level().registryAccess()
        val inventory = player.inventory
        var updated = 0
        for (slot in 0 until inventory.containerSize) {
            val stack = inventory.getItem(slot)
            val refreshed = refresh(stack, registries) ?: continue
            inventory.setItem(slot, refreshed)
            updated++
        }
        if (updated > 0) logger.info("Synced $updated item(s) for ${player.gameProfile.name}")
    }

    /**
     * Returns a rebuilt stack if [stack] is a Fiw Tools item whose stored revision is out of date,
     * or null if it is not ours, is unknown, or is already current (the common case — no work done).
     */
    private fun refresh(stack: ItemStack, registries: HolderLookup.Provider): ItemStack? {
        if (stack.isEmpty) return null
        val data = stack.get(DataComponents.CUSTOM_DATA) ?: return null
        val tag = data.copyTag()
        val id = tag.getString(ItemBuilder.FIW_TOOLS_ID_KEY).takeIf { it.isNotEmpty() } ?: return null

        val def = ItemRegistry.byId(id) ?: return null
        val storedRev = tag.getString(ItemBuilder.REVISION_KEY)
        if (storedRev == ItemBuilder.revision(def)) return null

        val rebuilt = ItemBuilder.build(def, stack.count, registries) ?: return null

        // Carry every scrap of player-earned / admin-set state onto the fresh build (durability
        // rewards, enchants, rename, uncurse, imbue log, binding, charges, streak, stance, souls,
        // foreign attributes, trim, dye) — the same pipeline the awaken/craft transforms use.
        com.fiw.tools.state.StackStatePreserver.carry(stack, tag, rebuilt, def, registries, carryAwakenProgress = true)

        // Preserve the player's accumulated wear rather than resetting durability on every edit.
        // Runs after the preserver so an earned max-damage bonus is already reflected in maxDamage.
        if (rebuilt.isDamageableItem) {
            val maxDamage = rebuilt.maxDamage
            rebuilt.damageValue = stack.damageValue.coerceIn(0, (maxDamage - 1).coerceAtLeast(0))
        }
        return rebuilt
    }

}
