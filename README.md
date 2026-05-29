# FIW Tools

> Custom Item Framework · Fabric 1.21.11 · JSON-Driven · Server-Side Only

[![Modrinth](https://img.shields.io/modrinth/v/fiw-tools?label=Modrinth&logo=modrinth&color=00AF5C)](https://modrinth.com/mod/fiw-tools)
[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.11-62B47A)](https://minecraft.net)
[![Fabric](https://img.shields.io/badge/Fabric-1.21.11-DBB591)](https://fabricmc.net)
[![License](https://img.shields.io/badge/License-CC%20BY--NC--SA%204.0-blue)](https://creativecommons.org/licenses/by-nc-sa/4.0/)

A data-driven custom-item framework — define fully custom items, weapons, and armor entirely through JSON, using vanilla items as the base. No coding, no client install, no restarts. Drop a config, run `/fiwtools reload`, and your item is live.

Designed as the sibling mod to [FIW Bosses](https://github.com/Fi3w0/Fiw-Bosses) — they work standalone or together. With both installed, bosses can drop or wear custom Fiw Tools items by id.

## Supported versions

| Loader | Minecraft | Status | Branch |
|---|---|---|---|
| **Fabric** | **1.21.11** | **Active** — backport from the 26.x line | [`fabric-1.21.11`](https://github.com/Fi3w0/Fiw-Tools/tree/fabric-1.21.11) |
| **Fabric** | **26.1.2** | Active — primary development target | [`main`](https://github.com/Fi3w0/Fiw-Tools) |

NeoForge and earlier Minecraft versions are not currently supported. The codebase only touches vanilla data components and Fabric API events, so a future port is possible but not planned.

---

## Features

- **JSON-driven** — define any item without touching a single line of code
- **Fully server-side** — vanilla 1.21.11 clients connect with no install required
- **Vanilla data components** — custom name, lore, rarity, attributes, enchantments (uncapped levels), durability, food, tool, hide-tooltip flags, free-form NBT
- **8 player-balanced abilities** — right-click cast, on-attack proc, on-hurt counter, on-kill, on-block-break — all configurable
- **Animated arc slash** — multi-tick layered particle sweep ported from Fiw Bosses
- **Keep on death** — flagged items survive respawn even when `keepInventory` is off
- **Hot reload** — `/fiwtools reload` swaps the registry atomically without a server restart
- **Tab completion** — every command tab-completes loaded item ids
- **Public API** — `FiwToolsAPI.getItemStack(id, server)` for other mods to look up items
- **Pairs with Fiw Bosses** — bosses can drop or equip Fiw Tools items by id (soft dependency, silently degrades when one mod is missing)

---

## Requirements

| Dependency | Version |
|---|---|
| Minecraft | 1.21.11 |
| Fabric Loader | 0.19.2+ |
| Fabric API | 0.141.4+1.21.11 or newer |
| Fabric Language Kotlin | latest (Kotlin 2.3.21) |
| Java | 21 |
| Client-side required | No |

---

## Installation

1. Install [Fabric Loader](https://fabricmc.net/use/installer/) for Minecraft 1.21.11
2. Install [Fabric API](https://modrinth.com/mod/fabric-api)
3. Install [Fabric Language Kotlin](https://modrinth.com/mod/fabric-language-kotlin)
4. Drop the Fiw Tools jar (`fiw-tools-fabric-1.21.11-<version>.jar`) into your `mods/` folder
5. Start the server — configs generate automatically in `config/fiw_tools/items/`

To get started, copy any JSON from [`examples/items/`](examples/items/) into `config/fiw_tools/items/`, then run `/fiwtools reload` in-game.

---

## Commands

```
/fiwtools give <players> <itemId>           — give one item to one or more players
/fiwtools give <players> <itemId> <count>   — give a stack
/fiwtools list                              — list all loaded item ids
/fiwtools info <itemId>                     — print a summary of the item's definition
/fiwtools reload                            — reload all item configs without restart
```

Permission level: 2 (operator). Tab-complete works on item ids.

---

## Customization

Every item JSON is a flat object. All fields except `id` and `base` are optional.

| Section | Fields |
|---|---|
| Identity | `id`, `base`, `displayName`, `lore`, `rarity` |
| Stack | `stackSize`, `repairCost` |
| Durability | `unbreakable`, `maxDamage`, `damage` |
| Visual | `enchantmentGlint`, `hideFlags` |
| Power | `enchantments` (uncapped levels), `attributes` (per-slot, every operation) |
| Persistence | `keepOnDeath`, `customData` |
| Use | `food`, `tool` |
| Behavior | `abilities` |

Color codes use `&` prefix: `&0`–`&f` for colors, `&l`/`&o`/`&n`/`&m`/`&k`/`&r` for styles — same syntax as Fiw Bosses.

Full schema, every field, every default, every example — see **[ITEM_CONFIG_DOCS.md](ITEM_CONFIG_DOCS.md)**.

> **Note on attributes:** in Minecraft 1.21.11, attribute IDs dropped the `generic.` / `player.` prefix. Use `minecraft:attack_damage` and `minecraft:armor`, not `minecraft:generic.attack_damage`.

---

## Abilities

| Ability | Description |
|---|---|
| `arc_slash` | Animated multi-tick blade sweep — 5 layered particle types, configurable arc / radius / roll / height |
| `riptide_dash` | Forward dash in look direction with a small upward kick — works on dry land |
| `lightning_strike` | Cosmetic lightning at the target on attack with bonus damage — no fires, no pig conversion by default |
| `shockwave` | Ground ring around the player — entities in radius take damage and knockback |
| `heal_on_hit` | Player heals a small amount when they damage something |
| `blink` | Short teleport in look direction, clamped to safe blocks |
| `projectile_burst` | Particle projectile travels forward, deals AoE damage on impact or at max range |
| `frost_nova` | AoE around the player applies Slowness and small magic damage |

**Triggers:** `on_right_click`, `on_attack`, `on_kill`, `on_hurt`, `on_block_break`.

Every ability supports `cooldownTicks` (per-player), `chance` (0.0–1.0), and a `params` block with ability-specific knobs (range, damage, particles, sound, etc.). Cooldowns are tracked per-player per-ability-slot, so dual-wielding two copies of the same item shares one cooldown for that ability.

---

## Pair with Fiw Bosses

When both mods are installed, [Fiw Bosses](https://github.com/Fi3w0/Fiw-Bosses) can reference Fiw Tools items by id from its loot tables and equipment slots:

```json
// boss loot entry
{ "toolId": "soul_blade", "count": 1, "chance": 0.25 }

// boss equipment slot
"mainHand": { "toolId": "soul_blade" }
```

If Fiw Tools is missing the lookup is silently skipped — boss configs never crash. Each mod also works fully on its own.

The integration goes through a stable static API (`FiwToolsAPI.getItemStack(id, server, count)`) that any other mod is free to consume the same way.

---

## Documentation

Full configuration reference — every field, every ability parameter, every component, full examples:

**[ITEM_CONFIG_DOCS.md](ITEM_CONFIG_DOCS.md)**

---

## Included Examples

Pre-built items live in [`examples/items/`](examples/items/) — copy any into `config/fiw_tools/items/` to use them.

| Item | Style | Highlights |
|---|---|---|
| `legendary_sword` | Weapon | Netherite sword, +11 damage, sharpness 6 + fire aspect 2, arc_slash on right-click |
| `storm_axe` | Weapon | Diamond axe, lightning_strike 35% on attack, shockwave on right-click |
| `riptide_trident` | Weapon | Trident, riptide 3 + impaling 4, riptide_dash on right-click |
| `mythic_helmet` | Armor | Golden helmet, +5 armor, +0.2 KB resist, keep on death |
| `soul_apple` | Food | Apple base, nutrition 6, saturation 8, always-edible |

---

## Issues & Feedback

Found a bug or have a feature request? Open an issue:

**[Issue Tracker](https://github.com/Fi3w0/Fiw-Tools/issues)**

---

## Building from Source

```bash
git clone -b fabric-1.21.11 https://github.com/Fi3w0/Fiw-Tools
cd Fiw-Tools
./gradlew build
```

Output JAR in `build/libs/fiw-tools-fabric-1.21.11-<version>.jar`. Requires Java 21.

---

## Known Issues

- Cooldowns reset on player disconnect or server restart (intentional — kept simple, avoids NBT bloat).
- Food effects beyond nutrition / saturation / always-edible are not yet implemented.
- Items already in player inventories are not retroactively updated when their JSON changes — re-give to refresh.
- Modded enchantments and attributes work via their full registry id but are not extensively tested.
- A tiny Java helper (`HolderAccess`) exists to unwrap `Holder.value()` because Kotlin's synthetic-property resolution collides with the private `value` field on `Holder.Reference` in 1.21.11 Mojang mappings.

---

## License

This project is licensed under **Creative Commons Attribution-NonCommercial-ShareAlike 4.0 International (CC BY-NC-SA 4.0)**.

You are free to:
- **Share** — copy and redistribute the material in any medium or format
- **Adapt** — remix, transform, and build upon the material

Under the following terms:
- **Attribution** — you must give appropriate credit, provide a link to the original repository (https://github.com/Fi3w0/Fiw-Tools), and indicate if changes were made.
- **NonCommercial** — you may not use the material for commercial purposes.
- **ShareAlike** — if you remix, transform, or build upon the material, you must distribute your contributions under the same license as the original.

Full license text: https://creativecommons.org/licenses/by-nc-sa/4.0/

For uses outside these terms (including commercial use), open an issue or contact Fi3w0 to request permission.

---

*Made by Fi3w0 — built for my SMP, shared with everyone who asks first.*
