# Arcadia Patch Create

`arcadia-patch-create` is a focused NeoForge performance patch mod for Arcadia's Minecraft 1.21.1 server stack.

The repository intentionally keeps `main` limited to patches that have been tested and approved. Experimental ideas,
profiling notes, and broader optimization research live on `dev`.

## Current validated patch set

- Create `BeltInventory` early return when a belt is completely empty.

## Supported environment

- Minecraft `1.21.1`
- NeoForge `21.1.221`
- Create `6.0.9`
- Java `21`

## Build

```powershell
./gradlew.bat clean jar
```

The compiled jar is generated in `build/libs/`.

## Repository layout

- `src/main/java` - mod sources
- `src/main/resources` - NeoForge and Mixin metadata
- `docs/stable` - approved reports and test procedures
- `docs/research` - investigation material, only on `dev`

## Validation policy

Only patches that meet all of the following criteria should be merged into `main`:

- clean startup
- no known gameplay regression
- plausible Spark improvement on the targeted hotspot
- conservative implementation with a narrow blast radius
