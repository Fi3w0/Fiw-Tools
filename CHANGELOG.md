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
  piercing; the slash trail itself is drawn with a fully purple witch/dragon-breath/spark mix
  (`arcParticles`)
- **`arc_slash_storm`** — the same engine as an area ultimate: every slash fires at every
  matching entity in `radius` (default: players and mobs within 40), 15 rolls fanning flat to
  near-vertical, no finisher. Example item: `examples/items/dismantle_blade.json`
- **`beam`** — the Fiw Bosses beam attack as an item ability: spiral charge-up at the eyes, then
  a sustained end-rod/spark beam along the crosshair that damages everything in its cylinder;
  sweeps with your aim (or locks direction), stops at walls unless `throughBlocks`. All sounds,
  particles, windup/duration/damage/width/range configurable. Example:
  `examples/items/beam_staff.json`
- **`slash_wave`** — flying crescent slash (purple, matching the arc-slash family) that travels
  along the crosshair cutting everything it passes; `waves`+`spreadAngle` fires a fan, `roll`
  tilts it vertical. Example: `examples/items/crescent_edge.json`
- **`arrow_rain`** — mark a zone at the crosshair, telegraph ring, then real arrows (kill credit,
  not collectable) pour down in waves; `flaming`/`critical` options. Example:
  `examples/items/tempest_bow.json`
- **`sonic_boom`** — warden-style charge then an instant shockwave ray that pierces walls,
  armor-ignoring damage plus heavy knockback. Example: `examples/items/warden_horn.json`
- **`grapple`** — hook onto a block and get reeled in (sneak lets go); `examples/items/hookshot.json`
- **`summon`** — temporary allied mobs that fight for you and poof after a duration;
  `examples/items/wolfpack_totem.json`
- **Killstreak momentum** — per-stack `killstreak` block: consecutive kills build buff tiers,
  death resets, `{streak}`/`{streak_tier}` lore placeholders; `examples/items/bloodthirst_axe.json`
- **`maxHealth` sacrifice cost** — abilities can permanently (until death) cut the payer's max health
- **Seven new triggers** — `on_shoot`, `on_arrow_hit`, `on_shield_block`, `on_crit`,
  `on_block_place`, `on_fall` (with `minDistance`), and `on_charge_release`
- **Hold-to-charge items** — `chargeable` block gives bow-style charging; release fires
  `on_charge_release` abilities tiered by `minChargeTicks`/`maxChargeTicks`;
  `examples/items/stormcaller_lance.json`
- **Stances** — `stances` list + sneak+swap-hands toggle; abilities gate on `stance`, `{stance}`
  lore placeholder; `examples/items/twinfang_blade.json`
- **Recipe book & viewer display for custom crafts** (1.21.2+ versions) — custom recipes are
  published into the vanilla recipe manager as real server-side recipes, so the vanilla recipe
  book, JEI, REI and EMI all show them (custom names/lore included in ingredients and results)
  through plain vanilla display sync — zero client-side code, zero viewer dependencies, vanilla
  clients unaffected; recipes carry real placements (base item standing in for `fiw:`
  ingredients) so JEI lists them too. A registered recipe serializer (with a real stream codec
  rebuilding the recipe client-side) is required for this: JEI/REI/EMI read Fabric's own
  recipe-sync module, which only syncs recipes whose serializer a client has declared support
  for — the vanilla recipe book uses a separate always-on display path and doesn't need this,
  which is why it worked first and viewers didn't
- **Friendlier attributes** — `type` accepts bare constant names (`ATTACK_DAMAGE`,
  `MOVEMENT_SPEED`, any case) alongside full ids, auto-prefixed per game version; `body` slot and
  legacy operation names (`addition`, `multiply_base`, `multiply_total`) accepted; docs now list
  every vanilla attribute
- **Enchantment transfer in custom crafts** — an `enchantments` block on a recipe carries the
  consumed ingredients' enchantments onto the result, so players upgrading enchanted gear into
  custom gear keep their enchantments. Fully configurable: `mode` (`merge` / `keep_old` /
  `keep_new`), per-enchant `conflict` policy when both sides have the same one (`highest`,
  `lowest`, `old`, `new`, `add`, or anvil-style `combine`), donor filtering (`from`), id
  whitelist/blacklist (`only`/`exclude`), `transferCurses`, `respectMaxLevel`, hard `maxLevel`
  cap, `onlyCompatible` (result must be able to hold it), `checkConflicts` (drop vanilla-
  incompatible pairs). Safe by construction: donors are always consumed by the craft (transfer
  moves, never copies), final levels are clamped to the 1–255 storable range, duplicates are
  impossible, enchanted books donate and receive via stored enchantments, and a `count > 1`
  result refuses to load without an explicit `allowMultiplied` opt-in so enchantments can't be
  silently multiplied across copies
- **Enchanting control per item** — new `enchanting` root field decides what each station may do
  with the item: `table` (enchanting table), `anvil` (books/repair/combine, blocked with the item
  in either slot), `grindstone` (disenchanting — still blocked by default so configured
  enchantments stay permanent; two-item grindstone merges involving a Fiw item stay blocked even
  when enabled), `smithing` (netherite upgrade/trims), and `allowRename` (`false` keeps the anvil
  usable for enchantments but forces the configured name back onto the result; rename-only
  operations yield nothing, and renames are wiped on reload re-sync). Defaults match the old
  behaviour exactly; explicit values override the curse locks, and the enchanting-table block now
  also works on 1.21.1 (which previously had no way to refuse cursed items at the table)

### Fixed

- **Crafting dupe** — taking a custom recipe result now consumes exactly one item per grid slot
  (crafting remainders like buckets are still returned), instead of letting vanilla's
  no-recipe fallback refund and double the ingredients
- Recipe results with a config `count` above the item's max stack size are clamped, so a
  mis-configured recipe can no longer inject oversized stacks into the world
- Stale custom crafting results can no longer be exploited: if `/fiwtools reload` removes or
  changes a recipe while its result is showing in an open grid, the result is wiped (vanilla's
  own result is recomputed), and a take racing the same tick still consumes the ingredients
  instead of triggering vanilla's no-recipe refund
- `on_drop` no longer fires for the mod's own overflow drops (full-inventory spill from
  `/fiwtools give`, infinite-ammo returns, keep-on-death restores, crafting remainders) — it now
  only means a deliberate player toss
- `infinite.replaceCount` above the item's max stack size is clamped, closing the same oversized-
  stack hole as recipe `count`
- `beam` with `windup: 0` now plays its fire sound
- Summons no longer risk being culled by their own stray-cleanup during spawn
- Kills are credited to the weapon that actually scored them — a projectile kill resolves to the
  bow that fired the arrow even if you swapped items mid-flight (on_kill, boss kills, awakening,
  killstreak)
- `on_shield_block` fires for the item that actually blocked, not both hands
- Imbued abilities now keep their `cost`, `stance` and `cooldownGroup` (previously silently dropped)
- `on_pickup` abilities act on the real inventory stack instead of a snapshot copy
- `soul_surge` no longer burns its souls when nothing was in range
- `/fiwtools reload` rebuilds preserve killstreak and stance state alongside awakening/binding/charges
- `uncurse` with `scope: main_hand` targets the selected hotbar slot (was always slot 0)
- Builds with `--configure-on-demand` no longer fail on the core source-set reference (all
  loader modules now declare the core evaluation dependency)
- The stale-result marker is stored as a defensive copy so slot mutations can't break the
  reload-race protection
- `on_pickup` resolves the exact picked-up stack when the player carries multiple copies of the
  same item (component match first, id match as fallback)
- `deal_damage` awakening progress resolves the credited item through the damage source, so
  projectile damage credits the bow even after an item swap
