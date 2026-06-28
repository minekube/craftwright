# Packaged Driver Mod Distribution Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Bundle the Craftless Fabric driver mod into the packaged runtime context and wire CLI server start to pass driver-mod configuration into the daemon.

**Architecture:** Keep `driver-fabric` outside the daemon and CLI compile classpath. The package task builds `:driver-fabric:remapJar` and stages the remapped jar as a runtime file; Docker exposes that file through `CRAFTLESS_FABRIC_DRIVER_MOD`. CLI server start passes the provided environment map into `ConfiguredClientRuntimeDriverModProvider`.

**Tech Stack:** Kotlin/JVM tests, mise tasks, Dockerfile, Gradle remapJar, Markdown evidence.

---

### Task 1: Add Red CLI Env Propagation Test

**Files:**
- Modify: `cli/src/test/kotlin/com/minekube/craftless/cli/CraftlessCliTest.kt`

- [x] **Step 1: Add test**

  Add a test that starts the server once with a workspace and
  `env = mapOf("CRAFTLESS_FABRIC_DRIVER_MOD" to driverMod.toString())`,
  then creates a client during `afterStart` and verifies the cached driver mod
  appears under `cache/mods/craftless`.

- [x] **Step 2: Run red test**

  ```sh
  mise exec -- gradle :cli:test --tests '*CraftlessCliTest.server start forwards configured fabric driver mod environment*'
  ```

  Expected: fails because `runServerStart` ignores the supplied env map.

### Task 2: Pass CLI Env Into Daemon Provider

**Files:**
- Modify: `cli/src/main/kotlin/com/minekube/craftless/cli/Main.kt`

- [x] **Step 1: Thread env through server start**

  Pass the `env` map from `CraftlessCli.run` into `runServerStart`, then into
  `ConfiguredClientRuntimeDriverModProvider(environment = env)` when creating
  `LocalSessionApiServer.inMemory`.

- [x] **Step 2: Run focused green**

  ```sh
  mise exec -- gradle :cli:test --tests '*CraftlessCliTest.server start forwards configured fabric driver mod environment*'
  ```

### Task 3: Guard Package Task And Docker Env

**Files:**
- Modify: `protocol/src/test/kotlin/com/minekube/craftless/protocol/NamespacePolicyTest.kt`
- Modify: `.mise.toml`
- Modify: `Dockerfile`

- [x] **Step 1: Add package policy test**

  Add a test that reads `.mise.toml` and `Dockerfile`, then asserts:

  - `package-cli` invokes `:driver-fabric:remapJar`;
  - the task stages `craftless-driver-fabric.jar`;
  - Docker sets `CRAFTLESS_FABRIC_DRIVER_MOD=/opt/craftless/mods/craftless-driver-fabric.jar`.

- [x] **Step 2: Run red policy test**

  ```sh
  mise exec -- gradle :protocol:test --tests '*NamespacePolicyTest.package cli stages craftless fabric driver mod for docker runtime*'
  ```

- [x] **Step 3: Update package task and Dockerfile**

  Make `mise run package-cli` build `:cli:distZip :cli:distTar
  :driver-fabric:remapJar`, create `build/docker/craftless/mods`, and copy
  `driver-fabric/build/libs/driver-fabric-*.jar` to
  `build/docker/craftless/mods/craftless-driver-fabric.jar`.

  Add:

  ```dockerfile
  ENV CRAFTLESS_FABRIC_DRIVER_MOD=/opt/craftless/mods/craftless-driver-fabric.jar
  ```

- [x] **Step 4: Run green policy test**

  ```sh
  mise exec -- gradle :protocol:test --tests '*NamespacePolicyTest.package cli stages craftless fabric driver mod for docker runtime*'
  ```

### Task 4: Verify Package Smoke And Record Evidence

**Files:**
- Modify: `AGENTS.md`
- Modify: `docs/project-completion-checklist.md`
- Create: `docs/superpowers/evidence/2026-06-28-packaged-driver-mod-distribution.md`

- [x] **Step 1: Register Phase 97**

  Add Phase 97 to `AGENTS.md` and the project checklist.

- [x] **Step 2: Run local gates**

  ```sh
  git diff --check
  mise exec -- gradle :cli:test --tests '*CraftlessCliTest.server start forwards configured fabric driver mod environment*'
  mise exec -- gradle :protocol:test --tests '*NamespacePolicyTest.package cli stages craftless fabric driver mod for docker runtime*'
  mise exec -- gradle :cli:ktlintCheck :cli:detekt :protocol:ktlintCheck :protocol:detekt
  mise run package-cli
  test -f build/docker/craftless/mods/craftless-driver-fabric.jar
  ```

- [x] **Step 3: Record evidence**

  Write red/green and local gate outcomes to
  `docs/superpowers/evidence/2026-06-28-packaged-driver-mod-distribution.md`.

### Task 5: Commit And Push

**Files:**
- All modified files from Tasks 1-4

- [x] **Step 1: Commit and push**

  ```sh
  git add .mise.toml Dockerfile AGENTS.md cli/src/main/kotlin/com/minekube/craftless/cli/Main.kt cli/src/test/kotlin/com/minekube/craftless/cli/CraftlessCliTest.kt protocol/src/test/kotlin/com/minekube/craftless/protocol/NamespacePolicyTest.kt docs/project-completion-checklist.md docs/superpowers/specs/2026-06-28-97-packaged-driver-mod-distribution-design.md docs/superpowers/plans/2026-06-28-97-packaged-driver-mod-distribution-plan.md docs/superpowers/evidence/2026-06-28-packaged-driver-mod-distribution.md
  git commit -m "build: package fabric driver mod for runtime"
  git push origin main
  ```

## Self-Review

- Spec coverage: CLI env, package task, Docker env, tests, smoke, evidence,
  and push are covered.
- Placeholder scan: no TODO/TBD placeholders.
- Scope: no public gameplay actions, static descriptors, version-specific API,
  compile dependency from daemon/CLI to driver-fabric, or completion claim.
