# Trip checklist conversation repair

Status: **WIP handoff, not release-ready.** Production compilation passes. The final eight-case test run using actual Plan-mode message prefixing has **6 passes and 2 failures**. Existing-planner compatibility remains unfinished.

The 2.0.12 screenshots reopen planning acceptance. Prior feature completion and passing command checks did not demonstrate a useful full conversation. The reported failures are purpose becoming destination, ordinal dates becoming speculative text, and summary/stop requests falling into a generic clarification.

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

## Handoff requested by the user

The user asked to push the current work to `main` and continue in another Codex session. This is a **work-in-progress checkpoint, not completed planning acceptance or a new release**. Keep version 2.0.12 / 112 unchanged for this handoff.

### Implemented in this checkpoint

- `TripChecklistConversation.kt` introduces persistent trip/checklist state, calendar-date parsing, intent precedence, short change acknowledgements, before/during/after grouping, stable item references, draft review, explicit completion, and pause/resume.
- `PlanWorkspace.kt` stores optional trip state and native checklist entries on a step; older documents still read with defaults.
- `ConversationPlanner.kt` integrates natural trip entry and the checklist conversation, summary/stop/finish controls, and explicit completion into the existing transactional save path.
- `ConversationPlanStore.kt` saves ordinary entries in the main task's native `subtasksJson`; dated items are real child tasks. Existing receipt deduplication, snapshot guards, reminder retry and saved history are retained. Empty new fields are excluded from legacy content hashes.
- `TripChecklistConversationTest.kt` contains eight complete conversation/database checks. The final helper mirrors the composer by prefixing messages with `/plan` while Plan mode is active.

### Verification at handoff

- Initial production Kotlin compilation passed.
- Initial eight-case conversation run: **7 passed, 1 failed**, no errors/skips. The compound-change case exposed a trailing comma in `remove apples, and add ...`. Normalization now removes the trailing comma before resolving the reference.
- Final run, with composer-equivalent `/plan` prefixing: **6 passed, 2 failed**, zero errors/skips; production and test compilation succeeded. Total Gradle run 6m 48s. The comma/compound-edit regression now passes.
- Remaining failing methods: `examPurposeDatesAndQuantitiesSurviveRestartAndPause` (trip state becomes null) and `newDetailsDoNotDiscardPendingMonthAndFinishDoesNotSilentlySaveAnOldDate` (expected native save never occurs). Log in the current environment: `/workspace/.setup/trip-conversation-handoff-checks.log`. The failures remain reproducible in the committed tests; no assertion has been disabled.
- CI is not expected to pass this WIP checkpoint. The user explicitly requested pushing the unfinished work to main for another Codex session. No version bump or new APK publication is part of this handoff.
- Source boundary guard (108 files) and whitespace check passed.
- No full-suite audit or device/UI validation has been performed for this change.

### Remaining work, in order

0. **Fix actual Plan-mode date routing first.** `BubbleChatActivity.sendMessage` prefixes normal input with `/plan`. `ConversationPlanner.respond` computes `explicitGoal` from prefixes such as `the exam ...`, so `/plan the exam is on 27 July 2029` is classified as a fresh goal and replaces the active trip with a study workspace before the trip interpreter gets the message. This explains the two final failures. Resolve new-goal versus current-plan edit precedence using active context; preserve an explicit way to start a genuinely new plan. Do not remove the test helper's `/plan` prefix to make the tests pass. Rerun the eight-case suite after fixing this.

1. **Unify entry and old drafts.** Bare `/plan trip` and `/plan a trip` deliberately still use the old graph workspace for compatibility. Detailed/natural trip goals use the new checklist flow. This is an unresolved product inconsistency: provide a safe common entry or explicit advanced route, and migrate eligible old trip drafts without losing stored steps, dates, links, or IDs. Fix the reported behavior for existing drafts as well as fresh ones.
2. **Integrate existing advanced planning.** The new trip owner currently intercepts many turns before canonical graph/milestone handlers. Detailed trip goals used by existing scheduling/dependency tests and `/plan prepare for an exam trip` milestone flows can regress. Preserve those capabilities through explicit typed delegation/promotion. Do not merely weaken tests. The changed native checklist shape does legitimately require some assertions to inspect `Task.subtasks` instead of expecting separate tasks.
3. **Finish natural multi-fact turns.** Support compact destination/date/arrival answers (`Chennai, next Friday, I need to be there by 8am`), noun needs (`we need train tickets and somewhere to stay`), discussion-to-choice replies (`yes overnight, remove accommodation, add checking the admit card`), and event/travel date corrections in one message. Route purpose before arbitrary destination filling, but let typed dates win over purpose. Initial dates attached specifically to the exam must not be assumed to be departure dates.
4. **Tighten pending-question and review lifecycle.** Adding an item preserves a pending month, but item rename/removal currently clears pending state too broadly. Keep unrelated clarification intact; clear it only when answered/cancelled. Invalidate prior review authority for pending edits and cancellations. Bare destination handling must reject questions/control utterances, and adding an item must not erase an unanswered destination question. Summaries and stopping must always remain available.
5. **Harden reconciliation and projection.** `refresh saved tasks` should reconcile native checklist IDs/titles/completion with trip items, rather than retaining stale in-memory checklist data. Validate consistency between trip items, the root's checklist, and dated child steps. Check removal/re-addition of a previously saved dated item, outside native edits, archive/restore and saved undo; retain snapshot/receipt safeguards. Warn about preparation dated after departure and relative preparation dates moved into the past.
6. **Broaden wording without changing meaning.** Improve transport references (`flying` versus `Book flights`), object-only additions, replacement and quantity edits, and before/during/after phrasing for arbitrary destinations. The current code still has generic fallback replies and should not be claimed to understand unrestricted conversation. Keep suggestions distinct from accepted items; do not introduce repeated mandatory follow-up questions.
7. **Run only necessary compatibility checks.** Start with the eight new journey checks, then the affected existing cases below. Fix behavior where it regressed; update expectations only for the intentional native checklist change. CI may surface those older expectations on this WIP push. Do not disable tests or perform broad audits by default.
8. **Verify the actual app and finish delivery.** Build a debug APK, replay a Goa and an exam conversation in the chat UI (including Plan prefixing, finish, pause, and task/checklist rendering), and update this handoff with results. Do not claim the UI/keyboard/popup has been revalidated; no UI code was changed here. Only then mark this repair complete and prepare any separately requested version/release.

Affected existing cases to examine first:

- `ConversationQualitySafetyTest.reportedJourneyReplacesIrrelevantGoalAndEditsDraftWithoutNativeWrites`
- `ConversationQualitySafetyTest.milestonesKeepTravelAndStudyFactsSeparateAndRetainReviewedNativeSave`
- `ConversationQualitySafetyTest.planComparisonRetainsFactsAndBlocksInvalidChoicesWithoutTaskWrites`
- `ConversationalPlannerTest.planningIsConversationOnlyAndNeverWritesBeforeReview`
- `ConversationalPlannerTest.planningTogetherAsksForTheGoalWithoutLosingTheNextRepliesFacts`
- `AssistantGuidedPlanningTest.collaborativeTripRequestOverridesVacationHelpAndSavesOnlyAfterReview`
- `AssistantGuidedPlanningTest.collaborativePlanningParaphrasesKeepTheirOwnerAndNeverCreateTasks`
- Relevant canonical receipt/hash, persistence, saved undo and archive checks if those paths change.

### Reproduction command

Use the repository's Android setup and JDK 21. In this environment, source `/workspace/.setup/java-env.sh`; other environments may need their own SDK/JDK paths. The checked-in init script works around the already-observed cloud Robolectric JDK C2 crash without skipping assertions:

```sh
./gradlew :app:testDebugUnitTest \
  --tests 'com.focusbyrj.app.util.TripChecklistConversationTest' \
  -I tools/gradle/cloud-test-jvm.gradle \
  -Pkotlin.compiler.execution.strategy=in-process \
  '-Dorg.gradle.jvmargs=-Xmx8192m -XX:MaxMetaspaceSize=1024m -XX:ReservedCodeCacheSize=512m -XX:+UseParallelGC -XX:TieredStopAtLevel=1 -XX:ActiveProcessorCount=2' \
  --no-daemon --max-workers=2
python tools/check_chat_boundary.py
git diff --check
```

Do not edit Kotlin sources during a Gradle verification run. Local logs under `/workspace/.setup` and draft helper scripts under `/tmp` are not repository artifacts and are not needed to resume. Use the committed source and this handoff as authoritative. The previous environment's GitHub API access does not guarantee API access in a new environment; normal Git fetch/push was verified here.

### Suggested next-session prompt

> Read AGENTS.md and docs/assistant/implementation/TRIP_CHECKLIST_CONVERSATION.md. Continue the WIP trip-planning repair from main. Complete the numbered remaining work, preserve existing planning capabilities and the current UI, keep everything offline with no model, and run only the necessary conversation/save/compatibility checks. Do not restart with another broad plan or call it complete until the end-to-end experience and native task contents are verified.
