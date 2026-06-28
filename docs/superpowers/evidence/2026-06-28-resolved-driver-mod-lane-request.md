# Resolved Driver Mod Lane Request Evidence

Phase: 112

## Red

Command:

```sh
mise exec -- gradle :daemon:test --tests '*LocalSessionApiServerTest.prepared runtime asks driver mod provider for resolved runtime lane*'
```

Result:

- Exit code: 1
- `LocalSessionApiServerTest.prepared runtime asks driver mod provider for resolved runtime lane` failed.
- Failure reason: `ClientRuntimeDriverModProvider` still received the
  unresolved client request version `latest-release`.

## Green

Command:

```sh
mise exec -- gradle :daemon:test --tests '*LocalSessionApiServerTest.prepared runtime asks driver mod provider for *runtime lane*'
```

Result:

- Exit code: 0
- The exact-version provider request test passed.
- The alias provider request test passed and recorded concrete Minecraft
  version `1.21.6` with Fabric Loader `0.17.2`.

## Local Gates

Initial full CI command:

```sh
mise run ci
```

Result:

- Exit code: 1
- Failure: `:daemon:ktlintTestSourceSetCheck`.
- Root cause: one new assertion line in
  `LocalSessionApiServerTest.kt` chained through `launcher.launches.single().prepared.manifest`
  without ktlint-required newlines before `.`.

Focused fix verification:

```sh
mise exec -- gradle :daemon:ktlintTestSourceSetCheck
mise exec -- gradle :daemon:test --tests '*LocalSessionApiServerTest.prepared runtime asks driver mod provider for *runtime lane*'
```

Results:

- `:daemon:ktlintTestSourceSetCheck`: exit code 0.
- Focused exact and alias provider request tests: exit code 0.

Final local gates:

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

## Scope Guard

This phase only changes the prepared-runtime driver-mod request to use the
concrete prepared runtime identity.

It adds no compiled Fabric lane, public gameplay action, generated route
family, CLI gameplay catalog, Fabric gameplay binding, scenario shortcut,
public version-specific API, runnable latest/older lane, or new Minecraft
support claim.
