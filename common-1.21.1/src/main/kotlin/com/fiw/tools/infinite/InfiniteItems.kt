package com.fiw.tools.infinite

import com.fiw.tools.build.FiwItems
import com.fiw.tools.build.ItemBuilder
import com.fiw.tools.config.ItemDefinition
import com.fiw.tools.config.ItemRegistry
import net.minecraft.core.HolderLookup
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.core.component.DataComponents
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.projectile.AbstractArrow
import net.minecraft.world.food.FoodProperties
import net.minecraft.world.item.ItemStack
import java.util.Optional

/**
 * Infinite-use items. Three modes (`infinite` in the item JSON):
 *
 * - `keep` (alias `normal`) — the item is never consumed
 * - `damage` — each use costs `damagePerUse` durability instead of the item; it finally breaks
 *   when the next use would exceed `maxDamage`
 * - `replace` — each use turns the item into `replaceWith` (vanilla or `fiw:` item)
 *
 * Minecraft 1.21.1 does not expose the later generic `use_remainder` data component. Edible
 * items are restored through the older food `usingConvertsTo` field; bow/crossbow ammo is still
 * restored through the entity-spawn hook in [onEntityLoad].
 */
object InfiniteItems {
    /** Entity command-tag marking an arrow already restored to its shooter (persisted with the entity). */
    private const val RESTORED_TAG = "fiw_infinite_restored"

    private const val SWEEP_TICKS = 10
    private var counter = 0

    /** Marks nested replacement builds so an A→B→A `replaceWith` cycle can't recurse forever. */
    private val buildingReplacement = ThreadLocal.withInitial { false }

    /** Called by [ItemBuilder] once every other component is on the stack. */
    fun applyUseRemainder(stack: ItemStack, def: ItemDefinition, registries: HolderLookup.Provider) {
        val inf = def.infinite ?: return
        val food = stack.get(DataComponents.FOOD) ?: return
        val expected = when (inf.mode) {
            "keep" -> stack.copyWithCount(1)
            "damage" -> {
                val next = stack.damageValue + inf.damagePerUse
                if (stack.maxDamage in 1..next) null // next use is the last — let vanilla consume it
                else stack.copyWithCount(1).also { it.damageValue = next }
            }
            "replace" -> buildReplacement(inf, registries)
            else -> null
        }
        val baked = food.usingConvertsTo().orElse(null)
        if (expected == null) {
            if (baked != null) stack.set(DataComponents.FOOD, withFoodRemainder(food, null))
            return
        }
        stripFoodRemainder(expected)
        // Only re-set when the baked remainder is missing or stale. FoodProperties embeds an
        // ItemStack and never compares equal, so an unconditional set would dirty the slot on
        // every sweep and re-send it to clients twice a second.
        if (baked != null && baked.count == expected.count && ItemStack.isSameItemSameComponents(baked, expected)) return
        stack.set(DataComponents.FOOD, withFoodRemainder(food, expected))
    }

    private fun buildReplacement(inf: ItemDefinition.InfiniteDef, registries: HolderLookup.Provider): ItemStack? {
        val spec = inf.replaceWith ?: return null
        if (spec.startsWith("fiw:")) {
            if (buildingReplacement.get()) return null // cycle guard; the sweep completes the chain later
            val def = ItemRegistry.byId(spec.removePrefix("fiw:")) ?: return null
            buildingReplacement.set(true)
            try {
                return clampToMaxStack(ItemBuilder.build(def, inf.replaceCount, registries))
            } finally {
                buildingReplacement.set(false)
            }
        }
        val id = ResourceLocation.tryParse(spec) ?: return null
        val item = BuiltInRegistries.ITEM.get(id)
        val stack = ItemStack(item, inf.replaceCount)
        return if (stack.isEmpty) null else clampToMaxStack(stack)
    }

    /** Config `replaceCount` may exceed the item's stack cap; oversized stacks corrupt saves. */
    private fun clampToMaxStack(stack: ItemStack?): ItemStack? {
        if (stack != null && stack.count > stack.maxStackSize) stack.count = stack.maxStackSize
        return stack
    }

    /**
     * Re-stamp sweep. A consumed infinite food item comes back as its baked remainder, which by
     * construction lacks its own food remainder; this puts it back (and, for damage mode, keeps
     * the baked remainder's durability one step ahead of the live stack).
     */
    fun tick(server: MinecraftServer) {
        if (++counter < SWEEP_TICKS) return
        counter = 0
        for (player in server.playerList.players) {
            val registries = player.level().registryAccess()
            val inv = player.inventory
            for (slot in 0 until inv.containerSize) {
                val stack = inv.getItem(slot)
                if (stack.isEmpty) continue
                val id = FiwItems.fiwId(stack) ?: continue
                val inf = ItemRegistry.byId(id)?.takeIf { it.infinite != null } ?: continue
                restamp(stack, inf, registries)
            }
        }
    }

    private fun restamp(stack: ItemStack, def: ItemDefinition, registries: HolderLookup.Provider) {
        applyUseRemainder(stack, def, registries)
    }

    private fun stripFoodRemainder(stack: ItemStack) {
        val food = stack.get(DataComponents.FOOD) ?: return
        if (food.usingConvertsTo().isEmpty) return
        stack.set(DataComponents.FOOD, withFoodRemainder(food, null))
    }

    private fun withFoodRemainder(food: FoodProperties, remainder: ItemStack?): FoodProperties {
        return FoodProperties(
            food.nutrition(),
            food.saturation(),
            food.canAlwaysEat(),
            food.eatSeconds(),
            Optional.ofNullable(remainder),
            food.effects()
        )
    }

    /**
     * Projectile hook (Fabric `ServerEntityEvents.ENTITY_LOAD` / NeoForge `EntityJoinLevelEvent`).
     * When an arrow fired from an infinite Fiw ammo stack spawns, its shooter immediately gets the
     * ammo back per the item's mode. The arrow is tagged (so chunk reloads can't restore twice)
     * and made non-pickupable (so collecting it can't dupe). Multishot side-arrows and
     * Infinity-enchant arrows spawn as CREATIVE_ONLY pickup and are skipped.
     */
    fun onEntityLoad(entity: Entity) {
        val arrow = entity as? AbstractArrow ?: return
        val world = arrow.level()
        if (world.isClientSide) return
        if (arrow.pickup != AbstractArrow.Pickup.ALLOWED) return
        if (arrow.tags.contains(RESTORED_TAG)) return
        val origin = arrow.pickupItemStackOrigin
        val id = FiwItems.fiwId(origin) ?: return
        val inf = ItemRegistry.byId(id)?.infinite ?: return
        val owner = arrow.owner as? ServerPlayer ?: return

        arrow.addTag(RESTORED_TAG)
        arrow.pickup = AbstractArrow.Pickup.DISALLOWED

        val give: ItemStack? = when (inf.mode) {
            "keep" -> origin.copyWithCount(1)
            "damage" -> {
                val next = origin.damageValue + inf.damagePerUse
                if (origin.maxDamage in 1..next) null // that was the last shot
                else origin.copyWithCount(1).also { it.damageValue = next }
            }
            "replace" -> buildReplacement(inf, owner.level().registryAccess())
            else -> null
        }
        if (give != null && !owner.inventory.add(give)) {
            com.fiw.tools.util.InternalDrops.drop(owner, give)
        }
    }
}
