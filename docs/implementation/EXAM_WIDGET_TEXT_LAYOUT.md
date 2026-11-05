# Exam widget text, layout and preview refinement

Release: 2.0.42 (142).

## Problems addressed

A one-exam widget using LIST mode rendered a small row inside a large surface, including the redundant “1 EXAMS” heading and dense date/time/timezone text. The editor's illustrative design cards did not describe the configured widget's real arrangement. Pairing minimum width with minimum height could also select dimensions that never occur on the launcher.

## Rendering

- A list with only one visible exam uses the full single-exam design, including when finished exams are filtered out. Adding more visible exams restores list presentation without rewriting the saved display mode.
- Timezone text is never emitted by the shared date formatter or widget accessibility labels, even when an old configuration has showZone=true. The legacy field is retained for document compatibility. Event zone IDs remain intact for correct target-time calculations and advanced exam-time editing.
- Multi-exam rows distinguish the exam name, countdown value, smaller unit caption and supporting details. Labels are separate from names. Rows use available vertical space, and compact/large-font arrangements adapt without overflowing. Optional content is reduced before text is made smaller. Accessibility descriptions retain the exam name and full countdown.
- Day/hour/week/month abbreviations use smaller unit spans beside the numbers. Single-widget date text scales with the title size. Lists reserve header space only for a chosen widget name or edit shortcut; the redundant exam-count heading is removed.
- One typography policy drives pagination and row fitting, including small system-font normalization. Navigation uses bounded labels and fixed-size arrow glyphs.

## Real previews and launcher sizes

Design thumbnails are scaled snapshots of production RemoteViews using the actual draft, exam count, format and preview dimensions. The selected style retains its current text adjustments; another style previews exactly the candidate that selecting it will apply. Re-tapping the selected style does not reset those adjustments.

Existing-widget editors open at the actual size resolved from launcher options. Actual/Wide/Square/Tall choices share dimensions between the live preview and design cards. The initial page anchor is preserved, and preview navigation remains local. Glass backdrops are separately rendered light/dark samples, never part of widget configuration.

Android 12 responsive RemoteViews use the supplied OPTION_APPWIDGET_SIZES pairs. Older launchers fall back to portrait minimum-width/maximum-height or landscape maximum-width/minimum-height, not an impossible pair of both minima. Navigation uses the same resolved dimensions. Large logical dimensions retain their aspect ratio while surface bitmaps remain bounded to 1200 pixels per side; thumbnail images are bounded to 512 pixels per side.

## Simpler customization

The third editor tab is **Text**. It provides Compact/Balanced/Large sizing, Sans/Serif/Mono choices, contextual alignment and countdown format. Exact sizes and spacing remain under Fine-tune text, rather than being duplicated in Design.

Visible details are selected in one sheet. Essentials and Date + time shortcuts provide clean starting points without deleting stored names, messages or venues. Friendly/full/numeric date formats and clock formats remain available. There is no timezone-display option. Preparation-window and post-exam behavior remain available in collapsed sections. The current saved palette is represented among the simple color choices.

## Verification

57 focused rendering/editor/integration executions passed across five suites. After the final contextual text-control refinement, all 11 editor checks and debug assembly passed again. No failures, errors or skips. Coverage includes:

- The supplied one-exam LIST scenario across all eight active designs, with no rendered timezone and preserved scheduling zone.
- Preview bitmap equality against a direct production render for identical data and dimensions.
- Real size-pair selection in both orientations, pagination at portrait size, and normalized small-font pagination.
- Compact lists at 1.0/1.6/2.0 font scales, visible-text bounds, and large-widget bitmap aspect ratio/memory bounds.
- Text/detail presets, restoration, unchanged exam content, selected-style preservation and the single-visible-exam editor controls.
- Historical widget configuration readers and productivity archive round trips.

Chat-boundary and whitespace guards passed. Source hashes, test reports and actual production UI renders are in `/workspace/artifacts/exam-widget-text-layout/`. Physical launcher/OEM verification remains outstanding. Release build verification is recorded in `docs/releases/2.0.42.md`. No new dependencies, fonts, packaged images, permissions, scheduling work or backup-format changes were introduced.
