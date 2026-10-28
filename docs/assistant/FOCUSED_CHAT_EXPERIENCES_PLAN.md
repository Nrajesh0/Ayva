# Run: focused offline chat experiences

Created: 28 October 2026. Status: implementation delivered locally; release validation remains open. See [implementation evidence and limits](FOCUSED_CHAT_IMPLEMENTATION.md).

## Product goal

Make Ayva a dependable, discoverable offline companion for specific activities. Aim for an excellent experience within advertised capabilities, rather than unrestricted conversation. Users should know what they can do, receive meaningful follow-ups, and have a graceful way to finish or switch activities.

An “8/10” is an aspirational product target, not an accuracy claim or a promised benchmark. Success means users can discover an activity without guessing, enjoy a complete session, recover from ordinary mistakes, and understand its limits.

This plan supersedes earlier *future general-chat expansion recommendations*. It does not replace the completed implementation record in GENERAL_CHAT_IMPLEMENTATION.md or reopen accepted task planning work. Completion of earlier code is not evidence that every experience below already exists.

## Non-negotiable boundaries

- Fully offline at runtime, with no trained models, embeddings, or remote inference.
- Preserve Plan behavior, date parsing, checklist edits, task/habit authority, review/commit, undo, explicit handoff, and existing dated-task priority in normal Chat.
- Preserve the familiar composer and functions above it. Discovery is a compact addition, not a composer redesign.
- Never turn unsupported conversation, game answers, facts, or quotations into tasks without an explicit operational request through the existing authority path.
- No invented expertise, human biography, factual claims, plot details, or quotation attribution.
- No unsolicited references to distant occasions or unrelated pending tasks.
- No modifications to the app-blocking overlay mechanism.
- Avoid new conversation runtimes and competing sources of session state.
- Run only essential checks for changed behavior. Defer broad audits and exhaustive suites unless a concrete regression requires them.

## Current baseline and gaps

Existing code includes structured general-dialogue memory, authored topic packs, preference corrections, follow-ups, a restricted ChatScript meaning fallback, small joke/riddle collections, a five-round word-chain game, short branching stories, and established dictionary/learning features. Existing hobby handlers support some more specific continuations.

The main gaps are discovery, overlapping conversation ownership, shallow activity content, uneven short-answer understanding, weak replayability, and unclear limits. ChatScript supplies recognition, not a ready-made general knowledge or entertainment collection. Its package and startup costs must be justified by useful coverage.

## Delivery sequence

Each batch must ship a complete, usable slice. Keep incomplete experiences out of discovery. Mark batches complete only after their acceptance criteria are demonstrated; record limitations and evidence beside the checkbox.

### Batch 0 — Inventory and capability contract

- [x] Inventory real entry points, owners, stored state, commands, content counts and follow-ups in AssistantSocial, EverydayTopics, GeneralConversation, GeneralDialogue, dictionary and learning handlers.
- [x] Record existing behavior before changing ownership, including the recently repaired reading → fantasy → tell me more exchange.
- [x] Introduce a typed capability registry: stable ID, name, concise promise, example requests, owner, supported follow-ups, limits, content version, availability and discovery category.
- [x] Distinguish general discussion, guided reflection, factual knowledge, games and operational commands. A supported word such as “music” does not imply knowledge of every musician or song.
- [x] Advertise only implemented, available content. Missing or invalid packs hide their entry or give a concise unavailable response.

Acceptance: every displayed capability maps to a working owner and a complete sample interaction. Unfinished entries cannot appear as working features. Registry data is reused by discovery and capability/help responses.

### Batch 1 — Ownership and continuation

- [x] Define one active general-chat experience with stable ID/version, phase, last question, expected answer type, focus item, last supported explanation, and bounded recent-item history.
- [x] Reuse the existing encrypted session store. Add explicit migration/reset behavior for older or incompatible frames; do not create a second persistent conversation database.
- [x] Centralize ownership decisions for the general-chat area: explicit commands and existing operational priority first, then valid continuation of the active experience, then new supported intent, then bounded clarification/unsupported handling.
- [x] Resolve short answers against the last question: “fantasy,” “harder,” “the second one,” “not that,” “another,” “why,” and “same category.” Ask when multiple interpretations remain plausible.
- [x] Support stop, skip, restart, change category, pause and resume consistently. Preserve a small bounded suspended-activity history; never restore stale action authority.
- [x] Allow explicit task/habit interruptions through existing handlers. Offer return to the previous activity without replaying an answer or action.
- [x] Prevent the early and late fallback paths from independently advancing the same turn.
- [x] Record selected owner/rule and bounded failure categories without storing raw user text in diagnostics by default.

Acceptance: a user can interrupt and resume a game or discussion without losing its place; a short answer reaches its owner; a factual follow-up refers to the right item. Plan state and pending operational confirmations remain unchanged by general chat.

### Batch 2 — Discoverability and honest limits

- [x] Add a compact “Explore Ayva” entry using existing chat visual conventions. Open a dismissible panel with Play, Discover, Take a break, Talk a little, and Use Run.
- [x] Keep the text field, keyboard behavior, existing function row and send controls intact. Discovery must not cover the composer or erase a draft.
- [x] Show a small selection of real example requests. Use honest names such as “Explore what you enjoy about books,” not promises of unrestricted book knowledge.
- [x] Add contextual optional chips: Hint, Explain, Another, Skip, Stop, or Resume as appropriate. Typed equivalents must work; no mandatory forms.
- [x] Answer “what can you do?”, “what can we talk about?” and “help” from the registry, scoped to the current activity when useful.
- [x] Implement three outcomes: supported answer; clearly bounded exercise using user-supplied details; or a warm, specific limitation with a relevant supported alternative.
- [x] Use at most one clarification for the same unresolved interpretation before offering a graceful exit or concrete choices. Do not apply this limit to separate legitimate questions in a game.
- [x] Avoid repeating limitation notices on every turn, unrelated promotional suggestions, and forced mode switches.

Acceptance: a new user can start an activity without knowing a slash command. Unknown festival, relationship, project or plot requests do not produce fabricated expertise or task creation. Alternatives are offered only when the underlying feature exists.

Example: “I don’t have a reliable guide to that festival offline. I can help organize the preparations you tell me about.” Enter planning only after the user requests that handoff.

### Batch 3 — Word chain as a complete game

- [x] Replace the fixed shallow exchange with configurable short sessions, difficulty and an optional longer session.
- [x] Use a curated playable dictionary subset, indexed by starting letter and suitable difficulty. Avoid scanning large shards on every turn.
- [x] Validate words, required letters and repeats; define rules for inflections, hyphens, proper names, regional spellings and very obscure entries.
- [x] Give fair uncertainty wording: “That word isn’t in my game dictionary” instead of declaring it nonexistent. Allow a skip or a clearly unscored continuation when appropriate.
- [x] Ensure Ayva chooses a valid unused word, and handle exhausted candidate sets without loops or invented words.
- [x] Add progressive hints, optional meanings, score/progress, difficulty changes between rounds, and a satisfying end summary.
- [x] Preserve turn state across recreation/resume. Do not penalize accidental duplicate delivery or an operational interruption.

Acceptance: complete easy and harder sessions with a misspelling, repeated word, hint, unknown word, interruption and return. Every scored word is validated against the declared rules.

### Batch 4 — Anagrams and vocabulary challenges

- [x] Implement a second strong word game: anagrams or build-a-word, selected after checking the quality of available dictionary entries.
- [x] Generate rounds from known valid answers; enforce letter multiplicity and accept alternative valid answers where the rules allow them.
- [x] Filter unsuitable words and avoid puzzles whose answer is effectively given away or whose difficulty cannot be explained.
- [x] Add progressive hints, reveal, definitions/examples, skip, restart and meaningful session endings.
- [x] Reuse existing vocabulary/drill components where appropriate instead of creating a competing learning owner.
- [x] Adapt difficulty conservatively to recent answers; keep achievements, preferences and learning history bounded and clearable.

Acceptance: no unsolvable generated round, repeated immediate puzzle, invalid rejection of a supported alternative, or loss of progress on resume. The existing learning features continue to own their established actions and records.

### Batch 5 — Riddles, jokes and motivational lines

- [x] Move content from tiny embedded lists to versioned, validated data packs.
- [x] Riddles carry accepted answers/aliases, ambiguity notes, progressive hints and an explanation. A plausible unrecognized answer gets an honest response, not a false definitive rejection.
- [x] Jokes carry category, tone, explanation, repetition group and suitable audience metadata. Support “not my kind,” “less silly,” “explain,” and “another.”
- [x] Motivational content has distinct calm, disciplined, resilient and battle-inspired styles. Keep it brief, optional and free of shaming or promises of success.
- [x] Separate verified quotations from original Run lines. Store author/work/source for attributed quotations; omit uncertain attributions rather than guessing.
- [x] Introduce bounded seen/disliked histories and category-aware selection. Explain when a collection is exhausted; offer switching or an explicit replay instead of silently cycling.
- [x] Avoid turning a request for a joke or line into unsolicited coaching.

Initial editorial targets, adjustable after review: 40 genuinely distinct riddles, 60 jokes and 80 original motivational lines. These are variety targets, not permission to ship filler. Expand only reviewed content; do not advertise unreviewed drafts.

Acceptance: several consecutive sessions have useful variety, hints/explanations are item-specific, disliked categories are respected, and exhaustion is handled explicitly. A quotation attribution can be traced to its source.

### Batch 6 — Curated fact trails

- [x] Start with a small collection of durable facts in a few categories, for example space, nature and language. Defer live news, prices, schedules and claims that require current data.
- [x] Each fact card includes stable ID, claim, source, review date, topic, short explanation, example/comparison, supported questions, related-card links and uncertainty qualifiers.
- [x] Build follow-up trails rather than independent fact strings. “Why?”, “How?”, “Compared with…?” and “Related fact” resolve against the current card.
- [x] Expose source details on request or through a discreet detail action; avoid noisy attribution text in every reply.
- [x] Abstain when the requested follow-up is not supported. A related fact must not be presented as an answer to a different question.
- [x] Use alias indexes first. Add Room FTS only if collection size and measured retrieval failures justify it; retrieval must not manufacture an answer from an unrelated match.

Initial target: 30 reviewed cards, each with at least two meaningful follow-ups and a useful related connection. Prefer fewer complete trails to a large trivia dump.

Acceptance: a user can explore several related turns without repeated paraphrases. Sources support the actual claim and explanation. Unknown follow-ups terminate or redirect honestly without invented causal explanations.

### Batch 7 — Tiny interactive stories

- [x] Replace the very small fixed scene set with authored story graphs: nodes, supported choices, optional aliases, state requirements and endings.
- [x] Keep sessions short and clearly choice-based. Accept natural wording for supported choices, but do not pretend arbitrary text rewrites the story.
- [x] Provide meaningful consequences, several endings, pause/resume, restart and a clear finish.
- [x] Use controlled variation only for cosmetic details that cannot contradict the plot or change a choice’s meaning.
- [x] Validate links, reachable endings and state prerequisites during content packaging.

Initial target: three polished short stories with at least two meaningful endings each.

Acceptance: every displayed choice works, every reachable path can end, and resuming preserves the chosen branch. Unsupported actions receive a concise explanation and available choices.

### Batch 8 — Deeper guided discussions

- [x] Begin with three narrow experiences selected from existing coverage: unwinding preferences, study difficulties and reading tastes. Expand work, music and cooking only after these meet the same quality bar.
- [x] Give each experience a clear purpose and boundary. Distinguish talking about the user’s preferences from knowing a particular book, song, exam syllabus or recipe.
- [x] Store dimensions that matter to that experience, plus tentative versus definite statements. Update the referenced dimension rather than overwriting the latest arbitrary note.
- [x] Build branches for elaboration, disagreement, correction, rejection of advice, examples and changing direction.
- [x] Use concise acknowledgements followed by a relevant contribution. Ask questions only when the answer helps choose a meaningful continuation.
- [x] Offer a natural stopping point: a brief summary, a suitable suggestion, or an invitation to return. Never pad the session with generic questions to make it longer.
- [x] Offer relationship, festival, project or other adjacent exercises only after an explicit capability is authored for them; otherwise use the honest boundary behavior.

Acceptance: each experience handles a realistic 8–12-turn example with at least one correction, short answer, interruption and unsupported follow-up. This is a depth probe, not a requirement to prolong every conversation. Do not count stock acknowledgements as meaningful continuations.

### Batch 9 — Quality, resources and release readiness

- [x] Compare native-enabled and Kotlin-only handling on the same small set of representative paraphrases. Measure useful extra recognition, latency and memory on available devices.
- [x] Retain ChatScript only where the observed value warrants its approximately 16 MB compressed data and process startup cost. If it does not, move the useful rules into the existing Kotlin boundary and remove the native package in a dedicated change.
- [x] Review owner selection, abandonment points, repeated content and fallback categories using synthetic sessions and user-reported examples. Do not enable raw transcript collection by default.
- [x] Check content indexing/loading off the UI thread, bounded caches/history, missing-pack fallback and compatibility with older session versions.
- [x] Verify the discovery panel with keyboard visible, back handling, accessibility labels, text scaling and restoration of the input draft.
- [x] Run the small established planner/authority regression set only where shared routing changes could affect it.
- [ ] Perform ARM64/Android 16 device checks when a suitable device is available; label emulator-only evidence accurately.
- [x] Update capability help and the completion ledger to match what actually ships. Version bumps and publication are separate release actions.

Acceptance: advertised experiences pass their end-to-end examples, users can escape every activity, ordinary unsupported input does not loop, and no outstanding routing regression affects Plan or task/habit actions. Record remaining device limitations explicitly.

## Reuse decisions

| Source or project | Intended use | Boundary |
|---|---|---|
| Existing Wordset/WordNet data | Word validation, definitions, suitable synonym puzzles | Filter senses and playable entries; a dictionary relation is not a factual explanation |
| ChatScript | Recognition of additional wording within supported experiences | Existing runtime only; no competing native state, app actions or sample-bot persona |
| ChatterBot corpus | Editorial inspiration for openings and banter | No automatic nearest-response serving or copied fictional identity |
| Tracery | Design reference for constrained wording/story variation | Implement the small needed subset in Kotlin; no JavaScript runtime by default |
| Taskmaster / SGD / MultiWOZ | Reference examples of corrections and interruptions | Design material, not executable knowledge or a conversational model |
| Curated factual and quotation sources | Verified offline cards and attribution | Select and inspect sources before import; no blanket repository ingestion |
| Room FTS | Optional retrieval as reviewed content grows | Add only with measured need; not a source of semantic understanding |
| RiveScript, AIML/Program-Y, extra chatbot frameworks | No additional runtime | Avoid duplicated ownership and state |
| KStateMachine, better-parse, new date recognizers, scheduling solvers | Remain deferred | This project does not reopen accepted planning or shared parsing |

Use the existing research documents as evidence, not as an instruction to install every repository. Recheck a chosen source’s version, data quality, notices and Android integration before adding assets or dependencies. Bundle provenance/licensing in the appropriate information surface, not repetitive chat replies.

## Shared content contract

Content packs need stable item IDs, schema/version, category, short promise, supported input aliases, follow-up IDs, explanation/hint data, source/provenance where relevant, audience suitability, difficulty, repetition group and graceful exhaustion behavior. Only use fields relevant to the experience.

Keep rendering, selection and content separate. Store seen IDs and explicit preferences rather than full duplicate content. Bound retention, use existing deletion controls and never infer permanent user traits from a casual answer. Selection should vary relevant content without random contradictions or immediate repeats.

At packaging time, check schema validity, duplicate IDs, missing references, invalid puzzle answers and unreachable story endings. Human review covers correctness, ambiguity, humor and tone; passing a schema check does not establish quality.

## Essential verification policy

Prefer a few complete representative sessions to hundreds of one-line template assertions. Required checks are limited to changed high-impact behavior:

- Ownership and state transitions, including interruption/resume and unsupported-input escape.
- No accidental task/habit mutations or changes to pending operational authority.
- Puzzle solvability, answer validation and repeat/exhaustion rules.
- Pack validity, bounded state and migration/reset behavior.
- Native timeout/failure fallback when modifying that boundary.
- One focused device pass for changed discovery/keyboard behavior when available.

Do not delete an existing regression test merely because it catches a conflict. Update wording assertions only when intended behavior has changed and the test still protects the underlying contract. Leave broad audits for later unless concrete evidence makes them necessary.

## Expansion and stopping rules

During implementation, newly observed shortcomings go into a short ledger: example input sequence, intended owner, observed problem, user impact, proposed bounded fix, and affected batch. Finish an in-scope blocker before marking its batch complete. Defer unrelated features explicitly rather than silently increasing the scope.

Every new advertised capability must include an opening, useful continuation, correction/rejection handling, interruption/resume behavior where relevant, an honest limit, and a satisfying exit. New opening phrases alone do not qualify as a completed capability.

Stop adding content to an experience when additional material is repetitive or unreviewed. Improve the experience’s weak branch before advertising another broad topic. An activity that ends well after five turns is preferable to one that becomes generic after fifteen.

## Completion ledger

| Batch | Status | Evidence / remaining work |
|---|---|---|
| 0 — Inventory and capability contract | Implemented | Shared registry and owner inventory; content promises bounded to implemented handlers |
| 1 — Ownership and continuation | Implemented, bounded | Versioned focused frames; social snapshots; three suspended activities; existing operational authority retained |
| 2 — Discovery and honest limits | Implemented | Compact grouped panel, example requests, draft preservation and unsupported-topic exits |
| 3 — Word chain | Implemented | Dictionary validation; bounded curated bot vocabulary; hints/meaning/skip; explicit difficulty and length |
| 4 — Anagrams and vocabulary | Implemented | 20 puzzles; alternate dictionary answers; existing vocabulary owner linked from discovery |
| 5 — Riddles, jokes and lines | Implemented; editorial review open | 40 riddles, 60 jokes, 80 original lines; category selection and exhaustion |
| 6 — Fact trails | Implemented; source verification blocked | 30 cards, explanations/examples/source action; source websites returned HTTP 403 |
| 7 — Interactive stories | Implemented | Three validated reachable graphs with multiple endings and supported choice aliases |
| 8 — Guided discussions | Implemented, bounded | Existing topic packs extended for short answers and specific study/work difficulties; graceful limits |
| 9 — Quality and release readiness | Emulator verification completed; physical validation open | Targeted regression/build, native/Kotlin sample comparison and app PSS evidence recorded; physical Android 16 and child-process peak memory unavailable |

## Scope resolutions and remaining validation

Unchecked original checklist items are not claims of completion. Some broad design goals are intentionally bounded in this delivery:

- Existing social and dialogue state contracts remain compatible; a common adapter coordinates new experiences rather than rewriting every mature owner. Existing coordinator request IDs prevent duplicate turn execution; new activity chips carry expiring session/revision references and an allowlisted query; existing social chips keep their signed-choice contract.
- Difficulty changes are explicit. After three anagrams solved without hints, Ayva offers harder puzzles; the user chooses whether to accept. Harder puzzles use at least seven letters. No inferred permanent user traits or hidden achievements were added.
- The small bot vocabulary is filtered in memory; a new index/FTS store is unnecessary at this size. English alphabetic dictionary entries are accepted; comprehensive proper-name/regional/inflection classification is not claimed.
- Fact related-card navigation stays within the current category. Arbitrary comparisons and causal questions are not generated; unsupported questions receive a boundary.
- Cosmetic randomized story variation and a new framework are unnecessary for three authored graphs. Stable authored choices and ordinal/natural prefixes are supported.
- Audience/repetition/dislike metadata, explicit fact links, and longer reading/unwinding sessions are implemented and checked. Category preferences, dislikes and seen histories remain bounded within the existing session store.
- Source-site verification and physical ARM64/Android 16 review remain open. Native recognition/timing and app PSS were measured on the software emulator; child peak memory and phone performance remain unmeasured. The emulator discovery checks verify opening, selection, dismissal, draft preservation, real IME visibility, Back, and 1.6× text size. They do not establish physical Android 16 behavior.

The completed code is usable independently of those validation and editorial tasks. Do not label it an objectively measured 8/10 or claim all original acceptance criteria have been demonstrated.
