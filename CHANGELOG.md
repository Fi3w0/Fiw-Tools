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

