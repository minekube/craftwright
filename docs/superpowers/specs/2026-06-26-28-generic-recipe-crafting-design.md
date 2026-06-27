# Generic Recipe And Crafting Design

## Purpose

Phase 28 closes the next honest survival blocker by giving public agents a
generic way to discover craftable outputs and invoke crafting through the live
generated API. It must not add survival shortcuts such as `craft.sword`,
`craft.planks`, `craft.table`, `make.weapon`, or `task.survival.*`.

## Public Shape

The live per-client OpenAPI may expose these Craftless-owned runtime actions:

- `recipe.query`: list recipe handles discovered from the running client and
  filtered by public criteria such as `category`, `output`, `craftable`, and
  `limit`.
- `recipe.craft`: invoke a discovered recipe handle with an optional `count`
  and `target` object.

The live graph may expose:

- resource `recipe`;
- handle `recipe.handle`;
- operation events for `recipe.query` and `recipe.craft`.

Recipe handles are opaque Craftless-owned values such as
`recipe.handle:<fingerprint>`. Public output may include stable categories such
as `material`, `tool`, `weapon`, `utility`, `food`, and `other`, plus public
inventory evidence. It must not expose raw registry identifiers, mappings,
Fabric/Yarn class names, or Minecraft implementation names as public contracts.

## Runtime Discovery

Discovery belongs in the Fabric runtime capability graph. A probe inspects live
client state on the client thread:

- player and inventory availability;
- world and screen/handler availability;
- recipe manager or recipe-book availability;
- whether the currently available crafting context can execute the recipe.

If recipe metadata can be discovered but execution is not currently possible,
the graph may expose unavailable operations with machine-readable reasons such
as `recipe-context-unavailable`, `recipe-not-craftable`, or
`screen-handler-unavailable`. Do not add static placeholder action descriptors.

## Invocation

`recipe.query` is read-only and returns public recipe records:

- `handle`;
- `label`;
- `category`;
- `craftable`;
- `requires`;
- `produces`;
- optional `reason` when not craftable.

`recipe.craft` consumes a public recipe handle returned by `recipe.query`. It
must validate stale handles, count bounds, current inventory/screen state, and
post-action evidence. The result should include:

- `handle`;
- `accepted`;
- `changed`;
- `crafted-count`;
- `inventory-before` or fingerprint evidence;
- `inventory-after` or fingerprint evidence;
- machine-readable failure reason when applicable.

When taking a crafting output slot, `crafted-count` must be based on the
observed output stack count before the slot click, not on a hard-coded
single-item assumption or the requested count alone.

## Public-Agent Use

The final public-agent survival scenario composes existing generated actions
with recipe actions:

1. collect ordinary materials through `world.block.query`,
   `navigation.follow`, `world.block.break`, `entity.query`, and
   `inventory.query`;
2. query recipes through `recipe.query`;
3. craft generic outputs by handle through `recipe.craft`;
4. verify inventory state through `inventory.query`;
5. equip through `inventory.equip`;
6. continue to placement, combat, chat, and Robin confirmation.

The runner may prefer useful categories such as `weapon`, `tool`, `material`,
or `utility`, but that preference is agent policy. The product API remains
generic.

## Acceptance

- Generated per-client OpenAPI exposes recipe operations only from the runtime
  graph.
- Public action/resource/handle names are Craftless-owned.
- Focused tests prove no `craft.sword`, `craft.planks`, `craft.table`,
  `make.weapon`, `kill.cow`, or `task.survival.*` shortcut appears.
- Public-agent tests prove an external policy can query recipe handles, invoke
  a generic craft, and verify inventory state without static survival actions.
- Final completion still requires live gameplay and Robin's Minecraft chat
  confirmation.
