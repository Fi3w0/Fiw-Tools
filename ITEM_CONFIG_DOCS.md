# Fiw Tools — Item Configuration Guide

Items are defined as `.json` files — no coding, no client install, no server restart needed.

| Path | Purpose |
|------|---------|
| `config/fiw_tools/items/` | Active item definitions |
| `examples/items/` | Pre-built items ready to copy |

> **Server-side only.** Fiw Tools has no client-side code, so it installs on the server only (Fabric or NeoForge) and players never need the mod to join. On Fabric, even fully vanilla clients can connect.

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
  - [Targeting (`affects`)](#targeting-affects)
  - Original set: [riptide_dash](#riptide_dash) · [arc_slash](#arc_slash) · [lightning_strike](#lightning_strike) · [shockwave](#shockwave) · [heal_on_hit](#heal_on_hit) · [blink](#blink) · [projectile_burst](#projectile_burst) · [frost_nova](#frost_nova)
  - PvP: [grappling_pull](#grappling_pull) · [disarm](#disarm) · [parry_counter](#parry_counter) · [execute](#execute) · [leech](#leech) · [silence_sigil](#silence_sigil) · [tether](#tether)
  - PvE: [cleave](#cleave) · [whirlwind](#whirlwind) · [slaying_edge](#slaying_edge) · [soul_harvest](#soul_harvest) · [chain_lightning](#chain_lightning) · [gravity_well](#gravity_well) · [ground_slam](#ground_slam)
  - Utility: [phase_dash](#phase_dash) · [feather_fall](#feather_fall) · [second_wind](#second_wind) · [ender_recall](#ender_recall) · [levitate_self](#levitate_self)
  - Support / social: [rally_banner](#rally_banner) · [taunt](#taunt) · [healing_totem](#healing_totem) · [beacon_ping](#beacon_ping) · [firework_burst](#firework_burst) · [glow_mark](#glow_mark) · [prank_swap](#prank_swap)
  - [Passives (`while_held`)](#passives-while_held)
    - Held self-buffs: [passive_buff](#passive_buff) · [featherweight](#featherweight) · [aqua_kit](#aqua_kit) · [thermal_ward](#thermal_ward) · [saturation_aura](#saturation_aura) · [magnet](#magnet) · [berserker](#berserker) · [combat_focus](#combat_focus) · [lifeline](#lifeline)
    - Allied auras: [rally_aura](#rally_aura) · [beacon_aura](#beacon_aura) · [mending_aura](#mending_aura)
    - Reactive defense: [static_field](#static_field) · [repulse_ward](#repulse_ward) · [chill_aura](#chill_aura) · [blinding_flash](#blinding_flash) · [spore_cloud](#spore_cloud) · [thorn_pulse](#thorn_pulse) · [coward_mark](#coward_mark) · [hornet_swarm](#hornet_swarm) · [curse_pulse](#curse_pulse)
    - Conditional survival: [last_stand](#last_stand) · [adrenaline](#adrenaline) · [shield_battery](#shield_battery)
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
| `while_held` | Periodic sweep (every 10 ticks ≈ 0.5 s) while the item is in your hand — see [Passives](#passives-while_held) |
| `while_worn` | Same sweep but for armor slots — see [Worn passives](#worn-passives-while_worn) |

**Particles** can be a string id or `{ "id": "minecraft:flame", "count": 8, "speed": 0.05 }`.

---

### Targeting (`affects`)

AoE and multi-target abilities accept an `affects` value inside `params` that controls **who they may hit**. This is the main PvP-vs-PvE balance dial — the same ability tuned `hostiles` is a horde tool, tuned `players` is a duel tool.

| Value | Hits |
|-------|------|
| `players` | other players only (never the caster) |
| `mobs` | any non-player living entity — **default** for the PvE abilities |
| `hostiles` | hostile mobs only |
| `allies` | other players, as friendly targets (for buffs/heals) |
| `all` | anything alive except the caster |

> **Two rules hold for every ability:** the caster is never hit by their own ability, and **conditional** abilities (e.g. `execute`, `second_wind`, `slaying_edge`, `feather_fall`, `cleave`, `chain_lightning`) only spend their `cooldownTicks` on a tick where they actually fire — a miss costs nothing.

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

## PvP Abilities

Control, counters and finishers. Built for duels; all damage is dealt as the player so PvP / keep-inventory rules apply.

### `grappling_pull`

Best trigger: `on_attack`. Yanks the target toward you instead of knocking it back — catches runners. No damage of its own.

| Param | Default | Description |
|-------|---------|-------------|
| `pullStrength` | 1.2 | Horizontal pull velocity |

```json
{ "type": "grappling_pull", "trigger": "on_attack", "cooldownTicks": 40 }
```

---

### `disarm`

Best trigger: `on_attack`. Applies Weakness + Mining Fatigue so the victim's next hits land softer. Pair with a low `chance`.

| Param | Default | Description |
|-------|---------|-------------|
| `duration` | 60 | Effect length in ticks |
| `amplifier` | 0 | Effect level (0 = I) |

```json
{ "type": "disarm", "trigger": "on_attack", "chance": 0.2, "cooldownTicks": 40, "params": { "duration": 80 } }
```

---

### `parry_counter`

Best trigger: `on_hurt`. Reflects a capped fraction of the melee damage you just took back at the attacker, and shoves them off.

| Param | Default | Description |
|-------|---------|-------------|
| `reflectPercent` | 0.3 | Fraction of incoming damage reflected (0.0–1.0) |
| `maxReflect` | 6.0 | Hard cap on reflected damage |
| `knockback` | 0.5 | Push applied to the attacker |

```json
{ "type": "parry_counter", "trigger": "on_hurt", "cooldownTicks": 30 }
```

---

### `execute`

Best trigger: `on_attack`. Bonus damage **only** when the target is already below a HP fraction — does nothing on a healthy target, so it can't open fights.

| Param | Default | Description |
|-------|---------|-------------|
| `threshold` | 0.25 | Fires when target HP ≤ this fraction of max |
| `bonus` | 6.0 | Extra damage dealt |

```json
{ "type": "execute", "trigger": "on_attack", "params": { "threshold": 0.3, "bonus": 8 } }
```

---

### `leech`

Best trigger: `on_attack`. Lifesteal — heals a small flat amount on hit (does nothing at full HP).

| Param | Default | Description |
|-------|---------|-------------|
| `amount` | 1.0 | HP restored per hit |

```json
{ "type": "leech", "trigger": "on_attack", "params": { "amount": 1.5 } }
```

---

### `silence_sigil`

Best trigger: `on_attack`. Hunger + Slowness — denies the target's regen and slows them, a soft lockdown rather than a hard stun.

| Param | Default | Description |
|-------|---------|-------------|
| `duration` | 60 | Effect length in ticks |
| `amplifier` | 0 | Effect level (0 = I) |

```json
{ "type": "silence_sigil", "trigger": "on_attack", "cooldownTicks": 60 }
```

---

### `tether`

Best trigger: `on_attack`. Marks the target on hit; striking the **same** marked target again before the mark expires deals bonus damage.

| Param | Default | Description |
|-------|---------|-------------|
| `markDuration` | 100 | Ticks the mark lasts |
| `bonus` | 4.0 | Bonus damage on the follow-up hit |

```json
{ "type": "tether", "trigger": "on_attack", "params": { "markDuration": 120, "bonus": 5 } }
```

---

## PvE Abilities

AoE clear, group damage and crowd control. These default to `affects: hostiles` — widen with the `affects` param.

### `cleave`

Best trigger: `on_attack`. Splashes flat damage to other enemies around the entity you hit (excludes the struck target).

| Param | Default | Description |
|-------|---------|-------------|
| `radius` | 3.0 | Splash radius around the target |
| `damage` | 3.0 | Damage to each splashed entity |
| `affects` | `mobs` | Who it splashes (see [Targeting](#targeting-affects)) |

```json
{ "type": "cleave", "trigger": "on_attack", "params": { "radius": 3.5, "damage": 4 } }
```

---

### `whirlwind`

Best trigger: `on_right_click`. 360° sweep — damages and knocks back nearby enemies and tugs loose items / XP orbs toward you.

| Param | Default | Description |
|-------|---------|-------------|
| `radius` | 4.0 | Reach and item-pull radius |
| `damage` | 3.0 | Damage per enemy |
| `knockback` | 0.6 | Outward push |
| `affects` | `mobs` | Who it damages |

```json
{ "type": "whirlwind", "trigger": "on_right_click", "cooldownTicks": 60 }
```

---

### `slaying_edge`

Best trigger: `on_attack`. Bonus damage against a configurable **entity-type tag** (smite/bane as a knob).

| Param | Default | Description |
|-------|---------|-------------|
| `targetType` | `minecraft:undead` | Entity-type tag id the bonus applies to |
| `bonus` | 4.0 | Extra damage vs matching entities |

```json
{ "type": "slaying_edge", "trigger": "on_attack", "params": { "targetType": "minecraft:undead", "bonus": 5 } }
```

---

### `soul_harvest`

Best trigger: `on_kill`. On a kill: heal and refresh a short Strength + Speed buff that decays once you stop killing.

| Param | Default | Description |
|-------|---------|-------------|
| `heal` | 2.0 | HP restored per kill |
| `buffDuration` | 100 | Buff length in ticks (refreshed each kill) |
| `strengthAmplifier` | 0 | Strength level (0 = I) |
| `speedAmplifier` | 0 | Speed level (0 = I) |

```json
{ "type": "soul_harvest", "trigger": "on_kill", "params": { "heal": 3 } }
```

---

### `chain_lightning`

Best trigger: `on_attack`. Lightning arcs from the struck target to nearby enemies, losing damage each jump.

| Param | Default | Description |
|-------|---------|-------------|
| `jumps` | 3 | Max number of arcs (1–10) |
| `damage` | 4.0 | Damage on the first jump |
| `falloffPercent` | 0.3 | Damage lost per jump (0.0–0.9) |
| `jumpRange` | 5.0 | Reach between links |
| `affects` | `mobs` | Who it can chain to |

```json
{ "type": "chain_lightning", "trigger": "on_attack", "chance": 0.5, "cooldownTicks": 30 }
```

---

### `gravity_well`

Best trigger: `on_right_click`. Drops a pulsing zone ahead of you that drags enemies toward its center for a few seconds.

| Param | Default | Description |
|-------|---------|-------------|
| `range` | 4.0 | How far ahead the well is placed |
| `radius` | 5.0 | Pull radius |
| `pullStrength` | 0.6 | Inward pull per pulse |
| `duration` | 60 | Total lifetime in ticks |
| `period` | 5 | Ticks between pulses |
| `affects` | `mobs` | Who is pulled |

```json
{ "type": "gravity_well", "trigger": "on_right_click", "cooldownTicks": 120 }
```

---

### `ground_slam`

Best trigger: `on_right_click`. Leap, then a **telegraphed** delayed impact: AoE damage + outward knockback where you land.

| Param | Default | Description |
|-------|---------|-------------|
| `windup` | 10 | Ticks before the impact lands |
| `radius` | 4.0 | Impact radius |
| `damage` | 6.0 | Impact damage |
| `knockback` | 1.0 | Outward push |
| `hop` | 0.45 | Upward leap on cast |
| `affects` | `mobs` | Who is hit |

```json
{ "type": "ground_slam", "trigger": "on_right_click", "cooldownTicks": 80 }
```

---

## Utility Abilities

Movement and survival, all self-targeted.

### `phase_dash`

Best trigger: `on_right_click`. Blink in your look direction (clamped to safe blocks) **plus** a sliver of damage immunity.

| Param | Default | Description |
|-------|---------|-------------|
| `distance` | 5.0 | Max dash distance |
| `immunity` | 10 | Ticks of full damage immunity after the dash |
| `particle` | `minecraft:portal` | Burst at both ends |

```json
{ "type": "phase_dash", "trigger": "on_right_click", "cooldownTicks": 80 }
```

---

### `feather_fall`

Best trigger: `on_hurt`. Cancels fall damage by healing it back the tick after it lands.

| Param | Default | Description |
|-------|---------|-------------|
| `reducePercent` | 1.0 | Fraction of the fall damage healed back (0.0–1.0) |

```json
{ "type": "feather_fall", "trigger": "on_hurt" }
```

---

### `second_wind`

Best trigger: `on_hurt`. Once you drop below a HP fraction, burst-heal + Resistance + Absorption. Lock behind a long cooldown — it only fires (and only spends its cooldown) when you're actually low.

| Param | Default | Description |
|-------|---------|-------------|
| `threshold` | 0.35 | Fires when your HP ≤ this fraction of max |
| `heal` | 8.0 | HP restored |
| `resistDuration` | 100 | Resistance length in ticks |
| `resistAmplifier` | 1 | Resistance level (1 = II) |
| `absorptionDuration` | 200 | Absorption length in ticks |

```json
{ "type": "second_wind", "trigger": "on_hurt", "cooldownTicks": 1200 }
```

---

### `ender_recall`

Best trigger: `on_right_click`. First cast anchors your spot; a second cast within range and time teleports you back to it.

| Param | Default | Description |
|-------|---------|-------------|
| `maxDistance` | 32.0 | Max distance the recall will reach |
| `window` | 600 | Ticks the anchor stays valid |

> The anchor is per-player and in-memory only — it clears on disconnect, server stop, and dimension change.

```json
{ "type": "ender_recall", "trigger": "on_right_click", "cooldownTicks": 40 }
```

---

### `levitate_self`

Best trigger: `on_right_click`. A controlled upward boost for traversal — not flight.

| Param | Default | Description |
|-------|---------|-------------|
| `power` | 0.8 | Upward velocity |

```json
{ "type": "levitate_self", "trigger": "on_right_click", "cooldownTicks": 30, "params": { "power": 1.0 } }
```

---

## Support / Social Abilities

Buffs for teammates, aggro control, and pure-flair effects — the "for other players" tools.

### `rally_banner`

Best trigger: `on_right_click`. AoE buff to you and nearby allies; never reaches enemies.

| Param | Default | Description |
|-------|---------|-------------|
| `radius` | 6.0 | Buff radius |
| `duration` | 200 | Buff length in ticks |
| `amplifier` | 0 | Level applied to every buff (0 = I) |
| `buffs` | `["speed","resistance"]` | Effects to apply (see list below) |

Allowed `buffs` values: `speed`, `strength`, `resistance`, `regeneration`, `jump_boost`, `fire_resistance`, `absorption`, `slow_falling`.

```json
{ "type": "rally_banner", "trigger": "on_right_click", "cooldownTicks": 200, "params": { "buffs": ["speed", "regeneration"], "amplifier": 1 } }
```

---

### `taunt`

Best trigger: `on_right_click`. Forces nearby **hostile mobs** to target you — a tank tool for group PvE. Never affects players.

| Param | Default | Description |
|-------|---------|-------------|
| `radius` | 12.0 | Taunt radius |

```json
{ "type": "taunt", "trigger": "on_right_click", "cooldownTicks": 100 }
```

---

### `healing_totem`

Best trigger: `on_right_click`. Drops a stationary totem that pulses healing to you and nearby allies.

| Param | Default | Description |
|-------|---------|-------------|
| `radius` | 5.0 | Heal radius |
| `pulseAmount` | 1.0 | HP healed per pulse |
| `lifetime` | 200 | Total lifetime in ticks |
| `period` | 20 | Ticks between pulses |

```json
{ "type": "healing_totem", "trigger": "on_right_click", "cooldownTicks": 300 }
```

---

### `beacon_ping`

Best trigger: `on_right_click`. A tall particle beam + bell sound to mark a spot for everyone nearby. Pure communication, no gameplay effect.

| Param | Default | Description |
|-------|---------|-------------|
| `height` | 16.0 | Beam height in blocks |

```json
{ "type": "beacon_ping", "trigger": "on_right_click", "cooldownTicks": 20 }
```

---

### `firework_burst`

Best trigger: `on_kill`. A cosmetic celebration burst at the kill location.

*No params.*

```json
{ "type": "firework_burst", "trigger": "on_kill" }
```

---

### `glow_mark`

Best trigger: `on_attack`. Applies Glowing so teammates can track the target through walls. No damage.

| Param | Default | Description |
|-------|---------|-------------|
| `duration` | 120 | Glowing length in ticks |

```json
{ "type": "glow_mark", "trigger": "on_attack", "params": { "duration": 200 } }
```

---

### `prank_swap`

Best trigger: `on_right_click`. Swaps positions with the nearest other player in range — chaotic social utility, no damage.

| Param | Default | Description |
|-------|---------|-------------|
| `range` | 8.0 | Max distance to a swappable player |

```json
{ "type": "prank_swap", "trigger": "on_right_click", "cooldownTicks": 100 }
```

---

## Passives (`while_held`)

Passives fire on a periodic sweep — every **10 ticks (~0.5 s)** the server walks each player's main and off hand. For every Fiw item, every ability with `"trigger": "while_held"` (aliases: `passive`, `held`) is considered.

Steady self-buffs leave `cooldownTicks` at **0** — they re-apply a short effect each sweep (default `buffDuration: 40` = 2 s) so the buff persists while held and fades ~1 s after you unequip. Reactive passives (zone damage, debuff pulses, panic buttons) use `cooldownTicks` exactly like active abilities — only consumed on a sweep where the passive actually acts.

**Passive-specific keys (in `params`):**

| Param | Default | Description |
|-------|---------|-------------|
| `hand` | `either` | Slot filter — `main`, `off`, or `either` |
| `buffDuration` | varies | Effect duration in ticks (auto-refreshed each sweep) |
| `affects` | varies | For auras/reactives: who is touched (see [Targeting](#targeting-affects)) |
| `whenBelowHealth` | — | Only fire when `player.health / maxHealth ≤ value` (0.0–1.0) |
| `whenAboveHealth` | — | Only fire when `player.health / maxHealth ≥ value` |
| `whenUnderwater` | — | If `true`, only fire while submerged |
| `whenEnemyWithin` | — | Only fire when a hostile is within `value` blocks |
| `whenInCombat` | — | Only fire when last damage taken was within `value` ticks |
| `whenOutOfCombat` | — | Only fire when last damage taken was **more than** `value` ticks ago |

Conditions are AND-ed, and an absent key never gates. Use them so a single item can carry both "fight mode" and "rest mode" passives.

> Passives never hit the caster, never break blocks, and never spend their `cooldownTicks` unless they acted.

---

### `passive_buff`

The flexible buff engine. Reapplies a configurable list of effects each sweep — main vs off hand can carry different kits.

| Param | Default | Description |
|-------|---------|-------------|
| `buffs` | `["speed"]` | Effect names (haste, speed, strength, resistance, night_vision, water_breathing, fire_resistance, jump_boost, regeneration, dolphins_grace, conduit_power, …) |
| `amplifier` | 0 | Effect level (0 = level I) |
| `buffDuration` | 40 | Refresh duration (ticks) |
| `hand` | `either` | `main` / `off` / `either` |

```json
{ "type": "passive_buff", "trigger": "while_held", "params": { "hand": "off", "buffs": ["haste", "night_vision"], "amplifier": 0 } }
```

---

### `featherweight`

Slow Falling + clears fall distance every sweep — pure QoL, no fall damage while held.

| Param | Default | Description |
|-------|---------|-------------|
| `buffDuration` | 40 | Refresh duration |

```json
{ "type": "featherweight", "trigger": "while_held" }
```

---

### `aqua_kit`

Water Breathing + Dolphin's Grace + Night Vision — **only while submerged**. Off the rest of the time, so it isn't always-on.

| Param | Default | Description |
|-------|---------|-------------|
| `buffDuration` | 60 | Refresh duration |

```json
{ "type": "aqua_kit", "trigger": "while_held" }
```

---

### `thermal_ward`

Fire Resistance while held. Pair with `whenInCombat` or leave always-on.

| Param | Default | Description |
|-------|---------|-------------|
| `buffDuration` | 60 | Refresh duration |

```json
{ "type": "thermal_ward", "trigger": "while_held" }
```

---

### `saturation_aura`

Slowly tops off hunger and refreshes a touch of saturation. Mildly OP — gate behind a long `cooldownTicks`.

| Param | Default | Description |
|-------|---------|-------------|
| `foodLevel` | 20 | Target food level (does nothing once at or above) |
| `amount` | 1 | Hunger restored per fire |

```json
{ "type": "saturation_aura", "trigger": "while_held", "cooldownTicks": 200 }
```

---

### `magnet`

Continuously tugs nearby dropped items and XP orbs toward you. No cooldown — runs every sweep.

| Param | Default | Description |
|-------|---------|-------------|
| `radius` | 6.0 | Pull radius (blocks) |

```json
{ "type": "magnet", "trigger": "while_held", "params": { "radius": 8.0 } }
```

---

### `berserker`

Strength + Speed that scale up the lower your HP — nothing at full health, strongest near death. Rewards risk.

| Param | Default | Description |
|-------|---------|-------------|
| `maxAmplifier` | 2 | Cap on effect level at near-death |
| `buffDuration` | 40 | Refresh duration |

```json
{ "type": "berserker", "trigger": "while_held" }
```

---

### `combat_focus`

Haste + Speed, but **only while a hostile is within range**. On in fights, off while mining.

| Param | Default | Description |
|-------|---------|-------------|
| `radius` | 8.0 | Hostile detection radius |
| `amplifier` | 0 | Effect level |
| `buffDuration` | 40 | Refresh duration |

```json
{ "type": "combat_focus", "trigger": "while_held", "params": { "radius": 10, "amplifier": 1 } }
```

---

### `lifeline`

Slow Regeneration **only when out of combat** (no damage taken for `idleTicks`). Sustain, not in-fight healing.

| Param | Default | Description |
|-------|---------|-------------|
| `idleTicks` | 100 | Ticks since last damage before it kicks in |
| `amplifier` | 0 | Regen level |
| `buffDuration` | 40 | Refresh duration |

```json
{ "type": "lifeline", "trigger": "while_held" }
```

---

### `rally_aura`

Passive version of `rally_banner`: continuously buffs you and nearby allies. Never reaches enemies.

| Param | Default | Description |
|-------|---------|-------------|
| `radius` | 6.0 | Ally radius |
| `buffs` | `["speed","resistance"]` | Effect list |
| `amplifier` | 0 | Effect level |
| `buffDuration` | 40 | Refresh duration |

```json
{ "type": "rally_aura", "trigger": "while_held", "params": { "buffs": ["speed","regeneration"] } }
```

---

### `beacon_aura`

Single configurable buff radiating to you and nearby allies. Lightweight one-effect version.

| Param | Default | Description |
|-------|---------|-------------|
| `buff` | `"speed"` | Effect name |
| `radius` | 6.0 | Ally radius |
| `amplifier` | 0 | Effect level |
| `buffDuration` | 40 | Refresh duration |

```json
{ "type": "beacon_aura", "trigger": "while_held", "params": { "buff": "haste" } }
```

---

### `mending_aura`

Gentle healing pulse to you and nearby allies. Pace with `cooldownTicks`.

| Param | Default | Description |
|-------|---------|-------------|
| `radius` | 5.0 | Ally radius |
| `heal` | 1.0 | HP healed per pulse (each ally + you) |

```json
{ "type": "mending_aura", "trigger": "while_held", "cooldownTicks": 60 }
```

---

### `static_field`

Periodically zaps and lightly damages anything close. Great offhand "don't touch me." Default `affects: all`.

| Param | Default | Description |
|-------|---------|-------------|
| `radius` | 3.0 | Zap radius |
| `damage` | 1.0 | Magic damage per target |
| `affects` | `all` | `players` / `mobs` / `hostiles` / `all` |

```json
{ "type": "static_field", "trigger": "while_held", "cooldownTicks": 40, "params": { "affects": "hostiles" } }
```

---

### `repulse_ward`

Knocks back anything in range — no damage. Very annoying in PvP; cooldown-gated.

| Param | Default | Description |
|-------|---------|-------------|
| `radius` | 3.0 | Push radius |
| `knockback` | 0.8 | Horizontal push strength |
| `affects` | `all` | Scope |

```json
{ "type": "repulse_ward", "trigger": "while_held", "cooldownTicks": 60 }
```

---

### `chill_aura`

Applies short Slowness to nearby targets. Soft kiting tool.

| Param | Default | Description |
|-------|---------|-------------|
| `radius` | 4.0 | Aura radius |
| `duration` | 40 | Slowness duration |
| `amplifier` | 0 | Slowness level |
| `affects` | `all` | Scope |

```json
{ "type": "chill_aura", "trigger": "while_held", "cooldownTicks": 40, "params": { "affects": "hostiles" } }
```

---

### `blinding_flash`

Brief Blindness + Nausea on anything close. The most obnoxious — use a real cooldown.

| Param | Default | Description |
|-------|---------|-------------|
| `radius` | 4.0 | Flash radius |
| `duration` | 40 | Debuff duration |
| `affects` | `all` | Scope |

```json
{ "type": "blinding_flash", "trigger": "while_held", "cooldownTicks": 200 }
```

---

### `spore_cloud`

Pulses Poison + Hunger onto adjacent enemies. DoT zone.

| Param | Default | Description |
|-------|---------|-------------|
| `radius` | 3.0 | Cloud radius |
| `duration` | 60 | DoT duration |
| `amplifier` | 0 | DoT level |
| `affects` | `all` | Scope |

```json
{ "type": "spore_cloud", "trigger": "while_held", "cooldownTicks": 80, "params": { "affects": "hostiles" } }
```

---

### `thorn_pulse`

Reflect-style retaliation: only fires when you were hit within `combatWindow` ticks. Passive cousin of `parry_counter`.

| Param | Default | Description |
|-------|---------|-------------|
| `combatWindow` | 40 | Must have taken damage within this many ticks |
| `radius` | 3.0 | Lash radius |
| `damage` | 2.0 | Magic damage per target |
| `affects` | `all` | Scope |

```json
{ "type": "thorn_pulse", "trigger": "while_held", "cooldownTicks": 30 }
```

---

### `coward_mark`

Glows nearby enemies so they can't sneak up. No damage — pure info.

| Param | Default | Description |
|-------|---------|-------------|
| `radius` | 8.0 | Detection radius |
| `duration` | 120 | Glowing duration |
| `affects` | `all` | Scope |

```json
{ "type": "coward_mark", "trigger": "while_held", "cooldownTicks": 100 }
```

---

### `hornet_swarm`

On a cooldown, auto-pesters the **nearest** threat with particle stings and a tiny shove. Flavor + light harass.

| Param | Default | Description |
|-------|---------|-------------|
| `radius` | 5.0 | Search radius |
| `affects` | `all` | Scope |

```json
{ "type": "hornet_swarm", "trigger": "while_held", "cooldownTicks": 40 }
```

---

### `curse_pulse`

Nags nearby enemies with Weakness + Mining Fatigue. PvP debuff zone.

| Param | Default | Description |
|-------|---------|-------------|
| `radius` | 4.0 | Pulse radius |
| `duration` | 60 | Debuff duration |
| `amplifier` | 0 | Debuff level |
| `affects` | `all` | Scope |

```json
{ "type": "curse_pulse", "trigger": "while_held", "cooldownTicks": 60 }
```

---

### `last_stand`

When you drop below a HP fraction: Resistance + Absorption + Totem particles/sound. The passive panic button — pair with a long `cooldownTicks`.

| Param | Default | Description |
|-------|---------|-------------|
| `threshold` | 0.3 | HP fraction trigger |
| `duration` | 100 | Resistance duration |
| `resistAmplifier` | 1 | Resistance level |
| `absorptionDuration` | 200 | Absorption duration |

```json
{ "type": "last_stand", "trigger": "while_held", "cooldownTicks": 1200 }
```

---

### `adrenaline`

Speed + Strength the moment you take a hit, decaying after. "Wake up" in fights.

| Param | Default | Description |
|-------|---------|-------------|
| `combatWindow` | 30 | Must have taken damage within this many ticks |
| `buffDuration` | 40 | Buff duration |
| `speedAmplifier` | 0 | Speed level |
| `strengthAmplifier` | 0 | Strength level |

```json
{ "type": "adrenaline", "trigger": "while_held", "cooldownTicks": 60 }
```

---

### `shield_battery`

Maintains a small Absorption shield while held. Refreshes back to the cap on its `cooldownTicks`.

| Param | Default | Description |
|-------|---------|-------------|
| `shield` | 4.0 | Absorption cap (HP) |

```json
{ "type": "shield_battery", "trigger": "while_held", "cooldownTicks": 200 }
```

---

## Worn passives (`while_worn`)

The same passive sweep that drives `while_held`, applied to armor. Every 10 ticks the server walks each player's four armor slots (head, chest, legs, feet) and runs every ability with `"trigger": "while_worn"` (aliases: `worn`, `armor`) on the worn stack — same cooldown / chance / condition machinery, same param shape. **All 24 existing passives work unchanged** as `while_worn` — `featherweight` boots, `combat_focus` helmet, `rally_aura` chestplate, `passive_buff` on any piece, etc.

**The only new param is `slot`** — filters which armor slots the passive responds to. Defaults to `any` so a passive declared on an item works in any slot the item is wearable in.

| `slot` value | Fires when worn in |
|--------------|--------------------|
| `head` | helmet slot only |
| `chest` | chestplate slot only |
| `legs` | leggings slot only |
| `feet` | boots slot only |
| `any` (default) | any of the four armor slots |

All the `whenBelowHealth` / `whenEnemyWithin` / `whenUnderwater` / `whenInCombat` / `whenOutOfCombat` gates from `while_held` work identically.

### Examples

Featherweight boots — slow falling while worn on feet:
```json
{ "type": "featherweight", "trigger": "while_worn", "params": { "slot": "feet" } }
```

Combat focus helmet — Haste + Speed when an enemy is within 10 blocks, only when worn on head:
```json
{ "type": "combat_focus", "trigger": "while_worn",
  "params": { "slot": "head", "radius": 10, "amplifier": 1, "buffDuration": 40 } }
```

Aqua chestplate — Water Breathing + Dolphin's Grace + Night Vision while underwater:
```json
{ "type": "aqua_kit", "trigger": "while_worn", "params": { "slot": "chest", "buffDuration": 80 } }
```

Static field on any armor slot — annoying ward that zaps anything nearby:
```json
{ "type": "static_field", "trigger": "while_worn", "cooldownTicks": 40,
  "params": { "slot": "any", "radius": 3, "damage": 1, "affects": "hostiles" } }
```

> An item can carry **both** `while_held` and `while_worn` abilities side-by-side. They never overlap — held abilities only fire when in main/off hand, worn abilities only fire when in an armor slot. Imbued abilities work the same way.

---

## Curse System

A cursed Fiw item kills anyone who carries it unless they are on its whitelist. Curses are admin-applied via command, written to the item's JSON (so they survive restarts), and locked against renaming, repair, enchanting and grindstones. The only ways to lift a curse are admin commands or the `uncurse` ability.

### Definition fields

```json
{
  "id": "forbidden_blade",
  "base": "minecraft:netherite_sword",
  "displayName": "&4&lForbidden Blade",
  "cursed": true,
  "curseWhitelist": [
    "Fi3w0|8c4d1e9a-1f2b-4c3d-8e5a-1234567890ab"
  ],
  "curseSettings": {
    "perTick": 1.0,
    "ignoreArmor": true,
    "ignoreResistance": true,
    "checksEnderChest": false,
    "sound": "minecraft:entity.wither.ambient",
    "particles": "minecraft:sculk_soul"
  }
}
```

| Field | Default | Description |
|-------|---------|-------------|
| `cursed` | `false` | Turn the curse on for this item id |
| `curseWhitelist` | `[]` | Exempt players. Each entry is a name, a UUID, or `"name\|uuid"`. Empty list = nobody is safe (trap item) |
| `curseSettings.perTick` | `1.0` | HP drained per second from non-whitelisted holders |
| `curseSettings.ignoreArmor` | `true` | Damage bypasses armor (via the `fiw_tools:curse` damage type tag) |
| `curseSettings.ignoreResistance` | `true` | Bypasses Resistance and Protection enchantments |
| `curseSettings.checksEnderChest` | `false` | **If true, hiding the item in your ender chest still curses you.** Catches the "stash it until I need it" trick |
| `curseSettings.sound` | `entity.wither.ambient` | Played at the victim each tick |
| `curseSettings.particles` | `sculk_soul` | Spawned around the victim each tick |

### How a curse behaves in-game

- A 20-tick sweep walks each online player's hotbar, main inventory, armor, off hand, **and** ender chest (only if any cursed item carried has `checksEnderChest: true`). The ender-chest pass is opt-in to keep the hot path cheap.
- For every cursed stack on a non-whitelisted player, `perTick` HP is drained using a custom `fiw_tools:curse` damage source tagged `bypasses_armor` / `bypasses_resistance` / `bypasses_enchantments` / `bypasses_effects` / `no_knockback`. Default 1 HP/sec kills a healthy player in 20 s — short enough to be punishing, long enough to realise and drop it.
- Stacking two cursed items doubles the drain per sweep.
- Cursed items **always drop on death**, even if `keepOnDeath: true` was set. The curse can't be cheesed by suicide.
- Cursed items can't be put through anvils (rename, repair, book-enchant), enchanting tables (the `enchantable` component is stripped), or grindstones. The grindstone block is a feature of every Fiw item; the anvil block is curse-specific.
- A specific stack can be marked safe forever with the `fiw_uncursed` custom_data flag (set by the uncurse ability or `/fiwtools uncurse_held`). The flag is preserved across `/fiwtools reload`.

### Uncurse ability (`uncurse`)

Lives on a normal Fiw item — the canonical use is a consumable scroll. On `on_right_click`, scans the player's inventory and stamps `fiw_uncursed: 1b` onto cursed stacks it finds, lifting their curse permanently. Right-clicks that find nothing to uncurse cost no cooldown.

| Param | Default | Description |
|-------|---------|-------------|
| `scope` | `all_inventory` | `main_hand` / `off_hand` / `armor` / `all_inventory` |
| `limit` | `1` | How many cursed items per use. `0` = uncurse all of them |
| `consumeSelf` | `true` | Shrink the scroll by 1 on success |
| `sound` | `block.beacon.activate` | Played on success |
| `particles` | `end_rod` | Spawned on success |

```json
{
  "id": "blessed_scroll",
  "base": "minecraft:paper",
  "displayName": "&e&lBlessed Scroll",
  "rarity": "rare",
  "abilities": [
    { "type": "uncurse", "trigger": "on_right_click",
      "params": { "scope": "all_inventory", "limit": 1, "consumeSelf": true } }
  ]
}
```

### Commands (op-level)

```
/fiwtools curse add <itemId>
/fiwtools curse remove <itemId>
/fiwtools curse list <itemId>
/fiwtools curse whitelist add <itemId> <player>
/fiwtools curse whitelist remove <itemId> <nameOrUuid>
/fiwtools uncurse_held
```

All `add` / `remove` / `whitelist` commands rewrite the backing JSON in place — every other key is preserved verbatim — and then trigger a full reload + resync. `uncurse_held` is an admin escape valve that flags the specific stack in your main hand as permanently uncursed.

---

## Imbuing System

An admin-defined upgrade system. A **catalyst** is a normal Fiw item carrying the `imbue` ability; when right-clicked it scans the player's off hand + armor for a valid **target**, rolls one **outcome** from its outcome list, and applies the outcome's `mods` block (enchantments, attributes, abilities, lore) to the target. Outcomes can be deterministic or weighted-random, the admin's choice. Each target tracks how many times it's been imbued; cap reached = the catalyst refuses with an admin-set message.

### Catalyst definition

```json
{
  "id": "spark_of_storms",
  "base": "minecraft:nether_star",
  "displayName": "&b&lSpark of Storms",
  "lore": ["&7Hold a sword in your off hand and right-click."],
  "rarity": "epic",
  "abilities": [
    {
      "type": "imbue",
      "trigger": "on_right_click",
      "params": {
        "targets": { "tags": ["#minecraft:swords"] },
        "rng": true,
        "maxUses": 5,
        "maxImbuements": 3,
        "outcomes": [
          { "name": "&7Faint Spark",      "weight": 60, "mods": { "enchantments": { "minecraft:sharpness": 6 } } },
          { "name": "&bStorm Spark",      "weight": 35, "mods": {
              "enchantments": { "minecraft:sharpness": 10 },
              "attributes": [
                { "type": "minecraft:attack_damage", "slot": "mainhand", "amount": 2, "operation": "add_value" }
              ]
          } },
          { "name": "&5&lThunderstrike",  "weight": 5,
            "chat": "&5✦ &dThunderstrike has been awakened!",
            "mods": {
              "enchantments": { "minecraft:sharpness": 15 },
              "addAbilities": [
                { "type": "chain_lightning", "trigger": "on_attack", "chance": 0.5, "cooldownTicks": 30 }
              ],
              "appendLore": ["&5Thunderstrike awakened."]
          } },
          { "name": "&4Cracked",          "weight": 10,
            "mods": { "enchantments": { "minecraft:sharpness": 1 }, "appendLore": ["&4The spark cracked the blade."] }
          }
        ],
        "messages": {
          "noTarget":  "&cHold a sword in your off hand.",
          "wrongType": "&cThis spark only works on swords.",
          "capReached":"&6&lThis blade can take no more sparks.",
          "cursed":    "&cCursed blades refuse the spark.",
          "noOutcomes":"&cThis catalyst is misconfigured (no outcomes)."
        }
      }
    }
  ]
}
```

### Top-level `params`

| Key | Default | Description |
|-----|---------|-------------|
| `targets` | `{}` (match anything) | What kinds of items this catalyst can imbue. See below. |
| `rng` | `false` | `true` = weighted random across outcomes. `false` = always uses the first outcome (deterministic). |
| `maxUses` | `1` | How many imbuements a single catalyst stack performs before it's consumed. `0`/negative = unlimited (debug). |
| `maxImbuements` | `3` | Fallback cap when the target has no `imbueLimit` field of its own. |
| `outcomes` | — | The outcome pool (see below). Required. |
| `messages` | — | Optional admin-set refusal messages keyed by `noTarget`, `wrongType`, `capReached`, `cursed`, `outOfCharges`, `noOutcomes`. An empty string silences the refusal. |

### `targets` filter

Any one of three sub-lists makes a match (they OR together). Leave the block empty to match anything.

| Key | Description |
|-----|-------------|
| `items` | Array of Fiw item ids — `["forbidden_blade", "obsidian_axe"]` |
| `bases` | Array of vanilla base ids — `["minecraft:netherite_sword"]` |
| `tags` | Array of item tags — `["#minecraft:swords", "#minecraft:trimmable_armor"]` |

### Outcome shape

| Key | Description |
|-----|-------------|
| `name` | Flavor text shown to the player on use (`&`-color codes work) |
| `weight` | Used in RNG mode (default 1). Ignored when `rng: false`. |
| `chat` | Optional server-wide broadcast for rare god-roll outcomes |
| `mods` | The actual changes (see below). All sub-keys optional. |

### `mods` block

| Key | Effect |
|-----|--------|
| `enchantments` | Map of `id: level`. **Sets** the level on the target — bad rolls genuinely override good rolls. Above-vanilla levels (10, 15…) are honored. |
| `attributes` | Array of attribute modifiers in the same format as the top-level `attributes` block. Appended (stacks across imbuements). |
| `addAbilities` | Array of `AbilityDef` entries. Appended via the imbue log; works for active and passive abilities both. |
| `appendLore` | Array of lore lines appended to the existing lore. |
| `setDisplayName` | Optional rename. |

### Target-side `imbueLimit`

A Fiw item can declare its own ceiling that overrides any catalyst's `maxImbuements`:

```json
{
  "id": "ancient_blade",
  "base": "minecraft:netherite_sword",
  "imbueLimit": 2
}
```

| Value | Meaning |
|-------|---------|
| `null` (field absent) | Catalyst's `maxImbuements` applies |
| `0` | Item is locked from imbuing — any catalyst refuses with `capReached` |
| `-1` | Unlimited (debug) |
| `1+` | Hard cap, no matter what catalyst is used |

This is how you ship an OP weapon that can take 1–2 risky imbuements at most.

### Reserved custom_data keys

| Key | Stored on | Meaning |
|-----|-----------|---------|
| `fiw_imbue_count` | Target stack | How many times this stack has been imbued |
| `fiw_imbue_log` | Target stack | JSON-encoded array of every applied `mods` block — replayed on every rebuild so imbuements survive `/fiwtools reload` |
| `fiw_imbue_uses` | Catalyst stack | Charges already spent on this catalyst |

### Commands (op-level)

```
/fiwtools imbue best  <catalystId>   — apply the highest-weight outcome of <catalystId> to the off-hand target
/fiwtools imbue roll  <catalystId>   — roll a fresh outcome at the catalyst's actual odds
/fiwtools imbue reset                — zero out fiw_imbue_count on held main-hand stack (keeps mods, reopens the cap)
/fiwtools imbue clear                — wipe imbue log + count and rebuild the held main-hand stack from its config
/fiwtools imbue log                  — print the held main-hand stack's imbuement history
```

`best` ignores `rng` so admins can reward proven feats with the catalyst's god-roll without RNG luck.

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

Internally, Fiw Bosses calls the stable public API documented below. If Fiw Tools is missing, the lookup is silently skipped (the loot entry drops nothing, the equipment slot stays empty) — boss configs never crash because of a missing Tools install.

The reverse direction does **not** apply: Fiw Tools never reads Fiw Bosses. The two mods can be released, updated, and run independently.

---

## Public API for Other Mods

Other mods can integrate with Fiw Tools without depending on its internals. FIW Bosses uses this same API by reflection, so these signatures are kept stable across loader modules:

```text
com.fiw.tools.api.FiwToolsAPI.isLoaded(): boolean
com.fiw.tools.api.FiwToolsAPI.listIds(): Set<String>
com.fiw.tools.api.FiwToolsAPI.getItemStack(String id, MinecraftServer server, int count): ItemStack?
com.fiw.tools.api.FiwToolsAPI.getItemStack(String id, HolderLookup.Provider registries, int count): ItemStack?
```

### Behavior

| Method | Behavior |
|--------|----------|
| `isLoaded()` | Returns `true` when Fiw Tools is present and the API class loaded successfully. |
| `listIds()` | Returns the currently loaded item ids. Empty means no item configs have loaded yet. |
| `getItemStack(id, server, count)` | Builds a fresh stack from the currently loaded config id. Returns `null` if the id is unknown or cannot be built. |
| `getItemStack(id, registries, count)` | Same lookup when the caller already has registry access. |

### Failure rules

- Unknown ids return `null`.
- Missing Fiw Tools should be treated as "skip this optional item", not as a hard error.
- Returned stacks are fresh copies; callers can safely place them in loot drops, inventories, or equipment slots.
- The API does not force-load configs. Call it after the server has started and Fiw Tools has loaded its registry.

### Reflection-safe lookup

Mods that want a soft dependency can mirror FIW Bosses' approach:

```java
Class<?> api = Class.forName("com.fiw.tools.api.FiwToolsAPI");
Method getItemStack = api.getMethod("getItemStack", String.class, MinecraftServer.class, int.class);
ItemStack stack = (ItemStack) getItemStack.invoke(null, "void_reaver", server, 1);
```

---

## Behavior Notes

**Server-side guarantee**
- Fiw Tools has no client-side code; install it on the server only.
- On Fabric, fully vanilla clients can connect with no Fiw Tools install.
- On NeoForge, clients do not need Fiw Tools installed to join.
- All custom data, lore, attributes, and ability effects are server-authoritative.

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
