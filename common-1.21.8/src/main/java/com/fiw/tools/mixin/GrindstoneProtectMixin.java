package com.fiw.tools.mixin;

import com.fiw.tools.build.EnchantingRules;
import com.fiw.tools.build.FiwItems;
import net.minecraft.world.inventory.GrindstoneMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Enforces each Fiw item's {@code enchanting.grindstone} policy (default: blocked, keeping the
 * item's enchantments permanent). When a recipe opts in, only the single-item disenchant is
 * allowed — a two-item merge involving any Fiw item stays blocked so two customs can never be
 * ground together into one, mirroring the crafting-grid repair protection.
 */
@Mixin(GrindstoneMenu.class)
public abstract class GrindstoneProtectMixin {
    @Inject(method = "computeResult", at = @At("RETURN"), cancellable = true)
    private void fiwTools$applyEnchantingPolicy(ItemStack input, ItemStack additional,
                                                CallbackInfoReturnable<ItemStack> cir) {
        if (!EnchantingRules.allowsGrindstone(input) || !EnchantingRules.allowsGrindstone(additional)) {
            cir.setReturnValue(ItemStack.EMPTY);
            return;
        }
        if (!input.isEmpty() && !additional.isEmpty()
                && (FiwItems.isFiwItem(input) || FiwItems.isFiwItem(additional))) {
            cir.setReturnValue(ItemStack.EMPTY);
        }
    }
}
