# Changelog

All notable changes to this project are documented here. Each release's section below
is used as the GitHub Release notes by the release workflow when the tag matches the
version header.

## [1.1.1] - HOTFIX: custom recipe crafting dupe

> **Update immediately if you use custom recipes.** Taking a custom recipe's result from a
> crafting grid did not consume the ingredients — vanilla found no recipe of its own for the
> grid and handed every ingredient back as a "remainder", merging it onto the grid stacks.
> A slot holding n items came back as roughly 2n, so players could multiply any ingredient
> infinitely; the resulting oversized stacks (>99) also failed chunk/Ledger serialization.

### Added

- **`arc_slash_barrage`** — cinematic single-target combo: opening line + charge-up sound,
  a volley of arc slashes rolled at configurable angles carving a single victim, then a teleport
  behind them and one heavy armor-ignoring finisher. Targets mobs and players; with no victim it
  slashes the air in front instead. Every message, sound, particle, roll, delay and damage number
  is a param; `ignoreArmor`/`slashIgnoreArmor` take true/false or a percent for partial armor
  piercing
- **`arc_slash_storm`** — the same engine as an area ultimate: every slash fires at every
  matching entity in `radius` (default: players and mobs within 40), 15 rolls fanning flat to
  near-vertical, no finisher. Example item: `examples/items/dismantle_blade.json`

### Fixed

- **Crafting dupe** — taking a custom recipe result now consumes exactly one item per grid slot
  (crafting remainders like buckets are still returned), instead of letting vanilla's
  no-recipe fallback refund and double the ingredients
- Recipe results with a config `count` above the item's max stack size are clamped, so a
  mis-configured recipe can no longer inject oversized stacks into the world
