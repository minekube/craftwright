# Representative Older Release Lane Evidence Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a known older Minecraft release to the compatibility matrix as explicit unsupported evidence.

**Architecture:** Keep the compiled Fabric client lane at `1.21.6`. Add `1.20.6` as a real older-release runtime input in the private compatibility matrix with `UNSUPPORTED/runtime-lane-missing`, using Mojang metadata for Java 21. Tests and docs must make clear this is evidence, not working older-version client support.

**Tech Stack:** Kotlin/JVM, Fabric runtime compatibility matrix, Gradle tests through mise, Markdown docs.

---

### Task 1: Add Failing Older-Lane Matrix Guard

**Files:**
- Modify: `driver-fabric/src/test/kotlin/com/minekube/craftless/driver/fabric/runtime/FabricCompatibilityMatrixTest.kt`

- [x] **Step 1: Verify Mojang metadata**

  Run:

  ```sh
  mise exec -- bun -e '
  const manifest = await (await fetch("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json")).json();
  const v = manifest.versions.find((version) => version.id === "1.20.6");
  if (!v) throw new Error("missing 1.20.6");
  const meta = await (await fetch(v.url)).json();
  console.log(JSON.stringify({ id: v.id, type: v.type, releaseTime: v.releaseTime, time: v.time, sha1: v.sha1, javaVersion: meta.javaVersion }, null, 2));
  '
  ```

  Expected: release `1.20.6` with Java major version `21`.

- [x] **Step 2: Write failing matrix test**

  Add a test expecting `1.20.6` to resolve to lane id
  `older-release-1-20-6`, status `UNSUPPORTED`, Java `21`, provider
  `no-compatible-client-lane`, and reason `runtime-lane-missing`.

- [x] **Step 3: Verify RED**

  Run:

  ```sh
  mise exec -- gradle :driver-fabric:test --tests '*FabricCompatibilityMatrixTest*'
  ```

  Expected: fails because `1.20.6` still resolves through the generic
  `fabric-unsupported-*` fallback.

### Task 2: Add Older Release Compatibility Lane

**Files:**
- Modify: `driver-fabric/src/main/kotlin/com/minekube/craftless/driver/fabric/runtime/FabricCompatibilityMatrix.kt`

- [x] **Step 1: Add matrix lane**

  Add `older-release-1-20-6` with `UNSUPPORTED`, Java `21`,
  `no-compatible-client-lane`, and `runtime-lane-missing`.

- [x] **Step 2: Verify GREEN**

  Run:

  ```sh
  mise exec -- gradle :driver-fabric:test --tests '*FabricCompatibilityMatrixTest*'
  ```

  Expected: pass.

### Task 3: Record Evidence And Checklist

**Files:**
- Modify: `AGENTS.md`
- Modify: `docs/project-completion-checklist.md`
- Create: `docs/superpowers/evidence/2026-06-28-representative-older-release-lane-evidence.md`

- [x] **Step 1: Register Phase 66**

  Add Phase 66 to `AGENTS.md` and state that the lane is unsupported evidence,
  not working older-version support.

- [x] **Step 2: Record evidence**

  Write the Mojang metadata command/output and focused matrix test output into
  the evidence file.

- [x] **Step 3: Update checklist**

  Add a Phase 66 section and update final-gate wording so completion remains
  gated by Codex evidence, not human chat confirmation.

### Task 4: Verify, Commit, Push, And Monitor

**Files:**
- Commit all Phase 66 files and code changes.

- [x] **Step 1: Run verification**

  Run:

  ```sh
  git diff --check
  mise run architecture-check
  mise run ci
  ```

- [ ] **Step 2: Commit and push**

  Run:

  ```sh
  git add AGENTS.md docs/project-completion-checklist.md docs/superpowers/specs/2026-06-28-66-representative-older-release-lane-evidence-design.md docs/superpowers/plans/2026-06-28-66-representative-older-release-lane-evidence-plan.md docs/superpowers/evidence/2026-06-28-representative-older-release-lane-evidence.md driver-fabric/src/main/kotlin/com/minekube/craftless/driver/fabric/runtime/FabricCompatibilityMatrix.kt driver-fabric/src/test/kotlin/com/minekube/craftless/driver/fabric/runtime/FabricCompatibilityMatrixTest.kt
  git commit -m "driver-fabric: record older release lane evidence"
  git push origin main
  ```

- [ ] **Step 3: Verify remote CI**

  Run:

  ```sh
  gh run list --repo minekube/craftless --branch main --limit 5 --json databaseId,headSha,status,conclusion,name,event,createdAt
  gh run watch <latest-run-id> --repo minekube/craftless --exit-status
  ```
