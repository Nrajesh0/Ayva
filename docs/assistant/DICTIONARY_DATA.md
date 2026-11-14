# General dictionary removal and vocabulary preservation

The general Wordset dictionary is no longer bundled. Its 256 compressed shards,
manifest, editorial data, runtime lookup store and import/check scripts are removed.
The removed asset directory occupied 9,541,647 bytes before APK packaging. Current
CI no longer invokes its deleted checker. This does not measure the APK saving.

## Preserved data and features

- `assets/vocab.db` is byte-identical. The native 1,773 idioms and 2,027 OWS entries,
  learning, mastery, missed-word flags and quizzes remain.
- Room entities, schema versions and migrations are unchanged. Existing saved
  dictionary words are ordinary native OWS rows; their bookmarks, progress and
  source links remain. Backup/restore still exports and remaps those records.
- Existing native vocabulary lookups and reviewed bookmark/undo actions remain.
  General meaning-query aliases search this vocabulary rather than inventing a
  definition. Unknown words get a bounded unavailable response.
- Word-chain and anagram games use authored game words plus native vocabulary.
  Their coverage is smaller. Rules explain this; unknown words remain unscored and
  users can skip. Native queries suspend rather than blocking the UI thread.
- Authored sentence-pattern explanations remain independent of dictionary data.
- Old dictionary controls and persisted save actions fail closed without accessing
  deleted assets or modifying vocabulary. Old saved rows are not removed.

## Retained attribution

A small `assets/licenses/Wordset-NOTICE.txt` notice remains for previously saved
Wordset entries; `dictionary credits` can show it. Wordset data uses CC BY-SA 4.0,
with the underlying WordNet notice. This notice is not a bundled lookup dataset or
an additional app capability. It does not relicense independent application code.
The historical `vocab.db` has no separate redistribution-provenance record in
this repository; that existing documentation gap is unchanged. The Wordset notice
does not assert a licence for the original vocabulary asset. Historical audits
refer to the dataset available at their dated checkpoints.

## Validation

Latest per-class results cover **563 focused tests across 23 suites, with no
failures, errors or skips**. A broad run passed 562 cases and exposed one assertion
for the previous capability-help wording. The updated capability and vocabulary
suites passed all 37 cases on rerun; the total uses each suite's latest report,
without counting repeats twice. Earlier integration checks also caught a helper
name collision and an obsolete dictionary-producing isolation fixture; both were
corrected before this final regression result.

Coverage includes native asset row counts, saved-word bookmarks/mastery/timestamps,
quiz access and undo; retired controls/action validation; authored and native game
words; sentence patterns, assistant routing/permissions, task/planning behavior,
Notes isolation and saved-word backup/restore. The vocabulary asset and original
request grammar were also compared byte-for-byte with the prior commit. No Room
schema, backup implementation, crypto dependency or blocking-service changes were
made. Test workers used the documented cloud C1 workaround; assertions were not
skipped. This is selected regression coverage, not a physical-device certification.

Evidence outside the repository: `/workspace/scratch/dictionary-removal-regressions.log`,
`/workspace/scratch/dictionary-removal-final-recheck.log`,
`/workspace/scratch/dictionary-removal-final-results.json`, and
`/workspace/scratch/dictionary-removal-final-inputs.json` (829 frozen build/app/tool
inputs). Source guards pass, and workflows no longer reference deleted tools.
Full `lintDebug`/`lintRelease` passed with zero errors/fatal findings (245/243
warnings and 57 informational findings each). That scan preceded the final
one-word authored-bank correction and version metadata bump. The correction
aligns the legacy bot word “easy” with the accepted game bank; 27 focused game and
vocabulary cases passed again, preserving the 563-case latest-per-class total.
The versioned build subsequently reran release-vital checks and R8 optimization.

Debug, optimized unsigned release and device-test APK assembly passed for
**2.0.47 (147)**. APK metadata and packaged manifests both report this version.
Debug is 53,679,687 bytes; release is 34,931,012 bytes. These are final artifact
sizes, not an isolated before/after benchmark. Both production APKs have no
`assets/dictionary/` entries, retain the exact original `assets/vocab.db` and source
notice, and request no camera, network or broad storage/media permission (15
permissions total). Debug DEX also has no old dictionary/store classes.

All 829 final source/build/tool fingerprints match. No device/emulator was
connected; device-test assembly does not establish physical-device execution.
Evidence: `/workspace/scratch/dictionary-removal-final-lint.log`,
`/workspace/scratch/dictionary-removal-game-final.log`,
`/workspace/scratch/dictionary-removal-2.0.47-build.log` and
`/workspace/scratch/dictionary-removal-2.0.47-apks.json`. The working tree contains
926 nonignored files after removal, compared with 1,187 at the prior checkpoint.

## Subsequent CI repair

The first hosted run for commit `d58345b` exposed limits not established by the
local checks above: release compilation ran out of memory in its separate 2 GiB
Kotlin daemon, and all four Android API jobs rejected the large note editor's
generated bytecode with `VerifyError`. The native jobs each passed nine other
checks; APK assembly and JVM tests had not detected this editor failure. No
2.0.47 release was published by that failed run.

The repair introduces non-inline layout boundaries in `KeepNoteEditor`, preserving
its state/effects, callbacks, layout and public signature. Its main debug DEX
method shrinks from 33,876 to 7,897 code units. Kotlin now shares the bounded
4 GiB Gradle heap. Forced, uncached compilation of both variants passed, as did
debug/device-test APK assembly and 72 note/archive/private-media regressions in
seven suites (no failures, errors or skips; documented cloud C1 test workaround).
The asset sizes and 829 fingerprints above describe the preceding checkpoint;
they do not certify this subsequent repair. Hosted native validation is still
required for the new commit.
