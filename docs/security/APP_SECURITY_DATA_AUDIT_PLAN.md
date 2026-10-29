# Run security, data integrity and flow audit

Created: 29 October 2026. Status: source audit executed; focused fixes verified separately. See [audit results](APP_SECURITY_DATA_AUDIT_2026_10_29.md) for all batch verdicts, outstanding findings and unexecuted device checks.

## Purpose and boundaries

Audit the current app for actionable security weaknesses, lost or corrupted data, incorrect recovery, broken flows and conflicts between features. Notes, attached images/files/audio, vaults, backups, restores and snapshots receive the deepest review. Other features receive normal protection checks appropriate to their data and permissions. User data preservation is the primary acceptance criterion.

This is an evidence-led audit, not a redesign or a requirement to produce findings. A correct implementation should remain as it is. Cosmetic preferences, theoretical attacks with no credible path, alternative coding styles, and speculative “best practice” changes are not defects. Optional improvements must be labelled optional and kept outside remediation unless accepted separately.

Planning does not establish that a feature is secure. Record the exact reviewed commit, local changes, build type, device/API and evidence for every completed batch. Distinguish source-reviewed, exercised, device-verified and unverified behavior. No absolute security guarantee or arbitrary numerical score.

The older AYVA_SECURITY_DATA_REVIEW.md and AYVA_REMEDIATION_PLAN.md are historical inputs. Revalidate each relevant finding against current code; do not blindly apply old fixes or rerun old source assertions as proof. Preserve historical reports and identify already-resolved findings. At plan creation the checkout also contains unpublished transcription-popup work; establish a fixed baseline before auditing it.

## Protection expectations

| Area | Required attention |
|---|---|
| Notes and vault | Strong confidentiality at rest, correct key lifecycle, access checks at every exposed entry, faithful storage and recovery |
| Attachments and media | Same confidentiality expectations as their parent data; bounded parsing, safe paths, reliable association and cleanup |
| Backups and exported recovery files | Authenticated encryption where promised, clear export boundaries, complete contents, portable recovery where promised |
| Restore and snapshots | Authenticate and validate before live mutation; preserve current data on rejection/failure; explicit merge/replace semantics and recovery after interruption |
| Tasks, habits, schedules, chat and settings | Normal app-private storage and permission boundaries; correctness, predictable commands, no accidental destructive actions |
| Notifications, widgets, sharing and speech | Disclosure appropriate to the surface, least necessary access, no accidental bridge into private Notes or vault data |

Threats considered: a lost locked device; another ordinary Android app; a malicious or damaged imported file/archive; tampered backups; incorrect passwords; unavailable or invalidated keys; compromised sharing permissions; accidental user operations; process death, reboot, cancellation, concurrency and storage failure. A rooted device or attacker controlling an unlocked process is a limitation to document, not a promise that encryption can solve. Android Keystore behavior must be checked on available real hardware before making hardware-backed claims.

## Data-preservation rules for conducting the audit

- Use disposable app profiles and synthetic fixtures. Never experiment on the user's only database, backup, keys or attachments.
- Record a reproducible baseline and preserve fixture originals before modifying or restoring them. Do not clear storage, regenerate keys, use destructive migration, or prune snapshots to make an error disappear.
- An unreadable database is not an empty database. Authentication, parsing and I/O errors must remain distinguishable from a valid empty dataset.
- Compare meaningful contents, identifiers, relationships and attachment hashes across round trips. Counts alone cannot establish successful recovery; encrypted bytes need not be identical across exports.
- Review backup completeness before relying on it to protect a destructive operation. A snapshot that omits attachments must not silently promise full recovery.
- Do not log note bodies, passwords, keys, transcript contents or genuine user files in reports or CI artifacts. Use sanitized synthetic evidence.
- Fix only confirmed issues with a plausible user impact. Use the smallest appropriate change; preserve format compatibility and existing correct behavior.
- Follow AGENTS.md: blocking remains a direct WindowManager overlay. Do not introduce Activity launches as its primary mechanism.

## Audit method and evidence

For each batch: read its callers and downstream storage/actions; document the intended contract; inspect realistic failure paths; exercise only the critical paths needed to resolve uncertainty; issue a verdict. Where a defect is confirmed, implement a focused repair and rerun the affected scenario plus its immediate compatibility boundary. Avoid broad repeated suites without evidence of wider risk.

Use these verdicts:

- **No actionable issue found:** reviewed scope behaves as intended; keep it unchanged. State evidence and limits.
- **Confirmed issue:** reproducible defect or concrete reachable unsafe path, with affected data and impact.
- **Needs verification:** a specific unresolved question, why evidence is missing, and the check needed. Do not describe it as an established vulnerability.
- **Accepted tradeoff / optional improvement:** current behavior is reasonable for its scope; no mandatory change.
- **Not applicable:** explain why the path or capability does not exist in the reviewed build.

Severity follows impact and reachability: critical for plausible broad private-data exposure or irreversible loss; high for significant corruption, access bypass or failed recovery; medium for bounded loss, privacy leakage or repeatable disruptive failures; low for limited-impact defects. A frightening code pattern alone does not determine severity.

Every confirmed finding records: ID, reviewed revision, source locations/call path, trigger and prerequisites, expected/actual behavior, affected data, likelihood and severity rationale, minimal remedy, compatibility implications and verification evidence. Do not store an exploitable recipe using real user secrets.

## Batches and dependency order

### Batch 0 — Baseline, inventory and historical review

Map data categories to owners, databases, preferences, files, encryption, export/sync destinations, retention and deletion. Trace Notes, attachments, chat, widgets, backups, snapshots and vault entry points. Inventory schema/file-format versions and supported upgrade routes, including older supported releases from the historical review. Record release/debug differences and dependencies used for cryptography, parsing and storage.

Entry points: NoteDatabase, NoteDao, DatabaseKeyProvider, NoteMediaManager, ArchiveVaultSecurity, app manifest, backup rules, FileProvider paths, current security documents and build configuration.

Deliver: baseline revision, data-flow map, format/version inventory, reconciled old findings and prioritized execution checklist. No automatic fixes based on old reports.

### Batch 1 — Cryptography and key lifecycle

Review DatabaseKeyProvider, NoteDatabase initialization, VaultPayloadEncryptor, EncryptedMediaStorage, VaultCryptoEngine, CryptoBackupEngine, SnapshotCryptoEngine and EncryptedSnapshotFile together. Identify actual algorithms, authenticated metadata, randomness/nonce uniqueness, key separation, password KDF parameters and bounds, key wrapping/storage, and format version negotiation. Check wrong passwords, changed headers, truncation, corrupted tags and plaintext fallback paths.

Follow key creation through upgrade, credential changes, biometric/device-lock changes, key invalidation, restore to another device, app-data reset and missing/corrupted key material. Verify that unreadable keys cannot trigger replacement of existing encrypted data. Identify which files are intentionally device-bound and which are portable.

Essential evidence: valid read/write, rejected tampering and wrong key/password without altering original data, and a documented recovery outcome when keys are unavailable. Hardware-specific behavior remains pending until a suitable device is available.

### Batch 2 — Notes persistence, migrations and editing durability

Trace NotesViewModel/editor state → NoteDao → NoteDatabase, including NoteDatabaseMigrationHelper and SQLCipher/Room journal handling. Examine autosave, switching notes, recreation/backgrounding, simultaneous editors, checklist/rich-text conversion, timestamps, archive/trash and migrations. Check transaction boundaries, error propagation, cancellation and accidental destructive fallbacks.

Exercise representative oldest-supported and recent migrations, mixed note types, attachments, empty/large/Unicode content, and process interruption around commit. Compare actual contents and relationships. User-visible success must correspond to durable data, or the UI must clearly report pending/failed saving.

### Batch 3 — Images, files, audio and attachment lifecycle

Trace selection/capture/import → validation → encryption → stored reference → rendering/editing → export/share → deletion. Review NoteMediaManager, EncryptedMediaStorage, EncryptedMediaFetcher, media pickers, audio readers, renderers and FileProvider consumers.

Check unreliable MIME/extension metadata, malicious names and paths, URI permission lifetime, unsupported/corrupt formats, huge decoded dimensions, archive expansion, allocation limits, EXIF/orientation, temporary plaintext, thumbnail/cache behavior, stream closure and interrupted imports. Trace shared/deduplicated attachments and orphan cleanup: removing one note must not remove another note's media, and a missing file must not silently erase the note reference.

Essential evidence: attachment hashes and parent links survive save/export/restore; malformed/oversized input fails within bounded resources; failure cleanup does not delete valid existing files. Include canceled imports, denied/revoked URI access and low storage.

### Batch 4 — Backup creation and portability

Trace BackupRestoreManager, CryptoBackupEngine, AutoBackupWorker, AutoBackupScheduler and backup UI. Inventory every included/excluded data category, attachment and required metadata/key reference. Examine concurrent edits, encryption finalization, interrupted writes, external document-provider failures, partial output, temporary files and retention.

Determine how a complete consistent backup is produced across database and filesystem changes. Require a truthful completion result and preservation of the last known-good backup. Verify promised portability on a fresh installation using synthetic data. Review exported plaintext only where intentionally offered, with clear user choice and no misleading encrypted label.

### Batch 5 — Restore validation, atomicity and rollback

Audit preview and commit paths for merge, replace and partial restore. Treat imported archives as untrusted: validate format/version, authenticated contents, paths, duplicate entries/IDs, sizes, attachment references and unsupported versions before modifying live state. Check whether streamed authentication completes before any decrypted data becomes live.

Trace staging, database transactions, file moves, preferences and key changes, rollback journals/recovery markers and post-restart cleanup. One database transaction does not cover filesystem changes. Check ID remapping, collisions, duplicate retries, locked vault state, references and alarm/widget rebuilding.

Essential scenarios: wrong password, modified/truncated backup, corrupt entry near the end, low storage and process death before/during/after commit. A failed restore must leave the previous dataset usable or an explicit recoverable state; never silently report success after partial mutation. Successful recovery must match the intended source under documented merge rules.

### Batch 6 — Snapshots, trash and disaster recovery

Review DataSafetyManager, EncryptedSnapshotFile, SnapshotCryptoEngine, TaskRecoveryStore and every destructive caller. Establish whether pre-operation snapshots are complete, durable and recoverable before the operation begins. Review scheduled snapshots, emergency recovery, trash expiry, retention, clock changes, pruning and space exhaustion.

Check corrupt newest snapshots with older valid ones, restore selection/preview, repeated restores, missing attachment keys and cleanup after failed snapshot creation. Verify that the app does not delete the only useful recovery copy or imply that snapshots recover data they never contained. Retention and explicit permanent-delete behavior should match their UI explanation.

### Batch 7 — Vault access and sync conflicts

Review ArchiveVaultSecurity, VaultSyncManager, VaultCryptoEngine and VaultPayloadEncryptor, including UI, widgets, notifications, search and export entry points. Check unlock lifetime, background/relock, permission bypass, caches and error messages. Determine actual network/backend use before assessing remote risks; do not assume a named sync class implies a remote service.

Where synchronization exists, inspect authentication/transport, stale writes, merge ordering, deletions/tombstones, duplicate/out-of-order deliveries, device clock skew, interrupted sync and concurrent edit/restore. A remote empty/error response must not become a mass local deletion. Keep task/ordinary-data protection proportionate while maintaining private Notes boundaries.

### Batch 8 — Android boundaries and data leakage

Review exported activities/services/receivers, PendingIntents, deep links, FileProvider grants/paths, share sheets, clipboard, thumbnails, logs/crash reporting, screenshots/recents, notifications/widgets, WebView/document rendering, OS backup/device-transfer configuration and cleartext network policy.

Check private content on unintended surfaces and unauthorized invocations. Normal features need appropriate permissions and internal storage, not new vault-style friction. Review speech-service disclosures and microphone lifecycle; distinguish Android/provider online processing from app-owned offline functionality. Check shipped dependencies and known advisories only for actual version/reachability, not warnings detached from use.

### Batch 9 — Cross-feature races and recovery conflicts

Use the previous flow maps to select realistic combinations: autosave during backup; attachment import during note switch; restore during sync/auto-backup; trash purge during snapshot; two editors; app lock during media view; widget access after relock; cancellation/process death during file move; transcription correction during a late speech callback.

Inspect shared locks, transaction order, worker retries, idempotency and ownership. Reconcile database contents, media references, UI state and recovery state after each selected interruption. Fix reproducible conflicts; avoid speculative global locking or rewrites.

### Batch 10 — Ordinary feature correctness and general flows

Perform proportionate checks of tasks/subtasks, habits and pause/resume, scheduling/time zones, reminders, focus/blocking, widgets, chat commands, settings, search, import/export and navigation. Focus on incorrect actions, duplicate operations, lost drafts, broken Back behavior, stale controls, permission denial and conflicts users can encounter.

Keep accepted planning behavior unless a concrete defect is found. Verify important data contracts rather than demanding advanced encryption for harmless preferences or public content. Respect direct-overlay blocking requirements and Android lifecycle limitations.

### Batch 11 — Remediation verification and final report

Complete confirmed repairs in small reviewable changes, keeping discovery evidence separate from fix evidence. Validate only affected contracts and nearby recovery/compatibility paths, then run required release checks once for the final revision. Include upgrade and restore evidence when those contracts changed.

Publish a batch-by-batch verdict, resolved and outstanding findings, known tradeoffs, device/environment limits and release recommendation. Explicitly list healthy areas left unchanged. Mark every unexecuted check as pending; do not mark an entire feature unsafe because a device or fixture is unavailable. Version bumps and publication are separate requested actions.

## Required failure coverage, used selectively

Use one reusable synthetic fixture set: empty dataset; mixed Notes/checklists/archived/trashed/vault items; shared and missing media; image/audio/document files; tasks with references; old supported formats; large but supported contents. Keep an expected content manifest and attachment hashes.

| Failure or transition | Evidence sought |
|---|---|
| Wrong password, wrong key, corrupted authenticated bytes | Rejection without altering live data |
| Interrupted write, canceled export, document-provider failure | Incomplete output not reported as a usable backup; existing backup retained |
| Restore crash at each meaningful commit boundary | Deterministic recovery; old or complete new data, no silent mixture |
| Low storage or denied access | Explicit error and preserved existing content |
| Key invalidation, credential change, fresh-device restore | Documented recovery limits; no silent key replacement/data reset |
| Concurrent save/import/backup/sync/purge | Consistent records and files; no lost updates or unintended deletion |
| Upgrade across supported schemas/formats | Preserved contents, IDs/references and decryptability |
| Relock/restart/widget/share handoff | Access boundaries and correct cleanup of temporary grants/files |

Do not exhaustively cross every scenario with every feature. Prioritize destructive boundaries and new uncertainty. Reuse existing checks when they exercise the real implementation. Synthetic crypto models and source searches are supporting evidence, not substitutes for testing actual encrypted storage and recovery paths.

## Work ledger and stopping rule

Initial status was **Not started**. Current per-batch results are in the linked audit report; do not infer device verification from source-review completion. Complete them in order 0 → 1 → 2/3 → 4 → 5 → 6/7 → 8 → 9 → 10 → 11, adjusting order only for a newly confirmed high-impact dependency. Each batch report must include reviewed paths, evidence, verdict, confirmed findings and any remaining limitations.

Stop investigating a path when its intended contract is adequately supported and no credible issue remains. Do not manufacture changes to justify the audit. If a batch is healthy, record “No actionable issue found within the reviewed scope; no changes required.” If a finding is blocked, explain what evidence or environment is needed and continue independent batches.

Completion means the planned scope has explicit verdicts, necessary fixes have appropriate evidence, and unresolved risks are plainly recorded. It does not mean every file was rewritten, every optional hardening idea was implemented, or unavailable checks were assumed to pass.
