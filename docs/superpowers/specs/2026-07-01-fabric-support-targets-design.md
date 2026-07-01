# Fabric Support Targets Design

## Goal

Craftless needs an honest answer for Fabric version support. For every
Minecraft target discovered from Fabric metadata, agents should be able to see
whether the current Craftless daemon has a compatible packaged driver lane or
whether the target is unsupported.

## Design

Add `GET /versions/support-targets` to the stable supervisor API. The route
composes:

- Fabric game target metadata from `/versions/game`;
- the configured Craftless driver mod manifest from `/versions/driver-mods`.

Each response target includes:

- `minecraftVersion`;
- Fabric stability flag;
- `loader = FABRIC`;
- `supported`;
- `driverMods`, containing all exact driver lanes for that Minecraft version;
- `reason = NO_DRIVER_MOD` when no lane exists.

This is deliberately a compatibility reporting surface. It does not add static
gameplay APIs, copied per-version routes, or a claim that unsupported targets
can launch. Existing client creation continues to fail when no manifest lane
can satisfy the requested runtime identity.

## Follow-Up

The broader goal still needs deeper coverage:

- optionally expand the matrix to per-loader-version combinations by fetching
  compatible loader metadata for each target;
- add packaged probes for new supported lanes;
- add scheduled drift checks that fail when Fabric metadata introduces a new
  stable target without a support verdict.
