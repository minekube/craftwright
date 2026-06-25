---
name: test-suite-builder
description: Design and generate layered Craftwright Kotlin/JVM tests that balance speed, realism, and regression value across unit, protocol, Ktor HTTP, CLI, Fabric-boundary, and integration levels. Use when adding coverage for protocol logic, daemon routes, client lifecycle, serialization, adaptive CLI behavior, driver contracts, or end-to-end workflows.
---

# Test Suite Builder

Source mapping: Craftwright Kotlin/JVM, Ktor, CLI, protocol, and driver test strategy.

## Mission

Choose the lightest test that proves the behavior.
Generate tests that catch regressions instead of reproducing the implementation line by line.

## Start With A Test Strategy

Before writing code, classify what needs to be proven:

- pure business logic
- HTTP contract and validation
- daemon/session behavior
- serialization behavior
- CLI stdout/stderr and exit-code behavior
- driver contract behavior
- cross-component integration
- real process or Minecraft client integration

## Three-Layer Strategy

- Use unit tests for domain logic, calculations, decision tables, and deterministic branching.
- Use slice tests for framework boundaries:
  - Ktor test host or focused daemon server tests for HTTP contracts
  - focused JSON tests for serialization and OpenAPI metadata
  - CLI tests with in-memory daemon fixtures for command behavior
- Use process-level, Fabric, or external integration tests only when a realistic runtime boundary must be proven.

## Kotlin Testing Rules

- Prefer JUnit 5.
- Prefer real fakes and small fixtures over mocks unless the boundary is otherwise impractical.
- Use readable backtick test names when that matches the project style.
- Use `runTest` and the coroutine test toolkit for coroutine-heavy code.
- Build reusable fixtures, builders, or object mothers instead of duplicating inline object construction.

## What To Cover

- Success path.
- Validation failures.
- Business rule failures.
- Edge cases around nullability, optional fields, empty collections, and duplicates.
- At least one regression-oriented test for the bug or change that motivated the work.

## What Not To Do

- Do not write tests that only verify mock interactions and prove nothing observable.
- Do not couple assertions to private implementation details when public behavior is enough.
- Do not use real time, random values, or shared mutable state without control.

## Output Contract

Return these sections:

- `Test plan`: which layers to use and why.
- `Generated tests`: the concrete test classes or patch plan.
- `Test data support`: builders, fixtures, or factories to add.
- `Coverage gaps`: important cases still not covered.
- `Verification`: commands to run and which tests should fail before the fix.

## Framework-Specific Checks

- Match Ktor server/client test style to the module under test.
- Match Fabric or process fixtures to the actual runtime boundary being verified.
- If serialization or validation is the bug, include a test that proves the wire contract, not only the service logic.
- If CLI behavior is the bug, assert stdout, stderr, exit code, and daemon payload shape.

## Advanced Testing Nuances

- In-memory fakes can hide process, classloader, and Minecraft client thread behavior. Use higher-level smoke tests for those boundaries.
- Avoid testing generated OpenAPI by string fragments alone when structured JSON assertions are practical.
- Async and event-driven flows need deterministic waiting strategies such as controlled schedulers, latches, or Awaitility. Do not scatter sleeps.
- Concurrency and deadlock behavior cannot be proven in a single-threaded fake. Use multiple clients or explicit synchronization when reviewing those cases.
- Reused runtime fixtures improve speed but require strict state isolation. Do not trade determinism away for a faster green build.
- Use deterministic time and ID providers when business logic depends on clocks or UUIDs.

## Expert Heuristics

- If a bug was caused by framework wiring, add at least one test above unit level.
- If a bug was caused by domain branching, do not drag the whole daemon or runtime process into the fix.
- When in doubt, choose the smallest test that would have failed before the change.
- Treat test code as production code for readability and maintenance. Fixtures and helpers should reduce noise, not hide behavior.

## Guardrails

- Keep tests deterministic.
- Keep setup explicit and local to the scenario.
- Prefer one clear reason for failure per test.
- Do not silently introduce slow infrastructure-heavy tests into fast unit test suites.

## Quality Bar

A good run of this skill produces a test suite that is fast where possible and realistic where necessary.
A bad run floods the project with context-heavy tests, brittle mocks, and no clear explanation of why each test level exists.
