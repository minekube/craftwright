# Codex Evidence Completion Gate Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make Codex-verifiable public API/CLI evidence the final Craftless completion gate.

**Architecture:** Keep historical Robin-confirmation artifacts as diagnostic
evidence, but make active docs and checklist require CI, distribution,
compatibility, and final public gameplay evidence instead. Do not change public
gameplay API breadth.

**Tech Stack:** Markdown docs, AGENTS.md, repo-local skills, mise verification.

---

### Task 1: Align Active Governance Docs

**Files:**
- Modify: `AGENTS.md`
- Modify: `README.md`
- Modify: `docs/roadmap.md`
- Modify: `docs/final-gameplay-runbook.md`
- Modify: `.agents/skills/craftless-public-gameplay-agent/SKILL.md`
- Modify: `docs/project-completion-checklist.md`

- [x] **Step 1: Replace current Robin-required completion language**

  Update active docs so completion requires Codex-verifiable public API/CLI
  evidence and no longer requires Robin chat confirmation.

- [x] **Step 2: Preserve historical traceability**

  Leave older dated specs and evidence files intact unless they are presented
  as current source of truth.

- [x] **Step 3: Verify active docs**

  Run:

  ```sh
  git diff --check
  rg -n "Robin writes|Robin confirms|goal may be completed|required Robin|must.*Robin|Minecraft chat confirmation.*required" README.md AGENTS.md docs/roadmap.md docs/final-gameplay-runbook.md .agents/skills/craftless-public-gameplay-agent/SKILL.md -S
  ```

  Expected: `git diff --check` exits `0`; the focused search prints no current
  requirement that Robin chat confirmation is needed for completion.

### Task 2: Reverify And Push

**Files:**
- Modify: none beyond Task 1

- [x] **Step 1: Run focused architecture verification**

  Run:

  ```sh
  mise run architecture-check
  ```

  Expected: exit `0`.

- [ ] **Step 2: Commit and push**

  Run:

  ```sh
  git add AGENTS.md README.md docs/roadmap.md docs/final-gameplay-runbook.md .agents/skills/craftless-public-gameplay-agent/SKILL.md docs/project-completion-checklist.md docs/superpowers/specs/2026-06-28-65-codex-evidence-completion-gate-design.md docs/superpowers/plans/2026-06-28-65-codex-evidence-completion-gate-plan.md
  git commit -m "docs: require codex evidence for completion gate"
  git push origin main
  ```

  Expected: push succeeds.

- [ ] **Step 3: Monitor CI**

  Run:

  ```sh
  gh run list --repo minekube/craftless --branch main --limit 5 --json databaseId,headSha,status,conclusion,name,event,createdAt
  ```

  Expected: identify the pushed commit's CI run and watch it to completion.
