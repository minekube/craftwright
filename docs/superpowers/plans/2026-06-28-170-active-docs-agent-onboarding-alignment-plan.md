# Active Docs Agent Onboarding Alignment Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Align active public docs and repo-local agent guidance around the generated OpenAPI/public-agent workflow.

**Architecture:** This is a docs-governance slice. README owns public onboarding, `.agents/skills/craftless-public-gameplay-agent/SKILL.md` owns agent execution rules, and `docs/project-completion-checklist.md` owns active gate status.

**Tech Stack:** Markdown docs, ripgrep checks, Bun Playwright distribution tests through `mise`, Git diff whitespace checks.

---

### Task 1: Add README Agent Usage

**Files:**
- Modify: `README.md`

- [x] **Step 1: Insert an `Agent Usage` section**

Add a section after generated CLI aliases that tells agents to discover
`GET /openapi.json`, fetch `GET /clients/{id}/openapi.json`, treat
`x-craftless-actions` and `x-craftless-resources` as authority, use
`/actions` and `/resources` as projections, subscribe to SSE, invoke only
advertised actions, and reject internals/scenario shortcuts as proof.

- [x] **Step 2: Verify active-doc wording**

Run:

```sh
rg -n "<old-product-name>|<old-domain>|TypeScript SDK|typescript sdk|static (action|gameplay)|static.*catalog|scenario shortcut|hand-written gameplay|sendChat|player/sendChat|task\\.survival" README.md AGENTS.md docs/agent-operating-contract.md docs/agent-module-contracts.md docs/roadmap.md docs/final-gameplay-runbook.md docs/agent-skills.md .agents/skills/craftless-public-gameplay-agent/SKILL.md -S
```

Expected: any matches are negative guardrails or historical references, not
active product instructions.

### Task 2: Update Completion Tracking

**Files:**
- Modify: `docs/project-completion-checklist.md`
- Modify: `docs/superpowers/phase-index.md`
- Create: `docs/superpowers/evidence/2026-06-28-active-docs-agent-onboarding-alignment.md`

- [x] **Step 1: Mark governance and onboarding docs gates**

Update the active completion board to record Phase 170 evidence for README,
roadmap/checklist, active agent skills, and GitHub Action/agent onboarding
docs.

- [x] **Step 2: Add Phase 170 to the phase index**

Record that active docs now share the generated OpenAPI/public-agent workflow
while binding-exit, multi-version, and final gameplay remain open.

- [x] **Step 3: Run verification**

Run:

```sh
mise exec -- bun test playwright
git diff --check
```

Expected: both exit `0`.

- [x] **Step 4: Commit and push**

Run:

```sh
git add README.md docs/project-completion-checklist.md docs/superpowers/phase-index.md docs/superpowers/specs/2026-06-28-170-active-docs-agent-onboarding-alignment-design.md docs/superpowers/plans/2026-06-28-170-active-docs-agent-onboarding-alignment-plan.md docs/superpowers/evidence/2026-06-28-active-docs-agent-onboarding-alignment.md
git commit -m "docs: align active agent onboarding"
git push origin main
```
