# Ayva conversation quality — delivered implementation specification

Status: **finite implementation batches delivered within documented bounds**. Updated 2026-10-21. See [the completion record](implementation/CONVERSATION_QUALITY_CURRENT.md) for supported contracts, limits and source-specific checks. Baseline: version 2.0.11 / 111. The sections below preserve the authorized specification; they are not a fresh implementation queue. P3 visual/IME acceptance is blocked by the unusable cloud emulator. Independent quality evaluation and the wider device matrix remain deferred under CQ10. The completion record controls current acceptance status.

## Outcome and constraints

Make everyday conversation attentive, coherent and useful: understand supported combinations, remember what matters, respond appropriately, explain uncertainty and help a user think before turning discussion into a plan. Keep everything offline with ordinary deterministic code, authored knowledge and structured state. No language/embedding models, downloaded inference, server calls or hidden conversation telemetry. English first; additional languages require their own grammar/content validation.

Ayva's voice should be warm, calm, curious and concise, with optional light humour. It must respect “just listen,” stop unsolicited productivity coaching, avoid claiming feelings or personal experiences, and admit missing understanding. Personality is a consistent response policy, not merely a larger joke bank. No numeric quality rating is promised in advance.

Preserve current task, habit, routine, Drill, settings and focus owners. Keep Notes/vault inaccessible to chat and blocking overlays unchanged. New conversation context must never restore expired execution authority. Keep existing confirmations where required; improve conversation without adding forms or new confirmation steps to harmless discussion.

## Highest priority: repair the reported planning experience

User screenshots supplied after this plan show two repeated command-help replies to “lets plan a trip together” and “lets plan for ibps exam trip”, an active workspace titled “hey”, an oversized composer with duplicated Plan controls, and keyboard overlap hiding its lower controls. The third screenshot is the requested visual reference: a compact blue pill above the composer with icon, “Create a plan”, “Use plan mode” and dismiss. These issues reopen **experience acceptance** for the earlier delivered core; implementation-complete must not be treated as evidence the experience is satisfactory.

**First record the minimal CQ0 ownership map and baseline for the reported failures, then do these priority batches before broad CQ0–CQ10 expansion.** CQ9's affected composer/mode work is pulled forward here. Borrow only the minimal interpretation and dialogue changes needed from CQ1–CQ4, then generalize them in those batches. Priority repairs are implemented; verification status is tracked in the completion record.

### P0 — Restore the old chatbox and fix keyboard visibility

Inspect the actual chat host/window, inset consumption, sheet height and composer layout together. `AyvaComposerBar.kt` is the composer owner; an `adjustResize` declaration alone does not prove the chat host resizes correctly. The screenshots establish overlap, not its precise cause. Trace the host before choosing one owner for IME/navigation-bar insets; avoid stacking padding fixes at multiple levels.

Restore the old chatbox appearance and its functions above the input, as explicitly requested by the user. Recover the pre-redesign layout from Git history (start by comparing `e5378ed` with `2f34aea`), including its shape, spacing, colours, input sizing and function placement; verify the actual historical implementation before selecting the restoration baseline. Preserve the original functions and their behaviour rather than moving them into the new bottom toolbar or hiding them in an overflow menu. This is a targeted visual restoration, not a wholesale code rollback: retain current draft persistence, keyboard fixes and action safeguards. Keep the text area bounded as it grows. Keep text, cursor, send and mode controls above the keyboard, including multiline input and suggestion display. Reserve transcript space for the composer so the latest reply is reachable. First Back dismisses the keyboard without leaving chat or losing the draft; later Back follows normal navigation. Preserve text through keyboard show/hide, mode changes and returning to chat.

Acceptance: reproduce the reported keyboard-open state and verify all controls remain visible/tappable on the affected configuration when available; also check a small viewport, gesture navigation and multiline input. Record Android/keyboard configuration actually checked. A screenshot with the keyboard closed is insufficient evidence. Use focused inset/Back/draft regression checks; the broader device matrix remains separate.

### P1 — Reference-matched blue suggestion and subtle selected mode

Add the supplied reference's interaction to the restored old UI: one compact rounded blue suggestion above the composer, with a small planning icon, short label, “Use plan mode” action and dismiss. Selecting it removes the suggestion and places one subtle blue Plan indicator at the top inside the composer. Avoid the current redundant heading plus prominent bottom mode control. Keep the old function controls above the chatbox in their established positions; the blue suggestion supplements them and must not replace or obscure them. Apply the same suggestion interaction to Talk/Drill where appropriate. Do not copy unrelated model/security controls from the reference or redesign the rest of chat.

Typing `/plan` or a clear mode request can offer the suggestion without sending text. Selecting a suggestion preserves the user's meaningful text, selection, focus and keyboard. Sending an explicit planning request must work without tapping the suggestion. A mention of “plan” inside unrelated prose must not change modes. Dismissal persists for that suggestion/input episode instead of reappearing on every keystroke. Mode exit preserves the draft and does not save tasks.

Use restrained fade/position transitions, stable layout and reduced-motion support. Match the reference's visual hierarchy and proportions within Ayva's theme, using sufficient touch targets and contrast. Exact animation timing cannot be inferred from a still image; specify and review it during implementation. Acceptance requires comparing the restored chatbox and function row against the historical UI, and the popup against the supplied reference, in keyboard-open and keyboard-closed states. Verify both the old functions and the new popup remain usable together. A passing visibility assertion alone is insufficient.

### P2 — Conversational planning without the command-help loop

The repeated text comes from `ConversationPlanner.kt`'s unrecognized-edit fallback. `OfflineAssistant` routes into this planner; inspect entry/re-entry and active-workspace ownership together. The displayed “hey” title suggests an irrelevant workspace is being retained; investigate its creation and reuse instead of assuming a greeting should become a goal.

Plan mode is a conversational context, not a command-only parser. It must support discussing a goal, explaining suggestions, answering questions, changing constraints and adding draft steps in the same exchange. Do not require “add …” syntax, a separate answer form, or a switch to Talk to discuss the plan. Unknown messages must not fall through into native task creation.

Implement a planning dialogue layer that identifies goal introduction/replacement, current-question answers, facts/preferences, questions, suggestions, accepted draft additions and corrections. Reuse the existing typed workspace and executor. Build a thin vertical slice of the shared goal/dialogue contracts below, first exercised by trip/exam-travel and an unrelated project. Do not introduce separate trip/exam conversation engines or phrase-specific state. A friendly replacement fallback is insufficient. On an unfamiliar phrase, use known context to ask one specific clarification; avoid repeating the same help paragraph. Show command syntax only when requested as help.

A clear new goal should start or explicitly offer a separate draft while preserving an existing meaningful draft. Empty/accidentally greeting-named drafts need a safe repair path; never silently overwrite saved work. Distinguish travel for an exam from studying for it. “IBPS exam trip” must prompt a scoped question when intent is unclear, without inventing exam dates, venues or travel prices.

Ayva should contribute grounded suggestions, explain their purpose and adapt them to user facts. “We need tickets and a hotel” can add those to the active draft and acknowledge the change naturally. “Maybe we could stay overnight” remains a possibility until accepted. Proposals and factual conversation need no confirmation ceremony; saving/updating actual app tasks uses the existing required review. User-facing replies should emphasize decisions and useful next questions; hide revision numbers and routine timezone metadata in optional details unless relevant to a scheduling ambiguity.

Acceptance journey (illustrative wording, not exact-string requirements):

- User: “lets plan a trip together” → acknowledge the trip and ask where/to what purpose; no command lecture, no goal named “hey”.
- User: “its for my ibps exam” → connect the trip to attending an exam and ask for the city or known date. If the initial request is “lets plan for ibps exam trip”, clarify travel versus preparation only as needed.
- User: “Chennai, next Friday, I need to be there by 8am” → retain destination and arrival constraint; resolve date/time ambiguity only when necessary.
- User: “we need train tickets and somewhere to stay” → add appropriate draft preparation steps, discuss arrival feasibility and ask one useful missing detail; no fabricated timetable or hotel availability.
- User: “why a hotel? could I travel overnight?” → explain the trade-off, retain the alternatives and avoid saving tentative lodging as a settled choice.
- User: “yes overnight, remove the hotel, add checking the admit card” → update only the relevant draft items; preserve arrival constraint and any unresolved train timing.
- User: “just talk it through first” → continue reasoning with the same plan context and pause unsolicited additions.
- User: “okay save the agreed tasks” → present the existing meaningful review with known dates, unresolved fields and changes; apply only through the existing authorized path.

Necessary evidence: one end-to-end journey through the actual chat entry point with an irrelevant existing draft, plus ambiguity/no-unintended-write and correction cases. Verify the existing trip, exam, task and Talk owners still work. Do not treat a single newly recognized phrase or friendly fallback as completion.

### P3 — Accept the repaired experience before expansion

Review the screenshot scenarios as integrated chat journeys: compact blue suggestion, preserved composer/keyboard, natural planning dialogue, explanations and reversible draft edits. Include an interruption and return to the trip. Record actual before/after responses and visual results. Mark P0–P2 implemented and verified separately; leave device-specific verification pending where unavailable. Broader CQ batches then deepen the same architecture instead of replacing it again.

## Architecture direction

Evolve the current `OfflineAssistant`, `AyvaIntentRouter`, `AssistantDiscussion`, `AssistantSocial`, `AssistantSession`, `DiscussionComparison` and `ChatMode` incrementally. First map their ownership, then add a shared semantic interpretation and dialogue policy behind existing entry points. Avoid a parallel assistant with its own competing state or an all-at-once rewrite.

The target pipeline is: literal-safe input → candidate semantic frames → reference/context resolution → ownership arbitration → dialogue move → grounded response composition → existing action executor when authorized. Candidate interpretations carry supporting spans, uncertainty and source identity. Dialogue moves include acknowledge, reflect, answer, ask, compare, repair, suggest and hand off. Facts carry source, scope, temporal validity and correction history. Conversation memory and action authority remain separate.

## General planning contract — required across domains

The trip/exam examples expose a general problem; they must not define the limits of the solution. A topic label alone cannot select a rigid questionnaire. The shared planning model must represent **outcome → milestones → actionable steps → optional scheduled sessions**, with each level independently discussable and revisable. Travel, exam preparation, exam attendance, projects, events and unfamiliar user-defined goals use the same contracts. Do not equate domain coverage with understanding arbitrary subjects.

### Shared state and ownership

Extend existing typed workspace/state incrementally, without introducing a second source of truth:

| Concept | Required representation and behaviour |
| --- | --- |
| Goal | Stable identity, desired outcome, user-defined success criteria, scope, current stage and optional deadline |
| Evidence | User statement, authored knowledge or explicitly derived fact; retain source and uncertainty; do not promote an assumption to fact |
| Constraints | Hard versus preferred, scope, dates/availability, budget units, resources and dependencies; separate unknown, rejected and explicitly absent values |
| Decisions | Alternatives, accepted choice, rejected choices, reasons and assumptions; provisional choices remain revisable |
| Work | Milestone/step identity, parent, prerequisites, effort range, status, canonical task link and relevant constraints; title changes never change identity |
| Dialogue | Current focus, question target, unresolved issues, paused topics, rejected suggestions and listen/explore/organize stance |
| Proposal | Suggested versus accepted draft work, explanation and downstream impact; distinct from a saved native task |

Use the existing graph, discussion and comparison structures wherever possible. Identify which fields belong in transient discussion versus durable workspace before adding schema. Define backward-compatible readers, bounds, truncation rules and forget semantics. Displayed plan summaries must be projections of this state, not a separate text plan that can drift from saved tasks.

### Planning moves that must compose

- Discover the desired outcome when vague; accept a detailed goal without re-asking answered questions. A goal may remain unscheduled while the user explores it.
- Suggest meaningful milestones, then decompose only the part being discussed. Explain why a suggested step helps and what information it depends on. Avoid dumping a generic twenty-item checklist.
- Answer questions and compare alternatives before asking the next planning question. A mixed message may answer a question, introduce a constraint and ask for an explanation; account for all recognized parts and identify any unresolved portion.
- Handle user initiative: accept volunteered details in any order, skip optional questions, revise earlier choices and move between broad discussion and concrete steps without changing modes manually.
- Rank clarification needs: ambiguous action target and hard conflicts first, information blocking the next useful step second, optional preferences later. Ask at most one focused question by default; offer an explicitly labelled reversible assumption only when it is low consequence. Do not demand every field before contributing something useful.
- Keep suggestions tentative until accepted or clearly requested as additions. A declarative need in an active plan can become draft work; quoted, hypothetical, rejected and speculative actions cannot. Explain meaningful draft changes briefly, without repeating the whole plan after every turn.
- Explain current decisions, what changed, what remains unknown and what happens next. “Why?”, “what did we decide?” and “what are we missing?” must refer to current evidence and actual decisions.
- Support a what-if branch without changing the accepted draft. Compare its consequences and merge it only on request; bound branch count/storage and retain canonical identity on merge.
- Resume, pause, rename, split, combine and archive plans through explicit intent. Before merging plans, resolve conflicting constraints and duplicated linked tasks. Never duplicate saved tasks because a topic was resumed or rephrased.
- End naturally: conversation can finish with an idea, a comparison, a partial draft or saved tasks. Do not repeatedly push saving, scheduling or productivity when the user wants to discuss.

### Knowledge and reusable planning methods

Build a small registry of composable methods rather than domain-specific scripts. Each method declares its applicability, required inputs, optional inputs, steps, rationale, dependencies, alternatives, unknowns and completion conditions. Useful general methods include prepare materials, learn/practise/review, arrange logistics, research/compare/decide, produce/review/deliver and coordinate an event. Method selection must be explainable and overridable.

Compose methods for mixed goals: “prepare for an exam in another city” can need study and travel, sharing an exam date while keeping separate milestones and constraints. Ask which parts the user wants help with; do not force a single trip-versus-exam classification when both apply. Shared constraints propagate only to their declared scope. A change to travel dates need not rewrite the study syllabus.

For unfamiliar goals, use a generic collaborative scaffold: desired result, existing progress, obstacles, user-known steps and available time. Contribute general organization, dependency ordering, comparisons and scheduling from supplied facts. Ask the user for domain knowledge rather than inventing expert procedures. If the user says “I don't know,” offer a modest useful next move or explain the missing knowledge; avoid endlessly returning the same question.

Authored knowledge must include scope, rationale and provenance. No invented train schedules, exam syllabuses, registration rules, venue requirements, current prices or professional advice. “Check the official requirements” can be a suggested task; an unknown requirement cannot be asserted as mandatory. Optional user-supplied information can fill the gap without fetching private app data or requiring network access.

### Evolving plans and real-life constraints

Separate deadlines, arrival times, work intervals and flexible availability. Account for effort ranges, buffers, prerequisite lead times, recurring commitments and constraints crossing midnight/timezones. Missing duration may allow draft organization while preventing precise scheduling. If constraints conflict, name the conflict and offer feasible relaxations; do not silently move a protected commitment.

Progress can be partial, blocked, skipped or complete. Replan only affected unfinished work, show consequences and preserve accepted constraints. Daily workload, available energy and priorities may be explicitly supplied by the user; never infer a hidden psychological profile. Reconcile edits made elsewhere in the app, interrupted saves and restored backups through existing version checks/receipts. Explain stale conflicts in ordinary language.

Multiple plans can compete for the same time/resource. Use canonical occupied sessions and explicit user priorities; do not give each draft a fictional empty calendar. Scheduling recommendations remain grounded in known availability. Advanced optimization is incremental: start with correct feasible proposals and explain their trade-offs before adding scoring heuristics.

### Where this lands in the batches

| Owner batch | Required deliverable from this contract |
| --- | --- |
| P2 | Shared goal/dialogue interface, natural entry/re-entry, grounded draft updates, trip plus unrelated-project vertical slice |
| CQ1–CQ2 | Consistent ownership, multi-clause meaning, uncertainty and reference resolution |
| CQ3 | Stable goals, bounded topics, decision/evidence memory, reload compatibility and branch storage |
| CQ4–CQ5 | User initiative, useful question selection, substantive replies, repetition control and coherent voice |
| CQ6 | Reusable method registry, mixed-domain composition and unfamiliar-goal scaffold |
| CQ7 | Decision explanations, what-if comparison, provisional choices and explicit branch merge |
| CQ8 | Partial progress, dependencies, competing plans, split/merge/archive and affected-work replanning |
| CQ10 | Implementation handoff, indispensable checks and explicit deferred-audit ledger |

These are required planned deliverables, not an unowned wishlist. Child batches may split them for implementation, but cannot silently drop them. Record deferred scope explicitly with its user impact.

## Acceptance beyond a demonstration conversation

Use only absolutely necessary checks during implementation. The journey matrix below specifies desired behaviour, not a requirement to build an automated suite or conduct an audit now. Broad human/held-out evaluation is deferred until explicitly requested or needed to investigate a concrete failure. Do not turn every wording variation into a separate brittle automated test. Assess the actual UI → routing → discussion/draft → reviewed native-save path; direct parser calls alone cannot establish a successful user experience.

| Journey family | Must demonstrate |
| --- | --- |
| Leisure trip | Preferences, alternatives, dates, rationale and grounded preparation steps |
| Exam preparation | Starting level supplied by the user, study/practice/review milestones and changing daily availability |
| Exam travel plus study | Mixed goals, shared date, scoped corrections and no fabricated exam/travel facts |
| Project or event | Outcome, milestones, dependencies, resource limits and deliverable changes |
| Unfamiliar goal | Useful generic collaboration, user-supplied expertise and honest knowledge boundaries |
| Long-running plan | Restart/resume, interruption, partial progress, native edits and constrained replanning |
| Multiple plans | Stable references, competing commitments and explicit split/merge without duplicate saved work |
| Conversation without execution | Listen, explore, disagree, reject suggestions and finish without creating tasks |

Cross these selectively with misspellings, apostrophe-free informal English, long multi-intent messages, short answers, contradictions, negation, hypothetical wording, abandoned questions and requests outside supported language. Include at least one unfamiliar goal and new paraphrases not used to author the rules. Prefer a few meaningful end-to-end journeys to hundreds of isolated recognition claims.

Release acceptance must establish: no known unintended writes or lost accepted constraints; no repeated command-help loop in the supported journeys; successful contextual explanation and correction; no mandatory form or command syntax; useful contribution rather than continuous questioning; preserved old composer/functions and keyboard visibility; and a clear account of unsupported cases. Track task success, context errors, clarification loops, repetition and human-rated usefulness separately. Set quantitative targets from CQ0 before implementing the measured improvements, not after seeing the results. Passing the showcased trip conversation alone cannot close this plan.

## Maximum-scope conversation programme

The target is the strongest practical model-free assistant we can incrementally build, not a claim of unrestricted AI understanding or a provable absolute maximum. Planning and general conversation share interpretation, evidence, topic state and response policy. New content must deepen these capabilities rather than create competing dispatchers. The following are required planned child batches, with explicit limits and owners.

### Planning experience additions

- **CQ8.E1 — Initiative with restraint:** offer the next useful contribution from the current goal and facts (an overlooked dependency, alternative, conflict or suggested step), explain why, and stop when declined. No unsolicited agenda on greetings, repeated saving prompts or autonomous notifications. Let the user ask for more or less initiative.
- **CQ8.E2 — Adjustable depth:** move between overview, milestones and one detailed step through conversation. “Make it simpler,” “break this down,” “what should I do first?” and “give me the full picture” operate on the same stable plan. Reduce scope when time is insufficient instead of compressing work unrealistically.
- **CQ8.E3 — Explainable assistance:** keep a concise decision trail and change summary so “why did you move that?” or “what changed since yesterday?” has a factual answer. Clearly distinguish assistant suggestion, accepted draft change and actual saved task. Explain missing inputs without exposing revision IDs or internal parser terminology.
- **CQ8.E4 — Durable collaboration:** resume with a short relevant recap on request, preserve user-authored partial work, and reconcile outside edits. Keep a suggestion rejection history within the plan so discarded ideas are not repeatedly proposed. Identify completed/blocked work and a feasible next action without demanding a daily questionnaire.

These additions depend on CQ3–CQ8's applicable contracts. They are part of the implementation queue, not separate engines or promises of background autonomous work.

### GC1 — Everyday conversation and topic development

Depends on CQ1–CQ5. Handle greetings, sharing news, opinions, preferences, light reflection, curiosity and conversational closings. Respond to what was said before asking anything. A message may deserve a short acknowledgement, a relevant observation or an answer with no follow-up question. Distinguish friendly conversation from requests for coaching. Keep a bounded topic agenda so a tangent can develop and later return naturally.

Example: “I finally finished my project” should acknowledge the achievement and optionally connect to details supplied in this discussion, not list pending tasks. “I don't want advice” changes the dialogue policy immediately. Exit: several connected turns remain relevant without automatic planning or repeated “what would you like to do?” prompts.

### GC2 — Explanations and grounded knowledge conversation

Depends on GC1 and CQ6. Build a local concept/relation index with aliases, authored explanations, examples, prerequisites, contrasting concepts and scope/provenance. Deterministic lexical retrieval can locate candidates; retrieval score is not factual confidence. Compose answers from supported propositions and explicit reasoning steps. Support “why?”, “how?”, “give an example,” “simpler,” “more detail” and “how is that different?” against the actual prior answer.

Start with app capabilities, planning, study/learning, everyday organization and general travel preparation; expand content in bounded packages. Keep stable knowledge separate from user claims and unavailable current facts. Explanation depth must not exceed the authored relationships. Do not claim to summarize arbitrary articles or possess encyclopedic knowledge from a dictionary. Dedicated attribution remains accessible without repeated lookup footers.

### GC3 — Reasoning and collaborative exploration

Depends on CQ2, CQ7 and GC2. Support explicit premises, alternatives, pros/cons, constraints, counterexamples and conditional consequences. Provide a recommendation only when evidence supports it, with assumptions and a clear explanation of what could change it. Respect disagreement and update the relevant premise; don't flatter, insist, or silently reverse an answer.

Brainstorm by combining structured domain attributes and planning methods, filtering against user constraints and avoiding already rejected ideas. Identify proposed possibilities as ideas. Unknown consequences stay unknown; a causal claim requires an authored relation or user-supplied premise. “What if?” can remain discussion without creating a planning branch or task.

### GC4 — Listening, stance and personality continuity

Depends on GC1 and CQ5. Track explicit listen/explore/act preferences and the user's stated feelings within context; do not diagnose or infer hidden emotions. Use concrete acknowledgement grounded in their words, not repetitive praise or stock empathy. Build consistent warmth, respectful disagreement, restrained humour and comfortable short replies. Explain the assistant's limitations honestly without repeating an offline disclaimer every turn.

Personalization is explicit and inspectable: verbosity, tone, humour, question frequency and initiative. Session-only instructions override saved defaults for that conversation. Never pretend to have lived experiences, personal emotions or human relationships. The goal is an attentive recognizable voice, not a fictional biography.

### GC5 — Language robustness and graceful repair

Depends on CQ1–CQ4. Expand reusable normalization and grammar for contractions, missing apostrophes, common typing mistakes, ellipsis, references and coordinated clauses. Preserve literal names, quotations, numbers and dates. Use evidence-based disambiguation; never fuzzy-match a destructive action or silently guess a target.

Explain which part was understood and ask about only the uncertain part. Accept user corrections such as “I meant travelling for the exam” without discarding unrelated facts. After repeated failed repairs, offer a concise relevant alternative and let the user move on. Don't repeat a help wall or imply that the user must memorize commands. Additional languages/code-switching require separate explicit coverage; do not quietly claim support from typo handling.

### GC6 — Lightweight creative and playful conversation

Depends on GC1–GC2 and CQ5. Expand existing stories, games, quizzes and humour using authored structures, consistent characters/state and user-selected themes. Track what's already used and support continuation, shorter versions, changed tone and stopping. Constraint-based variation should produce coherent small outputs, not pretend to offer unrestricted novel writing. Keep games and creative mode from capturing unrelated task commands or resuming after the user has left them.

### GC7 — Explicit conversational memory and user teaching

Depends on CQ3 and GC1–GC5. Permit explicit saving of preferences, topic summaries and user-defined terminology/templates through existing encrypted memory controls. Recall only relevant scoped facts; support inspection, correction, forgetting and conflict resolution. A user definition is attributed user-provided knowledge, not a global factual correction or executable instruction. No silent training, background profiling, hidden transcript retention or Notes/vault access.

Resuming a discussion may restore its facts and open questions, never an expired permission to act. Clarify conflicting saved and current information before using it consequentially. Bound storage, retrieval cost and retention; forget must cover derived summaries/index entries as well as the original record.

### GC8 — Shared integration and bounded resource use

Depends on GC1–GC7. Keep chat/planning/help/creative modes interoperable through shared ownership, allowing interruption without destroying context. Register content and methods with a consistent schema, supported language, provenance, version and limits. Support optional diagnostics using synthetic inputs and structural routing reasons, without logging private text by default.

Bound parser candidates, graph traversal, response alternatives and history lookup so increasingly rich knowledge cannot freeze typing or responses. Move expensive work off the UI thread, support cancellation and reject stale results when input/state changes. Set content size and runtime budgets from available device measurements when needed; do not introduce a mandatory performance laboratory phase. Remove redundant routing rules through explicit ownership migration rather than leaving old and new handlers to compete.

### Delivery order and scope control

P0–P3 remain first, with the minimal CQ0 baseline. Then CQ1–CQ5 establish the shared foundation; deliver GC1 and GC4 as the first general-chat vertical slices. Extend CQ6 with GC2, CQ7 with GC3, and CQ8 with its E1–E4 additions. GC5 proceeds alongside the affected language batches. GC6 and GC7 follow their dependencies; GC8 integrates their registries and bounds. CQ9 finishes UI polish. CQ10 records the handoff and only indispensable checks; broader audits are deferred.

For each child batch record implemented scope and an example of unsupported input. Every proposal above must either be implemented or explicitly remain open; a new greeting pack cannot stand in for GC1, and a phrase alias cannot stand in for GC5. Do not start a new master roadmap when a missing abstraction is discovered: extend this one with a bounded child batch and preserve working behaviour.

## Ordered batch queue

The queue below is the target scope; per-batch implementation status belongs to the active record. Each ends with a usable vertical slice and a small handoff. Dependencies are explicit; do not treat this list as permission to mark aspirational capabilities implemented.

### CQ0 — Establish examples and ownership (minimal baseline before repairs, broader set afterward)

Dependency: none. Trace actual routing and persisted dialogue state. Record current responses for a compact development set spanning greeting, listening, casual chat, explanation, correction, interruption, comparison and planning. Use synthetic examples; collect no private conversations. Keep a separate unseen evaluation set for later assessment.

Deliver: ownership map, baseline transcripts and a failure taxonomy (wrong intent, wrong referent, lost constraint, repetition, premature advice, unsupported claim). Retain a tiny critical regression set; this is not a full audit. Exit: each observed failure has an owning component and expected user outcome.

### CQ1 — Shared interpretation and arbitration

Dependency: CQ0. Introduce typed candidate frames for question, assertion, preference, correction, hypothetical and explicit action. Preserve quoted titles and negation. Resolve mode, active question and explicit command precedence centrally while delegating execution to existing owners. Classify uncertainty without fabricated probability scores.

Journey: “I want to go away, but I'm not booking anything yet” starts discussion without creating tasks; an explicit task command still works during that discussion. Exit: supported conflict cases have one owner, unsupported input stays inert and useful clarification names the ambiguity. Necessary checks: routing, literal preservation and unintended-write regressions.

### CQ2 — Compositional meaning and references

Dependency: CQ1. Parse bounded clauses, conjunctions, contrast, alternatives and scoped corrections into reusable structures. Resolve “that one,” “the cheaper option,” “same time” and short answers against typed candidates; ask when multiple referents fit. Track asserted versus questioned/hypothetical facts. Stage compound edits before committing recognized changes; explain unrecognized clauses.

Journey: “Friday, not Saturday, and keep the train option” corrects only the date and preserves the choice. Exit: combinations work beyond memorized whole phrases; ambiguous references cannot silently target a saved item. Necessary checks: scoped negation/correction and multi-clause atomicity.

### CQ3 — Continuity and explicit memory

Dependency: CQ2. Add bounded topic frames and interruption/resumption with salient goals, unresolved questions, constraints and source statements. Separate transient topic history from explicitly saved memory. Define schema versioning, size/retention limits and inspect/forget controls before persisting new fields. Restore conversational context without replaying commands.

Journey: discuss a trip, ask a vocabulary question, then “back to the trip” resumes without asking the destination again. Exit: correction, restart, clear and forget behave predictably; old short answers cannot answer a new question. Necessary checks: reload compatibility, privacy, expiration and ownership.

### CQ4 — Dialogue policy that listens and asks well

Dependency: CQ3. Choose the next conversational move from user intent, stance, known facts and unresolved needs. Ask one high-value question at a time; accept “I don't know,” refusal, silence and topic changes without loops. Track answered questions and rejected suggestions. Avoid automatically converting feelings or greetings into tasks.

Journey: “I'm exhausted; I just want to talk” receives relevant acknowledgement and optional space to elaborate, not a checklist. Exit: responses use the user's actual details, stop unwanted advice and recover after two failed interpretations with a concise choice or supported alternative. Necessary checks only for state/ownership changes; inspect representative dialogue manually for wording.

### CQ5 — Consistent personality and response composition

Dependency: CQ4. Define a concise voice guide and compositional response renderer: grounded content first, tone and brevity second. Use recent response signatures to reduce repetitive openings, questions and advice. Respect existing tone/emoji/detail/quiet preferences. Humour must fit context and remain optional; empathy must not invent diagnoses or feelings.

Journey: equivalent information remains correct in warm, direct and playful styles; a serious conversation suppresses frivolous jokes. Exit: tone changes do not alter facts or action meaning, and repeated turns do not cycle through identical templates. Verify preference persistence only if changed; evaluate prose with a small transcript review rather than brittle exact-string tests.

### CQ6 — Deeper supported conversation domains

Dependency: CQ4–CQ5. Add structured, licensed/authored knowledge and conversation methods for travel preparation, study, projects, everyday decisions and routines. Each domain needs concepts, relations, follow-up questions, examples, trade-offs and explicit unknowns. Separate stable general guidance from live prices, weather, availability and other unavailable facts. Prefer connected explanations and analogies over isolated FAQ answers.

Deliver one domain at a time, beginning with travel and study. Journey: “why would that help?” explains the previous recommendation and its assumptions. Exit: known concepts support several follow-ups and unfamiliar topics get an honest useful boundary; sources/licenses are maintained outside repetitive chat footers. No runtime external lookup.

### CQ7 — Deliberation and richer planning handoff

Dependency: CQ2–CQ4 and first CQ6 domain. Extend current comparison with explicit trade-offs, optional user-provided ordinal priorities/weights, uncertainty and sensitivity explanations. Do not invent numerical preferences or compare incompatible units. Keep qualitative evidence distinct from computed scores. Allow “what if,” revisiting a decision and explaining why a recommendation changed.

Journey: “cheaper matters most, but I won't travel overnight” preserves the hard constraint and explains remaining options. Exit: missing facts remain visible; unsupported precision cannot determine a winner; planning handoff retains constraints and creates only a draft. Necessary checks: arithmetic, hard constraints, changed priorities and handoff authority.

### CQ8 — Collaborative planning over time

Dependency: CQ7. Enrich authored planning methods with goal decomposition, user skill/effort constraints, prerequisite questions and meaningful alternatives. Add progress-aware replanning of remaining work, with explanations of changed assumptions and affected sessions. Reuse existing canonical scheduler and native task graph; avoid parallel scheduling state. Handle missed work, changed availability and partial completion through conversation.

Journey: “I missed yesterday and only have half an hour today” proposes a feasible revision while preserving protected sessions and completed work. Exit: infeasibility is explained, saved changes use existing reviewed transactions, and unrelated work is untouched. Necessary checks: scheduler constraints, stale native edits, recurrence and undo when affected.

### CQ9 — Polished interaction and recovery

Dependency: CQ4–CQ8. Refine existing blue mode suggestions, compact actions and mode indicators around demonstrated conversation needs. Suggestions must be subtle, dismissible and keyboard-accessible; typed conversation remains sufficient. Preserve draft, cursor and keyboard-first Back. Respect reduced motion, text scaling and screen-reader labels.

Exit: no mandatory second input box, no surprise navigation/mode switches, and no loss of typed text. Use focused checks for changed Back/draft dispatch; a brief manual UI review for spacing/motion. Full OEM/device matrix stays in final acceptance rather than running every batch.

### CQ10 — Implementation handoff; broad audits deferred

Dependency: CQ0–CQ9 and the applicable GC/extension batches. Record implemented capabilities, known limits, checks actually run and deferred evaluation. Use a brief walkthrough where needed to establish the reported UI/conversation bug was fixed; do not create a large transcript evaluation project or a device/performance matrix now. Do not claim human quality, cross-device reliability or a numeric conversation score without evidence.

Only run additional tests when indispensable for a concrete data-loss, unintended-write, privacy, migration or scheduling-corruption risk, or to reproduce/verify a reported failure. Select the smallest affected case/suite and state why. Existing hosted CI remains intact; this instruction is not authorization to remove CI checks or delete distinct regression coverage. Version bump/push follows the active user instruction. Broad audits may be done later if something breaks or the user requests them; they do not block ordinary implementation completion.

## Expansion within every implementation batch

Reserve a discovery sub-batch after each vertical slice; this is how the plan broadens during implementation instead of freezing today's assumptions.

1. Collect failures from the implemented slice's realistic conversations. Identify the missing concept or policy, rather than adding one-off phrase exceptions.
2. Record a candidate extension with example dialogue, reusable abstraction, affected owners, uncertainty, storage/performance cost and minimal acceptance evidence.
3. Prioritize correctness/privacy/data-loss fixes first, then recurring context and comprehension failures, then breadth and stylistic improvements.
4. Include a bounded extension when it fits the batch's contracts. Give larger changes a `CQn.E1` child batch with dependencies and an explicit planned status; do not quietly expand unrelated components.
5. Re-run only affected necessary checks and update the handoff. Repeated exceptions or growing rule conflicts trigger a design revision before more content is added.

Potential later extensions: richer causal explanations, more domain methods, additional languages, more flexible constraint optimization and broader accessible interactions. These are candidates, not completed or guaranteed capabilities. Keep scope expandable without making delivery depend on “perfect general conversation.”

## Session handoff and testing discipline

For each batch record: status (planned/in progress/implemented/verified), user-visible outcome, changed owners, supported examples and counterexamples, checks actually run, known limitations, discovered child batches and the next dependency-ready slice. Update this queue instead of creating a competing master plan. Implementation and independent acceptance are separate statuses.

Use **absolutely necessary tests only**, per the latest user instruction. A category appearing in this plan does not automatically justify a test run. State the concrete failure or consequential risk, reuse the smallest relevant existing check and stop when resolved. Do not add tests for routine conversation content, personality, layout or low-impact reversible edits; inspect changes and compile only as needed. No routine full suites, exhaustive language matrices, mandatory benchmarks or broad audits during implementation. Remove a test only with an explicit equivalent retained coverage mapping; old tests are not redundant merely because the feature is complete. Broad audits are deferred; conduct them only when explicitly requested or needed for a concrete failure investigation. Existing hosted CI remains enabled.
