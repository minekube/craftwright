# Phase 41: Launch Argument Placeholders Design

## Problem

Prepared Minecraft launch argument files can contain Mojang-style placeholders
that are not resolved during cache preparation because they depend on the
client instance and profile being launched. Examples include
`{{auth_player_name}}`, `{{auth_uuid}}`, `{{auth_access_token}}`,
`{{user_type}}`, launcher metadata, asset/version metadata, optional account
ids, optional resolution values, and quick-play placeholders.

Craftless already resolves cache-time placeholders such as classpath and
native directory while writing the prepared launch arguments, and it resolved
`{{gameRoot}}` at process launch. Leaving profile and quick-play placeholders
unresolved makes the process command invalid for real Minecraft client
launches.

## Design

Keep launch-time placeholder expansion in the supervisor/client-runtime layer.
This is process launch metadata, not a gameplay action or generated per-client
API.

The process launcher should:

- resolve `{{gameRoot}}` from the selected instance files;
- resolve offline profile values from `CreateClientRequest.profile`;
- use the standard Minecraft offline UUID convention for offline profiles;
- provide `0` as the offline access token;
- provide `legacy` as the offline user type;
- resolve launcher, version type, and asset index metadata from Craftless and
  the selected Minecraft version;
- resolve `{{quickPlayPath}}` to an instance-local quick-play path;
- omit optional quick-play, account-id, and resolution flags when their
  resolved placeholder value is empty;
- leave gameplay API, generated OpenAPI, Fabric descriptors, and CLI gameplay
  catalogs unchanged.

## Acceptance

- Focused daemon launcher test proves the prepared command contains resolved
  username, UUID, access token, user type, asset index, version type, launcher
  metadata, game root, and quick-play path.
- The same test proves unset quick-play mode, account-id, and resolution flags
  are omitted rather than passed with empty or unresolved placeholder values.
- Existing daemon tests continue to pass.
- No public gameplay action ids, static route families, or scenario shortcuts
  are added.
