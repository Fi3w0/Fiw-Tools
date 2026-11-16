package com.fiw.tools.mixin;

import com.fiw.tools.build.FiwItems;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.UseOnContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Any Fiw Tools item whose base is a {@code BlockItem} (bell, head, banner, sapling, …) must
 * never be placed in the world — placement would lose the custom NBT and turn the item into a
 * plain world block. Cancelling at the HEAD of {@link BlockItem#useOn} blocks both server-side
 * placement and the client-side prediction in one shot, so the held item stays where it is.
 */
@Mixin(BlockItem.class)
public abstract class BlockItemPlacePreventMixin {
    @Inject(method = "useOn", at = @At("HEAD"), cancellable = true)
    private void fiwTools$blockPlacement(UseOnContext ctx, CallbackInfoReturnable<InteractionResult> cir) {
        if (FiwItems.isFiwItem(ctx.getItemInHand())) {
            cir.setReturnValue(InteractionResult.FAIL);
        }
    }
}
