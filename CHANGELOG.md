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

### Fixed

- **Crafting dupe** — taking a custom recipe result now consumes exactly one item per grid slot
  (crafting remainders like buckets are still returned), instead of letting vanilla's
  no-recipe fallback refund and double the ingredients
- Recipe results with a config `count` above the item's max stack size are clamped, so a
  mis-configured recipe can no longer inject oversized stacks into the world
