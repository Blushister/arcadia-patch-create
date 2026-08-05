# Arcadia Patch Create

`arcadia-patch-create` is a focused NeoForge performance patch mod for Arcadia's Minecraft 1.21.1 server stack.

It removes redundant work from Create and a few of its addons without changing how any machine behaves. Every
optimization can be toggled at runtime, is validated against an exact bytecode fingerprint, and falls back to the
original logic whenever anything is unexpected.

Project links:

- Repository: `https://github.com/Team-Arcadia/arcadia-patch-create`
- Issues: `https://github.com/Team-Arcadia/arcadia-patch-create/issues`

## Supported environment

| | |
|---|---|
| Minecraft | `1.21.1` |
| NeoForge | `21.1.221` |
| Create | `6.0.10` |
| Java | `21` |
| Mod version | `1.4.4` |

Server-side only. The admin panel uses a vanilla menu type, so it works in single player and on a dedicated server
without any client-side installation.

## Patch set

### Performance

| Patch | What it removes |
|---|---|
| CreateHeatJS metadata cache | A full recipe-list scan on every basin heat check |
| Item Drain lookup reuse | The duplicate recipe lookup inside a single processing call |
| Fluid pipe idle fast-path | The tick of pipes with no pressure and no flow |
| Fluid pipe connection map | Hash-table iteration over a six-entry map, four times per pipe per tick |
| Belt empty fast-path | The tick of belts holding no items |
| Factory Gauge throttle | Panel inspections while the global throttle is engaged |

### Stability

- Reject unreadable or corrupted contraption NBT before Create dereferences it.
- Guard invalid continuous OBB collision manifolds where Create can expose a null axis.
- Discard invalid contraption entities on load instead of crashing the server tick.

### Optional

- Faster despawn for physical items dropped by Create machines, off by default.

## Safety model

Every patch answers three separate questions, all three visible in the admin panel:

- **configured** - is the toggle on?
- **available** - did the target bytecode match its fingerprint?
- **effective** - is the patch actually running?

A patch is applied only when the target class matches an exact SHA-256 hash *and* the expected member layout and call
chain. A missing addon, a Create update, or any unknown bytecode disables the related patch group and leaves the
original logic untouched. The mod still starts normally.

No cache outlives the call that created it unless its invalidation is proven. Recipe metadata is keyed per
`RecipeManager` and rebuilt on datapack reload; per-call frames are cleared in a `finally` block and reset when
reopened, so nothing is ever reused across ticks.

## Administration

```
/arcadiapatchcreate panel                    open the admin panel
/arcadiapatchcreate status                   one-line status of every module
/arcadiapatchcreate debug dump               detailed runtime report
/arcadiapatchcreate <module> enabled <bool>  toggle a single module
/arcadiapatchcreate throttle mode <mode>     off | static <interval> | adaptive
```

Modules: `master`, `belt`, `fluid`, `factoryGauge`, `heatJs`, `itemDrain`, `createDrops`.

State is persisted to `config/arcadia-patch-create.properties` and reapplied on startup.

The global throttle spaces out passive inspections when the server is under load. In `adaptive` mode the interval
follows the measured MSPT at the 35 / 45 / 55 ms steps; `static` uses a fixed interval regardless of load.

## Build

```powershell
./gradlew.bat clean build
```

The compiled jar is generated in `build/libs/`.

A development server with the full Create stack is available for validating patches against a real environment:

```powershell
./gradlew.bat runServer
```

## Repository layout

- `src/main/java/.../bootstrap` - fingerprint validation, decides which mixins apply
- `src/main/java/.../mixin` - the patches themselves
- `src/main/java/.../bridge` - direct accessors to Create state, used instead of reflection
- `src/main/java/.../runtime` - patch state, counters, configuration persistence
- `src/main/java/.../command`, `.../menu`, `.../debug` - administration surface

Test procedures, benchmarks and server analysis reports are kept internally and are not published with the mod.

## Validation policy

A patch is merged only when it meets all of the following:

- clean server startup with and without every optional addon
- no gameplay regression: no machine slowed, no reaction delayed, no event missed
- a measured improvement on the targeted hotspot, verified by an A/B profile on the same server
  with the module toggled off and on, corrected against unpatched block types as a baseline
- a narrow blast radius and a documented fallback to the original behaviour
