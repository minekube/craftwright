# Session 019f121c Craftless Usability Evidence

## Session Source

Codex session `019f121c-6725-7aa3-85b2-c7bbe08ccd40` used Craftless as an
external real-client probe during a Minekube Connect production incident.

Relevant Craftless friction observed in the session:

- The agent used `craftless api /clients` to create a real client for a join
  smoke.
- It had to discover manually that `/clients` has both `GET` and `POST`
  operations, because `craftless api /clients --help` defaulted to the `GET`
  operation unless `--method POST` was supplied.
- It started a daemon that answered HTTP health checks but could not launch
  clients because no workspace was configured, then had to restart with an
  explicit `--workspace`.
- Active docs still showed removed cache shortcut commands even though the CLI
  had moved to the generic `craftless api <endpoint>` invoker.
- After a connect request, the real client could remain `RUNNING` with
  resources still unavailable as `client-not-connected`; callers had to infer
  from logs that Minecraft had attempted to connect but Craftless had not
  observed a connected state.
- Later Craftless behavior already addressed the other major ambiguity from the
  same session: field-bearing `craftless api /clients -F ...` infers `POST`,
  `POST /clients` descriptions explicitly warn that it launches a new real
  Minecraft process, and `presentation.window = NONE` now fails early with an
  actionable windowless-wrapper message when no real windowless strategy is
  available.

## Fix

`craftless api <endpoint> --help` now lists every matching OpenAPI operation
when the caller did not explicitly provide `--method` and did not provide body
fields or `--input`.

For `/clients`, help now includes both:

- `Route: GET /clients`, the list operation agents should use before creating
  another client.
- `Route: POST /clients`, the create operation with OpenAPI-derived request
  fields, enum values, defaults, and lifecycle description.

Actual invocation semantics are unchanged: `-F`, `--field`, `-f`,
`--raw-field`, or `--input` still infer `POST`, while body-less invocation still
defaults to `GET`.

`POST /clients/{id}:connect` now also records
`client.connect.unobserved` when the driver accepts a connect request but the
client state remains non-connected. The response still reports the truthful
client state, but `/clients/{id}/events` and `/clients/{id}/events:stream`
give agents a machine-readable breadcrumb instead of making them infer the
failed observation from external process logs.

`craftless daemon start` now always starts with an effective workspace. The
precedence is `--workspace`, then `CRAFTLESS_WORKSPACE`, then
`~/.craftless/workspace`. Startup metadata reports that effective workspace, so
agents no longer get a healthy daemon that fails later with `cache workspace is
not configured` when they try to create a real client.

The README and file-management docs now show cache preparation through
`craftless api /cache:prepare` instead of the removed `craftless cache prepare`
shortcut.

## Verification

```sh
mise exec -- gradle :cli:test --tests 'com.minekube.craftless.cli.CraftlessCliTest.api help shows every matching method when route is ambiguous'
```

Result: passed. The test first failed because only `GET /clients` appeared in
ambiguous route help, then passed after multi-operation help was implemented.

```sh
mise exec -- gradle :cli:test
```

Result: passed.

```sh
mise exec -- gradle :daemon:test --tests 'com.minekube.craftless.daemon.LocalSessionApiServerTest.server does not emit connected event for unobserved connect'
```

Result: passed. The test first failed because unobserved connects emitted no
diagnostic event, then passed after adding `client.connect.unobserved`.

```sh
mise exec -- gradle :daemon:test
```

Result: passed.

```sh
mise exec -- gradle :cli:test --tests 'com.minekube.craftless.cli.CraftlessCliTest.daemon start uses environment workspace when workspace flag is omitted'
```

Result: passed. The test first failed because `daemon start --once` reported no
workspace when the flag was omitted, then passed after adding the default
workspace resolution.
