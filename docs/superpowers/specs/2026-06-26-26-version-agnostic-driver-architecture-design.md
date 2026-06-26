# Version-Agnostic Driver Architecture Design

## Intent

Make the Fabric driver and runtime discovery architecture capable of supporting
many current and future Minecraft Java client versions without turning
Craftless into a catalog of version-specific public actions.

The current implementation proves the runtime graph direction, but it is still
shaped around one Fabric/Loom target:

- `driver-fabric/build.gradle.kts` pins Minecraft `1.21.6`, Yarn
  `1.21.6+build.1`, Fabric Loader `0.19.3`, and Fabric API
  `0.128.2+1.21.6`.
- `driver-fabric/src/main/.../fabric/v1_21_6/` and matching tests put the
  whole driver, probes, adapters, smoke plans, mixin package, and entrypoint
  behind a 1.21.6 package name.
- `fabric.mod.json` names the driver as a Minecraft 1.21.6 scaffold, depends
  on exactly `minecraft: 1.21.6`, and points the client entrypoint at
  `v1_21_6`.
- The local server smoke defaults to Minecraft `1.21.6`, so evidence currently
  exercises one server/client version lane.

That is acceptable as bootstrap evidence. It is not a durable architecture for
Craftless. The product promise is a live generated API for the running client,
where Minecraft version, loader, mappings, installed mods, registries, server
features, permissions, and live state are runtime inputs to discovery.

## Product Rules

- Public API remains Craftless-owned and generated from the per-client runtime
  graph.
- Minecraft, Fabric, Yarn, intermediary, mod package, and launcher names remain
  private implementation inputs. They may appear in private source evidence and
  diagnostics, not as public routes, action ids, CLI commands, event names, or
  README contracts.
- Do not add one public descriptor/binding pair per Minecraft action or per
  Minecraft version as the durable design.
- Do not add version modules as public API. If version-specific source sets,
  strategies, or adapters exist, they are internal driver implementation
  details behind stable Craftless graph interfaces.
- Unavailable runtime support is first-class. A probe may discover an
  affordance and mark it unavailable with a machine-readable reason when the
  running version, loader, mapping set, mod set, permissions, or client state
  cannot execute it.
- Generated per-client OpenAPI remains the source of truth for agents, CLI
  dispatch/help, SDK generation, and external gameplay composition.

## Architecture

`driver-fabric` should keep one consolidated product module where practical,
but split its internal responsibilities into stable and version-sensitive
boundaries.

The stable side owns Craftless concepts:

- driver lifecycle and `DriverSession` integration;
- runtime metadata collection shape;
- `RuntimeCapabilityGraph` probe orchestration;
- Craftless resource/action/handle/event ids;
- graph-to-adapter dispatch keys;
- public evidence redaction and namespace policy checks.

The version-sensitive side owns Minecraft/Fabric access:

- Fabric Loader, Fabric API, and game version metadata reads;
- mapping and name-resolution inputs;
- registry, callback, screen, handler, world, entity, inventory, and permission
  probes that depend on Minecraft binary/source signatures;
- mixins and accessors that need exact method descriptors;
- client-thread execution adapters that touch Minecraft classes directly;
- optional backend integration probes such as pathfinding runtime detection.

Those version-sensitive pieces must be selected through internal strategy or
provider interfaces after the running client is identified. Strategies may be
implemented with direct typed code for a compatible version family, reflection,
mapping-aware lookup, registry inspection, or small mixins/accessors. The
selection result is not a public API contract; it is private runtime evidence
that explains which probe and adapter families are active.

## Version Facades

Introduce explicit internal facades for Minecraft-sensitive operations before
adding more version breadth:

- runtime identity: game version, protocol/data version when available, loader
  version, Fabric API version, mappings fingerprint, installed mod fingerprint;
- client state: connected client, player, world, camera, interaction manager,
  current screen, inventory/container access;
- registries: blocks, items, entity types, game events, screen handlers, tags,
  and identifiers projected into Craftless handles/categories;
- targeting and interaction: raycast, block break, block interact, entity
  attack, inventory equip, and screen close as generic execution ports;
- event hooks: lifecycle, client tick, chat, action progress, and graph
  operation events.

The facades should expose Craftless-domain values or private evidence objects,
not raw Minecraft classes, across broad driver boundaries. Narrow implementation
classes may still use `net.minecraft.*` and Fabric APIs internally.

## Compatibility Matrix

Support should be expressed as a compatibility matrix, not as a single
hard-coded version:

- latest supported release lane;
- previous recent release lane;
- at least one older Java 17/21-compatible lane once feasible;
- loader and Fabric API ranges that are known to work;
- optional backend compatibility, including pathfinder runtime availability;
- unsupported lanes with explicit reasons.

The matrix is an internal quality gate and documentation artifact. It should
drive tests, smoke selection, and release notes, but it must not freeze public
actions to a version-specific catalog.

## Probe Metadata

Every version-sensitive probe or adapter should report private capability
metadata:

- provider id and implementation family;
- supported version range or predicate;
- selected mapping namespace/fingerprint;
- required classes, methods, registries, callbacks, or accessors;
- probe result: available, unavailable, or failed;
- machine-readable reason for unavailable or failed support;
- redacted diagnostic details suitable for artifact inspection.

Projection may use the availability state and Craftless-owned reason code.
Public OpenAPI and SSE streams must not expose raw class names, method names,
Yarn/intermediary names, or mod implementation names as contracts.

## Testing And Evidence

Unit tests should make it hard to accidentally add new single-version coupling:

- source layout tests identify Minecraft-version package roots and force them
  behind explicit internal boundaries;
- Fabric metadata tests assert `fabric.mod.json`, mixin config, and entrypoint
  naming do not claim only one Minecraft version once compatibility work is
  implemented;
- graph tests assert availability and private evidence change by runtime
  version/provider selection;
- public namespace tests continue to reject Fabric/Yarn/intermediary/raw
  Minecraft leakage;
- smoke tests can select a Minecraft version from the compatibility matrix and
  record the chosen runtime lane in artifacts.

Live smoke should remain evidence, not product truth by itself. It proves that
one matrix lane works. Completion requires the architecture to make adding more
lanes an internal compatibility exercise, not a public API expansion exercise.

## Non-Goals

- Do not implement the version-agnostic refactor in this spec.
- Do not mark Craftless complete.
- Do not add `find.tree`, `mine.log`, `kill.cow`, `craft.sword`, survival
  macros, or any other scenario shortcut.
- Do not split the public API by Minecraft version.
- Do not expose Fabric/Yarn/intermediary names in generated OpenAPI, CLI help,
  SSE events, README examples, or agent-facing docs.

## Completion Gate

This phase is complete only when the implementation plan has been executed and
evidence shows:

- 1.21.6-specific code is isolated behind documented internal facades or
  provider boundaries;
- runtime graph discovery records selected version/provider metadata and
  explicit unavailable/failure reasons;
- compatibility matrix tests cover at least the current lane and one additional
  version lane or a simulated provider lane;
- public generated OpenAPI remains Craftless-owned and graph-derived;
- no new public gameplay breadth was added as a static descriptor/binding
  catalog;
- project checklist and agent guidance are updated only after the architecture
  changes are accepted and verified.
