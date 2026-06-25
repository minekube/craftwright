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

The current repository, package names, OpenAPI metadata, and JVM coordinates
still use `craftwright` / `com.minekube.craftwright` until a deliberate rename
is implemented. Documentation should make that distinction explicit when it
matters.

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

`Craftwright` works well as a Playwright-like framework name. It has a polished
developer-tool feel, but `wright` is not immediately understood by everyone and
it over-emphasizes the framework/client-library layer.

`Craftless` works better for the broader platform. It is shorter, closer to the
Browserless-style infrastructure category, and can mean less manual Minecraft
client management or less manual crafting because agents/tools automate the
client for you.

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
- One exact public repo, `Craftless/Craftless`, had 0 stars.

This was a quick GitHub availability check, not a trademark or domain search.

## Naming Architecture

Preferred target naming if the rename proceeds:

- Product/repository: `Craftless`
- Protocol: `Craftless Client Protocol`
- Runtime: `Craftless Runtime`
- CLI: `craftless`
- Generated clients: roadmap, derived from OpenAPI/action descriptors rather
  than hand-written as the source of truth
- Durable client driver: Craftless Fabric driver

Implementation package names may remain `com.minekube.craftwright` until a
separate package-level migration is explicitly planned.
