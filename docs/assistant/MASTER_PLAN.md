# Ayva offline personal assistant: sequential development plan

> Historical specification — B0–B11 bounded implementation complete; independent acceptance gaps remain explicit. Superseded as the active queue by [the conversation-quality plan](CONVERSATION_QUALITY_PLAN.md). See [delivery summary](DELIVERY_SUMMARY.md).

Planning baseline: 2026-10-18. This is the original specification; implementation/acceptance status is tracked in [STATUS.md](STATUS.md). See [OFFLINE_ASSISTANT_IMPLEMENTATION.md](OFFLINE_ASSISTANT_IMPLEMENTATION.md) for the current bounded implementation and explicit remaining gates. A proposed feature below is not automatically implemented or accepted.

## Product goal and limits

Build a dependable, personable English assistant for Ayva's actual tasks, habits, routines, app restrictions, focus, learning, settings, dictionary lookup and help, alongside enjoyable everyday conversation. Everything in the conversation engine runs locally using rules, structured state, packaged knowledge and reviewed response templates. No downloaded or bundled language model, embeddings service, remote inference, external search, telemetry of conversations or runtime model training.

AI can help write and review code, language examples and conversation content during development. The shipped app executes ordinary deterministic code. This can produce rich, useful conversation within supported domains; it cannot honestly promise unrestricted human-level conversation, arbitrary reasoning or current world knowledge. On unfamiliar requests, explain the limit and offer a relevant supported next step without creating an unintended task.

Prioritize correct understanding and useful follow-ups over the number of canned answers. First establish boundaries and a common engine, then deepen capabilities, then personality. Keep changes incremental and preserve working behavior.

## Non-negotiable boundaries

- Chat never reads, searches, summarizes, creates, edits, deletes or indexes Notes/vault data, including titles, counts, previews, attachments and private metadata. No consent phrase or prompt can bypass this. No note database/repository, export, restore or backup-content capability in the chat domain.
- Generic bundled help about Notes and explicit navigation to its native screen are acceptable; they must not read its data or trigger an operation there. A task such as “remind me to review my notes” remains a task. Text users type into chat is chat input, never a reason to fetch private storage.
- Audit indirect access too: shared task recovery, transcript helpers, diagnostics, logs, caches, backup status and legacy handlers. Keep native Notes functionality and independent app backup protections intact.
- Preserve task creation/rescheduling receipt cards, existing cats, composer/slash access, briefs, native Drill/Blitz/vocabulary windows and approved response controls. No general UI redesign. App picker improvements concern sorting/filtering only.
- Preserve WindowManager blocking and existing strict routine/focus protections; follow AGENTS.md.
- `/talk` is app information/settings conversation. It must not implicitly create tasks. Ordinary messages retain natural task creation. Explicit task commands use the existing task path. Information, quoted examples, hypotheticals, negation and small talk must not accidentally become mutations.
- Every UI chip and text command goes through the same validated action path. Replies report actual outcomes, distinguish draft from saved state and never claim an overlay/reminder was delivered merely because it was configured.
- Maintain confirmations for bulk/destructive/settings changes, stale-state checks, scope snapshots and guarded undo. Clear low-risk task creation can keep its existing direct-save behavior; do not add confirmation to every sentence.

## Current foundation and observed gaps

The current working tree already contains typed actions, encrypted expiring sessions, task references and lists, clarification and repair, three suspended questions, actual settings knowledge, habits/routines, app groups and exceptions, learning and focus context, and explicit allowlisted preferences. Reuse them.

Primary entry points are `OfflineAssistant.kt`, `AssistantActionExecutor.kt`, `AssistantSession.kt`, `AssistantPreferences.kt`, `AssistantAppKnowledge.kt`, domain helpers, `AyvaIntentRouter.kt` and the legacy `AyvaTalkEngine.kt`. Several overlapping parsing/routing layers and domain-specific state handling make consistency harder. Introduce common contracts gradually rather than rewriting everything.

Baseline privacy finding: `AssistantActionExecutor.execute` used to open `NoteDatabase` for `TrashTasks` and pass its DAO to an all-data snapshot helper. B1 replaces this indirect read with selected-task recovery; see [B0_B1_IMPLEMENTATION.md](B0_B1_IMPLEMENTATION.md) and STATUS.md for evidence and remaining verification. Preserve that boundary while expanding capabilities.

At planning baseline, speech used Android's on-device recognizer in `AudioMemoManager` alongside saved-media playback. B1 extracts `util/speech/OnDeviceTranscriber` for chat, while the native audio manager delegates transcription and retains its media functions separately. Voice may use an already installed OS speech service; it must never fall back to cloud or trigger a model download. Text must work with no speech service installed. Do not promise offline dictation on every device.

Existing tests and build evidence are recorded in STATUS.md. Older capability documents describe earlier batches and may be stale; code, current tests and the new acceptance criteria take precedence.

## Target architecture

`Input → normalize/tokenize → intent candidates → mode/context resolution → dialogue policy → read capability or validated action → actual result → response composition`

1. **Capability registry:** Each supported operation declares aliases, required fields, read/write classification, allowed contexts, typed inputs, validators, risk/confirmation policy, side effects, undo support and help examples. Unsupported operations have truthful explanations/native navigation. Settings use the real preference schema, never a separately invented list.
2. **Language interpretation:** Preserve quoted titles and original text. Normalize contractions and polite wrappers; handle negation, dates, clauses, exceptions and corrections explicitly. Rank candidates using explainable rule evidence, not invented statistical confidence. Ties involving a mutation require a focused clarification. Fuzzy matching may suggest an entity; it must not silently select a different app for a write.
3. **Dialogue state:** Typed domain frames hold selected stable IDs, displayed ordering, missing fields, draft changes, source/time/version, suspended question and expiry. Keep current confirmation tokens single-use. Distinguish saved facts, user-provided preferences, temporary assumptions and unsaved drafts. Never restore confirmation authority after expiry/restart merely by replaying text.
4. **Narrow data interfaces:** Inject only allowed feature readers/writers. No general SQL/filesystem/network tools or full application database in the conversational layer. Android adapters perform validated domain operations. Enforce Notes exclusions with dependency checks and behavioral tests; this is an application boundary, not an OS sandbox claim.
5. **Response composer:** Facts and action receipts come from structured results. Reviewed variants add connective language, warmth and optional humor. Personality cannot change targets, dates, actions or confirmation semantics. Inline settings and primary/More actions consume canonical capabilities.

## Ordered batches

Do one batch at a time. A batch may span multiple sessions; if too large, record explicit sub-batches before continuing. Complete its gate before dependent work. Do not recreate already working features just to satisfy a batch title.

### B0 — Preserve the baseline and make verification portable

Inventory current tracked/untracked edits; preserve existing implementation and design assets. Review the previous two local implementation batches and create a durable checkpoint when commit/push is authorized. Record exact SHA and tree state. Establish representative conversation fixtures before refactoring.

Resolve/document the toolchain discrepancy: current release CI uses JDK 17/Gradle 8.9 while the wrapper points at Gradle 9.3.1. Test compatibility before choosing and pinning a common supported setup. Add a non-publishing validation workflow for source/test changes and PRs; keep release publishing separate. Never require signing secrets for unit tests.

Gate: a fresh checkout can run the documented tests/build; baseline failures and device-only gaps are recorded; ordinary code changes trigger checks. Another account can recover all necessary files without the old chat history.

### B1 — Enforce zero Notes access

Map every chat entry point and transitive data dependency, including legacy actions and chat speech. Replace task Trash's all-data recovery call with task-only snapshots/recovery that preserve existing failure-before-delete and undo protections. Do not pass an empty notes list to an all-data restore format if that could erase notes on restore. Use explicit scope/versioned task-only format and restore semantics; leave native all-data backup intact.

Introduce narrow chat data contracts and regression guards against Notes imports/access. Use synthetic sentinel Notes in integration tests, instrument reads/writes, and prove zero accesses for task deletion/undo, app health, backups help, voice, malicious paraphrases and cross-domain follow-ups. Check persisted chat state/log output for sentinel data too.

Gate: task Trash/recovery still works and refuses mutation when recovery fails; no chat-triggered note read/write or private metadata disclosure; independent native Notes/backups still work. Static scans alone are not sufficient.

### B2 — Establish one capability contract

Inventory every existing feature and label each operation: read, mutate, navigate, unsupported. Centralize help/action availability around actual domain schemas and permission state. Wrap existing handlers behind the contract; migrate one vertical slice first (settings read/change), then others without changing their UI.

Gate: help cannot advertise an unavailable write; every action chip has a validated equivalent command; unknown setting keys and direct legacy bypasses cannot mutate anything. A matrix maps each feature to its owner, current behavior, pending improvements and tests.

B2 implementation: [CAPABILITY_MATRIX.md](CAPABILITY_MATRIX.md) records the runtime contracts, schema-derived settings adapter, legacy-chip normalization and current prerequisites. All typed actions map exhaustively to a contract and the coordinator enforces required review. Domain parsers/read handlers remain in place for B3/B4; settings is the first complete shared read/preview migration. Consult STATUS.md for validation before continuing.

### B3 — Unify understanding and ambiguity handling

Build shared candidate resolution around existing parsers. Cover contractions, polite language, punctuation, common English typos, questions vs commands, quoted text, hypothetical actions and negative scope. Protect task title text and date parsing. Preserve `/talk` semantics and don't create tasks from conversational fallbacks.

Examples: “how do I lock apps?” explains; “lock social apps except WhatsApp” previews; “don't lock WhatsApp” never executes a lock; “remind me to ask how locking works tomorrow” creates a task. “Tell me a joke” is conversation; genuinely ambiguous noun phrases get a short choice.

Gate: independent paraphrase and near-miss scenarios resolve correctly; ambiguous writes never execute; clear existing task inputs retain their behavior. Keep a held-out set unseen during parser tuning and report regressions, not just aggregate accuracy.

### B4 — Make context consistent across features

Migrate domain state to shared typed dialogue frames incrementally. Support “it”, “those”, “the second one”, “except that one”, correction of earlier fields, interruptions, explicit resume, recap and cancellation. Retain established short expiry unless evidence justifies a change. Topic switches must not leak a task reference into settings or app restrictions.

Example: select tasks → ask a setting question → “back to those tasks” → “move the second one to Friday”. Resolve the saved list identities, revalidate current rows, and ask again if scope is stale. “Yes, but at eight” revises a preview rather than executing it.

Gate: recreation, expiry, deleted entities, external edits, reordered lists, repeated taps and two competing requests cannot produce a wrong-target write. All recap/repair aliases share consistent policy; repeated replies do not replay actions.

### B5 — Deepen task conversations and planning

Improve combined filtering, selection, multi-field edits, recurrence explanations, missing-date questions, duplicate resolution and batch previews using current task capabilities. Handle “show overdue priority tasks”, “move those except the dentist to Saturday”, “same time”, “the following week” and corrections reliably.

Plans use real due dates/priority/completion and user-supplied available time or duration estimates. If duration is absent, ask or label a tentative timebox; do not invent calendar availability. Offer explicit approval before applying a proposed schedule. Reuse existing cards.

Gate: calendar boundaries, timezone/DST, recurrence, completion/reward receipts, reversible edits and failure partway through a batch behave honestly. Recommendations never silently reschedule.

### B5P — Guided planning conversations and reviewed task/checklist creation

Required after B5 and before B6. Follow [GUIDED_PLANNING.md](GUIDED_PLANNING.md), which records repository findings, external research limitations, state design, popup behavior and acceptance scenarios. Deliver P1–P6 sequentially: interaction/state prototype, day planning, safe task/subtask saving, exam preparation, trip preparation, independent/device validation.

Use dismissible planning offers for inferred intent, focused question sheets for missing information and a separate editable review before writes. Explicit planning requests can start immediately. Keep chat and popup answers on one validated path; corrections revise a versioned draft and invalidate old confirmations. Emotional disclosure alone must not force planning. The existing checklist subtasks can be reused; independently dated work uses separate tasks. Do not invent travel facts, syllabus content, durations or calendar availability. Plan saves require atomic task writes, durable deduplication and tested recovery of side effects; the current session confirmation mechanism alone is not enough evidence.

Gate: all P1–P6 checks pass with zero observed unintended writes or Notes accesses; context survives supported interruptions without reviving stale save authority. Existing task receipts/UI remain protected; new popups are limited to guided planning. Expose limitations and recovery paths rather than promising perfect language understanding.

### B6 — Deepen app restrictions and routines

Unify lock/unlock all, categories, named apps, exceptions and follow-up changes on the actual eligible app inventory. Support “keep WhatsApp available”, “also leave Maps unlocked”, “only social apps instead”, “make those soft”, and routine app/schedule revisions. Preview concrete scope and exclusions.

Audit category assignments/search with curated synthetic package fixtures, user categories, locale-aware ordering and deterministic ties. Unknown/duplicate app names need disambiguation. Explain Android permission or strict-mode limitations. Do not promise timed locks unless an actual timer/expiry owner is implemented and tested as a separate sub-batch.

Gate: all means all eligible apps, not merely saved rules; exceptions survive revisions; uninstall/reinstall or inventory changes invalidate stale scope; strict focus protections remain effective. Picker layout and overlay mechanism stay intact.

### B7 — Deepen habits, focus and learning

Make habits support consistent inspection/edit/log/undo and conversational schedule repairs. Connect task planning to existing focus sessions without replacing running timers. Help users resume learning, inspect actual mistakes/results and select a native test from a conversation.

Examples: “I only have 15 minutes” → a suitable focus suggestion; “how did I do last time?” → stored results or an honest absence; “quiz me on words I saved” → supported native exercise or a clear limitation. Never fabricate completion, scores, time spent or per-question timings.

Gate: tracker caps and reward receipts survive retries; timer lifecycle uses the real owner; native test windows remain separate; no duplicate sessions/tests from replayed callbacks.

### B7D — Offline dictionary and simple English explanations

This is a required sequential batch after B7 and before B8; the suffix preserves existing batch IDs. Complete D1, D2 and D3 in order, recording each handoff. No runtime model or dictionary download is required: ship the selected text data in the app. Do not author every word with AI or attempt to store every possible sentence.

**D1 — Data selection and size prototype.** Inventory the existing vocabulary database, lookup/bookmark functions and licenses before choosing new data. Compare existing redistributable dictionary datasets for coverage, simple definitions, senses, examples, idioms, regional English and offensive/dated usage labels. Verify redistribution, attribution and derivative-data obligations against the actual source/version; do not assume public availability permits bundling. Record provenance, checksums, source versions and required notices. Keep the import pipeline reproducible and deduplicate existing entries. AI may assist reviewed editorial work, but must not invent dictionary facts, examples attributed to sources or source licenses.

Prototype a read-only indexed local store with stable entry/sense IDs. Measure actual compressed APK increase, installed data/index footprint, cold/warm lookup latency and memory on a representative device. Avoid whole-dictionary memory loads, duplicate packaged/unpacked copies where unnecessary, audio/image assets and oversized indexes. Compare at least a compact useful selection and a broader candidate; choose using measured coverage and size, and record a concrete budget before full integration. Do not promise a dataset size before measuring it. If a suitable license/source is unavailable, preserve existing lookup and mark the expansion blocked rather than fabricate permission.

**D2 — Conversational word and phrase lookup.** Provide accessible definitions, parts of speech, common senses, sourced/reviewed examples and synonyms only where supported. Resolve common inflections without damaging proper names; suggest spelling alternatives rather than silently assuming one. Match known multiword idioms/phrasal verbs before interpreting their individual words. Keep definitions and user text inert: “what does delete all tasks mean?” never executes deletion. Lookup is available in ordinary chat and as a read-only informational request in `/talk`; an unknown word never falls through to task creation.

Use a bounded dictionary dialogue frame containing entry/sense IDs, the user-supplied example when needed, explanation level and expiry. Support “more simply”, “another example”, “the other meaning”, “use it in a sentence”, “what is the difference between X and Y?”, “save this word” and “quiz me on it”. Saving goes only to the existing vocabulary bookmark store through a validated action; never Notes. Exercises keep their native windows. Distinguish quoted commands and literal task creation: “what does postpone mean?” is lookup; “postpone my dentist task” is a task action; “remind me to learn the word postpone” creates a task.

**D3 — Bounded sentence explanation.** Explain common idioms and explicitly supported grammar constructions in user-entered sentences using tested patterns and dictionary senses. Examples include conditional “I would have gone had I known” when that construction is supported, and “under the weather” as a recognized phrase. Context clues can rank senses but are not proof; show alternatives or ask when the meaning is ambiguous. Distinguish explaining one word/phrase from explaining the entire sentence. If the construction, sarcasm or implication is unsupported, say so and offer to explain its words or a selected phrase. Never present word-by-word synonym substitution as an accurate sentence paraphrase. Preserve negation, tense, modality and who did what in supported explanations. Simplified wording must come from accessible source definitions or reviewed sense-specific explanations, not a claim that arbitrary text can be simplified reliably.

Gate: a fresh install in airplane mode supports the chosen dictionary coverage without downloads; missing entries and ambiguous senses yield honest responses; definitions/examples remain tied to their source/sense. Verify multi-turn lookups, typo/inflection edge cases, unfamiliar idioms, quoted action words, switching back to tasks, stale references and zero Notes access. Include independent meaning-review samples, license notices and measured size/performance in the handoff. D3 ships with an explicit supported-pattern inventory and tested counterexamples, not an unrestricted sentence-understanding claim.

### B8 — Make settings and feature explanations authoritative

Complete the feature matrix from B2: existing settings, permissions, reminders, theme, bubble behavior, categories, rewards, safe backup-health facts and all remaining chat-accessible features. For each, implement supported read/change/explain/navigation and explicitly label unsupported actions.

Support “what does that do?”, “what is it set to now?”, “why didn't it work?”, “change it back” (only when a valid guarded undo exists), and “what's the difference?” using live values and bundled verified facts. Permission diagnosis distinguishes missing permission from unknown OEM delivery behavior.

Gate: catalogue/schema consistency checks catch renamed keys/defaults/ranges; obsolete help cannot change settings; diagnostics are grounded and contain no Notes data, secrets or archive inspection. Links go to the correct native settings screen.

### B9 — Add conversational personality and general conversation

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

### B10 — Add user-controlled personalization and useful initiative

Extend explicit allowlisted memory: preferred address, tone, verbosity, focus duration, planning preferences and user-selected quiet behavior. Show, edit and forget those preferences through chat. Keep ephemeral references separate from durable preferences; no arbitrary sensitive biography or inferred psychological profile.

Suggestions can use allowed local task/habit/focus state within chat, with cooldowns and dismissal. No unsolicited notifications or writes without a separately agreed product requirement. “Not now” should stop the suggestion; “forget my preferences” should clear only assistant memory and invalidate stale drafts, never Notes/tasks.

Gate: memory migration, inspection, expiry, erase, process death and repeat suggestions work predictably; explain why a suggestion appeared using allowed facts. No private content in development logs or benchmark fixtures.

### B11 — Validate offline behavior, performance and release quality

Run the full multi-turn and adversarial suite, build debug and optimized release, and test physical Android devices including Android 16. Exercise airplane mode, no speech service/language installed, partial/final speech, rotation/restart, timezone changes, permissions, keyboard/accessibility and OEM background behavior. Confirm no model download or online fallback is invoked.

Measure cold/warm turn latency, memory, APK size and battery impact on representative hardware; record device-specific budgets from baseline, then prevent regressions. Bound candidate parsing, history, lists and asset indexes; include long/malformed inputs, large app/task inventories, dictionary indexes and dialogue-pack selection. Recheck the B7D size/latency budget on the final optimized APK and verify all bundled data license notices.

Gate: no unresolved high-severity privacy or wrong-target mutation defects; every supported capability has grounded acceptance scenarios; remaining limitations are explicit. Unit tests do not replace device checks. Bump version/publish only when requested, then verify Actions for the exact pushed SHA.

## Quality scorecard for every batch

- Privacy: zero Notes reads/writes/disclosures in the exercised paths; no conversation network calls/model downloads.
- Action correctness: correct target, values, scope and side effects; zero unintended writes in the adversarial suite. A zero observed count is a test result, not proof for every possible input.
- Understanding: scenario completion, unnecessary clarifications, wrong intent rates and regressions on independent examples. Report denominators and concrete failures.
- Context: successful repairs, interruptions, references and expiry; never silently use stale authority.
- Grounding: every factual feature/setting claim matches its actual source; unknown stays unknown.
- Experience: short useful replies, relevant next actions, varied but appropriate tone. User review of sample conversations determines whether it feels better.
- Efficiency: meaningful tests per risk/behavior rather than an arbitrary total. Keep existing regressions; remove duplicate tests only after showing equivalent coverage.

Use synthetic data. Each scenario should declare initial domain state, utterances, expected interpretation/clarification, allowed writes, forbidden writes, final state and relevant expiry/version changes. Test public conversation entry points and action effects, not merely parser implementation details.

## Sequential session/account handoff

Use one canonical repository and an agreed branch. Work sequentially from a recorded checkpoint; a new account has no reliable access to another account's chat history or uncommitted workspace. Before switching, save the implementation, tests and these docs in a recoverable Git checkpoint when authorized, or export all necessary changes explicitly. A prose summary alone cannot transfer untracked files.

For each batch: read AGENTS.md and these documents; inspect branch/SHA/status; implement only the next batch or named sub-batch; run focused checks and broader checks warranted by shared-engine changes; update STATUS.md with exact results, limitations and next step. Do not force-push, discard existing changes, create unnecessary worktrees or bump versions per batch. Commit/push only under applicable user authorization. When a session must stop early, mark the batch in progress and document failing tests and incomplete paths instead of claiming completion.

Use this kickoff prompt in the next session:

> Work on Nrajesh0/Ayva. Read AGENTS.md, docs/assistant/MASTER_PLAN.md and docs/assistant/STATUS.md. Verify the checkout and preserve existing changes. Execute the next ready batch (or the recorded incomplete sub-batch) only, completing its acceptance gates. Keep the assistant fully offline without downloaded models. Chat must never access Notes, including indirectly through backups. Preserve the protected UI and native test windows. Reuse existing capabilities, validate actual behavior, and update STATUS.md with changed files, exact checks/results, limitations and the next step. If the batch is too large, split it into explicit ordered sub-batches and finish one coherent slice. Do not bump the version or publish unless I request it.
