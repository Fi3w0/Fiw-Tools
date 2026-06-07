# FIW Tools

**Data-driven custom item framework for Fabric 1.21.11 — fully server-side, JSON-driven, vanilla items only.**

Define custom items, weapons, and armor entirely through JSON. No coding, no client install, no restarts — drop a config, run `/fiwtools reload`, and it's live. Vanilla clients connect with no mod installed; everything rides on **vanilla data components**, so items survive an uninstall cleanly.

Sibling mod to [FIW Bosses](https://modrinth.com/mod/fiw-bosses) — use them standalone or together.

---

## Supported Versions

| Loader | Minecraft | Status |
|---|---|---|
| **Fabric** | **1.21.11** | **Active — primary target** |
| Fabric | 26.1.2 | Earlier branch — not actively worked on |

NeoForge not supported. Only vanilla data components + Fabric API events are touched, so a future port is possible but unplanned.

---

## Features

- **JSON-driven items** — any item, zero code
- **Vanilla data components** — name, lore, rarity, per-slot attributes, uncapped enchantments, durability, food, tool, hide-tooltip flags, free-form NBT
- **60 abilities** — PvP, PvE, utility, team support, and passives (held buffs, allied auras, reactive defense, conditional survival), plus admin tools `uncurse` and `imbue`. Each gated by per-player cooldown, `chance`, and an `affects` filter (PvP vs PvE)
- **7 triggers** — `on_right_click`, `on_attack`, `on_kill`, `on_hurt`, `on_block_break`, `while_held` (passive sweep with health/combat/underwater/enemy-near gates), `while_worn` (same engine on each armor slot, per-slot filter)
- **Worn armor passives** — every passive also runs in armor slots; mix held + worn on one item
- **Curse system** — mark an item cursed with a player whitelist. Non-whitelisted holders take armor- and resistance-bypassing `fiw_tools:curse` damage until they drop it or die. Optionally **scans the ender chest** so stashing it there doesn't save them. Cursed items can't be renamed, repaired, enchanted, grindstoned, or placed. Consumable **Blessed Scroll** lifts a curse via the `uncurse` ability
- **Imbuing system** — catalysts upgrade items: enchant levels above the vanilla cap, extra attributes, new abilities, rewritten lore/name. Outcomes **deterministic** or **weighted-random** (incl. rare god-rolls and "cracked" downgrades so OP upgrades stay risky). Catalysts carry a `maxUses` charge; targets carry an independent `imbueLimit`. All imbuements replay on top of fresh builds at `/fiwtools reload`
- **Keep on death** — flagged items survive respawn even with keepInventory off (cursed items always drop, so the curse can't be cheesed by suicide)
- **Color codes** — `&` prefix for colors/styles in name and lore
- **Hot reload** — one command swaps the registry atomically; player enchants, anvil renames, uncurse flags, and imbuements all survive
- **Public API** — other mods look up items by id through a stable static API
- **Pairs with FIW Bosses** — bosses can drop or equip Tools items by id (soft dependency, degrades silently)
- **Server-side only** — vanilla clients need no install

---

## Quick Start

1. Drop the JAR in `mods/` alongside Fabric API + Fabric Language Kotlin
2. Start the server — `config/fiw_tools/items` generates
3. Copy an example item in, run `/fiwtools reload`
4. `/fiwtools give @s <itemId>`

---

## Commands
| Command | Does |
|---|---|
| `give` / `list` / `info` / `reload` | build & hand out, list ids, summarize, hot-reload |
| `curse add / remove / list` | mark or unmark an item cursed (rewrites its JSON in place) |
| `curse whitelist add / remove` | manage the per-item exemption list |
| `uncurse_held` | flag the held stack permanently uncursed |
| `imbue best / roll` | apply top outcome, or roll at real odds, onto the off-hand target |
| `imbue reset / clear / log` | clear count, strip & rebuild, or print imbue history |

All at permission level 2 (operator); item ids tab-complete.

---

## Customization Summary

Every item JSON is a flat object. Only `id` and `base` are required.

| Section | Fields |
|---|---|
| Identity | `id`, `base`, `displayName`, `lore`, `rarity` |
| Stack / Durability | `stackSize`, `repairCost`, `unbreakable`, `maxDamage`, `damage` |
| Visual | `enchantmentGlint`, `hideFlags` |
| Power | `enchantments` (uncapped), `attributes` (per-slot, all operations) |
| Persistence / Use | `keepOnDeath`, `customData`, `food`, `tool` |
| Behavior | `abilities` (active + `while_held` + `while_worn`, one array) |
| Curse | `cursed`, `curseWhitelist`, `curseSettings` |
| Imbuing | `imbueLimit` on targets; `imbue` ability (`targets`/`rng`/`maxUses`/`outcomes`/`messages`) on catalysts |

---

## Requirements

| | |
|---|---|
| Minecraft | 1.21.11 (active) — 26.1.2 build also published |
| Fabric Loader | 0.19.2+ |
| Fabric API | matching the MC version |
| Fabric Language Kotlin | latest |
| Java | 21 (1.21.11) — 25 (26.1.2) |
| Server-side only | Yes |

---

Full field reference, ability params, and curse/imbue examples are in the [GitHub repository](https://github.com/Fi3w0/Fiw-Tools).

**License:** CC BY-NC-SA 4.0 — share and adapt with attribution, non-commercial, same license. Commercial use: contact Fi3w0.

*Made by Fi3w0 — built for a private SMP, shared with everyone. Developed with assistance from Claude Opus 4.8.*