# Selected Ayva chat design

The native chat now uses the choices from the part-by-part review:

- Compact header with the anchored dropdown (controls A) and separate, local conversation search. Notification Colors, Text Size, Clear History and Open Settings retain their destinations. Clearing history asks for confirmation.
- Open assistant replies and small user bubbles. Ordinary text remains selectable and uses the existing 12–24 sp setting.
- Message actions B + D: a visible menu beside the timestamp provides Copy, Reply and Details; a rightward swipe also prepares a reply. Replying never sends text or applies an action. A quotation appears above the unchanged composer and can be cancelled. The sent quote is retained in the existing encrypted transcript payload.
- Context B + C: the active task/habit chip reflects the encrypted `AssistantSession`. The chip can switch or release the reference. Questions and action replies can carry frozen item/time references, while routine replies keep their context out of the visible prose. Reading the chip does not renew the session.
- Feedback C: successful task mutations have an action receipt containing the saved result, previous value when available, and a scoped Undo while its session token is valid. Snapshots do not change when an item is edited later. Partial batches are labelled accordingly; failed operations do not get a success receipt.
- Spacing D: related details stay close; ordinary conversation turns have a larger gap. Native feature cards retain their previous spacing.

## Preserved surfaces

`AyvaComposerBar`, the blank input, in-box slash/mic, command shortcuts, suggestions, priority/persistence, live transcription and send behavior are reused. Welcome cats, the peeking/floating cats, their assets and reactions are unchanged. Native morning/evening briefs, Drill/Blitz test windows, summaries, solutions, habit/task/vocabulary cards, quests, mystery boxes and streak features keep their renderers and actions. App blocking/overlay services are outside this change.

The Working / Ask / Saved / Failed preview switches are not app controls. There is no artificial typing delay. A small working indicator appears only while a real request is in flight.

## State and data handling

The existing encrypted `AssistantSession` remains authoritative. Its lifetime remains 15 minutes. The UI polls a read-only projection, and confirmation/undo clicks still pass through the executor's token and version checks. A reference selection validates the ID against the database; copied titles or transcript JSON are never mutation authority. Releasing a reference clears pending conversation questions, rather than deleting tasks, habits or history.

Replying to the current question can preserve its pending slot. Quoting an older reply may select its original still-existing item, but cannot revive a historical confirmation. Unavailable quoted items ask the user to choose again. Undo always uses the executor's original target, even after the topic changes.

Frozen reference, receipt and quote metadata use a versioned `presentation` object within the existing encrypted message payload. No database migration or new plaintext store is introduced. Search examines stored message text on this device and jumps to the actual timeline item; it neither filters out native feature cards nor queries private notes or an external service.

## Verification

Integration tests cover snapshot history, reference validation/release/expiry, answering a quoted date question, historical replies, scoped undo and the existing message-persistence conversions. Compose interaction tests cover the menu, swipe versus vertical gestures, expired Undo, header controls, local search and cancelling a reply. Physical Android device testing remains necessary for keyboard, accessibility, gesture feel and cat/native animation rendering on the target device.


Final cloud verification: **559 tests passed, zero failures/errors/skips** across 43 suites, including 13 new conversation/presentation integration tests and 6 new Compose interaction tests. Debug and minified release APK assembly passed. All original animation asset hashes and the protected composer/cat/full-screen Drill source files were verified unchanged.

The cloud JDK 21 test worker encountered a HotSpot C2 compiler crash while compiling the existing `FocusStatsManager.refreshStats` method. The full suite was rerun successfully with test-worker-only `jvmArgs '-XX:TieredStopAtLevel=1'` supplied by an external Gradle init script. No tests, assertions or application optimizations were disabled; production Gradle configuration is unchanged. The successful build/test log is `/tmp/ayva-chat-design-passed.log` in this workspace.

No Android device or emulator was connected (`adb devices -l` returned an empty list). Visual, keyboard, accessibility and native animation checks on the target Android device remain outstanding.
