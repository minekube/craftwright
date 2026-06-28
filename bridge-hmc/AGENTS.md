# HMC Bridge Module Instructions

`bridge-hmc/` is temporary evidence infrastructure for launching and controlling
real clients before the Fabric driver is complete.

## Scope

- HeadlessMC/HMC-Specifics launch and bridge experiments.
- Internal command mapping.
- Opt-in real-client smoke planning and evidence capture.

## Rules

- Never expose HeadlessMC or HMC-Specifics command strings as public API names,
  JSON fields, CLI verbs, SDK methods, or docs contracts.
- Label bridge behavior as bridge-only evidence. Do not describe it as robust
  movement, perception, inventory, or final automation.
- Do not use bridge behavior to justify product API shape, version support, or
  gameplay completion. If a bridge smoke discovers a useful primitive, move the
  product work into the Fabric runtime graph, generated OpenAPI, and generic
  invocation path.
- Do not add bridge-specific version compatibility fallbacks. Multi-version
  support belongs in resolver/cache/driver-manifest/Fabric lane plumbing.
- Keep real-client smoke tests opt-in and guarded by environment variables.
- Default tests must not download Minecraft/server artifacts or launch a real
  client.

## Verification

```sh
mise exec -- gradle :bridge-hmc:test
```
