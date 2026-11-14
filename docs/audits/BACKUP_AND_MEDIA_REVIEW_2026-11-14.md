# Backup, restore and note-media review — 14 November 2026

Reviewed baseline: `5aa68f8`. The findings and flow descriptions below record that
baseline. The implementation follow-up at the end describes the subsequent fixes
and validation. This is not a physical-device speed benchmark or a certification
of every document provider/OEM.

## Findings and priorities

| Priority | Finding | Evidence / consequence | Recommended action |
| --- | --- | --- | --- |
| High | Article-image import falls back to an external URI when local processing fails. | `KeepNoteEditor.kt:629,651` inserts `uri.toString()` on a null import result. `NoteMediaInventory` includes that reference, while `BackupRestoreManager.buildMediaManifest` requires an owned local file. A note can appear to contain an image but later lose access or prevent a complete portable backup. The backup fails rather than silently omitting it. | Show an import error, preserve the draft and permit retry; insert only a durably verified owned attachment. Check editor-session identity before asynchronous completion. |
| Medium | Empty chat snapshots do not replace current chat history. | `BackupRestoreManager.kt:1298` wraps both creation and clean-mode clearing of chat messages in `chatArray.length() > 0`. A valid empty chat snapshot leaves local messages behind. | Clear in clean mode even when the input array is empty; keep merge behavior separate. Add empty/nonempty and repeated restore regressions. This finding is source-confirmed, not newly reproduced on a device. |
| Medium | Image orientation is incomplete. | `NoteImageHelper.kt:107–113` handles rotation and horizontal/vertical flips but not EXIF transpose/transverse (5/7). Those inputs reach the default untransformed path. | Handle all eight EXIF orientations and test mirrored portrait fixtures. |
| Quality limitation | All image imports become JPEG at quality 80 with a maximum 1920-pixel edge. | `NoteImageHelper.kt:22–23,129,177`. JPEG is lossy and cannot retain alpha; flattening also discards animation and original resolution. Good for ordinary photos, less suitable for transparent drawings, screenshots and scanned text. | Choose encoding by content/alpha; distinguish an optimized image from an original file attachment. Never recompress existing user files automatically. |
| Quality limitation | Camera capture requests a preview bitmap. | `KeepNoteEditor.kt:667` uses `TakePicturePreview`, so image quality is limited by the camera-returned thumbnail before the 1920-pixel policy applies. | Capture to a narrow, temporary FileProvider destination, then import/encrypt and remove temporary data; handle cancellation/process death. This needs careful ownership and cleanup tests. |
| Medium | Some post-restore derived-state failures are hidden from the completion result. | Reminder rescheduling logs exceptions; several manager/widget refreshes suppress them (`BackupRestoreManager.kt:1455–1497`). Data restoration can report success while a reminder or widget refresh needs retry. | Keep committed restored data, show a partial-success warning and schedule durable reconciliation; do not roll back sound data solely for a launcher refresh. |
| Low | Backup metadata reports a fixed app version. | Backup creation uses `appVersion = "1.0.0"` (`BackupRestoreManager.kt:250`), rather than the build version. | Use build metadata; keep the archive format version separate. |
| Performance | Backup creation performs two full post-write verification passes. | `createEncryptedBackupLocked` reads/decrypts ZIP/media, then `createVerifiedEncryptedBackup` calls the full restore-compatible verifier. Each opens the password-derived stream again. | Combine their requirements in one authoritative full verifier. Preserve authenticated EOF draining, archive limits, manifest validation and every attachment digest; remove redundant traversal, not integrity checks. |

No claim is made here that all other paths are bug-free. Existing regression
coverage and retained security boundaries are useful evidence but do not replace
fault-injection or physical-device acceptance.

## What is backed up

There are two distinct systems:

1. Portable manual/automatic full backups: selected database records, portable
   preferences, assistant planning state and referenced attachment bytes. Manual
   files use a chosen password; automatic files use a generated portable recovery
   credential. This is a logical export, not an APK/system-permission/keystore clone.
2. Internal daily/pre-operation safety snapshots: encrypted, device-bound records
   and media references. They do not contain attachment bytes and are not sufficient
   for device loss, uninstall or deleted attachments (`DataSafetyManager.kt:45–60`).

The full archive is ZIP content encrypted with AES-256-GCM, random salt/nonces and
Argon2id (64 MiB, three passes, parallelism one). Only current outer format v4 is
accepted. A vault PIN and a portable backup credential have separate purposes.
Automatic vault authorization is saved wrapped under the backup configuration;
normal scheduled creation does not require opening/unlocking the Archive screen.
Missing, revoked or stale authorization fails the complete capture, retaining
previous backups. Private notes are not silently excluded.

## Creation and automatic publication

```text
Read consistent saved records and authorize private-note access
  → authenticate/hash referenced local attachments
  → build and validate logical manifest
  → write ZIP through archive encryption
  → read/decrypt and verify archive/media
  → read/decrypt again with full restore-compatible verifier
  → automatic: retain encrypted local stage and its digest
  → copy to chosen folder as an incomplete document
  → read back and compare ciphertext length/SHA-256
  → rename (or copy to final document if rename unavailable), verify
  → persist ownership record
  → verify newest retained copy before pruning owned old copies
  → record success, remove local retry stage
```

Already-compressed images are currently passed through default ZIP deflation.
Testing lower/no ZIP compression for compressed media can reduce CPU work without
changing image quality or archive encryption. JSON/text should retain useful
compression. Measure archive size, time and compatibility before choosing a policy.

Backup creation holds the maintenance consistency gate while gathering data,
writing and performing its first verification. Other guarded data writes may wait
behind this work. Shortening that critical section requires a properly pinned
snapshot/media ownership design; simply releasing it earlier risks inconsistent
exports or concurrent cleanup. This is a performance candidate, not a measured
phone UI stall in this review.

| Scenario | Current intended handling / limitation |
| --- | --- |
| App not opened | WorkManager's 15-minute due-time checker can run in the background, subject to battery/storage/charging and Android restrictions. Daily is a due schedule, not an exact-time guarantee. |
| Swipe away / process killed | WorkManager can reschedule; behavior is OEM-dependent. A backup is not marked successful until verified publication/bookkeeping finishes. |
| Force-stop | Android stops jobs until the app is launched again. This differs from removing a task from Recents. |
| Reboot | Work can resume after normal startup/unlock; hardware keys and providers must be accessible. |
| Missing folder permission/provider/storage | The run fails/retries and preserves prior protected copies; setup may need folder selection again. |
| Cloud destination offline | Provider behavior determines availability; the encrypted local stage can be retained for retry. |
| Vault locked | Works with previously valid explicit private-vault backup authorization. New/stale authorization needs the PIN/credential flow. |
| Missing/corrupt attachment | Complete capture fails rather than making an apparently complete backup without that attachment. |
| Wrong password, tampering or unsupported development format | Reader rejects it. Original archive remains available. |
| Interrupted publication | Incomplete/new documents are cleaned when possible; death can leave an unmanaged document. Unknown files are not deleted by scanning names. |
| Destination lacks rename | A second final copy is written and verified, increasing I/O and temporary storage. |
| Retention failure | Older files remain; status can report verified backup plus cleanup warning. |
| Retry after a failed run | A matching, unexpired authenticated encrypted stage can be reused. A distinct explicit request captures fresh data. |
| Pause / change folder or key | Pending work carries scope/epoch checks. Previously published files are not retroactively erased. |
| Uninstall / clear app data | Local keys/config/internal snapshots are lost. Recovery needs an accessible portable backup and its external credential; permissions must be selected again. |

A blocked provider call or a process killed before a `finally` block runs can leave
temporary work/status behind. No design can promise a completed backup at the
instant of arbitrary device failure; the important boundary is preserving older
verified copies and reporting the actual capture time.

## Restore semantics and attachment handling

```text
Select archive and credential / authorize saved recovery key
  → derive key, authenticate and unpack into encrypted staging
  → validate manifest, limits, paths, references and all attachment hashes
  → review date/counts and choose snapshot or explicit extra-copy import
  → authenticate current vault, or configure one for incoming private notes
  → write/read-back encrypted rollback journal and note safety snapshot
  → authenticate staged media, allocate fresh attachment destinations
  → restore databases/preferences and remap attachment references
  → install encrypted media with verified moves/copy fallback
  → refresh managers, reminders and widgets
  → durable commit marker; lock vault; discard prepared staging
```

Snapshot mode logically replaces the covered app records/settings; later local
changes can be lost after the replacement confirmation. It does not replace the
APK or every file on the phone. The empty-chat exception above means it is not
currently perfectly consistent replacement in all covered stores.

Extra-copy import intentionally adds records and may also apply backed-up settings;
it is not a content-based deduplicating merge. Repeating it can produce duplicates.
Snapshot mode is the appropriate path for restoring an earlier saved state without
appending new copies each time.

Media entries are decrypted from the portable container and immediately staged with
this installation's media encryption. Fresh `import_UUID_...` names avoid filename
collisions and overwriting originals. Notes' URI fields are remapped; surrounding
rich content is preserved. Images are not decoded or recompressed during restore,
so restore preserves the imported bytes, not the pre-import camera original.

Original attachments remain available for rollback/recovery; later conservative
media cleanup determines reclamation. Repeated restores may therefore increase
physical storage even when note counts do not increase. Do not aggressively delete
old files before commit, during locked-vault ambiguity or while recovery can use them.

Preparation expires after ten minutes; leaving the app during checking/review/PIN
steps discards it. During application, cancellation/failure uses the durable journal
and startup recovery. This is multiple database transactions coordinated by recovery,
not one atomic filesystem-wide transaction. If recovery itself cannot finish,
maintenance is blocked with the encrypted journal retained. Device loss or damaged
storage can still defeat recovery.

## Image display and speed

Note tiles use Coil AsyncImage. The custom encrypted File fetcher authenticates and
decrypts the entire compressed image into memory before decoding. Coil can decode
to display constraints and use memory caching, but a cold small-card load still
needs the complete encrypted source; there is no dedicated encrypted thumbnail
pipeline in these reviewed paths. Card previews limit display to four images;
the editor's collage uses eager rows and caps its layout at 50 images.
Attachments beyond that cap are not shown by this collage, even though the
underlying note list can contain more; do not confuse this UI cap with deletion.

Encrypted thumbnails for list tiles, bounded concurrent reads, lazy editor rows,
and explicit load/error/retry states are promising improvements. Cache identity,
vault relocking, deleted/replaced files and restore invalidation must be tested.
Never speed up rendering by writing decrypted thumbnails or bypassing GCM tags.

## Timing and validation limits

No device-specific timing for the user's approximately 7.8 MB archive is available.
Archive size alone does not predict duration. Creation currently derives the
archive key three times (write plus two verifier openings); restore opening derives
it once, plus vault PIN work if required. Full export/restore read/write passes,
attachment count, encrypted staging, fsync, rollback-journal size and provider
latency also matter. Existing live progress is phase/item based, not a measured
percentage or ETA.

A small incoming backup can still restore slowly if the current local store is
large, because rollback protects the current state too. Free-space planning must
include the archive/staging, new attachment copies, current data and recovery files;
it cannot be inferred from compressed archive size alone.

Recommended instrumentation: monotonic per-phase elapsed time, bytes/items and
cold/warm image decode timings, without names, paths, note text, PINs or credentials.
Benchmark local-folder and representative cloud providers on physical hardware.
Maintain KDF strength and all durable/integrity checks while optimizing passes.

Fresh focused JVM/Robolectric regression run passed **100 tests,
0 failures, 0 errors, 0 skipped** across
AutomaticBackupsTest, RestoreFlowSafetyTest and EncryptedMediaReadSafetyTest.
The Gradle command completed successfully, using the documented cloud C1 test-JVM
workaround and one worker. The initial sandbox attempt could not open the Gradle
daemon socket; the permitted retry outside that sandbox ran successfully.
Source security contracts, chat-boundary checks and whitespace checks passed.

These existing tests protect retry/retention, authorization, snapshot repetition,
rollback, tampered staging and authenticated reads. They are not new regression
reproductions of every finding above. No phone/provider speed measurement,
physical-device camera/EXIF acceptance or whole-project test pass is claimed.
Logs: `/workspace/scratch/backup-review-tests.log`. The approximately six-minute
Gradle/build/test run is tooling time, not the time a user backup or restore takes.


## Implementation follow-up — working tree

The findings above describe the reviewed baseline. The follow-up implements:

- One full restore-compatible post-write verifier in place of two archive reads.
  The required GCM EOF check, full schema/limit validation and attachment hashes
  remain; the destination publication/read-back/retention checks are unchanged.
- No ZIP recompression for common already-compressed media; text/JSON still use
  normal compression. This changes ZIP compression choices, not the outer v4
  crypto format, KDF settings or credentials. Backup app-version metadata now uses
  the actual build version.
- Empty-chat replacement clears local chat rows; extra-copy import retains them.
  A regression checks both modes, repeated replacement and one verifier opening.
- Restored media uses fresh UUID names with a bounded extension, preventing names
  from growing past filesystem limits after repeated backup/restore cycles. Rich
  attachment display names and bytes are preserved. Archive output explicitly
  requests truncation (`wt`), so writing a smaller archive to an existing document
  does not retain an old ciphertext tail. Full verification still rejects bad output.
- Failed optimized-image imports show feedback instead of storing external URIs.
  Asynchronous article imports validate the current editor session, and discard
  their own unused newly created file after cancellation/session replacement.
- Image imports are serialized to bound peak decode memory, and every internally
  allocated bitmap is recycled once, including resized images without rotation.
- All EXIF orientations, including transpose/transverse, are supported. PNG/alpha
  images use lossless encoding rather than JPEG. The original-file picker keeps
  all source bytes, including original resolution/animation, as an attachment.
  Optimized inline images remain bounded by the 1920-pixel import policy; known
  GIF inputs direct the user to original-file attachment instead of flattening.
- Camera capture uses a full-resolution, URI-scoped temporary destination rather
  than TakePicturePreview. The importer encrypts the processed image and removes
  the temporary original. Cancelled/stale results cannot attach to another editor.
  Startup and delayed cleanup remove abandoned camera files after 24 hours; they
  never scan saved notes/media. Android/OEM job restrictions still affect timing.
- Private-note camera/gallery/file requests live at the Notes screen level, so
  vault relocking and editor closure do not lose the external activity's result.
  The editor is saved/sealed before opening the picker; no vault key or private
  content is retained in continuation metadata. The initial implementation required
  a fresh PIN for every return; the bounded grace policy below supersedes that
  behavior. Session, storage generation, opaque row fingerprint and
  operation-specific result registration prevent delivery to a different note or
  an outdated request. An unrelated open draft is preserved. Prepared images are
  encrypted before the PIN prompt; the prompt explains that they appear above the
  private note, preserving article body formatting. Cancellation retires unused
  media conservatively, and saved-state replay cannot duplicate committed images.
  Selection metadata expires after 24 hours and carries no authorization itself.
- Authenticated encrypted thumbnails in app cache: 480px card previews and 960px
  editor previews, bounded to 128 entries/32 MiB and two concurrent producers.
  A complete source ciphertext fingerprint and an authenticated binding prevent
  same-size corruption or substituted cache files from serving another image.
  Requests disable Coil plaintext disk and decoded-memory caching for these
  previews. Cache hits avoid original decryption/decoding, not source-integrity
  reads. First generation has a cost; no handset speed-up percentage is claimed.
  Preview generation binds its cache identity to the exact authenticated source
  snapshot, including when a source is atomically replaced during a read. Article
  previews retain their aspect ratio, captions and original-image open action.
- Editor image pages contain at most 12 tiles and can reach attachments beyond
  the previous 50-image layout cap. The surrounding note scroll remains intact.
  Loading indicators and retry controls retain the existing Material styling.
- Backup/restore show a monotonic elapsed clock for the visible step. Debug-only
  phase-duration logs contain fixed phase codes and milliseconds, never the
  progress message, note text, paths, PINs or credentials. No telemetry is sent.
- Automatic backup settings show loading before presenting setup/current controls.
  UI tests wait for the actual loaded state instead of assuming IO is complete
  when Compose becomes idle.
- Restore completion reports derived-state refresh warnings. A durable worker
  retries manager/reminder/widget reconciliation without reapplying the archive
  or changing restored records. The vault and recovery journal remain protected.

Existing imported image bytes are not rewritten. Existing broken external image
references are not silently migrated or removed; affected notes need a deliberate
reattachment from an accessible original. The format-v4 development-version policy,
backup/vault credential separation and approximate Android schedules are retained.

Physical-device camera/provider behavior, OEM background delivery and actual
7.8 MB backup/restore speed still require device measurements. Verification below
records local checks; it is not whole-device certification.

## Initial implementation verification (superseded by the follow-up below)

The final targeted JVM/Robolectric run passed **213 tests across 15 suites, with
zero failures, errors or skipped tests**. It covers automatic scheduling/publication
and portable recovery; manual backup and restore UI; replacement, extra-copy import,
repeated restore, long filenames, empty chats, rollback and tampered staging;
authenticated media reads and encrypted previews; real FileProvider capture and
native image transformations; article rendering and image pagination; archive move
policies; and private-media continuation, fresh PIN verification, stale-session
rejection and committed-image replay prevention.

New coverage uses SDK 31 and 34 for restore/private-continuation logic and SDK 34
native graphics for image/Compose cases. The documented cloud C1 test-JVM workaround
does not change production compilation or skip assertions. Source security
contracts, chat-boundary and dictionary checks pass. The debug merged manifest
retains no camera, network or broad storage/media permission, disables Android
system backup, and keeps the FileProvider unexported with URI grants restricted to
the existing export/diagnostic and new temporary capture subdirectories.

Test evidence: `/workspace/scratch/backup-implementation-final-tests.log` and
`/workspace/scratch/backup-implementation-final-results.json`. These are environment
logs rather than files shipped with the app. This is focused regression coverage,
not a claim that every repository test or every physical device was exercised.

Full `:app:lintDebug :app:lintRelease` passed with **zero errors/fatal findings**.
Debug reports 246 warnings and 57 informational findings; release reports 244
warnings and 57 informational findings. This is not a warning-free repository.
Two `MutableCollectionMutableState` warnings concern the continuation's saveable
`ArrayList` state: all updates replace each collection, and no in-place mutation is
used, so those warnings do not establish a stale-UI bug. Other warning categories
include obsolete SDK checks, resource/icon organization and dependency suggestions.
The compiler recovered from one daemon startup retry; the command exited zero.
Evidence: `/workspace/scratch/backup-implementation-final-lint.log` and the debug/
release lint reports under `app/build/reports/`.

`:app:assembleDebug :app:assembleRelease :app:assembleDebugAndroidTest` completed
successfully, including R8 optimization of the unsigned release APK. Packaged
debug and release manifests contain no camera, network or broad storage/media
permission. Release mapping retains both new WorkManager classes and their required
constructors. All 1,028 captured source/build fingerprints still match the tested
inputs; final source guards and whitespace checks pass. Only documentation changed
while the final pipeline ran.

Build evidence: `/workspace/scratch/backup-implementation-final-build.log`;
APK sizes and SHA-256 digests: `/workspace/scratch/backup-implementation-final-apks.json`.
ADB reported no connected devices. Device instrumentation was compiled but was not
executed; physical camera/provider timing, Android KeyStore recovery and OEM
background-delivery acceptance remain device checks. No version bump, publication
or GitHub push was part of this implementation.

## Coverage and identity follow-up

A subsequent question about exact feature coverage prompted a separate audit of
the working tree. The initial coverage audit below found five omissions. They have since been fixed
as described after the findings; the earlier 213-test result predates these fixes.

| Data | Portable manual/automatic backup coverage |
| --- | --- |
| Notes, archive and trash | Saved note records, checklist/labels/formatting, trash timestamps and referenced media bytes are included. Private notes require authorization and are encrypted for the destination vault on restore. |
| Tasks and habits | Saved records, completion history, habit logs/frequency, task planning, routines/templates and countdowns are included. |
| Vocabulary | Learned timestamps, mastery, bookmarks and linked word definitions/sense links are included. Word identity is matched by text/definition/part of speech rather than assuming numeric IDs match another installation. |
| Completed drills and aptitude profile | Session/result JSON, claimed status, saved aptitude XP, counts, streaks and daily statistics are included. |
| Main profile | Name, role/bio, XP/gold, purchased/selected avatars, stored focus statistics and Enclave identity preferences are included. Profile-specific pinned-note selections are now included and remapped. |
| Assistant | Chat history, plan workspaces, saved templates/discussions, explicitly saved facts and planning-library preferences are included. Chat personalization in `ayva_personalization` is now included. Live conversational authority, pending approvals and undo receipts are deliberately reset. |
| Blocking | Package restrictions, limits, quotes, schedules and allowlisted focus/unlock/tracker preferences are included. Schedule `isEnabled` is now preserved; older missing flags restore paused. Android usage-event history and system permissions are not portable app data. |

The preference list is explicit (`BackupRestoreManager.PREF_FILES`), not a scan of
every preference store. Manual and portable automatic creation share this payload.
Internal daily/pre-operation safety snapshots are a separate, narrower system.

### Gaps confirmed by the initial coverage audit (now addressed)

1. `AssistantPersonalization` reads/writes `ayva_personalization`, which is absent
   from the portable preference list. Preferred name, tone, brief replies, quiet/
   question/suggestion choices, default focus duration and plan-size/order choices
   are not recovered on a fresh installation. On the same installation, existing
   values survive a replacement restore instead of matching the archive.
2. Portable schedule serialization omits `FocusSchedule.isEnabled`, and restoration
   uses the constructor's `true` default. A schedule disabled at capture is enabled
   after restore. The internal safety-snapshot path already preserves this flag;
   it does not repair the portable path's omission.
3. `AccountScreen` stores profile pins in `profile_pinned_notes_prefs`, outside the
   backup list. The notes themselves and their ordinary `isPinned` flags are backed
   up, but these separate Account-screen selections are not.
4. Extra-copy import allocates new note IDs but copies `note_widget_prefs` without
   remapping `note_specific_id_*`/`note_current_id_*`. An imported specific-note
   widget setting can point to the existing local note instead of the imported
   copy, or to an absent note. Android widget host IDs/placement also cannot be
   presumed portable between installations. Other secondary ID consumers should
   receive an explicit mapping review before claiming universal merge safety.
5. `active_drill_state` is neither exported nor cleared by a successful replacement
   restore. A current unfinished drill can remain beside restored historical/profile
   data. This confirms mixed-state behavior, not a reproduced reward-duplication
   exploit. Restore needs an explicit policy for stopping/resetting live drills.

### IDs, deletion and media behavior

- Within an existing database, ordinary new note/task/habit/schedule records use
  SQLite auto-increment identities. Ordinary deletion does not reset that sequence.
  Snapshot restore deliberately reinstates archived IDs and removes covered rows
  absent from the snapshot; extra-copy import creates fresh numeric IDs for these
  records. Task dependencies, assistant task links, habit logs and routine chains
  have explicit mapping paths. Drill/chat records instead upsert their existing
  string IDs; app restrictions use package names. Imported settings overwrite
  colliding keys, so extra-copy import is not a universal preserve-both merge.
- Media filenames are separate from note IDs. Restore writes fresh bounded UUID
  filenames, re-encrypts for this installation, and remaps header/audio/embedded
  references. Referenced files shared by notes use the same destination mapping;
  live originals are retained for rollback rather than overwritten.
- Trash retains references. Permanent deletion/image removal can retire files;
  cleanup requires a fresh, complete reference inventory and preserves files when
  private references or pending recovery cannot be verified. Creation/undo leases
  and conservative retirement can retain encrypted files longer and consume space.
- Older portable backups retain their own attachment bytes. Deleting a local image
  does not delete its bytes from those backups; restoring an older snapshot can
  intentionally bring deleted content back. A backup made after permanent deletion
  does not contain that unreferenced image. Internal reference-only snapshots cannot
  recover physically deleted attachment bytes.

Fresh verification: 40 existing media/planning/productivity cases passed, plus four
disposable audit probes that assert the observed behavior above, including the
confirmed defects. The probes also recover a physically deleted original image,
verify its exact bytes under a new filename, and check that subsequent note IDs
remain above the existing database's allocation high-water mark. These original passing
diagnostic assertions documented the omissions; permanent corrected regression
tests now check the repaired behavior below.

Evidence: `/workspace/scratch/backup-coverage-checks.log` and
`/workspace/scratch/backup-coverage-results.json`. The probe source and temporary
Gradle source-root configuration live under `/workspace/scratch/`, outside the repo.
All 1,028 previously validated source/build fingerprints remain unchanged.


## Coverage, media-return and chat-control implementation follow-up

The five confirmed coverage gaps are addressed in both portable manual and
automatic backups:

- Assistant personalization, Account pinned-note selections, and chat update/color
  settings are included in the explicit portable preference allowlist and the
  encrypted rollback journal. Known personalization value types and bounds are
  validated before any data replacement.
- Paused schedules stay paused. Current-format archives created before this
  change cannot recover an omitted flag; unknown flags restore paused with a
  warning. Missing assistant personalization keeps current choices with a warning;
  missing Account pins clear those selections during replacement.
- Extra-copy import remaps profile pins and specific/current-note widget bindings
  to the new note IDs. A nonportable installation identity distinguishes Android
  widget host IDs: a foreign/older archive cannot attach its host IDs to local
  widgets. Replacement disconnects affected bindings and reports a reconnect
  warning. This does not restore launcher placement or operating-system permissions.
- Portable restore ends running drills in both modes, retaining completed results
  according to the selected restore mode. The review screen states this policy.
  A durable restore commit invalidates older live sessions, including their reward
  settlement; a failed restore rolls back the active drill and keeps it retryable.
  Unrelated internal maintenance does not invalidate a drill. Failed restores also
  reconcile briefing/practice alarms with recovered preferences, including both
  enabled-to-disabled and disabled-to-enabled attempts. Restored chat rows determine
  the unread badge, avoiding a stale count from replaced history.

### Private attachment return

An already-authorized private editor can return from camera, gallery or original
file selection without another PIN **within two minutes**. The editor is sealed
before leaving; the global vault stays locked while outside the app. One
process-local, one-use authorization is bound to the note, saved encrypted-row
fingerprint, storage generation, credentials and monotonic deadline. Its owned key
bytes are wiped on expiry/consumption/revocation. No key or PIN is persisted in
selection metadata.

Screen-off/device lock, a normal explicit vault lock, changed credentials or
restored storage, expired time or lost process/session require a fresh PIN. A
quick cancelled selection can return to the original note through the same
bounded grant. Stale/replayed results and an unrelated editor are still rejected.
Prepared attachments stay encrypted and retain the existing conservative cleanup
rules. The PIN fallback explains why authorization is needed; successful image
return identifies where the images were added.

### Automatic assistant updates

The three-dot menu is now **Chat updates & colors**. A master switch and seven
categories control briefings, practice/streak nudges, screen-time reminders,
bedtime reminders, focus alert previews, automatic quest/reward prompts and other
automatic cards. Disabling a stream rejects future automatic cards before chat
storage or unread increments, as well as suppressing its preview. Structured
message kinds/category IDs take precedence over text inference. Practice controls
share the existing setting and honor Vacation Mode. Briefing/practice alarms are
cancelled/rescheduled with their effective settings; midnight maintenance remains.
Master pause preserves individual choices, and settings changes retain scroll
position in the dialog.

Existing history is retained; direct replies, explicitly requested cards, completed
drill results and earned rewards remain available. Blocking itself stays active.
The dialog points out that task/habit reminders are managed separately in Settings.
Queued producers carry a chat-generation check so a producer from replaced history
cannot append against a newly restored transcript.

### Final follow-up verification

Latest per-class results cover **368 tests across 31 suites, with zero failures,
errors or skips**. The broad run passed 367 cases. After the final small settings
and completion-dialog changes, seven affected suites passed 51 cases; the late
alarm-recovery correction then passed 86 cases across five recovery/drill/security
suites, including its additional fault-injection case. The total uses each class's
latest report, not the sum of repeated runs. This is selected regression coverage,
not an all-repository or physical-device pass.

The corrected coverage suite checks personalization/profile/pins, paused schedules,
alert settings, extra-copy ID links, restored image bytes and allocation high-water
marks, foreign widget hosts, missing settings in older current-format backups,
pre-mutation rejection of invalid types, running-drill reset, older-session reward
rejection, rollback recovery, and recovered alarm scheduling in both directions.
Private continuation covers SDK 31/34 expiry, key-buffer wiping, screen-off,
stale/replayed results and changed storage. Native Compose cases exercise quick
camera, gallery and original-file return, expiry/lock PIN fallback, cancellation,
exact file bytes, and private note identity/content. Chat cases check storage and
unread suppression, direct replies, retained history, Vacation Mode, master/category
choices, structured classification, stale producer generations and Reset All.

Evidence outside the repo:
- `/workspace/scratch/backup-chat-comprehensive-tests.log`
- `/workspace/scratch/backup-chat-final-ui-tests.log`
- `/workspace/scratch/backup-chat-final-recovery-tests.log`
- `/workspace/scratch/backup-chat-final-results.json`
- `/workspace/scratch/backup-chat-final-source.json` (1,033 frozen source/build inputs)

The cloud C1 test-JVM workaround does not skip assertions or alter production APK
compilation. Physical camera/provider behavior, actual handset speed, Android
KeyStore recovery and OEM background delivery still need device acceptance.


Full `:app:lintDebug :app:lintRelease` passed with **zero errors/fatal findings**.
Debug reports 245 warnings and 57 informational findings; release reports 243
warnings and 57 informational findings. This is not a warning-free repository.
The two earlier continuation `MutableCollectionMutableState` warnings are gone:
saveable state is typed as read-only lists and replaced on each update. One new
`StaticFieldLeak` warning points to `MediaReturn.context`: construction supplies
`context.applicationContext`, never an Activity, and revocation clears the lease,
receiver and timer. Retaining this application context for the bounded operation
does not retain a screen; the diagnostic remains visible without suppression.
Other reported warnings include existing SDK checks, legacy EXIF API and UI/style
suggestions. No lint rule or assertion was disabled for this implementation.
Evidence: `/workspace/scratch/backup-chat-final-lint.log`,
`/workspace/scratch/backup-chat-final-lint-results.json`, and preserved XML reports.

`:app:assembleDebug :app:assembleRelease :app:assembleDebugAndroidTest` completed
successfully, including release-vital checks and R8 optimization of the unsigned
release APK. Packaged debug/release manifests each have 15 permissions and retain
no camera, network or broad storage/media access. System backup is disabled and
the FileProvider is unexported, with URI grants. Source paths remain restricted to
export, diagnostics and temporary camera directories. R8 mapping retains both
`CaptureCleanupWorker` and `RestoreRefreshWorker` and their Context/WorkerParameters
constructors.

All 1,033 frozen source/build fingerprints match after validation. Source security
contracts, the 118-file chat-boundary check, dictionary integrity and whitespace
checks pass. There are no unmerged files. Build evidence is in
`/workspace/scratch/backup-chat-final-build.log`; APK sizes and SHA-256 digests are
in `/workspace/scratch/backup-chat-final-apks.json`.

ADB reports no connected device/emulator. The device-test APK was compiled, not
executed. Handset camera/provider behavior, actual timing, hardware KeyStore and OEM
background acceptance remain unverified here. Version remains 2.0.46 (146); the
implementation is local, with no version bump, commit or GitHub push for this task.


## Subsequent source cleanup

A later cleanup consolidates read-only verification onto the same `prepareRestore`
reader used before restore, replacing the duplicated decrypt/unpack/validate loop.
Verification closes its staging and does not apply any records. The [cleanup
follow-up](CODE_CLEANUP_REVIEW.md#14-november-follow-up) records scope and fresh
validation; the counts and frozen inputs above describe the preceding checkpoint.
