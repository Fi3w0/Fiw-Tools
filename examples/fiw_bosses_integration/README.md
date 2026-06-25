# FIW Bosses Integration Example

This folder mirrors the cross-mod example from FIW Bosses.

## Install

```text
examples/fiw_bosses_integration/items/*.json
-> config/fiw_tools/items/

examples/fiw_bosses_integration/bosses/void_reaver_boss.json
-> config/fiw_bosses/bosses/
```

Then run:

```text
/fiwtools reload
/boss reload
/boss spawn void_reaver
```

## Item ids used by the boss

- `void_reaver`
- `starfall_halberd`
- `eclipse_crown`
- `rift_apple`

FIW Bosses looks these up through `FiwToolsAPI.getItemStack(id, server, count)`.
