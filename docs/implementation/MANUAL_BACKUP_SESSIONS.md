# Manual backup sessions and private-note authorization

## User flow

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

The existing encryption algorithms, archive version, passwords' exact byte
semantics and restore logic are retained. No password, PIN or vault key is put in
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

## Automatic backups

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

## Validation

Regression coverage lives in `ManualBackupFilesTest`, `ManualBackupIntegrationTest`,
`ManualBackupUiTest`, `AutomaticBackupsTest`, `PortableAutomaticBackupTest` and
`AutomaticBackupUiTest`, with account navigation and existing vault/restore safety
tests also run.

Executed on 2026-11-06:

| Check | Result |
| --- | --- |
| Debug APK build, broad backup/security regression suite | Passed: 173 tests, zero failures/errors/skips across 15 classes. Includes portable recovery after local credentials/data are removed, authorization/revocation, retention, bounded archives, provider publication and screen-capture protection. |
| Android lint in the broad validation run | Passed: zero errors, 365 warnings and 56 informational findings. Warning count is unchanged from the earlier lint remediation. |
| Final picker-registration change | Debug APK rebuilt successfully; all 20 manual-backup file, private-data integration and UI tests passed again with zero failures/errors/skips. The UI check starts a newer backup, delivers an older picker result, and confirms it cannot complete or interrupt the newer session. |
| Small-screen UI | Native Robolectric captures reviewed for password entry and successful save at 320 × 480 dp with 1.6× text, plus the ready dialog at the normal test size. Controls remain reachable; lengthy dialog content scrolls. |
| Patch formatting | `git diff --check` passed. |

The focused follow-up can be reproduced with:

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest \
  --tests 'com.focusbyrj.app.ui.components.ManualBackupUiTest' \
  --tests 'com.focusbyrj.app.util.backup.ManualBackupFilesTest' \
  --tests 'com.focusbyrj.app.util.backup.ManualBackupIntegrationTest' \
  --no-daemon --max-workers=2
```

These are JVM/Robolectric checks, not physical-device certification. Before
release, exercise real document providers (including delayed cloud sync and
revoked permissions), process kill while picking/copying, reboot, hardware
Keystore behavior, low storage, and large attachments on supported devices.
Provider read-back verifies the bytes visible to Android; it cannot prove a
provider has finished uploading them off-device. The separate automatic-backup
guide lists PDF/QR rendering and OEM scheduling checks as well.

For the 2.0.43 audit corrections, all 24 focused manual-backup checks passed.
The full 1,663-case run had one pre-test WorkManager setup timeout; its unchanged
two-case reminder class passed in isolation. Debug/release assembly, APK version
and universal-architecture checks passed for 2.0.43/code 143. Lint retained zero
errors and 365 warnings. See [2.0.43 release validation](../releases/2.0.43.md) for
the complete outcome and device-only limitations.
