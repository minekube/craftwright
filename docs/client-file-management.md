# Client File Management

Craftless owns its instance layout and public file metadata. Prism Launcher is
useful design input for Minecraft client file management, but it is not a core
runtime dependency and its internal file names are not public Craftless API.

## Current Contract

Craftless protocol responses expose a small, stable `InstanceFiles` object:

- `root`
- `gameRoot`
- `mods`
- `config`
- `saves`
- `resourcePacks`
- `shaderPacks`

The default layout is Craftless-owned:

```text
instances/{instanceId}/
  minecraft/
    mods/
    config/
    saves/
    resourcepacks/
    shaderpacks/
```

This is intentionally narrower than a launcher UI. It gives agents and generated
clients the file handles they need without coupling the daemon, CLI, or OpenAPI
surface to a desktop launcher's storage model.

## Prism Source Findings

Prism Launcher checkout inspected:

- Path: `/tmp/prismlauncher-source`
- Commit: `9c2c6415310a0f36f9a9c48f3ee4901ba20bb139`

Relevant source areas:

- `launcher/BaseInstance.h` distinguishes an instance root from a game root and
  requires concrete instance types to expose a mods directory.
- `launcher/minecraft/MinecraftInstance.cpp` resolves the game root under the
  instance, then derives mods, config, saves, resource packs, shader packs,
  data packs, local libraries, natives, and resources from that boundary.
- `launcher/InstanceCopyTask.cpp` treats instance copying/linking as a file
  operation with filters and special handling for saves when links are used.
- `launcher/InstanceImportTask.cpp` detects several pack/archive formats and
  delegates conversion into an instance rather than making one archive format
  the core instance model.
- `launcher/ui/dialogs/ExportInstanceDialog.cpp` filters transient game files
  and blocked paths during export.
- `launcher/minecraft/AssetsUtils.cpp` reads asset indexes and reconstructs
  virtual/resource asset locations from the asset index metadata.

## Craftless Decisions

- Keep `InstanceFiles` launcher-neutral and Craftless-owned.
- Keep `gameRoot` separate from `root`; most Minecraft mutable folders live
  under `gameRoot`.
- Keep saves explicit because copy/link/import workflows often need different
  treatment for worlds than for cache or generated runtime data.
- Add future file handles only when needed by automation or generated OpenAPI
  clients, such as logs, screenshots, resource cache, data packs, local
  libraries, natives, and asset/cache roots.
- Treat pack/import adapters as optional translation layers. They may read
  launcher or modpack archive formats internally, but they must output
  Craftless-owned instance metadata.
- Do not require Prism Launcher to launch, attach to, or automate a Minecraft
  Java client.
- Do not expose Prism, MultiMC, pack manifest, or launcher-internal file names in
  protocol DTOs, OpenAPI extensions, CLI commands, or README active-product
  surfaces.

## Verification

Use these checks when changing file-management contracts:

```sh
mise exec -- gradle :protocol:test --tests com.minekube.craftless.protocol.ClientModelsTest --tests com.minekube.craftless.protocol.NamespacePolicyTest
rg -n "Prism|PrismLauncher|MultiMC|MMC|instance\\.cfg|mmc-pack|patches/|ManagedPack" protocol/src/main daemon/src/main cli/src/main driver-api/src/main driver-runtime/src/main driver-fabric/src/main bridge-hmc/src/main testkit/src/main --glob '!**/build/**' --glob '!driver-fabric/run/**'
```
