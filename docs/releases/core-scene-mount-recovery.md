# Core scene mount and Safe Base recovery repair

This runtime patch closes the startup gap exposed by Monolith AI `1.2.0-beta.200008+gen1.ui2`.

- Android now injects `monolith_scene_runtime.js` before `monolith_core.js` and the voice runtime.
- Core is not marked stable until the exclusive scene host, router, and one active scene are verified.
- A failed scene mount is persisted to the Deterministic Startup Boundary instead of remaining a black viewport.
- Clearing diagnostics also clears persisted crash-loop and safe-mode state.
- Safe Base is a native recovery console with WebView disabled and no legacy home-shell rendering.
- CI validates both packaged scene assets and the compiled Android references that load them.
