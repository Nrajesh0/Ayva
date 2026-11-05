# Exam widget expansion

The current editor and selectable design catalogue are described in [Exam widget revamp — 2.0.41](EXAM_WIDGET_REVAMP.md). The following records the original expansion.

Scope: manage multiple independent launcher widgets and display multiple exams within one widget, retaining the shared minimal widget-editor style.

## Delivered features

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

## Compatibility and safety

The existing `exam_countdowns` and device-local `exam_widget_bindings` stores remain. New JSON fields are optional when reading older records. Older date visibility and edit-shortcut defaults are retained. Backup validation and restore use the same model; expanded exam/style data remain portable while launcher IDs stay device-local.

Apply saves only the selected instance, with a copy for a newly pinned widget. Cancel does not save the draft. Layout saving is a separate explicit action. Styling preserves the current page; changing exams, order, display mode or filtering resets it. Draft saving includes incomplete text fields and all exam/style settings across rotation.

Updates remain inexact and non-wakeup, including local-midnight, target-time and hour-boundary refreshes with a 30-minute upper interval. The widget does not create notification alarms or a continuous timer. Small widgets reduce details and cap text to fit. No large illustration/font assets or new libraries were added.

Core blocking, notification FIFO, private notes and Bubble Settings are unchanged. Version/release publication is outside this implementation task.

## Validation

Full regression: **1,463 tests across 106 suites passed**, with zero failures, errors or skips. Tests exercise old/new JSON, invalid/ambiguous dates, bounded exam counts, instance isolation, restore/rebind, saved-layout deletion guards, selection/filtering/page wrap, countdown formats, native rendering of designs/palettes, large fonts, draft restoration and staged cancellation/removal. Backup round-trip coverage now includes multiple exams and advanced styling.

Screenshots are renders of production UI under Robolectric; physical-launcher pinning/OEM behavior is not established by these tests.

The local screenshot gallery is `/workspace/artifacts/exam-widgets/index.html`. It contains production single-exam renders for all six designs and selected multi-exam renders, the editor, and compact layouts. No device was attached for a launcher/OEM check.

Debug and release assembly passed, as did the chat data-boundary and full dictionary guards. The universal release is 44,194,759 bytes (42.15 MiB), with all four supported native ABIs and no retired dialogue files. All 289 packaged assets match the previous debug APK exactly, including dictionary and artwork. Release output in this environment is unsigned; the test install artifact uses the debug build.

Final editor/preservation tests and debug assembly passed after the last UI refinement. A further native-rendering pass checks every captured text view stays within vertical widget bounds. One multi-exam timeline screenshot is omitted because Robolectric draws incomplete text despite correct measured bounds; the timeline design itself applies and passes the rendering/bounds tests. The signed debug APK is `/workspace/artifacts/Ayva-exam-widgets-debug.apk`; its signature was verified with `apksigner`.
