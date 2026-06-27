# Projection And OpenAPI Design

**Goal:** Generate per-client OpenAPI, action/resource projections, aliases, schemas, and fingerprints from the runtime capability graph.

**Architecture:** `protocol/` defines graph-to-OpenAPI projection. `daemon/` asks the active driver for a graph snapshot, projects it, and serves `/clients/{id}/openapi.json`, `/clients/{id}/actions`, and `/clients/{id}/resources`. Descriptor projections remain convenience views of the generated OpenAPI.

**Projection Rules:**
- Graph resources become `x-craftless-resources`.
- Graph operations become `x-craftless-actions` and generated alias routes.
- Graph event sources become stream metadata in the per-client OpenAPI.
- Graph schemas become JSON/OpenAPI schemas for arguments, results,
  resources, handles, and event payloads. Object and array schemas must
  preserve nested properties/items rather than collapsing to type-only
  placeholders.
- Runtime fingerprints include graph node ids, schemas, availability, source versions, and runtime metadata.

**Public API Rules:**
- No raw Fabric/Yarn/intermediary/Minecraft implementation names.
- Generated aliases are derived from operation ids and resource ids.
- The supervisor `/openapi.json` stays stable and lifecycle-focused.
- Per-client OpenAPI is the gameplay authority.

**Completion Gate:**
- Protocol/daemon tests prove OpenAPI generation from graph snapshots.
- `/clients/{id}/actions` and `/clients/{id}/resources` are derived from the same graph-projected OpenAPI.
- ETag and runtime fingerprint change when graph shape, schema, or availability changes.
