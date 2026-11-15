# Open findings and acceptance

This is the current follow-up list, consolidated from dated reviews. A historical
finding is not still open merely because its original report said so. Revalidate
the relevant source before implementing a change; do not replay retired roadmaps.

## Licence-signing key

High priority: the 13 November review identified a historical root `private.pem`
whose derived EC public key matches the active lifetime-license verifier in
`LicenseManager.kt`. Addition commit: `43661bb998a5c20153cb0671f66ca41ec7340355`;
removal: `3981775`; historical blob: `baef34788b8294c327cbe112647f23ab383f9778`.
The reported public-key SHA-256 is
`476df9274797e7ddc412557382f771aa84f7ddcabc11fd0330fbcf750c526b3b`.
Do not retrieve or paste the private material into another report.

The reviewed impact is forged activation licences, not disclosure of vault PINs,
VDKs, SQLCipher passphrases, media keys or a demonstrated APK-signer compromise.
Retire/replace the licence signer outside Git/AI imports, update its verifier and
decide how old licences are handled. History rewriting alone cannot revoke a
disclosed key and needs a coordinated plan; do not rotate user data keys or the
Android signer in response. This documentation cleanup does not resolve the finding.

## Deferred product/privacy and provenance

- Speech-provider/privacy behavior was explicitly deferred. Test and describe actual
  recognizer/service/language behavior; no blanket offline/privacy claim follows
  from a UI toggle or absence of app INTERNET permission.
- `QUERY_ALL_PACKAGES` remains an intentional discovery capability; distribution/store
  eligibility requires its own review before narrowing behavior or publishing.
- The original bundled `vocab.db` has no separate redistribution-provenance record.
  The Wordset notice covers saved Wordset-derived entries, not that independent seed.
- Custom-format secrets, remote forks/artifacts and arbitrary binary/text encodings
  are outside the former pattern scan's completeness claim. Consider real secret
  prevention/scanning; ignore patterns alone are not a security boundary.

## Device and acceptance work

Record device/OS, actual commit/APK hash and outcomes for:

- Native Keystore/Argon2id/SQLCipher, key invalidation, low storage and retained
  unreadable stores. Process kill on both sides of restore commit and repeated
  recovery; JVM fault injection is not physical power-loss verification.
- Real local/cloud/SD document providers, permission revocation, incomplete files,
  recovery PDF/QR rendering and reinstall recovery using the exported credential.
- Modern Android/OEM blocking overlays, alarm-access changes, reboot/force-stop,
  notification delivery, optional device-admin migration and delegated camera.
- IME/Back/rotation/process recreation, large text/TalkBack, widget host behavior,
  speech lifecycle and independent conversation wording. See the assistant guide.
- Phone-side backup/restore/image latency and memory measurements; do not infer
  user-visible timing or quality scores from test duration or source line counts.

Current local source-cleanup checks passed as recorded in the compact history;
native execution for that working tree was unavailable. Earlier API-matrix passes
belong to their earlier commits, not automatically to subsequent local changes.

## Maintenance candidates, not established runtime defects

The large main note editor/NotesViewModel still need coherent state/IO boundaries.
Account retains some UI-to-storage reads. Root `util` contains feature services;
move them as responsibilities are refactored. Shared chat UI/storage/Room DTOs have
separate purposes: retain mappings and round-trip coverage rather than collapsing
them blindly. A consistent unavailable/error state should distinguish IO failure
from a truly empty repository without weakening preservation behavior.

Older static review named HkdfUtil, VaultSyncManager and PreferencesHubHelper, some
resources and root tooling metadata as candidates. References/tests/dynamic platform
entry points need checking before deleting or wiring them into new flows; this list
does not establish that they are dead or safe to remove. Keep tests, Room schemas,
build wrappers, licensing/attribution and security guards while simplifying docs.
