package com.fiw.tools.mixin;

import com.fiw.tools.ability.AbilityDispatcher;
import com.fiw.tools.build.FiwItems;
import com.fiw.tools.util.InternalDrops;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Fires the {@code on_drop} trigger when a player deliberately tosses a Fiw item (Q key, drag
 * out of an inventory, …). Death drops are excluded — dying is not "dropping your artifact".
 */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerDropMixin {
    @Inject(
        method = "drop(Lnet/minecraft/world/item/ItemStack;ZZ)Lnet/minecraft/world/entity/item/ItemEntity;",
        at = @At("HEAD")
    )
    private void fiwTools$onDrop(ItemStack stack, boolean throwRandomly, boolean includeThrower,
                                 CallbackInfoReturnable<ItemEntity> cir) {
        if (InternalDrops.active()) return;
        ServerPlayer self = (ServerPlayer) (Object) this;
        if (!stack.isEmpty() && !self.isDeadOrDying() && FiwItems.isFiwItem(stack)) {
            AbilityDispatcher.INSTANCE.onDrop(self, stack);
        }
    }
}
