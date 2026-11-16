package com.fiw.tools.mixin;

import com.fiw.tools.death.KeepOnDeathHandler;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Inventory.class)
public abstract class PlayerInventoryDropAllMixin {
    @Shadow public Player player;

    @Inject(method = "dropAll", at = @At("HEAD"))
    private void fiwTools$captureKeepOnDeath(CallbackInfo ci) {
        Inventory self = (Inventory) (Object) this;
        int size = self.getContainerSize();
        for (int i = 0; i < size; i++) {
            ItemStack stack = self.getItem(i);
            if (KeepOnDeathHandler.shouldKeep(stack)) {
                KeepOnDeathHandler.stash(player.getUUID(), i, stack);
                self.setItem(i, ItemStack.EMPTY);
            }
        }
    }
}
