package com.fiw.tools.mixin;

import com.fiw.tools.ability.AbilityDispatcher;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.UseOnContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Fires the {@code on_block_place} trigger after a successful placement. */
@Mixin(BlockItem.class)
public abstract class BlockItemPlaceMixin {
    @Inject(method = "useOn", at = @At("RETURN"))
    private void fiwTools$onBlockPlace(UseOnContext ctx, CallbackInfoReturnable<InteractionResult> cir) {
        if (!cir.getReturnValue().consumesAction()) return;
        if (ctx.getPlayer() instanceof ServerPlayer serverPlayer) {
            AbilityDispatcher.INSTANCE.onBlockPlace(serverPlayer, ctx.getClickedPos().relative(ctx.getClickedFace()));
        }
    }
}
