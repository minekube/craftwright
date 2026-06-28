# Latest Official Fabric Lane Boundary Design

## Problem

Phase 145 made the latest/current Fabric lane blocker executable. Running the
latest official probe under Java 25 now fails with
`blockers=loom-remap-requires-mappings` because the existing
`driver-fabric` module uses the `fabric-loom-remap` plugin. That plugin is
appropriate for the verified Yarn/remap lanes, but it cannot represent the
26.x official/unobfuscated lane boundary where Fabric's current porting
direction removes the Yarn mappings dependency.

## Goal

Introduce a separate internal `driver-fabric-official` module that uses the
non-remap Fabric Loom plugin, Java 25, Minecraft `26.2`, Fabric Loader
`0.19.3`, and Fabric API `0.153.0+26.2`. Reroute the latest official probe to
compile that module and write `status=compiled` when the boundary compiles.

## Non-Goals

- Do not package the official module as a supported driver lane.
- Do not add a `26.2` entry to `driver-mods.json`.
- Do not claim `latest-release` support.
- Do not clone the Yarn/remap gameplay bindings into the official module.
- Do not expose Fabric, Mojang, Yarn, intermediary, or Minecraft names as
  public API contracts.

## Design

`driver-fabric-official` is an internal build/probe module only. It should:

- apply `net.fabricmc.fabric-loom`, not `net.fabricmc.fabric-loom-remap`;
- override the Java toolchain to 25;
- depend on Minecraft `26.2`, Fabric Loader `0.19.3`, and Fabric API
  `0.153.0+26.2`;
- omit a Yarn mappings dependency;
- provide a tiny client entrypoint that proves Fabric loader/API integration
  compiles;
- provide `fabric.mod.json` metadata for the probe artifact;
- produce no public gameplay actions, runtime graph bindings, or packaged
  distribution manifest entries.

The existing `driver-fabric` module remains the verified Yarn/remap product
lane for current and representative older support. Phase 146 only removes the
build-system blocker for the latest/current lane.

## Acceptance

- Settings include `driver-fabric-official`.
- The root Gradle plugin block declares `net.fabricmc.fabric-loom`.
- The official module applies non-remap Loom and does not reference
  `fabric-loom-remap` or `craftless.fabric.yarnMappings`.
- The latest official mise probe compiles `:driver-fabric-official`.
- `mise run fabric-lane-check-latest-official` writes `status=compiled`.
- No packaged CLI/Docker driver manifest includes a 26.x official lane.
