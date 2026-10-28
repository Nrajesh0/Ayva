# General conversation: revised recommendation, planning preserved

This review supersedes the implementation scope in RUN_RECOMMENDED_STACK_2026-10-25.md. The user now explicitly considers task planning satisfactory. Preserve Plan behavior, its parsers, dates, checklist operations, review/commit, undo, UI and existing explicit conversation-to-plan handoff. Licensing is outside this technical selection. This is source inspection and architecture analysis, not an Android benchmark or a completed integration.

## Decision

Build a general-conversation-only Kotlin boundary, typed conversational memory and Run-authored dialogue packs. Evaluate **ChatScript as the sole general dialogue runtime** behind that boundary. Reuse selected dictionary senses and synonyms through the existing dictionary store. Use selected ChatterBot corpus material as editorial input, not as an automatic response database. Add Room FTS4 retrieval only when the authored knowledge pack warrants it.

Do not install the previously proposed five-library stack together. KStateMachine, better-parse, Recognizers-Text and a scheduling solver do not solve the current general-conversation gap sufficiently to justify touching working operational paths. RiveScript Java remains a mutually exclusive fallback if ChatScript fails the native integration/value gate. A failed prototype should leave the native general-chat implementation usable; adopting another runtime is not mandatory.

## What the renewed inspection found

1. **Planning is coupled to shared conversation helpers.** ConversationPlanner calls GeneralConversation.handoff; PlanningLibrary calls its archive; PlanLanguage delegates normalization and clause splitting to ConversationLanguage and ConversationMeaning; PlanningDialogue uses ConversationKnowledge; TripChecklistConversation checks ConversationMeaning.Kind.POSSIBILITY. ConversationVoice.finish also affects plan_workspace responses. A wholesale replacement of these shared helpers would violate the new scope even if no planner file changed.
2. **There are early and late general-chat paths.** OfflineAssistant invokes GeneralConversation before native dialogue processing and again near its final fallback. The `question || isConversationalStatement` branch can return before the late fallback. BubbleChatActivity routes a null result through AyvaIntentRouter, including task creation. Installing a new engine only at the last fallback will miss eligible turns. Installing it first risks taking native commands. General-chat ownership needs an explicit, narrowly scoped integration contract.
3. **Context is often stored as strings rather than usable relationships.** GeneralConversation retains topic and ten recent notes; an `actually` correction drops the most recent note rather than locating a particular assertion. The sharing path often echoes the user and rotates three questions. More synonyms will not repair this data model.
4. **Follow-up handling can answer the wrong speech act.** In a knowledge frame, the existing `shorter` path calls ConversationKnowledge.explain, while other frames often summarize the user's note. A proper follow-up needs the last assistant answer, its supporting content and the user's requested transformation.
5. **Conversation recall and action authority share a clock helper.** GeneralConversation.current requires AssistantContext.live, a 15-minute predicate also used for action references. Introduce a separate conversational recall policy; do not extend confirmation validity or alter AssistantContext.expire.
6. **Current diagnostics can be misleading.** OfflineAssistant computes lastConversationRoute after the turn has changed session state. Future general-chat diagnostics should record the owner and rule actually selected at decision time, with no raw text by default.
7. **The dictionary is not an existing searchable knowledge database.** AssistantDictionaryStore loads hash-selected gzip JSON shards with a four-shard cache; DictionarySense exposes definitions, examples and synonyms. No Room FTS annotations were found in the inspected source. Room is available, but conversational full-text search would be new work. Nor does this store expose a ready-made commonsense graph.
8. **A prior product choice limits normal Chat.** Its calendar-priority branch deliberately captures eligible dated messages as tasks before general dialogue. A personal statement mentioning a date can therefore still become a task. Preserve this requested behavior now; use Talk for date-bearing discussion. Do not claim seamless unrestricted conversation in normal Chat while retaining that rule.

## Runtime and data review

| Candidate | Decision | Reason and remaining limitation |
|---|---|---|
| ChatScript | First-choice isolated prototype | Concepts, rule-based English parsing, rejoinders, topic control and facts offer more than simple trigger matching. Requires native build, custom scripts, lifecycle/storage integration and real device measurements. No automatic general intelligence. |
| RiveScript Java | Alternative only | Easier JVM-shaped integration and structured authoring, but still authored patterns and responses. Older build uses JCenter and obsolete Gradle configurations; Java compatibility alone does not prove modern Android compatibility. Use core only if selected. |
| ChatterBot corpus | Small editorial source | Inspected conversations contain useful openings, but also movie dialogue, invented human hobbies/identity and unrelated follow-up chains. Extract concepts/examples, rewrite responses and attach explicit conditions. Do not serve raw nearest-neighbour pairs. |
| Existing dictionary assets | Reuse read-only | Reuse sense-specific synonyms and definitions for supported concepts. Broad synonym expansion can confuse senses such as bank or interest. A word relation does not establish a fact about the user. |
| Room FTS4 | Conditional later addition | Useful lexical retrieval over curated explanation cards; does not resolve pronouns, negation or semantic similarity. Start with indexed aliases for small packs. No Lucene or second search runtime needed. |
| KStateMachine | Defer | Does not interpret language. Existing session already owns operational state. Inspected persistence docs require identical machine structure on restore; migration remains app work. Current upstream build uses Kotlin 2.4.20 with language 2.0, while Run uses Kotlin 2.0.21; artifact compatibility remains unverified. |
| better-parse | Defer | Useful for a future isolated formal command grammar. General language and topic knowledge remain authored. Rewriting current shared task grammar conflicts with preservation scope. |
| Recognizers-Text Java | Defer | Rule-based entity extraction can help later, but upstream still labels Java in progress. It cannot decide who/what a date refers to. Keep Run's working dates intact. |
| SymSpell JVM | Defer | Consider only after measuring conversational typo failures. ChatScript already has optional spelling processing, so two normalizers could compound errors. Protect quotes, acronyms, names and numeric values. |
| ConceptNet subset | Later, evidence-driven | Relations can enrich a supported knowledge pack; bulk import adds ambiguity and size without guaranteeing appropriate replies. No embeddings under the no-trained-model constraint. |
| Taskmaster / SGD / MultiWOZ | Design/reference material | Useful for corrections, interruption and multi-turn scenario design. They do not supply Run's personality or an inference engine. |
| Rosie / AIML / Program-Y | No extra runtime | Content adaptation remains substantial; another interpreter duplicates ownership and memory. |
| Tracery | Design ideas only | Conditional wording variation can live in ChatScript or Kotlin; a JavaScript runtime is unnecessary. |
| Rasa / Botpress / ChatterBot engine / trained NLP stacks | Exclude from this scope | Runtime, model and deployment assumptions do not fit the stated Android/offline/no-trained-model requirement. |
| Choco / Timefold / OptaPlanner | Exclude from this work | Scheduling is already accepted and these do not improve general conversation. |

### ChatScript details that matter before adoption

- SRC/common.h has Android feature exclusions and NOMAIN embedding guidance. This establishes an embedding path, not a maintained drop-in Android AAR or a tested Run build.
- Inspected RAWDATA/HARRY/simplecontrol.top tries rejoinders/current topic/keyword topics and eventually generic quibbles. It enables spell checking, date merging and parsing in the sample. Use a custom controller with deliberate abstention and controlled normalization; do not ship Harry's fallback behavior.
- RAWDATA/HARRY/childhood.top includes a fictional childhood and unsuitable stock remarks. The sample bot is not Run's persona or knowledge pack.
- SRC/userSystem.cpp has global user variables, filesystem user-state/backup paths and logging. Integrate one serialized runtime; bound work and avoid the UI thread. Route state through an explicit versioned persistence bridge and disable content logs by default. Clearing Run memory must also clear native caches/state.
- Android native libraries must support the app's arm64-v8a and x86_64 packaging and current 16-KiB page-size requirements. Measure release-build startup, memory, package increase, per-turn time and resume behavior; upstream desktop speed claims are not phone results.
- A JNI fatal crash is not caught by Kotlin try/catch, and coroutine cancellation does not automatically interrupt native computation. Require native work limits; consider a separate bound service process if reliable failure containment requires it. Do not promise a timeout/fallback without proving the mechanism.
- Engine snapshots and native state must not advance independently of the committed conversation frame. Include migration/reset behavior when dialogue packs change; do not replay app actions on restore.

## How and where to implement

### 1. Preserve boundaries before introducing a runtime

Add a general-chat facade under util/assistant/conversation (suggested new location). Inputs: original text, a bounded conversational frame and read-only preferences. Outputs: Reply, Clarify or NotHandled, with a frame delta and actual selected rule ID. No AssistantAction, DB writer, CLI redirect or Plan mutation capability is exposed to this runtime.

At existing general-conversation dispatch points in OfflineAssistant, use a narrowly scoped owner gate. Explicit modes, active planning and pending native questions continue through their current owners. A successfully handled conversational turn must produce a response so it cannot fall into BubbleChatActivity's task-creation route. Keep native commands recognized by existing code; do not replace AyvaIntentRouter globally in this work.

Keep GeneralConversation.handoff/archive compatibility and the existing encrypted persistence conventions. Retain shared planning helper implementations unchanged. New conversation-only normalization and knowledge lookup must not alter PlanLanguage, ConversationMeaning or existing knowledge semantics. Preserve existing explicit plan handoff; do not add new automatic handoff triggers.

### 2. Add memory that can support replies

Represent speaker, subject/entity, assertion, negation, tentative status, source turn and correction target. Store topic stack, last assistant answer act/content ID, unresolved conversational question and recent response-act IDs. Preserve original user wording alongside extracted facts. Facts outside supported extraction remain quoted notes, not confidently inferred relationships.

Handle "that", "the other one", "why", "shorter" and "actually" using both speaker turns and the selected frame. Ask one specific clarification when multiple referents fit. Explicit durable memory remains inspectable/deletable; conversational recall must not grant action authority. Preserve existing Plan handoff output semantics even if richer conversational context is stored separately.

### 3. Author complete topic packs

Start with identity/greetings, everyday check-ins, study stress, interests, work frustration and bounded comparisons. Each pack needs entry patterns/concepts, follow-ups, corrections, negation, interruptions, resumptions, unknown-detail handling and termination. Define answer acts: acknowledge, explain, compare, reflect, clarify and close. Prefer a direct response; ask only when useful, at most one optional question in a reply.

Persona: warm, concise, practical, occasional dry humour; Run is the app, Ayva is its assistant. Retain truthful answers about its authored offline nature. Vary wording after choosing meaning. Track repeated acts and content, not just exact reply text. Do not append humour to distress or turn every exchange into advice.

### 4. Prove ChatScript on these packs

Use the same packs and conversation scenarios to compare the current native path with the restricted native prototype. Require a demonstrated improvement in contextual replies and recovery, not just successful JNI compilation. Ship only one engine. If ChatScript fails, retain the facade/memory/content work and evaluate RiveScript core against the same requirements.

### 5. Expand useful knowledge selectively

Store reviewed explanation cards with aliases, topic, prerequisites, exclusions, examples, concise/detailed versions and provenance. Retrieval proposes candidates; topic, answer type and negation decide whether a card is applicable. Add FTS only when small alias indexes stop being sufficient. Keep current app-help, dictionary and native feature handling as their existing owners.

## Necessary validation and honest limits

No tests were run for this documentation-only review. For implementation, essential checks are: existing Plan transcripts/state remain identical; general chat cannot write tasks or clear pending reviews; mode/handoff/clear/resume behavior is preserved; native persistence and real-device lifecycle work. Use a small set of complete general-chat conversations, including unseen paraphrases, rather than hundreds of one-line response assertions.

Examples: "I like quiet evenings" -> "Crowds wear me out" -> "What could I try?" -> "Nothing outdoors" -> "Why that?"; explain a supported concept -> example -> shorter -> disagree; correct a named detail after an interruption; ask an unsupported factual question and then change subject. Check actual state and relevance, not just polished wording.

The achievable result is a substantially better authored offline conversational assistant on covered subjects. Unfamiliar topics, sarcasm, long ambiguous references, mixed languages, implicit causal reasoning and current-world questions remain difficult. Neither a corpus nor a rule engine removes these limits. Content authoring and maintenance are continuing work. No throughput, APK-size, battery or memory result has yet been measured for the proposed runtime.

## Evidence links

- [ChatScript Android/embedding switches](https://github.com/ChatScript/ChatScript/blob/master/SRC/common.h)
- [ChatScript sample control](https://github.com/ChatScript/ChatScript/blob/master/RAWDATA/HARRY/simplecontrol.top)
- [ChatScript sample persona](https://github.com/ChatScript/ChatScript/blob/master/RAWDATA/HARRY/childhood.top)
- [ChatScript user storage](https://github.com/ChatScript/ChatScript/blob/master/SRC/userSystem.cpp)
- [KStateMachine persistence](https://github.com/KStateMachine/kstatemachine/blob/master/docs/pages/persistence.md)
- [RiveScript Java](https://github.com/aichaos/rivescript-java)
- [ChatterBot corpus sample](https://github.com/gunthercox/chatterbot-corpus/blob/master/chatterbot_corpus/data/english/conversations.yml)
- [Recognizers-Text platform coverage](https://github.com/microsoft/Recognizers-Text)

Prior 45-project source catalogue remains in OFFLINE_CONVERSATION_SOURCES_2026-10-25.json. This follow-up re-read application call sites and fetched additional upstream source files; it did not build any proposed runtime.
