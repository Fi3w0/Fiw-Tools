# Changelog

All notable changes to this project are documented here. Each release's section below
is used as the GitHub Release notes by the release workflow when the tag matches the
version header.

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

