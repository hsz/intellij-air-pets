# Prototype verification

Verified on 2026-10-21, macOS arm64, IDEA 2026.2.3 (`IU-262.10968.63`), AIR `262.8665.497`, JBR `25.0.4+1-b508.27`; compilation toolchain Java 25.

## Automated checks

```bash
./gradlew test buildPlugin verifyPluginStructure verifyPluginProjectConfiguration --console=plain
```

Latest validation: `./gradlew test buildPlugin verifyPluginStructure --console=plain` completed with **BUILD SUCCESSFUL**. 41 tests, zero failures, errors, or skipped tests:

- 20 typed AIR adapter tests: state mapping/precedence, root/worktree scoping, unknown workspaces, archives, stable IDs, disappearance, navigation, and marking sessions done with success/failure/timeout/cancellation coverage.
- 6 model tests: attention priority, capacity, deduplication, stable species, bounded movement, and demo coverage.
- 2 sprite tests: all six bundled GIFs decode, contain frames/pixels, loop, and cover every state.
- 11 overlay tests: rendering, mouse hit testing, callbacks, empty states, palette-layer ordering, zoom, heart/checkmark hover, separate finish/open clicks, dragging, retained manual positions, bounds, and click-through behavior.
- 2 native status-bar widget tests: availability while hidden/empty and extension registration.

The heart-hover regression was run before implementation and failed. Final gesture tests pass at 100% and 200% scale; IDE compilation also succeeded, and `git diff --check` found no whitespace errors. Marking done uses AIR's individual-session archive action; only typed fixtures and injected endpoint responses were used, with no real session archived. Drag positions are retained in memory, not across IDE restarts. The sandbox smoke test below predates these new interactions.

The sandbox exposed a Kotlin overload bug where `add(component, PALETTE_LAYER)` selected the integer-index overload. A new regression test was run **before the fix** and failed (`expected 100, actual 0`). Explicitly assigning the layer made it pass; the final full suite passes with unchanged assertions.

Package inspected: `build/distributions/intellij-air-pets-1.0.0-SNAPSHOT.zip`, with plugin classes, descriptor, all six GIFs, and `pets/THIRD-PARTY-NOTICES.txt`. No AIR/platform libraries or test classes are bundled. Six downloaded GIF blob hashes match the pinned upstream inventory recorded in [artwork research](pet-art-research.md); each canvas is 32×32 pixels.

## Sandbox smoke test

```bash
./gradlew runIde -PairPetsDemo=true --args="<repository>/build/demo-project"
```

A disposable project under `build/` was used. For this unattended smoke test only, `idea.trust.all.projects=true` was passed to the sandbox JVM to avoid an interactive trust dialog; it is not configured in the plugin or Gradle task defaults.

- Both AIR and AIR Pets loaded in the real IDE.
- Final run at 11:51 logged `AIR Pets overlay attached (demo=true)`.
- A capture of only the sandbox window confirmed pets rendered above project/editor content, below ordinary IDE notifications. The previous layer-overload warning no longer appeared.
- Rendering preview: [demo-preview.png](demo-preview.png). Generated from the same component in the automated test; not an authenticated agent task.
- The sandbox was stopped after verification. No task prompt was submitted and no real agent execution/input-request cycle was driven end-to-end. Live integration is covered by typed AIR fixtures, not a claimed authenticated-agent smoke test.

## Known limitations and diagnostics

- AIR's frontend catalog collapses backend paused/ready distinctions. The overlay uses the explicit **Ready / paused** label, not an inferred backend status.
- Internal AIR APIs are version-sensitive. Compatibility is intentionally bounded to `262.*`. The configuration checker recommends removing this upper bound; that forward-compatibility recommendation is deliberately not followed.
- AIR itself emits a startup diagnostic for `AirCloudAgentDescriptorKt` accessing `StructuredSessionSourceFactory` during static initialization, attributed by IDEA to AIR `262.8665.497`. The sandbox also reports daemon discovery/distribution warnings. These external diagnostics were not suppressed or patched; they did not prevent the pet overlay from rendering.
- Earlier IDE build/inspection calls timed out; the current IDE build succeeds. AIR internal-API inspections still flag the prototype's pinned integration as unsuitable for Marketplace publication.
- This is an initial local-frame prototype, not a remote-development integration or a Marketplace-ready compatibility guarantee. See the [README](../README.md) for controls and deferred features.