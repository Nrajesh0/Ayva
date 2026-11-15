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

## Camera and picker return

A private editor seals its draft before opening a camera, gallery or file picker.
Returning within two minutes can reopen that same unchanged note with a one-use,
memory-only authorization; the global vault remains locked while the picker is
open. Screen-off/device lock, an explicit vault lock, expiry, process loss,
credential changes or a restore revoke this convenience and require a fresh PIN.
Saved selection metadata contains no key or PIN and cannot authorize a replay.
Quick cancellation also returns to the same note; expired selections show the
existing PIN flow. See [backup and restore](../backups/BACKUP_AND_RESTORE.md) for archive coverage and recovery.



## Media display, deletion and recovery

Images use authenticated bounded reads, sampled display and encrypted preview
derivatives. Preview identity includes the original encrypted file identity;
unsupported/large inputs use the bounded display fallback. No plaintext disk
thumbnail cache is introduced. Image imports apply the existing bounded image
pipeline; attaching the original file preserves its bytes, animation/resolution
included. Keep original attachments distinct from disposable previews.

Small encrypted streams use an authenticated in-memory snapshot wiped on close;
large streams authenticate before exposing plaintext. Existing encryption formats,
original image quality and backup password derivation are not weakened for speed.
Sensitive progress/logs contain operation labels, not note text, credentials or paths.

Removing an image or permanently deleting a note creates known retirement
candidates. Reclamation needs a fresh, complete strict reference inventory and
must preserve shared media, trash references, current creation/editor/undo leases
and pending recovery. Unknown files, unsupported payloads and incomplete private
inventories are retained regardless of age. This deliberately favors retention
over reclaiming every abandoned byte. Flash deletion is not forensic erasure.

Archive batches are transactional. Editor operations retain revisions, pending
new-note IDs and newer drafts until persistence succeeds; unsupported/corrupt
payloads cannot open as empty editable notes. Whole-store restore invalidates old
editor generations, including equal-timestamp rows. Recovery never overwrites a
newer live note with an older draft; preserved copies make conflicts recoverable.

The editor's non-inline layout boundaries prevent oversized generated Android
methods; preserve them when refactoring state/effects. Native Android verification
can catch a DEX VerifyError that JVM tests and APK assembly do not.
