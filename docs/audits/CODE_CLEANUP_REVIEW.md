# Code cleanup review — 6 November 2026

## Scope and evidence

Reviewed the tracked file inventory, production Kotlin declarations, references in
production/test Kotlin and Android XML, manifest entry points, Gradle configuration,
CI, exact file hashes, asset consumers, and existing regression coverage.
Baseline: 413 production Kotlin files, 124,715 lines. This is a static maintenance
review with targeted runtime validation, not a proof that every remaining function
is reachable or a security audit. Comments and imports can create false references;
reflection, Android entry points and persisted class names can hide real ones.

## Applied cleanup

| Change | Evidence and compatibility |
| --- | --- |
| Delete `ui/components/AnalyticsComponents.kt` | Its sole composable, `HeatmapAndStreaksWidget`, has no callers in production or tests. The active account heatmap remains in `AccountScreen.kt`. |
| Remove `EnclaveAchievementsPreviewRow` from `AccountScreen.kt` | Private composable with no callers. The active achievements tab and `getAchievements` remain. |
| Remove `ModeSelectorBox` from `SchedulesScreen.kt` | No callers; routine creation and selection use other controls. |
| Remove `ArchiveVaultSettingsDialog` from `ArchiveVaultDialogs.kt` | No callers. Current vault settings use `PrivateVaultSettingsPanel`; unlock, PIN change, recovery and export dialogs remain. |
| Remove unused imports from those three files | Explicit imported identifiers absent from the remaining bodies; delegated property operators retained. |
| Consolidate identical exam layouts | Seventeen layouts share four identical structures. Keep four XML files and thirteen resource aliases in `values/exam_layout_aliases.xml`. Existing design/resource names and view IDs remain available. Do not collapse the design settings: the renderer applies design-specific colors and decoration. |
| Delete `assets/cat_dancing.lottie` | SHA-256 matches `cat_dance.lottie`; the rare action pool selected identical content. Use `cat_dance.lottie` directly and update sizing and the asset-load test inventory. The 1% rare-action probability remains. |

Shared layout structures:

- `widget_exam_hero`: AURORA, POSTER, PRISM aliases.
- `widget_exam_colorblock`: LAYERS, ORBITAL, TICKET aliases.
- `widget_exam_countdown`: FROST, SIGNAL aliases.
- `widget_exam_list_row`: DASHBOARD, HERO, MINIMAL, TICKET, TIMELINE list aliases.

## Remaining candidates and files worth keeping

| Files | Assessment | Recommended treatment |
| --- | --- | --- |
| Root `package.json` | Declares only `canvas`; no checked tool imports that Node package. `verify_vizag_natural_motion.cjs` instead imports undeclared `playwright`. HTML canvas usage does not require Node canvas. | Likely stale tooling manifest. Define a reproducible art-tool workflow and its actual dependencies before removing/replacing it. It does not participate in the Gradle app build. |
| Root `metadata.json` | Declares server-side Gemini capability despite the offline Android product. No reference found in inspected app/build/tooling entry points. | Likely external scaffold metadata; confirm the external consumer before deleting. Do not interpret it as an implemented Android capability. |
| `docs/app-atlas/RUN-Visual-Guide*` | PDF, HTML and ZIP total about 7 MiB. Deliberate published formats, with links in the atlas README. Snapshot describes version 2.0.37 / 396 Kotlin files, not today's tree. | Keep as historical documentation; label/update it when regenerating. Moving downloadable bundles to release assets could reduce future repository growth. No APK size benefit from deleting docs. |
| `AUDIT_TRACKER.md`, dated audit/release documents | Maintenance history, not application input. The PowerShell coverage helper reads the tracker. | Keep; archive deliberately if navigation becomes difficult. Historical inventories can mention files removed by this cleanup. |
| `tools/data/dictionary_plain_english.json` and bundled dictionary copy | Byte-identical by design. Import tooling uses the source; `check_dictionary.py` asserts equality and the manifest hash. | Keep both, along with dictionary attribution and licenses. |
| `ic_launcher.xml` and `ic_launcher_round.xml` | Identical XML, separate Android icon roles. | Keep both names. They are tiny and platform-facing. |
| Reconcile workers, receivers, services, widget providers | Some have few textual references but are scheduled or registered Android entry points. WorkManager can persist worker class names. | Keep; low reference count is not sufficient evidence for deletion. |
| Database migrations, legacy dialogue cleanup, backup/restore and recovery code | Supports existing installations and persisted data. | Keep. Removal requires explicit upgrade/restore compatibility evidence. |
| `tools/gradle/cloud-test-jvm.gradle` | Conditional workaround with documented trigger. | Keep; do not enable or delete merely because the current JDK run passes. |
| Vizag export/verification scripts | Art provenance and regeneration tools. Some require external gallery input, Python image libraries, Playwright, and a hardcoded `/usr/bin/chromium`. | Document/pin their tool environment and parameterize the browser path in a separate tooling change. Absence from the Android runtime is expected. |
| Ignored `.gradle/`, `.kotlin/`, `app/build/`, root `build/` | Disposable outputs/caches, not tracked application source. | Safe to regenerate when disk space is needed. Preserved here to avoid wasting the validated environment's build cache. |

## Structural priorities

The main maintenance problem is large responsibilities, rather than simply too
many files. Baseline largest files include `AccountScreen.kt` (4,790 lines),
`KeepNoteEditor.kt` (3,766), `ArchiveVaultDialogs.kt` (2,491), `NotesViewModel.kt`
(2,255), and `BubbleChatActivity.kt` (2,171). Other large files include
`AyvaTalkEngine.kt`, `NotesScreen.kt`, `BackupRestoreManager.kt` and
`AyvaChatTimeline.kt` (roughly 1,850–1,925 lines each).

Suggested follow-up order:

1. Extract account profile editing, avatar store and sharing UI into cohesive
   components; keep profile state ownership in one place. Avoid moving everything
   into another large generic helper.
2. Separate note editor state/actions from editor UI, preserving autosave,
   selection, undo, back navigation and attachment ownership. This requires editor
   regression and device coverage, not just a compile.
3. Organize vault dialogs by unlock/enrollment, recovery, and passcode management.
   Preserve security state transitions and recovery semantics.
4. Separate floating chat window lifecycle from conversation presentation. Retain
   the Android overlay mechanism required by `AGENTS.md`.
5. Keep assistant language/planning/action boundaries and their dependency guard.
   Small, referenced assistant helpers are not automatically redundant; combining
   them wholesale would weaken those boundaries.

These are future refactor boundaries, not claims that the corresponding features
are unnecessary. This change intentionally avoids broad state/lifecycle rewrites.

## Validation

The full baseline suite passed during onboarding: 1,635 tests, no failures or
skips. That baseline is not evidence for the modified tree.

On the modified tree:

- `:app:assembleDebug`: passed.
- 53 tests across `ExamWidgetTest`, `ExamWidgetEditorTest`,
  `ProfileTabNavigationTest`, `AccountSettingsNavigationTest`,
  `SecurityRemediationRegressionTest`, and `ArithmeticCrashTest`: passed, no
  failures/errors/skips. Includes real RemoteViews rendering for all widget
  designs and retained Lottie asset loading. The complete suite was not rerun.
- Chat dependency boundary and dictionary integrity checks: passed.
- `git diff --check`: passed.
- `:app:lintDebug`: completed, reporting 7 errors, 365 warnings and 56 information
  findings. Gradle exits successfully because the existing build sets
  `lint.abortOnError = false`; this is not a clean lint result. Reports are in
  `app/build/reports/lint-results-debug.{html,xml}` (generated/ignored).
- No device/emulator tests were run. Overlay entry points and core blocking logic
  were not changed.

### Initial lint follow-up findings

The seven error locations are in code outside the removed functions (the chat
StateFlow expression is unchanged). No baseline lint run was made, so these are
not a measured before/after error-count comparison:

| Check | Location and assessment |
| --- | --- |
| `SuspiciousIndentation` | `NotesViewModel.kt`, image insertion owner-session guard: indentation is misleading. The guard also removes temporary files, so preserve that ownership behavior when reformatting. |
| `UnspecifiedRegisterReceiverFlag` (3) | `BlockActivity.kt`, `BubbleService.kt`, `AuditPersistenceNotificationTest.kt`. Production sites already use `RECEIVER_NOT_EXPORTED` on Android 13+ and the legacy overload in the older-SDK branch. Review as guarded-overload/tool analysis findings, not proof that flags are missing on current Android. |
| `ProduceStateDoesNotAssignValue` | `SettingsVizagFooter.kt`: the producer visibly assigns `value` twice, including the decoded artwork result. Investigate detector/compiler resolution before rewriting the working loading path. |
| `StateFlowValueCalledInComposition` | `BubbleChatActivity.kt`: direct StateFlow `.value` access during composition. Follow up with observable Compose collection to ensure UI updates reliably. |
| `QueryAllPackagesPermission` | Manifest: app-blocker package discovery requires product/distribution review. Replacing it blindly with a narrow query list could break installed-app selection. |

Lint flags seven unused resources: `xml/backup_rules.xml`,
`xml/data_extraction_rules.xml`, `drawable/exam_list_card.xml`,
`drawable/ic_achievement_focus_champion.webp`, `drawable/ic_bubble_launcher_icon.xml`,
`drawable/ic_widget_check_filled.xml`, and `drawable/ic_widget_todo.xml`.
No source references were found; the only checked `getIdentifier` call reads a
system speech-service string. These are additional removal candidates, left out
of this validated batch. Review backup-policy intent and artwork provenance before
removing those files. The largest warning groups are hardcoded text (91), obsolete
SDK checks (88), and icon locations (53). Warnings should be triaged by behavior
and accessibility impact rather than fixed mechanically.

## Completed lint follow-up

The follow-up debug build and lint run completed successfully. The final XML
report contains **zero errors, 365 warnings and 56 information findings**.
The seven original errors were addressed as follows:

- Chat chest state now uses the existing lifecycle-aware Compose StateFlow
  collection API, so state changes can trigger recomposition. No layout, reward
  calculation or persistence behavior was rewritten.
- Image insertion indentation was corrected. Comparing tokens against HEAD
  confirms the complete `NotesViewModel.kt` change is whitespace-only.
- The three legacy receiver calls retain their original SDK guards and runtime
  behavior. Each legacy call has a local `UnspecifiedRegisterReceiverFlag`
  suppression explaining that the explicit private flag is supplied on API 33+.
  AndroidX registration was evaluated but rejected after Android 12 tests exposed
  an additional permission requirement. It is not part of the final patch.
- The artwork producer retains both explicit `value` assignments and its IO
  decoding. A local suppression documents the detector's missed assignments.
- The installed-app discovery permission remains, with a manifest-local lint
  exception explaining why app blocking needs it. This does not waive app-store
  distribution requirements or broaden the existing permission declaration.

No global lint rules were disabled. The remaining warnings were not mechanically
rewritten or hidden. Encryption/database implementations, backup implementations,
core blocking overlays and data formats are unchanged.

Final regression run: **58 tests passed, zero failures/errors/skips**, across
`BubbleRecoveryTest` (21), `AuditPersistenceNotificationTest` (3),
`SettingsVizagFooterTest` (11), `AyvaConversationUiTest` (6),
`MysteryChestRewardTest` (3), `KeepNotesFeatureTest` (8),
`SecurityRemediationRegressionTest` (3), and the new `BlockActivityReceiverTest`
(3). Cross-version receiver checks exercise Android API 31, 34 and 36. The new
test verifies that an untargeted close broadcast is ignored and an explicit
app-targeted close broadcast still finishes the legacy activity. Security checks
cover plaintext vault downgrade rejection, shared encrypted media retention and
malformed restore rejection. The chat dependency guard and diff whitespace check
also pass.

The separate full-suite follow-up was stopped at the user's request; it is not
reported as passed. Device/emulator tests remain unrun. These results establish
the checked behavior, not a guarantee that every possible device flow is covered.


## 14 November follow-up

Reviewed the current working tree, including the uncommitted backup/media and chat
changes. There are 433 production Kotlin files. No loose `.patch`, `.diff`, `.orig`,
`.rej`, `.bak`, `.tmp` or editor-save files were found in the tracked/nonignored
inventory. Exact text-file comparison found no duplicate Kotlin files; the two
identical plain-English dictionary files remain intentional source/output copies.
This is a scoped cleanup review, not a proof of global function reachability.

### Applied

- `verifyEncryptedBackup` now uses the existing `prepareRestore` reader and closes
  its encrypted staging with `use`. It never applies the prepared restore. This
  removes 98 duplicate lines, including unused metadata locals and a stale comment;
  manifest/media validation, path and size limits, full authenticated EOF draining
  and the verification summary remain. Source-opening errors now use the same
  actionable exception as restore. Cancellation still propagates. The cleanup
  assertions now check both old verification and shared-reader staging prefixes.
- Rename `launchCameraPreview` to `launchCameraCapture` in its declaration, editor
  caller and tests. It delegates full-photo capture; the preview name was stale.
- Group ten Vizag art scripts and two approved source-art files in `tools/vizag/`.
  Update usage text, repository-root resolution, the exporter child-script path,
  source-art path and maintained feature documentation. Preserve executable modes,
  source-art bytes, generated evidence paths and packaged resources. Historical
  file inventories/visual snapshots continue to describe their original checkpoint.
- Add the [tools index](../../tools/README.md) to distinguish CI/release utilities,
  dictionary inputs and artwork authoring stages. Older `fix_vizag_layers.py` is
  an art transformation requiring gallery input, not an app runtime repair patch.

### Keep and future boundaries

`PortableNoteSettings`, `RestoreJournal`, `RestoreRefreshWorker`,
`CaptureCleanupWorker`, `PrivateMediaInput`, `NoteThumbnail` and
`AssistantUpdateDelivery` have active callers and separate responsibilities.
They cover ID remapping, atomic recovery, durable refresh, temporary-camera cleanup,
authorized picker continuation, authenticated previews and automatic chat delivery.
Worker class identities can be persisted by Android WorkManager. Combining these
files or deleting them because they were added during a fix is not justified.
The guarded `TestRuntimeKeys` bridge also remains necessary for JVM tests; it is
not an installed-app software-key fallback.

The structural priorities above remain appropriate: extract focused components
from the roughly 4,630-line Account screen, 3,773-line note editor and 2,568-line
Notes view model, retaining one owner for mutable state. Further backup
serialization/apply separation should preserve its maintenance and rollback gates.
No broad UI/state refactor or test/security/provenance deletion was included here.

### Fresh validation

Latest per-class results cover **203 focused regression cases across 13 suites,
with zero failures, errors or skips**. The first run passed 202 cases; one automatic
backup UI case timed out waiting for settings to load, before archive verification.
The fixture now polls through a single helper that drains Robolectric's simulated
Android main queue. Its deadlines and assertions are unchanged. All 14 automatic
backup UI cases passed on rerun; the combined count uses each suite's latest
report rather than counting repeated cases twice.

The selection covers backup verification without mutation, encrypted staging
cleanup, wrong passwords, truncation/tampering, archive/path limits, automatic
publication, manual/automatic backup UI, snapshot/extra-copy restore, rollback,
portable preferences and IDs, vault/deposit authorization, and camera/private-media
return. Debug compilation passed with the test run. The optional diagnosed cloud
C1 workaround was used for test workers; no tests or production checks were disabled.

Python/JavaScript syntax, moved script modes, repository-root resolution and
referenced tool/evidence paths pass. Approved source artwork is byte-identical.
Maintained documentation links resolve. Art export was not run: this organization
change does not regenerate or replace packaged artwork. Source security contracts,
the chat-boundary guard and whitespace checks pass. Only three production Kotlin
files and three existing test files changed in `app/src`; its file set, resources,
assets and other source inputs match the pre-cleanup working-tree snapshot.

Evidence: `/workspace/scratch/code-cleanup-regressions.log`,
`/workspace/scratch/code-cleanup-ui-recheck.log`,
`/workspace/scratch/code-cleanup-final-results.json`, and
`/workspace/scratch/code-cleanup-final-inputs.json`. This is focused regression
coverage; earlier full lint/APK results belong to the checkpoint linked in the
backup/media review, not a new full lint/device run for this source cleanup.


`:app:compileReleaseKotlin` also passed on the final source. All 1,053 recorded
app/tool inputs still match their final fingerprints. Earlier uncommitted changes
are preserved. No version bump, publication or GitHub push was performed for this
cleanup. Release compile evidence: `/workspace/scratch/code-cleanup-release-compile.log`.
