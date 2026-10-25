# Trip checklist conversation repair

Status (2026-10-22): **Implementation and 30 targeted conversation/compatibility checks pass; device acceptance remains open.** Continued from main `0a6d35d`. The original two failing Plan-prefixed conversations now pass. The final 15-trip-check rerun and debug APK build also pass. Actual Android UI replay is the only remaining acceptance gate.

Version bumped to **2.0.13 / 113** at the user’s request for GitHub delivery. The version push triggers the existing GitHub build/release workflow; device acceptance is still open. The chat layout, cats, slash composer, briefs, native Drill windows, task receipt/reschedule cards and Android blocking overlays were not changed. Everything remains offline with no model or new network dependency. Chat's Notes data boundary is unchanged.

## Contract

- Natural trip goals establish one named main task in a durable draft. Goal, destination, purpose, travel date, exam date, and return date have distinct meanings.
- A turn is interpreted against the draft and its pending question. Conversation controls precede slot answers; summary and pause never become facts or tasks.
- Ordinary checklist items save inside the native main task. Independently dated items save as linked child tasks so their dates remain actionable. Before/during/after groupings appear in the conversation and task details.
- Additions acknowledge the actual change once. Unknown text never creates tasks. Suggestions and speculative alternatives do not become agreed items.
- Changes and references operate on stable item IDs. Duplicates do not create another item. Ambiguous references ask which item, and compound edits either succeed together or leave the draft unchanged.
- A partial ordinal asks for the missing month unless an established travel date provides it. Date clarification survives restart, review, and unrelated additions. Changing travel moves explicitly relative preparation deadlines and preserves independent dates.
- Explicit completion such as “that's it” or “save it” authorizes saving the current draft. Pending changes must be resolved or cancelled. The existing transactional committer retains snapshot conflict checks, idempotent receipts, reminder reconciliation, and undo. Review/apply commands continue to work.
- Pausing retains the draft and exits Plan mode. Discarding a draft does not delete saved tasks.

## Necessary acceptance

Use full conversations through OfflineAssistant and inspect saved native rows:

1. Goa: partial date, multiple additions, during-trip activity, changed date, removal, summary, save, repeated save.
2. Exam trip: purpose versus city, dates, admit card/photos/pen quantity, process restart, pause/resume, finish.
3. Compound edit/undo, ambiguous references, unsupported compound rollback.
4. Relative preparation deadline versus independent child date, final parent links.
5. Pending date survives unrelated additions and summary; conflict blocks finishing until corrected.
6. Editing a saved checklist keeps the task identity; outside native edits cannot be overwritten.
7. Suggestions, speculative input, listening, duplicate prevention, quantity correction.
8. Empty /plan entry, invalid calendar dates, transport replacement, final native checklist.

Broader audits and OEM/UI testing are deferred. No new model or network dependency is introduced. This is bounded compositional English understanding, not a claim of unrestricted AI conversation.

## Numbered work disposition

0. **Fixed Plan-mode date routing.** Active trip facts get first refusal even when the composer adds `/plan`; an exam date no longer replaces the workspace. Explicit `new plan …` still starts another goal. The eight original journey checks passed immediately after this fix.
1. **Unified simple trip entry and safe old-draft migration.** Bare and detailed trip requests use the checklist owner. Eligible unsaved old lists migrate with workspace/item IDs, facts, order and dated deadlines retained. Graphs with native links, work sessions, constraints, milestones or unresolved edits stay with the advanced owner; they are not collapsed into checklist entries. Migration revokes old save authority.
2. **Restored advanced capabilities.** Typed duration/dependency/subtask/milestone requests promote the draft to the canonical planner, preserving IDs and saved links. `use advanced planning` explicitly exposes the full graph planner. A fresh `/plan advanced trip` starts there directly. Exam-trip preparation retains its milestone flow. Open saved checklist entries become linked task drafts only on review/apply; completed entries remain completed on the parent. Templates remove task links/dates while retaining the work. Hypothetical scheduling from checklist mode explains the advanced route without changing the draft.
3. **Added compositional facts and choices.** Destination/date/arrival answers, noun needs, overnight choice plus checklist edits, and joint exam/travel corrections work through the public assistant entry. Date pairs are checked together, atomically. An initial date attached to the exam is stored as the exam date, not departure. Unsupported compound edits leave the items/facts unchanged.
4. **Hardened pending questions and reviews.** Unrelated additions/renames/removals retain pending dates. Ambiguous item references and item dates suspend and restore the earlier clarification. Pending edits, failed references and cancellations invalidate save review. Refresh preserves unresolved questions. Bare destination answers reject questions/control words; adding items preserves an unanswered destination question. Save reviews show the native checklist contents.
5. **Hardened projection and reconciliation.** Semantic item IDs/titles/dates must match native checklist or dated child projections. Refresh reads native checklist IDs/titles/completion, retains a native parent rename, and preserves relative dates when the native deadline still matches. Removal/re-addition of a saved dated item before committing reuses its ID/link rather than creating and deleting the same work. Native snapshot conflicts, receipt hashes, saved undo, archive/resume and encrypted archive guards remain in place. Preparation after departure and relative preparation moved into the past produce warnings.
6. **Broadened bounded wording.** Flying resolves to flights; noun additions, transport replacement, quantities, ordinal references and before/during/after wording for the actual destination are supported. Suggestions stay separate from accepted entries. This remains bounded English interpretation, not unrestricted conversation or verified travel/exam advice.
7. **Targeted checks passed.** Thirty selected checks passed: 15 trip journeys/projection regressions, the seven handoff compatibility cases, five additional existing cases affected by common trip entry, and three receipt/history/archive checks. No full suite or broad audit was run. Old assertions were changed only where the intentional native checklist shape replaced separate task rows. Final evidence is below.
8. **Device acceptance still required.** Build the debug APK and replay the journeys below on Android 16. `adb devices` reports no connected device; this environment has no installed emulator or `/dev/kvm`. Automated public-entry conversations inspect real Room task rows, but do not validate the actual keyboard, UI layout, popups or rendering. Do not call the repair release-ready until the device replay passes.

## Verification evidence

- GitHub Actions for the initial v2.0.13 push exposed five regressions in existing discussion-to-plan handoffs: the trip checklist owner replaced the original discussion goal and discarded handoff facts. The correction marks trip discussion handoffs for the advanced graph owner while retaining the facts and older workspace. All five previously failing `AssistantDiscussionTest` cases now pass locally, along with all 15 trip conversation checks. The full GitHub unit suite is pending on the follow-up commit.

- Original eight Plan-prefixed journeys after routing fix: **8 passed, 0 failures/errors/skips**.
- First compatibility run: **15 passed** (eight original journeys plus seven named handoff cases).
- Persistence regression run exposed a completed-checklist refresh omission: **15 passed, 1 failed**. Fixed the filter that omitted completed native checklist entries; retained the failing regression.
- Expanded targeted run: **30 passed, 0 failures/errors/skips**; Gradle `BUILD SUCCESSFUL` in 2m 38s. Included migration, simultaneous date corrections, clarification/review lifecycle, native refresh/completion, dated identity reuse, advanced saved conversion, suggestions, warnings, archive/resume and projection validation.
- Final focused trip run after preserving clarification through refresh: **15 passed, 0 failures/errors/skips**. Combined test/debug build: `BUILD SUCCESSFUL` in 3m 3s.
- Debug APK: **built successfully**, `app/build/outputs/apk/debug/app-debug.apk` (2.0.13 / 113, debug build; rebuilt successfully after the version bump).
- Chat data-boundary dependency guard: **passed (109 source files)**. No Notes dependency was added.
- `git diff --check`: passed for the final tree.
- Version bumped to 2.0.13 / 113 for the requested GitHub push. Local verification does not claim CI success or device validation.

Local logs for this cloud workspace are `/workspace/scratch/trip-routing-eight.log`, `trip-compatibility.log`, `trip-persistence.log`, `trip-final-targeted.log`, and `trip-debug-build.log`. They are diagnostic conveniences, not required repository artifacts.

## Targeted reproduction

Use JDK 21 and the repository Android SDK setup. In this workspace, source `/workspace/toolchains/ayva-env.sh`. Do not edit Kotlin during verification. The checked-in init script handles the previously observed cloud Robolectric C2 crash without disabling assertions.

```sh
./gradlew :app:testDebugUnitTest \
 --tests 'com.focusbyrj.app.util.TripChecklistConversationTest' \
 --tests 'com.focusbyrj.app.util.ConversationQualitySafetyTest.reportedJourneyReplacesIrrelevantGoalAndEditsDraftWithoutNativeWrites' \
 --tests 'com.focusbyrj.app.util.ConversationQualitySafetyTest.milestonesKeepTravelAndStudyFactsSeparateAndRetainReviewedNativeSave' \
 --tests 'com.focusbyrj.app.util.ConversationQualitySafetyTest.planComparisonRetainsFactsAndBlocksInvalidChoicesWithoutTaskWrites' \
 --tests 'com.focusbyrj.app.util.ConversationalPlannerTest.planningIsConversationOnlyAndNeverWritesBeforeReview' \
 --tests 'com.focusbyrj.app.util.ConversationalPlannerTest.planningTogetherAsksForTheGoalWithoutLosingTheNextRepliesFacts' \
 --tests 'com.focusbyrj.app.util.ConversationalPlannerTest.ordinaryUnrecognizedPlanRepliesCannotCreateTasks' \
 --tests 'com.focusbyrj.app.util.ConversationalPlannerTest.ambiguousReferenceIsClarifiedWithStableIds' \
 --tests 'com.focusbyrj.app.util.ConversationalPlannerTest.scheduledSubtasksRetainTheirOwnDurationsAndTimesAcrossReload' \
 --tests 'com.focusbyrj.app.util.ConversationalPlannerTest.multipleFactsAreCapturedWithoutRepeatingDestinationQuestion' \
 --tests 'com.focusbyrj.app.util.ConversationalPlannerTest.unresolvedChangesCannotSaveAndCancellationRequiresANewReview' \
 --tests 'com.focusbyrj.app.util.ConversationalPlannerTest.planIntentHashRemainsCompatibleWithPreContextReceipts' \
 --tests 'com.focusbyrj.app.util.AssistantGuidedPlanningTest.collaborativeTripRequestOverridesVacationHelpAndSavesOnlyAfterReview' \
 --tests 'com.focusbyrj.app.util.AssistantGuidedPlanningTest.collaborativePlanningParaphrasesKeepTheirOwnerAndNeverCreateTasks' \
 --tests 'com.focusbyrj.app.util.PlanningCompletionEssentialsTest.savedHistoryUndoRedoSurvivesSessionResetAndDoesNotDuplicateTasks' \
 --tests 'com.focusbyrj.app.util.PlanningArchiveIntegrationTest.encryptedBackupRoundTripPreservesGraphAndRequiresFreshReview' \
 -I tools/gradle/cloud-test-jvm.gradle -Pkotlin.compiler.execution.strategy=in-process \
 -Dorg.gradle.jvmargs='-Xmx4096m -XX:MaxMetaspaceSize=1024m -Dfile.encoding=UTF-8' --no-daemon --max-workers=2 --offline
python tools/check_chat_boundary.py
git diff --check
```

For just the final trip journeys and debug build, put the test filter immediately after the test task:

```sh
./gradlew :app:testDebugUnitTest \
  --tests 'com.focusbyrj.app.util.TripChecklistConversationTest' \
  :app:assembleDebug \
  -I tools/gradle/cloud-test-jvm.gradle \
  -Pkotlin.compiler.execution.strategy=in-process \
  '-Dorg.gradle.jvmargs=-Xmx4096m -XX:MaxMetaspaceSize=1024m -Dfile.encoding=UTF-8' \
  --no-daemon --max-workers=2 --offline
```

## Remaining device acceptance

Use the existing Plan composer, retaining its `/plan` prefix. Inspect the native task editor as well as chat replies. Future dates below are intentional reproducible examples; change the year when they are no longer in the future.

1. Goa: `I want to plan a trip to Goa` → `it's 26th` → `July 2029` → `Book a bus and pack clothes` → `I also want to buy apples` → `We should visit Baga beach there` → `Actually make it 28th` → `Remove buying apples from the list` → `what do we have till now` → `that's it`. Verify one main task dated July 28 with bus, clothes and Baga beach native checklist entries, no apples, and no duplicate after repeating `that's it`.
2. Exam: `new plan ibps exam trip` → `I'm thinking 26th` → `July 2029` → `the exam is on 27 July 2029` → `to Chennai` → `add Download admit card and bring photos and take 3 pens` → `summary` → `end`. Reopen `/plan`, confirm the draft survived, then `that's it`. Verify separate travel/exam dates in the conversation and details, one main task with the agreed checklist, correct native rendering, and no writes before finish.
3. Check keyboard/composer, reply scrolling and unchanged cards/cats/briefs/Drill on the device. Record device, Android version and observed result here. A physical screenshot/test result has not yet been supplied for this repair.

Continue from this implementation; do not restart a broad architecture or UI redesign. The remaining acceptance is device verification, followed by any separately authorized version/release work.

## Follow-up context refinement (2026-10-22)

This increment repairs two routing collisions: accepting suggestions is handled before ordinary item creation, and a full date answering an unresolved exam/return-date question retains that question's role. Accepting the same suggestions twice cannot create a literal “those” item.

Short factual questions about departure, exam and return dates read the draft without changing its revision or pending question. “Make that 4” adjusts the selected item's quantity; “make it 28th” retains its travel-date meaning. Items can move between preparation, during-trip and after-return groups using the existing stable-ID reference and ambiguity handling. Return before the exam is now a blocking date conflict, alongside travel after the exam and return before departure.

Acknowledgements and suggestion prompts are shorter. Suggestions remain opt-in. Two focused journey regressions cover these changes through the actual Plan-prefixed assistant entry and native task saves. All 17 focused trip conversation checks passed (zero failures/errors/skips), including both new journeys. The final noun-only quantity correction also passed its focused journey rerun (1 passed). Its first rerun caught a case-sensitive test expectation; the assertion now checks the unchanged noun and quantity without requiring lowercase. The chat data-boundary guard passed for 109 files. Device acceptance above remains open.

## Longer conversation transitions (2026-10-22)

The next increment covers these continuation patterns through the same trip owner:

- Select numbered suggestions (`add first and third suggestions`) or exclude named suggestions (`add those except accommodation`). Invalid or ambiguous selections do not add anything. The original suggestion numbering remains stable after partial acceptance, and duplicate acceptance remains harmless.
- Ask what is on the checklist, when an individual item is due, the destination, trip length, or what detail is needed next. These replies read the current draft without consuming its pending question or changing its revision.
- Correct the destination while retaining the checklist. Explicitly change exam/return dates or clear a named date. A bare day in an explicitly named date correction uses that role's established month before falling back to travel context.
- Move an existing item to a deadline relative to departure. Those deadlines follow later travel-date changes. Relative dates also answer an already pending item-date question.
- Clarify an unmatched item name without repeating the original edit. An interrupted date question resumes after item reference/date clarification; nested travel/exam date questions retain their separate roles.
- `skip` does not erase pending edits or conflicts and thereby authorize an unintended save. Users can keep planning, resolve the change, or explicitly cancel it.
- Polite requests (`can you change…`, `could you remove…`) reach the same typed edit handlers.

Three focused conversation journeys exercise these transitions, pending-state persistence, summaries, and final native task/checklist saves. All 20 focused trip conversation checks passed (zero failures/errors/skips); production and test compilation passed, as did the 109-file chat boundary guard and diff formatting check. Existing device acceptance remains open; this does not claim unrestricted natural-language understanding or validation of every possible conversation.

## Dated additions and checklist edits (2026-10-23)

- Natural additions such as `book flights by 20 July 2029` create dated child tasks directly. A later addition of the same title with a different date reschedules that item's stable ID instead of creating a duplicate.
- An incomplete date on a new item opens an item-date clarification, preserving an earlier travel/exam question. Finishing stays blocked until that clarification is resolved or cancelled. Explicitly rescheduling that item resolves its pending question; deleting it removes the obsolete question and resumes any suspended one.
- Group removals (`remove apples and photos`, `remove first and third`) resolve every item against the original ordering and apply as one undoable change. A missing or ambiguous item prevents all removals. Exact titles containing `and` are treated as one item.
- Phase-specific questions (`show preparation`, `what are we doing there?`) show the relevant checklist entries with their full-summary numbering and do not change the draft.
- Addition replies acknowledge the actual items and calendar dates concisely. Multiple incomplete dates in one addition require clarification before the batch changes the draft.

Three public-entry conversation regressions cover linked native task saves, stable rescheduling, clarification recovery/deletion, atomic removal and undo. All 23 focused trip conversation checks passed (zero failures/errors/skips), including the three new journeys. Production/test compilation, the 109-file chat data-boundary guard and diff formatting check passed. Device acceptance remains open.

## Contextual shifts and shared edits (2026-10-23)

- `move flights two days later` shifts the flight task's own deadline. Travel, exam and return shifts use the named date and retain existing conflict checks. An undated item asks for a date instead of using departure or today as an implicit starting point.
- Shifting an item already relative to departure adjusts its offset, so subsequent departure changes continue moving it. Explicit calendar dates remain independent.
- Date and phase edits accept several named or numbered items, resolve references against the original list, apply atomically and produce one undo checkpoint. Ambiguous or incomplete edits leave every item unchanged.
- Comma-form dates such as `June 20, 2029` are preserved inside additions instead of splitting the year into another checklist item.

Three necessary public-entry journeys cover date anchors, relative links, native task deadlines, shared edit rollback/undo and exam conflicts. All 26 focused conversation checks passed (zero failures/errors/skips), including these three journeys. Production/test compilation, the chat boundary check and diff formatting passed. Device acceptance remains open.

## Checklist progress in conversation (2026-10-23)

- `check off apples`, `mark apples as done`, `apples is done`, `reopen apples` and `mark apples as not done` update native checklist completion in the draft. Named/numbered groups resolve fully before changing progress; ambiguous or mixed dated/checklist groups leave everything unchanged.
- `what's left?` and `show completed items` read progress without changing the revision or consuming pending questions. Numbering matches the full plan; the full summary marks completed entries.
- Completion survives ordinary edits, draft persistence, undo and native checklist saves. Repeating the same progress update is a no-op. Progress does not silently finish or save the planning conversation.
- Dated task completion remains managed in Tasks; refresh saved tasks to bring it into the plan. Completed checklist entries must be reopened before conversion to dated tasks so completion is not silently lost. A completed main task must be reopened in Tasks before its checklist can change.

Validation: all 28 focused conversation checks passed (zero failures/errors/skips), including the two new progress journeys. Production/test compilation, the 109-file chat boundary guard and diff formatting passed. Physical-device acceptance remains open.

## Explicit plan controls and relevant checklist contexts (2026-10-24)

- A leading `.` in Plan mode marks an explicit question/change. It is stripped before command routing, and unknown dotted messages cannot become checklist items or generic topic answers. Existing plain summary/edit commands remain supported. Dotted commands without an active plan ask for a goal without creating one.
- Simple exam and grocery/shopping plans reuse the checklist engine and native parent/checklist save path. They retain exam/target dates and use checklist prompts instead of travel-city prompts. Explicit study/revision and advanced planning retain the graph workflow.
- Bare ordinals use the named date's established month, then the plan's primary date month, then the current month in the plan timezone. Past or invalid dates require clarification. Explicitly undecided months remain pending; regression journeys that need this state now state that uncertainty explicitly.
- Non-command item phrases can be added directly to a checklist. Summary questions read the current plan; `remove that` uses the last selected item. `clear` asks which scope; `clear checklist` removes draft items as one undoable edit, retaining the main task and date.
- Native refresh retains the correct primary date role for exam/list plans. Completion and existing transactional save protections are preserved.

Validation: all 31 focused conversation checks passed (zero failures/errors/skips), including new exam opening/date, dotted command, grocery checklist and calendar-default coverage. Production/test compilation, the 109-file chat boundary guard and diff formatting passed. The regression run also caught and resolved exam-date goal replacement, dotted undo routing and archive-as-item fallback. Device acceptance remains open.

## Normal-chat date/time task priority — v2.0.14 (114)

Normal Chat uses the existing SmartDateParser before implicit planning/discussion routing. A recognized date or clock time with task text enters the existing task creation, duplicate-check and action-execution path. Date-only input without a selected task opens a title question and retains its due date and composer flags. Explicit slash commands, Talk/Plan/Drill modes, live task questions and recognized task edits/corrections retain their existing handlers. This deliberately makes dated normal-chat text task-oriented; use Talk for discussion or Plan for a draft conversation.

Release validation: all 4 targeted task-routing/correction/duplicate checks passed, production/test compilation passed, and the chat boundary and diff formatting checks passed; prior planning and notes-widget verification is recorded above and in docs/widgets/NOTE_WIDGET_FONT_STABILITY.md. Physical-device verification remains open.

### v2.0.14 release-gate repair

The first release run stopped at unit tests, before APK build/signing/publication. Full-suite reproduction exposed normal-chat date priority stealing explicit focus, settings, routine and habit commands, incomplete reminders, live answers and compound operations. These retain their existing owners; duration-only phrases are excluded from the calendar probe. Ordinary dated chat still creates tasks. Tests for the previous dated-chat conflict behavior now assert task creation, draft journeys explicitly select Plan, and simple exam saves assert the parent/checklist representation. New checklist titles use the normalized goal rather than retaining a `new plan` prefix.

Validation: all 1,153 unit tests passed with zero failures/errors/skips; chat boundary, dictionary and diff checks passed. No CI gates were removed. Version remains 2.0.14 (114), which had not been published.
