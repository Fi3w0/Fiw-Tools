package com.fiw.tools.neoforge.mixin;

import com.fiw.tools.recipe.FiwCraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeMap;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.network.payload.RecipeContentPayload;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collection;
import java.util.List;

/**
 * Keeps this mod's runtime recipes out of NeoForge's recipe sync.
 *
 * <p>When another mod asks NeoForge to send recipes to clients (recipe viewers do), every recipe
 * of the requested types is written with its registered serializer. Ours is not registered on
 * NeoForge, because a synced registry entry would lock out every NeoForge client that does not
 * have this mod, and the mod is server-side only. Unregistered, it cannot be written, so these
 * recipes are left out of that packet. The vanilla recipe book is not affected: it gets its
 * display data another way.
 */
@Mixin(value = RecipeContentPayload.class, remap = false)
public abstract class RecipeContentPayloadMixin {

    @Inject(method = "create", at = @At("RETURN"), cancellable = true)
    private static void fiw$leaveOutRuntimeRecipes(Collection<RecipeType<?>> recipeTypes, RecipeMap recipes,
                                                   CallbackInfoReturnable<RecipeContentPayload> cir) {
        RecipeContentPayload payload = cir.getReturnValue();
        boolean hasOurs = false;
        for (RecipeHolder<?> holder : payload.recipes()) {
            if (holder.value() instanceof FiwCraftingRecipe) {
                hasOurs = true;
                break;
            }
        }
        if (!hasOurs) return;
        List<RecipeHolder<?>> kept = payload.recipes().stream()
                .filter(holder -> !(holder.value() instanceof FiwCraftingRecipe))
                .toList();
        cir.setReturnValue(new RecipeContentPayload(payload.recipeTypes(), kept));
    }
}
