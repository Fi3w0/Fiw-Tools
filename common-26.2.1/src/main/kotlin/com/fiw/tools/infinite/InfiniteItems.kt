package com.fiw.tools.infinite

import com.fiw.tools.build.FiwItems
import com.fiw.tools.build.ItemBuilder
import com.fiw.tools.config.ItemDefinition
import com.fiw.tools.config.ItemRegistry
import net.minecraft.core.HolderLookup
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.projectile.arrow.AbstractArrow
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.ItemStackTemplate
import net.minecraft.world.item.component.UseRemainder

/**
 * Infinite-use items. Three modes (`infinite` in the item JSON):
 *
 * - `keep` (alias `normal`) — the item is never consumed
 * - `damage` — each use costs `damagePerUse` durability instead of the item; it finally breaks
 *   when the next use would exceed `maxDamage`
 * - `replace` — each use turns the item into `replaceWith` (vanilla or `fiw:` item)
 *
 * Mechanism: vanilla's `use_remainder` component, baked to hand back a copy of the item itself.
 * The copy can't recursively contain its own remainder, so a cheap sweep re-stamps the component
 * onto any infinite item found without one. Bow/crossbow ammo never goes through `use_remainder`,
 * so fired arrows are restored via the entity-spawn hook in [onEntityLoad] instead.
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
        val remainder = buildRemainder(stack, inf, registries)
        if (remainder == null) {
            stack.remove(DataComponents.USE_REMAINDER)
        } else {
            // 26.x: UseRemainder wraps an ItemStackTemplate rather than a live ItemStack.
            stack.set(DataComponents.USE_REMAINDER, UseRemainder(ItemStackTemplate.fromNonEmptyStack(remainder)))
        }
    }

    private fun buildRemainder(stack: ItemStack, inf: ItemDefinition.InfiniteDef, registries: HolderLookup.Provider): ItemStack? =
        when (inf.mode) {
            "keep" -> stack.copyWithCount(1).also { it.remove(DataComponents.USE_REMAINDER) }
            "damage" -> {
                val next = stack.damageValue + inf.damagePerUse
                if (stack.maxDamage in 1..next) null // next use is the last — let vanilla consume it
                else stack.copyWithCount(1).also {
                    it.remove(DataComponents.USE_REMAINDER)
                    it.damageValue = next
                }
            }
            "replace" -> buildReplacement(inf, registries)
            else -> null
        }

    private fun buildReplacement(inf: ItemDefinition.InfiniteDef, registries: HolderLookup.Provider): ItemStack? {
        val spec = inf.replaceWith ?: return null
        if (spec.startsWith("fiw:")) {
            if (buildingReplacement.get()) return null // cycle guard; the sweep completes the chain later
            val def = ItemRegistry.byId(spec.removePrefix("fiw:")) ?: return null
            buildingReplacement.set(true)
            try {
                return ItemBuilder.build(def, inf.replaceCount, registries)
            } finally {
                buildingReplacement.set(false)
            }
        }
        val id = Identifier.tryParse(spec) ?: return null
        val holder = BuiltInRegistries.ITEM.get(id).orElse(null) ?: return null
        val stack = ItemStack(com.fiw.tools.util.HolderAccess.value(holder), inf.replaceCount)
        return if (stack.isEmpty) null else stack
    }

    /**
     * Re-stamp sweep. A consumed infinite item comes back as its baked remainder, which by
     * construction lacks its own `use_remainder`; this puts it back (and, for damage mode, keeps
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
        val inf = def.infinite ?: return
        when (inf.mode) {
            "damage" -> {
                val next = stack.damageValue + inf.damagePerUse
                if (stack.maxDamage in 1..next) {
                    // Next use must consume the item for real.
                    if (stack.has(DataComponents.USE_REMAINDER)) stack.remove(DataComponents.USE_REMAINDER)
                    return
                }
                val baked = stack.get(DataComponents.USE_REMAINDER)?.convertInto()?.create()
                if (baked == null || baked.damageValue != next || !ItemStack.isSameItem(baked, stack)) {
                    applyUseRemainder(stack, def, registries)
                }
            }
            else -> {
                if (!stack.has(DataComponents.USE_REMAINDER)) applyUseRemainder(stack, def, registries)
            }
        }
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
        if (arrow.entityTags().contains(RESTORED_TAG)) return // 26.x: getTags() renamed to entityTags()
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
            owner.drop(give, false, false)
        }
    }
}
