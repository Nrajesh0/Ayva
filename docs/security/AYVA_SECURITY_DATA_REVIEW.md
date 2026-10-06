# Ayva security, data integrity, and production readiness

Reviewed 2026-10-06. Repository: https://github.com/Nrajesh0/Ayva. Pinned commit: `eaeeb8367d4bcff6cc935e08a74b9f88d7b5c049` (main at retrieval). Review policy: maximum practical confidentiality and durability for **all notes**, with standard Android protection for other features.

**Verdict: not production-ready under that policy.** There are credible paths to permanent data loss and failure of notes confidentiality. The app has useful security components, but their composition and failure handling do not provide the guarantees claimed in several comments. I would block a public production release until the high-priority findings below are fixed and tested on real Android devices.

## Scope and evidence limits

Retrieved 274 text source/configuration/test files through the GitHub connector, covering all Kotlin source in the tree, manifest, XML security settings, build configuration, and release workflow. Git clone failed because the shell could not connect to its configured proxy. The local source snapshot is `/workspace/Ayva`; it is not a Git clone and excludes binary assets and the binary Gradle wrapper JAR. Application source was not modified. Findings are independent of the repository's historical `AUDIT_TRACKER.md` assertions.

Manually traced notes storage, vault setup/unlock/lock/recovery, media imports and deletion, snapshots, encrypted backup and restore, import/export helpers, widgets, Android component exposure, habit updates, diagnostic handling, tests, and release gating. Eight source checks and synthetic cryptographic/SQLite checks passed; see [script](ayva_review_checks.py) and [results](ayva_review_checks.json). These confirm source properties and minimal failure models, **not execution of the Android app**. No emulator, real device, instrumentation suite, dependency advisory database, APK analysis, or server deployment was tested. `bash gradlew --version` failed because the source-only snapshot lacks the wrapper JAR; the usual Android SDK paths also do not exist here. No claim is made that an Android build or tests passed.

High severity means release-blocking under the requested policy; it does not mean a remote attacker can exploit every issue. Reading app-private files generally requires a device/app compromise or another access path. Failure-mode and data-loss findings often require no attacker.

## How data is handled

| Data | Current implementation | Assessment |
|---|---|---|
| Ordinary note text, lists, labels | Room/SQLCipher `keep_notes_vault.db`; random database passphrase wrapped by Android Keystore | Good starting point; plaintext snapshots defeat consistent encryption at rest |
| Secret Archive note payloads | Extra AES-GCM layer, key derived from six-digit PIN via Argon2id; encrypted title/content/list/media-path/label payload | Useful separation in normal operation, broken by lock/save race and fallback behavior |
| Photos, drawings, attachments, audio files | Files in `keep_images` / `keep_audio`, encrypted with a separate app-wide Keystore media key | Encryption exists; fixed-key fallback and attachment lifecycle undermine protection. Archive PIN does not separately protect attachment bytes |
| Tasks, habits, schedules, restrictions | SQLCipher FocusDatabase using the same DatabaseKeyProvider as notes | More encryption than standard protection requires; coupled failure domain |
| Vocabulary | Room database from bundled `vocab.db` | Ordinary app-private storage is reasonable for this feature |
| Settings and some feature state | SharedPreferences / JSON | Generally suitable for standard protection; imported preference names need restriction |
| Daily and pre-operation snapshots | Plain JSON in `files/auto_backups`; seven daily snapshots and bounded pre-operation snapshots | Ordinary notes are readable in snapshot files; attachment contents and old vault key generations are not preserved |
| Manual backups | Password-derived encryption with AES-GCM around ZIP; media normally decrypted and re-encrypted for portability | Promising; restore is not atomic across databases, preferences, and files |
| Automatic/cloud sync | `triggerAutoSync()` has an empty body; remaining VaultSyncManager is a serialization/import helper | No working automatic cloud sync demonstrated in this commit |
| Speech transcription | Android `SpeechRecognizer.createSpeechRecognizer` | Recognition service may process speech outside the app/on a server; app's missing INTERNET permission does not prevent another service's network use |

The manifest has no INTERNET permission and disables cleartext traffic and Android auto-backup. This supports a mostly local architecture, but does not prove that speech recognition or user exports stay on-device. No cloud tenancy/RLS assessment is possible: the tree contains no Supabase implementation or server access-policy files for the old tests to exercise.

## Release-blocking findings

### F01 — High: predictable fallback encryption keys and raw vault-key fallback

Evidence: [DatabaseKeyProvider.kt:153](https://github.com/Nrajesh0/Ayva/blob/eaeeb8367d4bcff6cc935e08a74b9f88d7b5c049/app/src/main/java/com/focusbyrj/app/data/note/DatabaseKeyProvider.kt#L153), [ArchiveVaultSecurity.kt:1043](https://github.com/Nrajesh0/Ayva/blob/eaeeb8367d4bcff6cc935e08a74b9f88d7b5c049/app/src/main/java/com/focusbyrj/app/data/note/ArchiveVaultSecurity.kt#L1043), [EncryptedMediaStorage.kt:71](https://github.com/Nrajesh0/Ayva/blob/eaeeb8367d4bcff6cc935e08a74b9f88d7b5c049/app/src/main/java/com/focusbyrj/app/util/crypto/EncryptedMediaStorage.kt#L71), and [ArchiveVaultSecurity.kt:215](https://github.com/Nrajesh0/Ayva/blob/eaeeb8367d4bcff6cc935e08a74b9f88d7b5c049/app/src/main/java/com/focusbyrj/app/data/note/ArchiveVaultSecurity.kt#L215).

On non-SecurityException Keystore failures, the helpers construct AES keys from SHA-256 of fixed strings compiled into the app. Every installation taking a given fallback uses the same wrapping/media key. Archive setup and recovery can additionally persist the PIN-derived **decryption key itself** with an empty IV if wrapping fails. This is not a password verifier: the same bytes are used to decrypt archived note payloads.

Impact: an attacker with the relevant app-private ciphertext/preferences can reconstruct fallback keys without the PIN, unwrap the database or vault key, or decrypt media. Keystore becoming available later can also cause previously fallback-encrypted data to be read under a different key. The synthetic checks independently reconstructed all three constants and decrypted sample ciphertext.

Fix: remove these fallbacks from production. Distinguish missing keys, temporarily inaccessible keys, and permanent invalidation. Fail safely with a recovery option; never replace an existing key or silently change key source. Use explicit dependency injection for test keys. For maximum notes protection, use authentication-bound Keystore access where appropriate and verify hardware security level instead of asserting hardware backing in comments.

### F02 — High: locking leaves decrypted editor visible and permits unencrypted vault saves

Evidence: [NotesViewModel.kt:204](https://github.com/Nrajesh0/Ayva/blob/eaeeb8367d4bcff6cc935e08a74b9f88d7b5c049/app/src/main/java/com/focusbyrj/app/ui/screens/notes/NotesViewModel.kt#L204), [NotesScreen.kt:1549](https://github.com/Nrajesh0/Ayva/blob/eaeeb8367d4bcff6cc935e08a74b9f88d7b5c049/app/src/main/java/com/focusbyrj/app/ui/screens/notes/NotesScreen.kt#L1549), [NotesViewModel.kt:1801](https://github.com/Nrajesh0/Ayva/blob/eaeeb8367d4bcff6cc935e08a74b9f88d7b5c049/app/src/main/java/com/focusbyrj/app/ui/screens/notes/NotesViewModel.kt#L1801), [VaultPayloadEncryptor.kt:143](https://github.com/Nrajesh0/Ayva/blob/eaeeb8367d4bcff6cc935e08a74b9f88d7b5c049/app/src/main/java/com/focusbyrj/app/util/crypto/VaultPayloadEncryptor.kt#L143), [NoteRepository.kt:90](https://github.com/Nrajesh0/Ayva/blob/eaeeb8367d4bcff6cc935e08a74b9f88d7b5c049/app/src/main/java/com/focusbyrj/app/data/note/NoteRepository.kt#L90).

`lockVault()` wipes the subkey and clears one cache, but does not clear `_editingState`, cancel pending autosave, or close the editor. The editor's visibility depends only on `editingState != null`, not lock state. The encryptor returns the original note when `vaultSubKey == null`, and the repository saves it.

Reproduction scenario: unlock an archived note, edit it, and background the app before the 300ms autosave runs. ON_STOP wipes the key while the view-model IO coroutine can still save the decrypted archived entity. Returning to the same editor also leaves decrypted content in editor state and permits further saves without the key. Stored text remains inside SQLCipher, but loses the extra PIN vault layer and may subsequently appear in plaintext snapshots.

Fix: serialize lock and persist operations through a common lifecycle/security boundary. Finish a protected save before wiping, or retain an unsaved encrypted draft. Cancel saves on lock, remove decrypted editor state/undo/history/media buffers, hide the editor, and require reauthentication. An archived write with a configured-but-locked vault must fail explicitly rather than return a plaintext entity. Device reproduction is still required to establish exact timing.

### F03 — High: PIN rotation and recovery can orphan notes after a crash

Evidence: [ArchiveVaultSecurity.kt:180](https://github.com/Nrajesh0/Ayva/blob/eaeeb8367d4bcff6cc935e08a74b9f88d7b5c049/app/src/main/java/com/focusbyrj/app/data/note/ArchiveVaultSecurity.kt#L180), [ArchiveVaultSecurity.kt:278](https://github.com/Nrajesh0/Ayva/blob/eaeeb8367d4bcff6cc935e08a74b9f88d7b5c049/app/src/main/java/com/focusbyrj/app/data/note/ArchiveVaultSecurity.kt#L278), [ArchiveVaultSecurity.kt:757](https://github.com/Nrajesh0/Ayva/blob/eaeeb8367d4bcff6cc935e08a74b9f88d7b5c049/app/src/main/java/com/focusbyrj/app/data/note/ArchiveVaultSecurity.kt#L757).

Setup/change re-encrypts and commits notes in a Room transaction, then commits the new credentials to SharedPreferences. Recovery and legacy KDF upgrades repeat this pattern. A process death or failed preference commit between these steps leaves new-key ciphertext with old persisted credentials/recovery metadata. Moving the in-memory key assignment after `commit()` does not make the two stores atomic. Envelope `kv` stays at constant 1 and does not identify individual PIN rotations.

Fix: use a stable random vault data key, change the PIN-derived wrapping key rather than re-encrypting every note, and persist the new wrapper with transactional/versioned recovery metadata. Alternatively implement a durable rotation journal retaining both key generations until successful finalization. Fault-inject process death and failed disk commits at every step.

### F04 — High: legacy migration destroys the original database after incomplete copying

Evidence: [NoteDatabaseMigrationHelper.kt:130](https://github.com/Nrajesh0/Ayva/blob/eaeeb8367d4bcff6cc935e08a74b9f88d7b5c049/app/src/main/java/com/focusbyrj/app/data/note/NoteDatabaseMigrationHelper.kt#L130), [NoteDatabaseMigrationHelper.kt:154](https://github.com/Nrajesh0/Ayva/blob/eaeeb8367d4bcff6cc935e08a74b9f88d7b5c049/app/src/main/java/com/focusbyrj/app/data/note/NoteDatabaseMigrationHelper.kt#L154), [FocusDatabaseMigrationHelper.kt:299](https://github.com/Nrajesh0/Ayva/blob/eaeeb8367d4bcff6cc935e08a74b9f88d7b5c049/app/src/main/java/com/focusbyrj/app/data/FocusDatabaseMigrationHelper.kt#L299).

The notes helper catches cursor/read failures and individual insert failures, logs successful insert count, then wipes the plaintext DB/WAL/SHM unconditionally. Focus migration also catches table-copy failures and proceeds to wipe. Disk-full, schema incompatibility, SQLCipher opening failure during the first insert, or malformed data can therefore erase uncopied records. No attacker is needed.

Fix: copy inside a transaction, propagate errors, compare complete counts and important fields, reopen and validate the encrypted destination, durably mark completion, and retain the original until verified. On retry, avoid REPLACE overwriting newer destination rows. Apply these checks before deleting any legacy fallback database as well.

### F05 — High for notes: automatic backups bypass encryption at rest

Evidence: [AutoBackupWorker.kt:40](https://github.com/Nrajesh0/Ayva/blob/eaeeb8367d4bcff6cc935e08a74b9f88d7b5c049/app/src/main/java/com/focusbyrj/app/util/backup/AutoBackupWorker.kt#L40), [DataSafetyManager.kt:549](https://github.com/Nrajesh0/Ayva/blob/eaeeb8367d4bcff6cc935e08a74b9f88d7b5c049/app/src/main/java/com/focusbyrj/app/util/backup/DataSafetyManager.kt#L549), [DataSafetyManager.kt:574](https://github.com/Nrajesh0/Ayva/blob/eaeeb8367d4bcff6cc935e08a74b9f88d7b5c049/app/src/main/java/com/focusbyrj/app/util/backup/DataSafetyManager.kt#L574).

Snapshots serialize note title/content/checklists/labels/attachment paths into unencrypted JSON. Correctly vault-encrypted archive envelopes stay encrypted; **ordinary notes do not**. Protecting the primary database while retaining plaintext copies defeats maximum protection for all notes. Same-device snapshots also do not protect against device loss, uninstall, or inaccessible storage.

Fix: encrypt snapshots with a separate versioned backup key, with a user-controlled portable recovery mechanism. Include recoverable attachment content and key-version metadata. Offer a verified portable backup/recovery workflow; distinguish local rollback snapshots from disaster recovery.

### F06 — High: restoring historical snapshots can reintroduce ciphertext under obsolete keys

Evidence: [DataSafetyManager.kt:304](https://github.com/Nrajesh0/Ayva/blob/eaeeb8367d4bcff6cc935e08a74b9f88d7b5c049/app/src/main/java/com/focusbyrj/app/util/backup/DataSafetyManager.kt#L304), [DataSafetyManager.kt:353](https://github.com/Nrajesh0/Ayva/blob/eaeeb8367d4bcff6cc935e08a74b9f88d7b5c049/app/src/main/java/com/focusbyrj/app/util/backup/DataSafetyManager.kt#L353), [SecurityScreen.kt:1276](https://github.com/Nrajesh0/Ayva/blob/eaeeb8367d4bcff6cc935e08a74b9f88d7b5c049/app/src/main/java/com/focusbyrj/app/ui/screens/SecurityScreen.kt#L1276).

Snapshots preserve encrypted archive rows but not the corresponding old subkey or credential generation. After changing the PIN, restoring a pre-change snapshot inserts old ciphertext directly using REPLACE; current credentials cannot decrypt it. Snapshots also contain media paths, not file contents, so restoring after attachment deletion cannot recover the media. A snapshot from before securing a note can restore historical plaintext/state without applying current vault policy.

Fix: make backups self-contained and key-version-aware. Validate decryptability and enforce current privacy policy before modifying live records. Rewrap/import into the current vault key, or retain recoverable versioned key envelopes. Show previewed overwrite/conflict behavior.

### F07 — High: default encrypted backup restore overwrites local notes with colliding numeric IDs

Evidence: [BackupRestoreManager.kt:639](https://github.com/Nrajesh0/Ayva/blob/eaeeb8367d4bcff6cc935e08a74b9f88d7b5c049/app/src/main/java/com/focusbyrj/app/util/backup/BackupRestoreManager.kt#L639), [BackupRestoreManager.kt:695](https://github.com/Nrajesh0/Ayva/blob/eaeeb8367d4bcff6cc935e08a74b9f88d7b5c049/app/src/main/java/com/focusbyrj/app/util/backup/BackupRestoreManager.kt#L695), [NoteDao.kt:67](https://github.com/Nrajesh0/Ayva/blob/eaeeb8367d4bcff6cc935e08a74b9f88d7b5c049/app/src/main/java/com/focusbyrj/app/data/note/NoteDao.kt#L67).

`cleanRestore` defaults to false, but restored notes retain backup IDs and `insertNote` uses REPLACE. Importing a backup from a different device whose note 1 is unrelated to local note 1 overwrites the local note instead of adding it. Similar source-ID collisions apply to other REPLACE entities. The pre-operation JSON snapshot mitigates some text loss, but does not make this safe merge semantics.

Fix: use globally stable IDs with explicit conflict resolution, or assign new local IDs and remap references in import mode. Distinguish replace, merge, and duplicate-import behavior before mutation. Test unrelated devices with overlapping IDs.

### F08 — High: full restore can fail after replacing live data

Evidence: [BackupRestoreManager.kt:680](https://github.com/Nrajesh0/Ayva/blob/eaeeb8367d4bcff6cc935e08a74b9f88d7b5c049/app/src/main/java/com/focusbyrj/app/util/backup/BackupRestoreManager.kt#L680), [BackupRestoreManager.kt:705](https://github.com/Nrajesh0/Ayva/blob/eaeeb8367d4bcff6cc935e08a74b9f88d7b5c049/app/src/main/java/com/focusbyrj/app/util/backup/BackupRestoreManager.kt#L705), [BackupRestoreManager.kt:983](https://github.com/Nrajesh0/Ayva/blob/eaeeb8367d4bcff6cc935e08a74b9f88d7b5c049/app/src/main/java/com/focusbyrj/app/util/backup/BackupRestoreManager.kt#L983), [BackupRestoreManager.kt:987](https://github.com/Nrajesh0/Ayva/blob/eaeeb8367d4bcff6cc935e08a74b9f88d7b5c049/app/src/main/java/com/focusbyrj/app/util/backup/BackupRestoreManager.kt#L987).

Notes, FocusDatabase, drill/vocabulary/chat stores, preferences, and media are committed in separate phases. Late malformed input, failed preference writes, media encryption failure, or disk-full can return failure after earlier stores have already changed. Installing media truncates existing destination files directly; there is no all-app rollback. Individual Room transactions provide only local atomicity. Pre-operation snapshot failure is ignored and snapshot data does not fully restore files/settings/all databases.

Fix: validate and decrypt all input first, build a complete staged generation, then use a journaled switch with resumable rollback. Keep old files/stores until validation and commit finish. Check every disk commit result and refuse destructive restoration without a verified recovery point.

### F09 — High: attachment deletion precedes successful persistence; cleanup can delete valid media

Evidence: [NotesViewModel.kt:1089](https://github.com/Nrajesh0/Ayva/blob/eaeeb8367d4bcff6cc935e08a74b9f88d7b5c049/app/src/main/java/com/focusbyrj/app/ui/screens/notes/NotesViewModel.kt#L1089), [NotesViewModel.kt:1473](https://github.com/Nrajesh0/Ayva/blob/eaeeb8367d4bcff6cc935e08a74b9f88d7b5c049/app/src/main/java/com/focusbyrj/app/ui/screens/notes/NotesViewModel.kt#L1473), [NotesViewModel.kt:1645](https://github.com/Nrajesh0/Ayva/blob/eaeeb8367d4bcff6cc935e08a74b9f88d7b5c049/app/src/main/java/com/focusbyrj/app/ui/screens/notes/NotesViewModel.kt#L1645), [NoteMediaManager.kt:153](https://github.com/Nrajesh0/Ayva/blob/eaeeb8367d4bcff6cc935e08a74b9f88d7b5c049/app/src/main/java/com/focusbyrj/app/data/note/NoteMediaManager.kt#L153).

Removing image/audio references immediately deletes their files while saving is asynchronous. A failed/cancelled save leaves the persisted note referencing destroyed data; undo can also restore a reference whose file has gone. Empty Trash removes files before taking the repository's pre-operation snapshot and deleting rows. Duplicate operations reuse original paths when copying fails, so deleting either note can destroy media referenced by the other.

Orphan cleanup protects the normal locked-vault case, but uses legacy `decryptNotePayload`, which returns ciphertext on corruption/key mismatch. It then sees empty media lists and can delete valid files older than five minutes. A single unreadable note must not authorize deleting its unidentified attachments.

Fix: transactionally remove references first, enqueue deferred file collection, and delete only when no live/trashed/draft/recoverable reference remains. Abort cleanup if any payload cannot be inspected. Preserve attachment copies in recovery snapshots or a retention store; fail duplication when independent copies cannot be made.

## Other confirmed issues and policy gaps

### F10 — Medium: failed saves appear successful and drafts are discarded

Evidence: [NoteRepository.kt:90](https://github.com/Nrajesh0/Ayva/blob/eaeeb8367d4bcff6cc935e08a74b9f88d7b5c049/app/src/main/java/com/focusbyrj/app/data/note/NoteRepository.kt#L90), [NotesViewModel.kt:1745](https://github.com/Nrajesh0/Ayva/blob/eaeeb8367d4bcff6cc935e08a74b9f88d7b5c049/app/src/main/java/com/focusbyrj/app/ui/screens/notes/NotesViewModel.kt#L1745), [NotesViewModel.kt:1827](https://github.com/Nrajesh0/Ayva/blob/eaeeb8367d4bcff6cc935e08a74b9f88d7b5c049/app/src/main/java/com/focusbyrj/app/ui/screens/notes/NotesViewModel.kt#L1827).

Repository catches exceptions and returns `0L`; several mutations similarly swallow errors. Close clears editing state before persistence, and callers cache the entity regardless of failure. Users can believe a note is saved when it is not, especially on full storage or crypto failure. CancellationException is also caught as Exception in repository paths. Return typed results, retain dirty drafts until successful durable save, show save/retry state, and propagate cancellation.

### F11 — Medium for notes: screen capture, speech, and export residue weaken privacy

Evidence: [MainActivity.kt:240](https://github.com/Nrajesh0/Ayva/blob/eaeeb8367d4bcff6cc935e08a74b9f88d7b5c049/app/src/main/java/com/focusbyrj/app/MainActivity.kt#L240), [AudioMemoManager.kt:122](https://github.com/Nrajesh0/Ayva/blob/eaeeb8367d4bcff6cc935e08a74b9f88d7b5c049/app/src/main/java/com/focusbyrj/app/ui/screens/notes/AudioMemoManager.kt#L122), [AudioMemoManager.kt:184](https://github.com/Nrajesh0/Ayva/blob/eaeeb8367d4bcff6cc935e08a74b9f88d7b5c049/app/src/main/java/com/focusbyrj/app/ui/screens/notes/AudioMemoManager.kt#L184), [ArticleExporter.kt:639](https://github.com/Nrajesh0/Ayva/blob/eaeeb8367d4bcff6cc935e08a74b9f88d7b5c049/app/src/main/java/com/focusbyrj/app/ui/screens/notes/ArticleExporter.kt#L639).

MainActivity FLAG_SECURE is optional and defaults off, including the notes editor and vault dialogs. Speech uses the default recognizer without requiring an on-device recognizer; full recognized text is sent to Log.d. Release shrinking rules reviewed here do not establish that this log is removed. Logcat access is restricted on normal devices, but transcript logging is still inappropriate for highly protected notes. Sharing writes plaintext exports to cache with no cleanup in this path; another export with the same title can overwrite a still-shared filename.

Fix: automatically protect notes/secret/recovery windows, with standard capture behavior elsewhere. Use on-device speech only for maximum privacy, or obtain clear feature-specific consent before delegating to a service; remove transcript logging. Use unpredictable unique export filenames and bounded cleanup/revocation. User-directed sharing necessarily exposes plaintext to the selected recipient and should be explicit.

### F12 — Medium: habit increments can lose updates

Evidence: [HabitRepository.kt:127](https://github.com/Nrajesh0/Ayva/blob/eaeeb8367d4bcff6cc935e08a74b9f88d7b5c049/app/src/main/java/com/focusbyrj/app/data/HabitRepository.kt#L127), [HabitRepository.kt:152](https://github.com/Nrajesh0/Ayva/blob/eaeeb8367d4bcff6cc935e08a74b9f88d7b5c049/app/src/main/java/com/focusbyrj/app/data/HabitRepository.kt#L152), [HabitDao.kt:65](https://github.com/Nrajesh0/Ayva/blob/eaeeb8367d4bcff6cc935e08a74b9f88d7b5c049/app/src/main/java/com/focusbyrj/app/data/HabitDao.kt#L65).

Progress is read, copied with count+1/-1, and REPLACE-inserted without a surrounding transaction. UI/notification operations can read the same count and overwrite one another. Synthetic SQLite interleaving demonstrated two increments resulting in one. Habit deletion also removes logs and the habit in separate operations. Use transactional insert-if-absent plus an atomic SQL increment, transactional deletes, and constraint-backed relationships.

### F13 — High readiness blocker: release checks are missing and test sources are stale

Evidence: [.github/workflows/release.yml:65](https://github.com/Nrajesh0/Ayva/blob/eaeeb8367d4bcff6cc935e08a74b9f88d7b5c049/.github/workflows/release.yml#L65), [app/build.gradle.kts:42](https://github.com/Nrajesh0/Ayva/blob/eaeeb8367d4bcff6cc935e08a74b9f88d7b5c049/app/build.gradle.kts#L42), [SyncAndConflictResolutionTest.kt:7](https://github.com/Nrajesh0/Ayva/blob/eaeeb8367d4bcff6cc935e08a74b9f88d7b5c049/app/src/test/java/com/focusbyrj/app/SyncAndConflictResolutionTest.kt#L7).

Only release assembly is required before artifact upload/publishing; tests and lint are not gates. Workflow push triggering is limited to `app/build.gradle.kts`, and wrapper validation is disabled. Build configuration sets `abortOnError=false` and `checkReleaseBuilds=false`. Multiple test suites import SupabaseSyncEngine/AuthManager/StorageEngine/KeyManager, whose implementations are absent from this repository and declared dependencies. This is a source-level test compilation blocker unless an uncommitted external source is supplied; it was not confirmed with an Android build here.

Fix: repair/delete obsolete tests with meaningful replacements, require build/lint/unit/instrumentation checks for every PR, require signing configuration for release, validate wrapper, and test encryption on devices without software fallbacks. Add fault-injection coverage for all high findings and real stored-schema migrations. Notes and Focus databases disable Room schema export, reducing migration validation evidence; export historical schemas and test every supported upgrade path.

### F14 — Medium: import trust and resource limits are incomplete

Evidence: [BackupRestoreManager.kt:554](https://github.com/Nrajesh0/Ayva/blob/eaeeb8367d4bcff6cc935e08a74b9f88d7b5c049/app/src/main/java/com/focusbyrj/app/util/backup/BackupRestoreManager.kt#L554), [BackupRestoreManager.kt:926](https://github.com/Nrajesh0/Ayva/blob/eaeeb8367d4bcff6cc935e08a74b9f88d7b5c049/app/src/main/java/com/focusbyrj/app/util/backup/BackupRestoreManager.kt#L926), [NotesViewModel.kt:999](https://github.com/Nrajesh0/Ayva/blob/eaeeb8367d4bcff6cc935e08a74b9f88d7b5c049/app/src/main/java/com/focusbyrj/app/ui/screens/notes/NotesViewModel.kt#L999).

ZIP media has limits, but `data.json` is read into unbounded ByteArrayOutputStream. Restore accepts every preference namespace present in the archive rather than enforcing its export allowlist, including namespaces that could overwrite security/key state. A third-party backup encrypted using its creator's password is not trusted simply because the user can decrypt it. Attachment import trusts provider-reported size then streams with uncapped copyTo; providers can report unknown/false sizes and exhaust disk. Check limits on bytes actually read, cap JSON/entities/entry count, reject duplicates/unknown versions, and allowlist safe preference keys/namespaces while excluding encryption credentials.

## Dormant helper defects: do not describe as active cloud vulnerabilities

[VaultSyncManager.kt:117](https://github.com/Nrajesh0/Ayva/blob/eaeeb8367d4bcff6cc935e08a74b9f88d7b5c049/app/src/main/java/com/focusbyrj/app/util/sync/VaultSyncManager.kt#L117) has no call sites in current production Kotlin source. Its replace mode deletes all notes/tasks before validating every record, with no transaction or snapshot. Archived imports call an encryptor that can return plaintext if locked. Repeated default merge imports allocate fresh numeric IDs and duplicate records; attachment paths are serialized without transporting files. These must be fixed or the helper removed before connecting it to UI/sync. They are not evidence of an exposed cloud endpoint today. [NotesViewModel.kt:1919](https://github.com/Nrajesh0/Ayva/blob/eaeeb8367d4bcff6cc935e08a74b9f88d7b5c049/app/src/main/java/com/focusbyrj/app/ui/screens/notes/NotesViewModel.kt#L1919) explicitly shows empty automatic sync.

## Positive controls worth retaining

- SQLCipher for notes, random database passphrase on normal initialization, AES-GCM, random IVs, real Argon2id integration, constant-time PIN comparison, and attempts/lockout tracking.
- Existing-key alias collision safeguards and refusal to overwrite keys when their entries cannot be loaded as expected.
- Narrow FileProvider exports/diagnostics cache roots, non-exported sensitive action receivers, BIND_REMOTEVIEWS protection, and widget guards excluding archived/trashed notes. I did not establish a widget vault-reading bypass.
- Image MIME/scheme/dimension checks, several media size guards, backup ZIP traversal checks and encrypted manual backups.
- Soft-delete trash, timestamps, Room transactions for some restore phases, and AtomicFile snapshots. These reduce risk but do not resolve the cross-store/lifecycle failures above.

## Protection levels that fit the requested product

| Requirement | Notes: maximum practical protection | Other features: standard protection |
|---|---|---|
| Storage | Encrypted database, media, snapshots, drafts; no fixed/plaintext fallbacks | Android private storage, parameterized Room operations; encryption optional based on sensitivity |
| Access | Explicit vault/session policy, reauthentication, fail-closed archived writes, prompt removal of decrypted UI state | Normal app/device access; avoid adding PIN prompts to every task/habit action |
| Key architecture | Separate notes domain; random data key; PIN/passphrase wraps key; durable recoverable rotation | Independent domain so notes-key failure does not disable unrelated features |
| Screen/service exposure | Secure sensitive windows, controlled clipboard/share/export, on-device speech or informed opt-in | Normal Android capture/notification behavior, reasonable user privacy preferences |
| Data integrity | Save acknowledgements, recoverable drafts, reference-safe media GC, verified migration/restore | Transactions, atomic counters, validation and reliable error handling remain mandatory |
| Recovery | Self-contained encrypted portable backup with tested restoration and key versions | Standard backup/restore with explicit conflict semantics |
| Tests | Device crypto/auth/lifecycle, process-death/disk-full/corruption/rotation tests | Unit/integration tests for meaningful feature correctness |

“Maximum protection” cannot guarantee secrecy after a fully compromised unlocked process/device. Define the threat model and measure against Android MASVS storage/crypto/auth/platform requirements. A six-digit PIN alone has one million possibilities; Argon2 slows offline guessing but does not make that a high-entropy encryption secret. Stronger passphrases and authentication-bound hardware wrapping should be supported where the threat model demands it.

## Recommended implementation order and release acceptance

1. Remove F01 production fallbacks and fix F02 vault/editor lifecycle. Keep a safe recovery path for already-existing data; do not mass-reset keys.
2. Implement durable key wrapping/rotation and verified migration before shipping another schema/key change (F03–F04).
3. Encrypt and make recovery artifacts self-contained; fix ID conflict semantics, all-store restore, and attachment retention (F05–F09).
4. Make save failures visible, enforce notes-specific privacy, fix concurrent feature mutations and import limits (F10–F12/F14).
5. Repair tests and require CI checks before any release (F13). Test supported Android/API/ABI versions, devices with temporary/inaccessible Keystore, locked/restarted devices, malformed/oversized backups, same/different-device restore, and process death/disk-full at each critical transition.

Release only after demonstrated invariants: locked vault writes cannot become plaintext; every supported migration preserves every record or keeps the source intact; PIN changes/recovery survive interruption; restored notes and media decrypt after restart; merge import cannot overwrite unrelated data; failed saves retain user edits; deletion never destroys another note's attachments; no plaintext notes remain in internal snapshots; and the pinned release passes its repaired checks. Current code does not satisfy those invariants.
