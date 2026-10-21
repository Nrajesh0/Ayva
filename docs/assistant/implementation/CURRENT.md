# Conversational planner — implementation ledger

> Historical implementation ledger — bounded core complete. This file preserves checkpoint evidence, not the active queue. Use [the current summary](../DELIVERY_SUMMARY.md) and [new batch plan](../CONVERSATION_QUALITY_PLAN.md). Earlier uncommitted/pending statements below are historical.

This ledger records implementation of the [roadmap](../../AYVA_CONVERSATIONAL_PLANNER_ROADMAP.md). The bounded core delivery is implemented; the open research frontier and deferred acceptance audits are not marked complete. The newest section and requirement table describe current behavior; older sections retain checkpoint history. The foundation and backup/conversational-repair checkpoint passed functional and release validation. All six Android 10 editor/mode scenarios subsequently obtained passing focused runs. Further continuations add structured discussion context, explicit discussion-to-plan handoff, quieter dictionary replies, typed cost/time reasoning, qualitative requirements/preferences, and native hierarchy/work-interval persistence. Evidence belongs to each recorded source checkpoint.

## Latest continuation: remaining core implementation

The remaining bounded core implementation pass is complete. It adds work-start reminders, multi-session tasks, native hierarchy/session editing, native-task adoption, richer constraints and dependent replanning, explicit discussion memory and reusable templates, numeric ranges, listening context, draft/saved undo/redo and rolling workspace recovery. Chat agenda includes work sessions and deadlines distinctly. Completed tasks and occasions remain read-only adoption context. See [the delivery record](COMPLETION_IMPLEMENTATION.md) for commands, supported scope, storage limits and deferred audits.

Final affected-only verification passed **80 JVM cases** (41 completion essentials and 39 discussion), with **zero failures/errors/skips**, plus a fresh debug build in **3m 13s** (`/workspace/.setup/completion-final-validation.log`). This includes the last Talk-prefixed native-task handoff fix; stale references cannot adopt a task. The preceding broader integration run passed **174 cases** (41 essentials, 39 discussion, 67 planner, 14 native planning and 13 archive) plus debug packaging in **3m 34s** (`/workspace/.setup/completion-handoff-checks.log`). That run compiled before the final prefix fix and its expanded test; the unchanged planner/native/archive suites were not repeated afterward. Counts are separate, not additive. Full-suite, emulator, visual, performance, independent-language and optimized-release audits remain deferred at the user's request.

Final production/JVM Kotlin fingerprint: `a79ec60bdd62f8de65e9d3189071490e96b31f9c8b8d40583e2acb7ad11b31bd`; including Android tests: `7db5878ccf7bb94edc6197efac806529a21c5dff4134745cfc4e8726b040bf52`. Both matched before/after the final run. Fingerprints hash sorted relative Kotlin paths followed by their contents. Preserved evidence: `/workspace/.setup/completion-final-results.json`, `.xml`, and `completion-final-source-before.json` / `-after.json`. The broader run's results are in `completion-handoff-results.*`; its compiled-source identity and final-fix distinction are in `completion-handoff-source-compiled.json`.

Current debug artifact: `app/build/outputs/apk/debug/app-debug.apk`, **63,252,426 bytes**. The chat boundary guard passed for **87 source files**; `git diff --check` passed. Room stays at schema 14 and metadata/workspace/comparison format-1 readers remain compatible with the new format 2. No new model, inference service, network requirement or dependency was introduced. Core blocking overlays are untouched. Release/instrumentation APKs belong to older checkpoints. No publication or rollout occurred; work is uncommitted on branch `work`, based on `e5378ede1869d41be5642fd96fbeff1e6e223aa7`. The emulator is stopped. Broader natural-language quality, predictive Back/OEM behavior and release readiness are not established by these checks.

Earlier evidence below belongs to its recorded checkpoint; open items in historical sections may have been implemented by this delivery. The current requirement table is authoritative for implementation status.

## Version 2.0.10 / build 110 publication request

The user requested the version bump and a push of the completed implementation to GitHub `main`. App metadata changes from 2.0.9 / 109 to **2.0.10 / 110**, and the README download label follows it. Version-manifest verification is recorded in `docs/assistant/STATUS.md`. The Kotlin implementation is unchanged from the final 80-case checkpoint above. Existing local APKs retain their earlier embedded version until rebuilt; GitHub's existing release workflow is triggered by the version change. Hosted build/release success is separate from a successful Git push. Use Git history and the remote `main` ref for publication identity; the uncommitted statements above describe the verification checkpoint.

## Prior continuation: native task hierarchy and work intervals

Implementation and focused verification are complete for the storage/integration slice. See [the slice record](NATIVE_TASK_PLANNING.md). Schema 14 stores native parent/prerequisite task IDs, duration and a work interval alongside each task. Reviewed writes allocate IDs and apply the graph in one transaction; full task version checks, receipts and undo now include that metadata. Scheduling checks read canonical intervals after workspace discard and recheck native/other-draft conflicts before applying. Native edits replace saved reservations; unsaved draft proposals remain reservations. Deadlines alone never occupy time.

Encrypted backups, task recovery, rolling task snapshots, raw vault import/export and plaintext database migration preserve the optional value. Merges rebind graph edges and workspace expected versions to imported IDs; missing references cannot bind colliding local IDs. Native task cards/editors show planning data, the editor can clear work time separately from its deadline, and task/widget Today/Upcoming classification uses the earlier explicit commitment. Refresh adopts supported native plan fields. An outside-draft graph change can be handled by explicitly unlinking a separate draft copy, renaming/rescheduling it and reviewing it; the original task remains unchanged.

Work-start reminders, multiple sessions per step, richer native hierarchy navigation/rescheduling, rolling-workspace restoration, deeper reasoning, independent evaluation and outstanding device/release gates remain open. Earlier saved workspaces gain native metadata on a newly reviewed application; the migration does not silently execute old proposals. No models, dependencies or network services were added. The emulator is stopped and work remains uncommitted on branch `work` at base `e5378ede1869d41be5642fd96fbeff1e6e223aa7`.

Validation: **96 necessary JVM tests passed**, zero failures/errors/skips, plus a fresh debug build in **3m 7s** (`/workspace/.setup/canonical-planning-verified-checks.log`). This includes 14 canonical planning cases, all 67 existing planner cases, 13 archive cases, the schema-12 migration chain and the plaintext task-migration regression. Coverage includes child-first graph allocation, conflict rejection before any writes, native work after workspace discard, saved versus unsaved reservations, native work refresh, separate-copy reconciliation, full-metadata undo, partial/colliding import IDs, malformed/cyclic graphs, encrypted merges with matching expected versions, rolling task snapshots, raw vault merge, Today/Upcoming classification and unchanged deadline-alarm timing. Schema 14 and its migration were exported/registered.

That checkpoint’s production/JVM Kotlin fingerprint: `7f67138b55204cdfec335e9f9d9b12561867252ab277c2a3b92f7a90eda6bed1`; including Android tests: `39bf5bda7145b6802f6ff8d2ad1c5c5a23638487e06bf395ad35b01dbfbd66fc`. Both matched before/after validation. Fingerprints hash sorted relative Kotlin paths followed by their contents. Results are preserved in `/workspace/.setup/canonical-planning-verified-results.json` and `.xml`; matching source hashes are in `canonical-planning-verified-source-before.json` and `-after.json`. The debug APK at that checkpoint is `app/build/outputs/apk/debug/app-debug.apk`, 62,186,654 bytes. The boundary guard passed for 79 source files; `git diff --check` passed. No full-suite, emulator or optimized-release rerun was required for this slice; release/native APKs retain earlier evidence.

Earlier attempts are not acceptance: the first new test compilation needed a nullable assertion; a subsequent run passed existing regressions but found two new fixture errors (JUnit return type and a bare Plan command after restore deliberately reset the mode). Those fixtures were corrected, the separate-copy reconciliation path was completed, and the final 96-case run above passed. Kotlin daemon startup retries recovered during the successful build. Previous evidence below belongs to its own source checkpoint.

## Prior continuation: qualitative requirements and preferences

`DiscussionCriteria.kt` adds explicit required/preferred properties, user-supplied true/false descriptions, a top qualitative priority, and scoped unknown-quality questions inside the encrypted comparison frame. See the [slice record](QUALITATIVE_CRITERIA.md). Cost/time analysis now includes known qualitative conflicts and asks about unknown requirements. Preferences remain soft; numeric-only questions inspect their factor without changing a qualitative priority. Switching the explicit top priority between a quality and cost/time keeps the other criteria. No automatic weights, scores, external knowledge or option selection are introduced.

Named/ordinal property statements support bounded affirmative/negative forms and atomic conflict repair. A short yes/no or matching property answer binds only to the current option/property; unknown replies do not create a false value. Resumption retains the question; changed options or another comparison question cancel its previous target. A choice with an unconfirmed requirement is tentative. Unknown required qualities and known conflicts block handoff, ask for clarification, keep Talk active and leave older workspaces untouched. Corrected criteria/evidence can resolve the conflict; explicit clearing relaxes a rule. Source statements, normalized descriptions and an explicit choice carry into the new plan as bounded facts, with no native task writes.

Requirements and option descriptions given before naming the options remain user-authored notes until references are available, then promote to typed comparison evidence. Stored native commands and old short answers cannot bind a new question. Conflicting earlier assertions remain available and block handoff until clarified; a successful correction removes the superseded generic assertion. Unrecognized requirements remain notes rather than silently becoming executable rules. State supports six properties, six rules and 36 option/property observations. Long descriptions are split into workspace facts below the existing size bound. Older numeric-only discussion frames remain readable. No database migration, model, dependency or network requirement was added.

The first qualitative checkpoint passed **103 focused JVM tests**: 36 discussion and 67 planner, zero failures/errors/skips, plus a debug build in 2m 6s (`/workspace/.setup/qualitative-focused-checks.log`). Its production/JVM Kotlin fingerprint was `cd77d44ecd3b48acf9d4ccdd7a1788973a70d12225fa5f46788c060f9baa77dd`; including Android tests: `fe8ba2509b7644ce6c7af16ebe033f4f626b2e15e1da812b343454db0fae1c46`. Results were preserved under `/workspace/.setup/qualitative-initial-results*`. Earlier-note promotion, its handoff conflict guard and bounded-capacity coverage followed this checkpoint; the canonical planner file was unchanged and the affected paths were checked below.

That **qualitative checkpoint** passed **39 affected discussion tests**, zero failures/errors/skips, and a fresh debug build in 1m 20s (`/workspace/.setup/qualitative-final-build.log`). Coverage includes combined numeric/qualitative analysis, hard versus soft criteria, primary-priority changes, unknown qualities across reload/resumption, negative/compound corrections, scoped yes/no authority, invalidated choices, keeping older workspaces, earlier-note promotion and conflict repair, legacy frames, and the six-option/six-property storage limits. That checkpoint’s production/JVM Kotlin fingerprint: `5612d038caaf8802911693618602e5e0a40382d10e07c80fd93eb6be8d7fec43`; including Android tests: `fd170c5b752be9c840e3fb454d2fa5b60fb69bc423fb70710582488cfe4d24f6`. Both matched before/after validation. Results were preserved in `/workspace/.setup/qualitative-final-results.json` and `.xml`. The debug APK at that checkpoint is `app/build/outputs/apk/debug/app-debug.apk` (62,176,996 bytes). The dependency boundary guard passed for 78 source files; `git diff --check` passed. The final follow-up did not repeat the already-passing 67 planner cases, full suite, release build, native tests or emulator matrix. Release/native APKs remain at earlier checkpoints.

Remaining scope includes graded/weighted criteria, uncertainty/ranges, richer logical and linguistic relations, independent conversation evaluation, native task hierarchy/work-session integration (subsequently addressed by the newest slice) and remaining device/release gates. Generic notes and supported binary traits do not establish unrestricted conversational understanding. The emulator is stopped. All work remains uncommitted on branch `work` at base `e5378ede1869d41be5642fd96fbeff1e6e223aa7`.

## Prior continuation: cost/time reasoning and contextual explanations

`DiscussionComparison.kt` adds bounded typed quantities, explicitly scoped upper limits, one cost/time priority, a missing-measurement question, and a remembered comparison view to the encrypted discussion frame. The [slice record](COMPARISON_REASONING.md) describes the design and supported journey. Named or ordinal options can receive cost and duration figures in one message. Decimal arithmetic calculates differences; duration units normalize to minutes; currencies remain separate. Missing figures and currency mismatches ask a focused question. Short replies fill only its captured option/measurement, including after explicit discussion resumption. Unknown values remain unknown.

Full comparisons identify known constraint violations and then use supplied figures for priority or Pareto analysis. Equal figures are a tie. An overall trip budget is not silently treated as a per-option bound. Single-factor questions such as “which is cheaper?” or “which is faster?” preserve the user's broader priority and disclose known limit violations. Follow-up “why?” explains the current comparison view rather than switching objectives. These calculations do not select an option, infer qualitative scores, invent prices or perform exchange-rate conversion.

Conflicting figure/limit changes require a correction; an unaccepted contradiction in a compound measurement edit changes neither figure. Changing options clears the pending measurement target and drops removed options' figures. Figures, limits and priorities can be explicitly cleared. A choice known to violate a limit is declined. If later evidence invalidates an existing choice, handoff asks for a correction or another choice, stays in Talk, and preserves older workspaces. Successful handoff retains compact comparison evidence, limits, priority and the user's explicit choice as workspace facts, creating no native tasks or invented steps. Older frames without comparison state remain readable. There is no database migration or new dependency/model/network requirement.

The first comparison checkpoint passed **92 focused JVM tests**: 25 discussion and 67 planner, zero failures/errors/skips, in 2m 7s (`/workspace/.setup/comparison-focused-checks.log`). Its production/JVM Kotlin fingerprint was `533c9a7455b5ec74d529f75f38bea144f899ca2d471cbd4c24a3f60beb5541ea`; including Android tests: `f8d82dd37411e2ee0639a04ed4852bc34a4373d50437c0dbf5ba55bb8d5abe0b`. XML/summary results were preserved under `/workspace/.setup/comparison-initial-results*` before narrower runs replaced Gradle's reports. The single-factor query follow-up passed 26 discussion tests and a debug build in 1m 36s (`/workspace/.setup/comparison-final-build.log`); its final explanation-view refinement was then checked below. The planner/handoff implementation is unchanged after the 92-test checkpoint.

That comparison checkpoint passed **26 affected discussion tests**, zero failures/errors/skips, and a fresh debug build in 1m 4s (`/workspace/.setup/comparison-context-final.log`). Coverage includes numeric trade-offs, priority changes, hard-limit conflicts, keeping older workspaces during blocked handoff, missing figures through reload/resumption, currency clarification, compound correction atomicity, ordinal references, removed options, unknown/cleared quantities, equal figures, legacy discussion frames, and single-factor follow-up explanations. Its production/JVM Kotlin fingerprint was `1aaf89e0f95b24b929fb23a54c14ca00f3c7156b989ef21b9d7d6eb375e83534`; including Android tests: `f195945de5b8a1dd7c88d6e2227b21f1f88b4623a555c3e43c91251f42cc352e`. Both matched before/after validation. Results are preserved in `/workspace/.setup/comparison-context-final-results.json` and `.xml`. That debug APK was 62,152,454 bytes. The chat dependency boundary guard passed for 77 source files, and `git diff --check` passed. No full-suite, release, native-test or emulator rerun was performed for those deterministic dialogue changes.

Remaining scope includes typed qualitative criteria, uncertainty/ranges, broader semantic reference/correction coverage, independent conversation evaluation, native task hierarchy/work-session integration, and the remaining device/release gates. This slice does not complete those requirements. The emulator remains stopped; work is uncommitted on branch `work` at base `e5378ede1869d41be5642fd96fbeff1e6e223aa7`.

## Continuation: structured discussions and lookup presentation

`AssistantDiscussion.kt` adds one bounded discussion frame to the existing encrypted session. It retains the user's topic, up to twelve short details, up to six comparison options, an explicitly chosen option, and the current question. Discussion replies can recap those details, explain a question, handle uncertainty, replace a uniquely identified detail, and correct keyed budget/timing/destination details without discarding other evidence. Conflicting or ambiguous corrections ask for a precise replacement. Questions are not recorded as asserted details. The comparison response organizes user-supplied evidence and asks about missing trade-offs; it does not invent prices, facts, or a winning option.

Explicit discussion openings include “let's talk through …”, “I'm deciding between …”, and “I need to decide …”. Trip/exam remarks retain their supplied context instead of offering a redundant answer form. Within an active discussion, first-person requirements such as “I need to keep my evenings free” remain discussion details. Explicit task commands, clock/arithmetic requests and app questions retain their existing routes. Bare “undo that” explains how to correct the discussion; `/undo` remains an explicit saved-app-change request.

“Let's plan it”, “turn this into a plan”, or bare `/plan` transfers the active discussion into a **new** durable workspace, preserving details/options/current choice and leaving older workspaces intact. Handoff also handles the `/talk` and `/plan` prefixes the actual composer sends. It creates no task records or invented steps. Existing review/version/idempotency rules own subsequent saves. A pending field cannot be captured by an implicit pronoun handoff. Quoted, negated and hypothetical planning language does not grant handoff authority.

Discussion context has the existing fifteen-minute session/frame lifetime. A clock, app-help or task interruption can leave the frame dormant; “back to our discussion” explicitly resumes a still-live frame and cancels current confirmation authority. It does not restore an old task confirmation. Pronoun handoff requires an active discussion; expired, cancelled, forgotten or replaced social context cannot be silently reused. `/clear` and existing forget-conversation behavior remove the frame. No personal profile, Notes access, network call or model is introduced.

Dictionary definitions, sense follow-ups, word comparisons and bookmark-success replies no longer append Wordset/WordNet/“dictionary credits” notices. The bundled attribution/license files and explicit credits/source/license commands remain available. Dictionary data verification passed unchanged: 107,945 headwords, 108,128 source entries and 163,274 senses.

The structured-discussion checkpoint passed **101 focused JVM tests** (13 discussion, 21 offline-expansion and 67 planner), zero failures/errors/skips, and a debug APK build in 2m 14s (`/workspace/.setup/discussion-verified-checks.log`). Its production/JVM Kotlin fingerprint is `01a1cd78fe92b9dc12f4ad6120110bd9c28c972d30d23f818cead79f3cb16b4e`; including Android tests it is `5fd794c0e8867e0d38efc8e3b4a6bb4cfdc6c0fcb735d8bbf163a557c793e3d7`. Earlier local attempts caught reminder-field interception, arithmetic interruption and empty `/plan` review regressions; those failed attempts do not establish acceptance. The successful run includes their fixes. Dictionary presentation and explicit discussion-resume changes followed that checkpoint. The planner implementation is unchanged by those follow-ups.

That discussion/lookup checkpoint passed **36 affected JVM tests** (15 discussion and 21 offline-expansion), zero failures/errors/skips, and a fresh debug APK build in 1m 27s (`/workspace/.setup/discussion-and-dictionary-final.log`). Its final run includes live/expired discussion resumption, cancelling an unrelated confirmation before resuming, explicit saved undo versus discussion corrections, and dictionary replies without repeated credits. The unchanged 67-case planner suite was not repeated after that follow-up. Its production/JVM Kotlin fingerprint was `5ba9ca47c32929722cda2dd184b11a80baf0de5b1286ca9a70cc976b4a312cca`; including Android tests: `17c33e725f8fc72f92244a6985d1bf2daf29083262aa5cf433b7d07f4c5617d7`. Both fingerprints matched before/after that run. Its debug APK was 62,122,465 bytes. Boundary checks passed for 76 source files, dictionary verification passed, and `git diff --check` passed. No emulator was started. Release and Android-test APKs remain at earlier checkpoints.

One supported end-to-end journey is:

```text
/talk let's talk through a trip to Goa
options are train or bus
I care about quiet travel
budget is 10000
actually budget is 15000
dates are not fixed
compare them
let's go with the first one
/talk what time is it
/talk back to our discussion
/talk let's plan it
```

The result is a separate unsaved plan workspace containing the corrected budget, user-supplied constraints and current choice; no native task has been created. Continue in Plan with explicit steps/scheduling and the existing review/apply flow.

This is a compositional discussion foundation, not unrestricted conversation or independent natural-language evaluation. Hobby/story packs outside this frame remain bounded. Topic extraction is conservative; opening text is retained as a topic, not fully decomposed into every possible semantic fact. Long-term general-conversation retention, richer per-option reasoning and independently authored held-out evaluation remain open. Native hierarchy/work-session persistence, predictive Back/OEM coverage and the remaining roadmap are still incomplete. No full-suite, release or native rerun is implied by this JVM continuation.

## Prior Back/device checkpoint and validation policy

The Back/device checkpoint's production/JVM Kotlin fingerprint is `379b39f529e341506622544202ad089af986dde9ceab94fd64fdaf75faa9c143`; including Android tests it is `0f0d503dda30395027c424ff6a63574ec533d67e13d47701588947652aaa81cf`. Its application/test-APK build passed in 2m 8s; the intentional-exit test correction then passed a test-APK-only build in 39s. Application code was unchanged by that fixture correction.

The shared Back handler now requires an explicit close action and reads live root-window IME visibility when Back arrives. This avoids relying on a composed visibility snapshot that can remain true in translucent windows after the keyboard is hidden. Task/habit sheets, quick-add, chat, main notes, and quick notes all use this single-callback contract. Quick-note save-before-close and a task sheet passed a focused two-scenario rerun; main-note passed its subsequent targeted run. The final chat-only fixture-corrected run **passed**: `OK (1 test)`, 184.801s (`/workspace/.setup/chat-back-final-device.log`). All six scenarios now have passing focused runs on Android 10; they were not all repeated together after each local correction. The detailed attempt history below distinguishes failed attempts, their fixes, and passing reruns.

Per the user's explicit preference, those post-checkpoint keyboard changes were checked with builds and affected native scenarios. The 998-test full-suite evidence belongs to the preceding compact-composer fingerprint; planner/backup logic was unchanged for that Back-only refinement. Later discussion/planner changes have their own evidence above. The optimized release APK belongs to the older planner checkpoint and has not been rebuilt for subsequent changes. No claim is made that the entire roadmap, Android 15/predictive Back, OEM devices, process-death recovery, motion, or accessibility acceptance is complete.

## Current refinement: compact composer and broader editor acceptance

The first passing device screenshots exposed duplicate shortcut rows consuming excessive conversation space. The composer now uses a single input card with an animated blue mode label above the text, one toolbar, a mode menu, and an options menu for existing commands and reminder settings. Enabled priority/persistent options retain visible status icons. Microphone/send controls remain directly accessible with Material touch targets. Mode selection still preserves text and cursor state; the native mode test now follows the mode menu.

Three additional Android scenarios cover habit keyboard-first Back plus recovery after sheet reopening, main-note keyboard-first and second-Back close behavior, and quick-note keyboard-first followed by a verified database save on second Back. The main-note scenario hosts the actual editor composable with controlled callbacks; it does not establish full native navigation or note persistence. Quick-note uses the real activity/database and cleans up only the unique note it creates.

The production/JVM Kotlin fingerprint is `46bf3e1a806055391ab40a4730c4ebe59f5749341d256ad9dc15b97dff2897cf`; including six Android scenarios (with the final chat keyboard assertion) it is `b168e220743023f8f15ee3ba46846ec96471cb6df9e6ec55166199a53eb38e6a`. Application changes after the prior validated checkpoint are confined to `AyvaComposerBar.kt`; the planner/backup engine is unchanged. The first refinement build was stopped after final label/status layout changes, so it is not validation of this frozen version. The fresh functional/debug build **passed all 998 tests across 62 suites**, with zero failures/errors/skips, in 6m 5s (`/workspace/.setup/compact-composer-final-functional.log`). The final Android test assertion was added afterward. Its test-APK-only rebuild **passed in 34s** (`/workspace/.setup/compact-composer-final-device-build.log`); application/JVM source is unchanged. The extended chat scenario now checks keyboard-first Back before mode selection and recreation. Both APK installations reported `Success`. The six-scenario Android run completed in 378.189s: **five passed, one failed**, no ignored tests (`/workspace/.setup/compact-composer-device-tests.log`). Task-sheet, quick-add, chat Back/mode/recreation, habit reopening, and main-note checks passed. Quick-note preserved the typed text and hid the keyboard, but second Back did not finish save-and-close within 60s. Device logs show the note save beginning only during test teardown; this is being addressed by consolidating the quick-note Back path into one IME-first callback. The first focused quick-note rerun still timed out in 119.443s (`/workspace/.setup/quick-note-back-fix-device.log`); logs show no save-and-close callback before test teardown. The next correction reads current root-window IME insets at Back dispatch rather than a potentially stale composed snapshot. The live-insets debug/test APK build passed in 47s. Its focused native rerun **passed both quick-note and task-sheet scenarios**: `OK (2 tests)`, 119.859s (`/workspace/.setup/live-insets-back-device.log`). Logs confirm the root mismatch: native keyboard visibility was false while composed visibility remained true. The quick-note save then completed and its unique database row was verified. Chat and the main note editor now use the same single-callback contract, with the close action required at all call sites. The debug/test APK build passed in 2m 8s. The focused two-path run (204.636s, `/workspace/.setup/editor-back-final-device.log`) passed main-note; chat reached its requested final close, but Espresso raised `NoActivityResumedException` for closing the last activity. The test is being corrected to use `pressBackUnconditionally()` only for intentional exits, retaining the actual `isFinishing` assertion and all keyboard/text/mode checks. The fixture-only APK build passed in 39s and the chat-only rerun passed in 184.801s. Application source is unchanged by that fixture correction. Do not count the earlier failed attempts as acceptance. The post-checkpoint application changes are confined to Back handling in the shared helper, quick-note, main-note, and chat. The user subsequently requested necessary tests only: do not repeat the full suite or optimized release for these UI/Back changes. The existing release APK belongs to the preceding planner checkpoint and does not contain the latest composer/back refinement.

## Continuation: backup continuity and conversational repairs

Implemented:

- Encrypted exports include versioned plan workspaces and report their count in backup/restore summaries. Imported workspaces receive fresh IDs; save/undo tokens, pending questions, and old edit focus are not restored.
- Merge restores map linked task snapshots onto newly assigned task IDs while preserving full expected versions. Missing links remain visibly unresolved until the user removes or explicitly unlinks the draft step. A stale native edit is not silently adopted.
- Replacement restores clear obsolete workspace/session references, including when importing an older backup without planning data. Merges preserve local workspaces and their active selection while cancelling action authority. Workspace capacity and malformed graphs are checked before mutation.
- Backup/restore shares the chat-turn lock. Existing rollback captures encrypted plan/session files and mode preferences; a failure injected after installing new plan files restores the old tasks, workspace bytes, conversation and mode.
- Typed edit focus supports duration corrections, deadline versus work-time clarification, clock-only repairs on the chosen day, relative earlier/later moves, independent clearing of work times/deadlines, and compound word/fraction durations.
- Date and reference clarification retain the original relative-day anchor across midnight. Meaningful unfinished changes block save review; cancellation and uncertain replies leave previous draft values intact.
- Conflicting work can offer a feasible later slot on the requested day, respecting the known availability window and other plan sessions. Accepting it rechecks current constraints. The user can instead change the proposed duration/time or leave it open. Other sessions are never moved by this negotiation.
- “Why did you ask?” explains the current clarification rather than returning an unrelated explanation of dependencies.
- Pending moves to Trash can be cancelled without adopting or overwriting a newer native task edit. Explicitly recognized no-op clauses no longer cause an otherwise valid compound draft edit to be rejected.

These additions are deterministic and introduce no dependencies, models, services, or database migration. Canonical native hierarchy/session storage and its reminder/UI integration remain open. Plan export/restore support here refers to the password-protected full backup; rolling/pre-operation snapshot restoration of planning data remains separate work.

New supported examples:

```text
give Research one hour and thirty minutes
actually, forty-five minutes
move it half an hour later
actually tomorrow at 9am
work time
why did you ask
use that time
unschedule it
clear deadline for it
leave it open
unlink Missing task
cancel removal of Research
cancel pending removals
```

The first focused run exercised 63 tests, with one failure in the injected application's context fixture. Correcting the fixture made all nine archive integration tests pass (`/workspace/.setup/planning-archive-recheck.log`). The next run exercised 59 tests and exposed ordering/date interception and separated-minus duration cases; both were retained and their routing corrected. The next focused run passed all 90 tests (`/workspace/.setup/planning-continuity-tests.log`). Two final stale-removal/no-op regressions were then added. These are authored regression scenarios, not independent held-out generalization evidence.

A subsequent adversarial check reproduced five failures around hypothetical follow-up, save authority, leaked acceptance instructions, return-to-plan prompts, and compatibility with earlier receipt hashes. These scenarios were retained and corrected. Hypothetical previews now explicitly suspend real edits and save authority until the user returns to the actual plan; returning restates the real outstanding question. Presentation-only fields remain outside the canonical receipt hash. All **98 focused tests passed** (`/workspace/.setup/planning-hypothetical-tests.log`).

Full validation of the frozen continuation checkpoint **passed 998 tests across 62 suites**, with zero failures, errors, or skips, plus debug and Android-test APK builds in 6m 54s (`/workspace/.setup/planning-final-functional.log`). The planner suite has 67 scenarios and the archive suite has 11. The functional/release Kotlin source fingerprint is `145395eebace54052f577e9f24148af75c1f3a592c61fc537f201284d9f4254a` (sorted relative Kotlin paths plus file bytes, excluding `androidTest`). Including device tests, it is `1b6417294000866a29e2de4da20c960325fa97fcb062fd6890d690be65bfe5df`. These are authored regression scenarios, not independent held-out generalization evidence.

The optimized unsigned release build for this fingerprint **passed in 12m 7s**, including R8 optimization and APK packaging (`/workspace/.setup/planning-final-release.log`). Both Kotlin fingerprints still match after completion, the debug APK v2 signature verifies, and the boundary/dictionary/whitespace guards pass. The earlier 992-test checkpoint and optimized release passed, but are superseded by the hypothetical-view hardening above.

Three native scenarios in `app/src/androidTest/java/com/focusbyrj/app/ui/EditorBackDeviceTest.kt` cover task-sheet keyboard-first and second-Back dismissal, quick-add Back and recreation recovery, and persistent chat mode/draft recreation. Their APK builds successfully. Device execution is separate from the 998 JVM tests. The first attempt reached the real task editor and entered the test draft, but Android displayed “System UI isn’t responding” over it (captured in `/workspace/.setup/device-live.png`). The runner was deliberately stopped for emulator recovery; its `Process crashed` termination records that stop, not an independently observed application crash. This interrupted attempt establishes no Back/IME acceptance result. The retry, with no build running alongside it, **passed all three native tests**: `OK (3 tests)`, runner result `-1`, 445.604s (`/workspace/.setup/editor-device-retry.log`). It verifies real task-sheet IME-first/second-Back behavior, quick-add draft recovery across activity recreation, and chat mode/text persistence across activity recreation on Android 10.

This host has no `/dev/kvm`. The Android 15 software emulator failed inside ART/system_server during boot (`SuspendThreadByThreadId timed out`); that is not an app acceptance result. A smaller Android 10 AOSP emulator has completed boot. A workspace-local Python ADB client establishes shell connectivity and the application is installed. Native ADB initialization requires a directory in the read-only home. The tooling workaround changes no application dependencies or network behavior. The three scenarios above have completed successfully on Android 10. Android 15/predictive Back, OEM devices, process-death recovery, motion, and accessibility acceptance remain open. The three additional habit/main-note/quick-note scenarios have passing runs, with the quick-note correction and narrower reruns recorded in the latest-source section.

## Foundation implemented in the first slice

- Persistent Plan/Talk/Drill selection, blue mode suggestions, and a mode label in the composer. Selecting a mode preserves existing text and selection; sending an explicit slash command retains that command's authority.
- Encrypted, recoverable drafts for task editing/creation, quick-add, habit editing/creation, and the chat composer. Drafts are removed after the database write succeeds. Late lifecycle saves cannot resurrect a cleared draft.
- Keyboard-first Back callbacks inside task/habit sheet windows and in quick-add/chat and the two main note editors. Task/habit sheets explicitly delegate Back handling to the callback in their own window.
- Social greetings no longer deliberately fall through to an agenda. Context task counts exclude deleted/trashed items and occasions; next-due context uses a seven-day horizon. Explicitly opted-in suggestions retain their existing behavior.
- Encrypted plan workspaces independent of the expiring action session, with up to ten named workspaces and 100 steps each. Plans retain goals, facts, stable step IDs, hierarchy, dependencies, durations, deadlines, work intervals, pending clarification, and linked task snapshots.
- Conversational addition, rename, removal, ordering, reference clarification, duration/date clarification, optional domain starting points, plan explanation, hypothetical edits, scheduling into explicit daily windows, and plan resumption.
- Deadline/session distinction, overlap/dependency/cycle checks, bounded partial scheduling, date/time ambiguity handling, and rejection of invalid or repeated DST clock times.
- Reviewed revision-bound task creation/update/trash, outside-edit checks, transactional idempotency receipts, recovery after commit interruption, the existing reminder/widget outbox, and the existing version-aware task undo gateway. A separate one-turn draft undo prevents draft edits from falling through to an earlier task commit; saved undo is bound to the workspace that created it.
- Legacy planning cards invite replies in chat instead of opening a second answer form. Informational actions use compact chips.

## First-slice integration boundary (historical; superseded)

The following describes the original checkpoint. Schema 14, native editing, work-start reminders and multiple sessions are now implemented; see the latest delivery record.

Plan hierarchy and work intervals are durable **workspace data**, now included in full encrypted backups. Committing creates or updates canonical tasks with actual due dates; it does not reinterpret work-session starts as task deadlines. Existing task details are preserved on updates. Work-session reminders, normalized native task hierarchy, and native task-screen session editing are not implemented yet. The main database schema has not changed.

The language grammar is bounded and supports specific constructions below. This is a working foundation, not a claim of unrestricted conversational understanding. The old assistant continues to serve other app features and legacy planning commands through compatibility paths.

## Try the implemented journey

```text
/plan a trip to Goa
Goa, three days next month, around ₹15,000. I can prepare only after work.
suggest steps
use suggested steps
give Research transport 30 minutes
give Research transport 45 minutes
schedule Research transport on Saturday at 9am
add Pack bag for 20 minutes
Pack bag depends on Research transport
schedule Pack bag on Sunday at 9am
what if rename Pack bag to Packing
show plan
save plan
apply plan
```

Optional template steps and user-added steps should have distinct names. Work-session conflict checks cover Plan workspaces, not an external calendar. `I'm free from 6pm to 9pm` asks whether the window is for today or repeats daily, then schedules eligible steps over that explicit scope. Existing sessions are preserved. Missing durations or infeasible steps remain unscheduled.

Other supported interactions:

```text
add subtask Pack charger under Pack bag
rename the second one to Check options
move Pack bag before Research transport
deadline Research transport is Friday
show plan
why this order
/talk hello
/plan
/plan list plans
/plan new plan Exam preparation
/plan resume plan 1
/plan refresh saved tasks
/plan undo
/plan undo saved changes
/plan pause
/plan discard plan
```

`refresh saved tasks` explicitly adopts current linked task titles/deadlines/snapshots before a fresh review. It does not overwrite task details. A missing/trashed linked task requires reconciliation rather than silent resurrection. Undo changes saved tasks through the existing gateway and refreshes the workspace task links, retaining the proposal as an unsaved draft. After outside native edits, explicitly reconcile linked task data before another save. Use `/plan discard all plan drafts` to remove all workspace drafts, including recovery from a malformed workspace file, without deleting saved tasks.

## Discoveries incorporated during implementation

- A real multi-fact trip utterance exposed the danger of treating any occurrence of “after” as a dependency. Bare dependency wording now requires two resolved plan-step references; “after work” remains availability information. The original utterance is retained as a regression scenario.
- A daily availability window is not inferred from “I'm free from six to nine.” The assistant asks today versus daily before arranging work, and retains the window while awaiting that answer.
- Relative requested times are resolved before a missing-duration question; the clarification does not silently shift “tomorrow” to another date on resumption.
- Review chips carry plan ID, revision, and a fresh review token. A chip from another plan cannot apply the current one, even if both have been reviewed.
- A crash between the task transaction and workspace reconciliation reuses its durable receipt. The regression simulates restoring the earlier workspace and checks that tasks are not duplicated.
- Editor ownership prevents a completed save's late callback from deleting the next form's draft. An unreadable encrypted draft is retained rather than overwritten with an empty form.
- Scheduling local window boundaries uses calendar wall times, not a fixed number of elapsed hours, so a daylight-saving transition does not extend availability past the chosen local closing time.
- Draft undo and saved-task undo have separate ownership. Undo after an unsaved edit restores that draft turn; changing workspaces cannot undo a previous workspace's task commit. Explicit “undo saved changes” uses the current workspace's still-valid saved-task undo token.
- “Let's plan together” opens an unanswered goal frame. Supplying a goal and destination in the next message fills both, instead of retaining the entry phrase as the goal and asking for the destination again.

## Requirement coverage

Current implementation is bounded and English-first. Audits below are deferred at the user's request; passing focused correctness checks does not establish unrestricted conversation or device/release acceptance.

| ID | Implemented behavior | Deferred acceptance or expansion |
| --- | --- | --- |
| R01 | Offline, no model/inference service or new dependency | Independent dependency/data-flow audit |
| R02 | Main-composer plan creation, refinement, review, apply and undo; no mandatory second answer form | Held-out end-to-end journeys and user evaluation |
| R03 | Persistent Chat/Plan/Talk/Drill, blue suggestions and composer mode label | Broader command arbitration and accessibility/device verification |
| R04 | Relevant greetings/agenda, structured discussion, listening, numeric ranges, binary requirements/preferences, contextual explanations | Graded/weighted reasoning, richer knowledge/language and measured conversation quality |
| R05 | Canonical parent/child tasks, prerequisites, multiple work intervals, deadlines, native editor/navigation, work-start reminders and bounded constraint scheduling | Advanced optimization, progress-driven scheduling and device performance |
| R06 | References, temporal repairs, conflicts, dependent replanning, range/limit clarification and isolated hypotheticals | Broader semantic composition and independently authored failure corpus |
| R07 | Encrypted durable workspaces, explicit named discussion memory, templates/defaults, archive/rolling recovery with fresh authority | Long-running retention and resumption evaluation |
| R08 | Keyboard-first Back and encrypted draft recovery across main editors; six prior focused Android 10 scenarios passed | Predictive Back/OEM/lifecycle/process-death matrix and remaining popup inventory |
| R09 | Compact controls, mode suggestions and animated composer label | Reference visual/motion review, large fonts, TalkBack and reduced motion |
| R10 | Reviewed/versioned transactional writes, idempotent receipts/outbox, bounded multi-level draft and saved undo/redo with crash retry | Broader failure injection and production lifecycle audit |
| R11 | Capability ledger, authored domain suggestions, reusable graph/constraint primitives and regression scenarios | Held-out replay/benchmark tooling and expansion experiments |
| R12 | Schema 14 compatibility, full/rolling archive remapping and rollback, reminder reconciliation, chat boundary guard; blocking overlays unchanged | Independent migration/security audit and optimized-release verification |

## First-slice evidence (prior source)

The first targeted run passed 15 planner tests. The first full run executed 935 tests and found two tests requiring the deliberately replaced agenda greeting; those tests now check conversational greetings and explicit agenda access. The second integration run executed 947 tests and found the trip “after work” precedence bug; the grammar was corrected and the scenario retained.

A subsequent completed integration run passed 949 tests with zero failures, errors, or skips and both APK builds. Additional undo ownership, undo reconciliation, goal-entry, and complete-review scenarios were then added. Superseded builds were stopped rather than treated as validation of newer source.

The final functional run **passed all 954 tests across 61 suites**, including 34 new planner/draft/mode scenarios, with zero failures, errors, or skips. The debug APK build passed in the same command. Its log is `/workspace/.setup/conversational-functional.log` (10m 24s). All test XML reports were regenerated in this run. Its Kotlin source fingerprint is `db1087b29f94f482a2df0b6ad7bc890b4e09991b442fabdfcff609dece100f42`; application source remained unchanged throughout validation. The fingerprint hashes sorted `app/src/**/*.kt` relative paths followed by their file bytes.

The first attempt at this final run hit a HotSpot C2 compiler SIGSEGV in the existing `FocusStatsManager.refreshStats` method, before the suite completed. Crash/replay reports are retained under `/workspace/.setup/jvm-crash/`. The rerun uses `/workspace/.setup/test-runtime.gradle` to apply `-XX:TieredStopAtLevel=1` only to Gradle test processes. This is an environment workaround, not an application source change or a disabled test. Release builds retain their normal optimizer settings.

The separate **optimized unsigned release build passed** in 12m 34s, including R8 optimization and APK packaging; its log is `/workspace/.setup/conversational-release.log`. The source fingerprint was rechecked after completion and still matches the functional run. The chat boundary guard (70 source files), dictionary verification, and `git diff --check` passed. The debug APK's v2 signature verifies. Both APK manifests identify `com.focusbyrj.app`, version `2.0.9` (109), min SDK 24 and target SDK 35.

Artifacts:

- Debug: `app/build/outputs/apk/debug/app-debug.apk`.
- Unsigned release: `app/build/outputs/apk/release/app-release-unsigned.apk`.

These are historical build and functional results for the first-slice fingerprint. Current continuation evidence is recorded above; neither constitutes publication.

Reproduce from `/workspace/Ayva`:

```bash
source /workspace/.setup/java-env.sh
./gradlew :app:testDebugUnitTest :app:assembleDebug -I /workspace/.setup/test-runtime.gradle '-Pkotlin.daemon.jvmargs=-Xmx8192m -XX:MaxMetaspaceSize=1024m -XX:+UseParallelGC' --no-daemon --max-workers=2
./gradlew :app:assembleRelease '-Pkotlin.daemon.jvmargs=-Xmx8192m -XX:MaxMetaspaceSize=1024m -XX:+UseParallelGC' --no-daemon --max-workers=2
```

The test emulator is stopped after validation to release CPU; restart/readiness instructions are saved in the cloud configuration. Live processes are not part of the reusable snapshot.

The cloud configuration draft's complete install/start instructions were updated to generate/use the test-only workaround. Configuration revision 6 also saves optional Android 10 device-tool installation (enabled with `AYVA_DEVICE_TEST_SETUP=1`), the local ADB client, restart/readiness checks, APK installation, six-scenario and focused native-runner guidance, and the user's necessary-tests preference. The optional installation block passed a cache-preserving recheck; full installer syntax and configuration read-back match. The client's total shell-run allowance is now 1800s for slow software emulation; scripts/start instructions were read back at revision 6. Publication and fresh-instance restoration are not verified. Saving the draft does not publish it or verify a fresh task's restoration. Work remains uncommitted on branch `work`, based on `e5378ede1869d41be5642fd96fbeff1e6e223aa7`; the source fingerprint, not the base commit alone, identifies the tested application code.

Native UI/IME/animation outcomes are recorded in the continuation section above. A compiled device-test APK alone is not acceptance evidence.

## Deferred audits and future expansion

The user's latest instruction authorizes completing implementation with only necessary checks and conducting audits later. The bounded core implementation is in place; the remaining work below is not silently treated as verified.

1. Independently authored conversations and replay traces to measure language composition, correction quality, interruptions and cross-domain usefulness.
2. Predictive Back, OEM/window/IME and process-death coverage; visual reference, motion, large-font and TalkBack review. Six core Android 10 scenarios have prior focused passing runs.
3. Named-device performance/retention measurements and broader migration, restore and failure-window audits.
4. Optimized release build and rollout acceptance for the final source. Existing release and instrumentation APKs belong to earlier checkpoints.
5. Expansion tracks from roadmap sections 14–20: graded/weighted reasoning, richer languages/knowledge, deeper dialogue arbitration, progress-aware replanning and advanced optimization. These need concrete journeys and promotion evidence, not isolated phrase batches.

Work remains uncommitted on branch `work` at base `e5378ede1869d41be5642fd96fbeff1e6e223aa7`. No publication or rollout has occurred. The emulator is stopped.
