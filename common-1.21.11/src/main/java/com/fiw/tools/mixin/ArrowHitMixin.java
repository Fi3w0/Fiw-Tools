package com.fiw.tools.mixin;

import com.fiw.tools.ability.AbilityDispatcher;
import com.fiw.tools.build.FiwItems;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Fires the {@code on_arrow_hit} trigger when a player's arrow (shot from a Fiw weapon) connects. */
@Mixin(AbstractArrow.class)
public abstract class ArrowHitMixin {
    @Inject(method = "onHitEntity", at = @At("HEAD"))
    private void fiwTools$onArrowHit(EntityHitResult result, CallbackInfo ci) {
        AbstractArrow self = (AbstractArrow) (Object) this;
        if (self.level().isClientSide()) return;
        if (!(self.getOwner() instanceof ServerPlayer owner)) return;
        ItemStack weapon = self.getWeaponItem();
        if (weapon == null || !FiwItems.isFiwItem(weapon)) return;
        if (result.getEntity() instanceof LivingEntity target) {
            AbilityDispatcher.INSTANCE.onArrowHit(owner, weapon, target);
        }
    }
}
