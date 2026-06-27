# Launch Argument Placeholders Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Resolve launch-time Minecraft argument placeholders from the client request and instance files before starting the prepared client process.

**Architecture:** Keep cache-time substitutions in cache preparation and launch-time substitutions in `ProcessClientRuntimeLauncher`. The launcher uses `CreateClientRequest` and `InstanceFiles` to build the variable map, then renders prepared JVM/game arguments before invoking `ProcessBuilder`.

**Tech Stack:** Kotlin/JVM, Gradle through mise, JUnit 5.

---

### Task 1: Launch-Time Placeholder Rendering

**Files:**
- Modify: `daemon/src/test/kotlin/com/minekube/craftless/daemon/LocalSessionApiServerTest.kt`
- Modify: `daemon/src/main/kotlin/com/minekube/craftless/daemon/WorkspaceClientRuntimeDriverFactory.kt`

- [x] **Step 1: Write failing launcher regression**

Extend `process client runtime launcher starts prepared command` so the
prepared launch-arguments file includes:

```json
{
  "game": [
    "--gameDir", "{{gameRoot}}",
    "--username", "{{auth_player_name}}",
    "--uuid", "{{auth_uuid}}",
    "--accessToken", "{{auth_access_token}}",
    "--userType", "{{user_type}}",
    "--assetIndex", "{{assets_index_name}}",
    "--versionType", "{{version_type}}",
    "--launcherName", "{{launcher_name}}",
    "--launcherVersion", "{{launcher_version}}",
    "--quickPlayPath", "{{quickPlayPath}}",
    "--quickPlaySingleplayer", "{{quickPlaySingleplayer}}",
    "--quickPlayMultiplayer", "{{quickPlayMultiplayer}}",
    "--quickPlayRealms", "{{quickPlayRealms}}",
    "--xuid", "{{auth_xuid}}",
    "--clientId", "{{clientid}}",
    "--width", "{{resolution_width}}",
    "--height", "{{resolution_height}}"
  ]
}
```

Assert the launched command contains the offline profile values for `Alice`,
the standard offline UUID `10920508-d5d8-3eed-93d2-92f193afe7d7`, access token
`0`, user type `legacy`, asset index `1.21.6`, version type `release`,
Craftless launcher metadata, and the instance quick-play path. Assert
unresolved quick-play mode, account-id, and resolution flags are omitted.

- [x] **Step 2: Verify RED**

Run:

```sh
mise exec -- gradle :daemon:test --tests 'com.minekube.craftless.daemon.LocalSessionApiServerTest.process client runtime launcher starts prepared command'
```

Expected before implementation: fail because only `{{gameRoot}}` is resolved.

- [x] **Step 3: Implement launcher variable map**

Pass `CreateClientRequest` into `launchCommand`, derive launch variables from
the request profile and instance files, resolve placeholders with a shared
regex, and omit `--flag value` pairs when the resolved value is blank.

- [x] **Step 4: Verify GREEN**

Run:

```sh
mise exec -- gradle :daemon:test --tests 'com.minekube.craftless.daemon.LocalSessionApiServerTest.process client runtime launcher starts prepared command'
```

Expected: pass.

### Task 2: Broader Verification

**Files:**
- Modify: `AGENTS.md`
- Modify: `docs/project-completion-checklist.md`

- [x] **Step 1: Run daemon checks**

Run:

```sh
mise exec -- gradle :daemon:test
mise exec -- gradle :daemon:ktlintCheck :daemon:detekt
```

Expected: pass.

- [x] **Step 2: Run repository gates**

Run:

```sh
git diff --check
mise run architecture-check
mise run lint
mise run ci
```

Expected: all pass before pushing.
