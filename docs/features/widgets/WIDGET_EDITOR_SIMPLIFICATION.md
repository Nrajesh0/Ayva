# Widget editor simplification and battery-widget removal

Included in version **2.0.37 / 137**. The verification below records the local checks before the release version bump.

## Removal

Remove the battery-health provider, manifest registration, launcher metadata, layout/drawable/colors/strings, settings/search entry, unused battery-reading helper, and checks specific to that removed feature. The general diagnostics battery-charge reading is retained. Existing task, note and exam-countdown widget configurations are retained.

After installation of an update, Android's widget host will detect the removed provider. A launcher that leaves an old placeholder may require the user to remove that home-screen item. No notes, tasks or countdowns are deleted.

## Editor organization

- Shared compact rows replace the large theme cards, color carousels and repeated containers. Choices open a bounded list with radio selection and color samples; notes keep their title and content/checklist snippet so similar names remain distinguishable.
- The preview opens on tap and keeps the existing note/task preview artwork and configured text size. It no longer takes up the first screen by default.
- Notes and countdowns use Content, Style and Layout tabs. The task editor's five controls fit on one compact page and do not need tabs.
- Apply stays in the top bar. Back cancels. Editing does not write preferences until Apply. Saved drafts and selected sections survive Activity state restoration, including partially invalid countdown text.
- The widget-management dialog shows a bounded instance list with each widget's content/style summary, alongside default-style and add actions.

## Retained customization

| Widget | Options |
|---|---|
| Tasks | Every theme/accent, opacity, corner radius and font size |
| Notes | Content mode, selected note, sorting, themes/accents, note-color matching, opacity/radius, font size/padding, title, quick-edit bar, voice/new-note buttons, navigation controls and adaptive layout |
| Exam countdown | Name/message, exam date/time/timezone, progress start, format, saved-countdown reuse, palettes/custom colors, opacity/radius, font size/alignment, date/progress visibility, elapsed-days behavior and ended message |

Preference keys, per-widget/default-style behavior, actual home-screen widget layouts and typography rules, countdown validation, pin callbacks and backup formats are unchanged. Planning, blocking, reminders and scenery are outside this change.

## Verification

**38 focused checks passed**, with no failures, errors or skips. New editor checks include Android 12 and Android 16 state restoration and note selection. Debug and optimized release builds passed; packaging verified one universal Android 12+ APK and the removal of the battery provider while retaining all three existing providers. All 262 conversation/dictionary assets remain unchanged. `git diff --check` passed. No physical-device check or publication was performed.

Final log: `/workspace/artifacts/widget-editors-final-validation.log`; results and screenshots: `/workspace/artifacts/widget-editor-final/`.

Focused checks exercise real editor composables, saved-state restoration, Apply/Cancel boundaries, note selection, every draft field remaining intact, invalid countdown drafts, removal of the battery provider, existing typography and widget-instance isolation, and countdown backup/date behavior. Production-composable screenshots are captured under `app/build/reports/widget-editors/` for local visual review.
