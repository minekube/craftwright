import { createHash } from "node:crypto";
import { mkdir, readFile, writeFile } from "node:fs/promises";
import { join } from "node:path";

export interface CraftlessAutomationClient {
  launch(input: {
    name: string;
    id?: string;
    version: string;
    loader?: string;
    offline?: boolean;
  }): Promise<unknown>;
}

export interface CraftlessPlayer {
  waitForChat(pattern: RegExp | string): Promise<unknown>;
}

export interface CraftlessFixtureOptions<TClient extends CraftlessAutomationClient> {
  client: TClient;
}

export type FixtureUse<T> = (value: T) => Promise<void>;

export interface CraftlessOpenApiAction {
  id: string;
  args?: Record<string, { type: string; required?: boolean }>;
  availability?: "available" | "unavailable";
}

export interface CraftlessOpenApiResource {
  id: string;
  kind?: string;
  availability?: "available" | "unavailable";
  actions?: string[];
  [key: string]: unknown;
}

export interface CraftlessOpenApiDocument {
  paths?: Record<string, unknown>;
  "x-craftless"?: Record<string, string>;
  "x-craftless-actions"?: CraftlessOpenApiAction[];
  "x-craftless-resources"?: CraftlessOpenApiResource[];
}

export interface CraftlessLiveEvent {
  id: string;
  type: string;
  clientId?: string;
  resourceId?: string;
  operationId?: string;
  correlationId?: string;
  payload?: Record<string, unknown>;
  timestamp?: string;
}

export interface CraftlessLiveEventFilter {
  type?: string;
  resourceId?: string;
  operationId?: string;
  correlationId?: string;
}

export interface OpenApiActionClientOptions {
  baseUrl: string;
  clientId: string;
  cacheDirectory?: string;
  fetch?: typeof fetch;
}

export interface OpenApiActionClient {
  invoke(action: string, args?: Record<string, unknown>): Promise<unknown>;
  resources(): Promise<CraftlessOpenApiResource[]>;
  events(filter?: CraftlessLiveEventFilter): Promise<CraftlessLiveEvent[]>;
}

export function createOpenApiActionClient(options: OpenApiActionClientOptions): OpenApiActionClient {
  const fetchImpl = options.fetch ?? fetch;
  const baseUrl = options.baseUrl.replace(/\/+$/, "");
  const clientId = options.clientId;
  const cacheDirectory = options.cacheDirectory;
  let cachedOpenApi: { etag: string; document: CraftlessOpenApiDocument } | undefined;

  async function loadOpenApi(): Promise<CraftlessOpenApiDocument> {
    cachedOpenApi ??= await readCachedOpenApi(cacheDirectory, baseUrl, clientId);
    const headers = cachedOpenApi ? new Headers({ "if-none-match": cachedOpenApi.etag }) : undefined;
    const response = await fetchImpl(`${baseUrl}/clients/${clientId}/openapi.json`, headers ? { headers } : undefined);
    if (response.status === 304 && cachedOpenApi) {
      return cachedOpenApi.document;
    }
    if (!response.ok) {
      throw new Error(`Craftless API request failed with ${response.status}: ${await response.text()}`);
    }
    const document = (await response.json()) as CraftlessOpenApiDocument;
    const etag = response.headers.get("etag");
    if (etag) {
      cachedOpenApi = { etag, document };
      await writeCachedOpenApi(cacheDirectory, baseUrl, clientId, cachedOpenApi);
    } else {
      cachedOpenApi = undefined;
    }
    return document;
  }

  return {
    async resources(): Promise<CraftlessOpenApiResource[]> {
      const openApi = await loadOpenApi();
      return openApi["x-craftless-resources"] ?? [];
    },

    async events(filter: CraftlessLiveEventFilter = {}): Promise<CraftlessLiveEvent[]> {
      const openApi = await loadOpenApi();
      const streamPath = `/clients/${clientId}/events:stream`;
      if (!openApi.paths?.[streamPath]) {
        throw new Error(`client ${clientId} OpenAPI does not describe live event stream`);
      }
      const params = new URLSearchParams();
      if (filter.type) params.set("type", filter.type);
      if (filter.resourceId) params.set("resourceId", filter.resourceId);
      if (filter.operationId) params.set("operationId", filter.operationId);
      if (filter.correlationId) params.set("correlationId", filter.correlationId);
      const query = params.size > 0 ? `?${params.toString()}` : "";
      const response = await fetchImpl(`${baseUrl}${streamPath}${query}`);
      if (!response.ok) {
        throw new Error(`Craftless API request failed with ${response.status}: ${await response.text()}`);
      }
      return parseSseEvents(await response.text());
    },

    async invoke(action: string, args: Record<string, unknown> = {}): Promise<unknown> {
      const openApi = await loadOpenApi();
      const actionDescriptor = openApi["x-craftless-actions"]?.find((descriptor) => descriptor.id === action);
      if (!actionDescriptor || actionDescriptor.availability === "unavailable") {
        throw new Error(`action ${action} is not available for client ${clientId}`);
      }
      if (!openApi.paths?.[`/clients/${clientId}:run`]) {
        throw new Error(`client ${clientId} OpenAPI does not describe generic action invocation`);
      }
      requireActionArguments(actionDescriptor, args);

      return fetchJson(fetchImpl, `${baseUrl}/clients/${clientId}:run`, {
        method: "POST",
        headers: { "content-type": "application/json" },
        body: JSON.stringify({ action, args }),
      });
    },
  };
}

function parseSseEvents(body: string): CraftlessLiveEvent[] {
  return body
    .split(/\n\n+/)
    .map((frame) =>
      frame
        .split(/\n/)
        .filter((line) => line.startsWith("data: "))
        .map((line) => line.slice("data: ".length))
        .join("\n"),
    )
    .filter((data) => data.length > 0)
    .map((data) => JSON.parse(data) as CraftlessLiveEvent);
}

async function readCachedOpenApi(
  cacheDirectory: string | undefined,
  baseUrl: string,
  clientId: string,
): Promise<{ etag: string; document: CraftlessOpenApiDocument } | undefined> {
  if (!cacheDirectory) {
    return undefined;
  }
  try {
    const body = await readFile(openApiCachePath(cacheDirectory, baseUrl, clientId), "utf8");
    const entry = JSON.parse(body) as { etag?: unknown; document?: unknown };
    if (typeof entry.etag === "string" && typeof entry.document === "object" && entry.document !== null) {
      return {
        etag: entry.etag,
        document: entry.document as CraftlessOpenApiDocument,
      };
    }
  } catch {
    return undefined;
  }
  return undefined;
}

async function writeCachedOpenApi(
  cacheDirectory: string | undefined,
  baseUrl: string,
  clientId: string,
  entry: { etag: string; document: CraftlessOpenApiDocument },
): Promise<void> {
  if (!cacheDirectory) {
    return;
  }
  await mkdir(cacheDirectory, { recursive: true });
  await writeFile(openApiCachePath(cacheDirectory, baseUrl, clientId), JSON.stringify(entry), "utf8");
}

function openApiCachePath(cacheDirectory: string, baseUrl: string, clientId: string): string {
  const digest = createHash("sha256").update(`${baseUrl}\n${clientId}`).digest("hex").slice(0, 32);
  return join(cacheDirectory, `${digest}.json`);
}

async function fetchJson<T>(fetchImpl: typeof fetch, url: string, init?: RequestInit): Promise<T> {
  const response = await fetchImpl(url, init);
  if (!response.ok) {
    throw new Error(`Craftless API request failed with ${response.status}: ${await response.text()}`);
  }
  return response.json() as Promise<T>;
}

function requireActionArguments(action: CraftlessOpenApiAction, args: Record<string, unknown>): void {
  const descriptors = action.args ?? {};
  for (const name of Object.keys(args)) {
    if (!descriptors[name]) {
      throw new Error(`action ${action.id} does not declare argument ${name}`);
    }
  }
  for (const [name, descriptor] of Object.entries(descriptors)) {
    if (descriptor.required && !(name in args)) {
      throw new Error(`action ${action.id} requires argument ${name}`);
    }
  }
}

export function createCraftlessFixture<TClient extends CraftlessAutomationClient>(
  options: CraftlessFixtureOptions<TClient>,
) {
  const client = options.client;
  return async function craftlessFixture(
    _args: Record<string, unknown>,
    use: FixtureUse<TClient>,
  ): Promise<void> {
    await use(client);
  };
}

export async function toHaveChat(
  player: Pick<CraftlessPlayer, "waitForChat">,
  pattern: RegExp | string,
): Promise<{ pass: boolean; message: string }> {
  await player.waitForChat(pattern);
  return {
    pass: true,
    message: `received chat matching ${pattern}`,
  };
}
