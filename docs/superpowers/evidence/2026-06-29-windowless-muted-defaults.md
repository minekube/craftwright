# Windowless Muted Defaults Evidence

Phase 196 turns the Phase 188 presentation defaults into process-launch
behavior:

- `CreateClientRequest.presentation` still defaults to
  `{ "window": "NONE", "audio": "MUTED" }`.
- `presentation.window = NONE` prefixes the Minecraft launch command with the
  Craftless windowless wrapper. The default wrapper is `xvfb-run -a
  --server-args=-screen 0 1280x720x24`, matching the Docker runtime dependency
  set. `CRAFTLESS_WINDOWLESS_WRAPPER` can point custom runtimes at another
  executable wrapper.
- `presentation.window = VISIBLE` bypasses the wrapper.
- `presentation.audio = MUTED` materializes Minecraft sound categories at
  `0.0`; `DEFAULT` audio leaves the options file untouched.

This remains lifecycle/runtime infrastructure only. It does not add static
gameplay routes, scenario shortcuts, or an alternate gameplay authority.

## Verification

```sh
mise exec -- gradle :daemon:test --tests 'com.minekube.craftless.daemon.LocalSessionApiServerTest.process client runtime launcher starts prepared command' --tests 'com.minekube.craftless.daemon.LocalSessionApiServerTest.process client runtime launcher bypasses windowless wrapper for visible clients'
```

Result: passed. The default launch used the injected `xvfb-run` wrapper and
wrote muted sound options; the visible/default-audio launch bypassed the
wrapper and did not write `options.txt`.
