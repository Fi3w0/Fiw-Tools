package com.fiw.tools.mixin;

import com.fiw.tools.ability.AbilityDispatcher;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Fires the {@code on_shield_block} trigger when a player's blocking item eats damage. */
@Mixin(LivingEntity.class)
public abstract class LivingEntityBlockingMixin {
    @Inject(method = "applyItemBlocking", at = @At("RETURN"))
    private void fiwTools$onShieldBlock(ServerLevel level, DamageSource source, float amount,
                                        CallbackInfoReturnable<Float> cir) {
        if (cir.getReturnValue() <= 0f) return;
        LivingEntity self = (LivingEntity) (Object) this;
        if (self instanceof ServerPlayer serverPlayer) {
            LivingEntity attacker = source.getEntity() instanceof LivingEntity living ? living : null;
            AbilityDispatcher.INSTANCE.onShieldBlock(serverPlayer, attacker, cir.getReturnValue(), self.getItemBlockingWith());
        }
    }
}
