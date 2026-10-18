# Conversation language — batch 2

This batch builds on the continuity work in `AYVA_CONVERSATION_CONTINUITY.md`. It extends the deterministic offline coordinator; no model, server, dependency or database migration is introduced. The chat layout, composer, cats, briefs, Drill and other native learning windows are unchanged.

## Supported conversations

- Polite requests share an anchored normalization path across chat, the router and NLU: “Could you please add task buy milk tomorrow at 6pm?”, “I'd like you to remind me to…”, and “Would you mind moving it to Friday?”
- Negated actions such as “don't delete it” and tentative actions such as “maybe delete it” do not execute. They invalidate an unrelated pending confirmation. Guidance requests such as “help me delete it” remain questions.
- “Thanks”, “got it” and similar acknowledgements do not become reminder titles, task edits, setting values or confirmation. Ayva reminds the user what answer is still needed. “Yes, thanks” is an explicit affirmative answer; “yes but move it…” is a new request. “Not yet” keeps a preview available without applying it.
- “Change it” asks which field to edit: date, title or priority. The workflow retains the selected task IDs and versions. Title and priority changes produce a fresh confirmation; dates use the existing date workflow. It also supports an explicitly selected group. An unclear date such as “after lunch” asks for a day and time instead of guessing.
- Edit questions can be interrupted and resumed with “back to that reminder.” Changes made outside the conversation invalidate the edit rather than silently overwriting newer data.
- A quoted title is literal, including words that normally count as acknowledgement or commands. Explicit creation such as `/create thanks` remains available. User task titles and emoji are preserved.
- Unrecognized requests get one focused question and at most two suggestions, including resuming a valid unfinished question. Repeated unrecognized input offers concrete examples. A fallback response uses the conversation topic rather than pretending to be a feature explanation.

## Reply preferences and boundaries

“Be concise”/“more detail” and “no emoji”/“use emojis” are independent preferences in the existing personalization store. They control the new help, suggestions and confirmation buttons. They never truncate task results or confirmation facts. Existing native cards and legacy feature explanations retain their own formatting. “Forget my preferences” clears these preferences through the existing path.

The grammar supports explicit patterns, not arbitrary English understanding. Ambiguous targets and unsupported values require clarification. Notes and vault contents remain outside chat. Previews still use typed actions, token expiry, version checks, receipts and undo.

## Verification

`AssistantConversationLanguageTest` adds 33 complete conversation checks against Room, including polite requests, negation, guidance, courtesy during unfinished requests, independent reply preferences, unknown-input recovery, guided edits, interruption, literal titles and stale targets. The targeted run passed all 190 tests across five suites. The full suite passed 638 tests across 47 suites. Both runs had zero failures, errors or skips.

Debug and optimized release APK assembly passed in the same final Gradle run. The release APK is unsigned; app metadata remains 2.0.7 (107). The final log is `/workspace/scratch/ayva-language-verified.log`. An earlier cloud restart interrupted validation; the recorded final run completed successfully. `git diff --check` also passed.

Cloud commands use `/workspace/toolchains/ayva-env.sh` and the external test JVM init script documented in `AYVA_CHAT_DESIGN.md`. No Android device is attached, so verification here covers automated conversations and builds, not on-device interaction.
