package com.fiw.tools.mixin;

import com.fiw.tools.build.EnchantingRules;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Enforces {@code enchanting.smithing} (default: allowed): with {@code smithing: false} on any
 * item in the template/base/addition slots, the smithing table's result is force-cleared — the
 * item can't be netherite-upgraded or trimmed into something the admin didn't design.
 */
@Mixin(SmithingMenu.class)
public abstract class SmithingProtectMixin {
    @Inject(method = "createResult", at = @At("RETURN"))
    private void fiwTools$applyEnchantingPolicy(CallbackInfo ci) {
        SmithingMenu menu = (SmithingMenu) (Object) this;
        for (int slot = 0; slot <= 2; slot++) {
            if (!EnchantingRules.allowsSmithing(menu.getSlot(slot).getItem())) {
                menu.getSlot(3).set(ItemStack.EMPTY);
                return;
            }
        }
    }
}
