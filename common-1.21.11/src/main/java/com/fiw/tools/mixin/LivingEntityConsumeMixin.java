package com.fiw.tools.mixin;

import com.fiw.tools.ability.AbilityDispatcher;
import com.fiw.tools.build.FiwItems;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fires the {@code on_consume} trigger the moment a player finishes eating or drinking a Fiw
 * item. Injected at HEAD so the ability still sees the stack before vanilla consumes it.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityConsumeMixin {
    @Inject(method = "completeUsingItem", at = @At("HEAD"))
    private void fiwTools$onConsume(CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (!(self instanceof ServerPlayer player) || player.level().isClientSide()) return;
        ItemStack use = player.getUseItem();
        if (!use.isEmpty() && FiwItems.isFiwItem(use)) {
            AbilityDispatcher.INSTANCE.onConsume(player, use);
        }
    }
}
