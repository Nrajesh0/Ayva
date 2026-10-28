# Recommended stack after inspecting Run's current chat code

> Superseded in implementation scope by [the general-chat-only review](GENERAL_CHAT_REVIEW_2026-10-25.md). The user wants task planning preserved. The revised recommendation defers shared parser/date/state-machine replacement and evaluates an isolated conversation runtime instead.

This supplements the 45-project research report. Licensing is set aside for this development-stage technical recommendation. No dependencies or production code were changed.

## Decision

Use **ChatScript for authored general dialogue**, **KStateMachine for explicit operational dialogue states**, **better-parse for native command/edit grammar**, selected **Recognizers-Text Java modules for typed entities**, and a **curated conversation corpus indexed through the existing Room stack**. Evaluate a JVM SymSpell component for conservative command normalization. Keep the existing native task/habit action executors, plan commits, snapshots, undo, and encrypted storage.

This is an integration target, not a claim that the libraries already build on Android or that import alone fixes the conversation. In particular, ChatScript requires an English-only restricted native/JNI packaging experiment, and Java recognizer coverage/Kotlin artifact compatibility must be checked. If that experiment fails its Android/content-value gates, use RiveScript Java as the lower-complexity alternative; do not run both engines.

## Current-code evidence

- `BubbleChatActivity.kt`: calls `OfflineAssistant.respond`; unhandled/redirected turns then pass through `AyvaIntentRouter`, which can choose conflict cards, task creation or a Talk query. The UI is therefore involved in a second interpretation stage. This is not inherently a failure for every message, but it increases the number of places where precedence must agree.
- `OfflineAssistant.kt`: 1,399 lines at inspection, coordinating explicit controls, calendar capture, planner, general/social conversation, native features, legacy NLU and fallback. `GeneralConversation.answer` is consulted in multiple phases. Do not add another independent early-return interpreter in the middle of this chain.
- `ConversationMeaning.kt`: 39 lines, primarily prefix-based speech-act classification and bounded clause splitting. `AssistantInterpretation`, task NLU, plan language and feature-specific parsers also classify text.
- `GeneralConversation.kt`: general frames retain topic/notes/stance, but a sharing branch echoes user text and chooses among three generic questions using note count modulo three. Existing follow-up/context facilities are useful but not broad language understanding.
- `ConversationKnowledge.kt`: 23 authored concepts in this particular index, not 23 facts across the whole app. Dictionary, app help and other knowledge live elsewhere. Adding only more variants cannot produce a broad topical response library.
- `AssistantSession.kt`: existing references, pending actions, guards, suspended requests and history should be preserved. The session stores the last six raw recent inputs; general topic notes and planning drafts use separate structures.
- `GeneralConversation.current` uses `AssistantContext.live`; that helper uses a 15-minute window also used by action references. Stored content may remain, but live conversational eligibility and action authority need distinct policies. Never extend stale confirmation authority merely to remember a topic longer.
- `SmartDateParser`, `TripDate`, `PlanLanguage.date`, temporal helpers and task-date repair already handle dates in different contexts. A common typed entity boundary is needed; simply replacing one parser leaves divergent policy elsewhere.
- `AssistantActionExecutor` and plan storage already provide valuable guarded execution and undo. Preserve them as the only write owners.

## What each component owns

| Component | Owns | Does not own |
|---|---|---|
| Run turn coordinator | One selected interpretation, mode policy, pending question, active entities and execution decision | Independent competing fallbacks |
| KStateMachine | Guarded operational states/transitions, interruption/resumption events | Text understanding, a duplicate database, or broader execution authority |
| better-parse grammar | Typed requests, queries, corrections, compound syntax | Arbitrary English semantics or world knowledge |
| Java recognizer adapter | Dates/times/numbers/units, spans and explicit/inferred values | Deciding which task or date role an extracted value changes |
| ChatScript | General dialogue rules, concept families, topic/rejoinder behavior and context-conditioned responses | Direct DB writes, arbitrary callbacks, stale native references or invented task success |
| Corpus + Room retrieval | Reviewed content blocks, aliases, topical support and source metadata | Unrestricted factual truth or automatically appropriate replies |
| Existing native executors | Task/habit mutations, planner validation, version checks, receipts and undo | General-chat generation |

The authoritative task/plan state stays in Run. A conversation adapter receives a bounded projection and returns reply acts or typed proposals; it does not keep a competing editable task list. Any engine-private conversation state should be versioned and persisted through one coordinator, not an unrelated user-memory store.

## Why ChatScript over RiveScript for the primary direction

Run already implements many trigger/response and prefix rules. RiveScript would make authoring more convenient, but a wholesale move could largely repackage the same limitations. ChatScript adds a more substantial alternative: concept sets, native English rule parsing, topic rules, rejoinders and fact-oriented discussion. That is more aligned with the requested breadth, provided we actually author useful content and integrate it correctly.

Native integration cost is real, so its adoption requires demonstrated benefit on the same complete conversations as the current system. A successful JNI build alone is not enough. Keep the richer engine out of ordinary task edits and tight native feature paths.

## What to reuse as data

Start with selected English ChatterBot corpus files as topic/example material. Convert reviewed content into Run's own topic rules and personality. Rosie can be used as an additional development content candidate when licensing is outside this decision, but it is not automatically better: AIML/Pandorabots assumptions, another persona and a separate interpreter format still create technical/editorial costs. Do not add an AIML runtime just to consume one corpus.

Use Taskmaster/SGD/MultiWOZ to discover realistic dialogue structures and later acceptance scenarios. They are not production response brains. Extend the existing Wordset/WordNet reuse for lexical concepts; optional ConceptNet subsets may broaden associations later.

Use ChatScript's own conditioned response variation or a small native realization component. Tracery is a useful design reference, but a separate JS runtime for phrasing is unnecessary if ChatScript is already integrated.

## Implementation order

1. Establish one turn coordinator and typed event/frame contracts; migrate existing owners incrementally. Preserve Chat's dated-task priority, Talk discussion, Plan drafts and explicit controls.
2. Prove ChatScript's restricted English native integration and a small useful Run dialogue pack while leaving existing native actions intact. Stop/choose RiveScript if it cannot justify the cost.
3. Move native edit/query syntax into a composable grammar; unify entity extraction and keep a single date-policy/reference-resolution layer. Initially adopt recognizers only where Java coverage is verified; retire duplicate paths as coverage moves, rather than keeping permanent parallel decisions.
4. Expand content and context-conditioned replies, with concise Run personality, repetition tracking, scoped recall and grounded references. Separate tentative ideas from committed changes.
5. Add conservative SymSpell normalization and optional lexical graph/knowledge expansion. Consider Choco only if actual scheduling constraints outgrow the existing planner; it will not fix conversation.

Do not import Rasa/Botpress, a Python bot platform, multiple dialogue engines, a broad trained NLP pipeline, or a large search/solver stack as the first change. Their added complexity does not address the observed causes.

## Acceptance examples

A small necessary set of complete journeys should cover: ordinary sharing followed by an on-topic question; a relevant short follow-up; a correction; reopening a topic later without reviving write permission; date in an opening exam/trip message; date-role correction; tentative versus confirmed additions; summary/removal/undo; habit pause/resume; and restarting with the draft intact. Compare current and proposed stacks using the same turns. Measure Android package size, cold start, per-turn time, memory and persistence behavior before choosing the final runtime. None of those runtime measurements has been executed for this recommendation.
