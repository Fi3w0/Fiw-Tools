# Fiw Custom Items — Item Configuration Guide

Items are defined as `.json` files — no coding, no client install, no server restart needed.

| Path | Purpose |
|------|---------|
| `config/fiw_tools/items/` | Active item definitions |
| `examples/items/` | Pre-built items ready to copy |

> **Server-side only.** Fiw Custom Items has no client-side code, so it installs on the server only (Fabric or NeoForge) and players never need the mod to join. On Fabric, even fully vanilla clients can connect.

> **Hot reload:** `/fiwtools reload` — picks up all item changes instantly.

> **Compatibility note:** the public name is Fiw Custom Items, but the internal mod id, config folder, `/fiwtools` command, and `FiwToolsAPI` class keep the Fiw Tools naming for existing servers and integrations.

> **Version note:** Some newer Minecraft data components do not exist on 1.21.1 or lower. Those features use version-specific fallback systems where possible; each affected section notes the exact behavior.

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
  - Original set: [riptide_dash](#riptide_dash) · [arc_slash](#arc_slash) · [arc_slash_barrage](#arc_slash_barrage) · [arc_slash_storm](#arc_slash_storm) · [beam](#beam) · [slash_wave](#slash_wave) · [arrow_rain](#arrow_rain) · [sonic_boom](#sonic_boom) · [lightning_strike](#lightning_strike) · [shockwave](#shockwave) · [heal_on_hit](#heal_on_hit) · [blink](#blink) · [projectile_burst](#projectile_burst) · [frost_nova](#frost_nova)
  - PvP: [grappling_pull](#grappling_pull) · [disarm](#disarm) · [parry_counter](#parry_counter) · [execute](#execute) · [leech](#leech) · [silence_sigil](#silence_sigil) · [tether](#tether)
  - PvE: [cleave](#cleave) · [whirlwind](#whirlwind) · [slaying_edge](#slaying_edge) · [soul_harvest](#soul_harvest) · [chain_lightning](#chain_lightning) · [gravity_well](#gravity_well) · [ground_slam](#ground_slam)
  - Active debuffs: [ignite](#ignite) · [wither_touch](#wither_touch) · [bleed](#bleed)
  - Utility: [phase_dash](#phase_dash) · [grapple](#grapple) · [feather_fall](#feather_fall) · [second_wind](#second_wind) · [ender_recall](#ender_recall) · [levitate_self](#levitate_self)
  - Support / social: [rally_banner](#rally_banner) · [taunt](#taunt) · [healing_totem](#healing_totem) · [summon](#summon) · [beacon_ping](#beacon_ping) · [firework_burst](#firework_burst) · [glow_mark](#glow_mark) · [prank_swap](#prank_swap)
  - [Passives (`while_held` / `while_sneaking`)](#passives-while_held)
    - Held self-buffs: [passive_buff](#passive_buff) · [featherweight](#featherweight) · [aqua_kit](#aqua_kit) · [thermal_ward](#thermal_ward) · [saturation_aura](#saturation_aura) · [magnet](#magnet) · [berserker](#berserker) · [combat_focus](#combat_focus) · [lifeline](#lifeline)
    - Holder debuffs (balance): [holder_debuff](#holder_debuff)
    - Allied auras: [rally_aura](#rally_aura) · [beacon_aura](#beacon_aura) · [mending_aura](#mending_aura)
    - Reactive defense: [static_field](#static_field) · [repulse_ward](#repulse_ward) · [chill_aura](#chill_aura) · [blinding_flash](#blinding_flash) · [spore_cloud](#spore_cloud) · [thorn_pulse](#thorn_pulse) · [coward_mark](#coward_mark) · [hornet_swarm](#hornet_swarm) · [curse_pulse](#curse_pulse) · [decay_aura](#decay_aura) · [ember_aura](#ember_aura)
    - Conditional survival: [last_stand](#last_stand) · [adrenaline](#adrenaline) · [shield_battery](#shield_battery)
- **Ice:** [ice_lance](#ice_lance) · [blizzard](#blizzard) · [glacial_shell](#glacial_shell)
- **Water:** [tidal_surge](#tidal_surge)
- **Fire:** [flame_dash](#flame_dash) · [meteor_strike](#meteor_strike)
- **Blood:** [blood_pact](#blood_pact) · [hemorrhage](#hemorrhage) · [sanguine_strike](#sanguine_strike)
- **[Resonance / Set Bonus](#resonance--set-bonus-system):** `resonanceId` + `resonanceRequires` fields — abilities with `trigger: resonance`
- **[Elemental Status](#elemental-status-system):** [freeze](#freeze) · [soak](#soak) · [shock](#shock) · [thaw_burst](#thaw_burst) · [storm_chain](#storm_chain)
- **[Soul System](#soul-system):** [soul_collector](#soul_collector) · [soul_surge](#soul_surge)
- **[Custom Crafting Recipes](#custom-crafting-recipes):** shaped + shapeless JSON recipes in `config/fiw_tools/recipes/`
- **[Infinite Use Items](#infinite-use-items):** `infinite` field — `keep` / `damage` / `replace` modes
- **[Recharge on Kill](#recharge-on-kill):** `rechargeOnKill` field — kill-based cooldown and durability rewards
- **[Artifact Awakening](#artifact-awakening):** `awakening` field — kill / damage / dimension triggers
- **[Bound Artifacts](#bound-artifacts):** `binding` field — first_use / first_pickup + thief curse
- **[Item Commands](#item-commands-run_command):** [run_command](#item-commands-run_command) ability + `on_shift_right_click` trigger
- **[Charges and Use Costs](#charges-and-use-costs):** `charges` pool + per-ability `cost` block
- **[Cooldown Groups](#cooldown-groups):** shared cooldowns across abilities and items
- **[Ability Conditions](#ability-conditions):** `whenBelowHealth`, `whenDimension`, `whenNight`, … on any trigger
- **[Dynamic Lore](#dynamic-lore):** live lore placeholders (`{owner}`, `{charges}`, `{souls}`, …)
- **[Enchanting Control](#enchanting-control):** `enchanting` field — per-item table/anvil/grindstone/smithing permissions + name lock
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
| `enchanting` | object | null | Per-station enchanting permissions — see [Enchanting Control](#enchanting-control) |
| `customData` | object | null | Free-form NBT passthrough — survives in `custom_data` |
| `abilities` | array | [] | Custom triggered effects (see below) |
| `infinite` | string/object | null | Infinite-use behaviour — see [Infinite Use Items](#infinite-use-items) |
| `rechargeOnKill` | object | null | Kill reward for cooldowns and durability — see [Recharge on Kill](#recharge-on-kill) |
| `awakening` | object | null | Auto-upgrade condition — see [Artifact Awakening](#artifact-awakening) |
| `binding` | object | null | Ownership binding — see [Bound Artifacts](#bound-artifacts) |
| `charges` | int/object | null | Universal charge pool — see [Charges and Use Costs](#charges-and-use-costs) |
| `dynamicLore` | string array | [] | Live lore lines — see [Dynamic Lore](#dynamic-lore) |
| `potionColor` | string | null | Potion liquid color (`"#RRGGBB"`), for potion/tipped-arrow bases |
| `dyedColor` | string | null | Dye color (`"#RRGGBB"`), for leather armor and dyeables |
| `itemModel` | string | null | Client model to render, e.g. `minecraft:trident` (not on 1.21.1) |

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

## Enchanting Control

The `enchanting` root field decides what players may do with the item at each enchanting station.
Every field is optional — omitted fields keep the long-standing defaults, so existing configs
behave exactly as before.

```json
"enchanting": {
  "table": true,
  "anvil": false,
  "grindstone": false,
  "smithing": true,
  "allowRename": false,
  "allowEnchant": false
}
```

That example is armor players can enchant at the **enchanting table**, but can't touch at the
**anvil** (no books, no repair, no combining) and can't strip at the **grindstone** — and even if
the anvil were allowed, `allowRename: false` keeps its configured name forever.

| Field | Default | Description |
|-------|---------|-------------|
| `table` | `true` (`false` if `cursed`) | Enchanting table. `false` strips the `enchantable` component (1.21.2+) and zeroes all three offers server-side. Alias: `enchantingTable` |
| `anvil` | `true` (`false` if `cursed`) | Anvil use with this item in **either** slot: rename, repair, book-enchant, combine. `false` force-clears the result |
| `grindstone` | `false` | Disenchanting at the grindstone. Fiw items are blocked by default so their configured enchantments are permanent; `true` allows the single-item disenchant. A two-item grindstone merge involving any Fiw item stays blocked regardless — two customs can never be ground into one |
| `smithing` | `true` | Smithing table (netherite upgrades, armor trims) with this item in any input slot |
| `allowRename` | `true` | Whether an anvil rename sticks. `false` still lets players use the anvil for enchantments/repair, but the configured name is forced back onto the result; a rename-only operation yields nothing. Renames are also wiped on `/fiwtools reload` re-sync. Alias: `rename` |
| `allowEnchant` | `true` | Whether players may **add** enchantments at the anvil (books, combining two enchanted copies). `false` still lets the anvil rename and repair the item, but any result that would add or change an enchantment is refused — the inverse of `allowRename`. Also blocks the item being used as an enchant **donor**: a locked item in the sacrifice slot can't transfer its enchantments onto another item. Alias: `enchant` |

Notes:

- **Attribute lock:** a Fiw item's configured `attributes` are always re-stamped onto anvil and
  smithing-table results, so a rename, repair, book-enchant, netherite upgrade or trim can never
  leave it with attributes other than the ones its config declares. Imbued attribute bonuses are
  replayed on top of the rebuild, so player-earned imbuements survive the station. This is
  automatic — no field to set — and only affects Fiw items that declare `attributes`.
- Non-Fiw items are never affected — vanilla stays vanilla.
- Explicit values override the curse defaults: a cursed item with `"anvil": true` is repairable.
- Player-added enchantments survive `/fiwtools reload` (config enchantments always come back at
  their configured level); see [Curse System](#curse-system) for how cursed items interact.
- To transfer enchantments through a **crafting** upgrade instead, see
  [Keeping Enchantments When Crafting](#keeping-enchantments-when-crafting).

---

## Attribute Modifiers

Two equivalent formats — full ids and bare constant-style names both work, in any case:

```json
"attributes": [
  { "type": "minecraft:generic.attack_damage", "slot": "mainhand", "amount": 12.0, "operation": "add_value" },
  { "type": "ATTACK_DAMAGE",  "slot": "MAINHAND", "amount": 6.0,  "operation": "ADD_VALUE" },
  { "type": "movement_speed", "slot": "mainhand", "amount": 0.05, "operation": "add_multiplied_total" }
]
```

Bare names are normalised automatically (`ATTACK_DAMAGE`, `GENERIC_ATTACK_DAMAGE`,
`generic.attack_damage` and `attack_damage` are all the same attribute), and the right
per-version id prefix is applied for you — the same config works on 1.21.1 and newer.

| Field | Default | Description |
|-------|---------|-------------|
| `type` | **required** | Attribute id or bare name, any case |
| `slot` | `any` | `mainhand` / `offhand` / `head` / `chest` / `legs` / `feet` / `hand` / `armor` / `body` / `any` (any case) |
| `amount` | 0.0 | Numeric value (sign matters for attack_speed) |
| `operation` | `add_value` | `add_value` (flat) / `add_multiplied_base` / `add_multiplied_total`; legacy `addition` / `multiply_base` / `multiply_total` also accepted |
| `id` | auto | Optional unique modifier id; auto-generated if omitted |

**All vanilla attributes** (usable on every supported version):
`armor`, `armor_toughness`, `attack_damage`, `attack_knockback`, `attack_speed`,
`block_break_speed`, `block_interaction_range`, `burning_time`, `entity_interaction_range`,
`explosion_knockback_resistance`, `fall_damage_multiplier`, `flying_speed`, `follow_range`,
`gravity`, `jump_strength`, `knockback_resistance`, `luck`, `max_absorption`, `max_health`,
`mining_efficiency`, `movement_efficiency`, `movement_speed`, `oxygen_bonus`,
`safe_fall_distance`, `scale`, `sneaking_speed`, `spawn_reinforcements`, `step_height`,
`submerged_mining_speed`, `sweeping_damage_ratio`, `water_movement_efficiency`.

Newer versions only: `tempt_range`, `camera_distance`, `waypoint_transmit_range`,
`waypoint_receive_range` (26.x). Unknown names log a warning and are skipped.

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

Free-form NBT — readable by other mods, datapacks, or `/data get`. Two keys are reserved by Fiw Custom Items: `fiw_tools_id` (always added so the item can be looked up) and `fiw_keep_on_death` (set when `keepOnDeath: true`).

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
| `cooldownGroup` | null | Shared cooldown group name — see [Cooldown Groups](#cooldown-groups) |
| `chance` | 1.0 | Roll per fire (0.0–1.0) |
| `cost` | null | Resources consumed per fire — see [Charges and Use Costs](#charges-and-use-costs) |
| `params` | {} | Ability-specific knobs. Every ability also accepts [condition keys](#ability-conditions) |

**Triggers:**

| Trigger | Fires when |
|---------|------------|
| `on_right_click` | Player right-clicks holding the item |
| `on_shift_right_click` | Player right-clicks **while sneaking** (aliases: `shift_right_click`, `sneak_right_click`). When an item defines this, plain `on_right_click` stops firing during sneak — one item, two right-click ability sets. Items without it behave exactly as before |
| `on_attack` | Player damages an entity with the item |
| `on_kill` | Player kills an entity with the item in main hand |
| `on_hurt` | Player takes damage while holding the item |
| `on_block_break` | Player breaks a block while holding the item |
| `on_boss_kill` | Player kills a boss with the item in main hand. Default bosses: ender dragon, wither, warden, elder guardian, and every FIW Bosses entity; override per ability with `"params": { "bosses": ["minecraft:wither"] }` |
| `on_consume` | Player finishes eating/drinking the item |
| `on_pickup` | Player picks the item up off the ground |
| `on_drop` | Player deliberately drops the item (death drops don't count) |
| `on_death` | Player dies — fires for every Fiw item in their inventory |
| `on_respawn` | Player respawns — fires for every Fiw item they still carry |
| `on_enter_dimension` | Player arrives in a dimension. Filter with `"params": { "dimension": "minecraft:the_nether" }` |
| `on_leave_dimension` | Player leaves a dimension. Same `dimension` param filter |
| `on_equip` | The item is equipped (armor or hand). Filter with `"params": { "slot": "head" }` |
| `on_unequip` | The item is unequipped. Same `slot` param filter |
| `on_shoot` | An arrow leaves the player's Fiw bow/crossbow |
| `on_arrow_hit` | The player's arrow (shot from a Fiw weapon) hits a living entity — target is the victim |
| `on_shield_block` | The player blocks damage with a blocking item while holding this item; target is the attacker, damage amount = blocked amount |
| `on_crit` | The player lands a critical melee hit while holding this item |
| `on_block_place` | The player places a block while holding this item (either hand) |
| `on_fall` | The player takes fall damage. Gate with `"params": { "minDistance": 10 }` (blocks fallen; damage amount = distance) |
| `on_charge_release` | The player releases a held charge on a `chargeable` item. Gate tiers with `"params": { "minChargeTicks": 20, "maxChargeTicks": 59 }` (charge ticks arrive as the damage amount) |
| `while_held` | Periodic sweep (every 10 ticks ≈ 0.5 s) while the item is in your hand — see [Passives](#passives-while_held) |
| `while_worn` | Same sweep but for armor slots — see [Worn passives](#worn-passives-while_worn) |
| `while_sneaking` | Same as `while_held` / `while_worn` but **only fires when the player is crouching** — use for sneak-activated passives |

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

### `arc_slash_barrage`

Cinematic single-target combo: speaks a line, plays a
charge-up sound, then carves the victim with a volley of arc slashes rolled at different angles,
and finishes by teleporting behind them for one heavy armor-ignoring hit. The victim is the
attacked entity on `on_attack`, or whatever is under the crosshair (up to `targetRange`) on
right-click triggers — mobs and players alike. With no victim it still fires, carving the air
`frontDistance` blocks ahead (slashes hit anything that steps in); only the teleport finisher
needs a locked target.

| Param | Default | Description |
|-------|---------|-------------|
| `rolls` | `[0, 45, 90, -45, 30, -30, 15, -15, 60, -60]` | One slash per entry; slash-plane tilt in degrees (0 = flat, 90 = vertical) |
| `startDelay` | 8 | Ticks between the opening line and the first slash |
| `slashInterval` | 3 | Ticks between slashes |
| `targetRange` | 16.0 | Crosshair target search range (right-click triggers) |
| `frontDistance` | 3.0 | Where slashes land when there is no victim: blocks ahead of the caster |
| `damage` | 4.0 | Damage per slash |
| `knockback` | 0.3 | Push per slash hit (0 = none) |
| `width` | 6.0 | Slash size in blocks |
| `arc` | 120.0 | Sweep angle in degrees |
| `height` | 1.0 | Vertical bulge of the sweep |
| `yOffset` | 1.5 | Slash height above the victim's feet |
| `hitRadius` | 1.0 | Hit distance around each arc point |
| `points` | 24 | Arc resolution |
| `slashDuration` | 2 | Ticks each slash animates over |
| `affects` | `all` | Who the slashes hurt (victim included) |
| `startMessage` | `&d&lYou're in my way...` | Chat line at cast (`""` disables) |
| `startMessageRadius` | 40.0 | Players within this radius see the line |
| `startSound` | `minecraft:block.respawn_anchor.charge` | Charge-up sound (`""` disables) |
| `startSoundVolume` / `startSoundPitch` | 2.0 / 0.5 | |
| `slashSound` | `minecraft:entity.player.attack.sweep` | Per-slash sound (`""` disables) |
| `slashSoundVolume` / `slashSoundPitch` | 1.0 / 1.2 | |
| `arcParticles` | witch ×2, dragon_breath ×1, electric_spark ×1 per arc point | Particles traced along each arc — the fully purple slash look (same burst format as `slashParticles`); a single id via `arcParticle` also works |
| `slashParticles` | witch ×10, dragon_breath ×5, electric_spark ×2 | Bursts fired per slash: `[{ "particle", "amount", "hSpread", "vSpread", "speed" }]` |
| `particleMode` | `all` | `all` = every burst each slash, `cycle` = one per slash in rotation |
| `burstAt` | `self` | Where bursts appear: `self` (caster) or `target` |
| `teleportBehind` | true | Teleport behind the victim before the finisher |
| `behindDistance` | 2.0 | Blocks behind the victim's back |
| `teleportSound` | `minecraft:entity.enderman.teleport` | |
| `teleportSoundVolume` / `teleportSoundPitch` | 1.0 / 0.5 | |
| `teleportParticles` | dragon_breath ×50 | Same burst format |
| `finalMessage` | `&c&lBehind you.` | Line before the finisher hit (`""` disables) |
| `finalMessageRadius` | 20.0 | |
| `finalDelay` | 5 | Ticks between teleport and finisher hit |
| `finalDamage` | 25.0 | Finisher damage (0 disables) |
| `ignoreArmor` | true | How much of the finisher bypasses armor: `true`/`false`, or a number — `0.4` (fraction) and `40` (percent) both mean 40% pierces, the rest is reduced by armor normally |
| `slashIgnoreArmor` | false | Same format, applied to every slash hit |

```json
{ "type": "arc_slash_barrage", "trigger": "on_right_click", "cooldownTicks": 400,
  "params": { "finalDamage": 20, "startMessage": "&d&lYou're in my way..." } }
```

---

### `arc_slash_storm`

The barrage engine as an area ultimate: after the opening line + charge-up, each slash fires at
**every** matching entity — players and mobs alike (default `affects: all`) — within `radius` of
the caster — 15 slashes fanning from flat to near-vertical. No finisher (the teleport/finisher
params are ignored — there is no single victim). Takes every `arc_slash_barrage` param;
different defaults:

| Param | Default | Description |
|-------|---------|-------------|
| `radius` | 40.0 | Victim search radius around the caster, re-checked each slash |
| `affects` | `all` | Narrow to `players`, `hostiles` or `mobs` to filter victims |
| `rolls` | `[0, 18, -18, 36, -36, 54, -54, 72, -72, 90, -90, 108, -108, 126, -126]` | |
| `startDelay` | 20 | |
| `slashInterval` | 2 | |
| `startMessage` | `&d&lIm tired of you` | |
| `startSoundVolume` / `startSoundPitch` | 1.8 / 0.6 | |
| `slashParticles` | witch ×8, dragon_breath ×5, electric_spark ×3 | |
| `particleMode` | `cycle` | One burst per slash, rotating through the list |

With nobody in radius it still fires, slashing the air `frontDistance` blocks ahead of the
caster (and hitting anything that walks in mid-storm).

```json
{ "type": "arc_slash_storm", "trigger": "on_shift_right_click", "cooldownTicks": 2400,
  "params": { "radius": 20, "damage": 4 } }
```

---

### `teleport_behind`

The arc-slash barrage's finisher as a standalone move: blink to a safe spot behind your target's
back, with optional damage. Works from `on_attack` (the struck target) or `on_right_click` (the
entity under the crosshair within `targetRange`). No target = no-op (nothing to be "behind"), so
the cooldown isn't spent.

| Param | Default | Description |
|-------|---------|-------------|
| `behindDistance` | 2.0 | How far behind the target you land |
| `targetRange` | 16.0 | Crosshair pick distance (right-click use) |
| `damage` | 0.0 | Optional finisher hit (0 = teleport only) |
| `ignoreArmor` | 1.0 | For the finisher: true/false or a percent (armor pierce) |
| `particle` | `minecraft:portal` | Burst at the landing spot |
| `particleCount` | 40 | Particle amount |
| `sound` | `minecraft:entity.enderman.teleport` | On teleport |
| `message` | *(none)* | Optional action-bar line |

```json
{ "type": "teleport_behind", "trigger": "on_right_click", "cooldownTicks": 120,
  "params": { "damage": 8, "message": "&c&lBehind you." } }
```

---

### `beam`

Sustained energy beam — the Fiw Bosses beam attack in item form. A windup gathers spiraling
energy at your eyes, then a solid particle beam fires along your crosshair for `duration` ticks,
damaging everything caught in its cylinder every tick (vanilla hurt-invulnerability paces the
hits). By default the beam follows your aim so you can sweep it like a laser; it stops at the
first wall it hits.

| Param | Default | Description |
|-------|---------|-------------|
| `windup` | 20 | Charge-up ticks before the beam fires |
| `duration` | 50 | Ticks the beam stays on |
| `damage` | 3.0 | Damage per tick to anything inside the beam |
| `width` | 0.9 | Beam hitbox half-width in blocks |
| `range` | 35.0 | Max beam length |
| `affects` | `all` | Who the beam hurts |
| `ignoreArmor` | true | `true`/`false` or a percent (like the slash abilities) |
| `followLook` | true | Beam tracks your crosshair; `false` locks the direction when the windup ends |
| `throughBlocks` | false | `true` lets the beam pass through walls |
| `startMessage` | *(none)* | Optional chat line at cast; `startMessageRadius` 48 |
| `chargeSound` | `minecraft:entity.elder_guardian.curse` | + `chargeSoundVolume` 1.5 / `chargeSoundPitch` 1.0 |
| `fireSound` | `minecraft:entity.guardian.attack` | Fires when the windup ends; volume 1.8 / pitch 0.6 |
| `humSound` | `minecraft:entity.guardian.attack` | Loops every `humInterval` (8) ticks; volume 0.6 / pitch 1.3 |
| `endSound` | `minecraft:block.glass.break` | Shutdown; volume 1.0 / pitch 0.8 |
| `coreParticle` | `minecraft:end_rod` | Bright beam core; `coreDensity` 10 per block |
| `outerParticle` | `minecraft:electric_spark` | Soft glow layer; `outerDensity` 5 per block |
| `chargeParticle` | `minecraft:electric_spark` | Windup spiral; `chargeCoreParticle` `minecraft:end_rod` at the center |

```json
{ "type": "beam", "trigger": "on_right_click", "cooldownTicks": 300,
  "params": { "damage": 3, "duration": 50, "range": 30 } }
```

---

### `slash_wave`

Flying crescent slash — a ranged cut launched from the weapon. A curved blade of particles
(purple by default, matching the arc-slash family) travels along your crosshair, cutting every
entity it passes through once. Fire a fan of them with `waves` + `spreadAngle`; `roll` tilts
each crescent (0 flat, 90 vertical).

| Param | Default | Description |
|-------|---------|-------------|
| `damage` | 6.0 | Damage per entity hit (once per wave) |
| `speed` | 1.2 | Blocks per tick |
| `range` | 24.0 | Distance before the wave fizzles |
| `width` | 2.5 | Crescent size in blocks |
| `arc` | 150.0 | Crescent curvature in degrees |
| `roll` | 0.0 | Crescent tilt (0 flat, 90 vertical) |
| `waves` | 1 | Crescents per cast, fanned horizontally |
| `spreadAngle` | 15.0 | Degrees between fanned waves |
| `piercing` | true | `false` stops at the first entity hit |
| `throughBlocks` | false | `true` flies through walls |
| `knockback` | 0.4 | Push along the flight direction |
| `hitRadius` | 1.2 | Hit distance around each crescent point |
| `points` | 16 | Crescent resolution |
| `ignoreArmor` | false | `true`/`false` or a percent, like the slash abilities |
| `affects` | `all` | Who the wave hurts |
| `waveParticles` | witch ×2, dragon_breath ×1, electric_spark ×1 | Trail (same burst format as `slashParticles`) |
| `launchSound` | `minecraft:entity.player.attack.sweep` | Volume 1.0 / pitch 0.7 |
| `hitSound` | `minecraft:entity.player.attack.crit` | Volume 1.0 / pitch 0.9 |

```json
{ "type": "slash_wave", "trigger": "on_right_click", "cooldownTicks": 60,
  "params": { "damage": 7, "waves": 3, "spreadAngle": 20 } }
```

---

### `arrow_rain`

Mark a zone at your crosshair (up to `targetRange`, or the surface you're looking at), telegraph
it with a shrinking particle ring, then real arrows pour from the sky over it in waves. Arrows
carry your kill credit and can't be picked up (no free ammo); like any arrow, they hit whatever
is under them — friend or foe.

| Param | Default | Description |
|-------|---------|-------------|
| `targetRange` | 40.0 | How far the zone can be placed |
| `radius` | 4.0 | Zone radius |
| `delay` | 10 | Telegraph ticks before the first wave |
| `count` | 20 | Total arrows |
| `waves` | 4 | Groups the arrows arrive in |
| `waveInterval` | 3 | Ticks between waves |
| `height` | 12.0 | Spawn height above the zone |
| `arrowSpeed` | 1.5 | Downward launch speed |
| `damage` | 4.0 | Arrow base damage (scales with speed like vanilla) |
| `flaming` | false | Arrows set targets on fire |
| `critical` | false | Crit trail + bonus roll |
| `markerParticle` | `minecraft:cloud` | Telegraph ring |
| `castSound` | `minecraft:entity.arrow.shoot` | At cast; volume 1.0 / pitch 0.8 |
| `rainSound` | `minecraft:entity.arrow.shoot` | Per wave; volume 0.8 / pitch ~1.1 |

```json
{ "type": "arrow_rain", "trigger": "on_right_click", "cooldownTicks": 200,
  "params": { "count": 30, "radius": 5, "flaming": true } }
```

---

### `sonic_boom`

Warden-style concussive shot: a short sculk-crackle charge, then one instant shockwave ray
along your crosshair that travels **through walls** by default, hits everything in its path
with armor-ignoring damage and hurls victims away. The anti-turtle answer to shields and cover.

| Param | Default | Description |
|-------|---------|-------------|
| `windup` | 10 | Charge ticks before the shot |
| `damage` | 12.0 | Damage to everything on the ray |
| `range` | 24.0 | Ray length |
| `width` | 1.2 | Ray hitbox half-width |
| `knockback` | 1.5 | Launch strength along the ray |
| `throughBlocks` | true | `false` stops at the first wall |
| `ignoreArmor` | true | `true`/`false` or a percent |
| `affects` | `all` | Who it hurts |
| `boomParticle` | `minecraft:sonic_boom` | One per `particleSpacing` (1.0) blocks |
| `chargeParticle` | `minecraft:sculk_charge_pop` | Windup crackle |
| `chargeSound` | `minecraft:entity.warden.sonic_charge` | Volume 2.0 / pitch 1.0 |
| `fireSound` | `minecraft:entity.warden.sonic_boom` | Volume 2.0 / pitch 1.0 |

```json
{ "type": "sonic_boom", "trigger": "on_right_click", "cooldownTicks": 400,
  "params": { "damage": 10, "range": 20 } }
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

### `lightning_rain`

Mark a circle at your crosshair (up to `targetRange`, or the surface you're looking at), telegraph
it, then rain lightning bolts down inside it on a fully custom schedule. Each bolt is cosmetic by
default (no fire, no mob conversion) and damages everything within `aoeRadius` of where it lands.

| Param | Default | Description |
|-------|---------|-------------|
| `targetRange` | 40.0 | How far the circle can be placed |
| `radius` | 5.0 | Circle radius |
| `delay` | 10 | Telegraph ticks before the first bolt |
| `groups` | *(none)* | Array of `{ "count": N, "delayTicks": T }` — each group drops `count` bolts `delayTicks` after the previous group. Omit for the even-spread fallback below |
| `count` | 12 | (fallback) Total bolts when `groups` is absent |
| `waves` | `count` | (fallback) Number of groups to spread `count` over |
| `waveInterval` | 8 | (fallback) Ticks between waves |
| `damage` | 5.0 | Damage per bolt |
| `aoeRadius` | 2.5 | Splash radius around each strike (0 = purely cosmetic) |
| `cosmetic` | true | Suppress vanilla lightning fire / mob conversion |
| `affects` | `all` | Who the bolts damage |
| `markerParticle` | `minecraft:electric_spark` | Telegraph ring |
| `boltParticle` | `minecraft:electric_spark` | Burst at each strike |
| `castSound` / `strikeSound` | thunder / impact | On cast and per strike |

```json
{ "type": "lightning_rain", "trigger": "on_right_click", "cooldownTicks": 200,
  "params": { "radius": 6, "damage": 6, "aoeRadius": 3,
    "groups": [ { "count": 1, "delayTicks": 0 }, { "count": 1, "delayTicks": 10 }, { "count": 3, "delayTicks": 10 } ] } }
```

---

### `lightning_ray`

A straight line of bolts marching forward from your look direction, one every `boltInterval` ticks
up to `range`, each landing on the ground and damaging within `aoeRadius`. Passes over terrain and
always reaches full range.

| Param | Default | Description |
|-------|---------|-------------|
| `range` | 16.0 | Line length in blocks |
| `stepDistance` | 2.0 | Spacing between consecutive bolts |
| `boltInterval` | 4 | Ticks between each bolt |
| `damage` | 5.0 | Damage per bolt |
| `aoeRadius` | 2.0 | Splash radius around each strike |
| `cosmetic` | true | Suppress vanilla lightning side effects |
| `affects` | `all` | Who the bolts damage |
| `boltParticle` | `minecraft:electric_spark` | Burst at each strike |
| `strikeSound` | `minecraft:entity.lightning_bolt.impact` | Per bolt |

```json
{ "type": "lightning_ray", "trigger": "on_right_click", "cooldownTicks": 160,
  "params": { "range": 20, "boltInterval": 3, "damage": 6 } }
```

---

### `homing_bolt`

Fires a particle projectile that curves toward the nearest valid enemy as it flies — turning at
most `turnRate` degrees per tick and re-aiming within `trackRange`. Deals `damage` in a small
`aoeRadius` on contact or at max `range`.

| Param | Default | Description |
|-------|---------|-------------|
| `range` | 24.0 | Max travel |
| `speed` | 0.8 | Blocks per tick |
| `damage` | 6.0 | Hit damage |
| `aoeRadius` | 1.0 | Radius checked for hits |
| `trackRange` | 12.0 | How far it looks for a target to steer toward |
| `turnRate` | 12.0 | Max degrees it can turn per tick (0 = flies straight) |
| `affects` | `all` | Who it can home in on and hit |
| `particle` | `minecraft:soul_fire_flame` | Trail |

```json
{ "type": "homing_bolt", "trigger": "on_right_click", "cooldownTicks": 40,
  "params": { "damage": 7, "turnRate": 15 } }
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

## Active Debuffs

Single-target debuffs applied by attacking. These complement the PvP and PvE categories — they apply on-hit effects rather than dealing immediate damage.

### `ignite`

Best trigger: `on_attack`. Sets the target on fire for a configurable number of seconds.

| Param | Default | Description |
|-------|---------|-------------|
| `seconds` | 5 | Fire duration in seconds |

```json
{ "type": "ignite", "trigger": "on_attack", "chance": 0.4, "cooldownTicks": 30, "params": { "seconds": 6 } }
```

---

### `wither_touch`

Best trigger: `on_attack`. Applies Wither — a magic DoT that bypasses armor and can kill.

| Param | Default | Description |
|-------|---------|-------------|
| `duration` | 60 | Effect length in ticks |
| `amplifier` | 0 | Wither level (0 = I) |

```json
{ "type": "wither_touch", "trigger": "on_attack", "chance": 0.3, "cooldownTicks": 40, "params": { "duration": 80, "amplifier": 1 } }
```

---

### `bleed`

Best trigger: `on_attack`. Schedules a real server-side DoT — ticking magic damage that **can kill** and bypasses armor. Unlike Wither, the cadence and total pulses are configurable.

| Param | Default | Description |
|-------|---------|-------------|
| `dps` | 1.0 | Damage per pulse (magic — bypasses armor) |
| `pulses` | 5 | Total number of pulses |
| `intervalTicks` | 20 | Ticks between pulses (20 = 1 s) |

Defaults deal 1 damage every second for 5 seconds = 5 total magic damage. Bleed on the same target refreshes rather than stacks — the harder bleed wins.

```json
{ "type": "bleed", "trigger": "on_attack", "cooldownTicks": 60, "params": { "dps": 1.5, "pulses": 6, "intervalTicks": 15 } }
```

---

### `stun_strike`

Best trigger: `on_attack`. A true hard crowd-control: the target is locked for `duration` ticks. A
per-tick sweep zeroes its movement (it can't walk, flee, or be knocked along) while stacked
Slowness / Weakness / Mining Fatigue stop it attacking or mining. A full freeze on mobs; a hard
root and near-total disable on players (they can still turn the camera — a server-side limit). Pair
with a low `chance` and a real cooldown.

| Param | Default | Description |
|-------|---------|-------------|
| `duration` | 60 | Lock length in ticks |
| `slownessAmplifier` | 250 | Slowness level applied — lower values give players a soft slow instead of a full root (mobs are held in place by the velocity lock either way) |
| `particle` | `minecraft:crit` | Burst on application |
| `particles` | true | Show the Slowness swirl on the target |

```json
{ "type": "stun_strike", "trigger": "on_attack", "chance": 0.2, "cooldownTicks": 120, "params": { "duration": 40 } }
```

Entities immune to the stun's effects (the Ender Dragon, and anything that can't be affected by
Slowness/Weakness/Mining Fatigue) are skipped entirely — the velocity lock won't freeze a boss that
otherwise ignores crowd control.

---

### `apply_effect`

The general-purpose potion-effect tool — name any vanilla effect and drop it on the caster or the
target, for any trigger. Replaces reaching for a bespoke buff/debuff ability. Effect names are the
same friendly set as `passive_buff` (`speed`, `strength`, `poison`, `wither`, `levitation`,
`regeneration`, `resistance`, `glowing`, …).

| Param | Default | Description |
|-------|---------|-------------|
| `effect` | **required** | Effect name (unknown = no-op) |
| `target` | `self` | `self` = the holder, `target` = the attacked / looked-at entity |
| `duration` | 100 | Ticks |
| `amplifier` | 0 | 0 = level I |
| `ambient` | false | Fainter "beacon" particles |
| `particles` | true | Show the swirl particles |

```json
{ "type": "apply_effect", "trigger": "on_attack", "chance": 0.5, "params": { "effect": "poison", "duration": 100, "amplifier": 1 } }
```

```json
{ "type": "apply_effect", "trigger": "on_right_click", "cooldownTicks": 400,
  "params": { "effect": "strength", "target": "self", "duration": 200, "amplifier": 1 } }
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

### `grapple`

Best trigger: `on_right_click`. Grappling hook: latch onto the **block** under your crosshair and
get reeled toward it, rope drawn in particles. Sneak mid-flight to let go; no fall damage while
reeling. (For pulling *entities* to you, see `grappling_pull`.)

| Param | Default | Description |
|-------|---------|-------------|
| `range` | 24.0 | Max block distance |
| `pullSpeed` | 0.9 | Reel speed in blocks per tick |
| `launchBoost` | 0.35 | Initial upward hop |
| `releaseDistance` | 1.5 | Arrival distance that ends the pull |
| `maxTicks` | 60 | Reel timeout |
| `ropeParticle` | `minecraft:crit` | Rope trail |
| `fireSound` | `minecraft:entity.fishing_bobber.throw` | + volume 1.0 / pitch 0.8 |
| `latchSound` | `minecraft:block.chain.place` | + volume 1.0 / pitch 0.8 |

```json
{ "type": "grapple", "trigger": "on_right_click", "cooldownTicks": 60, "params": { "range": 30 } }
```

---

### `summon`

Best trigger: `on_right_click`. Summons temporary allies around you: tamables come out tamed to
you, and every summon is aimed at whoever last hurt you, then re-aimed at nearby hostiles while
it lives. Summons vanish in a poof when their time runs out (strays from a crash are culled on
world load). Works best with tamable or neutral mobs — a summoned zombie is still a zombie.

| Param | Default | Description |
|-------|---------|-------------|
| `entity` | `minecraft:wolf` | Entity type to summon |
| `count` | 2 | How many |
| `duration` | 600 | Lifetime in ticks |
| `spawnRadius` | 1.8 | Ring around the caster they appear in |
| `particle` | `minecraft:cloud` | Spawn/poof burst |
| `sound` | `minecraft:entity.evoker.cast_spell` | + `soundVolume` 1.0 / `soundPitch` 1.0 |

```json
{ "type": "summon", "trigger": "on_right_click", "cooldownTicks": 600,
  "params": { "entity": "minecraft:wolf", "count": 3, "duration": 900 } }
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

### `aegis_shield`

Best trigger: `on_right_click`. Raise an on-demand barrier that swallows incoming hits for
`duration` ticks. A `pool` of damage is absorbed; each hit is eaten whole while the pool lasts, and
the hit that drains it shatters the barrier. Unlike Absorption this is a real damage gate — a chip
hit and a huge hit both cost one "hold" of the shield. Damage that bypasses invulnerability
(`/kill`, the void) and the [Curse System](#curse-system)'s drain are never absorbed — the barrier
is not an escape from either.

| Param | Default | Description |
|-------|---------|-------------|
| `pool` | 20.0 | Total damage the barrier absorbs before it breaks |
| `duration` | 100 | Ticks the barrier lasts if unbroken |
| `particle` | `minecraft:end_rod` | Dome burst on cast |
| `sound` | `minecraft:block.beacon.activate` | Played on cast |

```json
{ "type": "aegis_shield", "trigger": "on_right_click", "cooldownTicks": 400, "params": { "pool": 30, "duration": 120 } }
```

---

### `blood_debt`

A two-part risk mechanic set by a `mode` param — reward for aggression, punishment for greed.
Put one entry in `bank` mode on right-click and one in `spend` mode on `on_attack`.

| Param | Default | Description |
|-------|---------|-------------|
| `mode` | `bank` | `bank` = pay HP to store charges; `spend` = consume a charge on hit |
| `healthCost` | 6.0 | *(bank)* current HP paid to open a debt (refused if it would drop you too low) |
| `charges` | 3 | *(bank)* charges banked; a new debt stacks onto any unspent one |
| `windowTicks` | 120 | *(bank)* ticks before the debt collects |
| `backlashPerCharge` | 4.0 | *(bank)* magic damage per **unspent** charge when the window closes |
| `bonusDamage` | 4.0 | *(spend)* extra damage each consumed charge adds to the hit |
| `lifesteal` | 2.0 | *(spend)* HP healed per consumed charge |

```json
{ "type": "blood_debt", "trigger": "on_shift_right_click", "cooldownTicks": 40, "params": { "mode": "bank", "healthCost": 6, "charges": 3, "windowTicks": 120, "backlashPerCharge": 4 } },
{ "type": "blood_debt", "trigger": "on_attack", "params": { "mode": "spend", "bonusDamage": 4, "lifesteal": 2 } }
```

### `void_rift`

Plant a telegraphed rift at the block you're looking at; for `delay` ticks it drags nearby entities
inward, then detonates. You cast it *before* the enemy arrives — the skill is prediction and herding.

| Param | Default | Description |
|-------|---------|-------------|
| `range` | 20.0 | How far ahead the rift can be placed (raycast to the surface you look at) |
| `delay` | 40 | Telegraph ticks before it collapses |
| `radius` | 4.0 | Detonation radius (the inward tug reaches a bit further) |
| `damage` | 10.0 | Damage dealt in the radius on collapse |
| `pull` | 0.18 | Inward tug strength during the telegraph (0 = no pull) |
| `affects` | `all` | Who it pulls and damages |

```json
{ "type": "void_rift", "trigger": "on_right_click", "cooldownTicks": 120, "params": { "delay": 40, "radius": 4, "damage": 10, "pull": 0.2 } }
```

### `phase_shade`

Right-click blinks you forward and leaves a shade (a lingering wisp) where you were; right-click again
while it lasts to snap back. Commit to the dive, then bail if you time it — or lose the shade.

| Param | Default | Description |
|-------|---------|-------------|
| `distance` | 6.0 | Blink distance forward |
| `windowTicks` | 100 | How long the shade persists / the recall window |
| `recallResistance` | 30 | Ticks of Resistance V granted on recall (0 = none) |

```json
{ "type": "phase_shade", "trigger": "on_right_click", "cooldownTicks": 30, "params": { "distance": 7, "windowTicks": 100 } }
```

### `gravity_singularity`

A hold-to-charge pull — best on a `chargeable` item via `on_charge_release`. Longer charge = wider,
stronger well and a bigger implosion, but the charge commits you and `selfDamage` scales with it too.

| Param | Default | Description |
|-------|---------|-------------|
| `maxChargeTicks` | 40 | Charge ticks that count as "fully charged" (100%) |
| `minRadius` / `maxRadius` | 3.0 / 8.0 | Well radius interpolated by charge fraction |
| `pullStrength` | 0.55 | Base inward pull (scaled 40–100% by charge) |
| `duration` / `period` | 40 / 4 | How long the well lasts and how often it pulses |
| `damage` | 8.0 | Implosion damage at the end (scaled by charge) |
| `selfDamage` | 0.0 | HP the caster pays (scaled by charge) |
| `range` | 4.0 | How far ahead the well forms |
| `affects` | `mobs` | Who it pulls and damages |

```json
{ "type": "gravity_singularity", "trigger": "on_charge_release", "cooldownTicks": 80, "params": { "maxChargeTicks": 40, "maxRadius": 9, "damage": 8, "selfDamage": 2 } }
```

Pair with a `chargeable` block so the item can be held to charge:

```json
"chargeable": { "animation": "bow", "maxSeconds": 3600 }
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

### `repair`

Best trigger: `on_right_click`. A repair kit — hold it and right-click while a **damaged** item
sits in your other hand (or armor). It restores that item's durability, then is spent. Right-clicks
that find nothing to repair cost no cooldown and consume nothing.

| Param | Default | Description |
|-------|---------|-------------|
| `repairAmount` | 0 | Flat durability restored |
| `repairPercent` | 0.0 | Additional durability restored as a fraction of the item's max |
| `fullRepair` | false | Fully repair the item |
| `consumeSelf` | true | Spend the kit on success |
| `useKitDurability` | false | Instead of shrinking, burn the kit's own durability so one kit lasts several repairs (needs a `maxDamage` on the kit) |
| `kitDurabilityPerUse` | 1 | Durability the kit loses per use when `useKitDurability` is on |
| `targets` | *(any)* | Optional filter — `items` (Fiw ids) / `bases` (vanilla ids) / `tags` — limiting what it mends |
| `messages` | *(defaults)* | Override `noTarget` / `alreadyFull` (empty string silences) |

```json
{ "type": "repair", "trigger": "on_right_click", "params": { "repairAmount": 200, "targets": { "tags": ["#minecraft:swords"] } } }
```

```json
{ "type": "repair", "trigger": "on_right_click",
  "params": { "fullRepair": true, "consumeSelf": true } }
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

**`while_sneaking`** is a sub-variant: same sweep, same passive machinery, but only runs while the player is crouching (shift held). Use it for toggle-style effects or abilities that cost mobility to activate. All passives and conditions work unchanged with this trigger.

```json
{ "type": "passive_buff", "trigger": "while_sneaking", "params": { "buffs": ["speed", "jump_boost"], "amplifier": 1 } }
```

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

### `holder_debuff`

The balance tool. Applies negative status effects to whoever holds or wears the item — use it to give powerful items meaningful drawbacks. Works on `while_held`, `while_worn`, and `while_sneaking`.

The `debuffs` list accepts the same effect names as `passive_buff` — any vanilla effect name works. With no `cooldownTicks` the debuff is always-on (refreshed every passive sweep like a steady buff).

| Param | Default | Description |
|-------|---------|-------------|
| `debuffs` | `["slowness"]` | Negative effects applied to the holder |
| `amplifier` | 0 | Effect level |
| `buffDuration` | 40 | Refresh duration in ticks |

```json
{ "type": "holder_debuff", "trigger": "while_held", "params": { "debuffs": ["slowness", "mining_fatigue"], "amplifier": 1 } }
```

A cursed god-sword that makes you slow and clumsy while holding it:
```json
{
  "abilities": [
    { "type": "holder_debuff", "trigger": "while_held", "params": { "debuffs": ["slowness"], "amplifier": 0 } },
    { "type": "holder_debuff", "trigger": "while_held", "params": { "debuffs": ["weakness"], "amplifier": 0 } }
  ]
}
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

### `decay_aura`

Pulses Wither onto nearby enemies — magic DoT that can kill. Stronger than `chill_aura` or `curse_pulse` for PvE since Wither ignores armor; use `cooldownTicks` to avoid being too oppressive. Default `affects: hostiles`.

| Param | Default | Description |
|-------|---------|-------------|
| `radius` | 4.0 | Aura radius |
| `duration` | 60 | Wither duration in ticks |
| `amplifier` | 0 | Wither level (0 = I) |
| `affects` | `hostiles` | Scope |

```json
{ "type": "decay_aura", "trigger": "while_held", "cooldownTicks": 80, "params": { "radius": 5, "amplifier": 1 } }
```

---

### `ember_aura`

Sets nearby enemies on fire while held. Fire damage is reduced by Fire Resistance and armor. Default `affects: hostiles`.

| Param | Default | Description |
|-------|---------|-------------|
| `radius` | 4.0 | Aura radius |
| `seconds` | 3 | Fire duration applied per sweep |
| `affects` | `hostiles` | Scope |

```json
{ "type": "ember_aura", "trigger": "while_held", "cooldownTicks": 60, "params": { "radius": 5, "seconds": 5 } }
```

---

### `ice_lance`

On hit: bonus magic damage + heavy Slowness IV to the target. Pair with a fast weapon for consistent slow uptime.

| Param | Default | Description |
|-------|---------|-------------|
| `damage` | 4.0 | Bonus magic damage |
| `slowDuration` | 80 | Slowness ticks |
| `slowAmplifier` | 3 | Slowness level (0 = I) |

```json
{ "type": "ice_lance", "trigger": "on_attack", "cooldownTicks": 20, "params": { "damage": 5, "slowDuration": 60 } }
```

---

### `blizzard`

AoE frost burst around the caster: Slowness + Blindness + light magic damage to all targets in radius. Great on right-click staves.

| Param | Default | Description |
|-------|---------|-------------|
| `radius` | 6.0 | Blast radius |
| `damage` | 2.0 | Magic damage per target |
| `slowDuration` | 60 | Slowness ticks |
| `slowAmplifier` | 1 | Slowness level (0 = I) |
| `affects` | `all` | Scope |

```json
{ "type": "blizzard", "trigger": "on_right_click", "cooldownTicks": 200, "params": { "radius": 7, "damage": 3 } }
```

---

### `glacial_shell`

Passive survival: while the holder is below a health threshold, gain Resistance I + Slowness II. The penalty (Slowness) discourages tanking with it permanently.

| Param | Default | Description |
|-------|---------|-------------|
| `threshold` | 0.4 | HP fraction (0.4 = below 40%) |
| `buffDuration` | 40 | Effect duration per sweep tick |
| `resistAmplifier` | 0 | Resistance level (0 = I) |

```json
{ "type": "glacial_shell", "trigger": "while_held", "cooldownTicks": 0, "params": { "threshold": 0.35 } }
```

---

### `tidal_surge`

Right-click to knock back all entities in a forward cone. Applies Slowness after the push. Unlike `whirlwind`, this is directional — aim matters.

| Param | Default | Description |
|-------|---------|-------------|
| `radius` | 7.0 | Cone range |
| `angle` | 80.0 | Total cone width in degrees |
| `knockback` | 1.8 | Push force multiplier |
| `slowDuration` | 60 | Slowness ticks |
| `slowAmplifier` | 1 | Slowness level (0 = I) |
| `affects` | `all` | Scope |

```json
{ "type": "tidal_surge", "trigger": "on_right_click", "cooldownTicks": 160, "params": { "radius": 8, "knockback": 2.5, "angle": 60 } }
```

---

### `flame_dash`

Dash forward in the look direction. Every entity along the path is ignited. Uses the same safe-block pathing as `blink` — stops at walls.

| Param | Default | Description |
|-------|---------|-------------|
| `distance` | 7.0 | Max dash distance in blocks |
| `igniteSeconds` | 4 | Fire duration on hit entities |

```json
{ "type": "flame_dash", "trigger": "on_right_click", "cooldownTicks": 120, "params": { "distance": 8, "igniteSeconds": 5 } }
```

---

### `meteor_strike`

Raycast to a target position and call down a fire strike. AoE magic damage + ignite in radius at impact. Uses line-of-sight raycast; max 24-block range.

| Param | Default | Description |
|-------|---------|-------------|
| `range` | 24.0 | Max cast range |
| `radius` | 4.0 | Blast radius at impact |
| `damage` | 8.0 | Magic damage per target |
| `igniteSeconds` | 5 | Fire duration applied |
| `affects` | `all` | Scope |

```json
{ "type": "meteor_strike", "trigger": "on_right_click", "cooldownTicks": 300, "params": { "damage": 10, "radius": 5 } }
```

---

### `blood_pact`

On attack: sacrifice a fraction of your current HP to deal amplified damage. Player can never be killed by the self-damage (floor 1 HP). Less effective the more wounded you are.

| Param | Default | Description |
|-------|---------|-------------|
| `hpCost` | 0.25 | Fraction of current HP sacrificed |
| `multiplier` | 3.0 | Damage = sacrificed HP × multiplier |

```json
{ "type": "blood_pact", "trigger": "on_attack", "cooldownTicks": 20, "params": { "hpCost": 0.3, "multiplier": 4 } }
```

---

### `hemorrhage`

Reactive passive: when the holder takes damage, the attacker is immediately inflicted with a bleed DoT. Set trigger to `on_hurt`.

| Param | Default | Description |
|-------|---------|-------------|
| `dps` | 1.0 | Bleed damage per pulse |
| `pulses` | 4 | Number of bleed pulses |
| `intervalTicks` | 20 | Ticks between pulses |

```json
{ "type": "hemorrhage", "trigger": "on_hurt", "cooldownTicks": 0, "params": { "dps": 1.5, "pulses": 5 } }
```

---

### `sanguine_strike`

Lifesteal on attack that scales with missing HP — heal more when you're desperate. Does nothing when at full HP (baseHeal is the flat base).

| Param | Default | Description |
|-------|---------|-------------|
| `baseHeal` | 0.5 | Base heal per hit |
| `bonusHeal` | 3.0 | Extra heal multiplied by HP missing fraction |

```json
{ "type": "sanguine_strike", "trigger": "on_attack", "cooldownTicks": 0, "params": { "baseHeal": 1, "bonusHeal": 4 } }
```

---

## Resonance / Set Bonus System

When `resonanceRequires` or more equipped items share the same `resonanceId`, abilities with `trigger: "resonance"` on those items fire every passive sweep. This enables set bonuses — equip the right combination of items to unlock extra passives.

**Item JSON fields:**

| Field | Default | Description |
|-------|---------|-------------|
| `resonanceId` | `null` | Set name. Any string, case-sensitive. `null` = not in a set. |
| `resonanceRequires` | `2` | How many pieces must be equipped to activate. |

**Usage:** Put resonance abilities (with `"trigger": "resonance"`) on one item in the set (the "set holder"). Other items just need the same `resonanceId`. The ability fires from the first matching piece found.

```json
// storm_helmet.json
{
  "base": "minecraft:iron_helmet",
  "resonanceId": "storm",
  "resonanceRequires": 2,
  "abilities": [
    { "type": "resonance", "trigger": "resonance", "cooldownTicks": 0 }
  ]
}

// storm_chestplate.json — just needs the resonanceId, no abilities required
{
  "base": "minecraft:iron_chestplate",
  "resonanceId": "storm"
}
```

Any ability type works with `trigger: "resonance"` — passive_buff, auras, custom passives. The resonance sweep runs on the same 10-tick cycle as the normal passive sweep.

**Tiered set bonuses (`minPieces`):** each resonance ability may declare a `minPieces` param — it
only fires once at least that many pieces of the set are equipped. It defaults to the set's
`resonanceRequires`, so untiered sets behave exactly as before. Put several resonance abilities on
the set holder with different thresholds to build a 2-piece bonus *and* a stronger 4-piece bonus:

```json
"abilities": [
  { "type": "passive_buff", "trigger": "resonance", "params": { "minPieces": 2, "buffs": ["speed"] } },
  { "type": "passive_buff", "trigger": "resonance", "params": { "minPieces": 4, "buffs": ["strength"], "amplifier": 1 } }
]
```

---

## Elemental Status System

A server-side status tracker with three states: **FROZEN**, **SOAKED**, **SHOCKED**. Statuses are applied by dedicated abilities and consumed by interaction abilities for a setup+payoff loop. Burning is handled by vanilla fire (not tracked separately).

| Status | Tick effect | Consumed by |
|--------|-------------|-------------|
| FROZEN | Slowness VI + snowflake particles every 10t | `thaw_burst` |
| SOAKED | Drip particles every 20t (marker only) | `storm_chain` |
| SHOCKED | 1.0 magic dmg + spark particles every 20t | — (expires naturally) |

**Built-in applications:** `ice_lance` and `blizzard` apply FROZEN; `tidal_surge` applies SOAKED.

---

### `freeze`

Apply FROZEN to the attack target. Pairs with `thaw_burst` for the ice→fire interaction.

| Param | Default | Description |
|-------|---------|-------------|
| `duration` | 120 | Status duration in ticks |

```json
{ "type": "freeze", "trigger": "on_attack", "cooldownTicks": 20, "params": { "duration": 100 } }
```

---

### `soak`

Drench the target (SOAKED). Also extinguishes vanilla fire. Pairs with `storm_chain`.

| Param | Default | Description |
|-------|---------|-------------|
| `duration` | 100 | Status duration in ticks |

```json
{ "type": "soak", "trigger": "on_attack", "cooldownTicks": 20, "params": { "duration": 120 } }
```

---

### `shock`

Electrify target (SHOCKED): instant damage on application + periodic damage until expired.

| Param | Default | Description |
|-------|---------|-------------|
| `duration` | 100 | Status duration in ticks |
| `damage` | 2.0 | Instant damage on application |

```json
{ "type": "shock", "trigger": "on_attack", "cooldownTicks": 30, "params": { "damage": 3, "duration": 80 } }
```

---

### `thaw_burst`

**Interaction (Ice → Fire):** Consumes FROZEN on the target for an AoE fire damage burst around it. Returns false (no cooldown) if target isn't frozen.

| Param | Default | Description |
|-------|---------|-------------|
| `radius` | 4.0 | AoE radius |
| `damage` | 6.0 | Magic damage per target |
| `igniteSeconds` | 3 | Fire duration after burst |
| `affects` | `all` | Scope |

```json
{ "type": "thaw_burst", "trigger": "on_attack", "cooldownTicks": 60, "params": { "damage": 8, "radius": 5 } }
```

---

### `storm_chain`

**Interaction (Water → Lightning):** Consumes SOAKED on the target, deals bonus damage to it, then chains reduced damage to nearby targets. Returns false if target isn't soaked.

| Param | Default | Description |
|-------|---------|-------------|
| `damage` | 5.0 | Damage to primary (soaked) target |
| `chainDamage` | 3.0 | Damage to chained nearby targets |
| `chainRadius` | 5.0 | Chain reach |
| `affects` | `all` | Scope |

```json
{ "type": "storm_chain", "trigger": "on_attack", "cooldownTicks": 60, "params": { "damage": 6, "chainDamage": 4, "chainRadius": 6 } }
```

---

## Soul System

Items with `"soulCapacity": N` in their definition can store souls. Souls are collected via `soul_collector` (on kill) and spent via `soul_surge` (on right-click). The soul count is stored in item custom_data (`fiw_souls`) — persists across disconnects, resets on `/fiwtools reload`.

### `soul_collector`

Passive: collect one soul per kill, stored on the item stack. Shows a chat counter `(N/M)`. Does nothing if `soulCapacity` is not set on the item definition.

No configurable params — soul capacity comes from the item definition's `soulCapacity` field.

```json
{
  "soulCapacity": 10,
  "abilities": [
    { "type": "soul_collector", "trigger": "on_kill", "cooldownTicks": 0 },
    { "type": "soul_surge",     "trigger": "on_right_click", "cooldownTicks": 400 }
  ]
}
```

---

### `soul_surge`

Drain all stored souls and unleash an AoE magic damage burst. Damage = souls × `damagePerSoul`. Does nothing (and costs no cooldown) if the item holds zero souls.

| Param | Default | Description |
|-------|---------|-------------|
| `damagePerSoul` | 2.0 | Magic damage per soul spent |
| `radius` | 6.0 | Blast radius |
| `affects` | `all` | Scope |

```json
{ "type": "soul_surge", "trigger": "on_right_click", "cooldownTicks": 400, "params": { "damagePerSoul": 3, "radius": 7 } }
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
- Cursed items can't be put through anvils (rename, repair, book-enchant), enchanting tables (the `enchantable` component is stripped), or grindstones. The grindstone block is a feature of every Fiw item; the anvil block is curse-specific. All of these are just the *defaults* of the [`enchanting`](#enchanting-control) block — an explicit `"anvil": true` etc. overrides the curse lock.
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

An admin-defined upgrade system. A **catalyst** is a normal Fiw item carrying the `imbue` ability; when right-clicked it scans the player's off hand → **main hand** → armor for a valid **target**, rolls one **outcome** from its outcome list, and applies the outcome's `mods` block (enchantments, attributes, abilities, lore) to the target. Outcomes can be deterministic or weighted-random, the admin's choice. Each target tracks how many times it's been imbued; cap reached = the catalyst refuses with an admin-set message.

> **Imbuing the item you're holding:** because the scan reaches the main hand (and never the
> catalyst itself), you can upgrade the item in your **main hand** by holding the catalyst in your
> **off hand** and right-clicking. The main-hand target must not have its own right-click ability
> (that would fire instead of the catalyst). Off hand is still checked first, so the classic
> "catalyst in main hand, target in off hand" arrangement is unchanged.

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
| `messages` | — | Optional admin-set refusal messages keyed by `noTarget`, `wrongType`, `capReached`, `cursed`, `stacked`, `outOfCharges`, `noOutcomes`. An empty string silences the refusal. `stacked` fires when the target is a stack of more than one — the target must be a single item to imbue. |

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

## Custom Crafting Recipes

Drop JSON files into **`config/fiw_tools/recipes/`** and run `/fiwtools reload`. Fully server-side: vanilla clients place ingredients in any crafting grid (2x2 inventory or 3x3 table) and the result appears — no client install, no datapacks.

On 1.21.2+ versions the recipes are also published into the **vanilla recipe manager**, so they
show up in the vanilla recipe book *and* in any recipe viewer that reads vanilla recipes —
JEI, REI, EMI, client- or server-side — with the real custom items (names, lore, stats) in both
ingredients and results. No viewer integration or dependency involved; it's plain vanilla
display sync, so it can't break anything when no viewer is installed. (1.21.1 predates the
recipe-display rework, so there the crafts stay grid-only.)

One file can hold **as many recipes as you like**: a single recipe object, a bare array, or a `{ "recipes": [ ... ] }` wrapper.

```json
{
  "recipes": [
    {
      "id": "craft_soul_blade",
      "type": "shaped",
      "pattern": [
        " N ",
        "NDN",
        " S "
      ],
      "key": {
        "N": "minecraft:netherite_ingot",
        "D": "minecraft:diamond_sword",
        "S": "minecraft:stick"
      },
      "result": { "item": "fiw:soul_blade", "count": 1 }
    },
    {
      "id": "blade_to_star",
      "type": "shapeless",
      "ingredients": ["fiw:soul_blade", "minecraft:diamond_block"],
      "result": { "item": "minecraft:nether_star" }
    }
  ]
}
```

| Field | Description |
|-------|-------------|
| `id` | Optional unique name (defaults to `<filename>#<index>`). Shown by `/fiwtools recipes` |
| `type` | `shaped` or `shapeless`. Optional — presence of `pattern` implies shaped |
| `pattern` | Shaped: 1–3 rows of 1–3 symbols. Space = slot must be empty. Mirrored placement matches too |
| `key` | Shaped: symbol → ingredient spec |
| `ingredients` | Shapeless: 1–9 ingredient specs, each consumes one item |
| `result.item` | What you get — any ingredient spec form below |
| `result.count` | Stack size of the result (default 1) |
| `enchantments` | Optional [enchantment transfer](#keeping-enchantments-when-crafting) block — carry the ingredients' enchantments onto the result |

**Ingredient / result specs:**

| Spec | Matches |
|------|---------|
| `fiw:<id>` | A Fiw Custom Items entry by its custom id |
| `minecraft:stick` (any registry id) | That vanilla/modded item — **never** a Fiw item built on it |
| `#minecraft:planks` | Any item in that item tag (ingredients only) |

So you can craft **custom from vanilla**, **vanilla from custom**, and **custom from custom** — including crafting [awakened](#artifact-awakening) versions of artifacts.

**Protection rule:** when Fiw items sit in the grid and no custom recipe matches, any vanilla-computed result is suppressed — two Fiw swords can never be melted into a plain item by the vanilla repair recipe.

Commands: `/fiwtools recipes` lists everything loaded; `/fiwtools reload` reloads items **and** recipes.

### Keeping Enchantments When Crafting

By default a craft ignores whatever enchantments sit on the ingredients — the result is exactly
the configured item. Add an `enchantments` block to the recipe and the enchantments on the
**consumed** ingredients carry over to the result instead. Typical use: an upgrade recipe where a
player feeds in their Protection IV chestplate and gets the custom chestplate back *with* their
Protection IV.

```json
{
  "id": "upgrade_chestplate",
  "type": "shapeless",
  "ingredients": ["minecraft:netherite_chestplate", "fiw:dragon_core"],
  "result": { "item": "fiw:dragonscale_chestplate" },
  "enchantments": {
    "mode": "merge",
    "conflict": "highest",
    "from": ["minecraft:netherite_chestplate"],
    "exclude": ["binding_curse"],
    "respectMaxLevel": false,
    "onlyCompatible": false,
    "checkConflicts": false
  }
}
```

`"enchantments": true` is shorthand for the defaults (merge everything, highest level wins).
Omit the block (or use `false`) for the old behavior.

Everywhere below, **old** means enchantments already on the grid items; **new** means the
enchantments the freshly built result has on its own (a `fiw:` item's configured `enchantments`,
or none for a plain item result).

| Field | Default | Description |
|-------|---------|-------------|
| `mode` | `merge` | `merge` — old and new combined. `keep_old` (alias `old`) — the ingredients' enchantments replace the result's own entirely. `keep_new` (alias `new`) — no transfer at all |
| `conflict` | `highest` | When the same enchantment exists on both sides (or on two ingredients): `highest`, `lowest`, `old` (ingredient wins), `new` (result wins), `add` (levels are summed), `combine` (anvil rule: equal levels → level + 1, otherwise higher wins). Between two ingredients, `old`/`new` keep the earlier grid slot's level |
| `from` | all | List of ingredient specs (same syntax as `ingredients`) allowed to donate. Anything not listed is consumed silently, enchantments and all |
| `only` | all | Whitelist of enchantment ids that may transfer (`sharpness` or `minecraft:sharpness`) |
| `exclude` | none | Blacklist of enchantment ids that never transfer |
| `transferCurses` | `true` | Set `false` to leave Curse of Binding / Vanishing (and other `#minecraft:curse` enchantments) behind |
| `respectMaxLevel` | `false` | Clamp each transferred level to the enchantment's vanilla maximum (a Sharpness 10 donor becomes Sharpness 5) |
| `maxLevel` | `255` | Hard cap on every final level, 1–255. 255 is the storage format's limit, so nothing above it can ever be written |
| `onlyCompatible` | `false` | Drop enchantments the result item can't normally hold (no Lure on a chestplate). Ignored when the result is an enchanted book |
| `checkConflicts` | `false` | Drop an incoming enchantment that vanilla marks mutually exclusive with one already kept (Sharpness vs Smite). The result's own enchantments and earlier grid slots win |
| `allowMultiplied` | `false` | Required opt-in before a `count > 1` result may transfer — otherwise every crafted copy would carry the donors' enchantments, which is enchantment duplication. The recipe refuses to load without it |

Safety notes, all automatic:

- Enchantments **move**, they never multiply: every grid item is consumed by the craft, so the
  donor piece is gone the moment the enchanted result exists.
- Final levels are clamped to 1–255; level-0 or negative entries are dropped, and the same
  enchantment can never appear twice on the result.
- Enchanted **book** ingredients donate their stored enchantments too; an enchanted book *result*
  receives transfers as stored enchantments, like vanilla.
- The recipe book / JEI preview shows the base result — the actual crafted item picks up the
  enchantments from whatever the player really placed in the grid.

Custom items themselves stay enchantable the normal way: players can use the enchanting table and
anvil on them like any vanilla item — and the [`enchanting`](#enchanting-control) block controls
exactly which stations each item accepts.

---

## Infinite Use Items

The `infinite` root field makes consumable items refuse to run out — food, potions, snowballs, ender pearls, arrows, anything the game consumes on use.

```json
"infinite": "keep"
```

or the full form:

```json
"infinite": { "mode": "damage", "damagePerUse": 2 }
```

| Mode | Behaviour |
|------|-----------|
| `keep` (alias `normal`) | The item is never consumed. Eat the golden apple forever; the arrow returns to your quiver the instant it's fired |
| `damage` | Each use costs `damagePerUse` durability instead of the item (give the item `maxDamage`). When the next use would exceed max durability, the item is consumed for real |
| `replace` | Each use turns the item into `replaceWith` (`fiw:<id>` or vanilla id) × `replaceCount` |

```json
"infinite": { "mode": "replace", "replaceWith": "minecraft:bowl", "replaceCount": 1 }
```

**Notes:**
- Built on vanilla's `use_remainder` component plus a re-stamp sweep — survives reloads, restarts, and stacking.
- On Minecraft 1.21.1, vanilla has no generic `use_remainder` component yet. That port supports infinite food through the older food conversion field and infinite arrows through the projectile hook; generic non-food consumed items require 1.21.11 or 26.1.2.
- Fired arrows are restored instantly and the flying arrow can't be picked up (no duping). Multishot crossbows and the Infinity enchant are handled.
- `damage` mode needs a `maxDamage` value to count against; without one the item never breaks.

---

## Multi-block Mining

The `mining` root field turns a tool into a vein-miner, hammer, or tunnel-borer. When it breaks a
block it breaks the extra blocks around it — through your own game mode, so drops, XP, Fortune/Silk
Touch and durability all behave exactly like a hand-mined block. Hold **sneak** to break just one
block (unless `sneakToDisable` is off). The tool never snaps mid-vein: the sweep stops before its
last point of durability (an `unbreakable` or `rechargeOnKill`/`infinite` tool mines whole veins free).

```json
"mining": { "mode": "vein", "tags": ["#minecraft:iron_ores"], "maxBlocks": 64 }
```

```json
"mining": { "mode": "area", "radius": 1, "requireCorrectTool": true }
```

| Field | Default | Description |
|-------|---------|-------------|
| `mode` | `vein` | `vein` = flood-fill connected matching blocks; `area` = a `(2·radius+1)` square plane facing you (a hammer/excavator); `tunnel` = that plane extended `depth` blocks forward |
| `maxBlocks` | `64` | Hard cap on extra blocks broken per swing (server safety) |
| `radius` | `1` | `area`/`tunnel` half-width. `1` = 3×3, `2` = 5×5 |
| `depth` | `1` | `tunnel` only: how many blocks forward beyond the mined one |
| `matchBlock` | `true` | `vein` only: chain only blocks of the same type as the one mined (ignored when `whitelist`/`tags` are set) |
| `diagonal` | `true` | `vein` only: chain diagonally-touching blocks (26-neighbour), not just faces |
| `whitelist` | `[]` | Only break these block ids (alias: `blocks`). Empty = no id restriction |
| `tags` | `[]` | Only break blocks in these block tags, e.g. `#minecraft:logs`. Empty = no tag restriction |
| `requireCorrectTool` | `true` | Only break blocks this tool is the correct harvest tool for (a pickaxe won't chew dirt; an axe fells logs) |
| `maxHardness` | `-1` | Skip blocks harder than this (`-1` = no limit); unbreakable blocks (bedrock) are always skipped |
| `sneakToDisable` | `true` | Hold sneak to mine a single block |

**Tree-feller:** `{ "mode": "vein", "tags": ["#minecraft:logs"], "matchBlock": false, "maxBlocks": 128 }`
**3×3 hammer:** `{ "mode": "area", "radius": 1 }` · **Excavator tunnel:** `{ "mode": "tunnel", "radius": 1, "depth": 4 }`

**Notes:**
- The extra blocks are broken as if you mined each one, so each costs durability (unless the tool is unbreakable/infinite). `maxBlocks` and the durability guard keep a swing bounded.
- `vein` chains outward from the block you broke; with `matchBlock` (and no filter) it only follows the same block type — perfect for ores. Set `matchBlock: false` with a `tags` filter for tree-felling.
- `area`/`tunnel` orient to the face you're looking at, so a wall mines a vertical plane and the floor mines a horizontal one.

---

## Recharge on Kill

The `rechargeOnKill` root field rewards kills by recharging this item and/or repairing it. It is intentionally strong enough for "busted mending" artifacts: it can work from an inventory slot, repair durability-spending infinite items, grow max durability, and even make the item unbreakable.

```json
"rechargeOnKill": {
  "slot": "any",
  "entity": "any",
  "repairDamage": 1
}
```

Big artifact example:

```json
"rechargeOnKill": {
  "slot": "any",
  "target": "mobs",
  "entities": ["minecraft:wither", "minecraft:ender_dragon"],
  "cooldownTicks": 100,
  "cooldownPercent": 0.25,
  "cooldownAbilities": ["meteor_strike", "blood_pact"],
  "cooldownTriggers": ["on_right_click"],
  "resetCooldowns": false,
  "repairDamage": 25,
  "repairPercent": 0.10,
  "fullRepair": false,
  "addMaxDamage": 5,
  "maxDamageCap": 4000,
  "unbreakable": false,
  "chance": 1.0
}
```

| Field | Default | Description |
|-------|---------|-------------|
| `slot` / `hand` | `main` | Where the item can be when the player gets a kill: `main`, `off`, `both`, or `any`. `any` scans carried inventory slots too |
| `entity` | `any` | Shortcut target. Use `any`, `mobs`, `players`, or a specific entity id like `minecraft:zombie` |
| `target` | inferred | Explicit target mode: `any`, `mobs`, `players`, or `entities` |
| `entities` | [] | Specific entity ids. When present without `target`, target becomes `entities` |
| `players` / `playerNames` / `player` | [] | Specific player names or UUIDs to match |
| `chance` | 1.0 | Chance to apply after the target filter passes |
| `cooldownTicks` | 0 | Flat cooldown reduction for this item's active cooldowns |
| `cooldownPercent` | 0.0 | Percent of remaining cooldown to remove (`0.25` = 25%) |
| `resetCooldowns` | false | Clear matching cooldowns completely |
| `cooldownAbilities` / `abilityTypes` | [] | Only recharge these ability type ids. Empty = all abilities on this item |
| `cooldownTriggers` / `triggers` | [] | Only recharge abilities with these triggers. Empty = all triggers |
| `repairDamage` | 0 | Flat durability damage repaired |
| `repairPercent` | 0.0 | Repair a percent of current max durability |
| `fullRepair` / `repairToFull` | false | Set item damage to 0 on a matching kill |
| `addMaxDamage` / `addDurability` | 0 | Increase this stack's max durability on each matching kill |
| `maxDamageCap` | none | Cap for `addMaxDamage` growth |
| `unbreakable` / `makeUnbreakable` | false | Stamp the stack as unbreakable on a matching kill |

**Notes:**
- Durability repair works on normal damaged Fiw items and on `infinite: { "mode": "damage" }` items. Infinite remainders are re-stamped immediately after repair.
- `addMaxDamage` and `unbreakable` are stack upgrades. They survive `/fiwtools reload` because sync preserves earned max durability and unbreakable state.
- Cooldowns are per player and item id. If two copies share the same item id, recharging one item id affects that shared cooldown.

---

## Artifact Awakening

The `awakening` root field turns an item into a **dormant artifact** that upgrades itself into another item when its holder proves worthy.

```json
"awakening": {
  "trigger": "kill_entity",
  "entity": "minecraft:wither",
  "count": 3,
  "upgradeTo": "soul_blade_awakened",
  "message": "&5&lThe blade drinks the Wither's soul... it awakens!",
  "broadcast": true,
  "sound": "minecraft:entity.wither.spawn",
  "showProgress": true
}
```

| Field | Default | Description |
|-------|---------|-------------|
| `trigger` | required | `kill_entity`, `kill_player`, `deal_damage`, `visit_dimension` |
| `entity` | — | `kill_entity`: entity type id. Bosses are just ids — `minecraft:ender_dragon`, `minecraft:wither`, or any modded boss |
| `playerName` | any | `kill_player`: restrict to one specific victim (name or UUID) |
| `dimension` | — | `visit_dimension`: dimension id, e.g. `minecraft:the_end` |
| `count` | 1 | Kills needed, or total damage for `deal_damage` |
| `upgradeTo` | required | Fiw item id the artifact transforms into |
| `message` | generic | Awakening announcement (`&` codes) |
| `broadcast` | false | Send `message` to the whole server instead of just the holder |
| `sound` | wither spawn | Sound played at the holder |
| `showProgress` | true | Action-bar `Awakening 3/10` on each step |

**How it works:**
- Kill and damage progress counts the **main-hand** item; dimension visits check the whole inventory (checked once a second).
- Progress is stored on the specific stack and survives `/fiwtools reload`, rebuilds, and restarts.
- **Awaken more than once:** give `soul_blade_awakened` its own `awakening` block pointing at a third item, and so on — chains of any length.
- **Awaken by crafting:** make a [custom recipe](#custom-crafting-recipes) whose result is the awakened item (e.g. the dormant blade surrounded by nether stars).

---

## Bound Artifacts

The `binding` root field ties an item to **one owner** — defined by who touches it first.

```json
"binding": {
  "mode": "first_use",
  "curse": true,
  "cursePerTick": 2.0,
  "message": "&d&lThe artifact has sworn itself to you.",
  "blockUse": true
}
```

| Field | Default | Description |
|-------|---------|-------------|
| `mode` | required | `first_use` — binds to the first player who right-clicks or attacks with it. `first_pickup` — binds to the first inventory it lands in (detected within a second) |
| `curse` | false | Non-owners take damage every second while carrying it — the artifact punishes thieves. Uses the same armor-bypassing damage type as the [Curse System](#curse-system) |
| `cursePerTick` | 1.0 | Curse damage per second |
| `message` | generic | Shown to the player the moment it binds (`&` codes) |
| `blockUse` | true | Non-owners can't fire any of the item's abilities; they get a `Bound to <owner>` action-bar notice |

Ownership is stored on the stack and survives reloads, rebuilds, restarts, and being dropped — a bound artifact stays bound to its player forever.

---

## Item Commands (`run_command`)

The `run_command` ability (alias `command`) executes server commands when triggered — infinite ability possibilities without waiting for a dedicated ability type. Execution is **completely silent**: nothing in chat, nothing in the console, no admin broadcast.

```json
"abilities": [
  {
    "type": "run_command",
    "trigger": "on_right_click",
    "cooldownTicks": 100,
    "params": {
      "commands": [
        "effect give {player} minecraft:speed 15 1",
        "particle minecraft:end_rod {x} {y} {z} 0.5 1 0.5 0.05 40"
      ]
    }
  },
  {
    "type": "run_command",
    "trigger": "on_shift_right_click",
    "cooldownTicks": 1200,
    "params": { "command": "execute in minecraft:overworld run tp {player} 0 100 0" }
  }
]
```

| Param | Description |
|-------|-------------|
| `command` | A single command string (leading `/` optional) |
| `commands` | An array of command strings, run in order |

**Placeholders:**

| Placeholder | Replaced with |
|-------------|---------------|
| `{player}` | The triggering player's name |
| `{uuid}` | Their UUID |
| `{x}` `{y}` `{z}` | Their block position |
| `{target}` | The right-clicked/attacked entity's name (empty if none) |
| `{target_uuid}` | The right-clicked/attacked entity's UUID (empty if none). A UUID is a valid entity selector, so use this — not `{target}` — to run a command **on** the entity you hit, e.g. `effect give {target_uuid} minecraft:glowing 5 0`. `{target}` is only a display name and won't address a mob. |

All placeholders are sanitized (stripped to `[A-Za-z0-9_-]`) before substitution, so an entity renamed to a selector like `@a` can't inject commands.

Commands run at full permission level, so anything an op could do, an item can grant. Works with every trigger — pair `on_right_click` and `on_shift_right_click` for two command sets on one item, or hook `on_kill` / `on_hurt` for reactive effects.

---

## Charges and Use Costs

Give any item a **universal charge pool** and make abilities spend resources per use.

```json
"charges": { "max": 10, "start": 10, "regenTicks": 200, "regenAmount": 1 },
"abilities": [
  {
    "type": "meteor_strike",
    "trigger": "on_right_click",
    "cooldownTicks": 60,
    "cost": { "charges": 3, "health": 2.0 },
    "params": {}
  }
]
```

**`charges` block** (or shorthand `"charges": 10`):

| Field | Default | Description |
|-------|---------|-------------|
| `max` | required | Pool size |
| `start` | max | Charges on a freshly built stack |
| `regenTicks` | 0 | Ticks between natural regens; 0 = no regen. The countdown restarts whenever a charge is spent from full |
| `regenAmount` | 1 | Charges restored per regen |

**`cost` block** on any ability — all resources are checked *before* the ability runs (all-or-nothing) and only spent when it actually fires; a miss costs nothing:

| Field | Description |
|-------|-------------|
| `charges` | Charges from the item's pool |
| `durability` | Durability points — the item is never broken by a cost; the use is refused instead |
| `health` | Half-hearts — never lethal; the use is refused instead |
| `maxHealth` | Half-hearts of **max** health, sacrificed permanently until death (respawn restores them); refused below one remaining heart |
| `hunger` | Hunger points |
| `xpLevels` | XP levels |
| `item` + `itemCount` | Consume items from the inventory: `fiw:<id>`, a vanilla id, or `#tag` |

Refills: natural regen, or grant `"charges": N` inside [`rechargeOnKill`](#recharge-on-kill). Show the pool on the item with [Dynamic Lore](#dynamic-lore).

---

## Hold-to-Charge Items

Give an item a `chargeable` block (or `"chargeable": true`) and right-click becomes a bow-style
hold: the player charges as long as the button is held, and releasing fires the item's
`on_charge_release` abilities. Each ability can gate on the held time with `minChargeTicks` /
`maxChargeTicks` params, so one item can have weak / strong / overcharged tiers.

```json
"chargeable": { "animation": "bow" },
"abilities": [
  { "type": "arc_slash",  "trigger": "on_charge_release", "params": { "maxChargeTicks": 19 } },
  { "type": "shockwave",  "trigger": "on_charge_release", "params": { "minChargeTicks": 20, "maxChargeTicks": 59 } },
  { "type": "sonic_boom", "trigger": "on_charge_release", "params": { "minChargeTicks": 60 } }
]
```

| Field | Default | Description |
|-------|---------|-------------|
| `animation` | `bow` | Charge pose: `bow`, `spear`, `block`, `eat`, `drink`, `crossbow`, ... (1.21.1 always shows the eating pose) |
| `maxSeconds` | 3600 | Safety cap on a single hold |

Chargeable items shouldn't also carry `on_right_click` abilities (the click starts the charge),
and `chargeable` overrides the `food` block's use behaviour.

**Pick an inert base item** (rod, gem, sword, tool...). Bases with their own right-click use —
bow, crossbow, trident, shield, food — override the charge behaviour with their vanilla action,
and vanilla clients predict that action locally, so charging won't work on them.

---

## Stances

Give an item a `stances` list (2+ names) and **sneak + swap-hands** (default F while sneaking)
cycles its active stance instead of swapping — with an action-bar note and page-turn sound. The
active stance lives in the stack's custom data (first entry is the default). Any ability can
declare a `stance`: it only fires while that stance is active; abilities without one always
fire. `{stance}` works in `dynamicLore`.

```json
"stances": ["flame", "frost"],
"abilities": [
  { "type": "ignite", "trigger": "on_attack", "stance": "flame", "params": { "duration": 80 } },
  { "type": "freeze", "trigger": "on_attack", "stance": "frost", "params": { "duration": 80 } }
]
```

---

## Killstreak Momentum

Give an item a `killstreak` block and consecutive kills scored with it build a per-stack streak.
Every `killsPerTier` kills raises the tier (up to `maxTier`); each kill applies the listed buffs
at amplifier tier-1 for `buffDuration` ticks. Dying wipes the streak (unless `resetOnDeath` is
false). The counter lives in the stack's custom data, so it survives logouts and chests.

```json
"killstreak": {
  "killsPerTier": 3,
  "maxTier": 3,
  "buffs": ["strength", "speed"],
  "buffDuration": 200,
  "resetOnDeath": true,
  "tierMessage": "&c&lBloodlust {tier} &7({streak} kills)"
}
```

| Field | Default | Description |
|-------|---------|-------------|
| `killsPerTier` | 3 | Kills per buff tier |
| `maxTier` | 2 | Amplifier cap (tier N = amplifier N-1) |
| `buffs` | `["strength"]` | Effect names, same set as `passive_buff` |
| `buffDuration` | 200 | Ticks, refreshed on every kill |
| `resetOnDeath` | true | Wipe the streak on death |
| `tierMessage` | *(none)* | Action-bar line on tier-up; `{streak}` / `{tier}` placeholders |

`{streak}` and `{streak_tier}` also work in `dynamicLore`.

---

## Cooldown Groups

Give abilities a `cooldownGroup` name and they **share one cooldown per player — across items too**. This is the anti-combo dial: without it, carrying three blink items means three separate blink cooldowns.

```json
"abilities": [
  { "type": "blink", "trigger": "on_right_click", "cooldownTicks": 200, "cooldownGroup": "mobility" },
  { "type": "riptide_dash", "trigger": "on_shift_right_click", "cooldownTicks": 200, "cooldownGroup": "mobility" }
]
```

Firing either ability puts the whole `mobility` group on cooldown — including abilities in the same group on *other* items the player carries. Group names are case-insensitive. `rechargeOnKill` cooldown rewards recharge group cooldowns too.

---

## Proc Groups

Give two or more abilities the same `procGroup` and, on a given trigger, **only one of them fires** —
chosen by a single weighted roll using each ability's `chance` as its weight. Without a proc group,
every eligible ability rolls its own `chance` independently and any number can fire at once. This is
the "one of these on-hit effects happens, not all of them" dial.

```json
"abilities": [
  { "type": "ignite",       "trigger": "on_attack", "chance": 0.5, "procGroup": "on_hit" },
  { "type": "freeze",       "trigger": "on_attack", "chance": 0.3, "procGroup": "on_hit" },
  { "type": "stun_strike",  "trigger": "on_attack", "chance": 0.2, "procGroup": "on_hit", "cooldownTicks": 100 }
]
```

- The winner is picked in proportion to the `chance` values (above: 50 / 30 / 20).
- If the group's chances **sum below 1.0**, the shortfall is the probability that *nothing* fires
  (three effects at `0.1` each → 30 % one fires, 70 % none). Once they sum to ≥ 1.0 exactly one
  always fires and the values act purely as relative weights.
- Only eligible abilities enter the roll — an ability still on cooldown, gated by a failed
  condition, in the wrong stance, or whose `cost` can't be paid is skipped, and the roll happens
  among the rest (an unaffordable ability can never "steal" its group's proc).
- `procGroup` is independent of `cooldownGroup`; you can use both on the same ability.

---

## Ability Scaling

Any ability can carry a `scaling` block so its numbers grow with the holder's build instead of being
fixed. Each entry adds `perPoint × stat` to a base numeric param at fire time:

```json
{
  "type": "arc_slash", "trigger": "on_attack",
  "scaling": [
    { "stat": "streak_tier", "param": "damage", "perPoint": 2.0 },
    { "stat": "imbue_count", "param": "damage", "perPoint": 1.0, "cap": 5.0 }
  ],
  "params": { "damage": 6 }
}
```

| Field | Description |
|-------|-------------|
| `stat` | What drives the bonus: `streak_tier` (killstreak tier), `imbue_count` (times imbued), `charges` (current charge pool), or `max_health` (the holder's max-health value) |
| `param` | The `params` key whose number the bonus is added to. **The base value must be set explicitly in `params`** — a scaling entry that targets a param with no base fails the config at load (otherwise the bonus would silently replace the ability's built-in default and could make it weaker than unscaled) |
| `perPoint` | Amount added per point of `stat` |
| `cap` | Optional clamp on the total added bonus (before it's added to the base) |

In the example a tier-3 killstreak adds +6 damage, and each imbuement adds +1 up to a +5 cap — so a
fully-built blade hits far harder than a fresh one, with no new ability code. Scaling applies to
active and passive abilities alike, and stacks with everything else (cost, cooldown, proc groups).

---

## Ability Conditions

Every ability — active or passive, any trigger — accepts optional condition keys in its `params`. All declared conditions must hold (ANDed); a missing key never gates.

| Key | Type | Fires only when… |
|-----|------|------------------|
| `whenBelowHealth` | 0.0–1.0 | health fraction is at or below the value |
| `whenAboveHealth` | 0.0–1.0 | health fraction is at or above the value |
| `whenUnderwater` | bool | the player is underwater |
| `whenInWater` | bool | the player is touching water |
| `whenEnemyWithin` | blocks | a hostile mob is within the radius |
| `whenInCombat` | ticks | the player took damage within the last N ticks |
| `whenOutOfCombat` | ticks | the player has NOT taken damage for N ticks |
| `whenDimension` | id | the player is in that dimension |
| `whenDay` / `whenNight` | bool | it is day / night |
| `whenRaining` / `whenThundering` | bool | the weather matches |
| `whenSneaking` | bool | the player is sneaking |
| `whenOnFire` | bool | the player is burning |

```json
{ "type": "second_wind", "trigger": "on_hurt",
  "params": { "whenBelowHealth": 0.3, "whenNight": true } }
```

---

## Dynamic Lore

`dynamicLore` lines are appended below the static `lore` and refreshed live (once a second) with the stack's real state:

```json
"dynamicLore": [
  "&8Owner: &7{owner}",
  "&8Charges: &c{charges}&8/&c{max_charges}",
  "&8Souls: &5{souls}&8/&5{soul_capacity}",
  "&8Awakening: &7{awakening_stage}"
]
```

| Placeholder | Shows |
|-------------|-------|
| `{owner}` | Binding owner's name, or `Unbound` |
| `{charges}` / `{max_charges}` | Charge pool state |
| `{souls}` / `{soul_capacity}` | Soul system state |
| `{awakening_stage}` | `progress/target` while dormant, `Awakened` when the item has no awakening left |
| `{awakening_progress}` / `{awakening_count}` | Raw awakening numbers |
| `{durability}` / `{max_durability}` | Remaining / max durability |
| `{streak}` / `{streak_tier}` | Killstreak count and current tier |
| `{stance}` | Active stance name |

The lore component is only rewritten when the text actually changes, so idle items cost nothing to keep fresh.

---

## Bind with Fiw Bosses

Fiw Custom Items and Fiw Bosses are **fully independent mods** — each one works without the other.

When **both** are installed, Fiw Bosses can reference a Fiw Custom Items entry by id from its loot or equipment slots:

```json
// boss loot entry
{ "toolId": "soul_blade", "count": 1, "chance": 0.25 }

// boss equipment slot
"mainHand": { "toolId": "soul_blade" }
```

Internally, Fiw Bosses calls the stable public API documented below. If Fiw Custom Items is missing, the lookup is silently skipped (the loot entry drops nothing, the equipment slot stays empty) — boss configs never crash because of a missing install.

The reverse direction does **not** apply: Fiw Custom Items never reads Fiw Bosses. The two mods can be released, updated, and run independently.

---

## Public API for Other Mods

Other mods can integrate with Fiw Custom Items without depending on its internals. FIW Bosses uses this same API by reflection, so these signatures are kept stable across loader modules. The API class keeps the Fiw Tools name for compatibility:

```text
com.fiw.tools.api.FiwToolsAPI.isLoaded(): boolean
com.fiw.tools.api.FiwToolsAPI.listIds(): Set<String>
com.fiw.tools.api.FiwToolsAPI.getItemStack(String id, MinecraftServer server, int count): ItemStack?
com.fiw.tools.api.FiwToolsAPI.getItemStack(String id, HolderLookup.Provider registries, int count): ItemStack?
```

### Behavior

| Method | Behavior |
|--------|----------|
| `isLoaded()` | Returns `true` when Fiw Custom Items is present and the API class loaded successfully. |
| `listIds()` | Returns the currently loaded item ids. Empty means no item configs have loaded yet. |
| `getItemStack(id, server, count)` | Builds a fresh stack from the currently loaded config id. Returns `null` if the id is unknown or cannot be built. |
| `getItemStack(id, registries, count)` | Same lookup when the caller already has registry access. |

### Failure rules

- Unknown ids return `null`.
- Missing Fiw Custom Items should be treated as "skip this optional item", not as a hard error.
- Returned stacks are fresh copies; callers can safely place them in loot drops, inventories, or equipment slots.
- The API does not force-load configs. Call it after the server has started and Fiw Custom Items has loaded its registry.

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
- Fiw Custom Items has no client-side code; install it on the server only.
- On Fabric, fully vanilla clients can connect with no Fiw Custom Items install.
- On NeoForge, clients do not need Fiw Custom Items installed to join.
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
