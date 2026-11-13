# Backup implementation audit — 2026-11-06

## Scope and result

Fresh review of the current manual-backup sessions, inline vault authentication,
automatic-backup authorization/revocation, staging cleanup, destination
verification and related dialogs. Review included their integration with the
existing archive engine, application lifecycle, navigation and release shrinking
rules. This is a separate adversarial review pass over the implementation, not
third-party certification or a full repeat of the earlier app-wide security audit.

**Audit snapshot: two confirmed medium-severity reliability bugs were found.**
Both could prevent a manual backup from completing. Neither reproduction changed
the saved encrypted note or demonstrated an encryption/authorization bypass.

Production source was not changed during the audit itself. Temporary test
witnesses asserted the observed faulty behavior. The subsequent corrections are
described below; the original findings and evidence are retained as history.

## Remediation — 2.0.43 (143)

- BAK-01: preparation registers process-local file ownership before writing.
  Ownership remains with the delivered ciphertext session until discard.
  Registration, discard and pruning use the same lock, so age-based orphan
  cleanup cannot delete live preparations, ready sessions or saves. Live-session
  expiry still uses monotonic time; ownership is released on cancellation,
  preparation failure, successful discard or process death. Failed file deletion releases
  ownership so ordinary orphan cleanup can retry.
- BAK-02: the password confirmation consumes entry synchronously, and the
  ViewModel ignores preparation requests while preparation, ready/picker state
  or saving is active. Repeated input cannot cancel the authorized job and
  recapture a now-locked vault key.
- Permanent regressions cover slow verification, clock changes in both
  directions, cleanup during saving, released ownership after cancellation,
  duplicate ViewModel requests and repeated UI confirmation with encrypted
  private notes. Release validation is recorded in `docs/releases/CHANGELOG.md#version-2-0-43`.

The findings below describe the pre-fix audit snapshot.

## Findings

### BAK-01 — Cleanup can delete a live, unexpired manual backup

**Severity: Medium (backup availability and session consistency).**

Sources:

- `app/src/main/java/com/focusbyrj/app/util/backup/ManualBackupFiles.kt:52`
- `app/src/main/java/com/focusbyrj/app/util/backup/ManualBackupFiles.kt:110`
- `app/src/main/java/com/focusbyrj/app/util/backup/ExportCleanupWorker.kt:13`

The live session's ten-minute lease starts after preparation/verification and uses
`SystemClock.elapsedRealtime()`. Cleanup independently deletes files using their
last filesystem modification time and the wall clock. Cleanup does not know
which files belong to active preparations, ready sessions or saves.

Two deterministic witnesses reproduced the inconsistency:

1. Model a completed output whose modification time is more than ten minutes old
   when verification finishes. `prepare()` returns a new, unexpired session.
   Calling normal `prune()` deletes its file immediately, while `expired()` still
   returns false. A save then fails before opening the destination.
2. Prepare a fresh file, then call cleanup with the wall clock advanced by eleven
   minutes while monotonic session time is unchanged. The fresh file is deleted
   even though the session has not expired.

The first case models slow verification of large archives on slower storage.
The second models a system clock correction. A scheduled cleanup or another
preparation can encounter the same live files. The UI can offer a save/retry for
a source that cleanup has already removed. Existing saved records and external
archives are not deletion targets of this code.

**Recommended correction:** coordinate cleanup with explicit ownership/leases for
live preparations, ready sessions and saves. Use monotonic expiry for live
sessions; apply filesystem-age cleanup to abandoned files only. Updating the
modification time after verification helps the slow-verification case but does
not by itself solve clock changes or cleanup during active operations.

### BAK-02 — Repeated confirmation cancels the authorized preparation

**Severity: Medium (manual-backup reliability under repeated input).**

Sources:

- `app/src/main/java/com/focusbyrj/app/ui/screens/BackupFlow.kt:139`
- `app/src/main/java/com/focusbyrj/app/ui/screens/ManualBackupViewModel.kt:37`

The password dialog receives `isExporting = false`, and its confirmation callback
unconditionally calls `manual.prepare()`. Hiding the dialog relies on a later
recomposition; the callback does not reject a second activation in the meantime.

Each `prepare()` cancels the preceding operation, captures the globally active
vault key, and locks the global vault. Two confirmations before recomposition
therefore produce this sequence:

```text
First confirmation → capture authorized key → lock vault → start preparation
Second confirmation → cancel first job → capture no key → restart preparation
Private notes exist → second preparation fails because the vault is locked
```

A native-graphics Compose witness entered the PIN and password, invoked the
button's two valid click actions within one main-thread turn, and observed
"Backup Not Created" with the locked-vault error. The encrypted database record
still matched its original value and the vault remained locked. This is a
conditional input race, not a failure on every ordinary tap; queued taps during
UI delay can reach the same unguarded callback.

**Recommended correction:** consume confirmation synchronously and accept
preparation only from an eligible entry state. Reject duplicate preparation
requests while one is running, rather than cancelling it and recapturing vault
authorization. Keep the key-scoping and relocking behavior intact. Add a
regression that expects one successful prepared archive from repeated input.

## Controls that held in this review

- Automatic archive creation does not borrow an unlocked UI vault key when
  explicit automatic-backup authorization is missing. Private-note exports fail
  as a whole instead of silently omitting those records.
- The manual session holds ciphertext and non-secret metadata at the document
  picker boundary. The temporary vault-key copy is wiped before READY; the
  existing cancellation/handoff tests also passed.
- Destination success requires size/hash equality with an authenticated,
  restore-verified source. Corrupt, truncated, appended, unwritable and
  permission-revoked outputs are rejected by the exercised paths.
- Old picker results are bound to their prepared session; stale results did not
  complete a newer backup in the existing UI regression.
- Revocation invalidates pending automatic requests/stages, preserves external
  archives, and does not make already-exported recovery credentials revocable.
- Portable recovery, retention safeguards, key wrapping/binding and archive
  bounds passed the existing backup regression suite.

These observations do not establish that every provider/device is safe or close
unrelated findings from the earlier app-wide audit.

## Validation

- **145 existing backup/UI regression checks passed**, zero failures/errors/skips.
- **Three additional audit witnesses reproduced the two findings**, zero test
  failures/errors/skips because they explicitly assert the observed bugs.
  Two exercised cleanup timing; one exercised repeated confirmation and checked
  the original encrypted note for equality afterward.
- **Optimized release build passed** (`assembleRelease`, R8 minification and
  resource shrinking enabled). Produced `app/build/outputs/apk/release/app-release-unsigned.apk`.
  R8's mapping retains the new `ManualBackupViewModel(Application)` constructor
  used by the Android ViewModel factory. This checks the reflection/shrinking
  concern statically, not by launching the APK on hardware.
- Combined build/test execution: `BUILD SUCCESSFUL in 14m 23s`.
- `git diff --check` passed. Temporary audit sources were removed from the test
  tree; production source and the existing regression tests were not edited.

Command used for the audit (with the two temporary evidence classes installed):

```sh
./gradlew :app:assembleRelease :app:testDebugUnitTest \
  --tests 'com.focusbyrj.app.util.backup.*' \
  --tests 'com.focusbyrj.app.ui.components.ManualBackupUiTest' \
  --tests 'com.focusbyrj.app.ui.components.AutomaticBackupUiTest' \
  --tests 'com.focusbyrj.app.ui.components.IndependentBackupTapEvidenceTest' \
  --no-daemon --max-workers=2
```

JVM/Robolectric evidence covers controlled file/clock faults and Android UI
state transitions. The cleanup witnesses control timestamps and the archive
creation callback to isolate lifecycle ownership; they are not cryptography
tests. The repeated-input witness uses the real private-note encryption and
backup preparation path. A release build verifies compilation/shrinking, not
execution on a physical device. Real cloud-provider behavior, hardware Keystore,
process kill during real document IO and OEM scheduling still need device tests.

The temporary witness sources, all ten JUnit XML reports, and the complete build
log are retained in this workspace at
`/workspace/.ayva-env/backup-independent-audit-evidence/`. These historical
witnesses assert the pre-fix faulty behavior and are deliberately outside the
corrected test suite. They are not expected to pass against 2.0.43. The permanent
regressions named in the remediation section assert the corrected behavior.
