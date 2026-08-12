package com.fiw.tools.recipe

import com.fiw.tools.mixin.RecipeManagerAccessor
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceLocation
import net.minecraft.resources.ResourceKey
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.crafting.RecipeHolder
import net.minecraft.world.item.crafting.RecipeMap
import org.slf4j.LoggerFactory

/**
 * Publishes the loaded [RecipeRegistry] recipes into the vanilla recipe manager as
 * [FiwCraftingRecipe]s. From there everything is stock Minecraft: the recipe book shows them to
 * every client (vanilla included), and recipe viewers — JEI, REI, EMI, client- or server-side —
 * pick them up because they read vanilla recipes. No viewer dependency, nothing to detect.
 *
 * Runs at server start and after `/fiwtools reload`; recipes are unlocked for players so their
 * displays actually reach the client.
 */
object RecipeInjector {
    private val logger = LoggerFactory.getLogger("fiw-tools/recipes")
    private const val NAMESPACE = "fiw_tools"

    /** Replaces this mod's slice of the live recipe map with the currently loaded recipes. */
    fun inject(server: MinecraftServer) {
        val manager = server.recipeManager
        val accessor = manager as? RecipeManagerAccessor ?: run {
            logger.warn("RecipeManager accessor unavailable; recipe book display disabled.")
            return
        }
        val registries = server.registryAccess()
        val seen = HashSet<ResourceLocation>()
        val holders = RecipeRegistry.all().mapNotNull { def ->
            val id = ResourceLocation.fromNamespaceAndPath(NAMESPACE, sanitizePath(def.id))
            if (!seen.add(id)) {
                logger.warn("Recipe '${def.id}' collides with another after sanitising; not shown in the recipe book.")
                return@mapNotNull null
            }
            RecipeHolder(ResourceKey.create(Registries.RECIPE, id), FiwCraftingRecipe(def, registries))
        }
        val kept = accessor.`fiwTools$getRecipes`().values().filter { it.id().location().namespace != NAMESPACE }
        accessor.`fiwTools$setRecipes`(RecipeMap.create(kept + holders))
        manager.finalizeRecipeLoading(server.worldData.enabledFeatures())
        for (player in server.playerList.players) unlock(player)
        if (holders.isNotEmpty()) {
            logger.info("Published ${holders.size} custom recipe(s) to the vanilla recipe book.")
        }
    }

    /** Unlocks every Fiw recipe so its display syncs to this client's book and viewers. */
    fun unlock(player: ServerPlayer) {
        val ours = player.level().server.recipeManager.recipes
            .filter { it.id().location().namespace == NAMESPACE }
        if (ours.isNotEmpty()) player.awardRecipes(ours)
    }

    private fun sanitizePath(id: String): String =
        id.lowercase().replace(Regex("[^a-z0-9/._-]"), "_")
}
