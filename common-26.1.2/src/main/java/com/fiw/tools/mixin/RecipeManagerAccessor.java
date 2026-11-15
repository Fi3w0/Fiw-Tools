package com.fiw.tools.mixin;

import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Lets {@code RecipeInjector} swap the live recipe map so custom recipes appear in the vanilla
 * recipe manager — which is what feeds the recipe book and every recipe viewer (JEI/REI/EMI)
 * through plain vanilla display sync. No viewer dependency needed.
 */
@Mixin(RecipeManager.class)
public interface RecipeManagerAccessor {
    @Accessor("recipes")
    RecipeMap fiwTools$getRecipes();

    @Accessor("recipes")
    void fiwTools$setRecipes(RecipeMap recipes);
}
