package com.fiw.tools.mixin;

import com.fiw.tools.build.FiwItems;
import net.minecraft.world.inventory.GrindstoneMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Makes a Fiw Tools item's enchantments permanent: the grindstone result is what
 * {@code computeResult} returns, so for any custom item we force an empty result. The item
 * can't be disenchanted and yields no experience, while enchanting tables and anvils (which
 * add enchantments) are untouched.
 */
@Mixin(GrindstoneMenu.class)
public abstract class GrindstoneProtectMixin {
    @Inject(method = "computeResult", at = @At("RETURN"), cancellable = true)
    private void fiwTools$protectEnchantments(ItemStack input, ItemStack additional,
                                              CallbackInfoReturnable<ItemStack> cir) {
        if (FiwItems.isFiwItem(input) || FiwItems.isFiwItem(additional)) {
            cir.setReturnValue(ItemStack.EMPTY);
        }
    }
}
