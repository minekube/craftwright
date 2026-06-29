# Agent Skills

Craftless keeps repo-local agent skills narrow and relevant to the current
Kotlin/JVM work. Broad Android, KMP, JPA, database, or one-off migration skills
should not be installed unless the repository starts using those technologies.

## Installed Repo-Local Skills

- `.agents/skills/craftless-public-gameplay-agent` captures how external
  agents should control Craftless clients through generated per-client
  OpenAPI, adaptive CLI, and SSE/JSON-RPC streams without static scenario
  shortcuts. It also records live co-play rules for navigation schemas,
  stop-command parsing, Minecraft-chat coordination, generic
  `craftless clients <id> run <action>` invocation, POST JSON-RPC-style
  control/query, SSE observation, fresh live-state checks before status
  claims, stale-client cleanup, and required final gameplay artifacts.
- `.agents/skills/kotlin-jvm-modern-practices` captures Craftless's modern
  Kotlin/JVM defaults: Gradle Kotlin DSL, Ktor Server/Client, structured
  concurrency, JVM tests, Fabric client-thread boundaries, and no old Java HTTP
  stack.
- `.agents/skills/kotlin-tooling-java-to-kotlin` is installed as a narrowed
  Craftless-scoped Java-to-Kotlin conversion workflow. Java still remains
  appropriate for Fabric Mixins and bytecode-sensitive Minecraft glue.
- `.agents/skills/gradle-kotlin-dsl-doctor` is installed for Gradle Kotlin DSL
  and dependency-model repair.
- `.agents/skills/dependency-conflict-resolver` is installed for Gradle
  classpath, version-authority, and binary compatibility issues.
- `.agents/skills/test-suite-builder` is installed for layered Kotlin test
  design.
- `.agents/skills/performance-concurrency-advisor` is installed for
  evidence-driven Kotlin/JVM concurrency and performance work.
- `.agents/skills/integration-resilience-engineer` is installed for resilient
  HTTP, process, and remote-boundary design.

This curated set avoids Android, KMP mobile, JPA, database, Retrofit, Exposed,
and project-specific Spring controller assumptions.

## Researched Sources

- [Kotlin/kotlin-agent-skills](https://github.com/Kotlin/kotlin-agent-skills)
  is an Apache-2.0 collection following the Agent Skills standard. As of the
  2026-06-25 research pass, its public workflow set is mostly migration-focused:
  Java-to-Kotlin conversion, AGP 9 migration, CocoaPods-to-SwiftPM migration,
  immutable collections migration, and JPA entity mapping.
- [Kotlin AI skills documentation](https://kotlinlang.org/docs/kotlin-ai-skills.html)
  describes skills as reusable instructions for Kotlin-specific agent
  workflows and lists OpenAI Codex among compatible agents.
- [JetBrains/skills](https://github.com/JetBrains/skills) is a
  JetBrains-filtered catalog of upstream skills. It includes useful Kotlin
  backend skill patterns such as dependency-conflict resolution, Gradle Kotlin
  DSL repair, test-suite design, integration resilience, and performance or
  concurrency analysis.
- [OpenAI Codex skills documentation](https://developers.openai.com/codex/skills)
  documents the skill shape Craftless uses: a `SKILL.md` file plus optional
  scripts, references, assets, and agent metadata.
- [Android Studio skill documentation](https://developer.android.com/studio/gemini/skills)
  documents `.agents/skills` as a project-local skill location, but Android
  workflows are not a fit for this repository unless Craftless later adds an
  Android target.
- Marketplace search for "modern kotlin best practices" surfaced broad
  community skills, but none should be installed into Craftless as-is.

Current upstream skill folders:

- `kotlin-tooling-java-to-kotlin`
- `kotlin-tooling-immutable-collections-0-5-x-migration`
- `kotlin-backend-jpa-entity-mapping`
- `kotlin-tooling-agp9-migration`
- `kotlin-tooling-cocoapods-spm-migration`

Community candidates checked:

- `mindrally/skills@kotlin-development`: about 479 installs at search time and
  151 GitHub stars. It is a generic Cursor-rules conversion and includes weak
  or non-Kotlin-specific advice such as always declaring every variable type,
  underscore-case directories, and arrow-function terminology.
- `spartan-stratos/spartan-ai-toolkit@kotlin-best-practices`: about 32 installs
  at search time and 81 GitHub stars after the GitHub redirect to
  `c0x12c/ai-toolkit`. It assumes Exposed ORM, Either-based managers, and
  project-specific controller conventions that Craftless does not use.
- `aj-geddes/useful-ai-prompts@android-kotlin-development`: about 1.1k installs
  and 278 GitHub stars, but it is Android/MVVM/Compose/Retrofit/Room focused.

## Recommendation For This Repository

Do not install the whole `Kotlin/kotlin-agent-skills` set into Craftless right
now. Most upstream skills are not relevant to this repo's current Kotlin/JVM
server, CLI, Fabric-driver, and Playwright helper work:

- no Android Gradle Plugin migration;
- no CocoaPods or SwiftPM migration;
- no JPA/Hibernate model;
- no `kotlinx.collections.immutable` dependency.

Do not install the broad community candidates listed above. If Craftless needs
more Kotlin guidance, evolve `.agents/skills/kotlin-jvm-modern-practices` with
project-specific rules instead.

## Useful Install Commands

For a Codex plugin install:

```sh
codex plugin marketplace add Kotlin/kotlin-agent-skills
```

Then install `kotlin-agent-skills@Kotlin` from `/plugins`.

For manual repo-local installation of one upstream skill:

```sh
mkdir -p .agents/skills
cp -R path/to/kotlin-agent-skills/skills/kotlin-tooling-java-to-kotlin .agents/skills/
```

Do this only when that workflow is needed by a branch.
