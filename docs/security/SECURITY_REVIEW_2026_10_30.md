# Current-code security review — 30 October 2026

Scope: main c7eec3b (2.0.23), including local uncommitted voice-presentation changes. Read-only production review; no production fixes made. Reviewed Android manifest/exposure, Notes key handling, media/KDF code, database construction, backup import validation, restore startup integration, WebView configuration, note widget/share screens, screenshot policy and current speech selection. This is a targeted source review, not an exhaustive audit or penetration test.

## Assessment

Substantial protections against ordinary third-party app access and accidental exposure, with remaining privacy-policy gaps and unequal storage protection. No confirmed critical remote exploit or locked-vault bypass identified in the inspected paths. This is not a finding that the whole app is vulnerability-free.

## Findings and limitations

1. **Medium privacy-policy inconsistency: screenshot protection is not applied across sensitive activities.** SecurityScreen describes the option as preventing screenshots of the app. MainActivity.updateSecureWindowFlags reads secure_recents. NoteWidgetConfigureActivity reads active Notes and displays titles/content previews but has no secure-window application. BubbleChatActivity and ShareToNoteActivity also lack that flag/preference handling. NoteWidgetConfigureActivity is exported for Android widget setup; export itself is expected and does not grant a caller programmatic access to note data. The issue is inconsistent screen-capture protection when the user enabled it. The active-note query excludes archived and trashed Notes, so this does not establish disclosure of locked vault content. Remedy: shared sensitive-window policy, including these windows; validate via device screenshot, recents and recording tests.

2. **Medium conditional storage-extraction risk: chat/drill history are not app-layer encrypted.** AyvaChatDatabase and DrillDatabase construct MaintenanceOpenHelperFactory without SQLCipher; its default is FrameworkSQLiteOpenHelperFactory. Android sandbox and device filesystem encryption still protect these files. This does not allow an ordinary separate app to read them. If private storage becomes readable through a compromised device or extraction, these stores have less protection than SQLCipher Notes/Focus. Remedy: decide which records require encryption or retention limits, with lossless migration. Do not describe all app data as equally encrypted.

3. **Privacy boundary, deliberately accepted behaviour: transcription can use the network.** OnDeviceTranscriber now calls createSpeechRecognizer without forcing offline, per the user's request to restore the working phone provider. The provider runs with its own permissions; removal of Ayva's INTERNET permission does not guarantee local audio processing. This is not a newly discovered unauthorized change. Preserve the disclosure and distinguish offline assistant logic from system speech processing.

4. **Legacy conditional risk: static-key compatibility readers remain.** DatabaseKeyProvider and EncryptedMediaStorage retain known legacy software-key readers. New production encryption fails closed instead of deliberately using those static keys; database credentials have an upgrade path. These readers do not imply new records use static keys. Historical files written under the legacy keys have weaker protection if extracted. No installed user data was inspected, so the presence of such files on a particular phone is unknown. Safe remediation requires inventory/migration with verification, not deletion of compatibility readers.

## Controls confirmed in current source

- Notes uses SQLCipher, with Keystore-wrapped random passphrase and fail-closed production initialization; Focus also passes a SQLCipher factory.
- Media/vault encryption uses authenticated AES-GCM. New production Argon2id derivation fails closed if native crypto is unavailable; JVM fallback is test-only. Vault PIN checks include progressive lockout and constant-time comparison.
- FileProvider is not exported and exposes only exports/diagnostics cache paths, not the full private directory. Most action receivers and editor activities are unexported.
- Backup parsing limits entry counts and sizes, rejects duplicates, checks canonical paths and verifies media hashes. RestoreJournal startup recovery and maintenance write coordination are present. Earlier O1–O4 audit findings must be read together with the later remediation record, not repeated as current unfixed findings.
- The chest WebView denies navigation, network loads, file/content access and external requests; its bundled HTML has no exposed JavaScript-to-native bridge in the inspected implementation.
- allowBackup=false and usesCleartextTraffic=false. Chat dependency guard passed for 117 source files; chat remains isolated from private Notes capabilities.

## Verification boundaries

Ran the chat boundary guard and inspected source. No new exploit PoC, dependency advisory refresh, full test suite, signed APK analysis or physical-device security test was performed in this review. Prior audit/remediation documents record their own unit/emulator evidence; those results were not rerun here. Native ChatScript, SQLCipher, codecs, transitive dependencies, rooted-device resistance and OEM-specific behaviour require separate testing. The preceding full-suite attempt in this conversation had one dependency-download failure, so it must not be represented as a completely green fresh security suite.
