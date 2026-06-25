import { expect, test } from "bun:test";
import { createCraftwrightFixture, toHaveChat } from "./index";

test("fixture provides an injected automation client without shelling out to CLI output", async () => {
  let usedMc: unknown;
  const client = {
    launch: async () => ({ id: "alice" }),
  };
  const fixture = createCraftwrightFixture({ client });

  await fixture({}, async (mc) => {
    usedMc = mc;
    await mc.launch({ name: "Alice", version: "1.21.4", offline: true });
  });

  expect(usedMc).toBe(client);
});

test("chat matcher delegates to the injected player waitForChat method", async () => {
  const calls: RegExp[] = [];
  const player = {
    waitForChat: async (pattern: RegExp) => {
      calls.push(pattern);
      return { type: "chat", message: "Welcome Alice" };
    },
  };

  await expect(toHaveChat(player, /Welcome/)).resolves.toEqual({
    pass: true,
    message: "received chat matching /Welcome/",
  });
  expect(calls).toEqual([/Welcome/]);
});
