# Explicit preferences and planning — batch 4

This batch completes the four conversation-improvement batches. It adds deterministic planning and explicit preference controls without a model, network service, dependency or database migration. Chat layout, composer, cats, briefs and native learning windows are unchanged.

## Preferences you can review and remove

```
Remember that I prefer 15-minute focus sessions
Remember that I prefer 2 tasks in my plan
Remember that I prefer due dates first
What do you remember about me
Forget my focus preference
Forget my planning preferences
Forget my preferences
```

The allowlist contains name, reply detail, help/suggestion emoji, default focus minutes, plan size and planning order. Focus defaults are bounded to 1–240 whole minutes, plan size to 1–5 tasks, and ordering to priorities first or due dates first. Existing name/reply preferences remain compatible. Profile review lists only explicit settings; individual reset commands restore defaults. “Forget my preferences” also clears the conversational references, unfinished questions, undo and plan context without deleting saved app items. Clearing chat removes the conversation/plan while retaining explicitly saved preferences.

These controls use the existing local personalization preferences. New stored values are bounded numbers/enums; no arbitrary facts, passwords, inferred traits or task content are added to the profile. A rejected “remember…” request does not enter profile storage. “Remember to call Mum” still creates a task, and explicit `/create` text remains literal task creation. Existing encrypted short-lived conversation/history behavior is separate from profile storage.

An explicit duration overrides the default. Changing a preference does not resize a running timer. Both ordinary focus starts and focus sessions linked to tasks use the saved default when their duration is omitted.

## Plan with available time

```
I have 30 minutes
Why this order
Skip that one
Only today
Include future tasks
Include skipped tasks
Show my plan
```

Plans read current pending tasks, exclude completed/trashed/deleted items, and retain only displayed task IDs for ordinal follow-ups. Default priority ordering highlights overdue tasks before priorities and nearest due dates. Due-date ordering sorts nearest dates first and uses priority to break ties. “Plan my day” excludes future-dated tasks while including undated tasks. Plan size uses the saved preference. “What should I do next” selects one task.

A time budget allocates work blocks across the displayed tasks, capped by the preferred focus duration. Blocks never exceed the requested budget; any remaining time is explicitly unallocated. These are work blocks, not estimates of task completion. Plans do not change task dates, mark tasks done, create calendar events, or start a timer automatically. If a focus timer is already running, the reply reports it and offers its status instead of replacing it.

“Due dates first” or “priorities first” during planning changes that plan only. “Skip that one” temporarily excludes the selected task from the plan without postponing or completing it. “Include skipped tasks” restores those candidates. An explanation or refreshed plan re-reads live priorities, dates and completion state. Task action buttons retain explicit task IDs; old buttons cannot silently switch to the new first task.

```
Plan my time
→ How much time should the plan cover?
Half an hour
```

Planning budgets support 1–480 whole minutes, including numeric hours converted to whole minutes. Invalid or missing budgets produce a typed question. Courtesy cannot fill it. The question can be interrupted and resumed with “back to planning.” A reminder interrupted by a planning request can still resume with “back to that reminder.”

## Context and limits

Plan context lives in the existing encrypted conversation file. Its JSON fields are optional for older sessions. Plans and planning questions have their own 15-minute expiry as well as the existing conversation expiry; unrelated activity cannot keep an old plan alive indefinitely. Plans retain at most five displayed tasks and 100 temporary exclusions. Clear/forget/cancel paths remove the relevant unfinished plan. Preference changes are explicit; planning and emotional statements never infer durable preferences.

The grammar is bounded English, not open-ended reasoning. Task duration estimates, freeform long-term personal facts, dependencies and calendar availability are not inferred. Notes and vault contents remain outside chat. The app-blocking WindowManager mechanism is unchanged.

## Verification

`AssistantConversationPlanningTest` adds 51 checks for explicit persistence/reset, rejected profile data, defaults versus explicit durations, budget validation, live sorting and task filtering, immutable task data, scoped skipping, stale buttons, running focus sessions, interruption, expiry and old-session compatibility. The targeted run passed 278 tests across six suites. The full suite passed 742 tests across 49 suites. Both runs had zero failures, errors or skips.

The final combined Gradle run passed debug and optimized release APK assembly, including R8 and resource shrinking. The release APK is unsigned; app metadata remains 2.0.7 (107). The completed log is `/workspace/scratch/ayva-planning-verified.log`. `git diff --check` passed. No Android device or emulator was connected.

Cloud validation uses the existing offline Android toolchain and external test JVM init script described in `AYVA_CHAT_DESIGN.md`. On-device UI, notification and OEM alarm checks remain separate.
