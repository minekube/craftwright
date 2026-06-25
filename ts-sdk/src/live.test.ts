import { afterEach, expect, test } from "bun:test";
import { createCraftwright } from "./index";

const processes: Array<ReturnType<typeof Bun.spawn>> = [];

afterEach(() => {
  for (const process of processes.splice(0)) {
    process.kill();
  }
});

test("sdk talks to live mcw clients api server", async () => {
  const server = Bun.spawn(
    ["mise", "exec", "--", "gradle", "-q", ":cli:run", "--args=clients api"],
    {
      stdout: "pipe",
      stderr: "pipe",
    },
  );
  processes.push(server);

  const metadata = await readServerMetadata(server, 30_000);
  const mc = createCraftwright({ baseUrl: metadata.url });
  const alice = await mc.launch({ name: "Alice", version: "1.21.4", offline: true });

  await alice.connect("localhost", 25565);
  await alice.chat("hello live sdk");

  await expect(alice.waitForChat(/hello live sdk/, { timeoutMs: 1_000, intervalMs: 10 })).resolves.toMatchObject({
    type: "chat",
    message: "hello live sdk",
  });
  await expect(alice.player()).resolves.toMatchObject({
    id: "alice",
    name: "Alice",
    state: "CONNECTED",
  });
  await expect(alice.stop()).resolves.toMatchObject({
    id: "alice",
    state: "STOPPED",
  });
}, 45_000);

async function readServerMetadata(
  server: ReturnType<typeof Bun.spawn>,
  timeoutMs: number,
): Promise<{ url: string }> {
  const reader = server.stdout.getReader();
  const stderr = new Response(server.stderr).text();
  let output = "";
  const timedOut = sleep(timeoutMs).then(() => ({ kind: "timeout" as const }));
  const exited = server.exited.then((exitCode) => ({ kind: "exit" as const, exitCode }));

  try {
    while (true) {
      const result = await Promise.race([
        reader.read().then((read) => ({ kind: "read" as const, read })),
        exited,
        timedOut,
      ]);
      if (result.kind === "timeout") {
        throw new Error(`timed out waiting for mcw clients api metadata\nstdout:\n${output}\nstderr:\n${await stderr}`);
      }
      if (result.kind === "exit") {
        throw new Error(
          `mcw clients api exited before metadata with code ${result.exitCode}\nstdout:\n${output}\nstderr:\n${await stderr}`,
        );
      }
      const { value, done } = result.read;
      if (done) {
        break;
      }
      output += new TextDecoder().decode(value);
      const line = output.split(/\r?\n/).find((candidate) => candidate.trim().startsWith("{"));
      if (line) return JSON.parse(line);
    }
  } finally {
    reader.releaseLock();
  }

  throw new Error(`mcw clients api did not print metadata\nstdout:\n${output}\nstderr:\n${await stderr}`);
}

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}
