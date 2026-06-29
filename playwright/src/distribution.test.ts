import { describe, expect, test } from "bun:test";
import { existsSync, readFileSync } from "node:fs";
import { resolve } from "node:path";

const root = resolve(import.meta.dir, "..", "..");

function exists(path: string): boolean {
  return existsSync(resolve(root, path));
}

function read(path: string): string {
  return readFileSync(resolve(root, path), "utf8");
}

describe("distribution surface", () => {
  test("release workflow, setup action, installer, and Docker runtime files exist", () => {
    expect(exists(".github/workflows/release.yml")).toBe(true);
    expect(exists(".github/actions/setup-craftless/action.yml")).toBe(true);
    expect(exists("install.sh")).toBe(true);
    expect(exists("Dockerfile")).toBe(true);
    expect(exists("docker/entrypoint.sh")).toBe(true);
  });

  test("CLI distribution packages driver mod manifest", () => {
    const cliBuild = read("cli/build.gradle.kts");
    const fabricBuild = read("driver-fabric/build.gradle.kts");
    const mise = read(".mise.toml");

    expect(cliBuild).toContain("driver-mods.json");
    expect(cliBuild).toContain("fabric-driver-lanes.json");
    expect(cliBuild).toContain("writeFabricDriverLaneCatalog");
    expect(cliBuild).toContain("renderDriverModManifest");
    expect(cliBuild).toContain("JsonSlurper");
    expect(cliBuild).toContain("stageFabricDriverLaneArtifacts");
    expect(cliBuild).toContain("runtimeMods");
    expect(cliBuild).toContain("fabric-current-remap-jar");
    expect(cliBuild).toContain("driver-lane-artifacts");
    expect(cliBuild).toContain("distributionPath");
    expect(fabricBuild).toContain("mods/craftless-driver-fabric.jar");
    expect(cliBuild).not.toContain("catalog.readText().trimEnd()");
    expect(cliBuild).not.toContain('extensions.extraProperties["fabricCompiledMinecraftVersion"]');
    expect(cliBuild).not.toContain('extensions.extraProperties["fabricCompiledLoaderVersion"]');
    expect(cliBuild).not.toContain('into("mods")');
    expect(mise).toContain("driver-mods.json");
    expect(mise).toContain("tar -tf cli/build/distributions/craftless-*.tar | grep -q '/driver-mods.json$'");
    expect(mise).toContain("build/driver-mods-from-tar.json");
    expect(mise).toContain("! grep -q 'artifactKey' build/driver-mods-from-tar.json");
    expect(mise).toContain("! grep -q 'distributionPath' build/driver-mods-from-tar.json");
    expect(mise).toContain("jar tf cli/build/distributions/craftless-*.zip | grep -q '/driver-mods.json$'");
    expect(mise).toContain("! unzip -p cli/build/distributions/craftless-*.zip '*/driver-mods.json' | grep -q 'artifactKey'");
    expect(mise).toContain("! unzip -p cli/build/distributions/craftless-*.zip '*/driver-mods.json' | grep -q 'distributionPath'");
  });

  test("CLI distribution packages representative older fabric lane", () => {
    const cliBuild = read("cli/build.gradle.kts");
    const fabricBuild = read("driver-fabric/build.gradle.kts");
    const mise = read(".mise.toml");

    expect(fabricBuild).toContain("craftless.fabric.distributionPath");
    expect(cliBuild).toContain("craftless.extraFabricDriverLaneRoot");
    expect(cliBuild).toContain("extraFabricDriverLaneRoot");
    expect(mise).toContain("-Pcraftless.fabric.artifactKey=fabric-1-20-6-remap-jar");
    expect(mise).toContain("-Pcraftless.fabric.distributionPath=mods/fabric-1.20.6/craftless-driver-fabric.jar");
    expect(mise).toContain("-Pcraftless.extraFabricDriverLaneRoot=build/driver-lanes");
    expect(mise).toContain("mods/fabric-1.20.6/craftless-driver-fabric.jar");
    expect(mise).not.toContain("mods/fabric-1.20.6/runtime/");
    expect(mise).toContain("mods/fabric-1.21.6/runtime/");
    expect(mise).toContain(":driver-fabric:preparePathfinderRuntime");
    expect(mise).toContain("-Pcraftless.fabric.runtimeMods=");
    expect(mise).toContain("baritone-api-fabric-");
    expect(mise).toContain("nether-pathfinder-");
    expect(mise).toContain("tar -tf cli/build/distributions/craftless-*.tar | grep -q '/mods/fabric-1.21.6/runtime/baritone-api-fabric-");
    expect(mise).toContain("grep -q 'runtimeMods' build/driver-mods-from-tar.json");
    expect(mise).toContain("jar tf cli/build/distributions/craftless-*.zip | grep -q '/mods/fabric-1.21.6/runtime/baritone-api-fabric-");
    expect(mise).toContain("unzip -p cli/build/distributions/craftless-*.zip '*/driver-mods.json' | grep -q 'runtimeMods'");
    expect(mise).toContain("minecraftVersion");
    expect(mise).toContain("1.20.6");
  });

  test("CLI distribution packages latest official fabric lane", () => {
    const mise = read(".mise.toml");

    expect(mise).toContain(":driver-fabric-official:jar");
    expect(mise).toContain("build/driver-lanes/latest-official");
    expect(mise).toContain("mods/fabric-26.2/craftless-driver-fabric-official.jar");
    expect(mise).toContain('\\"minecraftVersion\\": \\"26.2\\"');
    expect(mise).toContain('\\"fabricApiVersion\\": \\"0.153.0+26.2\\"');
    expect(mise).toContain('\\"javaMajorVersion\\": 25');
    expect(mise).toContain("java@temurin-25.0.3+9.0.LTS");
  });

  test("packaged latest current probe is a mise-managed product surface", () => {
    const mise = read(".mise.toml");
    const script = read("scripts/packaged-latest-current-probe.sh");

    expect(mise).toContain("[tasks.packaged-latest-current-probe]");
    expect(mise).toContain("CRAFTLESS_LOCAL_SERVER_SMOKE=1");
    expect(mise).toContain("CRAFTLESS_SMOKE_JAVA_EXECUTABLE=$HOME/.local/share/mise/installs/java/temurin-25.0.3+9.0.LTS/bin/java");
    expect(mise).toContain("CRAFTLESS_PACKAGED_LATEST_TIMEOUT_MS=900000");
    expect(mise).toContain("$PWD/scripts/packaged-latest-current-probe.sh");
    expect(mise).toContain("mise run package-cli");
    expect(script).toContain("build/docker/craftless/bin/craftless");
    expect(script).toContain("--version latest-release");
    expect(script).toContain("supervisor-openapi.json");
    expect(script).toContain("clients-create-latest-release.log");
    expect(script).toContain("client-openapi-connected.json");
    expect(script).toContain("client-rpc-subscribe.json");
    expect(script).toContain("client-generated-action-selected.json");
    expect(script).toContain("client-rpc-invoke-generated.json");
    expect(script).toContain("client-cli-invoke-generated.log");
    expect(script).toContain("x-craftless-actions");
    expect(script).toContain('!action.id.startsWith("task.")');
    expect(script).toContain('method: "invoke"');
    expect(script).toContain('clients "$CLIENT_ID" run "$GENERATED_ACTION_ID"');
    expect(script).toContain("mise exec -- bun");
    expect(script).not.toContain("task.survival");
  });

  test("packaged representative older probe is a matching product surface", () => {
    const mise = read(".mise.toml");
    const script = read("scripts/packaged-representative-older-probe.sh");

    expect(mise).toContain("[tasks.packaged-representative-older-probe]");
    expect(mise).toContain("CRAFTLESS_SMOKE_MINECRAFT_VERSION=1.20.6");
    expect(mise).toContain("$PWD/scripts/packaged-representative-older-probe.sh");
    expect(mise).toContain("mise run package-cli");
    expect(script).toContain("build/docker/craftless/bin/craftless");
    expect(script).toContain("--version 1.20.6");
    expect(script).toContain("--loader-version 0.19.3");
    expect(script).toContain("clients-create-representative-older.log");
    expect(script).toContain("client-openapi-connected.json");
    expect(script).toContain("client-rpc-subscribe.json");
    expect(script).toContain("client-generated-action-selected.json");
    expect(script).toContain("client-rpc-invoke-generated.json");
    expect(script).toContain("client-cli-invoke-generated.log");
    expect(script).toContain("x-craftless-actions");
    expect(script).toContain('!action.id.startsWith("task.")');
    expect(script).toContain('method: "invoke"');
    expect(script).toContain('clients "$CLIENT_ID" run "$GENERATED_ACTION_ID"');
    expect(script).toContain("mise exec -- bun");
    expect(script).not.toContain("task.survival");
    expect(script).not.toContain(":driver-fabric:runClient");
  });

  test("final public gameplay probe uses generated public surfaces only", () => {
    const mise = read(".mise.toml");
    const script = read("scripts/final-public-gameplay-probe.sh");

    expect(mise).toContain("[tasks.final-public-gameplay-probe]");
    expect(mise).toContain("CRAFTLESS_DISABLE_SMOKE_PROVISIONING=1");
    expect(mise).toContain("CRAFTLESS_SMOKE_MINECRAFT_VERSION=1.21.6");
    expect(mise).toContain("$PWD/scripts/final-public-gameplay-probe.sh");
    expect(mise).not.toContain("CRAFTLESS_SMOKE_PROVISION_ITEM_ID=");
    expect(script).toContain("GET /clients/{id}/openapi.json authority");
    expect(script).toContain("missing-generic-primitive:");
    expect(script).toContain("player.chat");
    expect(script).toContain("inventory.query");
    expect(script).toContain("world.block.break");
    expect(script).toContain("navigation.plan");
    expect(script).toContain("navigation.follow");
    expect(script).toContain('radius: 64');
    expect(script).toContain("materialDropPosition");
    expect(script).toContain('category: "collectable"');
    expect(script).toContain("recipe-query-after-material");
    expect(script).toContain("recipe.craft");
    expect(script).toContain("craftingRecipe");
    expect(script).toContain("attempt === 1 ? 64 : 16");
    expect(script).toContain("entity-query-target-attempt");
    expect(script).toContain("entity-search-player");
    expect(script).toContain("verticalDelta");
    expect(script).toContain("entityNavigationBlocker");
    expect(script).toContain("entity.attack");
    expect(script).not.toContain("setTimeout(resolve, 1500));\nconst afterBreakInventory");
    expect(script).not.toContain("task.survival");
    expect(script).not.toContain("kill.cow");
    expect(script).not.toContain("find.tree");
    expect(script).not.toContain("craft.sword");
    expect(script).not.toContain("/give");
  });

  test("public docs make client creation lifecycle explicit", () => {
    const readme = read("README.md");
    const skill = read(".agents/skills/craftless-public-gameplay-agent/SKILL.md");

    for (const surface of [readme, skill].map((text) => text.replace(/\s+/g, " "))) {
      expect(surface).toContain("launches a new daemon-managed real Minecraft Java client process");
      expect(surface).toContain("not a selector, retry, or reuse operation");
      expect(surface).toContain("Creating fresh timestamped ids for retries leaves multiple Minecraft clients running");
      expect(surface).toContain("craftless clients <id> stop --api \"$CRAFTLESS\"");
    }
  });

  test("Dockerfile copies a built CLI distribution instead of building Craftless", () => {
    const dockerfile = read("Dockerfile");

    expect(dockerfile).toContain("COPY build/docker/craftless/");
    for (const forbidden of ["gradle", "mise", "npm", "yarn", "pnpm", "bun"]) {
      expect(dockerfile.toLowerCase()).not.toContain(forbidden);
    }
  });

  test("release workflow builds artifacts before Docker and publishes GitHub release assets", () => {
    const workflow = read(".github/workflows/release.yml");

    expect(workflow).toContain("mise run ci");
    expect(workflow).toContain("mise run package-cli");
    expect(workflow).toContain("softprops/action-gh-release");
    expect(workflow).toContain("generate_release_notes: true");
    expect(workflow).toContain("docker/setup-qemu-action");
    expect(workflow).toContain("docker/build-push-action");
    expect(workflow).toContain("platforms: linux/amd64,linux/arm64");
    expect(workflow).toContain("ghcr.io/minekube/craftless");
  });

  test("scheduled Release Please workflow creates release tags from main changes", () => {
    const workflow = read(".github/workflows/release-please.yml");
    const config = JSON.parse(read("release-please-config.json"));
    const manifest = JSON.parse(read(".release-please-manifest.json"));
    const changelog = read("CHANGELOG.md");

    expect(workflow).toContain('branches: ["main"]');
    expect(workflow).toContain("schedule:");
    expect(workflow).toContain('cron: "17 8 * * 1"');
    expect(workflow).toContain("workflow_dispatch:");
    expect(workflow).toContain("googleapis/release-please-action@v4");
    expect(workflow).toContain("config-file: release-please-config.json");
    expect(workflow).toContain("manifest-file: .release-please-manifest.json");
    expect(workflow).toContain("pull-requests: write");
    expect(config.packages["."]["package-name"]).toBe("craftless");
    expect(config.packages["."]["release-type"]).toBe("simple");
    expect(config.packages["."]["include-v-in-tag"]).toBe(true);
    expect(config.packages["."]["include-component-in-tag"]).toBe(false);
    expect(manifest["."]).toBe("0.1.2");
    expect(changelog).toContain("Release Please");
  });

  test("Fumadocs site is a Cloudflare Workers product surface with previews", () => {
    const mise = read(".mise.toml");
    const packageJson = JSON.parse(read("docs-site/package.json"));
    const wrangler = read("docs-site/wrangler.jsonc");
    const nextConfig = read("docs-site/next.config.mjs");
    const openapi = read("docs-site/lib/openapi.ts");
    const source = read("docs-site/lib/source.ts");
    const apiPage = read("docs-site/components/api-page.tsx");
    const page = read("docs-site/app/docs/[[...slug]]/page.tsx");
    const apiReference = read("docs-site/content/docs/api-reference.mdx");
    const schema = JSON.parse(read("docs-site/openapi/craftless-supervisor.json"));

    expect(packageJson.scripts.build).toBe("next build");
    expect(packageJson.scripts.deploy).toBe("wrangler deploy");
    expect(packageJson.scripts["preview:upload"]).toContain("wrangler versions upload");
    expect(packageJson.scripts["openapi:generate"]).toContain("mise run docs-site-openapi");
    expect(packageJson.dependencies["fumadocs-openapi"]).toBeDefined();
    expect(packageJson.dependencies["fumadocs-ui"]).toBeDefined();
    expect(packageJson.dependencies.next).toBeDefined();
    expect(packageJson.devDependencies.wrangler).toBeDefined();
    expect(nextConfig).toContain("output: 'export'");
    expect(nextConfig).toContain("trailingSlash: true");
    expect(openapi).toContain("createOpenAPI");
    expect(openapi).toContain("./openapi/craftless-supervisor.json");
    expect(source).toContain("openapi.staticSource");
    expect(source).toContain("openapi.loaderPlugin()");
    expect(apiPage).toContain("createOpenAPIPage");
    expect(page).toContain("getOpenAPIPageProps()");
    expect(schema.openapi).toBe("3.1.0");
    expect(schema.paths["/openapi.json"].get.description).toContain("stable supervisor API");
    expect(schema.tags.find((tag: { name: string }) => tag.name === "clients")?.description).toContain(
      "Daemon-managed real Minecraft Java clients",
    );
    expect(apiReference).toContain("Generated operation pages are grouped by Craftless API pillar");
    expect(mise).toContain("[tasks.docs-site-openapi]");
    expect(mise).toContain("[tasks.docs-site-build]");
    expect(mise).toContain("mise exec -- bun install");
    expect(mise).toContain("mise exec -- bun run build");
    expect(wrangler).toContain('"name": "craftless-docs"');
    expect(wrangler).toContain('"preview_urls": true');
    expect(wrangler).toContain('"directory": "./out"');
    expect(wrangler).toContain('"not_found_handling": "404-page"');
    expect(exists(".github/workflows/docs-pages.yml")).toBe(false);
  });

  test("install script installs from minekube/craftless GitHub releases", () => {
    const install = read("install.sh");

    expect(install).toContain("minekube/craftless");
    expect(install).toContain("api.github.com/repos/${CRAFTLESS_REPOSITORY}/releases/latest");
    expect(install).toContain("releases/download");
    expect(install).toContain("craftless-${asset_version}.tar");
    expect(install).toContain("CRAFTLESS_INSTALL_DIR");
  });

  test("setup action installs Craftless and can start the daemon", () => {
    const action = read(".github/actions/setup-craftless/action.yml");

    expect(action).toContain("description:");
    expect(action).toContain("version:");
    expect(action).toContain("start:");
    expect(action).toContain("api-url");
    expect(action).toContain("craftless daemon start");
  });

  test("README exposes install, Docker, and GitHub Actions quickstarts", () => {
    const readme = read("README.md");

    expect(readme).toContain("## Quickstart");
    expect(readme).toContain("curl -fsSL https://raw.githubusercontent.com/minekube/craftless/main/install.sh");
    expect(readme).toContain("CRAFTLESS_VERSION=v0.1.2");
    expect(readme).toContain("docker run");
    expect(readme).toContain("minekube/craftless/.github/actions/setup-craftless@v0.1.2");
    expect(readme).toContain("Release Please opens or updates the release PR");
    expect(readme).not.toContain("setup-craftless@v0.1.0");
    expect(readme).toContain("Minecraft artifacts are downloaded into the workspace at runtime");
    expect(readme).toContain("Latest/current `26.2` and representative older `1.20.6` packaged lanes are verified");
    expect(readme).not.toContain("gameplay actions still empty");
    expect(readme).not.toContain("final completion still requires a refreshed run after latest/current compatibility work");
    expect(readme.toLowerCase()).not.toContain("homebrew");
    expect(readme.toLowerCase()).not.toContain("brew install");
  });

  test("README does not present legacy diagnostic gameplay setup as product status", () => {
    const readme = read("README.md");

    expect(readme).not.toContain("--loader-version 0.17.2");
    expect(readme).not.toContain("provisions an `Iron Sword`");
    expect(readme).not.toContain("target-item provisioning");
    expect(readme).not.toContain("CRAFTLESS_SMOKE_PROVISION_ITEM");
    expect(readme).toContain("without server-provisioned inventory");
  });

  test("README presents generated API as product path and bridge as lifecycle evidence", () => {
    const readme = read("README.md");

    expect(readme).toContain("generated per-client OpenAPI");
    expect(readme).toContain("runtime capability graph");
    expect(readme).toContain("lifecycle/launch evidence only");
    expect(readme).toContain("not a gameplay adapter");
    expect(readme).not.toContain("HeadlessMC command");
    expect(readme).not.toContain("HMC-Specifics command");
  });

  test("active docs prefer latest aliases over concrete latest ids", () => {
    const readme = read("README.md");
    const roadmap = read("docs/roadmap.md");
    const fileManagement = read("docs/client-file-management.md");

    expect(readme).toContain('"version": "latest-release"');
    expect(readme).toContain("--mc latest-release");
    expect(fileManagement).toContain("latest-release");
    expect(fileManagement).toContain("latest-snapshot");
    expect(roadmap).not.toContain("current latest `26.2`");
    expect(roadmap).toContain("latest-release");
  });

  test("installer and release workflow do not require Homebrew", () => {
    const install = read("install.sh");
    const workflow = read(".github/workflows/release.yml");

    expect(install.toLowerCase()).not.toContain("brew");
    expect(workflow.toLowerCase()).not.toContain("brew");
  });
});
