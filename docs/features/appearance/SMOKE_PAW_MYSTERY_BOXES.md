# Smoke Paw mystery boxes — 2.0.23

The mystery-box dialog uses the approved portrait Smoke Paw artwork, with a shared kitten, red scarf, charcoal/ivory ink style and tier-specific accents and choreography.

| Final rolled tier | Opening |
| --- | --- |
| Quantum / Common | Short hop, single cut, compact cyan burst |
| Aurora / Rare | Scarf charge, low sweeping double strike, indigo accents |
| Celestial / Epic | Teleport, overhead plunge, violet star halo |
| Solaris / Legendary | Ink-cat guardian, seal break, layered amber solar burst |

The same dialog serves the chat window and Daily Quests. The base chest can upgrade under the existing rarity rules; the animation uses the final rolled tier. No probability table, XP/gold range, freeze cap or overflow conversion was changed.

## Award and lifecycle handling

`DailyQuestManager.openMysteryChest` checks the availability of the original source chest and performs the existing award calculation once under its synchronized operation. It participates in the data-maintenance write gate, so backup/restore cannot split that operation. The native dialog saves the resolved award and start time across screen recreation. Skip only ends presentation, and Collect only delivers the existing result to the caller; neither rolls or credits again. An unavailable chest cannot be opened again.

The artwork is generated from the bundled `smoke_paw_chest.html` vector scene by a private, offscreen rasterizer. It contains no sample reward amounts or user data. The browser engine is never attached to an app window: decoded animation frames are drawn by normal Compose Image nodes, avoiding platform WebView/dialog surface and elevation defects. Four reusable bitmap buffers bound frame memory. Frames are generated only while resumed; static states render once. Current Android targets the display cadence, with a lower update rate on older devices.

Network loads, file/content access, storage, navigation and popups are disabled; there is no JavaScript interface. Only numeric tier, time and reduced-motion state enter the rasterizer. XP, coins, freezes, controls and accessibility announcements are native Compose UI.

System-disabled animations reveal the result immediately. Skip remains available while opening. Renderer initialization failure falls back to a static chest and never traps a granted reward behind loading. The private engine follows the activity lifecycle and is destroyed when the dialog closes. Native content respects safe drawing insets; action targets are at least 48 dp. A stable, scrollable reward area avoids changing the artwork size during the reveal.

## Focused verification

- Economy regression checks: one award per source chest, unavailable chests, original legendary freeze overflow.
- Android device checks: actual on-screen native frames for all tiers; saved state, skip and single Collect delivery.
- Chat/dictionary boundary guards and debug/release build checks.

The earlier design-preview videos remain outside the source repository; the app packages only the small vector renderer, not videos or sample rewards.
