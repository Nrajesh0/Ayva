# Pre-release hardening

> Subsequently included in [Release 2.0.45 (145)](../releases/2.0.45.md).
> The version and publication statements below describe the original
> implementation/validation stage before that metadata-only bump.

> Subsequent backup-flow corrections and their latest validation are recorded in
> [Simpler backup and restore flow](BACKUP_FLOW_REDESIGN_2026-11-07.md).

This implements R01–R12 from the [pre-change review](../audits/REMAINING_WORK_2026-11-07.md).
The owner confirmed that the app has no released installations, so historical
local imports and old development archive encryption versions are unsupported.
Unsupported input is rejected and preserved; there is no automatic reset or
replacement of an unreadable store. Android system cloud/device extraction is explicitly excluded because device-bound keys cannot transfer that way. Current backups, restore rollback, vault
recovery and pending drafts remain supported.

Speech provider/privacy behavior remains deferred. The direct WindowManager app
blocker is retained. This pass does not change the app version or publish code.
Only `Nrajesh0/Ayva` is configured as a remote.

| Finding | Implementation |
| --- | --- |
| R01 | Removed startup/settings deletion of habits based only on demo-like titles, and removed that repository API. |
| R02 | Only a known note removal/change can create a candidate in an encrypted retirement ledger. Cleanup uses a complete, strict reference inventory inside a Room transaction. Current process creation/editor/undo leases and pending recovery retain media. Unknown files, unsupported containers and incomplete inventories are retained regardless of age. Block-media ownership is inventoried independently of the lenient display parser. |
| R03 | Credential redaction runs for all supported labels and precedes every AppLogger Android/disk/buffer sink, including throwable text. |
| R04 | Task, habit and planned-work notifications default to private visibility with a generic public version. A searchable setting controls lock-screen details, with Android retaining final channel/user control. Existing active notifications are refreshed or cancelled when redaction is requested, and policy is reconciled at startup and after restore/rollback. |
| R05 | All five user databases use SQLCipher on devices. The vocabulary asset seeds only public lexical content into a fresh encrypted database. Chat/drill/vocabulary schemas are exported. Completed-task history uses authenticated ciphertext, with explicit portable export/import inside encrypted archives and inclusion in restore rollback. |
| R06 | Removed plaintext database importers, static-key decryption adapters, plaintext media/snapshot readers, old staging conversion and manual archive encryption versions 1–3. Current manual password archives use version 4; the manifest version is a separate value. Debug Robolectric substitutes use process-random keys; release builds cannot enable them. Reads do not regenerate missing keys. Database corruption callbacks and recovery flags explicitly prohibit automatic file deletion/reset. Media streams authenticate the complete file before exposing plaintext; encrypted output installs atomically. |
| R07 | A domain-separated, one-way verifier replaces stored recoverable PIN-derived key material. The verifier and PIN-wrapped stable vault data key also have device Keystore protection. PIN changes and mnemonic recovery retain the stable data key. An unreadable configured recovery phrase cannot silently replace its working envelope. Interrupted first-time sealing resumes only after successful PIN authentication. |
| R08 | Habit notification tags and action/open PendingIntent URIs include the full Long habit ID. Delivery, feedback and cancellation share the same identity helper. |
| R09 | A stable drill session ID and encrypted recovery journal make rewards and the summary one recoverable operation, journaling only the affected row and reward preferences rather than copying all drill history. Submission snapshots counters and question/options data before disk work; a saving dialog blocks further input and pauses the timer. Vocabulary feedback waits for maintenance admission, keeps failed writes pending and flushes before Finish. Duplicate Finish does not award twice. Cache publication follows Room acknowledgement; claims update an existing row transactionally. Failed finishes keep the drill open with Retry/Keep open, and failed loads/claims report errors. Drill-only recovery does not invalidate note editors or change vault state. |
| R10 | Lint errors block builds. Both regular workflows run lint and retain reports, with a realistic time budget. A separate API 31/34/35/36 device matrix checks native crypto, encrypted stores, non-destructive corruption handling and process-death restore recovery, then runs the existing IME/back/recreation, large-text and native-artwork UI fixtures and records startup/rendering evidence. WorkManager fixtures use its official test initializer rather than shared initialization state. |
| R11 | Blocking readiness requires usage and overlay access. Notification capability, precision and recommended battery access are represented separately. Optional battery/exact-alarm access does not turn a working basic setup into an incomplete one; onboarding offers clear continuation. Task notification taps/rescheduling open the existing reminder screen directly when overlay access is absent, behind the normal device lock. |
| R12 | Extracted storage/ownership/notification responsibilities; removed obsolete migration code; refreshed the audit guard; checked durable preference results in recovery operations and license activation; fixed locale-dependent numeric formatting; moved 91 XML labels into resources and labelled/decorated widget images appropriately. |

## Data and security boundaries

- Media reclamation intentionally favors retention. Unknown abandoned files and
  files leased in the current process can consume space until a later safe cleanup
  or explicit review. No age threshold authorizes destruction. Flash overwrites
  cannot guarantee forensic erasure.
- A portable backup recovery key can decrypt its archive, including private notes.
  Explicit unattended private-backup authorization lets the app's backup process
  decrypt those notes without another PIN entry; locking the vault screen does
  not revoke that separate authorization. Revocation preserves exported archives.
- Keystore protection depends on the device. This is not a promise of protection
  against a compromised OS or running app process. Ordinary attachment files use
  app-level Keystore encryption; vault payload metadata uses the stable vault key.
- Room schema recipes are distinct from the removed plaintext/static-key
  compatibility importers. They are retained for explicit structural evolution;
  production builders do not fall back to destructive migration.
- A development installation using removed local formats needs a current-format
  encrypted portable export from a build that can still read that installation
  before adopting this storage model. Unsupported files are retained; there is no
  automatic reset or in-place conversion. Current v4 portable archives remain
  supported; earlier archives lacking completed-task history cannot supply that
  previously unexported history.
- Existing target SDK, runtime dependency versions, overlay behavior and screen
  structure are retained. Obsolete API guards, icon-location recommendations,
  resource aliases, intentional application-lifetime contexts and discretionary
  dependency upgrades are reviewed warnings, not demonstrated functional defects.
  Do not turn these into unmeasured UI/platform changes merely to reduce a count.

## Validation

The full exploratory run executed 1,731 tests in 125 classes. Its 11 failures
were retired compatibility/deletion expectations and drill gold expectations that
omitted the existing 50-gold daily quest bonus. Compatibility fixtures now verify
rejection **and preservation**; current-format round trips remain tested. Subsequent
regression runs resolved all 11:

| Execution | Result |
| --- | --- |
| Security, backup, recovery, legacy rejection and permissions regressions | 532 tests, 51 classes, zero failures/errors/skips |
| Final submitted-result snapshot, drill UI and recovery-sidecar checks | 42 tests, zero failures/errors/skips |
| Final media inventory, URI uncertainty and privacy checks | 26 tests, zero failures/errors/skips |
| Vocabulary feedback serialization/retry and final drill UI checks | 22 tests, zero failures/errors/skips |
| Debug/release lint | Zero errors; debug 244 warnings / 57 informational, release 242 warnings / 57 informational |
| Device-test APK | Compiled successfully |
| Debug APK and optimized unsigned release APK | Both built successfully; final combined validation finished successfully |

These are a full exploratory run followed by affected reruns, not a claim that a
single fresh full-suite invocation passed. Merging results by class and case name,
with an explicit mapping from seven retired positive-compatibility/deletion cases
to their passing preservation/rejection replacements, yields **1,752 distinct
current checks passing and no unresolved failures**. The machine-readable mapping
is retained in the evidence directory's `validation-summary.json`.
Source security contracts, chat-boundary
and dictionary-integrity checks pass. Workflow YAML, changed XML and the native
verification script parse successfully; `git diff --check` passes.
Final APK inspection confirmed version **2.0.44 / 144**, the system extraction
exclusions, and reminder popup keyguard protection. Retired `_software_seed_v1`
strings are absent from both app DEX payloads. APK SHA-256 hashes and metadata are
recorded in `apk-verification.json`. No version bump, commit or push was performed.

Evidence is retained under `/workspace/artifacts/pre-release-hardening/` and
Gradle logs under `/workspace/.ayva-env/pre-release-*.log`.

Native instrumentation compiled locally. ADB reports no connected
Android devices, and the environment has no emulator/KVM installation. Device
execution and startup/rendering measurements therefore require the added CI matrix
or an Android test device; compiling instrumentation does not establish a device pass.
