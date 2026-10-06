# Ayva remediation plan: preserve existing users' data first

Baseline: security/data review of `eaeeb8367d4bcff6cc935e08a74b9f88d7b5c049`. This is an implementation and release plan, not a completed fix. Application source remains unchanged. Covers F01–F14 and dormant VaultSyncManager defects from [the review](AYVA_SECURITY_DATA_REVIEW.md).

**Release rule:** an update must preserve user records, attachment bytes, recoverable key material, and user-visible state before tightening protection. Failure must leave a usable previous generation or a preserved recovery state, never an empty replacement database. Notes receive maximum practical protection; other features retain standard protection and reliable data handling.

## Compatibility baseline and non-negotiable rules

The user reports that existing installations are probably above v1.6.0. Use **v1.6.0 inclusive as the conservative compatibility floor**, covering every shipped build since then, including private builds. GitHub release listing checked on 2026-10-06 contains 16 published APK releases: v1.8.3, v1.8.4, v1.8.5, v1.8.6, v1.8.7, v1.8.8, v1.8.9, v1.9.0, v1.9.2, v1.9.3, v1.9.4, v1.9.5, v1.9.6, v1.9.7, v1.9.8, v1.9.9. Recover v1.6.x through v1.8.2 APKs/schemas from legitimate archived artifacts, repository history, or preserved test installations. Missing releases in the GitHub list do not establish absence of affected users. Obtain their APK/source/signing fingerprints and add them to the support matrix. Capture artifact hashes; do not assume a mutable tag reproduces an APK exactly. Missing evidence for any supported schema/crypto transition is a release blocker, not a waiver; reconstructed fixtures must be identified and independently validated against historical code/artifacts.

- Preserve applicationId, signing identity/signing lineage, and minSdk support unless a separately approved product change is made. Never ask existing users to uninstall, clear storage, reset the vault, or lose access to upgrade.
- Test direct upgrades from every supported release. Users may skip every intermediate remediation release.
- Never use destructive migration, replace an unreadable database with a blank one, regenerate an existing key on failure, or delete a file merely because parsing/decryption failed.
- Keep old-format **readers** isolated for recovery/migration. New-format **writers** must never use static keys, unwrapped secret keys, or a mislabeled KDF.
- Credentials and successful decryptability are prerequisites for migration; corrupted credentials are not “first run.” Distinguish transient Keystore errors, unavailable native libraries, permanent invalidation, wrong credentials, corruption, and disk-full.
- All data conversions preserve original payload bytes/values: titles, text, block JSON, checklist IDs/order, labels, colors/fonts, created/updated times, trash times, archived state, media, and unknown legacy fields. Normalization is a separate explicit feature, not a migration side effect.
- Pause migration-dependent writes, backup/restore, cleanup, rotation, and purge through one operation coordinator. Run migration checks before application startup workers and destructive housekeeping. A failed notes migration must not wipe notes or unnecessarily block unrelated features.
- A backup is not verified by “file exists.” Reopen it, authenticate it, inspect contents, and demonstrate restoration independently.
- Do not promise recovery of data already destroyed by old code. If old ciphertext's key/salt no longer exists, retain it and report the recovery limits; do not overwrite it with placeholders.
- Preserve the existing WindowManager app-blocking mechanism required by AGENTS.md. Security work does not redesign blocker overlays or substitute background Activity launches.

## Target architecture

### Keys and formats

Introduce explicit versioned envelopes describing algorithm, KDF parameters, salt, nonce, key ID/generation, and authenticated object context. Authenticate record/media identity and format context with AEAD additional authenticated data in new formats; preserve old readers. Bound parsing before allocating or deriving keys.

Use random data-encryption keys. A PIN/passphrase-derived key wraps the vault data key; changing a PIN changes the wrapper, not every note's ciphertext. Recovery credentials independently wrap that same data key. Persist wrapper transitions and status in one durable transactional metadata store, with an encrypted recovery journal when filesystem/Keystore operations span stores. Do not persist the data key as a password verifier; use successful authenticated unwrap to verify credentials.

Keep notes and ordinary feature key domains independent. Existing databases encrypted under the shared passphrase stay readable throughout staged separation; do not abruptly enable authentication on the old shared alias. Avoid gratuitously decrypting general databases just because standard protection would allow it.

For notes, protect database/media/snapshots/drafts at rest. Secret archive attachments must be protected by the vault domain as well as their references. Choose a per-attachment random data key wrapped into the appropriate notes/vault domain; shared references have explicit ownership. Old attachment bytes remain available until the new wrappers/files are verified.

Use authentication-bound Keystore wrapping for interactive sensitive access where supported, and inspect security level instead of assuming all devices provide StrongBox. Do not require StrongBox on hardware that lacks it or silently substitute a fixed software key. Define a documented secure supported-device fallback policy or a preserved read-only recovery state. Background backup must not introduce a second unauthenticated unlock route: it can copy a consistent encrypted generation and already-protected recovery envelopes, or defer work requiring authentication.

Retain existing PINs through migration. Offer a stronger passphrase option; clearly document that six digits plus a KDF is not high-entropy protection against offline guessing. Device authentication and backup recovery are different mechanisms; portable backups must not depend solely on a non-exportable device key.

### Durable operations

Create a coordinator for migration, restore, key changes, autosave/lock, snapshots, and media collection. Record operation ID, source/destination generation, schema/crypto versions, phase, verification result, and recovery action. All APIs return typed results and propagate coroutine cancellation. Completion means the result survives process death, not just that an in-memory callback ran.

For cross-database/file changes, use immutable staged generations and a durable active-generation pointer plus a recoverable journal. Room transactions alone cannot atomically commit separate databases and files. Define restart behavior at every transition. Serialize or version concurrent edits; never let a copy-on-upgrade lose edits made during the copy.

## Implementation sequence and gates

### Work package 1 — Reproducible builds, fixtures, and safety guardrails

Addresses F13 and establishes tests for every finding.

1. Obtain a full repository checkout including wrapper/assets; configure a pinned JDK/Gradle/Android SDK toolchain. Confirm release baseline assembly, dependency compatibility, ABI support, and signing.
2. Repair stale tests referencing removed Supabase code by replacing them with meaningful tests of current local features. Remove a stale assertion only with recorded coverage replacement or proof that its feature is absent. Never restore cloud code just to make tests compile.
3. Export Room schemas. Recover historical schemas from shipped versions and construct fixtures by running real old APKs on devices/emulators, not only manually synthesized current entities.
4. Add PR CI: compile, unit tests, lint, migration tests, appropriate instrumentation tests, dependency/secret scans, wrapper validation. Gate release on success for the exact commit and fail release if signing is absent. Audit/pin build dependencies/actions; resolve actual advisories and native platform/ABI compatibility, not only upgrade version numbers.
5. Add a migration/maintenance barrier before startup backup/purge/placeholder cleanup and media collection. Route legacy paths through it; stop unsafe source deletion immediately.
6. Introduce fault injection for failed writes, preferences commits, atomic rename, SQLite operations, Keystore calls, native KDF loading, and abrupt process death. Test doubles are injected through interfaces and cannot be selected from production by a classpath/environment check.

Gate: repaired tests build; safety regressions reproduce original bugs; release cannot bypass required checks. No data format changes are necessary to establish this gate.

### Work package 2 — Stop save/lock/delete data loss using existing formats

Addresses F02, F09, F10; interim mitigation for F04/F08.

1. Make repository writes return Saved/Locked/StorageUnavailable/Conflict/Corrupt/Failure, with affected IDs/revisions. Return failure for update of a missing row. Remove success sentinels (`0L`) and silent errors. Never swallow CancellationException.
2. Keep encrypted durable draft/revision state and show saved/pending/failed status. Close/dispose must not discard edits before successful persistence or recoverable draft commit. Define crash behavior for edits not yet acknowledged saved.
3. Implement lock as a coordinated transition: prevent new edits; seal the latest draft/save with an already-authorized session key; durably commit; cancel stale writers; clear decrypted editor/undo/history/cache/media state; then discard session keys. If storage is full, retain the last durable revision and expose the failed save. Do not unlock again automatically. Screen protection applies before hiding or switching content.
4. Capture a session epoch/revision in each write; reject stale writes after lock, editor switch, restore, or migration. Test completion/cancellation around the 300ms debounce and duplicate/close interactions.
5. Distinguish vault-disabled archival from enabled-but-locked archival. Enabled vault writes must fail closed. Opening data with a corrupted envelope is an error/recovery state, not an editable placeholder.
6. Replace immediate physical attachment deletion with committed reference removal and a deferred deletion queue. Preserve references needed by drafts, undo, trash, backups, and previous generations. Stop GC if any note/media references are unreadable.
7. Duplicate attachments into verified independent storage or use deliberate reference-counted immutable sharing; copying failure cannot silently reuse a path with deletion semantics that assume exclusive ownership.
8. Take and verify a recoverable pre-operation snapshot before Empty Trash/purge, including relevant media. Snapshot failure blocks destructive work and reports the reason. User cancellation must leave data unchanged.

Gate: locking never leaves the archived editor visible or stores plaintext vault payloads; failed/cancelled saves preserve acknowledged data and dirty drafts; deleting one note cannot delete another note's media. No five-minute orphan heuristic can override unreadable references.

### Work package 3 — Safe upgrade engine and legacy cryptography readers

Addresses F01/F04; prerequisite for schema/key/media conversions.

1. Inventory actual on-device files, schema versions, database headers, preference types, old fallback databases, vault status, media formats, snapshot formats, and key aliases without changing them. Preserve unrecognized files and malformed values.
2. Make a consistent recovery checkpoint using SQLite backup facilities or coordinated closure/checkpointing. Do not copy a live .db while ignoring WAL. Capture credential metadata and media manifests with the correct source generation. Encrypt recovery material under a separately recoverable key; if that cannot be done safely, do not start destructive migration.
3. Route legacy plaintext DB, normal Keystore-wrapped DB key, static-wrapped legacy keys/media, and unwrapped vault-key records to explicit read-only compatibility adapters. Authenticate AEAD candidates and validate the corresponding SQLCipher database. Never overwrite the old alias/prefs to “try” a candidate.
4. Handle existing PIN KDF variants: PBKDF2 records, real Argon2id, and historical PBKDF2 mislabeled Argon2id. Current Argon2idKdf can also fall back to PBKDF2 for LOGIN when its native library is unavailable; new writes must fail explicitly or use a deliberately selected/labeled supported KDF. Keep legacy derivation read paths deterministic and store actual parameters in new metadata. Do not auto-upgrade credentials before the journaled conversion is ready.
5. Validate user PIN/recovery authentication before migrating vault access. Do not treat possession of a fallback-wrapped key as authorization to open the user's vault UI. If authentication is needed, mark vault conversion pending and preserve ciphertext; ordinary feature use can continue where independent and safe.
6. Stage destination records/files, preserving IDs and references. Verify every source record and field, attachment byte hash/length, counts per table/state, integrity/foreign-key checks, and reopen/decrypt after a fresh process. Counts alone are insufficient.
7. Commit the generation switch durably only after validation. Resume idempotently after a kill; retries neither duplicate data nor overwrite newer records. Check storage capacity before starting, maintain headroom, and pause without touching the source if space is insufficient.
8. Keep old source generation and required key metadata protected until a successful restart and verified encrypted recovery checkpoint. Cleanup is a separate journaled step. Existing insecure plaintext originals/snapshots should be encrypted/contained as soon as verified preservation is possible; do not retain them indefinitely as a privacy leak.
9. Quarantine legacy fallback databases for comparison/recovery; never delete them blindly on startup. Handle conflicts between recovered legacy and current records by preserving both revisions for review.

Gate: real old-APK fixtures upgrade directly and preserve every field/media byte; killing at any step yields the old or new consistent generation; unreadable/corrupt sources are retained with a recoverable error. Static-key compatibility can read old data but cannot encrypt new data.

### Work package 4 — Stable vault keys, independent domains, and versioned media

Addresses F01/F03 plus archived attachment separation.

1. Add new key registry, authenticated envelope formats, wrapper generations, and recovery metadata. Verify KDF parameters/native library behavior on every supported ABI; bound parameters from imported files.
2. Migrate DB wrapping/domain separation using work package 3. Keep already-encrypted general data encrypted; ordinary features must work if the notes domain is locked or temporarily unavailable. Do not introduce unnecessary authentication prompts for tasks/habits.
3. After successful user authentication, migrate archived note payloads from PIN-as-data-key to a random vault data key. Keep the legacy source/key accessible through protected recovery until every payload verifies.
4. Migrate media in a resumable per-file job with explicit ownership, new key IDs, and authenticated identity. Match plaintext/decrypted content hashes before switching references. Never decrypt legacy files to persistent plaintext staging. Temporary encrypted destination files must be durable before atomic replacement.
5. Change PIN/stronger passphrase by atomically updating wrappers. Keep old wrapper as protected journal recovery state until new wrapper is reopened successfully. Recovery phrase changes, enabling/disabling the vault, archive/unarchive, and KDF upgrades use the same transactional rules.
6. Implement device-key invalidation recovery from verified portable backup/recovery envelope. Preserve inaccessible material without claiming it is decryptable. Never create a new alias over an existing but unreadable alias.

Gate: rotate credentials with zero note-ciphertext rewrite after initial conversion; interrupt rotation/recovery/disable at every step; current and retained backup generations remain decryptable. No production fixed-key/raw-key fallback. All new media and archive attachments match their declared protection domain.

### Work package 5 — Self-contained encrypted snapshots and portable backups

Addresses F05/F06; prerequisite for safe destructive restore.

1. Introduce a versioned manifest containing source-instance identity, globally stable entity IDs, schema/crypto versions, protected key envelopes, attachment identities/hashes/lengths, preference types, and completeness state. Keep note contents and sensitive filenames encrypted.
2. Snapshot consistent data plus actual media bytes/recoverable immutable objects, not paths alone. Preserve keys needed by historical encrypted records without storing raw keys or PIN verifiers. Capture tables/settings needed for full recovery, with an explicit completeness list.
3. For automatic snapshots while notes are locked, back up opaque authenticated ciphertext and recovery wrappers without loading decrypted vault keys. If required authentication/material is unavailable, defer and show last successful backup, rather than marking an incomplete archive successful.
4. Convert existing plaintext snapshots into encrypted legacy containers preserving their exact contents before removing old plaintext files. Do not silently discard snapshots that lack attachment bytes or old vault keys. Mark them partial and allow safe text recovery; preserve undecryptable rows without injecting them over working current rows.
5. Historical pre-PIN-change ciphertext may be unrecoverable if its original key/salt/recovery material was overwritten. Old PIN alone is insufficient without the old salt. Search available legitimate backup metadata, report per-record recoverability, and never promise automatic repair of this case.
6. Preserve backup format readers for all shipped versions. Verify native KDF/algorithm parameters explicitly; tampered/corrupt data must be rejected. New backup writing cannot silently copy encrypted media bytes as if they were portable plaintext after a decryption failure.
7. Reopen/authenticate every created backup before reporting success. Regularly restore sample archives into isolated stores and compare full content/media. Provide a user-controlled encrypted export for device loss/uninstall; local snapshots are not that protection.
8. Define bounded retention and size accounting including referenced media. Keep a verified recovery point during destructive operations. Notify about incomplete/no-portable backups without exposing contents.

Gate: new snapshots contain no readable note content; backups restore on another device after reinstall without the original Keystore, given the documented recovery credentials; old snapshots never corrupt current vault access.

### Work package 6 — Validated import, true merge, and crash-safe full restore

Addresses F07/F08/F14 and dormant VaultSyncManager defects.

1. Before live changes, authenticate the complete backup and validate all schemas/versions, preference types, record counts, duplicates, payload lengths, nesting, entry count, actual decompressed bytes, paths, attachment hashes, and vault decryptability. Do not treat encryption with a supplied password as trust in archive contents. No mutations before authentication/validation finish.
2. Stage encrypted records/media under new immutable names. Never trust imported absolute paths; map attachment identities into app-owned paths. Validate embedded block attachment references as well as top-level media lists. No plaintext temporary restore files for notes/media.
3. Keep local numeric IDs for existing users/widgets, adding stable UUIDs and source-instance namespaces. For legacy backups without global identity, remap imported IDs and references; preserve local records on ambiguity. Repeated import must be idempotent where identity can be established, and otherwise offer explicit duplicate/conflict review rather than destructive guessing.
4. Separate Merge, Import as Copies, and Replace operations. Show counts/conflicts/missing media before commit. Keep both conflicting revisions until the user chooses; compare identity and revision, not numeric ID or timestamp alone.
5. Allowlist safe preference names/keys/types; exclude security credentials, Keystore wrappers, operational journals, licenses, and privileged state from general preference import. Handle legitimate key recovery through dedicated validated key-envelope import.
6. Enforce current vault policy during import. Require authentication for private entries, or preserve them as pending encrypted recovery material outside live editable notes. Never insert unreadable old ciphertext over current valid rows.
7. Restore every database/preferences/media generation behind the coordinator. Switch once staged generations verify, record the active pointer durably, reload managers, refresh widget configs, reschedule alarms, and clean stale caches. Resume/rollback after process death without mixed generations; retained old data stays recoverable.
8. Repair dormant serialization/import helpers to call this validated service. Keep automatic/cloud sync explicitly unavailable unless separately implemented and assessed; this task does not add a new server feature.

Gate: overlapping source/local IDs never overwrite unrelated records in merge; malformed input changes nothing; failed restoration or process death leaves a coherent generation; widgets, labels, checklists, reminders and media references survive remapping.

### Work package 7 — Notes privacy and ordinary feature correctness

Addresses F11/F12 and remaining input/lifecycle issues.

1. Apply FLAG_SECURE automatically to notes, archive/recovery/authentication windows, and relevant external editor activities; clear it only when those sensitive surfaces are gone. Hide competing overlays on sensitive surfaces where supported. Retain standard capture behavior for other screens.
2. Require an on-device speech recognizer for protected notes; on API/device combinations without one, keep manual entry available and explain voice unavailability. Remote transcription, if ever offered, requires a separately explicit privacy choice. Remove transcript/content/key logging from all builds and test crash/diagnostic exports for sensitive data.
3. Use unique opaque share/export filenames, minimum URI grants, appropriate revocation/cleanup, and a clear confidentiality boundary at user-directed export. Test delayed recipients and process death; cleanup must not break an active share or leak it indefinitely.
4. Handle clipboard/IME/autofill/accessibility exposure deliberately. Recovery phrases/PINs must not use inappropriate suggestions/autofill/logging. Android secure-window flags do not remove all third-party keyboard or accessibility access.
5. Treat existing ordinary-note widgets as an explicit external plaintext view: preserve their configuration, require a clear one-time disclosure/opt-in before rendering after the protection upgrade, and show a safe placeholder until then. Vault notes remain excluded. Do not silently delete widget configurations or expose new notes automatically.
6. Use atomic habit-counter SQL, transactional creation/deletion, appropriate uniqueness/foreign-key constraints, and idempotent notification actions. Migrate constraints without deleting duplicates/orphans: consolidate only with proven identity, retain conflicts for recovery.
7. Replace attachment-provider size trust with capped streaming and actual byte limits. Validate names/extensions/paths; unknown sizes are not unlimited. Ensure encrypted staging cleanup respects in-flight/draft ownership.
8. Remove destructive placeholder cleanup based only on user-visible habit title, or require proven seed provenance. Existing user records with those titles must survive. Review remaining startup and scheduled cleanup for the same failure pattern.

Gate: notes-specific privacy works across all entry points without forcing vault restrictions onto ordinary features; concurrent user actions retain progress and records; diagnostic/export paths obey documented protection.

## Test matrix and proof required before release

| Dimension | Required cases |
|---|---|
| Installed versions | Every shipped v1.6.0+ build directly to final build, including the 16 published APKs and recovered v1.6.x–v1.8.2 fixtures; private/intermediate remediation builds; no reinstall/clear-data |
| Stored schemas | Every shipped notes/Focus/chat/drill/vocabulary schema and historical preferences; v6 article/unknown fields preserved even if not rendered |
| Crypto history | Keystore, static legacy wrappers, raw vault key, PBKDF2, mislabeled PBKDF2, Argon2id, mixed media, disabled/enabled/locked vault, old recovery envelopes |
| User data | Unicode/large text, rich blocks, malformed JSON preserved, labels/checklists/IDs/order, archive/trash, many/shared/missing attachments, duplicate source IDs, widgets, habits/logs/reminders |
| Interruptions | Kill/restart after every journal/DB/file/credential write; device reboot; screen lock; foreground/background/editor changes; repeated retries |
| Storage/crypto failures | Insufficient headroom, full disk mid-write, short writes, failed fsync/rename/commit, SQLite corruption, WAL-only changes, temporary/permanent Keystore failure, missing native KDF/ABI |
| Recovery/import | All old backup versions, encrypted/partial snapshots, correct/incorrect credentials, old PIN changes, different-device restore, overlapping IDs, zip bombs/traversal, duplicate entries, corrupt tags/manifests/media |
| Device reality | Supported API 24–35 and newer targeted platforms as applicable, actual shipped ABIs, low-memory devices, hardware-backed and documented supported alternatives; real-device Keystore tests |

For every fixture compare every logical field and attachment plaintext byte hash before/after, plus raw preserved data for unknown fields. Assert a fresh process can reopen the destination. Record exact APK hashes, fixture schema/crypto versions, build commit and test environment. Logs contain operation/error categories and counts only, never note content, paths, PINs, phrases or keys. Passing source assertions or in-memory Robolectric tests does not establish hardware migration correctness.

## Rollout and rollback

Develop/review work packages as separate changes with their tests. A compatibility/safety release can ship once work packages 1–2 and safe non-destructive migration foundations are complete, provided it does not claim full notes protection prematurely. The final release includes all required legacy readers and can upgrade old versions directly without relying on the safety release having been installed.

Use internal synthetic fixtures first, then an opt-in beta with a verified recovery archive and non-destructive migration. Expand release cohorts gradually only after investigating every migration/restore/key failure. Every observed record/media loss, false save acknowledgement, or vault plaintext save stops rollout. Use distribution rollout controls where available; GitHub APK distribution needs explicit beta artifacts/manual promotion and cannot be assumed to support managed cohorts.

Rollback is principally **forward recovery**: publish a higher-version compatible fix that reads both generations, or restore the previous consistent generation using the journal. Do not tell users to install an older APK over a new schema or clear storage. Android downgrade behavior, signing and old code's destructive startup paths make naive binary rollback unsafe. Retain protected recovery material until the rollback/recovery window ends and validation criteria are met; cleanup itself is resumable.

If a user lacks enough storage, cannot unlock the vault, or has an unavailable key, defer only dependent conversion with an accurate explanation and retry/recovery action. Preserve original data; do not present a blank notes list as if the user has none. Display recoverable errors and last successfully saved/backed-up state. No one-time wizard should be able to discard all data by skipping setup.

## Coverage and completion checklist

| Finding | Owner work packages | Required proof |
|---|---|---|
| F01 insecure fallback | 3, 4 | Legacy reads succeed; new fallback writes impossible; real-device failure tests |
| F02 lock/save | 2, 7 | No visible/unencrypted vault content after lock, protected dirty draft recovery |
| F03 rotation/recovery | 3, 4 | Every interruption recoverable, old backups usable |
| F04 destructive migration | 1, 3 | Complete verified copy or untouched source; all supported direct upgrades |
| F05 plaintext snapshots | 5 | Encrypted/authenticated new snapshots; preserved encrypted conversion of old snapshots |
| F06 historical snapshot restore | 4, 5, 6 | Decryptability preflight, no damage from obsolete keys, honest partial recovery |
| F07 ID overwrite | 6 | Cross-device collision fixtures preserve local records and references |
| F08 partial restore | 3, 5, 6 | Journaled coherent recovery across DB/preferences/media |
| F09 destructive media handling | 2, 4, 5 | Ownership/reference/undo/backup tests and unreadable-note GC guard |
| F10 silent writes | 2 | Durable save acknowledgement, retained drafts and surfaced failures |
| F11 privacy surfaces | 7 | All entry-point capture/log/speech/export tests |
| F12 habit concurrency | 7 | Exact counts under concurrent UI/widget/notification actions |
| F13 release gates | 1, all | Exact release commit passes repaired CI and device matrix |
| F14 import limits/trust | 6, 7 | Bounded streamed input and strict preference/manifest validation |
| Dormant sync helpers | 6 | Safe service delegation; no unsafe restore entry point remains |

Completion is demonstrated preservation plus protection, not the number of closed audit items. Release requires all gates above, independent review of crypto/upgrade/restore transitions, verified fresh-process restoration, and an operational recovery procedure for users. Already-lost historical data is explicitly recorded as unrecoverable where evidence establishes that limit; it is never hidden by a successful migration status.
