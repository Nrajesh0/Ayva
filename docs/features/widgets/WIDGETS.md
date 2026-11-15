# Home-screen widgets

Current widgets cover Notes, Tasks and Exam countdowns. The experimental battery
health widget was removed in 2.0.37 because reliable capacity measurements were
unavailable on the target phone; do not present its old design as a live feature.

## Configuration and rendering

Choose an installed instance in Settings; widget ID 0 explicitly represents default
style. Keep per-instance/default preference isolation, typography, rendering and
pin callbacks. Editor changes use the existing draft/apply policy, rather than
implicitly writing the first installed instance. Task controls preserve title,
content, count/progress/priority toggles, alpha, radius and padding. Notes preserve
title/content sizes, alpha and layout. Exam controls preserve target-date validation,
design/palette/type/layout/detail/progress and ended-state behavior.

Notes RemoteViews use `NoteWidgetTypography`: convert configured SP with current
display metrics, send explicit PX and disable inherited auto-size in XML. Header,
rows, loading/empty/error states use the same policy. Adapter identity includes
renderer version, widget ID, size, density and font scale; publish only the latest
serialized render snapshot, and keep factory note/content/config immutable together.
Do not add a RemoteViews runtime setter that Android does not allow.

Exam designs can intentionally share layout aliases while retaining separate design
names, palettes and decoration. Validate event dates/countdowns using calendar and
timezone rules; a missing reading/date is not a fabricated value. Source artwork,
font attribution and existing sizing variants remain app resources.

## Privacy, actions and restore

Archived/trashed private notes must not become widget previews. A launcher owns the
RemoteViews window: protecting the configuration Activity cannot apply FLAG_SECURE
to the launcher. Use privacy-conscious content selection, not a screenshot guarantee.
Keep explicit user-tap PendingIntent routing, keyguard behavior and transactional
checklist/task edits against current rows; older cached rows cannot overwrite new data.

Portable backup includes supported widget appearance and note-binding preferences,
not launcher placement, host permissions or OS instance identity. Copy-import remaps
note references. Foreign/older host IDs require disconnection/reconnection warnings
rather than attaching their bindings to unrelated local instances. See
[backup and restore](../backups/BACKUP_AND_RESTORE.md).
