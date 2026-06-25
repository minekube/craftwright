# Product Positioning And Naming Notes

This note captures the side-session reasoning behind the current public
positioning direction. It is not a full rename plan.

## Current Decision

Use **Craftless** as the target product name.

Short description:

```text
Automation infrastructure for real Minecraft Java clients, headless or visible,
with generated local APIs.
```

The repository, package names, OpenAPI metadata, JVM coordinates, and Fabric
metadata use `craftless` / `com.minekube.craftless`.

Public web/domain references use `minekube.com`.

## Why This Product Exists

Minecraft already provides the real client runtime. Craftless should stay thin:

- launch or attach to a real Minecraft Java client;
- load a small client-side driver, primarily the durable Fabric driver;
- expose local generated APIs for the running client;
- describe each client's live action surface through per-client OpenAPI;
- manage lifecycle, sessions, events, files, logs, profiles, and CI/Docker
  runtime concerns;
- let agents, tools, tests, CI, and generated clients use that API without depending on
  Minecraft internals, loader internals, mappings, or bridge commands.

The project should avoid becoming a large hand-coded SDK with one static method
or route per possible Minecraft action. The generated API and action
descriptors are the product contract.

## API Layer Shape

Craftless keeps public API layers separate:

- Stable supervisor API: `/openapi.json`, client lifecycle, events, setup,
  files, and per-client spec discovery.
- Live per-client API: `/clients/{id}/openapi.json`, generated from one
  running client and owning gameplay actions, aliases, schemas, availability,
  and runtime fingerprints.
- Descriptor projections: `/clients/{id}/actions` is a convenience view of the
  per-client action metadata, not another source of truth.
- Adaptive consumers: the `craftless` CLI, agents, and future generated
  clients fetch the live specs/descriptors at runtime instead of mirroring
  Minecraft actions in static source code.
- Internal driver boundary: `DriverSession` stays lifecycle-oriented and
  invokes discovered actions generically through `actions()` and `invoke(...)`.
- Fabric internals: discovery/projection and execution bindings stay inside
  the driver; raw Minecraft, loader, mapping, mod, and bridge names are inputs,
  not public contracts.

## Browserless And CDP Analogy

The closest browser-world analogy is Browserless plus the Chrome DevTools
Protocol ecosystem:

- Chrome already exists; CDP gives tools a machine-readable control protocol.
- Browserless runs real browser infrastructure and exposes automation access.
- Playwright/Puppeteer provide ergonomic client libraries above lower-level browser
  automation primitives.

Craftless maps to Minecraft this way:

- Minecraft Java client is the runtime that already exists.
- Craftless Client Protocol is the generated local OpenAPI/action surface.
- Craftless runtime is the daemon, CLI, Docker image, sessions, files, logs,
  events, and driver wiring.
- Future generated clients can provide a lightweight Playwright-style developer
  experience above the generated API without becoming the source of truth.

This means the product name should sound like runtime/control infrastructure,
not only like a high-level client library.

## Name Comparison

`Craftless` works for the broader platform. It is short, close to the
Browserless-style infrastructure category, and can mean less manual Minecraft
client management because agents/tools automate the client for you.

Known downside: `Craftless` can be read as "without crafting" or "not crafty."
The tagline should carry the exact meaning:

```text
Craftless is automation infrastructure for real Minecraft Java clients,
headless or visible.
```

## GitHub Availability Snapshot

Checked during the side session:

- `minekube/craftless` was available.
- GitHub search had 23 global name hits for `craftless`.
- One exact public repo, `Craftless/craftless`, had 0 stars.

This was a quick GitHub availability check, not a trademark or domain search.

## Naming Architecture

Current naming architecture:

- Product/repository: `Craftless`
- Public domain: `minekube.com`
- Protocol: `Craftless Client Protocol`
- Runtime: `Craftless Runtime`
- CLI: `craftless`
- Generated clients: roadmap, derived from OpenAPI/action descriptors rather
  than hand-written as the source of truth
- Durable client driver: Craftless Fabric driver

Implementation package names use `com.minekube.craftless`.
