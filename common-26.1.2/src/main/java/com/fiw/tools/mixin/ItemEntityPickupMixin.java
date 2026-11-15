package com.fiw.tools.mixin;

import com.fiw.tools.ability.AbilityDispatcher;
import com.fiw.tools.build.FiwItems;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fires the {@code on_pickup} trigger when a player collects a Fiw item off the ground. The
 * stack is snapshotted at HEAD (vanilla empties it on pickup) and the trigger fires at TAIL
 * only for the amount actually taken, so a full-inventory brush-past fires nothing.
 */
@Mixin(ItemEntity.class)
public abstract class ItemEntityPickupMixin {
    @Unique
    private ItemStack fiwTools$beforePickup = ItemStack.EMPTY;

    @Inject(method = "playerTouch", at = @At("HEAD"))
    private void fiwTools$capture(Player player, CallbackInfo ci) {
        ItemEntity self = (ItemEntity) (Object) this;
        fiwTools$beforePickup = (!self.level().isClientSide()
                && player instanceof ServerPlayer
                && FiwItems.isFiwItem(self.getItem()))
                ? self.getItem().copy() : ItemStack.EMPTY;
    }

    @Inject(method = "playerTouch", at = @At("TAIL"))
    private void fiwTools$onPickup(Player player, CallbackInfo ci) {
        if (fiwTools$beforePickup.isEmpty() || !(player instanceof ServerPlayer serverPlayer)) return;
        ItemEntity self = (ItemEntity) (Object) this;
        int remaining = self.isRemoved() ? 0 : self.getItem().getCount();
        int taken = fiwTools$beforePickup.getCount() - remaining;
        if (taken > 0) {
            AbilityDispatcher.INSTANCE.onPickup(serverPlayer, fiwTools$beforePickup.copyWithCount(taken));
        }
        fiwTools$beforePickup = ItemStack.EMPTY;
    }
}
