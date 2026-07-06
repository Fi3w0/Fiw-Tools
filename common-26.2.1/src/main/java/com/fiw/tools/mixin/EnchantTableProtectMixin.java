package com.fiw.tools.mixin;

import com.fiw.tools.build.EnchantingRules;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Enforces {@code enchanting.table} at the enchanting table: all three offers are zeroed out for
 * a blocked item, and the button click is refused server-side in case a client shows stale
 * offers. On 1.21.2+ the builder already strips the ENCHANTABLE component for blocked items, so
 * this is a second line of defense there; on 1.21.1 (no such component) it's the only one.
 */
@Mixin(EnchantmentMenu.class)
public abstract class EnchantTableProtectMixin {
    @Inject(method = "slotsChanged", at = @At("RETURN"))
    private void fiwTools$clearOffers(Container container, CallbackInfo ci) {
        EnchantmentMenu menu = (EnchantmentMenu) (Object) this;
        ItemStack item = menu.getSlot(0).getItem();
        if (item.isEmpty() || EnchantingRules.allowsTable(item)) return;
        for (int i = 0; i < 3; i++) {
            menu.costs[i] = 0;
            menu.enchantClue[i] = -1;
            menu.levelClue[i] = -1;
        }
        menu.broadcastChanges();
    }

    @Inject(method = "clickMenuButton", at = @At("HEAD"), cancellable = true)
    private void fiwTools$refuseClick(Player player, int id, CallbackInfoReturnable<Boolean> cir) {
        ItemStack item = ((EnchantmentMenu) (Object) this).getSlot(0).getItem();
        if (!item.isEmpty() && !EnchantingRules.allowsTable(item)) {
            cir.setReturnValue(false);
        }
    }
}
