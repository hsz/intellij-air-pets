# AIR Pets

A working IntelliJ IDEA plugin prototype that turns **AIR agent sessions into animated companions** above the IDE's bottom edge. **Clippy, Kodee, Runzo, green snakes, and yellow rubber ducks** accompany agents while they work, ask for attention when input is needed, rest, and celebrate results.

## Try it

Requires IntelliJ IDEA **2026.2** and the **AIR plugin**. This prototype compiles against IDEA **2026.2.3** and AIR **262.8665.497**. AIR's integration APIs are internal; other AIR versions are not guaranteed compatible.

```bash
./gradlew runIde -PairPetsDemo=true
```

In the sandbox IDE, open a project. Demo mode displays six fictional sessions covering all five species and every state; **click a pet to cycle its state**. It never creates, runs, or changes a real AIR session.

For normal use, run `./gradlew runIde`, open a project, and create or resume sessions in AIR. The overlay updates from AIR's live session catalog, without opening its tool window.

### Controls

- **Tools → AIR Pets → Show AIR Pets** toggles the overlay, including restoring it after hiding.
- **Tools → AIR Pets → AIR Pets Demo Mode** switches between clearly labeled demo and live data.
- Click a live pet to open that session in AIR; hover for its full name and state.
- **Show session names on hover** reveals a name directly beneath its pet only while hovering the pet, badge, or revealed name. Long names are shortened to keep labels compact; the tooltip retains the full name.
- **Show session cost** and **Show session time** independently enable a compact readout beneath each pet, for example `≈$0.42 · 1m 23s`. Both are off by default and saved per project; readouts remain visible without hovering, even with animation or names disabled. Hovered names appear below the readout.
- Costs come from AIR in **USD**; `≈` marks an estimate and tiny positive costs show `<$0.01`, not zero. Unsupported/unreported costs are omitted. Time means **observed working time during the current project opening**, not the historical session total: it accumulates while AIR reports working, pauses while waiting/reviewing/resting/completed or disconnected, and resets when the project closes. Sessions not yet observed working have no time value; the tooltip explains missing values. Demo metrics are fictional samples.
- Hover a completed pet's green **♥** badge to reveal **✓**, then click to **Mark as done** using AIR's own per-session Done action (archive). The pet disappears when AIR confirms it is archived; clicking the pet or its name still opens the session. In demo mode, this only removes the fictional pet.
- Drag a pet to move it out of the way anywhere inside the IDE. Its name and badge move with it, and it stays at the dropped location for the current overlay lifetime; positions are not saved across IDE restarts. A drag never opens or finishes a session.
- Click the **AIR icon + Pets** control in the bottom-right IDE status bar, or right-click a pet, for animation, name-label, demo, and visibility controls, a state guide, and **All sessions** navigation. The status-bar widget remains available when pets are hidden or there are no sessions; it can be toggled from the status bar's context menu as **AIR Pets**.
- Both menus use IntelliJ's native popup styling, toggle checkmarks, and keyboard navigation. Start typing to search the menu or session submenu.
- Turn off **Animate pets** for reduced motion. Visibility, animation, label, cost, and time preferences are saved per project; demo mode is not saved. Working time continues updating once per second without pet animation.

The transparent background passes mouse events through to the editor/tool windows. Only pet tiles, visible labels, and badges are interactive. Pets do not take keyboard focus.

## Session behavior

| AIR signal | Companion behavior |
| --- | --- |
| Processing | Runs or plays its working animation while wandering; blue `…` badge |
| Needs input / attention / waiting for predecessor | Stays nearby and bobs for attention; amber `?` |
| Reviewing | Walks slowly; purple `✓` |
| Ready / draft / paused | Rests on a still idle frame; gray `zZ` |
| Unread result / completed occurrence / Done folder | Bounces in celebration; green `♥` |
| Stopped with error | Bobs for attention; red `!` |

Active processing/input/review signals override stale completion/error information. AIR's frontend reports **READY**, not a distinct paused state, so the prototype labels that state **Ready / paused** rather than inferring backend execution status. An unread result is displayed as a completion signal, not proof that the entire task has been accepted.

Only non-archived sessions in the current project's AIR workspace and its worktrees are shown. Opening a worktree separately scopes it to that worktree. Identity and species are stable across title/status changes. Removed/archived sessions disappear. Available width limits the overlay to at most 12 pets; attention requests take priority, the status-bar widget shows the total session count, and **All sessions** retains access to every session.

Species are assigned automatically from the session identity. Kodee uses the supplied floating, walking, loading, greeting, and happy animations. Runzo uses short loops prepared from the supplied vector poses. Clippy is a newly drawn paperclip interpretation of the supplied reference, with metallic shading, blinking eyes, a questioning tilt, and a celebratory wink. All three have transparent backgrounds, attention and celebration clips, and smooth scaling; snake and duck retain crisp pixel-art rendering. All five share the same badges, hover names, optional metrics, dragging, and click actions.

## Build and test

Use JDK 21+; Gradle resolves the Java 21 compilation toolchain.

```bash
./gradlew test
./gradlew buildPlugin
```

Install `build/distributions/intellij-air-pets-1.0.0-SNAPSHOT.zip` using **Settings → Plugins → gear → Install Plugin from Disk**, with AIR installed and enabled.

Tests cover typed AIR discovery, project/worktree isolation, state precedence, navigation, marking sessions done, stable identities, motion bounds, bundled GIF decoding, rendering, dragging, and click-through behavior. The rendering test writes a preview to `build/reports/air-pets/demo.png`.
Mascot galleries for every state at 100% and 200% scale, on light and dark backgrounds, are written to `build/reports/air-pets/mascots-{light,dark}-{100,200}.png`.

The scaffold's IntelliJ Platform reports are also useful:

```bash
./gradlew intellijPlatformInfo intellijPlatformSdk intellijPlatformDoctor intellijPlatformSources
```

Reports are written to `build/reports/intellij-platform/`.

## Implementation and limitations

- A project service collects AIR's `StateFlow`; no catalog polling or session-log scraping. Costs update with AIR snapshots. A separate one-second UI refresh updates enabled working-time readouts, including in reduced-motion mode; it does not query AIR. Time tracking uses a monotonic clock, continues while pets are hidden or demo mode is active, and pauses on a lost connection.
- A non-opaque Swing component lives in the frame's palette layer, leaving the IDE glass pane and popup layers intact. A shared 10 Hz timer drives visible pets; it stops when hidden, empty, or disposed. GIFs are decoded off the UI thread with their frame timing and disposal metadata.
- This initial version supports ordinary local project frames. Detached editor windows, remote-development frontend/backend separation, configurable species, sounds, and lifecycle commands other than marking completed sessions done are not implemented.
- There is no network access, telemetry, or AI interaction in this plugin at runtime. Artwork is bundled. AIR itself remains responsible for agent execution and its network access.
- If AIR discovery fails, pets are cleared, the status-bar widget reports the connection problem in its tooltip and menu, and the plugin retries. Demo remains available.

Research and API evidence: [AIR integration](docs/air-api-research.md), [artwork licensing](docs/pet-art-research.md).

## Artwork credits

Snake and rubber duck animations by **[Marc Duiker](https://marcduiker.dev/pixelart)**, from [VS Code Pets](https://github.com/tonybaloney/vscode-pets), pinned to `93a420cab4b6618bfa9928da0dd941447a145d8f`. Six original GIFs are bundled under **MIT**, Copyright (c) 2022 Anthony Shaw; they are scaled/mirrored only at rendering time. Full attribution and license are included in the distributed plugin at [`pets/THIRD-PARTY-NOTICES.txt`](src/main/resources/pets/THIRD-PARTY-NOTICES.txt).

**Kodee by JetBrains s.r.o. is licensed under [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/)**; see the [official mascot usage guidelines](https://kotlinfoundation.org/guidelines). The five supplied GIFs are bundled unchanged apart from their filenames and scaled/mirrored at runtime. No endorsement is implied.

**Runzo artwork by JetBrains**, prepared from the supplied Illustrator poses/expressions and character guide. The transparent GIF loops are new pose-rocking adaptations, not official animation cycles. **No redistribution license was supplied or verified: confirm authorization before publishing the prototype or its derived Runzo artwork.** See [mascot provenance, state mapping, and reproducible preparation](docs/mascot-artwork.md); each mascot has a separate section in the packaged third-party notices.

**Clippy-inspired paperclip**, newly drawn from the supplied visual reference using `tools/prepare_clippy.py`. No Microsoft image files or Office animations are bundled. Clippy/Clippit is Microsoft's character; these are unofficial interpretations with no endorsement or verified character-usage permission. Review character rights before public redistribution. See [artwork preparation](docs/mascot-artwork.md#clippy).

Commercial cat sprites and no-derivatives dog sprites were deliberately not bundled.
