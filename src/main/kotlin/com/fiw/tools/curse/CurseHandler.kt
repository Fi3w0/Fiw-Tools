package com.fiw.tools.curse

import com.fiw.tools.build.FiwItems
import com.fiw.tools.build.ItemBuilder
import com.fiw.tools.config.ItemDefinition
import com.fiw.tools.config.ItemRegistry
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.core.component.DataComponents
import net.minecraft.core.particles.ParticleOptions
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundSource
import net.minecraft.world.damagesource.DamageType
import net.minecraft.world.item.ItemStack
import org.slf4j.LoggerFactory

/**
 * Drives the curse mechanic. Every [SWEEP_TICKS] ticks (~1 s) every online player's inventory + armor
 * + off hand is scanned for cursed Fiw items. For any item whose definition has `cursed: true`, whose
 * specific stack does *not* carry the [ItemBuilder.UNCURSED_KEY] flag, and whose holder is not in the
 * `curseWhitelist`, the player takes [ItemDefinition.CurseSettings.perTick] damage from the custom
 * `fiw_tools:curse` damage type — armor/resistance/enchantments are bypassed via the datapack tags.
 *
 * Ender chest scanning is opt-in per-definition (`checksEnderChest`). When at least one of a player's
 * carried cursed items has the flag, the ender chest is also walked so stashing the cursed item there
 * does not buy the holder peace.
 */
object CurseHandler {
    private val logger = LoggerFactory.getLogger("fiw-tools/curse")

    /** Sweep cadence — once per second is fine. The work is a cheap inventory scan. */
    private const val SWEEP_TICKS = 20
    private var counter = 0

    /** Resolved at use time so a server restart picks up datapack registration. */
    val CURSE_DAMAGE_TYPE: ResourceKey<DamageType> = ResourceKey.create(
        Registries.DAMAGE_TYPE,
        Identifier.fromNamespaceAndPath("fiw_tools", "curse")
    )

    fun init() {
        ServerTickEvents.END_SERVER_TICK.register { server ->
            if (++counter >= SWEEP_TICKS) {
                counter = 0
                sweep(server)
            }
        }
    }

    private fun sweep(server: MinecraftServer) {
        for (player in server.playerList.players) {
            tickPlayer(player, player.level())
        }
    }

    private fun tickPlayer(player: ServerPlayer, world: ServerLevel) {
        val inv = player.inventory
        val name = player.gameProfile.name
        val uuidStr = player.uuid.toString()

        var totalDamage = 0f
        var firstDef: ItemDefinition? = null

        // Main inventory (hotbar + main grid + armor + offhand all live in Inventory.containerSize).
        for (slot in 0 until inv.containerSize) {
            val stack = inv.getItem(slot)
            val def = cursedDefFor(stack, name, uuidStr) ?: continue
            totalDamage += def.curseSettings.perTick
            if (firstDef == null) firstDef = def
        }

        // Ender chest pass — gated on the registry, not on what the player happens to be carrying.
        // Without this, hiding the cursed item *only* in the ender chest would let the player escape
        // the sweep entirely (nothing in main inv → checkEnder stays false → chest never walked).
        if (anyDefinitionWatchesEnderChest()) {
            val ender = player.enderChestInventory
            for (slot in 0 until ender.containerSize) {
                val stack = ender.getItem(slot)
                val def = cursedDefFor(stack, name, uuidStr) ?: continue
                if (!def.curseSettings.checksEnderChest) continue
                totalDamage += def.curseSettings.perTick
                if (firstDef == null) firstDef = def
            }
        }

        if (totalDamage <= 0f || firstDef == null) return

        val src = world.damageSources().source(CURSE_DAMAGE_TYPE)
        player.invulnerableTime = 0
        player.hurtServer(world, src, totalDamage)

        // Flavor — fires once per sweep regardless of stack count, so two curses don't double the SFX.
        val settings = firstDef.curseSettings
        playSound(world, player, settings.sound)
        spawnParticles(world, player, settings.particles)
    }

    /**
     * Does any loaded cursed definition watch the ender chest? Computed each sweep and used to gate the
     * (rare) ender-chest walk. Iterating the registry's values is a few dozen entries at worst — cheap
     * compared to actually opening + scanning every player's ender chest unconditionally.
     */
    private fun anyDefinitionWatchesEnderChest(): Boolean {
        for (def in ItemRegistry.all()) {
            if (def.cursed && def.curseSettings.checksEnderChest) return true
        }
        return false
    }

    /**
     * Returns the definition of a cursed Fiw stack whose holder is *not* whitelisted and which has not
     * been individually uncursed. Null in every other case so the sweep can `continue` quickly.
     */
    private fun cursedDefFor(stack: ItemStack, holderName: String, holderUuid: String): ItemDefinition? {
        if (stack.isEmpty) return null
        val data = stack.get(DataComponents.CUSTOM_DATA) ?: return null
        val tag = data.copyTag()
        // Hot path: the CURSED_KEY flag is stamped by ItemBuilder; reading it is one tag lookup with no
        // map indirection. Items without it skip the rest of the work.
        if (!tag.getBoolean(ItemBuilder.CURSED_KEY).orElse(false)) return null
        if (tag.getBoolean(ItemBuilder.UNCURSED_KEY).orElse(false)) return null
        val id = FiwItems.fiwId(stack) ?: return null
        val def = ItemRegistry.byId(id) ?: return null
        if (!def.cursed) return null  // definition flipped since stack was built — wait for resync
        if (isWhitelisted(def.curseWhitelist, holderName, holderUuid)) return null
        return def
    }

    /** Whitelist accepts player names, raw UUID strings, or the `name|uuid` form written by commands. */
    private fun isWhitelisted(whitelist: List<String>, name: String, uuid: String): Boolean {
        if (whitelist.isEmpty()) return false
        for (entry in whitelist) {
            val trimmed = entry.trim()
            if (trimmed.isEmpty()) continue
            val pipe = trimmed.indexOf('|')
            if (pipe >= 0) {
                val n = trimmed.substring(0, pipe)
                val u = trimmed.substring(pipe + 1)
                if (u.equals(uuid, ignoreCase = true) || n.equals(name, ignoreCase = true)) return true
            } else {
                if (trimmed.equals(uuid, ignoreCase = true) || trimmed.equals(name, ignoreCase = true)) return true
            }
        }
        return false
    }

    private fun playSound(world: ServerLevel, player: ServerPlayer, id: String) {
        val sid = Identifier.tryParse(id) ?: return
        val holder = BuiltInRegistries.SOUND_EVENT.get(sid).orElse(null) ?: return
        val event = com.fiw.tools.util.HolderAccess.value(holder)
        world.playSound(null, player.x, player.y, player.z, event, SoundSource.HOSTILE, 0.6f, 0.8f)
    }

    private fun spawnParticles(world: ServerLevel, player: ServerPlayer, id: String) {
        val sid = Identifier.tryParse(id) ?: return
        val holder = BuiltInRegistries.PARTICLE_TYPE.get(sid).orElse(null) ?: return
        val type = com.fiw.tools.util.HolderAccess.value(holder) as? ParticleOptions ?: return
        world.sendParticles(type, player.x, player.y + 1.0, player.z, 14, 0.4, 0.6, 0.4, 0.02)
    }
}
