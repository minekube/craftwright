# Windowless Muted Defaults Evidence

Phase 196 turns the Phase 188 presentation defaults into process-launch
behavior:

- `CreateClientRequest.presentation` still defaults to
  `{ "window": "NONE", "audio": "MUTED" }`.
- `presentation.window = NONE` prefixes the Minecraft launch command with the
  Craftless windowless wrapper when one is configured or when Linux `xvfb-run`
  is available. Hosts without an available wrapper launch directly instead of
  failing before Minecraft starts. `CRAFTLESS_WINDOWLESS_WRAPPER` can point
  custom runtimes at another executable wrapper; `none`, `disabled`, or a blank
  value disables wrapper prefixing.
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
wrote muted sound options; platform/default wrapper selection only chose
available Linux `xvfb-run`; the no-wrapper path launched directly; the
visible/default-audio launch bypassed the wrapper and did not write
`options.txt`.
