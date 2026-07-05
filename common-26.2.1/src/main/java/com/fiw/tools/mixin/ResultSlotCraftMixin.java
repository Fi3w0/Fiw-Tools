package com.fiw.tools.mixin;

import com.fiw.tools.recipe.CraftingHandler;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Dupe fix. Fiw recipes fill the result slot without existing in the vanilla recipe manager, so
 * when the result is taken, vanilla's {@code onTake} finds no matching recipe and hits its
 * "no recipe &rarr; hand the inputs back as remainders" fallback: every ingredient is refunded and
 * merged back onto the grid stacks (a slot of n magma blocks becomes ~2n), instead of being
 * consumed. When the grid matches a Fiw recipe we consume one item per slot ourselves (honoring
 * crafting remainders like buckets) and cancel vanilla's remainder logic entirely.
 */
@Mixin(ResultSlot.class)
public abstract class ResultSlotCraftMixin {
    @Shadow @Final private CraftingContainer craftSlots;

    @Shadow protected abstract void checkTakeAchievements(ItemStack stack);

    @Inject(method = "onTake", at = @At("HEAD"), cancellable = true)
    private void fiwTools$consumeCustomRecipe(Player player, ItemStack stack, CallbackInfo ci) {
        if (CraftingHandler.INSTANCE.match(craftSlots.getItems(), craftSlots.getWidth(), craftSlots.getHeight()) == null) {
            return;
        }
        ci.cancel();
        checkTakeAchievements(stack);
        for (int i = 0; i < craftSlots.getContainerSize(); i++) {
            ItemStack slotStack = craftSlots.getItem(i);
            if (slotStack.isEmpty()) continue;
            ItemStackTemplate template = slotStack.getItem().getCraftingRemainder();
            ItemStack remainder = template != null ? template.create() : ItemStack.EMPTY;
            craftSlots.removeItem(i, 1);
            if (remainder.isEmpty()) continue;
            if (craftSlots.getItem(i).isEmpty()) {
                craftSlots.setItem(i, remainder);
            } else if (!player.getInventory().add(remainder)) {
                player.drop(remainder, false);
            }
        }
    }
}
