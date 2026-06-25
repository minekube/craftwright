---
name: gradle-kotlin-dsl-doctor
description: Generate, debug, and repair Craftwright Kotlin/JVM Gradle builds with minimal, compatible changes. Use when `build.gradle.kts` or `settings.gradle.kts` is failing, Kotlin/Fabric/Loom/Ktor plugins or toolchains are incompatible, test or runtime classpaths are broken, or a Kotlin DSL patch must be safe and incremental.
---

# Gradle Kotlin DSL Doctor

Source mapping: Craftwright Kotlin/JVM and Gradle Kotlin DSL build maintenance.

## Mission

Stabilize the build with the smallest defensible change.
Treat Gradle problems as compatibility and model problems, not only syntax problems.

## Read First

- `settings.gradle.kts`
- root and module `build.gradle.kts`
- `gradle.properties`
- `gradle/libs.versions.toml` or other version catalogs
- `gradle-wrapper.properties`
- the exact Gradle output from the failing task

## Classify The Failure

Classify the problem before editing anything:

- plugin resolution or plugin version mismatch
- Kotlin DSL syntax or type-safe accessor issue
- dependency resolution or platform conflict
- JDK toolchain or target mismatch
- source set or test runtime misconfiguration
- compiler plugin problem such as serialization, Loom, KAPT, or KSP
- task wiring or multi-module convention issue

## Work Sequence

1. Identify the failing task and the first meaningful error line.
2. Determine whether the breakage is in plugin resolution, dependency resolution, compilation, test execution, or packaging.
3. Extract the current version authorities:
   - Kotlin plugin
   - Fabric Loom plugin
   - Gradle wrapper
   - JDK toolchain
   - version catalog or convention plugin
4. Verify whether dependency versions belong in the root build, a version catalog, plugin management, or module-local declarations.
5. Patch the narrowest file possible. Prefer module-local fixes over global rewrites.
6. Recommend the smallest verification command that proves the fix before running the full build.

## Kotlin/JVM Checks

- Verify JVM target and Java toolchain alignment.
- Verify Ktor, `kotlinx.serialization`, coroutine, and Kotlin stdlib libraries align with the Kotlin version in use.
- Verify Fabric Loom and Minecraft/Fabric dependencies stay inside driver modules unless intentionally shared.
- Verify whether annotation processing should use KAPT or KSP in this project.

## Preferred Fix Style

- Remove unnecessary explicit versions before adding new ones.
- Prefer small diffs over full-file replacement.
- Preserve existing conventions such as version catalogs, convention plugins, or build logic modules.
- If a fix spans several modules, explain the dependency graph that forces it.

## Advanced Build Diagnostics

- Distinguish dependency declaration problems from variant-selection problems. A dependency can exist and still resolve the wrong JVM target, classifier, or capability.
- Check whether the repo uses `platform(...)`, `enforcedPlatform(...)`, constraints, or dependency locking. That changes how conflict resolution behaves.
- Check whether the real plugin source of truth is `pluginManagement`, a version catalog, or a convention plugin rather than the module build file.
- Check configuration-cache compatibility if the project is trying to use it. Eager task access and mutable global state in custom build logic often explain strange Gradle behavior.
- Check whether KAPT, KSP, code generation, or source-set wiring requires generated-source directories or task ordering that is currently missing.
- Check dependency locking, verification metadata, or repository policy files before suggesting new repositories or version changes.
- Check test fixtures, included builds, and composite builds when inter-module dependencies behave differently in IDE and CI.

## Expert Heuristics

- Prefer explaining which layer owns a version: wrapper, plugin, platform, version catalog, or module override. This avoids repeated drift after the immediate fix.
- If the build fails only in CI, compare wrapper version, JDK vendor, `org.gradle.jvmargs`, configuration cache flags, and repository credentials before touching dependency declarations.
- If a custom task or plugin breaks under a new Gradle version, isolate that change from ordinary dependency or Kotlin compiler fixes.
- If the build uses dependency substitution or included builds, verify whether the resolved artifact is local source or published binary before diagnosing API mismatches.

## Output Contract

Return these sections:

- `Root cause`: what is broken and at which stage of the build.
- `Minimal patch`: the exact Gradle change to make.
- `Why this works`: the compatibility rule or Gradle model fact behind the patch.
- `Verification`: one or more commands in increasing confidence order.
- `Follow-up risk`: only if the fix unblocks the build but leaves technical debt behind.

## Guardrails

- Do not invent random dependency or plugin versions.
- Do not rewrite Kotlin DSL into Groovy or Maven syntax.
- Do not change unrelated modules just because they look similar.
- Do not hide version conflicts with `force` or aggressive exclusions unless there is a strong, explicit reason.
- Do not recommend clearing caches as the primary fix when the build model itself is wrong.

## High-Signal Commands

Use these when the repository permits command execution:

- `mise exec -- gradle help`
- `mise exec -- gradle test`
- `mise exec -- gradle :module:compileKotlin`
- `mise exec -- gradle dependencies`
- `mise exec -- gradle dependencyInsight --dependency <artifact>`

## Quality Bar

A good run of this skill leaves the build model clearer than before and produces a fix that survives CI.
A bad run throws version guesses at the problem, rewrites too much, or ignores the repository's real version authority.
