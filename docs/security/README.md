# Data protection and security contracts

These are maintenance invariants for the current implementation, not a security
score or certification. Preserve user data when storage, credentials, native
libraries or recovery are unavailable. The latest unresolved issues are in
[open findings](OPEN_FINDINGS.md).

## Protection layers

| Layer | Current contract |
| --- | --- |
| Five user databases | SQLCipher on devices: Notes, Focus/tasks/habits/rules, Chat, Drill and Vocabulary; random passphrase wrapped by Android Keystore |
| Private note payloads | PIN-authenticated stable vault data key, protected wrapping and device-bound authentication metadata; sealed write-only archiving is a separate capability |
| Attachments and previews | Authenticated device-key encryption, bounded reads and atomic installation; no plaintext disk thumbnail/staging cache |
| Portable backups | Password/recovery-key v4 authenticated archives; exact credentials and separately authorized private-note export |
| Local recovery | Encrypted authenticated journal and commit marker, maintenance admission and operation-owned cleanup |
| Android extraction | Cloud/device extraction excluded because device-bound keys cannot transfer through it |

Reads cannot create replacement keys for an existing encrypted store. Missing
keys, failed decryptions, corruption and unsupported inputs must preserve originals
and fail visibly; never reset/delete a database or silently adopt plaintext/static
fallback keys. Production Argon2id failure is an error, not a relabeled PBKDF2 result.
Robolectric's process-random substitutes require both DEBUG and its runtime identity;
they cannot become installed production fallback behavior.

PIN verification uses a domain-separated one-way verifier. PIN changes/recovery
retain the stable data key and authenticate existing envelopes before replacing
them. Interrupted sealing resumes only after successful authentication. An unreadable
configured recovery phrase is not permission to generate a new one over it.

Retired plaintext/static-key importers, old local development credential formats and
manual archive encryption versions 1–3 are unsupported. Reject and retain them.
Room schema migrations are separate supported structural evolution and remain in
the project. Recovery of old dummy/development data requires a readable original
build and its verified current-format portable export; documentation must not
promise automatic conversion or suggest disabling protection as a universal fix.

## Restore, lifetime and publication

Validate bounds, manifest identities, graph links and complete GCM authentication
before any live mutation. Hold maintenance admission across databases, preferences,
assistant state and widget/background writes. A verified synced before-state journal
precedes writes; a durable operation-specific marker distinguishes commit from
rollback. Keep the journal when recovery fails and gate ordinary app access.
Never interpret unreadable state as an empty dataset. See the full
[backup/restore contract](../features/backups/BACKUP_AND_RESTORE.md).

Vault relock clears decrypted caches and stale refresh/editor results cannot reopen
private data. PIN-free archiving grants write-only encryption, not read/export.
Private-picker return is a bounded, one-use memory grant; unattended private backup
requires its separate saved authorization. Archive keys can decrypt their private
records independently of the original PIN. Revocation cannot revoke already exported
files/keys. Media reclamation requires proven ownership and a complete reference
inventory; age/filename alone never authorizes deletion.

Logging sanitizes credentials and throwable text before Android/disk/buffer sinks.
Progress and outcome files contain non-sensitive status/counts, not content or keys.
Private exports use narrow FileProvider paths, encrypted staging and scoped expiry.
Provider read-back and actual bytes streamed outrank provider-reported success/size.
Use FLAG_SECURE/sensitive-window protections without clearing stronger flags;
launcher widgets and external providers are not app-owned protected windows.

App-private broadcasts require signature authentication as well as non-exported
registration. Keep WindowManager blocking per AGENTS.md and permissions consent
per the [runtime guide](../features/system/RELIABILITY_AND_PERMISSIONS.md).
The assistant has no private-Notes metadata/content capability, even on request.

## Verification and threat limits

```sh
python3 tools/check_chat_boundary.py
python3 docs/security/ayva_review_checks.py
```

These are source contracts, not native cryptographic proof. Distinct JVM, Android
emulator and physical-device checks cover different failures; see the
[build guide](../development/BUILD_AND_TEST.md). Record actual revision, device,
commands, failures/reruns and limits. A historical pass does not certify new code.

Keystore hardware protection depends on the device; StrongBox is not assumed.
Immutable strings/library/provider buffers cannot be guaranteed immediately wiped.
Logical deletion and flash overwrites are not forensic-erasure promises. Exported
recovery credentials are intentionally readable secrets. This design does not
promise protection against a compromised running app/OS or recovery without a
valid archive and usable matching key. Never include real secrets in review reports.
