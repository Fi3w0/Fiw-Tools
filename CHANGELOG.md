# Changelog

All notable changes to this project are documented here. Each release's section below
is used as the GitHub Release notes by the release workflow when the tag matches the
version header.

## [1.0.2] - FIW Bosses-style monorepo

> **Repository restructure.** This release reorganizes Fiw Tools into one root project
> with shared code, loader-specific modules, shared docs, examples, CI, and release
> automation. The goal is to keep Fabric, NeoForge, and future version targets in one
> unified repo without breaking FIW Bosses' optional reflection integration.

> ⚠️ **Not play-tested at release.** All three targets build cleanly and configs parse, but
> the in-game behavior (abilities, passives, curses, imbuements, sync) was **not fully
> play-tested before this release**. Fabric 1.21.11 was polished in-game before being merged
> into the monorepo, so it is the most stable target; NeoForge 1.21.11 and Fabric 26.1.2 are
> expected to behave the same but are still unverified. Testing of all three is in progress, and
> **a hotfix is likely within 2–3 days** of release. Please report anything broken on the
> [issue tracker](https://github.com/Fi3w0/Fiw-Tools/issues).

### Added

- **Root monorepo layout** matching the FIW Bosses project style:
  - `core`
  - `common-1.21.11`
  - `fabric-1.21.11`
  - `neoforge-1.21.11`
  - `common-26.2.1`
  - `fabric-26.2.1`
- **Minecraft-free `core` module** for shared parsing and text helpers:
  - `ItemDefinition`
  - `ItemConfigParser`
  - `TextCodes`
- **Core unit tests** for the shared module:
  - item config parsing
  - missing required item config fields
  - example item JSON parsing
  - legacy `&` text-code segmentation
- **NeoForge 1.21.11 build target**:
  - NeoForge module at `neoforge-1.21.11`
  - Java entrypoint at `FiwToolsNeoForge`
  - `META-INF/neoforge.mods.toml`
  - NeoForge server/client run configs through ModDevGradle
  - shared `common-1.21.11` Java, Kotlin, and resources wired into the NeoForge build
- **NeoForge Kotlin packaging**:
  - the NeoForge jar includes Kotlin stdlib in the built mod jar
  - docs now explain that Fabric needs Fabric Language Kotlin separately, while the NeoForge build includes the Kotlin runtime it needs
- **Fabric 1.21.11 build target** in the root monorepo:
  - module at `fabric-1.21.11`
  - shared `common-1.21.11` source and resources wired into the Fabric build
  - `core` output bundled into the loader jar
- **Fabric 26.x build target** in the root monorepo:
  - module at `fabric-26.2.1`
  - shared `common-26.2.1` source and resources wired into the Fabric build
  - `core` output bundled into the loader jar
- **Shared common source trees** for the active Minecraft targets:
  - command registration and `/fiwtools` commands
  - item config registry/loading
  - ability registry, dispatcher, targeting, cooldowns, and state
  - active ability implementations
  - passive held/worn handlers
  - curse handling
  - keep-on-death handling
  - item sync handling
  - imbuement handling
  - mixins for anvil, grindstone, block placement, and inventory-drop behavior
  - damage type data resources for curse damage behavior
- **Loader-specific event adapters**:
  - Fabric event wiring lives in each Fabric module's `FiwTools.kt`
  - NeoForge event wiring lives in `FiwToolsNeoForge`
  - shared server lifecycle, tick, join/leave, and ability trigger handling stays in common code
- **Mod icon packaging** using the root `fiw tools.jpg` artwork:
  - `assets/fiw-tools/icon.png`
  - root `icon.png`
  - included through the shared common resources for current loader builds
- **Root documentation in the FIW Bosses style**:
  - `README.md`
  - `MODRINTH.md`
  - `ITEM_CONFIG_DOCS.md`
  - `CONTRIBUTING.md`
  - `SECURITY.md`
  - `CHANGELOG.md`
- **Root example configs**:
  - shared example item JSON files under `examples/items`
  - FIW Bosses integration examples under `examples/fiw_bosses_integration`
  - example docs explaining where to copy item and boss configs
- **FIW Bosses integration example**:
  - sample boss config using Fiw Tools `toolId` references
  - matching sample item configs used by that boss example
- **GitHub project metadata**:
  - `CODEOWNERS`
  - issue templates
  - pull request template
  - `.editorconfig`
  - `.gitattributes`
- **Build workflow**:
  - installs JDK 21 and JDK 25
  - runs `./gradlew build --stacktrace`
  - uploads built jars as a workflow artifact
- **Release workflow**:
  - builds all targets on `v*` tags
  - collects non-source, non-dev loader jars
  - excludes the standalone `core` jar from release uploads
  - extracts the matching changelog section into GitHub release notes

### Changed

- **Project structure changed from separate loader/version folders to one root Gradle project.**
  The root `settings.gradle` now includes the active build targets from one place.
- **Current supported build matrix is explicit and limited to the requested targets:**
  - Fabric 1.21.11
  - NeoForge 1.21.11
  - Fabric 26.x through the `fabric-26.2.1` module
- **The `fabric-26.2.1` module keeps the requested folder/module name**, but the buildable Minecraft
  coordinate is currently `26.1.2` because Fabric's official metadata endpoint rejects `26.2.1`.
- **Item config parsing moved into `core`.**
  The common Minecraft-facing item registry now delegates JSON parsing to `ItemConfigParser` instead
  of keeping that parsing model duplicated inside each common source tree.
- **Text-code parsing support moved into `core`.**
  The `TextCodes` helper is tested without requiring Minecraft, making basic legacy color/style parsing
  easier to verify.
- **Common entry behavior was split from loader bootstrapping.**
  `FiwToolsCommon` owns shared startup, tick, stop, player join/leave, and player action hooks; Fabric
  and NeoForge modules only adapt their loader events into those common methods.
- **The 26.x common source tree was synced to the same repository shape as 1.21.11.**
  Its commands, item registry, ability packages, passive handlers, curse/death/sync handlers, imbuement
  code, mixins, data resources, and icon resources now live under `common-26.2.1`.
- **FIW Bosses public API surface was kept stable for reflection users.**
  `FiwToolsAPI` still exposes:
  - `isLoaded()`
  - `listIds()`
  - `getItemStack(String id, MinecraftServer server, int count)`
  - `getItemStack(String id, HolderLookup.Provider registries, int count)`
- **Fabric docs were made more precise.**
  Fabric support is documented as server-side friendly for matching vanilla clients when using server-safe behavior.
- **NeoForge docs were made more visible.**
  README and Modrinth notes now state that NeoForge is a first-class build, is installed on the server only
  (the mod is server-side and clients do not need it), and includes the Kotlin runtime inside the Fiw Tools NeoForge jar.
- **README was rewritten around the monorepo.**
  It now documents supported targets, loader requirements, commands, features, build commands, examples,
  FIW Bosses integration, and repository layout.
- **Modrinth description was rewritten.**
  It now presents the JSON item workflow, supported versions, loader requirements, ability highlights,
  FIW Bosses integration, and the 26.1.2/26.2.1 coordinate note.
- **Item config docs were expanded into a full reference.**
  The docs cover item JSON fields, commands, abilities, passives, curses, imbuements, API usage, examples,
  server/client behavior notes, and current limitations.

### Fixed

- **Docs no longer make the project look Fabric-only.**
  README and Modrinth now include NeoForge install and Kotlin runtime notes next to the Fabric dependency notes.
- **Docs no longer say only "26.2.1" for the current 26.x target.**
  The supported-version notes now explain that the module is named `fabric-26.2.1`, while the buildable
  Minecraft target is currently `26.1.2`.
- **FIW Bosses integration was kept optional.**
  The documented API remains reflection-friendly so FIW Bosses can skip `toolId` entries when Fiw Tools is absent.
- **The mod icon is now available from shared resources.**
  Loader jars include `icon.png` and `assets/fiw-tools/icon.png` from the generated `fiw tools.jpg` artwork.
- **NeoForge jar now carries the real mod version.**
  The `neoforge-1.21.11` build sets `version`/`group` from `gradle.properties`, so the jar name and
  `neoforge.mods.toml` report the mod version instead of `unspecified`.

### Requirements

- Fabric 1.21.11:
  - Minecraft 1.21.11
  - Fabric Loader 0.19.2
  - Fabric API 0.141.4+1.21.11
  - Fabric Language Kotlin 1.13.11+kotlin.2.3.21
  - Java 21
- NeoForge 1.21.11:
  - Minecraft 1.21.11
  - NeoForge 21.11.42
  - Kotlin runtime included inside the Fiw Tools NeoForge jar
  - Java 21
- Fabric 26.x:
  - module folder: `fabric-26.2.1`
  - current buildable Minecraft target: 26.1.2
  - Fabric Loader 0.19.2
  - Fabric API 0.148.0+26.1.2
  - Fabric Language Kotlin 1.13.11+kotlin.2.3.21
  - Java 25

### Verification

- `./gradlew build --continue --console=plain` completed successfully after the monorepo restructure.
- The build covered:
  - `:core:test`
  - `:fabric-1.21.11:build`
  - `:neoforge-1.21.11:build`
  - `:fabric-26.2.1:build`
- Built loader jars were checked for packaged icon resources.
- FIW Bosses-facing `FiwToolsAPI` method signatures were checked in the compiled output.

### Known notes

- The `fabric-26.2.1` module name is intentional, but the Minecraft coordinate remains `26.1.2` until a valid
  Fabric/Minecraft `26.2.1` coordinate exists.
- Fabric builds need Fabric API and Fabric Language Kotlin as separate mods.
- Fiw Tools is server-side only on every target — it ships no client-side code, so it is installed on the
  server and clients do not need the mod (fully vanilla clients can connect on Fabric).
