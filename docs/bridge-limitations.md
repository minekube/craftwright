# HeadlessMC/HMC-Specifics Bridge Limitations

Craftless's Phase 1 bridge backend is temporary evidence infrastructure. It
may launch and drive a real Minecraft Java client through HeadlessMC and
HMC-Specifics, but public routes and CLI output must remain Craftless-owned.

The bridge must not expose HeadlessMC or HMC-Specifics command strings as public
API names, JSON fields, CLI verbs, or SDK methods. Those command strings are an
internal adapter detail.

The `driver-runtime` module can adapt this bridge behind `DriverSession`, but
that adapter is still only Phase 1 evidence. The durable backend remains the
Fabric driver running inside the client JVM.

Known limitations:

- Movement is simulated through bridge input and must not be described as robust
  player movement.
- First-run screens, title screens, focus, and current GUI state can swallow
  movement input.
- Usernames longer than 16 characters fail offline login packet encoding.
- Rendered text and server logs are useful evidence, but they are not the final
  structured event or perception API.
- Nearby blocks, nearby entities, raycasts, inventory, screen state, and clicks
  need a Craftless Fabric driver with direct Minecraft client API access.

The Fabric backend now has a client-thread gateway for connection, chat,
command, stop, and generated action invocation. It accepts `player.move` and
`player.chat` through generic action invocation and maps them to client movement
intent and Craftless-owned chat action execution. The daemon exposes
`/clients/{id}/openapi.json` with client metadata plus discovered action
schemas, `GET /clients/{id}/actions` for discovery, `POST /clients/{id}:run` as
the stable generic invocation path, and generated aliases such as
`POST /clients/{id}/player:move` and `POST /clients/{id}/player:chat` from
those action descriptors. The next durable milestone is proving movement in a
real-client smoke and adding player state, look direction, raycasts, and
perception as generated resources/actions inside the client.
