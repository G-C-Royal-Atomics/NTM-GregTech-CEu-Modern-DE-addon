# NTM: GregTech Integration

Addon that connects HBM's Nuclear Tech Mod: Decentralized Edition and GregTech CEu Modern.

- Minecraft 1.21.1, NeoForge 21.1.267
- GregTech CEu Modern 8.0.0-SNAPSHOT, NTM: Decentralized Edition 1.0.0-beta.15

## For modpacks

### Settings

Three files in `config/`, every option commented inside:

- `ntmgt-startup.toml` — what changes registration: NTM metals as GT materials, NTM items used as GT's,
  GT metals as NTM materials (and which to leave out), shared fluids, GT radioactivity off. Restart to apply.
- `ntmgt-common.toml` — game rules: what GT items melt in NTM's crucible, hot ingots, slag for rubber and
  plastics, cable insulation, GT ores and their metal/stone amounts, GT molds in NTM's foundry, NTM ducts taking
  from GT only on the output side, NTM hazards on GT items. Read while the game runs.
- `ntmgt-client.toml` — tooltips (formulas, fluid lines, material ids) and what JEI hides.

### Molten metal ids

Every GT metal and alloy with an ingot is an NTM material named like the GT material: `bronze`,
`stainless_steel`, `tungsten_steel`, ... NTM's own keep NTM's names (`copper`, `steel`, `aluminum`). NTM recipe
files take these names, e.g. a crucible alloy in a datapack (`data/<pack>/recipe/crucible/<name>.json`):

```json
{
  "type": "hbm:crucible",
  "frequency": 9,
  "icon": {"id": "gtceu:bronze_ingot"},
  "input_materials": [{"amount": 54, "material": "copper"}, {"amount": 18, "material": "tin"}],
  "order": 2049,
  "output_materials": [{"amount": 72, "material": "bronze"}]
}
```

72 = one ingot. In game: hover molten metal or scraps (JEI's crucible and casting pages) with advanced tooltips
(F3+H) on — the last line is `NTM material: <id>` (`tooltips.materialIds = "always"` shows it without F3+H).

## Build

1. Put the dependency jars into `libs/` (list in `libs/README.md`).
2. `./gradlew build` → `build/libs/`. Needs JDK 21 (Gradle picks it via toolchains).
3. `./gradlew runClient` starts a dev client with the mods from `libs/`.

## License

GPL-3.0-or-later, see `LICENSE`.
