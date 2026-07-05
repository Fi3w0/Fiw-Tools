package com.fiw.tools.stance

import com.fiw.tools.build.FiwItems
import com.fiw.tools.config.ItemDefinition
import com.fiw.tools.config.ItemRegistry
import com.fiw.tools.util.TextParser
import net.minecraft.core.component.DataComponents
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.CustomData

/**
 * Stance toggle. Items with a `stances` list carry an active stance per stack (`fiw_stance` in
 * custom data, first entry by default); sneak + swap-hands cycles it instead of swapping.
 * Abilities that declare a `stance` only fire while it is active; `{stance}` works in
 * `dynamicLore`.
 */
object StanceHandler {
    /** Per-stack custom-data key: active stance name. */
    const val STANCE_KEY = "fiw_stance"

    fun activeStance(stack: ItemStack, def: ItemDefinition): String? {
        val first = def.stances.firstOrNull() ?: return null
        val data = stack.get(DataComponents.CUSTOM_DATA) ?: return first
        val tag = data.copyTag()
        val stored = tag.getString(STANCE_KEY).orElse(null) ?: return first
        return if (def.stances.any { it.equals(stored, ignoreCase = true) }) stored else first
    }

    /** True when the sneak+swap was consumed as a stance toggle (the hand swap is cancelled). */
    fun onSwapHands(player: ServerPlayer): Boolean {
        if (!player.isShiftKeyDown) return false
        val stack = player.mainHandItem
        if (stack.isEmpty) return false
        val def = ItemRegistry.byId(FiwItems.fiwId(stack) ?: return false) ?: return false
        if (def.stances.size < 2) return false

        val current = activeStance(stack, def)
        val index = def.stances.indexOfFirst { it.equals(current, ignoreCase = true) }.coerceAtLeast(0)
        val next = def.stances[(index + 1) % def.stances.size]
        val data = stack.get(DataComponents.CUSTOM_DATA) ?: return false
        val tag = data.copyTag()
        tag.putString(STANCE_KEY, next)
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag))

        val text = "&7Stance: &f&l" + next
        player.sendOverlayMessage(TextParser.parse(text)) // 26.x: displayClientMessage(c, true) renamed
        player.level().playSound(null, player.x, player.y, player.z,
            SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.8f, 1.4f)
        return true
    }
}
