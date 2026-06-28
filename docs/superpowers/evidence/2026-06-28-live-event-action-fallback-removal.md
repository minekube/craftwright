# Live Event Action Fallback Removal Evidence

Phase: 117

## Red

Command:

```sh
mise exec -- gradle :protocol:test --tests '*NamespacePolicyTest.daemon live event normalization does not synthesize gameplay action ids*'
```

Result:

- Exit code: 1
- Failure reason: `LocalSessionApiServer` still contained static fallback
  mappings from raw event types to `player.chat` and `player.move`.

## Green

Commands:

```sh
mise exec -- gradle :protocol:test --tests '*NamespacePolicyTest.daemon live event normalization does not synthesize gameplay action ids*'
mise exec -- gradle :daemon:test --tests '*LocalSessionApiServerTest.server streams filtered live client events as sse*' --tests '*LocalSessionApiServerTest.server invokes actions through json rpc with correlation ids*' --tests '*LocalSessionApiServerTest.server persists json rpc subscriptions as sse filters*'
```

Results:

- Protocol source guard: exit code 0.
- Daemon SSE/JSON-RPC focused regressions: exit code 0.

## Local Gates

Commands:

```sh
git diff --check
mise run ci
```

Results:

- `git diff --check`: exit code 0.
- `mise run ci`: exit code 0.
- `mise run ci` completed:
  - `mise exec -- gradle lint`
  - `mise run unused-check`
  - `mise exec -- gradle test`
  - `mise exec -- bun test playwright`
- Bun reported 18 passing tests across the Playwright helper suite.

## Scope Guard

This phase only removes static event type fallback mapping. It does not add a
public gameplay action, generated route family, CLI gameplay catalog, Fabric
gameplay binding, scenario shortcut, public version-specific API, runnable
latest/older lane, or new Minecraft support claim.
