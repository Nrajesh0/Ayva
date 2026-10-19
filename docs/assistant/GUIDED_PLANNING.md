# Guided planning: research findings and implementation specification

Original implementation specification, inspected 2026-10-18. A bounded draft/question/review/save flow is now implemented; see [OFFLINE_ASSISTANT_IMPLEMENTATION.md](OFFLINE_ASSISTANT_IMPLEMENTATION.md) and [STATUS.md](STATUS.md). P6 physical acceptance and the advanced constraints explicitly listed as unsupported remain open. The findings table below describes the planning baseline. Required batch B5P in [MASTER_PLAN.md](MASTER_PLAN.md), after B5 and before B6. This document defines a bounded offline planning assistant, not unrestricted natural-language reasoning. The quality goal is correct, understandable and recoverable behavior; no claim of perfect understanding.

## Findings from the current code

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

## Research scope and external guidance

This specification is grounded in repository inspection, existing state/confirmation patterns and the following design principles: explicit mode boundaries, progressive disclosure, targeted clarification, editable previews, conservative execution and recovery after interruption.

External reference requests were attempted but blocked by the environment's network proxy (HTTP 403), so their current contents were not reviewed. Revisit these primary references during P1 when access is available; do not describe this document as an externally validated literature review:
- [Google conversation design: confirmations](https://developers.google.com/assistant/conversation-design/confirmations)
- [Android Compose dialogs](https://developer.android.com/develop/ui/compose/components/dialog)
- [Android UI state preservation](https://developer.android.com/topic/libraries/architecture/saving-states)

No model or online service is proposed. Future implementation must verify Compose lifecycle/accessibility details against the project's installed library versions and test actual behavior.

## Interaction contract: offer, clarify, draft, review, save

### Entering planning

An explicit “help me plan my day/exams/trip” starts a planning draft without a redundant permission popup. An inferred topic such as “I have exams next month and I'm behind” may offer a compact, dismissible planning prompt: “Would a study plan help?” with **Plan together**, **Just chat**, and dismiss. An ambiguous standalone phrase gets a clarification rather than creating tasks. Do not repeatedly offer planning after dismissal or trigger it from a single keyword.

Planning is a scoped conversation state, visible through a small “Planning · Exam preparation” status and an accessible review/exit action. It is not a permanent global mode or a replacement for `/talk`. Explicit planning can start during `/talk` with a visible scope transition; afterwards restore the prior app-help mode. A casual aside must never toggle modes invisibly.

### Questions and popups

Use a compact themed bottom sheet for a structured planning question and a small dialog for a single consequential choice. Keep ordinary low-impact follow-ups inline. These are new planning components only, not a chat redesign. Do not stack modal windows or reopen a dismissed one on recomposition.

Each question has:
- One specific question, the current relevant value and a short reason if useful.
- Two or three sensible options when applicable, plus a free-text/custom answer.
- “Not sure” or “Skip for now” for optional information; optional fields cannot block a useful unscheduled checklist.
- “Back/change an earlier answer” and “Cancel planning.” Closing a question never saves anything.
- An inline transcript equivalent so the user can answer by typing, revisit the reasoning and use accessibility tools.

Ask only what is needed for the requested output, one question at a time by default. Dates and available time are required for a timed schedule, not for brainstorming a checklist. Collect answers already supplied in a sentence and do not ask them again. When parsing fails, explain the field's expected format and retain other answers. Offer a manual picker/field after repeated failures rather than trapping the user in a language loop.

Use theme semantic colors, readable contrast, adequate touch targets, keyboard/inset handling, large text and TalkBack labels. Android Back dismisses the current surface without discarding the draft; cancelling explicitly discards it. Retain entered answers through rotation using the state owner, not only composable-local state. No question UI can call a DAO directly.

### Drafting and corrections

Show the understood goal, constraints, proposed tasks/checklists and unresolved assumptions before saving. Each suggested step is marked as user-requested, existing-task-based or an optional template suggestion. Let users remove/reorder entries or change dates/effort. Keep provenance internal unless it helps explain a proposal.

“Actually Friday, not Thursday” changes the referenced field and rechecks dependent dates. If there are two plausible Friday fields, ask which. “Only 30 minutes”, “skip packing”, “make that a checklist instead”, “keep the booking task” and “don't change the existing one” produce targeted draft edits. Explain material changes concisely; do not regenerate the entire plan and silently discard accepted edits.

A plan can remain unscheduled when details are unknown. Do not invent flight dates, exam subjects, chapter lists, bookings, travel durations or free calendar slots. An impossible schedule produces a conflict question with options to reduce scope, change availability/deadline or keep an unscheduled backlog; never claim everything fits.

### Review and save

Provide a distinct review sheet with a summary such as “Create 4 tasks with 6 checklist items; update 1 existing task.” Show the actual titles, dates, timezone where relevant, selected entries, changes and conflicts. The primary button describes the action, e.g. **Add 4 tasks**; **Keep editing** is always available. Reviewing, selecting or dismissing has no task side effects.

A save token binds the draft ID/revision, selected draft item IDs, expected current task versions and concrete action payload. Any correction invalidates the old token. Saving revalidates all targets and constraints. Changed external tasks trigger a refreshed comparison rather than an overwrite. “Yes” answers only the active scoped question/offer; it cannot confirm a suspended save after an unrelated joke or dictionary question.

Task-only plan writes should commit atomically in a Room transaction, with a persisted idempotency receipt in the same transaction. Schedule reminders/update widgets after commit through recoverable, idempotent domain effects. Test crash windows explicitly; if a side effect fails, report that status rather than claiming notifications are set. Audit existing executor semantics before choosing a minimal compatible implementation. Do not use note-containing backup paths: B1 is a prerequisite.

Guarded plan undo reverses only this plan's unchanged writes. Define and test recurrence/reward behavior and recovery-snapshot requirements. Never roll back a later manual edit silently. A saved plan receipt maps draft items to actual task/subtask IDs; follow-up “move the second one” uses that mapping, not a newly sorted database list.

## Structured state and domain rules

Introduce a versioned `PlanningDraft` (names illustrative) containing:
- ID/revision, domain (day/exam/trip), lifecycle stage and timestamps/expiry.
- Goal and supplied constraints, with explicit vs assumed status.
- Required/optional missing fields and the exact active question ID.
- Stable draft item IDs, selected entries, references/versions for existing tasks, dependencies and optional proposed timeboxes.
- Concrete dates with timezone, user-supplied duration estimates distinct from suggested work blocks.
- Validation conflicts, suspended topic and read-only saved receipt mapping.

Bound draft size, nesting, parser work and suspended contexts. Store only allowed planning data in encrypted assistant state; no Notes access, automatic clipboard reading or external calendar access. Use user-entered fixed commitments and supported task data. Keep existing short expiry initially; if longer draft retention is wanted, implement an explicit save-draft capability with inspection/deletion and its own policy. Do not silently persist travel details or emotional disclosures as personality memory.

State transitions: `offered → collecting → drafting → reviewing → saving → saved`; `paused`, `cancelled`, `expired` and `needs-repair` are explicit alternatives. Only a validated current review can transition to saving. Process recreation may restore a valid draft, but requires renewed review before saving; it must not replay old buttons. Every popup answer carries question ID and draft revision, rejecting stale callbacks.

### Day planning

Use existing tasks, stated available windows, priorities and user-entered commitments. Use a deterministic ordering rule with stable ties. Allocate suggested work blocks without pretending to estimate completion. Distinguish due date from planned work time: the current task model has a due date but no separate scheduled work interval. Keep work blocks in the draft unless a user explicitly approves due-date changes; a future scheduling field needs its own migration/design.

### Exam preparation

Collect subjects/topics from the user and exam dates when scheduling. Ask for available study windows and optional difficulty/remaining effort. Reviewed templates may suggest learning, practice and revision blocks, but must not invent syllabus content or predict mastery. Respect dependencies and actual daily capacity, allow missed-session replanning, and leave unknown effort clearly unestimated.

### Trip preparation

Support preparation checklists and deadlines using supplied departure/return dates and selected travel type. Template suggestions such as packing, checking documents or arranging transport remain optional. Avoid inferring destination rules, visa eligibility, weather, live prices, bookings or routes. When current requirements matter, say they need independent checking; no online lookup or invented authoritative travel advice.

### Checklist subtasks versus scheduled tasks

Use existing `Subtask` for small checklist steps that share a parent and need no independent reminder. Use separate `Task` records for independently scheduled actions. Present that choice in plain language when ambiguous: “One checklist, or separate reminders?” New typed subtask edits must validate parent snapshot, stable child IDs, title/size limits and completion semantics. Audit native rendering, serialization, undo, backup/restore and recurrence cloning without changing protected card appearance. In particular, verify legacy subtask records missing IDs get stable identities before permitting conversational targeting; the current JSON reader can generate an ID while reading.

Do not assume completing a parent completes its children or vice versa. Preserve existing product behavior and expose any needed policy decision explicitly in the implementation handoff. No nested subtasks or independently timed children in the initial release.

## Sequential implementation slices and acceptance gates

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
