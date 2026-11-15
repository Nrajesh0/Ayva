# Feature contracts

Use the focused guides below for implementation rules and the module README for
source ownership. Historical release claims belong in the compact history.

| Feature | Current contract |
| --- | --- |
| Backup/restore | [Portable/manual/automatic flows, identity, interruption and retention](backups/BACKUP_AND_RESTORE.md) |
| Notes/private archive | [PIN and write-only archiving, media, revisions and picker return](notes/ARCHIVE_AND_MEDIA.md) |
| Assistant/planning | [Bounded capabilities, reviewed actions, receipts and native tasks](../assistant/README.md) |
| Settings | [Canonical ownership, search, ranges and privacy controls](settings/SETTINGS_BEHAVIOR.md) |
| Permissions/background work | [Readiness, overlays, reminder FIFO and speech lifecycle](system/RELIABILITY_AND_PERMISSIONS.md) |
| Widgets | [Instance configuration, typography and restore limits](widgets/WIDGETS.md) |
| Artwork | [Approved Vizag renderer and regeneration](settings/VIZAG_FOOTER.md) |
| Vocabulary | [Native learning and retired dictionary boundaries](../assistant/DICTIONARY_DATA.md) |

## Habits

Pause keeps the habit visible, disables logging/reminders and excludes it from
the active completion denominator without changing its reminder toggle, schedule,
target, logs or rewards. Notification actions reject paused habits and remove
queued nudges. Resume recalculates reminders. Paused local-calendar intervals
bridge streak gaps without fabricated completions or increases to streaks;
actual completions still count. Backup and assistant snapshots preserve pause
state/history. Talk actions retain confirmation, snapshot guards and undo.

The editor and chat creation form share interval helpers with explicit existing
window policies. A short chat window must not invert slider bounds and crash;
sharing code does not authorize silently changing saved schedule semantics.

## Rewards and presentation

Drill finish captures counters/question data before suspension and uses one stable
session ID and a durable journal for rewards plus result persistence. Duplicate
Finish cannot award twice. Failed saves keep the drill retryable; claims update
an existing row before UI success. Vocabulary feedback waits for maintenance
admission and flushes pending writes before Finish. Restore commits invalidate
old active drills; unrelated internal maintenance does not invalidate note editors.

Smoke Paw mystery-box presentation uses the single resolved award for its original
source chest. Skip ends animation and Collect delivers that award; neither rerolls
or credits again. Reward probabilities/ranges and overflow rules belong to the
economy owner. Reduced motion and renderer failure reveal an accessible static
result rather than blocking collection. The private offscreen vector rasterizer
has no network/file/content/storage/navigation access or JavaScript bridge; actual
rewards and controls remain native UI.
