package com.fiw.tools.mixin;

import com.google.common.collect.Multimap;
import java.util.Map;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeMap;
import net.minecraft.world.item.crafting.RecipeType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * 26.3 only builds a {@link RecipeMap} from the recipe registry ({@code create(HolderLookup)}),
 * so {@code RecipeInjector} calls the private constructor that {@code create} itself uses.
 */
@Mixin(RecipeMap.class)
public interface RecipeMapInvoker {
    @Invoker("<init>")
    static RecipeMap fiwTools$create(Multimap<RecipeType<?>, RecipeHolder<?>> byType,
                                     Map<ResourceKey<Recipe<?>>, RecipeHolder<?>> byKey) {
        throw new AssertionError();
    }
}
