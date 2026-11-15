# Backup and restore

Manual and scheduled portable archives use the same full exporter and restore
reader. Internal safety snapshots are a separate, device-bound recovery layer.
Archive encryption version 4 and JSON manifest version 2 are separate version
numbers; manifest compatibility does not enable retired encryption formats.
This guide describes current behavior; it replaces the older setup/audit diaries.

## Normal navigation

```text
Automatic backups → Choose folder → Save and verify recovery file
                  → Consent + inline vault PIN when required → Enable
Recent managed backup → Current vault PIN if configured → Saved key check
                      → Review snapshot/replacement warning → Restore
Another backup → Select archive → Recovery file/key or manual password
               → Check once → Review → Current/new vault PIN if needed → Restore
```

The normal automatic page offers Daily/Weekly, last/next backup, Pause, Back up
now and Restore. Advanced options hold custom schedules, retention, charging,
alternate key formats/import/replacement, private-note access and diagnostics.
The recovery file must be saved and read back before enablement is confirmed.
Do not ask users to disable the vault or navigate through Archive to authorize
backup/restore. A new PIN does not grant access to an older vault-bound saved key;
its original portable recovery credential is needed to reconnect it.

## Coverage and identity

| Data | Portable archive behavior |
| --- | --- |
| Notes, archive/trash, checklists, labels and formatting | Saved records plus referenced image/audio/drawing/embedded attachment bytes |
| Tasks, completed history, planning, schedules, restrictions, habits/logs | Supported saved records, graph mappings, pause state and enabled/disabled choices |
| Vocabulary | Saved/custom entries, learned/mastered/bookmarked state and identity/source mappings |
| Drill/chat | Completed results and saved transcript/card payloads; restore ends active drills and invalidates stale live sessions after durable commit |
| Assistant/profile/settings | Explicit portable allowlist, personalization, profile pins, chat update/color choices, supported widget preferences and planning state |
| Device-only authority | Excluded: Keystore keys, saved wrapped recovery/private-backup grants, vault credentials, staging/ownership authority, OS grants and launcher placement |

Unsaved editor input is separate from a saved-record archive. Missing older
optional fields have explicit compatibility behavior: missing schedule enable
flags restore paused with a warning, missing personalization preserves current
choices with a warning, and missing profile pins clear those selections on
replacement. An old archive cannot supply data it never contained.

Replacement is the UI default: backed-up data becomes the reviewed snapshot;
later edits/extra attachments are replaced after warning. Same note with three
current attachments and two in the backup ends with the backup's two references.
The reverse restores all three backed-up references. Repeating replacement does
not append notes. Import copies deliberately creates extra notes/tasks/habits/
schedules each time; it also applies backed-up settings and is not a universal
preserve-both merge or synchronization feature.

Numeric record IDs, content IDs and attachment filenames are separate identities.
Imports allocate/remap numeric IDs; package-name restrictions retain their own
identity. Chat/drill ID collisions preserve local records and report skips in
copy-import mode. Task/habit card links, plan edges, profile pins and supported
widget bindings are remapped, never rebound to unrelated coincident local IDs.
Foreign/older widget-host IDs are disconnected with a reconnect warning.

Restored media uses fresh bounded UUID destinations and remapped URI fields.
Shared references share one mapping. Originals are retained for rollback; source
backup archives are retained. Permanent local image deletion does not remove bytes
from an older portable archive, so restoring it can intentionally bring them back.
## Manual export

Settings → Data & recovery → Create manual backup:

1. If a vault is enabled, enter its PIN directly on this screen. Never ask the
   user to disable vault protection or navigate through Archive first.
2. Choose and confirm a manual backup password. Explain that it is distinct from
   the vault PIN, automatic backup recovery key and vault recovery phrase.
3. Capture one temporary vault-key copy, immediately lock the global vault, and
   prepare the existing portable encrypted backup format in app-private,
   Android-backup-excluded storage. Authenticate the archive and verify that the
   restore reader accepts its manifest/media before offering a save location.
4. Wipe the temporary key. Only ciphertext, its digest and non-secret counts stay
   in the session. The ready dialog explains that the vault is locked.
5. Open CreateDocument. Copy the verified encrypted archive, then read back the
   destination and require identical size and SHA-256 before reporting success.
   Preparation alone must not update the last verified manual export marker.
6. Remove the internal temporary copy after success or cancellation. Never delete
   other external backups as part of manual-session cleanup.

The existing encryption algorithms, archive version and passwords' exact byte
semantics are retained. No password, PIN or vault key is put in
SavedState, preferences or a new delegation store. Temporary key access is scoped
to preparation only; it does not authorize scheduled backups.

Each prepared archive has its own Android activity-result registration. An old
picker result cannot be relabeled with a newer session's ID or cause the newer
archive to be written to the older destination.

Live files have process-local ownership registered before their first write and
held until discard. Registration, deletion and cleanup serialize under the same
lock. Filesystem-age pruning only considers abandoned files, so slow verification
or wall-clock changes cannot delete an active preparation, ready session or save.
Cancellation and completion release ownership; process death drops all ownership
and leaves only encrypted orphans for cleanup. A failed deletion also releases
ownership so cleanup can retry. The ViewModel enforces the live session's
monotonic expiry independently of filesystem timestamps.

Password confirmation is consumed synchronously. Duplicate preparation requests
are ignored while a backup is active, preserving its original vault authorization
and archive instead of starting again with a locked vault.

```text
Manual backup
  Vault enabled → Inline PIN → Manual backup password
  Vault disabled ────────────→ Manual backup password
                                      ↓
                  Capture one-time key → Lock vault
                                      ↓
                  Encrypt + authenticate + restore-read verification
                                      ↓
                  Wipe key → Ciphertext-only ready session
                                      ↓
                  Save picker → Copy → Read-back comparison → Success
                                  ↘ Failure → Retry until expiry / Discard

Automatic backup
  Explicit consent + inline PIN → Keystore-wrapped backup authorization
                                      ↓
                    Vault stays locked during scheduled jobs
                                      ↓
                  Encrypt + verify → Publish + read back → Record success
  Missing/stale authorization + private notes → Fail complete backup
  Revoke → Remove authorization + invalidate requests + discard pending stages
```

| Credential | Purpose |
| --- | --- |
| Vault PIN | Unlock private notes or grant a backup authorization on this installation. |
| Manual backup password | Open the particular manual archive created with it. |
| Automatic backup recovery key | Open automatic archives made with that key, including their private notes. |
| Vault recovery phrase | Recover the vault through its existing recovery flow; it is not the backup credential. |

Neither backup credential requires the original vault PIN to read its archive.
Keep credentials separate from the encrypted archives and preserve old recovery
keys while archives made with them remain needed.

## Lifecycle and failure rules

| Event | Required outcome |
| --- | --- |
| Cancel PIN/password entry | Dismiss entry and lock the vault. No archive is published. |
| Leave/background during preparation | Cancel the session, invalidate its generation, and discard any late ciphertext result. The preparation job wipes its owned key in `finally`. |
| Background for the document picker | Allowed: only a verified encrypted file and non-secret metadata remain. |
| Rotation with completed preparation | The route ViewModel can retain ciphertext state; secret-entry dialogs restart. |
| Process death | No secret or verified-session authority is restored. A late picker result cannot confirm or save a lost session. Abandoned ciphertext is pruned on startup/periodic cleanup. |
| Cancel picker | Discard the internal encrypted copy and explain that no save was completed. |
| Picker unavailable | Keep the encrypted copy briefly for retry; do not falsely report success. |
| Destination loses permission, fills up, corrupts or truncates output | Do not report success. Keep the verified encrypted source for another location until expiry. Tell the user the destination may contain an incomplete file. |
| Cancel while copying | Cancel publication confirmation and discard the local stage. A provider may retain partial encrypted output. Existing saved application records are not changed. |
| Prepared file changed/missing, or session expires before saving starts | Refuse publication and require a new authenticated session. Expiry uses monotonic time in the live process. A copy already in progress may finish verification. |
| Provider claims success but returns different bytes | Reject the save. Provider success is not a substitute for read-back verification. |

Provider IO and Android scheduling are best effort: cancellation cannot force
every third-party provider to immediately stop a blocked syscall, and deletion
cannot guarantee forensic erasure from flash. Temporary files are encrypted from
the start. Archive generation may finish an in-flight protected stream during
cancellation; generation checks prevent a cancelled result from becoming ready,
and its private temporary output is discarded. Live records and media are not
deleted as part of this cleanup.

## Automatic private-note authorization

Automatic private-note backups require a saved, explicitly granted authorization,
even if the user happens to have unlocked Archive. The automatic path cannot
borrow that active key. Without usable authorization, an archive containing
private notes fails as a whole rather than omitting those notes.

Settings show the last verified full backup, last attempt, failure detail and
whether private-note authorization is saved. Revocation requires confirmation:

- Remove the saved wrapped vault authorization.
- Advance the authorization generation and request epoch; old queued requests
  cannot publish a pending private backup.
- Remove staged metadata and discard app-owned uncommitted encrypted files.
- Keep external archives and their ownership ledger. Their exported recovery
  keys still work; revocation cannot revoke copies already exported.
- Retry deletion of abandoned private stages with normal cleanup when possible.

These mutations serialize with running automatic backup operations. Key
replacement/import also invalidates pending stages; it must never delete old
external files or imply that a new key opens old archives. Failed wrapped-key
installation keeps the previous credentials intact.


## Keys and encryption

- Each recovery key contains 32 bytes from `java.security.SecureRandom` (256 bits). No PIN, timestamp, device ID or UUID contributes key entropy.
- Portable encoding: `RUN-BK1-<64 hexadecimal payload characters>-<8 hexadecimal checksum characters>`, with grouping for transcription.
- A domain-separated SHA-256-derived 128-bit public identifier associates setup/documents; a separate 32-bit checksum detects common transcription errors. The checksum is not authentication and does not add key strength.
- The complete canonical credential is supplied to the existing backup engine. New archives retain the existing v4 format: Argon2id BACKUP parameters (64 MiB, three passes, parallelism one), per-archive random salt/nonces and AES-256-GCM authenticated encryption. The current reader accepts v4 only. Unsupported development versions are rejected without modifying the original archive.
- Device-local recovery credentials and the optional private-vault backup authorization are AES-256-GCM wrapped with a dedicated Android Keystore key, random 96-bit nonces, 128-bit tags and versioned, purpose-bound AAD. Private-vault authorization additionally binds the current recovery key ID and authorization generation. New wrapped secrets must decrypt and match before they can replace stored credentials. Explicit import can replace a permanently invalidated wrapper; transient errors and normal reads never do so. Production has no fixed/software fallback. Hardware backing depends on the device; StrongBox is not assumed.
- Reads never generate a replacement key. A missing/unusable device key needs explicit recovery-key import. Creating a new portable key requires an explicit replacement action and warning; existing archives still need their original key.
- Device-only settings, wrapped secrets, authorization tokens, ownership ledger and staging references are deliberately excluded from portable full-backup preferences.
- Secret byte arrays are wiped where practical. Kotlin/Java strings and framework/provider memory cannot be guaranteed to erase immediately. The settings route uses the existing sensitive-screen protection, and raw keys are not saved in Compose SavedState, logged, copied to the clipboard or implicitly shared.
- Exported key documents are intentionally readable credentials. Anyone possessing an archive and its key can read ordinary and private records. A compromised unlocked app/device can also use an authorized device key. This feature is not protection against a fully compromised device.

## Recovery PDF and import

The PDF uses Android's existing PdfDocument facility and the already-installed ZXing encoder for an offline QR containing the canonical key. No new PDF/QR dependency is added. It prints the full key, identifier and recovery instructions. A bounded ASCII comment before `startxref` permits deterministic import without executing/rendering an untrusted PDF. Object/xref offsets are unchanged.

Imports are bounded to 2 MiB and validate the credential/version/checksum; JSON key documents additionally validate their identifier. PDF imports require exactly one intact embedded marker. Reprinting/rewriting a PDF can strip that marker; the printed key remains usable. The JSON key file is the alternative machine-readable export. This is a RUN backup recovery key, distinct from a private-vault recovery phrase.

An enabled private vault must be unlocked before recovery-key export; this is enforced by the backend as well as the UI. Private keys also retain the identity of the vault that authorized them, so disabling/resetting/replacing that vault does not remove protection. Rebinding requires the saved portable recovery credential or a fresh key; ordinary configuration repair cannot transfer this authority. The PIN prompt follows destination selection because entering the file picker backgrounds the app and locks its vault. A vault temporarily unlocked just for export is relocked after completion/failure. Scheduled jobs still use their authorized protected vault key without unlocking the normal vault.

Picker exports are bound to the public key identifier present when the picker opened. A stale picker cannot acknowledge or mark a different key exported. Damaged nonsecret configuration can be repaired by reusing the protected key; repair pauses scheduling and requires destination/export confirmation again. A damaged ownership ledger can be quarantined during explicit setup/import without deleting external files. Re-importing even the same key pauses scheduling, clears private-vault authorization and requires export confirmation again.

## Publication, retries and retention

- A single mutex serializes automatic jobs and configuration changes. Archive capture uses the existing assistant-state/restore maintenance barriers.
- The full archive is created in `noBackupFilesDir/automatic_backup_staging`, authenticated and checked by the full restore-compatible read-only verifier, hashed, and durably recorded as a verified encrypted retry stage. No plaintext automatic archive is staged there.
- Publication creates a unique `.incomplete` document, writes it, and streams a SHA-256/length read-back check. Rename is followed by another check and supports providers that change the URI. Providers without rename get a separately verified final copy before temporary cleanup.
- Only after publication and durable ownership bookkeeping does retention run. Destination failures preserve the verified local stage for retry. Stages expire at the next scheduled capture; a new explicit Back up now request has a new request ID and cannot reuse an earlier request’s capture. Retrying the same request can reuse its verified stage. An explicit request replaces queued/backoff work so it does not get silently ignored by WorkManager KEEP semantics. It is bound to the setup stream, key ID and revocable request epoch, checked again under the configuration mutex. Pause/key import invalidate older requests even if Android cancellation is delayed. Changing the key, folder/setup stream or private-vault authorization invalidates the stage for reuse. Future-dated stages after clock rollback are recaptured.
- Revoking private-note authorization removes the wrapped vault key and pending stage metadata, advances the authorization generation and request epoch, and discards app-owned uncommitted encrypted stages. Recovery-key replacement/import also discards invalidated private stages after the new credentials commit successfully. These changes serialize with running jobs; already committed external archives and their ownership ledger remain. Their original recovery key still opens them. Cleanup retries failed local deletions without scanning or deleting unknown external files.
- The status reports the archive's capture time, including when an older retry stage was finally published. Overdue capture schedules a catch-up check; missed intervals do not produce a burst of identical backups.
- Old local uncommitted encrypted stage files are cleaned only inside the private staging directory, after a new external copy verifies or when authorization/key changes invalidate them. Periodic cleanup also removes abandoned stages while preserving the current recorded retry stage. Failed captures preserve the current retry stage. Damaged or incorrectly typed stage/ownership metadata is quarantined; fresh exports continue, and unknown external files are kept.
- Retention uses private ledger commit order, not wall-clock order or a filename scan. It applies only to the current installation/setup stream and key. Before deletion, the newest committed archive is rechecked as a valid retained copy. If that check fails, all older archives and the local retry stage are kept, and the last-success timestamp does not advance. Duplicate ownership references cannot retire a retained document. Candidate display name, actual streamed size and SHA-256 must match the recorded file. Provider metadata size is not trusted.
- Manual backups, other setups/installations, edited files and unknown files are kept. Lowering retention takes effect only after the next verified export. Failed deletion keeps extra copies and reports a warning. At least one committed owned copy is retained.
- The ledger is bounded to 5,000 records and eight million UTF-16 characters. If cleanup remains broken indefinitely, the oldest ownership records are relinquished and their external files kept rather than preventing new backups. The UI reports this condition.
- Process death at an external-provider operation can leave `.incomplete` or untracked final documents. They are never broadly deleted by scanning a user folder. A provider acknowledging a write does not prove that cloud synchronization/off-device durability has completed.

## Android and operational limits

WorkManager runs a unique periodic 15-minute due-time checker, with battery-not-low/storage-not-low constraints and optional charging. An explicit Back up now request is separate and requires adequate storage. The first automatic backup uses these same constraints instead of an unconstrained forced job. Due times support DST changes, month ends, missed intervals, time-zone changes and significant wall-clock rollback. Calendar anchors encode a date at UTC midnight so travel does not change the selected day.

Actual execution is approximate. Doze, OEM battery restrictions, force-stop, low storage/battery, Android job/foreground-service quotas and unavailable providers/accounts can delay it. After reboot the user must unlock once. Uninstalling stops future jobs. The long-running worker uses WorkManager's dataSync foreground service and a progress notification; failure to start is reported and retried. Notification permission/channel settings can prevent health warnings from being shown, so the page also displays backup health and the last protected time.

A local external folder survives normal app uninstall, but not necessarily factory reset, device loss, SD failure or manual deletion. Cloud folders require the provider/account to remain accessible. Persisted tree permission is lost on uninstall and must be selected again for new scheduled exports. Changing a destination starts a new ownership scope and preserves files in the old folder.

Archives contain saved records, preferences and attachments supported by the existing manual exporter. Unsaved editor input and local safety snapshots are separate. Available space must accommodate the encrypted local stage and provider publication (providers without rename may temporarily need two external copies). Export, verification and restore now share bounds: 50 MiB manifest, 250 MiB per attachment, 500 MiB total attachments, 20,000 ZIP entries and 64 JSON nesting levels. Serialized JSON in structured entity fields, widget/library preferences and recognized rich-note blocks is guarded before downstream parsing too. The automatic streaming verification bound is 1,000,000,000 encrypted bytes. Unsupported exports fail visibly before publication/retention; malformed nested input is rejected before recursive JSON parsing and before live-data mutation. The complete read-only restore verifier runs before either a manual or automatic export reports success. Keep an independent off-device copy and periodically verify/restore-test important archives. No implementation can guarantee recovery without a valid archive and its matching key.

The shared last-verified-export marker advances only after a manual destination passes read-back verification or an automatic archive is published and recorded successfully. Preparing an internal encrypted stage alone does not count as a saved export. Automatic backup health continues to show the archive capture time, so publishing an older retry stage does not imply that newer records were captured.


## Restore identity, conflicts and interruption recovery

Portable manual and automatic archives use the same restore implementation.
Replacement remains the default; repeating replacement restores the snapshot
rather than appending rows. Advanced import-copies deliberately creates more
notes/tasks/habits/schedules on each confirmed import. It is not synchronization.

Imported task/habit chat cards remap their IDs to the imported records. Missing
or unreadable links are removed with a warning, never rebound to an unrelated
local ID. Merge preserves existing chat messages and drill results on ID
conflicts; skipped counts are reported. Duplicate chat/drill/log identities and
habit logs without a backed-up parent are rejected during preparation, before
mutating live stores. Saved routine chains with missing steps produce warnings;
empty chains are explicitly reported as skipped.

Before creating restored attachment files, the encrypted recovery journal records
all newly allocated destinations and temporary paths in one durable update.
Rollback/startup recovery removes only those proven operation-owned files.
Committed recovery keeps destination files. Existing or ambiguous historical
files are not swept by age or filename. The marker still decides commit versus
rollback; an unreadable journal fails closed rather than guessing what to delete.

| Interruption point | Reopen/retry behavior |
| --- | --- |
| Checking/review, before writes | The unfinished session is discarded. Choose the archive again; it is not resumed automatically. A persisted checking outcome gives a restart notice. |
| Applying, before durable commit | Startup recovers prior databases/settings and removes new operation-owned attachment files. A recovery notice explains that the backup can be selected again. |
| After durable commit, before success is seen | Startup keeps restored records/files and reports completion. The notice warns against repeating import-copies. |
| Recovery cannot finish | The journal is retained; further ordinary writes remain gated. The error asks the user to keep the backup and restart/resolve storage problems. |
| Retry replacement after recovery | The selected snapshot is applied normally, without appending duplicate note rows. |
| Explicitly repeat import-copies | More copies are intentional; the confirmation warns about this. Chat/drill conflicts preserve local rows and produce skip notices. |

A small atomic status report in app-private, Android-backup-excluded storage
contains only outcomes/counts/fixed warning text, never passwords, PINs, keys or
record content. Completion/recovery notices survive process death until
acknowledged. A damaged cosmetic report cannot block app startup or bypass the
encrypted recovery journal. Foreground failures distinguish completed cleanup,
recovered previous data and pending recovery; detailed errors remain available.
Local safety snapshots continue to hold records/media references, not attachment
bytes. They are not a substitute for a verified portable archive.

If startup recovery cannot finish, application-manager initialization is deferred.
Main opens a secure recovery gate instead of normal data screens. Retry reuses the
retained journal; Close leaves it intact. The normal activity initializes only
after recovery succeeds. A corrupted status report alone does not trigger this
journal recovery gate.

Developer note: the legacy `restoreEncryptedBackup` convenience API retains its
historical `cleanRestore=false` default. New internal callers/tests must select
this argument explicitly. The user-facing ViewModel uses `applyPreparedRestore`
with its explicit replacement/import-copies selection; its default is replacement.

Journal creation and read-back verification also run inside recovery handling.
Failure there either removes the abandoned journal or keeps ordinary writes
blocked until recovery succeeds; it cannot silently leave a journal that later
rolls back newer edits. Explicit cancellation at review clears the checking
notice, so a cancelled operation is not reported as an interrupted restore.



## Internal snapshots, progress and verification

`DataSafetyManager` keeps seven rolling daily device-key-encrypted snapshots and
bounded pre-operation recovery points. They contain records/media references,
not attachment bytes; they cannot recover deleted media or a lost device key.
Destructive trash cleanup requires its pre-operation recovery point. Ordinary
Android cloud/device extraction is excluded because device-bound keys do not
transfer through it; use verified portable archives for complete recovery.

Progress uses real operation names and attachment counts, not invented completion
times/percentages. Password derivation, complete authentication, durable writes,
destination read-back and safe retention remain mandatory even for a small archive.
Reuse of one verified preparation avoids another KDF/read/staging pass during
PIN/setup retry. Local staged ciphertext can be verified/moved, or digest-checked
copied across volumes, without redundant decrypt/encrypt passes. No phone timing
or speedup factor is inferred from JVM test duration.

Relevant regressions live in backup, restore, media, data-preservation and UI
suites under `app/src/test`, with kill/restart fixtures under `app/src/androidTest`.
Test actual document providers, low storage, permission loss, process kill on
both sides of commit, reboot, recovery PDF/QR rendering and real Keystore behavior.
Provider read-back proves bytes visible through Android, not off-device cloud
upload completion. See [security](../../security/README.md) and the
[build guide](../../development/BUILD_AND_TEST.md) for verification boundaries.
