# Feature implementation guide

These are developer references, grouped by feature. Dated entries and merged
histories preserve original checkpoints; consult later records where behavior
changed. For build and CI instructions use the [build guide](../development/BUILD_AND_TEST.md).

| Feature | Start here | Supporting records |
| --- | --- | --- |
| Backup and restore | [Simplified setup/restore flow](backups/BACKUP_FLOW_REDESIGN_2026-11-07.md) | [Automatic backup design](backups/AUTOMATIC_FULL_BACKUPS.md), [manual authorization](backups/MANUAL_BACKUP_SESSIONS.md), [progress and media reads](backups/MEDIA_BACKUP_PROGRESS_2026-11-13.md), [audit history](backups/AUTOMATIC_BACKUP_AUDIT_HISTORY.md) |
| Private notes/archive | [Archive and PIN flow](notes/ARCHIVE_FLOW_2026-11-13.md) | Viewing/unarchiving authorization and optional PIN-free archiving are documented together. |
| Widgets | [Editor simplification](widgets/WIDGET_EDITOR_SIMPLIFICATION.md) | [Exam widget history](widgets/EXAM_WIDGET_HISTORY.md), [battery health](widgets/BATTERY_HEALTH_WIDGET.md), [note-font stability](widgets/NOTE_WIDGET_FONT_STABILITY.md) |
| Settings | [Organization, search and boundaries](settings/SETTINGS_BEHAVIOR.md) | [Original information-architecture audit](settings/INFORMATION_ARCHITECTURE_AUDIT.md), [Vizag footer](settings/VIZAG_FOOTER.md) |
| Reliability and permissions | [Runtime reliability fixes](system/RUNTIME_RELIABILITY_FIXES.md) | [Permission hardening](system/PERMISSION_HARDENING.md), [pre-release hardening](system/PRE_RELEASE_HARDENING.md), [reminder queue](system/REMINDER_OVERLAY_QUEUE.md), [OnePlus startup investigation](system/ONEPLUS_ANDROID16_STARTUP_INVESTIGATION.md) |
| Habits | [Pause behavior](habits/PAUSE.md) | — |
| Voice | [Speech connection recovery](voice/SPEECH_CONNECTION_RECOVERY.md) | [Transcription styles](voice/TRANSCRIPTION_STYLES.md); these records do not imply the separately deferred speech-privacy work is complete. |
| Appearance | [Smoke-paw mystery boxes](appearance/SMOKE_PAW_MYSTERY_BOXES.md) | Runtime artwork is retained. |
| Offline assistant | [Assistant guide](../assistant/README.md) | Separate maintained status, implementation, plans and historical records. |

## Evidence and older reviews

Security findings are indexed in the [security guide](../security/README.md).
Cross-feature reviews remain under `docs/audits/`, including the
[earlier selected-feature implementation review](../audits/FEATURE_IMPLEMENTATION_REVIEW.md).

Generated Vizag/reminder JSON records remain under `docs/implementation/`, and the
settings inventory remains at `docs/design/SETTINGS_AUDIT_INVENTORY.json`. Those
locations are preserved for tooling and provenance. Historical `/workspace/artifacts/`
references describe the original review session; they are not repository files or
requirements for a new build.
