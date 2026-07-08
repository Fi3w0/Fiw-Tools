package com.fiw.tools.mixin;

import com.fiw.tools.build.EnchantingRules;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Enforces each Fiw item's {@code enchanting} policy at the anvil, after vanilla computes the
 * result. {@code anvil: false} (the default for cursed items) force-clears the result whether the
 * item sits in the base or the sacrifice slot. {@code allowEnchant: false} clears any result that
 * would add or change the base's enchantments (rename and repair still pass). {@code allowRename:
 * false} stamps the configured name back onto the result — and if the rename was the only change,
 * clears the result entirely so players aren't sold a no-op. The item's configured attribute
 * modifiers are always re-stamped so an anvil op can never leave it with different attributes.
 */
@Mixin(AnvilMenu.class)
public abstract class AnvilProtectMixin {
    @Inject(method = "createResult", at = @At("RETURN"))
    private void fiwTools$applyEnchantingPolicy(CallbackInfo ci) {
        AnvilMenu menu = (AnvilMenu) (Object) this;
        ItemStack base = menu.getSlot(0).getItem();
        ItemStack sacrifice = menu.getSlot(1).getItem();
        if (!EnchantingRules.allowsAnvil(base) || !EnchantingRules.allowsAnvil(sacrifice)) {
            clearResult(menu);
            return;
        }
        ItemStack result = menu.getSlot(2).getItem();
        if (result.isEmpty()) return;

        // Enchant lock: any result that changes the base's enchantments is an enchant op — refuse.
        if (!EnchantingRules.allowsEnchant(base) && EnchantingRules.enchantmentsDiffer(base, result)) {
            clearResult(menu);
            return;
        }

        boolean changed = EnchantingRules.restoreAttributes(result);

        if (!EnchantingRules.allowsRename(base) && EnchantingRules.restoreName(base, result)) {
            if (result.getCount() == base.getCount() && ItemStack.isSameItemSameComponents(result, base)) {
                // The rename was the only change; nothing left worth paying for.
                clearResult(menu);
                return;
            }
            changed = true;
        }
        if (changed) menu.getSlot(2).set(result);  // re-set so corrections sync to the client
    }

    private static void clearResult(AnvilMenu menu) {
        menu.getSlot(2).set(ItemStack.EMPTY);
        menu.setData(0, 0);  // levelCost — zeroed so the UI doesn't lie about a possible craft
    }
}
