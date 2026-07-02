# Changelog

All notable changes to this project are documented here. Each release's section below
is used as the GitHub Release notes by the release workflow when the tag matches the
version header.

## [1.1.0] - Charges, costs, ten new triggers, conditions, dynamic lore

> **Big systems update.** A universal charge economy, per-ability use costs, shared cooldown
> groups, ten new item lifecycle triggers, conditions on every ability, live dynamic lore, and
> vanilla visual components — plus a round of fixes. Fully additive and backward-compatible;
> still 100% server-side.

### Added

- **Universal charges** — `charges` block (`max`, `start`, `regenTicks`, `regenAmount`); per-stack pool that regenerates over time, survives reloads, and can be granted by `rechargeOnKill` (`"charges": N`)
- **Use costs** — per-ability `cost` block: `charges`, `durability`, `health`, `hunger`, `xpLevels`, and `item`+`itemCount` (vanilla, `fiw:` or `#tag`). All-or-nothing, checked before the ability fires, only spent when it actually acts; never breaks the item, never kills the payer
- **Cooldown groups** — `cooldownGroup` on any ability shares one per-player cooldown across abilities *and items*, killing broken multi-item combos
- **Ten new triggers** — `on_consume`, `on_pickup`, `on_drop`, `on_death`, `on_respawn`, `on_enter_dimension`, `on_leave_dimension` (with `dimension` param filter), `on_equip`, `on_unequip` (with `slot` param filter), and `on_boss_kill` (vanilla bosses + every FIW Bosses entity, or an explicit `bosses` list)
- **Ability conditions everywhere** — the passive condition keys now gate *every* trigger, plus new ones: `whenDimension`, `whenDay`/`whenNight`, `whenRaining`/`whenThundering`, `whenSneaking`, `whenOnFire`, `whenInWater`
- **Dynamic lore** — `dynamicLore` lines refreshed live with `{owner}`, `{charges}`/`{max_charges}`, `{souls}`/`{soul_capacity}`, `{awakening_stage}`/`{awakening_progress}`/`{awakening_count}`, `{durability}`/`{max_durability}`
- **Visual components** — `potionColor` (potion liquid), `dyedColor` (leather), `itemModel` (render as another item's model; 1.21.2+ only)

### Fixed

- Recharge durability rewards (`addMaxDamage`, `unbreakable`) are now recorded per stack and replayed on rebuilds — config edits that lower `maxDamage` or remove `unbreakable` win again
- 1.21.1: infinite food no longer re-stamps its remainder every sweep (stopped needless client resyncs)
- `rechargeOnKill` with `target: "mobs"` plus a `players` filter is now rejected at parse time instead of silently never firing
- `rechargeOnKill` cooldown rewards now also recharge group cooldowns

## [1.0.6] - Recharge on kill item rewards

### Added

- Added `rechargeOnKill`, a kill reward block for cooldown recharge and durability upkeep.
- Supports `slot` / `hand` filters: `main`, `off`, `both`, and `any` carried inventory slot.
- Supports target filters for any living entity, mobs, players, specific entity ids, and specific player names/UUIDs.
- Cooldowns can be reset, reduced by flat ticks, reduced by percentage, and filtered by ability type or trigger.
- Durability rewards can repair flat damage, repair by percent, fully repair, increase max durability, cap max durability growth, or make the stack unbreakable.

### Fixed

- Durability-spending infinite items are re-stamped immediately after `rechargeOnKill` repair so their next-use remainder stays correct.
- Item sync now preserves earned max durability and earned unbreakable state across `/fiwtools reload`.

### Changed

- Bumped the mod version to 1.0.6.

## [1.0.5] - Minecraft 1.21.1 ports and release publishing

### Added

- Added Fabric 1.21.1 and NeoForge 1.21.1 loader targets.
- Added the new 1.21.1 jars to automated GitHub release and Modrinth publishing.

### Fixed

- Added a 1.21.1-compatible infinite food fallback using the old food conversion field, since Minecraft 1.21.1 does not have the generic `use_remainder` data component.
- Hardened Modrinth publishing with target-specific version names, missing-jar checks, clearer curl failures, and manual release checkout by the selected tag.

### Changed

- Bumped the mod version to 1.0.5.
- Rebranded public-facing text to Fiw Custom Items while keeping the existing mod id, config paths, `/fiwtools` command, and API names for compatibility.
- Updated public docs and issue templates for the expanded target matrix.

## [1.0.4] - Custom crafting, infinite items, awakening, binding, and command abilities

> **Major systems expansion.** Five new server-side systems: a JSON custom crafting system,
> infinite-use items, artifact awakening, bound artifacts, and command-executing abilities.
> All changes are purely additive and backward-compatible — existing item configs continue
> to work without modification. Still 100% server-side: vanilla clients need nothing.

### Added

- **Custom crafting system** (`com.fiw.tools.recipe`):
  - New config folder `config/fiw_tools/recipes/` — drop JSON files, `/fiwtools reload` picks them up
  - Multiple recipes per file: single object, bare array, or `{ "recipes": [ ... ] }`
  - Shaped (`pattern` + `key`, with mirroring) and shapeless (`ingredients`) recipes
  - Ingredients/results: `fiw:<id>` custom items, vanilla ids, and `#namespace:tag` item tags
  - Craft custom items from vanilla, vanilla from custom, or custom from custom
  - Works in the 2x2 inventory grid and the 3x3 crafting table, fully server-side — vanilla clients craft normally
  - Grid protection: when Fiw items are in the grid and no custom recipe matches, vanilla results are suppressed (no more losing artifacts to the sword-repair recipe)
  - `/fiwtools recipes` lists every loaded recipe

- **Infinite-use items** (`com.fiw.tools.infinite`):
  - New `infinite` item field — string shorthand (`"infinite": "keep"`) or object form
  - Mode `keep` (alias `normal`): the item is never consumed — infinite food, potions, snowballs, ender pearls, arrows…
  - Mode `damage`: each use costs `damagePerUse` durability instead of consuming the item; it breaks for real when durability runs out
  - Mode `replace`: the item turns into `replaceWith` (vanilla id or `fiw:` item) × `replaceCount` when used
  - Built on vanilla's `use_remainder` component + a re-stamp sweep — survives reloads and restarts
  - Arrows fired from bows/crossbows are restored to the shooter instantly via an entity-spawn hook; fired arrows can't be picked up (no duping), multishot and Infinity are handled

- **Artifact awakening** (`com.fiw.tools.awaken`):
  - New `awakening` item block — the artifact auto-upgrades into `upgradeTo` when its condition is met
  - Triggers: `kill_entity` (any entity id — wither, ender dragon, or any boss), `kill_player` (any player, or one specific `playerName`), `deal_damage` (total damage dealt while holding the item), `visit_dimension`
  - Per-stack progress stored on the item and preserved across `/fiwtools reload` and rebuilds
  - `count` sets kills needed / damage required; optional action-bar progress (`showProgress`)
  - Awakening `message` (text codes) to the holder or the whole server (`broadcast: true`), plus a `sound`
  - Chain awakenings by giving the upgraded item its own `awakening` block (awaken once or many times)
  - Craft-based awakening: make a custom recipe whose result is the awakened item

- **Bound artifacts** (`com.fiw.tools.bind`):
  - New `binding` item block — the artifact belongs to one player once bound
  - `mode: "first_use"` binds to the first player who right-clicks or attacks with it; `mode: "first_pickup"` binds to the first inventory it lands in
  - Non-owners can't trigger any of its abilities (`blockUse`, default on) — they get a "Bound to X" action-bar notice instead
  - Optional `curse: true`: non-owners carrying someone else's artifact take `cursePerTick` damage per second (same armor-bypassing damage type as the curse system)
  - Custom bind `message` (text codes); ownership survives reloads, rebuilds, and restarts

- **Item commands** (`run_command` ability, alias `command`):
  - Executes any server command(s) when triggered — infinite custom abilities via the command system
  - Fully silent: no chat feedback, no console log, no admin broadcast
  - `command` (single) or `commands` (array) in params; leading `/` optional
  - Placeholders: `{player}`, `{uuid}`, `{x}` `{y}` `{z}` (block position), `{target}`
  - Works on any trigger, including the new one below

- **New trigger `on_shift_right_click`** (aliases `shift_right_click`, `sneak_right_click`):
  - Fires instead of `on_right_click` when the player is sneaking
  - Backward compatible: items without a shift ability keep firing `on_right_click` while sneaking
  - Lets one item carry two separate right-click ability sets
