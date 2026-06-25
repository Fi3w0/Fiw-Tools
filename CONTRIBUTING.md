# Contributing to FIW Tools

## Getting Started

1. Fork the repo and clone it.
2. Open the project in your IDE.
3. Run `./gradlew build` to download dependencies and compile all targets.

## Build

The mod currently targets Fabric and NeoForge across three modules:

```bash
./gradlew build
```

For a single target:

```bash
./gradlew :fabric-1.21.11:build
```

## Development Workflow

- New item or ability logic should start in `common-1.21.11`.
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
./gradlew :fabric-1.21.11:build :neoforge-1.21.11:build :fabric-26.2.1:build
```

For in-game testing, build the relevant jar and copy it to a matching Minecraft instance's `mods/` folder.

## Pull Requests

- Ensure the Gradle build passes.
- Include config examples when adding visible item behavior.
- Mention whether FIW Bosses `toolId` integration is affected.
