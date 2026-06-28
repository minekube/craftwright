# Fabric API Cache Resolution Design

## Problem

Cache preparation resolves Mojang metadata, Java runtimes, Fabric Loader
metadata, and Fabric Loader profile libraries, but it does not resolve the
Fabric API mod artifact for the requested Minecraft version. That leaves the
multi-version foundation incomplete: latest/current and older runtime lanes
cannot become provider-backed without resolving both Fabric Loader and Fabric
API from metadata for the selected version.

Fabric API is a mod artifact, not just a library. It should be represented in
cache and launch metadata as a Fabric mod so a later launch step can place it in
the instance `mods` directory instead of treating it as a static compiled-lane
classpath constant.

## Goals

- Resolve the latest compatible Fabric API Maven artifact for a requested
  Minecraft version from Fabric Maven metadata.
- Cache the selected Fabric API jar as a `FABRIC_MOD` artifact.
- Expose Fabric mod handles in `CacheLaunchPlan` so launchers can copy them to
  the instance mods directory.
- Keep using Ktor-backed cache metadata fetching; do not add OkHttp, Java
  `HttpClient`, or static version catalogs.
- Keep latest/current and representative older support open until a complete
  provider-backed runtime lane and generated API/CLI gameplay verification
  land.

## Non-Goals

- Do not add new compiled Fabric/Loom lanes in this phase.
- Do not claim `26.2`, `1.20.6`, or broad Fabric client support.
- Do not add public version-specific APIs, generated route families, CLI
  gameplay catalogs, Fabric gameplay bindings, or scenario shortcuts.
- Do not mark the project complete.

## Acceptance Criteria

- A Fabric cache-prepare result for a version with Fabric API Maven metadata
  contains one `FABRIC_MOD` artifact for the matching `+<minecraftVersion>`
  Fabric API artifact.
- `CacheLaunchPlan.mods` includes the Fabric API mod handle.
- Existing Fabric Loader library handling remains unchanged.
- Product code contains no hard-coded latest/older Fabric API version catalog.
- Focused daemon/protocol tests and local gates pass.
