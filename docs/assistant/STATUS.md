# Assistant roadmap handoff

Last updated: 2026-10-18. Master specification: [MASTER_PLAN.md](MASTER_PLAN.md).

## Current checkpoint

- Repository: Nrajesh0/Ayva; local branch `main`.
- HEAD at planning time: `01c692116526e0f78bea043403b6539914f93146`.
- Version at planning time: 2.0.8 / 108.
- The planning baseline had substantial uncommitted implementation/documents and design assets. The authorized B0–B2 checkpoint includes the reviewed implementation, tests and roadmap; design mockups remain local. The SHA above identifies the original planning base, not the completed checkpoint; use `git log` for the latter.
- Latest request: stop after B2 and push the validated checkpoint to main. B0–B2 are locally validated. Do not start B3 or bump the version without a new request.

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
| B2 | Validated locally; CI/device checks pending | Shared contracts, schema-derived settings/help, reviewed legacy chips and feature matrix |
| B3 | Ready; awaiting a new implementation request | Common interpretation and ambiguity policy |
| B4 | Pending B3 | Shared context, reference and repair semantics |
| B5 | Pending B4 | Task conversations/planning |
| B5P | Pending B5 | Guided planning P1–P6: popup/state design, day plans, task/subtask saves, exams, trips, validation |
| B6 | Pending B5P | App controls/routines and classification |
| B7 | Pending B6 | Habits/focus/learning |
| B7D | Pending B7 | Dictionary D1 data/license/size prototype → D2 conversational lookup → D3 bounded sentence explanations |
| B8 | Pending B7D | Complete settings/features coverage |
| B9 | Pending B8 | C1 context → C2 conversation packs → C3 wit/empathy → C4 action separation and review |
| B10 | Pending B9 | Explicit preferences and restrained suggestions |
| B11 | Pending B10 | Offline/device/performance/release validation |

Stopped implementation after validated B2 as requested. The next implementation batch, only when requested, is B3: common interpretation and ambiguity policy. B0/B1 local verification is complete; hosted CI and physical-device checks remain pending. See [B0_B1_IMPLEMENTATION.md](B0_B1_IMPLEMENTATION.md) and [BUILD_AND_TEST.md](BUILD_AND_TEST.md).

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

User-approved scope added to the plan, not yet implemented:
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
