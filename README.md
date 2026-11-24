# NTM: GregTech Integration

Addon that connects HBM's Nuclear Tech Mod: Decentralized Edition and GregTech CEu Modern.

- Minecraft 1.21.1, NeoForge 21.1.267
- GregTech CEu Modern 8.0.0-SNAPSHOT, NTM: Decentralized Edition 1.0.0-beta.15

## Build

1. Put the dependency jars into `libs/` (list in `libs/README.md`).
2. `./gradlew build` → `build/libs/`. Needs JDK 21 (Gradle picks it via toolchains).
3. `./gradlew runClient` starts a dev client with the mods from `libs/`.

## License

GPL-3.0-or-later, see `LICENSE`.
