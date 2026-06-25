package com.fiw.tools.mixin;

import com.fiw.tools.build.FiwItems;
import com.fiw.tools.build.ItemBuilder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Cursed Fiw items can't be touched at the anvil — no rename, no book-enchant, no repair. The result
 * slot is force-cleared after vanilla finishes computing it. Grindstone is already blocked for every
 * Fiw item by {@link GrindstoneProtectMixin}; this closes the matching anvil hole for cursed ones.
 */
@Mixin(AnvilMenu.class)
public abstract class AnvilProtectMixin {
    @Inject(method = "createResult", at = @At("RETURN"))
    private void fiwTools$blockCursed(CallbackInfo ci) {
        AnvilMenu menu = (AnvilMenu) (Object) this;
        ItemStack input = menu.getSlot(0).getItem();
        if (isCursed(input)) {
            menu.getSlot(2).set(ItemStack.EMPTY);
            menu.setData(0, 0);  // levelCost — zeroed so the UI doesn't lie about a possible craft
        }
    }

    private static boolean isCursed(ItemStack stack) {
        if (stack.isEmpty() || !FiwItems.isFiwItem(stack)) return false;
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return false;
        return data.copyTag().getBoolean(ItemBuilder.CURSED_KEY).orElse(false);
    }
}
