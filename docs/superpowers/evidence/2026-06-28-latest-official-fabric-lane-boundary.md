# Latest Official Fabric Lane Boundary Evidence

Phase 146 adds a separate internal non-remap official Fabric lane boundary for
the latest/current Minecraft 26.x build path.

## TDD Record

Red command:

```sh
mise exec -- gradle :driver-fabric:test --tests '*FabricDriverModuleTest.latest official lane probe uses separate non remap module boundary'
```

Observed red result before implementation:

```text
FabricDriverModuleTest > latest official lane probe uses separate non remap module boundary() FAILED
```

Green command:

```sh
mise exec -- gradle :driver-fabric:test --tests '*FabricDriverModuleTest.latest official lane probe uses separate non remap module boundary' --tests '*FabricDriverModuleTest.mise latest lane probe uses official mapping boundary not yarn remap lane*'
```

Observed green result:

```text
BUILD SUCCESSFUL in 1s
17 actionable tasks: 17 up-to-date
```

Direct official module compile/jar command:

```sh
mise exec -- gradle :driver-fabric-official:compileKotlin :driver-fabric-official:processResources :driver-fabric-official:jar
```

Observed result:

```text
BUILD SUCCESSFUL in 4s
4 actionable tasks: 2 executed, 2 up-to-date
```

Older lane regression command:

```sh
mise run fabric-lane-check-older
```

Observed result:

```text
BUILD SUCCESSFUL in 8s
9 actionable tasks: 4 executed, 5 up-to-date
```

## Boundary

The new internal module is `driver-fabric-official`.

It uses:

- non-remap `net.fabricmc.fabric-loom`;
- Java 25 toolchain;
- Minecraft `26.2`;
- Fabric Loader `0.19.3`;
- Fabric API `0.153.0+26.2`;
- no Yarn mappings dependency;
- a minimal client entrypoint only.

It is not packaged as a supported driver lane and is not added to
`driver-mods.json`.

## Probe

Command:

```sh
mise run fabric-lane-check-latest-official
```

Observed result:

```text
BUILD SUCCESSFUL in 1s
4 actionable tasks: 4 up-to-date
status=compiled
```

Status artifact:

```text
status=compiled
```

Lint verification:

```sh
mise exec -- gradle lint
```

Observed result:

```text
BUILD SUCCESSFUL in 4s
101 actionable tasks: 7 executed, 94 up-to-date
```

Whitespace verification:

```sh
git diff --check
```

Observed result: exit code 0 with no output.

This removes the Phase 145 `loom-remap-requires-mappings` build blocker. It
does not prove latest/current runtime support. The next work is to extract or
port the shared driver attach/discovery/projection boundary into this official
module, then package, launch, self-attach, expose generated
OpenAPI/actions/resources, stream SSE, and pass public API/CLI gameplay
evidence.
