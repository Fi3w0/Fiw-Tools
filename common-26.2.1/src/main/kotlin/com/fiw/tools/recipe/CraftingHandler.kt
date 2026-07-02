package com.fiw.tools.recipe

import com.fiw.tools.build.FiwItems
import com.fiw.tools.build.ItemBuilder
import com.fiw.tools.config.ItemRegistry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.resources.Identifier
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.tags.TagKey
import net.minecraft.world.inventory.AbstractCraftingMenu
import net.minecraft.world.item.ItemStack
import org.slf4j.LoggerFactory

/**
 * Server-side custom crafting. Every tick, players with a crafting grid open (2x2 inventory or
 * 3x3 table — both are [AbstractCraftingMenu]) have their grid checked against [RecipeRegistry];
 * on a match the result slot is filled with the crafted stack. Vanilla's own take-handling then
 * consumes one item per grid slot, so no mixin or recipe-manager injection is needed and vanilla
 * clients see everything.
 *
 * Safety rule: when Fiw items sit in the grid but no custom recipe matches, any vanilla-computed
 * result is suppressed. Without this, two damaged Fiw swords would match vanilla's repair recipe
 * and be melted into a plain vanilla item.
 */
object CraftingHandler {
    private val logger = LoggerFactory.getLogger("fiw-tools/crafting")

    fun tick(server: MinecraftServer) {
        if (RecipeRegistry.all().isEmpty()) return
        for (player in server.playerList.players) {
            val menu = player.containerMenu as? AbstractCraftingMenu ?: continue
            handle(player, menu)
        }
    }

    private fun handle(player: ServerPlayer, menu: AbstractCraftingMenu) {
        val gridSlots = menu.inputGridSlots
        val width = menu.gridWidth
        val height = menu.gridHeight
        if (gridSlots.size != width * height) return

        val grid = gridSlots.map { it.item }
        if (grid.all { it.isEmpty }) return

        val recipe = match(grid, width, height)
        val resultSlot = menu.resultSlot
        if (recipe != null) {
            val result = buildResult(recipe, player) ?: return
            val existing = resultSlot.item
            if (!ItemStack.isSameItemSameComponents(existing, result) || existing.count != result.count) {
                resultSlot.set(result)
            }
        } else if (grid.any { FiwItems.isFiwItem(it) }) {
            if (!resultSlot.item.isEmpty) resultSlot.set(ItemStack.EMPTY)
        }
    }

    /** First matching recipe for this grid, or null. */
    fun match(grid: List<ItemStack>, width: Int, height: Int): RecipeDefinition? {
        for (recipe in RecipeRegistry.all()) {
            val ok = if (recipe.shaped) matchShaped(recipe, grid, width, height)
            else matchShapeless(recipe, grid)
            if (ok) return recipe
        }
        return null
    }

    private fun matchShaped(recipe: RecipeDefinition, grid: List<ItemStack>, width: Int, height: Int): Boolean {
        val rw = recipe.width
        val rh = recipe.height
        if (rw > width || rh > height) return false
        for (offY in 0..(height - rh)) {
            for (offX in 0..(width - rw)) {
                if (matchShapedAt(recipe, grid, width, height, offX, offY, mirrored = false)) return true
                if (rw > 1 && matchShapedAt(recipe, grid, width, height, offX, offY, mirrored = true)) return true
            }
        }
        return false
    }

    private fun matchShapedAt(
        recipe: RecipeDefinition,
        grid: List<ItemStack>,
        width: Int,
        height: Int,
        offX: Int,
        offY: Int,
        mirrored: Boolean
    ): Boolean {
        for (y in 0 until height) {
            for (x in 0 until width) {
                val rx = if (mirrored) recipe.width - 1 - (x - offX) else x - offX
                val ry = y - offY
                val spec = if (rx in 0 until recipe.width && ry in 0 until recipe.height) {
                    recipe.ingredientAt(rx, ry)
                } else null
                val stack = grid[y * width + x]
                if (spec == null) {
                    if (!stack.isEmpty) return false
                } else {
                    if (!ingredientMatches(spec, stack)) return false
                }
            }
        }
        return true
    }

    private fun matchShapeless(recipe: RecipeDefinition, grid: List<ItemStack>): Boolean {
        val stacks = grid.filter { !it.isEmpty }
        if (stacks.size != recipe.ingredients.size) return false
        // Backtracking assignment — specs can overlap (a tag and a specific item), so greedy
        // matching could wrongly fail. Nine slots max keeps this trivial.
        val used = BooleanArray(recipe.ingredients.size)
        fun assign(i: Int): Boolean {
            if (i == stacks.size) return true
            for (j in recipe.ingredients.indices) {
                if (used[j] || !ingredientMatches(recipe.ingredients[j], stacks[i])) continue
                used[j] = true
                if (assign(i + 1)) return true
                used[j] = false
            }
            return false
        }
        return assign(0)
    }

    /**
     * Does [stack] satisfy ingredient [spec]? `fiw:` specs match by custom id; everything else
     * matches by item registry id or `#tag` — but never a Fiw item, so custom items can't be
     * spent as their vanilla base material.
     */
    fun ingredientMatches(spec: String, stack: ItemStack): Boolean {
        if (stack.isEmpty) return false
        if (spec.startsWith("fiw:")) return FiwItems.fiwId(stack) == spec.removePrefix("fiw:")
        if (FiwItems.isFiwItem(stack)) return false
        if (spec.startsWith("#")) {
            val tagId = Identifier.tryParse(spec.substring(1)) ?: return false
            return stack.`is`(TagKey.create(Registries.ITEM, tagId))
        }
        val id = Identifier.tryParse(spec) ?: return false
        return BuiltInRegistries.ITEM.getKey(stack.item) == id
    }

    /** Build the recipe's result stack — a full custom item for `fiw:` results, plain otherwise. */
    fun buildResult(recipe: RecipeDefinition, player: ServerPlayer): ItemStack? {
        val spec = recipe.resultItem
        if (spec.startsWith("fiw:")) {
            val fiwId = spec.removePrefix("fiw:")
            val def = ItemRegistry.byId(fiwId) ?: run {
                logger.warn("Recipe '${recipe.id}': unknown Fiw item '$fiwId' in result")
                return null
            }
            return ItemBuilder.build(def, recipe.resultCount, player.level().registryAccess())
        }
        val itemId = Identifier.tryParse(spec) ?: run {
            logger.warn("Recipe '${recipe.id}': invalid result id '$spec'"); return null
        }
        val holder = BuiltInRegistries.ITEM.get(itemId).orElse(null) ?: run {
            logger.warn("Recipe '${recipe.id}': unknown result item '$spec'"); return null
        }
        val item = com.fiw.tools.util.HolderAccess.value(holder)
        val stack = ItemStack(item, recipe.resultCount)
        return if (stack.isEmpty) null else stack
    }
}
