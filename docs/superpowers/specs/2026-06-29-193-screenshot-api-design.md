# Screenshot API Design

## Goal

Expose screenshot capture as a generated live per-client media capability and
serve the produced bytes through generic client artifact infrastructure.

## Product Shape

- Runtime resource: `media.screenshot`
- Runtime operation: `media.screenshot.capture`
- Generated alias: `POST /clients/{id}/media/screenshot:capture`
- Generic fallback: `POST /clients/{id}:run` with
  `action=media.screenshot.capture`
- Artifact download: `GET /clients/{id}/artifacts/{artifact-id}`

`media.screenshot.capture` is not a stable handwritten gameplay route family.
It is a runtime graph operation whose alias route is generated from the
per-client OpenAPI document.

## Result Contract

The action result `data` object contains:

- `artifact-id`
- `media-type`
- `byte-size`
- `sha256`
- `width`
- `height`
- `created-at`
- `download-url`

The artifact route serves bytes from the daemon-owned client artifact store and
guards against absolute paths and traversal. It is generic media/artifact
infrastructure, not screenshot-specific routing.

## Slice Boundary

This PR implements protocol/daemon/runtime graph wiring and a deterministic
fake-driver screenshot path. Fabric capture remains a follow-up adapter task:
the Fabric lane should discover the same operation from live client capability
evidence and write PNG bytes into the generic artifact store from the client
thread.

## Verification

- Protocol OpenAPI projection proves the generated alias, resource, and result
  schema.
- Testkit fake driver proves external callers can discover and invoke
  `media.screenshot.capture` through the runtime graph.
- Daemon tests prove artifact bytes are served through
  `/clients/{id}/artifacts/{artifact-id}` and traversal is rejected.
