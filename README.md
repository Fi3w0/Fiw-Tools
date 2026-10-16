<div align="center">

# Fiw Custom Items

**Create custom weapons, armor, food, curses, imbuements, and player abilities entirely through JSON - no coding, no restarts.**

[![Build](https://github.com/Fi3w0/Fiw-Tools/actions/workflows/build.yml/badge.svg)](https://github.com/Fi3w0/Fiw-Tools/actions/workflows/build.yml)
[![Modrinth](https://img.shields.io/modrinth/v/fiw-custom-items?label=Modrinth&logo=modrinth&color=00AF5C)](https://modrinth.com/mod/fiw-custom-items)
[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.1_·_1.21.8_·_1.21.11_·_26.1.2-62B47A)](https://minecraft.net)
[![License](https://img.shields.io/badge/License-CC%20BY--NC--SA%204.0-blue)](LICENSE)

[![Fabric](https://img.shields.io/badge/Fabric-✓-DBB591)](https://fabricmc.net)
[![NeoForge](https://img.shields.io/badge/NeoForge-✓-F16436)](https://neoforged.net)

[Quick Start](#quick-start) · [Features](#features) · [Abilities](#abilities) · [Documentation](ITEM_CONFIG_DOCS.md) · [Issues](https://github.com/Fi3w0/Fiw-Tools/issues)

</div>

---

## Overview

Fiw Custom Items turns JSON files into playable server items. Drop an item file into `config/fiw_tools/items/`, run `/fiwtools reload`, and your server can give it immediately - no code, no restart.

It is not a fixed item pack: vanilla item bases, custom names and lore, attributes, enchantments above vanilla caps, durability, food, tool rules, curses, imbuements, keep-on-death flags, and active or passive abilities all come from config.

> **Server-side only.** Fiw Custom Items has no client-side code — install it on the server only, on Fabric or NeoForge. Players do not need the mod (or any resource pack) to join. On Fabric, even fully vanilla clients can connect.

> **Compatibility note:** the internal mod id, config folder, `/fiwtools` command, and `FiwToolsAPI` names stay as `fiw_tools` / Fiw Tools for existing servers and integrations.

---

## Quick Start

```bash
# Fabric:
# 1. Install Fabric Loader, Fabric API, and Fabric Language Kotlin.
# 2. Drop the Fabric Fiw Custom Items jar into mods/.
#
# NeoForge:
# 1. Install NeoForge for the matching Minecraft version.
# 2. Drop the NeoForge Fiw Custom Items jar into mods/.
#    The NeoForge jar includes the Kotlin runtime it needs.
#
# Start once, then add an item file:
```

`config/fiw_tools/items/storm_blade.json`

```json
{
  "id": "storm_blade",
  "base": "minecraft:diamond_sword",
  "displayName": "&b&lStorm Blade",
  "lore": ["&7Right-click to call lightning."],
  "rarity": "epic",
  "enchantments": {
    "minecraft:sharpness": 7
  },
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

Full JSON reference -> **[ITEM_CONFIG_DOCS.md](ITEM_CONFIG_DOCS.md)**

---

## Supported Targets

| Loader   | Minecraft | Module             | Java |
| -------- | --------- | ------------------ | ---- |
| Fabric   | 1.21.1    | `fabric-1.21.1`    | 21   |
| NeoForge | 1.21.1    | `neoforge-1.21.1`  | 21   |
| Fabric   | 1.21.8    | `fabric-1.21.8`    | 21   |
| NeoForge | 1.21.8    | `neoforge-1.21.8`  | 21   |
| Fabric   | 1.21.11   | `fabric-1.21.11`   | 21   |
| NeoForge | 1.21.11   | `neoforge-1.21.11` | 21   |
| Fabric   | 26.1.2    | `fabric-26.2.1`    | 25   |

The `fabric-26.2.1` module keeps the requested folder/module name. Fabric's official metadata endpoint currently rejects `26.2.1`, so the buildable Minecraft target remains `26.1.2` until that coordinate exists.

| Loader | Extra requirements |
| ------ | ------------------ |
| Fabric | **[Fabric API](https://modrinth.com/mod/fabric-api)** and **[Fabric Language Kotlin](https://modrinth.com/mod/fabric-language-kotlin)** |
| NeoForge | Kotlin runtime is included in the Fiw Custom Items NeoForge jar. Install it on the NeoForge server only; clients do not need the mod. |

---

## Features

### JSON-driven items

Use any vanilla item as the base and layer custom names, lore, rarity, durability, hide flags, attributes, enchantments, food behavior, tool rules, and persistent custom data on top.

### Active abilities

Items can fire abilities from player actions: right-click, attack, kill, hurt, and block break. Every ability supports cooldowns, chance, and ability-specific parameters.

### Passive held and worn effects

Passive abilities run while an item is held or worn. Armor can provide auras, reactive defenses, survival triggers, utility buffs, and conditional effects based on combat state, nearby enemies, health, or water.

### Curse system

Cursed items can damage non-whitelisted holders, bypass armor and resistance, scan ender chests, block anvil and grindstone abuse, and resist placement as world blocks.

### Elemental status system

Abilities can apply FROZEN, SOAKED, or SHOCKED to entities. Each status has passive tick effects (slowness, water drip, periodic magic damage). Two interaction abilities — `thaw_burst` and `storm_chain` — consume a status for a payoff, rewarding setup-and-execute combos.

### Set bonuses (resonance)

Tag items with a `resonanceId`. When a player has enough matching pieces equipped (held or worn), the first item in the set fires its `resonance` trigger automatically each passive sweep — unlocking bonus effects without any player input. A per-ability `minPieces` enables tiered set bonuses — a 2-piece bonus and a stronger 4-piece bonus on one set.

### Soul system

Items with `soulCapacity` can store souls. `soul_collector` adds one on kill; `soul_surge` spends them all at once for AoE magic damage that scales with the count stored; `soul_lash` spends just one per hit for a smaller, repeatable bonus-damage-plus-lifesteal proc.

### Imbuing system

Catalyst items can upgrade targets with deterministic or weighted-random outcomes: new abilities, extra attributes, enchantment upgrades, lore rewrites, charge limits, and per-target imbuement caps.

### Custom crafting recipes

Drop JSON recipe files into `config/fiw_tools/recipes/` — shaped and shapeless, multiple recipes per file. Craft custom items from vanilla ingredients, vanilla items from custom ingredients, or custom from custom, in any crafting grid. Fully server-side; vanilla clients craft normally. An optional `enchantments` block transfers the consumed ingredients' enchantments onto the result — merge, keep-old or keep-new, with per-enchantment conflict policies, filters and level caps — so players upgrading their enchanted gear keep what they earned. A per-item `enchanting` block controls which stations accept each item — enchanting table, anvil, grindstone, smithing — plus a name lock that lets players enchant at the anvil but never rename.

### Infinite use items

The `infinite` field makes consumables never run out: `keep` (never consumed — infinite food, potions, pearls, arrows), `damage` (uses durability instead), or `replace` (turns into another item). Fired infinite arrows return to the shooter instantly and can't be dupe-farmed. On Minecraft 1.21.1, the port supports infinite food and arrows through the older food-conversion and arrow hooks; generic non-food consumed items need a newer target with vanilla `use_remainder`.

### Multi-block mining

The `mining` field turns a tool into a vein-miner, hammer, or tunnel-borer: `vein` flood-fills connected matching blocks (ores, or `#minecraft:logs` for a tree-feller), `area` breaks a `(2·radius+1)` plane facing you, and `tunnel` extends that plane forward. Extra blocks break through your own game mode, so drops, XP, Fortune/Silk Touch and durability all behave normally. Filter with `whitelist`/`tags`, cap with `maxBlocks`, hold sneak to mine one block; the tool stops before its last durability so it never snaps mid-vein (unbreakable/infinite tools mine whole veins free).

### Recharge on kill

The `rechargeOnKill` field turns kills into item upkeep: reduce or reset cooldowns, repair damage, grow max durability, or make the item unbreakable. It can trigger from main hand, offhand, both hands, or any inventory slot, and can match any entity, mobs, players, specific mob ids, or specific player names/UUIDs.

### Artifact awakening

The `awakening` field auto-upgrades an item into another when its holder proves worthy: kill a boss or specific mob N times, kill (a specific) player, deal total damage, or visit a dimension. Chain awakenings for multi-stage artifacts, or awaken via a custom crafting recipe.

### Bound artifacts

The `binding` field ties an item to the first player who uses or picks it up. Non-owners can't fire its abilities, and an optional curse damages anyone else who dares carry it.

### Universal charges and use costs

Give items a regenerating `charges` pool and make any ability spend charges, durability, health, hunger, XP levels, or inventory items per use. All-or-nothing, refused when unaffordable, refunded on a miss.

Ability cooldowns, including shared groups, survive relogging and server restarts as remaining ticks; offline time does not consume them. Optional `logCommands` auditing (`off` by default, `console` or `file`) in `config/fiw_tools/settings.json` records the player, item, ability index and final command. File logs go to `config/fiw_tools/logs/commands.jsonl`.

### Cooldown groups

`cooldownGroup` shares one cooldown across abilities and across items — the anti-broken-combo dial.

### Proc groups

`procGroup` makes abilities on the same trigger mutually exclusive — one weighted roll fires at most one of them, instead of every eligible ability firing at once.

### Ability scaling

A `scaling` block makes any ability's numbers grow with the holder's build — killstreak tier, imbue count, remaining charges, or max health — turning static abilities into build-defined curves with no new code.

### Enchanting control

Per-station `enchanting` rules gate the table, anvil, grindstone, and smithing table. `allowEnchant` blocks adding enchantments while keeping rename and repair; configured attributes are re-stamped after any anvil or smithing operation so they can't be overwritten.

### Ten lifecycle triggers

`on_consume`, `on_pickup`, `on_drop`, `on_death`, `on_respawn`, `on_enter_dimension`, `on_leave_dimension`, `on_equip`, `on_unequip`, `on_boss_kill` (vanilla + FIW Bosses aware), plus `on_boss_phase` — fires when a nearby FIW Bosses boss changes phase.

### Conditions on everything

`whenBelowHealth`, `whenDimension`, `whenNight`, `whenRaining`, `whenSneaking`, `whenEnemyWithin`, … gate any ability on any trigger.

### Dynamic lore

`dynamicLore` lines update live on the item: `{owner}`, `{charges}/{max_charges}`, `{souls}/{soul_capacity}`, `{awakening_stage}`, `{durability}`.

### Vanilla visuals

`potionColor`, `dyedColor`, and `itemModel` set the corresponding vanilla components straight from JSON.

### Item commands

The `run_command` ability silently executes any server command(s) on any trigger — with `{player}`, `{x}/{y}/{z}`, `{target}` placeholders. Combined with the new `on_shift_right_click` trigger, one item can carry two full command kits.

### Hot reload and sync

`/fiwtools reload` re-reads item configs live. Existing player stacks are rebuilt from the new definition while preserving durability, player-added enchants (including anvil-upgraded config enchants), player renames, uncurse flags, imbuement history, souls, killstreaks, stances, charges, awakening progress, binding owners, and any attribute modifiers, armor trim or dye added by players, commands or other mods. The same state carries through artifact awakenings and crafting upgrade recipes.

### Auto ability lore
`"autoLore": true` writes the tooltip for you: one line per ability, like *Right-click: Lightning Rain (6s cooldown)*, after your own lore. Rename or hide abilities per line; a server-wide default lives in `config/fiw_tools/settings.json`.

### Export items
`/fiwtools export <newId>` saves the item in your hand as a new item config — name, lore, enchantments, attributes and more — and lists anything it couldn't map. Exporting a Fiw item makes a full copy you can tweak.

### Boss slayer
`bonusVsBosses` / `bonusVsMinions` multiply the damage an item (or one of its abilities) deals to bosses and boss minions, and the `whenTargetIsBoss` / `whenTargetIsMinion` conditions gate abilities on what you're hitting. Works with FIW Bosses and the vanilla bosses.

### FIW Bosses integration

FIW Bosses can reference Fiw Custom Items entries by `toolId` in equipment and loot entries. If Fiw Custom Items is missing, those entries are skipped without crashing the boss mod.

With FIW Bosses 1.2.0+, items can react to boss fights with the `on_boss_phase` trigger, and the op-only `boss_phase` ability lets admins move a live boss to the next/previous/any phase without touching its HP (see `examples/fiw_bosses_integration/items/phase_warden_rod.json`). FIW Bosses' new per-player raid loot can hand out a `unique` Fiw item to exactly one raider.

---

## Abilities

Abilities are small, tunable modules. Add them to an item, set a trigger, tune the params, and combine them into a kit.

**Highlights**

| Ability | What it does |
|---|---|
| `arc_slash` | Animated multi-tick particle slash ported from FIW Bosses |
| `lightning_strike` | Calls lightning or damage at a targeted location |
| `lightning_rain` | Rains bolts over a crosshair circle on a custom stagger schedule |
| `lightning_ray` | Sequential bolts marching forward in a straight line |
| `teleport_behind` | Blink behind your target with optional finisher damage |
| `homing_bolt` | Projectile that curves toward the nearest enemy mid-flight |
| `aegis_shield` | On-demand barrier that swallows a pool of incoming damage |
| `stun_strike` | True hard crowd-control — a full movement/attack lock |
| `apply_effect` | Apply any vanilla potion effect to self or target, any trigger |
| `boss_phase` | Admin tool: move a FIW Bosses boss to the next/previous/any phase without touching HP |
| `repair` | Repair-kit item that mends a damaged item in your other hand |
| `projectile_burst` | Fires a custom particle projectile with damage and AoE |
| `frost_nova` | Freezes and slows enemies around the player |
| `riptide_dash` | Launches the player in the look direction |
| `grappling_pull` | Pulls a target toward the caster |
| `chain_lightning` | Bounces damage between nearby targets |
| `gravity_well` | Pulsing zone that pulls entities inward |
| `passive_buff` | Applies held or worn status effects |
| `last_stand` | Conditional survival trigger at low health |
| `uncurse` | Consumable ability that marks a cursed stack as safe |
| `imbue` | Catalyst upgrade engine for custom item progression |
| `freeze` / `soak` / `shock` | Apply elemental status with passive tick effects |
| `thaw_burst` | Consume FROZEN for a fire AoE explosion around the target |
| `storm_chain` | Consume SOAKED to arc lightning to nearby enemies |
| `soul_collector` | Absorb a soul on kill (stored up to `soulCapacity`) |
| `soul_surge` | Spend all stored souls for scaled AoE magic damage |
| `soul_lash` | Spend one soul per hit for bonus damage + lifesteal |
| `blood_pact` | Sacrifice HP to multiply next attack damage |
| `sanguine_strike` | Lifesteal scaled by the target's missing HP |
| `hemorrhage` | On-hurt: bleed DoT applied to whoever damaged you |
| `flame_dash` | Dash forward, igniting enemies along the path |
| `meteor_strike` | Raycast AoE impact at cursor position |
| `ember_burst` | On-kill: fire nova at the corpse ignites nearby enemies |
| `geyser_burst` | Raycast AoE launch + damage + SOAKED at a targeted point |
| `run_command` | Silently run any server command(s) — infinite ability possibilities |

<details>
<summary><strong>Core ability groups</strong></summary>

```text
core combat, PvP, PvE, utility, support, passive self-buffs, passive auras,
reactive defense, conditional survival, curse tools, and imbuing.
```

</details>

Full ability parameters and examples live in **[ITEM_CONFIG_DOCS.md](ITEM_CONFIG_DOCS.md)**.

---

## Commands

`/fiwtools` requires op level 2.

```text
/fiwtools give <players> <itemId> [count]
/fiwtools list
/fiwtools recipes
/fiwtools info <itemId>
/fiwtools reload
/fiwtools export <newId>

/fiwtools curse add <itemId>
/fiwtools curse remove <itemId>
/fiwtools curse list <itemId>
/fiwtools curse whitelist add <itemId> <player>
/fiwtools curse whitelist remove <itemId> <player>
/fiwtools uncurse_held

/fiwtools imbue best <catalystId>
/fiwtools imbue roll <catalystId>
/fiwtools imbue reset
/fiwtools imbue clear
/fiwtools imbue log
```

**Config folder**

```text
config/fiw_tools/items/     active item definitions
config/fiw_tools/recipes/   custom crafting recipes
config/fiw_tools/settings.json  defaults and command auditing
config/fiw_tools/data/cooldowns/  persisted player cooldowns
```

---

## Building

Run Gradle with **JDK 25** available. The 1.21.1, 1.21.8 and 1.21.11 modules compile against a Java 21 toolchain, and the Fabric 26 module compiles against Java 25.

```bash
git clone https://github.com/Fi3w0/Fiw-Tools
cd Fiw-Tools
./gradlew build
```

<details>
<summary><strong>Current release-style matrix build</strong></summary>

```bash
./gradlew :fabric-1.21.11:build \
  :fabric-1.21.1:build \
  :neoforge-1.21.1:build \
  :fabric-1.21.8:build \
  :neoforge-1.21.8:build \
  :neoforge-1.21.11:build \
  :fabric-26.2.1:build
```

Output jars land in each module's `build/libs/`.

</details>

### Repository layout

```text
core/                Minecraft-free item config parser, text codes, and tests
common-1.21.1/       shared MC 1.21.1 implementation
fabric-1.21.1/       Fabric entrypoint, events, metadata, build
neoforge-1.21.1/     NeoForge entrypoint, events, metadata, build
common-1.21.8/       shared MC 1.21.8 implementation
fabric-1.21.8/       Fabric entrypoint, events, metadata, build
neoforge-1.21.8/     NeoForge entrypoint, events, metadata, build
common-1.21.11/      shared MC 1.21.11 implementation
fabric-1.21.11/      Fabric entrypoint, events, metadata, build
neoforge-1.21.11/    NeoForge entrypoint, events, metadata, build
common-26.2.1/       shared Fabric 26 implementation
fabric-26.2.1/       Fabric entrypoint, events, metadata, build
examples/             example item JSON + FIW Bosses integration configs
```

Every loader module pulls its `common-*` source set via Gradle `srcDir`, and `core` is
bundled into each loader jar — so one item file behaves the same on every supported target.

---

<div align="center">

**[Documentation](ITEM_CONFIG_DOCS.md)** · **[Report a bug](https://github.com/Fi3w0/Fiw-Tools/issues)** · **[License](LICENSE)**

Made by **Fiw** for SMP-style custom item progression and boss loot.

</div>
