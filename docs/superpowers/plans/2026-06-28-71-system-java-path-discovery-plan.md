# System Java PATH Discovery Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the system Java runtime provider discover compatible Java executables from `PATH` without weakening mise-only repository tooling.

**Architecture:** Keep Java discovery in the supervisor/runtime layer. Add `PATH` candidate enumeration to `SystemJavaRuntimeProvider`, and keep all validation in the existing bounded Java validator.

**Tech Stack:** Kotlin/JVM, Gradle through mise, `ProcessBuilder` Java validation.

---

### Task 1: Add PATH Discovery Test

**Files:**
- Modify: `daemon/src/test/kotlin/com/minekube/craftless/daemon/JavaRuntimeResolverTest.kt`

- [x] **Step 1: Write the failing test**

  Add:

  ```kotlin
  @Test
  fun `discovers system Java from PATH without JAVA_HOME`() {
      val pathRoot = Files.createTempDirectory("craftless-system-path-java")
      val pathJava = pathRoot.resolve("java")
      fakeJava(pathJava, "25.0.3")

      val selection =
          JavaRuntimeResolver()
              .resolve(
                  requirement = java25Requirement(),
                  context =
                      JavaRuntimeDiscoveryContext(
                          environment = mapOf("PATH" to pathRoot.toString()),
                          home = Files.createTempDirectory("craftless-empty-home"),
                      ),
              )

      assertEquals(JavaRuntimeSelectionStatus.SELECTED, selection.status)
      assertEquals(JavaRuntimeProviderKind.SYSTEM, selection.selected?.provider)
      assertEquals(pathJava.toString(), selection.selected?.executable)
  }
  ```

- [x] **Step 2: Verify RED**

  Run:

  ```sh
  mise exec -- gradle :daemon:test --tests '*JavaRuntimeResolverTest.discovers system Java from PATH without JAVA_HOME'
  ```

  Expected before implementation: FAIL at the selected-status assertion because
  the system provider does not scan `PATH`.

### Task 2: Implement PATH Candidate Discovery

**Files:**
- Modify: `daemon/src/main/kotlin/com/minekube/craftless/daemon/JavaRuntimeProvider.kt`

- [x] **Step 1: Add PATH candidates**

  In `SystemJavaRuntimeProvider.candidates`, split `PATH` with
  `File.pathSeparatorChar`, ignore blank entries, and add existing `java` and
  `java.exe` files from each entry to the candidate list.

- [x] **Step 2: Verify GREEN**

  Run:

  ```sh
  mise exec -- gradle :daemon:test --tests '*JavaRuntimeResolverTest.discovers system Java from PATH without JAVA_HOME'
  ```

  Expected after implementation: PASS.

### Task 3: Register Phase 71 And Verify

**Files:**
- Modify: `AGENTS.md`
- Modify: `docs/project-completion-checklist.md`
- Create: `docs/superpowers/specs/2026-06-28-71-system-java-path-discovery-design.md`
- Create: `docs/superpowers/plans/2026-06-28-71-system-java-path-discovery-plan.md`

- [x] **Step 1: Register governance**

  Add Phase 71 to `AGENTS.md` and the checklist as supervisor/runtime Java
  discovery only. State that it adds no gameplay surface and no new Minecraft
  support claim.

- [x] **Step 2: Run verification**

  Run:

  ```sh
  mise exec -- gradle :daemon:test --tests '*JavaRuntimeResolverTest*'
  git diff --check
  mise run architecture-check
  mise run ci
  ```

- [x] **Step 3: Commit, push, and verify CI**

  Run:

  ```sh
  git add AGENTS.md docs/project-completion-checklist.md docs/superpowers/specs/2026-06-28-71-system-java-path-discovery-design.md docs/superpowers/plans/2026-06-28-71-system-java-path-discovery-plan.md daemon/src/main/kotlin/com/minekube/craftless/daemon/JavaRuntimeProvider.kt daemon/src/test/kotlin/com/minekube/craftless/daemon/JavaRuntimeResolverTest.kt
  git commit -m "daemon: discover system Java from PATH"
  git push origin main
  gh run watch <latest-run-id> --repo minekube/craftless --exit-status
  ```

### Guardrails

- [x] No static gameplay action descriptor, route family, CLI gameplay command,
  Fabric descriptor/binding pair, or scenario shortcut is added.
- [x] Repository verification commands use `mise`.
- [x] Product runtime discovery does not require `mise` to be on `PATH`.
- [x] Java validation still uses `ProcessBuilder`, not shell execution.
