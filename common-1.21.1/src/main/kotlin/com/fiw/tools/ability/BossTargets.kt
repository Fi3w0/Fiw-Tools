package com.fiw.tools.ability

import com.fiw.tools.api.FiwBossesBridge
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.entity.Entity

/** Which entities count as bosses or boss minions for on_boss_kill, conditions and the slayer bonus. */
object BossTargets {
    /** Vanilla bosses, recognised with or without FIW Bosses. */
    val VANILLA_BOSSES = setOf(
        "minecraft:ender_dragon", "minecraft:wither", "minecraft:warden", "minecraft:elder_guardian"
    )
    private const val FIW_MINION_TYPE = "fiw_bosses:minion"

    fun typeId(entity: Entity): String = BuiltInRegistries.ENTITY_TYPE.getKey(entity.type).toString()

    /** FIW Bosses bosses (not their minions) and the vanilla bosses. */
    fun isBoss(entity: Entity): Boolean =
        typeId(entity) in VANILLA_BOSSES || (FiwBossesBridge.isPresent && FiwBossesBridge.isBoss(entity))

    /** FIW Bosses minions (custom minions and shadow clones). */
    fun isMinion(entity: Entity): Boolean =
        FiwBossesBridge.isMinion(entity) || typeId(entity) == FIW_MINION_TYPE
}
