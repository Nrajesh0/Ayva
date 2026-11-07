# Simpler backup and restore flow

> Subsequently included in [Release 2.0.45 (145)](../releases/2.0.45.md).
> Validation below records the implementation before the metadata-only bump.

This follows the user's report that automatic-backup setup was confusing,
a vault-related restore error forced them to disable protection, and restoring
onto existing data produced duplicate records. These changes build on the earlier
[pre-release hardening](PRE_RELEASE_HARDENING.md); they do not change the version
or publish code.

## Normal navigation

```text
Automatic backups
  → Choose folder
  → Save recovery file
      → If a vault exists: consent + one PIN confirmation
  → Turn on automatic backups
```

Keys are generated as part of setup. Saving and reading back the recovery file
remains mandatory. Turning backups on explicitly confirms that step. The normal
page shows last/next backup, pause, Back up now, Restore backup, and Daily/Weekly.
Custom schedules, retention, charging, alternate key formats/import/replacement,
private-note access and diagnostic detail are under Advanced options.

```text
Recent backup on this phone
  → Confirm current vault PIN (when configured)
  → Check with saved recovery key
  → Review date, counts and replacement warning
  → Apply snapshot → Show completion

Another backup file
  → Select file → Choose recovery file or enter manual password
  → Check archive (once) → Review snapshot
  → Confirm current vault PIN, or create a PIN for a new vault
  → Apply snapshot → Show completion
```

Recent backups are shown by date/size, with the latest marked, rather than by
opaque filenames. Selecting a managed backup checks it directly with the current
key. The current recovery key can be used without showing or typing it. For a configured
vault, its PIN is confirmed first and a session-only capability is reused through
review and apply, so the normal saved-key path needs one PIN prompt. If that key
cannot open an older/different backup, retry directs the user to its original
recovery file. Missing/unavailable source files instead offer file selection
again; they are not reported as a wrong recovery key. If a prior vault reset
changed key binding, setup offers Reconnect recovery file. Binding changes still
require the original recovery credential, never just a new vault PIN. A vault PIN is entered on the restore page; disabling the vault or
navigating to Archive is unnecessary.

## Correctness and data handling

- **Snapshot is the normal restore mode.** It uses replacement semantics after
  an explicit review confirmation, so restoring onto existing data or repeating
  the same restore does not append fresh copies. The review warns that changes
  made after the backup will be replaced.
- **Import as extra copies is deliberate and separate.** It is under Other
  restore options, states that it adds records and applies backed-up settings,
  and has its own confirmation. No title/content heuristic deletes authored rows.
- Existing duplicates are not erased automatically. Selecting the original
  pre-duplication backup in snapshot mode replaces them with that snapshot's
  records. Preserve any valuable later changes before doing that.
- Device-saved key access enforces the same vault-identity binding as key export.
  A new vault PIN or disabling the vault cannot expose older private backups.
  Supplying the original external recovery credential is an independent valid
  capability. It is required when the saved key belongs to a prior vault.
- Existing vault protection must be authenticated before a restore can replace
  protected data, even if the incoming backup has no private notes. Authentication
  happens before writing recovery snapshots. A copied, one-use vault capability
  is captured atomically with its credential state, used only for this restore
  while the global vault remains locked, then wiped. Cancelled jobs cannot publish
  into a newer restore session.
- Vault settings are checked again inside the maintenance operation. A change
  during preparation/recovery fails closed rather than encrypting against a stale
  key. New-vault setup remains inside the recovery journal.
- The archive is fully authenticated, bounded, parsed and checked before a review
  or write. Manifest data remains in memory; temporary media is encrypted with
  the device media key. Preparation expires, is discarded when backgrounded during
  review/PIN entry, and never enters SavedState. Cancelling an active write waits
  for its rollback before deleting its owned staging.
- The full durable recovery journal remains the rollback authority across all
  databases, preferences and assistant state, including process death. The local
  notes/tasks safety snapshot is retained as a separate recovery tool.
- A strict media inventory is shared by cleanup and backup. A malformed unrelated
  display block cannot hide a later attachment. Restore rewrites only media URI
  fields in block metadata, retaining surrounding text, extra fields and original
  formatting data instead of reserializing a partially parsed display model.
- Source backup files and existing attachments are kept. Restore media receives
  fresh destinations, so failed replacement cannot overwrite the original bytes.

## Safe performance changes

| Work | Change |
| --- | --- |
| PIN/setup retry | Reuses the verified preparation; no second archive read, password derivation, decompression or media staging. |
| Restore rollback | Removes the redundant in-memory capture and rollback of every store. The durable, authenticated journal already provides broader recovery and remains mandatory. |
| Restored attachments | Re-authenticates staged ciphertext and moves it to a durable installation temporary file. Cross-volume fallback copies ciphertext and checks its digest. It no longer decrypts and encrypts the same local ciphertext again under the same key. |
| Progress | Shows queued work and real preparation/save/verification/finishing phases. Successful status is recorded only after the saved archive verifies. |

Password KDF parameters, authenticated encryption, archive/content hashes,
destination read-back, retention checks and durable commits are retained. Large
attachments and slow document/cloud providers still take time. No phone timing
or percentage speed-up is claimed from JVM test timings.

## Validation

The final-source backup/recovery/security/UI run passed **313 checks in 45
classes, with zero failures, errors or skips**. These were selected regression
methods plus the complete restore-safety and automatic-backup UI classes; this
is targeted coverage, not a fresh entire-project suite. Earlier broader
multi-SDK coverage passed 385 checks before the final saved-key authorization
change. The final restore-safety class passes 22 checks on API 31/34, including
the new identity-boundary cases; the UI class passes 14 checks on API 34.

Coverage includes repeated snapshot restore on existing notes/tasks/habits/logs;
current-vault PIN and new-vault setup without rereading the archive; authentication
before recovery writes; locked global vault during apply; rollback after a late
failure; staged-media tampering; malformed unrelated text formatting with a later
valid attachment; source-file absence; background cancellation; confirmation;
recovery-key replacement/revocation; small-screen/large-text layout; current-page
key availability; recent-backup selection and missing-key fallback; one-PIN
saved-key restoration while the global vault remains locked; rejection after
vault replacement or disabling; and explicit external-key restoration into a
different authenticated vault.

The final immutable-source Gradle pipeline completed successfully, including
debug/release lint, debug and release APK assembly, and instrumentation APK
assembly. Debug lint reports zero errors, 244 warnings and 57 informational
items; release lint reports zero errors, 242 warnings and 57 informational
items. Passing lint does not mean those existing warnings have been removed.
Source security contracts, chat boundary, dictionary integrity, shell syntax
and whitespace checks pass. Recorded source hashes match the inputs validated
by this final run. The APK version remains **2.0.44 (144)**; no version bump,
commit or push was performed for this follow-up.
Reports/screenshots, APK hashes/metadata, validated source hashes and
`test-summary.json` are retained in
`/workspace/artifacts/backup-flow-redesign/`; logs are under
`/workspace/.ayva-env/backup-flow-*.log`.

The native matrix now also runs snapshot replacement twice with an enabled vault
and media. Its instrumentation APK compiles successfully. Local device execution
remains unavailable without a connected Android device/emulator; compiling that
fixture is not a device pass.
