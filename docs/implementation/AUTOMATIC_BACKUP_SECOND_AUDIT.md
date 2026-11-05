# Automatic backup second audit

This pass examines assumptions left after the first audit: queued WorkManager jobs across settings changes, initial charging/battery constraints, pause behavior, private-vault identity changes, malformed device-local metadata and disabled-vault legacy notes. It preserves the existing v4 archive format and v1–v3 readers.

## Reproduction evidence

Before these fixes, eight new scenarios failed on both API 35 and API 36 (16 failing executions):

- A queued forced request wrote into a different, newly selected backup setup.
- The initial automatic backup bypassed charging constraints through its forced one-time job.
- Pausing did not cancel a queued forced request/retry.
- Disabling/resetting the private vault removed the key-export gate for an older private backup key.
- Configuring a different vault/PIN let that vault export the older vault's recovery key.
- Deeply nested local configuration, retry metadata and ownership records reached recursive JSON parsing and raised StackOverflowError instead of a controlled validation failure (three scenarios).

A separate normal-use case also failed on both APIs (two executions): plaintext archived notes left after disabling vault protection prevented a complete encrypted backup. These logs are preserved in `before-fixes.log` and `plaintext-archived-before.log` in the second-audit artifact directory. They are simulated/reproduced edge cases, not evidence of data loss in a published release; this feature remains unreleased.

## Fixes

### Queued requests and pause

Every explicit request now carries its setup stream, recovery-key ID, request ID and a revocable request epoch. The worker checks the setup before foreground promotion; the runner checks it again **under the same mutex as key/folder changes** before reading keys, writing files or updating last-attempt state. A preflight-only check would leave a race while waiting for that mutex.

A new explicit request refreshes the epoch, and key installation/import refreshes it atomically with the stored key/configuration. Pause refreshes the epoch before cancelling queued immediate work. Even if Android cancellation is delayed or an already-starting worker reaches the mutex later, an old request cannot become an implicit request for the new setup. Explicit Back up now after pausing still works with a new current epoch. Malformed request metadata is replaced as nonsecret scheduling state when a new request is made; no encryption key is regenerated for this purpose.

WorkManager cancellation is awaited asynchronously with a bounded timeout, rather than blocking a thread indefinitely. The persisted epoch is the execution safeguard; cancellation alone is not the security boundary.

### Initial automatic backup constraints

Enabling schedules the first capture through the constrained periodic checker, with `nextDue` set to now when no successful backup exists. It no longer creates a forced job that ignores charging/battery preferences. The periodic job requires battery-not-low and storage-not-low, plus optional charging. Android timing remains approximate. A separate explicit Back up now remains a deliberate request that can override charging, with adequate storage required.

Tests inspect real WorkManager work information/inputs: charging constraints, absence of the old unconstrained initial job, cancelled pending immediate work after pause, and setup/epoch values carried by explicit work. These checks do not simulate physical Doze or OEM quotas.

### Private-vault identity survives reset/revocation

When private-note backup authorization is granted, the store records a domain-separated SHA-256 fingerprint of the authorizing 256-bit vault data key, associated with the portable backup key ID. The fingerprint is not a PIN hash and contains no plaintext recovery credential. Changing the PIN around the same stable vault key preserves this identity.

Key export now checks both ordinary enabled-vault authentication and the retained historical identity. Disabling, deleting or replacing the vault does not erase that identity. A new/different vault PIN cannot claim authority to export an older private backup key. Revoking unattended authorization preserves the identity while removing its wrapped vault-key copy. Configuration repair also preserves it.

Reauthorizing a different vault under the older key is refused until the user explicitly imports the saved portable credential, proving possession of that recovery key, or generates a fresh key. Ordinary stored-key repair cannot silently transfer ownership. Explicit import resets the binding and pauses setup/requires key export/confirmation again; the imported credential still decrypts its existing archives. A fresh generated key starts a new key/setup scope, and old archives/documents remain necessary for older backups.

For older local metadata that cannot prove an archive key was never private, the guard fails closed instead of guessing. An existing wrapped authorization can supply the historical fingerprint; an unreadable/ambiguous identity requires the explicit recovery-key import/new-key path. Nothing deletes or rewrites existing external archives.

This is an intentional recovery tradeoff: after deleting/replacing the original vault, the new PIN alone cannot retrieve an older private recovery key. Keep its exported recovery document. The saved key continues to restore independently of the old vault PIN and installation, as before.

### Device-local metadata bounds

The JSON nesting bound is now applied before parsing the automatic configuration, encrypted-stage metadata and ownership ledger, not just before parsing external archives. Malformed local state produces a controlled validation/repair path rather than crashing the settings coroutine or worker with StackOverflowError. Metadata quarantine remains conservative: unknown files are preserved and cryptographic credentials are not regenerated.

### Archived plaintext and locked-vault preflight

An archived flag does not always mean the note still contains a vault-encrypted payload: disabling the vault intentionally decrypts archived notes. Export now includes those ordinary plaintext records when protection is disabled/not configured. When protection is enabled, legacy plaintext archived records still require an unlocked or explicitly authorized vault, and preflight reports this accurately. Encrypted note payloads always require successful authenticated decryption; they are never omitted or copied as opaque ciphertext into a supposedly portable backup.

The shared exporter accepts an authenticated NotEncrypted result only for the appropriate legacy plaintext path. Archive verification and all existing rollback/media-inventory checks remain in place. Archive restore can still request a vault PIN for archived/private records under the existing restore policy.

## Importing a different key

Importing a different key now shows an explicit replacement warning before changing the stored credential, from either file import or typed entry. Cancel leaves the credential and settings unchanged. Same-key recovery and first-time import avoid an unnecessary replacement warning. The pending imported credential exists only in unsaved composition memory; rotation discards the confirmation rather than saving the secret in SavedState.

## Additional checks

Regression tests cover ownership preservation across revocation and damaged-configuration repair; deliberate transfer by importing a known portable key; stale requests after pause/reimport even when the folder/key ID are unchanged; real WorkManager request inputs; recovery from incorrectly typed request epochs; and locked enabled-vault preflight for legacy plaintext notes. The earlier portable restore, wrong-key, authentication-tag, truncation, provider failure, retention and key-document tests remain active.

Validation passed: 1,611 full-app regression executions across 114 suites, then 461 final security/backup/data-integrity executions across 42 suites, including 124 backup/UI executions. No failures, errors or skips. The final import-confirmation UI change is included in the latter run. Both debug and unsigned release builds passed, and the release worker name is preserved for WorkManager reflection. Final source hashes and build hashes are recorded in the accompanying `validation.json`. The implementation and previous audit reports remain available for the overall encryption/recovery design.

## Limits unchanged

Physical-device testing remains necessary for Android Keystore, actual PDF/QR output, reboot/uninstall/reinstall, OnePlus/OEM scheduling and the intended cloud/SD document providers. Provider read-back cannot guarantee later cloud durability, and no app can prevent external deletion after verification. Android may defer jobs; users must check last protected data and retain a separate recovery-key copy. No version bump, commit, push or publication is part of this pass.
