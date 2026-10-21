# Ayva implementation playbook

This document defines the continuing development workflow. Use [the delivery summary](assistant/DELIVERY_SUMMARY.md) for completed work and [the conversation-quality plan](assistant/CONVERSATION_QUALITY_PLAN.md) for the next batches.

## Validation preference

The latest user instruction is **absolutely necessary tests only**. Do not add/run tests for routine prose, personality, layout or reversible low-impact changes. Run the smallest relevant check only for a concrete consequential risk (data loss, privacy, unintended writes, migrations or scheduling corruption) or a reported failure; state the reason. Use inspection/compilation as needed. No routine full-suite reruns, benchmark projects or broad device/language audits. Defer audits until requested or needed to investigate something broken. Retain existing CI and distinct regression coverage; this is not permission to disable safeguards.

## Current handoff

The original bounded implementation is **complete**. Start with [the delivery summary](assistant/DELIVERY_SUMMARY.md), then use [the conversation-quality batches](assistant/CONVERSATION_QUALITY_PLAN.md) as the sole active queue. The new batches are in progress; use [the active implementation record](assistant/implementation/CONVERSATION_QUALITY_CURRENT.md) for actual scope, check results and the next slice. Older ledger entries preserve historical evidence and are not instructions to repeat completed work.

Version metadata is 2.0.11 / 111; code was pushed to main. Hosted validation of the preceding CI-repair commit passed full unit tests and debug/release builds. Later release publication has not been rechecked during this cleanup. Inspect actual Git state and workflow results before claiming a newer checkpoint verified.

Keep necessary-test-only validation, offline/no-model operation, private-data boundaries, existing execution safeguards and WindowManager blocking. Open acceptance and expansion work are explicitly carried into the new plan.

## Start-of-session procedure

1. Read `AGENTS.md`, the current user instructions, this handoff, the relevant roadmap sections, and active slice/decision records.
2. Inspect Git status, actual HEAD, and the affected code. Preserve existing user changes. Use the existing isolated checkout unless the user explicitly requests a worktree.
3. Identify what is implemented, proposed, tested, blocked, and stale. A prior summary or passing test count is not a substitute for current code evidence.
4. Select the next dependency-ready vertical slice. State its user outcome and acceptance gate, including requirement IDs.
5. Check environment activation and only the prerequisites needed for the slice. The prepared environment uses `/workspace/.setup/java-env.sh`; inspect availability before relying on it. Use the repository-pinned Gradle and documented memory override where applicable.
6. Continue authorized work autonomously. Ask for missing information only if it materially blocks progress; prepare concrete choices for consequential product tradeoffs.

## Work in resumable vertical slices

A slice is small enough to implement, review, and validate coherently but must represent meaningful behavior. Examples: preserve a habit draft after keyboard dismissal and recreation; resume a trip plan after process death; move a referenced subtask while keeping a protected appointment fixed. “Add thirty phrases” is not an adequate slice outcome.

Record for every slice:

```text
Slice ID / title:
User journey and requirement IDs:
Current behavior and reproduction:
Target semantic state / visible behavior:
Dependencies and affected contracts:
Implementation scope and migration implications:
Regression risks:
Acceptance scenarios and necessary device checks:
Actual commands, exit status, result paths, and tested revision:
Remaining limitations or blockers:
Next dependency-ready action:
```

Use typed interfaces and testable boundaries before adding domain breadth. Complete error paths, persistence, and integration alongside the happy path. Do not mark a slice done if only the parser recognizes it but the real UI routes it elsewhere or execution loses the information.

## Durable project records

Create these records when implementation begins, and keep them concise enough to remain useful:

| Record | Contents | Update trigger |
| --- | --- | --- |
| Requirement/capability ledger | R01–R12, supported journeys, state, owner milestone, evidence links, missing dependencies | A behavior is added, deferred, tested, or found broken |
| Active slice | Reproduction, design, affected files, acceptance checks, progress | Meaningful implementation result |
| Decisions | Problem, alternatives, chosen approach, evidence, consequences, reconsideration trigger | Consequential architectural choice |
| Experiments | Hypothesis, bounded prototype, baseline, outcomes, recommended next action | Investigation or capability-expansion spike |
| Failure corpus | Anonymized/synthetic conversation fixture, clock, records, expected state, failure category | Realistic misunderstanding or regression |
| Evaluation report | Tested revision, suite/device scope, actual results, performance, limitations | Milestone/release gate |
| Handoff | Completed work, exact unresolved state, running processes, next command/action | Session boundary or significant task change |

Suggested location: `docs/assistant/implementation/`, with fixtures placed in the relevant test directories. Do not fill these folders with speculative boilerplate in advance. Do not store secrets or private conversations in repository records. Product memory and development records are separate systems.

Use explicit states: proposed, investigated, implementing, implemented-unverified, verified, blocked, deferred, and rejected. “Verified” names its tested scope. A parser unit test does not verify Android Back behavior, and an emulator run does not establish all OEM behavior.

## Discovery work during implementation

At the beginning of each slice identify at least the important uncertainties; do not invent research where the implementation is routine. At completion ask whether an observed limitation should become a bug, a reusable primitive, or an expansion experiment.

Expansion proposal template:

```text
Capability / motivating conversation:
Why the current design cannot satisfy it:
Reusable concept it would add:
Candidate no-model, offline approaches:
Smallest experiment that distinguishes those approaches:
Acceptance examples and adversarial cases:
Expected costs, dependencies, and affected existing behavior:
Decision: integrate now / schedule next / defer / reject:
Evidence and reconsideration trigger:
```

Prioritize wrong actions and lost work, then severe conversational friction, then capabilities that unlock multiple journeys, then optional breadth. If the experiment fails, document why and revise the design. Failure is useful evidence; suppressing an assertion or reducing the supported scope without recording it is not progress.

Before adopting a new library, check offline operation, Android compatibility, license, footprint, deterministic behavior, and maintenance. A no-model solver or grammar library may be appropriate; a model hidden inside a dependency violates R01.

## Validation discipline

The latest validation preference above supersedes broader testing language in historical documents and earlier batch notes.

- Inspect the relevant failure path before claiming a fix. Add or run a focused test only when absolutely necessary to establish correctness for the concrete risk or reported failure.
- Use existing checks wherever possible; do not build a new test framework or broad corpus for routine implementation.
- For the reported keyboard overlap and lost drafts, use a focused reproduction/check when feasible. Record unavailable device verification honestly; do not silently call it verified.
- Inspect diffs and compile when needed. Keep existing hosted CI enabled; do not repeatedly rerun expensive local checks without a new reason.
- Record checks actually run, failures and unverified limitations. A build is not proof of conversational quality.
- Preserve unrelated changes and distinct existing tests. Deletion needs an equivalent retained-coverage mapping.
- Broad audits, held-out quality scoring and device/performance matrices remain deferred until requested or needed for a concrete failure investigation.

## Ending a session without losing the project

Update the handoff with the actual branch/HEAD when available, dirty files, changes made, checks passed/failed/unrun, unresolved design choices, and next action. Include any service/process started and whether it is still needed. Record precise blockers and useful work that remains independent of them.

Do not leave only “continue the plan.” A good handoff says what file or scenario to examine next, what hypothesis is being tested, and what result would change the decision. Avoid dumping the entire conversation; preserve facts and evidence.

When resuming, verify those facts before acting. If a prior design is demonstrably wrong, amend the decision and roadmap rather than following it mechanically. If the user changes priorities, retain the requirement history and adjust dependencies without silently dropping commitments.

## Milestone completion review

Before closing a milestone, record its implemented user journey, affected requirements, integration changes and known limitations. Run only absolutely necessary checks under the policy above; unperformed audits remain explicit and do not imply unfinished code. Review what the milestone taught us and select the next capability expansion using evidence.

Declare separately what is ready for development, what is implemented, what is device-verified, and what is release-ready. Baseline gates stay visible even when new ambitious capabilities are added. The long-term objective is sustained, measurable improvement across sessions, not the appearance of completing a checklist.
