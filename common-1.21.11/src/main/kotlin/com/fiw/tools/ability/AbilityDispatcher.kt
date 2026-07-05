package com.fiw.tools.ability

import com.fiw.tools.build.ItemBuilder
import com.fiw.tools.config.ItemDefinition
import com.fiw.tools.config.ItemRegistry
import com.fiw.tools.imbue.ImbueMods
import net.minecraft.core.component.DataComponents
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.Vec3
import org.slf4j.LoggerFactory
import java.util.concurrent.ThreadLocalRandom

object AbilityDispatcher {
    private val logger = LoggerFactory.getLogger("fiw-tools/abilities")

    fun onRightClick(player: ServerPlayer, stack: ItemStack, world: ServerLevel, target: LivingEntity?, targetPos: Vec3?): Boolean {
        // Sneaking routes to on_shift_right_click, but only when the item actually defines one —
        // items from before this trigger existed keep firing on_right_click regardless of sneak.
        val trigger = if (player.isShiftKeyDown && hasTrigger(stack, AbilityTrigger.ON_SHIFT_RIGHT_CLICK)) {
            AbilityTrigger.ON_SHIFT_RIGHT_CLICK
        } else {
            AbilityTrigger.ON_RIGHT_CLICK
        }
        return fire(trigger, player, stack, world, target, targetPos)
    }

    private fun hasTrigger(stack: ItemStack, trigger: AbilityTrigger): Boolean {
        if (stack.isEmpty) return false
        val def = resolveDefinition(stack) ?: return false
        return ImbueMods.effectiveAbilities(stack, def).any { AbilityTrigger.parse(it.trigger) == trigger }
    }

    fun onAttack(player: ServerPlayer, stack: ItemStack, world: ServerLevel, target: LivingEntity) {
        fire(AbilityTrigger.ON_ATTACK, player, stack, world, target, target.position())
    }

    fun onKill(entity: LivingEntity, source: DamageSource) {
        val attacker = source.entity
        if (attacker is ServerPlayer) {
            fire(AbilityTrigger.ON_KILL, attacker, attacker.mainHandItem, attacker.level(), entity, entity.position())
            val victimId = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(entity.type).toString()
            fire(AbilityTrigger.ON_BOSS_KILL, attacker, attacker.mainHandItem, attacker.level(), entity, entity.position(),
                abilityFilter = { ab -> bossMatches(ab, victimId) })
            com.fiw.tools.awaken.AwakeningHandler.onKill(attacker, attacker.mainHandItem, entity)
            com.fiw.tools.streak.KillstreakHandler.onKill(attacker, attacker.mainHandItem)
            com.fiw.tools.recharge.RechargeOnKillHandler.onKill(attacker, entity)
        }
    }

    /** Vanilla bosses recognised by `on_boss_kill` when the ability lists no explicit `bosses`. */
    private val DEFAULT_BOSSES = setOf(
        "minecraft:ender_dragon", "minecraft:wither", "minecraft:warden", "minecraft:elder_guardian"
    )

    /** An explicit `bosses` param wins; otherwise FIW Bosses entities and the vanilla boss set count. */
    private fun bossMatches(ab: ItemDefinition.AbilityDef, victimId: String): Boolean {
        val listed = ab.params.getAsJsonArray("bosses")
        if (listed != null) return listed.any { it.isJsonPrimitive && it.asString.equals(victimId, ignoreCase = true) }
        return victimId.startsWith("fiw_bosses:") || victimId in DEFAULT_BOSSES
    }

    fun onConsume(player: ServerPlayer, stack: ItemStack) {
        fire(AbilityTrigger.ON_CONSUME, player, stack, player.level(), null, null)
    }

    fun onPickup(player: ServerPlayer, stack: ItemStack) {
        fire(AbilityTrigger.ON_PICKUP, player, stack, player.level(), null, null)
    }

    fun onDrop(player: ServerPlayer, stack: ItemStack) {
        fire(AbilityTrigger.ON_DROP, player, stack, player.level(), null, null)
    }

    fun onDeath(player: ServerPlayer) {
        com.fiw.tools.streak.KillstreakHandler.onDeath(player)
        fireInventory(AbilityTrigger.ON_DEATH, player, null)
    }

    fun onRespawn(player: ServerPlayer) = fireInventory(AbilityTrigger.ON_RESPAWN, player, null)

    /** Fires leave for the origin and enter for the destination; abilities may filter via a `dimension` param. */
    fun onDimensionChange(player: ServerPlayer, fromId: String, toId: String) {
        fireInventory(AbilityTrigger.ON_LEAVE_DIMENSION, player) { ab -> paramMatches(ab, "dimension", fromId) }
        fireInventory(AbilityTrigger.ON_ENTER_DIMENSION, player) { ab -> paramMatches(ab, "dimension", toId) }
    }

    /** Armor/hand slot changed; abilities may filter via a `slot` param (`head`, `chest`, `mainhand`, …). */
    fun onEquipmentChange(player: ServerPlayer, slotName: String, previous: ItemStack, current: ItemStack) {
        val world = player.level()
        if (!previous.isEmpty) {
            fire(AbilityTrigger.ON_UNEQUIP, player, previous, world, null, null,
                abilityFilter = { ab -> paramMatches(ab, "slot", slotName) })
        }
        if (!current.isEmpty) {
            fire(AbilityTrigger.ON_EQUIP, player, current, world, null, null,
                abilityFilter = { ab -> paramMatches(ab, "slot", slotName) })
        }
    }

    private fun fireInventory(
        trigger: AbilityTrigger,
        player: ServerPlayer,
        abilityFilter: ((ItemDefinition.AbilityDef) -> Boolean)?
    ) {
        val world = player.level()
        val inv = player.inventory
        for (slot in 0 until inv.containerSize) {
            val stack = inv.getItem(slot)
            if (!stack.isEmpty) fire(trigger, player, stack, world, null, null, abilityFilter = abilityFilter)
        }
    }

    /** True when the ability doesn't declare [key], or declares it equal to [value]. */
    private fun paramMatches(ab: ItemDefinition.AbilityDef, key: String, value: String): Boolean {
        val configured = ab.params.get(key)?.takeIf { !it.isJsonNull && it.isJsonPrimitive }?.asString ?: return true
        return configured.equals(value, ignoreCase = true)
    }

    /** Damage the player dealt to something else — feeds `deal_damage` awakening progress. */
    fun onDamageDealt(attacker: ServerPlayer, amount: Float) {
        com.fiw.tools.awaken.AwakeningHandler.onDamageDealt(attacker, attacker.mainHandItem, amount)
    }

    fun onHurt(player: ServerPlayer, source: DamageSource, amount: Float) {
        val world = player.level()
        AbilityState.recordDamage(player.uuid, world.gameTime)
        val attacker = source.entity as? LivingEntity
        val attackerPos = attacker?.position()
        val stack = player.mainHandItem
        fire(AbilityTrigger.ON_HURT, player, stack, world, attacker, attackerPos, source, amount)
        val off = player.offhandItem
        if (off !== stack) fire(AbilityTrigger.ON_HURT, player, off, world, attacker, attackerPos, source, amount)
    }

    fun onBlockBreak(player: ServerPlayer, world: ServerLevel, pos: net.minecraft.core.BlockPos) {
        fire(AbilityTrigger.ON_BLOCK_BREAK, player, player.mainHandItem, world, null, Vec3.atCenterOf(pos))
    }

    private fun fire(
        trigger: AbilityTrigger,
        player: ServerPlayer,
        stack: ItemStack,
        world: ServerLevel,
        target: LivingEntity?,
        targetPos: Vec3?,
        damageSource: net.minecraft.world.damagesource.DamageSource? = null,
        damageAmount: Float = 0f,
        abilityFilter: ((ItemDefinition.AbilityDef) -> Boolean)? = null
    ): Boolean {
        if (stack.isEmpty) return false
        val def = resolveDefinition(stack) ?: return false
        // Binding gate first: right-click/attack are the deliberate "uses" that can bind a fresh
        // artifact; any trigger is refused outright when the stack belongs to someone else.
        if (def.binding != null) {
            val deliberateUse = trigger == AbilityTrigger.ON_RIGHT_CLICK ||
                trigger == AbilityTrigger.ON_SHIFT_RIGHT_CLICK || trigger == AbilityTrigger.ON_ATTACK
            if (deliberateUse) {
                if (!com.fiw.tools.bind.BindingHandler.onUse(player, stack, def)) return false
            } else if (com.fiw.tools.bind.BindingHandler.blocksUse(player, stack, def)) {
                return false
            }
        }
        // Imbued abilities live in the stack's log, not the definition — merged in via effectiveAbilities.
        val abilities = ImbueMods.effectiveAbilities(stack, def)
        if (abilities.isEmpty()) return false

        var any = false
        abilities.forEachIndexed { idx, abilityDef ->
            if (AbilityTrigger.parse(abilityDef.trigger) != trigger) return@forEachIndexed
            if (abilityFilter != null && !abilityFilter(abilityDef)) return@forEachIndexed
            val key = AbilityCooldownTracker.keyFor(def.id, idx, abilityDef.cooldownGroup)
            if (!AbilityCooldownTracker.isReady(player, key)) return@forEachIndexed
            if (!PassiveConditions.met(player, world, abilityDef.params)) return@forEachIndexed
            if (abilityDef.chance < 1f && ThreadLocalRandom.current().nextFloat() > abilityDef.chance) return@forEachIndexed
            val cost = abilityDef.cost
            if (cost != null && !AbilityCosts.canPay(player, stack, def, cost)) {
                if (trigger == AbilityTrigger.ON_RIGHT_CLICK || trigger == AbilityTrigger.ON_SHIFT_RIGHT_CLICK ||
                    trigger == AbilityTrigger.ON_ATTACK) {
                    AbilityCosts.notifyInsufficient(player)
                }
                return@forEachIndexed
            }
            val ability = AbilityRegistry.get(abilityDef.type) ?: run {
                logger.warn("Unknown ability type '${abilityDef.type}' on '${def.id}'")
                return@forEachIndexed
            }
            try {
                if (ability.execute(AbilityContext(player, stack, world, target, targetPos, abilityDef.params, damageSource, damageAmount))) {
                    AbilityCooldownTracker.arm(player, key, abilityDef.cooldownTicks)
                    if (cost != null) AbilityCosts.pay(player, stack, cost)
                    any = true
                }
            } catch (e: Exception) {
                logger.warn("Ability '${abilityDef.type}' on '${def.id}' threw: ${e.message}")
            }
        }
        return any
    }

    private fun resolveDefinition(stack: ItemStack): ItemDefinition? {
        val data = stack.get(DataComponents.CUSTOM_DATA) ?: return null
        val tag = data.copyTag()
        val id = tag.getString(ItemBuilder.FIW_TOOLS_ID_KEY).orElse(null) ?: return null
        return ItemRegistry.byId(id)
    }
}
