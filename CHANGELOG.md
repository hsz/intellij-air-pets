<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# Intellij-air-pets Changelog

## [Unreleased]

### Added

- Live AIR session companions in a transparent IDE overlay, using attributed MIT-licensed snake and rubber-duck animations.
- Attention, working, review, resting, completion, and error behaviors, with click-to-open navigation.
- Interactive demo, reduced-motion and label controls, per-project preferences, and an all-sessions menu.
- AIR API/licensing research and tests for mapping, workspace isolation, animation, rendering, and hit testing.
- Completed pets' heart badges turn into checkmarks on hover and can mark the session done in AIR, independently of click-to-open navigation.
- Drag pets, together with their badges and names, anywhere inside the IDE to keep underlying content visible.
- Independently toggle per-pet AIR cost (USD, with estimated/unavailable handling) and locally observed working time, with saved per-project preferences and reduced-motion support.
- Kodee and Runzo companions based on the supplied mascot artwork, with transparent state-specific animation and smooth scaling alongside the original pixel-art pets.
- Clippy-inspired paperclip companion with newly drawn metallic artwork, blinking eyes, attention and celebration animations, automatic assignment, and demo coverage.
- Compact session hover cards after 450 ms, showing state, workspace/worktree details, independently enabled cost and observed working time, Open session, and Mark as done only for completed sessions using AIR's archive action. Cards never take keyboard focus, allow pointer entry with a 250 ms leave grace, and close on exit, Escape, outside click, window deactivation, dragging, removal, hiding, or disposal.
- Click live pets for a focused, resizable Quick Chat using AIR's native existing-session conversation and composer, with Open in AIR navigation. Escape/outside dismissal preserves the same composer and its draft in memory for the current project opening; native views are released on session removal or project close. Dragging, completed badges, demo interactions, hover cards, and full-session menus retain their existing roles.

### Changed

- Moved floating AIR Pets controls into the IDE's bottom-right status bar, keeping preferences and sessions accessible when pets are hidden.
- Session names appear immediately on hover in a compact pill beneath their pet, with existing click/drag support; the pill hides while the delayed card is open. The name preference hides both the pill and the card's actual title, using AIR session instead. Cards replace plain tooltips; hidden labels pass clicks through to the IDE.

### Fixed

- Quick Chat loads the existing AIR conversation using AIR's local/runtime thread ID instead of the external agent session ID, avoiding an empty Draft with disabled input.
