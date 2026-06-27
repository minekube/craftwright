# Scenario Shortcut Action Guard Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Prevent known survival-scenario shortcut action ids from being accepted as public Craftless action ids.

**Architecture:** Extend the existing protocol namespace policy instead of adding a separate scanner. The shared `isCraftlessActionId()` validator is the correct boundary because OpenAPI action descriptors, runtime graph operations, generated aliases, handles, events, and resource descriptors all depend on it.

**Tech Stack:** Kotlin/JVM, kotlinx.serialization DTOs, JUnit 5, Gradle through mise.

---

### Task 1: Add Protocol Guard

**Files:**
- Modify: `protocol/src/test/kotlin/com/minekube/craftless/protocol/NamespacePolicyTest.kt`
- Modify: `protocol/src/main/kotlin/com/minekube/craftless/protocol/OpenApiDocument.kt`

- [x] **Step 1: Write the failing policy test**

Add a test named `public action descriptors reject scenario shortcut action ids`
that asserts both `OpenApiAction` and `RuntimeOperationNode` reject these ids:

```kotlin
listOf(
    "find.tree",
    "find.cow",
    "mine.log",
    "collect.wood",
    "craft.sword",
    "craft.planks",
    "craft.table",
    "make.weapon",
    "kill.cow",
    "hunt.animal",
    "pickup.log",
    "equip.log",
    "build.house",
    "place.log",
)
```

- [x] **Step 2: Run the focused test for RED**

Run:

```sh
mise exec -- gradle :protocol:test --tests 'com.minekube.craftless.protocol.NamespacePolicyTest.public action descriptors reject scenario shortcut action ids'
```

Expected RED before implementation: `OpenApiAction(id = "find.tree")` is
accepted and the `assertFailsWith` assertion fails.

- [x] **Step 3: Add the shared validator blocklist**

In `OpenApiDocument.kt`, make `isCraftlessActionId()` also reject the exact
scenario shortcut ids listed in Step 1.

- [x] **Step 4: Run the focused test for GREEN**

Run:

```sh
mise exec -- gradle :protocol:test --tests 'com.minekube.craftless.protocol.NamespacePolicyTest.public action descriptors reject scenario shortcut action ids'
```

Expected: pass.

- [x] **Step 5: Run the protocol suite**

Run:

```sh
mise exec -- gradle :protocol:test
```

Expected: pass, proving existing generic action ids remain valid.

### Task 2: Documentation And Verification

**Files:**
- Modify: `AGENTS.md`
- Modify: `docs/project-completion-checklist.md`

- [x] **Step 1: Record Phase 37**

Add Phase 37 to the active guardrail narrative and checklist with focused
verification commands.

- [x] **Step 2: Run final verification**

Run:

```sh
mise run lint
mise run architecture-check
mise run ci
git diff --check
```

Expected: all pass.
