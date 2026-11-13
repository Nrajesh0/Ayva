# Chat experience implementation records

These delivery records preserve general chat, focused experience and guided
planning behavior and evidence. Their individual verification limits remain
explicit; later [status](../STATUS.md) and
[conversation-quality records](CONVERSATION_QUALITY_CURRENT.md) provide context.

## Contents

- [General conversation implementation](#general-chat)
- [Focused offline chat implementation](#focused-chat)
- [Guided planning: research findings and implementation specification](#guided-planning)

<a id="general-chat"></a>

<a id="general-chat-general-conversation-implementation"></a>

## General conversation implementation

<a id="general-chat-implemented-code-in-this-change"></a>

### Implemented code in this change

GeneralConversation now delegates supported conversation to GeneralDialogue, a capability-free Kotlin engine. GeneralDialoguePacks contains original English content for unwinding, feelings, study stress, work frustrations, reading, music and cooking. Existing authored knowledge explanations also use the new follow-up context. No models, network calls or database migration were added. A restricted ChatScript worker now supplies additional general-language meanings when Kotlin cannot handle a turn.

The engine receives only text, a copied bounded JSON frame and style preferences. It returns a reply and updated frame. It cannot obtain a repository, create a task, change a habit or mutate a plan. The existing GeneralConversation adapter owns persistence and the established archive/handoff representation. The source boundary checker now prevents app/session/action capabilities from being imported into either new engine file.

<a id="general-chat-behavior"></a>

#### Behavior

- Supported preferences are stored by dimension, with original wording and tentative status. A setting correction does not erase unrelated company/atmosphere preferences.
- Tentative alternatives stay separate from firm preferences. Both-settings alternatives do not select one automatically.
- Corrections with no identifiable target ask one specific question instead of deleting the most recent note.
- Suggestions respect firm setting/energy constraints. Rejecting a named suggestion, or requesting another, excludes it from subsequent alternatives in the current frame.
- Why/example/shorter/more follow-ups refer to the last supported explanation, reflection or suggestion.
- Topic-specific reflections avoid recently used wording and respect listening/question/tone preferences.
- Summaries show current structured details and bounded notes. Current general-chat recall can last seven days without extending task references or confirmation authority. Explicit forgetting clears the frame through the existing controls.
- Unsupported questions inside a supported frame produce an honest conversational response instead of falling through to task creation.
- Questions, quoted examples and explicitly third-party statements do not overwrite user preference fields. Common negative setting phrases are recognized.
- A tentative preference can be confirmed with “use that preference” or dropped with “forget that possibility”; ambiguous candidates request clarification.
- Replies without an explainable answer invalidate stale follow-up references. Correcting a multi-fact sentence preserves the unaffected facts in the legacy notes/handoff view.

<a id="general-chat-preserved-boundaries"></a>

### Preserved boundaries

Plan implementation, shared date parsing, shared clause parsing, native task/habit executors, planner UI and Chat's existing dated-task priority remain unchanged. The new adapter declines Plan mode and pending native questions. Dictionary lookup remains with the existing dictionary owner; it is not used for unrestricted synonym substitution. Existing explicit handoffs retain their interface and validity checks.

<a id="general-chat-validation"></a>

### Validation

The preceding integration check passed 103 tests with zero failures/errors/skips, selected from:

- GeneralDialogueIntegrationTest (eight complete context/isolation scenarios).
- NativeGeneralDialogueTest (two protocol and input-boundary checks).
- TripChecklistConversationTest.
- ConversationQualitySafetyTest.
- AssistantConversationLanguageTest.

These include real coordinator calls and Room assertions for zero unintended task creation, unchanged plan state while talking, preserved native pending input, expired action authority, correction/negation, tentative alternatives, response continuity and forgetting. The chat dependency guard and git whitespace check also passed. An installed-APK API 29 x86_64 instrumentation check exercised native meaning lookup, unknown/negated input and scratch cleanup successfully. No full-suite audit or manual Android UI audit was conducted.

<a id="general-chat-native-engine-decision"></a>

### Native engine decision

ChatScript at `9f5eec4736ba22bd992a6498c1e0052e2a795125` is now packaged as a restricted ARM64/x86_64 subprocess, with original English meaning rules. Kotlin retains the only conversation state and all responses. The adapter accepts only whitelisted meaning tokens and has no session, database or action APIs. It skips main-thread calls, 32-bit processes and Android versions below API 26; those retain Kotlin behavior.

The worker executes from the installed native library directory, starts fresh each turn, disables user logs/cache, and forbids regular-file writes before reading user text. It receives bounded text over stdin. A native CPU limit, Java timeout, failure cooldown and process termination protect the caller. This is crash containment, not a separate-UID security sandbox. Engine allocations are configured but there is no hard RSS ceiling.

Public assets are hash checked and extracted atomically into private no-backup storage with path/size limits. Scratch is removed after each invocation and on recovery from process death. No native conversation state survives; existing encrypted Kotlin sessions and forgetting controls remain authoritative. The package includes provenance, hashes, licenses and rebuild scripts. Native libraries use 16-KiB ELF alignment. See `tools/experiments/chatscript/README.md` for rebuild and device-check details.

KStateMachine, better-parse, new date recognizers, SymSpell, ConceptNet and scheduling solvers remain deferred per the revised recommendation. The current authored pack is small enough to use direct concept lookup; Room FTS is unnecessary at this size. ChatterBot sample content was reviewed as editorial research, not imported as a response database.

<a id="general-chat-scope-completion"></a>

### Scope completion

The selected implementation scope is complete: the general-only adapter, structured
conversation memory, authored content packs and follow-ups, original native meaning
rules, Android packaging, process lifetime/timeout fallback, private storage cleanup,
and rebuild/provenance tooling. Existing dictionary lookup remains read-only under
its existing owner. Plan behavior and shared operational parsers remain preserved.

The coordinator may consult general conversation twice for one message. Native
classifications, including abstentions, are now reused for ten seconds in a bounded
four-entry process-local cache. Keys are SHA-256 digests; no input wording or
conversation frames are retained. Responses are always generated using the current
Kotlin frame. Nonzero native exits now enter the same one-minute cooldown as timeouts
and other failures, preventing repeated crash/startup loops. Forgetting conversation
memory still uses the existing encrypted-session controls; classification caching
cannot restore facts or replies from a prior frame.

Libraries explicitly deferred in the reviewed recommendation remain out of scope,
rather than incomplete integrations. Physical ARM64 and Android 16 validation needs
an appropriate device; the cloud emulator evidence above covers API 29 x86_64.

<a id="general-chat-limits-and-next-steps"></a>

### Limits and next steps

This improves covered conversation; it does not add open-ended language-model understanding. Arbitrary subjects, complex negation, mixed languages and ambiguous references remain limited. New topics require authored replies and continuation/correction rules. Existing legacy general-chat paths still handle topics outside these packs, and may retain their older limitations. Normal Chat retains date-to-task behavior; Talk is appropriate for dated discussion.

Further topic coverage should include complete conversation examples. Physical ARM64 latency/memory and Android 16/16-KiB runtime checks remain release-device validation work. The assets add approximately 16 MB compressed plus roughly 2 MB per ABI before APK compression. Kotlin remains the first path to avoid native startup on recognized turns. No native task permissions belong behind this language boundary.

The final runtime completion change adds one focused cache/cooldown recovery case. NativeGeneralDialogueTest and GeneralDialogueIntegrationTest passed all 11 cases with no failures, errors or skips. The debug APK build, source-boundary guard and whitespace check also passed. No broad audit was run.

---

<a id="focused-chat"></a>

<a id="focused-chat-focused-offline-chat-implementation"></a>

## Focused offline chat implementation

28 October 2026. Prepared for version 2.0.17 (117), following the requested version bump and GitHub push.

<a id="focused-chat-implemented"></a>

### Implemented

- Compact Explore Ayva discovery in the existing function row; grouped examples and concise capability promises. Selection preserves the composer draft. Plan mode keeps its existing UI.
- A single active experience within the existing encrypted session, seven-day resumable frames and at most three suspended focused activities. Operational commands retain their existing execution authority. Social progress is captured before the earlier dated-task route so riddles survive task interruptions.
- Dictionary-backed word chain with scoring, repeat checks, hints, skip, configurable difficulty, short/long rounds and exhaustion. Unknown words are described as missing from the game dictionary, not nonexistent.
- Twenty authored anagram puzzles with exact letter multiplicity, alternate dictionary answers, progressive hints, reveal, skip, completion, pause/resume and bounded seen history. Four longer puzzles give harder mode a meaningful distinction; consecutive unassisted solves offer an optional difficulty increase.
- Sixty jokes, forty riddles and eighty original Run encouragement lines. Jokes have explanations and category selection; riddles have aliases, hints and explanations. No invented quotation attribution. Collections end explicitly rather than silently cycling.
- Thirty authored fact cards across space, nature and language, each with explanation, example and source reference. Unknown follow-ups receive an honest boundary.
- Three short branching stories with multiple endings, supported choices, pause/resume and restart. Story choices do not create tasks.
- More specific short-answer handling for study difficulties, work interruptions and unwinding. Existing hobby continuations remain in their established owner. Exhausted discussions offer recap or an exit instead of padding with generic questions.
- Honest unsupported-topic replies and capability help. No new trained models, runtime dependencies, databases or remote calls.

<a id="focused-chat-ownership-inventory"></a>

### Ownership inventory

| Area | Owner and persisted state | Boundary |
|---|---|---|
| New games, fact trails, encouragement, stories | FocusedChat; featureState.focusedChat/history | Speech and navigation only |
| Jokes, riddles, hobby openings | AssistantSocial; social/pausedSocial | Existing signed choices retained |
| Guided reflection | GeneralDialogue / GeneralConversation; dialogueV2 | User-supplied details and authored suggestions |
| Broader small talk | Existing EverydayTopics and routing | Finite supported topics |
| Dictionary and learning | Existing dictionary and AssistantLearningLanguage | Existing learning records/actions unchanged |
| Tasks, habits and planning | Existing authoritative handlers | New content cannot execute domain mutations |

<a id="focused-chat-deliberate-bounds"></a>

### Deliberate bounds

- Difficulty is user-controlled. Three anagrams solved without hints trigger an optional harder-puzzle suggestion; accepting selects words of at least seven letters. No hidden automatic change or inferred permanent trait.
- English alphabetic dictionary entries are accepted in word games; the curated bot vocabulary is smaller than the full dictionary. Dictionary coverage is not linguistic authority.
- Story wording choices and ordinal aliases are supported; arbitrary user-written story actions are not generated.
- Fact references are bundled offline. This environment denied all attempted public source-site fetches with HTTP 403. Editorial dates do not mean the source pages were fetched successfully. External factual review is still required before treating the collection as source-verified.
- A native/Kotlin comparison has now run on the API 29 x86_64 software emulator. See FOCUSED_CHAT_EMULATOR_COMPARISON.json. The one extra recognized sample is now handled directly in Kotlin. Retain the native fallback for unmatched wording for now: eight synthetic phrases and software-emulator timings do not establish that its wider coverage can be removed safely. No native process is needed for the focused activities. Physical-device and child-process peak-memory measurements remain open.
- No ARM64/Android 16 physical device is attached. Emulator evidence is recorded separately below.
- Human editorial review of humor, ambiguity and discussion quality remains valuable; content counts are not a quality score.

<a id="focused-chat-verification"></a>

### Verification

- 86 selected unit/integration checks passed after the operational-interruption fix, including existing planner regressions. The final social-control rerun also passed. The final narrow follow-up passed all 14 selected checks after the harder-puzzle change; the debug APK rebuilt successfully.
- Debug app and instrumentation APKs assembled successfully.
- API 29 emulator: FocusedDiscoveryDeviceTest passed (one test), covering discovery selection/dismissal and preservation of an unfinished composer draft.
- Source dependency guard passed across 116 files; git diff whitespace checks passed.
- Broad audits and physical-device checks remain deferred. Native comparison and API 29 keyboard/Back/1.6× text-size checks are recorded below; they do not establish Android 16 behavior.

<a id="focused-chat-try-it"></a>

### Try it

- Explore Ayva → Word chain → hint → a matching word → meaning → pause → resume word chain → stop.
- “Play anagrams” → hint → planet → next → pause → “buy apples tomorrow” → resume anagrams. Only the explicit dated request creates a task.
- “Give me a riddle” → hint → piano → next → skip → reveal → stop. Stopping discards the resumable riddle.
- “Tell me a joke” → explain → less silly → another → stop.
- “Explore facts” → why → example → source → related fact. Unsupported questions do not invent an explanation.
- “Play a tiny story” → first → second → next → pause story → resume story.
- “I am stressed about studying” → remembering it → recap. The short reply updates the study difficulty instead of opening an unrelated topic.

<a id="focused-chat-integration-notes"></a>

### Integration notes

The reusable assets are the existing dictionary and learning infrastructure. This change adds authored Kotlin content and a small session adapter, not imported chatbots or new framework dependencies. Fact references are visible only on request. Task planning files, date parsing, habit execution and app-blocking overlays are unchanged.

The last implementation review found and repaired three specific issues: a dated-task early return bypassing riddle snapshots, length changes accidentally resetting difficulty, and typed social controls diverging from buttons. These are covered in the focused regression sessions.

<a id="focused-chat-remaining-implementation-completed-in-the-follow-up"></a>

### Remaining implementation completed in the follow-up

- Activity choices use the existing internal-command route, with session ID, revision, expiry, current owner and an allowlist of the displayed choices. Old, replayed and forged choices cannot advance a round, create a dated task, or force a chat-mode change.
- Focused activity frames now include phase, expected answer type, focus item and a bounded owner/phase diagnostic containing no user wording. Dictionary work in the focused adapter runs on a background dispatcher.
- General discussions pause/resume alongside games and hobbies. Saved discussion snapshots contain recall data, not operational references. Stop removes resumable discussion state.
- Word-chain bot candidates are indexed by starting letter. Rules explain dictionary coverage, alphabetic entries, inflections, regional forms, proper-name labels and unscored skipping. Score/progress can be requested explicitly.
- Joke metadata includes stable content-derived IDs, repetition group, tone, general audience and provenance. “Not my kind” stores a bounded dislike list that remains excluded when the collection is replayed.
- Fact metadata includes editorial date, supported follow-ups, explicit related-card IDs and an offline qualification. Related navigation cannot drift into another category.
- Reading/music/cooking discussion facets distinguish tentative interest and rejection. Recaps preserve those distinctions across interruption/resume; repeated explanations end with a concise choice to recap, switch aspect or stop.
- Unknown-topic notices shorten after the first refusal instead of replaying the full capability pitch.

<a id="focused-chat-comparative-evidence-and-editorial-review"></a>

### Comparative evidence and editorial review

The native comparison used the same eight synthetic inputs and unwinding frame. Before the added Kotlin wording, seven inputs had no useful extra native interpretation; one relaxation recommendation did. Native process calls took approximately 3.25–5.98 seconds on the software emulator, versus 0.14–0.53 seconds for direct Kotlin. These are diagnostic observations under emulation, not phone latency benchmarks. App PSS increased from 228,159 to 233,159 KiB during the sample; this does not measure child-process peak memory or attribute the delta solely to native code.

A local editorial pass checked the bounded content contracts: original lines have no invented attribution; riddle misses acknowledge stored-answer limits; facts separate Earth-day durations and solar/rotational days; hobby explanations avoid specific book/song/recipe knowledge. Source pages remain externally unverified because their hosts are blocked. Local editorial review is not a claim of independent source verification or a measured product rating.

Only external source verification and physical-device/child-process performance evidence remain unavailable here. Broad audits remain deferred as requested. Final follow-up evidence is recorded below.

The final recall cleanup also removes suspended activity snapshots and session-only content preferences when the user forgets the conversation. Explicitly saved memories/plans remain under their existing deletion controls. This prevents a later resume command from reconstructing forgotten chat state.

Follow-up verification: the full selected set passed 90 checks after the typed-choice routing, normalization and hobby-preference changes. Both native instrumentation checks passed. The final recall-cleanup run passed all 18 selected checks and rebuilt the debug APK. Both discovery instrumentation checks passed on API 29, including the keyboard, Back and 1.6× text-size scenario. No physical-device result is implied.

The keyboard probe initially encountered a frozen Android System UI and its system ANR dialog. After closing/recovering that emulator system process, both UI tests passed (49.409 seconds total). This was an emulator failure, not evidence of a composer defect. The test also explicitly prepares keyboard/focus state and records a screenshot if the IME fails to appear again.

Final source dependency guard passed across 116 files; whitespace checks passed. Version 2.0.17 (117) includes this implementation; GitHub Actions determines release publication. The remaining release-validation items are source-site review, physical ARM64/Android 16 checks, and physical-device/child-process peak-memory measurements. All planned feature-code changes are implemented; native removal remains an explicit evidence-based follow-up, not an implicit dependency of these features.

<a id="focused-chat-feedback-refinement--28-october-2026"></a>

### Feedback refinement — 28 October 2026

Removed Talk a little from discovery; retained underlying context handlers used by existing features. Games now use separated titles, puzzle lettering, score text and compact instructions. Riddles separate the question from controls. Stories display numbered choices and accept unambiguous wording containing the meaningful choice words instead of repeating the whole scene on a miss.

Nonpersonal content IDs now persist in `ayva_content_rotation` independently of chat history. Clearing a chat still clears its conversation and activity state, but no longer resets anagrams, jokes, riddles, facts or encouragement to their first item. Story openings and word-chain starters rotate too. Explicit replay controls remain available when a finite collection ends.

Added original interactive retellings of The Tortoise and the Hare, The Lion and the Mouse, and The Crow and the Pitcher, bringing the story collection to six. Alternate endings are labelled. No modern adaptation text or illustrations were imported.

Version 2.0.18 (118) packages this update. Debug build and 19 focused checks passed, including fresh-session rotation, natural story choices, existing context handling and activity authority. Dependency and whitespace checks passed. Visual formatting still needs real-device taste review; these changes are local and have not been version-bumped or pushed.

---

<a id="guided-planning"></a>

<a id="guided-planning-guided-planning-research-findings-and-implementation-specification"></a>

## Guided planning: research findings and implementation specification

Original implementation specification, inspected 2026-10-18. A bounded draft/question/review/save flow is now implemented; see [OFFLINE_ASSISTANT_IMPLEMENTATION.md](../history/FOUNDATION_DELIVERY.md#b3-b11-continuation) and [STATUS.md](../STATUS.md). P6 physical acceptance and the advanced constraints explicitly listed as unsupported remain open. The findings table below describes the planning baseline. Required batch B5P in [MASTER_PLAN.md](../history/PLANNING_BASELINE.md#sequential-batch-plan), after B5 and before B6. This document defines a bounded offline planning assistant, not unrestricted natural-language reasoning. The quality goal is correct, understandable and recoverable behavior; no claim of perfect understanding.

<a id="guided-planning-findings-from-the-current-code"></a>

### Findings from the current code

| Existing component | Observed behavior | Design consequence |
| --- | --- | --- |
| `util/assistant/AssistantPlanning.kt` | Orders saved tasks, filters/skips items and allocates work blocks; asks for a 1–480 minute budget. Its context holds time budget, order, limit and excluded IDs, with a 15-minute lifetime. | Reuse as the quick day-plan path. Add a separate richer draft contract; do not mislabel allocated time as a completion estimate. |
| `data/Task.kt` | `subtasksJson` stores `Subtask(id, title, isDone)`. Subtasks have no independent due date, reminder or nested children. | Existing checklist subtasks can be reused. Independently scheduled work must be top-level tasks unless a later schema extension is explicitly designed. |
| `util/assistant/AssistantAction.kt` | Task serialization preserves subtasks; edit fields currently do not include a dedicated subtask operation. | Add typed, validated subtask operations with stable IDs and parent snapshots rather than modifying arbitrary JSON through chat. |
| `OfflineAssistant.kt` | Expiring choices/confirmations, typed actions, receipt handling and compound actions already exist. | Reuse routing/guards but audit batch transaction and crash recovery before saving plans; do not infer exactly-once guarantees from session receipts. |
| `AssistantSession.kt` and domain contexts | Encrypted ephemeral state and interrupted questions exist. | Extend with versioned planning frames; ordinary restoration must not revive save authority. |
| `ui/screens/chat/AyvaResponseControls.kt` | Compose controls send canonical queries; custom settings use `AlertDialog`; task list selection is read-only until an action is chosen. | Add planning question/review components using the same validated dispatcher. Preserve current task receipts and overall chat layout. |

Correction to the earlier exploratory discussion: the app already has checklist subtasks. A new parent–child task database is not required for simple checklists. More advanced nested/due-dated subtasks are a distinct potential future feature, not part of the initial promise.

The current planner also detects emotional words such as “stressed” and can suggest work. Audit that routing during B3/B5P: emotional disclosure must not automatically enter planning or force a productivity response. Explicitly asking for help planning is different from saying “I'm exhausted.”

<a id="guided-planning-research-scope-and-external-guidance"></a>

### Research scope and external guidance

This specification is grounded in repository inspection, existing state/confirmation patterns and the following design principles: explicit mode boundaries, progressive disclosure, targeted clarification, editable previews, conservative execution and recovery after interruption.

External reference requests were attempted but blocked by the environment's network proxy (HTTP 403), so their current contents were not reviewed. Revisit these primary references during P1 when access is available; do not describe this document as an externally validated literature review:
- [Google conversation design: confirmations](https://developers.google.com/assistant/conversation-design/confirmations)
- [Android Compose dialogs](https://developer.android.com/develop/ui/compose/components/dialog)
- [Android UI state preservation](https://developer.android.com/topic/libraries/architecture/saving-states)

No model or online service is proposed. Future implementation must verify Compose lifecycle/accessibility details against the project's installed library versions and test actual behavior.

<a id="guided-planning-interaction-contract-offer-clarify-draft-review-save"></a>

### Interaction contract: offer, clarify, draft, review, save

<a id="guided-planning-entering-planning"></a>

#### Entering planning

An explicit “help me plan my day/exams/trip” starts a planning draft without a redundant permission popup. An inferred topic such as “I have exams next month and I'm behind” may offer a compact, dismissible planning prompt: “Would a study plan help?” with **Plan together**, **Just chat**, and dismiss. An ambiguous standalone phrase gets a clarification rather than creating tasks. Do not repeatedly offer planning after dismissal or trigger it from a single keyword.

Planning is a scoped conversation state, visible through a small “Planning · Exam preparation” status and an accessible review/exit action. It is not a permanent global mode or a replacement for `/talk`. Explicit planning can start during `/talk` with a visible scope transition; afterwards restore the prior app-help mode. A casual aside must never toggle modes invisibly.

<a id="guided-planning-questions-and-popups"></a>

#### Questions and popups

Use a compact themed bottom sheet for a structured planning question and a small dialog for a single consequential choice. Keep ordinary low-impact follow-ups inline. These are new planning components only, not a chat redesign. Do not stack modal windows or reopen a dismissed one on recomposition.

Each question has:
- One specific question, the current relevant value and a short reason if useful.
- Two or three sensible options when applicable, plus a free-text/custom answer.
- “Not sure” or “Skip for now” for optional information; optional fields cannot block a useful unscheduled checklist.
- “Back/change an earlier answer” and “Cancel planning.” Closing a question never saves anything.
- An inline transcript equivalent so the user can answer by typing, revisit the reasoning and use accessibility tools.

Ask only what is needed for the requested output, one question at a time by default. Dates and available time are required for a timed schedule, not for brainstorming a checklist. Collect answers already supplied in a sentence and do not ask them again. When parsing fails, explain the field's expected format and retain other answers. Offer a manual picker/field after repeated failures rather than trapping the user in a language loop.

Use theme semantic colors, readable contrast, adequate touch targets, keyboard/inset handling, large text and TalkBack labels. Android Back dismisses the current surface without discarding the draft; cancelling explicitly discards it. Retain entered answers through rotation using the state owner, not only composable-local state. No question UI can call a DAO directly.

<a id="guided-planning-drafting-and-corrections"></a>

#### Drafting and corrections

Show the understood goal, constraints, proposed tasks/checklists and unresolved assumptions before saving. Each suggested step is marked as user-requested, existing-task-based or an optional template suggestion. Let users remove/reorder entries or change dates/effort. Keep provenance internal unless it helps explain a proposal.

“Actually Friday, not Thursday” changes the referenced field and rechecks dependent dates. If there are two plausible Friday fields, ask which. “Only 30 minutes”, “skip packing”, “make that a checklist instead”, “keep the booking task” and “don't change the existing one” produce targeted draft edits. Explain material changes concisely; do not regenerate the entire plan and silently discard accepted edits.

A plan can remain unscheduled when details are unknown. Do not invent flight dates, exam subjects, chapter lists, bookings, travel durations or free calendar slots. An impossible schedule produces a conflict question with options to reduce scope, change availability/deadline or keep an unscheduled backlog; never claim everything fits.

<a id="guided-planning-review-and-save"></a>

#### Review and save

Provide a distinct review sheet with a summary such as “Create 4 tasks with 6 checklist items; update 1 existing task.” Show the actual titles, dates, timezone where relevant, selected entries, changes and conflicts. The primary button describes the action, e.g. **Add 4 tasks**; **Keep editing** is always available. Reviewing, selecting or dismissing has no task side effects.

A save token binds the draft ID/revision, selected draft item IDs, expected current task versions and concrete action payload. Any correction invalidates the old token. Saving revalidates all targets and constraints. Changed external tasks trigger a refreshed comparison rather than an overwrite. “Yes” answers only the active scoped question/offer; it cannot confirm a suspended save after an unrelated joke or dictionary question.

Task-only plan writes should commit atomically in a Room transaction, with a persisted idempotency receipt in the same transaction. Schedule reminders/update widgets after commit through recoverable, idempotent domain effects. Test crash windows explicitly; if a side effect fails, report that status rather than claiming notifications are set. Audit existing executor semantics before choosing a minimal compatible implementation. Do not use note-containing backup paths: B1 is a prerequisite.

Guarded plan undo reverses only this plan's unchanged writes. Define and test recurrence/reward behavior and recovery-snapshot requirements. Never roll back a later manual edit silently. A saved plan receipt maps draft items to actual task/subtask IDs; follow-up “move the second one” uses that mapping, not a newly sorted database list.

<a id="guided-planning-structured-state-and-domain-rules"></a>

### Structured state and domain rules

Introduce a versioned `PlanningDraft` (names illustrative) containing:
- ID/revision, domain (day/exam/trip), lifecycle stage and timestamps/expiry.
- Goal and supplied constraints, with explicit vs assumed status.
- Required/optional missing fields and the exact active question ID.
- Stable draft item IDs, selected entries, references/versions for existing tasks, dependencies and optional proposed timeboxes.
- Concrete dates with timezone, user-supplied duration estimates distinct from suggested work blocks.
- Validation conflicts, suspended topic and read-only saved receipt mapping.

Bound draft size, nesting, parser work and suspended contexts. Store only allowed planning data in encrypted assistant state; no Notes access, automatic clipboard reading or external calendar access. Use user-entered fixed commitments and supported task data. Keep existing short expiry initially; if longer draft retention is wanted, implement an explicit save-draft capability with inspection/deletion and its own policy. Do not silently persist travel details or emotional disclosures as personality memory.

State transitions: `offered → collecting → drafting → reviewing → saving → saved`; `paused`, `cancelled`, `expired` and `needs-repair` are explicit alternatives. Only a validated current review can transition to saving. Process recreation may restore a valid draft, but requires renewed review before saving; it must not replay old buttons. Every popup answer carries question ID and draft revision, rejecting stale callbacks.

<a id="guided-planning-day-planning"></a>

#### Day planning

Use existing tasks, stated available windows, priorities and user-entered commitments. Use a deterministic ordering rule with stable ties. Allocate suggested work blocks without pretending to estimate completion. Distinguish due date from planned work time: the current task model has a due date but no separate scheduled work interval. Keep work blocks in the draft unless a user explicitly approves due-date changes; a future scheduling field needs its own migration/design.

<a id="guided-planning-exam-preparation"></a>

#### Exam preparation

Collect subjects/topics from the user and exam dates when scheduling. Ask for available study windows and optional difficulty/remaining effort. Reviewed templates may suggest learning, practice and revision blocks, but must not invent syllabus content or predict mastery. Respect dependencies and actual daily capacity, allow missed-session replanning, and leave unknown effort clearly unestimated.

<a id="guided-planning-trip-preparation"></a>

#### Trip preparation

Support preparation checklists and deadlines using supplied departure/return dates and selected travel type. Template suggestions such as packing, checking documents or arranging transport remain optional. Avoid inferring destination rules, visa eligibility, weather, live prices, bookings or routes. When current requirements matter, say they need independent checking; no online lookup or invented authoritative travel advice.

<a id="guided-planning-checklist-subtasks-versus-scheduled-tasks"></a>

#### Checklist subtasks versus scheduled tasks

Use existing `Subtask` for small checklist steps that share a parent and need no independent reminder. Use separate `Task` records for independently scheduled actions. Present that choice in plain language when ambiguous: “One checklist, or separate reminders?” New typed subtask edits must validate parent snapshot, stable child IDs, title/size limits and completion semantics. Audit native rendering, serialization, undo, backup/restore and recurrence cloning without changing protected card appearance. In particular, verify legacy subtask records missing IDs get stable identities before permitting conversational targeting; the current JSON reader can generate an ID while reading.

Do not assume completing a parent completes its children or vice versa. Preserve existing product behavior and expose any needed policy decision explicitly in the implementation handoff. No nested subtasks or independently timed children in the initial release.

<a id="guided-planning-sequential-implementation-slices-and-acceptance-gates"></a>

### Sequential implementation slices and acceptance gates

| Slice | Deliverable | Gate |
| --- | --- | --- |
| P1 | Capability inventory, concrete state schema, small interactive question/review prototype and external reference verification when available | User can enter, dismiss, correct and exit without writes; protected UI intact; architecture and unsupported cases documented |
| P2 | Guided day planning and popup/typed answers via one dispatcher | Missing/optional fields, impossible capacity, timezone/DST, stale questions, interruptions and process recreation tested |
| P3 | Task/checklist drafts, typed subtask edits, reviewed atomic save and recovery | Duplicate taps/retries/crash windows create no duplicate task batches; stale edits refused; existing cards, backups and guarded undo verified; zero Notes accesses |
| P4 | Exam preparation and replanning | No invented subjects/effort; capacity/deadline conflicts explained; correction preserves unaffected choices |
| P5 | Trip preparation and topic switching | No fabricated current travel facts; optional suggestions removable; casual conversation/dictionary interruption resumes correct draft without stale save authority |
| P6 | Independent scenario and Android device review | Full mixed-domain transcripts, accessibility/theme/keyboard checks and all action/privacy invariants pass; residual unsupported wording documented |

P1–P6 belong to B5P. Implement sequentially in separate sessions as necessary. B9 later supplies richer wit/empathy; P1–P6 must already provide respectful neutral conversation and basic interruption safety, so there is no circular dependency on B9.

Minimum end-to-end scenarios include: a casual exam disclosure declined as planning; explicit day plan with no tasks; missing date retained as unscheduled draft; corrections after review; two overlapping plans; date-only versus time-only changes; duplicate task titles; a subtask changed in native UI during review; save retry after process death; storage failure; timezone change; too much work before a deadline; unknown trip requirements; negated/quoted task commands; “yes” after a different question; and Notes-related requests inside a plan.

Measure scenario completion, unintended writes, wrong-target changes, unnecessary questions, abandoned loops, repetition, latency and user-rated clarity. Require zero observed wrong writes/Notes access in the acceptance suite and investigate every such defect. Record sample sizes and failures; passing scenarios cannot establish perfection for arbitrary language. No arbitrary target number of tests or templates substitutes for behavioral coverage.
