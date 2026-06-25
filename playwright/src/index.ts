export interface CraftwrightAutomationClient {
  launch(input: {
    name: string;
    id?: string;
    version: string;
    loader?: string;
    offline?: boolean;
  }): Promise<unknown>;
}

export interface CraftwrightPlayer {
  waitForChat(pattern: RegExp | string): Promise<unknown>;
}

export interface CraftwrightFixtureOptions<TClient extends CraftwrightAutomationClient> {
  client: TClient;
}

export type FixtureUse<T> = (value: T) => Promise<void>;

export function createCraftwrightFixture<TClient extends CraftwrightAutomationClient>(
  options: CraftwrightFixtureOptions<TClient>,
) {
  const client = options.client;
  return async function craftwrightFixture(
    _args: Record<string, unknown>,
    use: FixtureUse<TClient>,
  ): Promise<void> {
    await use(client);
  };
}

export async function toHaveChat(
  player: Pick<CraftwrightPlayer, "waitForChat">,
  pattern: RegExp | string,
): Promise<{ pass: boolean; message: string }> {
  await player.waitForChat(pattern);
  return {
    pass: true,
    message: `received chat matching ${pattern}`,
  };
}
