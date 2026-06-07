package com.fiw.tools.sync

import com.fiw.tools.build.ItemBuilder
import com.fiw.tools.config.ItemDefinition
import com.fiw.tools.config.ItemRegistry
import com.fiw.tools.util.TextParser
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.minecraft.core.HolderLookup
import net.minecraft.core.component.DataComponents
import net.minecraft.resources.Identifier
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.enchantment.ItemEnchantments
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

    fun init() {
        ServerPlayConnectionEvents.JOIN.register { handler, _, _ ->
            sync(handler.player)
        }

        ServerTickEvents.END_SERVER_TICK.register { server ->
            if (++tickCounter >= SWEEP_INTERVAL_TICKS) {
                tickCounter = 0
                syncAll(server)
            }
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
        val id = tag.getString(ItemBuilder.FIW_TOOLS_ID_KEY).orElse(null) ?: return null

        val def = ItemRegistry.byId(id) ?: return null
        val storedRev = tag.getString(ItemBuilder.REVISION_KEY).orElse("")
        if (storedRev == ItemBuilder.revision(def)) return null

        val rebuilt = ItemBuilder.build(def, stack.count, registries) ?: return null

        // Preserve the player's accumulated wear rather than resetting durability on every edit.
        if (rebuilt.isDamageableItem) {
            val maxDamage = rebuilt.maxDamage
            rebuilt.damageValue = stack.damageValue.coerceIn(0, (maxDamage - 1).coerceAtLeast(0))
        }
        preservePlayerEnchantments(stack, rebuilt, def)
        preservePlayerName(stack, rebuilt, tag.getString(ItemBuilder.CONFIG_NAME_KEY).orElse(""))
        preserveUncursed(tag, rebuilt)
        preserveImbueState(tag, rebuilt, registries)
        return rebuilt
    }

    /**
     * Carry the imbuement log + count across rebuilds, then replay every logged mod block on top of
     * the fresh stack. This is what makes imbuements survive `/fiwtools reload` and builder bumps —
     * the config is the floor, the log is what's been added on top.
     */
    private fun preserveImbueState(
        oldTag: net.minecraft.nbt.CompoundTag,
        rebuilt: ItemStack,
        registries: net.minecraft.core.HolderLookup.Provider
    ) {
        val logJson = oldTag.getString(ItemBuilder.IMBUE_LOG_KEY).orElse(null)
        val count = oldTag.getInt(ItemBuilder.IMBUE_COUNT_KEY).orElse(0)
        if (logJson.isNullOrEmpty() && count == 0) return

        val data = rebuilt.get(DataComponents.CUSTOM_DATA) ?: net.minecraft.world.item.component.CustomData.EMPTY
        val tag = data.copyTag()
        if (!logJson.isNullOrEmpty()) tag.putString(ItemBuilder.IMBUE_LOG_KEY, logJson)
        if (count > 0) tag.putInt(ItemBuilder.IMBUE_COUNT_KEY, count)
        rebuilt.set(DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(tag))

        com.fiw.tools.imbue.ImbueMods.replayLog(rebuilt, registries)
    }

    /**
     * Carry the per-stack `fiw_uncursed` flag across rebuilds. Without this, a `/fiwtools reload` would
     * undo a Blessed Scroll use — the rebuilt stack would lose the flag and start cursing again. The
     * flag is authoritative: once a specific stack is uncursed it stays uncursed forever.
     */
    private fun preserveUncursed(oldTag: net.minecraft.nbt.CompoundTag, rebuilt: ItemStack) {
        if (!oldTag.getBoolean(ItemBuilder.UNCURSED_KEY).orElse(false)) return
        val data = rebuilt.get(DataComponents.CUSTOM_DATA) ?: return
        val tag = data.copyTag()
        tag.putBoolean(ItemBuilder.UNCURSED_KEY, true)
        rebuilt.set(DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(tag))
    }

    /**
     * Re-applies any enchantment the player added themselves (enchanting table / anvil). Config
     * enchantments are owned by the definition — they always come from the fresh [rebuilt] stack at
     * their configured level, so a player can't override or drop them; everything else is carried over.
     */
    private fun preservePlayerEnchantments(old: ItemStack, rebuilt: ItemStack, def: ItemDefinition) {
        val playerEnchants = old.get(DataComponents.ENCHANTMENTS) ?: return
        if (playerEnchants.isEmpty) return
        val baseIds = def.enchantments.keys.mapNotNull { Identifier.tryParse(it)?.toString() }.toSet()

        val merged = ItemEnchantments.Mutable(rebuilt.get(DataComponents.ENCHANTMENTS) ?: ItemEnchantments.EMPTY)
        for (entry in playerEnchants.entrySet()) {
            val id = entry.key.unwrapKey().orElse(null)?.identifier()?.toString() ?: continue
            if (id !in baseIds) merged.set(entry.key, entry.intValue)
        }
        rebuilt.set(DataComponents.ENCHANTMENTS, merged.toImmutable())
    }

    /**
     * Keeps a player's anvil rename. [appliedConfigName] is the raw config name the item last carried;
     * if the live name still equals that, the player never renamed it and the fresh config name wins.
     */
    private fun preservePlayerName(old: ItemStack, rebuilt: ItemStack, appliedConfigName: String) {
        val currentName = old.get(DataComponents.CUSTOM_NAME) ?: return
        val lastConfigName = if (appliedConfigName.isEmpty()) null else TextParser.parse(appliedConfigName)
        if (currentName != lastConfigName) rebuilt.set(DataComponents.CUSTOM_NAME, currentName)
    }
}
