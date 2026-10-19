# Assistant roadmap handoff

Last updated: 2026-10-19. Master specification: [MASTER_PLAN.md](MASTER_PLAN.md).

## Current checkpoint

Continuation publication: code through `deaa29748ca63ac3aedf7eb3a2062a69cdee8c6b` was pushed to GitHub main and the remote SHA verified on 2026-10-19. Earlier local-only statements below record historical checkpoints. The publication handoff commit accompanies main; consult Git history for its identity.

- Repository: Nrajesh0/Ayva; local branch `main`.
- HEAD at planning time: `01c692116526e0f78bea043403b6539914f93146`.
- Version at planning time: 2.0.8 / 108.
- The planning baseline had substantial uncommitted implementation/documents and design assets. The authorized B0–B2 checkpoint includes the reviewed implementation, tests and roadmap; design mockups remain local. The SHA above identifies the original planning base, not the completed checkpoint; use `git log` for the latter.
- Latest request: continue after the B2 push and complete the remaining batches. B0–B2 were pushed to main as `1c8618e540d066366115b6f65fcaf39cbe6309d3`; remote HEAD was verified. Continue sequentially without a version bump.

## Existing work to preserve

Response controls, authoritative app help/settings, `/talk` mode, app lock/unlock groups/exceptions, shared app sorting/filtering, conversational repairs and routine/habit follow-up handling are already implemented locally.

Details and historical evidence:
- [Response controls and app help](../AYVA_RESPONSE_CONTROLS_AND_APP_HELP.md)
- [Conversation repairs](../AYVA_CONVERSATION_REPAIRS.md)
- Earlier feature documents are historical, not the authoritative current feature matrix.

Previous implementation validation recorded 839 tests passing, zero failures/errors/skips; debug and optimized release assembly passed. This is prior-run evidence, not a fresh run for this documentation change. No physical Android device checks were completed. Release output is unsigned locally.

Prior local build log: `/workspace/scratch/ayva-conversation-expansion-full.log`. These environment paths are not portable. Previous local setup used JDK 21/Gradle 8.9, `/workspace/toolchains/ayva-env.sh` and a test-only JVM init script `/workspace/toolchains/ayva-test-jvm.gradle` for a worker C2 issue. Do not assume another account has these files, or change production compilation to mask an environment issue.

At planning baseline, release CI used JDK 17/Gradle 8.9, the wrapper specified 9.3.1, and release builds omitted tests. B0 aligned the wrapper/CI with the verified Gradle 8.9/JDK 21 toolchain and added source validation. See BUILD_AND_TEST.md for the current commands. Standard validation targets are `:app:testDebugUnitTest`, `:app:assembleDebug`, `:app:assembleRelease`; use `--offline` only after dependency caches are populated. Build-time dependency downloads are distinct from prohibited runtime model downloads.

## Batch status

| Batch | State | Next deliverable |
| --- | --- | --- |
| B0 | Validated locally; hosted CI pending | Wrapper/JDK alignment, non-publishing CI and portable instructions |
| B1 | Validated locally; device checks pending | Task-only recovery, storage-independent speech/session and privacy regression guard |
| B2 | Pushed as `1c8618e`; hosted/device checks pending | Shared contracts, schema-derived settings/help, reviewed legacy chips and feature matrix |
| B3 | Validated locally; device/human gates open | Shared interpretation, safe command arbitration and typo/quote handling |
| B4 | Validated locally; device/human gates open | Absolute lifetimes, process authority, transactional row guards and repair |
| B5 | Validated locally; device/human gates open | Combined task views, stable lists, clock/date repairs and checklist edits |
| B5P | Bounded implementation; P6 device acceptance pending | Guided day/exam/trip drafts, scoped sheets, deadlines, atomic save/receipts/retry |
| B6 | Implemented; device enforcement pending | Inventory-bound reviews, conversational exceptions, routine toggle undo; existing picker sorting preserved |
| B7 | Implemented; native device flows pending | Real result recall and native saved-word quiz filtering; existing habit/focus owners reused |
| B7D | Implemented; device performance/human meaning review pending | Licensed bundled dictionary, contextual source senses/bookmarks and bounded sentence patterns |
| B8 | Implemented; native delivery checks pending | Schema-backed setting follow-ups/undo, truthful help and diagnostics |
| B9 | Implemented; human conversational evaluation pending | Finite topic/game/story/joke/listening packs with scoped controls |
| B10 | Validated locally; device/human gates open | Explicit tone/quiet/suggestion preferences; cooldown/dismiss/forget |
| B11 | Local regression/build checks passed; device/human/hosted gates open | Final regression, artifact/data integrity, APK size/builds and device checklist |

The former stop-after-B2 instruction was superseded by the user’s request to complete remaining batches. The remaining implementation is recorded in OFFLINE_ASSISTANT_IMPLEMENTATION.md; physical and independent human acceptance gates remain open. B0/B1 local verification is complete; hosted CI and physical-device checks remain pending. See [B0_B1_IMPLEMENTATION.md](B0_B1_IMPLEMENTATION.md) and [BUILD_AND_TEST.md](BUILD_AND_TEST.md).

## B0/B1 validation and handoff

At B0/B1 completion on 2026-10-18, version was 2.0.8 / 108 and HEAD was still the planning base; no commit/push or hosted Actions run had yet occurred. B2 and the subsequent main-push authorization are recorded below. Before switching environments, transfer the complete working tree changes or publish an authorized checkpoint. A final local bundle is saved at `/workspace/scratch/ayva-assistant-b0-b1/working-tree.tar.gz`, with base SHA/per-file hashes in `manifest.json` and a tracked patch in `tracked.patch`. It includes prior local implementation and design files; do not blindly apply over another dirty checkout. The pre-batch archive remains separately available.

- Wrapper distribution download/checksum and `./gradlew --version`: passed with Gradle 8.9/JDK 21.
- B0 initial combined targets: succeeded with cached outputs (not a fresh execution of all tests).
- First B1 focused run: identified per-task bulk snapshots and transaction-bookkeeping noise in query instrumentation; both corrected, without weakening deletion/privacy assertions.
- First full combined tests/optimization run: 849 tests, 1 failure in the existing native Notes draft-deletion timing check. Evidence retained in `/workspace/scratch/ayva-b1-first-full-results/` and `ayva-b0-b1-full.log`. The native security class passed separately (13 tests). No native deletion assertion or production Notes editor logic was changed.
- Final full test invocation, separated from APK optimization: **849 tests, 0 failures, 0 errors, 0 skipped**, BUILD SUCCESSFUL in 2m 15s. This includes 10 new privacy/recovery tests. Log: `/workspace/scratch/ayva-b0-b1-tests.log`.
- Subsequent debug and optimized unsigned release assembly: **BUILD SUCCESSFUL in 18s**. Log: `/workspace/scratch/ayva-b0-b1-apks.log`. Both APK DEX payloads contain the selected-task recovery format marker.
- The optional test-only cloud C2 workaround was used. Final tests: `./gradlew :app:testDebugUnitTest --init-script tools/gradle/cloud-test-jvm.gradle --offline --no-daemon --console=plain --max-workers=2 -Pkotlin.incremental=false -Pkotlin.compiler.execution.strategy=in-process -Dorg.gradle.jvmargs='-Xmx4096m -XX:MaxMetaspaceSize=768m -Dfile.encoding=UTF-8'`. APK invocation used the same resource options without the test init script and targeted `:app:assembleDebug :app:assembleRelease`.
- Fresh-checkout debug signing fallback: `:app:validateSigningDebug` passed with the existing local root key temporarily absent; the original local key was restored. Log: `/workspace/scratch/ayva-b0-fresh-debug-signing.log`.
- Dependency boundary guard: passed for 50 source files. Workflow YAML, documentation links and `git diff --check`: passed. Hosted workflow execution is unverified until an authorized push.
- Debug APK SHA-256: `15779249dec67e8e59df91edaee38d224a72a65484f0de02754283258af113bb`.
- Unsigned release APK SHA-256: `cf61a4ff55d91caad69f27e9d962152279467d247a65315c0462fbc8a183057b`.

CI now runs tests before APK optimization in separate invocations to reduce contention around asynchronous lifecycle tests. The first-run timing failure remains in the evidence; the later pass does not prove arbitrary scheduling is free of races. Physical Android 16 speech, lifecycle, overlay/reminder delivery and native recovery UI checks remain unrun. Old media-encrypted assistant sessions intentionally expire on upgrade; saved app data is retained.

See [B0_B1_IMPLEMENTATION.md](B0_B1_IMPLEMENTATION.md) for the file/behavior summary and [BUILD_AND_TEST.md](BUILD_AND_TEST.md) for portable setup.

## B2 implementation and verification

Implemented and locally verified on 2026-10-18; debug and optimized release assembly passed. [CAPABILITY_MATRIX.md](CAPABILITY_MATRIX.md) records the domain inventory, exact migration boundary and next work.

- `AssistantCapabilities.kt`: stable read/write/navigation/conversation/unavailable contracts, exhaustive typed-action mapping, required review/undo metadata, schema-derived setting adapters, safe legacy-chip conversion and informational capability help. Actual Usage Access and speech prerequisites are checked without reading private data.
- `AssistantPreferences.kt`: group metadata now belongs to the preference schema. Settings current values, ranges, choices, preview commands and help share this source rather than a separately maintained grouping list.
- `AssistantAppKnowledge.kt`, `AssistantResponseContent.kt`, `AssistantActionExecutor.kt`: settings help/controls resolve through the capability contract; the executor rejects unknown setting keys/type mismatches before mutation. Domain owners still perform entity/state/side-effect validation.
- `OfflineAssistant.kt` / `AssistantConversationSupport.kt`: review policy also covers actions selected through clarification choices; a chosen routine gets a concrete named preview before enabling/disabling. Existing low-risk task creation/single-task editing remains direct.
- `TalkAction.kt`, `ui/components/TalkActionChips.kt`, `AyvaResponseControls.kt`: old direct preference/routine mutations become canonical preview queries, including direct execution outside the newer renderer. Invalid keys/types/values/IDs do not navigate or write.
- `AssistantCapabilitiesTest.kt`: 12 new integration/contract regressions covering current owners, preview round trips, legacy execution, stale settings, routine choices, permission readiness, unavailable-feature help and unchanged natural task behavior. Existing direct-chip and response-renderer regressions now require review and reject wrong types.
- `tools/check_chat_boundary.py`: 52 source files, now including the older chip renderer; it also rejects direct preference/executor writes from preview-only chip code.

Focused run before the final prerequisite/description additions: 116 tests passed, zero failures/errors/skips (`/workspace/scratch/ayva-b2-focused.log`). Final full suite: **861 tests passed, zero failures/errors/skips**, BUILD SUCCESSFUL in 4m 23s (`/workspace/scratch/ayva-b2-tests.log`). The checked-in cloud C2 test init script and existing resource flags from B0/B1 were used. Subsequent `:app:assembleDebug :app:assembleRelease` completed successfully in **5m 31s**, using the existing offline/resource options without the test init script. Log: `/workspace/scratch/ayva-b2-apks.log`. Both APK DEX payloads contain capability-help and selected-task recovery markers. The local release is unsigned.

Runtime examples: `/talk capabilities`, `/talk capabilities tasks`, `/talk capabilities settings`, and `what can you do with tasks?`. These replies show supported operations and explicitly label planned features; example sentences in their text do not execute. General language interpretation is still the existing parser system; B3 is responsible for consolidating competing interpretations. B4 handles broader context consistency. Dictionary expansion, guided planning and richer personality remain their later batches.

Dependency guard passed for 52 source files; documentation links, workflow YAML and whitespace checks passed. This is local evidence; the hosted Actions result is not yet recorded here.

- Debug APK SHA-256: `ff531da388f0059d42120c30c25f1de4f8bbe792576b3286c202473074302971`.
- Unsigned release APK SHA-256: `862b91fc6cb63f1f385379bd65ce26672fa962e1335e4680f050498a0d1cc656`.

The user requested stopping after B2 and pushing to main, with no version bump. This handoff accompanies that checkpoint; use `git log` for its actual commit identity. Hosted CI and physical-device UI/OS-delivery checks remain unverified here. `tools/release_intent.py` prevents ordinary build-infrastructure pushes from replacing an existing release: automatic publication requires version-name/code changes; explicit release/manual events retain their existing publication behavior. The current push's publication decision was tested as false. Decision checks also cover version changes, missing base metadata, PRs and explicit publication events. The prior B0/B1 bundle is unchanged. B2 source, tests and handoff documents are included in the authorized main checkpoint. Untracked design mockups remain local and are not required for app builds.

## Plan expansion: dictionary and everyday conversation

Historical planning expansion (implementation status is now in the batch table/current continuation below):
- B7D introduces a bundled, licensed offline dictionary, building on existing vocabulary data. It requires source/license review, measured size and lookup budgets, contextual follow-ups, native learning integration and honest limits for sentence explanations. No per-word manual authoring project or runtime model/data downloads.
- B9 now has four sequential slices for general conversation state, broader multi-turn content, responsive wit/empathy and safe transitions to app capabilities. Casual conversation must work without a slash command and must not be forced into task creation or productivity advice.
- B8 now depends on B7D. Original batch IDs remain stable. At the time of that plan update, B0 was next; this did not mark either new feature implemented. Consult the current batch table above.
- Documentation verification: local Markdown links and whitespace checked; no application code changes or new application test/build run for this update.

## Plan expansion: guided planning

Added [GUIDED_PLANNING.md](GUIDED_PLANNING.md) and required batch B5P, after B5 and before B6. This specifies mode offers, missing-information question sheets, corrections, conflict handling, editable reviews and guarded task/checklist creation for day, exam and trip preparation. P1–P6 provide bounded sequential implementation slices; B9 later enhances conversational personality without blocking basic planning.

Repository inspection confirmed `Task.subtasksJson` already supports checklist subtasks, but these have no individual dates/reminders. The earlier suggestion that all subtasks might need a new data model is superseded by this finding. The current planner only orders existing tasks and allocates timeboxes; guided plan generation/saving is not yet implemented. External Android/conversation design pages could not be retrieved (proxy HTTP 403); the design document marks them as unverified references, not reviewed evidence.

During the earlier guided-planning specification update, only documentation changed. Later implementation and publication status are recorded in the batch table and validation sections above.

## Newly confirmed priority finding

At planning baseline, `AssistantActionExecutor.kt` obtained a Notes DAO for task Trash recovery, and the all-data snapshot helper read Notes. Existing chat refusal text does not satisfy the stronger zero-access boundary. The planning turn documented this finding. B1 now replaces that dependency with encrypted selected-task recovery, with passing local privacy/recovery tests and full regression/build validation. Native all-data backups remain independent.

## Required handoff update after each batch

Record:
- Batch/sub-batch and status: not started / in progress / validated / blocked.
- Base SHA; implementation commit SHA if created; current uncommitted files. Refer to a completed implementation commit from a later handoff commit rather than trying to embed a commit's own SHA within itself.
- Concrete behavior added, preserved, intentionally unsupported; changed files.
- Exact commands, actual test counts and outcomes, build types, timestamps and evidence locations.
- New/existing failures; unrun device checks; privacy and stale-action regression results.
- Decisions and rationale; explicit next action and dependencies.
- Whether commits were pushed and any CI run/commit it validated. Never equate local success with GitHub success.

Keep this file factual. Do not copy private user data, Notes, credentials or raw personal conversation logs into it.

## Continuation after B2 push

The user superseded the stop instruction and authorized continuing all remaining batches. B2 was pushed and remote main verified at `1c8618e540d066366115b6f65fcaf39cbe6309d3`. GitHub Actions API requests are blocked by the environment proxy (403); hosted CI is unverified. No version bump. Subsequent work at the start of continuation was uncommitted; final checkpoint status is recorded below.

B3/B4: shared interpretation barrier across coordinator/router/legacy NLU; bounded absolute reference/review lifetimes; full-row task/routine confirmation guards. First focused run: 90 tests, 3 failures (two quoted-title regressions and an adjacent-letter typo case). Corrected the policy to preserve literal quoted slot answers and recognize transposed command letters without executing a correction.

Historical B5/B5P progress checkpoint: composable extended task views; versioned day/exam/trip preparation drafts; scoped answer/review sheet; stable draft item IDs, corrections and capacity conflicts; task-only Room 12→13 migration and atomic plan/receipt save with reminder reconciliation. Focused run after fixes: BUILD SUCCESSFUL in 2m31s (`/workspace/scratch/ayva-b3-b5p-tests.log`), including planning rollback/retry, stale question/save, interruption and duplicate-title regressions. Full suite/device/accessibility checks remain pending; do not mark P6 complete from unit tests. Quick existing day planning and protected cards remain intact. At that checkpoint, independent item deadlines were not yet implemented; the current implementation below supports them for separate tasks. Arbitrary conversational scheduling remains unsupported.

## Current continuation implementation

[OFFLINE_ASSISTANT_IMPLEMENTATION.md](OFFLINE_ASSISTANT_IMPLEMENTATION.md) records the delivered grammar, state/mutation paths, constraints and migration details. [DICTIONARY_DATA.md](DICTIONARY_DATA.md) records source/license/size evidence. [DEVICE_ACCEPTANCE.md](DEVICE_ACCEPTANCE.md) records unrun acceptance. Do not call the entire roadmap fully accepted while these hardware/human gates are open.

Version is unchanged. The B2 push is verified; the subsequent implementation is a locally committed checkpoint after the final regression/build. No continuation push or release publication. Existing untracked design mockups are preserved and excluded. The implementation does not promise unrestricted language, calendar/dependency optimization or arbitrary sentence paraphrasing.

Validation progression:
- B3/B4 initial focus: 90 tests, 3 failures, corrected literal title/typo policy.
- B3–B5P focus after fixes: 97 tests passed, no failures/errors/skips (2m31s).
- Broader B3–B7 focus: 140 tests, 2 old capability assertions still labelled newly implemented planning features unavailable; those contracts/assertions were updated.
- First continuation full run: 890 tests, 16 failures, 0 errors/skips (4m06s). Corrected Android's automatic `.gz` asset expansion, contextual done replies, quoted habit titles, archive-list routing and failure-time confirmation authority. Preserved established task-aware greetings; updated the emotional-disclosure test to require no forced planning and an explicit request for prioritization.
- Focus after compatibility fixes: 307 tests, 2 failures (archive read routing and an incorrect new test assumption that reluctant has two senses; the pinned source has three). Corrected both without changing source meanings.
- Full run after corrections: 894 tests passed, no failures/errors/skips (4m06s).
- Full run including later task/routine transaction guards, routine undo, database migration and hobby tests: 898 tests passed, no failures/errors/skips (4m05s).
- The final source adds combined priority/overdue filtering and preview/saved-task clock-only repairs, including AM/PM clarification. Final full regression of this source: **901 tests passed, 0 failures/errors/skips**, BUILD SUCCESSFUL in **4m09s** (`/workspace/scratch/ayva-assistant-final-tests.log`). Earlier green runs remain historical evidence. APK assembly is next.

Logs are under `/workspace/scratch/ayva-remaining-*.log` and `/workspace/scratch/ayva-assistant-final-tests.log`; failed/earlier green XML evidence is preserved in separate scratch directories. Standard sequential commands and the cloud test-only JVM workaround remain as documented in BUILD_AND_TEST.md. No native Notes deletion assertion or overlay mechanism was changed.

ADB from the configured Android SDK reported an empty device list. GitHub Actions API access is blocked by proxy HTTP403; no hosted pass is claimed. No version bump or release publication is part of this continuation.

Final review follow-up: the 901-test full run passed in 4m09s. Explicit /talk help/settings and feature questions now take priority over game/hobby continuations; the 902-test full run passed in 4m07s. The first debug/release build passed in 5m30s, followed by a build of that routing revision in 5m05s. Those artifacts precede the last input-rejection review and are not the final checkpoint artifacts.

The last review additionally revokes old task/plan confirmation controls after oversized inputs or an unavailable historical reply target, clears implicit mutation references on exceptions, and keeps ordinary want/like/enjoy statements conversational. These changes have their own added regressions. Their final full run and refreshed artifacts will be recorded below.

Input-boundary review: **905 tests passed, 0 failures/errors/skips**, BUILD SUCCESSFUL in 4m08s (`/workspace/scratch/ayva-assistant-rejection-tests.log`). A subsequent review found that a renewed review revision after a committed save/process failure incorrectly failed receipt matching. The final fix hashes concrete task payloads (excluding generated row IDs/update timestamps) rather than review metadata, while the coordinator still requires the current nonce/revision. A dedicated process-after-commit regression now checks recovery of the same IDs. Verification of that final hash change is pending below.


Final continuation verification progression (2026-10-18/19):
- The receipt-hash/process recovery and opaque quoted-field fixes passed **908 tests**, zero failures/errors/skips, in 4m07s (`/workspace/scratch/ayva-assistant-checkpoint-tests.log`).
- Goal retention/recap/descriptions, unsupported planning-domain clarification, explicit day budgets and normalized native quiz distractors passed **913 tests**, zero failures/errors/skips, in 4m07s (`/workspace/scratch/ayva-assistant-complete-tests.log`).
- The subsequent legacy-write/unknown-app-help/dated-prose safety run executed **916 tests**, with one failure and no errors/skips (4m09s; `/workspace/scratch/ayva-assistant-final-checkpoint-tests.log`). The ambiguity policy had caught clear anchored task requests such as moving furniture. Restored the existing direct-capture behavior for anchored requests while retaining a choice for otherwise unrecognized dated messages. The original compatibility assertion is retained. Failed XML evidence: `/workspace/scratch/ayva-assistant-final-checkpoint-first-results/`. Final corrected-source verification is recorded next.

Corrected-source final unit run: **916 tests passed, 0 failures/errors/skips**, BUILD SUCCESSFUL in **4m24s** on 2026-10-19 (`/workspace/scratch/ayva-assistant-validated-tests.log`). This retains the original direct-task regression assertions as well as the new wrong-action checks. Both static guards also passed: 65 chat source files, and all 107,945 dictionary headwords / 163,274 senses with source/asset integrity and byte budgets. Workflow YAML, local documentation links and whitespace checks passed.

The tested source freeze covers 769 files under app/src, app/schemas, tools and .github/workflows: SHA-256 `e7904c7c2abb43098091c3c45e8b5e8b537df0025d93082967bb9e53c5844f41`. Documentation/commit metadata are outside that source freeze. APK builds and final checkpoint details follow below.


## Final local continuation checkpoint (2026-10-19)

B3–B10 and the bounded B5P/B7D implementation are complete locally. B11 automated regression, source/data integrity and APK assembly passed; P6/B11 hardware and independent human acceptance remain **open**. The new work is committed locally on main; use `git log` for its commit identity. Only B2 has been pushed. Version remains **2.0.8 / 108**; no release signing/publication or continuation push. The untracked design directory is preserved and excluded.

After the 916-test pass, separate `:app:assembleDebug :app:assembleRelease` completed **BUILD SUCCESSFUL in 5m24s** (`/workspace/scratch/ayva-assistant-validated-apks.log`), using the documented offline/resource flags without the test-only init script. The tested source digest still matched after build. Both APKs contain the final goal, dated-message ambiguity, native quiz and task-only recovery markers. Their exact 259 dictionary assets match source byte-for-byte, including all 256 shard SHA values and the manifest/editorial/license files; no expanded or duplicate dictionary assets are packaged.

| Artifact | Bytes | SHA-256 |
| --- | ---: | --- |
| Debug APK | 61,284,908 | `00894bce786fbd6a746ccd6fadaf8a214e7c1306a513d346a54d8285ecafbf91` |
| Optimized unsigned release APK | 56,711,484 | `28027569fe4bceaf7640fdf9741512e490c8031814829a5b56d7b9f7ec2167ad` |

The release is 9,629,786 bytes larger than the B2 47,081,698-byte baseline; dictionary assets account for 9,530,861 packaged bytes. Debug dictionary assets account for 9,531,377 packaged bytes. Build metadata and `aapt dump badging` independently confirm package com.focusbyrj.app, version 2.0.8 / 108 in both files. `apkanalyzer apk summary` encountered a SAX error rendering an ampersand; the AAPT metadata check succeeded, without changing app manifests to accommodate the analyzer.

Final evidence JSON: `/workspace/scratch/ayva-assistant-checkpoint-evidence.json`. Final local source bundle and README/log/evidence handoff: `/workspace/scratch/ayva-assistant-continuation-handoff.tar.gz`. The bundle requires the pushed B2 prerequisite; its README describes inspection/fast-forward import into a clean checkout. These files are local transfer artifacts, not GitHub publication.

ADB was rechecked on 2026-10-19 and listed no devices. Actual airplane-mode/device latency/heap/battery, speech, TalkBack/large text/lifecycle, OEM overlay/reminder enforcement and human conversational-quality/held-out-language evaluation remain unrun. Existing GitHub Actions API access returned proxy HTTP403; no hosted pass is claimed. See DEVICE_ACCEPTANCE.md for the remaining executable checklist. No further code change is required by the currently passing automated checks; continue with those acceptance gates on available hardware rather than adding test-count targets.


## Scripted conversation review and reply polish (2026-10-19)

After the user requested continuation, device availability was rechecked: ADB still listed no devices, and no emulator/system image or /dev/kvm was available. The user deferred reviewing the app. Device and human acceptance therefore remain open.

Ran a scratch-only collector through the real coordinator/reply engine with in-memory stores: six synthetic scenarios, 38 turns. The collector asserts actual task/checklist/bookmark state, records native handoffs without launching them, and is excluded from production and the permanent suite. This is developer-authored exploratory evidence, not an independent or held-out human quality score. [CONVERSATION_REVIEW.md](CONVERSATION_REVIEW.md) contains the resulting dialog.

The initial run passed in 45s and exposed confusing copy: setting undo mentioned completion rewards; planning recaps still promised to ask for already-supplied steps/deadlines; dictionary bookmark preview unnecessarily mentioned Notes. Corrected these messages and omitted routine delivery diagnostics on a successful plan save. Real effect-refresh failure still shows retry instructions; scheduling/delivery behavior is unchanged. Task/habit completion undo retains its existing reward wording, task receipt/reschedule layouts are unchanged, and no Notes capability was added. Other undo receipts now use their own domain.

Extended the existing current-setting/undo regression with restored-value/domain relevance assertions; no permanent test cases were added. The focused capabilities/planning/dictionary regressions plus the scratch collector passed **54 tests, 0 failures/errors/skips**, BUILD SUCCESSFUL in **2m23s** (`/workspace/scratch/ayva-review/polish-tests.log`). Comparing the before/after collection showed the same controls, native handoffs and final task rows for all 38 turns; 11 reply texts changed. The previous full 916-test pass applies to baseline `9015eaf`; it is not represented as a new full run of the reply-polish revision. Its original XML is preserved under `/workspace/scratch/ayva-review/validated-916-results/`.

Current tested source freeze (769 files; same scope as above): `3d7ec30cc616324f165a7ec068f682c12a80d3ef58a6638cbbacfe7fd64bd233`. Both static guards and whitespace checks passed. Build/artifact/checkpoint details follow after assembly; version remains unchanged and there is no push.

Reply-polish artifact verification: `:app:assembleDebug :app:assembleRelease` passed separately in **5m05s**, using the same documented offline/resource flags (`/workspace/scratch/ayva-review/polish-apks.log`). Both APKs contain the updated wording and exclude the scratch collector; all 259 dictionary assets still match source byte-for-byte. The source freeze matched after assembly. AAPT confirmed unchanged version 2.0.8 / 108.

- Debug: 61,284,908 bytes; SHA-256 `00cfbf222f5dfada03f5d982ebfb36341f832b3b439888ff74cd5265e008b688`.
- Optimized unsigned release: 56,711,484 bytes; SHA-256 `c6635299ca906f79db5957af2bf4479fec91dd688cde508d3df0470d2971c416`.
- Current evidence: `/workspace/scratch/ayva-review/polish-evidence.json`. Its unit counts are the focused run: 53 existing regression cases plus one scratch collector case. The latter exercised six scenarios, not an addition to the permanent suite.
- Wording comparison: `/workspace/scratch/ayva-review/wording-diff.json`. Source/collector/transcripts/final logs are retained in the refreshed local continuation handoff; the bundle still requires the pushed B2 base. No model/download/Notes capability, action-policy change, protected UI change or version bump.

This follow-up is committed locally after `9015eaf`; consult Git history for its SHA. There is no push or publication. No new full-suite result is claimed beyond the earlier 916-test baseline. The focused run and actual dialogue-state comparisons are appropriate to these message-only changes. Required physical checks and independent human/held-out evaluation remain deferred, as requested by the user.


## Continuation pushed to GitHub main

The user explicitly authorized pushing the completed continuation. `git fetch origin main` confirmed the remote was still B2 and local main was two commits ahead, with no divergence. A normal fast-forward push succeeded from `1c8618e` to `deaa297`; `git ls-remote origin refs/heads/main` returned exactly `deaa29748ca63ac3aedf7eb3a2062a69cdee8c6b`. This includes implementation commit `9015eaf` and reply-polish commit `deaa297`. This documentation update follows that verified code push.

Version remains **2.0.8 / 108**; no version bump, signed release or release publication was requested. Untracked design mockups remain excluded. Code/tests/assets are unchanged from the locally validated checkpoints; this follow-up changes only handoff documentation, so no redundant build/test run was performed.

Hosted Actions status is still unverified. Existing local evidence remains the earlier 916-case full run at `9015eaf`, the 54-case focused/scratch run after message-only polish, and both final debug/optimized unsigned APK builds. Physical Android 16, independent human conversation review and performance/accessibility/OEM checks remain deferred.

Publication handoff recorded at 2026-10-19T03:16:10+00:00.
