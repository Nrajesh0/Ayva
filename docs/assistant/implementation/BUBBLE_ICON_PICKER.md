# Chat bubble icon picker

Implemented 3 November 2026. Chat's three-dot menu now includes **Bubble icon**.
The compact picker uses all 12 existing `AppIconManager.iconOptions` assets;
there are no new images, animation dependencies or launcher aliases.

`BubbleIconManager` saves a validated ID in `bubble_prefs/bubble_icon_id`.
`BubbleIconPicker` displays the shared artwork and current selection. Choosing
an icon closes the picker and broadcasts an app-scoped icon-change event.
`BubbleService` updates its existing ImageView without starting/resuming the
service, modifying snooze state or changing overlay visibility. A later service
creation reads the saved choice. The docking preview reads the same preference.
This preference is separate from the launcher icon setting. Unknown IDs fall
back to the default icon. No database migration is required.

Validation: `:app:compileDebugKotlin` passed; after the final changes,
`:app:compileDebugUnitTestKotlin` passed, including production compilation and
the updated existing header-test callers. Device/launcher presentation has not
been exercised. No full test suite or release build was run for this small UI
change, in line with the project's focused-validation preference.

The recovered app atlas is a separate documentation deliverable. Environment
shell connectivity recovered during this task; environment publication success
was not established. No environment caches or existing mockups were removed.
