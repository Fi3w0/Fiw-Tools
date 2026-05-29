# FIW Tools

**A data-driven custom item framework for Fabric 26.1.2 — fully server-side, JSON-driven, vanilla items only.**

Define fully custom items, weapons, and armor entirely through JSON files. No coding, no client install, no server restarts — drop a config, run `/fiwtools reload`, and your item is live.

---

## Why FIW Tools?

Most "custom item" solutions for Fabric require client mods, register new item ids that desync with vanilla clients, or are tied to specific RPG frameworks. FIW Tools fills the gap — pure server-side, vanilla clients connect with no install, and every customization rides on **vanilla data components** so items survive uninstalls cleanly. Built as the sibling mod to [FIW Bosses](https://modrinth.com/mod/fiw-bosses): use them standalone or together.

---

## Supported Loaders

| Loader | Minecraft | Status |
|---|---|---|
| **Fabric** | **26.1.2** | **Active — primary development target** |

NeoForge and earlier Minecraft versions are not currently supported. The codebase only touches vanilla data components and Fabric API events, so a future port is possible but not planned.

---

## Features

- **JSON-driven items** — create any item without writing a single line of code
- **Vanilla data components** — custom name, lore, rarity, attributes (per-slot, every operation), enchantments at uncapped levels, durability, food, tool, hide-tooltip flags, free-form custom NBT
- **8 player-balanced abilities** — animated arc slash with layered particle sweep, riptide dash, cosmetic lightning strike, ground shockwave, heal on hit, short blink teleport, particle projectile burst, and frost nova
- **5 ability triggers** — right-click, on attack, on kill, on hurt, on block break
- **Per-player cooldown tracker** — every ability has its own cooldown per player per item slot
- **Keep on death** — flagged items survive respawn even when the keepInventory gamerule is off
- **Color codes** — & prefix for colors and styles in display name and lore, matching FIW Bosses syntax
- **Hot reload** — one command swaps the registry atomically with no server restart
- **Tab completion** — every command tab-completes loaded item ids
- **Public API** — other mods can look up items by id through a stable static API
- **Pairs with FIW Bosses** — bosses can drop or equip FIW Tools items by id when both are installed; soft dependency that silently degrades if one is missing
- **Server-side only** — vanilla 26.1.2 clients connect with no install required

---

## Quick Start

1. Drop the JAR into your server's mods folder alongside Fabric API and Fabric Language Kotlin
2. Start the server — folder generates at config/fiw_tools/items
3. Copy an example item from the examples folder into config/fiw_tools/items
4. Run the reload command
5. Give it to yourself with the give command

---

## Commands

- **give** — build the item from JSON and hand it to one or more players, with an optional count
- **list** — list every loaded item id
- **info** — print a one-line summary of an item's definition
- **reload** — re-read every JSON file and atomically swap the registry without a restart

All commands are at permission level 2 (operator) and tab-complete loaded item ids.

---

## Customization Summary

Every item JSON is a flat object. Only id and base are required.

| Section | Fields |
|---|---|
| Identity | id, base, displayName, lore, rarity |
| Stack | stackSize, repairCost |
| Durability | unbreakable, maxDamage, damage |
| Visual | enchantmentGlint, hideFlags |
| Power | enchantments (uncapped), attributes (per-slot, all operations) |
| Persistence | keepOnDeath, customData |
| Use | food, tool |
| Behavior | abilities |

---

## Pair with FIW Bosses

When both mods are installed, FIW Bosses can reference FIW Tools items by id from its loot tables and equipment slots — boss loot entries can pull a Tools item by id, and equipment slots can equip one by id. If FIW Tools is missing the lookup is silently skipped, so boss configs never crash. Each mod also works fully on its own.

---

## Requirements

| | |
|---|---|
| Minecraft | 26.1.2 |
| Fabric Loader | 0.19.2 or newer |
| Fabric API | 0.148.0+26.1.2 or newer |
| Fabric Language Kotlin | latest |
| Java | 25 |
| Server-side only | Yes |

---

## Documentation

Full configuration reference with every field, every ability parameter, and complete examples available on the [GitHub repository](https://github.com/Fi3w0/Fiw-Tools).

---

## License

Creative Commons Attribution-NonCommercial-ShareAlike 4.0 International (CC BY-NC-SA 4.0). You may share and adapt the material with attribution and a link to the original repository, for non-commercial use, under the same license. For commercial use, contact Fi3w0 to request permission.

---

*Made by Fi3w0 — built for a private SMP, shared with everyone.*
*Developed with assistance from Claude Opus 4.7.*
