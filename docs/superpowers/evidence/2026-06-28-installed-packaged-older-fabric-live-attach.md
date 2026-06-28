# Installed Packaged Older Fabric Live Attach Evidence

Date: 2026-06-28

## Result

The installed packaged Craftless distribution launched and attached the
representative older Fabric lane through the public supervisor and packaged CLI.

This is packaged older-lane live attach evidence. It is not final honest
survival gameplay evidence.

## Commands

Package build:

```sh
mise run package-cli
```

Result: passed. The package task rebuilt the CLI distribution and Docker
staging directory, including current and older Fabric driver jars plus
`driver-mods.json`.

Packaged supervisor:

```sh
build/docker/craftless/bin/craftless server start \
  --port 18082 \
  --workspace /tmp/craftless-packaged-older-live-attach/workspace
```

Result:

```json
{"ok":true,"url":"http://127.0.0.1:18082","openapi":"/openapi.json","events":"/events","workspace":"/tmp/craftless-packaged-older-live-attach/workspace"}
```

Packaged client create:

```sh
CRAFTLESS_HTTP_REQUEST_TIMEOUT_MS=900000 \
build/docker/craftless/bin/craftless clients create older-cli \
  --api http://127.0.0.1:18082 \
  --version 1.20.6 \
  --loader fabric \
  --loader-version 0.19.3 \
  --offline-name OlderCli
```

Result: returned `state":"RUNNING"` for client `older-cli`,
instance `older-cli-1.20.6-fabric`, Minecraft `1.20.6`, loader `FABRIC`.

Stop:

```sh
build/docker/craftless/bin/craftless clients older-cli stop --api http://127.0.0.1:18082
```

Result: returned `state":"STOPPED"`.

Process cleanup:

```sh
ps -axo pid,command | rg -i 'craftless|older-cli|1.20.6|fabric-loader' | rg -v 'rg -i|exec_command|codex' || true
```

Result: no managed Craftless, older-cli, 1.20.6, or Fabric loader processes
remained.

## Artifact Root

`/tmp/craftless-packaged-older-live-attach/artifacts/`

Key artifacts:

- `server-start.log`
- `clients-create.log`
- `client-openapi-attached.json`
- `client-actions-attached.json`
- `client-resources-attached.json`
- `client-events-attached.sse`
- `clients-stop.log`

## Attached Runtime Identity

From `client-openapi-attached.json` under `x-craftless`:

```json
{
  "x-craftless-client-id": "older-cli",
  "x-craftless-minecraft-version": "1.20.6",
  "x-craftless-loader": "FABRIC",
  "x-craftless-loader-version": "0.19.3",
  "x-craftless-driver": "craftless-driver-fabric",
  "x-craftless-driver-version": "0.1.0-SNAPSHOT",
  "x-craftless-mappings-fingerprint": "craftless-fabric-bindings-1-20-6",
  "x-craftless-runtime-fingerprint": "graph:90a3b5c0f713c767"
}
```

The generated OpenAPI document contained:

- 22 generated actions.
- 14 generated resources.
- 22 generated action alias paths.

Generated actions:

```text
entity.attack
entity.query
inventory.equip
inventory.query
navigation.follow
navigation.plan
navigation.stop
player.chat
player.look
player.move
player.query
player.raycast
recipe.craft
recipe.query
screen.close
screen.query
task.run
task.status
world.block.break
world.block.interact
world.block.query
world.time.query
```

Generated resources:

```text
runtime
registry
event
client
player
inventory
recipe
world
entity
screen
navigation
task
world.block
world.time
```

## Attach Event

`client-events-attached.sse` contained:

```text
event: client.created
data: {"id":"event:older-cli:0001","type":"client.created","clientId":"older-cli",...}

event: client.attached
data: {"id":"event:older-cli:0002","type":"client.attached","clientId":"older-cli",...}
```

The first artifact fetch raced the self-attach and produced empty action/resource
files. The attached artifacts listed above were fetched again after
`client.attached` and are the evidence for this phase.

## Installed Mods

The packaged client staged both expected mods:

```text
craftless-driver-fabric 0.1.0-SNAPSHOT 1.20.6
fabric-api 0.100.8+1.20.6 >=1.20.5- <1.20.7-
```

The Craftless driver jar was copied from the packaged lane into:

```text
/tmp/craftless-packaged-older-live-attach/workspace/cache/mods/craftless/abc3c80cf665bfc3a0ef920c0b27ed5cde3252358a3b0ceba89a2059c688c136.jar
```

## Limits

- This proves packaged older-lane launch, self-attach, generated OpenAPI,
  generated actions/resources, SSE events, and cleanup.
- This does not prove final honest survival gameplay.
- This does not add public gameplay actions, static gameplay catalogs,
  scenario shortcuts, or version-specific public route families.
