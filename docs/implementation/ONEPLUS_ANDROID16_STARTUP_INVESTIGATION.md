# OnePlus Nord startup crash investigation

Date: 2026-11-04. Status: investigation complete with device evidence outstanding; no device-specific root cause confirmed.


Latest finding: the incident is now described as Settings → bubble → typing, with ordinary Chat/Talk mode uncertain. A **real encrypted-draft autosave exception crash was reproduced on API35/36 and fixed locally**, preserving the last saved encrypted draft when writes are rejected and retrying on later edits. This path was introduced after 2.0.0. The actual phone's cause and the later logo-screen crash remain unconfirmed without its crash/exit trace. Historical migrations/legacy key format passed targeted checks, and the actual published 2.0.0 and 2.0.39 native libraries match. No signed hotfix has been published. See the bubble follow-up for reproduction, patch and validation details.

## Incident and scope

The affected phone is in the OnePlus Nord series, running Android 16. The previous app version worked on the same OS. After upgrading to the latest release, launching repeatedly shows the logo and terminates. The previous app version is now estimated by the user as around 2.0.0. Exact Nord model, exact previous version, OxygenOS build, memory page size and crash trace are not available.

GitHub's latest release resolves to **v2.0.39**, commit **33c5e0750fe6f38bb0a0724069238ac6449c05af**. Investigation used the actual downloaded GitHub APKs for 2.0.38 and 2.0.39, rather than assuming local development builds matched the shipped artifacts. The current, unreleased exam-widget changes are outside the release under investigation. Application/MainActivity startup code is unchanged by those widget edits.

## Verified release evidence

| Check | Result |
| --- | --- |
| Published 2.0.39 APK | 44,144,483 bytes; SHA-256 `db812919ae0e1bd87bcf10ffa45a15593f90eee561ace0f07311be054b72e3bd` |
| Published 2.0.38 APK | 62,831,648 bytes; SHA-256 `05f860d57aae10c85f7ea4545e9456e957c19506112b75bf05f3cf6caf37c0ad` |
| Published checksums | Both downloaded APKs match their release SHA256SUMS.txt |
| APK signatures | Both verify; same signing certificate SHA-256 `6b3e9c1d48575999d3dd2f986ef8302a9f419bfbd3b9ab2afaa76c675b5310c7` |
| SDK settings | Version 2.0.39/code139, min SDK31, target SDK35; no SDK change from 2.0.38 |
| Native libraries | Every retained library has the same CRC in both APKs; only the four ABI copies of `librun_dialogue.so` were removed |
| Assets | ChatScript runtime/licenses removed; retained assets unchanged except generated DEX baseline profiles |
| Removed-code references | Parsed every DEX string table: 2.0.39 has no `NativeGeneralDialogue` or `run_dialogue` strings; 2.0.38 contains both |
| Database/schema/key changes in release diff | None |

This supports artifact integrity and narrows the update delta. It does not prove that this user's installed copy matches the published APK, that all release-only behavior is correct, or that every R8/runtime issue is excluded.

## Confirmed Android compatibility debt

ELF program headers of the **published ARM64 release** contain 4 KB (`0x1000`) PT_LOAD alignment in:

- `libsqlcipher.so` from deprecated `android-database-sqlcipher:4.5.4`.
- `libargon2jni.so` and `libargon2native.so` from Argon2kt 1.4.0.
- `libimage_processing_util_jni.so` from the CameraX dependency chain.

Rive, libc++ and AndroidX graphics-path ARM64 libraries have 16 KB (`0x4000`) alignment. The problem is ELF segment alignment; changing SQLite's database page-size pragma or ZIP compression would not rebuild those libraries.

These older native binaries are a real risk on 16 KB-page devices. Android version alone does not establish page size, and Android 16 can provide a compatibility mode for some 4 KB binaries. Most importantly, the same native binaries were in 2.0.38, which makes this a weak explanation for a regression from that version on an otherwise unchanged phone. The user now estimates the old version as around 2.0.0; the follow-up below also compares that published release.

SQLCipher's own deprecated-library notice explicitly calls out migration and 16 KB support: https://github.com/sqlcipher/android-database-sqlcipher#readme

The eventual compatibility fix should migrate to a maintained, 16 KB-compatible SQLCipher Android distribution and upgrade/rebuild all other affected native dependencies. Preserve existing encryption keys and verify opening/upgrading real old encrypted databases. Never silently replace encryption with plaintext, regenerate a missing key, or delete an existing database.

## Code-backed candidates for this crash loop

All candidates below remain **unconfirmed for the affected phone**. The logo is Android's launch surface; dying before the first app frame does not identify the failing subsystem.

| Candidate | Startup path and repeat condition | Evidence needed |
| --- | --- | --- |
| Existing database/key cannot open | MainActivity constructs its ViewModels before `setContent`; repository creation obtains the encrypted database. DatabaseKeyProvider deliberately throws when decryption/key retrieval fails or existing data lacks a key. The same persisted failure can recur every launch. There is no bootstrap recovery screen. | `SecurityException`, `Database encryption failure`, `KeyStore`, authentication-tag errors, SQLCipher/SQLite open error or Room schema validation stack |
| Interrupted restore or legacy staging processing | FocusApplication blocks startup with `runBlocking(IO)` to recover the encrypted restore journal and protect old media stages. An unreadable journal, schema mismatch, filesystem error or lengthy work occurs before any recovery UI exists. Retaining the journal is correct for data safety, but repeating the same failing operation can block every launch. | Stack mentioning `RestoreJournal`, `EncryptedSnapshotFile`, `RestoreStaging`; ANR/exit reason; whether a restore was interrupted |
| Saved preference has wrong type/unexpected state | Application startup synchronously initializes many managers, including typed preference reads. A `ClassCastException` can terminate before the first frame; no release-wide preference reset is justified. | Exact exception and preference key/manager frame |
| Foreground service fails after the launch request | MainActivity starts the blocker and optionally the bubble. Wrapping `startService` does not catch later exceptions inside a service callback. Blocker foreground promotion also swallows failures with `runCatching`; if promotion fails and the service remains started, Android can terminate the process after its deadline. Manifest special-use permissions/type are present. | `Unable to create/start service`, `ForegroundServiceDidNotStartInTimeException`, notification-posting exception, denied promotion or OS exit reason |
| Native linker / runtime failure | SQLCipher is loaded very early; a load failure is logged and execution continues. A later native DB call can still fail. Native faults do not necessarily enter the Java crash handler. | `UnsatisfiedLinkError`, `dlopen failed`, native signal/tombstone, actual `getconf PAGESIZE` result |
| OS kill / low memory | A splash disappearing can also be an OS process kill rather than a Java exception. No evidence currently establishes this. | ApplicationExitInfo / system bug report |

Release-specific cleanup was inspected separately. It deletes only the retired extraction/scratch roots, guards recursive deletion failures with `runCatching`, and does not access database keys or user-authored data. The old runtime archive contains 87 entries, about 62 MB expanded. Cleanup is on an IO coroutine; no deterministic upgrade crash was found in that path. Obtaining the two directory paths happens outside the per-root guard, and the launch coroutine has no outer exception handler, so an exceptional filesystem/context failure is not mathematically excluded without a trace. GeneralDialoguePhrases is used for conversation requests, not knowledge-base startup warmup.

## Executed checks

**12 focused tests passed, zero failures/skips:**

- New full Application/MainActivity startup smoke checks on API35 and API36 (Android15/16).
- Nine existing restore/recovery tests, including interrupted restore, idempotence, atomic replacement failure, maintenance admission and recovery integrity.
- Existing cleanup isolation test: retired files removed while user files survive.

The startup test instantiates FocusApplication and MainActivity and verifies all three main ViewModels initialize. These are debug JVM/Robolectric tests. The application uses a test database fallback in Robolectric; they do **not** validate the physical Android linker, SQLCipher JNI, hardware KeyStore, minified release execution, OnePlus services or rendering all the way to a real display frame. No Android device/emulator was attached. Passing checks therefore cannot establish that the reported incident is resolved.

Evidence files: `/workspace/artifacts/oneplus-startup-investigation/` contains release hashes, native ELF findings, the test log and JUnit results.

## Obtain the decisive evidence without deleting app data

On the affected phone, reproduce once, then use **Developer options → Take bug report → Interactive report** (wording can vary in OxygenOS). Preserve the resulting report and the exact model/OxygenOS build. If a computer with Android platform-tools is available, these are read-only captures after reproducing:

```sh
adb shell getprop ro.product.model
adb shell getprop ro.build.display.id
adb shell getconf PAGESIZE
adb logcat -b crash -d -v threadtime > run-crash.txt
adb shell dumpsys activity exit-info com.focusbyrj.app > run-exit-info.txt
```

Keep app storage intact. Do not uninstall, clear storage, delete recovery files, or reset encryption credentials to troubleshoot this incident. The release's Java handler normally writes `files/diagnostics/last_crash.txt`, but it is app-private and cannot normally be read with `adb run-as` from the signed release. In-app diagnostic export is not a usable primary recovery path while the app cannot open; native faults and ANRs may not create that Java file at all.

## Recommended next implementation

1. Use the crash/exit trace to identify the failing subsystem and reproduce the affected persisted state before patching it.
2. Add a small startup recovery path with phase-level diagnostics and a way to export evidence even when normal startup fails. Keep unresolved encrypted recovery data intact and prevent ordinary writes until recovery succeeds. Do not catch and continue through an inconsistent restore.
3. Repair the service-promotion failure path if implicated; record the error and terminate/retry appropriately rather than hiding failure. Keep direct WindowManager blocking overlays as required by AGENTS.md.
4. Schedule the native dependency migration independently of whether it caused this incident, with an automated ELF audit and real 4 KB/16 KB upgrade tests.

The initial investigation changed no production behavior, native dependency, version or release. It added startup and historical-upgrade regression tests. The later bubble-typing investigation below produced a targeted autosave fix; existing exam-widget work is preserved.


## Follow-up: upgrade from approximately 2.0.0

The user clarified that the previously working app was approximately **2.0.0**, rather than 2.0.38. This significantly broadens the relevant release delta. The old release must not be inferred from the initially compared adjacent releases.

The published GitHub 2.0.0 APK (`app-release.apk`) was downloaded and inspected. Its tag is commit `569572efad930c584c96daf1eb45f532de7d6843`. Its production source/schemas match the earlier version-bump commit `fe8da10` for the inspected paths. The APK is 46,782,906 bytes. Its signature verifies with the same release certificate as 2.0.39. All native libraries are byte-identical between published 2.0.0 and 2.0.39: no additions, removals or changed retained libraries. Thus the 4 KB compatibility debt already existed in 2.0.0 as well.

The actual changes relevant to persisted data include:

- FocusDatabase **12 → 13 → 14 → 15 → 16**: planning receipts, task planning data, habit pause history and habit frequency. All four migration objects exist and are registered in production.
- Vocabulary database **2 → 3**, adding dictionary bookmarks. The bundled version1 vocabulary asset is byte-identical in both published APKs; the old release upgraded it to version2.
- Notes remain schema7, chat schema1 and drill sessions schema1; their entity/schema shapes did not change across this interval.
- Database key alias, preference names, AES-GCM wrapper format and legacy software-wrapping seed are unchanged. A later guard refuses to create a replacement key if an existing encrypted database has lost its saved credentials. This is data-preserving behavior, but would stop startup for an installation with that particular damaged state.
- The current focus-session manager supports the old Boolean-only active-session flag by assigning a bounded deadline. Most startup settings managers—including theme, licence, economy and focus statistics—retain their earlier preference format. Bubble chat initialization and maintenance handling changed and remain relevant to a device trace.

**Eight additional test executions passed on API35/36:**

1. Created populated databases from the committed historical schema12,13,14,15 snapshots; opened them with the real migration objects, passed Room's schema validation for16, verified tasks/habits/logs/boundaries/schedules and detail fields survive, then closed/reopened and verified again.
2. Constructed the exact legacy software-wrapped passphrase representation and verified current key retrieval preserves its bytes and stored ciphertext.
3. Built the old vocabulary version2 state using its actual bundled asset and historical1→2 migration, set learning/bookmark state, then verified2→3 preserves all 1,773 idioms and 2,027 word-substitution entries plus the changed state.
4. Restored the old active-session Boolean without new timer fields and verified protection remains active with a valid deadline.

These checks exercise real migration SQL and persisted compatibility on the framework SQLite test driver. They do not open a physical old SQLCipher database using hardware KeyStore, reproduce the unknown user's exact data, or establish a successful minified-APK upgrade on OnePlus. SQLCipher key accessibility and native execution remain physical-device checks.

The evidence therefore supports a direct upgrade for the tested historical states; it does not identify a general missing migration or prove this incident resolved. Exact device-specific failure still requires the crash/ANR/exit report. No intermediate-version installation, downgrade, data reset or database-key replacement was performed or recommended.

Additional evidence: `release-2.0.0-evidence.json`, `legacy-upgrade-tests.log` and `legacy-upgrade-test-results.zip` in the artifact directory. The initial12 checks and these8 checks passed in separate focused runs (20 executions total).


## Follow-up: bubble interaction over Settings while typing

The user now reports that the app had reached Settings and the affected person opened/interacted with the floating bubble, then typed. They initially recalled `/talk`, then clarified they cannot remember whether Talk mode was active. Both plain chat and Talk mode must therefore be covered. This shifts attention to runtime chat interaction; a universal failure to migrate the database before the first app screen is a weaker explanation for the initial crash.

### Reproduced autosave crash and targeted fix

A concrete shared typing failure was found in `ui/components/EditorDrafts.kt`. This encrypted editor-draft autosave path was added after 2.0.0, so the old working version did not exercise the same path while typing. The bubble composer calls `PersistEditorDraft`, which attempts an encrypted draft save after a 200 ms debounce and also during ON_STOP/disposal. All three calls previously allowed storage/crypto exceptions to escape. A recoverable IOException or SecurityException can therefore leave the UI coroutine/callback uncaught and terminate the application process.

The controlled reproduction saved a valid encrypted draft, made AtomicFile's new-file path unwritable by occupying it with a directory, then rendered/edited the same autosave component used in the bubble. **Both API35 and API36 tests failed before the fix** with:

```
java.io.IOException: Failed to create directory for ...enc.new
    androidx.core.util.AtomicFile.startWrite
    EncryptedSnapshotFile.write
    MaintenanceGate.write
    EditorDraftStore.save
    PersistEditorDraft ... invokeSuspend
```

This proves a real error-containment bug. It does **not** prove that this phone had that filesystem condition, a KeyStore failure, or that every reported repeat-logo termination shares this cause.

The production change catches recoverable Exceptions at the optional editor-autosave boundary, logs the failure without including draft content, and shows a save-failure toast once per failure episode. Its warning flag is not Compose UI state, so updating it during disposal does not request UI recomposition. Current input remains in the editor; atomic storage keeps the last successfully saved encrypted draft. A later edit retries saving, and successful saving resets the warning episode. The same handling covers automatic save, ON_STOP and disposal. Strict storage writes, restore journals, database/key guards and encryption behavior are unchanged; this does not hide failed database/restore operations or replace encryption with plaintext.

The patch intentionally does not redesign synchronous draft I/O. Long KeyStore/filesystem stalls or native faults still require a device trace; catching a recoverable save exception does not resolve an ANR, OS kill or native crash.

### Executed bubble checks

**115 focused tests passed, zero failures/errors/skips**, including:

- API35/36 controlled autosave write failure: text remains usable, prior encrypted bytes survive and disposal is safe.
- API35/36 recovery: a later edit successfully saves after storage becomes writable again.
- Actual ChatInterface on API36: unsent Unicode/multiline input in Talk and ordinary Chat modes is saved as a draft without being added to conversation history.
- Bubble visibility/recovery tests extended to API36: Settings broadcasts, chat open/close, permission revocation/regrant, snooze and service recreation.
- Existing chat transcript cold-load/restore test, conversational planner/draft ownership tests and SmartDateParser coverage.

Typing updates the composer and live date preview; assistant response generation starts on Send, so an unsent-keystroke crash need not originate in the conversation engine. Opening chat uses a separate translucent activity in the same application process, so Settings remains behind it. A fatal chat exception therefore terminates the Settings activity and bubble service too; separate task affinity does not isolate their processes. The Settings footer considers STARTED to be foreground and may keep animating while paused under that translucent activity; this is a performance consideration, not an established crash cause, and was not changed.

Evidence: `draft-failure-before.log` and `.xml` preserve the failing reproduction. `bubble-focused-tests.log` and `bubble-focused-results.zip` preserve passing follow-up checks. **Final full regression: 1,488 tests across 109 suites passed, with zero failures/errors/skips; debug assembly passed.** The batch includes the existing unreleased widget work as well as these fixes. No signed release has been published.

No version bump, signed hotfix or GitHub publication has occurred. A debug-signed test APK must not be presented as a drop-in update to this user's release-signed installation; preserve the release signing identity and app data for any delivered hotfix.


### Regression-run environment notes

The initial extended batch hit Compose idling timeouts in the new draft UI tests. Those tests now use native graphics consistently with the other UI tests, and the diagnostic startup controller explicitly disposes its Compose roots before later tests register idling. The combined startup/composer sequence passed after these test-isolation changes.

A subsequent extended batch using the existing cloud JDK21 C1 workaround exhausted the JVM code cache (`VirtualMachineError: Out of space in CodeCache for adapters`). That run was terminated rather than treating downstream VM errors as app failures. The final verification uses a temporary Gradle init script setting test-only `TieredStopAtLevel=1`, `ReservedCodeCacheSize=512m` and `forkEvery=20`. Assertions, production APK compiler/runtime and packaging are unaffected. The script and final results are retained with the evidence.

Final proof: `final-full-test-results.zip`, `final-regression-build.log` and `final-validation.json`. The final run completed successfully in 7m 58s with all tests/assertions enabled. The targeted production patch is `bubble-autosave-fix.patch`; it was checked against the workspace with `git apply --check --reverse`.
