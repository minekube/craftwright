# Java Runtime Resolution Design

## Intent

Make Java runtime selection an explicit Craftless supervisor/runtime concern so
Minecraft versions can launch with the Java major they actually require.

The compatibility probe for Minecraft `26.2` showed the current gap clearly:

- the repository build toolchain is pinned to Java 21 through `mise`;
- a plain Minecraft `26.2` server requires Java 25 classfile support;
- `testkit` can be forced to use Java 25 with `CRAFTLESS_SMOKE_JAVA_EXECUTABLE`,
  but that is an operator workaround, not a product contract;
- the cache preparer already downloads Mojang Java runtime files, but it does
  not expose a selected Java runtime descriptor or make launches consume that
  descriptor consistently;
- the Fabric client smoke still launches the compiled `1.21.6` lane even when
  the server/cache request uses `26.2`.

Craftless needs Prism-style multi-Java behavior, adapted for an API-first
daemon instead of a GUI launcher.

## Prism Input

The Prism Launcher source in `/tmp/PrismLauncher` shows the useful shape:

- scan multiple Java locations instead of assuming one process JVM;
- validate candidates by launching a Java checker;
- store selected Java per instance;
- derive compatible Java majors and runtime names from Minecraft metadata;
- auto-select a compatible local Java or download a managed runtime;
- support externally supplied runtime paths through environment/configuration.

Craftless should use the same principles, not Prism's UI model or public names.
The public supervisor API must expose Craftless-owned runtime descriptors,
selection evidence, and launch plans.

## Product Rules

- `mise` remains the repository tool manager for build, tests, Bun, Gradle, and
  local developer workflows.
- `mise` must not be the only product runtime provider. Docker, GitHub Actions,
  CI workers, and API users should be able to run Craftless without having mise
  installed.
- A `mise` Java installation may be discovered as one Java provider.
- A managed Craftless/Mojang Java runtime cache is the preferred product
  fallback when no compatible configured runtime exists.
- The selected Java runtime must be visible in supervisor metadata and cache
  manifests with Craftless-owned fields.
- The live per-client generated API must continue to describe Minecraft
  runtime affordances. Java runtime selection belongs to the supervisor/client
  management layer, not to gameplay action descriptors.
- Java provider names and file paths are operational metadata. They must not
  become gameplay API names, route families, action ids, or CLI command
  catalogs.

## Architecture

Add a supervisor-side Java runtime resolver with a small stable model:

- `JavaRuntimeRequirement`: requested Java major, optional component name,
  architecture, operating-system family, source policy, and reason.
- `JavaRuntimeDescriptor`: id, provider, Java home, executable handle/path,
  major version, full version string, vendor, architecture, source, managed
  flag, and validation evidence.
- `JavaRuntimeSelection`: requirement, selected descriptor, considered
  candidates, rejected candidates with machine-readable reasons, and selected
  reason.
- `JavaRuntimeProvider`: internal provider interface for configured, managed,
  mise, and system runtimes.

Provider order should be deterministic:

1. explicit request/configured path;
2. prepared Craftless-managed runtime in the workspace cache;
3. discovered mise Java installations;
4. system discoveries such as `JAVA_HOME`, `PATH`, macOS JVM dirs, SDKMAN,
   asdf, Gradle toolchains, and common Linux/Windows locations;
5. managed download when a prepare/install job allows network mutation.

Providers may produce candidates; only the resolver chooses. Candidate
validation must execute the Java binary with a bounded timeout and parse
version/vendor/architecture evidence. Path existence alone is not enough.

## Minecraft Version Input

For Minecraft version metadata:

- read `javaVersion.majorVersion` when present;
- read `javaVersion.component` when present for managed Mojang runtime lookup;
- record the Minecraft version manifest URL and hash as private selection
  evidence;
- use classfile-major probing of downloaded server/client jars only as a
  fallback or diagnostic check when metadata is absent or contradictory.

For Minecraft `26.2`, the resolver must select Java 25 or fail with an explicit
`java-runtime.unsatisfied` result. It must not silently use the repository's
Java 21 build runtime.

## API And CLI Surface

The supervisor API may add stable lifecycle/resource endpoints such as:

- `GET /runtimes/java`;
- `POST /runtimes/java:resolve`;
- Java selection fields in `POST /cache:prepare`, `POST /clients`, and prepared
  cache manifests.

The CLI may add supervisor/runtime commands such as:

- `craftless runtimes java list`;
- `craftless runtimes java resolve --mc <version>`;
- `craftless cache prepare --mc <version> --loader fabric --java auto`.

These are client-management/runtime commands. They are allowed because they do
not enumerate Minecraft gameplay actions.

## Cache And Jobs

The current synchronous cache preparation is too brittle for large version
sets. Java runtime resolution should be implemented with resumable cache job
semantics:

- each artifact has an idempotent target handle;
- existing files with matching size/hash are reused;
- failed downloads are recorded per artifact;
- retryable failures use bounded retry/backoff;
- job status reports progress and the selected Java runtime;
- the final manifest includes `javaSelection` and a launch plan that points at
  the selected executable handle/path.

The first implementation may keep the existing synchronous route if tests
cover idempotence and retry behavior, but the model must not block future async
job progress endpoints.

## Docker And CI

The runtime Docker image should not build Craftless. It should contain the
Craftless CLI distribution and enough OS libraries to run Minecraft clients.
Java selection inside Docker should use the same resolver:

- if the image includes a base Java, it is just one candidate;
- missing compatible versions are prepared into the workspace/runtime cache;
- CI can use `mise` to install extra Java majors, but release users are not
  forced to install mise.

## Testing And Evidence

Completion evidence for this phase requires:

- unit tests for requirement derivation from Minecraft metadata;
- provider tests for explicit, managed-cache, mise-discovered, and rejected
  candidates;
- resolver tests proving Java 25 is selected for a `26.2` manifest and Java 21
  is not accepted for that requirement;
- cache manifest tests showing selected Java runtime evidence and launch plan
  integration;
- testkit smoke configuration consumes resolver output instead of relying only
  on `CRAFTLESS_SMOKE_JAVA_EXECUTABLE`;
- documentation/checklist evidence ties the 26.2 compatibility blocker to this
  resolver.

Live Minecraft smoke remains evidence, not the product truth by itself. This
phase is complete when version-aware Java selection is a reusable supervisor
system, not a one-off environment variable workaround.

## Non-Goals

- Do not implement a new gameplay action.
- Do not expose Java runtime selection in the live per-client gameplay API.
- Do not require mise for all end-user runtime launches.
- Do not claim Minecraft `26.2` Fabric client support until a compatible Fabric
  driver lane is selected and verified separately.
- Do not mark Craftless complete.

## Completion Gate

Phase 27 is complete only when:

- Java runtime requirement derivation is implemented from Minecraft metadata;
- the resolver validates and selects explicit, managed, mise, and system
  candidates through one internal interface;
- cache/client launch plans consume the selected runtime;
- `26.2` requires Java 25 in tests and smoke evidence;
- resolver output is visible through Craftless-owned supervisor metadata;
- failed selection is machine-readable and does not fall back silently;
- docs, AGENTS, and the project checklist are updated with evidence.
