# Exam widget implementation history

Original expansion, editor/design revamp and subsequent layout improvements,
in delivery order. Earlier behavior is superseded where a later section says
so; test counts and release references describe those checkpoints.

## Contents

- [Exam widget expansion](#expansion)
- [Exam widget revamp — 2.0.41](#editor-and-design-revamp)
- [Exam widget text, layout and preview refinement](#text-layout-and-previews)

<a id="expansion"></a>

<a id="expansion-exam-widget-expansion"></a>

## Exam widget expansion

The current editor and selectable design catalogue are described in [Exam widget revamp — 2.0.41](EXAM_WIDGET_HISTORY.md#editor-and-design-revamp). The following records the original expansion.

Scope: manage multiple independent launcher widgets and display multiple exams within one widget, retaining the shared minimal widget-editor style.

<a id="expansion-delivered-features"></a>

### Delivered features

- Up to eight exams per widget, each with its own name, message, venue, label, date, time, time zone and preparation-window start.
- Primary-exam, next-up, multi-exam list and manual page modes. Lists adapt to widget height and font scale; overflow uses previous/next page controls.
- Custom or chronological order, promotion to primary, confirmed draft removal and finished-exam filtering. Next-up falls back to the most recently completed exam when no future exams remain.
- Six designs (Classic, Big countdown, Exam ticket, Minimal, Progress timeline, Study dashboard), including distinct list-row variants.
- Ten palettes including app-following and custom hex colors; background opacity, transparency, border, radius, gradient/orbit/stripe detail.
- Sans/serif/monospace, countdown/title/list text sizes, alignment where applicable, spacing, maximum list rows, date styles and 12/24-hour/device clock formats.
- Days, weeks+days, days+hours, months+days, and weekdays. Weekdays exclude today, include the target date and do not exclude public holidays.
- Detail toggles, elapsed/remaining preparation timeline and percentage, urgency thresholds with light/dark colors, after-exam message or elapsed count, edit shortcut and tap behavior.
- Preview uses the production RemoteViews renderer, with adjustable height and local paging that never sends a launcher action.
- Manager separates home-screen instances from saved layouts. Edit, duplicate, create, reuse and delete unbound layouts are supported. Bound layouts cannot be deleted; actual home-widget removal remains a launcher action.

<a id="expansion-compatibility-and-safety"></a>

### Compatibility and safety

The existing `exam_countdowns` and device-local `exam_widget_bindings` stores remain. New JSON fields are optional when reading older records. Older date visibility and edit-shortcut defaults are retained. Backup validation and restore use the same model; expanded exam/style data remain portable while launcher IDs stay device-local.

Apply saves only the selected instance, with a copy for a newly pinned widget. Cancel does not save the draft. Layout saving is a separate explicit action. Styling preserves the current page; changing exams, order, display mode or filtering resets it. Draft saving includes incomplete text fields and all exam/style settings across rotation.

Updates remain inexact and non-wakeup, including local-midnight, target-time and hour-boundary refreshes with a 30-minute upper interval. The widget does not create notification alarms or a continuous timer. Small widgets reduce details and cap text to fit. No large illustration/font assets or new libraries were added.

Core blocking, notification FIFO, private notes and Bubble Settings are unchanged. Version/release publication is outside this implementation task.

<a id="expansion-validation"></a>

### Validation

Full regression: **1,463 tests across 106 suites passed**, with zero failures, errors or skips. Tests exercise old/new JSON, invalid/ambiguous dates, bounded exam counts, instance isolation, restore/rebind, saved-layout deletion guards, selection/filtering/page wrap, countdown formats, native rendering of designs/palettes, large fonts, draft restoration and staged cancellation/removal. Backup round-trip coverage now includes multiple exams and advanced styling.

Screenshots are renders of production UI under Robolectric; physical-launcher pinning/OEM behavior is not established by these tests.

The local screenshot gallery is `/workspace/artifacts/exam-widgets/index.html`. It contains production single-exam renders for all six designs and selected multi-exam renders, the editor, and compact layouts. No device was attached for a launcher/OEM check.

Debug and release assembly passed, as did the chat data-boundary and full dictionary guards. The universal release is 44,194,759 bytes (42.15 MiB), with all four supported native ABIs and no retired dialogue files. All 289 packaged assets match the previous debug APK exactly, including dictionary and artwork. Release output in this environment is unsigned; the test install artifact uses the debug build.

Final editor/preservation tests and debug assembly passed after the last UI refinement. A further native-rendering pass checks every captured text view stays within vertical widget bounds. One multi-exam timeline screenshot is omitted because Robolectric draws incomplete text despite correct measured bounds; the timeline design itself applies and passes the rendering/bounds tests. The signed debug APK is `/workspace/artifacts/Ayva-exam-widgets-debug.apk`; its signature was verified with `apksigner`.

---

<a id="editor-and-design-revamp"></a>

<a id="editor-and-design-revamp-exam-widget-revamp--2041"></a>

## Exam widget revamp — 2.0.41

Text/layout and renderer-preview improvements for 2.0.42 are described in [Exam widget text, layout and preview refinement](EXAM_WIDGET_HISTORY.md#text-layout-and-previews). The following records the 2.0.41 release.

The exam editor groups setup into **Exams**, **Design** and **Details**, with a visible production-renderer preview and persistent Apply/Add widget action. Other widget editors retain their existing interface.

<a id="editor-and-design-revamp-final-design-catalogue"></a>

### Final design catalogue

The picker exposes eight distinct designs in two groups. There is no duplicate layout chooser under advanced appearance.

| Group | Design | Stored ID | Composition |
| --- | --- | --- | --- |
| Bold | Poster | POSTER | Condensed oversized type, vivid surface and cut corner |
| Bold | Orbit | ORBITAL | Preparation-window ring beside the exam |
| Bold | Aurora | AURORA | Soft mesh light and centered countdown |
| Bold | Bento | BENTO | Separate number and details tiles |
| Bold | Signal | SIGNAL | Monospace type and a technical grid |
| Glass | Frost | FROST | Smoked translucent pane and reflective rim |
| Glass | Prism | PRISM | Tinted facets and centered countdown |
| Glass | Layers | LAYERS | Offset translucent panes and asymmetric details |

New widgets use the Aurora preset through the new-widget factory. Older configurations are loaded as stored. Selecting a preset intentionally changes its visual defaults, including a starting palette for the expressive designs, while retaining exams and independent widget bindings. Fine-tuning remains available afterward.

Seven older/overlapping layouts are removed from selection: CLASSIC, HERO, TICKET, MINIMAL, TIMELINE, DASHBOARD and COLORBLOCK. Their IDs, readers and renderers remain supported for installed widgets, saved layouts and portable backups. An existing layout is kept until its owner selects a new design; editing unrelated settings or simply applying the editor preserves it. Stored-layout compatibility is deliberately separate from the eight selectable designs.

<a id="editor-and-design-revamp-organization"></a>

### Organization

- Exams: compact schedule rows open individual sheets. Name, date and time come first; venue, label, message, preparation dates and time zone are under More exam details. Multi-exam display/order controls appear when relevant. Reusable layout actions are collapsed.
- Design: the eight visual cards, contextual palettes and transparency/opacity controls. Custom colors, typography, spacing, corners and decorations are under Fine-tune appearance. Countdown sizing supports 24–96 sp with adaptive fitting.
- Details: countdown format, preparation timeline and main visibility controls. Additional fields, date/time formatting, end-of-exam behavior, tap actions and deadline-color thresholds are grouped in expandable controls.
- Manager: installed widgets and reusable unbound layouts are separate. Card taps open a widget/layout; secondary actions use an overflow menu. Bound configurations do not appear again in Saved layouts. Deletion checks bindings before removing a layout.

<a id="editor-and-design-revamp-rendering-and-glass"></a>

### Rendering and glass

Countdown value and unit use distinct typography, with full accessible countdown descriptions. Multi-exam rows adapt to available height and font scale. Capacity reserves actual toolbar and navigation space; normal-size wide lists can show two exams and page to the rest. Custom finished messages remain verbatim.

Orbit uses the currently displayed exam's elapsed/remaining preparation-window time, not study completion. Transparent Orbit retains its ring; a list does not display a misleading aggregate ring. Compact widths/heights and large fonts use a readable stacked arrangement for two-column designs. Layers uses Frost in those conditions. Bento keeps extra information on its details tile.

Glass uses genuine alpha transparency, tint and procedural reflective edges. RemoteViews cannot reliably blur the wallpaper behind a widget. No wallpaper capture, permissions or live-blur claim is introduced. Separate preview-only light/dark backdrops sit behind the widget; that choice is saved only as editor UI state and never enters the widget configuration or launcher bitmap.

Default glass text is checked over light backdrops. Aurora detail text remains bright across its glow. Poster and Bento choose contrasting ink on vivid surfaces; custom foreground/background/accent values remain available. All shapes and preview backdrops are procedural and bitmap-size bounded. No image/font assets, dependencies or background refresh jobs are added.

<a id="editor-and-design-revamp-preservation-and-verification"></a>

### Preservation and verification

The storage keys, independent instances, launcher binding format, full backups and scheduling model are unchanged. Drafts survive rotation, incomplete input is validated at Apply, cancellation does not write configuration, and exam removal requires confirmation. Updated-app designs/palettes require an updated app to edit or restore.

Tests cover editor restoration/cancellation, legacy layout preservation without exposing retired choices, new-widget defaults, current and historical JSON, native rendering/bounds for all 15 supported stored IDs, compact/large-font behavior, multi-exam paging, contrast/transparency, current-exam ring semantics, and productivity archive round-trip preservation. Release validation is recorded in `docs/releases/CHANGELOG.md#version-2-0-41`.

Screenshot galleries are production UI renders under Robolectric. Physical launcher pinning, OEM resizing and translucency behavior remain device checks. Local release builds are unsigned; production signing and publication run in GitHub Actions.

---

<a id="text-layout-and-previews"></a>

<a id="text-layout-and-previews-exam-widget-text-layout-and-preview-refinement"></a>

## Exam widget text, layout and preview refinement

Release: 2.0.42 (142).

<a id="text-layout-and-previews-problems-addressed"></a>

### Problems addressed

A one-exam widget using LIST mode rendered a small row inside a large surface, including the redundant “1 EXAMS” heading and dense date/time/timezone text. The editor's illustrative design cards did not describe the configured widget's real arrangement. Pairing minimum width with minimum height could also select dimensions that never occur on the launcher.

<a id="text-layout-and-previews-rendering"></a>

### Rendering

- A list with only one visible exam uses the full single-exam design, including when finished exams are filtered out. Adding more visible exams restores list presentation without rewriting the saved display mode.
- Timezone text is never emitted by the shared date formatter or widget accessibility labels, even when an old configuration has showZone=true. The legacy field is retained for document compatibility. Event zone IDs remain intact for correct target-time calculations and advanced exam-time editing.
- Multi-exam rows distinguish the exam name, countdown value, smaller unit caption and supporting details. Labels are separate from names. Rows use available vertical space, and compact/large-font arrangements adapt without overflowing. Optional content is reduced before text is made smaller. Accessibility descriptions retain the exam name and full countdown.
- Day/hour/week/month abbreviations use smaller unit spans beside the numbers. Single-widget date text scales with the title size. Lists reserve header space only for a chosen widget name or edit shortcut; the redundant exam-count heading is removed.
- One typography policy drives pagination and row fitting, including small system-font normalization. Navigation uses bounded labels and fixed-size arrow glyphs.

<a id="text-layout-and-previews-real-previews-and-launcher-sizes"></a>

### Real previews and launcher sizes

Design thumbnails are scaled snapshots of production RemoteViews using the actual draft, exam count, format and preview dimensions. The selected style retains its current text adjustments; another style previews exactly the candidate that selecting it will apply. Re-tapping the selected style does not reset those adjustments.

Existing-widget editors open at the actual size resolved from launcher options. Actual/Wide/Square/Tall choices share dimensions between the live preview and design cards. The initial page anchor is preserved, and preview navigation remains local. Glass backdrops are separately rendered light/dark samples, never part of widget configuration.

Android 12 responsive RemoteViews use the supplied OPTION_APPWIDGET_SIZES pairs. Older launchers fall back to portrait minimum-width/maximum-height or landscape maximum-width/minimum-height, not an impossible pair of both minima. Navigation uses the same resolved dimensions. Large logical dimensions retain their aspect ratio while surface bitmaps remain bounded to 1200 pixels per side; thumbnail images are bounded to 512 pixels per side.

<a id="text-layout-and-previews-simpler-customization"></a>

### Simpler customization

The third editor tab is **Text**. It provides Compact/Balanced/Large sizing, Sans/Serif/Mono choices, contextual alignment and countdown format. Exact sizes and spacing remain under Fine-tune text, rather than being duplicated in Design.

Visible details are selected in one sheet. Essentials and Date + time shortcuts provide clean starting points without deleting stored names, messages or venues. Friendly/full/numeric date formats and clock formats remain available. There is no timezone-display option. Preparation-window and post-exam behavior remain available in collapsed sections. The current saved palette is represented among the simple color choices.

<a id="text-layout-and-previews-verification"></a>

### Verification

57 focused rendering/editor/integration executions passed across five suites. After the final contextual text-control refinement, all 11 editor checks and debug assembly passed again. No failures, errors or skips. Coverage includes:

- The supplied one-exam LIST scenario across all eight active designs, with no rendered timezone and preserved scheduling zone.
- Preview bitmap equality against a direct production render for identical data and dimensions.
- Real size-pair selection in both orientations, pagination at portrait size, and normalized small-font pagination.
- Compact lists at 1.0/1.6/2.0 font scales, visible-text bounds, and large-widget bitmap aspect ratio/memory bounds.
- Text/detail presets, restoration, unchanged exam content, selected-style preservation and the single-visible-exam editor controls.
- Historical widget configuration readers and productivity archive round trips.

Chat-boundary and whitespace guards passed. Source hashes, test reports and actual production UI renders are in `/workspace/artifacts/exam-widget-text-layout/`. Physical launcher/OEM verification remains outstanding. Release build verification is recorded in `docs/releases/CHANGELOG.md#version-2-0-42`. No new dependencies, fonts, packaged images, permissions, scheduling work or backup-format changes were introduced.
