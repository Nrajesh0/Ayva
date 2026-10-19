# Offline assistant continuation: B3–B11

Continuation base: `1c8618e540d066366115b6f65fcaf39cbe6309d3` (the verified B2 push). Version remains 2.0.8 / 108. The user superseded the stop-after-B2 instruction and requested the remaining implementation. Consult [STATUS.md](STATUS.md) for current validation/publication state. This is bounded deterministic English conversation, not a language model or a promise of perfect understanding.

## Interpretation and context (B3/B4)

`AssistantInterpretation` supplies a common decision before the coordinator, router and legacy NLU can dispatch. Negation, hypothetical/reported/quoted examples and uncertain command typos cannot grant execution authority. Recognized commands remain explicit; unanchored action mentions ask for clarification. Typos in command words can suggest a correction but do not execute a fuzzy write. Explicit capture and quoted answers to live task/habit title fields preserve literal contents.

Existing domain parsers remain responsible for their grammar. This is an incremental shared arbitration layer, not a universal semantic parser. `/talk` retains app information/settings semantics and never implicitly creates tasks.

Legacy write classification also requires an anchored request; loose mentions of a routine starting, an app allowing something, or a report being done grant no write authority. Clearing history requires an exact chat-target request. An otherwise unrecognized dated message offers Add task / Ask Ayva instead of silently creating a task; clear imperative task captures such as `buy milk tomorrow` stay direct. Unknown app-help replies clear the implicit task target.

References and reviews have their own absolute 15-minute limits. Unrelated activity cannot refresh them. Persisted process identity invalidates confirmation tokens and pauses plan saves after process recreation. Stored lists and suspended questions can be inspected/resumed under their expiry/entity checks. A restored guided draft needs fresh review. Other active pending questions are cleared after process recreation; the user can restate them. Old confirmation authority is never restored.

Task and routine previews capture full records, including changes that don't advance timestamps. Reviewed task/routine writes recheck those snapshots inside their Room transaction. Multi-field task actions advance the expected snapshot using their own undo receipts, rather than adopting an intervening native edit. Settings also recheck the owner value when executing. An exception clears pending confirmation authority and implicit mutation references, including an earlier preview interrupted by a failed read. Oversized messages and unavailable quoted-reply targets explicitly revoke old task/plan controls too.

## Task conversations and guided planning (B5/B5P)

Extended task views combine supported status/priority/date/recurrence/undated/title filters and retain displayed identities. Examples:

- `Show overdue priority tasks`
- `Show priority unscheduled tasks about read`
- `Show recurring tasks this week`
- `Show tasks without a due date`

Existing list selection, saved-task receipts, reschedule cards, recurrence and undo behavior remain in place. Clock-only repairs preserve the date in the current preview or selected saved task. `Yes but at eight` asks AM/PM and invalidates the earlier confirmation; a qualified yes is not permission to apply that earlier preview.

Start a guided unsaved draft with `help me plan my trip`, `plan my exam` or `plan my day from scratch`. Existing quick `plan my day` continues to order saved tasks and suggest work blocks.

`Help me plan my trip to Goa next Friday` retains the supplied goal as context and asks for concrete steps and an explicit deadline; it does not infer bookings or treat the destination phrase as a task. `Goal: Visit Kochi!` changes that context, and `what are we planning?` recaps it. Goal text is included in saved task descriptions. Explicit `goal:`, `name:`, `steps:` and `topics:` fields preserve literal text. Unknown planning domains ask which supported planning flow to use. `Help me plan my day for 2 hours` orders real saved tasks within a tentative budget; it does not create a task from the request.

The guided draft contains stable item IDs, revision, domain, timestamps, timezone, supplied constraints, provenance and current question/review. Questions collect steps/topics, an optional shared deadline and optional whole-plan time budget. They have inline equivalents and a user-opened themed sheet. Closing the sheet never saves or discards the draft. Sheet answers and typed answers use the same coordinator; controls bind draft ID/revision and save authority. No sheet calls a DAO.

Useful edits:

- `Rename 2 to Arrange transport`, `remove 2`, `move 2 before 1`
- `Deadline: Friday at 6pm`, `deadline 2: tomorrow at 4pm`, `deadline 2: none`, `deadline 2: shared`
- `Budget: 60 minutes`, `effort: 90 minutes` (explicit total estimated effort)
- `Name: Goa trip preparation`, `one checklist`, `separate tasks`
- `Back to my plan`, `cancel planning`

Stated effort exceeding the available budget blocks save until revised. Unknown effort remains unknown. Deadlines are not scheduled work intervals or estimates of completion. Independently dated entries require separate tasks; checklist children share the parent's deadline and have no separate reminders. Dated work blocks, calendar availability, duration/dependency solving, nested checklists and arbitrary free-form replanning are not implemented.

Exam drafts use user-supplied topics. `Suggest study steps` optionally expands up to six topics into editable learn/practise/revise entries; it invents neither syllabus nor effort. Trip drafts optionally offer a generic preparation checklist. Every suggested entry is labelled optional and can be removed. There is no lookup or invention of current travel requirements, weather, prices or bookings.

### Save, recovery and undo

Only the current **Add tasks** control saves a plan. Its nonce binds the current revision and review; corrections, interruptions, expiry, process recreation and timezone changes require fresh review. A casual yes never saves a plan. A timezone change is shown on renewed review. Existing active duplicate titles block the save rather than overwriting the original.

`AssistantPlanStore` writes tasks and a `AssistantPlanReceipt` in one Room transaction. The receipt stores the draft/revision/concrete-task-payload hash and resulting task identities, so retrying after a commit/session failure returns the existing batch, including after fresh review increments its UI revision. The hash excludes generated row IDs/timestamps and review metadata; changed task contents still fail against an existing receipt. A simulated failure before receipt insertion rolls back the tasks. Receipt pruning only removes completed entries older than a day, beyond draft authority's lifetime.

Reminder/widget effects run after commit. Their pending marker remains until reconciliation succeeds. `Retry plan reminders` retries pending effects without creating tasks. Reconciliation skips rows changed since this save; it cannot resurrect, overwrite or schedule a newly changed row through an old snapshot. A successful scheduling request does not prove Android delivered a notification. The journal is task-only and never calls a Notes/all-data snapshot helper.

Plan undo uses the established guarded task receipts. Native edits prevent an overwrite. After saving, displayed task IDs are recorded in draft order, so `the second one` resolves to the saved plan's second task. Completing a checklist child does not complete its parent.

`AssistantChecklist` supports reading, adding, completing/unchecking, renaming and removing numbered checklist entries of one resolved parent. Every edit is reviewed, verifies original checklist contents and preserves stable IDs. Legacy children missing IDs get deterministic identities on read and persist them on their next edit. Duplicate child IDs require repair in the native editor.

Database migration: Focus database 12→13 adds only `assistant_plan_receipts`; the existing task/checklist schema stays intact.

## App controls, learning and settings (B6/B7/B8)

Existing full-inventory lock/unlock, categories, exceptions, routine editing, habit schedules/logging/undo and focus behavior were reused rather than recreated. New bulk reviews bind the visible installed inventory. Installing/removing/renaming an eligible app invalidates an old review. `Also leave Reader unlocked` and `only social apps instead` refine the current selection and preserve applicable exceptions. Picker layouts and WindowManager blocking are unchanged. Manual unlock cannot bypass an active strict routine/focus protection. Routine toggles now have a full-row guarded undo receipt.

`How did I do last time` reads the latest valid stored test result; it never invents scores or question timings. `Quiz me on saved words` filters native vocabulary practice to real bookmarks, including unlearned bookmarked words without falsely marking them learned. A dictionary's `quiz me on it` targets that saved OWS entry in the separate native test window. Native Drill/Blitz/vocabulary test UI is retained.

Native vocabulary quiz choices exclude the target and normalized duplicate distractors, including unlearned bookmarked targets. Questions without three distinct alternative answers are skipped with an honest empty-result state rather than becoming one-option automatic successes. Distractor pools are fetched once per quiz, and candidate questions are bounded before construction. This changes question data, not the test window's layout.

Settings read/change/help still use the 24 allowlisted actual preference schemas. Contextual `what does that do`, `what is it set to now`, `why didn't it work` and `change it back` use the current owner or matching guarded undo. A setting undo cannot undo an unrelated task. Permission diagnostics distinguish saved configuration, observed prerequisites and unknown Android/OEM delivery. Generic catalogue help adds guided-planning/dictionary topics and accurately describes on-device-only speech.

## Dictionary (B7D)

See [DICTIONARY_DATA.md](DICTIONARY_DATA.md) for the pinned source, notices, reproducible importer, measured data size and hardware limitations.

The dictionary supports exact word/phrase meanings, multiple stable source senses, source labels/examples where present, selected plain-English alternatives, inflection suggestions and contextual follow-ups. Existing idiom/OWS `look up` behavior is preserved; those native lookups now use indexed exact queries instead of loading every entry for a match. Unknown words remain unknown and never become tasks. Historical dictionary controls bind the selected word/sense and cannot save whichever unrelated word happens to be current.

`Save this word` previews a source-sense bookmark. Its atomic vocabulary transaction links the source sense to an existing matching OWS row or creates one; it doesn't mark the word learned. Undo removes the bookmark, preserving the imported library entry for reuse and its source linkage. The new vocabulary 2→3 migration adds only a source-link table, retaining the original 1,773 idioms and 2,027 OWS entries. The bundled v1 database migrates through both existing/new migrations on fresh install.

Whole-sentence explanations support explicitly named patterns: `I would [not] have gone had I known`, `I used to …`, `I am used to …` and `I am [not] feeling under the weather`. Negation and past modality remain explicit. Unsupported constructions, sarcasm and arbitrary sentence paraphrasing are not claimed. Dictionary definitions/examples are inert text, including words such as delete, lock and complete.

## Conversation and preferences (B9/B10)

`AssistantSocial` contains finite conversation packs for check-ins, good/bad news, light banter, boredom, empathy/listening, eight rotating jokes with explanations, four riddles, a bounded word-chain game, two branching story openings and six hobby/interest topics. Follow-ups resolve within the active pack. Game/story controls bind the current conversation frame; old controls cannot resume an unrelated branch or authorize a mutation. Games end, can be stopped, and never grant productivity rewards.

`Just listen`, `no advice` and `be serious` affect the current exchange, without saving an emotional profile or immediately forcing planning. A casual exam/trip disclosure offers planning separately; only an explicit choice/request enters it. Past-tense `I finally finished it` after general conversation asks what was finished rather than completing an old task. Existing task-aware greeting content remains; a new greeting clears old mutation/reference authority. General conversation is bounded, can be repetitive outside the authored branches, and doesn't provide arbitrary knowledge or human-level understanding.

Durable tone, quiet conversation and task-suggestion settings require explicit allowlisted preference requests:

- `Remember that I prefer a direct tone` (warm/playful/direct)
- `Remember quiet conversation`
- `Remember task suggestions` / `remember no suggestions`
- `What do you remember about me`, `forget my reply preferences`, `forget my preferences`

Suggestions are off by default, appear only in chat, use actual saved task facts, and have a 30-minute cooldown. Dismissal quiets them for a day. They never change a task or send a notification. Quiet conversation suppresses them. Forgetting preferences clears the suggestion receipt/context without deleting tasks, habits or private data.

## Validation and remaining gates (B11)

Use the exact runs/artifacts in STATUS.md. Relevant regression groups cover interpretation boundaries, Notes sentinels/query monitoring, source-backed dictionary lookup/credits/bookmarks/native quiz filtering, atomic plan rollback/retry, stale controls, process/expiry, native edits, scope changes, task/date corrections, settings owners/undo and protected existing flows. Test counts are evidence of executed coverage, not a quality score or proof for arbitrary language.

No physical Android device is connected in this environment. P6/B11 physical UI, large text/TalkBack, rotation/keyboard, real speech availability, airplane mode, OEM overlay/reminder delivery, device latency/memory/battery and representative-device dictionary budgets remain unverified. There has been no independent human conversational-quality review or genuinely held-out human-authored language evaluation. These gates remain open; do not label the complete roadmap fully accepted from local tests alone. [DEVICE_ACCEPTANCE.md](DEVICE_ACCEPTANCE.md) provides the concrete checklist.
