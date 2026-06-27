# Public Agent Live Co-Play Guidance Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make future public-agent live co-play runs use generated Craftless API schemas correctly and avoid false stop-command handling.

**Architecture:** Keep product code and public API unchanged. Update only repo-local agent skill guidance, docs, AGENTS guardrails, and checklist status.

**Tech Stack:** Markdown, Agent Skills, Superpowers project docs, git diff verification.

---

### Task 1: Update Public Gameplay Agent Skill

**Files:**
- Modify: `.agents/skills/craftless-public-gameplay-agent/SKILL.md`

- [x] **Step 1: Add navigation schema guidance**

Document that agents must read generated OpenAPI schemas and that current
block navigation uses:

```json
{
  "action": "navigation.plan",
  "arguments": {
    "goal": {
      "kind": "block",
      "position": { "x": 12, "y": 64, "z": -8 },
      "radius": 3.0
    }
  }
}
```

- [x] **Step 2: Add live co-play stop handling guidance**

Document that standalone `stop`, `stopp`, `halt`, and `stop following` are stop
commands, but instruction text such as "follow me until I say stop" means the
agent should continue following until a later clear stop command arrives.

### Task 2: Register Phase 64

**Files:**
- Modify: `AGENTS.md`
- Modify: `docs/agent-skills.md`
- Modify: `docs/project-completion-checklist.md`
- Create: `docs/superpowers/specs/2026-06-28-64-public-agent-live-coplay-guidance-design.md`
- Create: `docs/superpowers/plans/2026-06-28-64-public-agent-live-coplay-guidance-plan.md`

- [x] **Step 1: Add AGENTS phase entry**

Register Phase 64 after Phase 63 and state that it changes only agent
guidance for generated public API live co-play.

- [x] **Step 2: Add checklist status**

Mark Phase 64 complete after the skill and docs are updated. Keep final
completion open on Robin's Minecraft chat confirmation.

### Task 3: Verify And Commit

**Files:**
- Verify: changed markdown files

- [x] **Step 1: Run whitespace verification**

Run:

```bash
git diff --check
```

Expected: no output and exit code 0.

- [x] **Step 2: Commit and push**

Run:

```bash
git add AGENTS.md docs/agent-skills.md docs/project-completion-checklist.md docs/superpowers/specs/2026-06-28-64-public-agent-live-coplay-guidance-design.md docs/superpowers/plans/2026-06-28-64-public-agent-live-coplay-guidance-plan.md .agents/skills/craftless-public-gameplay-agent/SKILL.md
git commit -m "docs: guide public agents for live coplay"
git push origin main
```
