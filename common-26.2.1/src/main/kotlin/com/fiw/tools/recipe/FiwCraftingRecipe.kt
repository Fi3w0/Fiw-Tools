package com.fiw.tools.recipe

import com.fiw.tools.build.ItemBuilder
import com.fiw.tools.config.ItemRegistry
import com.fiw.tools.util.HolderAccess
import com.mojang.serialization.MapCodec
import net.minecraft.core.HolderLookup
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.resources.Identifier
import net.minecraft.tags.TagKey
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.crafting.CraftingBookCategory
import net.minecraft.world.item.crafting.CraftingInput
import net.minecraft.world.item.crafting.CraftingRecipe
import net.minecraft.world.item.crafting.PlacementInfo
import net.minecraft.world.item.crafting.RecipeSerializer
import net.minecraft.world.item.crafting.display.RecipeDisplay
import net.minecraft.world.item.crafting.display.ShapedCraftingRecipeDisplay
import net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay
import net.minecraft.world.item.crafting.display.SlotDisplay
import net.minecraft.world.level.Level

/**
 * A [RecipeDefinition] wrapped as a real vanilla [CraftingRecipe], runtime-injected into the
 * recipe manager. Matching and assembly run server-side only and reuse the component-aware
 * `fiw:` ingredient logic; [display] hands clients pure vanilla display data (full ItemStacks,
 * components included), so the recipe book — and any recipe viewer reading vanilla recipes
 * (JEI/REI/EMI) — shows custom crafts without any client-side Fiw code.
 */
class FiwCraftingRecipe(
    private val definition: RecipeDefinition,
    private val registries: HolderLookup.Provider
) : CraftingRecipe {

    override fun matches(input: CraftingInput, level: Level): Boolean =
        CraftingHandler.matches(definition, input.items(), input.width(), input.height())

    override fun assemble(input: CraftingInput): ItemStack =
        CraftingHandler.buildResult(definition, registries)?.copy() ?: ItemStack.EMPTY

    override fun getSerializer(): RecipeSerializer<out CraftingRecipe> = Serializer

    override fun category(): CraftingBookCategory = CraftingBookCategory.MISC

    override fun group(): String = "fiw_tools"

    override fun showNotification(): Boolean = false

    /** No recipe-book autofill: `fiw:` ingredients can't be expressed as vanilla placements. */
    override fun placementInfo(): PlacementInfo = PlacementInfo.NOT_PLACEABLE

    override fun display(): List<RecipeDisplay> {
        val result = CraftingHandler.buildResult(definition, registries)?.takeIf { !it.isEmpty } ?: return emptyList()
        val resultDisplay = SlotDisplay.ItemStackSlotDisplay(net.minecraft.world.item.ItemStackTemplate.fromNonEmptyStack(result))
        val station = SlotDisplay.ItemSlotDisplay(Items.CRAFTING_TABLE)
        return if (definition.shaped) {
            val slots = ArrayList<SlotDisplay>(definition.width * definition.height)
            for (y in 0 until definition.height) {
                for (x in 0 until definition.width) {
                    slots.add(slotFor(definition.ingredientAt(x, y)))
                }
            }
            listOf(ShapedCraftingRecipeDisplay(definition.width, definition.height, slots, resultDisplay, station))
        } else {
            listOf(ShapelessCraftingRecipeDisplay(definition.ingredients.map { slotFor(it) }, resultDisplay, station))
        }
    }

    private fun slotFor(spec: String?): SlotDisplay {
        if (spec == null) return SlotDisplay.Empty.INSTANCE
        if (spec.startsWith("fiw:")) {
            val def = ItemRegistry.byId(spec.removePrefix("fiw:")) ?: return SlotDisplay.Empty.INSTANCE
            val stack = ItemBuilder.build(def, 1, registries)?.takeIf { !it.isEmpty } ?: return SlotDisplay.Empty.INSTANCE
            return SlotDisplay.ItemStackSlotDisplay(net.minecraft.world.item.ItemStackTemplate.fromNonEmptyStack(stack))
        }
        if (spec.startsWith("#")) {
            val tagId = Identifier.tryParse(spec.substring(1)) ?: return SlotDisplay.Empty.INSTANCE
            return SlotDisplay.TagSlotDisplay(TagKey.create(Registries.ITEM, tagId))
        }
        val id = Identifier.tryParse(spec) ?: return SlotDisplay.Empty.INSTANCE
        val holder = BuiltInRegistries.ITEM.get(id).orElse(null) ?: return SlotDisplay.Empty.INSTANCE
        return SlotDisplay.ItemSlotDisplay(HolderAccess.value(holder))
    }

    private companion object {
        /** Runtime recipes are never read from JSON or written anywhere; the codecs must never run. */
        val Serializer: RecipeSerializer<FiwCraftingRecipe> = RecipeSerializer(
            MapCodec.unit { throw UnsupportedOperationException("Fiw recipes are runtime-injected and never serialized") },
            StreamCodec.of(
                { _, _ -> throw UnsupportedOperationException("Fiw recipes are runtime-injected and never synced as logic") },
                { _ -> throw UnsupportedOperationException("Fiw recipes are runtime-injected and never synced as logic") }
            )
        )
    }
}
