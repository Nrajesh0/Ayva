# B0/B1: reproducible verification and Notes isolation

Implemented and locally validated 2026-10-18: 849 tests passed, debug and optimized unsigned release assembly passed. See STATUS.md for exact commands, first-run failures, evidence and pending CI/device checks. This record describes changes made after the roadmap; earlier response-control/conversation work remains present.

## B0 changes

- Preserved all pre-existing modified/untracked files (223 files, including design assets) in `/workspace/scratch/ayva-before-assistant-b0/working-tree.tar.gz`, with per-file hashes in `manifest.json` and the original tracked diff in `tracked.patch`. Base commit: `01c692116526e0f78bea043403b6539914f93146`. This is a local recovery artifact, not a GitHub checkpoint or permission to overwrite newer edits.
- Pinned the wrapper distribution to verified Gradle 8.9, matching the AGP 8.7.3 build used successfully by this project. Java/Kotlin app bytecode still targets 17; Gradle/test runtime is JDK 21.
- Added `.github/workflows/validate.yml`: tests plus debug and optimized release assembly for PRs/main/master pushes, read-only repository permissions, no publication and no signing-secret requirement. It retains reports on failures and runs the chat dependency guard.
- Release workflow now uses the same wrapper/JDK and requires unit-test success before publishing. Existing release triggers remain unchanged.
- Debug builds reuse an existing local key or fall back to AGP's standard generated debug signing configuration on fresh checkouts.
- Added portable commands and environment limitations in BUILD_AND_TEST.md. The optional cloud C2 workaround is now a checked-in test-only init script; normal CI does not use it.

## B1 changes

The chat executor previously called the all-data recovery helper, which read Notes during task Trash. It now writes encrypted selected-task snapshots through `TaskRecoveryStore`; this store depends on no Notes DAO, vault state, media attachment helper or all-data backup manager.

A reviewed Trash group executes as one task transaction. It builds exact before/after rows and durably writes/verifies their recovery snapshot before changing task rows. A storage/encryption/verification failure prevents the task write. The snapshot has an explicit `selected_tasks_v1` scope, count/version, stable identities and no Notes fields. A dedicated directory and bounded retention isolate it from all-data backup rotation.

Task snapshots are discoverable in native recovery. Their recovery is dispatched before any all-data safety snapshot or Notes operation. Recovery validates scope/encryption/path and every current task against its expected post-operation row; a missing or externally edited task rejects the entire group. A task-only rollback snapshot precedes recovery's database writes. Repeated recovery refuses to overwrite the restored row. Reminders/widgets refresh afterwards; an error there reports that tasks were restored but refresh failed. Chat offers no snapshot-file restore capability.

Shared encrypted file IO was extracted to a codec with no database access. Native daily/pre-operation all-data backup behavior still uses that codec and retains its own Notes access outside chat. Existing all-data formats are not reinterpreted as empty Notes collections.

Chat now uses a storage-independent `OnDeviceTranscriber`. The native Notes audio manager delegates transcription to that same component and retains its saved-media playback/copy features separately. Existing native speech tests therefore continue exercising the shared recognizer behavior. Chat no longer holds an audio manager that can open saved attachments. The existing voice dialog is shared presentation only: supplied captions, callbacks and a link to Android Voice settings.

Assistant session persistence no longer depends on the saved-media encryption API. It uses the storage-only snapshot codec and authenticated encryption. Old media-format sessions intentionally expire on upgrade, clearing temporary context/confirmations without touching tasks, preferences or saved Notes/media. Current-format sessions retain the existing 15-minute lifetime and size bound.

A legacy status branch no longer constructs an empty `NoteEntity` list. Its Notes navigation label omits the fabricated zero count; brief layout and cards remain unchanged.

## Boundary audit and validation coverage

Inspected conversational core/domain helpers, legacy Talk engine, router/actions, chat UI callbacks, task reminders, backup status receipts and speech paths. Backup status reads only existing success timestamps; generic Notes help/navigation does not fetch private data. Task state, native learning launches, settings and app controls continue through existing owners. No Note database/content capability is introduced into those paths. Wider capability contracts remain B2 work.

`tools/check_chat_boundary.py` rejects direct Notes/vault/all-data/media capabilities in the inspected core, routing, chat UI, speech and task-recovery sources. It is an architectural regression guard, not a complete transitive static analyzer or an OS security sandbox.

`AssistantNotesIsolationTest` seeds a synthetic private Notes row and instruments SQL touching its table. It exercises chat task Trash/undo, privacy/help/status and cross-topic replies, storage failure, native task-only recovery through a Notes DAO that rejects every call, externally edited groups, corrupt/foreign snapshots, repeated recovery, on-device speech and expiring old encrypted sessions. A separate explicit native-backup smoke test confirms independent all-data backup still includes Notes. Existing bulk-deletion security tests now assert the task-only recovery location.

The first focused run caught per-task bulk snapshots and delayed transaction-bookkeeping observations in the query instrument. The implementation now groups Trash execution; the query monitor observes actual private-table statements rather than unrelated transaction completion. These were corrected before final validation.

Physical Android checks, fresh hosted CI execution and future adversarial language/context work remain separate from local unit/build results. No model, network inference or dictionary data was added in these batches. No version bump or publication is part of this work.

## Final local verification

All 849 unit tests passed in a test-only invocation (zero failures/errors/skips), followed by successful debug and optimized release assembly. A combined tests/R8 run earlier exposed a timing-sensitive native Notes deletion test; its 13-test class and the complete suite passed separately afterwards. CI and documentation now use separate test/build invocations. The assertion was not disabled or changed. Dependency boundary checks, workflow YAML, documentation links and whitespace checks passed. Hosted Actions and physical Android checks remain unrun. See STATUS.md for logs and APK hashes.
