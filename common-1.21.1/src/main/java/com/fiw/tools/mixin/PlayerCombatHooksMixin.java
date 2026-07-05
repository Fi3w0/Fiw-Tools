package com.fiw.tools.mixin;

import com.fiw.tools.ability.AbilityDispatcher;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Server-side combat hooks: {@code on_crit} and {@code on_fall} triggers. */
@Mixin(Player.class)
public abstract class PlayerCombatHooksMixin {
    @Inject(method = "crit", at = @At("HEAD"))
    private void fiwTools$onCrit(Entity target, CallbackInfo ci) {
        Player self = (Player) (Object) this;
        if (self.level().isClientSide()) return;
        if (self instanceof ServerPlayer serverPlayer && target instanceof LivingEntity living) {
            AbilityDispatcher.INSTANCE.onCrit(serverPlayer, living);
        }
    }

    @Inject(method = "causeFallDamage", at = @At("RETURN"))
    private void fiwTools$onFall(float fallDistance, float multiplier, DamageSource source,
                                 CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue()) return;
        Player self = (Player) (Object) this;
        if (self instanceof ServerPlayer serverPlayer) {
            AbilityDispatcher.INSTANCE.onFall(serverPlayer, (double) fallDistance);
        }
    }

    @Inject(method = "hurtCurrentlyUsedShield", at = @At("HEAD"))
    private void fiwTools$onShieldBlock(float amount, CallbackInfo ci) {
        Player self = (Player) (Object) this;
        if (amount > 0 && self instanceof ServerPlayer serverPlayer) {
            AbilityDispatcher.INSTANCE.onShieldBlock(serverPlayer, self.getLastHurtByMob(), amount);
        }
    }
}
