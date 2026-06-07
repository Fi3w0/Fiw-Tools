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
| **Fabric** | **1.21.11** | **Active — primary development target** | [`fabric-1.21.11`](https://github.com/Fi3w0/Fiw-Tools/tree/fabric-1.21.11) |
| **Fabric** | **26.1.2** | Earlier release branch — not the version actively worked on | [`main`](https://github.com/Fi3w0/Fiw-Tools) |

NeoForge and earlier Minecraft versions are not currently supported. The codebase only touches vanilla data components and Fabric API events, so a future port is possible but not planned.

---

## Features

- **JSON-driven** — define any item without touching a single line of code
- **Fully server-side** — vanilla 1.21.11 clients connect with no install required
- **Vanilla data components** — custom name, lore, rarity, attributes, enchantments (uncapped levels), durability, food, tool, hide-tooltip flags, free-form NBT
- **60 player-balanced abilities** — across PvP, PvE, utility, team-support and **passives** (held buffs, allied auras, reactive defense, conditional survival), plus admin tools `uncurse` and `imbue`; all configurable, each gated by per-player cooldown, `chance`, and an `affects` PvP-vs-PvE filter
- **7 triggers** — right-click, on-attack, on-kill, on-hurt, on-block-break, **`while_held`** (passive sweep with health/combat/underwater/enemy-near gates), and **`while_worn`** (the same passive engine on each of the four armor slots, with a per-slot filter)
- **Worn armor passives** — every passive ability also runs in armor slots; mix held + worn passives on the same item
- **Curse system** — mark an item cursed with a player whitelist. Non-whitelisted holders take armor- and resistance-bypassing `fiw_tools:curse` damage until they drop it or die; optionally **scans the ender chest** so stashing it there doesn't save them. Cursed items can't be renamed, repaired, enchanted, grindstoned, or placed. A consumable Blessed Scroll lifts a curse via the `uncurse` ability
- **Imbuing system** — catalysts upgrade items: enchant levels above the vanilla cap, extra attributes, new abilities, rewritten lore/name. Outcomes can be **deterministic** or **weighted-random** (rare god-rolls and "cracked" downgrades included). Catalysts carry a `maxUses` charge; targets a separate `imbueLimit`. Imbuements replay on top of fresh builds at reload, so player upgrades survive config edits
- **Animated arc slash** — multi-tick layered particle sweep ported from Fiw Bosses
- **Keep on death** — flagged items survive respawn even when `keepInventory` is off (cursed items always drop, so the curse can't be cheesed by suicide)
- **Hot reload** — `/fiwtools reload` swaps the registry atomically without a server restart; player enchants, anvil renames, uncurse flags, and imbuements all survive the swap
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
/fiwtools give <players> <itemId> [count]   — give an item (optionally a stack)
/fiwtools list                              — list all loaded item ids
/fiwtools info <itemId>                     — print a summary of the item's definition
/fiwtools reload                            — reload all item configs without restart

/fiwtools curse add <itemId>                — mark an item cursed (rewrites its JSON in place)
/fiwtools curse remove <itemId>             — unmark an item cursed
/fiwtools curse list                        — list cursed item ids
/fiwtools curse whitelist add <itemId> <player>    — exempt a player from the curse
/fiwtools curse whitelist remove <itemId> <player> — remove a player from the whitelist
/fiwtools uncurse_held                       — flag the held main-hand stack permanently uncursed

/fiwtools imbue best                        — apply the top-weight outcome to the off-hand target
/fiwtools imbue roll                        — roll an outcome at the catalyst's real odds
/fiwtools imbue reset                       — clear the held item's imbue count
/fiwtools imbue clear                       — strip all imbuements and rebuild from config
/fiwtools imbue log                         — print the held item's imbuement history
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
| Behavior | `abilities` (active + `while_held` + `while_worn`, one array) |
| Curse | `cursed`, `curseWhitelist`, `curseSettings` (`perTick`, `ignoreArmor`, `ignoreResistance`, `checksEnderChest`, `sound`, `particles`) |
| Imbuing | `imbueLimit` on targets; an `imbue` ability (`targets` / `rng` / `maxUses` / `maxImbuements` / `outcomes` / `messages`) on catalysts |

Color codes use `&` prefix: `&0`–`&f` for colors, `&l`/`&o`/`&n`/`&m`/`&k`/`&r` for styles — same syntax as Fiw Bosses.

Full schema, every field, every default, every example — see **[ITEM_CONFIG_DOCS.md](ITEM_CONFIG_DOCS.md)**.

> **Note on attributes:** in Minecraft 1.21.11, attribute IDs dropped the `generic.` / `player.` prefix. Use `minecraft:attack_damage` and `minecraft:armor`, not `minecraft:generic.attack_damage`.

---

## Abilities

60 abilities (58 combat/utility + 2 admin) across nine roles. Every one is server-side, never hits the caster, and never breaks blocks. Full params, defaults and examples are in **[ITEM_CONFIG_DOCS.md](ITEM_CONFIG_DOCS.md)**.

| Role | Abilities |
|---|---|
| **Core** | `arc_slash`, `riptide_dash`, `lightning_strike`, `shockwave`, `heal_on_hit`, `blink`, `projectile_burst`, `frost_nova` |
| **PvP** | `grappling_pull`, `disarm`, `parry_counter`, `execute`, `leech`, `silence_sigil`, `tether` |
| **PvE** | `cleave`, `whirlwind`, `slaying_edge`, `soul_harvest`, `chain_lightning`, `gravity_well`, `ground_slam` |
| **Utility** | `phase_dash`, `feather_fall`, `second_wind`, `ender_recall`, `levitate_self` |
| **Support / social** | `rally_banner`, `taunt`, `healing_totem`, `beacon_ping`, `firework_burst`, `glow_mark`, `prank_swap` |
| **Passive — held self-buffs** | `passive_buff`, `featherweight`, `aqua_kit`, `thermal_ward`, `saturation_aura`, `magnet`, `berserker`, `combat_focus`, `lifeline` |
| **Passive — allied auras** | `rally_aura`, `beacon_aura`, `mending_aura` |
| **Passive — reactive defense** | `static_field`, `repulse_ward`, `chill_aura`, `blinding_flash`, `spore_cloud`, `thorn_pulse`, `coward_mark`, `hornet_swarm`, `curse_pulse` |
| **Passive — conditional survival** | `last_stand`, `adrenaline`, `shield_battery` |
| **Admin / systems** | `uncurse` (consumable curse-lifter), `imbue` (catalyst upgrade engine) |

All passive abilities also run from armor slots via the `while_worn` trigger (optional per-slot `slot` filter: `head` / `chest` / `legs` / `feet`).

**Triggers:** `on_right_click`, `on_attack`, `on_kill`, `on_hurt`, `on_block_break`, `while_held` (passive sweep every ~0.5 s; supports `whenBelowHealth` / `whenEnemyWithin` / `whenUnderwater` / `whenInCombat` / `whenOutOfCombat` gates), `while_worn` (same engine on each armor slot).

Every ability supports `cooldownTicks` (per-player), `chance` (0.0–1.0), and a `params` block with ability-specific knobs. AoE abilities take an **`affects`** param (`players` / `mobs` / `hostiles` / `allies` / `all`) — the main PvP-vs-PvE dial. Conditional abilities only consume their cooldown on a tick they actually fire. Cooldowns are tracked per-player per-ability-slot, so dual-wielding two copies of the same item shares one cooldown for that ability.

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

Pre-built items live in [`examples/items/`](examples/items/) — copy any into `config/fiw_tools/items/` to use them. Each one is built to demonstrate a different part of the mod.

| Item | Showcases |
|---|---|
| `aegis_sword` | Active abilities + uncapped power — arc slash, lightning, finisher, cleave; glint, keep-on-death |
| `vampire_fang` | Lifesteal across triggers — heal/leech on attack, soul harvest on kill |
| `sunforged_trident` | Movement + crowd-control casts — riptide dash, frost nova counter, `hideFlags` |
| `warlords_horn` | Team-support abilities — rally banner, healing totem, kill firework |
| `wanderers_charm` | **Held passives** (`while_held`) — haste/night-vision buff, item magnet, out-of-combat regen |
| `guardian_plate` | **Worn passives** (`while_worn`) — ally aura, thorns retaliation, last-stand panic |
| `abyssal_crown` | **Conditional worn passives** — aqua kit underwater, combat focus near enemies, enemy glow |
| `phantom_treads` | Worn buffs + utility — no fall damage, permanent speed, item magnet |
| `forbidden_blade` | **Curse system** — un-whitelisted holders drained; ender-chest detection on |
| `blessed_scroll` | **Uncurse** consumable — lifts a curse from one item on right-click |
| `spark_of_storms` | **RNG imbuing** — weighted catalyst with a god-roll and a downgrade; 5 charges |
| `essence_of_calm` | **Deterministic imbuing** — guaranteed armor upgrade, single charge |
| `ancient_blade` | **Imbue target** with `imbueLimit: 2` — a strong weapon capped at 2 upgrades |
| `prospectors_pick` | Custom **tool** rules — fast ore mining, unbreakable, mining-time saturation |
| `soul_apple` | **Food** component — any item made edible, always-eatable |

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
