# FIW Tools Examples

Copy item JSON files from `examples/items/` into:

```text
config/fiw_tools/items/
```

Then run:

```text
/fiwtools reload
```

## FIW Bosses Integration

`examples/fiw_bosses_integration/` contains a matched cross-mod setup:

- Copy `examples/fiw_bosses_integration/items/*.json` into `config/fiw_tools/items/`.
- Copy `examples/fiw_bosses_integration/bosses/void_reaver_boss.json` into `config/fiw_bosses/bosses/`.
- Run `/fiwtools reload` and `/boss reload`.

The boss uses Fiw Tools item ids through `toolId` equipment and loot entries. If Fiw Tools is missing, FIW Bosses skips those entries instead of crashing.
