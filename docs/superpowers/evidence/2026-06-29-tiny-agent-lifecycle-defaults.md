# Tiny-Agent Lifecycle Defaults Evidence

Phase 188 adds lifecycle-only defaults for small agents:

- `CreateClientRequest.profile` may be omitted.
- The daemon derives a safe offline profile from the client id.
- Client requests and responses expose Craftless-owned presentation intent.
- API-first clients default to `window = NONE` and `audio = MUTED`.
- Daemon-managed muted clients materialize Minecraft sound options at `0.0`.
- Generated OpenAPI advertises presentation enum values and defaults.
- The CLI exposes only concrete lifecycle flags: `--visible` and
  `--audio muted|default`.

This slice does not add gameplay roles, static gameplay catalogs, scenario
shortcuts, or alternate gameplay authority. Generated per-client OpenAPI
remains the gameplay contract.

## Verification

Passed from the current worktree on 2026-06-29:

```sh
mise exec -- gradle :protocol:test --tests com.minekube.craftless.protocol.ClientModelsTest --tests com.minekube.craftless.protocol.OpenApiGenerationTest
```

Result: `BUILD SUCCESSFUL in 2s`.

```sh
mise exec -- gradle :daemon:test --tests com.minekube.craftless.daemon.ClientSessionServiceTest --tests com.minekube.craftless.daemon.LocalSessionApiServerTest
```

Result: `BUILD SUCCESSFUL in 10s`.

```sh
mise exec -- gradle :cli:test --tests com.minekube.craftless.cli.CraftlessCliTest
```

Result: `BUILD SUCCESSFUL in 7s`.

```sh
git diff --check
```

Result: passed with no output.

```sh
mise run ci
```

Result: passed after rerunning lint, unused-check/detekt, full Gradle tests,
packaged Craftless CLI smoke, and Playwright/Bun tests. An initial CI attempt
failed on ktlint import ordering in `ClientSessionServiceTest.kt`; the import
order was corrected and the full command passed on rerun.

## Guard Checks

Guard query: searched production/test/docs paths for stale client-role
surface, static participant semantics, stale final-completion wording, and the
temporary Phase 188 in-progress status.

Result before checklist update: only the checklist's temporary Phase 188
status remained. No role contract or stale final completion wording remained.

Independent sub-agent review found two README status issues, under-specified
presentation enum metadata, and the risk that muted audio looked like metadata
only. Follow-up fixes updated README status/roadmap wording, added OpenAPI
enum/default schema metadata, and materialized muted Minecraft sound options
in the daemon launch path.
