# Version Support Completion Gate Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Prevent unsupported latest/older runtime lanes from satisfying final Craftless completion.

**Architecture:** Add a docs guard in the Fabric module tests that reads the final completion gate and rejects support-boundary wording. Update AGENTS and the project checklist so unsupported compatibility lanes remain diagnostic evidence only, while final completion requires runnable latest and representative older support evidence.

**Tech Stack:** Kotlin/JVM, Gradle via mise, Kotlin test, Markdown.

---

### Task 1: Add Red Completion Gate Guard

**Files:**
- Modify: `driver-fabric/src/test/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/FabricDriverModuleTest.kt`

- [x] **Step 1: Add docs guard**

  Add a test named
  `completion gate does not accept unsupported version lanes as support`.

  It must read `docs/project-completion-checklist.md`, extract the section
  from `## Final Completion Gate` to the end of the file, and assert:

  - it does not contain `accepted support boundary`;
  - it does not contain `unsupported lane` as completion language;
  - it does contain `runnable support evidence`;
  - it contains `latest`;
  - it contains `representative older`.

- [x] **Step 2: Run red guard**

  ```sh
  mise exec -- gradle :driver-fabric:test --tests '*FabricDriverModuleTest.completion gate does not accept unsupported version lanes as support*'
  ```

  Expected: fails before implementation because the final gate currently
  accepts an explicitly accepted support boundary.

### Task 2: Tighten Final Gate Wording

**Files:**
- Modify: `AGENTS.md`
- Modify: `docs/project-completion-checklist.md`

- [x] **Step 1: Add Phase 91 to AGENTS**

  Add Phase 91 to the ordered sequence and explain that unsupported latest/older
  lanes are diagnostic only and cannot satisfy completion.

- [x] **Step 2: Update final completion gate**

  Replace the final gate sentence that allows "requested support or an
  explicitly accepted support boundary" with wording that requires runnable
  support evidence for both latest and representative older runtime lanes.

- [x] **Step 3: Preserve diagnostic lane history**

  Do not rewrite Phase 50 or Phase 66 as supported. Leave those historical
  sections explicit that the lanes remain unsupported diagnostics.

### Task 3: Update Governance And Evidence

**Files:**
- Modify: `docs/project-completion-checklist.md`
- Create: `docs/superpowers/evidence/2026-06-28-version-support-completion-gate.md`

- [x] **Step 1: Add checklist section**
- [x] **Step 2: Record red and green evidence**

### Task 4: Final Verification And Push

**Files:**
- All modified files from previous tasks

- [x] **Step 1: Run focused green test**

  ```sh
  mise exec -- gradle :driver-fabric:test --tests '*FabricDriverModuleTest.completion gate does not accept unsupported version lanes as support*'
  ```

- [x] **Step 2: Run source scan and local gates**

  ```sh
  rg -n "accepted support boundary|unsupported lane.*completion|runnable support evidence" docs/project-completion-checklist.md AGENTS.md
  git diff --check
  mise exec -- gradle :driver-fabric:test
  ```

- [ ] **Step 3: Commit and push**

  ```sh
  git add AGENTS.md docs/project-completion-checklist.md docs/superpowers/specs/2026-06-28-91-version-support-completion-gate-design.md docs/superpowers/plans/2026-06-28-91-version-support-completion-gate-plan.md docs/superpowers/evidence/2026-06-28-version-support-completion-gate.md driver-fabric/src/test/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/FabricDriverModuleTest.kt
  git commit -m "docs: tighten version support completion gate"
  git push origin main
  ```

## Self-Review

- Spec coverage: guard, final gate wording, governance, and verification are
  covered.
- Placeholder scan: no TBD/TODO placeholders.
- Scope: no new support claim, compiled lane, gameplay action, route family, or
  CLI catalog.
