package com.fiw.tools.projectile

import com.fiw.tools.ability.AbilityDispatcher
import com.fiw.tools.build.FiwItems
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.projectile.arrow.AbstractArrow

/**
 * Entity-spawn hook for the `on_shoot` trigger: an arrow fired from a Fiw weapon dispatches it
 * once for the shooter. The arrow is tagged so chunk reloads can't re-fire the trigger.
 */
object ProjectileHooks {
    private const val SHOT_TAG = "fiw_shot_dispatched"

    fun onEntityLoad(entity: Entity) {
        val arrow = entity as? AbstractArrow ?: return
        if (arrow.level().isClientSide) return
        if (arrow.tags.contains(SHOT_TAG)) return
        val owner = arrow.owner as? ServerPlayer ?: return
        val weapon = arrow.weaponItem ?: return
        if (!FiwItems.isFiwItem(weapon)) return
        arrow.addTag(SHOT_TAG)
        AbilityDispatcher.onShoot(owner, weapon)
    }
}
