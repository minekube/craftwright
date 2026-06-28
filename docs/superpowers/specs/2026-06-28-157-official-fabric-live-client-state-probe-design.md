# Official Fabric Live Client State Probe Design

## Problem

The latest/current official Fabric lane now launches, self-attaches, reads live
Fabric Loader metadata, and composes shared registry/event/client-state graph
fragments. Its client-state graph is still always built from
`FabricClientStateGraphSnapshot.disconnected()`, even when the in-client
official driver is running inside Minecraft `26.2`.

That keeps the lane at metadata-only evidence and hides the next real
compatibility boundary: whether official/Mojang mappings can safely inspect the
running client state on the Minecraft client thread.

## Goal

Add a narrow official-lane client-state provider:

- it reads the running `net.minecraft.client.Minecraft` singleton through the
  official/Mojang mapping boundary;
- it queries on the Minecraft client thread with `submit(...)` when needed;
- it maps only booleans into shared `FabricClientStateGraphSnapshot`;
- `OfficialFabricDriverBackend` composes that snapshot through
  `fabricClientStateGraphFragment`;
- runtime evidence changes from `metadata-only` to a client-state-probe status.

This is runtime evidence plumbing, not gameplay support. The official lane must
still expose zero gameplay actions until generic official-lane operation
discovery/adapters exist.

## Non-Goals

- Do not add public gameplay actions.
- Do not copy Yarn/remap Fabric gateways, bindings, or operation adapters.
- Do not package the official 26.x lane into the public driver manifest.
- Do not add static action catalogs, CLI gameplay commands, or scenario tasks.
- Do not claim latest/current support.

## Design

Create an official-lane `OfficialFabricClientStateProvider` fun interface that
returns `FabricClientStateGraphSnapshot`.

The production provider should live in `OfficialFabricClientStateProvider.kt`
and depend only on official 26.2 Minecraft names available to the official
module:

- `net.minecraft.client.Minecraft.getInstance()`;
- `Minecraft.isSameThread()`;
- `Minecraft.submit { ... }`;
- public state fields/methods such as `player`, `level`, `gameMode`,
  `getConnection()`, and `getCameraEntity()`;
- inherited player access such as `inventory`, `containerMenu`, and
  `recipeBook`.

`OfficialFabricDriverBackend` should accept a provider constructor parameter so
tests can inject snapshots without instantiating Minecraft.

The official backend should continue returning no operations/actions. Connect,
invoke, and stop messages should describe the official probe backend, not a
metadata-only backend.

## Acceptance

- Unit tests prove `OfficialFabricDriverBackend` uses an injected live
  `FabricClientStateGraphSnapshot` for client-state resources and handles.
- Unit tests prove the official backend still exposes no operations/actions.
- Architecture guard proves the official backend no longer calls
  `FabricClientStateGraphSnapshot.disconnected()` directly.
- Architecture guard proves official production code still does not import the
  Yarn/remap `driver-fabric` module or `RuntimeCapabilityGraph`.
- Focused official/discovery/Fabric tests pass.
- Real enabled official attach probe observes `client.attached`.
- Official OpenAPI still reports `actions=0`, while client-state availability
  is derived from the official live snapshot instead of a hard-coded
  disconnected fragment.
- No packaged 26.x manifest entry, public gameplay action, static gameplay
  catalog, scenario shortcut, or latest/current support claim is added.
