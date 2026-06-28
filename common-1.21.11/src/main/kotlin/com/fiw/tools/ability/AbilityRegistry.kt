package com.fiw.tools.ability

import com.fiw.tools.ability.impl.AdrenalineAbility
import com.fiw.tools.ability.impl.BleedAbility
import com.fiw.tools.ability.impl.BlizzardAbility
import com.fiw.tools.ability.impl.BloodPactAbility
import com.fiw.tools.ability.impl.DecayAuraAbility
import com.fiw.tools.ability.impl.EmberAuraAbility
import com.fiw.tools.ability.impl.FlameDashAbility
import com.fiw.tools.ability.impl.GlacialShellAbility
import com.fiw.tools.ability.impl.HemorrhageAbility
import com.fiw.tools.ability.impl.HolderDebuffAbility
import com.fiw.tools.ability.impl.IceLanceAbility
import com.fiw.tools.ability.impl.IgniteAbility
import com.fiw.tools.ability.impl.MeteorStrikeAbility
import com.fiw.tools.ability.impl.SanguineStrikeAbility
import com.fiw.tools.ability.impl.SoulCollectorAbility
import com.fiw.tools.ability.impl.SoulSurgeAbility
import com.fiw.tools.ability.impl.TidalSurgeAbility
import com.fiw.tools.ability.impl.FreezeAbility
import com.fiw.tools.ability.impl.SoakAbility
import com.fiw.tools.ability.impl.ShockAbility
import com.fiw.tools.ability.impl.ThawBurstAbility
import com.fiw.tools.ability.impl.StormChainAbility
import com.fiw.tools.ability.impl.WitherTouchAbility
import com.fiw.tools.ability.impl.AquaKitAbility
import com.fiw.tools.ability.impl.ArcSlashAbility
import com.fiw.tools.ability.impl.BeaconAuraAbility
import com.fiw.tools.ability.impl.BeaconPingAbility
import com.fiw.tools.ability.impl.BerserkerAbility
import com.fiw.tools.ability.impl.BlindingFlashAbility
import com.fiw.tools.ability.impl.BlinkAbility
import com.fiw.tools.ability.impl.ChillAuraAbility
import com.fiw.tools.ability.impl.CombatFocusAbility
import com.fiw.tools.ability.impl.CowardMarkAbility
import com.fiw.tools.ability.impl.CursePulseAbility
import com.fiw.tools.ability.impl.ChainLightningAbility
import com.fiw.tools.ability.impl.FeatherweightAbility
import com.fiw.tools.ability.impl.HornetSwarmAbility
import com.fiw.tools.ability.impl.ImbueAbility
import com.fiw.tools.ability.impl.LastStandAbility
import com.fiw.tools.ability.impl.LifelineAbility
import com.fiw.tools.ability.impl.MagnetAbility
import com.fiw.tools.ability.impl.MendingAuraAbility
import com.fiw.tools.ability.impl.PassiveBuffAbility
import com.fiw.tools.ability.impl.RallyAuraAbility
import com.fiw.tools.ability.impl.RepulseWardAbility
import com.fiw.tools.ability.impl.SaturationAuraAbility
import com.fiw.tools.ability.impl.ShieldBatteryAbility
import com.fiw.tools.ability.impl.SporeCloudAbility
import com.fiw.tools.ability.impl.StaticFieldAbility
import com.fiw.tools.ability.impl.ThermalWardAbility
import com.fiw.tools.ability.impl.ThornPulseAbility
import com.fiw.tools.ability.impl.UncurseAbility
import com.fiw.tools.ability.impl.CleaveAbility
import com.fiw.tools.ability.impl.DisarmAbility
import com.fiw.tools.ability.impl.EnderRecallAbility
import com.fiw.tools.ability.impl.ExecuteAbility
import com.fiw.tools.ability.impl.FeatherFallAbility
import com.fiw.tools.ability.impl.FireworkBurstAbility
import com.fiw.tools.ability.impl.FrostNovaAbility
import com.fiw.tools.ability.impl.GlowMarkAbility
import com.fiw.tools.ability.impl.GrapplingPullAbility
import com.fiw.tools.ability.impl.GravityWellAbility
import com.fiw.tools.ability.impl.GroundSlamAbility
import com.fiw.tools.ability.impl.HealOnHitAbility
import com.fiw.tools.ability.impl.HealingTotemAbility
import com.fiw.tools.ability.impl.LeechAbility
import com.fiw.tools.ability.impl.LevitateSelfAbility
import com.fiw.tools.ability.impl.LightningStrikeAbility
import com.fiw.tools.ability.impl.ParryCounterAbility
import com.fiw.tools.ability.impl.PhaseDashAbility
import com.fiw.tools.ability.impl.PrankSwapAbility
import com.fiw.tools.ability.impl.ProjectileBurstAbility
import com.fiw.tools.ability.impl.RallyBannerAbility
import com.fiw.tools.ability.impl.RiptideDashAbility
import com.fiw.tools.ability.impl.SecondWindAbility
import com.fiw.tools.ability.impl.ShockwaveAbility
import com.fiw.tools.ability.impl.SilenceSigilAbility
import com.fiw.tools.ability.impl.SlayingEdgeAbility
import com.fiw.tools.ability.impl.SoulHarvestAbility
import com.fiw.tools.ability.impl.TauntAbility
import com.fiw.tools.ability.impl.TetherAbility
import com.fiw.tools.ability.impl.WhirlwindAbility

object AbilityRegistry {
    private val factories: MutableMap<String, () -> Ability> = HashMap()

    fun init() {
        // Original set
        register("riptide_dash") { RiptideDashAbility }
        register("arc_slash") { ArcSlashAbility }
        register("lightning_strike") { LightningStrikeAbility }
        register("shockwave") { ShockwaveAbility }
        register("heal_on_hit") { HealOnHitAbility }
        register("blink") { BlinkAbility }
        register("projectile_burst") { ProjectileBurstAbility }
        register("frost_nova") { FrostNovaAbility }

        // PvP
        register("grappling_pull") { GrapplingPullAbility }
        register("disarm") { DisarmAbility }
        register("parry_counter") { ParryCounterAbility }
        register("execute") { ExecuteAbility }
        register("leech") { LeechAbility }
        register("silence_sigil") { SilenceSigilAbility }
        register("tether") { TetherAbility }

        // PvE
        register("cleave") { CleaveAbility }
        register("whirlwind") { WhirlwindAbility }
        register("slaying_edge") { SlayingEdgeAbility }
        register("soul_harvest") { SoulHarvestAbility }
        register("chain_lightning") { ChainLightningAbility }
        register("gravity_well") { GravityWellAbility }
        register("ground_slam") { GroundSlamAbility }

        // Utility
        register("phase_dash") { PhaseDashAbility }
        register("feather_fall") { FeatherFallAbility }
        register("second_wind") { SecondWindAbility }
        register("ender_recall") { EnderRecallAbility }
        register("levitate_self") { LevitateSelfAbility }

        // Support / social
        register("rally_banner") { RallyBannerAbility }
        register("taunt") { TauntAbility }
        register("healing_totem") { HealingTotemAbility }
        register("beacon_ping") { BeaconPingAbility }
        register("firework_burst") { FireworkBurstAbility }
        register("glow_mark") { GlowMarkAbility }
        register("prank_swap") { PrankSwapAbility }

        // Passives — held self-buffs (re-applied each sweep; leave cooldownTicks at 0 for steady buffs)
        register("passive_buff") { PassiveBuffAbility }
        register("featherweight") { FeatherweightAbility }
        register("aqua_kit") { AquaKitAbility }
        register("thermal_ward") { ThermalWardAbility }
        register("saturation_aura") { SaturationAuraAbility }
        register("magnet") { MagnetAbility }
        register("berserker") { BerserkerAbility }
        register("combat_focus") { CombatFocusAbility }
        register("lifeline") { LifelineAbility }

        // Passives — auras to nearby allies
        register("rally_aura") { RallyAuraAbility }
        register("beacon_aura") { BeaconAuraAbility }
        register("mending_aura") { MendingAuraAbility }

        // Passives — reactive self-defense (use cooldownTicks)
        register("static_field") { StaticFieldAbility }
        register("repulse_ward") { RepulseWardAbility }
        register("chill_aura") { ChillAuraAbility }
        register("blinding_flash") { BlindingFlashAbility }
        register("spore_cloud") { SporeCloudAbility }
        register("thorn_pulse") { ThornPulseAbility }
        register("coward_mark") { CowardMarkAbility }
        register("hornet_swarm") { HornetSwarmAbility }
        register("curse_pulse") { CursePulseAbility }

        // Passives — conditional survival
        register("last_stand") { LastStandAbility }
        register("adrenaline") { AdrenalineAbility }
        register("shield_battery") { ShieldBatteryAbility }

        // Active debuffs
        register("ignite") { IgniteAbility }
        register("wither_touch") { WitherTouchAbility }
        register("bleed") { BleedAbility }

        // Passive debuffs on holder (balance tools)
        register("holder_debuff") { HolderDebuffAbility }

        // Passive zone debuffs
        register("decay_aura") { DecayAuraAbility }
        register("ember_aura") { EmberAuraAbility }

        // Ice
        register("ice_lance") { IceLanceAbility }
        register("blizzard") { BlizzardAbility }
        register("glacial_shell") { GlacialShellAbility }

        // Water
        register("tidal_surge") { TidalSurgeAbility }

        // Fire
        register("flame_dash") { FlameDashAbility }
        register("meteor_strike") { MeteorStrikeAbility }

        // Blood
        register("blood_pact") { BloodPactAbility }
        register("hemorrhage") { HemorrhageAbility }
        register("sanguine_strike") { SanguineStrikeAbility }

        // Soul system
        register("soul_collector") { SoulCollectorAbility }
        register("soul_surge") { SoulSurgeAbility }

        // Elemental status appliers
        register("freeze") { FreezeAbility }
        register("soak") { SoakAbility }
        register("shock") { ShockAbility }

        // Elemental interactions (consume status for payoff)
        register("thaw_burst") { ThawBurstAbility }
        register("storm_chain") { StormChainAbility }

        // Curse system
        register("uncurse") { UncurseAbility }

        // Imbuement system
        register("imbue") { ImbueAbility }
    }

    fun register(type: String, factory: () -> Ability) {
        factories[type.lowercase()] = factory
    }

    fun get(type: String): Ability? = factories[type.lowercase()]?.invoke()

    fun knownTypes(): Set<String> = factories.keys
}
