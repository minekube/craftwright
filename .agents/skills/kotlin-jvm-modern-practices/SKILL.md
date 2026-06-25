---
name: kotlin-jvm-modern-practices
description: Use when writing, reviewing, or refactoring Kotlin/JVM code in Craftwright, especially CLI, daemon, protocol, Ktor HTTP/WebSocket, Gradle Kotlin DSL, coroutine, test, or Fabric driver-adjacent code. Applies modern Kotlin practice without Android, Spring, JPA, Retrofit, OkHttp, or old Java HTTP assumptions.
---

# Kotlin/JVM Modern Practices

## Default Stack

- Use Kotlin/JVM for product logic and tests.
- Use Gradle Kotlin DSL for build configuration.
- Use `kotlinx.serialization` for JSON contracts when possible.
- Use Ktor Server for local HTTP/WebSocket APIs.
- Use Ktor Client when Craftwright needs an HTTP client.
- Keep Java for Fabric Mixins, accessors, and bytecode-sensitive Minecraft glue.
- Run tools through `mise`; use Bun for JavaScript-side test helpers.

## Kotlin Style

- Model protocol and domain state with `data class`, `enum class`, sealed
  hierarchies, and value classes where they clarify invariants.
- Prefer explicit domain types over stringly typed maps or ad hoc JSON.
- Use nullability to encode real absence; avoid nullable values as unfinished
  design.
- Keep side effects at boundaries. Core protocol and state transitions should be
  easy to unit test.
- Prefer small functions with clear names over broad helper objects.
- Avoid premature abstractions; introduce interfaces only where tests, module
  boundaries, or runtime replacement need them.

## HTTP And Protocol Rules

- Do not add custom HTTP method enums or hand-rolled HTTP clients.
- Do not use `com.sun.net.httpserver`, `java.net.http.HttpClient`, or OkHttp for
  new Craftwright HTTP paths unless a local constraint is documented first.
- Keep public API names Craftwright-owned; never expose HeadlessMC,
  HMC-Specifics, or Minecraft console command strings as stable route names.
- Make error payloads machine-readable and stable enough for generated clients.

## Coroutines And Concurrency

- Prefer structured concurrency. Avoid unscoped background work.
- Put timeouts at remote/process boundaries.
- Keep mutable shared state isolated behind one owner, a mutex, actor, or clear
  single-thread confinement.
- Never block Ktor event loops or coroutine dispatchers with process waits or
  long filesystem operations; move blocking work to an appropriate context.

## Fabric Boundary

- Execute Minecraft client actions on the Minecraft client thread.
- Keep version-specific Minecraft API usage inside versioned driver modules.
- Keep low-level Mixin and accessor code Java-first unless Kotlin has a clear
  advantage.
- Do not make bridge/HMC behavior the durable product contract.

## Testing

- Add the lightest test that proves behavior: unit tests for pure logic, focused
  module tests for adapters, and integration smoke tests for real process/client
  boundaries.
- Prefer JUnit 5 and readable backtick test names when matching local style.
- For coroutine code, use coroutine test tooling rather than real sleeps.
- Verify with focused Gradle tasks first, then `mise run ci` before completion.
