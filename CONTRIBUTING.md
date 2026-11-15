# Contributing to Fiw Custom Items

## Getting Started

1. Fork the repo and clone it.
2. Open the project in your IDE.
3. Run `./gradlew build` to download dependencies and compile all targets.

## Build

The mod currently targets Fabric and NeoForge across five loader modules:

```bash
./gradlew build
```

For a single target:

```bash
./gradlew :fabric-1.21.11:build
```

## Development Workflow

- New item or ability logic should start in `common-1.21.11`, then be ported to the other `common-*` source sets as API differences require.
- Loader wiring belongs in `fabric-*` or `neoforge-*`, not in `common-*`.
- Version-specific API changes should stay inside the matching `common-*` folder.
- Keep the public `FiwToolsAPI` signatures stable because FIW Bosses reflects them.

## Code Conventions

- 4-space indentation, UTF-8, LF line endings.
- Keep Kotlin common logic loader-neutral.
- Keep per-loader event adapters thin.
- No AI co-author trailers on commits. Author must be a human.

## Testing

Run the current matrix before pushing:

```bash
./gradlew :fabric-26.1.2:build :fabric-1.21.11:build :neoforge-1.21.11:build :fabric-1.21.8:build :neoforge-1.21.8:build :fabric-1.21.1:build :neoforge-1.21.1:build
```

Then play the built jars with the scripted client (Node 22+ required). It boots each production
jar in a disposable server under `build/smoke/`, installs the QA pack from `examples/qa`, uses
the items like a player would and restarts the server:

```bash
npm ci --prefix .github/player-tests --ignore-scripts
bash .github/scripts/verify-target.sh fabric-26.1.2   # repeat for every module
```

CI runs the same check for every target and only publishes when all of them pass.

For manual in-game testing, build the relevant jar and copy it to a matching Minecraft instance's `mods/` folder.

## Pull Requests

- Ensure the Gradle build passes.
- Include config examples when adding visible item behavior.
- Mention whether FIW Bosses `toolId` integration is affected.
