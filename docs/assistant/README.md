# Offline assistant

Ayva uses bounded Kotlin rules, typed state and authored content. It does not
download an inference model or call a hosted language model. General and focused
chat are supported experiences, not unrestricted natural-language understanding.
Current source and the capability registry take precedence over former batch plans.

## Supported areas and boundaries

| Area | Implemented behavior | Boundary |
| --- | --- | --- |
| Tasks and checklists | Read saved tasks, create/edit, dates, recurrence, completion, guarded trash/undo, checklist editing and progress | Preserve row identity, current versions and explicit command intent; do not turn every noun into a task |
| Guided plans | Discuss a supported goal, clarify constraints, draft/revise, review and save; day/exam/trip journeys, native task adoption, hierarchy and work sessions | Drafts and discussion do not authorize native writes; budgets, dates, conflicts and missing information stay visible |
| Habits | Create/edit goals and schedules, record progress, pause/resume and archive/restore | Same-day guarded progress/reward rules and paused-state protections remain |
| App rules and routines | Eligible installed apps/categories, reviewed rules, schedule changes and enable/disable | Manual unlock does not remove an active strict routine or override focus protection; package identity is authoritative |
| Focus | Explain actual state and start/stop supported countdowns | Do not replace a running session or change app rules implicitly |
| Learning | Native idioms/OWS, bookmarks, supported sentence patterns, Drill/Blitz/practice navigation and actual results | Native tests stay in their own windows; no invented definitions, scores, elapsed times or results |
| Settings | Actual values, owner/default/type/range help, validated previews and guarded changes/undo | Unknown keys, invalid types and stale values are rejected; help text is not write authority |
| Usage and permissions | Read supported OS state and explain missing prerequisites | Missing data is unknown/unavailable, not zero or proof that a notification/alarm was delivered |
| Conversation | Authored discussion, listen/tone controls, comparisons, stories/games and repair/continuity | Language and factual coverage are bounded; no claimed ChatGPT-equivalent flexibility or numerical quality score |
| Personalization | Explicit supported preferences/memory, inspection and scoped forget | No hidden personal profile inferred from private notes or external data |
| Speech | Existing capture UI, partial/final transcript and reviewed task extraction | Partial captions cannot execute actions; recognizer availability and speech privacy are separate concerns |
| Backups | Generic native navigation and allowed recorded status | No chat inspection, export, restore, contents/counts access or recovery-key handling |
| Private notes/vault | Generic packaged help and native-screen navigation | No titles, counts, metadata, attachment access, CRUD, indexing or consent bypass |

`util/assistant/AssistantCapabilities.kt` and `AssistantPreferences.kt` describe
capabilities and the allowlisted settings schema. `/talk capabilities` and its
domain forms expose informational help; displayed examples are not executed.
Domain owners still enforce permissions, strict mode, current rows and side effects.

## Authority and interaction contracts

- Matching help or choosing a clarification does not bypass review policy.
  Legacy action chips normalize to reviewed query requests; preview-only paths
  cannot directly write preferences or call the executor.
- Pending confirmations bind to the current operation and expected data. Old
  buttons, a stale short answer or a response after a topic shift must not save
  an earlier proposal. Unsupported input should clarify or report its limit.
- Android Back/IME handling, sheet dismissal, rotation and process recreation
  preserve drafts and distinguish leaving a screen from committing a write.
  A compact chat composer is not a replacement for native editors/test windows.
- Native app/catalog filtering preserves protected system/launcher/keyboard/phone
  entries, duplicate-label identity and locale-aware presentation. Revalidate a
  review when installed apps or saved rules change.

## Planning and discussion persistence

Workspaces, selected references, discussions and explicit preferences have bounded,
versioned storage and participate in portable backup and restore recovery. A
workspace ID/revision and scoped question identity prevent stale answer replay.
Corrupt/unsupported state is not permission to silently execute or discard tasks.

Task deadlines and work intervals/sessions are distinct. Deadlines alone do not
reserve time. Native `planningJson` stores hierarchy, prerequisites, duration,
work sessions and reminder choices. Saved work remains reserved when a workspace
is discarded; unsaved draft proposals remain draft reservations. Completing,
trashing or deleting tasks changes occupancy without cascading deletion to children.

Reviewed saves allocate task IDs and graph edges in a transaction, compare full
expected rows and record a durable receipt. Retrying after commit reuses that
receipt; reminder/widget effects can be reconciled instead of creating another
batch. Failed effects do not imply that task insertion failed. Undo/reconciliation
must preserve outside native edits. Imported references are remapped, not rebound
to coincident local IDs; malformed/cyclic known graphs are rejected before writes.

Native edits and refreshing saved tasks invalidate a stale review. Missing outside
references require explicit reconciliation; making an unlinked draft copy leaves
the original task intact. Recurring occurrences do not inherit another occurrence's
graph identity or work reservation. Task/widget Today/Upcoming classification uses
the earliest explicit work/deadline commitment and a calendar-derived day boundary.

Comparison reasoning separates facts supplied by the user, unknowns, hard
requirements and soft preferences. Unknown costs/durations/qualities remain unknown;
no unsupported option is ranked as a measured optimum. Named evidence, scoped
questions and explicit choices can inform a plan but cannot create tasks until
review. Contradictions, corrections, numeric ranges and clearing a requirement
must update the typed state, not merely acknowledge the change in prose.

## Automatic chat updates

The three-dot menu is **Chat updates & colors**. A master pause and seven
categories control automatic briefings, practice/streak nudges, screen-time and
bedtime reminders, focus previews, quest/reward prompts and other automatic cards.
Disabled streams reject future automatic cards before persistence and badge
increments as well as suppressing previews. Explicit replies, requested cards,
earned rewards and completed drill results remain available; existing history is
retained. Task/habit reminders have their own Settings controls. Queued producers
check chat generation so replaced history cannot receive an old producer's result.

## Acceptance and limits

Use the existing synthetic privacy/query-monitoring, stale action, plan-receipt,
undo, persistence and routing regressions for relevant changes. The chat boundary
guard covers assistant/router/speech/UI paths and the extracted shared models;
it is a narrow source contract, not proof of all transitive behavior.

Device acceptance must record device/OS, actual commit/APK hash and results:

1. Cold offline launch and supported vocabulary/sentence-pattern requests, with
   honest unknown responses and no model/dictionary download.
2. Day/exam/trip drafts, corrections, stale buttons/topic interruptions, native
   outside edits, graph conflicts, DST/timezone changes and process-kill retries.
3. IME/Back/sheets, rotation, large text, TalkBack, light/dark/accent themes and
   retained native Drill/test windows.
4. Eligible app previews, install/uninstall races, strict routines/focus and direct
   WindowManager blocking on supported modern Android/OEM devices.
5. Actual speech-service/language availability, partial/final capture and lifecycle;
   actual reminder/notification delivery under platform and battery restrictions.
6. Independently worded conversations for relevance, repetition and misunderstanding.
   Scripted developer fixtures are not held-out human quality evaluation.

Use synthetic content; do not include private Notes in bug reports. Local tests
and previous emulator runs do not certify the current working tree on every OEM.
See [open findings](../security/OPEN_FINDINGS.md), [speech boundaries](../features/system/RELIABILITY_AND_PERMISSIONS.md#speech-lifecycle-and-privacy),
and [vocabulary/attribution](DICTIONARY_DATA.md).
