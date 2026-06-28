# v0.1.1 Release Install Evidence

Date: 2026-06-28

## Release Publication

Command:

```sh
gh release list --limit 5
```

Observed:

```text
v0.1.1 Latest v0.1.1 2026-06-28T08:33:22Z
v0.1.0        v0.1.0 2026-06-26T16:25:51Z
```

Command:

```sh
gh release view v0.1.1 --json tagName,url,assets,targetCommitish,publishedAt \
  --jq '{tagName,url,targetCommitish,publishedAt,assets:[.assets[].name]}'
```

Observed:

```json
{
  "assets": [
    "craftless-0.1.1.tar",
    "craftless-0.1.1.zip",
    "SHA256SUMS"
  ],
  "publishedAt": "2026-06-28T08:33:22Z",
  "tagName": "v0.1.1",
  "targetCommitish": "main",
  "url": "https://github.com/minekube/craftless/releases/tag/v0.1.1"
}
```

Command:

```sh
gh run view 28316490956 --json status,conclusion,headSha,url
```

Observed:

```json
{
  "conclusion": "success",
  "headSha": "bc9e630c1c4d250584b1b5999d717b3dd17d25d3",
  "status": "completed",
  "url": "https://github.com/minekube/craftless/actions/runs/28316490956"
}
```

## Install Smoke

Command:

```sh
set -eu
tmp="$(mktemp -d /tmp/craftless-v011-install-smoke.XXXXXX)"
CRAFTLESS_VERSION=v0.1.1 CRAFTLESS_INSTALL_DIR="$tmp/bin" CRAFTLESS_HOME="$tmp/home" ./install.sh
"$tmp/bin/craftless" --help >/tmp/craftless-v011-help.out
"$tmp/bin/craftless" server start --once --port 0 --workspace "$tmp/workspace" >/tmp/craftless-v011-server-once.out
curl -fsSL https://github.com/minekube/craftless/releases/download/v0.1.1/craftless-0.1.1.tar -o "$tmp/craftless-0.1.1.tar"
tar -tf "$tmp/craftless-0.1.1.tar" | tee /tmp/craftless-v011-tar-list.out | grep -q '^craftless-0.1.1/mods/craftless-driver-fabric.jar$'
```

Observed:

```text
craftless 0.1.1 installed to /tmp/craftless-v011-install-smoke.zaWHiR/bin/craftless
/tmp/craftless-v011-install-smoke.zaWHiR/bin/craftless -> /tmp/craftless-v011-install-smoke.zaWHiR/home/0.1.1/bin/craftless
```

`craftless --help` printed the adaptive CLI command surface, including
`clients <id> openapi`, `clients <id> actions`, `clients <id> resources`,
`clients <id> events`, and `clients <id> run <action>`.

`craftless server start --once --port 0` printed:

```json
{
  "ok": true,
  "url": "http://127.0.0.1:53955",
  "openapi": "/openapi.json",
  "events": "/events",
  "workspace": "/tmp/craftless-v011-install-smoke.zaWHiR/workspace"
}
```

Published tar inspection found:

```text
craftless-0.1.1/mods/craftless-driver-fabric.jar
```

## Conclusion

The published `v0.1.1` release has tar, zip, and checksum assets. The public
install script installs the release from GitHub without cloning the repository,
the installed CLI starts the Ktor supervisor in `--once` mode, and the
published tar contains the Fabric driver mod needed by installed client
launches.
