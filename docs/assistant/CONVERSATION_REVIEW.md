# Scripted conversation review

Generated on 2026-10-19 from the actual coordinator/reply engine using six synthetic dialogs (38 turns). Base: `9015eaf`; subsequent reply-copy fixes are recorded in STATUS.md and Git history. Robolectric API 28 with in-memory task/vocabulary stores; no Notes fixture or real user data. Native handoffs were recorded without launching test windows.

This is developer-authored exploratory evidence, not held-out human evaluation or Android 16 device testing. The scratch-only collector is excluded from the app and permanent unit suite. Existing regression assertions also checked real setting undo, plan saves, bookmarks, stale controls and task state. The after/before comparison found identical controls, native handoffs and final task rows across all 38 turns.

The review corrected domain-irrelevant reward text on setting undo, repeated future-tense planning instructions after answers were complete, an unnecessary Notes reference on bookmarking, and routine Android-delivery commentary on an otherwise successful save. Real reminder-refresh failures still report retry instructions. Task completion/reward undo text and protected receipt/cards were retained.

For human review later, assess relevance, repetition, warmth and whether each next action matches the request. Physical checks remain in [DEVICE_ACCEPTANCE.md](DEVICE_ACCEPTANCE.md). These exact scripted inputs do not establish arbitrary-English understanding.


## Listening, then an explicit reminder

**You:** I had a rough day

**Ayva:** That sounds like a lot to carry. Would you rather talk about it, have a small distraction, or just be heard?

Controls: Just listen, A distraction

**You:** just listen

**Ayva:** I'm listening. Say as much or as little as you want; I won't turn this into a task list.

**You:** I feel overwhelmed

**Ayva:** That sounds hard. I'm listening; you don't have to turn it into a plan.

Controls: Just listen, A distraction

**You:** remind me to call Mum tomorrow at 6pm

**Ayva:** Added **Call Mum** — Oct 20, 6:00 PM.

Controls: Undo last change, Change time

Final task rows: [{"title":"Call Mum","done":false,"details":"","children":[]}]

## Trip planning, corrections, conflict and reviewed checklist

**You:** help me plan my trip to Goa next Friday

**Ayva:** Planning · trip

Goal: to Goa next Friday
I'll keep these details in the saved task descriptions.

What preparation steps do you want? Separate them with commas or semicolons, or choose the optional trip checklist. I can't check current visas, weather, prices or bookings.

Deadline: none · Etc/UTC.

Nothing is saved until you use Add tasks in this review.

Controls: Use optional trip checklist, Cancel planning

**You:** steps: Pack bag; Book train; Charge power bank

**Ayva:** Planning · trip

Goal: to Goa next Friday

Should these tasks share a deadline? Give a day and time, or say ‘skip’ for an undated plan. A deadline is not a scheduled work interval.

1. Pack bag — no due date
2. Book train — no due date
3. Charge power bank — no due date

Deadline: none · Etc/UTC.

Nothing is saved until you use Add tasks in this review.

Controls: Skip for now, Cancel planning

**You:** Friday at 6pm

**Ayva:** Planning · trip

Goal: to Goa next Friday

How much time is available for this plan? Give 1–480 minutes, or ‘skip’ for a checklist without a time budget. I won't assume how long your steps take.

1. Pack bag — Oct 23, 6:00 PM
2. Book train — Oct 23, 6:00 PM
3. Charge power bank — Oct 23, 6:00 PM

Deadline: Oct 23, 6:00 PM · Etc/UTC.

Nothing is saved until you use Add tasks in this review.

Controls: Skip for now, Cancel planning

**You:** 90 minutes

**Ayva:** Planning · trip

Goal: to Goa next Friday

Review the titles and shared deadline below. Choose one checklist or separate tasks; you can remove, rename or reorder an entry before saving.

1. Pack bag — Oct 23, 6:00 PM
2. Book train — Oct 23, 6:00 PM
3. Charge power bank — Oct 23, 6:00 PM

Deadline: Oct 23, 6:00 PM · Etc/UTC. Available: 90 minutes for the whole plan.
Completion time is unknown; the budget doesn't prove these steps fit.

Nothing is saved until you use Add tasks in this review.

Controls: One checklist, Add 3 task(s), Cancel planning

**You:** effort: 120 minutes

**Ayva:** Planning · trip

Goal: to Goa next Friday

Review the titles and shared deadline below. Choose one checklist or separate tasks; you can remove, rename or reorder an entry before saving.

1. Pack bag — Oct 23, 6:00 PM
2. Book train — Oct 23, 6:00 PM
3. Charge power bank — Oct 23, 6:00 PM

Deadline: Oct 23, 6:00 PM · Etc/UTC. Available: 90 minutes for the whole plan. Your estimated total effort: 120 minutes.
Your stated effort exceeds the available time. Remove work, change ‘effort:’ or ‘budget:’ before saving. Nothing is scheduled automatically.

Nothing is saved until you use Add tasks in this review.

Controls: One checklist, Cancel planning

**You:** budget: 150 minutes

**Ayva:** Planning · trip

Goal: to Goa next Friday

Review the titles and shared deadline below. Choose one checklist or separate tasks; you can remove, rename or reorder an entry before saving.

1. Pack bag — Oct 23, 6:00 PM
2. Book train — Oct 23, 6:00 PM
3. Charge power bank — Oct 23, 6:00 PM

Deadline: Oct 23, 6:00 PM · Etc/UTC. Available: 150 minutes for the whole plan. Your estimated total effort: 120 minutes.

Nothing is saved until you use Add tasks in this review.

Controls: One checklist, Add 3 task(s), Cancel planning

**You:** goal: Visit Kochi instead!

**Ayva:** Planning · trip

Goal: Visit Kochi instead!

Review the titles and shared deadline below. Choose one checklist or separate tasks; you can remove, rename or reorder an entry before saving.

1. Pack bag — Oct 23, 6:00 PM
2. Book train — Oct 23, 6:00 PM
3. Charge power bank — Oct 23, 6:00 PM

Deadline: Oct 23, 6:00 PM · Etc/UTC. Available: 150 minutes for the whole plan. Your estimated total effort: 120 minutes.

Nothing is saved until you use Add tasks in this review.

Controls: One checklist, Add 3 task(s), Cancel planning

**You:** one checklist

**Ayva:** Planning · trip

Goal: Visit Kochi instead!

Review the titles and shared deadline below. Choose one checklist or separate tasks; you can remove, rename or reorder an entry before saving.

1. Pack bag — Oct 23, 6:00 PM
2. Book train — Oct 23, 6:00 PM
3. Charge power bank — Oct 23, 6:00 PM

Deadline: Oct 23, 6:00 PM · Etc/UTC. Available: 150 minutes for the whole plan. Your estimated total effort: 120 minutes.

Nothing is saved until you use Add tasks in this review.

Controls: Separate tasks, Add 1 task(s), Cancel planning

**You:** Tap: Add tasks

**Ayva:** Added 1 task(s) with 3 checklist items.

Controls: Undo plan

Final task rows: [{"title":"Trip preparation","done":false,"details":"Visit Kochi instead!","children":["Pack bag","Book train","Charge power bank"]}]

## Dictionary meanings, examples, bookmark and separate quiz

**You:** define reluctant

**Ayva:** **reluctant** · adjective · meaning 1/3
not eager
Source example: foreigners stubbornly reluctant to accept our ways
Other source senses (not ranked by frequency):
1. adjective: not eager
2. adjective: disinclined to become involved
3. adjective: unwillingness to do something contrary to your custom
Source: Wordset / WordNet. Say ‘dictionary credits’ for attribution and license.

Controls: Simpler, Example, Save word, Other meaning

**You:** simpler

**Ayva:** **reluctant** · adjective · meaning 1/3
In simple English: Not eager to do something.
Source: Wordset / WordNet. Say ‘dictionary credits’ for attribution and license.

Controls: Simpler, Example, Save word, Other meaning

**You:** give an example

**Ayva:** **reluctant** · adjective · meaning 1/3
Ayva example for this sense: She was reluctant to speak first.
Source: Wordset / WordNet. Say ‘dictionary credits’ for attribution and license.

Controls: Simpler, Example, Save word, Other meaning

**You:** other meaning

**Ayva:** **reluctant** · adjective · meaning 2/3
disinclined to become involved
Source example: they were usually reluctant to socialize
Source: Wordset / WordNet. Say ‘dictionary credits’ for attribution and license.

Controls: Simpler, Example, Save word, Other meaning

**You:** save this word

**Ayva:** Save this meaning of **reluctant** to vocabulary bookmarks?
disinclined to become involved
Bookmarking won't mark it learned.

Controls: Confirm, Cancel

**You:** Tap: Confirm bookmark

**Ayva:** Saved **reluctant** to vocabulary bookmarks. This doesn't mark it learned. Wordset source and license remain available through ‘dictionary credits’.

Controls: Undo last change

**You:** quiz me on it

**Ayva:** Opening the native quiz with your bookmarked vocabulary, using this saved sense.

Native handoff: `/vocab_quiz saved ows:1`

Final task rows: []

## Current setting, explanation, reviewed change and undo

**You:** /talk soft mode wait

**Ayva:** **Soft Mode Wait Timer**
Currently **10 seconds**.
The wait on a Soft Mode block screen before its relief option becomes available. The supported range is 5–60 seconds. This is separate from the number of minutes of relief.
Supported: 5–60 seconds.

Controls: Open App Settings

**You:** what does that do

**Ayva:** **Soft Mode Wait Timer**
Currently **10 seconds**.
The wait on a Soft Mode block screen before its relief option becomes available. The supported range is 5–60 seconds. This is separate from the number of minutes of relief.
Supported: 5–60 seconds.

Controls: Open App Settings

**You:** set it to 15 seconds

**Ayva:** Change **Soft-mode wait**?
Current: 10 seconds
Proposed: **15 seconds**

Controls: Confirm, Cancel

**You:** Tap: Confirm setting

**Ayva:** Soft-mode wait: **15 seconds**.

Controls: Undo last change

**You:** what is it set to now

**Ayva:** **Soft Mode Wait Timer**
Currently **15 seconds**.
The wait on a Soft Mode block screen before its relief option becomes available. The supported range is 5–60 seconds. This is separate from the number of minutes of relief.
Supported: 5–60 seconds.

Controls: Open App Settings

**You:** change it back

**Ayva:** Restored **Soft-mode wait** to **10 seconds**.

Final task rows: []

## Task reference interrupted by a joke

**You:** buy milk tomorrow

**Ayva:** Added **Buy milk** — Oct 20, 8:00 AM.

Controls: Undo last change, Change time

**You:** tell me a joke

**Ayva:** My calendar tried stand-up comedy. Its timing was excellent, but every joke was dated.

Controls: Another joke, Try a riddle

**You:** why is that funny

**Ayva:** ‘Timing’ suits both comedy and a calendar. ‘Dated’ can mean marked with dates or no longer fresh.

Controls: Another joke

**You:** I finally finished it

**Ayva:** That sounds like a satisfying moment. What did you finish? I haven't changed any saved task.

**You:** show all tasks

**Ayva:** 1. **Buy milk** — Oct 20, 8:00 AM
Select a task to act, or tell me what to change.

Final task rows: [{"title":"Buy milk","done":false,"details":"","children":[]}]

## Boredom, interests and tone

**You:** I'm bored

**Ayva:** We could try a riddle, a tiny branching story, or a questionable joke. Pick your kind of fun.

Controls: Riddle, Story, Joke

**You:** tell me a story

**Ayva:** A cat found a tiny door beneath the sofa. A sign read: ‘Lost things, second chances.’ Would you open it or knock?

Controls: Open it, Knock

**You:** knock

**Ayva:** Someone knocked back, then opened the door. On a table lay a book titled ‘The Afternoon You Forgot to Rest’. Keep it or return it?

Controls: Keep it, Return it

**You:** return the book

**Ayva:** The cat returned the book. In its place appeared a small cushion and a note: ‘Some lost things find their own way home.’ The cat tested the cushion. The end.

Controls: Another story, A joke

**You:** be serious

**Ayva:** Understood. I'll keep this conversation straightforward.

**You:** I like music

**Ayva:** With music, is it the rhythm, the melody, the words, or the atmosphere that draws you in?

**You:** tell me more

**Ayva:** A familiar track can sound different when you follow just one instrument or voice. Do you listen closely, or enjoy music in the background?

Final task rows: []
