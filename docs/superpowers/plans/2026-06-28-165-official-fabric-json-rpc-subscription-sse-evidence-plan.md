# Official Fabric JSON-RPC Subscription SSE Evidence Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the official/latest connected attach probe prove JSON-RPC subscription control with filtered SSE delivery for `client.connected`.

**Architecture:** Reuse existing daemon `POST /clients/{id}:rpc` subscribe/query/unsubscribe methods and `GET /clients/{id}/events:stream?subscriptionId=...`. Extend only the official probe evidence harness so it writes raw JSON-RPC/SSE artifacts and records compact subscription summaries in `probe-result.json`; do not add product routes, gameplay descriptors, operation adapters, CLI catalogs, or scenario logic.

**Tech Stack:** Kotlin/JVM test harness, Ktor Client, kotlinx.serialization JSON, existing daemon JSON-RPC and SSE endpoints, Gradle through mise.

---

### Task 1: Capture JSON-RPC Subscription And Filtered SSE Artifacts

**Files:**
- Modify: `driver-fabric-official/src/test/kotlin/com/minekube/craftless/driver/fabric/official/probe/OfficialFabricAttachProbe.kt`

- [x] **Step 1: Verify current subscription artifacts are missing**

Run:

```sh
rm -f driver-fabric-official/build/craftless-official-attach-probe/client-rpc-subscribe.json \
  driver-fabric-official/build/craftless-official-attach-probe/client-events-subscription-stream.sse \
  driver-fabric-official/build/craftless-official-attach-probe/client-rpc-subscriptions.json \
  driver-fabric-official/build/craftless-official-attach-probe/client-rpc-unsubscribe.json \
  driver-fabric-official/build/craftless-official-attach-probe/client-rpc-subscriptions-after-unsubscribe.json
test -f driver-fabric-official/build/craftless-official-attach-probe/client-rpc-subscribe.json && \
  test -f driver-fabric-official/build/craftless-official-attach-probe/client-events-subscription-stream.sse && \
  test -f driver-fabric-official/build/craftless-official-attach-probe/client-rpc-subscriptions.json && \
  test -f driver-fabric-official/build/craftless-official-attach-probe/client-rpc-unsubscribe.json && \
  test -f driver-fabric-official/build/craftless-official-attach-probe/client-rpc-subscriptions-after-unsubscribe.json
```

Expected: FAIL before implementation because the official probe has not written
JSON-RPC subscription artifacts.

- [x] **Step 2: Add generic JSON-RPC request helper**

In `OfficialFabricAttachProbe.kt`, replace the body of `queryJsonRpc` with a
small generic helper so subscribe/query/unsubscribe use the same endpoint:

```kotlin
private suspend fun sendJsonRpc(
    http: HttpClient,
    daemonUrl: String,
    id: String,
    method: String,
    params: JsonObject = buildJsonObject {},
): String =
    http
        .post("$daemonUrl/clients/${config.clientId}:rpc") {
            contentType(ContentType.Application.Json)
            setBody(
                probeJson.encodeToString(
                    JsonRpcRequest(
                        id = id,
                        method = method,
                        params = params,
                    ),
                ),
            )
        }.bodyAsText()
```

Then make `queryJsonRpc` delegate:

```kotlin
private suspend fun queryJsonRpc(
    http: HttpClient,
    daemonUrl: String,
    target: String,
): String =
    sendJsonRpc(
        http = http,
        daemonUrl = daemonUrl,
        id = "rpc:official_probe:$target",
        method = JsonRpcMethod.QUERY,
        params =
            buildJsonObject {
                put("target", target)
            },
    )
```

- [x] **Step 3: Add subscription helpers**

Add helpers inside `OfficialFabricAttachProbe`:

```kotlin
private suspend fun subscribeJsonRpc(
    http: HttpClient,
    daemonUrl: String,
    type: String,
): String =
    sendJsonRpc(
        http = http,
        daemonUrl = daemonUrl,
        id = "rpc:official_probe:subscribe",
        method = JsonRpcMethod.SUBSCRIBE,
        params =
            buildJsonObject {
                put("type", type)
            },
    )

private suspend fun unsubscribeJsonRpc(
    http: HttpClient,
    daemonUrl: String,
    subscriptionId: String,
): String =
    sendJsonRpc(
        http = http,
        daemonUrl = daemonUrl,
        id = "rpc:official_probe:unsubscribe",
        method = JsonRpcMethod.UNSUBSCRIBE,
        params =
            buildJsonObject {
                put("subscriptionId", subscriptionId)
            },
    )
```

- [x] **Step 4: Fetch and write subscription artifacts**

In `OfficialFabricAttachProbe.run()`, after the unfiltered SSE artifact is
written, add:

```kotlin
val rpcSubscribeText = subscribeJsonRpc(http, daemonUrl, "client.connected")
val rpcSubscriptionId = rpcSubscriptionId(rpcSubscribeText)
val subscriptionEventsText =
    http.get("$daemonUrl/clients/${config.clientId}/events:stream?subscriptionId=$rpcSubscriptionId").bodyAsText()
val rpcSubscriptionsText = queryJsonRpc(http, daemonUrl, "subscriptions")
val rpcUnsubscribeText = unsubscribeJsonRpc(http, daemonUrl, rpcSubscriptionId)
val rpcSubscriptionsAfterUnsubscribeText = queryJsonRpc(http, daemonUrl, "subscriptions")

config.artifactsDir.resolve("client-rpc-subscribe.json").writeText(rpcSubscribeText + "\n")
config.artifactsDir.resolve("client-events-subscription-stream.sse").writeText(subscriptionEventsText + "\n")
config.artifactsDir.resolve("client-rpc-subscriptions.json").writeText(rpcSubscriptionsText + "\n")
config.artifactsDir.resolve("client-rpc-unsubscribe.json").writeText(rpcUnsubscribeText + "\n")
config.artifactsDir.resolve("client-rpc-subscriptions-after-unsubscribe.json")
    .writeText(rpcSubscriptionsAfterUnsubscribeText + "\n")
```

- [x] **Step 5: Record subscription summary fields**

Add helpers:

```kotlin
private fun rpcSubscriptionId(text: String): String =
    requireNotNull(probeJson.decodeFromString<JsonRpcResponse>(text).result)
        .jsonObject
        .getValue("subscriptionId")
        .jsonPrimitive
        .content

private fun rpcResultArraySize(text: String): Int =
    requireNotNull(probeJson.decodeFromString<JsonRpcResponse>(text).result)
        .jsonArray
        .size
```

Add fields to `OfficialFabricAttachProbeResult`:

```kotlin
val rpcSubscriptionId: String? = null,
val rpcSubscriptionEventTypes: List<String> = emptyList(),
val rpcSubscriptionCount: Int = 0,
val rpcSubscriptionCountAfterUnsubscribe: Int = 0,
```

Pass:

```kotlin
rpcSubscriptionId = rpcSubscriptionId,
rpcSubscriptionEventTypes = sseEventTypes(subscriptionEventsText),
rpcSubscriptionCount = rpcResultArraySize(rpcSubscriptionsText),
rpcSubscriptionCountAfterUnsubscribe = rpcResultArraySize(rpcSubscriptionsAfterUnsubscribeText),
```

- [x] **Step 6: Run focused official tests**

Run:

```sh
mise exec -- gradle :driver-fabric-official:test --tests '*OfficialFabricSharedRuntimeMetadataTest*'
```

Expected: PASS.

### Task 2: Connected Probe Evidence And Docs

**Files:**
- Create: `docs/superpowers/evidence/2026-06-28-official-fabric-json-rpc-subscription-sse-evidence.md`
- Modify: `docs/project-completion-checklist.md`
- Modify: `docs/superpowers/phase-index.md`
- Modify: `docs/superpowers/plans/2026-06-28-165-official-fabric-json-rpc-subscription-sse-evidence-plan.md`

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

- [x] **Step 2: Inspect subscription/SSE evidence**

Run:

```sh
jq -r '.result.subscriptionId, (.result.filter.types | join(","))' \
  driver-fabric-official/build/craftless-official-attach-probe/client-rpc-subscribe.json
grep '^event: ' \
  driver-fabric-official/build/craftless-official-attach-probe/client-events-subscription-stream.sse
jq -r '.result | length' \
  driver-fabric-official/build/craftless-official-attach-probe/client-rpc-subscriptions.json
jq -r '.result.unsubscribed' \
  driver-fabric-official/build/craftless-official-attach-probe/client-rpc-unsubscribe.json
jq -r '.result | length' \
  driver-fabric-official/build/craftless-official-attach-probe/client-rpc-subscriptions-after-unsubscribe.json
jq -r '{rpcSubscriptionId, rpcSubscriptionEventTypes, rpcSubscriptionCount, rpcSubscriptionCountAfterUnsubscribe}' \
  driver-fabric-official/build/craftless-official-attach-probe/probe-result.json
```

Expected: subscription id is present, filter type is `client.connected`, SSE
contains only `event: client.connected`, subscription count is `1`,
unsubscribe is `true`, and post-unsubscribe count is `0`.

- [x] **Step 3: Record evidence and checklist status**

Record red check, focused tests, connected probe command, subscription
artifacts, parsed result fields, and boundary notes in:

```text
docs/superpowers/evidence/2026-06-28-official-fabric-json-rpc-subscription-sse-evidence.md
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

Expected: all commands pass and the worktree only contains Phase 165 files.

Commit and push:

```sh
git add docs/project-completion-checklist.md docs/superpowers/phase-index.md docs/superpowers/specs/2026-06-28-165-official-fabric-json-rpc-subscription-sse-evidence-design.md docs/superpowers/plans/2026-06-28-165-official-fabric-json-rpc-subscription-sse-evidence-plan.md docs/superpowers/evidence/2026-06-28-official-fabric-json-rpc-subscription-sse-evidence.md driver-fabric-official/src/test/kotlin/com/minekube/craftless/driver/fabric/official/probe/OfficialFabricAttachProbe.kt
git commit -m "test: capture official fabric json rpc subscription evidence"
git push origin main
```

## Self-Review

- Spec coverage: the plan captures subscribe, filtered SSE, subscriptions
  query, unsubscribe, and post-unsubscribe query while preserving `actions=0`
  and avoiding gameplay/API/product endpoint changes.
- Placeholder scan: no task uses TBD/TODO/fill-in wording.
- Type consistency: `rpcSubscriptionId`, `rpcSubscriptionEventTypes`,
  `rpcSubscriptionCount`, `rpcSubscriptionCountAfterUnsubscribe`,
  `client-rpc-subscribe.json`, `client-events-subscription-stream.sse`,
  `client-rpc-subscriptions.json`, `client-rpc-unsubscribe.json`, and
  `client-rpc-subscriptions-after-unsubscribe.json` are named consistently.
