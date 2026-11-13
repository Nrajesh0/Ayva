# Notes widget font stability

## Investigation (2026-10-23)

The existing preference isolation test verified saved enum values only. It never applied a RemoteViews row, measured TextView size, or exercised recycled views.

Identified rendering weaknesses:

- Header updates ran in independent IO jobs. A slow render could publish an older settings snapshot after a later render.
- The collection adapter URI contained only the widget ID. A typography change reused the same launcher/service cache identity.
- Header and rows delegated SP conversion to the host independently. TextView XML did not explicitly disable inherited auto-size.
- Empty/error headers and the platform-provided loading row did not consistently use the configured font size.
- The factory stored content, config and current note in separate mutable fields, allowing mixed snapshots during refresh callbacks.

These are code-level failure paths, not proof that every one caused the reported device symptom. The user reports a Poco X6 Pro running Android 16; launcher and reproduction trigger remain unconfirmed. No device reproduction is available yet.

## Changes

`NoteWidgetTypography` converts the configured SP through the app's current display metrics and sends explicit PX in RemoteViews, preserving system font scaling. Auto-size is disabled in XML; its runtime setter is not allowed in RemoteViews. Header, empty/error states, both row types, and the explicit blank loading row use this helper.

The adapter URI includes a renderer version, widget ID, size, display density and font scale. Rebinding after a typography change cannot select an old size cache. Note switches and ordinary refreshes retain the existing adapter identity.

Provider rendering is serialized, and obsolete settings snapshots are rejected before publishing. The factory publishes its note, content and configuration as one immutable snapshot. XML defaults provide consistent initial inflation and explicitly opt out of auto-size.

## Verification

Passed all 11 focused checks: six actual RemoteViews rendering/cache checks across Android 9 and Android 16, plus five existing widget regression checks. Production/resource compilation and `git diff --check` passed. The first rendering run caught Android rejecting a runtime auto-size setter; that unsupported call was removed, and the corrected XML-based approach passed on both versions. No physical Poco/launcher validation has been performed in this environment.

Device acceptance: retain two widgets at different sizes, edit and switch notes, scroll rows, toggle checklist entries, resize, leave and return to home, then restart the launcher. Verify chosen sizes remain stable. Change Android font size deliberately and verify the widget respects it on refresh. Capture launcher/OS information if a discrepancy remains.
