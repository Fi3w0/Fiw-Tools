# QA pack

One test pack for the current version, updated in place each release. Use the Prism instances **FIW Test Fabric 1.21.8** and **FIW Test NeoForge 1.21.11** (Java 21) with a new disposable world. Fabric also needs Fabric API and Fabric Language Kotlin.

## Build before installing

From this repo, run Gradle on JDK 25 (the per-target toolchains select Java 21/25):

```sh
JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-25.jdk/Contents/Home ./gradlew build --stacktrace
```

The two player-test jars should be `fabric-1.21.8/build/libs/fiw-tools-fabric-1.21.8-<version>.jar` and `neoforge-1.21.11/build/libs/fiw-tools-neoforge-1.21.11-<version>.jar`. Do not rename an old jar to these names. NeoForge ships its own nested Kotlin runtime; no Kotlin-for-Forge dependency is required. Some older NeoForge dev `runServer` configurations have a known Kotlin classpath problem, so validate production jars separately if a dev launch fails.

## Install and setup

Copy the seven files from `items/` directly into `config/fiw_tools/items/` and `recipes/qa_recipe.json` into `config/fiw_tools/recipes/`. Copy `settings/off.json` to `config/fiw_tools/settings.json` initially. These are instance-level configs, not world folders. Keep any original settings backed up. Do not copy `negative/` into an active config folder yet.

Run in player chat as an operator (the mod commands require level 2):

```text
/fiwtools reload
/fiwtools list
/fiwtools recipes
/scoreboard objectives add fiw_qa dummy
/scoreboard objectives setdisplay sidebar fiw_qa
/scoreboard players set @s fiw_qa 0
/fiwtools give @s qa_slot_cd
/fiwtools give @s qa_group_a
/fiwtools give @s qa_group_reset
/fiwtools give @s qa_group_reduce
/fiwtools give @s qa_audit
/fiwtools give @s qa_audit_helm
/fiwtools give @s qa_soul_blade
/give @s minecraft:iron_sword
/gamemode survival
```

A successful wand activation increments the scoreboard once and shows a cyan `QA ... fired` line. A blocked activation does neither. Run `scoreboard players get <name> fiw_qa` from the server console to inspect the count. Console commands omit the leading slash; `@s` in these setup commands means a player and will not work as the console.

## Cooldowns and recharge

- [ ] **Slot cooldown:** right-click `qa_slot_cd`, immediately reconnect, then right-click again. It must stay blocked. Duplicating the same item with `/fiwtools give` must not avoid that player's cooldown.
- [ ] **Shared group:** activate `qa_group_a`, then try `qa_group_reset` and `qa_group_reduce`. Both must be blocked despite different ids and mixed-case group spelling. Reconnect and repeat: still blocked.
- [ ] **Clean server restart:** arm both the slot and group cooldowns; stop cleanly with `stop` (dedicated server) or Save & Quit (singleplayer), reopen, and try both again. They must remain blocked until their remaining online ticks elapse. Back up/check `config/fiw_tools/data/cooldowns/<uuid>.json` while stopped: positive remaining ticks, keys `qa_slot_cd#0` and `group:qa_shared`, not absolute world time.
- [ ] **Reset:** keep `qa_group_reset` in the offhand, hold a vanilla iron sword, run `/summon minecraft:chicken ~2 ~ ~`, and kill that chicken yourself. Switch to Group A: it should activate immediately. Log out/in: its newly armed cooldown must remain; no old reset snapshot may overwrite it.
- [ ] **Reduction:** arm Group A, equip `qa_group_reduce` offhand and kill one chicken with the vanilla sword. It subtracts 200 ticks plus half of the remaining cooldown, so it becomes ready sooner. Reconnect during the reduced remainder; it must not return to the original 1200 ticks.
- [ ] **Reset survives stop:** arm Group A, perform the offhand reset, stop before using Group A again, restart and activate it. The cleared shared key must not return from disk.
- [ ] **Reload/dimension:** arm a cooldown, `/fiwtools reload`, then change dimension and return. It must remain armed. For a quick dimension check use `/execute in minecraft:the_nether run tp @s 0 100 0`, then `/execute in minecraft:overworld run tp @s 0 100 0` with an appropriate safe landing/creative mode.

Optional longer check on the Fabric instance only: disconnect with most of the 60-second cooldown remaining, wait over 60 real seconds, reconnect. It must still be blocked: offline time pauses remaining ticks. This need not be repeated on every target.

## Audit modes and dispatch

Switch modes by copying the relevant `settings/*.json` over the active `settings.json`, then `/fiwtools reload`.

- [ ] **Off:** use `qa_audit`. The deliberate `qa_intentionally_missing` command must not prevent the final cyan message. No new audit JSON lines should appear; check new entries rather than expecting an old file to disappear.
- [ ] **Console:** use it again, then sneak-right-click it. Console JSON must include your name/UUID, `qa_audit`, ability index **0** for normal click and **1** for shift-click, and the substituted command with your actual name. The invalid command attempt must also be logged. Normal click executes three commands; shift-click executes two.
- [ ] **File:** repeat. New JSON lines must append to `config/fiw_tools/logs/commands.jsonl`, including the failed attempt. Reconnect and repeat to verify appending. Stop cleanly and check the last commands were flushed.
- [ ] **Passive:** wear `qa_audit_helm`; wait one sweep. Audit identifies `qa_audit_helm`, index 0. Its 60-second cooldown should prevent constant message spam. Remove it after checking.
- [ ] **Invalid mode:** copy `settings/invalid-mode.json`, reload, and use the audit wand. Expect an unknown-mode warning and `off` behavior. Restore `off.json` afterwards.

## Config stress and existing state

- [ ] With the soul blade, kill two chickens yourself. Note the soul lore, reload, reconnect: the count stays at two. Rename a stack at an anvil; verify a reload keeps the player rename.
- [ ] In the live `qa_slot_cd.json`, change only `displayName` to `&aQA Reloaded`, then reload. The inventory stack should update without resetting its cooldown. Restore the fixture file afterwards.
- [ ] Place one paper in a crafting grid. Result: one `qa_slot_cd`; taking it consumes the paper. Reload with the grid open and check there is no duplication.
- [ ] Copy `negative/qa_bad_item.json.invalid` into active `items/` as `qa_bad_item.json`, then reload. Expect a reported `stackSize` validation failure for that file; valid QA items must still load. Remove only this temporary active copy, reload, and check the failure is gone.

Report the instance, first failing step, expected/actual scoreboard or file contents, and the new log lines. Keep the world/config/data/log files until the test is resolved. No push or release until testing passes.
