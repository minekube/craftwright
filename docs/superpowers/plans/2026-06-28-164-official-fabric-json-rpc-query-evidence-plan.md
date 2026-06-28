# Official Fabric JSON-RPC Query Evidence Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the official/latest connected attach probe write public JSON-RPC `query` evidence for live OpenAPI, actions, and resources projections.

**Architecture:** Reuse the existing daemon `POST /clients/{id}:rpc` endpoint and `query` targets. Extend only the official probe evidence harness so it writes raw JSON-RPC response artifacts and records a small summary in `probe-result.json`; do not add product routes, gameplay descriptors, operation adapters, static CLI catalogs, or scenario logic.

**Tech Stack:** Kotlin/JVM test harness, Ktor Client, kotlinx.serialization JSON, existing daemon JSON-RPC endpoint, Gradle through mise.

---

### Task 1: Capture JSON-RPC Query Artifacts

**Files:**
- Modify: `driver-fabric-official/src/test/kotlin/com/minekube/craftless/driver/fabric/official/probe/OfficialFabricAttachProbe.kt`

- [x] **Step 1: Verify current JSON-RPC artifacts are missing**

Run:

```sh
rm -f driver-fabric-official/build/craftless-official-attach-probe/client-rpc-openapi.json \
  driver-fabric-official/build/craftless-official-attach-probe/client-rpc-actions.json \
  driver-fabric-official/build/craftless-official-attach-probe/client-rpc-resources.json
test -f driver-fabric-official/build/craftless-official-attach-probe/client-rpc-openapi.json && \
  test -f driver-fabric-official/build/craftless-official-attach-probe/client-rpc-actions.json && \
  test -f driver-fabric-official/build/craftless-official-attach-probe/client-rpc-resources.json
```

Expected: FAIL before implementation because the official probe has not
written JSON-RPC query artifacts.

- [x] **Step 2: Add JSON-RPC query helper**

In `OfficialFabricAttachProbe.kt`, add imports:

```kotlin
import com.minekube.craftless.protocol.JsonRpcMethod
import com.minekube.craftless.protocol.JsonRpcRequest
import com.minekube.craftless.protocol.JsonRpcResponse
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.put
```

Add this helper inside `OfficialFabricAttachProbe`:

```kotlin
private suspend fun queryJsonRpc(
    http: HttpClient,
    daemonUrl: String,
    target: String,
): String =
    http
        .post("$daemonUrl/clients/${config.clientId}:rpc") {
            contentType(ContentType.Application.Json)
            setBody(
                probeJson.encodeToString(
                    JsonRpcRequest(
                        id = "rpc:official_probe:$target",
                        method = JsonRpcMethod.QUERY,
                        params =
                            buildJsonObject {
                                put("target", target)
                            },
                    ),
                ),
            )
        }.bodyAsText()
```

- [x] **Step 3: Fetch and write JSON-RPC response bodies**

In `OfficialFabricAttachProbe.run()`, after the REST projection endpoint
bodies are fetched, fetch:

```kotlin
val rpcOpenApiText = queryJsonRpc(http, daemonUrl, "openapi")
val rpcActionsText = queryJsonRpc(http, daemonUrl, "actions")
val rpcResourcesText = queryJsonRpc(http, daemonUrl, "resources")
```

Write:

```kotlin
config.artifactsDir.resolve("client-rpc-openapi.json").writeText(rpcOpenApiText + "\n")
config.artifactsDir.resolve("client-rpc-actions.json").writeText(rpcActionsText + "\n")
config.artifactsDir.resolve("client-rpc-resources.json").writeText(rpcResourcesText + "\n")
```

- [x] **Step 4: Record JSON-RPC summary fields**

Add helper functions:

```kotlin
private fun rpcResultArraySize(text: String): Int =
    requireNotNull(probeJson.decodeFromString<JsonRpcResponse>(text).result)
        .jsonArray
        .size

private fun rpcResourceIds(text: String): List<String> =
    requireNotNull(probeJson.decodeFromString<JsonRpcResponse>(text).result)
        .jsonArray
        .mapNotNull { element -> element.jsonObject["id"]?.jsonPrimitive?.content }
```

Add fields to `OfficialFabricAttachProbeResult`:

```kotlin
val rpcQueryTargets: List<String> = emptyList(),
val rpcActionCount: Int = 0,
val rpcResourceIds: List<String> = emptyList(),
```

Pass:

```kotlin
rpcQueryTargets = listOf("openapi", "actions", "resources"),
rpcActionCount = rpcResultArraySize(rpcActionsText),
rpcResourceIds = rpcResourceIds(rpcResourcesText),
```

- [x] **Step 5: Run focused official tests**

Run:

```sh
mise exec -- gradle :driver-fabric-official:test --tests '*OfficialFabricSharedRuntimeMetadataTest*'
```

Expected: PASS.

### Task 2: Connected Probe Evidence And Docs

**Files:**
- Create: `docs/superpowers/evidence/2026-06-28-official-fabric-json-rpc-query-evidence.md`
- Modify: `docs/project-completion-checklist.md`
- Modify: `docs/superpowers/phase-index.md`
- Modify: `docs/superpowers/plans/2026-06-28-164-official-fabric-json-rpc-query-evidence-plan.md`

- [x] **Step 1: Run the connected official attach probe**

Run:

```sh
rm -rf driver-fabric-official/build/craftless-official-attach-probe
CRAFTLESS_OFFICIAL_FABRIC_ATTACH_PROBE=1 \
CRAFTLESS_OFFICIAL_ATTACH_PROBE_CONNECT=1 \
CRAFTLESS_OFFICIAL_ATTACH_PROBE_TIMEOUT_MS=180000 \
mise exec -- gradle :driver-fabric-official:officialFabricAttachProbe
```

Expected: `BUILD SUCCESSFUL`.

- [x] **Step 2: Inspect JSON-RPC query evidence**

Run:

```sh
jq -r '.result | "actions=" + (length | tostring)' \
  driver-fabric-official/build/craftless-official-attach-probe/client-rpc-actions.json
jq -r '.result[].id' \
  driver-fabric-official/build/craftless-official-attach-probe/client-rpc-resources.json
jq -r '{rpcQueryTargets, rpcActionCount, rpcResourceIds}' \
  driver-fabric-official/build/craftless-official-attach-probe/probe-result.json
```

Expected: `actions=0`, resource ids include `runtime`, `registry`, `event`,
`client`, `player`, `inventory`, `world`, and `entity`, and
`probe-result.json` records the same JSON-RPC projection data.

- [x] **Step 3: Record evidence and checklist status**

Record red check, focused tests, connected probe command, JSON-RPC artifacts,
parsed result fields, and boundary notes in:

```text
docs/superpowers/evidence/2026-06-28-official-fabric-json-rpc-query-evidence.md
docs/project-completion-checklist.md
docs/superpowers/phase-index.md
```

- [x] **Step 4: Run final verification and push**

Run:

```sh
mise exec -- gradle :driver-fabric-official:test --tests '*OfficialFabricSharedRuntimeMetadataTest*'
mise run fabric-lane-check-latest-official
mise run ci
git diff --check
git status --short --branch
```

Expected: all commands pass and the worktree only contains Phase 164 files.

Commit and push:

```sh
git add docs/project-completion-checklist.md docs/superpowers/phase-index.md docs/superpowers/specs/2026-06-28-164-official-fabric-json-rpc-query-evidence-design.md docs/superpowers/plans/2026-06-28-164-official-fabric-json-rpc-query-evidence-plan.md docs/superpowers/evidence/2026-06-28-official-fabric-json-rpc-query-evidence.md driver-fabric-official/src/test/kotlin/com/minekube/craftless/driver/fabric/official/probe/OfficialFabricAttachProbe.kt
git commit -m "test: capture official fabric json rpc query evidence"
git push origin main
```

## Self-Review

- Spec coverage: the plan captures JSON-RPC `openapi`, `actions`, and
  `resources` targets, records targets/counts/ids, preserves `actions=0`, and
  avoids gameplay/API/product endpoint changes.
- Placeholder scan: no task uses TBD/TODO/fill-in wording.
- Type consistency: `rpcQueryTargets`, `rpcActionCount`, `rpcResourceIds`,
  `client-rpc-openapi.json`, `client-rpc-actions.json`, and
  `client-rpc-resources.json` are named consistently.
