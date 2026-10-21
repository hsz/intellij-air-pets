# Air Pets

A working IntelliJ IDEA plugin prototype that turns **Air agent sessions into animated companions** above the IDE's bottom edge. **Clippy, Kodee, Runzo, green snakes, and yellow rubber ducks** accompany agents while they work, ask for attention when input is needed, rest, and celebrate results.

## Try it

Requires IntelliJ IDEA **2026.2** and the **Air plugin**. This prototype compiles against IDEA **2026.2.3** and Air **262.8665.497**. Air's integration APIs are internal; other Air versions are not guaranteed compatible.

```bash
./gradlew runIde -PairPetsDemo=true
```

In the sandbox IDE, open a project. Demo mode displays six fictional sessions covering all five species and every state; **click a pet to cycle its state**. It never creates, runs, or changes a real Air session.

For normal use, run `./gradlew runIde`, open a project, and create or resume sessions in Air. The overlay updates from Air's live session catalog, without opening its tool window.

### Controls

- **Tools → Air Pets → Show Air Pets** toggles the overlay, including restoring it after hiding.
- **Tools → Air Pets → Air Pets Demo Mode** switches between clearly labeled demo and live data.
- Click a live pet to open **Quick Chat** beside it: Air's native conversation and reply composer, bound to the existing session. The popup is titled with the session name and follows name changes, falling back to **Untitled session** for blank names. It takes keyboard focus, is resizable/movable, and includes **Open in Air** for the full view. Native questions and permission controls remain Air's responsibility; no simplified chat protocol is substituted. Unsupported/non-native sessions offer the full Air view instead. Demo clicks still cycle fictional states.
- **Escape** or an outside click closes Quick Chat. Reopening a pet reuses its composer, preserving unsent text and attachments **during this project opening**. Hidden composers are retained in memory until their session is removed/archived or the project closes, not saved by this plugin across restarts. Popup and full Air composers are separate: drafts are not live-synchronized between them; avoid editing both at once.
- Hover for **450 ms** to open a compact card with the full title, state, workspace path and worktree branch when available, and an **Open session** action that still navigates to full Air. Only completed pets offer **Mark as done**, using Air's own per-session Done action (archive), not a stop, pause, or review command. Demo actions affect fictional sessions only.
- **Show session names on hover** reveals the compact name pill immediately beneath the pet; long names are shortened. The pill hides while the delayed card is open, avoiding duplicate names, and supports click-to-chat and dragging. Turning this preference off hides the pill and replaces the card's actual title with **Air session**.
- **Show session cost** and **Show session time** independently enable a compact readout beneath each pet, for example `≈$0.42 · 1m 23s`, and their corresponding card rows. Both are off by default and saved per project; readouts remain visible without hovering, even with animation or names disabled. Hovered name pills appear below the readout.
- Costs come from Air in **USD**; `≈` marks an estimate and tiny positive costs show `<$0.01`, not zero. Unsupported/unreported costs are omitted from readouts. Time means **observed working time during the current project opening**, not the historical session total: it accumulates while Air reports working, pauses while waiting/reviewing/resting/completed or disconnected, and resets when the project closes. Sessions not yet observed working have no time value; enabled card rows explain missing values. Demo metrics are fictional samples.
- Hover a completed pet's green **♥** badge to reveal **✓**, then click to **Mark as done** using Air's own per-session Done action (archive). The pet disappears when Air confirms it is archived; clicking the pet or its name opens Quick Chat. In demo mode, this only removes the fictional pet.
- Drag a pet to move it out of the way anywhere inside the IDE. Its name and badge move with it, and it stays at the dropped location for the current overlay lifetime; positions are not saved across IDE restarts. A drag never opens or finishes a session.
- Click the **Air icon + Pets** control in the bottom-right IDE status bar, or right-click a pet, for animation, name-label, demo, and visibility controls, a state guide, and **All sessions** navigation. The status-bar widget remains available when pets are hidden or there are no sessions; it can be toggled from the status bar's context menu as **Air Pets**.
- Both menus use IntelliJ's native popup styling, toggle checkmarks, and keyboard navigation. Start typing to search the menu or session submenu.
- Turn off **Animate pets** for reduced motion. Visibility, animation, label, cost, and time preferences are saved per project; demo mode is not saved. Working time continues updating once per second without pet animation.

The transparent background passes mouse events through to the editor/tool windows. Only pet tiles, visible labels, badges, and open popups are interactive. Pets and hover cards never take keyboard focus; Quick Chat intentionally does. The card uses a native non-focusable popup rather than a plain tooltip; a **250 ms** leave grace lets the pointer move into it without closing it. It closes when the pointer leaves the pet/card, on Escape, an outside click, window deactivation, dragging, session removal, hiding pets, or overlay disposal. Hover cards are suppressed while Quick Chat is open, and its pet stays still. Hiding pets or entering demo mode dismisses Quick Chat without discarding its cached draft.

## Session behavior

| Air signal | Companion behavior |
| --- | --- |
| Processing | Runs or plays its working animation while wandering; blue `…` badge |
| Needs input / attention / waiting for predecessor | Stays nearby and bobs for attention; amber `?` |
| Reviewing | Walks slowly; purple `✓` |
| Ready / draft / paused | Rests on a still idle frame; gray `zZ` |
| Unread result / completed occurrence / Done folder | Bounces in celebration; green `♥` |
| Stopped with error | Bobs for attention; red `!` |

Active processing/input/review signals override stale completion/error information. Air's frontend reports **READY**, not a distinct paused state, so the prototype labels that state **Ready / paused** rather than inferring backend execution status. An unread result is displayed as a completion signal, not proof that the entire task has been accepted.

Only non-archived sessions in the current project's Air workspace and its worktrees are shown. Opening a worktree separately scopes it to that worktree. Identity and species are stable across title/status changes. Removed/archived sessions disappear. Available width limits the overlay to at most 12 pets; attention requests take priority, the status-bar widget shows the total session count, and **All sessions** retains access to every session.

Species are assigned automatically from the session identity. Kodee uses the supplied floating, walking, loading, greeting, and happy animations. Runzo uses short loops prepared from the supplied vector poses. Clippy is a newly drawn paperclip interpretation of the supplied reference, with metallic shading, blinking eyes, a questioning tilt, and a celebratory wink. All three have transparent backgrounds, attention and celebration clips, and smooth scaling; snake and duck retain crisp pixel-art rendering. All five share the same badges, hover names and cards, optional metrics, dragging, and click actions.

## Build and test

Use JDK 21+; Gradle resolves the Java 21 compilation toolchain.

```bash
./gradlew test
./gradlew buildPlugin
```

Install `build/distributions/intellij-air-pets-1.0.0-SNAPSHOT.zip` using **Settings → Plugins → gear → Install Plugin from Disk**, with Air installed and enabled.

Tests cover typed Air discovery, project/worktree isolation, state precedence, navigation, marking sessions done, stable identities, motion bounds, bundled GIF decoding, rendering, dragging, and click-through behavior. The rendering test writes a preview to `build/reports/air-pets/demo.png`.
Mascot galleries for every state at 100% and 200% scale, on light and dark backgrounds, are written to `build/reports/air-pets/mascots-{light,dark}-{100,200}.png`.

The scaffold's IntelliJ Platform reports are also useful:

```bash
./gradlew intellijPlatformInfo intellijPlatformSdk intellijPlatformDoctor intellijPlatformSources
```

Reports are written to `build/reports/intellij-platform/`.

## Implementation and limitations

- A project service collects Air's `StateFlow`; no catalog polling or session-log scraping. Costs update with Air snapshots. A separate one-second UI refresh updates enabled working-time readouts, including in reduced-motion mode; it does not query Air. Time tracking uses a monotonic clock, continues while pets are hidden or demo mode is active, and pauses on a lost connection.
- A non-opaque Swing component lives in the frame's palette layer, leaving the IDE glass pane and popup layers intact. A shared 10 Hz timer drives visible pets; it stops when hidden, empty, or disposed. GIFs are decoded off the UI thread with their frame timing and disposal metadata.
- This initial version supports ordinary local project frames. Detached editor windows, remote-development frontend/backend separation, configurable species, sounds, and lifecycle commands other than marking completed sessions done are not implemented.
- No independent network client or telemetry is added. Artwork is bundled. Quick Chat embeds Air's native UI; sending a reply or responding to its controls can continue agent execution and incur normal Air costs. Air itself remains responsible for agent execution and its network access.
- Quick Chat uses Air's internal native-view provider, not reflection, transcript scraping, or reparenting Air's mounted tool window. It never creates a session on click. Its cached native views are project-scoped because this Air version has no typed composer-flush hook; they are released on confirmed session removal and project disposal, but survive temporary discovery outages. Subagent navigation and the full session menu remain in the full Air view.
- If Air discovery fails, pets are cleared, the status-bar widget reports the connection problem in its tooltip and menu, and the plugin retries. Demo remains available.

Research and API evidence: [Air integration](docs/air-api-research.md), [artwork licensing](docs/pet-art-research.md).

## Artwork credits

Snake and rubber duck animations by **[Marc Duiker](https://marcduiker.dev/pixelart)**, from [VS Code Pets](https://github.com/tonybaloney/vscode-pets), pinned to `93a420cab4b6618bfa9928da0dd941447a145d8f`. Six original GIFs are bundled under **MIT**, Copyright (c) 2022 Anthony Shaw; they are scaled/mirrored only at rendering time. Full attribution and license are included in the distributed plugin at [`pets/THIRD-PARTY-NOTICES.txt`](src/main/resources/pets/THIRD-PARTY-NOTICES.txt).

**Kodee by JetBrains s.r.o. is licensed under [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/)**; see the [official mascot usage guidelines](https://kotlinfoundation.org/guidelines). The five supplied GIFs are bundled unchanged apart from their filenames and scaled/mirrored at runtime. No endorsement is implied.

**Runzo artwork by JetBrains**, prepared from the supplied Illustrator poses/expressions and character guide. The transparent GIF loops are new pose-rocking adaptations, not official animation cycles. **No redistribution license was supplied or verified: confirm authorization before publishing the prototype or its derived Runzo artwork.** See [mascot provenance, state mapping, and reproducible preparation](docs/mascot-artwork.md); each mascot has a separate section in the packaged third-party notices.

**Clippy-inspired paperclip**, newly drawn from the supplied visual reference using `tools/prepare_clippy.py`. No Microsoft image files or Office animations are bundled. Clippy/Clippit is Microsoft's character; these are unofficial interpretations with no endorsement or verified character-usage permission. Review character rights before public redistribution. See [artwork preparation](docs/mascot-artwork.md#clippy).

Commercial cat sprites and no-derivatives dog sprites were deliberately not bundled.
