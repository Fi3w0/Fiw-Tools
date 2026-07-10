package com.fiw.tools.state

import com.fiw.tools.awaken.AwakeningHandler
import com.fiw.tools.bind.BindingHandler
import com.fiw.tools.build.ItemBuilder
import com.fiw.tools.charge.ChargeHandler
import com.fiw.tools.config.ItemDefinition
import com.fiw.tools.imbue.ImbueMods
import com.fiw.tools.recharge.RechargeOnKillHandler
import com.fiw.tools.soul.SoulHandler
import com.fiw.tools.stance.StanceHandler
import com.fiw.tools.streak.KillstreakHandler
import com.fiw.tools.util.TextParser
import com.google.gson.JsonParser
import net.minecraft.core.HolderLookup
import net.minecraft.core.component.DataComponents
import net.minecraft.nbt.CompoundTag
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.CustomData
import net.minecraft.world.item.component.Unbreakable
import net.minecraft.world.item.enchantment.ItemEnchantments

/**
 * Single source of truth for carrying a player's / admin's accumulated stack state from an old stack
 * onto a freshly built one whenever an item is rebuilt from a definition. Three callers:
 *
 *  - [com.fiw.tools.sync.ItemSyncHandler] resync — config edit / builder bump (same def),
 *  - [com.fiw.tools.awaken.AwakeningHandler] transform — into the `upgradeTo` def,
 *  - [com.fiw.tools.recipe.CraftingHandler] fiw upgrade recipes — single fiw ingredient → fiw result.
 *
 * Rule (MythicCrucible expectation): **player-earned / admin-set state is NEVER wiped by a rebuild.**
 * The definition stays authoritative for what it declares; everything layered on top is carried:
 * durability rewards, enchants (config-aware), anvil rename, uncursed flag, imbue log, binding,
 * charges, killstreak, stance, souls, foreign (non-Fiw) attribute modifiers, armor trim and dye.
 *
 * Awakening progress is the sole exception on the transform paths — carrying it into the upgraded
 * item would immediately satisfy the next chain stage — so [carryAwakenProgress] is false there.
 *
 * (1.21.1 port: direct NBT reads, `ResourceLocation`, `Unbreakable(true)`.)
 */
object StackStatePreserver {
    fun carry(
        old: ItemStack,
        oldTag: CompoundTag,
        rebuilt: ItemStack,
        newDef: ItemDefinition,
        registries: HolderLookup.Provider,
        carryAwakenProgress: Boolean
    ) {
        preserveDurabilityRewards(oldTag, rebuilt, newDef)
        preserveEnchantments(old, oldTag, rebuilt, newDef)
        preservePlayerName(old, oldTag, rebuilt, newDef)
        preserveUncursed(oldTag, rebuilt)
        preserveImbueState(oldTag, rebuilt, registries)
        preserveStackState(oldTag, rebuilt, newDef, carryAwakenProgress)
        preserveSouls(oldTag, rebuilt, newDef)
        preserveForeignAttributes(old, rebuilt)
        preserveTrimAndDye(old, rebuilt)
    }

    private fun preserveDurabilityRewards(oldTag: CompoundTag, rebuilt: ItemStack, def: ItemDefinition) {
        val bonus = oldTag.getInt(RechargeOnKillHandler.BONUS_MAX_DAMAGE_KEY)
        val earnedUnbreakable = oldTag.getBoolean(RechargeOnKillHandler.UNBREAKABLE_KEY)
        if (bonus <= 0 && !earnedUnbreakable) return
        val data = rebuilt.get(DataComponents.CUSTOM_DATA) ?: return
        val tag = data.copyTag()
        if (bonus > 0) {
            val cap = def.rechargeOnKill?.maxDamageCap ?: Int.MAX_VALUE
            val next = (rebuilt.maxDamage.coerceAtLeast(0) + bonus).coerceAtMost(cap)
            if (next > rebuilt.maxDamage) rebuilt.set(DataComponents.MAX_DAMAGE, next)
            tag.putInt(RechargeOnKillHandler.BONUS_MAX_DAMAGE_KEY, bonus)
        }
        if (earnedUnbreakable) {
            rebuilt.set(DataComponents.UNBREAKABLE, Unbreakable(true))
            tag.putBoolean(RechargeOnKillHandler.UNBREAKABLE_KEY, true)
        }
        rebuilt.set(DataComponents.CUSTOM_DATA, CustomData.of(tag))
    }

    /**
     * Config enchantments normally come back from the fresh build at their configured level — but a
     * player who upgrades one at an anvil keeps that upgrade: if the stack's live level differs from
     * the level the config last stamped, the player changed it and their level wins. Every non-config
     * enchantment the player added is carried too.
     */
    private fun preserveEnchantments(old: ItemStack, oldTag: CompoundTag, rebuilt: ItemStack, newDef: ItemDefinition) {
        val playerEnchants = old.get(DataComponents.ENCHANTMENTS) ?: return
        if (playerEnchants.isEmpty) return
        val configIds = newDef.enchantments.keys.mapNotNull { ResourceLocation.tryParse(it)?.toString() }.toSet()
        val stamp = parseStamp(oldTag.getString(ItemBuilder.CONFIG_ENCHANTS_KEY))

        val merged = ItemEnchantments.Mutable(rebuilt.get(DataComponents.ENCHANTMENTS) ?: ItemEnchantments.EMPTY)
        for (entry in playerEnchants.entrySet()) {
            val id = entry.key.unwrapKey().orElse(null)?.location()?.toString() ?: continue
            val level = entry.intValue
            val stampedLevel = stamp[id]
            if (id in configIds) {
                // Player upgraded a config enchant past what the config last applied → keep theirs.
                if (stampedLevel != null && level != stampedLevel) merged.set(entry.key, level)
            } else if (stampedLevel == null || stampedLevel != level) {
                // A genuine player-added enchant → carry it. But if it was a config enchant on the
                // OLD build at exactly this level, it belongs to the old definition, not the player,
                // so it must NOT ride along into an awakened/upgraded form (different def) or survive
                // the admin removing it from config.
                merged.set(entry.key, level)
            }
        }
        rebuilt.set(DataComponents.ENCHANTMENTS, merged.toImmutable())
    }

    private fun parseStamp(raw: String): Map<String, Int> {
        if (raw.isEmpty()) return emptyMap()
        val obj = runCatching { JsonParser.parseString(raw).asJsonObject }.getOrNull() ?: return emptyMap()
        val out = HashMap<String, Int>()
        for ((k, v) in obj.entrySet()) runCatching { out[k] = v.asInt }
        return out
    }

    private fun preservePlayerName(old: ItemStack, oldTag: CompoundTag, rebuilt: ItemStack, def: ItemDefinition) {
        if (def.enchanting?.allowRename == false) return
        val currentName = old.get(DataComponents.CUSTOM_NAME) ?: return
        val appliedConfigName = oldTag.getString(ItemBuilder.CONFIG_NAME_KEY)
        val lastConfigName = if (appliedConfigName.isEmpty()) null else TextParser.parse(appliedConfigName)
        if (currentName != lastConfigName) rebuilt.set(DataComponents.CUSTOM_NAME, currentName)
    }

    private fun preserveUncursed(oldTag: CompoundTag, rebuilt: ItemStack) {
        if (!oldTag.getBoolean(ItemBuilder.UNCURSED_KEY)) return
        val data = rebuilt.get(DataComponents.CUSTOM_DATA) ?: return
        val tag = data.copyTag()
        tag.putBoolean(ItemBuilder.UNCURSED_KEY, true)
        rebuilt.set(DataComponents.CUSTOM_DATA, CustomData.of(tag))
    }

    private fun preserveImbueState(oldTag: CompoundTag, rebuilt: ItemStack, registries: HolderLookup.Provider) {
        val logJson = oldTag.getString(ItemBuilder.IMBUE_LOG_KEY).takeIf { it.isNotEmpty() }
        val count = oldTag.getInt(ItemBuilder.IMBUE_COUNT_KEY)
        if (logJson.isNullOrEmpty() && count == 0) return

        val data = rebuilt.get(DataComponents.CUSTOM_DATA) ?: CustomData.EMPTY
        val tag = data.copyTag()
        if (!logJson.isNullOrEmpty()) tag.putString(ItemBuilder.IMBUE_LOG_KEY, logJson)
        if (count > 0) tag.putInt(ItemBuilder.IMBUE_COUNT_KEY, count)
        rebuilt.set(DataComponents.CUSTOM_DATA, CustomData.of(tag))

        ImbueMods.replayLog(rebuilt, registries)
    }

    private fun preserveStackState(
        oldTag: CompoundTag,
        rebuilt: ItemStack,
        def: ItemDefinition,
        carryAwakenProgress: Boolean
    ) {
        val progress = if (carryAwakenProgress && oldTag.contains(AwakeningHandler.PROGRESS_KEY)) {
            oldTag.getDouble(AwakeningHandler.PROGRESS_KEY)
        } else null
        val boundOwner = oldTag.getString(BindingHandler.OWNER_KEY).takeIf { it.isNotEmpty() }
        val boundName = oldTag.getString(BindingHandler.OWNER_NAME_KEY).takeIf { it.isNotEmpty() }
        val oldCharges = if (oldTag.contains(ChargeHandler.CHARGES_KEY)) oldTag.getInt(ChargeHandler.CHARGES_KEY) else -1
        val keepCharges = oldCharges >= 0 && def.charges != null
        val oldStreak = oldTag.getInt(KillstreakHandler.STREAK_KEY)
        val keepStreak = oldStreak > 0 && def.killstreak != null
        val oldStance = oldTag.getString(StanceHandler.STANCE_KEY).takeIf { it.isNotEmpty() }
        val keepStance = oldStance != null && def.stances.isNotEmpty()
        if (progress == null && boundOwner == null && !keepCharges && !keepStreak && !keepStance) return
        val data = rebuilt.get(DataComponents.CUSTOM_DATA) ?: return
        val tag = data.copyTag()
        progress?.let { tag.putDouble(AwakeningHandler.PROGRESS_KEY, it) }
        boundOwner?.let { tag.putString(BindingHandler.OWNER_KEY, it) }
        boundName?.let { tag.putString(BindingHandler.OWNER_NAME_KEY, it) }
        if (keepCharges) tag.putInt(ChargeHandler.CHARGES_KEY, oldCharges.coerceAtMost(def.charges!!.max))
        if (keepStreak) tag.putInt(KillstreakHandler.STREAK_KEY, oldStreak)
        if (keepStance) tag.putString(StanceHandler.STANCE_KEY, oldStance!!)
        rebuilt.set(DataComponents.CUSTOM_DATA, CustomData.of(tag))
    }

    private fun preserveSouls(oldTag: CompoundTag, rebuilt: ItemStack, def: ItemDefinition) {
        val capacity = def.soulCapacity ?: return
        val souls = oldTag.getInt(SoulHandler.SOUL_KEY)
        if (souls <= 0) return
        val data = rebuilt.get(DataComponents.CUSTOM_DATA) ?: CustomData.EMPTY
        val tag = data.copyTag()
        tag.putInt(SoulHandler.SOUL_KEY, souls.coerceAtMost(capacity))
        rebuilt.set(DataComponents.CUSTOM_DATA, CustomData.of(tag))
    }

    /**
     * Merge back attribute modifiers set by admin commands, datapacks or other mods — the rebuild
     * only re-derives Fiw-owned modifiers (config + imbue), so anything else would vanish. Fiw-owned
     * ids (`fiw_tools:` namespace) are skipped, and ids already present on the rebuilt stack are
     * deduped (covers config `attributes` that declare a custom id).
     */
    private fun preserveForeignAttributes(old: ItemStack, rebuilt: ItemStack) {
        com.fiw.tools.build.AttributeMerge.mergeForeign(old.get(DataComponents.ATTRIBUTE_MODIFIERS), rebuilt)
    }

    private fun preserveTrimAndDye(old: ItemStack, rebuilt: ItemStack) {
        old.get(DataComponents.TRIM)?.let { rebuilt.set(DataComponents.TRIM, it) }
        val oldDye = old.get(DataComponents.DYED_COLOR)
        if (oldDye != null && oldDye != rebuilt.get(DataComponents.DYED_COLOR)) {
            rebuilt.set(DataComponents.DYED_COLOR, oldDye)
        }
    }
}