package com.fiw.tools.mixin;

import com.fiw.tools.ability.BossSlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Boss-slayer bonus: scales damage players deal to bosses and boss minions (see {@link BossSlayer}). */
@Mixin(LivingEntity.class)
public abstract class LivingEntityBossBonusMixin {
    @ModifyVariable(method = "hurtServer", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private float fiwTools$bossBonus(float amount, ServerLevel level, DamageSource source) {
        return BossSlayer.scale((LivingEntity) (Object) this, source, amount);
    }
}
