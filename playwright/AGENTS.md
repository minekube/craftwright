# Playwright Helper Instructions

`playwright/` contains external helper tests and fixtures.

## Scope

- Playwright/Vitest helper tests.
- Thin protocol consumers for external integration checks.

## Rules

- Use Bun through mise: `mise exec -- bun ...`.
- Do not use npm, npx, yarn, pnpm, or globally installed node.
- Do not reintroduce a TypeScript SDK as an active product surface unless the
  project direction changes explicitly.
- Helpers should speak the daemon/OpenAPI/action API directly. Do not parse
  human CLI output.
- Helper tests must stay adaptive to live OpenAPI/actions. Do not add
  TypeScript-side static gameplay catalogs, Minecraft-version command tables,
  or scenario macros.

## Verification

```sh
mise exec -- bun test playwright
```
