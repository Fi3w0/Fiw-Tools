# Fiw Tools — Item Configuration Guide

Items are defined as `.json` files — no coding, no client install, no server restart needed.

| Path | Purpose |
|------|---------|
| `config/fiw_tools/items/` | Active item definitions |
| `examples/items/` | Pre-built items ready to copy |

> **Server-side only:** vanilla Minecraft 26.1.2 clients can connect to a server running Fiw Tools without installing anything.

> **Hot reload:** `/fiwtools reload` — picks up all item changes instantly.

**Commands:**

| Command | Permission | Description |
|---------|-----------|-------------|
| `/fiwtools give <players> <itemId> [count]` | level 2 | Build the item from JSON and give it |
| `/fiwtools list` | level 2 | List all loaded item ids |
| `/fiwtools info <itemId>` | level 2 | Print a summary of the item's definition |
| `/fiwtools reload` | level 2 | Reload all item configs without restart |

---

## Table of Contents

- [Root Fields](#root-fields)
- [Color Codes](#color-codes)
- [Display Name and Lore](#display-name-and-lore)
- [Rarity, Stack Size, Repair Cost](#rarity-stack-size-repair-cost)
- [Durability](#durability)
- [Enchantments and Glint](#enchantments-and-glint)
- [Attribute Modifiers](#attribute-modifiers)
- [Hide Tooltip Flags](#hide-tooltip-flags)
- [Keep on Death](#keep-on-death)
- [Food](#food)
- [Tool](#tool)
- [Custom Data](#custom-data)
- [Abilities](#abilities)
  - [riptide_dash](#riptide_dash)
  - [arc_slash](#arc_slash)
  - [lightning_strike](#lightning_strike)
  - [shockwave](#shockwave)
  - [heal_on_hit](#heal_on_hit)
  - [blink](#blink)
  - [projectile_burst](#projectile_burst)
  - [frost_nova](#frost_nova)
- [Bind with Fiw Bosses](#bind-with-fiw-bosses)
- [Behavior Notes](#behavior-notes)

---

## Root Fields

```json
{
  "id": "soul_blade",
  "base": "minecraft:netherite_sword",
  "displayName": "&5&lSoul Blade",
  "lore": ["&7Forged from condensed", "&7nightmares."],
  "rarity": "epic",
  "stackSize": 1,
  "unbreakable": true,
  "maxDamage": 2031,
  "damage": 0,
  "enchantmentGlint": true,
  "enchantments": { "minecraft:sharpness": 7 },
  "attributes": [ ... ],
  "keepOnDeath": true,
  "hideFlags": [ "enchantments", "attributes" ],
  "food": { ... },
  "tool": { ... },
  "repairCost": 5,
  "customData": { "tier": 4 },
  "abilities": [ ... ]
}
```

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `id` | string | **required** | Unique id — used in `/fiwtools give <id>` |
| `base` | string | **required** | Vanilla item registry id (`minecraft:diamond_sword`, etc.) |
| `displayName` | string | null | Custom name. Supports `&` color codes |
| `lore` | string array | [] | Description lines below the name |
| `rarity` | string | item default | `common` / `uncommon` / `rare` / `epic` |
| `stackSize` | int | item default | Max stack size, 1–99 |
| `unbreakable` | bool | false | Item never loses durability |
| `maxDamage` | int | item default | Override max durability |
| `damage` | int | 0 | Starting damage (use of durability) |
| `enchantmentGlint` | bool | auto | Force the shiny glint on/off regardless of enchantments |
| `enchantments` | map | {} | Map of enchantment id → level. Levels can exceed vanilla cap |
| `attributes` | array | [] | Attribute modifier list (see below) |
| `keepOnDeath` | bool | false | Item survives death without `keepInventory` (see below) |
| `hideFlags` | string array | [] | Hide specific tooltip sections |
| `food` | object | null | Make any item edible |
| `tool` | object | null | Make any item a custom tool |
| `repairCost` | int | 0 | XP cost added when this item is used in an anvil |
| `customData` | object | null | Free-form NBT passthrough — survives in `custom_data` |
| `abilities` | array | [] | Custom triggered effects (see below) |

---

## Color Codes

Use `&` prefix in `displayName`, `lore`, and any future text fields.

```
&0 Black    &8 Dark Gray     &l Bold
&1 Dark Blue  &9 Blue        &o Italic
&2 Dark Green &a Green       &n Underline
&3 Dark Aqua  &b Aqua        &m Strikethrough
&4 Dark Red   &c Red         &k Obfuscated
&5 Dark Purple &d Light Purple  &r Reset
&6 Gold       &e Yellow
&7 Gray       &f White
```

**Example:** `"&6&lDragon Fang"` → gold + bold

> Lore lines default to non-italic so colored text reads cleanly.

---

## Display Name and Lore

```json
"displayName": "&5&lSoul Blade",
"lore": [
  "&7Forged from condensed",
  "&7nightmares.",
  "",
  "&8&oEpic"
]
```

Empty strings produce blank lines in the tooltip.

---

## Rarity, Stack Size, Repair Cost

```json
"rarity": "epic",
"stackSize": 1,
"repairCost": 5
```

`rarity` controls the **name color** in tooltips: common (white), uncommon (yellow), rare (aqua), epic (light purple). Setting `displayName` color overrides it visually but the rarity tier is still recorded.

---

## Durability

```json
"unbreakable": true,
"maxDamage": 2031,
"damage": 0
```

| Field | Default | Description |
|-------|---------|-------------|
| `unbreakable` | false | Item never loses durability |
| `maxDamage` | item default | Total durability points |
| `damage` | 0 | Starting wear |

Set `unbreakable: true` *or* a bigger `maxDamage` — combining them works but the bar never drops.

---

## Enchantments and Glint

```json
"enchantments": {
  "minecraft:sharpness": 7,
  "minecraft:looting": 5,
  "minecraft:unbreaking": 10
}
```

- Levels are not capped at vanilla maximums.
- Modded enchantments work too — use their full registry id.
- `enchantmentGlint: true` forces the shimmer; `false` removes it (e.g. for an unenchanted-looking god item).

---

## Attribute Modifiers

```json
"attributes": [
  { "type": "minecraft:generic.attack_damage", "slot": "mainhand", "amount": 12.0, "operation": "add_value" },
  { "type": "minecraft:generic.attack_speed",  "slot": "mainhand", "amount": -2.4, "operation": "add_value" },
  { "type": "minecraft:generic.movement_speed","slot": "mainhand", "amount": 0.05, "operation": "add_multiplied_total" }
]
```

| Field | Default | Description |
|-------|---------|-------------|
| `type` | **required** | Attribute id |
| `slot` | `any` | `mainhand` / `offhand` / `head` / `chest` / `legs` / `feet` / `hand` / `armor` / `any` |
| `amount` | 0.0 | Numeric value (sign matters for attack_speed) |
| `operation` | `add_value` | `add_value` (flat) / `add_multiplied_base` / `add_multiplied_total` |
| `id` | auto | Optional unique modifier id; auto-generated if omitted |

**Common attributes:** `attack_damage`, `attack_speed`, `max_health`, `armor`, `armor_toughness`, `knockback_resistance`, `movement_speed`, `attack_knockback`, `luck`.

> Vanilla weapons set their own attack_damage when held — your `mainhand` modifier replaces that, it doesn't add to it. To get a +5 sword that adds on top of base 6, set `amount: 11`.

---

## Hide Tooltip Flags

```json
"hideFlags": [ "enchantments", "attributes", "unbreakable" ]
```

Recognized flags: `enchantments`, `stored_enchantments`, `attributes`, `unbreakable`, `dyed_color`, `trim`, `can_break`, `can_place_on`, `all` (hide every component tooltip line).

---

## Keep on Death

```json
"keepOnDeath": true
```

When the player dies, items with this flag are **not dropped** and are restored to their original slots on respawn. Works independently of `/gamerule keepInventory`. Implemented via a small mixin into `PlayerInventory.dropAll`.

> Cooldowns are not preserved through death/disconnect — the item is fresh on respawn.

---

## Food

```json
"food": {
  "nutrition": 6,
  "saturation": 8.0,
  "canAlwaysEat": true
}
```

| Field | Default | Description |
|-------|---------|-------------|
| `nutrition` | 0 | Hunger points restored |
| `saturation` | 0.0 | Saturation modifier |
| `canAlwaysEat` | false | Eat even at full hunger |

Apply this to any base item to make it edible (e.g. `minecraft:diamond` becomes a snack). The base item's other behaviors still apply.

---

## Tool

```json
"tool": {
  "defaultMiningSpeed": 1.5,
  "damagePerBlock": 1,
  "rules": [
    { "blocks": "#minecraft:logs", "speed": 12.0, "correctForDrops": true }
  ]
}
```

| Field | Default | Description |
|-------|---------|-------------|
| `defaultMiningSpeed` | 1.0 | Base speed multiplier |
| `damagePerBlock` | 1 | Durability used per block broken |
| `rules[].blocks` | required | Block id (`minecraft:stone`) or block tag (`#minecraft:logs`) |
| `rules[].speed` | inherits default | Mining speed against these blocks |
| `rules[].correctForDrops` | false | True = always drops items (correct tool) |

> Tool rules are evaluated top to bottom — first matching rule wins.

---

## Custom Data

```json
"customData": {
  "fiw_origin": "boss_drop",
  "tier": 4,
  "lore_signature": "souls"
}
```

Free-form NBT — readable by other mods, datapacks, or `/data get`. Two keys are reserved by Fiw Tools: `fiw_tools_id` (always added so the item can be looked up) and `fiw_keep_on_death` (set when `keepOnDeath: true`).

---

## Abilities

Abilities are server-side effects triggered by player actions. They never damage the caster, never break blocks, and are gated by configurable cooldowns and chances.

```json
"abilities": [
  {
    "type": "arc_slash",
    "trigger": "on_right_click",
    "cooldownTicks": 60,
    "chance": 1.0,
    "params": { "damage": 6.0, "range": 4.0 }
  }
]
```

**Common keys:**

| Field | Default | Description |
|-------|---------|-------------|
| `type` | required | Ability identifier — see below |
| `trigger` | `on_right_click` | When the ability fires |
| `cooldownTicks` | 0 | Per-player per-ability cooldown (20 ticks = 1 s) |
| `chance` | 1.0 | Roll per fire (0.0–1.0) |
| `params` | {} | Ability-specific knobs |

**Triggers:**

| Trigger | Fires when |
|---------|------------|
| `on_right_click` | Player right-clicks holding the item |
| `on_attack` | Player damages an entity with the item |
| `on_kill` | Player kills an entity with the item in main hand |
| `on_hurt` | Player takes damage while holding the item |
| `on_block_break` | Player breaks a block while holding the item |

**Particles** can be a string id or `{ "id": "minecraft:flame", "count": 8, "speed": 0.05 }`.

---

### `riptide_dash`

Launches the player forward in their look direction with a small upward kick. Trail particles.

| Param | Default | Description |
|-------|---------|-------------|
| `distance` | 5.0 | Dash distance multiplier |
| `vertical` | 0.4 | Upward kick |
| `particle` | `minecraft:cloud` | Trail particle |

```json
{ "type": "riptide_dash", "trigger": "on_right_click", "cooldownTicks": 60, "params": { "distance": 6.0 } }
```

---

### `arc_slash`

Sweeping cone in front of the player. Entities inside take damage and small knockback.

| Param | Default | Description |
|-------|---------|-------------|
| `range` | 3.5 | Reach in blocks |
| `arc` | 110.0 | Cone angle in degrees |
| `damage` | 5.0 | Damage per hit |
| `knockback` | 0.6 | Push strength |
| `particle` | `minecraft:enchanted_hit` | Arc trace |

```json
{ "type": "arc_slash", "trigger": "on_right_click", "cooldownTicks": 50, "params": { "damage": 6, "range": 4 } }
```

---

### `lightning_strike`

Cosmetic lightning bolt at the target on attack, plus bonus damage. Default cosmetic = no fire, no pig→zombified pig.

| Param | Default | Description |
|-------|---------|-------------|
| `damage` | 3.0 | Bonus damage |
| `cosmetic` | true | Suppress vanilla lightning side effects |
| `particle` | `minecraft:electric_spark` | Spark burst |

```json
{ "type": "lightning_strike", "trigger": "on_attack", "chance": 0.25, "cooldownTicks": 40, "params": { "damage": 4 } }
```

---

### `shockwave`

Ground ring around the player; nearby entities take damage and knockback.

| Param | Default | Description |
|-------|---------|-------------|
| `radius` | 4.0 | AoE radius |
| `damage` | 4.0 | Damage per hit |
| `knockback` | 1.2 | Outward push |
| `particle` | `minecraft:crit` | Ring particles |

```json
{ "type": "shockwave", "trigger": "on_right_click", "cooldownTicks": 80 }
```

---

### `heal_on_hit`

Player heals a small amount when they damage something.

| Param | Default | Description |
|-------|---------|-------------|
| `amount` | 1.5 | HP restored |
| `particle` | `minecraft:heart` | Pop particles |

```json
{ "type": "heal_on_hit", "trigger": "on_attack", "params": { "amount": 1.5 } }
```

---

### `blink`

Short teleport in look direction; clamped to safe blocks (won't suffocate).

| Param | Default | Description |
|-------|---------|-------------|
| `distance` | 5.0 | Max blink distance |
| `particle` | `minecraft:portal` | Burst at both ends |

```json
{ "type": "blink", "trigger": "on_right_click", "cooldownTicks": 60 }
```

---

### `projectile_burst`

Fires a fast particle projectile from the player. On hitting a living entity (or reaching range) it deals damage in a tiny AoE.

| Param | Default | Description |
|-------|---------|-------------|
| `range` | 16.0 | Max travel |
| `speed` | 1.4 | Blocks per tick |
| `damage` | 5.0 | Hit damage |
| `aoeRadius` | 1.0 | Radius around the projectile checked for hits |
| `particle` | `minecraft:flame` | Trail |

```json
{ "type": "projectile_burst", "trigger": "on_right_click", "cooldownTicks": 70 }
```

---

### `frost_nova`

AoE around the player applying a short Slowness and small magic damage.

| Param | Default | Description |
|-------|---------|-------------|
| `radius` | 4.0 | AoE radius |
| `damage` | 2.0 | Magic damage |
| `slowDuration` | 40 | Ticks of Slowness |
| `slowAmplifier` | 1 | Slowness level (0 = I) |
| `particle` | `minecraft:snowflake` | Ring particles |

```json
{ "type": "frost_nova", "trigger": "on_hurt", "chance": 0.4, "cooldownTicks": 100 }
```

---

## Bind with Fiw Bosses

Fiw Tools and Fiw Bosses are **fully independent mods** — each one works without the other.

When **both** are installed, Fiw Bosses can reference a Fiw Tools item by id from its loot or equipment slots:

```json
// boss loot entry
{ "toolId": "soul_blade", "count": 1, "chance": 0.25 }

// boss equipment slot
"mainHand": { "toolId": "soul_blade" }
```

Internally, Fiw Bosses calls `FiwToolsAPI.getItemStack(id, server)`. If Fiw Tools is missing, the lookup is silently skipped (the loot entry drops nothing, the equipment slot stays empty) — boss configs never crash because of a missing Tools install.

The reverse direction does **not** apply: Fiw Tools never reads Fiw Bosses. The two mods can be released, updated, and run independently.

---

## Behavior Notes

**Server-side guarantee**
- This mod runs only on the dedicated server. Vanilla 26.1.2 clients connect with no install.
- All custom data, lore, attributes, and ability effects are server-authoritative — they appear identical to a Fabric client and a vanilla client.

**Hot reload**
- `/fiwtools reload` re-reads every JSON in `config/fiw_tools/items/` and atomically swaps the registry.
- Items already in player inventories are **not** retroactively updated — they keep the components they had when given. Re-give to refresh.

**Cooldowns**
- Tracked per-player per-ability slot (item id + ability index). Two copies of the same item in main and offhand share a single cooldown for that ability.
- Cooldowns are in-memory only — they reset on player disconnect or server restart.

**Compatibility**
- Modded items can be used as `base` if the modded item is registered server-side.
- Modded enchantments and attributes work too, given their full registry ids.
- Items survive server restarts because all customizations live in vanilla data components.
