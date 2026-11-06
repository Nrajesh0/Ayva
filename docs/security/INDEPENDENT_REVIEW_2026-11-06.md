# Independent source review: application and user-data security

Date: 6 November 2026. Reviewed the current working tree based on commit
`908cd397618c595a065544674cc51b236f244858`, including the uncommitted cleanup and
lint fixes. This is a fresh source review and focused test run, not an independent
third-party certification or a complete penetration test. No application code was
changed for this audit.

## Assessment

The app has meaningful protections against ordinary sandboxed applications and
extraction of modern encrypted note/media files. It has authenticated encryption,
Keystore-wrapped keys, encrypted portable backups and substantial restore checks.
However, it does not justify an unconditional “all user data is encrypted,”
“strictly air-gapped,” or “the vault remains protected after app-process compromise”
claim. Protection varies by data type and attacker access.

The main priorities are enforcing the offline speech boundary, clarifying the
vault's key-custody boundary, closing encryption coverage gaps, and reducing
notification disclosure. None of the findings below establishes that an ordinary
unprivileged app can directly open the private databases or extract Keystore keys.

## Findings, ordered by remediation priority

### S01 — High privacy risk: speech is not constrained to on-device recognition

Evidence: `util/speech/OnDeviceTranscriber.kt:161–169,309–321` uses
`SpeechRecognizer.createSpeechRecognizer(context)` and the general phone speech
service. The recognizer intent does not request `EXTRA_PREFER_OFFLINE`; it also
does not use the dedicated on-device recognizer API. Error messages referring to
offline packs do not enforce offline execution.

The merged debug manifest has no INTERNET permission. That blocks direct network
access by this app, but not network access by a speech provider running under its
own identity. Audio/transcripts may therefore be processed remotely depending on
the installed provider and its settings. No actual provider upload was captured
in this environment; the confirmed defect is the missing offline constraint.

Recommendation: require `isOnDeviceRecognitionAvailable` and
`createOnDeviceSpeechRecognizer` on the supported API levels, with no automatic
fallback to the general provider. If provider-assisted recognition remains an
option, make it a separately explained, explicit user choice. A “prefer offline”
hint alone is not an adequate offline guarantee.

### S02 — Medium: vault PIN is not an independent boundary against app-identity execution

Evidence: `data/note/ArchiveVaultSecurity.kt:243–263` derives a PIN wrapping key
(KEK), wraps a random vault data key with it, then also stores the KEK encrypted
under an Android Keystore master key. `verifyPasscode` decrypts that expected KEK
at `427–453`; `unwrapVaultDataKey` is defined at `93–98`. The master-key creation
at `1254–1263` does not require OS user authentication.

An attacker already able to execute as the app and use its Keystore operations
can recover the stored KEK and unwrap the vault key without knowing the PIN. This
is a code-derived attack path, not a demonstrated remote exploit or proof that
another ordinary app can access the key. Process compromise is a strong
prerequisite, but matters if the PIN is advertised as independent protection.
Vault attachment files also use the app-wide media Keystore key rather than a
separate vault-PIN-bound file key (`util/crypto/EncryptedMediaStorage.kt`).

Recommendation: specify the threat model explicitly. For stronger isolation,
design verifier-only credential storage rather than persisting a recoverable KEK,
combined with a high-entropy passphrase and/or OS-auth-bound key access. A six-digit
PIN alone has limited offline entropy. Any key-custody change needs a verified,
crash-safe migration and recovery design; deleting existing envelopes would risk
permanent data loss. Do not make that change as a cosmetic cleanup.

### S03 — Medium, conditional on data extraction: encryption coverage is incomplete

Evidence:

- `data/chat/AyvaChatDatabase.kt:38–48`: ordinary Room `ayva_chat.db`.
- `FocusApplication.kt:173–182`: ordinary Room `vocab.db`, including user learning
  state/bookmarks.
- `data/drill/DrillDatabase.kt:60–66`: ordinary Room `drill_sessions.db`.
- `util/CompletedTaskHistoryManager.kt:60–85,168–188`: task titles and details are
  copied from the encrypted main database into JSON SharedPreferences. Records
  are limited to the current day, but readable there while retained.
- `util/backup/MaintenanceOpenHelperFactory.kt:11–15` defaults to the framework
  SQLite factory; the maintenance gate is not encryption.

These stores still benefit from Android app sandboxing and device file-based
encryption. They are not globally readable files. But obtaining the app's private
data after unlock, through root, debugging or another compromise, exposes these
records without the app's SQLCipher key. Encrypting the primary task database
does not protect its plaintext preference copies.

Recommendation: inventory all sensitive stores and migrate auxiliary databases
and content-bearing preferences to consistent encrypted storage. Preserve
bookmarks, history, IDs and rollback/recovery during migration. Until then, scope
encryption claims to the data types actually covered.

### S04 — Medium, legacy-dependent: public static compatibility keys remain accepted

Evidence: `data/note/DatabaseKeyProvider.kt:197–233` and
`util/crypto/EncryptedMediaStorage.kt:102–105,207–215` derive legacy AES keys from
fixed source-code strings. Media reads also accept legacy plaintext at `223–233`.
These are read-compatibility paths. New production writes fail closed if
Keystore setup fails; this is not evidence that newly created data uses static
keys. Database passphrases have an upgrade attempt at
`DatabaseKeyProvider.kt:241–275`. The inspected media read path does not rewrite
old files under the modern key.

If old protected files remain, a person who obtains them can derive the legacy
key without a device secret. Merely renaming files or upgrading the app does not
establish that every legacy file was re-encrypted. Legacy-data prevalence on real
installations was not measured.

Recommendation: add an explicit, resumable migration inventory with verified
read-back before retiring old copies. Do not remove compatibility readers before
successful migration and recovery testing. New-write fail-closed behavior should
remain mandatory.

### S05 — Medium, device-policy dependent: reminder contents can appear on the lock screen

Evidence: `service/TaskReminderReceiver.kt:131` sets the task notification channel
to `VISIBILITY_PUBLIC`. At `212–223`, the notification contains the task title,
details and expanded details without an explicit redacted public version.

Someone looking at a locked device may see sensitive task contents. Actual
visibility depends on OS/OEM settings, notification/channel settings and existing
user overrides. This is a presentation disclosure path, not a cipher failure.

Recommendation: use a private notification visibility default, provide a generic
public version, and offer an explicit content-on-lock-screen choice. Existing
channel settings may remain under user control; changing only code does not
guarantee old installed channels adopt a new policy.

## Verified strengths and boundaries

| Area | Evidence and practical meaning |
| --- | --- |
| Main task/habit/focus database | SQLCipher factory supplied in `FocusApplication.kt:96–105`; production initialization fails closed rather than silently creating an unencrypted replacement. |
| Notes database | SQLCipher and a random, Keystore-wrapped database passphrase (`data/note/NoteDatabase.kt:310–345`, `DatabaseKeyProvider.kt`). |
| Vault note payloads | Random vault data key, AES-256-GCM, 12-byte IVs and 128-bit tags; authenticated decryption errors do not return fabricated plaintext (`VaultPayloadEncryptor.kt:145–211,254–300`). PIN attempts are progressively delayed; vault state is cleared on app background (`MainActivity.kt:271–275`). These do not negate S02. |
| Media | Modern images/attachments use authenticated Keystore-backed encryption and atomic writes (`NoteImageHelper.kt:127–138`, `EncryptedMediaStorage.kt:113–141`). Keystore backing does not prove hardware isolation on every device. |
| Portable backups | Current v4 format uses AES-256-GCM, authenticated headers, fresh salt/IVs and Argon2id 64 MiB / 3 passes. User-facing export requires at least eight trimmed characters; a long unique passphrase is substantially safer. Recovery/exported files must be protected by the user. |
| Restore safety | Size/count/nesting limits, canonical media-path checks, staging and verification before mutation; corruption and collision cases are exercised by the tests. These reduce corruption/path-traversal risks, not a proof against every malformed archive. |
| Sharing | FileProvider is unexported and grants scoped access only to cache exports/diagnostics. Incoming share parsing restricts URI schemes and rejects self-provider/traversal cases. Explicitly sharing decrypted documents still discloses them to the chosen recipient app. Export cleanup is best effort, not cryptographic erasure. |
| Screenshots/overlays | `MainActivity.kt:253–266,431–434` enables secure-window and overlay-hiding protection for sensitive routes; a global privacy preference covers other surfaces. This does not prevent physical photography or compromised OS capture. |
| Android backup/network | Merged debug manifest confirms `allowBackup=false` and no INTERNET permission. The app's direct network restriction does not constrain external providers (S01), browsers or user-selected share destinations. OEM device-transfer behavior still needs device testing. |

## Data protection by threat

| Threat | Assessment |
| --- | --- |
| Ordinary unrelated app on a supported, unrooted device | Useful protection from sandboxing, scoped providers and internal receivers; no general private-file read route established in this review. Exported components still need fuzzing. |
| Lost locked phone | Depends heavily on Android credential strength, file-based encryption, OS patching, OEM implementation and bootloader state. Not measured on hardware. |
| Offline copies of modern encrypted notes/media | Meaningful protection if Keystore secrets cannot be used/recovered. Auxiliary and legacy files have weaker coverage (S03/S04). |
| Stolen portable backup | Authenticated encryption provides good protection with a strong unique password or securely held recovery credential. Weak/reused passwords remain guessable; loss of all recovery material can make recovery impossible. |
| App-process compromise/root | Not a vault-isolation guarantee (S02). Android Keystore can restrict key extraction without preventing authorized decrypt operations by compromised app code. |
| Lock-screen observer | Task content can be exposed (S05). |
| Voice provider | Provider may process audio using its own network privileges (S01). |
| Accidental deletion/key loss | Snapshots, trash and journals help; no guarantee of recovery without valid keys/backups. Trash/snapshots/exports mean a delete action is not necessarily immediate forensic erasure on flash storage. |

## Validation and limitations

Fresh execution: **37 tests passed, zero failures/errors/skips** across:
`WorkPackage2SecurityTest` (8), `WorkPackage4SecurityTest` (5),
`WorkPackage5SecurityTest` (5), `WorkPackage6SecurityTest` (4),
`ZeroNetworkSecurityAuditTest` (2), `SecurityRemediationRegressionTest` (3), and
`PortableAutomaticBackupTest` (10). Command used `:app:testDebugUnitTest` with
these seven class filters, `--no-daemon --max-workers=2`.

Those are existing regression tests, not independent proof of the threat paths
above. The findings come from tracing the current implementations; no exploit of
a real user's device or live provider was attempted.

Important: Robolectric can substitute software test keys, PBKDF2 for unavailable
native Argon2id (`Argon2idKdf.kt:96–101`) and an in-memory notes database when
SQLCipher is unavailable (`NoteDatabase.kt:329–345`). Therefore these passing tests
do **not** prove hardware Keystore protection, production native KDF behavior, or
SQLCipher at-rest security on a real device.

Inspected the merged debug manifest and generated debug APK context; the manifest
is intentionally debuggable. This is a development artifact, not a release-security
finding. A signed, non-debuggable shipping APK was not independently validated.
No hardware/device pentest, live speech-provider traffic capture, dependency/CVE
database scan, native-library audit, comprehensive exported-component fuzzing or
complete migration/restore matrix was performed. No claim of zero vulnerabilities
or quantified security score is supported.

Before relying on the app for highly sensitive data, resolve S01 and choose an
explicit S02 threat model, address S03/S04 coverage, harden S05, then validate the
signed release on physical devices: locked/unlocked states, offline speech,
screenshots/recents, notification redaction, Keystore invalidation and cross-device
backup recovery. Security migrations must preserve existing encrypted data.
