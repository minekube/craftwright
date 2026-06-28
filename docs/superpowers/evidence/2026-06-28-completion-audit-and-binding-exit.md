# Completion Audit And Binding Exit Evidence

Date: 2026-06-28

## Intent

Audit the current project state against the active Craftless goal after Phase
75 refreshed distribution, compatibility, CI, and final public gameplay
evidence.

This audit records the current completion decision: Craftless has current
Codex-verifiable evidence for several important gates, but the overall goal
must remain active because the Fabric gameplay surface still depends on a
small transitional hand-written binding allowlist.

## Verified Gates

- CLI packaging and packaged CLI smoke were refreshed in Phase 75.
- Docker runtime image build and smoke were refreshed in Phase 75.
- Install script smoke for published `v0.1.0` was refreshed in Phase 75.
- Repository tooling uses `mise`; Bun usage is through `mise exec -- bun`.
- JVM HTTP/client/SSE code is governed by the Ktor-only rule in `AGENTS.md`.
- JSON-RPC-style invocation, generated projection queries, and SSE event
  streaming are documented and covered by Phase 6 and later evidence.
- Latest release `26.2` and representative older release `1.20.6` have current
  compatibility evidence. Both are explicit unsupported lanes today, not
  supported Fabric runtime lanes.
- Final gameplay evidence was refreshed in Phase 75 through public
  OpenAPI/actions/resources/SSE. The no-hold run fetched generated per-client
  OpenAPI/actions/resources, subscribed to SSE, collected materials, crafted
  and equipped a `Wooden Sword`, found Cows through generated `entity.query`,
  killed a Cow through generated `entity.attack`, and observed `Raw Beef`,
  `Leather`, and a Cow with `alive:false` through generated public state.
- The active completion gate no longer requires Robin's Minecraft chat
  confirmation. Human co-play remains optional diagnostic evidence.

## Open Gates

- The public gameplay action surface still relies on
  `driver-fabric/src/main/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/FabricActionBindings.kt`
  for transitional bootstrap descriptors and execution bindings.
- `docs/architecture/transitional-fabric-action-allowlist.txt` currently
  lists:

  ```text
  inventory.equip
  inventory.query
  player.chat
  player.look
  player.move
  player.query
  player.raycast
  screen.close
  screen.query
  world.block.break
  world.block.interact
  world.time.query
  ```

- `FabricDriverModuleTest.hand written fabric gameplay descriptors stay
  transitional and graph represented` enforces that the descriptors match this
  allowlist and are represented as runtime graph operations. That guard is
  correct for bootstrap evidence, but it also proves the durable generated
  discovery exit is not finished.
- Latest `26.2` and older `1.20.6` lanes are currently detected and reported
  as unsupported. That is honest compatibility evidence, but not the requested
  broad runtime support.
- The next completion work must move public gameplay breadth from the
  transitional binding list into generic runtime discovery/projection and
  private executable adapters. Adding more action IDs to the allowlist is not
  completion progress.

## Completion Decision

Do not mark the active goal complete yet.

The next implementation phase should be a binding-exit phase that replaces the
transitional public descriptor source with generic runtime-discovered graph
nodes and keeps execution behind private adapters. Completion should require a
rerun of the distribution, compatibility, CI, and final public gameplay gates
after that exit work lands.

## Verification Evidence

- `git diff --check`: passed locally.
- `mise run architecture-check`: passed locally.
- `mise run ci`: passed locally.
- Implementation commit `348672e` passed GitHub Actions `ci` run
  `28308547679`.
