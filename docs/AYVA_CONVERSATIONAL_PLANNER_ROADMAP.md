# Ayva: conversational planning without models

> Historical roadmap — bounded core implementation complete. The open expansion frontier now belongs to [the new conversation-quality plan](assistant/CONVERSATION_QUALITY_PLAN.md). See [delivery summary](assistant/DELIVERY_SUMMARY.md) for completed scope and remaining acceptance work. The original design and checkpoint descriptions below are retained as reference.

Status: living product and engineering roadmap. The bounded core delivery now includes canonical multi-session tasks, reminders, native editing, conversational planning and discussion, templates/memory, constraint scheduling, undo/redo and recovery. Necessary checks and debug packaging are recorded per source checkpoint. Independent audits, device/release acceptance and the research frontier remain open at the user’s request. Earlier optimized-release evidence applies only to its recorded checkpoint. See [the implementation ledger](assistant/implementation/CURRENT.md) for actual code and validation. The findings in section 2 describe the pre-implementation baseline. The baseline below is a starting design, not a ceiling on capability. Sections 14–20 define how implementation must discover and incorporate better approaches. Use [the implementation playbook](AYVA_IMPLEMENTATION_PLAYBOOK.md) to carry this work across sessions without losing requirements or overstating progress.

## 1. Product objective

Build an offline assistant that can hold a useful planning conversation: discover a goal, retain constraints, propose meaningful steps, negotiate alternatives, schedule work, revise the plan, and apply agreed changes to the app. All interaction must be possible in the main chat composer. Forms, buttons, and detail views are optional conveniences, never required answers to an assistant question.

Use no language model, embedding model, remote inference, or network-dependent planning service. Use a deterministic language pipeline, typed dialogue state, explicit knowledge, authored planning methods, constraint solving, and compositional responses. This can produce unusually capable conversation within supported domains. It cannot honestly promise unrestricted human-level conversation or general world knowledge. Unknown meanings must lead to a useful, specific repair question rather than an invented answer or accidental task.

The quality target is end-to-end conversations, not the number of phrases or commands recognized. A plan can take many sessions and survive app restarts. Users should not need to learn field names, repeat facts, or re-enter a goal in a second box.

## 2. Findings from the current implementation

| Area | Evidence | Architectural consequence |
| --- | --- | --- |
| Planning entry | `AssistantGuidedPlanning` recognizes bounded day/exam/trip requests and asks for comma-separated steps, deadline, then budget. | It collects a checklist more readily than it collaborates on a goal. |
| Separate answer box | `AyvaPlanningControls` opens a `ModalBottomSheet` with an `OutlinedTextField`; answers are keyed by plan ID and revision. | Keep revision checking, remove the second input as the default planning path. |
| Short-lived state | `AssistantSessionStore` expires after 15 minutes; references and planning drafts also have short authority lifetimes. | Separate durable conversation and draft memory from short-lived permission to mutate current records. |
| Flat plan structure | `PlanningDraft` has up to 20 items, three domains, a shared deadline and aggregate budget/effort. | Replace this representation for advanced planning; raising the item cap is insufficient. |
| Subtask limitations | `Subtask` contains ID, title, and completion only. Checklist subtasks cannot have independent deadlines in `PlanningDraft.tasks()`. | Real scheduled subtasks require data and app-wide behavior changes. |
| Schedule limitations | `Task` has a due date, but no work interval or duration. Existing planning allocates suggested focus blocks without reserving time. | A deadline must remain distinct from a scheduled work session. |
| Greeting relevance | Warm greetings can fall through `AssistantSocial` to the task-aware greeting in `AyvaTalkEngine`. `AyvaContextEngine` selects the next future-due item without a horizon/type filter there. | A distant occasion can appear as the next task; fix the relevance policy rather than just the greeting wording. |
| Routing | `AyvaIntentRouter` explicitly prioritizes task creation and uses ambiguity cards; other assistant handlers have ordered precedence. | Introduce explicit mode ownership and one arbitration policy before expanding language. |
| Existing strengths | `AssistantPlanStore` already provides transactional task creation, content-bound deduplication, stale checks, undo data, and retryable reminder/widget effects. | Preserve and extend these foundations. |
| Back behavior | Habit editing already has an IME-aware `BackHandler`, but the sheet has its own dismissal path and editor values use `remember`. | Reproduce actual window/IME dispatch and draft loss. No single cause is proven yet. |

The previous planning roadmap deliberately excluded duration estimates, dependencies, calendar availability, and database migrations. Those are now central scope, not minor follow-up extensions.

## 3. Experience contract

### Modes and the composer

- `/plan` selects a durable planning workspace. `/talk` selects conversation. `/drill` selects the existing drill workflow. Register other existing functions through the same command catalog.
- Typing `/` opens a compact, searchable command menu. Typing a standalone `plan`, `talk`, or `drill`, or a clear planning request, can show a quiet blue suggestion above the composer. A word mentioned inside ordinary prose must not steal the conversation.
- Tapping the suggestion activates a small mode label inside the composer, above the text. `/plan` submission also activates it directly. A clear submitted request such as “let's plan a trip” may enter Plan with a visible acknowledgement; ambiguous mentions only suggest it.
- Preserve the unsent text, cursor, selection, keyboard focus, and draft when selecting a mode. Remove only the recognized command prefix, never the user's goal.
- Plan stays active until explicitly exited or switched. Closing its label exits the mode and preserves the plan. Switching to Drill suspends the plan. Returning resumes its current state.
- Persist the mode with the conversation. On reopening, show which mode and plan are active. Allow several named plans with one active plan per conversation; explicit thread identity prevents cross-chat leakage.
- Plan edits affect the draft by default. An explicit command for another app function can run through the shared action gateway and return to the plan. If “move it” has two plausible owners, ask which one.

### Visual and interaction direction

Use the compact blue suggestion and selected-mode pattern familiar from Codex, implemented with native Compose components. Exact animation parity requires a reference capture and visual comparison; it is not an existing specification.

Proposed starting motion tokens: suggestion fade/slide in roughly 120–160 ms; mode-label transition roughly 160–200 ms; small height changes with a restrained spring. Validate on device, respect disabled/reduced animations, and never delay text entry or Send for animation. Keep layout stable when suggestions appear. A small keyboard-navigation menu, TalkBack labels, predictable focus order, dark-theme contrast, large fonts, and landscape behavior are required.

Replace large repeated action blocks with text-first responses, at most two useful compact choices, and an overflow menu. Keep a single prominent action only when the next step warrants it. Visual chips can be around 28–32 dp high, but retain non-overlapping 48 dp touch targets. Compact appearance must not shrink accessibility targets. Hide duplicate or irrelevant actions; do not attach a menu to every social reply.

### Conversation examples that define the feature

These are target behavior scripts, not claims that the current app supports them.

**Goal discovery**

User: “I want to go on a trip.”

Ayva: “Let's work it out. Do you have a destination in mind, or are we deciding that too?”

User: “Goa, three days next month, around ₹15,000. I can prepare only after work.”

Ayva retains destination, duration, approximate travel window, budget currency/value, and preparation availability in one turn. It asks about the departure city or exact dates only when those affect the next useful decision. It does not invent live prices, bookings, visa requirements, weather, or travel time.

User: “Dates aren't fixed. Let's sort what needs doing first.”

Ayva proposes an editable preparation outline with conditional suggestions, keeps dates unresolved, and continues usefully.

**Revision and conflict negotiation**

User: “Book the stay after we agree on dates. Check transport first, and give me half an hour for that.”

Ayva adds a dependency, ordering preference, and duration to their intended items. It clarifies whether “check transport” means a preparation task if necessary; it does not claim to perform a booking.

User: “Put that on Friday at seven.”

Ayva resolves “that” to the transport task using the active topic. If seven is ambiguous, it uses an explicitly established evening window or asks AM/PM. If a known fixed commitment overlaps, it explains the actual overlap and offers feasible alternatives.

User: “Keep the workout. Saturday morning instead, and move the packing to the day before we leave.”

Ayva preserves the workout, moves only the intended task, and stores packing as a relation to departure. Until departure is set, packing remains unscheduled with that dependency visible.

**Interruption and resumption**

User: “What's a buffer?” → Ayva explains in the context of this plan.

User: “Okay, add fifteen minutes between those.” → Ayva applies the buffer to the referenced sessions, or asks which if the set is ambiguous.

User: “Start a drill.” → The plan is suspended, not lost.

The user returns tomorrow: “Back to the trip.” → Ayva restores the goal and unresolved decisions, then rechecks current tasks before proposing writes.

**Social relevance**

User: “Hi.” → “Hi. What's on your mind?”

User: “I'm tired.” → A brief acknowledgement and, if useful, a choice between talking and simplifying the day. No automatic productivity lecture or task mutation.

User: “What needs attention today?” → Only now retrieve an appropriate agenda. Separate actionable work from occasions and show the relevant dates.

## 4. Foundation: stop losing input

This is the first engineering milestone, independent of the new planner.

Inventory every text-entry surface: task creation/editing, habit creation/editing, chat, notes, searches, dialogs, bottom sheets, and floating activities. Record who owns its window, focus, Back callback, dismissal, and draft.

For an ordinary editor, Back while the IME is visible should hide the keyboard and leave the editor and text intact. A later Back can dismiss the editor or navigate. A foreground popup or picker should follow an explicit overlay-specific policy. Test rapid repeated Back presses and predictive-back cancellation so one gesture never triggers both IME hiding and navigation.

Inspect IME insets inside each actual dialog/window, Material sheet Back handling, `onDismissRequest`, activity callbacks, and keyboard-animation races. Do not blanket-install competing BackHandlers. Use a shared policy with appropriate adapters for activities, Compose dialogs, sheets, and any view-based windows.

Use `SavedStateHandle`/saveable editor state for recreation and durable local drafts for meaningful unfinished forms and plans. Restore cursor and relevant selection. Auto-save a draft locally without committing a task or habit. Explicit Save commits; explicit Discard removes the draft. Accidental navigation, rotation, process death, and app switching must not erase work. Integrate draft deletion with the app's privacy and clear-data behavior.

Exit gate: device tests reproduce the original failure and pass on supported Android versions with gesture and three-button navigation, representative IMEs, and predictive Back where applicable. The core app-blocking `WindowManager.addView` mechanism remains intact.

## 5. Architecture: one typed conversation pipeline

```text
Composer + mode + conversation/thread identity
    -> normalization and clause segmentation
    -> candidate interpretations with evidence
    -> reference resolution and dialogue arbitration
    -> proposed plan operations / questions / conversation acts
    -> validation and constraint solving
    -> response composition and optional compact controls
    -> reviewed change set -> shared action gateway -> repositories
```

Keep the core engine pure Kotlin where possible. Supply clock, time zone, locale, current records, and preferences as explicit inputs. Android services, persistence, reminders, and UI rendering are adapters. Every response should carry a typed semantic payload, not use display text as executable state.

Suggested module boundaries: `language`, `dialogue`, `context`, `planning`, `scheduling`, `execution`, `presentation`, and `evaluation`. Avoid creating a second assistant behind `/plan`; modes share the parser, context services, and executor while owning separate active workflows.

### Language understanding without models

1. Preserve original text and spans. Normalize punctuation, apostrophes, common contractions, and bounded typo variants without rewriting names or task titles.
2. Recognize speech acts: request, fact, answer, correction, rejection, comparison, explanation request, hypothetical, acknowledgement, interruption, resume, and commit.
3. Use compositional grammars for dates, quantities, entities, constraints, references, and operations. Lexicons and domain synonyms feed these grammars; regex remains useful for local tokens, not as the whole conversation architecture.
4. Produce several candidate interpretations with evidence and unresolved fields. A deterministic score is a ranking signal, not an uncalibrated probability. Establish acceptance thresholds with held-out examples.
5. Handle negation and scope explicitly: “don't move Friday's meeting,” “not this Saturday, the next one,” “except the booking,” “only the first two.”
6. Handle multiple clauses: “Move packing to Thursday, leave tickets alone, and remind me at six.” Resolve what the reminder belongs to rather than attaching it arbitrarily.
7. Reuse task names, aliases, plan titles, and domain vocabulary for bounded fuzzy matching. Never fuzzy-match destructive actions or ambiguous targets into execution.
8. Never treat arbitrary chat as a task by default. Unsupported input gets a narrow repair: “Do you mean move the booking task, or change the trip dates?”

Support English well first, including common informal spelling and the user's locale conventions. Additional languages and mixed-language input require their own grammars, lexicons, examples, and evaluation; do not call them supported merely because a few keywords match.

### Dialogue manager

Use a hierarchy of dialogue frames instead of one global `expected` slot. Each frame records its goal, mode, active entities, answered fields, unresolved decisions, suspended parent, and evidence references. Store a typed question describing what is missing and why it matters. Accept an answer, an explanation request, a correction, a side question, or a topic switch while a question is pending.

Extract all supplied facts before deciding the next question. Prefer the question that unlocks the most useful progress with the least user effort. Ask one focused question normally; group tightly related missing facts only when that is clearer. “I don't know” must allow a useful partial plan, a proposed assumption, or an explicitly unscheduled task. Do not repeat answered questions.

Resolve pronouns, ordinals, and groups using stable entity IDs and the displayed revision: “it,” “the second one,” “both bookings,” “everything after that.” Track separate entities such as trip dates, preparation tasks, and work sessions. If two meanings are plausible, ask using their names. Treat tentative ideas, hypotheticals, and rejected facts differently from accepted constraints.

## 6. Context and memory

Separate four stores:

1. **Turn context:** current utterance, displayed items, and selection.
2. **Conversation frames:** topics, pending questions, recent entities, corrections, and resumable interruptions.
3. **Durable plan workspace:** goals, decisions, revisions, constraints, items, schedules, and unsolved questions.
4. **Explicit preferences:** availability patterns, planning style, usual durations, and permitted personalization. Users can inspect, correct, and forget these.

Do not solve continuity by extending all current 15-minute expiries. Drafts may be durable while mutation confirmations remain short-lived and bound to live record versions. A remembered plan is not authorization to execute an old change.

Store provenance for facts: user statement, current app record, explicit preference, or proposed assumption. Include scope, time of assertion, and whether the fact was superseded. “I finish work at six today” must not become a permanent work schedule. Corrections replace effective facts while retaining enough revision history for explanation and undo.

Build compact structured summaries from the state itself; no model is needed. Rehydrate the relevant frame and current records, not every transcript message. Mark stale references instead of silently reusing them. Define clear distinctions among clearing chat, deleting plan drafts, forgetting preferences, and deleting saved tasks, and explain their effects in the UI.

For relevant responses, prioritize: explicit current request, active frame, referenced plan/items, immediate time horizon, explicit preferences, then optional suggestions. Greetings do not require a database agenda scan. Agenda views exclude trashed/deleted items, separate occasions from work, and use explicit horizons. A months-away birthday is not an urgent pending task; an explicitly requested reminder to buy its gift can be actionable work.

## 7. Planning representation and domain capability

Introduce a versioned plan graph:

- Plan: stable ID, goal, status, domain, revision, time zone, assumptions, open questions.
- Node: goal, milestone, task, subtask, or decision, with stable ID and optional linked app record.
- Edges: parent/child, prerequisite, blocks/unblocks, optional alternative, or timing relation. Avoid cycles and limit nesting for usability.
- Estimates: duration or range, source, confidence category, splittable flag, minimum session size.
- Constraints: deadlines, earliest start, availability windows, fixed appointments, buffers, budgets, preferences, and protected commitments.
- Work sessions: start/end and time zone, independent from due dates; several sessions may advance one task.
- Change history: typed operations, prior revision, user-visible explanation, and undo boundary.

Operations include add/remove/rename, group/split, add dependency, set duration, add constraint, schedule, move, defer, replace, compare alternatives, explain, and commit. Draft operations are immediately reflected in the conversation; app mutations pass through a change set.

Build reusable, authored planning methods using hierarchical task decomposition: trip preparation, exams/study, projects, events, errands, routines, and a domain-independent goal-to-milestone fallback. Methods declare prerequisites, useful questions, optional steps, alternatives, and ordering rules. They are suggestions, not mandatory checklists. For example, an international trip may make travel-document checks relevant, but the engine must ask before assuming it is international.

Permit user-created reusable plan templates and named routines. Add local factual packs only where maintained sources and provenance are practical. Do not represent static packs as current travel regulations, prices, or personalized professional advice. Within unsupported subjects, Ayva can still organize user-supplied facts, compare stated criteria, break down decisions, and manage schedules.

The existing subtask JSON cannot represent this design. Evaluate normalized task hierarchy and session tables, with adapters preserving old IDs and checklist completion. Migrate reminders, widgets, trash/restore, backups, exports/imports, completion cascades, search, and editing screens together. Avoid two independently editable copies of the same task. Published plans should reference canonical task IDs and revisions; drafts contain proposed deltas.

## 8. A real scheduling engine

Keep due dates, fixed events, preferred work windows, and scheduled sessions as different types. “Due Friday” is not “work on it Friday.” An undated checklist is valid and need not acquire a fake schedule.

Start with deterministic interval scheduling, dependency ordering, and bounded search. Place fixed commitments first. Allocate dependent work into allowed windows, respecting durations, minimum chunks, buffers, and hard deadlines. Optimize soft preferences such as earlier completion, fewer context switches, preferred hours, and minimal disruption to the existing plan. Make ranking and tie-breaking stable and explainable.

For infeasible requests, report a concrete conflict: “These steps need at least 3 hours; you have 90 minutes before the deadline.” Show practical alternatives: reduce scope, split work, change an allowed window, or move a flexible deadline. Never silently violate a hard constraint, move a protected appointment, or claim a globally optimal schedule. Bound computation; return a valid partial solution with unresolved work if the search budget is exhausted.

Scheduling must support:

- Relative dates, next-occurrence interpretation, month/year boundaries, ambiguous numeric dates, and past dates.
- Local dates versus instants, travel time-zone changes, DST gaps/overlaps, and recurrence semantics.
- Task dependencies, lead/lag relationships, buffers, recurring commitments, and incomplete work.
- Per-item estimates and user-approved defaults, including uncertainty ranges.
- Minimal-change replanning: lock unaffected items and propagate only affected dependencies.
- Alternative drafts: “keep Friday free” versus “finish earlier,” with a readable difference.
- Conflicts with current app records and later edits made outside chat.

Do not assume the app knows the user's whole calendar. Initially say “no conflict with the commitments you've added.” Android calendar integration can be an optional local permission-based adapter later. Scheduling and reminders must remain useful without that permission.

## 9. Commit, correction, and recovery

Keep proposal and execution separate without making users repeatedly approve harmless draft edits. A user can converse entirely through text, including “save this plan” or “apply those changes.” For a material change to existing commitments, present the affected items and obtain an unambiguous confirmation bound to the current revision. A vague “okay” after an explanation is not a commit.

Reuse the existing transactional, idempotent executor foundations. Extend them to atomic create/update/reparent/session operations, record-version checks, deterministic operation IDs, undo, and an outbox for alarms/widgets. Detect native edits made since preview and ask only about conflicting fields. Retrying after process death must not duplicate tasks or reminders.

Undo must be version-aware: do not roll back somebody's later edit or recreate a deleted item silently. Report separately whether app records were saved and whether Android reminder delivery is available. Test revocation of notification/exact-alarm permissions, reboot, recurrence, backup restore, and partial side-effect failure.

## 10. Better general conversation

Build compositional responses from semantic dialogue acts: acknowledge, reflect a stated constraint, answer, compare, propose, ask, summarize, explain, and repair. Maintain authored variation but prioritize factual consistency and continuity over random wording. Avoid repetitive “I can help” introductions and giant feature menus.

Support check-ins, brief small talk, explaining the app, talking through choices, evaluating pros/cons against user criteria, discussing progress, and gently offering planning when relevant. Distinguish “just listen,” “help me think,” and “help me act.” Do not turn emotional language into an automatic productivity intervention.

Explain decisions with actual planner evidence: “I put research first because booking depends on your dates.” Answer “what did we decide?”, “what remains uncertain?”, “why did you ask?”, and “what changed?” directly from state. Admit when a topic is outside the installed knowledge and redirect to a useful supported conversation without pretending comprehension.

## 11. Evaluation as a product capability

Build a deterministic conversation replay harness with an injected clock, locale, time zone, repository snapshot, and expected semantic state. Evaluate meanings, questions, selected IDs, plan revisions, and mutations; do not require one exact phrasing for every response.

Maintain held-out scenarios authored independently from grammar rules. Include natural paraphrases, misspellings, fragments, multiple facts per turn, negation, corrections, ambiguity, quoted commands, interruptions, restarts, changing dates, and unrelated tasks. Add failures from opt-in local feedback only; do not upload private conversations by default.

Track task success across entire conversations, unwanted writes, wrong-reference resolution, repeated questions, unnecessary clarifications, false mode activation, lost drafts, recovery after interruption, latency, and constraint violations. A large unit-test count alone does not demonstrate conversational quality.

Proposed initial release gates, to validate against measured baseline:

- Every critical mutation, stale-reference, keyboard/draft, migration, and recovery scenario passes; zero unintended writes in the release corpus.
- At least 95% successful completion on a held-out corpus within the declared supported domains; report coverage and abstention separately.
- No hard constraint violations in property-based scheduler tests; reproducible explanations and stable output for the same inputs.
- No repeat request for an already captured, still-valid fact in the critical conversation suite.
- Durable drafts survive all defined Back, rotation, recreation, process-death, and resume tests.
- Proposed performance budgets: p95 ordinary turns under 150 ms and typical 50-item replans under 500 ms on a named midrange test device. Bound larger searches and show progress/cancellation. Measure before committing to those numbers.
- TalkBack, large fonts, disabled animations, narrow screens, and representative OEM/IME checks pass.

The existing 920-test baseline and debug/release builds are valuable regression protection. Preserve relevant invariants; update tests that deliberately encode superseded product behavior with explicit rationale. Keep the chat/notes/vault data boundary. New persistent assistant data needs encryption, bounded storage, schema migration, retention controls, and backup policy consistent with the app.

## 12. Delivery roadmap with acceptance gates

These are dependent product milestones, not batches of isolated phrase handlers. Each milestone must improve complete user journeys.

| Milestone | Main deliverable | Acceptance evidence | Rough focused engineering effort |
| --- | --- | --- | --- |
| 0. Baseline and interaction recovery | Reproduction matrix, conversation corpus, keyboard/draft fixes, greeting relevance | Original Back failures reproduced and fixed on devices; no text loss; plain greeting stays conversational | 1–2 weeks |
| 1. Mode-aware conversation shell | Command registry, `/plan`/Talk/Drill lifecycle, compact suggestions and controls, persisted composer state | Mode switches preserve text and suspended work; false activation tests; visual/accessibility review | 2–3 weeks |
| 2. Shared dialogue and durable context | Typed candidates, arbitration, references, frames, persistent drafts, explicit memory | Multi-turn correction/interruption/resumption scenarios pass; no stale authority revival | 3–5 weeks |
| 3. Plan graph and conversational planning | New entities, decomposition methods, domain-independent planning, inline editing and explanations | Trip/study/project plans completed entirely through chat, with unresolved dates supported | 3–5 weeks |
| 4. Scheduling and replanning | Intervals, dependencies, conflict explanations, alternatives, minimal-change solver | Feasible plans satisfy constraints; infeasible ones explain conflicts; DST and recurrence tests | 3–5 weeks |
| 5. Canonical task integration | Scheduled subtasks, migrations, atomic execution, native editor synchronization, undo/outbox | Upgrade/restore/crash tests, widgets/reminders, edits from both chat and native screens | 3–5 weeks |
| 6. Breadth and release hardening | Expanded domain/language coverage, conversational polish, performance and device testing | Held-out end-to-end gates pass and supported scope is documented | 3–6 weeks |

Budget roughly 18–31 focused engineering weeks for the baseline scope, with uncertainty concentrated in parser coverage, migrations, and Android behavior. For one developer doing implementation, design, content, and QA, six to twelve calendar months is a more prudent planning range. These are preliminary estimates, not a promised delivery date or an estimate for every expansion in section 16. Include explicit discovery, evaluation, and integration work in each milestone; do not assume those activities are free. Re-estimate after milestones 0 and 2 and after consequential capability additions. Design the task/schema migration during milestone 2 even if rollout is milestone 5. Independent UI and corpus work can overlap, but schema decisions must precede persistent planner rollout.

First meaningful vertical slice: one user plans a trip through chat, changes a date, resolves one genuine scheduling conflict, leaves the app, resumes later, and commits once without duplicating tasks. Expand from that tested path to study and projects. Do not wait until the end to evaluate real conversations.

Introduce feature flags and adapters to existing commands. Shadow comparison must be read-only and local; never execute both old and new routes. Roll out supported journeys incrementally with data-compatible fallback. Destructive schema transitions need migration fixtures and recovery plans rather than a nominal toggle.

## 13. Completion definition

The feature is exceptional when the user can say what they want once, refine it naturally over many turns and sessions, understand what Ayva knows and is assuming, get feasible schedules with clear conflict resolution, and trust that agreed changes happen exactly once. It must remain useful when the user is uncertain, changes their mind, interrupts, types imperfectly, or returns tomorrow.

The bounded core implementation spans editor recovery, greetings, modes, durable discussion/planning, canonical task hierarchy and multiple sessions, work-start reminders, native editing, reviewed writes, constraints and dependent replanning, templates/preferences, numeric ranges and binary qualitative reasoning, multi-level undo/redo, and encrypted archive/rolling recovery. Existing native tasks can be adopted without duplication; completed/occasion records stay read-only context. See the [current ledger](assistant/implementation/CURRENT.md) and [delivery record](assistant/implementation/COMPLETION_IMPLEMENTATION.md) for exact supported behavior and evidence. The user has deferred broader audits. Remaining acceptance includes independent conversation evaluation, performance/device/visual checks and a current optimized release; graded/weighted reasoning, richer knowledge/languages, deeper arbitration and advanced optimization remain expansion tracks.

## 14. Treat the roadmap as an evolving design

The implementation must improve this plan as it learns. Initial requirements can be firm while architectures, estimates, and hypotheses remain revisable. Discoveries must expand useful capability, not merely generate more documentation or more supported phrases.

Maintain three layers:

1. **Product commitments:** the user-visible outcomes and constraints in the requirements register. These cannot disappear during refactoring, context loss, or scope reduction.
2. **Current implementation plan:** the best supported route to those outcomes, including dependencies, migrations, and validation. Change this when evidence supports a better route.
3. **Expansion frontier:** additional capabilities, uncertain approaches, and research questions. Promote an item only after it has a concrete user journey, a plausible offline/no-model design, and a validation approach.

Every milestone includes a capability review at its start and end. Ask: what can the user now accomplish; which realistic conversation still breaks; does the failure expose a missing phrase, a missing concept, or a wrong architecture; can one reusable primitive solve several gaps; and does the change remain practical on device?

Reserve an explicit discovery allowance, initially around 15–20% of each milestone's capacity, and revise it using actual findings. Use bounded prototypes for high-uncertainty choices. Do not spend months completing a design that a short experiment could disprove. Equally, do not let perpetual redesign prevent shipping a useful, tested journey.

Routine architectural and implementation decisions within the agreed scope should proceed without repeated user approval. Bring genuinely consequential product tradeoffs to the user after preparing concrete alternatives: a model/network dependency, new access to private data, incompatible behavior, removal of a requested capability, or a major scope/timeline change. Never silently introduce these as convenient workarounds.

## 15. Continuous discovery and improvement loop

For every meaningful vertical slice:

1. **Observe:** run realistic conversations, inspect actual state transitions, and capture the smallest reproducible failure with its clock, locale, records, and mode.
2. **Classify:** language coverage, reference resolution, dialogue ownership, missing knowledge, planning representation, scheduling, execution, lifecycle/UI, or performance. Separate a capability gap from a bug.
3. **Propose:** record the desired user outcome, evidence, candidate approaches, affected contracts, risks, and a small discriminating experiment.
4. **Prototype:** compare at least two approaches when the choice materially affects architecture. Use disposable fixtures and read-only execution until mutation behavior is validated.
5. **Integrate:** prefer a reusable concept over one-off phrase patches. Include migration, state serialization, UI behavior, and failure handling in the change.
6. **Challenge:** try negation, corrections, similar names, interruptions, stale records, unknown values, and a different domain. Verify behavior through the real routing and persistence path.
7. **Measure:** compare against the prior baseline, including regressions, latency, memory, clarification burden, and unsupported-input handling.
8. **Update:** revise the capability matrix, decision record, roadmap dependencies, and next-session handoff. Record rejected approaches and the evidence so future work does not repeat them blindly.

Once a held-out example has been studied to improve the implementation, treat it as a regression example and replenish the held-out set. Do not repeatedly tune to one supposedly independent test set. Scenarios should be independently written or reviewed and include real user evaluation where available; generated variations alone are insufficient evidence of natural conversation quality.

Use metamorphic tests: paraphrasing should preserve meaning, adding an irrelevant fact should not change the target, negation should prevent an action, list reorderings should not change stable references, and replaying a confirmed operation should not duplicate it. Cross-domain transfer is another gate: a dependency feature built for trips should also work for study or projects without duplicating the engine.

If repeated attempts fail, stop patching the symptom. Summarize the diagnoses tested, inspect the abstraction boundary, and change the approach or isolate a concrete blocker. Do not lower the acceptance criterion just to mark the milestone complete.

## 16. Capability expansion frontier

These tracks broaden the assistant beyond the initial examples. Foundational mechanisms belong in the baseline architecture; advanced depth is promoted through evidence and user value, not automatically claimed as implemented.

| Track | User-visible expansion | No-model implementation direction | Gate before promotion |
| --- | --- | --- | --- |
| Language composition | Several facts and changes in one message; fragments, corrections, negation, references, and informal wording | Shared grammar productions, scoped clause representation, candidate ranking, domain lexicons | Novel compositions succeed across domains without unintended actions |
| Ambiguity repair | “Not that booking—the other one,” “I meant next week,” “why are you asking?” | Typed ambiguity sets, question provenance, correction operations, explanation acts | Repair preserves unaffected facts and avoids restart or repeated questions |
| Goal discovery | Turn a vague intention into a useful plan without demanding a complete checklist | Goal/decision schemas, domain methods, question-value heuristics, uncertainty tracking | Users can progress with unknown dates, estimates, or destination |
| Decomposition | Build milestones, nested steps, prerequisites, parallel work, and optional branches | Hierarchical planning methods with preconditions and effects; graph validation | New domains reuse the same planner primitives; no cycles or invented required steps |
| Decision support | Compare options using budget, effort, timing, and user-stated priorities | Explicit criteria, weighted comparisons, dominance checks, reason templates | Explanations reflect inputs; unknown values remain unknown |
| Alternatives and counterfactuals | “What if we leave Sunday?”, “What would change if I had half the time?” | Branch revisions, bounded solver reruns, semantic diffs, isolated hypothetical frames | Hypotheticals never mutate the active or published plan |
| Robust scheduling | Handle uncertain durations, buffers, split work, recurring commitments, and competing goals | Interval constraints, duration ranges, scenarios, protected commitments, bounded optimization | Valid schedules or explicit infeasibility; measured mobile performance |
| Long-term continuity | Resume weeks later, track several plans, recall reasons and unresolved decisions | Durable frame/plan stores, structured summaries, named references, live-record revalidation | No cross-plan leakage, stale writes, or unnecessary re-entry |
| Replanning from progress | Adapt when a task takes longer, a session is missed, or priorities change | Completion/progress events, remaining-work estimates, minimal-change propagation | Only affected flexible work moves; changes are explained and reversible |
| Negotiation | Relax a preference, compare the cost, and preserve non-negotiable commitments | Hard/soft constraint types, conflict explanations, user-approved relaxation | No silent hard-constraint relaxation or coercive suggestion loops |
| User-authored methods | Reuse “how I prepare for exams” or “my travel checklist” | Versioned editable templates and parameter bindings; explicit saved defaults | Templates remain inspectable, removable, and safe to apply repeatedly |
| Conversation depth | Explain, reflect, summarize, ask useful follow-ups, discuss progress and tradeoffs | Dialogue acts, semantic response composition, topic packs and repetition management | Evaluators observe continuity and usefulness, not just surface variation |
| Local knowledge | Broader factual explanations for supported topics | Curated, versioned factual packs with sources and freshness metadata | Knowledge is grounded, license-compatible, storage-bounded, and not presented as live information |
| Language/localization | Additional languages, date conventions, and mixed-language phrasing | Locale-specific grammar/lexicon packs and shared semantic representation | Separate held-out evaluation; no false claim of universal multilingual support |
| Local integrations | Check device calendar commitments and optionally consume supported input channels | Explicit permission adapters feeding the same typed pipeline | Core remains offline and functional without permissions; integration scope is clear |
| Personalization | Remember planning preferences and offer better defaults | Explicit preferences, user-authored aliases, optional inspectable summary statistics | No hidden inference of personal traits, silent grammar rewriting, or reinforcement of mistaken assumptions |

Local adaptation is not autonomous self-modification. Runtime learning in this project means explicit preferences, editable aliases/templates, and optionally user-approved factual updates. Grammar changes, knowledge-pack changes, and new planning logic go through the development and validation process. No model training or opaque inferred profile is introduced under the label “learning.”

Prioritize expansion by observed frequency/severity of user friction, how many journeys a reusable primitive unlocks, confidence from experiments, regression risk, and implementation cost. Keep scores transparent and provisional; do not invent precise value estimates. Fix lost work, wrong actions, and misleading output before broadening optional knowledge or social games.

## 17. Extensibility contracts that make growth practical

Define contracts before broadening domain coverage:

- A **capability descriptor** declares supported modes, input meanings, required context, permissions, side effects, and result types. It does not independently parse and execute arbitrary text.
- A **domain pack** supplies vocabulary, planning methods, questions, factual content, and examples. It cannot bypass the shared executor or access private data through response rendering.
- A **dialogue policy** selects typed questions and next moves using frame state. Domain-specific logic extends explicit rules instead of altering global dispatch order unpredictably.
- A **planner operator** declares preconditions, effects, invariants, reversibility, and validation. A schedule proposal is data, not an executable command string.
- A **response document** carries structured explanation, references, optional controls, and status. Text, accessibility output, and compact UI render from the same semantics.
- A **context query** declares what information is relevant and permitted. A greeting does not gain broad data access merely because some other capability needs it.

Create developer tooling to inspect a turn locally: selected mode, candidates, discarded interpretations, resolved references, applicable facts, selected question, plan delta, solver reasons, and proposed writes. Redact private values by default in exported diagnostics. This trace makes subtle failures debuggable without relying on a developer guessing which regex fired.

Version all persistent schemas and domain-pack formats. Reject incompatible packs cleanly and test upgrades/downgrades where supported. Index relevant records and parse incrementally where useful; move expensive work off the UI thread and support cancellation. Maintain explicit memory, storage, and execution budgets as breadth increases.

## 18. Continuous architecture challenges

The following assumptions are hypotheses, not permanent commitments:

| Question | Experiment | Decision consequence |
| --- | --- | --- |
| Is a custom compositional parser maintainable enough? | Implement the same correction-heavy corpus with a small parser-combinator/grammar approach and the current bounded recognizers | Choose based on held-out behavior, debug visibility, footprint, and maintenance cost |
| Can a pure Kotlin bounded scheduler meet the target? | Benchmark realistic 10/50/200-item dependency sets, recurrence, and infeasible constraints on a named device | Keep a heuristic solver or evaluate an offline deterministic solver library with acceptable license/footprint; never introduce a model as a shortcut |
| Which task hierarchy migration is safest? | Prototype normalized child tasks/session tables and compatibility adapters against upgrade/backup/widget fixtures | Select canonical storage before shipping persistent advanced plans |
| How much conversation state is useful? | Run long conversations with interruptions and multiple plans, inspect retrieval relevance and stale-reference behavior | Adjust frame selection and summarization rather than retaining everything indiscriminately |
| Does authored decomposition generalize? | Reuse the same graph operators and dialogue policies in trip, study, and project journeys | Refactor duplicated domain logic before expanding more packs |
| Are compact controls actually easier to use? | Device trials with large fonts, TalkBack, keyboard open, and accidental taps | Revise layout, hit regions, and animation from observed behavior |
| Is conversation becoming too interrogative? | Measure repeated/avoidable questions and completion success with uncertain users | Improve extraction and partial planning rather than hiding questions that remain necessary |

Record experiment outcomes with actual evidence. Prefer a measured smaller implementation over an elaborate architecture that has not demonstrated user value. Conversely, do not reject a necessary data migration simply because the old implementation avoided migrations.

## 19. Requirements coverage and protected boundaries

Use these stable requirement IDs in implementation slices and evaluation scenarios:

| ID | Commitment | Primary gates |
| --- | --- | --- |
| R01 | On-device assistant without models or required network services | Dependency/data-flow audit; offline end-to-end execution |
| R02 | Complete planning through the main chat; no mandatory duplicate form | Trip/study/project journeys completed with typed replies only |
| R03 | Dedicated `/plan`, shared command suggestions, visible and persistent mode | Routing, mode switch, draft/cursor preservation and false-activation tests |
| R04 | Useful general conversation and relevant context; greetings do not dump distant tasks | Social, agenda-horizon, occasion, and interruption scenarios |
| R05 | Goals, tasks, subtasks, dependencies, dates, work sessions, rearrangement | Plan graph, scheduler, canonical task integration and native UI tests |
| R06 | Clarify meaningful ambiguity and conflicts; accept corrections naturally | Semantic state and mutation checks across multi-turn repairs |
| R07 | Durable plans and resumable conversations without stale authority | Restart, time passage, record changes, clear/forget and retention tests |
| R08 | Keyboard Back behavior and preservation of typed work throughout the app | Real device/window/IME matrix and lifecycle recovery tests |
| R09 | Polished compact controls, subtle blue mode suggestions, accessible motion | Visual/device review, large font, TalkBack and reduced-motion checks |
| R10 | Exactly-once agreed changes, explanations, recovery, and usable undo | Transaction, conflict, idempotency, outbox and crash-window tests |
| R11 | Broaden capabilities during implementation through evidence | Capability ledger, experiments, cross-domain scenarios and milestone reviews |
| R12 | Preserve app data integrity and established security boundaries | Migration/restore tests; chat boundary guard; blocking overlay regression |

Every requirement has an owner milestone, evidence, and current status in the implementation ledger. A deferred requirement remains visible with its reason and next dependency; it does not quietly disappear from the definition of done. An optional expansion must not replace a baseline requirement because it is easier to demonstrate.

The direct WindowManager overlay blocking mechanism in `AGENTS.md` remains a protected invariant. Notes/vault contents remain outside assistant context under the existing boundary. Keyboard fixes must respect secure-screen behavior and window-specific lifecycles. Broader conversational capability does not imply permission to broaden private-data access.

## 20. Release gates and an open improvement horizon

Define completion per release while leaving room for further capability growth. A release can be complete against declared supported journeys even while the expansion frontier remains open. Never call an assistant universally conversational because one domain passes.

For each release publish an internal capability report: supported journeys, tested language/locale scope, known limitations, failure categories, evaluation conditions, performance measurements, and device coverage. Preserve regression scenarios and report improvements against prior versions. Re-estimate the next release from observed work, not the original speculative timeline.

At each release boundary choose a small number of high-value expansion tracks and complete their end-to-end journeys. Maintain the long-term frontier, but avoid an unlimited collection of unfinished features. The goal is continuing, measurable growth in capability with stable everyday behavior—not a frozen plan and not endless redesign.
