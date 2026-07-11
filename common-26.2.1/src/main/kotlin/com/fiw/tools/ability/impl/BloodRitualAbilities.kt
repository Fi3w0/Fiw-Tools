package com.fiw.tools.ability.impl

import com.fiw.tools.ability.Ability
import com.fiw.tools.ability.AbilityContext
import com.fiw.tools.ability.AbilityState
import com.fiw.tools.ability.AffectScope
import com.fiw.tools.ability.ChannelHandler
import com.fiw.tools.ability.collectTargets
import com.fiw.tools.ability.optD
import com.fiw.tools.ability.optF
import com.fiw.tools.ability.optI
import com.fiw.tools.ability.scope
import com.fiw.tools.util.TextParser
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import kotlin.math.cos
import kotlin.math.sin

/**
 * `blood_debt` — a two-part risk mechanic driven by a `mode` param.
 *
 *  - `mode: "bank"` (best on right-click / shift-right-click): pay `healthCost` current HP to bank
 *    `charges` blood charges. They must be **spent within `windowTicks`** or the debt collects —
 *    every unspent charge backlashes for `backlashPerCharge`.
 *  - `mode: "spend"` (best on `on_attack`): each hit consumes one banked charge for `bonusDamage`
 *    (plus `lifesteal` healing).
 *
 * The skill is the loop: open a debt when you can afford the HP, then land enough hits to clear it
 * before the window closes — overcommit and the collector kills you.
 */
object BloodDebtAbility : Ability {
    override fun execute(ctx: AbilityContext): Boolean {
        val mode = ctx.params.get("mode")?.takeIf { it.isJsonPrimitive }?.asString?.lowercase() ?: "bank"
        return if (mode == "spend") spend(ctx) else bank(ctx)
    }

    private fun bank(ctx: AbilityContext): Boolean {
        val p = ctx.params
        val player = ctx.player
        val world = ctx.world
        val healthCost = p.optF("healthCost", 6f).coerceAtLeast(0f)
        // Never let the sacrifice itself kill you — refuse if you can't spare the blood.
        if (player.health <= healthCost + 1f) {
            player.sendOverlayMessage(TextParser.parse("&cNot enough blood to spare."))
            return false
        }
        val charges = p.optI("charges", 3).coerceIn(1, 64)
        val window = p.optI("windowTicks", 120).coerceIn(20, 1200)
        val backlash = p.optF("backlashPerCharge", 4f).coerceAtLeast(0f)

        player.health = (player.health - healthCost).coerceAtLeast(0.5f)
        AbilityState.addBloodDebt(player.uuid, charges, world.gameTime + window, backlash)

        val total = AbilityState.bloodDebtCharges(player.uuid)
        player.sendOverlayMessage(TextParser.parse("&4Blood Debt: &c$total&4 charge(s) — spend before it collects."))
        world.sendParticles(ParticleTypes.DAMAGE_INDICATOR, player.x, player.y + 1.0, player.z, 20, 0.4, 0.6, 0.4, 0.05)
        world.playSound(null, player.x, player.y, player.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.8f, 0.5f)
        return true
    }

    private fun spend(ctx: AbilityContext): Boolean {
        val target = ctx.target ?: return false
        if (!AbilityState.spendBloodDebt(ctx.player.uuid)) return false
        val p = ctx.params
        val world = ctx.world
        val bonus = p.optF("bonusDamage", 4f)
        val lifesteal = p.optF("lifesteal", 2f)
        if (bonus > 0f) target.hurtServer(world, world.damageSources().indirectMagic(ctx.player, ctx.player), bonus)
        if (lifesteal > 0f) ctx.player.heal(lifesteal)
        world.sendParticles(ParticleTypes.DAMAGE_INDICATOR, target.x, target.y + target.bbHeight * 0.5, target.z, 10, 0.2, 0.3, 0.2, 0.1)
        world.playSound(null, target.x, target.y, target.z, SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 0.7f, 0.6f)
        return true
    }
}
/**
 * `exsanguinate` — a channelled blood nova. Hold sneak to bleed yourself over `channelTicks`; the
 * more you drain, the bigger the burst on completion. Landing it returns more blood than you spent
 * (`bloodReturn`); getting interrupted (moved / hit / un-sneaked) wastes everything you'd drained.
 * The skill is committing to a long, exposed channel while low on HP and protecting it.
 */
object ExsanguinateAbility : Ability {
    override fun execute(ctx: AbilityContext): Boolean {
        if (ChannelHandler.isChanneling(ctx.player.uuid)) return false
        val p = ctx.params
        val channelTicks = p.optI("channelTicks", 60).coerceIn(10, 400)
        val pulse = p.optI("drainInterval", 10).coerceAtLeast(2)
        val drainPer = p.optF("drainPerPulse", 1f)
        val radius = p.optD("radius", 5.0)
        val baseDamage = p.optF("baseDamage", 4f)
        val perDrain = p.optF("damagePerDrain", 1.5f)
        val bloodReturn = p.optF("bloodReturn", 1.5f)
        val scope = p.scope("affects", AffectScope.ALL)
        val drained = floatArrayOf(0f)

        return ChannelHandler.start(
            player = ctx.player, durationTicks = channelTicks, requireSneak = true,
            maxMove = p.optD("maxMove", 1.5), label = "&4Exsanguinating",
            particle = ParticleTypes.DAMAGE_INDICATOR,
            onProgress = { pl, elapsed ->
                if (elapsed > 0 && elapsed % pulse == 0) {
                    val take = minOf(drainPer, (pl.health - 1f).coerceAtLeast(0f))
                    if (take > 0f) { pl.health -= take; drained[0] += take }
                }
            },
            onComplete = { pl ->
                val world = pl.level()
                val dmg = baseDamage + perDrain * drained[0]
                val src = world.damageSources().indirectMagic(pl, pl)
                var hits = 0
                for (e in collectTargets(world, pl.position(), radius, pl, scope)) { e.hurtServer(world, src, dmg); hits++ }
                if (hits > 0 && bloodReturn > 0f) pl.heal(drained[0] * bloodReturn)
                world.sendParticles(ParticleTypes.DAMAGE_INDICATOR, pl.x, pl.y + 1.0, pl.z, 60, radius * 0.5, 0.8, radius * 0.5, 0.1)
                world.playSound(null, pl.x, pl.y, pl.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 0.9f, 1.2f)
            }
        )
    }
}

/**
 * `blood_circle` — a full ritual. Sneak-right-click (pay any `cost` offering) to inscribe a circle and
 * channel it standing still. On completion enemies inside take `damage` + Weakness/Wither and allies
 * (you included) gain Regeneration + Strength for `boonTicks`. Breaking the channel fizzles it with a
 * `backlash`. The skill is protecting a long channel out in the open.
 */
object BloodCircleAbility : Ability {
    override fun execute(ctx: AbilityContext): Boolean {
        if (ChannelHandler.isChanneling(ctx.player.uuid)) return false
        val p = ctx.params
        val channelTicks = p.optI("channelTicks", 80).coerceIn(20, 400)
        val radius = p.optD("radius", 5.0)
        val damage = p.optF("damage", 8f)
        val boonTicks = p.optI("boonTicks", 200)
        val backlash = p.optF("backlash", 4f)
        val anchor = ctx.player.position()

        return ChannelHandler.start(
            player = ctx.player, durationTicks = channelTicks, requireSneak = true, maxMove = 1.0,
            label = "&4&lBlood Circle", particle = ParticleTypes.SOUL_FIRE_FLAME,
            onProgress = { pl, elapsed ->
                val world = pl.level()
                val frac = elapsed.toDouble() / channelTicks
                for (i in 0 until 24) {
                    if (i / 24.0 > frac) continue
                    val a = i / 24.0 * Math.PI * 2 + elapsed * 0.04
                    world.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, anchor.x + cos(a) * radius, anchor.y + 0.1, anchor.z + sin(a) * radius, 1, 0.0, 0.0, 0.0, 0.0)
                }
            },
            onInterrupt = { pl ->
                if (backlash > 0f) {
                    pl.invulnerableTime = 0
                    pl.hurtServer(pl.level(), pl.level().damageSources().magic(), backlash)
                }
            },
            onComplete = { pl ->
                val world = pl.level()
                val src = world.damageSources().magic()
                for (e in collectTargets(world, anchor, radius, pl, AffectScope.MOBS)) {
                    if (damage > 0f) e.hurtServer(world, src, damage)
                    e.addEffect(MobEffectInstance(MobEffects.WEAKNESS, boonTicks, 1, false, true))
                    e.addEffect(MobEffectInstance(MobEffects.WITHER, boonTicks, 0, false, true))
                }
                val allies = collectTargets(world, anchor, radius, pl, AffectScope.ALLIES).toMutableList()
                allies.add(pl)
                for (ally in allies) {
                    ally.addEffect(MobEffectInstance(MobEffects.REGENERATION, boonTicks, 1, false, true))
                    ally.addEffect(MobEffectInstance(MobEffects.STRENGTH, boonTicks, 0, false, true))
                }
                world.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, anchor.x, anchor.y + 0.5, anchor.z, 80, radius * 0.6, 0.4, radius * 0.6, 0.05)
                world.playSound(null, anchor.x, anchor.y, anchor.z, SoundEvents.EVOKER_CAST_SPELL, SoundSource.PLAYERS, 1.0f, 0.6f)
            }
        )
    }
}
