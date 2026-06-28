# README And Roadmap Evidence Alignment Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Align README and roadmap with current Phase 68 evidence without changing product behavior.

**Architecture:** This is a docs-only alignment pass. README becomes the concise public entrypoint, roadmap becomes the current product baseline, and AGENTS/checklist gain a traceable phase entry. Unsupported version lanes remain explicitly unsupported.

**Tech Stack:** Markdown, mise verification tasks.

---

### Task 1: Update Public README

**Files:**
- Modify: `README.md`

- [x] **Step 1: Rewrite the status and roadmap sections**

  Update README so it states the Phase 68 no-confirmation final gameplay gate
  has current evidence while project completion still requires the broader
  generic-discovery and multi-version completion audit.

- [x] **Step 2: Keep examples generated-API first**

  Keep example commands focused on supervisor OpenAPI, per-client OpenAPI,
  `/clients/{id}:run`, SSE, and adaptive CLI discovery. Do not add static
  gameplay command catalogs.

- [x] **Step 3: Check README for stale claims**

  Run:

  ```sh
  rg -n "server-provisioned|Iron Sword|Robin confirmation|required Robin|task\\.survival|\\.dev" README.md
  ```

  Expected: no stale product-surface matches. Any remaining match must be an
  explicit negative statement against server provisioning or static shortcuts.

### Task 2: Update Roadmap Baseline

**Files:**
- Modify: `docs/roadmap.md`

- [x] **Step 1: Replace diagnostic smoke baseline**

  Rewrite the current baseline so it describes current generated OpenAPI,
  runtime graph, Ktor, CLI, packaging, compatibility evidence, and public-agent
  gameplay evidence. Do not present provisioned iron-sword smoke as the current
  product proof.

- [x] **Step 2: Keep unsupported lanes honest**

  Document latest `26.2` and representative older `1.20.6` as verified
  runtime-resolution and compatibility-probe evidence with explicit
  `UNSUPPORTED/runtime-lane-missing` Fabric client lanes.

### Task 3: Update Governance Docs

**Files:**
- Modify: `AGENTS.md`
- Modify: `docs/project-completion-checklist.md`

- [x] **Step 1: Add Phase 69 to AGENTS.md**

  Add Phase 69 to the active phase list and describe it as a docs-only public
  README/roadmap evidence alignment.

- [x] **Step 2: Add Phase 69 to checklist**

  Add the spec/plan references and checklist items proving README, roadmap,
  and governance docs are aligned.

### Task 4: Verify

**Files:**
- Verify all changed files.

- [x] **Step 1: Run whitespace verification**

  Run:

  ```sh
  git diff --check
  ```

- [x] **Step 2: Run local architecture and CI gates**

  Run:

  ```sh
  mise run architecture-check
  mise run ci
  ```
