# v0.1.1 Release Install Evidence Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Refresh public docs and evidence so install-script and reusable Action users target the first release containing installed driver-mod distribution.

**Architecture:** Treat release publication as an external distribution gate. Keep code unchanged except for doc/tests: README pins `v0.1.1`, Bun distribution tests guard that public example, checklist/evidence record the release assets and install smoke.

**Tech Stack:** GitHub Releases via `gh`, shell install script, Bun tests through `mise exec -- bun`, Gradle/mise gates.

---

### Task 1: Guard Public Release Examples

**Files:**
- Modify: `README.md`
- Modify: `playwright/src/distribution.test.ts`

- [x] **Step 1: Update README reusable Action example**

  Change:

  ```yaml
  - uses: minekube/craftless/.github/actions/setup-craftless@v0.1.0
  ```

  To:

  ```yaml
  - uses: minekube/craftless/.github/actions/setup-craftless@v0.1.1
  ```

- [x] **Step 2: Add distribution test coverage**

  Add assertions to `README exposes install, Docker, and GitHub Actions quickstarts`:

  ```ts
  expect(readme).toContain("CRAFTLESS_VERSION=v0.1.1");
  expect(readme).toContain("minekube/craftless/.github/actions/setup-craftless@v0.1.1");
  expect(readme).not.toContain("setup-craftless@v0.1.0");
  ```

- [x] **Step 3: Run focused Bun test**

  Run:

  ```sh
  mise exec -- bun test playwright/src/distribution.test.ts
  ```

  Expected: PASS.

### Task 2: Verify Published v0.1.1 Release

**Files:**
- Create: `docs/superpowers/evidence/2026-06-28-v011-release-install-evidence.md`
- Modify: `docs/project-completion-checklist.md`

- [x] **Step 1: Check release workflow**

  Run:

  ```sh
  gh run view 28316490956 --json status,conclusion,headSha,url
  ```

  Expected: `status` is `completed`, `conclusion` is `success`, `headSha` is
  `bc9e630c1c4d250584b1b5999d717b3dd17d25d3`.

- [x] **Step 2: Check release assets**

  Run:

  ```sh
  gh release list --limit 5
  gh release view v0.1.1 --json tagName,url,assets,targetCommitish,publishedAt
  ```

  Expected: assets include `craftless-0.1.1.tar`,
  `craftless-0.1.1.zip`, and `SHA256SUMS`.

- [x] **Step 3: Run install-script smoke**

  Run:

  ```sh
  tmp="$(mktemp -d /tmp/craftless-v011-install-smoke.XXXXXX)" &&
    CRAFTLESS_VERSION=v0.1.1 CRAFTLESS_INSTALL_DIR="$tmp/bin" CRAFTLESS_HOME="$tmp/home" ./install.sh &&
    "$tmp/bin/craftless" server start --once --port 0 --workspace "$tmp/workspace" &&
    curl -fsSL https://github.com/minekube/craftless/releases/download/v0.1.1/craftless-0.1.1.tar -o "$tmp/craftless-0.1.1.tar" &&
    tar -tf "$tmp/craftless-0.1.1.tar" | grep -q '^craftless-0.1.1/mods/craftless-driver-fabric.jar$'
  ```

  Expected: installed binary prints `{"ok":true,...}` and tar inspection
  finds `mods/craftless-driver-fabric.jar`.

- [x] **Step 4: Record checklist and evidence**

  Update Phase 25 and final completion gate install/release bullets to cite
  `v0.1.1`, the release workflow run, install smoke, and packaged driver mod.

### Task 3: Verify, Commit, Push

- [x] **Step 1: Run local gates**

  ```sh
  git diff --check
  mise exec -- bun test playwright/src/distribution.test.ts
  mise run ci
  ```

- [x] **Step 2: Commit and push**

  ```sh
  git add README.md playwright/src/distribution.test.ts docs/project-completion-checklist.md docs/superpowers/specs/2026-06-28-104-v011-release-install-evidence-design.md docs/superpowers/plans/2026-06-28-104-v011-release-install-evidence-plan.md docs/superpowers/evidence/2026-06-28-v011-release-install-evidence.md
  git commit -m "docs: refresh v0.1.1 install evidence"
  git push origin main
  ```

## Self-Review

- Spec coverage: README, tests, release asset evidence, install smoke, and
  checklist updates are covered.
- Placeholder scan: no TODO/TBD placeholders.
- Static gameplay scan: this plan changes release evidence only.
