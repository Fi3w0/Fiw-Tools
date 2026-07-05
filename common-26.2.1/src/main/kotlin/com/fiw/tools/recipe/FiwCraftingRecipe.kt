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
import net.minecraft.world.item.crafting.Ingredient
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

    override fun getSerializer(): RecipeSerializer<out CraftingRecipe> = SERIALIZER

    override fun category(): CraftingBookCategory = CraftingBookCategory.MISC

    override fun group(): String = "fiw_tools"

    override fun showNotification(): Boolean = false

    private var placement: PlacementInfo? = null

    /**
     * Real placements where expressible — recipe viewers (JEI) build their entries from these,
     * not from [display]. `fiw:` ingredients stand in as their base item here; actual matching
     * stays component-aware server-side, so a plain base item still can't be spent.
     */
    override fun placementInfo(): PlacementInfo {
        placement?.let { return it }
        val built = buildPlacement()
        placement = built
        return built
    }

    private fun buildPlacement(): PlacementInfo {
        if (definition.shaped) {
            val slots = ArrayList<java.util.Optional<Ingredient>>(definition.width * definition.height)
            for (y in 0 until definition.height) {
                for (x in 0 until definition.width) {
                    val spec = definition.ingredientAt(x, y)
                    if (spec == null) {
                        slots.add(java.util.Optional.empty())
                        continue
                    }
                    val ingredient = ingredientFor(spec) ?: return PlacementInfo.NOT_PLACEABLE
                    slots.add(java.util.Optional.of(ingredient))
                }
            }
            return PlacementInfo.createFromOptionals(slots)
        }
        val ingredients = definition.ingredients.map { ingredientFor(it) ?: return PlacementInfo.NOT_PLACEABLE }
        return PlacementInfo.create(ingredients)
    }

    private fun ingredientFor(spec: String): Ingredient? {
        if (spec.startsWith("fiw:")) {
            val def = ItemRegistry.byId(spec.removePrefix("fiw:")) ?: return null
            return itemIngredient(def.base)
        }
        if (spec.startsWith("#")) {
            val tagId = Identifier.tryParse(spec.substring(1)) ?: return null
            val tag = BuiltInRegistries.ITEM.get(TagKey.create(Registries.ITEM, tagId)).orElse(null) ?: return null
            return Ingredient.of(tag)
        }
        return itemIngredient(spec)
    }

    private fun itemIngredient(id: String): Ingredient? {
        val itemId = Identifier.tryParse(id) ?: return null
        val holder = BuiltInRegistries.ITEM.get(itemId).orElse(null) ?: return null
        return Ingredient.of(HolderAccess.value(holder))
    }

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

    companion object {
        /**
         * A real, registered serializer — required for Fabric's recipe-sync module (which is what
         * feeds JEI/REI/EMI's `SynchronizedRecipes`) to include these recipes at all: it only
         * syncs recipes whose serializer the connecting client has declared support for, which in
         * turn only happens for serializers registered in [Registries.RECIPE_SERIALIZER]. The
         * vanilla recipe book doesn't need this — it reads display data through a separate,
         * always-on path — which is why the book worked before this serializer existed and a
         * pure-viewer mod like JEI didn't.
         *
         * `codec()` (JSON) is never invoked: these recipes are runtime-injected, never loaded
         * from or written to a datapack file.
         */
        val SERIALIZER: RecipeSerializer<FiwCraftingRecipe> = RecipeSerializer(
            MapCodec.unit { throw UnsupportedOperationException("Fiw recipes are runtime-injected and never loaded from JSON") },
            StreamCodec.of(
                { buf, recipe -> RecipeDefinitionStreamCodec.encode(buf, recipe.definition) },
                { buf -> FiwCraftingRecipe(RecipeDefinitionStreamCodec.decode(buf), buf.registryAccess()) }
            )
        )

        fun register() {
            net.minecraft.core.Registry.register(BuiltInRegistries.RECIPE_SERIALIZER,
                Identifier.fromNamespaceAndPath("fiw_tools", "custom_recipe"), SERIALIZER)
        }
    }
}

/** Wire format for [RecipeDefinition]: everything needed to rebuild it client-side for display. */
private object RecipeDefinitionStreamCodec {
    fun encode(buf: RegistryFriendlyByteBuf, def: RecipeDefinition) {
        buf.writeUtf(def.id)
        buf.writeBoolean(def.shaped)
        buf.writeCollection(def.pattern) { b, line -> b.writeUtf(line) }
        buf.writeCollection(def.key.entries) { b, e -> b.writeUtf(e.key.toString()); b.writeUtf(e.value) }
        buf.writeCollection(def.ingredients) { b, i -> b.writeUtf(i) }
        buf.writeUtf(def.resultItem)
        buf.writeVarInt(def.resultCount)
    }

    fun decode(buf: RegistryFriendlyByteBuf): RecipeDefinition {
        val id = buf.readUtf()
        val shaped = buf.readBoolean()
        val pattern = buf.readList { it.readUtf() }
        val key = buf.readList { b -> b.readUtf()[0] to b.readUtf() }.toMap()
        val ingredients = buf.readList { it.readUtf() }
        val resultItem = buf.readUtf()
        val resultCount = buf.readVarInt()
        return RecipeDefinition(id, shaped, pattern, key, ingredients, resultItem, resultCount)
    }
}
