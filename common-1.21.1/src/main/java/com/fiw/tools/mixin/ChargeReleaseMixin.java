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
 * Fires the {@code on_charge_release} trigger when a player lets go of a charging Fiw item
 * (one with a {@code chargeable} block) before its use time runs out — i.e. every deliberate
 * release, since chargeable items use an hour-long cap.
 */
@Mixin(LivingEntity.class)
public abstract class ChargeReleaseMixin {
    @Inject(method = "releaseUsingItem", at = @At("HEAD"))
    private void fiwTools$onChargeRelease(CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (self.level().isClientSide() || !(self instanceof ServerPlayer serverPlayer)) return;
        ItemStack using = self.getUseItem();
        if (using.isEmpty() || !FiwItems.isFiwItem(using)) return;
        int ticks = self.getTicksUsingItem();
        if (ticks <= 0) return;
        AbilityDispatcher.INSTANCE.onChargeRelease(serverPlayer, using, ticks);
    }
}
