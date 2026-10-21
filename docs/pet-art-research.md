# Pet artwork research

Primary-source research, updated **2026-10-21**. Only text and Git tree/commit metadata were fetched; **no artwork, archives, extension packages, or image headers were downloaded**. This task changes only this document, not rendering, tests, assets, or the AIR integration.

## Actionable choice: green snake + yellow rubber duck, MIT

**Bundle the existing green snake and yellow rubber duck from `tonybaloney/vscode-pets`, pinned to `93a420cab4b6618bfa9928da0dd941447a145d8f`.** These two pets have a stronger verified creator/provenance chain than the other candidates below. MIT permits redistribution, commercial use, and modification, including mirroring and scaling, provided its notice is retained. No purchase or no-derivatives exception is needed for this selection. [upstream-license] [upstream-credits] [artist-license] [artist-readme]

- **Artist:** Marc Duiker. Upstream credits him for both pets, and his own portfolio identifies the rubber duck and Python as his VS Code Pets creations. [upstream-credits] [artist-site]
- **Independent creator-hosted evidence:** his fork `marcduiker/vscode-pets`, pinned to `ac75fc09468b2eb6b4ba23a05c4dadfa143b11ab`, distributes these same assets alongside the MIT license. All eight action paths listed below have **identical Git blob IDs** in that fork and the selected upstream revision, not merely matching names. Its README distinguishes the separately licensed cat/dog assets from Marc's creations. [artist-tree] [artist-license] [artist-readme] [upstream-tree]
- **License scope:** the selected upstream `LICENSE` contains plain MIT text, with **no exclusion list inside that file**. The complete tree contains only that `LICENSE` and `media/dog/license.txt` as license/notice-named files; neither selected pet has an overriding file. This supports MIT for this specifically checked, creator-attributed selection, **not a blanket MIT claim for every species**. Cats, dogs, and foxes have contrary first-party terms, detailed below. [upstream-license] [upstream-tree] [media-readme] [dog-license] [fox-author]
- **Why not two snake colors?** There is only `green` in the pinned tree and `Snake.possibleColors`; **no blue snake exists there**. The rubber duck similarly has only `yellow`. Do not invent a blue URL or recolor artwork just to make the inventory fit. If the implementation requires one species, two instances of the existing green snake are possible, but they are not two existing color variants. [upstream-tree] [snake-code] [duck-code]

**Handoff/download gate:** the parent must join/receive this result before downloading any artwork. The URLs below are a download manifest for that later step, not files downloaded or bundled by this research task.

## Exact pinned download manifest

Use `snake`/`green` and `rubber-duck`/`yellow` as the source identifiers; UI/model names are the parent's decision. These are individual GIF animations, **not sprite-sheet atlas cells**. Paths were checked against complete Git tree metadata, without requesting GIF bodies. [upstream-tree] [snake-code] [duck-code]

| Pet | Intended action | Exact pinned raw URL |
| --- | --- | --- |
| Green snake | Idle | https://raw.githubusercontent.com/tonybaloney/vscode-pets/93a420cab4b6618bfa9928da0dd941447a145d8f/media/snake/green_idle_8fps.gif |
| Green snake | Walk | https://raw.githubusercontent.com/tonybaloney/vscode-pets/93a420cab4b6618bfa9928da0dd941447a145d8f/media/snake/green_walk_8fps.gif |
| Green snake | Run, matching upstream ordinary running | https://raw.githubusercontent.com/tonybaloney/vscode-pets/93a420cab4b6618bfa9928da0dd941447a145d8f/media/snake/green_walk_fast_8fps.gif |
| Green snake | Chase/run alias, optional duplicate | https://raw.githubusercontent.com/tonybaloney/vscode-pets/93a420cab4b6618bfa9928da0dd941447a145d8f/media/snake/green_run_8fps.gif |
| Yellow rubber duck | Idle | https://raw.githubusercontent.com/tonybaloney/vscode-pets/93a420cab4b6618bfa9928da0dd941447a145d8f/media/rubber-duck/yellow_idle_8fps.gif |
| Yellow rubber duck | Walk | https://raw.githubusercontent.com/tonybaloney/vscode-pets/93a420cab4b6618bfa9928da0dd941447a145d8f/media/rubber-duck/yellow_walk_8fps.gif |
| Yellow rubber duck | Run, matching upstream ordinary running | https://raw.githubusercontent.com/tonybaloney/vscode-pets/93a420cab4b6618bfa9928da0dd941447a145d8f/media/rubber-duck/yellow_walk_fast_8fps.gif |
| Yellow rubber duck | Chase/run alias, optional duplicate | https://raw.githubusercontent.com/tonybaloney/vscode-pets/93a420cab4b6618bfa9928da0dd941447a145d8f/media/rubber-duck/yellow_run_8fps.gif |

**Minimal bundle: six GIFs**, using `idle`, `walk`, and `walk_fast` for each pet. Upstream ordinary running selects `walk_fast`; chasing selects `run`. For these particular pets, `walk_fast` and `run` have identical blobs, so downloading both is unnecessary. [states] [upstream-tree]

| Pet/action | Verified Git blob ID in both repositories |
| --- | --- |
| Snake idle | `14116f290dd51a5d085a2da76dcc54d75c7e43c4` |
| Snake walk | `603aec83dc7e6389e730763d31c2f1803c494ce4` |
| Snake walk_fast / run | `1e2ef6c8c2e0dbb865d72953f6be93e0a49fc85b` |
| Duck idle | `daa812f56078484737e866195ba3df54661bad3f` |
| Duck walk | `67a23261eb3dfc293fd9eb369a9d58e3fcdeed15` |
| Duck walk_fast / run | `8876c733c62416838d70b76f9fce59e2734c9b39` |

These are **Git blob identifiers, not ordinary file SHA-1 checksums**. They establish matching repository objects through metadata; no local image decoding or checksum verification was performed. [upstream-tree] [artist-tree]

### Animation limitations for the parent

- **No sleep/lie GIFs** exist for either selected pet. Do not construct `_sleep_8fps.gif` or `_lie_8fps.gif` URLs. Optional `swipe` and `with_ball` files exist, but are unnecessary for the prototype. [upstream-tree]
- Both source state machines support idle, walking, running, and chasing. Upstream uses the same animation for left/right and applies `scaleX(-1)` / `scaleX(1)` to face it. MIT allows equivalent rendering transforms; exact original facing still needs visual inspection. [snake-code] [duck-code] [base-code] [upstream-license]
- **Native canvas dimensions, decoded frame counts, delays, transparency, disposal behavior, and visual quality remain unverified.** `_8fps` is a naming convention, not proof of a particular frame count or delay. After the mandatory join, the parent should inspect these six files before defining frame extraction or sizing assumptions. This research did not inspect binary artwork.
- Preserve actual GIF timing and logical canvas composition when decoding; do not infer a uniform sprite-sheet grid. A prototype can remain idle instead of inventing an unavailable sleeping animation.

## License owner text and distribution notices

**Exact copyright line in both inspected licenses:** `Copyright (c) 2022 Anthony Shaw`. **Artwork creator credit:** `Marc Duiker`. Do not replace the existing copyright line with an invented Marc Duiker copyright year; retain the actual license and add his creator credit separately. [upstream-license] [artist-license] [upstream-credits]

Later implementation must package the **complete MIT text below** with the assets in the distributed plugin, and retain it in source-distribution notices. A link or this development research note alone is not a substitute for including the notice. Suggested accompanying attribution:

> Green snake and yellow rubber duck animations by Marc Duiker (https://marcduiker.dev/pixelart), from VS Code Pets (https://github.com/tonybaloney/vscode-pets), revision `93a420cab4b6618bfa9928da0dd941447a145d8f`, `media/snake/green_*` and `media/rubber-duck/yellow_*`. Distributed under the MIT License, Copyright (c) 2022 Anthony Shaw. Rendering may mirror/scale the animations.

List the actual six selected files beside that attribution. If later implementations edit/recolor files, describe those changes separately rather than claiming unchanged artwork. Do not imply artist or upstream endorsement. [upstream-license] [upstream-credits]

Pinned raw notice URL: https://raw.githubusercontent.com/tonybaloney/vscode-pets/93a420cab4b6618bfa9928da0dd941447a145d8f/LICENSE

```text
MIT License

Copyright (c) 2022 Anthony Shaw

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

## Focused disposition of the requested alternatives

Inventory and absence of per-directory licenses below refer only to the pinned upstream tree. The root MIT file itself does not enumerate excluded species; separate artist terms must still be respected. [upstream-tree] [upstream-license]

| Species / existing variants | Creator and primary terms | Decision |
| --- | --- | --- |
| **Snake / green** | Marc Duiker; root MIT, matching creator-hosted assets and MIT notice. [upstream-credits] [artist-license] [artist-tree] | **Selected.** No blue variant. |
| **Rubber duck / yellow** | Marc Duiker; same independently checked MIT provenance. [upstream-credits] [artist-license] [artist-tree] | **Selected.** Same artist/license as snake. |
| **Rocky / gray** | Marc Duiker, explicitly credited in both upstream and his fork; root MIT, no separate license file in `media/rocky/`. [upstream-credits] [artist-readme] [upstream-tree] | Reasonable MIT fallback, but one color and no need to expand the selected six-file bundle. |
| **Crab / red** | Marc Duiker's Ferris animations: root MIT and explicit credit. Original Ferris concept: Karen Rustad Tölva; her Ferris site explicitly publishes the original under **CC0**. CC0 for the concept is not a reason to strip MIT notices from Marc's animations. [upstream-credits] [ferris-author] [upstream-license] | Permissive alternative; retain both creator credits if chosen. Not needed for this bundle. |
| **Chicken / white, brown, gray** | Root MIT, no local override found. The original animation commit is by **Gulnur Baimukhambetova** ([gulyapulya](https://github.com/gulyapulya)); brown was added by **ToMansion** ([Tomansion](https://github.com/Tomansion)), gray by **tcheffert** ([tcheffert](https://github.com/tcheffert)). Unlike the selected pair, this task did not establish matching assets in an artist-controlled MIT release. [chicken-original] [chicken-brown] [chicken-gray] [upstream-tree] | Existing same-species color set, but not the strongest verified creator chain. Prefer the selected Marc pair; do not attribute the chickens to Marc. |
| **Fox / red, white** | **Elthen**. The creator's pack page links his licensing page and explains **CC BY-NC with supplementary commercial permission excluding blockchain-related projects**. That is not plain MIT, CC0, or CC BY. The linked Patreon license page returned HTTP 403, so its full current wording was not independently read; the creator's explanation on itch.io is accessible. [upstream-credits] [fox-author] [fox-license] | **Reject for this requested plain-permissive-license bundle**, despite available lie animations. Do not infer MIT from absence of `media/fox/license.txt`. |

**Earlier candidates remain rejected:** Catset is a commercial asset pack whose individual assets may not be openly redistributed; upstream intentionally omits `media/cat/`. Dogs have an explicit `CC BY-ND 4.0` override, so avoid them for this modification-friendly bundle. See the original creator terms, not the repository's MIT badge. [media-readme] [cat-author] [dog-license] [dog-author]

## Verification and scope

Verified all eight selected paths exist in the complete pinned upstream tree, their blob IDs match the credited artist's pinned fork, and each `run`/`walk_fast` pair is identical. Verified both license texts, credits, artist portfolio, requested candidate inventories, and the explicit Catset/dog/fox licensing boundaries. No claim is made that binary dimensions or animation decoding were tested, or that every upstream contributor's chain of title is independently warranted. No assets downloaded; no application code, tests, configuration, or other documentation changed; no build needed for this documentation-only research.

## Primary sources

[upstream-tree]: https://api.github.com/repos/tonybaloney/vscode-pets/git/trees/93a420cab4b6618bfa9928da0dd941447a145d8f?recursive=1
[upstream-license]: https://github.com/tonybaloney/vscode-pets/blob/93a420cab4b6618bfa9928da0dd941447a145d8f/LICENSE
[upstream-credits]: https://github.com/tonybaloney/vscode-pets/blob/93a420cab4b6618bfa9928da0dd941447a145d8f/docs/credits.md
[artist-tree]: https://api.github.com/repos/marcduiker/vscode-pets/git/trees/ac75fc09468b2eb6b4ba23a05c4dadfa143b11ab?recursive=1
[artist-license]: https://github.com/marcduiker/vscode-pets/blob/ac75fc09468b2eb6b4ba23a05c4dadfa143b11ab/LICENSE
[artist-readme]: https://github.com/marcduiker/vscode-pets/blob/ac75fc09468b2eb6b4ba23a05c4dadfa143b11ab/README.md
[artist-site]: https://marcduiker.dev/pixelart
[snake-code]: https://github.com/tonybaloney/vscode-pets/blob/93a420cab4b6618bfa9928da0dd941447a145d8f/src/panel/pets/snake.ts
[duck-code]: https://github.com/tonybaloney/vscode-pets/blob/93a420cab4b6618bfa9928da0dd941447a145d8f/src/panel/pets/rubberduck.ts
[states]: https://github.com/tonybaloney/vscode-pets/blob/93a420cab4b6618bfa9928da0dd941447a145d8f/src/panel/states.ts
[base-code]: https://github.com/tonybaloney/vscode-pets/blob/93a420cab4b6618bfa9928da0dd941447a145d8f/src/panel/basepettype.ts
[ferris-author]: https://rustacean.net/
[chicken-original]: https://github.com/tonybaloney/vscode-pets/commit/cad11be14290468bbf80c645fe1d370b74e5bd28
[chicken-brown]: https://github.com/tonybaloney/vscode-pets/commit/ac342d090daad09b90b8f111247a95120c1773d7
[chicken-gray]: https://github.com/tonybaloney/vscode-pets/commit/6554662a53ef093b7598598b77e51f95128a1f38
[fox-author]: https://elthen.itch.io/2d-pixel-art-fox-sprites
[fox-license]: https://www.patreon.com/posts/licensing-27430241
[media-readme]: https://github.com/tonybaloney/vscode-pets/blob/93a420cab4b6618bfa9928da0dd941447a145d8f/media/README.md
[cat-author]: https://seethingswarm.itch.io/catset
[dog-license]: https://github.com/tonybaloney/vscode-pets/blob/93a420cab4b6618bfa9928da0dd941447a145d8f/media/dog/license.txt
[dog-author]: https://nvph-studio.itch.io/dog-animation-4-different-dogs
