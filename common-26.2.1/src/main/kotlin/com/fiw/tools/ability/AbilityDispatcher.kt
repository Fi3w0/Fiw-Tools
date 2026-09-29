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
            val weapon = killingWeapon(attacker, source)
            fire(AbilityTrigger.ON_KILL, attacker, weapon, attacker.level(), entity, entity.position())
            val victimId = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(entity.type).toString()
            fire(AbilityTrigger.ON_BOSS_KILL, attacker, weapon, attacker.level(), entity, entity.position(),
                abilityFilter = { ab -> bossMatches(ab, victimId) })
            com.fiw.tools.awaken.AwakeningHandler.onKill(attacker, weapon, entity)
            com.fiw.tools.streak.KillstreakHandler.onKill(attacker, weapon)
            com.fiw.tools.recharge.RechargeOnKillHandler.onKill(attacker, entity)
        }
    }

    /**
     * The stack that actually scored a kill: a projectile kill resolves to the weapon that fired
     * the arrow (swapping items mid-flight can't credit the wrong one), else the main hand.
     */
    private fun killingWeapon(attacker: ServerPlayer, source: DamageSource): ItemStack {
        val direct = source.directEntity
        if (direct is net.minecraft.world.entity.projectile.arrow.AbstractArrow) {
            direct.weaponItem?.takeIf { !it.isEmpty }?.let { return liveStackFor(attacker, it) }
        }
        return attacker.mainHandItem
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

    /** Set while on_boss_phase abilities run, so one that shifts the phase can't recurse. */
    private var inBossPhaseDispatch = false

    /**
     * A Fiw Bosses boss changed phase ([phase] is 1-based). Fires `on_boss_phase` on the held and
     * worn items of every player near the boss, with the boss as the target. Abilities may filter
     * with `range` (default 48), `phase` (only when entering that phase) and `bosses` (config ids).
     */
    fun onBossPhase(boss: LivingEntity, bossId: String, phase: Int, previousPhase: Int) {
        if (inBossPhaseDispatch) return
        val world = boss.level() as? ServerLevel ?: return
        val filter = { ab: ItemDefinition.AbilityDef, distSq: Double ->
            val range = ab.params.get("range")?.takeIf { !it.isJsonNull && it.isJsonPrimitive }?.asDouble ?: 48.0
            val wanted = ab.params.get("phase")?.takeIf { !it.isJsonNull && it.isJsonPrimitive }?.asInt
            val listed = ab.params.get("bosses")?.takeIf { it.isJsonArray }?.asJsonArray
            distSq <= range * range &&
                (wanted == null || wanted == phase) &&
                (listed == null || listed.any { it.isJsonPrimitive && it.asString.equals(bossId, ignoreCase = true) })
        }
        inBossPhaseDispatch = true
        try {
            for (player in world.players()) {
                if (player.isSpectator) continue
                val distSq = player.distanceToSqr(boss)
                if (distSq > MAX_BOSS_PHASE_RANGE * MAX_BOSS_PHASE_RANGE) continue
                val seen = ArrayList<ItemStack>()
                for (slot in BOSS_PHASE_SLOTS) {
                    val stack = player.getItemBySlot(slot)
                    if (stack.isEmpty || seen.any { it === stack }) continue
                    seen.add(stack)
                    fire(AbilityTrigger.ON_BOSS_PHASE, player, stack, world, boss, boss.position(),
                        abilityFilter = { ab -> filter(ab, distSq) })
                }
            }
        } finally {
            inBossPhaseDispatch = false
        }
    }

    private const val MAX_BOSS_PHASE_RANGE = 256.0
    private val BOSS_PHASE_SLOTS = listOf(
        net.minecraft.world.entity.EquipmentSlot.MAINHAND, net.minecraft.world.entity.EquipmentSlot.OFFHAND,
        net.minecraft.world.entity.EquipmentSlot.HEAD, net.minecraft.world.entity.EquipmentSlot.CHEST,
        net.minecraft.world.entity.EquipmentSlot.LEGS, net.minecraft.world.entity.EquipmentSlot.FEET
    )

    fun onConsume(player: ServerPlayer, stack: ItemStack) {
        fire(AbilityTrigger.ON_CONSUME, player, stack, player.level(), null, null)
    }

    fun onPickup(player: ServerPlayer, stack: ItemStack) {
        // The pickup hook hands us a snapshot copy; abilities must mutate the real inventory stack.
        fire(AbilityTrigger.ON_PICKUP, player, liveInventoryStack(player, stack), player.level(), null, null)
    }

    private fun liveInventoryStack(player: ServerPlayer, copy: ItemStack): ItemStack {
        val id = com.fiw.tools.build.FiwItems.fiwId(copy) ?: return copy
        val inv = player.inventory
        var sameId: ItemStack? = null
        for (slot in 0 until inv.containerSize) {
            val s = inv.getItem(slot)
            if (s.isEmpty || com.fiw.tools.build.FiwItems.fiwId(s) != id) continue
            // Exact per-stack state wins — two copies of one item can differ in charges/streak.
            if (ItemStack.isSameItemSameComponents(s, copy)) return s
            if (sameId == null) sameId = s
        }
        return sameId ?: copy
    }

    fun onDrop(player: ServerPlayer, stack: ItemStack) {
        fire(AbilityTrigger.ON_DROP, player, stack, player.level(), null, null)
    }

    fun onDeath(player: ServerPlayer) {
        com.fiw.tools.streak.KillstreakHandler.onDeath(player)
        fireInventory(AbilityTrigger.ON_DEATH, player, null)
    }

    fun onRespawn(player: ServerPlayer) {
        // Death is a hard reset for transient combat state (blood debt, tethers, shades, parry,
        // stuns, aegis...). Cooldowns are intentionally NOT cleared, so dying can't reset them.
        AbilityState.clear(player)
        ChannelHandler.cancel(player.uuid)
        fireInventory(AbilityTrigger.ON_RESPAWN, player, null)
    }

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

    /**
     * Damage the player dealt to something else — feeds `deal_damage` awakening progress.
     * The credited item resolves through the damage source (arrow damage credits the bow);
     * non-projectile delayed damage still falls back to the current main hand.
     */
    fun onDamageDealt(attacker: ServerPlayer, source: DamageSource, amount: Float) {
        com.fiw.tools.awaken.AwakeningHandler.onDamageDealt(attacker, killingWeapon(attacker, source), amount)
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

    /** A projectile left the bow/crossbow; [weapon] may be the arrow's snapshot of it. */
    fun onShoot(player: ServerPlayer, weapon: ItemStack) {
        fire(AbilityTrigger.ON_SHOOT, player, liveStackFor(player, weapon), player.level(), null, null)
    }

    /** The player's arrow connected. Fired with the weapon that shot it. */
    fun onArrowHit(player: ServerPlayer, weapon: ItemStack, target: LivingEntity) {
        fire(AbilityTrigger.ON_ARROW_HIT, player, liveStackFor(player, weapon), player.level(), target, target.position())
    }

    fun onShieldBlock(player: ServerPlayer, attacker: LivingEntity?, blocked: Float, blocking: ItemStack?) {
        val world = player.level()
        // The item that actually blocked owns the trigger; both hands only as a fallback.
        if (blocking != null && !blocking.isEmpty) {
            fire(AbilityTrigger.ON_SHIELD_BLOCK, player, blocking, world, attacker, attacker?.position(), damageAmount = blocked)
            return
        }
        val stack = player.mainHandItem
        fire(AbilityTrigger.ON_SHIELD_BLOCK, player, stack, world, attacker, attacker?.position(), damageAmount = blocked)
        val off = player.offhandItem
        if (off !== stack) fire(AbilityTrigger.ON_SHIELD_BLOCK, player, off, world, attacker, attacker?.position(), damageAmount = blocked)
    }

    fun onCrit(player: ServerPlayer, target: LivingEntity) {
        fire(AbilityTrigger.ON_CRIT, player, player.mainHandItem, player.level(), target, target.position())
    }

    fun onBlockPlace(player: ServerPlayer, pos: net.minecraft.core.BlockPos) {
        val world = player.level()
        val center = Vec3.atCenterOf(pos)
        val stack = player.mainHandItem
        fire(AbilityTrigger.ON_BLOCK_PLACE, player, stack, world, null, center)
        val off = player.offhandItem
        if (off !== stack) fire(AbilityTrigger.ON_BLOCK_PLACE, player, off, world, null, center)
    }

    /** Fall damage landed; abilities may gate on a `minDistance` param (blocks fallen). */
    fun onFall(player: ServerPlayer, distance: Double) {
        val world = player.level()
        val filter = { ab: ItemDefinition.AbilityDef ->
            val min = ab.params.get("minDistance")?.takeIf { !it.isJsonNull && it.isJsonPrimitive }?.asDouble ?: 0.0
            distance >= min
        }
        val stack = player.mainHandItem
        fire(AbilityTrigger.ON_FALL, player, stack, world, null, null,
            damageAmount = distance.toFloat(), abilityFilter = filter)
        val off = player.offhandItem
        if (off !== stack) fire(AbilityTrigger.ON_FALL, player, off, world, null, null,
            damageAmount = distance.toFloat(), abilityFilter = filter)
    }

    /**
     * A hold-to-charge item was released after [chargeTicks] ticks. Abilities gate on
     * `minChargeTicks`/`maxChargeTicks` params; the tick count arrives as the damage amount.
     */
    fun onChargeRelease(player: ServerPlayer, stack: ItemStack, chargeTicks: Int) {
        val filter = { ab: ItemDefinition.AbilityDef ->
            val min = ab.params.get("minChargeTicks")?.takeIf { !it.isJsonNull && it.isJsonPrimitive }?.asInt ?: 0
            val max = ab.params.get("maxChargeTicks")?.takeIf { !it.isJsonNull && it.isJsonPrimitive }?.asInt ?: Int.MAX_VALUE
            chargeTicks in min..max
        }
        fire(AbilityTrigger.ON_CHARGE_RELEASE, player, stack, player.level(), null, null,
            damageAmount = chargeTicks.toFloat(), abilityFilter = filter)
    }

    /** Copies (an arrow's weapon snapshot) resolve back to the live held stack when possible. */
    private fun liveStackFor(player: ServerPlayer, copy: ItemStack): ItemStack {
        val id = com.fiw.tools.build.FiwItems.fiwId(copy) ?: return copy
        if (com.fiw.tools.build.FiwItems.fiwId(player.mainHandItem) == id) return player.mainHandItem
        if (com.fiw.tools.build.FiwItems.fiwId(player.offhandItem) == id) return player.offhandItem
        return copy
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

        // Pass 1: keep the abilities that clear every non-random gate (trigger, stance, filter,
        // cooldown, conditions, affordable cost). Chance is resolved next so proc groups can pick a
        // single winner — an unpayable ability must not enter the roll and steal its group's proc.
        val eligible = ArrayList<Int>()
        abilities.forEachIndexed { idx, abilityDef ->
            if (AbilityTrigger.parse(abilityDef.trigger) != trigger) return@forEachIndexed
            val stanceGate = abilityDef.stance
            if (stanceGate != null &&
                !stanceGate.equals(com.fiw.tools.stance.StanceHandler.activeStance(stack, def), ignoreCase = true)) return@forEachIndexed
            if (abilityFilter != null && !abilityFilter(abilityDef)) return@forEachIndexed
            val key = AbilityCooldownTracker.keyFor(def.id, idx, abilityDef.cooldownGroup)
            if (!AbilityCooldownTracker.isReady(player, key)) return@forEachIndexed
            if (!PassiveConditions.met(player, world, abilityDef.params)) return@forEachIndexed
            val cost = abilityDef.cost
            if (cost != null && !AbilityCosts.canPay(player, stack, def, cost)) {
                if (trigger == AbilityTrigger.ON_RIGHT_CLICK || trigger == AbilityTrigger.ON_SHIFT_RIGHT_CLICK ||
                    trigger == AbilityTrigger.ON_ATTACK) {
                    AbilityCosts.notifyInsufficient(player)
                }
                return@forEachIndexed
            }
            eligible.add(idx)
        }
        if (eligible.isEmpty()) return false

        // Pass 2: resolve chance + proc groups into the winners that actually fire.
        val winners = ProcGroups.resolve(eligible.map {
            ProcGroups.Candidate(it, abilities[it].chance, abilities[it].procGroup)
        })

        var any = false
        for (idx in eligible) {
            if (idx !in winners) continue
            val abilityDef = abilities[idx]
            // Re-check silently: an earlier winner in this same dispatch may have drained the pool.
            val cost = abilityDef.cost
            if (cost != null && !AbilityCosts.canPay(player, stack, def, cost)) continue
            val ability = AbilityRegistry.get(abilityDef.type) ?: run {
                logger.warn("Unknown ability type '${abilityDef.type}' on '${def.id}'")
                continue
            }
            val key = AbilityCooldownTracker.keyFor(def.id, idx, abilityDef.cooldownGroup)
            val params = AbilityScaling.resolve(player, stack, def, abilityDef)
            try {
                if (ability.execute(AbilityContext(player, stack, world, target, targetPos, params, damageSource, damageAmount))) {
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
