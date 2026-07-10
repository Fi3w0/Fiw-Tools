package com.fiw.tools.mining

import com.fiw.tools.build.FiwItems
import com.fiw.tools.config.ItemDefinition
import com.fiw.tools.config.ItemRegistry
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.tags.TagKey
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState
import kotlin.math.abs

/**
 * Multi-block mining. When a tool carrying a `mining` config breaks a block, this breaks the extra
 * blocks around it — a connected [vein][veinTargets] (ores, logs), an [area][planeTargets] hammer
 * plane, or a [tunnel][tunnelTargets]. Each extra block is broken through the player's own
 * game-mode, so drops, XP, Fortune/Silk Touch and durability all behave exactly like a hand-mined
 * block. A thread-local guard stops the extra breaks from recursively triggering more mining (or the
 * `on_block_break` ability trigger), and the tool is kept alive — the sweep stops before the final
 * point of durability so a vein never snaps your pickaxe.
 */
object MiningHandler {
    // Our own destroyBlock calls fire the loader's break hook again; skip mining + on_block_break then.
    private val active = ThreadLocal.withInitial { false }

    /** True while we are breaking the extra blocks — loaders check this to suppress re-entrant work. */
    @JvmStatic
    fun isActive(): Boolean = active.get()

    /** Called from each loader's block-break hook with the state that was (or is about to be) broken. */
    fun onBlockBroken(player: ServerPlayer, level: ServerLevel, origin: BlockPos, brokenState: BlockState) {
        if (active.get()) return
        val stack = player.mainHandItem
        val id = FiwItems.fiwId(stack) ?: return
        val def = ItemRegistry.byId(id) ?: return
        val cfg = def.mining ?: return
        if (cfg.sneakToDisable && player.isShiftKeyDown) return

        val targets = when (cfg.mode) {
            "vein" -> veinTargets(level, stack, origin, brokenState, cfg)
            "area" -> planeTargets(player, origin, cfg, depth = 0)
            "tunnel" -> planeTargets(player, origin, cfg, depth = cfg.depth)
            else -> return
        }
        if (targets.isEmpty()) return

        active.set(true)
        try {
            for (pos in targets) {
                if (stack.isEmpty) break
                // Keep the tool alive: leave the last point of durability (breakables only).
                if (stack.isDamageableItem && !stack.has(DataComponents.UNBREAKABLE) &&
                    stack.maxDamage - stack.damageValue <= 1) break
                if (!matches(level, stack, pos, brokenState, cfg)) continue
                player.gameMode.destroyBlock(pos)
            }
        } finally {
            active.set(false)
        }
    }

    /** Flood-fill connected matching blocks outward from the mined block. */
    private fun veinTargets(
        level: ServerLevel, tool: ItemStack, origin: BlockPos, brokenState: BlockState, cfg: ItemDefinition.MiningDef
    ): List<BlockPos> {
        val out = ArrayList<BlockPos>()
        val visited = HashSet<BlockPos>()
        val queue = ArrayDeque<BlockPos>()
        visited.add(origin.immutable())
        for (n in neighbours(origin, cfg.diagonal)) if (visited.add(n)) queue.add(n)
        while (queue.isNotEmpty() && out.size < cfg.maxBlocks) {
            val pos = queue.removeFirst()
            if (!matches(level, tool, pos, brokenState, cfg)) continue
            out.add(pos)
            for (n in neighbours(pos, cfg.diagonal)) if (visited.add(n)) queue.add(n)
        }
        return out
    }

    /** A `(2r+1)`-square plane facing the player, optionally extended [depth] blocks forward (tunnel). */
    private fun planeTargets(player: ServerPlayer, origin: BlockPos, cfg: ItemDefinition.MiningDef, depth: Int): List<BlockPos> {
        val look = player.lookAngle
        val ax = abs(look.x); val ay = abs(look.y); val az = abs(look.z)
        val axis = when { ax >= ay && ax >= az -> Direction.Axis.X; ay >= az -> Direction.Axis.Y; else -> Direction.Axis.Z }
        val sign = if ((axis == Direction.Axis.X && look.x < 0) ||
            (axis == Direction.Axis.Y && look.y < 0) ||
            (axis == Direction.Axis.Z && look.z < 0)) -1 else 1
        val (uAxis, vAxis) = when (axis) {
            Direction.Axis.X -> Direction.Axis.Y to Direction.Axis.Z
            Direction.Axis.Y -> Direction.Axis.X to Direction.Axis.Z
            else -> Direction.Axis.X to Direction.Axis.Y
        }
        val r = cfg.radius
        val out = ArrayList<BlockPos>()
        for (f in 0..depth) {
            for (du in -r..r) for (dv in -r..r) {
                if (f == 0 && du == 0 && dv == 0) continue  // the mined block itself
                val d = IntArray(3)
                addAxis(d, axis, f * sign); addAxis(d, uAxis, du); addAxis(d, vAxis, dv)
                out.add(BlockPos(origin.x + d[0], origin.y + d[1], origin.z + d[2]))
                if (out.size >= cfg.maxBlocks) return out
            }
        }
        return out
    }

    private fun addAxis(d: IntArray, axis: Direction.Axis, n: Int) {
        when (axis) {
            Direction.Axis.X -> d[0] += n
            Direction.Axis.Y -> d[1] += n
            Direction.Axis.Z -> d[2] += n
        }
    }

    private fun neighbours(pos: BlockPos, diagonal: Boolean): List<BlockPos> {
        val out = ArrayList<BlockPos>(if (diagonal) 26 else 6)
        if (diagonal) {
            for (dx in -1..1) for (dy in -1..1) for (dz in -1..1) {
                if (dx == 0 && dy == 0 && dz == 0) continue
                out.add(BlockPos(pos.x + dx, pos.y + dy, pos.z + dz))
            }
        } else {
            for (dir in Direction.entries) out.add(pos.relative(dir))
        }
        return out
    }

    /** May this position be broken by the sweep? Applies hardness, correct-tool, and block-filter gates. */
    private fun matches(level: ServerLevel, tool: ItemStack, pos: BlockPos, brokenState: BlockState, cfg: ItemDefinition.MiningDef): Boolean {
        val state = level.getBlockState(pos)
        if (state.isAir) return false
        val hardness = state.getDestroySpeed(level, pos)
        if (hardness < 0f) return false  // unbreakable (bedrock, portal frame, ...)
        if (cfg.maxHardness >= 0 && hardness > cfg.maxHardness) return false
        if (cfg.requireCorrectTool && !tool.isCorrectToolForDrops(state)) return false
        if (cfg.whitelist.isNotEmpty() || cfg.tags.isNotEmpty()) return matchesFilter(state, cfg)
        if (cfg.mode == "vein" && cfg.matchBlock) return state.`is`(brokenState.block)
        return true
    }

    private fun matchesFilter(state: BlockState, cfg: ItemDefinition.MiningDef): Boolean {
        if (cfg.whitelist.isNotEmpty()) {
            val id = BuiltInRegistries.BLOCK.getKey(state.block).toString()
            if (cfg.whitelist.any { it == id }) return true
        }
        for (raw in cfg.tags) {
            val tagId = ResourceLocation.tryParse(raw.removePrefix("#")) ?: continue
            val key: TagKey<Block> = TagKey.create(Registries.BLOCK, tagId)
            if (state.`is`(key)) return true
        }
        return false
    }
}