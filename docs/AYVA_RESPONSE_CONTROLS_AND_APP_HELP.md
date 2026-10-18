# Response controls and app/settings conversation

The task-list C and settings/help B+C choices apply to ordinary conversation
responses. Task-added/reschedule receipts, native task cards, the chat composer,
cats, morning/evening brief cards and separate test windows retain their renderers.

## Response controls

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

## App help and intent boundaries

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

## Verification

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

## Manual locking and unlocking

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

## Shared app selection data

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
