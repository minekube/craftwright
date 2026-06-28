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
    expect(mise).toContain("! tar -xOf cli/build/distributions/craftless-*.tar '*/driver-mods.json' | grep -q 'artifactKey'");
    expect(mise).toContain("! tar -xOf cli/build/distributions/craftless-*.tar '*/driver-mods.json' | grep -q 'distributionPath'");
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
    expect(script).toContain('method: "invoke"');
    expect(script).toContain('clients "$CLIENT_ID" run "$GENERATED_ACTION_ID"');
    expect(script).toContain("mise exec -- bun");
    expect(script).not.toContain("task.survival");
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
    expect(workflow).toContain("docker/setup-qemu-action");
    expect(workflow).toContain("docker/build-push-action");
    expect(workflow).toContain("platforms: linux/amd64,linux/arm64");
    expect(workflow).toContain("ghcr.io/minekube/craftless");
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
    expect(action).toContain("craftless server start");
  });

  test("README exposes install, Docker, and GitHub Actions quickstarts", () => {
    const readme = read("README.md");

    expect(readme).toContain("## Quickstart");
    expect(readme).toContain("curl -fsSL https://raw.githubusercontent.com/minekube/craftless/main/install.sh");
    expect(readme).toContain("CRAFTLESS_VERSION=v0.1.1");
    expect(readme).toContain("docker run");
    expect(readme).toContain("minekube/craftless/.github/actions/setup-craftless@v0.1.1");
    expect(readme).not.toContain("setup-craftless@v0.1.0");
    expect(readme).toContain("Minecraft artifacts are downloaded into the workspace at runtime");
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
