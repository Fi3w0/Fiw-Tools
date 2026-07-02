# v1.1.0 test pack

Low-value configs for the 1.1.0 systems. Copy `items/` into `config/fiw_tools/items/` and
`/fiwtools reload`.

| Test | Item | How to verify |
|------|------|---------------|
| Charges + cost + regen + dynamic lore | `test_charge_wand` | Blink costs 2 charges + 1 hunger; lore shows `{charges}/{max_charges}` live; a charge regens every 5 s; kills grant +2 |
| Cooldown groups | `test_group_dash` + wand | Dash puts the wand's blink on cooldown too (shared `test_mobility` group) |
| on_pickup / on_drop / on_death / on_respawn / dimensions | `test_trigger_totem` | Action-bar whispers on each event; nether entry/overworld exit filtered by `dimension` param |
| on_equip / on_unequip + dyedColor | `test_equip_helm` | "helm on/off" action bar; helmet renders green |
| on_consume + whenNight + on_boss_kill + potionColor | `test_boss_feast` | Drink at night = regen (day = nothing); kill a wither/dragon/warden/elder guardian or FIW boss while holding = 5 diamonds; brew is purple |
