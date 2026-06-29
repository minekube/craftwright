# Client Lifecycle Surface Clarity Evidence

## Debug Finding

The requested Codex session id was a prefix of local session file:

`/Users/robin/.codex/sessions/2026/06/29/rollout-2026-06-29T08-41-19-019f121c-6725-7aa3-85b2-c7bbe08ccd40.jsonl`

The transcript showed repeated timestamped client creation against the same
daemon and workspace. Examples included `codexhub...`, `codexdirect...`,
`codexpub...`, `codexcopyfix...`, `codexprerun...`, `codexdbg...`,
`codexpost...`, and `codexnew...`.

Fresh local API state during diagnosis showed ten daemon-managed Craftless
clients on `http://127.0.0.1:58080`, all created from that retry pattern. The
root misunderstanding was that `clients create` looked like setup/retry/select,
while the product semantics are "launch a new real client process."

## Fix

- `ApiRoute` now carries optional `summary` and `description` metadata.
- `OpenApiDocument` emits those fields for route operations.
- Generated CLI help prints route summary and description.
- Supervisor route metadata now explains that `POST /clients` launches a new
  daemon-managed real Minecraft Java client process, is not selector/retry/reuse,
  and should be preceded by list/get plus stop of abandoned attempts.
- README, the agent operating contract, and the public gameplay-agent skill now
  carry the same lifecycle discipline.

## Focused Red Evidence

Initial focused tests failed before the fix:

```sh
mise exec -- gradle :protocol:test --tests com.minekube.craftless.protocol.OpenApiGenerationTest.stable\ supervisor\ openapi\ makes\ client\ creation\ lifecycle\ explicit
```

Result: failed at test compilation because `OpenApiOperation.description` did
not exist.

```sh
mise exec -- gradle :cli:test --tests com.minekube.craftless.cli.CraftlessCliTest.generated\ supervisor\ route\ help\ is\ loaded\ from\ supervisor\ openapi
```

Result: failed because generated CLI help did not contain the lifecycle warning.

```sh
mise exec -- bun test playwright/src/distribution.test.ts --test-name-pattern "public docs make client creation lifecycle explicit"
```

Result: failed because README and the gameplay-agent skill did not contain the
process-launch warning.

## Focused Green Evidence

After implementation:

```sh
mise exec -- gradle :protocol:test --tests com.minekube.craftless.protocol.OpenApiGenerationTest.stable\ supervisor\ openapi\ makes\ client\ creation\ lifecycle\ explicit
```

Result: passed.

```sh
mise exec -- gradle :cli:test --tests com.minekube.craftless.cli.CraftlessCliTest.generated\ supervisor\ route\ help\ is\ loaded\ from\ supervisor\ openapi
```

Result: passed.

```sh
mise exec -- bun test playwright/src/distribution.test.ts --test-name-pattern "public docs make client creation lifecycle explicit"
```

Result: passed.

## Final Verification

Final broad verification from the completed worktree:

```sh
mise exec -- gradle :protocol:test :cli:test
```

Result: passed. Gradle reported `BUILD SUCCESSFUL in 11s`.

```sh
mise exec -- bun test playwright/src/distribution.test.ts
```

Result: passed. Bun reported `18 pass`, `0 fail`, and `204 expect() calls`.

```sh
git diff --check
```

Result: passed with no output.
