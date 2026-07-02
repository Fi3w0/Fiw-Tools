# v1.0.4 test pack

Cheap, low-value configs to verify every 1.0.4 mechanic fast. Copy `items/` and `recipes/`
into `config/fiw_tools/` and run `/fiwtools reload`.

| Test | Item / recipe | How to verify |
|------|---------------|---------------|
| Infinite keep (food) | `test_inf_food` | Eat repeatedly — never consumed. Craft: 1 dirt |
| Infinite keep (arrow) | `test_inf_arrow` | Shoot with a bow — arrow returns instantly, can't pick up the fired one. Craft: 1 gravel |
| Infinite damage | `test_inf_damage_bread` | Each bite costs 1 durability; gone after 3 bites |
| Infinite replace | `test_inf_replace_pearl` | Throw — pearl becomes a slime ball |
| Awakening kill_entity | `test_dormant_stick` | Kill 2 zombies → becomes Awakened Stick (action-bar progress) |
| Awakening deal_damage | `test_awakened_stick` | Deal 10 total damage → Final Stick (broadcast) |
| Awakening visit_dimension | `test_nether_charm` | Carry into the Nether → awakens within a second |
| Awakening by craft | recipe `test_craft_awakening` | Dormant stick + diamond → Awakened Stick |
| Binding first_use + curse | `test_bound_blade` | First hit/right-click binds; another player holding it takes 1 dmg/s and can't use it |
| Binding first_pickup | `test_bound_pickup` | Binds on pickup; non-owner right-click is refused |
| run_command + shift trigger | `test_cmd_wand` | Right-click = Speed + particles, shift-right-click = Jump Boost, silent |
| Custom→vanilla recipe | `test_custom_to_vanilla` | Infinite steak + stick → 2 diamonds |
| Tag ingredient recipe | `test_tag_wand` | Any plank + stick → Command Wand |
| Grid protection | any two damaged fiw swords | Vanilla repair result stays empty |
