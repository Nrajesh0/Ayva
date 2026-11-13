# Chat UI and app-control implementation history

Preserved UI, context, authorization and app-control contracts from their original
implementation checkpoints. Dated verification is historical evidence. The
[assistant status](../STATUS.md) describes the maintained handoff.

## Contents

- [Selected Ayva chat design](#chat-design)
- [Response controls and app/settings conversation](#response-controls-and-app-help)

<a id="chat-design"></a>

<a id="chat-design-selected-ayva-chat-design"></a>

## Selected Ayva chat design

The native chat now uses the choices from the part-by-part review:

- Compact header with the anchored dropdown (controls A) and separate, local conversation search. Notification Colors, Text Size, Clear History and Open Settings retain their destinations. Clearing history asks for confirmation.
- Open assistant replies and small user bubbles. Ordinary text remains selectable and uses the existing 12–24 sp setting.
- Message actions B + D: a visible menu beside the timestamp provides Copy, Reply and Details; a rightward swipe also prepares a reply. Replying never sends text or applies an action. A quotation appears above the unchanged composer and can be cancelled. The sent quote is retained in the existing encrypted transcript payload.
- Context B + C: the active task/habit chip reflects the encrypted `AssistantSession`. The chip can switch or release the reference. Questions and action replies can carry frozen item/time references, while routine replies keep their context out of the visible prose. Reading the chip does not renew the session.
- Feedback C: successful task mutations have an action receipt containing the saved result, previous value when available, and a scoped Undo while its session token is valid. Snapshots do not change when an item is edited later. Partial batches are labelled accordingly; failed operations do not get a success receipt.
- Spacing D: related details stay close; ordinary conversation turns have a larger gap. Native feature cards retain their previous spacing.

<a id="chat-design-preserved-surfaces"></a>

### Preserved surfaces

`AyvaComposerBar`, the blank input, in-box slash/mic, command shortcuts, suggestions, priority/persistence, live transcription and send behavior are reused. Welcome cats, the peeking/floating cats, their assets and reactions are unchanged. Native morning/evening briefs, Drill/Blitz test windows, summaries, solutions, habit/task/vocabulary cards, quests, mystery boxes and streak features keep their renderers and actions. App blocking/overlay services are outside this change.

The Working / Ask / Saved / Failed preview switches are not app controls. There is no artificial typing delay. A small working indicator appears only while a real request is in flight.

<a id="chat-design-state-and-data-handling"></a>

### State and data handling

The existing encrypted `AssistantSession` remains authoritative. Its lifetime remains 15 minutes. The UI polls a read-only projection, and confirmation/undo clicks still pass through the executor's token and version checks. A reference selection validates the ID against the database; copied titles or transcript JSON are never mutation authority. Releasing a reference clears pending conversation questions, rather than deleting tasks, habits or history.

Replying to the current question can preserve its pending slot. Quoting an older reply may select its original still-existing item, but cannot revive a historical confirmation. Unavailable quoted items ask the user to choose again. Undo always uses the executor's original target, even after the topic changes.

Frozen reference, receipt and quote metadata use a versioned `presentation` object within the existing encrypted message payload. No database migration or new plaintext store is introduced. Search examines stored message text on this device and jumps to the actual timeline item; it neither filters out native feature cards nor queries private notes or an external service.

<a id="chat-design-verification"></a>

### Verification

Integration tests cover snapshot history, reference validation/release/expiry, answering a quoted date question, historical replies, scoped undo and the existing message-persistence conversions. Compose interaction tests cover the menu, swipe versus vertical gestures, expired Undo, header controls, local search and cancelling a reply. Physical Android device testing remains necessary for keyboard, accessibility, gesture feel and cat/native animation rendering on the target device.


Final cloud verification: **559 tests passed, zero failures/errors/skips** across 43 suites, including 13 new conversation/presentation integration tests and 6 new Compose interaction tests. Debug and minified release APK assembly passed. All original animation asset hashes and the protected composer/cat/full-screen Drill source files were verified unchanged.

The cloud JDK 21 test worker encountered a HotSpot C2 compiler crash while compiling the existing `FocusStatsManager.refreshStats` method. The full suite was rerun successfully with test-worker-only `jvmArgs '-XX:TieredStopAtLevel=1'` supplied by an external Gradle init script. No tests, assertions or application optimizations were disabled; production Gradle configuration is unchanged. The successful build/test log is `/tmp/ayva-chat-design-passed.log` in this workspace.

No Android device or emulator was connected (`adb devices -l` returned an empty list). Visual, keyboard, accessibility and native animation checks on the target Android device remain outstanding.

---

<a id="response-controls-and-app-help"></a>

<a id="response-controls-and-app-help-response-controls-and-appsettings-conversation"></a>

## Response controls and app/settings conversation

The task-list C and settings/help B+C choices apply to ordinary conversation
responses. Task-added/reschedule receipts, native task cards, the chat composer,
cats, morning/evening brief cards and separate test windows retain their renderers.

<a id="response-controls-and-app-help-response-controls"></a>

### Response controls

- Natural task lists carry bounded, inert display metadata with stable task IDs.
  Tap a row to select conversational focus; this does not edit the task. The
  selected row offers completion/reopening and changing its time, through the
  existing coordinator. Live repository data disables removed items. Lists expire
  after 15 minutes; ordinals retain the displayed order.
- Information replies promote one relevant action; secondary actions are in More.
  Confirm/cancel, disambiguation choices and task receipts keep their existing
  controls. Legacy preference buttons are converted to validated preview queries.
- Supported settings show a live current-value picker with presets and custom
  integer/time entry. Selecting a replacement creates a preview showing current
  and proposed values. It never saves from the UI. Existing confirmation nonces,
  value-change guards, validation and undo still apply.

<a id="response-controls-and-app-help-app-help-and-intent-boundaries"></a>

### App help and intent boundaries

`/talk` (also `/ask`) explicitly asks about the app or requests supported settings
changes. It cannot create tasks implicitly. Ordinary messages retain natural task
creation, and `/create` explicitly creates a literal task. Existing explicit task
management commands remain supported for compatibility with saved controls.

`AssistantAppKnowledge` resolves settings before task-edit and creation heuristics.
Questions such as “how do I turn it off?” are read-only. “Turn it off”, “change it”
and a requested numeric value can continue the last setting. A new app topic
clears the old live task reference. An app question cannot confirm an old task
preview. Context continues to use the existing encrypted, expiring session.

Examples:

- `/talk settings` -> browse blocking, reminders, bubble, appearance and privacy.
- `/talk how does soft mode wait work?` -> current value, explanation and range.
- `what is the range?` / `where is it?` -> answers about that setting.
- `make it 30 seconds` -> preview, then confirm or cancel.
- `/talk practice reminder time` -> actual clock value or randomized schedule.
- `/talk notes vault PIN` -> explains vault PIN/recovery separately from blocking.
- `check device health` -> current permission/service checks, not guessed status.

The allowlist in `AssistantPreferences` owns values, ranges, defaults, storage
owners, formatting and preview commands. Catalogue text lives in
`ayva_knowledge_base.json`. New editable settings should have a matching catalogue
entry, a real native consumer, and validated confirmation-based execution. Read-only
typo matching never authorizes writes. Unknown app requests receive bounded help.
Android permissions, vault secrets and backup operations use their dedicated UI.

Corrected native parity includes notification style and launch-tab choices, bubble
preference ownership, practice reminder random/legacy defaults, soft-mode limits,
and the evening schedule fallback (8 PM, matching Bubble Settings). Documentation
also avoids claiming automatic cloud sync, a universally absent PIN, or guaranteed
notification delivery. Smart Regex Parsing is explicitly described as an existing
flag not consumed by the current chat coordinator.

<a id="response-controls-and-app-help-verification"></a>

### Verification

Focused integration tests cover mode boundaries, interrupted task context,
question/change separation, authoritative preference reads, randomized practice
scheduling, confirmation guards, list IDs and Android notification navigation.
Compose tests exercise selection, action menus, preview-only pickers and visible
confirm/cancel controls. Existing appearance tests continue to protect dark/light
contrast and saved receipts. No on-device speech or overlay behavior is claimed
from these JVM checks.

Verified on 2026-10-18: all 819 JVM/Compose tests passed, with zero failures,
errors or skipped tests. `:app:assembleDebug` and `:app:assembleRelease` both
completed successfully, including release R8/resource optimization. The release
APK is unsigned in this environment. These checks do not substitute for an
Android 16 device check of speech recognition, notifications or overlay delivery.

<a id="response-controls-and-app-help-manual-locking-and-unlocking"></a>

### Manual locking and unlocking

Chat resolves `lock all` from the complete eligible launcher-app catalogue, rather
than the subset that already has saved rules. `unlock all` also disables old saved manual rules, including entries without a
launcher or a currently installed app. It can clear an old manual rule for an
essential app; it never creates a new lock on that app. Ayva, launcher/home screens, current keyboard, dialers/emergency calling
and system controls remain protected.

Examples:

- `lock all except WhatsApp and Instagram`
- `block social apps except WhatsApp`
- `unlock all except Instagram`
- `except WhatsApp`, `include YouTube`, `also lock Reader`
- `make them soft`, `switch them to strict mode`
- `keep Instagram locked`, `leave WhatsApp unlocked`
- `show locked apps`, `which ones`

All changes have a named-app/package preview and confirmation. An initial
exception leaves its old manual rule unchanged. Revising an already-applied group
shows any required unlock/lock of removed members as a new preview. Merely
selecting, reporting or revising a preview changes no saved app rules. Exact app
names or packages are required for writes; ambiguous names and unknown exceptions
invalidate an older pending confirmation and ask for clarification. Selections
are bounded at 500 apps, with an explicit refusal rather than truncation. A timed
expiry is never implied by a manual rule.

`SetAppRules` validates every current snapshot and active strict/focus protection
before a single transaction saves the batch. Undo checks the entire batch before
restoring it. Switching topics, canceling, undoing or expiration releases the bulk
follow-up context. Existing routine/focus rules continue to govern effective
blocking, so “manual unlock” does not claim a protected routine was bypassed.
The WindowManager blocking mechanism is unchanged.

<a id="response-controls-and-app-help-shared-app-selection-data"></a>

### Shared app selection data

The manual-shield plus picker, routine selector, custom category editor and chat
use `AppCatalog`. The UI layout and category chips are unchanged. The catalogue
filters disabled/protected apps and deduplicates multiple launcher activities by
package. Curated package namespaces override misleading generic Android metadata;
word fragments such as `ea`, `king`, `pay` and `store` no longer classify unrelated
apps. Unknown apps retain a fallback category. Categories are best-effort metadata,
not an assertion about how a person uses an app.

Sorting uses locale-aware, case-insensitive label comparison with package ties.
Existing search fields trim whitespace, ignore accents, match all query tokens
against label/package, and rank exact/prefix label matches ahead of package-only
matches. Category/custom-group and search filters combine, so Select All still
acts on exactly the displayed filtered list. Editing a selected custom category
refreshes its membership immediately; category selection tracks its stable ID,
so a name or membership edit does not lose the selected chip.
