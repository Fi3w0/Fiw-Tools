package com.fiw.tools.api

import com.fiw.tools.build.ItemBuilder
import com.fiw.tools.config.ItemRegistry
import net.minecraft.core.HolderLookup
import net.minecraft.server.MinecraftServer
import net.minecraft.world.item.ItemStack

/**
 * Stable, public API for other mods (notably Fiw Bosses) to look up Fiw Tools items.
 */
object FiwToolsAPI {
    @JvmStatic fun isLoaded(): Boolean = true

    @JvmStatic fun listIds(): Set<String> = ItemRegistry.ids()

    @JvmStatic
    @JvmOverloads
    fun getItemStack(id: String, count: Int = 1, registries: HolderLookup.Provider): ItemStack? {
        val def = ItemRegistry.byId(id) ?: return null
        return ItemBuilder.build(def, count, registries)
    }

    @JvmStatic
    @JvmOverloads
    fun getItemStack(id: String, server: MinecraftServer, count: Int = 1): ItemStack? =
        getItemStack(id, count, server.registryAccess())
}
