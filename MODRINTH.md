<div align="center">

# FIW Tools

### Build custom items from JSON.

**Weapons. Armor. Food. Curses. Imbuements. Passives. Player abilities.**
No Java. No restarts. Fabric and NeoForge builds are both supported.

[![Modrinth](https://img.shields.io/modrinth/v/fiw-tools?label=Modrinth&logo=modrinth&color=00AF5C)](https://modrinth.com/mod/fiw-tools)
[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.11_·_26.1.2-62B47A)](https://minecraft.net)
[![Fabric](https://img.shields.io/badge/Fabric-✓-DBB591)](https://fabricmc.net)
[![NeoForge](https://img.shields.io/badge/NeoForge-✓-F16436)](https://neoforged.net)

</div>

---

## The pitch

FIW Tools gives server owners a JSON item framework: drop a file into `config/fiw_tools/items/`, run `/fiwtools reload`, and a custom item is live.

It is not a fixed item pack. It is a toolkit for building your own progression: custom display, lore, attributes, enchantments above vanilla caps, durability, food, tool rules, active abilities, passive armor effects, curses, imbuements, and persistent upgrades.

It also pairs with **FIW Bosses**. Bosses can equip or drop Fiw Tools items by id through `toolId`, and the boss mod safely skips those entries if Fiw Tools is not installed.

FIW Tools is server-side only — it has no client-side code. Install it on the server (Fabric or NeoForge); players do not need the mod to join. On Fabric, even fully vanilla clients can connect.

---

## Sixty seconds to your first item

`config/fiw_tools/items/storm_blade.json`

```json
{
  "id": "storm_blade",
  "base": "minecraft:diamond_sword",
  "displayName": "&b&lStorm Blade",
  "lore": ["&7Right-click to call lightning."],
  "rarity": "epic",
  "abilities": [
    {
      "type": "lightning_strike",
      "trigger": "on_right_click",
      "cooldownTicks": 120,
      "params": { "range": 18, "damage": 10 }
    }
  ]
}
```

```text
/fiwtools reload
/fiwtools give @s storm_blade
```

Tweak the file, reload, try again.

---

## What you can build

**Custom gear** - turn vanilla bases into named weapons, armor, trinkets, tools, food, and catalysts.

**Active abilities** - right-click casts, attack procs, kill effects, hurt reactions, and block-break effects.

**Elemental status** - apply FROZEN, SOAKED, or SHOCKED to enemies. Stack statuses for interactions: freeze then thaw_burst for a fire explosion, soak then storm_chain to arc lightning between targets.

**Set bonuses** - tag items with a `resonanceId`. Equip enough pieces and extra passive abilities unlock automatically.

**Soul system** - items can store souls on kill. Spend them all at once for a burst of scaled AoE damage.

**Passive equipment** - held or worn buffs, ally auras, defensive pulses, emergency survival triggers, and conditional effects.

**Cursed items** - punish non-whitelisted holders, scan ender chests, block grindstone/anvil abuse, and prevent world placement.

**Imbuements** - catalyst-based upgrades with weighted outcomes, charges, target caps, stat changes, new abilities, and history that survives reloads.

**Hot reload** - update configs live while preserving player changes like durability, renames, extra enchants, uncurse flags, and imbuement logs.

---

## Ability highlights

| Ability | What it does |
|---|---|
| `arc_slash` | Animated particle slash, ported from FIW Bosses |
| `lightning_strike` | Calls lightning or damage at range |
| `projectile_burst` | Custom particle projectile with optional AoE |
| `frost_nova` | Freezes nearby enemies |
| `riptide_dash` | Movement burst in the look direction |
| `chain_lightning` | Bounces damage between targets |
| `gravity_well` | Pulling zone effect |
| `last_stand` | Low-health survival passive |
| `uncurse` | Consumable curse removal |
| `imbue` | Catalyst upgrade engine |
| `freeze` / `soak` / `shock` | Apply elemental status; each has passive tick effects |
| `thaw_burst` | Consume FROZEN for a fire AoE explosion around the target |
| `storm_chain` | Consume SOAKED to arc lightning damage to nearby enemies |
| `soul_collector` | Absorb a soul on kill; stored in the item up to `soulCapacity` |
| `soul_surge` | Spend all stored souls for scaled AoE magic damage |
| `blood_pact` | Sacrifice HP to multiply next attack damage |
| `sanguine_strike` | Lifesteal that scales with the target's missing HP |
| `hemorrhage` | On-hurt: inflict bleed DoT on whoever hit you |
| `flame_dash` | Teleport forward, igniting enemies along the path |
| `meteor_strike` | Raycast impact AoE at cursor position |

Full schema and every ability parameter are in the documentation.

---

## Supported versions

| Loader | Minecraft | Java |
|---|---|---|
| Fabric | 1.21.11 | 21 |
| NeoForge | 1.21.11 | 21 |
| Fabric | 26.1.2 | 25 |

Fabric builds need [Fabric API](https://modrinth.com/mod/fabric-api) and [Fabric Language Kotlin](https://modrinth.com/mod/fabric-language-kotlin).

NeoForge builds include the Kotlin runtime inside the FIW Tools jar. Install it on the NeoForge server only; clients do not need the mod, and no separate KotlinForForge mod is required.

The repo module is named `fabric-26.2.1` as requested, but Fabric's official metadata currently rejects the `26.2.1` game coordinate. The buildable target is `26.1.2` until Fabric publishes a valid `26.2.1` coordinate.

---

## Commands

```text
/fiwtools give <players> <itemId> [count]   /fiwtools list
/fiwtools info <itemId>                     /fiwtools reload
/fiwtools curse add|remove|list ...
/fiwtools uncurse_held
/fiwtools imbue best|roll|reset|clear|log
```

---

<div align="center">

Made by **Fiw** for custom SMP item progression.

</div>
