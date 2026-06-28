# Official Fabric JSON-RPC Subscription SSE Evidence

## Scope

Phase 165 extends the official/latest Fabric attach probe to capture public
JSON-RPC `subscribe`, subscription-filtered SSE, `subscriptions` query,
`unsubscribe`, and post-unsubscribe query evidence. This is transport evidence
only: no gameplay action, operation adapter, static catalog, public route, CLI
gameplay command, scenario shortcut, packaging support, or latest/current
support claim is added.

## Red Check

Command:

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

Result before implementation: failed with exit code `1` because the official
probe did not write JSON-RPC subscription artifacts.

## Focused Check

Command:

```sh
mise exec -- gradle :driver-fabric-official:test --tests '*OfficialFabricSharedRuntimeMetadataTest*'
```

Result: `BUILD SUCCESSFUL`.

## Connected Official Attach Probe

Command:

```sh
rm -rf driver-fabric-official/build/craftless-official-attach-probe
CRAFTLESS_OFFICIAL_FABRIC_ATTACH_PROBE=1 \
CRAFTLESS_OFFICIAL_ATTACH_PROBE_CONNECT=1 \
CRAFTLESS_OFFICIAL_ATTACH_PROBE_TIMEOUT_MS=180000 \
mise exec -- gradle :driver-fabric-official:officialFabricAttachProbe
```

Result: `BUILD SUCCESSFUL`.

Probe output:

```text
official Fabric probe observed connected client state for official-probe
```

Subscription response inspection:

```sh
jq -r '.result.subscriptionId, (.result.filter.types | join(","))' \
  driver-fabric-official/build/craftless-official-attach-probe/client-rpc-subscribe.json
```

Output:

```text
subscription:official-probe:0001
client.connected
```

Filtered SSE inspection:

```sh
grep '^event: ' \
  driver-fabric-official/build/craftless-official-attach-probe/client-events-subscription-stream.sse
```

Output:

```text
event: client.connected
```

Filter exclusion check:

```sh
if grep -q '^event: client.created' driver-fabric-official/build/craftless-official-attach-probe/client-events-subscription-stream.sse || \
  grep -q '^event: client.attached' driver-fabric-official/build/craftless-official-attach-probe/client-events-subscription-stream.sse; then
  echo unexpected-unfiltered-event
  exit 1
else
  echo filtered-stream-only-client-connected
fi
```

Output:

```text
filtered-stream-only-client-connected
```

Subscription lifecycle inspection:

```sh
jq -r '.result | length' \
  driver-fabric-official/build/craftless-official-attach-probe/client-rpc-subscriptions.json
jq -r '.result.unsubscribed' \
  driver-fabric-official/build/craftless-official-attach-probe/client-rpc-unsubscribe.json
jq -r '.result | length' \
  driver-fabric-official/build/craftless-official-attach-probe/client-rpc-subscriptions-after-unsubscribe.json
```

Output:

```text
1
true
0
```

`probe-result.json` inspection:

```sh
jq -r '{rpcSubscriptionId, rpcSubscriptionEventTypes, rpcSubscriptionCount, rpcSubscriptionCountAfterUnsubscribe, rpcActionCount, status, connectTarget}' \
  driver-fabric-official/build/craftless-official-attach-probe/probe-result.json
```

Output:

```json
{
  "rpcSubscriptionId": "subscription:official-probe:0001",
  "rpcSubscriptionEventTypes": [
    "client.connected"
  ],
  "rpcSubscriptionCount": 1,
  "rpcSubscriptionCountAfterUnsubscribe": 0,
  "rpcActionCount": 0,
  "status": "CONNECTED",
  "connectTarget": "127.0.0.1:56484"
}
```

## Boundary

This phase proves the connected official/latest lane can use JSON-RPC
subscription control to filter public SSE lifecycle events. It intentionally
preserves zero official gameplay actions.

## Final Verification

Commands:

```sh
mise exec -- gradle :driver-fabric-official:test --tests '*OfficialFabricSharedRuntimeMetadataTest*'
mise run fabric-lane-check-latest-official
mise run ci
git diff --check
```

Results:

- Focused official shared metadata tests: `BUILD SUCCESSFUL`.
- Latest-official lane check: `BUILD SUCCESSFUL`.
- Full local CI: `BUILD SUCCESSFUL`, including Gradle lint, detekt
  unused-check, Gradle tests, and Bun Playwright tests.
- Diff check: exit code `0`.
