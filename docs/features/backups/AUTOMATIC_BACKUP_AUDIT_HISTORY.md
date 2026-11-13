# Automatic backup audit history

These are historical audit records, consolidated to preserve their distinct
findings and verification evidence. Statements about unreleased versions, legacy
readers, local artifacts or pending work describe those original checkpoints.
They are not current implementation instructions or current test results.

For the present flow, read [automatic full backups](AUTOMATIC_FULL_BACKUPS.md),
[backup flow redesign](BACKUP_FLOW_REDESIGN_2026-11-07.md),
[live progress/media reads](MEDIA_BACKUP_PROGRESS_2026-11-13.md) and
[archive/PIN options](../notes/ARCHIVE_FLOW_2026-11-13.md). In the current implementation,
unsupported development backup versions are rejected; historical mentions of
v1–v3 reader support below do not override that policy.

## First audit

### Automatic backup production audit

This audit covers the newly added full-backup scheduler, portable recovery credential, key export/import UI, Android Keystore wrapping, staging/publication, retention and the shared full-backup exporter/verifier/restorer. It also includes the requested immediate export popup after creating or replacing a key. It does not certify hardware/OEM/cloud behavior without physical-device testing.

#### Findings reproduced before the fixes

The first adversarial run reproduced seven distinct failure scenarios on both Android 15 and Android 16 Robolectric configurations: **14 failing executions out of 52**. A later direct export test reproduced an eighth issue on API 36: an enabled, locked private vault did not block exporting its backup recovery key. The earlier ordinary regression suite did not cover these injected failures. `before-fixes.log` and `reproduced-failures.json` in the audit artifacts preserve that evidence.

| Finding | Trigger and previous behavior | Fix |
| --- | --- | --- |
| Locked-vault recovery key export bypass | With private-note backup authorization saved, direct key export could write a usable recovery credential even while the private vault was locked. | Export now checks the enabled vault’s active authorization in the backend before opening the key document. After the file picker returns, the UI requests the existing vault PIN and relocks the vault after success/failure/cancellation. Unattended backups still use the separately authorized protected vault key and remain functional while locked. |
| Restore-incompatible archive accepted | Persisted settings that the restore validator rejects could still be encrypted, reported successful and published. Export also lacked the restore reader's attachment/manifest/entry limits. | Export and restore share limits; source manifests are validated before writing; the full read-only restore-compatible verifier must accept an export before success/publication. This applies to manual and automatic exports using the shared manager. |
| Missing newest copy did not stop retention | Remove the newest committed file before pruning. The old routine could delete the older recoverable copies because it trusted ledger order alone. | Recheck the newest committed file's identity, actual length and SHA-256. On failure, delete no older copies, preserve the verified local retry stage, report failure and do not advance last-success time. External changes after checks remain outside the app's control. |
| Stale retry substituted for a current capture | A verified encrypted stage from an earlier schedule could be published much later, including after a new explicit backup request. | Expire retry stages at the next capture interval. Each explicit request has a fresh UUID stored in WorkManager input and stage metadata; a new request recaptures, while a retry of that same request may reuse its authenticated stage. Replace queued/backoff immediate work so an explicit request is not silently ignored. |
| Broken retry metadata stopped all exports | Invalid stage JSON threw before a new archive could be created; repeated jobs stayed stuck. | Quarantine malformed stage metadata, retain unknown local stages until fresh external success, and capture a fresh archive. No external file scan/deletion is involved. |
| Broken ownership metadata blocked protection | Invalid ownership JSON prevented publication bookkeeping from completing and could repeatedly leave untracked external copies without a success record. | Quarantine the damaged ledger, relinquish trust in its old ownership claims, preserve unknown external files and continue with newly verified records. Surface the existing unmanaged-file warning. |
| Wrapped replacement accepted without decryption check | Injected corruption in a freshly wrapped recovery key was committed over the previous local credential. Later export/decryption would fail. | Decrypt and compare every newly wrapped recovery credential or private-vault authorization before committing it. A failed check leaves the prior stored credential/configuration unchanged. |
| Malformed key-document errors revealed key text | A malformed JSON key document caused JSONTokener's exception to include the complete input credential. UI/error callers could expose that text. | Replace JSON parsing errors with a fixed message without retaining the original exception cause or input. Regression checks inspect the entire resulting stack trace for the credential. |

These are fault-injection findings, not evidence that a released user's data was lost or that the previous OnePlus crash had these causes. The automatic backup implementation remains unreleased.

#### Further hardening and regression coverage

- Incorrect SharedPreferences value types are handled during bookkeeping quarantine and explicit key import, rather than throwing again while trying to save the quarantined value.
- Duplicate ownership references are deduplicated by document URI, preserving the newest claim, so a retained document cannot also become an older deletion candidate.
- A null provider query is treated as unavailable information, not proof that an archive was deleted. Missing access preserves ownership and files.
- Transient zero-length stream reads make bounded progress instead of spinning indefinitely in hash/key-document verification.
- Decrypted JSON is checked for a maximum of 64 structural nesting levels before recursive JSONObject parsing. The guard also covers serialized JSON in entity fields, exam-widget/library preferences and recognized rich-note block markers, since those strings get parsed again by downstream readers. Brackets and escaped quotes inside note strings are handled correctly. Parsing errors avoid echoing decrypted manifest content.
- Export/read limits share one policy: 50 MiB manifest, 250 MiB per attachment, 500 MiB total attachment bytes and 20,000 ZIP entries. Automatic encrypted-file hashing is bounded to 1,000,000,000 bytes. Oversized or incompatible archives fail visibly rather than retiring older copies.
- Private-vault wrapping AAD binds the recovery key ID and authorization generation as well as purpose. Reintroducing an old wrapped authorization under a revoked/new generation fails authentication. Normal reads never regenerate the wrapping key. Only explicit import/creation can replace a permanently invalidated wrapper; transient errors fail closed.
- External filenames use a full UUID suffix. Cleanup stays inside the app-private staging directory and happens only after verified external protection; failed captures leave previous local stages available.
- Success is recorded after retention's newest-copy validation. A provider returning changed data between publication and retention keeps the local retry stage and old owned copy, with last-success time unchanged.
- Cancellation propagates, clears the running indicator and preserves old archives. A partially created new local stage is removed without deleting old owned files.
- Failed key-document read-back clears temporary secret arrays and cannot acknowledge export or enable the schedule.

#### Cryptographic and recovery checks

The portable credential still contains 256 bits from SecureRandom. Its public identifier is a domain-separated 128-bit SHA-256 prefix; the independent 32-bit transcription checksum is not an authentication mechanism. No PIN, timestamp, UUID, device identifier or fixed software seed derives the recovery credential. Production backup wrapping has no software fallback.

The existing archive format remains v4: Argon2id BACKUP parameters (64 MiB, three passes, parallelism one), random per-archive salt/nonces, AES-256-GCM and authenticated header metadata. Legacy v1–v3 reads remain supported. Automatic preferences, wrapped secrets, staging/ownership records and authorization tokens stay outside the portable preference allowlist.

Authenticated stream draining remains mandatory after ZIP EOF, so a valid ZIP prefix cannot bypass GCM authentication. Tests corrupt the magic/header verifier/payload tag, truncate an archive, append bytes, and use the wrong recovery key. Verification and clean restore must fail while the original saved note and encrypted attachment bytes remain unchanged; interrupted-restore recovery must not be left pending.

The full portable recovery test includes ordinary notes, tasks, encrypted media and locked private-vault notes. It independently decrypts the ZIP using only the exported recovery credential and confirms the attachment entry contains the original plaintext bytes rather than device-bound media ciphertext. It then removes local automatic credentials and original records/vault settings, and restores with the exported key and a new vault PIN. A wrong/stale private-vault authorization must fail a subsequent backup without replacing the healthy archive. Corrupting a live attachment's authentication tag must likewise preserve the prior healthy backup.

Authenticated archives containing 10,000 nested outer arrays, or deeply nested serialized widget/task/note metadata inside otherwise shallow manifests, are rejected before any live-data mutation. Normal quoted note text is preserved. Tests also cover revocation, stale picker IDs, explicit replacement guards, DST/month ends/time-zone/clock changes, rename URI changes, unsupported rename, failed writes, failed deletion, ownership limits and document-provider operations.

#### Key creation/export UI

After either initial key creation or confirmed replacement, Save your recovery key opens immediately. Export PDF is the primary action, Key file is the alternative, and Later remains available. Cancelling the external file picker keeps the prompt; failures are shown in the prompt for retry. Busy export actions cannot be dismissed/repeated. Only the public key ID is saved across activity recreation; the credential is not stored in SavedState. The existing expected-key-ID check binds export to the key that opened the picker. The schedule still requires verified export and a separate user acknowledgment. When the private vault is enabled, exporting the recovery credential requires unlocking it. Authentication happens after returning from the external picker because MainActivity locks the vault when the app backgrounds. The backend enforces this too; UI controls alone are not the security boundary. Temporary export authentication is always relocked, and only the public destination/key identity is saved across recreation.

#### Validation and remaining limits

The focused post-fix suite passes **92 executions across four suites**, with no failures/errors/skips. Adding the private-export guard and picker-return PIN test passed 95 focused executions. The full app regression then passed 1,585 executions with no failures/errors/skips, including a real-PIN authenticated export test. Final embedded-JSON/probe hardening passed 434 backup, security, data-integrity, planning-archive and legacy-upgrade executions across 41 suites, including 97 backup/UI executions, with no failures/errors/skips. Final debug/release build results and source hashes are recorded in `audit/final-validation.json`. The source implementation document describes staging, retention, recovery and platform limits in detail.

Robolectric exercises API 35/36 state/crypto/storage paths and API 34 Compose UI with native graphics. PDF marker/xref tests use a valid PDF container fixture; they do not execute Android's real PdfDocument writer. These checks do not replace physical-device tests of hardware Keystore, actual PDF/QR export/import, reboot/uninstall/reinstall, OnePlus battery policy, Android foreground-job quotas, or intended SD/cloud document providers.

Android execution times are approximate; force-stop, low battery/storage, quotas and provider/account availability can delay jobs. Full read-only verification requires temporary encrypted verification space as well as the local encrypted archive. A cloud provider's successful read-back does not prove completed off-device synchronization. No retention implementation can prevent later external deletion or provider/device failure. Normal external-folder files survive app uninstall; local folders do not protect against losing/resetting the phone. The exported recovery key is intentionally a readable credential and belongs somewhere separate from the archives. No code can recover an archive without its matching key or recover changes never captured by a completed backup.

Version remains 2.0.39 (139); no commit, push or release is part of this audit.


## Second audit

### Automatic backup second audit

This pass examines assumptions left after the first audit: queued WorkManager jobs across settings changes, initial charging/battery constraints, pause behavior, private-vault identity changes, malformed device-local metadata and disabled-vault legacy notes. It preserves the existing v4 archive format and v1–v3 readers.

#### Reproduction evidence

Before these fixes, eight new scenarios failed on both API 35 and API 36 (16 failing executions):

- A queued forced request wrote into a different, newly selected backup setup.
- The initial automatic backup bypassed charging constraints through its forced one-time job.
- Pausing did not cancel a queued forced request/retry.
- Disabling/resetting the private vault removed the key-export gate for an older private backup key.
- Configuring a different vault/PIN let that vault export the older vault's recovery key.
- Deeply nested local configuration, retry metadata and ownership records reached recursive JSON parsing and raised StackOverflowError instead of a controlled validation failure (three scenarios).

A separate normal-use case also failed on both APIs (two executions): plaintext archived notes left after disabling vault protection prevented a complete encrypted backup. These logs are preserved in `before-fixes.log` and `plaintext-archived-before.log` in the second-audit artifact directory. They are simulated/reproduced edge cases, not evidence of data loss in a published release; this feature remains unreleased.

#### Fixes

##### Queued requests and pause

Every explicit request now carries its setup stream, recovery-key ID, request ID and a revocable request epoch. The worker checks the setup before foreground promotion; the runner checks it again **under the same mutex as key/folder changes** before reading keys, writing files or updating last-attempt state. A preflight-only check would leave a race while waiting for that mutex.

A new explicit request refreshes the epoch, and key installation/import refreshes it atomically with the stored key/configuration. Pause refreshes the epoch before cancelling queued immediate work. Even if Android cancellation is delayed or an already-starting worker reaches the mutex later, an old request cannot become an implicit request for the new setup. Explicit Back up now after pausing still works with a new current epoch. Malformed request metadata is replaced as nonsecret scheduling state when a new request is made; no encryption key is regenerated for this purpose.

WorkManager cancellation is awaited asynchronously with a bounded timeout, rather than blocking a thread indefinitely. The persisted epoch is the execution safeguard; cancellation alone is not the security boundary.

##### Initial automatic backup constraints

Enabling schedules the first capture through the constrained periodic checker, with `nextDue` set to now when no successful backup exists. It no longer creates a forced job that ignores charging/battery preferences. The periodic job requires battery-not-low and storage-not-low, plus optional charging. Android timing remains approximate. A separate explicit Back up now remains a deliberate request that can override charging, with adequate storage required.

Tests inspect real WorkManager work information/inputs: charging constraints, absence of the old unconstrained initial job, cancelled pending immediate work after pause, and setup/epoch values carried by explicit work. These checks do not simulate physical Doze or OEM quotas.

##### Private-vault identity survives reset/revocation

When private-note backup authorization is granted, the store records a domain-separated SHA-256 fingerprint of the authorizing 256-bit vault data key, associated with the portable backup key ID. The fingerprint is not a PIN hash and contains no plaintext recovery credential. Changing the PIN around the same stable vault key preserves this identity.

Key export now checks both ordinary enabled-vault authentication and the retained historical identity. Disabling, deleting or replacing the vault does not erase that identity. A new/different vault PIN cannot claim authority to export an older private backup key. Revoking unattended authorization preserves the identity while removing its wrapped vault-key copy. Configuration repair also preserves it.

Reauthorizing a different vault under the older key is refused until the user explicitly imports the saved portable credential, proving possession of that recovery key, or generates a fresh key. Ordinary stored-key repair cannot silently transfer ownership. Explicit import resets the binding and pauses setup/requires key export/confirmation again; the imported credential still decrypts its existing archives. A fresh generated key starts a new key/setup scope, and old archives/documents remain necessary for older backups.

For older local metadata that cannot prove an archive key was never private, the guard fails closed instead of guessing. An existing wrapped authorization can supply the historical fingerprint; an unreadable/ambiguous identity requires the explicit recovery-key import/new-key path. Nothing deletes or rewrites existing external archives.

This is an intentional recovery tradeoff: after deleting/replacing the original vault, the new PIN alone cannot retrieve an older private recovery key. Keep its exported recovery document. The saved key continues to restore independently of the old vault PIN and installation, as before.

##### Device-local metadata bounds

The JSON nesting bound is now applied before parsing the automatic configuration, encrypted-stage metadata and ownership ledger, not just before parsing external archives. Malformed local state produces a controlled validation/repair path rather than crashing the settings coroutine or worker with StackOverflowError. Metadata quarantine remains conservative: unknown files are preserved and cryptographic credentials are not regenerated.

##### Archived plaintext and locked-vault preflight

An archived flag does not always mean the note still contains a vault-encrypted payload: disabling the vault intentionally decrypts archived notes. Export now includes those ordinary plaintext records when protection is disabled/not configured. When protection is enabled, legacy plaintext archived records still require an unlocked or explicitly authorized vault, and preflight reports this accurately. Encrypted note payloads always require successful authenticated decryption; they are never omitted or copied as opaque ciphertext into a supposedly portable backup.

The shared exporter accepts an authenticated NotEncrypted result only for the appropriate legacy plaintext path. Archive verification and all existing rollback/media-inventory checks remain in place. Archive restore can still request a vault PIN for archived/private records under the existing restore policy.

#### Importing a different key

Importing a different key now shows an explicit replacement warning before changing the stored credential, from either file import or typed entry. Cancel leaves the credential and settings unchanged. Same-key recovery and first-time import avoid an unnecessary replacement warning. The pending imported credential exists only in unsaved composition memory; rotation discards the confirmation rather than saving the secret in SavedState.

#### Additional checks

Regression tests cover ownership preservation across revocation and damaged-configuration repair; deliberate transfer by importing a known portable key; stale requests after pause/reimport even when the folder/key ID are unchanged; real WorkManager request inputs; recovery from incorrectly typed request epochs; and locked enabled-vault preflight for legacy plaintext notes. The earlier portable restore, wrong-key, authentication-tag, truncation, provider failure, retention and key-document tests remain active.

Validation passed: 1,611 full-app regression executions across 114 suites, then 461 final security/backup/data-integrity executions across 42 suites, including 124 backup/UI executions. No failures, errors or skips. The final import-confirmation UI change is included in the latter run. Both debug and unsigned release builds passed, and the release worker name is preserved for WorkManager reflection. Final source hashes and build hashes are recorded in the accompanying `validation.json`. The implementation and previous audit reports remain available for the overall encryption/recovery design.

#### Limits unchanged

Physical-device testing remains necessary for Android Keystore, actual PDF/QR output, reboot/uninstall/reinstall, OnePlus/OEM scheduling and the intended cloud/SD document providers. Provider read-back cannot guarantee later cloud durability, and no app can prevent external deletion after verification. Android may defer jobs; users must check last protected data and retain a separate recovery-key copy. No version bump, commit, push or publication is part of this pass.
