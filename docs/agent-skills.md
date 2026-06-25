# Agent Skills

Craftwright keeps repo-local agent skills narrow and relevant to the current
Kotlin/JVM work. Broad Android, KMP, JPA, database, or one-off migration skills
should not be installed unless the repository starts using those technologies.

## Installed Repo-Local Skills

- `.agent/skills/craftwright-kotlin-jvm` is the active repository skill for
  Kotlin/JVM, Gradle, Ktor, CLI, daemon, protocol, bridge, Fabric-driver, and
  TypeScript integration work in this repo.

This skill exists because the public Kotlin marketplace skills found so far are
either task-specific or mismatch Craftwright's stack. Repo-local guidance is
safer than importing external rules that would push Android, Exposed ORM,
Either managers, Retrofit, or blanket Java-to-Kotlin conversion into this codebase.

## Researched Sources

- JetBrains/Kotlin publishes `Kotlin/kotlin-agent-skills`, an Apache-2.0
  collection following the Agent Skills standard.
- Kotlin's documentation describes these skills as reusable instructions for
  Kotlin-specific agent workflows and lists Codex compatibility.
- Google documents Android skills as project-local instructions under
  `.skills/` or `.agent/skills/`, but those workflows target Android apps.
- Marketplace search for "modern kotlin best practices" surfaced broad
  community skills, but none should be installed into Craftwright as-is.

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
  project-specific controller conventions that Craftwright does not use.
- `aj-geddes/useful-ai-prompts@android-kotlin-development`: about 1.1k installs
  and 278 GitHub stars, but it is Android/MVVM/Compose/Retrofit/Room focused.

## Recommendation For This Repository

Do not install the whole `Kotlin/kotlin-agent-skills` set into Craftwright right
now. Most upstream skills are not relevant to this repo's current Kotlin/JVM
server, CLI, Fabric-driver, and Playwright helper work:

- no Android Gradle Plugin migration;
- no CocoaPods or SwiftPM migration;
- no JPA/Hibernate model;
- no `kotlinx.collections.immutable` dependency.

Install `kotlin-tooling-java-to-kotlin` only when there is a concrete conversion
task. Craftwright intentionally keeps Java for Fabric Mixins and bytecode-facing
Minecraft glue, so a Java-to-Kotlin skill should not be globally active by
default.

Do not install the broad community candidates listed above. If Craftwright needs
more Kotlin guidance, evolve `.agent/skills/craftwright-kotlin-jvm` with
project-specific rules instead.

## Useful Install Commands

For a Codex plugin install:

```sh
codex plugin marketplace add Kotlin/kotlin-agent-skills
```

Then install `kotlin-agent-skills@Kotlin` from `/plugins`.

For manual repo-local installation of one upstream skill:

```sh
mkdir -p .agent/skills
cp -R path/to/kotlin-agent-skills/skills/kotlin-tooling-java-to-kotlin .agent/skills/
```

Do this only when that workflow is needed by a branch.
