# Latest Official Fabric Lane Boundary Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a minimal internal non-remap official Fabric lane module and reroute the latest/current probe to compile it.

**Architecture:** Keep `driver-fabric` as the verified Yarn/remap lane. Add `driver-fabric-official` as a separate Java 25/Fabric Loom official-boundary probe module with a tiny entrypoint and no gameplay bindings. Update the existing latest official mise task to compile that module and record status evidence.

**Tech Stack:** Gradle 9.6, Fabric Loom 1.17.12, Kotlin/JVM 2.4, Java 25 through mise, Fabric Loader 0.19.3, Fabric API 0.153.0+26.2.

---

### Task 1: Guard the official module boundary

**Files:**
- Modify: `driver-fabric/src/test/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/FabricDriverModuleTest.kt`

- [x] **Step 1: Write the failing architecture test**

Add:

```kotlin
@Test
fun `latest official lane probe uses separate non remap module boundary`()
```

The test reads `settings.gradle.kts`, `build.gradle.kts`, `.mise.toml`, and
future official module files. It asserts:

- `settings.gradle.kts` includes `"driver-fabric-official"`;
- root `build.gradle.kts` declares `id("net.fabricmc.fabric-loom")`;
- `.mise.toml` latest task invokes `:driver-fabric-official:compileKotlin`,
  `:driver-fabric-official:processResources`, and
  `:driver-fabric-official:jar`;
- `.mise.toml` latest task no longer invokes `:driver-fabric:compileKotlin`;
- `driver-fabric-official/build.gradle.kts` applies
  `net.fabricmc.fabric-loom`;
- `driver-fabric-official/build.gradle.kts` does not contain
  `fabric-loom-remap`;
- `driver-fabric-official/build.gradle.kts` does not contain
  `craftless.fabric.yarnMappings`.

- [x] **Step 2: Run the test red**

```sh
mise exec -- gradle :driver-fabric:test --tests '*FabricDriverModuleTest.latest official lane probe uses separate non remap module boundary'
```

Expected before implementation: failure because the official module does not
exist and the probe still targets `:driver-fabric`.

### Task 2: Add the official module

**Files:**
- Modify: `settings.gradle.kts`
- Modify: `build.gradle.kts`
- Create: `driver-fabric-official/AGENTS.md`
- Create: `driver-fabric-official/build.gradle.kts`
- Create: `driver-fabric-official/src/main/kotlin/com/minekube/craftless/driver/fabric/official/CraftlessFabricOfficialEntrypoint.kt`
- Create: `driver-fabric-official/src/main/resources/fabric.mod.json`

- [x] **Step 1: Register the module and plugin**

Add `driver-fabric-official` to `settings.gradle.kts`, and add
`id("net.fabricmc.fabric-loom") version "1.17.12" apply false` to the root
plugin block.

- [x] **Step 2: Create module instructions**

Create `driver-fabric-official/AGENTS.md` explaining that this module is an
internal latest/current official lane boundary only, not a support claim or
public gameplay surface.

- [x] **Step 3: Create module build file**

Use Java 25 toolchain, apply non-remap Loom, depend on Minecraft `26.2`,
Fabric Loader `0.19.3`, and Fabric API `0.153.0+26.2`, and avoid Yarn
mappings.

- [x] **Step 4: Add a minimal entrypoint**

Implement a tiny `ClientModInitializer` entrypoint with no gameplay actions.

- [x] **Step 5: Add Fabric metadata**

Add `fabric.mod.json` with the official entrypoint, Java `>=25`, Minecraft
`26.2`, Fabric Loader `>=0.19.3`, and Fabric API `>=0.153.0+26.2`.

### Task 3: Reroute the latest probe and record evidence

**Files:**
- Modify: `.mise.toml`
- Modify: `docs/project-completion-checklist.md`
- Create: `docs/superpowers/evidence/2026-06-28-latest-official-fabric-lane-boundary.md`

- [x] **Step 1: Reroute probe task**

Change `fabric-lane-check-latest-official` to compile
`:driver-fabric-official:compileKotlin`,
`:driver-fabric-official:processResources`, and `:driver-fabric-official:jar`
under Java 25.

- [x] **Step 2: Run focused tests**

```sh
mise exec -- gradle :driver-fabric:test --tests '*FabricDriverModuleTest.latest official lane probe uses separate non remap module boundary' --tests '*FabricDriverModuleTest.mise latest lane probe uses official mapping boundary not yarn remap lane*'
```

- [x] **Step 3: Run latest official probe**

```sh
mise run fabric-lane-check-latest-official
```

Expected: `build/reports/fabric-lane-check-latest-official.status` contains
`status=compiled`.

- [ ] **Step 4: Verify and commit**

```sh
git diff --check
git add .mise.toml AGENTS.md README.md settings.gradle.kts build.gradle.kts driver-fabric-official driver-fabric/src/test/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/FabricDriverModuleTest.kt docs/project-completion-checklist.md docs/superpowers/specs/2026-06-28-146-latest-official-fabric-lane-boundary-design.md docs/superpowers/plans/2026-06-28-146-latest-official-fabric-lane-boundary-plan.md docs/superpowers/evidence/2026-06-28-latest-official-fabric-lane-boundary.md
git commit -m "build: add latest official fabric lane boundary"
git push origin main
```
