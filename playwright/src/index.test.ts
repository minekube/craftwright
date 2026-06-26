import { expect, test } from "bun:test";
import { createCraftlessFixture, createOpenApiActionClient, toHaveChat } from "./index";

test("fixture provides an injected automation client without shelling out to CLI output", async () => {
  let usedMc: unknown;
  const client = {
    launch: async () => ({ id: "alice" }),
  };
  const fixture = createCraftlessFixture({ client });

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

test("openapi action client discovers actions from the live client spec before invoking", async () => {
  const calls: Array<{ url: string; init?: RequestInit }> = [];
  const fetchImpl = async (url: string, init?: RequestInit): Promise<Response> => {
    calls.push({ url, init });
    if (url.endsWith("/clients/alice/openapi.json")) {
      return Response.json({
        openapi: "3.1.0",
        info: { title: "Craftless test API", version: "1" },
        paths: {
          "/clients/alice:run": { post: {} },
        },
        "x-craftless": {},
        "x-craftless-actions": [
          {
            id: "player.chat",
            schemaVersion: "1",
            args: { message: { type: "string", required: true } },
            availability: "available",
          },
        ],
      });
    }
    if (url.endsWith("/clients/alice:run")) {
      return Response.json({
        action: "player.chat",
        status: "ACCEPTED",
        message: "hello from helper",
      });
    }
    return new Response("missing", { status: 404 });
  };
  const client = createOpenApiActionClient({
    baseUrl: "http://127.0.0.1:8080",
    clientId: "alice",
    fetch: fetchImpl,
  });

  await expect(client.invoke("player.chat", { message: "hello from helper" })).resolves.toEqual({
    action: "player.chat",
    status: "ACCEPTED",
    message: "hello from helper",
  });

  expect(calls.map((call) => call.url)).toEqual([
    "http://127.0.0.1:8080/clients/alice/openapi.json",
    "http://127.0.0.1:8080/clients/alice:run",
  ]);
  expect(await calls[1]?.init?.body).toBe(
    JSON.stringify({
      action: "player.chat",
      args: { message: "hello from helper" },
    }),
  );
});

test("openapi action client discovers resources from the live client spec", async () => {
  const calls: Array<{ url: string; init?: RequestInit }> = [];
  const resources = [
    {
      id: "inventory",
      kind: "inventory",
      availability: "available",
      actions: ["inventory.query"],
    },
  ];
  const fetchImpl = async (url: string, init?: RequestInit): Promise<Response> => {
    calls.push({ url, init });
    if (url.endsWith("/clients/alice/openapi.json")) {
      return Response.json({
        openapi: "3.1.0",
        info: { title: "Craftless test API", version: "1" },
        paths: {},
        "x-craftless": {},
        "x-craftless-resources": resources,
      });
    }
    if (url.endsWith("/clients/alice/resources")) {
      return Response.json([{ id: "stale-projection" }]);
    }
    return new Response("missing", { status: 404 });
  };
  const client = createOpenApiActionClient({
    baseUrl: "http://127.0.0.1:8080",
    clientId: "alice",
    fetch: fetchImpl,
  });

  await expect(client.resources()).resolves.toEqual(resources);

  expect(calls.map((call) => call.url)).toEqual([
    "http://127.0.0.1:8080/clients/alice/openapi.json",
  ]);
});

test("openapi action client revalidates cached live spec by etag", async () => {
  const calls: Array<{ url: string; init?: RequestInit }> = [];
  const resources = [
    {
      id: "inventory",
      availability: "available",
      actions: ["inventory.query"],
    },
  ];
  const fetchImpl = async (url: string, init?: RequestInit): Promise<Response> => {
    calls.push({ url, init });
    if (url.endsWith("/clients/alice/openapi.json")) {
      if (init?.headers instanceof Headers && init.headers.get("if-none-match") === "\"fingerprint-a\"") {
        return new Response(null, { status: 304 });
      }
      return Response.json(
        {
          openapi: "3.1.0",
          info: { title: "Craftless test API", version: "1" },
          paths: {
            "/clients/alice:run": { post: {} },
          },
          "x-craftless": {
            "x-craftless-runtime-fingerprint": "fingerprint-a",
          },
          "x-craftless-actions": [
            {
              id: "player.chat",
              schemaVersion: "1",
              args: { message: { type: "string", required: true } },
              availability: "available",
            },
          ],
          "x-craftless-resources": resources,
        },
        {
          headers: {
            etag: "\"fingerprint-a\"",
          },
        },
      );
    }
    if (url.endsWith("/clients/alice:run")) {
      return Response.json({ action: "player.chat", status: "ACCEPTED" });
    }
    return new Response("missing", { status: 404 });
  };
  const client = createOpenApiActionClient({
    baseUrl: "http://127.0.0.1:8080",
    clientId: "alice",
    fetch: fetchImpl,
  });

  await client.invoke("player.chat", { message: "hello" });
  await expect(client.resources()).resolves.toEqual(resources);

  expect(calls.map((call) => call.url)).toEqual([
    "http://127.0.0.1:8080/clients/alice/openapi.json",
    "http://127.0.0.1:8080/clients/alice:run",
    "http://127.0.0.1:8080/clients/alice/openapi.json",
  ]);
  expect(calls[2]?.init?.headers).toBeInstanceOf(Headers);
  expect((calls[2]?.init?.headers as Headers).get("if-none-match")).toBe("\"fingerprint-a\"");
});
