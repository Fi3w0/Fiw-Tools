package com.fiw.tools.charge

import com.fiw.tools.build.FiwItems
import com.fiw.tools.config.ItemDefinition
import com.fiw.tools.config.ItemRegistry
import net.minecraft.core.component.DataComponents
import net.minecraft.server.MinecraftServer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.CustomData

/**
 * Universal per-stack charge pool. Items with a `charges` block carry `fiw_charges` in custom
 * data; abilities drain it through their `cost` block, kills refill it via `rechargeOnKill`,
 * and natural regen (when `regenTicks` > 0) runs from the once-a-second sweep here.
 *
 * While the pool is full the regen timestamp keeps sliding forward, so the countdown to the
 * next charge always starts from the moment one is actually spent.
 */
object ChargeHandler {
    /** Per-stack custom-data key: current charges. */
    const val CHARGES_KEY = "fiw_charges"

    /** Per-stack custom-data key: game time of the next natural regen. */
    const val REGEN_AT_KEY = "fiw_charge_regen_at"

    private const val SWEEP_TICKS = 20
    private var counter = 0

    fun charges(stack: ItemStack): Int {
        val data = stack.get(DataComponents.CUSTOM_DATA) ?: return 0
        return data.copyTag().getInt(CHARGES_KEY).orElse(0)
    }

    /** Add up to [amount] charges (clamped to max). Returns how many were actually added. */
    fun add(stack: ItemStack, def: ItemDefinition, amount: Int): Int {
        val cfg = def.charges ?: return 0
        if (amount <= 0) return 0
        val current = charges(stack)
        val next = (current + amount).coerceAtMost(cfg.max)
        if (next == current) return 0
        write(stack) { it.putInt(CHARGES_KEY, next) }
        return next - current
    }

    /** Spend [amount] charges. Returns false (and spends nothing) when there aren't enough. */
    fun consume(stack: ItemStack, amount: Int): Boolean {
        if (amount <= 0) return true
        val current = charges(stack)
        if (current < amount) return false
        write(stack) { it.putInt(CHARGES_KEY, current - amount) }
        return true
    }

    fun tick(server: MinecraftServer) {
        if (++counter < SWEEP_TICKS) return
        counter = 0
        for (player in server.playerList.players) {
            val now = player.level().gameTime
            val inv = player.inventory
            for (slot in 0 until inv.containerSize) {
                val stack = inv.getItem(slot)
                if (stack.isEmpty) continue
                val id = FiwItems.fiwId(stack) ?: continue
                val cfg = ItemRegistry.byId(id)?.charges ?: continue
                if (cfg.regenTicks <= 0) continue
                regen(stack, cfg, now)
            }
        }
    }

    private fun regen(stack: ItemStack, cfg: ItemDefinition.ChargesDef, now: Long) {
        val data = stack.get(DataComponents.CUSTOM_DATA) ?: return
        val tag = data.copyTag()
        val current = tag.getInt(CHARGES_KEY).orElse(0)
        val regenAt = tag.getLong(REGEN_AT_KEY).orElse(0L)
        if (current >= cfg.max || regenAt == 0L || regenAt > now + cfg.regenTicks) {
            // Full pool, first sighting, or a stale future timestamp (e.g. world swap):
            // (re)start the countdown without granting anything.
            val next = now + cfg.regenTicks
            if (regenAt != next) {
                tag.putLong(REGEN_AT_KEY, next)
                stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag))
            }
            return
        }
        if (now < regenAt) return
        tag.putInt(CHARGES_KEY, (current + cfg.regenAmount).coerceAtMost(cfg.max))
        tag.putLong(REGEN_AT_KEY, now + cfg.regenTicks)
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag))
    }

    private inline fun write(stack: ItemStack, edit: (net.minecraft.nbt.CompoundTag) -> Unit) {
        val data = stack.get(DataComponents.CUSTOM_DATA) ?: return
        val tag = data.copyTag()
        edit(tag)
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag))
    }
}
