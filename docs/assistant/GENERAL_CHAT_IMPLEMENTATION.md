# General conversation implementation

## Implemented code in this change

GeneralConversation now delegates supported conversation to GeneralDialogue, a capability-free Kotlin engine. GeneralDialoguePacks contains original English content for unwinding, feelings, study stress, work frustrations, reading, music and cooking. Existing authored knowledge explanations also use the new follow-up context. No models, network calls or database migration were added. A restricted ChatScript worker now supplies additional general-language meanings when Kotlin cannot handle a turn.

The engine receives only text, a copied bounded JSON frame and style preferences. It returns a reply and updated frame. It cannot obtain a repository, create a task, change a habit or mutate a plan. The existing GeneralConversation adapter owns persistence and the established archive/handoff representation. The source boundary checker now prevents app/session/action capabilities from being imported into either new engine file.

### Behavior

- Supported preferences are stored by dimension, with original wording and tentative status. A setting correction does not erase unrelated company/atmosphere preferences.
- Tentative alternatives stay separate from firm preferences. Both-settings alternatives do not select one automatically.
- Corrections with no identifiable target ask one specific question instead of deleting the most recent note.
- Suggestions respect firm setting/energy constraints. Rejecting a named suggestion, or requesting another, excludes it from subsequent alternatives in the current frame.
- Why/example/shorter/more follow-ups refer to the last supported explanation, reflection or suggestion.
- Topic-specific reflections avoid recently used wording and respect listening/question/tone preferences.
- Summaries show current structured details and bounded notes. Current general-chat recall can last seven days without extending task references or confirmation authority. Explicit forgetting clears the frame through the existing controls.
- Unsupported questions inside a supported frame produce an honest conversational response instead of falling through to task creation.
- Questions, quoted examples and explicitly third-party statements do not overwrite user preference fields. Common negative setting phrases are recognized.
- A tentative preference can be confirmed with “use that preference” or dropped with “forget that possibility”; ambiguous candidates request clarification.
- Replies without an explainable answer invalidate stale follow-up references. Correcting a multi-fact sentence preserves the unaffected facts in the legacy notes/handoff view.

## Preserved boundaries

Plan implementation, shared date parsing, shared clause parsing, native task/habit executors, planner UI and Chat's existing dated-task priority remain unchanged. The new adapter declines Plan mode and pending native questions. Dictionary lookup remains with the existing dictionary owner; it is not used for unrestricted synonym substitution. Existing explicit handoffs retain their interface and validity checks.

## Validation

The preceding integration check passed 103 tests with zero failures/errors/skips, selected from:

- GeneralDialogueIntegrationTest (eight complete context/isolation scenarios).
- NativeGeneralDialogueTest (two protocol and input-boundary checks).
- TripChecklistConversationTest.
- ConversationQualitySafetyTest.
- AssistantConversationLanguageTest.

These include real coordinator calls and Room assertions for zero unintended task creation, unchanged plan state while talking, preserved native pending input, expired action authority, correction/negation, tentative alternatives, response continuity and forgetting. The chat dependency guard and git whitespace check also passed. An installed-APK API 29 x86_64 instrumentation check exercised native meaning lookup, unknown/negated input and scratch cleanup successfully. No full-suite audit or manual Android UI audit was conducted.

## Native engine decision

ChatScript at `9f5eec4736ba22bd992a6498c1e0052e2a795125` is now packaged as a restricted ARM64/x86_64 subprocess, with original English meaning rules. Kotlin retains the only conversation state and all responses. The adapter accepts only whitelisted meaning tokens and has no session, database or action APIs. It skips main-thread calls, 32-bit processes and Android versions below API 26; those retain Kotlin behavior.

The worker executes from the installed native library directory, starts fresh each turn, disables user logs/cache, and forbids regular-file writes before reading user text. It receives bounded text over stdin. A native CPU limit, Java timeout, failure cooldown and process termination protect the caller. This is crash containment, not a separate-UID security sandbox. Engine allocations are configured but there is no hard RSS ceiling.

Public assets are hash checked and extracted atomically into private no-backup storage with path/size limits. Scratch is removed after each invocation and on recovery from process death. No native conversation state survives; existing encrypted Kotlin sessions and forgetting controls remain authoritative. The package includes provenance, hashes, licenses and rebuild scripts. Native libraries use 16-KiB ELF alignment. See `tools/experiments/chatscript/README.md` for rebuild and device-check details.

KStateMachine, better-parse, new date recognizers, SymSpell, ConceptNet and scheduling solvers remain deferred per the revised recommendation. The current authored pack is small enough to use direct concept lookup; Room FTS is unnecessary at this size. ChatterBot sample content was reviewed as editorial research, not imported as a response database.

## Scope completion

The selected implementation scope is complete: the general-only adapter, structured
conversation memory, authored content packs and follow-ups, original native meaning
rules, Android packaging, process lifetime/timeout fallback, private storage cleanup,
and rebuild/provenance tooling. Existing dictionary lookup remains read-only under
its existing owner. Plan behavior and shared operational parsers remain preserved.

The coordinator may consult general conversation twice for one message. Native
classifications, including abstentions, are now reused for ten seconds in a bounded
four-entry process-local cache. Keys are SHA-256 digests; no input wording or
conversation frames are retained. Responses are always generated using the current
Kotlin frame. Nonzero native exits now enter the same one-minute cooldown as timeouts
and other failures, preventing repeated crash/startup loops. Forgetting conversation
memory still uses the existing encrypted-session controls; classification caching
cannot restore facts or replies from a prior frame.

Libraries explicitly deferred in the reviewed recommendation remain out of scope,
rather than incomplete integrations. Physical ARM64 and Android 16 validation needs
an appropriate device; the cloud emulator evidence above covers API 29 x86_64.

## Limits and next steps

This improves covered conversation; it does not add open-ended language-model understanding. Arbitrary subjects, complex negation, mixed languages and ambiguous references remain limited. New topics require authored replies and continuation/correction rules. Existing legacy general-chat paths still handle topics outside these packs, and may retain their older limitations. Normal Chat retains date-to-task behavior; Talk is appropriate for dated discussion.

Further topic coverage should include complete conversation examples. Physical ARM64 latency/memory and Android 16/16-KiB runtime checks remain release-device validation work. The assets add approximately 16 MB compressed plus roughly 2 MB per ABI before APK compression. Kotlin remains the first path to avoid native startup on recognized turns. No native task permissions belong behind this language boundary.

The final runtime completion change adds one focused cache/cooldown recovery case. NativeGeneralDialogueTest and GeneralDialogueIntegrationTest passed all 11 cases with no failures, errors or skips. The debug APK build, source-boundary guard and whitespace check also passed. No broad audit was run.
