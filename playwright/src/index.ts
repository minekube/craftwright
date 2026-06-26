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

export interface OpenApiActionClientOptions {
  baseUrl: string;
  clientId: string;
  fetch?: typeof fetch;
}

export interface OpenApiActionClient {
  invoke(action: string, args?: Record<string, unknown>): Promise<unknown>;
  resources(): Promise<CraftlessOpenApiResource[]>;
}

export function createOpenApiActionClient(options: OpenApiActionClientOptions): OpenApiActionClient {
  const fetchImpl = options.fetch ?? fetch;
  const baseUrl = options.baseUrl.replace(/\/+$/, "");
  const clientId = options.clientId;
  let cachedOpenApi: { etag: string; document: CraftlessOpenApiDocument } | undefined;

  async function loadOpenApi(): Promise<CraftlessOpenApiDocument> {
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
