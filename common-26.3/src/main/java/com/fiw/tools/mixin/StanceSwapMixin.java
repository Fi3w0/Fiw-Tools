package com.fiw.tools.mixin;

import com.fiw.tools.stance.StanceHandler;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Sneak + swap-hands cycles the stance of a held Fiw item with a {@code stances} list instead
 * of swapping hands. The HEAD injection runs twice (netty thread, then re-dispatched on the
 * server thread); the off-thread pass is skipped so state is only touched on the server thread.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class StanceSwapMixin {
    @Shadow public net.minecraft.server.level.ServerPlayer player;

    @Inject(method = "handlePlayerAction", at = @At("HEAD"), cancellable = true)
    private void fiwTools$stanceToggle(ServerboundPlayerActionPacket packet, CallbackInfo ci) {
        if (packet.getAction() != ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND) return;
        if (!this.player.level().getServer().isSameThread()) return;
        if (StanceHandler.INSTANCE.onSwapHands(this.player)) ci.cancel();
    }
}
