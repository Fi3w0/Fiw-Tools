package com.fiw.tools.recipe

import com.fiw.tools.build.FiwItems
import com.fiw.tools.build.ItemBuilder
import com.fiw.tools.config.ItemRegistry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.tags.TagKey
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.CraftingMenu
import net.minecraft.world.inventory.InventoryMenu
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack
import org.slf4j.LoggerFactory
import java.util.UUID

/**
 * Server-side custom crafting. Every tick, players with a crafting grid open (2x2 inventory or
 * 3x3 table have their grid checked against [RecipeRegistry];
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

    /** Last custom result shown per player, so reload/despawn races can't hand it out unconsumed. */
    private val customResults = HashMap<UUID, ItemStack>()

    fun tick(server: MinecraftServer) {
        if (RecipeRegistry.all().isEmpty() && customResults.isEmpty()) return
        for (player in server.playerList.players) {
            val menu = player.containerMenu
            when (menu) {
                is CraftingMenu -> handle(player, menu, menu.slots[0], menu.slots.subList(1, 10), 3, 3)
                is InventoryMenu -> handle(player, menu, menu.slots[0], menu.slots.subList(1, 5), 2, 2)
                else -> customResults.remove(player.uuid)
            }
        }
    }

    /**
     * Was [taken] the custom result this player's grid was showing? Covers the race where the
     * result is taken in the same tick a reload removed its recipe — ResultSlotCraftMixin then
     * still consumes the ingredients instead of letting vanilla's no-recipe fallback refund them.
     */
    fun wasCustomResult(player: Player, taken: ItemStack): Boolean {
        val marker = customResults[player.uuid] ?: return false
        return marker.count == taken.count && ItemStack.isSameItemSameComponents(marker, taken)
    }

    fun clear(player: Player) {
        customResults.remove(player.uuid)
    }

    private fun handle(player: ServerPlayer, menu: AbstractContainerMenu, resultSlot: Slot, gridSlots: List<Slot>, width: Int, height: Int) {
        val grid = gridSlots.map { it.item }
        if (grid.all { it.isEmpty }) {
            customResults.remove(player.uuid)
            return
        }

        val recipe = match(grid, width, height)
        if (recipe != null) {
            val result = buildResult(recipe, player, grid)
            if (result == null) {
                customResults.remove(player.uuid)
                if (!resultSlot.item.isEmpty) resultSlot.set(ItemStack.EMPTY)
                return
            }
            val existing = resultSlot.item
            if (!ItemStack.isSameItemSameComponents(existing, result) || existing.count != result.count) {
                resultSlot.set(result)
            }
            // Defensive copy: the slot stack can be mutated (split) by take handling; an aliased
            // marker would mutate with it and break the stale-take comparison.
            customResults[player.uuid] = result.copy()
            return
        }
        val stale = customResults.remove(player.uuid)
        if (grid.any { FiwItems.isFiwItem(it) }) {
            if (!resultSlot.item.isEmpty) resultSlot.set(ItemStack.EMPTY)
        } else if (stale != null && !resultSlot.item.isEmpty &&
            resultSlot.item.count == stale.count && ItemStack.isSameItemSameComponents(resultSlot.item, stale)
        ) {
            // Recipes changed under an open grid (reload): wipe our stale result and let
            // vanilla recompute its own result for this grid.
            resultSlot.set(ItemStack.EMPTY)
            menu.slotsChanged(gridSlots[0].container)
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
            val tagId = ResourceLocation.tryParse(spec.substring(1)) ?: return false
            return stack.`is`(TagKey.create(Registries.ITEM, tagId))
        }
        val id = ResourceLocation.tryParse(spec) ?: return false
        return BuiltInRegistries.ITEM.getKey(stack.item) == id
    }

    /**
     * Build the recipe's result stack — a full custom item for `fiw:` results, plain otherwise.
     * Pass the crafting [grid] so ingredient enchantments can transfer when the recipe asks for it.
     */
    fun buildResult(recipe: RecipeDefinition, player: ServerPlayer, grid: List<ItemStack>? = null): ItemStack? {
        val stack = buildBaseResult(recipe, player) ?: return null
        if (grid != null) {
            EnchantTransfer.apply(recipe, stack, grid)
            carryUpgradeState(recipe, stack, grid, player)
        }
        return stack
    }

    /**
     * Upgrade recipes (docs recommend crafting as an awakening path) rebuild the `fiw:` result fresh,
     * which would return a blank sword for an imbued / soul-bound / enchanted input. When the grid
     * holds exactly one Fiw-item ingredient, carry that ingredient's player state onto the result —
     * the same pipeline the resync and awaken transforms use. Awakening progress is not carried.
     */
    private fun carryUpgradeState(
        recipe: RecipeDefinition,
        result: ItemStack,
        grid: List<ItemStack>,
        player: ServerPlayer
    ) {
        if (!recipe.resultItem.startsWith("fiw:")) return
        val fiwSources = grid.filter { !it.isEmpty && FiwItems.isFiwItem(it) }
        if (fiwSources.size != 1) return
        val source = fiwSources[0]
        val def = ItemRegistry.byId(recipe.resultItem.removePrefix("fiw:")) ?: return
        val oldData = source.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA) ?: return
        com.fiw.tools.state.StackStatePreserver.carry(
            source, oldData.copyTag(), result, def, player.level().registryAccess(), carryAwakenProgress = false
        )
    }

    private fun buildBaseResult(recipe: RecipeDefinition, player: ServerPlayer): ItemStack? {
        val spec = recipe.resultItem
        if (spec.startsWith("fiw:")) {
            val fiwId = spec.removePrefix("fiw:")
            val def = ItemRegistry.byId(fiwId) ?: run {
                logger.warn("Recipe '${recipe.id}': unknown Fiw item '$fiwId' in result")
                return null
            }
            return clampToMaxStack(ItemBuilder.build(def, recipe.resultCount, player.level().registryAccess()))
        }
        val itemId = ResourceLocation.tryParse(spec) ?: run {
            logger.warn("Recipe '${recipe.id}': invalid result id '$spec'"); return null
        }
        val item = BuiltInRegistries.ITEM.get(itemId) ?: run {
            logger.warn("Recipe '${recipe.id}': unknown result item '$spec'"); return null
        }
        val stack = ItemStack(item, recipe.resultCount)
        return if (stack.isEmpty) null else clampToMaxStack(stack)
    }

    /** Config `count` may exceed the item's stack cap; oversized stacks corrupt saves. */
    private fun clampToMaxStack(stack: ItemStack?): ItemStack? {
        if (stack != null && stack.count > stack.maxStackSize) stack.count = stack.maxStackSize
        return stack
    }
}
