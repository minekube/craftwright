# Completion Audit And Binding Exit Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Align active completion docs with current evidence while keeping the transitional Fabric binding allowlist visible as the remaining completion blocker.

**Architecture:** Treat this as a governance and evidence audit. Update the root agent contract, project checklist, and evidence file; do not change runtime behavior or add gameplay breadth.

**Tech Stack:** Markdown docs, mise, Gradle, GitHub Actions.

---

### Task 1: Record Completion Audit Evidence

**Files:**
- Create: `docs/superpowers/evidence/2026-06-28-completion-audit-and-binding-exit.md`

- [x] **Step 1: Inspect the active goal**

  Run:

  ```sh
  get_goal
  ```

  Expected: the objective requires generated runtime graph/OpenAPI gameplay,
  multi-version evidence, Ktor, mise/Bun workflows, CLI/Docker/release
  usability, CI, and final public API/CLI gameplay; no Robin confirmation is
  required.

- [x] **Step 2: Inspect the transitional Fabric action allowlist**

  Run:

  ```sh
  cat docs/architecture/transitional-fabric-action-allowlist.txt
  ```

  Expected: the file lists the current bootstrap gameplay action ids and says
  they are transitional only.

- [x] **Step 3: Inspect the guard test**

  Run:

  ```sh
  sed -n '970,1020p' driver-fabric/src/test/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/FabricDriverModuleTest.kt
  ```

  Expected: tests enforce that hand-written Fabric gameplay descriptors match
  the transitional allowlist and are graph represented.

- [x] **Step 4: Write the audit evidence**

  Record verified gates, open gates, and the completion decision in
  `docs/superpowers/evidence/2026-06-28-completion-audit-and-binding-exit.md`.

### Task 2: Align Active Governance Docs

**Files:**
- Modify: `AGENTS.md`
- Modify: `docs/project-completion-checklist.md`

- [x] **Step 1: Add Phase 76 to `AGENTS.md`**

  Add Phase 76 after Phase 75, stating that it records current completion
  evidence and keeps transitional hand-written Fabric bindings as a blocker
  until generated runtime discovery owns the public gameplay surface.

- [x] **Step 2: Update stale checklist wording**

  In `docs/project-completion-checklist.md`, mark the current gameplay
  evidence as verified by Phase 75, remove active Robin-confirmation language
  from the latest gate text, and replace stale "Phase 68" final-gate wording
  with Phase 75 plus Phase 76 audit wording.

- [x] **Step 3: Add a Phase 76 checklist section**

  Add checked entries for the spec, plan, audit evidence, no-Robin completion
  rule, verified gate inventory, and remaining binding/multi-version blockers.

### Task 3: Verify And Push

**Files:**
- Modify: `docs/superpowers/plans/2026-06-28-76-completion-audit-and-binding-exit-plan.md`

- [x] **Step 1: Run local verification**

  Run:

  ```sh
  git diff --check
  mise run architecture-check
  mise run ci
  ```

  Expected: all commands exit `0`.

- [x] **Step 2: Commit and push**

  Run:

  ```sh
  git add AGENTS.md docs/project-completion-checklist.md docs/superpowers/specs/2026-06-28-76-completion-audit-and-binding-exit-design.md docs/superpowers/plans/2026-06-28-76-completion-audit-and-binding-exit-plan.md docs/superpowers/evidence/2026-06-28-completion-audit-and-binding-exit.md
  git commit -m "docs: audit completion blockers"
  git push origin main
  ```

- [x] **Step 3: Verify remote CI**

  Run:

  ```sh
  gh run list --repo minekube/craftless --branch main --limit 5 --json databaseId,headSha,status,conclusion,name,event,createdAt
  gh run watch <latest-run-id> --repo minekube/craftless --exit-status
  ```

  Expected: pushed commit's GitHub Actions CI exits successfully.

  Evidence: implementation commit `348672e` passed GitHub Actions `ci` run
  `28308547679`.
