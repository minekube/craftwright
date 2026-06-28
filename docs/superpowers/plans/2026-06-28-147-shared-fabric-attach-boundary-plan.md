# Shared Fabric Attach Boundary Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extract Fabric self-attach/Ktor loopback transport into a shared module consumed by both Fabric lanes, then wire the official lane to shared attach without adding gameplay bindings or support claims.

**Architecture:** Keep `driver-fabric` as the verified Yarn/remap runtime lane and `driver-fabric-official` as the latest/current official lane. Move version-neutral attach environment parsing, self-attach startup, loopback routes, and daemon replacement calls into `driver-fabric-attach`. The official lane starts shared attach with a metadata-only backend until generic discovery/projection is shared.

**Tech Stack:** Kotlin/JVM, Gradle 9.6, Fabric Loom, Ktor Server CIO, Ktor Client CIO, kotlinx.serialization, mise-managed Java/Gradle.

---

### Task 1: Add architecture guard tests

**Files:**
- Modify: `driver-fabric/src/test/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/FabricDriverModuleTest.kt`

- [ ] **Step 1: Write the failing guard test**

Add a test named:

```kotlin
@Test
fun `official lane uses shared fabric attach boundary without depending on yarn remap lane`()
```

The test should read:

- `settings.gradle.kts`;
- `driver-fabric/build.gradle.kts`;
- `driver-fabric-official/build.gradle.kts`;
- `driver-fabric-official/src/main/kotlin/com/minekube/craftless/driver/fabric/official/CraftlessFabricOfficialEntrypoint.kt`.

Assert:

- settings includes `"driver-fabric-attach"`;
- `driver-fabric/build.gradle.kts` contains `project(":driver-fabric-attach")`;
- `driver-fabric-official/build.gradle.kts` contains `project(":driver-fabric-attach")`;
- `driver-fabric-official/build.gradle.kts` does not contain `project(":driver-fabric")`;
- the official entrypoint references `FabricDriverSelfAttach.startFromEnvironment`;
- no official-lane file contains public gameplay descriptor/catalog names.

- [ ] **Step 2: Run the guard test red**

```sh
mise exec -- gradle :driver-fabric:test --tests '*FabricDriverModuleTest.official lane uses shared fabric attach boundary without depending on yarn remap lane'
```

Expected before implementation: failure because `driver-fabric-attach` does
not exist and the official entrypoint does not start shared attach.

### Task 2: Create the shared attach module

**Files:**
- Modify: `settings.gradle.kts`
- Create: `driver-fabric-attach/AGENTS.md`
- Create: `driver-fabric-attach/build.gradle.kts`
- Move: `driver-fabric/src/main/kotlin/com/minekube/craftless/driver/fabric/FabricDriverAttachEnvironment.kt`
- Move: `driver-fabric/src/main/kotlin/com/minekube/craftless/driver/fabric/FabricDriverLoopbackEndpoint.kt`
- Move: `driver-fabric/src/main/kotlin/com/minekube/craftless/driver/fabric/FabricDriverSelfAttach.kt`
- Move: `driver-fabric/src/test/kotlin/com/minekube/craftless/driver/fabric/FabricDriverSelfAttachTest.kt`

- [ ] **Step 1: Register the module**

Add `driver-fabric-attach` to `settings.gradle.kts`.

- [ ] **Step 2: Add module instructions**

Create `driver-fabric-attach/AGENTS.md` stating that the module owns
version-neutral Fabric attach/loopback transport only. It must not contain
gameplay bindings, per-version route trees, or public action catalogs.

- [ ] **Step 3: Add the Gradle build**

Create `driver-fabric-attach/build.gradle.kts` with:

```kotlin
dependencies {
    implementation(project(":driver-api"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    implementation("io.ktor:ktor-client-core-jvm:3.5.0")
    implementation("io.ktor:ktor-client-cio-jvm:3.5.0")
    implementation("io.ktor:ktor-server-core-jvm:3.5.0")
    implementation("io.ktor:ktor-server-cio-jvm:3.5.0")

    testImplementation(project(":daemon"))
}
```

- [ ] **Step 4: Move attach sources**

Move the three attach source files into
`driver-fabric-attach/src/main/kotlin/com/minekube/craftless/driver/fabric/attach/`.
Change their package to:

```kotlin
package com.minekube.craftless.driver.fabric.attach
```

Make the classes/functions public where they are consumed by other modules.

- [ ] **Step 5: Move attach tests**

Move `FabricDriverSelfAttachTest.kt` into
`driver-fabric-attach/src/test/kotlin/com/minekube/craftless/driver/fabric/attach/`
and update the package/imports.

- [ ] **Step 6: Verify shared attach tests**

```sh
mise exec -- gradle :driver-fabric-attach:test
```

Expected: moved self-attach tests pass.

### Task 3: Rewire the Yarn/remap lane to shared attach

**Files:**
- Modify: `driver-fabric/build.gradle.kts`
- Modify: `driver-fabric/src/main/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/FabricCurrentLaneBootstrap.kt`

- [ ] **Step 1: Add module dependency**

Add:

```kotlin
implementation(project(":driver-fabric-attach"))
include(project(":driver-fabric-attach"))
```

to `driver-fabric/build.gradle.kts` using the existing dependency/include style.

- [ ] **Step 2: Update imports**

Update `FabricCurrentLaneBootstrap.kt` to import:

```kotlin
import com.minekube.craftless.driver.fabric.attach.FabricDriverSelfAttach
```

- [ ] **Step 3: Verify current lane behavior**

```sh
mise exec -- gradle :driver-fabric:test
```

Expected: existing Fabric driver tests pass.

### Task 4: Wire the official lane to shared attach

**Files:**
- Modify: `driver-fabric-official/build.gradle.kts`
- Modify: `driver-fabric-official/src/main/kotlin/com/minekube/craftless/driver/fabric/official/CraftlessFabricOfficialEntrypoint.kt`
- Create: `driver-fabric-official/src/main/kotlin/com/minekube/craftless/driver/fabric/official/OfficialFabricDriverBackend.kt`

- [ ] **Step 1: Add dependencies**

Add dependencies on:

```kotlin
implementation(project(":driver-api"))
implementation(project(":driver-runtime"))
implementation(project(":driver-fabric-attach"))
```

Do not add `project(":driver-fabric")`.

- [ ] **Step 2: Add metadata-only backend**

Create `OfficialFabricDriverBackend` implementing the stable runtime backend
contract. It should return runtime metadata for the official lane, expose no
gameplay actions until generic discovery/projection is shared, and reject
generic gameplay invocation with a structured unavailable result.

- [ ] **Step 3: Start shared attach**

Update `CraftlessFabricOfficialEntrypoint` to create the metadata-only backend
and call:

```kotlin
FabricDriverSelfAttach.startFromEnvironment(
    sessionFactory = { clientId ->
        BackendDriverSession(clientId = clientId, backend = backend)
    },
)
```

- [ ] **Step 4: Verify official compile boundary**

```sh
mise exec -- gradle :driver-fabric-official:compileKotlin :driver-fabric-official:processResources :driver-fabric-official:jar
```

Expected: all official lane tasks pass.

### Task 5: Refresh evidence and docs

**Files:**
- Modify: `docs/project-completion-checklist.md`
- Modify: `README.md`
- Create: `docs/superpowers/evidence/2026-06-28-shared-fabric-attach-boundary.md`

- [ ] **Step 1: Run the latest official probe**

```sh
mise run fabric-lane-check-latest-official
```

Expected: `build/reports/fabric-lane-check-latest-official.status` contains
`status=compiled`.

- [ ] **Step 2: Run lint and whitespace checks**

```sh
mise exec -- gradle lint
git diff --check
```

Expected: both commands pass.

- [ ] **Step 3: Update docs**

Record that the official lane now shares attach/runtime handoff code, while
still lacking launch/attach evidence, generated gameplay discovery breadth,
packaged 26.x manifest support, and final latest/current gameplay support.

- [ ] **Step 4: Commit and push**

```sh
git add AGENTS.md driver-fabric/AGENTS.md driver-fabric-official/AGENTS.md driver-runtime/AGENTS.md daemon/AGENTS.md cli/AGENTS.md docs/AGENTS.md testkit/AGENTS.md bridge-hmc/AGENTS.md playwright/AGENTS.md settings.gradle.kts driver-fabric-attach driver-fabric driver-fabric-official docs/project-completion-checklist.md README.md docs/superpowers/specs/2026-06-28-147-shared-fabric-attach-boundary-design.md docs/superpowers/plans/2026-06-28-147-shared-fabric-attach-boundary-plan.md docs/superpowers/evidence/2026-06-28-shared-fabric-attach-boundary.md
git commit -m "refactor: share fabric attach boundary"
git push origin main
```
