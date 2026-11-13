# Archive flow and optional PIN-free archiving

## User flow

Settings → Private notes vault → **Ask for PIN when archiving**.
The switch is on by default and requires a configured vault to change it.

| Vault mode | Archive a note | View / unarchive |
| --- | --- | --- |
| Ask for PIN | Inline vault authorization when locked | Vault PIN required |
| PIN-free archiving | Add encrypted notes while the vault stays locked | Vault PIN required |

Turning off the switch requires one PIN confirmation. It does not disable the
vault, save the PIN, or retain a key capable of opening the vault. Turning the
switch back on does not rewrite or invalidate earlier notes. After a PIN change
or recovery changes its protected key envelope, PIN-free archiving must be
confirmed again; the safe fallback is to ask for the PIN. Existing notes remain
readable with the current authenticated vault key.

PIN verification for a move does not navigate away from the editor. Cancelling
leaves the draft and selection. Progress and errors use the app's existing
Material components; the PIN sheet has an explicit Cancel button. Return-route
navigation occurs after persistence succeeds, rather than on tapping Archive.

## Persistence and concurrency

- Editor archive/unarchive saves the current complete draft through the existing
  optimistic revision checks, including new notes without an assigned ID. A PIN
  prompt can receive newer draft/attachment state; the write captures that latest
  state after authorization. A change after the captured write snapshot is not
  discarded.
- Batch moves run in one Room transaction. A missing, conflicting, or unreadable
  target rolls back the whole batch and preserves selection.
- A whole-store restore invalidates a pending move. Vault-setting changes before
  a write reject stale authorization. Failed writes preserve existing rows.
- Authorized writes/recovery and key wiping complete in a scoped NonCancellable
  block if the screen owner is destroyed. Private edits that cannot be committed
  are retained through the encrypted recovery mechanism; a storage failure is
  reported rather than claiming success. Existing recovery files cannot be
  overwritten by a new archive operation. Interrupted AtomicFile recovery writes
  are reopened with AtomicFile semantics.
- The global vault relocks before the write; the one-use VDK exists only in the
  operation, and its owned array is wiped in finally. Cached private plaintext is
  cleared on relock. Opening a stale card/cache entry or completing an asynchronous
  refresh cannot open a private note while the vault is locked.

## Write-only encryption

PIN-free archiving uses RFC 9180 HPKE base mode, implemented by Bouncy Castle's
lightweight API: X25519, HKDF-SHA256, AES-256-GCM. `bcprov-jdk18on:1.87` is pinned
from Maven Central. No global JCA provider is registered or reordered. Existing
PIN derivation, stable VDK wrapping, SQLCipher keys, and backup format stay intact.

Enabling the option generates a receiver key pair. The receiver's 32-byte private
key is AES-256-GCM wrapped by the authenticated VDK, with domain-separated AAD
binding its public key. Only that wrapped private key and its public metadata are
persisted. Configuration is additionally authenticated/encrypted with the existing
device media key in noBackupFilesDir. A complete self-test precedes publication;
configuration is bound to one atomic snapshot of the current supported PIN
authentication metadata and protected VDK envelope. Missing, stale, or
tampered configuration falls back to requesting the PIN, never plaintext writes.

Each new sealed note carries its public key, VDK-wrapped private key, wrapping IV,
HPKE encapsulation, and authenticated ciphertext. HPKE AAD binds the public/wrapped
key metadata. Opening needs the VDK to unwrap the receiver key; the write
capability has no VDK and cannot open the note. Per-note self-contained wrappers
allow old notes to open after this option is disabled or reconfigured. Payloads
use the same field codec and redaction as existing vault notes, including title,
content, checklist, labels, image/audio paths, color and font. The envelope is a
`sealed-v1` subtype of the existing vault prefix; existing envelopes are unchanged.
Configuration is bounded to 8 KiB; sealed envelopes to 64 MiB, with fixed-length
key/IV/encapsulation validation and authentication before payload decoding.

The private key's raw serialized array and owned plaintext buffers are wiped.
Library/JCA objects and immutable JVM strings remain subject to normal garbage
collection; this is not a claim of complete heap erasure.

## Backup and restore

The public write capability grants no read/export authorization. Manual and
automatic backups still need their existing separately authorized VDK to include
private notes; unavailable authorization fails the complete backup rather than
omitting notes. Both original and sealed notes are decrypted by the existing
vault payload API before entering the password-encrypted archive. Restore uses
the destination vault's normal encryption and needs no copy of the source's
PIN-free archive configuration. Repeated clean restores retain note counts and
authenticated attachment bytes.

Unsupported older development vault credentials remain fail-closed and unchanged;
this feature neither resets them nor reintroduces credential migrations.

## Verification

Focused tests cover both modes, settings/PIN UI, small-screen cancellation, wrong
PINs, stale callbacks, new/edited notes, screen destruction, revision conflicts,
atomic batches, recovery-file preservation, all envelope components being
modified, wrong keys, corrupted configuration, option disable/re-enable, PIN
changes, separate automatic-backup authorization, and repeat restore with media.
The native restore fixture retains its original private-note assertions and adds
a sealed private note through backup/restore and lock checks.

Latest per-class results cover **389 selected regression executions across 24
classes**, with zero failures, errors or skips. The broad run executed 387 cases;
one recovery fixture checked before cleanup finished. Its wait now includes the
main recovery file, retaining its original assertions. The affected three classes
were rerun (61 passing cases), also adding two executions for unsupported/changed
authentication metadata. The combined count uses each class's latest result rather
than double-counting reruns.

Debug and optimized release APK builds pass, and native fixtures compile. Full
Debug/Release lint passes with zero errors (245/243 warnings and 57 informational
findings per report at that scan). The sole added dependency-catalog style warning
was then fixed by moving the unchanged 1.87 dependency into the version catalog.
Both APK variants rebuilt successfully; release dependencyInsight confirms 1.87.
A scoped UseTomlInstead rerun reports only the 10 existing literal-dependency
warnings, with none for the added library. Full reports were preserved separately;
no permanent lint checks or suppressions were changed. Application sources were
unchanged during this final build-file-only cleanup.

Source security contracts, chat data boundary, dictionary integrity, shell syntax
and diff whitespace checks pass.
No connected device/emulator is available locally; native fixture compilation does
not constitute native execution. The completed changes are included in version 2.0.46 (146) for the requested GitHub push.
