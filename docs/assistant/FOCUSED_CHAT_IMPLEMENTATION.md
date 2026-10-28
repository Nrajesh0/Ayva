# Focused offline chat implementation

28 October 2026. Prepared for version 2.0.17 (117), following the requested version bump and GitHub push.

## Implemented

- Compact Explore Ayva discovery in the existing function row; grouped examples and concise capability promises. Selection preserves the composer draft. Plan mode keeps its existing UI.
- A single active experience within the existing encrypted session, seven-day resumable frames and at most three suspended focused activities. Operational commands retain their existing execution authority. Social progress is captured before the earlier dated-task route so riddles survive task interruptions.
- Dictionary-backed word chain with scoring, repeat checks, hints, skip, configurable difficulty, short/long rounds and exhaustion. Unknown words are described as missing from the game dictionary, not nonexistent.
- Twenty authored anagram puzzles with exact letter multiplicity, alternate dictionary answers, progressive hints, reveal, skip, completion, pause/resume and bounded seen history. Four longer puzzles give harder mode a meaningful distinction; consecutive unassisted solves offer an optional difficulty increase.
- Sixty jokes, forty riddles and eighty original Run encouragement lines. Jokes have explanations and category selection; riddles have aliases, hints and explanations. No invented quotation attribution. Collections end explicitly rather than silently cycling.
- Thirty authored fact cards across space, nature and language, each with explanation, example and source reference. Unknown follow-ups receive an honest boundary.
- Three short branching stories with multiple endings, supported choices, pause/resume and restart. Story choices do not create tasks.
- More specific short-answer handling for study difficulties, work interruptions and unwinding. Existing hobby continuations remain in their established owner. Exhausted discussions offer recap or an exit instead of padding with generic questions.
- Honest unsupported-topic replies and capability help. No new trained models, runtime dependencies, databases or remote calls.

## Ownership inventory

| Area | Owner and persisted state | Boundary |
|---|---|---|
| New games, fact trails, encouragement, stories | FocusedChat; featureState.focusedChat/history | Speech and navigation only |
| Jokes, riddles, hobby openings | AssistantSocial; social/pausedSocial | Existing signed choices retained |
| Guided reflection | GeneralDialogue / GeneralConversation; dialogueV2 | User-supplied details and authored suggestions |
| Broader small talk | Existing EverydayTopics and routing | Finite supported topics |
| Dictionary and learning | Existing dictionary and AssistantLearningLanguage | Existing learning records/actions unchanged |
| Tasks, habits and planning | Existing authoritative handlers | New content cannot execute domain mutations |

## Deliberate bounds

- Difficulty is user-controlled. Three anagrams solved without hints trigger an optional harder-puzzle suggestion; accepting selects words of at least seven letters. No hidden automatic change or inferred permanent trait.
- English alphabetic dictionary entries are accepted in word games; the curated bot vocabulary is smaller than the full dictionary. Dictionary coverage is not linguistic authority.
- Story wording choices and ordinal aliases are supported; arbitrary user-written story actions are not generated.
- Fact references are bundled offline. This environment denied all attempted public source-site fetches with HTTP 403. Editorial dates do not mean the source pages were fetched successfully. External factual review is still required before treating the collection as source-verified.
- A native/Kotlin comparison has now run on the API 29 x86_64 software emulator. See FOCUSED_CHAT_EMULATOR_COMPARISON.json. The one extra recognized sample is now handled directly in Kotlin. Retain the native fallback for unmatched wording for now: eight synthetic phrases and software-emulator timings do not establish that its wider coverage can be removed safely. No native process is needed for the focused activities. Physical-device and child-process peak-memory measurements remain open.
- No ARM64/Android 16 physical device is attached. Emulator evidence is recorded separately below.
- Human editorial review of humor, ambiguity and discussion quality remains valuable; content counts are not a quality score.

## Verification

- 86 selected unit/integration checks passed after the operational-interruption fix, including existing planner regressions. The final social-control rerun also passed. The final narrow follow-up passed all 14 selected checks after the harder-puzzle change; the debug APK rebuilt successfully.
- Debug app and instrumentation APKs assembled successfully.
- API 29 emulator: FocusedDiscoveryDeviceTest passed (one test), covering discovery selection/dismissal and preservation of an unfinished composer draft.
- Source dependency guard passed across 116 files; git diff whitespace checks passed.
- Broad audits and physical-device checks remain deferred. Native comparison and API 29 keyboard/Back/1.6× text-size checks are recorded below; they do not establish Android 16 behavior.

## Try it

- Explore Ayva → Word chain → hint → a matching word → meaning → pause → resume word chain → stop.
- “Play anagrams” → hint → planet → next → pause → “buy apples tomorrow” → resume anagrams. Only the explicit dated request creates a task.
- “Give me a riddle” → hint → piano → next → skip → reveal → stop. Stopping discards the resumable riddle.
- “Tell me a joke” → explain → less silly → another → stop.
- “Explore facts” → why → example → source → related fact. Unsupported questions do not invent an explanation.
- “Play a tiny story” → first → second → next → pause story → resume story.
- “I am stressed about studying” → remembering it → recap. The short reply updates the study difficulty instead of opening an unrelated topic.

## Integration notes

The reusable assets are the existing dictionary and learning infrastructure. This change adds authored Kotlin content and a small session adapter, not imported chatbots or new framework dependencies. Fact references are visible only on request. Task planning files, date parsing, habit execution and app-blocking overlays are unchanged.

The last implementation review found and repaired three specific issues: a dated-task early return bypassing riddle snapshots, length changes accidentally resetting difficulty, and typed social controls diverging from buttons. These are covered in the focused regression sessions.

## Remaining implementation completed in the follow-up

- Activity choices use the existing internal-command route, with session ID, revision, expiry, current owner and an allowlist of the displayed choices. Old, replayed and forged choices cannot advance a round, create a dated task, or force a chat-mode change.
- Focused activity frames now include phase, expected answer type, focus item and a bounded owner/phase diagnostic containing no user wording. Dictionary work in the focused adapter runs on a background dispatcher.
- General discussions pause/resume alongside games and hobbies. Saved discussion snapshots contain recall data, not operational references. Stop removes resumable discussion state.
- Word-chain bot candidates are indexed by starting letter. Rules explain dictionary coverage, alphabetic entries, inflections, regional forms, proper-name labels and unscored skipping. Score/progress can be requested explicitly.
- Joke metadata includes stable content-derived IDs, repetition group, tone, general audience and provenance. “Not my kind” stores a bounded dislike list that remains excluded when the collection is replayed.
- Fact metadata includes editorial date, supported follow-ups, explicit related-card IDs and an offline qualification. Related navigation cannot drift into another category.
- Reading/music/cooking discussion facets distinguish tentative interest and rejection. Recaps preserve those distinctions across interruption/resume; repeated explanations end with a concise choice to recap, switch aspect or stop.
- Unknown-topic notices shorten after the first refusal instead of replaying the full capability pitch.

## Comparative evidence and editorial review

The native comparison used the same eight synthetic inputs and unwinding frame. Before the added Kotlin wording, seven inputs had no useful extra native interpretation; one relaxation recommendation did. Native process calls took approximately 3.25–5.98 seconds on the software emulator, versus 0.14–0.53 seconds for direct Kotlin. These are diagnostic observations under emulation, not phone latency benchmarks. App PSS increased from 228,159 to 233,159 KiB during the sample; this does not measure child-process peak memory or attribute the delta solely to native code.

A local editorial pass checked the bounded content contracts: original lines have no invented attribution; riddle misses acknowledge stored-answer limits; facts separate Earth-day durations and solar/rotational days; hobby explanations avoid specific book/song/recipe knowledge. Source pages remain externally unverified because their hosts are blocked. Local editorial review is not a claim of independent source verification or a measured product rating.

Only external source verification and physical-device/child-process performance evidence remain unavailable here. Broad audits remain deferred as requested. Final follow-up evidence is recorded below.

The final recall cleanup also removes suspended activity snapshots and session-only content preferences when the user forgets the conversation. Explicitly saved memories/plans remain under their existing deletion controls. This prevents a later resume command from reconstructing forgotten chat state.

Follow-up verification: the full selected set passed 90 checks after the typed-choice routing, normalization and hobby-preference changes. Both native instrumentation checks passed. The final recall-cleanup run passed all 18 selected checks and rebuilt the debug APK. Both discovery instrumentation checks passed on API 29, including the keyboard, Back and 1.6× text-size scenario. No physical-device result is implied.

The keyboard probe initially encountered a frozen Android System UI and its system ANR dialog. After closing/recovering that emulator system process, both UI tests passed (49.409 seconds total). This was an emulator failure, not evidence of a composer defect. The test also explicitly prepares keyboard/focus state and records a screenshot if the IME fails to appear again.

Final source dependency guard passed across 116 files; whitespace checks passed. Version 2.0.17 (117) includes this implementation; GitHub Actions determines release publication. The remaining release-validation items are source-site review, physical ARM64/Android 16 checks, and physical-device/child-process peak-memory measurements. All planned feature-code changes are implemented; native removal remains an explicit evidence-based follow-up, not an implicit dependency of these features.

## Feedback refinement — 28 October 2026

Removed Talk a little from discovery; retained underlying context handlers used by existing features. Games now use separated titles, puzzle lettering, score text and compact instructions. Riddles separate the question from controls. Stories display numbered choices and accept unambiguous wording containing the meaningful choice words instead of repeating the whole scene on a miss.

Nonpersonal content IDs now persist in `ayva_content_rotation` independently of chat history. Clearing a chat still clears its conversation and activity state, but no longer resets anagrams, jokes, riddles, facts or encouragement to their first item. Story openings and word-chain starters rotate too. Explicit replay controls remain available when a finite collection ends.

Added original interactive retellings of The Tortoise and the Hare, The Lion and the Mouse, and The Crow and the Pitcher, bringing the story collection to six. Alternate endings are labelled. No modern adaptation text or illustrations were imported.

Version 2.0.18 (118) packages this update. Debug build and 19 focused checks passed, including fresh-session rotation, natural story choices, existing context handling and activity authority. Dependency and whitespace checks passed. Visual formatting still needs real-device taste review; these changes are local and have not been version-bumped or pushed.
