# RuN Android module

This module contains RuN's Kotlin application, Android resources and bundled assets,
unit tests, instrumented tests and Room schema snapshots.

Use the [repository README](../README.md) for current features, privacy boundaries,
installation and build commands. The [build and test guide](../docs/development/BUILD_AND_TEST.md)
covers the supported toolchain, verification and CI scheduling. Feature and
security records are indexed in the [documentation guide](../docs/README.md).

Run Gradle commands from the repository root using the checked-in wrapper.

## Source ownership

| Area | Location |
| --- | --- |
| Account screen state and navigation | `ui/screens/AccountScreen.kt` |
| Account presentation sections and dialogs | `ui/screens/account/` |
| Note editor state, save callbacks and media authorization | `ui/screens/notes/KeepNoteEditor.kt` |
| Independent editor widgets and image viewer | `ui/screens/notes/KeepEditorComponents.kt`, `ZoomableImagePager.kt` |
| Shared chat models and presentation/storage mappings | `data/chat/ChatMessage.kt`, `ChatMessageMappings.kt` |
| Active drill models and durable completion | `data/drill/DrillSession.kt`, `DrillCompletion.kt` |
| Habit reminder calculations and vocabulary card encoding | `data/HabitIntervalRules.kt`, `VocabBriefPayload.kt` |
| Note content delimiters and recent-note cache | `data/note/NoteContentFormat.kt`, `LatestNotesCache.kt` |
| Backup authorization, maintenance admission and commit/rollback ordering | `util/backup/BackupRestoreManager.kt`, `RestoreJournal.kt` |
| Backup record encoding | `util/backup/BackupRecordCodec.kt` |

UI, persisted chat and Room entity models remain separate representations. Keep
their mappings explicit and preserve round-trip coverage when adding fields.
The recent-note cache can contain decrypted records; vault locking and restore
must continue to invalidate it. Source file moves do not require database or
backup-format migrations.
