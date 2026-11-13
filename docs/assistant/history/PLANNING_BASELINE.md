# Historical assistant planning baseline

These original roadmaps preserve design decisions and acceptance contracts.
They are historical specifications, not instructions to repeat completed work.
Use [assistant status](../STATUS.md) and the
[conversation-quality plan](../CONVERSATION_QUALITY_PLAN.md) to interpret current
scope; an aspiration in an old plan is not evidence of implementation.

## Contents

- [Ayva offline personal assistant: sequential development plan](#sequential-batch-plan)
- [Ayva: conversational planning without models](#conversational-planner-roadmap)

<a id="sequential-batch-plan"></a>

<a id="sequential-batch-plan-ayva-offline-personal-assistant-sequential-development-plan"></a>

## Ayva offline personal assistant: sequential development plan

> Historical specification — B0–B11 bounded implementation complete; independent acceptance gaps remain explicit. Superseded as the active queue by [the conversation-quality plan](../CONVERSATION_QUALITY_PLAN.md). See [assistant status](../STATUS.md).

Planning baseline: 2026-10-18. This is the original specification; implementation/acceptance status is tracked in [STATUS.md](../STATUS.md). See [OFFLINE_ASSISTANT_IMPLEMENTATION.md](FOUNDATION_DELIVERY.md#b3-b11-continuation) for the current bounded implementation and explicit remaining gates. A proposed feature below is not automatically implemented or accepted.

<a id="sequential-batch-plan-product-goal-and-limits"></a>

### Product goal and limits

Build a dependable, personable English assistant for Ayva's actual tasks, habits, routines, app restrictions, focus, learning, settings, dictionary lookup and help, alongside enjoyable everyday conversation. Everything in the conversation engine runs locally using rules, structured state, packaged knowledge and reviewed response templates. No downloaded or bundled language model, embeddings service, remote inference, external search, telemetry of conversations or runtime model training.

AI can help write and review code, language examples and conversation content during development. The shipped app executes ordinary deterministic code. This can produce rich, useful conversation within supported domains; it cannot honestly promise unrestricted human-level conversation, arbitrary reasoning or current world knowledge. On unfamiliar requests, explain the limit and offer a relevant supported next step without creating an unintended task.

Prioritize correct understanding and useful follow-ups over the number of canned answers. First establish boundaries and a common engine, then deepen capabilities, then personality. Keep changes incremental and preserve working behavior.

<a id="sequential-batch-plan-non-negotiable-boundaries"></a>

### Non-negotiable boundaries

- Chat never reads, searches, summarizes, creates, edits, deletes or indexes Notes/vault data, including titles, counts, previews, attachments and private metadata. No consent phrase or prompt can bypass this. No note database/repository, export, restore or backup-content capability in the chat domain.
- Generic bundled help about Notes and explicit navigation to its native screen are acceptable; they must not read its data or trigger an operation there. A task such as “remind me to review my notes” remains a task. Text users type into chat is chat input, never a reason to fetch private storage.
- Audit indirect access too: shared task recovery, transcript helpers, diagnostics, logs, caches, backup status and legacy handlers. Keep native Notes functionality and independent app backup protections intact.
- Preserve task creation/rescheduling receipt cards, existing cats, composer/slash access, briefs, native Drill/Blitz/vocabulary windows and approved response controls. No general UI redesign. App picker improvements concern sorting/filtering only.
- Preserve WindowManager blocking and existing strict routine/focus protections; follow AGENTS.md.
- `/talk` is app information/settings conversation. It must not implicitly create tasks. Ordinary messages retain natural task creation. Explicit task commands use the existing task path. Information, quoted examples, hypotheticals, negation and small talk must not accidentally become mutations.
- Every UI chip and text command goes through the same validated action path. Replies report actual outcomes, distinguish draft from saved state and never claim an overlay/reminder was delivered merely because it was configured.
- Maintain confirmations for bulk/destructive/settings changes, stale-state checks, scope snapshots and guarded undo. Clear low-risk task creation can keep its existing direct-save behavior; do not add confirmation to every sentence.

<a id="sequential-batch-plan-current-foundation-and-observed-gaps"></a>

### Current foundation and observed gaps

The current working tree already contains typed actions, encrypted expiring sessions, task references and lists, clarification and repair, three suspended questions, actual settings knowledge, habits/routines, app groups and exceptions, learning and focus context, and explicit allowlisted preferences. Reuse them.

Primary entry points are `OfflineAssistant.kt`, `AssistantActionExecutor.kt`, `AssistantSession.kt`, `AssistantPreferences.kt`, `AssistantAppKnowledge.kt`, domain helpers, `AyvaIntentRouter.kt` and the legacy `AyvaTalkEngine.kt`. Several overlapping parsing/routing layers and domain-specific state handling make consistency harder. Introduce common contracts gradually rather than rewriting everything.

Baseline privacy finding: `AssistantActionExecutor.execute` used to open `NoteDatabase` for `TrashTasks` and pass its DAO to an all-data snapshot helper. B1 replaces this indirect read with selected-task recovery; see [B0_B1_IMPLEMENTATION.md](FOUNDATION_DELIVERY.md#b0-b1-foundation) and STATUS.md for evidence and remaining verification. Preserve that boundary while expanding capabilities.

At planning baseline, speech used Android's on-device recognizer in `AudioMemoManager` alongside saved-media playback. B1 extracts `util/speech/OnDeviceTranscriber` for chat, while the native audio manager delegates transcription and retains its media functions separately. Voice may use an already installed OS speech service; it must never fall back to cloud or trigger a model download. Text must work with no speech service installed. Do not promise offline dictation on every device.

Existing tests and build evidence are recorded in STATUS.md. Older capability documents describe earlier batches and may be stale; code, current tests and the new acceptance criteria take precedence.

<a id="sequential-batch-plan-target-architecture"></a>

### Target architecture

`Input → normalize/tokenize → intent candidates → mode/context resolution → dialogue policy → read capability or validated action → actual result → response composition`

1. **Capability registry:** Each supported operation declares aliases, required fields, read/write classification, allowed contexts, typed inputs, validators, risk/confirmation policy, side effects, undo support and help examples. Unsupported operations have truthful explanations/native navigation. Settings use the real preference schema, never a separately invented list.
2. **Language interpretation:** Preserve quoted titles and original text. Normalize contractions and polite wrappers; handle negation, dates, clauses, exceptions and corrections explicitly. Rank candidates using explainable rule evidence, not invented statistical confidence. Ties involving a mutation require a focused clarification. Fuzzy matching may suggest an entity; it must not silently select a different app for a write.
3. **Dialogue state:** Typed domain frames hold selected stable IDs, displayed ordering, missing fields, draft changes, source/time/version, suspended question and expiry. Keep current confirmation tokens single-use. Distinguish saved facts, user-provided preferences, temporary assumptions and unsaved drafts. Never restore confirmation authority after expiry/restart merely by replaying text.
4. **Narrow data interfaces:** Inject only allowed feature readers/writers. No general SQL/filesystem/network tools or full application database in the conversational layer. Android adapters perform validated domain operations. Enforce Notes exclusions with dependency checks and behavioral tests; this is an application boundary, not an OS sandbox claim.
5. **Response composer:** Facts and action receipts come from structured results. Reviewed variants add connective language, warmth and optional humor. Personality cannot change targets, dates, actions or confirmation semantics. Inline settings and primary/More actions consume canonical capabilities.

<a id="sequential-batch-plan-ordered-batches"></a>

### Ordered batches

Do one batch at a time. A batch may span multiple sessions; if too large, record explicit sub-batches before continuing. Complete its gate before dependent work. Do not recreate already working features just to satisfy a batch title.

<a id="sequential-batch-plan-b0--preserve-the-baseline-and-make-verification-portable"></a>

#### B0 — Preserve the baseline and make verification portable

Inventory current tracked/untracked edits; preserve existing implementation and design assets. Review the previous two local implementation batches and create a durable checkpoint when commit/push is authorized. Record exact SHA and tree state. Establish representative conversation fixtures before refactoring.

Resolve/document the toolchain discrepancy: current release CI uses JDK 17/Gradle 8.9 while the wrapper points at Gradle 9.3.1. Test compatibility before choosing and pinning a common supported setup. Add a non-publishing validation workflow for source/test changes and PRs; keep release publishing separate. Never require signing secrets for unit tests.

Gate: a fresh checkout can run the documented tests/build; baseline failures and device-only gaps are recorded; ordinary code changes trigger checks. Another account can recover all necessary files without the old chat history.

<a id="sequential-batch-plan-b1--enforce-zero-notes-access"></a>

#### B1 — Enforce zero Notes access

Map every chat entry point and transitive data dependency, including legacy actions and chat speech. Replace task Trash's all-data recovery call with task-only snapshots/recovery that preserve existing failure-before-delete and undo protections. Do not pass an empty notes list to an all-data restore format if that could erase notes on restore. Use explicit scope/versioned task-only format and restore semantics; leave native all-data backup intact.

Introduce narrow chat data contracts and regression guards against Notes imports/access. Use synthetic sentinel Notes in integration tests, instrument reads/writes, and prove zero accesses for task deletion/undo, app health, backups help, voice, malicious paraphrases and cross-domain follow-ups. Check persisted chat state/log output for sentinel data too.

Gate: task Trash/recovery still works and refuses mutation when recovery fails; no chat-triggered note read/write or private metadata disclosure; independent native Notes/backups still work. Static scans alone are not sufficient.

<a id="sequential-batch-plan-b2--establish-one-capability-contract"></a>

#### B2 — Establish one capability contract

Inventory every existing feature and label each operation: read, mutate, navigate, unsupported. Centralize help/action availability around actual domain schemas and permission state. Wrap existing handlers behind the contract; migrate one vertical slice first (settings read/change), then others without changing their UI.

Gate: help cannot advertise an unavailable write; every action chip has a validated equivalent command; unknown setting keys and direct legacy bypasses cannot mutate anything. A matrix maps each feature to its owner, current behavior, pending improvements and tests.

B2 implementation: [CAPABILITY_MATRIX.md](../CAPABILITY_MATRIX.md) records the runtime contracts, schema-derived settings adapter, legacy-chip normalization and current prerequisites. All typed actions map exhaustively to a contract and the coordinator enforces required review. Domain parsers/read handlers remain in place for B3/B4; settings is the first complete shared read/preview migration. Consult STATUS.md for validation before continuing.

<a id="sequential-batch-plan-b3--unify-understanding-and-ambiguity-handling"></a>

#### B3 — Unify understanding and ambiguity handling

Build shared candidate resolution around existing parsers. Cover contractions, polite language, punctuation, common English typos, questions vs commands, quoted text, hypothetical actions and negative scope. Protect task title text and date parsing. Preserve `/talk` semantics and don't create tasks from conversational fallbacks.

Examples: “how do I lock apps?” explains; “lock social apps except WhatsApp” previews; “don't lock WhatsApp” never executes a lock; “remind me to ask how locking works tomorrow” creates a task. “Tell me a joke” is conversation; genuinely ambiguous noun phrases get a short choice.

Gate: independent paraphrase and near-miss scenarios resolve correctly; ambiguous writes never execute; clear existing task inputs retain their behavior. Keep a held-out set unseen during parser tuning and report regressions, not just aggregate accuracy.

<a id="sequential-batch-plan-b4--make-context-consistent-across-features"></a>

#### B4 — Make context consistent across features

Migrate domain state to shared typed dialogue frames incrementally. Support “it”, “those”, “the second one”, “except that one”, correction of earlier fields, interruptions, explicit resume, recap and cancellation. Retain established short expiry unless evidence justifies a change. Topic switches must not leak a task reference into settings or app restrictions.

Example: select tasks → ask a setting question → “back to those tasks” → “move the second one to Friday”. Resolve the saved list identities, revalidate current rows, and ask again if scope is stale. “Yes, but at eight” revises a preview rather than executing it.

Gate: recreation, expiry, deleted entities, external edits, reordered lists, repeated taps and two competing requests cannot produce a wrong-target write. All recap/repair aliases share consistent policy; repeated replies do not replay actions.

<a id="sequential-batch-plan-b5--deepen-task-conversations-and-planning"></a>

#### B5 — Deepen task conversations and planning

Improve combined filtering, selection, multi-field edits, recurrence explanations, missing-date questions, duplicate resolution and batch previews using current task capabilities. Handle “show overdue priority tasks”, “move those except the dentist to Saturday”, “same time”, “the following week” and corrections reliably.

Plans use real due dates/priority/completion and user-supplied available time or duration estimates. If duration is absent, ask or label a tentative timebox; do not invent calendar availability. Offer explicit approval before applying a proposed schedule. Reuse existing cards.

Gate: calendar boundaries, timezone/DST, recurrence, completion/reward receipts, reversible edits and failure partway through a batch behave honestly. Recommendations never silently reschedule.

<a id="sequential-batch-plan-b5p--guided-planning-conversations-and-reviewed-taskchecklist-creation"></a>

#### B5P — Guided planning conversations and reviewed task/checklist creation

Required after B5 and before B6. Follow [GUIDED_PLANNING.md](../implementation/CHAT_EXPERIENCES.md#guided-planning), which records repository findings, external research limitations, state design, popup behavior and acceptance scenarios. Deliver P1–P6 sequentially: interaction/state prototype, day planning, safe task/subtask saving, exam preparation, trip preparation, independent/device validation.

Use dismissible planning offers for inferred intent, focused question sheets for missing information and a separate editable review before writes. Explicit planning requests can start immediately. Keep chat and popup answers on one validated path; corrections revise a versioned draft and invalidate old confirmations. Emotional disclosure alone must not force planning. The existing checklist subtasks can be reused; independently dated work uses separate tasks. Do not invent travel facts, syllabus content, durations or calendar availability. Plan saves require atomic task writes, durable deduplication and tested recovery of side effects; the current session confirmation mechanism alone is not enough evidence.

Gate: all P1–P6 checks pass with zero observed unintended writes or Notes accesses; context survives supported interruptions without reviving stale save authority. Existing task receipts/UI remain protected; new popups are limited to guided planning. Expose limitations and recovery paths rather than promising perfect language understanding.

<a id="sequential-batch-plan-b6--deepen-app-restrictions-and-routines"></a>

#### B6 — Deepen app restrictions and routines

Unify lock/unlock all, categories, named apps, exceptions and follow-up changes on the actual eligible app inventory. Support “keep WhatsApp available”, “also leave Maps unlocked”, “only social apps instead”, “make those soft”, and routine app/schedule revisions. Preview concrete scope and exclusions.

Audit category assignments/search with curated synthetic package fixtures, user categories, locale-aware ordering and deterministic ties. Unknown/duplicate app names need disambiguation. Explain Android permission or strict-mode limitations. Do not promise timed locks unless an actual timer/expiry owner is implemented and tested as a separate sub-batch.

Gate: all means all eligible apps, not merely saved rules; exceptions survive revisions; uninstall/reinstall or inventory changes invalidate stale scope; strict focus protections remain effective. Picker layout and overlay mechanism stay intact.

<a id="sequential-batch-plan-b7--deepen-habits-focus-and-learning"></a>

#### B7 — Deepen habits, focus and learning

Make habits support consistent inspection/edit/log/undo and conversational schedule repairs. Connect task planning to existing focus sessions without replacing running timers. Help users resume learning, inspect actual mistakes/results and select a native test from a conversation.

Examples: “I only have 15 minutes” → a suitable focus suggestion; “how did I do last time?” → stored results or an honest absence; “quiz me on words I saved” → supported native exercise or a clear limitation. Never fabricate completion, scores, time spent or per-question timings.

Gate: tracker caps and reward receipts survive retries; timer lifecycle uses the real owner; native test windows remain separate; no duplicate sessions/tests from replayed callbacks.

<a id="sequential-batch-plan-b7d--offline-dictionary-and-simple-english-explanations"></a>

#### B7D — Offline dictionary and simple English explanations

This is a required sequential batch after B7 and before B8; the suffix preserves existing batch IDs. Complete D1, D2 and D3 in order, recording each handoff. No runtime model or dictionary download is required: ship the selected text data in the app. Do not author every word with AI or attempt to store every possible sentence.

**D1 — Data selection and size prototype.** Inventory the existing vocabulary database, lookup/bookmark functions and licenses before choosing new data. Compare existing redistributable dictionary datasets for coverage, simple definitions, senses, examples, idioms, regional English and offensive/dated usage labels. Verify redistribution, attribution and derivative-data obligations against the actual source/version; do not assume public availability permits bundling. Record provenance, checksums, source versions and required notices. Keep the import pipeline reproducible and deduplicate existing entries. AI may assist reviewed editorial work, but must not invent dictionary facts, examples attributed to sources or source licenses.

Prototype a read-only indexed local store with stable entry/sense IDs. Measure actual compressed APK increase, installed data/index footprint, cold/warm lookup latency and memory on a representative device. Avoid whole-dictionary memory loads, duplicate packaged/unpacked copies where unnecessary, audio/image assets and oversized indexes. Compare at least a compact useful selection and a broader candidate; choose using measured coverage and size, and record a concrete budget before full integration. Do not promise a dataset size before measuring it. If a suitable license/source is unavailable, preserve existing lookup and mark the expansion blocked rather than fabricate permission.

**D2 — Conversational word and phrase lookup.** Provide accessible definitions, parts of speech, common senses, sourced/reviewed examples and synonyms only where supported. Resolve common inflections without damaging proper names; suggest spelling alternatives rather than silently assuming one. Match known multiword idioms/phrasal verbs before interpreting their individual words. Keep definitions and user text inert: “what does delete all tasks mean?” never executes deletion. Lookup is available in ordinary chat and as a read-only informational request in `/talk`; an unknown word never falls through to task creation.

Use a bounded dictionary dialogue frame containing entry/sense IDs, the user-supplied example when needed, explanation level and expiry. Support “more simply”, “another example”, “the other meaning”, “use it in a sentence”, “what is the difference between X and Y?”, “save this word” and “quiz me on it”. Saving goes only to the existing vocabulary bookmark store through a validated action; never Notes. Exercises keep their native windows. Distinguish quoted commands and literal task creation: “what does postpone mean?” is lookup; “postpone my dentist task” is a task action; “remind me to learn the word postpone” creates a task.

**D3 — Bounded sentence explanation.** Explain common idioms and explicitly supported grammar constructions in user-entered sentences using tested patterns and dictionary senses. Examples include conditional “I would have gone had I known” when that construction is supported, and “under the weather” as a recognized phrase. Context clues can rank senses but are not proof; show alternatives or ask when the meaning is ambiguous. Distinguish explaining one word/phrase from explaining the entire sentence. If the construction, sarcasm or implication is unsupported, say so and offer to explain its words or a selected phrase. Never present word-by-word synonym substitution as an accurate sentence paraphrase. Preserve negation, tense, modality and who did what in supported explanations. Simplified wording must come from accessible source definitions or reviewed sense-specific explanations, not a claim that arbitrary text can be simplified reliably.

Gate: a fresh install in airplane mode supports the chosen dictionary coverage without downloads; missing entries and ambiguous senses yield honest responses; definitions/examples remain tied to their source/sense. Verify multi-turn lookups, typo/inflection edge cases, unfamiliar idioms, quoted action words, switching back to tasks, stale references and zero Notes access. Include independent meaning-review samples, license notices and measured size/performance in the handoff. D3 ships with an explicit supported-pattern inventory and tested counterexamples, not an unrestricted sentence-understanding claim.

<a id="sequential-batch-plan-b8--make-settings-and-feature-explanations-authoritative"></a>

#### B8 — Make settings and feature explanations authoritative

Complete the feature matrix from B2: existing settings, permissions, reminders, theme, bubble behavior, categories, rewards, safe backup-health facts and all remaining chat-accessible features. For each, implement supported read/change/explain/navigation and explicitly label unsupported actions.

Support “what does that do?”, “what is it set to now?”, “why didn't it work?”, “change it back” (only when a valid guarded undo exists), and “what's the difference?” using live values and bundled verified facts. Permission diagnosis distinguishes missing permission from unknown OEM delivery behavior.

Gate: catalogue/schema consistency checks catch renamed keys/defaults/ranges; obsolete help cannot change settings; diagnostics are grounded and contain no Notes data, secrets or archive inspection. Links go to the correct native settings screen.

<a id="sequential-batch-plan-b9--add-conversational-personality-and-general-conversation"></a>

#### B9 — Add conversational personality and general conversation

The goal is enjoyable ongoing conversation, not only greetings followed by a task suggestion. Build on B3/B4 and dictionary batch B7D. Deliver the following coherent slices sequentially; update STATUS.md after each.

**C1 — Conversation state and transitions.** Add a bounded general-conversation frame for topic, last conversational question/offer, explicit user preferences within this exchange, game state and recent response families. Track whether the user is joking, asking for an explanation, sharing something or requesting help using conservative language cues; uncertainty is not a diagnosis. Keep emotional cues ephemeral, not a durable psychological profile. References such as “why?”, “another one”, “that wasn't funny”, “tell me more” and “not what I meant” must resolve within the active topic. Support “let's change the subject”, “back to the joke” and “never mind” without reviving stale action authority. Suspend or invalidate pending mutations under the existing expiry rules: a “yes” answering a casual offer must never confirm a previously suspended deletion.

**C2 — Broad, authored conversation packs.** Include greetings/farewells, daily check-ins, sharing good/bad news, hobbies/interests, boredom, mild frustration, motivation, celebration, playful banter, jokes, riddles, word games, short interactive stories and feature discovery. Each pack declares intents, follow-up branches, stop/repair routes, appropriate tone, factual sources where relevant and supported limits. Give games and stories explicit finite state and clear endings. Add local date/time and bounded arithmetic/unit conversions through real functions, and dictionary explanations through B7D. Packaged factual answers are reviewed and attributable; no fabricated fresh news, quotations or unlimited general-knowledge claims. Unknown topics get a short honest response and a relevant optional continuation, not an unrelated canned answer.

**C3 — Wit and empathy.** Use warm/playful/direct response variants with concise defaults, occasional situational humor and a bounded history that reduces repeated phrases, questions and joke families. Humor should follow the user's tone, never mock distress, identity or mistakes. On “be serious”, “less jokes” or “just listen”, adapt immediately in the current conversation; store a durable tone preference only through the explicit B10 flow. Acknowledge what the person actually said before offering help. Avoid claiming to feel emotions, know their life or understand exactly how they feel. Do not force positivity, guilt, dependence or a productivity solution into every exchange. A user can simply chat without being redirected to tasks.

For frustration or sadness, respond gently and offer a small choice only when useful: talking, a distraction or practical help. Do not diagnose from wording or assume sarcasm recognition is reliable. Explicit imminent-danger statements need a brief supportive safety response encouraging immediate real-world help, without invented location-specific resources or jokes; ordinary sadness should not automatically trigger crisis messaging. Keep sensitive disclosures out of durable preferences and diagnostic logs, subject to the existing ephemeral chat lifecycle.

**C4 — Conversation/action integration and review.** Only structured capabilities can mutate data. Dialogue packs can propose a supported read or action, but acceptance is scoped to the exact current offer and routed through the normal validator. An offer to find a small task is permission to show a suggestion, not to modify a schedule. Do not let a joke containing “delete”, a quoted example, a statement of past activity or an emotional disclosure become a task. General conversation requires no slash command; preserve `/talk` as the explicit app information/settings mode with read-only dictionary/help support. In `/talk`, handle social asides politely without silently changing mode or implicitly creating tasks.

Review complete exchanges, including:
- “My to-do list is judging me” → a light reply → “no advice, just a joke” → a joke → “another one” → a different joke, with zero task writes.
- “I had a rough day” → a considerate acknowledgement → “just distract me” → an optional short game → “stop” ends it, with no saved mood profile.
- “I finally finished it” after casual conversation → acknowledge or clarify; do not mark an old task complete from that statement alone.
- “What does reluctant mean?” → definition → “simpler” → accessible explanation → “give an example” → correct sense-specific example → “back to my tasks” revalidates task context.
- A pending delete preview → “tell me a joke” → “yes” to a joke offer never executes deletion; returning to deletion requires current explicit confirmation.

Gate: independent multi-turn scenarios cover topic switches, refusal of advice, humor correction, empathy, games, quoted commands and unexpected follow-ups. Test invariants and structured outcomes rather than exact wording for every variant; make variation reproducible in tests. Human review scores relevance, warmth, naturalness, repetition, excessive questioning and usefulness with concrete transcripts using synthetic data. Record failures and supported coverage; thousands of templates alone are not evidence of conversational quality. All pathways preserve the Notes boundary and action confirmation policy.

<a id="sequential-batch-plan-b10--add-user-controlled-personalization-and-useful-initiative"></a>

#### B10 — Add user-controlled personalization and useful initiative

Extend explicit allowlisted memory: preferred address, tone, verbosity, focus duration, planning preferences and user-selected quiet behavior. Show, edit and forget those preferences through chat. Keep ephemeral references separate from durable preferences; no arbitrary sensitive biography or inferred psychological profile.

Suggestions can use allowed local task/habit/focus state within chat, with cooldowns and dismissal. No unsolicited notifications or writes without a separately agreed product requirement. “Not now” should stop the suggestion; “forget my preferences” should clear only assistant memory and invalidate stale drafts, never Notes/tasks.

Gate: memory migration, inspection, expiry, erase, process death and repeat suggestions work predictably; explain why a suggestion appeared using allowed facts. No private content in development logs or benchmark fixtures.

<a id="sequential-batch-plan-b11--validate-offline-behavior-performance-and-release-quality"></a>

#### B11 — Validate offline behavior, performance and release quality

Run the full multi-turn and adversarial suite, build debug and optimized release, and test physical Android devices including Android 16. Exercise airplane mode, no speech service/language installed, partial/final speech, rotation/restart, timezone changes, permissions, keyboard/accessibility and OEM background behavior. Confirm no model download or online fallback is invoked.

Measure cold/warm turn latency, memory, APK size and battery impact on representative hardware; record device-specific budgets from baseline, then prevent regressions. Bound candidate parsing, history, lists and asset indexes; include long/malformed inputs, large app/task inventories, dictionary indexes and dialogue-pack selection. Recheck the B7D size/latency budget on the final optimized APK and verify all bundled data license notices.

Gate: no unresolved high-severity privacy or wrong-target mutation defects; every supported capability has grounded acceptance scenarios; remaining limitations are explicit. Unit tests do not replace device checks. Bump version/publish only when requested, then verify Actions for the exact pushed SHA.

<a id="sequential-batch-plan-quality-scorecard-for-every-batch"></a>

### Quality scorecard for every batch

- Privacy: zero Notes reads/writes/disclosures in the exercised paths; no conversation network calls/model downloads.
- Action correctness: correct target, values, scope and side effects; zero unintended writes in the adversarial suite. A zero observed count is a test result, not proof for every possible input.
- Understanding: scenario completion, unnecessary clarifications, wrong intent rates and regressions on independent examples. Report denominators and concrete failures.
- Context: successful repairs, interruptions, references and expiry; never silently use stale authority.
- Grounding: every factual feature/setting claim matches its actual source; unknown stays unknown.
- Experience: short useful replies, relevant next actions, varied but appropriate tone. User review of sample conversations determines whether it feels better.
- Efficiency: meaningful tests per risk/behavior rather than an arbitrary total. Keep existing regressions; remove duplicate tests only after showing equivalent coverage.

Use synthetic data. Each scenario should declare initial domain state, utterances, expected interpretation/clarification, allowed writes, forbidden writes, final state and relevant expiry/version changes. Test public conversation entry points and action effects, not merely parser implementation details.

<a id="sequential-batch-plan-sequential-sessionaccount-handoff"></a>

### Sequential session/account handoff

Use one canonical repository and an agreed branch. Work sequentially from a recorded checkpoint; a new account has no reliable access to another account's chat history or uncommitted workspace. Before switching, save the implementation, tests and these docs in a recoverable Git checkpoint when authorized, or export all necessary changes explicitly. A prose summary alone cannot transfer untracked files.

For each batch: read AGENTS.md and these documents; inspect branch/SHA/status; implement only the next batch or named sub-batch; run focused checks and broader checks warranted by shared-engine changes; update STATUS.md with exact results, limitations and next step. Do not force-push, discard existing changes, create unnecessary worktrees or bump versions per batch. Commit/push only under applicable user authorization. When a session must stop early, mark the batch in progress and document failing tests and incomplete paths instead of claiming completion.

Use this kickoff prompt in the next session:

> Work on Nrajesh0/Ayva. Read AGENTS.md, docs/assistant/history/PLANNING_BASELINE.md#sequential-batch-plan and docs/assistant/STATUS.md. Verify the checkout and preserve existing changes. Execute the next ready batch (or the recorded incomplete sub-batch) only, completing its acceptance gates. Keep the assistant fully offline without downloaded models. Chat must never access Notes, including indirectly through backups. Preserve the protected UI and native test windows. Reuse existing capabilities, validate actual behavior, and update STATUS.md with changed files, exact checks/results, limitations and the next step. If the batch is too large, split it into explicit ordered sub-batches and finish one coherent slice. Do not bump the version or publish unless I request it.

---

<a id="conversational-planner-roadmap"></a>

<a id="conversational-planner-roadmap-ayva-conversational-planning-without-models"></a>

## Ayva: conversational planning without models

> Historical roadmap — bounded core implementation complete. The open expansion frontier now belongs to [the new conversation-quality plan](../CONVERSATION_QUALITY_PLAN.md). See [delivery summary](../STATUS.md) for completed scope and remaining acceptance work. The original design and checkpoint descriptions below are retained as reference.

Status: living product and engineering roadmap. The bounded core delivery now includes canonical multi-session tasks, reminders, native editing, conversational planning and discussion, templates/memory, constraint scheduling, undo/redo and recovery. Necessary checks and debug packaging are recorded per source checkpoint. Independent audits, device/release acceptance and the research frontier remain open at the user’s request. Earlier optimized-release evidence applies only to its recorded checkpoint. See [the implementation ledger](../implementation/CURRENT.md) for actual code and validation. The findings in section 2 describe the pre-implementation baseline. The baseline below is a starting design, not a ceiling on capability. Sections 14–20 define how implementation must discover and incorporate better approaches. Use [the implementation playbook](../../development/ASSISTANT_WORKFLOW.md) to carry this work across sessions without losing requirements or overstating progress.

<a id="conversational-planner-roadmap-1-product-objective"></a>

### 1. Product objective

Build an offline assistant that can hold a useful planning conversation: discover a goal, retain constraints, propose meaningful steps, negotiate alternatives, schedule work, revise the plan, and apply agreed changes to the app. All interaction must be possible in the main chat composer. Forms, buttons, and detail views are optional conveniences, never required answers to an assistant question.

Use no language model, embedding model, remote inference, or network-dependent planning service. Use a deterministic language pipeline, typed dialogue state, explicit knowledge, authored planning methods, constraint solving, and compositional responses. This can produce unusually capable conversation within supported domains. It cannot honestly promise unrestricted human-level conversation or general world knowledge. Unknown meanings must lead to a useful, specific repair question rather than an invented answer or accidental task.

The quality target is end-to-end conversations, not the number of phrases or commands recognized. A plan can take many sessions and survive app restarts. Users should not need to learn field names, repeat facts, or re-enter a goal in a second box.

<a id="conversational-planner-roadmap-2-findings-from-the-current-implementation"></a>

### 2. Findings from the current implementation

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

<a id="conversational-planner-roadmap-3-experience-contract"></a>

### 3. Experience contract

<a id="conversational-planner-roadmap-modes-and-the-composer"></a>

#### Modes and the composer

- `/plan` selects a durable planning workspace. `/talk` selects conversation. `/drill` selects the existing drill workflow. Register other existing functions through the same command catalog.
- Typing `/` opens a compact, searchable command menu. Typing a standalone `plan`, `talk`, or `drill`, or a clear planning request, can show a quiet blue suggestion above the composer. A word mentioned inside ordinary prose must not steal the conversation.
- Tapping the suggestion activates a small mode label inside the composer, above the text. `/plan` submission also activates it directly. A clear submitted request such as “let's plan a trip” may enter Plan with a visible acknowledgement; ambiguous mentions only suggest it.
- Preserve the unsent text, cursor, selection, keyboard focus, and draft when selecting a mode. Remove only the recognized command prefix, never the user's goal.
- Plan stays active until explicitly exited or switched. Closing its label exits the mode and preserves the plan. Switching to Drill suspends the plan. Returning resumes its current state.
- Persist the mode with the conversation. On reopening, show which mode and plan are active. Allow several named plans with one active plan per conversation; explicit thread identity prevents cross-chat leakage.
- Plan edits affect the draft by default. An explicit command for another app function can run through the shared action gateway and return to the plan. If “move it” has two plausible owners, ask which one.

<a id="conversational-planner-roadmap-visual-and-interaction-direction"></a>

#### Visual and interaction direction

Use the compact blue suggestion and selected-mode pattern familiar from Codex, implemented with native Compose components. Exact animation parity requires a reference capture and visual comparison; it is not an existing specification.

Proposed starting motion tokens: suggestion fade/slide in roughly 120–160 ms; mode-label transition roughly 160–200 ms; small height changes with a restrained spring. Validate on device, respect disabled/reduced animations, and never delay text entry or Send for animation. Keep layout stable when suggestions appear. A small keyboard-navigation menu, TalkBack labels, predictable focus order, dark-theme contrast, large fonts, and landscape behavior are required.

Replace large repeated action blocks with text-first responses, at most two useful compact choices, and an overflow menu. Keep a single prominent action only when the next step warrants it. Visual chips can be around 28–32 dp high, but retain non-overlapping 48 dp touch targets. Compact appearance must not shrink accessibility targets. Hide duplicate or irrelevant actions; do not attach a menu to every social reply.

<a id="conversational-planner-roadmap-conversation-examples-that-define-the-feature"></a>

#### Conversation examples that define the feature

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

<a id="conversational-planner-roadmap-4-foundation-stop-losing-input"></a>

### 4. Foundation: stop losing input

This is the first engineering milestone, independent of the new planner.

Inventory every text-entry surface: task creation/editing, habit creation/editing, chat, notes, searches, dialogs, bottom sheets, and floating activities. Record who owns its window, focus, Back callback, dismissal, and draft.

For an ordinary editor, Back while the IME is visible should hide the keyboard and leave the editor and text intact. A later Back can dismiss the editor or navigate. A foreground popup or picker should follow an explicit overlay-specific policy. Test rapid repeated Back presses and predictive-back cancellation so one gesture never triggers both IME hiding and navigation.

Inspect IME insets inside each actual dialog/window, Material sheet Back handling, `onDismissRequest`, activity callbacks, and keyboard-animation races. Do not blanket-install competing BackHandlers. Use a shared policy with appropriate adapters for activities, Compose dialogs, sheets, and any view-based windows.

Use `SavedStateHandle`/saveable editor state for recreation and durable local drafts for meaningful unfinished forms and plans. Restore cursor and relevant selection. Auto-save a draft locally without committing a task or habit. Explicit Save commits; explicit Discard removes the draft. Accidental navigation, rotation, process death, and app switching must not erase work. Integrate draft deletion with the app's privacy and clear-data behavior.

Exit gate: device tests reproduce the original failure and pass on supported Android versions with gesture and three-button navigation, representative IMEs, and predictive Back where applicable. The core app-blocking `WindowManager.addView` mechanism remains intact.

<a id="conversational-planner-roadmap-5-architecture-one-typed-conversation-pipeline"></a>

### 5. Architecture: one typed conversation pipeline

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

<a id="conversational-planner-roadmap-language-understanding-without-models"></a>

#### Language understanding without models

1. Preserve original text and spans. Normalize punctuation, apostrophes, common contractions, and bounded typo variants without rewriting names or task titles.
2. Recognize speech acts: request, fact, answer, correction, rejection, comparison, explanation request, hypothetical, acknowledgement, interruption, resume, and commit.
3. Use compositional grammars for dates, quantities, entities, constraints, references, and operations. Lexicons and domain synonyms feed these grammars; regex remains useful for local tokens, not as the whole conversation architecture.
4. Produce several candidate interpretations with evidence and unresolved fields. A deterministic score is a ranking signal, not an uncalibrated probability. Establish acceptance thresholds with held-out examples.
5. Handle negation and scope explicitly: “don't move Friday's meeting,” “not this Saturday, the next one,” “except the booking,” “only the first two.”
6. Handle multiple clauses: “Move packing to Thursday, leave tickets alone, and remind me at six.” Resolve what the reminder belongs to rather than attaching it arbitrarily.
7. Reuse task names, aliases, plan titles, and domain vocabulary for bounded fuzzy matching. Never fuzzy-match destructive actions or ambiguous targets into execution.
8. Never treat arbitrary chat as a task by default. Unsupported input gets a narrow repair: “Do you mean move the booking task, or change the trip dates?”

Support English well first, including common informal spelling and the user's locale conventions. Additional languages and mixed-language input require their own grammars, lexicons, examples, and evaluation; do not call them supported merely because a few keywords match.

<a id="conversational-planner-roadmap-dialogue-manager"></a>

#### Dialogue manager

Use a hierarchy of dialogue frames instead of one global `expected` slot. Each frame records its goal, mode, active entities, answered fields, unresolved decisions, suspended parent, and evidence references. Store a typed question describing what is missing and why it matters. Accept an answer, an explanation request, a correction, a side question, or a topic switch while a question is pending.

Extract all supplied facts before deciding the next question. Prefer the question that unlocks the most useful progress with the least user effort. Ask one focused question normally; group tightly related missing facts only when that is clearer. “I don't know” must allow a useful partial plan, a proposed assumption, or an explicitly unscheduled task. Do not repeat answered questions.

Resolve pronouns, ordinals, and groups using stable entity IDs and the displayed revision: “it,” “the second one,” “both bookings,” “everything after that.” Track separate entities such as trip dates, preparation tasks, and work sessions. If two meanings are plausible, ask using their names. Treat tentative ideas, hypotheticals, and rejected facts differently from accepted constraints.

<a id="conversational-planner-roadmap-6-context-and-memory"></a>

### 6. Context and memory

Separate four stores:

1. **Turn context:** current utterance, displayed items, and selection.
2. **Conversation frames:** topics, pending questions, recent entities, corrections, and resumable interruptions.
3. **Durable plan workspace:** goals, decisions, revisions, constraints, items, schedules, and unsolved questions.
4. **Explicit preferences:** availability patterns, planning style, usual durations, and permitted personalization. Users can inspect, correct, and forget these.

Do not solve continuity by extending all current 15-minute expiries. Drafts may be durable while mutation confirmations remain short-lived and bound to live record versions. A remembered plan is not authorization to execute an old change.

Store provenance for facts: user statement, current app record, explicit preference, or proposed assumption. Include scope, time of assertion, and whether the fact was superseded. “I finish work at six today” must not become a permanent work schedule. Corrections replace effective facts while retaining enough revision history for explanation and undo.

Build compact structured summaries from the state itself; no model is needed. Rehydrate the relevant frame and current records, not every transcript message. Mark stale references instead of silently reusing them. Define clear distinctions among clearing chat, deleting plan drafts, forgetting preferences, and deleting saved tasks, and explain their effects in the UI.

For relevant responses, prioritize: explicit current request, active frame, referenced plan/items, immediate time horizon, explicit preferences, then optional suggestions. Greetings do not require a database agenda scan. Agenda views exclude trashed/deleted items, separate occasions from work, and use explicit horizons. A months-away birthday is not an urgent pending task; an explicitly requested reminder to buy its gift can be actionable work.

<a id="conversational-planner-roadmap-7-planning-representation-and-domain-capability"></a>

### 7. Planning representation and domain capability

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

<a id="conversational-planner-roadmap-8-a-real-scheduling-engine"></a>

### 8. A real scheduling engine

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

<a id="conversational-planner-roadmap-9-commit-correction-and-recovery"></a>

### 9. Commit, correction, and recovery

Keep proposal and execution separate without making users repeatedly approve harmless draft edits. A user can converse entirely through text, including “save this plan” or “apply those changes.” For a material change to existing commitments, present the affected items and obtain an unambiguous confirmation bound to the current revision. A vague “okay” after an explanation is not a commit.

Reuse the existing transactional, idempotent executor foundations. Extend them to atomic create/update/reparent/session operations, record-version checks, deterministic operation IDs, undo, and an outbox for alarms/widgets. Detect native edits made since preview and ask only about conflicting fields. Retrying after process death must not duplicate tasks or reminders.

Undo must be version-aware: do not roll back somebody's later edit or recreate a deleted item silently. Report separately whether app records were saved and whether Android reminder delivery is available. Test revocation of notification/exact-alarm permissions, reboot, recurrence, backup restore, and partial side-effect failure.

<a id="conversational-planner-roadmap-10-better-general-conversation"></a>

### 10. Better general conversation

Build compositional responses from semantic dialogue acts: acknowledge, reflect a stated constraint, answer, compare, propose, ask, summarize, explain, and repair. Maintain authored variation but prioritize factual consistency and continuity over random wording. Avoid repetitive “I can help” introductions and giant feature menus.

Support check-ins, brief small talk, explaining the app, talking through choices, evaluating pros/cons against user criteria, discussing progress, and gently offering planning when relevant. Distinguish “just listen,” “help me think,” and “help me act.” Do not turn emotional language into an automatic productivity intervention.

Explain decisions with actual planner evidence: “I put research first because booking depends on your dates.” Answer “what did we decide?”, “what remains uncertain?”, “why did you ask?”, and “what changed?” directly from state. Admit when a topic is outside the installed knowledge and redirect to a useful supported conversation without pretending comprehension.

<a id="conversational-planner-roadmap-11-evaluation-as-a-product-capability"></a>

### 11. Evaluation as a product capability

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

<a id="conversational-planner-roadmap-12-delivery-roadmap-with-acceptance-gates"></a>

### 12. Delivery roadmap with acceptance gates

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

<a id="conversational-planner-roadmap-13-completion-definition"></a>

### 13. Completion definition

The feature is exceptional when the user can say what they want once, refine it naturally over many turns and sessions, understand what Ayva knows and is assuming, get feasible schedules with clear conflict resolution, and trust that agreed changes happen exactly once. It must remain useful when the user is uncertain, changes their mind, interrupts, types imperfectly, or returns tomorrow.

The bounded core implementation spans editor recovery, greetings, modes, durable discussion/planning, canonical task hierarchy and multiple sessions, work-start reminders, native editing, reviewed writes, constraints and dependent replanning, templates/preferences, numeric ranges and binary qualitative reasoning, multi-level undo/redo, and encrypted archive/rolling recovery. Existing native tasks can be adopted without duplication; completed/occasion records stay read-only context. See the [current ledger](../implementation/CURRENT.md) and [delivery record](FOUNDATION_DELIVERY.md#core-completion) for exact supported behavior and evidence. The user has deferred broader audits. Remaining acceptance includes independent conversation evaluation, performance/device/visual checks and a current optimized release; graded/weighted reasoning, richer knowledge/languages, deeper arbitration and advanced optimization remain expansion tracks.

<a id="conversational-planner-roadmap-14-treat-the-roadmap-as-an-evolving-design"></a>

### 14. Treat the roadmap as an evolving design

The implementation must improve this plan as it learns. Initial requirements can be firm while architectures, estimates, and hypotheses remain revisable. Discoveries must expand useful capability, not merely generate more documentation or more supported phrases.

Maintain three layers:

1. **Product commitments:** the user-visible outcomes and constraints in the requirements register. These cannot disappear during refactoring, context loss, or scope reduction.
2. **Current implementation plan:** the best supported route to those outcomes, including dependencies, migrations, and validation. Change this when evidence supports a better route.
3. **Expansion frontier:** additional capabilities, uncertain approaches, and research questions. Promote an item only after it has a concrete user journey, a plausible offline/no-model design, and a validation approach.

Every milestone includes a capability review at its start and end. Ask: what can the user now accomplish; which realistic conversation still breaks; does the failure expose a missing phrase, a missing concept, or a wrong architecture; can one reusable primitive solve several gaps; and does the change remain practical on device?

Reserve an explicit discovery allowance, initially around 15–20% of each milestone's capacity, and revise it using actual findings. Use bounded prototypes for high-uncertainty choices. Do not spend months completing a design that a short experiment could disprove. Equally, do not let perpetual redesign prevent shipping a useful, tested journey.

Routine architectural and implementation decisions within the agreed scope should proceed without repeated user approval. Bring genuinely consequential product tradeoffs to the user after preparing concrete alternatives: a model/network dependency, new access to private data, incompatible behavior, removal of a requested capability, or a major scope/timeline change. Never silently introduce these as convenient workarounds.

<a id="conversational-planner-roadmap-15-continuous-discovery-and-improvement-loop"></a>

### 15. Continuous discovery and improvement loop

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

<a id="conversational-planner-roadmap-16-capability-expansion-frontier"></a>

### 16. Capability expansion frontier

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

<a id="conversational-planner-roadmap-17-extensibility-contracts-that-make-growth-practical"></a>

### 17. Extensibility contracts that make growth practical

Define contracts before broadening domain coverage:

- A **capability descriptor** declares supported modes, input meanings, required context, permissions, side effects, and result types. It does not independently parse and execute arbitrary text.
- A **domain pack** supplies vocabulary, planning methods, questions, factual content, and examples. It cannot bypass the shared executor or access private data through response rendering.
- A **dialogue policy** selects typed questions and next moves using frame state. Domain-specific logic extends explicit rules instead of altering global dispatch order unpredictably.
- A **planner operator** declares preconditions, effects, invariants, reversibility, and validation. A schedule proposal is data, not an executable command string.
- A **response document** carries structured explanation, references, optional controls, and status. Text, accessibility output, and compact UI render from the same semantics.
- A **context query** declares what information is relevant and permitted. A greeting does not gain broad data access merely because some other capability needs it.

Create developer tooling to inspect a turn locally: selected mode, candidates, discarded interpretations, resolved references, applicable facts, selected question, plan delta, solver reasons, and proposed writes. Redact private values by default in exported diagnostics. This trace makes subtle failures debuggable without relying on a developer guessing which regex fired.

Version all persistent schemas and domain-pack formats. Reject incompatible packs cleanly and test upgrades/downgrades where supported. Index relevant records and parse incrementally where useful; move expensive work off the UI thread and support cancellation. Maintain explicit memory, storage, and execution budgets as breadth increases.

<a id="conversational-planner-roadmap-18-continuous-architecture-challenges"></a>

### 18. Continuous architecture challenges

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

<a id="conversational-planner-roadmap-19-requirements-coverage-and-protected-boundaries"></a>

### 19. Requirements coverage and protected boundaries

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

<a id="conversational-planner-roadmap-20-release-gates-and-an-open-improvement-horizon"></a>

### 20. Release gates and an open improvement horizon

Define completion per release while leaving room for further capability growth. A release can be complete against declared supported journeys even while the expansion frontier remains open. Never call an assistant universally conversational because one domain passes.

For each release publish an internal capability report: supported journeys, tested language/locale scope, known limitations, failure categories, evaluation conditions, performance measurements, and device coverage. Preserve regression scenarios and report improvements against prior versions. Re-estimate the next release from observed work, not the original speculative timeline.

At each release boundary choose a small number of high-value expansion tracks and complete their end-to-end journeys. Maintain the long-term frontier, but avoid an unlimited collection of unfinished features. The goal is continuing, measurable growth in capability with stable everyday behavior—not a frozen plan and not endless redesign.
