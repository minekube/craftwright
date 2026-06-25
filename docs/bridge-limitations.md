# HeadlessMC/HMC-Specifics Bridge Limitations

Craftwright's Phase 1 bridge backend is temporary evidence infrastructure. It
may launch and drive a real Minecraft Java client through HeadlessMC and
HMC-Specifics, but public routes and CLI output must remain Craftwright-owned.

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
  need a Craftwright Fabric driver with direct Minecraft client API access.

The next durable milestone is a Fabric driver that sets movement intent, look
direction, chat, connection lifecycle, raycasts, and perception directly inside
the client.
